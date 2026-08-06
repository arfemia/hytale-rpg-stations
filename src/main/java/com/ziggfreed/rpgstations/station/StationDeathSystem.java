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
 * Ends any live station session BEFORE the respawn screen (design section 4.2 - this mod owns
 * its own teardown hooks). Camera reset must beat the respawn screen (the stranded-camera
 * window); a no-op when no session is live for the victim. Extends the engine's own
 * {@code DeathSystems.OnDeathSystem}, the first-party shape for this moment.
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
            StationService.getInstance().stopForRef(victimRef, store, StationService.StopReason.DIED, commandBuffer);
        } catch (Throwable t) {
            Log.warn("StationDeathSystem error: " + t.getMessage());
        }
    }
}
