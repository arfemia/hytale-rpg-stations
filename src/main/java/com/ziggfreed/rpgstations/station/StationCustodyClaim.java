package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.world.stash.BlockStash;
import com.ziggfreed.common.world.stash.StashPile;

/**
 * One block's placed-input custody claim, read as a VIEW over the block's persisted
 * {@link BlockStash}: WHO placed it and WHAT is in it, keyed by the owner's uuid and a
 * {@code (itemId -> quantity)} tally (insertion-ordered, oldest-placed-first - the drain order
 * {@link StationCustody#drain} walks). The backing stash lives on the block's own chunk section
 * (ziggfreed-common's {@code BlockStashes} store, registered by the library at setup) and is saved
 * and loaded WITH the chunk, so placed input survives a disconnect, a restart and a chunk unload
 * alike; the metadata-preserving {@link #uniqueStack()} rides the stash's engine-codec item leaf
 * and survives the same way.
 *
 * <p><b>A claim is materialized fresh per touch</b> ({@code StationService#custodyClaimAt} resolves
 * the section live, never through a write-through cache) and holds the stash's ONE
 * {@value #MAIN_PILE} pile. <b>Whoever mutates, marks:</b> a call site that adds, drains or swaps
 * the unique stack calls {@link #markDirty()} once when done, or the change survives only until the
 * section's next unload (the {@code BlockStashes} dirty contract). Removing a whole claim goes
 * through {@code BlockStashes.removeStashAt}, which marks the section itself.
 *
 * <p>The display prop's identity ({@code Ref}/{@code NetworkId}) is deliberately NOT here: a
 * network id is per-world and never boot-stable, so it lives in {@code StationService}'s volatile
 * display side map and is respawned from the persisted stash on the block's first touch after a
 * restart.
 */
final class StationCustodyClaim {

    /**
     * The one reserved pile id a socket-less station's whole tally lives under. Kept a named
     * constant so a future multi-pile layout can treat today's shape as its degenerate case.
     */
    static final String MAIN_PILE = "main";

    /** The stash {@code Tag} prefix that marks a stash as this mod's, ahead of any other consumer's. */
    static final String TAG_PREFIX = "rpgstations:";

    @Nonnull final UUID ownerId;
    @Nonnull final String stationId;
    @Nonnull final String actionId;

    /**
     * The block this claim lives at (the SAME coordinates its block key encodes, stashed directly
     * rather than re-parsed out of the block-key string) - needed by the press-F RETRIEVAL path
     * ({@code StationService#retrieveCustody}), which is entered from the display ENTITY's own
     * interaction (no block-coordinate packet field to read, unlike every other custody call site
     * which already has {@code blockX}/{@code blockY}/{@code blockZ} in hand from the interaction
     * that triggered it).
     */
    final int blockX;
    final int blockY;
    final int blockZ;

    /** The live persisted pile this view reads and writes ({@link #MAIN_PILE} of the block's stash). */
    @Nonnull
    private final StashPile pile;

    /** Flags the owning chunk section for a save; a no-op for a detached (test-built) view. */
    @Nonnull
    private final Runnable dirtyMarker;

    /**
     * A DETACHED view over a fresh pile, for the pure decision-core tests (and nothing else in
     * production): the same real {@link StashPile} storage type, with nowhere to mark dirty.
     */
    StationCustodyClaim(@Nonnull UUID ownerId, @Nonnull String stationId, @Nonnull String actionId,
            int blockX, int blockY, int blockZ) {
        this(ownerId, stationId, actionId, blockX, blockY, blockZ, new StashPile(), () -> { });
    }

    private StationCustodyClaim(@Nonnull UUID ownerId, @Nonnull String stationId, @Nonnull String actionId,
            int blockX, int blockY, int blockZ, @Nonnull StashPile pile, @Nonnull Runnable dirtyMarker) {
        this.ownerId = ownerId;
        this.stationId = stationId;
        this.actionId = actionId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.pile = pile;
        this.dirtyMarker = dirtyMarker;
    }

    /**
     * The claim view over one resolved stash, or {@code null} when the stash is absent, belongs to
     * another consumer (a {@code Tag} this mod did not write), or cannot be read back (no
     * {@value #MAIN_PILE} pile, or an unparseable owner). {@code dirtyMarker} is what
     * {@link #markDirty()} runs - the caller binds it to the stash's own section.
     */
    @Nullable
    static StationCustodyClaim of(@Nullable BlockStash stash, int blockX, int blockY, int blockZ,
            @Nonnull Runnable dirtyMarker) {
        if (stash == null) {
            return null;
        }
        String tag = stash.getTag();
        String stationId = stationIdOfTag(tag);
        String actionId = actionIdOfTag(tag);
        if (stationId == null || actionId == null) {
            return null;
        }
        StashPile pile = stash.pile(MAIN_PILE);
        UUID owner = pile != null ? parseUuid(pile.getOwner()) : null;
        if (pile == null || owner == null) {
            return null;
        }
        return new StationCustodyClaim(owner, stationId, actionId, blockX, blockY, blockZ, pile, dirtyMarker);
    }

    // ==================== the stash Tag (station/action identity, restart-stable) ====================

    /** The {@code Tag} value a claim's stash carries: this mod's prefix plus the owning station and action ids. */
    @Nonnull
    static String encodeTag(@Nonnull String stationId, @Nonnull String actionId) {
        return TAG_PREFIX + stationId + "/" + actionId;
    }

    /** The station id a stash {@code Tag} encodes, or {@code null} for a tag this mod did not write. */
    @Nullable
    static String stationIdOfTag(@Nullable String tag) {
        if (tag == null || !tag.startsWith(TAG_PREFIX)) {
            return null;
        }
        int slash = tag.indexOf('/', TAG_PREFIX.length());
        if (slash <= TAG_PREFIX.length()) {
            return null;
        }
        return tag.substring(TAG_PREFIX.length(), slash);
    }

    /** The action id a stash {@code Tag} encodes, or {@code null} for a tag this mod did not write. */
    @Nullable
    static String actionIdOfTag(@Nullable String tag) {
        if (stationIdOfTag(tag) == null) {
            return null;
        }
        String actionId = tag.substring(tag.indexOf('/', TAG_PREFIX.length()) + 1);
        return actionId.isBlank() ? null : actionId;
    }

    @Nullable
    private static UUID parseUuid(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ==================== the tally ====================

    /** The live, mutable tally - {@link StationCustody}'s drain/available cores iterate + mutate this directly. */
    @Nonnull
    Map<String, Integer> items() {
        return pile.itemsMutable();
    }

    void add(@Nonnull String itemId, int quantity) {
        if (quantity > 0) {
            pile.itemsMutable().merge(itemId, quantity, Integer::sum);
        }
    }

    int totalQuantity() {
        Map<String, Integer> items = pile.getItems();
        if (items == null) {
            return 0;
        }
        int total = 0;
        for (Integer q : items.values()) {
            if (q != null) {
                total += q;
            }
        }
        return total;
    }

    boolean isEmpty() {
        Map<String, Integer> items = pile.getItems();
        return items == null || items.isEmpty();
    }

    /**
     * The real, metadata-bearing placed stack (the pile's engine-codec {@code Unique} leaf, so
     * durability and any prior enhancement survive a restart with the stash), or {@code null} for
     * a bulk fungible-resource claim.
     */
    @Nullable
    ItemStack uniqueStack() {
        return pile.getUnique();
    }

    /** Sets/replaces the metadata-preserving unique stack (the Stamp step's commit phase writes the mutated result back here). */
    void setUniqueStack(@Nullable ItemStack stack) {
        pile.setUnique(stack);
    }

    /**
     * Flags the owning chunk section as needing a save. Call ONCE after a batch of in-place
     * mutations (adds, a drain, a unique-stack swap) - without it the engine has no reason to
     * write the section out, and the mutation survives only until the section's next unload.
     */
    void markDirty() {
        dirtyMarker.run();
    }

    /** One concrete {@link ItemStack} per tallied item id, for the hand-back paths - prefers {@link #uniqueStack()} when set (metadata preserved). */
    @Nonnull
    List<ItemStack> toItemStacks() {
        ItemStack unique = pile.getUnique();
        if (unique != null) {
            return List.of(unique);
        }
        Map<String, Integer> items = pile.getItems();
        if (items == null) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>(items.size());
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.add(new ItemStack(e.getKey(), e.getValue()));
            }
        }
        return out;
    }

    /**
     * The whole-claim write-back for a stash minted by {@code StationService#ensureClaimAt}: stamps
     * the stash's tag, whole-stash owner and {@value #MAIN_PILE} owner in one place, so a freshly
     * created stash always records enough to survive a save (a pile with no leaf set at all is
     * dropped by the engine's map codec on save).
     */
    static void stampNewStash(@Nonnull BlockStash stash, @Nonnull UUID ownerId,
            @Nonnull String stationId, @Nonnull String actionId) {
        stash.setTag(encodeTag(stationId, actionId));
        stash.setOwner(ownerId.toString());
        StashPile pile = stash.ensurePile(MAIN_PILE);
        if (pile.getOwner() == null) {
            pile.setOwner(ownerId.toString());
        }
    }
}
