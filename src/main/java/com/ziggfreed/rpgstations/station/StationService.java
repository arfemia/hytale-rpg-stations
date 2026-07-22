package com.ziggfreed.rpgstations.station;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.ziggfreed.common.camera.CameraShakeService;
import com.ziggfreed.common.cast.ModelParticleService;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.common.cast.WorldKeyedQueues;
import com.ziggfreed.common.cast.step.CastKernel;
import com.ziggfreed.common.i18n.Msg;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.ui.rows.SummaryRow;
import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.SummaryContext;
import com.ziggfreed.rpgstations.api.SummaryDecorateContext;
import com.ziggfreed.rpgstations.api.SummaryEnricher;
import com.ziggfreed.rpgstations.api.XpAsk;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.SummaryEnricherRegistryImpl;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.Roll;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.loot.FactorSnapshot;
import com.ziggfreed.rpgstations.loot.LootEngine;
import com.ziggfreed.rpgstations.ui.StationSummaryHud;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Session lifecycle + work loop for interactive stations. Ported from the MMO Skill Tree's
 * {@code station.StationService} (RPG Stations extraction phase 1, leg 2 - the engine move).
 *
 * <p><b>State machine:</b> {@code IDLE -> STARTING -> WORKING -> STOPPING -> IDLE}. One entry
 * point ({@link #toggle}, from {@code interaction.StationUseInteraction} on the world thread),
 * one idempotent exit funnel ({@link #stop}). Every start-denial is a localized toast, never
 * an interaction {@code Failed}.
 *
 * <p><b>SCOPE NOTE (leg 2 engine move, leg 3 loot + summary, leg 4 api artifact):</b> the port
 * keeps the ENGINE mechanics fully functional (session machine, toggle/stop funnel, heartbeat,
 * the Convert cycle transaction, swing/impact scheduling, idle mode, the {@link #emitMoment}
 * presentation choke point, durability drain, seat mount calls) and SEVERS every MMO-specific
 * progression call the original made ({@code SkillService}-driven XP awards, {@code
 * SkillTreeService}/{@code MasteryService}-driven luck aggregation, {@code ProgressEvents.fire},
 * {@code FeedbackService.emit}, the MMO's session-summary HUD) - those become event firing
 * (LANDED this leg via {@link StationEvents}: {@link #onCycleCompleted} fires {@code
 * StationCycleCompletedEvent} with the station's forwarded {@code Work.Xp} asks + resolved tool
 * multiplier) and the conditional-lootable engine + the standalone-rich summary panel, landed
 * leg 3 ({@link #rollCompletionLoot}, {@link #showSessionSummary} over {@code loot.LootEngine} /
 * {@code ui.StationSummaryHud} - the per-cycle Roll pass moved into {@link StationStepHandlers
 * .RollHandler} at phase-2 leg B's step-engine refactor), now ALSO consulting the api's
 * {@code SummaryEnricherRegistry} union for extra rows/theming (leg 4). The MMO is NOT touched by
 * this leg; its own copy of this class (registered interaction id {@code mmo_station_use})
 * coexists unchanged until leg 5's bridge.
 */
public final class StationService {

    /** Heartbeat cadence (terminate checks + hold refresh); the hold TTL is 2.5x this. */
    private static final long HEARTBEAT_MS = 1000L;

    static final long DEFAULT_CYCLE_MS = 5000L;
    private static final long DEFAULT_MAX_DURATION_MS = 600_000L;
    private static final double DEFAULT_MAX_MOVE_METERS = 1.5;
    private static final String DEFAULT_HOLD_EFFECT = "RPG_Station_Hold";

    /**
     * The implicit single-action id (design section 3.1); phase 2's multi-action stations add
     * named ids on top - see {@link ActionResolver#ACTION_WORK} (the shared constant).
     */
    private static final String ACTION_WORK = ActionResolver.ACTION_WORK;

    /**
     * Every station moment fired through {@link #emitMoment} is a ONE-SHOT beat, so its
     * particle playback is always capped to this many seconds of client playback (an
     * unbounded-spawner particle asset fired at a bare position would otherwise leak forever).
     */
    private static final float MOMENT_PARTICLE_MAX_DURATION_SECONDS = 4.0f;

    /** Every teardown path a session can leave through; drives the localized stop toast. */
    public enum StopReason {
        PLAYER_EXIT, MOVED, DAMAGED, DIED, DISCONNECTED, WORLD_CHANGED, STATION_GONE,
        OUT_OF_INPUTS, INVENTORY_FULL, SESSION_CAP, FEATURE_DISABLED, SERVER_STOP, TOOL_CHANGED,
        /** The held tool broke from the opt-in durability drain. */
        TOOL_BROKEN,
        /**
         * A {@code station.step} program step threw, had no registered handler, or otherwise
         * failed to complete (design section 9.3's M4 fix: a step handler throw is guarded and
         * mapped HERE rather than propagating out of the per-world frame drain). New this leg -
         * the phase-1 implicit program never reaches it (its four steps cannot throw in a way the
         * pre-refactor inline code did not already catch), but any FUTURE authored step program
         * degrades to a clean session stop instead of a world-drain crash.
         */
        STEP_FAILED
    }

    private static final StationService INSTANCE = new StationService();

    private final WorldKeyedQueues<StationSession> sessionsByWorld = new WorldKeyedQueues<>("rpgstations-work");
    private final ConcurrentHashMap<UUID, StationSession> byPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byBlock = new ConcurrentHashMap<>();

    /**
     * Live placed-input custody claims (design section 9.4, phase-2 leg C), keyed by the SAME
     * {@code "<worldUuid>:<x>:<y>:<z>"} block key {@link #byBlock} uses - one per-block claim,
     * never persisted (session-scoped by ruling; a restart/crash loses it, self-healed at the
     * block-state layer on the next interaction - see {@link #toggle}).
     */
    private final ConcurrentHashMap<String, StationCustodyClaim> custodyByBlock = new ConcurrentHashMap<>();

    /**
     * A single-shot {@code (factorId, param) -> value} lookup, pure/testable independent of the
     * live api registry (used by {@link #conditionPasses}). {@link #checkRequires} builds one
     * inline against {@link FactorRegistryImpl} + a fresh {@link FactorContext} (leg 4 - replaces
     * the leg-3 stand-in static {@code factorLookup} field).
     */
    @FunctionalInterface
    interface FactorLookup {
        @Nullable
        Double resolve(@Nonnull String factorId, @Nullable String param);
    }

    private StationService() {
    }

    @Nonnull
    public static StationService getInstance() {
        return INSTANCE;
    }

    /** Called once by the drain system when it registers, so the no-drainer warning stays silent. */
    public void attachDrainer() {
        sessionsByWorld.markDrainerAttached();
    }

    public int activeCount() {
        return byPlayer.size();
    }

    // ==================== STARTING ====================

    /**
     * The one entry point (world thread, from {@code StationUseInteraction}). A re-press
     * while working is the primary exit; otherwise validate (each denial a localized toast)
     * and engage. Never throws.
     */
    public void toggle(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                       @Nonnull PlayerRef playerRef, @Nonnull Player player,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull String stationId, int blockX, int blockY, int blockZ) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        StationSession existing = byPlayer.get(playerUuid);
        if (existing != null) {
            stop(existing, StopReason.PLAYER_EXIT, store);
            return;
        }

        // 1) Feature gate + catalog hit.
        if (!stationsEnabled()) {
            toast(playerRef, RpgMsg.tr("ui.station.locked"));
            return;
        }
        StationAsset asset = StationCatalog.getInstance().getStation(stationId);
        if (asset == null) {
            Log.warn("STATION unknown station id '" + stationId + "' on block interaction");
            toast(playerRef, RpgMsg.tr("ui.station.locked"));
            return;
        }

        // 2) Requires gate (RpgStations' own Permission + Conditions, design section 4.4.2).
        if (!checkRequires(asset.getRequires(), playerRef, asset)) {
            toast(playerRef, RpgMsg.tr("ui.station.locked"));
            return;
        }

        UUID worldUuid = playerRef.getWorldUuid();
        String blockKey = worldUuid + ":" + blockX + ":" + blockY + ":" + blockZ;
        World world;
        try {
            world = WorldEvictors.worldOf(ref);
        } catch (Throwable t) {
            Log.warn("STATION could not resolve world for session start: " + t.getMessage());
            return;
        }

        // 2.5) Placed-input custody (design section 9.4): a state-dependent F BEFORE the classic
        // engage flow - empty + a matching held stack places (or tops up); loaded + owner F falls
        // through to engage, sourcing the convert check from the claim instead of live inventory.
        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, ACTION_WORK);
        Custody custody = action.getCustody();
        if (custody != null) {
            StationCustodyClaim claim = custodyByBlock.get(blockKey);
            if (claim != null && !claim.ownerId.equals(playerUuid)) {
                toast(playerRef, RpgMsg.tr("ui.station.occupied"));
                return;
            }
            boolean loadedBefore = claim != null && claim.totalQuantity() > 0;
            if (!loadedBefore) {
                // Restart-reconcile self-heal (design 9.4): a Loaded block-state surviving a
                // restart with no live claim behind it resets to Empty here, idempotently.
                flipCustodyState(world, blockX, blockY, blockZ, custody, false);
            }
            ItemStack heldForPlacement = player.getInventory().getActiveHotbarItem();
            boolean roomLeft = claim == null || claim.totalQuantity() < custody.effectiveMaxQuantity();
            if (roomLeft && custodyAccepts(custody, asset, heldForPlacement)) {
                int moved = placeIntoCustody(store, ref, blockKey, playerUuid, asset.getId(),
                        action.getActionId(), heldForPlacement, custody);
                if (moved > 0) {
                    if (!loadedBefore) {
                        flipCustodyState(world, blockX, blockY, blockZ, custody, true);
                    }
                    toast(playerRef, RpgMsg.tr(loadedBefore
                            ? "ui.station.custody.topped_up" : "ui.station.custody.placed"));
                    return;
                }
            }
            // Nothing placed (no match, or already full): fall through to engage below.
        }

        // 3) Exclusive occupancy.
        StationAsset.Work work = asset.getWork();
        boolean exclusive = work == null || work.getExclusive() == null || work.getExclusive();
        if (exclusive) {
            UUID occupant = byBlock.get(blockKey);
            if (occupant != null && !occupant.equals(playerUuid)) {
                toast(playerRef, RpgMsg.tr("ui.station.occupied"));
                return;
            }
        }

        // 4) Held-tool gate.
        if (!heldToolMatches(player, asset.getTool())) {
            toast(playerRef, RpgMsg.tr("ui.station.wrong_tool"));
            return;
        }

        // 5) Convert viability (custody-sourced when Custody governs), or idle practice when the
        // station opts in.
        StationAsset.Work.Idle idleGroup = work != null ? work.getIdle() : null;
        boolean idleEnabled = idleGroup != null && idleGroup.getEnabled() != null && idleGroup.getEnabled();
        ConversionCheck check = custody != null
                ? firstRunnableConversionFromCustody(custodyByBlock.get(blockKey), player, asset)
                : firstRunnableConversion(player, asset);
        boolean startIdle = false;
        if (check.state == ConversionState.NO_INPUTS) {
            if (!idleEnabled) {
                toast(playerRef, RpgMsg.tr("ui.station.no_materials"));
                return;
            }
            startIdle = true;
        }
        if (check.state == ConversionState.NO_ROOM) {
            toast(playerRef, RpgMsg.tr("ui.station.inventory_full"));
            return;
        }

        // 6) Engage.
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d pos = transform.getPosition();

        StationSession s = new StationSession();
        s.playerUuid = playerUuid;
        s.ref = ref;
        s.playerRef = playerRef;
        s.stationId = asset.getId();
        s.blockKey = blockKey;
        s.blockX = blockX;
        s.blockY = blockY;
        s.blockZ = blockZ;
        s.startBlockId = blockIdAt(world, blockX, blockY, blockZ);
        StationAsset.Identity identity = asset.getIdentity();
        String authoredIcon = identity != null ? identity.getIcon() : null;
        s.stationIconItemId = authoredIcon != null && !authoredIcon.isBlank()
                ? authoredIcon : blockItemIdAt(world, blockX, blockY, blockZ);
        s.originX = pos.x;
        s.originY = pos.y;
        s.originZ = pos.z;

        s.cycleMs = work != null && work.getCycleMs() != null && work.getCycleMs() > 0
                ? work.getCycleMs() : DEFAULT_CYCLE_MS;
        s.maxDurationMs = work != null && work.getMaxDurationMs() != null && work.getMaxDurationMs() > 0
                ? work.getMaxDurationMs() : DEFAULT_MAX_DURATION_MS;
        double maxMove = work != null && work.getMaxMoveMeters() != null && work.getMaxMoveMeters() > 0
                ? work.getMaxMoveMeters() : DEFAULT_MAX_MOVE_METERS;
        s.maxMoveSq = maxMove * maxMove;
        s.exclusive = exclusive;

        StationAsset.Hold hold = asset.getHold();
        StationAsset.Hold.Mount mountGroup = hold != null ? hold.getMount() : null;
        boolean mounted = mountGroup != null;
        s.entityMountMode = mounted && mountGroup.isEntitySurface();
        s.seatMode = mounted && !s.entityMountMode;
        s.holdEffectId = hold != null && hold.getEffectId() != null && !hold.getEffectId().isBlank()
                ? hold.getEffectId() : DEFAULT_HOLD_EFFECT;
        s.interruptOnDamage = hold == null || hold.getInterruptOnDamage() == null || hold.getInterruptOnDamage();

        if (s.seatMode && !StationMountController.mount(ref, commandBuffer, blockX, blockY, blockZ, pos)) {
            toast(playerRef, RpgMsg.tr("ui.station.seat_unavailable"));
            return;
        }

        StationAsset.Hold.Mount.Entity entityGroup = s.entityMountMode ? mountGroup.getEntity() : null;
        s.entitySteerable = entityGroup != null && entityGroup.effectiveSteerable();
        s.entityDismountOnMove = entityGroup == null || entityGroup.effectiveDismountOnMove();
        if (s.entityMountMode) {
            // Steerable (default false) applies the hold effect + heartbeat snap-back to defeat
            // the native WASD-steers-the-anchor behavior (design 9.2); Steerable true skips both,
            // reserved for a future vehicle-like station.
            Ref<EntityStore> anchorRef = StationEntityMountController.spawnAnchor(commandBuffer, blockX, blockY, blockZ);
            // attach() failing AFTER a successful spawn is an accepted, extremely-low-probability
            // edge case (both calls are simple queued commandBuffer ops, effectively non-throwing
            // under normal operation) - a stray unmounted anchor from that narrow window is a
            // phase-2 spike-item cleanup, not solved here (the commandBuffer's own pending-ref
            // semantics make an immediate same-tick despawn unverified, so it is not attempted).
            if (anchorRef == null || !StationEntityMountController.attach(ref, anchorRef, commandBuffer, entityGroup)) {
                toast(playerRef, RpgMsg.tr("ui.station.mount_unavailable"));
                return;
            }
            s.mountAnchorRef = anchorRef;
        }
        s.movementLock = (!mounted && (hold == null || hold.getMovementLock() == null || hold.getMovementLock()))
                || (s.entityMountMode && !s.entitySteerable);

        StationAsset.Camera camera = asset.getCamera();
        String cameraMode = camera != null && camera.getMode() != null ? camera.getMode() : "ThirdPerson";
        boolean mountDefaultNoCamera = mounted && camera == null;
        s.cameraApplied = !mountDefaultNoCamera && !"None".equalsIgnoreCase(cameraMode);
        s.cameraLocked = camera == null || camera.getLocked() == null || camera.getLocked();
        s.faceBlock = s.cameraApplied && camera != null && camera.getFaceBlock() != null && camera.getFaceBlock();
        s.cameraRecipe = camera != null ? camera.getRecipe() : null;

        StationAsset.Animation animation = asset.getAnimation();
        s.emoteId = animation != null ? animation.getEmoteId() : null;
        s.actionClip = animation != null ? animation.getActionClip() : null;
        s.toolReq = asset.getTool();

        StationAsset.Tool.Durability durability = s.toolReq != null ? s.toolReq.getDurability() : null;
        s.durabilityPerSwing = StationToolScaling.resolvedDurabilityAmount(
                durability != null ? durability.getPerSwing() : null);
        s.durabilityPerCycle = StationToolScaling.resolvedDurabilityAmount(
                durability != null ? durability.getPerCycle() : null);

        StationAsset.Animation.Swing swing = animation != null ? animation.getSwing() : null;
        s.swingIntervalMs = swing != null && swing.getIntervalMs() != null && swing.getIntervalMs() > 0
                ? swing.getIntervalMs() : 0L;
        s.swingPresentation = swing != null ? swing.getPresentation() : null;

        StationAsset.Animation.Swing.Impact impact = swing != null ? swing.getImpact() : null;
        s.impactDelayMs = impact != null && impact.getDelayMs() != null && impact.getDelayMs() > 0
                ? impact.getDelayMs() : 0L;
        s.impactPresentation = impact != null ? impact.getPresentation() : null;
        s.pendingImpactAtMs = 0L;

        s.idleEnabled = idleEnabled;
        s.idleCycleMs = StationToolScaling.resolvedIdleCycleMs(
                idleGroup != null ? idleGroup.getCycleMs() : null, s.cycleMs);
        s.idleXpFraction = StationToolScaling.resolvedXpFraction(
                idleGroup != null ? idleGroup.getXpFraction() : null);
        s.idleMode = startIdle;

        long now = System.currentTimeMillis();
        s.startedAtMs = now;
        s.nextHeartbeatAtMs = now + HEARTBEAT_MS;
        s.nextCycleAtMs = now + (s.idleMode ? s.idleCycleMs : s.cycleMs);
        s.nextSwingAtMs = now + s.swingIntervalMs;

        byPlayer.put(playerUuid, s);
        if (exclusive) {
            byBlock.put(blockKey, playerUuid);
        }
        sessionsByWorld.queueFor(world).offer(s);

        StationHoldController.applyHold(s, store);
        StationHoldController.applyCamera(s);
        if (!s.seatMode) {
            StationHoldController.playEmote(s, store);
        }

        StationEvents.fireSessionStarted(store, s.playerRef, s.playerUuid, s.sessionId, s.stationId,
                ACTION_WORK, s.blockX, s.blockY, s.blockZ, s.idleMode);

        toast(playerRef, RpgMsg.tr("ui.station.start", stationNameMsg(asset)).color(Color.WHITE));

        if (s.idleMode) {
            toast(playerRef, RpgMsg.tr("ui.station.practice"));
        }
    }

    // ==================== WORKING (frame drain) ====================

    /**
     * Tick this world's active sessions once. Called once per world per frame by
     * {@code StationFrameSystem} (extends {@code AbstractWorldFrameSystem}).
     */
    public void tickFrameOnce(@Nonnull World world, @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        ConcurrentLinkedQueue<StationSession> sessions = sessionsByWorld.peek(world);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<StationSession> it = sessions.iterator();
        while (it.hasNext()) {
            StationSession s = it.next();
            if (s.stopped.get()) {
                it.remove();
                continue;
            }
            try {
                if (now >= s.nextHeartbeatAtMs) {
                    s.nextHeartbeatAtMs = now + HEARTBEAT_MS;
                    if (!heartbeat(s, world, store)) {
                        it.remove();
                        continue;
                    }
                }
                if (s.programSuspended) {
                    // A step program (design 9.3) is mid-suspension - bypass the normal
                    // Work.CycleMs cadence gate entirely; resume once its own committed deadline
                    // passes (never re-derived here, matching the kernel's resume contract). The
                    // phase-1 implicit program has no Wait step, so this branch is unreached by
                    // the shipped sawmill; it exists for a future authored Wait-bearing program.
                    if (now >= s.stepDeadlineMs && !resumeCycleProgram(s, store, commandBuffer)) {
                        it.remove();
                        continue;
                    }
                } else if (now >= s.nextCycleAtMs) {
                    s.nextCycleAtMs = now + s.cycleMs;
                    if (!runCycle(s, store, commandBuffer)) {
                        it.remove();
                        continue;
                    }
                }
                if (s.swingIntervalMs > 0 && now >= s.nextSwingAtMs) {
                    s.nextSwingAtMs = now + s.swingIntervalMs;
                    runSwing(s, store);
                }
                if (impactDue(now, s.pendingImpactAtMs)) {
                    s.pendingImpactAtMs = 0L;
                    runImpact(s, store);
                }
            } catch (Throwable t) {
                Log.warn("STATION tick failed: " + t.getMessage(), t);
                stop(s, StopReason.PLAYER_EXIT, store);
                it.remove();
            }
        }
    }

    /** Terminate checks in order + hold TTL refresh. Returns false when the session ended. */
    private boolean heartbeat(@Nonnull StationSession s, @Nonnull World world,
                              @Nonnull Store<EntityStore> store) {
        if (s.ref == null || !s.ref.isValid() || s.ref.getStore() != store) {
            stop(s, StopReason.WORLD_CHANGED, null);
            return false;
        }
        if (blockIdAt(world, s.blockX, s.blockY, s.blockZ) != s.startBlockId) {
            stop(s, StopReason.STATION_GONE, store);
            return false;
        }
        boolean mounted = s.seatMode || s.entityMountMode;
        if (seatModeShouldStop(mounted, StationMountController.isMounted(s.ref, store))) {
            stop(s, StopReason.MOVED, store);
            return false;
        }
        // Walk-off (origin-delta) check: the Block route's native mount snaps the transform (no
        // check needed), and the Entity route only runs it when DismountOnMove is true (default -
        // the entity-mount controller has no native auto-dismount, so this IS the dismount; false
        // = hard-lock until crouch/re-press, the enchanting-circle look, design 9.2).
        if (!s.seatMode && (!s.entityMountMode || s.entityDismountOnMove)) {
            TransformComponent transform = store.getComponent(s.ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                double dx = pos.x - s.originX;
                double dy = pos.y - s.originY;
                double dz = pos.z - s.originZ;
                if (dx * dx + dy * dy + dz * dz > s.maxMoveSq) {
                    stop(s, StopReason.MOVED, store);
                    return false;
                }
            }
        }
        if (s.entityMountMode && !s.entitySteerable) {
            StationEntityMountController.snapBack(s.mountAnchorRef, store, s.blockX, s.blockY, s.blockZ);
        }
        MovementStatesComponent ms = store.getComponent(s.ref, MovementStatesComponent.getComponentType());
        if (ms != null && ms.getMovementStates() != null && ms.getMovementStates().crouching) {
            stop(s, StopReason.PLAYER_EXIT, store);
            return false;
        }
        if (s.toolReq != null) {
            Player heartbeatPlayer = store.getComponent(s.ref, Player.getComponentType());
            boolean matches = heartbeatPlayer != null && heldToolMatches(heartbeatPlayer, s.toolReq);
            ItemStack heldStack = heartbeatPlayer != null
                    ? heartbeatPlayer.getInventory().getActiveHotbarItem() : null;
            boolean broken = heldStack != null && heldStack.isBroken();
            StopReason toolStop = toolGateStopReason(matches, broken);
            if (toolStop != null) {
                if (toolStop == StopReason.TOOL_BROKEN) {
                    String heldItemId = heldStack != null && heldStack.getItemId() != null
                            ? heldStack.getItemId() : "";
                    StationEvents.fireToolBroke(store, s.playerRef, s.playerUuid, s.sessionId, s.stationId,
                            heldItemId);
                }
                stop(s, toolStop, store);
                return false;
            }
        }
        if (System.currentTimeMillis() - s.startedAtMs >= s.maxDurationMs) {
            stop(s, StopReason.SESSION_CAP, store);
            return false;
        }
        if (!stationsEnabled()) {
            stop(s, StopReason.FEATURE_DISABLED, store);
            return false;
        }
        StationHoldController.applyHold(s, store);
        return true;
    }

    /**
     * One cycle: a real Convert cycle when a conversion is runnable, an opt-in idle practice
     * cycle when materials are absent AND the station enables {@code Work.Idle}, or a stop
     * (out-of-inputs / inventory-full) otherwise.
     */
    private boolean runCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                             @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            stop(s, StopReason.STATION_GONE, store);
            return false;
        }
        Player player = store.getComponent(s.ref, Player.getComponentType());
        if (player == null) {
            stop(s, StopReason.WORLD_CHANGED, null);
            return false;
        }

        Custody custody = ActionResolver.resolve(asset, ACTION_WORK).getCustody();
        ConversionCheck check = custody != null
                ? firstRunnableConversionFromCustody(custodyByBlock.get(s.blockKey), player, asset)
                : firstRunnableConversion(player, asset);
        if (check.state == ConversionState.RUNNABLE) {
            if (s.idleMode) {
                s.idleMode = false;
            }
            return runRealCycle(s, store, commandBuffer, asset, player, check);
        } else if (check.state == ConversionState.NO_INPUTS && s.idleEnabled) {
            if (!s.idleMode) {
                s.idleMode = true;
                toast(s.playerRef, RpgMsg.tr("ui.station.practice"));
            }
            s.nextCycleAtMs = System.currentTimeMillis() + s.idleCycleMs;
            return runIdleCycle(s, store, commandBuffer, asset);
        } else if (check.state == ConversionState.NO_INPUTS) {
            stop(s, StopReason.OUT_OF_INPUTS, store);
            return false;
        } else { // NO_ROOM
            stop(s, StopReason.INVENTORY_FULL, store);
            return false;
        }
    }

    /**
     * The real Convert cycle: design 9.3's "one engine, no dual path" - the pre-chosen {@code
     * check} conversion becomes the IMPLICIT four-step program ({@link ImplicitProgram}), walked
     * through {@link #dispatchProgram}, the SAME choke point an authored multi-action
     * {@code Steps} program would use. Precondition: {@code check.state == RUNNABLE}.
     *
     * <p>The implicit program has no {@code Wait} step, so it NEVER suspends - this call always
     * resolves synchronously to {@code Completed} or {@code Failed} within the SAME frame (the
     * byte-stable regression anchor: today's sawmill schedules exactly as before, every
     * pre-refactor behavior test stays green). {@link #resumeCycleProgram} exists for a FUTURE
     * authored non-implicit program's {@code Suspended} case only - never reached by the sawmill.
     */
    private boolean runRealCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
                                 @Nonnull Player player, @Nonnull ConversionCheck check) {
        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, ACTION_WORK);
        int attemptCycleIndex = s.cyclesDone + 1;
        // Sawmill migration (design 9.4): an action authoring Custody ALWAYS draws its implicit
        // Consume from the claim, never the live inventory - the backpack drain the pre-leg-C
        // engine ran per cycle is retired for any station custody governs.
        String consumeFrom = action.getCustody() != null
                ? StationStep.Consume.FROM_CUSTODY : StationStep.Consume.FROM_INVENTORY;
        StationStep.Consume consumeStep = StationStep.Consume.of(
                check.inputIsResource ? null : check.inputRef,
                check.inputIsResource ? check.inputRef : null,
                check.inputCount, consumeFrom);
        StationStep.Produce produceStep = StationStep.Produce.of(check.outputItem, check.outputCount,
                StationStep.Produce.TO_INVENTORY);
        Roll[] resolvedRolls = LootEngine.resolveRolls(action.getLoot()).toArray(new Roll[0]);
        List<StationStep> steps = ImplicitProgram.build(consumeStep, produceStep, resolvedRolls,
                action.getPresentation());
        ItemStack cycleOutput = new ItemStack(check.outputItem, check.outputCount);
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps, cycleOutput,
                attemptCycleIndex, 0);
    }

    /**
     * Re-enters a {@code programSuspended} session's in-flight program at
     * {@code s.programIndex}, called from {@link #tickFrameOnce} once {@code s.stepDeadlineMs}
     * passes - bypassing the normal {@code Work.CycleMs} cadence gate entirely while suspended.
     * Rebuilds NOTHING from live inventory state (the whole point of {@link StationSession}'s
     * {@code activeProgram*} snapshot fields, design 9.3: a resume must never re-derive WHICH
     * conversion is running, since the live inventory may have changed mid-suspension).
     */
    private boolean resumeCycleProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            stop(s, StopReason.STATION_GONE, store);
            return false;
        }
        Player player = store.getComponent(s.ref, Player.getComponentType());
        if (player == null) {
            stop(s, StopReason.WORLD_CHANGED, null);
            return false;
        }
        List<StationStep> steps = s.activeProgramSteps;
        ItemStack cycleOutput = s.activeProgramCycleOutput;
        if (steps == null || cycleOutput == null) {
            Log.warn("STATION resume with no active program snapshot for '" + s.stationId + "' - stopping");
            stop(s, StopReason.STEP_FAILED, store);
            return false;
        }
        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, ACTION_WORK);
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps, cycleOutput,
                s.activeProgramCycleIndex, s.programIndex);
    }

    /**
     * The ONE {@link StationStepKernel} dispatch choke point (fresh start AND resume both funnel
     * here): builds the per-run {@link StationStepContext}, walks {@code steps} from
     * {@code startIndex}, and applies the THREE possible outcomes. {@code s.cyclesDone}
     * increments ONLY on {@code Completed} (never on a mid-program failure - the pre-refactor
     * "only count a real success" invariant); {@code attemptCycleIndex} (the value a Roll/Command
     * step's placeholder + factor-context substitution sees, and the value the cycle-completed
     * event fires with below) is {@code s.cyclesDone + 1}, computed ONCE by the caller before the
     * walk starts so it stays stable across a suspend/resume pair without persisting the counter
     * early.
     */
    private boolean dispatchProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player, @Nonnull List<StationStep> steps,
            @Nonnull ItemStack cycleOutput, int attemptCycleIndex, int startIndex) {
        FactorSnapshot snapshot = new FactorSnapshot(buildFactorContext(s, store, player, asset, attemptCycleIndex));
        StationStepContext ctx = new StationStepContext(s, store, commandBuffer, player, asset, action, snapshot,
                steps, attemptCycleIndex, cycleOutput);

        CastKernel.Walk<StationStepResult> walk = StationStepKernel.runResumable(ctx, startIndex);
        if (walk instanceof CastKernel.Walk.Suspended<StationStepResult> suspended) {
            s.programSuspended = true;
            s.programIndex = suspended.resumeIndex();
            s.activeProgramSteps = steps;
            s.activeProgramCycleOutput = cycleOutput;
            s.activeProgramCycleIndex = attemptCycleIndex;
            return true;
        }
        s.programSuspended = false;
        s.programIndex = 0;
        s.activeProgramSteps = null;
        s.activeProgramCycleOutput = null;
        if (walk instanceof CastKernel.Walk.Failed<StationStepResult> failed) {
            StationStepResult.Fail fail = (StationStepResult.Fail) failed.result();
            Log.warn("STATION step program failed for '" + s.stationId + "' at step index "
                    + failed.atIndex() + ": " + fail.message());
            stop(s, fail.reason(), store);
            return false;
        }

        s.cyclesDone++;
        if (s.durabilityPerCycle > 0) {
            drainHeldToolDurability(store, s.ref, player, s.durabilityPerCycle);
        }
        double xpMult = resolveXpMultiplier(player, asset);
        onCycleCompleted(s, store, commandBuffer, asset, xpMult, false, s.cyclesDone);
        return true;
    }

    /**
     * A normalized {@code (itemId, consumedAmount)} read of one live
     * {@code ResourceSlotTransaction}'s pre-removal stack, for the PURE
     * {@link #tallyConsumedResource} core.
     */
    static final class ConsumedSlot {
        @Nullable final String itemId;
        final int consumed;

        ConsumedSlot(@Nullable String itemId, int consumed) {
            this.itemId = itemId;
            this.consumed = consumed;
        }
    }

    /**
     * PURE core: fold {@code consumedSlots} into {@code tally} (summing per item id), falling
     * back to tallying the raw {@code resourceTypeId} itself only when no slot yielded a
     * usable item id.
     */
    static void tallyConsumedResource(@Nonnull Map<String, Integer> tally, @Nonnull List<ConsumedSlot> consumedSlots,
                                      @Nonnull String resourceTypeId) {
        boolean any = false;
        for (ConsumedSlot slot : consumedSlots) {
            if (slot == null || slot.itemId == null || slot.consumed <= 0) {
                continue;
            }
            tally.merge(slot.itemId, slot.consumed, Integer::sum);
            any = true;
        }
        if (!any) {
            tally.merge(resourceTypeId, 1, Integer::sum);
        }
    }

    /**
     * Item-ledger tally for a {@code ResourceTypeId} ("any log" family) consume: the
     * transactional {@code removeResource} call returns which concrete item id(s) it actually
     * drained via each {@link ResourceSlotTransaction}'s pre-removal stack.
     */
    static void tallyResourceConsumption(@Nonnull StationSession s, @Nullable ResourceTransaction tx,
                                                 @Nonnull String resourceTypeId) {
        List<ConsumedSlot> slots = new ArrayList<>();
        if (tx != null) {
            for (ResourceSlotTransaction slotTx : tx.getList()) {
                if (slotTx != null && slotTx.succeeded() && slotTx.getConsumed() > 0) {
                    ItemStack before = slotTx.getSlotBefore();
                    slots.add(new ConsumedSlot(before != null ? before.getItemId() : null, slotTx.getConsumed()));
                }
            }
        }
        tallyConsumedResource(s.consumedItems, slots, resourceTypeId);
    }

    /**
     * Opt-in idle practice cycle: NO conversion, NO loot roll, NO progress fire - just the
     * cycle presentation + the (idle-scaled) XP-ask forwarding.
     */
    private boolean runIdleCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset) {
        onCycleCompleted(s, store, commandBuffer, asset, 1.0, true, s.cyclesDone);

        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.Slot.CYCLE, asset.getPresentation(), blockPos);
        s.cyclesDone++;
        return true;
    }

    /**
     * Fires {@code StationCycleCompletedEvent} (design section 3.1/7.2): forwards this cycle's
     * {@code Work.Xp} asks (idle-scaled by {@code Work.Idle.XpFraction} when {@code idle}) plus
     * the resolved tool multiplier (forced {@code 1.0} for an idle cycle). A listening
     * progression mod (the MMO bridge) reads {@code asset.getWork().getXp()} semantics off the
     * event's {@code XpAsk} list to know what an ask means; this engine never interprets it.
     *
     * <p>{@code commandBuffer} is GUARANTEED non-null here: both call sites (the real-cycle path
     * in {@link #runRealCycle} and the idle-cycle path in {@link #runIdleCycle}) run inside the
     * per-world frame drain ({@link #tickFrameOnce}), which always holds a live {@code
     * CommandBuffer} for the current tick (critique fix, binding - see the event's own javadoc).
     */
    private static void onCycleCompleted(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            double toolMultiplier, boolean idle, int cycleIndex) {
        List<XpAsk> asks = xpAsks(asset, idle, s.idleXpFraction);
        StationEvents.fireCycleCompleted(store, commandBuffer, s.playerRef, s.playerUuid, s.sessionId,
                s.stationId, ACTION_WORK, cycleIndex, idle, asks, toolMultiplier);
    }

    /**
     * The station's forwarded {@code Work.Xp} asks for one cycle-completed event (design section
     * 4.4.1): a real cycle forwards the RAW authored {@code PerCycle} (the listener multiplies
     * by {@link StationCycleCompletedEvent#toolMultiplier()}); an idle cycle pre-scales each ask
     * by {@code idleXpFraction} and the caller forces {@code toolMultiplier} to {@code 1.0}
     * (matching today's idle semantics: fractional XP, no progress). A blank/missing skill id
     * entry is skipped (the validator's {@code MISSING_XP_SKILL} catches the authoring mistake).
     */
    @Nonnull
    private static List<XpAsk> xpAsks(@Nonnull StationAsset asset, boolean idle, double idleXpFraction) {
        StationAsset.Work work = asset.getWork();
        StationAsset.WorkXp[] xp = work != null ? work.getXp() : null;
        if (xp == null || xp.length == 0) {
            return List.of();
        }
        List<XpAsk> out = new ArrayList<>(xp.length);
        for (StationAsset.WorkXp x : xp) {
            if (x == null || x.getSkill() == null || x.getSkill().isBlank()) {
                continue;
            }
            double perCycle = x.getPerCycle() != null ? x.getPerCycle() : 0.0;
            out.add(new XpAsk(x.getSkill(), idle ? perCycle * idleXpFraction : perCycle));
        }
        return out;
    }

    /** The station's authored {@code Work.Xp} skill ids, in authoring order (for {@link FactorContext#progressionSkills()}). */
    @Nonnull
    private static List<String> progressionSkills(@Nonnull StationAsset asset) {
        StationAsset.Work work = asset.getWork();
        StationAsset.WorkXp[] xp = work != null ? work.getXp() : null;
        if (xp == null || xp.length == 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>(xp.length);
        for (StationAsset.WorkXp x : xp) {
            if (x != null && x.getSkill() != null && !x.getSkill().isBlank()) {
                out.add(x.getSkill());
            }
        }
        return out;
    }

    /**
     * The Completion-trigger loot pass (design section 4.5.1's {@code "Completion"} trigger,
     * non-silent, {@code cyclesDone >= 1}): runs BEFORE {@link #showSessionSummary} in {@link
     * #stop} so any items it grants still appear in the session's item ledger. No live cycle
     * output exists here ({@code cycleOutput} null), so a {@code Grants.BonusOutputCopies}
     * authored under a Completion roll is silently inert (the validator warns at author time,
     * M3 fix 5).
     */
    private void rollCompletionLoot(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            return;
        }
        List<Roll> rolls = LootEngine.resolveRolls(asset.getLoot());
        if (rolls.isEmpty()) {
            return;
        }
        Player player = store.getComponent(s.ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        FactorSnapshot snapshot = new FactorSnapshot(buildFactorContext(s, store, player, asset));
        LootEngine.GrantResult result = LootEngine.rollAndGrant(rolls, Roll.TRIGGER_COMPLETION, snapshot, player,
                null, s.playerRef, s.stationId, ACTION_WORK, s.cyclesDone);
        applyGrantResult(s, store, result);
    }

    /**
     * Folds a {@link LootEngine.GrantResult} into the session's item ledger, plays every
     * reached floor's {@code Presentation} through {@link #emitMoment} on {@link
     * StationFlairs.Slot#RARE_FIND}, and fires the two keyed notifications (design 4.5.1):
     * bonus-copy grants -> {@code ui.station.lucky}, droplist grants -> {@code
     * ui.station.rare_find}. Both may fire on the same pass (independent grant kinds).
     */
    static void applyGrantResult(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull LootEngine.GrantResult result) {
        if (!result.anyGranted()) {
            return;
        }
        for (Map.Entry<String, Integer> e : result.getBonusCopyItems().entrySet()) {
            s.luckItems.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : result.getDropListItems().entrySet()) {
            s.luckItems.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        for (Presentation p : result.getFloorPresentations()) {
            emitMoment(store, s, StationFlairs.Slot.RARE_FIND, p, blockPos);
        }
        if (s.playerRef != null) {
            if (!result.getBonusCopyItems().isEmpty()) {
                toast(s.playerRef, RpgMsg.tr("ui.station.lucky"));
            }
            if (!result.getDropListItems().isEmpty()) {
                toast(s.playerRef, RpgMsg.tr("ui.station.rare_find"));
            }
        }
    }

    /**
     * Per-cycle api {@link FactorContext} for the built-in {@code rpgstations:} factors ({@code
     * api.impl.FactorRegistryImpl#registerBuiltins}) plus every other registered provider:
     * session seconds elapsed, the CURRENT (already-incremented) cycle index, and the
     * currently-held item's tool power / durability percent - read fresh, mirroring {@link
     * #resolveXpMultiplier}'s no-snapshot convention.
     */
    @Nonnull
    private static FactorContext buildFactorContext(@Nonnull StationSession s, @Nullable Store<EntityStore> store,
            @Nonnull Player player, @Nonnull StationAsset asset) {
        return buildFactorContext(s, store, player, asset, s.cyclesDone);
    }

    /**
     * {@code cycleIndex}-overriding form: {@link #dispatchProgram} passes the ATTEMPT index
     * (design section 9.3 - {@code s.cyclesDone + 1}, computed before {@code s.cyclesDone} itself
     * advances) so a Roll step's factor context sees the cycle it is actually running, not the
     * last COMPLETED one.
     */
    private static FactorContext buildFactorContext(@Nonnull StationSession s, @Nullable Store<EntityStore> store,
            @Nonnull Player player, @Nonnull StationAsset asset, int cycleIndex) {
        long sessionSeconds = Math.max(0L, (System.currentTimeMillis() - s.startedAtMs) / 1000L);
        return FactorContext.builder()
                .store(store)
                .playerRef(s.playerRef)
                .playerId(s.playerUuid)
                .stationId(s.stationId)
                .actionId(ACTION_WORK)
                .sessionSeconds(sessionSeconds)
                .cycleIndex(cycleIndex)
                .toolPower(resolveHeldToolPower(player, asset.getTool()))
                .toolDurabilityPercent(resolveHeldToolDurabilityPercent(player))
                .progressionSkills(progressionSkills(asset))
                .build();
    }

    /**
     * The held tool's power for the station's effective gather type ({@code Tool.Gather.GatherType}
     * only - unlike {@link #resolveXpMultiplier}, this reads regardless of whether {@code
     * Tool.XpScale} is authored, since {@code rpgstations:tool_power} is a general-purpose
     * factor, not an XP multiplier). 0 when no gather type resolves or no matching spec is held.
     */
    private static double resolveHeldToolPower(@Nonnull Player player, @Nullable StationAsset.Tool tool) {
        StationAsset.Tool.Gather gather = tool != null ? tool.getGather() : null;
        String gatherType = gather != null ? gather.getGatherType() : null;
        if (gatherType == null || gatherType.isBlank()) {
            return 0.0;
        }
        ItemStack held = player.getInventory().getActiveHotbarItem();
        Item item = held != null ? held.getItem() : null;
        ItemTool itemTool = item != null ? item.getTool() : null;
        ItemToolSpec[] specs = itemTool != null ? itemTool.getSpecs() : null;
        return StationToolScaling.heldPowerFor(toolPowers(specs), gatherType);
    }

    /** The active hotbar item's durability percent [0,100]; 100 when no item held or it tracks no durability. */
    private static double resolveHeldToolDurabilityPercent(@Nonnull Player player) {
        ItemStack held = player.getInventory().getActiveHotbarItem();
        if (held == null || held.isEmpty() || held.getMaxDurability() <= 0) {
            return 100.0;
        }
        return Math.max(0.0, Math.min(100.0, (held.getDurability() / held.getMaxDurability()) * 100.0));
    }

    /**
     * The standalone-rich end-of-session summary panel ({@code ui.StationSummaryHud}, design
     * section 4.1/4.3): title + a cycles-only body + every registered {@code SummaryEnricher}'s
     * extra rows PREPENDED before the engine's own item ledger (design section 3.2/7.2-7.3 - the
     * MMO bridge's per-skill XP rows land here, leg 5) + a post-build {@code decorate} pass for
     * cross-jar theming. Falls back to the classic {@code NotificationUtil} toast (cycles-only
     * body, no ledger rows - a text toast has no icon slot) on a settings-disabled HUD, an
     * unregistered instance, or a push failure.
     */
    private void showSessionSummary(@Nonnull StationSession s, @Nullable Store<EntityStore> store) {
        if (s.playerRef == null) {
            return;
        }
        Message title = RpgMsg.tr("ui.station.summary.title");
        Message body = RpgMsg.tr("ui.station.summary.cycles", s.cyclesDone);
        List<SummaryEnricher> enrichers = SummaryEnricherRegistryImpl.getInstance().enrichers();
        List<SummaryRow> extraRows = enricherRows(s, store, enrichers);
        Consumer<UICommandBuilder> decorateHook = enrichers.isEmpty() ? null : cmd -> decorate(s, cmd, enrichers);
        if (!StationSummaryHud.tryShow(s.playerRef, title, body, s.stationIconItemId, extraRows, ledgerRows(s),
                decorateHook)) {
            toast(s.playerRef, body);
        }
    }

    /**
     * Every registered {@link SummaryEnricher}'s {@code rows()}, concatenated in registration
     * order. Never throws; a throwing enricher is skipped so the rest of the summary still
     * renders. Empty (zero-cost) when no enricher is registered.
     */
    @Nonnull
    private static List<SummaryRow> enricherRows(@Nonnull StationSession s, @Nullable Store<EntityStore> store,
            @Nonnull List<SummaryEnricher> enrichers) {
        if (enrichers.isEmpty()) {
            return List.of();
        }
        SummaryContext ctx = new SummaryContext(s.playerUuid, s.sessionId, s.stationId, s.cyclesDone,
                System.currentTimeMillis() - s.startedAtMs, store, s.playerRef);
        List<SummaryRow> out = new ArrayList<>();
        for (SummaryEnricher e : enrichers) {
            try {
                List<SummaryRow> rows = e.rows(ctx);
                if (rows != null) {
                    out.addAll(rows);
                }
            } catch (Throwable t) {
                Log.fine("STATION summary enricher rows() threw: " + t.getMessage());
            }
        }
        return out;
    }

    /**
     * The summary panel's post-build theming pass (design section 3.2): {@link
     * StationSummaryHud#ROOT_SELECTOR} is the FROZEN root selector every enricher's {@code
     * decorate} writes against. Never throws; a throwing enricher is skipped.
     */
    private static void decorate(@Nonnull StationSession s, @Nonnull UICommandBuilder cmd,
            @Nonnull List<SummaryEnricher> enrichers) {
        SummaryDecorateContext ctx = new SummaryDecorateContext(cmd, StationSummaryHud.ROOT_SELECTOR, s.playerRef);
        for (SummaryEnricher e : enrichers) {
            try {
                e.decorate(ctx);
            } catch (Throwable t) {
                Log.fine("STATION summary enricher decorate() threw: " + t.getMessage());
            }
        }
    }

    /**
     * The item ledger for the summary panel: consumed items first, then produced, then luck
     * grants (bonus-copy + droplist), each carrying the native client-resolved item name
     * ({@link #itemNameMsg}).
     */
    @Nonnull
    private static List<StationSummaryHud.LedgerRow> ledgerRows(@Nonnull StationSession s) {
        List<StationSummaryHud.LedgerRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> e : s.consumedItems.entrySet()) {
            Message line = RpgMsg.tr("ui.station.summary.item_consumed", itemNameMsg(e.getKey()), e.getValue());
            rows.add(new StationSummaryHud.LedgerRow(e.getKey(), e.getValue(), line, SummaryRow.Kind.CONSUMED));
        }
        for (Map.Entry<String, Integer> e : s.producedItems.entrySet()) {
            Message line = RpgMsg.tr("ui.station.summary.item_produced", itemNameMsg(e.getKey()), e.getValue());
            rows.add(new StationSummaryHud.LedgerRow(e.getKey(), e.getValue(), line, SummaryRow.Kind.PRODUCED));
        }
        for (Map.Entry<String, Integer> e : s.luckItems.entrySet()) {
            Message line = Msg.cat(
                    RpgMsg.tr("ui.station.summary.item_produced", itemNameMsg(e.getKey()), e.getValue()),
                    RpgMsg.tr("ui.station.summary.lucky"));
            rows.add(new StationSummaryHud.LedgerRow(e.getKey(), e.getValue(), line, SummaryRow.Kind.LUCKY));
        }
        return rows;
    }

    /**
     * The ONE presentation-playback choke point: every station moment funnels through here.
     * Resolves the effective presentation through {@link StationFlairs#effective} FIRST, then
     * plays {@code Sound}, {@code Particles}, and (new this leg) {@code Shake} - see
     * {@link Presentation.Shake}'s javadoc for the exact {@code CameraShakeService} parameter
     * shape this leaf was verified against (critique m6 binding fix). Shake needs the player
     * SPECIFICALLY (not "nearby players" like Sound3D/ModelParticleService), so it reads
     * {@code s.playerRef} rather than {@code targetPos}.
     */
    static void emitMoment(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
                                   @Nonnull StationFlairs.Slot slot, @Nullable Presentation base,
                                   @Nonnull Vector3d targetPos) {
        Presentation p = StationFlairs.effective(base, cachedFlairs(s), slot, s.playerUuid, s.stationId);
        if (p == null) {
            return;
        }
        if (p.getSound() != null && !p.getSound().isBlank()) {
            Sound3D.play(p.getSound(), targetPos, store, "STATION");
        }
        if (p.getParticles() != null && !p.getParticles().isBlank()) {
            ModelParticleService.spawnAt(store, p.getParticles(), targetPos, MOMENT_PARTICLE_MAX_DURATION_SECONDS);
        }
        Presentation.Shake shake = p.getShake();
        if (shake != null && shake.getEffectId() != null && !shake.getEffectId().isBlank()) {
            float intensity = shake.getIntensity() != null ? shake.getIntensity().floatValue() : 1.0f;
            CameraShakeService.shake(s.playerRef, shake.getEffectId(), intensity);
        }
    }

    /**
     * The station's authored {@code Flairs} map, or {@code null} when the station authors none
     * OR a mid-session catalog re-fold has dropped the station entirely.
     */
    @Nullable
    private static Map<String, StationAsset.Flair> cachedFlairs(@Nonnull StationSession s) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        return asset != null ? asset.getFlairs() : null;
    }

    /**
     * The per-swing cadence cue: re-fires the work animation as a ONE-SHOT TOGETHER with the
     * session's snapshotted {@link StationSession#swingPresentation} at the block. The clip
     * re-fire routes by {@link StationSession#seatMode}.
     */
    private void runSwing(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        if (StationCatalog.getInstance().getStation(s.stationId) == null) {
            return;
        }
        Player swingPlayer = store.getComponent(s.ref, Player.getComponentType());
        if (useActionSlotForSwing(s.seatMode)) {
            StationHoldController.playActionSwing(s, swingPlayer, store);
        } else {
            StationHoldController.playEmote(s, store);
        }
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.Slot.SWING, s.swingPresentation, blockPos);

        if (s.impactDelayMs > 0 && s.impactPresentation != null) {
            s.pendingImpactAtMs = scheduleImpactAt(System.currentTimeMillis(), s.impactDelayMs);
        }

        if (s.durabilityPerSwing > 0 && !s.idleMode && swingPlayer != null) {
            drainHeldToolDurability(store, s.ref, swingPlayer, s.durabilityPerSwing);
        }
    }

    /** The pure seat-vs-effect swing-route decision. */
    static boolean useActionSlotForSwing(boolean seatMode) {
        return seatMode;
    }

    /** The delayed swing-impact cue itself, on the SAME {@code Slot.SWING} resolution the swing cue uses. */
    private static void runImpact(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.Slot.SWING, s.impactPresentation, blockPos);
    }

    /** Pure due-time scheduling for the delayed swing-impact cue. */
    static long scheduleImpactAt(long nowMs, long delayMs) {
        return nowMs + delayMs;
    }

    /** Pure due-time check for the delayed swing-impact cue. */
    static boolean impactDue(long nowMs, long pendingImpactAtMs) {
        return pendingImpactAtMs > 0 && nowMs >= pendingImpactAtMs;
    }

    /**
     * The tool-power multiplier for THIS cycle: resolves the station's EFFECTIVE gather type,
     * reads the held item's max matching {@code ItemToolSpec} power, and delegates the clamp
     * formula to {@link StationToolScaling}. Returns 1.0 when the station authors no
     * {@code Tool.XpScale}, or when neither gather type resolves.
     */
    private static double resolveXpMultiplier(@Nonnull Player player, @Nonnull StationAsset asset) {
        StationAsset.Tool tool = asset.getTool();
        StationAsset.Tool.XpScale scale = tool != null ? tool.getXpScale() : null;
        if (scale == null) {
            return 1.0;
        }
        String gatherType = scale.getGatherType() != null && !scale.getGatherType().isBlank()
                ? scale.getGatherType()
                : (tool.getGather() != null ? tool.getGather().getGatherType() : null);
        if (gatherType == null || gatherType.isBlank()) {
            return 1.0;
        }
        ItemStack held = player.getInventory().getActiveHotbarItem();
        Item item = held != null ? held.getItem() : null;
        ItemTool itemTool = item != null ? item.getTool() : null;
        ItemToolSpec[] specs = itemTool != null ? itemTool.getSpecs() : null;
        double heldPower = StationToolScaling.heldPowerFor(toolPowers(specs), gatherType);
        return StationToolScaling.multiplier(heldPower, scale);
    }

    /** Adapts live {@code ItemToolSpec}s to the pure {@code StationToolScaling.ToolPower} shape. */
    @Nonnull
    private static List<StationToolScaling.ToolPower> toolPowers(@Nullable ItemToolSpec[] specs) {
        if (specs == null || specs.length == 0) {
            return List.of();
        }
        List<StationToolScaling.ToolPower> out = new ArrayList<>(specs.length);
        for (ItemToolSpec spec : specs) {
            if (spec != null) {
                out.add(new StationToolScaling.ToolPower(spec.getGatherType(), spec.getPower()));
            }
        }
        return out;
    }

    // ==================== STOPPING ====================

    /**
     * The pure gate for the session-completion presentation moment: fires only for a
     * NON-SILENT stop with at least one completed cycle.
     */
    static boolean shouldPlayCompletion(boolean silent, int cyclesDone) {
        return !silent && cyclesDone >= 1;
    }

    /**
     * The session-completion presentation moment itself: plays the station's
     * {@link StationAsset#getCompletion} through the SAME {@link #emitMoment} choke point, on
     * the {@code StationFlairs.Slot#COMPLETION} slot. Plays at the PLAYER's position, not the
     * block (completion celebrates the player).
     */
    private static void playCompletionMoment(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            return;
        }
        TransformComponent transform = store.getComponent(s.ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d playerPos = transform.getPosition();
        emitMoment(store, s, StationFlairs.Slot.COMPLETION, asset.getCompletion(), playerPos);
    }

    /**
     * The one idempotent exit funnel. Each teardown step is individually guarded so one
     * failure never skips the rest. {@code store} is null on paths where the entity is gone.
     */
    private void stop(@Nonnull StationSession s, @Nonnull StopReason reason,
                      @Nullable Store<EntityStore> store) {
        if (!s.stopped.compareAndSet(false, true)) {
            return;
        }
        byPlayer.remove(s.playerUuid, s);
        if (s.blockKey != null) {
            byBlock.remove(s.blockKey, s.playerUuid);
        }
        s.pendingImpactAtMs = 0L;

        // Placed-input custody auto-return (design 9.4): EVERY exit path, silent included - the
        // design's own binding list (re-press, walk-off, damage, death, disconnect, tool-changed,
        // out-of-inputs, inventory-full, session-cap, feature-disabled, step-failed, server-stop)
        // funnels through this ONE call, unconditionally, before any of the notification logic
        // below runs.
        StationAsset stopAsset = StationCatalog.getInstance().getStation(s.stationId);
        Custody stopCustody = stopAsset != null ? ActionResolver.resolve(stopAsset, ACTION_WORK).getCustody() : null;
        returnCustody(s, stopCustody);

        boolean entityAlive = store != null && s.ref != null && s.ref.isValid() && s.ref.getStore() == store;
        boolean silent = reason == StopReason.DISCONNECTED || reason == StopReason.SERVER_STOP
                || reason == StopReason.DIED || reason == StopReason.WORLD_CHANGED;

        if (entityAlive) {
            StationHoldController.stopEmote(s, store);
        }
        if (reason != StopReason.SERVER_STOP) {
            StationHoldController.resetCamera(s.playerRef);
        }
        if (entityAlive) {
            StationHoldController.releaseHold(s, store);
        }
        if ((s.seatMode || s.entityMountMode) && entityAlive) {
            // Same removal call for BOTH Mount surfaces - it just clears MountedComponent,
            // agnostic of Block vs Entity controller type.
            StationMountController.dismount(s.ref, store);
        }
        if (s.entityMountMode) {
            // The anchor's own store/ref is independent of the player's (entityAlive above answers
            // "is the PLAYER still here", not "is the anchor's world/store still resolvable") - fall
            // back to the anchor ref's own store so a WORLD_CHANGED/DISCONNECTED stop (store param
            // null) still despawns it, same reasoning as returnCustody's ownerStore resolution.
            Store<EntityStore> anchorStore = store;
            if (anchorStore == null && s.mountAnchorRef != null && s.mountAnchorRef.isValid()) {
                try {
                    anchorStore = s.mountAnchorRef.getStore();
                } catch (Throwable ignored) {
                    anchorStore = null;
                }
            }
            StationEntityMountController.despawn(s.mountAnchorRef, anchorStore);
        }
        if (!silent && s.playerRef != null) {
            try {
                String reasonKey = stopReasonKey(reason);
                if (reasonKey != null) {
                    toast(s.playerRef, RpgMsg.tr(reasonKey));
                }
                // The Completion-trigger loot pass runs BEFORE the summary so anything it grants
                // still lands in the item ledger the summary renders (design section 4.5.1).
                if (entityAlive && s.cyclesDone >= 1) {
                    rollCompletionLoot(s, store);
                }
                if (s.cyclesDone > 0) {
                    // Summary enrichers (design section 7.2/7.3) run INSIDE this call, before the
                    // unconditional StationSessionCompletedEvent fires below.
                    showSessionSummary(s, store);
                }
                if (entityAlive && shouldPlayCompletion(silent, s.cyclesDone)) {
                    playCompletionMoment(s, store);
                }
            } catch (Throwable t) {
                Log.fine("STATION stop notification failed: " + t.getMessage());
            }
        }
        // The ONE unconditional cleanup signal (design section 3.1/7.3): fires for EVERY stop,
        // silent included, AFTER the non-silent summary (enrichers included) + completion moment
        // above - a listener always sees its enricher state before this clears it session-side.
        StationEvents.fireSessionCompleted(store, s.playerRef, s.playerUuid, s.sessionId, s.stationId,
                reason.name(), silent, s.cyclesDone, System.currentTimeMillis() - s.startedAtMs);
        Log.fine("STATION session ended (" + reason + ") for " + s.playerUuid
                + " at " + s.stationId + " after " + s.cyclesDone + " cycle(s)");
    }

    // ==================== External exit hooks ====================

    /** Damage interrupt (from {@code StationInterruptDamageSystem}, Inspect group, read-only). */
    public void onDamage(@Nonnull Ref<EntityStore> victimRef, @Nonnull Store<EntityStore> store) {
        if (byPlayer.isEmpty()) {
            return;
        }
        for (StationSession s : byPlayer.values()) {
            if (s.ref != null && s.ref.getStore() == store
                    && s.ref.getIndex() == victimRef.getIndex() && s.interruptOnDamage) {
                stop(s, StopReason.DAMAGED, store);
                return;
            }
        }
    }

    /** Death hook - camera reset fires before the respawn screen. */
    public void stopForRef(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull StopReason reason) {
        for (StationSession s : byPlayer.values()) {
            if (s.ref != null && s.ref.getStore() == store && s.ref.getIndex() == ref.getIndex()) {
                stop(s, reason, store);
                return;
            }
        }
    }

    /** Disconnect hook; no store, entity is gone. */
    public void stopFor(@Nonnull UUID playerUuid, @Nonnull StopReason reason) {
        StationSession s = byPlayer.get(playerUuid);
        if (s != null) {
            stop(s, reason, null);
        }
    }

    /** Server shutdown: best-effort teardown of every live session. */
    public void stopAll(@Nonnull StopReason reason) {
        for (StationSession s : new ArrayList<>(byPlayer.values())) {
            stop(s, reason, null);
        }
    }

    // ==================== Convert transaction core ====================

    private enum ConversionState { RUNNABLE, NO_INPUTS, NO_ROOM }

    private static final class ConversionCheck {
        final ConversionState state;
        final boolean inputIsResource;
        final String inputRef;
        final int inputCount;
        final String outputItem;
        final int outputCount;

        ConversionCheck(ConversionState state, boolean inputIsResource, String inputRef, int inputCount,
                        String outputItem, int outputCount) {
            this.state = state;
            this.inputIsResource = inputIsResource;
            this.inputRef = inputRef;
            this.inputCount = inputCount;
            this.outputItem = outputItem;
            this.outputCount = outputCount;
        }
    }

    /**
     * Scan {@code Recipe.Conversions} in order; the FIRST whose input the inventory satisfies
     * wins. {@code NO_ROOM} is reported only when some conversion had its input but lacked
     * output room.
     */
    @Nonnull
    private ConversionCheck firstRunnableConversion(@Nonnull Player player, @Nonnull StationAsset asset) {
        StationAsset.Conversion[] conversions = StationCatalog.getInstance().resolvedConversions(asset);
        if (conversions == null || conversions.length == 0) {
            return new ConversionCheck(ConversionState.NO_INPUTS, false, null, 0, null, 0);
        }
        boolean sawInputWithoutRoom = false;
        try {
            var combined = player.getInventory().getCombinedBackpackStorageHotbar();
            var backpack = player.getInventory().getStorage();
            for (StationAsset.Conversion c : conversions) {
                if (c == null || c.getInput() == null || c.getOutput() == null) {
                    continue;
                }
                StationAsset.Ingredient in = c.getInput();
                StationAsset.Ingredient out = c.getOutput();
                String outItem = out.getItemId();
                if (outItem == null || outItem.isBlank()) {
                    continue;
                }
                String resourceId = in.getResourceTypeId();
                boolean isResource = resourceId != null && !resourceId.isBlank();
                String inputRef = isResource ? resourceId : in.getItemId();
                if (inputRef == null || inputRef.isBlank()) {
                    continue;
                }
                int inCount = in.getQuantity() != null && in.getQuantity() > 0 ? in.getQuantity() : 1;
                int outCount = out.getQuantity() != null && out.getQuantity() > 0 ? out.getQuantity() : 1;
                boolean hasInput = isResource
                        ? combined.canRemoveResource(new ResourceQuantity(inputRef, inCount))
                        : combined.canRemoveItemStacks(List.of(new ItemStack(inputRef, inCount)));
                if (!hasInput) {
                    continue;
                }
                if (!backpack.canAddItemStacks(List.of(new ItemStack(outItem, outCount)))) {
                    sawInputWithoutRoom = true;
                    continue;
                }
                return new ConversionCheck(ConversionState.RUNNABLE, isResource, inputRef, inCount,
                        outItem, outCount);
            }
        } catch (Throwable t) {
            Log.warn("STATION inventory check failed: " + t.getMessage());
        }
        return new ConversionCheck(
                sawInputWithoutRoom ? ConversionState.NO_ROOM : ConversionState.NO_INPUTS,
                false, null, 0, null, 0);
    }

    /**
     * The custody-sourced sibling of {@link #firstRunnableConversion} (design section 9.4): the
     * SAME {@code Recipe.Conversions} scan, but availability reads {@code claim} (the placed-input
     * pouch) instead of the player's live inventory - output room is STILL checked against the
     * player's real inventory (only the input side moved into custody at placement; {@code Produce}
     * always writes {@code To: Inventory}). A null/empty {@code claim} always yields
     * {@code NO_INPUTS} (an empty custody station behaves exactly like an out-of-materials one, so
     * the existing idle-practice fallback in {@link #toggle}/{@link #runCycle} applies unchanged).
     */
    @Nonnull
    private ConversionCheck firstRunnableConversionFromCustody(@Nullable StationCustodyClaim claim,
            @Nonnull Player player, @Nonnull StationAsset asset) {
        StationAsset.Conversion[] conversions = StationCatalog.getInstance().resolvedConversions(asset);
        if (conversions == null || conversions.length == 0 || claim == null) {
            return new ConversionCheck(ConversionState.NO_INPUTS, false, null, 0, null, 0);
        }
        boolean sawInputWithoutRoom = false;
        try {
            var backpack = player.getInventory().getStorage();
            for (StationAsset.Conversion c : conversions) {
                if (c == null || c.getInput() == null || c.getOutput() == null) {
                    continue;
                }
                StationAsset.Ingredient in = c.getInput();
                StationAsset.Ingredient out = c.getOutput();
                String outItem = out.getItemId();
                if (outItem == null || outItem.isBlank()) {
                    continue;
                }
                String resourceId = in.getResourceTypeId();
                boolean isResource = resourceId != null && !resourceId.isBlank();
                String inputRef = isResource ? resourceId : in.getItemId();
                if (inputRef == null || inputRef.isBlank()) {
                    continue;
                }
                int inCount = in.getQuantity() != null && in.getQuantity() > 0 ? in.getQuantity() : 1;
                int outCount = out.getQuantity() != null && out.getQuantity() > 0 ? out.getQuantity() : 1;
                int have = StationCustody.available(claim, isResource ? null : inputRef,
                        isResource ? inputRef : null, StationService::liveResourceTypeIdsOf);
                if (have < inCount) {
                    continue;
                }
                if (!backpack.canAddItemStacks(List.of(new ItemStack(outItem, outCount)))) {
                    sawInputWithoutRoom = true;
                    continue;
                }
                return new ConversionCheck(ConversionState.RUNNABLE, isResource, inputRef, inCount,
                        outItem, outCount);
            }
        } catch (Throwable t) {
            Log.warn("STATION custody check failed: " + t.getMessage());
        }
        return new ConversionCheck(
                sawInputWithoutRoom ? ConversionState.NO_ROOM : ConversionState.NO_INPUTS,
                false, null, 0, null, 0);
    }

    // ==================== Placed-input custody (design section 9.4) ====================

    /** Package-private accessor for {@code StationStepHandlers}' {@code Consume From:"Custody"} route. */
    @Nullable
    StationCustodyClaim custodyClaimFor(@Nullable String blockKey) {
        return blockKey != null ? custodyByBlock.get(blockKey) : null;
    }

    /**
     * True when {@code held} satisfies the station's custody placement matcher: an explicit
     * {@link Custody#getInput()} when authored, else ANY resolved {@code Recipe.Conversions}
     * input (the sawmill's "logs by ResourceTypeId family" - zero extra authoring).
     */
    private static boolean custodyAccepts(@Nonnull Custody custody, @Nonnull StationAsset asset,
            @Nullable ItemStack held) {
        if (held == null || held.isEmpty()) {
            return false;
        }
        String heldItemId = held.getItemId();
        String[] heldResourceTypeIds = liveResourceTypeIdsOf(heldItemId);
        var matcher = custody.getInput();
        if (matcher != null) {
            return StationCustody.matchesInput(matcher, heldItemId, heldResourceTypeIds, liveRawTagsOf(heldItemId));
        }
        StationAsset.Conversion[] conversions = StationCatalog.getInstance().resolvedConversions(asset);
        return conversions != null && conversions.length > 0
                && StationCustody.matchesAnyConversionInput(conversions, heldItemId, heldResourceTypeIds);
    }

    /**
     * Moves up to {@code custody.effectiveMaxQuantity() - currentTotal} of {@code held}'s ACTIVE
     * HOTBAR SLOT into the block's claim (creating it, owned by {@code playerUuid}, on first
     * placement), removing exactly that amount from the slot. Returns the amount actually moved
     * (0 = nothing eligible / no room / the slot removal failed).
     */
    private int placeIntoCustody(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
            @Nonnull String blockKey, @Nonnull UUID playerUuid, @Nonnull String stationId,
            @Nonnull String actionId, @Nonnull ItemStack held, @Nonnull Custody custody) {
        String itemId = held.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        StationCustodyClaim claim = custodyByBlock.get(blockKey);
        int currentTotal = claim != null ? claim.totalQuantity() : 0;
        int moveCount = StationCustody.placeableQuantity(currentTotal, held.getQuantity(),
                custody.effectiveMaxQuantity());
        if (moveCount <= 0) {
            return 0;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null || hotbar.getActiveSlot() == InventoryComponent.INACTIVE_SLOT_INDEX) {
            return 0;
        }
        try {
            hotbar.getInventory().removeItemStackFromSlot(hotbar.getActiveSlot(), moveCount);
        } catch (Throwable t) {
            Log.warn("STATION custody placement removal failed: " + t.getMessage());
            return 0;
        }
        if (claim == null) {
            claim = new StationCustodyClaim(playerUuid, stationId, actionId);
            custodyByBlock.put(blockKey, claim);
        }
        claim.add(itemId, moveCount);
        return moveCount;
    }

    /**
     * Every custody auto-return path (design section 9.4: "unconsumed input auto-returns on
     * EVERY exit path") funnels here from {@link #stop}: removes the block's claim (if any owned
     * by THIS session's player), returns its items to the owner's inventory when reachable and
     * there is room ({@link StationCustody#shouldReturnToInventory}), else drops them at the
     * block once, then flips the block back to its Empty custody state.
     *
     * <p>{@code s.ref.getStore()} (not the {@code store} parameter {@link #stop} may have been
     * handed as {@code null}, e.g. on {@code stopAll}'s shutdown sweep) is the store source here -
     * a valid ref always knows its own owning store, so this covers the shutdown case too as long
     * as the ref has not actually been removed yet.
     */
    private void returnCustody(@Nonnull StationSession s, @Nullable Custody custody) {
        if (s.blockKey == null) {
            return;
        }
        StationCustodyClaim claim = custodyByBlock.remove(s.blockKey);
        if (claim == null) {
            return;
        }
        if (!claim.ownerId.equals(s.playerUuid)) {
            // Not this session's claim to touch (should not happen - custody ownership gates
            // session start - but never silently swallow another player's placed items).
            custodyByBlock.putIfAbsent(s.blockKey, claim);
            return;
        }
        Store<EntityStore> ownerStore = null;
        if (s.ref != null && s.ref.isValid()) {
            try {
                ownerStore = s.ref.getStore();
            } catch (Throwable ignored) {
                ownerStore = null;
            }
        }
        if (!claim.isEmpty()) {
            List<ItemStack> stacks = claim.toItemStacks();
            Player player = ownerStore != null ? ownerStore.getComponent(s.ref, Player.getComponentType()) : null;
            boolean hasRoom = player != null;
            if (hasRoom) {
                try {
                    hasRoom = player.getInventory().getStorage().canAddItemStacks(stacks);
                } catch (Throwable t) {
                    hasRoom = false;
                }
            }
            if (StationCustody.shouldReturnToInventory(player != null, hasRoom)) {
                try {
                    for (ItemStack stack : stacks) {
                        player.getInventory().getStorage().addItemStack(stack);
                    }
                } catch (Throwable t) {
                    Log.warn("STATION custody return to inventory failed: " + t.getMessage());
                    dropCustodyAtBlock(ownerStore, s.blockX, s.blockY, s.blockZ, stacks);
                }
            } else {
                dropCustodyAtBlock(ownerStore, s.blockX, s.blockY, s.blockZ, stacks);
            }
        }
        if (custody != null) {
            World world = null;
            if (s.ref != null && s.ref.isValid()) {
                try {
                    world = WorldEvictors.worldOf(s.ref);
                } catch (Throwable ignored) {
                    world = null;
                }
            }
            if (world != null) {
                flipCustodyState(world, s.blockX, s.blockY, s.blockZ, custody, false);
            }
        }
    }

    /**
     * The "block broken" custody auto-return path ({@link StationCustodyBreakSystem}, no session
     * required - a player can place input then walk away before ever pressing F again). Drops
     * everything at the block once; no-ops when nothing is claimed there (including the common
     * case where a session's own {@link #stop} already handled it via its heartbeat's block-gone
     * check on the same or a following tick - no double drop, {@link ConcurrentHashMap#remove}
     * is the idempotency gate).
     */
    void onCustodyBlockBroken(@Nonnull Store<EntityStore> store, @Nonnull String blockKey, int x, int y, int z) {
        StationCustodyClaim claim = custodyByBlock.remove(blockKey);
        if (claim == null || claim.isEmpty()) {
            return;
        }
        dropCustodyAtBlock(store, x, y, z, claim.toItemStacks());
    }

    /** Drops {@code stacks} at the block's center via the native dropped-item spawn. */
    private static void dropCustodyAtBlock(@Nullable Store<EntityStore> store, int x, int y, int z,
            @Nonnull List<ItemStack> stacks) {
        if (store == null || stacks.isEmpty()) {
            if (!stacks.isEmpty()) {
                Log.warn("STATION custody items lost - no store available to drop at (" + x + "," + y + "," + z + ")");
            }
            return;
        }
        try {
            Vector3d pos = new Vector3d(x + 0.5, y + 1.0, z + 0.5);
            var holders = ItemComponent.generateItemDrops(store, stacks, pos, Rotation3f.IDENTITY);
            for (var holder : holders) {
                store.addEntity(holder, AddReason.SPAWN);
            }
        } catch (Throwable t) {
            Log.warn("STATION custody drop-at-block failed: " + t.getMessage());
        }
    }

    /**
     * Flips the block at {@code (x,y,z)} to {@code custody}'s Empty/Loaded state (design 9.4's
     * hint-only state pair; a nullable {@link Custody#getStates()} means "no visual/hint flip,
     * custody still works mechanically" - a no-op here). Guarded exactly like the kweebec shrine
     * precedent: a block that is gone, or that never authored the named state, no-ops (retried
     * naturally on the next interaction).
     */
    private static void flipCustodyState(@Nonnull World world, int x, int y, int z, @Nonnull Custody custody,
            boolean toLoaded) {
        Custody.States states = custody.getStates();
        if (states == null) {
            return;
        }
        String stateName = toLoaded ? states.getLoaded() : states.getEmpty();
        if (stateName == null || stateName.isBlank()) {
            return;
        }
        try {
            BlockType bt = world.getBlockType(x, y, z);
            if (bt == null || bt.getData() == null || bt.getBlockForState(stateName) == null) {
                return;
            }
            world.setBlockInteractionState(new Vector3i(x, y, z), bt, stateName);
        } catch (Throwable t) {
            Log.fine("STATION custody state flip failed: " + t.getMessage());
        }
    }

    /** Live {@code ItemResourceType} family ids for {@code itemId} ({@code []} when unresolvable). */
    @Nonnull
    static String[] liveResourceTypeIdsOf(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return new String[0];
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return new String[0];
        }
        ItemResourceType[] types = item.getResourceTypes();
        if (types == null || types.length == 0) {
            return new String[0];
        }
        String[] out = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = types[i] != null ? types[i].id : null;
        }
        return out;
    }

    /** Live raw tags for {@code itemId} (the SAME route {@link #tagsMatch} reads), empty when unresolvable. */
    @Nonnull
    private static Map<String, String[]> liveRawTagsOf(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Map.of();
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return Map.of();
        }
        AssetExtraInfo.Data data = item.getData();
        if (data == null) {
            return Map.of();
        }
        Map<String, String[]> raw = data.getRawTags();
        return raw != null ? raw : Map.of();
    }

    // ==================== Requires gate (design section 4.4.2) ====================

    /**
     * Checks {@code reqs} against {@code playerRef}: a blank/absent {@link Requires#getPermission()}
     * always passes; a null/empty {@link Requires#getConditions()} always passes. Every
     * condition must pass (see {@link #conditionPasses}), resolved against a fresh pre-session
     * {@link FactorContext} (design section 3.2's api {@link FactorRegistryImpl}, leg 4 - a
     * degenerate context since no session exists yet: {@code sessionSeconds}/{@code cycleIndex}
     * 0, held-tool power/durability not read here since the station's own {@code Requires}
     * shipped station never authors a tool-power condition; a skill-level-style condition needs
     * only {@code playerId}). A null {@code reqs} always passes.
     */
    private static boolean checkRequires(@Nullable Requires reqs, @Nonnull PlayerRef playerRef,
            @Nonnull StationAsset asset) {
        if (reqs == null || reqs.isEmpty()) {
            return true;
        }
        String permission = reqs.getPermission();
        if (permission != null && !permission.isBlank() && !playerRef.hasPermission(permission)) {
            return false;
        }
        Condition[] conditions = reqs.getConditions();
        if (conditions == null || conditions.length == 0) {
            return true;
        }
        UUID playerId = playerRef.getUuid();
        if (playerId == null) {
            return false;
        }
        FactorContext ctx = FactorContext.builder()
                .playerRef(playerRef)
                .playerId(playerId)
                .stationId(asset.getId())
                .actionId(ACTION_WORK)
                .sessionSeconds(0L)
                .cycleIndex(0)
                .progressionSkills(progressionSkills(asset))
                .build();
        FactorLookup lookup = (factorId, param) -> FactorRegistryImpl.getInstance().resolve(factorId, param, ctx);
        for (Condition c : conditions) {
            if (c == null) {
                continue;
            }
            if (!conditionPasses(c, lookup)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pure(r) condition check over an injected {@link FactorLookup} (unit-testable without a
     * live server): a null/blank {@code Factor} passes vacuously (an authoring mistake, not a
     * gate); an unresolvable factor id (the lookup returns null) FAILS CLOSED; otherwise the
     * resolved value must clear both {@code Min} (if authored) and {@code Max} (if authored).
     */
    static boolean conditionPasses(@Nonnull Condition c, @Nonnull FactorLookup lookup) {
        String factorId = c.getFactor();
        if (factorId == null || factorId.isBlank()) {
            return true;
        }
        Double value = lookup.resolve(factorId, c.getParam());
        if (value == null) {
            Log.warn("STATION Requires condition references unknown factor '" + factorId + "' - denying (fail closed)");
            return false;
        }
        if (c.getMin() != null && value < c.getMin()) {
            return false;
        }
        return c.getMax() == null || value <= c.getMax();
    }

    // ==================== Helpers ====================

    /** The engine block id at (x,y,z), or Integer.MIN_VALUE when unreadable (chunk unloaded). */
    private static int blockIdAt(@Nonnull World world, int x, int y, int z) {
        try {
            return world.getBlock(x, y, z);
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * The BlockType id string of the block at (x,y,z) - the fallback summary-crest icon when a
     * station authors no {@code Identity.Icon}. Captured at ENGAGE only.
     */
    @Nullable
    private static String blockItemIdAt(@Nonnull World world, int x, int y, int z) {
        try {
            var blockType = world.getBlockType(x, y, z);
            String id = blockType != null ? blockType.getId() : null;
            return id != null && !id.isBlank() && !"Empty".equals(id) ? id : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Localized station display name: explicit NameKey (already a full id), else the rpgstations.station.<id>.name convention. */
    @Nonnull
    private static Message stationNameMsg(@Nonnull StationAsset asset) {
        String key = asset.getIdentity() != null && asset.getIdentity().getNameKey() != null
                ? asset.getIdentity().getNameKey()
                : "rpgstations.station." + asset.getId() + ".name";
        return Msg.key(key);
    }

    /** Native item/block name as a client-resolved {@link Message} ({@code items.<id>.name} convention). */
    @Nonnull
    private static Message itemNameMsg(@Nonnull String itemId) {
        return Msg.key("items." + itemId + ".name");
    }

    private static void toast(@Nonnull PlayerRef playerRef, @Nonnull Message message) {
        try {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), message.color(Color.YELLOW));
        } catch (Throwable t) {
            Log.fine("STATION toast failed: " + t.getMessage());
        }
    }

    /** The stop-toast key for a reason, or null for reasons that toast nothing. */
    @Nullable
    private static String stopReasonKey(@Nonnull StopReason reason) {
        return switch (reason) {
            case PLAYER_EXIT -> "ui.station.stop.player";
            case MOVED -> "ui.station.stop.moved";
            case DAMAGED -> "ui.station.stop.damaged";
            case OUT_OF_INPUTS -> "ui.station.stop.out_of_inputs";
            case INVENTORY_FULL -> "ui.station.stop.inventory_full";
            case SESSION_CAP -> "ui.station.stop.session_cap";
            case STATION_GONE -> "ui.station.stop.station_gone";
            case TOOL_CHANGED -> "ui.station.stop.tool_changed";
            case TOOL_BROKEN -> "ui.station.stop.tool_broke";
            case FEATURE_DISABLED -> "ui.station.locked";
            case STEP_FAILED -> "ui.station.stop.step_failed";
            default -> null;
        };
    }

    /** Engine feature toggle, backed by {@code SettingsAsset.Enabled} (design section 4.6). */
    private static boolean stationsEnabled() {
        return SettingsCatalog.getInstance().current().isEnabled();
    }

    /**
     * The held-tool heartbeat gate's PURE decision: given whether the held item currently
     * matches the station's {@link StationAsset.Tool} gate and whether it reports broken,
     * decides which {@link StopReason} (if any) the heartbeat should raise.
     */
    @Nullable
    static StopReason toolGateStopReason(boolean matches, boolean broken) {
        if (!matches) {
            return StopReason.TOOL_CHANGED;
        }
        if (broken) {
            return StopReason.TOOL_BROKEN;
        }
        return null;
    }

    /**
     * The mount-mode heartbeat's PURE decision - generic over EITHER {@code Hold.Mount} surface
     * (design 9.2): both the Block route (native {@code BlockMountAPI}) and the Entity route (a
     * spawned anchor) attach the SAME {@code MountedComponent} type to the player, so one native
     * {@code isMounted} read serves both; the caller passes {@code s.seatMode || s.entityMountMode}
     * as {@code seatMode}. Kept under its original name (tested, byte-stable) rather than renamed
     * out from under its existing coverage.
     */
    static boolean seatModeShouldStop(boolean seatMode, boolean mounted) {
        return seatMode && !mounted;
    }

    /**
     * Opt-in held-tool durability drain: reduces the ACTIVE HOTBAR item's durability by
     * {@code amount}, mirroring the native gathering-tool wear call shape.
     */
    private static void drainHeldToolDurability(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                                                @Nonnull Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        try {
            if (!ItemUtils.canDecreaseItemStackDurability(ref, store)) {
                return;
            }
            ItemStack held = player.getInventory().getActiveHotbarItem();
            if (held == null || held.isEmpty() || held.isUnbreakable() || held.isBroken()) {
                return;
            }
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            if (hotbar == null || hotbar.getActiveSlot() == InventoryComponent.INACTIVE_SLOT_INDEX) {
                return;
            }
            ItemUtils.updateItemStackDurability(ref, held, hotbar.getInventory(), hotbar.getActiveSlot(), -amount, store);
        } catch (Throwable t) {
            Log.fine("STATION durability drain failed: " + t.getMessage());
        }
    }

    /**
     * True when the player's HELD item satisfies the station's {@link StationAsset.Tool} gate.
     * A null group (or no non-blank route) means no requirement. Match = ANY of the three
     * NATIVE routes.
     */
    static boolean heldToolMatches(@Nonnull Player player, @Nullable StationAsset.Tool tool) {
        if (tool == null) {
            return true;
        }
        Map<String, String[]> tags = tool.getTags();
        String[] ids = tool.getIds();
        StationAsset.Tool.Gather gather = tool.getGather();
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasGather = gather != null && gather.getGatherType() != null && !gather.getGatherType().isBlank();
        boolean hasIds = hasNonBlank(ids);
        if (!hasTags && !hasGather && !hasIds) {
            return true;
        }
        ItemStack held = player.getInventory().getActiveHotbarItem();
        Item item = held != null ? held.getItem() : null;
        String heldId = item != null ? item.getId() : null;
        if (item == null || heldId == null) {
            return false;
        }
        if (hasIds && idsMatch(heldId, ids)) {
            return true;
        }
        if (hasTags && tagsMatch(item, tags)) {
            return true;
        }
        return hasGather && gatherMatches(item, gather);
    }

    /** Fallback id route: an exact id OR a case-insensitive underscore-separated id SEGMENT. */
    private static boolean idsMatch(@Nonnull String heldId, @Nonnull String[] ids) {
        String[] segments = null;
        for (String candidate : ids) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.equalsIgnoreCase(heldId)) {
                return true;
            }
            if (segments == null) {
                segments = heldId.split("_");
            }
            for (String segment : segments) {
                if (segment.equalsIgnoreCase(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Native-tag route: a non-empty case-insensitive intersection with the held item's raw tags. */
    private static boolean tagsMatch(@Nonnull Item item, @Nonnull Map<String, String[]> required) {
        AssetExtraInfo.Data data = item.getData();
        if (data == null) {
            return false;
        }
        Map<String, String[]> raw = data.getRawTags();
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String[]> req : required.entrySet()) {
            String key = req.getKey();
            String[] wanted = req.getValue();
            if (key == null || wanted == null || wanted.length == 0) {
                continue;
            }
            String[] have = rawTagValues(raw, key);
            if (have == null) {
                continue;
            }
            for (String want : wanted) {
                if (want == null) {
                    continue;
                }
                for (String h : have) {
                    if (want.equalsIgnoreCase(h)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Case-insensitive key lookup into a raw-tags map (direct hit first, then a scan). */
    @Nullable
    private static String[] rawTagValues(@Nonnull Map<String, String[]> raw, @Nonnull String key) {
        String[] direct = raw.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String[]> e : raw.entrySet()) {
            if (key.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Functional gather route: a matching {@code GatherType} whose {@code Power} clears {@code MinPower}. */
    private static boolean gatherMatches(@Nonnull Item item, @Nonnull StationAsset.Tool.Gather gather) {
        ItemTool itemTool = item.getTool();
        if (itemTool == null) {
            return false;
        }
        ItemToolSpec[] specs = itemTool.getSpecs();
        if (specs == null) {
            return false;
        }
        String wantType = gather.getGatherType();
        double minPower = gather.getMinPower() != null ? gather.getMinPower() : 0.0;
        for (ItemToolSpec spec : specs) {
            if (spec == null || spec.getGatherType() == null) {
                continue;
            }
            if (spec.getGatherType().equalsIgnoreCase(wantType) && spec.getPower() >= minPower) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code values} holds at least one non-blank entry. */
    private static boolean hasNonBlank(@Nullable String[] values) {
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
