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

    // ==================== Generic per-step Presentation entry (maintainer-approved extension) ====================

    /**
     * Whether a step's authored {@code Presentation} should play GENERICALLY as this handler call
     * begins - the maintainer-approved wiring that makes ANY step type's own {@code Presentation}
     * fire once, not just the dedicated {@code Present} step's. {@code false} when {@code step} IS
     * {@code resumingStep} (the suspend-resume RE-CHECK of the exact step that already played its
     * Presentation on its first entry - a {@code Wait} step must never replay it on every
     * heartbeat re-check while suspended, only once when it BEGINS); {@code false} for a
     * {@code "Present"}-typed step (its own {@code PresentHandler} already emits this exact
     * Presentation - this generic hook would otherwise double-play it). {@code resumingStep} is
     * {@code null} for a fresh (non-resuming) dispatch, so every step it walks answers
     * {@code true} here (subject to the Present-type exclusion). Identity comparison (never
     * {@code equals}) - {@code resumingStep} and a later step in the SAME authored steps list are
     * the exact same object reference across a suspend/resume pair (see
     * {@code StationSession#activeProgramSteps}), so {@code ==} is the correct, intentional test.
     */
    static boolean shouldEmitPresentationOnEntry(@Nonnull StationStep step, @Nullable StationStep resumingStep) {
        if (step == resumingStep) {
            return false;
        }
        return !StationStep.TYPE_PRESENT.equalsIgnoreCase(step.getType());
    }

    // ==================== Step-synced puppet swings (maintainer-approved, round-8) ====================

    /**
     * Whether a step's own authored {@code Puppet.Clip} should play ONCE on the session's puppet as
     * this handler call begins EXECUTING - the step-synced swing, mirroring
     * {@link #shouldEmitPresentationOnEntry}'s once-per-ITERATION-entry, never-on-resume-recheck
     * semantics (the trigger is per-iteration-entry BY CONSTRUCTION, so future step repetition -
     * N or stat-driven N-M iterations - fires one clip per iteration for free). {@code false} when
     * {@code step} IS {@code resumingStep} (the suspend-resume RE-CHECK of a step that already
     * played its clip on its first entry - a {@code Wait} step must never replay it on every
     * heartbeat re-check while suspended, only once when it BEGINS). {@code false} when the step
     * authors no non-blank {@code Puppet.Clip} (nothing to play - the step either inherits the
     * action's default clip, which the GENERIC engage/swing beat owns for a non-clip program, or
     * simply idles, e.g. the anvil's {@code settle} step). Identity comparison (never
     * {@code equals}), for the SAME reason {@link #shouldEmitPresentationOnEntry} uses it -
     * {@code resumingStep} and a later step in the same authored steps list are the exact same
     * object reference across a suspend/resume pair.
     */
    static boolean shouldPlayClipOnEntry(@Nonnull StationStep step, @Nullable StationStep resumingStep) {
        if (step == resumingStep) {
            return false;
        }
        return stepAuthorsClip(step);
    }

    /**
     * Whether ANY step in an authored program authors a non-blank per-step {@code Puppet.Clip} -
     * the gate {@code StationService} resolves ONCE at engage to decide whether to SUPPRESS the
     * generic engage/swing puppet clip (round-8): a stepped program whose steps author clips drives
     * the puppet ENTIRELY from those per-step-entry clips, so the generic per-cycle swing must not
     * double-fire on top of them. {@code false} for a null/empty list or a program whose every step
     * inherits the action's default clip - that program KEEPS its one generic engage swing (no step
     * clip ever fires at entry, so nothing would drive the puppet otherwise).
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
     * Whether the puppet's held PROP should be re-synced to THIS step's own effective prop the
     * moment the step begins EXECUTING - the step-synced prop swap (round-8 continuation, mirroring
     * {@link #shouldPlayClipOnEntry}'s once-per-ITERATION-entry, never-on-resume-recheck trigger via
     * the SAME {@link StationStepContext#resumingStep} identity guard). {@code false} when
     * {@code step} IS {@code resumingStep} (the suspend-resume RE-CHECK of a step that already
     * synced its prop on its first entry - the swing-beat suspension-gated sync
     * ({@code StationPuppetController#playSwing}'s {@code activeStepPuppetOverride} read) HOLDS the
     * suspended step's prop across every heartbeat while it waits, so re-syncing it on each re-check
     * would be redundant, exactly as replaying its clip would be); {@code true} for every FRESH step
     * entry (identity comparison, never {@code equals}, for the same object-reference reason the
     * clip/Presentation hooks use it).
     *
     * <p><b>Deliberately NOT gated on the step authoring a prop override</b> (the load-bearing
     * difference from {@link #shouldPlayClipOnEntry}, which DOES require an authored clip). Every
     * fresh entry syncs the prop to the step's own effective resolution - the step's {@code
     * Puppet.Prop} override when it authors one, ELSE the session default ({@code
     * StationPuppetController#syncProp}'s own {@code override != null && override.getProp() != null ?
     * override.getProp() : puppetDefaultProp} fallback). This ONE rule handles both edges with no
     * separate revert path: entering a prop-overriding step swaps TO the override (e.g. the anvil's
     * {@code stamp} step's {@code Prop.Source:"None"} empties the puppet's hands), and entering ANY
     * later step that authors no override reverts the prop BACK to the session default - the exit
     * edge, made consistent with how the swing-beat suspension-gated sync already reverts to the
     * default whenever the program is not suspended on an override-bearing step. A gate on "authors
     * an override" would leave the prop STUCK on a spent override once the program moves past that
     * step (a non-override next step would do nothing), which is the bug this rule avoids. The sync
     * itself is dirty-gated ({@code PlayerPuppetService#updateHeldItem} only mutates when the
     * resolved item id actually CHANGED), so a same-prop entry is a cheap no-op.
     */
    static boolean shouldSyncPropOnEntry(@Nonnull StationStep step, @Nullable StationStep resumingStep) {
        return step != resumingStep;
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
