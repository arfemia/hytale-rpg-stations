package com.ziggfreed.rpgstations.asset;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.reward.RewardSpec;
import com.ziggfreed.rpgstations.loot.StationRewardKinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link StationAsset} codec surface under the ACTION-FIRST schema: a station keeps only
 * {@code Identity}/{@code Block}/{@code Requires}/{@code Flairs} plus an ORDERED {@code Actions}
 * ARRAY, and every group that describes a job lives inside an {@link ActionDef} entry. Covers id
 * canonicalization, {@link StationAsset#filenameFor} round-trip, native {@code Parent} inheritance
 * (wholesale inherit-on-omit at the top level plus sibling-leaf inherit inside a partially
 * overridden group), and each nested group's own decode.
 */
public class StationAssetCodecTest {

    private static StationAsset decodeAsset(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static StationAsset decodeWithParent(String body, StationAsset parent, String key, String parentKey)
            throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(StationAsset.class, key, parentKey);
        return StationAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), parent, new AssetExtraInfo<>(data));
    }

    // ==================== Id canonicalization + filenameFor ====================

    @Test
    void id_isLowercasedAtDecode() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(StationAsset.class, "Sharpening_Anvil", null);
        StationAsset a = StationAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{}"), null, new AssetExtraInfo<>(data));
        assertEquals("sharpening_anvil", a.getId());
    }

    @Test
    void filenameFor_isTheInverseOfTheDecodeLowering() {
        assertEquals("Sawmill", StationAsset.filenameFor("sawmill"));
        assertEquals("Sharpening_Anvil", StationAsset.filenameFor("sharpening_anvil"));
        assertEquals("Enchanting_Podium", StationAsset.filenameFor("enchanting_podium"));
    }

    // ==================== Station scope: the four surviving groups ====================

    @Test
    void station_keepsOnlyIdentityBlockRequiresFlairsAndActions() {
        // "Tags" rides every Pattern-A asset from AssetBuilderCodec itself, so it is not a station group.
        assertEquals(java.util.Set.of("Name", "Tags", "Identity", "Block", "Requires", "Flairs", "Actions"),
                StationAsset.CODEC.getEntries().keySet(),
                "a station describes itself and lists its actions; every job group lives in an action");
    }

    @Test
    void decodesIdentityAndBlock() throws Exception {
        StationAsset a = decodeAsset("{ \"Identity\": { \"NameKey\": \"rpgstations.station.fixture.name\","
                + " \"DescKey\": \"rpgstations.station.fixture.desc\", \"Icon\": \"Fixture_Icon\" },"
                + " \"Block\": { \"Exclusive\": false } }");

        assertEquals("rpgstations.station.fixture.name", a.getIdentity().getNameKey());
        assertEquals("rpgstations.station.fixture.desc", a.getIdentity().getDescKey());
        assertEquals("Fixture_Icon", a.getIdentity().getIcon());
        assertFalse(a.getBlock().effectiveExclusive());
        assertNull(a.getActions());
    }

    @Test
    void block_exclusiveReaderDefaultsToTrueEvenWithNoGroupAuthored() throws Exception {
        assertTrue(StationAsset.Block.effectiveExclusive(null));
        assertTrue(StationAsset.Block.effectiveExclusive(StationAsset.Block.of(null)));
        StationAsset a = decodeAsset("{ \"Block\": {} }");
        assertTrue(a.getBlock().effectiveExclusive());
    }

    // ==================== Actions: an ORDERED ARRAY ====================

    @Test
    void actions_decodeAsAnOrderedArrayCarryingEveryJobGroup() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": ["
                + " { \"Id\": \"Alpha\", \"Select\": { \"ResourceTypeId\": \"Fixture_Family\" },"
                + "   \"Tool\": { \"Gather\": { \"GatherType\": \"Fixtures\", \"MinPower\": 0.25 } },"
                + "   \"Recipe\": { \"FromCrafting\": { \"Categories\": [\"FixturePlanks\"] },"
                + "     \"Yield\": { \"Base\": 3 } },"
                + "   \"Work\": { \"CycleMs\": 1234, \"Looping\": false },"
                + "   \"Custody\": { \"MaxQuantity\": 7 },"
                + "   \"Bonus\": { \"Lootables\": [\"FixtureFinds\"] },"
                + "   \"ContributionScale\": { \"Floors\": [ { \"Min\": 5, \"Scale\": 2.0 } ] },"
                + "   \"Worker\": { \"Hold\": { \"EffectId\": \"Fixture_Hold\" },"
                + "     \"Camera\": { \"Recipe\": \"LookRot\" },"
                + "     \"Animation\": { \"EmoteId\": \"Fixture_Emote\" },"
                + "     \"Puppet\": { \"Enabled\": true } },"
                + "   \"Moments\": { \"Cycle\": { \"Sounds\": [\"Fixture_Cycle\"] },"
                + "     \"Completion\": { \"Sounds\": [\"Fixture_Done\"] } } },"
                + " { \"Id\": \"Beta\", \"Ref\": \"SharedFixtureAction\" } ] }");

        ActionDef[] actions = a.getActions();
        assertEquals(2, actions.length);

        ActionDef alpha = actions[0];
        assertEquals("Alpha", alpha.getId());
        assertEquals("Fixture_Family", alpha.getSelect().getResourceTypeId());
        assertEquals("Fixtures", alpha.getTool().getGather().getGatherType());
        assertEquals("FixturePlanks", alpha.getRecipe().getFromCrafting().getCategories()[0]);
        assertEquals(3, alpha.getRecipe().getYield().getBase());
        assertEquals(1234L, alpha.getWork().getCycleMs());
        assertFalse(alpha.getWork().effectiveLooping());
        assertEquals(7, alpha.getCustody().getMaxQuantity());
        assertEquals("FixtureFinds", alpha.getBonus().getLootables()[0]);
        assertEquals(2.0, alpha.getContributionScale().getFloors()[0].effectiveScale());
        assertEquals("Fixture_Hold", alpha.getWorker().getHold().getEffectId());
        assertEquals("LookRot", alpha.getWorker().getCamera().getRecipe());
        assertEquals("Fixture_Emote", alpha.getWorker().getAnimation().getEmoteId());
        assertTrue(alpha.getWorker().getPuppet().effectiveEnabled());
        assertEquals("Fixture_Cycle", alpha.getMoments().get("Cycle").getSounds()[0].getEventId());
        assertEquals("Fixture_Done", alpha.getMoments().get("Completion").getSounds()[0].getEventId());

        ActionDef beta = actions[1];
        assertEquals("Beta", beta.getId());
        assertTrue(beta.hasRef());
        assertEquals("SharedFixtureAction", beta.getRef());
        assertNull(beta.getRecipe(), "a Ref-only entry authors no group of its own");
    }

    @Test
    void actions_preserveAuthoredOrder() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"First\" }, { \"Id\": \"Second\" },"
                + " { \"Id\": \"Third\" } ] }");
        assertEquals("First", a.getActions()[0].getId());
        assertEquals("Second", a.getActions()[1].getId());
        assertEquals("Third", a.getActions()[2].getId());
    }

    @Test
    void actions_parentInheritsWholesaleOnOmit_ownReplacesTheWholeArray() throws Exception {
        StationAsset parent = decodeWithParent("{ \"Actions\": [ { \"Id\": \"Base\" } ] }",
                null, "actions_parent", null);
        assertEquals(1, parent.getActions().length);

        StationAsset inherited = decodeWithParent("{}", parent, "actions_child", "actions_parent");
        assertNotNull(inherited.getActions());
        assertEquals("Base", inherited.getActions()[0].getId());

        StationAsset replaced = decodeWithParent("{ \"Actions\": [ { \"Id\": \"Own\" } ] }",
                parent, "actions_own", "actions_parent");
        assertEquals(1, replaced.getActions().length);
        assertEquals("Own", replaced.getActions()[0].getId());
    }

    // ==================== Requires (the station-entry gate) ====================

    @Test
    void requires_decodesPermissionAndConditions() throws Exception {
        StationAsset a = decodeAsset("{ \"Requires\": { \"Permission\": \"myserver.stations.fixture\","
                + " \"Conditions\": [ { \"Factor\": \"yourmod:reputation\", \"Param\": \"GUILD\", \"Min\": 15 } ] } }");
        assertNotNull(a.getRequires());
        assertEquals("myserver.stations.fixture", a.getRequires().getPermission());
        assertEquals(1, a.getRequires().getConditions().length);
        assertEquals("yourmod:reputation", a.getRequires().getConditions()[0].getFactor());
        assertEquals("GUILD", a.getRequires().getConditions()[0].getParam());
        assertEquals(15.0, a.getRequires().getConditions()[0].getMin());
        assertNull(a.getRequires().getConditions()[0].getMax());
    }

    @Test
    void requires_parentInheritsWholesaleOnOmit_ownWins() throws Exception {
        StationAsset parent = decodeWithParent(
                "{ \"Requires\": { \"Permission\": \"myserver.stations.base\" } }",
                null, "requires_parent", null);
        assertEquals("myserver.stations.base", parent.getRequires().getPermission());

        StationAsset child = decodeWithParent("{}", parent, "requires_child", "requires_parent");
        assertNotNull(child.getRequires(), "Requires inherits wholesale on omit");
        assertEquals("myserver.stations.base", child.getRequires().getPermission());

        StationAsset ownChild = decodeWithParent(
                "{ \"Requires\": { \"Permission\": \"myserver.stations.override\" } }",
                parent, "requires_own", "requires_parent");
        assertEquals("myserver.stations.override", ownChild.getRequires().getPermission());
    }

    @Test
    void requires_isEmpty_trueWhenNeitherLeafAuthored() {
        assertTrue(Requires.of(null, null).isEmpty());
        assertFalse(Requires.of("a.permission", null).isEmpty());
    }

    // ==================== Block: sibling-leaf Parent inherit inside a group ====================

    @Test
    void block_parentSiblingLeafInherit() throws Exception {
        StationAsset parent = decodeWithParent("{ \"Block\": { \"Exclusive\": false } }",
                null, "block_parent", null);
        StationAsset child = decodeWithParent("{ \"Block\": {} }", parent, "block_child", "block_parent");
        assertFalse(child.getBlock().effectiveExclusive(),
                "an authored-but-empty group still inherits its parent's leaves");
    }

    // ==================== Flairs (station scope, an open moment map) ====================

    @Test
    void flairs_decodeAsAnOpenMomentMap() throws Exception {
        StationAsset a = decodeAsset("{ \"Flairs\": { \"gilded\": { \"Moments\": {"
                + " \"cycle\": { \"Sounds\": [\"Fixture_Gilded\"] } } } } }");
        assertEquals(1, a.getFlairs().size());
        assertEquals("Fixture_Gilded",
                a.getFlairs().get("gilded").getMoments().get("cycle").getSounds()[0].getEventId());
    }

    // ==================== Yield: purely deterministic, four leaves ====================

    @Test
    void yield_authorsExactlyBaseScaleMinMax() {
        assertEquals(java.util.Set.of("Base", "Scale", "Min", "Max"),
                StationAsset.Yield.CODEC.getEntries().keySet(),
                "everything conditional is a Bonus Roll instead, never hidden inside Yield");
    }

    @Test
    void yield_decodesEveryLeafAndReaderDefaultsScale() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": { \"Yield\": {"
                + " \"Base\": 2, \"Scale\": 1.5, \"Min\": 2, \"Max\": 9 } } } ] }");
        StationAsset.Yield y = a.getActions()[0].getRecipe().getYield();
        assertEquals(2, y.getBase());
        assertEquals(1.5, y.effectiveScale());
        assertEquals(2, y.getMin());
        assertEquals(9, y.getMax());

        StationAsset.Yield bare = StationAsset.Yield.of(1, null, null, null);
        assertEquals(1.0, bare.effectiveScale(), "an absent Scale is the neutral 1.0");
        assertEquals(1.0, StationAsset.Yield.of(1, -2.0, null, null).effectiveScale(),
                "a non-positive Scale reader-defaults to neutral rather than erasing output");
    }

    // ==================== Recipe: one per action, no per-recipe tool ====================

    @Test
    void recipe_authorsConversionsFromCraftingYieldAndDonenessOnly() {
        assertEquals(java.util.Set.of("Conversions", "FromCrafting", "Yield", "Doneness"),
                StationAsset.Recipe.CODEC.getEntries().keySet(),
                "the ACTION's Tool is the one gate; a recipe never carries its own");
    }

    @Test
    void recipe_decodesMultiInputMultiOutputConversions() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": { \"Conversions\": [ {"
                + " \"Input\": [ { \"ItemId\": \"Fixture_Plank\", \"Quantity\": 2 },"
                + "              { \"ResourceTypeId\": \"Fixture_Nails\", \"Quantity\": 1 } ],"
                + " \"Output\": [ { \"ItemId\": \"Fixture_Crate\", \"Quantity\": 1 },"
                + "               { \"ItemId\": \"Fixture_Offcut\", \"Quantity\": 4 } ],"
                + " \"DurationMs\": 900, \"Category\": \"FixtureBoxes\" } ] } } ] }");
        StationAsset.Conversion c = a.getActions()[0].getRecipe().getConversions()[0];
        assertEquals(2, c.getInput().length);
        assertEquals("Fixture_Plank", c.primaryInput().getItemId());
        assertEquals("Fixture_Nails", c.getInput()[1].getResourceTypeId());
        assertEquals(2, c.getOutput().length);
        assertEquals("Fixture_Crate", c.primaryOutput().getItemId());
        assertEquals(4, c.getOutput()[1].effectiveQuantity());
        assertEquals(900L, c.getDurationMs());
        assertEquals("FixtureBoxes", c.getCategory());
        assertTrue(c.isComplete());
    }

    @Test
    void conversion_decodesTierExactSetAndTagsRoute() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": { \"Conversions\": [ {"
                + " \"Input\": [ { \"Tags\": { \"Fixture_Family\": [\"Prepared\"] }, \"Quantity\": 2,"
                + "               \"Socket\": \"Rack\" },"
                + "              { \"Quantity\": 3 } ],"
                + " \"Output\": [ { \"ItemId\": \"Fixture_Stew\" } ],"
                + " \"Tier\": 2, \"IsExactSet\": true } ] } } ] }");
        StationAsset.Conversion c = a.getActions()[0].getRecipe().getConversions()[0];
        assertEquals(2, c.getTier());
        assertEquals(2, c.effectiveTier());
        assertTrue(c.effectiveIsExactSet());
        assertFalse(c.isDerived(), "an authored row never wears the derived mark");
        Ingredient tagged = c.getInput()[0];
        assertTrue(tagged.hasTagsRoute());
        assertEquals("Prepared", tagged.getTags().get("Fixture_Family")[0]);
        assertEquals("Rack", tagged.getSocket());
        Ingredient matchAny = c.getInput()[1];
        assertTrue(matchAny.isMatchAny(), "a route-less input decodes as the legal match-any form");
        assertEquals(3, matchAny.effectiveQuantity());
    }

    @Test
    void conversion_tierAndExactSetReaderDefaults() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": { \"Conversions\": [ {"
                + " \"Input\": [ { \"ItemId\": \"Fixture_In\" } ],"
                + " \"Output\": [ { \"ItemId\": \"Fixture_Out\" } ] } ] } } ] }");
        StationAsset.Conversion c = a.getActions()[0].getRecipe().getConversions()[0];
        assertNull(c.getTier());
        assertEquals(0, c.effectiveTier(), "unauthored Tier reads as 0 (authored order intact)");
        assertNull(c.getIsExactSet());
        assertFalse(c.effectiveIsExactSet());
    }

    @Test
    void recipe_isRunnableOnlyWithConversionsOrADeriveRule() {
        assertFalse(StationAsset.Recipe.of(null, null, null).isRunnable());
        assertTrue(StationAsset.Recipe.of(null,
                StationAsset.FromCrafting.of(new String[] {"FixturePlanks"}), null).isRunnable());
        assertTrue(StationAsset.Recipe.of(new StationAsset.Conversion[] {
                StationAsset.Conversion.of(Ingredient.item("Fixture_A", 1), Ingredient.item("Fixture_B", 1))},
                null, null).isRunnable());
    }

    @Test
    void fromCrafting_decodesBenchesTypesAndNativeTime() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": { \"FromCrafting\": {"
                + " \"Categories\": [\"FixturePlanks\"], \"Benches\": [\"FixtureBench\"],"
                + " \"Types\": [\"Processing\"], \"NativeTime\": { \"Scale\": 2.0, \"OffsetMs\": 750 } } } } ] }");
        StationAsset.FromCrafting fc = a.getActions()[0].getRecipe().getFromCrafting();
        assertEquals("FixturePlanks", fc.getCategories()[0]);
        assertEquals("FixtureBench", fc.getBenches()[0]);
        assertEquals("Processing", fc.getTypes()[0]);
        assertEquals(2.0, fc.getNativeTime().effectiveScale());
        assertEquals(750L, fc.getNativeTime().effectiveOffsetMs());
    }

    // ==================== Work: no Exclusive leaf any more ====================

    @Test
    void work_carriesNoExclusiveLeaf() {
        assertEquals(java.util.Set.of("CycleMs", "MaxDurationMs", "MaxMoveMeters",
                        "PerCycleContributions", "Idle", "Looping"),
                StationAsset.Work.CODEC.getEntries().keySet(),
                "one worker per placed block is a Block property, not a Work one");
    }

    @Test
    void work_decodesContributionsAndIdle() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Work\": {"
                + " \"CycleMs\": 4665, \"MaxDurationMs\": 600000, \"MaxMoveMeters\": 2.5,"
                + " \"PerCycleContributions\": [ { \"Channel\": \"yourmod:test\", \"Param\": \"ALPHA\","
                + "   \"Amount\": 8.0 } ],"
                + " \"Idle\": { \"CycleMs\": 15000, \"Fraction\": 0.2 } } } ] }");
        StationAsset.Work w = a.getActions()[0].getWork();
        assertEquals(4665L, w.getCycleMs());
        assertEquals(600000L, w.getMaxDurationMs());
        assertEquals(2.5, w.getMaxMoveMeters());
        assertEquals("yourmod:test", w.getPerCycleContributions()[0].getChannel());
        assertEquals("ALPHA", w.getPerCycleContributions()[0].getParam());
        assertEquals(8.0, w.getPerCycleContributions()[0].getAmount());
        assertTrue(w.getIdle().effectiveEnabled(), "an authored Idle group means idle practice is on");
        assertEquals(15000L, w.getIdle().getCycleMs());
        assertEquals(0.2, w.getIdle().getFraction());
        assertTrue(w.effectiveLooping());
    }

    // ==================== Worker + Moments: grouping, not new concepts ====================

    @Test
    void worker_groupsTheFourPresentationConcerns() {
        assertEquals(java.util.Set.of("Hold", "Camera", "Animation", "Puppet"),
                ActionDef.Worker.CODEC.getEntries().keySet());
    }

    @Test
    void moments_isAnOpenMapKeyedByMomentId_matchedCaseInsensitively() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Moments\": {"
                + " \"Cycle\": { \"Sounds\": [\"Fixture_Cycle\"] },"
                + " \"swing\": { \"Sounds\": [\"Fixture_Swing\"] },"
                + " \"impact\": { \"DelayMs\": 140, \"Sounds\": [\"Fixture_Impact\"] },"
                + " \"step:a:strike\": { \"Sounds\": [\"Fixture_Step\"] } } } ] }");
        Map<String, Presentation> moments = a.getActions()[0].getMoments();

        assertEquals(java.util.Set.of("Cycle", "swing", "impact", "step:a:strike"), moments.keySet(),
                "the map keeps whatever casing was authored; the engine canonicalizes on resolve");
        assertEquals("Fixture_Cycle", moments.get("Cycle").getSounds()[0].getEventId());
        assertEquals(140L, moments.get("impact").effectiveDelayMs());
        assertEquals("Fixture_Step", moments.get("step:a:strike").getSounds()[0].getEventId());
    }

    @Test
    void worker_decodesHoldMountAndSwingCadence() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Worker\": {"
                + " \"Hold\": { \"MovementLock\": true, \"EffectId\": \"Fixture_Hold\","
                + "   \"Mount\": { \"Surface\": \"Entity\", \"Entity\": {"
                + "     \"Offset\": { \"Z\": 0.6 }, \"VisibleAnchorItemId\": \"Fixture_Anchor\" } } },"
                + " \"Animation\": { \"EmoteId\": \"Fixture_Emote\", \"ActionClip\": \"Chop\","
                + "   \"Swing\": { \"IntervalMs\": 900 } } } } ] }");
        ActionDef.Worker w = a.getActions()[0].getWorker();
        assertTrue(w.getHold().getMount().isEntitySurface());
        assertEquals(0.6, w.getHold().getMount().getEntity().getOffset().getZ());
        assertEquals("Fixture_Anchor", w.getHold().getMount().getEntity().getVisibleAnchorItemId());
        assertEquals("Chop", w.getAnimation().getActionClip());
        assertEquals(900L, w.getAnimation().getSwing().getIntervalMs());
        assertEquals(java.util.Set.of("IntervalMs"), StationAsset.Animation.Swing.CODEC.getEntries().keySet(),
                "Swing is pure cadence: the swing and impact CUES are Moments entries, not leaves here");
    }

    @Test
    void puppet_decodesHideLookOffsetAndProp() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Worker\": { \"Puppet\": {"
                + " \"Enabled\": true, \"Hide\": { \"Route\": \"Scale\" },"
                + " \"Look\": { \"Source\": \"NpcRole\", \"FallbackModelId\": \"Fixture_Model\","
                + "   \"Role\": { \"RoleId\": \"Fixture_Role\", \"SkinSource\": \"PlayerClone\","
                + "     \"Persist\": true, \"SpeedMps\": 3.5 } },"
                + " \"Offset\": { \"X\": 0.0, \"Y\": -0.4, \"Z\": 1.0 },"
                + " \"Rotation\": { \"Yaw\": 90.0, \"Pitch\": 15.0, \"Roll\": -5.0 },"
                + " \"Prop\": { \"Source\": \"MirrorHeld\", \"Slot\": \"Hotbar\" } } } } ] }");
        Puppet p = a.getActions()[0].getWorker().getPuppet();
        assertTrue(p.effectiveEnabled());
        assertEquals("Scale", p.getHide().getRoute());
        assertEquals("NpcRole", p.getLook().getSource());
        assertEquals("Fixture_Model", p.getLook().getFallbackModelId());
        assertEquals("Fixture_Role", p.getLook().getRole().getRoleId());
        assertEquals(3.5, p.getLook().getRole().getSpeedMps());
        assertEquals(-0.4, p.getOffset().getY());
        assertEquals(90.0, p.effectiveYawDegrees());
        assertEquals(15.0, p.effectivePitchDegrees());
        assertEquals(-5.0, p.effectiveRollDegrees());
        assertEquals("MirrorHeld", p.getProp().getSource());
    }

    // ==================== Bonus: the LootRef vocabulary, plus OutputItems ====================

    @Test
    void bonus_decodesLootablesAndInlineRolls() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Bonus\": {"
                + " \"Lootables\": [\"FixtureFinds\"],"
                + " \"Rolls\": [ { \"Trigger\": \"Cycle\","
                + "   \"Chance\": { \"Base\": 25, \"Clamp\": { \"Max\": 80 } },"
                + "   \"Grants\": { \"DropLists\": [\"Fixture_T1\"],"
                + "     \"Rewards\": [ { \"Kind\": \"rpgstations:output_items\","
                + "       \"Params\": { \"Count\": \"2\" } } ] } } ] } } ] }");
        LootRef bonus = a.getActions()[0].getBonus();
        assertEquals("FixtureFinds", bonus.getLootables()[0]);
        Roll roll = bonus.getRolls()[0];
        assertEquals("Cycle", roll.effectiveTrigger());
        assertEquals(25.0, roll.getChance().getBase());
        assertEquals(80.0, roll.getChance().getClamp().getMax());
        assertEquals("Fixture_T1", roll.getGrants().getDropLists()[0]);
        RewardSpec extraOutput = roll.getGrants().rewardSpecs().get(0);
        assertEquals(StationRewardKinds.KIND_OUTPUT_ITEMS, extraOutput.kind());
        assertEquals(2.0, extraOutput.doubleParam("count", 0.0));
    }

    /**
     * Extra units of the cycle's own output are a registered REWARD KIND, so the same
     * {@code Grants} shape reaches this engine's own payouts and any other mod's without the loot
     * layer knowing what either means.
     */
    @Test
    void grantsOutputItemsReward_readerDefaultsAndEmptiness() {
        assertTrue(LootGrants.of(null, null, null, null).isEmpty());
        LootGrants extra = LootGrants.of(null, null, null, new LootGrants.Reward[] {
                LootGrants.Reward.of(StationRewardKinds.KIND_OUTPUT_ITEMS, Map.of("Count", "0.5"))});
        assertFalse(extra.isEmpty(),
                "a fraction-only amount grants an item some of the time, so it is not empty");
        assertEquals(0.5, extra.rewardSpecs().get(0).doubleParam("count", 0.0));
    }

    /**
     * A Roll carries its OWN top-level cue, so a plain chance roll needs no degenerate one-floor
     * Ladder to hang one on. A cue is a MOMENT ID the granting site plays, never a presentation
     * body - the same id an action's {@code Moments} map and a flair both key on.
     */
    @Test
    void bonus_rollDecodesItsOwnTopLevelCue() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Bonus\": { \"Rolls\": [ {"
                + " \"Trigger\": \"Cycle\", \"Chance\": { \"Base\": 0.5 },"
                + " \"Grants\": { \"Commands\": [\"give {player} Fixture_Trophy\"] },"
                + " \"Cue\": \"cue:trophy\" } ] } } ] }");
        Roll roll = a.getActions()[0].getBonus().getRolls()[0];

        assertEquals("cue:trophy", roll.getCue());
        assertNull(roll.getLadder(), "no ladder is needed to carry a roll-level cue");
    }

    @Test
    void bonus_rollCueOmitted_decodesNull() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Bonus\": { \"Rolls\": [ {"
                + " \"Grants\": { \"DropLists\": [\"Fixture_T1\"] } } ] } } ] }");
        assertNull(a.getActions()[0].getBonus().getRolls()[0].getCue());
    }

    /** The amount is FRACTIONAL: a half-step tier is authorable on the floor that earns it. */
    @Test
    void bonus_ladderFloorGrantsExtraOutput() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Bonus\": { \"Rolls\": [ {"
                + " \"Ladder\": { \"Factors\": [ { \"Factor\": \"hytale:tool_quality\", \"Weight\": 10.0 } ],"
                + "   \"Floors\": [ { \"Min\": 11, \"Grants\": { \"Rewards\": [ { \"Kind\":"
                + "        \"rpgstations:output_items\", \"Params\": { \"Count\": \"1\" } } ] } },"
                + "                 { \"Min\": 33, \"Grants\": { \"Rewards\": [ { \"Kind\":"
                + "        \"rpgstations:output_items\", \"Params\": { \"Count\": \"2.5\" } } ] } } ] } } ] } } ] }");
        Roll.Ladder ladder = a.getActions()[0].getBonus().getRolls()[0].getLadder();
        assertEquals("hytale:tool_quality", ladder.getFactors()[0].getFactor());
        assertEquals(10.0, ladder.getFactors()[0].weightOrDefault());
        assertEquals(1.0, ladder.getFloors()[0].getGrants().rewardSpecs().get(0)
                .doubleParam("count", 0.0));
        assertEquals(2.5, ladder.getFloors()[1].getGrants().rewardSpecs().get(0)
                        .doubleParam("count", 0.0),
                "a whole-number floor and a fractional one decode through the same leaf");
    }

    // ==================== ContributionScale ====================

    @Test
    void contributionScale_decodesFactorsAndFloors() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"ContributionScale\": {"
                + " \"Factors\": [ { \"Factor\": \"hytale:tool_quality\", \"Weight\": 10.0 },"
                + "                { \"Factor\": \"hytale:tool_power\" } ],"
                + " \"Floors\": [ { \"Min\": 11, \"Scale\": 2.0 }, { \"Min\": 33 } ] } } ] }");
        ContributionScale scale = a.getActions()[0].getContributionScale();
        assertEquals(2, scale.getFactors().length);
        assertEquals(1.0, scale.getFactors()[1].weightOrDefault());
        assertEquals(11.0, scale.getFloors()[0].effectiveMin());
        assertEquals(2.0, scale.getFloors()[0].effectiveScale());
        assertEquals(ContributionScale.NEUTRAL_SCALE, scale.getFloors()[1].effectiveScale(),
                "a floor authoring no Scale is the neutral multiplier, never zero");
    }

    // ==================== Custody ====================

    @Test
    void custody_decodesStatesInputAndDisplay() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Custody\": {"
                + " \"MaxQuantity\": 64, \"SingleFamily\": true,"
                + " \"Input\": { \"ResourceTypeId\": \"Fixture_Family\" },"
                + " \"States\": { \"Empty\": \"Default\", \"Loaded\": \"Loaded\", \"Working\": \"Lit\" },"
                + " \"Display\": { \"Offset\": { \"Y\": -0.1 }, \"Scale\": 0.4,"
                + "   \"Rotation\": { \"Yaw\": 0.0, \"Roll\": 90.0 } } } } ] }");
        Custody c = a.getActions()[0].getCustody();
        assertEquals(64, c.effectiveMaxQuantity());
        assertTrue(c.effectiveSingleFamily());
        assertEquals("Fixture_Family", c.getInput().getResourceTypeId());
        assertEquals("Lit", c.getStates().getWorking());
        assertEquals(-0.1, c.getDisplay().getOffset().getY());
        assertEquals(0.4, c.getDisplay().getScale());
        assertEquals(90.0, c.getDisplay().getRotation().getRoll());
    }

    @Test
    void custody_decodesTheDonenessStatePair() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Custody\": {"
                + " \"States\": { \"Empty\": \"Default\", \"Loaded\": \"Loaded\","
                + "   \"Ready\": \"Steaming\", \"Overdone\": \"Burnt\" } } } ] }");
        Custody.States states = a.getActions()[0].getCustody().getStates();
        assertEquals("Steaming", states.getReady());
        assertEquals("Burnt", states.getOverdone());
        assertNull(states.getWorking(), "the five state leaves stay independently nullable");
    }

    // ==================== Doneness (the ready window, both altitudes) ====================

    @Test
    void doneness_decodesOnTheRecipeAndOnAConversion() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": {"
                + " \"Doneness\": { \"ReadyMs\": 60000,"
                + "   \"Overdone\": [ { \"ItemId\": \"Fixture_Charcoal\", \"Quantity\": 2 } ] },"
                + " \"Conversions\": [ {"
                + "   \"Input\": [ { \"ItemId\": \"Fixture_In\" } ],"
                + "   \"Output\": [ { \"ItemId\": \"Fixture_Out\" } ],"
                + "   \"Doneness\": { \"ReadyMs\": 5000 } } ] } } ] }");
        StationAsset.Recipe recipe = a.getActions()[0].getRecipe();
        assertEquals(60000L, recipe.getDoneness().getReadyMs());
        assertEquals("Fixture_Charcoal", recipe.getDoneness().getOverdone()[0].getItemId());
        assertEquals(2, recipe.getDoneness().getOverdone()[0].effectiveQuantity());
        StationAsset.Conversion row = recipe.getConversions()[0];
        assertEquals(5000L, row.getDoneness().getReadyMs());
        assertNull(row.getDoneness().getOverdone(),
                "the row authors only ReadyMs; the recipe default supplies Overdone at resolve time");
    }

    @Test
    void doneness_readerDefaultsToNoWindow() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Recipe\": { \"Conversions\": [ {"
                + " \"Input\": [ { \"ItemId\": \"Fixture_In\" } ],"
                + " \"Output\": [ { \"ItemId\": \"Fixture_Out\" } ] } ] } } ] }");
        StationAsset.Recipe recipe = a.getActions()[0].getRecipe();
        assertNull(recipe.getDoneness(), "unauthored Doneness reads as null - deterministic exactly as before");
        assertNull(recipe.getConversions()[0].getDoneness());
        assertNull(StationAsset.Doneness.resolve(null, null),
                "neither altitude authored = no window anywhere");
    }

    @Test
    void doneness_copyWithsCarryTheLeaf() {
        StationAsset.Doneness doneness = StationAsset.Doneness.of(1000L, null);
        StationAsset.Conversion row = StationAsset.Conversion
                .of(Ingredient.item("Fixture_In", 1), Ingredient.item("Fixture_Out", 1))
                .withDoneness(doneness);
        assertEquals(doneness, row.withTier(3).getDoneness(),
                "withTier carries the doneness leaf");
        assertEquals(doneness, row.withExactSet(true).getDoneness(),
                "withExactSet carries the doneness leaf");
        assertEquals(3, row.withTier(3).withDoneness(doneness).getTier(),
                "withDoneness carries the tier leaf");
    }

    // ==================== Anchors + Steps stay per-action ====================

    @Test
    void anchors_decodeAsANamedMapWithARadiusDefault() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Anchors\": {"
                + " \"Fire\": { \"Station\": \"FixtureFire\", \"MaxRadiusMeters\": 8 },"
                + " \"Bench\": { \"Station\": \"FixtureBench\" } } } ] }");
        var anchors = a.getActions()[0].getAnchors();
        assertEquals("FixtureFire", anchors.get("Fire").getStation());
        assertEquals(8.0, anchors.get("Fire").effectiveMaxRadiusMeters());
        assertEquals(ActionDef.Anchor.DEFAULT_MAX_RADIUS_METERS,
                anchors.get("Bench").effectiveMaxRadiusMeters());
    }

    @Test
    void steps_decodeInsideAnAction() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": [ { \"Id\": \"A\", \"Steps\": ["
                + " { \"Id\": \"Load\", \"Consume\": { \"Items\": [ { \"ResourceTypeId\": \"Fixture_Family\","
                + "   \"Quantity\": 1 } ], \"From\": \"Custody\" } },"
                + " { \"Id\": \"Beat\", \"Duration\": { \"Ms\": 600 }, \"IsWork\": true } ] } ] }");
        StationStep[] steps = a.getActions()[0].getSteps();
        assertEquals(2, steps.length);
        assertEquals("Load", steps[0].getId());
        assertEquals(StationStep.Consume.FROM_CUSTODY, steps[0].getConsume().effectiveFrom());
        assertEquals(600L, steps[1].getDuration().getMs());
        assertTrue(steps[1].effectiveIsWork());
    }
}
