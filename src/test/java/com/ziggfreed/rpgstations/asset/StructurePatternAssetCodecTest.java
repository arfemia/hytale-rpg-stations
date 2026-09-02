package com.ziggfreed.rpgstations.asset;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StructurePatternAsset} decode + resolution semantics: the group shapes, the
 * exactly-one-of cell routes, the anchor-cell defaulting, and the revert-block fallback. Every
 * fixture is authored HERE in deliberately neutral vocabulary (a ring of alpha blocks around a
 * beta anchor) - no shipped content is read.
 */
public class StructurePatternAssetCodecTest {

    private static StructurePatternAsset decode(String json) throws Exception {
        return StructurePatternAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(json), null,
                new AssetExtraInfo<>(new AssetExtraInfo.Data(StructurePatternAsset.class, "FixtureRing", null)));
    }

    private static final String FULL_RING = """
            {
              "Identity": { "NameKey": "fixture.ring.name", "DescKey": "fixture.ring.desc" },
              "Rotate": { "Yaw90": true, "Mirror": false },
              "Activate": { "Block": "Fixture_Station_Beta", "RevertBlock": "Fixture_Beta_Cold" },
              "Cells": [
                { "Offset": { "X": 0, "Y": 0, "Z": 0 }, "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true },
                { "Offset": { "X": 1, "Y": 0, "Z": 0 }, "Block": { "ResourceTypeId": "Alpha" } },
                { "Offset": { "X": -1, "Y": 0, "Z": 0 }, "Block": { "ResourceTypeId": "Alpha" } },
                { "Offset": { "X": 0, "Y": 1, "Z": 0 }, "Empty": true }
              ],
              "Requires": { "Permission": "fixture.build.ring" },
              "Moments": {
                "$Comment": "editorial keys are legal inside this InheritMapCodec map",
                "activated": { "Sounds": ["Fixture_Chime"] },
                "broken": { "Sounds": ["Fixture_Crumble"] }
              }
            }
            """;

    @Test
    void fullPattern_decodesEveryGroup() throws Exception {
        StructurePatternAsset a = decode(FULL_RING);

        assertEquals("fixturering", a.getId(), "the id is the lowercased filename");
        assertEquals("fixture.ring.name", a.getIdentity().getNameKey());
        assertTrue(a.getRotate().effectiveYaw90());
        assertFalse(a.getRotate().effectiveMirror());
        assertEquals("Fixture_Station_Beta", a.getActivate().getBlock());
        assertEquals(4, a.getCells().length);
        assertEquals("fixture.build.ring", a.getRequires().getPermission());
        assertNotNull(a.moment("activated"));
        assertNotNull(a.moment("Broken"), "moment lookup is case-insensitive");
        assertNull(a.moment("unheard_of"));
    }

    @Test
    void anchorCell_isTheAuthoredIsAnchorCell_whereverItSits() throws Exception {
        StructurePatternAsset a = decode("""
                { "Cells": [
                    { "Offset": { "X": 1 }, "Block": { "ResourceTypeId": "Alpha" } },
                    { "Offset": { "X": 2 }, "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true }
                ] }
                """);
        assertEquals(1, a.anchorCellIndex());
    }

    @Test
    void noAuthoredAnchor_defaultsToTheOriginCell() throws Exception {
        StructurePatternAsset a = decode("""
                { "Cells": [
                    { "Offset": { "X": 1 }, "Block": { "ResourceTypeId": "Alpha" } },
                    { "Offset": { "X": 0, "Y": 0, "Z": 0 }, "Block": { "ItemId": "Fixture_Beta_Cold" } }
                ] }
                """);
        assertEquals(1, a.anchorCellIndex(), "the cell at offset (0,0,0) stands in for an unauthored anchor");
    }

    @Test
    void noAuthoredAnchorAndNoOriginCell_fallsBackToCellZero() throws Exception {
        StructurePatternAsset a = decode("""
                { "Cells": [
                    { "Offset": { "X": 1 }, "Block": { "ItemId": "Fixture_Beta_Cold" } },
                    { "Offset": { "X": 2 }, "Block": { "ResourceTypeId": "Alpha" } }
                ] }
                """);
        assertEquals(0, a.anchorCellIndex());
    }

    @Test
    void cellRoutes_areExactlyOneOf() throws Exception {
        StructurePatternAsset a = decode("""
                { "Cells": [
                    { "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true },
                    { "Offset": { "X": 1 }, "Block": { "ItemId": "Fixture_Alpha" }, "Empty": true },
                    { "Offset": { "X": 2 } }
                ] }
                """);
        assertTrue(a.getCells()[0].hasExactlyOneRoute());
        assertFalse(a.getCells()[1].hasExactlyOneRoute(), "both routes authored is malformed");
        assertFalse(a.getCells()[2].hasExactlyOneRoute(), "neither route authored is malformed");
    }

    @Test
    void emptyCell_readsAsAir_notAsABlockMatcher() throws Exception {
        StructurePatternAsset a = decode("""
                { "Cells": [
                    { "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true },
                    { "Offset": { "Y": 1 }, "Empty": true }
                ] }
                """);
        assertTrue(a.getCells()[1].isEmptyCell());
        assertNull(a.getCells()[1].getBlock());
    }

    @Test
    void revertBlock_authoredWins_elseTheAnchorCellsOwnId() throws Exception {
        StructurePatternAsset authored = decode(FULL_RING);
        assertEquals("Fixture_Beta_Cold", authored.effectiveRevertBlock());

        StructurePatternAsset defaulted = decode("""
                { "Activate": { "Block": "Fixture_Station_Beta" },
                  "Cells": [ { "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true } ] }
                """);
        assertEquals("Fixture_Beta_Cold", defaulted.effectiveRevertBlock(),
                "an unauthored RevertBlock falls back to the anchor cell's own block id");

        StructurePatternAsset none = decode("""
                { "Cells": [ { "Block": { "ResourceTypeId": "Alpha" }, "IsAnchor": true } ] }
                """);
        assertNull(none.effectiveRevertBlock(),
                "a family-matched anchor with no RevertBlock has nothing to revert to");
    }

    @Test
    void rotateDefaults_yawOnMirrorOff() throws Exception {
        StructurePatternAsset bare = decode("""
                { "Rotate": {},
                  "Cells": [ { "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true } ] }
                """);
        assertTrue(bare.getRotate().effectiveYaw90());
        assertFalse(bare.getRotate().effectiveMirror());
    }

    @Test
    void offsetAxes_defaultToZeroPerAxis() throws Exception {
        StructurePatternAsset a = decode("""
                { "Cells": [ { "Offset": { "Y": 2 }, "Block": { "ItemId": "Fixture_Beta_Cold" }, "IsAnchor": true } ] }
                """);
        assertEquals(0, a.getCells()[0].offsetX());
        assertEquals(2, a.getCells()[0].offsetY());
        assertEquals(0, a.getCells()[0].offsetZ());
    }
}
