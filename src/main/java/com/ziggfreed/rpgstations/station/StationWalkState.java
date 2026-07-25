package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;

import com.ziggfreed.common.entity.performer.WalkHandle;

/**
 * The in-flight state of ONE {@code Walk} phase (scope-2 wave 3, design 2.3): the poll-driven
 * {@link WalkHandle} the active {@code StationPerformer} handed back at {@code walkTo}, plus the
 * anti-wedge timeout bookkeeping that survives a suspend/resume across ticks (the composite handler
 * re-enters {@link StationStepHandlers.CompositeStepHandler#execute} every frame while
 * {@link StationSession#walkState} is non-null). Held ON the session, never persisted.
 *
 * <p><b>Seam route (decision 55, F2):</b> the walk MECHANISM now lives entirely behind the
 * performer seam - the Holder backend re-solves the SAME bounded-A* {@code PuppetNav} path and
 * advances the puppet via {@code PlayerPuppetService.walkTick} under the hood (byte-parity for the
 * PlayerClone puppet), the NpcRole backend drives its native gait toward a marked target - so this
 * class no longer carries the raw waypoints/progress or drives the walk itself; it only holds the
 * {@link #handle} to poll and the walk POLICY (speed, timeout). Arrival is decided by the handle
 * (ARRIVED/STUCK/FAILED); this class keeps ONLY design 2.3's outer anti-wedge timeout
 * ({@link #timedOut}) so a handle that never terminates (a genuinely stuck walk) still completes
 * the step rather than suspending the program forever.
 */
final class StationWalkState {

    /** Timeout grace added to the ideal travel time before a walk completes as-arrived (design 2.3's 2s). */
    static final long TIMEOUT_GRACE_MS = 2000L;

    /** The anchor id this walk targets ({@code "self"} or a declared anchor id). */
    @Nonnull final String targetAnchorId;

    /** The poll-driven walk handle from {@code StationPerformer.walkTo}. */
    @Nonnull final WalkHandle handle;

    /** The walk speed in blocks/metres per second (design default 2.5). */
    final double speedMps;

    /**
     * The straight-line distance (blocks) from the station block to the walk target, the ideal-time
     * basis for {@link #timedOut}. A conservative proxy for the solved path length (which now lives
     * inside the handle): the puppet starts at the station block, so this under-estimates a winding
     * path slightly, which the {@link #TIMEOUT_GRACE_MS} grace absorbs - the timeout is an anti-wedge
     * net, not a precise arrival predictor (the handle owns arrival).
     */
    final double estimatedDistance;

    /** When the walk began (epoch millis), for the distance/speed + grace timeout. */
    final long startedAtMs;

    /** The last tick's timestamp, for the per-tick {@code dt} passed to {@link WalkHandle#poll}. */
    long lastTickMs;

    StationWalkState(@Nonnull String targetAnchorId, @Nonnull WalkHandle handle, double speedMps,
            double estimatedDistance, long nowMs) {
        this.targetAnchorId = targetAnchorId;
        this.handle = handle;
        this.speedMps = speedMps > 0 ? speedMps : 2.5;
        this.estimatedDistance = Math.max(0.0, estimatedDistance);
        this.startedAtMs = nowMs;
        this.lastTickMs = nowMs;
    }

    /**
     * True when the walk has run longer than its ideal travel time plus {@link #TIMEOUT_GRACE_MS}
     * (design 2.3: "the step completes as if arrived, never wedges the program"). Checked by
     * {@code StationStepHandlers.advanceWalk} only while the handle still reports WALKING.
     */
    boolean timedOut(long nowMs) {
        long ideal = speedMps > 0 ? (long) (estimatedDistance / speedMps * 1000.0) : 0L;
        return nowMs - startedAtMs > ideal + TIMEOUT_GRACE_MS;
    }
}
