package com.ziggfreed.rpgstations.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

/**
 * Codec + pure-core layer for the scope-2 {@link ExtensionAsset} (design 1.8): id canonicalization,
 * the {@code Target} exactly-one-of, payload decode + {@link ExtensionAsset#payloadAllowedFor}, the
 * {@link ExtensionAsset.StepInsertion.Anchor} exactly-one-of + degrade rule, and the DETERMINISTIC
 * {@link ExtensionAsset#sortedForApply} ordering (the merge-logic half both this leg and the engine
 * leg test against - the ordering pure core lives here so the catalog can reuse it).
 */
public class ExtensionAssetCodecTest {

    private static ExtensionAsset decode(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, id, null);
        return ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    private static ExtensionAsset decodeAnon(String body) throws Exception {
        return ExtensionAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body), new ExtraInfo());
    }

    // ==================== id + Target exactly-one-of ====================

    @Test
    void id_isLowercasedAtDecode() throws Exception {
        ExtensionAsset e = decode("SawmillProgression", "{}");
        assertEquals("sawmillprogression", e.getId());
    }

    @Test
    void target_exactlyOneOf_resolvesTypeAndId() throws Exception {
        ExtensionAsset e = decodeAnon("{ \"Target\": { \"Station\": \"sawmill\" } }");
        assertTrue(e.getTarget().hasExactlyOneTarget());
        assertEquals(ExtensionAsset.Target.STATION, e.getTarget().resolvedType());
        assertEquals("sawmill", e.getTarget().resolvedId());

        ExtensionAsset action = decodeAnon("{ \"Target\": { \"Action\": \"prepfish\" } }");
        assertEquals(ExtensionAsset.Target.ACTION, action.getTarget().resolvedType());
        assertEquals("prepfish", action.getTarget().resolvedId());
    }

    @Test
    void target_zeroOrTwoLeaves_isNotExactlyOne() throws Exception {
        ExtensionAsset none = decodeAnon("{ \"Target\": {} }");
        assertFalse(none.getTarget().hasExactlyOneTarget());
        assertNull(none.getTarget().resolvedType());

        ExtensionAsset two = decodeAnon("{ \"Target\": { \"Station\": \"s\", \"Lootable\": \"l\" } }");
        assertFalse(two.getTarget().hasExactlyOneTarget());
        assertNull(two.getTarget().resolvedId());
    }

    // ==================== payload decode + legality ====================

    @Test
    void stationTargetPayload_decodes() throws Exception {
        ExtensionAsset e = decodeAnon("{ \"Target\": { \"Station\": \"sawmill\" }, \"Priority\": 5,"
                + " \"Xp\": [ { \"Skill\": \"WOODCUTTING\", \"PerCycle\": 12 } ],"
                + " \"Loot\": { \"Lootables\": [\"sawmillluck\"] },"
                + " \"Conversions\": [ { \"Input\": { \"ResourceTypeId\": \"Wood_Hardwood_Trunk\", \"Quantity\": 1 },"
                + "   \"Output\": { \"ItemId\": \"Wood_Hardwood_Planks\", \"Quantity\": 2 } } ],"
                + " \"Steps\": [ { \"Action\": \"work\", \"Anchor\": { \"After\": \"roll\" },"
                + "   \"Insert\": [ { \"Id\": \"extra\", \"Commands\": [\"say hi\"] } ] } ] }");
        assertEquals(5, e.effectivePriority());
        assertEquals("WOODCUTTING", e.getXp()[0].getSkill());
        assertEquals("sawmillluck", e.getLoot().getLootables()[0]);
        assertEquals("Wood_Hardwood_Planks", e.getConversions()[0].getOutput().getItemId());
        ExtensionAsset.StepInsertion ins = e.getSteps()[0];
        assertEquals("work", ins.getAction());
        assertEquals(ExtensionAsset.StepInsertion.Anchor.AFTER, ins.getAnchor().effectivePlacement());
        assertEquals("roll", ins.getAnchor().anchorStepId());
        assertEquals("extra", ins.getInsert()[0].getId());
    }

    @Test
    void payloadAllowedFor_matchesTheDesignTable() {
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_XP));
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_ACTIONS));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.STATION, ExtensionAsset.PAYLOAD_ENTRIES));
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.LOOTABLE, ExtensionAsset.PAYLOAD_ROLLS));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.LOOTABLE, ExtensionAsset.PAYLOAD_XP));
        assertTrue(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ROLLPOOL, ExtensionAsset.PAYLOAD_ENTRIES));
        assertFalse(ExtensionAsset.payloadAllowedFor(ExtensionAsset.Target.ACTION, ExtensionAsset.PAYLOAD_ACTIONS));
        assertFalse(ExtensionAsset.payloadAllowedFor(null, ExtensionAsset.PAYLOAD_XP));
    }

    // ==================== StepInsertion.Anchor exactly-one-of + degrade ====================

    @Test
    void insertionAnchor_exactlyOneOf_andDegradesToAtEnd() {
        assertEquals(ExtensionAsset.StepInsertion.Anchor.AFTER,
                ExtensionAsset.StepInsertion.Anchor.after("x").effectivePlacement());
        assertEquals(ExtensionAsset.StepInsertion.Anchor.AT_START,
                ExtensionAsset.StepInsertion.Anchor.atStart().effectivePlacement());
        assertTrue(ExtensionAsset.StepInsertion.Anchor.atEnd().hasExactlyOnePlacement());

        ExtensionAsset.StepInsertion.Anchor empty = new ExtensionAsset.StepInsertion.Anchor();
        assertFalse(empty.hasExactlyOnePlacement());
        assertEquals(ExtensionAsset.StepInsertion.Anchor.AT_END, empty.effectivePlacement(),
                "no placement authored degrades to AtEnd");
        assertNull(empty.anchorStepId());
    }

    // ==================== Deterministic apply ordering (merge-logic pure core) ====================

    @Test
    void sortedForApply_ordersByPriorityThenId_deterministic() throws Exception {
        ExtensionAsset p0a = ExtensionAsset.of("alpha", null, 0);
        ExtensionAsset p0b = ExtensionAsset.of("beta", null, 0);
        ExtensionAsset p5 = ExtensionAsset.of("zeta", null, 5);
        ExtensionAsset p2 = ExtensionAsset.of("gamma", null, 2);

        // Any encounter order collapses to the SAME (Priority asc, id lex) order.
        List<String> order1 = ids(ExtensionAsset.sortedForApply(Arrays.asList(p5, p0b, p2, p0a)));
        List<String> order2 = ids(ExtensionAsset.sortedForApply(Arrays.asList(p0a, p2, p5, p0b)));
        assertEquals(List.of("alpha", "beta", "gamma", "zeta"), order1);
        assertEquals(order1, order2, "same input set -> identical composed order regardless of encounter order");
    }

    @Test
    void sortedForApply_coAnchoredSamePriority_breaksByIdLex() {
        // m2: two extensions co-anchored on one step apply in (Priority, id) order.
        ExtensionAsset b = ExtensionAsset.of("b_ext", null, 0);
        ExtensionAsset a = ExtensionAsset.of("a_ext", null, 0);
        assertEquals(List.of("a_ext", "b_ext"), ids(ExtensionAsset.sortedForApply(Arrays.asList(b, a))));
    }

    // ==================== Native Parent inheritance ====================

    @Test
    void parentInheritance_wholesaleOnOmit() throws Exception {
        AssetExtraInfo.Data pData = new AssetExtraInfo.Data(ExtensionAsset.class, "base_ext", null);
        ExtensionAsset parent = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Target\": { \"Station\": \"sawmill\" }, \"Priority\": 3 }"),
                null, new AssetExtraInfo<>(pData));
        assertEquals(3, parent.effectivePriority());

        AssetExtraInfo.Data cData = new AssetExtraInfo.Data(ExtensionAsset.class, "child_ext", "base_ext");
        ExtensionAsset child = ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{}"), parent, new AssetExtraInfo<>(cData));
        assertNotNull(child.getTarget(), "Target inherits wholesale on omit");
        assertEquals("sawmill", child.getTarget().getStation());
        assertEquals(3, child.effectivePriority(), "Priority inherits on omit");
    }

    private static List<String> ids(List<ExtensionAsset> list) {
        return list.stream().map(ExtensionAsset::getId).collect(Collectors.toList());
    }
}
