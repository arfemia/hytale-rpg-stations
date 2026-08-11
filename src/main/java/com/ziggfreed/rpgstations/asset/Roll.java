package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.ziggfreed.common.factor.FactorCondition;

/**
 * ONE conditional-lootable roll: gate ({@code Conditions}/{@code Chance}) + payoff
 * ({@code Ladder}/{@code Grants}) plus an optional celebration ({@code Presentation}), shared by
 * {@link LootRef#getRolls()} (inline, wherever a {@link LootRef} is authored) and
 * {@link LootableAsset#getRolls()} (a referenced table).
 *
 * <p><b>Concern boundary:</b> {@code Yield} decides how much of the thing you MADE - deterministic,
 * four leaves, nothing hidden. A Roll decides what ELSE the cycle handed over: drop lists, commands,
 * native effects, contribution posts, and {@code Grants.OutputItems} (ADDITIVE items of that same
 * primary output, fractional so a half-step tier is authorable). Additive is the load-bearing word:
 * the two numbers are directly comparable, so a Roll can never silently MULTIPLY a quantity authored
 * in another file.
 *
 * <p><b>Weighted-factor unification:</b>
 * <ul>
 *   <li>{@link Chance#getFactors()} entries are {@link FactorRef}s ({@code {Factor, Param?,
 *   Weight?}}), so a chance can weight several factor channels: {@code effective =
 *   clamp(BasePercent + sum(resolve(f.Factor, f.Param) * f.Weight), 0, CapPercent)}.
 *   <li>{@link Ladder#getFactors()} is a {@link FactorRef}{@code []} summed BEFORE the floor lookup,
 *   so one ladder composes several channels (a third-party aggregate plus a native stat channel,
 *   each weighted). A single-factor ladder authors a one-element array. Every ladder consumer in
 *   this schema ({@code Ladder} here and an action's {@code ContributionScale}) resolves through the
 *   ONE shared {@code loot.FactorLadder} core, so identical JSON behaves identically at either site.
 * </ul>
 *
 * <pre>{@code
 * {
 *   "Trigger": "Cycle",
 *   "Conditions": [ { "Factor": "rpgstations:cycle_count", "Min": 3 } ],
 *   "Chance":    { "BasePercent": 0, "Factors": [ { "Factor": "yourmod:fortune" } ],
 *                  "CapPercent": 90 },
 *   "Ladder":    { "Factors": [ { "Factor": "yourmod:fortune" },
 *                               { "Factor": "hytale:stat", "Param": "<EntityStatType id>", "Weight": 0.5 } ],
 *                  "Floors": [ { "Min": 50,  "Grants": { "DropLists": ["SawmillFinds_T1"] } },
 *                              { "Min": 100, "Grants": { "DropLists": ["SawmillFinds_T2"] },
 *                                "Presentation": { "Sounds": ["SFX_Coins_Land"] } } ] },
 *   "Grants":    { "DropLists": [ "..." ], "Commands": [ "give {player} ..." ] },
 *   "Presentation": { "Sounds": ["SFX_Chest_Legendary_FirstOpen_Player"] }
 * }
 * }</pre>
 *
 * <p>Binding evaluation rules: a {@link Ladder.Floor} has NO direct drop-list leaf (every floor
 * reward routes through its OWN {@link Ladder.Floor#getGrants()}); top-level {@link #getGrants()}
 * AND the reached floor's grants BOTH apply; a present, FAILING {@link Chance} means nothing fires
 * (the {@link Ladder} is never evaluated).
 *
 * <p><b>The smart-cue rule</b> (binding at BOTH presentation altitudes - {@link #getPresentation()}
 * here and {@link Ladder.Floor#getPresentation()}): a presentation never celebrates over nothing.
 * Each one is paired with its OWN grants group - the roll's top-level {@link #getGrants()} for the
 * roll-level cue, the floor's own {@link Ladder.Floor#getGrants()} for a floor cue. With NO grants
 * authored beside it, the presentation is a pure cue and plays on the hit (or on the floor being
 * reached), exactly as written. With grants authored, it plays only when applying them actually
 * PRODUCED something: an item that a referenced drop table's own internal weights really handed
 * over, a command run, an effect applied, an {@code OutputItems} amount tallied, or a contribution
 * posted. A drop table whose internal weights resolve to nothing therefore stays silent instead of
 * firing a jackpot fanfare over an empty hand.
 */
public final class Roll {

    /** {@link #getTrigger()} default and the ONLY two recognized values (case-insensitive at read). */
    public static final String TRIGGER_CYCLE = "Cycle";
    public static final String TRIGGER_COMPLETION = "Completion";

    @Nullable protected String trigger;
    @Nullable protected FactorCondition[] conditions;
    @Nullable protected Chance chance;
    @Nullable protected Ladder ladder;
    @Nullable protected Grants grants;
    @Nullable protected Presentation presentation;

    public static final BuilderCodec<Roll> CODEC = BuilderCodec.builder(Roll.class, Roll::new)
            .appendInherited(new KeyedCodec<>("Trigger", Codec.STRING, false),
                    (o, v) -> o.trigger = v, o -> o.trigger, (o, p) -> o.trigger = p.trigger)
            .documentation("When this roll fires: 'Cycle' (per completed cycle, the default) or 'Completion' (at session stop).")
            .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:roll-trigger"))).add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Conditions.CODEC, FactorCondition[]::new), false),
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
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation)
            .documentation("Played at the station block on the rare-find moment when this roll HITS (its Conditions and "
                    + "Chance both passed), so a plain chance roll can carry its own celebration without a one-floor "
                    + "Ladder standing in for it. Smart cue: with no top-level Grants authored beside it this is a pure "
                    + "cue and always plays on the hit; with Grants authored it plays only when applying them actually "
                    + "produced something (an item a referenced drop table's own internal weights really handed over, a "
                    + "command run, an effect applied, an OutputItems amount tallied, or a contribution posted), so a "
                    + "table that resolves to nothing never fires a fanfare over an empty hand. A reached Ladder floor's "
                    + "own Presentation is judged the same way against that floor's own Grants, and both can play.").add()
            .build();

    public Roll() {
    }

    @Nonnull
    public static Roll of(@Nullable String trigger, @Nullable FactorCondition[] conditions, @Nullable Chance chance,
            @Nullable Ladder ladder, @Nullable Grants grants) {
        return of(trigger, conditions, chance, ladder, grants, null);
    }

    /** As above, plus the roll-level celebration cue (see the smart-cue rule on this class). */
    @Nonnull
    public static Roll of(@Nullable String trigger, @Nullable FactorCondition[] conditions, @Nullable Chance chance,
            @Nullable Ladder ladder, @Nullable Grants grants, @Nullable Presentation presentation) {
        Roll r = new Roll();
        r.trigger = trigger;
        r.conditions = conditions;
        r.chance = chance;
        r.ladder = ladder;
        r.grants = grants;
        r.presentation = presentation;
        return r;
    }

    @Nullable
    public String getTrigger() {
        return trigger;
    }

    @Nullable
    public FactorCondition[] getConditions() {
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

    /**
     * The roll-level celebration, played on the rare-find moment when this roll HITS - the plain-
     * chance sibling of a {@link Ladder.Floor#getPresentation()}, so a roll with no tiers to climb
     * needs no degenerate one-floor ladder to carry a cue. Judged by the smart-cue rule on this
     * class against this roll's OWN {@link #getGrants()}: a pure cue (no grants beside it) plays on
     * every hit, and a granting one plays only when the grant actually produced something.
     */
    @Nullable
    public Presentation getPresentation() {
        return presentation;
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
     * The probabilistic gate (it gates the WHOLE Roll, Ladder included). {@code effective =
     * clamp(BasePercent + sum(resolve(f) * f.Weight for f in Factors), 0, CapPercent)}, all in
     * PERCENT units (0..100), rolled ONCE per trigger against a {@code [0,100)} uniform sample.
     *
     * <p>This is the ONE chance vocabulary in the schema: every probabilistic gate an author writes
     * is this exact type, wherever it appears.
     */
    public static final class Chance {
        @Nullable protected Double basePercent;
        @Nullable protected FactorRef[] factors;
        @Nullable protected Double capPercent;

        public static final BuilderCodec<Chance> CODEC = BuilderCodec.builder(Chance.class, Chance::new)
                .appendInherited(new KeyedCodec<>("BasePercent", Codec.DOUBLE, false),
                        (o, v) -> o.basePercent = v, o -> o.basePercent, (o, p) -> o.basePercent = p.basePercent)
                .documentation("The flat base chance in percent (0..100) before any factor contributions.")
                .addValidator(CodecWarnValidators.nonNegative("Roll.Chance.BasePercent should not be negative.")).add()
                .appendInherited(new KeyedCodec<>("Factors", new ArrayCodec<>(FactorRef.CODEC, FactorRef[]::new), false),
                        (o, v) -> o.factors = v, o -> o.factors, (o, p) -> o.factors = p.factors)
                .documentation("Weighted factor references summed onto BasePercent: sum(resolve(Factor, Param) * Weight).").add()
                .appendInherited(new KeyedCodec<>("CapPercent", Codec.DOUBLE, false),
                        (o, v) -> o.capPercent = v, o -> o.capPercent, (o, p) -> o.capPercent = p.capPercent)
                .documentation("The maximum effective chance in percent; the summed chance clamps to [0, CapPercent]. Absent = 100.")
                .addValidator(CodecWarnValidators.positive("Roll.Chance.CapPercent should be positive; a zero/negative cap means the roll never fires.")).add()
                .build();

        @Nonnull
        public static Chance of(@Nullable Double basePercent, @Nullable FactorRef[] factors,
                @Nullable Double capPercent) {
            Chance c = new Chance();
            c.basePercent = basePercent;
            c.factors = factors;
            c.capPercent = capPercent;
            return c;
        }

        @Nullable
        public Double getBasePercent() {
            return basePercent;
        }

        /** Weighted {@link FactorRef} entries, each resolved and summed onto {@link #basePercent}. */
        @Nullable
        public FactorRef[] getFactors() {
            return factors;
        }

        @Nullable
        public Double getCapPercent() {
            return capPercent;
        }
    }

    /**
     * A floor ladder over an UNCAPPED, SUMMED factor value (deliberately uncapped - a floor above
     * a factor's "normal" range stays reachable via a multi-source stack); the HIGHEST reached
     * floor wins. {@link #factors} is a {@link FactorRef}{@code []} summed before the floor lookup.
     *
     * <p><b>One ladder core, one set of semantics</b> ({@code loot.FactorLadder}, shared with an
     * action's {@code ContributionScale}): absent/empty {@link #factors} resolves the ladder value to
     * {@code 0.0} (so a constant-reward floor is authorable); a floor's {@code Min} reader-defaults
     * to {@code 0} and a {@code Min <= 0} floor is REACHABLE (an author writing a baseline tier gets
     * one); and on two floors sharing a {@code Min}, the LAST authored one wins, matching every other
     * later-wins rule in this schema. The validator warns about the duplicate rather than the
     * evaluator silently picking.
     */
    public static final class Ladder {
        @Nullable protected FactorRef[] factors;
        @Nullable protected Floor[] floors;

        public static final BuilderCodec<Ladder> CODEC = BuilderCodec.builder(Ladder.class, Ladder::new)
                .appendInherited(new KeyedCodec<>("Factors", new ArrayCodec<>(FactorRef.CODEC, FactorRef[]::new), false),
                        (o, v) -> o.factors = v, o -> o.factors, (o, p) -> o.factors = p.factors)
                .documentation("Weighted factor references SUMMED to the ladder value before the floor lookup; a single-factor ladder is a one-element array, and an empty one resolves to 0.").add()
                .appendInherited(new KeyedCodec<>("Floors", new ArrayCodec<>(Floor.CODEC, Floor[]::new), false),
                        (o, v) -> o.floors = v, o -> o.floors, (o, p) -> o.floors = p.floors)
                .documentation("The reward floors; the HIGHEST floor whose Min <= the summed value grants.").add()
                .build();

        @Nonnull
        public static Ladder of(@Nullable FactorRef[] factors, @Nullable Floor[] floors) {
            Ladder l = new Ladder();
            l.factors = factors;
            l.floors = floors;
            return l;
        }

        /** The weighted factor references summed to the ladder value; null/empty resolves to 0. */
        @Nullable
        public FactorRef[] getFactors() {
            return factors;
        }

        @Nullable
        public Floor[] getFloors() {
            return floors;
        }

        /** One {@code {Min, Grants, Presentation}} floor; a floor has no direct drop-list leaf. */
        public static final class Floor {
            @Nullable protected Double min;
            @Nullable protected Grants grants;
            @Nullable protected Presentation presentation;

            public static final BuilderCodec<Floor> CODEC = BuilderCodec.builder(Floor.class, Floor::new)
                    .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                            (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
                    .documentation("The summed-value threshold this floor requires (inclusive); reader-defaults to 0, and a 0 threshold is reachable (the baseline tier).").add()
                    .appendInherited(new KeyedCodec<>("Grants", Grants.CODEC, false),
                            (o, v) -> o.grants = v, o -> o.grants, (o, p) -> o.grants = p.grants)
                    .documentation("This floor's ONLY reward path; there is deliberately no sibling drop-list leaf.").add()
                    .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                            (o, v) -> o.presentation = v, o -> o.presentation,
                            (o, p) -> o.presentation = p.presentation)
                    .documentation("Played at the station block on the rare-find moment when this floor is REACHED. Smart "
                            + "cue: with no Grants authored on this floor it is a pure cue and always plays on the reach; "
                            + "with Grants authored it plays only when applying them actually produced something (an item a "
                            + "referenced drop table's own internal weights really handed over, a command run, an effect "
                            + "applied, an OutputItems amount tallied, or a contribution posted), so a table that resolves "
                            + "to nothing never fires a fanfare over an empty hand. The roll's own top-level Presentation "
                            + "is judged the same way against the roll's own Grants, and both can play.").add()
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

            /** Reader-defaulted threshold (0 when absent or non-finite) - the shared ladder rule. */
            public double effectiveMin() {
                return min != null && Double.isFinite(min) ? min : 0.0;
            }

            @Nullable
            public Grants getGrants() {
                return grants;
            }

            /**
             * This floor's own celebration, played on the rare-find moment when the floor is
             * reached. Judged by the smart-cue rule on {@link Roll} against THIS floor's own
             * {@link #getGrants()}, independently of the roll-level cue: a pure cue always plays on
             * the reach, and a granting one plays only when the grant actually produced something.
             */
            @Nullable
            public Presentation getPresentation() {
                return presentation;
            }
        }
    }

    /**
     * The ONE reward vocabulary a Roll (top-level) or a {@link Ladder.Floor} (per-floor) grants
     * through - orthogonal nullable leaves, independently composable, every collection a plural
     * array.
     */
    public static final class Grants {
        @Nullable protected String[] dropLists;
        @Nullable protected String[] commands;
        @Nullable protected EffectRef[] effects;
        @Nullable protected Contribution[] contributions;
        @Nullable protected Double outputItems;

        public static final BuilderCodec<Grants> CODEC = BuilderCodec.builder(Grants.class, Grants::new)
                .appendInherited(new KeyedCodec<>("OutputItems", Codec.DOUBLE, false),
                        (o, v) -> o.outputItems = v, o -> o.outputItems, (o, p) -> o.outputItems = p.outputItems)
                .documentation("ADDITIVE items of the cycle's own primary output, handed over on top of the deterministic Yield "
                        + "quantity. FRACTIONAL: the whole part is granted every time and the fraction left over is the chance "
                        + "of ONE more, so 1.5 pays one item always plus a second half the time and averages exactly 1.5 per "
                        + "cycle. That makes a half-step tier authorable directly on the ladder floor that earns it, instead of "
                        + "needing a separate banded roll beside it. Everything a single cycle grants is summed FIRST and "
                        + "resolved once, so two rolls paying 0.5 each average one whole item rather than rounding twice. "
                        + "Additive, never a multiplier on the produced stack, so this number and the Yield number "
                        + "stay directly comparable even though they are authored in different groups. Cycle trigger ONLY (a "
                        + "Completion-trigger roll has no cycle output to add to; the validator warns and the engine drops it).")
                .addValidator(CodecWarnValidators.positive("Roll.Grants.OutputItems should be positive; a zero/negative amount grants nothing.")).add()
                .appendInherited(new KeyedCodec<>("DropLists",
                                new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.dropLists = v, o -> o.dropLists, (o, p) -> o.dropLists = p.dropLists)
                .documentation("Native ItemDropList asset ids, each rolled independently in authored order via ItemModule.getRandomItemDrops (a guaranteed common table plus a rare one is two entries).").add()
                .appendInherited(new KeyedCodec<>("Commands", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.commands = v, o -> o.commands, (o, p) -> o.commands = p.commands)
                .documentation("Commands run with {player}/{uuid}/{station}/{action}/{cycles} placeholders substituted.").add()
                .appendInherited(new KeyedCodec<>("Effects", new ArrayCodec<>(EffectRef.CODEC, EffectRef[]::new), false),
                        (o, v) -> o.effects = v, o -> o.effects, (o, p) -> o.effects = p.effects)
                .documentation("Native EntityEffects (id-ref-only, each with an optional DurationMs) applied to the player "
                        + "when the roll grants. Teardown differs by Trigger, deliberately: a Cycle-trigger "
                        + "roll's effect is session-tracked and torn down when the session stops; a Completion-trigger "
                        + "roll's effect applies from INSIDE that same stop (after its teardown already ran) and "
                        + "deliberately PERSISTS for its own authored/asset duration as a finishing reward, never "
                        + "stripped by this session.").add()
                .appendInherited(new KeyedCodec<>("Contributions",
                                new ArrayCodec<>(Contribution.CODEC, Contribution[]::new), false),
                        (o, v) -> o.contributions = v, o -> o.contributions, (o, p) -> o.contributions = p.contributions)
                .documentation("One-shot amounts posted verbatim when this roll grants. Cycle trigger ONLY (a "
                        + "Completion-trigger roll has no cycle event to ride on; the validator warns) and "
                        + "DELIBERATELY UNSCALED: unlike the station's own Work.PerCycleContributions, a find's grant "
                        + "never inherits the idle fraction.").add()
                .build();

        @Nonnull
        public static Grants of(@Nullable String[] dropLists, @Nullable String[] commands) {
            return of(dropLists, commands, null);
        }

        @Nonnull
        public static Grants of(@Nullable String[] dropLists, @Nullable String[] commands,
                @Nullable EffectRef[] effects) {
            return of(dropLists, commands, effects, null);
        }

        @Nonnull
        public static Grants of(@Nullable String[] dropLists, @Nullable String[] commands,
                @Nullable EffectRef[] effects, @Nullable Contribution[] contributions) {
            Grants g = new Grants();
            g.dropLists = dropLists;
            g.commands = commands;
            g.effects = effects;
            g.contributions = contributions;
            return g;
        }

        /** Convenience for the additive-output authoring shape (fixtures / Java-side construction). */
        @Nonnull
        public static Grants ofOutputItems(@Nullable Double outputItems) {
            Grants g = new Grants();
            g.outputItems = outputItems;
            return g;
        }

        /** Convenience for the single-table authoring shape (fixtures / Java-side construction). */
        @Nonnull
        public static Grants ofDropList(@Nullable String dropList) {
            return of(dropList == null ? null : new String[] {dropList}, null);
        }

        /** Native {@code ItemDropList} ids, rolled independently in authored order; null/empty = none. */
        @Nullable
        public String[] getDropLists() {
            return dropLists;
        }

        @Nullable
        public String[] getCommands() {
            return commands;
        }

        /** Native EntityEffects (id-ref-only) applied when the roll grants; null = none. */
        @Nullable
        public EffectRef[] getEffects() {
            return effects;
        }

        /**
         * One-shot {@link Contribution} posts; null = none. Meaningful only under a {@code Cycle}
         * trigger (the validator warns otherwise) and deliberately UNSCALED - see
         * {@link Contribution}'s own javadoc for the site-fixed scaling contract.
         */
        @Nullable
        public Contribution[] getContributions() {
            return contributions;
        }

        /**
         * Extra items of the CYCLE's own primary output, granted additively on top of the
         * deterministic {@code Yield} quantity; null/non-positive = none. FRACTIONAL: the whole part
         * is granted every time and the fraction left over is the chance of one more, so {@code 1.5}
         * pays one item always plus a second half the time. Meaningful only under a {@code Cycle}
         * trigger.
         */
        @Nullable
        public Double getOutputItems() {
            return outputItems;
        }

        /**
         * Reader-defaulted {@link #getOutputItems()} (0 when absent, non-positive, or non-finite).
         * A TALLY, not a granted count: every value a cycle collects is summed and the whole
         * fractional total is resolved to items ONCE, by {@code loot.OutputItemResolver}.
         */
        public double effectiveOutputItems() {
            return outputItems != null && Double.isFinite(outputItems) && outputItems > 0.0 ? outputItems : 0.0;
        }

        /** True when no leaf is authored (an empty group is a no-op, same as an absent one). */
        public boolean isEmpty() {
            return (dropLists == null || dropLists.length == 0)
                    && (commands == null || commands.length == 0)
                    && (effects == null || effects.length == 0)
                    && (contributions == null || contributions.length == 0)
                    && effectiveOutputItems() <= 0.0;
        }
    }
}
