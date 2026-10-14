package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * The summary panel's post-build theming hook.
 *
 * <p><b>{@link #rootSelector()} names the panel's frame</b>: the element that carries the frame's
 * {@code Background} ({@code "#RpgStationSummaryRoot #Content"} - see {@code
 * ui.StationSummaryHud#decorateContext}), so a consumer's {@code .Background.Color} tint lands on
 * the texture it multiplies. Treat it as opaque and write only against it: an external consumer's
 * {@code SummaryEnricher.decorate} pushes {@link UICommandBuilder} commands cross-jar, where a
 * selector the panel lacks, or a {@code .Background} leaf on an element with no Background,
 * disconnects the player with no compile error on either side. Until 1.1.2 it named the bare
 * {@code #RpgStationSummaryRoot}, which has no Background, and a themed tint there did exactly
 * that.
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

    /** The summary panel's frame, the element a theme tints. Load-bearing for cross-jar theming. */
    @Nonnull
    public String rootSelector() {
        return rootSelector;
    }

    @Nullable
    public PlayerRef playerRef() {
        return playerRef;
    }
}
