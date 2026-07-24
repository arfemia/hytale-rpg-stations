package com.ziggfreed.rpgstations.station;

import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.ziggfreed.common.entity.PlayerPuppetService;

/**
 * The in-flight state of ONE {@code Walk} phase (scope-2 wave 3, design 2.3): the solved waypoint
 * polyline the puppet travels, the parametric arc-length progress already covered, and the
 * timeout/cadence bookkeeping that survives a suspend/resume across ticks (the composite handler
 * re-enters {@link StationStepHandlers.CompositeStepHandler#execute} every frame while
 * {@link StationSession#walkState} is non-null). Held ON the session, never persisted.
 *
 * <p>The walk POLICY (speed, arrival epsilon, timeout grace) lives here; the per-tick MECHANISM
 * (transform write + walk-state flip) is {@code ziggfreed-common}'s
 * {@link PlayerPuppetService#walkTick}/{@link PlayerPuppetService#setWalking}.
 */
final class StationWalkState {

    /** Arrival tolerance in blocks (design 2.3's 0.25m). */
    static final double ARRIVAL_EPSILON = 0.25;

    /** Timeout grace added to the ideal travel time before a walk completes as-arrived (design 2.3's 2s). */
    static final long TIMEOUT_GRACE_MS = 2000L;

    /** The anchor id this walk targets ({@code "self"} or a declared anchor id). */
    @Nonnull final String targetAnchorId;

    /** The solved block-centred feet waypoints from the puppet's start to the anchor column. */
    @Nonnull final List<Vector3d> waypoints;

    /** The total arc-length of {@link #waypoints} (blocks), cached once at solve time. */
    final double pathLength;

    /** The walk speed in blocks/metres per second (design default 2.5). */
    final double speedMps;

    /** When the walk began (epoch millis), for the distance/speed + grace timeout. */
    final long startedAtMs;

    /** The arc-length already travelled; advanced each tick by {@link PlayerPuppetService#walkTick}. */
    double progress;

    /** The last tick's timestamp, for the per-tick {@code dt}. */
    long lastTickMs;

    StationWalkState(@Nonnull String targetAnchorId, @Nonnull List<Vector3d> waypoints, double speedMps,
            long nowMs) {
        this.targetAnchorId = targetAnchorId;
        this.waypoints = waypoints;
        this.pathLength = PlayerPuppetService.pathLength(waypoints);
        this.speedMps = speedMps > 0 ? speedMps : 2.5;
        this.startedAtMs = nowMs;
        this.progress = 0.0;
        this.lastTickMs = nowMs;
    }

    /** True when the puppet has covered the whole path (within {@link #ARRIVAL_EPSILON}). */
    boolean arrived() {
        return PlayerPuppetService.isArrived(progress, pathLength, ARRIVAL_EPSILON);
    }

    /**
     * True when the walk has run longer than its ideal travel time plus {@link #TIMEOUT_GRACE_MS}
     * (design 2.3: "the step completes as if arrived, never wedges the program").
     */
    boolean timedOut(long nowMs) {
        long ideal = speedMps > 0 ? (long) (pathLength / speedMps * 1000.0) : 0L;
        return nowMs - startedAtMs > ideal + TIMEOUT_GRACE_MS;
    }
}
