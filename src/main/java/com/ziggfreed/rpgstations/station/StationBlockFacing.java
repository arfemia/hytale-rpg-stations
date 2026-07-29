package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The ONE reader of a placed station block's own facing yaw, plus the ONE horizontal
 * facing-relative rotation core every consumer composes against it (round-8 for
 * {@link StationCustodyDisplay}'s {@code Custody.Display}, round-3-smoke for
 * {@link StationPuppetController}'s {@code Puppet.Offset}/{@code Yaw}). Extracted so the block-yaw
 * read and its trig live in exactly one place - a second consumer copies the CALL, never the
 * mechanism.
 *
 * <p><b>Authoring convention (identical at every consumer):</b> an authored horizontal offset is in
 * the BLOCK'S OWN frame - {@code +Z} is the block's FRONT, {@code +X} its right - and an authored
 * yaw is relative to the block's own facing (the block yaw folds into it additively). {@code Y} is
 * the vertical axis and is NEVER rotated. At a DEFAULT-orientation placement
 * ({@code blockYawRadians == 0}) the whole composition is the IDENTITY ({@code cos 0 = 1},
 * {@code sin 0 = 0}), so every value tuned before a consumer adopted this composition renders
 * BYTE-IDENTICALLY - no re-tune, no migration.
 *
 * <p><b>Fail-soft:</b> every read here is try-guarded to yaw {@code 0.0} (an unloaded chunk, a
 * null-facing read, an unresolvable world, any throw), which degrades exactly to the pre-adoption
 * WORLD-SPACE behavior rather than aborting whatever spawn asked for it.
 */
final class StationBlockFacing {

    private StationBlockFacing() {
    }

    /**
     * Impure: the placed block's own facing yaw at {@code (blockX, blockY, blockZ)}, in radians.
     * Reads the non-deprecated {@code World#getBlockRotationIndex} (a placed block's rotation is a
     * discrete {@link RotationTuple} of 0/90/180/270 yaw/pitch/roll enums) and returns just the
     * yaw's radians. Try-guarded to {@code 0.0}; a {@code null} {@code world} is {@code 0.0} too.
     * WORLD-THREAD ONLY (matching the {@code getBlockRotationIndex} chunk-read contract).
     */
    static double yawRadians(@Nullable World world, int blockX, int blockY, int blockZ) {
        if (world == null) {
            return 0.0;
        }
        try {
            int index = world.getBlockRotationIndex(blockX, blockY, blockZ);
            return RotationTuple.get(index).yaw().getRadians();
        } catch (Throwable t) {
            Log.fine("STATION could not read block facing at " + blockX + "," + blockY + "," + blockZ
                    + " - falling back to world-space placement: " + t.getMessage());
            return 0.0;
        }
    }

    /**
     * Impure: {@link #yawRadians(World, int, int, int)} for a caller that holds only a
     * {@link CommandBuffer} - resolves the {@link World} off its store through the engine-stable
     * {@code store -> externalData -> world} chain ({@link WorldEvictors#worldOf(
     * com.hypixel.hytale.component.Store)}). Try-guarded to {@code 0.0} on an unresolvable world.
     */
    static double yawRadians(@Nonnull CommandBuffer<EntityStore> commandBuffer, int blockX, int blockY, int blockZ) {
        World world;
        try {
            world = WorldEvictors.worldOf(commandBuffer.getStore());
        } catch (Throwable t) {
            Log.fine("STATION could not resolve the world for a block-facing read at " + blockX + ","
                    + blockY + "," + blockZ + " - falling back to world-space placement: " + t.getMessage());
            return 0.0;
        }
        return yawRadians(world, blockX, blockY, blockZ);
    }

    /**
     * PURE: a facing-relative {@code (x, y, z)} offset rotated into world space by
     * {@code blockYawRadians}, with {@code y} left VERTICAL (never rotated). Uses the engine's own
     * block-vector yaw convention ({@code Rotation.rotateY}: {@code x' = x*cos(yaw) + z*sin(yaw)},
     * {@code z' = -x*sin(yaw) + z*cos(yaw)}), so an authored {@code +Z} tracks the block's front for
     * any placement orientation. Returns {@code [worldX, y, worldZ]} - primitive-typed, so it needs
     * no live Hytale type and stays unit-testable without a running server.
     */
    @Nonnull
    static double[] rotateOffset(double x, double y, double z, double blockYawRadians) {
        double cos = Math.cos(blockYawRadians);
        double sin = Math.sin(blockYawRadians);
        return new double[] {x * cos + z * sin, y, -x * sin + z * cos};
    }
}
