package com.ziggfreed.rpgstations.station;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.cast.step.StepHandler;
import com.ziggfreed.common.cast.step.StepRegistry;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The production {@code station.step} handler registry: registers the six executable handlers
 * (design 9.3), each wrapped in TWO layers every registrant goes through, never bypassed by a
 * handler body:
 *
 * <ol>
 *   <li><b>Conditions gate.</b> A step's {@link StationStep#getConditions()} (if any) are checked
 *   FIRST, against the same factor-lookup contract {@code StationService.conditionPasses} already
 *   uses for a station's {@code Requires} gate - one condition semantics, everywhere a condition
 *   is authored. A failing condition resolves via {@link StationStep.OnConditionFail#effectiveResult()}:
 *   {@code "Skip"} short-circuits to {@link StationStepResult#SKIP} WITHOUT calling the inner
 *   handler (the {@code nextIndex} hook in {@link StationStepSemantics} is where an authored
 *   {@code Goto} then jumps); {@code "Fail"} (the default) fails the walk here.</li>
 *   <li><b>Throw guard (design 9.3/M4's binding fix).</b> The inner handler's {@code execute} runs
 *   inside a {@code try/catch(Throwable)} that degrades a throw to
 *   {@code Fail(STEP_FAILED, message)} + a guarded warn log - mirroring
 *   {@code com.ziggfreed.common.cast.ObserverRegistry}'s per-listener guard, per the reshaped
 *   kernel's own documented seam (a consumer wraps each REGISTERED handler, not the kernel's
 *   dispatch loop - {@code CastKernel}'s no-catch contract stays intact). One throwing step stops
 *   the SESSION (a clean {@code stop()}), never crashes the shared per-world frame drain.</li>
 * </ol>
 */
final class StationStepRegistry extends StepRegistry<String, StationStepContext, StationStep, StationStepResult> {

    private StationStepRegistry() {
        super();
    }

    @Nonnull
    static StationStepRegistry production() {
        StationStepRegistry registry = new StationStepRegistry();
        registry.register(StationStep.TYPE_CONSUME.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.ConsumeHandler()));
        registry.register(StationStep.TYPE_PRODUCE.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.ProduceHandler()));
        registry.register(StationStep.TYPE_WAIT.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.WaitHandler()));
        registry.register(StationStep.TYPE_ROLL.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.RollHandler()));
        registry.register(StationStep.TYPE_COMMAND.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.CommandHandler()));
        registry.register(StationStep.TYPE_PRESENT.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.PresentHandler()));
        // TYPE_STAMP / TYPE_MOUNT deliberately unregistered - reserved, unimplemented this leg
        // (StationStep's javadoc); an authored program using either fails at the kernel's own
        // missing-handler path (StationStepSemantics#onMissingHandler), which logs + degrades to
        // a clean STEP_FAILED stop - the same outcome the guard produces for a handler throw.
        return registry;
    }

    @Nonnull
    private static StepHandler<StationStepContext, StationStep, StationStepResult> guard(
            @Nonnull StepHandler<StationStepContext, StationStep, StationStepResult> inner) {
        return (ctx, step) -> {
            StationStepResult conditionOutcome = checkConditions(ctx, step);
            if (conditionOutcome != null) {
                return conditionOutcome;
            }
            try {
                return inner.execute(ctx, step);
            } catch (Throwable t) {
                Log.warn("STATION step '" + step.getId() + "' (Type '" + step.getType() + "') threw", t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "step '" + step.getId() + "' threw: " + t.getMessage());
            }
        };
    }

    /** {@code null} = every condition passed (or none authored) - proceed to the inner handler. */
    @Nullable
    private static StationStepResult checkConditions(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        StationService.FactorLookup lookup = ctx.snapshot::resolve;
        StationStepDecisions.ConditionOutcome outcome = StationStepDecisions.resolveConditionOutcome(
                step.getConditions(), step.getOnConditionFail(), lookup);
        return switch (outcome) {
            case PASS -> null;
            case SKIP -> StationStepResult.SKIP;
            case FAIL -> StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "step '" + step.getId() + "' Conditions failed");
        };
    }
}
