package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Socket ISOLATION over the pure custody cores and the detached claim view: each socket's
 * contents live in their own pile, a drain can never cross a pile boundary, {@code SingleFamily}
 * locks ONE socket's pile without touching its neighbours, and capacity composes as the min of
 * the socket's own cap and the custody-level cap with the PER-BLOCK total capped by the
 * custody-level cap across every pile.
 */
class SocketIsolationTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final Function<String, String[]> FAMILIES = id -> switch (id) {
        case "Food_Meat_Raw", "Food_Poultry_Raw" -> new String[] {"Meat"};
        case "Food_Carrot", "Food_Onion" -> new String[] {"Vegetable"};
        default -> new String[0];
    };

    private static StationCustodyClaim claim() {
        return new StationCustodyClaim(OWNER, "cookingpit", "stew", 0, 64, 0);
    }

    @Test
    void drainingOneSocket_neverTouchesAnother() {
        StationCustodyClaim claim = claim();
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 3);
        claim.addTo("veg", OWNER, "Food_Carrot", 4);

        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        int drained = StationCustody.drainFromPile(claim.items("meat"), "Food_Meat_Raw", null, 2,
                FAMILIES, drainedOut);

        assertEquals(2, drained);
        assertEquals(1, claim.totalQuantity("meat"), "the drained pile keeps its remainder");
        assertEquals(4, claim.totalQuantity("veg"), "the neighbour pile is untouched");
        assertEquals(Map.of("Food_Meat_Raw", 2), drainedOut);
    }

    @Test
    void aFamilyDrain_staysInsideItsOwnPile() {
        // Both piles hold Meat-family items; a family drain addressed at ONE pile must not reach
        // across even when it runs short there.
        StationCustodyClaim claim = claim();
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 1);
        claim.addTo("overflow", OWNER, "Food_Poultry_Raw", 5);

        int drained = StationCustody.drainFromPile(claim.items("meat"), null, "Meat", 3, FAMILIES, null);

        assertEquals(1, drained, "a short pile drains short - never borrows from a neighbour");
        assertEquals(5, claim.totalQuantity("overflow"));
    }

    @Test
    void availability_isPerPile() {
        StationCustodyClaim claim = claim();
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 2);
        claim.addTo("veg", OWNER, "Food_Carrot", 7);

        assertEquals(2, StationCustody.availableInPile(claim.items("meat"), null, "Meat", FAMILIES));
        assertEquals(0, StationCustody.availableInPile(claim.items("veg"), null, "Meat", FAMILIES));
        assertEquals(7, StationCustody.availableInPile(claim.items("veg"), null, "Vegetable", FAMILIES));
    }

    @Test
    void singleFamily_locksOnlyItsOwnPile() {
        StationCustodyClaim claim = claim();
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 1);

        // The meat pile is locked to the Meat family...
        assertFalse(StationCustody.pileAcceptsFamily(true, claim.items("meat"),
                "Food_Carrot", new String[] {"Vegetable"}, FAMILIES));
        assertTrue(StationCustody.pileAcceptsFamily(true, claim.items("meat"),
                "Food_Poultry_Raw", new String[] {"Meat"}, FAMILIES));
        // ...while the (empty) veg pile accepts anything, single-family or not.
        assertTrue(StationCustody.pileAcceptsFamily(true, claim.items("veg"),
                "Food_Carrot", new String[] {"Vegetable"}, FAMILIES));
    }

    @Test
    void capacity_isMinOfSocketAndCustodyCaps() {
        // Socket cap 5 under custody cap 100: the socket's own cap clips.
        assertEquals(3, StationCustody.placeableQuantity(2, 2, 10, 5, 100, null));
        // Socket cap wider than the custody cap never matters - ResolvedSocket already mins the
        // two, but the pure core also clips against the block room on its own.
        assertEquals(1, StationCustody.placeableQuantity(0, 99, 10, 100, 100, null));
    }

    @Test
    void perBlockTotal_capsAcrossPiles() {
        // Two piles of 40 under a custody cap of 100: a third socket with plenty of its own room
        // may only take the block's remaining 20.
        StationCustodyClaim claim = claim();
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 40);
        claim.addTo("veg", OWNER, "Food_Carrot", 40);
        int blockTotal = claim.totalQuantity();
        assertEquals(80, blockTotal);
        assertEquals(20, StationCustody.placeableQuantity(0, blockTotal, 64, 100, 100, null));
    }

    @Test
    void retrievalOfOnePile_leavesTheOthersStanding() {
        // The pile-removal half of a per-socket retrieval (the ItemStack hand-back itself needs a
        // live server - the established in-JVM boundary): removing one pile never touches its
        // neighbours, and the stash stands while any pile remains.
        StationCustodyClaim claim = claim();
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 3);
        claim.addTo("veg", OWNER, "Food_Carrot", 4);

        claim.removePile("meat");

        assertTrue(claim.isEmpty("meat"));
        assertEquals(4, claim.totalQuantity("veg"), "removing one pile never touches another");
        assertTrue(claim.hasAnyPile(), "the stash stands while any pile remains");
        assertEquals(List.of("veg"), claim.pileIds());
    }
}
