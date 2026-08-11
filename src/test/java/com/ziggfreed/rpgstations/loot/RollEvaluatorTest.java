package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.factor.FactorCondition;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.Roll;

/**
 * Pure {@link RollEvaluator} coverage over the scope-2 weighted-{@link FactorRef} vocabulary:
 * {@code Chance.AddFactors} and {@code Ladder.Values} are now {@link FactorRef}{@code []} summed via
 * {@link FactorMath} ({@code sum(resolve(Factor,Param) * Weight)}). A test-authored fixture SHAPED
 * like the shipped standalone sawmill's {@code Loot} exercises the integration (structure only,
 * never production balance numbers - the repo's no-balance-tests rule).
 */
public class RollEvaluatorTest {

    private static RollEvaluator.FactorLookup lookup(Map<String, Double> values) {
        return (factorId, param) -> values.get(factorId);
    }

    private static java.util.function.DoubleSupplier fixedRoll(double value) {
        return () -> value;
    }

    private static FactorRef ref(String factor) {
        return FactorRef.of(factor, null);
    }

    private static FactorRef[] refs(String... factors) {
        FactorRef[] out = new FactorRef[factors.length];
        for (int i = 0; i < factors.length; i++) {
            out[i] = ref(factors[i]);
        }
        return out;
    }

    // ==================== chancePasses (scope-2: AddFactors is a weighted FactorRef[], summed) ====================

    @Test
    void chancePasses_absentChance_alwaysPasses() {
        assertTrue(RollEvaluator.chancePasses(null, lookup(Map.of()), fixedRoll(99.0)));
    }

    @Test
    void chancePasses_sumsEveryAddFactorEntry() {
        Map<String, Double> values = Map.of("hytale:tool_power", 5.0, "rpgstations:cycle_count", 3.0);
        Roll.Chance chance = Roll.Chance.of(2.0, refs("hytale:tool_power", "rpgstations:cycle_count"), 100.0);
        assertTrue(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(9.999)));
        assertFalse(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(10.0)));
    }

    @Test
    void chancePasses_weightScalesTheContribution() {
        // A weight-0.5 factor over a value of 10 contributes 5, onto a base of 2 -> 7% effective.
        Roll.Chance chance = Roll.Chance.of(2.0, new FactorRef[]{FactorRef.of("f", null, 0.5)}, 100.0);
        Map<String, Double> values = Map.of("f", 10.0);
        assertTrue(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(6.999)));
        assertFalse(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(7.0)));
    }

    @Test
    void chancePasses_clampsToCapPercent() {
        Roll.Chance chance = Roll.Chance.of(50.0, refs("f"), 60.0);
        Map<String, Double> values = Map.of("f", 1000.0);
        assertTrue(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(59.999)));
        assertFalse(RollEvaluator.chancePasses(chance, lookup(values), fixedRoll(60.0)));
    }

    @Test
    void chancePasses_nonpositiveEffective_neverHits() {
        assertFalse(RollEvaluator.chancePasses(Roll.Chance.of(0.0, null, 100.0), lookup(Map.of()), fixedRoll(0.0)));
    }

    @Test
    void chancePasses_unresolvableAddFactor_contributesZero() {
        Roll.Chance chance = Roll.Chance.of(5.0, refs("rpgstations:unknown"), 100.0);
        assertTrue(RollEvaluator.chancePasses(chance, lookup(Map.of()), fixedRoll(4.999)));
    }

    // ==================== highestFloor (Ladder.Values summed before the floor lookup) ====================

    @Test
    void highestFloor_picksTheHighestReachedFloor() {
        Roll.Ladder.Floor low = Roll.Ladder.Floor.of(10.0, Roll.Grants.ofDropList("T1"), null);
        Roll.Ladder.Floor high = Roll.Ladder.Floor.of(25.0, Roll.Grants.ofDropList("T2"), null);
        Roll.Ladder ladder = Roll.Ladder.of(refs("rpgstations:cycle_count"), new Roll.Ladder.Floor[]{low, high});
        assertEquals(high, RollEvaluator.highestFloor(ladder, lookup(Map.of("rpgstations:cycle_count", 30.0))));
        assertEquals(low, RollEvaluator.highestFloor(ladder, lookup(Map.of("rpgstations:cycle_count", 12.0))));
        assertNull(RollEvaluator.highestFloor(ladder, lookup(Map.of("rpgstations:cycle_count", 1.0))));
    }

    @Test
    void highestFloor_summedValues_composeMultipleChannels() {
        // Two channels 8 + 5 = 13 clears the 10 floor a single channel would miss.
        Roll.Ladder.Floor f = Roll.Ladder.Floor.of(10.0, Roll.Grants.ofDropList("T1"), null);
        Roll.Ladder ladder = Roll.Ladder.of(refs("a", "b"), new Roll.Ladder.Floor[]{f});
        assertEquals(f, RollEvaluator.highestFloor(ladder, lookup(Map.of("a", 8.0, "b", 5.0))));
    }

    @Test
    void highestFloor_unresolvableValueFactor_reachesNoFloor() {
        Roll.Ladder ladder = Roll.Ladder.of(refs("rpgstations:unknown"),
                new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, Roll.Grants.ofDropList("T1"), null)});
        assertNull(RollEvaluator.highestFloor(ladder, lookup(Map.of())), "an unresolvable factor sums to 0 - no floor reached");
    }

    @Test
    void highestFloor_malformedFloor_isSkippedNotThrown() {
        Roll.Ladder.Floor malformed = Roll.Ladder.Floor.of(null, Roll.Grants.ofDropList("T1"), null);
        Roll.Ladder.Floor valid = Roll.Ladder.Floor.of(5.0, Roll.Grants.ofDropList("T2"), null);
        Roll.Ladder ladder = Roll.Ladder.of(refs("f"), new Roll.Ladder.Floor[]{malformed, valid});
        assertEquals(valid, RollEvaluator.highestFloor(ladder, lookup(Map.of("f", 10.0))));
    }

    // ==================== evaluate() - the M3-fixed integration semantics ====================

    @Test
    void evaluate_conditionsFail_producesNone() {
        Roll roll = Roll.of("Cycle", new FactorCondition[]{FactorCondition.of("rpgstations:unknown", null, null, null)},
                null, null, Roll.Grants.of(null, null));
        RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup(Map.of()), fixedRoll(0.0));
        assertFalse(outcome.isHit());
        assertNull(outcome.getTopGrants());
    }

    @Test
    void evaluate_chanceFails_killsTheWholeRoll_ladderIncluded() {
        Roll.Ladder ladder = Roll.Ladder.of(refs("rpgstations:cycle_count"),
                new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, Roll.Grants.ofDropList("T1"), null)});
        Roll roll = Roll.of("Cycle", null, Roll.Chance.of(0.0, null, 100.0), ladder, null);
        RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup(Map.of("rpgstations:cycle_count", 999.0)),
                fixedRoll(0.0));
        assertFalse(outcome.isHit());
        assertNull(outcome.getFloorGrants());
    }

    @Test
    void evaluate_topGrantsAndFloorGrants_stack() {
        Roll.Grants top = Roll.Grants.of(null, null);
        Roll.Grants floorGrants = Roll.Grants.ofDropList("T1");
        Roll.Ladder ladder = Roll.Ladder.of(refs("rpgstations:cycle_count"),
                new Roll.Ladder.Floor[]{Roll.Ladder.Floor.of(1.0, floorGrants, null)});
        Roll roll = Roll.of("Cycle", null, null, ladder, top);
        RollEvaluator.Outcome outcome = RollEvaluator.evaluate(roll, lookup(Map.of("rpgstations:cycle_count", 5.0)),
                fixedRoll(0.0));
        assertTrue(outcome.isHit());
        assertEquals(top, outcome.getTopGrants());
        assertEquals(floorGrants, outcome.getFloorGrants());
    }

    @Test
    void sawmillShapedFixture_chanceGatedRoll_andGatedLadderRoll() {
        Roll chanceRoll = Roll.of("Cycle", null,
                Roll.Chance.of(2.0, refs("hytale:tool_power"), 25.0),
                null, Roll.Grants.ofDropList("Fixture_Bonus"));

        Roll.Grants t1 = Roll.Grants.ofDropList("RPG_Station_Sawmill_T1");
        Roll.Grants t2 = Roll.Grants.ofDropList("RPG_Station_Sawmill_T2");
        Roll.Ladder ladder = Roll.Ladder.of(refs("rpgstations:cycle_count"),
                new Roll.Ladder.Floor[]{
                        Roll.Ladder.Floor.of(10.0, t1, null),
                        Roll.Ladder.Floor.of(25.0, t2, null)
                });
        Roll ladderRoll = Roll.of("Cycle",
                new FactorCondition[]{FactorCondition.of("rpgstations:cycle_count", null, 10.0, null)},
                Roll.Chance.of(15.0, null, 100.0), ladder, null);

        Map<String, Double> earlySession = new HashMap<>();
        earlySession.put("hytale:tool_power", 0.5);
        earlySession.put("rpgstations:cycle_count", 3.0);

        RollEvaluator.Outcome chanceHit = RollEvaluator.evaluate(chanceRoll, lookup(earlySession), fixedRoll(2.0));
        assertTrue(chanceHit.isHit());
        assertEquals("Fixture_Bonus", chanceHit.getTopGrants().getDropLists()[0]);

        RollEvaluator.Outcome tooEarly = RollEvaluator.evaluate(ladderRoll, lookup(earlySession), fixedRoll(0.0));
        assertFalse(tooEarly.isHit());

        Map<String, Double> lateSession = new HashMap<>(earlySession);
        lateSession.put("rpgstations:cycle_count", 30.0);
        RollEvaluator.Outcome lateHit = RollEvaluator.evaluate(ladderRoll, lookup(lateSession), fixedRoll(14.999));
        assertTrue(lateHit.isHit());
        assertNotNull(lateHit.getFloorGrants());
        assertEquals("RPG_Station_Sawmill_T2", lateHit.getFloorGrants().getDropLists()[0]);
    }
}
