package com.ziggfreed.rpgstations.puppetspike;

import java.awt.Color;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * hytale-shared-source/HytaleServer/NPC/.../commands/NPCSpawnCommand.java}), playing the
 * Hatchet-family {@code "Chop"} swing clip once on the {@code Action} slot (mirroring {@code
 * station.StationHoldController#playActionSwing}'s exact resolution - it only plays if the
 * caller is holding a Hatchet-family tool; see this class's report for the caveat), and holding
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

    /** Despawns the puppet only; the self-hide stays active (isolates observations). */
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
                if (s != null) {
                    despawnPuppetRef(s.puppetRef, s.callerStore);
                    s.puppetRef = null;
                }
                playerRef.sendMessage(RpgMsg.tr("command.puppet.shown").color(Color.GREEN));
                Log.info("[PUPPETSPIKE] route=show puppet despawned, hide kept uuid=" + uuid
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
            return;
        }
        undoHide(s);
        despawnPuppetRef(s.puppetRef, s.callerStore);
        Log.info("[PUPPETSPIKE] reverted uuid=" + uuid + " hideRoute=" + s.hideRoute);
    }

    /**
     * World-thread, idempotent, never throws. {@code PlayerReadyEvent} safety net (design
     * section 4.4's leg-P5 net, in miniature - "a spike must never strand an invisible player"):
     * UNCONDITIONALLY clears any lingering {@link EntityScaleComponent}, restores the correct
     * cosmetic model, and un-hides the player from themselves on the FRESH ready ref/store -
     * deliberately NOT gated on {@link #byPlayer} still holding an entry, because a full server
     * restart wipes that in-memory map while a persisted broken scale/model on the player's own
     * entity would survive it. Also despawns + clears any stray tracked puppet for this uuid.
     */
    public void safetyNetOnReady(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }
        try {
            store.removeComponentIfExists(ref, EntityScaleComponent.getComponentType());
        } catch (Throwable t) {
            Log.fine("[PUPPETSPIKE] ready safety-net scale-clear failed: " + t.getMessage());
        }
        PlayerModelService.restore(ref, store);
        try {
            playerRef.getHiddenPlayersManager().showPlayer(uuid);
        } catch (Throwable t) {
            Log.fine("[PUPPETSPIKE] ready safety-net show-self failed: " + t.getMessage());
        }
        PuppetSpikeState stray = byPlayer.remove(uuid);
        if (stray != null) {
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
        s.puppetRef = spawnPuppet(store, ref);

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
    private Ref<EntityStore> spawnPuppet(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> callerRef) {
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

            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

            Ref<EntityStore> puppetRef = store.addEntity(holder, AddReason.SPAWN);
            if (puppetRef == null) {
                Log.warn("[PUPPETSPIKE] puppet spawn returned null ref");
                return null;
            }

            playPuppetSwingClip(store, puppetRef, heldByCaller);

            Log.info("[PUPPETSPIKE] puppet spawned ref=" + puppetRef + " pos=" + puppetPos
                    + " heldItem=" + (heldByCaller != null ? heldByCaller.getItemId() : "none"));
            return puppetRef;
        } catch (Throwable t) {
            Log.warn("[PUPPETSPIKE] puppet spawn failed: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * One-shot swing clip on the {@code Action} slot, mirroring {@code
     * StationHoldController#playActionSwing}'s exact resolution: the clip only plays when the
     * mirrored held item resolves a Hatchet-family {@code PlayerAnimationsId} (skipped, not
     * failed, otherwise - logged so the maintainer knows to hold a Hatchet-family tool).
     */
    private void playPuppetSwingClip(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> puppetRef,
            @Nullable ItemStack heldByCaller) {
        try {
            Item item = heldByCaller != null ? heldByCaller.getItem() : null;
            String itemAnimationsId = item != null ? item.getPlayerAnimationsId() : null;
            if (itemAnimationsId == null || itemAnimationsId.isBlank()) {
                Log.info("[PUPPETSPIKE] puppet animation skipped: held item '"
                        + (item != null ? item.getId() : "none")
                        + "' has no PlayerAnimationsId (hold a Hatchet-family tool to see the swing clip)");
                return;
            }
            AnimationUtils.playAnimation(puppetRef, AnimationSlot.Action, itemAnimationsId, PUPPET_CLIP_ID, true, store);
            Log.info("[PUPPETSPIKE] puppet animation played clip=" + PUPPET_CLIP_ID
                    + " itemAnimationsId=" + itemAnimationsId);
        } catch (Throwable t) {
            Log.warn("[PUPPETSPIKE] puppet animation failed: " + t.getMessage(), t);
        }
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
            Store<EntityStore> store = puppetRef.getStore();
            if (store == null) {
                store = fallbackStore;
            }
            if (store != null) {
                store.removeEntity(puppetRef, RemoveReason.REMOVE);
            }
        } catch (Throwable t) {
            Log.fine("[PUPPETSPIKE] puppet despawn failed: " + t.getMessage());
        }
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
