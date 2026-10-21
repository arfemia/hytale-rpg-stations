package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The PURE rules of a conversion {@code Batch} ({@code Recipe.Batch}, a {@code Convert} beat's own
 * {@code Convert.Batch}): how many conversions one cycle runs, and what they take.
 *
 * <p><b>The input rule.</b> Every conversion of a batch is the ONE matched row, run again: its inputs are that row's
 * own entries with their quantities multiplied ({@link #times}), every route and socket address unchanged, so a batch
 * takes more of the very item, family or tag the row converts and never another material. A batch of logs of one
 * species saws more of that species; it never reaches into a second species standing in the same pile.
 *
 * <p><b>The count.</b> The beat's own {@code Batch} wins over the recipe's ({@link #batchOf}); it resolves through the
 * {@code Repeat} shape's one rule ({@link StationStep.Repeat#resolveCount}) against the worker, and reads its authored
 * count (Times, else Min) where there is nobody to ask. A batch never runs more conversions than the pile or the bag
 * can feed, and never fewer than the one the selection already found runnable ({@link #count}).
 */
final class StationBatch {

    private StationBatch() {
    }

    /** The batch a Convert beat runs under: its own {@code Convert.Batch}, else the recipe's, else null (one). */
    @Nullable
    static StationStep.Repeat batchOf(@Nullable StationStep.Convert convert, @Nullable StationAsset.Recipe recipe) {
        StationStep.Repeat own = convert != null ? convert.getBatch() : null;
        if (own != null) {
            return own;
        }
        return recipe != null ? recipe.getBatch() : null;
    }

    /**
     * The batch's size for the worker {@code lookup} stands for, never below 1; a null {@code lookup} (an unattended
     * settle) reads the authored count, the factors contributing nothing.
     */
    static int resolve(@Nullable StationStep.Repeat batch, @Nullable FactorLookup lookup) {
        if (batch == null) {
            return 1;
        }
        double contribution = lookup == null ? 0.0 : StationStepDecisions.repeatFactorContribution(batch, lookup::resolve);
        return StationStepDecisions.resolveRepeatCount(batch, contribution);
    }

    /**
     * How many conversions actually run: the resolved {@code batch} held to what the inputs can {@code feed}, and at
     * least 1 (the selection found one runnable before the batch was asked, so a feed reading of 0, a count the
     * caller could not make, still runs that one).
     */
    static int count(int batch, int feed) {
        return Math.max(1, Math.min(Math.max(1, batch), feed));
    }

    /**
     * How many cycles a pile that feeds {@code feedable} single conversions lasts under {@code batch} conversions a
     * cycle: the last cycle runs whatever is left ({@link #count}), so it is the share rounded up. {@code feedable}
     * unchanged for a batch of 1 or less, or a non-positive reading.
     */
    static int cyclesLeft(int feedable, int batch) {
        if (batch <= 1 || feedable <= 0) {
            return feedable;
        }
        return feedable / batch + (feedable % batch == 0 ? 0 : 1);
    }

    /**
     * {@code each} with every entry's quantity multiplied by {@code times}, each copy carrying its entry's routes and
     * socket unchanged ({@link Ingredient#withQuantity}); the SAME array for a {@code times} of 1 or less, so a
     * batch of one costs nothing. A null entry stays null.
     */
    @Nonnull
    static Ingredient[] times(@Nonnull Ingredient[] each, int times) {
        if (times <= 1) {
            return each;
        }
        Ingredient[] out = new Ingredient[each.length];
        for (int i = 0; i < each.length; i++) {
            Ingredient in = each[i];
            out[i] = in == null ? null : in.withQuantity((int) Math.min(Integer.MAX_VALUE,
                    (long) in.effectiveQuantity() * times));
        }
        return out;
    }
}
