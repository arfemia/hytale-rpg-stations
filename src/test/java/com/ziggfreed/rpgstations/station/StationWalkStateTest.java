package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.entity.performer.WalkHandle;

/**
 * The pure walk POLICY (scope-2 wave 3, design 2.3) after the decision-55 performer-seam route (F2):
 * the walk MECHANISM (path solve + per-tick advance) now lives inside the performer's
 * {@link WalkHandle}, so {@link StationWalkState} keeps only the outer anti-wedge {@code timedOut}
 * timer and the completion mapping lives in {@link StationStepDecisions#walkStepDone}. Verified
 * without a live server (a fake {@code WalkHandle} stands in for the engine-driven one, whose own
 * arrival/stuck semantics ziggfreed-common tests).
 */
class StationWalkStateTest {

    /** A no-op {@link WalkHandle} stand-in - {@link StationWalkState} only holds it, never polls it here. */
    private static final class FakeHandle implements WalkHandle {
        @Override
        @Nonnull
        public State poll(@Nonnull ComponentAccessor<EntityStore> accessor, double dtMs) {
            return State.WALKING;
        }

        @Override
        @Nonnull
        public State state() {
            return State.WALKING;
        }

        @Override
        public void cancel() {
        }
    }

    private static StationWalkState walk(double distance, double speed, long nowMs) {
        return new StationWalkState("fire", new FakeHandle(), speed, distance, nowMs);
    }

    // ==================== anti-wedge timeout (distance/speed + grace) ====================

    @Test
    void timeoutCompletesAsArrived() {
        // ideal travel = 10 / 2.5 * 1000 = 4000ms; grace 2000ms -> threshold 6000ms after start.
        StationWalkState w = walk(10.0, 2.5, 1000L);
        assertFalse(w.timedOut(1000L + 6000L), "exactly at the threshold is not yet timed out");
        assertTrue(w.timedOut(1000L + 6001L), "past distance/speed + grace completes as-arrived (never wedges)");
    }

    @Test
    void nonPositiveSpeedDefaultsTo2_5() {
        StationWalkState w = walk(5.0, 0.0, 0L);
        assertEquals(2.5, w.speedMps, 1e-9, "a non-positive speed falls back to the 2.5 default");
    }

    @Test
    void negativeEstimatedDistanceClampsToZero() {
        StationWalkState w = walk(-3.0, 2.5, 0L);
        assertEquals(0.0, w.estimatedDistance, 1e-9);
        // distance 0 -> ideal 0, so a self-walk times out purely on the 2s grace.
        assertFalse(w.timedOut(2000L));
        assertTrue(w.timedOut(2001L));
    }

    // ==================== handle-state -> step-done mapping (StationStepDecisions.walkStepDone) ====================

    @Test
    void walking_isNotDone_untilTimedOut() {
        assertFalse(StationStepDecisions.walkStepDone(WalkHandle.State.WALKING, false),
                "still walking, not timed out -> keep driving");
        assertTrue(StationStepDecisions.walkStepDone(WalkHandle.State.WALKING, true),
                "still walking but past the anti-wedge grace -> complete the step");
    }

    @Test
    void arrivedStuckFailed_allCompleteTheStep_neverWedge() {
        assertTrue(StationStepDecisions.walkStepDone(WalkHandle.State.ARRIVED, false));
        // STUCK / FAILED complete-as-arrived (mirrors the pre-seam gone-puppet + timeout behavior),
        // so a stuck NPC or a lost Holder ends the walk gracefully rather than suspending forever.
        assertTrue(StationStepDecisions.walkStepDone(WalkHandle.State.STUCK, false));
        assertTrue(StationStepDecisions.walkStepDone(WalkHandle.State.FAILED, false));
    }

    // ==================== Timeout-path walking-flag clear (MIN-2, arc-close) ====================

    /** Records every {@link #cancel()} call; a {@link WalkHandle} fake for {@code completeTimedOutWalk}. */
    private static final class CancelTrackingHandle implements WalkHandle {
        int cancelCalls;

        @Override
        @Nonnull
        public State poll(@Nonnull ComponentAccessor<EntityStore> accessor, double dtMs) {
            return State.WALKING;
        }

        @Override
        @Nonnull
        public State state() {
            return State.WALKING;
        }

        @Override
        public void cancel() {
            cancelCalls++;
        }
    }

    @Test
    void completeTimedOutWalk_timedOut_cancelsAndClearsTheWalkingFlag() {
        // MIN-2 (arc-close): cancel() alone (decision 55) never flips the puppet's walking
        // movement-state off - StationStepHandlers.advanceWalk must ALSO clear it on the timeout
        // exit, the same in-place-flip safety the arrival path already relies on.
        CancelTrackingHandle handle = new CancelTrackingHandle();
        List<Ref<EntityStore>> cleared = new ArrayList<>();
        Ref<EntityStore> performerRef = null; // a null ref exercises the no-live-server fixture path

        StationStepHandlers.completeTimedOutWalk(handle, performerRef, true, cleared::add);

        assertEquals(1, handle.cancelCalls, "the timeout branch still cancels the handle");
        assertEquals(1, cleared.size(), "the timeout branch clears the walking flag exactly once");
        assertEquals(performerRef, cleared.get(0), "the SAME performer ref the caller resolved is threaded to the clearer");
    }

    @Test
    void completeTimedOutWalk_notTimedOut_neverCancelsOrClears() {
        // Still WALKING (no timeout) or a terminal ARRIVED/STUCK/FAILED reached via poll: neither
        // needs this timeout-only cleanup (ARRIVED already clears in-place inside the handle's own
        // poll, per HolderWalkHandle).
        CancelTrackingHandle handle = new CancelTrackingHandle();
        List<Ref<EntityStore>> cleared = new ArrayList<>();

        StationStepHandlers.completeTimedOutWalk(handle, null, false, cleared::add);

        assertEquals(0, handle.cancelCalls, "no timeout - the handle is left driving, never cancelled here");
        assertTrue(cleared.isEmpty(), "no timeout - the walking flag is never touched by this cleanup");
    }
}
