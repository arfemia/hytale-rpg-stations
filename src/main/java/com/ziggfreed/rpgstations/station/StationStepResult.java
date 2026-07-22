package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A {@code station.step} handler's outcome, the {@code R} type parameter of the
 * {@link com.ziggfreed.common.cast.step.CastKernel} instance {@link StationStepKernel} holds.
 * Sealed so {@link StationStepSemantics}'s dispatch is exhaustive.
 *
 * <p>{@link Suspend} is the design 9.3 "sessions suspend/resume across ticks" contract: a
 * {@code Wait} step returns it to pause the walk; {@code StationService#resumeCycleProgram}
 * re-enters via {@link StationStepKernel#runResumable} once {@link Suspend#untilMs()} passes, per
 * the kernel's binding resume contract (re-enters the SAME suspending step - see
 * {@code com.ziggfreed.common.cast.step.CastKernel#runResumable}'s javadoc).
 */
public sealed interface StationStepResult {

    /** The step succeeded; the walk continues (or completes, if this was the last step). */
    record Success() implements StationStepResult {
    }

    /** The step suspends the walk until {@code untilMs} (epoch millis); resume re-enters this SAME step. */
    record Suspend(long untilMs) implements StationStepResult {
    }

    /**
     * The step is a no-op this run (its {@code Conditions} failed and
     * {@code OnConditionFail.Result} is {@code "Skip"}); treated as a SUCCESS-continuing result by
     * {@link StationStepSemantics#isSuccess} - "skip" means "continue past this step", not "stop".
     */
    record Skip() implements StationStepResult {
    }

    /** The step failed; the walk stops here and the session tears down with {@code reason}. */
    record Fail(@Nonnull StationService.StopReason reason, @Nullable String message) implements StationStepResult {
    }

    StationStepResult SUCCESS = new Success();
    StationStepResult SKIP = new Skip();

    @Nonnull
    static StationStepResult fail(@Nonnull StationService.StopReason reason, @Nullable String message) {
        return new Fail(reason, message);
    }

    @Nonnull
    static StationStepResult suspend(long untilMs) {
        return new Suspend(untilMs);
    }
}
