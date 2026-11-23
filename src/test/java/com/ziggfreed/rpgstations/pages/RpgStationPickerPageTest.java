package com.ziggfreed.rpgstations.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.ui.ZigFrame;
import com.ziggfreed.rpgstations.pages.PickerCategories.Category;

/**
 * Pure-core coverage for {@link RpgStationPickerPage} (decision 50): the {@link Category} value
 * factories and {@link PickerCategories#visibleCategories} filter, the only logic in this feature
 * that does not need a live server (kept off {@code RpgStationPickerPage} itself deliberately -
 * see {@link PickerCategories}'s javadoc for why). Rendering/selection is smoke-tested in-game
 * per the {@code ui-pages} skill (`.ui` files are not compiled). {@link Message#raw} construction
 * is proven safe in a bare unit JVM elsewhere in this mod's test suite (e.g.
 * {@code StationEnhanceLedgerRowsTest}), so the maintainer picker-smoke-fix costLine carry-through
 * test below constructs a real {@link Message} rather than stubbing it.
 */
public class RpgStationPickerPageTest {

    @Test
    void unlocked_carriesNoLockReason() {
        Category c = Category.unlocked("planks", "Item_Wood_Plank", null, null);
        assertFalse(c.locked());
        assertNull(c.requiredToolLabel());
        assertEquals("planks", c.id());
        assertEquals("Item_Wood_Plank", c.iconItemId());
    }

    @Test
    void locked_carriesTheRequiredToolLabel() {
        Category c = Category.locked("slabs", "Item_Wood_Slab", null, null, "Saw");
        assertTrue(c.locked());
        assertEquals("Saw", c.requiredToolLabel());
    }

    @Test
    void unlocked_carriesTheLabelAndCostLine() {
        Message label = Message.raw("Planks");
        Message cost = Message.raw("1x Trunk -> 4x Planks");
        Category c = Category.unlocked("planks", "Item_Wood_Plank", label, cost);
        assertSame(label, c.label());
        assertSame(cost, c.costLine());
    }

    @Test
    void visibleCategories_showLockedTrue_keepsEveryEntryInOrder() {
        Category a = Category.unlocked("planks", "Item_Wood_Plank", null, null);
        Category b = Category.locked("beams", "Item_Wood_Beam", null, null, "Axe");
        List<Category> visible = PickerCategories.visibleCategories(List.of(a, b), true);
        assertEquals(List.of(a, b), visible);
    }

    @Test
    void visibleCategories_showLockedFalse_dropsLockedEntries_preservesOrder() {
        Category a = Category.unlocked("planks", "Item_Wood_Plank", null, null);
        Category b = Category.locked("beams", "Item_Wood_Beam", null, null, "Axe");
        Category c = Category.unlocked("slabs", "Item_Wood_Slab", null, null);
        List<Category> visible = PickerCategories.visibleCategories(List.of(a, b, c), false);
        assertEquals(List.of(a, c), visible);
    }

    @Test
    void visibleCategories_allLocked_showLockedFalse_yieldsEmptyList() {
        Category a = Category.locked("beams", "Item_Wood_Beam", null, null, "Axe");
        List<Category> visible = PickerCategories.visibleCategories(List.of(a), false);
        assertTrue(visible.isEmpty());
    }

    // ==================== the box on the band ====================

    /**
     * The picker is the one band page whose box follows its cards, and CommonLib's band reaches
     * {@link ZigFrame#BAND_HEAD} above and below the box: however many cards a station offers, the box and its band
     * fit a 1080 screen, and a short list is exactly its cards.
     */
    @Test
    void theBoxFollowsItsCardsAndItsBandFitsA1080Screen() {
        Message cost = Message.raw("1x Trunk -> 4x Planks");
        Category plain = Category.unlocked("planks", "Item_Wood_Plank", null, null);
        Category costed = Category.unlocked("slabs", "Item_Wood_Slab", null, cost);
        Category locked = Category.locked("beams", "Item_Wood_Beam", null, cost, "Axe");

        assertEquals(PickerCategories.CHROME + 2 * PickerCategories.CARD,
                PickerCategories.boxHeight(List.of(costed, costed)), "two cards with their cost lines");
        assertEquals(PickerCategories.CHROME + PickerCategories.CARD - PickerCategories.CARD_LINE,
                PickerCategories.boxHeight(List.of(plain)), "a card with no cost line has no line under it");
        assertEquals(PickerCategories.CHROME + PickerCategories.CARD + PickerCategories.CARD_LINE,
                PickerCategories.boxHeight(List.of(locked)), "a locked card shows its reason under its cost");

        List<Category> many = Collections.nCopies(40, locked);
        int tallest = PickerCategories.boxHeight(many);
        assertEquals(PickerCategories.CHROME + PickerCategories.MAX_LIST_HEIGHT, tallest,
                "a long list stops growing and scrolls");
        assertTrue(tallest + 2 * ZigFrame.BAND_HEAD <= 1080,
                () -> "the tallest box (" + tallest + ") and the band's head and foot must fit a 1080 screen");
    }

    /**
     * A document cannot read a Java constant, so the two documents spell the numbers {@link PickerCategories#boxHeight}
     * adds up: if one moves without the other, the cards overflow the box or leave it a gap.
     */
    @Test
    void theDocumentsSpellTheNumbersTheBoxIsAddedUpFrom() throws IOException {
        String page = Files.readString(PAGE_UI, StandardCharsets.UTF_8);
        String card = Files.readString(CARD_UI, StandardCharsets.UTF_8);

        int twoCards = PickerCategories.CHROME + 2 * PickerCategories.CARD;
        assertTrue(page.contains("Anchor: (Width: " + PickerCategories.BOX_WIDTH + ", Height: " + twoCards + ");"),
                "the box's written size is the width and the two-card height the page starts from");
        assertTrue(page.contains("Anchor: (Width: " + PickerCategories.BOX_WIDTH + ");"), "the frame fills the box");
        assertTrue(page.contains("Anchor: (Height: " + PickerCategories.TITLE_HEIGHT + ");"), "the title");
        assertTrue(page.contains("Anchor: (Top: " + PickerCategories.HINT_TOP + ", Height: "
                + PickerCategories.HINT_HEIGHT + ");"), "the hint");
        assertTrue(page.contains("Anchor: (Top: " + PickerCategories.LIST_TOP + ");"), "the list's gap");

        assertTrue(card.contains("Anchor: (Width: 596, Bottom: " + PickerCategories.CARD_GAP + ");"), "the card's gap");
        assertTrue(card.contains("Anchor: (Height: " + PickerCategories.CARD_BUTTON + ", Width: 596);"),
                "the card's button");
        int lineGap = 4;
        assertEquals(2, count(card, "Anchor: (Top: " + lineGap + ", Left: 84, Width: 512, Height: "
                + (PickerCategories.CARD_LINE - lineGap) + ");"), "the cost line and the lock reason");
    }

    private static final Path PAGE_UI = Path.of("src/main/resources/Common/UI/Custom/Pages/RpgStationPicker.ui");
    private static final Path CARD_UI = Path.of("src/main/resources/Common/UI/Custom/Pages/RpgStationPickerTab.ui");

    private static int count(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            n++;
        }
        return n;
    }
}
