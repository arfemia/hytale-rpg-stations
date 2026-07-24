package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * Determinism coverage for the {@link ExtensionCatalog} pure merge cores (scope-2 design 1.8,
 * decision 37): the {@code (Priority, extension id)} apply order fully determines the merged result
 * for a given input SET, regardless of the order the fold happened to encounter the extensions in -
 * incl. two extensions co-anchored on ONE step (m2). Fixtures decode through the real
 * {@link ExtensionAsset#CODEC} so the test exercises the shipped schema, not a hand-built shape.
 */
public class ExtensionCatalogTest {

    private static ExtensionAsset ext(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, id, null);
        return ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    private static List<String> stepIds(List<StationStep> steps) {
        List<String> ids = new ArrayList<>();
        for (StationStep s : steps) {
            ids.add(s.getId());
        }
        return ids;
    }

    // ==================== mergeSteps: co-anchored insertion determinism (m2) ====================

    @Test
    void mergeSteps_twoExtensionsCoAnchoredOnOneStep_applyInPriorityThenIdOrder() throws Exception {
        // Both insert AFTER "base1"; equal priority 0 -> id lex tie-break: "a-ext" before "b-ext".
        ExtensionAsset a = ext("a-ext",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { \"Action\":\"work\", "
                        + "\"Anchor\":{\"After\":\"base1\"}, \"Insert\":[ {\"Id\":\"a1\"} ] } ] }");
        ExtensionAsset b = ext("b-ext",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { \"Action\":\"work\", "
                        + "\"Anchor\":{\"After\":\"base1\"}, \"Insert\":[ {\"Id\":\"b1\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"), StationStep.of("base2"));

        // Feed the two extensions in BOTH encounter orders; sortedForApply must collapse both to
        // the same deterministic result.
        List<StationStep> ab = ExtensionCatalog.mergeSteps(base, "work", ExtensionAsset.sortedForApply(List.of(a, b)));
        List<StationStep> ba = ExtensionCatalog.mergeSteps(base, "work", ExtensionAsset.sortedForApply(List.of(b, a)));

        assertEquals(List.of("base1", "a1", "b1", "base2"), stepIds(ab));
        assertEquals(stepIds(ab), stepIds(ba), "the merged result is identical regardless of fold encounter order");
    }

    @Test
    void mergeSteps_priorityWins_higherPriorityAppliesLater() throws Exception {
        // priority 5 applies LATER than priority 0, so for two "After base1" inserts the priority-0
        // block lands first, the priority-5 block after it.
        ExtensionAsset low = ext("z-low",
                "{ \"Target\":{\"Station\":\"s\"}, \"Priority\":0, \"Steps\":[ { \"Anchor\":{\"After\":\"base1\"}, "
                        + "\"Insert\":[ {\"Id\":\"low\"} ] } ] }");
        ExtensionAsset high = ext("a-high",
                "{ \"Target\":{\"Station\":\"s\"}, \"Priority\":5, \"Steps\":[ { \"Anchor\":{\"After\":\"base1\"}, "
                        + "\"Insert\":[ {\"Id\":\"high\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"));
        List<StationStep> merged = ExtensionCatalog.mergeSteps(base, "work", ExtensionAsset.sortedForApply(List.of(high, low)));
        assertEquals(List.of("base1", "low", "high"), stepIds(merged),
                "priority 0 applies before priority 5 (higher priority applies later)");
    }

    @Test
    void mergeSteps_danglingAnchor_degradesToAtEnd() throws Exception {
        ExtensionAsset e = ext("e",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { \"Anchor\":{\"After\":\"nope\"}, "
                        + "\"Insert\":[ {\"Id\":\"tail\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"));
        List<StationStep> merged = ExtensionCatalog.mergeSteps(base, "work", ExtensionAsset.sortedForApply(List.of(e)));
        assertEquals(List.of("base1", "tail"), stepIds(merged), "a dangling After degrades to AtEnd");
    }

    @Test
    void mergeSteps_actionFilter_onlyInsertsIntoTheMatchingActionProgram() throws Exception {
        ExtensionAsset e = ext("e",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { \"Action\":\"other\", \"Anchor\":{\"AtEnd\":true}, "
                        + "\"Insert\":[ {\"Id\":\"x\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"));
        List<StationStep> merged = ExtensionCatalog.mergeSteps(base, "work", ExtensionAsset.sortedForApply(List.of(e)));
        assertEquals(List.of("base1"), stepIds(merged), "an insertion targeting a different action is skipped");
    }

    // ==================== mergeXp / mergeLoot append determinism ====================

    @Test
    void mergeXp_appendsInApplyOrder_baseFirst() throws Exception {
        StationAsset.WorkXp[] base = {StationAsset.WorkXp.of("WOODCUTTING", 5.0)};
        ExtensionAsset a = ext("a", "{ \"Target\":{\"Station\":\"s\"}, \"Xp\":[ {\"Skill\":\"COOKING\",\"PerCycle\":3} ] }");
        ExtensionAsset b = ext("b", "{ \"Target\":{\"Station\":\"s\"}, \"Xp\":[ {\"Skill\":\"MINING\",\"PerCycle\":1} ] }");
        StationAsset.WorkXp[] merged = ExtensionCatalog.mergeXp(base, ExtensionAsset.sortedForApply(List.of(b, a)));
        assertEquals(3, merged.length);
        assertEquals("WOODCUTTING", merged[0].getSkill());
        assertEquals("COOKING", merged[1].getSkill(), "a-ext before b-ext by id tie-break");
        assertEquals("MINING", merged[2].getSkill());
    }

    @Test
    void mergeLoot_unionsLootablesAndRolls() throws Exception {
        com.ziggfreed.rpgstations.asset.LootRef base =
                com.ziggfreed.rpgstations.asset.LootRef.of(new String[]{"sawmillfinds"}, null);
        ExtensionAsset a = ext("a", "{ \"Target\":{\"Station\":\"s\"}, \"Loot\":{ \"Lootables\":[\"sawmillluck\"] } }");
        com.ziggfreed.rpgstations.asset.LootRef merged =
                ExtensionCatalog.mergeLoot(base, ExtensionAsset.sortedForApply(List.of(a)));
        assertEquals(List.of("sawmillfinds", "sawmillluck"), List.of(merged.getLootables()));
    }
}
