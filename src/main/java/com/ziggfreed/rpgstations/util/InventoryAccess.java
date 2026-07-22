package com.ziggfreed.rpgstations.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The non-deprecated replacements for {@code Player}'s {@code @Deprecated(forRemoval = true)}
 * accessors (maintainer deprecation-sweep edict, 2026-07-22): every player storage / hotbar /
 * combined-inventory / {@link PlayerRef} read routes through here instead of duplicating the
 * ref/store null-guard at every call site, the same shape {@code loot.LootEngine}'s own
 * {@code storageContainerOf} already established for the storage-only case. Each method here is
 * the EXACT replacement named on the deprecated method's own javadoc, never a guessed
 * alternative (e.g. {@code InventoryComponent#getItemInHand} was deliberately NOT used for
 * {@link #activeHotbarItemOf} - it also folds in the {@code Tool} component, a different
 * semantic than the deprecated {@code getActiveHotbarItem()} it replaces). {@code null}
 * whenever the player's ref/store cannot be resolved or the requested component is absent;
 * never throws.
 */
public final class InventoryAccess {

    private InventoryAccess() {
    }

    /**
     * Replaces {@code Player.getInventory().getStorage()} ({@code Inventory#getStorage}'s own
     * javadoc: fetch the {@link InventoryComponent.Storage} component directly).
     */
    @Nullable
    public static ItemContainer storageOf(@Nonnull Player player) {
        Ref<EntityStore> ref = refOf(player);
        if (ref == null) {
            return null;
        }
        InventoryComponent.Storage storage = ref.getStore()
                .getComponent(ref, InventoryComponent.Storage.getComponentType());
        return storage != null ? storage.getInventory() : null;
    }

    /**
     * Replaces {@code Player.getInventory().getActiveHotbarItem()} ({@code
     * Inventory#getActiveHotbarItem}'s own javadoc: fetch the {@link InventoryComponent.Hotbar}
     * component and call {@code getActiveItem()}).
     */
    @Nullable
    public static ItemStack activeHotbarItemOf(@Nonnull Player player) {
        Ref<EntityStore> ref = refOf(player);
        if (ref == null) {
            return null;
        }
        InventoryComponent.Hotbar hotbar = ref.getStore()
                .getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        return hotbar != null ? hotbar.getActiveItem() : null;
    }

    /**
     * Replaces {@code Player.getInventory().getCombinedBackpackStorageHotbar()} ({@code
     * Inventory#getCombinedBackpackStorageHotbar}'s own javadoc: use the {@link
     * InventoryComponent#getCombined} equivalent, over the same {@link
     * InventoryComponent#BACKPACK_STORAGE_HOTBAR} priority order the deprecated method used).
     */
    @Nullable
    public static ItemContainer combinedBackpackStorageHotbarOf(@Nonnull Player player) {
        Ref<EntityStore> ref = refOf(player);
        if (ref == null) {
            return null;
        }
        return InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.BACKPACK_STORAGE_HOTBAR);
    }

    /**
     * Replaces {@code Player.getPlayerRef()} ({@code Player#getPlayerRef}'s own javadoc: fetch
     * the {@link PlayerRef} component manually), the same fetch {@code RpgStationsPlugin
     * #registerSummaryHudInstall} already established.
     */
    @Nullable
    public static PlayerRef playerRefOf(@Nonnull Player player) {
        Ref<EntityStore> ref = refOf(player);
        if (ref == null) {
            return null;
        }
        return ref.getStore().getComponent(ref, PlayerRef.getComponentType());
    }

    @Nullable
    private static Ref<EntityStore> refOf(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        return (ref != null && ref.isValid()) ? ref : null;
    }
}
