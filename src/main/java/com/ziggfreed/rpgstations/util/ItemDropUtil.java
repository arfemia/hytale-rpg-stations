package com.ziggfreed.rpgstations.util;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The ONE world-drop sink every "no room in the inventory" fallback in this mod
 * routes through - the native mob-death drop mechanism ({@link ItemComponent#generateItemDrops}
 * + a tick-safe spawn). A grant that cannot fit in the target
 * inventory MUST land as a ground item at the block instead of being skipped - this applies to
 * placed-input custody returns ({@code station.StationService#returnCustody}), luck bonus-copy
 * grants, and rare-find/tier loot grants ({@code loot.LootEngine}) alike, superseding the older
 * "room-checked, skipped silently when full" convention. Lifted out of {@code StationService}'s
 * original private {@code dropCustodyAtBlock} so {@code loot.LootEngine} (a different package)
 * can reuse the SAME sink rather than re-deriving it - one drop mechanism, several callers.
 *
 * <p><b>Pass the live {@code commandBuffer} from anything running inside a tick.</b> Spawning an
 * item entity through a live {@code Store} is rejected while that store is processing, so an
 * in-tick caller that hands over only a store loses the drop; the buffer route queues the spawn
 * instead and always lands. Only a caller genuinely outside any tick - one hopping onto the world
 * thread through {@code World#execute} - has no buffer to give, and the store route is there to
 * serve exactly that case. A store-route drop that fails says so in its own words rather than as a
 * generic failure, and names the caller, so a buffer that should have been passed shows up in the
 * log the first time it costs a player their items.
 */
public final class ItemDropUtil {

    private ItemDropUtil() {
    }

    /**
     * Drops {@code stacks} at the block center ({@code x+0.5, y+1.0, z+0.5}) via the native
     * dropped-item spawn. Never throws; a {@code null}/unresolvable {@code store} or an empty
     * list is a no-op (logged when items would otherwise be silently lost).
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
        Holder<EntityStore>[] holders;
        try {
            holders = ItemComponent.generateItemDrops(store, stacks, pos, Rotation3f.IDENTITY);
        } catch (Throwable t) {
            Log.warn("STATION items lost - could not build drops: " + t.getMessage());
            return false;
        }
        if (holders == null || holders.length == 0) {
            return false;
        }
        int spawned = 0;
        try {
            for (Holder<EntityStore> holder : holders) {
                if (commandBuffer != null) {
                    commandBuffer.addEntity(holder, AddReason.SPAWN);
                } else {
                    store.addEntity(holder, AddReason.SPAWN);
                }
                spawned++;
            }
            return true;
        } catch (Throwable t) {
            int lost = holders.length - spawned;
            if (commandBuffer == null) {
                // The store route is only legal outside a tick. Say so outright, and name the
                // caller, so the next time someone reaches this sink from inside one the log points
                // straight at the frame that should have handed over its CommandBuffer.
                Log.warn("STATION items lost - " + lost + " ground drop(s) at (" + x + "," + y + "," + z
                        + ") went nowhere because this sink was given a Store and no CommandBuffer, and"
                        + " the store would not accept a spawn: " + t.getMessage()
                        + ". Called from " + callerFrame() + "; pass the live CommandBuffer from any"
                        + " caller running inside a tick.", t);
            } else {
                Log.warn("STATION items lost - " + lost + " ground drop(s) at (" + x + "," + y + "," + z
                        + ") failed: " + t.getMessage(), t);
            }
            return false;
        }
    }

    /**
     * The first frame outside this class, as {@code Class#method:line} - who asked for the drop.
     * Only ever walked on a failure path, never in the normal flow. Falls back to a plain marker
     * when the walk itself cannot answer, because a diagnostic must never become the failure.
     */
    @Nonnull
    private static String callerFrame() {
        try {
            return StackWalker.getInstance()
                    .walk(frames -> frames
                            .filter(f -> !ItemDropUtil.class.getName().equals(f.getClassName()))
                            .findFirst()
                            .map(f -> f.getClassName() + "#" + f.getMethodName() + ":" + f.getLineNumber())
                            .orElse("an unknown caller"));
        } catch (Throwable ignored) {
            return "an unknown caller";
        }
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
