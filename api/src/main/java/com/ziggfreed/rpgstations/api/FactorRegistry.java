package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * The write side of the extensible numeric-factor vocabulary. RpgStations
 * registers its OWN built-in {@code rpgstations:} factors through this SAME registry (dogfooded);
 * an external mod (e.g. {@code yourmod:reputation}) registers into it the same way.
 * Namespace-prefix your ids by convention to avoid collisions.
 *
 * <p>This is the READ side of the extension surface: a provider turns an authored id into a
 * number the engine composes. Its write-side twin is {@link ContributionChannelRegistry}, which
 * takes no provider at all - the engine never resolves a channel, only forwards it.
 */
public interface FactorRegistry {

    /** Register {@code provider} under {@code factorId} (lowercased at register); last write wins. */
    void register(@Nonnull String factorId, @Nonnull StationFactorProvider provider);
}
