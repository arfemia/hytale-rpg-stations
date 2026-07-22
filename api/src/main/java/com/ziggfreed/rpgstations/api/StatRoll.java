package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * ONE resolved, cap-clamped stat-roll entry crossing the {@link EnhanceStamper} boundary (design
 * section 9.5): a stat id (opaque to RpgStations - only the registered stamper interprets it) plus
 * a whole-point value already rolled and clamped against every authored cap
 * ({@code PerItemBudget}/{@code PerStat}/{@code SkillScaledBudget}, the M2-fixed MIN-of-every-
 * authored-total-budget-cap rule) by {@code station.StampCapEngine} - the stamper never re-derives
 * caps, it only applies the finished entries.
 */
public record StatRoll(@Nonnull String statId, int points) {
}
