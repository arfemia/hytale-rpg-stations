package com.ziggfreed.rpgstations.loot;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.util.Log;

/**
 * The INTERNAL numeric-factor registry the loot layer's {@link RollEvaluator}/{@link
 * FactorSnapshot} resolve over (leg 3 stand-in for the design section 3.2 api {@code
 * FactorRegistry} - see {@link FactorContext}'s javadoc for the leg-4 adoption note).
 *
 * <p>Registers RpgStations' own built-in factors under the {@code rpgstations:} namespace
 * ({@link #registerBuiltins()}, called once from {@code RpgStationsPlugin#setup()}):
 * {@code rpgstations:session_seconds}, {@code rpgstations:cycle_count}, {@code
 * rpgstations:tool_power}, {@code rpgstations:tool_durability_percent} - dogfooding the SAME
 * registry the future api will expose to third parties (design section 3.2: "RpgStations
 * registers its own built-in factors through the SAME registry").
 *
 * <p>An unregistered factor id resolves to {@code null} (fail-closed for a gating {@code
 * Condition}, zero for a summed {@code Chance.AddFactors}/{@code Ladder.Value} entry - see
 * {@link RollEvaluator}); a throwing provider is caught and treated the same as unregistered.
 */
public final class StationFactorRegistry {

    @FunctionalInterface
    public interface StationFactorProvider {
        /** Resolve a numeric factor for {@code ctx}; must not retain {@code ctx} past this call. */
        double resolve(@Nonnull FactorContext ctx, @Nullable String param);
    }

    private static final ConcurrentHashMap<String, StationFactorProvider> PROVIDERS = new ConcurrentHashMap<>();

    private StationFactorRegistry() {
    }

    /** Register a provider under {@code factorId} (lowercased); last write wins. */
    public static void register(@Nonnull String factorId, @Nonnull StationFactorProvider provider) {
        PROVIDERS.put(factorId.toLowerCase(Locale.ROOT), provider);
    }

    /**
     * Resolve {@code factorId} against {@code ctx}; {@code null} when {@code factorId} is
     * blank, unregistered, or the provider threw (logged at FINE, never propagated - a bad
     * factor provider must not crash a loot roll or a station gate check).
     */
    @Nullable
    public static Double resolve(@Nullable String factorId, @Nullable String param, @Nonnull FactorContext ctx) {
        if (factorId == null || factorId.isBlank()) {
            return null;
        }
        StationFactorProvider provider = PROVIDERS.get(factorId.toLowerCase(Locale.ROOT));
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
    public static boolean isKnown(@Nullable String factorId) {
        return factorId != null && !factorId.isBlank() && PROVIDERS.containsKey(factorId.toLowerCase(Locale.ROOT));
    }

    /** Register RpgStations' own built-in {@code rpgstations:} factors. Idempotent (re-registering is harmless). */
    public static void registerBuiltins() {
        register("rpgstations:session_seconds", (ctx, param) -> (double) ctx.getSessionSeconds());
        register("rpgstations:cycle_count", (ctx, param) -> (double) ctx.getCycleIndex());
        register("rpgstations:tool_power", (ctx, param) -> ctx.getToolPower());
        register("rpgstations:tool_durability_percent", (ctx, param) -> ctx.getToolDurabilityPercent());
    }
}
