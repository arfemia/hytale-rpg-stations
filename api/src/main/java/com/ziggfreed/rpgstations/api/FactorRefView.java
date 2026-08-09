package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One factor reference a {@link ValidationHook} can see inside a {@link RollView} (design section
 * 3.2): the same {@code {Factor, Param?, Weight?}} triple the asset authored, flattened out of
 * whichever of {@code Conditions} / {@code Chance.Factors} / {@code Ladder.Factors} it came
 * from. It is a plain value snapshot - nothing here is live, so a hook may retain it.
 *
 * <p>{@link #weight} is the EFFECTIVE weight (reader-default 1.0 already applied), so a hook that
 * reasons about double-counting can compare contributions without re-deriving defaults.
 *
 * @param factor the namespaced factor id as authored (never blank)
 * @param param  the optional provider-interpreted argument as authored; null when unauthored
 * @param weight the effective weight this reference contributes with
 */
public record FactorRefView(@Nonnull String factor, @Nullable String param, double weight) {
}
