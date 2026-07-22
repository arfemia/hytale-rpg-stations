package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * The summary panel's post-build theming hook (design section 3.2).
 *
 * <p><b>{@link #rootSelector()} is a FROZEN api contract (critique m5, binding).</b> It names
 * RpgStations' summary-panel content root ({@code "#RpgStationSummaryRoot"} today - see {@code
 * ui.StationSummaryHud#ROOT_SELECTOR}) and a RpgStations {@code .ui} restructure MUST keep it
 * stable: an external consumer's {@code SummaryEnricher.decorate} (e.g. the MMO's {@code
 * ThemeRetint.appendThemed}) writes {@link UICommandBuilder} commands against this exact
 * selector cross-jar, so a silent rename here breaks that consumer's theming with no compile
 * error on either side.
 */
public final class SummaryDecorateContext {

    @Nonnull private final UICommandBuilder commandBuilder;
    @Nonnull private final String rootSelector;
    @Nullable private final PlayerRef playerRef;

    public SummaryDecorateContext(@Nonnull UICommandBuilder commandBuilder, @Nonnull String rootSelector,
            @Nullable PlayerRef playerRef) {
        this.commandBuilder = commandBuilder;
        this.rootSelector = rootSelector;
        this.playerRef = playerRef;
    }

    @Nonnull
    public UICommandBuilder commandBuilder() {
        return commandBuilder;
    }

    /** FROZEN (critique m5): the summary panel's content root selector. Load-bearing for cross-jar theming. */
    @Nonnull
    public String rootSelector() {
        return rootSelector;
    }

    @Nullable
    public PlayerRef playerRef() {
        return playerRef;
    }
}
