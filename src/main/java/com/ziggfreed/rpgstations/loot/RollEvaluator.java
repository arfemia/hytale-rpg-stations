package com.ziggfreed.rpgstations.loot;

import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Roll;
// FactorMath is in this same package (loot) - no import needed.

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
 *       {@link Roll#getLadder()}; absent = deterministic pass. A failed roll means the Ladder is
 *       never even evaluated.</li>
 *   <li>Top-level {@link Roll#getGrants()} applies whenever steps 1 and 2 both pass (regardless of
 *       whether a Ladder floor is also reached).</li>
 *   <li>{@link Roll#getLadder()}, if present, sums its {@code Factors} and finds the HIGHEST
 *       reached floor through the shared {@link FactorLadder} core; that floor's OWN {@code Grants}
 *       applies TOO (top and floor grants STACK, they are not exclusive alternatives).</li>
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

    /**
     * Absent = always (deterministic pass); {@code effective = clamp(BasePercent + sum(resolve(f) *
     * f.Weight for f in Factors), 0, CapPercent)}. Public because it is the ONE chance-gate
     * authority in this mod: every {@code Roll.Chance} in the schema - an action's {@code Bonus},
     * a standalone lootable's rolls, a step's own {@code Roll} phase - reuses this exact type AND
     * this exact evaluation, so a chance authored at any site behaves identically.
     */
    public static boolean chancePasses(@Nullable Roll.Chance chance, @Nonnull FactorLookup lookup,
            @Nonnull DoubleSupplier chanceRoll) {
        if (chance == null) {
            return true;
        }
        double base = chance.getBasePercent() != null ? chance.getBasePercent() : 0.0;
        double sum = FactorMath.sum(chance.getFactors(), lookup::resolve);
        double cap = chance.getCapPercent() != null ? chance.getCapPercent() : 100.0;
        double effective = clamp(base + sum, 0.0, cap);
        if (effective <= 0.0) {
            return false;
        }
        return chanceRoll.getAsDouble() < effective;
    }

    /**
     * The reached {@link Roll.Ladder.Floor}, resolved through the ONE shared {@link FactorLadder}
     * core so a loot ladder and an action's {@code ContributionScale} ladder behave identically on
     * identical JSON:
     * the ladder value is the weighted {@code Factors} sum (an absent/empty array resolving to
     * {@code 0}), each floor's threshold is its reader-defaulted {@code effectiveMin()} (so a
     * {@code Min: 0} baseline tier IS reachable), and an equal-{@code Min} tie goes to the LAST
     * authored floor. {@code null} when {@code floors} is null/empty or nothing is reached.
     */
    @Nullable
    static Roll.Ladder.Floor highestFloor(@Nonnull Roll.Ladder ladder, @Nonnull FactorLookup lookup) {
        Roll.Ladder.Floor[] floors = ladder.getFloors();
        if (floors == null || floors.length == 0) {
            return null;
        }
        double resolved = FactorLadder.value(ladder.getFactors(), lookup::resolve);
        double[] mins = new double[floors.length];
        for (int i = 0; i < floors.length; i++) {
            // A null hole can never win: push its threshold above every reachable value.
            mins[i] = floors[i] != null ? floors[i].effectiveMin() : Double.POSITIVE_INFINITY;
        }
        int index = FactorLadder.highestFloorIndex(mins, resolved);
        return index == FactorLadder.NO_FLOOR ? null : floors[index];
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
