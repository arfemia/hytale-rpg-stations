package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Forces static init of every RpgStations asset codec so a lower-case {@code KeyedCodec} key
 * (which throws at {@code AssetBuilderCodec}/{@code BuilderCodec} class-init) is caught by the
 * build, not the running server. Ported from the MMO's {@code AssetCodecInitTest} convention
 * (RPG Stations extraction leg 2) - RpgStations has one asset-store type this leg
 * ({@link StationAsset}), whose builder chain transitively touches every nested codec
 * ({@link Presentation}, {@link Presentation.Shake}, {@link Requires}, {@link Condition},
 * and every {@code StationAsset} nested group) at class-load, so a single assertion covers
 * the whole schema surface.
 */
public class AssetCodecInitTest {

    @Test
    void stationAssetCodec_initializesWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(StationAsset.CODEC));
    }

    @Test
    void presentationCodec_initializesWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Presentation.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Presentation.Shake.CODEC));
    }

    @Test
    void requiresAndConditionCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Requires.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Condition.CODEC));
    }
}
