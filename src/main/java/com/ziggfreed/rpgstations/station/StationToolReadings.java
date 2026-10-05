package com.ziggfreed.rpgstations.station;

import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.entity.HeldItemUtil;
import com.ziggfreed.common.entity.ItemReadings;

/**
 * The station's readings of the worker's held tool ({@code hytale:tool_quality},
 * {@code hytale:tool_item_level}, {@code hytale:tool_durability_percent}), each over the ONE
 * shared item reader ({@link ItemReadings}) with the station's own defaults where that reader
 * cannot answer: {@code 0} for a quality or level with nothing held, {@code 100} for the wear of an
 * empty hand or an item that tracks no durability. The numbers are the ones the api
 * {@code FactorContext} has always carried; only where they are read from moved.
 *
 * <p><b>Quality reads the held ITEM, never the stack.</b> A stack reads the quality given to it (a
 * stamp can give one), else its item's current quality; the tool reading has always been the
 * item's, so a re-qualified tool still reads as its item, through
 * {@link ItemReadings#quality(com.hypixel.hytale.server.core.asset.type.item.config.Item)}. The
 * placed piece's own stack quality is {@code hytale:item_quality}'s reading, a different question.
 *
 * <p>Pure over the stack (no player, no store), so the defaults pin in a unit JVM even though the
 * stack itself cannot be built there.
 */
public final class StationToolReadings {

    /** The quality and item level of nothing held, or of an item the engine cannot read. */
    public static final double NONE = 0.0;

    /** The wear of an empty hand, or of an item that cannot wear. */
    public static final double UNWORN_PERCENT = 100.0;

    private StationToolReadings() {
    }

    /** The held item's current {@code ItemQuality.QualityValue} floored at 0; {@link #NONE} when nothing usable is held. */
    public static double quality(@Nullable ItemStack held) {
        return orDefault(ItemReadings.quality(HeldItemUtil.itemOf(held)), NONE);
    }

    /** The held item's native {@code ItemLevel} floored at 0; {@link #NONE} when nothing usable is held. */
    public static double itemLevel(@Nullable ItemStack held) {
        return orDefault(ItemReadings.itemLevel(held), NONE);
    }

    /** The held stack's remaining durability in {@code [0, 100]}; {@link #UNWORN_PERCENT} for an empty hand or an item that cannot wear. */
    public static double durabilityPercent(@Nullable ItemStack held) {
        return orDefault(ItemReadings.durabilityPercent(held), UNWORN_PERCENT);
    }

    /** The shared reader's "cannot answer" (null) folded onto the station's own default. */
    static double orDefault(@Nullable Double reading, double fallback) {
        return reading != null && Double.isFinite(reading) ? reading : fallback;
    }
}
