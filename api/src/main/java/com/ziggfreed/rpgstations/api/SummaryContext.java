package com.ziggfreed.rpgstations.api;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The context a {@link SummaryEnricher#rows} call receives (design section 3.2): plain session
 * totals plus sync-only live world-thread context. {@link #store()}/{@link #playerRef()} are
 * valid ONLY synchronously during the call.
 */
public final class SummaryContext {

    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    private final int cyclesDone;
    private final long durationMs;
    @Nullable private final Store<EntityStore> store;
    @Nullable private final PlayerRef playerRef;

    public SummaryContext(@Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId,
            int cyclesDone, long durationMs, @Nullable Store<EntityStore> store, @Nullable PlayerRef playerRef) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.cyclesDone = cyclesDone;
        this.durationMs = durationMs;
        this.store = store;
        this.playerRef = playerRef;
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

    public int cyclesDone() {
        return cyclesDone;
    }

    public long durationMs() {
        return durationMs;
    }

    @Nullable
    public Store<EntityStore> store() {
        return store;
    }

    @Nullable
    public PlayerRef playerRef() {
        return playerRef;
    }
}
