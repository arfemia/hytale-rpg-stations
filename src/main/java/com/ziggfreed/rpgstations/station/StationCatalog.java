package com.ziggfreed.rpgstations.station;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The RUNTIME AUTHORITY for station content. {@code StationService} and the interaction
 * layer read every {@link StationAsset} from here. Ported verbatim from the MMO's
 * {@code station.StationCatalog} (RPG Stations extraction leg 2).
 *
 * <p>Folded by the plugin's own {@code LoadedAssetsEvent} wiring: the jar-bundled
 * {@code Server/RpgStations/Stations/*.json} defaults ARE part of the folded layer, with pack
 * entries winning per id via the engine's own store merge.
 *
 * <p>Keyed by LOWERCASED asset id.
 */
public final class StationCatalog {

    private static final StationCatalog INSTANCE = new StationCatalog();

    private final ConcurrentHashMap<String, StationAsset> stations = new ConcurrentHashMap<>();
    /**
     * Per-station cache of the effective (authored + {@code FromCrafting}-derived) conversions,
     * keyed by lowercase station id. Computed lazily on first use and dropped on {@link #fold}.
     */
    private final ConcurrentHashMap<String, StationAsset.Conversion[]> resolvedConversions =
            new ConcurrentHashMap<>();

    private StationCatalog() {
    }

    @Nonnull
    public static StationCatalog getInstance() {
        return INSTANCE;
    }

    /**
     * Replace (when {@code replace} is {@code true}) or add to the catalog with {@code layer}
     * (already keyed lowercase by the caller). Any fold can change a station's {@code Recipe},
     * so the derived conversion cache is invalidated here.
     */
    public void fold(@Nonnull Map<String, StationAsset> layer, boolean replace) {
        if (replace) {
            stations.clear();
        }
        stations.putAll(layer);
        resolvedConversions.clear();
    }

    /**
     * The station's EFFECTIVE conversions - authored {@code Conversions} FIRST, then any
     * {@code FromCrafting}-derived conversions - computed lazily ONCE per station and cached
     * until the next {@link #fold}. See {@link StationRecipeDeriver}.
     */
    @Nonnull
    public StationAsset.Conversion[] resolvedConversions(@Nonnull StationAsset asset) {
        String id = asset.getId();
        if (id == null) {
            return StationRecipeDeriver.resolve(asset.getRecipe(), StationRecipeDeriver.liveCandidates());
        }
        return resolvedConversions.computeIfAbsent(id.toLowerCase(Locale.ROOT),
                k -> StationRecipeDeriver.resolve(asset.getRecipe(), StationRecipeDeriver.liveCandidates()));
    }

    /**
     * The ACTION-AWARE sibling of {@link #resolvedConversions(StationAsset)} (design section 9.1,
     * phase 2 leg E): a multi-action station's per-action {@code Recipe} override (e.g. the
     * anvil's "convert" action) needs its OWN derived-conversion cache entry, distinct from the
     * station-level one - {@code actionRecipe} is the caller's ALREADY {@code ActionResolver}-resolved
     * group (whole-group-override applied), never re-derived here. Cached under a
     * {@code "<stationId>::<actionId>"} key so the single-action ({@code ACTION_WORK}) case never
     * collides with (or invalidates) the plain per-station cache entry above.
     */
    @Nonnull
    public StationAsset.Conversion[] resolvedConversions(@Nonnull StationAsset asset, @Nonnull String actionId,
            @Nullable StationAsset.Recipe actionRecipe) {
        String id = asset.getId();
        String cacheKey = (id != null ? id.toLowerCase(Locale.ROOT) : "?") + "::" + actionId.toLowerCase(Locale.ROOT);
        return resolvedConversions.computeIfAbsent(cacheKey,
                k -> StationRecipeDeriver.resolve(actionRecipe, StationRecipeDeriver.liveCandidates()));
    }

    @Nullable
    public StationAsset getStation(@Nonnull String id) {
        return stations.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, StationAsset> all() {
        return Collections.unmodifiableMap(stations);
    }

    public int size() {
        return stations.size();
    }

    /**
     * Every KNOWN flair id, lowercased: every station's inline {@code Flairs} map, UNIONED with
     * every registered standalone {@code asset.FlairAsset} id (design section 9.6, leg F - the
     * open vocabulary; a {@code FlairAsset} id counts here regardless of its own {@code Stations}
     * restriction, since this method answers "is this id valid to grant at all", not "does it
     * apply to THIS station"). Used by the flair-grant command soft-warn (any consumer that
     * grants a flair, e.g. the MMO bridge's {@code /mmostation flair grant}).
     */
    @Nonnull
    public Set<String> allFlairIds() {
        Set<String> ids = new HashSet<>();
        for (StationAsset asset : stations.values()) {
            Map<String, StationAsset.Flair> flairs = asset.getFlairs();
            if (flairs == null || flairs.isEmpty()) {
                continue;
            }
            for (String id : flairs.keySet()) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String id : FlairCatalog.getInstance().all().keySet()) {
            if (id != null && !id.isBlank()) {
                ids.add(id.toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }
}
