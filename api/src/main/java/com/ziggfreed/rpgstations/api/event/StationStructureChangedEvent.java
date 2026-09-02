package com.ziggfreed.rpgstations.api.event;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Fired synchronously on the shared Hytale event bus when a multiblock structure pattern changes
 * standing state at its anchor: a player-built arrangement COMPLETED and activated into a working
 * station ({@link #activated()} {@code true}), or a standing build was broken and its anchor
 * reverted ({@code false}). Fired on the world thread, AFTER the anchor swap and the pattern
 * bookkeeping have committed.
 *
 * <p>{@link #blockItemId()} is the block item id NOW STANDING at the anchor: the pattern's
 * activation block after an activation (the anchor cell's own block where the pattern authors no
 * swap), the revert block after a revert. {@link #actor()} is the player whose placement
 * completed the shape on an activation, or the breaker on a player-break revert; it is
 * {@code null} on an environment-break revert (an explosion or physics collapse has no acting
 * player). {@link #actorId()} is the same actor as a retainable uuid, null under the same
 * condition.
 *
 * <p><b>Plain data</b> ({@link #worldUuid()}, {@link #anchorX()}/{@link #anchorY()}/
 * {@link #anchorZ()}, {@link #patternId()}, {@link #blockItemId()}, {@link #activated()},
 * {@link #actorId()}) is always safe to retain. <b>Live world-thread context</b>
 * ({@link #actor()}) is valid ONLY synchronously during dispatch; a listener that defers work
 * must capture the plain fields and re-resolve.
 */
public final class StationStructureChangedEvent implements IEvent<Void> {

    @Nonnull private final UUID worldUuid;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    @Nonnull private final String patternId;
    @Nonnull private final String blockItemId;
    private final boolean activated;
    @Nullable private final UUID actorId;
    @Nullable private final Ref<EntityStore> actor;

    public StationStructureChangedEvent(@Nonnull UUID worldUuid, int anchorX, int anchorY, int anchorZ,
            @Nonnull String patternId, @Nonnull String blockItemId, boolean activated,
            @Nullable UUID actorId, @Nullable Ref<EntityStore> actor) {
        this.worldUuid = worldUuid;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.patternId = patternId;
        this.blockItemId = blockItemId;
        this.activated = activated;
        this.actorId = actorId;
        this.actor = actor;
    }

    @Nonnull
    public UUID worldUuid() {
        return worldUuid;
    }

    /** The anchor block's position - the one cell of the pattern that becomes (or stops being) the station. */
    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    /** The structure pattern's (lowercased) id. */
    @Nonnull
    public String patternId() {
        return patternId;
    }

    /** The block item id now standing at the anchor (activation block, or the revert block). */
    @Nonnull
    public String blockItemId() {
        return blockItemId;
    }

    /** {@code true} = a completed build activated; {@code false} = a standing build reverted. */
    public boolean activated() {
        return activated;
    }

    /**
     * The acting player's uuid (the placer on activation, the breaker on a player-break revert),
     * or {@code null} on an environment-break revert. Plain data - safe to retain.
     */
    @Nullable
    public UUID actorId() {
        return actorId;
    }

    /**
     * The acting player's entity ref, under the same rules as {@link #actorId()} - live
     * world-thread context, dispatch-synchronous only.
     */
    @Nullable
    public Ref<EntityStore> actor() {
        return actor;
    }
}
