package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * ONE committed consumption, session-free: everything the ONE input-consumed hook
 * ({@code StationService#onInputConsumed}) receives, whichever path consumed. The worker, the world
 * and the block the consumed pile stood at, the station and action ids, and the consumed stacks,
 * each with the custody socket it left ({@link ConsumedInput}; the stack's own id, count and item
 * carry its quality and item level).
 *
 * <p>The worker is NULLABLE, and null in exactly one case: an unattended settle, where a station
 * consumes with nobody engaged ({@link #unattended}). An attended session always names its worker
 * (the session paths build it through {@code StationService#attendedConsumption}).
 *
 * @param playerRef the worker's player handle, null on an unattended settle
 * @param worker    the worker's entity ref, null on an unattended settle
 * @param workerId  the worker's uuid, null on an unattended settle
 * @param worldUuid the world the station stands in
 * @param blockX    the block the consumed pile stood at (the station's own block for an
 *                  inventory-sourced consume, the {@code At} anchor's block for a remote pile)
 * @param blockY    as {@code blockX}
 * @param blockZ    as {@code blockX}
 * @param stationId the station id
 * @param actionId  the action id the consumption ran under
 * @param consumed  the consumed stacks, never empty for a reported consumption
 */
record InputConsumption(@Nullable PlayerRef playerRef, @Nullable Ref<EntityStore> worker,
        @Nullable UUID workerId, @Nonnull UUID worldUuid, int blockX, int blockY, int blockZ,
        @Nonnull String stationId, @Nonnull String actionId, @Nonnull List<ConsumedInput> consumed) {

    /** A consumption an unattended settle committed: no worker, the claim's own block. */
    @Nonnull
    static InputConsumption unattended(@Nonnull UUID worldUuid, int blockX, int blockY, int blockZ,
            @Nonnull String stationId, @Nonnull String actionId, @Nonnull List<ConsumedInput> consumed) {
        return new InputConsumption(null, null, null, worldUuid, blockX, blockY, blockZ,
                stationId, actionId, consumed);
    }

    /** True when a worker consumed (an attended session); false on an unattended settle. */
    boolean hasWorker() {
        return playerRef != null && worker != null && workerId != null;
    }
}
