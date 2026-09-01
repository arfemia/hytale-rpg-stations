package com.ziggfreed.rpgstations.station;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import com.ziggfreed.rpgstations.asset.RpgStationsSettingsAsset;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The RUNTIME AUTHORITY for the ONE {@link RpgStationsSettingsAsset} instance (design section 4.6: a
 * single fixed id, jar default + pack-overridable via the normal Pattern-A store merge).
 * Mirrors {@link StationCatalog}'s fold shape; {@link #current()} always returns a non-null
 * asset (falls back to {@link RpgStationsSettingsAsset#defaults()} before anything has loaded, so callers
 * never null-check).
 */
public final class SettingsCatalog {

    private static final SettingsCatalog INSTANCE = new SettingsCatalog();

    private final AtomicReference<RpgStationsSettingsAsset> current = new AtomicReference<>(RpgStationsSettingsAsset.defaults());

    private SettingsCatalog() {
    }

    @Nonnull
    public static SettingsCatalog getInstance() {
        return INSTANCE;
    }

    /**
     * Folds {@code layer} (already keyed lowercase by the caller): the LAST entry keyed
     * {@link RpgStationsSettingsAsset#ID} wins (defaults, then pack - the engine's own store merge already
     * orders the fold, this just takes whichever single instance survives it). An empty layer
     * is a no-op (the previous / default value stays live).
     */
    public void fold(@Nonnull Map<String, RpgStationsSettingsAsset> layer, boolean replace) {
        RpgStationsSettingsAsset settings = layer.get(RpgStationsSettingsAsset.ID);
        if (settings != null) {
            current.set(settings);
            warnRetiredLeaves(settings);
        } else if (replace) {
            current.set(RpgStationsSettingsAsset.defaults());
        }
    }

    /**
     * One WARN per fold for a retired leaf still authored, naming its replacement (warn only,
     * never a parse failure - the file keeps loading and every live leaf applies).
     */
    private static void warnRetiredLeaves(@Nonnull RpgStationsSettingsAsset settings) {
        RpgStationsSettingsAsset.Limits limits = settings.getLimits();
        if (limits != null && limits.getRetiredMaxCustodyClaimsPerWorld() != null) {
            Log.warn("Settings Limits.MaxCustodyClaimsPerWorld is retired and ignored: placed input"
                    + " is stored on the block's own chunk section, so the ceiling is per section -"
                    + " author Limits.MaxStashesPerSection instead.");
        }
    }

    @Nonnull
    public RpgStationsSettingsAsset current() {
        return current.get();
    }
}
