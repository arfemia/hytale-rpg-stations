package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Pins the set-recipe MATCHING cores: the full four-route pile-entry matcher (exact / family /
 * Tags / match-any), the predicate forms of the pile count and drain, and the {@code IsExactSet}
 * contamination semantics per drawn socket (extras in a pile the row draws from block it; extras
 * anywhere else never do). All fixtures are the test's own; identity resolvers are injected maps.
 */
class StationSetRecipeMatchTest {

    private static final Function<String, String[]> FAMILIES = itemId -> switch (itemId) {
        case "Food_Meat_Raw", "Food_Poultry_Raw" -> new String[] {"Meat"};
        case "Food_Carrot", "Food_Onion" -> new String[] {"Vegetable"};
        default -> new String[0];
    };

    private static final Function<String, Map<String, String[]>> TAGS = itemId -> switch (itemId) {
        case "Food_Carrot", "Food_Onion" -> Map.of("Type", new String[] {"Ingredient"});
        case "Food_Meat_Raw" -> Map.of("Type", new String[] {"Ingredient"}, "Raw", new String[0]);
        default -> Map.of();
    };

    private static Custody.ResolvedSocket socket(String id) {
        return new Custody.ResolvedSocket(id, true, null, null, null, 100,
                false, false, null, false, false, false, null);
    }

    private static Map<String, Integer> pile(Object... pairs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return out;
    }

    // ==================== ingredientEntryMatcher (the four routes) ====================

    @Test
    void entryMatcher_exactRoute() {
        var matches = StationCustody.ingredientEntryMatcher(Ingredient.item("Food_Carrot", 1), FAMILIES, TAGS);
        assertTrue(matches.test("food_carrot"));
        assertFalse(matches.test("Food_Onion"));
    }

    @Test
    void entryMatcher_familyRoute() {
        var matches = StationCustody.ingredientEntryMatcher(Ingredient.resource("Meat", 1), FAMILIES, TAGS);
        assertTrue(matches.test("Food_Meat_Raw"));
        assertTrue(matches.test("Food_Poultry_Raw"));
        assertFalse(matches.test("Food_Carrot"));
    }

    @Test
    void entryMatcher_tagsRoute_valuesAndPresenceForms() {
        var values = StationCustody.ingredientEntryMatcher(
                Ingredient.tagged(Map.of("Type", new String[] {"Ingredient"}), 1), FAMILIES, TAGS);
        assertTrue(values.test("Food_Carrot"));
        assertFalse(values.test("Rock_Stone"));
        var presence = StationCustody.ingredientEntryMatcher(
                Ingredient.tagged(Map.of("Raw", new String[0]), 1), FAMILIES, TAGS);
        assertTrue(presence.test("Food_Meat_Raw"), "empty value list = family-key presence");
        assertFalse(presence.test("Food_Carrot"));
    }

    @Test
    void entryMatcher_matchAnyRoute_acceptsEverything() {
        var matches = StationCustody.ingredientEntryMatcher(Ingredient.matchAny(3), FAMILIES, TAGS);
        assertTrue(matches.test("Food_Carrot"));
        assertTrue(matches.test("Anything_At_All"));
    }

    // ==================== predicate count/drain cores ====================

    @Test
    void availableInPile_predicateForm_countsMatchingEntriesOnly() {
        Map<String, Integer> items = pile("Food_Meat_Raw", 2, "Food_Carrot", 3);
        assertEquals(2, StationCustody.availableInPile(items,
                StationCustody.ingredientEntryMatcher(Ingredient.resource("Meat", 1), FAMILIES, TAGS)));
        assertEquals(5, StationCustody.availableInPile(items,
                StationCustody.ingredientEntryMatcher(Ingredient.matchAny(1), FAMILIES, TAGS)));
    }

    @Test
    void drainFromPile_predicateForm_oldestFirstAcrossMatchingEntries() {
        Map<String, Integer> items = pile("Food_Meat_Raw", 1, "Food_Carrot", 2, "Food_Onion", 2);
        Map<String, Integer> drained = new LinkedHashMap<>();
        int got = StationCustody.drainFromPile(items,
                StationCustody.ingredientEntryMatcher(Ingredient.matchAny(3), FAMILIES, TAGS), 3, drained);
        assertEquals(3, got);
        assertEquals(Map.of("Food_Meat_Raw", 1, "Food_Carrot", 2), drained);
        assertEquals(pile("Food_Onion", 2), items, "the un-needed remainder stays put");
    }

    // ==================== IsExactSet (per drawn socket) ====================

    private static StationAsset.Conversion exactSetRow(Ingredient... inputs) {
        return StationAsset.Conversion.of(inputs,
                new Ingredient[] {Ingredient.item("Food_Kebab", 1)}, null, null).withExactSet(true);
    }

    @Test
    void exactSet_pileHoldingExactlyTheInputs_passes() {
        StationAsset.Conversion row = exactSetRow(
                Ingredient.of(null, "Meat", 2, "rack"), Ingredient.of(null, "Vegetable", 1, "basket"));
        Map<String, Map<String, Integer>> piles = Map.of(
                "rack", pile("Food_Meat_Raw", 2),
                "basket", pile("Food_Carrot", 1));
        assertTrue(StationCustody.exactSetSatisfied(row, piles::get,
                List.of(socket("rack"), socket("basket")), FAMILIES, TAGS));
    }

    @Test
    void exactSet_extraQuantityInADrawnPile_blocks() {
        StationAsset.Conversion row = exactSetRow(Ingredient.of(null, "Meat", 2, "rack"));
        Map<String, Map<String, Integer>> piles = Map.of("rack", pile("Food_Meat_Raw", 3));
        assertFalse(StationCustody.exactSetSatisfied(row, piles::get,
                List.of(socket("rack")), FAMILIES, TAGS),
                "a third meat is beyond the row's inputs");
    }

    @Test
    void exactSet_foreignItemInADrawnPile_blocks() {
        StationAsset.Conversion row = exactSetRow(
                Ingredient.of(null, "Meat", 1, "rack"), Ingredient.of(null, "Vegetable", 1, "rack"));
        Map<String, Map<String, Integer>> piles = Map.of("rack", pile("Food_Meat_Raw", 1, "Rock_Stone", 1));
        assertFalse(StationCustody.exactSetSatisfied(row, piles::get,
                List.of(socket("rack")), FAMILIES, TAGS),
                "a rock matches no input drawing from the rack");
    }

    @Test
    void exactSet_extrasInAnUndrawnSocket_neverBlock() {
        StationAsset.Conversion row = exactSetRow(Ingredient.of(null, "Meat", 2, "rack"));
        Map<String, Map<String, Integer>> piles = Map.of(
                "rack", pile("Food_Meat_Raw", 2),
                "basket", pile("Food_Onion", 40));
        assertTrue(StationCustody.exactSetSatisfied(row, piles::get,
                List.of(socket("rack"), socket("basket")), FAMILIES, TAGS),
                "material elsewhere is not the drawn pile's business");
    }

    @Test
    void exactSet_socketlessInputsDrawTheFirstItemSocket() {
        StationAsset.Conversion row = exactSetRow(Ingredient.resource("Meat", 2));
        Map<String, Map<String, Integer>> piles = Map.of("rack", pile("Food_Meat_Raw", 2));
        assertTrue(StationCustody.exactSetSatisfied(row, piles::get,
                List.of(socket("rack")), FAMILIES, TAGS));
        Map<String, Map<String, Integer>> contaminated = Map.of("rack", pile("Food_Meat_Raw", 2, "Food_Carrot", 1));
        assertFalse(StationCustody.exactSetSatisfied(row, contaminated::get,
                List.of(socket("rack")), FAMILIES, TAGS));
    }

    @Test
    void exactSet_matchAnyPlusExact_theWholePileMustBeAccountedFor() {
        // "1 named meat + 2 of anything, and nothing else": 1 meat + 2 onions passes; a 4th item fails.
        StationAsset.Conversion row = exactSetRow(
                Ingredient.item("Food_Meat_Raw", 1), Ingredient.matchAny(2));
        Map<String, Map<String, Integer>> exact = Map.of("main", pile("Food_Meat_Raw", 1, "Food_Onion", 2));
        assertTrue(StationCustody.exactSetSatisfied(row, exact::get,
                List.of(socket("main")), FAMILIES, TAGS));
        Map<String, Map<String, Integer>> over = Map.of("main", pile("Food_Meat_Raw", 1, "Food_Onion", 3));
        assertFalse(StationCustody.exactSetSatisfied(row, over::get,
                List.of(socket("main")), FAMILIES, TAGS));
    }
}
