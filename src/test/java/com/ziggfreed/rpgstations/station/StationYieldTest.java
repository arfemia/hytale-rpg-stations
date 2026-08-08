package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Exercises the PURE {@code Recipe.Yield} transform ({@link StationYield}). Every expected number
 * here is derived from fixture values this test AUTHORS, never from shipped balance data - a
 * balancing pass on a station's own JSON must never require a test edit.
 */
public class StationYieldTest {

    /** A fixture factor resolver over an authored map; an unknown id resolves null (fail-closed). */
    private static BiFunction<String, String, Double> lookup(Map<String, Double> values) {
        return (factorId, param) -> values.get(factorId);
    }

    private static StationAsset.Yield.Floor floor(double min, int add) {
        return StationAsset.Yield.Floor.of(min, add);
    }

    private static StationAsset.Yield.Bonus bonus(FactorRef[] values, StationAsset.Yield.Floor... floors) {
        return StationAsset.Yield.Bonus.of(values, floors);
    }

    // ==================== No group / identity ====================

    @Test
    void nullYield_leavesTheAuthoredQuantityUntouched() {
        assertEquals(7, StationYield.resolveQuantity(null, 7, 999.0));
    }

    @Test
    void nullYield_returnsTheSameOutputsArrayInstance() {
        Ingredient[] outputs = {Ingredient.item("Wood_Hardwood_Planks", 3)};
        assertSame(outputs, StationYield.applyToOutputs(null, outputs, 0.0));
    }

    // ==================== Base / Scale ====================

    @Test
    void absentBase_defersToTheConversionsOwnQuantity() {
        StationAsset.Yield y = StationAsset.Yield.of(null, null, null, null, null);
        assertEquals(4, StationYield.resolveQuantity(y, 4, 0.0));
    }

    @Test
    void authoredBase_overridesTheConversionsOwnQuantity() {
        StationAsset.Yield y = StationAsset.Yield.of(2, null, null, null, null);
        assertEquals(2, StationYield.resolveQuantity(y, 9, 0.0));
    }

    @Test
    void scale_multipliesTheBaseAndRoundsToWholeItems() {
        assertEquals(6, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, 2.0, null, null, null), 1, 0.0));
        // 3 * 1.5 = 4.5, rounds to 5.
        assertEquals(5, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, 1.5, null, null, null), 1, 0.0));
    }

    @Test
    void nonpositiveScale_readerDefaultsToNeutral() {
        assertEquals(3, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, 0.0, null, null, null), 1, 0.0));
        assertEquals(3, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, Double.NaN, null, null, null), 1, 0.0));
    }

    // ==================== Bonus ladder ====================

    @Test
    void ladderValue_isTheWeightedSumOfEveryAuthoredFactor() {
        StationAsset.Yield y = StationAsset.Yield.of(1, null, bonus(new FactorRef[]{
                FactorRef.of("fixture:quality", null, 1.0),
                FactorRef.of("fixture:power", null, 2.0)}, floor(5.0, 1)), null, null);
        // 4*1.0 + 0.5*2.0 = 5.0
        assertEquals(5.0, StationYield.ladderValue(y,
                lookup(Map.of("fixture:quality", 4.0, "fixture:power", 0.5))));
    }

    @Test
    void unresolvedFactor_contributesZero() {
        StationAsset.Yield y = StationAsset.Yield.of(1, null, bonus(new FactorRef[]{
                FactorRef.of("fixture:missing", null, 1.0)}, floor(1.0, 1)), null, null);
        assertEquals(0.0, StationYield.ladderValue(y, lookup(Map.of())));
    }

    @Test
    void reachedFloor_addsItsAddOnTopOfTheScaledBase() {
        StationAsset.Yield y = StationAsset.Yield.of(2, null,
                bonus(new FactorRef[]{FactorRef.of("fixture:v", null, null)}, floor(5.0, 1)), null, null);
        assertEquals(2, StationYield.resolveQuantity(y, 1, 4.9));
        assertEquals(3, StationYield.resolveQuantity(y, 1, 5.0));
        assertEquals(3, StationYield.resolveQuantity(y, 1, 100.0));
    }

    @Test
    void floorsAreNotCumulative_highestReachedWins() {
        StationAsset.Yield y = StationAsset.Yield.of(2, null,
                bonus(new FactorRef[]{FactorRef.of("fixture:v", null, null)},
                        floor(5.0, 1), floor(9.0, 2)), null, null);
        assertEquals(2, StationYield.bonusAdd(y, 4.0) + 2);
        assertEquals(1, StationYield.bonusAdd(y, 5.0));
        assertEquals(1, StationYield.bonusAdd(y, 8.9));
        // NOT 3 - the 9.0 floor replaces the 5.0 one rather than stacking with it.
        assertEquals(2, StationYield.bonusAdd(y, 9.0));
    }

    @Test
    void floorOrderInTheArrayDoesNotMatter() {
        StationAsset.Yield descending = StationAsset.Yield.of(1, null,
                bonus(new FactorRef[]{FactorRef.of("fixture:v", null, null)},
                        floor(9.0, 2), floor(5.0, 1)), null, null);
        assertEquals(2, StationYield.bonusAdd(descending, 9.0));
        assertEquals(1, StationYield.bonusAdd(descending, 5.0));
    }

    @Test
    void noBonusGroup_addsNothing() {
        assertEquals(0, StationYield.bonusAdd(StationAsset.Yield.of(2, null, null, null, null), 999.0));
    }

    // ==================== Clamps ====================

    @Test
    void minRaisesAndMaxCapsTheFinalQuantity() {
        assertEquals(5, StationYield.resolveQuantity(
                StationAsset.Yield.of(1, null, null, 5, null), 1, 0.0));
        assertEquals(3, StationYield.resolveQuantity(
                StationAsset.Yield.of(10, null, null, null, 3), 1, 0.0));
    }

    @Test
    void oneItemFloorIsAlwaysEnforced() {
        // Base 1 scaled by 0.1 rounds to 0, which would consume inputs and produce nothing.
        assertEquals(StationAsset.Yield.ABSOLUTE_MIN, StationYield.resolveQuantity(
                StationAsset.Yield.of(1, 0.1, null, null, null), 1, 0.0));
        // An authored Min below the absolute floor cannot lower it either.
        assertEquals(StationAsset.Yield.ABSOLUTE_MIN, StationYield.resolveQuantity(
                StationAsset.Yield.of(1, 0.1, null, 0, null), 1, 0.0));
    }

    // ==================== Multi-output ====================

    @Test
    void everyOutputOfAMultiOutputConversionIsTransformed() {
        StationAsset.Yield y = StationAsset.Yield.of(null, 2.0, null, null, null);
        Ingredient[] outputs = {Ingredient.item("Main", 3), Ingredient.item("Byproduct", 1)};
        Ingredient[] out = StationYield.applyToOutputs(y, outputs, 0.0);
        assertEquals(6, out[0].effectiveQuantity());
        assertEquals("Main", out[0].getItemId());
        assertEquals(2, out[1].effectiveQuantity());
        assertEquals("Byproduct", out[1].getItemId());
    }
}
