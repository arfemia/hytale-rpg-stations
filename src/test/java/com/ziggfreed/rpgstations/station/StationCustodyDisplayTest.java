package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Custody;

/**
 * Pure tests for {@link StationCustodyDisplay}'s unit-JVM-safe composition cores -
 * {@link StationCustodyDisplay#resolveWorldOffset}/{@link StationCustodyDisplay#resolvePosition}/
 * {@link StationCustodyDisplay#resolveRotationRadians}/{@link StationCustodyDisplay#resolveScale}
 * (design section 9, phase 2 leg G + round-8 FACING-RELATIVE composition: the placed-input
 * PLACED-AS-ENTITY visual's Offset/Rotation math, kept primitive-typed - the placed block's facing
 * enters as a plain {@code blockYawRadians} scalar - so it needs no live Hytale ECS/world type, the
 * same discipline {@code StationEntityMountControllerTest} establishes for its sibling class).
 * {@link StationCustodyDisplay#spawn}/{@link StationCustodyDisplay#despawn} and the impure block-facing
 * read ({@code blockYawRadians}) touch live Store/World/Holder types and have NO unit coverage,
 * matching that precedent.
 *
 * <p>Block facings are the engine's discrete 0/90/180/270 yaws (radians here). The rotation follows
 * the engine's own block-vector yaw convention ({@code Rotation.rotateY}:
 * {@code x' = x*cos + z*sin}, {@code z' = -x*sin + z*cos}); the four-facing cases below assert exactly
 * that mapping. A default-orientation placement (yaw 0) is the identity, so every pre-round-8 value is
 * unchanged (the yaw-0 cases assert byte-identity with the old world-space behavior).
 */
class StationCustodyDisplayTest {

    private static final double YAW_90 = Math.PI / 2.0;
    private static final double YAW_180 = Math.PI;
    private static final double YAW_270 = 3.0 * Math.PI / 2.0;

    // ==================== resolveWorldOffset (round-8 facing-relative X/Z rotation) ====================

    @Test
    void resolveWorldOffset_noDisplay_isZero() {
        assertArrayEquals(new double[] {0.0, 0.0, 0.0},
                StationCustodyDisplay.resolveWorldOffset(null, 0.0));
    }

    @Test
    void resolveWorldOffset_defaultFacing_isIdentity() {
        // yaw 0 (default placement): the authored X/Z pass through unchanged, exactly the pre-round-8
        // world-space behavior - existing packs render byte-identically.
        Custody.Display.Offset offset = Custody.Display.Offset.of(0.3, 0.55, -0.2);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {0.3, 0.55, -0.2},
                StationCustodyDisplay.resolveWorldOffset(display, 0.0));
    }

    @Test
    void resolveWorldOffset_xOffset_rotatesThroughFourFacings() {
        // Authored Offset.X = 0.3 (a pure +X shift) rotated by each block facing. Matches the engine's
        // Rotation.rotateY on (0.3, y, 0): None (0.3,0), Ninety (0,-0.3), OneEighty (-0.3,0), TwoSeventy (0,0.3).
        Custody.Display.Offset offset = Custody.Display.Offset.of(0.3, 0.0, 0.0);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {0.3, 0.0, 0.0},
                StationCustodyDisplay.resolveWorldOffset(display, 0.0), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0, -0.3},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_90), 1e-9);
        assertArrayEquals(new double[] {-0.3, 0.0, 0.0},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_180), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0, 0.3},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_270), 1e-9);
    }

    @Test
    void resolveWorldOffset_zOffset_rotatesThroughFourFacings() {
        // Authored Offset.Z = 0.3 (a pure +Z shift) rotated by each block facing. Matches the engine's
        // Rotation.rotateY on (0, y, 0.3): None (0,0.3), Ninety (0.3,0), OneEighty (0,-0.3), TwoSeventy (-0.3,0).
        Custody.Display.Offset offset = Custody.Display.Offset.of(0.0, 0.0, 0.3);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {0.0, 0.0, 0.3},
                StationCustodyDisplay.resolveWorldOffset(display, 0.0), 1e-9);
        assertArrayEquals(new double[] {0.3, 0.0, 0.0},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_90), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0, -0.3},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_180), 1e-9);
        assertArrayEquals(new double[] {-0.3, 0.0, 0.0},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_270), 1e-9);
    }

    @Test
    void resolveWorldOffset_mixedOffset_rotatesBothAxes() {
        // Offset (X=1, Z=2) at yaw 90: worldX = 1*cos + 2*sin = 2, worldZ = -1*sin + 2*cos = -1;
        // matches Rotation.rotateY(Ninety) on (1, y, 2) = (2, y, -1). Y is untouched (vertical).
        Custody.Display.Offset offset = Custody.Display.Offset.of(1.0, 0.5, 2.0);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {2.0, 0.5, -1.0},
                StationCustodyDisplay.resolveWorldOffset(display, YAW_90), 1e-9);
    }

    @Test
    void resolveWorldOffset_yStaysVertical_neverRotated() {
        // Only Y authored: it stays put at every facing (Y is the vertical axis, never rotated).
        Custody.Display.Offset offset = Custody.Display.Offset.of(null, 0.55, null);
        Custody.Display display = Custody.Display.of(offset, null, null);
        for (double yaw : new double[] {0.0, YAW_90, YAW_180, YAW_270}) {
            assertArrayEquals(new double[] {0.0, 0.55, 0.0},
                    StationCustodyDisplay.resolveWorldOffset(display, yaw), 1e-9);
        }
    }

    @Test
    void resolveWorldOffset_partiallyAuthored_missingLeavesDefaultToZero() {
        Custody.Display.Offset offset = Custody.Display.Offset.of(1.0, null, null);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {1.0, 0.0, 0.0},
                StationCustodyDisplay.resolveWorldOffset(display, 0.0), 1e-9);
    }

    // ==================== resolvePosition (block center + facing-relative offset) ====================

    @Test
    void resolvePosition_noDisplay_isBlockCenter() {
        assertArrayEquals(new double[] {10.5, 64.5, -3.5},
                StationCustodyDisplay.resolvePosition(null, 10, 64, -4, 0.0));
    }

    @Test
    void resolvePosition_noOffset_isBlockCenter() {
        Custody.Display display = Custody.Display.of(null, null, null);
        assertArrayEquals(new double[] {10.5, 64.5, -3.5},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4, 0.0));
    }

    @Test
    void resolvePosition_authoredOffset_defaultFacing_shiftsEachAxis() {
        // yaw 0: identical to the pre-round-8 world-space behavior (exact, cos 0 = 1 / sin 0 = 0).
        Custody.Display.Offset offset = Custody.Display.Offset.of(0.0, 0.55, 0.2);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {10.5, 65.05, -3.3},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4, 0.0));
    }

    @Test
    void resolvePosition_authoredOffset_rotatedFacing_shiftsRelativeToBlock() {
        // Offset.X = 1 at yaw 90 -> world (0, 0, -1): block center (10.5, 64.5, -3.5) + (0, 0, -1).
        Custody.Display.Offset offset = Custody.Display.Offset.of(1.0, 0.0, 0.0);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {10.5, 64.5, -4.5},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4, YAW_90), 1e-9);
    }

    @Test
    void resolvePosition_partiallyAuthoredOffset_missingLeavesDefaultToZero() {
        Custody.Display.Offset offset = Custody.Display.Offset.of(1.0, null, null);
        Custody.Display display = Custody.Display.of(offset, null, null);
        assertArrayEquals(new double[] {11.5, 64.5, -3.5},
                StationCustodyDisplay.resolvePosition(display, 10, 64, -4, 0.0));
    }

    // ==================== resolveRotationRadians (nested {X,Y,Z} degrees + facing-relative yaw) ====

    @Test
    void resolveRotationRadians_noDisplay_defaultFacing_allZero() {
        assertArrayEquals(new float[] {0f, 0f, 0f}, StationCustodyDisplay.resolveRotationRadians(null, 0.0));
    }

    @Test
    void resolveRotationRadians_noRotationGroup_defaultFacing_allZero() {
        assertArrayEquals(new float[] {0f, 0f, 0f},
                StationCustodyDisplay.resolveRotationRadians(Custody.Display.of(null, null, null), 0.0));
    }

    @Test
    void resolveRotationRadians_yawOnly_defaultFacing_convertsYToRadians() {
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(null, 180.0, null));
        assertArrayEquals(new float[] {0f, (float) Math.PI, 0f},
                StationCustodyDisplay.resolveRotationRadians(display, 0.0), 1e-5f);
    }

    @Test
    void resolveRotationRadians_pitchOnly_defaultFacing_convertsXToRadians() {
        // The reported anvil defect's fix: X (pitch) = 90 lays the weapon flat.
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(90.0, null, null));
        assertArrayEquals(new float[] {(float) (Math.PI / 2.0), 0f, 0f},
                StationCustodyDisplay.resolveRotationRadians(display, 0.0), 1e-5f);
    }

    @Test
    void resolveRotationRadians_fullXYZ_defaultFacing_convertsEachAxisInOrder() {
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(90.0, 180.0, 45.0));
        assertArrayEquals(
                new float[] {(float) Math.toRadians(90.0), (float) Math.toRadians(180.0), (float) Math.toRadians(45.0)},
                StationCustodyDisplay.resolveRotationRadians(display, 0.0), 1e-5f);
    }

    @Test
    void resolveRotationRadians_partialGroup_defaultFacing_missingLeavesDefaultToZero() {
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(null, null, 90.0));
        assertArrayEquals(new float[] {0f, 0f, (float) (Math.PI / 2.0)},
                StationCustodyDisplay.resolveRotationRadians(display, 0.0), 1e-5f);
    }

    @Test
    void resolveRotationRadians_blockYawAddedIntoYaw_pitchRollUnchanged() {
        // Authored Y=45deg with the block placed at 90deg -> yaw = PI/4 + PI/2 = 3PI/4;
        // X (pitch) and Z (roll) ride the block facing unchanged.
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(90.0, 45.0, 20.0));
        assertArrayEquals(
                new float[] {(float) Math.toRadians(90.0), (float) (Math.toRadians(45.0) + YAW_90),
                        (float) Math.toRadians(20.0)},
                StationCustodyDisplay.resolveRotationRadians(display, YAW_90), 1e-5f);
    }

    @Test
    void resolveRotationRadians_noAuthoredYaw_blockFacingBecomesYaw() {
        // The anvil enhance case: authored X=90 (pitch), no Y. At a 180deg placement the prop's yaw
        // is purely the block facing (PI); pitch stays PI/2, roll 0.
        Custody.Display display = Custody.Display.of(null, null, Custody.Display.Rotation.of(90.0, null, null));
        assertArrayEquals(new float[] {(float) (Math.PI / 2.0), (float) Math.PI, 0f},
                StationCustodyDisplay.resolveRotationRadians(display, YAW_180), 1e-5f);
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
