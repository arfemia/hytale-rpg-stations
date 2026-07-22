package com.ziggfreed.rpgstations.util;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * SMOKE-FIX S3 (b): the ONE world-drop sink every "no room in the inventory" fallback in this mod
 * routes through - the native mob-death drop mechanism ({@link ItemComponent#generateItemDrops}
 * + {@code store.addEntity}, per the hytale-source-search LEDGER's "World placement differs by
 * call site" entry). MAINTAINER DIRECTIVE (2026-07-22): a grant that cannot fit in the target
 * inventory MUST land as a ground item at the block instead of being skipped - this applies to
 * placed-input custody returns ({@code station.StationService#returnCustody}), luck bonus-copy
 * grants, and rare-find/tier loot grants ({@code loot.LootEngine}) alike, superseding the older
 * "room-checked, skipped silently when full" convention. Lifted out of {@code StationService}'s
 * original private {@code dropCustodyAtBlock} so {@code loot.LootEngine} (a different package)
 * can reuse the SAME sink rather than re-deriving it - one drop mechanism, several callers.
 */
public final class ItemDropUtil {

    private ItemDropUtil() {
    }

    /**
     * Drops {@code stacks} at the block center ({@code x+0.5, y+1.0, z+0.5}) via the native
     * dropped-item spawn. Never throws; a {@code null}/unresolvable {@code store} or an empty
     * list is a no-op (logged when items would otherwise be silently lost).
     */
    public static void dropAtBlock(@Nullable Store<EntityStore> store, int x, int y, int z,
            @Nonnull List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        if (store == null) {
            Log.warn("STATION items lost - no store available to drop at (" + x + "," + y + "," + z + ")");
            return;
        }
        try {
            Vector3d pos = new Vector3d(x + 0.5, y + 1.0, z + 0.5);
            var holders = ItemComponent.generateItemDrops(store, stacks, pos, Rotation3f.IDENTITY);
            for (var holder : holders) {
                store.addEntity(holder, AddReason.SPAWN);
            }
        } catch (Throwable t) {
            Log.warn("STATION drop-at-block failed: " + t.getMessage());
        }
    }

    /** Single-stack convenience over {@link #dropAtBlock(Store, int, int, int, List)}. */
    public static void dropAtBlock(@Nullable Store<EntityStore> store, int x, int y, int z,
            @Nonnull ItemStack stack) {
        dropAtBlock(store, x, y, z, List.of(stack));
    }
}
