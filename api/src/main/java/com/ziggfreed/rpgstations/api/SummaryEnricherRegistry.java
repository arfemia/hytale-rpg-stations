package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/** The write side of the summary-panel enricher list (design section 3.2). */
public interface SummaryEnricherRegistry {

    /** Register {@code enricher}; every registered enricher runs, in registration order, on every non-silent summary. */
    void register(@Nonnull SummaryEnricher enricher);
}
