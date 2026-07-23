package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.api.EnhanceStamper;
import com.ziggfreed.rpgstations.api.StampInspection;
import com.ziggfreed.rpgstations.api.StampResult;
import com.ziggfreed.rpgstations.api.StatRoll;

/**
 * Pure tests for {@link StationStepHandlers.StampHandler#applyStampMutation} (design section 9.5,
 * phase 2 round-7 D-6). The load-bearing REGRESSION is the throwing-stamper case: a mutation
 * failure must PROPAGATE out of {@code applyStampMutation} (so the caller's own try/catch restores
 * reagents and never reaches {@code StationCustodyClaim#setUniqueStack}), never be swallowed. The
 * others assert the new {@code Mutation} return shape carries the {@code StampResult}'s stack + line
 * report and a zero durability delta when no {@code Durability} group is authored.
 *
 * <p><b>Why no live {@code ItemStack}:</b> the real {@code ItemStack} class initializer fails in a
 * unit JVM (it drags in the Hytale logger + codec validators). These tests therefore only exercise
 * the branches that never dereference the stack - the throwing/no-op/lines-passthrough paths, all
 * with a {@code null} stack reference (never a with-copy durability call, which is {@code
 * ItemStack}'s own tested behavior, not {@code applyStampMutation}'s). The durability-delta CAPTURE
 * itself (reading {@code Durability.AddMax}) runs before any stack mutation and is covered
 * indirectly; a live-{@code ItemStack} durability round-trip belongs to the in-game checklist.
 */
class StationStampMutationTest {

    private static EnhanceStamper stamper(StampResult result, boolean throwOnApply) {
        return new EnhanceStamper() {
            @Nonnull
            @Override
            public StampInspection inspect(@Nonnull ItemStack stack) {
                return StampInspection.empty();
            }

            @Nonnull
            @Override
            public StampResult apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries) {
                if (throwOnApply) {
                    throw new IllegalStateException("bad third-party stamper");
                }
                return result;
            }
        };
    }

    private static StampCapEngine.Plan planWithEntries() {
        return new StampCapEngine.Plan(List.of(new StatRoll("MMO_CritChance", 5)), false);
    }

    @Test
    void throwingStamper_propagatesSoCustodyIsNeverWritten() {
        // The M5 invariant at the pure level: a throwing apply must ESCAPE applyStampMutation (the
        // caller catches it and restores reagents) - it must NOT return normally.
        assertThrows(IllegalStateException.class, () -> StationStepHandlers.StampHandler.applyStampMutation(
                null, null, planWithEntries(), stamper(null, true)));
    }

    @Test
    void noStatsNoDurability_returnsInputStackEmptyLinesZeroDelta() {
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, StampCapEngine.Plan.NOTHING_TO_GRANT, null);
        assertNull(m.stack());   // the (untouched) input stack passes straight through
        assertTrue(m.lines().isEmpty());
        assertEquals(0.0, m.durabilityAdded());
    }

    @Test
    void stamperResult_linesPassThroughAndZeroDurabilityWithoutGroup() {
        List<EnhanceLine> reported = List.of(new EnhanceLine("MMO_CritChance", 5, Message.raw("+5% Crit")));
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, planWithEntries(), stamper(new StampResult(null, reported), false));
        assertEquals(1, m.lines().size());
        assertEquals("MMO_CritChance", m.lines().get(0).statId());
        assertEquals(5, m.lines().get(0).points());
        assertEquals(0.0, m.durabilityAdded());
    }

    @Test
    void emptyPlan_skipsStamperEntirely() {
        // A denied/empty roll must not call the stamper at all; with no durability group the
        // result is the untouched input, empty lines, zero delta.
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, StampCapEngine.Plan.NOTHING_TO_GRANT, stamper(new StampResult(null, List.of()), true));
        assertNull(m.stack());
        assertTrue(m.lines().isEmpty());
        assertEquals(0.0, m.durabilityAdded());
    }
}
