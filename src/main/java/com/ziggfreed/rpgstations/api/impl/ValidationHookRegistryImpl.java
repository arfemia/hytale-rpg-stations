package com.ziggfreed.rpgstations.api.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.api.ValidationHook;
import com.ziggfreed.rpgstations.api.ValidationHookRegistry;

/**
 * The concrete {@link ValidationHookRegistry} the FULL validate pass reads from: every registered
 * hook runs, in registration order, over one shared read-only scope, each inside its OWN
 * try/catch so a throwing third-party hook costs its own findings and nothing else (the same
 * guard discipline {@link FactorRegistryImpl#resolve} applies to a throwing factor provider).
 *
 * <p>Union-of-all shape ({@link FlairUnlockRegistryImpl}/{@link SummaryEnricherRegistryImpl}'s,
 * not {@link FactorRegistryImpl}'s last-write-wins): several mods may each own rules worth
 * hearing. Read by {@code station.StationValidator} directly, not back through
 * {@code RpgStationsApi}.
 */
public final class ValidationHookRegistryImpl implements ValidationHookRegistry {

    private static final ValidationHookRegistryImpl INSTANCE = new ValidationHookRegistryImpl();

    private final CopyOnWriteArrayList<ValidationHook> hooks = new CopyOnWriteArrayList<>();

    private ValidationHookRegistryImpl() {
    }

    @Nonnull
    public static ValidationHookRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(@Nonnull ValidationHook hook) {
        hooks.add(hook);
    }

    /** A snapshot of every registered hook, in registration order. */
    @Nonnull
    public List<ValidationHook> hooks() {
        return List.copyOf(hooks);
    }

    /**
     * Test-only reset (the frozen api contract exposes no unregister - a unit test needs a clean
     * slate between cases; production code never calls this).
     */
    public void resetForTests() {
        hooks.clear();
    }
}
