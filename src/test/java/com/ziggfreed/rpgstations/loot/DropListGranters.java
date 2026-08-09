package com.ziggfreed.rpgstations.loot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PINNED {@link LootEngine.DropListGranter} fixtures shared by every loot test, so the native
 * drop-table boundary is a decided outcome rather than live randomness. The values are the tests'
 * own - no shipped table is referenced.
 *
 * <p>The two shapes are the whole point of the smart-cue rule: {@link #empty()} is a table whose
 * own internal weights chose to hand over nothing, {@link #paying} is one that paid.
 */
final class DropListGranters {

    private DropListGranters() {
    }

    /** Every table pays nothing (the "internal Choice rolled Empty" outcome). */
    static LootEngine.DropListGranter empty() {
        return id -> Map.of();
    }

    /** Every table hands over exactly this stack. */
    static LootEngine.DropListGranter paying(String itemId, int quantity) {
        return id -> {
            Map<String, Integer> landed = new LinkedHashMap<>();
            landed.put(itemId, quantity);
            return landed;
        };
    }
}
