package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * The write-side channel vocabulary: DECLARATION ONLY. Where
 * {@link FactorRegistry} takes a provider that RESOLVES an id to a number, this registry takes
 * nothing but the id itself - the engine never resolves a channel, it only forwards
 * {@link StationContribution}s naming one. That asymmetry is the whole contract: the engine owns
 * built-in FACTORS because it can compute them, and owns ZERO built-in channels because it
 * interprets none.
 *
 * <p>Declaring buys exactly two things, both authoring quality-of-life:
 * <ul>
 *   <li>the id appears in the {@code rpgstations:channels} Asset-Editor dropdown, so an author
 *   picks it instead of typing it;
 *   <li>the content validator stops warning {@code UNKNOWN_CHANNEL} for it, and that warn's
 *   message echoes the declared set so a typo is obvious.
 * </ul>
 *
 * <p><b>Fail-open, absolutely.</b> An UNDECLARED channel is still forwarded verbatim on the cycle
 * event; the validator only ever warns, and nothing here can block an asset load, a cycle, or a
 * grant. Declaration order and load order are irrelevant - a mod that declares late simply widens
 * the next dropdown answer and the next validate pass.
 *
 * <p>Namespace-prefix your ids by convention ({@code "yourmod:crop_quality"}) to avoid collisions.
 * Prefer ONE channel plus a {@code Param} over one channel per subject: a channel set that grows
 * with content is unusable as a pick list.
 */
public interface ContributionChannelRegistry {

    /**
     * Declare {@code channelId} as a known contribution channel (lowercased at declare;
     * re-declaring is harmless). Declaration only - there is nothing to resolve and no callback.
     */
    void declare(@Nonnull String channelId);
}
