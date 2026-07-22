package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * One block's live, in-memory placed-input custody claim (design section 9.4, phase-2 leg C):
 * WHO placed it and WHAT is in it, keyed by the owner's uuid and a {@code (itemId -> quantity)}
 * tally (insertion-ordered, oldest-placed-first - the drain order {@link StationCustody#drain}
 * walks). Never persisted (session-scoped by ruling, the same no-per-player-persistence
 * constraint every other in-memory session type in this mod honors); a server restart or crash
 * loses an in-flight claim (accepted, documented) - the block's own {@code State.Definitions}
 * flip is the only thing that survives, which is why {@code StationService} self-heals a
 * Loaded-state block with no matching live claim back to Empty on the next interaction.
 */
final class StationCustodyClaim {

    @Nonnull final UUID ownerId;
    @Nonnull final String stationId;
    @Nonnull final String actionId;

    /** {@code itemId -> quantity}, insertion order = placement order (oldest first). */
    private final Map<String, Integer> items = new LinkedHashMap<>();

    StationCustodyClaim(@Nonnull UUID ownerId, @Nonnull String stationId, @Nonnull String actionId) {
        this.ownerId = ownerId;
        this.stationId = stationId;
        this.actionId = actionId;
    }

    /** The live, mutable tally - {@link StationCustody}'s drain/available cores iterate + mutate this directly. */
    @Nonnull
    Map<String, Integer> items() {
        return items;
    }

    void add(@Nonnull String itemId, int quantity) {
        if (quantity > 0) {
            items.merge(itemId, quantity, Integer::sum);
        }
    }

    int totalQuantity() {
        int total = 0;
        for (int q : items.values()) {
            total += q;
        }
        return total;
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    /** One concrete {@link ItemStack} per tallied item id, for the auto-return path. */
    @Nonnull
    List<ItemStack> toItemStacks() {
        List<ItemStack> out = new ArrayList<>(items.size());
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.add(new ItemStack(e.getKey(), e.getValue()));
            }
        }
        return out;
    }
}
