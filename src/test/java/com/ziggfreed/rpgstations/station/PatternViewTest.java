package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.rpgstations.api.PatternView;
import com.ziggfreed.rpgstations.api.impl.RpgStationsApiImpl;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;

/**
 * The api {@code patterns()} view over a DECODED fixture: one pattern folded through the real
 * {@link StructurePatternAsset#CODEC} and read back through {@link RpgStationsApiImpl}, pinning
 * the projection's whole contract - anchor-relative offset normalization (the fixture authors a
 * shifted frame on purpose), the per-cell route summary vocabulary, the effective revert-block
 * fallback, and the rotation flags. Fixtures are authored HERE in neutral vocabulary; no shipped
 * content is read.
 */
class PatternViewTest {

    @AfterEach
    void clearCatalog() {
        PatternCatalog.getInstance().clearForTest();
    }

    private static StructurePatternAsset decode(String name, String json) throws Exception {
        return StructurePatternAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(json), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StructurePatternAsset.class, name, null)));
    }

    @Test
    void patternsView_projectsTheDecodedFixture() throws Exception {
        // The frame is deliberately SHIFTED (anchor at (5, 1, 5)): the view must normalize every
        // offset anchor-relative, so the anchor reads (0,0,0) and the ring reads (+/-1, 0, 0).
        StructurePatternAsset asset = decode("FixtureRing", """
                {
                  "Rotate": { "Yaw90": false, "Mirror": true },
                  "Activate": { "Block": "Fixture_Station_Beta" },
                  "Cells": [
                    { "Offset": { "X": 5, "Y": 1, "Z": 5 }, "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true },
                    { "Offset": { "X": 6, "Y": 1, "Z": 5 }, "Block": { "ResourceTypeId": "Alpha" } },
                    { "Offset": { "X": 4, "Y": 1, "Z": 5 }, "Block": { "Tags": { "Type": ["Alpha", "Gamma"], "Family": [] } } },
                    { "Offset": { "X": 5, "Y": 2, "Z": 5 }, "Empty": true }
                  ]
                }
                """);
        PatternCatalog.getInstance().fold(Map.of(asset.getId(), asset), true);

        Collection<PatternView> views = RpgStationsApiImpl.getInstance().patterns();
        assertEquals(1, views.size());
        PatternView view = views.iterator().next();

        assertEquals("fixturering", view.id(), "ids are lowercased at decode");
        assertEquals("Fixture_Station_Beta", view.activateBlock());
        assertEquals("Fixture_Beta_Cold", view.revertBlock(),
                "no authored RevertBlock falls back to the anchor cell's own block id");
        assertFalse(view.rotateYaw90());
        assertTrue(view.rotateMirror());
        assertEquals(4, view.cellCount());

        List<PatternView.CellView> cells = view.cells();
        PatternView.CellView anchor = cells.get(0);
        assertTrue(anchor.anchor());
        assertEquals(0, anchor.offsetX());
        assertEquals(0, anchor.offsetY());
        assertEquals(0, anchor.offsetZ());
        assertEquals(PatternView.ROUTE_ITEM_ID, anchor.route());
        assertEquals("Fixture_Beta_Cold", anchor.value());

        PatternView.CellView family = cells.get(1);
        assertFalse(family.anchor());
        assertEquals(1, family.offsetX());
        assertEquals(0, family.offsetY());
        assertEquals(0, family.offsetZ());
        assertEquals(PatternView.ROUTE_RESOURCE_TYPE, family.route());
        assertEquals("Alpha", family.value());

        PatternView.CellView tagged = cells.get(2);
        assertEquals(-1, tagged.offsetX());
        assertEquals(PatternView.ROUTE_TAGS, tagged.route());
        assertEquals("Type=Alpha|Gamma,Family", tagged.value(),
                "a valued entry reads key=v1|v2, a key-presence entry reads the bare key");

        PatternView.CellView air = cells.get(3);
        assertEquals(0, air.offsetX());
        assertEquals(1, air.offsetY());
        assertEquals(PatternView.ROUTE_EMPTY, air.route());
        assertNull(air.value());
    }

    @Test
    void patternsView_malformedCellReadsAsRouteNone_andCellLessPatternStaysVisible() throws Exception {
        // A both-routes-neither cell (decode-warned) must summarize as None, never pretend to
        // match; a cell-less pattern the compile skips must still be visible to a consumer lint.
        StructurePatternAsset malformed = decode("FixtureOdd", """
                { "Cells": [ { "Offset": { "X": 0 }, "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true },
                             { "Offset": { "X": 1 } } ] }
                """);
        StructurePatternAsset cellLess = decode("FixtureBare", "{ }");
        PatternCatalog.getInstance().fold(
                Map.of(malformed.getId(), malformed, cellLess.getId(), cellLess), true);

        Collection<PatternView> views = RpgStationsApiImpl.getInstance().patterns();
        assertEquals(2, views.size(), "the raw folded set is projected, compiled or not");

        PatternView odd = views.stream().filter(v -> v.id().equals("fixtureodd")).findFirst().orElseThrow();
        assertEquals(PatternView.ROUTE_NONE, odd.cells().get(1).route());
        assertNull(odd.cells().get(1).value());
        assertNull(odd.activateBlock(), "no Activate group = no activation block");

        PatternView bare = views.stream().filter(v -> v.id().equals("fixturebare")).findFirst().orElseThrow();
        assertEquals(0, bare.cellCount());
        assertTrue(bare.cells().isEmpty());
        assertTrue(bare.rotateYaw90(), "reader defaults hold with no Rotate group");
        assertFalse(bare.rotateMirror());
        assertNull(bare.revertBlock(), "no anchor block to fall back to");
    }
}
