package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationCameraPreset}. Ported verbatim from the MMO's
 * {@code StationCameraPresetTest} (RPG Stations extraction leg 2).
 */
public class StationCameraPresetTest {

    @Test
    void defaultIsFrozen() {
        assertSame(StationCameraPreset.FROZEN, StationCameraPreset.DEFAULT);
    }

    @Test
    void fromId_parsesEveryDeclaredPreset_caseInsensitiveAndTrimmed() {
        for (StationCameraPreset preset : StationCameraPreset.values()) {
            assertSame(preset, StationCameraPreset.fromId(preset.id()));
            assertSame(preset, StationCameraPreset.fromId(preset.id().toUpperCase(java.util.Locale.ROOT)));
            assertSame(preset, StationCameraPreset.fromId("  " + preset.id() + "  "));
        }
    }

    @Test
    void fromId_knownIds_matchTheBriefedPresetNames() {
        assertSame(StationCameraPreset.FROZEN, StationCameraPreset.fromId("frozen"));
        assertSame(StationCameraPreset.FREE_NULL, StationCameraPreset.fromId("free_null"));
        assertSame(StationCameraPreset.FREE_DIR, StationCameraPreset.fromId("free_dir"));
        assertSame(StationCameraPreset.LOOK_ROT, StationCameraPreset.fromId("look_rot"));
        assertSame(StationCameraPreset.LOOK_ROT_BLEND, StationCameraPreset.fromId("look_rot_blend"));
        assertSame(StationCameraPreset.LOOK_ROT_NO_TARGET, StationCameraPreset.fromId("look_rot_no_target"));
        assertSame(StationCameraPreset.LOOK_ROT_ATTACHED, StationCameraPreset.fromId("look_rot_attached"));
        assertSame(StationCameraPreset.CUSTOM_SEED, StationCameraPreset.fromId("custom_seed"));
    }

    @Test
    void fromId_unknownOrNull_returnsNull() {
        assertNull(StationCameraPreset.fromId("not_a_preset"));
        assertNull(StationCameraPreset.fromId(""));
        assertNull(StationCameraPreset.fromId(null));
    }

    @Test
    void id_isLowerCasedEnumName() {
        assertEquals("frozen", StationCameraPreset.FROZEN.id());
        assertEquals("free_null", StationCameraPreset.FREE_NULL.id());
        assertEquals("free_dir", StationCameraPreset.FREE_DIR.id());
        assertEquals("look_rot", StationCameraPreset.LOOK_ROT.id());
        assertEquals("look_rot_blend", StationCameraPreset.LOOK_ROT_BLEND.id());
        assertEquals("look_rot_no_target", StationCameraPreset.LOOK_ROT_NO_TARGET.id());
        assertEquals("look_rot_attached", StationCameraPreset.LOOK_ROT_ATTACHED.id());
        assertEquals("custom_seed", StationCameraPreset.CUSTOM_SEED.id());
    }

    @Test
    void resolve_explicitOverride_alwaysWins() {
        assertSame(StationCameraPreset.FREE_DIR,
                StationCameraPreset.resolve(StationCameraPreset.FREE_DIR, "look_rot"));
        assertSame(StationCameraPreset.FROZEN,
                StationCameraPreset.resolve(StationCameraPreset.FROZEN, "custom_seed"));
    }

    @Test
    void resolve_noOverride_usesAssetDefault() {
        assertSame(StationCameraPreset.FROZEN, StationCameraPreset.resolve(null, "frozen"));
        assertSame(StationCameraPreset.CUSTOM_SEED, StationCameraPreset.resolve(null, "custom_seed"));
        assertSame(StationCameraPreset.LOOK_ROT_BLEND, StationCameraPreset.resolve(null, "look_rot_blend"));
    }

    @Test
    void resolve_noOverrideAndNoAssetDefault_fallsBackToLookRot() {
        assertSame(StationCameraPreset.LOOK_ROT, StationCameraPreset.resolve(null, null));
        assertSame(StationCameraPreset.LOOK_ROT, StationCameraPreset.resolve(null, ""));
        assertSame(StationCameraPreset.LOOK_ROT, StationCameraPreset.resolve(null, "not_a_real_preset"));
    }

    @Test
    void resolve_neverDefaultsToFrozen_whenNothingIsSet() {
        assertNotSame(StationCameraPreset.FROZEN, StationCameraPreset.resolve(null, null));
    }
}
