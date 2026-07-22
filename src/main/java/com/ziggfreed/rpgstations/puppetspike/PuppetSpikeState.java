package com.ziggfreed.rpgstations.puppetspike;

import java.util.concurrent.ScheduledFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Per-player bookkeeping for the P0 puppet-presentation self-hide SPIKE (see
 * {@link PuppetSpikeService}'s own javadoc for the whole harness - this class is TEMPORARY,
 * deleted with the rest of the {@code puppetspike} package once the spike verdict lands).
 * Plain mutable fields, mirroring {@code station.StationSession}'s own style. Never persisted -
 * an in-memory map entry only, lost on a full server restart by design; {@link
 * PuppetSpikeService#safetyNetOnReady} does NOT depend on this surviving one (it unconditionally
 * re-asserts scale/model/hide on every {@code PlayerReadyEvent} regardless of bookkeeping).
 */
final class PuppetSpikeState {

    /** The caller's own {@code PlayerRef}, captured at apply time - the ONLY handle onto
     * {@link com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager} (the
     * {@code "hidden"} route's revert target; not reachable via {@link Ref}/{@link Store}). */
    @Nonnull
    final PlayerRef playerRef;

    /** The caller's own entity ref/store, captured at apply time (the {@code "scale"}/{@code
     * "modelswap"} routes' revert target). */
    @Nonnull
    final Ref<EntityStore> callerRef;
    @Nonnull
    final Store<EntityStore> callerStore;

    /** The spawned puppet, or {@code null} if the spawn failed (the hide route still applies
     * standalone - a spawn failure is never a hide-apply failure). */
    @Nullable
    Ref<EntityStore> puppetRef;

    /** {@code "scale"|"modelswap"|"hidden"}, or {@code null} when no hide route is active. */
    @Nullable
    String hideRoute;

    /**
     * {@code "scale"} revert payload: the prior {@link
     * com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent} scale, or
     * {@code null} when no such component existed before applying the route (revert then REMOVES
     * the component instead of restoring it to {@code 1.0}, matching the entity's true baseline).
     */
    @Nullable
    Float savedScale;

    /**
     * The repeating swing-clip re-fire task (round-4 harness-bug fix: the clip used to fire
     * ONCE, synchronously, inside the same world-thread hop that just spawned the puppet -
     * before the entity's tracker registration could mark it visible to any viewer, so the
     * packet was silently dropped; see {@link PuppetSpikeService#startAnimationBeat}'s own
     * javadoc for the full source trail). Cancelled wherever the puppet is despawned so a stray
     * beat never targets a dead/reused ref.
     */
    @Nullable
    ScheduledFuture<?> animationBeatTask;

    PuppetSpikeState(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> callerRef,
            @Nonnull Store<EntityStore> callerStore) {
        this.playerRef = playerRef;
        this.callerRef = callerRef;
        this.callerStore = callerStore;
    }
}
