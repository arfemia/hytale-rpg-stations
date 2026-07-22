package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.ziggfreed.common.cast.step.StepHandler;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.CommandRewardExecutor;
import com.ziggfreed.rpgstations.loot.LootEngine;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The six executable {@code station.step} handlers (design section 9.3's initial step-type set,
 * minus the two schema-reserved-unimplemented ids - see {@link StationStep}'s javadoc). Each is
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

    /** Removes {@code Consume.Quantity} of the item/resource-type from the player's inventory (storage-first). */
    static final class ConsumeHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            StationStep.Consume consume = step.getConsume();
            if (consume == null) {
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Consume step '" + step.getId() + "' has no Consume group");
            }
            if (!StationStep.Consume.FROM_INVENTORY.equalsIgnoreCase(consume.effectiveFrom())) {
                Log.warn("STATION Consume step '" + step.getId() + "' authors From '" + consume.effectiveFrom()
                        + "' which has no handler yet (only 'Inventory' is implemented this leg)");
                return StationStepResult.fail(StationService.StopReason.STEP_FAILED,
                        "Consume.From '" + consume.effectiveFrom() + "' is not yet implemented");
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
            try {
                if (isResource) {
                    ResourceQuantity resource = new ResourceQuantity(inputRef, quantity);
                    ResourceTransaction tx = ctx.player.getInventory().getStorage().canRemoveResource(resource)
                            ? ctx.player.getInventory().getStorage().removeResource(resource)
                            : ctx.player.getInventory().getCombinedBackpackStorageHotbar().removeResource(resource);
                    StationService.tallyResourceConsumption(ctx.session, tx, inputRef);
                } else {
                    ItemStack input = new ItemStack(inputRef, quantity);
                    if (ctx.player.getInventory().getStorage().canRemoveItemStack(input)) {
                        ctx.player.getInventory().getStorage().removeItemStack(input);
                    } else {
                        ctx.player.getInventory().getCombinedBackpackStorageHotbar().removeItemStack(input);
                    }
                    ctx.session.consumedItems.merge(inputRef, quantity, Integer::sum);
                }
            } catch (Throwable t) {
                Log.warn("STATION Consume step failed for '" + ctx.session.stationId + "': " + t.getMessage());
                return StationStepResult.fail(StationService.StopReason.INVENTORY_FULL, t.getMessage());
            }
            return StationStepResult.SUCCESS;
        }
    }

    /** Adds {@code Produce.Quantity} of {@code Produce.ItemId} to the player's storage. */
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
                ctx.player.getInventory().getStorage().addItemStack(new ItemStack(produce.getItemId(), quantity));
                ctx.session.producedItems.merge(produce.getItemId(), quantity, Integer::sum);
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
                    ctx.action.getActionId(), ctx.cycleIndex);
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

    /** Plays this step's OWN {@code Presentation} at the block through the {@code CYCLE} moment slot. */
    static final class PresentHandler implements StepHandler<StationStepContext, StationStep, StationStepResult> {
        @Override
        public StationStepResult execute(StationStepContext ctx, StationStep step) {
            if (step.getPresentation() == null) {
                return StationStepResult.SUCCESS;
            }
            Vector3d blockPos = new Vector3d(ctx.session.blockX + 0.5, ctx.session.blockY + 0.5,
                    ctx.session.blockZ + 0.5);
            StationService.emitMoment(ctx.store, ctx.session, StationFlairs.Slot.CYCLE, step.getPresentation(), blockPos);
            return StationStepResult.SUCCESS;
        }
    }
}
