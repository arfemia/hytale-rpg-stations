package com.ziggfreed.rpgstations.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
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
 * <p><b>Ladders multiply, and the clamps hold the range.</b> An {@code ExtensionAsset} targeting
 * the action may carry a {@code Pace} of its own (the same shape); each ladder resolves to its own
 * scale from its own thresholds, the scales MULTIPLY, and THIS action's {@link #clamp} bounds the
 * product. A pack's ladder over a factor of its own therefore composes with the jar's ladder
 * without either restating the other, and none can push a beat past the bounds the action set.
 * The action's {@code Clamp} is where the whole range lives, so author BOTH sides on an action's
 * pace (the validator warns {@code PACE_UNCLAMPED} on a missing one). An extension's {@code Clamp}
 * only NARROWS that range, applied after the action's, so the tighter bound wins at each end and
 * one side is a complete statement: a pack that wants its own floor states just its {@code Min}.
 *
 * <p><b>{@link #stretch} sets how long the paced beats are to begin with.</b> It multiplies every
 * {@code Paced} beat AFTER the clamp: the ladders and the clamp say how much faster a worker runs
 * the beats, the stretch says how long they are, so a stretched program keeps its clamp's
 * proportions (the fastest run is the same fraction of the slowest). The action's own stretch and
 * every matching extension's multiply; absent (or not positive) reads as {@code 1.0}. It is how a
 * pack lengthens or shortens a ritual it does not own with one leaf, its presentation timing
 * stretching with it.
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
    @Nullable protected Double stretch;

    /** A pace, on an action or an extension: the ladder, the {@code Clamp} that bounds the composed scale, and the stretch. */
    public static final BuilderCodec<Pace> CODEC = BuilderCodec.builder(Pace.class, Pace::new)
            .appendInherited(new KeyedCodec<>("Ladder", ContributionScale.CODEC, false),
                    (o, v) -> o.ladder = v, o -> o.ladder, (o, p) -> o.ladder = p.ladder)
            .documentation("The factor ladder (Factors summed, the highest reached Floor's Scale wins; none reached = 1.0) whose scale multiplies the Duration of every step marked Paced. The same shape as ContributionScale.").add()
            .appendInherited(new KeyedCodec<>("Clamp", FactorFormula.Clamp.CODEC, false),
                    (o, v) -> o.clamp = v, o -> o.clamp, (o, p) -> o.clamp = p.clamp)
            .documentation("Bounds on the pace scale once every ladder (the action's and each matching extension's) has multiplied in, before any Stretch: Min is the fastest a paced beat may run, Max the slowest. On an action, author both sides (PACE_UNCLAMPED warns on a missing one): it is where the pace range lives. On an extension it only narrows that range, applied after the action's, so the tighter bound wins at each end and one side is enough.").add()
            .appendInherited(new KeyedCodec<>("Stretch", Codec.DOUBLE, false),
                    (o, v) -> o.stretch = v, o -> o.stretch, (o, p) -> o.stretch = p.stretch)
            .documentation("How long the Paced beats are to begin with: a fixed multiplier on every Paced step's Duration (and its own presentation timing), applied after the Clamp, so the ladders still speed the beats up in the same proportions. The action's Stretch and every matching extension's multiply. Absent = 1.0; 3.0 makes every paced beat three times its authored length.")
            .addValidator(CodecWarnValidators.positive("Pace.Stretch should be positive (leave it out for the authored lengths).")).add()
            .build();

    public Pace() {
    }

    @Nonnull
    public static Pace of(@Nullable ContributionScale ladder, @Nullable FactorFormula.Clamp clamp) {
        return of(ladder, clamp, null);
    }

    @Nonnull
    public static Pace of(@Nullable ContributionScale ladder, @Nullable FactorFormula.Clamp clamp,
            @Nullable Double stretch) {
        Pace p = new Pace();
        p.ladder = ladder;
        p.clamp = clamp;
        p.stretch = stretch;
        return p;
    }

    /** The authored stretch on the paced beats; null = none (see {@link #effectiveStretch()}). */
    @Nullable
    public Double getStretch() {
        return stretch;
    }

    /** The stretch in force: the authored one when it is a positive finite number, else the neutral {@code 1.0}. */
    public double effectiveStretch() {
        return stretch != null && Double.isFinite(stretch) && stretch > 0.0 ? stretch : 1.0;
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
