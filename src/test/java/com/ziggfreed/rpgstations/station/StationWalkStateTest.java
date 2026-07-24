package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.joml.Vector3d;

/**
 * The pure walk state machine (scope-2 wave 3, design 2.3): the arrival + timeout DECISIONS that
 * gate a {@code Walk} phase's suspend/resume across ticks, verified without a live server (the
 * per-tick {@code PlayerPuppetService#walkTick}/{@code #setWalking} transform/movement-state writes
 * are engine-only and covered by ziggfreed-common's own math tests). Models the two-tick
 * "still walking -> arrived" transition the composite handler drives each frame.
 */
class StationWalkStateTest {

    private static List<Vector3d> straightPath(double length) {
        return List.of(new Vector3d(0, 64, 0), new Vector3d(length, 64, 0));
    }

    @Test
    void pathLengthComputedFromWaypoints() {
        StationWalkState w = new StationWalkState("fire", straightPath(10.0), 2.5, 1000L);
        assertEquals(10.0, w.pathLength, 1e-9);
    }

    @Test
    void twoTickSuspendResume_stillWalkingThenArrives() {
        // Frame 0: fresh walk, progress 0 -> NOT arrived (the handler suspends).
        StationWalkState w = new StationWalkState("fire", straightPath(10.0), 2.5, 1000L);
        assertFalse(w.arrived(), "a fresh walk has not arrived");

        // Frame 1: the drain advanced the puppet ~partway (2.5 m/s over ~1.2s) -> still walking.
        w.progress = 3.0;
        assertFalse(w.arrived(), "mid-path is still walking (keeps suspending)");
        assertFalse(w.timedOut(1000L + 2000L), "not timed out yet");

        // Frame 2: the puppet covered the whole path -> ARRIVED (the handler clears walkState).
        w.progress = 10.0;
        assertTrue(w.arrived(), "full progress = arrived");
    }

    @Test
    void arrivalWithinEpsilon() {
        StationWalkState w = new StationWalkState("fire", straightPath(10.0), 2.5, 0L);
        w.progress = 10.0 - StationWalkState.ARRIVAL_EPSILON + 0.01; // just inside the tolerance
        assertTrue(w.arrived());
        w.progress = 10.0 - StationWalkState.ARRIVAL_EPSILON - 0.01; // just outside
        assertFalse(w.arrived());
    }

    @Test
    void timeoutCompletesAsArrived() {
        // ideal travel = 10 / 2.5 * 1000 = 4000ms; grace 2000ms -> threshold 6000ms after start.
        StationWalkState w = new StationWalkState("fire", straightPath(10.0), 2.5, 1000L);
        assertFalse(w.timedOut(1000L + 6000L), "exactly at the threshold is not yet timed out");
        assertTrue(w.timedOut(1000L + 6001L), "past distance/speed + grace completes as-arrived (never wedges)");
    }

    @Test
    void nonPositiveSpeedDefaultsTo2_5() {
        StationWalkState w = new StationWalkState("self", straightPath(5.0), 0.0, 0L);
        assertEquals(2.5, w.speedMps, 1e-9, "a non-positive speed falls back to the 2.5 default");
    }
}
