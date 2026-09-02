package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Custody;

/**
 * The pure placement router ({@link StationCustody#routePlacement}): authored socket order IS
 * placement priority, {@code PlacePerPress} absent means the whole held stack (the classic press,
 * byte-identical through the degenerate socket) while an authored 1 loads one at a time, the
 * placed quantity clips to the min of press size / socket room / block room, and a press that
 * places nothing reports its most SPECIFIC refusal (share denial over full socket over
 * nothing-matched).
 */
class SocketPlacementRoutingTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID GUEST = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static final Function<String, String[]> NO_FAMILIES = id -> new String[0];

    /** A fixture Item socket; the injected matcher decides acceptance, so match stays null here. */
    private static Custody.ResolvedSocket socket(String id, int maxQuantity, Integer placePerPress,
            boolean sharePlace) {
        return new Custody.ResolvedSocket(id, true, null, placePerPress, null, maxQuantity,
                false, false, null, sharePlace, false, false, null);
    }

    private static Predicate<Custody.ResolvedSocket> accepts(String... ids) {
        List<String> accepted = List.of(ids);
        return s -> accepted.contains(s.id());
    }

    @Test
    void authoredOrder_firstAcceptingSocketWins() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        List<Custody.ResolvedSocket> sockets = List.of(
                socket("meat", 10, null, false),
                socket("veg", 10, null, false));

        StationCustody.PlacementRoute route = StationCustody.routePlacement(sockets, claim, OWNER,
                "Food_Carrot", 3, null, 100, accepts("meat", "veg"), NO_FAMILIES);

        assertTrue(route.placed());
        assertEquals("meat", route.socket().id(), "both accept - the earlier authored one receives");
    }

    @Test
    void nonAcceptingSocket_isSkipped_notBlocking() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        List<Custody.ResolvedSocket> sockets = List.of(
                socket("meat", 10, null, false),
                socket("veg", 10, null, false));

        StationCustody.PlacementRoute route = StationCustody.routePlacement(sockets, claim, OWNER,
                "Food_Carrot", 3, null, 100, accepts("veg"), NO_FAMILIES);

        assertEquals("veg", route.socket().id());
    }

    @Test
    void placePerPress_absent_movesTheWholeHeldStack() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        StationCustody.PlacementRoute route = StationCustody.routePlacement(
                List.of(socket("main", 100, null, false)), claim, OWNER,
                "Wood_Oak_Log", 17, null, 100, accepts("main"), NO_FAMILIES);
        assertEquals(17, route.quantity(), "the classic whole-stack press");
    }

    @Test
    void placePerPress_one_loadsOneAtATime() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        StationCustody.PlacementRoute route = StationCustody.routePlacement(
                List.of(socket("ingredients", 100, 1, false)), claim, OWNER,
                "Food_Carrot", 17, null, 100, accepts("ingredients"), NO_FAMILIES);
        assertEquals(1, route.quantity());
    }

    @Test
    void quantity_clipsToSocketAndBlockRoom() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 8);
        claim.addTo("veg", OWNER, "Food_Carrot", 90);

        // Socket room: cap 10 - 8 held = 2 left, even with a huge held stack.
        StationCustody.PlacementRoute bySocket = StationCustody.routePlacement(
                List.of(socket("meat", 10, null, false)), claim, OWNER,
                "Food_Meat_Raw", 64, null, 200, accepts("meat"), NO_FAMILIES);
        assertEquals(2, bySocket.quantity());

        // Block room: 98 already across piles under a 100 custody cap = 2 left, whatever the
        // socket's own cap still allows.
        StationCustody.PlacementRoute byBlock = StationCustody.routePlacement(
                List.of(socket("spare", 50, null, false)), claim, OWNER,
                "Wood_Oak_Log", 64, null, 100, accepts("spare"), NO_FAMILIES);
        assertEquals(2, byBlock.quantity());
    }

    @Test
    void fullFirstSocket_overflowsToTheNextAcceptingOne() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 10);
        List<Custody.ResolvedSocket> sockets = List.of(
                socket("meat", 10, null, false),
                socket("spare", 10, null, false));

        StationCustody.PlacementRoute route = StationCustody.routePlacement(sockets, claim, OWNER,
                "Food_Meat_Raw", 4, null, 100, accepts("meat", "spare"), NO_FAMILIES);

        assertEquals("spare", route.socket().id(), "a full socket passes the press along");
    }

    @Test
    void denials_reportTheMostSpecificReason() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        claim.addTo("meat", OWNER, "Food_Meat_Raw", 10);

        // Nothing matched at all.
        StationCustody.PlacementRoute wrong = StationCustody.routePlacement(
                List.of(socket("meat", 10, null, false)), claim, OWNER,
                "Rock_Stone", 1, null, 100, s -> false, NO_FAMILIES);
        assertFalse(wrong.placed());
        assertEquals(StationCustody.PlacementDenial.WRONG_INPUT, wrong.denial());

        // Matched but full.
        StationCustody.PlacementRoute full = StationCustody.routePlacement(
                List.of(socket("meat", 10, null, false)), claim, OWNER,
                "Food_Meat_Raw", 1, null, 100, accepts("meat"), NO_FAMILIES);
        assertEquals(StationCustody.PlacementDenial.FULL, full.denial());

        // Matched but the pile belongs to someone else: the share refusal outranks both.
        StationCustody.PlacementRoute shared = StationCustody.routePlacement(
                List.of(socket("meat", 10, null, false), socket("spare", 10, null, false)),
                claim, GUEST, "Food_Meat_Raw", 1, null, 100, accepts("meat"), NO_FAMILIES);
        assertEquals(StationCustody.PlacementDenial.NOT_SHARED, shared.denial(),
                "the guest matched the owner's pile - the refusal names sharing, not the material");
    }

    @Test
    void blockRouteSockets_neverReceivePlacements() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        Custody.ResolvedSocket vessel = new Custody.ResolvedSocket("vessel", false, null, null, null,
                100, false, true, null, false, false, false, null);

        StationCustody.PlacementRoute route = StationCustody.routePlacement(List.of(vessel), claim, OWNER,
                "RPG_Station_Cooking_Pot", 1, null, 100, s -> true, NO_FAMILIES);

        assertFalse(route.placed(), "a Block socket is world state - nothing is ever stored for it");
        assertNull(route.denial(), "and it does not manufacture a refusal reason either");
    }

    @Test
    void sharedEmptySocket_acceptsAGuestsFirstContribution() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "pit", "stew", 0, 64, 0);
        StationCustody.PlacementRoute route = StationCustody.routePlacement(
                List.of(socket("ingredients", 10, 1, true)), claim, GUEST,
                "Food_Carrot", 5, null, 100, accepts("ingredients"), NO_FAMILIES);
        assertTrue(route.placed(), "Share.Place opens an EMPTY pile to a guest");
        assertEquals(1, route.quantity());
    }
}
