package com.ziggfreed.rpgstations.pages;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;

/**
 * The PURE core behind {@link RpgStationPickerPage}: the {@link Category} value type and the
 * {@link #visibleCategories} filter. Kept in its OWN class (never a nested type on the page
 * itself) so it is unit-testable WITHOUT loading the page class - {@code RpgStationPickerPage}
 * extends the engine's {@code InteractiveCustomUIPage}, and merely classloading that hierarchy in
 * a bare unit JVM throws ({@code ExceptionInInitializerError} at {@code HytaleLogger}, the same
 * class of trap the {@code ItemToolSpec}/{@code StationToolScaling} note documents for asset
 * codecs) - this mirrors that pattern for a PAGE base class instead of a codec base class.
 */
public final class PickerCategories {

    private PickerCategories() {
    }

    /**
     * One selectable output category: a representative item icon, a display NAME (the maintainer
     * smoke-fix wave: the representative output item's own native item name, so a tab is never
     * icon-only), an optional COST LINE (that same representative conversion's input-to-output
     * shape, e.g. "1x Trunk -&gt; 4x Planks"), and, when {@link #locked()}, the display name of the
     * tool required to unlock it. Pure value type - the caller (the engine leg resolving native
     * recipe categories) is the schema authority for what an "id" means; this page treats it as an
     * opaque selection key.
     */
    public record Category(@Nonnull String id, @Nonnull String iconItemId, @Nullable Message label,
            @Nullable Message costLine, boolean locked, @Nullable String requiredToolLabel) {

        @Nonnull
        public static Category unlocked(@Nonnull String id, @Nonnull String iconItemId, @Nullable Message label,
                @Nullable Message costLine) {
            return new Category(id, iconItemId, label, costLine, false, null);
        }

        @Nonnull
        public static Category locked(@Nonnull String id, @Nonnull String iconItemId, @Nullable Message label,
                @Nullable Message costLine, @Nullable String requiredToolLabel) {
            return new Category(id, iconItemId, label, costLine, true, requiredToolLabel);
        }
    }

    /**
     * The rendered tab list: every category when {@code showLocked}, else the unlocked ones only,
     * order preserved.
     */
    @Nonnull
    public static List<Category> visibleCategories(@Nonnull List<Category> categories, boolean showLocked) {
        if (showLocked) {
            return categories;
        }
        List<Category> out = new ArrayList<>(categories.size());
        for (Category c : categories) {
            if (!c.locked()) {
                out.add(c);
            }
        }
        return out;
    }
}
