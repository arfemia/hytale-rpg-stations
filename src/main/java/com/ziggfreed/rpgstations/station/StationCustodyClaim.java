package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.world.stash.BlockStash;
import com.ziggfreed.common.world.stash.StashPile;
import com.ziggfreed.rpgstations.asset.Custody;

/**
 * One block's placed-input custody claim, read as a VIEW over the block's persisted
 * {@link BlockStash}: WHO stood it up, WHAT its piles hold, keyed by socket id. Each socket's
 * contents live in the stash pile under that socket's id (insertion-ordered per pile,
 * oldest-placed-first - the drain order {@link StationCustody#drain} walks), and each pile records
 * its OWN owner (the first contributor), so a shared station can hold several players' materials
 * side by side without ever co-mingling them. A socket-less custody keeps its whole tally under
 * the one reserved {@value #MAIN_PILE} pile - the degenerate case every pre-socket stash already
 * is. The backing stash lives on the block's own chunk section (ziggfreed-common's
 * {@code BlockStashes} store, registered by the library at setup) and is saved and loaded WITH the
 * chunk, so placed input survives a disconnect, a restart and a chunk unload alike; each pile's
 * metadata-preserving {@code Unique} stack rides the stash's engine-codec item leaf and survives
 * the same way.
 *
 * <p><b>A claim is materialized fresh per touch</b> ({@code StationService#custodyClaimAt} resolves
 * the section live, never through a write-through cache). <b>Whoever mutates, marks:</b> a call
 * site that adds, drains, removes a pile or swaps a unique stack calls {@link #markDirty()} once
 * when done, or the change survives only until the section's next unload (the {@code BlockStashes}
 * dirty contract). Removing a whole claim goes through {@code BlockStashes.removeStashAt}, which
 * marks the section itself.
 *
 * <p>The display props' identities ({@code Ref}/{@code NetworkId}) are deliberately NOT here: a
 * network id is per-world and never boot-stable, so they live in {@code StationService}'s volatile
 * per-socket display side map and are respawned from the persisted stash on the block's first
 * touch after a restart.
 */
final class StationCustodyClaim {

    /**
     * The one reserved pile id a socket-less station's whole tally lives under - the SAME id
     * {@link Custody#effectiveSockets()} synthesizes for its degenerate socket, so today's shape
     * IS the one-socket case of the multi-pile layout.
     */
    static final String MAIN_PILE = Custody.MAIN_SOCKET_ID;

    /** The stash {@code Tag} prefix that marks a stash as this mod's, ahead of any other consumer's. */
    static final String TAG_PREFIX = "rpgstations:";

    /**
     * The stash-level owner: whoever stood the claim up (its first placer). Per-pile ownership is
     * {@link #pileOwner}; this is the fallback identity for a pile that recorded none, and the
     * anchor-claim / degenerate ownership gates' comparand.
     */
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

    /** The live persisted stash this view reads and writes. */
    @Nonnull
    private final BlockStash stash;

    /** Flags the owning chunk section for a save; a no-op for a detached (test-built) view. */
    @Nonnull
    private final Runnable dirtyMarker;

    /**
     * A DETACHED view over a fresh stash, for the pure decision-core tests (and nothing else in
     * production): the same real {@link BlockStash} storage type, with nowhere to mark dirty.
     */
    StationCustodyClaim(@Nonnull UUID ownerId, @Nonnull String stationId, @Nonnull String actionId,
            int blockX, int blockY, int blockZ) {
        this(ownerId, stationId, actionId, blockX, blockY, blockZ,
                detachedStash(ownerId, stationId, actionId), () -> { });
    }

    private static BlockStash detachedStash(@Nonnull UUID ownerId, @Nonnull String stationId,
            @Nonnull String actionId) {
        BlockStash stash = new BlockStash();
        stampNewStash(stash, ownerId, stationId, actionId);
        return stash;
    }

    private StationCustodyClaim(@Nonnull UUID ownerId, @Nonnull String stationId, @Nonnull String actionId,
            int blockX, int blockY, int blockZ, @Nonnull BlockStash stash, @Nonnull Runnable dirtyMarker) {
        this.ownerId = ownerId;
        this.stationId = stationId;
        this.actionId = actionId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.stash = stash;
        this.dirtyMarker = dirtyMarker;
    }

    /**
     * The claim view over one resolved stash, or {@code null} when the stash is absent, belongs to
     * another consumer (a {@code Tag} this mod did not write), or records no readable owner at all
     * (neither a stash-level owner nor any pile's). {@code dirtyMarker} is what
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
        UUID owner = parseUuid(stash.getOwner());
        if (owner == null) {
            Map<String, StashPile> piles = stash.getPiles();
            if (piles != null) {
                for (StashPile pile : piles.values()) {
                    owner = pile != null ? parseUuid(pile.getOwner()) : null;
                    if (owner != null) {
                        break;
                    }
                }
            }
        }
        if (owner == null) {
            return null;
        }
        return new StationCustodyClaim(owner, stationId, actionId, blockX, blockY, blockZ, stash, dirtyMarker);
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

    // ==================== per-socket piles ====================

    /** The pile under this socket id, or {@code null} when nothing was ever placed there. */
    @Nullable
    StashPile pile(@Nonnull String socketId) {
        return stash.pile(socketId);
    }

    /**
     * The live, mutable per-socket tally the drain/available cores iterate and mutate. A socket
     * with no pile answers a FRESH empty map (a drain over it removes nothing, an available over
     * it counts nothing) - reading never mints a pile in the persisted stash.
     */
    @Nonnull
    Map<String, Integer> items(@Nonnull String socketId) {
        StashPile pile = stash.pile(socketId);
        return pile != null ? pile.itemsMutable() : new LinkedHashMap<>();
    }

    /**
     * Adds {@code quantity} of {@code itemId} to the socket's pile, creating it on first use.
     * {@code adder} non-null stamps the pile's owner when the pile is EMPTY (or never recorded
     * one) - the first-contributor-owns rule, which is also what re-opens a pile the moment it
     * drains empty; pass {@code null} to leave ownership untouched (a refund back into a pile must
     * never re-own it).
     */
    void addTo(@Nonnull String socketId, @Nullable UUID adder, @Nonnull String itemId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        StashPile pile = stash.ensurePile(socketId);
        Map<String, Integer> items = pile.getItems();
        boolean wasEmpty = items == null || items.isEmpty();
        if (adder != null && (wasEmpty || pile.getOwner() == null)) {
            pile.setOwner(adder.toString());
        }
        pile.itemsMutable().merge(itemId, quantity, Integer::sum);
    }

    /** The socket pile's recorded owner, falling back to the stash-level {@link #ownerId} for a pile that recorded none; null when the pile is absent. */
    @Nullable
    UUID pileOwner(@Nonnull String socketId) {
        StashPile pile = stash.pile(socketId);
        if (pile == null) {
            return null;
        }
        UUID owner = parseUuid(pile.getOwner());
        return owner != null ? owner : ownerId;
    }

    /** The socket pile's item count (its unique stack not double-counted; the tally already carries it). */
    int totalQuantity(@Nonnull String socketId) {
        StashPile pile = stash.pile(socketId);
        return pile != null ? tallyOf(pile) : 0;
    }

    /** True when the socket's pile holds no items (absent piles included). */
    boolean isEmpty(@Nonnull String socketId) {
        StashPile pile = stash.pile(socketId);
        Map<String, Integer> items = pile != null ? pile.getItems() : null;
        return items == null || items.isEmpty();
    }

    /** Every pile id currently in the stash, in insertion order. */
    @Nonnull
    List<String> pileIds() {
        Map<String, StashPile> piles = stash.getPiles();
        return piles != null ? List.copyOf(piles.keySet()) : List.of();
    }

    /** The pile ids OWNED by {@code player} (a pile that recorded no owner counts as the stash owner's). */
    @Nonnull
    List<String> pileIdsOwnedBy(@Nonnull UUID player) {
        List<String> out = new ArrayList<>();
        for (String socketId : pileIds()) {
            if (player.equals(pileOwner(socketId))) {
                out.add(socketId);
            }
        }
        return out;
    }

    /** Removes the socket's pile outright (the caller has settled its contents and marks dirty). */
    void removePile(@Nonnull String socketId) {
        Map<String, StashPile> piles = stash.getPiles();
        if (piles != null) {
            piles.remove(socketId);
        }
    }

    /** True while ANY pile remains in the stash (empty ones included - a standing pile keeps its record). */
    boolean hasAnyPile() {
        Map<String, StashPile> piles = stash.getPiles();
        return piles != null && !piles.isEmpty();
    }

    /** The socket pile's metadata-bearing unique stack (see {@link #uniqueStack()}), or null. */
    @Nullable
    ItemStack uniqueStack(@Nonnull String socketId) {
        StashPile pile = stash.pile(socketId);
        return pile != null ? pile.getUnique() : null;
    }

    /** Sets/replaces the socket pile's metadata-preserving unique stack (creating the pile on first use). */
    void setUniqueStack(@Nonnull String socketId, @Nullable ItemStack stack) {
        stash.ensurePile(socketId).setUnique(stack);
    }

    /** One concrete {@link ItemStack} per tallied item id of ONE socket's pile - prefers its unique stack (metadata preserved). */
    @Nonnull
    List<ItemStack> toItemStacks(@Nonnull String socketId) {
        StashPile pile = stash.pile(socketId);
        if (pile == null) {
            return List.of();
        }
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

    // ==================== the degenerate (main-pile) view ====================

    /** The live, mutable {@value #MAIN_PILE} tally - the socket-less view the pure decision cores default to. */
    @Nonnull
    Map<String, Integer> items() {
        return stash.ensurePile(MAIN_PILE).itemsMutable();
    }

    /** Adds to the {@value #MAIN_PILE} pile as the stash owner (the socket-less placement shape). */
    void add(@Nonnull String itemId, int quantity) {
        addTo(MAIN_PILE, ownerId, itemId, quantity);
    }

    /** The item count across EVERY pile - the per-block total the custody-level {@code MaxQuantity} caps. */
    int totalQuantity() {
        Map<String, StashPile> piles = stash.getPiles();
        if (piles == null) {
            return 0;
        }
        int total = 0;
        for (StashPile pile : piles.values()) {
            if (pile != null) {
                total += tallyOf(pile);
            }
        }
        return total;
    }

    /** True when NO pile holds any item. */
    boolean isEmpty() {
        Map<String, StashPile> piles = stash.getPiles();
        if (piles == null) {
            return true;
        }
        for (StashPile pile : piles.values()) {
            Map<String, Integer> items = pile != null ? pile.getItems() : null;
            if (items != null && !items.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int tallyOf(@Nonnull StashPile pile) {
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

    /**
     * The real, metadata-bearing placed stack of the {@value #MAIN_PILE} pile (the engine-codec
     * {@code Unique} leaf, so durability and any prior enhancement survive a restart with the
     * stash), or {@code null} for a bulk fungible-resource claim.
     */
    @Nullable
    ItemStack uniqueStack() {
        return uniqueStack(MAIN_PILE);
    }

    /** Sets/replaces the {@value #MAIN_PILE} pile's metadata-preserving unique stack (the Stamp step's commit phase writes the mutated result back here). */
    void setUniqueStack(@Nullable ItemStack stack) {
        setUniqueStack(MAIN_PILE, stack);
    }

    /**
     * Flags the owning chunk section as needing a save. Call ONCE after a batch of in-place
     * mutations (adds, a drain, a pile removal, a unique-stack swap) - without it the engine has
     * no reason to write the section out, and the mutation survives only until the section's next
     * unload.
     */
    void markDirty() {
        dirtyMarker.run();
    }

    /** One concrete {@link ItemStack} per tallied item id across EVERY pile, for the drop-everything paths (a block break). */
    @Nonnull
    List<ItemStack> toItemStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (String socketId : pileIds()) {
            out.addAll(toItemStacks(socketId));
        }
        return out;
    }

    /**
     * The identity stamp for a stash minted by {@code StationService#ensureClaimAt}: this mod's
     * tag plus the stash-level owner, so a freshly created stash always records enough to survive
     * a save and to resolve back into a claim. Piles are minted by the first {@link #addTo} into
     * each socket, which is also what records each pile's own owner.
     */
    static void stampNewStash(@Nonnull BlockStash stash, @Nonnull UUID ownerId,
            @Nonnull String stationId, @Nonnull String actionId) {
        stash.setTag(encodeTag(stationId, actionId));
        stash.setOwner(ownerId.toString());
    }
}
