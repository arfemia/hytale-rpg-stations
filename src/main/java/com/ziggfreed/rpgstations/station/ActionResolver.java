package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * PURE, unit-testable resolution of a station's effective per-action groups (design section 9.1):
 * the WHOLE-GROUP override rule ("an action authoring a group replaces the station-level group
 * wholesale; omitting inherits it") - the ONE choke point every group read goes through, so no
 * call site re-derives the fallback-to-station-default logic by hand. Zero engine/store touch;
 * takes only {@link StationAsset}/{@link ActionDef} value objects.
 *
 * <p>The phase-1 single-action default (no {@code Actions} map authored) is modeled as ONE
 * implicit action id, {@link #ACTION_WORK}, whose {@link ResolvedAction} is simply the station's
 * own top-level groups verbatim - {@link #resolve} never special-cases it beyond that (a station
 * with no {@code Actions} map behaves exactly as if it authored one entry, {@code "work"}, with
 * every group omitted - the design's own byte-stable-regression framing).
 */
public final class ActionResolver {

    /** The one action id phase 1 ever forwarded; still the implicit default id in phase 2. */
    public static final String ACTION_WORK = "work";

    private ActionResolver() {
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

    /**
     * The whole-group-override-resolved view of {@code actionId} on {@code asset}. An unknown
     * {@code actionId} against an asset that DOES author an {@code Actions} map resolves as if no
     * override existed for it (every group falls back to the station-level default) - callers
     * that need "does this action exist" should check {@link #actionIds} first.
     */
    @Nonnull
    public static ResolvedAction resolve(@Nonnull StationAsset asset, @Nonnull String actionId) {
        Map<String, ActionDef> actions = asset.getActions();
        ActionDef def = actions != null ? actions.get(actionId) : null;
        return new ResolvedAction(
                actionId,
                def != null && def.getInput() != null ? def.getInput() : null,
                def != null && def.getCustody() != null ? def.getCustody() : asset.getCustody(),
                def != null && def.getWork() != null ? def.getWork() : asset.getWork(),
                def != null && def.getRecipe() != null ? def.getRecipe() : asset.getRecipe(),
                def != null && def.getTool() != null ? def.getTool() : asset.getTool(),
                def != null && def.getHold() != null ? def.getHold() : asset.getHold(),
                def != null && def.getCamera() != null ? def.getCamera() : asset.getCamera(),
                def != null && def.getAnimation() != null ? def.getAnimation() : asset.getAnimation(),
                def != null && def.getPresentation() != null ? def.getPresentation() : asset.getPresentation(),
                def != null && def.getCompletion() != null ? def.getCompletion() : asset.getCompletion(),
                def != null && def.getLoot() != null ? def.getLoot() : asset.getLoot(),
                def != null && def.getRequires() != null ? def.getRequires() : asset.getRequires(),
                def != null ? def.getSteps() : null);
    }

    /**
     * Diegetic action selection (design 9.1): the FIRST action (authored order) whose
     * {@link ActionInput} matches {@code heldItemId}/{@code heldResourceTypeId}/{@code heldTags}/
     * {@code heldFunction}, or a catch-all ({@link ActionInput#isCatchAll()}) entry. Returns
     * {@code null} when nothing matches (including no catch-all). A station with no {@code
     * Actions} map always resolves to {@link #ACTION_WORK} (no matcher to fail).
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
            ActionInput input = def != null ? def.getInput() : null;
            if (input == null || input.isCatchAll() || matches(input, heldItemId, heldResourceTypeId, heldTags, heldFunction)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * The live-item-aware sibling of {@link #selectAction} (phase 2 leg E, a DIFFERENT name -
     * never an overload - since a {@code null} 3rd argument would otherwise be ambiguous between
     * the {@code String}/{@code String[]} forms): matches against the held item's FULL
     * {@code ResourceTypeId} FAMILY set (an item can belong to more than one family) instead of a
     * single id - the anvil's "convert" action matches ANY vanilla {@code Ingredient_Bar_<Metal>}
     * by its {@code Metal_Bars} family membership, the exact same family-array matching
     * {@code station.StationCustody} already uses for placed-input custody.
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
            ActionInput input = def != null ? def.getInput() : null;
            if (input == null || input.isCatchAll()
                    || matchesAnyResourceType(input, heldItemId, heldResourceTypeIds, heldTags, heldFunction)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Restart-orphan recovery (R5 fix, design 9.4's self-heal extended): the FIRST action whose
     * {@link Custody#getStates()}' {@code Loaded} name case-insensitively matches {@code
     * currentStateName} - the block's own CURRENTLY PERSISTED interaction-state name, read
     * separately from any live claim (which is memory-only and lost across a restart). Consulted
     * ONLY as a third fallback ({@code StationService#toggle}, after both the live-claim selector
     * and {@link #selectActionByFamily} return null) so it never disturbs the already-correct
     * in-session fast path. Returns {@code null} when {@code currentStateName} is null/blank, or
     * when no action authors a matching {@code Custody.States.Loaded} (including no {@code
     * Custody} at all) - a genuinely idle/never-loaded block correctly finds nothing here.
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
     * {@code station.step} handler / the phase-1 direct-Java engine path should read - never the
     * raw {@link StationAsset} or {@link ActionDef} group directly once an action id is chosen.
     */
    public static final class ResolvedAction {
        private final String actionId;
        @Nullable private final ActionInput input;
        @Nullable private final Custody custody;
        @Nullable private final StationAsset.Work work;
        @Nullable private final StationAsset.Recipe recipe;
        @Nullable private final StationAsset.Tool tool;
        @Nullable private final StationAsset.Hold hold;
        @Nullable private final StationAsset.Camera camera;
        @Nullable private final StationAsset.Animation animation;
        @Nullable private final Presentation presentation;
        @Nullable private final Presentation completion;
        @Nullable private final StationAsset.Loot loot;
        @Nullable private final Requires requires;
        @Nullable private final com.ziggfreed.rpgstations.asset.StationStep[] steps;

        ResolvedAction(@Nonnull String actionId, @Nullable ActionInput input, @Nullable Custody custody,
                @Nullable StationAsset.Work work,
                @Nullable StationAsset.Recipe recipe, @Nullable StationAsset.Tool tool,
                @Nullable StationAsset.Hold hold, @Nullable StationAsset.Camera camera,
                @Nullable StationAsset.Animation animation, @Nullable Presentation presentation,
                @Nullable Presentation completion, @Nullable StationAsset.Loot loot, @Nullable Requires requires,
                @Nullable com.ziggfreed.rpgstations.asset.StationStep[] steps) {
            this.actionId = actionId;
            this.input = input;
            this.custody = custody;
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

        /** The authored step program, or {@code null} when this action wants the implicit program. */
        @Nullable
        public com.ziggfreed.rpgstations.asset.StationStep[] getSteps() {
            return steps;
        }
    }
}
