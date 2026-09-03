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
 * Fired synchronously on the shared Hytale event bus when a batch of items comes into a worker's
 * hands during an ATTENDED session - on the world thread, AFTER the whole batch landed. Two
 * moments fire it:
 *
 * <ul>
 *   <li><b>A committed produce phase</b> - the moment a station action's own outputs come into
 *   existence, whether they landed in the block's placed custody (a stew batch standing in the
 *   pot's output slot; {@link #socketId()} names the receiving pile) or went straight to the
 *   worker's inventory (a milled plank; {@link #socketId()} is {@code null}). One event per
 *   committed produce phase, never per item: a phase producing several stacks reports them
 *   together on {@link #outputs()}, and a custody batch spanning several sockets reports the
 *   FIRST produced socket (the one its readiness window sits on).
 *   <li><b>A committed grant pass</b> - everything one loot pass paid INTO THE WORKER'S HANDS:
 *   the additive bonus units of the cycle's own output ({@code Grants.OutputItems}, as the count
 *   that actually landed) first, then one stack per distinct item id the pass paid through
 *   {@code Grants.Items} and {@code Grants.DropLists}. {@link #socketId()} is always {@code null}
 *   (a grant pass lands in the inventory, or on the ground at the station block when the
 *   inventory is full), {@link #actionId()} is the paying action, and the block is the session's
 *   own primary station block. The per-cycle {@code Roll} phase, an authored program's completed
 *   pass and the session's completion pass all report this way; a pass that landed nothing fires
 *   nothing.
 * </ul>
 *
 * <p><b>What never appears here.</b> A {@code Grants.Commands} payout is invisible: the engine
 * cannot know what a console command gave, so an item handed over by a command is never reported.
 * An UNATTENDED settle fires nothing, and neither does the gather that pays out its accrued rolls;
 * that output surfaces once, on {@link StationUnattendedGatheredEvent}, where the gatherer is the
 * one paid. A stack that reached neither the inventory nor the ground is excluded on both moments,
 * since the player never received it.
 *
 * <p><b>The worker is never null.</b> Both moments fire only from an attended session, so
 * {@link #worker()} always names the working player's live entity and {@link #workerId()} their
 * uuid.
 *
 * <p>{@link #outputs()} carries fresh, immutable {@link ItemStack} copies of what landed.
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

    /** The working player's entity ref - NEVER null (this event fires only from an attended session). */
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
     * the session's own primary station block for an inventory produce and for a grant pass.
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
     * {@code null} when the outputs went to the worker's inventory - always {@code null} for a
     * grant pass.
     */
    @Nullable
    public String socketId() {
        return socketId;
    }

    /** The stacks that landed, as fresh immutable copies - safe to retain and inspect. */
    @Nonnull
    public List<ItemStack> outputs() {
        return outputs;
    }
}
