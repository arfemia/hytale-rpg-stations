package com.ziggfreed.rpgstations.api.event;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.api.XpAsk;

/**
 * Fired synchronously on the shared Hytale event bus after every completed work cycle (real OR
 * idle), on the world thread from {@code StationService}'s cycle path - AFTER the loot roll for
 * a real cycle (design section 3.1). An idle cycle fires this too, with {@link #idle()} true,
 * {@link #xpAsks()} already scaled by the station's {@code Work.Idle.XpFraction}, and {@link
 * #toolMultiplier()} forced {@code 1.0} (idle practice grants no progress-scaled bonus, matching
 * today's fractional-XP/no-progress semantics).
 *
 * <p><b>{@link #commandBuffer()} is GUARANTEED non-null on every firing path (critique fix,
 * binding).</b> The engine's only two cycle-firing call sites - the real-cycle path and the
 * idle-cycle path - both run inside the per-world frame drain ({@code
 * StationService.tickFrameOnce}), which always holds a live {@code CommandBuffer} for that tick;
 * the fire site enforces this with a {@code @Nonnull} parameter, so a listener MAY dispatch its
 * own command-buffer-driven work (e.g. firing a {@code ProgressEvents.fire}) directly off it,
 * with no live-{@code Store} fallback needed.
 *
 * <p><b>Plain data</b> ({@link #playerId()}, {@link #sessionId()}, {@link #stationId()},
 * {@link #actionId()}, {@link #cycleIndex()}, {@link #idle()}, {@link #xpAsks()}, {@link
 * #toolMultiplier()}) is always safe to retain. <b>Live world-thread context</b> ({@link
 * #store()}, {@link #commandBuffer()}, {@link #playerRef()}) is valid ONLY synchronously during
 * dispatch; a listener that defers work must capture the plain fields and re-resolve.
 */
public final class StationCycleCompletedEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final CommandBuffer<EntityStore> commandBuffer;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    private final int cycleIndex;
    private final boolean idle;
    @Nonnull private final List<XpAsk> xpAsks;
    private final double toolMultiplier;

    public StationCycleCompletedEvent(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            int cycleIndex, boolean idle, @Nonnull List<XpAsk> xpAsks, double toolMultiplier) {
        this.store = store;
        this.commandBuffer = commandBuffer;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.actionId = actionId;
        this.cycleIndex = cycleIndex;
        this.idle = idle;
        this.xpAsks = List.copyOf(xpAsks);
        this.toolMultiplier = toolMultiplier;
    }

    @Nonnull
    public Store<EntityStore> store() {
        return store;
    }

    /** GUARANTEED non-null - see the class javadoc. */
    @Nonnull
    public CommandBuffer<EntityStore> commandBuffer() {
        return commandBuffer;
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

    /** 1-based cycle count for this session (real + idle cycles both increment it). */
    public int cycleIndex() {
        return cycleIndex;
    }

    public boolean idle() {
        return idle;
    }

    /** The station's {@code Work.Xp} asks; for an idle cycle already scaled by {@code Work.Idle.XpFraction}. */
    @Nonnull
    public List<XpAsk> xpAsks() {
        return xpAsks;
    }

    /** The resolved tool-power multiplier for a real cycle; forced {@code 1.0} for an idle cycle. */
    public double toolMultiplier() {
        return toolMultiplier;
    }
}
