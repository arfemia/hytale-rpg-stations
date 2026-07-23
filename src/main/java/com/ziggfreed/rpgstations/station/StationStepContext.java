package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.FactorSnapshot;

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
    @Nonnull final StationAsset asset;
    @Nonnull final ActionResolver.ResolvedAction action;
    @Nonnull final FactorSnapshot snapshot;
    @Nonnull final List<StationStep> steps;

    /**
     * This attempt's 1-based cycle index (design section 7.2's cycle-completed event contract):
     * {@code session.cyclesDone + 1} for a real cycle, computed ONCE before the walk starts and
     * used by BOTH the {@link FactorSnapshot}'s context and a {@code Roll}/{@code Command} step's
     * placeholder substitution - deliberately NOT {@code session.cyclesDone} read live (that
     * field only advances after the whole program COMPLETES, matching the pre-refactor "only
     * count a real success" invariant; see {@code StationService#runRealCycle}).
     */
    final int cycleIndex;

    /**
     * This program run's live cycle output, for a {@code Roll} step's
     * {@code Grants.BonusOutputCopies} (design 4.5.1's "THIS cycle's Output" - the implicit
     * program's Produce step already chose it via {@code ConversionCheck} before the walk
     * started; an authored program with no live conversion leaves this {@code null}, so
     * {@code BonusOutputCopies} is silently inert there, same as today's Completion-trigger
     * pass with no cycle output).
     */
    @Nullable final ItemStack cycleOutputForBonusCopies;

    /**
     * The step being RE-ENTERED this dispatch, when this run is a resume of a previously
     * suspended program ({@code StationService#resumeCycleProgram} -> {@code dispatchProgram}
     * with {@code resuming=true}); {@code null} for a fresh (non-resuming) dispatch. Identity-
     * compared (never {@code equals}) against the step {@link StationStepRegistry}'s generic
     * per-step Presentation hook is about to run for, so the suspend-resume RE-CHECK of the exact
     * step that already played its own Presentation on first entry never plays it a second time -
     * see {@code StationStepDecisions#shouldEmitPresentationOnEntry}, the pure decision core this
     * field feeds.
     */
    @Nullable final StationStep resumingStep;

    StationStepContext(@Nonnull StationSession session, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Player player, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull FactorSnapshot snapshot,
            @Nonnull List<StationStep> steps, int cycleIndex, @Nullable ItemStack cycleOutputForBonusCopies,
            @Nullable StationStep resumingStep) {
        this.session = session;
        this.store = store;
        this.commandBuffer = commandBuffer;
        this.player = player;
        this.asset = asset;
        this.action = action;
        this.snapshot = snapshot;
        this.steps = steps;
        this.cycleIndex = cycleIndex;
        this.cycleOutputForBonusCopies = cycleOutputForBonusCopies;
        this.resumingStep = resumingStep;
    }
}
