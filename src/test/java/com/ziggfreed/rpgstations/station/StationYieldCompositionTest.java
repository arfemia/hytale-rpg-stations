package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The stated composition rule of {@code Recipe.Yield} over a MULTI-OUTPUT row: {@code Base}
 * replaces the primary (first) output's quantity alone, every other output keeps its native
 * quantity, and {@code Scale}/{@code Min}/{@code Max} apply to every output. A single-output row
 * (every plank recipe) reads exactly as it always has.
 */
public class StationYieldCompositionTest {

    private static final Ingredient[] SALVAGE_RETURN = {
            Ingredient.item("Fixture_Ore", 2),
            Ingredient.item("Fixture_Hide", 1),
            Ingredient.item("Fixture_Scrap", 3)};

    @Test
    void base_landsOnThePrimaryOutputOnly_neverFlatteningTheRest() {
        Ingredient[] yielded = StationYield.applyToOutputs(StationAsset.Yield.of(1, null, null, null), SALVAGE_RETURN);
        assertEquals(1, yielded[0].effectiveQuantity(), "Base replaces the primary output's own two");
        assertEquals(1, yielded[1].effectiveQuantity(), "the hide keeps its native one");
        assertEquals(3, yielded[2].effectiveQuantity(), "the scrap keeps its native three");
        assertEquals("Fixture_Ore", yielded[0].getItemId());
        assertEquals("Fixture_Scrap", yielded[2].getItemId());
    }

    @Test
    void scaleAndClamps_applyToEveryOutput_afterBaseTookThePrimary() {
        Ingredient[] yielded = StationYield.applyToOutputs(StationAsset.Yield.of(4, 2.0, null, 5), SALVAGE_RETURN);
        assertEquals(5, yielded[0].effectiveQuantity(), "4 x 2 = 8, capped at 5");
        assertEquals(2, yielded[1].effectiveQuantity(), "1 x 2");
        assertEquals(5, yielded[2].effectiveQuantity(), "3 x 2 = 6, capped at 5");
    }

    @Test
    void aSingleOutputRow_underBaseOne_isUnchanged() {
        Ingredient[] plank = {Ingredient.item("Fixture_Plank", 1)};
        Ingredient[] yielded = StationYield.applyToOutputs(StationAsset.Yield.of(1, null, null, null), plank);
        assertEquals(1, yielded.length);
        assertEquals(1, yielded[0].effectiveQuantity());
        assertEquals("Fixture_Plank", yielded[0].getItemId());
    }

    @Test
    void resolveQuantity_statesWhichOutputIsBeingResolved() {
        StationAsset.Yield base = StationAsset.Yield.of(7, null, null, null);
        assertEquals(7, StationYield.resolveQuantity(base, 2), "the two-arg form resolves the primary");
        assertEquals(7, StationYield.resolveQuantity(base, 2, true));
        assertEquals(2, StationYield.resolveQuantity(base, 2, false), "a secondary output ignores Base");
        assertEquals(2, StationYield.resolveQuantity(null, 2, false), "a null Yield is the identity for every output");
    }

    @Test
    void anEmptyOutputList_yieldsAnEmptyList() {
        assertEquals(0, StationYield.applyToOutputs(StationAsset.Yield.of(1, null, null, null), new Ingredient[0]).length,
                "the essence-only route's empty output survives the transform");
    }
}
