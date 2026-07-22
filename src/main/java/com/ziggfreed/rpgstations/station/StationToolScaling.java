package com.ziggfreed.rpgstations.station;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * PURE decision cores for two station knobs, kept out of {@link StationService} so both are
 * unit-testable without the store, the live item map, or a running session. Ported verbatim
 * from the MMO's {@code station.StationToolScaling} (RPG Stations extraction leg 2).
 *
 * <ul>
 *   <li><b>Tool-power XP scaling:</b> {@link #multiplier} implements
 *       {@code clamp((heldPower / ReferencePower) ^ Exponent, MinMult, MaxMult)} over a
 *       station's {@link StationAsset.Tool.XpScale}; {@link #heldPowerFor} is the pure spec
 *       scan that {@code StationService} feeds from the held item's live
 *       {@code ItemTool.getSpecs()}.</li>
 *   <li><b>Idle practice cadence/fraction:</b> {@link #resolvedIdleCycleMs} and
 *       {@link #resolvedXpFraction} apply {@code Work.Idle}'s reader defaults/floor/clamp.</li>
 * </ul>
 *
 * <p>Zero-authoring neutrality throughout: a null/inactive {@link StationAsset.Tool.XpScale}
 * or a held tool with no matching spec both resolve to multiplier 1.0.
 */
public final class StationToolScaling {

    /** {@link #heldPowerFor} sentinel: no spec matched the requested gather type. */
    static final double NO_MATCH = -1.0;

    static final double DEFAULT_EXPONENT = 1.0;
    static final double DEFAULT_MIN_MULT = 0.5;
    static final double DEFAULT_MAX_MULT = 2.0;

    static final double DEFAULT_IDLE_XP_FRACTION = 0.1;

    private StationToolScaling() {
    }

    /**
     * A normalized {@code (gatherType, power)} read of one live {@code ItemToolSpec}, for the
     * PURE {@link #heldPowerFor} core - merely CONSTRUCTING a real {@code ItemToolSpec}
     * triggers its {@code AssetBuilderCodec} static init, which throws outside a running
     * Hytale server, so a unit test (and this pure core) never touches the live asset class.
     * {@code StationService} builds these from the held item's real
     * {@code ItemTool.getSpecs()} at the one live call site.
     */
    static final class ToolPower {
        @Nullable final String gatherType;
        final double power;

        ToolPower(@Nullable String gatherType, double power) {
            this.gatherType = gatherType;
            this.power = power;
        }
    }

    // ==================== Tool-power XP scaling ====================

    /**
     * {@code clamp((heldPower / ReferencePower) ^ Exponent, MinMult, MaxMult)}. Neutral (1.0)
     * for a null {@code scale}, a null/nonpositive {@code ReferencePower}, or a nonpositive
     * {@code heldPower} (the {@link #NO_MATCH} sentinel from {@link #heldPowerFor}).
     */
    static double multiplier(double heldPower, @Nullable StationAsset.Tool.XpScale scale) {
        if (scale == null || heldPower <= 0.0) {
            return 1.0;
        }
        Double referencePower = scale.getReferencePower();
        if (referencePower == null || referencePower <= 0.0) {
            return 1.0;
        }
        double exponent = scale.getExponent() != null ? scale.getExponent() : DEFAULT_EXPONENT;
        double minMult = scale.getMinMult() != null ? scale.getMinMult() : DEFAULT_MIN_MULT;
        double maxMult = scale.getMaxMult() != null ? scale.getMaxMult() : DEFAULT_MAX_MULT;
        double raw = Math.pow(heldPower / referencePower, exponent);
        return Math.max(minMult, Math.min(maxMult, raw));
    }

    /**
     * The MAX {@code power} across every entry in {@code specs} whose {@code gatherType}
     * matches {@code gatherType} case-insensitively; {@link #NO_MATCH} (-1.0) when
     * {@code specs} is null/empty, {@code gatherType} is blank, or nothing matches.
     */
    static double heldPowerFor(@Nullable Collection<ToolPower> specs, @Nonnull String gatherType) {
        if (specs == null || specs.isEmpty() || gatherType.isBlank()) {
            return NO_MATCH;
        }
        double best = NO_MATCH;
        for (ToolPower spec : specs) {
            if (spec == null || spec.gatherType == null) {
                continue;
            }
            if (spec.gatherType.equalsIgnoreCase(gatherType) && spec.power > best) {
                best = spec.power;
            }
        }
        return best;
    }

    // ==================== Idle practice cadence/fraction ====================

    /**
     * {@code Work.Idle.CycleMs}'s reader resolution: a null or nonpositive authored value
     * resolves to 3x {@code workCycleMs}; any authored value is FLOORED at 2x it.
     */
    static long resolvedIdleCycleMs(@Nullable Long authoredCycleMs, long workCycleMs) {
        long floor = 2L * workCycleMs;
        if (authoredCycleMs == null || authoredCycleMs <= 0) {
            return 3L * workCycleMs;
        }
        return Math.max(authoredCycleMs, floor);
    }

    /**
     * {@code Work.Idle.XpFraction}'s reader resolution: a null authored value resolves to
     * {@link #DEFAULT_IDLE_XP_FRACTION} (0.1); any authored value is CLAMPED to {@code [0, 1]}.
     */
    static double resolvedXpFraction(@Nullable Double authoredFraction) {
        double v = authoredFraction != null ? authoredFraction : DEFAULT_IDLE_XP_FRACTION;
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    // ==================== Held-tool durability drain ====================

    /**
     * {@code Tool.Durability.PerSwing}/{@code PerCycle}'s reader resolution: a null or
     * nonpositive authored value is OFF (0 - opt-in only). The two leaves resolve
     * independently; either, both, or neither may be authored.
     */
    static int resolvedDurabilityAmount(@Nullable Integer authored) {
        return authored != null && authored > 0 ? authored : 0;
    }
}
