package com.ziggfreed.rpgstations.station;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.loot.FactorLadder;

/**
 * The PURE resolution of an action's {@link ContributionScale} ladder into ONE multiplier, over the
 * SAME {@link FactorLadder} core every other ladder in this schema uses - so identical
 * {@code {Factors, Floors}} JSON behaves identically wherever it is authored.
 *
 * <p><b>The engine pre-scales.</b> {@code StationService} multiplies every per-cycle contribution
 * amount by this number BEFORE the cycle-completed event is dispatched, and the event carries the
 * multiplier for DISPLAY only. A listener that forgot to multiply therefore cannot under-award, and
 * one that multiplied again cannot over-award: the amount on the event is always the amount to
 * grant.
 *
 * <p>Store-free, so it unit-tests with a plain injected factor lookup.
 */
public final class ContributionScaling {

    private ContributionScaling() {
    }

    /**
     * The multiplier for {@code scale} at the factor values {@code lookup} resolves: the reached
     * floor's {@code Scale}, or {@link ContributionScale#NEUTRAL_SCALE} when no ladder is authored,
     * no floor is reached, or the group carries no floors at all. Ladder semantics are
     * {@link FactorLadder}'s: an absent/empty {@code Factors} resolves the value to {@code 0.0}, a
     * floor {@code Min} reader-defaults to {@code 0} and a {@code Min <= 0} floor IS reachable, and
     * an equal-{@code Min} tie goes to the LAST authored floor.
     */
    public static double multiplier(@Nullable ContributionScale scale,
            @Nonnull BiFunction<String, String, Double> lookup) {
        if (scale == null) {
            return ContributionScale.NEUTRAL_SCALE;
        }
        ContributionScale.Floor[] floors = scale.getFloors();
        if (floors == null || floors.length == 0) {
            return ContributionScale.NEUTRAL_SCALE;
        }
        double value = FactorLadder.value(scale.getFactors(), lookup);
        double[] mins = new double[floors.length];
        for (int i = 0; i < floors.length; i++) {
            // A null hole can never win: push it above every reachable value.
            mins[i] = floors[i] != null ? floors[i].effectiveMin() : Double.POSITIVE_INFINITY;
        }
        int index = FactorLadder.highestFloorIndex(mins, value);
        return index == FactorLadder.NO_FLOOR
                ? ContributionScale.NEUTRAL_SCALE : floors[index].effectiveScale();
    }
}
