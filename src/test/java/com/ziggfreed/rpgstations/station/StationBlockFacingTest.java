package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationBlockFacing#rotateOffset} - the ONE facing-relative horizontal
 * rotation core both {@link StationCustodyDisplay} (round-8 {@code Custody.Display}) and
 * {@link StationPuppetController} (round-3 smoke, {@code Puppet.Offset}) compose against. Its
 * sibling {@code yawRadians} reads a live {@code World}/{@code CommandBuffer} and has NO unit
 * coverage, matching {@link StationCustodyDisplayTest}'s own precedent.
 *
 * <p>Block facings are the engine's discrete 0/90/180/270 yaws (radians here); the mapping asserted
 * is the engine's own block-vector yaw convention ({@code Rotation.rotateY}:
 * {@code x' = x*cos + z*sin}, {@code z' = -x*sin + z*cos}).
 */
class StationBlockFacingTest {

    private static final double YAW_90 = Math.PI / 2.0;
    private static final double YAW_180 = Math.PI;
    private static final double YAW_270 = 3.0 * Math.PI / 2.0;

    @Test
    void defaultFacing_isIdentity() {
        // The load-bearing no-migration guarantee: at yaw 0 every authored value passes through
        // untouched, so values tuned before either consumer adopted this composition are unchanged.
        assertArrayEquals(new double[] {0.3, -0.4, 1.0},
                StationBlockFacing.rotateOffset(0.3, -0.4, 1.0, 0.0), 1e-9);
    }

    @Test
    void pureFrontOffset_rotatesThroughFourFacings() {
        assertArrayEquals(new double[] {0.0, 0.0, 1.0},
                StationBlockFacing.rotateOffset(0.0, 0.0, 1.0, 0.0), 1e-9);
        assertArrayEquals(new double[] {1.0, 0.0, 0.0},
                StationBlockFacing.rotateOffset(0.0, 0.0, 1.0, YAW_90), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0, -1.0},
                StationBlockFacing.rotateOffset(0.0, 0.0, 1.0, YAW_180), 1e-9);
        assertArrayEquals(new double[] {-1.0, 0.0, 0.0},
                StationBlockFacing.rotateOffset(0.0, 0.0, 1.0, YAW_270), 1e-9);
    }

    @Test
    void pureRightOffset_rotatesThroughFourFacings() {
        assertArrayEquals(new double[] {1.0, 0.0, 0.0},
                StationBlockFacing.rotateOffset(1.0, 0.0, 0.0, 0.0), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0, -1.0},
                StationBlockFacing.rotateOffset(1.0, 0.0, 0.0, YAW_90), 1e-9);
        assertArrayEquals(new double[] {-1.0, 0.0, 0.0},
                StationBlockFacing.rotateOffset(1.0, 0.0, 0.0, YAW_180), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0, 1.0},
                StationBlockFacing.rotateOffset(1.0, 0.0, 0.0, YAW_270), 1e-9);
    }

    @Test
    void verticalAxis_isNeverRotated() {
        for (double yaw : new double[] {0.0, YAW_90, YAW_180, YAW_270}) {
            assertArrayEquals(new double[] {0.0, -0.45, 0.0},
                    StationBlockFacing.rotateOffset(0.0, -0.45, 0.0, yaw), 1e-9);
        }
    }

    @Test
    void fullTurn_returnsToIdentity() {
        assertArrayEquals(new double[] {1.0, 2.0, 3.0},
                StationBlockFacing.rotateOffset(1.0, 2.0, 3.0, 2.0 * Math.PI), 1e-9);
    }
}
