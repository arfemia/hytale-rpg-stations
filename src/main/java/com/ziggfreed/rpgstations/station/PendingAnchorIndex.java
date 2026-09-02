package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

/**
 * The VOLATILE pending-anchor index that buys build-order freedom: when an indexed placement (an
 * exact-id pattern cell, typically the anchor block placed first) matches no pattern completely,
 * the implied anchor is remembered here, and any LATER placement within the widest pattern's
 * bounding radius re-walks just the remembered candidates instead of the whole world.
 *
 * <p>Strictly in-memory and per world: entries evict when their world unloads and never persist.
 * After a restart a half-built shape completes by re-placing any exact-id block of the pattern
 * (which re-registers the candidate), the documented recovery. Bounded per world
 * ({@value #MAX_PER_WORLD}); past the ceiling the OLDEST entry is dropped - a busy build site
 * self-heals the same way the restart case does, by the next exact-id placement.
 *
 * <p>Each world's entries are only ever touched from that world's own thread (place/break events
 * dispatch there), so the inner map needs no locking; the outer map is concurrent for the
 * cross-world eviction sweep.
 */
final class PendingAnchorIndex {

    /** The per-world entry ceiling. */
    static final int MAX_PER_WORLD = 128;

    /** One remembered candidate: where the anchor would stand, and which pattern it would root. */
    record Pending(int x, int y, int z, @Nonnull String patternId) {
    }

    private final ConcurrentHashMap<UUID, LinkedHashMap<String, Pending>> byWorld = new ConcurrentHashMap<>();

    /** Remembers one candidate (idempotent per (position, pattern)); evicts the oldest past the ceiling. */
    void register(@Nonnull UUID worldUuid, int x, int y, int z, @Nonnull String patternId) {
        LinkedHashMap<String, Pending> entries = byWorld.computeIfAbsent(worldUuid, k -> new LinkedHashMap<>());
        String key = key(x, y, z, patternId);
        if (entries.containsKey(key)) {
            return;
        }
        while (entries.size() >= MAX_PER_WORLD) {
            Iterator<String> oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        entries.put(key, new Pending(x, y, z, patternId.toLowerCase(Locale.ROOT)));
    }

    /**
     * Every remembered candidate whose anchor lies within Chebyshev distance {@code radius} of
     * {@code (x, y, z)} - the cheap spatial pre-check before a re-walk. Registration order.
     */
    @Nonnull
    List<Pending> candidatesNear(@Nonnull UUID worldUuid, int x, int y, int z, int radius) {
        Map<String, Pending> entries = byWorld.get(worldUuid);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<Pending> out = new ArrayList<>();
        for (Pending p : entries.values()) {
            int distance = Math.max(Math.abs(p.x() - x), Math.max(Math.abs(p.y() - y), Math.abs(p.z() - z)));
            if (distance <= radius) {
                out.add(p);
            }
        }
        return out;
    }

    /** Forgets one remembered candidate (a completed or refused walk). */
    void remove(@Nonnull UUID worldUuid, @Nonnull Pending pending) {
        Map<String, Pending> entries = byWorld.get(worldUuid);
        if (entries != null) {
            entries.remove(key(pending.x(), pending.y(), pending.z(), pending.patternId()));
        }
    }

    /** Forgets every candidate anchored at {@code (x, y, z)}, whatever pattern (an activation or a break there). */
    void removeAt(@Nonnull UUID worldUuid, int x, int y, int z) {
        Map<String, Pending> entries = byWorld.get(worldUuid);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        entries.values().removeIf(p -> p.x() == x && p.y() == y && p.z() == z);
    }

    /** Drops a removed world's entries wholesale. */
    void clearWorld(@Nonnull UUID worldUuid) {
        byWorld.remove(worldUuid);
    }

    /** The live entry count for one world (test/diagnostic read). */
    int size(@Nonnull UUID worldUuid) {
        Map<String, Pending> entries = byWorld.get(worldUuid);
        return entries != null ? entries.size() : 0;
    }

    @Nonnull
    private static String key(int x, int y, int z, @Nonnull String patternId) {
        return x + ":" + y + ":" + z + "|" + patternId.toLowerCase(Locale.ROOT);
    }
}
