package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;


/**
 * Pure tests for {@link StationToolScaling}: the held-tool power read plus the idle-cadence and
 * durability-drain reader defaults. There is no tool-power CURVE here to test - the engine holds no
 * baked multiplier of its own, so "a better tool earns more" is authored as a factor inside a
 * recipe Yield ladder instead.
 */
public class StationToolScalingTest {

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

    // ==================== resolvedIdleCycleMs() / resolvedIdleFraction() ====================

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
    void resolvedIdleFraction_defaultsAndClamps() {
        assertEquals(0.1, StationToolScaling.resolvedIdleFraction(null), 1e-9);
        assertEquals(0.0, StationToolScaling.resolvedIdleFraction(-0.5), 1e-9);
        assertEquals(1.0, StationToolScaling.resolvedIdleFraction(1.5), 1e-9);
        assertEquals(0.35, StationToolScaling.resolvedIdleFraction(0.35), 1e-9);
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
