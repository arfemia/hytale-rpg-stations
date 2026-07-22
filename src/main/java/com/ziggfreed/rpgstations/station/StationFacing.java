package com.ziggfreed.rpgstations.station;

/**
 * PURE yaw math for the optional player-model rotation lock: computes the yaw a player at one
 * position must face to look at another position, in the engine's own convention. Ported
 * verbatim from the MMO's {@code station.StationFacing} (RPG Stations extraction leg 2).
 *
 * <p>The convention: the protocol {@code Direction} struct is three RADIAN floats
 * {@code (yaw, pitch, roll)}. The yaw formula mirrors the engine's own
 * {@code Rotation3f.lookAt(Vector3d relative)}: {@code yaw = atan2(-relative.x, -relative.z)},
 * wrapped to {@code (-PI, PI]}.
 */
final class StationFacing {

    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    private StationFacing() {
    }

    /**
     * The yaw (radians, engine convention) that faces {@code (toX, toZ)} from
     * {@code (fromX, fromZ)}. Y is deliberately ignored (a station face-block lock is a
     * horizontal turn only).
     */
    static float yawToward(double fromX, double fromZ, double toX, double toZ) {
        float yaw = (float) Math.atan2(fromX - toX, fromZ - toZ);
        return wrapAngle(yaw);
    }

    /** Reproduces the engine's {@code MathUtil#wrapAngle} algorithm (wrap to {@code (-PI, PI]}). */
    private static float wrapAngle(float angle) {
        angle = angle % TWO_PI;
        if (angle <= -PI) {
            angle += TWO_PI;
        } else if (angle > PI) {
            angle -= TWO_PI;
        }
        return angle;
    }
}
