package com.ziggfreed.rpgstations.station;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.ziggfreed.common.camera.CameraShakeService;
import com.ziggfreed.common.cast.ModelParticleService;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.common.cast.WorldKeyedQueues;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
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
 * <p><b>CRITICAL SCOPE NOTE (leg 2, per the extraction brief's critical-scope-rule):</b> this
 * port keeps the ENGINE mechanics fully functional (session machine, toggle/stop funnel,
 * heartbeat, the Convert cycle transaction, swing/impact scheduling, idle mode, the
 * {@link #emitMoment} presentation choke point, durability drain, seat mount calls) but
 * SEVERS every MMO-specific progression call the original made ({@code SkillService}-driven
 * XP awards, {@code SkillTreeService}/{@code MasteryService}-driven luck aggregation,
 * {@code ProgressEvents.fire}, {@code FeedbackService.emit}, the MMO's session-summary HUD) -
 * those become event firing (leg 4's api artifact) and the loot engine (leg 3), left here as
 * clearly-marked no-op seams: {@link #onCycleCompleted}, {@link #rollLootAndBonuses},
 * {@link #showSessionSummary}. The MMO is NOT touched by this leg; its own copy of this class
 * (registered interaction id {@code mmo_station_use}) coexists unchanged until leg 5.
 */
public final class StationService {

    /** Heartbeat cadence (terminate checks + hold refresh); the hold TTL is 2.5x this. */
    private static final long HEARTBEAT_MS = 1000L;

    static final long DEFAULT_CYCLE_MS = 5000L;
    private static final long DEFAULT_MAX_DURATION_MS = 600_000L;
    private static final double DEFAULT_MAX_MOVE_METERS = 1.5;
    private static final String DEFAULT_HOLD_EFFECT = "RPG_Station_Hold";

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
        TOOL_BROKEN
    }

    private static final StationService INSTANCE = new StationService();

    private final WorldKeyedQueues<StationSession> sessionsByWorld = new WorldKeyedQueues<>("rpgstations-work");
    private final ConcurrentHashMap<UUID, StationSession> byPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byBlock = new ConcurrentHashMap<>();

    /**
     * The Requires-condition factor lookup (design section 3.2's api {@code FactorRegistry},
     * pre-wired seam): TODO(leg 4) - the real registry replaces this default. Nothing
     * registered = every factor unknown = every condition FAILS CLOSED (a gate never silently
     * opens because a provider has not registered yet).
     */
    @FunctionalInterface
    interface FactorLookup {
        @Nullable
        Double resolve(@Nonnull String factorId, @Nullable String param);
    }

    private static volatile FactorLookup factorLookup = (factorId, param) -> null;

    private StationService() {
    }

    @Nonnull
    public static StationService getInstance() {
        return INSTANCE;
    }

    /** Install the live Requires-condition factor lookup (wired by a later leg's api registry). */
    public static void setFactorLookup(@Nonnull FactorLookup lookup) {
        factorLookup = lookup;
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
        if (!checkRequires(asset.getRequires(), playerRef)) {
            toast(playerRef, RpgMsg.tr("ui.station.locked"));
            return;
        }

        // 3) Exclusive occupancy.
        StationAsset.Work work = asset.getWork();
        boolean exclusive = work == null || work.getExclusive() == null || work.getExclusive();
        UUID worldUuid = playerRef.getWorldUuid();
        String blockKey = worldUuid + ":" + blockX + ":" + blockY + ":" + blockZ;
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

        // 5) Convert viability, or idle practice when the station opts in.
        StationAsset.Work.Idle idleGroup = work != null ? work.getIdle() : null;
        boolean idleEnabled = idleGroup != null && idleGroup.getEnabled() != null && idleGroup.getEnabled();
        ConversionCheck check = firstRunnableConversion(player, asset);
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
        World world;
        try {
            world = WorldEvictors.worldOf(ref);
        } catch (Throwable t) {
            Log.warn("STATION could not resolve world for session start: " + t.getMessage());
            return;
        }

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
        StationAsset.Hold.Seat seatGroup = hold != null ? hold.getSeat() : null;
        s.seatMode = seatGroup != null && seatGroup.getEnabled() != null && seatGroup.getEnabled();
        s.movementLock = !s.seatMode
                && (hold == null || hold.getMovementLock() == null || hold.getMovementLock());
        s.holdEffectId = hold != null && hold.getEffectId() != null && !hold.getEffectId().isBlank()
                ? hold.getEffectId() : DEFAULT_HOLD_EFFECT;
        s.interruptOnDamage = hold == null || hold.getInterruptOnDamage() == null || hold.getInterruptOnDamage();

        if (s.seatMode && !StationMountController.mount(ref, commandBuffer, blockX, blockY, blockZ, pos)) {
            toast(playerRef, RpgMsg.tr("ui.station.seat_unavailable"));
            return;
        }

        StationAsset.Camera camera = asset.getCamera();
        String cameraMode = camera != null && camera.getMode() != null ? camera.getMode() : "ThirdPerson";
        boolean seatDefaultNoCamera = s.seatMode && camera == null;
        s.cameraApplied = !seatDefaultNoCamera && !"None".equalsIgnoreCase(cameraMode);
        s.cameraLocked = camera == null || camera.getLocked() == null || camera.getLocked();
        s.faceBlock = s.cameraApplied && camera != null && camera.getFaceBlock() != null && camera.getFaceBlock();
        s.faceBlockMode = camera != null ? camera.getFaceBlockMode() : null;

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
                if (now >= s.nextCycleAtMs) {
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
        if (seatModeShouldStop(s.seatMode, StationMountController.isMounted(s.ref, store))) {
            stop(s, StopReason.MOVED, store);
            return false;
        }
        if (!s.seatMode) {
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

        ConversionCheck check = firstRunnableConversion(player, asset);
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
            return runIdleCycle(s, store, asset);
        } else if (check.state == ConversionState.NO_INPUTS) {
            stop(s, StopReason.OUT_OF_INPUTS, store);
            return false;
        } else { // NO_ROOM
            stop(s, StopReason.INVENTORY_FULL, store);
            return false;
        }
    }

    /**
     * The real Convert cycle: transaction (pre-checked output room BEFORE consuming input,
     * zero loss), tool-power-scaled multiplier resolution, the cycle presentation moment, and
     * the leg-3/leg-4 seams ({@link #rollLootAndBonuses}, {@link #onCycleCompleted}).
     * Precondition: {@code check.state == RUNNABLE}.
     */
    private boolean runRealCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
                                 @Nonnull Player player, @Nonnull ConversionCheck check) {
        try {
            if (check.inputIsResource) {
                ResourceQuantity resource = new ResourceQuantity(check.inputRef, check.inputCount);
                ResourceTransaction tx = player.getInventory().getStorage().canRemoveResource(resource)
                        ? player.getInventory().getStorage().removeResource(resource)
                        : player.getInventory().getCombinedBackpackStorageHotbar().removeResource(resource);
                tallyResourceConsumption(s, tx, check.inputRef);
            } else {
                ItemStack input = new ItemStack(check.inputRef, check.inputCount);
                if (player.getInventory().getStorage().canRemoveItemStack(input)) {
                    player.getInventory().getStorage().removeItemStack(input);
                } else {
                    player.getInventory().getCombinedBackpackStorageHotbar().removeItemStack(input);
                }
                s.consumedItems.merge(check.inputRef, check.inputCount, Integer::sum);
            }
            player.getInventory().getStorage()
                    .addItemStack(new ItemStack(check.outputItem, check.outputCount));
            s.producedItems.merge(check.outputItem, check.outputCount, Integer::sum);
        } catch (Throwable t) {
            Log.warn("STATION conversion failed for '" + s.stationId + "': " + t.getMessage());
            stop(s, StopReason.INVENTORY_FULL, store);
            return false;
        }
        s.cyclesDone++;

        if (s.durabilityPerCycle > 0) {
            drainHeldToolDurability(store, s.ref, player, s.durabilityPerCycle);
        }

        double xpMult = resolveXpMultiplier(player, asset);

        // Leg 3 seam: the conditional-lootable loot engine replaces this with real Roll
        // evaluation over Loot.Tables/Loot.Rolls (see this class's javadoc + StationAsset's).
        rollLootAndBonuses(s, store, asset, player, check);

        // Leg 4 seam: fires StationCycleCompletedEvent once the api artifact lands (XpAsks
        // forwarded from asset.getWork().getXp(), toolMultiplier = xpMult).
        onCycleCompleted(s, asset, xpMult, false, s.cyclesDone);

        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.Slot.CYCLE, asset.getPresentation(), blockPos);
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
    private static void tallyResourceConsumption(@Nonnull StationSession s, @Nullable ResourceTransaction tx,
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
     * cycle presentation + the leg-4 XP-ask seam at the idle fraction/multiplier.
     */
    private boolean runIdleCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull StationAsset asset) {
        onCycleCompleted(s, asset, 1.0, true, s.cyclesDone);

        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.Slot.CYCLE, asset.getPresentation(), blockPos);
        s.cyclesDone++;
        return true;
    }

    /**
     * TODO(leg 4): forwards this cycle's {@code Work.Xp} asks + the resolved tool multiplier
     * as a {@code StationCycleCompletedEvent} once the api artifact + event firing land (design
     * section 3.1/7.2). No-op for now - the engine computes {@code toolMultiplier} (pure,
     * MMO-free) but has nowhere to send it yet; a listening progression mod (the MMO bridge)
     * reads {@code asset.getWork().getXp()} off the event to know what an ask means.
     */
    private static void onCycleCompleted(@Nonnull StationSession s, @Nonnull StationAsset asset,
            double toolMultiplier, boolean idle, int cycleIndex) {
        // intentionally empty - see the javadoc above
    }

    /**
     * TODO(leg 3): the conditional-lootable loot engine (design section 4.5) replaces this
     * seam - Roll evaluation over {@code Loot.Tables}/{@code Loot.Rolls}, keyed off a per-cycle
     * {@code FactorSnapshot}. The MMO-specific per-skill luck aggregation the original engine
     * inlined here ({@code StationLuck} + {@code SkillTreeService} + {@code MasteryService})
     * becomes the MMO bridge's OWN {@code mmoskilltree:station_luck} factor provider, consumed
     * generically by the loot engine - no MMO call belongs in this file again.
     * {@code StationAsset.Luck} is still decoded (unchanged schema this leg) but unread until
     * leg 3 lands the {@code Loot} group that supersedes it.
     */
    private void rollLootAndBonuses(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull StationAsset asset, @Nonnull Player player, @Nonnull ConversionCheck check) {
        // intentionally empty - see the javadoc above
    }

    /**
     * TODO(leg 3): {@code ui.StationSummaryHud} (design section 10, leg 3) replaces this seam
     * with the standalone-rich item-ledger summary panel; the MMO's per-skill XP rows become
     * its OWN {@code StationSummaryEnricher} (leg 5), reached through the api
     * {@code SummaryEnricherRegistry}. No-op for now - the stop-reason toast still fires.
     */
    private void showSessionSummary(@Nonnull StationSession s) {
        // intentionally empty - see the javadoc above
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
    private static void emitMoment(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
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
        if (s.seatMode && entityAlive) {
            StationMountController.dismount(s.ref, store);
        }
        if (!silent && s.playerRef != null) {
            try {
                String reasonKey = stopReasonKey(reason);
                if (reasonKey != null) {
                    toast(s.playerRef, RpgMsg.tr(reasonKey));
                }
                if (s.cyclesDone > 0) {
                    showSessionSummary(s);
                }
                if (entityAlive && shouldPlayCompletion(silent, s.cyclesDone)) {
                    playCompletionMoment(s, store);
                }
            } catch (Throwable t) {
                Log.fine("STATION stop notification failed: " + t.getMessage());
            }
        }
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

    // ==================== Requires gate (design section 4.4.2) ====================

    /**
     * Checks {@code reqs} against {@code playerRef}: a blank/absent {@link Requires#getPermission()}
     * always passes; a null/empty {@link Requires#getConditions()} always passes. Every
     * condition must pass (see {@link #conditionPasses}). A null {@code reqs} always passes.
     */
    private static boolean checkRequires(@Nullable Requires reqs, @Nonnull PlayerRef playerRef) {
        if (reqs == null || reqs.isEmpty()) {
            return true;
        }
        String permission = reqs.getPermission();
        if (permission != null && !permission.isBlank() && !playerRef.hasPermission(permission)) {
            return false;
        }
        Condition[] conditions = reqs.getConditions();
        if (conditions != null) {
            for (Condition c : conditions) {
                if (c == null) {
                    continue;
                }
                if (!conditionPasses(c, factorLookup)) {
                    return false;
                }
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
        return com.ziggfreed.common.i18n.Msg.key(key);
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
            default -> null;
        };
    }

    /**
     * Engine feature toggle. TODO(leg 3): wire {@code SettingsAsset.Enabled} once the settings
     * asset lands (design section 4.6); always-enabled seam for now.
     */
    private static boolean stationsEnabled() {
        return true;
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

    /** The seat-mode heartbeat's PURE decision. */
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
