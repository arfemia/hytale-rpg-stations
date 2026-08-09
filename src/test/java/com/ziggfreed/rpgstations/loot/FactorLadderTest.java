package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.FactorRef;

/**
 * The ONE ladder core both ladder-shaped consumers call ({@code Roll.Ladder} via
 * {@link RollEvaluator} and an action's {@code ContributionScale} via
 * {@code station.ContributionScaling}), so the
 * three rules that used to differ between them are pinned exactly once here:
 * an absent/empty {@code Factors} array resolves 0, a {@code Min <= 0} floor is reachable, and an
 * equal-{@code Min} tie goes to the LAST authored floor.
 */
class FactorLadderTest {

    private static FactorRef ref(String factor, double weight) {
        return FactorRef.of(factor, null, weight);
    }

    @Test
    void value_isTheWeightedSum() {
        FactorRef[] factors = {ref("a", 10.0), ref("b", 0.5)};
        double v = FactorLadder.value(factors, (f, p) -> Map.of("a", 3.0, "b", 8.0).get(f));
        assertEquals(34.0, v, 1e-9, "10*3 + 0.5*8");
    }

    @Test
    void value_absentOrEmptyFactors_resolvesZero() {
        assertEquals(0.0, FactorLadder.value(null, (f, p) -> 5.0), 1e-9);
        assertEquals(0.0, FactorLadder.value(new FactorRef[0], (f, p) -> 5.0), 1e-9);
    }

    @Test
    void value_unresolvableFactorFailsClosedAtZero() {
        assertEquals(0.0, FactorLadder.value(new FactorRef[] {ref("missing", 4.0)}, (f, p) -> null), 1e-9);
    }

    @Test
    void highestFloorIndex_picksTheHighestReachedThreshold() {
        double[] mins = {10.0, 25.0, 40.0};
        assertEquals(FactorLadder.NO_FLOOR, FactorLadder.highestFloorIndex(mins, 9.9));
        assertEquals(0, FactorLadder.highestFloorIndex(mins, 10.0), "inclusive at the threshold");
        assertEquals(1, FactorLadder.highestFloorIndex(mins, 39.9));
        assertEquals(2, FactorLadder.highestFloorIndex(mins, 1000.0), "deliberately uncapped at the top");
    }

    @Test
    void highestFloorIndex_zeroThresholdIsReachable() {
        assertEquals(0, FactorLadder.highestFloorIndex(new double[] {0.0}, 0.0),
                "a baseline tier an author writes as Min 0 must actually apply");
    }

    @Test
    void highestFloorIndex_negativeThresholdIsReachable() {
        assertEquals(0, FactorLadder.highestFloorIndex(new double[] {-5.0}, 0.0));
    }

    @Test
    void highestFloorIndex_equalMinTie_lastAuthoredWins() {
        assertEquals(2, FactorLadder.highestFloorIndex(new double[] {10.0, 20.0, 20.0}, 50.0),
                "later-wins, matching every other later-wins rule in this schema");
    }

    @Test
    void highestFloorIndex_emptyFloors_reachesNothing() {
        assertEquals(FactorLadder.NO_FLOOR, FactorLadder.highestFloorIndex(new double[0], 100.0));
    }
}
