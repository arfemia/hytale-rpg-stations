package com.ziggfreed.rpgstations;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.ziggfreed.common.asset.AssetStoreRegistrar;
import com.ziggfreed.rpgstations.api.RpgStationsApi;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.RpgStationsApiImpl;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.SettingsAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.command.RpgStationsCommand;
import com.ziggfreed.rpgstations.interaction.StationUseInteraction;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.station.SettingsCatalog;
import com.ziggfreed.rpgstations.station.StationCatalog;
import com.ziggfreed.rpgstations.station.StationCustodyBreakSystem;
import com.ziggfreed.rpgstations.station.StationDeathSystem;
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
 * <p><b>Leg 4 (api artifact + wiring) stage:</b> registers the station engine (asset store,
 * catalog fold, the {@code rpg_station_use} interaction, the frame-drain system, the
 * damage-interrupt system) and the conditional-lootable layer ({@link LootableAsset} store), the
 * engine {@link SettingsAsset} store - AND installs the real extension surface: {@link
 * RpgStationsApi#set} injects {@link RpgStationsApiImpl} before anything else runs, then {@link
 * FactorRegistryImpl#registerBuiltins} registers the four {@code rpgstations:} built-ins through
 * that SAME api-backed registry (design section 3.2, dogfooded). The engine now fires its four
 * lifecycle events ({@code StationSessionStartedEvent}/{@code StationCycleCompletedEvent}/{@code
 * StationSessionCompletedEvent}/{@code StationToolBrokeEvent}) and consults the {@code
 * FlairUnlockRegistry}/{@code SummaryEnricherRegistry} unions from {@code StationService}/{@code
 * StationFlairs} - see {@code .claude/research/raw/rpg-stations-unified-design-2026-07-21.md}
 * section 3. The MMO bridge (leg 5) is the first real external consumer.
 *
 * <p><b>Phase-1 closeout (leg P0):</b> registers {@link RpgStationsCommand}
 * ({@code /rpgstations camera <preset>|list}, {@code /rpgstations validate}), the design 4.1
 * command-group scope that was never landed before the MMO deleted its own camera subgroup.
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
        RpgStationsApi.set(RpgStationsApiImpl.getInstance());
        FactorRegistryImpl.getInstance().registerBuiltins();
        registerStationAssetStore();
        registerLootableAssetStore();
        registerSettingsAssetStore();
        registerStationInteraction();
        registerStationSystems();
        registerTeardownHooks();
        getCommandRegistry().registerCommand(new RpgStationsCommand());
        Log.info("RpgStations setup complete (leg 4 - the api artifact is live: events fire, "
                + "the factor/flair-unlock/summary-enricher registries are wired into the engine).");
    }

    /**
     * The two teardown hooks {@link StationService#stopForRef}/{@link StationService#stopFor}
     * were ALREADY shaped for (see their own javadoc: "Death hook", "Disconnect hook") but never
     * wired to a live event until now (design section 4.2; leg 5 relies on RpgStations owning
     * these once the MMO's equivalent calls are deleted). Server-shutdown teardown was already
     * covered by {@link #shutdown()}'s {@code stopAll}.
     */
    private void registerTeardownHooks() {
        getEntityStoreRegistry().registerSystem(new StationDeathSystem());
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            try {
                var playerRef = event.getPlayerRef();
                var uuid = playerRef != null ? playerRef.getUuid() : null;
                if (uuid != null) {
                    StationService.getInstance().stopFor(uuid, StationService.StopReason.DISCONNECTED);
                }
            } catch (Throwable t) {
                Log.warn("Station disconnect teardown failed: " + t.getMessage());
            }
        });
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

    /**
     * Registers the per-world frame drain, the damage-interrupt reader (Inspect group,
     * read-only), and the placed-input custody block-break auto-return reader (design section
     * 9.4, phase-2 leg C - {@link StationCustodyBreakSystem}, the no-active-session case
     * {@code StationService#stop}'s own return path can never reach).
     */
    private void registerStationSystems() {
        getEntityStoreRegistry().registerSystem(new StationFrameSystem());
        getEntityStoreRegistry().registerSystem(new StationInterruptDamageSystem());
        getEntityStoreRegistry().registerSystem(new StationCustodyBreakSystem());
    }

    @Override
    protected void shutdown() {
        StationService.getInstance().stopAll(StationService.StopReason.SERVER_STOP);
        Log.info("RpgStations shutdown complete.");
    }
}
