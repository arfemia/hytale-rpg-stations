package com.ziggfreed.rpgstations.api.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.api.FlairUnlockProvider;
import com.ziggfreed.rpgstations.api.FlairUnlockRegistry;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The concrete {@link FlairUnlockRegistry} {@code station.StationFlairs} consults the UNION of
 * (design section 3.2). No provider registered = empty union = base presentations only.
 */
public final class FlairUnlockRegistryImpl implements FlairUnlockRegistry {

    private static final FlairUnlockRegistryImpl INSTANCE = new FlairUnlockRegistryImpl();

    private final CopyOnWriteArrayList<FlairUnlockProvider> providers = new CopyOnWriteArrayList<>();

    private FlairUnlockRegistryImpl() {
    }

    @Nonnull
    public static FlairUnlockRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(@Nonnull FlairUnlockProvider provider) {
        providers.add(provider);
    }

    /**
     * Union across every registered provider (design section 3.2); {@code
     * station.StationFlairs} filters the result against the station's OWN authored {@code
     * Flairs} map. Never throws; a throwing provider is skipped. Empty when no provider is
     * registered.
     */
    @Nonnull
    public Set<String> unlockedFlairIds(@Nonnull UUID playerId) {
        if (providers.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (FlairUnlockProvider p : providers) {
            try {
                Set<String> ids = p.unlockedFlairIds(playerId);
                if (ids != null) {
                    out.addAll(ids);
                }
            } catch (Throwable t) {
                Log.fine("STATION FlairUnlockProvider threw: " + t.getMessage());
            }
        }
        return out;
    }

    /**
     * Test-only reset (the frozen api contract exposes no unregister - a unit test needs a
     * clean slate between cases; production code never calls this).
     */
    public void resetForTests() {
        providers.clear();
    }
}
