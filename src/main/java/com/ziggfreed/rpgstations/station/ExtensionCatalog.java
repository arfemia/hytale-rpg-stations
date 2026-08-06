package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The RUNTIME AUTHORITY for {@link ExtensionAsset}s (scope-2 design 1.8, decision 27): the ONE
 * additive fourth-party composition mechanism. Folded by the plugin's own {@code LoadedAssetsEvent}
 * wiring (always additive, no {@code PackControlAsset} infra); generalizes the {@code FlairCatalog
 * .effectiveFlairsFor} "resolve at read against the folded set, cached per fold generation" pattern.
 *
 * <p>{@link #applyToStationLoot}/{@link #applyToStationContributions}/... are the read-side entry
 * points: they consult the extensions targeting a given id (sorted into
 * {@link ExtensionAsset#APPLY_ORDER}) and merge their payloads onto a base per the deterministic
 * rules. The PURE merge cores
 * ({@link #mergeContributions}/{@link #mergeLoot}/{@link #mergeConversions}/{@link #mergeRolls}/
 * {@link #mergeEntries}/{@link #mergeActions}/{@link #mergeSteps}) are unit-tested without a live
 * catalog: same input set, any fold order -&gt; identical merged result.
 *
 * <p><b>Merge rules (design 1.8, decision 37):</b> ADDITIVE only; keyed collections (Actions,
 * Anchors) the BASE always wins a collision; unkeyed arrays (PerCycleContributions, Conversions,
 * Rolls, Entries, LootRef) pure append in {@link ExtensionAsset#APPLY_ORDER}; ordered step insertion applies each
 * extension's insertions in {@code APPLY_ORDER} against a live working list (later extensions can
 * anchor on earlier-inserted step ids), a dangling {@code After}/{@code Before} anchor degrading to
 * {@code AtEnd}.
 *
 * <p><b>The presentation overlays ({@code Puppet}/{@code Custody}, {@link ExtensionAsset}'s rule
 * 5)</b> are the one NON-collection payload shape, so they merge PER LEAF instead of appending:
 * {@link #overlayPuppet}/{@link #overlayCustody} walk the group recursively and, at every nesting
 * depth, take the OVERLAY's leaf when it is authored and the BASE's when it is not
 * ({@link #firstNonNull} is the ONE rule, applied everywhere). Consequences worth stating because
 * they are the whole point: a {@code Custody} overlay carrying only {@code Display} leaves
 * {@code States}/{@code MaxQuantity}/{@code Input} untouched; a {@code Display} carrying only
 * {@code Scale} leaves {@code Offset}/{@code Rotation} untouched; a {@code Puppet} carrying only
 * {@code Offset.Z} leaves {@code Hide}/{@code Look}/{@code Prop} and even {@code Offset.X}/
 * {@code .Y} untouched. Extensions overlay in {@link ExtensionAsset#APPLY_ORDER}, so the LATER
 * (higher-priority) extension wins a same-leaf contest, and the whole fold is still deterministic
 * for a given input set. A null overlay group returns the base UNCHANGED (identity), so an
 * extension that authors neither key costs nothing.
 */
public final class ExtensionCatalog {

    private static final ExtensionCatalog INSTANCE = new ExtensionCatalog();

    private final ConcurrentHashMap<String, ExtensionAsset> extensions = new ConcurrentHashMap<>();
    /** Cache of the sorted extension list per {@code "<type>::<id>"}, cleared on every {@link #fold}. */
    private final ConcurrentHashMap<String, List<ExtensionAsset>> forTargetCache = new ConcurrentHashMap<>();

    private ExtensionCatalog() {
    }

    @Nonnull
    public static ExtensionCatalog getInstance() {
        return INSTANCE;
    }

    /**
     * Fold {@code layer} in (always additive), invalidate the per-target cache, and log ONE INFO
     * {@code EXTENSION_APPLIED} summary line per distinct target (design 1.8's boot visibility).
     */
    public void fold(@Nonnull Map<String, ExtensionAsset> layer, boolean replace) {
        if (replace) {
            extensions.clear();
        }
        extensions.putAll(layer);
        forTargetCache.clear();
        logAppliedSummary();
    }

    @Nullable
    public ExtensionAsset get(@Nonnull String id) {
        return extensions.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, ExtensionAsset> all() {
        return Collections.unmodifiableMap(extensions);
    }

    public int size() {
        return extensions.size();
    }

    /**
     * The extensions targeting {@code (targetType, targetId)}, sorted into
     * {@link ExtensionAsset#APPLY_ORDER}. Case-insensitive on the target id. Cached per fold
     * generation (the cache clears on {@link #fold}).
     */
    @Nonnull
    public List<ExtensionAsset> extensionsFor(@Nonnull String targetType, @Nullable String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return List.of();
        }
        String key = targetType + "::" + targetId.toLowerCase(Locale.ROOT);
        return forTargetCache.computeIfAbsent(key, k -> {
            List<ExtensionAsset> matched = new ArrayList<>();
            for (ExtensionAsset ext : extensions.values()) {
                ExtensionAsset.Target t = ext != null ? ext.getTarget() : null;
                if (t == null || !targetType.equals(t.resolvedType())) {
                    continue;
                }
                String rid = t.resolvedId();
                if (rid != null && rid.equalsIgnoreCase(targetId)) {
                    matched.add(ext);
                }
            }
            return ExtensionAsset.sortedForApply(matched);
        });
    }

    // ==================== Read-side apply entry points (consult the folded set) ====================

    /** The station's effective loot: {@code base} plus every {@code Station}-targeted extension's {@code Loot} (design 1.8). */
    @Nullable
    public LootRef applyToStationLoot(@Nonnull String stationId, @Nullable LootRef base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.STATION, stationId);
        return exts.isEmpty() ? base : mergeLoot(base, exts);
    }

    /** An action's effective loot: {@code base} plus every {@code Action}-targeted extension's {@code Loot}. */
    @Nullable
    public LootRef applyToActionLoot(@Nonnull String actionId, @Nullable LootRef base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId);
        return exts.isEmpty() ? base : mergeLoot(base, exts);
    }

    /**
     * The station's effective per-cycle contributions: {@code base} plus every
     * {@code Station}-targeted extension's own {@code PerCycleContributions}.
     */
    @Nullable
    public Contribution[] applyToStationContributions(@Nonnull String stationId, @Nullable Contribution[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.STATION, stationId);
        return exts.isEmpty() ? base : mergeContributions(base, exts);
    }

    /**
     * An action's effective per-cycle contributions: {@code base} plus every
     * {@code Action}-targeted extension's own {@code PerCycleContributions}.
     */
    @Nullable
    public Contribution[] applyToActionContributions(@Nonnull String actionId, @Nullable Contribution[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId);
        return exts.isEmpty() ? base : mergeContributions(base, exts);
    }

    /**
     * The station's effective {@code Puppet} group: {@code base} with every {@code Station}-targeted
     * extension's {@code Puppet} overlaid PER LEAF, in {@link ExtensionAsset#APPLY_ORDER}. Returns
     * {@code base} unchanged when no extension authors one.
     */
    @Nullable
    public Puppet applyToStationPuppet(@Nonnull String stationId, @Nullable Puppet base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.STATION, stationId);
        return exts.isEmpty() ? base : mergePuppet(base, exts);
    }

    /** An action's effective {@code Puppet} group: {@code base} with every {@code Action}-targeted extension's {@code Puppet} overlaid PER LEAF. */
    @Nullable
    public Puppet applyToActionPuppet(@Nonnull String actionId, @Nullable Puppet base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId);
        return exts.isEmpty() ? base : mergePuppet(base, exts);
    }

    /**
     * The station's effective {@code Custody} group: {@code base} with every {@code Station}-targeted
     * extension's {@code Custody} overlaid PER LEAF, in {@link ExtensionAsset#APPLY_ORDER}. An overlay
     * carrying only {@code Display} never clobbers {@code States}/{@code MaxQuantity}/{@code Input}.
     */
    @Nullable
    public Custody applyToStationCustody(@Nonnull String stationId, @Nullable Custody base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.STATION, stationId);
        return exts.isEmpty() ? base : mergeCustody(base, exts);
    }

    /** An action's effective {@code Custody} group: {@code base} with every {@code Action}-targeted extension's {@code Custody} overlaid PER LEAF. */
    @Nullable
    public Custody applyToActionCustody(@Nonnull String actionId, @Nullable Custody base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId);
        return exts.isEmpty() ? base : mergeCustody(base, exts);
    }

    /** A lootable's effective rolls: {@code base} plus every {@code Lootable}-targeted extension's {@code Rolls}. */
    @Nullable
    public Roll[] applyToLootableRolls(@Nonnull String lootableId, @Nullable Roll[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.LOOTABLE, lootableId);
        return exts.isEmpty() ? base : mergeRolls(base, exts);
    }

    /** A roll pool's effective entries: {@code base} plus every {@code RollPool}-targeted extension's {@code Entries}. */
    @Nullable
    public StatRollEntry[] applyToRollPoolEntries(@Nonnull String rollPoolId, @Nullable StatRollEntry[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ROLLPOOL, rollPoolId);
        return exts.isEmpty() ? base : mergeEntries(base, exts);
    }

    // ==================== PURE merge cores (deterministic; unit-tested without a live catalog) ====================

    /**
     * Append every extension's {@code PerCycleContributions} onto {@code base}, in {@code exts}
     * order (already APPLY_ORDER-sorted).
     */
    @Nullable
    static Contribution[] mergeContributions(@Nullable Contribution[] base,
            @Nonnull List<ExtensionAsset> exts) {
        List<Contribution> out = new ArrayList<>();
        appendAll(out, base);
        for (ExtensionAsset ext : exts) {
            appendAll(out, ext.getPerCycleContributions());
        }
        return out.isEmpty() ? base : out.toArray(new Contribution[0]);
    }

    /** Union {@code base}'s lootable refs + inline rolls with every extension's {@code Loot} (append order = APPLY_ORDER). */
    @Nullable
    static LootRef mergeLoot(@Nullable LootRef base, @Nonnull List<ExtensionAsset> exts) {
        List<String> lootables = new ArrayList<>();
        List<Roll> rolls = new ArrayList<>();
        if (base != null) {
            appendAll(lootables, base.getLootables());
            appendAll(rolls, base.getRolls());
        }
        for (ExtensionAsset ext : exts) {
            LootRef l = ext.getLoot();
            if (l != null) {
                appendAll(lootables, l.getLootables());
                appendAll(rolls, l.getRolls());
            }
        }
        if (lootables.isEmpty() && rolls.isEmpty()) {
            return base;
        }
        return LootRef.of(lootables.isEmpty() ? null : lootables.toArray(new String[0]),
                rolls.isEmpty() ? null : rolls.toArray(new Roll[0]));
    }

    /** Append every extension's {@code Conversions} onto {@code base}. */
    @Nullable
    static StationAsset.Conversion[] mergeConversions(@Nullable StationAsset.Conversion[] base,
            @Nonnull List<ExtensionAsset> exts) {
        List<StationAsset.Conversion> out = new ArrayList<>();
        appendAll(out, base);
        for (ExtensionAsset ext : exts) {
            appendAll(out, ext.getConversions());
        }
        return out.isEmpty() ? base : out.toArray(new StationAsset.Conversion[0]);
    }

    /** Append every extension's {@code Rolls} onto {@code base} (Lootable target). */
    @Nullable
    static Roll[] mergeRolls(@Nullable Roll[] base, @Nonnull List<ExtensionAsset> exts) {
        List<Roll> out = new ArrayList<>();
        appendAll(out, base);
        for (ExtensionAsset ext : exts) {
            appendAll(out, ext.getRolls());
        }
        return out.isEmpty() ? base : out.toArray(new Roll[0]);
    }

    /** Append every extension's {@code Entries} onto {@code base} (RollPool target). */
    @Nullable
    static StatRollEntry[] mergeEntries(@Nullable StatRollEntry[] base, @Nonnull List<ExtensionAsset> exts) {
        List<StatRollEntry> out = new ArrayList<>();
        appendAll(out, base);
        for (ExtensionAsset ext : exts) {
            appendAll(out, ext.getEntries());
        }
        return out.isEmpty() ? base : out.toArray(new StatRollEntry[0]);
    }

    // ---------- Puppet / Custody: the PER-LEAF presentation overlays (ExtensionAsset rule 5) ----------

    /**
     * Overlay every extension's {@code Puppet} onto {@code base}, in {@code exts} order (already
     * APPLY_ORDER-sorted, so the later/higher-priority extension wins a same-leaf contest).
     */
    @Nullable
    static Puppet mergePuppet(@Nullable Puppet base, @Nonnull List<ExtensionAsset> exts) {
        Puppet out = base;
        for (ExtensionAsset ext : exts) {
            out = overlayPuppet(out, ext.getPuppet());
        }
        return out;
    }

    /**
     * ONE per-leaf {@code Puppet} overlay (PURE): every leaf of {@code overlay} that is authored
     * wins; every leaf it omits keeps {@code base}'s value, recursively through
     * {@code Hide}/{@code Look}/{@code Look.Model}/{@code Look.Role}/{@code Offset}/{@code Prop}. A
     * null {@code overlay} is the identity; a null {@code base} yields {@code overlay} as-is.
     */
    @Nullable
    static Puppet overlayPuppet(@Nullable Puppet base, @Nullable Puppet overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Puppet.of(
                firstNonNull(overlay.getEnabled(), base.getEnabled()),
                overlayHide(base.getHide(), overlay.getHide()),
                overlayLook(base.getLook(), overlay.getLook()),
                overlayVec3(base.getOffset(), overlay.getOffset()),
                firstNonNull(overlay.getYaw(), base.getYaw()),
                overlayProp(base.getProp(), overlay.getProp()));
    }

    @Nullable
    private static Puppet.Hide overlayHide(@Nullable Puppet.Hide base, @Nullable Puppet.Hide overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Puppet.Hide.of(
                firstNonNull(overlay.getRoute(), base.getRoute()),
                overlayEffectRef(base.getEffect(), overlay.getEffect()));
    }

    /** Per-leaf overlay of the shared {@code EffectRef} group ({@code Id}/{@code DurationMs}). */
    @Nullable
    private static EffectRef overlayEffectRef(@Nullable EffectRef base, @Nullable EffectRef overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return EffectRef.of(
                firstNonNull(overlay.getId(), base.getId()),
                firstNonNull(overlay.getDurationMs(), base.getDurationMs()));
    }

    @Nullable
    private static Puppet.Look overlayLook(@Nullable Puppet.Look base, @Nullable Puppet.Look overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Puppet.Look.of(
                firstNonNull(overlay.getSource(), base.getSource()),
                overlayLookModel(base.getModel(), overlay.getModel()),
                overlayLookRole(base.getRole(), overlay.getRole()));
    }

    @Nullable
    private static Puppet.Model overlayLookModel(@Nullable Puppet.Model base, @Nullable Puppet.Model overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Puppet.Model.of(
                firstNonNull(overlay.getModelId(), base.getModelId()),
                firstNonNull(overlay.getFallbackModelId(), base.getFallbackModelId()));
    }

    @Nullable
    private static Puppet.Role overlayLookRole(@Nullable Puppet.Role base, @Nullable Puppet.Role overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Puppet.Role.of(
                firstNonNull(overlay.getRoleId(), base.getRoleId()),
                firstNonNull(overlay.getSkinSource(), base.getSkinSource()),
                firstNonNull(overlay.getPersist(), base.getPersist()),
                firstNonNull(overlay.getSpeedMps(), base.getSpeedMps()));
    }

    @Nullable
    private static Puppet.Prop overlayProp(@Nullable Puppet.Prop base, @Nullable Puppet.Prop overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Puppet.Prop.of(
                firstNonNull(overlay.getSource(), base.getSource()),
                firstNonNull(overlay.getItemId(), base.getItemId()),
                firstNonNull(overlay.getSlot(), base.getSlot()));
    }

    /**
     * Overlay every extension's {@code Custody} onto {@code base}, in {@code exts} order (already
     * APPLY_ORDER-sorted, so the later/higher-priority extension wins a same-leaf contest).
     */
    @Nullable
    static Custody mergeCustody(@Nullable Custody base, @Nonnull List<ExtensionAsset> exts) {
        Custody out = base;
        for (ExtensionAsset ext : exts) {
            out = overlayCustody(out, ext.getCustody());
        }
        return out;
    }

    /**
     * ONE per-leaf {@code Custody} overlay (PURE). THE load-bearing property: an overlay authoring
     * only {@code Display} carries null {@code MaxQuantity}/{@code SingleFamily}/{@code Input}/
     * {@code States}, and a null leaf keeps the base's value - so re-skinning a station's
     * placed-input visual can never silently disable its custody mechanics. A null {@code overlay}
     * is the identity. NOTE for whoever adds the NEXT {@code Custody} leaf: add it to this factory
     * call in the same change, or an overlay silently drops it.
     */
    @Nullable
    static Custody overlayCustody(@Nullable Custody base, @Nullable Custody overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Custody.of(
                firstNonNull(overlay.getMaxQuantity(), base.getMaxQuantity()),
                firstNonNull(overlay.getSingleFamily(), base.getSingleFamily()),
                overlayInput(base.getInput(), overlay.getInput()),
                overlayStates(base.getStates(), overlay.getStates()),
                overlayDisplay(base.getDisplay(), overlay.getDisplay()));
    }

    /**
     * Per-leaf {@code Custody.Input} overlay. Every {@link ActionInput} route is independently
     * orthogonal ("match = ANY route satisfied"), so overlaying route by route WIDENS acceptance
     * predictably rather than silently swapping one matcher for another.
     */
    @Nullable
    private static ActionInput overlayInput(@Nullable ActionInput base, @Nullable ActionInput overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return ActionInput.of(
                firstNonNull(overlay.getItemId(), base.getItemId()),
                firstNonNull(overlay.getResourceTypeId(), base.getResourceTypeId()),
                firstNonNull(overlay.getTags(), base.getTags()),
                firstNonNull(overlay.getFunction(), base.getFunction()));
    }

    /**
     * Per-leaf {@code Custody.States} overlay. Every state name is an independent nullable knob, so
     * each overlays on its own. NOTE for whoever adds the FOURTH state name: add it here in the same
     * change, or an overlay silently drops it (a leaf missing from this factory call reads as
     * "neither side authored one" and the merged group loses it).
     */
    @Nullable
    private static Custody.States overlayStates(@Nullable Custody.States base, @Nullable Custody.States overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Custody.States.of(
                firstNonNull(overlay.getEmpty(), base.getEmpty()),
                firstNonNull(overlay.getLoaded(), base.getLoaded()),
                firstNonNull(overlay.getWorking(), base.getWorking()));
    }

    @Nullable
    private static Custody.Display overlayDisplay(@Nullable Custody.Display base, @Nullable Custody.Display overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Custody.Display.of(
                overlayVec3(base.getOffset(), overlay.getOffset()),
                firstNonNull(overlay.getScale(), base.getScale()),
                overlayRotation(base.getRotation(), overlay.getRotation()));
    }

    /** Per-leaf overlay of the ONE shared {@code Vec3} group, used at every offset site. */
    @Nullable
    private static Vec3 overlayVec3(@Nullable Vec3 base, @Nullable Vec3 overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Vec3.of(
                firstNonNull(overlay.getX(), base.getX()),
                firstNonNull(overlay.getY(), base.getY()),
                firstNonNull(overlay.getZ(), base.getZ()));
    }

    /** Per-leaf overlay of the ONE shared {@code Rotation} group. */
    @Nullable
    private static Rotation overlayRotation(@Nullable Rotation base, @Nullable Rotation overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return Rotation.of(
                firstNonNull(overlay.getYaw(), base.getYaw()),
                firstNonNull(overlay.getPitch(), base.getPitch()),
                firstNonNull(overlay.getRoll(), base.getRoll()));
    }

    /**
     * THE per-leaf rule, applied at every nesting depth of both presentation overlays: the overlay's
     * own value when it authored one, else the base's. Nothing else decides a leaf. Leaf
     * granularity note (adversarial-verify F9): a MAP-valued leaf ({@code Custody.Input.Tags})
     * replaces WHOLESALE as one leaf, never merged per tag family - the map is the leaf.
     */
    @Nullable
    private static <T> T firstNonNull(@Nullable T overlay, @Nullable T base) {
        return overlay != null ? overlay : base;
    }

    /**
     * Merge NEW actions from every extension onto {@code base} (Station target): the BASE always
     * wins a key collision ({@code EXTENSION_KEY_COLLISION}); among extensions the earlier in
     * APPLY_ORDER wins a new key (later duplicates skipped). Insertion-ordered.
     */
    @Nonnull
    static Map<String, ActionDef> mergeActions(@Nullable Map<String, ActionDef> base,
            @Nonnull List<ExtensionAsset> exts) {
        Map<String, ActionDef> out = new LinkedHashMap<>();
        if (base != null) {
            out.putAll(base);
        }
        for (ExtensionAsset ext : exts) {
            Map<String, ActionDef> add = ext.getActions();
            if (add == null) {
                continue;
            }
            for (Map.Entry<String, ActionDef> e : add.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                out.putIfAbsent(e.getKey(), e.getValue()); // base + earlier extension win a key.
            }
        }
        return out;
    }

    /**
     * Merge ordered step insertions into {@code baseSteps} for {@code actionId} (design 1.8, m2):
     * every extension's {@link ExtensionAsset.StepInsertion} whose {@code Action} matches
     * {@code actionId} (or is omitted) applies in {@code exts} order (APPLY_ORDER) against a live
     * working list, so later extensions can anchor on earlier-inserted step ids. Co-anchored
     * insertions land in APPLY_ORDER. A dangling {@code After}/{@code Before} step id degrades to
     * {@code AtEnd}.
     */
    @Nonnull
    static List<StationStep> mergeSteps(@Nonnull List<StationStep> baseSteps, @Nonnull String actionId,
            @Nonnull List<ExtensionAsset> exts) {
        List<StationStep> working = new ArrayList<>(baseSteps);
        Map<String, Integer> afterOffset = new HashMap<>();
        int atStartOffset = 0;
        for (ExtensionAsset ext : exts) {
            ExtensionAsset.StepInsertion[] insertions = ext.getSteps();
            if (insertions == null) {
                continue;
            }
            for (ExtensionAsset.StepInsertion ins : insertions) {
                if (ins == null || ins.getInsert() == null || ins.getInsert().length == 0) {
                    continue;
                }
                String insAction = ins.getAction();
                if (insAction != null && !insAction.isBlank() && !insAction.equalsIgnoreCase(actionId)) {
                    continue; // targets a different action's program
                }
                List<StationStep> block = new ArrayList<>();
                for (StationStep s : ins.getInsert()) {
                    if (s != null) {
                        block.add(s);
                    }
                }
                if (block.isEmpty()) {
                    continue;
                }
                atStartOffset = insertBlock(working, ins.getAnchor(), block, afterOffset, atStartOffset);
            }
        }
        return working;
    }

    /** Insert {@code block} at {@code anchor}'s resolved position; returns the updated {@code atStartOffset}. */
    private static int insertBlock(@Nonnull List<StationStep> working,
            @Nullable ExtensionAsset.StepInsertion.Anchor anchor, @Nonnull List<StationStep> block,
            @Nonnull Map<String, Integer> afterOffset, int atStartOffset) {
        String placement = anchor != null ? anchor.effectivePlacement() : ExtensionAsset.StepInsertion.Anchor.AT_END;
        String anchorStepId = anchor != null ? anchor.anchorStepId() : null;
        switch (placement) {
            case ExtensionAsset.StepInsertion.Anchor.AT_START -> {
                working.addAll(atStartOffset, block);
                return atStartOffset + block.size();
            }
            case ExtensionAsset.StepInsertion.Anchor.AFTER -> {
                int idx = indexOfStep(working, anchorStepId);
                if (idx < 0) {
                    working.addAll(block); // degrade to AtEnd
                    return atStartOffset;
                }
                int off = afterOffset.getOrDefault(anchorStepId, 0);
                working.addAll(idx + 1 + off, block);
                afterOffset.merge(anchorStepId, block.size(), Integer::sum);
                return atStartOffset;
            }
            case ExtensionAsset.StepInsertion.Anchor.BEFORE -> {
                int idx = indexOfStep(working, anchorStepId);
                if (idx < 0) {
                    working.addAll(block); // degrade to AtEnd
                    return atStartOffset;
                }
                working.addAll(idx, block); // re-finding the anchor keeps prior before-blocks ahead
                return atStartOffset;
            }
            default -> { // AT_END
                working.addAll(block);
                return atStartOffset;
            }
        }
    }

    private static int indexOfStep(@Nonnull List<StationStep> steps, @Nullable String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < steps.size(); i++) {
            StationStep s = steps.get(i);
            if (s != null && stepId.equalsIgnoreCase(s.getId())) {
                return i;
            }
        }
        return -1;
    }

    private static <T> void appendAll(@Nonnull List<T> out, @Nullable T[] arr) {
        if (arr != null) {
            for (T t : arr) {
                if (t != null) {
                    out.add(t);
                }
            }
        }
    }

    /**
     * The payload keys {@code ext} actually authors, in a FIXED declared order (PURE, unit-tested):
     * what the {@code EXTENSION_APPLIED} boot summary enumerates so a server owner can see WHICH
     * kinds of contribution composed onto a target, not just how many extensions did.
     */
    @Nonnull
    static List<String> authoredPayloadKinds(@Nonnull ExtensionAsset ext) {
        List<String> out = new ArrayList<>();
        if (ext.getPerCycleContributions() != null && ext.getPerCycleContributions().length > 0) {
            out.add(ExtensionAsset.PAYLOAD_PER_CYCLE_CONTRIBUTIONS);
        }
        if (ext.getLoot() != null) {
            out.add(ExtensionAsset.PAYLOAD_LOOT);
        }
        if (ext.getActions() != null && !ext.getActions().isEmpty()) {
            out.add(ExtensionAsset.PAYLOAD_ACTIONS);
        }
        if (ext.getConversions() != null && ext.getConversions().length > 0) {
            out.add(ExtensionAsset.PAYLOAD_CONVERSIONS);
        }
        if (ext.getSteps() != null && ext.getSteps().length > 0) {
            out.add(ExtensionAsset.PAYLOAD_STEPS);
        }
        if (ext.getAnchors() != null && !ext.getAnchors().isEmpty()) {
            out.add(ExtensionAsset.PAYLOAD_ANCHORS);
        }
        if (ext.getPuppet() != null) {
            out.add(ExtensionAsset.PAYLOAD_PUPPET);
        }
        if (ext.getCustody() != null) {
            out.add(ExtensionAsset.PAYLOAD_CUSTODY);
        }
        if (ext.getRolls() != null && ext.getRolls().length > 0) {
            out.add(ExtensionAsset.PAYLOAD_ROLLS);
        }
        if (ext.getEntries() != null && ext.getEntries().length > 0) {
            out.add(ExtensionAsset.PAYLOAD_ENTRIES);
        }
        return out;
    }

    private void logAppliedSummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Set<String>> kinds = new LinkedHashMap<>();
        for (ExtensionAsset ext : extensions.values()) {
            ExtensionAsset.Target t = ext != null ? ext.getTarget() : null;
            String type = t != null ? t.resolvedType() : null;
            String id = t != null ? t.resolvedId() : null;
            if (type == null || id == null) {
                continue;
            }
            String key = type + " " + id;
            counts.merge(key, 1, Integer::sum);
            kinds.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(authoredPayloadKinds(ext));
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Set<String> contributed = kinds.get(e.getKey());
            String detail = contributed == null || contributed.isEmpty()
                    ? "" : " [" + String.join(", ", contributed) + "]";
            Log.info("EXTENSION_APPLIED: " + e.getKey() + " <- " + e.getValue() + " extension(s)" + detail);
        }
    }
}
