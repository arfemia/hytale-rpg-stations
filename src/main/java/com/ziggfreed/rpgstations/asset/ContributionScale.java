package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * The per-action multiplier ladder over an action's {@code Work.PerCycleContributions} amounts:
 * sum {@link #factors} (the shared weighted {@link FactorRef} sum), look the result up against
 * {@link #floors}, and multiply every per-cycle amount by the reached floor's {@code Scale}. No
 * floor reached (or no ladder authored at all) is the neutral {@code 1.0}.
 *
 * <p><b>It is the SAME ladder core every other ladder in this schema uses</b>
 * ({@code loot.FactorLadder}, shared with {@code Roll.Ladder}), so identical {@code {Factors,
 * Floors}} JSON behaves identically here: an absent/empty {@code Factors} resolves the value to
 * {@code 0.0}, a floor's {@code Min} reader-defaults to {@code 0} and a {@code Min <= 0} floor is
 * reachable, and two floors sharing a {@code Min} resolve to the LAST authored one (the validator
 * warns about the duplicate).
 *
 * <p><b>The engine PRE-SCALES.</b> The resolved multiplier is applied to each forwarded amount
 * before the cycle-completed event is dispatched, and the multiplier itself rides that event for
 * DISPLAY only. A listener that forgot to multiply therefore cannot under-award, and one that
 * multiplied again cannot over-award - the amount on the event is always the amount to grant.
 *
 * <p>Because any installed mod registers its own factors into the one shared registry, a single
 * authored ladder may name engine {@code hytale:}/{@code rpgstations:} factors AND a third party's
 * own {@code yourmod:} factors side by side.
 *
 * <pre>{@code
 * "ContributionScale": {
 *   "Factors": [ { "Factor": "hytale:tool_quality", "Weight": 10.0 } ],
 *   "Floors":  [ { "Min": 11, "Scale": 1.25 }, { "Min": 33, "Scale": 1.5 } ]
 * }
 * }</pre>
 */
public final class ContributionScale {

    /** The multiplier when no ladder is authored, or no floor is reached (neutral). */
    public static final double NEUTRAL_SCALE = 1.0;

    @Nullable protected FactorRef[] factors;
    @Nullable protected Floor[] floors;

    public static final BuilderCodec<ContributionScale> CODEC =
            BuilderCodec.builder(ContributionScale.class, ContributionScale::new)
                    .appendInherited(new KeyedCodec<>("Factors",
                                    new ArrayCodec<>(FactorRef.CODEC, FactorRef[]::new), false),
                            (o, v) -> o.factors = v, o -> o.factors, (o, p) -> o.factors = p.factors)
                    .documentation("Weighted factor references SUMMED to the ladder value before the floor lookup; a single-factor ladder is a one-element array, and an empty one resolves to 0.").add()
                    .appendInherited(new KeyedCodec<>("Floors",
                                    new ArrayCodec<>(Floor.CODEC, Floor[]::new), false),
                            (o, v) -> o.floors = v, o -> o.floors, (o, p) -> o.floors = p.floors)
                    .documentation("The multiplier floors; the HIGHEST floor whose Min is reached supplies the multiplier. Empty, or none reached, = the neutral 1.0.").add()
                    .build();

    public ContributionScale() {
    }

    @Nonnull
    public static ContributionScale of(@Nullable FactorRef[] factors, @Nullable Floor[] floors) {
        ContributionScale c = new ContributionScale();
        c.factors = factors;
        c.floors = floors;
        return c;
    }

    /** The weighted factor references summed to the ladder value; null/empty resolves to 0. */
    @Nullable
    public FactorRef[] getFactors() {
        return factors;
    }

    @Nullable
    public Floor[] getFloors() {
        return floors;
    }

    /** One {@code {Min, Scale}} multiplier threshold. */
    public static final class Floor {
        @Nullable protected Double min;
        @Nullable protected Double scale;

        public static final BuilderCodec<Floor> CODEC = BuilderCodec.builder(Floor.class, Floor::new)
                .appendInherited(new KeyedCodec<>("Min", Codec.DOUBLE, false),
                        (o, v) -> o.min = v, o -> o.min, (o, p) -> o.min = p.min)
                .documentation("The summed-value threshold this floor requires (inclusive); reader-defaults to 0, and a 0 threshold is reachable (the baseline tier).").add()
                .appendInherited(new KeyedCodec<>("Scale", Codec.DOUBLE, false),
                        (o, v) -> o.scale = v, o -> o.scale, (o, p) -> o.scale = p.scale)
                .documentation("The multiplier applied to every per-cycle contribution Amount while this floor is the reached one; reader-defaults to 1.0 (neutral).")
                .addValidator(CodecWarnValidators.nonNegative("ContributionScale.Floors[].Scale should not be negative.")).add()
                .build();

        @Nonnull
        public static Floor of(@Nullable Double min, @Nullable Double scale) {
            Floor f = new Floor();
            f.min = min;
            f.scale = scale;
            return f;
        }

        @Nullable
        public Double getMin() {
            return min;
        }

        @Nullable
        public Double getScale() {
            return scale;
        }

        /** Reader-defaulted threshold (0 when absent or non-finite) - the shared ladder rule. */
        public double effectiveMin() {
            return min != null && Double.isFinite(min) ? min : 0.0;
        }

        /** Reader-defaulted multiplier ({@link #NEUTRAL_SCALE} when absent or non-finite/negative). */
        public double effectiveScale() {
            return scale != null && Double.isFinite(scale) && scale >= 0.0 ? scale : NEUTRAL_SCALE;
        }
    }
}
