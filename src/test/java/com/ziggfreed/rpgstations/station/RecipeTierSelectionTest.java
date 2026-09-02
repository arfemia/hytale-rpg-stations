package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Pins the set-recipe SELECTION rules: {@code Conversion.Tier} ordering (lower first, STABLE
 * authored order inside a tier, byte-identical order when no Tier is authored anywhere), the
 * derived-row tier stamp, the authored-row picker keys, and the decision-96 authored-conversion
 * picker plumbing. Every fixture is authored by the test itself - no balance data.
 */
class RecipeTierSelectionTest {

    private static StationAsset.Conversion row(String outItem, Integer tier) {
        StationAsset.Conversion c = StationAsset.Conversion.of(
                Ingredient.resource("Wood_Trunk", 1), Ingredient.item(outItem, 1), null, null);
        return tier != null ? c.withTier(tier) : c;
    }

    // ==================== tierOrdered ====================

    @Test
    void tierOrdered_noTierAnywhere_returnsTheSameArrayInstance() {
        StationAsset.Conversion[] all = {row("A", null), row("B", null), row("C", null)};
        assertSame(all, StationService.tierOrdered(all),
                "a file authoring no Tier anywhere keeps its byte-identical authored order");
    }

    @Test
    void tierOrdered_lowerTierRunsFirst() {
        StationAsset.Conversion[] all = {row("Late", 2), row("Early", 0), row("Mid", 1)};
        StationAsset.Conversion[] ordered = StationService.tierOrdered(all);
        assertEquals("Early", ordered[0].primaryOutput().getItemId());
        assertEquals("Mid", ordered[1].primaryOutput().getItemId());
        assertEquals("Late", ordered[2].primaryOutput().getItemId());
        // The input array itself is never mutated (a cached resolved array must stay authored order).
        assertEquals("Late", all[0].primaryOutput().getItemId());
    }

    @Test
    void tierOrdered_stableInsideATier_authoredOrderDecides() {
        StationAsset.Conversion[] all = {row("B1", 1), row("A0", 0), row("B2", 1), row("A1", 0)};
        StationAsset.Conversion[] ordered = StationService.tierOrdered(all);
        assertEquals("A0", ordered[0].primaryOutput().getItemId());
        assertEquals("A1", ordered[1].primaryOutput().getItemId());
        assertEquals("B1", ordered[2].primaryOutput().getItemId());
        assertEquals("B2", ordered[3].primaryOutput().getItemId());
    }

    @Test
    void tierOrdered_unauthoredTierReadsAsZero_outranksDerivedTierOne() {
        StationAsset.Conversion authored = row("Authored", null);
        StationAsset.Conversion derived = StationAsset.Conversion.derivedRow(
                new Ingredient[] {Ingredient.resource("Wood_Trunk", 1)},
                new Ingredient[] {Ingredient.item("Derived", 1)}, null, "Cat");
        StationAsset.Conversion[] ordered =
                StationService.tierOrdered(new StationAsset.Conversion[] {derived, authored});
        assertEquals("Authored", ordered[0].primaryOutput().getItemId());
        assertEquals("Derived", ordered[1].primaryOutput().getItemId());
    }

    @Test
    void tierOrdered_nullAndSingleArraysPassThrough() {
        assertNull(StationService.tierOrdered(null));
        StationAsset.Conversion[] one = {row("A", 5)};
        assertSame(one, StationService.tierOrdered(one));
    }

    // ==================== derived-row stamp ====================

    @Test
    void derivedRow_isStampedDerivedTierAndMarked() {
        StationAsset.Conversion derived = StationAsset.Conversion.derivedRow(
                new Ingredient[] {Ingredient.resource("Wood_Trunk", 1)},
                new Ingredient[] {Ingredient.item("Plank", 1)}, null, "WoodPlanks");
        assertEquals(StationAsset.Conversion.DERIVED_TIER, derived.effectiveTier());
        assertTrue(derived.isDerived());
        StationAsset.Conversion authored = row("Plank", null);
        assertEquals(0, authored.effectiveTier());
        assertFalse(authored.isDerived());
    }

    @Test
    void deriveFromCrafting_stampsEveryDerivedRowTierOne() {
        StationAsset.FromCrafting spec = StationAsset.FromCrafting.of(new String[] {"Cat"});
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(spec, List.of(
                new StationRecipeDeriver.CraftingCandidate("Out_A", List.of("Cat"),
                        List.of(Ingredient.item("In_A", 1))),
                new StationRecipeDeriver.CraftingCandidate("Out_B", List.of("Cat"),
                        List.of(Ingredient.resource("Fam_B", 2)))));
        assertEquals(2, derived.size());
        for (StationAsset.Conversion c : derived) {
            assertEquals(StationAsset.Conversion.DERIVED_TIER, c.effectiveTier());
            assertTrue(c.isDerived());
        }
    }

    // ==================== authored-row picker keys (decision 96) ====================

    @Test
    void conversionRowKey_roundTrips() {
        assertEquals(0, StationService.parseConversionRowIndex(StationService.conversionRowKey(0)));
        assertEquals(7, StationService.parseConversionRowIndex(StationService.conversionRowKey(7)));
    }

    @Test
    void parseConversionRowIndex_plainCategoryOrMalformed_isMinusOne() {
        assertEquals(-1, StationService.parseConversionRowIndex("WoodPlanks"));
        assertEquals(-1, StationService.parseConversionRowIndex(null));
        assertEquals(-1, StationService.parseConversionRowIndex("conversion:oak"));
    }

    @Test
    void conversionsForCategory_rowKey_keepsExactlyThatRow() {
        StationAsset.Conversion[] all = {row("A", null), row("B", null), row("C", null)};
        StationAsset.Conversion[] kept =
                StationService.conversionsForCategory(all, StationService.conversionRowKey(1));
        assertArrayEquals(new StationAsset.Conversion[] {all[1]}, kept);
    }

    @Test
    void conversionsForCategory_staleRowKey_fallsBackToAll() {
        StationAsset.Conversion[] all = {row("A", null)};
        assertSame(all, StationService.conversionsForCategory(all, StationService.conversionRowKey(9)),
                "a row index that no longer resolves must not blank the station");
    }

    @Test
    void authoredConversionIndexes_excludeDerivedRows() {
        StationAsset.Conversion derived = StationAsset.Conversion.derivedRow(
                new Ingredient[] {Ingredient.resource("Wood_Trunk", 1)},
                new Ingredient[] {Ingredient.item("Plank", 1)}, null, "Cat");
        StationAsset.Conversion[] all = {row("A", null), derived, row("B", 2)};
        assertEquals(List.of(0, 2), StationService.authoredConversionIndexes(all));
        assertTrue(StationService.authoredConversionIndexes(null).isEmpty());
    }
}
