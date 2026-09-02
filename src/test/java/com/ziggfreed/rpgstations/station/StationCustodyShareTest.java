package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The Share matrix over the pure ownership cores (decision 82: one owner per pile, exact by
 * construction): {@code Place} opens a pile only while EMPTY (the first contributor owns it until
 * it drains empty again, and a non-empty pile never accepts a second player's materials -
 * co-mingling is unrepresentable), {@code Use} gates engaging over a foreign non-empty pile,
 * {@code Reclaim} relaxes the owner-only retrieval, and a Produce-written pile belongs to the
 * session's WORKER. All three default false = the classic owner-only behavior.
 */
class StationCustodyShareTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID GUEST = UUID.fromString("99999999-8888-7777-6666-555555555555");

    // ==================== Share.Place (the empty-pile rule) ====================

    @Test
    void place_ownerAlwaysTopsUpTheirOwnPile() {
        assertTrue(StationCustody.canPlace(false, OWNER, OWNER, false, OWNER));
        assertTrue(StationCustody.canPlace(true, OWNER, OWNER, false, OWNER));
    }

    @Test
    void place_nonEmptyForeignPile_neverOpens_evenShared() {
        // Place=true opens pile CREATION, never co-mingling: a non-empty pile has exactly one
        // owner and only that player adds to it.
        assertFalse(StationCustody.canPlace(false, OWNER, OWNER, false, GUEST));
        assertFalse(StationCustody.canPlace(true, OWNER, OWNER, false, GUEST));
    }

    @Test
    void place_emptyPile_opensOnlyUnderShare() {
        // A guest may start a pile in someone else's stash only under Share.Place; the stash
        // owner needs no grant.
        assertFalse(StationCustody.canPlace(false, OWNER, null, true, GUEST));
        assertTrue(StationCustody.canPlace(true, OWNER, null, true, GUEST));
        assertTrue(StationCustody.canPlace(false, OWNER, null, true, OWNER));
    }

    @Test
    void place_drainedPile_reopens() {
        // First-contributor-owns lasts UNTIL DRAINED: an emptied pile is claimable again under
        // the same empty-pile rule, previous owner or not.
        assertTrue(StationCustody.canPlace(true, OWNER, GUEST, true, OWNER));
        assertTrue(StationCustody.canPlace(true, OWNER, OWNER, true, GUEST));
        assertFalse(StationCustody.canPlace(false, OWNER, OWNER, true, GUEST),
                "without Share.Place an emptied pile still only opens to the stash owner");
    }

    @Test
    void place_freshBlock_isAlwaysOpen() {
        // No stash yet: placing is what creates it, and the placer becomes its owner.
        assertTrue(StationCustody.canPlace(false, null, null, true, GUEST));
    }

    // ==================== Share.Use ====================

    @Test
    void use_matrix() {
        assertTrue(StationCustody.canUse(false, OWNER, false, OWNER), "the pile owner always works from it");
        assertFalse(StationCustody.canUse(false, OWNER, false, GUEST), "a foreign pile denies without Use");
        assertTrue(StationCustody.canUse(true, OWNER, false, GUEST), "Share.Use opens it");
        assertTrue(StationCustody.canUse(false, OWNER, true, GUEST), "an empty pile gates nothing");
        assertTrue(StationCustody.canUse(false, null, false, GUEST), "an ownerless pile gates nothing");
    }

    // ==================== Share.Reclaim ====================

    @Test
    void reclaim_matrix() {
        assertTrue(StationCustody.canReclaim(false, OWNER, OWNER), "the pile owner always takes it back");
        assertFalse(StationCustody.canReclaim(false, OWNER, GUEST), "a foreign pile denies without Reclaim");
        assertTrue(StationCustody.canReclaim(true, OWNER, GUEST), "Share.Reclaim relaxes the owner-only rule");
        assertTrue(StationCustody.canReclaim(false, null, GUEST), "an ownerless pile is open");
    }

    // ==================== pile ownership by construction (the claim view) ====================

    @Test
    void firstContribution_ownsThePile_topUpKeepsIt() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "cookingpit", "stew", 0, 64, 0);
        claim.addTo("ingredients", GUEST, "Food_Carrot", 2);
        assertEquals(GUEST, claim.pileOwner("ingredients"), "the first contributor owns the pile");

        claim.addTo("ingredients", OWNER, "Food_Carrot", 1);
        assertEquals(GUEST, claim.pileOwner("ingredients"),
                "topping up a non-empty pile never re-owns it");
    }

    @Test
    void producePile_belongsToTheWorker() {
        // A Produce.To:Custody pile is stamped with the session WORKER's uuid at the first write
        // (StationService#produceIntoCustody passes the session player as the adder).
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "cookingpit", "stew", 0, 64, 0);
        claim.addTo("output", GUEST, "Food_Stew", 1);
        assertEquals(GUEST, claim.pileOwner("output"), "the produce pile belongs to whoever did the work");
    }

    @Test
    void refund_neverReownsAPile() {
        // A refund back into a pile passes a null adder: contents return, ownership stays put -
        // even into a pile that drained empty mid-iteration.
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "cookingpit", "stew", 0, 64, 0);
        claim.addTo("meat", GUEST, "Food_Meat_Raw", 1);
        StationCustody.drainFromPile(claim.items("meat"), "Food_Meat_Raw", null, 1, id -> new String[0], null);
        assertTrue(claim.isEmpty("meat"));

        claim.addTo("meat", null, "Food_Meat_Raw", 1);
        assertEquals(GUEST, claim.pileOwner("meat"), "a refund restores contents, never ownership");
    }

    @Test
    void ownerlessLegacyPile_fallsBackToTheStashOwner() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work", 0, 64, 0);
        claim.addTo("main", null, "Wood_Oak_Log", 3);
        assertEquals(OWNER, claim.pileOwner("main"),
                "a pile that recorded no owner answers the stash owner's identity");
    }
}
