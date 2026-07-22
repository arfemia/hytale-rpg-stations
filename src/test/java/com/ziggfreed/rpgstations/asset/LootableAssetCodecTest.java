package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * Codec layer for {@link LootableAsset}: id lowercase canonicalization + native {@code Parent}
 * inheritance (wholesale inherit-on-omit of {@code Rolls}) - design section 4.5.1.
 */
public class LootableAssetCodecTest {

    private static LootableAsset decodeAsset(String body) throws Exception {
        return LootableAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body), new ExtraInfo());
    }

    private static LootableAsset decodeWithParent(String body, LootableAsset parent, String key, String parentKey)
            throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(LootableAsset.class, key, parentKey);
        return LootableAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), parent, new AssetExtraInfo<>(data));
    }

    @Test
    void id_isLowercasedAtDecode() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(LootableAsset.class, "SawmillFinds", null);
        LootableAsset a = LootableAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{}"), null, new AssetExtraInfo<>(data));
        assertEquals("sawmillfinds", a.getId());
    }

    @Test
    void decodesRolls() throws Exception {
        LootableAsset a = decodeAsset("{ \"Rolls\": [ { \"Trigger\": \"Cycle\","
                + " \"Chance\": { \"BasePercent\": 2, \"AddFactors\": [ { \"Factor\": \"rpgstations:tool_power\" } ] },"
                + " \"Grants\": { \"BonusOutputCopies\": 1 } } ] }");
        assertNotNull(a.getRolls());
        assertEquals(1, a.getRolls().length);
        assertEquals("Cycle", a.getRolls()[0].getTrigger());
        assertEquals(1, a.getRolls()[0].getGrants().getBonusOutputCopies());
    }

    @Test
    void rollsOmitted_decodesNull() throws Exception {
        LootableAsset a = decodeAsset("{}");
        assertNull(a.getRolls());
    }

    @Test
    void parentInheritance_wholesaleOnOmit_ownWins() throws Exception {
        String parentJson = "{ \"Rolls\": [ { \"Grants\": { \"DropList\": \"RPG_Station_Sawmill_T1\" } } ] }";
        LootableAsset parent = decodeWithParent(parentJson, null, "finds_parent", null);
        assertEquals(1, parent.getRolls().length);

        LootableAsset child = decodeWithParent("{}", parent, "finds_child", "finds_parent");
        assertNotNull(child.getRolls(), "Rolls inherits wholesale on omit");
        assertEquals("RPG_Station_Sawmill_T1", child.getRolls()[0].getGrants().getDropList());

        LootableAsset ownChild = decodeWithParent(
                "{ \"Rolls\": [ { \"Grants\": { \"DropList\": \"RPG_Station_Sawmill_T2\" } } ] }",
                parent, "finds_own", "finds_parent");
        assertEquals("RPG_Station_Sawmill_T2", ownChild.getRolls()[0].getGrants().getDropList());
    }
}
