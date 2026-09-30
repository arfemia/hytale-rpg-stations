package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.common.codec.Vec3;

/**
 * The presentation reach leaves: the dual-shape {@code Target} (a bare word or {@code {Kind,
 * Node}}), the particle {@code Color} tint, the effect's own {@code Target}, and the per-beat
 * {@code State} and {@code Display} leaves on a step, each decoded through the real codecs with
 * fixtures authored here. Also the pure {@code Display} overlay and same-look cores a step's
 * per-beat overlay runs on.
 */
class PresentationTargetCodecTest {

    private static Presentation decode(String json) throws Exception {
        return Presentation.CODEC.decodeJson(RawJsonReader.fromJsonString(json),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static BsonDocument encode(Presentation p) {
        return Presentation.CODEC.encode(p, new ExtraInfo());
    }

    private static StationStep step(String json) throws Exception {
        return StationStep.CODEC.decodeJson(RawJsonReader.fromJsonString(json),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    @Test
    void target_bareWord_decodesItsKind_andRoundTripsAsAWord() throws Exception {
        Presentation p = decode("{ \"Target\": \"Display\", \"Sounds\": [\"Fixture_Sound\"] }");
        assertEquals(Presentation.Target.Kind.DISPLAY, p.effectiveTargetKind());
        assertTrue(p.getTarget().isEntity());
        assertFalse(p.getTarget().hasNode());
        assertEquals("Display", encode(p).getString("Target").getValue(),
                "a bare word re-encodes as a bare word");
    }

    @Test
    void target_object_decodesKindAndNode_caseForgiving() throws Exception {
        Presentation p = decode("{ \"Target\": { \"Kind\": \"puppet\", \"Node\": \"Hand_R\" } }");
        assertEquals(Presentation.Target.Kind.PUPPET, p.effectiveTargetKind());
        assertEquals("Hand_R", p.getTarget().getNode());
        assertTrue(encode(p).isDocument("Target"), "a node makes the object form necessary");
    }

    @Test
    void target_absentOrUnknown_readsAsTheBlock() throws Exception {
        assertNull(decode("{ \"Sounds\": [\"Fixture_Sound\"] }").getTarget());
        assertEquals(Presentation.Target.Kind.BLOCK, decode("{ }").effectiveTargetKind());
        Presentation odd = decode("{ \"Target\": \"Ceiling\" }");
        assertEquals(Presentation.Target.Kind.BLOCK, odd.effectiveTargetKind(), "an unknown word reads as Block");
        assertFalse(odd.getTarget().isEntity());
    }

    @Test
    void target_ridesEveryCopyOfAPresentation() throws Exception {
        Presentation p = decode("{ \"Target\": \"Puppet\", \"Sounds\": [\"Fixture_Sound\"],"
                + " \"Particles\": [{ \"SystemId\": \"Fixture_Burst\" }], \"DelayMs\": 50 }");
        assertEquals(Presentation.Target.Kind.PUPPET, p.soundsOnly().effectiveTargetKind());
        assertEquals(Presentation.Target.Kind.PUPPET, p.withoutSounds().effectiveTargetKind());
        Presentation over = Presentation.of(Presentation.Target.of("Display"), null, null, null, null, null, null);
        assertEquals(Presentation.Target.Kind.DISPLAY, Presentation.overlaid(p, over).effectiveTargetKind(),
                "an authored Target wins the overlay");
        Presentation blank = Presentation.of(null, null, null, null, null, null, null);
        assertEquals(Presentation.Target.Kind.PUPPET, Presentation.overlaid(p, blank).effectiveTargetKind(),
                "an omitted Target falls through");
    }

    @Test
    void particleColor_decodesAsAuthored() throws Exception {
        Presentation p = decode("{ \"Particles\": [{ \"SystemId\": \"Fixture_Burst\", \"Color\": \"#8fd36a\" }] }");
        assertEquals("#8fd36a", p.getParticles()[0].getColor());
        assertNull(decode("{ \"Particles\": [{ \"SystemId\": \"Fixture_Burst\" }] }").getParticles()[0].getColor());
    }

    @Test
    void effectTarget_defaultsToThePlayer_andReadsPuppetForgivingly() throws Exception {
        assertFalse(decode("{ \"Effect\": { \"Id\": \"Fixture_Aura\" } }").getEffect().targetsPuppet());
        assertTrue(decode("{ \"Effect\": { \"Id\": \"Fixture_Aura\", \"Target\": \"puppet\" } }").getEffect().targetsPuppet());
        assertFalse(decode("{ \"Effect\": { \"Id\": \"Fixture_Aura\", \"Target\": \"Player\" } }").getEffect().targetsPuppet());
    }

    @Test
    void aStepsStateAndDisplayOverlay_decode() throws Exception {
        StationStep s = step("{ \"Id\": \"Draw\", \"IsWork\": true, \"State\": \"Drawing\","
                + " \"Display\": { \"Animated\": true, \"Offset\": { \"Y\": 0.9 } } }");
        assertTrue(s.hasState());
        assertEquals("Drawing", s.getState());
        assertTrue(s.getDisplay().effectiveAnimated());
        assertEquals(0.9, s.getDisplay().getOffset().getY());
        assertNull(s.getDisplay().getOffset().getX(), "an unauthored axis stays unauthored, to fall through");
        StationStep bare = step("{ \"Id\": \"Open\" }");
        assertFalse(bare.hasState());
        assertNull(bare.getDisplay());
    }

    @Test
    void displayOverlaid_authoredLeavesWin_axisByAxis_andSameLookTellsAnUnchangedPropApart() {
        Custody.Display base = Custody.Display.of(Vec3.of(0.1, 0.5, null), 1.0, Rotation.of(90.0, null, null), false);
        Custody.Display lift = Custody.Display.of(Vec3.of(null, 0.9, null), null, null, true);
        Custody.Display out = Custody.Display.overlaid(base, lift);
        assertEquals(0.1, out.getOffset().getX(), "an unauthored axis falls through");
        assertEquals(0.9, out.getOffset().getY(), "the authored axis wins");
        assertNull(out.getOffset().getZ());
        assertEquals(1.0, out.getScale());
        assertEquals(90.0, out.getRotation().getYaw());
        assertTrue(out.effectiveAnimated());
        assertTrue(Custody.Display.sameLook(base, Custody.Display.overlaid(base, null)));
        assertFalse(Custody.Display.sameLook(base, out));
        assertTrue(Custody.Display.sameLook(out, Custody.Display.overlaid(base, lift)),
                "the same overlay twice is the same look, so the prop is not respawned");
        assertTrue(Custody.Display.sameLook(null, null));
        assertFalse(Custody.Display.sameLook(null, base));
        assertTrue(Custody.Display.sameLook(Custody.Display.of(null, null, null),
                Custody.Display.of(Vec3.of(0.0, 0.0, 0.0), 1.0, Rotation.of(0.0, 0.0, 0.0), false)),
                "reader defaults and explicit zeroes are one look");
    }
}
