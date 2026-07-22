package com.ziggfreed.rpgstations.validation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One content-validation result. Ported verbatim from the MMO's {@code validation.Finding}
 * mini-core (RPG Stations extraction leg 2). Diagnostic messages are raw English by
 * convention (admin/log surface).
 */
public record Finding(@Nonnull Severity severity, @Nonnull String domain, @Nonnull String code,
                      @Nonnull String message, @Nullable String subjectId) {

    @Nonnull
    public static Finding error(@Nonnull String domain, @Nonnull String code,
                                @Nonnull String message, @Nullable String subjectId) {
        return new Finding(Severity.ERROR, domain, code, message, subjectId);
    }

    @Nonnull
    public static Finding warning(@Nonnull String domain, @Nonnull String code,
                                  @Nonnull String message, @Nullable String subjectId) {
        return new Finding(Severity.WARNING, domain, code, message, subjectId);
    }

    @Nonnull
    public static Finding info(@Nonnull String domain, @Nonnull String code,
                               @Nonnull String message, @Nullable String subjectId) {
        return new Finding(Severity.INFO, domain, code, message, subjectId);
    }
}
