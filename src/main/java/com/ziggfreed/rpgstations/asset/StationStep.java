package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

/**
 * ONE step of a multi-action station's step PROGRAM (design section 9.3), a Pattern A union
 * mirroring the MMO's {@code AbilityAsset.EffectStep}: a {@link #type} discriminator plus, per
 * type, ONE nested group carrying that step's own fields (orthogonal groups, never a flat
 * prefixed-key soup). Every base field ({@link #id}/{@link #type}/{@link #conditions}/
 * {@link #onConditionFail}/{@link #presentation}/{@link #puppet}) applies to EVERY step type;
 * {@link #id} is unique WITHIN one action's {@code Steps} array (the {@code station.step}
 * engine's resume/jump bookkeeping keys off it - {@code StationValidator} flags a duplicate).
 * {@link #puppet} (round-4 puppet-presentation design, {@link PuppetOverride}) is the ONE base
 * field NOT tied to {@link #type} the same way {@link #consume}/{@link #produce}/etc. are - it is
 * itself already a small, type-independent override group.
 *
 * <p><b>Branch is NOT a step type</b> (design 9.3): a step authors {@link OnConditionFail#getGoto()}
 * to jump, via the reshaped {@code cast.step} kernel's {@code StepSemantics#nextIndex} hook - no
 * dedicated branch node exists in this vocabulary.
 *
 * <p><b>{@code "Stamp"} lands this leg</b> (design 9.5, phase-2 leg E): the anvil's enhance-commit
 * step - see {@link Stamp}'s own javadoc for the compute-then-commit contract (M5's binding fix).
 * <b>{@code "Mount"} stays reserved, unimplemented</b> (a mid-sequence pose swap): the {@link #type}
 * string decodes fine (no dedicated nested group exists for it), but
 * {@code station.step.StationStepRegistry} registers no handler for it, so a program authoring one
 * FAILS at dispatch with a clear log - {@code StationValidator}'s {@code UNIMPLEMENTED_STEP_TYPE}
 * finding catches the authoring mistake ahead of runtime, per the design's binding note.
 */
public final class StationStep {

    /** The seven step types this leg's engine actually EXECUTES (see the class javadoc for the one reserved id). */
    public static final String TYPE_CONSUME = "Consume";
    public static final String TYPE_PRODUCE = "Produce";
    public static final String TYPE_WAIT = "Wait";
    public static final String TYPE_ROLL = "Roll";
    public static final String TYPE_COMMAND = "Command";
    public static final String TYPE_PRESENT = "Present";
    public static final String TYPE_STAMP = "Stamp";
    /** Schema-reserved, unimplemented this leg (see the class javadoc). */
    public static final String TYPE_MOUNT = "Mount";

    @Nullable protected String id;
    @Nullable protected String type;
    @Nullable protected Condition[] conditions;
    @Nullable protected OnConditionFail onConditionFail;
    @Nullable protected Presentation presentation;

    @Nullable protected Consume consume;
    @Nullable protected Produce produce;
    @Nullable protected Wait wait;
    @Nullable protected RollGroup roll;
    @Nullable protected CommandGroup command;
    @Nullable protected Stamp stamp;
    @Nullable protected PuppetOverride puppet;

    public static final BuilderCodec<StationStep> CODEC = BuilderCodec.builder(StationStep.class, StationStep::new)
            .appendInherited(new KeyedCodec<>("Id", Codec.STRING, false),
                    (o, v) -> o.id = v, o -> o.id, (o, p) -> o.id = p.id).add()
            .appendInherited(new KeyedCodec<>("Type", Codec.STRING, false),
                    (o, v) -> o.type = v, o -> o.type, (o, p) -> o.type = p.type).add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Condition.CODEC, Condition[]::new), false),
                    (o, v) -> o.conditions = v, o -> o.conditions, (o, p) -> o.conditions = p.conditions).add()
            .appendInherited(new KeyedCodec<>("OnConditionFail", OnConditionFail.CODEC, false),
                    (o, v) -> o.onConditionFail = v, o -> o.onConditionFail,
                    (o, p) -> o.onConditionFail = p.onConditionFail).add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation).add()
            .appendInherited(new KeyedCodec<>("Consume", Consume.CODEC, false),
                    (o, v) -> o.consume = v, o -> o.consume, (o, p) -> o.consume = p.consume).add()
            .appendInherited(new KeyedCodec<>("Produce", Produce.CODEC, false),
                    (o, v) -> o.produce = v, o -> o.produce, (o, p) -> o.produce = p.produce).add()
            .appendInherited(new KeyedCodec<>("Wait", Wait.CODEC, false),
                    (o, v) -> o.wait = v, o -> o.wait, (o, p) -> o.wait = p.wait).add()
            .appendInherited(new KeyedCodec<>("Roll", RollGroup.CODEC, false),
                    (o, v) -> o.roll = v, o -> o.roll, (o, p) -> o.roll = p.roll).add()
            .appendInherited(new KeyedCodec<>("Command", CommandGroup.CODEC, false),
                    (o, v) -> o.command = v, o -> o.command, (o, p) -> o.command = p.command).add()
            .appendInherited(new KeyedCodec<>("Stamp", Stamp.CODEC, false),
                    (o, v) -> o.stamp = v, o -> o.stamp, (o, p) -> o.stamp = p.stamp).add()
            .appendInherited(new KeyedCodec<>("Puppet", PuppetOverride.CODEC, false),
                    (o, v) -> o.puppet = v, o -> o.puppet, (o, p) -> o.puppet = p.puppet).add()
            .build();

    public StationStep() {
    }

    /** Java-side construction path (a program built procedurally, e.g. {@code ImplicitProgram}). */
    @Nonnull
    public static StationStep of(@Nullable String id, @Nonnull String type) {
        StationStep s = new StationStep();
        s.id = id;
        s.type = type;
        return s;
    }

    @Nullable
    public String getId() {
        return id;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public Condition[] getConditions() {
        return conditions;
    }

    @Nullable
    public OnConditionFail getOnConditionFail() {
        return onConditionFail;
    }

    @Nonnull
    public StationStep withOnConditionFail(@Nullable OnConditionFail v) {
        this.onConditionFail = v;
        return this;
    }

    @Nullable
    public Presentation getPresentation() {
        return presentation;
    }

    @Nullable
    public Consume getConsume() {
        return consume;
    }

    @Nonnull
    public StationStep withConsume(@Nonnull Consume v) {
        this.consume = v;
        return this;
    }

    @Nullable
    public Produce getProduce() {
        return produce;
    }

    @Nonnull
    public StationStep withProduce(@Nonnull Produce v) {
        this.produce = v;
        return this;
    }

    @Nullable
    public Wait getWait() {
        return wait;
    }

    @Nonnull
    public StationStep withWait(@Nonnull Wait v) {
        this.wait = v;
        return this;
    }

    @Nullable
    public RollGroup getRoll() {
        return roll;
    }

    @Nonnull
    public StationStep withRoll(@Nonnull RollGroup v) {
        this.roll = v;
        return this;
    }

    @Nullable
    public CommandGroup getCommand() {
        return command;
    }

    @Nonnull
    public StationStep withCommand(@Nonnull CommandGroup v) {
        this.command = v;
        return this;
    }

    @Nullable
    public Stamp getStamp() {
        return stamp;
    }

    @Nonnull
    public StationStep withStamp(@Nonnull Stamp v) {
        this.stamp = v;
        return this;
    }

    /**
     * The per-step puppet override (round-4 design, section 3.1 - "the new per step / station
     * knob"): a SMALL group, {@code {Clip?, Prop?}}, NOT tied to {@link #type} - it applies to
     * ANY step (a ritual's distinct beats each want their own puppet pose/prop), unlike every
     * other nested group here which is exclusive to its own {@link #type}. Null = the step
     * inherits the resolved action's default clip ({@link StationAsset.Animation}) and prop
     * ({@link Puppet#getProp()}). Meaningless (never played) when the resolved action's own
     * {@link Puppet} is not active - {@code station.StationValidator}'s
     * {@code PUPPET_STEP_OVERRIDE_WITHOUT_PUPPET} flags that authoring mistake.
     */
    @Nullable
    public PuppetOverride getPuppet() {
        return puppet;
    }

    @Nonnull
    public StationStep withPuppet(@Nonnull PuppetOverride v) {
        this.puppet = v;
        return this;
    }

    @Nonnull
    public StationStep withPresentation(@Nullable Presentation v) {
        this.presentation = v;
        return this;
    }

    /** True when {@link #type} is the one design-reserved, not-yet-executable id. */
    public boolean isReservedUnimplemented() {
        return TYPE_MOUNT.equalsIgnoreCase(type);
    }

    /**
     * The branch/skip leaf (design 9.3: "Branch is NOT a step type"): {@link #result} decides
     * what a FAILING {@link #conditions} check does ({@code "Skip"} - treat as a no-op success and
     * continue; {@code "Fail"}, the default when omitted - fail the walk at this step), and
     * {@link #goto_} (JSON key {@code "Goto"}) is an authored step {@code Id} the kernel's
     * {@code nextIndex} hook jumps to on a SUCCESS-continuing result (a station-authored branch),
     * or {@code null} for the classic linear advance.
     */
    public static final class OnConditionFail {
        public static final String RESULT_SKIP = "Skip";
        public static final String RESULT_FAIL = "Fail";

        @Nullable protected String result;
        @Nullable protected String goto_;

        public static final BuilderCodec<OnConditionFail> CODEC =
                BuilderCodec.builder(OnConditionFail.class, OnConditionFail::new)
                        .appendInherited(new KeyedCodec<>("Result", Codec.STRING, false),
                                (o, v) -> o.result = v, o -> o.result, (o, p) -> o.result = p.result).add()
                        .appendInherited(new KeyedCodec<>("Goto", Codec.STRING, false),
                                (o, v) -> o.goto_ = v, o -> o.goto_, (o, p) -> o.goto_ = p.goto_).add()
                        .build();

        @Nonnull
        public static OnConditionFail of(@Nullable String result, @Nullable String goto_) {
            OnConditionFail f = new OnConditionFail();
            f.result = result;
            f.goto_ = goto_;
            return f;
        }

        @Nullable
        public String getResult() {
            return result;
        }

        @Nullable
        public String getGoto() {
            return goto_;
        }

        /** {@link #result}, reader-defaulted to {@link #RESULT_FAIL} when null/blank/unrecognized. */
        @Nonnull
        public String effectiveResult() {
            return RESULT_SKIP.equalsIgnoreCase(result) ? RESULT_SKIP : RESULT_FAIL;
        }
    }

    /**
     * Consume {@link #quantity} of {@link #itemId} or {@link #resourceTypeId} (exactly one route,
     * matching {@code StationAsset.Ingredient}'s convention) {@link #from}. {@code From:
     * "Inventory"} (the default when omitted) and {@code From: "Custody"} (phase-2 leg C,
     * design 9.4 - drains the block's placed-input claim, the sawmill migration's route) are BOTH
     * executable; any other value fails cleanly at dispatch - {@code StationValidator}'s
     * {@code UNIMPLEMENTED_CONSUME_SOURCE} flags an authoring mistake ahead of runtime.
     */
    public static final class Consume {
        public static final String FROM_INVENTORY = "Inventory";
        public static final String FROM_CUSTODY = "Custody";

        @Nullable protected String itemId;
        @Nullable protected String resourceTypeId;
        @Nullable protected Integer quantity;
        @Nullable protected String from;

        public static final BuilderCodec<Consume> CODEC = BuilderCodec.builder(Consume.class, Consume::new)
                .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                        (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId).add()
                .appendInherited(new KeyedCodec<>("ResourceTypeId", Codec.STRING, false),
                        (o, v) -> o.resourceTypeId = v, o -> o.resourceTypeId,
                        (o, p) -> o.resourceTypeId = p.resourceTypeId).add()
                .appendInherited(new KeyedCodec<>("Quantity", Codec.INTEGER, false),
                        (o, v) -> o.quantity = v, o -> o.quantity, (o, p) -> o.quantity = p.quantity).add()
                .appendInherited(new KeyedCodec<>("From", Codec.STRING, false),
                        (o, v) -> o.from = v, o -> o.from, (o, p) -> o.from = p.from).add()
                .build();

        @Nonnull
        public static Consume of(@Nullable String itemId, @Nullable String resourceTypeId,
                @Nullable Integer quantity, @Nullable String from) {
            Consume c = new Consume();
            c.itemId = itemId;
            c.resourceTypeId = resourceTypeId;
            c.quantity = quantity;
            c.from = from;
            return c;
        }

        @Nullable
        public String getItemId() {
            return itemId;
        }

        @Nullable
        public String getResourceTypeId() {
            return resourceTypeId;
        }

        @Nullable
        public Integer getQuantity() {
            return quantity;
        }

        @Nullable
        public String getFrom() {
            return from;
        }

        /** {@link #from}, reader-defaulted to {@link #FROM_INVENTORY} when null/blank. */
        @Nonnull
        public String effectiveFrom() {
            return from == null || from.isBlank() ? FROM_INVENTORY : from;
        }
    }

    /**
     * Produce {@link #quantity} of {@link #itemId} {@link #to}. {@code To: "Inventory"} (default
     * when omitted) is the ONLY route this leg's engine EXECUTES; {@code "Custody"} decodes
     * (schema-reserved for a future leg's output-stays-in-custody shape, e.g. the anvil holding
     * the weapon being enhanced) but has no handler yet - {@code UNIMPLEMENTED_PRODUCE_DEST} flags it.
     */
    public static final class Produce {
        public static final String TO_INVENTORY = "Inventory";
        public static final String TO_CUSTODY = "Custody";

        @Nullable protected String itemId;
        @Nullable protected Integer quantity;
        @Nullable protected String to;

        public static final BuilderCodec<Produce> CODEC = BuilderCodec.builder(Produce.class, Produce::new)
                .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                        (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId).add()
                .appendInherited(new KeyedCodec<>("Quantity", Codec.INTEGER, false),
                        (o, v) -> o.quantity = v, o -> o.quantity, (o, p) -> o.quantity = p.quantity).add()
                .appendInherited(new KeyedCodec<>("To", Codec.STRING, false),
                        (o, v) -> o.to = v, o -> o.to, (o, p) -> o.to = p.to).add()
                .build();

        @Nonnull
        public static Produce of(@Nullable String itemId, @Nullable Integer quantity, @Nullable String to) {
            Produce p = new Produce();
            p.itemId = itemId;
            p.quantity = quantity;
            p.to = to;
            return p;
        }

        @Nullable
        public String getItemId() {
            return itemId;
        }

        @Nullable
        public Integer getQuantity() {
            return quantity;
        }

        @Nullable
        public String getTo() {
            return to;
        }

        /** {@link #to}, reader-defaulted to {@link #TO_INVENTORY} when null/blank. */
        @Nonnull
        public String effectiveTo() {
            return to == null || to.isBlank() ? TO_INVENTORY : to;
        }
    }

    /**
     * Suspend the walk until {@link #durationMs} elapses (real time) OR {@link #beats} swing
     * ticks have fired. Exactly one route is meaningful; both authored is a content mistake
     * ({@code StationValidator}'s {@code WAIT_BOTH_ROUTES} warns, {@code DurationMs} wins).
     * {@link #beats} decodes (forward-compat with the anvil's strike beats, phase-2 leg E) but has
     * no handler yet - {@code UNIMPLEMENTED_WAIT_BEATS} flags a {@code Beats}-only Wait.
     */
    public static final class Wait {
        @Nullable protected Long durationMs;
        @Nullable protected Integer beats;

        public static final BuilderCodec<Wait> CODEC = BuilderCodec.builder(Wait.class, Wait::new)
                .appendInherited(new KeyedCodec<>("DurationMs", Codec.LONG, false),
                        (o, v) -> o.durationMs = v, o -> o.durationMs, (o, p) -> o.durationMs = p.durationMs).add()
                .appendInherited(new KeyedCodec<>("Beats", Codec.INTEGER, false),
                        (o, v) -> o.beats = v, o -> o.beats, (o, p) -> o.beats = p.beats).add()
                .build();

        @Nonnull
        public static Wait ofDurationMs(@Nullable Long durationMs) {
            Wait w = new Wait();
            w.durationMs = durationMs;
            return w;
        }

        @Nonnull
        public static Wait ofBeats(@Nullable Integer beats) {
            Wait w = new Wait();
            w.beats = beats;
            return w;
        }

        @Nullable
        public Long getDurationMs() {
            return durationMs;
        }

        @Nullable
        public Integer getBeats() {
            return beats;
        }
    }

    /**
     * Evaluate a loot pass through the SAME {@code loot.LootEngine}/{@link Roll} vocabulary a
     * station's own {@code Loot} group uses (DRY - one roll engine, never a second). Either
     * {@link #lootable} (a referenced {@link LootableAsset} id) or {@link #rolls} (inline), or
     * both (both resolve, same as {@code StationAsset.Loot}).
     */
    public static final class RollGroup {
        @Nullable protected String lootable;
        @Nullable protected Roll[] rolls;

        public static final BuilderCodec<RollGroup> CODEC = BuilderCodec.builder(RollGroup.class, RollGroup::new)
                .appendInherited(new KeyedCodec<>("Lootable", Codec.STRING, false),
                        (o, v) -> o.lootable = v, o -> o.lootable, (o, p) -> o.lootable = p.lootable).add()
                .appendInherited(new KeyedCodec<>("Rolls", new ArrayCodec<>(Roll.CODEC, Roll[]::new), false),
                        (o, v) -> o.rolls = v, o -> o.rolls, (o, p) -> o.rolls = p.rolls).add()
                .build();

        @Nonnull
        public static RollGroup of(@Nullable String lootable, @Nullable Roll[] rolls) {
            RollGroup g = new RollGroup();
            g.lootable = lootable;
            g.rolls = rolls;
            return g;
        }

        @Nullable
        public String getLootable() {
            return lootable;
        }

        @Nullable
        public Roll[] getRolls() {
            return rolls;
        }
    }

    /** Run {@link #commands} through the SAME {@code loot.CommandRewardExecutor} a {@code Roll.Grants.Commands} uses. */
    public static final class CommandGroup {
        @Nullable protected String[] commands;

        public static final BuilderCodec<CommandGroup> CODEC = BuilderCodec.builder(CommandGroup.class, CommandGroup::new)
                .appendInherited(new KeyedCodec<>("Commands", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                        (o, v) -> o.commands = v, o -> o.commands, (o, p) -> o.commands = p.commands).add()
                .build();

        @Nonnull
        public static CommandGroup of(@Nullable String[] commands) {
            CommandGroup g = new CommandGroup();
            g.commands = commands;
            return g;
        }

        @Nullable
        public String[] getCommands() {
            return commands;
        }
    }

    /**
     * The anvil's enhance-commit step (design section 9.5): the ONE transaction commit, compute-
     * then-commit by construction (critique M5's binding fix, enforced by
     * {@code station.StationStepHandlers.StampHandler}, NOT by this codec - a codec cannot enforce
     * an execution order, only carry the data). Two ORTHOGONAL payload leaves, any combination:
     * {@link #durability} (RpgStations-native, real without any progression mod) and
     * {@link #stats} (delegated to the api {@code EnhanceStamperRegistry}; a Stats leaf with no
     * registered stamper no-ops with a runtime-audit warning while Durability still lands).
     * {@link #reagents} are consumed FROM THE PLAYER'S INVENTORY (never a second custody claim -
     * the design's "reagents stay in custody until this step" describes them staying UNTOUCHED in
     * the player's own inventory until this step's commit phase, in contrast to draining them
     * earlier via a separate {@code Consume} step; the actual placed-input custody this leg's
     * anvil uses is reserved for the single item BEING enhanced, capped at
     * {@code Custody.MaxQuantity: 1}).
     */
    public static final class Stamp {
        @Nullable protected Reagent[] reagents;
        @Nullable protected Durability durability;
        @Nullable protected Stats stats;

        public static final BuilderCodec<Stamp> CODEC = BuilderCodec.builder(Stamp.class, Stamp::new)
                .appendInherited(new KeyedCodec<>("Reagents", new ArrayCodec<>(Reagent.CODEC, Reagent[]::new), false),
                        (o, v) -> o.reagents = v, o -> o.reagents, (o, p) -> o.reagents = p.reagents).add()
                .appendInherited(new KeyedCodec<>("Durability", Durability.CODEC, false),
                        (o, v) -> o.durability = v, o -> o.durability, (o, p) -> o.durability = p.durability).add()
                .appendInherited(new KeyedCodec<>("Stats", Stats.CODEC, false),
                        (o, v) -> o.stats = v, o -> o.stats, (o, p) -> o.stats = p.stats).add()
                .build();

        @Nonnull
        public static Stamp of(@Nullable Reagent[] reagents, @Nullable Durability durability, @Nullable Stats stats) {
            Stamp s = new Stamp();
            s.reagents = reagents;
            s.durability = durability;
            s.stats = stats;
            return s;
        }

        @Nullable
        public Reagent[] getReagents() {
            return reagents;
        }

        @Nullable
        public Durability getDurability() {
            return durability;
        }

        @Nullable
        public Stats getStats() {
            return stats;
        }

        /**
         * ONE Stamp reagent cost line: {@code Quantity} of {@code ItemId} or {@code ResourceTypeId}
         * (exactly one route, matching {@link Consume}'s convention), consumed from the player's
         * inventory (storage-first, then the combined view). {@link Stats.Caps.Economics} scales
         * the EFFECTIVE quantity per prior stamp count when authored; this leaf's {@link #quantity}
         * is always the BASE (unscaled) amount.
         */
        public static final class Reagent {
            @Nullable protected String itemId;
            @Nullable protected String resourceTypeId;
            @Nullable protected Integer quantity;

            public static final BuilderCodec<Reagent> CODEC = BuilderCodec.builder(Reagent.class, Reagent::new)
                    .appendInherited(new KeyedCodec<>("ItemId", Codec.STRING, false),
                            (o, v) -> o.itemId = v, o -> o.itemId, (o, p) -> o.itemId = p.itemId).add()
                    .appendInherited(new KeyedCodec<>("ResourceTypeId", Codec.STRING, false),
                            (o, v) -> o.resourceTypeId = v, o -> o.resourceTypeId,
                            (o, p) -> o.resourceTypeId = p.resourceTypeId).add()
                    .appendInherited(new KeyedCodec<>("Quantity", Codec.INTEGER, false),
                            (o, v) -> o.quantity = v, o -> o.quantity, (o, p) -> o.quantity = p.quantity).add()
                    .build();

            @Nonnull
            public static Reagent of(@Nullable String itemId, @Nullable String resourceTypeId,
                    @Nullable Integer quantity) {
                Reagent r = new Reagent();
                r.itemId = itemId;
                r.resourceTypeId = resourceTypeId;
                r.quantity = quantity;
                return r;
            }

            @Nullable
            public String getItemId() {
                return itemId;
            }

            @Nullable
            public String getResourceTypeId() {
                return resourceTypeId;
            }

            @Nullable
            public Integer getQuantity() {
                return quantity;
            }

            /** {@link #quantity}, reader-defaulted to 1 when null/non-positive. */
            public int effectiveQuantity() {
                return quantity != null && quantity > 0 ? quantity : 1;
            }
        }

        /**
         * RpgStations-NATIVE durability stamp (design 9.5): {@link #addMax} raises the stack's own
         * {@code MaxDurability} (real, per-stack, independent of any progression mod - the
         * standalone anvil's own value-add without the MMO). The engine also adds the SAME delta
         * to the stack's current durability (a genuine upgrade, not a relative nerf against the
         * new higher max).
         */
        public static final class Durability {
            @Nullable protected Double addMax;

            public static final BuilderCodec<Durability> CODEC =
                    BuilderCodec.builder(Durability.class, Durability::new)
                            .appendInherited(new KeyedCodec<>("AddMax", Codec.DOUBLE, false),
                                    (o, v) -> o.addMax = v, o -> o.addMax, (o, p) -> o.addMax = p.addMax).add()
                            .build();

            @Nonnull
            public static Durability of(@Nullable Double addMax) {
                Durability d = new Durability();
                d.addMax = addMax;
                return d;
            }

            @Nullable
            public Double getAddMax() {
                return addMax;
            }
        }

        /**
         * The composable stat-roll + cap model (design 9.5, critique M2's binding fixes): roll
         * selection is EITHER {@link #pool} (a reusable {@code RollPool} asset id) or inline
         * {@link #entries} (or both - both resolve, same convention as {@code RollGroup}), picked
         * through {@link #picks} + {@link #unique}, then clamped by {@link #caps} - resolved end to
         * end by the PURE, unit-tested {@code station.StampCapEngine} before the Stamp step ever
         * touches the api {@code EnhanceStamper}.
         */
        public static final class Stats {
            @Nullable protected String pool;
            @Nullable protected StatRollEntry[] entries;
            @Nullable protected Picks picks;
            @Nullable protected Boolean unique;
            @Nullable protected Caps caps;

            public static final BuilderCodec<Stats> CODEC = BuilderCodec.builder(Stats.class, Stats::new)
                    .appendInherited(new KeyedCodec<>("Pool", Codec.STRING, false),
                            (o, v) -> o.pool = v, o -> o.pool, (o, p) -> o.pool = p.pool).add()
                    .appendInherited(new KeyedCodec<>("Entries", new ArrayCodec<>(StatRollEntry.CODEC, StatRollEntry[]::new), false),
                            (o, v) -> o.entries = v, o -> o.entries, (o, p) -> o.entries = p.entries).add()
                    .appendInherited(new KeyedCodec<>("Picks", Picks.CODEC, false),
                            (o, v) -> o.picks = v, o -> o.picks, (o, p) -> o.picks = p.picks).add()
                    .appendInherited(new KeyedCodec<>("Unique", Codec.BOOLEAN, false),
                            (o, v) -> o.unique = v, o -> o.unique, (o, p) -> o.unique = p.unique).add()
                    .appendInherited(new KeyedCodec<>("Caps", Caps.CODEC, false),
                            (o, v) -> o.caps = v, o -> o.caps, (o, p) -> o.caps = p.caps).add()
                    .build();

            @Nonnull
            public static Stats of(@Nullable String pool, @Nullable StatRollEntry[] entries, @Nullable Picks picks,
                    @Nullable Boolean unique, @Nullable Caps caps) {
                Stats s = new Stats();
                s.pool = pool;
                s.entries = entries;
                s.picks = picks;
                s.unique = unique;
                s.caps = caps;
                return s;
            }

            @Nullable
            public String getPool() {
                return pool;
            }

            @Nullable
            public StatRollEntry[] getEntries() {
                return entries;
            }

            @Nullable
            public Picks getPicks() {
                return picks;
            }

            /** {@link #unique}, reader-defaulted to false (duplicate stat picks allowed) when null. */
            public boolean isUnique() {
                return unique != null && unique;
            }

            @Nullable
            public Caps getCaps() {
                return caps;
            }

            /** How many pool entries a Stamp attempt picks (weighted route only - {@code Always} entries are extra). */
            public static final class Picks {
                @Nullable protected Integer min;
                @Nullable protected Integer max;

                public static final BuilderCodec<Picks> CODEC = BuilderCodec.builder(Picks.class, Picks::new)
                        .appendInherited(new KeyedCodec<>("Min", Codec.INTEGER, false),
                                (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min).add()
                        .appendInherited(new KeyedCodec<>("Max", Codec.INTEGER, false),
                                (o, v) -> o.max = v, o -> o.max, (o, p) -> o.max = p.max).add()
                        .build();

                @Nonnull
                public static Picks of(@Nullable Integer min, @Nullable Integer max) {
                    Picks p = new Picks();
                    p.min = min;
                    p.max = max;
                    return p;
                }

                @Nullable
                public Integer getMin() {
                    return min;
                }

                @Nullable
                public Integer getMax() {
                    return max;
                }

                /** {@link #min}, reader-defaulted to 1 when null/non-positive. */
                public int effectiveMin() {
                    return min != null && min > 0 ? min : 1;
                }

                /** {@link #max}, reader-defaulted to {@link #effectiveMin()} when null/less than it. */
                public int effectiveMax() {
                    int lo = effectiveMin();
                    return max != null && max >= lo ? max : lo;
                }
            }

            /**
             * Every cap leaf is nullable and independently composable - "alone or in ANY
             * combination" (maintainer directive 3). <b>M2's binding composition rule (documented
             * here, the ONE place a sonnet implementer needs to read it):</b> when more than one
             * TOTAL-BUDGET cap is authored ({@link #perItemBudget} and/or {@link #skillScaledBudget}),
             * the EFFECTIVE budget is the MIN of every authored one - never the max, never a sum.
             * {@link #perStat} is a SEPARATE, additional per-stat-id ceiling layered on top of
             * (not instead of) the total budget. {@link #economics} does not affect the point
             * budget at all - it scales REAGENT cost per prior stamp count.
             */
            public static final class Caps {
                @Nullable protected Double perItemBudget;
                @Nullable protected Map<String, Double> perStat;
                @Nullable protected SkillScaledBudget skillScaledBudget;
                @Nullable protected Economics economics;

                public static final BuilderCodec<Caps> CODEC = BuilderCodec.builder(Caps.class, Caps::new)
                        .appendInherited(new KeyedCodec<>("PerItemBudget", Codec.DOUBLE, false),
                                (o, v) -> o.perItemBudget = v, o -> o.perItemBudget,
                                (o, p) -> o.perItemBudget = p.perItemBudget).add()
                        .appendInherited(new KeyedCodec<>("PerStat", new MapCodec<>(Codec.DOUBLE, LinkedHashMap::new), false),
                                (o, v) -> o.perStat = v, o -> o.perStat, (o, p) -> o.perStat = p.perStat).add()
                        .appendInherited(new KeyedCodec<>("SkillScaledBudget", SkillScaledBudget.CODEC, false),
                                (o, v) -> o.skillScaledBudget = v, o -> o.skillScaledBudget,
                                (o, p) -> o.skillScaledBudget = p.skillScaledBudget).add()
                        .appendInherited(new KeyedCodec<>("Economics", Economics.CODEC, false),
                                (o, v) -> o.economics = v, o -> o.economics, (o, p) -> o.economics = p.economics).add()
                        .build();

                @Nonnull
                public static Caps of(@Nullable Double perItemBudget, @Nullable Map<String, Double> perStat,
                        @Nullable SkillScaledBudget skillScaledBudget, @Nullable Economics economics) {
                    Caps c = new Caps();
                    c.perItemBudget = perItemBudget;
                    c.perStat = perStat;
                    c.skillScaledBudget = skillScaledBudget;
                    c.economics = economics;
                    return c;
                }

                @Nullable
                public Double getPerItemBudget() {
                    return perItemBudget;
                }

                @Nullable
                public Map<String, Double> getPerStat() {
                    return perStat;
                }

                @Nullable
                public SkillScaledBudget getSkillScaledBudget() {
                    return skillScaledBudget;
                }

                @Nullable
                public Economics getEconomics() {
                    return economics;
                }
            }

            /**
             * A total-point budget that GROWS with a factor (design 9.5's "budget grows with a
             * factor, the skill-scaled model" - factor-based so ANY registered {@code FactorProvider}
             * can back it, not just a skill level): {@code effective = PointsPerLevel * resolve(Factor, Param)}.
             * The anvil's shipped example reads {@code mmoskilltree:skill_level} with
             * {@code Param: "SMITHING"} - an EXISTING registered factor (leg 5's
             * {@code StationFactorProviders}), zero new factor plumbing needed. An unresolvable
             * factor contributes 0 (fails closed, matching every other factor consumer in this mod).
             */
            public static final class SkillScaledBudget {
                @Nullable protected String factor;
                @Nullable protected String param;
                @Nullable protected Double pointsPerLevel;

                public static final BuilderCodec<SkillScaledBudget> CODEC =
                        BuilderCodec.builder(SkillScaledBudget.class, SkillScaledBudget::new)
                                .appendInherited(new KeyedCodec<>("Factor", Codec.STRING, false),
                                        (o, v) -> o.factor = v, o -> o.factor, (o, p) -> o.factor = p.factor).add()
                                .appendInherited(new KeyedCodec<>("Param", Codec.STRING, false),
                                        (o, v) -> o.param = v, o -> o.param, (o, p) -> o.param = p.param).add()
                                .appendInherited(new KeyedCodec<>("PointsPerLevel", Codec.DOUBLE, false),
                                        (o, v) -> o.pointsPerLevel = v, o -> o.pointsPerLevel,
                                        (o, p) -> o.pointsPerLevel = p.pointsPerLevel).add()
                                .build();

                @Nonnull
                public static SkillScaledBudget of(@Nullable String factor, @Nullable String param,
                        @Nullable Double pointsPerLevel) {
                    SkillScaledBudget b = new SkillScaledBudget();
                    b.factor = factor;
                    b.param = param;
                    b.pointsPerLevel = pointsPerLevel;
                    return b;
                }

                @Nullable
                public String getFactor() {
                    return factor;
                }

                @Nullable
                public String getParam() {
                    return param;
                }

                @Nullable
                public Double getPointsPerLevel() {
                    return pointsPerLevel;
                }
            }

            /**
             * The reagent-cost-scaling model (design 9.5, critique M2 fix (b)): EFFECTIVE reagent
             * quantity for a Stamp attempt = {@code ceil(baseQuantity * (1 + RepeatCostMultiplier *
             * stampCount))}, {@code stampCount} read off the registered stamper's own
             * {@link com.ziggfreed.rpgstations.api.StampInspection#stampCount()} (the M2 (b) leaf -
             * a per-stack stamp count now exists on the MMO's item metadata seam specifically so
             * this is computable). Absent {@code Economics} = flat cost every attempt (the
             * multiplier contributes nothing).
             */
            public static final class Economics {
                @Nullable protected Double repeatCostMultiplier;

                public static final BuilderCodec<Economics> CODEC =
                        BuilderCodec.builder(Economics.class, Economics::new)
                                .appendInherited(new KeyedCodec<>("RepeatCostMultiplier", Codec.DOUBLE, false),
                                        (o, v) -> o.repeatCostMultiplier = v, o -> o.repeatCostMultiplier,
                                        (o, p) -> o.repeatCostMultiplier = p.repeatCostMultiplier).add()
                                .build();

                @Nonnull
                public static Economics of(@Nullable Double repeatCostMultiplier) {
                    Economics e = new Economics();
                    e.repeatCostMultiplier = repeatCostMultiplier;
                    return e;
                }

                @Nullable
                public Double getRepeatCostMultiplier() {
                    return repeatCostMultiplier;
                }
            }
        }
    }

    /**
     * The per-step puppet override (round-4 puppet-presentation design, section 3.1/3.6): a
     * SMALL group tweaking only the moment-to-moment {@link #clip} + {@link #prop} for THIS step -
     * never the session-scoped hide/look/spawn knobs, which live on the station/action-level
     * {@link Puppet} group and are set once at engage. {@link #prop} reuses {@link Puppet.Prop}'s
     * EXACT codec (DRY - one prop shape, whether authored at the action level or per step).
     */
    public static final class PuppetOverride {
        @Nullable protected String clip;
        @Nullable protected Puppet.Prop prop;

        public static final BuilderCodec<PuppetOverride> CODEC =
                BuilderCodec.builder(PuppetOverride.class, PuppetOverride::new)
                        .appendInherited(new KeyedCodec<>("Clip", Codec.STRING, false),
                                (o, v) -> o.clip = v, o -> o.clip, (o, p) -> o.clip = p.clip).add()
                        .appendInherited(new KeyedCodec<>("Prop", Puppet.Prop.CODEC, false),
                                (o, v) -> o.prop = v, o -> o.prop, (o, p) -> o.prop = p.prop).add()
                        .build();

        @Nonnull
        public static PuppetOverride of(@Nullable String clip, @Nullable Puppet.Prop prop) {
            PuppetOverride o = new PuppetOverride();
            o.clip = clip;
            o.prop = prop;
            return o;
        }

        /** The puppet clip id for this beat (e.g. a ritual's strike/quench/stamp poses); null = inherit the action's default clip. */
        @Nullable
        public String getClip() {
            return clip;
        }

        /** The puppet's held prop for this beat; null = inherit the action's default {@link Puppet#getProp()}. */
        @Nullable
        public Puppet.Prop getProp() {
            return prop;
        }
    }
}
