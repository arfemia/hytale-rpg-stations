package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.rpgstations.asset.StationAsset;
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
    /** The station the program runs at: what the Convert phase's selection and the Bonus pass resolve against. */
    @Nonnull final StationAsset asset;
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

    /**
     * The action's composed PACE (its own ladder and clamp plus every matching extension's ladder),
     * resolved against {@link #snapshot} once per fresh step entry by the composite handler.
     */
    @Nonnull final StationPacing.Composed pace;

    /**
     * The conversion the classic convert loop already chose BEFORE dispatch (its pre-dispatch
     * selection drives idle practice, the out-of-inputs and inventory-full stops, the per-conversion
     * pace and the feedable-cycles count), handed to the implicit program's {@code Convert} phase so
     * it runs exactly that row. Null for an authored program, whose {@code Convert} phase selects
     * at the beat.
     */
    @Nullable final StationService.ConversionCheck preselected;

    StationStepContext(@Nonnull StationSession session, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Player player,
            @Nonnull StationAsset asset, @Nonnull ActionResolver.ResolvedAction action,
            @Nonnull FactorLookup snapshot, @Nonnull List<StationStep> steps, int cycleIndex,
            @Nonnull StationPacing.Composed pace, @Nullable StationService.ConversionCheck preselected) {
        this.session = session;
        this.store = store;
        this.commandBuffer = commandBuffer;
        this.player = player;
        this.asset = asset;
        this.action = action;
        this.snapshot = snapshot;
        this.steps = steps;
        this.cycleIndex = cycleIndex;
        this.pace = pace;
        this.preselected = preselected;
    }
}
