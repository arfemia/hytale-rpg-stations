package com.ziggfreed.rpgstations.loot;

import java.util.ArrayList;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.GroundSpillSinks;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.reward.RewardKinds;
import com.ziggfreed.common.subject.Subject;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.station.ExtensionCatalog;
import com.ziggfreed.rpgstations.util.ItemDropUtil;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The STATION-shaped half of the loot pass: it resolves what a site evaluates through the shared
 * table composition, wires the shared loot engine's seams to a work session's world, and reports
 * back the three station-only outcomes a cycle needs in its hands.
 *
 * <p>The rolling itself - conditions, chance, ladder, grants, and the smart-cue rule - is
 * {@code com.ziggfreed.common.loot}'s, so identical JSON behaves identically at a station, in a
 * chest, and at a quest turn-in. Nothing about that decision is re-derived here.
 *
 * <p><b>What this class adds, and why each piece cannot live in the shared engine:</b>
 * <ul>
 *   <li><b>A per-site diagnostic on the one table read.</b> A referenced table's rolls are its
 *       EFFECTIVE ones: whatever it authors, every {@code ContributesTo} contributor's, then every
 *       {@code Target:{Lootable}} extension's appended rolls. The extension rolls are not merged
 *       here: {@link ExtensionCatalog#registerLootableRollSource} hands them to the shared table
 *       composition, so a table gains them everywhere it is rolled, at a station or anywhere else,
 *       and no site can be left seeing the unextended table. {@link #resolve} is that shared read
 *       plus the site label its unknown-table line names. The table's {@code Pool} rides along with
 *       the rolls, so a station referencing a pooled table draws that bag exactly as a chest
 *       would.</li>
 *   <li><b>Where the station sinks drop.</b> The sinks themselves are the shared ground-spill
 *       preset ({@link GroundSpillSinks}): item grants go hotbar-first, then backpack storage, then
 *       ONE ground pile at the station block; native drop lists roll through the engine and grant
 *       the same way. What is the station's own is the drop target, its one ground sink
 *       ({@link ItemDropUtil}). A stack that fits nowhere still lands as a ground item rather than
 *       being discarded.</li>
 *   <li><b>The three station reward kinds</b> ({@link StationRewardKinds}), which COLLECT onto this
 *       pass rather than acting: an effect the session must track and tear down, a one-shot
 *       contribution the cycle event is about to carry, and a fractional output-item tally the whole
 *       cycle sums before resolving it to whole items exactly once. They live in the one shared
 *       vocabulary like every other kind; what is this pass's own is the {@link GrantResult} it
 *       layers onto the subject it pays, which is where they collect.</li>
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
        private final Map<String, Integer> expectedItems = new LinkedHashMap<>();
        private final Map<String, Integer> foundItems = new LinkedHashMap<>();
        private final List<String> cues = new ArrayList<>();
        private final List<EffectRef> effectGrants = new ArrayList<>();
        private final List<Contribution> contributions = new ArrayList<>();
        private final boolean cycleTrigger;
        private int commandsRun;
        private double outputItems;

        public GrantResult(boolean cycleTrigger) {
            this.cycleTrigger = cycleTrigger;
        }

        /** Every item that reached the player this pass (item id -&gt; total quantity), merged, whatever its origin. */
        @Nonnull
        public Map<String, Integer> getDropListItems() {
            return dropListItems;
        }

        /**
         * The part of {@link #getDropListItems()} paid by a roll authored {@code Expected}: the
         * moment's expected payout, shown as ORDINARY produced output (the plain item row, the
         * produced ledger row), never as a find. Empty for every table that authors no
         * {@code Expected}, which is every table shipped before the knob existed.
         */
        @Nonnull
        public Map<String, Integer> getExpectedItems() {
            return expectedItems;
        }

        /**
         * The part of {@link #getDropListItems()} paid by a find: a roll not authored
         * {@code Expected}, or a pool pick. Shown as a windfall (the gold row).
         */
        @Nonnull
        public Map<String, Integer> getFoundItems() {
            return foundItems;
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

    /** Everything a {@link LootRef} evaluates, through the shared table composition. */
    @Nonnull
    public static LootEngine.Resolved resolve(@Nullable LootRef loot) {
        return resolve(loot, "Bonus.Lootables");
    }

    /**
     * As above, with the author-facing {@code siteLabel} the unknown-table log names (e.g.
     * {@code "Roll step 'Strike'"}), so one resolution serves every reference site without any of
     * them losing its own diagnostic.
     *
     * <p>This is the shared {@link LootEngine#resolve}, nothing more: each referenced table
     * contributes its rolls (its own, its {@code ContributesTo} contributors', then every
     * {@code Target:{Lootable}} extension's, which reach it as a registered roll source) and its
     * {@code Pool}, then the ref's own inline rolls follow. Each table keeps its own pool rather
     * than the pools being poured together, because a pool is a bag whose entries compete for the
     * same picks; two referenced tables draw twice, once each.
     *
     * <p>An id no table answers to is SKIPPED rather than failing the pass - one bad reference must
     * not cost a player the rest of the loot, and the validator catches the same mistake at
     * authoring time where it is cheap to fix.
     */
    @Nonnull
    public static LootEngine.Resolved resolve(@Nullable LootRef loot, @Nonnull String siteLabel) {
        return LootEngine.resolve(loot,
                tableId -> Log.fine("STATION " + siteLabel + " references unknown lootable '" + tableId + "'"));
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
        GroundSpillSinks spill = stationSinks(player, commandBuffer, store, blockX, blockY, blockZ);
        return rollAndGrant(resolved, trigger, lookup, () -> ThreadLocalRandom.current().nextDouble(),
                spill.items(), spill.dropLists(), subjectOf(player, playerRef),
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
     *
     * <p>Rewards pay through the ONE shared vocabulary ({@link RewardKinds#shared()}), the same
     * table every other site pays through, with this pass's {@link GrantResult} layered onto the
     * subject as a facet: the three station kinds find it there by its {@link StationRewardKinds.Sink}
     * type, and every other kind reads the subject exactly as it would anywhere else.
     *
     * <p><b>A {@code Lootable} reward rolled here pays into this pass too.</b> The table it rolls is
     * paid through the same subject, so its station kinds collect onto this result like the outer
     * table's do. Its rolls answer to no trigger of the station's (the shared {@code Lootable} kind
     * hands its nested pass none unless the reward authors one), so a {@code Completion} roll in a
     * table nested under a {@code Cycle} roll fires on that cycle. What a completion pass can carry
     * is still this result's call: a cycle-scoped kind nested under a completion pass drops quietly
     * there, exactly as it would at the top. (Ziggfreed Common's own tests pin that the nested pass
     * forwards this very subject; a station-side test cannot, since a {@code Lootable} reward needs a
     * live player.)
     */
    @Nonnull
    static GrantResult rollAndGrant(@Nonnull LootEngine.Resolved resolved, @Nonnull String trigger,
            @Nonnull FactorLookup lookup, @Nonnull DoubleSupplier chanceSample,
            @Nullable LootEngine.ItemSink items, @Nullable LootEngine.DropListSink dropLists,
            @Nonnull Subject subject, @Nullable Map<String, String> placeholders,
            @Nonnull String sourceId) {
        GrantResult result = new GrantResult(TRIGGER_CYCLE.equalsIgnoreCase(trigger));
        LootEngine.Sinks.Builder builder = LootEngine.Sinks.builder()
                .items(items)
                .dropLists(dropLists)
                .rewards(RewardKinds.shared(), subject.withFacets(result))
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

    /**
     * Fold one shared-engine pass into the station tally, so two calls read as one pass. The
     * merged item map is kept whole, and the shared engine's per-roll origin rides beside it: what
     * an {@code Expected} roll paid and what a find paid, so the caller can show the one as
     * ordinary output and the other as a windfall.
     */
    static void absorb(@Nonnull GrantResult result, @Nonnull LootEngine.Result shared) {
        for (Map.Entry<String, Integer> entry : shared.getItems().entrySet()) {
            result.dropListItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : shared.getExpectedItems().entrySet()) {
            result.expectedItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : shared.getFoundItems().entrySet()) {
            result.foundItems.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        result.cues.addAll(shared.getCues());
        result.commandsRun += shared.getCommandsRun();
    }

    /**
     * The subject a registered reward kind is paid to: the PLAYER on the handle, which is where the
     * shared {@code Item}/{@code Lootable}/{@code Stamped_Item} kinds look for one, with the
     * {@link PlayerRef} layered beside it, which is what a {@code Command} reward authored
     * {@code RunAs: Player} asks for (without it that reward always failed at a station; now it runs
     * with the player's own authority). The seam core layers the pass's own collector on top, so the
     * three station kinds find theirs the same way, without any kind having to know about another.
     *
     * <p>It is never null, even when the session has no resolvable {@code PlayerRef}. A null subject
     * would switch the WHOLE reward leaf off, and the three station kinds collect onto the pass
     * rather than paying anything to anybody - so an unidentifiable worker would silently lose their
     * contributions and their extra output alongside the item rewards that genuinely cannot land.
     * An anonymous subject (the 0-0 id, an empty name) lets the collecting kinds run and leaves the
     * paying ones to fail on their own terms, which is the outcome each of them is written for.
     */
    @Nonnull
    static Subject subjectOf(@Nullable Player player, @Nullable PlayerRef playerRef) {
        UUID uuid = playerRef != null ? playerRef.getUuid() : null;
        String username = playerRef != null ? playerRef.getUsername() : null;
        return new Subject(uuid != null ? uuid : ANONYMOUS_WORKER,
                username != null ? username : "", player).withFacets(playerRef);
    }

    /**
     * The station {@code Items} and {@code DropLists} sinks, both the shared ground-spill preset: a
     * stack goes hotbar-first, then backpack storage, and whatever fits nowhere lands as ONE pile at
     * the station block through {@link ItemDropUtil}, the station's one ground drop. A drop list is
     * rolled once through the engine's native roll and handed over the same way, so a roll that
     * overflows leaves one pile that equals the remainder rather than a find scattered across
     * several ground entities.
     *
     * <p>Both answer what actually LANDED (the inventory, or a pile the drop answered as landed): a
     * stack that went nowhere no longer exists, so it is never reported as found. An empty answer
     * covers every way a table can pay nothing - it rolled its own empty branch, the roll itself
     * failed, or nothing could be handed over - and the shared engine reads that as "produced
     * nothing", which is what keeps a celebration cue silent over an empty hand.
     */
    @Nonnull
    private static GroundSpillSinks stationSinks(@Nonnull Player player,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nullable Store<EntityStore> store,
            int blockX, int blockY, int blockZ) {
        return GroundSpillSinks.at(stacks -> ItemDropUtil.dropAtBlock(commandBuffer, store, blockX, blockY, blockZ, stacks))
                .inventoryFirst(player)
                .warn(message -> Log.fine("STATION loot " + message))
                .build();
    }
}
