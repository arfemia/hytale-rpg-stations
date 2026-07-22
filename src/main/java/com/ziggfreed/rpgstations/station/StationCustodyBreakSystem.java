package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The "block broken" custody auto-return path (design section 9.4): when a station block
 * carrying a LIVE placed-input claim is broken - with or without an active work session, since a
 * player can place input then walk away before ever pressing F again - the claim's items drop at
 * the block once and its bookkeeping clears, mirroring the design's "block broken (drop at block
 * once, state reset)" bullet. A session-tied claim is already handled by
 * {@link StationService}'s {@code stop()} return path (its heartbeat's block-gone check notices
 * the SAME break next tick and stops the session, whose custody return then no-ops on an
 * already-cleared claim - no double drop); this system is what covers the no-session case that
 * path can never reach.
 */
public final class StationCustodyBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public StationCustodyBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final BreakBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        var worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            return;
        }
        var pos = event.getTargetBlock();
        String blockKey = worldUuid + ":" + pos.x + ":" + pos.y + ":" + pos.z;
        StationService.getInstance().onCustodyBlockBroken(store, commandBuffer, blockKey, pos.x, pos.y, pos.z);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
