package com.ziggfreed.rpgstations.loot;

import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;

/**
 * The PURE {@link Roll} decision core (design section 4.5.1, unit-tested without a live server
 * or store): given a Roll, an injected factor lookup, and an injected {@code [0,100)} chance
 * sample, decides WHETHER the roll hits and WHAT it grants. Store-touching grant EXECUTION
 * (inventory mutation, command dispatch, presentation playback) lives in {@link LootEngine}.
 *
 * <p>Evaluation order (binding, matches the M3 critique fixes on {@link Roll}'s javadoc):
 * <ol>
 *   <li>{@link Roll#getConditions()} - ALL must pass (absent = pass); a failing condition
 *       (unresolvable factor, or resolved value outside {@code Min}/{@code Max}) means the
 *       WHOLE roll produces nothing.</li>
 *   <li>{@link Roll#getChance()} - a probabilistic gate over the WHOLE roll INCLUDING its
 *       {@link Roll#getLadder()} (M3 fix 4); absent = deterministic pass. A failed roll means
 *       the Ladder is never even evaluated.</li>
 *   <li>Top-level {@link Roll#getGrants()} applies whenever step 1+2 both pass (regardless of
 *       whether a Ladder floor is also reached).</li>
 *   <li>{@link Roll#getLadder()}, if present, resolves its {@code Value} factor and finds the
 *       HIGHEST reached floor; that floor's OWN {@code Grants} applies TOO (M3 fix 3 - top and
 *       floor grants STACK, they are not exclusive alternatives).</li>
 * </ol>
 */
public final class RollEvaluator {

    private RollEvaluator() {
    }

    @FunctionalInterface
    public interface FactorLookup {
        /** {@code null} = the factor is unresolvable (unregistered provider, or a threw-and-caught one). */
        @Nullable
        Double resolve(@Nonnull String factorId, @Nullable String param);
    }

    /** The consolidated outcome of one evaluation: whether it hit, and what to grant/play. */
    public static final class Outcome {

        public static final Outcome NONE = new Outcome(false, null, null, null);

        private final boolean hit;
        @Nullable private final Roll.Grants topGrants;
        @Nullable private final Roll.Grants floorGrants;
        @Nullable private final Presentation floorPresentation;

        private Outcome(boolean hit, @Nullable Roll.Grants topGrants, @Nullable Roll.Grants floorGrants,
                @Nullable Presentation floorPresentation) {
            this.hit = hit;
            this.topGrants = topGrants;
            this.floorGrants = floorGrants;
            this.floorPresentation = floorPresentation;
        }

        /** True once Conditions + Chance both passed (a floor need not have been reached). */
        public boolean isHit() {
            return hit;
        }

        @Nullable
        public Roll.Grants getTopGrants() {
            return topGrants;
        }

        @Nullable
        public Roll.Grants getFloorGrants() {
            return floorGrants;
        }

        /** The reached floor's own moment (null when no Ladder, or no floor reached). */
        @Nullable
        public Presentation getFloorPresentation() {
            return floorPresentation;
        }
    }

    /**
     * Evaluate {@code roll} against {@code lookup} (a factor resolver, typically a {@link
     * FactorSnapshot#resolve}) and {@code chanceRoll} (returns a fresh uniform sample in
     * {@code [0,100)} each call - injected so tests are deterministic).
     */
    @Nonnull
    public static Outcome evaluate(@Nonnull Roll roll, @Nonnull FactorLookup lookup,
            @Nonnull DoubleSupplier chanceRoll) {
        if (!conditionsPass(roll.getConditions(), lookup)) {
            return Outcome.NONE;
        }
        if (!chancePasses(roll.getChance(), lookup, chanceRoll)) {
            return Outcome.NONE;
        }
        Roll.Grants topGrants = roll.getGrants();
        Roll.Ladder ladder = roll.getLadder();
        Roll.Ladder.Floor floor = ladder != null ? highestFloor(ladder, lookup) : null;
        Roll.Grants floorGrants = floor != null ? floor.getGrants() : null;
        Presentation floorPresentation = floor != null ? floor.getPresentation() : null;
        return new Outcome(true, topGrants, floorGrants, floorPresentation);
    }

    /** ALL conditions must pass; a null/empty array passes vacuously. Fails closed on an unresolvable factor. */
    static boolean conditionsPass(@Nullable Condition[] conditions, @Nonnull FactorLookup lookup) {
        if (conditions == null) {
            return true;
        }
        for (Condition c : conditions) {
            if (c == null) {
                continue;
            }
            if (!conditionPasses(c, lookup)) {
                return false;
            }
        }
        return true;
    }

    /** A blank {@code Factor} passes vacuously; an unresolvable one fails closed. */
    static boolean conditionPasses(@Nonnull Condition c, @Nonnull FactorLookup lookup) {
        String factorId = c.getFactor();
        if (factorId == null || factorId.isBlank()) {
            return true;
        }
        Double value = lookup.resolve(factorId, c.getParam());
        if (value == null) {
            return false;
        }
        if (c.getMin() != null && value < c.getMin()) {
            return false;
        }
        return c.getMax() == null || value <= c.getMax();
    }

    /** Absent = always (deterministic pass); {@code effective = clamp(BasePercent + sum(AddFactors), 0, CapPercent)}. */
    static boolean chancePasses(@Nullable Roll.Chance chance, @Nonnull FactorLookup lookup,
            @Nonnull DoubleSupplier chanceRoll) {
        if (chance == null) {
            return true;
        }
        double base = chance.getBasePercent() != null ? chance.getBasePercent() : 0.0;
        double sum = 0.0;
        Condition[] addFactors = chance.getAddFactors();
        if (addFactors != null) {
            for (Condition c : addFactors) {
                if (c == null || c.getFactor() == null || c.getFactor().isBlank()) {
                    continue;
                }
                Double v = lookup.resolve(c.getFactor(), c.getParam());
                sum += v != null ? v : 0.0;
            }
        }
        double cap = chance.getCapPercent() != null ? chance.getCapPercent() : 100.0;
        double effective = clamp(base + sum, 0.0, cap);
        if (effective <= 0.0) {
            return false;
        }
        return chanceRoll.getAsDouble() < effective;
    }

    /**
     * The highest-floor {@link Roll.Ladder.Floor} whose {@code Min <= resolved value}
     * (deliberately UNCAPPED); {@code null} when the value factor is unresolvable, no floor is
     * reached, or {@code floors} is null/empty. A malformed floor ({@code Min} null/nonpositive)
     * is SKIPPED, not thrown on - the validator catches the authoring mistake ahead of runtime.
     */
    @Nullable
    static Roll.Ladder.Floor highestFloor(@Nonnull Roll.Ladder ladder, @Nonnull FactorLookup lookup) {
        Condition valueRef = ladder.getValue();
        if (valueRef == null || valueRef.getFactor() == null || valueRef.getFactor().isBlank()) {
            return null;
        }
        Double resolved = lookup.resolve(valueRef.getFactor(), valueRef.getParam());
        if (resolved == null) {
            return null;
        }
        Roll.Ladder.Floor[] floors = ladder.getFloors();
        if (floors == null || floors.length == 0) {
            return null;
        }
        Roll.Ladder.Floor best = null;
        double bestFloor = Double.NEGATIVE_INFINITY;
        for (Roll.Ladder.Floor f : floors) {
            if (f == null) {
                continue;
            }
            Double min = f.getMin();
            if (min == null || min <= 0.0) {
                continue;
            }
            if (min <= resolved && min > bestFloor) {
                best = f;
                bestFloor = min;
            }
        }
        return best;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
