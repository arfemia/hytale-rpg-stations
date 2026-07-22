package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Stamp step's {@code Stats}-leaf delegate registry (design section 9.5): unlike
 * {@link FlairUnlockRegistry}/{@link SummaryEnricherRegistry} (which UNION every registrant),
 * this is a single ACTIVE slot - there is exactly one "how does this server encode enhancement
 * points onto an item" answer at a time, last-registration-wins (mirrors {@link FactorRegistry}'s
 * last-write-wins discipline). {@code null} from {@link #active()} means no progression mod has
 * registered a stamper yet - the Stamp step's {@code Stats} leaf no-ops (warns once) while
 * {@code Durability} still lands.
 */
public interface EnhanceStamperRegistry {

    /** Registers {@code stamper} as the active one, replacing any previously-registered stamper. */
    void register(@Nonnull EnhanceStamper stamper);

    /** The currently-active stamper, or {@code null} when none is registered. */
    @Nullable
    EnhanceStamper active();
}
