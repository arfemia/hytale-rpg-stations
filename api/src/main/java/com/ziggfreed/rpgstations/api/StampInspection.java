package com.ziggfreed.rpgstations.api;

import java.util.Map;

import javax.annotation.Nonnull;

/**
 * A registered {@link EnhanceStamper}'s read-only report of a stack's CURRENT enhancement state
 * for the cap-composition math: RpgStations' own
 * {@code station.StampCapEngine} reads this BEFORE rolling/clamping a Stamp step's stat entries, so
 * cap evaluation never mutates anything and a fully-capped item denies with zero reagent
 * consumption. Format-opaque to RpgStations - the registered stamper alone knows how "points
 * already stamped" is stored on the stack.
 *
 * @param totalPoints    the sum of every stat point currently stamped on the stack, across all
 *                       stats (compared against every authored {@code Caps.Budgets} entry).
 * @param pointsByStat   per-stat-id current point totals (compared against {@code PerStat}); a
 *                       stat id absent from this map has zero points stamped so far.
 * @param stampCount     how many times this exact stack has been successfully stamped before now
 *                       ({@code Economics.RepeatCostMultiplier} reads this to scale reagent cost
 *                       per prior stamp).
 */
public record StampInspection(int totalPoints, @Nonnull Map<String, Integer> pointsByStat, int stampCount) {

    /** A bare/never-enhanced stack: zero points, zero prior stamps. */
    @Nonnull
    public static StampInspection empty() {
        return new StampInspection(0, Map.of(), 0);
    }
}
