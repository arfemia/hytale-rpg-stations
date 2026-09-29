package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The custody consume fixes and the ritual queue's pure cores. A live {@code ItemStack} cannot be
 * built in this JVM (and a {@code StashPile}'s {@code Unique} leaf delegates to the engine's
 * {@code ItemStack} codec, so a 1.0.0-shaped pile carrying one cannot be decoded here either: the
 * boundary {@code CustodyPersistenceTest} states), so the unique-stack half is pinned at its
 * decision core ({@link StationCustody#uniqueDrained}: when a pile's metadata-bearing stack is
 * gone with the piece, and how a Unique-only pile reads) and at the ledger split
 * ({@link StationCustodyLedger#countsBesideUnique}: the piece goes back once, as itself); the
 * completed-pass clear and the queue's socket walk are pinned outright. Whether a stash saved by
 * 1.0.0 with a dangling {@code Unique} loads through the engine is a dev-server check.
 */
public class StationCustodyConsumeFixTest {

    private static StationSession session() {
        StationSession s = new StationSession();
        s.stationId = "fixture_table";
        s.actionId = "unmake";
        return s;
    }

    private static Custody.ResolvedSocket itemSocket(String id) {
        return new Custody.ResolvedSocket(id, true, null, null, null, 1, false, false, null, false, false, false, null);
    }

    // ==================== bug 1: the drained unique stack goes with the piece ====================

    @Test
    void uniqueDrained_whenTheTallyNoLongerCountsItsItem() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("Fixture_Sword", 1);
        assertFalse(StationCustody.uniqueDrained("Fixture_Sword", items), "the piece still counts, the stack stays");
        items.remove("Fixture_Sword");
        assertTrue(StationCustody.uniqueDrained("Fixture_Sword", items), "the last of the piece was taken");
        assertTrue(StationCustody.uniqueDrained("Fixture_Sword", null), "no tally at all");
        assertFalse(StationCustody.uniqueDrained(null, items), "no unique stack, nothing to drain");
        items.put("fixture_sword", 1);
        assertFalse(StationCustody.uniqueDrained("Fixture_Sword", items), "ids match case-insensitively");
    }

    @Test
    void theDanglingUniquePredicate_saysAUniqueOnlyPileIsDrained_andAStacklessPileAnswersNoUnique() {
        // Only the PREDICATE is pinned here: a pile whose stack outlived its count (the shape the
        // 1.0.0 consume left behind) is drained by the decision core's answer, which is what makes
        // the claim read such a pile as holding nothing. The pile itself cannot be built in this
        // JVM (its Unique leaf is an engine ItemStack), so the second half only shows a pile with
        // no stack at all answering none and taking none.
        Map<String, Integer> empty = new LinkedHashMap<>();
        assertTrue(StationCustody.uniqueDrained("Fixture_Sword", empty));
        StationCustodyClaim claim = new StationCustodyClaim(UUID.randomUUID(), "fixture_table", "unmake", 0, 64, 0);
        assertNull(claim.uniqueStack("main"), "a pile that never had a stack answers none");
        assertNull(claim.takeUniqueIfDrained("main"), "and takes none");
        assertTrue(claim.toItemStacks("main").isEmpty());
    }

    // ==================== bug 2: the refund hands the REAL piece back once ====================

    @Test
    void countsBesideUnique_netsThePiecesOwnShareOffTheCountHalf() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Fixture_Sword", 1);
        counts.put("Fixture_Salt", 2);
        Map<String, Integer> netted = StationCustodyLedger.countsBesideUnique(counts, "Fixture_Sword", 1);
        assertEquals(Map.of("Fixture_Salt", 2), netted, "the piece is restored as itself, so its count entry goes");
        assertSame(counts, StationCustodyLedger.countsBesideUnique(counts, null, 1), "no unique stack, no netting");
        assertSame(counts, StationCustodyLedger.countsBesideUnique(counts, "Fixture_Sword", 0));
        Map<String, Integer> two = new LinkedHashMap<>();
        two.put("Fixture_Sword", 3);
        assertEquals(Map.of("Fixture_Sword", 2), StationCustodyLedger.countsBesideUnique(two, "Fixture_Sword", 1),
                "only the unique stack's own quantity is netted");
    }

    @Test
    void theUniqueLedgerHalf_isKeyedByOriginatingPile_andClearedByEveryCommit() {
        StationSession s = session();
        StationService.recordIterationConsumedUnique(s, "w:0:64:0", "slot_a", null);
        assertTrue(s.iterationConsumedUnique.isEmpty(), "a pile that gave up no unique stack records nothing");
        Map<String, Integer> drained = new LinkedHashMap<>();
        drained.put("Fixture_Sword", 1);
        StationService.recordIterationConsumedCustody(s, "w:0:64:0", "slot_a", drained);
        StationService.recordIterationConsumedItem(s, "Fixture_Salt", 1);
        StationService.clearIterationLedgerOnCommittedProduce(s);
        assertTrue(s.iterationConsumed.isEmpty());
        assertTrue(s.iterationConsumedCustody.isEmpty());
        assertTrue(s.iterationConsumedUnique.isEmpty());
    }

    @Test
    void theRefundLedger_recordsOnlyThePieceThisDrainTook() throws IOException {
        // A stack left dangling on its pile before the drain (the 1.0.0 leftover shape) is taken
        // off the pile but never ledgered: a refund would put back a piece that no longer exists.
        // Its decision is the same one the hook's report uses (drainTookPiece, pinned by id below);
        // no ItemStack can be built here, so the live call site is pinned on the source.
        assertNull(StationCustodyLedger.pieceTaken(Map.of("Fixture_Sword", 1), null), "no stack, no piece");
        String custody = SourcePins.methodBody(SourcePins.read("StationStepHandlers.java"),
                "private static StationStepResult consumeFromCustody(");
        assertEquals(1, SourcePins.count(custody, "recordIterationConsumedUnique("), "one unique-ledger write");
        int write = custody.indexOf("recordIterationConsumedUnique(");
        String written = custody.substring(write, custody.indexOf(';', write)).replaceAll("\\s+", " ");
        assertTrue(written.endsWith("socketId, StationCustodyLedger.pieceTaken(drainedOut, unique))"),
                "the unique half records only what this drain took: " + written);
        String fromPile = SourcePins.methodBody(SourcePins.read("ConsumedInput.java"), "static List<ConsumedInput> fromPile(");
        assertTrue(fromPile.contains("StationCustodyLedger.pieceTaken(drained, unique)"),
                "the hook's report and the refund ledger share the one decision");
    }

    // ==================== bug 3: a completed pass clears the custody half too ====================

    @Test
    void aCompletedPass_clearsEveryHalfOfTheLedger() {
        // A completed pass commits through the one iteration commit (StationService#commitIteration,
        // pinned at the pass by InputConsumedHookOrderTest); its ledger half is this pure core.
        StationSession s = session();
        StationService.recordIterationConsumedItem(s, "Fixture_Salt", 1);
        Map<String, Integer> drained = new LinkedHashMap<>();
        drained.put("Fixture_Sword", 1);
        StationService.recordIterationConsumedCustody(s, "w:0:64:0", "slot_a", drained);
        s.iterationConsumedInputs.add(consumption());
        assertFalse(s.iterationConsumedCustody.isEmpty(), "the custody half holds the consumed piece before the pass completes");

        StationService.commitIterationLedger(s);

        assertTrue(s.iterationConsumed.isEmpty());
        assertTrue(s.iterationConsumedCustody.isEmpty(),
                "a completed pass owes nothing: the piece it consumed is never put back by a later stop");
        assertTrue(s.iterationConsumedUnique.isEmpty());
        assertTrue(s.iterationConsumedInputs.isEmpty(), "the hook half is taken by the same commit");
    }

    // ==================== the hook half: each consumption reported once, at its commit ====================

    /**
     * A consumption record as the hook half holds one. Its stack list is empty because this JVM
     * cannot build an {@code ItemStack}; the ledger never reads the stacks, it only carries them.
     */
    private static InputConsumption consumption() {
        return new InputConsumption(null, null, null, UUID.randomUUID(), 0, 64, 0, "fixture_table", "unmake",
                List.of());
    }

    @Test
    void theCommit_takesEachLedgeredConsumptionOnce_soTheHookHearsOfItExactlyOnce() {
        StationSession s = session();
        InputConsumption first = consumption();
        InputConsumption second = consumption();
        s.iterationConsumedInputs.add(first);
        s.iterationConsumedInputs.add(second);

        assertEquals(List.of(first, second), StationService.commitIterationLedger(s),
                "the commit answers every consumption the iteration ledgered, in consume order");
        assertTrue(StationService.commitIterationLedger(s).isEmpty(),
                "a second commit (a produce, then the completed pass) reports nothing again");
    }

    @Test
    void aSessionThatCannotSayWhereItStands_ledgersNothingForTheHook() {
        // No worker handle (teardown racing the phase), or nothing consumed: no record is built,
        // so the hook is never handed a consumption without its world and block.
        StationSession s = session();
        StationService.recordIterationConsumedInputs(s, null, List.of());
        assertTrue(s.iterationConsumedInputs.isEmpty());
        assertNull(StationService.attendedConsumption(s, null, List.of()));
    }

    @Test
    void anAttendedRecord_namesItsWholeWorker_andReadsTheStationsOwnWorld() throws IOException {
        // No PlayerRef can be built in this JVM, so the record's two rules are pinned on the
        // source: every worker field InputConsumption.hasWorker reads is required (an attended
        // record never reads as unattended), and the record's world is the STATION's own, read off
        // the worker's entity (a PlayerRef names no world only before its first join, never
        // between worlds, so it is not consulted at all).
        String body = SourcePins.methodBody(SourcePins.read("StationService.java"),
                "static InputConsumption attendedConsumption(");
        for (String required : new String[] {"s.playerRef == null", "s.ref == null", "s.playerUuid == null"}) {
            assertTrue(body.contains(required), "an attended record requires " + required.replace(" == null", ""));
        }
        int guard = body.indexOf("s.playerUuid == null");
        int record = body.indexOf("new InputConsumption(s.playerRef, s.ref, s.playerUuid, worldUuid,");
        assertTrue(guard >= 0 && record > guard, "the worker is required before the record is built");
        assertFalse(body.contains("getWorldUuid()"),
                "the worker's PlayerRef is not the authority on the station's world");
        int sessionWorld = body.indexOf("sessionWorld(s)");
        int worldUuid = body.indexOf("worldUuidOf(world)", sessionWorld);
        assertTrue(sessionWorld >= 0 && worldUuid > sessionWorld && record > worldUuid,
                "the station's own world is read off the worker's entity before the record is built");
    }

    @Test
    void thePieceIsReportedConsumed_onlyWhenTheDrainTookItsItem() {
        // The hook's stacks come from ConsumedInput.fromPile, which reports the piece's own stack
        // only when this drain counted its item: a stack left dangling on the pile before the drain
        // was not consumed by it.
        Map<String, Integer> drained = new LinkedHashMap<>();
        drained.put("fixture_sword", 1);
        assertTrue(StationCustodyLedger.drainTookPiece(drained, "Fixture_Sword"), "ids match case-insensitively");
        Map<String, Integer> otherMaterial = new LinkedHashMap<>();
        otherMaterial.put("Fixture_Salt", 2);
        assertFalse(StationCustodyLedger.drainTookPiece(otherMaterial, "Fixture_Sword"),
                "a dangling piece the drain never touched is not this drain's consumption");
        assertFalse(StationCustodyLedger.drainTookPiece(drained, (String) null), "no piece stack, nothing to report");
        Map<String, Integer> zeroed = new LinkedHashMap<>();
        zeroed.put("Fixture_Sword", 0);
        assertFalse(StationCustodyLedger.drainTookPiece(zeroed, "Fixture_Sword"));
    }

    @Test
    void anUnattendedConsumption_namesNoWorker() {
        InputConsumption settled = InputConsumption.unattended(UUID.randomUUID(), 1, 2, 3, "fixture_pit", "stew",
                List.of());
        assertFalse(settled.hasWorker(), "an unattended settle consumes with nobody engaged");
        assertNull(settled.playerRef());
        assertNull(settled.workerId());
        assertEquals(1, settled.blockX());
        assertEquals("fixture_pit", settled.stationId());
        assertEquals("stew", settled.actionId());
    }

    // ==================== the ritual queue: the next filled socket in authored order ====================

    @Test
    void nextFilledSocket_walksAuthoredOrder_skipsEmptyAndBlockSockets_andEndsWhenAllAreEmpty() {
        List<Custody.ResolvedSocket> sockets = List.of(
                itemSocket("slot_a"),
                new Custody.ResolvedSocket("vessel", false, null, null, null, 1, false, false, null, false, false, false, null),
                itemSocket("slot_b"),
                itemSocket("slot_c"));
        assertEquals("slot_a", StationCustody.nextFilledSocket(sockets, id -> true));
        assertEquals("slot_b", StationCustody.nextFilledSocket(sockets, id -> !id.equals("slot_a")));
        assertEquals("slot_c", StationCustody.nextFilledSocket(sockets, id -> id.equals("slot_c") || id.equals("vessel")),
                "a block socket is never queued over");
        assertNull(StationCustody.nextFilledSocket(sockets, id -> false), "every socket empty: the queue is done");
    }

    @Test
    void firstMatchingInPile_readsTheOldestPlacedMaterialTheInputAccepts() {
        Map<String, Integer> pile = new LinkedHashMap<>();
        pile.put("Fixture_Oak", 0);
        pile.put("Fixture_Pine", 2);
        pile.put("Fixture_Ash", 1);
        assertEquals("Fixture_Pine", StationCustody.firstMatchingInPile(pile, id -> true), "a zeroed entry is skipped");
        assertEquals("Fixture_Ash", StationCustody.firstMatchingInPile(pile, "Fixture_Ash"::equals));
        assertNull(StationCustody.firstMatchingInPile(pile, id -> false));
        assertNull(StationCustody.firstMatchingInPile(null, id -> true));
    }

    @Test
    void theQueueKnob_isOffByDefault_andReadsTheWorkGroup() {
        StationAsset.Work work = StationAsset.Work.of(null, null, null, null);
        assertFalse(work.effectiveQueue());
        assertTrue(work.withQueue(true).effectiveQueue());
        assertTrue(work.effectiveLooping(), "the queue is orthogonal to looping");
    }

    // ==================== the shared matcher's ONE acceptance rule ====================

    @Test
    void theSharedMatchersExceptHole_refusesWhatTheRoutesWouldTake() {
        ActionInput matcher = ActionInput.of(null, null, Map.of("Type", new String[] {"Weapon"}), null,
                ActionInput.of("Fixture_Dagger", null, null, null));
        Map<String, String[]> weapon = Map.of("Type", new String[] {"Weapon"});
        assertTrue(StationCustody.accepts(matcher, "Fixture_Sword", null, weapon, "Weapon"));
        assertFalse(StationCustody.accepts(matcher, "Fixture_Dagger", null, weapon, "Weapon"),
                "the dagger is a weapon the Except hole names");
        assertFalse(StationCustody.accepts(matcher, "Fixture_Rock", null, Map.of("Type", new String[] {"Rock"}), null),
                "a route set still refuses what no route matches");
        ActionInput noHole = ActionInput.of(null, null, Map.of("Type", new String[] {"Weapon"}), null);
        assertTrue(StationCustody.accepts(noHole, "Fixture_Dagger", null, weapon, "Weapon"));
    }

    @Test
    void aCatchAllMatcher_acceptsEverything_butItsExceptHole_theOneRuleAtEverySite() {
        Map<String, String[]> weapon = Map.of("Type", new String[] {"Weapon"});
        assertTrue(StationCustody.accepts(null, "Fixture_Anything", null, Map.of(), null), "no matcher accepts all");
        ActionInput catchAll = ActionInput.of(null, null, null, null);
        assertTrue(catchAll.isCatchAll());
        assertTrue(StationCustody.accepts(catchAll, "Fixture_Anything", null, Map.of(), null));
        ActionInput catchAllWithHole = ActionInput.of(null, null, null, null,
                ActionInput.of("Fixture_Grimoire", null, null, null));
        assertTrue(catchAllWithHole.isCatchAll(), "an Except authors no route of the outer matcher's own");
        assertTrue(StationCustody.accepts(catchAllWithHole, "Fixture_Sword", null, weapon, "Weapon"),
                "a catch-all with a hole takes everything...");
        assertFalse(StationCustody.accepts(catchAllWithHole, "Fixture_Grimoire", null, weapon, "Weapon"),
                "...except the hole, at every site alike");
        assertFalse(StationCustody.accepts(catchAllWithHole, "fixture_grimoire", null, null, null),
                "the hole matches ids case-insensitively, like every route");
        ActionInput hollow = ActionInput.of(null, null, null, null, ActionInput.of(null, null, null, null));
        assertTrue(StationCustody.accepts(hollow, "Fixture_Sword", null, weapon, "Weapon"),
                "a catch-all Except matches no route, so it carves no hole (the validator warns EXCEPT_CATCH_ALL)");
    }
}
