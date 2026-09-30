package com.ziggfreed.rpgstations.station;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.match.ItemMatch;

/**
 * The PURE split behind the custody refund: the count half of the ledger records every drained
 * id, and the unique half records the REAL stack a single-item socket gave up. A refund must hand
 * the piece back ONCE - as itself, wear and stamps intact - so the count half is netted of the
 * unique stack's own share before the bare stacks are rebuilt from it.
 */
final class StationCustodyLedger {

    private StationCustodyLedger() {
    }

    /**
     * {@code counts} with the unique stack's own quantity taken off its item id (the entry dropped
     * when nothing is left), so the caller restores the counts as bare stacks and the unique
     * stack as itself without doubling the piece. Identity-shaped when {@code unique} is null.
     */
    @Nonnull
    static Map<String, Integer> countsBesideUnique(@Nonnull Map<String, Integer> counts, @Nullable ItemStack unique) {
        if (unique == null) {
            return counts;
        }
        return countsBesideUnique(counts, uniqueId(unique), uniqueQuantity(unique));
    }

    /** The id-and-count core of {@link #countsBesideUnique(Map, ItemStack)}, pinned by the ledger test. */
    @Nonnull
    static Map<String, Integer> countsBesideUnique(@Nonnull Map<String, Integer> counts,
            @Nullable String uniqueItemId, int uniqueQuantity) {
        if (uniqueItemId == null || uniqueItemId.isBlank() || uniqueQuantity <= 0) {
            return counts;
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        int toNet = uniqueQuantity;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Integer count = e.getValue();
            if (e.getKey() == null || count == null || count <= 0) {
                continue;
            }
            if (toNet > 0 && ItemMatch.itemId(uniqueItemId, e.getKey())) {
                int netted = Math.min(toNet, count);
                toNet -= netted;
                count -= netted;
            }
            if (count > 0) {
                out.put(e.getKey(), count);
            }
        }
        return out;
    }

    /**
     * Did this drain take the piece itself: does {@code drained} count the unique stack's own
     * item? False with no unique stack, and false for one whose item the drain never touched (a
     * stack already left dangling on its pile before this drain), which the drain did not consume.
     */
    static boolean drainTookPiece(@Nonnull Map<String, Integer> drained, @Nullable ItemStack unique) {
        return unique != null && drainTookPiece(drained, uniqueId(unique));
    }

    /**
     * The piece THIS drain consumed: {@code unique} when {@link #drainTookPiece(Map, ItemStack)}
     * says the drain counted its item, else null. What the refund ledger's unique half records and
     * what the input-consumed hook reports as the piece, so a stack left dangling on its pile before
     * the drain is neither handed back by a refund nor reported consumed.
     */
    @Nullable
    static ItemStack pieceTaken(@Nonnull Map<String, Integer> drained, @Nullable ItemStack unique) {
        return drainTookPiece(drained, unique) ? unique : null;
    }

    /** The id core of {@link #drainTookPiece(Map, ItemStack)}, pinned by the ledger test. */
    static boolean drainTookPiece(@Nonnull Map<String, Integer> drained, @Nullable String uniqueItemId) {
        if (uniqueItemId == null || uniqueItemId.isBlank()) {
            return false;
        }
        for (Map.Entry<String, Integer> e : drained.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0
                    && ItemMatch.itemId(uniqueItemId, e.getKey())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String uniqueId(@Nonnull ItemStack unique) {
        try {
            return unique.getItemId();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int uniqueQuantity(@Nonnull ItemStack unique) {
        try {
            return unique.getQuantity();
        } catch (Throwable t) {
            return 1;
        }
    }
}
