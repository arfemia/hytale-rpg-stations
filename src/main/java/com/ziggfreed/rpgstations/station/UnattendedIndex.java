package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

/**
 * The VOLATILE per-boot index the unattended pass drives from: which block keys carry an
 * unattended-capable stash, and which chunk sections the hydrate walk has already visited. Pure
 * collection semantics (no engine touch), so the seeding/eviction/re-hydration rules are
 * unit-testable over stubbed walks ({@code UnattendedCatchUpTest}); {@code StationService} owns
 * the ONE instance and feeds it from three sources - a live stash write at an unattended action,
 * the per-world hydrate walk over loaded sections, and lazy eviction when a visit finds the
 * section unloaded or the stash gone.
 *
 * <p>Keys follow the engine's standing global-map convention: a block key is
 * {@code "<worldUuid>:<x>:<y>:<z>"} and a section key prefixes the same world uuid
 * ({@link #sectionKey}), so one prefix sweep drops a whole world ({@link #dropWorld}) exactly like
 * {@code StationService#forgetBlockKeyedState}. Dropping a section's hydrated marker
 * ({@link #dropSection}) is what re-arms the walk for that section: a section that unloads and
 * later reloads is re-hydrated because its marker went with it.
 *
 * <p>Thread posture: mutated on world threads only, but backed by concurrent sets so a cross-world
 * read (the world-remove sweep) never trips an iterator.
 */
final class UnattendedIndex {

    /** Block keys whose stash's committed action authors an enabled {@code Work.Unattended}. */
    private final Set<String> blocks = ConcurrentHashMap.newKeySet();

    /** Section keys the hydrate walk already visited (and whose stashes it seeded). */
    private final Set<String> hydratedSections = ConcurrentHashMap.newKeySet();

    /** The section key for the section holding block {@code (x, y, z)} in the world {@code worldPrefix} names. */
    @Nonnull
    static String sectionKey(@Nonnull String worldPrefix, int blockX, int blockY, int blockZ, int sectionBits) {
        return worldPrefix + "s:" + (blockX >> sectionBits) + ":" + (blockY >> sectionBits)
                + ":" + (blockZ >> sectionBits);
    }

    /** Registers one unattended-capable block (idempotent). */
    void register(@Nonnull String blockKey) {
        blocks.add(blockKey);
    }

    /** Forgets one block (a drained/removed/broken stash, or one that stopped being capable). */
    void evictBlock(@Nonnull String blockKey) {
        blocks.remove(blockKey);
    }

    /** Whether this block is currently indexed. */
    boolean containsBlock(@Nonnull String blockKey) {
        return blocks.contains(blockKey);
    }

    /** Whether the hydrate walk already visited this section. */
    boolean isHydrated(@Nonnull String sectionKey) {
        return hydratedSections.contains(sectionKey);
    }

    /** Marks this section visited; the walk skips it until {@link #dropSection} re-arms it. */
    void markHydrated(@Nonnull String sectionKey) {
        hydratedSections.add(sectionKey);
    }

    /** Re-arms the hydrate walk for one section (an unload was noticed; a reload re-hydrates). */
    void dropSection(@Nonnull String sectionKey) {
        hydratedSections.remove(sectionKey);
    }

    /** A snapshot of the indexed block keys in {@code worldPrefix}'s world, for one visit pass. */
    @Nonnull
    List<String> blocksInWorld(@Nonnull String worldPrefix) {
        List<String> out = new ArrayList<>();
        for (String key : blocks) {
            if (key.startsWith(worldPrefix)) {
                out.add(key);
            }
        }
        return out;
    }

    /** Drops everything the world {@code worldPrefix} names - blocks and hydrated markers alike. */
    void dropWorld(@Nonnull String worldPrefix) {
        dropMatching(key -> key.startsWith(worldPrefix));
    }

    /** Drops every block AND hydrated marker whose key {@code keyMatches} (both carry the world-uuid prefix). */
    void dropMatching(@Nonnull Predicate<String> keyMatches) {
        blocks.removeIf(keyMatches);
        hydratedSections.removeIf(keyMatches);
    }

    /** How many blocks are indexed (tests + diagnostics). */
    int blockCount() {
        return blocks.size();
    }
}
