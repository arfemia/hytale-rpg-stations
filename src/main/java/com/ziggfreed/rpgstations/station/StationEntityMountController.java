package com.ziggfreed.rpgstations.station;

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Policy-thin glue over the native entity-mount mechanics for {@code Hold.Mount.Surface}
 * {@code "Entity"} (design section 9.2, phase 2 leg D) - the STANDING work mount, sibling to
 * {@link StationMountController} (the unchanged Block-surface / native-seat route).
 *
 * <p><b>Mechanism (mount-mine Route 1, highest confidence):</b> at engage, spawn a minimal
 * anchor entity at the station block's center, then attach {@code MountedComponent(anchorRef,
 * attachmentOffset, MountController.Minecart)} to the player DIRECTLY via the public
 * constructor - the exact component {@code MountInteraction.firstRun} adds, but with no
 * interaction chain needed since the plugin attaches it itself (there is no player-facing
 * "press F on the anchor" step; the station's own F-interaction already engaged the session).
 * Because this path never populates the client's {@code MountedUpdate.Block} field (that leaf
 * is exclusive to the {@code BlockMount} controller), the mount mine infers the player renders
 * STANDING by construction - the strongest source-backed inference, but genuinely
 * in-game-unverifiable from server source alone (the maintainer's phase-2 smoke item).
 *
 * <p><b>CRITIQUE FIX (m7) - the attachment offset is a {@code Rotation3f}, NOT a
 * {@code Vector3f}.</b> {@code MountedComponent}'s entity-mount constructor takes a
 * {@code Rotation3f attachmentOffset} parameter that the engine actually uses as a plain
 * spatial XYZ offset for entity mounts (a native mislabeling, confirmed in the mount mine and
 * mirrored by {@code MountInteraction}'s own codec, which decodes an authored {@code Vector3f}
 * straight into a {@code Rotation3f} field). {@link #resolveAttachmentOffset} does the
 * conversion explicitly, kept primitive-typed (no {@code Rotation3f} touch) so it stays
 * unit-testable without a running Hytale server; {@link #attach} is the one place that actually
 * constructs the {@code Rotation3f}.
 *
 * <p><b>The steering/drift risk (documented, not solved here):</b> the native entity-mount
 * controller ({@code MountController.Minecart}) has NO auto-dismount and applies WASD input
 * DIRECTLY to the anchor entity's own transform ({@code HandleMountInput}, the same asymmetry
 * that exempts entity mounts from the Block-mount 600ms-grace auto-dismount). The default
 * ({@code Steerable} false) mitigation is two-layered: the session applies the SAME hold effect
 * effect-mode uses (via {@code StationHoldController}) to defeat client-sent movement input, AND
 * the heartbeat calls {@link #snapBack} every tick to re-assert the anchor's authored transform
 * against whatever drift slips through. Neither mitigation is proven sufficient against
 * client-trust {@code SetBody} (mouse-look) drift - the SAME unresolved-risk class the seated
 * {@code bodyOrientation} question already carries for the Block route; an in-game verification
 * item, not solved in code.
 *
 * <p><b>Anchor minimal component set - a phase-2 SPIKE default, iterate in-game.</b>
 * {@link #spawnAnchor} starts from {@code SpawnMinecartInteraction}'s own component list MINUS
 * the cart/model leaves ({@code MinecartComponent}, {@code ModelComponent}/{@code
 * PersistentModel}/{@code BoundingBox} - the anchor has no visual model and no physical
 * presence): {@code TransformComponent} (position/rotation) + {@code UUIDComponent} (parity with
 * every spawned entity) + {@code Interactable}/{@code Interactions} (an empty map - kept only
 * because the minecart precedent carries them; harmless if the anchor is never itself
 * interacted-with, and cheap to drop later if it proves unnecessary). Iteration knobs for the
 * maintainer's in-game spike: whether a {@code BoundingBox} is needed for the anchor to register
 * as a valid network-tracked mount target at all (untested - if the standing pose fails to
 * render, this is the first thing to add back), whether {@code Interactable}/{@code Interactions}
 * are load-bearing or dead weight, and whether the anchor needs a {@code Visible}-family
 * component for {@code TrackerUpdate} to pick it up promptly.
 */
final class StationEntityMountController {

    private StationEntityMountController() {
    }

    /**
     * Spawn the minimal anchor entity at the station block's center. Returns {@code null} (never
     * throws) on any failure - the caller denies the engage with a toast, matching
     * {@link StationMountController#mount}'s contract.
     */
    @Nullable
    static Ref<EntityStore> spawnAnchor(@Nonnull CommandBuffer<EntityStore> commandBuffer,
            int blockX, int blockY, int blockZ) {
        try {
            Vector3d position = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
            Rotation3f rotation = new Rotation3f(0f, 0f, 0f);
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.ensureComponent(Interactable.getComponentType());
            holder.addComponent(Interactions.getComponentType(), new Interactions(Collections.emptyMap()));
            return commandBuffer.addEntity(holder, AddReason.SPAWN);
        } catch (Throwable t) {
            Log.warn("STATION entity-mount anchor spawn failed: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Attach {@code MountedComponent(anchorRef, attachmentOffset, MountController.Minecart)} to
     * {@code playerRef} directly - no interaction chain, matching
     * {@code MountInteraction.firstRun}'s own component shape. Returns {@code false} (never
     * throws) on failure.
     */
    static boolean attach(@Nonnull Ref<EntityStore> playerRef, @Nonnull Ref<EntityStore> anchorRef,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nullable StationAsset.Hold.Mount.Entity entityGroup) {
        try {
            StationAsset.Hold.Mount.Entity.Offset offset = entityGroup != null ? entityGroup.getOffset() : null;
            float[] xyz = resolveAttachmentOffset(
                    offset != null ? offset.getX() : null,
                    offset != null ? offset.getY() : null,
                    offset != null ? offset.getZ() : null);
            // CRITIQUE FIX (m7): the constructor parameter is a Rotation3f, not a Vector3f - see
            // this class's header javadoc for why.
            Rotation3f attachmentOffset = new Rotation3f(xyz[0], xyz[1], xyz[2]);
            commandBuffer.addComponent(playerRef, MountedComponent.getComponentType(),
                    new MountedComponent(anchorRef, attachmentOffset, MountController.Minecart));
            return true;
        } catch (Throwable t) {
            Log.warn("STATION entity mount attach failed: " + t.getMessage(), t);
            return false;
        }
    }

    /**
     * Pure conversion from the authored {@code Hold.Mount.Entity.Offset} {@code X}/{@code Y}/
     * {@code Z} (nullable, default 0 each) to the float triple {@link #attach} feeds the
     * {@code Rotation3f} constructor. Kept primitive-typed (no {@code Rotation3f} or
     * {@code Offset} touch) so it is unit-testable without a running Hytale server.
     */
    @Nonnull
    static float[] resolveAttachmentOffset(@Nullable Double x, @Nullable Double y, @Nullable Double z) {
        return new float[] {
                x != null ? x.floatValue() : 0f,
                y != null ? y.floatValue() : 0f,
                z != null ? z.floatValue() : 0f
        };
    }

    /**
     * Re-assert the anchor's authored spawn transform (block center, zero rotation) - the
     * per-heartbeat drift mitigation for the default ({@code Steerable} false) case. No-op
     * (never throws) once the anchor is gone.
     */
    static void snapBack(@Nullable Ref<EntityStore> anchorRef, @Nonnull Store<EntityStore> store,
            int blockX, int blockY, int blockZ) {
        if (anchorRef == null || !anchorRef.isValid()) {
            return;
        }
        try {
            TransformComponent transform = store.getComponent(anchorRef, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            transform.setPosition(new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5));
            transform.setRotation(new Rotation3f(0f, 0f, 0f));
        } catch (Throwable t) {
            Log.fine("STATION entity-mount anchor snap-back failed: " + t.getMessage());
        }
    }

    /**
     * Despawn the anchor (session-scoped lifecycle - the ONE idempotent {@code stop()} funnel
     * calls this on every exit path, {@code stopAll}'s shutdown sweep included). No-op (never
     * throws) when already gone or {@code store} could not be resolved.
     */
    static void despawn(@Nullable Ref<EntityStore> anchorRef, @Nullable Store<EntityStore> store) {
        if (anchorRef == null || !anchorRef.isValid() || store == null) {
            return;
        }
        try {
            store.removeEntity(anchorRef, RemoveReason.REMOVE);
        } catch (Throwable t) {
            Log.fine("STATION entity-mount anchor despawn failed: " + t.getMessage());
        }
    }
}
