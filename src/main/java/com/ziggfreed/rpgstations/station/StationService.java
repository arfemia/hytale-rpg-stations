package com.ziggfreed.rpgstations.station;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
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
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.ziggfreed.common.camera.CameraShakeService;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.common.cast.WorldKeyedQueues;
import com.ziggfreed.common.cast.step.CastKernel;
import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.common.effect.AppliedEffectTracker;
import com.ziggfreed.common.effect.NativeEffectUtil;
import com.ziggfreed.common.entity.HeldItemUtil;
import com.ziggfreed.common.entity.PuppetNav;
import com.ziggfreed.common.entity.performer.PerformerReconciler;
import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.common.feedback.Notify;
import com.ziggfreed.common.feedback.PickupMimic;
import com.ziggfreed.common.i18n.Msg;
import com.ziggfreed.common.i18n.NativeNames;
import com.ziggfreed.common.interaction.NativeChainFire;
import com.ziggfreed.common.inventory.InventoryGrant;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.ui.rows.SummaryRow;
import com.ziggfreed.common.util.NumberFormatter;
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.api.SummaryContext;
import com.ziggfreed.rpgstations.api.SummaryDecorateContext;
import com.ziggfreed.rpgstations.api.SummaryEnricher;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;
import com.ziggfreed.rpgstations.api.impl.SummaryEnricherRegistryImpl;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.EffectRef;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Requires;
import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.interaction.StationUseInteraction;
import com.ziggfreed.rpgstations.loot.OutputItemResolver;
import com.ziggfreed.rpgstations.loot.StationLootEngine;
import com.ziggfreed.rpgstations.pages.PickerCategories;
import com.ziggfreed.rpgstations.pages.RpgStationPickerPage;
import com.ziggfreed.rpgstations.ui.StationSummaryHud;
import com.ziggfreed.rpgstations.util.InventoryAccess;
import com.ziggfreed.rpgstations.util.ItemDropUtil;
import com.ziggfreed.rpgstations.util.ItemGrantUtil;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Session lifecycle + work loop for interactive stations.
 *
 * <p><b>State machine:</b> {@code IDLE -> STARTING -> WORKING -> STOPPING -> IDLE}. One entry
 * point ({@link #toggle}, from {@code interaction.StationUseInteraction} on the world thread),
 * one idempotent exit funnel ({@link #stop}). Every start-denial is a localized toast, never
 * an interaction {@code Failed}.
 *
 * <p><b>What this class owns, and what it deliberately does not.</b> It owns the whole ENGINE
 * (session machine, toggle/stop funnel, heartbeat, the Convert cycle transaction, swing/impact
 * scheduling, idle mode, the {@link #emitMoment} presentation choke point, durability drain,
 * mount calls) plus the standalone reward layer (the conditional-lootable engine and the
 * self-sufficient summary panel - {@link #rollCompletionLoot}, {@link #showSessionSummary} over
 * {@code loot.LootEngine} / {@code ui.StationSummaryHud}, with the per-cycle Roll pass living in
 * {@code StationStepHandlers}' Roll phase). It owns NO progression: {@link #onCycleCompleted}
 * fires {@code StationCycleCompletedEvent} carrying the running action's authored contributions,
 * already pre-scaled, plus the multiplier that was applied for display; whichever mod declared a
 * channel decides what a post means. Extra summary rows and cross-jar theming arrive the same way,
 * through the api's {@code SummaryEnricherRegistry} union.
 */
public final class StationService {

    /** Heartbeat cadence (terminate checks + hold refresh); the hold TTL is 2.5x this. */
    private static final long HEARTBEAT_MS = 1000L;

    static final long DEFAULT_CYCLE_MS = 5000L;
    private static final long DEFAULT_MAX_DURATION_MS = 600_000L;
    private static final double DEFAULT_MAX_MOVE_METERS = 1.5;
    private static final String DEFAULT_HOLD_EFFECT = "RPG_Station_Hold";

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
         * {@code Work.Looping: false}'s "one completed program run completes the SESSION" (design
         * 9.3, phase 2 leg E - the anvil's Enhance ritual): a non-repeating action's program
         * completed successfully; the session stops right here, non-silent (a real completion,
         * not a denial).
         */
        RITUAL_COMPLETE,
        /**
         * A Stamp step's {@code Stats} leaf clamped its roll to nothing (design 9.5: "a
         * fully-capped item stamps nothing, consumes nothing, and denies with a keyed toast") -
         * every authored cap ({@code Caps.Budgets[]}/{@code Caps.PerStat}) is
         * already saturated for this item. No reagents were consumed.
         */
        ENHANCE_CAPPED,
        /**
         * Repeat-while-inputs completed naturally (scope-2 wave 3, design 2.4): a REPEATING program
         * ({@code Work.Looping: true}) whose {@code Consume} phase found insufficient inputs at its
         * claimed source ends GRACEFULLY here (the fish exemplar's "repeats while fish remain"). A
         * real completion, non-silent, summary shows totals - distinct from {@link #OUT_OF_INPUTS}
         * (a NON-repeating program's material shortage) only in its toast wording.
         */
        INPUTS_EXHAUSTED,
        /**
         * A remote anchor block this session claimed was broken mid-program (scope-2 wave 3, design
         * 2.6): {@link StationCustodyBreakSystem} resolves the owning session via the generalized
         * anchor map and stops it here; every OTHER claimed anchor's custody auto-returns, the
         * broken block's custody drops at that block, the in-flight iteration refunds. Non-silent.
         */
        ANCHOR_LOST,
        /**
         * A {@code Walk} phase could not solve a path to its anchor when the walk step began
         * (scope-2 wave 3, design 2.3/2.6): mid-program the terrain/obstacles changed so the puppet
         * can no longer reach the anchor. A graceful localized stop, never a wedged program.
         */
        PATH_BLOCKED
    }

    private static final StationService INSTANCE = new StationService();

    private final WorldKeyedQueues<StationSession> sessionsByWorld = new WorldKeyedQueues<>("rpgstations-work");

    /**
     * Cues waiting out their {@code Presentation.DelayMs}, one queue per world (see
     * {@link #emitMoment}). Deliberately NOT session-scoped: the completion moment is emitted from
     * inside {@link #stop}, so a queue that died with the session could never deliver a delayed
     * completion cue. Drained at the top of {@link #tickFrameOnce}, ahead of the session loop and
     * its empty-queue early return, so the last session of a world can still land its own farewell.
     * The partition self-registers a {@link WorldEvictors} evictor, so an unloaded world drops its
     * queue with everything else.
     */
    private final WorldKeyedQueues<PendingMoment> pendingMomentsByWorld =
            new WorldKeyedQueues<>("rpgstations-moment");

    /**
     * The live entry count of each world's delayed-cue queue, maintained alongside
     * {@link #pendingMomentsByWorld} so the capacity gate is O(1). A
     * {@code ConcurrentLinkedQueue}'s own {@code size()} is a full traversal, not a counter, and the
     * gate runs on every delayed emit - one counter keeps that read free.
     *
     * <p>It is also the KEY registry for the world-wide sweeps: {@code WorldKeyedQueues} exposes
     * values but not worlds, and an entry exists here for exactly the worlds that have ever parked a
     * cue, so {@link #dropPendingMoments} walks only those. Kept honest at both ends - every removal
     * path decrements, and {@link #drainPendingMoments} resets a world whose queue it just emptied,
     * so no drift can accumulate into a permanently "full" world.
     */
    private final ConcurrentHashMap<World, AtomicInteger> pendingMomentCounts = new ConcurrentHashMap<>();

    /**
     * How many delayed cues one world may hold at once. A cue is small and short-lived (the shipped
     * delays are tens of milliseconds against a drain that runs every tick), so the ceiling exists
     * purely to bound a pathological authoring - a whole server's worth of sessions each authoring a
     * delay longer than its own cycle. Past it a cue plays IMMEDIATELY rather than queueing: the
     * offset is the first thing worth losing, and dropping the cue outright would silence the
     * station instead.
     */
    static final int MAX_PENDING_MOMENTS_PER_WORLD = 256;

    /**
     * One cue waiting out its delay: the ALREADY-RESOLVED presentation (the flair fold ran at
     * emit time, so a re-fold mid-wait can never change what was scheduled), its session, its
     * moment id, the position it plays at, and the millisecond it comes due.
     *
     * <p>The session reference is held deliberately, and may be a STOPPED one: a delayed completion
     * cue outlives its session by construction. Everything the playback reads off it
     * ({@code playerRef}/{@code ref}/{@code playerUuid}/{@code stationId}) stays readable after
     * {@link #stop}.
     */
    private record PendingMoment(@Nonnull StationSession session, @Nonnull String momentId,
                                 @Nonnull Presentation presentation, @Nonnull Vector3d targetPos,
                                 long dueAtMs) {
    }
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
     * The live "this block is ACTIVELY BEING WORKED" block-state flip, one per player (a player runs
     * at most one session, and a session works at most one block at a time - a program's steps are
     * strictly sequential, so a second concurrent working block is unrepresentable by construction).
     * Written ONLY by {@link #enterWorkingState}/{@link #exitWorkingState}, never persisted -
     * a restart drops it, self-healed by the same next-interaction reset the Loaded state already
     * relies on. Empty for every station whose resolved {@code Custody.States.Working} is
     * unauthored, which is every pre-knob station.
     */
    private final ConcurrentHashMap<UUID, WorkingFlip> workingByPlayer = new ConcurrentHashMap<>();

    /**
     * One live Working flip: the block currently wearing its actively-working look, plus the anchor
     * id it was entered under (so the exit re-resolves the SAME {@code Custody} group that chose the
     * Working name) and its already-parsed coordinates (so the exit never re-parses a blockKey).
     */
    private record WorkingFlip(@Nonnull String blockKey, @Nullable String anchorId, int x, int y, int z) {
    }

    /**
     * The lazy per-block station index (scope-2 wave 3, gate m4, design 2.2): {@code blockKey ->
     * stationId} for every station block the engine has SEEN (an interaction warmed it, or a place
     * event registered it via the learned {@link #stationBlockItemToId} map). Anchor discovery
     * consults this FIRST (a cheap map scan), falling back to ONE bounded ring scan only when it
     * yields nothing. Populated opportunistically, removed on a block break; never persisted (a
     * freshly world-edited block is "seen" after any interaction, the honest documented contract).
     */
    private final ConcurrentHashMap<String, String> knownStationBlocks = new ConcurrentHashMap<>();

    /**
     * The {@code blockItemId(lowercased) -> stationId} map anchor discovery resolves an unseen block
     * against (the {@code PlaceBlockEvent} feed and the bounded ring scan both read it).
     *
     * <p>Filled from TWO sources. The AUTHORITATIVE one is {@link #seedStationBlockIndexFromAssets()},
     * which DERIVES the whole index from the native assets at every fold and once more post-load, so
     * a cold server (nobody has pressed F on anything yet) already knows every station block that
     * ships in any installed pack. The second is opportunistic LEARNING - the first interaction with
     * a given station block re-registers the same pair through
     * {@link #registerKnownStationBlock} - kept as harmless redundancy for a block whose
     * {@code Interactions.Use} the derivation could not walk (it re-writes an identical entry when
     * the derivation already covered it).
     */
    private final ConcurrentHashMap<String, String> stationBlockItemToId = new ConcurrentHashMap<>();

    /**
     * The multi-output picker's PENDING selection (selection wave, decision 50/56): {@code
     * playerUuid -> (blockKey, categoryId)}, written by the sneak+F picker's {@code onSelect}
     * callback and CONSUMED by the very next plain-F engage at the SAME block (the callback runs on
     * a page-event thread with no command buffer, so it cannot engage a session itself - it records
     * the choice, the player presses F to begin). Keyed by player so at most one pending choice
     * exists at a time; a choice for a DIFFERENT block than the one the player next engages is
     * ignored (blockKey mismatch) and overwritten by any newer selection. Consumed pending choices
     * become {@code StationSession.chosenOutputCategory}, which the conversion filter honors for the
     * whole session and which dies with the session ("cleared at stop"). Never persisted.
     */
    private final ConcurrentHashMap<UUID, PendingSelection> pendingByPlayer = new ConcurrentHashMap<>();

    /** One player's pending picker choice: the block it was made at + the chosen category id. */
    private record PendingSelection(@Nonnull String blockKey, @Nonnull String category) {
    }

    private StationService() {
        // The counter map shadows pendingMomentsByWorld, which self-registers its own evictor, so it
        // needs the matching registration to drop an unloaded world's entry at the same moment.
        WorldEvictors.registerEvictor(pendingMomentCounts::remove);
    }

    @Nonnull
    public static StationService getInstance() {
        return INSTANCE;
    }

    /** Called once by the drain system when it registers, so the no-drainer warning stays silent. */
    public void attachDrainer() {
        sessionsByWorld.markDrainerAttached();
        pendingMomentsByWorld.markDrainerAttached();
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
                       @Nonnull String stationId, int blockX, int blockY, int blockZ, boolean sneaking) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        StationSession existing = byPlayer.get(playerUuid);
        if (existing != null) {
            // A press of any kind while a session runs toggles it OFF (sneak included - crouch is
            // also the diegetic exit input, so a sneak+F over a running session ends it, never
            // re-opens a selection surface on top of it).
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
        String worldPrefix = StationAnchors.worldPrefix(String.valueOf(worldUuid));
        String blockKey = worldPrefix + blockX + ":" + blockY + ":" + blockZ;
        World world;
        try {
            world = WorldEvictors.worldOf(ref);
        } catch (Throwable t) {
            Log.warn("STATION could not resolve world for session start: " + t.getMessage());
            return;
        }

        // Warm the lazy station index (scope-2 wave 3, gate m4): this interaction "sees" the primary
        // block, so a later anchor discovery elsewhere finds it (and learns its block item id -> id
        // mapping for place-event indexing + the bounded ring scan).
        registerKnownStationBlock(blockKey, asset.getId(), blockItemIdAt(world, blockX, blockY, blockZ));

        // 2) Action selection - BEFORE Requires, so the RIGHT action's own gate is the one checked.
        // A loaded claim already owned by this player commits to ITS OWN action (re-pressing F with
        // a different item held must never switch a ritual already in progress mid-flight);
        // otherwise the station's ordered Actions list is walked front to back.
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

        // 2.5) Requires gate (Permission + factor Conditions), ANDed: the STATION's own entry gate
        // AND the selected action's own. Neither defaults the other - an action that authors none is
        // gated by the station's alone, and vice versa.
        if (!checkRequires(asset.getRequires(), playerRef, asset, action)
                || !checkRequires(action.getRequires(), playerRef, asset, action)) {
            toast(playerRef, RpgMsg.tr("ui.station.locked"));
            return;
        }

        // 2.6) Sneak+F selection surface (selection wave, decision 50/56, re-scoped by 64): an
        // EXPLICIT multi-output request that PRE-EMPTS the classic engage (never places into
        // custody, never starts work). Only fires on sneak, and only when the station derives 2+
        // output categories; otherwise (plain F, or a single-category station) it returns false and
        // the engage below runs unchanged. `preClaim` feeds decision 66's placed-material preview.
        if (sneaking && routeSneakSelection(store, ref, playerRef, player, playerUuid, asset, action,
                preClaim, blockX, blockY, blockZ)) {
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
                if (custodyAccepts(custody, asset, action, heldForPlacement, claim)) {
                    // Owner ceiling on how many stations may hold placed input at once in this
                    // world (Settings.Limits.MaxCustodyClaimsPerWorld). Checked HERE, at the
                    // placement itself, rather than at the top of the branch: only a press that
                    // actually OPENS a claim counts against the ceiling, so a press with nothing
                    // acceptable to place keeps its own honest denial and an idle-capable station
                    // still falls through to practice. Topping up a claim that already stands adds
                    // no bookkeeping and no prop entity, so it is never denied and a player is
                    // never locked out of material they already placed.
                    if (claim == null && atCustodyClaimCap(worldPrefix)) {
                        toast(playerRef, RpgMsg.tr("ui.station.storage_full"));
                        return;
                    }
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
                            hotbarComp.getActiveSlot(), claim);
                    if (found != null) {
                        // Same ceiling, same reason, at the backpack-sourced placement site.
                        if (claim == null && atCustodyClaimCap(worldPrefix)) {
                            toast(playerRef, RpgMsg.tr("ui.station.storage_full"));
                            return;
                        }
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
                if (!loadedBefore) {
                    // Decision 66 (round-3 smoke): the station is EMPTY and neither the held
                    // stack nor the rest of the inventory carries anything this custody
                    // accepts - deny with the honest reason NOW instead of falling through to
                    // the tool gate, whose "wrong tool" toast misled the smoke (a held
                    // Food_Fish_Raw at the cutting board read as a dagger problem when the
                    // real issue was an unacceptable material). A LOADED station still falls
                    // through (a denial further down really is about the tool), and an
                    // idle-capable classic station falls through too - empty-handed practice
                    // is a legitimate engage there.
                    boolean stepsAuthored = action.getSteps() != null && action.getSteps().length > 0;
                    StationAsset.Work workForIdle = action.getWork();
                    boolean idleCapable = !stepsAuthored && workForIdle != null && workForIdle.getIdle() != null;
                    if (!idleCapable) {
                        toast(playerRef, RpgMsg.tr("ui.station.no_materials"));
                        return;
                    }
                }
            }
            // Loaded but nothing more placed (topped out, or the held item does not match), or
            // an empty idle-capable station: fall through to engage below.
        }

        // 3) Exclusive occupancy - a property of the placed BLOCK, not of the job run at it.
        StationAsset.Work work = action.getWork();
        boolean exclusive = StationAsset.Block.effectiveExclusive(asset.getBlock());
        if (exclusive) {
            UUID occupant = byBlock.get(blockKey);
            if (occupant != null && !occupant.equals(playerUuid)) {
                toast(playerRef, RpgMsg.tr("ui.station.occupied"));
                return;
            }
        }

        // 4) Held-tool gate: the ACTION's own Tool is THE gate, checked once here and re-checked for
        // identity every heartbeat. A null gate means no tool is required.
        StationAsset.Tool toolGate = action.getTool();
        if (!heldToolMatches(player, toolGate)) {
            toast(playerRef, RpgMsg.tr("ui.station.wrong_tool"));
            return;
        }
        // 4b) Tool WEAR gate, orthogonal to the identity routes above and checked at ENGAGE only -
        // the per-heartbeat re-check stays about tool identity, so a session already running still
        // ends at breakage (TOOL_BROKEN) rather than at this threshold.
        if (toolGate != null && toolGate.hasDurabilityGate()
                && resolveHeldToolDurabilityPercent(player) < toolGate.getMinStartPercent()) {
            toast(playerRef, RpgMsg.tr("ui.station.tool_worn"));
            return;
        }

        // 5) Viability: a Steps-authored action (no Recipe/Convert check applies) is runnable
        // exactly when its OWN Custody governs and already holds something (an ungoverned Steps
        // action, no Custody authored, is always runnable - nothing gates its engagement); a classic
        // Recipe-driven action runs the conversion selection (custody-sourced when Custody governs),
        // or idle practice when the action opts in.
        boolean stepsProgram = action.getSteps() != null && action.getSteps().length > 0;
        // The program this session will actually run (authored steps plus any extension
        // insertions). Read raw one line above for the FLAVOR decision on purpose - an extension
        // may add beats to a program, never conjure one - and merged from here down, so the
        // engage-time derivations below see exactly the steps the dispatch will walk.
        List<StationStep> programSteps = effectiveProgramSteps(asset, action);
        StationAsset.Work.Idle idleGroup = work != null ? work.getIdle() : null;
        boolean idleEnabled = !stepsProgram && idleGroup != null && idleGroup.effectiveEnabled();
        // Selection wave (decision 56): the pending picker choice made (sneak+F) at THIS block
        // becomes the session's chosen output category. PEEKED, never consumed, until the engage
        // below actually commits - a denied press must leave the selection standing, or the player
        // silently loses the category they picked and the next plain F mills the default instead.
        // Null = no choice (the byte-identical all-categories behavior); a stale choice for another
        // block is ignored by peekPendingCategory.
        String chosenCategory = peekPendingCategory(playerUuid, blockKey);
        ConversionCheck check;
        if (stepsProgram) {
            boolean runnable = custody == null || (preClaim != null && !preClaim.isEmpty());
            check = new ConversionCheck(runnable ? ConversionState.RUNNABLE : ConversionState.NO_INPUTS);
        } else {
            check = selectConversion(asset, action, player, custody != null ? preClaim : null,
                    custody != null, chosenCategory);
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

        // 5b) Owner ceiling on concurrent sessions in this world
        // (Settings.Limits.MaxSessionsPerWorld). Placed LAST among the denials on purpose: a press
        // that only loads material into a station starts no session and returns above, so a busy
        // world never blocks placement, and every reason specific to THIS press (wrong tool, no
        // materials, no room) is reported first rather than being masked by a server-wide one. It
        // also sits before the anchor claims below, so a denial here has nothing to roll back.
        // Unlimited by default: the check costs one null read until an owner sets it.
        if (atSessionCap(world)) {
            toast(playerRef, RpgMsg.tr("ui.station.server_busy"));
            return;
        }

        // 6) Engage.
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d pos = transform.getPosition();

        // 6.1) Multi-station anchors (scope-2 wave 3, design 2.2): resolve + CLAIM every declared
        // anchor atomically (first-wins), self included. A deny leaves ZERO claims
        // (resolveAndClaimAnchors rolls back its own partial claims on every deny path); the only
        // engage return AFTER this point (seat-mount failure) releases them explicitly.
        AnchorResolution anchorRes = resolveAndClaimAnchors(world, worldUuid, playerUuid, action, programSteps,
                transform, store, blockX, blockY, blockZ, blockKey);
        if (anchorRes.denied()) {
            toast(playerRef, anchorRes.denyToast);
            return;
        }
        Map<String, String> claimedAnchorBlocks = anchorRes.anchorBlocks;

        StationSession s = new StationSession();
        s.playerUuid = playerUuid;
        s.ref = ref;
        s.playerRef = playerRef;
        s.stationId = asset.getId();
        s.actionId = action.getActionId();
        s.chosenOutputCategory = chosenCategory;
        s.blockKey = blockKey;
        s.blockX = blockX;
        s.blockY = blockY;
        s.blockZ = blockZ;
        if (claimedAnchorBlocks != null) {
            s.anchorBlocks.putAll(claimedAnchorBlocks);
        }
        s.startBlockId = blockIdAt(world, blockX, blockY, blockZ);
        // The block-gone comparand (see StationAnchors#blockGone): the block's ITEM id, resolved
        // ONCE here and reused for the summary crest below - the raw id above is only the fallback
        // for a block with no containing Item at all.
        s.startBlockItemId = blockItemIdAt(world, blockX, blockY, blockZ);
        StationAsset.Identity identity = asset.getIdentity();
        String authoredIcon = identity != null ? identity.getIcon() : null;
        s.stationIconItemId = authoredIcon != null && !authoredIcon.isBlank()
                ? authoredIcon : s.startBlockItemId;
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
            // Release the anchor claims taken in 6.1 - this is the ONLY engage return after them.
            if (claimedAnchorBlocks != null) {
                for (Map.Entry<String, String> e : claimedAnchorBlocks.entrySet()) {
                    if (!ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(e.getKey())
                            && !e.getValue().equals(blockKey)) {
                        byBlock.remove(e.getValue(), playerUuid);
                    }
                }
            }
            toast(playerRef, RpgMsg.tr("ui.station.seat_unavailable"));
            return;
        }
        // Past the LAST engage denial: the session is committed, so the picker choice this engage
        // used is now genuinely spent and can be cleared. Every earlier return above leaves it
        // standing for the player's next press.
        if (chosenCategory != null) {
            clearPendingCategory(playerUuid, blockKey);
        }

        StationAsset.Hold.Mount.Entity entityGroup = s.entityMountMode ? mountGroup.getEntity() : null;
        s.entitySteerable = entityGroup != null && entityGroup.effectiveSteerable();
        s.entityDismountOnMove = entityGroup == null || entityGroup.effectiveDismountOnMove();
        if (s.entityMountMode) {
            // Steerable (default false) applies the hold effect + heartbeat snap-back to defeat
            // the native WASD-steers-the-anchor behavior (design 9.2); Steerable true skips both,
            // reserved for a future vehicle-like station.
            Ref<EntityStore> anchorRef = StationEntityMountController.spawnAnchor(commandBuffer, blockX, blockY, blockZ, entityGroup);
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
        // Round-8 step-synced swings: an authored Steps program whose steps author their own
        // Puppet.Clip drives the puppet ENTIRELY from those per-step-entry clips, so the generic
        // engage/swing puppet clip is suppressed for it (never double-fired on top). A non-stepped
        // session, or a stepped program with no step clips, keeps its one generic engage swing.
        s.stepProgramAuthorsClip = stepsProgram
                && StationStepDecisions.programAuthorsAnyStepClip(programSteps);

        // Puppet presentation (round-4 design, doc section 4.2): spawn + hide AFTER the mount
        // attach above - the puppet layers on WHATEVER hold/mount the real player already has
        // (seat, standing mount, or effect-mode movement lock), never replacing it. Non-fatal on
        // failure: s.puppetActive stays false and the session continues in-body.
        // Owner ceiling on live puppets in this world (Settings.Limits.MaxPuppetsPerWorld): past it
        // the session starts and runs exactly as normal, it just performs in the player's own body -
        // the same degradation a failed spawn already takes, so no engage is ever denied over
        // presentation.
        if (atPuppetCap(world)) {
            Log.fine("STATION puppet ceiling reached in world " + world.getName()
                    + " - this session performs in-body");
        } else {
            StationPuppetController.spawnAndHide(s, commandBuffer, world, action.getPuppet(), player);
        }
        // Performer orphan-reconcile at engage (seam wave decision 48/55): despawn any stale double
        // left AT THIS block by a prior crashed session (a persistent performer whose owner is not
        // the engaging player). Deferred one tick via world.execute so the native performer sweep
        // (forEachEntityParallel) runs OUTSIDE toggle's write-processing lock; the freshly spawned
        // own double is owned by the engaging player, so engageStale KEEPs it. Inert when no
        // performer identity component is registered or no orphan exists.
        reconcileStalePerformersAtEngage(world, store, s.playerUuid, s.blockKey);

        StationAsset.Camera camera = action.getCamera();
        boolean mountDefaultNoCamera = mounted && camera == null;
        s.cameraApplied = !mountDefaultNoCamera && (camera == null || camera.effectiveEnabled());
        s.cameraLocked = camera == null || camera.getLocked() == null || camera.getLocked();
        // Authoring Camera.Recipe at all IS the fixed-look opt-in: the preset names which
        // ServerCameraSettings combination applies, so a second boolean gating it could only ever
        // make an authored preset silently inert.
        s.faceBlock = s.cameraApplied && camera != null && camera.hasRecipe();
        s.cameraRecipe = camera != null ? camera.getRecipe() : null;
        // The action's own gate drives the heartbeat identity re-check and the wear drain.
        s.toolReq = toolGate;

        StationAsset.Tool.Durability durability = s.toolReq != null ? s.toolReq.getDurability() : null;
        s.durabilityPerSwing = StationToolScaling.resolvedDurabilityAmount(
                durability != null ? durability.getPerSwing() : null);
        s.durabilityPerCycle = StationToolScaling.resolvedDurabilityAmount(
                durability != null ? durability.getPerCycle() : null);

        StationAsset.Animation.Swing swing = animation != null ? animation.getSwing() : null;
        s.swingIntervalMs = swing != null && swing.getIntervalMs() != null && swing.getIntervalMs() > 0
                ? swing.getIntervalMs() : 0L;
        // The action's whole moment vocabulary, snapshotted once: every cue this session plays that
        // the engine has no more specific presentation for resolves against THIS map, so a mid-session
        // catalog re-fold can never swap a moment out from under a running run.
        s.moments = action.getMoments();

        s.idleEnabled = idleEnabled;
        s.idleCycleMs = StationToolScaling.resolvedIdleCycleMs(
                idleGroup != null ? idleGroup.getCycleMs() : null, s.cycleMs);
        s.idleFraction = StationToolScaling.resolvedIdleFraction(
                idleGroup != null ? idleGroup.getFraction() : null);
        s.idleMode = startIdle;

        long now = System.currentTimeMillis();
        s.startedAtMs = now;
        s.nextHeartbeatAtMs = now + HEARTBEAT_MS;
        // Instant dispatch for a non-repeating authored Steps program (maintainer-approved,
        // round-7 D77): a ritual-shaped action (Work.Looping: false, e.g. the anvil's Enhance)
        // has exactly ONE program run to make, so waiting a full CycleMs before ever attempting
        // it is pure latency with no gameplay purpose - fire the first (and only) cycle
        // immediately at engage. A REPEATING program (the sawmill's classic loop, or any
        // Looping: true steps program) keeps the existing CycleMs pre-delay unchanged; idle mode
        // never applies to a Steps program (idleEnabled is forced false above), so this never
        // races s.idleMode's own cadence.
        boolean instantFirstDispatch = stepsProgram && work != null && !work.effectiveLooping();
        s.nextCycleAtMs = instantFirstDispatch ? now : now + (s.idleMode ? s.idleCycleMs : s.cycleMs);
        s.nextSwingAtMs = now + s.swingIntervalMs;

        byPlayer.put(playerUuid, s);
        if (exclusive) {
            byBlock.put(blockKey, playerUuid);
        }
        sessionsByWorld.queueFor(world).offer(s);

        // Actively-working block state (Custody.States.Working) for the CLASSIC convert loop: an
        // implicit-program session has no authored step to light its block on entry, and its first
        // conversion only commits a full CycleMs from now - but that whole CycleMs IS the work (the
        // cooking fire's 2500ms cook time), so the block must burn from engage rather than a cycle
        // late. An authored Steps program deliberately does NOT light here (its first step is a
        // load/walk beat, not work - it lights per-step instead), and idle practice mode converts
        // nothing so it never lights. No-op unless the resolved Custody authors a Working name.
        if (!stepsProgram && !s.idleMode) {
            enterWorkingState(s, null);
        }

        StationHoldController.applyHold(s, store);
        StationHoldController.applyCamera(s);
        // Puppet presentation (design 4.3): supersedes the seatMode-gated real-player emote
        // entirely - the puppet has no sit pose to fight, so it always plays its own loop clip
        // regardless of which mount/hold the (now possibly hidden) real player has. Round-8: the
        // generic engage loop is SUPPRESSED for a stepped program whose steps author their own
        // Puppet.Clip (s.stepProgramAuthorsClip) - the per-step-entry clips are the sole animation
        // driver there, so an engage loop would double-fire on top of the first step's entry clip.
        if (s.puppetActive) {
            if (!s.stepProgramAuthorsClip) {
                StationPuppetController.playLoop(s, store, player);
            }
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
        // Delayed presentation cues FIRST, and outside the session-empty early return below: a
        // completion moment is emitted from inside stop(), so its cue routinely outlives both its
        // own session and, when it was the world's last one, the whole session queue.
        drainPendingMoments(world, store);
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
                // F2 part (a): keep s.puppetRef pointed at the performer's live ref every frame -
                // covers the NpcRole backend's one-tick deferred-spawn window (see
                // StationPuppetController#refreshPuppetRef). Runs upstream of the heartbeat AND the
                // step-frame drive below, so both see a fresh ref.
                StationPuppetController.refreshPuppetRef(s);
                // ... and, for a puppet authoring a Pitch/Roll tilt, apply it on the first frame
                // that has a ref to tilt (a no-op for every untilted session).
                StationPuppetController.applyPendingTilt(s, commandBuffer);
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
                    runSwing(s, store, commandBuffer);
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
        // Block-gone by ITEM id, not by raw block id: this engine flips the primary block's own
        // interaction state mid-session (Custody.States Empty/Loaded/Working), and a state flip
        // REPLACES the block with its generated state variant, so a raw-int compare would read the
        // engine's own flip as "the station is gone". See StationAnchors#blockGone.
        if (StationAnchors.blockGone(s.startBlockItemId, blockItemIdAt(world, s.blockX, s.blockY, s.blockZ),
                s.startBlockId, blockIdAt(world, s.blockX, s.blockY, s.blockZ))) {
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
        // The SAME conversion selection the engage check ran, re-resolved every cycle because
        // materials run out mid-session and the session's chosen output category still narrows the
        // recipe's derived conversions.
        ConversionCheck check = selectConversion(asset, action, player,
                custody != null ? custodyByBlock.get(s.blockKey) : null, custody != null,
                s.chosenOutputCategory);
        if (check.state == ConversionState.RUNNABLE) {
            if (s.idleMode) {
                s.idleMode = false;
                // Adversarial-verify F2 (decision 61a): materials arrived mid-session, the
                // classic loop is genuinely working again - relight the primary block
                // (idempotent; a no-Working station no-ops).
                enterWorkingState(s, null);
            }
            // Per-conversion pace precedence (seam wave decision 52): a RUNNABLE conversion that
            // authors its own DurationMs (or a baked FromCrafting.NativeTime transform) OVERRIDES
            // the heartbeat's Work.CycleMs advance for the NEXT cycle. A conversion with no pace
            // override (check.durationMs <= 0, e.g. the shipped sawmill) leaves the cycleMs advance
            // untouched - byte-identical to before.
            if (check.durationMs > 0) {
                s.nextCycleAtMs = System.currentTimeMillis() + check.durationMs;
            }
            return runRealCycle(s, store, commandBuffer, asset, action, player, check);
        } else if (check.state == ConversionState.NO_INPUTS && s.idleEnabled) {
            if (!s.idleMode) {
                s.idleMode = true;
                toast(s.playerRef, RpgMsg.tr("ui.station.practice"));
                // Adversarial-verify F2 (decision 61a): idle practice converts nothing, so the
                // working look goes out (idempotent) - "off in every other state".
                exitWorkingState(s);
            }
            s.nextCycleAtMs = System.currentTimeMillis() + s.idleCycleMs;
            return runIdleCycle(s, store, commandBuffer, action, player);
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
        // Decision 73: the chosen conversion's FULL Ingredient arrays drive the implicit program, so
        // a multi-input recipe stays ONE atomic Consume/Produce step pair.
        StationStep.Consume consumeStep = StationStep.Consume.of(check.inputs, consumeFrom);
        // Recipe.Yield: the per-cycle output-quantity transform (StationYield), DETERMINISTIC end to
        // end. ONE FactorSnapshot still serves the whole cycle (the Bonus rolls below and any Stamp
        // phase read it), so two ladders reading the same factor can never disagree.
        StationAsset.Yield yield = check.recipe != null ? check.recipe.getYield() : null;
        FactorLookup snapshot = FactorRegistryImpl.getInstance().snapshotFor(
                buildFactorContext(s, store, player, action, attemptCycleIndex));
        Ingredient[] yieldedOutputs = StationYield.applyToOutputs(yield, check.outputs);
        recordYieldBreakdown(s, check.outputs, yieldedOutputs);
        // A Bonus roll's Grants.OutputItems adds EXTRA items of this cycle's own primary output; the
        // roll phase reports the fractional tally and applyGrantResult resolves it to whole items of
        // this id, once for the whole cycle.
        Ingredient primaryOutput = yieldedOutputs.length > 0 ? yieldedOutputs[0] : null;
        s.cycleOutputItemId = primaryOutput != null ? primaryOutput.getItemId() : null;
        StationStep.Produce produceStep = StationStep.Produce.of(yieldedOutputs,
                StationStep.Produce.TO_INVENTORY);
        // The action's effective Bonus rolls ride the implicit program's own Roll phase, so this
        // route resolves them at BUILD time rather than at completion.
        Roll[] resolvedRolls = effectiveBonusRolls(asset, action).toArray(new Roll[0]);
        List<StationStep> steps = ImplicitProgram.build(consumeStep, produceStep, resolvedRolls,
                action.getPresentation());
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps,
                attemptCycleIndex, 0, false, snapshot, false);
    }

    /**
     * An AUTHORED {@code Steps} program cycle attempt (design 9.3/9.5, phase 2 leg E): unlike
     * {@link #runRealCycle}, there is no live {@code ConversionCheck} - the program's own steps
     * (Consume/Produce/Roll/Stamp/...) validate and mutate whatever they individually need
     * (custody, inventory, reagents).
     */
    private boolean runAuthoredProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player) {
        List<StationStep> steps = effectiveProgramSteps(asset, action);
        int attemptCycleIndex = s.cyclesDone + 1;
        // An authored program has no single "cycle output", so an OutputItems grant has nothing to
        // add to and is dropped (LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT flags the authoring).
        s.cycleOutputItemId = null;
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps, attemptCycleIndex, 0, false);
    }

    /**
     * The program an action ACTUALLY runs: its own authored {@code Steps}, plus any insertions an
     * {@code Action}-targeted {@code ExtensionAsset} contributes, in apply order. Empty for an
     * action that authors no program at all.
     *
     * <p>The merge lives HERE, at the read, rather than on {@code ResolvedAction.getSteps()} - see
     * that accessor's javadoc for why. The rule this placement buys: an insertion can only ADD
     * beats to a program the action already authors, never conjure one, so which engine an action
     * runs (authored program versus the recipe-driven convert loop) stays entirely base-owned.
     * Every read of "the program this session will run" routes through here so the engage-time
     * derivations (walk-anchor reachability, whether the program drives its own puppet clips) and
     * the dispatch itself can never disagree about which steps exist.
     */
    @Nonnull
    static List<StationStep> effectiveProgramSteps(@Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action) {
        StationStep[] authored = action.getSteps();
        if (authored == null || authored.length == 0) {
            return List.of();
        }
        List<StationStep> base = Arrays.asList(authored);
        String target = ActionResolver.actionTargetId(asset, action.getActionId());
        return target != null
                ? ExtensionCatalog.getInstance().applyToActionSteps(asset.getId(), target, base) : base;
    }

    /**
     * The action's EFFECTIVE {@code Bonus} rolls: its own group, plus every matching
     * {@code ExtensionAsset}'s appended lootables and inline rolls, expanded through
     * {@link StationLootEngine#resolveRolls}. The ONE resolution all THREE Bonus read sites share - the
     * implicit convert cycle (which folds them into its program's own Roll phase), an authored
     * program's completed pass, and the session's Completion pass - so no route can ever see a
     * different effective Bonus than another for the same action.
     *
     * <p>Trigger filtering is deliberately NOT done here: {@link StationLootEngine#rollAndGrant} takes the
     * trigger and skips every roll that does not carry it, so one resolution serves the
     * {@code Cycle} and {@code Completion} passes alike.
     */
    @Nonnull
    static List<Roll> effectiveBonusRolls(@Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action) {
        LootRef bonus = action.getBonus();
        String target = ActionResolver.actionTargetId(asset, action.getActionId());
        if (target != null) {
            bonus = ExtensionCatalog.getInstance().applyToActionBonus(asset.getId(), target, bonus);
        }
        return StationLootEngine.resolveRolls(bonus);
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
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps,
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
     * early.
     *
     * <p>{@code Work.Looping: false} (design 9.3's "one completed program run completes the
     * SESSION" - the anvil's Enhance ritual): a COMPLETED program under a non-repeating action
     * stops the session right here, non-silent, immediately after the cycle-completed event fires
     * (so every listener still sees the last cycle) - never schedules another cycle attempt.
     *
     * <p><b>The {@code resuming} flag + the stepDeadlineMs hardening (round-7):</b> {@code false}
     * from {@link #runRealCycle}/{@link #runAuthoredProgram} (a FRESH cycle attempt,
     * {@code startIndex} always 0), {@code true} from {@link #resumeCycleProgram} (re-entering a
     * previously suspended program at {@code s.programIndex}, which can itself legally be 0 - so
     * {@code startIndex == 0} alone can NEVER distinguish a fresh dispatch from a resume, this
     * explicit flag is the only reliable signal). A FRESH dispatch EXPLICITLY resets
     * {@code s.stepDeadlineMs} to 0 before the walk starts - this used to be an IMPLICIT invariant
     * only (every {@code Wait} step resets its own committed deadline to 0 the instant it succeeds,
     * so a fresh dispatch "should" already read 0 by construction), made explicit so a fresh
     * program's very first {@code Wait} step can never inherit a stale nonzero value from anywhere.
     * Also feeds {@link StationStepContext#resumingStep} (the step object at {@code startIndex}
     * when {@code resuming} is true, else {@code null}) - {@code StationStepRegistry}'s generic
     * per-step Presentation entry AND the round-8 per-step puppet clip read it to skip the
     * suspend-resume RE-CHECK of an already-played step.
     */
    private boolean dispatchProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player, @Nonnull List<StationStep> steps,
            int attemptCycleIndex, int startIndex, boolean resuming) {
        // The AUTHORED-program entry (a fresh pass and a resume alike): such a program carries no
        // implicit Roll phase for the action's own Bonus, so its Cycle-trigger pass runs at
        // completion instead - see rollCycleBonus.
        return dispatchProgram(s, store, commandBuffer, asset, action, player, steps,
                attemptCycleIndex, startIndex, resuming, null, true);
    }

    /**
     * As above, with an optional PRE-BUILT {@code presetSnapshot} the caller already resolved
     * factors against. {@link #runRealCycle} passes its own so the {@code Recipe.Yield} transform and
     * this cycle's loot rolls read the IDENTICAL resolved factor numbers - the "one aggregation, two
     * consumers" invariant {@code FactorSnapshot} exists for, which a second snapshot per cycle would
     * quietly break (a factor is free to vary between two resolutions within one cycle).
     *
     * <p>{@code bonusAtCompletion} says WHERE this dispatch's action-level {@code Bonus} pass
     * happens, which is the ONE thing the two program shapes genuinely differ on: the implicit
     * convert program embeds the action's rolls as its own Roll phase and passes {@code false} (a
     * second pass here would double every cycle's loot), while an authored program has no such
     * phase and passes {@code true} so its completed pass still resolves the SAME
     * {@code Cycle}-trigger moment. Either way the moment is "this action completed a cycle", fired
     * exactly once per completed pass.
     */
    private boolean dispatchProgram(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player, @Nonnull List<StationStep> steps,
            int attemptCycleIndex, int startIndex, boolean resuming,
            @Nullable FactorLookup presetSnapshot, boolean bonusAtCompletion) {
        if (!resuming) {
            // A FRESH cycle attempt explicitly zeroes the suspend deadline AND the per-step
            // iteration counter before the walk starts, so a fresh program's very first Duration
            // hold / Repeat step can never inherit a stale nonzero value (an explicit guarantee on
            // top of every step's own success-path reset).
            s.stepDeadlineMs = 0L;
            s.stepIteration = 0;
        }
        FactorLookup snapshot = presetSnapshot != null ? presetSnapshot
                : FactorRegistryImpl.getInstance().snapshotFor(
                        buildFactorContext(s, store, player, action, attemptCycleIndex));
        StationStepContext ctx = new StationStepContext(s, store, commandBuffer, player, action, snapshot,
                steps, attemptCycleIndex);

        CastKernel.Walk<StationStepResult> walk = StationStepKernel.runResumable(ctx, startIndex);
        if (walk instanceof CastKernel.Walk.Suspended<StationStepResult> suspended) {
            s.programSuspended = true;
            s.programIndex = suspended.resumeIndex();
            s.activeProgramSteps = steps;
            s.activeProgramCycleIndex = attemptCycleIndex;
            return true;
        }
        s.programSuspended = false;
        s.programIndex = 0;
        s.activeProgramSteps = null;
        if (walk instanceof CastKernel.Walk.Failed<StationStepResult> failed) {
            StationStepResult.Fail fail = (StationStepResult.Fail) failed.result();
            Log.warn("STATION step program failed for '" + s.stationId + "' at step index "
                    + failed.atIndex() + ": " + fail.message());
            stop(s, fail.reason(), store, commandBuffer);
            return false;
        }

        s.cyclesDone++;
        // Iteration refund ledger (design 2.5/M1): a COMPLETED program cycle committed every output
        // (to inventory or custody), so nothing is owed - clear the ledger so a stop between cycles
        // refunds nothing. A mid-cycle stop still refunds whatever was recorded before the commit.
        s.iterationConsumed.clear();
        if (s.durabilityPerCycle > 0) {
            drainHeldToolDurability(store, s.ref, player, s.durabilityPerCycle);
        }
        // An authored program's Cycle-trigger Bonus pass, against the snapshot THIS dispatch pass
        // ran on. On a fresh single-pass program that IS the snapshot the whole walk used; on a
        // RESUMED one it is a fresh snapshot this pass built (the 8-arg overload passes no preset),
        // so a factor that moves while the program is suspended reads differently here than it did
        // for the legs before the hold - accepted, not papered over. BEFORE the cycle-completed
        // event, so a granted Roll.Grants.Contributions rides THIS cycle's event exactly as it does
        // on the implicit route (where the Roll phase ran inside the walk that just finished).
        if (bonusAtCompletion) {
            rollCycleBonus(s, store, commandBuffer, asset, action, player, snapshot, attemptCycleIndex);
        }
        // The action's ContributionScale ladder, resolved against the SAME snapshot this dispatch
        // pass already built - one factor aggregation, every consumer.
        double scale = ContributionScaling.multiplier(action.getContributionScale(), snapshot::resolve);
        onCycleCompleted(s, store, commandBuffer, action, false, s.cyclesDone, scale);

        StationAsset.Work work = action.getWork();
        if (work != null && !work.effectiveLooping()) {
            stop(s, StopReason.RITUAL_COMPLETE, store, commandBuffer);
            return false;
        }
        return true;
    }

    /**
     * Records ONE real cycle's DETERMINISTIC yield per produced item, for the summary panel's
     * per-produced-row breakdown line ({@link StationSession.YieldBreakdown}). The bonus half of that
     * line is filled in separately, by whatever {@code Roll.Grants.OutputItems} the cycle's Bonus
     * rolls hand over ({@link StationSession.YieldBreakdown#addBonus}).
     *
     * <p>It records {@code changed = false} for a cycle whose yield did not move the number - a
     * starter tool producing exactly the conversion's own quantity leaves the breakdown line
     * hidden. A recipe with NO {@code Yield} group is still recorded (every cycle contributing
     * {@code changed = false}), because {@code Yield} is not the only thing that can move the
     * number: a Bonus roll's {@code Grants.OutputItems} adds bonus items to a recipe that authors
     * no {@code Yield} at all, and skipping the record here left {@link StationSession.YieldBreakdown
     * #addBonus} with no entry to fill - so exactly the case the breakdown line exists to explain
     * ("your tool is earning you extra") was the one case it never rendered.
     */
    static void recordYieldBreakdown(@Nonnull StationSession s, @Nullable Ingredient[] authored,
            @Nonnull Ingredient[] produced) {
        if (authored == null) {
            return;
        }
        for (int i = 0; i < produced.length && i < authored.length; i++) {
            Ingredient out = produced[i];
            Ingredient src = authored[i];
            if (out == null || src == null || out.getItemId() == null || out.getItemId().isBlank()) {
                continue;
            }
            int quantity = out.effectiveQuantity();
            s.producedYield.computeIfAbsent(out.getItemId(), k -> new StationSession.YieldBreakdown())
                    .add(quantity, quantity != src.effectiveQuantity());
        }
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
        if (!mergeConsumedSlots(tally, consumedSlots)) {
            tally.merge(resourceTypeId, 1, Integer::sum);
        }
    }

    /**
     * PURE: merge each usable {@code (itemId, consumed)} slot into {@code tally} (summing per item
     * id); returns {@code true} when at least one slot merged. The shared fold every consume-ledger
     * path routes through - the {@code ResourceTypeId} family route ({@link #tallyConsumedResource},
     * which adds the raw-type fallback on an empty result) AND the Stamp reagent route
     * ({@link #tallyConsumedStacks}, whose reagents always carry concrete drained ids, so it needs
     * no fallback). ONE consumed-ledger authority, no parallel bookkeeping.
     */
    static boolean mergeConsumedSlots(@Nonnull Map<String, Integer> tally, @Nonnull List<ConsumedSlot> consumedSlots) {
        boolean any = false;
        for (ConsumedSlot slot : consumedSlots) {
            if (slot == null || slot.itemId == null || slot.consumed <= 0) {
                continue;
            }
            tally.merge(slot.itemId, slot.consumed, Integer::sum);
            any = true;
        }
        return any;
    }

    /**
     * Consume-ledger tally for the Stamp step's committed reagents ({@code StationStepHandlers
     * .StampHandler}): the anvil's Enhance ritual drains its sharpened bars via {@code Stamp
     * .Reagents} (never a {@code Consume} step), so without this the end-of-session summary showed
     * the enhancement stat / durability rows but NO consumed row for the reagents the ritual ate.
     * Routes the REAL drained stacks ({@code consumeReagent}'s per-slot reads, already computed for
     * restore-on-failure) into the SAME {@code s.consumedItems} ledger {@link #tallyResourceConsumption}
     * / the implicit-program {@code Consume} step feed, so {@link #ledgerRows} renders one
     * {@code SummaryRow.Kind.CONSUMED} row per input stack through the existing pipeline.
     */
    static void tallyConsumedStacks(@Nonnull StationSession s, @Nonnull List<ItemStack> stacks) {
        List<ConsumedSlot> slots = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItemId() != null && stack.getQuantity() > 0) {
                slots.add(new ConsumedSlot(stack.getItemId(), stack.getQuantity()));
            }
        }
        mergeConsumedSlots(s.consumedItems, slots);
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
     * Opt-in idle practice cycle: NO conversion, NO loot roll - just the cycle presentation plus
     * the (idle-scaled) contribution forwarding. Threads the RESOLVED action
     * (FIX ROUND, the same correction as the real-cycle path in {@link #dispatchProgram}) so a
     * multi-action station's idle practice reads ITS running action's {@code Work}/
     * {@code Presentation}, not the station-level default.
     */
    private boolean runIdleCycle(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                 @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player) {
        // An idle cycle scales the same way a real one does, off its own fresh snapshot (no cycle
        // program ran to build one): amount x idleFraction x contributionScale.
        FactorLookup snapshot = FactorRegistryImpl.getInstance().snapshotFor(
                buildFactorContext(s, store, player, action, s.cyclesDone + 1));
        double scale = ContributionScaling.multiplier(action.getContributionScale(), snapshot::resolve);
        onCycleCompleted(s, store, commandBuffer, action, true, s.cyclesDone, scale);

        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.MOMENT_CYCLE, action.getPresentation(), blockPos);
        s.cyclesDone++;
        return true;
    }

    /**
     * Fires {@code StationCycleCompletedEvent}: forwards this cycle's
     * {@code Work.PerCycleContributions}, PRE-SCALED, plus the multiplier that was applied. Whichever
     * mod declared a channel decides what a post on it means; this engine never interprets one.
     *
     * <p><b>Scaling order, applied here and documented on the event:</b>
     * {@code amount x Work.Idle.Fraction (idle cycles only) x contributionScale}. The engine
     * pre-scales so a listener cannot forget to multiply (under-award) or multiply again
     * (over-award); {@code contributionScale} rides the event for DISPLAY only. A one-shot
     * {@code Roll.Grants.Contributions} find bypasses BOTH and rides its own list verbatim.
     *
     * <p>{@code commandBuffer} is GUARANTEED non-null here: both call sites (the real-cycle path
     * in {@link #runRealCycle} and the idle-cycle path in {@link #runIdleCycle}) run inside the
     * per-world frame drain ({@link #tickFrameOnce}), which always holds a live {@code
     * CommandBuffer} for the current tick (critique fix, binding - see the event's own javadoc).
     */
    private static void onCycleCompleted(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull ActionResolver.ResolvedAction action,
            boolean idle, int cycleIndex, double contributionScale) {
        // Fold any Action-targeted ExtensionAsset contributions into the forwarded list so an
        // extension authoring a genuinely-new one reaches a listener exactly as if it were authored
        // inline on Work.PerCycleContributions. Append-only over the base, never a re-add of a
        // (Channel, Param) pair the base already posts (that would double it - the validator warns).
        Contribution[] merged = action.getWork() != null ? action.getWork().getPerCycleContributions() : null;
        String actionTarget = actionTargetIdFor(s, action.getActionId());
        if (actionTarget != null) {
            merged = ExtensionCatalog.getInstance().applyToActionContributions(s.stationId, actionTarget, merged);
        }
        List<StationContribution> perCycle = contributionsFrom(merged, idle, s.idleFraction, contributionScale);
        // A one-shot Roll.Grants.Contributions find rides its OWN list, never the scaled one - it
        // must not pick up the idle fraction or the contribution scale (both pre-applied above).
        // Drained here so each grant is forwarded exactly once.
        List<StationContribution> oneShot = s.pendingOneShotContributions.isEmpty()
                ? List.of() : List.copyOf(s.pendingOneShotContributions);
        s.pendingOneShotContributions.clear();
        StationEvents.fireCycleCompleted(store, commandBuffer, s.playerRef, s.playerUuid, s.sessionId,
                s.stationId, action.getActionId(), cycleIndex, idle, perCycle, oneShot, contributionScale);
    }

    /**
     * The forwarded {@code Work.PerCycleContributions} for one cycle-completed event, over an
     * already-resolved (extension-merged) array. Scaling order:
     * {@code amount x idleFraction (idle cycles only) x contributionScale}.
     *
     * <p>The per-cycle filter is deliberately WEAKER than {@code Contribution.isPostable()} (the
     * one-shot grants-site gate): only a blank {@code Channel} is skipped, and a null
     * {@code Amount} reads as {@code 0.0}. A zero-amount per-cycle entry therefore still reaches a
     * listener, which is what lets it render as a visible zero row in a session breakdown instead
     * of silently vanishing.
     */
    @Nonnull
    static List<StationContribution> contributionsFrom(@Nullable Contribution[] posts, boolean idle,
            double idleFraction, double contributionScale) {
        if (posts == null || posts.length == 0) {
            return List.of();
        }
        List<StationContribution> out = new ArrayList<>(posts.length);
        for (Contribution c : posts) {
            if (c == null || c.getChannel() == null || c.getChannel().isBlank()) {
                continue;
            }
            double amount = c.getAmount() != null ? c.getAmount() : 0.0;
            if (idle) {
                amount *= idleFraction;
            }
            out.add(new StationContribution(c.getChannel(), c.getParam(), amount * contributionScale));
        }
        return out;
    }

    /**
     * The running action's EFFECTIVE contribution {@code Param}s keyed by channel: the action's own
     * {@code Work.PerCycleContributions} PLUS whatever an {@code Action}-targeted
     * {@code ExtensionAsset} appends, through the same {@code applyToActionContributions} merge the
     * cycle-completed event and the api station view read. Resolving the raw base here left an
     * extension-declared {@code Param} invisible to a no-{@code Param} factor read while the very
     * same pair was already riding the cycle event - one channel vocabulary, two answers.
     *
     * <p>{@code actionTargetId} is the caller's already-resolved {@code Target:{Action}} identity
     * ({@code null} for the implicit action of a no-{@code Actions} station, which an Action target
     * deliberately never reaches); {@code stationId} is what decides whether a station-SCOPED
     * extension applies. Both are values every call site already holds.
     */
    @Nonnull
    static Map<String, List<String>> contributionParams(@Nullable String stationId,
            @Nullable String actionTargetId, @Nullable StationAsset.Work work) {
        Contribution[] posts = work != null ? work.getPerCycleContributions() : null;
        if (actionTargetId != null) {
            posts = ExtensionCatalog.getInstance().applyToActionContributions(stationId, actionTargetId, posts);
        }
        return contributionParams(posts);
    }

    /**
     * The PURE keyed projection of an already-resolved contribution array, in authoring order (for
     * {@link FactorContext#contributionParams(String)}). Channel keys are lowercased by
     * {@code FactorContext} itself on copy; a blank {@code Param} is dropped, so a channel posted to
     * with no params answers an empty list rather than disappearing.
     */
    @Nonnull
    private static Map<String, List<String>> contributionParams(@Nullable Contribution[] posts) {
        if (posts == null || posts.length == 0) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Contribution c : posts) {
            if (c == null || c.getChannel() == null || c.getChannel().isBlank()) {
                continue;
            }
            List<String> params = out.computeIfAbsent(c.getChannel(), k -> new ArrayList<>());
            if (c.getParam() != null && !c.getParam().isBlank()) {
                params.add(c.getParam());
            }
        }
        return out;
    }

    /**
     * The {@code Cycle}-trigger Bonus pass for an AUTHORED step program's completed pass. The
     * implicit convert program reaches the same moment through its own embedded Roll phase; an
     * authored program has none, so without this an action's {@code Bonus} was live under one
     * program shape and inert under the other, for no reason an author could see.
     * {@code Trigger: Cycle} means THE action's cycle-completed moment, whatever program shape it
     * runs.
     *
     * <p>Same snapshot, same {@link #applyGrantResult} handoff, and the same effective
     * {@link #effectiveBonusRolls} the other two routes read. {@code Grants.OutputItems} is the one
     * thing that still lands nowhere here: an authored program has no single cycle output to add
     * items to ({@code s.cycleOutputItemId} stays null and
     * {@link #grantBonusOutputItems} no-ops), which is exactly what the
     * {@code LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT} validator warning tells the author at authoring
     * time. Every OTHER grant kind - droplists, commands, effects, contributions, the reached
     * floor's presentation - now applies.
     */
    private static void rollCycleBonus(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nonnull StationAsset asset, @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player,
            @Nonnull FactorLookup snapshot, int cycleIndex) {
        List<Roll> rolls = effectiveBonusRolls(asset, action);
        if (rolls.isEmpty()) {
            return;
        }
        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(rolls,
                StationLootEngine.TRIGGER_CYCLE, snapshot, player,
                s.playerRef, s.stationId, action.getActionId(), cycleIndex, commandBuffer, store,
                s.blockX, s.blockY, s.blockZ);
        applyGrantResult(s, store, commandBuffer, player, result);
    }

    /**
     * The Completion-trigger Bonus pass (non-silent, {@code cyclesDone >= 1}): runs BEFORE
     * {@link #showSessionSummary} in {@link #stop} so any items it grants still appear in the
     * session's item ledger. Reads the SESSION's own action - the same {@code Bonus} group the
     * per-cycle pass reads, just filtered to the {@code Completion} trigger.
     */
    private void rollCompletionLoot(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        if (asset == null || s.actionId == null) {
            return;
        }
        ActionResolver.ResolvedAction action = ActionResolver.resolve(asset, s.actionId);
        List<Roll> rolls = effectiveBonusRolls(asset, action);
        if (rolls.isEmpty()) {
            return;
        }
        Player player = store.getComponent(s.ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        FactorLookup snapshot = FactorRegistryImpl.getInstance().snapshotFor(buildFactorContext(s, store, player, action, s.cyclesDone));
        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(rolls,
                StationLootEngine.TRIGGER_COMPLETION, snapshot, player,
                s.playerRef, s.stationId, s.actionId, s.cyclesDone, commandBuffer, store,
                s.blockX, s.blockY, s.blockZ);
        applyGrantResult(s, store, commandBuffer, player, result);
    }

    /**
     * Folds a {@link StationLootEngine.GrantResult} into the session's item ledger, plays every
     * reached floor's {@code Presentation} through {@link #emitMoment} on {@link
     * StationFlairs#MOMENT_RARE_FIND}, and fires a round-5 item-specific GOLD "what you gained"
     * notification ({@link #notifyItemGain}, {@code lucky=true}) per distinct granted item id -
     * REPLACES the old generic {@code ui.station.lucky}/{@code ui.station.rare_find}
     * toasts (design 4.5.1), which no longer fire from here.
     */
    static void applyGrantResult(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nullable Player player, @Nonnull StationLootEngine.GrantResult result) {
        if (!result.anyGranted()) {
            return;
        }
        grantBonusOutputItems(s, store, commandBuffer, player, result.getOutputItems());
        for (Map.Entry<String, Integer> e : result.getDropListItems().entrySet()) {
            s.luckItems.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        // A rpgstations:contribution reward buffers its one-shot post for THIS cycle's completed
        // event, which forwards them on oneShotContributions. The pass only ever collects them under
        // a Cycle trigger, so a Completion-trigger roll never queues one nothing would drain. Every
        // entry here already passed Contribution#isPostable (non-blank channel, positive amount).
        for (Contribution post : result.getContributions()) {
            s.pendingOneShotContributions.add(
                    new StationContribution(post.getChannel(), post.getParam(), post.getAmount()));
        }
        // A CUE is a moment id, so each one plays through the same emitMoment funnel every other
        // station moment does - which means the action's own Moments entry for that id, plus every
        // applicable flair overlay, gets its say. Passing a null base is what hands the resolution
        // to that map: the loot layer names the moment, this engine decides what it sounds like.
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        for (String cue : result.getCues()) {
            emitMoment(store, s, cue, null, blockPos);
        }
        if (s.playerRef != null) {
            for (Map.Entry<String, Integer> e : result.getDropListItems().entrySet()) {
                notifyItemGain(s.playerRef, e.getKey(), e.getValue(), true);
            }
        }
        // F1 (decision 51d): every granted Roll's Grants.Effects[] now goes LIVE - apply each native
        // EntityEffect on the player and TRACK it on the session, exactly like a Presentation.Effect
        // (emitMoment). BOTH roll routes (per-cycle step Roll + Completion) fold through
        // applyGrantResult, so this ONE site covers both apply calls - but the TWO ROUTES HAVE
        // DIFFERENT TEARDOWN SEMANTICS, by design (arc-close MIN-1, maintainer-ruled, not a bug):
        //   - Per-cycle route (rollPhase, mid-WORKING): tracked BEFORE this session's own stop() ever
        //     runs, so the appliedEffects teardown below (stop()'s removeAll, well before this
        //     method's own completion-route call site) strips it when the session ends.
        //   - Completion route (rollCompletionLoot): invoked from INSIDE stop() itself, AFTER that
        //     same appliedEffects.removeAll teardown already ran - so a completion-trigger effect is
        //     tracked here but NEVER stripped by this stop() call. That is deliberate: a
        //     completion-trigger effect is an end-of-work FINISHING REWARD meant to persist for its
        //     own authored/asset duration (running teardown after it would immediately no-op the
        //     reward, defeating the point of granting one) - it is not session-scoped like the
        //     per-cycle route's effects. Do not reorder stop()'s teardown/rollCompletionLoot call
        //     order to "fix" this; it is the intended contract.
        // Fail-closed (a missing/invalid ref or effect id no-ops via NativeEffectUtil).
        if (s.ref != null && s.ref.isValid() && !result.getEffectGrants().isEmpty()) {
            applyAndTrackEffects(result.getEffectGrants(), s.ref, s.appliedEffects,
                    (effectId, durMs) -> durMs != null && durMs > 0
                            ? NativeEffectUtil.applyFor(store, s.ref, effectId, durMs / 1000f, OverlapBehavior.OVERWRITE)
                            : NativeEffectUtil.apply(store, s.ref, effectId));
        }
    }

    /**
     * Grants the ADDITIVE items of THIS cycle's primary output a Bonus roll's
     * {@code Grants.OutputItems} handed over, through the same {@code util.ItemGrantUtil} seam every
     * other station grant uses, and folds them into BOTH the produced ledger and the produced row's
     * yield breakdown - so the summary reads "deterministic yield plus what the rolls added".
     *
     * <p>{@code tally} is the cycle's FRACTIONAL sum across every roll that granted, resolved to
     * whole items HERE, once ({@link OutputItemResolver}: the whole part always, plus one more at
     * the leftover fraction's probability). One resolution per cycle rather than one per roll is
     * what makes two rolls paying {@code 0.5} each average a whole item; the ledger, the yield
     * breakdown, and the toast then all report the count that actually LANDED
     * ({@link OutputItemResolver#reportable} folds the grant outcome into the rolled count), since
     * that is what the player received.
     *
     * <p>Deliberately SILENT: these are more of the item the cycle was already producing, and the
     * produce phase's own gain notification already fired for it - a second toast per cycle would be
     * noise. The summary's produced row (and its breakdown line) is where the extra shows up.
     *
     * <p>A no-op when the cycle has no primary output to add to (an authored Steps program, whose
     * "cycle output" is undefined - {@code LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT} flags that authoring)
     * or when nothing was granted. That no-op is load-bearing rather than defensive: an authored
     * program's completed pass DOES run the action's {@code Cycle}-trigger Bonus rolls, so an
     * {@code OutputItems} amount genuinely reaches here with no item id to spend it on, and must
     * fail quietly instead of grabbing a stale one.
     */
    static void grantBonusOutputItems(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nullable Player player, double tally) {
        int count = OutputItemResolver.resolve(tally, () -> ThreadLocalRandom.current().nextDouble());
        if (count <= 0) {
            return;
        }
        String itemId = s.cycleOutputItemId;
        if (itemId == null || itemId.isBlank() || player == null) {
            Log.fine("STATION Bonus OutputItems had no cycle output to add to at station '" + s.stationId + "'");
            return;
        }
        int reported;
        try {
            boolean landed = ItemGrantUtil.grantOrDrop(player, new ItemStack(itemId, count), commandBuffer, store,
                    s.blockX, s.blockY, s.blockZ);
            // The rolled count and the RECEIVED count are different facts, folded in one pure place
            // (OutputItemResolver#reportable) so the ledger, the yield breakdown, and the toast below
            // can never disagree about which of the two they are showing.
            reported = OutputItemResolver.reportable(count, landed);
        } catch (Throwable t) {
            Log.fine("STATION Bonus OutputItems grant failed: " + t.getMessage());
            return;
        }
        if (reported <= 0) {
            // Nowhere to put it and the ground drop failed too: the items do not exist, so
            // they must not be tallied into the session summary as produced.
            Log.warn("STATION bonus output lost - no inventory room and drop failed for '" + itemId + "'");
            return;
        }
        s.producedItems.merge(itemId, reported, Integer::sum);
        StationSession.YieldBreakdown breakdown = s.producedYield.get(itemId);
        if (breakdown != null) {
            breakdown.addBonus(reported);
        }
        // Tell the player about the bonus too. The Produce phase notifies only the recipe's own
        // deterministic Yield, so without this a cycle that granted one base plank plus four from
        // the tool ladder reported a single plank - the toast under-counted every bonus this
        // station pays, which reads as the reward not working rather than the toast being wrong.
        // Not flagged lucky: this is ordinary output of the cycle, just more of it.
        if (s.playerRef != null) {
            notifyItemGain(s.playerRef, itemId, reported, false);
        }
    }

    /**
     * The injectable per-effect apply seam for {@link #applyAndTrackEffects} - returns whether the
     * effect actually applied, so only a SUCCESS is tracked. Production passes a {@link
     * NativeEffectUtil} lambda; a fixture test passes a fake, letting the "granted effect lands in
     * the tracked list, teardown clears it" contract be verified without a live effect asset map
     * (constructing an {@code EntityEffect} throws outside a running server).
     */
    @FunctionalInterface
    interface EffectApplier {
        boolean apply(@Nonnull String effectId, @Nullable Long durationMs);
    }

    /**
     * Applies every granted {@link EffectRef} through {@code applier} and tracks each SUCCESS on
     * {@code tracker}, so the session's {@code stop()} teardown ({@link AppliedEffectTracker#removeAll})
     * removes them (decision 51d, mirroring a {@code Presentation.Effect}'s live path). A blank-id ref
     * is skipped; a failed apply is never tracked. Pure over the applier seam (unit-tested with a fake
     * applier + the null-ref tracker stand-in).
     */
    static void applyAndTrackEffects(@Nonnull List<EffectRef> effects, @Nonnull Ref<EntityStore> ref,
            @Nonnull AppliedEffectTracker tracker, @Nonnull EffectApplier applier) {
        for (EffectRef effect : effects) {
            if (effect == null || !effect.hasId()) {
                continue;
            }
            if (applier.apply(effect.getId(), effect.getDurationMs())) {
                tracker.track(ref, effect.getId());
            }
        }
    }

    /**
     * Per-cycle api {@link FactorContext} for the built-in {@code rpgstations:} factors ({@code
     * api.impl.FactorRegistryImpl#registerBuiltins}) plus every other registered provider: session
     * seconds elapsed, the cycle index, and the currently-held item's tool power / quality / item
     * level / durability percent, all read fresh each cycle rather than snapshotted at engage.
     *
     * <p>ALWAYS action-anchored: the running action owns the tool gate and the contribution
     * channels, so the context reports ITS id and ITS EFFECTIVE channels - extension-appended
     * entries included, exactly as the completed cycle will post them
     * ({@link #contributionParams(String, String, StationAsset.Work)}). {@code cycleIndex} is the ATTEMPT
     * index ({@code s.cyclesDone + 1}, computed before {@code s.cyclesDone} itself advances) so a
     * Roll step's factor context sees the cycle it is actually running, not the last COMPLETED one.
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
                .toolPowers(resolveHeldToolPowers(player))
                .toolQuality(resolveHeldToolQuality(player))
                .toolItemLevel(resolveHeldToolItemLevel(player))
                .contributions(contributionParams(s.stationId, actionTargetIdFor(s, action.getActionId()),
                        action.getWork()))
                .build();
    }

    /**
     * The held tool's power for the station's effective gather type ({@code Tool.Gather.GatherType}
     * only). 0 when no gather type resolves or no matching spec is held.
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

    /**
     * The active hotbar item's RARITY as the native {@code ItemQuality.QualityValue} the referenced
     * quality asset authors ({@code hytale:tool_quality}); 0 when nothing is held or the item
     * names no quality.
     *
     * <p>Two indirections, both deliberate. {@code Item#getQualityIndex()} returns an ASSET-MAP
     * INDEX, not the ordering value, so the index is resolved back through
     * {@code ItemQuality.getAssetMap()} to read the authored {@code QualityValue} - the number that
     * actually orders qualities (0 = lowest) and the only one a pack-added tier participates in. The
     * engine's own {@code DEFAULT_ITEM_QUALITY} authors {@code -1}, which is floored to 0 here so an
     * unqualified item can never drag a weighted formula below an authored {@code Junk} tier.
     *
     * <p>Fully try-guarded: this runs per cycle on the world thread, and a quality lookup is never
     * worth failing a work cycle over.
     */
    private static double resolveHeldToolQuality(@Nonnull Player player) {
        try {
            ItemStack held = InventoryAccess.activeHotbarItemOf(player);
            Item item = held != null ? held.getItem() : null;
            if (item == null) {
                return 0.0;
            }
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
            return quality == null ? 0.0 : Math.max(0.0, quality.getQualityValue());
        } catch (Throwable t) {
            Log.fine("STATION could not resolve the held tool's quality: " + t.getMessage());
            return 0.0;
        }
    }

    /**
     * The active hotbar item's native {@code ItemLevel} ({@code hytale:tool_item_level}); 0 when
     * nothing is held. The fine-grained third tool axis - see {@code FactorContext#toolItemLevel()}
     * for why it is a tiebreaker rather than a primary one. Try-guarded like its two siblings.
     */
    private static double resolveHeldToolItemLevel(@Nonnull Player player) {
        try {
            ItemStack held = InventoryAccess.activeHotbarItemOf(player);
            Item item = held != null ? held.getItem() : null;
            return item == null ? 0.0 : Math.max(0.0, item.getItemLevel());
        } catch (Throwable t) {
            Log.fine("STATION could not resolve the held tool's item level: " + t.getMessage());
            return 0.0;
        }
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
     * extra rows PREPENDED before the engine's own item ledger (design section 3.2/7.2-7.3 - a
     * listening mod's own progress rows land here) + a post-build {@code decorate} pass for
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
            rows.add(new StationSummaryHud.LedgerRow(e.getKey(), e.getValue(), line, SummaryRow.Kind.PRODUCED,
                    yieldBreakdownLine(s.producedYield.get(e.getKey()))));
        }
        for (Map.Entry<String, Integer> e : s.luckItems.entrySet()) {
            Message line = Msg.cat(
                    RpgMsg.tr("ui.station.summary.item_produced", itemNameMsg(e.getKey()), e.getValue()),
                    Msg.raw(" "),
                    RpgMsg.tr("ui.station.summary.lucky"));
            rows.add(new StationSummaryHud.LedgerRow(e.getKey(), e.getValue(), line, SummaryRow.Kind.LUCKY));
        }
        rows.addAll(enhanceLedgerRows(s.enhanceOutcomes));
        return rows;
    }

    /**
     * The optional SECOND line under a PRODUCED ledger row: the per-cycle yield decomposition then
     * the cycle count, e.g. "1 base + 3 tool  x 12 cycles" explaining a 48-plank row.
     *
     * <p>Returns {@code null} - so the row collapses back to one line - whenever the yield did not
     * actually change the number ({@code Scale} neutral and no bonus reached) or nothing was
     * recorded. That is deliberate: a starter tool producing exactly its base yield gets NO second
     * line, so the line's presence is itself the signal that the tool is earning something.
     *
     * <p>Deliberately NOT applied to LUCKY, CONSUMED, or ENHANCE rows: none of those is a yield, and
     * a luck grant's quantity comes from a native drop list this engine does not decompose.
     */
    @Nullable
    private static Message yieldBreakdownLine(@Nullable StationSession.YieldBreakdown breakdown) {
        if (breakdown == null || !breakdown.changed || breakdown.cycles <= 0) {
            return null;
        }
        return RpgMsg.tr("ui.station.summary.produced_breakdown",
                formatYieldTerm(breakdown.basePerCycle()),
                formatYieldTerm(breakdown.bonusPerCycle()),
                breakdown.cycles);
    }

    /** Formats one breakdown term: a whole number drops its trailing {@code .0}, a rung keeps its fraction. */
    @Nonnull
    private static String formatYieldTerm(double amount) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        return rounded == Math.rint(rounded)
                ? NumberFormatter.grouped((long) rounded) : String.valueOf(rounded);
    }

    /**
     * The enhance ledger rows for the summary panel (design section 9.5, phase 2 round-7 D-6):
     * per committed {@link StationEnhanceOutcome}, one row per {@link EnhanceLine} the registered
     * stamper reported, plus, when a stamp added max durability, ONE engine-owned durability row the
     * engine composes AND colors itself ({@link #ENHANCE_ROW_COLOR} - durability is RpgStations-native,
     * real even with no stamper registered). Extracted pure/static so it unit-tests without a live
     * session service; the icon is the enhanced item itself.
     *
     * <p>A line's {@code label()} renders VERBATIM when it has one - the mod that owns the stat
     * vocabulary owns its wording and its per-stat color, so no foreign stat vocabulary reaches this
     * engine. When it has none, the row still says the plain true thing: the stat's own id and the
     * points added to it. A row must never carry nothing - the ledger sends its text straight to the
     * client - and staying silent about a stat the ritual just applied would leave the player unable
     * to tell an enhancement from a failure.
     */
    @Nonnull
    static List<StationSummaryHud.LedgerRow> enhanceLedgerRows(@Nonnull List<StationEnhanceOutcome> outcomes) {
        List<StationSummaryHud.LedgerRow> rows = new ArrayList<>();
        for (StationEnhanceOutcome outcome : outcomes) {
            for (EnhanceLine line : outcome.lines()) {
                rows.add(new StationSummaryHud.LedgerRow(outcome.itemId(), line.points(),
                        enhanceStatLine(line), SummaryRow.Kind.ENHANCE));
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

    /**
     * One enhance line's summary text: the provider's own styled label when it supplied one, else the
     * engine's plain report of the stat id and its points.
     *
     * <p>The fallback is deliberately unglamorous rather than absent. This engine owns no stat
     * vocabulary, so it cannot name what {@code Damage} or {@code Swing_Speed} means to a player - but
     * it does know that the ritual just wrote that many points of it, and saying so is strictly better
     * than an empty row on the one panel that reports what an enhancement did.
     */
    @Nonnull
    private static Message enhanceStatLine(@Nonnull EnhanceLine line) {
        Message label = line.label();
        if (label != null) {
            return label;
        }
        return RpgMsg.tr("ui.station.summary.enhance_stat", line.statId(),
                NumberFormatter.grouped(line.points())).color(ENHANCE_ROW_COLOR);
    }

    /** Formats a durability delta for the summary row: a whole number drops its trailing {@code .0}. */
    @Nonnull
    private static String formatDurability(double amount) {
        return amount == Math.rint(amount) ? NumberFormatter.grouped((long) amount) : String.valueOf(amount);
    }

    /**
     * The ONE presentation-playback choke point: every station moment funnels through here.
     * Resolves the effective presentation through {@link StationFlairs#effective} FIRST, then
     * plays {@code Sounds} (in authored order), {@code Particles}, and {@code Shake} - see
     * {@link Presentation.Shake}'s javadoc for the exact {@code CameraShakeService} parameter
     * shape this leaf was verified against (critique m6 binding fix). Shake needs the player
     * SPECIFICALLY (not "nearby players" like Sound3D/ModelParticleService), so it reads
     * {@code s.playerRef} rather than {@code targetPos}.
     *
     * <p><b>Leg F (design section 9.6):</b> {@code momentId} is an open STRING (see {@link
     * StationFlairs}'s well-known constants + {@link StationFlairs#stepMomentId}), and the flair
     * map resolved against is the UNION of the station's own inline {@code Flairs} with every
     * applicable standalone {@code asset.FlairAsset} ({@link #effectiveFlairs}).
     *
     * <p><b>SPECIFICITY WINS, resolved here.</b> {@code base} is whatever presentation the CALLER
     * already holds for this emission - a step's own, a reached loot floor's. When it holds none,
     * the running action's own {@code Moments} entry for {@code momentId} is used instead
     * ({@link StationSession#moments}, snapshotted at engage). That one rule is why an action can
     * author {@code swing}/{@code impact}/{@code cycle}/{@code completion} beside every other cue
     * and still never override a step that speaks for itself.
     *
     * <p><b>{@code Presentation.DelayMs} is applied HERE, after the flair fold</b>, so the winning
     * presentation is the one whose timing is honored (a flair that re-times a moment re-times the
     * cue it actually replaced). A delayed cue is parked in this world's {@link #pendingMomentsByWorld}
     * queue with the RESOLVED presentation and played by {@link #drainPendingMoments}; an undelayed
     * one plays inline. A {@code Sounds} entry carrying its OWN offset is split off first into a
     * sound-only cue queued at the moment's delay PLUS its own, so per-sound timing needs no second
     * mechanism: everything lands on the one due-time core
     * ({@link #scheduleCueAt}/{@link #cueDue}), and there is exactly one scheduler in this engine.
     */
    static void emitMoment(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
                                   @Nonnull String momentId, @Nullable Presentation base,
                                   @Nonnull Vector3d targetPos) {
        Presentation resolvedBase = base != null ? base : actionMoment(s, momentId);
        Presentation p = StationFlairs.effective(resolvedBase, effectiveFlairs(s), momentId, s.playerUuid, s.stationId);
        if (p == null) {
            return;
        }
        for (Presentation offsetCue : offsetSoundCues(p)) {
            long cueDelay = offsetCue.effectiveDelayMs();
            if (!getInstance().queueDelayedMoment(store, s, momentId, offsetCue, targetPos, cueDelay)) {
                playMoment(store, s, offsetCue, targetPos);
            }
        }
        Presentation main = withoutOffsetSounds(p);
        if (!main.hasPlayableCue()) {
            return;
        }
        long delayMs = main.effectiveDelayMs();
        if (delayMs > 0 && getInstance().queueDelayedMoment(store, s, momentId, main, targetPos, delayMs)) {
            return;
        }
        playMoment(store, s, main, targetPos);
    }

    /**
     * The running action's own authored {@code Moments} entry for {@code momentId}, or null. The
     * session's snapshot is already canonicalized to lowercase keys, so the lookup lowercases to
     * match and a key authored {@code "Cycle"} resolves.
     */
    @Nullable
    private static Presentation actionMoment(@Nonnull StationSession s, @Nonnull String momentId) {
        return s.moments == null ? null : s.moments.get(momentId.toLowerCase(Locale.ROOT));
    }

    /**
     * PURE: {@code p} with every {@code Sounds} entry that carries its OWN offset removed, because
     * those play as their own cues (see {@link #offsetSoundCues}). Returns {@code p} ITSELF when no
     * entry carries one, which is the shipped-content path and keeps it allocation-free.
     */
    @Nonnull
    static Presentation withoutOffsetSounds(@Nonnull Presentation p) {
        Presentation.SoundCue[] sounds = p.getSounds();
        if (!hasOffsetSound(sounds)) {
            return p;
        }
        List<Presentation.SoundCue> kept = new ArrayList<>(sounds.length);
        for (Presentation.SoundCue cue : sounds) {
            if (cue != null && cue.effectiveDelayMs() <= 0) {
                kept.add(cue);
            }
        }
        return Presentation.of(kept.isEmpty() ? null : kept.toArray(new Presentation.SoundCue[0]),
                p.getParticles(), p.getShake(), p.getInteraction(), p.getEffect(), p.getDelayMs());
    }

    /**
     * PURE: one sound-only cue per {@code Sounds} entry carrying its own offset, each already
     * holding its TOTAL delay ({@code Presentation.DelayMs + the entry's own}) as its own
     * {@code DelayMs}. The moment's delay offsets the moment; the entry's offsets that sound inside
     * it, so the two ADD - and the result is an ordinary delayed cue that needs no special handling
     * anywhere downstream. Empty (and allocation-free) when no entry carries an offset.
     */
    @Nonnull
    static List<Presentation> offsetSoundCues(@Nonnull Presentation p) {
        Presentation.SoundCue[] sounds = p.getSounds();
        if (!hasOffsetSound(sounds)) {
            return List.of();
        }
        long groupDelayMs = p.effectiveDelayMs();
        List<Presentation> out = new ArrayList<>(sounds.length);
        for (Presentation.SoundCue cue : sounds) {
            if (cue == null || cue.effectiveDelayMs() <= 0) {
                continue;
            }
            out.add(Presentation.of(new Presentation.SoundCue[] {Presentation.SoundCue.of(cue.getEventId())},
                    null, null, null, null, groupDelayMs + cue.effectiveDelayMs()));
        }
        return out;
    }

    /** PURE: whether any authored {@code Sounds} entry carries an offset of its own. */
    private static boolean hasOffsetSound(@Nullable Presentation.SoundCue[] sounds) {
        if (sounds == null) {
            return false;
        }
        for (Presentation.SoundCue cue : sounds) {
            if (cue != null && cue.effectiveDelayMs() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parks a resolved cue until its delay elapses. Returns false when it could not be queued (the
     * world is unresolvable, or this world is already at {@link #MAX_PENDING_MOMENTS_PER_WORLD}), in
     * which case {@link #emitMoment} plays it immediately - a lost OFFSET, never a lost cue.
     */
    private boolean queueDelayedMoment(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
                                       @Nonnull String momentId, @Nonnull Presentation p,
                                       @Nonnull Vector3d targetPos, long delayMs) {
        World world;
        try {
            world = WorldEvictors.worldOf(store);
        } catch (Throwable t) {
            Log.fine("STATION could not resolve the world for a delayed moment: " + t.getMessage());
            return false;
        }
        ConcurrentLinkedQueue<PendingMoment> queue = pendingMomentsByWorld.queueFor(world);
        AtomicInteger count = pendingMomentCounts.computeIfAbsent(world, k -> new AtomicInteger());
        // Claim the slot FIRST and hand it back on refusal, so the ceiling holds exactly even if two
        // threads ever race here - reading then incrementing could admit both at the boundary.
        if (pendingMomentsAtCapacity(count.getAndIncrement())) {
            count.decrementAndGet();
            Log.fine("STATION delayed-moment queue is full for this world; playing '" + momentId + "' at once");
            return false;
        }
        // The position is COPIED, deliberately: the caller's Vector3d is a live mutable engine value
        // read a tick or more later, and a delayed cue should play where the moment happened, not
        // wherever the player has walked to by the time it fires.
        return queue.offer(new PendingMoment(s, momentId, p, new Vector3d(targetPos),
                scheduleCueAt(System.currentTimeMillis(), delayMs)));
    }

    /** Pure capacity gate for one world's delayed-cue queue. */
    static boolean pendingMomentsAtCapacity(int currentSize) {
        return currentSize >= MAX_PENDING_MOMENTS_PER_WORLD;
    }

    /**
     * Plays every cue in this world's delayed queue that has come due and leaves the rest parked.
     * Nothing is ever dropped for being late: a cue plays on the first tick at or after its own due
     * time, so two cues authored with different delays land in the order their delays put them -
     * with a resolution of one server tick. Two cues that come due inside the SAME tick both play in
     * that tick, in the order they were emitted; the delay orders cues across ticks, not within one.
     *
     * <p>Runs BEFORE the session loop in {@link #tickFrameOnce} and independently of it - a delayed
     * completion cue belongs to a session that has already stopped, and the world may by then hold
     * no sessions at all.
     *
     * <p>A cue whose player is gone (invalid ref) or has left this world is DISCARDED rather than
     * played: it is a positional cue for a body that is no longer there.
     */
    private void drainPendingMoments(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        ConcurrentLinkedQueue<PendingMoment> queue = pendingMomentsByWorld.peek(world);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        AtomicInteger count = pendingMomentCounts.get(world);
        long now = System.currentTimeMillis();
        Iterator<PendingMoment> it = queue.iterator();
        while (it.hasNext()) {
            PendingMoment pending = it.next();
            if (!cueDue(now, pending.dueAtMs())) {
                continue;
            }
            it.remove();
            if (count != null) {
                count.decrementAndGet();
            }
            StationSession s = pending.session();
            if (s.ref == null || !s.ref.isValid() || s.ref.getStore() != store) {
                continue;
            }
            try {
                playMoment(store, s, pending.presentation(), pending.targetPos());
            } catch (Throwable t) {
                Log.fine("STATION delayed moment '" + pending.momentId() + "' failed: " + t.getMessage());
            }
        }
        // Self-heal: an emptied queue pins its counter back to zero, so no accounting slip can ever
        // accumulate into a world that reports itself permanently full.
        if (count != null && queue.isEmpty()) {
            count.set(0);
        }
    }

    /**
     * Whether a stop should discard whatever cues its session still has parked.
     *
     * <p>An INTERRUPT falls silent: a session that ended by walking off, taking a hit, dying, or
     * breaking its tool should not keep playing the sounds of work that is no longer happening. A
     * COMPLETION keeps them: the cues a finished run parked are the sound of work that DID happen,
     * and a non-looping ritual emits its final cycle's cues microseconds before stopping itself, so
     * sweeping by session alone would silence exactly the moment the ritual exists to celebrate.
     *
     * <p>The session's own COMPLETION cue is outside this either way: {@link #stop} emits it after
     * the sweep, into a queue that belongs to the WORLD rather than the session, so a completion
     * moment authoring a delay always plays.
     */
    static boolean dropsPendingCuesAtStop(@Nonnull StopReason reason) {
        return reason != StopReason.RITUAL_COMPLETE && reason != StopReason.INPUTS_EXHAUSTED;
    }

    /**
     * Discards every cue this session still has parked (see {@link #dropsPendingCuesAtStop} for
     * which stops reach here). Walks {@link #pendingMomentCounts} rather than the queue partition's
     * values because only the counter map exposes the world each queue belongs to, and its keys are
     * exactly the worlds that have ever parked a cue.
     */
    private void dropPendingMoments(@Nonnull StationSession s) {
        for (Map.Entry<World, AtomicInteger> entry : pendingMomentCounts.entrySet()) {
            ConcurrentLinkedQueue<PendingMoment> queue = pendingMomentsByWorld.peek(entry.getKey());
            if (queue == null) {
                continue;
            }
            int removed = 0;
            Iterator<PendingMoment> it = queue.iterator();
            while (it.hasNext()) {
                if (it.next().session() == s) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                entry.getValue().addAndGet(-removed);
            }
        }
    }

    /**
     * Plays one already-resolved presentation: {@code Sounds} (in authored order),
     * {@code Particles}, {@code Shake}, and the two native-composition payloads. Reached either
     * inline from {@link #emitMoment} or, for a delayed cue, from {@link #drainPendingMoments}.
     */
    private static void playMoment(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
                                   @Nonnull Presentation p, @Nonnull Vector3d targetPos) {
        // Every Sounds entry reaching here plays NOW: an entry with its own offset was split off
        // into its own queued cue back in emitMoment, so this loop never re-reads a per-sound delay.
        Presentation.SoundCue[] sounds = p.getSounds();
        if (sounds != null) {
            for (Presentation.SoundCue cue : sounds) {
                if (cue != null && cue.hasEventId()) {
                    Sound3D.play(cue.getEventId(), targetPos, store, "STATION");
                }
            }
        }
        spawnMomentParticles(store, s, p.getParticles(), targetPos);
        Presentation.Shake shake = p.getShake();
        if (shake != null && shake.getEffectId() != null && !shake.getEffectId().isBlank()) {
            float intensity = shake.getIntensity() != null ? shake.getIntensity().floatValue() : 1.0f;
            CameraShakeService.shake(s.playerRef, shake.getEffectId(), intensity);
        }
        // Native-composition payloads (seam wave decisions 51b/51d), fired on the player entity:
        // a Presentation.Interaction fires a native RootInteraction chain by id; a
        // Presentation.Effect applies a native EntityEffect by id (session-tracked so stop() removes
        // it). Both id-ref-only, both fail-closed (a missing id is a no-op, never a throw). The
        // EntityEffect apply is byte-parity-safe from this processing-locked frame - the shipped
        // hold effect already applies via the live store the same way (StationHoldController).
        // An effect applied by a cue that outlives its session (the completion moment, delayed or
        // not) is tracked on a session whose teardown has already run, so it lives out its own
        // EffectRef.DurationMs / the effect asset's TTL instead of being stripped at stop - the same
        // lifetime the undelayed completion cue has always had, since stop() strips tracked effects
        // before it plays that moment.
        if (s.ref != null && s.ref.isValid()) {
            Presentation.Interaction interaction = p.getInteraction();
            if (interaction != null && interaction.hasId()) {
                NativeChainFire.fire(store, s.ref, interaction.getId(), InteractionType.Use);
            }
            EffectRef effect = p.getEffect();
            if (effect != null && effect.hasId()) {
                Long durMs = effect.getDurationMs();
                boolean applied = durMs != null && durMs > 0
                        ? NativeEffectUtil.applyFor(store, s.ref, effect.getId(), durMs / 1000f, OverlapBehavior.OVERWRITE)
                        : NativeEffectUtil.apply(store, s.ref, effect.getId());
                if (applied) {
                    s.appliedEffects.track(s.ref, effect.getId());
                }
            }
        }
    }

    /**
     * Plays every authored {@code Presentation.Particles} burst at {@code targetPos}, in authored
     * order: each entry contributes its own {@code Scale}, {@code DurationSeconds} playback cap,
     * {@code RotationOffset} (degrees, converted to the engine's radian yaw/pitch/roll arguments),
     * and FACING-RELATIVE {@code PositionOffset} (composed through the one shared
     * {@link StationBlockFacing} reader, exactly like {@code Custody.Display.Offset} and
     * {@code Puppet.Offset}). An entry authoring only {@code SystemId} reproduces the pre-array
     * single-string behavior byte for byte (scale 1, a 4s cap, zero rotation, no offset).
     *
     * <p>This reaches the engine's own full-arity {@code ParticleUtil} overload rather than
     * {@code ziggfreed-common}'s {@code ModelParticleService.spawnAt}, whose signature hardcodes the
     * rotation and scale arguments this schema now authors; the guard shape (one try/catch per
     * burst, a failure never escapes a moment) mirrors that primitive's own. The convergence target
     * is a common-side overload taking the full argument set - lift this call when one exists.
     * The per-burst duration cap is load-bearing: at least one shipped particle asset
     * ({@code Block_Gem_Sparks}) authors an unbounded spawner that never stops without it.
     */
    private static void spawnMomentParticles(@Nonnull Store<EntityStore> store, @Nonnull StationSession s,
            @Nullable Presentation.ModelParticle[] particles, @Nonnull Vector3d targetPos) {
        if (particles == null || particles.length == 0) {
            return;
        }
        double blockYaw = Double.NaN;
        for (Presentation.ModelParticle burst : particles) {
            if (burst == null || !burst.hasSystemId()) {
                continue;
            }
            Vector3d pos = targetPos;
            Vec3 offset = burst.getPositionOffset();
            if (offset != null) {
                if (Double.isNaN(blockYaw)) {
                    blockYaw = momentBlockYaw(store, s);
                }
                double[] world = StationBlockFacing.rotateOffset(
                        offset.getX() != null ? offset.getX() : 0.0,
                        offset.getY() != null ? offset.getY() : 0.0,
                        offset.getZ() != null ? offset.getZ() : 0.0,
                        blockYaw);
                pos = new Vector3d(targetPos.x() + world[0], targetPos.y() + world[1], targetPos.z() + world[2]);
            }
            Rotation rot = burst.getRotationOffset();
            float yaw = rot != null && rot.getYaw() != null ? (float) Math.toRadians(rot.getYaw()) : 0f;
            float pitch = rot != null && rot.getPitch() != null ? (float) Math.toRadians(rot.getPitch()) : 0f;
            float roll = rot != null && rot.getRoll() != null ? (float) Math.toRadians(rot.getRoll()) : 0f;
            try {
                ParticleUtil.spawnParticleEffect(burst.getSystemId(), pos, yaw, pitch, roll,
                        (float) burst.effectiveScale(), burst.effectiveDurationSeconds(), store);
            } catch (Throwable t) {
                Log.fine("STATION moment particle '" + burst.getSystemId() + "' failed: " + t.getMessage());
            }
        }
    }

    /**
     * The placed station block's own facing yaw for a moment's facing-relative particle offset,
     * try-guarded to {@code 0.0} (an unresolvable world degrades to world-space placement, never
     * aborts the burst) - the same fail-soft contract {@link StationBlockFacing} itself keeps.
     */
    private static double momentBlockYaw(@Nonnull Store<EntityStore> store, @Nonnull StationSession s) {
        try {
            return StationBlockFacing.yawRadians(WorldEvictors.worldOf(store), s.blockX, s.blockY, s.blockZ);
        } catch (Throwable t) {
            Log.fine("STATION could not resolve the world for a moment particle offset: " + t.getMessage());
            return 0.0;
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
     * The per-swing beat: re-fires the work animation as a ONE-SHOT and emits the TWO moments a
     * swing owns at the block - {@link StationFlairs#MOMENT_SWING} (the swing itself) and
     * {@link StationFlairs#MOMENT_IMPACT} (the strike landing behind it). The clip re-fire routes by
     * {@link StationSession#seatMode}.
     *
     * <p>Both moments emit UNCONDITIONALLY and resolve their own base through
     * {@link #emitMoment}: an action that authors neither entry costs one null map lookup per swing
     * and plays nothing, while a flair that authors one WITHOUT a base entry still gets to play -
     * which a "only emit when the action authored it" gate would have silently forbidden. What makes
     * the impact cue late is its own {@code Presentation.DelayMs}, riding the same one queue every
     * other delayed cue rides.
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
            // player. Round-8: the CLIP re-fire is suppressed for a stepped program whose steps
            // author their own Puppet.Clip (s.stepProgramAuthorsClip - those clips fire at each
            // step's iteration entry); the prop sync still runs regardless.
            StationPuppetController.playSwing(s, store, commandBuffer, swingPlayer, !s.stepProgramAuthorsClip);
        } else if (useActionSlotForSwing(s.seatMode)) {
            StationHoldController.playActionSwing(s, swingPlayer, store);
        } else {
            StationHoldController.playEmote(s, store);
        }
        Vector3d blockPos = new Vector3d(s.blockX + 0.5, s.blockY + 0.5, s.blockZ + 0.5);
        emitMoment(store, s, StationFlairs.MOMENT_SWING, null, blockPos);
        emitMoment(store, s, StationFlairs.MOMENT_IMPACT, null, blockPos);

        if (s.durabilityPerSwing > 0 && !s.idleMode && swingPlayer != null) {
            drainHeldToolDurability(store, s.ref, swingPlayer, s.durabilityPerSwing);
        }
    }

    /** The pure seat-vs-effect swing-route decision. */
    static boolean useActionSlotForSwing(boolean seatMode) {
        return seatMode;
    }

    /**
     * The ONE pure due-time scheduler in this engine: the millisecond at which a cue delayed by
     * {@code delayMs} comes due. EVERY offset cue resolves through it - a moment's own
     * {@code Presentation.DelayMs}, a single {@code Sounds} entry's, and the {@code impact} moment
     * that is late purely because it authors one - so there is deliberately no second scheduling
     * rule to keep in step with this one.
     */
    static long scheduleCueAt(long nowMs, long delayMs) {
        return nowMs + delayMs;
    }

    /**
     * The matching pure due check: true once {@code nowMs} has reached a scheduled
     * {@code dueAtMs}. A non-positive {@code dueAtMs} means "nothing scheduled" (the swing-impact
     * slot's own empty value), so it is never due.
     */
    static boolean cueDue(long nowMs, long dueAtMs) {
        return dueAtMs > 0 && nowMs >= dueAtMs;
    }


    /**
     * Every native {@code GatherType} the active hotbar item has a tool spec for, mapped to that
     * spec's power - backs the ADDRESSED {@code hytale:tool_power} read
     * ({@code {"Factor":"hytale:tool_power","Param":"<GatherType>"}}). Pre-resolved into the
     * {@code FactorContext} rather than read live at resolve time, so the pre-session
     * {@code Requires} gate (which builds a context with no live {@code Store}) still answers
     * correctly instead of degrading to 0. Try-guarded like its sibling reads.
     *
     * <p>The fold itself is the shared {@code entity.HeldItemUtil.toolPowersOf}, so a gather type a
     * tool authors TWICE keeps the STRONGEST spec here exactly as it does everywhere else that
     * reads a tool's powers.
     */
    @Nonnull
    private static Map<String, Double> resolveHeldToolPowers(@Nonnull Player player) {
        try {
            ItemStack held = InventoryAccess.activeHotbarItemOf(player);
            Item item = held != null ? held.getItem() : null;
            ItemTool itemTool = item != null ? item.getTool() : null;
            return HeldItemUtil.toolPowersOf(itemTool != null ? itemTool.getSpecs() : null);
        } catch (Throwable t) {
            Log.fine("STATION could not resolve the held tool's gather powers: " + t.getMessage());
            return Map.of();
        }
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
     * The session-completion presentation moment itself: plays the SESSION's own action's
     * {@code Moments.Completion} through the SAME {@link #emitMoment} choke point, on the
     * {@link StationFlairs#MOMENT_COMPLETION} moment id. Plays at the PLAYER's position, not the
     * block (completion celebrates the player).
     */
    private static void playCompletionMoment(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(s.ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        Vector3d playerPos = transform.getPosition();
        // No base is passed: emitMoment resolves the session's own snapshotted "completion" entry,
        // the same route every other action-authored moment takes.
        emitMoment(store, s, StationFlairs.MOMENT_COMPLETION, null, playerPos);
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
        byPlayer.remove(s.playerUuid, s);
        if (s.blockKey != null) {
            byBlock.remove(s.blockKey, s.playerUuid);
        }
        // Selection wave (decision 56): the chosen output category is session-scoped - clear it on
        // every stop path so it can never bleed into a future session (the session object is
        // discarded anyway; this is the explicit "cleared at stop" guarantee).
        s.chosenOutputCategory = null;
        // An INTERRUPTED session's still-parked cues go with it: work has stopped, so its leftover
        // sounds should not arrive afterwards. A COMPLETION keeps them - the cues of a run that
        // finished are the sound of work that happened. The COMPLETION cue itself is emitted below,
        // after this point, into the world-scoped queue, so a delayed completion moment survives the
        // stop either way.
        if (dropsPendingCuesAtStop(reason)) {
            dropPendingMoments(s);
        }

        // Placed-input custody auto-return (design 9.4): EVERY exit path, silent included - the
        // design's own binding list (re-press, walk-off, damage, death, disconnect, tool-changed,
        // out-of-inputs, inventory-full, session-cap, feature-disabled, step-failed, server-stop)
        // funnels through this ONE call, unconditionally, before any of the notification logic
        // below runs.
        // Multi-station teardown (scope-2 wave 3, design 2.6), BEFORE the primary custody return:
        // refund the in-flight iteration's consumed inputs (mutually exclusive with a custody return
        // per M1 - a Produce.To:Custody already cleared the ledger), then release + return every
        // remote anchor's claim/custody. The primary block's own custody returns just below.
        refundIterationLedger(s);
        releaseAnchorClaims(s, commandBuffer);

        StationAsset stopAsset = StationCatalog.getInstance().getStation(s.stationId);
        Custody stopCustody = stopAsset != null && s.actionId != null
                ? ActionResolver.resolve(stopAsset, s.actionId).getCustody() : null;
        returnCustody(s, stopCustody, commandBuffer);

        // Actively-working block state (Custody.States.Working): work has stopped by definition, so
        // darken whatever block this session left burning/running. Unconditional and idempotent,
        // the same posture as returnCustody above and revealAndDespawn below - this ONE call covers
        // EVERY stop reason (RITUAL_COMPLETE, INPUTS_EXHAUSTED, ANCHOR_LOST, PATH_BLOCKED,
        // STEP_FAILED, TOOL_CHANGED, damage, death, disconnect, shutdown, ...) with no per-reason
        // hook, because a failing step program reaches here through dispatchProgram's Failed branch.
        // Deliberately AFTER releaseAnchorClaims + returnCustody: both have already handed the
        // claims back, so the Loaded-vs-Empty read below sees the post-return truth and a remote
        // anchor darkens all the way to Empty instead of stranding a Loaded look over nothing.
        exitWorkingState(s);

        // Puppet reveal + despawn (round-4 design, doc section 4.4): the SAME unconditional-on-
        // every-exit-path posture as returnCustody above, threading the SAME nullable
        // commandBuffer (accessor-bug fix, fix round: the mutation itself must go through the
        // tick-safe commandBuffer, never a live store, from a processing context like toggle/the
        // heartbeat) - see StationPuppetController#revealAndDespawn for the full contract.
        StationPuppetController.revealAndDespawn(s, commandBuffer);

        // Native EntityEffect teardown (seam wave decision 51d): strip every session-applied
        // Presentation.Effect on EVERY exit path, mirroring returnCustody/revealAndDespawn's
        // unconditional posture. removeAll is fail-closed per entry (a gone ref/effect no-ops), and
        // clears the tracked list unconditionally. Prefer the live store when the entity is still
        // resolvable (byte-parity with releaseHold's own store-routed removeEffect); else the
        // tick-safe commandBuffer; else leave it (a disconnected/dead player's effects clear on
        // relog/respawn anyway - the same tradeoff the null-commandBuffer puppet/anchor paths accept).
        if (!s.appliedEffects.isEmpty()) {
            if (store != null) {
                s.appliedEffects.removeAll(store);
            } else if (commandBuffer != null) {
                s.appliedEffects.removeAll(commandBuffer);
            }
        }

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
                    rollCompletionLoot(s, store, commandBuffer);
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
        StationSession s = sessionOf(victimRef, store);
        if (s != null && s.interruptOnDamage) {
            stop(s, StopReason.DAMAGED, store, commandBuffer);
        }
    }

    /**
     * The live session belonging to the entity {@code ref} points at, or {@code null} when it has
     * none. ONE map lookup: {@link #byPlayer} is already keyed by player uuid, and the victim's own
     * {@code PlayerRef} component carries that uuid - the damage/death hooks used to linear-scan
     * every live session purely because they held a {@code Ref} instead of a uuid, an un-indexed
     * product of (damage-or-death events per second) x (live sessions) that grew with both axes at
     * once.
     *
     * <p>The identity re-check ({@code getStore()}/{@code getIndex()}) preserves the pre-lookup
     * semantic exactly: only the session's OWN entity counts, so a stale ref left by a respawn or a
     * relog never resolves. Never throws.
     */
    @Nullable
    private StationSession sessionOf(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (byPlayer.isEmpty()) {
            return null;
        }
        PlayerRef playerRef;
        try {
            playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        } catch (Throwable t) {
            Log.fine("STATION session lookup failed: " + t.getMessage());
            return null;
        }
        UUID uuid = playerRef != null ? playerRef.getUuid() : null;
        if (uuid == null) {
            return null;
        }
        StationSession s = byPlayer.get(uuid);
        if (s == null || s.ref == null || s.ref.getStore() != store || s.ref.getIndex() != ref.getIndex()) {
            return null;
        }
        return s;
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
        StationSession s = sessionOf(ref, store);
        if (s != null) {
            stop(s, reason, store, commandBuffer);
        }
    }

    /** Disconnect hook; no store, entity is gone. */
    public void stopFor(@Nonnull UUID playerUuid, @Nonnull StopReason reason) {
        StationSession s = byPlayer.get(playerUuid);
        if (s != null) {
            stop(s, reason, null, null);
        }
    }

    /**
     * Server shutdown: best-effort teardown of every live session, then a drain of every claim no
     * session was behind.
     *
     * <p>A claim is minted by placing input and walking away - no session required - so the session
     * sweep above can only ever reach a subset of them. The rest used to sit in the block-keyed maps
     * until the process exited, holding their entity refs with them. Draining is a pure release
     * here, not a hand-back: placed input is explicitly never persisted (a restart or crash loses an
     * in-flight claim, the documented contract this whole feature is built on), and the display
     * props are {@code NonSerialized} so they cannot outlive the process either. Touching a dying
     * world's entity store from the shutdown thread would risk a throw for something no player can
     * observe.
     */
    public void stopAll(@Nonnull StopReason reason) {
        for (StationSession s : new ArrayList<>(byPlayer.values())) {
            stop(s, reason, null, null);
        }
        int drained = forgetBlockKeyedState(key -> true);
        if (drained > 0) {
            Log.fine("STATION shutdown drained " + drained + " session-less custody claim(s)");
        }
    }

    // ==================== Owner ceilings (Settings.Limits) ====================

    /**
     * The owner's authored per-world ceilings, or {@code null} when none were authored. Read live
     * (never cached) so a settings reload takes effect on the next press, exactly like the engine
     * master switch beside it.
     */
    @Nullable
    private static RpgStationsSettingsAsset.Limits limits() {
        return SettingsCatalog.getInstance().current().getLimits();
    }

    /** True when {@code world} already runs as many sessions as the owner allows. */
    private boolean atSessionCap(@Nonnull World world) {
        RpgStationsSettingsAsset.Limits l = limits();
        return l != null && RpgStationsSettingsAsset.Limits.atCapacity(l.getMaxSessionsPerWorld(),
                () -> countLiveSessions(world, s -> true));
    }

    /** True when {@code world} already carries as many live worker doubles as the owner allows. */
    private boolean atPuppetCap(@Nonnull World world) {
        RpgStationsSettingsAsset.Limits l = limits();
        return l != null && RpgStationsSettingsAsset.Limits.atCapacity(l.getMaxPuppetsPerWorld(),
                () -> countLiveSessions(world, s -> s.puppetActive));
    }

    /** True when the world behind {@code worldPrefix} already holds as many claims as the owner allows. */
    private boolean atCustodyClaimCap(@Nonnull String worldPrefix) {
        RpgStationsSettingsAsset.Limits l = limits();
        return l != null && RpgStationsSettingsAsset.Limits.atCapacity(l.getMaxCustodyClaimsPerWorld(),
                () -> countClaimsInWorld(worldPrefix));
    }

    /**
     * How many NOT-yet-stopped sessions in {@code world} satisfy {@code match}. Counts the world's
     * own session queue rather than the global player map, so the answer is per-world by
     * construction; a stopped session still awaiting its frame drain is excluded, so the count
     * tracks what is genuinely live rather than what has not been swept yet.
     */
    private int countLiveSessions(@Nonnull World world, @Nonnull Predicate<StationSession> match) {
        ConcurrentLinkedQueue<StationSession> queued = sessionsByWorld.peek(world);
        if (queued == null) {
            return 0;
        }
        int live = 0;
        for (StationSession s : queued) {
            if (!s.stopped.get() && match.test(s)) {
                live++;
            }
        }
        return live;
    }

    /** How many custody claims stand in the world behind {@code worldPrefix}. */
    private int countClaimsInWorld(@Nonnull String worldPrefix) {
        int claims = 0;
        for (String key : custodyByBlock.keySet()) {
            if (key.startsWith(worldPrefix)) {
                claims++;
            }
        }
        return claims;
    }

    // ==================== World unload / disconnect eviction ====================

    /**
     * World-unload teardown, driven by {@code RpgStationsPlugin}'s own {@code RemoveWorldEvent}
     * listener: stop every session still running in {@code world}, then forget every block-keyed
     * entry that named it.
     *
     * <p>Every one of this engine's block-keyed maps is GLOBAL and keyed by a composite
     * {@code "<worldUuid>:<x>:<y>:<z>"} string rather than partitioned per world, so nothing about
     * unloading a world removed their entries: a fleet that creates and destroys instance worlds
     * accumulated them for the whole uptime, and each surviving claim pinned its display entity ref.
     * The world-uuid prefix the key already carries is the whole sweep.
     *
     * <p>Runs BEFORE the shared {@code WorldEvictors} fan-out at the call site, deliberately: that
     * fan-out drops the per-world session queue this method reads. Claims are released rather than
     * handed back - the world and its entity store are going away, and custody is never persisted.
     * Never throws.
     */
    public void onWorldRemoved(@Nonnull World world) {
        try {
            ConcurrentLinkedQueue<StationSession> queued = sessionsByWorld.peek(world);
            if (queued != null) {
                for (StationSession s : new ArrayList<>(queued)) {
                    try {
                        stop(s, StopReason.WORLD_CHANGED, null, null);
                    } catch (Throwable t) {
                        Log.warn("STATION world-unload session stop failed: " + t.getMessage());
                    }
                }
            }
            String worldUuid = worldUuidTextOf(world);
            if (worldUuid == null) {
                return;
            }
            String prefix = StationAnchors.worldPrefix(worldUuid);
            // Belt and braces: a session whose world queue entry was already drained is still in
            // byPlayer, and its block key names this world. stop() is idempotent, so a session
            // reached twice costs nothing.
            for (StationSession s : new ArrayList<>(byPlayer.values())) {
                if (s.blockKey != null && s.blockKey.startsWith(prefix)) {
                    try {
                        stop(s, StopReason.WORLD_CHANGED, null, null);
                    } catch (Throwable t) {
                        Log.warn("STATION world-unload session stop failed: " + t.getMessage());
                    }
                }
            }
            int forgotten = forgetBlockKeyedState(key -> key.startsWith(prefix));
            if (forgotten > 0) {
                Log.fine("STATION world unload released " + forgotten + " custody claim(s) in world "
                        + world.getName());
            }
        } catch (Throwable t) {
            Log.warn("STATION world-unload teardown failed: " + t.getMessage(), t);
        }
    }

    /**
     * The distinct world uuids that currently hold at least one custody claim owned by
     * {@code playerUuid} - what a disconnect handler needs in order to hop onto the right world
     * thread for each one. Empty (never null) when the player has no claims anywhere.
     */
    @Nonnull
    public Set<UUID> claimWorldsOf(@Nonnull UUID playerUuid) {
        Set<UUID> worlds = new LinkedHashSet<>();
        for (Map.Entry<String, StationCustodyClaim> e : custodyByBlock.entrySet()) {
            if (!e.getValue().ownerId.equals(playerUuid)) {
                continue;
            }
            String worldUuid = StationAnchors.worldUuidOf(e.getKey());
            if (worldUuid == null) {
                continue;
            }
            try {
                worlds.add(UUID.fromString(worldUuid));
            } catch (IllegalArgumentException ignored) {
                // A malformed key can never be resolved to a world; the shutdown drain reclaims it.
            }
        }
        return worlds;
    }

    /**
     * Hands back every custody claim {@code playerUuid} left standing in {@code worldUuid} - the
     * disconnect sweep for placed input with NO session behind it (place logs, walk away, log off:
     * the four existing claim-removal paths all need a session stop, a block break, or a press, so
     * none of them ever fired for that shape and both the claim and its display prop leaked for the
     * whole server uptime).
     *
     * <p><b>World thread only</b> - the caller hops via {@code World#execute} first, which is what
     * makes the display despawn and the block-state reset safe here. It holds no command buffer at
     * that point, so the despawn takes the live-{@code Store} route. Items drop at the block rather
     * than going to the owner's inventory: the owner is leaving, and a disconnecting player's
     * inventory is not a reachable destination. Never throws.
     */
    public void returnClaimsOf(@Nonnull UUID playerUuid, @Nonnull UUID worldUuid, @Nullable World world) {
        String prefix = StationAnchors.worldPrefix(worldUuid.toString());
        Store<EntityStore> store = null;
        if (world != null) {
            try {
                store = world.getEntityStore().getStore();
            } catch (Throwable t) {
                Log.fine("STATION disconnect claim sweep could not resolve a store: " + t.getMessage());
            }
        }
        for (Map.Entry<String, StationCustodyClaim> e : custodyByBlock.entrySet()) {
            String blockKey = e.getKey();
            StationCustodyClaim claim = e.getValue();
            if (!blockKey.startsWith(prefix) || !claim.ownerId.equals(playerUuid)) {
                continue;
            }
            // Whoever wins this removal owns the hand-back, exactly as returnCustody /
            // onCustodyBlockBroken already arbitrate between themselves.
            if (!custodyByBlock.remove(blockKey, claim)) {
                continue;
            }
            byBlock.remove(blockKey, playerUuid);
            try {
                StationCustodyDisplay.despawn(claim.displayRef(), store);
                giveClaimToOwner(store, null, claim, claim.blockX, claim.blockY, claim.blockZ);
                resetCustodyStateFor(world, claim);
            } catch (Throwable t) {
                Log.warn("STATION disconnect claim sweep failed at " + blockKey + ": " + t.getMessage());
            }
        }
    }

    /**
     * Resets {@code claim}'s block to its Empty custody state, resolving the same {@code Custody}
     * group the claim's own station/action authored (the {@link #retrieveCustody} flip, reused).
     * A no-op when the world is unreachable or that action authors no {@code States}.
     */
    private static void resetCustodyStateFor(@Nullable World world, @Nonnull StationCustodyClaim claim) {
        if (world == null) {
            return;
        }
        StationAsset asset = StationCatalog.getInstance().getStation(claim.stationId);
        Custody custody = asset != null ? ActionResolver.resolve(asset, claim.actionId).getCustody() : null;
        if (custody != null) {
            flipCustodyState(world, claim.blockX, claim.blockY, claim.blockZ, custody, false);
        }
    }

    /**
     * Drops every entry whose block key {@code keyMatches} - the claim map, the occupancy map, the
     * discovered-block index, and any pending picker choice made at such a block - and returns how
     * many CLAIMS went with it (the count worth logging; the rest are pure index entries).
     *
     * <p>{@code stationBlockItemToId} is deliberately NOT swept: it maps a block ITEM id to a station
     * id, is derived from the loaded assets, and is the same in every world, so a world unload has
     * nothing to say about it.
     *
     * <p>Pure bookkeeping: no entity, world, or block write happens here, because both callers are
     * teardown paths where the owning world is already going away.
     */
    private int forgetBlockKeyedState(@Nonnull Predicate<String> keyMatches) {
        int claims = 0;
        for (String key : new ArrayList<>(custodyByBlock.keySet())) {
            if (keyMatches.test(key) && custodyByBlock.remove(key) != null) {
                claims++;
            }
        }
        byBlock.keySet().removeIf(keyMatches);
        knownStationBlocks.keySet().removeIf(keyMatches);
        pendingByPlayer.values().removeIf(p -> keyMatches.test(p.blockKey()));
        return claims;
    }

    /** {@code world}'s own uuid as the text a block key carries, or {@code null} when unreadable. */
    @Nullable
    private static String worldUuidTextOf(@Nonnull World world) {
        try {
            UUID uuid = world.getWorldConfig().getUuid();
            return uuid != null ? uuid.toString() : null;
        } catch (Throwable t) {
            Log.fine("STATION could not read a world uuid for eviction: " + t.getMessage());
            return null;
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

    /**
     * Boot-time performer orphan reconcile (seam wave decision 48/55): despawn EVERY performer
     * double in {@code store} (the boot policy - no in-memory {@link StationSession} survives a
     * restart, so a persistent performer whose owning session died with the server would otherwise
     * strand forever). Wired ONCE per world at first {@code PlayerReadyEvent} by
     * {@code RpgStationsPlugin}; a transient ({@code NonSerialized}) performer is already gone at
     * boot, so this is the load-bearing sweep for a persistent performer. Inert (empty summary,
     * never throws) when {@code PerformerIdentityComponent} is not registered.
     */
    public void reconcilePerformersAtBoot(@Nonnull Store<EntityStore> store) {
        try {
            PerformerReconciler.ReconcileSummary summary =
                    PerformerReconciler.sweep(store, PerformerReconciler.bootDespawnAll());
            if (summary.despawned() > 0) {
                Log.info("STATION performer boot reconcile: despawned " + summary.despawned()
                        + " orphan performer(s) of " + summary.scanned() + " scanned");
            }
        } catch (Throwable t) {
            Log.warn("STATION performer boot reconcile failed: " + t.getMessage());
        }
    }

    /**
     * Engage-time performer orphan reconcile: despawn a stale double left AT {@code blockKey} by a
     * prior crashed session whose owner is not {@code engagingOwner}. Deferred one tick via
     * {@code world.execute} so the native performer sweep ({@code forEachEntityParallel}) runs
     * OUTSIDE {@code toggle()}'s write-processing lock; the freshly spawned own double (owned by the
     * engaging player) is KEPT by {@code engageStale}. Inert when the identity component is not
     * registered or no orphan exists.
     */
    private void reconcileStalePerformersAtEngage(@Nullable World world, @Nonnull Store<EntityStore> store,
            @Nonnull UUID engagingOwner, @Nonnull String blockKey) {
        if (world == null) {
            return;
        }
        try {
            world.execute(() -> {
                try {
                    PerformerReconciler.sweep(store, PerformerReconciler.engageStale(engagingOwner, blockKey));
                } catch (Throwable t) {
                    Log.warn("STATION performer engage reconcile failed: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            Log.warn("STATION performer engage reconcile dispatch failed: " + t.getMessage());
        }
    }

    // ==================== Convert transaction core ====================

    private enum ConversionState { RUNNABLE, NO_INPUTS, NO_ROOM }

    /**
     * One resolved conversion attempt. {@link #inputs}/{@link #outputs} are the chosen conversion's
     * FULL native-shaped arrays (decision 73), so a multi-input recipe drives the implicit program's
     * one atomic Consume/Produce phase pair rather than needing a step split.
     */
    private static final class ConversionCheck {
        final ConversionState state;
        @Nullable final Ingredient[] inputs;
        @Nullable final Ingredient[] outputs;
        /**
         * The action's {@code Recipe}, carried through so the produce phase reads its {@code Yield};
         * null for a non-runnable check or a Steps program (which has no recipe at all).
         */
        @Nullable StationAsset.Recipe recipe;
        /**
         * The chosen conversion's effective per-cycle pace override in ms (seam wave decision 52's
         * {@code Conversion.DurationMs}, incl. a baked {@code FromCrafting.NativeTime} transform), or
         * {@code <= 0} when this conversion authors none - the engine then falls to {@code
         * Work.CycleMs}. Meaningful only for a {@code RUNNABLE} check.
         */
        final long durationMs;

        /** The non-runnable shape: no chosen conversion at all. */
        ConversionCheck(ConversionState state) {
            this(state, null, null, 0L);
        }

        ConversionCheck(ConversionState state, @Nullable Ingredient[] inputs, @Nullable Ingredient[] outputs,
                        long durationMs) {
            this.state = state;
            this.inputs = inputs;
            this.outputs = outputs;
            this.durationMs = durationMs;
        }

        /** Fluent: attach the action's recipe to a RUNNABLE check. */
        @Nonnull
        ConversionCheck withRecipe(@Nullable StationAsset.Recipe recipe) {
            this.recipe = recipe;
            return this;
        }
    }

    /**
     * The CONVERSION selection for one cycle: narrow the action's ONE {@code Recipe} to the chosen
     * output category (the picker's choice, else the recipe's own first-authored default) and return
     * the first conversion whose inputs are available, carrying the recipe on the returned check so
     * the produce phase reads its {@code Yield}.
     *
     * <p>There is no tool arm here: the ACTION's {@code Tool} is the one gate, already checked at
     * engage and re-checked every heartbeat. Two transforms behind two different tools are two
     * ACTIONS, and the ordered {@code Actions} list picks between them.
     */
    @Nonnull
    private ConversionCheck selectConversion(@Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nonnull Player player,
            @Nullable StationCustodyClaim claim, boolean fromCustody, @Nullable String chosenCategory) {
        StationAsset.Recipe recipe = action.getRecipe();
        if (recipe == null) {
            return new ConversionCheck(ConversionState.NO_INPUTS);
        }
        StationAsset.Conversion[] conversions = StationCatalog.getInstance()
                .resolvedConversions(asset, action.getActionId(), recipe);
        conversions = conversionsForCategory(conversions,
                effectiveCategory(chosenCategory, recipe.getFromCrafting(), conversions));
        ConversionCheck check = fromCustody
                ? firstRunnableConversionFromCustody(claim, player, conversions)
                : firstRunnableConversion(player, conversions);
        return check.state == ConversionState.RUNNABLE ? check.withRecipe(recipe) : check;
    }

    /**
     * Every conversion the action's own {@code Recipe} resolves to, in derived order. Used by the
     * reads that ask "what can this action make/accept at all" rather than "what runs this cycle" -
     * the sneak+F picker's category strip and custody's derived acceptance matcher.
     */
    @Nonnull
    private static StationAsset.Conversion[] allConversionsFor(@Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action) {
        StationAsset.Recipe recipe = action.getRecipe();
        if (recipe == null) {
            return new StationAsset.Conversion[0];
        }
        StationAsset.Conversion[] one = StationCatalog.getInstance()
                .resolvedConversions(asset, action.getActionId(), recipe);
        return one != null ? one : new StationAsset.Conversion[0];
    }

    // ==================== Multi-output selection (selection wave, decision 50/56) ====================

    /** The sneak+F routing outcome (pure decision core, {@link #decideRoute}). */
    enum Route {
        /** No selection surface applies: run the classic engage (plain F, or a single-category station). */
        TOGGLE,
        /** Open the multi-output picker page (2+ derived output categories). */
        PICKER
    }

    /**
     * PURE (decision 50/56, re-scoped by decision 65): the sneak+F routing decision. A non-sneak
     * press always {@link Route#TOGGLE}s (plain F engages work). A sneak press opens the
     * {@link Route#PICKER} when the station derives 2+ distinct output categories, else
     * {@link Route#TOGGLE} (a single-category station never shows a picker). Unit-tested across
     * every combination.
     *
     * <p><b>Decision 65 (maintainer ruling, 2026-07-29) retired the native-bench route</b> that
     * decision 51a had put ahead of the picker here: a station block authoring a native
     * {@code BlockType.Bench} identity used to open the vanilla crafting/processing WINDOW on
     * sneak+F instead of anything of ours. The cooking fire was its only user and the maintainer
     * ruled the native window off it, so the whole branch (this enum's {@code BENCH} constant, the
     * {@code StationBenchWindow} opener, and the block's own {@code Bench}/{@code BlockEntity}
     * authoring) is gone rather than left dormant. Sneak+F is picker-or-toggle everywhere now.
     */
    @Nonnull
    static Route decideRoute(boolean sneaking, int distinctCategoryCount) {
        if (!sneaking) {
            return Route.TOGGLE;
        }
        return distinctCategoryCount > 1 ? Route.PICKER : Route.TOGGLE;
    }

    /**
     * PURE (decision 56): the conversions a session with the given chosen output category may run.
     * {@code chosenCategory} null/blank returns the input array UNCHANGED (the byte-identical
     * all-categories behavior every station has today); a set category returns only the conversions
     * whose {@code Category} matches it (case-insensitive). A null input array returns null. An
     * untagged conversion (null {@code Category}) is EXCLUDED once a category is chosen - a chosen
     * output never silently falls back to an untagged conversion.
     */
    @Nullable
    static StationAsset.Conversion[] conversionsForCategory(@Nullable StationAsset.Conversion[] all,
            @Nullable String chosenCategory) {
        if (all == null || chosenCategory == null || chosenCategory.isBlank()) {
            return all;
        }
        List<StationAsset.Conversion> kept = new ArrayList<>(all.length);
        for (StationAsset.Conversion c : all) {
            if (c != null && c.getCategory() != null && chosenCategory.equalsIgnoreCase(c.getCategory())) {
                kept.add(c);
            }
        }
        return kept.toArray(new StationAsset.Conversion[0]);
    }

    /**
     * PURE (decision 57): the EFFECTIVE output-category filter a session runs with, given the
     * player's explicit choice (if any), the resolved action's {@code FromCrafting} spec, and the
     * derived conversions. An explicit picker choice ({@code chosenCategory} non-blank) ALWAYS wins
     * verbatim. With NO explicit choice, a MULTI-category station (its derived conversions span
     * &gt;1 distinct source category) defaults to its FIRST AUTHORED priority - the first
     * {@code FromCrafting.Categories} entry that actually produced a derived conversion (the array
     * order IS the default priority; authors control the plain-F default by ordering), falling back
     * to the first derived conversion's own category when {@code FromCrafting} is absent (a purely
     * hand-authored multi-category station). A single-category or fully-untagged station returns
     * {@code null} - the byte-identical all-pass, so {@link #conversionsForCategory} leaves the
     * array untouched exactly as the pre-selection engine did (the null-with-one-category case).
     */
    @Nullable
    static String effectiveCategory(@Nullable String chosenCategory,
            @Nullable StationAsset.FromCrafting fromCrafting,
            @Nullable StationAsset.Conversion[] conversions) {
        if (chosenCategory != null && !chosenCategory.isBlank()) {
            return chosenCategory;
        }
        List<String> distinct = distinctConversionCategories(conversions);
        if (distinct.size() <= 1) {
            return null;
        }
        // Multi-category, no explicit choice: the first authored FromCrafting category that is
        // actually present in the derived set is the default (a first-authored category that
        // derived nothing must not blank out the station, so skip to the next present one).
        if (fromCrafting != null && fromCrafting.getCategories() != null) {
            for (String authored : fromCrafting.getCategories()) {
                if (authored == null || authored.isBlank()) {
                    continue;
                }
                for (String present : distinct) {
                    if (present.equalsIgnoreCase(authored)) {
                        return present;
                    }
                }
            }
        }
        return distinct.get(0);
    }

    /**
     * PURE (decision 56): the ORDERED, case-insensitively-distinct list of source-category tags
     * across {@code conversions} (untagged conversions contribute nothing). First-seen order is
     * preserved so the picker's tab strip is deterministic. Empty when nothing is tagged (a
     * single-category or fully-untagged station - no picker), size &gt; 1 gates the picker.
     */
    @Nonnull
    static List<String> distinctConversionCategories(@Nullable StationAsset.Conversion[] conversions) {
        List<String> out = new ArrayList<>();
        if (conversions == null) {
            return out;
        }
        for (StationAsset.Conversion c : conversions) {
            if (c == null || c.getCategory() == null || c.getCategory().isBlank()) {
                continue;
            }
            String cat = c.getCategory();
            boolean seen = false;
            for (String existing : out) {
                if (existing.equalsIgnoreCase(cat)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(cat);
            }
        }
        return out;
    }

    /**
     * PURE (decision 56, picker cost/name wave): the representative CONVERSION for a category -
     * the FIRST conversion in order whose {@code Category} matches and whose output resolves a
     * usable item id. Null when no tagged conversion for that category has a resolvable output
     * item id. {@link #representativeOutputFor} and {@link #pickerCostLine} both derive from this
     * SAME scan so a picker tab's icon/name and its cost line always describe ONE conversion, never
     * two independently-chosen ones.
     *
     * <p>The no-preference overload - equivalent to passing a null preferred input below, i.e. the
     * plain first-match every caller had before decision 66.
     */
    @Nullable
    static StationAsset.Conversion representativeConversionFor(@Nullable StationAsset.Conversion[] conversions,
            @Nonnull String category) {
        return representativeConversionFor(conversions, category, null, null);
    }

    /**
     * PURE (decision 66, maintainer smoke fix 2026-07-29): the representative CONVERSION for a
     * category, BIASED to the material the station actually has in front of it.
     *
     * <p>The sawmill derives one conversion per log SPECIES per category (33 in all - 11 species x
     * Planks/Decorative/Ornate), and {@code StationRecipeDeriver} sorts them by output item id, so
     * the plain first-match above always answered {@code Wood_Blackwood_*} no matter what was
     * loaded: a sawmill packed with oak logs previewed three Blackwood tabs. This overload scans
     * the same category in the same order but RETURNS the first conversion whose INPUT matches
     * {@code preferredInputItemId} (exactly, or through its {@code ResourceTypeId} family via
     * {@code preferredInputResourceTypeIds}), falling back to the first usable conversion when the
     * preferred item matches nothing in this category - so a category the loaded material cannot
     * actually produce still renders a meaningful tab instead of vanishing.
     *
     * <p>A null/blank preferred item is the byte-identical pre-decision-66 behavior. The engine's
     * own output is unaffected either way: which conversion actually RUNS is
     * {@link #firstRunnableConversionFromCustody}'s job against the live claim, and it already
     * picked the loaded species correctly - this bias only fixes what the picker DISPLAYS.
     */
    @Nullable
    static StationAsset.Conversion representativeConversionFor(@Nullable StationAsset.Conversion[] conversions,
            @Nonnull String category, @Nullable String preferredInputItemId,
            @Nullable String[] preferredInputResourceTypeIds) {
        if (conversions == null) {
            return null;
        }
        boolean wantPreferred = preferredInputItemId != null && !preferredInputItemId.isBlank();
        StationAsset.Conversion firstUsable = null;
        for (StationAsset.Conversion c : conversions) {
            if (c == null || c.getCategory() == null || !category.equalsIgnoreCase(c.getCategory())) {
                continue;
            }
            Ingredient repOut = c.primaryOutput();
            String outItem = repOut != null ? repOut.getItemId() : null;
            if (outItem == null || outItem.isBlank()) {
                continue;
            }
            if (!wantPreferred) {
                return c;
            }
            if (firstUsable == null) {
                firstUsable = c;
            }
            if (StationCustody.matchesConversionInput(c, preferredInputItemId, preferredInputResourceTypeIds)) {
                return c;
            }
        }
        return firstUsable;
    }

    /**
     * PURE (decision 56): a representative output ITEM id for a category, used as the picker tab's
     * icon. Null when {@link #representativeConversionFor} finds nothing. Thin wrapper kept for its
     * existing callers/tests; {@link #buildPickerCategories} calls {@link #representativeConversionFor}
     * directly since it needs the whole conversion, not just the output id.
     */
    @Nullable
    static String representativeOutputFor(@Nullable StationAsset.Conversion[] conversions, @Nonnull String category) {
        StationAsset.Conversion rep = representativeConversionFor(conversions, category);
        Ingredient out = rep != null ? rep.primaryOutput() : null;
        return out != null ? out.getItemId() : null;
    }

    /**
     * The picker tab's cost line (maintainer smoke fix, directive (3)): {@code "{qty}x {input} ->
     * {qty}x {output}"}, both sides a client-resolved native item-name {@link Message}
     * ({@link NativeNames#itemNameMsg}). The input side resolves through the SAME helper whether
     * the conversion authors an exact {@code ItemId} or a {@code ResourceTypeId} family (e.g. the
     * sawmill's "any Trunk of this species" input, {@code asset.Ingredient}'s exactly-one-of
     * route): a resource-type id has no native item-name key, so {@code itemNameMsg}'s own
     * existence-probe safely falls through to its prettified-raw fallback rather than handing the
     * client a broken translation key. Null when either side has no resolvable id (defensive; the
     * caller already required a resolvable OUTPUT before calling this, so this only guards a
     * missing/blank INPUT).
     */
    @Nullable
    static Message pickerCostLine(@Nonnull StationAsset.Conversion conversion) {
        Ingredient input = conversion.primaryInput();
        Ingredient output = conversion.primaryOutput();
        if (input == null || output == null) {
            return null;
        }
        String inputId = input.getItemId() != null && !input.getItemId().isBlank()
                ? input.getItemId() : input.getResourceTypeId();
        String outputId = output.getItemId();
        if (inputId == null || inputId.isBlank() || outputId == null || outputId.isBlank()) {
            return null;
        }
        return RpgMsg.tr("ui.station.picker.cost", input.effectiveQuantity(), NativeNames.itemNameMsg(inputId),
                output.effectiveQuantity(), NativeNames.itemNameMsg(outputId));
    }

    /**
     * The sneak+F selection router (decision 50/56, re-scoped by decision 65): opens the
     * multi-output picker, returning {@code true} iff it was opened (the caller then skips the
     * classic engage). Impure (it opens a page + reads the claim/catalog); the DECISION is the pure
     * {@link #decideRoute}, the FILTER math the pure {@link #distinctConversionCategories}.
     *
     * <p>Decision 65 removed the native-bench branch that used to pre-empt the picker here, so this
     * is now a straight picker-or-fall-through. {@code claim} is the block's live custody claim (may
     * be null/foreign) and feeds decision 66's placed-material preview bias below.
     */
    private boolean routeSneakSelection(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull Player player,
            @Nonnull UUID playerUuid, @Nonnull StationAsset asset, @Nonnull ActionResolver.ResolvedAction action,
            @Nullable StationCustodyClaim claim, int blockX, int blockY, int blockZ) {
        StationAsset.Conversion[] conversions = allConversionsFor(asset, action);
        List<String> categories = distinctConversionCategories(conversions);
        if (decideRoute(true, categories.size()) != Route.PICKER) {
            return false;
        }
        String blockKey = playerRef.getWorldUuid() + ":" + blockX + ":" + blockY + ":" + blockZ;
        // No engine path produces a LOCKED output category (no per-category tool gate exists), so
        // every tab renders unlocked; the page keeps its own knob for whenever one does.
        boolean showLocked = true;
        String previewInputItemId = pickerPreviewInputItemId(claim, player);
        List<PickerCategories.Category> tabs = buildPickerCategories(conversions, categories, previewInputItemId,
                liveResourceTypeIdsOf(previewInputItemId));
        if (tabs.size() < 2) {
            // Every representative output resolved empty (defensive) - nothing meaningful to show;
            // fall through to the plain engage rather than opening an empty picker.
            return false;
        }
        return RpgStationPickerPage.open(ref, store, playerRef, tabs, showLocked,
                (selRef, selStore, categoryId) -> onPickerSelect(selRef, selStore, playerUuid, blockKey, categoryId));
    }

    /**
     * The material the picker should PREVIEW recipes for (decision 66): whatever is PLACED IN THE
     * BLOCK (the custody claim's oldest-placed entry - its {@code items()} map is insertion-ordered),
     * else the player's currently HELD stack, else null (the first-derived fallback, i.e. the
     * pre-decision-66 behavior).
     *
     * <p>Deliberately NOT gated on claim ownership: the ask is "show the recipes for the block
     * placed in it", and what is physically loaded is the honest preview even when someone else
     * loaded it (a non-owner's engage is denied later by {@code toggle}'s own occupied check, which
     * this preview does not and must not pre-empt).
     */
    @Nullable
    private static String pickerPreviewInputItemId(@Nullable StationCustodyClaim claim, @Nonnull Player player) {
        if (claim != null && !claim.isEmpty()) {
            for (String itemId : claim.items().keySet()) {
                if (itemId != null && !itemId.isBlank()) {
                    return itemId;
                }
            }
        }
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
        String heldItemId = held != null ? held.getItemId() : null;
        return heldItemId != null && !heldItemId.isBlank() ? heldItemId : null;
    }

    /**
     * Build the picker's ordered tab list (one unlocked tab per category with a resolvable output
     * icon). Maintainer smoke fix: a category id is not itself a localized display name, so each
     * tab's NAME is its representative output item's own native item name ({@link
     * NativeNames#itemNameMsg}, client-resolved in the viewer's own locale - no hand-authored
     * per-category lang key needed), and its COST LINE is that same representative conversion's
     * input-to-output shape ({@link #pickerCostLine}).
     *
     * <p>Decision 66: {@code previewInputItemId} (+ its resource-type family) biases EVERY tab onto
     * the conversion that consumes the material actually loaded/held, so a sawmill full of oak
     * previews Oak Planks / Oak Decorative / Oak Ornate instead of three Blackwood tabs. One
     * preferred item drives all tabs, so the strip stays internally consistent (never oak in one
     * tab and blackwood in the next).
     */
    @Nonnull
    private static List<PickerCategories.Category> buildPickerCategories(
            @Nullable StationAsset.Conversion[] conversions, @Nonnull List<String> categories,
            @Nullable String previewInputItemId, @Nullable String[] previewInputResourceTypeIds) {
        List<PickerCategories.Category> tabs = new ArrayList<>(categories.size());
        for (String cat : categories) {
            StationAsset.Conversion rep = representativeConversionFor(conversions, cat, previewInputItemId,
                    previewInputResourceTypeIds);
            Ingredient repOutput = rep != null ? rep.primaryOutput() : null;
            if (repOutput == null) {
                continue; // no representative item to render as the tab icon; skip this category
            }
            String icon = repOutput.getItemId();
            Message name = NativeNames.itemNameMsg(icon);
            Message cost = pickerCostLine(rep);
            tabs.add(PickerCategories.Category.unlocked(cat, icon, name, cost));
        }
        return tabs;
    }

    /**
     * The picker's {@code onSelect} callback (runs on a page-event thread with no command buffer):
     * record the choice as this player's PENDING selection at that block. The player's next plain-F
     * engage at the same block reads it ({@link #peekPendingCategory}) into the new session's
     * {@code chosenOutputCategory}, and clears it only once that engage commits
     * ({@link #clearPendingCategory}). A localized toast advertises "press F to begin".
     */
    private void onPickerSelect(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull UUID playerUuid, @Nonnull String blockKey, @Nonnull String categoryId) {
        pendingByPlayer.put(playerUuid, new PendingSelection(blockKey, categoryId));
        PlayerRef pr = InventoryAccess.playerRefOf(store.getComponent(ref, Player.getComponentType()));
        if (pr != null) {
            toast(pr, RpgMsg.tr("ui.station.picker.selected"));
        }
    }

    /**
     * READ this player's pending picker choice IFF it was made at {@code blockKey} (a stale choice
     * for another block is left in place, not applied). Returns the chosen category id, or null
     * when there is no matching pending choice (the all-categories default).
     *
     * <p>Deliberately a PEEK, paired with {@link #clearPendingCategory} once an engage commits. A
     * press can still be denied for half a dozen reasons after the choice is read (no materials,
     * inventory full, world at its session ceiling, a busy or unreachable anchor, no seat), and
     * consuming the selection on the way past those denials silently threw away what the player
     * picked: their next plain-F press then ran the first-authored default instead, with nothing
     * said.
     */
    @Nullable
    private String peekPendingCategory(@Nonnull UUID playerUuid, @Nonnull String blockKey) {
        PendingSelection pending = pendingByPlayer.get(playerUuid);
        return pending != null && pending.blockKey().equals(blockKey) ? pending.category() : null;
    }

    /** Drop this player's pending picker choice once a session at {@code blockKey} has committed to it. */
    private void clearPendingCategory(@Nonnull UUID playerUuid, @Nonnull String blockKey) {
        PendingSelection pending = pendingByPlayer.get(playerUuid);
        if (pending != null && pending.blockKey().equals(blockKey)) {
            pendingByPlayer.remove(playerUuid, pending);
        }
    }

    /** PURE: a conversion's effective per-cycle pace override in ms, or {@code 0} when it authors none. */
    private static long effectiveConversionDurationMs(@Nonnull StationAsset.Conversion c) {
        Long d = c.getDurationMs();
        return d != null && d > 0 ? d : 0L;
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
            return new ConversionCheck(ConversionState.NO_INPUTS);
        }
        boolean sawInputWithoutRoom = false;
        try {
            var combined = InventoryAccess.combinedBackpackStorageHotbarOf(player);
            for (StationAsset.Conversion c : conversions) {
                if (!runnableShape(c)) {
                    continue;
                }
                // The exact-item entries are checked as ONE batch (two entries naming the same item
                // must not each pass against the same stack); resource-family entries have no batch
                // API, so they are checked individually.
                List<ItemStack> itemInputs = new ArrayList<>();
                boolean hasEveryInput = true;
                for (Ingredient in : c.getInput()) {
                    String ref = ingredientRef(in);
                    int need = in.effectiveQuantity();
                    if (isResourceRoute(in)) {
                        if (!combined.canRemoveResource(new ResourceQuantity(ref, need))) {
                            hasEveryInput = false;
                            break;
                        }
                    } else {
                        itemInputs.add(new ItemStack(ref, need));
                    }
                }
                if (!hasEveryInput || (!itemInputs.isEmpty() && !combined.canRemoveItemStacks(itemInputs))) {
                    continue;
                }
                if (!InventoryGrant.canAddAll(player, outputStacks(c))) {
                    sawInputWithoutRoom = true;
                    continue;
                }
                return new ConversionCheck(ConversionState.RUNNABLE, c.getInput(), c.getOutput(),
                        effectiveConversionDurationMs(c));
            }
        } catch (Throwable t) {
            Log.warn("STATION inventory check failed: " + t.getMessage());
        }
        return new ConversionCheck(
                sawInputWithoutRoom ? ConversionState.NO_ROOM : ConversionState.NO_INPUTS);
    }

    /**
     * PURE: is this conversion a runnable SHAPE - both sides authored, every input carrying a usable
     * route ref, every output an exact non-blank item id? A malformed conversion is skipped by both
     * runnable scans (the validator flags it at author time).
     */
    private static boolean runnableShape(@Nullable StationAsset.Conversion c) {
        if (c == null || !c.isComplete()) {
            return false;
        }
        for (Ingredient in : c.getInput()) {
            if (in == null || ingredientRef(in) == null) {
                return false;
            }
        }
        for (Ingredient out : c.getOutput()) {
            if (out == null || out.getItemId() == null || out.getItemId().isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * PURE: an ingredient's live lookup ref - its ResourceTypeId family, else its exact ItemId;
     * null when neither is authored.
     */
    @Nullable
    private static String ingredientRef(@Nullable Ingredient in) {
        if (in == null) {
            return null;
        }
        String resource = in.getResourceTypeId();
        if (resource != null && !resource.isBlank()) {
            return resource;
        }
        String item = in.getItemId();
        return item != null && !item.isBlank() ? item : null;
    }

    /** PURE: does this ingredient take the native resource-type FAMILY route rather than an exact item id? */
    private static boolean isResourceRoute(@Nullable Ingredient in) {
        return in != null && in.getResourceTypeId() != null && !in.getResourceTypeId().isBlank();
    }

    /** Every output of {@code c} as an {@link ItemStack}, for the one all-outputs-fit room check. */
    @Nonnull
    private static List<ItemStack> outputStacks(@Nonnull StationAsset.Conversion c) {
        Ingredient[] outputs = c.getOutput();
        List<ItemStack> stacks = new ArrayList<>(outputs.length);
        for (Ingredient out : outputs) {
            stacks.add(new ItemStack(out.getItemId(), out.effectiveQuantity()));
        }
        return stacks;
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
            return new ConversionCheck(ConversionState.NO_INPUTS);
        }
        boolean sawInputWithoutRoom = false;
        try {
            for (StationAsset.Conversion c : conversions) {
                if (!runnableShape(c)) {
                    continue;
                }
                boolean hasEveryInput = true;
                for (Ingredient in : c.getInput()) {
                    String ref = ingredientRef(in);
                    boolean isResource = isResourceRoute(in);
                    int have = StationCustody.available(claim, isResource ? null : ref,
                            isResource ? ref : null, StationService::liveResourceTypeIdsOf);
                    if (have < in.effectiveQuantity()) {
                        hasEveryInput = false;
                        break;
                    }
                }
                if (!hasEveryInput) {
                    continue;
                }
                if (!InventoryGrant.canAddAll(player, outputStacks(c))) {
                    sawInputWithoutRoom = true;
                    continue;
                }
                return new ConversionCheck(ConversionState.RUNNABLE, c.getInput(), c.getOutput(),
                        effectiveConversionDurationMs(c));
            }
        } catch (Throwable t) {
            Log.warn("STATION custody check failed: " + t.getMessage());
        }
        return new ConversionCheck(
                sawInputWithoutRoom ? ConversionState.NO_ROOM : ConversionState.NO_INPUTS);
    }

    // ==================== Placed-input custody (design section 9.4) ====================

    /** Package-private accessor for {@code StationStepHandlers}' {@code Consume From:"Custody"} route. */
    @Nullable
    StationCustodyClaim custodyClaimFor(@Nullable String blockKey) {
        return blockKey != null ? custodyByBlock.get(blockKey) : null;
    }

    /**
     * Is ANY live session working {@code blockKey} right now - as its primary block, or as one of
     * its claimed anchors?
     *
     * <p>{@link #byBlock} alone cannot answer this. The engage claim only writes that map for an
     * EXCLUSIVE station's primary block (a {@code Block.Exclusive: false} station legitimately
     * allows two concurrent sessions at one block, which a single-owner map cannot represent), so
     * a shared bench governed by {@code Custody} had NOTHING standing between a press-F retrieval
     * and the materials its own running session was mid-way through consuming. The occupancy map
     * stays the fast path; the live-session sweep is the correctness backstop, run once per press
     * over sessions that have not stopped - a cold path bounded by the players online.
     */
    private boolean sessionWorkingAt(@Nonnull String blockKey) {
        if (byBlock.containsKey(blockKey)) {
            return true;
        }
        for (StationSession s : byPlayer.values()) {
            if (!s.stopped.get()
                    && (blockKey.equals(s.blockKey) || s.anchorBlocks.containsValue(blockKey))) {
                return true;
            }
        }
        return false;
    }

    // ==================== Multi-station anchors (scope-2 wave 3, design 2.2/2.4/2.6) ====================

    /**
     * The refined stop reason for a {@code Consume} phase that found insufficient inputs (design
     * 2.4): a REPEATING program's shortage is the graceful natural end
     * ({@link StopReason#INPUTS_EXHAUSTED}); a non-repeating one keeps today's
     * {@link StopReason#OUT_OF_INPUTS}. Pure, unit-tested.
     */
    @Nonnull
    static StopReason shortInputStopReason(boolean repeating) {
        return repeating ? StopReason.INPUTS_EXHAUSTED : StopReason.OUT_OF_INPUTS;
    }

    /**
     * Warms the lazy station index (design 2.2/m4): records {@code blockKey -> stationId} and, when
     * {@code blockItemId} resolves, the {@code blockItemId -> stationId} learned map a place event /
     * ring scan reads back. Called from every station interaction ({@link #toggle} + the custody /
     * retrieve paths) - a station block is normally interacted with at least once, so it is "seen".
     */
    void registerKnownStationBlock(@Nonnull String blockKey, @Nonnull String stationId,
            @Nullable String blockItemId) {
        knownStationBlocks.put(blockKey, stationId);
        if (blockItemId != null && !blockItemId.isBlank()) {
            stationBlockItemToId.put(blockItemId.toLowerCase(java.util.Locale.ROOT), stationId);
        }
    }

    /**
     * A {@link PlaceBlockEvent} feed (design 2.2/m4, {@link StationBlockPlaceSystem}): when a placed
     * block's item id has been LEARNED as a station block (any prior interaction with that station
     * type), index the new block so a later anchor discovery finds it without a ring scan. A never-
     * before-interacted station type stays undiscovered-by-place until its first interaction (the
     * honest contract). No-op for a non-station item id.
     */
    void onStationBlockPlaced(@Nonnull UUID worldUuid, int x, int y, int z, @Nullable String blockItemId) {
        if (blockItemId == null || blockItemId.isBlank()) {
            return;
        }
        String stationId = stationBlockItemToId.get(blockItemId.toLowerCase(java.util.Locale.ROOT));
        if (stationId != null) {
            knownStationBlocks.put(StationAnchors.blockKey(worldUuid.toString(), x, y, z), stationId);
        }
    }

    /** Package-private for {@link StationBlockPlaceSystem} test wiring; the live count of indexed station blocks. */
    int knownStationBlockCount() {
        return knownStationBlocks.size();
    }

    /**
     * DERIVES the {@code blockItemId -> stationId} discovery index straight out of the native
     * assets, with ZERO extra authoring (maintainer ruling, AV wave). Before this existed the index
     * was LEARNED only - a press of F on a station block was the sole way an item id ever entered it
     * - so on a cold server (a restart, or a world where nobody has interacted yet) the ring scan and
     * the {@code PlaceBlockEvent} feed both missed every block and anchor discovery denied
     * {@code ui.station.anchor_missing} for old AND freshly placed station blocks alike.
     *
     * <p>The derivation is the inverse of how a station block is authored: a station block's
     * {@code BlockType.Interactions.Use} names a {@code RootInteraction} whose entries include this
     * mod's own {@code {"Type":"rpg_station_use","Station":"<id>"}}. So pass 1 walks the
     * {@link RootInteraction} asset map and collects {@code rootInteractionId -> stationId} for every
     * root interaction carrying a decoded {@link StationUseInteraction} (a small set - one per
     * station block type in every installed pack); pass 2 walks the {@link BlockType} asset map once
     * and pairs each block's own item id ({@link BlockType#getItem()}, the SAME accessor
     * {@link #blockItemIdAt} reads back in the world, so state variants fold onto their base item by
     * construction) with the root interaction its {@code Use} names. {@link StationAnchors
     * #deriveBlockItemIndex} is the pure join.
     *
     * <p>IDEMPOTENT: safe to re-run at every asset fold and once more post-load (a later pack layer
     * can add both halves). Fully try-guarded at BOTH altitudes - a malformed entry skips with one
     * warn and a total failure logs and returns, never throwing into the asset fold.
     */
    public void seedStationBlockIndexFromAssets() {
        try {
            Map<String, String> interactionToStation = new HashMap<>();
            for (Map.Entry<String, RootInteraction> e : RootInteraction.getAssetMap().getAssetMap().entrySet()) {
                try {
                    String stationId = stationIdOfRootInteraction(e.getValue());
                    if (stationId != null) {
                        interactionToStation.put(e.getKey(), stationId);
                    }
                } catch (Throwable t) {
                    Log.warn("STATION discovery seed skipped RootInteraction '" + e.getKey() + "': " + t.getMessage());
                }
            }
            if (interactionToStation.isEmpty()) {
                return;
            }
            List<StationAnchors.BlockUse> blockUses = new ArrayList<>();
            for (Map.Entry<String, BlockType> e : BlockType.getAssetMap().getAssetMap().entrySet()) {
                try {
                    BlockType blockType = e.getValue();
                    if (blockType == null) {
                        continue;
                    }
                    String useId = blockType.getInteractions().get(InteractionType.Use);
                    if (useId == null || useId.isBlank()) {
                        continue;
                    }
                    Item item = blockType.getItem();
                    if (item != null) {
                        blockUses.add(new StationAnchors.BlockUse(item.getId(), useId));
                    }
                } catch (Throwable t) {
                    Log.warn("STATION discovery seed skipped BlockType '" + e.getKey() + "': " + t.getMessage());
                }
            }
            Map<String, String> derived = StationAnchors.deriveBlockItemIndex(interactionToStation, blockUses);
            if (derived.isEmpty()) {
                return;
            }
            stationBlockItemToId.putAll(derived);
            Log.info("STATION discovery index: derived " + derived.size()
                    + " station block item id(s) from native assets: " + derived);
        } catch (Throwable t) {
            Log.warn("STATION discovery index derivation failed: " + t.getMessage(), t);
        }
    }

    /**
     * The station id a {@code RootInteraction} runs, or {@code null} when none of its interaction
     * entries is one of this mod's own {@code rpg_station_use} handlers. The engine has already
     * decoded the object form into a live {@link StationUseInteraction} (its {@code Station} leaf is
     * a real codec field), so this is an {@code instanceof} read, never a JSON re-parse.
     */
    @Nullable
    private static String stationIdOfRootInteraction(@Nullable RootInteraction root) {
        if (root == null) {
            return null;
        }
        String[] interactionIds = root.getInteractionIds();
        if (interactionIds == null) {
            return null;
        }
        for (String interactionId : interactionIds) {
            if (interactionId == null || interactionId.isBlank()) {
                continue;
            }
            Interaction interaction = Interaction.getAssetMap().getAsset(interactionId);
            if (interaction instanceof StationUseInteraction stationUse) {
                String stationId = stationUse.getStationId();
                if (stationId != null && !stationId.isBlank()) {
                    return stationId;
                }
            }
        }
        return null;
    }

    /**
     * Every station id at least one KNOWN station block resolves to (the derived seed plus anything
     * learned since). The validator reads this to flag an anchor naming a station no block maps to;
     * an EMPTY set means "not seeded yet" (a cold unit JVM, or a fold before the native asset maps
     * settled), never "nothing is discoverable" - callers must fail open on it.
     */
    @Nonnull
    java.util.Set<String> discoverableStationIds() {
        return java.util.Set.copyOf(stationBlockItemToId.values());
    }

    /**
     * Discovers the NEAREST placed block resolving to station {@code wantStationId} within
     * {@code radius} horizontal blocks of the primary block, SAME world (design 2.2): the lazy
     * index first (a cheap {@link #knownStationBlocks} scan), then ONE bounded ring scan
     * ({@link StationAnchors#ringOffsets}) over {@code world.getBlock} as a last resort. Returns the
     * discovered blockKey (also re-indexing a ring-scan hit for next time), or {@code null} when no
     * matching block is reachable within the bound.
     */
    @Nullable
    private String discoverAnchorBlock(@Nonnull World world, @Nonnull UUID worldUuid, int px, int py, int pz,
            @Nonnull String wantStationId, int radius) {
        String want = wantStationId.toLowerCase(java.util.Locale.ROOT);
        long radiusSq = (long) radius * radius;
        String worldPrefix = StationAnchors.worldPrefix(worldUuid.toString());
        // 1) the lazy index (cheap): nearest matching seen block in this world within the radius.
        String bestKey = null;
        long bestDistSq = Long.MAX_VALUE;
        for (Map.Entry<String, String> e : knownStationBlocks.entrySet()) {
            if (!e.getKey().startsWith(worldPrefix) || !want.equals(e.getValue().toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            int[] coords = StationAnchors.parseCoords(e.getKey());
            if (coords == null || (coords[0] == px && coords[1] == py && coords[2] == pz)) {
                continue; // skip the primary block itself
            }
            long distSq = StationAnchors.horizontalDistSq(px, pz, coords[0], coords[2]);
            if (distSq <= radiusSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestKey = e.getKey();
            }
        }
        if (bestKey != null) {
            return bestKey;
        }
        // 2) bounded ring scan (last resort, engage is cold): the FIRST offset (nearest-ordered)
        // whose block's item id learns back to the wanted station id wins.
        for (int[] off : StationAnchors.ringOffsets(radius, StationAnchors.SCAN_Y_SPREAD)) {
            int bx = px + off[0];
            int by = py + off[1];
            int bz = pz + off[2];
            String blockItemId = blockItemIdAt(world, bx, by, bz);
            if (blockItemId == null) {
                continue;
            }
            String stationId = stationBlockItemToId.get(blockItemId.toLowerCase(java.util.Locale.ROOT));
            if (stationId != null && want.equals(stationId.toLowerCase(java.util.Locale.ROOT))) {
                String key = StationAnchors.blockKey(worldUuid.toString(), bx, by, bz);
                knownStationBlocks.put(key, stationId); // re-index for next time
                return key;
            }
        }
        return null;
    }

    /**
     * The result of the engage-time anchor resolution+claim (design 2.2's atomic first-wins claim):
     * either {@link #ok(Map)} carrying the resolved {@code anchorId -> blockKey} map (self included),
     * or a graceful deny with a localized toast key + the station-name arg it interpolates.
     */
    private static final class AnchorResolution {
        @Nullable final Map<String, String> anchorBlocks;
        @Nullable final Message denyToast;

        private AnchorResolution(@Nullable Map<String, String> anchorBlocks, @Nullable Message denyToast) {
            this.anchorBlocks = anchorBlocks;
            this.denyToast = denyToast;
        }

        static AnchorResolution ok(@Nonnull Map<String, String> anchorBlocks) {
            return new AnchorResolution(anchorBlocks, null);
        }

        static AnchorResolution deny(@Nonnull Message denyToast) {
            return new AnchorResolution(null, denyToast);
        }

        boolean denied() {
            return denyToast != null;
        }
    }

    /**
     * Resolves + CLAIMS every declared anchor for {@code action} atomically on the world thread
     * (design 2.2/2.6, gate m5): each anchor discovers its nearest matching placed block (else
     * {@code ui.station.anchor_missing}), which must be reachable by a {@link PuppetNav} solve for
     * any anchor a {@code Walk} step targets (else {@code ui.station.anchor_unreachable} - a
     * DISTINCT toast from the not-found one, AV wave), and must not be busy with its OWN session or hold a
     * non-empty custody claim (else {@code ui.station.anchor_busy}, gate m5). On success every
     * resolved anchor's blockKey is claimed into {@link #byBlock} (first-wins) and returned; the
     * reserved {@code "self"} anchor maps to the primary block. A single-station action (no
     * {@code Anchors}) returns just {@code self}.
     *
     * <p>Runs BEFORE the session object is built, so a mid-resolution deny leaves ZERO partial
     * claims (nothing was written until every anchor validated). On success the caller writes the
     * returned map onto the session and the claims are already live in {@link #byBlock}.
     */
    @Nonnull
    private AnchorResolution resolveAndClaimAnchors(@Nonnull World world, @Nonnull UUID worldUuid,
            @Nonnull UUID playerUuid, @Nonnull ActionResolver.ResolvedAction action,
            @Nonnull List<StationStep> programSteps, @Nonnull TransformComponent transform,
            @Nonnull Store<EntityStore> store, int px, int py, int pz, @Nonnull String primaryBlockKey) {
        Map<String, ActionDef.Anchor> declared = action.getAnchors();
        Map<String, String> resolved = new java.util.LinkedHashMap<>();
        resolved.put(ActionDef.Anchor.RESERVED_SELF, primaryBlockKey);
        if (declared == null || declared.isEmpty()) {
            return AnchorResolution.ok(resolved);
        }
        // Over the EFFECTIVE program, not the raw authored one: a step an extension inserted can
        // target an anchor too, and an unchecked walk target fails mid-ritual instead of denying
        // the engage cleanly.
        java.util.Set<String> walkTargets = walkTargetAnchorIds(programSteps);
        Vector3d from = new Vector3d(transform.getPosition());
        List<String> claimed = new ArrayList<>();
        for (Map.Entry<String, ActionDef.Anchor> e : declared.entrySet()) {
            String anchorId = e.getKey();
            ActionDef.Anchor anchor = e.getValue();
            if (anchorId == null || anchorId.isBlank() || anchor == null || anchor.getStation() == null
                    || anchor.getStation().isBlank()) {
                releaseClaimed(claimed, playerUuid);
                return AnchorResolution.deny(RpgMsg.tr("ui.station.anchor_missing",
                        Msg.raw(anchorId != null ? anchorId : ""), 0));
            }
            int radius = StationAnchors.cappedRadius(anchor.effectiveMaxRadiusMeters());
            String blockKey = discoverAnchorBlock(world, worldUuid, px, py, pz, anchor.getStation(), radius);
            if (blockKey == null) {
                releaseClaimed(claimed, playerUuid);
                return AnchorResolution.deny(RpgMsg.tr("ui.station.anchor_missing",
                        anchorStationNameMsg(anchor.getStation()), radius));
            }
            // m5 precedence: refuse a block busy with its own session OR a non-empty custody claim.
            boolean busy = byBlock.containsKey(blockKey) && !playerUuid.equals(byBlock.get(blockKey));
            StationCustodyClaim custodyClaim = custodyByBlock.get(blockKey);
            boolean custodyBusy = custodyClaim != null && !custodyClaim.isEmpty()
                    && !playerUuid.equals(custodyClaim.ownerId);
            if (!StationAnchors.claimAllowed(busy, custodyBusy)) {
                releaseClaimed(claimed, playerUuid);
                return AnchorResolution.deny(RpgMsg.tr("ui.station.anchor_busy",
                        anchorStationNameMsg(anchor.getStation())));
            }
            // A walk-targeted anchor must be reachable at engage (design 2.3's per-anchor solve);
            // an unreachable walk anchor denies the whole engage gracefully (no partial claims).
            if (walkTargets.contains(anchorId.toLowerCase(java.util.Locale.ROOT))) {
                int[] coords = StationAnchors.parseCoords(blockKey);
                Vector3d to = coords != null
                        ? new Vector3d(coords[0] + 0.5, coords[1] + 1.0, coords[2] + 0.5)
                        : new Vector3d(px + 0.5, py + 1.0, pz + 0.5);
                if (PuppetNav.solve(world, store, from, to, radius) == null) {
                    releaseClaimed(claimed, playerUuid);
                    // Its OWN toast, not anchor_missing: the block WAS found, it just cannot be
                    // walked to. Telling the player "no cooking fire found within 12 blocks" while
                    // one sits in plain sight sends them looking for a station they already have.
                    return AnchorResolution.deny(RpgMsg.tr("ui.station.anchor_unreachable",
                            anchorStationNameMsg(anchor.getStation())));
                }
            }
            // Atomic first-wins claim into byBlock (the generalized occupancy map).
            UUID prior = byBlock.putIfAbsent(blockKey, playerUuid);
            if (prior != null && !prior.equals(playerUuid)) {
                releaseClaimed(claimed, playerUuid);
                return AnchorResolution.deny(RpgMsg.tr("ui.station.anchor_busy",
                        anchorStationNameMsg(anchor.getStation())));
            }
            claimed.add(blockKey);
            resolved.put(normAnchorId(anchorId), blockKey);
        }
        return AnchorResolution.ok(resolved);
    }

    /** Releases anchor blockKeys claimed so far into {@link #byBlock} for THIS player (a mid-resolution deny rollback). */
    private void releaseClaimed(@Nonnull List<String> claimed, @Nonnull UUID playerUuid) {
        for (String key : claimed) {
            byBlock.remove(key, playerUuid);
        }
    }

    /** The lowercased anchor ids any {@code Walk} step in {@code steps} targets ({@code "self"} excluded - always reachable). */
    @Nonnull
    private static java.util.Set<String> walkTargetAnchorIds(@Nullable List<StationStep> steps) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (steps == null) {
            return out;
        }
        for (StationStep step : steps) {
            StationStep.Walk walk = step != null ? step.getWalk() : null;
            String to = walk != null ? walk.getTo() : null;
            if (to != null && !to.isBlank() && !ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(to)) {
                out.add(to.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return out;
    }

    /**
     * The blockKey a step's {@code At}/{@code Walk.To} anchor id resolves to for THIS session
     * (design 2.2's custody-at-anchor + walk-target lookup): the reserved {@code "self"} (or a
     * null/blank id) is the primary block; any other id reads {@link StationSession#anchorBlocks}.
     * Returns {@code null} for an unresolved anchor id (an engine guard - the validator already
     * warned; the handler denies gracefully).
     */
    @Nullable
    static String anchorBlockKeyFor(@Nonnull StationSession s, @Nullable String anchorId) {
        if (anchorId == null || anchorId.isBlank() || ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(anchorId)) {
            return s.blockKey;
        }
        return s.anchorBlocks.get(normAnchorId(anchorId));
    }

    /**
     * The ONE anchor-id case rule (review minor: validator/runtime case divergence): anchor ids are
     * matched LOWERCASE everywhere. The validator already lowercases both the declared keys and the
     * step {@code At}/{@code Walk.To} references, so the runtime {@link StationSession#anchorBlocks}
     * map is keyed AND queried lowercase - an author declaring anchor {@code "Fire"} but referencing
     * {@code At:"fire"} passes validation and now resolves at runtime too, instead of a silent
     * {@code null} -> {@code PATH_BLOCKED}/{@code STEP_FAILED}.
     */
    @Nonnull
    private static String normAnchorId(@Nonnull String anchorId) {
        return anchorId.toLowerCase(java.util.Locale.ROOT);
    }

    /** The live custody claim at a step's {@code At} anchor (or the primary block for {@code "self"}/absent). */
    @Nullable
    StationCustodyClaim custodyClaimForAnchor(@Nonnull StationSession s, @Nullable String anchorId) {
        return custodyClaimFor(anchorBlockKeyFor(s, anchorId));
    }

    /**
     * {@code Produce.To:"Custody"} execution (scope-2 wave 3, design 2.2): stores {@code quantity}
     * of {@code itemId} into the custody claim at the step's {@code At} anchor block (the primary
     * block for {@code "self"}), creating the claim owned by this session's player when absent, and
     * spawning the placed-as-entity display via the ANCHOR station's own {@code Custody.Display}
     * when it authors one, else the running action's. Returns {@code true} when the produce landed.
     */
    boolean produceIntoCustody(@Nonnull StationSession s, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nullable String anchorId, @Nonnull String itemId, int quantity) {
        String blockKey = anchorBlockKeyFor(s, anchorId);
        if (blockKey == null || itemId.isBlank() || quantity <= 0) {
            return false;
        }
        int[] coords = anchorCoords(s, anchorId, blockKey);
        if (coords == null) {
            return false;
        }
        StationCustodyClaim claim = custodyByBlock.get(blockKey);
        if (claim == null) {
            claim = new StationCustodyClaim(s.playerUuid, s.stationId, s.actionId,
                    coords[0], coords[1], coords[2]);
            custodyByBlock.put(blockKey, claim);
        }
        claim.add(itemId, quantity);
        // Display spawn: the anchor station's own Custody.Display when it authors one, else the
        // running action's (design 2.2). Only when the claim has no live display yet.
        if (claim.displayRef() == null) {
            Custody displayCustody = anchorCustody(s, anchorId, c -> c.getDisplay() != null, true);
            Custody.Display displayGroup = displayCustody != null ? displayCustody.getDisplay() : null;
            if (displayGroup != null) {
                ItemStack visualStack = claim.uniqueStack() != null ? claim.uniqueStack() : new ItemStack(itemId, 1);
                StationCustodyDisplay.Spawned spawned = StationCustodyDisplay.spawn(commandBuffer, visualStack,
                        displayGroup, coords[0], coords[1], coords[2]);
                // Ref AND network id together, so press-F retrieval can match this prop with no
                // live component read (see StationCustodyClaim#displayNetworkId).
                claim.setDisplay(spawned != null ? spawned.ref() : null,
                        spawned != null ? spawned.networkId() : null);
            }
        }
        return true;
    }

    /**
     * The {@code Custody} group governing an anchor, for whichever nested group the caller needs:
     * for a named anchor, the ANCHOR station's own FIRST AUTHORED action's Custody (its own
     * {@code Display}/{@code States} knobs) when that Custody actually CARRIES the wanted group,
     * else the running action's Custody (always, for the reserved {@code "self"}). The first
     * authored action is the anchor station's own primary job, which is the one whose block
     * vocabulary a visiting program should honour. A best-effort lookup - a missing/unknown anchor
     * station falls back to the running action's Custody.
     *
     * <p>{@code carriesGroup} is the per-caller "does the anchor's own Custody answer this
     * question" predicate ({@code c -> c.getDisplay() != null} for the placed-as-entity visual,
     * {@code c -> c.getStates() != null} for the block-state flips) - ONE resolution rule shared by
     * both readers instead of two near-identical walks.
     *
     * <p>{@code allowRunningFallback} splits the two reader families (adversarial-verify F1): the
     * DISPLAY reader passes {@code true} - the running action's Display knobs are a sensible
     * fallback visual for a produced item at a bare anchor. The STATES readers pass {@code false} -
     * a block's state VOCABULARY belongs to its OWN station, never the running one's, so a remote
     * anchor whose station authors no {@code Custody.States} is simply never flipped (writing the
     * running action's state names there would clobber a foreign block's state with a
     * near-universally-resolvable name like {@code "Default"}). The {@code "self"} branch is
     * unconditional either way: the primary block's vocabulary IS the running action's.
     */
    @Nullable
    private Custody anchorCustody(@Nonnull StationSession s, @Nullable String anchorId,
            @Nonnull Predicate<Custody> carriesGroup, boolean allowRunningFallback) {
        if (anchorId == null || ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(anchorId)) {
            return runningActionCustody(s);
        }
        String blockKey = s.anchorBlocks.get(normAnchorId(anchorId));
        String anchorStationId = blockKey != null ? knownStationBlocks.get(blockKey) : null;
        if (anchorStationId != null) {
            StationAsset anchorAsset = StationCatalog.getInstance().getStation(anchorStationId);
            String anchorActionId = anchorAsset != null ? ActionResolver.firstActionId(anchorAsset) : null;
            if (anchorActionId != null) {
                Custody custody = ActionResolver.resolve(anchorAsset, anchorActionId).getCustody();
                if (custody != null && carriesGroup.test(custody)) {
                    return custody;
                }
            }
        }
        return allowRunningFallback ? runningActionCustody(s) : null;
    }

    /** The {@code Custody} group of the action this session is actually running (the {@code "self"} answer). */
    @Nullable
    private static Custody runningActionCustody(@Nonnull StationSession s) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        return asset != null && s.actionId != null ? ActionResolver.resolve(asset, s.actionId).getCustody() : null;
    }

    /**
     * This session's {@code Target:{Action}} extension identity (adversarial-verify F4): the ONE
     * {@link ActionResolver#actionTargetId} rule, resolved off the live catalog. {@code null} =
     * the implicit action of a no-{@code Actions} station (Action-targeted payloads deliberately
     * never reach it - the Station target is its addressing route).
     */
    @Nullable
    private static String actionTargetIdFor(@Nonnull StationSession s, @Nonnull String actionId) {
        StationAsset asset = StationCatalog.getInstance().getStation(s.stationId);
        return asset != null ? ActionResolver.actionTargetId(asset, actionId) : null;
    }

    /**
     * The world coordinates an anchor id resolves to for THIS session: the reserved {@code "self"}
     * (or a null/blank id) is the primary station block, any other id parses the already-resolved
     * {@code blockKey}. The ONE derivation every anchor-addressed call site shares
     * ({@code Produce.To:Custody}, the Working flip, the walk target) - {@code null} when the key is
     * unparseable.
     */
    @Nullable
    private static int[] anchorCoords(@Nonnull StationSession s, @Nullable String anchorId,
            @Nonnull String blockKey) {
        if (anchorId == null || anchorId.isBlank() || ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(anchorId)) {
            return new int[] {s.blockX, s.blockY, s.blockZ};
        }
        return StationAnchors.parseCoords(blockKey);
    }

    /**
     * Releases every ANCHOR block this session claimed (design 2.6, {@code stop()}'s teardown):
     * clears the {@link #byBlock} occupancy for each non-{@code self} anchor, returns any custody
     * standing at that anchor to the owner (else drops it at the block), and resets that anchor's
     * own {@code Custody.States} block state back to Empty. Skips {@code self} (the primary block's
     * own claim + custody are handled by the existing {@code stop()} paths).
     */
    private void releaseAnchorClaims(@Nonnull StationSession s, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        if (s.anchorBlocks.isEmpty()) {
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
        for (Map.Entry<String, String> e : s.anchorBlocks.entrySet()) {
            String anchorId = e.getKey();
            String blockKey = e.getValue();
            if (ActionDef.Anchor.RESERVED_SELF.equalsIgnoreCase(anchorId) || blockKey.equals(s.blockKey)) {
                continue;
            }
            byBlock.remove(blockKey, s.playerUuid);
            StationCustodyClaim claim = custodyByBlock.get(blockKey);
            if (claim != null && claim.ownerId.equals(s.playerUuid)) {
                custodyByBlock.remove(blockKey, claim);
                StationCustodyDisplay.despawn(claim.displayRef(), commandBuffer);
                giveClaimToOwner(ownerStore, s.ref, claim, claim.blockX, claim.blockY, claim.blockZ);
            }
            // Anchor block-state reset, mirroring the flip returnCustody already does for the
            // PRIMARY block. Previously absent and harmless (a remote anchor was never flipped at
            // all); load-bearing now that Custody.States.Working exists, because a program can hand
            // an anchor its Loaded look and then harvest it empty several steps later, stranding a
            // "has input" hint over nothing. Deliberately NOT gated on this session having owned a
            // claim here, but skipped when a FOREIGN claim still stands (never reset someone else's
            // Loaded look) and when the anchor authors no States at all.
            if (custodyByBlock.get(blockKey) != null) {
                continue;
            }
            Custody anchorStates = anchorCustody(s, anchorId, c -> c.getStates() != null, false);
            if (anchorStates == null) {
                continue;
            }
            int[] coords = anchorCoords(s, anchorId, blockKey);
            World world = sessionWorld(s);
            if (coords != null && world != null) {
                flipCustodyState(world, coords[0], coords[1], coords[2], anchorStates, false);
            }
        }
    }

    /**
     * Refunds the in-flight iteration's consumed inputs (design 2.5/M1, {@code stop()}'s teardown):
     * grants whatever remains in {@link StationSession#iterationConsumed} back to the owner
     * (hotbar-first via {@link ItemGrantUtil}, drop-at-block on overflow), then clears it. Empty for
     * every completed-cycle boundary (each {@code Produce.To:Custody} AND each completed program
     * cycle clears the ledger), so an orderly stop between cycles refunds NOTHING - refund and
     * custody-return stay mutually exclusive per iteration.
     */
    private void refundIterationLedger(@Nonnull StationSession s) {
        if (s.iterationConsumed.isEmpty()) {
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
        Player player = null;
        if (ownerStore != null && s.ref != null && s.ref.isValid()) {
            try {
                player = ownerStore.getComponent(s.ref, Player.getComponentType());
            } catch (Throwable ignored) {
                player = null;
            }
        }
        for (Map.Entry<String, Integer> e : s.iterationConsumed.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            ItemStack stack = new ItemStack(e.getKey(), e.getValue());
            try {
                if (player != null) {
                    ItemGrantUtil.grant(player, stack, null, ownerStore, s.blockX, s.blockY, s.blockZ);
                } else {
                    ItemDropUtil.dropAtBlock(null, ownerStore, s.blockX, s.blockY, s.blockZ, List.of(stack));
                }
            } catch (Throwable t) {
                Log.warn("STATION iteration refund failed for '" + e.getKey() + "': " + t.getMessage());
            }
        }
        s.iterationConsumed.clear();
    }

    /**
     * Resolves the target anchor's block-top column point (scope-2 wave 3, design 2.3): the reserved
     * {@code "self"}/null anchor targets the primary block, any other id reads
     * {@link StationSession#anchorBlocks}. Returns the block-centred column point (the walk goal), or
     * {@code null} when the anchor is unresolved (the handler maps a null to a graceful
     * {@link StopReason#PATH_BLOCKED} stop). The actual PATH SOLVE now lives behind the performer
     * seam (decision 55, F2): {@code StationPerformer.walkTo} takes this target and re-solves the
     * bounded-A* path from the puppet's own current position under the hood (the Holder backend
     * still uses {@code PuppetNav}, byte-parity), so this method no longer reads the puppet or solves
     * a path itself. WORLD-THREAD ONLY.
     */
    @Nullable
    Vector3d resolveWalkTarget(@Nonnull StationSession s, @Nullable String targetAnchorId) {
        if (s.ref == null || !s.ref.isValid()) {
            return null;
        }
        String blockKey = anchorBlockKeyFor(s, targetAnchorId);
        if (blockKey == null) {
            return null;
        }
        int[] coords = anchorCoords(s, targetAnchorId, blockKey);
        if (coords == null) {
            return null;
        }
        return new Vector3d(coords[0] + 0.5, coords[1] + 1.0, coords[2] + 0.5);
    }

    // ==================== Iteration refund ledger recorders (design 2.5/M1) ====================

    /** Records a REAL exact-item consume into the iteration refund ledger. */
    static void recordIterationConsumedItem(@Nonnull StationSession s, @Nonnull String itemId, int quantity) {
        if (quantity > 0) {
            s.iterationConsumed.merge(itemId, quantity, Integer::sum);
        }
    }

    /** Records the REAL drained ids of a {@code ResourceTypeId} inventory consume into the iteration ledger. */
    static void recordIterationConsumedResource(@Nonnull StationSession s, @Nullable ResourceTransaction tx,
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
        tallyConsumedResource(s.iterationConsumed, slots, resourceTypeId);
    }

    /** Records a custody drain's REAL drained ids into the iteration ledger. */
    static void recordIterationConsumedMap(@Nonnull StationSession s, @Nonnull Map<String, Integer> realIds) {
        for (Map.Entry<String, Integer> e : realIds.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                s.iterationConsumed.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
    }

    /**
     * The M1 single rule (design 2.5, extended for review minor m1): ANY committed produce - a
     * {@code Produce.To:"Custody"} OR a {@code Produce.To:"Inventory"} - clears the ENTIRE current
     * iteration's consumed ledger. The consumed inputs BECAME the produced output (handed back by
     * {@code returnCustody} for a custody produce, already in the player's inventory for an
     * inventory produce), so refund and the committed output stay mutually exclusive per iteration.
     * Without the inventory case a {@code Consume + Produce(To:Inventory) + Duration} step stopped
     * mid-{@code Duration} would refund the consumed inputs while the produced item is already in
     * the inventory - a double-grant.
     */
    static void clearIterationLedgerOnCommittedProduce(@Nonnull StationSession s) {
        s.iterationConsumed.clear();
    }

    /**
     * True when {@code held} satisfies {@code action}'s custody placement matcher: an explicit
     * {@link Custody#getInput()} when authored, else ANY of the RESOLVED action's
     * {@code Recipe.Conversions} inputs (the sawmill's "logs by ResourceTypeId family" - zero
     * extra authoring; an action with no {@code Recipe} at all must author an explicit
     * {@link Custody#getInput()} instead).
     */
    private static boolean custodyAccepts(@Nonnull Custody custody, @Nonnull StationAsset asset,
            @Nonnull ActionResolver.ResolvedAction action, @Nullable ItemStack held,
            @Nullable StationCustodyClaim claim) {
        if (held == null || held.isEmpty()) {
            return false;
        }
        String heldItemId = held.getItemId();
        String[] heldResourceTypeIds = liveResourceTypeIdsOf(heldItemId);
        // Decision 74: SingleFamily locks a non-empty claim to its first-placed material's family.
        // Checked FIRST so it gates both acceptance routes below with one statement.
        if (!StationCustody.acceptsFamily(custody.effectiveSingleFamily(), claim, heldItemId, heldResourceTypeIds,
                StationService::liveResourceTypeIdsOf)) {
            return false;
        }
        var matcher = custody.getInput();
        if (matcher != null) {
            return StationCustody.matchesInput(matcher, heldItemId, heldResourceTypeIds, liveRawTagsOf(heldItemId),
                    liveFunctionOf(heldItemId));
        }
        StationAsset.Conversion[] conversions = allConversionsFor(asset, action);
        return conversions.length > 0
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
            @Nonnull ActionResolver.ResolvedAction action, short skipSlot,
            @Nullable StationCustodyClaim claim) {
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
                if (custodyAccepts(custody, asset, action, stack, claim)) {
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
            StationCustodyDisplay.Spawned spawned = StationCustodyDisplay.spawn(commandBuffer, visualStack,
                    displayGroup, blockX, blockY, blockZ);
            // Both halves land together: the ref AND the network id the prop was built with, so
            // press-F retrieval never has to read a live NetworkId component back off the entity.
            claim.setDisplay(spawned != null ? spawned.ref() : null,
                    spawned != null ? spawned.networkId() : null);
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
                if (ItemGrantUtil.grant(player, stack, null, ownerStore, blockX, blockY, blockZ)
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
     * scoped to the PRESSER'S OWN WORLD because a network id is per-world and repeats across
     * worlds ({@link StationCustodyRetrieval#owns}), then routes the eligibility decision through
     * {@link StationCustodyRetrieval#decide}:
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
            UUID worldUuid = playerRef.getWorldUuid();
            if (playerUuid == null || worldUuid == null) {
                return;
            }
            NetworkId targetNetworkId = store.getComponent(targetEntity, NetworkId.getComponentType());
            if (targetNetworkId == null) {
                return;
            }
            // WORLD-SCOPED match (see StationCustodyRetrieval#owns): a network id comes from a
            // per-world counter that starts at 1 in every world, so an unscoped comparison can
            // resolve a claim in a DIFFERENT world and hand over its contents. The block key already
            // encodes the world uuid, so the presser's own prefix is the whole guard - and because
            // each claim carries the id its prop was built with, this walk reads no components at
            // all (it used to fetch NetworkId off every claim's entity, across every world, per
            // press).
            String worldPrefix = StationAnchors.worldPrefix(worldUuid.toString());
            int targetId = targetNetworkId.getId();
            String blockKey = null;
            for (Map.Entry<String, StationCustodyClaim> e : custodyByBlock.entrySet()) {
                if (StationCustodyRetrieval.owns(e.getKey(), worldPrefix, e.getValue().displayNetworkId(), targetId)) {
                    blockKey = e.getKey();
                    break;
                }
            }
            StationCustodyClaim claim = blockKey != null ? custodyByBlock.get(blockKey) : null;
            boolean hasActiveSession = blockKey != null && sessionWorkingAt(blockKey);
            boolean isOwner = claim != null && claim.ownerId.equals(playerUuid);
            boolean claimNonEmpty = claim != null && !claim.isEmpty();
            StationCustodyRetrieval.Outcome outcome =
                    StationCustodyRetrieval.decide(claim != null, hasActiveSession, isOwner, claimNonEmpty);
            if (outcome == StationCustodyRetrieval.Outcome.RETRIEVE) {
                // The presser's own collect/gather gesture (maintainer directive, round-3 smoke):
                // fired BEFORE the give-back so the animation set still reflects what they were
                // holding when they reached for it, not whatever the retrieved stack just became.
                playCollectAnimation(store, ref);
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
     * The native player COLLECT/gather gesture clip fired on a successful custody retrieval - the
     * engine's own reach-out pose, {@code Characters/Animations/Default/Interact.blockyanim}
     * (third person) / {@code Interact_FPS.blockyanim} (first person).
     *
     * <p><b>Why {@code "Interact"}:</b> the engine ships NO clip literally named Collect/Gather/
     * Pickup - enumerating every animation key across all 38 {@code ItemPlayerAnimations} catalogs
     * ({@code HytaleAssets/Server/Item/Animations/*.json}) turns up none, and the {@code Collector}
     * types in the interaction package are codec collection helpers, not animations.
     * {@code "Interact"} is the reach-out gesture native interactions themselves use for exactly
     * this shape of moment (22 {@code Effects.ItemAnimationId: "Interact"} sites across the vanilla
     * interaction assets, e.g. {@code Item/Interactions/Crops/Seed_Place.json} and the door set),
     * and it is present in the item-agnostic {@code Default}/{@code Item}/{@code Block} catalogs, so
     * it resolves whatever the presser happens to be holding.
     *
     * <p>An ENGINE constant, deliberately not authored content (this is one fixed engine gesture,
     * not a per-station knob): retune by editing this one line, or set it {@code null}/blank to
     * disable the gesture entirely.
     */
    @Nullable
    private static final String COLLECT_ANIMATION_CLIP = "Interact";

    /**
     * Plays {@link #COLLECT_ANIMATION_CLIP} on the RETRIEVING player's own {@code Action} slot -
     * the same mechanism {@code StationHoldController#playActionSwing} rides (the currently HELD
     * item's own {@code ItemPlayerAnimations} clip set, falling back to the engine's item-agnostic
     * {@code Default} set for an empty hand or an item with no set), with {@code sendToSelf=true}
     * so the presser sees their OWN gesture and not just the bystanders.
     *
     * <p>Fully try-guarded and fire-and-forget: a missing player component, an unreadable hotbar,
     * or any throw degrades to no gesture at all, never to a failed retrieval. Called ONLY from
     * {@link #retrieveCustody}'s {@code RETRIEVE} branch, so every denial/no-op path is untouched.
     * The animation is a network packet (not a component mutation), so it correctly routes through
     * the live {@code store}, exactly as {@code StationHoldController}'s two animation calls do.
     */
    private static void playCollectAnimation(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        String clip = COLLECT_ANIMATION_CLIP;
        if (clip == null || clip.isBlank()) {
            return;
        }
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            ItemStack held = player != null ? InventoryAccess.activeHotbarItemOf(player) : null;
            Item item = held != null ? held.getItem() : null;
            String itemAnimationsId = item != null ? item.getPlayerAnimationsId() : null;
            if (itemAnimationsId == null || itemAnimationsId.isBlank()) {
                itemAnimationsId = ItemPlayerAnimations.DEFAULT_ID;
            }
            AnimationUtils.playAnimation(ref, AnimationSlot.Action, itemAnimationsId, clip, true, store);
        } catch (Throwable t) {
            Log.fine("STATION retrieve collect animation failed: " + t.getMessage());
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
        // The broken block is no longer a discoverable station block (scope-2 wave 3, gate m4).
        knownStationBlocks.remove(blockKey);
        // FIRST: drop the broken block's OWN custody at the block (design 2.6's "broken block's
        // custody drops at that block"), removing the claim so a following ANCHOR_LOST stop's
        // anchor sweep does not also try to return it to inventory (mutually exclusive by removal).
        StationCustodyClaim claim = custodyByBlock.remove(blockKey);
        if (claim != null) {
            StationCustodyDisplay.despawn(claim.displayRef(), commandBuffer);
            if (!claim.isEmpty()) {
                dropCustodyAtBlock(store, x, y, z, claim.toItemStacks());
            }
        }
        // THEN: if this block was a REMOTE anchor of a live session, stop it gracefully - its OTHER
        // anchors auto-return + its in-flight iteration refunds through the one stop() funnel.
        stopSessionOwningAnchor(blockKey, store, commandBuffer);
    }

    /**
     * Stops the session that CLAIMED {@code blockKey} as a REMOTE anchor (design 2.6's ANCHOR_LOST):
     * resolves the owner via the generalized {@link #byBlock} map and stops with
     * {@link StopReason#ANCHOR_LOST}. A no-op when the block is the session's PRIMARY block (the
     * heartbeat's block-gone check owns that, {@link StopReason#STATION_GONE}) or nothing claims it.
     */
    private void stopSessionOwningAnchor(@Nonnull String blockKey, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID owner = byBlock.get(blockKey);
        if (owner == null) {
            return;
        }
        StationSession s = byPlayer.get(owner);
        if (s == null || s.stopped.get() || blockKey.equals(s.blockKey)) {
            return;
        }
        if (s.anchorBlocks.containsValue(blockKey)) {
            stop(s, StopReason.ANCHOR_LOST, store, commandBuffer);
        }
    }

    /**
     * Drops {@code stacks} at the block's center via the shared {@link ItemDropUtil} sink
     * (SMOKE-FIX S3 (b) lifted this out to a mod-wide utility so {@code loot.LootEngine}'s luck/
     * tier grants reuse the SAME world-drop mechanism instead of re-deriving it).
     */
    private static void dropCustodyAtBlock(@Nullable Store<EntityStore> store, int x, int y, int z,
            @Nonnull List<ItemStack> stacks) {
        ItemDropUtil.dropAtBlock(null, store, x, y, z, stacks);
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
        setBlockState(world, x, y, z, toLoaded ? states.getLoaded() : states.getEmpty());
    }

    /**
     * The ONE raw block-interaction-state write in this engine (extracted from
     * {@link #flipCustodyState} so the Working flip below shares the exact same guards rather than
     * re-deriving them): a null/blank name, a block that is gone, or a name the block's own
     * {@code State.Definitions} never authored all no-op (a state variant is a DISTINCT generated
     * BlockType key - an unauthored name has nothing to resolve to). Returns {@code true} only when
     * the write actually went through, so a caller can decide whether to REMEMBER the flip.
     */
    private static boolean setBlockState(@Nonnull World world, int x, int y, int z, @Nullable String stateName) {
        if (stateName == null || stateName.isBlank()) {
            return false;
        }
        try {
            BlockType bt = world.getBlockType(x, y, z);
            if (bt == null || bt.getData() == null || bt.getBlockForState(stateName) == null) {
                return false;
            }
            world.setBlockInteractionState(new Vector3i(x, y, z), bt, stateName);
            return true;
        } catch (Throwable t) {
            Log.fine("STATION block state flip to '" + stateName + "' failed: " + t.getMessage());
            return false;
        }
    }

    // ==================== The actively-working block state (Custody.States.Working) ====================

    /**
     * Puts the block a work step runs AT into its {@code Custody.States.Working} look, for as long
     * as that work is genuinely running there. The semantic is "actively working", NOT "has input in
     * it" - the cooking fire's burning look lives on this state, so raw fish sitting on a cold fire
     * leaves it unlit until the cook beat begins.
     *
     * <p>Covers BOTH altitudes with one call, because it resolves through the SAME
     * {@link #anchorBlockKeyFor} the step phases already use: an absent/{@code "self"} anchor is the
     * primary station block (the cooking fire's own plain-F convert loop), any other id is the
     * claimed remote anchor (the cutting board's fish program lighting the fire it walked to).
     *
     * <p>IDEMPOTENT per block: re-entering the SAME block never re-writes the state, so the implicit
     * convert program (one working step re-dispatched every cycle) holds a steady look instead of
     * flickering once per cycle. Entering a DIFFERENT block exits the previous one first, so at most
     * one block per player is ever left working. A no-op when the resolved {@code Custody} authors
     * no {@code Working} name - every pre-knob station is byte-identical.
     */
    void enterWorkingState(@Nonnull StationSession s, @Nullable String anchorId) {
        String blockKey = anchorBlockKeyFor(s, anchorId);
        if (blockKey == null) {
            return;
        }
        WorkingFlip live = workingByPlayer.get(s.playerUuid);
        if (live != null && live.blockKey().equals(blockKey)) {
            return;
        }
        exitWorkingState(s);
        Custody custody = anchorCustody(s, anchorId, c -> c.getStates() != null, false);
        Custody.States states = custody != null ? custody.getStates() : null;
        if (states == null || states.getWorking() == null || states.getWorking().isBlank()) {
            return;
        }
        int[] coords = anchorCoords(s, anchorId, blockKey);
        if (coords == null) {
            return;
        }
        World world = sessionWorld(s);
        if (world == null) {
            return;
        }
        if (setBlockState(world, coords[0], coords[1], coords[2], states.getWorking())) {
            workingByPlayer.put(s.playerUuid, new WorkingFlip(blockKey, anchorId, coords[0], coords[1], coords[2]));
        }
    }

    /**
     * Takes whatever block this player's session left in its Working look back OUT of it - to
     * {@code Loaded} when a custody claim still stands there, else {@code Empty}. Idempotent (a
     * session with no live flip no-ops), so it can be called freely from every "work is no longer
     * running here" moment: the step engine calls it on entering any non-working step and on
     * entering a {@code Walk} phase, and {@link #stop} calls it unconditionally so EVERY exit path
     * (re-press, crouch, walk-off, tool changed, damage, death, disconnect, world change, shutdown,
     * {@code RITUAL_COMPLETE}, {@code INPUTS_EXHAUSTED}, {@code ANCHOR_LOST}, {@code PATH_BLOCKED},
     * {@code STEP_FAILED}, ...) darkens it - a step-program failure reaches {@link #stop} through
     * {@code dispatchProgram}'s Failed branch, so no reason needs its own hook here.
     */
    void exitWorkingState(@Nonnull StationSession s) {
        WorkingFlip flip = workingByPlayer.remove(s.playerUuid);
        if (flip == null) {
            return;
        }
        Custody custody = anchorCustody(s, flip.anchorId(), c -> c.getStates() != null, false);
        if (custody == null) {
            return;
        }
        // A gone entity (disconnect/shutdown) has no world to write through: the flip is dropped
        // here and the block is left wearing its Working look until the next interaction, which is
        // EXACTLY the pre-existing restart-orphan story for the Loaded state - toggle()'s
        // not-loaded self-heal resets it idempotently (custody is never persisted by construction).
        World world = sessionWorld(s);
        if (world == null) {
            return;
        }
        StationCustodyClaim claim = custodyByBlock.get(flip.blockKey());
        flipCustodyState(world, flip.x(), flip.y(), flip.z(), custody, claim != null && claim.totalQuantity() > 0);
    }

    /** This session's world, or {@code null} when its entity is gone (a shutdown/disconnect stop). */
    @Nullable
    private static World sessionWorld(@Nonnull StationSession s) {
        if (s.ref == null || !s.ref.isValid()) {
            return null;
        }
        try {
            return WorldEvictors.worldOf(s.ref);
        } catch (Throwable ignored) {
            return null;
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
     * The action-selection choke point: walks {@code asset}'s ORDERED {@code Actions} list and
     * returns the first action whose {@code Select} is absent or matches the player's CURRENTLY HELD
     * active-hotbar stack (item id, EVERY resolved resource-type family, native raw tags, and the
     * functional route - {@link #liveFunctionOf}). {@code null} when nothing matches, or when the
     * station authors no actions at all.
     */
    @Nullable
    private static String selectActionForHeld(@Nonnull StationAsset asset, @Nonnull Player player) {
        ItemStack held = InventoryAccess.activeHotbarItemOf(player);
        String heldItemId = held != null ? held.getItemId() : null;
        return ActionResolver.selectActionByFamily(asset, heldItemId, liveResourceTypeIdsOf(heldItemId),
                liveRawTagsOf(heldItemId), liveFunctionOf(heldItemId));
    }

    /**
     * The held item's FUNCTIONAL route (design 9.1's {@code ActionInput.Function}, phase 2 leg E -
     * previously schema-only, resolved for the first time here): {@code "Weapon"}/{@code "Armor"}/
     * {@code "Tool"} tested against the live {@link Item} shape - the item's own weapon/armor/tool
     * group is the whole test, no registry involved. {@code null} when unresolvable or none apply.
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
     * condition must pass (the shared array evaluator, which names the first factor that shut the
     * gate so the deny can be logged usefully), resolved against a fresh pre-session
     * {@link FactorContext} (the api {@link FactorRegistryImpl} - a degenerate context since no
     * session exists yet: {@code sessionSeconds}/{@code cycleIndex} 0, held-tool power/durability
     * not read here since no shipped station authors a tool-power gate condition; a player-standing
     * condition needs only {@code playerId}). A null {@code reqs} always passes.
     *
     * <p>Called TWICE at engage - once with the station's own entry gate and once with the selected
     * action's - because the two are ANDed and neither defaults the other. {@code action} anchors the
     * context either way: the running action owns the contribution channels a condition may read,
     * extension-appended entries included.
     */
    private static boolean checkRequires(@Nullable Requires reqs, @Nonnull PlayerRef playerRef,
            @Nonnull StationAsset asset, @Nonnull ActionResolver.ResolvedAction action) {
        if (reqs == null || reqs.isEmpty()) {
            return true;
        }
        String permission = reqs.getPermission();
        if (permission != null && !permission.isBlank() && !playerRef.hasPermission(permission)) {
            return false;
        }
        FactorCondition[] conditions = reqs.getConditions();
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
                .actionId(action.getActionId())
                .sessionSeconds(0L)
                .cycleIndex(0)
                .contributions(contributionParams(asset.getId(),
                        ActionResolver.actionTargetId(asset, action.getActionId()), action.getWork()))
                .build();
        String failed = FactorRegistryImpl.getInstance().firstFailedCondition(conditions, ctx);
        if (failed != null) {
            if (!FactorRegistryImpl.getInstance().isKnown(failed)) {
                Log.warn("STATION Requires condition references unknown factor '" + failed
                        + "' - denying (fail closed)");
            } else {
                Log.fine("STATION Requires condition on factor '" + failed + "' not met - denying");
            }
            return false;
        }
        return true;
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
     * The display-name {@link Message} for a station id referenced only by id (an anchor's target
     * station, scope-2 wave 3): the resolved asset's own {@code Identity.NameKey} when the station
     * is loaded, else the {@code rpgstations.station.<id>.name} convention key - so an anchor-deny
     * toast names the OTHER station the same way {@link #stationNameMsg} names the primary one.
     */
    @Nonnull
    private static Message anchorStationNameMsg(@Nullable String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return Msg.raw("");
        }
        StationAsset asset = StationCatalog.getInstance().getStation(stationId);
        if (asset != null) {
            return stationNameMsg(asset);
        }
        return Msg.key("rpgstations.station." + stationId.toLowerCase(java.util.Locale.ROOT) + ".name");
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
                line = Msg.cat(line, Msg.raw(" "), RpgMsg.tr("ui.station.summary.lucky")).color(GOLD);
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
            case INPUTS_EXHAUSTED -> "ui.station.stop.inputs_exhausted";
            case ANCHOR_LOST -> "ui.station.stop.anchor_lost";
            case PATH_BLOCKED -> "ui.station.stop.path_blocked";
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
