package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Forces static init of every RpgStations asset codec so a lower-case {@code KeyedCodec} key
 * (which throws at {@code AssetBuilderCodec}/{@code BuilderCodec} class-init) is caught by the
 * build, not the running server. Ported from the MMO's {@code AssetCodecInitTest} convention
 * (RPG Stations extraction leg 2, extended leg 3 with the three asset-store types this jar now
 * registers - {@link StationAsset}, {@link LootableAsset}, {@link RpgStationsSettingsAsset} - plus the
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
        assertDoesNotThrow(() -> assertNotNull(RpgStationsSettingsAsset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(RpgStationsSettingsAsset.SummaryHud.CODEC));
    }

    /**
     * Design section 9.1's multi-action schema (leg B). {@code stationAssetCodec_
     * initializesWithoutThrowing} already exercises this whole tree TRANSITIVELY
     * ({@link StationAsset#CODEC}'s {@code Actions} field references {@link ActionDef#CODEC},
     * which references {@link ActionInput#CODEC} and {@link StationStep#CODEC}, which references
     * every nested step-group codec) - these explicit assertions are the same PascalCase-key
     * guard at the SAME granularity every other type in this file gets.
     */
    @Test
    void actionDefAndActionInputCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(ActionDef.CODEC));
        assertDoesNotThrow(() -> assertNotNull(ActionInput.CODEC));
    }

    @Test
    void stationStepCodecAndNestedGroups_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(StationStep.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.OnConditionFail.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Consume.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Produce.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Wait.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.RollGroup.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.CommandGroup.CODEC));
    }

    /**
     * The round-4 puppet-presentation route (design {@code rpg-stations-puppet-presentation
     * -design-2026-07-22.md} section 3): the top-level {@link Puppet} group, its per-action
     * whole-group override on {@link ActionDef}, and the per-step small override on
     * {@link StationStep.PuppetOverride} (which reuses {@link Puppet.Prop}'s codec verbatim).
     */
    @Test
    void puppetCodecAndNestedGroups_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Puppet.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Hide.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Look.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Offset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Prop.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.PuppetOverride.CODEC));
    }
}
