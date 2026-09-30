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
 * Fired synchronously on the shared Hytale event bus when a station CONSUMES input - on the world
 * thread, AFTER the consumption committed, exactly once per committed batch, and never for a batch
 * the engine gave back. Every path on which a station takes input reaches it:
 *
 * <ul>
 *   <li><b>An attended session's consume</b> - a {@code Consume} phase, or a {@code Convert}
 *   phase's own drain, reported when the iteration COMMITS (a committed produce or conversion, or
 *   a completed program pass); an interrupted iteration refunds its inputs and reports nothing.
 *   <li><b>A {@code Stamp} phase's reagents</b>, reported the moment the enhanced stack commits.
 *   <li><b>An unattended settle</b> - a custody-loaded station settling its conversions with nobody
 *   engaged. Here, and only here, {@link #playerRef()}, {@link #worker()} and {@link #workerId()}
 *   are {@code null}: nobody consumed, the station did.
 * </ul>
 *
 * <p>{@link #inputs()} carries fresh, immutable {@link Consumed} records: each the REAL stack that
 * was taken (the metadata-bearing piece when a single-item socket gave it up, else a bare stack of
 * the drained id and count), the custody socket it left ({@code null} on the inventory route), and
 * the quality index and item level read off that stack, {@code null} where the stack cannot tell
 * (a bare stack of a drained id reads as its item's defaults, and an inventory-route stack is a
 * bare {@code (id, count)} stack, so wear and metadata are not on it).
 *
 * <p><b>Plain data</b> ({@link #workerId()}, {@link #worldUuid()}, {@link #blockX()}/
 * {@link #blockY()}/{@link #blockZ()}, {@link #stationId()}, {@link #actionId()},
 * {@link #inputs()}) is always safe to retain. <b>Live world-thread context</b> ({@link #store()},
 * {@link #playerRef()}, {@link #worker()}) is valid ONLY synchronously during dispatch; a listener
 * that defers work captures the plain fields and re-resolves.
 */
public final class StationInputConsumedEvent implements IEvent<Void> {

    /**
     * ONE consumed stack: the stack itself (an immutable copy), the custody socket it left
     * ({@code null} for the inventory route), and the quality index and item level read off it
     * ({@code null} where the stack cannot tell).
     */
    public record Consumed(@Nonnull ItemStack stack, @Nullable String socketId, @Nullable Integer quality,
            @Nullable Integer itemLevel) {

        /** The stack's item id, the target a listener keys on. */
        @Nonnull
        public String itemId() {
            return stack.getItemId();
        }

        /** The stack's quantity, the amount a listener counts. */
        public int quantity() {
            return stack.getQuantity();
        }
    }

    @Nonnull private final Store<EntityStore> store;
    @Nullable private final PlayerRef playerRef;
    @Nullable private final Ref<EntityStore> worker;
    @Nullable private final UUID workerId;
    @Nonnull private final UUID worldUuid;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    @Nonnull private final List<Consumed> inputs;

    public StationInputConsumedEvent(@Nonnull Store<EntityStore> store, @Nullable PlayerRef playerRef,
            @Nullable Ref<EntityStore> worker, @Nullable UUID workerId, @Nonnull UUID worldUuid,
            int blockX, int blockY, int blockZ, @Nonnull String stationId, @Nonnull String actionId,
            @Nonnull List<Consumed> inputs) {
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
        this.inputs = List.copyOf(inputs);
    }

    @Nonnull
    public Store<EntityStore> store() {
        return store;
    }

    /** The working player's ref handle, or {@code null} on an unattended settle - live world-thread context. */
    @Nullable
    public PlayerRef playerRef() {
        return playerRef;
    }

    /** The working player's entity ref, or {@code null} on an unattended settle. */
    @Nullable
    public Ref<EntityStore> worker() {
        return worker;
    }

    /** The working player's uuid, or {@code null} on an unattended settle. */
    @Nullable
    public UUID workerId() {
        return workerId;
    }

    /** True when a player consumed (an attended session); false on an unattended settle. */
    public boolean attended() {
        return playerRef != null && worker != null && workerId != null;
    }

    @Nonnull
    public UUID worldUuid() {
        return worldUuid;
    }

    /**
     * The block the consumed pile stood at: the station's own block for an inventory-sourced
     * consume, the {@code At} anchor's block for a remote pile.
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

    /** The action id the consumption ran under. */
    @Nonnull
    public String actionId() {
        return actionId;
    }

    /** The consumed stacks, as fresh immutable copies with their sockets and readings - safe to retain and inspect. */
    @Nonnull
    public List<Consumed> inputs() {
        return inputs;
    }
}
