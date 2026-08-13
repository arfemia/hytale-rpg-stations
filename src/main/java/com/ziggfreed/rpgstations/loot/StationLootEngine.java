package com.ziggfreed.rpgstations.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootPool;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.LootableConfig;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.reward.RewardKindRegistry;
import com.ziggfreed.common.subject.Subject;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.station.ExtensionCatalog;
import com.ziggfreed.rpgstations.util.ItemDropUtil;
import com.ziggfreed.rpgstations.util.ItemGrantUtil;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The STATION-shaped half of the loot pass: it resolves what a site evaluates (this engine's own
 * extension composition), wires the shared loot engine's seams to a work session's world, and
 * reports back the three station-only outcomes a cycle needs in its hands.
 *
 * <p>The rolling itself - conditions, chance, ladder, grants, and the smart-cue rule - is
 * {@code com.ziggfreed.common.loot}'s, so identical JSON behaves identically at a station, in a
 * chest, and at a quest turn-in. Nothing about that decision is re-derived here.
 *
 * <p><b>What this class adds, and why each piece cannot live in the shared engine:</b>
 * <ul>
 *   <li><b>Extension-aware table resolution.</b> A referenced table's rolls are its EFFECTIVE ones:
 *       whatever it authors, plus every {@code Target:{Lootable}} extension's appended rolls. The
 *       merge belongs at THIS read rather than at each caller, so a table gains its extended rolls
 *       everywhere it is referenced and no site can be left seeing the unextended table. The table's
 *       {@code Pool} rides along with them, so a station referencing a pooled table draws that bag
 *       exactly as a chest would.</li>
 *   <li><b>The station sinks.</b> Item grants go hotbar-first, then backpack storage, then a ground
 *       drop at the station block; native drop lists roll through {@code ItemModule} and grant the
 *       same way. A stack that fits nowhere still lands as a ground item rather than being
 *       discarded.</li>
 *   <li><b>The three station reward kinds</b> ({@link StationRewardKinds}), which COLLECT onto this
 *       pass rather than acting: an effect the session must track and tear down, a one-shot
 *       contribution the cycle event is about to carry, and a fractional output-item tally the whole
 *       cycle sums before resolving it to whole items exactly once.</li>
 * </ul>
 *
 * <p>Like the shared engine, this class stays presentation-agnostic: it reports which CUE ids were
 * earned and {@code StationService} plays each one through its own {@code emitMoment} choke point,
 * where the action's {@code Moments} map and every applicable flair get their say.
 */
public final class StationLootEngine {

    /** The trigger a roll answers on every completed work cycle. */
    public static final String TRIGGER_CYCLE = "Cycle";

    /** The trigger a roll answers once, at session stop. */
    public static final String TRIGGER_COMPLETION = "Completion";

    /** Stands in for a worker whose player reference could not be resolved (see {@link #subjectOf}). */
    private static final UUID ANONYMOUS_WORKER = new UUID(0L, 0L);

    private StationLootEngine() {
    }

    /**
     * What one {@link #rollAndGrant} pass produced: the items that reached the player, the cue ids
     * to play, and the three station-only collections the caller applies.
     */
    public static final class GrantResult implements StationRewardKinds.Sink {

        private final Map<String, Integer> dropListItems = new LinkedHashMap<>();
        private final List<String> cues = new ArrayList<>();
        private final List<EffectRef> effectGrants = new ArrayList<>();
        private final List<Contribution> contributions = new ArrayList<>();
        private final boolean cycleTrigger;
        private int commandsRun;
        private double outputItems;

        public GrantResult(boolean cycleTrigger) {
            this.cycleTrigger = cycleTrigger;
        }

        /** Every item that reached the player this pass (item id -&gt; total quantity), merged. */
        @Nonnull
        public Map<String, Integer> getDropListItems() {
            return dropListItems;
        }

        /**
         * The EARNED cue ids, in evaluation order, roll-level before floor-level within one roll.
         * Each is a MOMENT id the caller emits, so a cue resolves through the same action
         * {@code Moments} map and flair overlay every other station moment does.
         *
         * <p>Earned is the load-bearing word: a cue with no grants beside it rides on the plain
         * hit or floor reach, and a cue authored beside grants rides only once those grants
         * genuinely produced something - so a drop table whose own internal weights rolled empty
         * never fires a fanfare over an empty hand.
         */
        @Nonnull
        public List<String> getCues() {
            return cues;
        }

        /**
         * Every {@code rpgstations:effect} reward this pass collected. Reported rather than applied:
         * the caller applies each native EntityEffect and TRACKS it on the session, so the session's
         * own teardown removes it.
         */
        @Nonnull
        public List<EffectRef> getEffectGrants() {
            return effectGrants;
        }

        /**
         * Every {@code rpgstations:contribution} reward this pass collected, for the caller to
         * forward UNSCALED on the cycle event - a find's grant is worth the same whatever tool the
         * player holds and whether or not the cycle was an idle one.
         */
        @Nonnull
        public List<Contribution> getContributions() {
            return contributions;
        }

        public int getCommandsRun() {
            return commandsRun;
        }

        /**
         * The FRACTIONAL tally of extra units of the cycle's own primary output this pass granted.
         * ADDITIVE, never a multiplier on the produced stack, so this number and the deterministic
         * {@code Yield} number stay directly comparable.
         *
         * <p>Reported as the raw SUM, unresolved: the whole pass's tally resolves to whole items
         * exactly once ({@link OutputItemResolver}, at the caller), so two rolls paying {@code 0.5}
         * each average one item instead of rounding twice.
         */
        public double getOutputItems() {
            return outputItems;
        }

        public boolean anyGranted() {
            return !dropListItems.isEmpty() || !cues.isEmpty() || !effectGrants.isEmpty()
                    || !contributions.isEmpty() || commandsRun > 0 || outputItems > 0.0;
        }

        @Override
        public void effect(@Nonnull EffectRef effect) {
            if (effect.hasId()) {
                effectGrants.add(effect);
            }
        }

        @Override
        public void contribution(@Nonnull Contribution post) {
            if (post.isPostable()) {
                contributions.add(post);
            }
        }

        @Override
        public void outputItems(double count) {
            outputItems += count;
        }

        @Override
        public boolean acceptsCycleGrants() {
            return cycleTrigger;
        }
    }

    // ==================== resolution ====================

    /** Everything a {@link LootRef} evaluates, with this engine's extension composition applied. */
    @Nonnull
    public static LootEngine.Resolved resolve(@Nullable LootRef loot) {
        return resolve(loot, "Bonus.Lootables");
    }

    /**
     * As above, with the author-facing {@code siteLabel} the unknown-table log names (e.g.
     * {@code "Roll step 'Strike'"}), so one resolution serves every reference site without any of
     * them losing its own diagnostic.
     *
     * <p>A referenced table contributes BOTH halves of what it holds: its rolls (its own, plus every
     * {@code Target:{Lootable}} extension's appended ones) and its {@code Pool}. Each table keeps its
     * own pool rather than the pools being poured together, because a pool is a bag whose entries
     * compete for the same picks - merging two would change the odds inside both. Two referenced
     * tables draw twice, once each.
     *
     * <p>An id no table answers to is SKIPPED rather than failing the pass - one bad reference must
     * not cost a player the rest of the loot, and the validator catches the same mistake at
     * authoring time where it is cheap to fix.
     */
    @Nonnull
    public static LootEngine.Resolved resolve(@Nullable LootRef loot, @Nonnull String siteLabel) {
        List<Roll> rolls = new ArrayList<>();
        List<LootPool> pools = new ArrayList<>();
        if (loot == null) {
            return new LootEngine.Resolved(rolls, pools);
        }
        String[] lootables = loot.getLootables();
        if (lootables != null) {
            for (String tableId : lootables) {
                if (tableId == null || tableId.isBlank()) {
                    continue;
                }
                LootableAsset table = LootableConfig.getInstance().resolve(tableId);
                if (table == null) {
                    Log.fine("STATION " + siteLabel + " references unknown lootable '" + tableId + "'");
                    continue;
                }
                Roll[] extended = ExtensionCatalog.getInstance().applyToLootableRolls(tableId, table.getRolls());
                if (extended != null) {
                    rolls.addAll(Arrays.asList(extended));
                }
                pools.addAll(table.poolOrEmpty());
            }
        }
        Roll[] inline = loot.getRolls();
        if (inline != null) {
            rolls.addAll(Arrays.asList(inline));
        }
        return new LootEngine.Resolved(rolls, pools);
    }

    // ==================== the pass ====================

    /**
     * Evaluate and apply everything {@code resolved} holds that answers to {@code trigger}, against
     * ONE {@code lookup} for the whole batch. {@code store}/{@code blockX,Y,Z} are the ground-drop
     * fallback target.
     */
    @Nonnull
    public static GrantResult rollAndGrant(@Nonnull LootEngine.Resolved resolved, @Nonnull String trigger,
            @Nonnull FactorLookup lookup, @Nonnull Player player, @Nullable PlayerRef playerRef,
            @Nonnull String stationId, @Nonnull String actionId, int cycleIndex,
            @Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        LootEngine.DropListSink dropLists =
                id -> rollAndGrantDropList(id, player, commandBuffer, store, blockX, blockY, blockZ);
        LootEngine.ItemSink items = (itemId, count) ->
                grantItem(itemId, count, player, commandBuffer, store, blockX, blockY, blockZ);
        return rollAndGrant(resolved, trigger, lookup, () -> ThreadLocalRandom.current().nextDouble(),
                items, dropLists, subjectOf(player, playerRef),
                playerRef != null
                        ? CommandRewardExecutor.placeholders(playerRef, stationId, actionId, cycleIndex)
                        : null,
                stationId);
    }

    /**
     * The seam-driven core, with every engine handle already reduced to an injected function, so a
     * fixture test drives a whole pass against a PINNED table outcome instead of live randomness.
     *
     * <p>Rolls answer to a trigger and a pool does not, which is why the two halves are applied in
     * two calls rather than one. A referenced table's pool is drawn on the CYCLE pass, the station's
     * own default moment: drawing it again on the completion pass would hand one session the same
     * bag twice, so the completion pass evaluates that table's Completion-trigger rolls and nothing
     * else. Rolls apply before pool picks, matching the shared engine's own order.
     */
    @Nonnull
    static GrantResult rollAndGrant(@Nonnull LootEngine.Resolved resolved, @Nonnull String trigger,
            @Nonnull FactorLookup lookup, @Nonnull DoubleSupplier chanceSample,
            @Nullable LootEngine.ItemSink items, @Nullable LootEngine.DropListSink dropLists,
            @Nonnull Subject subject, @Nullable Map<String, String> placeholders,
            @Nonnull String sourceId) {
        GrantResult result = new GrantResult(TRIGGER_CYCLE.equalsIgnoreCase(trigger));
        RewardKindRegistry kinds = StationRewardKinds.forPass(result);
        LootEngine.Sinks.Builder builder = LootEngine.Sinks.builder()
                .items(items)
                .dropLists(dropLists)
                .rewards(kinds, subject)
                .sourceId("station:" + sourceId)
                .warn(message -> Log.fine("STATION loot " + message));
        if (placeholders != null) {
            builder.commands(CommandRewardExecutor.consoleAs(placeholders.getOrDefault("player", "")),
                    placeholders);
        }
        LootEngine.Sinks sinks = builder.build();
        absorb(result, LootEngine.rollAndGrant(resolved.rolls(), trigger, lookup, chanceSample, sinks));
        if (result.cycleTrigger && !resolved.pools().isEmpty()) {
            absorb(result, LootEngine.rollAndGrant(List.of(), resolved.pools(), null, lookup,
                    chanceSample, sinks));
        }
        return result;
    }

    /** Fold one shared-engine pass into the station tally, so two calls read as one pass. */
    private static void absorb(@Nonnull GrantResult result, @Nonnull LootEngine.Result shared) {
        for (Map.Entry<String, Integer> entry : shared.getItems().entrySet()) {
            result.dropListItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        result.cues.addAll(shared.getCues());
        result.commandsRun += shared.getCommandsRun();
    }

    /**
     * The subject a registered reward kind is paid to: the PLAYER on the handle, which is where the
     * shared {@code item}/{@code lootable}/{@code stamped_item} kinds look for one. The three
     * station kinds carry their collection through the per-pass registry instead, so both reach what
     * they need without either having to know about the other.
     *
     * <p>It is never null, even when the session has no resolvable {@code PlayerRef}. A null subject
     * would switch the WHOLE reward leaf off, and the three station kinds collect onto the pass
     * rather than paying anything to anybody - so an unidentifiable worker would silently lose their
     * contributions and their extra output alongside the item rewards that genuinely cannot land.
     * An anonymous subject lets the collecting kinds run and leaves the paying ones to fail on their
     * own terms, which is the outcome each of them is written for.
     */
    @Nonnull
    private static Subject subjectOf(@Nullable Player player, @Nullable PlayerRef playerRef) {
        UUID uuid = playerRef != null ? playerRef.getUuid() : null;
        String username = playerRef != null ? playerRef.getUsername() : null;
        return new Subject(uuid != null ? uuid : ANONYMOUS_WORKER,
                username != null ? username : "", player);
    }

    /**
     * The station {@code Items} sink: hand over one exact stack hotbar-first, then backpack storage,
     * then the ground at the station block, answering how many actually landed.
     */
    private static int grantItem(@Nonnull String itemId, int count, @Nonnull Player player,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nullable Store<EntityStore> store,
            int blockX, int blockY, int blockZ) {
        ItemStack stack;
        try {
            stack = new ItemStack(itemId, count);
        } catch (Throwable t) {
            Log.fine("STATION loot item grant failed for '" + itemId + "': " + t.getMessage());
            return 0;
        }
        if (ItemGrantUtil.grantToInventory(player, stack)) {
            return count;
        }
        return ItemDropUtil.dropAtBlock(commandBuffer, store, blockX, blockY, blockZ, List.of(stack))
                ? count : 0;
    }

    /**
     * The station {@code DropLists} sink: roll {@code dropListId} once via the native
     * {@code ItemModule.getRandomItemDrops} (pure, world-thread-safe; frequency control lives
     * entirely in the drop list's own weighted container) and grant every resulting stack.
     *
     * <p>Answers what actually LANDED. An empty answer covers all three ways a table can pay
     * nothing - it rolled its own empty branch, the roll itself failed, or every stack failed to
     * grant - and the shared engine reads that as "produced nothing", which is what keeps a
     * celebration cue silent over an empty hand.
     */
    @Nonnull
    private static Map<String, Integer> rollAndGrantDropList(@Nonnull String dropListId, @Nonnull Player player,
            @Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        List<ItemStack> drops;
        try {
            drops = ItemModule.get().getRandomItemDrops(dropListId);
        } catch (Throwable t) {
            Log.fine("STATION loot droplist roll failed for '" + dropListId + "': " + t.getMessage());
            return Map.of();
        }
        if (drops == null || drops.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> landed = new LinkedHashMap<>();
        // Overflow is collected and dropped ONCE at the end rather than per stack. A single roll can
        // hand back several stacks of the same item, and dropping each leftover on its own scattered
        // a find across several ground entities - so the pile a player walked up to showed only part
        // of what the notification told them they got. One drop call per roll means the pile equals
        // the remainder.
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : drops) {
            try {
                // Read the quantity BEFORE granting, and record it only when the stack actually
                // reached the player - inventory, or the ground when it was full. A stack that went
                // nowhere no longer exists, so counting it here would tell the player they found
                // something they never received.
                int quantity = stack.getQuantity();
                if (ItemGrantUtil.grantToInventory(player, stack)) {
                    landed.merge(stack.getItemId(), quantity, Integer::sum);
                } else {
                    overflow.add(stack);
                }
            } catch (Throwable t) {
                Log.fine("STATION loot droplist item grant failed: " + t.getMessage());
            }
        }
        if (!overflow.isEmpty()
                && ItemDropUtil.dropAtBlock(commandBuffer, store, blockX, blockY, blockZ, overflow)) {
            for (ItemStack dropped : overflow) {
                landed.merge(dropped.getItemId(), dropped.getQuantity(), Integer::sum);
            }
        }
        return landed;
    }
}
