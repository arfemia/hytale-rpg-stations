package com.ziggfreed.rpgstations.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A numeric-factor resolver registered under one factor id (design section 3.2). */
@FunctionalInterface
public interface StationFactorProvider {

    /**
     * Resolve a numeric factor for {@code ctx}; world thread, synchronous, must not retain
     * {@code ctx} past this call. A throwing provider is caught by the registry and treated as
     * unresolvable (fail-closed for a gating {@code Condition}, zero for a summed value).
     */
    double resolve(@Nonnull FactorContext ctx, @Nullable String param);
}
