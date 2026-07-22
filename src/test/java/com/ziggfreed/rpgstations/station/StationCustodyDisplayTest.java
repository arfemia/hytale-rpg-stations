package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Custody;

/**
 * Pure tests for {@link StationCustodyDisplay}'s ONLY unit-JVM-safe logic -
 * {@link StationCustodyDisplay#resolvePosition}/{@link StationCustodyDisplay#resolveYawRadians}/
 * {@link StationCustodyDisplay#resolveScale} (design section 9, phase 2 leg G: the placed-input
 * PLACED-AS-ENTITY visual's Offset/Scale/Rotation math, kept primitive-typed so it needs no live
 * Hytale ECS type - the same discipline {@code StationEntityMountControllerTest} establishes for
 * its sibling class). {@link StationCustodyDisplay#spawn}/{@link StationCustodyDisplay#despawn}
 * touch live Store/Holder/component types and have NO unit coverage, matching that precedent.
 */
class StationCustodyDisplayTest {

    // ==================== resolvePosition ====================

    @Test
    void resolvePosition_noDisplay_isBlockCenter() {
        assertArrayEquals(new double[] {10.5, 64.5, -3.5},
                StationCustodyDisplay.resolvePosition(null, 10, 64, -4));
    }

    @Test
    void resolvePosition_noOffset_isBlockCenter() {
        Custody.Display display = Custody.Display.of(null, null, null);
        assertArrayEquals(new double[] {10.5, 64.5, -3.5},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4));
    }

    @Test
    void resolvePosition_authoredOffset_shiftsEachAxis() {
        Custody.Display.Offset offset = Custody.Display.Offset.of(0.0, 0.55, 0.2);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {10.5, 65.05, -3.3},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4));
    }

    @Test
    void resolvePosition_partiallyAuthoredOffset_missingLeavesDefaultToZero() {
        Custody.Display.Offset offset = Custody.Display.Offset.of(1.0, null, null);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {11.5, 64.5, -3.5},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4));
    }

    // ==================== resolveYawRadians ====================

    @Test
    void resolveYawRadians_noDisplay_isZero() {
        assertEquals(0f, StationCustodyDisplay.resolveYawRadians(null));
    }

    @Test
    void resolveYawRadians_noRotationAuthored_isZero() {
        assertEquals(0f, StationCustodyDisplay.resolveYawRadians(Custody.Display.of(null, null, null)));
    }

    @Test
    void resolveYawRadians_authoredDegrees_convertsToRadians() {
        Custody.Display display = Custody.Display.of(null, null, 180.0);
        assertEquals((float) Math.PI, StationCustodyDisplay.resolveYawRadians(display), 1e-5f);
    }

    // ==================== resolveScale ====================

    @Test
    void resolveScale_noDisplay_defaultsToOne() {
        assertEquals(1f, StationCustodyDisplay.resolveScale(null));
    }

    @Test
    void resolveScale_noneAuthored_defaultsToOne() {
        assertEquals(1f, StationCustodyDisplay.resolveScale(Custody.Display.of(null, null, null)));
    }

    @Test
    void resolveScale_authoredPositive_usesIt() {
        assertEquals(1.5f, StationCustodyDisplay.resolveScale(Custody.Display.of(null, 1.5, null)));
    }

    @Test
    void resolveScale_nonPositiveAuthored_fallsBackToOne() {
        assertEquals(1f, StationCustodyDisplay.resolveScale(Custody.Display.of(null, 0.0, null)));
        assertEquals(1f, StationCustodyDisplay.resolveScale(Custody.Display.of(null, -2.0, null)));
    }
}
