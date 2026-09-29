package com.ziggfreed.rpgstations.progression;

import javax.annotation.Nonnull;

import com.ziggfreed.common.progress.runtime.MomentPayload;
import com.ziggfreed.rpgstations.api.event.StationInputConsumedEvent;

/**
 * What rides with a {@code STATION_INPUT} moment beyond the item id it targets and the station id
 * it is qualified by: the api input event itself and the ONE consumed stack this moment counts (an
 * event carrying several stacks fires several moments), so a listener keying per action, per
 * socket, per quality or per item level reads those off the event instead of re-deriving them.
 * Read on the world thread, inside the dispatch that produced it.
 *
 * @param event    the consumption batch this stack was taken in
 * @param consumed the immutable record of the stack this moment counts
 */
public record StationInputPayload(@Nonnull StationInputConsumedEvent event,
        @Nonnull StationInputConsumedEvent.Consumed consumed) implements MomentPayload {
}
