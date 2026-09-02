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

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.cast.step.StepHandler;
import com.ziggfreed.common.command.CommandRunner;
import com.ziggfreed.common.entity.PlayerPuppetService;
import com.ziggfreed.common.entity.performer.WalkHandle;
import com.ziggfreed.common.inventory.InventoryGrant;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.stamp.RollPoolAsset;
import com.ziggfreed.common.loot.stamp.RollPoolConfig;
import com.ziggfreed.common.loot.stamp.StampCapEngine;
import com.ziggfreed.common.loot.stamp.StampInspection;
import com.ziggfreed.common.loot.stamp.StampPlan;
import com.ziggfreed.common.loot.stamp.StampSpec;
import com.ziggfreed.common.loot.stamp.StampIdentity;
import com.ziggfreed.common.loot.stamp.Stamper;
import com.ziggfreed.common.loot.stamp.StamperRegistry;
import com.ziggfreed.common.loot.stamp.StatRoll;
import com.ziggfreed.common.loot.stamp.StatRollEntry;
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.CommandRewardExecutor;
import com.ziggfreed.rpgstations.loot.StationLootEngine;
import com.ziggfreed.rpgstations.station.ExtensionCatalog;
import com.ziggfreed.common.inventory.PlayerAccess;
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
     *
     * <p>Each iteration also drives the ACTIVELY-WORKING block state
     * ({@code asset.Custody.States.Working}): a {@link StationStep#effectiveIsWork()} step lights its
     * {@code At} anchor once its walk (if any) has landed, anything else darkens whatever was lit.
     * A step's own SUCCESS deliberately does NOT darken - the next step's entry, or {@code stop()},
     * owns that - so a repeating single-step convert program never flickers between cycles. A
     * phase FAIL return needs no explicit darken either: it propagates to a session stop, whose one
     * exit funnel calls {@code exitWorkingState} unconditionally.
     */
    private static StationStepResult runIterations(@Nonnull StationStepContext ctx, @Nonnull StationStep step,
            long now, boolean walkAlreadyDone, int repeatCount) {
        StationSession s = ctx.session;
        for (int i = s.stepIteration; i < repeatCount; i++) {
            s.stepIteration = i;

            // WALK phase (first). A fresh iteration begins the walk (suspends to drive it); a
            // post-arrival re-entry skips it for THIS iteration.
            if (!walkAlreadyDone && step.getWalk() != null) {
                // Nothing is being WORKED while the puppet travels (design ruling: the fire goes
                // dark during walk phases between cycles) - darken before departing.
                StationService.getInstance().exitWorkingState(s);
                StationStepResult wr = beginWalk(ctx, step, now, repeatCount);
                if (wr != null) {
                    return wr; // suspend (walk driving) or fail (unreachable at walk-step entry)
                }
            }
            walkAlreadyDone = false;

            // Actively-working block state, resolved POST-walk so a working step that travels first
            // only lights its anchor once the puppet has arrived. A working step puts its At-anchor
            // block into Custody.States.Working; anything else takes whatever block was working out
            // of it. Both calls are idempotent + no-ops for an unauthored Working name, so a station
            // that never authors one (sawmill, anvil, cutting board) is byte-identical to pre-knob.
            // NOT cleared when the step SUCCEEDS: the next step (or stop()) owns the exit, so the
            // implicit convert program - one working step re-dispatched every cycle at the same
            // block - holds a steady look instead of flickering once per cycle.
            if (step.effectiveIsWork()) {
                StationService.getInstance().enterWorkingState(s, step.getAt());
            } else {
                StationService.getInstance().exitWorkingState(s);
            }

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
     *
     * <p><b>Timeout-path walking-flag clear (MIN-2, arc-close):</b> {@link WalkHandle#cancel()}
     * carries no accessor (decision 55), so on its own it never flips the puppet's walking
     * movement-state off - see {@link #completeTimedOutWalk} for the fix (the SAME {@code ctx.store}
     * accessor {@code poll} already used, mirroring the arrival path's own in-place flip inside
     * {@code HolderWalkHandle.poll}).
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
        completeTimedOutWalk(w.handle, s.performer.ref(), timedOut,
                ref -> PlayerPuppetService.setWalking(ctx.store, ref, false));
        return StationStepDecisions.walkStepDone(state, timedOut);
    }

    /**
     * The injectable walking-flag-clear seam {@link #completeTimedOutWalk} calls (MIN-2, arc-close)
     * - production passes {@link PlayerPuppetService#setWalking}, a fixture test passes a fake, so
     * "the clear fires exactly on a timeout, never otherwise" is verifiable without a live
     * {@code ComponentAccessor}.
     */
    @FunctionalInterface
    interface WalkingFlagClearer {
        void clearWalking(@Nullable Ref<EntityStore> ref);
    }

    /**
     * The timeout-exit cleanup for {@link #advanceWalk} (MIN-2, arc-close, unlisted parity
     * deviation from the pre-seam {@code advanceWalk}, which cleared the walk animation on BOTH the
     * arrival AND the timeout exit): {@link WalkHandle#cancel()} (decision 55) deliberately carries
     * no accessor, so it only latches the terminal state and never flips the puppet's walking
     * movement-state off - left unaddressed, a Holder/PlayerClone puppet that hits the anti-wedge
     * timeout is left animating a walk-in-place until the session's {@code revealAndDespawn}
     * eventually removes it. Cancels {@code handle} and clears the flag through the injected
     * {@code clearer} ONLY when {@code timedOut} is true (the ARRIVED path already clears in-place
     * inside the handle's own {@code poll} - see {@code HolderWalkHandle.poll}; STUCK/FAILED reached
     * via {@code poll} need no separate clearing here, same as before this fix). A no-op
     * (never cancels, never calls the clearer) when {@code timedOut} is false. Pure over the
     * handle+clearer seam, unit-tested with fakes (no live {@code ComponentAccessor} needed).
     */
    static void completeTimedOutWalk(@Nonnull WalkHandle handle, @Nullable Ref<EntityStore> performerRef,
            boolean timedOut, @Nonnull WalkingFlagClearer clearer) {
        if (!timedOut) {
            return;
        }
        handle.cancel(); // still walking past the grace window - drop the walk, complete the step
        clearer.clearWalking(performerRef);
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
        String momentId = presentMomentId(ctx, step);
        boolean actionAuthorsThisMoment =
                StationStepDecisions.actionAuthorsStepMoment(ctx.session.moments, momentId);
        if (StationStepDecisions.shouldEmitPresentationOnEntry(step, null, actionAuthorsThisMoment)) {
            Vector3d blockPos = new Vector3d(ctx.session.blockX + 0.5, ctx.session.blockY + 0.5,
                    ctx.session.blockZ + 0.5);
            // A null base defers to the action's own Moments entry for this step (specificity wins
            // when the step authors its own).
            StationService.emitMoment(ctx.store, ctx.session, momentId, step.getPresentation(), blockPos);
        }
    }

    /**
     * The per-step moment id ({@link StationStepDecisions#momentIdForStep}): {@code
     * step:<actionId>:<stepId>} when the step authors an Id, else {@link StationFlairs#MOMENT_CYCLE}.
     *
     * <p>An action that authors NO {@code Steps} runs the engine's implicit convert loop, whose one
     * synthesized step is the cycle itself - so its cue plays under the plain {@code cycle} moment,
     * the id the docs name and a flair targets, rather than under a {@code step:} id derived from an
     * engine-internal name no author ever wrote.
     */
    @Nonnull
    static String presentMomentId(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        StationStep[] authored = ctx.action.getSteps();
        boolean implicitProgram = authored == null || authored.length == 0;
        return StationStepDecisions.momentIdForStep(ctx.action.getActionId(), step.getId(), implicitProgram);
    }

    // ==================== Consume phase ====================

    /**
     * Removes EVERY {@code Consume.Items} entry from the player's inventory (storage-first) or from
     * the station's placed-input custody claim ({@code From:"Custody"}) - the multi-item phase of
     * decision 73. Returns {@code null} on success (or no/empty {@code Consume} phase - a no-op), or
     * a {@link StationStepResult.Fail} the composite handler propagates.
     *
     * <p><b>All-or-nothing.</b> Availability is checked across every entry BEFORE anything is
     * removed, so the common shortfall never leaves a partial consume behind. The residual case (an
     * entry that passes the pre-check but throws mid-removal) is covered by the pre-existing
     * ITERATION REFUND LEDGER: each removal is recorded into it as it lands, and the failing step
     * fails the program, whose {@code stop()} refunds every recorded id
     * ({@code StationService#refundIterationLedger}) unless a committed {@code Produce} cleared it.
     */
    @Nullable
    static StationStepResult consumePhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        StationStep.Consume consume = step.getConsume();
        if (consume == null || consume.isEmpty()) {
            return null;
        }
        Ingredient[] items = consume.getItems();
        for (Ingredient item : items) {
            if (item == null || consumeRef(item) == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Consume step '" + step.getId() + "' has an item with neither ItemId nor ResourceTypeId");
            }
        }
        if (StationStep.Consume.FROM_CUSTODY.equalsIgnoreCase(consume.effectiveFrom())) {
            return consumeFromCustody(ctx, step, items);
        }
        if (!StationStep.Consume.FROM_INVENTORY.equalsIgnoreCase(consume.effectiveFrom())) {
            Log.warn("STATION Consume step '" + step.getId() + "' authors From '" + consume.effectiveFrom()
                    + "' which has no handler (only 'Inventory'/'Custody' are implemented)");
            return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                    "Consume.From '" + consume.effectiveFrom() + "' is not implemented");
        }
        try {
            var combined = PlayerAccess.combinedBackpackStorageHotbar(ctx.player);
            boolean repeating = ctx.action.getWork() != null && ctx.action.getWork().effectiveLooping();
            // The exact-item entries are checked as ONE batch (two entries naming the same item must
            // not each pass against the same stack); resource-family entries have no batch API, so
            // they are pre-summed per family and each family checked once with its total (two
            // entries on the same family must not each pass against the same stock).
            List<ItemStack> itemInputs = new ArrayList<>();
            Map<String, Integer> resourceNeeds = new LinkedHashMap<>();
            for (Ingredient item : items) {
                if (isResourceRoute(item)) {
                    resourceNeeds.merge(consumeRef(item), item.effectiveQuantity(), Integer::sum);
                } else {
                    itemInputs.add(new ItemStack(consumeRef(item), item.effectiveQuantity()));
                }
            }
            for (Map.Entry<String, Integer> need : resourceNeeds.entrySet()) {
                if (!combined.canRemoveResource(new ResourceQuantity(need.getKey(), need.getValue()))) {
                    return StationStepResult.fail(StationService.shortInputStopReason(repeating),
                            "Consume step '" + step.getId() + "' is short on '" + need.getKey()
                                    + "' (needs " + need.getValue() + ")");
                }
            }
            if (!itemInputs.isEmpty() && !combined.canRemoveItemStacks(itemInputs)) {
                return StationStepResult.fail(StationService.shortInputStopReason(repeating),
                        "Consume step '" + step.getId() + "' is short on its authored Items");
            }
            for (Ingredient item : items) {
                String ref = consumeRef(item);
                int quantity = item.effectiveQuantity();
                if (isResourceRoute(item)) {
                    ResourceQuantity resource = new ResourceQuantity(ref, quantity);
                    ResourceTransaction tx = storageContainer(ctx.player).canRemoveResource(resource)
                            ? storageContainer(ctx.player).removeResource(resource)
                            : PlayerAccess.combinedBackpackStorageHotbar(ctx.player).removeResource(resource);
                    StationService.tallyResourceConsumption(ctx.session, tx, ref);
                    // Iteration refund ledger (design 2.5/M1): record the REAL drained ids so a
                    // mid-iteration stop refunds them - unless a Produce.To:Custody clears the ledger.
                    StationService.recordIterationConsumedResource(ctx.session, tx, ref);
                } else {
                    ItemStack input = new ItemStack(ref, quantity);
                    if (storageContainer(ctx.player).canRemoveItemStack(input)) {
                        storageContainer(ctx.player).removeItemStack(input);
                    } else {
                        PlayerAccess.combinedBackpackStorageHotbar(ctx.player).removeItemStack(input);
                    }
                    ctx.session.consumedItems.merge(ref, quantity, Integer::sum);
                    StationService.recordIterationConsumedItem(ctx.session, ref, quantity);
                }
            }
        } catch (Throwable t) {
            Log.warn("STATION Consume step failed for '" + ctx.session.stationId + "': " + t.getMessage());
            return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL, t.getMessage());
        }
        return null;
    }

    /** PURE: an ingredient's live lookup ref - its ResourceTypeId family, else its exact ItemId; null when neither is authored. */
    @Nullable
    private static String consumeRef(@Nullable Ingredient item) {
        if (item == null) {
            return null;
        }
        String resource = item.getResourceTypeId();
        if (resource != null && !resource.isBlank()) {
            return resource;
        }
        String itemId = item.getItemId();
        return itemId != null && !itemId.isBlank() ? itemId : null;
    }

    /** PURE: does this ingredient take the native resource-type FAMILY route rather than an exact item id? */
    private static boolean isResourceRoute(@Nullable Ingredient item) {
        return item != null && item.getResourceTypeId() != null && !item.getResourceTypeId().isBlank();
    }

    /**
     * The player's Storage section as its raw item CONTAINER: the one unwrap of the shared
     * {@code PlayerAccess.storage} read, so the reagent probe and drain paths below never repeat
     * it. {@code null} whenever the player's ref cannot be resolved or the section is absent, which
     * is exactly what a caller dereferencing the container has always seen.
     */
    @Nullable
    private static ItemContainer storageContainer(@Nonnull Player player) {
        InventoryComponent.Storage storage = PlayerAccess.storage(player);
        return storage != null ? storage.getInventory() : null;
    }

    /**
     * Drains every {@code items} entry from the block's live claim, each from the SOCKET pile it
     * addresses (its own {@code Socket}, else the phase's group-level one, else the first Item
     * socket - {@code "main"} for a degenerate custody), tallying the REAL drained item ids into
     * the session ledger PER PILE (an interrupted iteration refunds to the originating pile, never
     * merging piles). Availability is PEEKED across every entry first (a short claim fails before
     * any drain runs), so a multi-item custody consume is all-or-nothing too; a short drain fails
     * {@code OUT_OF_INPUTS}/{@code INPUTS_EXHAUSTED}, the same reasons an empty custody station
     * denies at engage.
     */
    @Nullable
    private static StationStepResult consumeFromCustody(@Nonnull StationStepContext ctx, @Nonnull StationStep step,
            @Nonnull Ingredient[] items) {
        // Custody drains from the step's At-anchor block (scope-2 wave 3, design 2.2) - the primary
        // block for a null/self At, a remote anchor's claim otherwise.
        StationCustodyClaim claim = StationService.getInstance().custodyClaimForAnchor(ctx.session, step.getAt());
        StationStep.Consume consume = step.getConsume();
        String groupSocket = consume != null ? consume.getSocket() : null;
        List<Custody.ResolvedSocket> sockets = actionSockets(ctx);
        for (Ingredient item : items) {
            String ref = consumeRef(item);
            boolean isResource = isResourceRoute(item);
            String socketId = StationCustody.socketIdFor(item.getSocket(), groupSocket, sockets);
            int have = StationCustody.availableInPile(claim != null ? claim.items(socketId) : null,
                    isResource ? null : ref, isResource ? ref : null,
                    StationService::liveResourceTypeIdsOf);
            int need = item.effectiveQuantity();
            if (have < need) {
                // Design 2.4: a REPEATING program's shortage is the graceful natural end
                // (INPUTS_EXHAUSTED); a non-repeating one keeps OUT_OF_INPUTS.
                boolean repeating = ctx.action.getWork() != null && ctx.action.getWork().effectiveLooping();
                return StationStepResult.fail(StationService.shortInputStopReason(repeating),
                        "Consume step '" + step.getId() + "' custody ran short ("
                                + have + "/" + need + " of '" + ref + "' in socket '" + socketId + "')");
            }
        }
        String anchorBlockKey = StationService.anchorBlockKeyFor(ctx.session, step.getAt());
        for (Ingredient item : items) {
            String ref = consumeRef(item);
            boolean isResource = isResourceRoute(item);
            String socketId = StationCustody.socketIdFor(item.getSocket(), groupSocket, sockets);
            Map<String, Integer> drainedOut = new LinkedHashMap<>();
            StationCustody.drainFromPile(claim != null ? claim.items(socketId) : null,
                    isResource ? null : ref, isResource ? ref : null,
                    item.effectiveQuantity(), StationService::liveResourceTypeIdsOf, drainedOut);
            for (Map.Entry<String, Integer> e : drainedOut.entrySet()) {
                ctx.session.consumedItems.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            if (anchorBlockKey != null) {
                StationService.recordIterationConsumedCustody(ctx.session, anchorBlockKey, socketId, drainedOut);
            } else {
                // No resolvable pile address (an unresolved anchor) - the player hand-back half
                // of the ledger still covers the refund.
                StationService.recordIterationConsumedMap(ctx.session, drainedOut);
            }
        }
        // One dirty mark for the whole drain batch: the claim is a view over the block's
        // chunk-persisted stash, and an unmarked mutation survives only until the section unloads.
        if (claim != null) {
            claim.markDirty();
        }
        return null;
    }

    /** The running action's resolved custody sockets (the degenerate one-{@code main} list when socket-less). */
    @Nonnull
    private static List<Custody.ResolvedSocket> actionSockets(@Nonnull StationStepContext ctx) {
        Custody custody = ctx.action.getCustody();
        return custody != null ? custody.effectiveSockets() : List.of();
    }

    // ==================== Produce phase ====================

    /**
     * Commits a produce phase. {@code To:"Custody"} (wave 3) stores into the step's {@code At}-anchor
     * custody claim; {@code To:"Inventory"} adds {@code Produce.Quantity} of {@code Produce.ItemId} to
     * the player, hotbar-first then backpack storage then drop-at-block. EITHER committed destination
     * clears the current iteration's refund ledger (review minor m1) - the consumed inputs became the
     * produced output, so a later stop never both refunds the inputs AND keeps the output. Returns
     * {@code null} on success/no-op.
     *
     * <p>The inventory route grants through {@link ItemGrantUtil#grantOrDrop} rather than the plain
     * {@code grant}, because what it does next is COUNT and ANNOUNCE the stack: only
     * {@code grantOrDrop} distinguishes "landed on the ground" from "the ground drop failed too",
     * and a stack that reached neither place must not be reported to the player as output.
     */
    @Nullable
    static StationStepResult producePhase(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        StationStep.Produce produce = step.getProduce();
        if (produce == null || produce.isEmpty()) {
            return null;
        }
        Ingredient[] items = produce.getItems();
        for (Ingredient item : items) {
            if (item == null || item.getItemId() == null || item.getItemId().isBlank()) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Produce step '" + step.getId() + "' has an item with no ItemId");
            }
        }
        String to = produce.effectiveTo();

        // To:"Custody" (scope-2 wave 3, design 2.2): store the outputs into the step's At-anchor
        // custody claim (the primary block for a null/self At), each item into the SOCKET pile it
        // addresses (its own Socket, else the phase's, else the first Item socket - a produce pile
        // is owned by the session's worker), then clear the iteration refund ledger (M1: the
        // consumed inputs BECAME these custody items - refund + custody-return are mutually
        // exclusive; returnCustody now hands the produced items back instead).
        if (StationStep.Produce.TO_CUSTODY.equalsIgnoreCase(to)) {
            try {
                List<Custody.ResolvedSocket> sockets = actionSockets(ctx);
                for (Ingredient item : items) {
                    String socketId = StationCustody.socketIdFor(item.getSocket(), produce.getSocket(), sockets);
                    boolean placed = StationService.getInstance().produceIntoCustody(ctx.session, ctx.commandBuffer,
                            step.getAt(), socketId, item.getItemId(), item.effectiveQuantity());
                    if (!placed) {
                        return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                                "Produce step '" + step.getId() + "' could not resolve its To:Custody anchor '"
                                        + step.getAt() + "'");
                    }
                    ctx.session.producedItems.merge(item.getItemId(), item.effectiveQuantity(), Integer::sum);
                }
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
            for (Ingredient item : items) {
                int quantity = item.effectiveQuantity();
                // grantOrDrop, not grant: this loop both COUNTS the output into the session ledger
                // and ANNOUNCES it, and the plain grant result cannot tell "landed on the ground"
                // from "the ground drop failed too". A stack that reached neither the inventory nor
                // the world no longer exists, so counting it would put output in the end-of-session
                // summary the player never received and toast them for it besides.
                boolean landed = ItemGrantUtil.grantOrDrop(ctx.player, new ItemStack(item.getItemId(), quantity),
                        ctx.commandBuffer, ctx.store,
                        ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
                if (!landed) {
                    Log.warn("STATION Produce step '" + step.getId() + "' lost '" + item.getItemId()
                            + "' - no inventory room and the drop failed");
                    continue;
                }
                ctx.session.producedItems.merge(item.getItemId(), quantity, Integer::sum);
                if (ctx.session.playerRef != null) {
                    StationService.notifyItemGain(ctx.session.playerRef, item.getItemId(), quantity, false);
                }
            }
            // M1 (review minor m1): the outputs are now committed to the inventory, so the consumed
            // inputs are spent - clear the refund ledger, exactly as the To:Custody branch does, or a
            // stop on a later Duration suspend in this same iteration would double-grant. Cleared
            // ONCE after every item lands, so a mid-list grant failure still leaves the ledger able
            // to refund the inputs.
            StationService.clearIterationLedgerOnCommittedProduce(ctx.session);
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
        // The SAME resolution an action's Bonus uses (referenced tables' effective rolls, incl. any
        // Lootable-targeted extension's appended ones, plus each table's own pool, then the ref's own
        // inline rolls) - a step's Roll phase must never see a narrower view of a shared table than a
        // Bonus does.
        LootEngine.Resolved resolved = StationLootEngine.resolve(ref, "Roll step '" + step.getId() + "'");
        if (resolved.rolls().isEmpty() && resolved.pools().isEmpty()) {
            return null;
        }
        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(resolved,
                StationLootEngine.TRIGGER_CYCLE, ctx.snapshot,
                ctx.player, ctx.session.playerRef, ctx.session.stationId,
                ctx.action.getActionId(), ctx.cycleIndex, ctx.commandBuffer, ctx.store,
                ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
        StationService.applyGrantResult(ctx.session, ctx.store, ctx.commandBuffer, ctx.player, result);
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
        String player = ctx.session.playerRef.getUsername();
        CommandRunner.runAllWith(CommandRewardExecutor.consoleAs(player != null ? player : ""),
                Arrays.asList(commands),
                CommandRewardExecutor.placeholders(ctx.session.playerRef, ctx.session.stationId,
                        ctx.action.getActionId(), ctx.cycleIndex),
                message -> Log.fine("STATION step commands: " + message));
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
                    ? StationService.getInstance().custodyClaimFor(ctx.session, ctx.session.blockKey) : null;
            ItemStack weaponStack = claim != null ? claim.uniqueStack() : null;
            if (weaponStack == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' has no custody item to enhance");
            }

            // ===== COMPUTE PHASE (zero mutation, per M5) =====
            StampSpec statsGroup = stamp.getStats();
            Stamper stamper = StamperRegistry.get();
            StampInspection inspection = StampInspection.empty();
            StampPlan plan = StampPlan.NOTHING;
            if (statsGroup != null) {
                if (stamper == null) {
                    Log.fine("STAMP step '" + step.getId() + "' authors Stats with no registered stamper "
                            + "- the Stats leaf no-ops this attempt (Durability still lands)");
                } else {
                    inspection = safeInspect(stamper, weaponStack);
                    plan = StampCapEngine.resolve(withExtendedEntries(statsGroup), inspection,
                            ctx.snapshot, () -> ThreadLocalRandom.current().nextDouble());
                    if (plan.denied()) {
                        return StationStepResult.fail(StationService.StopReason.ENHANCE_CAPPED,
                                "Stamp step '" + step.getId() + "' fully capped for '"
                                        + weaponStack.getItemId() + "'");
                    }
                }
            }

            Ingredient[] reagents = stamp.getReagents();
            double repeatCostMultiplier = economicsMultiplier(stamp.getEconomics());
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

            if (!InventoryGrant.canAdd(ctx.player, weaponStack)) {
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
                restoreReagents(ctx, consumedForRestore);
                Log.warn("STAMP step '" + step.getId() + "' reagent consumption failed, restored: " + t.getMessage(), t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' reagent consumption failed: " + t.getMessage());
            }

            Mutation mutation;
            try {
                mutation = applyStampMutation(weaponStack, stamp.getDurability(), plan, stamper,
                        StampIdentity.resolve(stamp.getStats(), poolOf(stamp.getStats())));
            } catch (Throwable t) {
                restoreReagents(ctx, consumedForRestore);
                Log.warn("STAMP step '" + step.getId() + "' mutation failed, restored reagents: " + t.getMessage(), t);
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' mutation failed: " + t.getMessage());
            }
            claim.setUniqueStack(mutation.stack());
            // The enhanced stack now lives in the block's chunk-persisted stash; mark it for the
            // next chunk save so the mutation survives an unload.
            claim.markDirty();

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
         * The same spec with its candidate entries already FLATTENED: the named pool's own entries
         * plus every {@code Target:{RollPool}} extension's appended ones, then the spec's own inline
         * entries.
         *
         * <p>The shared engine reads a pool straight off the shared store, which is right for every
         * other consumer and one layer short here - this engine's extension mechanism is its own, and
         * an extension that adds a stat to a shared pool has to reach a stamp step for the same
         * reason an extension that adds a roll to a shared table has to reach a loot pass.
         */
        @Nonnull
        static StampSpec withExtendedEntries(@Nonnull StampSpec spec) {
            String poolId = spec.getPool();
            if (poolId == null || poolId.isBlank()) {
                return spec;
            }
            RollPoolAsset pool = RollPoolConfig.getInstance().resolve(poolId);
            StatRollEntry[] poolEntries = ExtensionCatalog.getInstance()
                    .applyToRollPoolEntries(poolId, pool != null ? pool.getEntries() : null);
            List<StatRollEntry> merged = new ArrayList<>();
            if (poolEntries != null) {
                merged.addAll(Arrays.asList(poolEntries));
            }
            if (spec.getEntries() != null) {
                merged.addAll(Arrays.asList(spec.getEntries()));
            }
            return StampSpec.of(null, merged.toArray(StatRollEntry[]::new), spec.getPicks(),
                    spec.isUnique(), spec.getCaps());
        }

        /**
         * The roll pool a stamp draws from, or null when it names none - the fallback layer for a
         * rename or a rarity the stamp itself did not state.
         */
        @Nullable
        private static RollPoolAsset poolOf(@Nullable StampSpec spec) {
            String poolId = spec == null ? null : spec.getPool();
            if (poolId == null || poolId.isBlank()) {
                return null;
            }
            return RollPoolConfig.getInstance().resolve(poolId);
        }

        /**
         * PURE: applies {@code Durability.AddMax} then the (already rolled + cap-clamped) {@code plan}
         * entries via {@code stamper}, in that order, returning a {@link Mutation} (the new stack +
         * one {@link EnhanceLine} per stat written + the max-durability delta). Both mutations are
         * {@code ItemStack} with-copy operations, so no live server/Player is needed here (unit-tested
         * directly, incl. a THROWING stamper - proves a mutation failure never reaches
         * {@link StationCustodyClaim#setUniqueStack}, the caller's job).
         *
         * <p>Each line carries the stat id and its points, plus the label the STAMPER gave it - the
         * same object that just wrote the stat is the one that knows what it is called, in what
         * colour, in the player's own locale, so it is asked rather than second-guessed. A stamper
         * with no wording answers null and the line stays label-less, which the summary reports
         * plainly; this engine still learns no stat vocabulary either way.
         */
        @Nonnull
        static Mutation applyStampMutation(@Nonnull ItemStack weaponStack,
                @Nullable StationStep.Stamp.Durability durabilityGroup, @Nonnull StampPlan plan,
                @Nullable Stamper stamper) {
            return applyStampMutation(weaponStack, durabilityGroup, plan, stamper, null);
        }

        /**
         * As above, plus the authored identity a rename and a rarity come from. Separate because
         * identity is not rolled and not capped: it is what the pool or the stamp SAID this item
         * should become, and a stamp that authored neither passes null and changes neither.
         */
        @Nonnull
        static Mutation applyStampMutation(@Nonnull ItemStack weaponStack,
                @Nullable StationStep.Stamp.Durability durabilityGroup, @Nonnull StampPlan plan,
                @Nullable Stamper stamper, @Nullable StampIdentity identity) {
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
                mutated = stamper.apply(mutated, plan.entries(), identity);
                List<EnhanceLine> written = new ArrayList<>(plan.entries().size());
                for (StatRoll entry : plan.entries()) {
                    written.add(new EnhanceLine(entry.statId(), entry.points(), safeDescribe(stamper, entry)));
                }
                lines = List.copyOf(written);
            }
            return new Mutation(mutated, lines, durabilityAdded);
        }

        /** The pure result of {@link #applyStampMutation}: the mutated stack, the provider's verbatim report, and the max-durability delta. */
        record Mutation(@Nonnull ItemStack stack, @Nonnull List<EnhanceLine> lines, double durabilityAdded) {
        }

        /**
         * Best-effort restore: each stack failing independently is logged, never re-thrown.
         *
         * <p>Routes through {@link ItemGrantUtil} rather than adding to storage directly, so a
         * reagent lands in the hotbar, else backpack storage, else on the ground at the station
         * block. A ritual whose reagents were already consumed can be failing precisely BECAUSE
         * the run produced something that filled the last slot, so a storage-only restore is the
         * one path where a full inventory silently destroys the player's materials.
         */
        private static void restoreReagents(@Nonnull StationStepContext ctx, @Nonnull List<ItemStack> toRestore) {
            for (ItemStack restore : toRestore) {
                if (restore != null) {
                    try {
                        ItemGrantUtil.grant(ctx.player, restore, ctx.commandBuffer, ctx.store,
                                ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
                    } catch (Throwable restoreFailure) {
                        Log.warn("STAMP restore failed for '" + restore.getItemId() + "': " + restoreFailure.getMessage());
                    }
                }
            }
        }

        /**
         * Never-throwing {@link Stamper#describe} - the stamper's own wording for a stat it just
         * wrote, or null. A stamper that throws here costs its line a LABEL and nothing else: the
         * stat was already applied, and the summary's plain fallback still reports it.
         */
        @Nullable
        private static Message safeDescribe(@Nonnull Stamper stamper, @Nonnull StatRoll entry) {
            try {
                return stamper.describe(entry);
            } catch (Throwable t) {
                Log.warn("STAMP stamper describe threw for '" + entry.statId()
                        + "', reporting it plainly: " + t.getMessage());
                return null;
            }
        }

        /** Never-throwing {@link Stamper#inspect} - a bad third-party stamper must never crash a ritual. */
        @Nonnull
        private static StampInspection safeInspect(@Nonnull Stamper stamper, @Nonnull ItemStack stack) {
            try {
                return stamper.inspect(stack);
            } catch (Throwable t) {
                Log.warn("STAMP stamper inspect threw, treating as bare: " + t.getMessage());
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

        /** {@code Economics.RepeatCostMultiplier}, or 0 (flat cost) when unauthored. */
        private static double economicsMultiplier(@Nullable StationStep.Stamp.Economics economics) {
            if (economics == null) {
                return 0.0;
            }
            Double m = economics.getRepeatCostMultiplier();
            return m != null ? m : 0.0;
        }

        /** {@code ceil(baseQuantity * (1 + RepeatCostMultiplier * stampCount))}. */
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
                return storageContainer(player).canRemoveResource(resource)
                        || PlayerAccess.combinedBackpackStorageHotbar(player).canRemoveResource(resource);
            }
            ItemStack want = new ItemStack(ref, quantity);
            return storageContainer(player).canRemoveItemStack(want)
                    || PlayerAccess.combinedBackpackStorageHotbar(player).canRemoveItemStack(want);
        }

        /** Storage-first-then-combined removal; returns the REAL drained stack(s) for the caller's restore-on-failure ledger. */
        @Nonnull
        private static List<ItemStack> consumeReagent(@Nonnull Player player, boolean isResource,
                @Nonnull String ref, int quantity) {
            if (isResource) {
                ResourceQuantity resource = new ResourceQuantity(ref, quantity);
                ResourceTransaction tx = storageContainer(player).canRemoveResource(resource)
                        ? storageContainer(player).removeResource(resource)
                        : PlayerAccess.combinedBackpackStorageHotbar(player).removeResource(resource);
                return drainedStacksOf(tx);
            }
            ItemStack want = new ItemStack(ref, quantity);
            if (storageContainer(player).canRemoveItemStack(want)) {
                storageContainer(player).removeItemStack(want);
            } else {
                PlayerAccess.combinedBackpackStorageHotbar(player).removeItemStack(want);
            }
            return List.of(want);
        }
    }
}
