package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.ziggfreed.common.cast.step.StepHandler;
import com.ziggfreed.common.entity.performer.WalkHandle;
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.api.EnhanceStamper;
import com.ziggfreed.rpgstations.api.StampInspection;
import com.ziggfreed.rpgstations.api.StampResult;
import com.ziggfreed.rpgstations.api.impl.EnhanceStamperRegistryImpl;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.CommandRewardExecutor;
import com.ziggfreed.rpgstations.loot.LootEngine;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.util.InventoryAccess;
import com.ziggfreed.rpgstations.util.ItemGrantUtil;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The ONE composite {@code station.step} handler (scope-2 design 2.1, decision 34) plus the pure
 * per-phase execution bodies it walks. The pre-scope-2 {@code Type} union and its seven per-type
 * handlers are GONE; {@link CompositeStepHandler} walks the FIXED phase order per iteration and
 * owns the per-step {@code Repeat} + {@code Duration} suspend/resume machinery.
 *
 * <p>{@link StationStepRegistry} wraps {@link CompositeStepHandler} in the conditions-gate +
 * throw-guard layer, so the phase bodies below may assume a step's {@code Conditions} already
 * passed and never need their own top-level try/catch for an UNEXPECTED throw - only for the
 * SPECIFIC failure modes they want a more precise {@link StationService.StopReason} for
 * (Consume/Produce map an inventory-mutation throw to {@code INVENTORY_FULL}).
 */
final class StationStepHandlers {

    private StationStepHandlers() {
    }

    /**
     * The composite handler: for each {@code Repeat} iteration, walk {@code Walk} (wave-3 deny)
     * -&gt; {@code Consume} -&gt; {@code Stamp} -&gt; {@code Produce} -&gt; {@code Roll} -&gt;
     * {@code Commands} -&gt; {@code Presentation}/{@code Puppet.Clip}/{@code Puppet.Prop} entry cues
     * -&gt; {@code Duration} hold. A {@code Duration} suspends the walk (reusing the retired
     * {@code Wait} type's suspend/resume math); {@link StationSession#stepIteration} +
     * {@link StationSession#stepDeadlineMs} carry the resume state across ticks so a repeated,
     * duration-holding step resumes at the correct iteration without re-running its earlier
     * iterations' mutations.
     */
    static final class CompositeStepHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationSession s = ctx.session;
            long now = System.currentTimeMillis();

            // === WALK in flight (scope-2 wave 3, design 2.3)? Advance it every frame ===
            // A Walk phase suspends WITHOUT a stepDeadline (deadline stays 0 => the frame drain
            // re-enters here every frame), driving the puppet until it arrives or times out.
            if (s.walkState != null) {
                if (!advanceWalk(ctx, now)) {
                    return StationStepResult.suspend(now); // still walking; re-enter next frame
                }
                s.walkState = null; // arrived - the walk for iteration s.stepIteration is done
                return runIterations(ctx, step, now, true, s.stepRepeatCount);
            }

            // === Duration hold resume (a prior tick committed a deadline for this iteration) ===
            boolean resumed = false;
            if (s.stepDeadlineMs != 0L) {
                if (!StationStepDecisions.durationDue(now, s.stepDeadlineMs)) {
                    return StationStepResult.suspend(s.stepDeadlineMs);
                }
                s.stepDeadlineMs = 0L;
                s.stepIteration++; // the iteration that was holding is complete - advance past it.
                resumed = true;
            }

            // Repeat resolves ONCE at step entry (design 2.1, m1): a fresh entry computes the count;
            // a Duration-hold/walk resume reuses the value cached at the hold, so a factor-scaled
            // Repeat combined with a Duration hold never re-resolves its iteration count mid-loop.
            int repeatCount;
            if (resumed) {
                repeatCount = s.stepRepeatCount;
            } else {
                double contribution = StationStepDecisions.repeatFactorContribution(step.getRepeat(), ctx.snapshot::resolve);
                repeatCount = StationStepDecisions.resolveRepeatCount(step.getRepeat(), contribution);
                s.stepRepeatCount = repeatCount; // cache so a walk resume can read it back
            }

            return runIterations(ctx, step, now, false, repeatCount);
        }
    }

    /**
     * Walks the fixed phase order for iterations {@code [s.stepIteration, repeatCount)}: the
     * {@code Walk} phase FIRST (suspending the walk across ticks), then
     * {@code Consume -> Stamp -> Produce -> Roll -> Commands -> entry cues -> Duration}. On a
     * post-walk-arrival re-entry {@code walkAlreadyDone} skips the Walk phase for the CURRENT
     * iteration only (its walk already completed); every later iteration runs its own walk again.
     */
    private static StationStepResult runIterations(@Nonnull StationStepContext ctx, @Nonnull StationStep step,
            long now, boolean walkAlreadyDone, int repeatCount) {
        StationSession s = ctx.session;
        for (int i = s.stepIteration; i < repeatCount; i++) {
            s.stepIteration = i;

            // WALK phase (first). A fresh iteration begins the walk (suspends to drive it); a
            // post-arrival re-entry skips it for THIS iteration.
            if (!walkAlreadyDone && step.getWalk() != null) {
                StationStepResult wr = beginWalk(ctx, step, now, repeatCount);
                if (wr != null) {
                    return wr; // suspend (walk driving) or fail (unreachable at walk-step entry)
                }
            }
            walkAlreadyDone = false;

            StationStepResult phase;
            if ((phase = consumePhase(ctx, step)) != null) {
                s.stepIteration = 0;
                return phase;
            }
            if ((phase = StampHandler.executeStampPhase(ctx, step)) != null) {
                s.stepIteration = 0;
                return phase;
            }
            if ((phase = producePhase(ctx, step)) != null) {
                s.stepIteration = 0;
                return phase;
            }
            if ((phase = rollPhase(ctx, step)) != null) {
                s.stepIteration = 0;
                return phase;
            }
            if ((phase = commandsPhase(ctx, step)) != null) {
                s.stepIteration = 0;
                return phase;
            }

            emitEntryCues(ctx, step);

            StationStep.Duration duration = step.getDuration();
            if (duration != null && duration.effectiveMs() > 0) {
                long deadline = StationStepDecisions.commitOrReadDeadline(now, duration.effectiveMs(), 0L);
                s.stepDeadlineMs = deadline;
                s.stepIteration = i;
                s.stepRepeatCount = repeatCount; // cache the resolved count for the resume (m1)
                if (!StationStepDecisions.durationDue(now, deadline)) {
                    return StationStepResult.suspend(deadline);
                }
                s.stepDeadlineMs = 0L;
            }
        }

        s.stepIteration = 0;
        return StationStepResult.SUCCESS;
    }

    // ==================== Walk phase (scope-2 wave 3, design 2.3) ====================

    /**
     * Begins the {@code Walk} phase (design 2.3), routed through the performer seam (decision 55,
     * F2): resolves the target anchor column, then hands the walk to {@code s.performer.walkTo} - the
     * Holder backend re-solves the SAME bounded-A* {@code PuppetNav} path and drives
     * {@code PlayerPuppetService.walkTick} under the hood (byte-parity for the PlayerClone puppet),
     * the NpcRole backend drives its NATIVE gait toward a marked target, and {@code walkTo} itself
     * flips the puppet's walk state on. Stores the returned {@link WalkHandle} on the session and
     * returns a {@code Suspend} so the frame drain polls it. Returns a {@code Fail} when there is no
     * active performer ({@code WALK_REQUIRES_PUPPET} + engage already gate this) or the anchor is
     * unresolved / unreachable (the {@code walkTo} FAILED handle). Returns {@code null} ONLY if there
     * is nothing to walk (never, since the caller guards {@code step.getWalk() != null}).
     */
    @Nullable
    private static StationStepResult beginWalk(@Nonnull StationStepContext ctx, @Nonnull StationStep step,
            long now, int repeatCount) {
        StationSession s = ctx.session;
        StationStep.Walk walk = step.getWalk();
        if (walk == null) {
            return null;
        }
        String targetAnchor = walk.getTo();
        if (!s.puppetActive || s.performer == null || !s.performer.isAlive()) {
            s.stepIteration = 0;
            return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "Walk step '" + step.getId() + "' requires an active Puppet");
        }
        Vector3d target = StationService.getInstance().resolveWalkTarget(s, targetAnchor);
        if (target == null) {
            s.stepIteration = 0;
            return StationStepResult.fail(StationService.StopReason.PATH_BLOCKED,
                    "Walk step '" + step.getId() + "' cannot reach anchor '" + targetAnchor + "'");
        }
        // Drive the walk through the ONE performer seam (no look-source branch): the backend picks
        // the mechanism (Holder = PuppetNav/walkTick, NpcRole = native gait). The starting frame's
        // accessor is the tick-safe commandBuffer (path solve + the walk-state archetype add).
        WalkHandle handle = s.performer.walkTo(ctx.commandBuffer, target, walk.effectiveSpeedMps());
        if (handle.state() == WalkHandle.State.FAILED) {
            s.stepIteration = 0;
            return StationStepResult.fail(StationService.StopReason.PATH_BLOCKED,
                    "Walk step '" + step.getId() + "' cannot reach anchor '" + targetAnchor + "'");
        }
        Vector3d blockCenter = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        s.walkState = new StationWalkState(
                targetAnchor != null && !targetAnchor.isBlank() ? targetAnchor : ActionDef.Anchor.RESERVED_SELF,
                handle, walk.effectiveSpeedMps(), blockCenter.distance(target), now);
        s.stepRepeatCount = repeatCount; // cache for the per-frame walk resume
        // Sync the walk step's carried prop at DEPARTURE so the puppet holds it while walking (the
        // fish exemplar's walkout carries the raw fish); emitEntryCues re-syncs on arrival (a cheap
        // dirty-gated no-op). No-op for a step authoring no prop / a puppet-less session.
        StationPuppetController.syncStepProp(s, ctx.commandBuffer, ctx.player, step);
        return StationStepResult.suspend(now); // suspend (deadline stays 0) so the drain re-enters each frame
    }

    /**
     * Advances the in-flight walk one frame (design 2.3), by POLLING the performer's
     * {@link WalkHandle} (decision 55, F2): {@code poll} over the live store advances the Holder's
     * transform (the in-place {@code walkTick} write the engine tracker broadcasts each frame -
     * byte-parity with the pre-seam path) or measures the NpcRole's distance to its marked target.
     * Returns {@code true} when the handle terminates (ARRIVED / STUCK / FAILED) or design 2.3's
     * anti-wedge timeout fires while still WALKING; {@code false} = still walking. A gone performer
     * is treated as arrived (never wedge). Terminal-state mapping lives in the pure
     * {@link StationStepDecisions#walkStepDone}.
     */
    private static boolean advanceWalk(@Nonnull StationStepContext ctx, long now) {
        StationSession s = ctx.session;
        StationWalkState w = s.walkState;
        if (w == null || s.performer == null || !s.performer.isAlive()) {
            return true;
        }
        double dtMs = Math.max(0.0, now - w.lastTickMs);
        w.lastTickMs = now;
        WalkHandle.State state = w.handle.poll(ctx.store, dtMs);
        boolean timedOut = state == WalkHandle.State.WALKING && w.timedOut(now);
        if (timedOut) {
            w.handle.cancel(); // still walking past the grace window - drop the walk, complete the step
        }
        return StationStepDecisions.walkStepDone(state, timedOut);
    }

    // ==================== Per-iteration entry cues (presentation + puppet clip/prop) ====================

    /**
     * Fire the step's iteration-entry cues once per FRESH iteration (never a Duration-hold resume
     * re-check - the composite handler only reaches this on a genuine fresh iteration, so the
     * decision cores are passed a {@code null} resumingStep = "emit"): the step's own
     * {@code Puppet.Clip} (the step-synced swing), a re-sync of the puppet's held {@code Puppet.Prop}
     * (the step's override, else the session default), and the step's own {@code Presentation}
     * moment (played AFTER the mutation phases, byte-equivalent to the old Present step running last
     * in the four-step implicit program). No-op for a session with no puppet (the controller's own
     * guards) or a step authoring none of these.
     */
    private static void emitEntryCues(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        if (StationStepDecisions.shouldPlayClipOnEntry(step, null)) {
            StationPuppetController.playStepClip(ctx.session, ctx.store, step.getPuppet().getClip());
        }
        if (StationStepDecisions.shouldSyncPropOnEntry(step, null)) {
            StationPuppetController.syncStepProp(ctx.session, ctx.commandBuffer, ctx.player, step);
        }
        if (StationStepDecisions.shouldEmitPresentationOnEntry(step, null)) {
            Vector3d blockPos = new Vector3d(ctx.session.blockX + 0.5, ctx.session.blockY + 0.5,
                    ctx.session.blockZ + 0.5);
            StationService.emitMoment(ctx.store, ctx.session, presentMomentId(ctx, step), step.getPresentation(),
                    blockPos);
        }
    }

    /** The per-step moment id: {@code step:<actionId>:<stepId>} when the step authors an Id, else {@link StationFlairs#MOMENT_CYCLE}. */
    @Nonnull
    static String presentMomentId(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        String stepId = step.getId();
        if (stepId != null && !stepId.isBlank()) {
            return StationFlairs.stepMomentId(ctx.action.getActionId(), stepId);
        }
        return StationFlairs.MOMENT_CYCLE;
    }

    // ==================== Consume phase ====================

    /**
     * Removes {@code Consume.Quantity} of the item/resource-type from the player's inventory
     * (storage-first) or from the station's placed-input custody claim ({@code From:"Custody"}).
     * Returns {@code null} on success (or no {@code Consume} phase authored - a no-op), or a
     * {@link StationStepResult.Fail} the composite handler propagates.
     */
    @Nullable
    static StationStepResult consumePhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        StationStep.Consume consume = step.getConsume();
        if (consume == null) {
            return null;
        }
        int quantity = consume.effectiveQuantity();
        String resourceTypeId = consume.getResourceTypeId();
        boolean isResource = resourceTypeId != null && !resourceTypeId.isBlank();
        String itemId = consume.getItemId();
        String inputRef = isResource ? resourceTypeId : itemId;
        if (inputRef == null || inputRef.isBlank()) {
            return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "Consume step '" + step.getId() + "' has neither ItemId nor ResourceTypeId");
        }
        if (StationStep.Consume.FROM_CUSTODY.equalsIgnoreCase(consume.effectiveFrom())) {
            return consumeFromCustody(ctx, step, isResource, resourceTypeId, itemId, quantity);
        }
        if (!StationStep.Consume.FROM_INVENTORY.equalsIgnoreCase(consume.effectiveFrom())) {
            Log.warn("STATION Consume step '" + step.getId() + "' authors From '" + consume.effectiveFrom()
                    + "' which has no handler (only 'Inventory'/'Custody' are implemented)");
            return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "Consume.From '" + consume.effectiveFrom() + "' is not implemented");
        }
        try {
            if (isResource) {
                ResourceQuantity resource = new ResourceQuantity(inputRef, quantity);
                ResourceTransaction tx = InventoryAccess.storageOf(ctx.player).canRemoveResource(resource)
                        ? InventoryAccess.storageOf(ctx.player).removeResource(resource)
                        : InventoryAccess.combinedBackpackStorageHotbarOf(ctx.player).removeResource(resource);
                StationService.tallyResourceConsumption(ctx.session, tx, inputRef);
                // Iteration refund ledger (design 2.5/M1): record the REAL drained ids so a
                // mid-iteration stop refunds them - unless a Produce.To:Custody clears the ledger.
                StationService.recordIterationConsumedResource(ctx.session, tx, inputRef);
            } else {
                ItemStack input = new ItemStack(inputRef, quantity);
                if (InventoryAccess.storageOf(ctx.player).canRemoveItemStack(input)) {
                    InventoryAccess.storageOf(ctx.player).removeItemStack(input);
                } else {
                    InventoryAccess.combinedBackpackStorageHotbarOf(ctx.player).removeItemStack(input);
                }
                ctx.session.consumedItems.merge(inputRef, quantity, Integer::sum);
                StationService.recordIterationConsumedItem(ctx.session, inputRef, quantity);
            }
        } catch (Throwable t) {
            Log.warn("STATION Consume step failed for '" + ctx.session.stationId + "': " + t.getMessage());
            return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL, t.getMessage());
        }
        return null;
    }

    /**
     * Drains {@code quantity} of {@code itemId}/{@code resourceTypeId} from the block's live claim,
     * tallying the REAL drained item ids into the session ledger. A short drain fails
     * {@code OUT_OF_INPUTS}, the same reason an empty custody station denies at engage.
     */
    @Nullable
    private static StationStepResult consumeFromCustody(@Nonnull StationStepContext ctx, @Nonnull StationStep step,
            boolean isResource, @Nullable String resourceTypeId, @Nullable String itemId, int quantity) {
        // Custody drains from the step's At-anchor block (scope-2 wave 3, design 2.2) - the primary
        // block for a null/self At, a remote anchor's claim otherwise.
        StationCustodyClaim claim = StationService.getInstance().custodyClaimForAnchor(ctx.session, step.getAt());
        Map<String, Integer> drainedOut = new LinkedHashMap<>();
        int drained = StationCustody.drain(claim, isResource ? null : itemId, isResource ? resourceTypeId : null,
                quantity, StationService::liveResourceTypeIdsOf, drainedOut);
        if (drained < quantity) {
            // Design 2.4: a REPEATING program's shortage is the graceful natural end
            // (INPUTS_EXHAUSTED); a non-repeating one keeps OUT_OF_INPUTS.
            boolean repeating = ctx.action.getWork() != null && ctx.action.getWork().effectiveRepeat();
            return StationStepResult.fail(StationService.shortInputStopReason(repeating),
                    "Consume step '" + step.getId() + "' custody ran short ("
                            + drained + "/" + quantity + " of '" + (isResource ? resourceTypeId : itemId) + "')");
        }
        for (Map.Entry<String, Integer> e : drainedOut.entrySet()) {
            ctx.session.consumedItems.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        StationService.recordIterationConsumedMap(ctx.session, drainedOut);
        return null;
    }

    // ==================== Produce phase ====================

    /**
     * Commits a produce phase. {@code To:"Custody"} (wave 3) stores into the step's {@code At}-anchor
     * custody claim; {@code To:"Inventory"} adds {@code Produce.Quantity} of {@code Produce.ItemId} to
     * the player, hotbar-first then backpack storage then drop-at-block. EITHER committed destination
     * clears the current iteration's refund ledger (review minor m1) - the consumed inputs became the
     * produced output, so a later stop never both refunds the inputs AND keeps the output. Returns
     * {@code null} on success/no-op.
     */
    @Nullable
    static StationStepResult producePhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        StationStep.Produce produce = step.getProduce();
        if (produce == null) {
            return null;
        }
        if (produce.getItemId() == null || produce.getItemId().isBlank()) {
            return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "Produce step '" + step.getId() + "' has no Produce.ItemId");
        }
        int quantity = produce.effectiveQuantity();
        String to = produce.effectiveTo();

        // To:"Custody" (scope-2 wave 3, design 2.2): store the output into the step's At-anchor
        // custody claim (the primary block for a null/self At), then clear the iteration refund
        // ledger (M1: the consumed inputs BECAME this custody item - refund + custody-return are
        // mutually exclusive; returnCustody now hands the produced item back instead).
        if (StationStep.Produce.TO_CUSTODY.equalsIgnoreCase(to)) {
            try {
                boolean placed = StationService.getInstance().produceIntoCustody(ctx.session, ctx.commandBuffer,
                        step.getAt(), produce.getItemId(), quantity);
                if (!placed) {
                    return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                            "Produce step '" + step.getId() + "' could not resolve its To:Custody anchor '"
                                    + step.getAt() + "'");
                }
                ctx.session.producedItems.merge(produce.getItemId(), quantity, Integer::sum);
                StationService.clearIterationLedgerOnCommittedProduce(ctx.session);
            } catch (Throwable t) {
                Log.warn("STATION Produce To:Custody step failed for '" + ctx.session.stationId + "': "
                        + t.getMessage());
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED, t.getMessage());
            }
            return null;
        }
        if (!StationStep.Produce.TO_INVENTORY.equalsIgnoreCase(to)) {
            Log.warn("STATION Produce step '" + step.getId() + "' authors To '" + to
                    + "' which has no handler (only 'Inventory'/'Custody' are implemented)");
            return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "Produce.To '" + to + "' is not implemented");
        }
        try {
            ItemGrantUtil.grant(ctx.player, new ItemStack(produce.getItemId(), quantity), ctx.store,
                    ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
            ctx.session.producedItems.merge(produce.getItemId(), quantity, Integer::sum);
            // M1 (review minor m1): the output is now committed to the inventory, so the consumed
            // inputs are spent - clear the refund ledger, exactly as the To:Custody branch does, or a
            // stop on a later Duration suspend in this same iteration would double-grant.
            StationService.clearIterationLedgerOnCommittedProduce(ctx.session);
            if (ctx.session.playerRef != null) {
                StationService.notifyItemGain(ctx.session.playerRef, produce.getItemId(), quantity, false);
            }
        } catch (Throwable t) {
            Log.warn("STATION Produce step failed for '" + ctx.session.stationId + "': " + t.getMessage());
            return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL, t.getMessage());
        }
        return null;
    }

    // ==================== Roll phase ====================

    /**
     * Evaluates + grants a loot pass through the SAME {@code loot.LootEngine} a station's {@code Loot}
     * group uses, over the step's {@link LootRef} ({@code Lootables[]} + inline {@code Rolls[]}).
     * Returns {@code null} always (a loot pass never fails a step; a full inventory drops at the
     * block).
     */
    @Nullable
    static StationStepResult rollPhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        LootRef ref = step.getRoll();
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        List<Roll> rolls = new ArrayList<>();
        String[] lootables = ref.getLootables();
        if (lootables != null) {
            for (String lootableId : lootables) {
                if (lootableId == null || lootableId.isBlank()) {
                    continue;
                }
                LootableAsset table = LootableCatalog.getInstance().get(lootableId);
                if (table != null && table.getRolls() != null) {
                    rolls.addAll(Arrays.asList(table.getRolls()));
                } else {
                    Log.fine("STATION Roll step '" + step.getId() + "' references unknown lootable '" + lootableId + "'");
                }
            }
        }
        if (ref.getRolls() != null) {
            rolls.addAll(Arrays.asList(ref.getRolls()));
        }
        if (rolls.isEmpty()) {
            return null;
        }
        LootEngine.GrantResult result = LootEngine.rollAndGrant(rolls, Roll.TRIGGER_CYCLE, ctx.snapshot,
                ctx.player, ctx.cycleOutputForBonusCopies, ctx.session.playerRef, ctx.session.stationId,
                ctx.action.getActionId(), ctx.cycleIndex, ctx.store,
                ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
        StationService.applyGrantResult(ctx.session, ctx.store, result);
        return null;
    }

    // ==================== Commands phase ====================

    /** Runs {@code Commands} through the SAME zero-code integration surface a {@code Roll.Grants.Commands} uses. */
    @Nullable
    static StationStepResult commandsPhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        String[] commands = step.getCommands();
        if (commands == null || commands.length == 0 || ctx.session.playerRef == null) {
            return null;
        }
        CommandRewardExecutor.Placeholders placeholders = CommandRewardExecutor.Placeholders.of(
                ctx.session.playerRef, ctx.session.stationId, ctx.action.getActionId(), ctx.cycleIndex);
        for (String raw : commands) {
            if (raw != null && !raw.isBlank()) {
                CommandRewardExecutor.run(raw, placeholders);
            }
        }
        return null;
    }

    // ==================== Stamp phase (kept in a StampHandler namespace for the pure-mutation test) ====================

    /**
     * The anvil's enhance-commit phase (design 9.5, critique M5's binding fix): COMPUTE everything
     * first with ZERO mutation (roll + cap-clamp the {@code Stats} leaf via {@link StampCapEngine},
     * validate reagent availability, validate the enhanced weapon can be returned), THEN commit
     * reagent consumption + the durability/stat mutation under one restore-on-failure discipline -
     * custody's live {@link StationCustodyClaim#uniqueStack()} is the ONE write, the very last line.
     * Named {@code StampHandler} (though no longer a {@code StepHandler}) so the pure-mutation test's
     * {@code StationStepHandlers.StampHandler.applyStampMutation}/{@code Mutation} references keep
     * resolving.
     */
    static final class StampHandler {

        private StampHandler() {
        }

        /** Returns {@code null} on success (or no {@code Stamp} phase authored), or a Fail the composite handler propagates. */
        @Nullable
        static StationStepResult executeStampPhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
            StationStep.Stamp stamp = step.getStamp();
            if (stamp == null) {
                return null;
            }
            Custody custody = ctx.action.getCustody();
            StationCustodyClaim claim = custody != null
                    ? StationService.getInstance().custodyClaimFor(ctx.session.blockKey) : null;
            ItemStack weaponStack = claim != null ? claim.uniqueStack() : null;
            if (weaponStack == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' has no custody item to enhance");
            }

            // ===== COMPUTE PHASE (zero mutation, per M5) =====
            StationStep.Stamp.Stats statsGroup = stamp.getStats();
            EnhanceStamper stamper = EnhanceStamperRegistryImpl.getInstance().active();
            StampInspection inspection = StampInspection.empty();
            StampCapEngine.Plan plan = StampCapEngine.Plan.NOTHING_TO_GRANT;
            if (statsGroup != null) {
                if (stamper == null) {
                    Log.fine("STAMP step '" + step.getId() + "' authors Stats with no registered EnhanceStamper "
                            + "- the Stats leaf no-ops this attempt (Durability still lands)");
                } else {
                    inspection = safeInspect(stamper, weaponStack);
                    StampCapEngine.FactorLookup lookup = ctx.snapshot::resolve;
                    StampCapEngine.RollSource rng = () -> ThreadLocalRandom.current().nextDouble();
                    plan = StampCapEngine.resolve(statsGroup, inspection, lookup, rng);
                    if (plan.denied()) {
                        return StationStepResult.fail(StationService.StopReason.ENHANCE_CAPPED,
                                "Stamp step '" + step.getId() + "' fully capped for '"
                                        + weaponStack.getItemId() + "'");
                    }
                }
            }

            Ingredient[] reagents = stamp.getReagents();
            double repeatCostMultiplier = economicsMultiplier(statsGroup);
            int stampCount = stamper != null ? inspection.stampCount() : 0;
            if (reagents != null) {
                for (Ingredient r : reagents) {
                    if (r == null) {
                        continue;
                    }
                    int effectiveQty = effectiveReagentQuantity(r.effectiveQuantity(), repeatCostMultiplier, stampCount);
                    boolean isResource = r.getResourceTypeId() != null && !r.getResourceTypeId().isBlank();
                    String reagentRef = isResource ? r.getResourceTypeId() : r.getItemId();
                    if (reagentRef == null || reagentRef.isBlank()) {
                        continue;
                    }
                    if (!reagentAvailable(ctx.player, isResource, reagentRef, effectiveQty)) {
                        return StationStepResult.fail(StationService.StopReason.OUT_OF_INPUTS,
                                "Stamp step '" + step.getId() + "' reagents unavailable ('" + reagentRef + "' x"
                                        + effectiveQty + ")");
                    }
                }
            }

            if (!InventoryAccess.storageOf(ctx.player).canAddItemStacks(List.of(weaponStack))) {
                return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL,
                        "Stamp step '" + step.getId() + "' - no room to return the enhanced item later");
            }

            // ===== COMMIT PHASE (mutation, restore-on-failure per M5) =====
            List<ItemStack> consumedForRestore = new ArrayList<>();
            try {
                if (reagents != null) {
                    for (Ingredient r : reagents) {
                        if (r == null) {
                            continue;
                        }
                        int effectiveQty = effectiveReagentQuantity(r.effectiveQuantity(), repeatCostMultiplier, stampCount);
                        boolean isResource = r.getResourceTypeId() != null && !r.getResourceTypeId().isBlank();
                        String reagentRef = isResource ? r.getResourceTypeId() : r.getItemId();
                        if (reagentRef == null || reagentRef.isBlank()) {
                            continue;
                        }
                        consumedForRestore.addAll(consumeReagent(ctx.player, isResource, reagentRef, effectiveQty));
                    }
                }
            } catch (Throwable t) {
                restoreReagents(ctx.player, consumedForRestore);
                Log.warn("STAMP step '" + step.getId() + "' reagent consumption failed, restored: " + t.getMessage(), t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' reagent consumption failed: " + t.getMessage());
            }

            Mutation mutation;
            try {
                mutation = applyStampMutation(weaponStack, stamp.getDurability(), plan, stamper);
            } catch (Throwable t) {
                restoreReagents(ctx.player, consumedForRestore);
                Log.warn("STAMP step '" + step.getId() + "' mutation failed, restored reagents: " + t.getMessage(), t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' mutation failed: " + t.getMessage());
            }
            claim.setUniqueStack(mutation.stack());

            StationService.tallyConsumedStacks(ctx.session, consumedForRestore);

            String weaponId = weaponStack.getItemId() != null ? weaponStack.getItemId() : "";
            StationEnhanceOutcome outcome = new StationEnhanceOutcome(weaponId, weaponStack, mutation.stack(),
                    mutation.lines(), mutation.durabilityAdded());
            ctx.session.enhanceOutcomes.add(outcome);
            if (ctx.session.playerRef != null) {
                StationEvents.fireEnhanceCompleted(ctx.store, ctx.session.playerRef, ctx.session.playerUuid,
                        ctx.session.sessionId, ctx.session.stationId, ctx.action.getActionId(), outcome);
            }
            return null;
        }

        /**
         * PURE: applies {@code Durability.AddMax} then the (already rolled + cap-clamped) {@code plan}
         * entries via {@code stamper}, in that order, returning a {@link Mutation} (the new stack +
         * the provider's {@link EnhanceLine} report + the max-durability delta). Both mutations are
         * {@code ItemStack} with-copy operations, so no live server/Player is needed here (unit-tested
         * directly, incl. a THROWING stamper - proves a mutation failure never reaches
         * {@link StationCustodyClaim#setUniqueStack}, the caller's job).
         */
        @Nonnull
        static Mutation applyStampMutation(@Nonnull ItemStack weaponStack,
                @Nullable StationStep.Stamp.Durability durabilityGroup, @Nonnull StampCapEngine.Plan plan,
                @Nullable EnhanceStamper stamper) {
            ItemStack mutated = weaponStack;
            double durabilityAdded = 0.0;
            if (durabilityGroup != null && durabilityGroup.getAddMax() != null && durabilityGroup.getAddMax() > 0) {
                double addMax = durabilityGroup.getAddMax();
                durabilityAdded = addMax;
                mutated = mutated.withMaxDurability(mutated.getMaxDurability() + addMax)
                        .withIncreasedDurability(addMax);
            }
            List<EnhanceLine> lines = List.of();
            if (!plan.entries().isEmpty() && stamper != null) {
                StampResult result = stamper.apply(mutated, plan.entries());
                mutated = result.stack();
                lines = result.lines();
            }
            return new Mutation(mutated, lines, durabilityAdded);
        }

        /** The pure result of {@link #applyStampMutation}: the mutated stack, the provider's verbatim report, and the max-durability delta. */
        record Mutation(@Nonnull ItemStack stack, @Nonnull List<EnhanceLine> lines, double durabilityAdded) {
        }

        /** Best-effort restore: each stack failing independently is logged, never re-thrown. */
        private static void restoreReagents(@Nonnull Player player, @Nonnull List<ItemStack> toRestore) {
            for (ItemStack restore : toRestore) {
                if (restore != null) {
                    try {
                        InventoryAccess.storageOf(player).addItemStack(restore);
                    } catch (Throwable restoreFailure) {
                        Log.warn("STAMP restore failed for '" + restore.getItemId() + "': " + restoreFailure.getMessage());
                    }
                }
            }
        }

        /** Never-throwing {@link EnhanceStamper#inspect} - a bad third-party stamper must never crash a ritual. */
        @Nonnull
        private static StampInspection safeInspect(@Nonnull EnhanceStamper stamper, @Nonnull ItemStack stack) {
            try {
                return stamper.inspect(stack);
            } catch (Throwable t) {
                Log.warn("STAMP EnhanceStamper#inspect threw, treating as bare: " + t.getMessage());
                return StampInspection.empty();
            }
        }

        /** The REAL drained stack(s) for {@code tx} (a ResourceTypeId route can drain several concrete item ids), for a precise restore. */
        @Nonnull
        private static List<ItemStack> drainedStacksOf(@Nullable ResourceTransaction tx) {
            List<ItemStack> out = new ArrayList<>();
            if (tx == null) {
                return out;
            }
            for (ResourceSlotTransaction slotTx : tx.getList()) {
                if (slotTx != null && slotTx.succeeded() && slotTx.getConsumed() > 0) {
                    ItemStack before = slotTx.getSlotBefore();
                    if (before != null && before.getItemId() != null) {
                        out.add(new ItemStack(before.getItemId(), slotTx.getConsumed()));
                    }
                }
            }
            return out;
        }

        /** {@code Stats.Caps.Economics.RepeatCostMultiplier}, or 0 (flat cost) when unauthored. */
        private static double economicsMultiplier(@Nullable StationStep.Stamp.Stats statsGroup) {
            if (statsGroup == null || statsGroup.getCaps() == null || statsGroup.getCaps().getEconomics() == null) {
                return 0.0;
            }
            Double m = statsGroup.getCaps().getEconomics().getRepeatCostMultiplier();
            return m != null ? m : 0.0;
        }

        /** {@code ceil(baseQuantity * (1 + RepeatCostMultiplier * stampCount))} (design 9.5, critique M2 fix (b)). */
        private static int effectiveReagentQuantity(int baseQuantity, double repeatCostMultiplier, int stampCount) {
            if (repeatCostMultiplier <= 0.0 || stampCount <= 0) {
                return baseQuantity;
            }
            return (int) Math.ceil(baseQuantity * (1.0 + repeatCostMultiplier * stampCount));
        }

        /** Pure availability query (storage then combined) - never mutates. */
        private static boolean reagentAvailable(@Nonnull Player player, boolean isResource, @Nonnull String ref,
                int quantity) {
            if (isResource) {
                ResourceQuantity resource = new ResourceQuantity(ref, quantity);
                return InventoryAccess.storageOf(player).canRemoveResource(resource)
                        || InventoryAccess.combinedBackpackStorageHotbarOf(player).canRemoveResource(resource);
            }
            ItemStack want = new ItemStack(ref, quantity);
            return InventoryAccess.storageOf(player).canRemoveItemStack(want)
                    || InventoryAccess.combinedBackpackStorageHotbarOf(player).canRemoveItemStack(want);
        }

        /** Storage-first-then-combined removal; returns the REAL drained stack(s) for the caller's restore-on-failure ledger. */
        @Nonnull
        private static List<ItemStack> consumeReagent(@Nonnull Player player, boolean isResource,
                @Nonnull String ref, int quantity) {
            if (isResource) {
                ResourceQuantity resource = new ResourceQuantity(ref, quantity);
                ResourceTransaction tx = InventoryAccess.storageOf(player).canRemoveResource(resource)
                        ? InventoryAccess.storageOf(player).removeResource(resource)
                        : InventoryAccess.combinedBackpackStorageHotbarOf(player).removeResource(resource);
                return drainedStacksOf(tx);
            }
            ItemStack want = new ItemStack(ref, quantity);
            if (InventoryAccess.storageOf(player).canRemoveItemStack(want)) {
                InventoryAccess.storageOf(player).removeItemStack(want);
            } else {
                InventoryAccess.combinedBackpackStorageHotbarOf(player).removeItemStack(want);
            }
            return List.of(want);
        }
    }
}
