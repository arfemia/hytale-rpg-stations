package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Forces static init of every RpgStations asset codec so a lower-case {@code KeyedCodec} key
 * (which throws at {@code AssetBuilderCodec}/{@code BuilderCodec} class-init) is caught by the
 * build, not the running server. Ported from the MMO's {@code AssetCodecInitTest} convention
 * (RPG Stations extraction leg 2, extended leg 3 with the three asset-store types this jar now
 * registers - {@link StationAsset}, {@link LootableAsset}, {@link SettingsAsset} - plus the
 * {@link Roll} value type shared by the loot layer's two asset-store consumers).
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

    @Test
    void rollCodecAndNestedGroups_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Roll.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Roll.Chance.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Roll.Ladder.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Roll.Ladder.Floor.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Roll.Grants.CODEC));
    }

    @Test
    void lootableAssetCodec_initializesWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(LootableAsset.CODEC));
    }

    @Test
    void settingsAssetCodec_initializesWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(SettingsAsset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(SettingsAsset.SummaryHud.CODEC));
    }
}
