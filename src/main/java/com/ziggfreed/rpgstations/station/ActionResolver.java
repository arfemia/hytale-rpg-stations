package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.common.match.ItemMatch;

/**
 * Resolves ONE of a station's ordered actions into the flat group view every engine read uses. The
 * ONE choke point, so no call site re-derives resolution logic.
 *
 * <p><b>There is no station-level fallback.</b> A station supplies no defaults; an action's groups
 * come from the action itself, or from the standalone {@link ActionAsset} its {@code Ref} names, and
 * nowhere else. {@code Ref} resolution is a single GROUP-WISE overlay: the inline entry's own group
 * wins when authored, else the {@code Ref} base's, else nothing. A dangling {@code Ref} resolves as
 * if no ref existed (the validator reports {@code ACTION_REF_UNKNOWN}; {@code toggle()} denies
 * gracefully).
 *
 * <p><b>Selection is authored order.</b> {@link #selectAction}/{@link #selectActionByFamily} walk
 * {@link StationAsset#getActions()} front to back and return the FIRST action whose effective
 * {@code Select} is absent (matches anything) or matches the context. A station that authors no
 * actions selects nothing and is inert.
 *
 * <p>The pure core {@link #resolve(StationAsset, String, Function)} takes an injected
 * {@code refLookup} (an {@code ActionAsset} id -&gt; its {@link ActionDef} body, or {@code null}),
 * so {@code Ref} overlay resolution is unit-testable without a live {@link ActionCatalog}. The
 * 2-arg {@link #resolve(StationAsset, String)} convenience wires the live catalog and layers the
 * {@link ExtensionCatalog}'s Action-targeted per-leaf overlays on top.
 */
public final class ActionResolver {

    /** The live Ref lookup: an {@link ActionAsset} id -> its {@link ActionDef} body, or null when unknown. */
    private static final Function<String, ActionDef> CATALOG_REF_LOOKUP = ActionResolver::catalogBody;

    private ActionResolver() {
    }

    @Nullable
    private static ActionDef catalogBody(@Nullable String refId) {
        if (refId == null || refId.isBlank()) {
            return null;
        }
        ActionAsset a = ActionCatalog.getInstance().get(refId);
        return a != null ? a.getBody() : null;
    }

    /**
     * The id one {@code Actions} entry answers to: its authored {@code Id}, else the
     * {@code ActionAsset} id its {@code Ref} names, else its 0-based position in the array. Both
     * fallbacks are stable within one asset, but only an explicit {@code Id} is targetable by an
     * {@code ExtensionAsset} or a step insertion - which is exactly why the validator asks for one.
     */
    @Nonnull
    public static String effectiveActionId(@Nullable ActionDef def, int index) {
        if (def != null) {
            String own = def.getId();
            if (own != null && !own.isBlank()) {
                return own;
            }
            String ref = def.getRef();
            if (ref != null && !ref.isBlank()) {
                return ref;
            }
        }
        return Integer.toString(index);
    }

    /**
     * The station's EFFECTIVE ordered action list: its own, plus any {@code Target:{Station}}
     * {@link com.ziggfreed.rpgstations.asset.ExtensionAsset} appending whole NEW actions. Appended
     * actions land at the END, so a base action always keeps its higher selection priority.
     * Identity-preserving when nothing targets the station, so the zero-extension path costs
     * nothing.
     */
    @Nullable
    public static ActionDef[] effectiveActions(@Nonnull StationAsset asset) {
        String stationId = asset.getId();
        if (stationId == null || stationId.isBlank()) {
            return asset.getActions();
        }
        return ExtensionCatalog.getInstance().applyToStationActions(stationId, asset.getActions());
    }

    /** The station's action ids, in AUTHORED ORDER (which IS selection priority); empty when none. */
    @Nonnull
    public static List<String> actionIds(@Nonnull StationAsset asset) {
        ActionDef[] actions = effectiveActions(asset);
        if (actions == null || actions.length == 0) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(actions.length);
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null) {
                ids.add(effectiveActionId(actions[i], i));
            }
        }
        return ids;
    }

    /** The FIRST authored action's id, or {@code null} for a station with no actions at all. */
    @Nullable
    public static String firstActionId(@Nonnull StationAsset asset) {
        List<String> ids = actionIds(asset);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** The {@code Actions} entry answering to {@code actionId} (case-insensitive), or {@code null}. */
    @Nullable
    public static ActionDef findAction(@Nonnull StationAsset asset, @Nullable String actionId) {
        ActionDef[] actions = effectiveActions(asset);
        if (actions == null || actionId == null || actionId.isBlank()) {
            return null;
        }
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && effectiveActionId(actions[i], i).equalsIgnoreCase(actionId)) {
                return actions[i];
            }
        }
        return null;
    }

    /**
     * The resolved view of {@code actionId} on {@code asset}, using the live {@link ActionCatalog}
     * for {@code Ref} AND the live {@link ExtensionCatalog} for the Action-targeted overlays (the
     * per-leaf {@code Puppet}/{@code Custody}/{@code ContributionScale} plus the keyed
     * {@code Anchors} map). Every live engine read goes through here, so the overlays cover every
     * reader at once.
     */
    @Nonnull
    public static ResolvedAction resolve(@Nonnull StationAsset asset, @Nonnull String actionId) {
        return applyExtensionOverlays(asset, actionId, resolve(asset, actionId, CATALOG_REF_LOOKUP));
    }

    /**
     * The ONE {@code Target:{Action}} identity every Action-targeted extension payload resolves by:
     * the {@code Ref}'d {@link ActionAsset} id when the entry Refs one (an Action target reaches
     * every {@code Ref} user of that action), else the entry's own effective id. {@code null} when
     * no such action exists on this station.
     */
    @Nullable
    public static String actionTargetId(@Nonnull StationAsset asset, @Nonnull String actionId) {
        ActionDef def = findAction(asset, actionId);
        if (def == null) {
            return null;
        }
        String refId = def.hasRef() ? def.getRef() : null;
        return refId != null && !refId.isBlank() ? refId : effectiveActionIdOf(asset, def);
    }

    @Nonnull
    private static String effectiveActionIdOf(@Nonnull StationAsset asset, @Nonnull ActionDef def) {
        ActionDef[] actions = effectiveActions(asset);
        if (actions != null) {
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] == def) {
                    return effectiveActionId(def, i);
                }
            }
        }
        return effectiveActionId(def, 0);
    }

    /**
     * The live {@link ExtensionCatalog} overlay pass over a pure-resolved action. Only
     * ACTION-targeted extensions apply: a station carries no group an extension could overlay any
     * more, so its only payload is a whole new action. Identity-preserving - with no extension
     * targeting this action the merge cores hand back the SAME group objects and the original
     * {@code resolved} is returned untouched.
     *
     * <p>Four groups layer here rather than at each reader: the three per-leaf overlays
     * ({@code Puppet}/{@code Custody}/{@code ContributionScale}) and the keyed {@code Anchors} map,
     * where an extension may declare NEW keys only (the base wins a collision). Anchors are safe to
     * fold at this level precisely because nothing branches on the map being non-empty - it is only
     * ever consulted to resolve a {@code Walk}/{@code At} target - so an added key widens what an
     * inserted step can address without changing which engine path the action takes.
     */
    @Nonnull
    private static ResolvedAction applyExtensionOverlays(@Nonnull StationAsset asset,
            @Nonnull String actionId, @Nonnull ResolvedAction resolved) {
        String targetId = actionTargetId(asset, actionId);
        if (targetId == null) {
            return resolved;
        }
        // The station this action is being resolved ON: what decides whether a station-scoped
        // Target:{Station, Action} extension applies here.
        String stationId = asset.getId();
        ExtensionCatalog exts = ExtensionCatalog.getInstance();
        Puppet puppet = exts.applyToActionPuppet(stationId, targetId, resolved.getPuppet());
        Custody custody = exts.applyToActionCustody(stationId, targetId, resolved.getCustody());
        ContributionScale scale =
                exts.applyToActionContributionScale(stationId, targetId, resolved.getContributionScale());
        Map<String, ActionDef.Anchor> anchors =
                exts.applyToActionAnchors(stationId, targetId, resolved.getAnchors());
        if (puppet == resolved.getPuppet() && custody == resolved.getCustody()
                && scale == resolved.getContributionScale() && anchors == resolved.getAnchors()) {
            return resolved;
        }
        return resolved.with(custody, puppet, scale, anchors);
    }

    /**
     * The PURE resolution core: {@code refLookup} maps a {@code Ref} {@link ActionAsset} id to its
     * {@link ActionDef} body (or {@code null}). Precedence per GROUP: the inline entry's own group,
     * then the {@code Ref} base's. An unknown {@code actionId} resolves to an all-null view carrying
     * only that id. Extension overlays are NOT applied here - this core stays catalog-free for unit
     * tests; the 2-arg live entry above is what layers them.
     */
    @Nonnull
    public static ResolvedAction resolve(@Nonnull StationAsset asset, @Nonnull String actionId,
            @Nonnull Function<String, ActionDef> refLookup) {
        ActionDef def = findAction(asset, actionId);
        ActionDef base = def != null && def.hasRef() ? refLookup.apply(def.getRef()) : null;
        ActionDef.Worker worker = pick(def, base, ActionDef::getWorker);
        Map<String, Presentation> moments = pick(def, base, ActionDef::getMoments);
        return new ResolvedAction(
                actionId,
                pick(def, base, ActionDef::getSelect),
                pick(def, base, ActionDef::getRequires),
                pick(def, base, ActionDef::getTool),
                pick(def, base, ActionDef::getRecipe),
                pick(def, base, ActionDef::getWork),
                pick(def, base, ActionDef::getCustody),
                pick(def, base, ActionDef::getAnchors),
                pick(def, base, ActionDef::getSteps),
                pick(def, base, ActionDef::getBonus),
                pick(def, base, ActionDef::getContributionScale),
                worker != null ? worker.getHold() : null,
                worker != null ? worker.getCamera() : null,
                worker != null ? worker.getAnimation() : null,
                worker != null ? worker.getPuppet() : null,
                moments != null ? StationFlairs.canonicalMomentKeys(moments) : null);
    }

    /**
     * Group pick: the inline entry's own group first, then the {@code Ref} base's. A blank
     * {@link ActionDef} (the {@code Ref}-only case) contributes no override, so the base wins -
     * exactly the "author only the delta" intent.
     */
    @Nullable
    private static <T> T pick(@Nullable ActionDef def, @Nullable ActionDef base,
            @Nonnull Function<ActionDef, T> getter) {
        if (def != null) {
            T v = getter.apply(def);
            if (v != null) {
                return v;
            }
        }
        return base != null ? getter.apply(base) : null;
    }

    /**
     * The effective {@code Steps} for a {@code def} (its own when it authors the group, else its
     * {@code Ref} base's) - the same group-wise {@link #pick} rule {@link #resolve} applies,
     * exposed for callers that hold only the entry and not the station it sits on. {@code null}
     * when neither authors one, which IS the recipe-driven convert-loop shape.
     */
    @Nullable
    public static StationStep[] effectiveStepsOf(@Nonnull ActionDef def) {
        if (def.getSteps() != null) {
            return def.getSteps();
        }
        if (def.hasRef()) {
            ActionDef base = catalogBody(def.getRef());
            return base != null ? base.getSteps() : null;
        }
        return null;
    }

    /** The effective {@link ActionInput} for a {@code def} (its own, else its {@code Ref} base's). */
    @Nullable
    private static ActionInput effectiveSelectOf(@Nonnull ActionDef def) {
        if (def.getSelect() != null) {
            return def.getSelect();
        }
        if (def.hasRef()) {
            ActionDef base = catalogBody(def.getRef());
            return base != null ? base.getSelect() : null;
        }
        return null;
    }

    /**
     * Action selection: the FIRST action in AUTHORED ORDER whose effective {@code Select} (its own,
     * or its {@code Ref} base's) is absent, catch-all, or matches. {@code null} when nothing matches
     * or the station authors no actions.
     */
    @Nullable
    public static String selectAction(@Nonnull StationAsset asset, @Nullable String heldItemId,
            @Nullable String heldResourceTypeId, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        ActionDef[] actions = effectiveActions(asset);
        if (actions == null) {
            return null;
        }
        for (int i = 0; i < actions.length; i++) {
            ActionDef def = actions[i];
            if (def == null) {
                continue;
            }
            ActionInput select = effectiveSelectOf(def);
            if (select == null || select.isCatchAll()
                    || matches(select, heldItemId, heldResourceTypeId, heldTags, heldFunction)) {
                return effectiveActionId(def, i);
            }
        }
        return null;
    }

    /**
     * The live-item-aware sibling of {@link #selectAction}: matches against the held item's FULL
     * {@code ResourceTypeId} FAMILY set instead of a single id.
     */
    @Nullable
    public static String selectActionByFamily(@Nonnull StationAsset asset, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        ActionDef[] actions = effectiveActions(asset);
        if (actions == null) {
            return null;
        }
        for (int i = 0; i < actions.length; i++) {
            ActionDef def = actions[i];
            if (def == null) {
                continue;
            }
            ActionInput select = effectiveSelectOf(def);
            if (select == null || select.isCatchAll()
                    || matchesAnyResourceType(select, heldItemId, heldResourceTypeIds, heldTags, heldFunction)) {
                return effectiveActionId(def, i);
            }
        }
        return null;
    }

    /**
     * Restart-orphan recovery: the FIRST action whose resolved {@link Custody#getStates()}'
     * {@code Loaded} name case-insensitively matches {@code currentStateName}. Resolves through
     * {@link #resolve} so a {@code Ref}'d action's custody (on the base {@link ActionAsset}) is
     * honored. Returns {@code null} when nothing matches.
     */
    @Nullable
    public static String selectActionForBlockState(@Nonnull StationAsset asset, @Nullable String currentStateName) {
        if (currentStateName == null || currentStateName.isBlank()) {
            return null;
        }
        for (String actionId : actionIds(asset)) {
            ResolvedAction resolved = resolve(asset, actionId);
            Custody custody = resolved.getCustody();
            Custody.States states = custody != null ? custody.getStates() : null;
            String loaded = states != null ? states.getLoaded() : null;
            if (loaded != null && loaded.equalsIgnoreCase(currentStateName)) {
                return actionId;
            }
        }
        return null;
    }

    private static boolean matchesAnyResourceType(@Nonnull ActionInput input, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        if (heldResourceTypeIds != null && heldResourceTypeIds.length > 0) {
            for (String rt : heldResourceTypeIds) {
                if (matches(input, heldItemId, rt, heldTags, heldFunction)) {
                    return true;
                }
            }
            return false;
        }
        return matches(input, heldItemId, null, heldTags, heldFunction);
    }

    private static boolean matches(@Nonnull ActionInput input, @Nullable String heldItemId,
            @Nullable String heldResourceTypeId, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        if (ItemMatch.itemId(input.getItemId(), heldItemId)
                || ItemMatch.resourceFamily(input.getResourceTypeId(), heldResourceTypeId)
                || ItemMatch.tags(input.getTags(), heldTags)) {
            return true;
        }
        String wantFunction = input.getFunction();
        return wantFunction != null && !wantFunction.isBlank() && wantFunction.equalsIgnoreCase(heldFunction);
    }

    /**
     * The resolved, FLAT view of one action. Every accessor is the group a {@code station.step}
     * handler / the direct-Java engine path should read - never the raw {@link StationAsset}/
     * {@link ActionDef}/{@link ActionAsset} group directly once an action id is chosen. The
     * {@code Worker}/{@code Moments} nesting is an AUTHORING grouping only; it is flattened here so
     * a reader asks one question per call.
     */
    public static final class ResolvedAction {
        private final String actionId;
        @Nullable private final ActionInput select;
        @Nullable private final Requires requires;
        @Nullable private final StationAsset.Tool tool;
        @Nullable private final StationAsset.Recipe recipe;
        @Nullable private final StationAsset.Work work;
        @Nullable private final Custody custody;
        @Nullable private final Map<String, ActionDef.Anchor> anchors;
        @Nullable private final StationStep[] steps;
        @Nullable private final LootRef bonus;
        @Nullable private final ContributionScale contributionScale;
        @Nullable private final StationAsset.Hold hold;
        @Nullable private final StationAsset.Camera camera;
        @Nullable private final StationAsset.Animation animation;
        @Nullable private final Puppet puppet;
        @Nullable private final Map<String, Presentation> moments;

        ResolvedAction(@Nonnull String actionId, @Nullable ActionInput select, @Nullable Requires requires,
                @Nullable StationAsset.Tool tool, @Nullable StationAsset.Recipe recipe,
                @Nullable StationAsset.Work work, @Nullable Custody custody,
                @Nullable Map<String, ActionDef.Anchor> anchors, @Nullable StationStep[] steps,
                @Nullable LootRef bonus, @Nullable ContributionScale contributionScale,
                @Nullable StationAsset.Hold hold, @Nullable StationAsset.Camera camera,
                @Nullable StationAsset.Animation animation, @Nullable Puppet puppet,
                @Nullable Map<String, Presentation> moments) {
            this.actionId = actionId;
            this.select = select;
            this.requires = requires;
            this.tool = tool;
            this.recipe = recipe;
            this.work = work;
            this.custody = custody;
            this.anchors = anchors;
            this.steps = steps;
            this.bonus = bonus;
            this.contributionScale = contributionScale;
            this.hold = hold;
            this.camera = camera;
            this.animation = animation;
            this.puppet = puppet;
            this.moments = moments;
        }

        /** A copy with the four extension-overlaid groups swapped in; everything else is carried over. */
        @Nonnull
        ResolvedAction with(@Nullable Custody newCustody, @Nullable Puppet newPuppet,
                @Nullable ContributionScale newScale, @Nullable Map<String, ActionDef.Anchor> newAnchors) {
            return new ResolvedAction(actionId, select, requires, tool, recipe, work, newCustody, newAnchors,
                    steps, bonus, newScale, hold, camera, animation, newPuppet, moments);
        }

        /** The id this action answers to (its authored {@code Id}, or the documented fallback). */
        @Nonnull
        public String getActionId() {
            return actionId;
        }

        /** The selection matcher; null = matches any context (and derives custody acceptance from {@code Recipe}). */
        @Nullable
        public ActionInput getSelect() {
            return select;
        }

        /** This action's OWN gate; the station's own {@code Requires} is checked alongside it, never merged in. */
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

        /** Session-scoped placed-input custody; null = classic direct-inventory flow. */
        @Nullable
        public Custody getCustody() {
            return custody;
        }

        /**
         * The declared multi-station anchor map ({@code anchorId -> {Station, MaxRadiusMeters}});
         * null = single-station. Includes any NEW keys an {@code Action}-targeted extension
         * declared (the base wins a collision).
         */
        @Nullable
        public Map<String, ActionDef.Anchor> getAnchors() {
            return anchors;
        }

        /**
         * The AUTHORED step program, or {@code null} when this action wants the implicit program.
         *
         * <p><b>Deliberately NOT extension-merged</b>, unlike every other group on this view. This
         * array is what decides WHICH engine an action runs (authored program versus the
         * recipe-driven convert loop), so folding insertions in here would let an extension flip a
         * convert action into a step program and silently skip its conversion check. Extension
         * {@code Steps} insertions are applied where the program is read for DISPATCH instead, over
         * the base program this accessor returns; an action authoring none has nothing to insert
         * into.
         */
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

        /** {@code Worker.Hold} - the movement lock / mount. */
        @Nullable
        public StationAsset.Hold getHold() {
            return hold;
        }

        /** {@code Worker.Camera} - the camera pull. */
        @Nullable
        public StationAsset.Camera getCamera() {
            return camera;
        }

        /** {@code Worker.Animation} - the work emote plus the per-swing cue layer. */
        @Nullable
        public StationAsset.Animation getAnimation() {
            return animation;
        }

        /** {@code Worker.Puppet} - the puppet presentation route; null = the classic in-body worker. */
        @Nullable
        public Puppet getPuppet() {
            return puppet;
        }

        /**
         * This action's whole {@code Moments} map, already canonicalized to lowercase keys; null =
         * the action authors no moments at all. Handed to the session at engage so every emission
         * resolves against ONE snapshot.
         */
        @Nullable
        public Map<String, Presentation> getMoments() {
            return moments;
        }

        /**
         * The action's authored {@code Moments} entry for {@code momentId} (matched
         * case-insensitively), or null when it authors none. This is the BASE for that moment; a
         * presentation the engine already holds for the same emission - a step's own, a loot
         * floor's - outranks it and is played instead.
         */
        @Nullable
        public Presentation getMoment(@Nonnull String momentId) {
            return moments == null ? null : moments.get(momentId.toLowerCase(Locale.ROOT));
        }

        /** Shorthand for the {@code cycle} moment: the per-completed-cycle cue. */
        @Nullable
        public Presentation getPresentation() {
            return getMoment(StationFlairs.MOMENT_CYCLE);
        }

        /** Shorthand for the {@code completion} moment: the session-end cue. */
        @Nullable
        public Presentation getCompletion() {
            return getMoment(StationFlairs.MOMENT_COMPLETION);
        }
    }
}
