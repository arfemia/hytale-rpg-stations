package com.ziggfreed.rpgstations.loot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ziggfreed.common.factor.FactorFormula;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootPool;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.LootableConfig;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.subject.Subject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A station referencing a table reads BOTH halves of it. The rolls half was always read; the
 * {@code Pool} half was resolved by the shared library and then dropped on the floor here, so a
 * table authored as a bag of competing outcomes handed a station nothing while handing a chest its
 * picks - the same JSON behaving differently purely by where it was referenced.
 *
 * <p>Fixture table ids, items and weights are this test's own and mirror no shipped content.
 */
class StationLootPoolTest {

    private static final Subject WORKER =
            Subject.of(UUID.fromString("66666666-6666-6666-6666-666666666666"), "Fixture");

    /** The pool always draws its first entry: one pick, and a sample pinned at the bottom. */
    private static final DoubleSupplier FIRST_ENTRY = () -> 0.0;

    @AfterEach
    void clearTables() {
        LootableConfig.getInstance().mergePackLayer(Map.of());
    }

    private static void load(LootableAsset... tables) {
        Map<String, LootableAsset> layer = new LinkedHashMap<>();
        for (LootableAsset table : tables) {
            layer.put(table.getId(), table);
        }
        LootableConfig.getInstance().mergePackLayer(layer);
    }

    private static LootPool onePickOf(String itemId) {
        return LootPool.of(FactorFormula.of(1.0, null, null),
                new LootPool.Entry[] {LootPool.Entry.of(1.0, null, LootGrants.ofItem(itemId, 1))});
    }

    private static Roll cycleRollGranting(String itemId) {
        return Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null,
                LootGrants.ofItem(itemId, 1), null);
    }

    /** A ref naming one table, the shape an action's {@code Bonus.Lootables} authors. */
    private static LootEngine.Resolved resolveRef(String tableId) {
        return StationLootEngine.resolve(LootRef.of(new String[] {tableId}, null));
    }

    /** One pass with an item sink that always delivers in full, so what landed is what was drawn. */
    private static StationLootEngine.GrantResult pass(LootEngine.Resolved resolved, String trigger) {
        return StationLootEngine.rollAndGrant(resolved, trigger, FactorLookup.none(), FIRST_ENTRY,
                (itemId, count) -> count, null, WORKER, null, "fixture");
    }

    @Test
    void aReferencedTablesPoolIsResolvedAlongsideItsRolls() {
        load(LootableAsset.of("fixture_bag", new Roll[] {cycleRollGranting("Fixture_Staple")},
                onePickOf("Fixture_Pick"), null));

        LootEngine.Resolved resolved = resolveRef("fixture_bag");

        assertEquals(1, resolved.rolls().size());
        assertEquals(1, resolved.pools().size(), "the Pool half rides the same resolution");
    }

    @Test
    void aCyclePassDrawsThePool() {
        load(LootableAsset.of("fixture_bag", null, onePickOf("Fixture_Pick"), null));

        StationLootEngine.GrantResult result =
                pass(resolveRef("fixture_bag"), StationLootEngine.TRIGGER_CYCLE);

        assertEquals(Map.of("Fixture_Pick", 1), result.getDropListItems());
    }

    /**
     * Rolls and picks are separate halves of one pass, and a table holding both hands over one of
     * each - never a pick standing in for a roll, and never a half counted twice.
     */
    @Test
    void aTableHoldingBothHandsOverOneOfEach() {
        load(LootableAsset.of("fixture_bag", new Roll[] {cycleRollGranting("Fixture_Staple")},
                onePickOf("Fixture_Pick"), null));

        StationLootEngine.GrantResult result =
                pass(resolveRef("fixture_bag"), StationLootEngine.TRIGGER_CYCLE);

        assertEquals(Map.of("Fixture_Staple", 1, "Fixture_Pick", 1), result.getDropListItems());
    }

    /**
     * A pool names no trigger, so it belongs to the station's own default moment - the completed
     * work cycle. The completion pass runs from inside session stop over the SAME resolution, so
     * drawing there too would hand one session its bag twice.
     */
    @Test
    void aCompletionPassDrawsNoPool() {
        load(LootableAsset.of("fixture_bag", null, onePickOf("Fixture_Pick"), null));

        StationLootEngine.GrantResult result =
                pass(resolveRef("fixture_bag"), StationLootEngine.TRIGGER_COMPLETION);

        assertTrue(result.getDropListItems().isEmpty());
    }

    /** Two referenced tables draw twice, once each - their bags never pour together. */
    @Test
    void twoReferencedTablesEachKeepTheirOwnBag() {
        load(LootableAsset.of("fixture_bag", null, onePickOf("Fixture_Pick"), null),
                LootableAsset.of("fixture_other", null, onePickOf("Fixture_Other_Pick"), null));

        LootEngine.Resolved resolved = StationLootEngine.resolve(
                LootRef.of(new String[] {"fixture_bag", "fixture_other"}, null));
        StationLootEngine.GrantResult result = pass(resolved, StationLootEngine.TRIGGER_CYCLE);

        assertEquals(2, resolved.pools().size());
        assertEquals(Map.of("Fixture_Pick", 1, "Fixture_Other_Pick", 1), result.getDropListItems());
    }
}
