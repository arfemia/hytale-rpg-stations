package com.ziggfreed.rpgstations.api.event;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.api.StationContribution;

/**
 * Fired synchronously on the shared Hytale event bus when a player GATHERS a custody pile that
 * accrued UNATTENDED work cycles - the payout moment of unattended processing: the station
 * transformed its placed inputs while nobody was engaged, the cycle count accrued on the output
 * pile, and whoever collects that pile is the one paid. Fired on the world thread, AFTER the
 * engine's own item/loot grants for the batch have been applied to the gatherer.
 *
 * <p><b>The gatherer is never null.</b> This event fires only at a live gather (a press-F
 * retrieval, or a stop hand-back, by a present player), so {@link #gatherer()} always names a
 * valid player entity and {@link #gathererId()} their uuid - no listener ever has to handle an
 * absent worker.
 *
 * <p><b>{@link #contributions()} arrives ALREADY SCALED; grant each amount verbatim.</b> The
 * engine has applied the idle rate ({@code Work.Idle.Fraction} - unattended cycles pay at
 * idle-practice value), the action's {@code ContributionScale} ladder resolved against the
 * GATHERER, and the {@link #settledCycles()} multiplier before dispatch. A listener MUST filter
 * the list by {@link StationContribution#channel()} - a channel it did not declare belongs to
 * someone else.
 *
 * <p><b>Plain data</b> ({@link #gathererId()}, {@link #worldUuid()}, {@link #blockX()}/
 * {@link #blockY()}/{@link #blockZ()}, {@link #stationId()}, {@link #actionId()},
 * {@link #settledCycles()}, {@link #contributions()}) is always safe to retain. <b>Live
 * world-thread context</b> ({@link #store()}, {@link #playerRef()}, {@link #gatherer()}) is valid
 * ONLY synchronously during dispatch; a listener that defers work must capture the plain fields
 * and re-resolve.
 */
public final class StationUnattendedGatheredEvent implements IEvent<Void> {

    @Nonnull private final Store<EntityStore> store;
    @Nonnull private final PlayerRef playerRef;
    @Nonnull private final Ref<EntityStore> gatherer;
    @Nonnull private final UUID gathererId;
    @Nonnull private final UUID worldUuid;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    @Nonnull private final String stationId;
    @Nonnull private final String actionId;
    private final int settledCycles;
    @Nonnull private final List<StationContribution> contributions;

    public StationUnattendedGatheredEvent(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> gatherer, @Nonnull UUID gathererId, @Nonnull UUID worldUuid,
            int blockX, int blockY, int blockZ, @Nonnull String stationId, @Nonnull String actionId,
            int settledCycles, @Nonnull List<StationContribution> contributions) {
        this.store = store;
        this.playerRef = playerRef;
        this.gatherer = gatherer;
        this.gathererId = gathererId;
        this.worldUuid = worldUuid;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.stationId = stationId;
        this.actionId = actionId;
        this.settledCycles = settledCycles;
        this.contributions = List.copyOf(contributions);
    }

    @Nonnull
    public Store<EntityStore> store() {
        return store;
    }

    /** The gathering player's ref handle - live world-thread context, dispatch-synchronous only. */
    @Nonnull
    public PlayerRef playerRef() {
        return playerRef;
    }

    /** The gathering player's entity ref - NEVER null (this event fires only at a live gather). */
    @Nonnull
    public Ref<EntityStore> gatherer() {
        return gatherer;
    }

    @Nonnull
    public UUID gathererId() {
        return gathererId;
    }

    @Nonnull
    public UUID worldUuid() {
        return worldUuid;
    }

    /** The gathered station block's position (where the unattended work happened). */
    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    @Nonnull
    public String stationId() {
        return stationId;
    }

    @Nonnull
    public String actionId() {
        return actionId;
    }

    /** How many accrued unattended cycles this gather paid out (already ceiling-capped). */
    public int settledCycles() {
        return settledCycles;
    }

    /**
     * The batch's contribution posts, ALREADY SCALED (idle rate x gatherer-resolved
     * {@code ContributionScale} x {@link #settledCycles()}) - grant each amount verbatim, filtered
     * by {@link StationContribution#channel()}.
     */
    @Nonnull
    public List<StationContribution> contributions() {
        return contributions;
    }
}
