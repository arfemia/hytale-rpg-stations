package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.codec.Vec3i;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;
import com.ziggfreed.rpgstations.station.PatternCatalog.CellOffset;
import com.ziggfreed.rpgstations.station.PatternCatalog.CompiledPattern;
import com.ziggfreed.rpgstations.station.PatternCells.CellMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DETECT vs HOLD compile ({@link PatternCatalog#compile}) and the pure cell matcher
 * ({@link PatternCells}): anchor substitution, Block-socket cell exclusion, variant expansion, and
 * the identity routes. All fixtures are neutral vocabulary (alpha ring, beta anchor) authored by
 * this test.
 */
public class PatternCompileTest {

    // ==================== fixtures ====================

    private static StructurePatternAsset ringAsset(Boolean yaw90, Boolean mirror) {
        return StructurePatternAsset.of("fixturering", null,
                StructurePatternAsset.Rotate.of(yaw90, mirror),
                StructurePatternAsset.Activate.of("Fixture_Station_Beta", null),
                new StructurePatternAsset.Cell[] {
                        cell(0, 0, 0, ActionInput.of("Fixture_Beta_Cold", null, null, null), null, true),
                        cell(1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null),
                        cell(-1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null),
                        cell(0, 1, 0, null, true, null)},
                null, null);
    }

    private static StructurePatternAsset.Cell cell(int x, int y, int z, ActionInput block,
            Boolean empty, Boolean isAnchor) {
        return StructurePatternAsset.Cell.of(Vec3i.of(x, y, z), block, empty, isAnchor);
    }

    private static final UnaryOperator<String> BASE_ID = id ->
            id.endsWith("_Lit") ? id.substring(0, id.length() - "_Lit".length()) : id;
    private static final Function<String, List<String>> FAMILIES = id ->
            id.startsWith("Fixture_Alpha") ? List.of("Alpha") : List.of();
    private static final Function<String, Map<String, String[]>> TAGS = id ->
            id.startsWith("Fixture_Tagged") ? Map.of("Type", new String[] {"Rock"}) : Map.of();

    private static boolean matches(CellMatcher m, String blockId) {
        return PatternCells.matches(m, blockId, BASE_ID, FAMILIES, TAGS);
    }

    // ==================== DETECT vs HOLD ====================

    @Test
    void detect_keepsTheAuthoredAnchorMatcher_andHoldSubstitutesTheActivatedBlock() {
        CompiledPattern cp = PatternCatalog.compile("FixtureRing", ringAsset(null, null), Set.of(), null);

        assertEquals("fixturering", cp.id());
        assertEquals("Fixture_Beta_Cold", cp.detect().payload(cp.detect().anchorIndex()).itemId(),
                "DETECT tests the anchor as the block that stands there BEFORE activation");
        assertEquals("Fixture_Station_Beta", cp.hold().payload(cp.hold().anchorIndex()).itemId(),
                "HOLD tests the anchor as the ACTIVATED station block");
    }

    @Test
    void hold_excludesBlockSocketCells_butNeverTheAnchor() {
        Set<CellOffset> socketCells = Set.of(new CellOffset(0, 1, 0));
        CompiledPattern cp = PatternCatalog.compile("FixtureRing", ringAsset(null, null), socketCells, null);

        assertEquals(4, cp.detect().cellCount());
        assertEquals(3, cp.hold().cellCount(),
                "the Empty cell above the anchor is a Block-socket target - the pot placed there"
                        + " must not read as the shape breaking");
        // An exclusion set covering the anchor's own offset is ignored for the anchor cell.
        CompiledPattern anchorSafe = PatternCatalog.compile("FixtureRing", ringAsset(null, null),
                Set.of(new CellOffset(0, 0, 0)), null);
        assertEquals(4, anchorSafe.hold().cellCount());
    }

    @Test
    void hold_keepsEmptyCellsThatAreNotSocketTargets() {
        CompiledPattern cp = PatternCatalog.compile("FixtureRing", ringAsset(null, null), Set.of(), null);
        boolean holdHasAirCell = false;
        for (int i = 0; i < cp.hold().cellCount(); i++) {
            holdHasAirCell |= cp.hold().payload(i).empty();
        }
        assertTrue(holdHasAirCell, "a non-socket Empty cell stays part of the standing shape");
    }

    @Test
    void customCoreBlock_activateEqualToAnchor_holdsTheSameId() {
        StructurePatternAsset asset = StructurePatternAsset.of("core", null, null,
                StructurePatternAsset.Activate.of("Fixture_Core", null),
                new StructurePatternAsset.Cell[] {
                        cell(0, 0, 0, ActionInput.of("Fixture_Core", null, null, null), null, true),
                        cell(1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null)},
                null, null);
        CompiledPattern cp = PatternCatalog.compile("core", asset, Set.of(), null);
        assertEquals(cp.detect().payload(cp.detect().anchorIndex()).itemId(),
                cp.hold().payload(cp.hold().anchorIndex()).itemId(),
                "the custom-core style is plain authoring: DETECT and HOLD test the same id");
    }

    // ==================== variant expansion ====================

    @Test
    void variantExpansion_followsTheRotateFlags() {
        assertEquals(4, PatternCatalog.compile("a", ringAsset(null, null), Set.of(), null)
                .detect().variants().size(), "Yaw90 defaults on: four quarter-turns");
        assertEquals(8, PatternCatalog.compile("b", ringAsset(true, true), Set.of(), null)
                .detect().variants().size(), "Mirror doubles the yaw variants");
        assertEquals(1, PatternCatalog.compile("c", ringAsset(false, null), Set.of(), null)
                .detect().variants().size(), "Yaw90 false: the authored orientation only");
    }

    // ==================== the cell matcher's identity routes ====================

    @Test
    void exactIdRoute_matchesTheBaseBehindAStateVariant() {
        CellMatcher m = CellMatcher.exact("Fixture_Beta_Cold");
        assertTrue(matches(m, "Fixture_Beta_Cold"));
        assertTrue(matches(m, "Fixture_Beta_Cold_Lit"), "a state variant folds onto its base");
        assertFalse(matches(m, "Fixture_Other"));
        assertFalse(matches(m, "Empty"));
    }

    @Test
    void familyRoute_matchesAnyMemberOfTheResourceFamily() {
        CellMatcher m = CellMatcher.of(cell(0, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null));
        assertTrue(matches(m, "Fixture_Alpha_Cobble"));
        assertTrue(matches(m, "Fixture_Alpha_Brick"));
        assertFalse(matches(m, "Fixture_Other"));
        assertFalse(matches(m, "Empty"));
    }

    @Test
    void tagRoute_matchesThroughTheItemTags() {
        CellMatcher m = CellMatcher.of(cell(0, 0, 0,
                ActionInput.of(null, null, Map.of("Type", new String[] {"Rock"}), null), null, null));
        assertTrue(matches(m, "Fixture_Tagged_One"));
        assertFalse(matches(m, "Fixture_Other"));
    }

    @Test
    void emptyMatcher_matchesOnlyAir() {
        CellMatcher m = CellMatcher.air();
        assertTrue(matches(m, "Empty"));
        assertTrue(matches(m, "empty"), "the engine key compares case-insensitively");
        assertFalse(matches(m, "Fixture_Alpha_Cobble"));
    }

    @Test
    void routelessMatcher_matchesNothing() {
        CellMatcher m = CellMatcher.of(cell(0, 0, 0, null, null, null));
        assertFalse(matches(m, "Fixture_Alpha_Cobble"));
        assertFalse(matches(m, "Empty"));
    }
}
