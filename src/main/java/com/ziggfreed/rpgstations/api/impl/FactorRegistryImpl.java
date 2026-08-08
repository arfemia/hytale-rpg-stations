package com.ziggfreed.rpgstations.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * Register the built-in factors - called once from {@code RpgStationsPlugin#setup()},
     * idempotent (re-registering is harmless).
     *
     * <p><b>A factor's namespace names the VOCABULARY's owner, not whoever registered the
     * provider.</b> This engine registers all of the below, but only some of the numbers are its
     * own idea:
     * <ul>
     *   <li>{@code rpgstations:session_seconds} / {@code rpgstations:cycle_count} - concepts that
     *       exist only because a station SESSION exists. Genuinely this mod's vocabulary.</li>
     *   <li>{@code hytale:tool_power} / {@code hytale:tool_quality} /
     *       {@code hytale:tool_item_level} / {@code hytale:tool_durability_percent} - straight reads
     *       of native item data ({@code ItemToolSpec} power for a native {@code GatherType},
     *       {@code ItemQuality.QualityValue}, {@code ItemLevel}, durability), meaningful with no
     *       station involved at all. {@code tool_power} takes the gather type as its {@code Param}
     *       exactly as {@code stat} takes a stat id, so the addressing is explicit and the factor is
     *       portable; omitting {@code Param} falls back to the station's own effective gather type,
     *       which is the common case and why no authored content changed when the addressed form
     *       arrived.</li>
     *   <li>{@code hytale:stat} - reads ANY registered native {@code EntityStatType}'s effective
     *       value ({@code {"Factor":"hytale:stat","Param":"<StatId>"}}), so a mod that writes native
     *       stats participates in loot/gate/cap formulas with zero bridge code (design section 4.1,
     *       gate decision 37).</li>
     * </ul>
     * The practical payoff: a `hytale:`-namespaced id means the same thing to every mod that reads
     * native data, so two mods converging on it is agreement rather than a collision, and an author
     * can tell from the id alone whether a factor is portable or station-specific.
     */
    public void registerBuiltins() {
        register("rpgstations:session_seconds", (ctx, param) -> (double) ctx.sessionSeconds());
        register("rpgstations:cycle_count", (ctx, param) -> (double) ctx.cycleIndex());
        register("hytale:tool_power", (ctx, param) -> ctx.toolPowerFor(param));
        register("hytale:tool_durability_percent", (ctx, param) -> ctx.toolDurabilityPercent());
        register("hytale:tool_quality", (ctx, param) -> ctx.toolQuality());
        register("hytale:tool_item_level", (ctx, param) -> ctx.toolItemLevel());
        register("hytale:stat", new StatChannelFactorProvider());
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

    /**
     * Every currently-registered factor id, lowercased and sorted - the third engine-internal
     * extension read beside {@link #resolve}/{@link #isKnown} (never part of the frozen
     * register-only api contract). Backs the {@code rpgstations:factors} Asset-Editor dropdown
     * dataset, which is why it is a snapshot: a mod registering a factor later simply widens the
     * next request's answer.
     */
    @Nonnull
    public List<String> registeredIds() {
        List<String> ids = new ArrayList<>(providers.keySet());
        Collections.sort(ids);
        return ids;
    }
}
