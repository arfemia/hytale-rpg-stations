package com.ziggfreed.rpgstations;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.asset.AssetStoreRegistrar;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.rpgstations.api.RpgStationsApi;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.RpgStationsApiImpl;
import com.ziggfreed.rpgstations.asset.ActionAsset;
import com.ziggfreed.rpgstations.asset.AssetEditorDataSets;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.command.RpgStationsCommand;
import com.ziggfreed.rpgstations.interaction.StationRetrieveInteraction;
import com.ziggfreed.rpgstations.interaction.StationUseInteraction;
import com.ziggfreed.rpgstations.station.ActionCatalog;
import com.ziggfreed.rpgstations.station.ExtensionCatalog;
import com.ziggfreed.rpgstations.station.FlairCatalog;
import com.ziggfreed.rpgstations.station.SettingsCatalog;
import com.ziggfreed.rpgstations.station.StationBlockPlaceSystem;
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
 * work-station engine (sawmill, forge, and friends). It depends on {@code ziggfreed-common} ONLY;
 * any other mod reaches the station engine exclusively through a soft extension surface (native
 * events plus the {@code api} artifact), and nothing here hard-deps a consumer.
 *
 * <p>Setup registers the station engine (asset store, catalog fold, the {@code rpg_station_use}
 * interaction, the frame-drain system, the damage-interrupt system), the conditional-lootable
 * layer, the engine {@link RpgStationsSettingsAsset} store, AND the
 * extension surface: {@link RpgStationsApi#set} injects {@link RpgStationsApiImpl} before anything
 * else runs, then {@link FactorRegistryImpl#registerBuiltins} registers the {@code rpgstations:}
 * built-in factors through that SAME api-backed registry (design section 3.2, dogfooded). It
 * deliberately declares ZERO built-in contribution CHANNELS: the engine owns built-in factors
 * because it can compute them, and owns no channels because it interprets none. The engine fires
 * its lifecycle events ({@code StationSessionStartedEvent}/{@code StationCycleCompletedEvent}/
 * {@code StationSessionCompletedEvent}/{@code StationToolBrokeEvent}) and consults the
 * {@code FlairUnlockRegistry}/{@code SummaryEnricherRegistry} unions from
 * {@code StationService}/{@code StationFlairs} - see
 * {@code .claude/research/raw/rpg-stations-unified-design-2026-07-21.md} section 3.
 *
 * <p>It also registers {@link RpgStationsCommand} ({@code /rpgstations camera <preset>|list},
 * {@code /rpgstations validate}), the design 4.1 command-group scope.
 */
public class RpgStationsPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static RpgStationsPlugin instance;

    /**
     * One-shot gate for {@link #registerPostLoadAudit}'s deferred FULL {@link StationValidator}
     * pass (D4 fix): the audit runs at the FIRST {@code PlayerReadyEvent} and never again.
     */
    private static final AtomicBoolean postLoadAuditLogged = new AtomicBoolean(false);

    /**
     * Worlds whose ONE-shot performer boot reconcile ({@link #registerPerformerReconcile}) has
     * already run - keyed on the {@code World} object identity (a singleton per world), so a second
     * player's ready in the same world never re-sweeps and clobbers a live puppet. Cleared only by a
     * restart (a fresh plugin instance).
     */
    private final Set<World> performerBootSweptWorlds = ConcurrentHashMap.newKeySet();

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
        registerActionAssetStore();
        registerExtensionAssetStore();
        registerFlairAssetStore();
        registerSettingsAssetStore();
        registerAssetEditorDataSets();
        registerStationInteraction();
        registerStationRetrieveInteraction();
        registerStationSystems();
        registerTeardownHooks();
        registerWorldEviction();
        registerPostLoadAudit();
        registerSummaryHudInstall();
        registerPuppetSafetyNet();
        registerPerformerReconcile();
        getCommandRegistry().registerCommand(new RpgStationsCommand());
        Log.info("RpgStations setup complete (leg 4 - the api artifact is live: events fire, "
                + "the factor/flair-unlock/summary-enricher registries are wired into the engine).");
    }

    /**
     * Serve the in-game Asset Editor's dropdown value lists for every dataset id this mod's
     * codecs name in their {@code UIEditor.Dropdown} metadata (see {@link AssetEditorDataSets}).
     *
     * <p>Guarded as a whole: the Asset Editor is a builtin module, so a server build without it
     * would throw here on class resolution. An authoring convenience must never be able to fail
     * plugin startup - a failure degrades every dropdown to a plain free-text field, and the
     * content validator (which never depended on the editor) still backs every one of them.
     */
    private void registerAssetEditorDataSets() {
        try {
            AssetEditorDataSets.register(getEventRegistry());
        } catch (Throwable t) {
            Log.warn("RpgStations could not register its Asset Editor dropdown datasets; "
                    + "editor fields fall back to free text.", t);
        }
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
     * race-free here.
     */
    private void registerPostLoadAudit() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            if (postLoadAuditLogged.compareAndSet(false, true)) {
                try {
                    // Re-derive the anchor-discovery index FIRST: by now every native asset layer
                    // (Items/BlockTypes/RootInteractions from this jar AND every installed pack) has
                    // settled, whereas the per-fold seed can run while a later layer is still
                    // loading. The seed is idempotent, and the validator's discoverability check
                    // reads exactly this index.
                    StationService.getInstance().seedStationBlockIndexFromAssets();
                } catch (Throwable t) {
                    Log.warn("Deferred station discovery seeding failed: " + t.getMessage());
                }
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
     * wired to a live event until now (design section 4.2). Server-shutdown teardown was already
     * covered by {@link #shutdown()}'s {@code stopAll}.
     *
     * <p><b>SMOKE-FIX S3 (custody return "not coming back at session stop at all"):</b>
     * {@code StationService#stop} touches {@code Store} repeatedly (custody's inventory-return /
     * drop-at-block writes, camera reset, hold release, mount dismount) - and {@code Store}
     * (hytale-shared-source {@code component/Store.java}) asserts it is only ever touched on its
     * owning world thread. Every OTHER {@code stop()} entry point already runs on the world
     * thread (the heartbeat/cycle paths run inside an {@code AbstractWorldFrameSystem} tick,
     * {@code toggle()} runs inside the {@code rpg_station_use} interaction handler, death runs
     * inside an {@code EntityStoreRegistry} system) - {@code PlayerDisconnectEvent} does NOT, which
     * is exactly why every store-touching disconnect cleanup has to world.execute-hop first.
     * Calling {@code stopFor} directly here
     * risked an off-thread throw partway through {@code returnCustody} - AFTER it had already
     * removed the claim from {@code custodyByBlock} but before the items landed in the owner's
     * inventory or were dropped at the block - silently losing them. Hopping to the player's own
     * world before calling {@code stopFor} closes that gap; a null/dead world (already torn down)
     * falls back to the direct call so a shutdown-adjacent disconnect still attempts cleanup.
     *
     * <p><b>The session-less claim sweep</b> rides the same hop. Placing input starts NO session, so
     * a player who loads a station and walks away leaves a claim (and its display prop) that none of
     * the four claim-removal paths can reach - all of them need a session stop, a block break, or a
     * press. Those claims used to survive the disconnect and every disconnect after it. The sweep
     * runs AFTER {@code stopFor} in the SAME task, so the session's own custody return happens first
     * and the sweep only ever sees what it left behind; claims the player left in OTHER worlds
     * (worked a station, then travelled) each get their own hop, because the despawn and block-state
     * reset are world-thread work.
     */
    private void registerTeardownHooks() {
        getEntityStoreRegistry().registerSystem(new StationDeathSystem());
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            var playerRef = event.getPlayerRef();
            var uuid = playerRef != null ? playerRef.getUuid() : null;
            if (uuid == null) {
                return;
            }
            var worldUuid = playerRef.getWorldUuid();
            // The departure world's own teardown gets its OWN try, so a failure there cannot skip
            // the cross-world sweep below. World#execute is not merely "throws if the world is
            // dead": a world stops accepting tasks BEFORE it reports itself dead, so isAlive() can
            // be true and the submit still throw - and when it did, every claim this player held in
            // OTHER worlds leaked for the rest of the uptime, because no other removal path can
            // reach a session-less claim in a world the player is not in.
            try {
                World world = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
                if (world != null && world.isAlive()) {
                    world.execute(() -> {
                        try {
                            StationService.getInstance().stopFor(uuid, StationService.StopReason.DISCONNECTED);
                            StationService.getInstance().returnClaimsOf(uuid, worldUuid, world);
                        } catch (Throwable t) {
                            Log.warn("Station disconnect teardown failed (world thread): " + t.getMessage());
                        }
                    });
                } else {
                    StationService.getInstance().stopFor(uuid, StationService.StopReason.DISCONNECTED);
                    if (worldUuid != null) {
                        StationService.getInstance().returnClaimsOf(uuid, worldUuid, world);
                    }
                }
            } catch (Throwable t) {
                Log.warn("Station disconnect teardown failed: " + t.getMessage());
            }
            try {
                sweepClaimsInOtherWorlds(uuid, worldUuid);
            } catch (Throwable t) {
                Log.warn("Station cross-world claim sweep failed: " + t.getMessage());
            }
        });
    }

    /**
     * Hands back the disconnecting player's custody claims standing in every world OTHER than the
     * one they left from, each on its own world thread ({@link #registerTeardownHooks} covers the
     * departure world inline). A world that is gone or dead falls back to the direct call, which
     * still releases the bookkeeping even though it can no longer write to that world.
     */
    private static void sweepClaimsInOtherWorlds(@Nonnull UUID playerUuid,
            @Nullable UUID departureWorldUuid) {
        for (UUID claimWorldUuid : StationService.getInstance().claimWorldsOf(playerUuid)) {
            if (claimWorldUuid.equals(departureWorldUuid)) {
                continue;
            }
            World claimWorld = Universe.get().getWorld(claimWorldUuid);
            if (claimWorld != null && claimWorld.isAlive()) {
                claimWorld.execute(() -> {
                    try {
                        StationService.getInstance().returnClaimsOf(playerUuid, claimWorldUuid, claimWorld);
                    } catch (Throwable t) {
                        Log.warn("Station cross-world claim sweep failed: " + t.getMessage());
                    }
                });
            } else {
                StationService.getInstance().returnClaimsOf(playerUuid, claimWorldUuid, claimWorld);
            }
        }
    }

    /**
     * The world-unload teardown listener this mod OWNS (design: RpgStations depends on
     * {@code ziggfreed-common} alone and is never a consumer of another mod).
     *
     * <p>Two structures inside this engine self-register a per-world evictor at construction (the
     * session queue partition and the frame-gate map), and both rely on somebody calling
     * {@code WorldEvictors.onWorldRemoved}. {@code ziggfreed-common}'s own plugin does that
     * unconditionally from its {@code setup()} (its {@code RemoveWorldEvent} listener), so the
     * shared fan-out fires whenever the library is installed, with or without any other consumer.
     * This mod's registration here is deliberate REDUNDANCY, kept for ORDERING: this engine's own
     * teardown must run FIRST (it reads the per-world session queue the shared fan-out is about to
     * drop), so it calls the fan-out itself right after, and the library's later call for the same
     * world is a no-op because {@code WorldEvictors} guards against a second eviction of an
     * already-removed world. A second registrant elsewhere in the process is harmless for the same
     * reason - every evictor is an idempotent removal.
     *
     * <p><b>Two limits of this hook, both inherent to the event and stated so they are not
     * mistaken for guarantees.</b>
     * <ul>
     *   <li><b>Cancellation is only observable from listeners that already ran.</b> The
     *   {@code isCancelled()} check skips teardown for a removal cancelled BEFORE this listener,
     *   which is the case worth catching; a plugin that cancels AFTER it leaves the world loaded
     *   with its sessions already stopped. Listener order is not something a plugin can pin, so
     *   there is no ordering fix - a player in such a world simply re-presses F to work again.</li>
     *   <li><b>The dispatch thread is not guaranteed to be the removed world's own thread.</b> A
     *   world removed by the engine's own instance-removal path dispatches from a pool thread,
     *   precisely because the world thread it is about to stop cannot run the removal. Every store
     *   touch inside the teardown is individually guarded and degrades to releasing bookkeeping, so
     *   nothing leaks and no exception escapes; what it cannot do is hand placed input back, which
     *   is why this path releases claims rather than returning them (the world and its entity store
     *   are going away, so there is nowhere in it to return them TO). Hopping to the world thread
     *   is not an alternative here: a world mid-removal has already stopped accepting tasks.</li>
     * </ul>
     */
    private void registerWorldEviction() {
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, event -> {
            try {
                if (event.isCancelled()) {
                    return;
                }
                World removed = event.getWorld();
                if (removed != null) {
                    StationService.getInstance().onWorldRemoved(removed);
                    WorldEvictors.onWorldRemoved(removed);
                }
            } catch (Throwable t) {
                Log.warn("Station world-removal teardown failed: " + t.getMessage());
            }
        });
    }

    /**
     * Registers the {@link StationAsset} Pattern-A store at {@code Server/RpgStations/Stations}
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
        // Derive the anchor-discovery index (blockItemId -> stationId) from the native
        // RootInteraction/BlockType assets, so a COLD server discovers station blocks nobody has
        // pressed F on yet. Idempotent, try-guarded end to end (never throws into the fold), and
        // re-run post-load from registerPostLoadAudit() because a native Item/BlockType layer can
        // settle AFTER this station fold fires.
        StationService.getInstance().seedStationBlockIndexFromAssets();
        // Structural-only at fold time (D4 fix) - the FULL pass (incl. cross-layer reference
        // checks) runs once, post-load, from registerPostLoadAudit().
        StationValidator.runStructuralAndLog();
    }

    /**
     * Registers the {@link ActionAsset} Pattern-A store at {@code Server/RpgStations/Actions}
     * (scope-2 design 1.5) and folds every loaded entry into {@link ActionCatalog} - an inline
     * {@code Actions} entry's {@code Ref} leaf resolves against it.
     */
    private void registerActionAssetStore() {
        AssetStoreRegistrar.registerStore(
                ActionAsset.class,
                new DefaultAssetMap<String, ActionAsset>(),
                "RpgStations/Actions",
                ActionAsset::getId,
                ActionAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, ActionAsset.class,
                RpgStationsPlugin::onActionAssetsLoaded);
    }

    private static void onActionAssetsLoaded(
            LoadedAssetsEvent<String, ActionAsset, DefaultAssetMap<String, ActionAsset>> event) {
        DefaultAssetMap<String, ActionAsset> assetMap = event.getAssetMap();
        Map<String, ActionAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, ActionAsset> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        ActionCatalog.getInstance().fold(layer, false);
        Log.info("Action asset layer: folded " + layer.size() + " standalone action(s) into ActionCatalog: "
                + layer.keySet());
    }

    /**
     * Registers the {@link ExtensionAsset} Pattern-A store at {@code Server/RpgStations/Extensions}
     * (scope-2 design 1.8, decision 27) and folds every loaded entry into {@link ExtensionCatalog}
     * (the ONE additive fourth-party composition mechanism); {@code ExtensionCatalog.fold} logs the
     * {@code EXTENSION_APPLIED} summary per target.
     */
    private void registerExtensionAssetStore() {
        AssetStoreRegistrar.registerStore(
                ExtensionAsset.class,
                new DefaultAssetMap<String, ExtensionAsset>(),
                "RpgStations/Extensions",
                ExtensionAsset::getId,
                ExtensionAsset.CODEC,
                null);
        getEventRegistry().register(LoadedAssetsEvent.class, ExtensionAsset.class,
                RpgStationsPlugin::onExtensionAssetsLoaded);
    }

    private static void onExtensionAssetsLoaded(
            LoadedAssetsEvent<String, ExtensionAsset, DefaultAssetMap<String, ExtensionAsset>> event) {
        DefaultAssetMap<String, ExtensionAsset> assetMap = event.getAssetMap();
        Map<String, ExtensionAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, ExtensionAsset> entry : assetMap.getAssetMap().entrySet()) {
            layer.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        ExtensionCatalog.getInstance().fold(layer, false);
        Log.info("Extension asset layer: folded " + layer.size() + " extension(s) into ExtensionCatalog: "
                + layer.keySet());
    }

    /**
     * Registers the {@link FlairAsset} Pattern-A store at {@code Server/RpgStations/Flairs}
     * (design section 9.6, phase 2 leg F - the open flair/moment vocabulary's asset-driven half)
     * and folds every loaded entry into {@link FlairCatalog}; {@link StationValidator#runStructuralAndLog}
     * re-runs on THIS fold too (unlike the shared loot stores) for the same structural per-station/
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
     * per-player {@code HudManager} at first ready. Nothing in this jar ever called {@code
     * player.getHudManager().addCustomHud(...)} for this HUD, so {@code StationSummaryHud.tryShow}
     * always failed {@code KeyedCustomHud.get}'s native lookup and every session silently fell
     * back to the plain-toast path, which read in-game as "the completion HUD no longer appears
     * at all". The install shape is the standard custom-HUD one: at {@code PlayerReadyEvent}, with
     * a world.execute hop before any
     * Store/Ref/HudManager touch; {@code HudManager#addCustomHud} itself is replace-safe on a
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
     * The PRODUCTION puppet-presentation route's {@code PlayerReadyEvent} safety net (design
     * section 4.4, leg P5): an unconditional (not gated on any remembered session - a restart
     * wipes every in-memory {@code StationSession} by construction) re-assert of the real
     * player's correct scale/model on the FRESH ready ref/store, mirroring {@link
     * #registerSummaryHudInstall}'s exact world.execute-hop shape.
     */
    private void registerPuppetSafetyNet() {
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
                        StationService.getInstance().reassertPuppetOnReady(ref, ref.getStore());
                    } catch (Throwable t) {
                        Log.warn("Puppet ready safety-net failed: " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                Log.warn("Puppet ready safety-net (outer) failed: " + t.getMessage());
            }
        });
    }

    /**
     * The performer BOOT reconcile (seam wave decision 48/55): a ONE-shot-per-world sweep at first
     * {@code PlayerReadyEvent} that despawns every orphan performer double (a persistent performer
     * whose owning session died with the server - no in-memory {@link StationService} session
     * survives a restart). Gated on the {@code World} object identity ({@link #performerBootSweptWorlds})
     * so a second player's ready in the same world never re-sweeps and clobbers a live puppet.
     * Mirrors {@link #registerPuppetSafetyNet}'s world.execute-hop shape; inert when the identity
     * component is unregistered or no persistent orphan exists (a transient puppet is already gone).
     */
    private void registerPerformerReconcile() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            try {
                Player player = event.getPlayer();
                World world = player.getWorld();
                if (world == null || !performerBootSweptWorlds.add(world)) {
                    return;
                }
                world.execute(() -> {
                    try {
                        Ref<EntityStore> ref = player.getReference();
                        if (ref == null || !ref.isValid()) {
                            return;
                        }
                        StationService.getInstance().reconcilePerformersAtBoot(ref.getStore());
                    } catch (Throwable t) {
                        Log.warn("Performer boot reconcile failed: " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                Log.warn("Performer boot reconcile (outer) failed: " + t.getMessage());
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
        // Scope-2 wave 3 (gate m4): the place-event feed for the lazy station-block index the
        // multi-station anchor discovery reads (StationBlockPlaceSystem); the break side (index
        // removal + ANCHOR_LOST) is handled by StationCustodyBreakSystem -> onCustodyBlockBroken.
        getEntityStoreRegistry().registerSystem(new StationBlockPlaceSystem());
    }

    @Override
    protected void shutdown() {
        StationService.getInstance().stopAll(StationService.StopReason.SERVER_STOP);
        Log.info("RpgStations shutdown complete.");
    }
}
