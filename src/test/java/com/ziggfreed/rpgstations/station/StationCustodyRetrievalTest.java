package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationCustodyRetrieval} (the new press-F custody retrieval feature):
 * the network-id bookkeeping lookup, and the retrieval eligibility decision - most notably the
 * "a session actively working the block is a no-op" rule the maintainer's brief calls out
 * explicitly, exercised without a live server (mirrors {@code StationCustodyTest}'s own
 * injected/fixture-only pattern).
 */
class StationCustodyRetrievalTest {

    // ==================== findOwningBlockKey ====================

    @Test
    void findOwningBlockKey_matchesById() {
        Map<String, Integer> snapshot = new HashMap<>();
        snapshot.put("world:1:64:1", 42);
        snapshot.put("world:2:64:2", 99);
        assertEquals("world:2:64:2", StationCustodyRetrieval.findOwningBlockKey(snapshot, 99));
    }

    @Test
    void findOwningBlockKey_noMatch_returnsNull() {
        Map<String, Integer> snapshot = new HashMap<>();
        snapshot.put("world:1:64:1", 42);
        assertNull(StationCustodyRetrieval.findOwningBlockKey(snapshot, 7));
    }

    @Test
    void findOwningBlockKey_emptySnapshot_returnsNull() {
        assertNull(StationCustodyRetrieval.findOwningBlockKey(new HashMap<>(), 7));
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
