package com.ziggfreed.rpgstations.api.event;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fired synchronously on the shared Hytale event bus when a station turns a player's press away:
 * the wrong tool, nothing it can work with, a full socket, someone else's materials, a busy
 * anchor, a locked gate, a structure that cannot be raised - every denial that answers with a
 * notice instead of starting work. Fired from the world thread, AFTER the engine's own answer to
 * the press (the notice and the refusal cue) has been sent.
 *
 * <p><b>One dispatch is one refusal worth reacting to.</b> The engine throttles a REPEAT of the
 * same {@link #reason()} by the same player at the same block inside the settings' refusal repeat
 * window (a mashed key): a repeat still answers audibly, but it sends no notice and fires no event.
 * A different reason, or the same reason at another block, is new information and always fires.
 *
 * <p>{@link #reason()} is the refusal's own wording key tail (the part of
 * {@code ui.station.<reason>} after the dot) as an id written {@code Is_Like_This}:
 * {@code No_Materials}, {@code Wrong_Tool}, {@code Occupied}, {@code Socket_Missing},
 * {@code Retrieve_Busy}, ... It is also the tail of the {@code Refused:<Reason>} moment id the
 * cue resolved against; compare it case-insensitively, as every id in this vocabulary is matched. A wording variant that names a
 * part of the station reports its base reason - the reason is about WHY, never about which line
 * was chosen.
 *
 * <p>{@link #actionId()} is {@code null} when the press was refused before an action could be
 * chosen (nothing held matched any action, the station is disabled or unknown) and for a
 * multiblock structure's refusal, which has no action at all.
 *
 * <p><b>Plain data</b> is always safe to retain; {@link #store()}/{@link #playerRef()} are valid
 * ONLY synchronously during dispatch.
 */
public final class StationRefusedEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID worldUuid;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    @Nonnull private final String stationId;
    @Nullable private final String actionId;
    @Nonnull private final String reason;

    public StationRefusedEvent(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID worldUuid, int blockX, int blockY, int blockZ,
            @Nonnull String stationId, @Nullable String actionId, @Nonnull String reason) {
        this.store = store;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.worldUuid = worldUuid;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.stationId = stationId;
        this.actionId = actionId;
        this.reason = reason;
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

    /** The world the refused block stands in (a block position is only meaningful with it). */
    @Nonnull
    public UUID worldUuid() {
        return worldUuid;
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

    /**
     * The station that refused - its asset id, or for a multiblock structure the pattern's own
     * station (or the pattern id when it names none).
     */
    @Nonnull
    public String stationId() {
        return stationId;
    }

    /** The action the press had resolved to, or {@code null} when it was refused before one was chosen. */
    @Nullable
    public String actionId() {
        return actionId;
    }

    /** The refusal's reason tail (see the class javadoc); never blank. */
    @Nonnull
    public String reason() {
        return reason;
    }
}
