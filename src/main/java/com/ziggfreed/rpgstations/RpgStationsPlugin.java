package com.ziggfreed.rpgstations;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.ziggfreed.common.asset.AssetStoreRegistrar;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.SettingsAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.interaction.StationUseInteraction;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.loot.StationFactorRegistry;
import com.ziggfreed.rpgstations.station.SettingsCatalog;
import com.ziggfreed.rpgstations.station.StationCatalog;
import com.ziggfreed.rpgstations.station.StationFrameSystem;
import com.ziggfreed.rpgstations.station.StationInterruptDamageSystem;
import com.ziggfreed.rpgstations.station.StationService;
import com.ziggfreed.rpgstations.station.StationValidator;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Entry point for RPG Stations, a standalone Hytale mod owning the diegetic interactive
 * work-station engine (sawmill, forge, and friends) extracted out of the MMO Skill Tree mod.
 * It depends on {@code ziggfreed-common} ONLY - the MMO Skill Tree keeps every piece of
 * progression and reaches the station engine exclusively through a soft extension surface
 * (native events + the {@code api} artifact, leg 4); neither mod hard-deps the other.
 *
 * <p><b>Leg 3 (loot + settings + standalone content) stage:</b> registers the station engine
 * (asset store, catalog fold, the {@code rpg_station_use} interaction, the frame-drain system,
 * the damage-interrupt system), the conditional-lootable layer ({@link LootableAsset} store +
 * {@link StationFactorRegistry}'s built-in {@code rpgstations:} factors), and the engine
 * {@link SettingsAsset} store - the standalone loop (a real station block a player can press F
 * on, work, and get loot from) is now complete with THIS JAR ALONE (the standalone sawmill jar
 * content). The api artifact + event firing (leg 4) and the MMO bridge (leg 5) land in later
 * legs; see {@code .claude/research/raw/rpg-stations-unified-design-2026-07-21.md}.
 */
public class RpgStationsPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static RpgStationsPlugin instance;

    @Nonnull
    public static RpgStationsPlugin getInstance() {
        return instance;
    }

    public RpgStationsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("RpgStations initializing...");
    }

    @Override
    protected void setup() {
        StationFactorRegistry.registerBuiltins();
        registerStationAssetStore();
        registerLootableAssetStore();
        registerSettingsAssetStore();
        registerStationInteraction();
        registerStationSystems();
        Log.info("RpgStations setup complete (leg 3 - the loot engine, engine settings, and the "
                + "standalone sawmill content are live).");
    }

    /**
     * Registers the {@link StationAsset} Pattern-A store at {@code Server/RpgStations/Stations}
     * (the design's leg-2 store-path change from the MMO's {@code Server/MMOSkillTree/Stations})
     * and folds every loaded entry into {@link StationCatalog}. No {@code PackControlAsset}
     * infra exists yet this leg, so the fold is always additive (replace=false); a reload
     * re-fires this event and re-folds for free.
     */
    private void registerStationAssetStore() {
        AssetStoreRegistrar.registerStore(
                StationAsset.class,
                new DefaultAssetMap<String, StationAsset>(),
                "RpgStations/Stations",
                StationAsset::getId,
                StationAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, StationAsset.class,
                RpgStationsPlugin::onStationAssetsLoaded);
    }

    private static void onStationAssetsLoaded(
            LoadedAssetsEvent<String, StationAsset, DefaultAssetMap<String, StationAsset>> event) {
        DefaultAssetMap<String, StationAsset> assetMap = event.getAssetMap();
        Map<String, StationAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, StationAsset> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        StationCatalog.getInstance().fold(layer, false);
        Log.info("Station asset layer: folded " + layer.size()
                + " station asset(s) into StationCatalog: " + layer.keySet());
        StationValidator.runAndLog();
    }

    /**
     * Registers the {@link LootableAsset} Pattern-A store at {@code Server/RpgStations/Lootables}
     * and folds every loaded entry into {@link LootableCatalog} (design section 4.5.1).
     */
    private void registerLootableAssetStore() {
        AssetStoreRegistrar.registerStore(
                LootableAsset.class,
                new DefaultAssetMap<String, LootableAsset>(),
                "RpgStations/Lootables",
                LootableAsset::getId,
                LootableAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, LootableAsset.class,
                RpgStationsPlugin::onLootableAssetsLoaded);
    }

    private static void onLootableAssetsLoaded(
            LoadedAssetsEvent<String, LootableAsset, DefaultAssetMap<String, LootableAsset>> event) {
        DefaultAssetMap<String, LootableAsset> assetMap = event.getAssetMap();
        Map<String, LootableAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, LootableAsset> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        LootableCatalog.getInstance().fold(layer, false);
        Log.info("Lootable asset layer: folded " + layer.size() + " lootable table(s) into LootableCatalog: "
                + layer.keySet());
    }

    /**
     * Registers the {@link SettingsAsset} Pattern-A store at {@code Server/RpgStations/Settings}
     * and folds the resolved instance into {@link SettingsCatalog} (design section 4.6).
     */
    private void registerSettingsAssetStore() {
        AssetStoreRegistrar.registerStore(
                SettingsAsset.class,
                new DefaultAssetMap<String, SettingsAsset>(),
                "RpgStations/Settings",
                SettingsAsset::getId,
                SettingsAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, SettingsAsset.class,
                RpgStationsPlugin::onSettingsAssetsLoaded);
    }

    private static void onSettingsAssetsLoaded(
            LoadedAssetsEvent<String, SettingsAsset, DefaultAssetMap<String, SettingsAsset>> event) {
        DefaultAssetMap<String, SettingsAsset> assetMap = event.getAssetMap();
        Map<String, SettingsAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, SettingsAsset> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        SettingsCatalog.getInstance().fold(layer, false);
        Log.info("Settings asset layer: folded " + layer.size() + " settings entry(ies), Enabled="
                + SettingsCatalog.getInstance().current().isEnabled());
    }

    /** Registers {@code "rpg_station_use"}, the object-form interaction every station block references. */
    private void registerStationInteraction() {
        try {
            getCodecRegistry(Interaction.CODEC).register(
                    StationUseInteraction.TYPE_NAME,
                    StationUseInteraction.class,
                    StationUseInteraction.CODEC);
            Log.info("Registered interaction: " + StationUseInteraction.TYPE_NAME);
        } catch (Exception e) {
            Log.severe("Failed to register StationUse interaction: " + e.getMessage());
        }
    }

    /** Registers the per-world frame drain + the damage-interrupt reader (Inspect group, read-only). */
    private void registerStationSystems() {
        getEntityStoreRegistry().registerSystem(new StationFrameSystem());
        getEntityStoreRegistry().registerSystem(new StationInterruptDamageSystem());
    }

    @Override
    protected void shutdown() {
        StationService.getInstance().stopAll(StationService.StopReason.SERVER_STOP);
        Log.info("RpgStations shutdown complete.");
    }
}
