package com.ziggfreed.rpgstations.api.impl;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.FactorContext;
import com.ziggfreed.rpgstations.api.FactorRegistry;
import com.ziggfreed.rpgstations.api.StationFactorProvider;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The concrete {@link FactorRegistry} the RpgStations engine dogfoods for its OWN {@code
 * rpgstations:} built-ins (design section 3.2: "RpgStations registers its own built-in factors
 * through the SAME registry") and exposes to third parties via {@link
 * com.ziggfreed.rpgstations.api.RpgStationsApi#get()}{@code .factors()}. Leg 4 adopts this in
 * place of the leg-3 stand-in {@code loot.StationFactorRegistry} (deleted this leg).
 *
 * <p>Engine-internal readers ({@code loot.FactorSnapshot}, {@code
 * station.StationService}'s Requires gate, {@code station.StationValidator}'s known-factor
 * check) call {@link #resolve}/{@link #isKnown} directly against THIS singleton rather than
 * through the narrow {@code register}-only public interface - those two reads are an
 * engine-internal extension of the frozen api contract, not part of it.
 */
public final class FactorRegistryImpl implements FactorRegistry {

    private static final FactorRegistryImpl INSTANCE = new FactorRegistryImpl();

    private final ConcurrentHashMap<String, StationFactorProvider> providers = new ConcurrentHashMap<>();

    private FactorRegistryImpl() {
    }

    @Nonnull
    public static FactorRegistryImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(@Nonnull String factorId, @Nonnull StationFactorProvider provider) {
        providers.put(factorId.toLowerCase(Locale.ROOT), provider);
    }

    /**
     * Register RpgStations' own built-in {@code rpgstations:} factors ({@code
     * session_seconds}/{@code cycle_count}/{@code tool_power}/{@code tool_durability_percent}) -
     * called once from {@code RpgStationsPlugin#setup()}. Idempotent (re-registering is harmless).
     */
    public void registerBuiltins() {
        register("rpgstations:session_seconds", (ctx, param) -> (double) ctx.sessionSeconds());
        register("rpgstations:cycle_count", (ctx, param) -> (double) ctx.cycleIndex());
        register("rpgstations:tool_power", (ctx, param) -> ctx.toolPower());
        register("rpgstations:tool_durability_percent", (ctx, param) -> ctx.toolDurabilityPercent());
    }

    /**
     * Resolve {@code factorId} against {@code ctx}; {@code null} when {@code factorId} is
     * blank, unregistered, or the provider threw (logged at FINE, never propagated - a bad
     * factor provider must not crash a loot roll or a station gate check).
     */
    @Nullable
    public Double resolve(@Nullable String factorId, @Nullable String param, @Nonnull FactorContext ctx) {
        if (factorId == null || factorId.isBlank()) {
            return null;
        }
        StationFactorProvider provider = providers.get(factorId.toLowerCase(Locale.ROOT));
        if (provider == null) {
            return null;
        }
        try {
            return provider.resolve(ctx, param);
        } catch (Throwable t) {
            Log.fine("STATION factor provider '" + factorId + "' threw: " + t.getMessage());
            return null;
        }
    }

    /** True when a provider is registered for {@code factorId} (the validator's known-factor check). */
    public boolean isKnown(@Nullable String factorId) {
        return factorId != null && !factorId.isBlank() && providers.containsKey(factorId.toLowerCase(Locale.ROOT));
    }
}
