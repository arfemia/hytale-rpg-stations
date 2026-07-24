package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * ONE conditional-lootable roll: gate ({@code Conditions}/{@code Chance}) + payoff
 * ({@code Ladder}/{@code Grants}), shared by {@link LootRef#getRolls()} (inline, wherever a
 * {@link LootRef} is authored) and {@link LootableAsset#getRolls()} (a referenced table) - design
 * section 4.5.1, TIGHTENED per the adversarial critique's binding M3 fix (all five items below are
 * load-bearing).
 *
 * <p><b>Scope-2 changes (weighted-factor unification, design 4.2):</b>
 * <ul>
 *   <li>{@link Chance#getAddFactors()} entries are now {@link FactorRef}s ({@code {Factor, Param?,
 *   Weight?}}), so a chance can weight several factor channels: {@code effective =
 *   clamp(BasePercent + sum(resolve(f.Factor, f.Param) * f.Weight), 0, CapPercent)}.
 *   <li>{@link Ladder#getValues()} (JSON key {@code Values}) REPLACES the old single
 *   {@code Ladder.Value}: a {@link FactorRef}{@code []} summed BEFORE the floor lookup, so a ladder
 *   composes {@code stat}-channel luck (e.g. {@code MMO_Luck} + {@code MMO_Luck_WOODCUTTING})
 *   exactly the way the loot middle path requires. A single-factor ladder authors a one-element
 *   array.
 * </ul>
 *
 * <pre>{@code
 * {
 *   "Trigger": "Cycle",
 *   "Conditions": [ { "Factor": "rpgstations:cycle_count", "Min": 3 } ],
 *   "Chance":    { "BasePercent": 0, "AddFactors": [ { "Factor": "stat", "Param": "MMO_Luck" } ],
 *                  "CapPercent": 90 },
 *   "Ladder":    { "Values": [ { "Factor": "stat", "Param": "MMO_Luck" },
 *                              { "Factor": "stat", "Param": "MMO_Luck_WOODCUTTING" } ],
 *                  "Floors": [ { "Min": 50,  "Grants": { "DropList": "SawmillFinds_T1" } },
 *                              { "Min": 100, "Grants": { "DropList": "SawmillFinds_T2" },
 *                                "Presentation": { "Sound": "SFX_Coins_Land" } } ] },
 *   "Grants":    { "BonusOutputCopies": 1, "DropList": "...", "Commands": [ "give {player} ..." ] }
 * }
 * }</pre>
 *
 * <p><b>M3 fix 2</b> - a {@link Ladder.Floor} has NO direct {@code DropList} leaf; every floor
 * reward routes through its OWN {@link Ladder.Floor#getGrants()}. <b>M3 fix 3</b> - top-level
 * {@link #getGrants()} AND the reached floor's grants BOTH apply. <b>M3 fix 4</b> - a present,
 * FAILING {@link Chance} means nothing fires (the {@link Ladder} is never evaluated). <b>M3 fix
 * 5</b> - {@link Grants#getBonusOutputCopies()} is meaningless outside a {@code Cycle} trigger
 * (validator warns).
 */
public final class Roll {

    /** {@link #getTrigger()} default and the ONLY two recognized values (case-insensitive at read). */
    public static final String TRIGGER_CYCLE = "Cycle";
    public static final String TRIGGER_COMPLETION = "Completion";

    @Nullable protected String trigger;
    @Nullable protected Condition[] conditions;
    @Nullable protected Chance chance;
    @Nullable protected Ladder ladder;
    @Nullable protected Grants grants;

    public static final BuilderCodec<Roll> CODEC = BuilderCodec.builder(Roll.class, Roll::new)
            .appendInherited(new KeyedCodec<>("Trigger", Codec.STRING, false),
                    (o, v) -> o.trigger = v, o -> o.trigger, (o, p) -> o.trigger = p.trigger)
            .documentation("When this roll fires: 'Cycle' (per completed cycle, the default) or 'Completion' (at session stop).").add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Condition.CODEC, Condition[]::new), false),
                    (o, v) -> o.conditions = v, o -> o.conditions, (o, p) -> o.conditions = p.conditions)
            .documentation("Gate: every Condition must pass (bounded factor checks) before the roll is considered.").add()
            .appendInherited(new KeyedCodec<>("Chance", Chance.CODEC, false),
                    (o, v) -> o.chance = v, o -> o.chance, (o, p) -> o.chance = p.chance)
            .documentation("Probabilistic gate over the WHOLE roll (Ladder included); absent = a deterministic pass.").add()
            .appendInherited(new KeyedCodec<>("Ladder", Ladder.CODEC, false),
                    (o, v) -> o.ladder = v, o -> o.ladder, (o, p) -> o.ladder = p.ladder)
            .documentation("A floor ladder over a summed factor value; the highest reached floor's Grants fire.").add()
            .appendInherited(new KeyedCodec<>("Grants", Grants.CODEC, false),
                    (o, v) -> o.grants = v, o -> o.grants, (o, p) -> o.grants = p.grants)
            .documentation("Top-level rewards granted when the roll fires (in addition to any reached Ladder floor's Grants).").add()
            .build();

    public Roll() {
    }

    @Nonnull
    public static Roll of(@Nullable String trigger, @Nullable Condition[] conditions, @Nullable Chance chance,
            @Nullable Ladder ladder, @Nullable Grants grants) {
        Roll r = new Roll();
        r.trigger = trigger;
        r.conditions = conditions;
        r.chance = chance;
        r.ladder = ladder;
        r.grants = grants;
        return r;
    }

    @Nullable
    public String getTrigger() {
        return trigger;
    }

    @Nullable
    public Condition[] getConditions() {
        return conditions;
    }

    @Nullable
    public Chance getChance() {
        return chance;
    }

    @Nullable
    public Ladder getLadder() {
        return ladder;
    }

    @Nullable
    public Grants getGrants() {
        return grants;
    }

    /** {@link #trigger}, reader-defaulted to {@link #TRIGGER_CYCLE} when null/blank/unrecognized. */
    @Nonnull
    public String effectiveTrigger() {
        if (trigger == null || trigger.isBlank()) {
            return TRIGGER_CYCLE;
        }
        if (TRIGGER_COMPLETION.equalsIgnoreCase(trigger)) {
            return TRIGGER_COMPLETION;
        }
        return TRIGGER_CYCLE;
    }

    public boolean isCompletionTrigger() {
        return TRIGGER_COMPLETION.equalsIgnoreCase(effectiveTrigger());
    }

    /**
     * The probabilistic gate (M3 fix 4: gates the WHOLE Roll, Ladder included). {@code effective =
     * clamp(BasePercent + sum(resolve(f) * f.Weight for f in AddFactors), 0, CapPercent)}, all in
     * PERCENT units (0..100), rolled ONCE per trigger against a {@code [0,100)} uniform sample.
     */
    public static final class Chance {
        @Nullable protected Double basePercent;
        @Nullable protected FactorRef[] addFactors;
        @Nullable protected Double capPercent;

        public static final BuilderCodec<Chance> CODEC = BuilderCodec.builder(Chance.class, Chance::new)
                .appendInherited(new KeyedCodec<>("BasePercent", Codec.DOUBLE, false),
                        (o, v) -> o.basePercent = v, o -> o.basePercent, (o, p) -> o.basePercent = p.basePercent)
                .documentation("The flat base chance in percent (0..100) before any factor contributions.").add()
                .appendInherited(new KeyedCodec<>("AddFactors", new ArrayCodec<>(FactorRef.CODEC, FactorRef[]::new), false),
                        (o, v) -> o.addFactors = v, o -> o.addFactors, (o, p) -> o.addFactors = p.addFactors)
                .documentation("Weighted factor references summed onto BasePercent: sum(resolve(Factor, Param) * Weight).").add()
                .appendInherited(new KeyedCodec<>("CapPercent", Codec.DOUBLE, false),
                        (o, v) -> o.capPercent = v, o -> o.capPercent, (o, p) -> o.capPercent = p.capPercent)
                .documentation("The maximum effective chance in percent; the summed chance clamps to [0, CapPercent].").add()
                .build();

        @Nonnull
        public static Chance of(@Nullable Double basePercent, @Nullable FactorRef[] addFactors,
                @Nullable Double capPercent) {
            Chance c = new Chance();
            c.basePercent = basePercent;
            c.addFactors = addFactors;
            c.capPercent = capPercent;
            return c;
        }

        @Nullable
        public Double getBasePercent() {
            return basePercent;
        }

        /** Weighted {@link FactorRef} entries, each resolved and summed (scope-2: an array of FactorRefs). */
        @Nullable
        public FactorRef[] getAddFactors() {
            return addFactors;
        }

        @Nullable
        public Double getCapPercent() {
            return capPercent;
        }
    }

    /**
     * A floor ladder over an UNCAPPED, SUMMED factor value (deliberately uncapped - a floor above
     * a factor's "normal" range stays reachable via a multi-source stack); the HIGHEST reached
     * floor wins. {@link #values} (JSON key {@code Values}) is a {@link FactorRef}{@code []} summed
     * before the floor lookup (scope-2 design, {@code Ladder.Value} -&gt; {@code Ladder.Values[]}).
     */
    public static final class Ladder {
        @Nullable protected FactorRef[] values;
        @Nullable protected Floor[] floors;

        public static final BuilderCodec<Ladder> CODEC = BuilderCodec.builder(Ladder.class, Ladder::new)
                .appendInherited(new KeyedCodec<>("Values", new ArrayCodec<>(FactorRef.CODEC, FactorRef[]::new), false),
                        (o, v) -> o.values = v, o -> o.values, (o, p) -> o.values = p.values)
                .documentation("Weighted factor references SUMMED to the ladder value before the floor lookup; a single-factor ladder is a one-element array.").add()
                .appendInherited(new KeyedCodec<>("Floors", new ArrayCodec<>(Floor.CODEC, Floor[]::new), false),
                        (o, v) -> o.floors = v, o -> o.floors, (o, p) -> o.floors = p.floors)
                .documentation("The reward floors; the HIGHEST floor whose Min <= the summed value grants.").add()
                .build();

        @Nonnull
        public static Ladder of(@Nullable FactorRef[] values, @Nullable Floor[] floors) {
            Ladder l = new Ladder();
            l.values = values;
            l.floors = floors;
            return l;
        }

        /** The weighted factor references summed to the ladder value (scope-2 {@code Values[]}). */
        @Nullable
        public FactorRef[] getValues() {
            return values;
        }

        @Nullable
        public Floor[] getFloors() {
            return floors;
        }

        /** One {@code {Min, Grants, Presentation}} floor (M3 fix 2: no direct {@code DropList}). */
        public static final class Floor {
            @Nullable protected Double min;
            @Nullable protected Grants grants;
            @Nullable protected Presentation presentation;

            public static final BuilderCodec<Floor> CODEC = BuilderCodec.builder(Floor.class, Floor::new)
                    .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                            (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
                    .documentation("The summed-value threshold this floor requires (inclusive).").add()
                    .appendInherited(new KeyedCodec<>("Grants", Grants.CODEC, false),
                            (o, v) -> o.grants = v, o -> o.grants, (o, p) -> o.grants = p.grants)
                    .documentation("This floor's ONLY reward path (no sibling DropList leaf, M3 fix 2).").add()
                    .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                            (o, v) -> o.presentation = v, o -> o.presentation,
                            (o, p) -> o.presentation = p.presentation)
                    .documentation("Played on the rare-find moment when this floor is reached and grants something.").add()
                    .build();

            @Nonnull
            public static Floor of(@Nullable Double min, @Nullable Grants grants, @Nullable Presentation presentation) {
                Floor f = new Floor();
                f.min = min;
                f.grants = grants;
                f.presentation = presentation;
                return f;
            }

            @Nullable
            public Double getMin() {
                return min;
            }

            @Nullable
            public Grants getGrants() {
                return grants;
            }

            @Nullable
            public Presentation getPresentation() {
                return presentation;
            }
        }
    }

    /**
     * The ONE reward vocabulary a Roll (top-level) or a {@link Ladder.Floor} (per-floor) grants
     * through - orthogonal nullable leaves, independently composable.
     */
    public static final class Grants {
        @Nullable protected Integer bonusOutputCopies;
        @Nullable protected String dropList;
        @Nullable protected String[] commands;

        public static final BuilderCodec<Grants> CODEC = BuilderCodec.builder(Grants.class, Grants::new)
                .appendInherited(new KeyedCodec<>("BonusOutputCopies", Codec.INTEGER, false),
                        (o, v) -> o.bonusOutputCopies = v, o -> o.bonusOutputCopies,
                        (o, p) -> o.bonusOutputCopies = p.bonusOutputCopies)
                .documentation("N extra copies of THIS cycle's Output (Cycle trigger only); silently skipped when inventory is full.").add()
                .appendInherited(new KeyedCodec<>("DropList", Codec.STRING, false),
                        (o, v) -> o.dropList = v, o -> o.dropList, (o, p) -> o.dropList = p.dropList)
                .documentation("A native ItemDropList asset id, rolled via ItemModule.getRandomItemDrops.").add()
                .appendInherited(new KeyedCodec<>("Commands", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.commands = v, o -> o.commands, (o, p) -> o.commands = p.commands)
                .documentation("Commands run with {player}/{uuid}/{station}/{action}/{cycles} placeholders substituted.").add()
                .build();

        @Nonnull
        public static Grants of(@Nullable Integer bonusOutputCopies, @Nullable String dropList,
                @Nullable String[] commands) {
            Grants g = new Grants();
            g.bonusOutputCopies = bonusOutputCopies;
            g.dropList = dropList;
            g.commands = commands;
            return g;
        }

        /** N extra copies of THIS cycle's Output (M3 fix 5: meaningless outside a {@code Cycle} trigger; validator warns). */
        @Nullable
        public Integer getBonusOutputCopies() {
            return bonusOutputCopies;
        }

        @Nullable
        public String getDropList() {
            return dropList;
        }

        @Nullable
        public String[] getCommands() {
            return commands;
        }

        /** True when neither leaf is authored (an empty group is a no-op, same as an absent one). */
        public boolean isEmpty() {
            return bonusOutputCopies == null
                    && (dropList == null || dropList.isBlank())
                    && (commands == null || commands.length == 0);
        }
    }
}
