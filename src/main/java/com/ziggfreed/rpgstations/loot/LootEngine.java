package com.ziggfreed.rpgstations.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.util.ItemDropUtil;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The store-touching half of the conditional-lootable layer (design section 4.5): resolves a
 * station's effective {@link Roll} list ({@link #resolveRolls}), then evaluates + APPLIES every
 * roll matching a trigger against ONE {@link FactorSnapshot} for the batch ({@link
 * #rollAndGrant}). The pure decision core is {@link RollEvaluator}; this class owns inventory
 * mutation, native {@code ItemDropList} rolling, and command dispatch - never store mutation
 * inside the pure evaluator.
 *
 * <p>Every grant is storage-first with a per-stack room check; SMOKE-FIX S3 (b) (MAINTAINER
 * DIRECTIVE, 2026-07-22) supersedes the old "room-checked, skipped silently when full" behavior -
 * a stack that cannot fit in the player's inventory now drops as a ground item at the station
 * block via the shared {@link ItemDropUtil} sink instead of being discarded (never fails or stops
 * the cycle either way). {@link GrantResult} tallies what actually landed (inventory OR ground)
 * so the caller ({@code StationService}) can fold it into its own session item ledger and play
 * the reached floors' presentations through its OWN {@code emitMoment} choke point (this class
 * stays presentation-agnostic; it only reports WHAT to play).
 */
public final class LootEngine {

    private LootEngine() {
    }

    /**
     * The tally of one {@link #rollAndGrant} pass. Bonus-copy items and droplist items are
     * tracked in SEPARATE tallies (not merged) so the caller can play the right notification
     * per grant kind - design section 4.5.1: bonus copies get the throttled "lucky" toast,
     * droplist grants (almost always a reached ladder floor) get the "rare find" toast.
     */
    public static final class GrantResult {
        private final Map<String, Integer> bonusCopyItems = new LinkedHashMap<>();
        private final Map<String, Integer> dropListItems = new LinkedHashMap<>();
        private final List<Presentation> floorPresentations = new ArrayList<>();
        private int commandsRun;

        /** Bonus-output-copy items granted this pass (item id -> total quantity). */
        @Nonnull
        public Map<String, Integer> getBonusCopyItems() {
            return bonusCopyItems;
        }

        /** {@code ItemDropList}-derived items granted this pass (item id -> total quantity). */
        @Nonnull
        public Map<String, Integer> getDropListItems() {
            return dropListItems;
        }

        /** Every reached floor's non-null {@code Presentation}, in roll-evaluation order. */
        @Nonnull
        public List<Presentation> getFloorPresentations() {
            return floorPresentations;
        }

        public int getCommandsRun() {
            return commandsRun;
        }

        public boolean anyGranted() {
            return !bonusCopyItems.isEmpty() || !dropListItems.isEmpty()
                    || !floorPresentations.isEmpty() || commandsRun > 0;
        }
    }

    /**
     * The station's effective Roll list: every referenced {@link LootableAsset}'s Rolls (via
     * {@link LootableCatalog}, order-preserving over {@code Loot.Tables}), THEN the station's
     * own inline {@code Loot.Rolls}. An unresolvable table id is skipped with a fine log (the
     * validator's {@code LOOT_UNKNOWN_TABLE} catches the authoring mistake ahead of runtime).
     */
    @Nonnull
    public static List<Roll> resolveRolls(@Nullable StationAsset.Loot loot) {
        List<Roll> out = new ArrayList<>();
        if (loot == null) {
            return out;
        }
        String[] tables = loot.getTables();
        if (tables != null) {
            for (String tableId : tables) {
                if (tableId == null || tableId.isBlank()) {
                    continue;
                }
                LootableAsset table = LootableCatalog.getInstance().get(tableId);
                if (table == null) {
                    Log.fine("STATION Loot.Tables references unknown lootable '" + tableId + "'");
                    continue;
                }
                Roll[] rolls = table.getRolls();
                if (rolls != null) {
                    out.addAll(Arrays.asList(rolls));
                }
            }
        }
        Roll[] inline = loot.getRolls();
        if (inline != null) {
            out.addAll(Arrays.asList(inline));
        }
        return out;
    }

    /**
     * Evaluate + apply every {@code rolls} entry whose {@link Roll#effectiveTrigger()} matches
     * {@code trigger}, against ONE {@code snapshot} for the whole batch. {@code cycleOutput} is
     * this cycle's {@code {ItemId, Quantity}} for {@code Grants.BonusOutputCopies} - pass
     * {@code null} for a trigger with no live cycle output (Completion); a roll authoring
     * {@code BonusOutputCopies} there is silently skipped (the validator warns at author time).
     * {@code store}/{@code blockX,Y,Z} are the SMOKE-FIX S3 (b) world-drop fallback target (a
     * {@code null} store degrades to the old "log and lose it" behavior only when the caller
     * genuinely cannot resolve one - every live call site has a store).
     */
    @Nonnull
    public static GrantResult rollAndGrant(@Nonnull List<Roll> rolls, @Nonnull String trigger,
            @Nonnull FactorSnapshot snapshot, @Nonnull Player player, @Nullable ItemStack cycleOutput,
            @Nullable PlayerRef playerRef, @Nonnull String stationId, @Nonnull String actionId, int cycleIndex,
            @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        GrantResult result = new GrantResult();
        CommandRewardExecutor.Placeholders placeholders = playerRef != null
                ? CommandRewardExecutor.Placeholders.of(playerRef, stationId, actionId, cycleIndex)
                : null;
        for (Roll roll : rolls) {
            if (roll == null || !trigger.equalsIgnoreCase(roll.effectiveTrigger())) {
                continue;
            }
            RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, snapshot::resolve,
                    () -> ThreadLocalRandom.current().nextDouble(100.0));
            if (!outcome.isHit()) {
                continue;
            }
            applyGrants(outcome.getTopGrants(), player, cycleOutput, placeholders, result, store, blockX, blockY, blockZ);
            applyGrants(outcome.getFloorGrants(), player, cycleOutput, placeholders, result, store, blockX, blockY, blockZ);
            // A floor's Presentation plays whenever the floor is REACHED (design 4.5.1), regardless
            // of whether that floor also authored Grants (the validator separately flags a
            // Grants-less floor as a content mistake - it does not silence the moment).
            if (outcome.getFloorPresentation() != null) {
                result.floorPresentations.add(outcome.getFloorPresentation());
            }
        }
        return result;
    }

    private static void applyGrants(@Nullable Roll.Grants grants, @Nonnull Player player,
            @Nullable ItemStack cycleOutput, @Nullable CommandRewardExecutor.Placeholders placeholders,
            @Nonnull GrantResult result, @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        if (grants == null) {
            return;
        }
        if (grants.getBonusOutputCopies() != null && grants.getBonusOutputCopies() > 0 && cycleOutput != null) {
            grantBonusOutputCopies(player, cycleOutput, grants.getBonusOutputCopies(), result,
                    store, blockX, blockY, blockZ);
        }
        String dropListId = grants.getDropList();
        if (dropListId != null && !dropListId.isBlank()) {
            grantDropList(player, dropListId, result, store, blockX, blockY, blockZ);
        }
        String[] commands = grants.getCommands();
        if (commands != null && placeholders != null) {
            for (String raw : commands) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                CommandRewardExecutor.run(raw, placeholders);
                result.commandsRun++;
            }
        }
    }

    /**
     * The non-deprecated replacement for {@code Player.getInventory().getStorage()}
     * ({@code Inventory#getStorage} is {@code @Deprecated(forRemoval=true)}; its own javadoc
     * names this exact replacement): the {@code InventoryComponent.Storage} component fetched
     * off the player's own ref/store, mirroring the established in-mod precedent
     * ({@code station.StationService#placeIntoCustody}'s {@code InventoryComponent.Hotbar}
     * fetch). {@code null} when the player's ref/store cannot be resolved (never throws).
     */
    @Nullable
    private static ItemContainer storageContainerOf(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        InventoryComponent.Storage storage = ref.getStore()
                .getComponent(ref, InventoryComponent.Storage.getComponentType());
        return storage != null ? storage.getInventory() : null;
    }

    /**
     * N extra copies of {@code cycleOutput}, storage-first; a copy that cannot fit drops as a
     * ground item at the block instead of being skipped (SMOKE-FIX S3 (b)) - every copy still
     * counts as granted either way, so the remaining copies keep rolling.
     */
    private static void grantBonusOutputCopies(@Nonnull Player player, @Nonnull ItemStack cycleOutput,
            int copies, @Nonnull GrantResult result, @Nullable Store<EntityStore> store,
            int blockX, int blockY, int blockZ) {
        ItemContainer container = storageContainerOf(player);
        for (int i = 0; i < copies; i++) {
            try {
                ItemStack bonus = new ItemStack(cycleOutput.getItemId(), cycleOutput.getQuantity());
                if (container != null && container.canAddItemStacks(List.of(bonus))) {
                    container.addItemStack(bonus);
                } else {
                    ItemDropUtil.dropAtBlock(store, blockX, blockY, blockZ, bonus);
                }
                result.bonusCopyItems.merge(cycleOutput.getItemId(), cycleOutput.getQuantity(), Integer::sum);
            } catch (Throwable t) {
                Log.fine("STATION loot bonus-copy grant failed: " + t.getMessage());
                return;
            }
        }
    }

    /**
     * Roll {@code dropListId} once via the native {@code ItemModule.getRandomItemDrops} (pure,
     * world-thread-safe; frequency control lives entirely in the droplist's own weighted
     * container) and grant every resulting stack storage-first; a stack that cannot fit drops as
     * a ground item at the block instead of being skipped (SMOKE-FIX S3 (b)).
     */
    private static void grantDropList(@Nonnull Player player, @Nonnull String dropListId,
            @Nonnull GrantResult result, @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        List<ItemStack> drops;
        try {
            drops = ItemModule.get().getRandomItemDrops(dropListId);
        } catch (Throwable t) {
            Log.fine("STATION loot droplist roll failed for '" + dropListId + "': " + t.getMessage());
            return;
        }
        if (drops == null || drops.isEmpty()) {
            return;
        }
        ItemContainer container = storageContainerOf(player);
        for (ItemStack stack : drops) {
            try {
                if (container != null && container.canAddItemStacks(List.of(stack))) {
                    container.addItemStack(stack);
                } else {
                    ItemDropUtil.dropAtBlock(store, blockX, blockY, blockZ, stack);
                }
                result.dropListItems.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            } catch (Throwable t) {
                Log.fine("STATION loot droplist item grant failed: " + t.getMessage());
            }
        }
    }
}
