package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Roll;

/**
 * Pure {@link RollEvaluator} coverage (design section 4.8), including a test-authored fixture
 * SHAPED like the shipped standalone sawmill {@code Loot} (mirroring
 * {@code Server/RpgStations/Lootables/SawmillFinds.json}'s structure, NOT its production
 * balance numbers - the repo's no-balance-tests rule: every value here is this test's OWN
 * fixture). Every M3 critique fix gets its own assertion: (1) {@code AddFactors} is an array
 * and every entry is summed, (2) a Ladder floor's only reward path is its own {@code Grants},
 * (3) top-level and floor {@code Grants} STACK, (4) a failed {@code Chance} kills the whole
 * roll including its {@code Ladder}, (5) covered on the validator side ({@code
 * StationValidatorTest}) since it is a content-authoring warning, not a runtime decision.
 */
public class RollEvaluatorTest {

    /** A simple {@link RollEvaluator.FactorLookup} backed by a fixed map; missing key = null (unresolvable). */
    private static RollEvaluator.FactorLookup lookup(Map<String, Double> values) {
        return (factorId, param) -> values.get(factorId);
    }

    private static java.util.function.DoubleSupplier fixedRoll(double value) {
        return () -> value;
    }

    // ==================== conditionsPass / conditionPasses ====================

    @Test
    void conditionsPass_nullArray_passesVacuously() {
        assertTrue(RollEvaluator.conditionsPass(null, lookup(Map.of())));
    }

    @Test
    void conditionPasses_blankFactor_passesVacuously() {
        Condition c = Condition.of("", null, null, null);
        assertTrue(RollEvaluator.conditionPasses(c, lookup(Map.of())));
    }

    @Test
    void conditionPasses_unresolvableFactor_failsClosed() {
        Condition c = Condition.of("rpgstations:unknown", null, null, null);
        assertFalse(RollEvaluator.conditionPasses(c, lookup(Map.of())));
    }

    @Test
    void conditionPasses_minMaxBounds() {
        Map<String, Double> values = Map.of("rpgstations:cycle_count", 10.0);
        assertTrue(RollEvaluator.conditionPasses(Condition.of("rpgstations:cycle_count", null, 5.0, null), lookup(values)));
        assertFalse(RollEvaluator.conditionPasses(Condition.of("rpgstations:cycle_count", null, 15.0, null), lookup(values)));
        assertTrue(RollEvaluator.conditionPasses(Condition.of("rpgstations:cycle_count", null, null, 10.0), lookup(values)));
        assertFalse(RollEvaluator.conditionPasses(Condition.of("rpgstations:cycle_count", null, null, 9.0), lookup(values)));
    }

    // ==================== chancePasses (M3 fix 1: AddFactors is an array, summed) ====================

    @Test
    void chancePasses_absentChance_alwaysPasses() {
        assertTrue(RollEvaluator.chancePasses(null, lookup(Map.of()), fixedRoll(99.0)));
    }

    @Test
    void chancePasses_sumsEveryAddFactorEntry() {
        Map<String, Double> values = Map.of("rpgstations:tool_power", 5.0, "rpgstations:cycle_count", 3.0);
        Roll.Chance chance = Roll.Chance.of(2.0,
                new Condition[]{
                        Condition.of("rpgstations:tool_power", null, null, null),
                        Condition.of("rpgstations:cycle_count", null, null, null)
                }, 100.0);
        // effective = 2 + 5 + 3 = 10; a roll sample just under 10 hits, just at/over misses.
        assertTrue(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(9.999)));
        assertFalse(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(10.0)));
    }

    @Test
    void chancePasses_clampsToCapPercent() {
        Roll.Chance chance = Roll.Chance.of(50.0,
                new Condition[]{Condition.of("f", null, null, null)}, 60.0);
        Map<String, Double> values = Map.of("f", 1000.0); // would be 1050 uncapped
        assertTrue(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(59.999)));
        assertFalse(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(60.0)));
    }

    @Test
    void chancePasses_nonpositiveEffective_neverHits() {
        Roll.Chance chance = Roll.Chance.of(0.0, null, 100.0);
        assertFalse(RollEvaluator.chancePasses(chance, lookup(Map.of()), fixedRoll(0.0)));
    }

    @Test
    void chancePasses_unresolvableAddFactor_contributesZero() {
        Roll.Chance chance = Roll.Chance.of(5.0,
                new Condition[]{Condition.of("rpgstations:unknown", null, null, null)}, 100.0);
        assertTrue(RollEvaluator.chancePasses(chance, lookup(Map.of()), fixedRoll(4.999)));
    }

    // ==================== highestFloor (the ladder pick) ====================

    @Test
    void highestFloor_picksTheHighestReachedFloor() {
        Roll.Ladder.Floor low = Roll.Ladder.Floor.of(10.0, Roll.Grants.of(null, "T1", null), null);
        Roll.Ladder.Floor high = Roll.Ladder.Floor.of(25.0, Roll.Grants.of(null, "T2", null), null);
        Roll.Ladder ladder = Roll.Ladder.of(Condition.of("rpgstations:cycle_count", null, null, null),
                new Roll.Ladder.Floor[]{low, high});
        Map<String, Double> values = Map.of("rpgstations:cycle_count", 30.0);
        assertEquals(high, RollEvaluator.highestFloor(ladder, lookup(values)));

        Map<String, Double> onlyLow = Map.of("rpgstations:cycle_count", 12.0);
        assertEquals(low, RollEvaluator.highestFloor(ladder, lookup(onlyLow)));

        Map<String, Double> none = Map.of("rpgstations:cycle_count", 1.0);
        assertNull(RollEvaluator.highestFloor(ladder, lookup(none)));
    }

    @Test
    void highestFloor_unresolvableValueFactor_returnsNull() {
        Roll.Ladder ladder = Roll.Ladder.of(Condition.of("rpgstations:unknown", null, null, null),
                new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, Roll.Grants.of(null, "T1", null), null)});
        assertNull(RollEvaluator.highestFloor(ladder, lookup(Map.of())));
    }

    @Test
    void highestFloor_malformedFloor_isSkippedNotThrown() {
        Roll.Ladder.Floor malformed = Roll.Ladder.Floor.of(null, Roll.Grants.of(null, "T1", null), null);
        Roll.Ladder.Floor valid = Roll.Ladder.Floor.of(5.0, Roll.Grants.of(null, "T2", null), null);
        Roll.Ladder ladder = Roll.Ladder.of(Condition.of("f", null, null, null),
                new Roll.Ladder.Floor[]{malformed, valid});
        assertEquals(valid, RollEvaluator.highestFloor(ladder, lookup(Map.of("f", 10.0))));
    }

    // ==================== evaluate() - the M3-fixed integration semantics ====================

    @Test
    void evaluate_conditionsFail_producesNone() {
        Roll roll = Roll.of("Cycle", new Condition[]{Condition.of("rpgstations:unknown", null, null, null)},
                null, null, Roll.Grants.of(1, null, null));
        RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup(Map.of()), fixedRoll(0.0));
        assertFalse(outcome.isHit());
        assertNull(outcome.getTopGrants());
    }

    @Test
    void evaluate_chanceFails_killsTheWholeRoll_ladderIncluded() {
        // M3 fix 4: a failed Chance means the Ladder is never even reached, despite the sample
        // that WOULD hit the ladder's own factor.
        Roll.Ladder ladder = Roll.Ladder.of(Condition.of("rpgstations:cycle_count", null, null, null),
                new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, Roll.Grants.of(null, "T1", null), null)});
        Roll roll = Roll.of("Cycle", null, Roll.Chance.of(0.0, null, 100.0), ladder, null);
        RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup(Map.of("rpgstations:cycle_count", 999.0)),
                fixedRoll(0.0));
        assertFalse(outcome.isHit());
        assertNull(outcome.getFloorGrants());
    }

    @Test
    void evaluate_topGrantsAndFloorGrants_stack() {
        // M3 fix 3: top-level Grants and the reached floor's Grants BOTH apply.
        Roll.Grants top = Roll.Grants.of(1, null, null);
        Roll.Grants floorGrants = Roll.Grants.of(null, "T1", null);
        Roll.Ladder ladder = Roll.Ladder.of(Condition.of("rpgstations:cycle_count", null, null, null),
                new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, floorGrants, null)});
        Roll roll = Roll.of("Cycle", null, null, ladder, top);
        RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup(Map.of("rpgstations:cycle_count", 5.0)),
                fixedRoll(0.0));
        assertTrue(outcome.isHit());
        assertEquals(top, outcome.getTopGrants());
        assertEquals(floorGrants, outcome.getFloorGrants());
    }

    /**
     * A test-authored fixture SHAPED like the shipped standalone sawmill's {@code Loot} (two
     * rolls: a tool-power-scaled bonus-copy Chance, and a cycle-count Ladder gated behind a
     * Conditions+Chance combo) - mirrors the STRUCTURE of {@code SawmillFinds.json}, never its
     * production balance numbers (every value below is this test's own choice).
     */
    @Test
    void sawmillShapedFixture_bonusCopyRoll_andGatedLadderRoll() {
        Roll bonusCopyRoll = Roll.of("Cycle", null,
                Roll.Chance.of(2.0, new Condition[]{Condition.of("rpgstations:tool_power", null, null, null)}, 25.0),
                null, Roll.Grants.of(1, null, null));

        Roll.Grants t1 = Roll.Grants.of(null, "RPG_Station_Sawmill_T1", null);
        Roll.Grants t2 = Roll.Grants.of(null, "RPG_Station_Sawmill_T2", null);
        Roll.Ladder ladder = Roll.Ladder.of(Condition.of("rpgstations:cycle_count", null, null, null),
                new Roll.Ladder.Floor[]{
                        Roll.Ladder.Floor.of(10.0, t1, null),
                        Roll.Ladder.Floor.of(25.0, t2, null)
                });
        Roll ladderRoll = Roll.of("Cycle",
                new Condition[]{Condition.of("rpgstations:cycle_count", null, 10.0, null)},
                Roll.Chance.of(15.0, null, 100.0), ladder, null);

        Map<String, Double> earlySession = new HashMap<>();
        earlySession.put("rpgstations:tool_power", 0.5);
        earlySession.put("rpgstations:cycle_count", 3.0);

        // Bonus-copy roll: 2 + 0.5 = 2.5% effective; a sample under it hits.
        RollEvaluator.Outcome bonusHit = RollEvaluator.evaluate(bonusCopyRoll, lookup(earlySession), fixedRoll(2.0));
        assertTrue(bonusHit.isHit());
        assertEquals(1, bonusHit.getTopGrants().getBonusOutputCopies());

        // Ladder roll: Conditions require cycle_count >= 10; cycle 3 fails Conditions outright.
        RollEvaluator.Outcome tooEarly = RollEvaluator.evaluate(ladderRoll, lookup(earlySession), fixedRoll(0.0));
        assertFalse(tooEarly.isHit());

        Map<String, Double> lateSession = new HashMap<>(earlySession);
        lateSession.put("rpgstations:cycle_count", 30.0);
        // Conditions pass (30 >= 10); Chance 15% - a sample under it hits; the ladder resolves T2 (30 >= 25).
        RollEvaluator.Outcome lateHit = RollEvaluator.evaluate(ladderRoll, lookup(lateSession), fixedRoll(14.999));
        assertTrue(lateHit.isHit());
        assertNotNull(lateHit.getFloorGrants());
        assertEquals("RPG_Station_Sawmill_T2", lateHit.getFloorGrants().getDropList());
    }
}
