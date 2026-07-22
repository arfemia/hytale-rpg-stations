package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
 * replacing {@code Feedback}), and that {@code Luck} decodes unchanged (its schema does not
 * change until leg 3's loot engine). Ported (trimmed to the representative cases; the codec
 * class itself is a mechanical, vetted copy - see {@link StationAsset}'s javadoc) from the
 * MMO's {@code StationAssetCodecTest}.
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

    // ==================== Luck (UNCHANGED schema this leg) ====================

    @Test
    void luck_decodesTiersUnchanged() throws Exception {
        StationAsset a = decodeAsset("{ \"Luck\": { \"Tiers\": ["
                + " { \"MinLuck\": 50, \"DropList\": \"RPG_Station_Sawmill_T1\" },"
                + " { \"MinLuck\": 100, \"DropList\": \"RPG_Station_Sawmill_T2\","
                + "   \"Presentation\": { \"Sound\": \"SFX_Coins_Land\" } } ] } }");
        assertNotNull(a.getLuck());
        assertEquals(2, a.getLuck().getTiers().length);
        assertEquals(50.0, a.getLuck().getTiers()[0].getMinLuck());
        assertEquals("RPG_Station_Sawmill_T2", a.getLuck().getTiers()[1].getDropList());
        assertEquals("SFX_Coins_Land", a.getLuck().getTiers()[1].getPresentation().getSound());
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
}
