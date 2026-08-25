package com.ziggfreed.rpgstations.command;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.function.consumer.TriConsumer;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import com.ziggfreed.common.inventory.PlayerAccess;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.util.Log;

/**
 * SCOPE-3 NPC-performer spike (throwaway dev harness): drives the {@code RPG_Performer_Spike}
 * Role NPC through {@code /rpgstations npcspike start [noserialize] | walk | stop | prop
 * <itemId|off> | clip <emoteId>}, so the maintainer can smoke the open scope-3 questions a Role
 * performer (as opposed to the shipped bare-{@code Holder} puppet) raises: does a cloned PLAYER
 * skin render on a walking {@link NPCEntity}? does its native {@code MotionControllerWalk} gait /
 * step-up beat the hand-rolled A* walker? is there really no nameplate / health bar / F-prompt /
 * crosshair target? how does the {@code NonSerialized}-on-NPC variant behave across a relog? does
 * the puppet's PROP technique (a {@code Hotbar} write) render a held item on the NPC? does the
 * puppet's CLIP technique ({@code ActiveAnimationComponent} + {@code AnimationUtils.playAnimation})
 * fire a one-shot animation on the NPC without fighting the Role brain's own walk gait?
 *
 * <p>The design authority is
 * {@code .claude/research/raw/rpg-stations-npc-performer-feasibility-2026-07-24.md} (the MINIMAL
 * SPIKE, section 5) + {@code .claude/research/raw/rpg-stations-look-source-performer-seam-2026-07-24.md}
 * (section 2's performer contract - {@code setProp}/{@code playClip} listed UNPROVEN, Q5's spike
 * extension) + the recon digest
 * {@code .claude/research/raw/npc-behavior-mods-recon-2026-07-24.md}. The mechanism, source-verified
 * against the official shared source:
 * <ul>
 *   <li><b>spawn</b> - read the caller's live {@link PlayerSkinComponent}, clone the
 *       {@link PlayerSkin}, build a {@link Model} via {@link CosmeticsModule} (the
 *       {@code PlayerPuppetService} clone recipe, the in-repo precedent), and pass it as
 *       {@link NPCPlugin#spawnEntity}'s {@code spawnModel} param (the model is a spawn param, NOT
 *       Role-owned). {@code postSpawn} re-applies a fresh {@link PlayerSkinComponent} clone and
 *       SEEDS the leash at the spawn pos ({@code setLeashPoint}/{@code setLeashHeading}/
 *       {@code setLeashPitch} - the Hycompanion "plugin spawns skip the engine leash-seed" fix).
 *       The {@code noserialize} variant additionally marks the NonSerialized component in
 *       {@code preAddToWorld} (this variant tests the untested NonSerialized-on-NPC gap).</li>
 *   <li><b>walk</b> - spawn a bare invisible marker entity (TransformComponent + NetworkId, no
 *       ModelComponent so it renders nothing) ~8 blocks ahead of the caller's facing, mark it into
 *       the Role's {@code MoveTarget} slot via {@link Role#setMarkedTarget(Ref,
 *       com.hypixel.hytale.component.ComponentAccessor, String, Ref)} (the only walk-to-a-point
 *       route - the {@code Target} sensor / {@code Seek} BodyMotion in the role asset path toward
 *       whatever is marked), and RE-ANCHOR the leash to the destination (or background leash logic
 *       fights the Seek - the recon's leash gotcha).</li>
 *   <li><b>stop</b> - despawn the NPC + the marker ({@code store.removeEntity}, REMOVE reason).</li>
 *   <li><b>prop</b> - attempts the bare-Holder puppet's OWN prop technique ({@code
 *       InventoryComponent.Hotbar}) retargeted at the NPC ref, but via the NPC-NATIVE write
 *       ({@link InventoryHelper#useItem}) rather than the puppet's from-scratch component
 *       replace: {@code NPCPlugin.spawnEntity}'s own {@code NPCSystems.OnNPCAdded} hook already
 *       provisions a {@code Hotbar} (3 slots) on every NPC at add-time (source-verified,
 *       {@code NPCSystems.java:673-674}), so the NPC's inventory surface genuinely DIFFERS from
 *       the puppet's ad hoc single-slot Hotbar - {@code InventoryHelper.useItem} sets the item
 *       into that EXISTING hotbar and marks it the active slot (the same call the NPC package's
 *       own {@code ActionInventory}/{@code ActionPickUpItem} use), which is the NPC-native route
 *       the class javadoc above asks for. Role's own {@code HotbarItems} field
 *       ({@code BuilderRole.java:252}) is asset-static (authored once, baked at Role-build time,
 *       no runtime setter) so it is NOT an option here - runtime always means a component write,
 *       confirmed by reading the builder.</li>
 *   <li><b>clip</b> - attempts the bare-Holder puppet's OWN clip technique
 *       ({@code ActiveAnimationComponent} write + {@code AnimationUtils.playAnimation} packet,
 *       {@code PlayerPuppetService.playAnimation}'s exact mechanism) retargeted at the NPC ref.
 *       {@code spawnEntity} never pre-seeds {@code ActiveAnimationComponent} (unlike the puppet's
 *       optional {@code initialAnimation} holder pre-seed), so this ensures one exists first (the
 *       "write" itself) via a plain {@code putComponent}, then fires
 *       {@link AnimationUtils#playAnimation} directly on {@code AnimationSlot.Emote} - NOT
 *       {@link NPCEntity#playAnimation}, the NPC's own convenience wrapper: its model-registered
 *       gate does NOT exempt {@code AnimationSlot.Emote} the way {@code AnimationUtils
 *       .playAnimation}'s own gate explicitly does (source-verified, {@code NPCEntity.java:327}
 *       vs {@code AnimationUtils.java:68}), so a player-clone model (whose animation set will not
 *       list a custom id like {@code RPG_Emote_Hammer}) would have the NPC's own wrapper silently
 *       swallow the clip. Calling {@code AnimationUtils.playAnimation} directly is the exact
 *       mechanism the shipped puppet already proves works for emote clips.</li>
 * </ul>
 *
 * <p><b>World-thread discipline.</b> Every {@code Store}/{@code Ref}/{@code spawnEntity}/
 * {@code removeEntity} touch runs inside {@code world.execute(...)} (the command dispatch thread is
 * not the world thread); chat feedback goes through {@link PlayerRef#sendMessage} which is
 * packet-based and thread-safe. Fully try-guarded - a throw degrades to a logged no-op, never
 * escapes into the command. Per-player spawn state is tracked in {@link #byPlayer}; a fresh
 * {@code start} first tears down any prior spawn for that player.
 */
public final class NpcPerformerSpike {

    /** The jar-shipped throwaway Role asset id (filename minus {@code .json}). */
    private static final String ROLE_ID = "RPG_Performer_Spike";

    /** The marked-target slot name the role's {@code Target} sensor reads (must match the asset). */
    private static final String MOVE_TARGET_SLOT = "MoveTarget";

    /** Distance in front of the caller to place the spawned performer, in blocks. */
    private static final double SPAWN_AHEAD = 2.0;

    /** Distance in front of the caller to place the walk marker, in blocks. */
    private static final double MARKER_AHEAD = 8.0;

    /** Per-player live spawn state (the throwaway spike's own tracking). */
    private final Map<UUID, Spawned> byPlayer = new ConcurrentHashMap<>();

    /** The refs a single player's spike spawn owns: the performer NPC and its optional walk marker. */
    private static final class Spawned {
        @Nullable
        private NPCEntity npc;
        @Nullable
        private Ref<EntityStore> npcRef;
        @Nullable
        private Ref<EntityStore> markerRef;
    }

    // ==================== subcommand entry points ====================

    /**
     * {@code /rpgstations npcspike start [noserialize]} - spawn the performer NPC ~2 blocks in
     * front of {@code player}, cloning the player's own skin onto it.
     */
    public void start(@Nonnull PlayerRef player, boolean noSerialize) {
        onWorld(player, (world, playerRef, store) -> doStart(playerRef, store, noSerialize));
    }

    /**
     * {@code /rpgstations npcspike walk} - send the active performer walking toward a fresh
     * invisible marker ~8 blocks ahead of {@code player}.
     */
    public void walk(@Nonnull PlayerRef player) {
        onWorld(player, (world, playerRef, store) -> doWalk(playerRef, store));
    }

    /** {@code /rpgstations npcspike stop} - despawn the active performer NPC + its marker. */
    public void stop(@Nonnull PlayerRef player) {
        onWorld(player, (world, playerRef, store) -> doStop(player, store));
    }

    /**
     * {@code /rpgstations npcspike prop <itemId|off>} - attempts the puppet's prop technique
     * (a held-item write) on the active performer NPC. {@code off}/blank clears the held item.
     */
    public void prop(@Nonnull PlayerRef player, @Nullable String itemIdOrOff) {
        onWorld(player, (world, playerRef, store) -> doProp(player, store, itemIdOrOff));
    }

    /**
     * {@code /rpgstations npcspike clip <emoteId>} - attempts the puppet's clip technique (a
     * one-shot {@code AnimationSlot.Emote} animation) on the active performer NPC. Suggested
     * test ids: {@code RPG_Emote_Hammer}, {@code RPG_Emote_Knife}.
     */
    public void clip(@Nonnull PlayerRef player, @Nullable String emoteId) {
        onWorld(player, (world, playerRef, store) -> doClip(player, store, emoteId));
    }

    // ==================== operations (world thread) ====================

    private void doStart(@Nonnull Ref<EntityStore> playerRef,
            @Nonnull Store<EntityStore> store, boolean noSerialize) {
        PlayerRef player = PlayerAccess.playerRef(store, playerRef);
        if (player == null) {
            return;
        }
        // A fresh start tears down any prior spawn for this player first.
        despawnFor(player.getUuid(), store);

        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        HeadRotation hr = store.getComponent(playerRef, HeadRotation.getComponentType());
        PlayerSkinComponent skinComp = store.getComponent(playerRef, PlayerSkinComponent.getComponentType());
        if (tc == null || hr == null) {
            Log.warn("[npcspike] start aborted: player has no transform/head-rotation yet");
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }
        if (skinComp == null) {
            Log.warn("[npcspike] start aborted: player has no PlayerSkinComponent to clone");
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }

        NPCPlugin npc = NPCPlugin.get();
        if (npc == null || !npc.hasRoleName(ROLE_ID)) {
            Log.warn("[npcspike] role '" + ROLE_ID + "' not registered - is the asset pack loaded?");
            player.sendMessage(RpgMsg.tr("command.npcspike.role_missing").color(Color.RED));
            return;
        }
        int idx = npc.getIndex(ROLE_ID);
        if (idx < 0) {
            Log.warn("[npcspike] role '" + ROLE_ID + "' resolved to a negative index");
            player.sendMessage(RpgMsg.tr("command.npcspike.role_missing").color(Color.RED));
            return;
        }

        float playerYaw = hr.getRotation().y();
        Vector3d spawnPos = ahead(tc.getPosition(), playerYaw, SPAWN_AHEAD);
        // Face back toward the player so the cloned skin reads at a glance.
        float spawnYaw = playerYaw + (float) Math.PI;
        Rotation3f spawnRot = new Rotation3f(0f, spawnYaw, 0f);

        PlayerSkin skinClone = new PlayerSkin(skinComp.getPlayerSkin());
        Model model = CosmeticsModule.get().createModel(skinClone);

        TriConsumer<NPCEntity, Holder<EntityStore>, Store<EntityStore>> preAdd = noSerialize
                ? (npcEntity, holder, s) ->
                        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType())
                : null;

        Spawned spawned = new Spawned();
        Vector3d leashSeed = new Vector3d(spawnPos);
        TriConsumer<NPCEntity, Ref<EntityStore>, Store<EntityStore>> postSpawn = (npcEntity, ref, s) -> {
            // Apply the cloned player skin (the /npc spawn --randommodel precedent, minus the
            // random-skin reload marker: ApplyRandomSkinPersistedComponent would re-roll a RANDOM
            // skin on reload and wipe our clone - whether the clone survives a chunk reload without
            // some reload marker is a documented spike gap).
            s.putComponent(ref, PlayerSkinComponent.getComponentType(),
                    new PlayerSkinComponent(new PlayerSkin(skinClone)));
            // Seed the leash at the spawn pos (plugin spawns skip the engine's leash seed).
            npcEntity.setLeashPoint(new Vector3d(leashSeed));
            npcEntity.setLeashHeading(spawnYaw);
            npcEntity.setLeashPitch(0f);
            spawned.npc = npcEntity;
            spawned.npcRef = ref;
        };

        try {
            var result = npc.spawnEntity(store, idx, spawnPos, spawnRot, model, preAdd, postSpawn);
            if (result == null || spawned.npcRef == null) {
                Log.warn("[npcspike] spawnEntity returned null for role '" + ROLE_ID + "'");
                player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
                return;
            }
        } catch (Throwable t) {
            Log.warn("[npcspike] spawnEntity threw: " + t.getMessage());
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }

        byPlayer.put(player.getUuid(), spawned);
        Message ok = noSerialize
                ? RpgMsg.tr("command.npcspike.spawned_noserialize").color(Color.GREEN)
                : RpgMsg.tr("command.npcspike.spawned").color(Color.GREEN);
        player.sendMessage(ok);
        Log.info("[npcspike] spawned performer for " + player.getUuid() + " (noSerialize=" + noSerialize + ")");
    }

    private void doWalk(@Nonnull Ref<EntityStore> playerRef,
            @Nonnull Store<EntityStore> store) {
        PlayerRef player = PlayerAccess.playerRef(store, playerRef);
        if (player == null) {
            return;
        }
        Spawned spawned = byPlayer.get(player.getUuid());
        if (spawned == null || spawned.npcRef == null || !spawned.npcRef.isValid() || spawned.npc == null) {
            player.sendMessage(RpgMsg.tr("command.npcspike.walk_no_npc").color(Color.YELLOW));
            return;
        }

        Role role = spawned.npc.getRole();
        if (role == null) {
            // The brain builds the Role on its first tick; a walk issued the same instant as start
            // can race it. In practice the two commands are seconds apart, so this is rare.
            player.sendMessage(RpgMsg.tr("command.npcspike.role_not_ready").color(Color.YELLOW));
            return;
        }

        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        HeadRotation hr = store.getComponent(playerRef, HeadRotation.getComponentType());
        if (tc == null || hr == null) {
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }
        Vector3d markerPos = ahead(tc.getPosition(), hr.getRotation().y(), MARKER_AHEAD);

        // Replace any prior marker.
        removeRef(spawned.markerRef, store);
        spawned.markerRef = null;

        Ref<EntityStore> markerRef = spawnMarker(store, markerPos);
        if (markerRef == null) {
            Log.warn("[npcspike] failed to spawn walk marker");
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }
        spawned.markerRef = markerRef;

        try {
            role.setMarkedTarget(spawned.npcRef, store, MOVE_TARGET_SLOT, markerRef);
            // Re-anchor the leash to the destination (the recon leash gotcha).
            spawned.npc.setLeashPoint(new Vector3d(markerPos));
        } catch (Throwable t) {
            Log.warn("[npcspike] setMarkedTarget/leash re-anchor failed: " + t.getMessage());
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }

        player.sendMessage(RpgMsg.tr("command.npcspike.walking", (int) MARKER_AHEAD).color(Color.GREEN));
        Log.info("[npcspike] walk target set " + (int) MARKER_AHEAD + " blocks ahead for " + player.getUuid());
    }

    private void doStop(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store) {
        boolean tracked = byPlayer.containsKey(player.getUuid());
        // Tear down whatever THIS player's map tracks (NPC + its walk marker), if anything.
        despawnFor(player.getUuid(), store);
        // Sweep fallback (feasibility 4d "SPIKE MERGE HARDENING" + decision 46): the per-player
        // map is empty after a server restart, so the DEFAULT (serialized) spawn variant leaves a
        // PERSISTED spike NPC with no tracking ref - it would strand forever. Despawn ANY entity
        // whose NPCEntity role is the throwaway spike role, so a stale map never orphans one. A
        // registered tag component would be gold-plating; the role-name match suffices for a
        // throwaway. Markers need no separate sweep: they are NonSerialized (they never survive
        // the restart that stales the map) and are torn down with the tracked owner above.
        int swept = sweepStrandedPerformers(store);
        if (tracked || swept > 0) {
            player.sendMessage(RpgMsg.tr("command.npcspike.stopped").color(Color.GREEN));
            Log.info("[npcspike] despawned performer for " + player.getUuid() + " (swept=" + swept + ")");
        } else {
            player.sendMessage(RpgMsg.tr("command.npcspike.stop_none").color(Color.YELLOW));
        }
    }

    /**
     * Attempts the prop-mirroring technique on the active performer's Hotbar (decision 48,
     * batch-2 spike extension). Graceful no-NPC handling; never throws into the command.
     */
    private void doProp(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store, @Nullable String itemIdOrOff) {
        Spawned spawned = byPlayer.get(player.getUuid());
        if (spawned == null || spawned.npcRef == null || !spawned.npcRef.isValid()) {
            player.sendMessage(RpgMsg.tr("command.npcspike.walk_no_npc").color(Color.YELLOW));
            return;
        }
        String trimmed = itemIdOrOff == null ? "" : itemIdOrOff.trim();
        boolean off = trimmed.isEmpty() || "off".equalsIgnoreCase(trimmed);

        boolean applied;
        try {
            // NPC-native write (class javadoc's <li>prop</li>): the NPC already carries its own
            // Hotbar (NPCSystems.OnNPCAdded provisions one on every NPC at add-time), so this sets
            // into it and marks the slot active rather than replacing the whole component the way
            // the bare-Holder puppet does.
            applied = InventoryHelper.useItem(spawned.npcRef, off ? null : trimmed, store);
        } catch (Throwable t) {
            Log.warn("[npcspike] prop attempt failed: " + t.getMessage());
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }
        if (!off && !applied) {
            player.sendMessage(RpgMsg.tr("command.npcspike.prop_unknown_item", trimmed).color(Color.YELLOW));
            return;
        }
        Message ok = off
                ? RpgMsg.tr("command.npcspike.prop_cleared")
                : RpgMsg.tr("command.npcspike.prop_set", trimmed);
        player.sendMessage(ok.color(Color.GREEN));
        Log.info("[npcspike] prop attempted for " + player.getUuid() + ": " + (off ? "off" : trimmed));
    }

    /**
     * Attempts the clip-playing technique on the active performer (decision 48, batch-2 spike
     * extension). Graceful no-NPC handling; never throws into the command.
     */
    private void doClip(@Nonnull PlayerRef player, @Nonnull Store<EntityStore> store, @Nullable String emoteId) {
        Spawned spawned = byPlayer.get(player.getUuid());
        if (spawned == null || spawned.npcRef == null || !spawned.npcRef.isValid()) {
            player.sendMessage(RpgMsg.tr("command.npcspike.walk_no_npc").color(Color.YELLOW));
            return;
        }
        String trimmed = emoteId == null ? "" : emoteId.trim();
        if (trimmed.isEmpty()) {
            player.sendMessage(RpgMsg.tr("command.npcspike.clip_usage").color(Color.YELLOW));
            return;
        }

        try {
            // The "write" (class javadoc's <li>clip</li>): spawnEntity never pre-seeds
            // ActiveAnimationComponent, so ensure one exists before firing the packet.
            ActiveAnimationComponent anim = store.getComponent(spawned.npcRef, ActiveAnimationComponent.getComponentType());
            if (anim == null) {
                anim = new ActiveAnimationComponent();
                store.putComponent(spawned.npcRef, ActiveAnimationComponent.getComponentType(), anim);
            }
            anim.setPlayingAnimation(AnimationSlot.Emote, trimmed);
            // The packet: AnimationUtils directly, NOT NPCEntity#playAnimation - see the class
            // javadoc's <li>clip</li> for why the NPC's own wrapper is the wrong choice here.
            AnimationUtils.playAnimation(spawned.npcRef, AnimationSlot.Emote, null, trimmed, true, store);
        } catch (Throwable t) {
            Log.warn("[npcspike] clip attempt failed: " + t.getMessage());
            player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
            return;
        }
        player.sendMessage(RpgMsg.tr("command.npcspike.clip_attempted", trimmed).color(Color.GREEN));
        Log.info("[npcspike] clip attempted for " + player.getUuid() + ": " + trimmed);
    }

    /**
     * Despawns every {@link NPCEntity} in {@code store} whose role is {@link #ROLE_ID}, returning
     * how many matched. Runs on the world thread (the caller is inside {@code world.execute}); the
     * removal is queued through the parallel iteration's {@code CommandBuffer} (the
     * {@code NPCCleanCommand} first-party precedent) and applied when the sweep completes, so a
     * ref this player's own {@link #despawnFor} already removed is a no-op via
     * {@code tryRemoveEntity}. Never throws.
     */
    private static int sweepStrandedPerformers(@Nonnull Store<EntityStore> store) {
        java.util.concurrent.atomic.AtomicInteger matched = new java.util.concurrent.atomic.AtomicInteger();
        try {
            store.forEachEntityParallel(NPCEntity.getComponentType(), (index, chunk, cmdBuffer) -> {
                NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
                if (npc == null || !ROLE_ID.equals(npc.getRoleName())) {
                    return;
                }
                matched.incrementAndGet();
                cmdBuffer.tryRemoveEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE);
            });
        } catch (Throwable t) {
            Log.warn("[npcspike] performer sweep failed: " + t.getMessage());
        }
        return matched.get();
    }

    // ==================== helpers ====================

    /** Removes (and forgets) any spawned NPC + marker owned by {@code uuid}. World thread. */
    private void despawnFor(@Nonnull UUID uuid, @Nonnull Store<EntityStore> store) {
        Spawned spawned = byPlayer.remove(uuid);
        if (spawned == null) {
            return;
        }
        removeRef(spawned.markerRef, store);
        removeRef(spawned.npcRef, store);
    }

    /** Spawns a bare, invisible (no ModelComponent), NonSerialized marker entity at {@code pos}. */
    @Nullable
    private static Ref<EntityStore> spawnMarker(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        try {
            Rotation3f rot = new Rotation3f(0f, 0f, 0f);
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            Log.warn("[npcspike] spawnMarker failed: " + t.getMessage());
            return null;
        }
    }

    /** Removes {@code ref} if valid (no-op, never throws, otherwise). */
    private static void removeRef(@Nullable Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
        } catch (Throwable t) {
            Log.warn("[npcspike] removeEntity failed: " + t.getMessage());
        }
    }

    /** A horizontal point {@code distance} blocks ahead of {@code from} along {@code yaw}, same Y. */
    @Nonnull
    private static Vector3d ahead(@Nonnull Vector3d from, float yaw, double distance) {
        Vector3d forward = new Vector3d();
        PhysicsMath.vectorFromAngles(yaw, 0f, forward);
        forward.y = 0.0;
        if (forward.lengthSquared() < 1.0e-6) {
            forward.set(0.0, 0.0, 1.0);
        }
        forward.normalize(distance);
        return new Vector3d(from).add(forward);
    }

    /**
     * Resolves {@code player}'s world + entity ref, then runs {@code op} on the world thread.
     * Chat-errors (thread-safe) and returns on any missing piece.
     */
    private void onWorld(@Nonnull PlayerRef player, @Nonnull WorldOp op) {
        try {
            UUID worldUuid = player.getWorldUuid();
            World world = worldUuid != null ? Universe.get().getWorld(worldUuid) : null;
            Ref<EntityStore> playerRef = player.getReference();
            if (world == null || !world.isAlive() || playerRef == null || !playerRef.isValid()) {
                player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
                return;
            }
            world.execute(() -> {
                try {
                    Ref<EntityStore> ref = player.getReference();
                    if (ref == null || !ref.isValid()) {
                        player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
                        return;
                    }
                    op.run(world, ref, ref.getStore());
                } catch (Throwable t) {
                    Log.warn("[npcspike] world-thread op failed: " + t.getMessage());
                    player.sendMessage(RpgMsg.tr("command.npcspike.spawn_failed").color(Color.RED));
                }
            });
        } catch (Throwable t) {
            Log.warn("[npcspike] onWorld dispatch failed: " + t.getMessage());
        }
    }

    /** A world-thread operation over the caller's own {@link World}/{@link Ref}/{@link Store}. */
    @FunctionalInterface
    private interface WorldOp {
        void run(@Nonnull World world, @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store);
    }
}
