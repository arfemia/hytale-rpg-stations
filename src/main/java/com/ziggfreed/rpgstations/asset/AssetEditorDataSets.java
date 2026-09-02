package com.ziggfreed.rpgstations.asset;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.EventRegistry;
import com.ziggfreed.common.asset.EditorDataSets;
import com.ziggfreed.common.loot.LootableConfig;
import com.ziggfreed.common.loot.stamp.RollPoolConfig;
import com.ziggfreed.common.ui.hud.HudPosition;
import com.ziggfreed.rpgstations.api.impl.ContributionChannelRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.loot.StationLootEngine;
import com.ziggfreed.rpgstations.station.ActionCatalog;
import com.ziggfreed.rpgstations.station.StationCameraPreset;
import com.ziggfreed.rpgstations.station.StationCatalog;
import com.ziggfreed.rpgstations.station.StationService;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Serves the value lists behind every {@code UIEditor.Dropdown} dataset id this package's codecs
 * declare, so the in-game Asset Editor offers a real pick list instead of a free-text field.
 *
 * <p>The registration mechanism itself is the shared {@code asset.EditorDataSets} primitive (the
 * engine's own keyed {@code AssetEditorRequestDataSetEvent}, one registration per dataset id, guarded
 * end to end so a server build without the Asset Editor module degrades to plain free-text fields
 * rather than failing plugin startup). What lives here is only WHICH datasets this mod serves and
 * what answers them.
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
    public static final String STATION_BLOCKS = "rpgstations:station-blocks";
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
     * Register one handler per dataset id. Called once from {@code RpgStationsPlugin#setup()}.
     */
    public static void register(@Nonnull EventRegistry registry) {
        EditorDataSets.live(registry, STATIONS, () -> StationCatalog.getInstance().all().keySet());
        // StructurePatternAsset.Activate.Block: the block item ids the asset-derived discovery
        // index resolves to a station - exactly the values a pattern activation is useful with
        // (RevertBlock stays free text: any block id is a legitimate revert). There is
        // deliberately NO rpgstations:patterns dataset: no codec leaf names a pattern id (the
        // runtime's stash tag is not authored), and a dataset nothing consumes is a decoy.
        EditorDataSets.live(registry, STATION_BLOCKS,
                () -> StationService.getInstance().stationBlockItemIds());
        EditorDataSets.live(registry, ACTIONS, () -> ActionCatalog.getInstance().all().keySet());
        EditorDataSets.live(registry, LOOTABLES, () -> LootableConfig.getInstance().all().keySet());
        EditorDataSets.live(registry, ROLLPOOLS, () -> RollPoolConfig.getInstance().all().keySet());
        EditorDataSets.live(registry, FACTORS, () -> FactorRegistryImpl.getInstance().registeredIds());
        EditorDataSets.live(registry, CHANNELS, () -> ContributionChannelRegistryImpl.getInstance().registeredIds());

        // StationAsset.Hold.Mount.Surface, compared case-insensitively by StationValidator and
        // the two mount controllers it discriminates between.
        EditorDataSets.fixed(registry, MOUNT_SURFACE, "Block", "Entity");
        EditorDataSets.fixed(registry, CAMERA_PRESETS, cameraPresetIds());
        EditorDataSets.fixed(registry, HIDE_ROUTE, Puppet.HIDE_ROUTE_SCALE, Puppet.HIDE_ROUTE_EFFECT, Puppet.HIDE_ROUTE_NONE);
        EditorDataSets.fixed(registry, LOOK_SOURCE, Puppet.LOOK_SOURCE_PLAYER_CLONE, Puppet.LOOK_SOURCE_MODEL,
                Puppet.LOOK_SOURCE_NPC_ROLE);
        EditorDataSets.fixed(registry, SKIN_SOURCE, Puppet.SKIN_SOURCE_PLAYER_CLONE, Puppet.SKIN_SOURCE_ROLE_DEFAULT);
        EditorDataSets.fixed(registry, PROP_SOURCE, Puppet.PROP_SOURCE_MIRROR_HELD, Puppet.PROP_SOURCE_ITEM_ID,
                Puppet.PROP_SOURCE_NONE);
        EditorDataSets.fixed(registry, PROP_SLOT, Puppet.PROP_SLOT_HOTBAR, Puppet.PROP_SLOT_UTILITY);
        EditorDataSets.fixed(registry, CONSUME_FROM, StationStep.Consume.FROM_INVENTORY, StationStep.Consume.FROM_CUSTODY);
        EditorDataSets.fixed(registry, PRODUCE_TO, StationStep.Produce.TO_INVENTORY, StationStep.Produce.TO_CUSTODY);
        EditorDataSets.fixed(registry, CONDITION_FAIL_RESULT, StationStep.OnConditionFail.RESULT_SKIP,
                StationStep.OnConditionFail.RESULT_FAIL);
        EditorDataSets.fixed(registry, ROLL_TRIGGER, StationLootEngine.TRIGGER_CYCLE,
                StationLootEngine.TRIGGER_COMPLETION);
        // ActionInput.Function, resolved against the held item's live shape by ActionResolver.
        EditorDataSets.fixed(registry, ACTION_FUNCTION, "Weapon", "Armor", "Tool");
        EditorDataSets.fixed(registry, HUD_POSITIONS, hudPositions());
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
