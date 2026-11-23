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
 *
 * <p><b>The box's height</b> ({@link #boxHeight}) lives here too. The page sits on CommonLib's band frame, whose
 * head and foot reach 78 above and below the page's box, and it is the one band page whose box follows its content,
 * so the page sets the box's height itself from the numbers below, which {@code Pages/RpgStationPicker.ui} and
 * {@code Pages/RpgStationPickerTab.ui} spell (a document cannot read a Java constant, so
 * {@code RpgStationPickerPageTest} holds the two together). The list stops growing at {@link #MAX_LIST_HEIGHT} and
 * scrolls past it, so the tallest box and its band fit a 1080 screen.
 */
public final class PickerCategories {

    /** The box's width: {@code #RpgStationPickerRoot}'s, and the frame's inside it. */
    public static final int BOX_WIDTH = 640;

    /** The frame's own padding round its column, on every side (CommonLib's {@code ZigFrames.ui} {@code #Content}). */
    static final int FRAME_PADDING = 18;

    /** The title's height. */
    static final int TITLE_HEIGHT = 30;

    /** The gap over the hint. */
    static final int HINT_TOP = 4;

    /** The hint's height: two lines at most. */
    static final int HINT_HEIGHT = 40;

    /** The gap over the list. */
    static final int LIST_TOP = 16;

    /** A card's button. */
    static final int CARD_BUTTON = 76;

    /** A line under a card's button (its cost line, its lock reason): the 4 gap over it and its 20. */
    static final int CARD_LINE = 24;

    /** The gap under every card. */
    static final int CARD_GAP = 10;

    /** A card with its cost line, the usual shape. */
    static final int CARD = CARD_BUTTON + CARD_LINE + CARD_GAP;

    /** The list's tallest: five cards and half a sixth, so a longer list plainly scrolls. */
    static final int MAX_LIST_HEIGHT = 5 * CARD + CARD / 2;

    /** Everything in the box but its list: the frame's padding above and below, the title, the hint and the gaps. */
    static final int CHROME = FRAME_PADDING + TITLE_HEIGHT + HINT_TOP + HINT_HEIGHT + LIST_TOP + FRAME_PADDING;

    private PickerCategories() {
    }

    /** One card's height: its button, a line for each of its cost line and its lock reason it shows, its gap. */
    static int cardHeight(@Nonnull Category c) {
        return CARD_BUTTON + (c.costLine() != null ? CARD_LINE : 0) + (c.locked() ? CARD_LINE : 0) + CARD_GAP;
    }

    /**
     * The box's height for these cards (the visible ones, {@link #visibleCategories}): the chrome and the cards, the
     * cards no taller than {@link #MAX_LIST_HEIGHT}, past which the list scrolls.
     */
    public static int boxHeight(@Nonnull List<Category> visible) {
        int list = 0;
        for (Category c : visible) {
            list += cardHeight(c);
        }
        return CHROME + Math.min(list, MAX_LIST_HEIGHT);
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
