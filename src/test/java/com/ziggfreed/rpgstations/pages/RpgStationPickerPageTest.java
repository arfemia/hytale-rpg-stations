package com.ziggfreed.rpgstations.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;

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
}
