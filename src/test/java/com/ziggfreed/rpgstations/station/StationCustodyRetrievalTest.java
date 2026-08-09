package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationCustodyRetrieval} (the press-F custody retrieval feature): the
 * WORLD-SCOPED network-id match, and the retrieval eligibility decision - most notably the "a
 * session actively working the block is a no-op" rule the maintainer's brief calls out explicitly,
 * exercised without a live server (mirrors {@code StationCustodyTest}'s own injected/fixture-only
 * pattern). Every id here is authored by the test; none of them come from shipped content.
 */
class StationCustodyRetrievalTest {

    /** Two fixture worlds, spelled as the block-key prefix the engine builds. */
    private static final String WORLD_A = "aaaaaaaa-0000-0000-0000-000000000001:";
    private static final String WORLD_B = "bbbbbbbb-0000-0000-0000-000000000002:";

    // ==================== owns (the world-scoped match) ====================

    @Test
    void owns_matchesSameWorldSameId() {
        assertTrue(StationCustodyRetrieval.owns(WORLD_A + "2:64:2", WORLD_A, 99, 99));
    }

    @Test
    void owns_rejectsSameIdInAnotherWorld() {
        // The whole point of the scoping: a network id counter starts at 1 in EVERY world, so the
        // same integer routinely names a different entity in each. An unscoped match would resolve
        // world B's claim from a press in world A and hand over its contents.
        assertFalse(StationCustodyRetrieval.owns(WORLD_B + "2:64:2", WORLD_A, 99, 99));
    }

    @Test
    void owns_rejectsDifferentIdInSameWorld() {
        assertFalse(StationCustodyRetrieval.owns(WORLD_A + "1:64:1", WORLD_A, 42, 99));
    }

    @Test
    void owns_rejectsClaimWithNoDisplay() {
        assertFalse(StationCustodyRetrieval.owns(WORLD_A + "1:64:1", WORLD_A, null, 99));
    }

    // ==================== findOwningBlockKey ====================

    @Test
    void findOwningBlockKey_matchesById() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put(WORLD_A + "1:64:1", 42);
        snapshot.put(WORLD_A + "2:64:2", 99);
        assertEquals(WORLD_A + "2:64:2",
                StationCustodyRetrieval.findOwningBlockKey(snapshot, WORLD_A, 99));
    }

    @Test
    void findOwningBlockKey_skipsAnIdenticalIdInAnotherWorld() {
        // Insertion order puts the foreign-world collision FIRST, so a scope-blind scan would
        // return it: the exact nondeterministic misdelivery this guards against.
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put(WORLD_B + "5:64:5", 99);
        snapshot.put(WORLD_A + "2:64:2", 99);
        assertEquals(WORLD_A + "2:64:2",
                StationCustodyRetrieval.findOwningBlockKey(snapshot, WORLD_A, 99));
    }

    @Test
    void findOwningBlockKey_foreignWorldOnly_returnsNull() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put(WORLD_B + "5:64:5", 99);
        assertNull(StationCustodyRetrieval.findOwningBlockKey(snapshot, WORLD_A, 99));
    }

    @Test
    void findOwningBlockKey_noMatch_returnsNull() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put(WORLD_A + "1:64:1", 42);
        assertNull(StationCustodyRetrieval.findOwningBlockKey(snapshot, WORLD_A, 7));
    }

    @Test
    void findOwningBlockKey_emptySnapshot_returnsNull() {
        assertNull(StationCustodyRetrieval.findOwningBlockKey(new LinkedHashMap<>(), WORLD_A, 7));
    }

    // ==================== decide (precedence) ====================

    @Test
    void decide_unknownTarget_whenNoClaimFound() {
        assertEquals(StationCustodyRetrieval.Outcome.UNKNOWN_TARGET,
                StationCustodyRetrieval.decide(false, false, true, true));
    }

    @Test
    void decide_busy_whenSessionActive_evenForOwnerWithNonEmptyClaim() {
        // The maintainer's explicit no-op case: an active session at the block always wins,
        // regardless of ownership/claim contents.
        assertEquals(StationCustodyRetrieval.Outcome.BUSY,
                StationCustodyRetrieval.decide(true, true, true, true));
    }

    @Test
    void decide_busy_outranksNotOwner() {
        // BUSY is checked before ownership - a non-owner pressing an actively-worked station
        // still reads as BUSY, not NOT_OWNER (precedence order per Outcome's own javadoc).
        assertEquals(StationCustodyRetrieval.Outcome.BUSY,
                StationCustodyRetrieval.decide(true, true, false, true));
    }

    @Test
    void decide_notOwner_whenClaimBelongsToSomeoneElse() {
        assertEquals(StationCustodyRetrieval.Outcome.NOT_OWNER,
                StationCustodyRetrieval.decide(true, false, false, true));
    }

    @Test
    void decide_nothingToRetrieve_whenOwnerButClaimEmpty() {
        assertEquals(StationCustodyRetrieval.Outcome.NOTHING_TO_RETRIEVE,
                StationCustodyRetrieval.decide(true, false, true, false));
    }

    @Test
    void decide_retrieve_whenOwnerNoActiveSessionAndNonEmptyClaim() {
        assertEquals(StationCustodyRetrieval.Outcome.RETRIEVE,
                StationCustodyRetrieval.decide(true, false, true, true));
    }
}
