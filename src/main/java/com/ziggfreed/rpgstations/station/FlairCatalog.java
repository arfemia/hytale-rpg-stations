package com.ziggfreed.rpgstations.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The RUNTIME AUTHORITY for standalone {@link FlairAsset}s (design section 9.6, phase 2 leg F):
 * the asset-driven half of the open flair/moment vocabulary. Mirrors {@link StationCatalog}'s
 * defaults-then-pack fold shape (keyed by lowercased asset id), folded by the plugin's own
 * {@code LoadedAssetsEvent} wiring - no {@code PackControlAsset} infra exists yet in this mod, so
 * the fold is always additive.
 *
 * <p>{@link #effectiveFlairsFor} is the ONE merge point {@link StationFlairs#effective} resolves
 * against: a station's own inline {@code Flairs} map (added first, a pure authoring convenience)
 * UNIONED with every folded {@link FlairAsset} whose {@link FlairAsset#appliesTo} answers this
 * station id (added second - an asset-driven entry with the SAME flair id as an inline one wins,
 * "folded ONTO the per-station flair map" per the design's own wording). The merged shape is a
 * plain {@code flairId -> momentId -> Presentation} map, decoupled from either source asset type -
 * {@link StationFlairs} never has to know whether a flair id came from a station's own JSON or a
 * third-party pack's standalone file.
 */
public final class FlairCatalog {

    private static final FlairCatalog INSTANCE = new FlairCatalog();

    private final ConcurrentHashMap<String, FlairAsset> flairAssets = new ConcurrentHashMap<>();

    private FlairCatalog() {
    }

    @Nonnull
    public static FlairCatalog getInstance() {
        return INSTANCE;
    }

    public void fold(@Nonnull Map<String, FlairAsset> layer, boolean replace) {
        if (replace) {
            flairAssets.clear();
        }
        flairAssets.putAll(layer);
    }

    @Nullable
    public FlairAsset get(@Nonnull String id) {
        return flairAssets.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, FlairAsset> all() {
        return Collections.unmodifiableMap(flairAssets);
    }

    public int size() {
        return flairAssets.size();
    }

    /**
     * The merged {@code flairId -> momentId -> Presentation} map for one station: the station's
     * own inline {@code Flairs} (a {@code null}/empty-{@code Moments} entry is skipped - it can
     * never overlay anything) UNIONED with every folded {@link FlairAsset} that
     * {@link FlairAsset#appliesTo} {@code stationId}. Returns an empty (never {@code null}) map
     * when neither source contributes anything, so {@link StationFlairs#effective}'s
     * {@code isEmpty()} fast path still short-circuits for a plain station with zero flair
     * content.
     */
    @Nonnull
    public Map<String, Map<String, Presentation>> effectiveFlairsFor(@Nonnull String stationId,
            @Nullable StationAsset asset) {
        Map<String, Map<String, Presentation>> out = new LinkedHashMap<>();
        Map<String, StationAsset.Flair> inline = asset != null ? asset.getFlairs() : null;
        if (inline != null) {
            for (Map.Entry<String, StationAsset.Flair> e : inline.entrySet()) {
                String flairId = e.getKey();
                StationAsset.Flair flair = e.getValue();
                if (flairId == null || flair == null || flair.getMoments() == null || flair.getMoments().isEmpty()) {
                    continue;
                }
                out.put(flairId, flair.getMoments());
            }
        }
        for (FlairAsset fa : flairAssets.values()) {
            if (fa == null || fa.getId() == null || fa.getMoments() == null || fa.getMoments().isEmpty()
                    || !fa.appliesTo(stationId)) {
                continue;
            }
            out.put(fa.getId(), fa.getMoments());
        }
        return out;
    }
}
