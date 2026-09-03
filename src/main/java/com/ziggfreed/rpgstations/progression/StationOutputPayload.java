package com.ziggfreed.rpgstations.progression;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.progress.runtime.MomentPayload;
import com.ziggfreed.rpgstations.api.event.StationOutputProducedEvent;

/**
 * What rides with a {@code STATION_OUTPUT} moment beyond the item id it targets and the station
 * id it is qualified by: the api output event itself and the ONE stack this moment counts (an
 * event carrying several stacks fires several moments), so a listener keying per action, per
 * socket or per landing block reads those off the event instead of re-deriving them. Read on the
 * world thread, inside the dispatch that produced it.
 *
 * @param event the output batch this stack landed in
 * @param stack the immutable copy of the stack this moment counts
 */
public record StationOutputPayload(@Nonnull StationOutputProducedEvent event, @Nonnull ItemStack stack)
        implements MomentPayload {
}
