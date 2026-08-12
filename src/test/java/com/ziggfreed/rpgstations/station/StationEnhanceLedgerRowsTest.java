package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.common.loot.stamp.StampInspection;
import com.ziggfreed.common.loot.stamp.StampPlan;
import com.ziggfreed.common.loot.stamp.StatRoll;
import com.ziggfreed.common.loot.stamp.Stamper;
import com.ziggfreed.common.ui.rows.SummaryRow;
import com.ziggfreed.rpgstations.api.EnhanceLine;
import com.ziggfreed.rpgstations.ui.StationSummaryHud;

/**
 * Pure tests for {@link StationService#enhanceLedgerRows} (design section 9.5, phase 2 round-7
 * D-6): one summary row per provider {@link EnhanceLine} (a supplied label rendered verbatim, an
 * absent one falling back to the stat id and its points), plus ONE engine-owned durability row per
 * outcome that added max durability. Extracted pure/static so it needs no live session service. The
 * outcome's before/after {@code ItemStack} snapshots are passed {@code null} here (this method reads
 * only itemId/lines/durabilityAdded, never the stacks), sidestepping the fact that the real
 * {@code ItemStack} class initializer fails in a unit JVM.
 *
 * <p>The hand-made labels below are the DECORATED case. The unlabelled cases are the one production
 * takes today, so both are covered: a fixture set that only ever built labelled lines would go green
 * while every real enhancement rendered an empty row.
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

    /**
     * The shape production actually takes. {@link EnhanceLine#of} is the ONLY factory a Stamp step
     * uses and it always leaves the label null, because the stamper contract has no way to report
     * one - so every fixture above that passes a hand-made label is testing the decorated case, not
     * the shipped one. The row still has to say something: its text goes straight to the client, and
     * a summary that reports nothing about the stats a ritual just applied is indistinguishable from
     * a ritual that did nothing.
     */
    @Test
    void anUnlabelledLine_stillRendersTheStatAndItsPoints() {
        List<StationSummaryHud.LedgerRow> rows = StationService.enhanceLedgerRows(
                List.of(outcome("Sword", List.of(EnhanceLine.of("MMO_CritChance", 5)), 0.0)));

        assertEquals(1, rows.size());
        assertEquals(SummaryRow.Kind.ENHANCE, rows.get(0).kind());
        assertEquals(5, rows.get(0).quantity());
        assertNotNull(rows.get(0).line(), "an enhance row must never carry nothing");
        assertEquals("rpgstations.ui.station.summary.enhance_stat", rows.get(0).line().getMessageId());
    }

    @Test
    void aLabelledLineStillWins_theFallbackIsOnlyForTheUnlabelled() {
        EnhanceLine labelled = line("MMO_Power", 3);
        List<StationSummaryHud.LedgerRow> rows = StationService.enhanceLedgerRows(List.of(
                outcome("Sword", List.of(labelled, EnhanceLine.of("MMO_CritChance", 5)), 0.0)));

        assertEquals(2, rows.size());
        assertSame(labelled.label(), rows.get(0).line());
        assertEquals("rpgstations.ui.station.summary.enhance_stat", rows.get(1).line().getMessageId());
    }

    /**
     * The whole chain a real ritual takes, in one case: a stamper that names its own stats writes
     * the label onto each line, and each line becomes exactly ONE summary row carrying that label.
     *
     * <p>The count is the point as much as the wording. A styled row and a fallback row for the same
     * stat would both render, so the player would read every enhancement twice; the label belongs ON
     * the line precisely so there is one row per stat no matter who supplied the words.
     */
    @Test
    void aDescribingStamper_yieldsOneLabelledRowPerStatAndNoSecondCopy() {
        StampPlan plan = new StampPlan(
                List.of(new StatRoll("Fixture_CritChance", 5), new StatRoll("Fixture_Power", 2)), false);
        StationStepHandlers.StampHandler.Mutation mutation =
                StationStepHandlers.StampHandler.applyStampMutation(null, null, plan, describingStamper());

        List<StationSummaryHud.LedgerRow> rows = StationService.enhanceLedgerRows(
                List.of(outcome("Sword", mutation.lines(), 0.0)));

        assertEquals(2, rows.size(), "one row per stat written, never a styled row plus a fallback row");
        assertEquals("Fixture_CritChance +5", rows.get(0).line().getRawText());
        assertEquals("Fixture_Power +2", rows.get(1).line().getRawText());
        assertEquals(5, rows.get(0).quantity());
        assertEquals(SummaryRow.Kind.ENHANCE, rows.get(0).kind());
    }

    /** A stamper that owns its stat vocabulary and says what each stat is called. */
    private static Stamper describingStamper() {
        return new Stamper() {
            @Nonnull
            @Override
            public StampInspection inspect(@Nonnull ItemStack stack) {
                return StampInspection.empty();
            }

            @Nonnull
            @Override
            public ItemStack apply(@Nonnull ItemStack stack, @Nonnull List<StatRoll> entries) {
                return stack;
            }

            @Override
            public Message describe(@Nonnull StatRoll entry) {
                return Message.raw(entry.statId() + " +" + entry.points());
            }
        };
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
