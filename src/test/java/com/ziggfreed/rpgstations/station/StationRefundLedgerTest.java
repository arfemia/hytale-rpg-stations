package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
        // Any committed produce clears the ENTIRE current iteration's ledger (M1).
        StationService.clearIterationLedgerOnCommittedProduce(s);
        assertTrue(s.iterationConsumed.isEmpty(), "a custody produce clears the whole ledger");
    }

    @Test
    void produceToInventoryClearsLedger_noMidDurationDoubleGrant() {
        // Review minor m1: a step authoring Consume + Produce(To:Inventory) + Duration commits the
        // output to the player's inventory, THEN suspends on the Duration. A stop during that suspend
        // must NOT refund the consumed inputs (the output already went to the player) - the SAME M1
        // clear the To:Custody branch does. The grant itself needs a live server, so this locks the
        // pure ledger contract the inventory-produce branch now invokes (StationStepHandlers
        // .producePhase -> clearIterationLedgerOnCommittedProduce): the refund source is emptied, so
        // refundIterationLedger re-grants nothing.
        StationSession s = session();
        StationService.recordIterationConsumedItem(s, "Fish", 1);   // Consume phase
        assertTrue(s.iterationConsumed.containsKey("Fish"));
        StationService.clearIterationLedgerOnCommittedProduce(s);    // Produce To:Inventory commit
        assertTrue(s.iterationConsumed.isEmpty(),
                "an inventory produce clears the refund ledger - a mid-Duration stop never double-grants");
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
        StationService.clearIterationLedgerOnCommittedProduce(s);

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

    // ==================== stash-backed custody x the refund ledger ====================

    @Test
    void interruptedIteration_overStashBackedCustody_refundLedgerHoldsTheRealDrainedIds() {
        // The Consume From:Custody phase drains the block's chunk-persisted stash (via the claim
        // view) and records the REAL drained ids into the session's refund ledger. An interrupt
        // BEFORE any produce commits must find those ids still in the ledger - that is the refund
        // source - while the stash keeps only what was not drained.
        StationSession s = session();
        StationCustodyClaim claim = new StationCustodyClaim(
                UUID.randomUUID(), "cuttingboard", "prepfish", 0, 64, 0);
        claim.add("Food_Fish_Raw", 2);
        claim.add("Ingredient_Salt", 1);

        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        StationCustody.drain(claim, "Food_Fish_Raw", null, 1, id -> new String[0], drainedOut);
        StationService.recordIterationConsumedMap(s, drainedOut);

        assertEquals(Map.of("Food_Fish_Raw", 1), s.iterationConsumed,
                "the interrupt refund source is exactly what the stash gave up");
        assertEquals(2, claim.totalQuantity(), "the stash keeps the undrained remainder");
    }

    @Test
    void custodyReturnAndRefund_stayMutuallyExclusive_overStashBackedCustody() {
        // The D38 rule with the stash in the loop: consume FROM the stash, produce the transformed
        // item back INTO it, and the committed produce clears the whole iteration's ledger - a
        // later stop hands back the stash's contents (the produced item) and refunds NOTHING, so
        // the player can never receive both the input and the output of one transform.
        StationSession s = session();
        StationCustodyClaim claim = new StationCustodyClaim(
                UUID.randomUUID(), "cookingfire", "cook", 0, 64, 0);
        claim.add("Food_Fish_Raw", 1);

        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        StationCustody.drain(claim, "Food_Fish_Raw", null, 1, id -> new String[0], drainedOut);
        StationService.recordIterationConsumedMap(s, drainedOut);
        assertTrue(s.iterationConsumed.containsKey("Food_Fish_Raw"));

        // Produce.To:Custody - the transformed output lands in the SAME stash, ledger clears.
        claim.add("Food_Fish_Cooked", 1);
        StationService.clearIterationLedgerOnCommittedProduce(s);

        assertTrue(s.iterationConsumed.isEmpty(),
                "refund and custody-return are mutually exclusive per iteration");
        assertEquals(Map.of("Food_Fish_Cooked", 1), claim.items(),
                "the stash holds the produced item the custody return will hand back");
    }

    // ==================== which stops hand custody back (persistence rule) ====================

    @Test
    void custodyReturnsAtStop_leaveItReasons_keepTheStashInTheWorld() {
        // A disconnect, a server stop and a world change leave placed custody standing in the
        // chunk-persisted stash - the player collects it at the block later. These are also the
        // stop paths that can run without the owning world's thread, so they must not touch
        // chunk state either way.
        assertFalse(StationService.custodyReturnsAtStop(StationService.StopReason.DISCONNECTED));
        assertFalse(StationService.custodyReturnsAtStop(StationService.StopReason.SERVER_STOP));
        assertFalse(StationService.custodyReturnsAtStop(StationService.StopReason.WORLD_CHANGED));
    }

    @Test
    void custodyReturnsAtStop_presentPlayerReasons_handTheMaterialsBack() {
        // Every reason whose player is still present keeps the long-standing hand-back.
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.PLAYER_EXIT));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.MOVED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.DAMAGED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.DIED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.STATION_GONE));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.OUT_OF_INPUTS));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.INPUTS_EXHAUSTED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.INVENTORY_FULL));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.SESSION_CAP));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.FEATURE_DISABLED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.TOOL_CHANGED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.TOOL_BROKEN));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.STEP_FAILED));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.RITUAL_COMPLETE));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.ANCHOR_LOST));
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.PATH_BLOCKED));
    }
}
