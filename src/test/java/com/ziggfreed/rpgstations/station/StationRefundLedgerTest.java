package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The iteration refund ledger (scope-2 wave 3, design 2.5, gate M1's single rule) + the
 * repeat-while-inputs stop reason (design 2.4), verified without a live server on the pure
 * {@code StationService} ledger recorders + {@code StationSession#iterationConsumed}. Includes the
 * MANDATORY cross-item transform-stop case: a {@code Produce.To:Custody} clears the whole ledger so
 * a mid-transform stop never refunds the CONSUMED input while the custody return hands back the
 * PRODUCED item (refund and custody-return mutually exclusive per iteration).
 */
class StationRefundLedgerTest {

    private static StationSession session() {
        StationSession s = new StationSession();
        s.stationId = "cuttingboard";
        s.actionId = "prepfish";
        return s;
    }

    @Test
    void consumeRecordsIntoLedger() {
        StationSession s = session();
        StationService.recordIterationConsumedItem(s, "Fish", 1);
        assertEquals(Map.of("Fish", 1), s.iterationConsumed);
    }

    @Test
    void produceToCustodyClearsEntireLedger_theM1Rule() {
        StationSession s = session();
        StationService.recordIterationConsumedItem(s, "Fish", 2);
        StationService.recordIterationConsumedItem(s, "Salt", 1);
        // Any Produce.To:Custody clears the ENTIRE current iteration's ledger (M1).
        StationService.clearIterationLedgerOnCustodyProduce(s);
        assertTrue(s.iterationConsumed.isEmpty(), "a custody produce clears the whole ledger");
    }

    @Test
    void crossItemTransformStop_fishNotRefundedAfterRawProducedToCustody() {
        // The MANDATORY test (gate M1): consume Fish, produce Food_Fish_Raw to custody, then a
        // mid-cook stop. The refund source (iterationConsumed) must NOT still hold Fish - the
        // custody return (a separate path) is what hands back the raw fish.
        StationSession s = session();

        // load step: Consume Fish x1 -> the refund ledger tracks it.
        StationService.recordIterationConsumedItem(s, "Fish", 1);
        assertTrue(s.iterationConsumed.containsKey("Fish"));

        // placeraw step: Produce Food_Fish_Raw To:Custody -> clears the ledger (the transform commit).
        StationService.clearIterationLedgerOnCustodyProduce(s);

        // Mid-cook stop: the refund source is empty, so the Fish is NOT refunded (the custody
        // return, not modelled here, hands back the raw fish standing in the fire's custody).
        assertFalse(s.iterationConsumed.containsKey("Fish"),
                "the consumed Fish is NOT in the refund ledger after the transform - never double-refunded");
        assertTrue(s.iterationConsumed.isEmpty());

        // The ledger keeps working across the transform: harvest re-consumes the raw fish.
        Map<String, Integer> drained = new LinkedHashMap<>();
        drained.put("Food_Fish_Raw", 1);
        StationService.recordIterationConsumedMap(s, drained);
        assertEquals(Map.of("Food_Fish_Raw", 1), s.iterationConsumed);
    }

    @Test
    void recordIterationConsumedMap_sumsRealDrainedIds() {
        StationSession s = session();
        Map<String, Integer> drained = new LinkedHashMap<>();
        drained.put("Wood_Oak_Trunk", 2);
        drained.put("Wood_Birch_Trunk", 1);
        StationService.recordIterationConsumedMap(s, drained);
        StationService.recordIterationConsumedItem(s, "Wood_Oak_Trunk", 3);
        assertEquals(5, s.iterationConsumed.get("Wood_Oak_Trunk"), "same-id records sum");
        assertEquals(1, s.iterationConsumed.get("Wood_Birch_Trunk"));
    }

    @Test
    void recorders_ignoreNonPositiveQuantities() {
        StationSession s = session();
        StationService.recordIterationConsumedItem(s, "Fish", 0);
        StationService.recordIterationConsumedItem(s, "Fish", -3);
        Map<String, Integer> drained = new LinkedHashMap<>();
        drained.put("Salt", 0);
        StationService.recordIterationConsumedMap(s, drained);
        assertTrue(s.iterationConsumed.isEmpty());
    }

    // ==================== INPUTS_EXHAUSTED transition (design 2.4) ====================

    @Test
    void shortInputStopReason_repeatingIsInputsExhausted() {
        assertEquals(StationService.StopReason.INPUTS_EXHAUSTED, StationService.shortInputStopReason(true));
    }

    @Test
    void shortInputStopReason_nonRepeatingIsOutOfInputs() {
        assertEquals(StationService.StopReason.OUT_OF_INPUTS, StationService.shortInputStopReason(false));
    }
}
