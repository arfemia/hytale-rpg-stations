package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ONE amount a station posts OUT to a named channel - the write-side twin of
 * the {@link FactorRegistry} read side, and the api form of the asset's own
 * {@code asset.Contribution} leaf. The station ENGINE never interprets {@link #channel} or
 * {@link #param}: it forwards {@code {channel, param, amount}} verbatim and lets whichever mod
 * declared that channel decide what the post means. Declare a channel id through
 * {@link ContributionChannelRegistry} so authors get it in the editor dropdown and out of the
 * {@code UNKNOWN_CHANNEL} validator warn; an undeclared channel still forwards.
 *
 * <p><b>Which list a contribution arrives on decides its scaling</b>, never a flag on the record:
 * {@code StationCycleCompletedEvent.contributions()} entries are the station's own per-cycle posts
 * (an idle cycle already pre-scaled them by the idle
 * fraction), while {@code oneShotContributions()} entries are find grants that must be posted at
 * their stated amount with no multiplier at all.
 *
 * @param channel the namespaced channel id (opaque to the engine, never blank)
 * @param param   the optional channel-interpreted argument (opaque to the engine; may be null)
 * @param amount  the amount posted, already scaled or deliberately unscaled per the list it
 *                arrived on
 */
public record StationContribution(@Nonnull String channel, @Nullable String param, double amount) {
}
