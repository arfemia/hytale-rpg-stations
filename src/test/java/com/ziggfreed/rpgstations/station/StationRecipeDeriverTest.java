package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Ingredient;
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
                List.of(Ingredient.resource(resourceTypeId, qty)));
    }

    private static StationAsset.FromCrafting spec(String... categories) {
        return StationAsset.FromCrafting.of(categories);
    }

    // ==================== Match + yield ====================

    @Test
    void derivedConversion_carriesTheNativeOnePerCraft() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        StationAsset.Conversion c = derived.get(0);
        assertEquals("Wood_Hardwood_Trunk", c.primaryInput().getResourceTypeId());
        assertNull(c.primaryInput().getItemId());
        assertEquals(1, c.primaryInput().getQuantity());
        assertEquals("Wood_Hardwood_Planks", c.primaryOutput().getItemId());
        // Yield retuning is Recipe.Yield's job (per cycle), never the deriver's - see StationYield.
        assertEquals(1, c.primaryOutput().getQuantity());
    }

    @Test
    void exactItemInput_isDerivedVerbatim() {
        CraftingCandidate itemInput = new CraftingCandidate("Some_Modded_Plank", List.of("WoodPlanks"),
                List.of(Ingredient.item("Some_Modded_Log", 2)));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), List.of(itemInput));
        assertEquals(1, derived.size());
        assertEquals("Some_Modded_Log", derived.get(0).primaryInput().getItemId());
        assertNull(derived.get(0).primaryInput().getResourceTypeId());
        assertEquals(2, derived.get(0).primaryInput().getQuantity());
    }

    // ==================== Skips ====================

    @Test
    void skipsNonMatchingCategoryAndInputlessRecipes() {
        // Decision 73: a MULTI-input native recipe now derives (it used to be skipped), so the only
        // structural skips left are a non-matching category and a recipe with no inputs at all.
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1),
                resourceCandidate("Stone_Bricks", "StoneBricks", "Rock", 1),
                new CraftingCandidate("Bench_Builders", List.of("WoodPlanks"), List.of(
                        Ingredient.resource("Wood_Trunk", 6),
                        Ingredient.item("Rock", 3))),
                new CraftingCandidate("Empty_Recipe", List.of("WoodPlanks"), List.of()));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(List.of("Bench_Builders", "Wood_Hardwood_Planks"),
                derived.stream().map(c -> c.primaryOutput().getItemId()).toList());
    }

    @Test
    void multiInputNativeRecipe_derivesEveryInput() {
        // Decision 73: Conversion.Input is an Ingredient[] mirroring native CraftingRecipe.Input,
        // so a "6 trunks + 3 rock" recipe derives with BOTH inputs instead of being dropped.
        List<CraftingCandidate> candidates = List.of(
                new CraftingCandidate("Bench_Builders", List.of("WoodPlanks"), List.of(
                        Ingredient.resource("Wood_Trunk", 6),
                        Ingredient.item("Rock", 3))));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        Ingredient[] inputs = derived.get(0).getInput();
        assertEquals(2, inputs.length);
        assertEquals("Wood_Trunk", inputs[0].getResourceTypeId());
        assertEquals(6, inputs[0].effectiveQuantity());
        assertEquals("Rock", inputs[1].getItemId());
        assertEquals(3, inputs[1].effectiveQuantity());
    }

    @Test
    void tagsRouteInput_isDerivedVerbatim() {
        // The native ItemTag input route (resolved to its tag NAME by the live adapter) derives
        // onto the Ingredient.Tags presence form instead of skipping the candidate.
        CraftingCandidate tagInput = new CraftingCandidate("Food_Stew", List.of("Prepared"),
                List.of(Ingredient.tagged(Map.of("CookingIngredient", new String[0]), 3)));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("Prepared"), List.of(tagInput));
        assertEquals(1, derived.size());
        Ingredient in = derived.get(0).primaryInput();
        assertTrue(in.hasTagsRoute());
        assertTrue(in.getTags().containsKey("CookingIngredient"));
        assertEquals(3, in.effectiveQuantity());
    }

    @Test
    void routeLessNativeInput_skipsTheCandidate_neverDerivesMatchAny() {
        CraftingCandidate broken = new CraftingCandidate("Broken_Recipe", List.of("Prepared"),
                List.of(Ingredient.matchAny(1)));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("Prepared"), List.of(broken));
        assertTrue(derived.isEmpty());
    }

    @Test
    void craftingBenchCategories_deriveLikeProcessingOnes() {
        // A Crafting-type bench's own BenchRequirement.Categories rows (the Cookingbench
        // Prepared/Baked/Ingredients tabs) scope derivation exactly like a Processing bench's:
        // the category route is bench-type-agnostic, and the Types filter still applies on top.
        CraftingCandidate baked = new CraftingCandidate("Food_Bread", List.of("Baked"),
                List.of("Cookingbench"), List.of("Crafting"), 5f,
                List.of(Ingredient.item("Ingredient_Dough", 1), Ingredient.resource("Fuel", 3)));
        List<StationAsset.Conversion> byCategory =
                StationRecipeDeriver.deriveFromCrafting(spec("Baked"), List.of(baked));
        assertEquals(1, byCategory.size());
        assertEquals("Food_Bread", byCategory.get(0).primaryOutput().getItemId());
        assertEquals("Baked", byCategory.get(0).getCategory());
        List<StationAsset.Conversion> byCategoryAndType = StationRecipeDeriver.deriveFromCrafting(
                StationAsset.FromCrafting.of(new String[] {"Baked"}, null,
                        new String[] {StationAsset.FromCrafting.TYPE_CRAFTING}, null),
                List.of(baked));
        assertEquals(1, byCategoryAndType.size());
        List<StationAsset.Conversion> wrongType = StationRecipeDeriver.deriveFromCrafting(
                StationAsset.FromCrafting.of(new String[] {"Baked"}, null,
                        new String[] {StationAsset.FromCrafting.TYPE_PROCESSING}, null),
                List.of(baked));
        assertTrue(wrongType.isEmpty());
    }

    @Test
    void categoryMatchIsCaseInsensitive() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "woodplanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
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
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(List.of("Wood_Blackwood_Planks", "Wood_Hardwood_Planks", "Wood_Softwood_Planks"),
                derived.stream().map(c -> c.primaryOutput().getItemId()).toList());
    }

    // ==================== resolve() precedence ====================

    @Test
    void resolve_authoredFirstAndOverridesDerivedByInputRef() {
        StationAsset.Conversion authored = StationAsset.Conversion.of(
                Ingredient.resource("Wood_Hardwood_Trunk", 1),
                Ingredient.item("Custom_Beam", 4));
        StationAsset.Recipe recipe = StationAsset.Recipe.of(
                new StationAsset.Conversion[]{authored},
                spec("WoodPlanks"));
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1),
                resourceCandidate("Wood_Softwood_Planks", "WoodPlanks", "Wood_Softwood_Trunk", 1));
        StationAsset.Conversion[] resolved = StationRecipeDeriver.resolve(recipe, candidates);
        assertEquals(2, resolved.length);
        assertEquals("Custom_Beam", resolved[0].primaryOutput().getItemId());
        assertEquals("Wood_Softwood_Planks", resolved[1].primaryOutput().getItemId());
    }

    @Test
    void resolve_fromCraftingOnly_derivesEverything() {
        StationAsset.Recipe recipe = StationAsset.Recipe.of(null, spec("WoodPlanks"));
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
                Ingredient.resource("Wood_Trunk", 6),
                Ingredient.item("Rock", 3))));

        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(11, derived.size());
        for (StationAsset.Conversion c : derived) {
            assertEquals(1, c.primaryInput().getQuantity());
            assertEquals(1, c.primaryOutput().getQuantity());
        }
        StationAsset.Conversion hardwood = derived.stream()
                .filter(c -> "Wood_Hardwood_Planks".equals(c.primaryOutput().getItemId()))
                .findFirst().orElseThrow();
        assertEquals("Wood_Hardwood_Trunk", hardwood.primaryInput().getResourceTypeId());
    }

    // ==================== Seam wave (decision 51c/52): Benches / Types / NativeTime ====================

    private static CraftingCandidate fullCandidate(String itemId, List<String> categories,
            List<String> benchIds, List<String> types, float timeSeconds, String resourceTypeId, int qty) {
        return new CraftingCandidate(itemId, categories, benchIds, types, timeSeconds,
                List.of(Ingredient.resource(resourceTypeId, qty)));
    }

    private static StationAsset.FromCrafting spec(String[] categories,
            String[] benches, String[] types, StationAsset.FromCrafting.NativeTime nativeTime) {
        return StationAsset.FromCrafting.of(categories, benches, types, nativeTime);
    }

    @Test
    void benchesRoute_matchesByBenchIdWhenNoCategoryIntersect() {
        // The candidate's category ("Processing_Cook") does NOT intersect the spec's Categories,
        // but its bench id ("Campfire") IS in the spec's Benches - so it derives via the bench route.
        CraftingCandidate cand = fullCandidate("Food_Fish_Grilled", List.of("Processing_Cook"),
                List.of("Campfire"), List.of("Processing"), 2f, "Fish", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(new String[]{"WoodPlanks"}, new String[]{"Campfire"}, null, null), List.of(cand));
        assertEquals(1, derived.size());
        assertEquals("Food_Fish_Grilled", derived.get(0).primaryOutput().getItemId());
    }

    @Test
    void benchesRoute_noMatch_derivesNothing() {
        CraftingCandidate cand = fullCandidate("Food_Fish_Grilled", List.of("Processing_Cook"),
                List.of("Tannery"), List.of("Processing"), 2f, "Fish", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(null, new String[]{"Campfire"}, null, null), List.of(cand));
        assertEquals(0, derived.size());
    }

    @Test
    void typesFilter_excludesWrongKind() {
        // Bench id matches, but the candidate is a Crafting recipe while the spec wants Processing only.
        CraftingCandidate cand = fullCandidate("Some_Craft", List.of("WoodPlanks"),
                List.of("Campfire"), List.of("Crafting"), 1f, "Wood_Trunk", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(new String[]{"WoodPlanks"}, null, new String[]{"Processing"}, null), List.of(cand));
        assertEquals(0, derived.size());
    }

    @Test
    void typesFilter_absentAllowsBothKinds() {
        CraftingCandidate crafting = fullCandidate("A_Plank", List.of("WoodPlanks"),
                List.of("Bench"), List.of("Crafting"), 1f, "Wood_A_Trunk", 1);
        CraftingCandidate processing = fullCandidate("B_Plank", List.of("WoodPlanks"),
                List.of("Bench"), List.of("Processing"), 1f, "Wood_B_Trunk", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(new String[]{"WoodPlanks"}, null, null, null), List.of(crafting, processing));
        assertEquals(2, derived.size());
    }

    @Test
    void nativeTime_bakesDurationMsFromLinearTransform() {
        // Scale 2.0 * 3s * 1000 + 500ms offset = 6500ms.
        StationAsset.FromCrafting.NativeTime nt = StationAsset.FromCrafting.NativeTime.of(2.0, 500L);
        CraftingCandidate cand = fullCandidate("Wood_Hardwood_Planks", List.of("WoodPlanks"),
                List.of("Sawbench"), List.of("Processing"), 3f, "Wood_Hardwood_Trunk", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(new String[]{"WoodPlanks"}, null, null, nt), List.of(cand));
        assertEquals(1, derived.size());
        assertEquals(Long.valueOf(6500L), derived.get(0).getDurationMs());
    }

    @Test
    void noNativeTime_leavesDurationNull() {
        CraftingCandidate cand = fullCandidate("Wood_Hardwood_Planks", List.of("WoodPlanks"),
                List.of("Sawbench"), List.of("Processing"), 3f, "Wood_Hardwood_Trunk", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(new String[]{"WoodPlanks"}, null, null, null), List.of(cand));
        assertEquals(1, derived.size());
        assertNull(derived.get(0).getDurationMs());
    }

    @Test
    void nativeDurationMs_pureCore_defaultsStretchNativeTime() {
        // Null NativeTime = no pacing (fall to Work.CycleMs).
        assertNull(StationRecipeDeriver.nativeDurationMs(null, 5f));
        // An empty NativeTime{} uses defaults (Scale 1.0, Offset 2000): 1.0 * 2s * 1000 + 2000 = 4000.
        StationAsset.FromCrafting.NativeTime empty = StationAsset.FromCrafting.NativeTime.of(null, null);
        assertEquals(Long.valueOf(4000L), StationRecipeDeriver.nativeDurationMs(empty, 2f));
        // A non-positive scale / negative offset reader-defaults, never produces a negative duration.
        StationAsset.FromCrafting.NativeTime bad = StationAsset.FromCrafting.NativeTime.of(-1.0, -50L);
        assertEquals(Long.valueOf(2000L), StationRecipeDeriver.nativeDurationMs(bad, 0f));
    }

    @Test
    void typesMatch_pureCore() {
        assertTrue(StationRecipeDeriver.typesMatch(List.of("Crafting"), null));
        assertTrue(StationRecipeDeriver.typesMatch(List.of("Crafting"), new String[0]));
        assertTrue(StationRecipeDeriver.typesMatch(List.of(), new String[]{"Processing"}));
        assertTrue(StationRecipeDeriver.typesMatch(List.of("Processing"), new String[]{"processing"}));
        assertFalse(StationRecipeDeriver.typesMatch(List.of("Crafting"), new String[]{"Processing"}));
    }

    // ==================== Selection wave (decision 56): source-category stamp ====================

    @Test
    void categoryRouteMatch_stampsTheMatchedCategory() {
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "WoodPlanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        assertEquals("WoodPlanks", derived.get(0).getCategory());
    }

    @Test
    void categoryStamp_usesTheCandidatesOwnCasing_notTheSpecs() {
        // The candidate declares "woodplanks" (lower); the spec asks "WoodPlanks". The stamp is the
        // candidate's own native category string (what the picker groups/labels by).
        List<CraftingCandidate> candidates = List.of(
                resourceCandidate("Wood_Hardwood_Planks", "woodplanks", "Wood_Hardwood_Trunk", 1));
        List<StationAsset.Conversion> derived =
                StationRecipeDeriver.deriveFromCrafting(spec("WoodPlanks"), candidates);
        assertEquals(1, derived.size());
        assertEquals("woodplanks", derived.get(0).getCategory());
    }

    @Test
    void benchRouteMatch_noCandidateCategory_stampsTheBenchId() {
        // A bench-route match on a candidate carrying NO native category: the stamp is the matched
        // bench id (decision 56's "bench-route matches stamp the bench id when no category exists").
        CraftingCandidate cand = new CraftingCandidate("Food_Fish_Grilled", List.of(),
                List.of("Campfire"), List.of("Processing"), 2f, List.of(Ingredient.resource("Fish", 1)));
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(null, new String[]{"Campfire"}, null, null), List.of(cand));
        assertEquals(1, derived.size());
        assertEquals("Campfire", derived.get(0).getCategory());
    }

    @Test
    void benchRouteMatch_withCandidateCategory_stampsTheCategoryNotTheBench() {
        // Matched only by bench, but the candidate DOES carry a native category - the category
        // (the recipe's own source category) wins over the bench id.
        CraftingCandidate cand = fullCandidate("Food_Fish_Grilled", List.of("Processing_Cook"),
                List.of("Campfire"), List.of("Processing"), 2f, "Fish", 1);
        List<StationAsset.Conversion> derived = StationRecipeDeriver.deriveFromCrafting(
                spec(new String[]{"WoodPlanks"}, new String[]{"Campfire"}, null, null), List.of(cand));
        assertEquals(1, derived.size());
        assertEquals("Processing_Cook", derived.get(0).getCategory());
    }

    @Test
    void deriveSourceCategory_pureCore() {
        CraftingCandidate withCat = new CraftingCandidate("X", List.of("Alpha", "Beta"),
                List.of("BenchA"), List.of("Crafting"), 0f, List.of(Ingredient.item("In", 1)));
        // Category-route match returns the matched wanted category (first candidate cat intersecting).
        assertEquals("Beta", StationRecipeDeriver.deriveSourceCategory(withCat,
                new String[]{"beta"}, null, true));
        // No category-route match but the candidate has categories -> its first native category.
        assertEquals("Alpha", StationRecipeDeriver.deriveSourceCategory(withCat,
                new String[]{"WoodPlanks"}, new String[]{"BenchA"}, false));
        // No categories at all -> the matched bench id.
        CraftingCandidate noCat = new CraftingCandidate("Y", List.of(),
                List.of("BenchB"), List.of("Processing"), 0f, List.of(Ingredient.item("In", 1)));
        assertEquals("BenchB", StationRecipeDeriver.deriveSourceCategory(noCat,
                null, new String[]{"benchb"}, false));
    }
}
