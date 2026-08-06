package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * The write side of the third-party content-check list. Every registered
 * {@link ValidationHook} runs, in registration order, on every FULL validate pass; a hook that
 * throws is caught and skipped rather than costing the rest of the pass.
 *
 * <p>Union-of-all shape (like {@link FlairUnlockRegistry}/{@link SummaryEnricherRegistry}, not
 * {@link FactorRegistry}'s last-write-wins slot): several mods may each own their own rules and
 * all of them are worth hearing.
 */
public interface ValidationHookRegistry {

    /** Register {@code hook}; it runs on every subsequent full validate pass, in registration order. */
    void register(@Nonnull ValidationHook hook);
}
