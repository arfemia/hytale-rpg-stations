package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.StationRecipeDeriver.CraftingCandidate;

/**
 * Exercises the PURE derive-from-native core ({@link StationRecipeDeriver}). Ported verbatim
 * from the MMO's {@code StationRecipeDeriverTest} (RPG Stations extraction leg 2).
 */
public class StationRecipeDeriverTest {

    private static final String[][] WOOD_FAMILIES = {
            {"Wood_Hardwood_Trunk", "Wood_Hardwood_Planks"},
            {"Wood_Lightwood_Trunk", "Wood_Lightwood_Planks"},
            {"Wood_Softwood_Trunk", "Wood_Softwood_Planks"},
            {"Wood_Darkwood_Trunk", "Wood_Darkwood_Planks"},
            {"Wood_Blackwood_Trunk", "Wood_Blackwood_Planks"},
            {"Wood_Greenwood_Trunk", "Wood_Greenwood_Planks"},
            {"Wood_Redwood_Trunk", "Wood_Redwood_Planks"},
            {"Wood_Tropicalwood_Trunk", "Wood_Tropicalwood_Planks"},
            {"Wood_Drywood_Trunk", "Wood_Drywood_Planks"},
            {"Wood_Deadwood_Trunk", "Wood_Deadwood_Planks"},
            {"Wood_Goldenwood_Trunk", "Wood_Goldenwood_Planks"},
    };

    private static CraftingCandidate resourceCandidate(String itemId, String category, String resourceTypeId, int qty) {
        return new CraftingCandidate(itemId, List.of(category),
                List.of(StationAsset.Ingredient.resource(resourceTypeId, qty)));
    }

    private static StationAsset.FromCrafting spec(Integer outputPerInput, String... categories) {
        return StationAsset.FromCrafting.of(categories, outputPerInput);
    }

    // ==================== Match + yield ====================

    @Test
    void omittedOutputPerInput_yieldsTheNativeOnePerCraft() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(null, "WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        StationAsset.Conversion c = derived.get(0);
        assertEquals("Wood_Hardwood_Trunk", c.getInput().getResourceTypeId());
        assertNull(c.getInput().getItemId());
        assertEquals(1, c.getInput().getQuantity());
        assertEquals("Wood_Hardwood_Planks", c.getOutput().getItemId());
        assertEquals(1, c.getOutput().getQuantity());
    }

    @Test
    void explicitOutputPerInput_scalesTheYield() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(3, "WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        assertEquals(1, derived.get(0).getInput().getQuantity());
        assertEquals(3, derived.get(0).getOutput().getQuantity());
    }

    @Test
    void exactItemInput_isDerivedVerbatim() {
        CraftingCandidate itemInput = new CraftingCandidate("Some_Modded_Plank", List.of("WoodPlanks"),
                List.of(StationAsset.Ingredient.item("Some_Modded_Log", 2)));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(null, "WoodPlanks"), List.of(itemInput));
        assertEquals(1, derived.size());
        assertEquals("Some_Modded_Log", derived.get(0).getInput().getItemId());
        assertNull(derived.get(0).getInput().getResourceTypeId());
        assertEquals(2, derived.get(0).getInput().getQuantity());
    }

    // ==================== Skips ====================

    @Test
    void skipsNonMatchingCategoryAndBadInputCounts() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1),
                resourceCandidate("Stone_Bricks", "StoneBricks", "Rock", 1),
                new CraftingCandidate("Bench_Builders", List.of("WoodPlanks"), List.of(
                        StationAsset.Ingredient.resource("Wood_Trunk", 6),
                        StationAsset.Ingredient.item("Rock", 3))),
                new CraftingCandidate("Empty_Recipe", List.of("WoodPlanks"), List.of()));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(null, "WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        assertEquals("Wood_Hardwood_Planks", derived.get(0).getOutput().getItemId());
    }

    @Test
    void categoryMatchIsCaseInsensitive() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "woodplanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(null, "WoodPlanks"), candidates);
        assertEquals(1, derived.size());
    }

    // ==================== Determinism ====================

    @Test
    void derivationIsSortedByOutputItemId() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Softwood_Planks", "WoodPlanks", "Wood_Softwood_Trunk", 1),
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1),
                resourceCandidate("Wood_Blackwood_Planks", "WoodPlanks", "Wood_Blackwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(null, "WoodPlanks"), candidates);
        assertEquals(List.of("Wood_Blackwood_Planks", "Wood_Hardwood_Planks", "Wood_Softwood_Planks"),
                derived.stream().map(c -> c.getOutput().getItemId()).toList());
    }

    // ==================== resolve() precedence ====================

    @Test
    void resolve_authoredFirstAndOverridesDerivedByInputRef() {
        StationAsset.Conversion authored = StationAsset.Conversion.of(
                StationAsset.Ingredient.resource("Wood_Hardwood_Trunk", 1),
                StationAsset.Ingredient.item("Custom_Beam", 4));
        StationAsset.Recipe recipe = StationAsset.Recipe.of(
                new StationAsset.Conversion[]{authored},
                spec(null, "WoodPlanks"));
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1),
                resourceCandidate("Wood_Softwood_Planks", "WoodPlanks", "Wood_Softwood_Trunk", 1));
        StationAsset.Conversion[] resolved = StationRecipeDeriver.resolve(recipe, candidates);
        assertEquals(2, resolved.length);
        assertEquals("Custom_Beam", resolved[0].getOutput().getItemId());
        assertEquals("Wood_Softwood_Planks", resolved[1].getOutput().getItemId());
    }

    @Test
    void resolve_fromCraftingOnly_derivesEverything() {
        StationAsset.Recipe recipe = StationAsset.Recipe.of(null, spec(null, "WoodPlanks"));
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1),
                resourceCandidate("Wood_Softwood_Planks", "WoodPlanks", "Wood_Softwood_Trunk", 1));
        assertEquals(2, StationRecipeDeriver.resolve(recipe, candidates).length);
    }

    // ==================== The sawmill's 11 wood families ====================

    @Test
    void reproducesTheElevenWoodPlankFamiliesAtYieldOne() {
        List<CraftingCandidate> candidates = new ArrayList<>();
        for (String[] fam : WOOD_FAMILIES) {
            candidates.add(resourceCandidate(fam[1], "WoodPlanks", fam[0], 1));
        }
        candidates.add(new CraftingCandidate("Bench_Builders", List.of("Tools", "Workbench_Crafting"), List.of(
                StationAsset.Ingredient.resource("Wood_Trunk", 6),
                StationAsset.Ingredient.item("Rock", 3))));

        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec(null, "WoodPlanks"), candidates);
        assertEquals(11, derived.size());
        for (StationAsset.Conversion c : derived) {
            assertEquals(1, c.getInput().getQuantity());
            assertEquals(1, c.getOutput().getQuantity());
        }
        StationAsset.Conversion hardwood = derived.stream()
                .filter(c -> "Wood_Hardwood_Planks".equals(c.getOutput().getItemId()))
                .findFirst().orElseThrow();
        assertEquals("Wood_Hardwood_Trunk", hardwood.getInput().getResourceTypeId());
    }
}
