package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.LootableConfig;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.stamp.RollPoolAsset;
import com.ziggfreed.common.loot.stamp.RollPoolConfig;
import com.ziggfreed.common.loot.stamp.StampCapEngine;
import com.ziggfreed.common.loot.stamp.StampInspection;
import com.ziggfreed.common.loot.stamp.StampPlan;
import com.ziggfreed.common.loot.stamp.StampSpec;
import com.ziggfreed.common.loot.stamp.StatRoll;
import com.ziggfreed.common.loot.stamp.StatRollEntry;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Contribution;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.StationLootEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Every resolved roll's first granted droplist id, in resolution order. */
    private static List<String> dropListIds(List<Roll> rolls) {
        List<String> ids = new ArrayList<>();
        for (Roll r : rolls) {
            ids.add(r.getGrants().getDropLists()[0]);
        }
        return ids;
    }

    // ==================== mergeSteps: co-anchored insertion determinism (m2) ====================

    @Test
    void mergeSteps_twoExtensionsCoAnchoredOnOneStep_applyInPriorityThenIdOrder() throws Exception {
        // Both insert AFTER "base1"; equal priority 0 -> id lex tie-break: "a-ext" before "b-ext".
        ExtensionAsset a = ext("a-ext",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { "
                        + "\"Anchor\":{\"After\":\"base1\"}, \"Insert\":[ {\"Id\":\"a1\"} ] } ] }");
        ExtensionAsset b = ext("b-ext",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { "
                        + "\"Anchor\":{\"After\":\"base1\"}, \"Insert\":[ {\"Id\":\"b1\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"), StationStep.of("base2"));

        // Feed the two extensions in BOTH encounter orders; sortedForApply must collapse both to
        // the same deterministic result.
        List<StationStep> ab = ExtensionCatalog.mergeSteps(base, ExtensionAsset.sortedForApply(List.of(a, b)));
        List<StationStep> ba = ExtensionCatalog.mergeSteps(base, ExtensionAsset.sortedForApply(List.of(b, a)));

        assertEquals(List.of("base1", "a1", "b1", "base2"), stepIds(ab));
        assertEquals(stepIds(ab), stepIds(ba), "the merged result is identical regardless of fold encounter order");
    }

    @Test
    void mergeSteps_coAnchoredWithMixedCasing_stillLandInApplyOrder() throws Exception {
        // The anchor MATCH is case-insensitive, so both of these anchor on the same real step -
        // and the co-anchor ledger has to agree with that, or the second block lands in FRONT of
        // the first and APPLY_ORDER inverts purely on how the two authors capitalized an id.
        ExtensionAsset a = ext("a-ext",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { "
                        + "\"Anchor\":{\"After\":\"base1\"}, \"Insert\":[ {\"Id\":\"a1\"} ] } ] }");
        ExtensionAsset b = ext("b-ext",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { "
                        + "\"Anchor\":{\"After\":\"Base1\"}, \"Insert\":[ {\"Id\":\"b1\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"), StationStep.of("base2"));

        List<StationStep> ab = ExtensionCatalog.mergeSteps(base, ExtensionAsset.sortedForApply(List.of(a, b)));
        List<StationStep> ba = ExtensionCatalog.mergeSteps(base, ExtensionAsset.sortedForApply(List.of(b, a)));

        assertEquals(List.of("base1", "a1", "b1", "base2"), stepIds(ab),
                "a-ext applies first, so its block stays ahead of b-ext's despite the anchor's casing");
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
        List<StationStep> merged = ExtensionCatalog.mergeSteps(base, ExtensionAsset.sortedForApply(List.of(high, low)));
        assertEquals(List.of("base1", "low", "high"), stepIds(merged),
                "priority 0 applies before priority 5 (higher priority applies later)");
    }

    @Test
    void mergeSteps_danglingAnchor_degradesToAtEnd() throws Exception {
        ExtensionAsset e = ext("e",
                "{ \"Target\":{\"Station\":\"s\"}, \"Steps\":[ { \"Anchor\":{\"After\":\"nope\"}, "
                        + "\"Insert\":[ {\"Id\":\"tail\"} ] } ] }");
        List<StationStep> base = List.of(StationStep.of("base1"));
        List<StationStep> merged = ExtensionCatalog.mergeSteps(base, ExtensionAsset.sortedForApply(List.of(e)));
        assertEquals(List.of("base1", "tail"), stepIds(merged), "a dangling After degrades to AtEnd");
    }

    // ============ mergeContributions / mergeLoot append determinism ============

    @Test
    void mergeContributions_appendsInApplyOrder_baseFirst() throws Exception {
        Contribution[] base = {Contribution.of("yourmod:test", "ALPHA", 5.0)};
        ExtensionAsset a = ext("a", "{ \"Target\":{\"Station\":\"s\"}, \"PerCycleContributions\":"
                + "[ {\"Channel\":\"yourmod:test\",\"Param\":\"BETA\",\"Amount\":3} ] }");
        ExtensionAsset b = ext("b", "{ \"Target\":{\"Station\":\"s\"}, \"PerCycleContributions\":"
                + "[ {\"Channel\":\"yourmod:test\",\"Param\":\"GAMMA\",\"Amount\":1} ] }");
        Contribution[] merged = ExtensionCatalog.mergeContributions(base,
                ExtensionAsset.sortedForApply(List.of(b, a)));
        assertEquals(3, merged.length);
        assertEquals("ALPHA", merged[0].getParam());
        assertEquals("BETA", merged[1].getParam(), "a-ext before b-ext by id tie-break");
        assertEquals("GAMMA", merged[2].getParam());
    }

    @Test
    void mergeBonus_unionsLootablesAndRolls() throws Exception {
        LootRef base =
                LootRef.of(new String[]{"sawmillfinds"}, null);
        ExtensionAsset a = ext("a", "{ \"Target\":{\"Action\":\"mill\"}, \"Bonus\":{ \"Lootables\":[\"sawmillluck\"] } }");
        LootRef merged =
                ExtensionCatalog.mergeLoot(base, ExtensionAsset.sortedForApply(List.of(a)));
        assertEquals(List.of("sawmillfinds", "sawmillluck"), List.of(merged.getLootables()));
    }

    // ==================== mergeAnchors: keyed, base wins ====================

    @Test
    void mergeAnchors_appendsNewKeys_baseWinsACollision() throws Exception {
        ExtensionAsset e = ext("e", "{ \"Target\":{\"Action\":\"prep\"}, \"Anchors\":{"
                + " \"Fire\":{\"Station\":\"ExtensionFire\"}, \"Well\":{\"Station\":\"FixtureWell\"} } }");
        Map<String, ActionDef.Anchor> base = new LinkedHashMap<>();
        base.put("Fire", ActionDef.Anchor.of("BaseFire", null));
        Map<String, ActionDef.Anchor> merged =
                ExtensionCatalog.mergeAnchors(base, ExtensionAsset.sortedForApply(List.of(e)));

        assertEquals(List.of("Fire", "Well"), List.copyOf(merged.keySet()),
                "base keys keep their declaration order, new keys append");
        assertEquals("BaseFire", merged.get("Fire").getStation(),
                "the base always wins a key collision - an extension declares NEW anchors only");
        assertEquals("FixtureWell", merged.get("Well").getStation());
    }

    @Test
    void mergeAnchors_collisionIsCaseInsensitive() throws Exception {
        ExtensionAsset e = ext("e", "{ \"Target\":{\"Action\":\"prep\"},"
                + " \"Anchors\":{ \"FIRE\":{\"Station\":\"ExtensionFire\"} } }");
        Map<String, ActionDef.Anchor> base = new LinkedHashMap<>();
        base.put("Fire", ActionDef.Anchor.of("BaseFire", null));
        Map<String, ActionDef.Anchor> merged =
                ExtensionCatalog.mergeAnchors(base, ExtensionAsset.sortedForApply(List.of(e)));
        assertEquals(1, merged.size(), "an extension cannot shadow a base anchor by re-casing its key");
        assertEquals("BaseFire", merged.get("Fire").getStation());
    }

    @Test
    void mergeAnchors_laterExtensionInApplyOrderWinsAContestedKey() throws Exception {
        ExtensionAsset a = ext("a-ext", "{ \"Target\":{\"Action\":\"prep\"},"
                + " \"Anchors\":{ \"Well\":{\"Station\":\"FromA\"} } }");
        ExtensionAsset b = ext("b-ext", "{ \"Target\":{\"Action\":\"prep\"},"
                + " \"Anchors\":{ \"Well\":{\"Station\":\"FromB\"} } }");
        Map<String, ActionDef.Anchor> ab =
                ExtensionCatalog.mergeAnchors(null, ExtensionAsset.sortedForApply(List.of(a, b)));
        Map<String, ActionDef.Anchor> ba =
                ExtensionCatalog.mergeAnchors(null, ExtensionAsset.sortedForApply(List.of(b, a)));
        assertEquals("FromB", ab.get("Well").getStation(),
                "the later extension in APPLY_ORDER wins the contested key's value");
        assertEquals(ab.get("Well").getStation(), ba.get("Well").getStation(),
                "identical for a given input set regardless of fold encounter order");
    }

    @Test
    void mergeAnchors_laterWinnerKeepsTheFirstClaimsSpellingAndPosition() throws Exception {
        ExtensionAsset a = ext("a-ext", "{ \"Target\":{\"Action\":\"prep\"},"
                + " \"Anchors\":{ \"Well\":{\"Station\":\"FromA\"}, \"Pond\":{\"Station\":\"PondA\"} } }");
        ExtensionAsset b = ext("b-ext", "{ \"Target\":{\"Action\":\"prep\"},"
                + " \"Anchors\":{ \"WELL\":{\"Station\":\"FromB\"} } }");
        Map<String, ActionDef.Anchor> merged =
                ExtensionCatalog.mergeAnchors(null, ExtensionAsset.sortedForApply(List.of(a, b)));
        assertEquals(List.of("Well", "Pond"), List.copyOf(merged.keySet()),
                "the first claim fixes the slot's spelling and map position; only the value is replaced");
        assertEquals("FromB", merged.get("Well").getStation());
        assertEquals("PondA", merged.get("Pond").getStation());
    }

    @Test
    void mergeActions_laterExtensionInApplyOrderWinsAContestedId_inTheFirstClaimsSlot() throws Exception {
        ExtensionAsset a = ext("a-ext", "{ \"Target\":{\"Station\":\"mill\"}, \"Actions\":["
                + " {\"Id\":\"Season\",\"Label\":\"from.a\"}, {\"Id\":\"Rinse\",\"Label\":\"rinse.a\"} ] }");
        ExtensionAsset b = ext("b-ext", "{ \"Target\":{\"Station\":\"mill\"}, \"Actions\":["
                + " {\"Id\":\"season\",\"Label\":\"from.b\"} ] }");
        ActionDef[] baseArr = new ActionDef[0];
        ActionDef[] merged =
                ExtensionCatalog.mergeActions(baseArr, ExtensionAsset.sortedForApply(List.of(b, a)));
        assertEquals(2, merged.length, "one entry per distinct id");
        assertEquals("from.b", merged[0].getLabel(),
                "the later extension's entry replaces the earlier claimant's, in the first claim's slot");
        assertEquals("rinse.a", merged[1].getLabel());
    }

    // ==================== Live-catalog wiring: each payload reaches a real read path ====================

    /**
     * The gap that let three decoded payloads ship inert: every case above drives a PURE core
     * directly, which passes just as happily when nothing calls it. These fold into the live
     * {@link ExtensionCatalog} and assert through the engine read that actually consumes each
     * payload, so an unwired entry point fails here.
     */
    private static StationAsset stationWith(String id, ActionDef... actions) {
        return StationAsset.of(id, StationAsset.Identity.of(
                "rpgstations.station." + id + ".name", "rpgstations.station." + id + ".desc",
                "Fixture_Icon"), actions);
    }

    private static void fold(ExtensionAsset... exts) {
        Map<String, ExtensionAsset> layer = new LinkedHashMap<>();
        for (ExtensionAsset e : exts) {
            layer.put(e.getId(), e);
        }
        ExtensionCatalog.getInstance().fold(layer, true);
    }

    @AfterEach
    void clearFoldedExtensions() {
        ExtensionCatalog.getInstance().fold(Map.of(), true);
        StationCatalog.getInstance().fold(Map.of(), true);
        // The shared loot stores are keyed by id and every fixture here uses its own,
        // so a later merge of the same id replaces it outright and nothing leaks.
    }

    @Test
    void live_anchorsReachTheResolvedAction() throws Exception {
        StationAsset station = stationWith("fixtureprep", ActionDef.of("Prep")
                .withSteps(new StationStep[] {StationStep.of("Beat")})
                .withAnchors(Map.of("Fire", ActionDef.Anchor.of("FixtureFire", null))));
        fold(ext("anchors-ext", "{ \"Target\":{\"Action\":\"Prep\"},"
                + " \"Anchors\":{ \"Well\":{\"Station\":\"FixtureWell\"} } }"));

        Map<String, ActionDef.Anchor> anchors = ActionResolver.resolve(station, "Prep").getAnchors();
        assertNotNull(anchors);
        assertTrue(anchors.containsKey("Fire"), "the base anchor survives");
        assertNotNull(anchors.get("Well"), "the extension's NEW anchor reaches the resolved action");
    }

    @Test
    void live_stepInsertionsReachTheDispatchedProgram() throws Exception {
        StationAsset station = stationWith("fixtureritual", ActionDef.of("Enhance")
                .withSteps(new StationStep[] {StationStep.of("Beat1"), StationStep.of("Beat2")}));
        fold(ext("season-ext", "{ \"Target\":{\"Action\":\"Enhance\"}, \"Steps\":[ {"
                + " \"Anchor\":{\"After\":\"Beat1\"}, \"Insert\":[ {\"Id\":\"Extra\"} ] } ] }"));

        List<StationStep> program = StationService.effectiveProgramSteps(station,
                ActionResolver.resolve(station, "Enhance"));
        assertEquals(List.of("Beat1", "Extra", "Beat2"), stepIds(program));
    }

    @Test
    void live_stepInsertionsCannotConjureAProgram() throws Exception {
        // The action authors no Steps: it runs the recipe-driven convert loop, and an insertion
        // must never flip it into a step program (that would skip its conversion check entirely).
        StationAsset station = stationWith("fixtureconvert", ActionDef.of("Mill")
                .withRecipe(StationAsset.Recipe.of(new StationAsset.Conversion[] {
                        StationAsset.Conversion.of(Ingredient.item("Fixture_Trunk", 1),
                                Ingredient.item("Fixture_Plank", 2))})));
        fold(ext("season-ext", "{ \"Target\":{\"Action\":\"Mill\"}, \"Steps\":[ {"
                + " \"Anchor\":{\"AtEnd\":true}, \"Insert\":[ {\"Id\":\"Extra\"} ] } ] }"));

        assertTrue(StationService.effectiveProgramSteps(station,
                        ActionResolver.resolve(station, "Mill")).isEmpty(),
                "an action authoring no Steps has no program for an insertion to land in");
    }

    /**
     * The FactorContext half of the PerCycleContributions payload: a factor provider reading
     * {@code contributionParams(channel)} has to see the channels a completed cycle will actually
     * post on, extension-appended ones included - the cycle event and the api station view both
     * merged them, while this seam read the raw base and answered a shorter list.
     */
    @Test
    void live_contributionParamsSeeAnExtensionAppendedParam() throws Exception {
        StationAsset.Work work = StationAsset.Work.of(5000L, 600000L, 1.5,
                new Contribution[] {Contribution.of("yourmod:test", "ALPHA", 8.0)});
        fold(ext("params-ext", "{ \"Target\":{\"Action\":\"Mill\"}, \"PerCycleContributions\":[ {"
                + " \"Channel\":\"yourmod:test\", \"Param\":\"BETA\", \"Amount\":3 } ] }"));

        Map<String, List<String>> params = StationService.contributionParams("fixturemill", "Mill", work);
        assertEquals(List.of("ALPHA", "BETA"), params.get("yourmod:test"),
                "the extension's Param is visible to a no-Param factor read, beside the action's own");
    }

    @Test
    void live_contributionParamsWithNoExtension_areTheActionsOwn() {
        StationAsset.Work work = StationAsset.Work.of(5000L, 600000L, 1.5,
                new Contribution[] {Contribution.of("yourmod:test", "ALPHA", 8.0)});
        Map<String, List<String>> params = StationService.contributionParams("fixturemill", "Mill", work);
        assertEquals(List.of("ALPHA"), params.get("yourmod:test"), "the zero-extension path is unchanged");
    }

    @Test
    void live_conversionsReachTheResolvedConversionSet() throws Exception {
        StationAsset.Recipe recipe = StationAsset.Recipe.of(new StationAsset.Conversion[] {
                StationAsset.Conversion.of(Ingredient.item("Fixture_Trunk", 1),
                        Ingredient.item("Fixture_Plank", 2))});
        StationAsset station = stationWith("fixturemill", ActionDef.of("Mill").withRecipe(recipe));
        StationCatalog.getInstance().fold(Map.of("fixturemill", station), true);
        fold(ext("extra-conversion", "{ \"Target\":{\"Action\":\"Mill\"}, \"Conversions\":[ {"
                + " \"Input\":[{\"ItemId\":\"Fixture_Bark\",\"Quantity\":1}],"
                + " \"Output\":[{\"ItemId\":\"Fixture_Mulch\",\"Quantity\":1}] } ] }"));

        StationAsset.Conversion[] resolved =
                StationCatalog.getInstance().resolvedConversions(station, "Mill", recipe);
        List<String> outputs = new ArrayList<>();
        for (StationAsset.Conversion c : resolved) {
            outputs.add(c.getOutput()[0].getItemId());
        }
        assertEquals(List.of("Fixture_Plank", "Fixture_Mulch"), outputs,
                "the appended conversion lands AFTER the base recipe's own");
    }

    @Test
    void live_lootableRollsReachEveryReferenceOfThatTable() throws Exception {
        // The Lootable payload applies at the table READ, so a referencing LootRef sees the base
        // table's rolls plus the extension's appended ones, in that order.
        LootableConfig.getInstance().mergePackLayer(Map.of("fixturefinds",
                LootableAsset.of("fixturefinds", new Roll[] {
                        Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null, LootGrants.ofDropList("Fixture_Base_Drops"), null)})));
        fold(ext("finds-ext", "{ \"Target\":{\"Lootable\":\"FixtureFinds\"}, \"Rolls\":[ {"
                + " \"Trigger\":\"Cycle\", \"Grants\":{\"DropLists\":[\"Fixture_Extra_Drops\"]} } ] }"));

        List<Roll> rolls = StationLootEngine.resolve(LootRef.of(new String[] {"FixtureFinds"}, null)).rolls();
        assertEquals(List.of("Fixture_Base_Drops", "Fixture_Extra_Drops"), dropListIds(rolls),
                "the appended roll lands AFTER the table's own");
    }

    @Test
    void live_rollPoolEntriesReachAStampStepsCandidateSet() throws Exception {
        // The RollPool payload applies where a Stamp step reads its Pool, so an appended entry is a
        // genuine candidate. Both entries are Always, so the roll is deterministic without an RNG.
        RollPoolConfig.getInstance().mergePackLayer(Map.of("fixturepool",
                RollPoolAsset.of("fixturepool", new StatRollEntry[] {
                        StatRollEntry.of("Fixture_Base_Stat", StatRollEntry.Points.of(2.0, 2.0, null), null, true)})));
        fold(ext("pool-ext", "{ \"Target\":{\"RollPool\":\"FixturePool\"}, \"Entries\":[ {"
                + " \"Stat\":\"Fixture_Added_Stat\", \"Points\":{\"Min\":3,\"Max\":3}, \"Always\":true } ] }"));

        StampPlan plan = StampCapEngine.resolve(
                StationStepHandlers.StampHandler.withExtendedEntries(
                        StampSpec.of("FixturePool", null, null, false, null)),
                StampInspection.empty(), (id, param) -> null, () -> 0.0);
        List<String> stats = new ArrayList<>();
        for (StatRoll r : plan.entries()) {
            stats.add(r.statId());
        }
        assertEquals(List.of("Fixture_Base_Stat", "Fixture_Added_Stat"), stats,
                "the extension's entry is a candidate beside the pool's own");
    }

    // ==================== The station-SCOPED Action target ====================

    @Test
    void live_scopedExtensionAppliesOnItsOwnStationOnly() throws Exception {
        // Two stations authoring the SAME inline action id - the case the scoped form exists for.
        StationAsset mine = stationWith("fixturemine", ActionDef.of("Work")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        StationAsset yours = stationWith("fixtureyours", ActionDef.of("Work")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        fold(ext("scoped-ext", "{ \"Target\":{\"Station\":\"FixtureMine\",\"Action\":\"Work\"},"
                + " \"Anchors\":{ \"Well\":{\"Station\":\"FixtureWell\"} } }"));

        assertNotNull(ActionResolver.resolve(mine, "Work").getAnchors().get("Well"),
                "the scoped extension applies on the station it names");
        assertNull(ActionResolver.resolve(yours, "Work").getAnchors(),
                "another station's identically-named action is untouched");
    }

    @Test
    void live_bareExtensionStillAppliesOnEveryStationSharingTheId() throws Exception {
        StationAsset mine = stationWith("fixturemine", ActionDef.of("Work")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        StationAsset yours = stationWith("fixtureyours", ActionDef.of("Work")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        fold(ext("bare-ext", "{ \"Target\":{\"Action\":\"Work\"},"
                + " \"Anchors\":{ \"Well\":{\"Station\":\"FixtureWell\"} } }"));

        assertNotNull(ActionResolver.resolve(mine, "Work").getAnchors().get("Well"));
        assertNotNull(ActionResolver.resolve(yours, "Work").getAnchors().get("Well"),
                "an unscoped Action target reaches every station resolving that id");
    }

    @Test
    void live_scopedAndBareBothApply_andTheCacheKeepsThemApart() throws Exception {
        StationAsset mine = stationWith("fixturemine", ActionDef.of("Work")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        StationAsset yours = stationWith("fixtureyours", ActionDef.of("Work")
                .withSteps(new StationStep[] {StationStep.of("Beat")}));
        fold(ext("a-bare", "{ \"Target\":{\"Action\":\"Work\"}, \"Steps\":[ {"
                        + " \"Anchor\":{\"AtEnd\":true}, \"Insert\":[ {\"Id\":\"Everywhere\"} ] } ] }"),
                ext("b-scoped", "{ \"Target\":{\"Station\":\"FixtureMine\",\"Action\":\"Work\"}, \"Steps\":[ {"
                        + " \"Anchor\":{\"AtEnd\":true}, \"Insert\":[ {\"Id\":\"MineOnly\"} ] } ] }"));

        // Read the SHARED station first so its list is the one warming the cache.
        assertEquals(List.of("Beat", "Everywhere"), stepIds(StationService.effectiveProgramSteps(
                yours, ActionResolver.resolve(yours, "Work"))));
        assertEquals(List.of("Beat", "Everywhere", "MineOnly"), stepIds(StationService.effectiveProgramSteps(
                mine, ActionResolver.resolve(mine, "Work"))),
                "the per-station cache key keeps a warmed unscoped list from starving the scoped one");
    }

    @Test
    void live_extensionFoldInvalidatesACachedConversionSet() throws Exception {
        StationAsset.Recipe recipe = StationAsset.Recipe.of(new StationAsset.Conversion[] {
                StationAsset.Conversion.of(Ingredient.item("Fixture_Trunk", 1),
                        Ingredient.item("Fixture_Plank", 2))});
        StationAsset station = stationWith("fixturemill", ActionDef.of("Mill").withRecipe(recipe));
        StationCatalog.getInstance().fold(Map.of("fixturemill", station), true);
        // Warm the cache BEFORE the extension exists - the boot order this guards against.
        assertEquals(1, StationCatalog.getInstance().resolvedConversions(station, "Mill", recipe).length);

        fold(ext("extra-conversion", "{ \"Target\":{\"Action\":\"Mill\"}, \"Conversions\":[ {"
                + " \"Input\":[{\"ItemId\":\"Fixture_Bark\",\"Quantity\":1}],"
                + " \"Output\":[{\"ItemId\":\"Fixture_Mulch\",\"Quantity\":1}] } ] }"));

        assertEquals(2, StationCatalog.getInstance().resolvedConversions(station, "Mill", recipe).length,
                "folding extensions drops the derived-conversion cache, so a later fold is not lost");
    }
}
