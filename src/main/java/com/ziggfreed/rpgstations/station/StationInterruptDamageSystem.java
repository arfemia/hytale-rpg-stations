package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Interrupts a live station work session when its player takes damage. Ported verbatim from
 * the MMO's {@code event.StationInterruptDamageSystem} (RPG Stations extraction leg 2), moved
 * into {@code station/} per the design's package layout (section 4.2) and {@code SafeLog}
 * severed to RpgStations' own {@code util.Log}.
 *
 * <p>Runs in the <b>Inspect</b> damage group: it only READS the final damage and calls
 * {@code StationService.onDamage} - it never mutates the damage or adds a component, so it
 * never violates the no-components-in-the-damage-pipeline rule.
 */
public final class StationInterruptDamageSystem extends DamageEventSystem {

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        try {
            if (damage.isCancelled() || damage.getAmount() <= 0) {
                return;
            }
            if (StationService.getInstance().activeCount() == 0) {
                return;
            }
            Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
            StationService.getInstance().onDamage(victimRef, store, commandBuffer);
        } catch (Exception e) {
            Log.warn("StationInterruptDamageSystem error: " + e.getMessage());
        }
    }
}
