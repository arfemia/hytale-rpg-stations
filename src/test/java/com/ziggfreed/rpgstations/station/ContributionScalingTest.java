package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.FactorRef;

/**
 * The PURE {@link ContributionScaling} resolution: an action's {@code ContributionScale} ladder into
 * ONE multiplier, over the same {@code loot.FactorLadder} core every other ladder uses. Fixture
 * factors and thresholds are authored by this test.
 */
public class ContributionScalingTest {

    /** A factor lookup over an authored {@code factorId -> value} table; anything absent is unresolvable. */
    private static BiFunction<String, String, Double> lookup(Map<String, Double> values) {
        return (factorId, param) -> values.get(factorId);
    }

    private static ContributionScale scale(FactorRef[] factors, ContributionScale.Floor... floors) {
        return ContributionScale.of(factors, floors);
    }

    private static final FactorRef AXIS = FactorRef.of("fixture:axis", null, 1.0);

    // ==================== The neutral cases ====================

    @Test
    void nullLadder_isTheNeutralMultiplier() {
        assertEquals(ContributionScale.NEUTRAL_SCALE,
                ContributionScaling.multiplier(null, lookup(Map.of("fixture:axis", 99.0))));
    }

    @Test
    void ladderWithNoFloors_isTheNeutralMultiplier() {
        assertEquals(ContributionScale.NEUTRAL_SCALE,
                ContributionScaling.multiplier(scale(new FactorRef[] {AXIS}),
                        lookup(Map.of("fixture:axis", 99.0))));
    }

    @Test
    void noFloorReached_isTheNeutralMultiplier() {
        ContributionScale s = scale(new FactorRef[] {AXIS}, ContributionScale.Floor.of(10.0, 3.0));
        assertEquals(ContributionScale.NEUTRAL_SCALE,
                ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 4.0))));
    }

    // ==================== The ladder itself ====================

    @Test
    void theHighestReachedFloorSuppliesTheMultiplier() {
        ContributionScale s = scale(new FactorRef[] {AXIS},
                ContributionScale.Floor.of(10.0, 2.0),
                ContributionScale.Floor.of(20.0, 3.0),
                ContributionScale.Floor.of(30.0, 4.0));

        assertEquals(2.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 10.0))));
        assertEquals(2.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 19.9))));
        assertEquals(3.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 25.0))));
        assertEquals(4.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 500.0))),
                "the ladder is uncapped at the top");
    }

    @Test
    void floorsAreNotCumulative_theHighestOneWinsOutright() {
        ContributionScale s = scale(new FactorRef[] {AXIS},
                ContributionScale.Floor.of(1.0, 2.0),
                ContributionScale.Floor.of(2.0, 3.0));
        assertEquals(3.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 5.0))),
                "3.0, not 2.0 x 3.0");
    }

    @Test
    void factorsAreSummedWithTheirWeightsBeforeTheFloorLookup() {
        ContributionScale s = scale(new FactorRef[] {
                        FactorRef.of("fixture:quality", null, 10.0),
                        FactorRef.of("fixture:power", null, 1.0)},
                ContributionScale.Floor.of(11.0, 2.5));
        // 1 x 10.0 + 0.5 x 1.0 = 10.5, one rung short.
        assertEquals(ContributionScale.NEUTRAL_SCALE, ContributionScaling.multiplier(s,
                lookup(Map.of("fixture:quality", 1.0, "fixture:power", 0.5))));
        // 1 x 10.0 + 1.5 x 1.0 = 11.5, over the threshold.
        assertEquals(2.5, ContributionScaling.multiplier(s,
                lookup(Map.of("fixture:quality", 1.0, "fixture:power", 1.5))));
    }

    @Test
    void anUnresolvableFactorContributesZero_soAMissingProviderNeverInflatesTheMultiplier() {
        ContributionScale s = scale(new FactorRef[] {AXIS}, ContributionScale.Floor.of(1.0, 5.0));
        assertEquals(ContributionScale.NEUTRAL_SCALE, ContributionScaling.multiplier(s, lookup(Map.of())));
    }

    @Test
    void aZeroMinFloorIsReachable_soABaselineTierIsAuthorable() {
        ContributionScale s = scale(null, ContributionScale.Floor.of(0.0, 1.5));
        assertEquals(1.5, ContributionScaling.multiplier(s, lookup(Map.of())),
                "an absent Factors array resolves the ladder value to 0, which reaches a Min-0 floor");
    }

    @Test
    void equalMinFloors_resolveToTheLastAuthoredOne() {
        ContributionScale s = scale(new FactorRef[] {AXIS},
                ContributionScale.Floor.of(5.0, 2.0),
                ContributionScale.Floor.of(5.0, 7.0));
        assertEquals(7.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 5.0))),
                "the shared later-wins rule (the validator warns about the duplicate)");
    }

    @Test
    void aFloorAuthoringNoScale_isTheNeutralMultiplierRatherThanZero() {
        ContributionScale s = scale(new FactorRef[] {AXIS}, ContributionScale.Floor.of(1.0, null));
        assertEquals(ContributionScale.NEUTRAL_SCALE,
                ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 5.0))));
    }

    @Test
    void aNullHoleInTheFloorsArray_canNeverWin() {
        ContributionScale s = ContributionScale.of(new FactorRef[] {AXIS},
                new ContributionScale.Floor[] {ContributionScale.Floor.of(1.0, 2.0), null});
        assertEquals(2.0, ContributionScaling.multiplier(s, lookup(Map.of("fixture:axis", 99.0))));
    }
}
