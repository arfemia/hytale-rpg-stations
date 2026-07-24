package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The RUNTIME AUTHORITY for {@link ExtensionAsset}s (scope-2 design 1.8, decision 27): the ONE
 * additive fourth-party composition mechanism. Folded by the plugin's own {@code LoadedAssetsEvent}
 * wiring (always additive, no {@code PackControlAsset} infra); generalizes the {@code FlairCatalog
 * .effectiveFlairsFor} "resolve at read against the folded set, cached per fold generation" pattern.
 *
 * <p>{@link #applyToStationLoot}/{@link #applyToStationXp}/... are the read-side entry points: they
 * consult the extensions targeting a given id (sorted into {@link ExtensionAsset#APPLY_ORDER}) and
 * merge their payloads onto a base per the deterministic rules. The PURE merge cores
 * ({@link #mergeXp}/{@link #mergeLoot}/{@link #mergeConversions}/{@link #mergeRolls}/
 * {@link #mergeEntries}/{@link #mergeActions}/{@link #mergeSteps}) are unit-tested without a live
 * catalog: same input set, any fold order -&gt; identical merged result.
 *
 * <p><b>Merge rules (design 1.8, decision 37):</b> ADDITIVE only; keyed collections (Actions,
 * Anchors) the BASE always wins a collision; unkeyed arrays (Xp, Conversions, Rolls, Entries,
 * LootRef) pure append in {@link ExtensionAsset#APPLY_ORDER}; ordered step insertion applies each
 * extension's insertions in {@code APPLY_ORDER} against a live working list (later extensions can
 * anchor on earlier-inserted step ids), a dangling {@code After}/{@code Before} anchor degrading to
 * {@code AtEnd}.
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

    /** The station's effective work-Xp: {@code base} plus every {@code Station}-targeted extension's {@code Xp}. */
    @Nullable
    public StationAsset.WorkXp[] applyToStationXp(@Nonnull String stationId, @Nullable StationAsset.WorkXp[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.STATION, stationId);
        return exts.isEmpty() ? base : mergeXp(base, exts);
    }

    /** An action's effective work-Xp: {@code base} plus every {@code Action}-targeted extension's {@code Xp}. */
    @Nullable
    public StationAsset.WorkXp[] applyToActionXp(@Nonnull String actionId, @Nullable StationAsset.WorkXp[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId);
        return exts.isEmpty() ? base : mergeXp(base, exts);
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

    /** Append every extension's {@code Xp} onto {@code base}, in {@code exts} order (already APPLY_ORDER-sorted). */
    @Nullable
    static StationAsset.WorkXp[] mergeXp(@Nullable StationAsset.WorkXp[] base,
            @Nonnull List<ExtensionAsset> exts) {
        List<StationAsset.WorkXp> out = new ArrayList<>();
        appendAll(out, base);
        for (ExtensionAsset ext : exts) {
            appendAll(out, ext.getXp());
        }
        return out.isEmpty() ? base : out.toArray(new StationAsset.WorkXp[0]);
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

    private void logAppliedSummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ExtensionAsset ext : extensions.values()) {
            ExtensionAsset.Target t = ext != null ? ext.getTarget() : null;
            String type = t != null ? t.resolvedType() : null;
            String id = t != null ? t.resolvedId() : null;
            if (type == null || id == null) {
                continue;
            }
            counts.merge(type + " " + id, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Log.info("EXTENSION_APPLIED: " + e.getKey() + " <- " + e.getValue() + " extension(s)");
        }
    }
}
