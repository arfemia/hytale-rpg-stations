package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Pure tests for {@link StationToolScaling}. Ported from the MMO's
 * {@code StationToolScalingTest} (RPG Stations extraction leg 2).
 */
public class StationToolScalingTest {

    private static StationAsset.Tool.XpScale scale(Double referencePower, Double exponent,
            Double minMult, Double maxMult) {
        return StationAsset.Tool.XpScale.of(null, referencePower, exponent, minMult, maxMult);
    }

    // ==================== multiplier() ====================

    @Test
    void vanillaHatchetTriple_sawmillClamp() {
        StationAsset.Tool.XpScale sawmillScale = scale(0.2, null, 0.75, 1.5);
        assertEquals(0.75, StationToolScaling.multiplier(0.15, sawmillScale), 1e-9);
        assertEquals(1.0, StationToolScaling.multiplier(0.20, sawmillScale), 1e-9);
        assertEquals(1.5, StationToolScaling.multiplier(0.30, sawmillScale), 1e-9);
    }

    @Test
    void pickaxeFlatPower_landsOnDefaultMinMult() {
        StationAsset.Tool.XpScale defaultScale = scale(0.2, null, null, null);
        assertEquals(0.5, StationToolScaling.multiplier(0.05, defaultScale), 1e-9);
    }

    @Test
    void exponentShaping() {
        StationAsset.Tool.XpScale squared = scale(0.2, 2.0, 0.0, 10.0);
        assertEquals(4.0, StationToolScaling.multiplier(0.4, squared), 1e-9);
    }

    @Test
    void clampEdges_neverExceedMinOrMax() {
        StationAsset.Tool.XpScale clamped = scale(0.2, 1.0, 0.75, 1.5);
        assertEquals(1.5, StationToolScaling.multiplier(100.0, clamped), 1e-9);
        assertEquals(0.75, StationToolScaling.multiplier(0.0001, clamped), 1e-9);
    }

    @Test
    void nullScale_isNeutral() {
        assertEquals(1.0, StationToolScaling.multiplier(0.30, null), 1e-9);
    }

    @Test
    void nullOrNonpositiveReferencePower_isNeutral() {
        assertEquals(1.0, StationToolScaling.multiplier(0.30, scale(null, null, null, null)), 1e-9);
        assertEquals(1.0, StationToolScaling.multiplier(0.30, scale(0.0, null, null, null)), 1e-9);
        assertEquals(1.0, StationToolScaling.multiplier(0.30, scale(-1.0, null, null, null)), 1e-9);
    }

    @Test
    void nonpositiveHeldPower_isNeutral() {
        assertEquals(1.0, StationToolScaling.multiplier(-1.0, scale(0.2, null, 0.75, 1.5)), 1e-9);
        assertEquals(1.0, StationToolScaling.multiplier(0.0, scale(0.2, null, 0.75, 1.5)), 1e-9);
    }

    // ==================== heldPowerFor() ====================

    @Test
    void heldPowerFor_maxAcrossMatchingSpecs_caseInsensitive() {
        List<StationToolScaling.ToolPower> specs = List.of(
                new StationToolScaling.ToolPower("woods", 0.15),
                new StationToolScaling.ToolPower("Stone", 0.40),
                new StationToolScaling.ToolPower("Woods", 0.30));
        assertEquals(0.30, StationToolScaling.heldPowerFor(specs, "Woods"), 1e-6);
    }

    @Test
    void heldPowerFor_noMatch_returnsNegativeSentinel() {
        List<StationToolScaling.ToolPower> specs = List.of(new StationToolScaling.ToolPower("Stone", 0.40));
        assertEquals(-1.0, StationToolScaling.heldPowerFor(specs, "Woods"), 1e-9);
    }

    @Test
    void heldPowerFor_nullOrEmptySpecsOrBlankGatherType_returnsNegativeSentinel() {
        assertEquals(-1.0, StationToolScaling.heldPowerFor(null, "Woods"), 1e-9);
        assertEquals(-1.0, StationToolScaling.heldPowerFor(List.of(), "Woods"), 1e-9);
    }

    // ==================== resolvedIdleCycleMs() / resolvedXpFraction() ====================

    @Test
    void resolvedIdleCycleMs_defaultsToThreeXWorkCycle() {
        assertEquals(15000L, StationToolScaling.resolvedIdleCycleMs(null, 5000L));
        assertEquals(15000L, StationToolScaling.resolvedIdleCycleMs(0L, 5000L));
        assertEquals(15000L, StationToolScaling.resolvedIdleCycleMs(-1L, 5000L));
    }

    @Test
    void resolvedIdleCycleMs_flooredAtTwoXWorkCycle() {
        assertEquals(10000L, StationToolScaling.resolvedIdleCycleMs(1000L, 5000L));
        assertEquals(20000L, StationToolScaling.resolvedIdleCycleMs(20000L, 5000L));
    }

    @Test
    void resolvedXpFraction_defaultsAndClamps() {
        assertEquals(0.1, StationToolScaling.resolvedXpFraction(null), 1e-9);
        assertEquals(0.0, StationToolScaling.resolvedXpFraction(-0.5), 1e-9);
        assertEquals(1.0, StationToolScaling.resolvedXpFraction(1.5), 1e-9);
        assertEquals(0.35, StationToolScaling.resolvedXpFraction(0.35), 1e-9);
    }

    // ==================== resolvedDurabilityAmount() ====================

    @Test
    void resolvedDurabilityAmount_nullIsOff() {
        assertEquals(0, StationToolScaling.resolvedDurabilityAmount(null));
    }

    @Test
    void resolvedDurabilityAmount_zeroOrNegativeIsOff() {
        assertEquals(0, StationToolScaling.resolvedDurabilityAmount(0));
        assertEquals(0, StationToolScaling.resolvedDurabilityAmount(-5));
    }

    @Test
    void resolvedDurabilityAmount_positiveIsPassedThrough() {
        assertEquals(3, StationToolScaling.resolvedDurabilityAmount(3));
    }
}
