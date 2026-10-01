package com.ziggfreed.rpgstations.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootableAsset;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.reward.RewardKinds;
import com.ziggfreed.common.subject.Subject;

/**
 * The origin split a grant pass carries: what a roll authored {@code Expected} paid is the
 * moment's expected payout and reads as ORDINARY output, everything else a pass paid is a find.
 * The load-bearing case is the Sawmill: none of this jar's shipped Sawmill tables authors
 * {@code Expected}, so every item they pay is a find and every one of their HUD and summary rows
 * is exactly what it was before the knob existed. That pin reads the shipped files themselves
 * (structure, never a balance number).
 */
class StationLootOriginTest {

    private static final Subject WORKER =
            Subject.of(UUID.fromString("66666666-6666-6666-6666-666666666666"), "Fixture");

    private static final Path SHIPPED_LOOTABLES =
            Path.of("src", "main", "resources", "Server", "ZiggfreedCommon", "Lootables");

    /**
     * A pass pays its rewards through the shared vocabulary, which is process-wide and emptied by a
     * reset anywhere, so the station kinds are registered before each case like every other pass
     * test does.
     */
    @BeforeEach
    void registerTheStationKinds() {
        StationRewardKinds.registerInto(RewardKinds.shared());
    }

    private static StationLootEngine.GrantResult pass(List<Roll> rolls) {
        return StationLootEngine.rollAndGrant(new LootEngine.Resolved(rolls, List.of()),
                StationLootEngine.TRIGGER_CYCLE, FactorLookup.none(), () -> 0.0,
                (itemId, count) -> count, id -> Map.of(id + "_Item", 2), WORKER, null, "fixture");
    }

    private static Roll cycleRoll(LootGrants grants) {
        return Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null, grants, null);
    }

    @Test
    void anExpectedRollsItemsAreOrdinary_everyOtherRollsAreAFind() {
        StationLootEngine.GrantResult result = pass(List.of(
                cycleRoll(LootGrants.ofItem("Fixture_Essence", 3)).withExpected(true),
                cycleRoll(LootGrants.of(null, new String[] {"Fixture_Finds"}, null, null))));

        assertEquals(Map.of("Fixture_Essence", 3), result.getExpectedItems());
        assertEquals(Map.of("Fixture_Finds_Item", 2), result.getFoundItems());
        assertEquals(Map.of("Fixture_Essence", 3, "Fixture_Finds_Item", 2), result.getDropListItems(),
                "the merged tally is unchanged: the two maps split it, they never replace it");
    }

    @Test
    void aTableAuthoringNoExpected_paysFindsOnly_soItsRowsAreWhatTheyWere() {
        StationLootEngine.GrantResult result = pass(List.of(
                cycleRoll(LootGrants.ofItem("Fixture_Offcut", 1)),
                cycleRoll(LootGrants.of(null, new String[] {"Fixture_Finds"}, null, null))));

        assertTrue(result.getExpectedItems().isEmpty());
        assertEquals(result.getDropListItems(), result.getFoundItems(),
                "with no Expected roll anywhere, every landed item is a find: the gold row, as before");
    }

    @Test
    void theShippedSawmillTables_authorNoExpectedRoll_soTheSawmillDoesNotDrift() throws IOException {
        try (Stream<Path> files = Files.list(SHIPPED_LOOTABLES)) {
            List<Path> sawmill = files.filter(p -> p.getFileName().toString().startsWith("Sawmill")).toList();
            assertFalse(sawmill.isEmpty(), "the jar ships the Sawmill's tables under " + SHIPPED_LOOTABLES);
            for (Path file : sawmill) {
                LootableAsset table = LootableAsset.CODEC.decodeAndInheritJsonAsset(
                        RawJsonReader.fromJsonString(Files.readString(file, StandardCharsets.UTF_8)), null,
                        new AssetExtraInfo<>(new AssetExtraInfo.Data(LootableAsset.class,
                                file.getFileName().toString().replace(".json", ""), null)));
                for (Roll roll : table.rollsOrEmpty()) {
                    assertFalse(roll.isExpected(), file.getFileName()
                            + " authors an Expected roll: the Sawmill's find rows would stop reading as finds");
                }
            }
        }
    }
}
