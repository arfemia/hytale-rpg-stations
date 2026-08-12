package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The {@code C} (context) type parameter of the {@code station.step} {@link StationStepKernel}
 * walk: a per-program-run bundle, rebuilt FRESH by {@link StationService} on every
 * {@code tickFrameOnce} drain that dispatches or resumes a program (never retained across
 * frames itself - see {@code CastKernel#runResumable}'s "fresh ctx each call" contract). Resume
 * state that MUST survive a suspension lives on {@link StationSession} instead (its
 * {@code programIndex}/{@code programSuspended}/{@code stepDeadlineMs} fields), never here.
 */
final class StationStepContext {

    @Nonnull final StationSession session;
    @Nonnull final Store<EntityStore> store;
    @Nonnull final CommandBuffer<EntityStore> commandBuffer;
    @Nonnull final Player player;
    @Nonnull final ActionResolver.ResolvedAction action;
    @Nonnull final FactorLookup snapshot;
    @Nonnull final List<StationStep> steps;

    /**
     * This attempt's 1-based cycle index (design section 7.2's cycle-completed event contract):
     * {@code session.cyclesDone + 1} for a real cycle, computed ONCE before the walk starts and
     * used by BOTH the factor snapshot's context and a {@code Roll}/{@code Command} step's
     * placeholder substitution - deliberately NOT {@code session.cyclesDone} read live (that
     * field only advances after the whole program COMPLETES, matching the pre-refactor "only
     * count a real success" invariant; see {@code StationService#runRealCycle}).
     */
    final int cycleIndex;

    StationStepContext(@Nonnull StationSession session, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Player player,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull FactorLookup snapshot,
            @Nonnull List<StationStep> steps, int cycleIndex) {
        this.session = session;
        this.store = store;
        this.commandBuffer = commandBuffer;
        this.player = player;
        this.action = action;
        this.snapshot = snapshot;
        this.steps = steps;
        this.cycleIndex = cycleIndex;
    }
}
