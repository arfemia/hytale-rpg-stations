package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.EnvironmentBreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.cast.WorldEvictors;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The "block broken" custody path (design section 9.4): when a station block carrying placed-input
 * custody is broken - with or without an active work session, since a player can place input then
 * walk away before ever pressing F again - the block's stash is removed, its items drop at the
 * block once, and its display prop despawns, mirroring the design's "block broken (drop at block
 * once, state reset)" bullet. A session-tied claim is already handled by {@link StationService}'s
 * {@code stop()} return path (its heartbeat's block-gone check notices the SAME break next tick
 * and stops the session, whose custody return then no-ops on an already-removed stash - no double
 * drop); this system is what covers the no-session case that path can never reach.
 *
 * <p>TWO registrations cover the two ways the engine breaks a block. This class handles the
 * player's own {@link BreakBlockEvent}; the nested {@link Environment} sibling handles
 * {@link EnvironmentBreakBlockEvent}, which the engine fires INSTEAD (not in addition) for a break
 * with no instigating entity - fire consuming a flammable block, an unattributed explosion - so an
 * explosion can never leave a stash standing under a destroyed block. Both funnel into the SAME
 * {@link StationService#onCustodyBlockBroken} with the same drop-once semantics; the environment
 * route simply has no player to attribute.
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
        String blockKey = StationAnchors.blockKey(worldUuid.toString(), pos.x, pos.y, pos.z);
        StationService.getInstance().onCustodyBlockBroken(store, commandBuffer, blockKey, pos.x, pos.y, pos.z);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    /**
     * The environment-break sibling: a world-level system (no actor entity exists to query), fired
     * by the engine for fire/physics/unattributed-explosion breaks INSTEAD of
     * {@link BreakBlockEvent}. The event is not cancellable - the simulation has already removed
     * the block - so this only cleans up: the same {@link StationService#onCustodyBlockBroken}
     * funnel, the same drop-once semantics, no player attribution.
     */
    public static final class Environment extends WorldEventSystem<EntityStore, EnvironmentBreakBlockEvent> {

        public Environment() {
            super(EnvironmentBreakBlockEvent.class);
        }

        @Override
        public void handle(@Nonnull final Store<EntityStore> store,
                @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                @Nonnull final EnvironmentBreakBlockEvent event) {
            World world;
            try {
                world = WorldEvictors.worldOf(store);
            } catch (Throwable t) {
                Log.fine("STATION environment break could not resolve a world: " + t.getMessage());
                return;
            }
            var worldUuid = world.getWorldConfig().getUuid();
            if (worldUuid == null) {
                return;
            }
            var pos = event.getTargetBlock();
            String blockKey = StationAnchors.blockKey(worldUuid.toString(), pos.x(), pos.y(), pos.z());
            StationService.getInstance().onCustodyBlockBroken(store, commandBuffer, blockKey,
                    pos.x(), pos.y(), pos.z());
        }
    }
}
