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

/**
 * ONE action body (design section 9.1 / scope-2 1.5): a WHOLE-GROUP override set for one action.
 * Used at TWO sites with one schema authority: as a value of a {@link StationAsset}'s inline
 * {@code Actions} map, AND as the body of a standalone {@link ActionAsset} (which wraps this exact
 * field set with an id + native {@code Parent}). Every leaf here is nullable and, when authored,
 * REPLACES the station-level group wholesale for this action (no per-leaf merge); see
 * {@code station.ActionResolver#resolve} for the choke point.
 *
 * <p><b>{@link #ref} (scope-2 1.5):</b> when authored on an inline {@code Actions} entry, it names
 * a standalone {@link ActionAsset} as the BASE; any OTHER group authored on the inline entry
 * overlays it group-wise (the same whole-group-replace semantics applied twice). A dangling
 * {@link #ref} is a validator finding ({@code ACTION_REF_UNKNOWN}); engage denies gracefully.
 *
 * <p><b>{@link #anchors} (scope-2 2.2):</b> named multi-station anchor declarations
 * ({@code id -> {Station, MaxRadius}}), legal on both forms but expected mostly on an
 * {@link ActionAsset}. Decodes and validates this wave; anchor DISCOVERY/claiming/walk execution
 * is [wave 3].
 *
 * <p><b>{@link #steps}</b> is the authored step PROGRAM ({@link StationStep}[]); when absent the
 * engine builds the IMPLICIT classic-convert-loop program from this action's resolved groups.
 * {@link #loot} is a {@link LootRef} (scope-2's unified loot vocabulary).
 */
public final class ActionDef {

    @Nullable protected String label;
    @Nullable protected String ref;
    @Nullable protected ActionInput input;
    @Nullable protected Custody custody;
    @Nullable protected Puppet puppet;
    @Nullable protected StationAsset.Work work;
    @Nullable protected StationAsset.Recipe recipe;
    @Nullable protected StationAsset.Tool tool;
    @Nullable protected StationAsset.Hold hold;
    @Nullable protected StationAsset.Camera camera;
    @Nullable protected StationAsset.Animation animation;
    @Nullable protected Presentation presentation;
    @Nullable protected Presentation completion;
    @Nullable protected LootRef loot;
    @Nullable protected Requires requires;
    @Nullable protected StationStep[] steps;
    @Nullable protected Map<String, Anchor> anchors;

    public static final BuilderCodec<ActionDef> CODEC = BuilderCodec.builder(ActionDef.class, ActionDef::new)
            .appendInherited(new KeyedCodec<>("Label", Codec.STRING, false),
                    (o, v) -> o.label = v, o -> o.label, (o, p) -> o.label = p.label)
            .documentation("An advisory localization key for admin/UI display of the action's name.").add()
            .appendInherited(new KeyedCodec<>("Ref", Codec.STRING, false),
                    (o, v) -> o.ref = v, o -> o.ref, (o, p) -> o.ref = p.ref)
            .documentation("Names a standalone ActionAsset as the BASE; other groups authored here overlay it group-wise.").add()
            .appendInherited(new KeyedCodec<>("Input", ActionInput.CODEC, false),
                    (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input)
            .documentation("The diegetic action-selection matcher (held item / custody). Absent = a catch-all action.").add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (o, v) -> o.custody = v, o -> o.custody, (o, p) -> o.custody = p.custody)
            .documentation("Per-action placed-input custody override.").add()
            .appendInherited(new KeyedCodec<>("Puppet", Puppet.CODEC, false),
                    (o, v) -> o.puppet = v, o -> o.puppet, (o, p) -> o.puppet = p.puppet)
            .documentation("Per-action puppet-presentation override (whole-group replace).").add()
            .appendInherited(new KeyedCodec<>("Work", StationAsset.Work.CODEC, false),
                    (o, v) -> o.work = v, o -> o.work, (o, p) -> o.work = p.work)
            .documentation("Per-action work-loop cadence override.").add()
            .appendInherited(new KeyedCodec<>("Recipe", StationAsset.Recipe.CODEC, false),
                    (o, v) -> o.recipe = v, o -> o.recipe, (o, p) -> o.recipe = p.recipe)
            .documentation("Per-action convert recipe override.").add()
            .appendInherited(new KeyedCodec<>("Tool", StationAsset.Tool.CODEC, false),
                    (o, v) -> o.tool = v, o -> o.tool, (o, p) -> o.tool = p.tool)
            .documentation("Per-action held-tool gate override.").add()
            .appendInherited(new KeyedCodec<>("Hold", StationAsset.Hold.CODEC, false),
                    (o, v) -> o.hold = v, o -> o.hold, (o, p) -> o.hold = p.hold)
            .documentation("Per-action movement-hold / mount override.").add()
            .appendInherited(new KeyedCodec<>("Camera", StationAsset.Camera.CODEC, false),
                    (o, v) -> o.camera = v, o -> o.camera, (o, p) -> o.camera = p.camera)
            .documentation("Per-action camera-pull override.").add()
            .appendInherited(new KeyedCodec<>("Animation", StationAsset.Animation.CODEC, false),
                    (o, v) -> o.animation = v, o -> o.animation, (o, p) -> o.animation = p.animation)
            .documentation("Per-action work-animation override.").add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation)
            .documentation("Per-action per-cycle presentation moment override.").add()
            .appendInherited(new KeyedCodec<>("Completion", Presentation.CODEC, false),
                    (o, v) -> o.completion = v, o -> o.completion, (o, p) -> o.completion = p.completion)
            .documentation("Per-action session-completion presentation moment override.").add()
            .appendInherited(new KeyedCodec<>("Loot", LootRef.CODEC, false),
                    (o, v) -> o.loot = v, o -> o.loot, (o, p) -> o.loot = p.loot)
            .documentation("Per-action conditional-loot override (a LootRef: Lootables + inline Rolls).").add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (o, v) -> o.requires = v, o -> o.requires, (o, p) -> o.requires = p.requires)
            .documentation("Per-action start gate override.").add()
            .appendInherited(new KeyedCodec<>("Steps", new ArrayCodec<>(StationStep.CODEC, StationStep[]::new), false),
                    (o, v) -> o.steps = v, o -> o.steps, (o, p) -> o.steps = p.steps)
            .documentation("The authored step PROGRAM; absent = the implicit classic-convert-loop program.").add()
            .appendInherited(new KeyedCodec<>("Anchors",
                            new MapCodec<>(Anchor.CODEC, LinkedHashMap::new), false),
                    (o, v) -> o.anchors = v, o -> o.anchors, (o, p) -> o.anchors = p.anchors)
            .documentation("Named multi-station anchor declarations (id -> {Station, MaxRadius}). [wave 3 discovery]").add()
            .build();

    public ActionDef() {
    }

    /**
     * Full Java-side factory (the E1 factory gap is closed - every group is a parameter). For
     * fixture ergonomics prefer the empty ctor + {@code withX} chains.
     */
    @Nonnull
    public static ActionDef of(@Nullable String label, @Nullable String ref, @Nullable ActionInput input,
            @Nullable StationAsset.Work work, @Nullable StationAsset.Recipe recipe,
            @Nullable StationAsset.Tool tool, @Nullable StationAsset.Hold hold,
            @Nullable StationAsset.Camera camera, @Nullable StationAsset.Animation animation,
            @Nullable Presentation presentation, @Nullable Presentation completion,
            @Nullable LootRef loot, @Nullable Requires requires, @Nullable StationStep[] steps) {
        ActionDef a = new ActionDef();
        a.label = label;
        a.ref = ref;
        a.input = input;
        a.work = work;
        a.recipe = recipe;
        a.tool = tool;
        a.hold = hold;
        a.camera = camera;
        a.animation = animation;
        a.presentation = presentation;
        a.completion = completion;
        a.loot = loot;
        a.requires = requires;
        a.steps = steps;
        return a;
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
    public ActionDef withInput(@Nullable ActionInput input) {
        this.input = input;
        return this;
    }

    @Nonnull
    public ActionDef withCustody(@Nullable Custody custody) {
        this.custody = custody;
        return this;
    }

    @Nonnull
    public ActionDef withPuppet(@Nullable Puppet puppet) {
        this.puppet = puppet;
        return this;
    }

    @Nonnull
    public ActionDef withWork(@Nullable StationAsset.Work work) {
        this.work = work;
        return this;
    }

    @Nonnull
    public ActionDef withRecipe(@Nullable StationAsset.Recipe recipe) {
        this.recipe = recipe;
        return this;
    }

    @Nonnull
    public ActionDef withTool(@Nullable StationAsset.Tool tool) {
        this.tool = tool;
        return this;
    }

    @Nonnull
    public ActionDef withHold(@Nullable StationAsset.Hold hold) {
        this.hold = hold;
        return this;
    }

    @Nonnull
    public ActionDef withCamera(@Nullable StationAsset.Camera camera) {
        this.camera = camera;
        return this;
    }

    @Nonnull
    public ActionDef withAnimation(@Nullable StationAsset.Animation animation) {
        this.animation = animation;
        return this;
    }

    @Nonnull
    public ActionDef withPresentation(@Nullable Presentation presentation) {
        this.presentation = presentation;
        return this;
    }

    @Nonnull
    public ActionDef withCompletion(@Nullable Presentation completion) {
        this.completion = completion;
        return this;
    }

    @Nonnull
    public ActionDef withLoot(@Nullable LootRef loot) {
        this.loot = loot;
        return this;
    }

    @Nonnull
    public ActionDef withRequires(@Nullable Requires requires) {
        this.requires = requires;
        return this;
    }

    @Nonnull
    public ActionDef withSteps(@Nullable StationStep[] steps) {
        this.steps = steps;
        return this;
    }

    @Nonnull
    public ActionDef withAnchors(@Nullable Map<String, Anchor> anchors) {
        this.anchors = anchors;
        return this;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    /** A referenced {@link ActionAsset} id used as the BASE (scope-2 1.5); null = a self-contained inline action. */
    @Nullable
    public String getRef() {
        return ref;
    }

    /** True when {@link #ref} is authored (this inline entry references a standalone {@link ActionAsset}). */
    public boolean hasRef() {
        return ref != null && !ref.isBlank();
    }

    @Nullable
    public ActionInput getInput() {
        return input;
    }

    @Nullable
    public Custody getCustody() {
        return custody;
    }

    @Nullable
    public Puppet getPuppet() {
        return puppet;
    }

    @Nullable
    public StationAsset.Work getWork() {
        return work;
    }

    @Nullable
    public StationAsset.Recipe getRecipe() {
        return recipe;
    }

    @Nullable
    public StationAsset.Tool getTool() {
        return tool;
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
    public Presentation getPresentation() {
        return presentation;
    }

    @Nullable
    public Presentation getCompletion() {
        return completion;
    }

    /** The per-action conditional-loot override (a {@link LootRef}); null = inherits the station's own. */
    @Nullable
    public LootRef getLoot() {
        return loot;
    }

    @Nullable
    public Requires getRequires() {
        return requires;
    }

    /** The authored step program; {@code null}/empty means "build the implicit program". */
    @Nullable
    public StationStep[] getSteps() {
        return steps;
    }

    /** Named multi-station anchor declarations ({@code id -> {Station, MaxRadius}}); null = none. [wave 3] */
    @Nullable
    public Map<String, Anchor> getAnchors() {
        return anchors;
    }

    /**
     * ONE multi-station anchor declaration (scope-2 2.2): {@link #station} is the target STATION id
     * (a type filter), {@link #maxRadius} the horizontal block radius from the primary station
     * block. The reserved anchor id {@code "self"} = the primary block (never authored; the
     * validator rejects declaring it). Anchor DISCOVERY/claiming/walk is [wave 3]; this leaf
     * decodes and validates in wave 2.
     */
    public static final class Anchor {
        /** The reserved anchor id every program has implicitly (the primary station block); never authored. */
        public static final String RESERVED_SELF = "self";
        /** The design default anchor discovery radius (horizontal blocks). */
        public static final double DEFAULT_MAX_RADIUS = 12.0;

        @Nullable protected String station;
        @Nullable protected Double maxRadius;

        public static final BuilderCodec<Anchor> CODEC = BuilderCodec.builder(Anchor.class, Anchor::new)
                .appendInherited(new KeyedCodec<>("Station", Codec.STRING, false),
                        (o, v) -> o.station = v, o -> o.station, (o, p) -> o.station = p.station)
                .documentation("The target station id this anchor resolves against (a type filter).").add()
                .appendInherited(new KeyedCodec<>("MaxRadius", Codec.DOUBLE, false),
                        (o, v) -> o.maxRadius = v, o -> o.maxRadius, (o, p) -> o.maxRadius = p.maxRadius)
                .documentation("The horizontal block radius to search for the anchor station (reader-defaults to 12).").add()
                .build();

        @Nonnull
        public static Anchor of(@Nullable String station, @Nullable Double maxRadius) {
            Anchor a = new Anchor();
            a.station = station;
            a.maxRadius = maxRadius;
            return a;
        }

        @Nullable
        public String getStation() {
            return station;
        }

        @Nullable
        public Double getMaxRadius() {
            return maxRadius;
        }

        /** {@link #maxRadius}, reader-defaulted to {@link #DEFAULT_MAX_RADIUS} when null/non-positive. */
        public double effectiveMaxRadius() {
            return maxRadius != null && maxRadius > 0 ? maxRadius : DEFAULT_MAX_RADIUS;
        }
    }
}
