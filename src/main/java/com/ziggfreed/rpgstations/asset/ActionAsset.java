package com.ziggfreed.rpgstations.asset;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.codec.ContainedAssetCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

/**
 * A standalone, reusable, fourth-party-extendable ACTION (scope-2 design 1.5, decision 28a):
 * {@code Server/RpgStations/Actions/<Name>.json} (Pattern A, id = lowercased filename). Its BODY
 * is the SAME field set as the inline {@link ActionDef} (one schema authority - every key here
 * matches {@link ActionDef#CODEC} verbatim); {@code ActionAsset} is a thin Pattern-A wrapper
 * adding an id + native {@code Parent}. A station attaches it via an inline {@code Actions} entry's
 * {@link ActionDef#getRef()} leaf ({@code {"Ref": "<actionAssetId>"}}, optionally with overlay
 * groups); native {@code Parent} between {@code ActionAsset}s is the "author only the delta" reuse
 * route BETWEEN actions.
 *
 * <p><b>Implementation note (one schema authority):</b> the fields live on an embedded
 * {@link #body} {@link ActionDef}, and every codec leaf delegates to it - so {@link ActionDef}
 * stays the single storage + logic authority and this wrapper only adds id/data/Parent plumbing.
 * The key strings are re-declared here to match {@link ActionDef#CODEC} (an
 * {@code AssetBuilderCodec} cannot splice a {@code BuilderCodec} field list); keep the two in
 * lockstep - {@link AssetCodecInitTest} guards PascalCase, the codec round-trip test guards shape.
 */
public final class ActionAsset implements JsonAssetWithMap<String, DefaultAssetMap<String, ActionAsset>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nonnull
    private final ActionDef body = new ActionDef();

    public static final AssetBuilderCodec<String, ActionAsset> CODEC = AssetBuilderCodec.builder(
                    ActionAsset.class,
                    ActionAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id == null ? null : id.toLowerCase(Locale.ROOT),
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .documentation("Ignored - the action id comes from the asset filename, not this key. Kept as a schema field for editor display only.").add()
            .appendInherited(new KeyedCodec<>("Label", Codec.STRING, false),
                    (a, v) -> a.body.label = v, a -> a.body.label, (a, p) -> a.body.label = p.body.label)
            .documentation("An advisory localization key for admin/UI display of the action's name.").add()
            .appendInherited(new KeyedCodec<>("Input", ActionInput.CODEC, false),
                    (a, v) -> a.body.input = v, a -> a.body.input, (a, p) -> a.body.input = p.body.input)
            .documentation("The diegetic action-selection matcher (held item / custody). Absent = a catch-all action.").add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (a, v) -> a.body.custody = v, a -> a.body.custody, (a, p) -> a.body.custody = p.body.custody)
            .documentation("Placed-input custody for this action.").add()
            .appendInherited(new KeyedCodec<>("Puppet", Puppet.CODEC, false),
                    (a, v) -> a.body.puppet = v, a -> a.body.puppet, (a, p) -> a.body.puppet = p.body.puppet)
            .documentation("The puppet-presentation group for this action.").add()
            .appendInherited(new KeyedCodec<>("Work", StationAsset.Work.CODEC, false),
                    (a, v) -> a.body.work = v, a -> a.body.work, (a, p) -> a.body.work = p.body.work)
            .documentation("The work-loop cadence for this action.").add()
            .appendInherited(new KeyedCodec<>("Recipe", StationAsset.Recipe.CODEC, false),
                    (a, v) -> a.body.recipe = v, a -> a.body.recipe, (a, p) -> a.body.recipe = p.body.recipe)
            .documentation("The convert recipe for this action.").add()
            .appendInherited(new KeyedCodec<>("Tool", StationAsset.Tool.CODEC, false),
                    (a, v) -> a.body.tool = v, a -> a.body.tool, (a, p) -> a.body.tool = p.body.tool)
            .documentation("The held-tool gate for this action.").add()
            .appendInherited(new KeyedCodec<>("Hold", StationAsset.Hold.CODEC, false),
                    (a, v) -> a.body.hold = v, a -> a.body.hold, (a, p) -> a.body.hold = p.body.hold)
            .documentation("The movement-hold / mount for this action.").add()
            .appendInherited(new KeyedCodec<>("Camera", StationAsset.Camera.CODEC, false),
                    (a, v) -> a.body.camera = v, a -> a.body.camera, (a, p) -> a.body.camera = p.body.camera)
            .documentation("The camera pull for this action.").add()
            .appendInherited(new KeyedCodec<>("Animation", StationAsset.Animation.CODEC, false),
                    (a, v) -> a.body.animation = v, a -> a.body.animation, (a, p) -> a.body.animation = p.body.animation)
            .documentation("The work animation for this action.").add()
            .appendInherited(new KeyedCodec<>("Presentation", Presentation.CODEC, false),
                    (a, v) -> a.body.presentation = v, a -> a.body.presentation,
                    (a, p) -> a.body.presentation = p.body.presentation)
            .documentation("The per-cycle presentation moment for this action.").add()
            .appendInherited(new KeyedCodec<>("Completion", Presentation.CODEC, false),
                    (a, v) -> a.body.completion = v, a -> a.body.completion,
                    (a, p) -> a.body.completion = p.body.completion)
            .documentation("The session-completion presentation moment for this action.").add()
            .appendInherited(new KeyedCodec<>("Loot", LootRef.CODEC, false),
                    (a, v) -> a.body.loot = v, a -> a.body.loot, (a, p) -> a.body.loot = p.body.loot)
            .documentation("The conditional-loot (LootRef) for this action.").add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (a, v) -> a.body.requires = v, a -> a.body.requires, (a, p) -> a.body.requires = p.body.requires)
            .documentation("The start gate for this action.").add()
            .appendInherited(new KeyedCodec<>("Steps", new ArrayCodec<>(StationStep.CODEC, StationStep[]::new), false),
                    (a, v) -> a.body.steps = v, a -> a.body.steps, (a, p) -> a.body.steps = p.body.steps)
            .documentation("The authored step PROGRAM; absent = the implicit classic-convert-loop program.").add()
            .appendInherited(new KeyedCodec<>("Anchors",
                            new MapCodec<>(ActionDef.Anchor.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.body.anchors = v, a -> a.body.anchors, (a, p) -> a.body.anchors = p.body.anchors)
            .documentation("Named multi-station anchor declarations (id -> {Station, MaxRadiusMeters}); a step's At/Walk.To names one and the engine discovers + claims the nearest matching placed block within MaxRadiusMeters.").add()
            .build();

    /**
     * The engine's own contained-asset codec for this type: {@link ActionDef#getRef()} accepts EITHER
     * a plain {@code "<actionAssetId>"} string reference OR an inline anonymous action body
     * (registered as a generated child asset in this same store), including a nested
     * {@code "Parent": "<actionAssetId>"} that inherits from a named action.
     *
     * <p>Unlike the two single-array asset types ({@link LootableAsset}/{@link RollPool}), an inline
     * body here is a genuine delta authoring route: every {@code ActionAsset} leaf is
     * {@code appendInherited}, so a {@code Parent} body inherits each group it does not author.
     */
    @Nonnull
    public static final Codec<String> CHILD_ASSET_CODEC =
            new ContainedAssetCodec<>(ActionAsset.class, CODEC);

    public ActionAsset() {
    }

    /** Java-side construction path wrapping an existing {@link ActionDef} body under an id. */
    @Nonnull
    public static ActionAsset of(@Nonnull String id, @Nonnull ActionDef body) {
        ActionAsset a = new ActionAsset();
        a.id = id;
        a.body.label = body.label;
        a.body.ref = body.ref;
        a.body.input = body.input;
        a.body.custody = body.custody;
        a.body.puppet = body.puppet;
        a.body.work = body.work;
        a.body.recipe = body.recipe;
        a.body.tool = body.tool;
        a.body.hold = body.hold;
        a.body.camera = body.camera;
        a.body.animation = body.animation;
        a.body.presentation = body.presentation;
        a.body.completion = body.completion;
        a.body.loot = body.loot;
        a.body.requires = body.requires;
        a.body.steps = body.steps;
        a.body.anchors = body.anchors;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * The action body (design 1.5 - "the same ActionDef codec"). NOTE: an {@code ActionAsset} does
     * NOT carry {@link ActionDef#getRef()} (a Ref references ANOTHER action; a standalone action
     * asset is itself a base and never references) - the {@code Ref} key is intentionally absent
     * from this codec, so {@link ActionDef#getRef()} on this body is always null.
     */
    @Nonnull
    public ActionDef getBody() {
        return body;
    }
}
