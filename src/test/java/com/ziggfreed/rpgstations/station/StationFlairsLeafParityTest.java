package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.rpgstations.api.impl.FlairUnlockRegistryImpl;
import com.ziggfreed.rpgstations.asset.Presentation;

/**
 * A structural guard over {@link StationFlairs#effective}, which hand-enumerates every
 * {@link Presentation} leaf and rebuilds the winner through {@code Presentation.of}. That shape is
 * fine right up until a leaf is ADDED: an overlay that does not know about the new leaf both fails
 * to apply a flair's value for it AND erases the base's value for it, because the rebuild only
 * carries the leaves it was taught. Neither failure is visible in a compiler error or in any
 * hand-written per-leaf test, since the new leaf simply has no test.
 *
 * <p>So this walks {@code Presentation.CODEC}'s own entries rather than a hand-written list (the
 * same public {@code BuilderCodec#getEntries()} walk the documentation-coverage guard uses), and
 * compares ENCODED presentations rather than leaf-by-leaf getters - a comparison that keeps working
 * for leaves that do not exist yet.
 *
 * <p>Every value below is authored by this test; nothing reads shipped content.
 */
public class StationFlairsLeafParityTest {

    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String STATION_ID = "fixture_station";
    private static final String FLAIR_ID = "fixture_flair";

    /** What the next author has to do when one of the assertions below fails. */
    private static final String WHAT_TO_UPDATE =
            "\n\nStationFlairs.effective hand-enumerates Presentation's leaves and rebuilds the winner"
                    + " through Presentation.of, so a NEW leaf on Presentation.CODEC needs all four of:"
                    + "\n  1. a seed from the base in StationFlairs.effective (Type x = base != null ? base.getX() : null)"
                    + "\n  2. an overlay for it in the same method's per-flair loop (if (momentPresentation.getX() != null) ...)"
                    + "\n  3. a slot on the widest Presentation.of factory, which is what the rebuild returns"
                    + "\n  4. the leaf authored in BOTH fixtures in this test, so it stays covered here.";

    /** Every leaf a Presentation has, authored. The parity assertions below check that claim. */
    private static final String EVERY_LEAF_BASE = """
            {
              "Sounds": ["Fixture_Base_Sound"],
              "Particles": [{ "SystemId": "Fixture_Base_Particles", "Scale": 1.5 }],
              "Shake": { "EffectId": "Fixture_Base_Shake", "Intensity": 0.25 },
              "Interaction": { "Id": "fixture_base_interaction" },
              "Effect": { "Id": "Fixture_Base_Effect", "DurationMs": 800 },
              "DelayMs": 120
            }
            """;

    /** The same leaf set, every value DIFFERENT, so an unread leaf shows up as a base value surviving. */
    private static final String EVERY_LEAF_FLAIR = """
            {
              "Sounds": ["Fixture_Flair_Sound"],
              "Particles": [{ "SystemId": "Fixture_Flair_Particles", "Scale": 3.0 }],
              "Shake": { "EffectId": "Fixture_Flair_Shake", "Intensity": 0.75 },
              "Interaction": { "Id": "fixture_flair_interaction" },
              "Effect": { "Id": "Fixture_Flair_Effect", "DurationMs": 200 },
              "DelayMs": 640
            }
            """;

    @AfterEach
    void resetRegistry() {
        FlairUnlockRegistryImpl.getInstance().resetForTests();
    }

    private static Presentation decode(String json) throws Exception {
        return Presentation.CODEC.decodeJson(RawJsonReader.fromJsonString(json), new ExtraInfo());
    }

    private static BsonDocument encode(Presentation p) {
        return Presentation.CODEC.encode(p, new ExtraInfo());
    }

    private static Presentation overlay(Presentation base, Presentation flair) {
        FlairUnlockRegistryImpl.getInstance().register(playerId -> Set.of(FLAIR_ID));
        Map<String, Map<String, Presentation>> flairs =
                Map.of(FLAIR_ID, Map.of(StationFlairs.MOMENT_CYCLE, flair));
        return StationFlairs.effective(base, flairs, StationFlairs.MOMENT_CYCLE, PLAYER, STATION_ID);
    }

    @Test
    void theFixturesAuthorEveryLeafPresentationDeclares() throws Exception {
        Set<String> declared = new TreeSet<>(Presentation.CODEC.getEntries().keySet());

        assertEquals(declared, new TreeSet<>(encode(decode(EVERY_LEAF_BASE)).keySet()),
                "the BASE fixture no longer authors every declared Presentation leaf." + WHAT_TO_UPDATE);
        assertEquals(declared, new TreeSet<>(encode(decode(EVERY_LEAF_FLAIR)).keySet()),
                "the FLAIR fixture no longer authors every declared Presentation leaf." + WHAT_TO_UPDATE);
    }

    @Test
    void anEmptyFlairOverlayPreservesEveryLeafOfTheBase() throws Exception {
        Presentation base = decode(EVERY_LEAF_BASE);

        // An empty flair moment still triggers the rebuild, which is exactly the path that can drop
        // a leaf: it overrides nothing, so everything must come back through untouched.
        Presentation result = overlay(base, new Presentation());

        assertNotNull(result);
        assertEquals(encode(base), encode(result),
                "an overlay that overrides nothing ERASED a leaf of the base moment." + WHAT_TO_UPDATE);
    }

    @Test
    void aFullFlairOverlayReplacesEveryLeafOfTheBase() throws Exception {
        Presentation base = decode(EVERY_LEAF_BASE);
        Presentation flair = decode(EVERY_LEAF_FLAIR);

        Presentation result = overlay(base, flair);

        assertNotNull(result);
        assertEquals(encode(flair), encode(result),
                "a flair authoring every leaf did not win one of them, so that leaf has no flair"
                        + " support at all." + WHAT_TO_UPDATE);
    }
}
