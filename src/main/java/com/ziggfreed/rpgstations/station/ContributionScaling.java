package com.ziggfreed.rpgstations.station;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.rpgstations.asset.ContributionScale;

/**
 * The PURE resolution of an action's {@link ContributionScale} ladder into ONE multiplier.
 *
 * <p><b>Same rules as a loot roll's ladder, stated once here because this is the only other place
 * they apply:</b> the value is the weighted term sum (an absent or empty {@code Factors} resolves
 * to {@code 0.0}, and an unresolvable term contributes {@code 0}); a floor's {@code Min}
 * reader-defaults to {@code 0} and a {@code Min <= 0} floor IS reachable, so a baseline tier is
 * authorable; and two floors sharing a {@code Min} resolve to the LAST authored one, matching every
 * other later-wins rule in this schema. The validator warns about the duplicate rather than letting
 * the resolution silently pick.
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
     * no floor is reached, or the group carries no floors at all.
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
        double value = FactorFormula.sum(scale.getFactors(), lookup);
        ContributionScale.Floor reached = null;
        double reachedMin = Double.NEGATIVE_INFINITY;
        for (ContributionScale.Floor floor : floors) {
            if (floor == null) {
                // A null hole can never win; it names no threshold at all.
                continue;
            }
            double min = floor.effectiveMin();
            // ">=" on the running best is what hands an equal-Min tie to the LAST authored floor.
            if (value >= min && min >= reachedMin) {
                reachedMin = min;
                reached = floor;
            }
        }
        return reached == null ? ContributionScale.NEUTRAL_SCALE : reached.effectiveScale();
    }
}
