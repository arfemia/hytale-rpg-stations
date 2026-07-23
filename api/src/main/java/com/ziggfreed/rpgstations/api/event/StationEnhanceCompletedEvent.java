package com.ziggfreed.rpgstations.api.event;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.api.EnhanceLine;

/**
 * Fired synchronously on the shared Hytale event bus after an enhancement Stamp step commits
 * successfully (design section 9.5, phase 2 round-7 D-6), on the world thread from
 * {@code StationService}'s Stamp path - AFTER the mutated stack is written back to the custody
 * claim (so this reports ONLY a committed enhancement, never a cancelled/denied ritual).
 *
 * <p><b>Both maintainer-named reporting shapes are expressible, MMO-agnostically:</b> {@link
 * #lines()} is the "enhancements-metadata method" (the provider's own opaque-stat report), while
 * {@link #before()}/{@link #after()} are the "copy-of-item method" - the ENGINE snapshots the
 * stack around the apply call ({@code ItemStack} is immutable-with-copy, so the pre-mutation
 * reference IS the before copy), so a future consumer (achievements, an item-diff page, a
 * third-party progression mod) can diff or inspect them WITHOUT RpgStations ever learning any
 * stat vocabulary. Nothing listens MMO-side this round; the seam is future-proof.
 *
 * <p><b>Plain data</b> ({@link #playerId()}, {@link #sessionId()}, {@link #stationId()}, {@link
 * #actionId()}, {@link #itemId()}, {@link #before()}, {@link #after()}, {@link #lines()}, {@link
 * #durabilityAdded()}) is always safe to retain ({@code ItemStack} snapshots are immutable copies;
 * {@link #lines()} is defensively copied). <b>Live world-thread context</b> ({@link #store()},
 * {@link #playerRef()}) is valid ONLY synchronously during dispatch; a listener that defers work
 * must capture the plain fields and re-resolve.
 */
public final class StationEnhanceCompletedEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    @Nonnull private final String itemId;
    @Nonnull private final ItemStack before;
    @Nonnull private final ItemStack after;
    @Nonnull private final List<EnhanceLine> lines;
    private final double durabilityAdded;

    public StationEnhanceCompletedEvent(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            @Nonnull String itemId, @Nonnull ItemStack before, @Nonnull ItemStack after,
            @Nonnull List<EnhanceLine> lines, double durabilityAdded) {
        this.store = store;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.actionId = actionId;
        this.itemId = itemId;
        this.before = before;
        this.after = after;
        this.lines = List.copyOf(lines);
        this.durabilityAdded = durabilityAdded;
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
    public String actionId() {
        return actionId;
    }

    /** The id of the enhanced item (the custody item the stamp wrote onto). */
    @Nonnull
    public String itemId() {
        return itemId;
    }

    /** Immutable copy of the stack BEFORE the stamp mutation (the "copy-of-item" report). */
    @Nonnull
    public ItemStack before() {
        return before;
    }

    /** Immutable copy of the stack AFTER the stamp mutation (durability + stats applied). */
    @Nonnull
    public ItemStack after() {
        return after;
    }

    /** The provider's per-stat enhancements-metadata report (empty = durability-only / silent). */
    @Nonnull
    public List<EnhanceLine> lines() {
        return lines;
    }

    /** The max-durability delta the station's own {@code Durability.AddMax} added (0 = none). */
    public double durabilityAdded() {
        return durabilityAdded;
    }
}
