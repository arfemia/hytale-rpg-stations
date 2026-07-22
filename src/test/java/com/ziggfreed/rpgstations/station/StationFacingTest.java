package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationFacing#yawToward}. Ported verbatim from the MMO's
 * {@code StationFacingTest} (RPG Stations extraction leg 2).
 */
public class StationFacingTest {

    private static final float PI = (float) Math.PI;
    private static final float EPSILON = 1e-4f;

    @Test
    void facingNegativeZ_yawsToZero() {
        assertEquals(0f, StationFacing.yawToward(0, 0, 0, -1), EPSILON);
    }

    @Test
    void facingPositiveX_yawsToNegativeHalfPi() {
        assertEquals(-PI / 2f, StationFacing.yawToward(0, 0, 1, 0), EPSILON);
    }

    @Test
    void facingPositiveZ_yawsToPi() {
        assertEquals(PI, StationFacing.yawToward(0, 0, 0, 1), EPSILON);
    }

    @Test
    void facingNegativeX_yawsToPositiveHalfPi() {
        assertEquals(PI / 2f, StationFacing.yawToward(0, 0, -1, 0), EPSILON);
    }

    @Test
    void diagonal_northEast_yawsToNegativeQuarterPi() {
        assertEquals(-PI / 4f, StationFacing.yawToward(0, 0, 1, -1), EPSILON);
    }

    @Test
    void diagonal_southEast_yawsToNegativeThreeQuarterPi() {
        assertEquals(-3f * PI / 4f, StationFacing.yawToward(0, 0, 1, 1), EPSILON);
    }

    @Test
    void diagonal_southWest_yawsToPositiveThreeQuarterPi() {
        assertEquals(3f * PI / 4f, StationFacing.yawToward(0, 0, -1, 1), EPSILON);
    }

    @Test
    void diagonal_northWest_yawsToPositiveQuarterPi() {
        assertEquals(PI / 4f, StationFacing.yawToward(0, 0, -1, -1), EPSILON);
    }

    @Test
    void onlyRelativePositionMatters_notAbsoluteCoordinates() {
        assertEquals(StationFacing.yawToward(0, 0, 1, -1),
                StationFacing.yawToward(500, -1200, 501, -1201), EPSILON);
    }

    @Test
    void samePosition_isDegenerateButFinite() {
        assertEquals(0f, StationFacing.yawToward(3, 3, 3, 3), EPSILON);
    }
}
