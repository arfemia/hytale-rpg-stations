package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where a {@link ValidationHook} reports what it found. Deliberately
 * ADVISORY ONLY: there is no {@code error} method, because this engine's never-block posture is
 * absolute - an asset always loads, a station always engages, and a hook's opinion can never
 * change either. A hook's findings land in the same aggregate report the engine's own checks
 * write to, logged and echoed by the validate command.
 *
 * <p>{@code code} is a short SCREAMING_SNAKE identifier the reporting mod owns and keeps stable
 * (it is what a server owner greps for); {@code message} is a raw-English one-liner naming the
 * offending asset and what to do about it. {@code subjectId} is the asset id the finding is
 * about, so the report can group by subject; pass {@code null} when the finding is not about one
 * specific asset.
 */
public interface FindingSink {

    /** Report an advisory note (no action strictly required). */
    void info(@Nonnull String code, @Nonnull String message, @Nullable String subjectId);

    /** Report a warning (something is probably misauthored, but nothing is blocked). */
    void warn(@Nonnull String code, @Nonnull String message, @Nullable String subjectId);

    /** {@link #info(String, String, String)} with no specific subject asset. */
    default void info(@Nonnull String code, @Nonnull String message) {
        info(code, message, null);
    }

    /** {@link #warn(String, String, String)} with no specific subject asset. */
    default void warn(@Nonnull String code, @Nonnull String message) {
        warn(code, message, null);
    }
}
