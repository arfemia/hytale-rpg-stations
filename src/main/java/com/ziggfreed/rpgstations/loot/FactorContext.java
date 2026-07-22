package com.ziggfreed.rpgstations.loot;

import javax.annotation.Nonnull;

/**
 * Per-evaluation numeric context the built-in {@link StationFactorRegistry} providers read
 * (leg 3 INTERNAL stand-in for the design section 3.2 api {@code FactorContext} - leg 4 lands
 * the public, frozen, request/response {@code FactorRegistry} on the api artifact and this
 * class + {@link StationFactorRegistry} are adopted/retired in its favor; nothing outside the
 * {@code loot}/{@code station} packages should depend on this shape surviving leg 4).
 *
 * <p>Immutable; built fresh per evaluation batch (one per real/idle cycle or per session stop)
 * by {@code StationService}, matching the "one aggregation, many consumers" convention the
 * {@link FactorSnapshot} memoizes over.
 */
public final class FactorContext {

    private final long sessionSeconds;
    private final int cycleIndex;
    private final double toolPower;
    private final double toolDurabilityPercent;

    public FactorContext(long sessionSeconds, int cycleIndex, double toolPower, double toolDurabilityPercent) {
        this.sessionSeconds = sessionSeconds;
        this.cycleIndex = cycleIndex;
        this.toolPower = toolPower;
        this.toolDurabilityPercent = toolDurabilityPercent;
    }

    /** Whole seconds elapsed since the session started ({@code rpgstations:session_seconds}). */
    public long getSessionSeconds() {
        return sessionSeconds;
    }

    /** The 1-based cycle index this roll batch belongs to ({@code rpgstations:cycle_count}). */
    public int getCycleIndex() {
        return cycleIndex;
    }

    /** The held tool's resolved gather power for the station's effective gather type, 0 when none ({@code rpgstations:tool_power}). */
    public double getToolPower() {
        return toolPower;
    }

    /** The held tool's durability percent [0,100], 100 when no durability tracked ({@code rpgstations:tool_durability_percent}). */
    public double getToolDurabilityPercent() {
        return toolDurabilityPercent;
    }

    @Nonnull
    public static FactorContext of(long sessionSeconds, int cycleIndex, double toolPower, double toolDurabilityPercent) {
        return new FactorContext(sessionSeconds, cycleIndex, toolPower, toolDurabilityPercent);
    }
}
