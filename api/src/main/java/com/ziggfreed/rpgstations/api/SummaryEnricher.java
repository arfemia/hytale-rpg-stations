package com.ziggfreed.rpgstations.api;

import java.util.List;

import javax.annotation.Nonnull;

import com.ziggfreed.common.ui.rows.SummaryRow;

/**
 * Extends the end-of-session summary panel with extra ledger rows and/or theming (design section
 * 3.2). Every registered enricher runs, in registration order, on every NON-SILENT stop that
 * shows a summary (invoked from {@code StationService.stop()} BEFORE {@code
 * StationSessionCompletedEvent} fires - see that event's javadoc for the ordering contract).
 * Neither method may throw past the engine's own guard; the engine catches and skips a bad
 * enricher rather than losing the whole summary.
 */
public interface SummaryEnricher {

    /** Extra ledger rows, PREPENDED before the engine's own item rows, in registration order. */
    @Nonnull
    List<SummaryRow> rows(@Nonnull SummaryContext ctx);

    /** Optional post-build hook on the summary panel's {@link SummaryDecorateContext#commandBuilder()} (theming). */
    default void decorate(@Nonnull SummaryDecorateContext ctx) {
    }
}
