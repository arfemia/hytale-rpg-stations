package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The batch's input rule, as pure logic: every conversion of a batch is the ONE matched row run again, so its
 * inputs are that row's own entries with their quantities multiplied, every route and socket address kept, and
 * never another material; the count is held to what the source can feed and never below one; the beat's own batch
 * wins over the recipe's; and with nobody to ask (a settle) the authored count reads.
 */
class StationBatchTest {

    @Test
    void times_keepsEveryRouteAndSocket_andOnlyMultipliesTheQuantity() {
        Ingredient species = Ingredient.of(null, "Fixture_Oak_Trunk", 1, "logs");
        Ingredient exact = Ingredient.item("Fixture_Peg", 2);
        Ingredient tagged = Ingredient.tagged(Map.of("Type", new String[] {"Fixture"}), 1);
        Ingredient[] out = StationBatch.times(new Ingredient[] {species, exact, tagged, null}, 3);

        assertEquals("Fixture_Oak_Trunk", out[0].getResourceTypeId(), "the same species, never another");
        assertNull(out[0].getItemId());
        assertEquals("logs", out[0].getSocket(), "the same pile");
        assertEquals(3, out[0].effectiveQuantity());
        assertEquals("Fixture_Peg", out[1].getItemId());
        assertEquals(6, out[1].effectiveQuantity(), "each entry's own quantity, times the batch");
        assertEquals("Fixture", out[2].getTags().get("Type")[0]);
        assertEquals(3, out[2].effectiveQuantity());
        assertNull(out[3]);
        assertEquals(1, species.effectiveQuantity(), "the row's own entry is never written");
    }

    @Test
    void times_ofOne_isTheSameArray() {
        Ingredient[] row = {Ingredient.item("Fixture_Log", 1)};
        assertSame(row, StationBatch.times(row, 1));
        assertSame(row, StationBatch.times(row, 0));
    }

    @Test
    void count_isHeldToTheFeed_andNeverBelowOne() {
        assertEquals(2, StationBatch.count(2, 10));
        assertEquals(1, StationBatch.count(2, 1), "one log left saws the one");
        assertEquals(1, StationBatch.count(2, 0), "the selection already found one runnable");
        assertEquals(1, StationBatch.count(0, 10));
        assertEquals(8, StationBatch.count(8, 8));
    }

    @Test
    void cyclesLeft_countsCyclesAtTheBatch_theLastTakingWhatIsLeft() {
        assertEquals(5, StationBatch.cyclesLeft(10, 2));
        assertEquals(6, StationBatch.cyclesLeft(11, 2), "the last cycle saws the one log left");
        assertEquals(11, StationBatch.cyclesLeft(11, 1));
        assertEquals(0, StationBatch.cyclesLeft(0, 2));
        assertEquals(Integer.MAX_VALUE / 2 + 1, StationBatch.cyclesLeft(Integer.MAX_VALUE, 2));
    }

    @Test
    void theBeatsOwnBatchWins_overTheRecipes() {
        StationStep.Repeat beat = StationStep.Repeat.times(3);
        StationStep.Repeat recipeBatch = StationStep.Repeat.times(2);
        StationAsset.Recipe recipe = StationAsset.Recipe.of(null).withBatch(recipeBatch);
        assertSame(beat, StationBatch.batchOf(StationStep.Convert.of(null, beat), recipe));
        assertSame(recipeBatch, StationBatch.batchOf(StationStep.Convert.on(), recipe));
        assertNull(StationBatch.batchOf(StationStep.Convert.on(), StationAsset.Recipe.of(null)));
        assertNull(StationBatch.batchOf(null, null));
    }

    @Test
    void resolve_readsTheWorker_andTheAuthoredCountWithNobodyToAsk() {
        StationStep.Repeat ladder = StationStep.Repeat.of(null, 1, 2,
                new FactorFormula.Term[] {FactorFormula.Term.of("fixture:double", null, 1.0)});
        FactorLookup holder = (factorId, param) -> "fixture:double".equals(factorId) ? 1.0 : null;
        FactorLookup unknown = (factorId, param) -> null;
        assertEquals(2, StationBatch.resolve(ladder, holder));
        assertEquals(1, StationBatch.resolve(ladder, unknown), "an unanswerable factor adds nothing");
        assertEquals(1, StationBatch.resolve(ladder, null), "a settle reads Min");
        assertEquals(3, StationBatch.resolve(StationStep.Repeat.times(3), null), "a settle reads Times");
        assertEquals(1, StationBatch.resolve(null, holder));
    }
}
