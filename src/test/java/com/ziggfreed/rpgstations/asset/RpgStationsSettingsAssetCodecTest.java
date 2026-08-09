package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.ui.hud.HudPosition;

/** Codec layer for {@link RpgStationsSettingsAsset} (design section 4.6): decode + native {@code Parent} sibling-leaf inherit. */
public class RpgStationsSettingsAssetCodecTest {

    private static RpgStationsSettingsAsset decodeAsset(String body) throws Exception {
        return RpgStationsSettingsAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, "fixture", null)));
    }

    private static RpgStationsSettingsAsset decodeWithParent(String body, RpgStationsSettingsAsset parent, String key, String parentKey)
            throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, key, parentKey);
        return RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), parent, new AssetExtraInfo<>(data));
    }

    @Test
    void decodesEnabledAndSummaryHud() throws Exception {
        // Legacy SCREAMING_SNAKE spelling: a decode passthrough (the codec stores whatever string is
        // authored), kept here to prove an existing owner file with the old spelling still decodes.
        RpgStationsSettingsAsset a = decodeAsset("{ \"Enabled\": true, \"SummaryHud\": "
                + "{ \"Enabled\": true, \"Position\": \"top_center\", \"OffsetY\": 72, \"TtlMs\": 6000 } }");
        assertTrue(a.isEnabled());
        assertNotNull(a.getSummaryHud());
        assertEquals("top_center", a.getSummaryHud().getPosition());
        assertEquals(72, a.getSummaryHud().getOffsetY());
        assertEquals(6000L, a.getSummaryHud().getTtlMs());
    }

    @Test
    void decodesEnabledAndSummaryHud_pascalCasePosition() throws Exception {
        // Preferred PascalCase spelling, matching every other id in this schema.
        RpgStationsSettingsAsset a = decodeAsset("{ \"Enabled\": true, \"SummaryHud\": "
                + "{ \"Enabled\": true, \"Position\": \"TopCenter\", \"OffsetY\": 72, \"TtlMs\": 6000 } }");
        assertTrue(a.isEnabled());
        assertNotNull(a.getSummaryHud());
        assertEquals("TopCenter", a.getSummaryHud().getPosition());
        assertEquals(72, a.getSummaryHud().getOffsetY());
        assertEquals(6000L, a.getSummaryHud().getTtlMs());

        // Both spellings resolve to the same HudPosition through the shared-library parser
        // (HudPosition has no equals(); toString() reflects every field, so it is a valid comparator here).
        assertEquals(HudPosition.parse("top_center", 0, 72).toString(), HudPosition.parse("TopCenter", 0, 72).toString(),
                "legacy and PascalCase spellings resolve identically");
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

    // ==================== Limits (the per-world owner ceilings) ====================

    @Test
    void limits_decodeEveryLeaf() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Limits\": { \"MaxSessionsPerWorld\": 7, "
                + "\"MaxPuppetsPerWorld\": 3, \"MaxCustodyClaimsPerWorld\": 11 } }");
        assertNotNull(a.getLimits());
        assertEquals(7, a.getLimits().getMaxSessionsPerWorld());
        assertEquals(3, a.getLimits().getMaxPuppetsPerWorld());
        assertEquals(11, a.getLimits().getMaxCustodyClaimsPerWorld());
    }

    @Test
    void limits_areAbsentByDefault() throws Exception {
        assertNull(decodeAsset("{}").getLimits(), "an unauthored Limits group stays null");
        assertNull(RpgStationsSettingsAsset.defaults().getLimits(), "the built-in default sets no ceiling");
    }

    @Test
    void limits_oneLeafIsIndependentOfTheOthers() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Limits\": { \"MaxPuppetsPerWorld\": 3 } }");
        assertNotNull(a.getLimits());
        assertEquals(3, a.getLimits().getMaxPuppetsPerWorld());
        assertNull(a.getLimits().getMaxSessionsPerWorld(), "an unset sibling stays unlimited");
        assertNull(a.getLimits().getMaxCustodyClaimsPerWorld(), "an unset sibling stays unlimited");
    }

    @Test
    void limits_parentInheritance_siblingLeafInherit_ownWins() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, "Settings", null);
        RpgStationsSettingsAsset parent = RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Limits\": { \"MaxSessionsPerWorld\": 7, \"MaxPuppetsPerWorld\": 3 } }"),
                null, new AssetExtraInfo<>(data));

        RpgStationsSettingsAsset child = decodeWithParent("{ \"Limits\": { \"MaxPuppetsPerWorld\": 9 } }",
                parent, "settings_child", "settings");
        assertEquals(9, child.getLimits().getMaxPuppetsPerWorld(), "own leaf wins");
        assertEquals(7, child.getLimits().getMaxSessionsPerWorld(), "sibling leaf inherits");
    }

    @Test
    void atCapacity_nullMaxIsUnlimited() {
        assertFalse(RpgStationsSettingsAsset.Limits.atCapacity(null, () -> 1_000));
    }

    @Test
    void atCapacity_nullMaxNeverCounts() {
        // The count is a supplier precisely so an unset ceiling costs nothing: a server that never
        // authors Limits must not pay a per-world scan on every press.
        int[] calls = {0};
        RpgStationsSettingsAsset.Limits.atCapacity(null, () -> {
            calls[0]++;
            return 0;
        });
        assertEquals(0, calls[0], "an unset ceiling never asks for the count");
    }

    @Test
    void atCapacity_nonPositiveMaxIsUnlimited() {
        // A ceiling of zero or less would read as "turn the feature off", which is what the engine's
        // own Enabled switch is for - so it is treated as unset rather than as a hard block.
        assertFalse(RpgStationsSettingsAsset.Limits.atCapacity(0, () -> 5));
        assertFalse(RpgStationsSettingsAsset.Limits.atCapacity(-2, () -> 5));
    }

    @Test
    void atCapacity_deniesAtAndAboveTheCeiling() {
        assertFalse(RpgStationsSettingsAsset.Limits.atCapacity(3, () -> 2), "below the ceiling passes");
        assertTrue(RpgStationsSettingsAsset.Limits.atCapacity(3, () -> 3), "at the ceiling denies");
        assertTrue(RpgStationsSettingsAsset.Limits.atCapacity(3, () -> 4), "above the ceiling denies");
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
