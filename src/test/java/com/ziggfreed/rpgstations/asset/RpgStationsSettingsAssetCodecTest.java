package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/** Codec layer for {@link RpgStationsSettingsAsset} (design section 4.6): decode + native {@code Parent} sibling-leaf inherit. */
public class RpgStationsSettingsAssetCodecTest {

    private static RpgStationsSettingsAsset decodeAsset(String body) throws Exception {
        return RpgStationsSettingsAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body), new ExtraInfo());
    }

    private static RpgStationsSettingsAsset decodeWithParent(String body, RpgStationsSettingsAsset parent, String key, String parentKey)
            throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, key, parentKey);
        return RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), parent, new AssetExtraInfo<>(data));
    }

    @Test
    void decodesEnabledAndSummaryHud() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Enabled\": true, \"SummaryHud\": "
                + "{ \"Enabled\": true, \"Position\": \"top_center\", \"OffsetY\": 72, \"TtlMs\": 6000 } }");
        assertTrue(a.isEnabled());
        assertNotNull(a.getSummaryHud());
        assertEquals("top_center", a.getSummaryHud().getPosition());
        assertEquals(72, a.getSummaryHud().getOffsetY());
        assertEquals(6000L, a.getSummaryHud().getTtlMs());
    }

    @Test
    void enabled_readerDefaultsTrueWhenOmitted() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{}");
        assertTrue(a.isEnabled());
    }

    @Test
    void defaults_areEnabledWithASummaryHud() {
        RpgStationsSettingsAsset a = RpgStationsSettingsAsset.defaults();
        assertTrue(a.isEnabled());
        assertNotNull(a.getSummaryHud());
        assertTrue(a.getSummaryHud().isEnabled());
    }

    @Test
    void parentInheritance_siblingLeafInherit_ownWins() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, "Settings", null);
        RpgStationsSettingsAsset parent = RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Enabled\": true, \"SummaryHud\": "
                        + "{ \"Enabled\": true, \"Position\": \"top_center\", \"TtlMs\": 6000 } }"),
                null, new AssetExtraInfo<>(data));

        RpgStationsSettingsAsset child = decodeWithParent("{ \"SummaryHud\": { \"OffsetY\": 200 } }",
                parent, "settings_child", "settings");
        assertEquals(200, child.getSummaryHud().getOffsetY(), "own leaf wins");
        assertEquals("top_center", child.getSummaryHud().getPosition(), "sibling leaf inherits");
        assertEquals(6000L, child.getSummaryHud().getTtlMs(), "sibling leaf inherits");
    }
}
