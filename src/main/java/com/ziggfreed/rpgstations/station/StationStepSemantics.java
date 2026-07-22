package com.ziggfreed.rpgstations.station;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.cast.step.StepSemantics;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The consumer-supplied {@link StepSemantics} adapter wiring {@link StationStepContext}/
 * {@link StationStep}/{@link StationStepResult} into {@link StationStepKernel}'s
 * {@code CastKernel} instance (design section 9.3). Stateless singleton.
 *
 * <p><b>{@link #nextIndex} is the "Branch is NOT a step type" mechanism</b> (design 9.3): a step
 * whose {@link StationStep#getConditions()} failed AND whose
 * {@link StationStep.OnConditionFail#effectiveResult()} is {@code "Skip"} (the
 * {@link StationStepResult.Skip} case a guarded handler returns - see
 * {@link StationStepRegistry}) checks {@link StationStep.OnConditionFail#getGoto()}: authored ->
 * jump to that step's {@code Id} within the SAME program (an authored content-level branch,
 * unknown target id logs a warning and falls back to the classic linear advance); absent -> the
 * classic {@code currentIndex + 1} advance, same as every other success-continuing step.
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

    @Nullable
    @Override
    public String keyOf(@Nonnull StationStep step) {
        String type = step.getType();
        return type == null || type.isBlank() ? null : type.toLowerCase(Locale.ROOT);
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
        Log.warn("STATION step program '" + ctx.action.getActionId() + "' at station '" + ctx.session.stationId
                + "' has no registered handler for step Type '" + step.getType() + "' (Id '" + step.getId() + "')");
        return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                "unhandled step type '" + step.getType() + "'");
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
