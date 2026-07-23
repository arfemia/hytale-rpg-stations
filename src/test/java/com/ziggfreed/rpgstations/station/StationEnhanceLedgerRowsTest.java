package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.ui.rows.SummaryRow;
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.ui.StationSummaryHud;

/**
 * Pure tests for {@link StationService#enhanceLedgerRows} (design section 9.5, phase 2 round-7
 * D-6): one summary row per provider {@link EnhanceLine} (label rendered verbatim, {@code ENHANCE}
 * kind), plus ONE engine-owned durability row per outcome that added max durability. Extracted
 * pure/static so it needs no live session service. The outcome's before/after {@code ItemStack}
 * snapshots are passed {@code null} here (this method reads only itemId/lines/durabilityAdded,
 * never the stacks), sidestepping the fact that the real {@code ItemStack} class initializer fails
 * in a unit JVM.
 */
class StationEnhanceLedgerRowsTest {

    private static StationEnhanceOutcome outcome(String itemId, List<EnhanceLine> lines, double durabilityAdded) {
        return new StationEnhanceOutcome(itemId, null, null, lines, durabilityAdded);
    }

    private static EnhanceLine line(String statId, int points) {
        return new EnhanceLine(statId, points, Message.raw("+" + points + " " + statId));
    }

    @Test
    void noOutcomes_noRows() {
        assertTrue(StationService.enhanceLedgerRows(List.of()).isEmpty());
    }

    @Test
    void statLines_oneRowEach_verbatimLabelAndEnhanceKind() {
        EnhanceLine crit = line("MMO_CritChance", 5);
        EnhanceLine power = line("MMO_Power", 3);
        List<StationSummaryHud.LedgerRow> rows =
                StationService.enhanceLedgerRows(List.of(outcome("Sword", List.of(crit, power), 0.0)));

        assertEquals(2, rows.size());
        assertEquals(SummaryRow.Kind.ENHANCE, rows.get(0).kind());
        assertEquals("Sword", rows.get(0).itemId());
        assertEquals(5, rows.get(0).quantity());
        assertSame(crit.label(), rows.get(0).line());
        assertSame(power.label(), rows.get(1).line());
    }

    @Test
    void durabilityOnly_oneEngineOwnedDurabilityRow() {
        List<StationSummaryHud.LedgerRow> rows =
                StationService.enhanceLedgerRows(List.of(outcome("Sword", List.of(), 50.0)));

        assertEquals(1, rows.size());
        assertEquals(SummaryRow.Kind.ENHANCE, rows.get(0).kind());
        assertEquals("Sword", rows.get(0).itemId());
        assertEquals(50, rows.get(0).quantity());
    }

    @Test
    void statLinesPlusDurability_appendsDurabilityAfterStatRows() {
        List<StationSummaryHud.LedgerRow> rows = StationService.enhanceLedgerRows(
                List.of(outcome("Sword", List.of(line("MMO_CritChance", 5)), 25.0)));

        assertEquals(2, rows.size());
        assertEquals(5, rows.get(0).quantity());   // the stat line
        assertEquals(25, rows.get(1).quantity());  // the durability row, appended after
    }

    @Test
    void zeroDurability_addsNoDurabilityRow() {
        List<StationSummaryHud.LedgerRow> rows = StationService.enhanceLedgerRows(
                List.of(outcome("Sword", List.of(line("MMO_Power", 2)), 0.0)));
        assertEquals(1, rows.size());
    }

    @Test
    void multipleOutcomes_rowsSumAcrossThem() {
        List<StationSummaryHud.LedgerRow> rows = StationService.enhanceLedgerRows(List.of(
                outcome("Sword", List.of(line("A", 1), line("B", 2)), 10.0),
                outcome("Axe", List.of(line("C", 3)), 0.0)));
        // outcome 1: 2 stat rows + 1 durability row; outcome 2: 1 stat row, no durability.
        assertEquals(4, rows.size());
    }
}
