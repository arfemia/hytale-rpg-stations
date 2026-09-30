package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.validation.Finding;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ProtectListAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The server-wide protect-list as a store: every file adds to one list, a scoped file applies only
 * where it is scoped, and a later layer's file with the same id takes the earlier one's entries back.
 * Each layer is decoded through the real codec, the way the asset store hands it over, and folded
 * the way the plugin folds it. Also the validator's findings on a file. Fixtures are authored here.
 */
class ProtectListCatalogTest {

    private static final Map<String, String[]> TROPHY = Map.of("Type", new String[] {"Trophy"});

    private static ProtectListAsset file(String name, String body) throws Exception {
        return ProtectListAsset.CODEC.decodeAndInheritJsonAsset(RawJsonReader.fromJsonString(body), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(ProtectListAsset.class, name, null)));
    }

    /** One loaded layer, keyed the way the plugin keys it (lowercased id). */
    private static Map<String, ProtectListAsset> layer(ProtectListAsset... files) {
        return Arrays.stream(files).collect(Collectors.toMap(ProtectListAsset::getId, f -> f));
    }

    private static boolean protectedAt(String station, String action, String itemId, Map<String, String[]> tags) {
        return ProtectListCatalog.getInstance().protects(station, action, itemId, null, tags, null);
    }

    @BeforeEach
    @AfterEach
    void emptyTheCatalog() {
        ProtectListCatalog.getInstance().fold(Map.of(), true);
    }

    @Test
    void anEmptyStore_protectsNothing() {
        assertFalse(protectedAt("fixture_table", "Unmake", "Fixture_Grimoire", TROPHY));
    }

    @Test
    void twoFiles_addUp_neitherReplacesTheOther() throws Exception {
        ProtectListCatalog.getInstance().fold(layer(
                file("Jar_Trophies", "{ \"Protects\": { \"ItemId\": \"Fixture_Hatchet\" } }")), false);
        ProtectListCatalog.getInstance().fold(layer(
                file("Pack_Grimoires", "{ \"Protects\": { \"Tags\": { \"Type\": [\"Trophy\"] } } }")), false);

        assertTrue(protectedAt("fixture_table", "Unmake", "Fixture_Hatchet", null), "the jar's file still counts");
        assertTrue(protectedAt("fixture_table", "Unmake", "Fixture_Grimoire", TROPHY), "the pack's file counts too");
        assertFalse(protectedAt("fixture_table", "Unmake", "Fixture_Sword", null));
    }

    @Test
    void aScopedFile_appliesOnlyWhereItIsScoped() throws Exception {
        ProtectListCatalog.getInstance().fold(layer(
                file("Table_Only", "{ \"Stations\": [\"Fixture_Table\"], \"Actions\": [\"Unmake\"],"
                        + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }")), false);

        assertTrue(protectedAt("fixture_table", "unmake", "Fixture_Grimoire", null));
        assertFalse(protectedAt("fixture_table", "Mill", "Fixture_Grimoire", null), "another action at the station");
        assertFalse(protectedAt("fixture_mill", "Unmake", "Fixture_Grimoire", null), "another station");
    }

    @Test
    void aSameIdFile_inALaterLayer_takesTheEarliersEntriesBack() throws Exception {
        ProtectListCatalog.getInstance().fold(layer(
                file("Trophies", "{ \"Protects\": [ { \"ItemId\": \"Fixture_Hatchet\" },"
                        + " { \"ItemId\": \"Fixture_Grimoire\" } ] }")), false);
        assertTrue(protectedAt("fixture_table", "Unmake", "Fixture_Hatchet", null));

        // The owner's pack ships the same file name, keeping the grimoire and dropping the hatchet.
        ProtectListCatalog.getInstance().fold(layer(
                file("trophies", "{ \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }")), false);
        assertFalse(protectedAt("fixture_table", "Unmake", "Fixture_Hatchet", null),
                "overriding the file by id is how an entry is taken back");
        assertTrue(protectedAt("fixture_table", "Unmake", "Fixture_Grimoire", null));
    }

    @Test
    void anEntrysHole_andARoutelessEntry_protectNothingTheyShouldNot() throws Exception {
        ProtectListCatalog.getInstance().fold(layer(
                file("Holes", "{ \"Protects\": [ { },"
                        + " { \"Tags\": { \"Type\": [\"Trophy\"] }, \"Except\": { \"ItemId\": \"Fixture_Plaque\" } } ] }")),
                false);

        assertTrue(protectedAt("fixture_table", "Unmake", "Fixture_Grimoire", TROPHY));
        assertFalse(protectedAt("fixture_table", "Unmake", "Fixture_Plaque", TROPHY), "the entry's own hole");
        assertFalse(protectedAt("fixture_table", "Unmake", "Fixture_Sword", null),
                "an entry with no route is never read as everything");
    }

    // ==================== the validator ====================

    private static StationAsset station(String id, String... actionIds) {
        ActionDef[] actions = new ActionDef[actionIds.length];
        for (int i = 0; i < actionIds.length; i++) {
            actions[i] = ActionDef.of(actionIds[i]);
        }
        return StationAsset.of(id, StationAsset.Identity.of("rpgstations.station." + id + ".name",
                "rpgstations.station." + id + ".desc", "Fixture_Icon"), actions);
    }

    private static Set<String> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).collect(Collectors.toSet());
    }

    @Test
    void theValidator_warnsOnUnknownScopeIds_andOnAFileThatProtectsNothing() throws Exception {
        List<StationAsset> stations = List.of(station("fixture_table", "Unmake"), station("fixture_mill", "Mill"));

        Set<String> clean = codes(StationValidator.validateProtectLists(List.of(
                file("Clean", "{ \"Stations\": [\"Fixture_Table\"], \"Actions\": [\"unmake\"],"
                        + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }")), stations, id -> true));
        assertTrue(clean.isEmpty(), clean.toString());

        Set<String> wrong = codes(StationValidator.validateProtectLists(List.of(
                file("Wrong", "{ \"Stations\": [\"Fixture_Nowhere\"], \"Actions\": [\"Mill\"],"
                        + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }")), stations, id -> true));
        assertTrue(wrong.contains("PROTECT_LIST_UNKNOWN_STATION"), wrong.toString());
        assertTrue(wrong.contains("PROTECT_LIST_UNKNOWN_ACTION"), "Mill is not an action of any station in scope: "
                + wrong);

        Set<String> empty = codes(StationValidator.validateProtectLists(List.of(
                file("Empty", "{ \"Protects\": [ { } ] }"), file("Blank", "{ }")), stations, id -> true));
        assertTrue(empty.contains("PROTECT_LIST_EMPTY"), empty.toString());

        Set<String> mixed = codes(StationValidator.validateProtectLists(List.of(
                file("Mixed", "{ \"Protects\": [ { \"ItemId\": \"Fixture_Typo\" }, { } ] }")),
                stations, id -> !id.equals("Fixture_Typo")));
        assertTrue(mixed.contains("PROTECT_LIST_CATCH_ALL"), mixed.toString());
        assertTrue(mixed.contains("PROTECT_LIST_UNKNOWN_ITEM"), mixed.toString());
    }

    @Test
    void theStructuralPass_skipsTheScopeReads() throws Exception {
        Set<String> structural = codes(StationValidator.validateProtectLists(List.of(
                file("Wrong", "{ \"Stations\": [\"Fixture_Nowhere\"],"
                        + " \"Protects\": { \"ItemId\": \"Fixture_Grimoire\" } }")), null, id -> true));
        assertFalse(structural.contains("PROTECT_LIST_UNKNOWN_STATION"), structural.toString());
    }
}
