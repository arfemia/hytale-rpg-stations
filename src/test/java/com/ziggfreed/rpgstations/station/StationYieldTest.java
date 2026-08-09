package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE {@link StationYield} transform, now DETERMINISTIC end to end: no ladder, no chance, no
 * injected randomness. What a reader sees in a {@code Yield} group is exactly what a cycle makes;
 * everything conditional is a {@code Bonus} Roll granting ADDITIVE {@code OutputItems} instead.
 *
 * <p>Every number below is authored by this test.
 */
public class StationYieldTest {

    private static StationAsset.Yield yieldOf(Integer base, Double scale, Integer min, Integer max) {
        return StationAsset.Yield.of(base, scale, min, max);
    }

    // ==================== Base + Scale ====================

    @Test
    void nullYield_isTheIdentity_soAnUnauthoredRecipeIsUnchanged() {
        assertEquals(7, StationYield.resolveQuantity(null, 7));
    }

    @Test
    void absentBase_usesTheConversionsOwnQuantity() {
        assertEquals(4, StationYield.resolveQuantity(yieldOf(null, null, null, null), 4));
    }

    @Test
    void authoredBase_overridesTheConversionsOwnQuantity() {
        assertEquals(3, StationYield.resolveQuantity(yieldOf(3, null, null, null), 9));
    }

    @Test
    void nonPositiveBase_isIgnoredInFavourOfTheConversionsOwnQuantity() {
        assertEquals(9, StationYield.resolveQuantity(yieldOf(0, null, null, null), 9));
        assertEquals(9, StationYield.resolveQuantity(yieldOf(-2, null, null, null), 9));
    }

    @Test
    void scale_multipliesAndFloorsToAWholeItem() {
        assertEquals(6, StationYield.resolveQuantity(yieldOf(3, 2.0, null, null), 1));
        assertEquals(5, StationYield.resolveQuantity(yieldOf(2, 2.5, null, null), 1),
                "2 x 2.5 = 5.0 exactly");
        assertEquals(7, StationYield.resolveQuantity(yieldOf(3, 2.5, null, null), 1),
                "3 x 2.5 = 7.5 floors to 7 - deterministic, never a remainder roll");
    }

    @Test
    void absentOrNonPositiveScale_isTheNeutralOne() {
        assertEquals(3, StationYield.resolveQuantity(yieldOf(3, null, null, null), 1));
        assertEquals(3, StationYield.resolveQuantity(yieldOf(3, 0.0, null, null), 1));
        assertEquals(3, StationYield.resolveQuantity(yieldOf(3, -4.0, null, null), 1));
    }

    // ==================== The absolute 1-item floor ====================

    @Test
    void aSubOneResult_stillProducesOneItem_becauseItemLossIsNeverATuningOutcome() {
        assertEquals(1, StationYield.resolveQuantity(yieldOf(1, 0.4, null, null), 1));
        assertEquals(1, StationYield.resolveQuantity(yieldOf(2, 0.1, null, null), 1));
    }

    @Test
    void authoredMinBelowTheAbsoluteFloor_cannotLowerIt() {
        assertEquals(1, StationYield.resolveQuantity(yieldOf(1, 0.2, 0, null), 1));
        assertEquals(1, StationYield.resolveQuantity(yieldOf(1, 0.2, -5, null), 1));
    }

    // ==================== Min / Max clamps ====================

    @Test
    void authoredMin_raisesASmallResult() {
        assertEquals(4, StationYield.resolveQuantity(yieldOf(1, null, 4, null), 1));
    }

    @Test
    void authoredMax_capsALargeResult() {
        assertEquals(5, StationYield.resolveQuantity(yieldOf(20, null, null, 5), 1));
    }

    @Test
    void minAboveMax_resolvesToMax_soTheClampNeverContradictsItself() {
        assertEquals(2, StationYield.resolveQuantity(yieldOf(1, null, 9, 2), 1));
    }

    @Test
    void aNonPositiveMax_isIgnoredRatherThanErasingTheOutput() {
        assertEquals(3, StationYield.resolveQuantity(yieldOf(3, null, null, 0), 1));
    }

    // ==================== applyToOutputs ====================

    @Test
    void applyToOutputs_isIdentityOnANullYield_soTheNoKnobPathAllocatesNothing() {
        Ingredient[] outputs = {Ingredient.item("Fixture_Plank", 2)};
        assertSame(outputs, StationYield.applyToOutputs(null, outputs));
    }

    @Test
    void applyToOutputs_scalesEveryOutputOfAMultiOutputConversion() {
        Ingredient[] outputs = {
                Ingredient.item("Fixture_Plank", 2),
                Ingredient.item("Fixture_Offcut", 1)};
        Ingredient[] scaled = StationYield.applyToOutputs(yieldOf(null, 3.0, null, null), outputs);

        assertEquals(6, scaled[0].effectiveQuantity(), "the main product scales");
        assertEquals(3, scaled[1].effectiveQuantity(), "so does the byproduct - one recipe, one curve");
        assertEquals("Fixture_Plank", scaled[0].getItemId());
        assertEquals("Fixture_Offcut", scaled[1].getItemId());
    }

    @Test
    void applyToOutputs_isDeterministic_soRepeatedCallsAgree() {
        Ingredient[] outputs = {Ingredient.item("Fixture_Plank", 3)};
        StationAsset.Yield y = yieldOf(null, 2.5, null, null);
        int first = StationYield.applyToOutputs(y, outputs)[0].effectiveQuantity();
        for (int i = 0; i < 20; i++) {
            assertEquals(first, StationYield.applyToOutputs(y, outputs)[0].effectiveQuantity(),
                    "the same authored Yield always produces the same quantity");
        }
    }
}
