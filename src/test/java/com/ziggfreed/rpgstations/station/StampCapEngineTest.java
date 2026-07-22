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
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * Pure fixture tests for {@link StampCapEngine} (design section 9.5, critique M2's binding fixes):
 * the cap-composition MIN rule (effective budget = MIN of every authored total-budget cap, never
 * max, never sum), PerStat clamping, and the roll model (Always/weighted/Unique). Zero server
 * dependency - {@link ItemStack} is not used here at all (a {@link StatRoll}/{@link StampInspection}
 * fixture is enough).
 */
class StampCapEngineTest {

    private static final StampCapEngine.FactorLookup NO_FACTORS = (id, param) -> null;

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

    // ==================== M2: effective-budget MIN composition ====================

    @Test
    void effectiveBudget_onlyPerItemBudget_returnsIt() {
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(30.0, null, null, null);
        assertEquals(30.0, StampCapEngine.effectiveBudget(caps, NO_FACTORS));
    }

    @Test
    void effectiveBudget_onlySkillScaledBudget_computesFromFactor() {
        StationStep.Stamp.Stats.SkillScaledBudget scaled =
                StationStep.Stamp.Stats.SkillScaledBudget.of("mmoskilltree:skill_level", "SMITHING", 0.5);
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(null, null, scaled, null);
        StampCapEngine.FactorLookup lookup = (id, param) ->
                "mmoskilltree:skill_level".equals(id) && "SMITHING".equals(param) ? 40.0 : null;
        assertEquals(20.0, StampCapEngine.effectiveBudget(caps, lookup)); // 0.5 * 40
    }

    @Test
    void effectiveBudget_bothAuthored_skillScaledSmaller_pickstheMin() {
        StationStep.Stamp.Stats.SkillScaledBudget scaled =
                StationStep.Stamp.Stats.SkillScaledBudget.of("mmoskilltree:skill_level", "SMITHING", 0.5);
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(30.0, null, scaled, null);
        StampCapEngine.FactorLookup lookup = (id, param) -> 10.0; // 0.5 * 10 = 5, smaller than 30
        assertEquals(5.0, StampCapEngine.effectiveBudget(caps, lookup));
    }

    @Test
    void effectiveBudget_bothAuthored_perItemSmaller_pickstheMin() {
        StationStep.Stamp.Stats.SkillScaledBudget scaled =
                StationStep.Stamp.Stats.SkillScaledBudget.of("mmoskilltree:skill_level", "SMITHING", 0.5);
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(30.0, null, scaled, null);
        StampCapEngine.FactorLookup lookup = (id, param) -> 100.0; // 0.5 * 100 = 50, larger than 30
        assertEquals(30.0, StampCapEngine.effectiveBudget(caps, lookup));
    }

    @Test
    void effectiveBudget_neitherAuthored_isUnlimited() {
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(null, null, null, null);
        assertNull(StampCapEngine.effectiveBudget(caps, NO_FACTORS));
        assertNull(StampCapEngine.effectiveBudget(null, NO_FACTORS));
    }

    @Test
    void effectiveBudget_unresolvableFactor_failsClosedToZero() {
        StationStep.Stamp.Stats.SkillScaledBudget scaled =
                StationStep.Stamp.Stats.SkillScaledBudget.of("unknown:factor", null, 0.5);
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(null, null, scaled, null);
        assertEquals(0.0, StampCapEngine.effectiveBudget(caps, NO_FACTORS));
    }

    // ==================== resolve(): full roll + clamp ====================

    private static StationStep.Stamp.Stats statsOf(StatRollEntry[] entries, StationStep.Stamp.Stats.Picks picks,
            boolean unique, StationStep.Stamp.Stats.Caps caps) {
        return StationStep.Stamp.Stats.of(null, entries, picks, unique, caps);
    }

    @Test
    void resolve_fullyCappedItem_deniesWithNoEntries() {
        StatRollEntry entry = StatRollEntry.of("MMO_Crit_Chance", StatRollEntry.Points.of(5.0, 5.0), 1.0, true);
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(30.0, null, null, null);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{entry}, null, false, caps);
        // The item already has 30/30 budget spent.
        StampInspection inspection = new StampInspection(30, Map.of("MMO_Crit_Chance", 30), 1);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, inspection, NO_FACTORS, sequence(0.0));
        assertTrue(plan.denied());
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    void resolve_perStatClamp_boundsASingleStatIndependentlyOfTotalBudget() {
        StatRollEntry entry = StatRollEntry.of("MMO_Crit_Chance", StatRollEntry.Points.of(8.0, 8.0), 1.0, true);
        Map<String, Double> perStat = new LinkedHashMap<>();
        perStat.put("MMO_Crit_Chance", 10.0);
        StationStep.Stamp.Stats.Caps caps = StationStep.Stamp.Stats.Caps.of(100.0, perStat, null, null);
        StationStep.Stamp.Stats stats = statsOf(new StatRollEntry[]{entry}, null, false, caps);
        // 7 points already stamped on this stat, cap is 10 - only 3 more should land, not 8.
        StampInspection inspection = new StampInspection(7, Map.of("MMO_Crit_Chance", 7), 0);
        StampCapEngine.Plan plan = StampCapEngine.resolve(stats, inspection, NO_FACTORS, sequence(0.0));
        assertFalse(plan.denied());
        assertEquals(1, plan.entries().size());
        assertEquals(3, plan.entries().get(0).points());
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
        assertFalse(plan.denied(), "no candidates at all is a no-op, not a denial (nothing was ever attempted)");
        assertTrue(plan.entries().isEmpty());
    }
}
