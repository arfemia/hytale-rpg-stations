package com.ziggfreed.rpgstations.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
 * play the reached floors' presentations through its OWN {@code emitMoment} choke point (this
 * class stays presentation-agnostic; it only reports WHAT to play).
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

        /** Every reached floor's non-null {@code Presentation}, in roll-evaluation order. */
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
     */
    @Nonnull
    public static GrantResult rollAndGrant(@Nonnull List<Roll> rolls, @Nonnull String trigger,
            @Nonnull FactorSnapshot snapshot, @Nonnull Player player,
            @Nullable PlayerRef playerRef, @Nonnull String stationId, @Nonnull String actionId, int cycleIndex,
            @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ) {
        GrantResult result = new GrantResult();
        // One-shot contributions ride the cycle-completed event, and OutputItems adds to the cycle's
        // own output - both exist only on a Cycle-trigger pass.
        boolean cycleTrigger = Roll.TRIGGER_CYCLE.equalsIgnoreCase(trigger);
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
            applyGrants(outcome.getTopGrants(), player, placeholders, result, store, blockX, blockY,
                    blockZ, cycleTrigger);
            applyGrants(outcome.getFloorGrants(), player, placeholders, result, store, blockX, blockY,
                    blockZ, cycleTrigger);
            // A floor's Presentation plays whenever the floor is REACHED (design 4.5.1), regardless
            // of whether that floor also authored Grants (the validator separately flags a
            // Grants-less floor as a content mistake - it does not silence the moment).
            if (outcome.getFloorPresentation() != null) {
                result.floorPresentations.add(outcome.getFloorPresentation());
            }
        }
        return result;
    }

    // Package-visible for the effect-collection fixture test (an effects-only Grants never touches
    // player/store, so the test drives it with null engine handles - see LootEngineEffectGrantTest).
    static void applyGrants(@Nullable Roll.Grants grants, @Nonnull Player player,
            @Nullable CommandRewardExecutor.Placeholders placeholders,
            @Nonnull GrantResult result, @Nullable Store<EntityStore> store, int blockX, int blockY, int blockZ,
            boolean cycleTrigger) {
        if (grants == null) {
            return;
        }
        // Grants.OutputItems: TALLY the additive extra items of the cycle's own primary output for
        // the caller to grant (it owns the item id). Summed as a FRACTIONAL amount and resolved to
        // whole items once per cycle at the caller, never rounded per roll. Skipped entirely outside
        // a Cycle trigger.
        if (cycleTrigger) {
            result.outputItems += grants.effectiveOutputItems();
        }
        // Grants.DropLists[]: each table rolls INDEPENDENTLY, in authored order, so "a guaranteed
        // common table plus a rare one" is two entries rather than a synthetic merged asset.
        String[] dropLists = grants.getDropLists();
        if (dropLists != null) {
            for (String dropListId : dropLists) {
                if (dropListId != null && !dropListId.isBlank()) {
                    grantDropList(player, dropListId, result, store, blockX, blockY, blockZ);
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
                }
            }
        }
    }

    /**
     * Roll {@code dropListId} once via the native {@code ItemModule.getRandomItemDrops} (pure,
     * world-thread-safe; frequency control lives entirely in the droplist's own weighted
     * container) and grant every resulting stack hotbar-first then backpack storage then
     * drop-at-block (round-5, via {@code util.ItemGrantUtil}).
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
        for (ItemStack stack : drops) {
            try {
                ItemGrantUtil.grant(player, stack, store, blockX, blockY, blockZ);
                result.dropListItems.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            } catch (Throwable t) {
                Log.fine("STATION loot droplist item grant failed: " + t.getMessage());
            }
        }
    }
}
