package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Pace;
import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * The PURE pace resolution ({@link StationPacing}): ladders MULTIPLY (the action's own and each
 * extension's, every one from its own thresholds), only the action's clamp bounds the product, and
 * the scale stretches a paced beat's hold and its presentation timing. Every number is authored by
 * this test.
 */
public class StationPacingTest {

    private static BiFunction<String, String, Double> lookup(Map<String, Double> values) {
        return (factorId, param) -> values.get(factorId);
    }

    private static ContributionScale ladder(String factor, ContributionScale.Floor... floors) {
        return ContributionScale.of(new FactorFormula.Term[] {FactorFormula.Term.of(factor, null, 1.0)}, floors);
    }

    private static final ContributionScale BASE = ladder("fixture:proficiency",
            ContributionScale.Floor.of(10.0, 0.8), ContributionScale.Floor.of(40.0, 0.5));
    private static final ContributionScale PACK = ladder("fixture:level",
            ContributionScale.Floor.of(20.0, 0.75));

    @Test
    void nothingInPlay_isNeutralWithoutALookup() {
        assertEquals(StationPacing.NEUTRAL, StationPacing.multiplier(StationPacing.Composed.NONE, (f, p) -> {
            throw new AssertionError("a neutral composition never resolves a factor");
        }));
        assertTrue(new StationPacing.Composed(Pace.of(null, null), List.of()).isNeutral(),
                "a Pace with no ladder and no extension ladder is neutral");
    }

    @Test
    void theActionsOwnLadder_reachedFloorIsThePace_noneReachedIsNeutral() {
        StationPacing.Composed composed = new StationPacing.Composed(Pace.of(BASE, null), List.of());
        assertEquals(StationPacing.NEUTRAL, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 3.0))));
        assertEquals(0.8, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 15.0))));
        assertEquals(0.5, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 55.0))));
    }

    @Test
    void laddersMultiply_eachFromItsOwnThresholds() {
        StationPacing.Composed composed = new StationPacing.Composed(Pace.of(BASE, null), List.of(PACK));
        Map<String, Double> both = Map.of("fixture:proficiency", 15.0, "fixture:level", 25.0);
        assertEquals(0.8 * 0.75, StationPacing.multiplier(composed, lookup(both)), 1e-9,
                "the jar's 0.8 and the pack's 0.75 compose without either restating the other");
        Map<String, Double> packOnly = Map.of("fixture:level", 25.0);
        assertEquals(0.75, StationPacing.multiplier(composed, lookup(packOnly)), 1e-9,
                "a ladder that reaches no floor contributes the neutral 1.0");
    }

    @Test
    void theActionsClampBoundsTheProduct_atBothEnds() {
        Pace clamped = Pace.of(BASE, FactorFormula.Clamp.of(0.45, 0.9));
        StationPacing.Composed composed = new StationPacing.Composed(clamped, List.of(PACK));
        assertEquals(0.45, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 55.0, "fixture:level", 25.0))), 1e-9,
                "0.5 x 0.75 = 0.375 is raised to the floor");
        assertEquals(0.9, StationPacing.multiplier(composed, lookup(Map.of())), 1e-9,
                "the neutral 1.0 is lowered to the ceiling");
    }

    @Test
    void anExtensionLadderAlone_pacesWithNoActionLadder_andNoClampBoundsIt() {
        StationPacing.Composed composed = new StationPacing.Composed(null, List.of(PACK, PACK));
        assertEquals(0.75 * 0.75, StationPacing.multiplier(composed, lookup(Map.of("fixture:level", 99.0))), 1e-9);
    }

    // ==================== Stretch: how long the paced beats are to begin with ====================

    @Test
    void anExtensionsStretch_multipliesAfterTheClamp_soItsRangeKeepsTheClampsProportions() {
        Pace clamped = Pace.of(BASE, FactorFormula.Clamp.of(0.6, 1.0));
        StationPacing.Composed composed = new StationPacing.Composed(clamped, List.of(), 3.0);
        assertEquals(3.0, StationPacing.multiplier(composed, lookup(Map.of())), 1e-9,
                "untrained: the neutral pace, three times as long");
        assertEquals(0.6 * 3.0, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 55.0))), 1e-9,
                "the ladder's 0.5 is held at the clamp's 0.6 BEFORE the stretch; a clamp after it would read 1.0");
    }

    @Test
    void anActionsOwnStretch_andEveryExtensionsStretch_multiply() {
        Pace stretched = Pace.of(BASE, FactorFormula.Clamp.of(0.1, 1.0), 0.5);
        StationPacing.Composed composed = new StationPacing.Composed(stretched, List.of(), 4.0);
        assertEquals(0.8 * 0.5 * 4.0, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 15.0))), 1e-9);
    }

    @Test
    void aStretchAlone_isNotNeutral_andPacesWithNoLadderAnywhere() {
        StationPacing.Composed composed = new StationPacing.Composed(null, List.of(), 2.0);
        assertFalse(composed.isNeutral(), "a stretch with no ladder still changes every paced beat");
        assertEquals(2.0, StationPacing.multiplier(composed, lookup(Map.of())), 1e-9);
    }

    // ==================== An extension's Clamp: it narrows the range, never widens it ====================

    @Test
    void anExtensionsClamp_narrowsTheActionsRange_soEachEndTakesTheTighterBound() {
        Pace wide = Pace.of(BASE, FactorFormula.Clamp.of(0.1, 1.0));
        StationPacing.Composed composed = new StationPacing.Composed(wide, List.of(PACK), StationPacing.NEUTRAL,
                List.of(FactorFormula.Clamp.of(0.45, null)));
        assertEquals(0.45, StationPacing.multiplier(composed,
                lookup(Map.of("fixture:proficiency", 55.0, "fixture:level", 25.0))), 1e-9,
                "0.5 x 0.75 = 0.375 sits inside the action's 0.1 floor but under the extension's 0.45");
        assertEquals(1.0, StationPacing.multiplier(composed, lookup(Map.of())), 1e-9,
                "an extension clamp with no Max leaves the action's ceiling alone");
    }

    @Test
    void anExtensionsClamp_bindsBeforeTheStretch() {
        StationPacing.Composed composed = new StationPacing.Composed(Pace.of(BASE, FactorFormula.Clamp.of(0.1, 1.0)),
                List.of(), 2.5, List.of(FactorFormula.Clamp.of(0.6, null)));
        assertEquals(0.6 * 2.5, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 99.0))), 1e-9,
                "the floor is a fraction of the authored length; the stretch then lengthens what it held");
    }

    @Test
    void anExtensionsClamp_cannotWidenTheActionsRange() {
        StationPacing.Composed composed = new StationPacing.Composed(Pace.of(BASE, FactorFormula.Clamp.of(0.6, 1.0)),
                List.of(), StationPacing.NEUTRAL, List.of(FactorFormula.Clamp.of(0.2, null)));
        assertEquals(0.6, StationPacing.multiplier(composed, lookup(Map.of("fixture:proficiency", 99.0))), 1e-9,
                "a looser extension floor leaves the action's tighter one standing");
    }

    @Test
    void aStretchThatIsNotPositive_readsAsNoStretch() {
        assertEquals(1.0, Pace.of(null, null, 0.0).effectiveStretch(), 1e-9);
        assertEquals(1.0, Pace.of(null, null, -2.0).effectiveStretch(), 1e-9);
        assertEquals(1.0, Pace.of(null, null, null).effectiveStretch(), 1e-9);
        assertTrue(new StationPacing.Composed(null, List.of(), 0.0).isNeutral(),
                "a stretch that would make every paced beat instant reads as none");
    }

    @Test
    void scaleMs_roundsAndNeverGoesNegative() {
        assertEquals(4000L, StationPacing.scaleMs(8000L, 0.5));
        assertEquals(8000L, StationPacing.scaleMs(8000L, StationPacing.NEUTRAL));
        assertEquals(0L, StationPacing.scaleMs(-5L, 0.5));
        assertEquals(2667L, StationPacing.scaleMs(8000L, 1.0 / 3.0), "whole milliseconds, rounded");
    }

    @Test
    void scaleInTime_stretchesDelaysAndBurstCaps_andLeavesEverythingElseAlone() {
        Presentation.SoundCue[] sounds = {
                Presentation.SoundCue.of("Fixture_Charge"),
                Presentation.SoundCue.of("Fixture_Mote", 500L)};
        Presentation.ModelParticle[] particles = {
                Presentation.ModelParticle.of("Fixture_Sparks", 2.0, 4.0, null, null),
                Presentation.ModelParticle.of("Fixture_Endless", null, 0.0, null, null)};
        Presentation base = Presentation.of(sounds, particles, Presentation.Shake.of("Fixture_Shake", 0.3),
                null, null, 200L);

        Presentation scaled = StationPacing.scaleInTime(base, 0.5);

        assertEquals(100L, scaled.getDelayMs(), "the moment's own delay stretches");
        assertNull(scaled.getSounds()[0].getDelayMs(), "a sound with no delay keeps none");
        assertEquals(250L, scaled.getSounds()[1].getDelayMs(), "a sound's own delay stretches");
        assertEquals("Fixture_Mote", scaled.getSounds()[1].getEventId());
        assertEquals(2.0, scaled.getParticles()[0].getDurationSeconds(), 1e-9, "a burst's cap stretches");
        assertEquals(2.0, scaled.getParticles()[0].getScale(), "a burst's size does not");
        assertEquals(0.0, scaled.getParticles()[1].getDurationSeconds(), 1e-9, "an uncapped burst stays uncapped");
        assertSame(base.getShake(), scaled.getShake(), "a shake carries no time and reads through");
    }

    @Test
    void scaleInTime_isIdentityAtTheNeutralPace_andOnNothing() {
        Presentation base = Presentation.ofSound("Fixture_Charge");
        assertSame(base, StationPacing.scaleInTime(base, StationPacing.NEUTRAL));
        assertNull(StationPacing.scaleInTime(null, 0.5));
    }
}
