package com.ziggfreed.rpgstations.station;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.ziggfreed.common.cast.step.StepHandler;
import com.ziggfreed.common.cast.step.StepRegistry;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The production {@code station.step} handler registry: registers the seven executable handlers
 * (design 9.3/9.5), each wrapped in THREE layers every registrant goes through, never bypassed by
 * a handler body:
 *
 * <ol>
 *   <li><b>Conditions gate.</b> A step's {@link StationStep#getConditions()} (if any) are checked
 *   FIRST, against the same factor-lookup contract {@code StationService.conditionPasses} already
 *   uses for a station's {@code Requires} gate - one condition semantics, everywhere a condition
 *   is authored. A failing condition resolves via {@link StationStep.OnConditionFail#effectiveResult()}:
 *   {@code "Skip"} short-circuits to {@link StationStepResult#SKIP} WITHOUT calling the inner
 *   handler (the {@code nextIndex} hook in {@link StationStepSemantics} is where an authored
 *   {@code Goto} then jumps); {@code "Fail"} (the default) fails the walk here.</li>
 *   <li><b>Generic per-step Presentation entry (maintainer-approved extension).</b> ANY step's
 *   authored {@code Presentation} plays the MOMENT it begins executing - not just the dedicated
 *   {@code Present} step's - via the SAME {@code StationService#emitMoment} path
 *   {@code StationStepHandlers.PresentHandler} itself uses. {@link StationStepDecisions
 *   #shouldEmitPresentationOnEntry} is the pure gate: skipped entirely for a {@code "Present"}-
 *   typed step (its own handler already emits - never double-play it) and for the suspend-resume
 *   RE-CHECK of the exact step that already played on its first entry ({@link StationStepContext
 *   #resumingStep}, identity-compared).</li>
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
        registry.register(StationStep.TYPE_STAMP.toLowerCase(Locale.ROOT),
                guard(new StationStepHandlers.StampHandler()));
        // TYPE_MOUNT deliberately unregistered - reserved, unimplemented this leg (StationStep's
        // javadoc); an authored program using it fails at the kernel's own missing-handler path
        // (StationStepSemantics#onMissingHandler), which logs + degrades to a clean STEP_FAILED
        // stop - the same outcome the guard produces for a handler throw.
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
            emitGenericStepPresentation(ctx, step);
            emitStepPuppetClip(ctx, step);
            try {
                return inner.execute(ctx, step);
            } catch (Throwable t) {
                Log.warn("STATION step '" + step.getId() + "' (Type '" + step.getType() + "') threw", t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "step '" + step.getId() + "' threw: " + t.getMessage());
            }
        };
    }

    /**
     * The generic per-step Presentation entry (maintainer-approved extension, same commit as the
     * round-7 D77 timing instrumentation): plays {@code step}'s OWN authored {@code Presentation}
     * once, right as it begins executing, reusing the IDENTICAL emission path
     * {@code StationStepHandlers.PresentHandler} uses ({@code StationService#emitMoment} +
     * {@code StationStepHandlers#presentMomentId}) - never a second mechanism. No-ops for a step
     * with no authored {@code Presentation}, a {@code "Present"}-typed step (its own handler
     * already emits it), or the suspend-resume RE-CHECK of an already-started step (see
     * {@link StationStepDecisions#shouldEmitPresentationOnEntry}).
     */
    private static void emitGenericStepPresentation(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        if (step.getPresentation() == null) {
            return;
        }
        if (!StationStepDecisions.shouldEmitPresentationOnEntry(step, ctx.resumingStep)) {
            return;
        }
        Vector3d blockPos = new Vector3d(ctx.session.blockX + 0.5, ctx.session.blockY + 0.5, ctx.session.blockZ + 0.5);
        StationService.emitMoment(ctx.store, ctx.session, StationStepHandlers.presentMomentId(ctx, step),
                step.getPresentation(), blockPos);
    }

    /**
     * The step-synced puppet swing (maintainer-approved, round-8): plays {@code step}'s OWN
     * authored {@code Puppet.Clip} once on the session's puppet the moment the step begins
     * EXECUTING - the per-ITERATION-entry trigger (future-proof for step repetition by
     * construction). Uses the SAME once-per-entry / never-on-resume-recheck gate the generic
     * per-step Presentation hook uses ({@link StationStepDecisions#shouldPlayClipOnEntry},
     * {@link StationStepContext#resumingStep} identity-compared). No-op for a step with no authored
     * {@code Puppet.Clip} (it inherits the action's default - owned by the generic engage/swing beat
     * for a NON-clip program - or simply idles), for the suspend-resume RE-CHECK of an
     * already-started step, or when the session runs no puppet ({@link StationPuppetController
     * #playStepClip}'s own guard). The generic per-cycle puppet swing is suppressed for a stepped
     * program whose steps author any clip ({@link StationSession#stepProgramAuthorsClip}), so these
     * per-step-entry clips never double-fire with it.
     */
    private static void emitStepPuppetClip(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        if (!StationStepDecisions.shouldPlayClipOnEntry(step, ctx.resumingStep)) {
            return;
        }
        StationPuppetController.playStepClip(ctx.session, ctx.store, step.getPuppet().getClip());
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
