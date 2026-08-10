package com.ziggfreed.rpgstations.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.station.ExtensionCatalog;
import com.ziggfreed.rpgstations.util.ItemDropUtil;
import com.ziggfreed.rpgstations.util.ItemGrantUtil;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The store-touching half of the conditional-lootable layer (design section 4.5): resolves a
 * station's effective {@link Roll} list ({@link #resolveRolls}), then evaluates + APPLIES every
 * roll matching a trigger against ONE {@link FactorSnapshot} for the batch ({@link
 * #rollAndGrant}). The pure decision core is {@link RollEvaluator}; this class owns inventory
 * mutation, native {@code ItemDropList} rolling, and command dispatch - never store mutation
 * inside the pure evaluator.
 *
 * <p><b>Concern boundary:</b> a Roll grants what ELSE a cycle handed over - including
 * {@code Grants.OutputItems}, ADDITIVE items of the cycle's own primary output (fractional, see
 * {@link OutputItemResolver}). Additive is the load-bearing word: the deterministic {@code Yield}
 * number and this one are directly comparable, so no file can silently MULTIPLY a quantity authored
 * in another.
 *
 * <p>Every grant routes through the shared {@code util.ItemGrantUtil} seam (round-5, hotbar-first
 * then backpack storage then drop-at-block - see that class's javadoc for the historic
 * consume-side caveat this does NOT inherit); a stack that cannot fit anywhere still lands as a
 * ground item at the station block instead of being discarded (never fails or stops the cycle).
 * {@link GrantResult} tallies what actually landed (inventory OR
 * ground) so the caller ({@code StationService}) can fold it into its own session item ledger and
 * play every EARNED celebration cue through its OWN {@code emitMoment} choke point (this
 * class stays presentation-agnostic; it only reports WHAT to play).
 *
 * <p><b>Earned, not merely reached</b> - the schema's smart-cue rule (see {@link Roll}) is enforced
 * HERE, because only this class knows what a grant actually produced: a cue authored beside a
 * grants group rides out only once applying that group genuinely handed something over, while a cue
 * with no grants beside it is pure presentation and always rides. That is why
 * {@link #applyGrants} reports a boolean rather than returning void.
 */
public final class LootEngine {

    private LootEngine() {
    }

    /**
     * The tally of one {@link #rollAndGrant} pass: which {@code ItemDropList}-derived items landed,
     * which reached floors want a moment played, plus the effect/contribution grants the caller
     * applies.
     */
    public static final class GrantResult {
        private final Map<String, Integer> dropListItems = new LinkedHashMap<>();
        private final List<Presentation> floorPresentations = new ArrayList<>();
        private final List<EffectRef> effectGrants = new ArrayList<>();
        private final List<Contribution> contributions = new ArrayList<>();
        private int commandsRun;
        private double outputItems;

        /** {@code ItemDropList}-derived items granted this pass (item id -> total quantity). */
        @Nonnull
        public Map<String, Integer> getDropListItems() {
            return dropListItems;
        }

        /**
         * Every EARNED celebration cue this pass collected, in roll-evaluation order - both
         * altitudes ride this one transport: a {@link Roll#getPresentation()} whose roll hit, and a
         * reached {@link Roll.Ladder.Floor#getPresentation()}, with the roll-level cue first when
         * one roll carries both.
         *
         * <p><b>Earned is the load-bearing word</b> (the schema's smart-cue rule, see
         * {@link Roll}): a cue with NO grants group of its own is pure presentation and rides here
         * on the plain hit/reach, while a cue authored BESIDE grants rides only when applying those
         * grants actually produced something. A referenced drop table whose own internal weights
         * resolve to nothing therefore lands no cue at all, instead of celebrating an empty hand.
         */
        @Nonnull
        public List<Presentation> getFloorPresentations() {
            return floorPresentations;
        }

        /**
         * Every granted {@code Roll.Grants.Effects[]} entry (top-level AND per-floor), in
         * roll-evaluation order (decision 51d). Reported here rather than applied inline: this class
         * stays presentation/session-agnostic (it only reports WHAT to apply), and the caller
         * ({@code StationService.applyGrantResult}) applies each native {@code EntityEffect} on the
         * player and TRACKS it on the session so {@code stop()}'s teardown removes them - exactly the
         * "reports what, caller acts" split {@link #getFloorPresentations} already uses.
         */
        @Nonnull
        public List<EffectRef> getEffectGrants() {
            return effectGrants;
        }

        /**
         * Every granted {@code Roll.Grants.Contributions[]} entry (top-level AND per-floor), in
         * roll-evaluation order. Collected ONLY for a {@code Cycle}-trigger pass - a Completion roll
         * has no cycle event to ride on, so its contributions are dropped here rather than silently
         * queued for a cycle that never comes (the validator warns at author time). Reported rather
         * than applied, the same "reports what, caller acts" split {@link #getEffectGrants} uses:
         * {@code StationService.applyGrantResult} buffers them onto the session and
         * {@code onCycleCompleted} forwards them UNSCALED on the cycle event.
         */
        @Nonnull
        public List<Contribution> getContributions() {
            return contributions;
        }

        public int getCommandsRun() {
            return commandsRun;
        }

        /**
         * The FRACTIONAL tally of extra items of the CYCLE's own primary output this pass granted
         * (top-level AND per-floor {@code Grants.OutputItems}, summed). ADDITIVE, never a multiplier
         * on the produced stack. Collected ONLY for a {@code Cycle}-trigger pass - a Completion roll
         * has no cycle output to add to, so its amount is dropped here rather than misapplied (the
         * validator warns at author time).
         *
         * <p>Deliberately reported as the raw SUM, unresolved: the whole pass's tally resolves to
         * whole items exactly once ({@link OutputItemResolver}, at the caller), so two rolls paying
         * {@code 0.5} each average one item instead of rounding twice. Reported rather than applied,
         * the same split {@link #getEffectGrants} uses: the caller knows which item id the cycle
         * produced and grants that many of it.
         */
        public double getOutputItems() {
            return outputItems;
        }

        public boolean anyGranted() {
            return !dropListItems.isEmpty() || !floorPresentations.isEmpty() || !effectGrants.isEmpty()
                    || !contributions.isEmpty() || commandsRun > 0 || outputItems > 0.0;
        }
    }

    /**
     * The effective Roll list for a {@link LootRef} (scope-2's unified loot vocabulary, replacing
     * the old {@code StationAsset.Loot}): every referenced {@link LootableAsset}'s effective Rolls
     * (via {@link LootableCatalog}, order-preserving over {@code LootRef.Lootables}), THEN the
     * ref's own inline {@code Rolls}. An unresolvable lootable id is skipped with a fine log (the
     * validator's {@code LOOT_UNKNOWN_TABLE} catches the authoring mistake ahead of runtime).
     *
     * <p><b>A referenced table's rolls are its EFFECTIVE ones</b>: whatever it authors, plus every
     * {@code Target:{Lootable}} {@code ExtensionAsset}'s appended {@code Rolls}. The merge belongs
     * at THIS read rather than at each caller, so a table gains its extended rolls everywhere it is
     * referenced - an action's {@code Bonus} and a step's {@code Roll} phase alike - and no site
     * can be left seeing the unextended table. That is also why this reaches across into
     * {@code station.ExtensionCatalog}: the fold-generation-cached extension gather is one
     * authority for the whole mod, never a per-package copy. The catalog read is per call and
     * derives nothing, so no cache-invalidation companion is needed here.
     */
    @Nonnull
    public static List<Roll> resolveRolls(@Nullable LootRef loot) {
        return resolveRolls(loot, "Loot.Lootables");
    }

    /**
     * As above, with the author-facing {@code siteLabel} the unknown-lootable log names (e.g.
     * {@code "Roll step 'Strike'"}), so one resolution serves every reference site without any of
     * them losing its own diagnostic.
     */
    @Nonnull
    public static List<Roll> resolveRolls(@Nullable LootRef loot, @Nonnull String siteLabel) {
        List<Roll> out = new ArrayList<>();
        if (loot == null) {
            return out;
        }
        String[] lootables = loot.getLootables();
        if (lootables != null) {
            for (String tableId : lootables) {
                if (tableId == null || tableId.isBlank()) {
                    continue;
                }
                LootableAsset table = LootableCatalog.getInstance().get(tableId);
                if (table == null) {
                    Log.fine("STATION " + siteLabel + " references unknown lootable '" + tableId + "'");
                    continue;
                }
                Roll[] rolls = ExtensionCatalog.getInstance().applyToLootableRolls(tableId, table.getRolls());
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
     * {@code trigger}, against ONE {@code snapshot} for the whole batch.
     * {@code store}/{@code blockX,Y,Z} are the world-drop fallback target (a
     * {@code null} store degrades to "log and lose it" only when the caller
     * genuinely cannot resolve one - every live call site has a store).
     *
     * <p>The engine handles are folded into the four seams the pass actually consumes (factor
     * lookup, chance sample, command placeholders, drop-table granter) and handed to the core
     * below; this method is the live adapter, nothing more.
     */
    @Nonnull
    public static GrantResult rollAndGrant(@Nonnull List<Roll> rolls, @Nonnull String trigger,
            @Nonnull FactorSnapshot snapshot, @Nonnull Player player,
            @Nullable PlayerRef playerRef, @Nonnull String stationId, @Nonnull String actionId, int cycleIndex,
            @Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        CommandRewardExecutor.Placeholders placeholders = playerRef != null
                ? CommandRewardExecutor.Placeholders.of(playerRef, stationId, actionId, cycleIndex)
                : null;
        return rollAndGrant(rolls, trigger, snapshot::resolve,
                () -> ThreadLocalRandom.current().nextDouble(100.0), placeholders,
                id -> rollAndGrantDropList(id, player, commandBuffer, store, blockX, blockY, blockZ));
    }

    /**
     * The seam-driven core of the pass, with every engine handle already reduced to an injected
     * function: {@code lookup} resolves a factor, {@code chanceRoll} supplies a fresh {@code [0,100)}
     * sample, {@code placeholders} substitutes a command grant (null = no command can run), and
     * {@code dropLists} rolls-and-grants one native table. Package-visible so the smart-cue rule can
     * be exercised end to end against a PINNED table outcome rather than live randomness.
     */
    @Nonnull
    static GrantResult rollAndGrant(@Nonnull List<Roll> rolls, @Nonnull String trigger,
            @Nonnull RollEvaluator.FactorLookup lookup, @Nonnull DoubleSupplier chanceRoll,
            @Nullable CommandRewardExecutor.Placeholders placeholders, @Nonnull DropListGranter dropLists) {
        GrantResult result = new GrantResult();
        // One-shot contributions ride the cycle-completed event, and OutputItems adds to the cycle's
        // own output - both exist only on a Cycle-trigger pass.
        boolean cycleTrigger = Roll.TRIGGER_CYCLE.equalsIgnoreCase(trigger);
        for (Roll roll : rolls) {
            if (roll == null || !trigger.equalsIgnoreCase(roll.effectiveTrigger())) {
                continue;
            }
            RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup, chanceRoll);
            if (!outcome.isHit()) {
                continue;
            }
            boolean topProduced = applyGrants(outcome.getTopGrants(), dropLists, placeholders, result, cycleTrigger);
            boolean floorProduced = applyGrants(outcome.getFloorGrants(), dropLists, placeholders, result, cycleTrigger);
            // THE SMART-CUE RULE (see Roll's javadoc), applied at both altitudes against each cue's
            // OWN grants group: a cue with no grants beside it is pure presentation and plays on the
            // plain hit/reach, while a cue authored beside grants plays only once those grants
            // genuinely produced something. That is what keeps a referenced drop table whose own
            // internal weights rolled Empty from firing a jackpot fanfare over an empty hand.
            collectEarnedCue(result, roll.getPresentation(), outcome.getTopGrants(), topProduced);
            collectEarnedCue(result, outcome.getFloorPresentation(), outcome.getFloorGrants(), floorProduced);
        }
        return result;
    }

    /**
     * The smart-cue decision: collect {@code cue} when it is a PURE cue (its paired {@code grants}
     * group is null or authors nothing) or when applying that group actually {@code produced}
     * something. Shared by the roll-level and floor-level altitudes so one rule governs both.
     */
    private static void collectEarnedCue(@Nonnull GrantResult result, @Nullable Presentation cue,
            @Nullable Roll.Grants grants, boolean produced) {
        if (cue == null) {
            return;
        }
        if (grants == null || grants.isEmpty() || produced) {
            result.floorPresentations.add(cue);
        }
    }

    /**
     * The injectable native drop-table boundary: rolls ONE {@code ItemDropList} and grants every
     * stack it produced, reporting what actually landed (item id -&gt; quantity) so an EMPTY answer
     * is the engine's own "that table paid nothing this time". Production passes a lambda over
     * {@link #rollAndGrantDropList}; a fixture test passes a fake, which is what makes the smart-cue
     * rule testable against a PINNED table outcome instead of live randomness (the same
     * injected-seam discipline {@link RollEvaluator}'s chance sample and {@link OutputItemResolver}'s
     * fraction sample already use).
     */
    @FunctionalInterface
    interface DropListGranter {
        @Nonnull
        Map<String, Integer> grant(@Nonnull String dropListId);
    }

    /**
     * Applies one {@code Grants} group and reports whether it PRODUCED anything - the measurement
     * the smart-cue rule reads. Produced means: a drop-table item genuinely landed after that
     * table's own internal weights resolved, a command ran, an effect was collected, an
     * {@code OutputItems} amount was tallied, or a contribution was collected. An absent, empty, or
     * entirely-skipped group produces nothing.
     *
     * <p>The player/store/block handles fold into the {@code dropLists} seam rather than riding as
     * five more parameters: they exist for exactly one leaf, and closing over them there is what
     * lets a fixture test drive this method with no engine handles at all.
     */
    static boolean applyGrants(@Nullable Roll.Grants grants, @Nonnull DropListGranter dropLists,
            @Nullable CommandRewardExecutor.Placeholders placeholders,
            @Nonnull GrantResult result, boolean cycleTrigger) {
        if (grants == null) {
            return false;
        }
        boolean produced = false;
        // Grants.OutputItems: TALLY the additive extra items of the cycle's own primary output for
        // the caller to grant (it owns the item id). Summed as a FRACTIONAL amount and resolved to
        // whole items once per cycle at the caller, never rounded per roll. Skipped entirely outside
        // a Cycle trigger.
        if (cycleTrigger) {
            double tally = grants.effectiveOutputItems();
            if (tally > 0.0) {
                result.outputItems += tally;
                produced = true;
            }
        }
        // Grants.DropLists[]: each table rolls INDEPENDENTLY, in authored order, so "a guaranteed
        // common table plus a rare one" is two entries rather than a synthetic merged asset. An
        // EMPTY answer is a real outcome, not a failure: the table's own weighted container chose
        // its Empty branch, and nothing was produced for a cue to celebrate.
        String[] tables = grants.getDropLists();
        if (tables != null) {
            for (String dropListId : tables) {
                if (dropListId == null || dropListId.isBlank()) {
                    continue;
                }
                Map<String, Integer> landed = dropLists.grant(dropListId);
                for (Map.Entry<String, Integer> e : landed.entrySet()) {
                    result.dropListItems.merge(e.getKey(), e.getValue(), Integer::sum);
                    produced = true;
                }
            }
        }
        String[] commands = grants.getCommands();
        if (commands != null && placeholders != null) {
            for (String raw : commands) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                CommandRewardExecutor.run(raw, placeholders);
                result.commandsRun++;
                produced = true;
            }
        }
        // Grants.Effects[] (decision 51d): COLLECT every authored native-effect ref so the caller
        // applies + session-tracks it (the "reports what, caller acts" split - see
        // GrantResult.getEffectGrants). A blank-id ref is dropped here (never surfaced).
        EffectRef[] effects = grants.getEffects();
        if (effects != null) {
            for (EffectRef effect : effects) {
                if (effect != null && effect.hasId()) {
                    result.effectGrants.add(effect);
                    produced = true;
                }
            }
        }
        // Grants.Contributions[]: COLLECT every postable one-shot entry for the caller to forward
        // UNSCALED on the cycle event. Skipped entirely outside a Cycle trigger.
        Contribution[] posts = grants.getContributions();
        if (cycleTrigger && posts != null) {
            for (Contribution post : posts) {
                if (post != null && post.isPostable()) {
                    result.contributions.add(post);
                    produced = true;
                }
            }
        }
        return produced;
    }

    /**
     * The production {@link DropListGranter}: roll {@code dropListId} once via the native
     * {@code ItemModule.getRandomItemDrops} (pure, world-thread-safe; frequency control lives
     * entirely in the droplist's own weighted container) and grant every resulting stack
     * hotbar-first then backpack storage then drop-at-block (via {@code util.ItemGrantUtil}).
     *
     * <p>Returns what actually LANDED (item id -&gt; quantity). An empty answer covers all three
     * ways a table can pay nothing - it rolled its own Empty branch, the roll itself failed, or
     * every stack failed to grant - and the caller reads that as "produced nothing", which is what
     * keeps a celebration cue silent over an empty hand.
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
        // Overflow is collected and dropped ONCE at the end rather than per stack. A single roll
        // can hand back several stacks of the same item (a table pulling a shared list more than
        // once does exactly that), and dropping each leftover on its own scattered a find across
        // several ground entities - so the pile a player walked up to showed only part of what the
        // notification told them they got. One drop call per roll means the pile equals the
        // remainder.
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : drops) {
            try {
                // Read the quantity BEFORE granting, and record it only when the stack actually
                // reached the player - inventory, or the ground when it was full. A stack that
                // went nowhere no longer exists, so counting it here would tell the player they
                // found something they never received and inflate the session summary with it.
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
