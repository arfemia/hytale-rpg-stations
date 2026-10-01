package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * The shared input matcher's {@code Quality} leaf at the codec: an open list of quality ids that
 * decodes at every site the matcher is authored (an action's {@code Select}, a {@code Custody.Input}
 * and its {@code Except}, a socket's {@code Match}, a fallback's {@code Input}, a protect-list
 * entry), counts as a route, and inherits per leaf through native {@code Parent} like its siblings.
 * Every fixture is authored here.
 */
class ActionInputQualityCodecTest {

    private static StationAsset station(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static ActionAsset action(String id, String parentId, String body, ActionAsset parent) throws Exception {
        return ActionAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(body), parent,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ActionAsset.class, id, parentId)));
    }

    @Test
    void quality_decodesAtEveryMatcherSite_asAnOpenIdList() throws Exception {
        ActionDef a = station("{ \"Actions\": [ { \"Id\": \"Unmake\","
                + " \"Select\": { \"Quality\": [\"Fixture_Developer\", \"Fixture_Pack_Tier\"] },"
                + " \"Custody\": { \"Input\": { \"Except\": { \"Quality\": [\"Fixture_Developer\"] } },"
                + "   \"Sockets\": { \"slot_a\": { \"Item\": { \"Match\": { \"Quality\": [\"Fixture_Rare\"] } },"
                + "   \"MaxQuantity\": 1 } } },"
                + " \"Recipe\": { \"Fallback\": { \"Input\": { \"Tags\": { \"Type\": [\"Weapon\"] },"
                + "   \"Quality\": [\"Fixture_Epic\"] }, \"EssenceOnly\": {} } } } ] }").getActions()[0];
        assertArrayEquals(new String[] {"Fixture_Developer", "Fixture_Pack_Tier"}, a.getSelect().getQuality(),
                "an open list: any id, a pack's own tier included");
        assertFalse(a.getSelect().isCatchAll());
        assertArrayEquals(new String[] {"Fixture_Developer"}, a.getCustody().getInput().getExcepts()[0].getQuality());
        assertArrayEquals(new String[] {"Fixture_Rare"}, a.getCustody().effectiveSockets().get(0).match().getQuality());
        ActionInput filter = a.getRecipe().getFallback().getInput();
        assertArrayEquals(new String[] {"Fixture_Epic"}, filter.getQuality());
        assertEquals("Weapon", filter.getTags().get("Type")[0], "Quality sits beside the other routes");
    }

    @Test
    void quality_decodesOnAProtectListEntry() throws Exception {
        ProtectListAsset list = ProtectListAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(
                "{ \"Protects\": [ { \"Quality\": [\"Fixture_Developer\"] } ] }"), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ProtectListAsset.class, "Fixture_List", null)));
        assertArrayEquals(new String[] {"Fixture_Developer"}, list.getProtects()[0].getQuality());
        assertTrue(list.getProtects()[0].hasQualityRoute());
    }

    @Test
    void absent_isNoRoute() throws Exception {
        ActionDef a = station("{ \"Actions\": [ { \"Id\": \"Mill\", \"Select\": { \"ItemId\": \"Fixture_Log\" } } ] }")
                .getActions()[0];
        assertNull(a.getSelect().getQuality());
        assertFalse(a.getSelect().hasQualityRoute());
    }

    @Test
    void parentInheritance_qualityInheritsPerLeaf_andTheChildsOwnWins() throws Exception {
        ActionAsset parent = action("fixture_base", null,
                "{ \"Select\": { \"Function\": \"Weapon\", \"Quality\": [\"Fixture_Developer\"] } }", null);
        ActionAsset sibling = action("fixture_sibling", "fixture_base",
                "{ \"Select\": { \"Function\": \"Tool\" } }", parent);
        assertEquals("Tool", sibling.getBody().getSelect().getFunction(), "the child's own leaf wins");
        assertArrayEquals(new String[] {"Fixture_Developer"}, sibling.getBody().getSelect().getQuality(),
                "the sibling Quality leaf inherits beside an overridden Function");

        ActionAsset own = action("fixture_own", "fixture_base",
                "{ \"Select\": { \"Quality\": [\"Fixture_Debug\"] } }", parent);
        assertArrayEquals(new String[] {"Fixture_Debug"}, own.getBody().getSelect().getQuality());
        assertEquals("Weapon", own.getBody().getSelect().getFunction());
    }
}
