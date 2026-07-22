package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Policy-thin glue over the native {@code BlockMountAPI} for {@code Hold.Mount.Surface}
 * {@code "Block"} - today's seat mount, UNCHANGED behind the design-9.2 Mount knob family
 * (phase 2 leg D) refactor; the regression anchor. Formerly gated by {@code Hold.Seat.Enabled}
 * (unreleased rename, no alias). Ported verbatim from the MMO's
 * {@code station.StationMountController} (RPG Stations extraction leg 2), {@code SafeLog}
 * severed to RpgStations' own {@code util.Log}. Sibling: {@link StationEntityMountController}
 * (the {@code "Entity"} surface - the standing work mount).
 *
 * <p><b>Why this route exists:</b> the packet-camera preset hunt ({@link StationCameraPreset})
 * never found a combination that combines a FREE mouse-orbitable camera with a LOCKED player
 * body/head. Mounting the player on the station block via the native {@code BlockMountAPI}
 * sidesteps the packet hunt entirely: the client already renders its own free-orbit camera
 * for a seated/mounted entity.
 *
 * <p><b>The one load-bearing, unresolved risk (documented, not solved here):</b> nothing
 * server-side stops the CLIENT from continuing to send mouse-derived {@code bodyOrientation}
 * in its {@code ClientMovement} packets while seated. Whether the CLIENT itself suppresses it
 * while visually seated cannot be confirmed from server source; this is an in-game
 * verification item.
 *
 * <p>Requires the target block to author {@code BlockType.Seats[]} - {@link #mount} returns
 * {@code false} (never throws) when it does not.
 */
final class StationMountController {

    private StationMountController() {
    }

    /**
     * Attempt to seat {@code ref} on the block at {@code (blockX, blockY, blockZ)}, nearest to
     * {@code interactPos} among the block's available (unoccupied) authored seats. Returns
     * {@code true} only on {@link BlockMountAPI.Mounted}.
     */
    static boolean mount(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            int blockX, int blockY, int blockZ, @Nonnull Vector3d interactPos) {
        try {
            BlockMountAPI.BlockMountResult result = BlockMountAPI.mountOnBlock(
                    ref, commandBuffer, new Vector3i(blockX, blockY, blockZ), interactPos);
            if (result instanceof BlockMountAPI.Mounted) {
                return true;
            }
            Log.fine("STATION seat mount denied at (" + blockX + "," + blockY + "," + blockZ + "): " + result);
            return false;
        } catch (Throwable t) {
            Log.warn("STATION seat mount failed: " + t.getMessage(), t);
            return false;
        }
    }

    /**
     * Dismount {@code ref}, the SAME call shape the native {@code DismountCommand}/
     * {@code MountGamePacketHandler} use. A no-op (never throws) when already unmounted.
     */
    static void dismount(@Nullable Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        try {
            store.tryRemoveComponent(ref, MountedComponent.getComponentType());
        } catch (Throwable t) {
            Log.fine("STATION seat dismount failed: " + t.getMessage());
        }
    }

    /**
     * Whether {@code ref} still carries a {@link MountedComponent} - the heartbeat's
     * natural-dismount detector.
     */
    static boolean isMounted(@Nullable Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return false;
        }
        try {
            return store.getComponent(ref, MountedComponent.getComponentType()) != null;
        } catch (Throwable t) {
            Log.fine("STATION seat mount-state read failed: " + t.getMessage());
            return false;
        }
    }
}
