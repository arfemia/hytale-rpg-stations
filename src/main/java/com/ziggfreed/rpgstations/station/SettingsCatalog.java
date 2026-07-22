package com.ziggfreed.rpgstations.station;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.asset.SettingsAsset;

/**
 * The RUNTIME AUTHORITY for the ONE {@link SettingsAsset} instance (design section 4.6: a
 * single fixed id, jar default + pack-overridable via the normal Pattern-A store merge).
 * Mirrors {@link StationCatalog}'s fold shape; {@link #current()} always returns a non-null
 * asset (falls back to {@link SettingsAsset#defaults()} before anything has loaded, so callers
 * never null-check).
 */
public final class SettingsCatalog {

    private static final SettingsCatalog INSTANCE = new SettingsCatalog();

    private final AtomicReference<SettingsAsset> current = new AtomicReference<>(SettingsAsset.defaults());

    private SettingsCatalog() {
    }

    @Nonnull
    public static SettingsCatalog getInstance() {
        return INSTANCE;
    }

    /**
     * Folds {@code layer} (already keyed lowercase by the caller): the LAST entry keyed
     * {@link SettingsAsset#ID} wins (defaults, then pack - the engine's own store merge already
     * orders the fold, this just takes whichever single instance survives it). An empty layer
     * is a no-op (the previous / default value stays live).
     */
    public void fold(@Nonnull Map<String, SettingsAsset> layer, boolean replace) {
        SettingsAsset settings = layer.get(SettingsAsset.ID);
        if (settings != null) {
            current.set(settings);
        } else if (replace) {
            current.set(SettingsAsset.defaults());
        }
    }

    @Nonnull
    public SettingsAsset current() {
        return current.get();
    }
}
