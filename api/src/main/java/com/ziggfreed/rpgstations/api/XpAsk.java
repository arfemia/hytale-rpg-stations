package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;

/**
 * One station's forwarded per-cycle progression ask (design section 3.1). The station ENGINE
 * never interprets {@code skillId} itself - it forwards the asset's authored {@code Work.Xp}
 * declarations verbatim, plus (separately, via {@code StationCycleCompletedEvent.toolMultiplier})
 * the resolved tool-power multiplier. Whatever progression mod listens decides what an ask means.
 *
 * @param skillId      the station-authored skill id (opaque to the engine)
 * @param perCycleBase the per-cycle XP base; for an idle cycle already scaled by {@code Work.Idle.XpFraction}
 */
public record XpAsk(@Nonnull String skillId, double perCycleBase) {
}
