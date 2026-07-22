package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.rpgstations.api.StampInspection;
import com.ziggfreed.rpgstations.api.StatRoll;
import com.ziggfreed.rpgstations.asset.RollPool;
import com.ziggfreed.rpgstations.asset.StatRollEntry;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.RollPoolCatalog;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The PURE decision core for a Stamp step's {@code Stats} leaf (design section 9.5, critique
 * M2's binding fixes): resolves candidate roll entries, picks + rolls point values, then clamps
 * against every authored cap - zero store/api access, unit-tested with an injected RNG and a
 * fixture {@link StampInspection} (mirrors {@code loot.RollEvaluator}'s role for the {@code Loot}
 * group). {@code station.StationStepHandlers.StampHandler} calls this during the Stamp step's
 * COMPUTE phase (no mutation) and only proceeds to the commit phase when {@link Plan#denied()} is
 * false.
 *
 * <p><b>M2 cap-composition rule (binding, the ONE place this is implemented):</b> the effective
 * total-point budget is the MIN of every authored total-budget cap ({@code PerItemBudget} and/or
 * {@code SkillScaledBudget}'s computed value) - never their max, never their sum. {@code PerStat}
 * is a separate, additional per-stat-id ceiling layered on top. A roll that clamps to nothing
 * (every candidate entry clamped to zero points, or nothing was ever rolled) is a FULLY-CAPPED
 * deny ({@link Plan#denied()} true) - the caller consumes zero reagents and mutates nothing.
 */
final class StampCapEngine {

    private StampCapEngine() {
    }

    /** Uniform sample in {@code [0,1)}, injected so tests are deterministic (mirrors {@code RollEvaluator}'s {@code DoubleSupplier}). */
    @FunctionalInterface
    interface RollSource {
        double next();
    }

    @FunctionalInterface
    interface FactorLookup {
        @Nullable
        Double resolve(@Nonnull String factorId, @Nullable String param);
    }

    /** The finished, cap-clamped roll ready for {@code api.EnhanceStamper#apply}, or a fully-capped denial. */
    record Plan(@Nonnull List<StatRoll> entries, boolean denied) {
        static final Plan NOTHING_TO_GRANT = new Plan(List.of(), false);
    }

    @Nonnull
    static Plan resolve(@Nonnull StationStep.Stamp.Stats stats, @Nonnull StampInspection inspection,
            @Nonnull FactorLookup factorLookup, @Nonnull RollSource rng) {
        List<StatRollEntry> candidates = candidateEntries(stats);
        if (candidates.isEmpty()) {
            return Plan.NOTHING_TO_GRANT;
        }

        List<StatRollEntry> alwaysEntries = new ArrayList<>();
        List<StatRollEntry> weightedPool = new ArrayList<>();
        for (StatRollEntry e : candidates) {
            if (e == null || e.getStat() == null || e.getStat().isBlank()) {
                continue;
            }
            if (e.isAlways()) {
                alwaysEntries.add(e);
            } else {
                weightedPool.add(e);
            }
        }

        int pickCount = pickCount(stats.getPicks(), rng);
        boolean unique = stats.isUnique();
        List<StatRollEntry> picked = new ArrayList<>(alwaysEntries);
        List<StatRollEntry> pool = new ArrayList<>(weightedPool);
        for (int i = 0; i < pickCount && !pool.isEmpty(); i++) {
            StatRollEntry chosen = weightedPick(pool, rng);
            if (chosen == null) {
                break;
            }
            picked.add(chosen);
            if (unique) {
                pool.removeIf(e -> chosen.getStat().equalsIgnoreCase(e.getStat()));
            } else {
                pool.remove(chosen);
            }
        }
        if (unique) {
            picked = dedupeByStat(picked);
        }
        if (picked.isEmpty()) {
            return Plan.NOTHING_TO_GRANT;
        }

        List<StatRoll> rolled = new ArrayList<>(picked.size());
        for (StatRollEntry e : picked) {
            StatRollEntry.Points pts = e.getPoints();
            double min = pts != null ? pts.effectiveMin() : 1.0;
            double max = pts != null ? pts.effectiveMax() : min;
            double value = max > min ? min + rng.next() * (max - min) : min;
            int points = (int) Math.round(value);
            if (points > 0) {
                rolled.add(new StatRoll(e.getStat(), points));
            }
        }
        if (rolled.isEmpty()) {
            return Plan.NOTHING_TO_GRANT;
        }

        StationStep.Stamp.Stats.Caps caps = stats.getCaps();
        Double effectiveBudget = effectiveBudget(caps, factorLookup);
        Map<String, Double> perStat = caps != null && caps.getPerStat() != null ? caps.getPerStat() : Map.of();

        double runningTotal = 0.0;
        List<StatRoll> clamped = new ArrayList<>(rolled.size());
        for (StatRoll r : rolled) {
            double points = r.points();
            Double statCap = perStat.get(r.statId());
            if (statCap != null) {
                double already = inspection.pointsByStat().getOrDefault(r.statId(), 0);
                points = Math.min(points, Math.max(0.0, statCap - already));
            }
            if (effectiveBudget != null) {
                double remainingBudget = effectiveBudget - inspection.totalPoints() - runningTotal;
                points = Math.min(points, Math.max(0.0, remainingBudget));
            }
            int intPoints = (int) Math.floor(points);
            if (intPoints > 0) {
                clamped.add(new StatRoll(r.statId(), intPoints));
                runningTotal += intPoints;
            }
        }

        if (clamped.isEmpty()) {
            // Cap evaluation clamped the roll result to nothing (design 9.5): a fully-capped item
            // stamps nothing, consumes nothing - the caller denies the WHOLE Stamp step.
            return new Plan(List.of(), true);
        }
        return new Plan(List.copyOf(clamped), false);
    }

    /**
     * M2's binding rule: MIN of every authored total-budget cap. {@code null} = no total-budget
     * cap authored at all (unlimited, PerStat may still bind).
     */
    @Nullable
    static Double effectiveBudget(@Nullable StationStep.Stamp.Stats.Caps caps, @Nonnull FactorLookup factorLookup) {
        if (caps == null) {
            return null;
        }
        Double min = caps.getPerItemBudget();
        StationStep.Stamp.Stats.SkillScaledBudget scaled = caps.getSkillScaledBudget();
        if (scaled != null && scaled.getPointsPerLevel() != null
                && scaled.getFactor() != null && !scaled.getFactor().isBlank()) {
            Double factorValue = factorLookup.resolve(scaled.getFactor(), scaled.getParam());
            double scaledBudget = scaled.getPointsPerLevel() * (factorValue != null ? factorValue : 0.0);
            min = min == null ? scaledBudget : Math.min(min, scaledBudget);
        }
        return min;
    }

    @Nonnull
    private static List<StatRollEntry> candidateEntries(@Nonnull StationStep.Stamp.Stats stats) {
        List<StatRollEntry> out = new ArrayList<>();
        String poolId = stats.getPool();
        if (poolId != null && !poolId.isBlank()) {
            RollPool pool = RollPoolCatalog.getInstance().get(poolId);
            if (pool != null && pool.getEntries() != null) {
                out.addAll(Arrays.asList(pool.getEntries()));
            } else {
                Log.fine("STAMP Stats references unknown RollPool '" + poolId + "'");
            }
        }
        if (stats.getEntries() != null) {
            out.addAll(Arrays.asList(stats.getEntries()));
        }
        return out;
    }

    /** {@code Picks} absent = no weighted picks (only {@code Always} entries land) - a deliberate authoring default. */
    private static int pickCount(@Nullable StationStep.Stamp.Stats.Picks picks, @Nonnull RollSource rng) {
        if (picks == null) {
            return 0;
        }
        int lo = picks.effectiveMin();
        int hi = picks.effectiveMax();
        if (hi <= lo) {
            return lo;
        }
        return lo + (int) Math.floor(rng.next() * (hi - lo + 1));
    }

    @Nullable
    private static StatRollEntry weightedPick(@Nonnull List<StatRollEntry> pool, @Nonnull RollSource rng) {
        double total = 0.0;
        for (StatRollEntry e : pool) {
            total += e.effectiveWeight();
        }
        if (total <= 0.0) {
            return null;
        }
        double r = rng.next() * total;
        double cumulative = 0.0;
        for (StatRollEntry e : pool) {
            cumulative += e.effectiveWeight();
            if (r < cumulative) {
                return e;
            }
        }
        return pool.get(pool.size() - 1);
    }

    @Nonnull
    private static List<StatRollEntry> dedupeByStat(@Nonnull List<StatRollEntry> in) {
        List<StatRollEntry> out = new ArrayList<>(in.size());
        Set<String> seen = new HashSet<>();
        for (StatRollEntry e : in) {
            String stat = e.getStat();
            if (stat != null && seen.add(stat.toLowerCase(Locale.ROOT))) {
                out.add(e);
            }
        }
        return out;
    }
}
