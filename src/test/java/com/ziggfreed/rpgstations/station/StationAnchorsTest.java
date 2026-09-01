package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The pure anchor decision cores (scope-2 wave 3, design 2.2/2.4, gate m4/m5) - discovery radius
 * bounds, the ring-scan offset generator, the claim-precedence table, and block-key parsing,
 * verified without a live server (the live index/ring/claim glue lives in {@code StationService}).
 */
class StationAnchorsTest {

    // ==================== cappedRadius (design 2.2: authored MaxRadiusMeters, capped 16) ====================

    @Test
    void cappedRadius_roundsUpAndClampsTo16() {
        assertEquals(12, StationAnchors.cappedRadius(12.0));
        assertEquals(13, StationAnchors.cappedRadius(12.1), "rounds UP");
        assertEquals(16, StationAnchors.cappedRadius(99.0), "hard-capped at 16");
        assertEquals(0, StationAnchors.cappedRadius(-5.0), "floored at 0");
        assertEquals(1, StationAnchors.cappedRadius(0.4));
    }

    // ==================== claimAllowed (gate m5: own-session OR non-empty custody refuses) ====================

    @Test
    void claimAllowed_freeBlock() {
        assertTrue(StationAnchors.claimAllowed(false, false), "a free block accepts an anchor claim");
    }

    @Test
    void claimAllowed_ownSessionRefuses() {
        // The m5 "fire dual-role" case: a block busy with its OWN work session refuses an anchor claim.
        assertFalse(StationAnchors.claimAllowed(true, false));
    }

    @Test
    void claimAllowed_nonEmptyCustodyRefuses() {
        assertFalse(StationAnchors.claimAllowed(false, true));
    }

    @Test
    void claimAllowed_bothRefuse() {
        assertFalse(StationAnchors.claimAllowed(true, true));
    }

    // ==================== ringOffsets (design 2.2: bounded scan, nearest-ordered) ====================

    @Test
    void ringOffsets_withinBoundsAndExcludesOrigin() {
        int radius = 3;
        int ySpread = 2;
        List<int[]> offsets = StationAnchors.ringOffsets(radius, ySpread);
        long rSq = (long) radius * radius;
        for (int[] off : offsets) {
            assertTrue((long) off[0] * off[0] + (long) off[2] * off[2] <= rSq,
                    "every offset within the horizontal radius");
            assertTrue(Math.abs(off[1]) <= ySpread, "every offset within the y spread");
            assertFalse(off[0] == 0 && off[1] == 0 && off[2] == 0, "origin (the primary block) excluded");
        }
    }

    @Test
    void ringOffsets_orderedNearestFirst() {
        List<int[]> offsets = StationAnchors.ringOffsets(4, 1);
        long prevDistSq = -1;
        for (int[] off : offsets) {
            long distSq = (long) off[0] * off[0] + (long) off[2] * off[2];
            assertTrue(distSq >= prevDistSq, "offsets are ordered by ascending horizontal distance");
            prevDistSq = distSq;
        }
        // The very first offset is an immediate horizontal neighbour (distance 1), never a far one.
        assertEquals(1L, (long) offsets.get(0)[0] * offsets.get(0)[0]
                + (long) offsets.get(0)[2] * offsets.get(0)[2]);
    }

    @Test
    void ringOffsets_zeroRadiusYieldsColumnOnly() {
        // radius 0 -> only the (0, dy, 0) column offsets, origin excluded.
        List<int[]> offsets = StationAnchors.ringOffsets(0, 2);
        for (int[] off : offsets) {
            assertEquals(0, off[0]);
            assertEquals(0, off[2]);
            assertFalse(off[1] == 0);
        }
        assertEquals(4, offsets.size(), "dy in {-2,-1,1,2}");
    }

    // ==================== parseCoords / blockKey / horizontalDistSq ====================

    @Test
    void blockKeyRoundTripsThroughParseCoords() {
        String key = StationAnchors.blockKey("world-uuid-1234", 10, 64, -7);
        assertArrayEquals(new int[] {10, 64, -7}, StationAnchors.parseCoords(key));
    }

    @Test
    void parseCoords_toleratesColonInWorldUuid() {
        // The world uuid segment is opaque; only the LAST three colon fields are coords.
        assertArrayEquals(new int[] {1, 2, 3}, StationAnchors.parseCoords("a:b:c:1:2:3"));
    }

    @Test
    void parseCoords_malformedIsNull() {
        assertNull(StationAnchors.parseCoords(null));
        assertNull(StationAnchors.parseCoords("nope"));
        assertNull(StationAnchors.parseCoords("world:1:2:notanumber"));
    }

    @Test
    void worldPrefix_isTheKeysOwnLeadingSegment() {
        // The prefix is what every per-world sweep and the world-scoped custody-retrieval match
        // compare against, so it MUST be exactly what blockKey puts in front of the coordinates.
        String key = StationAnchors.blockKey("world-uuid-1234", 10, 64, -7);
        assertTrue(key.startsWith(StationAnchors.worldPrefix("world-uuid-1234")));
        assertFalse(key.startsWith(StationAnchors.worldPrefix("world-uuid-9999")));
    }

    @Test
    void worldPrefix_doesNotMatchAWorldWhoseUuidIsAPrefixOfAnother() {
        // The trailing separator is what stops "world-1" from matching "world-12"'s keys.
        String key = StationAnchors.blockKey("world-12", 1, 2, 3);
        assertFalse(key.startsWith(StationAnchors.worldPrefix("world-1")));
    }

    @Test
    void horizontalDistSq_ignoresY() {
        assertEquals(0L, StationAnchors.horizontalDistSq(5, 5, 5, 5));
        assertEquals(25L, StationAnchors.horizontalDistSq(0, 0, 3, 4));
    }

    // ==================== blockGone (AV wave: a state flip must not read as "station gone") ====================

    @Test
    void blockGone_stateFlipKeepsTheSameItemId() {
        // The regression this rule exists for: the state flip REPLACES the block with its generated
        // state variant, so the block-TYPE id changes on every Empty/Loaded/Working flip while the
        // containing Item id stays put. The session must survive that.
        assertFalse(StationAnchors.blockGone("RPG_Station_CookingFire", "RPG_Station_CookingFire",
                "RPG_Station_CookingFire", "*RPG_Station_CookingFire_Loaded"));
    }

    @Test
    void blockGone_itemIdMatchIsCaseInsensitive() {
        assertFalse(StationAnchors.blockGone("RPG_Station_Sawmill", "rpg_station_sawmill",
                "RPG_Station_Sawmill", "RPG_Station_Sawmill"));
    }

    @Test
    void blockGone_brokenBlockHasNoItemId() {
        assertTrue(StationAnchors.blockGone("RPG_Station_CookingFire", null,
                "RPG_Station_CookingFire", "Empty"));
    }

    @Test
    void blockGone_replacedWithADifferentBlock() {
        assertTrue(StationAnchors.blockGone("RPG_Station_CookingFire", "RPG_Station_Sawmill",
                "RPG_Station_CookingFire", "RPG_Station_Sawmill"));
    }

    @Test
    void blockGone_fallsBackToTypeIdWhenTheBlockHasNoItem() {
        // No containing Item at engage: the block-TYPE id compare is all we have.
        assertFalse(StationAnchors.blockGone(null, null, "Native_Thing", "Native_Thing"));
        assertTrue(StationAnchors.blockGone(null, null, "Native_Thing", "Other_Thing"));
        assertFalse(StationAnchors.blockGone(null, "SomethingElse", "Native_Thing", "Native_Thing"),
                "a null start item id ignores the item side entirely");
        assertFalse(StationAnchors.blockGone(null, null, null, null),
                "two unreadable reads compare equal, exactly like two identical ids");
    }

    // ==================== deriveBlockItemIndex (AV wave: cold-server discovery seeding) ====================

    @Test
    void deriveBlockItemIndex_joinsBlocksToStationsThroughTheirUseInteraction() {
        Map<String, String> interactions = new LinkedHashMap<>();
        interactions.put("RPG_Station_Sawmill_Use", "sawmill");
        interactions.put("RPG_Station_CookingFire_Use", "cookingfire");
        List<StationAnchors.BlockUse> blocks = List.of(
                new StationAnchors.BlockUse("RPG_Station_Sawmill", "RPG_Station_Sawmill_Use"),
                new StationAnchors.BlockUse("RPG_Station_CookingFire", "RPG_Station_CookingFire_Use"),
                new StationAnchors.BlockUse("Furniture_Crude_Brazier", "SomeVanillaUse"));

        Map<String, String> index = StationAnchors.deriveBlockItemIndex(interactions, blocks);

        assertEquals(2, index.size(), "a non-station block contributes nothing");
        assertEquals("sawmill", index.get("rpg_station_sawmill"));
        assertEquals("cookingfire", index.get("rpg_station_cookingfire"));
    }

    @Test
    void deriveBlockItemIndex_normalizesBothSidesAndSkipsBlanks() {
        Map<String, String> interactions = new LinkedHashMap<>();
        interactions.put("RPG_Station_Sawmill_Use", "SAWMILL");
        interactions.put("Blank_Use", "  ");
        List<StationAnchors.BlockUse> blocks = List.of(
                new StationAnchors.BlockUse("RPG_STATION_SAWMILL", "rpg_station_sawmill_use"),
                new StationAnchors.BlockUse("  ", "RPG_Station_Sawmill_Use"),
                new StationAnchors.BlockUse("Blank_Station_Block", "Blank_Use"),
                new StationAnchors.BlockUse(null, null));

        Map<String, String> index = StationAnchors.deriveBlockItemIndex(interactions, blocks);

        assertEquals(1, index.size());
        assertEquals("sawmill", index.get("rpg_station_sawmill"), "key AND value lowercased");
    }

    @Test
    void deriveBlockItemIndex_firstWinsOnARepeatedItemId() {
        Map<String, String> interactions = new LinkedHashMap<>();
        interactions.put("First_Use", "sawmill");
        interactions.put("Second_Use", "cookingfire");
        List<StationAnchors.BlockUse> blocks = List.of(
                new StationAnchors.BlockUse("RPG_Station_Sawmill", "First_Use"),
                new StationAnchors.BlockUse("RPG_Station_Sawmill", "Second_Use"));

        Map<String, String> index = StationAnchors.deriveBlockItemIndex(interactions, blocks);

        assertEquals("sawmill", index.get("rpg_station_sawmill"));
    }

    @Test
    void deriveBlockItemIndex_emptyInputsAreEmpty() {
        assertTrue(StationAnchors.deriveBlockItemIndex(Map.of(), List.of()).isEmpty());
    }
}
