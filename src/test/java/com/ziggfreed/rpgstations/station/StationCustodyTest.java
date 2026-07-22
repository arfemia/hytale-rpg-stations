package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Pure tests for {@link StationCustody} + {@link StationCustodyClaim} (design section 9.4,
 * phase-2 leg C): placement-quantity math, the family-matched drain/peek engine, the placement
 * matchers, and the auto-return branch decision - the "custody return-path coverage" gate this
 * leg's brief requires, exercised without a live server (mirroring {@code StationToolScaling}'s
 * injected-resolver pattern for the one live lookup, {@code Item.getResourceTypes}).
 */
class StationCustodyTest {

    private static final UUID OWNER = UUID.randomUUID();

    /** A tiny injected {@code itemId -> resourceTypeIds} family resolver, no live Item lookup. */
    private static Function<String, String[]> families(Map<String, String[]> table) {
        return id -> table.getOrDefault(id, new String[0]);
    }

    // ==================== placeableQuantity ====================

    @Test
    void placeableQuantity_wholeStackFitsUnderCap() {
        assertEquals(16, StationCustody.placeableQuantity(0, 16, 100));
    }

    @Test
    void placeableQuantity_capsAtRemainingHeadroom() {
        assertEquals(4, StationCustody.placeableQuantity(96, 16, 100));
    }

    @Test
    void placeableQuantity_alreadyFull_placesNothing() {
        assertEquals(0, StationCustody.placeableQuantity(100, 16, 100));
    }

    @Test
    void placeableQuantity_nonPositiveInputs_placeNothing() {
        assertEquals(0, StationCustody.placeableQuantity(0, 0, 100));
        assertEquals(0, StationCustody.placeableQuantity(0, 16, 0));
    }

    // ==================== available / drain (family match) ====================

    @Test
    void available_exactItemId_sumsAcrossMultipleEntries() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 5);
        claim.add("Wood_Pine_Log", 3);
        assertEquals(5, StationCustody.available(claim, "Wood_Oak_Log", null, families(Map.of())));
    }

    @Test
    void available_resourceTypeFamily_sumsEveryMatchingItemId() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 5);
        claim.add("Wood_Pine_Log", 3);
        claim.add("Stone_Cobble", 10);
        Function<String, String[]> resolver = families(Map.of(
                "Wood_Oak_Log", new String[] {"Wood_Hardwood_Trunk"},
                "Wood_Pine_Log", new String[] {"Wood_Hardwood_Trunk", "Wood_Trunk"},
                "Stone_Cobble", new String[] {"Stone"}));
        assertEquals(8, StationCustody.available(claim, null, "Wood_Hardwood_Trunk", resolver));
    }

    @Test
    void available_nullClaim_isZero() {
        assertEquals(0, StationCustody.available(null, "Wood_Oak_Log", null, families(Map.of())));
    }

    @Test
    void drain_exactItemId_removesAndZeroesEntryOut() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 5);
        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        int drained = StationCustody.drain(claim, "Wood_Oak_Log", null, 5, families(Map.of()), drainedOut);
        assertEquals(5, drained);
        assertEquals(Map.of("Wood_Oak_Log", 5), drainedOut);
        assertTrue(claim.isEmpty());
    }

    @Test
    void drain_familyMatch_oldestPlacedFirst_partialThenNextEntry() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 3);
        claim.add("Wood_Pine_Log", 4);
        Function<String, String[]> resolver = families(Map.of(
                "Wood_Oak_Log", new String[] {"Wood_Hardwood_Trunk"},
                "Wood_Pine_Log", new String[] {"Wood_Hardwood_Trunk"}));
        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        int drained = StationCustody.drain(claim, null, "Wood_Hardwood_Trunk", 5, resolver, drainedOut);
        assertEquals(5, drained);
        // 3 from the oldest (Oak) entry, 2 more from the second (Pine) entry - insertion order.
        assertEquals(3, drainedOut.get("Wood_Oak_Log"));
        assertEquals(2, drainedOut.get("Wood_Pine_Log"));
        assertEquals(2, claim.totalQuantity());
        assertFalse(claim.items().containsKey("Wood_Oak_Log"));
    }

    @Test
    void drain_insufficientClaim_returnsShortAmount_leavesWhatItCouldNotDrain() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 2);
        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        int drained = StationCustody.drain(claim, "Wood_Oak_Log", null, 5, families(Map.of()), drainedOut);
        assertEquals(2, drained);
        assertTrue(claim.isEmpty());
    }

    @Test
    void drain_nullClaim_drainsNothing() {
        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        assertEquals(0, StationCustody.drain(null, "Wood_Oak_Log", null, 5, families(Map.of()), drainedOut));
        assertTrue(drainedOut.isEmpty());
    }

    // ==================== StationCustodyClaim ====================
    // NOTE: toItemStacks() constructs a live ItemStack (the same engine-construction trap
    // StationToolScaling's javadoc documents for ItemToolSpec - constructing one outside a
    // running Hytale server throws), so it is exercised only at StationService's ONE live call
    // site, never here; the tally itself is fully covered without touching that type.

    @Test
    void claim_add_tallyIsPerItemIdAndTotalsAcrossEntries() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 5);
        claim.add("Wood_Oak_Log", 2);
        claim.add("Wood_Pine_Log", 3);
        assertEquals(2, claim.items().size());
        assertEquals(7, claim.items().get("Wood_Oak_Log"));
        assertEquals(10, claim.totalQuantity());
        assertFalse(claim.isEmpty());
    }

    @Test
    void claim_add_nonPositiveQuantity_isNoOp() {
        StationCustodyClaim claim = new StationCustodyClaim(OWNER, "sawmill", "work");
        claim.add("Wood_Oak_Log", 0);
        claim.add("Wood_Oak_Log", -3);
        assertTrue(claim.isEmpty());
    }

    // ==================== matchesInput / matchesAnyConversionInput ====================

    @Test
    void matchesInput_exactItemIdRoute() {
        ActionInput matcher = ActionInput.of("MMO_Sharpened_Iron_Bar", null, null, null);
        assertTrue(StationCustody.matchesInput(matcher, "MMO_Sharpened_Iron_Bar", new String[0], Map.of()));
        assertFalse(StationCustody.matchesInput(matcher, "MMO_Sharpened_Gold_Bar", new String[0], Map.of()));
    }

    @Test
    void matchesInput_resourceTypeFamilyRoute() {
        ActionInput matcher = ActionInput.of(null, "Wood_Hardwood_Trunk", null, null);
        assertTrue(StationCustody.matchesInput(matcher, "Wood_Oak_Log",
                new String[] {"Wood_Hardwood_Trunk", "Wood_Trunk"}, Map.of()));
        assertFalse(StationCustody.matchesInput(matcher, "Stone_Cobble", new String[] {"Stone"}, Map.of()));
    }

    @Test
    void matchesInput_tagsRoute() {
        ActionInput matcher = ActionInput.of(null, null, Map.of("Family", new String[] {"Hatchet"}), null);
        assertTrue(StationCustody.matchesInput(matcher, "Some_Axe", new String[0],
                Map.of("Family", new String[] {"Hatchet"})));
        assertFalse(StationCustody.matchesInput(matcher, "Some_Pick", new String[0],
                Map.of("Family", new String[] {"Pickaxe"})));
    }

    @Test
    void matchesInput_functionRoute_deferredToLegE_neverMatches() {
        ActionInput matcher = ActionInput.of(null, null, null, "Weapon");
        assertFalse(StationCustody.matchesInput(matcher, "Any_Item", new String[0], Map.of()));
    }

    @Test
    void matchesAnyConversionInput_resourceTypeFamily_bareLogFallback() {
        StationAsset.Conversion[] conversions = {
                StationAsset.Conversion.of(
                        StationAsset.Ingredient.resource("Wood_Hardwood_Trunk", 1),
                        StationAsset.Ingredient.item("Wood_Hardwood_Planks", 4))
        };
        assertTrue(StationCustody.matchesAnyConversionInput(conversions, "Wood_Oak_Log",
                new String[] {"Wood_Hardwood_Trunk"}));
        assertFalse(StationCustody.matchesAnyConversionInput(conversions, "Stone_Cobble", new String[] {"Stone"}));
    }

    @Test
    void matchesAnyConversionInput_exactItemId() {
        StationAsset.Conversion[] conversions = {
                StationAsset.Conversion.of(
                        StationAsset.Ingredient.item("MMO_Iron_Ingot", 2),
                        StationAsset.Ingredient.item("MMO_Sharpened_Iron_Bar", 1))
        };
        assertTrue(StationCustody.matchesAnyConversionInput(conversions, "MMO_Iron_Ingot", new String[0]));
        assertFalse(StationCustody.matchesAnyConversionInput(conversions, "MMO_Gold_Ingot", new String[0]));
    }

    // ==================== shouldReturnToInventory (the auto-return branch decision) ====================
    // Design 9.4's exit-path coverage: EVERY stop() call funnels through this one decision, so
    // its four combinations exhaustively cover "return to inventory" vs "drop at block".

    @Test
    void shouldReturnToInventory_ownerReachableWithRoom_returnsToInventory() {
        assertTrue(StationCustody.shouldReturnToInventory(true, true));
    }

    @Test
    void shouldReturnToInventory_ownerReachableButFull_dropsAtBlock() {
        assertFalse(StationCustody.shouldReturnToInventory(true, false));
    }

    @Test
    void shouldReturnToInventory_ownerUnreachableWithRoomFlagIrrelevant_dropsAtBlock() {
        assertFalse(StationCustody.shouldReturnToInventory(false, true));
    }

    @Test
    void shouldReturnToInventory_ownerUnreachableAndNoRoom_dropsAtBlock() {
        assertFalse(StationCustody.shouldReturnToInventory(false, false));
    }
}
