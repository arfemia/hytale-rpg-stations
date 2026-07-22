package com.ziggfreed.rpgstations.api.event;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fired synchronously on the shared Hytale event bus when the heartbeat detects the player's
 * held tool broke ({@code ItemStack.isBroken()}) mid-session, alongside the engine's own {@code
 * TOOL_BROKEN} stop (design section 3.1). Fired BEFORE the resulting {@link
 * StationSessionCompletedEvent}.
 *
 * <p><b>Plain data</b> is always safe to retain; {@link #store()}/{@link #playerRef()} are valid
 * ONLY synchronously during dispatch.
 */
public final class StationToolBrokeEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    @Nonnull private final String heldItemId;

    public StationToolBrokeEvent(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String heldItemId) {
        this.store = store;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.heldItemId = heldItemId;
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

    @Nonnull
    public String heldItemId() {
        return heldItemId;
    }
}
