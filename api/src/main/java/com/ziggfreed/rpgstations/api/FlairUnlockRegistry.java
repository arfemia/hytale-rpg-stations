package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * The write side of the flair-unlock union (design section 3.2). The engine's flair overlay
 * resolution ({@code station.StationFlairs}) consults the UNION of every registered provider; no
 * provider registered = empty set = base presentations only. Persistence of WHAT is unlocked
 * stays with whichever mod registers the provider (e.g. the MMO's {@code StationComponent}).
 */
public interface FlairUnlockRegistry {

    /** Register {@code provider}; every registered provider contributes to the union. */
    void register(@Nonnull FlairUnlockProvider provider);
}
