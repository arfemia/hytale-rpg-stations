package com.ziggfreed.rpgstations.api.event;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fired synchronously on the shared Hytale event bus for EVERY station session teardown, silent
 * stops included (design section 3.1) - the ONE unconditional cleanup signal a listener uses to
 * drop any per-session state it accumulated off {@link StationCycleCompletedEvent#sessionId()}.
 * Fired at the very end of {@code StationService}'s one idempotent {@code stop()} funnel, AFTER
 * the non-silent summary path (registered {@code SummaryEnricher}s included) and the completion
 * moment - see that funnel's ordering contract (design section 7.3).
 *
 * <p>{@link #store()} and {@link #playerRef()} are {@code @Nullable}: a disconnect or
 * server-stop teardown ({@code StationService.stopFor}/{@code stopAll}) has no live {@code
 * Store} to pass (the entity may already be gone by the time the stop funnel runs), and {@link
 * #playerRef()} carries the same "may be gone" caveat. A listener that only needs {@link
 * #playerId()} for bookkeeping cleanup never needs either.
 */
public final class StationSessionCompletedEvent implements IEvent<Void> {

    @Nullable private final Store<EntityStore> store;
    @Nullable private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    @Nonnull private final String stopReason;
    private final boolean silent;
    private final int cyclesDone;
    private final long durationMs;

    public StationSessionCompletedEvent(@Nullable Store<EntityStore> store, @Nullable PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String stopReason,
            boolean silent, int cyclesDone, long durationMs) {
        this.store = store;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.stopReason = stopReason;
        this.silent = silent;
        this.cyclesDone = cyclesDone;
        this.durationMs = durationMs;
    }

    @Nullable
    public Store<EntityStore> store() {
        return store;
    }

    @Nullable
    public PlayerRef playerRef() {
        return playerRef;
    }

    @Nonnull
    public UUID playerId() {
        return playerId;
    }

    @Nonnull
    public UUID sessionId() {
        return sessionId;
    }

    @Nonnull
    public String stationId() {
        return stationId;
    }

    /** The engine's {@code StationService.StopReason} enum name (e.g. {@code "PLAYER_EXIT"}, {@code "TOOL_BROKEN"}). */
    @Nonnull
    public String stopReason() {
        return stopReason;
    }

    /** True for a teardown that shows no summary/toast (disconnect, server stop, death, world change). */
    public boolean silent() {
        return silent;
    }

    public int cyclesDone() {
        return cyclesDone;
    }

    public long durationMs() {
        return durationMs;
    }
}
