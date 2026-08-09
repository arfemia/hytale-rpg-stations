package com.ziggfreed.rpgstations.loot;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.FactorRef;

/**
 * The ONE ladder core in this schema: "sum the weighted factors, then pick the reached floor". Both
 * ladder-shaped consumers call it - a loot {@code Roll.Ladder} (via {@link RollEvaluator}) and an
 * action's {@code ContributionScale} (via {@code station.ContributionScaling}) - so identical JSON
 * behaves identically at either site instead of the two presenting one shape with divergent
 * semantics.
 *
 * <p><b>The three rules, stated once and applied everywhere:</b>
 * <ol>
 *   <li>An absent/empty {@code Factors} array resolves the ladder value to {@code 0.0}, so a
 *       constant-reward floor is authorable (rather than the whole ladder silently going dark).</li>
 *   <li>A floor's {@code Min} reader-defaults to {@code 0} and a {@code Min <= 0} floor is
 *       REACHABLE - an author writing a baseline tier gets one. Rejecting a malformed threshold is
 *       a validator's job, never the evaluator's.</li>
 *   <li>On two floors sharing a {@code Min}, the LAST authored one wins, matching every other
 *       later-wins rule in this schema (extension overlays, {@code defaults < pack} load order).
 *       The validator warns about the duplicate so the author is told rather than surprised.</li>
 * </ol>
 *
 * <p>Pure and store-free: the {@code lookup} is any {@code (factorId, param) -> Double} resolver, so
 * both consumers and their tests share one decision core with no live server.
 */
public final class FactorLadder {

    /** {@link #highestFloorIndex} sentinel: no floor was reached. */
    public static final int NO_FLOOR = -1;

    private FactorLadder() {
    }

    /**
     * The ladder VALUE for {@code factors}: the shared weighted sum
     * {@code sum(resolve(Factor, Param) * Weight)} via {@link FactorMath#sum}. A null/empty array is
     * {@code 0.0} (rule 1), and an unresolvable factor contributes {@code 0} (fail-closed).
     */
    public static double value(@Nullable FactorRef[] factors,
            @Nonnull BiFunction<String, String, Double> lookup) {
        return FactorMath.sum(factors, lookup);
    }

    /**
     * The index into {@code mins} of the reached floor - the HIGHEST {@code Min <= value}, breaking a
     * tie in favour of the LAST authored entry (rules 2 and 3) - or {@link #NO_FLOOR} when
     * {@code mins} is empty or no entry is reached. {@code mins} carries each floor's ALREADY
     * reader-defaulted threshold, so a caller's {@code effectiveMin()} is the single place the
     * null/non-finite default lives.
     *
     * <p>Deliberately UNCAPPED at the top: a floor above a factor's "normal" range stays reachable
     * through a multi-source stack.
     */
    public static int highestFloorIndex(@Nonnull double[] mins, double value) {
        int best = NO_FLOOR;
        double bestMin = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < mins.length; i++) {
            double min = mins[i];
            if (value >= min && min >= bestMin) {
                bestMin = min;
                best = i;
            }
        }
        return best;
    }
}
