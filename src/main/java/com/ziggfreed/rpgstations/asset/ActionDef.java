package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * ONE entry of a multi-action station's {@code Actions} map (design section 9.1): a WHOLE-GROUP
 * override of the owning {@link StationAsset}'s own groups. Every leaf here is nullable and,
 * when authored, REPLACES the station-level group wholesale for this action (no per-leaf merge -
 * the design's explicit resolution rule: "an action authoring a group replaces the station-level
 * group wholesale; omitting inherits it"); see {@code station.ActionResolver#resolve} for the
 * choke point every group read goes through. This is distinct from native {@code Parent} asset
 * inheritance (which still composes at the WHOLE-{@code Actions}-map level, same as today's
 * {@code Flairs} map) - two different inheritance axes that do not interact.
 *
 * <p>{@link #label} is a full localization key for admin/UI display (the action's own name, e.g.
 * on a future action-picker); phase 2's action selection itself is diegetic (design 9.1's
 * input-matched, no menu), so {@link #label} is advisory, never read by the selection path.
 * {@link #input} is the diegetic selection matcher ({@link ActionInput}); an entry with no
 * {@link #input} (or an all-blank one) is a catch-all - see {@link ActionInput#isCatchAll()}.
 * {@link #steps} is the authored step PROGRAM; when absent, {@code station.ActionResolver} builds
 * the IMPLICIT classic-convert-loop program from this action's resolved groups (design 9.3) - see
 * {@code station.step.ImplicitProgram}.
 */
public final class ActionDef {

    @Nullable protected String label;
    @Nullable protected ActionInput input;
    @Nullable protected Custody custody;
    @Nullable protected StationAsset.Work work;
    @Nullable protected StationAsset.Recipe recipe;
    @Nullable protected StationAsset.Tool tool;
    @Nullable protected StationAsset.Hold hold;
    @Nullable protected StationAsset.Camera camera;
    @Nullable protected StationAsset.Animation animation;
    @Nullable protected Presentation presentation;
    @Nullable protected Presentation completion;
    @Nullable protected StationAsset.Loot loot;
    @Nullable protected Requires requires;
    @Nullable protected StationStep[] steps;

    public static final BuilderCodec<ActionDef> CODEC = BuilderCodec.builder(ActionDef.class, ActionDef::new)
            .appendInherited(new KeyedCodec<>("Label", Codec.STRING, false),
                    (o, v) -> o.label = v, o -> o.label, (o, p) -> o.label = p.label).add()
            .appendInherited(new KeyedCodec<>("Input", ActionInput.CODEC, false),
                    (o, v) -> o.input = v, o -> o.input, (o, p) -> o.input = p.input).add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (o, v) -> o.custody = v, o -> o.custody, (o, p) -> o.custody = p.custody).add()
            .appendInherited(new KeyedCodec<>("Work", StationAsset.Work.CODEC, false),
                    (o, v) -> o.work = v, o -> o.work, (o, p) -> o.work = p.work).add()
            .appendInherited(new KeyedCodec<>("Recipe", StationAsset.Recipe.CODEC, false),
                    (o, v) -> o.recipe = v, o -> o.recipe, (o, p) -> o.recipe = p.recipe).add()
            .appendInherited(new KeyedCodec<>("Tool", StationAsset.Tool.CODEC, false),
                    (o, v) -> o.tool = v, o -> o.tool, (o, p) -> o.tool = p.tool).add()
            .appendInherited(new KeyedCodec<>("Hold", StationAsset.Hold.CODEC, false),
                    (o, v) -> o.hold = v, o -> o.hold, (o, p) -> o.hold = p.hold).add()
            .appendInherited(new KeyedCodec<>("Camera", StationAsset.Camera.CODEC, false),
                    (o, v) -> o.camera = v, o -> o.camera, (o, p) -> o.camera = p.camera).add()
            .appendInherited(new KeyedCodec<>("Animation", StationAsset.Animation.CODEC, false),
                    (o, v) -> o.animation = v, o -> o.animation, (o, p) -> o.animation = p.animation).add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (o, v) -> o.presentation = v, o -> o.presentation, (o, p) -> o.presentation = p.presentation).add()
            .appendInherited(new KeyedCodec<>("Completion", Presentation.CODEC, false),
                    (o, v) -> o.completion = v, o -> o.completion, (o, p) -> o.completion = p.completion).add()
            .appendInherited(new KeyedCodec<>("Loot", StationAsset.Loot.CODEC, false),
                    (o, v) -> o.loot = v, o -> o.loot, (o, p) -> o.loot = p.loot).add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (o, v) -> o.requires = v, o -> o.requires, (o, p) -> o.requires = p.requires).add()
            .appendInherited(new KeyedCodec<>("Steps", new ArrayCodec<>(StationStep.CODEC, StationStep[]::new), false),
                    (o, v) -> o.steps = v, o -> o.steps, (o, p) -> o.steps = p.steps).add()
            .build();

    public ActionDef() {
    }

    /** Java-side construction path; sets the same fields the codec fills. */
    @Nonnull
    public static ActionDef of(@Nullable String label, @Nullable ActionInput input,
            @Nullable StationAsset.Work work, @Nullable StationAsset.Recipe recipe,
            @Nullable StationAsset.Tool tool, @Nullable StationAsset.Hold hold,
            @Nullable StationAsset.Camera camera, @Nullable StationAsset.Animation animation,
            @Nullable Presentation presentation, @Nullable Presentation completion,
            @Nullable StationAsset.Loot loot, @Nullable Requires requires, @Nullable StationStep[] steps) {
        ActionDef a = new ActionDef();
        a.label = label;
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

    /**
     * Java-side test/fixture helper; not part of any codec fold. {@link #of} has no
     * {@code custody} parameter (an oversight in that factory's original param list), so a
     * fixture needing a per-action {@link Custody} override chains this instead.
     */
    @Nonnull
    public ActionDef withCustody(@Nullable Custody custody) {
        this.custody = custody;
        return this;
    }

    @Nullable
    public String getLabel() {
        return label;
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

    @Nullable
    public StationAsset.Loot getLoot() {
        return loot;
    }

    @Nullable
    public Requires getRequires() {
        return requires;
    }

    /** The authored step program; {@code null}/empty means "build the implicit program" (design 9.3). */
    @Nullable
    public StationStep[] getSteps() {
        return steps;
    }
}
