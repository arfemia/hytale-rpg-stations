package com.ziggfreed.rpgstations.station;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ProtectListAsset;

/**
 * The RUNTIME AUTHORITY for the server-wide protect-list: every folded {@link ProtectListAsset},
 * keyed by lowercased asset id, the same fold shape {@link FlairCatalog} keeps. Every file ADDS to
 * the one list ({@link #protects} asks them all), so a pack's file never replaces the jar's or
 * another pack's; the one replacement is by id, which the asset store itself performs when a later
 * layer ships the same file name, and which is how an entry is taken back.
 */
public final class ProtectListCatalog {

    private static final ProtectListCatalog INSTANCE = new ProtectListCatalog();

    private final ConcurrentHashMap<String, ProtectListAsset> lists = new ConcurrentHashMap<>();

    private ProtectListCatalog() {
    }

    @Nonnull
    public static ProtectListCatalog getInstance() {
        return INSTANCE;
    }

    /** Folds {@code layer} (keyed lowercase by the caller); a same-id entry replaces the one already held. */
    public void fold(@Nonnull Map<String, ProtectListAsset> layer, boolean replace) {
        if (replace) {
            lists.clear();
        }
        lists.putAll(layer);
    }

    @Nullable
    public ProtectListAsset get(@Nonnull String id) {
        return lists.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, ProtectListAsset> all() {
        return Collections.unmodifiableMap(lists);
    }

    public int size() {
        return lists.size();
    }

    /**
     * Whether any folded file that applies at {@code stationId}'s action {@code actionId} protects
     * the material ({@link StationCustody#isProtected}, the pure core).
     */
    public boolean protects(@Nullable String stationId, @Nullable String actionId, @Nullable String heldItemId,
            @Nullable String[] heldResourceTypeIds, @Nullable Map<String, String[]> heldTags,
            @Nullable String heldFunction) {
        return !lists.isEmpty() && StationCustody.isProtected(lists.values(), stationId, actionId, heldItemId,
                heldResourceTypeIds, heldTags, heldFunction);
    }
}
