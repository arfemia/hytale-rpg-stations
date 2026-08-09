package com.ziggfreed.rpgstations.asset;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.schema.metadata.ui.UIEditor;

/**
 * ONE SELF-CONTAINED ACTION: a complete job a station offers, readable top to bottom with nothing
 * implied and nothing inherited from elsewhere in the file. Used at TWO sites with one schema
 * authority: as an entry of a {@link StationAsset}'s ordered {@code Actions} array, AND as the body
 * of a standalone {@link ActionAsset} (which wraps this exact field set with an id + native
 * {@code Parent}).
 *
 * <p><b>A station supplies no defaults.</b> An action reads its own groups, or the {@code Ref} base
 * it explicitly names, and nothing else. The one composition the station still performs is the
 * {@code Requires} AND: the station's own entry gate must pass AS WELL AS this action's.
 *
 * <p><b>The eight concerns</b>, in authored order: what it is ({@link #id}/{@link #ref}/
 * {@link #label}), when it applies ({@link #select}/{@link #requires}/{@link #tool}), what it makes
 * ({@link #recipe}), how the loop runs ({@link #work}/{@link #custody}), where it runs
 * ({@link #anchors}/{@link #steps}), what else it hands over ({@link #bonus}/
 * {@link #contributionScale}), how the person looks doing it ({@link #worker}), and what it sounds
 * and looks like ({@link #moments}).
 *
 * <p><b>{@link #ref}:</b> when authored on an inline {@code Actions} entry it names a standalone
 * {@link ActionAsset} as the BASE, and any OTHER group authored on the inline entry overlays it
 * GROUP-WISE (whole-group replace, one level). A dangling {@link #ref} is a validator finding
 * ({@code ACTION_REF_UNKNOWN}); engage denies gracefully.
 *
 * <p><b>{@link #steps}</b> is the authored step PROGRAM ({@link StationStep}[]); when absent the
 * engine builds the IMPLICIT classic-convert-loop program from this action's own {@link #recipe}.
 */
public final class ActionDef {

    @Nullable protected String id;
    @Nullable protected String ref;
    @Nullable protected String label;
    @Nullable protected ActionInput select;
    @Nullable protected Requires requires;
    @Nullable protected StationAsset.Tool tool;
    @Nullable protected StationAsset.Recipe recipe;
    @Nullable protected StationAsset.Work work;
    @Nullable protected Custody custody;
    @Nullable protected Map<String, Anchor> anchors;
    @Nullable protected StationStep[] steps;
    @Nullable protected LootRef bonus;
    @Nullable protected ContributionScale contributionScale;
    @Nullable protected Worker worker;
    @Nullable protected Moments moments;

    public static final BuilderCodec<ActionDef> CODEC = BuilderCodec.builder(ActionDef.class, ActionDef::new)
            .appendInherited(new KeyedCodec<>("Id", Codec.STRING, false),
                    (o, v) -> o.id = v, o -> o.id, (o, p) -> o.id = p.id)
            .documentation("This action's own id, unique within the station and matched case-insensitively. Step insertions and Extensions target it, and the engine records it on the running session.").add()
            .appendInherited(new KeyedCodec<>("Ref", ActionAsset.CHILD_ASSET_CODEC, false),
                    (o, v) -> o.ref = v, o -> o.ref, (o, p) -> o.ref = p.ref)
            .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:actions")))
            .documentation("Names a standalone ActionAsset as the BASE; any other group authored here replaces that group wholesale. "
                    + "May also be an INLINE action body (optionally with its own Parent), registered as a generated "
                    + "child action asset.").add()
            .appendInherited(new KeyedCodec<>("Label", Codec.STRING, false),
                    (o, v) -> o.label = v, o -> o.label, (o, p) -> o.label = p.label)
            .documentation("An advisory localization key for admin/UI display of the action's name.")
            .metadata(new UIEditor(new UIEditor.LocalizationKeyField("rpgstations.action.{assetId}.label"))).add()
            .appendInherited(new KeyedCodec<>("Select", ActionInput.CODEC, false),
                    (o, v) -> o.select = v, o -> o.select, (o, p) -> o.select = p.select)
            .documentation("Which held or placed material picks this action out of the station's ordered list; the first action whose Select matches wins. "
                    + "ABSENT means this action matches any context, and its custody acceptance derives from its own Recipe inputs instead.").add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (o, v) -> o.requires = v, o -> o.requires, (o, p) -> o.requires = p.requires)
            .documentation("This action's own start gate (permission plus factor Conditions). The station's own Requires must ALSO pass; this never inherits it.").add()
            .appendInherited(new KeyedCodec<>("Tool", StationAsset.Tool.CODEC, false),
                    (o, v) -> o.tool = v, o -> o.tool, (o, p) -> o.tool = p.tool)
            .documentation("The held-tool gate for this action - the ONE gate, checked at engage and re-checked every heartbeat.").add()
            .appendInherited(new KeyedCodec<>("Recipe", StationAsset.Recipe.CODEC, false),
                    (o, v) -> o.recipe = v, o -> o.recipe, (o, p) -> o.recipe = p.recipe)
            .documentation("The ONE transform this action performs (Conversions and/or FromCrafting, plus Yield). Two transforms means two actions.").add()
            .appendInherited(new KeyedCodec<>("Work", StationAsset.Work.CODEC, false),
                    (o, v) -> o.work = v, o -> o.work, (o, p) -> o.work = p.work)
            .documentation("The work-loop cadence, duration/exit bounds, per-cycle contributions, looping flag, and optional idle-practice mode.").add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (o, v) -> o.custody = v, o -> o.custody, (o, p) -> o.custody = p.custody)
            .documentation("Session-scoped placed-input custody; null = the classic direct-inventory Consume/Produce flow.").add()
            .appendInherited(new KeyedCodec<>("Anchors",
                            new MapCodec<>(Anchor.CODEC, LinkedHashMap::new), false),
                    (o, v) -> o.anchors = v, o -> o.anchors, (o, p) -> o.anchors = p.anchors)
            .documentation("Named multi-station anchor declarations (id -> {Station, MaxRadiusMeters}); a step's At/Walk.To names one and the engine discovers + claims the nearest matching placed block within MaxRadiusMeters.").add()
            .appendInherited(new KeyedCodec<>("Steps", new ArrayCodec<>(StationStep.CODEC, StationStep[]::new), false),
                    (o, v) -> o.steps = v, o -> o.steps, (o, p) -> o.steps = p.steps)
            .documentation("The authored step PROGRAM; absent = the implicit classic-convert-loop program built from Recipe.").add()
            .appendInherited(new KeyedCodec<>("Bonus", LootRef.CODEC, false),
                    (o, v) -> o.bonus = v, o -> o.bonus, (o, p) -> o.bonus = p.bonus)
            .documentation("What ELSE a cycle hands over: referenced Lootables plus inline Rolls. Yield decides how much of the thing you made, Bonus decides what else you got.").add()
            .appendInherited(new KeyedCodec<>("ContributionScale", ContributionScale.CODEC, false),
                    (o, v) -> o.contributionScale = v, o -> o.contributionScale,
                    (o, p) -> o.contributionScale = p.contributionScale)
            .documentation("A factor ladder multiplying every Work.PerCycleContributions amount before it is forwarded; the engine pre-scales, so a listener grants the amount verbatim.").add()
            .appendInherited(new KeyedCodec<>("Worker", Worker.CODEC, false),
                    (o, v) -> o.worker = v, o -> o.worker, (o, p) -> o.worker = p.worker)
            .documentation("How the person looks doing this: Hold, Camera, Animation, Puppet.").add()
            .appendInherited(new KeyedCodec<>("Moments", Moments.CODEC, false),
                    (o, v) -> o.moments = v, o -> o.moments, (o, p) -> o.moments = p.moments)
            .documentation("What it sounds and looks like: the per-Cycle moment and the session Completion moment.").add()
            .build();

    public ActionDef() {
    }

    /** Java-side factory for the two identity leaves; use the {@code withX} chain for the rest. */
    @Nonnull
    public static ActionDef of(@Nullable String id) {
        ActionDef a = new ActionDef();
        a.id = id;
        return a;
    }

    /** Java-side factory naming a {@link ActionAsset} base by id. */
    @Nonnull
    public static ActionDef of(@Nullable String id, @Nullable String ref) {
        ActionDef a = of(id);
        a.ref = ref;
        return a;
    }

    @Nonnull
    public ActionDef withId(@Nullable String id) {
        this.id = id;
        return this;
    }

    @Nonnull
    public ActionDef withLabel(@Nullable String label) {
        this.label = label;
        return this;
    }

    @Nonnull
    public ActionDef withRef(@Nullable String ref) {
        this.ref = ref;
        return this;
    }

    @Nonnull
    public ActionDef withSelect(@Nullable ActionInput select) {
        this.select = select;
        return this;
    }

    @Nonnull
    public ActionDef withRequires(@Nullable Requires requires) {
        this.requires = requires;
        return this;
    }

    @Nonnull
    public ActionDef withTool(@Nullable StationAsset.Tool tool) {
        this.tool = tool;
        return this;
    }

    @Nonnull
    public ActionDef withRecipe(@Nullable StationAsset.Recipe recipe) {
        this.recipe = recipe;
        return this;
    }

    @Nonnull
    public ActionDef withWork(@Nullable StationAsset.Work work) {
        this.work = work;
        return this;
    }

    @Nonnull
    public ActionDef withCustody(@Nullable Custody custody) {
        this.custody = custody;
        return this;
    }

    @Nonnull
    public ActionDef withAnchors(@Nullable Map<String, Anchor> anchors) {
        this.anchors = anchors;
        return this;
    }

    @Nonnull
    public ActionDef withSteps(@Nullable StationStep[] steps) {
        this.steps = steps;
        return this;
    }

    @Nonnull
    public ActionDef withBonus(@Nullable LootRef bonus) {
        this.bonus = bonus;
        return this;
    }

    @Nonnull
    public ActionDef withContributionScale(@Nullable ContributionScale contributionScale) {
        this.contributionScale = contributionScale;
        return this;
    }

    @Nonnull
    public ActionDef withWorker(@Nullable Worker worker) {
        this.worker = worker;
        return this;
    }

    @Nonnull
    public ActionDef withMoments(@Nullable Moments moments) {
        this.moments = moments;
        return this;
    }

    /**
     * This action's own id (unique within its station, matched case-insensitively); null on a
     * standalone {@link ActionAsset} body, whose id is its filename.
     */
    @Nullable
    public String getId() {
        return id;
    }

    /** A referenced {@link ActionAsset} id used as the BASE; null = a self-contained inline action. */
    @Nullable
    public String getRef() {
        return ref;
    }

    /** True when {@link #ref} is authored (this inline entry references a standalone {@link ActionAsset}). */
    public boolean hasRef() {
        return ref != null && !ref.isBlank();
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * The selection matcher; null = this action matches any context AND derives its custody
     * acceptance from its own {@code Recipe} inputs.
     */
    @Nullable
    public ActionInput getSelect() {
        return select;
    }

    /** This action's own start gate; the station's own {@code Requires} is ANDed with it. */
    @Nullable
    public Requires getRequires() {
        return requires;
    }

    @Nullable
    public StationAsset.Tool getTool() {
        return tool;
    }

    /** The ONE transform this action performs; null = a Steps-programmed or custody-only action. */
    @Nullable
    public StationAsset.Recipe getRecipe() {
        return recipe;
    }

    @Nullable
    public StationAsset.Work getWork() {
        return work;
    }

    @Nullable
    public Custody getCustody() {
        return custody;
    }

    /** Named multi-station anchor declarations ({@code id -> {Station, MaxRadiusMeters}}); null = none. */
    @Nullable
    public Map<String, Anchor> getAnchors() {
        return anchors;
    }

    /** The authored step program; {@code null}/empty means "build the implicit program". */
    @Nullable
    public StationStep[] getSteps() {
        return steps;
    }

    /** What ELSE a cycle hands over (a {@link LootRef}); null = nothing extra. */
    @Nullable
    public LootRef getBonus() {
        return bonus;
    }

    /** The per-cycle contribution multiplier ladder; null = the neutral 1.0. */
    @Nullable
    public ContributionScale getContributionScale() {
        return contributionScale;
    }

    /** How the person looks doing this ({@code Hold}/{@code Camera}/{@code Animation}/{@code Puppet}). */
    @Nullable
    public Worker getWorker() {
        return worker;
    }

    /** What it sounds and looks like ({@code Cycle}/{@code Completion}). */
    @Nullable
    public Moments getMoments() {
        return moments;
    }

    /**
     * GROUPING, not a new concept: the four presentation groups that answer "how does the person
     * look doing this". They travel together because an author tuning one almost always tunes the
     * next, and nesting them keeps an action at eight readable concerns instead of fourteen flat
     * siblings. Each leaf is the SAME type it always was, and each is independently nullable.
     */
    public static final class Worker {
        @Nullable protected StationAsset.Hold hold;
        @Nullable protected StationAsset.Camera camera;
        @Nullable protected StationAsset.Animation animation;
        @Nullable protected Puppet puppet;

        public static final BuilderCodec<Worker> CODEC = BuilderCodec.builder(Worker.class, Worker::new)
                .appendInherited(new KeyedCodec<>("Hold", StationAsset.Hold.CODEC, false),
                        (o, v) -> o.hold = v, o -> o.hold, (o, p) -> o.hold = p.hold)
                .documentation("The movement lock while working: the default self-effect hold, or the Mount knob family (seated/standing mount).").add()
                .appendInherited(new KeyedCodec<>("Camera", StationAsset.Camera.CODEC, false),
                        (o, v) -> o.camera = v, o -> o.camera, (o, p) -> o.camera = p.camera)
                .documentation("The third-person camera pull while working, plus the optional fixed-look Recipe preset.").add()
                .appendInherited(new KeyedCodec<>("Animation", StationAsset.Animation.CODEC, false),
                        (o, v) -> o.animation = v, o -> o.animation, (o, p) -> o.animation = p.animation)
                .documentation("The work emote id plus the optional per-swing cadence/impact cue layer.").add()
                .appendInherited(new KeyedCodec<>("Puppet", Puppet.CODEC, false),
                        (o, v) -> o.puppet = v, o -> o.puppet, (o, p) -> o.puppet = p.puppet)
                .documentation("The puppet presentation route: mount the player, hide their body, spawn a skinned visual performing the work. Null = the classic in-body worker.").add()
                .build();

        @Nonnull
        public static Worker of(@Nullable StationAsset.Hold hold, @Nullable StationAsset.Camera camera,
                @Nullable StationAsset.Animation animation, @Nullable Puppet puppet) {
            Worker w = new Worker();
            w.hold = hold;
            w.camera = camera;
            w.animation = animation;
            w.puppet = puppet;
            return w;
        }

        @Nullable
        public StationAsset.Hold getHold() {
            return hold;
        }

        @Nullable
        public StationAsset.Camera getCamera() {
            return camera;
        }

        @Nullable
        public StationAsset.Animation getAnimation() {
            return animation;
        }

        @Nullable
        public Puppet getPuppet() {
            return puppet;
        }
    }

    /**
     * GROUPING, not a new concept: the same {@link Presentation} type at two TIMES - once per
     * completed cycle, and once when the whole session finishes. Naming the moments rather than the
     * type is what makes the pair readable side by side.
     */
    public static final class Moments {
        @Nullable protected Presentation cycle;
        @Nullable protected Presentation completion;

        public static final BuilderCodec<Moments> CODEC = BuilderCodec.builder(Moments.class, Moments::new)
                .appendInherited(new KeyedCodec<>("Cycle", Presentation.CODEC, false),
                        (o, v) -> o.cycle = v, o -> o.cycle, (o, p) -> o.cycle = p.cycle)
                .documentation("The CYCLE-complete moment: sound/particle cues fired at the block each finished cycle.").add()
                .appendInherited(new KeyedCodec<>("Completion", Presentation.CODEC, false),
                        (o, v) -> o.completion = v, o -> o.completion, (o, p) -> o.completion = p.completion)
                .documentation("The SESSION-COMPLETION moment, played on a non-silent stop with at least one completed cycle. Null = silent completion.").add()
                .build();

        @Nonnull
        public static Moments of(@Nullable Presentation cycle, @Nullable Presentation completion) {
            Moments m = new Moments();
            m.cycle = cycle;
            m.completion = completion;
            return m;
        }

        @Nullable
        public Presentation getCycle() {
            return cycle;
        }

        @Nullable
        public Presentation getCompletion() {
            return completion;
        }
    }

    /**
     * ONE multi-station anchor declaration: {@link #station} is the target STATION id (a type
     * filter), {@link #maxRadiusMeters} the horizontal radius in meters from the primary station
     * block. The reserved anchor id {@code "self"} = the primary block (never authored; the
     * validator rejects declaring it). Anchor discovery, claiming, and walk execution are all live.
     */
    public static final class Anchor {
        /** The reserved anchor id every program has implicitly (the primary station block); never authored. */
        public static final String RESERVED_SELF = "self";
        /** The design default anchor discovery radius (horizontal blocks/meters). */
        public static final double DEFAULT_MAX_RADIUS_METERS = 12.0;

        @Nullable protected String station;
        @Nullable protected Double maxRadiusMeters;

        public static final BuilderCodec<Anchor> CODEC = BuilderCodec.builder(Anchor.class, Anchor::new)
                .appendInherited(new KeyedCodec<>("Station", Codec.STRING, false),
                        (o, v) -> o.station = v, o -> o.station, (o, p) -> o.station = p.station)
                .documentation("The target station id this anchor resolves against (a type filter).")
                .metadata(new UIEditor(new UIEditor.Dropdown("rpgstations:stations"))).add()
                .appendInherited(new KeyedCodec<>("MaxRadiusMeters", Codec.DOUBLE, false),
                        (o, v) -> o.maxRadiusMeters = v, o -> o.maxRadiusMeters,
                        (o, p) -> o.maxRadiusMeters = p.maxRadiusMeters)
                .documentation("The horizontal search radius for the anchor station, in meters (one block = one meter); reader-defaults to 12.")
                .addValidator(CodecWarnValidators.positive("Anchors[].MaxRadiusMeters should be positive.")).add()
                .build();

        @Nonnull
        public static Anchor of(@Nullable String station, @Nullable Double maxRadiusMeters) {
            Anchor a = new Anchor();
            a.station = station;
            a.maxRadiusMeters = maxRadiusMeters;
            return a;
        }

        @Nullable
        public String getStation() {
            return station;
        }

        @Nullable
        public Double getMaxRadiusMeters() {
            return maxRadiusMeters;
        }

        /**
         * {@link #maxRadiusMeters}, reader-defaulted to {@link #DEFAULT_MAX_RADIUS_METERS} when
         * null/non-positive.
         */
        public double effectiveMaxRadiusMeters() {
            return maxRadiusMeters != null && maxRadiusMeters > 0 ? maxRadiusMeters : DEFAULT_MAX_RADIUS_METERS;
        }
    }
}
