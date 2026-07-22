package com.ziggfreed.rpgstations.loot;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.RollPool;

/**
 * The RUNTIME AUTHORITY for named {@link RollPool} tables (design section 9.5), mirroring
 * {@link LootableCatalog}'s exact shape (defaults-then-pack fold via the plugin's own
 * {@code LoadedAssetsEvent} wiring, keyed by lowercased asset id).
 */
public final class RollPoolCatalog {

    private static final RollPoolCatalog INSTANCE = new RollPoolCatalog();

    private final ConcurrentHashMap<String, RollPool> pools = new ConcurrentHashMap<>();

    private RollPoolCatalog() {
    }

    @Nonnull
    public static RollPoolCatalog getInstance() {
        return INSTANCE;
    }

    public void fold(@Nonnull Map<String, RollPool> layer, boolean replace) {
        if (replace) {
            pools.clear();
        }
        pools.putAll(layer);
    }

    @Nullable
    public RollPool get(@Nonnull String id) {
        return pools.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, RollPool> all() {
        return Collections.unmodifiableMap(pools);
    }

    public int size() {
        return pools.size();
    }
}
