package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
