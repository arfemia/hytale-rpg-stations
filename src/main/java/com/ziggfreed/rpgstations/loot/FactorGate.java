package com.ziggfreed.rpgstations.loot;

import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.factor.FactorCondition;

/**
 * The ONE bound-gate authority for a {@code Conditions} array evaluated through an injected
 * {@code (factorId, param) -> Double} lookup - the gate twin of {@link FactorMath}'s weighted sum,
 * and kept beside it for the same reason: both the loot layer ({@code RollEvaluator}) and the
 * station layer ({@code StationService}'s {@code Requires} gate, {@code StationStepDecisions}'
 * step gate) share one decision, so identical JSON behaves identically wherever it is authored.
 *
 * <p>The bound TABLE itself is not written here: it is {@link FactorCondition#accepts}, the shared
 * leaf's own test, so an authored {@code Min}/{@code Max} means exactly the same thing in this mod
 * as in every other engine reading the same vocabulary. What this class adds is the array walk:
 *
 * <ul>
 *   <li>a null/empty array passes vacuously (no gate authored);</li>
 *   <li>a BLANK entry (no {@code Factor} id) is skipped rather than failing - a half-authored line
 *       is an authoring slip, and hiding working content behind it is far harder to diagnose than
 *       the validator finding that reports it;</li>
 *   <li>everything else fails CLOSED: an unresolvable factor (the lookup answers {@code null}) and
 *       a value outside the authored bounds both shut the gate.</li>
 * </ul>
 *
 * <p>Pure and store-free: the lookup is any resolver (a memoized {@code FactorSnapshot::resolve},
 * a fixture map), so every gate path is unit-testable with no live server.
 */
public final class FactorGate {

    private FactorGate() {
    }

    /**
     * The factor id of the FIRST entry that did not pass, or {@code null} when every entry passed
     * (or there was nothing to evaluate). A caller that wants to NAME the factor that shut the gate
     * uses this; one that only needs the verdict uses {@link #pass}.
     */
    @Nullable
    public static String firstFailure(@Nullable FactorCondition[] conditions,
            @Nonnull BiFunction<String, String, Double> lookup) {
        if (conditions == null || conditions.length == 0) {
            return null;
        }
        for (FactorCondition condition : conditions) {
            if (!passes(condition, lookup)) {
                return condition.getFactor();
            }
        }
        return null;
    }

    /** True when every entry passed - the boolean wrapper over {@link #firstFailure}. */
    public static boolean pass(@Nullable FactorCondition[] conditions,
            @Nonnull BiFunction<String, String, Double> lookup) {
        return firstFailure(conditions, lookup) == null;
    }

    /** ONE entry: a null or blank-factor entry passes vacuously, everything else fails closed. */
    public static boolean passes(@Nullable FactorCondition condition,
            @Nonnull BiFunction<String, String, Double> lookup) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        return condition.accepts(lookup.apply(condition.getFactor(), condition.getParam()));
    }
}
