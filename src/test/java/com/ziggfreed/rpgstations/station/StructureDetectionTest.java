package com.ziggfreed.rpgstations.station;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.codec.Vec3i;
import com.ziggfreed.common.world.pattern.BlockReader;
import com.ziggfreed.common.world.pattern.CellPredicate;
import com.ziggfreed.common.world.pattern.PatternIndex;
import com.ziggfreed.common.world.pattern.PatternMatch;
import com.ziggfreed.common.world.stash.BlockStash;
import com.ziggfreed.rpgstations.asset.ActionInput;
import com.ziggfreed.rpgstations.asset.StructurePatternAsset;
import com.ziggfreed.rpgstations.station.PatternCatalog.CompiledPattern;
import com.ziggfreed.rpgstations.station.PatternCells.CellMatcher;
import com.ziggfreed.rpgstations.station.StationStructures.ActivationDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detection over a stub world: a completed build resolves through the exact zc walk the live path
 * uses ({@code PatternIndex} candidates + {@code matchFromCell}), first-match determinism follows
 * registration order, and the activation decision splits conflict / gate-denial / idempotence
 * purely off the anchor stash's tag. Neutral fixture vocabulary throughout (alpha ring, beta
 * anchor); nothing reads shipped content or a live asset map.
 */
public class StructureDetectionTest {

    private static final UnaryOperator<String> BASE_ID = UnaryOperator.identity();
    private static final Function<String, List<String>> FAMILIES = id ->
            id.startsWith("Fixture_Alpha") ? List.of("Alpha") : List.of();
    private static final Function<String, Map<String, String[]>> TAGS = id -> Map.of();
    private static final CellPredicate<CellMatcher> PREDICATE =
            PatternCells.predicate(BASE_ID, FAMILIES, TAGS);

    /** A plus-shaped ring of alpha family blocks around a beta anchor, the standing fixture shape. */
    private static CompiledPattern ring() {
        StructurePatternAsset asset = StructurePatternAsset.of("ring", null, null,
                StructurePatternAsset.Activate.of("Fixture_Station_Beta", null),
                new StructurePatternAsset.Cell[] {
                        cell(0, 0, 0, ActionInput.of("Fixture_Beta_Cold", null, null, null), null, true),
                        cell(1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null),
                        cell(-1, 0, 0, ActionInput.of(null, "Alpha", null, null), null, null),
                        cell(0, 0, 1, ActionInput.of(null, "Alpha", null, null), null, null)},
                null, null);
        return PatternCatalog.compile("ring", asset, Set.of(), null);
    }

    private static StructurePatternAsset.Cell cell(int x, int y, int z, ActionInput block,
            Boolean empty, Boolean isAnchor) {
        return StructurePatternAsset.Cell.of(Vec3i.of(x, y, z), block, empty, isAnchor);
    }

    /** A tiny in-memory world: unset positions read as air ({@code "Empty"}). */
    private static final class StubWorld implements BlockReader {
        private final Map<String, String> blocks = new HashMap<>();

        StubWorld put(int x, int y, int z, String blockId) {
            blocks.put(x + ":" + y + ":" + z, blockId);
            return this;
        }

        StubWorld remove(int x, int y, int z) {
            blocks.remove(x + ":" + y + ":" + z);
            return this;
        }

        @Override
        public String blockItemIdAt(int x, int y, int z) {
            return blocks.getOrDefault(x + ":" + y + ":" + z, PatternCells.EMPTY_KEY);
        }
    }

    private static StubWorld completedRingAt(int ax, int ay, int az) {
        return new StubWorld()
                .put(ax, ay, az, "Fixture_Beta_Cold")
                .put(ax + 1, ay, az, "Fixture_Alpha_Cobble")
                .put(ax - 1, ay, az, "Fixture_Alpha_Brick")
                .put(ax, ay, az + 1, "Fixture_Alpha_Cobble");
    }

    // ==================== the placement walk ====================

    @Test
    void aCompletedBuild_matchesFromTheAnchorPlacement() {
        CompiledPattern cp = ring();
        StubWorld world = completedRingAt(10, 64, -3);

        PatternMatch<CellMatcher> match = cp.detect().variants().get(0)
                .matchFromCell(cp.detect().anchorIndex(), 10, 64, -3, world, PREDICATE);

        assertNotNull(match);
        assertEquals(10, match.anchorX());
        assertEquals(64, match.anchorY());
        assertEquals(-3, match.anchorZ());
        assertEquals(0, match.yawQuarterTurns());
    }

    @Test
    void aRotatedBuild_matchesItsRotatedVariant_andCarriesTheTurnCount() {
        CompiledPattern cp = ring();
        // One positive quarter-turn maps (x,y,z) -> (z,y,-x): the authored +X ring cell lands at
        // +Z... build the world by transforming each authored cell through that map.
        StubWorld world = new StubWorld()
                .put(0, 0, 0, "Fixture_Beta_Cold")
                .put(0, 0, -1, "Fixture_Alpha_Cobble")   // authored (1,0,0) -> (0,0,-1)
                .put(0, 0, 1, "Fixture_Alpha_Cobble")    // authored (-1,0,0) -> (0,0,1)
                .put(1, 0, 0, "Fixture_Alpha_Cobble");   // authored (0,0,1) -> (1,0,0)

        assertNull(cp.detect().variants().get(0)
                        .matchFromCell(cp.detect().anchorIndex(), 0, 0, 0, world, PREDICATE),
                "the identity orientation does not stand in this world");
        PatternMatch<CellMatcher> match = cp.detect().variants().get(1)
                .matchFromCell(cp.detect().anchorIndex(), 0, 0, 0, world, PREDICATE);
        assertNotNull(match);
        assertEquals(1, match.yawQuarterTurns(),
                "the matched variant's turn count is what the anchor swap carries");
    }

    @Test
    void anIncompleteBuild_doesNotMatch() {
        CompiledPattern cp = ring();
        StubWorld world = completedRingAt(0, 0, 0).remove(1, 0, 0);

        for (var variant : cp.detect().variants()) {
            assertNull(variant.matchFromCell(cp.detect().anchorIndex(), 0, 0, 0, world, PREDICATE));
        }
    }

    // ==================== first-match determinism ====================

    @Test
    void candidates_areAnsweredInRegistrationOrder_soSortedSeedingIsTheDeterminism() {
        CompiledPattern first = ring();
        CompiledPattern second = ring();
        PatternIndex<CellMatcher> index = new PatternIndex<>();
        // The catalog seeds sorted by pattern id, then variant, then cell - mirror two patterns
        // sharing one anchor id and assert the probe answers them in exactly that order.
        index.add("fixture_beta_cold", first.detect(), 0, first.detect().anchorIndex());
        index.add("fixture_beta_cold", second.detect(), 0, second.detect().anchorIndex());

        List<PatternIndex.Candidate<CellMatcher>> candidates = index.candidatesFor("fixture_beta_cold");
        assertEquals(2, candidates.size());
        assertEquals(first.detect(), candidates.get(0).pattern(),
                "the first-registered (alphabetically first) pattern is probed first, so it wins a tie");
    }

    // ==================== the activation decision ====================

    @Test
    void activationDecision_freshAnchor_activates() {
        assertEquals(ActivationDecision.ACTIVATE,
                StationStructures.decideActivation(null, "ring", true));
    }

    @Test
    void activationDecision_gateFailed_denies() {
        assertEquals(ActivationDecision.DENIED,
                StationStructures.decideActivation(null, "ring", false));
    }

    @Test
    void activationDecision_samePatternAlreadyStamped_isIdempotent() {
        String tag = StationCustodyClaim.withPatternSegment(null, "ring", 2);
        assertEquals(ActivationDecision.ALREADY_ACTIVE,
                StationStructures.decideActivation(tag, "ring", true));
        assertEquals(ActivationDecision.ALREADY_ACTIVE,
                StationStructures.decideActivation(tag, "RING", true), "pattern ids compare case-insensitively");
    }

    @Test
    void activationDecision_differentPattern_conflicts_beforeTheGateIsEvenAsked() {
        String tag = StationCustodyClaim.withPatternSegment(null, "otherring", 0);
        assertEquals(ActivationDecision.CONFLICT,
                StationStructures.decideActivation(tag, "ring", true));
        assertEquals(ActivationDecision.CONFLICT,
                StationStructures.decideActivation(tag, "ring", false),
                "a conflicting mark refuses whatever the gate would have said");
    }

    @Test
    void activationDecision_foreignConsumerStash_conflicts() {
        assertEquals(ActivationDecision.CONFLICT,
                StationStructures.decideActivation("someothermod:whatever", "ring", true));
    }

    @Test
    void activationDecision_ourPlainCustodyTag_activatesFreely() {
        String custodyTag = StationCustodyClaim.encodeTag("beta", "work");
        assertEquals(ActivationDecision.ACTIVATE,
                StationStructures.decideActivation(custodyTag, "ring", true),
                "a custom core block already holding materials gains the pattern mark beside its custody");
    }

    // ==================== the stash tag's pattern segment ====================

    @Test
    void patternSegment_roundTrips_onEveryTagShape() {
        String patternOnly = StationCustodyClaim.withPatternSegment(null, "Ring", 3);
        assertEquals("ring", StationCustodyClaim.patternIdOfTag(patternOnly));
        assertEquals(3, StationCustodyClaim.patternVariantOfTag(patternOnly));
        assertNull(StationCustodyClaim.stationIdOfTag(patternOnly),
                "an activated-but-never-engaged anchor has no custody identity yet");
        assertEquals(true, StationCustodyClaim.isOurTag(patternOnly));

        String custody = StationCustodyClaim.encodeTag("beta", "work");
        String both = StationCustodyClaim.withPatternSegment(custody, "ring", 1);
        assertEquals("beta", StationCustodyClaim.stationIdOfTag(both));
        assertEquals("work", StationCustodyClaim.actionIdOfTag(both));
        assertEquals("ring", StationCustodyClaim.patternIdOfTag(both));
        assertEquals(1, StationCustodyClaim.patternVariantOfTag(both));
        assertEquals(custody, StationCustodyClaim.withoutPatternSegment(both));
    }

    @Test
    void plainCustodyTag_hasNoPatternSegment() {
        String custody = StationCustodyClaim.encodeTag("beta", "work");
        assertNull(StationCustodyClaim.patternIdOfTag(custody));
        assertNull(StationCustodyClaim.patternVariantOfTag(custody));
        assertEquals("", StationCustodyClaim.patternSegmentOf(custody));
    }

    @Test
    void foreignTag_neverAnswersAPatternId() {
        assertNull(StationCustodyClaim.patternIdOfTag("someothermod:|pattern=ring/0"));
    }

    @Test
    void demotedTag_keepsThePatternMark_andDropsTheCustodyHalf() {
        String both = StationCustodyClaim.withPatternSegment(
                StationCustodyClaim.encodeTag("beta", "work"), "ring", 2);

        String demoted = StationCustodyClaim.demotedToPatternOnly(both);

        assertNull(StationCustodyClaim.stationIdOfTag(demoted),
                "a drained pattern station is open to whoever engages next");
        assertEquals("ring", StationCustodyClaim.patternIdOfTag(demoted),
                "the structure mark outlives the custody record, so a ring break still reverts");
        assertEquals(2, StationCustodyClaim.patternVariantOfTag(demoted));
    }

    @Test
    void stampNewStash_writesTheCustodyHalfInFrontOfThePatternSegment_neverOverIt() {
        BlockStash stash = new BlockStash();
        stash.setTag(StationCustodyClaim.withPatternSegment(null, "ring", 1));

        StationCustodyClaim.stampNewStash(stash, UUID.randomUUID(), "beta", "work");

        assertEquals("beta", StationCustodyClaim.stationIdOfTag(stash.getTag()));
        assertEquals("work", StationCustodyClaim.actionIdOfTag(stash.getTag()));
        assertEquals("ring", StationCustodyClaim.patternIdOfTag(stash.getTag()),
                "engaging a pattern-activated anchor must not lose which pattern stands there");
        assertEquals(1, StationCustodyClaim.patternVariantOfTag(stash.getTag()));
    }

    @Test
    void restampingASegment_replacesRatherThanAppends() {
        String tag = StationCustodyClaim.withPatternSegment(
                StationCustodyClaim.withPatternSegment(null, "ring", 0), "ring", 2);
        assertEquals("ring", StationCustodyClaim.patternIdOfTag(tag));
        assertEquals(2, StationCustodyClaim.patternVariantOfTag(tag));
        assertEquals(tag.toLowerCase(Locale.ROOT), tag, "the segment is lowercase throughout");
    }

    // ==================== the refusal-toast throttle ====================

    @Test
    void refusalToast_throttlesPerKeyWithinTheCooldown() {
        Map<String, Long> shownAt = new HashMap<>();
        long cooldown = StationStructures.REFUSAL_TOAST_COOLDOWN_MS;

        assertTrue(StationStructures.refusalToastAllowed(shownAt, "p1|w|0|64|0", 1_000L, cooldown),
                "the first refusal at an anchor always toasts");
        assertFalse(StationStructures.refusalToastAllowed(shownAt, "p1|w|0|64|0",
                1_000L + cooldown - 1, cooldown), "a repeat inside the cooldown stays silent");
        assertTrue(StationStructures.refusalToastAllowed(shownAt, "p1|w|0|64|0",
                1_000L + cooldown, cooldown), "the cooldown's boundary re-arms the toast");
    }

    @Test
    void refusalToast_keysAreIndependent() {
        Map<String, Long> shownAt = new HashMap<>();
        long cooldown = StationStructures.REFUSAL_TOAST_COOLDOWN_MS;

        assertTrue(StationStructures.refusalToastAllowed(shownAt, "p1|w|0|64|0", 1_000L, cooldown));
        assertTrue(StationStructures.refusalToastAllowed(shownAt, "p2|w|0|64|0", 1_000L, cooldown),
                "a second player at the same anchor gets their own toast");
        assertTrue(StationStructures.refusalToastAllowed(shownAt, "p1|w|9|64|9", 1_000L, cooldown),
                "the same player at another anchor gets their own toast");
    }

    @Test
    void refusalToast_pruningDropsOnlyExpiredEntries() {
        Map<String, Long> shownAt = new HashMap<>();
        for (int i = 0; i < 400; i++) {
            StationStructures.refusalToastAllowed(shownAt, "p|w|" + i + "|64|0", 1_000L, 5_000L);
        }
        // Far past every entry's cooldown: the next decision prunes the map back down.
        StationStructures.refusalToastAllowed(shownAt, "p|w|999|64|0", 60_000L, 5_000L);
        assertEquals(1, shownAt.size(), "expired entries are swept once the map outgrows its bound");
    }
}
