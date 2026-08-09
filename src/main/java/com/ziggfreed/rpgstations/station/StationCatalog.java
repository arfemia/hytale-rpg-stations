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
 * layer read every {@link StationAsset} from here.
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
     * Cache of the effective (authored + {@code FromCrafting}-derived) conversions, keyed
     * {@code "<stationId>::<actionId>"} - one recipe per action, so the action id alone identifies
     * it. Computed lazily on first use and dropped on {@link #fold}.
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
     * (already keyed lowercase by the caller). Any fold can change an action's {@code Recipe},
     * so the derived conversion cache is invalidated here.
     */
    public void fold(@Nonnull Map<String, StationAsset> layer, boolean replace) {
        if (replace) {
            stations.clear();
        }
        stations.putAll(layer);
        invalidateResolvedConversions();
    }

    /**
     * Drops the derived-conversion cache without touching the station map. Called by
     * {@link ExtensionCatalog#fold} and {@link ActionCatalog#fold} as well as {@link #fold}: an
     * {@code Action}-targeted {@code ExtensionAsset} appends to the very array cached here and a
     * {@code Ref}'d {@code ActionAsset} owns the {@code Recipe} it is derived from, and the three
     * stores fold in no guaranteed order, so a layer arriving after the first conversion resolve
     * would otherwise never be seen.
     */
    public void invalidateResolvedConversions() {
        resolvedConversions.clear();
    }

    /**
     * ONE action's EFFECTIVE conversions - its recipe's authored {@code Conversions} FIRST, then any
     * {@code FromCrafting}-derived ones (see {@link StationRecipeDeriver}), then any appended by an
     * {@code Action}-targeted {@code ExtensionAsset} - computed lazily and cached until the next
     * {@link #fold} or {@link #invalidateResolvedConversions}.
     *
     * <p>{@code recipe} is the caller's ALREADY {@code ActionResolver}-resolved {@code Recipe},
     * never re-derived here. The cache key is {@code "<stationId>::<actionId>"}: one recipe per
     * action, so an action id fully identifies the entry.
     *
     * <p>Extension conversions are folded in INSIDE the cache load, so the append is paid once per
     * action rather than on every per-cycle read; the correctness of that depends entirely on both
     * fold paths clearing this cache, which is why {@link #invalidateResolvedConversions} exists.
     * They land LAST, after the derived ones, so an appended conversion never displaces the base
     * recipe's first-authored default output category.
     */
    @Nonnull
    public StationAsset.Conversion[] resolvedConversions(@Nonnull StationAsset asset, @Nonnull String actionId,
            @Nullable StationAsset.Recipe recipe) {
        String id = asset.getId();
        String cacheKey = (id != null ? id.toLowerCase(Locale.ROOT) : "?")
                + "::" + actionId.toLowerCase(Locale.ROOT);
        return resolvedConversions.computeIfAbsent(cacheKey, k -> {
            StationAsset.Conversion[] derived =
                    StationRecipeDeriver.resolve(recipe, StationRecipeDeriver.liveCandidates());
            String target = ActionResolver.actionTargetId(asset, actionId);
            if (target == null) {
                return derived;
            }
            StationAsset.Conversion[] merged =
                    ExtensionCatalog.getInstance().applyToActionConversions(id, target, derived);
            return merged != null ? merged : derived;
        });
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
     * apply to THIS station"). Used by the flair-grant command soft-warn (whatever mod registers
     * a {@code FlairUnlockProvider} and grants ids into it).
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
