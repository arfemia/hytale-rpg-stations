package com.ziggfreed.rpgstations.loot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.impl.FactorRegistryImpl;

/**
 * Per-batch memoized factor cache over the api {@link FactorContext} + {@link FactorRegistryImpl}
 * (design section 4.5, leg 4 adoption - the leg-3 stand-in internal {@code FactorContext}/{@code
 * StationFactorRegistry} pair is retired in favor of this): resolves each distinct {@code
 * (factorId, param)} pair AT MOST ONCE per {@link FactorSnapshot} instance, restoring the "one
 * aggregation, many consumers" invariant a Roll's {@code Chance} and {@code Ladder} both need
 * when they reference the SAME factor (e.g. both reading {@code mmoskilltree:station_luck} - one
 * capped, one raw, but off the identical resolved number). One instance per trigger evaluation
 * batch (a real cycle, an idle cycle, or a session-completion roll pass); never shared across
 * batches (the underlying {@link FactorContext} is a per-batch snapshot too).
 */
public final class FactorSnapshot {

    @Nonnull private final FactorContext ctx;
    @Nonnull private final Map<String, Double> cache = new HashMap<>();

    public FactorSnapshot(@Nonnull FactorContext ctx) {
        this.ctx = ctx;
    }

    @Nonnull
    public FactorContext context() {
        return ctx;
    }

    /** Resolve (and cache) {@code factorId}/{@code param}; {@code null} on a blank id or an unresolved factor. */
    @Nullable
    public Double resolve(@Nullable String factorId, @Nullable String param) {
        if (factorId == null || factorId.isBlank()) {
            return null;
        }
        String key = factorId.toLowerCase(Locale.ROOT) + '#' + (param == null ? "" : param);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        Double value = FactorRegistryImpl.getInstance().resolve(factorId, param, ctx);
        cache.put(key, value);
        return value;
    }
}
