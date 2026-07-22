package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * ONE step of a multi-action station's step PROGRAM (design section 9.3), a Pattern A union
 * mirroring the MMO's {@code AbilityAsset.EffectStep}: a {@link #type} discriminator plus, per
 * type, ONE nested group carrying that step's own fields (orthogonal groups, never a flat
 * prefixed-key soup). Every base field ({@link #id}/{@link #type}/{@link #conditions}/
 * {@link #onConditionFail}/{@link #presentation}) applies to EVERY step type; {@link #id} is
 * unique WITHIN one action's {@code Steps} array (the {@code station.step} engine's resume/jump
 * bookkeeping keys off it - {@code StationValidator} flags a duplicate).
 *
 * <p><b>Branch is NOT a step type</b> (design 9.3): a step authors {@link OnConditionFail#getGoto()}
 * to jump, via the reshaped {@code cast.step} kernel's {@code StepSemantics#nextIndex} hook - no
 * dedicated branch node exists in this vocabulary.
 *
 * <p><b>Reserved, unimplemented this leg</b> (design 9.3/9.5): {@code "Stamp"} (the anvil's
 * enhance-commit step, full shape lands with the {@code EnhanceStamper} api registry, phase-2 leg
 * E) and {@code "Mount"} (a mid-sequence pose swap). Both {@link #type} strings decode fine (no
 * dedicated nested group exists for either yet); {@code station.step.StationStepRegistry}
 * registers no handler for them, so a program authoring one FAILS at dispatch with a clear log -
 * {@code StationValidator}'s {@code UNIMPLEMENTED_STEP_TYPE} finding catches the authoring
 * mistake ahead of runtime, per the design's binding note.
 */
public final class StationStep {

    /** The six step types this leg's engine actually EXECUTES (see the class javadoc for the two reserved ids). */
    public static final String TYPE_CONSUME = "Consume";
    public static final String TYPE_PRODUCE = "Produce";
    public static final String TYPE_WAIT = "Wait";
    public static final String TYPE_ROLL = "Roll";
    public static final String TYPE_COMMAND = "Command";
    public static final String TYPE_PRESENT = "Present";
    /** Schema-reserved, unimplemented this leg (see the class javadoc). */
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

    @Nonnull
    public StationStep withPresentation(@Nullable Presentation v) {
        this.presentation = v;
        return this;
    }

    /** True when {@link #type} is one of the two design-reserved, not-yet-executable ids. */
    public boolean isReservedUnimplemented() {
        return TYPE_STAMP.equalsIgnoreCase(type) || TYPE_MOUNT.equalsIgnoreCase(type);
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
     * "Inventory"} (the default when omitted) is the ONLY route this leg's engine EXECUTES;
     * {@code "Custody"} decodes (forward-compat with phase-2 leg C's placed-input custody) but has
     * no handler yet - {@code StationValidator}'s {@code UNIMPLEMENTED_CONSUME_SOURCE} flags it.
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
     * (forward-compat) but has no handler yet - {@code UNIMPLEMENTED_PRODUCE_DEST} flags it.
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
}
