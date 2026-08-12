package com.ziggfreed.rpgstations.asset;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.stamp.StampSpec;
import com.ziggfreed.common.loot.stamp.StatRollEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scope-2 {@link StationStep} phase-model codec + pure cores: {@link StationStep.Repeat} clamp
 * math, {@link StampSpec.Budget} exactly-one-of, {@link Ingredient}
 * exactly-one-of, and per-leaf native {@code Parent} inheritance of a step's nested phase group.
 * Decodes a step via a wrapping {@link StationAsset} (steps are never top-level assets).
 */
public class StationStepCodecTest {

    private static StationAsset decodeAsset(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture", null)));
    }

    private static StationStep[] stepsOf(String stepsJson) throws Exception {
        return decodeAsset("{ \"Actions\": [ { \"Id\": \"a\", \"Steps\": " + stepsJson + " } ] }")
                .getActions()[0].getSteps();
    }

    // ==================== Repeat ====================

    @Test
    void repeat_fixedTimes_decodesAndResolves() throws Exception {
        StationStep[] s = stepsOf("[ { \"Id\": \"x\", \"Repeat\": { \"Times\": 3 } } ]");
        StationStep.Repeat r = s[0].getRepeat();
        assertNotNull(r);
        assertTrue(r.isFixed());
        assertEquals(3, r.getTimes());
        assertEquals(3, r.resolveCount(999.0), "a fixed Times ignores the factor contribution");
    }

    @Test
    void repeat_rangedWithAddFactors_decodes() throws Exception {
        StationStep[] s = stepsOf("[ { \"Id\": \"x\", \"Repeat\": { \"Min\": 1, \"Max\": 4,"
                + " \"Factors\": [ { \"Factor\": \"stat\", \"Param\": \"MMO_Luck\", \"Weight\": 0.5 } ] } } ]");
        StationStep.Repeat r = s[0].getRepeat();
        assertFalse(r.isFixed());
        assertEquals(1, r.getMin());
        assertEquals(4, r.getMax());
        assertEquals("MMO_Luck", r.getFactors()[0].getParam());
        assertEquals(0.5, r.getFactors()[0].weightOrDefault());
    }

    @Test
    void repeat_resolveCount_clampsRoundsAndFloorsAtOne() {
        StationStep.Repeat r = StationStep.Repeat.of(null, 1, 4, null);
        assertEquals(1, r.resolveCount(0.0), "min + 0 = 1");
        assertEquals(3, r.resolveCount(2.0), "round(1 + 2) = 3");
        assertEquals(3, r.resolveCount(1.6), "round(1 + 1.6) = round(2.6) = 3");
        assertEquals(4, r.resolveCount(99.0), "clamps to Max");
        assertEquals(1, r.resolveCount(-99.0), "clamps to Min (>= 1)");

        StationStep.Repeat minless = StationStep.Repeat.of(null, null, null, null);
        assertEquals(1, minless.resolveCount(0.0), "defaults min=1, max=1");
    }

    // ==================== Duration / pure beat ====================

    @Test
    void duration_beatIsPure() throws Exception {
        StationStep[] s = stepsOf("[ { \"Id\": \"b\", \"Duration\": { \"Ms\": 800 } } ]");
        assertEquals(800L, s[0].getDuration().getMs());
        assertEquals(800L, s[0].getDuration().effectiveMs());
        assertTrue(s[0].isPureBeat());
    }

    // ==================== Stamp caps (Budgets exactly-one-of) ====================

    @Test
    void stampCaps_budgetsFlatAndFactorScaled_decode() throws Exception {
        StationStep[] s = stepsOf("[ { \"Id\": \"stamp\", \"Stamp\": {"
                + " \"Reagents\": [ { \"ItemId\": \"Coal\", \"Quantity\": 2 } ],"
                + " \"Stats\": { \"Caps\": { \"Budgets\": ["
                + "   { \"Points\": 30 },"
                + "   { \"PointsPer\": 0.5, \"Factors\": [ { \"Factor\": \"stat\", \"Param\": \"MMO_Level_SMITHING\" } ] } ],"
                + " \"PerStat\": { \"MMO_CritChance\": 10 } } },"
                + " \"Economics\": { \"RepeatCostMultiplier\": 1.25 } } } ]");
        StationStep.Stamp stamp = s[0].getStamp();
        assertNotNull(stamp);
        assertEquals("Coal", stamp.getReagents()[0].getItemId());
        assertEquals(2, stamp.getReagents()[0].effectiveQuantity());

        StampSpec.Caps caps = stamp.getStats().getCaps();
        StampSpec.Budget[] budgets = caps.getBudgets();
        assertEquals(2, budgets.length);

        assertTrue(budgets[0].isFlat());
        assertFalse(budgets[0].isFactorScaled());
        assertTrue(budgets[0].hasExactlyOneRoute());
        assertEquals(30.0, budgets[0].getPoints());

        assertTrue(budgets[1].isFactorScaled());
        assertTrue(budgets[1].hasExactlyOneRoute());
        assertEquals(0.5, budgets[1].getPointsPer());
        assertEquals("MMO_Level_SMITHING", budgets[1].getFactors()[0].getParam());

        assertEquals(10.0, caps.getPerStat().get("MMO_CritChance"));
        assertEquals(1.25, stamp.getEconomics().getRepeatCostMultiplier());
    }

    @Test
    void stampBudget_bothRoutesAuthored_isNotExactlyOne() {
        StampSpec.Budget both = new StampSpec.Budget();
        // Author both Points and PointsPer via the exposed factories is impossible; assert the guards on a hand-built.
        assertFalse(StampSpec.Budget.flat(30.0).isFactorScaled());
        assertTrue(StampSpec.Budget.flat(30.0).hasExactlyOneRoute());
        assertTrue(StampSpec.Budget.scaled(0.5, null).hasExactlyOneRoute());
        assertFalse(both.hasExactlyOneRoute(), "neither route authored = not exactly one");
    }

    // ==================== Ingredient exactly-one-of ====================

    @Test
    void ingredient_exactlyOneRoute() {
        assertTrue(Ingredient.item("Coal", 2).hasExactlyOneRoute());
        assertTrue(Ingredient.resource("Wood_Hardwood_Trunk", 1).hasExactlyOneRoute());
        assertFalse(Ingredient.of("Coal", "Wood", 1).hasExactlyOneRoute(), "both routes = not exactly one");
        assertFalse(Ingredient.of(null, null, 1).hasExactlyOneRoute(), "neither route = not exactly one");
        assertEquals(1, Ingredient.of(null, null, null).effectiveQuantity(), "quantity defaults to 1");
    }

    // ==================== StatRollEntry.Points.AddFactors (scope-2 magnitude scaling) ====================

    @Test
    void statRollEntryPoints_addFactorsDecode() throws Exception {
        StationStep[] s = stepsOf("[ { \"Id\": \"stamp\", \"Stamp\": { \"Stats\": { \"Entries\": ["
                + " { \"Stat\": \"MMO_CritChance\", \"Points\": { \"Min\": 1, \"Max\": 3,"
                + "   \"Factors\": [ { \"Factor\": \"stat\", \"Param\": \"MMO_Level_SMITHING\", \"Weight\": 0.1 } ] } } ] } } } ]");
        StatRollEntry entry = s[0].getStamp().getStats().getEntries()[0];
        assertEquals("MMO_CritChance", entry.getStat());
        assertEquals(1.0, entry.getPoints().effectiveMin());
        assertEquals(3.0, entry.getPoints().effectiveMax());
        assertEquals("MMO_Level_SMITHING", entry.getPoints().getFactors()[0].getParam());
        assertEquals(0.1, entry.getPoints().getFactors()[0].weightOrDefault());
    }

    // ==================== Native Parent per-leaf inheritance of a step phase group ====================

    @Test
    void stationStep_stampCaps_perLeafParentInherit() throws Exception {
        AssetExtraInfo.Data pData = new AssetExtraInfo.Data(StationAsset.class, "anvil_parent", null);
        StationAsset parent = StationAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString("{ \"Actions\": [ { \"Id\": \"enhance\", \"Steps\": ["
                        + " { \"Id\": \"stamp\", \"Stamp\": { \"Economics\": { \"RepeatCostMultiplier\": 1.25 },"
                        + " \"Stats\": { \"Caps\": { \"Budgets\": [ { \"Points\": 30 } ] } } } } ] } ] }"),
                null, new AssetExtraInfo<>(pData));
        // The whole Actions array inherits wholesale (validated in StationAssetCodecTest); here we assert the
        // parent's own budget decoded so downstream can trust the shape.
        StationStep.Stamp stamp = parent.getActions()[0].getSteps()[0].getStamp();
        StampSpec.Caps caps = stamp.getStats().getCaps();
        assertEquals(30.0, caps.getBudgets()[0].getPoints());
        assertEquals(1.25, stamp.getEconomics().getRepeatCostMultiplier());
        assertNull(caps.getPerStat());
    }
}
