package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.recipe.ItemIdentity;
import com.ziggfreed.common.recipe.NativeRecipe;
import com.ziggfreed.common.recipe.RecipeBench;
import com.ziggfreed.common.recipe.RecipeCatalog;
import com.ziggfreed.common.recipe.RecipeMaterial;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.station.StationRecipeDeriver.CraftingCandidate;

/**
 * The Sawmill's BYTE-IDENTITY pin: the full 33-row derivation the shipped
 * {@code Stations/Sawmill.json} produces over vanilla's eleven plank species, held to one exact
 * shape - rows, order, category stamps, tiers, quantities and the {@code Yield.Base 1} outcome -
 * whichever candidate source feeds the deriver. Every fixture value mirrors a vanilla recipe file
 * read out of the installed game's asset mirror ({@code Wood_<Species>_{Planks,Decorative,Ornate}.json}:
 * one {@code Wood_<Species>_Trunk} input, {@code OutputQuantity} 1, bench {@code Builders} of type
 * {@code StructuralCrafting} filed under one of the three plank categories; all 33 checked
 * against the mirror), plus the vanilla {@code Bench_Builders} recipe that shares the bench but
 * none of the categories and so must never derive.
 *
 * <p><b>What the capture evidence is.</b> Tests 1 and 3 ({@link #theThirtyThreePlankRows_deriveExactlyAsShipped_fromCandidates}
 * and {@link #yieldBaseOne_leavesEveryPlankRowAtOne}) drive the deriver through the
 * {@code CraftingCandidate} shape the 1.0.0 deriver already took, so they are the ones that could
 * run against RPG Stations {@code fb206bb} and did. Test 2
 * ({@link #theThirtyThreePlankRows_deriveExactlyAsShipped_fromTheSharedRecipeIndex}) drives the
 * NEW pure half of the live adapter, {@code StationRecipeDeriver.candidatesOf(RecipeCatalog)},
 * which {@code fb206bb} did not have, so it pins the index-fed path against the same 33 rows
 * rather than against a run on 1.0.0. The live swap itself, from the {@code Item} asset map to
 * {@code RecipeIndex.live()}, has no unit coverage: whether the index is populated when a station
 * catalog first asks, and whether its {@code generation()} moves on a late population, is a
 * dev-server check ({@code dev-server.ps1 -Check} over the shipped Sawmill).
 */
public class SawmillDerivationParityTest {

    /** Vanilla's plank species, in the order the 1.0.0 deriver sorts their rows (case-insensitive output id). */
    static final String[] SPECIES = {
            "Blackwood", "Darkwood", "Deadwood", "Drywood", "Goldenwood", "Greenwood",
            "Hardwood", "Lightwood", "Redwood", "Softwood", "Tropicalwood"};

    /** The three plank categories, in the order their output ids sort within one species. */
    static final String[][] KINDS = {
            {"Decorative", "DecorativePlanks"},
            {"Ornate", "OrnatePlanks"},
            {"Planks", "WoodPlanks"}};

    /** The Sawmill's own {@code Recipe} group: {@code FromCrafting.Categories} plus {@code Yield.Base 1}. */
    static StationAsset.Recipe sawmillRecipe() {
        return StationAsset.Recipe.of(null,
                StationAsset.FromCrafting.of(new String[] {"WoodPlanks", "DecorativePlanks", "OrnatePlanks"}),
                StationAsset.Yield.of(1, null, null, null));
    }

    /** The 1.0.0 candidate shape: one candidate per vanilla plank recipe, plus the non-deriving neighbours. */
    static List<CraftingCandidate> vanillaCandidates() {
        List<CraftingCandidate> out = new ArrayList<>();
        // Authored in a deliberately scrambled order: the deriver's own sort decides the row order.
        for (int i = SPECIES.length - 1; i >= 0; i--) {
            for (String[] kind : KINDS) {
                out.add(new CraftingCandidate("Wood_" + SPECIES[i] + "_" + kind[0], List.of(kind[1]),
                        List.of("Builders"), List.of("StructuralCrafting"), 0f,
                        List.of(Ingredient.resource("Wood_" + SPECIES[i] + "_Trunk", 1))));
            }
        }
        out.add(new CraftingCandidate("Bench_Builders", List.of("Tools", "Workbench_Crafting"),
                List.of("Fieldcraft", "Workbench"), List.of("Crafting", "Crafting"), 0f,
                List.of(Ingredient.resource("Wood_Trunk", 6), Ingredient.item("Rock", 3))));
        out.add(new CraftingCandidate("Salvage_Weapon_Sword_Iron", List.of(),
                List.of("Salvagebench"), List.of("Processing"), 4f,
                List.of(Ingredient.item("Weapon_Sword_Iron", 1))));
        return out;
    }

    /** The same vanilla set as the shared recipe index holds it: one {@link NativeRecipe} per plank recipe. */
    static RecipeCatalog vanillaCatalog() {
        List<NativeRecipe> recipes = new ArrayList<>();
        List<ItemIdentity> items = new ArrayList<>();
        for (int i = SPECIES.length - 1; i >= 0; i--) {
            for (String[] kind : KINDS) {
                String itemId = "Wood_" + SPECIES[i] + "_" + kind[0];
                RecipeMaterial planks = RecipeMaterial.item(itemId, 1);
                recipes.add(new NativeRecipe(itemId + "_Recipe_Generated_0",
                        List.of(RecipeMaterial.resource("Wood_" + SPECIES[i] + "_Trunk", 1)),
                        List.of(planks), planks,
                        List.of(new RecipeBench("Builders", "StructuralCrafting", List.of(kind[1]), 0)),
                        0f, itemId));
                items.add(ItemIdentity.ofId(itemId));
            }
        }
        RecipeMaterial bench = RecipeMaterial.item("Bench_Builders", 1);
        recipes.add(new NativeRecipe("Bench_Builders_Recipe_Generated_0",
                List.of(RecipeMaterial.resource("Wood_Trunk", 6), RecipeMaterial.item("Rock", 3)),
                List.of(bench), bench,
                List.of(new RecipeBench("Fieldcraft", "Crafting", List.of("Tools"), 0),
                        new RecipeBench("Workbench", "Crafting", List.of("Workbench_Crafting"), 0)),
                0f, "Bench_Builders"));
        recipes.add(new NativeRecipe("Salvage_Weapon_Sword_Iron",
                List.of(RecipeMaterial.item("Weapon_Sword_Iron", 1)),
                List.of(RecipeMaterial.item("Ore_Iron", 2), RecipeMaterial.item("Ingredient_Hide_Light", 1)),
                RecipeMaterial.item("Ore_Iron", 2),
                List.of(new RecipeBench("Salvagebench", "Processing", List.of(), 0)),
                4f, null));
        return RecipeCatalog.of(recipes, items);
    }

    /** Every assertion the Sawmill's 33 rows must keep, whichever candidate source produced them. */
    static void assertSawmillRows(StationAsset.Conversion[] rows) {
        assertEquals(33, rows.length, "eleven species times three plank categories");
        int index = 0;
        for (String species : SPECIES) {
            for (String[] kind : KINDS) {
                StationAsset.Conversion row = rows[index];
                String label = "row " + index + " (" + species + " " + kind[0] + ")";
                assertEquals("Wood_" + species + "_" + kind[0], row.primaryOutput().getItemId(), label);
                assertEquals(1, row.getOutput().length, label + " has one output");
                assertEquals(1, row.primaryOutput().effectiveQuantity(), label + " yields one");
                assertNull(row.primaryOutput().getResourceTypeId(), label);
                assertEquals(1, row.getInput().length, label + " has one input");
                assertEquals("Wood_" + species + "_Trunk", row.primaryInput().getResourceTypeId(), label);
                assertNull(row.primaryInput().getItemId(), label);
                assertEquals(1, row.primaryInput().effectiveQuantity(), label + " consumes one trunk");
                assertEquals(kind[1], row.getCategory(), label + " category stamp");
                assertEquals(StationAsset.Conversion.DERIVED_TIER, row.effectiveTier(), label + " tier");
                assertTrue(row.isDerived(), label + " derived mark");
                assertNull(row.getDurationMs(), label + " no native-time pace");
                assertFalse(row.effectiveIsExactSet(), label);
                assertNull(row.getDoneness(), label);
                index++;
            }
        }
    }

    @Test
    void theThirtyThreePlankRows_deriveExactlyAsShipped_fromCandidates() {
        StationAsset.Conversion[] rows = StationRecipeDeriver.resolve(sawmillRecipe(), vanillaCandidates());
        assertSawmillRows(rows);
    }

    @Test
    void theThirtyThreePlankRows_deriveExactlyAsShipped_fromTheSharedRecipeIndex() {
        StationAsset.Conversion[] rows = StationRecipeDeriver.resolve(sawmillRecipe(),
                StationRecipeDeriver.candidatesOf(vanillaCatalog()));
        assertSawmillRows(rows);
    }

    @Test
    void yieldBaseOne_leavesEveryPlankRowAtOne() {
        StationAsset.Conversion[] rows = StationRecipeDeriver.resolve(sawmillRecipe(), vanillaCandidates());
        StationAsset.Yield yield = sawmillRecipe().getYield();
        for (StationAsset.Conversion row : rows) {
            Ingredient[] yielded = StationYield.applyToOutputs(yield, row.getOutput());
            assertEquals(1, yielded.length);
            assertEquals(row.primaryOutput().getItemId(), yielded[0].getItemId());
            assertEquals(1, yielded[0].effectiveQuantity(), "Yield.Base 1 over a one-plank row stays one plank");
        }
    }
}
