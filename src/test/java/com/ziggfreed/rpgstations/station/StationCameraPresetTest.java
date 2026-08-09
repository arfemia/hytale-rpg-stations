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
        assertSame(StationCameraPreset.FROZEN, StationCameraPreset.fromId("Frozen"));
        assertSame(StationCameraPreset.FREE_NULL, StationCameraPreset.fromId("FreeNull"));
        assertSame(StationCameraPreset.FREE_DIR, StationCameraPreset.fromId("FreeDir"));
        assertSame(StationCameraPreset.LOOK_ROT, StationCameraPreset.fromId("LookRot"));
        assertSame(StationCameraPreset.LOOK_ROT_BLEND, StationCameraPreset.fromId("LookRotBlend"));
        assertSame(StationCameraPreset.LOOK_ROT_NO_TARGET, StationCameraPreset.fromId("LookRotNoTarget"));
        assertSame(StationCameraPreset.LOOK_ROT_ATTACHED, StationCameraPreset.fromId("LookRotAttached"));
        assertSame(StationCameraPreset.CUSTOM_SEED, StationCameraPreset.fromId("CustomSeed"));
    }

    @Test
    void fromId_unknownOrNull_returnsNull() {
        assertNull(StationCameraPreset.fromId("not_a_preset"));
        assertNull(StationCameraPreset.fromId(""));
        assertNull(StationCameraPreset.fromId(null));
    }

    @Test
    void id_isLowerCasedEnumName() {
        assertEquals("Frozen", StationCameraPreset.FROZEN.id());
        assertEquals("FreeNull", StationCameraPreset.FREE_NULL.id());
        assertEquals("FreeDir", StationCameraPreset.FREE_DIR.id());
        assertEquals("LookRot", StationCameraPreset.LOOK_ROT.id());
        assertEquals("LookRotBlend", StationCameraPreset.LOOK_ROT_BLEND.id());
        assertEquals("LookRotNoTarget", StationCameraPreset.LOOK_ROT_NO_TARGET.id());
        assertEquals("LookRotAttached", StationCameraPreset.LOOK_ROT_ATTACHED.id());
        assertEquals("CustomSeed", StationCameraPreset.CUSTOM_SEED.id());
    }

    @Test
    void resolve_explicitOverride_alwaysWins() {
        assertSame(StationCameraPreset.FREE_DIR,
                StationCameraPreset.resolve(StationCameraPreset.FREE_DIR, "LookRot"));
        assertSame(StationCameraPreset.FROZEN,
                StationCameraPreset.resolve(StationCameraPreset.FROZEN, "CustomSeed"));
    }

    @Test
    void resolve_noOverride_usesAssetDefault() {
        assertSame(StationCameraPreset.FROZEN, StationCameraPreset.resolve(null, "Frozen"));
        assertSame(StationCameraPreset.CUSTOM_SEED, StationCameraPreset.resolve(null, "CustomSeed"));
        assertSame(StationCameraPreset.LOOK_ROT_BLEND, StationCameraPreset.resolve(null, "LookRotBlend"));
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
