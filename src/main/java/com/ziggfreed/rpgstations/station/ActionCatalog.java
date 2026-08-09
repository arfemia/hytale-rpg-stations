package com.ziggfreed.rpgstations.station;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.ActionAsset;

/**
 * The RUNTIME AUTHORITY for standalone {@link ActionAsset}s (scope-2 design 1.5, decision 28a):
 * {@code Server/RpgStations/Actions/*.json} folded by the plugin's own {@code LoadedAssetsEvent}
 * wiring. Mirrors {@link StationCatalog}/{@link FlairCatalog}'s defaults-then-pack fold shape
 * (keyed by lowercased asset id); no {@code PackControlAsset} infra exists yet in this mod, so the
 * fold is always additive.
 *
 * <p>{@link ActionResolver} reads this catalog to resolve an inline {@code Actions} entry's
 * {@code Ref} leaf: the named {@code ActionAsset}'s {@link ActionAsset#getBody()} is the BASE the
 * inline entry overlays. A dangling {@code Ref} (no entry here) resolves as if no ref existed; the
 * validator reports {@code ACTION_REF_UNKNOWN}.
 */
public final class ActionCatalog {

    private static final ActionCatalog INSTANCE = new ActionCatalog();

    private final ConcurrentHashMap<String, ActionAsset> actions = new ConcurrentHashMap<>();

    private ActionCatalog() {
    }

    @Nonnull
    public static ActionCatalog getInstance() {
        return INSTANCE;
    }

    /**
     * Fold {@code layer} in and drop {@link StationCatalog}'s derived-conversion cache, the same
     * companion {@link StationCatalog#fold} and {@link ExtensionCatalog#fold} carry: a {@code Ref}'d
     * action's own {@code Recipe} is part of what that cache derives, and the three stores fold in
     * no guaranteed order, so an {@link ActionAsset} arriving after the first conversion resolve
     * would otherwise stay invisible for the rest of the uptime.
     */
    public void fold(@Nonnull Map<String, ActionAsset> layer, boolean replace) {
        if (replace) {
            actions.clear();
        }
        actions.putAll(layer);
        StationCatalog.getInstance().invalidateResolvedConversions();
    }

    @Nullable
    public ActionAsset get(@Nonnull String id) {
        return actions.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public Map<String, ActionAsset> all() {
        return Collections.unmodifiableMap(actions);
    }

    public int size() {
        return actions.size();
    }
}
