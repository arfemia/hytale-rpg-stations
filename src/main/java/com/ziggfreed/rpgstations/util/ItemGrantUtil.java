package com.ziggfreed.rpgstations.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.inventory.InventoryGrant;

/**
 * THE ONE shared item-GRANT seam this mod's grant call sites route through (round-5 maintainer
 * directive, 2026-07-22): every item GRANT this mod hands a player - placed-input custody
 * retrieval/return ({@code station.StationService#giveClaimToOwner}), a per-cycle produced output
 * ({@code station.StationStepHandlers.ProduceHandler}), a luck bonus-output-copy grant, and a
 * rare-find/tier {@code ItemDropList} grant (both {@code loot.LootEngine}) - routes through
 * {@link #grant} instead of re-deriving the container cascade + drop-at-block fallback at each
 * call site.
 *
 * <p><b>AMENDED same day (maintainer common-lift directive)</b>: the GENERIC hotbar-first-then-
 * backpack ORDERING primitive itself lives in {@code ziggfreed-common}'s {@code
 * inventory.InventoryGrant} (a mod-agnostic Hytale primitive, per the root lift paradigm), not
 * re-derived here. This class owns ONLY this mod's own POLICY over that shared engine: the
 * drop-at-block fallback target ({@link ItemDropUtil}, this mod's existing SMOKE-FIX S3 (b) world-
 * drop sink) - never the hotbar/backpack ordering itself.
 *
 * <p><b>THE HISTORIC CONSUME-SIDE CAVEAT (do NOT port this rule to a consume/drain path)</b>: see
 * {@code common.inventory.InventoryGrant}'s own javadoc for the full rationale - this mod's
 * CONSUME side ({@code station.StationService}'s per-cycle Convert drain, the held-tool gate/
 * durability reads) deliberately still prefers BACKPACK storage over the combined hotbar+storage
 * view (mutating the hotbar fans an Equipment update to every viewer, including the acting player,
 * under the server camera - a correlated client rendering issue in this mod's own pre-extraction
 * smoke testing). This GRANT-side hotbar-first rule is a DELIBERATELY DIFFERENT, maintainer-
 * directed exception (round-5): a per-cycle Produce/Roll grant now fires WHILE a station session's
 * server camera is typically locked - a previously-unexercised code path for this mod - so watch
 * for the SAME client symptom in the next in-game smoke pass. Never widen this rule to a held/
 * active-slot MUTATION (consume) path.
 */
public final class ItemGrantUtil {

    private ItemGrantUtil() {
    }

    /**
     * Hotbar-first, then backpack storage, then drop-at-block ({@link ItemDropUtil}) - this mod's
     * SMOKE-FIX S3 (b) "a grant is never silently skipped" ruling, unchanged. Never throws.
     */
    @Nonnull
    public static InventoryGrant.Landed grant(@Nonnull Player player, @Nonnull ItemStack stack,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nullable Store<EntityStore> store,
            int blockX, int blockY, int blockZ) {
        return InventoryGrant.grant(player, stack,
                dropped -> ItemDropUtil.dropAtBlock(commandBuffer, store, blockX, blockY, blockZ, dropped));
    }

    /**
     * As {@link #grant}, but answers the question a caller actually needs before telling the player
     * they received something: did this stack reach the player AT ALL?
     *
     * <p>{@code false} means it went nowhere - no inventory room and the ground drop failed too -
     * so the item no longer exists and must not be counted, notified, or added to a session
     * summary. The plain {@link #grant} overload cannot express that: its {@code FALLBACK} result
     * only says the drop was ATTEMPTED, which is how a failed drop came to be reported to players
     * as a successful find.
     */
    /**
     * Hotbar-then-backpack ONLY, with NO ground fallback: returns {@code false} when the stack did
     * not fit anywhere, leaving it to the caller.
     *
     * <p>For a caller granting SEVERAL stacks at once. Dropping each leftover the moment it fails
     * scatters one payout across several ground entities, so a player walking up to the pile sees
     * only part of what they were told they received. Collect the failures and make ONE
     * {@link ItemDropUtil#dropAtBlock} call with all of them instead, and the pile equals the
     * remainder. A single-stack caller wants {@link #grantOrDrop}.
     */
    public static boolean grantToInventory(@Nonnull Player player, @Nonnull ItemStack stack) {
        boolean[] overflowed = {false};
        InventoryGrant.grant(player, stack, dropped -> overflowed[0] = true);
        return !overflowed[0];
    }

    public static boolean grantOrDrop(@Nonnull Player player, @Nonnull ItemStack stack,
            @Nullable CommandBuffer<EntityStore> commandBuffer, @Nullable Store<EntityStore> store,
            int blockX, int blockY, int blockZ) {
        boolean[] droppedOk = {false};
        InventoryGrant.Landed landed = InventoryGrant.grant(player, stack,
                dropped -> droppedOk[0] = ItemDropUtil.dropAtBlock(commandBuffer, store, blockX, blockY, blockZ, dropped));
        return landed != InventoryGrant.Landed.FALLBACK || droppedOk[0];
    }
}
