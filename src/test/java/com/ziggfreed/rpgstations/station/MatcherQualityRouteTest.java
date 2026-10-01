package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.codec.Vec3i;
import com.ziggfreed.common.validation.Finding;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.ProtectListAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;

/**
 * The shared input matcher's {@code Quality} route: one or many native quality ids, matched without
 * regard to case against the quality the material carries, composing with the other routes exactly
 * as they compose with each other (match = ANY route, the {@code Except} hole carved out after) at
 * every site that asks the one rule: acceptance ({@link StationCustody#accepts}), the hole alone
 * ({@link StationCustody#exceptRefuses}), the protect-list ({@link StationCustody#isProtected}), the
 * fallback's gear filter ({@link StationFallbackRoutes#inFilter}) and action selection
 * ({@link ActionResolver#selectActionsByFamily}). A block carries no stack quality, so a block
 * matcher's {@code Quality} route never matches, and the validator says so; it also warns on an id
 * no loaded quality answers to. Every fixture is authored here.
 */
class MatcherQualityRouteTest {

    private static final Map<String, String[]> WEAPON = Map.of("Type", new String[] {"Weapon"});

    // ==================== the route itself ====================

    @Test
    void theQualityRoute_matchesAnyAuthoredId_withoutRegardToCase() {
        ActionInput dev = ActionInput.ofQuality("Fixture_Developer", "Fixture_Debug");
        assertTrue(StationCustody.accepts(dev, "Fixture_Sword", null, WEAPON, "Weapon", "fixture_developer"));
        assertTrue(StationCustody.accepts(dev, "Fixture_Sword", null, WEAPON, "Weapon", "FIXTURE_DEBUG"),
                "any one of the authored ids");
        assertFalse(StationCustody.accepts(dev, "Fixture_Sword", null, WEAPON, "Weapon", "Fixture_Common"));
        assertFalse(StationCustody.accepts(dev, "Fixture_Sword", null, WEAPON, "Weapon", null),
                "a material with no quality reading (a block, an unreadable stack) matches no Quality route");
        assertFalse(StationCustody.accepts(dev, "Fixture_Sword", null, WEAPON, "Weapon"),
                "the quality-less overload reads no quality");
    }

    @Test
    void aQualityOnlyMatcher_isARoute_notACatchAll() {
        ActionInput dev = ActionInput.ofQuality("Fixture_Developer");
        assertFalse(dev.isCatchAll(), "a matcher authoring only Quality authors a route");
        assertTrue(dev.hasQualityRoute());
        assertTrue(ActionInput.ofQuality(" ", null).isCatchAll(), "blank ids author no route");
        assertFalse(StationCustody.qualityMatches(new String[] {""}, ""), "blank against blank is no match");
    }

    @Test
    void qualityComposesWithTheOtherRoutes_matchIsAny() {
        ActionInput either = ActionInput.of("Fixture_Grimoire", null, null, null,
                new String[] {"Fixture_Developer"}, null);
        assertTrue(StationCustody.accepts(either, "Fixture_Grimoire", null, null, null, "Fixture_Common"),
                "the ItemId route alone is enough");
        assertTrue(StationCustody.accepts(either, "Fixture_Sword", null, WEAPON, "Weapon", "Fixture_Developer"),
                "the Quality route alone is enough");
        assertFalse(StationCustody.accepts(either, "Fixture_Sword", null, WEAPON, "Weapon", "Fixture_Common"));
    }

    @Test
    void aQualityExcept_carvesItsHole_outOfARouteSetAndACatchAllAlike() {
        ActionInput weaponsButDev = ActionInput.of(null, null, WEAPON, null,
                ActionInput.ofQuality("Fixture_Developer"));
        assertTrue(StationCustody.accepts(weaponsButDev, "Fixture_Sword", null, WEAPON, "Weapon", "Fixture_Rare"));
        assertFalse(StationCustody.accepts(weaponsButDev, "Fixture_Sword", null, WEAPON, "Weapon",
                "fixture_developer"), "the hole refuses what the routes accept");
        assertTrue(StationCustody.exceptRefuses(weaponsButDev, "Fixture_Sword", null, WEAPON, "Weapon",
                "Fixture_Developer"));

        ActionInput anythingButDev = ActionInput.of(null, null, null, null, ActionInput.ofQuality("Fixture_Developer"));
        assertTrue(anythingButDev.isCatchAll() && anythingButDev.hasExcept());
        assertTrue(StationCustody.accepts(anythingButDev, "Fixture_Rock", null, null, null, "Fixture_Common"));
        assertFalse(StationCustody.accepts(anythingButDev, "Fixture_Rock", null, null, null, "Fixture_Developer"));
    }

    // ==================== every site asks the same rule ====================

    @Test
    void theProtectList_refusesAQualityEntry_onlyWhereItIsScoped() {
        List<ProtectListAsset> lists = List.of(ProtectListAsset.of("fixture_tables",
                new ActionInput[] {ActionInput.ofQuality("Fixture_Developer")}, new String[] {"Fixture_Table"}, null));
        assertTrue(StationCustody.isProtected(lists, "fixture_table", "Unmake", "Fixture_Sword", null, WEAPON,
                "Weapon", "FIXTURE_DEVELOPER"));
        assertFalse(StationCustody.isProtected(lists, "fixture_table", "Unmake", "Fixture_Sword", null, WEAPON,
                "Weapon", "Fixture_Rare"));
        assertFalse(StationCustody.isProtected(lists, "fixture_mill", "Mill", "Fixture_Sword", null, WEAPON,
                "Weapon", "Fixture_Developer"), "a scoped file applies only at its stations");
    }

    @Test
    void theFallbackFilter_andActionSelection_answerAsTheOneRuleDoes() {
        ActionInput gearButDev = ActionInput.of(null, null, WEAPON, null, ActionInput.ofQuality("Fixture_Developer"));
        StationAsset.Fallback fallback = StationAsset.Fallback.of(gearButDev, null,
                StationAsset.Fallback.EssenceOnly.of(null));
        for (String quality : new String[] {"Fixture_Rare", "Fixture_Developer", null}) {
            assertEquals(StationCustody.accepts(gearButDev, "Fixture_Sword", null, WEAPON, "Weapon", quality),
                    StationFallbackRoutes.inFilter(fallback, "Fixture_Sword", null, WEAPON, "Weapon", quality),
                    "the gear filter is the one rule, quality " + quality);
        }
        assertFalse(StationFallbackRoutes.inFilter(fallback, "Fixture_Sword", null, WEAPON, "Weapon",
                "Fixture_Developer"));

        StationAsset station = StationAsset.of("fixture_table", null,
                ActionDef.of("Dev_Only").withSelect(ActionInput.ofQuality("Fixture_Developer")),
                ActionDef.of("Anything"));
        assertEquals(List.of("Dev_Only", "Anything"), ActionResolver.selectActionsByFamily(station, "Fixture_Sword",
                null, WEAPON, "Weapon", "fixture_developer"));
        assertEquals(List.of("Anything"), ActionResolver.selectActionsByFamily(station, "Fixture_Sword",
                null, WEAPON, "Weapon", "Fixture_Rare"));
        assertEquals(List.of("Anything"), ActionResolver.selectActionsByFamily(station, "Fixture_Sword",
                null, WEAPON, "Weapon"), "the quality-less form reads no quality");
    }

    @Test
    void aBlock_carriesNoQuality_soABlockMatchersQualityRouteNeverMatches() {
        Function<String, String[]> families = id -> new String[0];
        Function<String, Map<String, String[]>> tags = id -> Map.of();
        assertFalse(StationCustody.blockSocketMatches("Fixture_Stone", ActionInput.ofQuality("Fixture_Developer"),
                families, tags));
        assertTrue(StationCustody.blockSocketMatches("Fixture_Stone",
                ActionInput.of(null, null, null, null, ActionInput.ofQuality("Fixture_Developer")), families, tags),
                "a Quality hole on a block matcher refuses nothing");
    }

    // ==================== the validator ====================

    private static StationAsset station(String body) throws Exception {
        return StationAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(body),
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StationAsset.class, "fixture_table", null)));
    }

    private static Map<String, Long> codes(List<Finding> findings) {
        return findings.stream().collect(Collectors.groupingBy(Finding::code, Collectors.counting()));
    }

    @Test
    void theValidator_warnsOnAnUnknownQualityId_atEverySite_holesIncluded() throws Exception {
        StationAsset s = station("{ \"Actions\": [ { \"Id\": \"Unmake\","
                + " \"Select\": { \"Quality\": [\"Fixture_Developer\", \"Fixture_Typo_A\"] },"
                + " \"Custody\": { \"MaxQuantity\": 1, \"Input\": { \"Except\": { \"Quality\": [\"Fixture_Typo_B\"] } } },"
                + " \"Recipe\": { \"Fallback\": { \"Input\": { \"Quality\": [\"fixture_developer\"] },"
                + " \"EssenceOnly\": {} } } } ] }");
        List<ProtectListAsset> lists = List.of(ProtectListAsset.of("fixture_list",
                new ActionInput[] {ActionInput.ofQuality("Fixture_Typo_C")}, null, null));
        Set<String> known = Set.of("fixture_developer");
        List<Finding> out = StationValidator.checkMatcherQualities(List.of(s), List.of(), List.of(), lists, List.of(),
                id -> known.contains(id.toLowerCase(Locale.ROOT)));
        assertEquals(Map.of("QUALITY_UNKNOWN", 3L), codes(out),
                "the Select typo, the Custody.Input hole's typo and the protect-list typo; known ids pass in any case");
        assertTrue(out.stream().anyMatch(f -> f.message().contains("Custody.Input.Except.Quality 'Fixture_Typo_B'")));
    }

    @Test
    void theValidator_warnsThatAQualityRouteNeverMatchesABlock() throws Exception {
        StationAsset s = station("{ \"Actions\": [ { \"Id\": \"Cook\", \"Custody\": { \"Sockets\": {"
                + " \"fire\": { \"Block\": { \"Match\": { \"ItemId\": \"Fixture_Fire\", \"Quality\": [\"Fixture_Developer\"] } } },"
                + " \"pot\": { \"Item\": { \"Match\": { \"Quality\": [\"Fixture_Developer\"] } }, \"MaxQuantity\": 1 } } } } ] }");
        StructurePatternAsset pattern = StructurePatternAsset.of("fixture_ring", null, null,
                StructurePatternAsset.Activate.of("Fixture_Station", null), new StructurePatternAsset.Cell[] {
                        StructurePatternAsset.Cell.of(Vec3i.of(0, 0, 0), ActionInput.of("Fixture_Stone", null, null, null),
                                null, Boolean.TRUE),
                        StructurePatternAsset.Cell.of(Vec3i.of(1, 0, 0), ActionInput.ofQuality("Fixture_Developer"),
                                null, null)}, null, null);
        List<Finding> out = StationValidator.checkMatcherQualities(List.of(s), List.of(), List.of(), List.of(),
                List.of(pattern), id -> true);
        assertEquals(Map.of("QUALITY_ON_BLOCK", 2L), codes(out),
                "the block socket and the structure cell; the item socket's Quality is a real route");
    }

    /** One station whose one single-item socket authors {@code match} (a JSON object). */
    private static StationAsset socketMatching(String match) throws Exception {
        return station("{ \"Actions\": [ { \"Id\": \"Unmake\", \"Custody\": { \"Sockets\": {"
                + " \"piece\": { \"Item\": { \"Match\": " + match + " }, \"MaxQuantity\": 1 } } } } ] }");
    }

    /** The socket-resolution pass over {@code station}, with no live item, family or tag at all. */
    private static Map<String, Long> unmatchedCodes(StationAsset station) {
        return codes(StationValidator.checkCustodyInputsResolve(List.of(station), List.of(), Set.of(),
                tags -> false, itemId -> false));
    }

    @Test
    void aSocketMatchWithAQualityRoute_isNeverWarnedUnmatched_byItsOtherRoutes() throws Exception {
        assertFalse(unmatchedCodes(socketMatching("{ \"ItemId\": \"Missing\", \"Quality\": [\"Developer\"] }"))
                .containsKey("SOCKET_MATCH_UNMATCHED"),
                "the Quality route is read off the stack, so it may resolve whatever the id route does");
        assertFalse(unmatchedCodes(socketMatching("{ \"ItemId\": \"Missing\", \"Function\": \"Weapon\" }"))
                .containsKey("SOCKET_MATCH_UNMATCHED"), "a Function route counts the same way");
        assertEquals(1L, unmatchedCodes(socketMatching("{ \"ItemId\": \"Missing\" }"))
                .getOrDefault("SOCKET_MATCH_UNMATCHED", 0L),
                "an id route alone that resolves nothing is still warned");
    }
}
