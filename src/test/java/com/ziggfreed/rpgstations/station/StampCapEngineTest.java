package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.api.StampInspection;
import com.ziggfreed.rpgstations.api.StatRoll;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * Pure fixture tests for {@link StampCapEngine} (scope-2 design 3.8, critique M2's binding fix
 * re-shaped onto {@code Caps.Budgets[]}): the cap-composition MIN rule (effective budget = MIN of
 * every authored {@code Budget} entry, never max, never sum), a flat vs factor-scaled budget, and
 * the roll model (Always/weighted/Unique/PerStat). Zero server dependency.
 */
class StampCapEngineTest {

    private static final StampCapEngine.FactorLookup NO_FACTORS = (id, param) -> null;

    private static StationStep.Stamp.Stats.Budget flat(double points) {
        return StationStep.Stamp.Stats.Budget.flat(points);
    }

    private static StationStep.Stamp.Stats.Budget scaled(double pointsPer, String factor, String param) {
        return StationStep.Stamp.Stats.Budget.scaled(pointsPer, new FactorRef[]{FactorRef.of(factor, param, null)});
    }

    private static StationStep.Stamp.Stats.Caps caps(StationStep.Stamp.Stats.Budget[] budgets,
            Map<String, Double> perStat) {
        return StationStep.Stamp.Stats.Caps.of(budgets, perStat, null);
    }

    /** A deterministic sequence of {@code [0,1)} samples, cycling once exhausted. */
    private static StampCapEngine.RollSource sequence(double... values) {
        return new StampCapEngine.RollSource() {
            int i = 0;

            @Override
            public double next() {
                double v = values[i % values.length];
                i++;
                return v;
            }
        };
    }

    // ==================== M2: effective-budget MIN composition over Budgets[] ====================

    @Test
    void effectiveBudget_singleFlat_returnsIt() {
        assertEquals(30.0, StampCapEngine.effectiveBudget(
                caps(new StationStep.Stamp.Stats.Budget[]{flat(30.0)}, null), NO_FACTORS));
    }

    @Test
    void effectiveBudget_singleFactorScaled_computesFromFactor() {
        StampCapEngine.FactorLookup lookup = (id, param) ->
                "stat".equals(id) && "MMO_Level_SMITHING".equals(param) ? 40.0 : null;
        assertEquals(20.0, StampCapEngine.effectiveBudget(
                caps(new StationStep.Stamp.Stats.Budget[]{scaled(0.5, "stat", "MMO_Level_SMITHING")}, null), lookup));
    }

    @Test
    void effectiveBudget_bothAuthored_picksTheMin_scaledSmaller() {
        StampCapEngine.FactorLookup lookup = (id, param) -> 10.0; // 0.5 * 10 = 5 < 30
        assertEquals(5.0, StampCapEngine.effectiveBudget(
                caps(new StationStep.Stamp.Stats.Budget[]{flat(30.0), scaled(0.5, "stat", "MMO_Level_SMITHING")}, null),
                lookup));
    }

    @Test
    void effectiveBudget_bothAuthored_picksTheMin_flatSmaller() {
        StampCapEngine.FactorLookup lookup = (id, param) -> 100.0; // 0.5 * 100 = 50 > 30
        assertEquals(30.0, StampCapEngine.effectiveBudget(
                caps(new StationStep.Stamp.Stats.Budget[]{flat(30.0), scaled(0.5, "stat", "MMO_Level_SMITHING")}, null),
                lookup));
    }

    @Test
    void effectiveBudget_noBudgets_isUnlimited() {
        assertNull(StampCapEngine.effectiveBudget(caps(null, null), NO_FACTORS));
        assertNull(StampCapEngine.effectiveBudget(caps(new StationStep.Stamp.Stats.Budget[0], null), NO_FACTORS));
        assertNull(StampCapEngine.effectiveBudget(null, NO_FACTORS));
    }

    @Test
    void effectiveBudget_unresolvableFactor_failsClosedToZero() {
        assertEquals(0.0, StampCapEngine.effectiveBudget(
                caps(new StationStep.Stamp.Stats.Budget[]{scaled(0.5, "unknown:factor", null)}, null), NO_FACTORS));
    }

    // ==================== resolve(): full roll + clamp ====================

    private static StationStep.Stamp.Stats statsOf(StatRollEntry[] entries, StationStep.Stamp.Stats.Picks picks,
            boolean unique, StationStep.Stamp.Stats.Caps caps) {
        return StationStep.Stamp.Stats.of(null, entries, picks, unique, caps);
    }

    @Test
    void resolve_fullyCappedItem_deniesWithNoEntries() {
        StatRollEntry entry = StatRollEntry.of("MMO_CritChance", StatRollEntry.Points.of(5.0, 5.0), 1.0, true);
        StationStep.Stamp.Stats.Caps caps = caps(new StationStep.Stamp.Stats.Budget[]{flat(30.0)}, null);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{entry}, null, false, caps);
        StampInspection inspection = new StampInspection(30, Map.of("MMO_CritChance", 30), 1);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, inspection, NO_FACTORS, sequence(0.0));
        assertTrue(plan.denied());
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    void resolve_perStatClamp_boundsASingleStatIndependentlyOfTotalBudget() {
        StatRollEntry entry = StatRollEntry.of("MMO_CritChance", StatRollEntry.Points.of(8.0, 8.0), 1.0, true);
        Map<String, Double> perStat = new LinkedHashMap<>();
        perStat.put("MMO_CritChance", 10.0);
        StationStep.Stamp.Stats.Caps caps = caps(new StationStep.Stamp.Stats.Budget[]{flat(100.0)}, perStat);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{entry}, null, false, caps);
        StampInspection inspection = new StampInspection(7, Map.of("MMO_CritChance", 7), 0);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, inspection, NO_FACTORS, sequence(0.0));
        assertFalse(plan.denied());
        assertEquals(1, plan.entries().size());
        assertEquals(3, plan.entries().get(0).points());
    }

    @Test
    void resolve_pointsAddFactors_scalesTheRolledMagnitude() {
        // Base fixed 2 points + a factor value 10 weighted 0.5 = +5 -> 7 points.
        StatRollEntry entry = StatRollEntry.of("MMO_Luck",
                StatRollEntry.Points.of(2.0, 2.0, new FactorRef[]{FactorRef.of("stat", "MMO_Luck", 0.5)}), 1.0, true);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{entry}, null, false, null);
        StampCapEngine.FactorLookup lookup = (id, param) -> 10.0;
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, StampInspection.empty(), lookup, sequence(0.0));
        assertEquals(7, plan.entries().get(0).points());
    }

    @Test
    void resolve_alwaysEntry_grantsRegardlessOfPicks() {
        StatRollEntry always = StatRollEntry.of("MMO_Luck", StatRollEntry.Points.of(2.0, 2.0), 1.0, true);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{always}, null, false, null);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, StampInspection.empty(), NO_FACTORS, sequence(0.0));
        assertFalse(plan.denied());
        assertEquals(1, plan.entries().size());
        assertEquals("MMO_Luck", plan.entries().get(0).statId());
        assertEquals(2, plan.entries().get(0).points());
    }

    @Test
    void resolve_uniquePicks_neverGrantsTheSameStatTwice() {
        StatRollEntry a = StatRollEntry.of("MMO_Luck", StatRollEntry.Points.of(1.0, 1.0), 1.0, false);
        StatRollEntry b = StatRollEntry.of("MMO_Luck", StatRollEntry.Points.of(1.0, 1.0), 1.0, false);
        StatRollEntry c = StatRollEntry.of("MMO_BonusXp", StatRollEntry.Points.of(1.0, 1.0), 1.0, false);
        StationStep.Stamp.Stats.Picks picks = StationStep.Stamp.Stats.Picks.of(2, 2);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{a, b, c}, picks, true, null);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, StampInspection.empty(), NO_FACTORS, sequence(0.0, 0.99));
        long luckCount = plan.entries().stream().filter(e -> e.statId().equals("MMO_Luck")).count();
        assertTrue(luckCount <= 1, "Unique must never grant the same stat twice");
    }

    @Test
    void resolve_noCandidates_isNotDenied() {
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[0], null, false, null);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, StampInspection.empty(), NO_FACTORS, sequence(0.0));
        assertFalse(plan.denied());
        assertTrue(plan.entries().isEmpty());
    }
}
