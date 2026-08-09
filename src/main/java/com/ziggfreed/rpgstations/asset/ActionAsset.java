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
 * {@code AssetBuilderCodec} cannot splice a {@code BuilderCodec} field list); the two are held in
 * lockstep by a FIELD-SET PARITY TEST that walks both codecs' entries with named exclusion sets
 * ({@code Ref} and {@code Id} on the {@link ActionDef} side, {@code Name} on this one), so a leaf
 * added to one and forgotten on the other fails the build instead of shipping as a silently
 * unauthorable capability.
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
            .appendInherited(new KeyedCodec<>("Select", ActionInput.CODEC, false),
                    (a, v) -> a.body.select = v, a -> a.body.select, (a, p) -> a.body.select = p.body.select)
            .documentation("Which held or placed material picks this action out of a station's ordered list. ABSENT means it matches any context, and its custody acceptance derives from its own Recipe inputs instead.").add()
            .appendInherited(new KeyedCodec<>("Requires", Requires.CODEC, false),
                    (a, v) -> a.body.requires = v, a -> a.body.requires, (a, p) -> a.body.requires = p.body.requires)
            .documentation("This action's own start gate. The hosting station's own Requires must ALSO pass; this never inherits it.").add()
            .appendInherited(new KeyedCodec<>("Tool", StationAsset.Tool.CODEC, false),
                    (a, v) -> a.body.tool = v, a -> a.body.tool, (a, p) -> a.body.tool = p.body.tool)
            .documentation("The held-tool gate for this action - the ONE gate, checked at engage and re-checked every heartbeat.").add()
            .appendInherited(new KeyedCodec<>("Recipe", StationAsset.Recipe.CODEC, false),
                    (a, v) -> a.body.recipe = v, a -> a.body.recipe, (a, p) -> a.body.recipe = p.body.recipe)
            .documentation("The ONE transform this action performs (Conversions and/or FromCrafting, plus Yield). Two transforms means two actions.").add()
            .appendInherited(new KeyedCodec<>("Work", StationAsset.Work.CODEC, false),
                    (a, v) -> a.body.work = v, a -> a.body.work, (a, p) -> a.body.work = p.body.work)
            .documentation("The work-loop cadence for this action.").add()
            .appendInherited(new KeyedCodec<>("Custody", Custody.CODEC, false),
                    (a, v) -> a.body.custody = v, a -> a.body.custody, (a, p) -> a.body.custody = p.body.custody)
            .documentation("Placed-input custody for this action.").add()
            .appendInherited(new KeyedCodec<>("Anchors",
                            new MapCodec<>(ActionDef.Anchor.CODEC, LinkedHashMap::new), false),
                    (a, v) -> a.body.anchors = v, a -> a.body.anchors, (a, p) -> a.body.anchors = p.body.anchors)
            .documentation("Named multi-station anchor declarations (id -> {Station, MaxRadiusMeters}); a step's At/Walk.To names one and the engine discovers + claims the nearest matching placed block within MaxRadiusMeters.").add()
            .appendInherited(new KeyedCodec<>("Steps", new ArrayCodec<>(StationStep.CODEC, StationStep[]::new), false),
                    (a, v) -> a.body.steps = v, a -> a.body.steps, (a, p) -> a.body.steps = p.body.steps)
            .documentation("The authored step PROGRAM; absent = the implicit classic-convert-loop program built from Recipe.").add()
            .appendInherited(new KeyedCodec<>("Bonus", LootRef.CODEC, false),
                    (a, v) -> a.body.bonus = v, a -> a.body.bonus, (a, p) -> a.body.bonus = p.body.bonus)
            .documentation("What ELSE a cycle hands over: referenced Lootables plus inline Rolls. Yield decides how much of the thing you made, Bonus decides what else you got.").add()
            .appendInherited(new KeyedCodec<>("ContributionScale", ContributionScale.CODEC, false),
                    (a, v) -> a.body.contributionScale = v, a -> a.body.contributionScale,
                    (a, p) -> a.body.contributionScale = p.body.contributionScale)
            .documentation("A factor ladder multiplying every Work.PerCycleContributions amount before it is forwarded; the engine pre-scales, so a listener grants the amount verbatim.").add()
            .appendInherited(new KeyedCodec<>("Worker", ActionDef.Worker.CODEC, false),
                    (a, v) -> a.body.worker = v, a -> a.body.worker, (a, p) -> a.body.worker = p.body.worker)
            .documentation("How the person looks doing this: Hold, Camera, Animation, Puppet.").add()
            .appendInherited(new KeyedCodec<>("Moments", ActionDef.Moments.CODEC, false),
                    (a, v) -> a.body.moments = v, a -> a.body.moments, (a, p) -> a.body.moments = p.body.moments)
            .documentation("What it sounds and looks like: the per-Cycle moment and the session Completion moment.").add()
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
        a.body.select = body.select;
        a.body.requires = body.requires;
        a.body.tool = body.tool;
        a.body.recipe = body.recipe;
        a.body.work = body.work;
        a.body.custody = body.custody;
        a.body.anchors = body.anchors;
        a.body.steps = body.steps;
        a.body.bonus = body.bonus;
        a.body.contributionScale = body.contributionScale;
        a.body.worker = body.worker;
        a.body.moments = body.moments;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * The action body (the same {@link ActionDef} field set). NOTE: an {@code ActionAsset} carries
     * neither {@link ActionDef#getRef()} (a Ref references ANOTHER action; a standalone action asset
     * is itself a base and never references one) nor {@link ActionDef#getId()} (its id IS its
     * filename) - both keys are intentionally absent from this codec, so both read null on this body.
     */
    @Nonnull
    public ActionDef getBody() {
        return body;
    }
}
