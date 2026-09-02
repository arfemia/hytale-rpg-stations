package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionInput;

/**
 * The Block-socket pure cores over stubbed identity readers (the established smoke boundary: the
 * live {@code BlockOps} block/item reads are engine-bound and verified in game; everything
 * decidable from their answers is pinned here): the {@code At} offset composes with the station
 * block's facing exactly as {@code Custody.Display} offsets do (quarter-turn exact), a state
 * variant matches through its BASE item identity (the caller normalizes before matching), and a
 * catch-all {@code Match} still needs a REAL block - air never satisfies a socket.
 */
class SocketBlockMatchTest {

    private static final Function<String, String[]> RESOURCE_TYPES = id ->
            "Deco_Campfire_Off".equals(id) ? new String[] {"Campfire"} : new String[0];
    private static final Function<String, Map<String, String[]>> TAGS = id ->
            "Deco_Campfire_Off".equals(id) ? Map.of("Type", new String[] {"Fire"}) : Map.of();

    // ==================== facing composition (the Custody.Display convention) ====================

    @Test
    void target_identityAtDefaultFacing() {
        assertArrayEquals(new int[] {10, 65, 22}, StationCustody.blockSocketTarget(10, 64, 20, 0, 1, 2, 0.0),
                "yaw 0: the authored offset IS the world offset");
    }

    @Test
    void target_quarterTurnsCarryTheOffsetAround() {
        // The engine block-vector convention (StationBlockFacing.rotateOffset): one positive
        // quarter turn maps (x, z) -> (z, -x); Y never rotates. An authored +Z (the block's
        // front) must land toward the block's front for EVERY placement orientation.
        int[] front = {0, 0, 2};
        assertArrayEquals(new int[] {2, 64, 0},
                StationCustody.blockSocketTarget(0, 64, 0, front[0], front[1], front[2], Math.PI / 2));
        assertArrayEquals(new int[] {0, 64, -2},
                StationCustody.blockSocketTarget(0, 64, 0, front[0], front[1], front[2], Math.PI));
        assertArrayEquals(new int[] {-2, 64, 0},
                StationCustody.blockSocketTarget(0, 64, 0, front[0], front[1], front[2], 3 * Math.PI / 2));
    }

    @Test
    void target_roundsBackOntoExactCells() {
        // cos/sin of a quarter turn carry floating error (6.12e-17-style); the rounding must land
        // on exact cells for every axis combination.
        int[] t = StationCustody.blockSocketTarget(5, 64, 5, 3, -1, 1, Math.PI / 2);
        assertArrayEquals(new int[] {6, 63, 2}, t);
    }

    // ==================== matching (base-id identity over injected readers) ====================

    @Test
    void match_byExactItemId() {
        ActionInput match = ActionInput.of("Deco_Campfire_Off", null, null, null);
        assertTrue(StationCustody.blockSocketMatches("Deco_Campfire_Off", match, RESOURCE_TYPES, TAGS));
        assertFalse(StationCustody.blockSocketMatches("Rock_Stone", match, RESOURCE_TYPES, TAGS));
    }

    @Test
    void match_byResourceFamilyAndTags() {
        assertTrue(StationCustody.blockSocketMatches("Deco_Campfire_Off",
                ActionInput.of(null, "Campfire", null, null), RESOURCE_TYPES, TAGS));
        assertTrue(StationCustody.blockSocketMatches("Deco_Campfire_Off",
                ActionInput.of(null, null, Map.of("Type", new String[] {"Fire"}), null), RESOURCE_TYPES, TAGS));
        assertFalse(StationCustody.blockSocketMatches("Rock_Stone",
                ActionInput.of(null, "Campfire", null, null), RESOURCE_TYPES, TAGS));
    }

    @Test
    void match_stateVariantMatchesThroughItsBaseId() {
        // The caller normalizes a placed state variant onto its base item id BEFORE matching
        // (BlockOps.baseItemIdOf) - so what reaches this core for a lit fire is the base id, and
        // one matcher covers the whole state family.
        String normalizedBase = "Deco_Campfire_Off";
        assertTrue(StationCustody.blockSocketMatches(normalizedBase,
                ActionInput.of("Deco_Campfire_Off", null, null, null), RESOURCE_TYPES, TAGS));
    }

    @Test
    void catchAllMatch_needsARealBlock() {
        assertTrue(StationCustody.blockSocketMatches("Rock_Stone", null, RESOURCE_TYPES, TAGS),
                "a match-less socket accepts any real block");
        assertFalse(StationCustody.blockSocketMatches("Empty", null, RESOURCE_TYPES, TAGS),
                "air (the engine's empty key) never satisfies a socket");
        assertFalse(StationCustody.blockSocketMatches(null, null, RESOURCE_TYPES, TAGS),
                "an unreadable cell fails closed");
        assertFalse(StationCustody.blockSocketMatches("", null, RESOURCE_TYPES, TAGS));
    }
}
