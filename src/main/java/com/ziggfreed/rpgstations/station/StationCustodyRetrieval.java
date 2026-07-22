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
     * Pure: which blockKey's live claim owns the display entity carrying {@code targetNetworkId}
     * (a live snapshot of every claim's display entity's OWN network id, built by the caller -
     * engine-free so this stays unit-testable with plain fixture ids; comparing by NetworkId
     * rather than {@code Ref} identity sidesteps needing a live {@code Store} to construct a
     * fixture {@code Ref} in a test, and matches the wire-level identity the native mount/
     * interaction systems already key off). {@code null} when nothing matches.
     */
    @Nullable
    static String findOwningBlockKey(@Nonnull Map<String, Integer> blockKeyToDisplayNetworkId, int targetNetworkId) {
        for (Map.Entry<String, Integer> e : blockKeyToDisplayNetworkId.entrySet()) {
            Integer id = e.getValue();
            if (id != null && id == targetNetworkId) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Pure: the retrieval eligibility decision, precedence order per {@link Outcome}'s own javadoc. */
    @Nonnull
    static Outcome decide(boolean claimFound, boolean hasActiveSessionAtBlock, boolean isOwner, boolean claimNonEmpty) {
        if (!claimFound) {
            return Outcome.UNKNOWN_TARGET;
        }
        if (hasActiveSessionAtBlock) {
            return Outcome.BUSY;
        }
        if (!isOwner) {
            return Outcome.NOT_OWNER;
        }
        if (!claimNonEmpty) {
            return Outcome.NOTHING_TO_RETRIEVE;
        }
        return Outcome.RETRIEVE;
    }
}
