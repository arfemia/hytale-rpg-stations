package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
 * Codec layer for {@link FlairAsset} (design section 9.6, phase 2 leg F): id lowercase
 * canonicalization, the open {@code Moments} map (any string key, no hardcoded vocabulary), the
 * nullable {@code Stations} restriction, and native {@code Parent} inheritance - mirrors
 * {@link LootableAssetCodecTest}'s exact shape.
 */
public class FlairAssetCodecTest {

    private static FlairAsset decodeAsset(String body) throws Exception {
        return FlairAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body), new ExtraInfo());
    }

    private static FlairAsset decodeWithParent(String body, FlairAsset parent, String key, String parentKey)
            throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(FlairAsset.class, key, parentKey);
        return FlairAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), parent, new AssetExtraInfo<>(data));
    }

    @Test
    void id_isLowercasedAtDecode() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(FlairAsset.class, "GoldenSaw", null);
        FlairAsset a = FlairAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{}"), null, new AssetExtraInfo<>(data));
        assertEquals("goldensaw", a.getId());
    }

    @Test
    void decodesStationsAndMoments() throws Exception {
        FlairAsset a = decodeAsset("{ \"Stations\": [\"sawmill\"], \"Moments\": { \"swing\": "
                + "{ \"Particles\": \"Petal_Burst\" }, \"step:enhance:stamp\": { \"Sound\": \"SFX_Choir_Hit\" } } }");
        assertArrayEquals(new String[]{"sawmill"}, a.getStations());
        assertNotNull(a.getMoments());
        assertEquals(2, a.getMoments().size());
        assertEquals("Petal_Burst", a.getMoments().get("swing").getParticles());
        assertEquals("SFX_Choir_Hit", a.getMoments().get("step:enhance:stamp").getSound());
    }

    @Test
    void stationsOmitted_decodesNull_appliesToEverything() throws Exception {
        FlairAsset a = decodeAsset("{ \"Moments\": { \"cycle\": { \"Sound\": \"SFX_A\" } } }");
        assertNull(a.getStations());
        assertTrue(a.appliesTo("sawmill"));
        assertTrue(a.appliesTo("anything_at_all"));
    }

    @Test
    void momentsOmitted_decodesNull() throws Exception {
        FlairAsset a = decodeAsset("{}");
        assertNull(a.getMoments());
    }

    @Test
    void appliesTo_isCaseInsensitive_andRestrictsToTheListedStations() throws Exception {
        FlairAsset a = decodeAsset("{ \"Stations\": [\"Sawmill\", \"anvil\"] }");
        assertTrue(a.appliesTo("sawmill"));
        assertTrue(a.appliesTo("SAWMILL"));
        assertTrue(a.appliesTo("anvil"));
        assertFalse(a.appliesTo("lumbermill"));
    }

    @Test
    void parentInheritance_wholesaleOnOmit_ownWins() throws Exception {
        String parentJson = "{ \"Stations\": [\"sawmill\"], \"Moments\": { \"cycle\": { \"Sound\": \"SFX_Parent\" } } }";
        FlairAsset parent = decodeWithParent(parentJson, null, "flair_parent", null);
        assertEquals(1, parent.getStations().length);

        FlairAsset child = decodeWithParent("{}", parent, "flair_child", "flair_parent");
        assertNotNull(child.getMoments(), "Moments inherits wholesale on omit");
        assertEquals("SFX_Parent", child.getMoments().get("cycle").getSound());
        assertArrayEquals(new String[]{"sawmill"}, child.getStations());

        FlairAsset ownChild = decodeWithParent(
                "{ \"Moments\": { \"cycle\": { \"Sound\": \"SFX_Own\" } } }", parent, "flair_own", "flair_parent");
        assertEquals("SFX_Own", ownChild.getMoments().get("cycle").getSound());
    }
}
