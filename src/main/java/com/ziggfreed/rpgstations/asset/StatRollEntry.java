package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * ONE candidate entry of a Stamp step's stat roll (design section 9.5's composable roll model):
 * {@code {Stat, Points{Min,Max}, Weight, Always}}. Shared verbatim by both authoring routes -
 * {@link RollPool#getEntries()} (a reusable {@code Server/RpgStations/RollPools/*.json} table) and
 * {@link StationStep.Stamp.Stats#getEntries()} (inline) - so the roll/cap engine
 * ({@code station.StampCapEngine}) never special-cases which route produced an entry.
 *
 * <p>{@link #stat} is an OPAQUE id to this engine (it never interprets what a "stat" means - the
 * registered {@code api.EnhanceStamper} does); {@link #points} is the point value RANGE a hit
 * rolls within (inclusive, a fixed value when {@code Min == Max}). {@link #weight} (default 1.0,
 * must be {@code > 0} to ever be picked by the weighted route) and {@link #always} (default false
 * - when {@code true} this entry is GRANTED unconditionally on every stamp, independent of the
 * weighted pool and {@code Picks} count, per design 9.5's "deterministic pick" model) are
 * independently composable, matching the three maintainer-required roll models: pure weighted
 * (weight-only entries), deterministic (all {@code Always:true}), or a mix of both.
 */
public final class StatRollEntry {

    @Nullable protected String stat;
    @Nullable protected Points points;
    @Nullable protected Double weight;
    @Nullable protected Boolean always;

    public static final BuilderCodec<StatRollEntry> CODEC =
            BuilderCodec.builder(StatRollEntry.class, StatRollEntry::new)
                    .appendInherited(new KeyedCodec<>("Stat", Codec.STRING, false),
                            (o, v) -> o.stat = v, o -> o.stat, (o, p) -> o.stat = p.stat).add()
                    .appendInherited(new KeyedCodec<>("Points", Points.CODEC, false),
                            (o, v) -> o.points = v, o -> o.points, (o, p) -> o.points = p.points).add()
                    .appendInherited(new KeyedCodec<>("Weight", Codec.DOUBLE, false),
                            (o, v) -> o.weight = v, o -> o.weight, (o, p) -> o.weight = p.weight).add()
                    .appendInherited(new KeyedCodec<>("Always", Codec.BOOLEAN, false),
                            (o, v) -> o.always = v, o -> o.always, (o, p) -> o.always = p.always).add()
                    .build();

    public StatRollEntry() {
    }

    @Nonnull
    public static StatRollEntry of(@Nullable String stat, @Nullable Points points, @Nullable Double weight,
            @Nullable Boolean always) {
        StatRollEntry e = new StatRollEntry();
        e.stat = stat;
        e.points = points;
        e.weight = weight;
        e.always = always;
        return e;
    }

    @Nullable
    public String getStat() {
        return stat;
    }

    @Nullable
    public Points getPoints() {
        return points;
    }

    /** {@link #weight}, reader-defaulted to 1.0 when null/non-positive. */
    public double effectiveWeight() {
        return weight != null && weight > 0 ? weight : 1.0;
    }

    /** {@link #always}, reader-defaulted to false. */
    public boolean isAlways() {
        return always != null && always;
    }

    /** The point value range a hit on this entry rolls within (inclusive; {@code Min==Max} = fixed). */
    public static final class Points {
        @Nullable protected Double min;
        @Nullable protected Double max;

        public static final BuilderCodec<Points> CODEC = BuilderCodec.builder(Points.class, Points::new)
                .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                        (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min).add()
                .appendInherited(new KeyedCodec<>("Max", Codec.DOUBLE, false),
                        (o, v) -> o.max = v, o -> o.max, (o, p) -> o.max = p.max).add()
                .build();

        @Nonnull
        public static Points of(@Nullable Double min, @Nullable Double max) {
            Points p = new Points();
            p.min = min;
            p.max = max;
            return p;
        }

        @Nullable
        public Double getMin() {
            return min;
        }

        @Nullable
        public Double getMax() {
            return max;
        }

        /** {@link #min}, reader-defaulted to 1.0 when null. */
        public double effectiveMin() {
            return min != null ? min : 1.0;
        }

        /** {@link #max}, reader-defaulted to {@link #effectiveMin()} when null (a fixed value). */
        public double effectiveMax() {
            double lo = effectiveMin();
            return max != null && max >= lo ? max : lo;
        }
    }
}
