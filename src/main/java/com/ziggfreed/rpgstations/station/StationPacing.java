package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Pace;
import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * The PURE resolution of a program's pace: every ladder in play resolves to its own scale through
 * the ONE ladder rule ({@link ContributionScaling#multiplier}), the scales multiply, and the
 * action's clamp bounds the product. Store-free, so it unit-tests with a plain injected lookup.
 *
 * <p><b>Ladders multiply, and only the action clamps.</b> The action's own {@code Pace.Ladder}
 * and each matching extension's {@code Pace.Ladder} are independent ladders with their own
 * thresholds: a jar's ladder over one factor and a pack's over another compose without either
 * restating the other. A ladder that reaches no floor contributes the neutral {@code 1.0}. The
 * product is then held inside the action's {@code Pace.Clamp} (an extension's clamp is ignored);
 * with no clamp authored the product stands as it is.
 *
 * <p><b>What the scale touches.</b> A step marked {@code Paced} has its {@code Duration.Ms}
 * multiplied ({@link #scaleMs}), and its own presentation's timing stretched with it
 * ({@link #scaleInTime}: the moment's {@code DelayMs}, each sound's {@code DelayMs}, each burst's
 * {@code DurationSeconds}), so a beat's accents keep their place inside the beat. An unpaced step
 * keeps its authored length whatever the pace.
 */
public final class StationPacing {

    /** The pace of a program nothing scales: every beat at its authored length. */
    public static final double NEUTRAL = 1.0;

    private StationPacing() {
    }

    /**
     * The pace ladders in play for one action: its own {@link Pace} (ladder plus clamp) and every
     * matching extension's ladder, gathered once per dispatch and resolved once per step entry.
     */
    public record Composed(@Nullable Pace base, @Nonnull List<ContributionScale> extensionLadders) {

        /** The composition of an action nothing paces: the neutral scale at every step. */
        public static final Composed NONE = new Composed(null, List.of());

        public Composed {
            extensionLadders = extensionLadders == null ? List.of() : List.copyOf(extensionLadders);
        }

        /** True when no ladder is in play at all, so every step resolves to {@link #NEUTRAL} without a lookup. */
        public boolean isNeutral() {
            return (base == null || base.getLadder() == null) && extensionLadders.isEmpty();
        }
    }

    /**
     * The pace scale for {@code composed} at the factor values {@code lookup} resolves: the product
     * of every ladder's reached-floor scale (the action's own first, then each extension's in apply
     * order), held inside the action's clamp. {@link #NEUTRAL} when nothing is in play, and never a
     * non-finite or negative number (a ladder floor authoring a negative scale reads as neutral
     * through the shared floor rule, and a NaN product falls back to neutral).
     */
    public static double multiplier(@Nonnull Composed composed, @Nonnull BiFunction<String, String, Double> lookup) {
        if (composed.isNeutral()) {
            return NEUTRAL;
        }
        double product = NEUTRAL;
        Pace base = composed.base();
        if (base != null && base.getLadder() != null) {
            product *= ContributionScaling.multiplier(base.getLadder(), lookup);
        }
        for (ContributionScale ladder : composed.extensionLadders()) {
            product *= ContributionScaling.multiplier(ladder, lookup);
        }
        FactorFormula.Clamp clamp = base != null ? base.getClamp() : null;
        if (clamp != null) {
            product = clamp.apply(product);
        }
        return Double.isFinite(product) && product >= 0.0 ? product : NEUTRAL;
    }

    /** {@code ms} stretched by {@code scale}, rounded to whole milliseconds and never negative. */
    public static long scaleMs(long ms, double scale) {
        if (ms <= 0L || scale == NEUTRAL) {
            return Math.max(0L, ms);
        }
        return Math.max(0L, Math.round(ms * scale));
    }

    /**
     * {@code presentation} with its timing stretched by {@code scale}: the moment's own
     * {@code DelayMs}, each sound's {@code DelayMs} and each burst's {@code DurationSeconds} (an
     * authored uncapped burst, zero or negative, stays uncapped). Identity when {@code presentation}
     * is null or the scale is neutral; every cue that is not a time reads through untouched.
     */
    @Nullable
    public static Presentation scaleInTime(@Nullable Presentation presentation, double scale) {
        if (presentation == null || scale == NEUTRAL) {
            return presentation;
        }
        Presentation.SoundCue[] sounds = presentation.getSounds();
        Presentation.SoundCue[] scaledSounds = null;
        if (sounds != null) {
            scaledSounds = new Presentation.SoundCue[sounds.length];
            for (int i = 0; i < sounds.length; i++) {
                Presentation.SoundCue cue = sounds[i];
                scaledSounds[i] = cue == null ? null
                        : Presentation.SoundCue.of(cue.getEventId(), scaledDelay(cue.getDelayMs(), scale));
            }
        }
        Presentation.ModelParticle[] particles = presentation.getParticles();
        Presentation.ModelParticle[] scaledParticles = null;
        if (particles != null) {
            scaledParticles = new Presentation.ModelParticle[particles.length];
            for (int i = 0; i < particles.length; i++) {
                Presentation.ModelParticle burst = particles[i];
                scaledParticles[i] = burst == null ? null
                        : Presentation.ModelParticle.of(burst.getSystemId(), burst.getScale(),
                                scaledSeconds(burst.getDurationSeconds(), scale),
                                burst.getRotationOffset(), burst.getPositionOffset());
            }
        }
        return Presentation.of(scaledSounds, scaledParticles, presentation.getShake(),
                presentation.getInteraction(), presentation.getEffect(),
                scaledDelay(presentation.getDelayMs(), scale));
    }

    /** A nullable authored delay stretched by {@code scale}; null stays null, a non-positive delay stays as authored. */
    @Nullable
    static Long scaledDelay(@Nullable Long delayMs, double scale) {
        if (delayMs == null || delayMs <= 0L) {
            return delayMs;
        }
        return scaleMs(delayMs, scale);
    }

    /** A nullable authored playback cap stretched by {@code scale}; null stays null, an uncapped (non-positive) cap stays uncapped. */
    @Nullable
    static Double scaledSeconds(@Nullable Double seconds, double scale) {
        if (seconds == null || seconds <= 0.0) {
            return seconds;
        }
        double scaled = seconds * scale;
        return Double.isFinite(scaled) ? scaled : seconds;
    }

    /**
     * The extension ladders of {@code paces}, each a complete {@link Pace} whose ladder joins the
     * product (its clamp is the action's to set, never an extension's). Null-safe.
     */
    @Nonnull
    static List<ContributionScale> laddersOf(@Nullable List<Pace> paces) {
        if (paces == null || paces.isEmpty()) {
            return List.of();
        }
        List<ContributionScale> out = new ArrayList<>(paces.size());
        for (Pace pace : paces) {
            if (pace != null && pace.getLadder() != null) {
                out.add(pace.getLadder());
            }
        }
        return out;
    }
}
