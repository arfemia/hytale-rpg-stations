package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * The shared {@link Presentation} {@code DelayMs} leaf: it decodes at EVERY altitude a
 * {@code Presentation} appears at, survives native {@code Parent} inheritance per-leaf like its
 * siblings, and reader-defaults to "play at once" for every value that is not a positive delay.
 *
 * <p>Every number here is authored by the test itself; nothing reads a shipped balance value.
 */
public class PresentationDelayCodecTest {

    private static StationAsset decodeStation(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static ActionAsset decodeAction(String key, String parentKey, String body, ActionAsset parent)
            throws Exception {
        return ActionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(body), parent,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ActionAsset.class, key, parentKey)));
    }

    private static FlairAsset decodeFlair(String body) throws Exception {
        return FlairAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(FlairAsset.class, "fixture", null)));
    }

    /** One inline action carrying {@code Moments} plus one authored step. */
    private static ActionDef inlineAction(String momentsJson, String stepsJson) throws Exception {
        return decodeStation("{ \"Actions\": [ { \"Id\": \"a\", \"Moments\": " + momentsJson
                + ", \"Steps\": " + stepsJson + " } ] }").getActions()[0];
    }

    // ==================== Decode at each Presentation altitude ====================

    @Test
    void decodesOnAnActionsCycleAndCompletionMoments() throws Exception {
        ActionDef a = inlineAction(
                "{ \"Cycle\": { \"Sounds\": [\"Fixture_Break\"], \"DelayMs\": 120 },"
                        + " \"Completion\": { \"Sounds\": [\"Fixture_Done\"], \"DelayMs\": 250 } }",
                "[ { \"Id\": \"x\" } ]");

        assertEquals(120L, a.getMoments().getCycle().getDelayMs());
        assertEquals(120L, a.getMoments().getCycle().effectiveDelayMs());
        assertEquals(250L, a.getMoments().getCompletion().getDelayMs());
    }

    @Test
    void decodesOnAStepsOwnPresentation() throws Exception {
        ActionDef a = inlineAction("{ }",
                "[ { \"Id\": \"x\", \"Presentation\": { \"Sounds\": [\"Fixture_Beat\"], \"DelayMs\": 40 } } ]");

        assertEquals(40L, a.getSteps()[0].getPresentation().effectiveDelayMs());
    }

    @Test
    void decodesOnARollAndOnALadderFloor() throws Exception {
        StationAsset asset = decodeStation("{ \"Actions\": [ { \"Id\": \"a\", \"Bonus\": { \"Rolls\": [ {"
                + " \"Trigger\": \"Cycle\","
                + " \"Presentation\": { \"Sounds\": [\"Fixture_Roll_Cue\"], \"DelayMs\": 75 },"
                + " \"Ladder\": { \"Floors\": [ { \"Min\": 1.0,"
                + " \"Presentation\": { \"Sounds\": [\"Fixture_Floor_Cue\"], \"DelayMs\": 300 } } ] }"
                + " } ] } } ] }");
        Roll roll = asset.getActions()[0].getBonus().getRolls()[0];

        assertEquals(75L, roll.getPresentation().effectiveDelayMs());
        assertEquals(300L, roll.getLadder().getFloors()[0].getPresentation().effectiveDelayMs());
    }

    @Test
    void decodesOnAFlairAssetMoment() throws Exception {
        FlairAsset flair = decodeFlair("{ \"Moments\": { \"cycle\":"
                + " { \"Sounds\": [\"Fixture_Golden\"], \"DelayMs\": 500 } } }");

        assertEquals(500L, flair.getMoments().get("cycle").effectiveDelayMs());
    }

    // ==================== Reader defaults ====================

    @Test
    void omitted_decodesNullAndPlaysAtOnce() throws Exception {
        ActionDef a = inlineAction("{ \"Cycle\": { \"Sounds\": [\"Fixture_Break\"] } }",
                "[ { \"Id\": \"x\" } ]");

        assertNotNull(a.getMoments().getCycle());
        assertNull(a.getMoments().getCycle().getDelayMs());
        assertEquals(Presentation.NO_DELAY_MS, a.getMoments().getCycle().effectiveDelayMs());
    }

    @Test
    void zeroOrNegative_readsAsPlayAtOnce_soANonsenseValueNeverSwallowsTheCue() throws Exception {
        ActionDef zero = inlineAction("{ \"Cycle\": { \"DelayMs\": 0 } }", "[ { \"Id\": \"x\" } ]");
        ActionDef negative = inlineAction("{ \"Cycle\": { \"DelayMs\": -50 } }", "[ { \"Id\": \"x\" } ]");

        assertEquals(Presentation.NO_DELAY_MS, zero.getMoments().getCycle().effectiveDelayMs());
        assertEquals(Presentation.NO_DELAY_MS, negative.getMoments().getCycle().effectiveDelayMs());
        assertEquals(-50L, negative.getMoments().getCycle().getDelayMs(),
                "the authored value still round-trips; only the reader default clamps it");
    }

    @Test
    void javaFactory_carriesTheDelay_andTheFiveArgFormLeavesItUnset() {
        Presentation delayed = Presentation.of(new String[]{"Fixture_A"}, null, null, null, null, 90L);
        Presentation plain = Presentation.of(new String[]{"Fixture_A"}, null, null, null, null);

        assertEquals(90L, delayed.effectiveDelayMs());
        assertNull(plain.getDelayMs());
        assertEquals(Presentation.NO_DELAY_MS, plain.effectiveDelayMs());
    }

    // ==================== Native Parent inheritance (per leaf, like every sibling) ====================

    @Test
    void parentInheritance_delayInheritsOnOmit_andTheChildsOwnValueWins() throws Exception {
        ActionAsset parent = decodeAction("base_action", null,
                "{ \"Moments\": { \"Cycle\": { \"Sounds\": [\"Fixture_Parent\"], \"DelayMs\": 200 } } }", null);
        assertEquals(200L, parent.getBody().getMoments().getCycle().getDelayMs());

        ActionAsset inherits = decodeAction("child_inherits", "base_action",
                "{ \"Moments\": { \"Cycle\": { \"Sounds\": [\"Fixture_Child\"] } } }", parent);
        assertEquals(200L, inherits.getBody().getMoments().getCycle().getDelayMs(),
                "a child re-skinning the sounds keeps the parent's timing");

        ActionAsset overrides = decodeAction("child_overrides", "base_action",
                "{ \"Moments\": { \"Cycle\": { \"DelayMs\": 10 } } }", parent);
        assertEquals(10L, overrides.getBody().getMoments().getCycle().getDelayMs());
        assertEquals("Fixture_Parent", overrides.getBody().getMoments().getCycle().getSounds()[0],
                "the sibling Sounds leaf still inherits alongside an overridden DelayMs");
    }
}
