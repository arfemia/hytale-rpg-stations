package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Feeds the lazy per-block station index (scope-2 wave 3, gate m4, design 2.2): when a player
 * places a block whose item id has already been LEARNED as a station block (any prior interaction
 * with that station type warmed {@code stationBlockItemToId}), index the new block so a later
 * anchor discovery finds it WITHOUT a bounded ring scan. A never-before-interacted station type
 * stays undiscovered-by-place until its first interaction - the honest documented contract
 * ("the engine finds station blocks it has seen"); the bounded ring scan is the last resort for
 * a placed-but-never-interacted anchor.
 *
 * <p>ALSO the multiblock-structure placement feed ({@link StationStructures#onBlockPlaced}): might
 * this placement have completed or advanced an authored pattern? That half pre-filters cheaply on
 * this thread and defers its authoritative walk (and any anchor activation, which does write the
 * world) to {@code world.execute} - the event fires BEFORE the engine writes the placed block, so
 * nothing here reads or writes the world inline. A non-station, non-pattern placement is a cheap
 * no-op. Sibling to {@link StationCustodyBreakSystem} (which handles the break side, removing the
 * index entry and re-checking standing structures).
 */
public final class StationBlockPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public StationBlockPlaceSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final PlaceBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        ItemStack inHand = event.getItemInHand();
        String blockItemId = inHand != null ? inHand.getItemId() : null;
        if (blockItemId == null || blockItemId.isBlank()) {
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
        StationService.getInstance().onStationBlockPlaced(worldUuid, pos.x, pos.y, pos.z, blockItemId);
        // Multiblock structures: might this placement have completed (or advanced) an authored
        // pattern? Pre-filters cheaply here and defers the authoritative walk - the engine writes
        // the placed block AFTER this event. A no-pattern install early-outs on the first check.
        StationStructures.getInstance().onBlockPlaced(store, ref, playerRef, worldUuid,
                pos.x, pos.y, pos.z, blockItemId);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
