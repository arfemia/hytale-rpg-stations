package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.LootRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The resolution of a station's effective per-action groups (design 9.1 + scope-2 1.5): the
 * WHOLE-GROUP override rule ("an action authoring a group replaces the station-level group
 * wholesale; omitting inherits it"), NOW composed with the scope-2 {@code Ref} attachment route.
 * The ONE choke point every group read goes through, so no call site re-derives the fallback logic.
 *
 * <p><b>{@code Ref} resolution (scope-2 1.5):</b> when an inline {@code Actions} entry authors a
 * {@code Ref}, the named standalone {@link ActionAsset}'s body is the BASE, and any OTHER group
 * authored on the inline entry overlays it group-wise (the SAME whole-group-replace semantics
 * applied twice: {@code Ref}-base -&gt; inline overlay -&gt; station default). A dangling {@code Ref}
 * resolves as if no ref existed (every group falls back to the inline entry, then the station
 * default); the validator reports {@code ACTION_REF_UNKNOWN} and {@code toggle()} denies gracefully.
 *
 * <p>The pure core {@link #resolve(StationAsset, String, Function)} takes an injected
 * {@code refLookup} (an {@code ActionAsset} id -&gt; its {@link ActionDef} body, or {@code null}),
 * so Ref+overlay resolution is unit-testable without a live {@link ActionCatalog}. The 2-arg
 * {@link #resolve(StationAsset, String)} convenience wires the live catalog; likewise the diegetic
 * selection helpers consult the catalog only to resolve a {@code Ref}'d action's effective
 * {@link ActionInput} (an empty catalog degrades a Ref'd action to a catch-all, never a throw).
 */
public final class ActionResolver {

    /** The one action id phase 1 ever forwarded; still the implicit default id in phase 2/scope-2. */
    public static final String ACTION_WORK = "work";

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
     * The station's action ids, in authored order, or {@code List.of(ACTION_WORK)} when
     * {@link StationAsset#getActions()} is null/empty (the implicit single-action default).
     */
    @Nonnull
    public static List<String> actionIds(@Nonnull StationAsset asset) {
        Map<String, ActionDef> actions = asset.getActions();
        if (actions == null || actions.isEmpty()) {
            return List.of(ACTION_WORK);
        }
        return new ArrayList<>(actions.keySet());
    }

    /** The whole-group-override-resolved view of {@code actionId} on {@code asset}, using the live {@link ActionCatalog} for {@code Ref}. */
    @Nonnull
    public static ResolvedAction resolve(@Nonnull StationAsset asset, @Nonnull String actionId) {
        return resolve(asset, actionId, CATALOG_REF_LOOKUP);
    }

    /**
     * The PURE resolution core (design 9.1 + scope-2 1.5): {@code refLookup} maps a {@code Ref}
     * {@link ActionAsset} id to its {@link ActionDef} body (or {@code null}). Precedence per group:
     * inline entry override -&gt; {@code Ref} base override -&gt; station-level default (input/steps/
     * anchors have no station default). An unknown {@code actionId} against an asset that DOES
     * author an {@code Actions} map resolves as if no override existed for it.
     */
    @Nonnull
    public static ResolvedAction resolve(@Nonnull StationAsset asset, @Nonnull String actionId,
            @Nonnull Function<String, ActionDef> refLookup) {
        Map<String, ActionDef> actions = asset.getActions();
        ActionDef def = actions != null ? actions.get(actionId) : null;
        ActionDef base = def != null && def.hasRef() ? refLookup.apply(def.getRef()) : null;
        return new ResolvedAction(
                actionId,
                pick(def, base, ActionDef::getInput, null),
                pick(def, base, ActionDef::getCustody, asset.getCustody()),
                pick(def, base, ActionDef::getPuppet, asset.getPuppet()),
                pick(def, base, ActionDef::getWork, asset.getWork()),
                pick(def, base, ActionDef::getRecipe, asset.getRecipe()),
                pick(def, base, ActionDef::getTool, asset.getTool()),
                pick(def, base, ActionDef::getHold, asset.getHold()),
                pick(def, base, ActionDef::getCamera, asset.getCamera()),
                pick(def, base, ActionDef::getAnimation, asset.getAnimation()),
                pick(def, base, ActionDef::getPresentation, asset.getPresentation()),
                pick(def, base, ActionDef::getCompletion, asset.getCompletion()),
                pick(def, base, ActionDef::getLoot, asset.getLoot()),
                pick(def, base, ActionDef::getRequires, asset.getRequires()),
                pick(def, base, ActionDef::getSteps, null));
    }

    /**
     * Group pick: the inline entry's own override first, then the {@code Ref} base's override, then
     * the station default. A blank {@link ActionDef} (the {@code Ref}-only case) contributes no
     * override, so the base wins - exactly the "author only the delta" intent.
     */
    @Nullable
    private static <T> T pick(@Nullable ActionDef def, @Nullable ActionDef base,
            @Nonnull Function<ActionDef, T> getter, @Nullable T stationDefault) {
        if (def != null) {
            T v = getter.apply(def);
            if (v != null) {
                return v;
            }
        }
        if (base != null) {
            T v = getter.apply(base);
            if (v != null) {
                return v;
            }
        }
        return stationDefault;
    }

    /** The effective {@link ActionInput} for a {@code def} (its own, else its {@code Ref} base's), consulting the live catalog. */
    @Nullable
    private static ActionInput effectiveInputOf(@Nonnull ActionDef def) {
        if (def.getInput() != null) {
            return def.getInput();
        }
        if (def.hasRef()) {
            ActionDef base = catalogBody(def.getRef());
            return base != null ? base.getInput() : null;
        }
        return null;
    }

    /**
     * Diegetic action selection (design 9.1): the FIRST action (authored order) whose effective
     * {@link ActionInput} (its own, or its {@code Ref} base's - see {@link #effectiveInputOf})
     * matches, or a catch-all. Returns {@code null} when nothing matches. A station with no {@code
     * Actions} map always resolves to {@link #ACTION_WORK}.
     */
    @Nullable
    public static String selectAction(@Nonnull StationAsset asset, @Nullable String heldItemId,
            @Nullable String heldResourceTypeId, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        Map<String, ActionDef> actions = asset.getActions();
        if (actions == null || actions.isEmpty()) {
            return ACTION_WORK;
        }
        for (Map.Entry<String, ActionDef> e : actions.entrySet()) {
            ActionDef def = e.getValue();
            ActionInput input = def != null ? effectiveInputOf(def) : null;
            if (input == null || input.isCatchAll()
                    || matches(input, heldItemId, heldResourceTypeId, heldTags, heldFunction)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * The live-item-aware sibling of {@link #selectAction} (phase 2 leg E): matches against the held
     * item's FULL {@code ResourceTypeId} FAMILY set instead of a single id. Uses the effective
     * {@link ActionInput} (its own, or its {@code Ref} base's).
     */
    @Nullable
    public static String selectActionByFamily(@Nonnull StationAsset asset, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        Map<String, ActionDef> actions = asset.getActions();
        if (actions == null || actions.isEmpty()) {
            return ACTION_WORK;
        }
        for (Map.Entry<String, ActionDef> e : actions.entrySet()) {
            ActionDef def = e.getValue();
            ActionInput input = def != null ? effectiveInputOf(def) : null;
            if (input == null || input.isCatchAll()
                    || matchesAnyResourceType(input, heldItemId, heldResourceTypeIds, heldTags, heldFunction)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Restart-orphan recovery (R5 fix): the FIRST action whose resolved {@link Custody#getStates()}'
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
        String wantItem = input.getItemId();
        if (wantItem != null && !wantItem.isBlank() && wantItem.equalsIgnoreCase(heldItemId)) {
            return true;
        }
        String wantResource = input.getResourceTypeId();
        if (wantResource != null && !wantResource.isBlank() && wantResource.equalsIgnoreCase(heldResourceTypeId)) {
            return true;
        }
        String wantFunction = input.getFunction();
        if (wantFunction != null && !wantFunction.isBlank() && wantFunction.equalsIgnoreCase(heldFunction)) {
            return true;
        }
        Map<String, String[]> wantTags = input.getTags();
        if (wantTags != null && !wantTags.isEmpty() && heldTags != null && !heldTags.isEmpty()) {
            for (Map.Entry<String, String[]> req : wantTags.entrySet()) {
                String[] have = heldTags.get(req.getKey());
                if (have == null || req.getValue() == null) {
                    continue;
                }
                for (String want : req.getValue()) {
                    for (String h : have) {
                        if (want != null && want.equalsIgnoreCase(h)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The resolved, whole-group-overridden view of one action. Every accessor is the group a
     * {@code station.step} handler / the direct-Java engine path should read - never the raw
     * {@link StationAsset}/{@link ActionDef}/{@link ActionAsset} group directly once an action id is
     * chosen.
     */
    public static final class ResolvedAction {
        private final String actionId;
        @Nullable private final ActionInput input;
        @Nullable private final Custody custody;
        @Nullable private final Puppet puppet;
        @Nullable private final StationAsset.Work work;
        @Nullable private final StationAsset.Recipe recipe;
        @Nullable private final StationAsset.Tool tool;
        @Nullable private final StationAsset.Hold hold;
        @Nullable private final StationAsset.Camera camera;
        @Nullable private final StationAsset.Animation animation;
        @Nullable private final Presentation presentation;
        @Nullable private final Presentation completion;
        @Nullable private final LootRef loot;
        @Nullable private final Requires requires;
        @Nullable private final StationStep[] steps;

        ResolvedAction(@Nonnull String actionId, @Nullable ActionInput input, @Nullable Custody custody,
                @Nullable Puppet puppet, @Nullable StationAsset.Work work,
                @Nullable StationAsset.Recipe recipe, @Nullable StationAsset.Tool tool,
                @Nullable StationAsset.Hold hold, @Nullable StationAsset.Camera camera,
                @Nullable StationAsset.Animation animation, @Nullable Presentation presentation,
                @Nullable Presentation completion, @Nullable LootRef loot, @Nullable Requires requires,
                @Nullable StationStep[] steps) {
            this.actionId = actionId;
            this.input = input;
            this.custody = custody;
            this.puppet = puppet;
            this.work = work;
            this.recipe = recipe;
            this.tool = tool;
            this.hold = hold;
            this.camera = camera;
            this.animation = animation;
            this.presentation = presentation;
            this.completion = completion;
            this.loot = loot;
            this.requires = requires;
            this.steps = steps;
        }

        @Nonnull
        public String getActionId() {
            return actionId;
        }

        @Nullable
        public ActionInput getInput() {
            return input;
        }

        /** Session-scoped placed-input custody (design 9.4); null = classic direct-inventory flow. */
        @Nullable
        public Custody getCustody() {
            return custody;
        }

        /** The puppet presentation route (round-4 design); null = the classic in-body worker. */
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

        /** The per-action conditional-loot override (a {@link LootRef}, scope-2); null = the station default. */
        @Nullable
        public LootRef getLoot() {
            return loot;
        }

        @Nullable
        public Requires getRequires() {
            return requires;
        }

        /** The authored step program, or {@code null} when this action wants the implicit program. */
        @Nullable
        public StationStep[] getSteps() {
            return steps;
        }
    }
}
