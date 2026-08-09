package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.ziggfreed.rpgstations.asset.Roll;

/**
 * The PURE fractional-to-whole-items resolution: the whole part every time, the leftover fraction as
 * the chance of exactly one more. Driven with a FIXED roll source, so every case here is
 * deterministic and the "half the time" claim is checked at both sides of the boundary rather than
 * sampled.
 *
 * <p>Fixture amounts are this test's own; they deliberately do not mirror any shipped content.
 */
class OutputItemResolverTest {

    private static final Player NULL_PLAYER = null;

    /** A roll source that can never land under a fraction, so only unconditional grants survive it. */
    private static final DoubleSupplier NEVER_UNDER = () -> 1.0;

    private static DoubleSupplier fixed(double sample) {
        return () -> sample;
    }

    /** Replays {@code samples} in order, so a "one resolution per cycle" claim can be pinned down. */
    private static DoubleSupplier sequence(double... samples) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double s : samples) {
            queue.add(s);
        }
        return () -> queue.isEmpty() ? 1.0 : queue.poll();
    }

    @Test
    void wholeAmount_grantsEveryUnitAndNeverConsultsTheRoll() {
        assertEquals(3, OutputItemResolver.resolve(3.0, NEVER_UNDER));
        assertEquals(1, OutputItemResolver.resolve(1.0, NEVER_UNDER));
    }

    @Test
    void fractionalAmount_grantsTheWholePartAlways() {
        assertEquals(1, OutputItemResolver.resolve(1.5, NEVER_UNDER),
                "the whole part is unconditional; only the extra rides the roll");
        assertEquals(2, OutputItemResolver.resolve(2.25, NEVER_UNDER));
    }

    @Test
    void fractionalAmount_grantsOneMoreExactlyWhenTheRollLandsUnderTheFraction() {
        assertEquals(2, OutputItemResolver.resolve(1.5, fixed(0.49)));
        assertEquals(1, OutputItemResolver.resolve(1.5, fixed(0.5)),
                "the sample is a [0,1) draw compared strictly below the fraction");
        assertEquals(1, OutputItemResolver.resolve(1.5, fixed(0.99)));
    }

    @Test
    void fractionOnlyAmount_isOneItemOrNone() {
        assertEquals(1, OutputItemResolver.resolve(0.25, fixed(0.2)));
        assertEquals(0, OutputItemResolver.resolve(0.25, fixed(0.25)));
    }

    @Test
    void oneResolutionPerCycle_consultsTheRollAtMostOnce() {
        // The second sample must never be reached: a cycle resolves its SUMMED tally once, so two
        // halves cannot each draw (and each round to nothing).
        assertEquals(1, OutputItemResolver.resolve(0.5, sequence(0.4, 0.4)));
    }

    /**
     * The summing contract this resolution exists for: two rolls paying half an item each average a
     * whole one, because the caller sums first and resolves once. Resolving per roll would round
     * twice and average either zero or two.
     */
    @Test
    void twoHalfGrantsSumToAWholeItemBeforeResolution() {
        LootEngine.GrantResult result = new LootEngine.GrantResult();
        LootEngine.applyGrants(Roll.Grants.ofOutputItems(0.5), NULL_PLAYER, null, result, null, 0, 0, 0, true);
        LootEngine.applyGrants(Roll.Grants.ofOutputItems(0.5), NULL_PLAYER, null, result, null, 0, 0, 0, true);

        assertEquals(1.0, result.getOutputItems(), 1e-9);
        assertEquals(1, OutputItemResolver.resolve(result.getOutputItems(), NEVER_UNDER),
                "the summed whole item is granted outright, with no roll involved at all");
    }

    @Test
    void zeroNegativeAndNonFiniteAmounts_grantNothing() {
        assertEquals(0, OutputItemResolver.resolve(0.0, fixed(0.0)));
        assertEquals(0, OutputItemResolver.resolve(-2.5, fixed(0.0)));
        assertEquals(0, OutputItemResolver.resolve(Double.NaN, fixed(0.0)));
        assertEquals(0, OutputItemResolver.resolve(Double.POSITIVE_INFINITY, fixed(0.0)));
    }
}
