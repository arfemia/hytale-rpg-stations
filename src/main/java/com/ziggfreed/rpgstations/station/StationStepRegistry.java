package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.cast.step.StepHandler;
import com.ziggfreed.common.cast.step.StepRegistry;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The production {@code station.step} handler registry (scope-2 design 2.1, decision 34): the
 * pre-scope-2 per-{@code Type} handler table (Consume/Produce/Wait/Roll/Command/Present/Stamp) is
 * REPLACED by ONE composite handler ({@link StationStepHandlers.CompositeStepHandler}) registered
 * under the single {@link #STEP_KEY}. That handler walks the FIXED phase order per iteration
 * ({@code Walk} -&gt; {@code Consume} -&gt; {@code Stamp} -&gt; {@code Produce} -&gt; {@code Roll}
 * -&gt; {@code Commands} -&gt; {@code Presentation}/{@code Puppet.Clip} -&gt; {@code Duration}) and
 * owns the per-step {@code Repeat} + {@code Duration} suspend/resume machinery.
 *
 * <p>The composite handler is wrapped in TWO layers every registrant goes through, never bypassed:
 * <ol>
 *   <li><b>Conditions gate.</b> A step's {@link StationStep#getConditions()} (if any) are checked
 *   FIRST at step entry, against the same factor-lookup contract {@code StationService
 *   .conditionPasses} uses for a station's {@code Requires} gate. A failing condition resolves via
 *   {@link StationStep.OnConditionFail#effectiveResult()}: {@code "Skip"} short-circuits to
 *   {@link StationStepResult#SKIP} WITHOUT running the phase walk (the {@code nextIndex} hook in
 *   {@link StationStepSemantics} is where an authored {@code Goto} then jumps); {@code "Fail"} (the
 *   default) fails the walk here.</li>
 *   <li><b>Throw guard (design 2.1/M4's binding fix).</b> The composite handler runs inside a
 *   {@code try/catch(Throwable)} that degrades a throw to {@code Fail(STEP_FAILED, message)} + a
 *   guarded warn - mirroring {@code com.ziggfreed.common.cast.ObserverRegistry}'s per-listener
 *   guard. One throwing phase stops the SESSION (a clean {@code stop()}), never crashes the shared
 *   per-world frame drain.</li>
 * </ol>
 *
 * <p>Per-iteration presentation, puppet clip, and puppet prop cues now live INSIDE the composite
 * handler (fired once per FRESH iteration entry, skipped on a {@code Duration}-hold resume re-check
 * that the handler detects internally) rather than in this guard, so they re-fire correctly for
 * every {@code Repeat} iteration.
 */
final class StationStepRegistry extends StepRegistry<String, StationStepContext, StationStep, StationStepResult> {

    /** The ONE dispatch key every step maps to (scope-2: no {@code Type} discriminator). */
    static final String STEP_KEY = "step";

    private StationStepRegistry() {
        super();
    }

    @Nonnull
    static StationStepRegistry production() {
        StationStepRegistry registry = new StationStepRegistry();
        registry.register(STEP_KEY, guard(new StationStepHandlers.CompositeStepHandler()));
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
                Log.warn("STATION step '" + step.getId() + "' threw", t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "step '" + step.getId() + "' threw: " + t.getMessage());
            }
        };
    }

    /** {@code null} = every condition passed (or none authored) - proceed to the composite handler. */
    @Nullable
    private static StationStepResult checkConditions(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        FactorLookup lookup = ctx.snapshot::resolve;
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
