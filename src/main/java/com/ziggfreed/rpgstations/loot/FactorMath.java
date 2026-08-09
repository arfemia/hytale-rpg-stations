package com.ziggfreed.rpgstations.loot;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.FactorRef;

/**
 * The ONE weighted-sum authority for a {@link FactorRef}{@code []} (scope-2 design 1.3/4.2): the
 * flat composition {@code sum(resolve(Factor, Param) * Weight)} every consumer of the unified
 * factor vocabulary uses - {@code Roll.Chance.Factors}, {@code Roll.Ladder.Factors},
 * {@code StatRollEntry.Points.Factors}, {@code StationStep.Stamp.Stats.Caps.Budgets[].Factors},
 * and {@code StationStep.Repeat.Factors}. Pure, unit-testable, no store access; the {@code
 * lookup} is any {@code (factorId, param) -> Double} resolver (a {@code FactorSnapshot::resolve},
 * a fixture map, etc.). An unresolvable factor (a {@code null} lookup result) contributes 0
 * (fail-closed), never a throw. Kept in {@code loot/} so both the loot layer ({@code RollEvaluator})
 * and the station layer ({@code StampCapEngine}/{@code StationStepDecisions}) share one authority.
 */
public final class FactorMath {

    private FactorMath() {
    }

    /**
     * {@code sum over f in factors of (resolve(f.Factor, f.Param) ?? 0) * f.Weight}. A null/empty
     * array is 0; a factor with a blank {@code Factor} id is skipped; a null resolved value is
     * treated as 0.
     */
    public static double sum(@Nullable FactorRef[] factors,
            @Nonnull BiFunction<String, String, Double> lookup) {
        if (factors == null || factors.length == 0) {
            return 0.0;
        }
        double total = 0.0;
        for (FactorRef f : factors) {
            if (f == null || f.getFactor() == null || f.getFactor().isBlank()) {
                continue;
            }
            Double v = lookup.apply(f.getFactor(), f.getParam());
            total += (v != null ? v : 0.0) * f.effectiveWeight();
        }
        return total;
    }
}
