package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The PURE decision cores behind {@link StationStepRegistry}'s conditions gate,
 * {@link StationStepSemantics}'s Goto jump, and {@link StationStepHandlers.WaitHandler}'s
 * suspend/resume math - unit-tested with injected values (a {@link StationService.FactorLookup}
 * lambda, plain longs), zero store/engine access, mirroring {@code loot.RollEvaluator}'s role for
 * the conditional-lootable layer ("where the schema ambiguities were resolved into concrete
 * behavior"). Extracted so the {@code station.step} suspend/resume + branch contracts are
 * verifiable WITHOUT constructing a live {@link StationStepContext} (which needs a real
 * {@code Store}/{@code Player} this mod's test suite has no live-server fixture for).
 */
final class StationStepDecisions {

    private StationStepDecisions() {
    }

    /**
     * A {@code Wait} step's suspend/resume math (design 9.3's binding resume contract: derive the
     * deadline ONCE, never re-derive on re-entry). {@code storedDeadlineMs == 0} means "not yet
     * committed this suspension" - commits {@code nowMs + durationMs} and returns it; any other
     * value is read back VERBATIM (the durationMs argument is then irrelevant - a re-entry never
     * recomputes a fresh window).
     */
    static long commitOrReadDeadline(long nowMs, long durationMs, long storedDeadlineMs) {
        return storedDeadlineMs == 0L ? nowMs + durationMs : storedDeadlineMs;
    }

    /** Whether a previously committed {@code deadlineMs} has passed. */
    static boolean waitDue(long nowMs, long deadlineMs) {
        return nowMs >= deadlineMs;
    }

    /** A step's conditions-gate outcome (design 9.3's "Branch is NOT a step type" mechanism). */
    enum ConditionOutcome {
        /** No conditions authored, or every one passed - proceed to the type-specific handler. */
        PASS,
        /** A condition failed and {@code OnConditionFail.Result} is {@code "Skip"} - success-continuing no-op. */
        SKIP,
        /** A condition failed and {@code OnConditionFail.Result} is {@code "Fail"} (the default) - stop the walk. */
        FAIL
    }

    /**
     * Evaluate {@code conditions} against {@code lookup} (the SAME factor-lookup contract a
     * station's {@code Requires} gate uses - {@link StationService#conditionPasses}), in order,
     * short-circuiting on the first failure and resolving {@code onFail}'s effective result.
     */
    @Nonnull
    static ConditionOutcome resolveConditionOutcome(@Nullable Condition[] conditions,
            @Nullable StationStep.OnConditionFail onFail, @Nonnull StationService.FactorLookup lookup) {
        if (conditions == null || conditions.length == 0) {
            return ConditionOutcome.PASS;
        }
        for (Condition c : conditions) {
            if (c == null) {
                continue;
            }
            if (!StationService.conditionPasses(c, lookup)) {
                String effective = onFail != null ? onFail.effectiveResult() : StationStep.OnConditionFail.RESULT_FAIL;
                return StationStep.OnConditionFail.RESULT_SKIP.equals(effective) ? ConditionOutcome.SKIP : ConditionOutcome.FAIL;
            }
        }
        return ConditionOutcome.PASS;
    }

    /** The index of the step whose {@code Id} equals {@code gotoId} (case-insensitive), or -1 when absent/not found. */
    static int resolveGotoTarget(@Nonnull List<StationStep> steps, @Nullable String gotoId) {
        if (gotoId == null || gotoId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < steps.size(); i++) {
            StationStep s = steps.get(i);
            if (s != null && gotoId.equalsIgnoreCase(s.getId())) {
                return i;
            }
        }
        return -1;
    }
}
