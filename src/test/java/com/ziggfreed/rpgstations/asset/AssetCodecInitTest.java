package com.ziggfreed.rpgstations.asset;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.codec.Rotation;
import com.ziggfreed.common.codec.TagMatch;
import com.ziggfreed.common.codec.Vec3;
import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.stamp.RollPoolAsset;
import com.ziggfreed.common.loot.stamp.StampSpec;
import com.ziggfreed.common.loot.stamp.StatRollEntry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertDoesNotThrow(() -> assertNotNull(Presentation.Interaction.CODEC));
    }

    /** Seam wave shared leaves (decisions 50/51): the ONE EffectRef / Picker codecs. */
    @Test
    void seamWaveSharedLeafCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(EffectRef.CODEC));
    }

    /** Seam wave Look nesting + FromCrafting native-composition groups (decisions 47/51c/52). */
    @Test
    void seamWaveNestedGroupCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Puppet.Look.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Model.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Role.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationAsset.FromCrafting.NativeTime.CODEC));
    }

    @Test
    void requiresAndConditionCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Requires.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Conditions.CODEC));
    }

    /**
     * The shared loot codecs this schema embeds. They are not this mod's to define, but they ARE
     * this mod's to load: a static-init failure in any of them takes the station store down at
     * server start exactly as one in a codec authored here would.
     */
    @Test
    void rollCodecAndNestedGroups_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Roll.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Roll.Ladder.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Roll.Ladder.Floor.CODEC));
        assertDoesNotThrow(() -> assertNotNull(LootGrants.CODEC));
    }

    /** The shared leaves every site embeds: the ONE Ingredient / factor-term / LootRef codecs. */
    @Test
    void sharedLeafCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(Ingredient.CODEC));
        assertDoesNotThrow(() -> assertNotNull(FactorFormula.CODEC));
        assertDoesNotThrow(() -> assertNotNull(FactorFormula.Term.CODEC));
        assertDoesNotThrow(() -> assertNotNull(LootRef.CODEC));
    }

    /** Scope-2 new Pattern-A types (design 1.5/1.8): ActionAsset + ExtensionAsset and their nested groups. */
    @Test
    void actionAssetAndExtensionAssetCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(ActionAsset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(ActionDef.Anchor.CODEC));
        assertDoesNotThrow(() -> assertNotNull(ExtensionAsset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(ExtensionAsset.Target.CODEC));
        assertDoesNotThrow(() -> assertNotNull(ExtensionAsset.StepInsertion.CODEC));
        assertDoesNotThrow(() -> assertNotNull(ExtensionAsset.StepInsertion.Anchor.CODEC));
    }

    /** The multiblock structure-pattern type and its nested groups. */
    @Test
    void structurePatternAssetCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(StructurePatternAsset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StructurePatternAsset.Identity.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StructurePatternAsset.Rotate.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StructurePatternAsset.Activate.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StructurePatternAsset.Cell.CODEC));
    }

    @Test
    void rollPoolAndStatRollEntryCodecs_initializeWithoutThrowing() {
        assertDoesNotThrow(() -> assertNotNull(RollPoolAsset.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StatRollEntry.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StatRollEntry.Points.CODEC));
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
        assertDoesNotThrow(() -> assertNotNull(StationStep.Walk.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Repeat.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Duration.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Stamp.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.Stamp.Economics.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StampSpec.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StampSpec.Caps.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StampSpec.Budget.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.PuppetOverride.CODEC));
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
        assertDoesNotThrow(() -> assertNotNull(Vec3.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Rotation.CODEC));
        assertDoesNotThrow(() -> assertNotNull(TagMatch.CODEC));
        assertDoesNotThrow(() -> assertNotNull(Puppet.Prop.CODEC));
        assertDoesNotThrow(() -> assertNotNull(StationStep.PuppetOverride.CODEC));
    }
}
