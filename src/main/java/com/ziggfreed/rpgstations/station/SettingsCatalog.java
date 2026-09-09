package com.ziggfreed.rpgstations.station;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Presentation;
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

    /**
     * The live settings' {@code Moments} map rebuilt ONCE per fold as a case-insensitive map with
     * its authored spelling kept (blanks dropped - {@link StationFlairs#caseInsensitiveMomentKeys},
     * the same shape an action's map is held in), so every emission reads it with one plain
     * lookup. Never null: empty when nothing is authored.
     */
    private final AtomicReference<Map<String, Presentation>> defaultMoments = new AtomicReference<>(Map.of());

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
            defaultMoments.set(canonicalMoments(settings));
            warnRetiredLeaves(settings);
        } else if (replace) {
            current.set(RpgStationsSettingsAsset.defaults());
            defaultMoments.set(Map.of());
        }
    }

    @Nonnull
    private static Map<String, Presentation> canonicalMoments(@Nonnull RpgStationsSettingsAsset settings) {
        Map<String, Presentation> authored = settings.getMoments();
        return authored == null || authored.isEmpty() ? Map.of() : StationFlairs.caseInsensitiveMomentKeys(authored);
    }

    /**
     * The engine-wide default cue for {@code momentId} (matched case-insensitively), or null when
     * the settings author none - the layer that sits UNDER an action's own entry for the same id,
     * per leaf. Read live, never cached by a caller, so a settings reload lands on the next moment.
     */
    @Nullable
    public Presentation defaultMoment(@Nonnull String momentId) {
        return defaultMoments.get().get(momentId);
    }

    /** The whole engine-wide default cue map, case-insensitive with its authored spelling kept; empty (never null) when none is authored. */
    @Nonnull
    public Map<String, Presentation> defaultMoments() {
        return defaultMoments.get();
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
