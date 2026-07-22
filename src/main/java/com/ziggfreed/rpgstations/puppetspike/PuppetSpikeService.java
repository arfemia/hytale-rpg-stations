package com.ziggfreed.rpgstations.puppetspike;

import java.awt.Color;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.entity.PlayerModelService;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.station.StationService;
import com.ziggfreed.rpgstations.util.Log;

/**
 * <b>TEMPORARY P0 SPIKE HARNESS</b> - the puppet-presentation route's binding gate (design doc
 * {@code .claude/research/raw/rpg-stations-puppet-presentation-design-2026-07-22.md} section 5
 * leg P0, decision log {@code .claude/research/rpg-stations-extraction-design.md} item 11).
 * Never wired into a real {@code station.StationSession} - this is an admin-only tool
 * (<code>/rpgstations puppet &lt;scale|modelswap|hidden|show|off&gt;</code>, see {@code
 * command.RpgStationsCommand}) so the maintainer can observe the three candidate self-hide
 * routes against a live skinned puppet in ONE game session and pick a winner (or fall back to
 * the {@code Hide.Route: "None"} degraded scope). DELETE this whole {@code puppetspike} package
 * once the spike verdict lands and legs P1-P6 supersede it with the real {@code
 * StationAsset.Puppet} knob family.
 *
 * <p><b>What each route does, ON ANY route arg ({@code scale}/{@code modelswap}/{@code
 * hidden}):</b> (1) spawns a skinned PUPPET ~2 blocks in front of the caller - a bare networked
 * entity carrying a COPY of the caller's live {@link PlayerSkin} (the copy ctor, the proven
 * {@code /npc spawn --randommodel} shape - {@code
 * hytale-shared-source/HytaleServer/NPC/.../commands/NPCSpawnCommand.java}), repeating the
 * Hatchet-family {@code "Chop"} swing clip on a beat loop on the {@code Action} slot (mirroring
 * {@code station.StationHoldController#playActionSwing}'s exact resolution - it only plays if
 * the caller is holding a Hatchet-family tool; see {@link #startAnimationBeat}'s own javadoc for
 * the round-4 fix that made this observable at all), and holding
 * a copy of the caller's held item via a bare {@link InventoryComponent.Hotbar} (the generic
 * "any entity carrying Hotbar/Armor/Utility + Visible gets networked equipment" mechanism -
 * {@code InventorySystems.SyncEquipmentSystem}'s query is component-only, no player-type gate).
 * (2) applies the chosen SELF-HIDE route to the CALLER. Every application logs a {@code
 * [PUPPETSPIKE]} line with the key values.
 *
 * <p><b>Spawn mechanism note:</b> {@link #spawnPuppet} calls {@code store.addEntity} DIRECTLY
 * (never a {@link com.hypixel.hytale.component.CommandBuffer}) because this whole harness only
 * ever runs from a command's {@link World#execute} hop - NOT from inside an interaction/tick
 * handler's store-processing lock, the context that forces {@code station.StationCustodyDisplay}/
 * {@code station.StationEntityMountController} onto a {@code CommandBuffer}. This mirrors the
 * exact context {@code NPCSpawnCommand} itself calls {@code store.addEntity} from
 * ({@code NPCPlugin.spawnEntity}), not a new assumption.
 *
 * <p><b>Round-4 harness-bug fix wave (2026-07-22):</b> three maintainer in-game findings fixed
 * in place (spike verdict + round-4 queue, {@code
 * .claude/plans/work-stations-mod-extraction-prompt.md}). (1) CRITICAL: {@code /rpgstations
 * puppet off} teleported the caller. Root-cause: the strongest available evidence ties this to
 * the {@code "hidden"} route's {@link #applyHidden}/{@link #undoHide} self-hide-from-self call
 * (this class's OWN pre-existing javadoc on {@link #applyHidden} already flagged it
 * "untested-but-technically-possible... against engine intent"), corroborated by the maintainer's
 * independent round-4 verdict ("hidden route retired... buggy in their test", puppet design doc
 * section 1's "THE crux") and by the evidence log's timeline (the observed teleport's {@code off}
 * call reverted exactly this route). {@link com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager}
 * itself is a pure {@code Set<UUID>} (verified via the shared source - no positional side
 * effect), so the actual corruption happens in a downstream client/self-view resync system this
 * class cannot reach or prove from server source alone. Fix: {@link #revertFor} now wraps every
 * revert in a defensive position snapshot/restore guard ({@link #snapshotPosition}/{@link
 * #restorePositionIfDrifted}, decision core {@link #positionDrifted}) - this converts "silently
 * teleports" into "cannot move the caller, logged for further diagnosis" regardless of the exact
 * downstream mechanism, and despawns the puppet BEFORE the hide-undo runs (the safe operation
 * first). (2) {@code off} left the puppet alive (only a later {@code show}/route-switch actually
 * despawned it): {@link #revertFor} and {@link #show} now report an ACCURATE
 * {@code puppetDespawned} flag (the old {@code show} log unconditionally claimed "puppet
 * despawned" even when nothing was tracked to despawn), and {@link #despawnPuppetRef}'s failure
 * log was raised from {@code fine} (invisible at normal log levels) to {@code warn} so a future
 * despawn failure is no longer silent. (3) The swing clip fired ONCE, synchronously, in the same
 * world-thread hop that just called {@code store.addEntity} - before the entity's tracker
 * registration could mark it visible to any viewer, so {@code
 * AnimationUtils.playAnimation}'s {@code PlayerUtil.forEachPlayerThatCanSeeEntity} filter
 * (hytale-shared-source {@code AnimationUtils.java:80-84}) found zero qualifying viewers and
 * silently dropped the packet. Fixed by {@link #startAnimationBeat} - see its own javadoc for
 * the render-guaranteed {@link ActiveAnimationComponent} route this also pre-seeds.
 */
public final class PuppetSpikeService {

    private static final PuppetSpikeService INSTANCE = new PuppetSpikeService();

    @Nonnull
    public static PuppetSpikeService getInstance() {
        return INSTANCE;
    }

    private final ConcurrentHashMap<UUID, PuppetSpikeState> byPlayer = new ConcurrentHashMap<>();

    /** "Near-zero", not literally zero - avoids a stray divide-by-zero downstream. */
    private static final float NEAR_ZERO_SCALE = 0.01f;

    private static final double PUPPET_FORWARD_OFFSET_METERS = 2.0;

    /**
     * The Hatchet family's own swing clip - the exact id {@code
     * station.StationHoldController#DEFAULT_ACTION_CLIP} uses (that constant is package-private
     * to {@code station}, so this harness keeps its own copy rather than widening its access for
     * a temporary spike).
     */
    private static final String PUPPET_CLIP_ID = "Chop";

    /**
     * The swing re-fire cadence (round-4 harness-bug fix 3) - matches the station engine's own
     * per-swing timer convention, e.g. the shipped {@code Sawmill.json}'s {@code
     * Animation.Swing.IntervalMs: 933}.
     */
    private static final long ANIMATION_BEAT_MS = 933L;

    /**
     * The CRITICAL revert-teleport guard's tolerance (round-4 harness-bug fix 1): a legitimate
     * settle (gravity, a component-driven micro-nudge) stays well under half a block; a "random
     * coordinates" teleport does not.
     */
    private static final double TELEPORT_GUARD_EPSILON_METERS = 0.5;

    /**
     * One shared daemon scheduler backing every puppet's swing-beat re-fire ({@link
     * #startAnimationBeat}) - mirrors {@code ziggfreed-common}'s own {@code
     * lobby.DelayScheduler} production shape (a single daemon {@code ScheduledExecutorService},
     * each beat hopping onto the world thread via {@code World#execute} before touching the
     * store). A temporary-spike-scoped instance, not a lift candidate on its own.
     */
    private static final ScheduledExecutorService ANIMATION_BEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PuppetSpike-AnimationBeat");
                t.setDaemon(true);
                return t;
            });

    /**
     * Candidate empty/no-geometry {@link com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset}
     * ids probed for the {@code "modelswap"} route (design section 3.3/1: "whether a truly
     * empty/no-geometry ModelAsset exists or is authorable" was UNVERIFIED - no such asset is
     * visible anywhere in the in-repo {@code hytale-shared-source} sample). Each is checked via
     * {@link PlayerModelService#modelExists} at runtime; the route is reported unavailable
     * (never guessed) when none resolve.
     */
    private static final String[] EMPTY_MODEL_CANDIDATES = {
            "Empty", "None", "Invisible", "Hidden", "NoModel", "Empty_Model", "Invisible_Model"
    };

    private PuppetSpikeService() {
    }

    // ==================== command-facing entry points ====================

    /** {@code route}: {@code "scale"|"modelswap"|"hidden"}. World-thread hop performed here. */
    public void applyRoute(@Nonnull PlayerRef playerRef, @Nonnull String route) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            playerRef.sendMessage(RpgMsg.tr("command.puppet.not_in_world").color(Color.RED));
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            try {
                applyRouteSync(playerRef, ref, store, route);
            } catch (Throwable t) {
                Log.warn("[PUPPETSPIKE] applyRoute failed: " + t.getMessage(), t);
            }
        });
    }

    /**
     * Despawns the puppet only; the self-hide stays active (isolates observations).
     *
     * <p><b>Round-4 fix 2</b>: the log line now reports an ACCURATE {@code puppetDespawned} flag
     * instead of unconditionally claiming "puppet despawned" - the old wording printed
     * regardless of whether {@code s} (and therefore a live puppet) was even found, which is how
     * a prior {@code off} call's despawn got misattributed to a LATER {@code show} call in the
     * maintainer's evidence log (see {@link #revertFor}'s own javadoc for the fuller trail).
     */
    public void show(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }
        Store<EntityStore> store = resolveStoreForRevert(uuid, playerRef);
        Runnable task = () -> {
            try {
                PuppetSpikeState s = byPlayer.get(uuid);
                String hideRoute = s != null ? s.hideRoute : null;
                boolean puppetWasPresent = s != null && s.puppetRef != null && s.puppetRef.isValid();
                if (s != null) {
                    cancelAnimationBeat(s);
                    despawnPuppetRef(s.puppetRef, s.callerStore);
                    s.puppetRef = null;
                }
                playerRef.sendMessage(RpgMsg.tr("command.puppet.shown").color(Color.GREEN));
                Log.info("[PUPPETSPIKE] route=show puppetDespawned=" + puppetWasPresent + " uuid=" + uuid
                        + " hideRoute=" + hideRoute);
            } catch (Throwable t) {
                Log.warn("[PUPPETSPIKE] show failed: " + t.getMessage(), t);
            }
        };
        runOnWorldOrNow(store, task);
    }

    /** Full revert: despawn the puppet + undo whichever self-hide route is active. Idempotent. */
    public void off(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }
        Store<EntityStore> store = resolveStoreForRevert(uuid, playerRef);
        Runnable task = () -> {
            try {
                revertFor(uuid);
                playerRef.sendMessage(RpgMsg.tr("command.puppet.reverted").color(Color.GREEN));
            } catch (Throwable t) {
                Log.warn("[PUPPETSPIKE] off failed: " + t.getMessage(), t);
            }
        };
        runOnWorldOrNow(store, task);
    }

    // ==================== safety-net hooks (RpgStationsPlugin wires these in) ====================

    /**
     * World-thread, idempotent, never throws. Disconnect/shutdown-adjacent revert - same
     * contract as {@link #off}, but takes a bare uuid (no live {@link PlayerRef}/toast) since a
     * disconnecting player cannot receive a chat reply. Callers already own the world hop
     * (mirrors {@code StationService#stopFor}'s own calling convention from {@code
     * RpgStationsPlugin}'s {@code PlayerDisconnectEvent} handler).
     */
    public void revertFor(@Nonnull UUID uuid) {
        PuppetSpikeState s = byPlayer.remove(uuid);
        if (s == null) {
            Log.info("[PUPPETSPIKE] revertFor no-op uuid=" + uuid + " (nothing tracked)");
            return;
        }
        cancelAnimationBeat(s);

        // Round-4 fix 1 (CRITICAL teleport bug): despawn the puppet FIRST - a well-proven, safe
        // operation - so it is unconditionally gone before the riskier hide-undo runs, then wrap
        // the hide-undo in a defensive position snapshot/restore guard. See this class's own
        // header javadoc for the full root-cause trail.
        boolean puppetWasPresent = s.puppetRef != null && s.puppetRef.isValid();
        despawnPuppetRef(s.puppetRef, s.callerStore);
        s.puppetRef = null;

        double[] preRevertPos = snapshotPosition(s);
        undoHide(s);
        restorePositionIfDrifted(s, preRevertPos);

        Log.info("[PUPPETSPIKE] reverted uuid=" + uuid + " hideRoute=" + s.hideRoute
                + " puppetDespawned=" + puppetWasPresent);
    }

    /**
     * World-thread, idempotent, never throws. {@code PlayerReadyEvent} safety net (design
     * section 4.4's leg-P5 net, in miniature - "a spike must never strand an invisible player"):
     * UNCONDITIONALLY clears any lingering {@link EntityScaleComponent}, restores the correct
     * cosmetic model, and un-hides the player from themselves on the FRESH ready ref/store -
     * deliberately NOT gated on {@link #byPlayer} still holding an entry, because a full server
     * restart wipes that in-memory map while a persisted broken scale/model on the player's own
     * entity would survive it. Also despawns + clears any stray tracked puppet for this uuid.
     *
     * <p><b>Generalized, not duplicated (round-4 puppet-engine leg):</b> the scale-clear +
     * model-restore half of this net is now the SAME production primitive the real
     * {@code station.StationSession}-based engine uses ({@code station.StationService
     * #reassertPuppetOnReady}, delegating to {@code station.StationPuppetController
     * #reassertOnReady}) - this spike harness calls into it rather than keeping its own copy, per
     * this class's own "DELETE this whole puppetspike package once ... legs P1-P6 supersede it"
     * directive: the shared logic already lives on the side that survives. The self-hide-from-self
     * ({@code HiddenPlayersManager}) un-hide below stays HERE only, since the production {@code
     * Hide.Route} union no longer includes that retired route at all.
     */
    public void safetyNetOnReady(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }
        StationService.getInstance().reassertPuppetOnReady(ref, store);
        try {
            playerRef.getHiddenPlayersManager().showPlayer(uuid);
        } catch (Throwable t) {
            Log.fine("[PUPPETSPIKE] ready safety-net show-self failed: " + t.getMessage());
        }
        PuppetSpikeState stray = byPlayer.remove(uuid);
        if (stray != null) {
            cancelAnimationBeat(stray);
            despawnPuppetRef(stray.puppetRef, stray.callerStore);
            Log.info("[PUPPETSPIKE] ready-safety-net cleared stray state uuid=" + uuid);
        }
    }

    // ==================== pure cores (unit-tested without a live server) ====================

    /**
     * Pure: the puppet spawn offset {@code [x, z]} from the caller's own position, {@code
     * metersOffset} meters in front of {@code callerYawRadians}, via the SAME {@code
     * PhysicsMath.vectorFromAngles} the engine's own {@code NPCSpawnCommand} velocity calc uses
     * (pitch pinned to {@code 0} so the puppet stays level with the caller's feet regardless of
     * look angle). Kept a primitive-only return (no {@link Vector3d} leak) so it stays
     * unit-testable, mirroring {@code StationEntityMountController#resolveAttachmentOffset}'s
     * discipline; {@link PhysicsMath}/{@link Vector3d} are pure math with no live-server asset
     * registry dependency, so calling them from a bare JUnit run is safe (unlike constructing an
     * {@code ItemToolSpec}, this package's neighbor's documented trap).
     */
    @Nonnull
    static double[] computeForwardOffsetXZ(float callerYawRadians, double metersOffset) {
        Vector3d dir = new Vector3d();
        PhysicsMath.vectorFromAngles(callerYawRadians, 0f, dir);
        dir.normalize(metersOffset);
        return new double[] {dir.x, dir.z};
    }

    /** Pure: the puppet's own facing yaw (radians) - facing back toward the caller. */
    static float computeFacingYaw(float callerYawRadians) {
        return callerYawRadians - (float) Math.PI;
    }

    // ==================== internals ====================

    private void applyRouteSync(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store, @Nonnull String route) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null || !ref.isValid()) {
            return;
        }

        String emptyModelId = null;
        if ("modelswap".equals(route)) {
            emptyModelId = resolveEmptyModelId();
            if (emptyModelId == null) {
                Log.info("[PUPPETSPIKE] route=modelswap unavailable (no empty ModelAsset resolved; probed="
                        + Arrays.toString(EMPTY_MODEL_CANDIDATES) + ")");
                playerRef.sendMessage(RpgMsg.tr("command.puppet.unavailable", route).color(Color.YELLOW));
                return;
            }
        }

        // Fresh route every call: fully revert any prior spike state first (idempotent).
        revertFor(uuid);

        PuppetSpikeState s = new PuppetSpikeState(playerRef, ref, store);
        s.puppetRef = spawnPuppet(s, store, ref);

        switch (route) {
            case "scale" -> applyScale(s, store, ref);
            case "hidden" -> applyHidden(s, playerRef, uuid);
            case "modelswap" -> applyModelSwap(s, store, ref, emptyModelId);
            default -> {
                Log.warn("[PUPPETSPIKE] unknown route '" + route + "' (no hide applied)");
            }
        }

        byPlayer.put(uuid, s);
        playerRef.sendMessage(RpgMsg.tr("command.puppet.applied", route).color(Color.GREEN));
        Log.info("[PUPPETSPIKE] route=" + route + " applied uuid=" + uuid
                + " puppetRef=" + s.puppetRef + " hideRoute=" + s.hideRoute
                + " savedScale=" + s.savedScale
                + (emptyModelId != null ? " emptyModelId=" + emptyModelId : ""));
    }

    /**
     * Spawns the skinned puppet. Never throws; returns {@code null} on any failure (a spawn
     * failure is non-fatal to the hide-route test - the caller just works without a visible
     * puppet that run).
     */
    @Nullable
    private Ref<EntityStore> spawnPuppet(@Nonnull PuppetSpikeState s, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> callerRef) {
        try {
            TransformComponent callerTransform = store.getComponent(callerRef, TransformComponent.getComponentType());
            PlayerSkinComponent callerSkin = store.getComponent(callerRef, PlayerSkinComponent.getComponentType());
            if (callerTransform == null || callerSkin == null) {
                Log.warn("[PUPPETSPIKE] puppet spawn skipped: caller missing transform/skin");
                return null;
            }
            HeadRotation headRotation = store.getComponent(callerRef, HeadRotation.getComponentType());
            float yaw = headRotation != null ? headRotation.getRotation().yaw() : 0f;

            Vector3d origin = callerTransform.getPosition();
            double[] offsetXZ = computeForwardOffsetXZ(yaw, PUPPET_FORWARD_OFFSET_METERS);
            Vector3d puppetPos = new Vector3d(origin.x + offsetXZ[0], origin.y, origin.z + offsetXZ[1]);
            // Face back toward the caller (the NPCSpawnCommand facingRotation flip convention).
            Rotation3f puppetRotation = new Rotation3f(0f, computeFacingYaw(yaw), 0f);

            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(puppetPos, puppetRotation));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(EntityTrackerSystems.Visible.getComponentType());

            PlayerSkin puppetSkin = new PlayerSkin(callerSkin.getPlayerSkin());
            holder.addComponent(PlayerSkinComponent.getComponentType(), new PlayerSkinComponent(puppetSkin));
            Model model = CosmeticsModule.get().createModel(puppetSkin);
            if (model != null) {
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            }

            ItemStack heldByCaller = InventoryComponent.getItemInHand(store, callerRef);
            if (heldByCaller != null && heldByCaller.getItemId() != null) {
                SimpleItemContainer container = new SimpleItemContainer((short) 1);
                container.setItemStackForSlot((short) 0, new ItemStack(heldByCaller.getItemId(), 1));
                holder.addComponent(InventoryComponent.Hotbar.getComponentType(),
                        new InventoryComponent.Hotbar(container, (byte) 0));
            }

            String itemAnimationsId = resolveItemAnimationsId(heldByCaller);
            if (itemAnimationsId != null) {
                // Pre-seed the tracked clip state (round-4 fix 3): ModelSystems
                // .AnimationEntityTrackerUpdate (Query.and(Visible, ActiveAnimationComponent))
                // auto-catches-up ANY viewer whose tracker marks this entity newlyVisible on a
                // later tick, REGARDLESS of when the direct packet in #startAnimationBeat lands -
                // the render-guaranteed route the puppet design doc's P3 scout identified. See
                // #startAnimationBeat's own javadoc for the full source trail.
                ActiveAnimationComponent activeAnim = new ActiveAnimationComponent();
                activeAnim.setPlayingAnimation(AnimationSlot.Action, PUPPET_CLIP_ID);
                holder.addComponent(ActiveAnimationComponent.getComponentType(), activeAnim);
            }

            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

            Ref<EntityStore> puppetRef = store.addEntity(holder, AddReason.SPAWN);
            if (puppetRef == null) {
                Log.warn("[PUPPETSPIKE] puppet spawn returned null ref");
                return null;
            }

            Log.info("[PUPPETSPIKE] puppet spawned ref=" + puppetRef + " pos=" + puppetPos
                    + " heldItem=" + (heldByCaller != null ? heldByCaller.getItemId() : "none"));

            startAnimationBeat(s, store, puppetRef, itemAnimationsId);
            return puppetRef;
        } catch (Throwable t) {
            Log.warn("[PUPPETSPIKE] puppet spawn failed: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Resolves the held item's {@code PlayerAnimationsId} for the swing clip, mirroring {@code
     * StationHoldController#playActionSwing}'s exact resolution: {@code null} when the mirrored
     * held item has none (logged, not failed - the maintainer just needs to hold a Hatchet-family
     * tool to see the swing clip).
     */
    @Nullable
    private String resolveItemAnimationsId(@Nullable ItemStack heldByCaller) {
        Item item = heldByCaller != null ? heldByCaller.getItem() : null;
        String itemAnimationsId = item != null ? item.getPlayerAnimationsId() : null;
        if (itemAnimationsId == null || itemAnimationsId.isBlank()) {
            Log.info("[PUPPETSPIKE] puppet animation skipped: held item '"
                    + (item != null ? item.getId() : "none")
                    + "' has no PlayerAnimationsId (hold a Hatchet-family tool to see the swing clip)");
            return null;
        }
        return itemAnimationsId;
    }

    /**
     * Round-4 harness-bug fix 3: schedules the swing clip on a repeating {@link
     * #ANIMATION_BEAT_MS} beat instead of firing it once, synchronously, inside the same
     * world-thread hop that just spawned the puppet.
     *
     * <p><b>Root cause (source-confirmed):</b> {@code AnimationUtils.playAnimation} routes every
     * packet through {@code PlayerUtil.forEachPlayerThatCanSeeEntity} (hytale-shared-source
     * {@code AnimationUtils.java:80-84}) - "can see" meaning "the entity's tracker has already
     * registered this viewer", a registration that happens on a LATER tracker tick, not
     * synchronously inside {@code store.addEntity}. Firing immediately after spawn therefore
     * found zero qualifying viewers and silently dropped the packet for everyone - exactly what
     * the evidence log showed ("puppet animation played" logged BEFORE "puppet spawned" in
     * program order is misleading; the real defect is WHICH tick the packet went out on, not the
     * log-line ordering). Deferring every fire by at least one beat lets tracking catch up first.
     *
     * <p><b>Belt-and-suspenders, not a guess:</b> {@link #spawnPuppet} ALSO pre-seeds {@link
     * ActiveAnimationComponent} on the holder (design doc section 1's render-guaranteed NPC
     * route - {@code ModelSystems.AnimationEntityTrackerUpdate} pushes the CURRENT tracked clip
     * to any viewer the moment their tracker marks the puppet {@code newlyVisible}, a mechanism
     * that is correct regardless of this timer's own cadence). This beat loop is the "keeps
     * swinging" cue for already-tracking viewers, mirroring the station engine's own per-swing
     * timer ({@code StationService}'s {@code Animation.Swing.IntervalMs} convention, `933` here
     * too) - a single static clip would not read as a repeating work animation.
     *
     * <p><b>Leg-D pathfinding note:</b> a BARE entity (this harness's spawn route, not an
     * {@code NPCPlugin.spawnEntity} + Role) gets NO free {@code NPCEntity.playAnimation}
     * convenience wrapper - the real puppet-presentation system (design legs P1-P6) must either
     * (a) spawn via an NPC Role to get that wrapper for free, or (b) do exactly what this method
     * does: manually pre-seed {@code ActiveAnimationComponent} AND keep calling {@code
     * AnimationUtils.playAnimation} directly, since {@code ActiveAnimationComponent} alone never
     * re-pushes an UNCHANGED clip to an ALREADY-tracking viewer (its own {@code
     * isNetworkOutdated} write is a commented-out TODO in the shared source - the "newly visible"
     * path is the only live trigger).
     */
    private void startAnimationBeat(@Nonnull PuppetSpikeState s, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> puppetRef, @Nullable String itemAnimationsId) {
        if (itemAnimationsId == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Runnable beat = () -> {
            try {
                world.execute(() -> {
                    try {
                        if (!puppetRef.isValid()) {
                            cancelAnimationBeat(s);
                            return;
                        }
                        AnimationUtils.playAnimation(puppetRef, AnimationSlot.Action, itemAnimationsId,
                                PUPPET_CLIP_ID, true, store);
                        Log.info("[PUPPETSPIKE] puppet animation beat clip=" + PUPPET_CLIP_ID);
                    } catch (Throwable t) {
                        Log.warn("[PUPPETSPIKE] puppet animation beat failed (world thread): " + t.getMessage(), t);
                    }
                });
            } catch (Throwable t) {
                Log.fine("[PUPPETSPIKE] puppet animation beat scheduling failed: " + t.getMessage());
                cancelAnimationBeat(s);
            }
        };
        s.animationBeatTask = ANIMATION_BEAT_SCHEDULER.scheduleAtFixedRate(
                beat, ANIMATION_BEAT_MS, ANIMATION_BEAT_MS, TimeUnit.MILLISECONDS);
    }

    /** Cancels {@code s}'s repeating swing-beat task, if any. Idempotent, never throws. */
    private void cancelAnimationBeat(@Nonnull PuppetSpikeState s) {
        if (s.animationBeatTask != null) {
            s.animationBeatTask.cancel(false);
            s.animationBeatTask = null;
        }
    }

    /**
     * Stops the shared animation-beat scheduler's daemon thread (best-effort - the thread is
     * already daemon, so JVM exit does not hang on it regardless). Call from plugin shutdown.
     */
    public void shutdownAnimationScheduler() {
        ANIMATION_BEAT_SCHEDULER.shutdownNow();
    }

    private void applyScale(@Nonnull PuppetSpikeState s, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        EntityScaleComponent existing = store.getComponent(ref, EntityScaleComponent.getComponentType());
        s.savedScale = existing != null ? existing.getScale() : null;
        store.putComponent(ref, EntityScaleComponent.getComponentType(), new EntityScaleComponent(NEAR_ZERO_SCALE));
        s.hideRoute = "scale";
    }

    private void applyHidden(@Nonnull PuppetSpikeState s, @Nonnull PlayerRef playerRef, @Nonnull UUID uuid) {
        // The /hide command blocks the self case at the COMMAND layer only (HideCommand's own
        // "Prevent hiding from self" checks) - HiddenPlayersManager itself has no such guard, so
        // a direct manager call is the untested-but-technically-possible route this spike exists
        // to observe.
        playerRef.getHiddenPlayersManager().hidePlayer(uuid);
        s.hideRoute = "hidden";
    }

    private void applyModelSwap(@Nonnull PuppetSpikeState s, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull String emptyModelId) {
        PlayerModelService.apply(ref, store, emptyModelId, 1f);
        s.hideRoute = "modelswap";
    }

    private void undoHide(@Nonnull PuppetSpikeState s) {
        if (s.hideRoute == null) {
            return;
        }
        try {
            switch (s.hideRoute) {
                case "scale" -> {
                    if (s.callerRef.isValid()) {
                        if (s.savedScale != null) {
                            s.callerStore.putComponent(s.callerRef, EntityScaleComponent.getComponentType(),
                                    new EntityScaleComponent(s.savedScale));
                        } else {
                            s.callerStore.removeComponentIfExists(s.callerRef, EntityScaleComponent.getComponentType());
                        }
                    }
                }
                case "modelswap" -> {
                    if (s.callerRef.isValid()) {
                        PlayerModelService.restore(s.callerRef, s.callerStore);
                    }
                }
                case "hidden" -> s.playerRef.getHiddenPlayersManager().showPlayer(s.playerRef.getUuid());
                default -> {
                }
            }
        } catch (Throwable t) {
            Log.warn("[PUPPETSPIKE] undoHide failed route=" + s.hideRoute + ": " + t.getMessage(), t);
        }
    }

    private void despawnPuppetRef(@Nullable Ref<EntityStore> puppetRef, @Nullable Store<EntityStore> fallbackStore) {
        if (puppetRef == null || !puppetRef.isValid()) {
            return;
        }
        try {
            // Ref#getStore is @Nonnull (always returns the store the ref was constructed
            // against - see hytale-shared-source Ref.java), so fallbackStore never actually
            // substitutes; kept as an explicit param for callers that resolve one anyway.
            Store<EntityStore> store = puppetRef.getStore();
            if (store == null) {
                store = fallbackStore;
            }
            if (store != null) {
                store.removeEntity(puppetRef, RemoveReason.REMOVE);
            }
        } catch (Throwable t) {
            // Round-4 fix 2: raised from `fine` (invisible at normal log levels) to `warn` -
            // a swallowed-silent despawn failure here is exactly what made "off doesn't
            // despawn" undiagnosable from the evidence log alone.
            Log.warn("[PUPPETSPIKE] puppet despawn failed: " + t.getMessage(), t);
        }
    }

    /** Pure: the caller's current position as {@code [x, y, z]}, or {@code null} when unreadable. */
    @Nullable
    private double[] snapshotPosition(@Nonnull PuppetSpikeState s) {
        try {
            if (!s.callerRef.isValid()) {
                return null;
            }
            TransformComponent transform = s.callerStore.getComponent(s.callerRef, TransformComponent.getComponentType());
            if (transform == null) {
                return null;
            }
            Vector3d pos = transform.getPosition();
            return new double[] {pos.x, pos.y, pos.z};
        } catch (Throwable t) {
            Log.fine("[PUPPETSPIKE] position snapshot failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * The CRITICAL revert-teleport guard (round-4 harness-bug fix 1): restores the caller's
     * pre-revert position (leaving rotation untouched) if it drifted by more than {@link
     * #TELEPORT_GUARD_EPSILON_METERS} across {@link #undoHide}, logging a WARN with the observed
     * delta either way this fires - evidence for whoever investigates the underlying downstream
     * mechanism next. See this class's own header javadoc for the full root-cause trail.
     */
    private void restorePositionIfDrifted(@Nonnull PuppetSpikeState s, @Nullable double[] preRevertPos) {
        if (preRevertPos == null) {
            return;
        }
        try {
            if (!s.callerRef.isValid()) {
                return;
            }
            TransformComponent transform = s.callerStore.getComponent(s.callerRef, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            Vector3d pos = transform.getPosition();
            if (!positionDrifted(preRevertPos[0], preRevertPos[1], preRevertPos[2], pos.x, pos.y, pos.z,
                    TELEPORT_GUARD_EPSILON_METERS)) {
                return;
            }
            Log.warn("[PUPPETSPIKE] revert drift guard fired route=" + s.hideRoute
                    + " from=(" + preRevertPos[0] + "," + preRevertPos[1] + "," + preRevertPos[2] + ")"
                    + " to=(" + pos.x + "," + pos.y + "," + pos.z + ") - restoring pre-revert position");
            transform.setPosition(new Vector3d(preRevertPos[0], preRevertPos[1], preRevertPos[2]));
        } catch (Throwable t) {
            Log.warn("[PUPPETSPIKE] revert drift guard failed: " + t.getMessage(), t);
        }
    }

    /**
     * Pure: whether {@code (x2,y2,z2)} differs from {@code (x1,y1,z1)} by more than {@code
     * epsilonMeters} of Euclidean distance - the revert-teleport guard's decision core,
     * unit-tested without a live server.
     */
    static boolean positionDrifted(double x1, double y1, double z1, double x2, double y2, double z2,
            double epsilonMeters) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        return distanceSquared > epsilonMeters * epsilonMeters;
    }

    @Nullable
    private String resolveEmptyModelId() {
        for (String candidate : EMPTY_MODEL_CANDIDATES) {
            if (PlayerModelService.modelExists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private Store<EntityStore> resolveStoreForRevert(@Nonnull UUID uuid, @Nonnull PlayerRef playerRef) {
        PuppetSpikeState s = byPlayer.get(uuid);
        if (s != null) {
            return s.callerStore;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        return ref != null && ref.isValid() ? ref.getStore() : null;
    }

    private void runOnWorldOrNow(@Nullable Store<EntityStore> store, @Nonnull Runnable task) {
        if (store == null) {
            task.run();
            return;
        }
        store.getExternalData().getWorld().execute(task);
    }
}
