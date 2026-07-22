package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * The write side of the extensible numeric-factor vocabulary (design section 3.2). RpgStations
 * registers its OWN built-in {@code rpgstations:} factors through this SAME registry (dogfooded);
 * an external mod (e.g. the MMO bridge's {@code mmoskilltree:station_luck}/{@code
 * mmoskilltree:skill_level}) registers into it the same way. Namespace-prefix your ids by
 * convention to avoid collisions.
 */
public interface FactorRegistry {

    /** Register {@code provider} under {@code factorId} (lowercased at register); last write wins. */
    void register(@Nonnull String factorId, @Nonnull StationFactorProvider provider);
}
