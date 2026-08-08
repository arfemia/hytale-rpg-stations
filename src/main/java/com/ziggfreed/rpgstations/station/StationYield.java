package com.ziggfreed.rpgstations.station;

import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.loot.FactorMath;

/**
 * The PURE per-cycle output-quantity transform for {@link StationAsset.Yield} - the whole of "how
 * many items does this conversion actually hand over", with zero store access so it is unit-testable
 * without a live server (the same discipline {@link StationCustody} and {@code StampCapEngine}
 * follow).
 *
 * <p><b>Why this is resolved per cycle and not baked at fold time.</b> A derived conversion's
 * quantity could be stamped once during {@link StationRecipeDeriver}'s asset-load derivation, and the
 * retired {@code FromCrafting.OutputPerInput} leaf did exactly that. A yield that keys off the
 * WORKER's held tool cannot: the tool is re-read every cycle (it can change mid-session, and the
 * session's own tool gate only guarantees the tool still MATCHES, not that it is the same item). So
 * the transform belongs at the one point a conversion becomes a live produce phase, which is also
 * why it reads a ladder value the caller resolved from the same per-cycle
 * {@code FactorSnapshot} the loot rolls use - one factor resolution, two consumers.
 *
 * <p>Ordering is fixed and documented on {@link StationAsset.Yield}: base, scale, bonus, clamp. The
 * {@link StationAsset.Yield#ABSOLUTE_MIN} floor sits under every path because a conversion that
 * consumed its inputs and produced nothing is item loss, not a tuning outcome.
 */
public final class StationYield {

    private StationYield() {
    }

    /**
     * The summed bonus-ladder value for {@code yield} (the shared weighted-{@code FactorRef}
     * composition via {@link FactorMath#sum}), or 0 when no {@code Bonus.Values} are authored.
     * Separated from {@link #resolveQuantity} so the caller resolves factors ONCE per cycle and
     * applies the result to every output of a multi-output conversion.
     */
    public static double ladderValue(@Nullable StationAsset.Yield yield,
            @Nonnull BiFunction<String, String, Double> lookup) {
        StationAsset.Yield.Bonus bonus = yield != null ? yield.getBonus() : null;
        return bonus == null ? 0.0 : FactorMath.sum(bonus.getValues(), lookup);
    }

    /**
     * The extra items {@code yield}'s bonus ladder contributes at {@code ladderValue}: the HIGHEST
     * floor whose {@code Min} is reached wins (floors are NOT cumulative - authoring
     * {@code [{Min:5,Add:1},{Min:9,Add:2}]} means "+1 from 5, +2 from 9", never +3). May be
     * FRACTIONAL; 0 when no bonus/floors are authored or no floor is reached.
     */
    public static double bonusAdd(@Nullable StationAsset.Yield yield, double ladderValue) {
        StationAsset.Yield.Bonus bonus = yield != null ? yield.getBonus() : null;
        StationAsset.Yield.Floor[] floors = bonus != null ? bonus.getFloors() : null;
        if (floors == null || floors.length == 0) {
            return 0.0;
        }
        double add = 0.0;
        double bestMin = Double.NEGATIVE_INFINITY;
        for (StationAsset.Yield.Floor floor : floors) {
            if (floor == null) {
                continue;
            }
            double min = floor.effectiveMin();
            if (ladderValue >= min && min >= bestMin) {
                bestMin = min;
                add = floor.effectiveAdd();
            }
        }
        return add;
    }

    /**
     * The EXACT (possibly fractional) quantity for one output before the remainder is resolved:
     * {@code clamp(base * Scale + bonusAdd, Min, Max)} with the
     * {@link StationAsset.Yield#ABSOLUTE_MIN} floor underneath, where {@code base} is the authored
     * {@code Yield.Base} when present and {@code authoredQuantity} (the conversion's own output
     * quantity) otherwise. Exposed separately from {@link #resolveQuantity} so a caller (and a test)
     * can read the authored intent without consuming a roll.
     */
    public static double exactQuantity(@Nonnull StationAsset.Yield yield, int authoredQuantity,
            double ladderValue) {
        Integer authoredBase = yield.getBase();
        int base = authoredBase != null && authoredBase > 0 ? authoredBase : authoredQuantity;
        double total = base * yield.effectiveScale() + bonusAdd(yield, ladderValue);

        Integer min = yield.getMin();
        double floor = min != null ? Math.max(StationAsset.Yield.ABSOLUTE_MIN, min)
                : StationAsset.Yield.ABSOLUTE_MIN;
        if (total < floor) {
            total = floor;
        }
        Integer max = yield.getMax();
        if (max != null && max >= StationAsset.Yield.ABSOLUTE_MIN && total > max) {
            total = max;
        }
        return total;
    }

    /**
     * The final whole-item quantity for ONE output: {@link #exactQuantity}'s whole part, plus one
     * more item when its FRACTIONAL remainder wins a roll from {@code remainderRoll} (a
     * {@code [0,1)} supplier - {@code 2.5} pays 2 always and a 3rd half the time, so the long-run
     * average is exactly the authored number). A null {@code yield} returns {@code authoredQuantity}
     * untouched and consumes NO roll, so a station authoring no {@code Yield} group is
     * byte-identical to pre-knob behavior; an exact whole number consumes no roll either.
     */
    public static int resolveQuantity(@Nullable StationAsset.Yield yield, int authoredQuantity,
            double ladderValue, @Nonnull DoubleSupplier remainderRoll) {
        if (yield == null) {
            return authoredQuantity;
        }
        double exact = exactQuantity(yield, authoredQuantity, ladderValue);
        long whole = (long) Math.floor(exact);
        double remainder = exact - whole;
        if (remainder > 0.0 && remainderRoll.getAsDouble() < remainder) {
            whole++;
        }
        // The absolute floor is re-asserted AFTER the roll: a sub-1 exact value (e.g. Scale 0.4)
        // floors to 0 whole items and can lose its remainder roll, which would be item loss.
        if (whole < StationAsset.Yield.ABSOLUTE_MIN) {
            whole = StationAsset.Yield.ABSOLUTE_MIN;
        }
        return (int) Math.min(Integer.MAX_VALUE, whole);
    }

    /**
     * {@code outputs} with every entry's quantity run through {@link #resolveQuantity}, or the SAME
     * array instance when {@code yield} is null (identity on the no-knob path, so the zero-authoring
     * case allocates nothing). Applies to EVERY output of a multi-output conversion, deliberately:
     * a recipe yielding a main product plus a byproduct scales as one recipe, not one favoured item.
     * Each output rolls its OWN remainder, so a fractional yield does not correlate a byproduct's
     * extra item with the main product's.
     */
    @Nonnull
    public static Ingredient[] applyToOutputs(@Nullable StationAsset.Yield yield,
            @Nonnull Ingredient[] outputs, double ladderValue, @Nonnull DoubleSupplier remainderRoll) {
        if (yield == null) {
            return outputs;
        }
        Ingredient[] out = new Ingredient[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            Ingredient in = outputs[i];
            if (in == null) {
                out[i] = null;
                continue;
            }
            out[i] = Ingredient.item(in.getItemId(),
                    resolveQuantity(yield, in.effectiveQuantity(), ladderValue, remainderRoll));
        }
        return out;
    }
}
