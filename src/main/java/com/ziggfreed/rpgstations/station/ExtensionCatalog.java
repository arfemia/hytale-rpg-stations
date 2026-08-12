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
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.common.loot.stamp.StatRollEntry;
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
 * <p>{@link #applyToActionBonus}/{@link #applyToActionContributions}/... are the read-side entry
 * points: they consult the extensions targeting a given id (sorted into
 * {@link ExtensionAsset#APPLY_ORDER}) and merge their payloads onto a base per the deterministic
 * rules. The PURE merge cores
 * ({@link #mergeContributions}/{@link #mergeLoot}/{@link #mergeConversions}/{@link #mergeRolls}/
 * {@link #mergeEntries}/{@link #mergeActions}/{@link #mergeAnchors}/{@link #mergeSteps}) are
 * unit-tested without a live catalog: same input set, any fold order -&gt; identical merged result.
 *
 * <p><b>Where each payload is applied</b> (every one of them IS, there is no decode-only payload):
 * {@code Puppet}/{@code Custody}/{@code ContributionScale}/{@code Anchors} layer inside
 * {@code ActionResolver}'s own resolve choke point, so every reader of a {@code ResolvedAction}
 * sees them at once; {@code Bonus}/{@code PerCycleContributions} apply at {@code StationService}'s
 * per-cycle read sites; {@code Steps} applies where a session's authored program is read for
 * dispatch; {@code Conversions} applies inside {@code StationCatalog.resolvedConversions} before
 * that derivation is cached; {@code Actions} applies in {@code ActionResolver.effectiveActions};
 * {@code Rolls} applies where {@code loot.LootEngine.resolveRolls} reads a referenced lootable
 * table, and {@code Entries} where {@code StampCapEngine} gathers a Stamp step's candidate pool
 * entries. Those last two read their catalog per call and derive nothing, so unlike
 * {@code Conversions} they need no cache-invalidation companion.
 *
 * <p><b>The station context ({@code stationId} on every {@code applyToAction*}).</b> An
 * {@link ExtensionAsset.Target} may SCOPE an Action target to one station
 * ({@code {Station, Action}}), so "which extensions target this action" is only answerable
 * together with "on which station is it being read". Every Action-targeted call site therefore
 * threads the station it resolved the action from, and {@link #extensionsFor} keys its cache on
 * that context as well - a bare target still matches every station, a scoped one only its own.
 *
 * <p><b>Merge rules (design 1.8, decision 37):</b> ADDITIVE only; keyed collections (Actions,
 * Anchors) the BASE always wins a collision; unkeyed arrays (PerCycleContributions, Conversions,
 * Rolls, Entries, LootRef) pure append in {@link ExtensionAsset#APPLY_ORDER}; ordered step insertion applies each
 * extension's insertions in {@code APPLY_ORDER} against a live working list (later extensions can
 * anchor on earlier-inserted step ids), a dangling {@code After}/{@code Before} anchor degrading to
 * {@code AtEnd}.
 *
 * <p><b>The per-leaf overlays ({@code Puppet}/{@code Custody}/{@code ContributionScale},
 * {@link ExtensionAsset}'s rule 5)</b> are the NON-collection payload shape, so they merge PER LEAF
 * instead of appending:
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
     *
     * <p>Also drops {@link StationCatalog}'s derived-conversion cache, which is load-bearing rather
     * than defensive: {@link #applyToActionConversions} is folded in BEFORE that cache stores an
     * entry, and boot fold order between the Stations store and the Extensions store is not
     * guaranteed - without this, an extension folded after the first conversion resolve would be
     * silently dropped for the rest of the uptime.
     */
    public void fold(@Nonnull Map<String, ExtensionAsset> layer, boolean replace) {
        if (replace) {
            extensions.clear();
        }
        extensions.putAll(layer);
        forTargetCache.clear();
        StationCatalog.getInstance().invalidateResolvedConversions();
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
     * The extensions targeting {@code (targetType, targetId)} with NO station context, sorted into
     * {@link ExtensionAsset#APPLY_ORDER}. The Station/Lootable/RollPool entry point - a scoped
     * {@code {Station, Action}} target can never match here, since it resolves as an Action target
     * and this overload names no station to match its scope against.
     */
    @Nonnull
    public List<ExtensionAsset> extensionsFor(@Nonnull String targetType, @Nullable String targetId) {
        return extensionsFor(targetType, targetId, null);
    }

    /**
     * The extensions targeting {@code (targetType, targetId)} in the context of
     * {@code stationId}, sorted into {@link ExtensionAsset#APPLY_ORDER}. Case-insensitive on both
     * ids. Cached per fold generation (the cache clears on {@link #fold}).
     *
     * <p>{@code stationId} is what makes the scoped {@code Target:{Station, Action}} form
     * resolvable: a BARE target matches any context (its whole point is reaching every station that
     * resolves the id), a SCOPED one only its own station. The cache key therefore carries the
     * station context too - two stations sharing one {@code Ref}'d action legitimately see
     * DIFFERENT extension lists, and a single key per {@code (type, id)} would hand the second
     * station whichever list the first one warmed.
     */
    @Nonnull
    public List<ExtensionAsset> extensionsFor(@Nonnull String targetType, @Nullable String targetId,
            @Nullable String stationId) {
        if (targetId == null || targetId.isBlank()) {
            return List.of();
        }
        String key = targetType + "::" + targetId.toLowerCase(Locale.ROOT)
                + "::" + (stationId == null ? "" : stationId.toLowerCase(Locale.ROOT));
        return forTargetCache.computeIfAbsent(key, k -> {
            List<ExtensionAsset> matched = new ArrayList<>();
            for (ExtensionAsset ext : extensions.values()) {
                ExtensionAsset.Target t = ext != null ? ext.getTarget() : null;
                if (t == null || !targetType.equals(t.resolvedType())) {
                    continue;
                }
                String rid = t.resolvedId();
                if (rid != null && rid.equalsIgnoreCase(targetId) && t.matchesStationScope(stationId)) {
                    matched.add(ext);
                }
            }
            return ExtensionAsset.sortedForApply(matched);
        });
    }

    // ==================== Read-side apply entry points (consult the folded set) ====================
    //
    // Every Action-targeted applyTo* below threads the station the action is being read ON
    // (stationId, nullable only when a caller genuinely has none), because that is what decides
    // whether a SCOPED Target:{Station, Action} extension applies. Each stays identity-preserving
    // with no extension in play, so the zero-extension path costs nothing.

    /** An action's effective {@code Bonus}: {@code base} plus every matching extension's own. */
    @Nullable
    public LootRef applyToActionBonus(@Nullable String stationId, @Nonnull String actionId, @Nullable LootRef base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeLoot(base, exts);
    }

    /**
     * An action's effective per-cycle contributions: {@code base} plus every matching extension's
     * own {@code PerCycleContributions}.
     */
    @Nullable
    public Contribution[] applyToActionContributions(@Nullable String stationId, @Nonnull String actionId,
            @Nullable Contribution[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeContributions(base, exts);
    }

    /** An action's effective {@code Puppet} group: {@code base} with every matching extension's {@code Puppet} overlaid PER LEAF. */
    @Nullable
    public Puppet applyToActionPuppet(@Nullable String stationId, @Nonnull String actionId, @Nullable Puppet base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergePuppet(base, exts);
    }

    /** An action's effective {@code Custody} group: {@code base} with every matching extension's {@code Custody} overlaid PER LEAF. */
    @Nullable
    public Custody applyToActionCustody(@Nullable String stationId, @Nonnull String actionId, @Nullable Custody base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeCustody(base, exts);
    }

    /**
     * An action's effective {@code ContributionScale}: {@code base} with every matching extension's
     * own overlaid PER LEAF, so an extension authoring only {@code Floors} keeps the base action's
     * {@code Factors}.
     */
    @Nullable
    public ContributionScale applyToActionContributionScale(@Nullable String stationId, @Nonnull String actionId,
            @Nullable ContributionScale base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeContributionScale(base, exts);
    }

    /**
     * An action's effective {@code Recipe.Conversions}: {@code base} plus every matching extension's
     * own {@code Conversions}, appended in {@link ExtensionAsset#APPLY_ORDER}. {@code base} is the
     * already-derived array (authored conversions plus any {@code FromCrafting}-derived ones), so an
     * appended conversion always sorts AFTER everything the base action can already make - it can
     * never displace the base's first-authored default output category.
     */
    @Nullable
    public StationAsset.Conversion[] applyToActionConversions(@Nullable String stationId, @Nonnull String actionId,
            @Nullable StationAsset.Conversion[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeConversions(base, exts);
    }

    /**
     * An action's effective step program: {@code base} with every matching extension's
     * {@code Steps} insertions applied in {@link ExtensionAsset#APPLY_ORDER}.
     *
     * <p><b>{@code base} is the action's OWN authored program, never a synthesized one.</b> An
     * insertion ADDS beats to a program the action already authors; it can never turn a
     * recipe-driven convert action into a step-programmed one, because the flavor decision
     * ({@code Steps} authored or not) is read straight off the action and stays base-owned. That is
     * what "additive only" means for this payload: an extension changes what a ritual DOES, never
     * which engine runs it.
     */
    @Nonnull
    public List<StationStep> applyToActionSteps(@Nullable String stationId, @Nonnull String actionId,
            @Nonnull List<StationStep> base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeSteps(base, exts);
    }

    /**
     * An action's effective declared-anchor map: {@code base} plus every matching extension's NEW
     * anchor keys. The BASE wins a key collision (the entry is skipped), matching every other keyed
     * collection here.
     */
    @Nullable
    public Map<String, ActionDef.Anchor> applyToActionAnchors(@Nullable String stationId, @Nonnull String actionId,
            @Nullable Map<String, ActionDef.Anchor> base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.ACTION, actionId, stationId);
        return exts.isEmpty() ? base : mergeAnchors(base, exts);
    }

    /** The station's effective action list: {@code base} plus every {@code Station}-targeted extension's NEW actions. */
    @Nullable
    public ActionDef[] applyToStationActions(@Nonnull String stationId, @Nullable ActionDef[] base) {
        List<ExtensionAsset> exts = extensionsFor(ExtensionAsset.Target.STATION, stationId);
        return exts.isEmpty() ? base : mergeActions(base, exts);
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

    /** Union {@code base}'s lootable refs + inline rolls with every extension's {@code Bonus} (append order = APPLY_ORDER). */
    @Nullable
    static LootRef mergeLoot(@Nullable LootRef base, @Nonnull List<ExtensionAsset> exts) {
        List<String> lootables = new ArrayList<>();
        List<Roll> rolls = new ArrayList<>();
        if (base != null) {
            appendAll(lootables, base.getLootables());
            appendAll(rolls, base.getRolls());
        }
        for (ExtensionAsset ext : exts) {
            LootRef l = ext.getBonus();
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
     * {@code Hide}/{@code Look}/{@code Look.Model}/{@code Look.Role}/{@code Offset}/
     * {@code Rotation}/{@code Prop} - so an overlay authoring only {@code Rotation.Pitch} keeps the
     * base's {@code Rotation.Yaw} and {@code .Roll}. A null {@code overlay} is the identity; a null
     * {@code base} yields {@code overlay} as-is.
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
                overlayRotation(base.getRotation(), overlay.getRotation()),
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
                firstNonNull(overlay.getFallbackModelId(), base.getFallbackModelId()),
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
        return Puppet.Model.of(firstNonNull(overlay.getModelId(), base.getModelId()));
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
     * Overlay every extension's {@code ContributionScale} onto {@code base}, in {@code exts} order
     * (already APPLY_ORDER-sorted, so the later/higher-priority extension wins a same-leaf contest).
     */
    @Nullable
    static ContributionScale mergeContributionScale(@Nullable ContributionScale base,
            @Nonnull List<ExtensionAsset> exts) {
        ContributionScale out = base;
        for (ExtensionAsset ext : exts) {
            out = overlayContributionScale(out, ext.getContributionScale());
        }
        return out;
    }

    /**
     * ONE per-leaf {@code ContributionScale} overlay (PURE): an overlay authoring only
     * {@code Floors} keeps the base's {@code Factors}, and vice versa - the same
     * {@link #firstNonNull} rule the two presentation overlays use. Each leaf is a whole array, so
     * an authored one replaces wholesale (arrays are leaves here, never merged element-wise).
     */
    @Nullable
    static ContributionScale overlayContributionScale(@Nullable ContributionScale base,
            @Nullable ContributionScale overlay) {
        if (overlay == null) {
            return base;
        }
        if (base == null) {
            return overlay;
        }
        return ContributionScale.of(
                firstNonNull(overlay.getFactors(), base.getFactors()),
                firstNonNull(overlay.getFloors(), base.getFloors()));
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
     * Append every extension's NEW actions onto {@code base} (Station target): APPENDED, so a base
     * action always keeps its higher selection priority, and the BASE wins an {@code Id} collision
     * ({@code EXTENSION_KEY_COLLISION}, entry skipped). Among extensions the LATER in APPLY_ORDER
     * wins a contested id - its entry REPLACES the earlier claimant's, in the slot (array position)
     * the first claim established - the same later-wins rule every other contest in this schema
     * follows (overlay leaf contests, ladder floor ties, the pack-over-defaults layer order).
     */
    @Nonnull
    static ActionDef[] mergeActions(@Nullable ActionDef[] base, @Nonnull List<ExtensionAsset> exts) {
        List<ActionDef> out = new ArrayList<>();
        Set<String> baseIds = new LinkedHashSet<>();
        Map<String, Integer> extSlotById = new LinkedHashMap<>();
        if (base != null) {
            for (int i = 0; i < base.length; i++) {
                if (base[i] == null) {
                    continue;
                }
                out.add(base[i]);
                baseIds.add(ActionResolver.effectiveActionId(base[i], i).toLowerCase(Locale.ROOT));
            }
        }
        for (ExtensionAsset ext : exts) {
            ActionDef[] add = ext.getActions();
            if (add == null) {
                continue;
            }
            for (int i = 0; i < add.length; i++) {
                ActionDef def = add[i];
                if (def == null) {
                    continue;
                }
                String id = ActionResolver.effectiveActionId(def, out.size()).toLowerCase(Locale.ROOT);
                if (baseIds.contains(id)) {
                    continue;
                }
                Integer slot = extSlotById.get(id);
                if (slot != null) {
                    out.set(slot, def);
                } else {
                    extSlotById.put(id, out.size());
                    out.add(def);
                }
            }
        }
        return out.toArray(new ActionDef[0]);
    }

    /**
     * Fold every extension's NEW anchor declarations into {@code base} (Action target): a
     * {@link LinkedHashMap} preserving the base's own declaration order first, then each
     * extension's new keys in APPLY_ORDER. The BASE always wins a key collision
     * ({@code EXTENSION_KEY_COLLISION}, entry skipped) and among extensions the LATER in
     * APPLY_ORDER wins a contested key - its anchor REPLACES the earlier claimant's VALUE while
     * the first claim keeps the slot's map position and authored spelling - the same later-wins
     * rule {@link #mergeActions} applies, so one contest rule holds across the whole schema.
     * Case-insensitive on the key, so an extension cannot shadow a base anchor by re-casing it.
     * A null/empty base with no extension anchors returns {@code base} unchanged (identity), so
     * the zero-extension path costs nothing.
     */
    @Nullable
    static Map<String, ActionDef.Anchor> mergeAnchors(@Nullable Map<String, ActionDef.Anchor> base,
            @Nonnull List<ExtensionAsset> exts) {
        Map<String, ActionDef.Anchor> out = new LinkedHashMap<>();
        Set<String> baseKeys = new LinkedHashSet<>();
        Map<String, String> extKeyByLower = new LinkedHashMap<>();
        if (base != null) {
            for (Map.Entry<String, ActionDef.Anchor> e : base.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                    continue;
                }
                out.put(e.getKey(), e.getValue());
                baseKeys.add(e.getKey().toLowerCase(Locale.ROOT));
            }
        }
        for (ExtensionAsset ext : exts) {
            Map<String, ActionDef.Anchor> add = ext.getAnchors();
            if (add == null) {
                continue;
            }
            for (Map.Entry<String, ActionDef.Anchor> e : add.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                    continue;
                }
                String lower = e.getKey().toLowerCase(Locale.ROOT);
                if (baseKeys.contains(lower)) {
                    continue;
                }
                String slotKey = extKeyByLower.get(lower);
                if (slotKey != null) {
                    out.put(slotKey, e.getValue());
                } else {
                    extKeyByLower.put(lower, e.getKey());
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out.isEmpty() ? base : out;
    }

    /**
     * Merge ordered step insertions into {@code baseSteps} (design 1.8, m2): every extension's
     * {@link ExtensionAsset.StepInsertion} applies in {@code exts} order (APPLY_ORDER) against a
     * live working list, so later extensions can anchor on earlier-inserted step ids. Co-anchored
     * insertions land in APPLY_ORDER. A dangling {@code After}/{@code Before} step id degrades to
     * {@code AtEnd}.
     *
     * <p>WHICH action's program these insertions reach is decided upstream by the extension's own
     * {@code Target} (bare {@code {Action}} everywhere that id resolves, {@code {Station, Action}}
     * on one station's copy), so an insertion carries no action guard of its own and every
     * insertion in {@code exts} applies here.
     */
    @Nonnull
    static List<StationStep> mergeSteps(@Nonnull List<StationStep> baseSteps,
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

    /**
     * Insert {@code block} at {@code anchor}'s resolved position; returns the updated
     * {@code atStartOffset}.
     *
     * <p>The {@code afterOffset} ledger (what keeps two co-anchored After insertions in APPLY_ORDER
     * instead of stacking the later one in front of the earlier) is keyed on the LOWERCASED anchor
     * id, because {@link #indexOfStep} matches the anchor case-INSENSITIVELY: keying the ledger on
     * the raw authored id filed {@code "base1"} and {@code "Base1"} as two anchors, and the second
     * extension then read an offset of 0 and landed AHEAD of the first one's block.
     */
    private static int insertBlock(@Nonnull List<StationStep> working,
            @Nullable ExtensionAsset.StepInsertion.Anchor anchor, @Nonnull List<StationStep> block,
            @Nonnull Map<String, Integer> afterOffset, int atStartOffset) {
        String placement = anchor != null ? anchor.effectivePlacement() : ExtensionAsset.StepInsertion.Anchor.AT_END;
        String anchorStepId = anchor != null ? anchor.anchorStepId() : null;
        String anchorKey = anchorStepId == null ? null : anchorStepId.toLowerCase(Locale.ROOT);
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
                int off = afterOffset.getOrDefault(anchorKey, 0);
                working.addAll(idx + 1 + off, block);
                afterOffset.merge(anchorKey, block.size(), Integer::sum);
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
        if (ext.getBonus() != null) {
            out.add(ExtensionAsset.PAYLOAD_BONUS);
        }
        if (ext.getContributionScale() != null) {
            out.add(ExtensionAsset.PAYLOAD_CONTRIBUTION_SCALE);
        }
        if (ext.getActions() != null && ext.getActions().length > 0) {
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
            // A scoped target renders its qualifier, so a server owner can tell "this action
            // everywhere" from "this action on that one station" in the boot log.
            String scope = t.scopedStation();
            String key = type + " " + id + (scope != null ? " on Station " + scope : "");
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
