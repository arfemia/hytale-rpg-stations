package com.ziggfreed.rpgstations.api.event;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.api.StationContribution;

/**
 * Fired synchronously on the shared Hytale event bus after every completed work cycle (real OR
 * idle), on the world thread from {@code StationService}'s cycle path - AFTER the loot roll for
 * a real cycle. An idle cycle fires this too, with {@link #idle()} true and
 * {@link #contributions()} already scaled by the station's {@code Work.Idle.Fraction} (idle practice
 * produces nothing, matching the fractional-output semantics of practising without materials).
 *
 * <p><b>{@link #commandBuffer()} is GUARANTEED non-null on every firing path.</b> The engine's
 * only two cycle-firing call sites - the real-cycle path and the
 * idle-cycle path - both run inside the per-world frame drain ({@code
 * StationService.tickFrameOnce}), which always holds a live {@code CommandBuffer} for that tick;
 * the fire site enforces this with a {@code @Nonnull} parameter, so a listener MAY dispatch its
 * own command-buffer-driven work (e.g. firing a {@code ProgressEvents.fire}) directly off it,
 * with no live-{@code Store} fallback needed.
 *
 * <p><b>Two contribution lists, deliberately separate:</b> {@link #contributions()} carries the
 * station's own {@code Work.PerCycleContributions} (which an idle cycle already pre-scaled by the
 * idle fraction); {@link #oneShotContributions()} carries {@code Roll.Grants.Contributions} find
 * grants, which bypass that scaling and must be posted verbatim. A listener MUST filter both lists
 * by {@link StationContribution#channel()} - a channel it did not declare belongs to someone else.
 *
 * <p><b>Amounts arrive ALREADY SCALED; grant them verbatim.</b> The engine applies the action's own
 * {@code ContributionScale} ladder before dispatch and reports the resolved multiplier on
 * {@link #contributionScale()} for DISPLAY only. That removes the footgun in both directions: a
 * listener that forgets to multiply cannot under-award, and one that multiplies again cannot
 * over-award.
 *
 * <p><b>Plain data</b> ({@link #playerId()}, {@link #sessionId()}, {@link #stationId()},
 * {@link #actionId()}, {@link #cycleIndex()}, {@link #idle()}, {@link #contributions()},
 * {@link #oneShotContributions()}) is always safe to retain. <b>Live
 * world-thread context</b> ({@link #store()}, {@link #commandBuffer()}, {@link #playerRef()}) is
 * valid ONLY synchronously during dispatch; a listener that defers work must capture the plain
 * fields and re-resolve.
 */
public final class StationCycleCompletedEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final CommandBuffer<EntityStore> commandBuffer;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final UUID playerId;
    @Nonnull private final UUID sessionId;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    private final int cycleIndex;
    private final boolean idle;
    @Nonnull private final List<StationContribution> contributions;
    @Nonnull private final List<StationContribution> oneShotContributions;
    private final double contributionScale;

    public StationCycleCompletedEvent(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            int cycleIndex, boolean idle, @Nonnull List<StationContribution> contributions,
            @Nonnull List<StationContribution> oneShotContributions, double contributionScale) {
        this.store = store;
        this.commandBuffer = commandBuffer;
        this.playerRef = playerRef;
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.actionId = actionId;
        this.cycleIndex = cycleIndex;
        this.idle = idle;
        this.contributions = List.copyOf(contributions);
        this.oneShotContributions = List.copyOf(oneShotContributions);
        this.contributionScale = contributionScale;
    }

    @Nonnull
    public Store<EntityStore> store() {
        return store;
    }

    /** GUARANTEED non-null - see the class javadoc. */
    @Nonnull
    public CommandBuffer<EntityStore> commandBuffer() {
        return commandBuffer;
    }

    @Nonnull
    public PlayerRef playerRef() {
        return playerRef;
    }

    @Nonnull
    public UUID playerId() {
        return playerId;
    }

    @Nonnull
    public UUID sessionId() {
        return sessionId;
    }

    @Nonnull
    public String stationId() {
        return stationId;
    }

    @Nonnull
    public String actionId() {
        return actionId;
    }

    /** 1-based cycle count for this session (real + idle cycles both increment it). */
    public int cycleIndex() {
        return cycleIndex;
    }

    public boolean idle() {
        return idle;
    }

    /**
     * The action's {@code Work.PerCycleContributions} for this cycle, forwarded ALREADY SCALED -
     * grant each amount verbatim. The engine has applied both multipliers before dispatch: an idle
     * cycle's {@code Work.Idle.Fraction}, and the action's {@code ContributionScale} ladder (which
     * rides along on {@link #contributionScale()} for DISPLAY only, never to re-apply).
     */
    @Nonnull
    public List<StationContribution> contributions() {
        return contributions;
    }

    /**
     * One-shot contributions posted during this cycle by a {@code Roll.Grants.Contributions} entry
     * (a conditional-lootable find), SEPARATE because they are DELIBERATELY UNSCALED: post each at
     * its stated amount; the engine never pre-scales them by the idle fraction. Empty on most
     * cycles.
     */
    @Nonnull
    public List<StationContribution> oneShotContributions() {
        return oneShotContributions;
    }

    /**
     * The multiplier the engine ALREADY applied to every {@link #contributions()} amount (the
     * action's own {@code ContributionScale} ladder, resolved against this cycle's factor values;
     * {@code 1.0} when none is authored or no floor was reached).
     *
     * <p><b>DISPLAY ONLY.</b> Grant {@link #contributions()} verbatim - the amounts are already
     * scaled. Multiplying by this number again would double-count; ignoring it cannot under-award.
     * It is exposed purely so a listener can SHOW why a cycle was worth what it was worth (a "x2.5
     * tool" line in a summary). {@link #oneShotContributions()} is never touched by it.
     */
    public double contributionScale() {
        return contributionScale;
    }
}
