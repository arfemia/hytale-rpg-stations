package com.ziggfreed.rpgstations.api.impl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.EnhanceStamper;
import com.ziggfreed.rpgstations.api.EnhanceStamperRegistry;

/**
 * The concrete {@link EnhanceStamperRegistry} the Stamp step's {@code station.StampCapEngine} /
 * {@code station.StationStepHandlers.StampHandler} read from (design section 9.5): a single
 * volatile active slot, last-registration-wins - mirrors {@link FactorRegistryImpl}'s
 * last-write-wins discipline, not {@link SummaryEnricherRegistryImpl}'s union-of-all shape.
 */
public final class EnhanceStamperRegistryImpl implements EnhanceStamperRegistry {

    private static final EnhanceStamperRegistryImpl INSTANCE = new EnhanceStamperRegistryImpl();

    private volatile EnhanceStamper active;

    private EnhanceStamperRegistryImpl() {
    }

    @Nonnull
    public static EnhanceStamperRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(@Nonnull EnhanceStamper stamper) {
        this.active = stamper;
    }

    @Override
    @Nullable
    public EnhanceStamper active() {
        return active;
    }

    /** Test-only reset; production code never calls this. */
    public void resetForTests() {
        active = null;
    }
}
