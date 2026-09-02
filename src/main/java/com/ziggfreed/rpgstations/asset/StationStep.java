package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;
import com.ziggfreed.common.asset.EditorSchema;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.stamp.StampSpec;

/**
 * ONE step of a multi-action station's step PROGRAM, reshaped for scope-2 (design section 2.1,
 * gate Q1/Q2 ADOPTED) from the old {@code Type}-discriminated union into an ORTHOGONAL PHASE
 * record. A step composes any combination of nullable PHASE groups executed in ONE fixed,
 * documented order; a step with no phase group is a pure BEAT (presentation + clip + duration).
 * This kills the old {@code Wait} type, the reserved {@code Mount} type, and {@code Wait.Beats}
 * outright (no decoy fields the engine does not execute).
 *
 * <p><b>Base fields</b> (apply to every step): {@link #id} (unique within one action's {@code
 * Steps} array; required whenever another step or an {@code ExtensionAsset} insertion anchors on
 * it), {@link #conditions} + {@link #onConditionFail} (the gate + its branch/skip result),
 * {@link #at} (an anchor id from the action's {@code Anchors} map; absent = the primary station
 * {@code "self"}), {@link #repeat} (per-step iteration count), {@link #duration} (post-phase hold),
 * {@link #puppet} (per-step clip/prop), {@link #presentation} (a per-iteration-entry cue), and
 * {@link #commands} (a phase - see below).
 *
 * <p><b>Phase groups</b> (all nullable): {@link #walk}, {@link #consume}, {@link #stamp},
 * {@link #produce}, {@link #roll} (a {@link LootRef}), {@link #commands} ({@code String[]}).
 *
 * <p><b>Execution order within ONE step iteration (fixed, honored by the engine - leg A3):</b>
 * Conditions gate -&gt; {@link #walk} -&gt; {@link #consume} -&gt; {@link #stamp} -&gt;
 * {@link #produce} -&gt; {@link #roll} -&gt; {@link #commands} -&gt; {@link #presentation}/
 * {@link #puppet} clip (fire at iteration entry, listed here for the mental model) -&gt;
 * {@link #duration} hold (suspend) -&gt; next iteration or next step.
 *
 * <p><b>Every field here EXECUTES.</b> There are no decode-only decoys on this type: {@link #walk},
 * {@link #at}, and {@code Produce.To: "Custody"} run the multi-station seam for real, alongside
 * {@link #consume}/{@link #produce}/{@link #roll}/{@link #commands}/{@link #stamp}/
 * {@link #duration}/{@link #repeat}.
 */
public final class StationStep {

    @Nullable protected String id;
    @Nullable protected FactorCondition[] conditions;
    @Nullable protected OnConditionFail onConditionFail;
    @Nullable protected String at;
    @Nullable protected Repeat repeat;
    @Nullable protected Duration duration;
    @Nullable protected Presentation presentation;
    @Nullable protected PuppetOverride puppet;

    @Nullable protected Walk walk;
    @Nullable protected Consume consume;
    @Nullable protected Produce produce;
    @Nullable protected LootRef roll;
    @Nullable protected String[] commands;
    @Nullable protected Stamp stamp;
    @Nullable protected Boolean isWork;

    public static final BuilderCodec<StationStep> CODEC = BuilderCodec.builder(StationStep.class, StationStep::new)
            .appendInherited(new KeyedCodec<>("Id", Codec.STRING, false),
                    (o, v) -> o.id = v, o -> o.id, (o, p) -> o.id = p.id)
            .documentation("Unique step id within one action's Steps array; required when another step or extension insertion anchors on it.").add()
            .appendInherited(new KeyedCodec<>("Conditions", new ArrayCodec<>(Conditions.CODEC, FactorCondition[]::new), false),
                    (o, v) -> o.conditions = v, o -> o.conditions, (o, p) -> o.conditions = p.conditions)
            .documentation("Gate re-checked at each iteration entry; a failing check runs OnConditionFail.").add()
            .appendInherited(new KeyedCodec<>("OnConditionFail", OnConditionFail.CODEC, false),
                    (o, v) -> o.onConditionFail = v, o -> o.onConditionFail,
                    (o, p) -> o.onConditionFail = p.onConditionFail)
            .documentation("What a failing Conditions check does: Skip (no-op continue) or Fail (default); Goto jumps to a step Id.").add()
            .appendInherited(new KeyedCodec<>("At", Codec.STRING, false),
                    (o, v) -> o.at = v, o -> o.at, (o, p) -> o.at = p.at)
            .documentation("The anchor id (from the action's Anchors map) this step runs at; absent = the primary station 'self'.").add()
            .appendInherited(new KeyedCodec<>("Repeat", Repeat.CODEC, false),
                    (o, v) -> o.repeat = v, o -> o.repeat, (o, p) -> o.repeat = p.repeat)
            .documentation("Per-step iteration count: a fixed Times, or Min/Max/Factors resolved once at step entry.").add()
            .appendInherited(new KeyedCodec<>("Duration", Duration.CODEC, false),
                    (o, v) -> o.duration = v, o -> o.duration, (o, p) -> o.duration = p.duration)
            .documentation("A post-phase hold in ms per iteration; prop/presentation persist across the hold.").add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation)
            .documentation("A sound/particles/etc. cue played once at step ITERATION entry.").add()
            .appendInherited(new KeyedCodec<>("Puppet", PuppetOverride.CODEC, false),
                    (o, v) -> o.puppet = v, o -> o.puppet, (o, p) -> o.puppet = p.puppet)
            .documentation("Per-step puppet override: a Clip played at iteration entry and/or a Prop swap for this beat.").add()
            .appendInherited(new KeyedCodec<>("Walk", Walk.CODEC, false),
                    (o, v) -> o.walk = v, o -> o.walk, (o, p) -> o.walk = p.walk)
            .documentation("Move the puppet to an anchor (To) at SpeedMps; requires Puppet enabled (WALK_REQUIRES_PUPPET warns otherwise).").add()
            .appendInherited(new KeyedCodec<>("Consume", Consume.CODEC, false),
                    (o, v) -> o.consume = v, o -> o.consume, (o, p) -> o.consume = p.consume)
            .documentation("Consume every Items entry From Inventory (default) or Custody; all-or-nothing across the whole list.").add()
            .appendInherited(new KeyedCodec<>("Produce", Produce.CODEC, false),
                    (o, v) -> o.produce = v, o -> o.produce, (o, p) -> o.produce = p.produce)
            .documentation("Produce every Items entry To Inventory (default) or Custody (the At-anchor's claim).").add()
            .appendInherited(new KeyedCodec<>("Roll", LootRef.CODEC, false),
                    (o, v) -> o.roll = v, o -> o.roll, (o, p) -> o.roll = p.roll)
            .documentation("Evaluate a loot pass through the shared LootRef (Lootables + inline Rolls) vocabulary.").add()
            .appendInherited(new KeyedCodec<>("Commands", new ArrayCodec<>(Codec.STRING, String[]::new), false),
                    (o, v) -> o.commands = v, o -> o.commands, (o, p) -> o.commands = p.commands)
            .documentation("Run commands through the shared CommandRewardExecutor with the usual placeholder substitutions.").add()
            .appendInherited(new KeyedCodec<>("Stamp", Stamp.CODEC, false),
                    (o, v) -> o.stamp = v, o -> o.stamp, (o, p) -> o.stamp = p.stamp)
            .documentation("The enhance-commit phase (reagents + durability + stat rolls) - see Stamp.").add()
            .appendInherited(new KeyedCodec<>("IsWork", Codec.BOOLEAN, false),
                    (o, v) -> o.isWork = v, o -> o.isWork, (o, p) -> o.isWork = p.isWork)
            .documentation("Does this step count as WORK at its At-anchor block (driving that block's Custody.States.Working look)? Default: true for a Consume+Produce convert step, false otherwise. Author true on a pure beat that IS the work (a cook hold), false to suppress.").add()
            .build();

    public StationStep() {
    }

    /** Java-side construction path (a program built procedurally, e.g. {@code ImplicitProgram}). */
    @Nonnull
    public static StationStep of(@Nullable String id) {
        StationStep s = new StationStep();
        s.id = id;
        return s;
    }

    @Nullable
    public String getId() {
        return id;
    }

    @Nullable
    public FactorCondition[] getConditions() {
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

    /** The anchor id this step runs at (from the action's {@code Anchors} map); null = the primary station {@code "self"}. */
    @Nullable
    public String getAt() {
        return at;
    }

    /** The raw authored {@code IsWork} opt-in/opt-out; {@code null} = use the derived default ({@link #effectiveIsWork}). */
    @Nullable
    public Boolean getIsWork() {
        return isWork;
    }

    /** Java-side setter for a procedurally-built program (mirrors the {@code with*} phase setters). */
    @Nonnull
    public StationStep withIsWork(@Nullable Boolean v) {
        this.isWork = v;
        return this;
    }

    /**
     * Does this step count as WORK at its {@link #getAt()} anchor - i.e. should that block wear its
     * {@code Custody.States.Working} look while this step runs?
     *
     * <p>The reader-default is DERIVED, never a mode flag: a step that both {@code Consume}s and
     * {@code Produce}s is the engine's own atomic-transform CONVERT (the phase model's documented
     * "no consumed-without-produced window" rule), and a convert IS work - so the classic implicit
     * single-step program and any authored convert step light their block for free with zero extra
     * authoring. Every other shape (a pure beat, a lone Consume, a lone Produce, a walk, a stamp)
     * defaults to NOT work, because those are the load/carry/unload beats around the work. An
     * explicit {@code "IsWork": true} promotes a pure beat that genuinely IS the work (the fish
     * exemplar's 2.5s cook hold at the fire anchor); an explicit {@code false} demotes a convert
     * that should not light its block.
     *
     * <p>Zero effect unless the block's resolved {@code Custody.States.Working} is authored - every
     * pre-knob station stays byte-identical.
     */
    public boolean effectiveIsWork() {
        return isWork != null ? isWork : (consume != null && produce != null);
    }

    @Nonnull
    public StationStep withAt(@Nullable String v) {
        this.at = v;
        return this;
    }

    /** Per-step iteration count; null = a single iteration. */
    @Nullable
    public Repeat getRepeat() {
        return repeat;
    }

    @Nonnull
    public StationStep withRepeat(@Nullable Repeat v) {
        this.repeat = v;
        return this;
    }

    /** Post-phase hold; null = no hold. */
    @Nullable
    public Duration getDuration() {
        return duration;
    }

    @Nonnull
    public StationStep withDuration(@Nullable Duration v) {
        this.duration = v;
        return this;
    }

    @Nullable
    public Presentation getPresentation() {
        return presentation;
    }

    @Nonnull
    public StationStep withPresentation(@Nullable Presentation v) {
        this.presentation = v;
        return this;
    }

    /** Move the puppet to an anchor; null = no walk. */
    @Nullable
    public Walk getWalk() {
        return walk;
    }

    @Nonnull
    public StationStep withWalk(@Nullable Walk v) {
        this.walk = v;
        return this;
    }

    @Nullable
    public Consume getConsume() {
        return consume;
    }

    @Nonnull
    public StationStep withConsume(@Nullable Consume v) {
        this.consume = v;
        return this;
    }

    @Nullable
    public Produce getProduce() {
        return produce;
    }

    @Nonnull
    public StationStep withProduce(@Nullable Produce v) {
        this.produce = v;
        return this;
    }

    /** The loot phase (a {@link LootRef}); null = no roll. */
    @Nullable
    public LootRef getRoll() {
        return roll;
    }

    @Nonnull
    public StationStep withRoll(@Nullable LootRef v) {
        this.roll = v;
        return this;
    }

    /** The command phase; null = no commands. */
    @Nullable
    public String[] getCommands() {
        return commands;
    }

    @Nonnull
    public StationStep withCommands(@Nullable String[] v) {
        this.commands = v;
        return this;
    }

    @Nullable
    public Stamp getStamp() {
        return stamp;
    }

    @Nonnull
    public StationStep withStamp(@Nullable Stamp v) {
        this.stamp = v;
        return this;
    }

    /** The per-step puppet override ({@code {Clip?, Prop?}}); null = inherit the action's default clip/prop. */
    @Nullable
    public PuppetOverride getPuppet() {
        return puppet;
    }

    @Nonnull
    public StationStep withPuppet(@Nullable PuppetOverride v) {
        this.puppet = v;
        return this;
    }

    /** True when NO phase group is authored - a pure beat (presentation + clip + duration only). */
    public boolean isPureBeat() {
        return walk == null && consume == null && produce == null && roll == null
                && (commands == null || commands.length == 0) && stamp == null;
    }

    /** True when the phase, or any of its entries, authors a custody socket address. */
    private static boolean authorsAnySocket(@Nullable String groupSocket, @Nullable Ingredient[] items) {
        if (groupSocket != null && !groupSocket.isBlank()) {
            return true;
        }
        if (items != null) {
            for (Ingredient item : items) {
                if (item != null && item.getSocket() != null && !item.getSocket().isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * The branch/skip leaf (design 2.1): {@link #result} decides what a FAILING {@link #conditions}
     * check does ({@code "Skip"} - no-op success and continue; {@code "Fail"}, the default - fail
     * the walk at this step), and {@link #goto_} (JSON key {@code "Goto"}) is an authored step
     * {@code Id} the kernel's {@code nextIndex} hook jumps to on a success-continuing result.
     */
    public static final class OnConditionFail {
        public static final String RESULT_SKIP = "Skip";
        public static final String RESULT_FAIL = "Fail";

        @Nullable protected String result;
        @Nullable protected String goto_;

        public static final BuilderCodec<OnConditionFail> CODEC =
                BuilderCodec.builder(OnConditionFail.class, OnConditionFail::new)
                        .appendInherited(new KeyedCodec<>("Result", Codec.STRING, false),
                                (o, v) -> o.result = v, o -> o.result, (o, p) -> o.result = p.result)
                        .documentation("On a failing Conditions check: 'Skip' (treat as a no-op success and continue) or 'Fail' (default).")
                        .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:condition-fail-result")))
                        .metadata(EditorSchema.defaultValue(RESULT_FAIL)).add()
                        .appendInherited(new KeyedCodec<>("Goto", Codec.STRING, false),
                                (o, v) -> o.goto_ = v, o -> o.goto_, (o, p) -> o.goto_ = p.goto_)
                        .documentation("An authored step Id to jump to on a success-continuing result; null = classic linear advance.").add()
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
     * Per-step iteration count (design 2.1, decision 29c). EITHER a fixed {@link #times}, OR a
     * factor-resolved range {@code clamp(round(Min + sum(resolve(f) * f.Weight)), Min, Max)} via
     * {@link #factors} - the same weighted vocabulary as loot chances and caps. Resolved ONCE at
     * step entry; per iteration the Conditions re-check, the phases re-execute, and any
     * clip/presentation re-fire.
     */
    public static final class Repeat {
        @Nullable protected Integer times;
        @Nullable protected Integer min;
        @Nullable protected Integer max;
        @Nullable protected FactorFormula.Term[] factors;

        public static final BuilderCodec<Repeat> CODEC = BuilderCodec.builder(Repeat.class, Repeat::new)
                .appendInherited(new KeyedCodec<>("Times", Codec.INTEGER, false),
                        (o, v) -> o.times = v, o -> o.times, (o, p) -> o.times = p.times)
                .documentation("A fixed iteration count. Authored INSTEAD of Min/Max/Factors (the fixed route).")
                .addValidator(CodecWarnValidators.positive("StationStep.Repeat.Times should be positive; it floors at 1 otherwise.")).add()
                .appendInherited(new KeyedCodec<>("Min", Codec.INTEGER, false),
                        (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
                .documentation("Lower bound of the factor-resolved iteration count (the ranged route).")
                .addValidator(CodecWarnValidators.positive("StationStep.Repeat.Min should be positive; it floors at 1 otherwise.")).add()
                .appendInherited(new KeyedCodec<>("Max", Codec.INTEGER, false),
                        (o, v) -> o.max = v, o -> o.max, (o, p) -> o.max = p.max)
                .documentation("Upper bound of the factor-resolved iteration count (the ranged route).")
                .addValidator(CodecWarnValidators.positive("StationStep.Repeat.Max should be positive; a non-positive value floors to Min.")).add()
                .appendInherited(new KeyedCodec<>("Factors",
                                new ArrayCodec<>(FactorFormula.Term.codec(AssetEditorDataSets.FACTORS),
                                        FactorFormula.Term[]::new), false),
                        (o, v) -> o.factors = v, o -> o.factors, (o, p) -> o.factors = p.factors)
                .documentation("Weighted factor terms summed into the ranged count: clamp(round(Min + sum(resolve*Weight)), Min, Max).").add()
                .afterDecode((Repeat repeat, ExtraInfo extraInfo) -> {
                    if (repeat.times != null
                            && (repeat.min != null || repeat.max != null
                                    || (repeat.factors != null && repeat.factors.length > 0))) {
                        extraInfo.getValidationResults().warn(
                                "StationStep.Repeat should author either the fixed Times route OR the ranged "
                                        + "Min/Max/Factors route, not both - the fixed Times wins and the ranged "
                                        + "leaves are ignored.");
                    }
                })
                .build();

        @Nonnull
        public static Repeat times(@Nullable Integer times) {
            Repeat r = new Repeat();
            r.times = times;
            return r;
        }

        @Nonnull
        public static Repeat of(@Nullable Integer times, @Nullable Integer min, @Nullable Integer max,
                @Nullable FactorFormula.Term[] factors) {
            Repeat r = new Repeat();
            r.times = times;
            r.min = min;
            r.max = max;
            r.factors = factors;
            return r;
        }

        @Nullable
        public Integer getTimes() {
            return times;
        }

        @Nullable
        public Integer getMin() {
            return min;
        }

        @Nullable
        public Integer getMax() {
            return max;
        }

        /** Weighted factor terms summed into the ranged count; null = a fixed {@link #times} (or a Min-only floor). */
        @Nullable
        public FactorFormula.Term[] getFactors() {
            return factors;
        }

        /** True when the fixed {@link #times} route is authored (takes precedence over the ranged route). */
        public boolean isFixed() {
            return times != null;
        }

        /**
         * PURE iteration-count resolution (unit-tested; no live server): if {@link #times} is
         * authored it wins ({@code max(1, times)}); otherwise {@code clamp(round(min +
         * factorContribution), min, max)} where {@code factorContribution} is the caller-resolved
         * {@code sum(resolve(f) * f.Weight)}. Reader defaults: {@code min} 1, {@code max} =
         * {@code max(min, 1)} when omitted/less-than-min. Never returns below 1.
         */
        public int resolveCount(double factorContribution) {
            if (times != null) {
                return Math.max(1, times);
            }
            int lo = min != null && min > 0 ? min : 1;
            int hi = max != null && max >= lo ? max : lo;
            long rounded = Math.round(lo + factorContribution);
            long clamped = Math.max(lo, Math.min(hi, rounded));
            return (int) Math.max(1, clamped);
        }
    }

    /** A post-phase hold: suspend this iteration for {@link #ms} real milliseconds (prop/presentation persist). */
    public static final class Duration {
        @Nullable protected Long ms;

        public static final BuilderCodec<Duration> CODEC = BuilderCodec.builder(Duration.class, Duration::new)
                .appendInherited(new KeyedCodec<>("Ms", Codec.LONG, false),
                        (o, v) -> o.ms = v, o -> o.ms, (o, p) -> o.ms = p.ms)
                .documentation("The hold length in milliseconds for this iteration.")
                .addValidator(CodecWarnValidators.nonNegative("StationStep.Duration.Ms should not be negative (0 means no hold).")).add()
                .build();

        @Nonnull
        public static Duration of(@Nullable Long ms) {
            Duration d = new Duration();
            d.ms = ms;
            return d;
        }

        @Nullable
        public Long getMs() {
            return ms;
        }

        /** {@link #ms}, reader-defaulted to 0 when null/negative (no hold). */
        public long effectiveMs() {
            return ms != null && ms > 0 ? ms : 0L;
        }
    }

    /**
     * The Walk phase (design 2.3): move the PUPPET to the anchor {@link #to}
     * (an id from the action's {@code Anchors} map, or the reserved {@code "self"}) at
     * {@link #speedMps}. Requires the action's {@code Puppet} enabled (validator {@code
     * WALK_REQUIRES_PUPPET}).
     */
    public static final class Walk {
        /** The design default straight-line speed (m/s). */
        public static final double DEFAULT_SPEED_MPS = 2.5;

        @Nullable protected String to;
        @Nullable protected Double speedMps;

        public static final BuilderCodec<Walk> CODEC = BuilderCodec.builder(Walk.class, Walk::new)
                .appendInherited(new KeyedCodec<>("To", Codec.STRING, false),
                        (o, v) -> o.to = v, o -> o.to, (o, p) -> o.to = p.to)
                .documentation("The anchor id to walk the puppet to (or the reserved 'self' = the primary station).").add()
                .appendInherited(new KeyedCodec<>("SpeedMps", Codec.DOUBLE, false),
                        (o, v) -> o.speedMps = v, o -> o.speedMps, (o, p) -> o.speedMps = p.speedMps)
                .documentation("Straight-line walk speed in meters per second (reader-defaults to 2.5).")
                .addValidator(CodecWarnValidators.positive("StationStep.Walk.SpeedMps should be positive.")).add()
                .build();

        @Nonnull
        public static Walk of(@Nullable String to, @Nullable Double speedMps) {
            Walk w = new Walk();
            w.to = to;
            w.speedMps = speedMps;
            return w;
        }

        @Nullable
        public String getTo() {
            return to;
        }

        @Nullable
        public Double getSpeedMps() {
            return speedMps;
        }

        /** {@link #speedMps}, reader-defaulted to {@link #DEFAULT_SPEED_MPS} when null/non-positive. */
        public double effectiveSpeedMps() {
            return speedMps != null && speedMps > 0 ? speedMps : DEFAULT_SPEED_MPS;
        }
    }

    /**
     * Consume every entry of {@link #items} (each an {@link Ingredient}: exactly one of
     * {@code ItemId}/{@code ResourceTypeId}, plus a {@code Quantity}) {@link #from} - the native
     * {@code CraftingRecipe.Input} shape, so "2 planks + 1 nail" is ONE phase rather than a
     * step split. {@code From: "Inventory"} (default) and {@code From: "Custody"} (drains the
     * block's placed-input claim) are BOTH executable, and the route stays at GROUP level: a
     * phase draws every item from the same place.
     *
     * <p>The phase is ALL-OR-NOTHING: the handler checks availability across every entry before
     * mutating anything, so a shortfall on the last item never leaves the earlier ones consumed.
     */
    public static final class Consume {
        public static final String FROM_INVENTORY = "Inventory";
        public static final String FROM_CUSTODY = "Custody";

        @Nullable protected Ingredient[] items;
        @Nullable protected String from;
        @Nullable protected String socket;

        public static final BuilderCodec<Consume> CODEC = BuilderCodec.builder(Consume.class, Consume::new)
                .appendInherited(new KeyedCodec<>("Items", new ArrayCodec<>(Ingredient.CODEC, Ingredient[]::new), false),
                        (o, v) -> o.items = v, o -> o.items, (o, p) -> o.items = p.items)
                .documentation("The items this phase consumes, each an Ingredient (at most one of ItemId | ResourceTypeId | Tags, plus Quantity; a route-less entry matches any placed material and needs From:'Custody'). All-or-nothing: a shortfall on any entry consumes none of them.")
                .addValidator(CodecWarnValidators.nonEmptyIfAuthored("StationStep.Consume.Items is an empty array; author at least one entry or omit the group.")).add()
                .appendInherited(new KeyedCodec<>("From", Codec.STRING, false),
                        (o, v) -> o.from = v, o -> o.from, (o, p) -> o.from = p.from)
                .documentation("The source for EVERY item in this phase: 'Inventory' (default) or 'Custody' (the block's placed-input claim).")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:consume-from")))
                .metadata(EditorSchema.defaultValue(FROM_INVENTORY)).add()
                .appendInherited(new KeyedCodec<>("Socket", Codec.STRING, false),
                        (o, v) -> o.socket = v, o -> o.socket, (o, p) -> o.socket = p.socket)
                .documentation("The custody socket every entry of this phase drains from, unless an entry names its own Socket. Absent = the first Item socket. Only meaningful with From: 'Custody'.").add()
                .afterDecode((Consume consume, ExtraInfo extraInfo) -> {
                    if (!FROM_CUSTODY.equalsIgnoreCase(consume.effectiveFrom())
                            && authorsAnySocket(consume.socket, consume.items)) {
                        extraInfo.getValidationResults().warn(
                                "StationStep.Consume authors a Socket but draws From the Inventory - the socket "
                                        + "address is ignored there; author From: 'Custody' or drop the Socket.");
                    }
                })
                .build();

        @Nonnull
        public static Consume of(@Nullable Ingredient[] items, @Nullable String from) {
            return of(items, from, null);
        }

        /** As above, plus the group-level custody {@link #socket} address. */
        @Nonnull
        public static Consume of(@Nullable Ingredient[] items, @Nullable String from, @Nullable String socket) {
            Consume c = new Consume();
            c.items = items;
            c.from = from;
            c.socket = socket;
            return c;
        }

        /** Convenience for a single-item consume (the classic convert loop's shape). */
        @Nonnull
        public static Consume ofOne(@Nullable String itemId, @Nullable String resourceTypeId,
                @Nullable Integer quantity, @Nullable String from) {
            return of(new Ingredient[] {Ingredient.of(itemId, resourceTypeId, quantity)}, from);
        }

        /** The items this phase consumes; null/empty = a no-op phase. */
        @Nullable
        public Ingredient[] getItems() {
            return items;
        }

        @Nullable
        public String getFrom() {
            return from;
        }

        /** The group-level custody socket every entry drains from (an entry's own Socket wins), or null for the first Item socket. */
        @Nullable
        public String getSocket() {
            return socket;
        }

        /** True when no item is authored (the phase does nothing). */
        public boolean isEmpty() {
            return items == null || items.length == 0;
        }

        /** {@link #from}, reader-defaulted to {@link #FROM_INVENTORY} when null/blank. */
        @Nonnull
        public String effectiveFrom() {
            return from == null || from.isBlank() ? FROM_INVENTORY : from;
        }
    }

    /**
     * Produce every entry of {@link #items} (each an exact-{@code ItemId} {@link Ingredient})
     * {@link #to} - the native {@code CraftingRecipe.Output} shape, so a recipe yielding a main
     * output plus a byproduct is ONE phase. {@code To: "Inventory"} (default) and
     * {@code To: "Custody"} (the anchor's placed-input claim) are both executable; the route stays
     * at GROUP level, so a phase writes every item to the same destination.
     */
    public static final class Produce {
        public static final String TO_INVENTORY = "Inventory";
        public static final String TO_CUSTODY = "Custody";

        @Nullable protected Ingredient[] items;
        @Nullable protected String to;
        @Nullable protected String socket;

        public static final BuilderCodec<Produce> CODEC = BuilderCodec.builder(Produce.class, Produce::new)
                .appendInherited(new KeyedCodec<>("Items", new ArrayCodec<>(Ingredient.CODEC, Ingredient[]::new), false),
                        (o, v) -> o.items = v, o -> o.items, (o, p) -> o.items = p.items)
                .documentation("The items this phase produces, each an exact-ItemId Ingredient plus Quantity (ResourceTypeId is an INPUT-only route).")
                .addValidator(CodecWarnValidators.nonEmptyIfAuthored("StationStep.Produce.Items is an empty array; author at least one entry or omit the group.")).add()
                .appendInherited(new KeyedCodec<>("To", Codec.STRING, false),
                        (o, v) -> o.to = v, o -> o.to, (o, p) -> o.to = p.to)
                .documentation("The destination for EVERY item in this phase: 'Inventory' (default) or 'Custody' (the At-anchor's claim).")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:produce-to")))
                .metadata(EditorSchema.defaultValue(TO_INVENTORY)).add()
                .appendInherited(new KeyedCodec<>("Socket", Codec.STRING, false),
                        (o, v) -> o.socket = v, o -> o.socket, (o, p) -> o.socket = p.socket)
                .documentation("The custody socket every entry of this phase lands in, unless an entry names its own Socket. Absent = the first Item socket. Only meaningful with To: 'Custody'.").add()
                .afterDecode((Produce produce, ExtraInfo extraInfo) -> {
                    if (!TO_CUSTODY.equalsIgnoreCase(produce.effectiveTo())
                            && authorsAnySocket(produce.socket, produce.items)) {
                        extraInfo.getValidationResults().warn(
                                "StationStep.Produce authors a Socket but writes To the Inventory - the socket "
                                        + "address is ignored there; author To: 'Custody' or drop the Socket.");
                    }
                })
                .build();

        @Nonnull
        public static Produce of(@Nullable Ingredient[] items, @Nullable String to) {
            return of(items, to, null);
        }

        /** As above, plus the group-level custody {@link #socket} address. */
        @Nonnull
        public static Produce of(@Nullable Ingredient[] items, @Nullable String to, @Nullable String socket) {
            Produce p = new Produce();
            p.items = items;
            p.to = to;
            p.socket = socket;
            return p;
        }

        /** Convenience for a single-item produce (the classic convert loop's shape). */
        @Nonnull
        public static Produce ofOne(@Nullable String itemId, @Nullable Integer quantity, @Nullable String to) {
            return of(new Ingredient[] {Ingredient.item(itemId, quantity)}, to);
        }

        /** The items this phase produces; null/empty = a no-op phase. */
        @Nullable
        public Ingredient[] getItems() {
            return items;
        }

        @Nullable
        public String getTo() {
            return to;
        }

        /** The group-level custody socket every entry lands in (an entry's own Socket wins), or null for the first Item socket. */
        @Nullable
        public String getSocket() {
            return socket;
        }

        /** True when no item is authored (the phase does nothing). */
        public boolean isEmpty() {
            return items == null || items.length == 0;
        }

        /** {@link #to}, reader-defaulted to {@link #TO_INVENTORY} when null/blank. */
        @Nonnull
        public String effectiveTo() {
            return to == null || to.isBlank() ? TO_INVENTORY : to;
        }
    }

    /**
     * The enhance-commit phase (design 9.5 / scope-2 3.8, the anvil's stamp step): the ONE
     * transaction commit, compute-then-commit by construction (enforced by the handler, not this
     * codec). Orthogonal payload leaves, any combination: {@link #durability} (RpgStations-native)
     * and {@link #stats} (the shared stat-roll + budget model, written through the registered
     * stamper). {@link #reagents} are {@link Ingredient}s consumed FROM THE PLAYER'S INVENTORY at
     * this step's commit, optionally scaled by {@link #economics}.
     */
    public static final class Stamp {
        @Nullable protected Ingredient[] reagents;
        @Nullable protected Durability durability;
        @Nullable protected StampSpec stats;
        @Nullable protected Economics economics;

        public static final BuilderCodec<Stamp> CODEC = BuilderCodec.builder(Stamp.class, Stamp::new)
                .appendInherited(new KeyedCodec<>("Reagents", new ArrayCodec<>(Ingredient.CODEC, Ingredient[]::new), false),
                        (o, v) -> o.reagents = v, o -> o.reagents, (o, p) -> o.reagents = p.reagents)
                .documentation("Ingredient reagents consumed from the player's inventory at this step's commit.")
                .addValidator(CodecWarnValidators.nonEmptyIfAuthored("Stamp.Reagents is an empty array; author at least one entry or omit the group (free stamp).")).add()
                .appendInherited(new KeyedCodec<>("Durability", Durability.CODEC, false),
                        (o, v) -> o.durability = v, o -> o.durability, (o, p) -> o.durability = p.durability)
                .documentation("RpgStations-native durability upgrade (AddMax). Real with no other mod installed.").add()
                .appendInherited(new KeyedCodec<>("Stats", StampSpec.codec(AssetEditorDataSets.FACTORS), false),
                        (o, v) -> o.stats = v, o -> o.stats, (o, p) -> o.stats = p.stats)
                .documentation("The composable stat-roll + budget model: which entries are candidates (a shared roll "
                        + "pool, inline entries, or both), how many are picked, and the ceilings the result is held "
                        + "under. The points are written onto the item by whichever stamper this server registered.").add()
                .appendInherited(new KeyedCodec<>("Economics", Economics.CODEC, false),
                        (o, v) -> o.economics = v, o -> o.economics, (o, p) -> o.economics = p.economics)
                .documentation("Reagent-cost scaling per prior stamp count; never affects the point budget.").add()
                .build();

        @Nonnull
        public static Stamp of(@Nullable Ingredient[] reagents, @Nullable Durability durability,
                @Nullable StampSpec stats) {
            return of(reagents, durability, stats, null);
        }

        /** As above, plus the reagent-cost scaling applied to {@code reagents}. */
        @Nonnull
        public static Stamp of(@Nullable Ingredient[] reagents, @Nullable Durability durability,
                @Nullable StampSpec stats, @Nullable Economics economics) {
            Stamp s = new Stamp();
            s.reagents = reagents;
            s.durability = durability;
            s.stats = stats;
            s.economics = economics;
            return s;
        }

        /** {@link Ingredient} reagents consumed from the player's inventory at commit; null = free. */
        @Nullable
        public Ingredient[] getReagents() {
            return reagents;
        }

        @Nullable
        public Durability getDurability() {
            return durability;
        }

        @Nullable
        public StampSpec getStats() {
            return stats;
        }

        /** Reagent-cost scaling per prior stamp count; null = a flat cost every attempt. */
        @Nullable
        public Economics getEconomics() {
            return economics;
        }

        /**
         * RpgStations-NATIVE durability stamp: {@link #addMax} raises the stack's own
         * {@code MaxDurability} (and adds the same delta to current durability - a genuine upgrade).
         */
        public static final class Durability {
            @Nullable protected Double addMax;

            public static final BuilderCodec<Durability> CODEC =
                    BuilderCodec.builder(Durability.class, Durability::new)
                            .appendInherited(new KeyedCodec<>("AddMax", Codec.DOUBLE, false),
                                    (o, v) -> o.addMax = v, o -> o.addMax, (o, p) -> o.addMax = p.addMax)
                            .documentation("The amount added to the stack's MaxDurability (and its current durability).")
                            .addValidator(CodecWarnValidators.positive("Stamp.Durability.AddMax should be positive.")).add()
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
         * The reagent-cost-scaling model: EFFECTIVE reagent quantity =
         * {@code ceil(baseQuantity * (1 + RepeatCostMultiplier * stampCount))}, {@code stampCount}
         * read off the registered stamper. Absent = flat cost every attempt.
         */
        public static final class Economics {
            @Nullable protected Double repeatCostMultiplier;

            public static final BuilderCodec<Economics> CODEC =
                    BuilderCodec.builder(Economics.class, Economics::new)
                            .appendInherited(new KeyedCodec<>("RepeatCostMultiplier", Codec.DOUBLE, false),
                                    (o, v) -> o.repeatCostMultiplier = v, o -> o.repeatCostMultiplier,
                                    (o, p) -> o.repeatCostMultiplier = p.repeatCostMultiplier)
                            .documentation("Scales reagent cost per prior stamp count: ceil(base * (1 + mult * stampCount)).")
                            .addValidator(CodecWarnValidators.nonNegative("Stamp.Economics.RepeatCostMultiplier should not be negative.")).add()
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

    /**
     * The per-step puppet override (design 2.1): a SMALL group tweaking only the moment-to-moment
     * {@link #clip} + {@link #prop} for THIS step. {@link #prop} reuses {@link Puppet.Prop}'s EXACT
     * codec (DRY - one prop shape, whether at the action level or per step).
     */
    public static final class PuppetOverride {
        @Nullable protected String clip;
        @Nullable protected Puppet.Prop prop;

        public static final BuilderCodec<PuppetOverride> CODEC =
                BuilderCodec.builder(PuppetOverride.class, PuppetOverride::new)
                        .appendInherited(new KeyedCodec<>("Clip", Codec.STRING, false),
                                (o, v) -> o.clip = v, o -> o.clip, (o, p) -> o.clip = p.clip)
                        .documentation("The puppet clip id played once at this step's iteration entry; null = inherit the action's default clip.").add()
                        .appendInherited(new KeyedCodec<>("Prop", Puppet.Prop.CODEC, false),
                                (o, v) -> o.prop = v, o -> o.prop, (o, p) -> o.prop = p.prop)
                        .documentation("The puppet's held prop for this beat; null = inherit the action's default Prop.").add()
                        .build();

        @Nonnull
        public static PuppetOverride of(@Nullable String clip, @Nullable Puppet.Prop prop) {
            PuppetOverride o = new PuppetOverride();
            o.clip = clip;
            o.prop = prop;
            return o;
        }

        @Nullable
        public String getClip() {
            return clip;
        }

        @Nullable
        public Puppet.Prop getProp() {
            return prop;
        }
    }
}
