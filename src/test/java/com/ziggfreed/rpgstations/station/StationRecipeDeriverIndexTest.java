package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.recipe.ItemIdentity;
import com.ziggfreed.common.recipe.NativeRecipe;
import com.ziggfreed.common.recipe.RecipeBench;
import com.ziggfreed.common.recipe.RecipeCatalog;
import com.ziggfreed.common.recipe.RecipeIndex;
import com.ziggfreed.common.recipe.RecipeMaterial;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.StationRecipeDeriver.CraftingCandidate;

/**
 * Deriving from ANY vanilla bench through the shared recipe index: a standalone salvage-shaped
 * recipe derives with its FULL outputs at native quantities, the index's tag and family routes
 * carry over, a route-less line skips its recipe, two recipes making one item keep one order, and
 * the catalog's derived-conversion cache re-derives when the index's generation moves. Every
 * fixture value is authored by this test.
 */
public class StationRecipeDeriverIndexTest {

    private static NativeRecipe salvage(String id, String itemId, RecipeMaterial... outputs) {
        return new NativeRecipe(id, List.of(RecipeMaterial.item(itemId, 1)), List.of(outputs), outputs[0],
                List.of(new RecipeBench("Fixture_Salvagebench", "Processing", List.of(), 0)), 4f, null);
    }

    private static StationAsset.FromCrafting benchSpec() {
        return StationAsset.FromCrafting.of(null, new String[] {"Fixture_Salvagebench"}, null, null);
    }

    @Test
    void aStandaloneBenchRecipe_derivesWithItsFullOutputsAtNativeQuantities() {
        RecipeCatalog catalog = RecipeCatalog.of(List.of(
                salvage("Salvage_Fixture_Sword", "Fixture_Sword",
                        RecipeMaterial.item("Fixture_Ore", 2), RecipeMaterial.item("Fixture_Hide", 1),
                        RecipeMaterial.item("Fixture_Scrap", 1))),
                List.of(ItemIdentity.ofId("Fixture_Sword")));
        List<StationAsset.Conversion> rows = StationRecipeDeriver.deriveFromCrafting(benchSpec(),
                StationRecipeDeriver.candidatesOf(catalog));
        assertEquals(1, rows.size());
        StationAsset.Conversion row = rows.get(0);
        assertEquals("Fixture_Sword", row.primaryInput().getItemId());
        assertEquals(3, row.getOutput().length, "the whole output list rides the row");
        assertEquals(2, row.getOutput()[0].effectiveQuantity(), "the ore's native two");
        assertEquals("Fixture_Hide", row.getOutput()[1].getItemId());
        assertEquals("Fixture_Scrap", row.getOutput()[2].getItemId());
        assertEquals("Fixture_Salvagebench", row.getCategory(), "no category on the recipe, so the bench id stamps it");
        assertEquals(StationAsset.Conversion.DERIVED_TIER, row.effectiveTier());
        assertTrue(row.isDerived());
    }

    @Test
    void tagAndFamilyLines_carryOver_andARouteLessLineSkipsItsRecipe() {
        NativeRecipe tagged = new NativeRecipe("Fixture_Stew_Recipe_Generated_0",
                List.of(RecipeMaterial.tagged("Fixture_Ingredient", 3), RecipeMaterial.resource("Fixture_Fuel", 1)),
                List.of(RecipeMaterial.item("Fixture_Stew", 1)), RecipeMaterial.item("Fixture_Stew", 1),
                List.of(new RecipeBench("Fixture_Pot", "Processing", List.of("Prepared"), 0)), 5f, "Fixture_Stew");
        // A tag no loaded item carries keeps no route at the index (the material reads null on every route).
        NativeRecipe unresolvable = new NativeRecipe("Fixture_Broth_Recipe_Generated_0",
                List.of(new RecipeMaterial(null, null, null, 2, null)),
                List.of(RecipeMaterial.item("Fixture_Broth", 1)), RecipeMaterial.item("Fixture_Broth", 1),
                List.of(new RecipeBench("Fixture_Pot", "Processing", List.of("Prepared"), 0)), 5f, "Fixture_Broth");
        RecipeCatalog catalog = RecipeCatalog.of(List.of(tagged, unresolvable), List.of());
        List<CraftingCandidate> candidates = StationRecipeDeriver.candidatesOf(catalog);
        assertEquals(1, candidates.size(), "the route-less recipe is skipped at the candidate read");
        List<StationAsset.Conversion> rows = StationRecipeDeriver.deriveFromCrafting(
                StationAsset.FromCrafting.of(new String[] {"Prepared"}), candidates);
        assertEquals(1, rows.size());
        Ingredient[] inputs = rows.get(0).getInput();
        assertTrue(inputs[0].hasTagsRoute());
        assertTrue(inputs[0].getTags().containsKey("Fixture_Ingredient"));
        assertEquals(3, inputs[0].effectiveQuantity());
        assertEquals("Fixture_Fuel", inputs[1].getResourceTypeId());
        assertEquals("Prepared", rows.get(0).getCategory());
    }

    @Test
    void twoRecipesMakingOneItem_keepOneOrder_byRecipeId() {
        RecipeCatalog catalog = RecipeCatalog.of(List.of(
                salvage("Salvage_Fixture_Bloom2", "Fixture_Bloom_Alt", RecipeMaterial.item("Fixture_Petal", 2)),
                salvage("Salvage_Fixture_Bloom", "Fixture_Bloom", RecipeMaterial.item("Fixture_Petal", 1))),
                List.of());
        List<StationAsset.Conversion> rows = StationRecipeDeriver.deriveFromCrafting(benchSpec(),
                StationRecipeDeriver.candidatesOf(catalog));
        assertEquals(2, rows.size());
        assertEquals("Fixture_Bloom", rows.get(0).primaryInput().getItemId(),
                "same output id: the recipe id breaks the tie, on every boot");
        assertEquals("Fixture_Bloom_Alt", rows.get(1).primaryInput().getItemId());
    }

    @Test
    void aRecipeWithNoOutput_orNoBenchToScopeBy_isNotACandidate() {
        NativeRecipe noOutput = new NativeRecipe("Fixture_Void", List.of(RecipeMaterial.item("Fixture_A", 1)),
                List.of(), null, List.of(new RecipeBench("Fixture_Salvagebench", "Processing", List.of(), 0)), 1f, null);
        NativeRecipe pocket = new NativeRecipe("Fixture_Pocket", List.of(RecipeMaterial.item("Fixture_A", 1)),
                List.of(RecipeMaterial.item("Fixture_B", 1)), RecipeMaterial.item("Fixture_B", 1), List.of(), 1f, null);
        assertTrue(StationRecipeDeriver.candidatesOf(RecipeCatalog.of(List.of(noOutput, pocket), List.of())).isEmpty());
    }

    @Test
    void theOneOutputCandidateShape_stillDerivesOneOfTheItem() {
        CraftingCandidate legacy = new CraftingCandidate("Fixture_Plank", List.of("Planks"),
                List.of(Ingredient.resource("Fixture_Trunk", 1)));
        List<StationAsset.Conversion> rows = StationRecipeDeriver.deriveFromCrafting(
                StationAsset.FromCrafting.of(new String[] {"Planks"}), List.of(legacy));
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).getOutput().length);
        assertEquals(1, rows.get(0).primaryOutput().effectiveQuantity());
    }

    @Test
    void theCatalogsDerivedCache_reDerivesWhenTheRecipeIndexGenerationMoves() {
        StationAsset.Recipe recipe = StationAsset.Recipe.of(new StationAsset.Conversion[] {
                StationAsset.Conversion.of(Ingredient.item("Fixture_In", 1), Ingredient.item("Fixture_Out", 1))});
        StationAsset station = StationAsset.of("fixturegen",
                StationAsset.Identity.of("rpgstations.station.fixturegen.name", "rpgstations.station.fixturegen.desc", null),
                ActionDef.of("Mill").withRecipe(recipe));
        StationCatalog catalog = StationCatalog.getInstance();
        catalog.fold(Map.of("fixturegen", station), true);
        StationAsset.Conversion[] first = catalog.resolvedConversions(station, "Mill", recipe);
        assertSame(first, catalog.resolvedConversions(station, "Mill", recipe), "a steady index keeps the cache");
        RecipeIndex.live().invalidate();
        StationAsset.Conversion[] second = catalog.resolvedConversions(station, "Mill", recipe);
        assertNotSame(first, second, "a moved generation re-derives");
        assertEquals(first.length, second.length);
        assertEquals("Fixture_Out", second[0].primaryOutput().getItemId());
        catalog.fold(Map.of(), true);
    }
}
