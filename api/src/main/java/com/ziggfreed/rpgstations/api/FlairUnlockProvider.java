package com.ziggfreed.rpgstations.api;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

/** Answers "which flair ids has this player unlocked" (design section 3.2). */
@FunctionalInterface
public interface FlairUnlockProvider {

    /**
     * Every flair id {@code playerId} has unlocked, ACROSS every station (per-station scoping
     * happens in the engine's own overlay resolution against the station's authored {@code
     * Flairs} map). Must never throw and must never return {@code null} (an empty {@link Set} is
     * the "nothing unlocked" answer).
     */
    @Nonnull
    Set<String> unlockedFlairIds(@Nonnull UUID playerId);
}
