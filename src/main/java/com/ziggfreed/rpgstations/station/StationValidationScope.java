package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.FactorRefView;
import com.ziggfreed.rpgstations.api.RollView;
import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.api.StationView;
import com.ziggfreed.rpgstations.api.ValidationScope;
import com.ziggfreed.rpgstations.api.impl.RpgStationsApiImpl;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * Builds the read-only {@link ValidationScope} the FULL validate pass hands to every registered
 * {@code api.ValidationHook} ({@code StationValidator#runHooks}).
 *
 * <p>The projection is deliberately RICHER than the reference structure alone: a
 * {@link RollView} carries both which ids a roll names AND the formula numbers around them
 * (chance percents, ladder floor thresholds, factor weights, contribution amounts), because a
 * hook that owns a factor family or a channel needs to reason about composition, not just
 * spelling. Everything here is an immutable snapshot taken once per pass, so a hook may iterate
 * or retain it freely and can never reach a live asset through it.
 *
 * <p>Roll {@code site()} labels use the same {@code "Station[sawmill].Loot.Rolls[0]"} shape the
 * engine's own findings speak in, so a hook's message points an author at the exact block to
 * edit. Every walk here is null-tolerant: a malformed entry is skipped, never a throw (the caller
 * additionally guards the whole build).
 */
final class StationValidationScope implements ValidationScope {

    @Nonnull private final List<RollView> rolls;
    @Nonnull private final Collection<StationView> stations;

    private StationValidationScope(@Nonnull List<RollView> rolls, @Nonnull Collection<StationView> stations) {
        this.rolls = List.copyOf(rolls);
        this.stations = List.copyOf(stations);
    }

    /**
     * Snapshots every folded catalog into one scope: each station's own {@code Loot} plus its
     * inline actions' {@code Loot} and step {@code Roll} phases, each standalone
     * {@link ActionAsset}'s body, each {@link LootableAsset}'s table, and each
     * {@link ExtensionAsset}'s appended loot / rolls / inserted steps.
     */
    @Nonnull
    static ValidationScope build(@Nonnull Collection<StationAsset> stationAssets,
            @Nonnull Collection<ActionAsset> actionAssets,
            @Nonnull Collection<LootableAsset> lootables,
            @Nonnull Collection<ExtensionAsset> extensions) {
        List<RollView> out = new ArrayList<>();
        for (StationAsset a : stationAssets) {
            if (a == null || a.getId() == null) {
                continue;
            }
            String owner = a.getId();
            String base = "Station[" + owner + "]";
            collectLootRef(a.getLoot(), owner, base + ".Loot", out);
            collectActions(a.getActions(), owner, base, out);
        }
        for (ActionAsset act : actionAssets) {
            if (act == null || act.getId() == null) {
                continue;
            }
            collectActionDef(act.getBody(), act.getId(), "Action[" + act.getId() + "]", out);
        }
        for (LootableAsset l : lootables) {
            if (l == null || l.getId() == null) {
                continue;
            }
            collectRolls(l.getRolls(), l.getId(), "Lootable[" + l.getId() + "].Rolls", out);
        }
        for (ExtensionAsset ext : extensions) {
            if (ext == null || ext.getId() == null) {
                continue;
            }
            String owner = ext.getId();
            String base = "Extension[" + owner + "]";
            collectLootRef(ext.getLoot(), owner, base + ".Loot", out);
            collectRolls(ext.getRolls(), owner, base + ".Rolls", out);
            collectActions(ext.getActions(), owner, base, out);
            ExtensionAsset.StepInsertion[] insertions = ext.getSteps();
            if (insertions != null) {
                for (int i = 0; i < insertions.length; i++) {
                    ExtensionAsset.StepInsertion insertion = insertions[i];
                    if (insertion != null) {
                        collectSteps(insertion.getInsert(), owner, base + ".Steps[" + i + "].Insert", out);
                    }
                }
            }
        }
        return new StationValidationScope(out, RpgStationsApiImpl.getInstance().stations());
    }

    private static void collectActions(@Nullable Map<String, ActionDef> actions, @Nonnull String owner,
            @Nonnull String base, @Nonnull List<RollView> out) {
        if (actions == null) {
            return;
        }
        for (Map.Entry<String, ActionDef> e : actions.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                collectActionDef(e.getValue(), owner, base + ".Actions[" + e.getKey() + "]", out);
            }
        }
    }

    private static void collectActionDef(@Nullable ActionDef def, @Nonnull String owner, @Nonnull String base,
            @Nonnull List<RollView> out) {
        if (def == null) {
            return;
        }
        collectLootRef(def.getLoot(), owner, base + ".Loot", out);
        collectSteps(def.getSteps(), owner, base + ".Steps", out);
    }

    private static void collectSteps(@Nullable StationStep[] steps, @Nonnull String owner, @Nonnull String base,
            @Nonnull List<RollView> out) {
        if (steps == null) {
            return;
        }
        for (int i = 0; i < steps.length; i++) {
            StationStep step = steps[i];
            if (step != null) {
                collectLootRef(step.getRoll(), owner, base + "[" + i + "].Roll", out);
            }
        }
    }

    private static void collectLootRef(@Nullable LootRef loot, @Nonnull String owner, @Nonnull String base,
            @Nonnull List<RollView> out) {
        if (loot != null) {
            collectRolls(loot.getRolls(), owner, base + ".Rolls", out);
        }
    }

    private static void collectRolls(@Nullable Roll[] rolls, @Nonnull String owner, @Nonnull String base,
            @Nonnull List<RollView> out) {
        if (rolls == null) {
            return;
        }
        for (int i = 0; i < rolls.length; i++) {
            Roll roll = rolls[i];
            if (roll != null) {
                out.add(new RollViewImpl(roll, owner, base + "[" + i + "]"));
            }
        }
    }

    @Override
    @Nonnull
    public Collection<RollView> allRolls() {
        return rolls;
    }

    @Override
    @Nonnull
    public Collection<StationView> stations() {
        return stations;
    }

    /** One {@link Roll} projected into the api's read-only shape; every list is snapshotted at build. */
    private static final class RollViewImpl implements RollView {

        @Nonnull private final String ownerId;
        @Nonnull private final String site;
        @Nonnull private final String trigger;
        @Nonnull private final List<FactorRefView> factors;
        @Nullable private final Double chanceBasePercent;
        @Nullable private final Double chanceCapPercent;
        @Nonnull private final List<FactorRefView> ladderValues;
        @Nonnull private final List<LadderFloorView> ladderFloors;
        @Nonnull private final List<StationContribution> contributions;
        @Nonnull private final Set<String> contributionChannels;

        RollViewImpl(@Nonnull Roll roll, @Nonnull String ownerId, @Nonnull String site) {
            this.ownerId = ownerId;
            this.site = site;
            this.trigger = roll.effectiveTrigger();

            Roll.Chance chance = roll.getChance();
            this.chanceBasePercent = chance != null ? chance.getBasePercent() : null;
            this.chanceCapPercent = chance != null ? chance.getCapPercent() : null;

            Roll.Ladder ladder = roll.getLadder();
            this.ladderValues = viewFactorRefs(ladder != null ? ladder.getValues() : null);

            // The FLATTENED union, in the documented order: Conditions, then Chance.AddFactors,
            // then Ladder.Values - the walk order a double-count lint reads as authoring order.
            List<FactorRefView> flat = new ArrayList<>();
            Condition[] conditions = roll.getConditions();
            if (conditions != null) {
                for (Condition c : conditions) {
                    if (c != null && c.getFactor() != null && !c.getFactor().isBlank()) {
                        // A gate carries no Weight leaf; it reads its factor once, hence 1.0.
                        flat.add(new FactorRefView(c.getFactor(), c.getParam(), 1.0));
                    }
                }
            }
            flat.addAll(viewFactorRefs(chance != null ? chance.getAddFactors() : null));
            flat.addAll(this.ladderValues);
            this.factors = List.copyOf(flat);

            List<StationContribution> top = viewContributions(
                    roll.getGrants() != null ? roll.getGrants().getContributions() : null);
            this.contributions = top;

            List<LadderFloorView> floors = new ArrayList<>();
            Set<String> channels = new LinkedHashSet<>();
            for (StationContribution c : top) {
                channels.add(c.channel().toLowerCase(Locale.ROOT));
            }
            Roll.Ladder.Floor[] authoredFloors = ladder != null ? ladder.getFloors() : null;
            if (authoredFloors != null) {
                for (Roll.Ladder.Floor floor : authoredFloors) {
                    if (floor == null) {
                        continue;
                    }
                    List<StationContribution> floorPosts = viewContributions(
                            floor.getGrants() != null ? floor.getGrants().getContributions() : null);
                    for (StationContribution c : floorPosts) {
                        channels.add(c.channel().toLowerCase(Locale.ROOT));
                    }
                    floors.add(new LadderFloorView(floor.getMin(), floorPosts));
                }
            }
            this.ladderFloors = List.copyOf(floors);
            this.contributionChannels = Set.copyOf(channels);
        }

        @Nonnull
        private static List<FactorRefView> viewFactorRefs(@Nullable FactorRef[] refs) {
            if (refs == null || refs.length == 0) {
                return List.of();
            }
            List<FactorRefView> out = new ArrayList<>(refs.length);
            for (FactorRef f : refs) {
                if (f != null && f.getFactor() != null && !f.getFactor().isBlank()) {
                    out.add(new FactorRefView(f.getFactor(), f.getParam(), f.effectiveWeight()));
                }
            }
            return List.copyOf(out);
        }

        @Nonnull
        private static List<StationContribution> viewContributions(@Nullable Contribution[] posts) {
            if (posts == null || posts.length == 0) {
                return List.of();
            }
            List<StationContribution> out = new ArrayList<>(posts.length);
            for (Contribution c : posts) {
                if (c == null || c.getChannel() == null || c.getChannel().isBlank()) {
                    continue;
                }
                Double amount = c.getAmount();
                out.add(new StationContribution(c.getChannel(), c.getParam(), amount == null ? 0.0 : amount));
            }
            return List.copyOf(out);
        }

        @Override
        @Nonnull
        public String ownerId() {
            return ownerId;
        }

        @Override
        @Nonnull
        public String site() {
            return site;
        }

        @Override
        @Nonnull
        public String trigger() {
            return trigger;
        }

        @Override
        @Nonnull
        public List<FactorRefView> factors() {
            return factors;
        }

        @Override
        @Nullable
        public Double chanceBasePercent() {
            return chanceBasePercent;
        }

        @Override
        @Nullable
        public Double chanceCapPercent() {
            return chanceCapPercent;
        }

        @Override
        @Nonnull
        public List<FactorRefView> ladderValues() {
            return ladderValues;
        }

        @Override
        @Nonnull
        public List<LadderFloorView> ladderFloors() {
            return ladderFloors;
        }

        @Override
        @Nonnull
        public List<StationContribution> contributions() {
            return contributions;
        }

        @Override
        @Nonnull
        public Set<String> contributionChannels() {
            return contributionChannels;
        }
    }
}
