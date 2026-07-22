package com.ziggfreed.rpgstations;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.asset.AssetStoreRegistrar;
import com.ziggfreed.rpgstations.api.RpgStationsApi;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.RpgStationsApiImpl;
import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.LootableAsset;
import com.ziggfreed.rpgstations.asset.RollPool;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.command.RpgStationsCommand;
import com.ziggfreed.rpgstations.interaction.StationRetrieveInteraction;
import com.ziggfreed.rpgstations.interaction.StationUseInteraction;
import com.ziggfreed.rpgstations.loot.LootableCatalog;
import com.ziggfreed.rpgstations.loot.RollPoolCatalog;
import com.ziggfreed.rpgstations.puppetspike.PuppetSpikeService;
import com.ziggfreed.rpgstations.station.FlairCatalog;
import com.ziggfreed.rpgstations.station.SettingsCatalog;
import com.ziggfreed.rpgstations.station.StationCatalog;
import com.ziggfreed.rpgstations.station.StationCustodyBreakSystem;
import com.ziggfreed.rpgstations.station.StationDeathSystem;
import com.ziggfreed.rpgstations.station.StationFrameSystem;
import com.ziggfreed.rpgstations.station.StationInterruptDamageSystem;
import com.ziggfreed.rpgstations.station.StationService;
import com.ziggfreed.rpgstations.station.StationValidator;
import com.ziggfreed.rpgstations.ui.StationSummaryHud;
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
 * engine {@link RpgStationsSettingsAsset} store - AND installs the real extension surface: {@link
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

    /**
     * One-shot gate for {@link #registerPostLoadAudit}'s deferred FULL {@link StationValidator}
     * pass (D4 fix) - mirrors the MMO's own {@code ContentAudit.runAndLogAll()} first-{@code
     * PlayerReadyEvent} gate ({@code MMOSkillTreePlugin}'s {@code contentAuditLogged}).
     */
    private static final AtomicBoolean postLoadAuditLogged = new AtomicBoolean(false);

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
        registerRollPoolAssetStore();
        registerFlairAssetStore();
        registerSettingsAssetStore();
        registerStationInteraction();
        registerStationRetrieveInteraction();
        registerStationSystems();
        registerTeardownHooks();
        registerPostLoadAudit();
        registerSummaryHudInstall();
        registerPuppetSpikeSafetyNet();
        getCommandRegistry().registerCommand(new RpgStationsCommand());
        Log.info("RpgStations setup complete (leg 4 - the api artifact is live: events fire, "
                + "the factor/flair-unlock/summary-enricher registries are wired into the engine).");
    }

    /**
     * The ONE deferred full {@link StationValidator} audit (D4 fix - "fix the timing, not the
     * checks"): every per-fold {@code LoadedAssetsEvent} handler below now logs only the
     * STRUCTURAL pass ({@link StationValidator#runStructuralAndLog}), because a cross-layer
     * reference check (native {@code ItemDropList} id, this mod's own {@code Lootable}/{@code
     * RollPool} id, or a lang key resolved through a pack's OWN {@code rpgstations.lang} overlay)
     * can false-positive when it runs before a LATER pack layer has folded the asset it points at
     * (the boot-log evidence: {@code STAMP_UNKNOWN_POOL} for a RollPool that folded one line
     * later, {@code LOOT_UNKNOWN_DROPLIST}/{@code MISSING_*_LANG} for a pack layer's own
     * Drops/lang that had not settled yet relative to that SAME layer's Station fold). By the
     * first {@link PlayerReadyEvent} every asset pack (RpgStations' own AND every installed
     * content pack) has finished merging, so the FULL {@link StationValidator#runAndLog} pass is
     * race-free here - the EXACT same timing guarantee {@code MMOSkillTreePlugin}'s {@code
     * ContentAudit.runAndLogAll()} relies on for its own first-PlayerReady startup audit.
     */
    private void registerPostLoadAudit() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            if (postLoadAuditLogged.compareAndSet(false, true)) {
                try {
                    StationValidator.runAndLog();
                } catch (Throwable t) {
                    Log.warn("Deferred post-load station validation failed: " + t.getMessage());
                }
            }
        });
    }

    /**
     * The two teardown hooks {@link StationService#stopForRef}/{@link StationService#stopFor}
     * were ALREADY shaped for (see their own javadoc: "Death hook", "Disconnect hook") but never
     * wired to a live event until now (design section 4.2; leg 5 relies on RpgStations owning
     * these once the MMO's equivalent calls are deleted). Server-shutdown teardown was already
     * covered by {@link #shutdown()}'s {@code stopAll}.
     *
     * <p><b>SMOKE-FIX S3 (custody return "not coming back at session stop at all"):</b>
     * {@code StationService#stop} touches {@code Store} repeatedly (custody's inventory-return /
     * drop-at-block writes, camera reset, hold release, mount dismount) - and {@code Store}
     * (hytale-shared-source {@code component/Store.java}) asserts it is only ever touched on its
     * owning world thread. Every OTHER {@code stop()} entry point already runs on the world
     * thread (the heartbeat/cycle paths run inside an {@code AbstractWorldFrameSystem} tick,
     * {@code toggle()} runs inside the {@code rpg_station_use} interaction handler, death runs
     * inside an {@code EntityStoreRegistry} system) - {@code PlayerDisconnectEvent} does NOT
     * (mirrors the MMO's OWN {@code PlayerDisconnectEvent} handler, which world.execute-hops its
     * own store-touching cleanup for the identical reason). Calling {@code stopFor} directly here
     * risked an off-thread throw partway through {@code returnCustody} - AFTER it had already
     * removed the claim from {@code custodyByBlock} but before the items landed in the owner's
     * inventory or were dropped at the block - silently losing them. Hopping to the player's own
     * world before calling {@code stopFor} closes that gap; a null/dead world (already torn down)
     * falls back to the direct call so a shutdown-adjacent disconnect still attempts cleanup.
     */
    private void registerTeardownHooks() {
        getEntityStoreRegistry().registerSystem(new StationDeathSystem());
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            try {
                var playerRef = event.getPlayerRef();
                var uuid = playerRef != null ? playerRef.getUuid() : null;
                if (uuid == null) {
                    return;
                }
                var worldUuid = playerRef.getWorldUuid();
                World world = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
                if (world != null && world.isAlive()) {
                    world.execute(() -> {
                        try {
                            StationService.getInstance().stopFor(uuid, StationService.StopReason.DISCONNECTED);
                        } catch (Throwable t) {
                            Log.warn("Station disconnect teardown failed (world thread): " + t.getMessage());
                        }
                        try {
                            // TEMPORARY P0 spike harness (puppetspike/) - despawn any puppet +
                            // undo any self-hide route so a disconnecting spike test never
                            // leaves the player's OWN entity data with a shrunk/model-swapped
                            // persisted state. See PuppetSpikeService's own javadoc.
                            PuppetSpikeService.getInstance().revertFor(uuid);
                        } catch (Throwable t) {
                            Log.warn("Puppet spike disconnect revert failed (world thread): " + t.getMessage());
                        }
                    });
                } else {
                    StationService.getInstance().stopFor(uuid, StationService.StopReason.DISCONNECTED);
                    try {
                        PuppetSpikeService.getInstance().revertFor(uuid);
                    } catch (Throwable t) {
                        Log.warn("Puppet spike disconnect revert failed: " + t.getMessage());
                    }
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
        // Structural-only at fold time (D4 fix) - the FULL pass (incl. cross-layer reference
        // checks) runs once, post-load, from registerPostLoadAudit().
        StationValidator.runStructuralAndLog();
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
     * Registers the {@link RollPool} Pattern-A store at {@code Server/RpgStations/RollPools}
     * (design section 9.5, phase 2 leg E) and folds every loaded entry into
     * {@link RollPoolCatalog} - the anvil's Stamp step's {@code Stats.Pool} reference target.
     */
    private void registerRollPoolAssetStore() {
        AssetStoreRegistrar.registerStore(
                RollPool.class,
                new DefaultAssetMap<String, RollPool>(),
                "RpgStations/RollPools",
                RollPool::getId,
                RollPool.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, RollPool.class,
                RpgStationsPlugin::onRollPoolAssetsLoaded);
    }

    private static void onRollPoolAssetsLoaded(
            LoadedAssetsEvent<String, RollPool, DefaultAssetMap<String, RollPool>> event) {
        DefaultAssetMap<String, RollPool> assetMap = event.getAssetMap();
        Map<String, RollPool> layer = new LinkedHashMap<>();
        for (Map.Entry<String, RollPool> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        RollPoolCatalog.getInstance().fold(layer, false);
        Log.info("RollPool asset layer: folded " + layer.size() + " roll pool(s) into RollPoolCatalog: "
                + layer.keySet());
    }

    /**
     * Registers the {@link FlairAsset} Pattern-A store at {@code Server/RpgStations/Flairs}
     * (design section 9.6, phase 2 leg F - the open flair/moment vocabulary's asset-driven half)
     * and folds every loaded entry into {@link FlairCatalog}; {@link StationValidator#runStructuralAndLog}
     * re-runs on THIS fold too (unlike Lootable/RollPool) for the same structural per-station/
     * per-flair coverage - its own {@code Stations}-references-a-known-id check is a cross-layer
     * reference check now deferred to the post-load audit (D4 fix), like every other one.
     */
    private void registerFlairAssetStore() {
        AssetStoreRegistrar.registerStore(
                FlairAsset.class,
                new DefaultAssetMap<String, FlairAsset>(),
                "RpgStations/Flairs",
                FlairAsset::getId,
                FlairAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, FlairAsset.class,
                RpgStationsPlugin::onFlairAssetsLoaded);
    }

    private static void onFlairAssetsLoaded(
            LoadedAssetsEvent<String, FlairAsset, DefaultAssetMap<String, FlairAsset>> event) {
        DefaultAssetMap<String, FlairAsset> assetMap = event.getAssetMap();
        Map<String, FlairAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, FlairAsset> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        FlairCatalog.getInstance().fold(layer, false);
        Log.info("FlairAsset layer: folded " + layer.size() + " flair asset(s) into FlairCatalog: "
                + layer.keySet());
        // Structural-only at fold time (D4 fix) - the FULL pass (incl. cross-layer reference
        // checks) runs once, post-load, from registerPostLoadAudit().
        StationValidator.runStructuralAndLog();
    }

    /**
     * Registers the {@link RpgStationsSettingsAsset} Pattern-A store at {@code Server/RpgStations/Settings}
     * and folds the resolved instance into {@link SettingsCatalog} (design section 4.6).
     */
    private void registerSettingsAssetStore() {
        AssetStoreRegistrar.registerStore(
                RpgStationsSettingsAsset.class,
                new DefaultAssetMap<String, RpgStationsSettingsAsset>(),
                "RpgStations/Settings",
                RpgStationsSettingsAsset::getId,
                RpgStationsSettingsAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, RpgStationsSettingsAsset.class,
                RpgStationsPlugin::onSettingsAssetsLoaded);
    }

    private static void onSettingsAssetsLoaded(
            LoadedAssetsEvent<String, RpgStationsSettingsAsset, DefaultAssetMap<String, RpgStationsSettingsAsset>> event) {
        DefaultAssetMap<String, RpgStationsSettingsAsset> assetMap = event.getAssetMap();
        Map<String, RpgStationsSettingsAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, RpgStationsSettingsAsset> entry : assetMap.getAssetMap().entrySet()) {
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
     * Registers {@code "rpg_station_retrieve"} (new feature: press-F custody retrieval) - the
     * generic, parameterless interaction the jar-shipped {@code RPG_Station_Retrieve}
     * RootInteraction asset references. Every custody display entity in every installed pack
     * points at that ONE shared asset ({@code station.StationCustodyDisplay#addRetrieveInteraction}),
     * so this registration (unlike {@link #registerStationInteraction}) needs no per-station Java.
     */
    private void registerStationRetrieveInteraction() {
        try {
            getCodecRegistry(Interaction.CODEC).register(
                    StationRetrieveInteraction.TYPE_NAME,
                    StationRetrieveInteraction.class,
                    StationRetrieveInteraction.CODEC);
            Log.info("Registered interaction: " + StationRetrieveInteraction.TYPE_NAME);
        } catch (Exception e) {
            Log.severe("Failed to register StationRetrieve interaction: " + e.getMessage());
        }
    }

    /**
     * SMOKE-FIX S1: installs the session-summary HUD ({@link StationSummaryHud}) on the native
     * per-player {@code HudManager} at first ready, the missing half of the phase-1 leg-5 move
     * ("RpgStations installs its own HUD now" per {@code MMOSkillTreePlugin}'s own comment at the
     * spot it deleted the old install call) - nothing in this jar ever called {@code
     * player.getHudManager().addCustomHud(...)} for this HUD, so {@code StationSummaryHud.tryShow}
     * always failed {@code KeyedCustomHud.get}'s native lookup and every session silently fell
     * back to the plain-toast path, which read in-game as "the completion HUD no longer appears
     * at all". Mirrors the pre-extraction MMO's own {@code AbilityCooldownHud}/{@code
     * QuestTrackerHud} install shape at {@code PlayerReadyEvent} (world.execute hop before any
     * Store/Ref/HudManager touch); {@code HudManager#addCustomHud} itself is replace-safe on a
     * reconnect (clears + re-adds under the same key), so no existence guard is needed here.
     */
    private void registerSummaryHudInstall() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                Player player = event.getPlayer();
                World world = player.getWorld();
                world.execute(() -> {
                    try {
                        Ref<EntityStore> ref = player.getReference();
                        if (ref == null || !ref.isValid()) {
                            return;
                        }
                        // Player.getPlayerRef() is @Deprecated(forRemoval=true) - fetch the
                        // PlayerRef component manually per its own javadoc replacement note.
                        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                        Player readyPlayer = ref.getStore().getComponent(ref, Player.getComponentType());
                        if (playerRef != null && readyPlayer != null) {
                            readyPlayer.getHudManager().addCustomHud(playerRef, new StationSummaryHud(playerRef));
                        }
                    } catch (Throwable t) {
                        Log.warn("Failed to install station summary HUD: " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                Log.warn("Station summary HUD install (outer) failed: " + t.getMessage());
            }
        });
    }

    /**
     * <b>TEMPORARY P0 spike harness</b> ({@code puppetspike/}, see {@link
     * PuppetSpikeService}'s own javadoc): wires the {@code PlayerReadyEvent} "belt-and-suspenders"
     * safety net (design section 4.4's leg-P5 net, in miniature) minimally into the existing
     * ready-event plumbing, mirroring {@link #registerSummaryHudInstall}'s exact world.execute-hop
     * shape. Fires on EVERY ready (not gated to first-ever), unconditionally - a spike must never
     * strand an invisible/shrunk player after a reconnect.
     */
    private void registerPuppetSpikeSafetyNet() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                Player player = event.getPlayer();
                World world = player.getWorld();
                world.execute(() -> {
                    try {
                        Ref<EntityStore> ref = player.getReference();
                        if (ref == null || !ref.isValid()) {
                            return;
                        }
                        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                        if (playerRef != null) {
                            PuppetSpikeService.getInstance().safetyNetOnReady(playerRef, ref, ref.getStore());
                        }
                    } catch (Throwable t) {
                        Log.warn("Puppet spike ready safety-net failed: " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                Log.warn("Puppet spike ready safety-net (outer) failed: " + t.getMessage());
            }
        });
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
        PuppetSpikeService.getInstance().shutdownAnimationScheduler();
        Log.info("RpgStations shutdown complete.");
    }
}
