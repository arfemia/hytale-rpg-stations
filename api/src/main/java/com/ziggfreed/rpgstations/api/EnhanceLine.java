package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

/**
 * ONE display line of what an enhancement stamp actually applied: an opaque stat id, the
 * whole-point value written, and a fully-styled
 * client-resolved {@code label} the PROVIDER composed. RpgStations renders the label VERBATIM -
 * stat vocabulary, wording, and color all belong to the registered {@link EnhanceStamper}, never
 * to RpgStations - the engine reports the outcome without ever learning what the stats mean.
 *
 * <p>{@link #statId}/{@link #points} mirror the {@link StatRoll} the stamper received, so a
 * consumer that wants to react programmatically (achievements, an item-diff) reads structured
 * data; {@link #label} is the already-localized, already-colored line the session summary paints.
 */
public record EnhanceLine(@Nonnull String statId, int points, @Nonnull Message label) {
}
