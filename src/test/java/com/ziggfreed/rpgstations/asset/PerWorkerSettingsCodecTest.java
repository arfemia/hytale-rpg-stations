package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * The per-worker leaves decode through the REAL shipped codecs and survive an encode and a decode again:
 * {@code Recipe.Batch}, {@code Convert.Batch}, {@code Recipe.Yield.Ladder}, the fallback routes'
 * {@code PreferWhen} and the crafting share's {@code Ladder}, {@code Tool.Durability.Ladder}, a step's
 * {@code SkipWhen}, {@code Work.Unattended}'s {@code Requires}, {@code MaxQuantity} and {@code GatherMaxCycles},
 * and an extension's {@code Recipe}, {@code Tool}, {@code Work} and {@code StepOverlays} payloads. Every value is
 * authored by this test.
 */
class PerWorkerSettingsCodecTest {

    private static final String ACTION = "{ \"Id\": \"Work\","
            + " \"Tool\": { \"Durability\": { \"PerCycle\": 1, \"Ladder\": {"
            + "   \"Factors\": [ { \"Factor\": \"fixture:wear\", \"Weight\": 10 } ],"
            + "   \"Floors\": [ { \"Min\": 10, \"Scale\": 2.0 } ] } } },"
            + " \"Recipe\": {"
            + "   \"Batch\": { \"Min\": 1, \"Max\": 2, \"Factors\": [ { \"Factor\": \"fixture:batch\", \"Weight\": 1 } ] },"
            + "   \"Yield\": { \"Base\": 1, \"Ladder\": { \"Factors\": [ { \"Factor\": \"fixture:yield\" } ],"
            + "                 \"Floors\": [ { \"Min\": 1, \"Scale\": 0.5 } ] } },"
            + "   \"Fallback\": {"
            + "     \"CraftingShare\": { \"Share\": 0.3,"
            + "       \"PreferWhen\": { \"Factors\": [ { \"Factor\": \"fixture:strip\", \"Min\": 1 } ] },"
            + "       \"Ladder\": { \"Factors\": [ { \"Factor\": \"fixture:strip\", \"Weight\": 10 } ],"
            + "                   \"Floors\": [ { \"Min\": 10, \"Scale\": 1.1 } ] } },"
            + "     \"EssenceOnly\": { \"PreferWhen\": { \"AnyOf\": ["
            + "       { \"Factors\": [ { \"Factor\": \"fixture:plenty\", \"Min\": 1 } ] },"
            + "       { \"Factors\": [ { \"Factor\": \"fixture:only\", \"Min\": 1 } ] } ] } } } },"
            + " \"Work\": { \"CycleMs\": 4000, \"Unattended\": {"
            + "   \"Requires\": { \"Factors\": [ { \"Factor\": \"fixture:camp\", \"Min\": 1 } ] },"
            + "   \"MaxQuantity\": { \"Base\": 40, \"Factors\": [ { \"Factor\": \"fixture:camp_rank\", \"Weight\": 10 } ] },"
            + "   \"GatherMaxCycles\": { \"Base\": 24, \"Factors\": [ { \"Factor\": \"fixture:camp_rank\", \"Weight\": 4 } ] } } },"
            + " \"Steps\": ["
            + "   { \"Id\": \"Draw\", \"SkipWhen\": { \"Factors\": [ { \"Factor\": \"fixture:quick\", \"Min\": 1 } ] } },"
            + "   { \"Id\": \"Unmake\", \"Convert\": { \"Batch\": { \"Times\": 3 } } } ] }";

    private static ActionDef action() throws Exception {
        StationAsset station = StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(
                "{ \"Actions\": [ " + ACTION + " ] }"),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
        return station.getActions()[0];
    }

    @Test
    void everyPerWorkerLeaf_decodesOnAnAction() throws Exception {
        ActionDef a = action();

        StationStep.Repeat batch = a.getRecipe().getBatch();
        assertNotNull(batch);
        assertEquals(1, batch.resolveCount(0.0), "no factor reading = the authored Min");
        assertEquals(2, batch.resolveCount(1.0));
        assertEquals(2, batch.resolveCount(5.0), "held in Max");

        assertEquals(0.5, a.getRecipe().getYield().getLadder().getFloors()[0].effectiveScale());
        assertEquals(1, a.getRecipe().getYield().getBase());

        StationAsset.Fallback.CraftingShare share = a.getRecipe().getFallback().getCraftingShare();
        assertEquals(0.3, share.effectiveShare());
        assertEquals(0.33, share.effectiveShare(1.1), 1e-9, "the ladder multiplies the share");
        assertEquals(1.0, share.effectiveShare(10.0), "held at a whole share");
        assertEquals(0.3, share.effectiveShare(0.0), "a non-positive scale reads as 1");
        assertFalse(share.getPreferWhen().isEmpty());
        assertEquals("fixture:strip", share.getPreferWhen().factorsOrEmpty()[0].getFactor());
        assertEquals(2, a.getRecipe().getFallback().getEssenceOnly().getPreferWhen().anyOfOrEmpty().length);
        assertTrue(a.getRecipe().getFallback().hasEssenceOnly(), "authoring PreferWhen alone still opts the route in");

        StationAsset.Tool.Durability wear = a.getTool().getDurability();
        assertEquals(1, wear.getPerCycle());
        assertEquals(2.0, wear.getLadder().getFloors()[0].effectiveScale());

        StationAsset.Work.Unattended unattended = a.getWork().getUnattended();
        assertTrue(unattended.hasRequires());
        assertEquals(40.0, unattended.getMaxQuantity().getBase());
        assertEquals(10.0, unattended.getMaxQuantity().getFactors()[0].weightOrDefault());
        assertEquals(24.0, unattended.getGatherMaxCycles().getBase());
        assertEquals(24, unattended.effectiveMaxCycles(), "MaxCycles keeps its default beside the gather leaf");

        StationStep draw = a.getSteps()[0];
        assertNotNull(draw.getSkipWhen());
        assertEquals("fixture:quick", draw.getSkipWhen().factorsOrEmpty()[0].getFactor());
        assertNull(a.getSteps()[1].getSkipWhen());
        assertEquals(3, a.getSteps()[1].getConvert().getBatch().resolveCount(0.0));
        assertTrue(a.getSteps()[1].getConvert().effectiveEnabled());
    }

    @Test
    void theLeavesSurviveAnEncodeAndADecode() throws Exception {
        ActionDef a = action();
        ExtraInfo info = new ExtraInfo();

        BsonDocument recipe = StationAsset.Recipe.CODEC.encode(a.getRecipe(), info);
        StationAsset.Recipe again = StationAsset.Recipe.CODEC.decode(recipe, info);
        assertEquals(2, again.getBatch().resolveCount(1.0));
        assertEquals(0.5, again.getYield().getLadder().getFloors()[0].effectiveScale());
        assertEquals(1.1, again.getFallback().getCraftingShare().getLadder().getFloors()[0].effectiveScale());
        assertEquals("fixture:strip",
                again.getFallback().getCraftingShare().getPreferWhen().factorsOrEmpty()[0].getFactor());
        assertEquals(2, again.getFallback().getEssenceOnly().getPreferWhen().anyOfOrEmpty().length);

        BsonDocument tool = StationAsset.Tool.CODEC.encode(a.getTool(), info);
        assertEquals(2.0, StationAsset.Tool.CODEC.decode(tool, info).getDurability().getLadder().getFloors()[0]
                .effectiveScale());

        BsonDocument work = StationAsset.Work.CODEC.encode(a.getWork(), info);
        StationAsset.Work.Unattended u = StationAsset.Work.CODEC.decode(work, info).getUnattended();
        assertTrue(u.hasRequires());
        assertEquals(40.0, u.getMaxQuantity().getBase());
        assertEquals(4.0, u.getGatherMaxCycles().getFactors()[0].weightOrDefault());

        BsonDocument step = StationStep.CODEC.encode(a.getSteps()[0], info);
        assertEquals("fixture:quick",
                StationStep.CODEC.decode(step, info).getSkipWhen().factorsOrEmpty()[0].getFactor());
        BsonDocument convert = StationStep.CODEC.encode(a.getSteps()[1], info);
        assertEquals(3, StationStep.CODEC.decode(convert, info).getConvert().getBatch().resolveCount(0.0));
    }

    @Test
    void aStepCopyCarriesItsNewSkipWhen_andEveryOtherLeaf() throws Exception {
        StationStep unmake = action().getSteps()[1];
        StationStep copy = unmake.copyWithSkipWhen(action().getSteps()[0].getSkipWhen());
        assertNotNull(copy.getSkipWhen());
        assertNull(unmake.getSkipWhen(), "the decoded step is never written");
        assertEquals("Unmake", copy.getId());
        assertEquals(3, copy.getConvert().getBatch().resolveCount(0.0));
    }

    @Test
    void anExtensionsPerWorkerOverlays_decode_andAreActionPayloads() throws Exception {
        ExtensionAsset ext = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                "{ \"Target\": { \"Station\": \"Fixture_Mill\", \"Action\": \"Mill\" },"
                        + " \"Recipe\": { \"Batch\": { \"Min\": 1, \"Max\": 2 } },"
                        + " \"Tool\": { \"Durability\": { \"Ladder\": { \"Floors\": [ { \"Min\": 1, \"Scale\": 2 } ] } } },"
                        + " \"Work\": { \"Unattended\": { \"Requires\": { \"Factors\": [ { \"Factor\": \"fixture:camp\", \"Min\": 1 } ] } } },"
                        + " \"StepOverlays\": { \"Draw\": { \"SkipWhen\": { \"Factors\": [ { \"Factor\": \"fixture:quick\", \"Min\": 1 } ] } } } }"),
                null, new AssetExtraInfo<>(new AssetExtraInfo.Data(ExtensionAsset.class, "pack-techniques", null)));
        assertEquals(2, ext.getRecipe().getBatch().resolveCount(9.0));
        assertEquals(2.0, ext.getTool().getDurability().getLadder().getFloors()[0].effectiveScale());
        assertTrue(ext.getWork().getUnattended().hasRequires());
        assertEquals("fixture:quick",
                ext.getStepOverlays().get("Draw").getSkipWhen().factorsOrEmpty()[0].getFactor());
        for (String payload : new String[] {ExtensionAsset.PAYLOAD_RECIPE, ExtensionAsset.PAYLOAD_TOOL,
                ExtensionAsset.PAYLOAD_WORK, ExtensionAsset.PAYLOAD_STEP_OVERLAYS}) {
            assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION, payload), payload);
            assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, payload), payload);
        }
    }
}
