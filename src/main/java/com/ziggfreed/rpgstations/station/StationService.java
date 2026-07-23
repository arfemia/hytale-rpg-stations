package com.ziggfreed.rpgstations.station;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.ziggfreed.common.camera.CameraShakeService;
import com.ziggfreed.common.cast.ModelParticleService;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.common.cast.WorldKeyedQueues;
import com.ziggfreed.common.cast.step.CastKernel;
import com.ziggfreed.common.feedback.Notify;
import com.ziggfreed.common.feedback.PickupMimic;
import com.ziggfreed.common.i18n.Msg;
import com.ziggfreed.common.i18n.NativeNames;
import com.ziggfreed.common.inventory.InventoryGrant;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.ui.rows.SummaryRow;
import com.ziggfreed.common.util.NumberFormatter;
import com.ziggfreed.rpgstations.api.EnhanceLine;
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
import com.ziggfreed.rpgstations.util.InventoryAccess;
import com.ziggfreed.rpgstations.util.ItemDropUtil;
import com.ziggfreed.rpgstations.util.ItemGrantUtil;
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

    /** Round-5 refinement 3: the "lucky grant" notification color (distinct from {@link #toast}'s plain YELLOW). */
    private static final Color GOLD = new Color(0xFFD700);

    /**
     * D-6: the enhance summary accent (design section 9.5, phase 2 round-7). The engine's OWN
     * durability row bakes this into its Message at composition ({@link #enhanceLedgerRows}); a
     * per-stat enhance row keeps the provider's own pre-styled color instead (the summary HUD's
     * {@code ENHANCE} case never recolors).
     */
    private static final Color ENHANCE_ROW_COLOR = Color.decode("#c9a2ff");

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
        STEP_FAILED,
        /**
         * {@code Work.Repeat: false}'s "one completed program run completes the SESSION" (design
         * 9.3, phase 2 leg E - the anvil's Enhance ritual): a non-repeating action's program
         * completed successfully; the session stops right here, non-silent (a real completion,
         * not a denial).
         */
        RITUAL_COMPLETE,
        /**
         * A Stamp step's {@code Stats} leaf clamped its roll to nothing (design 9.5: "a
         * fully-capped item stamps nothing, consumes nothing, and denies with a keyed toast") -
         * every authored cap ({@code PerItemBudget}/{@code PerStat}/{@code SkillScaledBudget}) is
         * already saturated for this item. No reagents were consumed.
         */
        ENHANCE_CAPPED
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
     * [D77DIAG] temporary, one-sweep-removable (same pattern as the retired [SMOKEDIAG] lines) -
     * last-logged timestamp per player for the resume-check throttle in {@link #tickFrameOnce},
     * so the per-heartbeat-frame suspended-branch diag caps at ~250ms per session instead of
     * spamming every frame.
     */
    private static final ConcurrentHashMap<UUID, Long> d77diagLastResumeLogMs = new ConcurrentHashMap<>();

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
            stop(existing, StopReason.PLAYER_EXIT, store, commandBuffer);
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

        UUID worldUuid = playerRef.getWorldUuid();
        String blockKey = worldUuid + ":" + blockX + ":" + blockY + ":" + blockZ;
        World world;
        try {
            world = WorldEvictors.worldOf(ref);
        } catch (Throwable t) {
            Log.warn("STATION could not resolve world for session start: " + t.getMessage());
            return;
        }

        // 2) Diegetic action selection (design section 9.1, phase 2 leg E) - BEFORE Requires, so a
        // per-action Requires override (design 9.1) gates the RIGHT action. A loaded claim already
        // owned by this player commits to ITS OWN action (re-pressing F with a different item held
        // must never switch a ritual already in progress mid-flight); otherwise select by the
        // currently held stack. A single-action station (no Actions map) always resolves
        // ACTION_WORK, byte-identical to phase 1.
        StationCustodyClaim preClaim = custodyByBlock.get(blockKey);
        String selectedActionId = (preClaim != null && preClaim.ownerId.equals(playerUuid) && !preClaim.isEmpty())
                ? preClaim.actionId
                : selectActionForHeld(asset, player);
        if (selectedActionId == null) {
            // R5 fix (restart-orphan recovery, design 9.4's self-heal extended): neither the live
            // claim (memory-only, lost across a restart) nor the held item matched - before
            // denying, recover the action from the block's OWN persisted interaction-state name
            // (survives a restart) so a Loaded block orphaned by a crash/restart is not a
            // permanent dead end for a player holding the right tool but nothing matching held.
            selectedActionId = ActionResolver.selectActionForBlockState(asset,
                    currentBlockStateName(world, blockX, blockY, blockZ));
        }
        if (selectedActionId == null) {
            toast(playerRef, RpgMsg.tr("ui.station.no_action"));
            return;
        }
        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, selectedActionId);

        // 2.5) Requires gate (RpgStations' own Permission + Conditions, design section 4.4.2) -
        // the RESOLVED action's own override when authored, else the station-level default.
        if (!checkRequires(action.getRequires(), playerRef, asset)) {
            toast(playerRef, RpgMsg.tr("ui.station.locked"));
            return;
        }

        // 2.75) Placed-input custody (design section 9.4): a state-dependent F BEFORE the classic
        // engage flow - empty + a matching held stack places (or tops up); loaded + owner F falls
        // through to engage, sourcing the convert check from the claim instead of live inventory.
        Custody custody = action.getCustody();
        if (custody != null) {
            StationCustodyClaim claim = preClaim;
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
            boolean roomLeft = claim == null || claim.totalQuantity() < custody.effectiveMaxQuantity();
            if (roomLeft) {
                InventoryComponent.Hotbar hotbarComp =
                        store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
                ItemStack heldForPlacement = hotbarComp != null ? hotbarComp.getActiveItem() : null;
                int moved = 0;
                if (custodyAccepts(custody, asset, action, heldForPlacement)) {
                    moved = placeIntoCustody(store, ref, commandBuffer, blockKey, playerUuid, asset.getId(),
                            action.getActionId(), hotbarComp.getInventory(), hotbarComp.getActiveSlot(),
                            heldForPlacement, custody, blockX, blockY, blockZ);
                }
                if (moved <= 0 && hotbarComp != null) {
                    // R3 fix (directive 5's held-else-inventory ruling): the held slot didn't
                    // match (or nothing is held) - scan the rest of the inventory before denying,
                    // so matching material sitting unheld in the backpack is no longer invisible
                    // to placement.
                    InventoryMatch found = findFirstCustodyMatchInInventory(store, ref, custody, asset, action,
                            hotbarComp.getActiveSlot());
                    if (found != null) {
                        moved = placeIntoCustody(store, ref, commandBuffer, blockKey, playerUuid, asset.getId(),
                                action.getActionId(), found.container(), found.slot(), found.stack(), custody,
                                blockX, blockY, blockZ);
                    }
                }
                if (moved > 0) {
                    if (!loadedBefore) {
                        flipCustodyState(world, blockX, blockY, blockZ, custody, true);
                    }
                    toast(playerRef, RpgMsg.tr(loadedBefore
                            ? "ui.station.custody.topped_up" : "ui.station.custody.placed"));
                    return;
                }
            }
            // Nothing placed anywhere (not held, not elsewhere in the inventory, or already
            // full): fall through to engage below.
        }

        // 3) Exclusive occupancy - the RESOLVED action's own Work override when authored.
        StationAsset.Work work = action.getWork();
        boolean exclusive = work == null || work.getExclusive() == null || work.getExclusive();
        if (exclusive) {
            UUID occupant = byBlock.get(blockKey);
            if (occupant != null && !occupant.equals(playerUuid)) {
                toast(playerRef, RpgMsg.tr("ui.station.occupied"));
                return;
            }
        }

        // 4) Held-tool gate - the RESOLVED action's own Tool override when authored.
        if (!heldToolMatches(player, action.getTool())) {
            toast(playerRef, RpgMsg.tr("ui.station.wrong_tool"));
            return;
        }

        // 5) Viability: a Steps-authored action (design 9.3, phase 2 leg E - no Recipe/Convert
        // check applies) is runnable exactly when its OWN Custody governs and already holds
        // something (an ungoverned Steps action, no Custody authored, is always runnable - nothing
        // gates its engagement); a classic Recipe-driven action runs the Convert check
        // (custody-sourced when Custody governs), or idle practice when the station opts in.
        boolean stepsProgram = action.getSteps() != null && action.getSteps().length > 0;
        StationAsset.Work.Idle idleGroup = work != null ? work.getIdle() : null;
        boolean idleEnabled = !stepsProgram && idleGroup != null
                && idleGroup.getEnabled() != null && idleGroup.getEnabled();
        ConversionCheck check;
        if (stepsProgram) {
            boolean runnable = custody == null || (preClaim != null && !preClaim.isEmpty());
            check = new ConversionCheck(runnable ? ConversionState.RUNNABLE : ConversionState.NO_INPUTS,
                    false, null, 0, null, 0);
        } else {
            StationAsset.Conversion[] conversions =
                    StationCatalog.getInstance().resolvedConversions(asset, action.getActionId(), action.getRecipe());
            check = custody != null
                    ? firstRunnableConversionFromCustody(preClaim, player, conversions)
                    : firstRunnableConversion(player, conversions);
        }
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
        s.actionId = action.getActionId();
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

        StationAsset.Hold hold = action.getHold();
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
            boolean attached = anchorRef != null
                    && StationEntityMountController.attach(ref, anchorRef, commandBuffer, entityGroup);
            if (attached) {
                s.mountAnchorRef = anchorRef;
            } else {
                // Graceful degradation (fix round): the Entity-surface mount is a phase-2 spike
                // never verified in-game - a failure here must not brick the whole work loop.
                // Despawn whatever spawnAnchor already queued (it queues the entity BEFORE attach
                // can fail, so every failed press used to leak an orphan anchor) and fall back to
                // effect-mode (movement lock + hold effect - see the entityMountMode-now-false
                // read below), the same posture a station with no Mount group authored at all
                // gets, instead of denying the whole engage with a toast.
                if (anchorRef != null) {
                    StationEntityMountController.despawn(anchorRef, commandBuffer);
                }
                Log.warn("STATION entity mount unavailable for station '" + asset.getId() + "' action '"
                        + action.getActionId() + "' - falling back to effect-mode hold");
                s.entityMountMode = false;
            }
        }
        boolean effectivelyMounted = s.seatMode || s.entityMountMode;
        s.movementLock = (!effectivelyMounted && (hold == null || hold.getMovementLock() == null || hold.getMovementLock()))
                || (s.entityMountMode && !s.entitySteerable);

        // Animation fields (s.emoteId in particular) MUST be assigned before the puppet
        // spawn+hide call below - StationPuppetController#spawnAndHide reads s.emoteId to
        // pre-seed the puppet's initial ActiveAnimationComponent (the render-guaranteed catch-up
        // mechanism for a viewer not yet tracking the puppet at spawn time). Assigning it AFTER
        // that call (the fix-round defect) left every fresh session's puppet with no initial
        // animation component at all.
        StationAsset.Animation animation = action.getAnimation();
        s.emoteId = animation != null ? animation.getEmoteId() : null;
        s.actionClip = animation != null ? animation.getActionClip() : null;

        // Puppet presentation (round-4 design, doc section 4.2): spawn + hide AFTER the mount
        // attach above - the puppet layers on WHATEVER hold/mount the real player already has
        // (seat, standing mount, or effect-mode movement lock), never replacing it. Non-fatal on
        // failure: s.puppetActive stays false and the session continues in-body.
        StationPuppetController.spawnAndHide(s, commandBuffer, action.getPuppet(), player);

        StationAsset.Camera camera = action.getCamera();
        String cameraMode = camera != null && camera.getMode() != null ? camera.getMode() : "ThirdPerson";
        boolean mountDefaultNoCamera = mounted && camera == null;
        s.cameraApplied = !mountDefaultNoCamera && !"None".equalsIgnoreCase(cameraMode);
        s.cameraLocked = camera == null || camera.getLocked() == null || camera.getLocked();
        s.faceBlock = s.cameraApplied && camera != null && camera.getFaceBlock() != null && camera.getFaceBlock();
        s.cameraRecipe = camera != null ? camera.getRecipe() : null;
        s.toolReq = action.getTool();

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
        // Instant dispatch for a non-repeating authored Steps program (maintainer-approved,
        // round-7 D77): a ritual-shaped action (Work.Repeat: false, e.g. the anvil's Enhance)
        // has exactly ONE program run to make, so waiting a full CycleMs before ever attempting
        // it is pure latency with no gameplay purpose - fire the first (and only) cycle
        // immediately at engage. A REPEATING program (the sawmill's classic loop, or any
        // Repeat: true steps program) keeps the existing CycleMs pre-delay unchanged; idle mode
        // never applies to a Steps program (idleEnabled is forced false above), so this never
        // races s.idleMode's own cadence.
        boolean instantFirstDispatch = stepsProgram && work != null && !work.effectiveRepeat();
        s.nextCycleAtMs = instantFirstDispatch ? now : now + (s.idleMode ? s.idleCycleMs : s.cycleMs);
        s.nextSwingAtMs = now + s.swingIntervalMs;

        byPlayer.put(playerUuid, s);
        if (exclusive) {
            byBlock.put(blockKey, playerUuid);
        }
        sessionsByWorld.queueFor(world).offer(s);

        StationHoldController.applyHold(s, store);
        StationHoldController.applyCamera(s);
        // Puppet presentation (design 4.3): supersedes the seatMode-gated real-player emote
        // entirely - the puppet has no sit pose to fight, so it always plays its own loop clip
        // regardless of which mount/hold the (now possibly hidden) real player has.
        if (s.puppetActive) {
            StationPuppetController.playLoop(s, store);
        } else if (!s.seatMode) {
            StationHoldController.playEmote(s, store);
        }

        StationEvents.fireSessionStarted(store, s.playerRef, s.playerUuid, s.sessionId, s.stationId,
                s.actionId, s.blockX, s.blockY, s.blockZ, s.idleMode);

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
                    if (!heartbeat(s, world, store, commandBuffer)) {
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
                    // [D77DIAG] temporary, one-sweep-removable - logs the ACTUAL deadline value
                    // the gate below compares against, throttled to ~250ms per session so the
                    // per-heartbeat-frame cadence doesn't spam the log.
                    long d77DiagDeadline = s.stepDeadlineMs;
                    long d77DiagLastLoggedAt = d77diagLastResumeLogMs.getOrDefault(s.playerUuid, 0L);
                    if (now - d77DiagLastLoggedAt >= 250L) {
                        d77diagLastResumeLogMs.put(s.playerUuid, now);
                        Log.info("[D77DIAG] resume-check station=" + s.stationId + " action=" + s.actionId
                                + " now=" + now + " deadline=" + d77DiagDeadline);
                    }
                    boolean d77DiagDue = now >= d77DiagDeadline;
                    if (d77DiagDue) {
                        Log.info("[D77DIAG] resume-fire station=" + s.stationId + " action=" + s.actionId
                                + " now=" + now);
                    }
                    if (d77DiagDue && !resumeCycleProgram(s, store, commandBuffer)) {
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
                    runSwing(s, store, commandBuffer);
                }
                if (impactDue(now, s.pendingImpactAtMs)) {
                    s.pendingImpactAtMs = 0L;
                    runImpact(s, store);
                }
            } catch (Throwable t) {
                Log.warn("STATION tick failed: " + t.getMessage(), t);
                stop(s, StopReason.PLAYER_EXIT, store, commandBuffer);
                it.remove();
            }
        }
    }

    /** Terminate checks in order + hold TTL refresh. Returns false when the session ended. */
    private boolean heartbeat(@Nonnull StationSession s, @Nonnull World world,
                              @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (s.ref == null || !s.ref.isValid() || s.ref.getStore() != store) {
            stop(s, StopReason.WORLD_CHANGED, null, null);
            return false;
        }
        if (blockIdAt(world, s.blockX, s.blockY, s.blockZ) != s.startBlockId) {
            stop(s, StopReason.STATION_GONE, store, commandBuffer);
            return false;
        }
        boolean mounted = s.seatMode || s.entityMountMode;
        if (seatModeShouldStop(mounted, StationMountController.isMounted(s.ref, store))) {
            stop(s, StopReason.MOVED, store, commandBuffer);
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
                    stop(s, StopReason.MOVED, store, commandBuffer);
                    return false;
                }
            }
        }
        if (s.entityMountMode && !s.entitySteerable) {
            StationEntityMountController.snapBack(s.mountAnchorRef, store, s.blockX, s.blockY, s.blockZ);
        }
        MovementStatesComponent ms = store.getComponent(s.ref, MovementStatesComponent.getComponentType());
        if (ms != null && ms.getMovementStates() != null && ms.getMovementStates().crouching) {
            stop(s, StopReason.PLAYER_EXIT, store, commandBuffer);
            return false;
        }
        if (s.toolReq != null) {
            Player heartbeatPlayer = store.getComponent(s.ref, Player.getComponentType());
            boolean matches = heartbeatPlayer != null && heldToolMatches(heartbeatPlayer, s.toolReq);
            ItemStack heldStack = heartbeatPlayer != null
                    ? InventoryAccess.activeHotbarItemOf(heartbeatPlayer) : null;
            boolean broken = heldStack != null && heldStack.isBroken();
            StopReason toolStop = toolGateStopReason(matches, broken);
            if (toolStop != null) {
                if (toolStop == StopReason.TOOL_BROKEN) {
                    String heldItemId = heldStack != null && heldStack.getItemId() != null
                            ? heldStack.getItemId() : "";
                    StationEvents.fireToolBroke(store, s.playerRef, s.playerUuid, s.sessionId, s.stationId,
                            heldItemId);
                }
                stop(s, toolStop, store, commandBuffer);
                return false;
            }
        }
        if (System.currentTimeMillis() - s.startedAtMs >= s.maxDurationMs) {
            stop(s, StopReason.SESSION_CAP, store, commandBuffer);
            return false;
        }
        if (!stationsEnabled()) {
            stop(s, StopReason.FEATURE_DISABLED, store, commandBuffer);
            return false;
        }
        StationHoldController.applyHold(s, store);
        return true;
    }

    /**
     * One cycle: an authored {@code Steps} program (design 9.3/9.5, phase 2 leg E - the anvil's
     * Enhance ritual) when the resolved action authors one; else a real Convert cycle when a
     * conversion is runnable, an opt-in idle practice cycle when materials are absent AND the
     * station enables {@code Work.Idle}, or a stop (out-of-inputs / inventory-full) otherwise.
     */
    private boolean runCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                             @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            stop(s, StopReason.STATION_GONE, store, commandBuffer);
            return false;
        }
        Player player = store.getComponent(s.ref, Player.getComponentType());
        if (player == null) {
            stop(s, StopReason.WORLD_CHANGED, null, commandBuffer);
            return false;
        }

        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, s.actionId);
        if (action.getSteps() != null && action.getSteps().length > 0) {
            return runAuthoredProgram(s, store, commandBuffer, asset, action, player);
        }

        Custody custody = action.getCustody();
        StationAsset.Conversion[] conversions =
                StationCatalog.getInstance().resolvedConversions(asset, action.getActionId(), action.getRecipe());
        ConversionCheck check = custody != null
                ? firstRunnableConversionFromCustody(custodyByBlock.get(s.blockKey), player, conversions)
                : firstRunnableConversion(player, conversions);
        if (check.state == ConversionState.RUNNABLE) {
            if (s.idleMode) {
                s.idleMode = false;
            }
            return runRealCycle(s, store, commandBuffer, asset, action, player, check);
        } else if (check.state == ConversionState.NO_INPUTS && s.idleEnabled) {
            if (!s.idleMode) {
                s.idleMode = true;
                toast(s.playerRef, RpgMsg.tr("ui.station.practice"));
            }
            s.nextCycleAtMs = System.currentTimeMillis() + s.idleCycleMs;
            return runIdleCycle(s, store, commandBuffer, action);
        } else if (check.state == ConversionState.NO_INPUTS) {
            stop(s, StopReason.OUT_OF_INPUTS, store, commandBuffer);
            return false;
        } else { // NO_ROOM
            stop(s, StopReason.INVENTORY_FULL, store, commandBuffer);
            return false;
        }
    }

    /**
     * The real Convert cycle: design 9.3's "one engine, no dual path" - the pre-chosen {@code
     * check} conversion becomes the IMPLICIT four-step program ({@link ImplicitProgram}), walked
     * through {@link #dispatchProgram}, the SAME choke point an authored multi-action
     * {@code Steps} program uses. Precondition: {@code check.state == RUNNABLE}.
     *
     * <p>The implicit program has no {@code Wait} step, so it NEVER suspends - this call always
     * resolves synchronously to {@code Completed} or {@code Failed} within the SAME frame (the
     * byte-stable regression anchor: today's sawmill schedules exactly as before, every
     * pre-refactor behavior test stays green).
     */
    private boolean runRealCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
                                 @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player,
                                 @Nonnull ConversionCheck check) {
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
                attemptCycleIndex, 0, false);
    }

    /**
     * An AUTHORED {@code Steps} program cycle attempt (design 9.3/9.5, phase 2 leg E): unlike
     * {@link #runRealCycle}, there is no live {@code ConversionCheck} - the program's own steps
     * (Consume/Produce/Roll/Stamp/...) validate and mutate whatever they individually need
     * (custody, inventory, reagents), so {@link #dispatchProgram} is called with a {@code null}
     * cycle output (a Roll step's {@code BonusOutputCopies} stays inert for an authored program
     * with no live conversion output, same as today's Completion-trigger pass - design 4.5.1's
     * existing rule, not a new one).
     */
    private boolean runAuthoredProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player) {
        // [D77DIAG] temporary, one-sweep-removable (see StationStepHandlers/stop() siblings) -
        // proves the moment a stepped program is actually dispatched for a cycle, PLUS the
        // stepDeadlineMs value at that instant (maintainer's leading-suspect check: a fresh
        // dispatch must never inherit a stale nonzero deadline from a prior run - dispatchProgram
        // itself now asserts this explicitly, see its own [D77DIAG] STALE warn).
        Log.info("[D77DIAG] program-dispatch station=" + asset.getId() + " action=" + action.getActionId()
                + " stepDeadlineMsAtEntry=" + s.stepDeadlineMs + " now=" + System.currentTimeMillis());
        List<StationStep> steps = Arrays.asList(action.getSteps());
        int attemptCycleIndex = s.cyclesDone + 1;
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps, null, attemptCycleIndex, 0, false);
    }

    /**
     * Re-enters a {@code programSuspended} session's in-flight program at
     * {@code s.programIndex}, called from {@link #tickFrameOnce} once {@code s.stepDeadlineMs}
     * passes - bypassing the normal {@code Work.CycleMs} cadence gate entirely while suspended.
     * Rebuilds NOTHING from live inventory state (the whole point of {@link StationSession}'s
     * {@code activeProgram*} snapshot fields, design 9.3: a resume must never re-derive WHICH
     * conversion is running, since the live inventory may have changed mid-suspension). A
     * {@code null} {@code activeProgramCycleOutput} is LEGAL here (an authored program's own
     * cycle output, phase 2 leg E - only a missing {@code steps} snapshot is an error).
     */
    private boolean resumeCycleProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            stop(s, StopReason.STATION_GONE, store, commandBuffer);
            return false;
        }
        Player player = store.getComponent(s.ref, Player.getComponentType());
        if (player == null) {
            stop(s, StopReason.WORLD_CHANGED, null, commandBuffer);
            return false;
        }
        List<StationStep> steps = s.activeProgramSteps;
        if (steps == null) {
            Log.warn("STATION resume with no active program snapshot for '" + s.stationId + "' - stopping");
            stop(s, StopReason.STEP_FAILED, store, commandBuffer);
            return false;
        }
        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, s.actionId);
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps, s.activeProgramCycleOutput,
                s.activeProgramCycleIndex, s.programIndex, true);
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
     * early. {@code cycleOutput} is nullable (an authored program with no live Convert check,
     * design 9.3/9.5's Steps programs, phase 2 leg E).
     *
     * <p>{@code Work.Repeat: false} (design 9.3's "one completed program run completes the
     * SESSION" - the anvil's Enhance ritual): a COMPLETED program under a non-repeating action
     * stops the session right here, non-silent, immediately after the cycle-completed event fires
     * (so XP/luck listeners still see it) - never schedules another cycle attempt.
     *
     * <p><b>[D77DIAG] {@code resuming} + the stepDeadlineMs hardening (maintainer-flagged leading
     * suspect, round-7 D77):</b> {@code false} from {@link #runRealCycle}/{@link #runAuthoredProgram}
     * (a FRESH cycle attempt, {@code startIndex} always 0), {@code true} from
     * {@link #resumeCycleProgram} (re-entering a previously suspended program at
     * {@code s.programIndex}, which can itself legally be 0 - so {@code startIndex == 0} alone
     * can NEVER distinguish a fresh dispatch from a resume, this explicit flag is the only
     * reliable signal). A FRESH dispatch now EXPLICITLY resets {@code s.stepDeadlineMs} to 0
     * before the walk starts - this used to be an IMPLICIT invariant only (every {@code Wait}
     * step resets its own committed deadline to 0 the instant it succeeds, so a fresh dispatch
     * "should" already read 0 by construction), made explicit + logged here so a fresh program's
     * very first {@code Wait} step can never inherit a stale nonzero value from anywhere. Also
     * feeds {@link StationStepContext#resumingStep} (the step object at {@code startIndex} when
     * {@code resuming} is true, else {@code null}) - {@code StationStepRegistry}'s generic
     * per-step Presentation entry reads it to skip the suspend-resume RE-CHECK of an
     * already-played step.
     */
    private boolean dispatchProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player, @Nonnull List<StationStep> steps,
            @Nullable ItemStack cycleOutput, int attemptCycleIndex, int startIndex, boolean resuming) {
        if (!resuming) {
            if (s.stepDeadlineMs != 0L) {
                // [D77DIAG] this is the exact staleness the maintainer flagged as the leading
                // suspect for the ~600ms ritual-completion report - a fresh cycle attempt reading
                // a nonzero stepDeadlineMs left over from somewhere else. Should never fire given
                // every Wait step's own success-path reset; a WARN here on a live run is the
                // smoking gun if it ever does.
                Log.warn("[D77DIAG] program-dispatch station=" + asset.getId() + " action=" + action.getActionId()
                        + " STALE stepDeadlineMs=" + s.stepDeadlineMs + " reset to 0 at fresh dispatch, now="
                        + System.currentTimeMillis());
            }
            s.stepDeadlineMs = 0L;
        }
        StationStep resumingStep = resuming && startIndex >= 0 && startIndex < steps.size()
                ? steps.get(startIndex) : null;
        FactorSnapshot snapshot = new FactorSnapshot(buildFactorContext(s, store, player, action, attemptCycleIndex));
        StationStepContext ctx = new StationStepContext(s, store, commandBuffer, player, asset, action, snapshot,
                steps, attemptCycleIndex, cycleOutput, resumingStep);

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
            stop(s, fail.reason(), store, commandBuffer);
            return false;
        }

        s.cyclesDone++;
        if (s.durabilityPerCycle > 0) {
            drainHeldToolDurability(store, s.ref, player, s.durabilityPerCycle);
        }
        // FIX ROUND: both reads below now resolve off the RESOLVED action, not the station-level
        // asset - mirroring the action.getWork().effectiveRepeat() read two lines down. Before this
        // fix a multi-action station whose Work groups live entirely under Actions.* (the anvil's
        // convert/enhance) forwarded ZERO xp asks: asset.getWork() was null, so xpAsks() returned
        // List.of() and StationCycleCompletedEvent.xpAsks() was empty for every real cycle.
        double xpMult = resolveXpMultiplier(player, action.getTool());
        onCycleCompleted(s, store, commandBuffer, action, xpMult, false, s.cyclesDone);

        StationAsset.Work work = action.getWork();
        if (work != null && !work.effectiveRepeat()) {
            stop(s, StopReason.RITUAL_COMPLETE, store, commandBuffer);
            return false;
        }
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
     * cycle presentation + the (idle-scaled) XP-ask forwarding. Threads the RESOLVED action
     * (FIX ROUND, the same correction as the real-cycle path in {@link #dispatchProgram}) so a
     * multi-action station's idle practice reads ITS running action's {@code Work}/
     * {@code Presentation}, not the station-level default.
     */
    private boolean runIdleCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                 @Nonnull ActionResolver.ResolvedAction action) {
        onCycleCompleted(s, store, commandBuffer, action, 1.0, true, s.cyclesDone);

        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.MOMENT_CYCLE, action.getPresentation(), blockPos);
        s.cyclesDone++;
        return true;
    }

    /**
     * Fires {@code StationCycleCompletedEvent} (design section 3.1/7.2): forwards this cycle's
     * {@code Work.Xp} asks (idle-scaled by {@code Work.Idle.XpFraction} when {@code idle}) plus
     * the resolved tool multiplier (forced {@code 1.0} for an idle cycle). A listening
     * progression mod (the MMO bridge) reads {@code action.getWork().getXp()} semantics off the
     * event's {@code XpAsk} list to know what an ask means; this engine never interprets it.
     *
     * <p><b>FIX ROUND:</b> takes the RESOLVED {@code action}, not the station-level
     * {@code StationAsset} - a multi-action station whose {@code Work} groups live entirely under
     * {@code Actions.*} (the anvil's {@code convert}/{@code enhance}, both authoring {@code Xp}
     * with NO station-level {@code Work} group at all) forwarded ZERO xp asks before this fix
     * ({@code asset.getWork()} resolved {@code null}, so {@link #xpAsks} always returned
     * {@code List.of()}) and the event always carried {@code actionId="work"} regardless of which
     * action actually ran. {@code action.getActionId()}/{@code action.getWork()} carry the fix -
     * see {@link ActionResolver#resolve} for why this is safe for every single-action station too
     * (a station with no {@code Actions} map resolves {@code action}'s groups to the station-level
     * ones verbatim, byte-identical to before).
     *
     * <p>{@code commandBuffer} is GUARANTEED non-null here: both call sites (the real-cycle path
     * in {@link #runRealCycle} and the idle-cycle path in {@link #runIdleCycle}) run inside the
     * per-world frame drain ({@link #tickFrameOnce}), which always holds a live {@code
     * CommandBuffer} for the current tick (critique fix, binding - see the event's own javadoc).
     */
    private static void onCycleCompleted(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull ActionResolver.ResolvedAction action,
            double toolMultiplier, boolean idle, int cycleIndex) {
        List<XpAsk> asks = xpAsks(action.getWork(), idle, s.idleXpFraction);
        StationEvents.fireCycleCompleted(store, commandBuffer, s.playerRef, s.playerUuid, s.sessionId,
                s.stationId, action.getActionId(), cycleIndex, idle, asks, toolMultiplier);
    }

    /**
     * The forwarded {@code Work.Xp} asks for one cycle-completed event (design section 4.4.1):
     * a real cycle forwards the RAW authored {@code PerCycle} (the listener multiplies by
     * {@link StationCycleCompletedEvent#toolMultiplier()}); an idle cycle pre-scales each ask
     * by {@code idleXpFraction} and the caller forces {@code toolMultiplier} to {@code 1.0}
     * (matching today's idle semantics: fractional XP, no progress). A blank/missing skill id
     * entry is skipped (the validator's {@code MISSING_XP_SKILL} catches the authoring mistake).
     * {@code work} MUST be the resolved action's own {@code Work} group (FIX ROUND) - the
     * station-level {@code StationAsset.getWork()} is null for a station like the anvil whose
     * every {@code Work} group lives under {@code Actions.*}.
     */
    @Nonnull
    private static List<XpAsk> xpAsks(@Nullable StationAsset.Work work, boolean idle, double idleXpFraction) {
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

    /**
     * Station-level {@code Work.Xp} skill ids, in authoring order (for {@link
     * FactorContext#progressionSkills()}) - used only by {@link #checkRequires} (pre-session, no
     * resolved action exists yet) and {@link #rollCompletionLoot} (post-session, station-level
     * {@code Loot}, not per-action). Delegates to the {@link #progressionSkills(StationAsset.Work)}
     * overload every per-cycle/action-aware caller uses instead (FIX ROUND).
     */
    @Nonnull
    private static List<String> progressionSkills(@Nonnull StationAsset asset) {
        return progressionSkills(asset.getWork());
    }

    /**
     * {@code Work}-overriding form (FIX ROUND): {@link #buildFactorContext}'s action-aware
     * overload passes the RESOLVED action's own {@code Work} group here - a multi-action
     * station's per-cycle factor context must see the ACTUAL running action's {@code Xp} skills,
     * not the (possibly {@code Work}-less) station-level default the anvil authors.
     */
    @Nonnull
    private static List<String> progressionSkills(@Nullable StationAsset.Work work) {
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
                null, s.playerRef, s.stationId, ACTION_WORK, s.cyclesDone, store, s.blockX, s.blockY, s.blockZ);
        applyGrantResult(s, store, result);
    }

    /**
     * Folds a {@link LootEngine.GrantResult} into the session's item ledger, plays every
     * reached floor's {@code Presentation} through {@link #emitMoment} on {@link
     * StationFlairs#MOMENT_RARE_FIND}, and fires a round-5 item-specific GOLD "what you gained"
     * notification ({@link #notifyItemGain}, {@code lucky=true}) per distinct item id in EITHER
     * grant kind - REPLACES the old generic {@code ui.station.lucky}/{@code ui.station.rare_find}
     * toasts (design 4.5.1), which no longer fire from here.
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
            emitMoment(store, s, StationFlairs.MOMENT_RARE_FIND, p, blockPos);
        }
        if (s.playerRef != null) {
            for (Map.Entry<String, Integer> e : result.getBonusCopyItems().entrySet()) {
                notifyItemGain(s.playerRef, e.getKey(), e.getValue(), true);
            }
            for (Map.Entry<String, Integer> e : result.getDropListItems().entrySet()) {
                notifyItemGain(s.playerRef, e.getKey(), e.getValue(), true);
            }
        }
    }

    /**
     * Per-cycle api {@link FactorContext} for the built-in {@code rpgstations:} factors ({@code
     * api.impl.FactorRegistryImpl#registerBuiltins}) plus every other registered provider:
     * session seconds elapsed, the CURRENT (already-incremented) cycle index, and the
     * currently-held item's tool power / durability percent - read fresh, mirroring {@link
     * #resolveXpMultiplier}'s no-snapshot convention. Station-level (no resolved action) - used
     * only by {@link #rollCompletionLoot} (post-session, station-level {@code Loot}, not
     * per-action; every real/idle cycle instead uses the {@link #buildFactorContext(StationSession,
     * Store, Player, ActionResolver.ResolvedAction, int)} action-aware overload, FIX ROUND).
     */
    @Nonnull
    private static FactorContext buildFactorContext(@Nonnull StationSession s, @Nullable Store<EntityStore> store,
            @Nonnull Player player, @Nonnull StationAsset asset) {
        return buildFactorContext(s, store, player, asset, s.cyclesDone);
    }

    /**
     * {@code cycleIndex}-overriding, station-level form (no resolved action) - {@link
     * #buildFactorContext(StationSession, Store, Player, StationAsset)} delegates here.
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
     * ACTION-AWARE form (FIX ROUND): {@link #dispatchProgram} passes the RESOLVED action here
     * instead of the station-level {@code asset} - a Roll/Stamp step's factor context must report
     * the ACTUAL running action id and its OWN {@code Work.Xp} skills (mirroring {@link #xpAsks}'
     * same correction), not the station-level default. The anvil's {@code convert}/{@code enhance}
     * author NO station-level {@code Work} at all, so {@code progressionSkills(asset)} always
     * resolved empty here and every step saw {@code actionId="work"} instead of {@code "convert"}/
     * {@code "enhance"} before this fix. {@code cycleIndex} is the ATTEMPT index (design section
     * 9.3 - {@code s.cyclesDone + 1}, computed before {@code s.cyclesDone} itself advances) so a
     * Roll step's factor context sees the cycle it is actually running, not the last COMPLETED
     * one.
     */
    @Nonnull
    private static FactorContext buildFactorContext(@Nonnull StationSession s, @Nullable Store<EntityStore> store,
            @Nonnull Player player, @Nonnull ActionResolver.ResolvedAction action, int cycleIndex) {
        long sessionSeconds = Math.max(0L, (System.currentTimeMillis() - s.startedAtMs) / 1000L);
        return FactorContext.builder()
                .store(store)
                .playerRef(s.playerRef)
                .playerId(s.playerUuid)
                .stationId(s.stationId)
                .actionId(action.getActionId())
                .sessionSeconds(sessionSeconds)
                .cycleIndex(cycleIndex)
                .toolPower(resolveHeldToolPower(player, action.getTool()))
                .toolDurabilityPercent(resolveHeldToolDurabilityPercent(player))
                .progressionSkills(progressionSkills(action.getWork()))
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
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
        Item item = held != null ? held.getItem() : null;
        ItemTool itemTool = item != null ? item.getTool() : null;
        ItemToolSpec[] specs = itemTool != null ? itemTool.getSpecs() : null;
        return StationToolScaling.heldPowerFor(toolPowers(specs), gatherType);
    }

    /** The active hotbar item's durability percent [0,100]; 100 when no item held or it tracks no durability. */
    private static double resolveHeldToolDurabilityPercent(@Nonnull Player player) {
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
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
        rows.addAll(enhanceLedgerRows(s.enhanceOutcomes));
        return rows;
    }

    /**
     * The enhance ledger rows for the summary panel (design section 9.5, phase 2 round-7 D-6):
     * per committed {@link StationEnhanceOutcome}, one row per {@link EnhanceLine} the registered
     * stamper reported (the {@code line.label()} renders VERBATIM - the provider owns the stat
     * vocabulary, wording, and per-stat color, so RpgStations stays free of MMO stat vocabulary),
     * plus, when a stamp added max durability, ONE engine-owned durability row the engine composes
     * AND colors itself ({@link #ENHANCE_ROW_COLOR} - durability is RpgStations-native, real even
     * with no stamper registered). Extracted pure/static so it unit-tests without a live session
     * service; the icon is the enhanced item itself.
     */
    @Nonnull
    static List<StationSummaryHud.LedgerRow> enhanceLedgerRows(@Nonnull List<StationEnhanceOutcome> outcomes) {
        List<StationSummaryHud.LedgerRow> rows = new ArrayList<>();
        for (StationEnhanceOutcome outcome : outcomes) {
            for (EnhanceLine line : outcome.lines()) {
                rows.add(new StationSummaryHud.LedgerRow(outcome.itemId(), line.points(), line.label(),
                        SummaryRow.Kind.ENHANCE));
            }
            if (outcome.durabilityAdded() > 0) {
                Message line = RpgMsg.tr("ui.station.summary.enhance_durability",
                        formatDurability(outcome.durabilityAdded())).color(ENHANCE_ROW_COLOR);
                rows.add(new StationSummaryHud.LedgerRow(outcome.itemId(),
                        (int) Math.round(outcome.durabilityAdded()), line, SummaryRow.Kind.ENHANCE));
            }
        }
        return rows;
    }

    /** Formats a durability delta for the summary row: a whole number drops its trailing {@code .0}. */
    @Nonnull
    private static String formatDurability(double amount) {
        return amount == Math.rint(amount) ? NumberFormatter.grouped((long) amount) : String.valueOf(amount);
    }

    /**
     * The ONE presentation-playback choke point: every station moment funnels through here.
     * Resolves the effective presentation through {@link StationFlairs#effective} FIRST, then
     * plays {@code Sound}, {@code Particles}, and (new this leg) {@code Shake} - see
     * {@link Presentation.Shake}'s javadoc for the exact {@code CameraShakeService} parameter
     * shape this leaf was verified against (critique m6 binding fix). Shake needs the player
     * SPECIFICALLY (not "nearby players" like Sound3D/ModelParticleService), so it reads
     * {@code s.playerRef} rather than {@code targetPos}.
     *
     * <p><b>Leg F (design section 9.6):</b> {@code momentId} is an open STRING (see {@link
     * StationFlairs}'s well-known constants + {@link StationFlairs#stepMomentId}), and the flair
     * map resolved against is the UNION of the station's own inline {@code Flairs} with every
     * applicable standalone {@code asset.FlairAsset} ({@link #effectiveFlairs}).
     */
    static void emitMoment(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
                                   @Nonnull String momentId, @Nullable Presentation base,
                                   @Nonnull Vector3d targetPos) {
        Presentation p = StationFlairs.effective(base, effectiveFlairs(s), momentId, s.playerUuid, s.stationId);
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
     * The station's EFFECTIVE {@code flairId -> momentId -> Presentation} map (design 9.6's
     * open vocabulary, {@link FlairCatalog#effectiveFlairsFor}), or {@code null} when the
     * station itself is gone (a mid-session catalog re-fold has dropped it entirely).
     */
    @Nullable
    private static Map<String, Map<String, Presentation>> effectiveFlairs(@Nonnull StationSession s) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null) {
            return null;
        }
        return FlairCatalog.getInstance().effectiveFlairsFor(s.stationId, asset);
    }

    /**
     * The per-swing cadence cue: re-fires the work animation as a ONE-SHOT TOGETHER with the
     * session's snapshotted {@link StationSession#swingPresentation} at the block. The clip
     * re-fire routes by {@link StationSession#seatMode}.
     */
    private void runSwing(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                          @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (StationCatalog.getInstance().getStation(s.stationId) == null) {
            return;
        }
        Player swingPlayer = store.getComponent(s.ref, Player.getComponentType());
        if (s.puppetActive) {
            // Puppet presentation (design 4.3): supersedes useActionSlotForSwing entirely - the
            // puppet has no sit pose to fight, so it always plays its natural Emote-slot clip
            // (its own default, or the currently-suspended step's Puppet.Clip override) and syncs
            // its held prop, instead of routing anything onto the (now possibly hidden) real
            // player.
            StationPuppetController.playSwing(s, store, commandBuffer, swingPlayer);
        } else if (useActionSlotForSwing(s.seatMode)) {
            StationHoldController.playActionSwing(s, swingPlayer, store);
        } else {
            StationHoldController.playEmote(s, store);
        }
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.MOMENT_SWING, s.swingPresentation, blockPos);

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

    /**
     * The delayed swing-impact cue itself, on its OWN {@link StationFlairs#MOMENT_IMPACT} moment
     * id (design 9.6 - split off {@link StationFlairs#MOMENT_SWING} this leg; a flair author can
     * now target the impact cue independently of the swing cue that scheduled it).
     */
    private static void runImpact(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.MOMENT_IMPACT, s.impactPresentation, blockPos);
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
     * The tool-power multiplier for THIS cycle: resolves {@code tool}'s EFFECTIVE gather type,
     * reads the held item's max matching {@code ItemToolSpec} power, and delegates the clamp
     * formula to {@link StationToolScaling}. Returns 1.0 when {@code tool} authors no
     * {@code Tool.XpScale}, or when neither gather type resolves.
     *
     * <p><b>FIX ROUND:</b> {@code tool} is the RESOLVED action's own {@code Tool} group ({@code
     * action.getTool()}), not the station-level {@code asset.getTool()} - the same
     * station-vs-action smell {@link #onCycleCompleted}/{@link #buildFactorContext} had. Harmless
     * for every shipped station today (none override {@code Tool} per-action - the anvil's
     * {@code convert}/{@code enhance} both inherit the station-level {@code Tool.Ids} gate, and
     * neither authors an {@code XpScale}), but a future multi-action station with a per-action
     * {@code Tool.XpScale} would silently read the wrong one without this fix.
     */
    private static double resolveXpMultiplier(@Nonnull Player player, @Nullable StationAsset.Tool tool) {
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
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
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
     * the {@link StationFlairs#MOMENT_COMPLETION} moment id. Plays at the PLAYER's position, not
     * the block (completion celebrates the player).
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
        emitMoment(store, s, StationFlairs.MOMENT_COMPLETION, asset.getCompletion(), playerPos);
    }

    /**
     * The one idempotent exit funnel. Each teardown step is individually guarded so one
     * failure never skips the rest. {@code store} is null on paths where the entity is gone.
     *
     * <p><b>R4 companion fix, extended (round-6 puppet smoke, D-A secondary)</b>: {@code
     * commandBuffer} is nullable - most call sites (the frame-tick drain, {@code toggle}'s
     * re-press exit, and now {@link #onDamage}/{@link #stopForRef} - both DAMAGED and DIED thread
     * the live {@code CommandBuffer} their own dispatch already receives) hold one, so both the
     * custody display-prop despawn AND the puppet {@code Scale} reveal apply on those paths
     * instead of being silently skipped (a damage-interrupt/death used to strand a still-connected
     * player invisible with no recovery until their next {@code PlayerReadyEvent}). Only the
     * shutdown/disconnect sweeps ({@link #stopFor}/{@link #stopAll}) genuinely have no live
     * accessor and still pass {@code null} - {@link #returnCustody} falls back cleanly there (the
     * custody display prop, if any, is left behind - it is {@code NonSerialized} so it never
     * survives a restart regardless), and the puppet reveal correctly relies on the production
     * {@code PlayerReadyEvent} safety net ({@link StationPuppetController#reassertOnReady}) for
     * those two paths, since a disconnecting/shutting-down player has no live entity left to
     * network a reveal packet to anyway.
     */
    private void stop(@Nonnull StationSession s, @Nonnull StopReason reason,
                      @Nullable Store<EntityStore> store, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        if (!s.stopped.compareAndSet(false, true)) {
            return;
        }
        // [D77DIAG] temporary, one-sweep-removable - one line in the existing stop funnel.
        Log.info("[D77DIAG] stop station=" + s.stationId + " action=" + s.actionId + " reason=" + reason
                + " now=" + System.currentTimeMillis());
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
        String stopActionId = s.actionId != null ? s.actionId : ACTION_WORK;
        Custody stopCustody = stopAsset != null ? ActionResolver.resolve(stopAsset, stopActionId).getCustody() : null;
        returnCustody(s, stopCustody, commandBuffer);

        // Puppet reveal + despawn (round-4 design, doc section 4.4): the SAME unconditional-on-
        // every-exit-path posture as returnCustody above, threading the SAME nullable
        // commandBuffer (accessor-bug fix, fix round: the mutation itself must go through the
        // tick-safe commandBuffer, never a live store, from a processing context like toggle/the
        // heartbeat) - see StationPuppetController#revealAndDespawn for the full contract.
        StationPuppetController.revealAndDespawn(s, commandBuffer);

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
            // TICK-SAFETY FIX (R4-pattern, see StationEntityMountController's header javadoc): a
            // direct store.removeEntity throws "Store is currently processing!" from inside an
            // interaction-handler/tick context (every real call site here) - despawn takes the
            // tick-safe commandBuffer instead. When commandBuffer is null (the shutdown/
            // disconnect/damage/death sweeps), the anchor is left behind - harmless, it is
            // NonSerialized so it cannot survive a restart regardless, the SAME documented
            // tradeoff returnCustody's own display-prop despawn already accepts.
            StationEntityMountController.despawn(s.mountAnchorRef, commandBuffer);
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

    /**
     * Damage interrupt (from {@code StationInterruptDamageSystem}, Inspect group, read-only).
     *
     * <p><b>Puppet-reveal fix (round-6 puppet smoke, D-A secondary):</b> {@code commandBuffer} is
     * the SAME live one {@code StationInterruptDamageSystem#handle} already receives from its
     * {@code DamageEventSystem} dispatch - threading it through here (instead of the prior
     * always-{@code null}) lets {@link #stop}'s puppet reveal
     * ({@link StationPuppetController#revealAndDespawn}) actually apply the {@code Scale} un-hide
     * on this exit path, rather than being skipped entirely (a damage-interrupt left a hidden
     * player stuck invisible for the rest of the connected session with no recovery until the next
     * {@code PlayerReadyEvent} - a very common way to end a work session). A placed-input custody
     * display prop despawn still routes through the same {@code commandBuffer}.
     */
    public void onDamage(@Nonnull Ref<EntityStore> victimRef, @Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer) {
        if (byPlayer.isEmpty()) {
            return;
        }
        for (StationSession s : byPlayer.values()) {
            if (s.ref != null && s.ref.getStore() == store
                    && s.ref.getIndex() == victimRef.getIndex() && s.interruptOnDamage) {
                stop(s, StopReason.DAMAGED, store, commandBuffer);
                return;
            }
        }
    }

    /**
     * Death hook - camera reset fires before the respawn screen.
     *
     * <p><b>Puppet-reveal fix (round-6 puppet smoke, D-A secondary):</b> {@code commandBuffer} is
     * the SAME live one {@code StationDeathSystem#onComponentAdded} already receives - see
     * {@link #onDamage}'s javadoc for the identical rationale (death is connected, so no
     * {@code PlayerReadyEvent} follows to trigger the safety net either).
     */
    public void stopForRef(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                           @Nonnull StopReason reason, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        for (StationSession s : byPlayer.values()) {
            if (s.ref != null && s.ref.getStore() == store && s.ref.getIndex() == ref.getIndex()) {
                stop(s, reason, store, commandBuffer);
                return;
            }
        }
    }

    /** Disconnect hook; no store, entity is gone. */
    public void stopFor(@Nonnull UUID playerUuid, @Nonnull StopReason reason) {
        StationSession s = byPlayer.get(playerUuid);
        if (s != null) {
            stop(s, reason, null, null);
        }
    }

    /** Server shutdown: best-effort teardown of every live session. */
    public void stopAll(@Nonnull StopReason reason) {
        for (StationSession s : new ArrayList<>(byPlayer.values())) {
            stop(s, reason, null, null);
        }
    }

    /**
     * The puppet presentation route's {@code PlayerReadyEvent} safety net (design section 4.4,
     * leg P5): see {@link StationPuppetController#reassertOnReady} for the full contract.
     * Deliberately NOT gated on any remembered session - a restart wipes every in-memory {@link
     * StationSession} by construction, so this runs unconditionally on every ready.
     */
    public void reassertPuppetOnReady(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        StationPuppetController.reassertOnReady(ref, store);
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
     * Scan {@code conversions} (the caller's already action-resolved
     * {@code StationCatalog.resolvedConversions} result) in order; the FIRST whose input the
     * inventory satisfies wins. {@code NO_ROOM} is reported only when some conversion had its
     * input but lacked output room.
     */
    @Nonnull
    private ConversionCheck firstRunnableConversion(@Nonnull Player player,
            @Nullable StationAsset.Conversion[] conversions) {
        if (conversions == null || conversions.length == 0) {
            return new ConversionCheck(ConversionState.NO_INPUTS, false, null, 0, null, 0);
        }
        boolean sawInputWithoutRoom = false;
        try {
            var combined = InventoryAccess.combinedBackpackStorageHotbarOf(player);
            var backpack = InventoryAccess.storageOf(player);
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
     * SAME action-resolved {@code conversions} scan, but availability reads {@code claim} (the
     * placed-input pouch) instead of the player's live inventory - output room is STILL checked
     * against the player's real inventory (only the input side moved into custody at placement;
     * {@code Produce} always writes {@code To: Inventory}). A null/empty {@code claim} always
     * yields {@code NO_INPUTS} (an empty custody station behaves exactly like an out-of-materials
     * one, so the existing idle-practice fallback in {@link #toggle}/{@link #runCycle} applies
     * unchanged).
     */
    @Nonnull
    private ConversionCheck firstRunnableConversionFromCustody(@Nullable StationCustodyClaim claim,
            @Nonnull Player player, @Nullable StationAsset.Conversion[] conversions) {
        if (conversions == null || conversions.length == 0 || claim == null) {
            return new ConversionCheck(ConversionState.NO_INPUTS, false, null, 0, null, 0);
        }
        boolean sawInputWithoutRoom = false;
        try {
            var backpack = InventoryAccess.storageOf(player);
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
     * True when {@code held} satisfies {@code action}'s custody placement matcher: an explicit
     * {@link Custody#getInput()} when authored, else ANY of the RESOLVED action's
     * {@code Recipe.Conversions} inputs (the sawmill's "logs by ResourceTypeId family" - zero
     * extra authoring; the anvil's Enhance action always authors an explicit {@link Custody#getInput()}
     * instead, since it has no {@code Recipe} at all).
     */
    private static boolean custodyAccepts(@Nonnull Custody custody, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nullable ItemStack held) {
        if (held == null || held.isEmpty()) {
            return false;
        }
        String heldItemId = held.getItemId();
        String[] heldResourceTypeIds = liveResourceTypeIdsOf(heldItemId);
        var matcher = custody.getInput();
        if (matcher != null) {
            return StationCustody.matchesInput(matcher, heldItemId, heldResourceTypeIds, liveRawTagsOf(heldItemId),
                    liveFunctionOf(heldItemId));
        }
        StationAsset.Conversion[] conversions =
                StationCatalog.getInstance().resolvedConversions(asset, action.getActionId(), action.getRecipe());
        return conversions != null && conversions.length > 0
                && StationCustody.matchesAnyConversionInput(conversions, heldItemId, heldResourceTypeIds);
    }

    /**
     * One scanned inventory-fallback placement candidate (R3 fix - directive 5's held-else-
     * inventory ruling): the source container the match lives in, its slot within THAT
     * container, and the matched stack itself.
     */
    private record InventoryMatch(@Nonnull ItemContainer container, short slot, @Nonnull ItemStack stack) {
    }

    /**
     * R3 fix: when the player's held (active hotbar) stack does not satisfy {@code custody}'s
     * placement matcher, matching material sitting ELSEWHERE in the inventory (storage/backpack)
     * was previously invisible to placement - the station denied with the truthful-sounding but
     * misleading "no materials" toast even though the player was carrying the right item. Scans
     * the combined hotbar-storage-backpack view ({@link InventoryComponent#HOTBAR_STORAGE_BACKPACK},
     * the same priority order {@code Inventory}'s own combined accessors use) for the FIRST stack
     * {@link #custodyAccepts} accepts, skipping {@code skipSlot} (the already-tried held slot -
     * numerically identical to this combined view's own slot indices, since the hotbar container
     * is first in {@code HOTBAR_STORAGE_BACKPACK}). Returns {@code null} when nothing else
     * matches; never throws (an empty/unresolvable inventory just yields no match).
     */
    @Nullable
    private static InventoryMatch findFirstCustodyMatchInInventory(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull Custody custody, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, short skipSlot) {
        try {
            CombinedItemContainer combined =
                    InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
            short capacity = combined.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                if (slot == skipSlot) {
                    continue;
                }
                ItemStack stack = combined.getItemStack(slot);
                if (ItemStack.isEmpty(stack)) {
                    continue;
                }
                if (custodyAccepts(custody, asset, action, stack)) {
                    return new InventoryMatch(combined, slot, stack);
                }
            }
        } catch (Throwable t) {
            Log.warn("STATION custody inventory-fallback scan failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * Moves up to {@code custody.effectiveMaxQuantity() - currentTotal} of {@code matchedStack}'s
     * source slot into the block's claim (creating it, owned by {@code playerUuid}, on first
     * placement), removing exactly that amount from {@code sourceContainer}'s {@code sourceSlot}.
     * Returns the amount actually moved
     * (0 = nothing eligible / no room / the slot removal failed).
     *
     * <p><b>Metadata-preserving single-item placement</b> (a genuine fix, not in the original
     * leg-C design): when {@code custody.effectiveMaxQuantity() == 1} (the anvil's Enhance
     * action - one specific weapon, not a fungible resource pile), the REAL removed
     * {@link ItemStack} (durability/prior-enhancement metadata intact, via the removal
     * transaction's {@code getOutput()}) is stashed on the claim ({@link StationCustodyClaim#setUniqueStack})
     * alongside the count bookkeeping every custody claim already keeps - {@code toItemStacks()}
     * then returns THAT stack on auto-return instead of synthesizing a bare fresh one, and the
     * Stamp step reads/mutates it directly. The bulk fungible-resource case (the sawmill's logs,
     * any {@code MaxQuantity > 1} station) is completely unaffected - only the count map matters
     * there, exactly as before.
     *
     * <p><b>Display spawn (design section 9, phase 2 leg G)</b>: when {@code custody} authors a
     * {@link Custody.Display} group AND the claim has no live display entity yet
     * ({@link StationCustodyClaim#displayRef()} null - true on first placement, and a harmless
     * no-op guard on every top-up after that), spawns the PLACED-AS-ENTITY visual via
     * {@link StationCustodyDisplay#spawn} at {@code (blockX, blockY, blockZ)}, representing
     * {@link StationCustodyClaim#uniqueStack()} when set (the metadata-preserving single-item
     * case) else a fresh one-quantity stack of the just-placed {@code itemId} (the bulk case - the
     * visual represents PRESENCE, not the exact tally). A failed spawn is logged and swallowed
     * (never blocks the placement itself, which already succeeded).
     *
     * <p><b>R3 fix</b>: takes an explicit {@code sourceContainer}/{@code sourceSlot}/
     * {@code matchedStack} instead of deriving the active hotbar slot internally, so BOTH the
     * held-item placement AND the {@link #findFirstCustodyMatchInInventory} fallback go through
     * the IDENTICAL whole-stack + top-up + cap math, metadata-preserving single-item path, and
     * display-spawn logic - one engine, two candidate sources.
     *
     * <p><b>R4 fix</b>: takes {@code commandBuffer} and forwards it to {@link
     * StationCustodyDisplay#spawn} instead of {@code store} - {@code store.addEntity} throws
     * {@code IllegalStateException} when called from an interaction handler (this call site runs
     * inside {@code toggle()}, itself inside the store's processing lock), the swallowed root
     * cause of the display never appearing.
     */
    private int placeIntoCustody(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull String blockKey, @Nonnull UUID playerUuid,
            @Nonnull String stationId, @Nonnull String actionId, @Nonnull ItemContainer sourceContainer,
            short sourceSlot, @Nonnull ItemStack matchedStack, @Nonnull Custody custody,
            int blockX, int blockY, int blockZ) {
        String itemId = matchedStack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return 0;
        }
        StationCustodyClaim claim = custodyByBlock.get(blockKey);
        int currentTotal = claim != null ? claim.totalQuantity() : 0;
        int moveCount = StationCustody.placeableQuantity(currentTotal, matchedStack.getQuantity(),
                custody.effectiveMaxQuantity());
        if (moveCount <= 0) {
            return 0;
        }
        ItemStack movedStack;
        try {
            var transaction = sourceContainer.removeItemStackFromSlot(sourceSlot, moveCount);
            movedStack = transaction != null ? transaction.getOutput() : null;
        } catch (Throwable t) {
            Log.warn("STATION custody placement removal failed: " + t.getMessage());
            return 0;
        }
        if (claim == null) {
            claim = new StationCustodyClaim(playerUuid, stationId, actionId, blockX, blockY, blockZ);
            custodyByBlock.put(blockKey, claim);
        }
        claim.add(itemId, moveCount);
        if (custody.effectiveMaxQuantity() == 1 && movedStack != null) {
            claim.setUniqueStack(movedStack);
        }
        Custody.Display displayGroup = custody.getDisplay();
        if (displayGroup != null && claim.displayRef() == null) {
            ItemStack visualStack = claim.uniqueStack() != null ? claim.uniqueStack() : new ItemStack(itemId, 1);
            Ref<EntityStore> displayRef = StationCustodyDisplay.spawn(commandBuffer, visualStack, displayGroup,
                    blockX, blockY, blockZ);
            claim.setDisplayRef(displayRef);
        }
        return moveCount;
    }

    /**
     * Every custody auto-return path (design section 9.4: "unconsumed input auto-returns on
     * EVERY exit path") funnels here from {@link #stop}: removes the block's claim (if any owned
     * by THIS session's player), returns its items to the owner hotbar-first then backpack
     * storage (round-5, via {@link ItemGrantUtil}), else drops them at the block once, then flips
     * the block back to its Empty custody state.
     *
     * <p>{@code s.ref.getStore()} (not the {@code store} parameter {@link #stop} may have been
     * handed as {@code null}, e.g. on {@code stopAll}'s shutdown sweep) is the store source here -
     * a valid ref always knows its own owning store, so this covers the shutdown case too as long
     * as the ref has not actually been removed yet. This is also one of the two ONLY sites that
     * remove a claim from {@link #custodyByBlock} (the other is {@link #onCustodyBlockBroken}),
     * so it is one of the two despawn points for {@link StationCustodyClaim#displayRef()}
     * (design section 9, phase 2 leg G) - the display entity's lifecycle mirrors the claim's own.
     *
     * <p><b>R4 companion fix</b>: {@code commandBuffer} (nullable, forwarded from {@link #stop})
     * is what the display despawn now uses instead of a resolved {@code Store} - see {@link
     * StationCustodyDisplay#despawn}'s own javadoc for why. A {@code null} commandBuffer (the
     * damage/death/disconnect/shutdown hooks) leaves the display entity behind; it is {@code
     * NonSerialized} so it cannot survive a restart regardless.
     */
    private void returnCustody(@Nonnull StationSession s, @Nullable Custody custody,
            @Nullable CommandBuffer<EntityStore> commandBuffer) {
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
        StationCustodyDisplay.despawn(claim.displayRef(), commandBuffer);
        giveClaimToOwner(ownerStore, s.ref, claim, s.blockX, s.blockY, s.blockZ);
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
     * Hands {@code claim}'s contents to {@code ownerRef}'s owner - PER STACK, hotbar-first, then
     * backpack storage, then dropped at the block (round-5 refinement 1, via {@link
     * ItemGrantUtil} - supersedes this method's old ALL-OR-NOTHING batch-against-storage-only
     * check: a claim holding several distinct item ids can now land some in the hotbar, some in
     * the backpack, and only the genuine overflow on the ground, instead of dropping the WHOLE
     * claim the moment one combined-batch room check failed). Extracted (DRY) so both
     * {@link #returnCustody} (every session-stop exit) and {@link #retrieveCustody} (the press-F
     * retrieval feature) share ONE give-back engine. Returns the stacks that actually landed IN
     * INVENTORY (hotbar or backpack, excluding anything dropped) - {@link #retrieveCustody} uses
     * this to fire a native-pickup-mimic notification only for what the player genuinely
     * received (round-5 refinement 2); {@link #returnCustody} discards the return value. No-op
     * (empty result) when {@code claim} is empty. Never throws.
     */
    @Nonnull
    private static List<ItemStack> giveClaimToOwner(@Nullable Store<EntityStore> ownerStore,
            @Nullable Ref<EntityStore> ownerRef, @Nonnull StationCustodyClaim claim,
            int blockX, int blockY, int blockZ) {
        if (claim.isEmpty()) {
            return List.of();
        }
        List<ItemStack> stacks = claim.toItemStacks();
        // Try-guarded (SMOKE-FIX S3 hardening): this is the FIRST point a give-back mutates
        // anything, but the claim was already popped off custodyByBlock by the caller - an
        // unguarded throw here would escape entirely (never reaching the drop-at-block fallback
        // below), silently losing the items. Degrading to "no player found" routes every stack
        // through the SAME drop-at-block fallback every other unreachable-owner case already uses.
        Player player;
        try {
            player = ownerStore != null && ownerRef != null && ownerRef.isValid()
                    ? ownerStore.getComponent(ownerRef, Player.getComponentType()) : null;
        } catch (Throwable t) {
            Log.warn("STATION custody give player lookup failed: " + t.getMessage());
            player = null;
        }
        if (player == null) {
            dropCustodyAtBlock(ownerStore, blockX, blockY, blockZ, stacks);
            return List.of();
        }
        List<ItemStack> landedInInventory = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            try {
                if (ItemGrantUtil.grant(player, stack, ownerStore, blockX, blockY, blockZ)
                        != InventoryGrant.Landed.FALLBACK) {
                    landedInInventory.add(stack);
                }
            } catch (Throwable t) {
                Log.warn("STATION custody give failed for '" + stack.getItemId() + "': " + t.getMessage());
                dropCustodyAtBlock(ownerStore, blockX, blockY, blockZ, List.of(stack));
            }
        }
        return landedInInventory;
    }

    // ==================== Press-F custody retrieval (new feature) ====================

    /**
     * Press-F custody retrieval: the target is the PLACED-AS-ENTITY display entity itself (design
     * section 9's visual, phase 2 leg G), pressed via its own registered {@code Interactions}
     * entry ({@code interaction.StationRetrieveInteraction}, set at spawn by {@link
     * StationCustodyDisplay}). Resolves the clicked entity back to its owning block key by
     * NETWORK ID (comparing {@code NetworkId} values rather than {@code Ref} identity keeps the
     * matching decision core engine-free and unit-testable - see {@link StationCustodyRetrieval}),
     * then routes the eligibility decision through {@link StationCustodyRetrieval#decide}:
     * owner-only (the SAME ownership gate {@link #toggle}'s custody-placement branch already
     * enforces), and a NO-OP keyed toast while a session is ACTIVELY working that station - the
     * session owns its own input for the whole duration of a program run; yanking materials out
     * from under a running Consume step would either silently short a cycle or race the session's
     * own auto-return on its next stop. On success: gives the claim's contents back to the presser
     * via {@link #giveClaimToOwner} (the SAME inventory-first/drop-at-block engine {@link
     * #returnCustody} uses), despawns the display, flips the block back to its Empty custody
     * state, and removes the claim. Never throws.
     */
    public void retrieveCustody(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Ref<EntityStore> targetEntity) {
        try {
            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) {
                return;
            }
            NetworkId targetNetworkId = store.getComponent(targetEntity, NetworkId.getComponentType());
            if (targetNetworkId == null) {
                return;
            }
            Map<String, Integer> snapshot = new HashMap<>();
            for (Map.Entry<String, StationCustodyClaim> e : custodyByBlock.entrySet()) {
                Ref<EntityStore> displayRef = e.getValue().displayRef();
                if (displayRef == null || !displayRef.isValid()) {
                    continue;
                }
                NetworkId id = store.getComponent(displayRef, NetworkId.getComponentType());
                if (id != null) {
                    snapshot.put(e.getKey(), id.getId());
                }
            }
            String blockKey = StationCustodyRetrieval.findOwningBlockKey(snapshot, targetNetworkId.getId());
            StationCustodyClaim claim = blockKey != null ? custodyByBlock.get(blockKey) : null;
            boolean hasActiveSession = blockKey != null && byBlock.containsKey(blockKey);
            boolean isOwner = claim != null && claim.ownerId.equals(playerUuid);
            boolean claimNonEmpty = claim != null && !claim.isEmpty();
            StationCustodyRetrieval.Outcome outcome =
                    StationCustodyRetrieval.decide(claim != null, hasActiveSession, isOwner, claimNonEmpty);
            if (outcome == StationCustodyRetrieval.Outcome.RETRIEVE) {
                custodyByBlock.remove(blockKey, claim);
                StationCustodyDisplay.despawn(claim.displayRef(), commandBuffer);
                List<ItemStack> landed = giveClaimToOwner(store, ref, claim, claim.blockX, claim.blockY, claim.blockZ);
                StationAsset asset = StationCatalog.getInstance().getStation(claim.stationId);
                Custody custody = asset != null
                        ? ActionResolver.resolve(asset, claim.actionId).getCustody() : null;
                if (custody != null) {
                    try {
                        World world = WorldEvictors.worldOf(ref);
                        flipCustodyState(world, claim.blockX, claim.blockY, claim.blockZ, custody, false);
                    } catch (Throwable t) {
                        Log.fine("STATION retrieve block-state flip failed: " + t.getMessage());
                    }
                }
                if (!landed.isEmpty()) {
                    // Round-5 refinement 2: mimic the ENGINE's own native pickup feedback (message +
                    // SFX + item icon) per genuinely-received stack, via common's PickupMimic (which
                    // itself delegates to the real Player#notifyPickupItem - not a re-derived
                    // lookalike, scout findings 1-4). The classic generic toast below is reached
                    // only when EVERY stack dropped (landed stays empty) - a PARTIAL drop (some
                    // stacks landed, one or more overflowed to the block) fires this pickup
                    // feedback for what landed and gives no separate notice for the dropped
                    // remainder; "you picked it up" would still be a lie for something sitting on
                    // the ground, so that gap is accepted rather than mixing both toast shapes.
                    notifyNativePickup(store, ref, landed, claim.blockX, claim.blockY, claim.blockZ);
                } else {
                    toast(playerRef, RpgMsg.tr("ui.station.retrieve.done"));
                }
            }
            String key = retrieveOutcomeKey(outcome);
            if (key != null) {
                toast(playerRef, RpgMsg.tr(key));
            }
        } catch (Throwable t) {
            Log.warn("STATION custody retrieve failed: " + t.getMessage(), t);
        }
    }

    /**
     * The retrieve-toast key for a denial outcome; {@code RETRIEVE}'s own feedback is handled
     * inline in {@link #retrieveCustody} (round-5's native-pickup-mimic notification, or the
     * plain done toast when nothing landed in inventory) - never double-toast here.
     */
    @Nullable
    private static String retrieveOutcomeKey(@Nonnull StationCustodyRetrieval.Outcome outcome) {
        return switch (outcome) {
            case BUSY -> "ui.station.retrieve.busy";
            case NOT_OWNER -> "ui.station.occupied";
            case RETRIEVE, UNKNOWN_TARGET, NOTHING_TO_RETRIEVE -> null;
        };
    }

    /**
     * The "block broken" custody auto-return path ({@link StationCustodyBreakSystem}, no session
     * required - a player can place input then walk away before ever pressing F again). Drops
     * everything at the block once; no-ops when nothing is claimed there (including the common
     * case where a session's own {@link #stop} already handled it via its heartbeat's block-gone
     * check on the same or a following tick - no double drop, {@link ConcurrentHashMap#remove}
     * is the idempotency gate).
     *
     * <p>The display-entity despawn (design section 9, phase 2 leg G) happens BEFORE the
     * {@code isEmpty()} early-return, deliberately - a claim can legitimately hold a live
     * {@link StationCustodyClaim#displayRef()} with ZERO items left (a Consume step drained it to
     * empty mid-session, but the session had not yet stopped when the block broke), and this is
     * one of the two ONLY sites that remove a claim from {@link #custodyByBlock} (the other is
     * {@link #returnCustody}) - whichever of the two wins the removal race is the ONLY one that
     * ever sees this claim again, so it MUST be the one to despawn its display or the entity leaks.
     */
    void onCustodyBlockBroken(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull String blockKey, int x, int y, int z) {
        StationCustodyClaim claim = custodyByBlock.remove(blockKey);
        if (claim == null) {
            return;
        }
        StationCustodyDisplay.despawn(claim.displayRef(), commandBuffer);
        if (claim.isEmpty()) {
            return;
        }
        dropCustodyAtBlock(store, x, y, z, claim.toItemStacks());
    }

    /**
     * Drops {@code stacks} at the block's center via the shared {@link ItemDropUtil} sink
     * (SMOKE-FIX S3 (b) lifted this out to a mod-wide utility so {@code loot.LootEngine}'s luck/
     * tier grants reuse the SAME world-drop mechanism instead of re-deriving it).
     */
    private static void dropCustodyAtBlock(@Nullable Store<EntityStore> store, int x, int y, int z,
            @Nonnull List<ItemStack> stacks) {
        ItemDropUtil.dropAtBlock(store, x, y, z, stacks);
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

    /**
     * The diegetic action-selection choke point (design section 9.1, phase 2 leg E): resolves
     * {@code asset}'s effective action id against the player's CURRENTLY HELD active-hotbar stack
     * (item id, EVERY resolved resource-type family, native raw tags, and the functional route -
     * {@link #liveFunctionOf}). A single-action station (no {@code Actions} map) always resolves
     * {@link ActionResolver#ACTION_WORK} with zero live-item reads, byte-identical to phase 1.
     */
    @Nullable
    private static String selectActionForHeld(@Nonnull StationAsset asset, @Nonnull Player player) {
        if (asset.getActions() == null || asset.getActions().isEmpty()) {
            return ActionResolver.ACTION_WORK;
        }
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
        String heldItemId = held != null ? held.getItemId() : null;
        return ActionResolver.selectActionByFamily(asset, heldItemId, liveResourceTypeIdsOf(heldItemId),
                liveRawTagsOf(heldItemId), liveFunctionOf(heldItemId));
    }

    /**
     * The held item's FUNCTIONAL route (design 9.1's {@code ActionInput.Function}, phase 2 leg E -
     * previously schema-only, resolved for the first time here): {@code "Weapon"}/{@code "Armor"}/
     * {@code "Tool"} tested against the live {@link Item} shape (the {@code item/ItemEnhanceRoll}
     * gate precedent from the MMO's own item-enhancement package - re-derived independently here
     * since RpgStations has zero MMO dependency). {@code null} when unresolvable or none apply.
     */
    @Nullable
    private static String liveFunctionOf(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return null;
        }
        ItemWeapon weapon = item.getWeapon();
        if (weapon != null) {
            return "Weapon";
        }
        ItemArmor armor = item.getArmor();
        if (armor != null) {
            return "Armor";
        }
        return item.getTool() != null ? "Tool" : null;
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
     * The station's own ITEM id for the block at (x,y,z) - the fallback summary-crest icon when
     * a station authors no {@code Identity.Icon}. Captured at ENGAGE only.
     *
     * <p><b>R7 fix</b>: resolves through {@link BlockType#getItem()} (the block's containing Item
     * asset), NOT the raw {@link BlockType#getId()}. A custody-governed station (design 9.4) ONLY
     * engages after its materials are placed, which has already flipped the block to its
     * {@code Loaded}/{@code BarsPlaced}/{@code WeaponPlaced} state via
     * {@code setBlockInteractionState} - a state variant is a DISTINCT, generated-key
     * {@code BlockType} asset ({@code StateData#generateBlockKey}: {@code GENERATED_ID_PREFIX +
     * parentKey + "_" + stateName}, {@code GENERATED_ID_PREFIX = "*"}), so at engage the OLD
     * {@code blockType.getId()} returned e.g. {@code "*RPG_Station_Sawmill_Loaded"} - not a real
     * item id, so the crest's {@code new ItemStack(id, 1)} resolved the UNKNOWN placeholder
     * instead of the station's own icon. {@code getItem()} instead walks the asset's
     * container-key chain (confirmed against the shared source: a state variant decodes via
     * {@code ContainedAssetCodec.Mode.INJECT_PARENT}, so its {@code Data.containerData} is the
     * PARENT block's own {@code Data} - itself linked to the owning {@code Item} via the native
     * {@code Item.BlockType} field's {@code INHERIT_ID_AND_PARENT} containment, and
     * {@code Data#getContainerKey} recurses up that chain) and resolves the SAME base item id
     * regardless of which state variant is live. Falls back to the raw {@code blockType.getId()}
     * only when the block has no containing Item at all (a non-item-backed native block, the
     * pre-fix behavior for that edge case).
     */
    @Nullable
    private static String blockItemIdAt(@Nonnull World world, int x, int y, int z) {
        try {
            var blockType = world.getBlockType(x, y, z);
            if (blockType == null) {
                return null;
            }
            Item item = blockType.getItem();
            String id = item != null ? item.getId() : blockType.getId();
            return id != null && !id.isBlank() && !"Empty".equals(id) ? id : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * R5 fix: the block's CURRENTLY PERSISTED interaction-state name at (x,y,z) (e.g. custody's
     * own {@code "BarsPlaced"}/{@code "WeaponPlaced"}), or {@code null} when unreadable or the
     * block authors no state family at all. {@code world.getBlockType(x,y,z)} already returns the
     * block's CURRENT state variant (confirmed: {@code IChunkAccessorSync#getBlockType} reads the
     * live block id off the chunk, the same accessor {@link #flipCustodyState} writes through);
     * {@link BlockAccessor#getCurrentInteractionState} is the source-verified reverse lookup
     * ({@code blockType.getStateForBlock(blockType)}) from that live variant back to its state
     * NAME - the exact inverse of {@code BlockType#getBlockForState} (name -> variant), which
     * {@code flipCustodyState}/{@code setBlockInteractionState} already use to WRITE a state.
     */
    @Nullable
    private static String currentBlockStateName(@Nonnull World world, int x, int y, int z) {
        try {
            BlockType bt = world.getBlockType(x, y, z);
            return bt != null ? BlockAccessor.getCurrentInteractionState(bt) : null;
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

    /**
     * Native item/block display name as a client-resolved {@link Message}: the native {@code
     * server.items.<id>.name} key (vanilla/base-game items - most consumed/produced ledger rows,
     * e.g. the sawmill's logs/planks or the anvil's bars) FIRST, then the {@code items.<id>.name}
     * namespace this mod's/a pack's own {@code items.lang} loads under, else a prettified raw
     * fallback - {@code common.i18n.NativeNames}' shared two-tier probe (R1 fix: the previous
     * single-namespace {@code Msg.key("items." + itemId + ".name")} had no existence check and no
     * native-namespace fallback, so a native item resolved to an unregistered translation key the
     * client rendered as the raw key text).
     */
    @Nonnull
    private static Message itemNameMsg(@Nonnull String itemId) {
        return NativeNames.itemNameMsg(itemId);
    }

    /**
     * Round-5 refinement 3 (maintainer, 2026-07-22): a live, item-specific "what you gained"
     * notification - icon + client-resolved name, with the quantity riding the item-slot count
     * badge (round-7 D-4 - the value is now the bare item name so this reads EXACTLY like a native
     * pickup), routed through {@code ziggfreed-common}'s {@code feedback.Notify#itemKeyed} (the SAME
     * item-slot packet shape a native pickup uses; leg A's shared lift). Deliberately LIGHTER than
     * {@link #notifyNativePickup}/{@code PickupMimic}: no SFX and
     * no {@code ShowItemPickupNotifications} gate - this fires ambiently roughly once per work
     * cycle, not for a one-shot deliberate pickup action, so it skips the sound cue that primitive
     * layers on. {@code lucky=true} appends the ALREADY-9-locale {@code ui.station.summary.lucky}
     * suffix (DRY - the SAME {@code Msg.cat} composition {@link #ledgerRows} builds for the
     * end-of-session ledger row) and styles the whole line {@link #GOLD}. Called from both this
     * class ({@link #applyGrantResult}) and {@code StationStepHandlers.ProduceHandler} (same
     * package). Never throws.
     */
    static void notifyItemGain(@Nonnull PlayerRef playerRef, @Nonnull String itemId, int quantity, boolean lucky) {
        try {
            Message line = RpgMsg.tr("ui.station.gain.produced", itemNameMsg(itemId), quantity);
            if (lucky) {
                line = Msg.cat(line, RpgMsg.tr("ui.station.summary.lucky")).color(GOLD);
            }
            // D-4: the value is now the bare item name ({0}); the quantity rides the item-slot count
            // badge, matching a native pickup exactly (the unused quantity arg above is harmless).
            // Routed through the shared item-keyed helper (identical packet shape) - leg A's lift.
            Notify.itemKeyed(playerRef, line, null, itemId, quantity);
        } catch (Throwable t) {
            Log.fine("STATION item-gain notify failed: " + t.getMessage());
        }
    }

    /**
     * Round-5 refinement 2: mimics the ENGINE's own native item-pickup feedback (message + SFX +
     * item icon) once per retrieved stack, via {@code common.feedback.PickupMimic
     * #notifyLikeNativePickup} - which itself delegates STRAIGHT to the real {@code
     * Player#notifyPickupItem}, never a re-derived lookalike (scout findings 1-4). 3D-positioned
     * at the station block center (a world position IS known here) so the SFX plays from the
     * block, matching where the materials visually sat. Never throws.
     */
    private static void notifyNativePickup(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
            @Nonnull List<ItemStack> stacks, int blockX, int blockY, int blockZ) {
        if (stacks.isEmpty() || !ref.isValid()) {
            return;
        }
        Vector3d pos = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
        for (ItemStack stack : stacks) {
            try {
                PickupMimic.notifyLikeNativePickup(ref, store, stack, pos);
            } catch (Throwable t) {
                Log.fine("STATION retrieve pickup-mimic notify failed: " + t.getMessage());
            }
        }
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
            case RITUAL_COMPLETE -> "ui.station.stop.complete";
            case ENHANCE_CAPPED -> "ui.station.stop.capped";
            default -> null;
        };
    }

    /** Engine feature toggle, backed by {@code RpgStationsSettingsAsset.Enabled} (design section 4.6). */
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
            ItemStack held = InventoryAccess.activeHotbarItemOf(player);
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
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
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
