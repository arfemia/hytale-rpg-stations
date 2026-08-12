package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.loot.stamp.StampInspection;
import com.ziggfreed.common.loot.stamp.StampPlan;
import com.ziggfreed.common.loot.stamp.StatRoll;
import com.ziggfreed.common.loot.stamp.Stamper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for {@link StationStepHandlers.StampHandler#applyStampMutation}. The load-bearing
 * REGRESSION is the throwing-stamper case: a mutation failure must PROPAGATE out of
 * {@code applyStampMutation} (so the caller's own try/catch restores reagents and never reaches
 * {@code StationCustodyClaim#setUniqueStack}), never be swallowed. The others assert the
 * {@code Mutation} return shape reports one line per stat the plan actually wrote, and a zero
 * durability delta when no {@code Durability} group is authored.
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

    private static Stamper stamper(boolean throwOnApply) {
        return new Stamper() {
            @Nonnull
            @Override
            public StampInspection inspect(@Nonnull ItemStack stack) {
                return StampInspection.empty();
            }

            @Nonnull
            @Override
            public ItemStack apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries) {
                if (throwOnApply) {
                    throw new IllegalStateException("bad third-party stamper");
                }
                return stack;
            }
        };
    }

    /** A stamper that owns its stat vocabulary and says what each one is called. */
    private static Stamper describingStamper() {
        return new Stamper() {
            @Nonnull
            @Override
            public StampInspection inspect(@Nonnull ItemStack stack) {
                return StampInspection.empty();
            }

            @Nonnull
            @Override
            public ItemStack apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries) {
                return stack;
            }

            @Override
            public Message describe(@Nonnull StatRoll entry) {
                return Message.raw("Critical Chance +" + entry.points());
            }
        };
    }

    /** A stamper whose naming half is broken, while its write half works. */
    private static Stamper throwingDescribeStamper() {
        return new Stamper() {
            @Nonnull
            @Override
            public StampInspection inspect(@Nonnull ItemStack stack) {
                return StampInspection.empty();
            }

            @Nonnull
            @Override
            public ItemStack apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries) {
                return stack;
            }

            @Override
            public Message describe(@Nonnull StatRoll entry) {
                throw new IllegalStateException("bad third-party stamper");
            }
        };
    }

    private static StampPlan planWithEntries() {
        return new StampPlan(List.of(new StatRoll("Fixture_CritChance", 5)), false);
    }

    @Test
    void throwingStamper_propagatesSoCustodyIsNeverWritten() {
        // The M5 invariant at the pure level: a throwing apply must ESCAPE applyStampMutation (the
        // caller catches it and restores reagents) - it must NOT return normally.
        assertThrows(IllegalStateException.class, () -> StationStepHandlers.StampHandler.applyStampMutation(
                null, null, planWithEntries(), stamper(true)));
    }

    @Test
    void noStatsNoDurability_returnsInputStackEmptyLinesZeroDelta() {
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, StampPlan.NOTHING, null);
        assertNull(m.stack());   // the (untouched) input stack passes straight through
        assertTrue(m.lines().isEmpty());
        assertEquals(0.0, m.durabilityAdded());
    }

    /**
     * One line per stat the PLAN wrote. A stamper with no wording for its own stats leaves the label
     * unset, and the summary then reports the id and its points plainly - the engine never invents a
     * name for a vocabulary it does not own.
     */
    @Test
    void writtenStats_reportOneStructuredLineEach() {
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, planWithEntries(), stamper(false));
        assertEquals(1, m.lines().size());
        assertEquals("Fixture_CritChance", m.lines().get(0).statId());
        assertEquals(5, m.lines().get(0).points());
        assertNull(m.lines().get(0).label(), "a stamper that names nothing leaves the label unset");
        assertEquals(0.0, m.durabilityAdded());
    }

    /**
     * The stamper that WROTE the stat is the one asked what it is called, so its wording rides on
     * the line from the moment the ritual commits. That is the whole reason the label lives here
     * rather than being pieced back together later: one place decides, and every surface that
     * reports the enhancement reads the same answer.
     */
    @Test
    void aStamperThatNamesItsStats_putsThatNameOnTheLine() {
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, planWithEntries(), describingStamper());

        assertEquals(1, m.lines().size(), "still exactly one line per stat written");
        assertNotNull(m.lines().get(0).label());
        assertEquals("Critical Chance +5", m.lines().get(0).label().getRawText());
    }

    /** A label is a nicety; a stamper that fails at it must not cost the stat that was applied. */
    @Test
    void aStamperThatThrowsWhileNamingCostsOnlyTheName() {
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, planWithEntries(), throwingDescribeStamper());

        assertEquals(1, m.lines().size());
        assertEquals("Fixture_CritChance", m.lines().get(0).statId());
        assertEquals(5, m.lines().get(0).points());
        assertNull(m.lines().get(0).label(), "the stat still landed; only its wording was lost");
    }

    @Test
    void emptyPlan_skipsStamperEntirely() {
        // A denied/empty roll must not call the stamper at all; with no durability group the
        // result is the untouched input, empty lines, zero delta.
        StationStepHandlers.StampHandler.Mutation m = StationStepHandlers.StampHandler.applyStampMutation(
                null, null, StampPlan.NOTHING, stamper(true));
        assertNull(m.stack());
        assertTrue(m.lines().isEmpty());
        assertEquals(0.0, m.durabilityAdded());
    }
}
