package com.ziggfreed.rpgstations.util;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.instance.reward.NativeLootService;

/**
 * The ONE world-drop sink every "no room in the inventory" fallback in this mod routes through:
 * the station block's ground, over ziggfreed-common's {@link NativeLootService#spawnInWorld}. A
 * grant that cannot fit in the target inventory MUST land as a ground item at the block instead of
 * being skipped - this applies to placed-input custody returns
 * ({@code station.StationService#returnCustody}), every single-stack grant's fallback
 * ({@link ItemGrantUtil}), and the station loot pass alike. The loot pass
 * ({@code loot.StationLootEngine}) hands its items and drop lists over through ziggfreed-common's
 * {@code loot.GroundSpillSinks}, with this sink as its ground, so whatever a pass cannot fit lands
 * here as ONE pile. This class owns only where the station's ground is (the block centre, one
 * block up); the spawn itself is the library's.
 *
 * <p><b>Pass the live {@code commandBuffer} from anything running inside a tick.</b> With a buffer
 * the spawn takes the library's tick-safe pair form and lands in the same tick's flush. Only a
 * caller genuinely outside any tick - one hopping onto the world thread through
 * {@code World#execute} - has no buffer to give; it takes the one-accessor form, which re-queues a
 * spawn the store refuses mid-tick onto the world thread, so the drop lands a tick later instead of
 * being lost.
 */
public final class ItemDropUtil {

    private ItemDropUtil() {
    }

    /**
     * Drops {@code stacks} at the block center ({@code x+0.5, y+1.0, z+0.5}) via the native
     * dropped-item spawn. Never throws; an empty list is a no-op, and a {@code null} {@code store}
     * loses the drop (logged). Answers whether the drop landed or was queued to land.
     */
    public static boolean dropAtBlock(@Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nullable Store<EntityStore> store, int x, int y, int z, @Nonnull List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return true;
        }
        if (store == null) {
            Log.warn("STATION items lost - no store available to drop at (" + x + "," + y + "," + z + ")");
            return false;
        }
        Vector3d pos = new Vector3d(x + 0.5, y + 1.0, z + 0.5);
        return commandBuffer != null
                ? NativeLootService.spawnInWorld(store, commandBuffer, pos, new Rotation3f(Rotation3f.IDENTITY), stacks)
                : NativeLootService.spawnInWorld(store, pos, Rotation3f.IDENTITY, stacks);
    }

    /**
     * Single-stack convenience. Returns whether the stack actually reached the ground, so a caller
     * can avoid reporting an item the player never received.
     */
    public static boolean dropAtBlock(@Nullable CommandBuffer<EntityStore> commandBuffer,
            @Nullable Store<EntityStore> store, int x, int y, int z, @Nonnull ItemStack stack) {
        return dropAtBlock(commandBuffer, store, x, y, z, List.of(stack));
    }
}
