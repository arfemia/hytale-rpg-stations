package com.ziggfreed.rpgstations.api.event;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fired synchronously on the shared Hytale event bus when ONE produce phase COMMITS during an
 * attended session - the moment a station action's outputs come into existence, whether they
 * landed in the block's placed custody (a stew batch standing in the pot's output slot) or went
 * straight to the worker's inventory (a milled plank). One event per committed produce phase,
 * never per item: a phase producing several stacks reports them together on {@link #outputs()}.
 * Fired on the world thread, AFTER the whole batch is committed (custody written and its display
 * spawned, or every inventory grant landed).
 *
 * <p><b>The worker is never null.</b> This event fires only from an ATTENDED produce commit, so
 * {@link #worker()} always names the working player's live entity and {@link #workerId()} their
 * uuid. An UNATTENDED settle deliberately does not fire it - that output surfaces when a player
 * gathers the pile, on {@link StationUnattendedGatheredEvent}, where the gatherer is the one
 * paid.
 *
 * <p>{@link #outputs()} carries fresh, immutable {@link ItemStack} copies of what was committed
 * (an inventory grant that failed outright - no room and the ground drop failed too - is
 * excluded, since the player never received it). {@link #socketId()} names the custody socket
 * pile the batch landed in, or is {@code null} when the outputs went to the worker's inventory;
 * a custody batch spanning several sockets reports the FIRST produced socket (the one its
 * readiness window sits on).
 *
 * <p><b>Plain data</b> ({@link #workerId()}, {@link #worldUuid()}, {@link #blockX()}/
 * {@link #blockY()}/{@link #blockZ()}, {@link #stationId()}, {@link #actionId()},
 * {@link #socketId()}, {@link #outputs()}) is always safe to retain. <b>Live world-thread
 * context</b> ({@link #store()}, {@link #playerRef()}, {@link #worker()}) is valid ONLY
 * synchronously during dispatch; a listener that defers work must capture the plain fields and
 * re-resolve.
 */
public final class StationOutputProducedEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final Ref<EntityStore> worker;
    @Nonnull private final UUID workerId;
    @Nonnull private final UUID worldUuid;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    @Nullable private final String socketId;
    @Nonnull private final List<ItemStack> outputs;

    public StationOutputProducedEvent(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> worker, @Nonnull UUID workerId, @Nonnull UUID worldUuid,
            int blockX, int blockY, int blockZ, @Nonnull String stationId, @Nonnull String actionId,
            @Nullable String socketId, @Nonnull List<ItemStack> outputs) {
        this.store = store;
        this.playerRef = playerRef;
        this.worker = worker;
        this.workerId = workerId;
        this.worldUuid = worldUuid;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.stationId = stationId;
        this.actionId = actionId;
        this.socketId = socketId;
        this.outputs = List.copyOf(outputs);
    }

    @Nonnull
    public Store<EntityStore> store() {
        return store;
    }

    /** The working player's ref handle - live world-thread context, dispatch-synchronous only. */
    @Nonnull
    public PlayerRef playerRef() {
        return playerRef;
    }

    /** The working player's entity ref - NEVER null (this event fires only from an attended produce). */
    @Nonnull
    public Ref<EntityStore> worker() {
        return worker;
    }

    @Nonnull
    public UUID workerId() {
        return workerId;
    }

    @Nonnull
    public UUID worldUuid() {
        return worldUuid;
    }

    /**
     * The block the batch was committed at: the custody anchor for a placed-custody produce, or
     * the session's own primary station block for an inventory produce.
     */
    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    @Nonnull
    public String stationId() {
        return stationId;
    }

    @Nonnull
    public String actionId() {
        return actionId;
    }

    /**
     * The custody socket pile the batch landed in (the phase's first produced socket), or
     * {@code null} when the outputs went to the worker's inventory.
     */
    @Nullable
    public String socketId() {
        return socketId;
    }

    /** The committed output stacks, as fresh immutable copies - safe to retain and inspect. */
    @Nonnull
    public List<ItemStack> outputs() {
        return outputs;
    }
}
