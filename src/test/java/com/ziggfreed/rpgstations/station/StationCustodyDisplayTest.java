package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Custody;

/**
 * Pure tests for {@link StationCustodyDisplay}'s ONLY unit-JVM-safe logic -
 * {@link StationCustodyDisplay#resolvePosition}/{@link StationCustodyDisplay#resolveRotationRadians}/
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

    // ==================== resolveRotationRadians (D-1: nested {X,Y,Z} degrees group) ====================

    @Test
    void resolveRotationRadians_noDisplay_allZero() {
        assertArrayEquals(new float[] {0f, 0f, 0f}, StationCustodyDisplay.resolveRotationRadians(null));
    }

    @Test
    void resolveRotationRadians_noRotationGroup_allZero() {
        assertArrayEquals(new float[] {0f, 0f, 0f},
                StationCustodyDisplay.resolveRotationRadians(Custody.Display.of(null, null, null)));
    }

    @Test
    void resolveRotationRadians_yawOnly_convertsYToRadians() {
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(null, 180.0, null));
        assertArrayEquals(new float[] {0f, (float) Math.PI, 0f},
                StationCustodyDisplay.resolveRotationRadians(display), 1e-5f);
    }

    @Test
    void resolveRotationRadians_pitchOnly_convertsXToRadians() {
        // The reported anvil defect's fix: X (pitch) = 90 lays the weapon flat.
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(90.0, null, null));
        assertArrayEquals(new float[] {(float) (Math.PI / 2.0), 0f, 0f},
                StationCustodyDisplay.resolveRotationRadians(display), 1e-5f);
    }

    @Test
    void resolveRotationRadians_fullXYZ_convertsEachAxisInOrder() {
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(90.0, 180.0, 45.0));
        assertArrayEquals(
                new float[] {(float) Math.toRadians(90.0), (float) Math.toRadians(180.0), (float) Math.toRadians(45.0)},
                StationCustodyDisplay.resolveRotationRadians(display), 1e-5f);
    }

    @Test
    void resolveRotationRadians_partialGroup_missingLeavesDefaultToZero() {
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(null, null, 90.0));
        assertArrayEquals(new float[] {0f, 0f, (float) (Math.PI / 2.0)},
                StationCustodyDisplay.resolveRotationRadians(display), 1e-5f);
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
