package com.ziggfreed.rpgstations.progression;

import javax.annotation.Nonnull;

import com.ziggfreed.common.progress.runtime.MomentPayload;
import com.ziggfreed.rpgstations.api.event.StationCycleCompletedEvent;

/**
 * What rides with a {@code WORK_STATION} moment beyond the station id the moment targets: the api
 * cycle event itself, exactly as {@link StationProgressProducers} saw it, so a listener keying
 * per action, per cycle index or per contribution channel reads those off the event instead of
 * re-deriving them. Read on the world thread, inside the dispatch that produced it.
 *
 * @param event the completed real cycle this moment counts
 */
public record StationWorkPayload(@Nonnull StationCycleCompletedEvent event) implements MomentPayload {
}
