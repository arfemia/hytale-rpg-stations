package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Condition;

/**
 * Pure tests for {@link StationService}'s extracted decision cores. Ported (trimmed) from the
 * MMO's {@code StationServiceTest} (RPG Stations extraction leg 2): the MMO-specific XP-factor-
 * breakdown tests and the {@code unlockedFlairIdsFrom}/{@code PlayerDataRepository} test are
 * dropped (that bookkeeping moved off {@code StationSession}, see its javadoc); the Requires
 * {@link Condition} evaluation tests are NEW this leg (design section 4.4.2).
 */
public class StationServiceTest {

    // ==================== Delayed swing-impact due-time ====================

    @Test
    void scheduleImpactAt_addsTheDelayToNow() {
        assertEquals(1140L, StationService.scheduleImpactAt(1000L, 140L));
    }

    @Test
    void impactDue_falseBeforeTheDueTime() {
        assertFalse(StationService.impactDue(999L, 1000L));
    }

    @Test
    void impactDue_trueAtExactlyTheDueTime() {
        assertTrue(StationService.impactDue(1000L, 1000L));
    }

    @Test
    void impactDue_trueAfterTheDueTime() {
        assertTrue(StationService.impactDue(1500L, 1000L));
    }

    @Test
    void impactDue_falseWhenNothingIsPending() {
        assertFalse(StationService.impactDue(999_999L, 0L));
    }

    // ==================== Item-ledger tally ====================

    @Test
    void tallyConsumedResource_sumsPerItemIdAcrossSlots() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> slots = List.of(
                new StationService.ConsumedSlot("Wood_Oak_Log", 2),
                new StationService.ConsumedSlot("Wood_Birch_Log", 1),
                new StationService.ConsumedSlot("Wood_Oak_Log", 3));

        StationService.tallyConsumedResource(tally, slots, "Wood_Hardwood_Trunk");

        assertEquals(2, tally.size());
        assertEquals(5, tally.get("Wood_Oak_Log"));
        assertEquals(1, tally.get("Wood_Birch_Log"));
    }

    @Test
    void tallyConsumedResource_accumulatesAcrossCallsIntoTheSameTally() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        StationService.tallyConsumedResource(tally,
                List.of(new StationService.ConsumedSlot("Wood_Oak_Log", 1)), "Wood_Hardwood_Trunk");
        StationService.tallyConsumedResource(tally,
                List.of(new StationService.ConsumedSlot("Wood_Oak_Log", 4)), "Wood_Hardwood_Trunk");

        assertEquals(5, tally.get("Wood_Oak_Log"));
    }

    @Test
    void tallyConsumedResource_fallsBackToResourceTypeIdWhenNoSlotHasAnItemId() {
        Map<String, Integer> tally = new LinkedHashMap<>();

        StationService.tallyConsumedResource(tally, List.of(), "Wood_Hardwood_Trunk");

        assertEquals(1, tally.get("Wood_Hardwood_Trunk"));
    }

    @Test
    void tallyConsumedResource_ignoresAZeroOrNegativeConsumedSlot() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> slots = List.of(
                new StationService.ConsumedSlot("Wood_Oak_Log", 0),
                new StationService.ConsumedSlot(null, 3));

        StationService.tallyConsumedResource(tally, slots, "Wood_Hardwood_Trunk");

        assertFalse(tally.containsKey("Wood_Oak_Log"));
        assertEquals(1, tally.get("Wood_Hardwood_Trunk"));
    }

    // ==================== Stamp-reagent consumed tally (shared mergeConsumedSlots core) ====================
    // The pure core StationService#tallyConsumedStacks folds a Stamp step's committed reagents into
    // s.consumedItems so the enhance summary shows a CONSUMED row per input stack (e.g. the 2
    // sharpened bars). The ItemStack-reading adapter itself needs a live JVM (ItemStack's static
    // init fails in unit tests, per StationStampMutationTest), so these target its shared fold core.

    @Test
    void mergeConsumedSlots_sumsReagentStacksPerItemIdAndReportsAny() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> reagents = List.of(
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 1),
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 1));

        boolean any = StationService.mergeConsumedSlots(tally, reagents);

        assertTrue(any);
        assertEquals(1, tally.size());
        assertEquals(2, tally.get("MMO_Sharpened_Iron_Bar"));
    }

    @Test
    void mergeConsumedSlots_mixedReagentFamilyKeepsDistinctDrainedIds() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> reagents = List.of(
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 1),
                new StationService.ConsumedSlot("MMO_Sharpened_Gold_Bar", 1));

        StationService.mergeConsumedSlots(tally, reagents);

        assertEquals(2, tally.size());
        assertEquals(1, tally.get("MMO_Sharpened_Iron_Bar"));
        assertEquals(1, tally.get("MMO_Sharpened_Gold_Bar"));
    }

    @Test
    void mergeConsumedSlots_accumulatesOntoAnExistingConsumedLedger() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        tally.put("Wood_Oak_Log", 4); // a prior implicit-program Consume already tallied here
        StationService.mergeConsumedSlots(tally,
                List.of(new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 2)));

        assertEquals(4, tally.get("Wood_Oak_Log"));
        assertEquals(2, tally.get("MMO_Sharpened_Iron_Bar"));
    }

    @Test
    void mergeConsumedSlots_emptyOrUnusable_reportsNoneAndAddsNoFallback() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<StationService.ConsumedSlot> slots = List.of(
                new StationService.ConsumedSlot(null, 3),
                new StationService.ConsumedSlot("MMO_Sharpened_Iron_Bar", 0));

        boolean any = StationService.mergeConsumedSlots(tally, slots);

        assertFalse(any);
        assertTrue(tally.isEmpty()); // no raw-resource-type fallback on this path, unlike tallyConsumedResource
    }

    // ==================== Held-tool gate decision ====================

    @Test
    void toolGateStopReason_matchingAndNotBroken_continues() {
        assertNull(StationService.toolGateStopReason(true, false));
    }

    @Test
    void toolGateStopReason_notMatching_stopsToolChanged() {
        assertEquals(StationService.StopReason.TOOL_CHANGED, StationService.toolGateStopReason(false, false));
    }

    @Test
    void toolGateStopReason_matchingButBroken_stopsToolBroken() {
        assertEquals(StationService.StopReason.TOOL_BROKEN, StationService.toolGateStopReason(true, true));
    }

    @Test
    void toolGateStopReason_notMatchingAndBroken_toolChangedWins() {
        assertEquals(StationService.StopReason.TOOL_CHANGED, StationService.toolGateStopReason(false, true));
    }

    // ==================== Seat-mode heartbeat decision ====================

    @Test
    void seatModeShouldStop_effectMode_neverStops() {
        assertFalse(StationService.seatModeShouldStop(false, true));
        assertFalse(StationService.seatModeShouldStop(false, false));
    }

    @Test
    void seatModeShouldStop_seatedAndStillMounted_continues() {
        assertFalse(StationService.seatModeShouldStop(true, true));
    }

    @Test
    void seatModeShouldStop_seatedButNoLongerMounted_stops() {
        assertTrue(StationService.seatModeShouldStop(true, false));
    }

    // ==================== Seat-vs-effect swing-route decision ====================

    @Test
    void useActionSlotForSwing_seatMode_usesActionSlot() {
        assertTrue(StationService.useActionSlotForSwing(true));
    }

    @Test
    void useActionSlotForSwing_effectMode_usesEmoteSlot() {
        assertFalse(StationService.useActionSlotForSwing(false));
    }

    // ==================== Session-completion presentation gate ====================

    @Test
    void shouldPlayCompletion_nonSilentWithCycles_true() {
        assertTrue(StationService.shouldPlayCompletion(false, 1));
        assertTrue(StationService.shouldPlayCompletion(false, 5));
    }

    @Test
    void shouldPlayCompletion_nonSilentZeroCycles_false() {
        assertFalse(StationService.shouldPlayCompletion(false, 0));
    }

    @Test
    void shouldPlayCompletion_silentWithCycles_false() {
        assertFalse(StationService.shouldPlayCompletion(true, 5));
    }

    @Test
    void shouldPlayCompletion_silentZeroCycles_false() {
        assertFalse(StationService.shouldPlayCompletion(true, 0));
    }

    // ==================== Requires.Condition evaluation (new this leg, design section 4.4.2) ====================

    @Test
    void conditionPasses_blankFactor_passesVacuously() {
        Condition c = Condition.of("", null, null, null);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 999.0));
    }

    @Test
    void conditionPasses_unregisteredFactor_failsClosed() {
        Condition c = Condition.of("rpgstations:tool_power", null, null, null);
        assertFalse(StationService.conditionPasses(c, (factorId, param) -> null));
    }

    @Test
    void conditionPasses_belowMin_fails() {
        Condition c = Condition.of("mmoskilltree:skill_level", "WOODCUTTING", 15.0, null);
        assertFalse(StationService.conditionPasses(c, (factorId, param) -> 10.0));
    }

    @Test
    void conditionPasses_atOrAboveMin_passes() {
        Condition c = Condition.of("mmoskilltree:skill_level", "WOODCUTTING", 15.0, null);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 15.0));
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 20.0));
    }

    @Test
    void conditionPasses_aboveMax_fails() {
        Condition c = Condition.of("rpgstations:cycle_count", null, null, 10.0);
        assertFalse(StationService.conditionPasses(c, (factorId, param) -> 11.0));
    }

    @Test
    void conditionPasses_atOrBelowMax_passes() {
        Condition c = Condition.of("rpgstations:cycle_count", null, null, 10.0);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 10.0));
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> 5.0));
    }

    @Test
    void conditionPasses_paramForwardedToTheLookup() {
        Condition c = Condition.of("mmoskilltree:skill_level", "MINING", 5.0, null);
        assertTrue(StationService.conditionPasses(c, (factorId, param) -> {
            assertEquals("mmoskilltree:skill_level", factorId);
            assertEquals("MINING", param);
            return 5.0;
        }));
    }
}
