package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The ONE anchor-id case rule (review minor: validator/runtime case divergence). The validator is
 * fully case-insensitive (it lowercases both declared anchor keys and step {@code At}/{@code Walk.To}
 * references), so the runtime {@link StationService#anchorBlockKeyFor} lookup over
 * {@link StationSession#anchorBlocks} must be too - {@code resolveAndClaimAnchors} keys the map
 * lowercase and every lookup lowercases its query. An author declaring anchor {@code "Fire"} but
 * referencing {@code At:"fire"} passes validation, so it must resolve at runtime as well.
 */
class StationAnchorCaseTest {

    @Test
    void anchorBlockKeyFor_isCaseInsensitive() {
        StationSession s = new StationSession();
        // resolveAndClaimAnchors keys the map lowercase (the store side of the ONE rule).
        s.anchorBlocks.put("fire", "world:10:20:30");

        // A reference in ANY case resolves to the same block key.
        assertEquals("world:10:20:30", StationService.anchorBlockKeyFor(s, "fire"));
        assertEquals("world:10:20:30", StationService.anchorBlockKeyFor(s, "Fire"));
        assertEquals("world:10:20:30", StationService.anchorBlockKeyFor(s, "FIRE"));
    }

    @Test
    void anchorBlockKeyFor_selfAndBlankFallBackToPrimaryBlock() {
        StationSession s = new StationSession();
        s.blockKey = "world:1:2:3";
        s.anchorBlocks.put("fire", "world:10:20:30");

        // "self" (any case), null, and blank all resolve to the primary block, never the anchor map.
        assertEquals("world:1:2:3", StationService.anchorBlockKeyFor(s, "self"));
        assertEquals("world:1:2:3", StationService.anchorBlockKeyFor(s, "SELF"));
        assertEquals("world:1:2:3", StationService.anchorBlockKeyFor(s, null));
        assertEquals("world:1:2:3", StationService.anchorBlockKeyFor(s, "   "));
    }

    @Test
    void anchorBlockKeyFor_unknownAnchorIsNull() {
        StationSession s = new StationSession();
        s.blockKey = "world:1:2:3";
        assertNull(StationService.anchorBlockKeyFor(s, "nowhere"));
    }
}
