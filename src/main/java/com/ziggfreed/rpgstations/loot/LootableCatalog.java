package com.ziggfreed.rpgstations.loot;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.LootableAsset;

/**
 * The RUNTIME AUTHORITY for named {@link LootableAsset} tables, mirroring {@code
 * station.StationCatalog}'s shape (defaults-then-pack fold via the plugin's own {@code
 * LoadedAssetsEvent} wiring, keyed by lowercased asset id).
 */
public final class LootableCatalog {

    private static final LootableCatalog INSTANCE = new LootableCatalog();

    private final ConcurrentHashMap<String, LootableAsset> lootables = new ConcurrentHashMap<>();

    private LootableCatalog() {
    }

    @Nonnull
    public static LootableCatalog getInstance() {
        return INSTANCE;
    }

    public void fold(@Nonnull Map<String, LootableAsset> layer, boolean replace) {
        if (replace) {
            lootables.clear();
        }
        lootables.putAll(layer);
    }

    @Nullable
    public LootableAsset get(@Nonnull String id) {
        return lootables.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, LootableAsset> all() {
        return Collections.unmodifiableMap(lootables);
    }

    public int size() {
        return lootables.size();
    }
}
