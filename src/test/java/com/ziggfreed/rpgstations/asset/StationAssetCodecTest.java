package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * Codec layer for RpgStations' ported {@link StationAsset}: id lowercase canonicalization,
 * {@link StationAsset#filenameFor} round-trip, native {@code Parent} inheritance (wholesale
 * top-level inherit-on-omit + sibling-leaf inherit inside a partially-overridden group), the
 * schema deltas this leg introduces ({@link Requires}/{@link Condition} replacing the MMO's
 * {@code content.gate.Requirements}, {@link Presentation}'s {@link Presentation.Shake} leaf
 * replacing {@code Feedback}), and (leg 3) {@code Loot} replacing {@code Luck} entirely - see
 * {@link Roll}'s javadoc for the M3-critique-fixed schema. Ported (trimmed to the
 * representative cases; the codec class itself is a mechanical, vetted copy - see
 * {@link StationAsset}'s javadoc) from the MMO's {@code StationAssetCodecTest}.
 */
public class StationAssetCodecTest {

    private static StationAsset decodeAsset(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body), new ExtraInfo());
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

    // ==================== Basic shape decode ====================

    @Test
    void decodesIdentityWorkRecipeToolAnimationPresentation() throws Exception {
        StationAsset a = decodeAsset("{ \"Identity\": { \"NameKey\": \"rpgstations.station.sawmill.name\","
                + " \"DescKey\": \"rpgstations.station.sawmill.desc\", \"Icon\": \"Wood_Hardwood_Planks\" },"
                + " \"Work\": { \"CycleMs\": 4665, \"MaxDurationMs\": 600000,"
                + "   \"Xp\": [ { \"Skill\": \"WOODCUTTING\", \"PerCycle\": 8.0 } ] },"
                + " \"Recipe\": { \"FromCrafting\": { \"Categories\": [\"WoodPlanks\"] } },"
                + " \"Hold\": { \"EffectId\": \"RPG_Station_Hold\", \"Seat\": { \"Enabled\": true } },"
                + " \"Tool\": { \"Gather\": { \"GatherType\": \"Woods\", \"MinPower\": 0.1 } },"
                + " \"Animation\": { \"EmoteId\": \"RPG_Emote_Saw\" },"
                + " \"Presentation\": { \"Sound\": \"SFX_Wood_Break\" } }");

        assertEquals("rpgstations.station.sawmill.name", a.getIdentity().getNameKey());
        assertEquals("Wood_Hardwood_Planks", a.getIdentity().getIcon());
        assertEquals(4665L, a.getWork().getCycleMs());
        assertEquals("WOODCUTTING", a.getWork().getXp()[0].getSkill());
        assertNull(a.getRecipe().getConversions());
        assertEquals("WoodPlanks", a.getRecipe().getFromCrafting().getCategories()[0]);
        assertEquals("RPG_Station_Hold", a.getHold().getEffectId());
        assertEquals(Boolean.TRUE, a.getHold().getSeat().getEnabled());
        assertEquals("Woods", a.getTool().getGather().getGatherType());
        assertEquals("RPG_Emote_Saw", a.getAnimation().getEmoteId());
        assertEquals("SFX_Wood_Break", a.getPresentation().getSound());
        assertNull(a.getRequires());
    }

    // ==================== Requires (design section 4.4.2, this leg's schema delta) ====================

    @Test
    void requires_decodesPermissionAndConditions() throws Exception {
        StationAsset a = decodeAsset("{ \"Requires\": { \"Permission\": \"myserver.stations.sawmill\","
                + " \"Conditions\": [ { \"Factor\": \"mmoskilltree:skill_level\", \"Param\": \"WOODCUTTING\", \"Min\": 15 } ] } }");
        assertNotNull(a.getRequires());
        assertEquals("myserver.stations.sawmill", a.getRequires().getPermission());
        assertEquals(1, a.getRequires().getConditions().length);
        assertEquals("mmoskilltree:skill_level", a.getRequires().getConditions()[0].getFactor());
        assertEquals("WOODCUTTING", a.getRequires().getConditions()[0].getParam());
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
        Requires r = Requires.of(null, null);
        org.junit.jupiter.api.Assertions.assertTrue(r.isEmpty());
        Requires withPermission = Requires.of("x", null);
        org.junit.jupiter.api.Assertions.assertFalse(withPermission.isEmpty());
    }

    // ==================== Presentation.Shake (replaces Feedback this leg) ====================

    @Test
    void presentationShake_decodes() throws Exception {
        StationAsset a = decodeAsset("{ \"Completion\": { \"Sound\": \"SFX_Chest_Wooden_Open_Player\","
                + " \"Shake\": { \"EffectId\": \"Damage_Shake\", \"Intensity\": 0.4 } } }");
        assertNotNull(a.getCompletion());
        assertNotNull(a.getCompletion().getShake());
        assertEquals("Damage_Shake", a.getCompletion().getShake().getEffectId());
        assertEquals(0.4, a.getCompletion().getShake().getIntensity());
    }

    @Test
    void presentationShake_omitted_decodesNull() throws Exception {
        StationAsset a = decodeAsset("{ \"Presentation\": { \"Sound\": \"SFX_Wood_Break\" } }");
        assertNull(a.getPresentation().getShake());
    }

    // ==================== Native Parent inheritance (wholesale + sibling-leaf) ====================

    @Test
    void parentInheritance_childInheritsOmittedFields_ownFieldWins() throws Exception {
        String parentJson = "{ \"Identity\": { \"NameKey\": \"rpgstations.station.parent.name\","
                + " \"DescKey\": \"rpgstations.station.parent.desc\", \"Icon\": \"Parent_Icon\" },"
                + " \"Work\": { \"CycleMs\": 5000, \"MaxDurationMs\": 600000,"
                + "   \"Xp\": [ { \"Skill\": \"WOODCUTTING\", \"PerCycle\": 8.0 } ] },"
                + " \"Recipe\": { \"Conversions\": [ { \"Input\": { \"ResourceTypeId\": \"Wood_Hardwood_Trunk\", \"Quantity\": 1 },"
                + "   \"Output\": { \"ItemId\": \"Wood_Hardwood_Planks\", \"Quantity\": 2 } } ] },"
                + " \"Animation\": { \"EmoteId\": \"RPG_Emote_Saw\" } }";
        StationAsset parent = decodeWithParent(parentJson, null, "parent_station", null);
        assertEquals(5000L, parent.getWork().getCycleMs());

        String childJson = "{ \"Identity\": { \"Icon\": \"Child_Icon\" },"
                + " \"Work\": { \"CycleMs\": 4000 } }";
        StationAsset child = decodeWithParent(childJson, parent, "child_station", "parent_station");

        assertEquals("Child_Icon", child.getIdentity().getIcon());
        assertEquals(4000L, child.getWork().getCycleMs());
        assertEquals("rpgstations.station.parent.name", child.getIdentity().getNameKey());
        assertEquals("rpgstations.station.parent.desc", child.getIdentity().getDescKey());
        assertEquals(600000L, child.getWork().getMaxDurationMs());
        assertNotNull(child.getWork().getXp(), "Work.Xp survives a CycleMs-only override");
        assertEquals("WOODCUTTING", child.getWork().getXp()[0].getSkill());

        assertNotNull(child.getRecipe(), "Recipe inherits wholesale on omit");
        assertEquals("Wood_Hardwood_Trunk",
                child.getRecipe().getConversions()[0].getInput().getResourceTypeId());
        assertNotNull(child.getAnimation());
        assertEquals("RPG_Emote_Saw", child.getAnimation().getEmoteId());
    }

    @Test
    void fromCrafting_parentInheritsWholesaleOnOmit() throws Exception {
        String parentJson = "{ \"Recipe\": { \"FromCrafting\": { \"Categories\": [\"WoodPlanks\", \"StonePlanks\"],"
                + "   \"OutputPerInput\": 2 } } }";
        StationAsset parent = decodeWithParent(parentJson, null, "craft_parent", null);
        assertNull(parent.getRecipe().getConversions());
        assertEquals("WoodPlanks", parent.getRecipe().getFromCrafting().getCategories()[0]);

        StationAsset child = decodeWithParent("{}", parent, "craft_child", "craft_parent");
        assertNotNull(child.getRecipe());
        assertNotNull(child.getRecipe().getFromCrafting());
        assertEquals("WoodPlanks", child.getRecipe().getFromCrafting().getCategories()[0]);
        assertEquals(2, child.getRecipe().getFromCrafting().getOutputPerInput());
    }

    // ==================== Loot (leg 3, REPLACES the MMO's Luck group) ====================

    @Test
    void loot_decodesTablesAndInlineRolls() throws Exception {
        StationAsset a = decodeAsset("{ \"Loot\": { \"Tables\": [\"sawmillfinds\"],"
                + " \"Rolls\": [ { \"Trigger\": \"Cycle\","
                + "   \"Chance\": { \"BasePercent\": 2, \"AddFactors\": [ { \"Factor\": \"rpgstations:tool_power\" } ],"
                + "     \"CapPercent\": 25 }, \"Grants\": { \"BonusOutputCopies\": 1 } } ] } }");
        assertNotNull(a.getLoot());
        assertEquals("sawmillfinds", a.getLoot().getTables()[0]);
        assertEquals(1, a.getLoot().getRolls().length);
        Roll roll = a.getLoot().getRolls()[0];
        assertEquals("Cycle", roll.getTrigger());
        assertEquals(2.0, roll.getChance().getBasePercent());
        assertEquals("rpgstations:tool_power", roll.getChance().getAddFactors()[0].getFactor());
        assertEquals(25.0, roll.getChance().getCapPercent());
        assertEquals(1, roll.getGrants().getBonusOutputCopies());
        assertNull(roll.getLadder());
    }

    @Test
    void loot_ladderFloorRoutesThroughGrants_noDirectDropListLeaf() throws Exception {
        // M3 fix 2: a Ladder floor's ONLY reward path is its own Grants (no sibling DropList leaf).
        StationAsset a = decodeAsset("{ \"Loot\": { \"Rolls\": [ { \"Ladder\": {"
                + " \"Value\": { \"Factor\": \"rpgstations:cycle_count\" },"
                + " \"Floors\": [ { \"Min\": 10, \"Grants\": { \"DropList\": \"RPG_Station_Sawmill_T1\" } } ] } } ] } }");
        Roll.Ladder.Floor floor = a.getLoot().getRolls()[0].getLadder().getFloors()[0];
        assertEquals(10.0, floor.getMin());
        assertEquals("RPG_Station_Sawmill_T1", floor.getGrants().getDropList());
    }

    @Test
    void loot_omitted_decodesNull() throws Exception {
        StationAsset a = decodeAsset("{ \"Identity\": { \"NameKey\": \"rpgstations.station.x.name\" } }");
        assertNull(a.getLoot());
    }

    // ==================== Hold.Seat / Tool.XpScale / Work.Idle (unchanged, sibling-leaf inherit) ====================

    @Test
    void holdSeat_siblingLeafInherit() throws Exception {
        StationAsset parent = decodeWithParent("{ \"Hold\": { \"MovementLock\": true,"
                + " \"EffectId\": \"RPG_Station_Hold\", \"InterruptOnDamage\": true } }",
                null, "seat_parent", null);
        StationAsset child = decodeWithParent("{ \"Hold\": { \"Seat\": { \"Enabled\": true } } }",
                parent, "seat_child", "seat_parent");
        assertEquals(Boolean.TRUE, child.getHold().getSeat().getEnabled());
        assertEquals(Boolean.TRUE, child.getHold().getMovementLock(), "sibling leaf inherits");
        assertEquals("RPG_Station_Hold", child.getHold().getEffectId(), "sibling leaf inherits");
    }

    @Test
    void toolXpScale_siblingLeafInherit() throws Exception {
        StationAsset parent = decodeWithParent("{ \"Tool\": { \"XpScale\": {"
                + " \"ReferencePower\": 0.2, \"MinMult\": 0.75, \"MaxMult\": 1.5 } } }",
                null, "xpscale_parent", null);
        StationAsset child = decodeWithParent("{ \"Tool\": { \"XpScale\": { \"MaxMult\": 2.0 } } }",
                parent, "xpscale_child", "xpscale_parent");
        assertEquals(2.0, child.getTool().getXpScale().getMaxMult(), "own leaf wins");
        assertEquals(0.2, child.getTool().getXpScale().getReferencePower(), "sibling leaf inherits");
    }

    @Test
    void workIdle_siblingLeafInherit() throws Exception {
        StationAsset parent = decodeWithParent("{ \"Work\": { \"Idle\": {"
                + " \"Enabled\": true, \"CycleMs\": 15000, \"XpFraction\": 0.1 } } }",
                null, "idle_parent", null);
        StationAsset child = decodeWithParent("{ \"Work\": { \"Idle\": { \"XpFraction\": 0.2 } } }",
                parent, "idle_child", "idle_parent");
        assertEquals(0.2, child.getWork().getIdle().getXpFraction(), "own leaf wins");
        assertEquals(Boolean.TRUE, child.getWork().getIdle().getEnabled(), "sibling leaf inherits");
        assertEquals(15000L, child.getWork().getIdle().getCycleMs(), "sibling leaf inherits");
    }

    // ==================== Camera.Recipe (design 9.7, RENAMES FaceBlockMode this leg) ====================

    @Test
    void camera_recipeDecodes() throws Exception {
        StationAsset a = decodeAsset("{ \"Camera\": { \"Mode\": \"ThirdPerson\", \"Locked\": true,"
                + " \"FaceBlock\": true, \"Recipe\": \"look_rot\" } }");
        assertEquals("look_rot", a.getCamera().getRecipe());
    }

    // ==================== Actions (design 9.1, leg B) ====================

    @Test
    void actions_absent_resolvesEmpty() throws Exception {
        StationAsset a = decodeAsset("{ \"Identity\": { \"NameKey\": \"rpgstations.station.x.name\" } }");
        assertNull(a.getActions(), "no Actions map authored - the implicit single-action default");
    }

    @Test
    void actions_decodesPerActionWholeGroupOverrides() throws Exception {
        StationAsset a = decodeAsset("{ \"Work\": { \"CycleMs\": 5000 },"
                + " \"Actions\": { \"convert\": {"
                + "   \"Input\": { \"ResourceTypeId\": \"Metal_Ingot\" },"
                + "   \"Work\": { \"CycleMs\": 3800,"
                + "     \"Xp\": [ { \"Skill\": \"SMITHING\", \"PerCycle\": 6.0 } ] },"
                + "   \"Loot\": { \"Tables\": [\"anvil_sparks\"] } },"
                + " \"enhance\": {"
                + "   \"Input\": { \"Function\": \"Weapon\" },"
                + "   \"Work\": { \"Repeat\": false },"
                + "   \"Steps\": [ { \"Id\": \"strike1\", \"Type\": \"Wait\", \"Wait\": { \"Beats\": 1 } } ] } } }");
        assertNotNull(a.getActions());
        assertEquals(2, a.getActions().size());
        java.util.List<String> orderedIds = new java.util.ArrayList<>(a.getActions().keySet());
        assertEquals(java.util.List.of("convert", "enhance"), orderedIds, "LinkedHashMap preserves authoring order");

        ActionDef convert = a.getActions().get("convert");
        assertEquals("Metal_Ingot", convert.getInput().getResourceTypeId());
        assertEquals(3800L, convert.getWork().getCycleMs());
        assertEquals("SMITHING", convert.getWork().getXp()[0].getSkill());
        assertEquals("anvil_sparks", convert.getLoot().getTables()[0]);
        assertNull(convert.getSteps(), "convert authors no Steps - the implicit program builds from Work/Recipe/Loot");

        ActionDef enhance = a.getActions().get("enhance");
        assertEquals("Weapon", enhance.getInput().getFunction());
        assertEquals(Boolean.FALSE, enhance.getWork().getRepeat());
        assertEquals(1, enhance.getSteps().length);
        assertEquals("strike1", enhance.getSteps()[0].getId());
        assertEquals(StationStep.TYPE_WAIT, enhance.getSteps()[0].getType());
        assertEquals(1, enhance.getSteps()[0].getWait().getBeats());
    }

    @Test
    void actions_parentInheritsWholesaleOnOmit_ownReplacesWholesaleOnAuthor() throws Exception {
        String parentJson = "{ \"Actions\": { \"convert\": { \"Work\": { \"CycleMs\": 3800 } } } }";
        StationAsset parent = decodeWithParent(parentJson, null, "actions_parent", null);
        assertEquals(1, parent.getActions().size());

        StationAsset childOmitting = decodeWithParent("{}", parent, "actions_child_omit", "actions_parent");
        assertNotNull(childOmitting.getActions(), "Actions inherits WHOLESALE on omit, same as Flairs");
        assertEquals(3800L, childOmitting.getActions().get("convert").getWork().getCycleMs());

        String childJson = "{ \"Actions\": { \"enhance\": { \"Work\": { \"CycleMs\": 9000 } } } }";
        StationAsset childAuthoring = decodeWithParent(childJson, parent, "actions_child_own", "actions_parent");
        assertEquals(1, childAuthoring.getActions().size(),
                "authoring Actions REPLACES the parent's whole map, no per-key merge");
        assertNull(childAuthoring.getActions().get("convert"), "the parent's 'convert' entry does NOT survive");
        assertEquals(9000L, childAuthoring.getActions().get("enhance").getWork().getCycleMs());
    }

    // ==================== ActionInput (design 9.1) ====================

    @Test
    void actionInput_decodesEveryRoute() throws Exception {
        ActionInput i = decodeActionInputFixture();
        assertEquals("Wood_Oak_Log", i.getItemId());
        assertEquals("Metal_Ingot", i.getResourceTypeId());
        assertEquals("Weapon", i.getFunction());
        assertEquals("Hammer", i.getTags().get("Family")[0]);
        assertFalse(i.isCatchAll());
    }

    @Test
    void actionInput_allBlank_isCatchAll() {
        assertTrue(ActionInput.of(null, null, null, null).isCatchAll());
    }

    private static ActionInput decodeActionInputFixture() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": { \"any\": { \"Input\": {"
                + " \"ItemId\": \"Wood_Oak_Log\", \"ResourceTypeId\": \"Metal_Ingot\","
                + " \"Function\": \"Weapon\", \"Tags\": { \"Family\": [\"Hammer\"] } } } } }");
        return a.getActions().get("any").getInput();
    }

    // ==================== StationStep (design 9.3) ====================

    @Test
    void stationStep_decodesConsumeProduceWaitRollCommandGroups() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": { \"ritual\": { \"Steps\": ["
                + " { \"Id\": \"c\", \"Type\": \"Consume\","
                + "   \"Consume\": { \"ItemId\": \"MMO_Sharpened_Bar\", \"Quantity\": 2, \"From\": \"Custody\" } },"
                + " { \"Id\": \"p\", \"Type\": \"Produce\","
                + "   \"Produce\": { \"ItemId\": \"MMO_Enhanced_Sword\", \"Quantity\": 1 } },"
                + " { \"Id\": \"w\", \"Type\": \"Wait\", \"Wait\": { \"DurationMs\": 1200 },"
                + "   \"OnConditionFail\": { \"Result\": \"Skip\", \"Goto\": \"p\" },"
                + "   \"Conditions\": [ { \"Factor\": \"mmoskilltree:skill_level\", \"Min\": 10 } ] },"
                + " { \"Id\": \"r\", \"Type\": \"Roll\","
                + "   \"Roll\": { \"Lootable\": \"anvil_sparks\", \"Rolls\": [ { \"Trigger\": \"Cycle\" } ] } },"
                + " { \"Id\": \"cmd\", \"Type\": \"Command\","
                + "   \"Command\": { \"Commands\": [\"give {player} test 1\"] } } ] } } }");
        StationStep[] steps = a.getActions().get("ritual").getSteps();
        assertEquals(5, steps.length);

        assertEquals(StationStep.TYPE_CONSUME, steps[0].getType());
        assertEquals("MMO_Sharpened_Bar", steps[0].getConsume().getItemId());
        assertEquals(2, steps[0].getConsume().getQuantity());
        assertEquals("Custody", steps[0].getConsume().getFrom());
        assertEquals(StationStep.Consume.FROM_CUSTODY, steps[0].getConsume().effectiveFrom());

        assertEquals(StationStep.TYPE_PRODUCE, steps[1].getType());
        assertEquals("MMO_Enhanced_Sword", steps[1].getProduce().getItemId());
        assertEquals(StationStep.Produce.TO_INVENTORY, steps[1].getProduce().effectiveTo(), "To defaults to Inventory");

        assertEquals(StationStep.TYPE_WAIT, steps[2].getType());
        assertEquals(1200L, steps[2].getWait().getDurationMs());
        assertEquals(StationStep.OnConditionFail.RESULT_SKIP, steps[2].getOnConditionFail().effectiveResult());
        assertEquals("p", steps[2].getOnConditionFail().getGoto());
        assertEquals(1, steps[2].getConditions().length);

        assertEquals(StationStep.TYPE_ROLL, steps[3].getType());
        assertEquals("anvil_sparks", steps[3].getRoll().getLootable());
        assertEquals(1, steps[3].getRoll().getRolls().length);

        assertEquals(StationStep.TYPE_COMMAND, steps[4].getType());
        assertEquals("give {player} test 1", steps[4].getCommand().getCommands()[0]);
    }

    @Test
    void stationStep_reservedTypes_decodeAndFlagUnimplemented() throws Exception {
        StationAsset a = decodeAsset("{ \"Actions\": { \"anvil\": { \"Steps\": ["
                + " { \"Id\": \"stamp\", \"Type\": \"Stamp\" },"
                + " { \"Id\": \"mount\", \"Type\": \"Mount\" } ] } } }");
        StationStep[] steps = a.getActions().get("anvil").getSteps();
        assertTrue(steps[0].isReservedUnimplemented());
        assertTrue(steps[1].isReservedUnimplemented());
    }

    @Test
    void stationStep_onConditionFail_defaultsToFail() {
        StationStep.OnConditionFail omitted = StationStep.OnConditionFail.of(null, null);
        assertEquals(StationStep.OnConditionFail.RESULT_FAIL, omitted.effectiveResult());
    }
}
