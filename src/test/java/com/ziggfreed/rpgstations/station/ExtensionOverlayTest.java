package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.rpgstations.asset.ContributionScale;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The PER-LEAF overlays ({@link ExtensionAsset}'s merge rule 5): an {@code Action}-targeted
 * {@code ExtensionAsset} may carry a {@code Puppet}, {@code Custody} and/or
 * {@code ContributionScale} group, and {@link ExtensionCatalog#overlayPuppet}/
 * {@link ExtensionCatalog#overlayCustody}/{@link ExtensionCatalog#overlayContributionScale} fold it
 * onto the base ACTION's own group LEAF BY LEAF at every nesting depth, never as a whole-group
 * replace.
 *
 * <p>THE case this whole capability exists for is
 * {@link #custodyOverlay_displayOnly_neverClobbersStatesMaxQuantityOrInput()}: a pack re-skinning a
 * station's placed-input visual authors {@code Custody.Display} alone, and the base's custody
 * MECHANICS ({@code States}/{@code MaxQuantity}/{@code Input}) must survive that untouched - a
 * whole-group replace would silently disable placement on the action it was only meant to re-skin.
 *
 * <p>Every fixture below is authored BY THIS TEST with invented values (no production balance number
 * from a shipped station/lootable appears here), decoded through the REAL shipped codecs so the test
 * exercises the schema rather than a hand-built object graph.
 */
public class ExtensionOverlayTest {

    /** The station fixture whose ONE action's groups play the BASE in every overlay case below. */
    private static final String BASE_STATION = "{ \"Actions\": [ { \"Id\": \"Mill\","
            + " \"Worker\": { \"Puppet\": {"
            + "   \"Enabled\": true,"
            + "   \"Hide\": { \"Route\": \"Scale\" },"
            + "   \"Look\": { \"Source\": \"PlayerClone\", \"FallbackModelId\": \"Fixture_Fallback\" },"
            + "   \"Offset\": { \"X\": 1.5, \"Y\": 2.5, \"Z\": 3.5 },"
            + "   \"Yaw\": 45.0,"
            + "   \"Prop\": { \"Source\": \"MirrorHeld\", \"Slot\": \"Hotbar\" }"
            + " } },"
            + " \"Custody\": {"
            + "   \"MaxQuantity\": 7,"
            + "   \"SingleFamily\": true,"
            + "   \"Input\": { \"ResourceTypeId\": \"Fixture_Family\" },"
            + "   \"States\": { \"Empty\": \"FixtureEmpty\", \"Loaded\": \"FixtureLoaded\","
            + "                 \"Working\": \"FixtureWorking\" },"
            + "   \"Display\": { \"Offset\": { \"X\": 0.25, \"Y\": 0.75, \"Z\": 1.25 }, \"Scale\": 2.0,"
            + "                  \"Rotation\": { \"Pitch\": 10.0 } }"
            + " } } ] }";

    private static StationAsset station(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static ExtensionAsset ext(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, id, null);
        return ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    /** The BASE action's own groups - the overlay target in every case below. */
    private static Custody baseCustody() throws Exception {
        return station(BASE_STATION).getActions()[0].getCustody();
    }

    private static Puppet basePuppet() throws Exception {
        return station(BASE_STATION).getActions()[0].getWorker().getPuppet();
    }

    private static double d(Double value) {
        assertNotNull(value, "expected an authored/inherited leaf, not null");
        return value.doubleValue();
    }

    // ==================== Codec round-trip: the overlay payload keys decode ====================

    @Test
    void overlayPayloadKeys_decodeThroughTheRealExtensionCodec() throws Exception {
        ExtensionAsset e = ext("fixture_skin", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Puppet\": { \"Look\": { \"Source\": \"NpcRole\","
                + "   \"Role\": { \"RoleId\": \"Fixture_Role\", \"Persist\": true, \"SpeedMps\": 3.25 } } },"
                + " \"Custody\": { \"Display\": { \"Scale\": 9.0 } },"
                + " \"ContributionScale\": { \"Floors\": [ { \"Min\": 3, \"Scale\": 2.0 } ] } }");

        assertNotNull(e.getPuppet(), "Puppet decodes off the extension codec");
        assertEquals(Puppet.LOOK_SOURCE_NPC_ROLE, e.getPuppet().getLook().effectiveSource());
        assertEquals("Fixture_Role", e.getPuppet().getLook().getRole().getRoleId());
        assertTrue(e.getPuppet().getLook().getRole().effectivePersist());
        assertEquals(3.25, d(e.getPuppet().getLook().getRole().getSpeedMps()));

        assertNotNull(e.getCustody(), "Custody decodes off the extension codec");
        assertEquals(9.0, d(e.getCustody().getDisplay().getScale()));
        assertNull(e.getCustody().getStates(), "an unauthored group stays null so the overlay can skip it");

        assertNotNull(e.getContributionScale(), "ContributionScale decodes off the extension codec");
        assertEquals(2.0, e.getContributionScale().getFloors()[0].effectiveScale());
        assertNull(e.getContributionScale().getFactors(), "an unauthored Factors array stays null");
    }

    @Test
    void overlayPayloadKeys_inheritWholesaleOnOmitThroughNativeParent() throws Exception {
        AssetExtraInfo.Data pData = new AssetExtraInfo.Data(ExtensionAsset.class, "fixture_base", null);
        ExtensionAsset parent = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Target\": { \"Action\": \"Mill\" },"
                        + " \"Puppet\": { \"Yaw\": 12.5 }, \"Custody\": { \"MaxQuantity\": 3 } }"),
                null, new AssetExtraInfo<>(pData));

        AssetExtraInfo.Data cData = new AssetExtraInfo.Data(ExtensionAsset.class, "fixture_child", "fixture_base");
        ExtensionAsset child = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{}"), parent, new AssetExtraInfo<>(cData));

        assertEquals(12.5, d(child.getPuppet().getYaw()), "Puppet inherits on omit");
        assertEquals(Integer.valueOf(3), child.getCustody().getMaxQuantity(), "Custody inherits on omit");
    }

    @Test
    void payloadAllowedFor_acceptsTheOverlaysOnAnActionTargetOnly() {
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION, ExtensionAsset.PAYLOAD_PUPPET));
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION, ExtensionAsset.PAYLOAD_CUSTODY));
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION,
                ExtensionAsset.PAYLOAD_CONTRIBUTION_SCALE));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_PUPPET),
                "a station carries no presentation group for an overlay to land on any more");
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_CUSTODY));
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_ACTIONS),
                "adding a whole new action is the one station-scoped statement left");
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.LOOTABLE, ExtensionAsset.PAYLOAD_PUPPET));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ROLLPOOL, ExtensionAsset.PAYLOAD_CUSTODY));
    }

    // ==================== Custody: the load-bearing Display-only case ====================

    @Test
    void custodyOverlay_displayOnly_neverClobbersStatesMaxQuantityOrInput() throws Exception {
        Custody base = baseCustody();
        ExtensionAsset skin = ext("fixture_skin",
                "{ \"Target\": { \"Action\": \"Mill\" }, \"Custody\": { \"Display\": { \"Scale\": 9.0 } } }");

        Custody merged = ExtensionCatalog.mergeCustody(base, ExtensionAsset.sortedForApply(List.of(skin)));

        // The one authored leaf wins.
        assertEquals(9.0, d(merged.getDisplay().getScale()));
        // Everything the overlay did NOT author survives, at BOTH nesting depths.
        assertEquals(Integer.valueOf(7), merged.getMaxQuantity(), "MaxQuantity survives a Display-only overlay");
        assertEquals(Boolean.TRUE, merged.getSingleFamily(), "SingleFamily survives a Display-only overlay");
        assertEquals("Fixture_Family", merged.getInput().getResourceTypeId(), "Input survives");
        assertEquals("FixtureEmpty", merged.getStates().getEmpty(), "States.Empty survives");
        assertEquals("FixtureLoaded", merged.getStates().getLoaded(), "States.Loaded survives");
        assertEquals("FixtureWorking", merged.getStates().getWorking(), "States.Working survives");
        assertEquals(0.25, d(merged.getDisplay().getOffset().getX()), "a sibling leaf inside Display survives");
        assertEquals(0.75, d(merged.getDisplay().getOffset().getY()));
        assertEquals(1.25, d(merged.getDisplay().getOffset().getZ()));
        assertEquals(10.0, d(merged.getDisplay().getRotation().getPitch()), "Rotation survives a Scale-only overlay");
    }

    @Test
    void custodyOverlay_authoredLeafWinsAtEveryDepth() throws Exception {
        Custody base = baseCustody();
        ExtensionAsset skin = ext("fixture_skin", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Custody\": { \"MaxQuantity\": 42, \"States\": { \"Loaded\": \"FixtureReskinLoaded\" },"
                + "                \"Display\": { \"Offset\": { \"Y\": 5.5 } } } }");

        Custody merged = ExtensionCatalog.overlayCustody(base, skin.getCustody());

        assertEquals(Integer.valueOf(42), merged.getMaxQuantity());
        assertEquals("FixtureReskinLoaded", merged.getStates().getLoaded(), "an authored States leaf wins");
        assertEquals("FixtureEmpty", merged.getStates().getEmpty(), "its unauthored sibling still survives");
        assertEquals("FixtureWorking", merged.getStates().getWorking(), "every States knob overlays on its own");
        assertEquals(5.5, d(merged.getDisplay().getOffset().getY()));
        assertEquals(0.25, d(merged.getDisplay().getOffset().getX()), "an unauthored Offset axis survives");
        assertEquals(2.0, d(merged.getDisplay().getScale()), "an unauthored Display leaf survives");
    }

    // ==================== Puppet: the same rule, one group over ====================

    @Test
    void puppetOverlay_partialOffset_keepsEveryOtherLeafAndAxis() throws Exception {
        Puppet base = basePuppet();
        ExtensionAsset skin = ext("fixture_skin", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Puppet\": { \"Offset\": { \"Z\": 8.5 } } }");

        Puppet merged = ExtensionCatalog.mergePuppet(base, ExtensionAsset.sortedForApply(List.of(skin)));

        assertEquals(8.5, d(merged.getOffset().getZ()));
        assertEquals(1.5, d(merged.getOffset().getX()), "an unauthored Offset axis survives");
        assertEquals(2.5, d(merged.getOffset().getY()));
        assertEquals(45.0, d(merged.getYaw()), "Yaw survives an Offset-only overlay");
        assertTrue(merged.effectiveEnabled());
        assertEquals(Puppet.HIDE_ROUTE_SCALE, merged.getHide().effectiveRoute());
        assertEquals(Puppet.LOOK_SOURCE_PLAYER_CLONE, merged.getLook().effectiveSource());
        assertEquals("Fixture_Fallback", merged.getLook().getFallbackModelId());
        assertEquals(Puppet.PROP_SOURCE_MIRROR_HELD, merged.getProp().effectiveSource());
        assertEquals(Puppet.PROP_SLOT_HOTBAR, merged.getProp().effectiveSlot());
    }

    @Test
    void puppetOverlay_reskinToAnNpcRolePerformer_keepsThePlacementLeaves() throws Exception {
        Puppet base = basePuppet();
        ExtensionAsset skin = ext("fixture_skin", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Puppet\": { \"Look\": { \"Source\": \"NpcRole\", \"Role\": { \"RoleId\": \"Fixture_Role\" } },"
                + "               \"Prop\": { \"Source\": \"ItemId\", \"ItemId\": \"Fixture_Prop\" } } }");

        Puppet merged = ExtensionCatalog.overlayPuppet(base, skin.getPuppet());

        assertEquals(Puppet.LOOK_SOURCE_NPC_ROLE, merged.getLook().effectiveSource());
        assertEquals("Fixture_Role", merged.getLook().getRole().getRoleId());
        assertEquals("Fixture_Fallback", merged.getLook().getFallbackModelId(),
                "the base's any-source fallback model survives an arm swap");
        assertEquals(Puppet.PROP_SOURCE_ITEM_ID, merged.getProp().effectiveSource());
        assertEquals("Fixture_Prop", merged.getProp().getItemId());
        assertEquals(Puppet.PROP_SLOT_HOTBAR, merged.getProp().effectiveSlot(), "the base's Slot survives");
        assertEquals(1.5, d(merged.getOffset().getX()), "placement leaves are untouched by a look re-skin");
        assertEquals(45.0, d(merged.getYaw()));
    }

    // ==================== ContributionScale: the same rule again ====================

    @Test
    void contributionScaleOverlay_floorsOnly_keepsTheBaseFactors() throws Exception {
        StationAsset base = station("{ \"Actions\": [ { \"Id\": \"Mill\", \"ContributionScale\": {"
                + " \"Factors\": [ { \"Factor\": \"fixture:axis\", \"Weight\": 4.0 } ],"
                + " \"Floors\": [ { \"Min\": 1, \"Scale\": 1.5 } ] } } ] }");
        ExtensionAsset retune = ext("fixture_retune", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"ContributionScale\": { \"Floors\": [ { \"Min\": 9, \"Scale\": 3.0 } ] } }");

        ContributionScale merged = ExtensionCatalog.mergeContributionScale(
                base.getActions()[0].getContributionScale(), ExtensionAsset.sortedForApply(List.of(retune)));

        assertEquals("fixture:axis", merged.getFactors()[0].getFactor(), "the base Factors survive");
        assertEquals(4.0, merged.getFactors()[0].effectiveWeight());
        assertEquals(1, merged.getFloors().length, "an authored Floors array replaces wholesale (it is one leaf)");
        assertEquals(9.0, merged.getFloors()[0].effectiveMin());
        assertEquals(3.0, merged.getFloors()[0].effectiveScale());
    }

    // ==================== Identity + determinism ====================

    @Test
    void overlay_isIdentityWhenNoExtensionAuthorsTheGroup() throws Exception {
        ExtensionAsset bonusOnly = ext("fixture_bonus",
                "{ \"Target\": { \"Action\": \"Mill\" }, \"Bonus\": { \"Lootables\": [\"fixturetable\"] } }");
        List<ExtensionAsset> exts = ExtensionAsset.sortedForApply(List.of(bonusOnly));

        Puppet puppet = basePuppet();
        Custody custody = baseCustody();
        assertSame(puppet, ExtensionCatalog.mergePuppet(puppet, exts));
        assertSame(custody, ExtensionCatalog.mergeCustody(custody, exts));
    }

    @Test
    void overlay_onANullBase_yieldsTheOverlayItself() throws Exception {
        ExtensionAsset skin = ext("fixture_skin", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Puppet\": { \"Yaw\": 99.0 }, \"Custody\": { \"MaxQuantity\": 4 } }");

        assertEquals(99.0, d(ExtensionCatalog.overlayPuppet(null, skin.getPuppet()).getYaw()));
        assertEquals(Integer.valueOf(4), ExtensionCatalog.overlayCustody(null, skin.getCustody()).getMaxQuantity());
    }

    @Test
    void overlay_higherPriorityWinsASameLeafContest_regardlessOfEncounterOrder() throws Exception {
        Custody base = baseCustody();
        // "a_high" sorts FIRST by id but LAST by priority; APPLY_ORDER is priority-major, so it wins.
        ExtensionAsset high = ext("a_high", "{ \"Target\": { \"Action\": \"Mill\" }, \"Priority\": 5,"
                + " \"Custody\": { \"Display\": { \"Scale\": 6.0 } } }");
        ExtensionAsset low = ext("z_low", "{ \"Target\": { \"Action\": \"Mill\" }, \"Priority\": 0,"
                + " \"Custody\": { \"Display\": { \"Scale\": 3.0 } } }");

        Custody hl = ExtensionCatalog.mergeCustody(base, ExtensionAsset.sortedForApply(List.of(high, low)));
        Custody lh = ExtensionCatalog.mergeCustody(base, ExtensionAsset.sortedForApply(List.of(low, high)));

        assertEquals(6.0, d(hl.getDisplay().getScale()), "the higher priority overlays LAST and wins the leaf");
        assertEquals(d(hl.getDisplay().getScale()), d(lh.getDisplay().getScale()),
                "same input set -> identical merged result regardless of fold encounter order");
    }

    @Test
    void overlay_twoExtensionsTouchingDifferentLeaves_bothLand() throws Exception {
        Puppet base = basePuppet();
        ExtensionAsset a = ext("a_ext", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Puppet\": { \"Yaw\": 77.0 } }");
        ExtensionAsset b = ext("b_ext", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Puppet\": { \"Offset\": { \"Y\": -6.5 } } }");

        Puppet merged = ExtensionCatalog.mergePuppet(base, ExtensionAsset.sortedForApply(List.of(b, a)));

        assertEquals(77.0, d(merged.getYaw()));
        assertEquals(-6.5, d(merged.getOffset().getY()));
        assertEquals(1.5, d(merged.getOffset().getX()), "the base still supplies every untouched leaf");
    }

    // ==================== Station target: appending a whole NEW action ====================

    @Test
    void mergeActions_appendsNewActionsAfterTheBaseSoPriorityIsPreserved() throws Exception {
        StationAsset base = station("{ \"Actions\": [ { \"Id\": \"Mill\" } ] }");
        ExtensionAsset add = ext("fixture_add", "{ \"Target\": { \"Station\": \"fixture\" },"
                + " \"Actions\": [ { \"Id\": \"Polish\" } ] }");

        var merged = ExtensionCatalog.mergeActions(base.getActions(),
                ExtensionAsset.sortedForApply(List.of(add)));

        assertEquals(2, merged.length);
        assertEquals("Mill", merged[0].getId(), "a base action keeps its higher selection priority");
        assertEquals("Polish", merged[1].getId());
    }

    @Test
    void mergeActions_baseWinsAnIdCollision() throws Exception {
        StationAsset base = station("{ \"Actions\": [ { \"Id\": \"Mill\", \"Label\": \"base.label\" } ] }");
        ExtensionAsset clash = ext("fixture_clash", "{ \"Target\": { \"Station\": \"fixture\" },"
                + " \"Actions\": [ { \"Id\": \"mill\", \"Label\": \"extension.label\" } ] }");

        var merged = ExtensionCatalog.mergeActions(base.getActions(),
                ExtensionAsset.sortedForApply(List.of(clash)));

        assertEquals(1, merged.length, "ids match case-insensitively, so the colliding entry is skipped");
        assertEquals("base.label", merged[0].getLabel());
    }

    // ==================== The EXTENSION_APPLIED contribution-kind enumeration ====================

    @Test
    void authoredPayloadKinds_enumeratesTheOverlaysBesideTheCollectionPayloads() throws Exception {
        ExtensionAsset e = ext("fixture_mixed", "{ \"Target\": { \"Action\": \"Mill\" },"
                + " \"Bonus\": { \"Lootables\": [\"fixturetable\"] },"
                + " \"ContributionScale\": { \"Floors\": [ { \"Min\": 2, \"Scale\": 2.0 } ] },"
                + " \"Puppet\": { \"Yaw\": 1.0 },"
                + " \"Custody\": { \"Display\": { \"Scale\": 1.5 } } }");

        assertEquals(List.of(ExtensionAsset.PAYLOAD_BONUS, ExtensionAsset.PAYLOAD_CONTRIBUTION_SCALE,
                        ExtensionAsset.PAYLOAD_PUPPET, ExtensionAsset.PAYLOAD_CUSTODY),
                ExtensionCatalog.authoredPayloadKinds(e));
    }

    @Test
    void authoredPayloadKinds_isEmptyForATargetOnlyExtension() throws Exception {
        ExtensionAsset e = ext("fixture_bare", "{ \"Target\": { \"Action\": \"Mill\" } }");
        assertTrue(ExtensionCatalog.authoredPayloadKinds(e).isEmpty());
    }

    // ==================== The ONE Action-target identity ====================

    @Test
    void actionTargetId_oneRuleForEveryActionTargetedPayload() throws Exception {
        StationAsset withActions = station("{ \"Actions\": ["
                + " { \"Id\": \"Forge\", \"Ref\": \"Fixture_SharedForge\" },"
                + " { \"Id\": \"Inline\", \"Work\": { \"CycleMs\": 1234 } } ] }");
        assertEquals("Fixture_SharedForge", ActionResolver.actionTargetId(withActions, "Forge"),
                "a Ref entry is targeted by its ActionAsset id, so an Action target reaches every user of it");
        assertEquals("Inline", ActionResolver.actionTargetId(withActions, "Inline"),
                "an inline-only entry is targeted by its own Id");
        assertNull(ActionResolver.actionTargetId(withActions, "Unknown"),
                "an unknown action id has no Action-target identity");
    }
}
