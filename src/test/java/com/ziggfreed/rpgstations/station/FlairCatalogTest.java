package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.FlairAsset;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * Exercises {@link FlairCatalog#effectiveFlairsFor} - the ONE merge point (design section 9.6,
 * phase 2 leg F) between a station's own inline {@code Flairs} and every applicable standalone
 * {@link FlairAsset}. The singleton is reset after every test (mirrors the
 * {@code FlairUnlockRegistryImpl.resetForTests} discipline other engine seams use).
 */
public class FlairCatalogTest {

    private static final String STATION_ID = "sawmill";

    @AfterEach
    void resetCatalog() {
        FlairCatalog.getInstance().fold(Map.of(), true);
    }

    private static StationAsset stationWithInlineFlairs(Map<String, StationAsset.Flair> flairs) {
        return StationAsset.of(STATION_ID, null, null, null, null, null, null, null, null, null, null, flairs);
    }

    @Test
    void noInlineFlairs_noFlairAssets_effectiveMapIsEmpty() {
        StationAsset a = stationWithInlineFlairs(null);
        assertTrue(FlairCatalog.getInstance().effectiveFlairsFor(STATION_ID, a).isEmpty());
    }

    @Test
    void nullAsset_stillReturnsApplicableFlairAssets() {
        FlairCatalog.getInstance().fold(Map.of("golden_saw",
                FlairAsset.of("golden_saw", null, Map.of("swing", Presentation.ofSound("SFX_Golden")))), false);

        Map<String, Map<String, Presentation>> result = FlairCatalog.getInstance().effectiveFlairsFor(STATION_ID, null);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("golden_saw"));
    }

    @Test
    void inlineOnly_isPreserved() {
        StationAsset a = stationWithInlineFlairs(Map.of("inline_flair",
                StationAsset.Flair.of(Map.of("cycle", Presentation.ofSound("SFX_Inline")))));

        Map<String, Map<String, Presentation>> result = FlairCatalog.getInstance().effectiveFlairsFor(STATION_ID, a);

        assertEquals(1, result.size());
        assertEquals("SFX_Inline", result.get("inline_flair").get("cycle").getSound());
    }

    @Test
    void applicableFlairAsset_unionsWithInline() {
        StationAsset a = stationWithInlineFlairs(Map.of("inline_flair",
                StationAsset.Flair.of(Map.of("cycle", Presentation.ofSound("SFX_Inline")))));
        FlairCatalog.getInstance().fold(Map.of("golden_saw",
                FlairAsset.of("golden_saw", new String[]{STATION_ID},
                        Map.of("swing", Presentation.ofSound("SFX_Golden")))), false);

        Map<String, Map<String, Presentation>> result = FlairCatalog.getInstance().effectiveFlairsFor(STATION_ID, a);

        assertEquals(2, result.size());
        assertEquals("SFX_Inline", result.get("inline_flair").get("cycle").getSound());
        assertEquals("SFX_Golden", result.get("golden_saw").get("swing").getSound());
    }

    @Test
    void flairAssetRestrictedToAnotherStation_isExcluded() {
        FlairCatalog.getInstance().fold(Map.of("golden_saw",
                FlairAsset.of("golden_saw", new String[]{"anvil"},
                        Map.of("swing", Presentation.ofSound("SFX_Golden")))), false);

        Map<String, Map<String, Presentation>> result = FlairCatalog.getInstance()
                .effectiveFlairsFor(STATION_ID, stationWithInlineFlairs(null));

        assertFalse(result.containsKey("golden_saw"));
    }

    @Test
    void flairAssetNullStations_appliesToEveryStation() {
        FlairCatalog.getInstance().fold(Map.of("golden_saw",
                FlairAsset.of("golden_saw", null, Map.of("swing", Presentation.ofSound("SFX_Golden")))), false);

        Map<String, Map<String, Presentation>> result = FlairCatalog.getInstance()
                .effectiveFlairsFor("some_other_station", stationWithInlineFlairs(null));

        assertTrue(result.containsKey("golden_saw"));
    }

    @Test
    void flairAssetSameIdAsInline_foldsOntoIt_assetWins() {
        StationAsset a = stationWithInlineFlairs(Map.of("golden_saw",
                StationAsset.Flair.of(Map.of("cycle", Presentation.ofSound("SFX_Inline_Golden")))));
        FlairCatalog.getInstance().fold(Map.of("golden_saw",
                FlairAsset.of("golden_saw", null, Map.of("cycle", Presentation.ofSound("SFX_Asset_Golden")))), false);

        Map<String, Map<String, Presentation>> result = FlairCatalog.getInstance().effectiveFlairsFor(STATION_ID, a);

        assertEquals(1, result.size());
        assertEquals("SFX_Asset_Golden", result.get("golden_saw").get("cycle").getSound(),
                "the FlairAsset entry folds ONTO the inline one for the same flair id - it wins");
    }
}
