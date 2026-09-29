package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.factor.FactorFormula;

/**
 * The action-level PACE of a {@code Steps} program: a factor ladder ({@link #ladder}, the very
 * same {@code {Factors, Floors [{Min, Scale}]}} codec a {@code ContributionScale} is) whose reached
 * floor multiplies the {@code Duration} of every step marked {@code Paced}, held inside
 * {@link #clamp} at both ends. Below a reached floor the pace is the neutral {@code 1.0}, so an
 * untrained worker runs the program at its authored length and a floor's {@code Scale} of
 * {@code 0.5} halves every paced beat.
 *
 * <p><b>Ladders multiply, and the action's clamp holds the range.</b> An {@code ExtensionAsset}
 * targeting the action may carry a complete {@code Pace} ladder of its own; each ladder resolves
 * to its own scale from its own thresholds, the scales MULTIPLY, and THIS action's {@link #clamp}
 * bounds the product. A pack's ladder over a factor of its own therefore composes with the jar's
 * ladder without either restating the other, and neither can push a beat past the bounds the
 * action set. The action's {@code Clamp} is where the whole range lives, so author BOTH sides on
 * an action's pace (the validator warns {@code PACE_UNCLAMPED} on a missing one); an extension's
 * payload carries no {@code Clamp} at all ({@link #LADDER_ONLY_CODEC}).
 *
 * <p><b>Resolved once per step entry</b> and cached on the session, so a resume never re-scales a
 * deadline already committed. Within a paced beat the scale also stretches that beat's own
 * presentation timing ({@code DelayMs} and each burst's {@code DurationSeconds}), so its accents
 * keep their place inside the beat.
 *
 * <pre>{@code
 * "Pace": {
 *   "Ladder": {
 *     "Factors": [ { "Factor": "hytale:stat", "Param": "Your_Proficiency_Stat" } ],
 *     "Floors":  [ { "Min": 10, "Scale": 0.8 }, { "Min": 40, "Scale": 0.5 } ]
 *   },
 *   "Clamp": { "Min": 0.375, "Max": 1.0 }
 * }
 * }</pre>
 */
public final class Pace {

    @Nullable protected ContributionScale ladder;
    @Nullable protected FactorFormula.Clamp clamp;

    /** The action's pace: the ladder plus the {@code Clamp} that bounds the composed scale. */
    public static final BuilderCodec<Pace> CODEC = codec(true);

    /**
     * An extension's pace: {@code {Ladder}} only. An extension multiplies its own ladder in and
     * never bounds the product, so its payload carries no {@code Clamp} leaf to author.
     */
    public static final BuilderCodec<Pace> LADDER_ONLY_CODEC = codec(false);

    /**
     * ONE codec definition for both shapes: {@code withClamp} adds the {@code Clamp} leaf the action
     * owns, and the extension shape omits it.
     */
    @Nonnull
    private static BuilderCodec<Pace> codec(boolean withClamp) {
        BuilderCodec.Builder<Pace> builder = BuilderCodec.builder(Pace.class, Pace::new)
                .appendInherited(new KeyedCodec<>("Ladder", ContributionScale.CODEC, false),
                        (o, v) -> o.ladder = v, o -> o.ladder, (o, p) -> o.ladder = p.ladder)
                .documentation("The factor ladder (Factors summed, the highest reached Floor's Scale wins; none reached = 1.0) whose scale multiplies the Duration of every step marked Paced. The same shape as ContributionScale.").add();
        if (withClamp) {
            builder = builder.appendInherited(new KeyedCodec<>("Clamp", FactorFormula.Clamp.CODEC, false),
                            (o, v) -> o.clamp = v, o -> o.clamp, (o, p) -> o.clamp = p.clamp)
                    .documentation("Bounds on the FINAL pace scale after every ladder (this action's and each matching extension's) has multiplied in: Min is the fastest a paced beat may run, Max the slowest. Author both sides; the action's clamp is the one place the pace range lives (PACE_UNCLAMPED warns on a missing side).").add();
        }
        return builder.build();
    }

    public Pace() {
    }

    @Nonnull
    public static Pace of(@Nullable ContributionScale ladder, @Nullable FactorFormula.Clamp clamp) {
        Pace p = new Pace();
        p.ladder = ladder;
        p.clamp = clamp;
        return p;
    }

    /** The action's own ladder; null = this action contributes the neutral 1.0 (an extension's ladder may still). */
    @Nullable
    public ContributionScale getLadder() {
        return ladder;
    }

    /** The bounds on the composed pace; null = unbounded (always null on an extension's pace). */
    @Nullable
    public FactorFormula.Clamp getClamp() {
        return clamp;
    }

    /** True when the clamp bounds BOTH ends: the shape an action's pace should author. */
    public boolean isFullyClamped() {
        return clamp != null && clamp.getMin() != null && clamp.getMax() != null;
    }
}
