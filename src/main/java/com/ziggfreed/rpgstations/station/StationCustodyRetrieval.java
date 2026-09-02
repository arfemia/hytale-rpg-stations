package com.ziggfreed.rpgstations.station;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * PURE, unit-testable decision cores for press-F custody RETRIEVAL (a new feature: the
 * placed-input display entity's own {@code Interactions} entry,
 * {@code interaction.StationRetrieveInteraction}, set at spawn by {@link StationCustodyDisplay}):
 * resolving the clicked display entity back to its owning block key, and the retrieval
 * eligibility decision itself. Mirrors {@link StationCustody}'s injected-shape, zero-engine-touch
 * pattern (see that class's own javadoc) so both stay testable without a running Hytale server.
 */
final class StationCustodyRetrieval {

    private StationCustodyRetrieval() {
    }

    /** Retrieval eligibility outcomes, in the PRECEDENCE order {@link #decide} checks them. */
    enum Outcome {
        /** The clicked entity does not resolve to any live custody claim - a stale/foreign entity (silent). */
        UNKNOWN_TARGET,
        /**
         * A session is ACTIVELY working the target block right now - retrieval is a NO-OP, keyed
         * toast (the session owns its own input for the whole duration of a program run; yanking
         * materials out from under a running Consume step would either silently short a cycle or
         * race the session's own auto-return on its next stop).
         */
        BUSY,
        /** The claim belongs to someone else - the SAME ownership gate {@code StationService#toggle}'s custody-placement branch already enforces. */
        NOT_OWNER,
        /** The claim exists (right owner, no active session) but is already empty - nothing to hand back (silent). */
        NOTHING_TO_RETRIEVE,
        /** Retrieve: hand the claim's contents back to the presser, despawn the display, flip the block Empty. */
        RETRIEVE
    }

    /**
     * Pure: does the claim standing at {@code blockKey} own the display entity carrying
     * {@code targetNetworkId}? Comparing by network id rather than {@code Ref} identity keeps this
     * decision engine-free (a test needs no live {@code Store} to build a fixture {@code Ref}) and
     * matches the wire-level identity the native mount/interaction systems already key off.
     *
     * <p><b>The world scope is load-bearing, not a micro-optimisation.</b> A network id is issued
     * from a per-world counter that starts at 1 in every world, so the SAME integer routinely
     * names a different entity in each loaded world. {@code worldPrefix} is the presser's own
     * {@code "<worldUuid>:"} - the exact prefix {@code StationAnchors#blockKey} already encodes -
     * and a claim whose key does not start with it can never be the one that was clicked. Without
     * that guard, a press in world A can resolve a claim in world B and hand over its contents.
     *
     * @param blockKey             the candidate claim's own block key
     * @param worldPrefix          the presser's world uuid followed by {@code ":"}
     * @param claimDisplayNetworkId the candidate claim's stored display network id, null when it has no display
     * @param targetNetworkId      the clicked entity's network id
     */
    static boolean owns(@Nonnull String blockKey, @Nonnull String worldPrefix,
            @Nullable Integer claimDisplayNetworkId, int targetNetworkId) {
        return claimDisplayNetworkId != null
                && claimDisplayNetworkId == targetNetworkId
                && blockKey.startsWith(worldPrefix);
    }

    /**
     * Pure: the first blockKey in {@code blockKeyToDisplayNetworkId} whose claim {@link #owns} the
     * clicked entity, or {@code null} when none does. The map-shaped convenience over
     * {@link #owns} - the live retrieval path walks its own claim map with the predicate directly
     * (no snapshot allocation, no component read), so both share ONE matching rule.
     */
    @Nullable
    static String findOwningBlockKey(@Nonnull Map<String, Integer> blockKeyToDisplayNetworkId,
            @Nonnull String worldPrefix, int targetNetworkId) {
        for (Map.Entry<String, Integer> e : blockKeyToDisplayNetworkId.entrySet()) {
            if (owns(e.getKey(), worldPrefix, e.getValue(), targetNetworkId)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Pure: the retrieval eligibility decision, precedence order per {@link Outcome}'s own
     * javadoc. {@code mayReclaim} is the per-socket ownership answer: the presser owns the
     * clicked socket's pile, or that socket authors {@code Share.Reclaim} (the owner-only rule
     * relaxed per socket, never per block).
     */
    @Nonnull
    static Outcome decide(boolean claimFound, boolean hasActiveSessionAtBlock, boolean mayReclaim,
            boolean claimNonEmpty) {
        if (!claimFound) {
            return Outcome.UNKNOWN_TARGET;
        }
        if (hasActiveSessionAtBlock) {
            return Outcome.BUSY;
        }
        if (!mayReclaim) {
            return Outcome.NOT_OWNER;
        }
        if (!claimNonEmpty) {
            return Outcome.NOTHING_TO_RETRIEVE;
        }
        return Outcome.RETRIEVE;
    }

    // ==================== the per-socket display key ====================

    /**
     * The separator between a block key and a socket id in the display side map's composite key.
     * A block key is {@code "<worldUuid>:<x>:<y>:<z>"} (no {@code '#'} can appear in it), so the
     * split-back is unambiguous, and the composite still STARTS with the block key - every
     * world-prefix sweep and {@link #owns} world guard keeps working unchanged.
     */
    static final String SOCKET_KEY_SEPARATOR = "#";

    /** The composite side-map key one socket's display prop lives under. */
    @Nonnull
    static String displayKey(@Nonnull String blockKey, @Nonnull String socketId) {
        return blockKey + SOCKET_KEY_SEPARATOR + socketId;
    }

    /** The block-key half of a composite display key (the whole key when no separator is present). */
    @Nonnull
    static String blockKeyOf(@Nonnull String displayKey) {
        int sep = displayKey.indexOf(SOCKET_KEY_SEPARATOR);
        return sep >= 0 ? displayKey.substring(0, sep) : displayKey;
    }

    /** The socket-id half of a composite display key ({@link StationCustodyClaim#MAIN_PILE} when none is encoded). */
    @Nonnull
    static String socketIdOf(@Nonnull String displayKey) {
        int sep = displayKey.indexOf(SOCKET_KEY_SEPARATOR);
        return sep >= 0 && sep + 1 < displayKey.length()
                ? displayKey.substring(sep + 1) : StationCustodyClaim.MAIN_PILE;
    }
}
