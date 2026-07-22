package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * ONE conditional-lootable roll: gate ({@code Conditions}/{@code Chance}) + payoff
 * ({@code Ladder}/{@code Grants}), shared by {@link StationAsset.Loot#getRolls()} (inline) and
 * {@link LootableAsset#getRolls()} (a referenced table) - design section 4.5.1, TIGHTENED per
 * the adversarial critique's binding M3 fix (all five items below are load-bearing, not
 * cosmetic - a sonnet reader must not "fix" one back to the ambiguous draft shape).
 *
 * <pre>{@code
 * {
 *   "Trigger": "Cycle",
 *   "Conditions": [ { "Factor": "rpgstations:cycle_count", "Min": 3 } ],
 *   "Chance":    { "BasePercent": 0, "AddFactors": [ { "Factor": "mmoskilltree:station_luck" } ],
 *                  "CapPercent": 90 },
 *   "Ladder":    { "Value": { "Factor": "mmoskilltree:station_luck" },
 *                  "Floors": [
 *                    { "Min": 50,  "Grants": { "DropList": "MMO_Station_Sawmill_T1" } },
 *                    { "Min": 100, "Grants": { "DropList": "MMO_Station_Sawmill_T2" },
 *                      "Presentation": { "Sound": "SFX_Coins_Land" } } ] },
 *   "Grants":    { "BonusOutputCopies": 1, "DropList": "...", "Commands": [ "give {player} ..." ] }
 * }
 * }</pre>
 *
 * <p><b>M3 fix 1 - {@link Chance#getAddFactors()} is an ARRAY</b> (plural key {@code AddFactors}),
 * never a single object: {@code effective = clamp(BasePercent + sum(resolve(f) for f in
 * AddFactors), 0, CapPercent)}, rolled ONCE per trigger. Each entry reuses the shared
 * {@link Condition} leaf shape ({@code Factor}/{@code Param}) for its {@code Factor}/{@code Param}
 * ONLY - {@link Condition#getMin()}/{@link Condition#getMax()} are simply unused in this slot
 * (one condition-shaped leaf everywhere a factor is referenced, per the root DRY convention,
 * rather than a second near-identical "factor ref" type). {@link Ladder#getValue()} reuses the
 * same leaf the same way (a SINGLE factor reference, {@code Min}/{@code Max} unused).
 *
 * <p><b>M3 fix 2 - a {@link Ladder.Floor} has NO direct {@code DropList} leaf.</b> Every floor
 * reward routes through the floor's OWN {@link Ladder.Floor#getGrants()} (the same {@link Grants}
 * vocabulary the Roll's top level uses) - one reward vocabulary, never two overlapping droplist
 * paths on one floor.
 *
 * <p><b>M3 fix 3 - {@code Grants} stacking is EXPLICIT: top-level {@link #getGrants()} AND the
 * reached floor's {@link Ladder.Floor#getGrants()} BOTH apply</b> when a Roll carries both a
 * top-level {@code Grants} and a {@code Ladder} that reaches a floor. This is a deliberate design
 * choice (not an oversight) so a single Roll MAY combine "always grant X" with "additionally
 * grant Y at floor Z"; the shipped parity mapping (design 4.5.3) never exercises the combination
 * (it keeps the tier-0 proc and the tier ladder in SEPARATE Rolls), but the engine defines it.
 *
 * <p><b>M3 fix 4 - {@link Chance} gates the WHOLE Roll, including its {@link Ladder}.</b> A
 * {@code Chance} group present and FAILING its roll means NOTHING fires this trigger - no
 * top-level {@code Grants}, no {@code Ladder} floor lookup at all (the Ladder is never even
 * evaluated). An absent {@code Chance} is a deterministic pass (matches "Chance: probabilistic
 * gate... Absent = always").
 *
 * <p><b>M3 fix 5</b> - see {@link Grants#getBonusOutputCopies()}'s javadoc; the validator
 * ({@code StationValidator}) warns when it is authored under a non-{@code Cycle} {@link #getTrigger()}.
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
                    (o, v) -> o.trigger = v, o -> o.trigger, (o, p) -> o.trigger = p.trigger).add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Condition.CODEC, Condition[]::new), false),
                    (o, v) -> o.conditions = v, o -> o.conditions, (o, p) -> o.conditions = p.conditions).add()
            .appendInherited(new KeyedCodec<>("Chance", Chance.CODEC, false),
                    (o, v) -> o.chance = v, o -> o.chance, (o, p) -> o.chance = p.chance).add()
            .appendInherited(new KeyedCodec<>("Ladder", Ladder.CODEC, false),
                    (o, v) -> o.ladder = v, o -> o.ladder, (o, p) -> o.ladder = p.ladder).add()
            .appendInherited(new KeyedCodec<>("Grants", Grants.CODEC, false),
                    (o, v) -> o.grants = v, o -> o.grants, (o, p) -> o.grants = p.grants).add()
            .build();

    public Roll() {
    }

    /** Java-side factory; sets the same fields the codec fills. */
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

    /** The raw authored {@code Trigger}, or {@code null} when omitted (see {@link #effectiveTrigger()}). */
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
     * The probabilistic gate (M3 fix 4: gates the WHOLE Roll, Ladder included). {@code
     * effective = clamp(BasePercent + sum(AddFactors), 0, CapPercent)}, all in PERCENT units
     * (0..100, matching the {@code MAX_DEFENSE_REDUCTION}-style repo convention for a percent
     * scale) - rolled ONCE per trigger against a {@code [0,100)} uniform sample.
     */
    public static final class Chance {
        @Nullable protected Double basePercent;
        @Nullable protected Condition[] addFactors;
        @Nullable protected Double capPercent;

        public static final BuilderCodec<Chance> CODEC = BuilderCodec.builder(Chance.class, Chance::new)
                .appendInherited(new KeyedCodec<>("BasePercent", Codec.DOUBLE, false),
                        (o, v) -> o.basePercent = v, o -> o.basePercent, (o, p) -> o.basePercent = p.basePercent).add()
                .appendInherited(new KeyedCodec<>("AddFactors", new ArrayCodec<>(Condition.CODEC, Condition[]::new), false),
                        (o, v) -> o.addFactors = v, o -> o.addFactors, (o, p) -> o.addFactors = p.addFactors).add()
                .appendInherited(new KeyedCodec<>("CapPercent", Codec.DOUBLE, false),
                        (o, v) -> o.capPercent = v, o -> o.capPercent, (o, p) -> o.capPercent = p.capPercent).add()
                .build();

        @Nonnull
        public static Chance of(@Nullable Double basePercent, @Nullable Condition[] addFactors,
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

        /** Each entry's {@code Factor}/{@code Param} is resolved and SUMMED (M3 fix 1: an array). */
        @Nullable
        public Condition[] getAddFactors() {
            return addFactors;
        }

        @Nullable
        public Double getCapPercent() {
            return capPercent;
        }
    }

    /**
     * A floor ladder over an UNCAPPED factor value (deliberately - a floor above the factor's
     * "normal" range stays reachable, e.g. a multi-source luck stack); the HIGHEST reached floor
     * wins.
     */
    public static final class Ladder {
        @Nullable protected Condition value;
        @Nullable protected Floor[] floors;

        public static final BuilderCodec<Ladder> CODEC = BuilderCodec.builder(Ladder.class, Ladder::new)
                .appendInherited(new KeyedCodec<>("Value", Condition.CODEC, false),
                        (o, v) -> o.value = v, o -> o.value, (o, p) -> o.value = p.value).add()
                .appendInherited(new KeyedCodec<>("Floors", new ArrayCodec<>(Floor.CODEC, Floor[]::new), false),
                        (o, v) -> o.floors = v, o -> o.floors, (o, p) -> o.floors = p.floors).add()
                .build();

        @Nonnull
        public static Ladder of(@Nullable Condition value, @Nullable Floor[] floors) {
            Ladder l = new Ladder();
            l.value = value;
            l.floors = floors;
            return l;
        }

        /** The factor reference resolving the ladder's value ({@code Factor}/{@code Param} only, see M3 fix 1). */
        @Nullable
        public Condition getValue() {
            return value;
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
                            (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min).add()
                    .appendInherited(new KeyedCodec<>("Grants", Grants.CODEC, false),
                            (o, v) -> o.grants = v, o -> o.grants, (o, p) -> o.grants = p.grants).add()
                    .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                            (o, v) -> o.presentation = v, o -> o.presentation,
                            (o, p) -> o.presentation = p.presentation).add()
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

            /** This floor's ONLY reward path (M3 fix 2 - no sibling {@code DropList} leaf). */
            @Nullable
            public Grants getGrants() {
                return grants;
            }

            /** Played on the {@code station.StationFlairs#MOMENT_RARE_FIND} moment id when this floor is reached and grants something. */
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
                        (o, p) -> o.bonusOutputCopies = p.bonusOutputCopies).add()
                .appendInherited(new KeyedCodec<>("DropList", Codec.STRING, false),
                        (o, v) -> o.dropList = v, o -> o.dropList, (o, p) -> o.dropList = p.dropList).add()
                .appendInherited(new KeyedCodec<>("Commands", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.commands = v, o -> o.commands, (o, p) -> o.commands = p.commands).add()
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

        /**
         * N extra copies of THIS CYCLE's Output, room-checked storage-first, silent skip when
         * full. <b>M3 fix 5</b>: meaningless outside a {@code Cycle}-trigger Roll (a {@code
         * Completion} trigger fires at session stop with no live cycle output) - {@code
         * StationValidator} warns (does not error) when this is authored under a non-{@code
         * Cycle} {@link Roll#getTrigger()}; at runtime the engine simply has no cycle output to
         * copy there and skips this leaf silently.
         */
        @Nullable
        public Integer getBonusOutputCopies() {
            return bonusOutputCopies;
        }

        /** A native {@code ItemDropList} asset id, rolled via {@code ItemModule.getRandomItemDrops}. */
        @Nullable
        public String getDropList() {
            return dropList;
        }

        /**
         * Run through the common {@code util.CommandExecutor}, each with placeholders {@code
         * {player}}/{@code {uuid}}/{@code {station}}/{@code {action}}/{@code {cycles}} substituted -
         * the zero-code integration surface (design section 4.5.1).
         */
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
