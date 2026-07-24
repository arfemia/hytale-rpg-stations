package com.ziggfreed.rpgstations.station;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.stats.StackStats;
import com.ziggfreed.rpgstations.api.EnhanceStamper;
import com.ziggfreed.rpgstations.api.StampInspection;
import com.ziggfreed.rpgstations.api.StampResult;
import com.ziggfreed.rpgstations.api.StatRoll;

/**
 * RpgStations' own BUILT-IN DEFAULT {@link EnhanceStamper} (design section 3.9, gate decision 19
 * / 37): registered at {@code RpgStationsPlugin#setup()} so a standalone (no-MMO) server's anvil
 * Stamp step stamps REAL native stat channels with zero progression-mod code. Writes the common
 * {@code ziggfreed-common} {@link StackStats} record directly - the SAME record any richer
 * stamper (the MMO's own included) reads/writes, so cross-mod cap/budget accounting holds no
 * matter which server stamped a given stack first (decision 21). A later {@code
 * EnhanceStamperRegistry#register} call REPLACES this one wholesale (last-registration-wins,
 * {@code EnhanceStamperRegistryImpl}'s own discipline) - this class is only ever active on a
 * server with no richer stamper installed.
 *
 * <p><b>{@link #inspect}</b> is a thin read: {@link StackStats#entriesOf}/{@link
 * StackStats#stampCountOf} already answer both questions this contract needs, rounded to whole
 * points ({@code StampInspection.pointsByStat} is {@code Map<String, Integer>} - cap math is
 * whole-point, matching {@code StampCapEngine}'s own rolled-value rounding).
 *
 * <p><b>{@link #apply}</b> is {@code merge + stampReplacingWithCount(+1)}, exactly as the design
 * specifies - RpgStations already rolled + cap-clamped {@code entries} (via {@code
 * StampCapEngine}), this stamper never re-derives a cap, it only writes the finished result. The
 * actual GAMEPLAY effect of a stamped stat (a real stat-map modifier a wearer/wielder feels) comes
 * from whichever consumer bridges {@link StackStats#getEntries()} onto the native
 * {@code EntityStatMap} on equip (e.g. {@code ziggfreed-common}'s own {@code stats.EquipStatBridge}
 * - a standalone server that wants that bridge running installs/wires it itself; this stamper only
 * owns the ITEM-FORMAT write).
 *
 * <p><b>Deliberately reports NO {@link com.ziggfreed.rpgstations.api.EnhanceLine}s</b> - {@link
 * #apply} always returns an empty line list ({@link StampResult#of}). Composing a per-stat display
 * line needs a localized, styled label, and this default has no "richer policy" layer (display
 * refresh, per-stat wording/color) to draw one from - the design doc's own words for what stays
 * MMO-side. The session summary's engine-owned Durability row still reports unconditionally
 * (RpgStations-native, not stamper-sourced), so a bare anvil visibly did SOMETHING even with only
 * this default installed; per-stat labeled rows are the richer stamper's job. This mirrors the
 * documented "NO display refresh" ruling for the item's tooltip (design 3.9), extended here to the
 * summary ledger for the identical reason: a standalone-only server introduces zero new
 * player-facing strings from this class.
 */
public final class DefaultEnhanceStamper implements EnhanceStamper {

    @Override
    @Nonnull
    public StampInspection inspect(@Nonnull ItemStack stack) {
        Map<String, Double> entries = StackStats.entriesOf(stack);
        int stampCount = StackStats.stampCountOf(stack);
        Map<String, Integer> byStat = roundEntries(entries);
        int total = 0;
        for (int points : byStat.values()) {
            total += points;
        }
        return new StampInspection(total, byStat, stampCount);
    }

    @Override
    @Nonnull
    public StampResult apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries) {
        Map<String, Double> added = sumRolls(entries);
        if (added.isEmpty()) {
            return StampResult.of(stack);
        }
        Map<String, Double> merged = StackStats.mergeWith(stack, added);
        int nextStampCount = StackStats.stampCountOf(stack) + 1;
        ItemStack stamped = StackStats.stampReplacingWithCount(stack, merged, nextStampCount);
        return StampResult.of(stamped);
    }

    /**
     * PURE core of {@link #inspect}: {@code statId -> whole-point} rounding of a {@link
     * StackStats#getEntries} map ({@code null}/empty in, empty map out) - {@link
     * StampInspection#pointsByStat} is {@code Map<String, Integer>}, cap math is whole-point,
     * matching {@code StampCapEngine}'s own rolled-value rounding. Package-private so it is
     * unit-testable without an {@link ItemStack}.
     */
    @Nonnull
    static Map<String, Integer> roundEntries(@Nullable Map<String, Double> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> byStat = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : entries.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            byStat.merge(e.getKey(), (int) Math.round(e.getValue()), Integer::sum);
        }
        return byStat;
    }

    /**
     * PURE core of {@link #apply}: sums a {@link StatRoll} list into one {@code statId -> amount}
     * addition map for {@link StackStats#mergeWith} (same-stat rolls sum; a blank/null {@code
     * statId} is skipped). Package-private so it is unit-testable without an {@link ItemStack}.
     */
    @Nonnull
    static Map<String, Double> sumRolls(@Nonnull List<StatRoll> rolls) {
        if (rolls.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> added = new LinkedHashMap<>();
        for (StatRoll roll : rolls) {
            if (roll == null || roll.statId() == null || roll.statId().isBlank()) {
                continue;
            }
            added.merge(roll.statId(), (double) roll.points(), Double::sum);
        }
        return added;
    }
}
