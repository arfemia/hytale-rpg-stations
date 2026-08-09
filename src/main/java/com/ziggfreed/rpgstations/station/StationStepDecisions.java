package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.entity.performer.WalkHandle;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.FactorMath;

/**
 * The PURE decision cores behind {@link StationStepRegistry}'s conditions gate,
 * {@link StationStepSemantics}'s Goto jump, and the composite step handler's {@code Repeat} +
 * {@code Duration} math - unit-tested with injected values (a {@link StationService.FactorLookup}
 * lambda, plain longs), zero store/engine access, mirroring {@code loot.RollEvaluator}'s role for
 * the conditional-lootable layer. Extracted so the {@code station.step} suspend/resume + branch +
 * repeat contracts are verifiable WITHOUT constructing a live {@link StationStepContext}.
 */
final class StationStepDecisions {

    private StationStepDecisions() {
    }

    // ==================== Duration suspend/resume (scope-2: a Duration hold reuses the retired Wait math verbatim) ====================

    /**
     * A {@code Duration} hold's suspend/resume math (design 2.1's binding resume contract, reusing
     * the retired {@code Wait} step's math VERBATIM: derive the deadline ONCE, never re-derive on
     * re-entry). {@code storedDeadlineMs == 0} means "not yet committed this hold" - commits
     * {@code nowMs + durationMs} and returns it; any other value is read back VERBATIM (the
     * durationMs argument is then irrelevant - a re-entry never recomputes a fresh window).
     */
    static long commitOrReadDeadline(long nowMs, long durationMs, long storedDeadlineMs) {
        return storedDeadlineMs == 0L ? nowMs + durationMs : storedDeadlineMs;
    }

    /** Whether a previously committed {@code deadlineMs} has passed. */
    static boolean durationDue(long nowMs, long deadlineMs) {
        return nowMs >= deadlineMs;
    }

    // ==================== Repeat resolution (scope-2 design 2.1, decision 29c) ====================

    /**
     * A step's per-step iteration count (design 2.1): {@code Repeat.Times} (fixed), or
     * {@code clamp(round(Min + factorContribution), Min, Max)} via {@code Repeat.Factors} - the
     * caller supplies {@code factorContribution} (already {@code sum(resolve(f) * f.Weight)}, e.g.
     * via {@link #repeatFactorContribution}). A {@code null} {@code repeat} = a single iteration.
     * Never returns below 1. Delegates to {@link StationStep.Repeat#resolveCount(double)}, the
     * schema's own pure core.
     */
    static int resolveRepeatCount(@Nullable StationStep.Repeat repeat, double factorContribution) {
        if (repeat == null) {
            return 1;
        }
        return repeat.resolveCount(factorContribution);
    }

    /**
     * The {@code sum(resolve(f.Factor, f.Param) * f.Weight)} contribution a {@code Repeat}'s
     * {@code Factors} weighted references add to its {@code Min} floor (0 for a fixed
     * {@code Times}, an absent {@code Factors}, or a null {@code repeat}) - the ONE weighted-sum
     * vocabulary ({@link FactorMath}) every factor-consuming array shares.
     */
    static double repeatFactorContribution(@Nullable StationStep.Repeat repeat,
            @Nonnull BiFunction<String, String, Double> lookup) {
        if (repeat == null || repeat.isFixed()) {
            return 0.0;
        }
        return FactorMath.sum(repeat.getFactors(), lookup);
    }

    // ==================== Conditions gate (design 2.1's "Branch is NOT a step type" mechanism) ====================

    /** A step's conditions-gate outcome. */
    enum ConditionOutcome {
        /** No conditions authored, or every one passed - proceed to the phase walk. */
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

    // ==================== Per-iteration-entry presentation + puppet cues (round-8, carried forward) ====================

    /**
     * Whether a step's authored {@code Presentation} should play as an ITERATION begins - scope-2
     * fires it once per fresh iteration entry (never on the suspend-resume RE-CHECK of a
     * {@code Duration}-holding iteration, which the composite handler detects internally and skips).
     * With the {@code Type} union gone there is no longer a dedicated {@code Present} step to
     * exclude; the ONE remaining exclusion is the resume re-check ({@code step == resumingStep}),
     * identity-compared (never {@code equals} - {@code resumingStep} and a later occurrence of the
     * same step object are the exact same reference across a suspend/resume pair).
     */
    static boolean shouldEmitPresentationOnEntry(@Nonnull StationStep step, @Nullable StationStep resumingStep) {
        return step != resumingStep && step.getPresentation() != null;
    }

    /**
     * Whether a step's own authored {@code Puppet.Clip} should play ONCE on the session's puppet as
     * an ITERATION begins (the step-synced swing). {@code false} on the suspend-resume RE-CHECK
     * ({@code step == resumingStep}, identity-compared) and when the step authors no non-blank
     * {@code Puppet.Clip} (nothing to play - the step inherits the action's default clip or idles).
     */
    static boolean shouldPlayClipOnEntry(@Nonnull StationStep step, @Nullable StationStep resumingStep) {
        if (step == resumingStep) {
            return false;
        }
        return stepAuthorsClip(step);
    }

    /**
     * Whether ANY step in an authored program authors a non-blank per-step {@code Puppet.Clip} -
     * the gate {@code StationService} resolves ONCE at engage to SUPPRESS the generic engage/swing
     * puppet clip (round-8): a stepped program whose steps author clips drives the puppet ENTIRELY
     * from those per-step-entry clips, so the generic per-cycle swing must not double-fire. {@code
     * false} for a null/empty list or a program whose every step inherits the action's default clip.
     */
    static boolean programAuthorsAnyStepClip(@Nullable List<StationStep> steps) {
        if (steps == null) {
            return false;
        }
        for (StationStep step : steps) {
            if (step != null && stepAuthorsClip(step)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code step} authors a non-blank {@code Puppet.Clip} override (the raw predicate both hooks share). */
    private static boolean stepAuthorsClip(@Nonnull StationStep step) {
        StationStep.PuppetOverride puppet = step.getPuppet();
        String clip = puppet != null ? puppet.getClip() : null;
        return clip != null && !clip.isBlank();
    }

    /**
     * Whether the puppet's held PROP should be re-synced to THIS step's own effective prop as an
     * ITERATION begins (the step-synced prop swap). {@code false} on the suspend-resume RE-CHECK
     * ({@code step == resumingStep}); {@code true} for every FRESH iteration entry. Deliberately
     * NOT gated on the step authoring an override: every fresh entry syncs to the step's effective
     * prop (its {@code Puppet.Prop} override when authored, ELSE the session default), so moving
     * PAST a prop-overriding step reverts the prop to the default - one rule, no separate revert
     * path. The sync itself is dirty-gated (a same-prop entry is a cheap no-op).
     */
    static boolean shouldSyncPropOnEntry(@Nonnull StationStep step, @Nullable StationStep resumingStep) {
        return step != resumingStep;
    }

    // ==================== Walk phase completion (seam route, F2) ====================

    /**
     * Whether an in-flight {@code Walk} phase is DONE this frame (scope-2 wave 3, decision 55 seam
     * route): a still-{@link WalkHandle.State#WALKING} handle keeps driving UNLESS design 2.3's
     * anti-wedge {@code timedOut} guard fired; {@link WalkHandle.State#ARRIVED},
     * {@link WalkHandle.State#STUCK}, and {@link WalkHandle.State#FAILED} all complete the step -
     * never wedge the program. STUCK/FAILED completing-as-arrived mirrors the pre-seam behavior
     * exactly (the old {@code advanceWalk} returned "done" on a gone puppet and on the distance/speed
     * timeout), so a stuck NPC or a lost Holder ends the walk gracefully rather than suspending
     * forever.
     */
    static boolean walkStepDone(@Nonnull WalkHandle.State state, boolean timedOut) {
        if (state == WalkHandle.State.WALKING) {
            return timedOut;
        }
        return true;
    }

    // ==================== Goto branch target ====================

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
