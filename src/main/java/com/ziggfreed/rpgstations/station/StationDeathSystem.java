package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Ends any live station session BEFORE the respawn screen (design section 4.2 - RpgStations owns
 * its own teardown hooks; the MMO's pre-extraction equivalent, {@code
 * event.PlayerDeathEventSystem}'s station-stop call, was deleted in the phase-1 leg-5 bridge leg).
 * Camera reset must beat the respawn screen (the stranded-camera window); a no-op when no session
 * is live for the victim. Mirrors the MMO's own {@code DeathSystems.OnDeathSystem} shape (a proven
 * pattern, not invented here).
 */
public class StationDeathSystem extends DeathSystems.OnDeathSystem {

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> victimRef,
            @Nonnull DeathComponent death,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            StationService.getInstance().stopForRef(victimRef, store, StationService.StopReason.DIED);
        } catch (Throwable t) {
            Log.warn("StationDeathSystem error: " + t.getMessage());
        }
    }
}
