package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PURE per-cycle output-quantity transform for {@link StationAsset.Yield} - the whole of "how
 * many items does this conversion actually hand over", with zero store access so it is unit-testable
 * without a live server (the same discipline {@link StationCustody} and {@code StampCapEngine}
 * follow).
 *
 * <p><b>Deterministic, end to end.</b> {@code quantity = floor(base * Scale)}, clamped into
 * {@code [max(1, Min), Max]}. There is no roll here and no injected randomness: an author reading a
 * {@code Yield} group sees exactly what a cycle makes. Everything conditional or probabilistic is a
 * {@code Roll} in the action's {@code Bonus} group instead, whose {@code Grants.OutputItems} adds
 * whole extra items of this same output on top - additive, so the two numbers stay commensurable.
 *
 * <p><b>The composition rule over a multi-output row.</b> {@code Base} replaces the PRIMARY (first)
 * output's own quantity and nothing else; every other output keeps the quantity its recipe
 * authored. {@code Scale}, {@code Min} and {@code Max} then apply to EVERY output. So a salvage row
 * giving two ore, a hide and a scrap keeps its hide and its scrap under {@code Yield.Base 1}, and a
 * single-output row (every plank recipe) reads exactly as it always has.
 *
 * <p>The {@link StationAsset.Yield#ABSOLUTE_MIN} floor sits under every path because a conversion
 * that consumed its inputs and produced nothing is item loss, not a tuning outcome. A null
 * {@code Yield} is the IDENTITY (the conversion's own authored quantity, untouched), so a recipe
 * authoring none behaves exactly as it did before the group existed.
 */
public final class StationYield {

    private StationYield() {
    }

    /**
     * The final whole-item quantity for a conversion's PRIMARY output: {@code floor(base * Scale)}
     * clamped into {@code [max(1, Min), Max]}, where {@code base} is the authored {@code Yield.Base}
     * when present and {@code authoredQuantity} (the conversion's own output quantity) otherwise. A
     * null {@code yield} returns {@code authoredQuantity} untouched.
     */
    public static int resolveQuantity(@Nullable StationAsset.Yield yield, int authoredQuantity) {
        return resolveQuantity(yield, authoredQuantity, true);
    }

    /**
     * As {@link #resolveQuantity(StationAsset.Yield, int)}, stating which output is being resolved:
     * {@code Base} applies only when {@code primary} is true; a secondary output keeps its own
     * authored quantity as the base and takes {@code Scale}/{@code Min}/{@code Max} alone.
     */
    public static int resolveQuantity(@Nullable StationAsset.Yield yield, int authoredQuantity, boolean primary) {
        if (yield == null) {
            return authoredQuantity;
        }
        Integer authoredBase = yield.getBase();
        int base = primary && authoredBase != null && authoredBase > 0 ? authoredBase : authoredQuantity;
        long quantity = (long) Math.floor(base * yield.effectiveScale());

        Integer min = yield.getMin();
        long floor = min != null ? Math.max(StationAsset.Yield.ABSOLUTE_MIN, min)
                : StationAsset.Yield.ABSOLUTE_MIN;
        if (quantity < floor) {
            quantity = floor;
        }
        Integer max = yield.getMax();
        if (max != null && max >= StationAsset.Yield.ABSOLUTE_MIN && quantity > max) {
            quantity = max;
        }
        return (int) Math.min(Integer.MAX_VALUE, quantity);
    }

    /**
     * {@code outputs} with every entry's quantity run through {@link #resolveQuantity}, or the SAME
     * array instance when {@code yield} is null (identity on the no-knob path, so the zero-authoring
     * case allocates nothing). {@code Base} lands on the first entry only; {@code Scale} and the
     * clamps apply to EVERY output of a multi-output conversion, deliberately: a recipe yielding a
     * main product plus a byproduct scales as one recipe, not one favoured item.
     */
    @Nonnull
    public static Ingredient[] applyToOutputs(@Nullable StationAsset.Yield yield,
            @Nonnull Ingredient[] outputs) {
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
            out[i] = Ingredient.item(in.getItemId(), resolveQuantity(yield, in.effectiveQuantity(), i == 0));
        }
        return out;
    }
}
