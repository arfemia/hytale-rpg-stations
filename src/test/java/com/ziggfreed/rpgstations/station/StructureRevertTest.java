package com.ziggfreed.rpgstations.station;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.codec.Vec3i;
import com.ziggfreed.common.world.pattern.BlockReader;
import com.ziggfreed.common.world.pattern.CellPredicate;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;
import com.ziggfreed.rpgstations.station.PatternCatalog.CompiledPattern;
import com.ziggfreed.rpgstations.station.PatternCells.CellMatcher;
import com.ziggfreed.rpgstations.station.StationStructures.HoldCandidate;
import com.ziggfreed.rpgstations.station.StationStructures.SwapDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The break side's pure decision path: which (pattern, variant, cell) candidates a broken block
 * probes, whether a standing build survives its HOLD re-walk, the broken-position overlay, the
 * swap-back decision's rotation carry-through, and the {@code STRUCTURE_LOST} stop family
 * classification. Neutral fixture vocabulary; nothing touches a live world or asset map.
 */
public class StructureRevertTest {

    private static final UnaryOperator<String> BASE_ID = UnaryOperator.identity();
    private static final Function<String, List<String>> FAMILIES = id ->
            id.startsWith("Fixture_Alpha") ? List.of("Alpha") : List.of();
    private static final Function<String, Map<String, String[]>> TAGS = id -> Map.of();
    private static final CellPredicate<CellMatcher> PREDICATE =
            PatternCells.predicate(BASE_ID, FAMILIES, TAGS);

    private static CompiledPattern ring() {
        StructurePatternAsset asset = StructurePatternAsset.of("ring", null, null,
                StructurePatternAsset.Activate.of("Fixture_Station_Beta", "Fixture_Beta_Cold"),
                new StructurePatternAsset.Cell[] {
                        cell(0, 0, 0, ActionInput.of("Fixture_Beta_Cold", null, null, null), null, true),
                        cell(1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null),
                        cell(-1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null),
                        cell(0, 1, 0, null, true, null)},
                null, null);
        return PatternCatalog.compile("ring", asset, Set.of(), null);
    }

    private static StructurePatternAsset.Cell cell(int x, int y, int z, ActionInput block,
            Boolean empty, Boolean isAnchor) {
        return StructurePatternAsset.Cell.of(Vec3i.of(x, y, z), block, empty, isAnchor);
    }

    private static final class StubWorld implements BlockReader {
        private final Map<String, String> blocks = new HashMap<>();

        StubWorld put(int x, int y, int z, String blockId) {
            blocks.put(x + ":" + y + ":" + z, blockId);
            return this;
        }

        @Override
        public String blockItemIdAt(int x, int y, int z) {
            return blocks.getOrDefault(x + ":" + y + ":" + z, PatternCells.EMPTY_KEY);
        }
    }

    /** The ACTIVATED standing build: the station block at the anchor, the ring around it. */
    private static StubWorld standingBuild() {
        return new StubWorld()
                .put(0, 0, 0, "Fixture_Station_Beta")
                .put(1, 0, 0, "Fixture_Alpha_Cobble")
                .put(-1, 0, 0, "Fixture_Alpha_Brick");
    }

    // ==================== the break probe ====================

    @Test
    void aFamilyMatchedRingBlock_probesEveryVariantOfItsCell() {
        CompiledPattern cp = ring();
        List<HoldCandidate> candidates = StationStructures.holdCandidatesFor(
                List.of(cp), "Fixture_Alpha_Cobble", BASE_ID, FAMILIES, TAGS);

        assertFalse(candidates.isEmpty(),
                "a family-matched member is exactly what the exact-id index cannot cover");
        assertEquals(2 * cp.hold().variants().size(), candidates.size(),
                "two alpha cells times every orientation");
    }

    @Test
    void theActivatedAnchorBlock_isNotAReWalkCandidate() {
        CompiledPattern cp = ring();
        List<HoldCandidate> candidates = StationStructures.holdCandidatesFor(
                List.of(cp), "Fixture_Station_Beta", BASE_ID, FAMILIES, TAGS);
        assertTrue(candidates.isEmpty(),
                "breaking the anchor is the anchor-break path, keyed off its own stash, never a re-walk");
    }

    @Test
    void anUnrelatedBlock_probesNothing() {
        assertTrue(StationStructures.holdCandidatesFor(
                List.of(ring()), "Fixture_Unrelated", BASE_ID, FAMILIES, TAGS).isEmpty());
    }

    // ==================== the HOLD re-walk ====================

    @Test
    void anIntactBuild_survivesItsTaggedVariantWalk() {
        CompiledPattern cp = ring();
        assertTrue(StationStructures.holdStands(cp.hold(), 0, 0, 0, 0, standingBuild(), PREDICATE));
    }

    @Test
    void theBrokenPositionOverlay_failsTheWalk_evenBeforeTheEngineRemovesTheBlock() {
        CompiledPattern cp = ring();
        // The player break event fires BEFORE removal: the world still shows the ring block, but
        // the overlay reads it as air, which is what makes the re-walk see the future truth.
        BlockReader reader = StationStructures.withBrokenAt(standingBuild(), 1, 0, 0);
        assertFalse(StationStructures.holdStands(cp.hold(), 0, 0, 0, 0, reader, PREDICATE));
        assertEquals(PatternCells.EMPTY_KEY, reader.blockItemIdAt(1, 0, 0));
        assertEquals("Fixture_Station_Beta", reader.blockItemIdAt(0, 0, 0),
                "every other position reads through untouched");
    }

    @Test
    void aStaleTaggedVariant_fallsBackToAnyStandingOrientation() {
        CompiledPattern cp = ring();
        assertTrue(StationStructures.holdStands(cp.hold(), 99, 0, 0, 0, standingBuild(), PREDICATE),
                "an out-of-range variant index (the asset's Rotate flags changed) must not demolish a build");
        assertTrue(StationStructures.holdStands(cp.hold(), null, 0, 0, 0, standingBuild(), PREDICATE));
    }

    @Test
    void aFilledRequiredEmptyCell_alsoFailsTheHold() {
        CompiledPattern cp = ring();
        StubWorld world = standingBuild().put(0, 1, 0, "Fixture_Alpha_Cobble");
        assertFalse(StationStructures.holdStands(cp.hold(), 0, 0, 0, 0, world, PREDICATE),
                "a non-socket Empty cell is part of the standing shape");
    }

    // ==================== the swap-back decision ====================

    @Test
    void swapDecision_carriesTheReadRotationVerbatim() {
        SwapDecision swap = StationStructures.swapFor("Fixture_Station_Beta", "Fixture_Beta_Cold", 3);
        assertFalse(swap.skip());
        assertEquals("Fixture_Beta_Cold", swap.blockItemId());
        assertEquals(3, swap.rotationIndex(), "the anchor keeps the facing it was placed with");
    }

    @Test
    void swapDecision_unreadableRotation_staysNull_forTheNoRotationWrite() {
        SwapDecision swap = StationStructures.swapFor("Fixture_Station_Beta", "Fixture_Beta_Cold", null);
        assertFalse(swap.skip());
        assertNull(swap.rotationIndex());
    }

    @Test
    void swapDecision_skipsWhenTheTargetAlreadyStands_caseInsensitively() {
        assertTrue(StationStructures.swapFor("Fixture_Beta_Cold", "Fixture_Beta_Cold", 1).skip());
        assertTrue(StationStructures.swapFor("fixture_beta_cold", "Fixture_Beta_Cold", 1).skip(),
                "the custom-core style: activation and revert alike write nothing");
    }

    @Test
    void swapDecision_skipsWithNoTargetAtAll() {
        assertTrue(StationStructures.swapFor("Fixture_Station_Beta", null, 1).skip());
        assertTrue(StationStructures.swapFor("Fixture_Station_Beta", " ", 1).skip());
    }

    // ==================== the stop family ====================

    @Test
    void structureLost_isAPresentPlayerHandBackStop() {
        assertTrue(StationService.custodyReturnsAtStop(StationService.StopReason.STRUCTURE_LOST),
                "the stopping player's own piles hand back through the one stop funnel");
        assertTrue(StationService.dropsPendingCuesAtStop(StationService.StopReason.STRUCTURE_LOST),
                "an interrupted session's parked cues fall silent, like every other interrupt");
    }
}
