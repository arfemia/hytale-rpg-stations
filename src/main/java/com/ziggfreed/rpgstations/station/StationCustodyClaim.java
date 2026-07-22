package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    /**
     * The REAL placed {@link ItemStack} (metadata intact - durability, any prior enhancement),
     * for a single-item, metadata-preserving custody placement ONLY (the anvil's Enhance action,
     * {@code Custody.MaxQuantity: 1}). Null for the bulk fungible-resource case (the sawmill's
     * logs), where {@link #items} alone is authoritative and this field stays unused - a genuine
     * correctness fix over the count-only model: {@link #toItemStacks()} would otherwise
     * synthesize a bare fresh stack on auto-return, silently discarding the placed item's actual
     * durability/enhancement state (an item-loss-equivalent bug the generic bulk path never
     * exercises since fungible resources carry no per-stack identity worth preserving).
     */
    @Nullable private ItemStack uniqueStack;

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

    /**
     * The real, metadata-bearing placed stack ({@link #uniqueStack}), or {@code null} for a bulk
     * fungible-resource claim.
     */
    @Nullable
    ItemStack uniqueStack() {
        return uniqueStack;
    }

    /** Sets/replaces the metadata-preserving unique stack (the Stamp step's commit phase writes the mutated result back here). */
    void setUniqueStack(@Nullable ItemStack stack) {
        this.uniqueStack = stack;
    }

    /** One concrete {@link ItemStack} per tallied item id, for the auto-return path - prefers {@link #uniqueStack} when set (metadata preserved). */
    @Nonnull
    List<ItemStack> toItemStacks() {
        if (uniqueStack != null) {
            return List.of(uniqueStack);
        }
        List<ItemStack> out = new ArrayList<>(items.size());
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.add(new ItemStack(e.getKey(), e.getValue()));
            }
        }
        return out;
    }
}
