package com.ziggfreed.rpgstations.asset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.asseteditor.event.AssetEditorRequestDataSetEvent;
import com.hypixel.hytale.event.EventRegistry;
import com.ziggfreed.common.ui.hud.HudPosition;
import com.ziggfreed.rpgstations.api.impl.ContributionChannelRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.loot.RollPoolCatalog;
import com.ziggfreed.rpgstations.station.ActionCatalog;
import com.ziggfreed.rpgstations.station.StationCameraPreset;
import com.ziggfreed.rpgstations.station.StationCatalog;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Serves the value lists behind every {@code UIEditor.Dropdown} dataset id this package's codecs
 * declare, so the in-game Asset Editor offers a real pick list instead of a free-text field.
 *
 * <p>The mechanism is the engine's own keyed {@code AssetEditorRequestDataSetEvent}: the editor
 * asks for a dataset by id, a registered handler fills {@code setResults(...)}. The first-party
 * {@code ItemCategories} handler is the shape this class follows exactly, one registration per
 * dataset id.
 *
 * <p>Two flavors of dataset live here:
 * <ul>
 *   <li><b>LIVE</b> - id lists read straight off this mod's own runtime catalogs / registries
 *       ({@code stations}, {@code actions}, {@code lootables}, {@code rollpools},
 *       {@code factors}, {@code channels}). A request is answered from whatever is loaded at that
 *       moment, so an asset reload or a late third-party factor/channel registration simply widens
 *       the next answer. Answering an empty list is legitimate (nothing loaded yet, or no mod has
 *       declared a channel), never an error.</li>
 *   <li><b>FIXED</b> - the closed value sets of the union discriminators, each sourced from the
 *       SAME constant the decoder compares against, so a renamed arm can never leave a stale
 *       dropdown behind. The one literal set that has no constants ({@code action-function}) names
 *       its consumer in a comment.</li>
 * </ul>
 *
 * <p><b>The dropdown is authoring convenience, never validation.</b> A hand-written JSON never
 * passes through the editor at all, so every dataset here has a matching content-validator check
 * that stays the real backstop; nothing in the validator is retired because a dropdown exists.
 * Map-KEY vocabularies (flair moment ids, per-stat cap keys, tag families) are deliberately
 * absent: the editor's dropdown metadata applies to a field VALUE only, and those keep their
 * validator checks alone.
 *
 * <p>Handlers must stay cheap and side-effect free: the request is an async event, and every
 * source read below is a snapshot of a concurrent map or an immutable constant list.
 */
public final class AssetEditorDataSets {

    /** Every dataset id this class serves, prefixed so it can never collide with a first-party or third-party set. */
    public static final String STATIONS = "rpgstations:stations";
    public static final String ACTIONS = "rpgstations:actions";
    public static final String LOOTABLES = "rpgstations:lootables";
    public static final String ROLLPOOLS = "rpgstations:rollpools";
    public static final String FACTORS = "rpgstations:factors";
    public static final String CHANNELS = "rpgstations:channels";
    public static final String MOUNT_SURFACE = "rpgstations:mount-surface";
    public static final String CAMERA_PRESETS = "rpgstations:camera-presets";
    public static final String HIDE_ROUTE = "rpgstations:hide-route";
    public static final String LOOK_SOURCE = "rpgstations:look-source";
    public static final String SKIN_SOURCE = "rpgstations:skin-source";
    public static final String PROP_SOURCE = "rpgstations:prop-source";
    public static final String PROP_SLOT = "rpgstations:prop-slot";
    public static final String CONSUME_FROM = "rpgstations:consume-from";
    public static final String PRODUCE_TO = "rpgstations:produce-to";
    public static final String CONDITION_FAIL_RESULT = "rpgstations:condition-fail-result";
    public static final String ROLL_TRIGGER = "rpgstations:roll-trigger";
    public static final String ACTION_FUNCTION = "rpgstations:action-function";
    public static final String HUD_POSITIONS = "rpgstations:hud-positions";

    /**
     * The named corner presets {@code HudPosition.parse} accepts. Held as a literal list because
     * the layout value exposes a validator ({@code isValidPreset}) rather than an enumeration;
     * {@link #hudPositions()} runs every entry back through that validator, so a preset dropped
     * upstream surfaces as a warn instead of a silently-offered dead option.
     */
    private static final String[] HUD_POSITION_PRESETS = {
            "TopLeft", "TopCenter", "TopRight",
            "CenterLeft", "Center", "CenterRight",
            "BottomLeft", "BottomCenter", "BottomRight"
    };

    private AssetEditorDataSets() {
    }

    /**
     * Register one handler per dataset id. Called once from {@code RpgStationsPlugin#setup()};
     * the whole body is try-guarded there, so a server build without the Asset Editor module
     * degrades to plain free-text fields rather than failing plugin startup.
     */
    public static void register(@Nonnull EventRegistry registry) {
        live(registry, STATIONS, () -> StationCatalog.getInstance().all().keySet());
        live(registry, ACTIONS, () -> ActionCatalog.getInstance().all().keySet());
        live(registry, LOOTABLES, () -> LootableCatalog.getInstance().all().keySet());
        live(registry, ROLLPOOLS, () -> RollPoolCatalog.getInstance().all().keySet());
        live(registry, FACTORS, () -> FactorRegistryImpl.getInstance().registeredIds());
        live(registry, CHANNELS, () -> ContributionChannelRegistryImpl.getInstance().registeredIds());

        // StationAsset.Hold.Mount.Surface, compared case-insensitively by StationValidator and
        // the two mount controllers it discriminates between.
        fixed(registry, MOUNT_SURFACE, "Block", "Entity");
        fixed(registry, CAMERA_PRESETS, cameraPresetIds());
        fixed(registry, HIDE_ROUTE, Puppet.HIDE_ROUTE_SCALE, Puppet.HIDE_ROUTE_EFFECT, Puppet.HIDE_ROUTE_NONE);
        fixed(registry, LOOK_SOURCE, Puppet.LOOK_SOURCE_PLAYER_CLONE, Puppet.LOOK_SOURCE_MODEL,
                Puppet.LOOK_SOURCE_NPC_ROLE);
        fixed(registry, SKIN_SOURCE, Puppet.SKIN_SOURCE_PLAYER_CLONE, Puppet.SKIN_SOURCE_ROLE_DEFAULT);
        fixed(registry, PROP_SOURCE, Puppet.PROP_SOURCE_MIRROR_HELD, Puppet.PROP_SOURCE_ITEM_ID,
                Puppet.PROP_SOURCE_NONE);
        fixed(registry, PROP_SLOT, Puppet.PROP_SLOT_HOTBAR, Puppet.PROP_SLOT_UTILITY);
        fixed(registry, CONSUME_FROM, StationStep.Consume.FROM_INVENTORY, StationStep.Consume.FROM_CUSTODY);
        fixed(registry, PRODUCE_TO, StationStep.Produce.TO_INVENTORY, StationStep.Produce.TO_CUSTODY);
        fixed(registry, CONDITION_FAIL_RESULT, StationStep.OnConditionFail.RESULT_SKIP,
                StationStep.OnConditionFail.RESULT_FAIL);
        fixed(registry, ROLL_TRIGGER, Roll.TRIGGER_CYCLE, Roll.TRIGGER_COMPLETION);
        // ActionInput.Function, resolved against the held item's live shape by ActionResolver.
        fixed(registry, ACTION_FUNCTION, "Weapon", "Armor", "Tool");
        fixed(registry, HUD_POSITIONS, hudPositions());
    }

    /** Register a dataset answered from a live catalog / registry at request time. */
    private static void live(@Nonnull EventRegistry registry, @Nonnull String dataSet,
            @Nonnull Supplier<Collection<String>> source) {
        registry.register(AssetEditorRequestDataSetEvent.class, dataSet, event -> {
            try {
                List<String> ids = new ArrayList<>(source.get());
                Collections.sort(ids);
                event.setResults(ids.toArray(String[]::new));
            } catch (Throwable t) {
                Log.fine("STATION asset-editor dataset '" + dataSet + "' failed: " + t.getMessage());
            }
        });
    }

    /** Register a dataset whose values are a closed, compile-time-known set. */
    private static void fixed(@Nonnull EventRegistry registry, @Nonnull String dataSet,
            @Nonnull String... values) {
        registry.register(AssetEditorRequestDataSetEvent.class, dataSet,
                event -> event.setResults(values.clone()));
    }

    /** Every {@link StationCameraPreset} spelled the way {@code Camera.Recipe} parses it. */
    @Nonnull
    private static String[] cameraPresetIds() {
        StationCameraPreset[] presets = StationCameraPreset.values();
        String[] ids = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            ids[i] = presets[i].id();
        }
        return ids;
    }

    /** The HUD corner presets, each re-validated against the layout value that will parse it. */
    @Nonnull
    private static String[] hudPositions() {
        List<String> valid = new ArrayList<>(HUD_POSITION_PRESETS.length);
        for (String preset : HUD_POSITION_PRESETS) {
            if (HudPosition.isValidPreset(preset)) {
                valid.add(preset);
            } else {
                Log.warn("STATION asset-editor dataset '" + HUD_POSITIONS + "' lists preset '" + preset
                        + "' that HudPosition no longer accepts; omitted.");
            }
        }
        return valid.toArray(String[]::new);
    }
}
