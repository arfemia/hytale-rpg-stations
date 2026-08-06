package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * A third-party content check that runs inside this engine's own full validate pass (design
 * section 3.2). It is the answer to "my mod owns this factor family / this contribution channel,
 * and I know a composition that is always a mistake" - the rule lives with the vocabulary's owner
 * instead of being hardcoded here against a foreign id.
 *
 * <p>Contract:
 * <ul>
 *   <li>Read-only. A hook inspects {@link ValidationScope} and reports through
 *   {@link FindingSink}; it must not mutate anything, and there is nothing reachable from either
 *   parameter that it could mutate.
 *   <li>Advisory only. {@link FindingSink} offers {@code info}/{@code warn} and nothing else - a
 *   hook can never block an asset load or a station session.
 *   <li>Try-guarded. A throwing hook is caught, logged once, and skipped; the rest of the pass
 *   still runs.
 *   <li>Runs in the FULL pass only (post-load and on demand), never in the per-fold structural
 *   pass, so cross-asset reads are safe.
 * </ul>
 */
@FunctionalInterface
public interface ValidationHook {

    /** Inspect {@code scope} and report anything worth saying to {@code sink}. Never throws past the engine's guard. */
    void validate(@Nonnull ValidationScope scope, @Nonnull FindingSink sink);
}
