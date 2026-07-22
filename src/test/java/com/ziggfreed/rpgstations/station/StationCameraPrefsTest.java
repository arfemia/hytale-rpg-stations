package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link StationCameraPrefs}. Ported verbatim from the MMO's
 * {@code StationCameraPrefsTest} (RPG Stations extraction leg 2).
 */
public class StationCameraPrefsTest {

    @Test
    void unsetPlayer_resolvesToDefault() {
        UUID neverSet = UUID.randomUUID();
        assertSame(StationCameraPreset.DEFAULT, StationCameraPrefs.get(neverSet));
    }

    @Test
    void setThenGet_roundTrips() {
        UUID player = UUID.randomUUID();
        StationCameraPrefs.set(player, StationCameraPreset.LOOK_ROT);
        assertSame(StationCameraPreset.LOOK_ROT, StationCameraPrefs.get(player));
    }

    @Test
    void perPlayerIsolation_oneSetterDoesNotAffectAnother() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        StationCameraPrefs.set(playerA, StationCameraPreset.FREE_DIR);
        StationCameraPrefs.set(playerB, StationCameraPreset.FREE_NULL);

        assertEquals(StationCameraPreset.FREE_DIR, StationCameraPrefs.get(playerA));
        assertEquals(StationCameraPreset.FREE_NULL, StationCameraPrefs.get(playerB));
    }

    @Test
    void reSet_overwritesThePreviousPresetForTheSamePlayer() {
        UUID player = UUID.randomUUID();
        StationCameraPrefs.set(player, StationCameraPreset.FROZEN);
        StationCameraPrefs.set(player, StationCameraPreset.FREE_NULL);
        assertSame(StationCameraPreset.FREE_NULL, StationCameraPrefs.get(player));
    }

    @Test
    void getExplicit_unsetPlayer_returnsNull() {
        UUID neverSet = UUID.randomUUID();
        assertNull(StationCameraPrefs.getExplicit(neverSet));
    }

    @Test
    void getExplicit_setPlayer_returnsTheirChoice() {
        UUID player = UUID.randomUUID();
        StationCameraPrefs.set(player, StationCameraPreset.CUSTOM_SEED);
        assertSame(StationCameraPreset.CUSTOM_SEED, StationCameraPrefs.getExplicit(player));
    }

    @Test
    void getExplicit_evenWhenSetToFrozen_isDistinguishableFromUnset() {
        UUID player = UUID.randomUUID();
        StationCameraPrefs.set(player, StationCameraPreset.FROZEN);
        assertSame(StationCameraPreset.FROZEN, StationCameraPrefs.getExplicit(player));
    }
}
