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
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.api.EnhanceStamper;
import com.ziggfreed.rpgstations.api.StampInspection;
import com.ziggfreed.rpgstations.api.StampResult;
import com.ziggfreed.rpgstations.api.StatRoll;
import com.ziggfreed.rpgstations.api.impl.EnhanceStamperRegistryImpl;
import com.ziggfreed.rpgstations.asset.Custody;
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
 * The seven executable {@code station.step} handlers (design sections 9.3/9.5, minus the one
 * schema-reserved-unimplemented {@code Mount} id - see {@link StationStep}'s javadoc). Each is
 * registered UNGUARDED here; {@link StationStepRegistry} wraps every one in the conditions-gate +
 * throw-guard layer (design 9.3/M4's binding fix) before handing it to the kernel, so a handler
 * body below may assume its step's {@code Conditions} already passed and never needs its own
 * top-level try/catch for an UNEXPECTED throw - only for the SPECIFIC failure modes it wants a
 * more precise {@link StationService.StopReason} for (Consume/Produce map an inventory-mutation
 * throw to {@code INVENTORY_FULL}, matching the pre-refactor engine's exact behavior, rather than
 * the guard's generic {@code STEP_FAILED}).
 */
final class StationStepHandlers {

    private StationStepHandlers() {
    }

    /**
     * Removes {@code Consume.Quantity} of the item/resource-type either from the player's
     * inventory (storage-first) or from the station's placed-input custody claim (design section
     * 9.4, phase-2 leg C - {@code From:"Custody"}, the sawmill migration's route).
     */
    static final class ConsumeHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.Consume consume = step.getConsume();
            if (consume == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Consume step '" + step.getId() + "' has no Consume group");
            }
            int quantity = consume.getQuantity() != null && consume.getQuantity() > 0 ? consume.getQuantity() : 1;
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
                        + "' which has no handler yet (only 'Inventory'/'Custody' are implemented)");
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Consume.From '" + consume.effectiveFrom() + "' is not yet implemented");
            }
            try {
                if (isResource) {
                    ResourceQuantity resource = new ResourceQuantity(inputRef, quantity);
                    ResourceTransaction tx = InventoryAccess.storageOf(ctx.player).canRemoveResource(resource)
                            ? InventoryAccess.storageOf(ctx.player).removeResource(resource)
                            : InventoryAccess.combinedBackpackStorageHotbarOf(ctx.player).removeResource(resource);
                    StationService.tallyResourceConsumption(ctx.session, tx, inputRef);
                } else {
                    ItemStack input = new ItemStack(inputRef, quantity);
                    if (InventoryAccess.storageOf(ctx.player).canRemoveItemStack(input)) {
                        InventoryAccess.storageOf(ctx.player).removeItemStack(input);
                    } else {
                        InventoryAccess.combinedBackpackStorageHotbarOf(ctx.player).removeItemStack(input);
                    }
                    ctx.session.consumedItems.merge(inputRef, quantity, Integer::sum);
                }
            } catch (Throwable t) {
                Log.warn("STATION Consume step failed for '" + ctx.session.stationId + "': " + t.getMessage());
                return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL, t.getMessage());
            }
            return StationStepResult.SUCCESS;
        }

        /**
         * Drains {@code quantity} of {@code itemId}/{@code resourceTypeId} from the block's live
         * claim ({@link StationService#custodyClaimFor}), tallying the REAL drained item ids into
         * the session ledger ({@code StationCustody.drain}'s {@code drainedOut} parameter, mirroring
         * {@code StationService#tallyResourceConsumption}). A short drain (the claim ran out
         * mid-cycle - should not normally happen since {@code firstRunnableConversionFromCustody}
         * pre-checks, but a mid-session drain-by-another-source is not otherwise excluded) fails
         * {@code OUT_OF_INPUTS}, the same reason an empty custody station denies at engage.
         */
        private static StationStepResult consumeFromCustody(StationStepContext ctx, StationStep step,
                boolean isResource, String resourceTypeId, String itemId, int quantity) {
            StationCustodyClaim claim = StationService.getInstance().custodyClaimFor(ctx.session.blockKey);
            Map<String, Integer> drainedOut = new LinkedHashMap<>();
            int drained = StationCustody.drain(claim, isResource ? null : itemId, isResource ? resourceTypeId : null,
                    quantity, StationService::liveResourceTypeIdsOf, drainedOut);
            if (drained < quantity) {
                return StationStepResult.fail(StationService.StopReason.OUT_OF_INPUTS,
                        "Consume step '" + step.getId() + "' custody ran short ("
                                + drained + "/" + quantity + " of '" + (isResource ? resourceTypeId : itemId) + "')");
            }
            for (Map.Entry<String, Integer> e : drainedOut.entrySet()) {
                ctx.session.consumedItems.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            return StationStepResult.SUCCESS;
        }
    }

    /**
     * Adds {@code Produce.Quantity} of {@code Produce.ItemId} to the player, hotbar-first then
     * backpack storage then drop-at-block (round-5, via {@code util.ItemGrantUtil}), then fires a
     * live item-gain notification ({@code StationService#notifyItemGain}).
     */
    static final class ProduceHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.Produce produce = step.getProduce();
            if (produce == null || produce.getItemId() == null || produce.getItemId().isBlank()) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Produce step '" + step.getId() + "' has no Produce.ItemId");
            }
            if (!StationStep.Produce.TO_INVENTORY.equalsIgnoreCase(produce.effectiveTo())) {
                Log.warn("STATION Produce step '" + step.getId() + "' authors To '" + produce.effectiveTo()
                        + "' which has no handler yet (only 'Inventory' is implemented this leg)");
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Produce.To '" + produce.effectiveTo() + "' is not yet implemented");
            }
            int quantity = produce.getQuantity() != null && produce.getQuantity() > 0 ? produce.getQuantity() : 1;
            try {
                // Round-5: ItemGrantUtil.grant never throws for "no room" anymore (it drops at
                // the block instead) - this try/catch now only guards a genuinely unexpected
                // failure (ItemStack construction, the ledger merge, the notify call), not the
                // old "container full" case (that was already precluded by runCycle's NO_ROOM
                // precheck before this handler ever runs for a real cycle).
                ItemGrantUtil.grant(ctx.player, new ItemStack(produce.getItemId(), quantity), ctx.store,
                        ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
                ctx.session.producedItems.merge(produce.getItemId(), quantity, Integer::sum);
                if (ctx.session.playerRef != null) {
                    StationService.notifyItemGain(ctx.session.playerRef, produce.getItemId(), quantity, false);
                }
            } catch (Throwable t) {
                Log.warn("STATION Produce step failed for '" + ctx.session.stationId + "': " + t.getMessage());
                return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL, t.getMessage());
            }
            return StationStepResult.SUCCESS;
        }
    }

    /**
     * Suspends the walk until {@code Wait.DurationMs} elapses, deriving the deadline ONCE (session-
     * held, never re-derived on re-entry - the kernel's binding resume contract). {@code Beats} is
     * schema-reserved this leg (see {@link StationStep.Wait}'s javadoc) - fails cleanly rather
     * than hanging forever.
     */
    static final class WaitHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.Wait wait = step.getWait();
            if (wait == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Wait step '" + step.getId() + "' has no Wait group");
            }
            Long durationMs = wait.getDurationMs();
            if (durationMs == null || durationMs <= 0) {
                if (wait.getBeats() != null && wait.getBeats() > 0) {
                    Log.warn("STATION Wait step '" + step.getId() + "' authors Beats with no handler yet"
                            + " (Beats is schema-reserved this leg)");
                }
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Wait step '" + step.getId() + "' has no positive DurationMs");
            }
            long now = System.currentTimeMillis();
            long deadline = StationStepDecisions.commitOrReadDeadline(now, durationMs, ctx.session.stepDeadlineMs);
            ctx.session.stepDeadlineMs = deadline;
            if (!StationStepDecisions.waitDue(now, deadline)) {
                return StationStepResult.suspend(deadline);
            }
            ctx.session.stepDeadlineMs = 0L;
            return StationStepResult.SUCCESS;
        }
    }

    /** Evaluates + grants a loot pass through the SAME {@code loot.LootEngine} a station's {@code Loot} group uses. */
    static final class RollHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.RollGroup group = step.getRoll();
            if (group == null) {
                return StationStepResult.SUCCESS; // an empty Roll step is a legal no-op
            }
            List<Roll> rolls = new ArrayList<>();
            String lootableId = group.getLootable();
            if (lootableId != null && !lootableId.isBlank()) {
                LootableAsset table = LootableCatalog.getInstance().get(lootableId);
                if (table != null && table.getRolls() != null) {
                    rolls.addAll(Arrays.asList(table.getRolls()));
                } else {
                    Log.fine("STATION Roll step '" + step.getId() + "' references unknown lootable '" + lootableId + "'");
                }
            }
            if (group.getRolls() != null) {
                rolls.addAll(Arrays.asList(group.getRolls()));
            }
            if (rolls.isEmpty()) {
                return StationStepResult.SUCCESS;
            }
            LootEngine.GrantResult result = LootEngine.rollAndGrant(rolls, Roll.TRIGGER_CYCLE, ctx.snapshot,
                    ctx.player, ctx.cycleOutputForBonusCopies, ctx.session.playerRef, ctx.session.stationId,
                    ctx.action.getActionId(), ctx.cycleIndex, ctx.store,
                    ctx.session.blockX, ctx.session.blockY, ctx.session.blockZ);
            StationService.applyGrantResult(ctx.session, ctx.store, result);
            return StationStepResult.SUCCESS;
        }
    }

    /** Runs {@code Command.Commands} through the SAME zero-code integration surface a {@code Roll.Grants.Commands} uses. */
    static final class CommandHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.CommandGroup group = step.getCommand();
            String[] commands = group != null ? group.getCommands() : null;
            if (commands == null || commands.length == 0 || ctx.session.playerRef == null) {
                return StationStepResult.SUCCESS;
            }
            CommandRewardExecutor.Placeholders placeholders = CommandRewardExecutor.Placeholders.of(
                    ctx.session.playerRef, ctx.session.stationId, ctx.action.getActionId(), ctx.cycleIndex);
            for (String raw : commands) {
                if (raw != null && !raw.isBlank()) {
                    CommandRewardExecutor.run(raw, placeholders);
                }
            }
            return StationStepResult.SUCCESS;
        }
    }

    /**
     * Plays this step's OWN {@code Presentation} at the block through the per-step moment id
     * (design section 9.6, leg F): {@code step:<actionId>:<stepId>} when this step authors an
     * {@code Id}, else falls back to the well-known {@link StationFlairs#MOMENT_CYCLE} id (the
     * pre-leg-F behavior every implicit-program Present step still exercises, since the implicit
     * program's own steps author no {@code Id}).
     */
    static final class PresentHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            if (step.getPresentation() == null) {
                return StationStepResult.SUCCESS;
            }
            Vector3d blockPos = new Vector3d(ctx.session.blockX + 0.5, ctx.session.blockY + 0.5,
                    ctx.session.blockZ + 0.5);
            StationService.emitMoment(ctx.store, ctx.session, presentMomentId(ctx, step), step.getPresentation(), blockPos);
            return StationStepResult.SUCCESS;
        }
    }

    /** The pure per-step moment id decision {@link PresentHandler} resolves against. */
    @Nonnull
    static String presentMomentId(@Nonnull StationStepContext ctx, @Nonnull StationStep step) {
        String stepId = step.getId();
        if (stepId != null && !stepId.isBlank()) {
            return StationFlairs.stepMomentId(ctx.action.getActionId(), stepId);
        }
        return StationFlairs.MOMENT_CYCLE;
    }

    /**
     * The anvil's enhance-commit step (design section 9.5, critique M5's binding fix): COMPUTE
     * everything first with ZERO mutation (roll + cap-clamp the {@code Stats} leaf via
     * {@link StampCapEngine}, validate reagent availability, validate the enhanced weapon can be
     * returned to the player's inventory when the session later stops), THEN commit reagent
     * consumption + the durability/stat mutation under one {@code try/catch} that restores the
     * EXACT pre-step reagent quantities to the player's inventory on any failure and NEVER writes
     * the mutated weapon back to custody unless the whole commit succeeds - custody's live
     * {@link StationCustodyClaim#uniqueStack()} is the ONE write, as the very last line. Denies
     * cleanly (no consume, no mutation) on: no weapon in custody, a fully-capped {@code Stats}
     * roll ({@code ENHANCE_CAPPED}), insufficient reagents, or no room to return the weapon
     * (both {@code OUT_OF_INPUTS}/{@code INVENTORY_FULL} - the SAME reasons the classic Convert
     * cycle already uses for the equivalent denials).
     */
    static final class StampHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.Stamp stamp = step.getStamp();
            if (stamp == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Stamp step '" + step.getId() + "' has no Stamp group");
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

            StationStep.Stamp.Reagent[] reagents = stamp.getReagents();
            double repeatCostMultiplier = economicsMultiplier(statsGroup);
            int stampCount = stamper != null ? inspection.stampCount() : 0;
            List<ItemStack> effectiveReagents = new ArrayList<>();
            if (reagents != null) {
                for (StationStep.Stamp.Reagent r : reagents) {
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
                    if (!isResource) {
                        effectiveReagents.add(new ItemStack(reagentRef, effectiveQty));
                    }
                }
            }

            if (!InventoryAccess.storageOf(ctx.player).canAddItemStacks(List.of(weaponStack))) {
                return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL,
                        "Stamp step '" + step.getId() + "' - no room to return the enhanced item later");
            }

            // ===== COMMIT PHASE (mutation, restore-on-failure per M5) =====
            // Reagent consumption and the weapon MUTATION are two separate try/catch blocks
            // (not one), so a throw at EITHER point restores exactly what was consumed so far and
            // - critically - claim.setUniqueStack (the ONE custody write) is reached ONLY on the
            // final line, after applyStampMutation has ALREADY returned successfully: a throwing
            // mutation (a bad third-party EnhanceStamper) can therefore NEVER leave a
            // partially-applied stamp on the claim.
            List<ItemStack> consumedForRestore = new ArrayList<>();
            try {
                if (reagents != null) {
                    for (StationStep.Stamp.Reagent r : reagents) {
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

            // Record the ritual's committed reagents into the session's consumed ledger, the SAME
            // s.consumedItems the implicit-program Consume step feeds, so the end-of-session summary
            // renders one CONSUMED row per reagent stack (e.g. the 2 sharpened bars) through the
            // existing ledgerRows pipeline. Tallied only AFTER claim.setUniqueStack (the point of no
            // return - a restore-on-failure earlier would have refunded these, so they must not be
            // counted as consumed until the commit is final). `consumedForRestore` already holds the
            // REAL drained stacks a ResourceTypeId reagent family resolved to.
            StationService.tallyConsumedStacks(ctx.session, consumedForRestore);

            // D-6: capture the committed outcome AFTER the ONE custody write, so a session summary
            // + the api event report only a fully-committed enhancement (never a denied ritual).
            // `weaponStack` is the immutable pre-mutation "before" copy; mutation.stack() is "after".
            String weaponId = weaponStack.getItemId() != null ? weaponStack.getItemId() : "";
            StationEnhanceOutcome outcome = new StationEnhanceOutcome(weaponId, weaponStack, mutation.stack(),
                    mutation.lines(), mutation.durabilityAdded());
            ctx.session.enhanceOutcomes.add(outcome);
            if (ctx.session.playerRef != null) {
                StationEvents.fireEnhanceCompleted(ctx.store, ctx.session.playerRef, ctx.session.playerUuid,
                        ctx.session.sessionId, ctx.session.stationId, ctx.action.getActionId(), outcome);
            }
            return StationStepResult.SUCCESS;
        }

        /**
         * PURE: applies {@code Durability.AddMax} then the (already rolled + cap-clamped)
         * {@code plan} entries via {@code stamper}, in that order, returning a {@link Mutation}
         * (the new stack + the provider's {@link EnhanceLine} report + the max-durability delta) -
         * both mutations are {@code ItemStack} with-copy operations, so no live server/Player is
         * needed here (unit-tested directly, incl. a THROWING stamper - proves a mutation failure
         * never reaches {@link StationCustodyClaim#setUniqueStack}, the caller's job, never this
         * method's).
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

        /**
         * The pure result of {@link #applyStampMutation}: the mutated stack, the provider's
         * verbatim {@link EnhanceLine} report (empty = durability-only / silent), and the
         * max-durability delta the station's own {@code Durability.AddMax} added (for the engine-
         * owned durability summary row + the api event).
         */
        record Mutation(@Nonnull ItemStack stack, @Nonnull List<EnhanceLine> lines, double durabilityAdded) {
        }

        /** Best-effort restore: each stack failing independently is logged, never re-thrown (a restore must not itself crash the drain). */
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

        /**
         * The REAL drained stack(s) for {@code tx} (a {@code ResourceTypeId} route can drain
         * several distinct concrete item ids - mirrors {@code StationService#tallyResourceConsumption}'s
         * exact per-slot read), for a precise restore-on-failure (M5's binding fix: restore the
         * EXACT pre-step contents, never a guessed substitute).
         */
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

        /** Pure availability query (storage then combined) - never mutates, mirrors {@link ConsumeHandler}'s routing. */
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

        /**
         * Mirrors {@link ConsumeHandler}'s storage-first-then-combined removal routing exactly;
         * returns the REAL drained stack(s) (metadata-free reagents this leg, but generic) for the
         * caller's restore-on-failure ledger.
         */
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
