package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * The new codec leaves decode through the REAL shipped codecs: {@code StationStep.Convert} /
 * {@code Paced} / {@code RollBonus}, {@code ActionDef.Pace} (on an inline action and a standalone
 * one), {@code Work.Queue}, {@code Recipe.Fallback} with its three groups, the shared matcher's
 * {@code Except} hole, and an {@code ExtensionAsset}'s own {@code Pace}. Every value is authored
 * by this test.
 */
public class StationStepConvertCodecTest {

    private static StationAsset station(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static ActionDef action(String actionBody) throws Exception {
        return station("{ \"Actions\": [ " + actionBody + " ] }").getActions()[0];
    }

    @Test
    void convertPacedAndRollBonus_decodeOnAStep_andReadTheirDefaults() throws Exception {
        ActionDef a = action("{ \"Id\": \"Unmake\", \"Steps\": ["
                + " { \"Id\": \"Kindle\", \"Paced\": true, \"Duration\": { \"Ms\": 8000 } },"
                + " { \"Id\": \"Unmake\", \"Convert\": {} },"
                + " { \"Id\": \"Payout\", \"RollBonus\": true },"
                + " { \"Id\": \"Off\", \"Convert\": { \"Enabled\": false } } ] }");
        StationStep[] steps = a.getSteps();
        assertTrue(steps[0].effectivePaced());
        assertFalse(steps[0].effectiveRollBonus());
        assertNull(steps[0].getConvert());
        assertTrue(steps[0].isPureBeat(), "a paced hold is still a pure beat");

        assertNotNull(steps[1].getConvert());
        assertTrue(steps[1].getConvert().effectiveEnabled(), "an authored group is on");
        assertTrue(steps[1].effectiveIsWork(), "a convert IS work by default");
        assertFalse(steps[1].isPureBeat());
        assertFalse(steps[1].effectivePaced(), "Paced defaults off");

        assertTrue(steps[2].effectiveRollBonus());
        assertFalse(steps[2].isPureBeat(), "a beat that rolls the Bonus is a phase, not a pure beat");

        assertFalse(steps[3].getConvert().effectiveEnabled(), "a Parent child can switch a convert off");
        assertFalse(steps[3].effectiveIsWork(), "a switched-off convert converts nothing, so it is not work");
        assertTrue(steps[3].withIsWork(true).effectiveIsWork(), "an explicit IsWork still promotes the beat");
    }

    @Test
    void pace_decodesOnAnAction_ladderAndClamp() throws Exception {
        ActionDef a = action("{ \"Id\": \"Unmake\", \"Pace\": { \"Ladder\": {"
                + " \"Factors\": [ { \"Factor\": \"fixture:proficiency\", \"Weight\": 2.0 } ],"
                + " \"Floors\": [ { \"Min\": 10, \"Scale\": 0.8 }, { \"Min\": 40, \"Scale\": 0.5 } ] },"
                + " \"Clamp\": { \"Min\": 0.375, \"Max\": 1.0 } } }");
        Pace pace = a.getPace();
        assertNotNull(pace);
        assertEquals("fixture:proficiency", pace.getLadder().getFactors()[0].getFactor());
        assertEquals(2.0, pace.getLadder().getFactors()[0].weightOrDefault());
        assertEquals(2, pace.getLadder().getFloors().length);
        assertEquals(0.5, pace.getLadder().getFloors()[1].effectiveScale());
        assertEquals(0.375, pace.getClamp().getMin());
        assertEquals(1.0, pace.getClamp().getMax());
        assertFalse(pace.getClamp().isInverted());
    }

    @Test
    void pace_decodesOnAStandaloneAction_too() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ActionAsset.class, "disenchant", null);
        ActionAsset asset = ActionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                "{ \"Pace\": { \"Ladder\": { \"Floors\": [ { \"Min\": 0, \"Scale\": 0.9 } ] } } }"),
                null, new AssetExtraInfo<>(data));
        assertNotNull(asset.getBody().getPace());
        assertEquals(0.9, asset.getBody().getPace().getLadder().getFloors()[0].effectiveScale());
        assertNull(asset.getBody().getPace().getClamp());
    }

    @Test
    void workQueue_decodes_andDefaultsOff() throws Exception {
        ActionDef queued = action("{ \"Id\": \"Unmake\", \"Work\": { \"Looping\": false, \"Queue\": true } }");
        assertTrue(queued.getWork().effectiveQueue());
        assertFalse(queued.getWork().effectiveLooping());
        ActionDef plain = action("{ \"Id\": \"Mill\", \"Work\": { \"CycleMs\": 4805 } }");
        assertNull(plain.getWork().getQueue());
        assertFalse(plain.getWork().effectiveQueue());
    }

    @Test
    void recipeFallback_decodesItsThreeGroups_andTheMatchersExceptHole() throws Exception {
        ActionDef a = action("{ \"Id\": \"Unmake\", \"Recipe\": {"
                + " \"FromCrafting\": { \"Benches\": [ \"Fixture_Bench\" ] },"
                + " \"Fallback\": {"
                + "   \"Input\": { \"Tags\": { \"Type\": [ \"Weapon\", \"Armor\", \"Tool\" ] },"
                + "               \"Except\": { \"Tags\": { \"Type\": [ \"Ammo\" ] }, \"ItemId\": \"Fixture_Crate\" } },"
                + "   \"CraftingShare\": { \"Share\": 0.25 },"
                + "   \"EssenceOnly\": {} } } }");
        StationAsset.Fallback fallback = a.getRecipe().getFallback();
        assertNotNull(fallback);
        assertTrue(fallback.hasCraftingShare());
        assertEquals(0.25, fallback.getCraftingShare().effectiveShare());
        assertTrue(fallback.hasEssenceOnly());
        assertTrue(fallback.hasAnyRoute());
        ActionInput filter = fallback.getInput();
        assertNotNull(filter);
        assertEquals(3, filter.getTags().get("Type").length);
        ActionInput[] excepts = filter.getExcepts();
        assertNotNull(excepts);
        assertEquals(1, excepts.length, "one authored object reads as a one-entry hole list");
        ActionInput except = excepts[0];
        assertEquals("Fixture_Crate", except.getItemId());
        assertEquals("Ammo", except.getTags().get("Type")[0]);
        assertNull(except.getExcepts(), "an exclusion carves no hole of its own");
    }

    @Test
    void recipeFallback_partialGroups_readTheirRoutesOff() throws Exception {
        ActionDef a = action("{ \"Id\": \"Unmake\", \"Recipe\": { \"Fallback\": {"
                + " \"CraftingShare\": { \"Share\": 0 }, \"EssenceOnly\": { \"Enabled\": false } } } }");
        StationAsset.Fallback fallback = a.getRecipe().getFallback();
        assertFalse(fallback.hasCraftingShare(), "a non-positive share switches the route off");
        assertFalse(fallback.hasEssenceOnly());
        assertFalse(fallback.hasAnyRoute());
        assertNull(fallback.getInput());
    }

    @Test
    void exceptHole_decodesOnSelectAndOnACustodySocketMatch() throws Exception {
        ActionDef a = action("{ \"Id\": \"Unmake\","
                + " \"Select\": { \"Function\": \"Weapon\", \"Except\": { \"ItemId\": \"Fixture_Dagger\" } },"
                + " \"Custody\": { \"Sockets\": { \"slot_a\": { \"Item\": { \"Match\": { \"Function\": \"Tool\","
                + " \"Except\": { \"ResourceTypeId\": \"Fixture_Family\" } } }, \"MaxQuantity\": 1 } } } }");
        assertEquals("Fixture_Dagger", a.getSelect().getExcepts()[0].getItemId());
        assertEquals("Fixture_Family", a.getCustody().effectiveSockets().get(0).match().getExcepts()[0].getResourceTypeId());
    }

    @Test
    void anExtensionsOwnPace_decodes() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, "pack-pace", null);
        ExtensionAsset ext = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                "{ \"Target\": { \"Action\": \"Unmake\" }, \"Pace\": { \"Ladder\": {"
                        + " \"Factors\": [ { \"Factor\": \"fixture:level\" } ],"
                        + " \"Floors\": [ { \"Min\": 20, \"Scale\": 0.75 } ] } } }"),
                null, new AssetExtraInfo<>(data));
        assertNotNull(ext.getPace());
        assertEquals(0.75, ext.getPace().getLadder().getFloors()[0].effectiveScale());
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION, ExtensionAsset.PAYLOAD_PACE));
    }

    @Test
    void paceStretch_decodesOnAnExtension_withNoLadder_andOnAnAction() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, "pack-stretch", null);
        ExtensionAsset ext = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                "{ \"Target\": { \"Action\": \"Unmake\" }, \"Pace\": { \"Stretch\": 3.0 } }"),
                null, new AssetExtraInfo<>(data));
        assertEquals(3.0, ext.getPace().getStretch());
        assertNull(ext.getPace().getLadder(), "a stretch needs no ladder beside it");

        ExtensionAsset floored = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                "{ \"Target\": { \"Action\": \"Unmake\" }, \"Pace\": { \"Clamp\": { \"Min\": 0.42 } } }"),
                null, new AssetExtraInfo<>(new AssetExtraInfo.Data(ExtensionAsset.class, "pack-floor", null)));
        assertEquals(0.42, floored.getPace().getClamp().getMin(), "an extension's pace carries a Clamp of its own");
        assertNull(floored.getPace().getClamp().getMax());

        ActionDef a = action("{ \"Id\": \"Unmake\", \"Pace\": { \"Stretch\": 0.5,"
                + " \"Clamp\": { \"Min\": 0.4, \"Max\": 1.0 } } }");
        assertEquals(0.5, a.getPace().effectiveStretch());
        assertEquals(1.0, Pace.of(null, null).effectiveStretch(), "absent reads as no stretch");
    }

    @Test
    void stepPuppetClipMs_decodes_andIsAbsentByDefault() throws Exception {
        ActionDef a = action("{ \"Id\": \"Rite\", \"Steps\": ["
                + " { \"Id\": \"Open\", \"Puppet\": { \"Clip\": \"Fixture_Gesture\", \"ClipMs\": 500 } },"
                + " { \"Id\": \"Hold\", \"Puppet\": { \"Clip\": \"Fixture_Loop\" } } ] }");
        assertEquals("Fixture_Gesture", a.getSteps()[0].getPuppet().getClip());
        assertEquals(500L, a.getSteps()[0].getPuppet().getClipMs());
        assertNull(a.getSteps()[1].getPuppet().getClipMs(), "a clip with no ClipMs stays until the next one");
    }
}
