package com.ziggfreed.rpgstations.api.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.api.SummaryEnricher;
import com.ziggfreed.rpgstations.api.SummaryEnricherRegistry;

/**
 * The concrete {@link SummaryEnricherRegistry} {@code station.StationService.stop()} reads from
 * (design section 3.2/7.2-7.3): every registered enricher's {@code rows()} + {@code decorate()}
 * runs, in registration order, on every non-silent stop that shows a summary.
 */
public final class SummaryEnricherRegistryImpl implements SummaryEnricherRegistry {

    private static final SummaryEnricherRegistryImpl INSTANCE = new SummaryEnricherRegistryImpl();

    private final CopyOnWriteArrayList<SummaryEnricher> enrichers = new CopyOnWriteArrayList<>();

    private SummaryEnricherRegistryImpl() {
    }

    @Nonnull
    public static SummaryEnricherRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(@Nonnull SummaryEnricher enricher) {
        enrichers.add(enricher);
    }

    /** A snapshot of every registered enricher, in registration order. */
    @Nonnull
    public List<SummaryEnricher> enrichers() {
        return List.copyOf(enrichers);
    }

    /**
     * Test-only reset (the frozen api contract exposes no unregister - a unit test needs a
     * clean slate between cases; production code never calls this).
     */
    public void resetForTests() {
        enrichers.clear();
    }
}
