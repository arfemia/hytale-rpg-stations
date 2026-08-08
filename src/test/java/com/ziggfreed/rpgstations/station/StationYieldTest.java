package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Exercises the PURE {@code Recipe.Yield} transform ({@link StationYield}). Every expected number
 * here is derived from fixture values this test AUTHORS, never from shipped balance data - a
 * balancing pass on a station's own JSON must never require a test edit. The stochastic remainder is
 * covered by pinning the injected roll to always-lose / always-win rather than by sampling, so no
 * assertion here can flake.
 */
public class StationYieldTest {

    /** A roll that always LOSES, so a fractional remainder never pays out (whole part alone). */
    private static final DoubleSupplier NEVER = () -> 0.999999;
    /** A roll that always WINS, so any nonzero remainder pays out. */
    private static final DoubleSupplier ALWAYS = () -> 0.0;

    /** A fixture factor resolver over an authored map; an unknown id resolves null (fail-closed). */
    private static BiFunction<String, String, Double> lookup(Map<String, Double> values) {
        return (factorId, param) -> values.get(factorId);
    }

    private static StationAsset.Yield.Floor floor(double min, double add) {
        return StationAsset.Yield.Floor.of(min, add);
    }

    private static StationAsset.Yield.Bonus bonus(FactorRef[] values, StationAsset.Yield.Floor... floors) {
        return StationAsset.Yield.Bonus.of(values, floors);
    }

    private static FactorRef ref(String id, Double weight) {
        return FactorRef.of(id, null, weight);
    }

    // ==================== No group / identity ====================

    @Test
    void nullYield_leavesTheAuthoredQuantityUntouched() {
        assertEquals(7, StationYield.resolveQuantity(null, 7, 999.0, ALWAYS));
    }

    @Test
    void nullYield_returnsTheSameOutputsArrayInstance() {
        Ingredient[] outputs = {Ingredient.item("Wood_Hardwood_Planks", 3)};
        assertSame(outputs, StationYield.applyToOutputs(null, outputs, 0.0, ALWAYS));
    }

    // ==================== Base / Scale ====================

    @Test
    void absentBase_defersToTheConversionsOwnQuantity() {
        StationAsset.Yield y = StationAsset.Yield.of(null, null, null, null, null);
        assertEquals(4, StationYield.resolveQuantity(y, 4, 0.0, NEVER));
    }

    @Test
    void authoredBase_overridesTheConversionsOwnQuantity() {
        StationAsset.Yield y = StationAsset.Yield.of(2, null, null, null, null);
        assertEquals(2, StationYield.resolveQuantity(y, 9, 0.0, NEVER));
    }

    @Test
    void scale_multipliesTheBase() {
        assertEquals(6, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, 2.0, null, null, null), 1, 0.0, NEVER));
    }

    @Test
    void nonpositiveScale_readerDefaultsToNeutral() {
        assertEquals(3, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, 0.0, null, null, null), 1, 0.0, NEVER));
        assertEquals(3, StationYield.resolveQuantity(
                StationAsset.Yield.of(3, Double.NaN, null, null, null), 1, 0.0, NEVER));
    }

    // ==================== Bonus ladder ====================

    @Test
    void ladderValue_isTheWeightedSumOfEveryAuthoredFactor() {
        StationAsset.Yield y = StationAsset.Yield.of(1, null, bonus(new FactorRef[]{
                ref("fixture:quality", 10.0), ref("fixture:level", 0.1), ref("fixture:power", 1.0)},
                floor(11.0, 1.0)), null, null);
        // 2*10.0 + 20*0.1 + 0.3*1.0 = 22.3
        assertEquals(22.3, StationYield.ladderValue(y, lookup(Map.of(
                "fixture:quality", 2.0, "fixture:level", 20.0, "fixture:power", 0.3))), 1e-9);
    }

    @Test
    void unresolvedFactor_contributesZero() {
        StationAsset.Yield y = StationAsset.Yield.of(1, null,
                bonus(new FactorRef[]{ref("fixture:missing", 1.0)}, floor(1.0, 1.0)), null, null);
        assertEquals(0.0, StationYield.ladderValue(y, lookup(Map.of())));
    }

    @Test
    void reachedFloor_addsItsAddOnTopOfTheScaledBase() {
        StationAsset.Yield y = StationAsset.Yield.of(2, null,
                bonus(new FactorRef[]{ref("fixture:v", null)}, floor(5.0, 1.0)), null, null);
        assertEquals(2, StationYield.resolveQuantity(y, 1, 4.9, NEVER));
        assertEquals(3, StationYield.resolveQuantity(y, 1, 5.0, NEVER));
        assertEquals(3, StationYield.resolveQuantity(y, 1, 100.0, NEVER));
    }

    @Test
    void floorsAreNotCumulative_highestReachedWins() {
        StationAsset.Yield y = StationAsset.Yield.of(2, null,
                bonus(new FactorRef[]{ref("fixture:v", null)}, floor(5.0, 1.0), floor(9.0, 2.0)), null, null);
        assertEquals(0.0, StationYield.bonusAdd(y, 4.0));
        assertEquals(1.0, StationYield.bonusAdd(y, 5.0));
        assertEquals(1.0, StationYield.bonusAdd(y, 8.9));
        // NOT 3 - the 9.0 floor replaces the 5.0 one rather than stacking with it.
        assertEquals(2.0, StationYield.bonusAdd(y, 9.0));
    }

    @Test
    void floorOrderInTheArrayDoesNotMatter() {
        StationAsset.Yield descending = StationAsset.Yield.of(1, null,
                bonus(new FactorRef[]{ref("fixture:v", null)}, floor(9.0, 2.0), floor(5.0, 1.0)), null, null);
        assertEquals(2.0, StationYield.bonusAdd(descending, 9.0));
        assertEquals(1.0, StationYield.bonusAdd(descending, 5.0));
    }

    @Test
    void noBonusGroup_addsNothing() {
        assertEquals(0.0, StationYield.bonusAdd(StationAsset.Yield.of(2, null, null, null, null), 999.0));
    }

    // ==================== Fractional remainder ====================

    /** A fractional yield: base 1 plus a 1.5 floor = an exact 2.5. */
    private static StationAsset.Yield fractional() {
        return StationAsset.Yield.of(1, null,
                bonus(new FactorRef[]{ref("fixture:v", null)}, floor(10.0, 1.5)), null, null);
    }

    @Test
    void exactQuantity_keepsTheFractionBeforeAnyRoll() {
        assertEquals(2.5, StationYield.exactQuantity(fractional(), 1, 10.0), 1e-9);
    }

    @Test
    void fractionalYield_paysTheWholePartAlwaysAndTheRemainderOnAWinningRoll() {
        assertEquals(2, StationYield.resolveQuantity(fractional(), 1, 10.0, NEVER));
        assertEquals(3, StationYield.resolveQuantity(fractional(), 1, 10.0, ALWAYS));
    }

    @Test
    void wholeNumberYield_isUnaffectedByTheRoll() {
        StationAsset.Yield whole = StationAsset.Yield.of(2, null, null, null, null);
        assertEquals(2, StationYield.resolveQuantity(whole, 1, 0.0, NEVER));
        assertEquals(2, StationYield.resolveQuantity(whole, 1, 0.0, ALWAYS));
    }

    @Test
    void remainderRollComparesAgainstTheFractionItself() {
        // Exact 2.5 -> a roll of 0.49 wins (0.49 < 0.5), a roll of 0.51 loses.
        assertEquals(3, StationYield.resolveQuantity(fractional(), 1, 10.0, () -> 0.49));
        assertEquals(2, StationYield.resolveQuantity(fractional(), 1, 10.0, () -> 0.51));
    }

    // ==================== Clamps ====================

    @Test
    void minRaisesAndMaxCapsTheFinalQuantity() {
        assertEquals(5, StationYield.resolveQuantity(
                StationAsset.Yield.of(1, null, null, 5, null), 1, 0.0, NEVER));
        assertEquals(3, StationYield.resolveQuantity(
                StationAsset.Yield.of(10, null, null, null, 3), 1, 0.0, NEVER));
    }

    @Test
    void oneItemFloorIsAlwaysEnforced() {
        // Base 1 scaled by 0.1 is 0.1, which would consume inputs and produce nothing on a lost roll.
        assertEquals(StationAsset.Yield.ABSOLUTE_MIN, StationYield.resolveQuantity(
                StationAsset.Yield.of(1, 0.1, null, null, null), 1, 0.0, NEVER));
        // An authored Min below the absolute floor cannot lower it either.
        assertEquals(StationAsset.Yield.ABSOLUTE_MIN, StationYield.resolveQuantity(
                StationAsset.Yield.of(1, 0.1, null, 0, null), 1, 0.0, NEVER));
    }

    // ==================== Multi-output ====================

    @Test
    void everyOutputOfAMultiOutputConversionIsTransformed() {
        StationAsset.Yield y = StationAsset.Yield.of(null, 2.0, null, null, null);
        Ingredient[] outputs = {Ingredient.item("Main", 3), Ingredient.item("Byproduct", 1)};
        Ingredient[] out = StationYield.applyToOutputs(y, outputs, 0.0, NEVER);
        assertEquals(6, out[0].effectiveQuantity());
        assertEquals("Main", out[0].getItemId());
        assertEquals(2, out[1].effectiveQuantity());
        assertEquals("Byproduct", out[1].getItemId());
    }
}
