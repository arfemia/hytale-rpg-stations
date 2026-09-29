package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.BooleanSchema;
import com.hypixel.hytale.codec.schema.config.ObjectSchema;
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

    // ==================== SummaryHud.Color (this card's own tint) ====================

    @Test
    void summaryHud_colorDecodesTrimmedAndIsAbsentByDefault() throws Exception {
        assertEquals("#ffffffb8", decodeAsset("{ \"SummaryHud\": { \"Color\": \" #ffffffb8 \" } }").getSummaryHud().getColor(),
                "as authored, trimmed; the panel validates it against the shared library's hex reader");
        assertNull(decodeAsset("{ \"SummaryHud\": { \"Enabled\": true } }").getSummaryHud().getColor(),
                "absent: the look every HUD card shares");
        assertNull(decodeAsset("{ \"SummaryHud\": { \"Color\": \"   \" } }").getSummaryHud().getColor(), "a blank is absent");
        assertNull(RpgStationsSettingsAsset.defaults().getSummaryHud().getColor(), "the built-in default states none");
    }

    @Test
    void summaryHud_color_siblingLeafInheritsUnderParent_ownWins() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, "Settings", null);
        RpgStationsSettingsAsset parent = RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"SummaryHud\": { \"Color\": \"#112233\", \"TtlMs\": 4000 } }"),
                null, new AssetExtraInfo<>(data));

        RpgStationsSettingsAsset child = decodeWithParent("{ \"SummaryHud\": { \"TtlMs\": 9000 } }",
                parent, "settings_child", "settings");
        assertEquals("#112233", child.getSummaryHud().getColor(), "sibling leaf inherits");
        assertEquals(9000L, child.getSummaryHud().getTtlMs(), "own leaf wins");

        RpgStationsSettingsAsset restated = decodeWithParent("{ \"SummaryHud\": { \"Color\": \"#445566\" } }",
                parent, "settings_child", "settings");
        assertEquals("#445566", restated.getSummaryHud().getColor(), "own leaf wins");
        assertEquals(4000L, restated.getSummaryHud().getTtlMs(), "sibling leaf inherits");
    }

    // ==================== Limits (the owner ceilings) ====================

    @Test
    void limits_decodeEveryLeaf() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Limits\": { \"MaxSessionsPerWorld\": 7, "
                + "\"MaxPuppetsPerWorld\": 3, \"MaxStashesPerSection\": 11 } }");
        assertNotNull(a.getLimits());
        assertEquals(7, a.getLimits().getMaxSessionsPerWorld());
        assertEquals(3, a.getLimits().getMaxPuppetsPerWorld());
        assertEquals(11, a.getLimits().getMaxStashesPerSection());
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
        assertNull(a.getLimits().getMaxStashesPerSection(), "an unset sibling stays unlimited");
    }

    @Test
    void limits_unattendedIntervalMs_decodesAndReaderDefaults() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Limits\": { \"UnattendedIntervalMs\": 2500 } }");
        assertEquals(2500L, a.getLimits().getUnattendedIntervalMs());
        assertEquals(2500L, a.getLimits().effectiveUnattendedIntervalMs());

        RpgStationsSettingsAsset bare = decodeAsset("{ \"Limits\": { \"MaxPuppetsPerWorld\": 3 } }");
        assertNull(bare.getLimits().getUnattendedIntervalMs());
        assertEquals(1000L, bare.getLimits().effectiveUnattendedIntervalMs(),
                "the unattended pass paces at 1000ms unless the owner says otherwise");
    }

    @Test
    void limits_maxUnattendedGatherCycles_decodesAndClampsAsMinOfCaps() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Limits\": { \"MaxUnattendedGatherCycles\": 5 } }");
        assertNotNull(a.getLimits());
        assertEquals(5, a.getLimits().getMaxUnattendedGatherCycles());
        assertEquals(5, a.getLimits().clampGatherCycles(24), "the owner ceiling clamps the action knob");
        assertEquals(3, a.getLimits().clampGatherCycles(3),
                "min of caps: an action already tighter than the owner ceiling keeps its own knob");
        assertNull(a.getLimits().getMaxSessionsPerWorld(), "an unset sibling stays unlimited");
    }

    @Test
    void limits_maxUnattendedGatherCycles_nullOrNonPositiveLeavesTheActionKnobAlone() throws Exception {
        RpgStationsSettingsAsset bare = decodeAsset("{ \"Limits\": { \"MaxPuppetsPerWorld\": 3 } }");
        assertNull(bare.getLimits().getMaxUnattendedGatherCycles());
        assertEquals(24, bare.getLimits().clampGatherCycles(24), "null means no owner ceiling");
        assertEquals(24, RpgStationsSettingsAsset.Limits.of(null, null, null, null, 0).clampGatherCycles(24),
                "a non-positive owner value reads as unset, the one spelling every limit shares");
    }

    @Test
    void limits_retiredMaxCustodyClaimsPerWorld_decodesWithoutFailingAndFillsNothingLive() throws Exception {
        // The retired leaf is warn-only, never a parse failure: an owner file still authoring it
        // decodes cleanly, its value lands ONLY in the retired slot the settings fold warns on,
        // and the live stash ceiling stays untouched.
        RpgStationsSettingsAsset a = decodeAsset("{ \"Limits\": { \"MaxCustodyClaimsPerWorld\": 400 } }");
        assertNotNull(a.getLimits());
        assertEquals(400, a.getLimits().getRetiredMaxCustodyClaimsPerWorld());
        assertNull(a.getLimits().getMaxStashesPerSection(), "the retired leaf never fills the live one");
        assertNull(a.getLimits().getMaxSessionsPerWorld());
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

    // ==================== Moments (the engine-wide default cue layer) ====================

    @Test
    void moments_decodeAsAMomentIdToPresentationMap() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Moments\": { \"Refused\": { \"Sounds\": [\"Fixture_Thunk\"] },"
                + " \"Refused:No_Materials\": { \"Particles\": [ { \"SystemId\": \"Fixture_Puff\" } ] } } }");
        assertNotNull(a.getMoments());
        assertEquals(2, a.getMoments().size());
        assertEquals("Fixture_Thunk", a.getMoments().get("Refused").getSounds()[0].getEventId());
        assertEquals("Fixture_Puff", a.getMoments().get("Refused:No_Materials").getParticles()[0].getSystemId());
    }

    @Test
    void moments_areAbsentByDefault() throws Exception {
        assertNull(decodeAsset("{}").getMoments(), "an unauthored Moments map stays null");
        assertNull(RpgStationsSettingsAsset.defaults().getMoments(), "the built-in default dresses no moment");
    }

    @Test
    void moments_anAuthoredEmptySoundsArrayDecodesAsAnEmptyLeaf_notAnOmission() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Moments\": { \"Refused\": { \"Sounds\": [] } } }");
        assertNotNull(a.getMoments().get("Refused").getSounds(), "the leaf is authored");
        assertEquals(0, a.getMoments().get("Refused").getSounds().length, "and it is empty: silence");
    }

    @Test
    void moments_parentInheritance_mergesPerMomentId() throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RpgStationsSettingsAsset.class, "Settings", null);
        RpgStationsSettingsAsset parent = RpgStationsSettingsAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Moments\": { \"Refused\": { \"Sounds\": [\"Fixture_Thunk\"] },"
                        + " \"Cycle\": { \"Sounds\": [\"Fixture_Cycle\"] } } }"),
                null, new AssetExtraInfo<>(data));

        RpgStationsSettingsAsset child = decodeWithParent(
                "{ \"Moments\": { \"Refused\": { \"Sounds\": [\"Fixture_Child_Thunk\"] } } }",
                parent, "settings_child", "settings");
        assertEquals("Fixture_Child_Thunk", child.getMoments().get("Refused").getSounds()[0].getEventId(),
                "own entry wins");
        assertNotNull(child.getMoments().get("Cycle"), "an entry the child omits inherits");
    }

    // ==================== Refusals (the repeat window) ====================

    @Test
    void refusals_repeatWindowMs_decodesAndReaderDefaults() throws Exception {
        RpgStationsSettingsAsset a = decodeAsset("{ \"Refusals\": { \"RepeatWindowMs\": 900 } }");
        assertNotNull(a.getRefusals());
        assertEquals(900L, a.getRefusals().getRepeatWindowMs());
        assertEquals(900L, a.effectiveRefusalRepeatWindowMs());

        RpgStationsSettingsAsset bare = decodeAsset("{}");
        assertNull(bare.getRefusals(), "an unauthored Refusals group stays null");
        assertEquals(RpgStationsSettingsAsset.Refusals.DEFAULT_REPEAT_WINDOW_MS, bare.effectiveRefusalRepeatWindowMs(),
                "the window has a reader default whether or not the group is authored");
        assertEquals(RpgStationsSettingsAsset.Refusals.DEFAULT_REPEAT_WINDOW_MS,
                RpgStationsSettingsAsset.Refusals.of(null).effectiveRepeatWindowMs());
    }

    @Test
    void refusals_zeroDisablesTheThrottle_andANegativeValueReadsAsZero() {
        assertEquals(0L, RpgStationsSettingsAsset.Refusals.of(0L).effectiveRepeatWindowMs());
        assertEquals(0L, RpgStationsSettingsAsset.Refusals.of(-40L).effectiveRepeatWindowMs(),
                "a negative window is a codec warning and never a window that admits nothing");
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

    @Test
    void schemaDeclaresTheReaderDefaultOnEnabled() {
        ObjectSchema schema = RpgStationsSettingsAsset.CODEC.toSchema(new SchemaContext());
        BooleanSchema enabled = (BooleanSchema) schema.getProperties().get("Enabled");
        assertEquals(Boolean.TRUE, enabled.getDefault(),
                "unauthored Enabled means the engine is live, and the exported schema must say so "
                        + "or the editor renders an unchecked box that lies about the effective value");
    }
}
