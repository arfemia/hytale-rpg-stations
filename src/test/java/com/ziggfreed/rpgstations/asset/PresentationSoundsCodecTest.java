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

/**
 * The dual-shape {@code Presentation.Sounds} entry: a bare id STRING or a
 * {@code {EventId, DelayMs}} OBJECT, freely mixed inside one array and normalized to the same
 * {@link Presentation.SoundCue} record either way. Encoding is checked as well as decoding, because
 * the shorthand round-tripping AS a bare string is what keeps a hand-authored file and an
 * encode-based comparison agreeing on the same bytes.
 *
 * <p>Every value below is authored by this test; nothing reads shipped content.
 */
public class PresentationSoundsCodecTest {

    /**
     * Decodes through an {@code AssetExtraInfo}, the way a real asset load does - its collecting
     * {@code ValidationResults} is what lets a warn-only validator (an authored negative delay)
     * report without a live server logger behind it.
     */
    private static Presentation decode(String json) throws Exception {
        return Presentation.CODEC.decodeJson(RawJsonReader.fromJsonString(json),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static BsonDocument encode(Presentation p) {
        return Presentation.CODEC.encode(p, new ExtraInfo());
    }

    // ==================== Decode: both shapes, mixed ====================

    @Test
    void bareStringEntry_decodesAsAnUndelayedCue() throws Exception {
        Presentation p = decode("{ \"Sounds\": [\"Fixture_Thud\"] }");

        assertEquals(1, p.getSounds().length);
        assertEquals("Fixture_Thud", p.getSounds()[0].getEventId());
        assertNull(p.getSounds()[0].getDelayMs());
        assertEquals(Presentation.SoundCue.NO_DELAY_MS, p.getSounds()[0].effectiveDelayMs());
    }

    @Test
    void objectEntry_decodesItsOwnDelay() throws Exception {
        Presentation p = decode("{ \"Sounds\": [ { \"EventId\": \"Fixture_Hit\", \"DelayMs\": 140 } ] }");

        assertEquals("Fixture_Hit", p.getSounds()[0].getEventId());
        assertEquals(140L, p.getSounds()[0].getDelayMs());
        assertEquals(140L, p.getSounds()[0].effectiveDelayMs());
    }

    @Test
    void bothShapesMixInOneArray_inAuthoredOrder() throws Exception {
        Presentation p = decode("{ \"Sounds\": [ \"Fixture_Swing\","
                + " { \"EventId\": \"Fixture_Hit\", \"DelayMs\": 140 }, \"Fixture_Chime\" ] }");

        assertEquals(3, p.getSounds().length);
        assertEquals("Fixture_Swing", p.getSounds()[0].getEventId());
        assertEquals(0L, p.getSounds()[0].effectiveDelayMs());
        assertEquals("Fixture_Hit", p.getSounds()[1].getEventId());
        assertEquals(140L, p.getSounds()[1].effectiveDelayMs());
        assertEquals("Fixture_Chime", p.getSounds()[2].getEventId());
    }

    @Test
    void objectEntryWithoutADelay_isTheSameThingAsTheShorthand() throws Exception {
        Presentation object = decode("{ \"Sounds\": [ { \"EventId\": \"Fixture_Thud\" } ] }");
        Presentation shorthand = decode("{ \"Sounds\": [\"Fixture_Thud\"] }");

        assertEquals(encode(shorthand), encode(object),
                "an object authoring only EventId carries nothing the shorthand cannot express");
    }

    // ==================== Reader defaults ====================

    @Test
    void zeroOrNegativeDelay_readsAsPlayWithTheMoment_soANonsenseValueNeverSwallowsTheSound() throws Exception {
        Presentation p = decode("{ \"Sounds\": [ { \"EventId\": \"Fixture_A\", \"DelayMs\": 0 },"
                + " { \"EventId\": \"Fixture_B\", \"DelayMs\": -40 } ] }");

        assertEquals(Presentation.SoundCue.NO_DELAY_MS, p.getSounds()[0].effectiveDelayMs());
        assertEquals(Presentation.SoundCue.NO_DELAY_MS, p.getSounds()[1].effectiveDelayMs());
        assertEquals(-40L, p.getSounds()[1].getDelayMs(),
                "the authored value still round-trips; only the reader default clamps it");
    }

    @Test
    void blankOrMissingEventId_isSkippable_andReportsItself() throws Exception {
        Presentation p = decode("{ \"Sounds\": [ { \"DelayMs\": 40 }, \"\" ] }");

        assertFalse(p.getSounds()[0].hasEventId());
        assertFalse(p.getSounds()[1].hasEventId());
        assertTrue(Presentation.SoundCue.of("Fixture_A").hasEventId());
    }

    // ==================== Encode: the shorthand survives a round trip ====================

    @Test
    void shorthandEntry_reEncodesAsABareString_notAnInflatedObject() throws Exception {
        String json = "{ \"Sounds\": [\"Fixture_Thud\", \"Fixture_Chime\"] }";

        BsonDocument encoded = encode(decode(json));

        assertEquals(2, encoded.getArray("Sounds").size());
        assertTrue(encoded.getArray("Sounds").get(0).isString(),
                "a shorthand-authored entry must not inflate into an object on re-encode");
        assertEquals("Fixture_Thud", encoded.getArray("Sounds").get(0).asString().getValue());
        assertEquals(encode(decode(json)), encode(decode(json)));
    }

    @Test
    void offsetEntry_reEncodesAsAnObject_carryingBothLeaves() throws Exception {
        BsonDocument encoded = encode(decode(
                "{ \"Sounds\": [ { \"EventId\": \"Fixture_Hit\", \"DelayMs\": 140 } ] }"));

        BsonDocument entry = encoded.getArray("Sounds").get(0).asDocument();
        assertEquals("Fixture_Hit", entry.getString("EventId").getValue());
        assertEquals(140L, entry.getInt64("DelayMs").getValue());
    }

    // ==================== The Java factories agree with the decoded shape ====================

    @Test
    void javaFactory_matchesWhatTheShorthandDecodesTo() throws Exception {
        Presentation built = Presentation.of(
                new Presentation.SoundCue[] {Presentation.SoundCue.of("Fixture_Thud")},
                null, null, null, null);

        assertEquals(encode(decode("{ \"Sounds\": [\"Fixture_Thud\"] }")), encode(built));
    }
}
