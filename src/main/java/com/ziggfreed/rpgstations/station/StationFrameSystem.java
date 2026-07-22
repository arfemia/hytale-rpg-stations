package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.cast.AbstractWorldFrameSystem;

/**
 * Drains {@link StationService}'s per-world session queue once per world per frame. New in RPG
 * Stations (the MMO's own {@code AbilityDotTickingSystem} drained the (then-MMO-owned)
 * station service inline alongside its ability tick services; RpgStations owns its OWN
 * concrete {@link AbstractWorldFrameSystem} subclass instead, since the ECS system registry is
 * class-keyed - a second consumer of the base class needs its own class, not a shared
 * registration - design section 4.1's {@code event/AbilityDotTickingSystem} row).
 */
public final class StationFrameSystem extends AbstractWorldFrameSystem {

    public StationFrameSystem() {
        StationService.getInstance().attachDrainer();
    }

    @Override
    protected void drainFrame(@Nonnull World world, @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        StationService.getInstance().tickFrameOnce(world, store, commandBuffer);
    }
}
