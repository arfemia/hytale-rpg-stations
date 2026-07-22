package com.ziggfreed.rpgstations.api.event;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fired synchronously on the shared Hytale event bus when a station work session begins (every
 * gate/engage check already passed), on the world thread from {@code StationService.toggle}.
 *
 * <p><b>Plain data</b> ({@link #playerId()}, {@link #sessionId()}, {@link #stationId()},
 * {@link #actionId()}, {@link #blockX()}/{@link #blockY()}/{@link #blockZ()}, {@link
 * #idleMode()}) is always safe to retain. <b>Live world-thread context</b> ({@link #store()},
 * {@link #playerRef()}) is valid ONLY synchronously during dispatch; a listener that defers work
 * must capture the plain fields and re-resolve.
 */
public final class StationSessionStartedEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final boolean idleMode;

    public StationSessionStartedEvent(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            int blockX, int blockY, int blockZ, boolean idleMode) {
        this.store = store;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.actionId = actionId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.idleMode = idleMode;
    }

    @Nonnull
    public Store<EntityStore> store() {
        return store;
    }

    @Nonnull
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

    /** The one action id phase 1 ever forwards ({@code "work"}); phase 2 adds multi-action ids. */
    @Nonnull
    public String actionId() {
        return actionId;
    }

    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    /** True when the session engaged in opt-in idle practice mode (no runnable conversion, {@code Work.Idle} enabled). */
    public boolean idleMode() {
        return idleMode;
    }
}
