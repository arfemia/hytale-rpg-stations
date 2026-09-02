package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.Ingredient;

/**
 * The route-PARITY pin: {@link Ingredient} (a recipe/consume entry) and {@link ActionInput} (a
 * held-item / placement matcher) both accept the SAME three identity routes - exact {@code ItemId},
 * {@code ResourceTypeId} family, native {@code Tags} - and, delegating to the one shared
 * {@code ziggfreed-common} {@code ItemMatch} core, must answer IDENTICALLY for any single-route
 * reference over any candidate. The two leaves stay separate types on purpose (an ActionInput
 * carries no Quantity and adds the Function route; an Ingredient carries Quantity/Socket), so this
 * matrix is what keeps their shared half from drifting apart.
 */
class IngredientActionInputRouteParityTest {

    /** One candidate item identity, fully resolved the way the live seam resolves it. */
    private record Candidate(String itemId, String[] families, Map<String, String[]> tags) {
    }

    /** One single-route authored reference, expressed as BOTH leaves. */
    private record Reference(String label, Ingredient ingredient, ActionInput actionInput) {

        static Reference item(String id) {
            return new Reference("ItemId=" + id, Ingredient.item(id, 1),
                    ActionInput.of(id, null, null, null));
        }

        static Reference family(String family) {
            return new Reference("ResourceTypeId=" + family, Ingredient.resource(family, 1),
                    ActionInput.of(null, family, null, null));
        }

        static Reference tags(Map<String, String[]> tags) {
            return new Reference("Tags=" + tags.keySet(), Ingredient.tagged(tags, 1),
                    ActionInput.of(null, null, tags, null));
        }
    }

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate("Food_Meat_Raw", new String[] {"Meat"},
                    Map.of("Type", new String[] {"Ingredient"}, "Raw", new String[0])),
            new Candidate("Food_Carrot", new String[] {"Vegetable"},
                    Map.of("Type", new String[] {"Ingredient"})),
            new Candidate("Rock_Stone", new String[] {"Rock"},
                    Map.of("Type", new String[] {"Rock"})),
            new Candidate("Mystery_Thing", new String[0], Map.of()));

    private static final List<Reference> REFERENCES = List.of(
            Reference.item("Food_Meat_Raw"),
            Reference.item("food_carrot"),
            Reference.family("Meat"),
            Reference.family("rock"),
            Reference.tags(Map.of("Type", new String[] {"Ingredient"})),
            Reference.tags(Map.of("Raw", new String[0])),
            Reference.tags(Map.of("Type", new String[] {"Ore"})));

    @Test
    void everySingleRouteReference_answersIdenticallyThroughBothLeaves() {
        for (Reference ref : REFERENCES) {
            for (Candidate cand : CANDIDATES) {
                boolean viaIngredient = StationCustody.matchesIngredient(ref.ingredient(),
                        cand.itemId(), cand.families(), cand.tags());
                boolean viaActionInput = StationCustody.matchesInput(ref.actionInput(),
                        cand.itemId(), cand.families(), cand.tags(), null);
                assertEquals(viaIngredient, viaActionInput,
                        ref.label() + " vs " + cand.itemId() + ": the two leaves disagreed");
            }
        }
    }

    @Test
    void bothLeaves_acceptTheSameRouteSet() {
        // Each leaf reports the same three routes as authored/authorable.
        Ingredient item = Ingredient.item("X", 1);
        Ingredient family = Ingredient.resource("F", 1);
        Ingredient tags = Ingredient.tagged(Map.of("T", new String[0]), 1);
        assertTrue(item.hasItemRoute() && family.hasResourceRoute() && tags.hasTagsRoute());
        ActionInput aItem = ActionInput.of("X", null, null, null);
        ActionInput aFamily = ActionInput.of(null, "F", null, null);
        ActionInput aTags = ActionInput.of(null, null, Map.of("T", new String[0]), null);
        assertTrue(aItem.getItemId() != null && aFamily.getResourceTypeId() != null
                && aTags.getTags() != null);
    }

    @Test
    void routeLessLeaves_agreeOnMatchEverything() {
        // A route-less Ingredient is the match-any input; a route-less ActionInput is the
        // catch-all matcher. Both answer "matches" for any candidate.
        Ingredient matchAny = Ingredient.matchAny(1);
        ActionInput catchAll = ActionInput.of(null, null, null, null);
        assertTrue(catchAll.isCatchAll());
        for (Candidate cand : CANDIDATES) {
            assertTrue(StationCustody.matchesIngredient(matchAny, cand.itemId(), cand.families(),
                    cand.tags()));
            // matchesInput treats the catch-all at its CALLERS (isCatchAll short-circuits before
            // matching), which is the same "matches everything" answer.
        }
    }
}
