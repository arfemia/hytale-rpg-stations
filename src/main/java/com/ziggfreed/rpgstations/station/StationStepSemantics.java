package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.cast.step.StepSemantics;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The consumer-supplied {@link StepSemantics} adapter wiring {@link StationStepContext}/
 * {@link StationStep}/{@link StationStepResult} into {@link StationStepKernel}'s {@code CastKernel}
 * instance. Stateless singleton.
 *
 * <p><b>Scope-2 (design 2.1): the {@code Type} union is GONE.</b> Every step is now an
 * orthogonal-phase record dispatched to the ONE composite handler
 * ({@link StationStepRegistry#STEP_KEY}), so {@link #keyOf} returns that single constant key for
 * EVERY step (never a per-type discriminator).
 *
 * <p><b>{@link #nextIndex} is the "Branch is NOT a step type" mechanism</b> (design 2.1): a step
 * whose {@link StationStep#getConditions()} failed AND whose
 * {@link StationStep.OnConditionFail#effectiveResult()} is {@code "Skip"} (the
 * {@link StationStepResult.Skip} case the guard returns - see {@link StationStepRegistry}) checks
 * {@link StationStep.OnConditionFail#getGoto()}: authored -&gt; jump to that step's {@code Id}
 * within the SAME program (an authored content-level branch, unknown target id logs a warning and
 * falls back to the classic linear advance); absent -&gt; the classic {@code currentIndex + 1}
 * advance, same as every other success-continuing step.
 */
final class StationStepSemantics implements StepSemantics<StationStepContext, StationStep, String, StationStepResult> {

    static final StationStepSemantics INSTANCE = new StationStepSemantics();

    private StationStepSemantics() {
    }

    @Nonnull
    @Override
    public Iterable<StationStep> stepsOf(@Nonnull StationStepContext ctx) {
        return ctx.steps;
    }

    @Nonnull
    @Override
    public String keyOf(@Nonnull StationStep step) {
        // The Type union is gone (scope-2): one composite handler serves every step.
        return StationStepRegistry.STEP_KEY;
    }

    @Override
    public boolean isSuccess(@Nonnull StationStepResult result) {
        return result instanceof StationStepResult.Success || result instanceof StationStepResult.Skip;
    }

    @Nonnull
    @Override
    public StationStepResult successResult(@Nonnull StationStepContext ctx) {
        return StationStepResult.SUCCESS;
    }

    @Nonnull
    @Override
    public StationStepResult onMissingHandler(@Nonnull StationStepContext ctx, @Nonnull StationStep step,
            @Nullable String key) {
        // Unreachable in practice (keyOf always returns the one registered key); guarded per the
        // kernel's contract so a registry misconfiguration degrades to a clean stop, never a crash.
        Log.warn("STATION step program '" + ctx.action.getActionId() + "' at station '" + ctx.session.stationId
                + "' has no registered handler for key '" + key + "' (Id '" + step.getId() + "')");
        return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                "unhandled step key '" + key + "'");
    }

    @Override
    public boolean isSuspend(@Nonnull StationStepResult result) {
        return result instanceof StationStepResult.Suspend;
    }

    @Override
    public int nextIndex(@Nonnull StationStepContext ctx, @Nonnull StationStep step, int currentIndex,
            @Nonnull StationStepResult result) {
        if (!(result instanceof StationStepResult.Skip)) {
            return currentIndex + 1;
        }
        StationStep.OnConditionFail onFail = step.getOnConditionFail();
        String gotoId = onFail != null ? onFail.getGoto() : null;
        int target = StationStepDecisions.resolveGotoTarget(ctx.steps, gotoId);
        if (target < 0) {
            if (gotoId != null && !gotoId.isBlank()) {
                Log.warn("STATION step program '" + ctx.action.getActionId() + "' at station '"
                        + ctx.session.stationId + "' step '" + step.getId()
                        + "' OnConditionFail.Goto references unknown step id '" + gotoId
                        + "' - falling back to the next step");
            }
            return currentIndex + 1;
        }
        return target;
    }
}
