package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * {@link Presentation#overlaid}, the ONE per-leaf overlay rule every layered presentation in this
 * engine resolves through (a flair over a base moment, a refusal's four layers, the settings'
 * engine-wide default under an action's own entry). Leaf-for-leaf parity against the codec's
 * declared leaves is {@code station.StationFlairsLeafParityTest}'s job; this pins the RULE.
 *
 * <p>Every value below is authored by this test; nothing reads shipped content.
 */
public class PresentationOverlaidTest {

    private static Presentation.SoundCue[] cues(String eventId) {
        return new Presentation.SoundCue[] {Presentation.SoundCue.of(eventId)};
    }

    @Test
    void aNullOverReturnsTheBaseItself_andANullBaseReturnsTheOverItself() {
        Presentation base = Presentation.ofSound("Fixture_Base");
        assertSame(base, Presentation.overlaid(base, null));
        assertSame(base, Presentation.overlaid(null, base));
        assertNull(Presentation.overlaid(null, null));
    }

    @Test
    void anAuthoredLeafWins_anOmittedLeafFallsThrough() {
        Presentation base = Presentation.of(cues("Fixture_Base_Sound"),
                new Presentation.ModelParticle[] {Presentation.ModelParticle.of("Fixture_Base_Puff")},
                null, null, null, 120L);
        Presentation over = Presentation.of(cues("Fixture_Over_Sound"), null,
                Presentation.Shake.of("Fixture_Over_Shake", 0.5), null, null, null);

        Presentation out = Presentation.overlaid(base, over);

        assertNotSame(base, out);
        assertNotSame(over, out);
        assertEquals("Fixture_Over_Sound", out.getSounds()[0].getEventId(), "the over's authored leaf wins");
        assertEquals("Fixture_Base_Puff", out.getParticles()[0].getSystemId(), "the over's omitted leaf falls through");
        assertEquals("Fixture_Over_Shake", out.getShake().getEffectId(), "a leaf only the over authors is added");
        assertEquals(120L, out.getDelayMs(), "timing is a leaf like any other");
    }

    @Test
    void anAuthoredEmptyArrayIsALeafAndWinsAsNone() {
        Presentation base = Presentation.ofSound("Fixture_Base_Sound");
        Presentation over = Presentation.of(new Presentation.SoundCue[0], null, null, null, null);

        Presentation out = Presentation.overlaid(base, over);

        assertEquals(0, out.getSounds().length, "an authored empty array silences, it does not fall through");
    }

    @Test
    void anEmptyOverPreservesEveryLeafOfTheBase() {
        Presentation base = Presentation.of(cues("Fixture_Base_Sound"), null,
                Presentation.Shake.of("Fixture_Base_Shake", 0.25), Presentation.Interaction.of("fixture_chain"),
                EffectRef.of("Fixture_Effect", 800L), 120L);

        Presentation out = Presentation.overlaid(base, new Presentation());

        assertEquals("Fixture_Base_Sound", out.getSounds()[0].getEventId());
        assertEquals("Fixture_Base_Shake", out.getShake().getEffectId());
        assertEquals("fixture_chain", out.getInteraction().getId());
        assertEquals("Fixture_Effect", out.getEffect().getId());
        assertEquals(120L, out.getDelayMs());
    }
}
