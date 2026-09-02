package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The unattended catch-up math + the analytic settle (decision 90), pinned pure: the
 * elapsed/cycle floor, the {@code CatchUpMaxMs} ceiling, the zero-elapsed outage case (game time
 * stands still while the server is down, so a restart settles NOTHING), the input/room/
 * {@code MaxCycles} clamps, the bank-vs-forfeit clock rule, the accrual key namespace (never
 * inside the reserved {@code doneness:} prefix), the attended-parity tier order, the
 * skip-live-session authority rule, and the hydrate index's seeding/eviction/re-arm semantics
 * over stubbed walks. Every fixture is authored by the test itself.
 */
class UnattendedCatchUpTest {

    private static final Function<String, String[]> NO_FAMILIES = id -> new String[0];
    private static final Function<String, Map<String, String[]>> NO_TAGS = id -> Map.of();

    private static StationAsset.Work.Unattended unattended(Integer maxCycles, Long catchUpMaxMs) {
        return StationAsset.Work.Unattended.of(null, maxCycles, catchUpMaxMs);
    }

    private static List<Custody.ResolvedSocket> sockets(Custody.ResolvedSocket... sockets) {
        return List.of(sockets);
    }

    private static Custody.ResolvedSocket itemSocket(String id, int maxQuantity) {
        return new Custody.ResolvedSocket(id, true, null, null, null, maxQuantity,
                false, false, null, false, false, false, null);
    }

    // ==================== the raw catch-up math ====================

    @Test
    void usableElapsed_isCappedByTheCatchUpCeiling() {
        assertEquals(1_000L, StationUnattended.usableElapsed(1_000L, 86_400_000L));
        assertEquals(86_400_000L, StationUnattended.usableElapsed(500_000_000L, 86_400_000L));
        assertEquals(0L, StationUnattended.usableElapsed(-50L, 86_400_000L), "never negative");
    }

    @Test
    void rawCycles_floorsElapsedOverCycleMs() {
        assertEquals(0L, StationUnattended.rawCycles(4_999L, 5_000L));
        assertEquals(1L, StationUnattended.rawCycles(5_000L, 5_000L));
        assertEquals(3L, StationUnattended.rawCycles(19_999L, 5_000L));
    }

    @Test
    void rawCycles_zeroElapsed_theOutageCase_settlesNothing() {
        // Game time stands still while the server is down: an outage's elapsed is exactly zero.
        assertEquals(0L, StationUnattended.rawCycles(0L, 5_000L));
    }

    @Test
    void rawCycles_nonPositiveCycleMs_settlesNothing() {
        assertEquals(0L, StationUnattended.rawCycles(50_000L, 0L));
        assertEquals(0L, StationUnattended.rawCycles(50_000L, -1L));
    }

    @Test
    void advancedLastGameTime_unclampedSettle_banksTheSubCycleRemainder() {
        // 12_500ms usable at 5_000ms a cycle: 2 cycles settle, 2_500ms of progress banks.
        assertEquals(100_000L - 2_500L,
                StationUnattended.advancedLastGameTime(100_000L, 12_500L, 2L, 2, 5_000L));
    }

    @Test
    void advancedLastGameTime_clampedSettle_forfeitsTheLeftover() {
        // The settle was clamped below the raw time-derived count (inputs/room/MaxCycles): the
        // un-worked backlog forfeits, or topping the station up would burst-settle hours the
        // station spent unable to work.
        assertEquals(100_000L,
                StationUnattended.advancedLastGameTime(100_000L, 50_000L, 10L, 3, 5_000L));
    }

    // ==================== the accrual key namespace ====================

    @Test
    void accrualKeys_liveOutsideTheReservedDonenessPrefix() {
        String key = StationUnattended.accrualKey(3);
        assertTrue(key.startsWith(StationUnattended.ACCRUAL_KEY_PREFIX));
        assertFalse(key.startsWith(StationDoneness.RESERVED_KEY_PREFIX),
                "accrual must never collide with the reserved doneness: namespace");
        assertEquals("accrual:conversion:3", key, "the L7 conversion row-key channel");
    }

    @Test
    void parseAccrualIndex_roundTrips() {
        assertEquals(7, StationUnattended.parseAccrualIndex(StationUnattended.accrualKey(7)));
        assertEquals(-1, StationUnattended.parseAccrualIndex("doneness:batches"));
        assertEquals(-1, StationUnattended.parseAccrualIndex("accrual:conversion:notanumber"));
        assertEquals(-1, StationUnattended.parseAccrualIndex(null));
    }

    // ==================== attended-parity ordering + the authority rule ====================

    @Test
    void tierOrderedIndices_stableByEffectiveTier_keepsResolvedIndices() {
        StationAsset.Conversion tier2 = StationAsset.Conversion.of(
                Ingredient.item("A", 1), Ingredient.item("OutA", 1)).withTier(2);
        StationAsset.Conversion tier0 = StationAsset.Conversion.of(
                Ingredient.item("B", 1), Ingredient.item("OutB", 1));
        StationAsset.Conversion tier1 = StationAsset.Conversion.of(
                Ingredient.item("C", 1), Ingredient.item("OutC", 1)).withTier(1);
        assertArrayEquals(new int[] {1, 2, 0}, StationUnattended.tierOrderedIndices(
                new StationAsset.Conversion[] {tier2, tier0, tier1}),
                "candidates walk in tier order but keep their RESOLVED index (the accrual key)");
        assertArrayEquals(new int[0], StationUnattended.tierOrderedIndices(null));
    }

    @Test
    void shouldVisit_skipsALiveSession_attendedIsTheAuthority() {
        assertTrue(StationUnattended.shouldVisit(true, false));
        assertFalse(StationUnattended.shouldVisit(true, true), "a live session owns the block");
        assertFalse(StationUnattended.shouldVisit(false, false), "no stash, nothing to settle");
    }

    // ==================== the analytic settle over a detached claim ====================

    private static StationCustodyClaim pitClaim(UUID owner) {
        return new StationCustodyClaim(owner, "cookingpit", "stew", 0, 64, 0);
    }

    @Test
    void firstVisit_stampsTheClockAndSettlesNothing() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 10);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 40_000L, NO_FAMILIES, NO_TAGS);

        assertFalse(settle.transformed());
        assertTrue(settle.clockStamped());
        assertEquals(40_000L, claim.unattendedLastGameTime(),
                "the first visit anchors the catch-up clock; nothing is owed for unmeasured time");
        assertEquals(10, claim.totalQuantity("ingredients"), "nothing drained");
    }

    @Test
    void settle_transformsDrainsProducesAndAccrues_analytically() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 10);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 2, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        // 17_500ms at 5_000ms a cycle pays for 3 whole cycles; inputs pay for 5; room is ample.
        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 17_500L, NO_FAMILIES, NO_TAGS);

        assertEquals(3, settle.settledCycles());
        assertEquals(0, settle.conversionIndex());
        assertEquals("output", settle.produceSocketId());
        assertEquals(4, claim.totalQuantity("ingredients"), "2 per cycle x 3 cycles drained");
        assertEquals(3, claim.totalQuantity("output"), "1 per cycle x 3 cycles produced");
        assertEquals(3, claim.pendingCycles("output").get(StationUnattended.accrualKey(0)),
                "the settled count accrues on the produce pile under the conversion's row key");
        assertEquals(17_500L - 2_500L, claim.unattendedLastGameTime(),
                "an unclamped settle banks the sub-cycle remainder");
    }

    @Test
    void settle_inputClamp_forfeitsTheLeftoverTime() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 3);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 2, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        // Time pays for 10 cycles but the pile holds only 1 cycle's worth (3 / 2 = 1).
        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 50_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(1, settle.settledCycles());
        assertEquals(1, claim.totalQuantity("ingredients"));
        assertEquals(50_000L, claim.unattendedLastGameTime(),
                "a clamped settle forfeits the un-worked backlog outright");
    }

    @Test
    void settle_outputRoomClamp_boundsOnNetFlow() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 100);
        claim.addTo("output", claim.ownerId, "Food_Stew", 8);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        // The output socket caps at 10 and already holds 8: room pays for 2 cycles only
        // (the block-level cap of 200 never binds).
        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 150), itemSocket("output", 10)),
                rows, null, 200, unattended(null, null), 5_000L, 100_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(2, settle.settledCycles());
        assertEquals(10, claim.totalQuantity("output"));
    }

    @Test
    void settle_maxCyclesCapsOneBurst() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 100);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 150), itemSocket("output", 150)),
                rows, null, 300, unattended(5, null), 5_000L, 500_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(5, settle.settledCycles(), "MaxCycles caps a single settle burst");
        assertEquals(500_000L, claim.unattendedLastGameTime(), "the capped backlog forfeits");
    }

    @Test
    void settle_catchUpCeiling_capsTheUsableElapsed() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 100);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        // A week of elapsed game time under a 25_000ms ceiling: only 5 cycles' worth is usable
        // (MaxCycles left far above so the ceiling is what binds).
        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 150), itemSocket("output", 150)),
                rows, null, 300, unattended(100, 25_000L), 5_000L, 604_800_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(5, settle.settledCycles());
    }

    @Test
    void settle_subCycleElapsed_leavesTheClockBanking() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 10);
        claim.setUnattendedLastGameTime(10_000L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 13_000L, NO_FAMILIES, NO_TAGS);

        assertFalse(settle.transformed());
        assertFalse(settle.clockStamped());
        assertEquals(10_000L, claim.unattendedLastGameTime(),
                "3_000ms of a 5_000ms cycle keeps banking toward the first settle");
    }

    @Test
    void settle_noRunnableRow_forfeitsAndStampsTheClock() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 2, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        // Empty piles: nothing can run, so the elapsed time forfeits rather than banking.
        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 90_000L, NO_FAMILIES, NO_TAGS);

        assertFalse(settle.transformed());
        assertTrue(settle.clockStamped());
        assertEquals(90_000L, claim.unattendedLastGameTime());
    }

    @Test
    void settle_conversionDurationOutranksWorkCycleMs() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 100);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"), 10_000L)};

        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 150), itemSocket("output", 150)),
                rows, null, 300, unattended(null, null), 5_000L, 30_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(3, settle.settledCycles(),
                "the row's own DurationMs (10s) paces the settle, not Work.CycleMs (5s)");
    }

    @Test
    void settle_producePileOwner_inheritsTheFirstConsumedSocketsOwner() {
        // Decision 82: an unattended produce inherits the owner of the first-consumed socket.
        UUID stashOwner = UUID.randomUUID();
        UUID contributor = UUID.randomUUID();
        StationCustodyClaim claim = pitClaim(stashOwner);
        claim.addTo("ingredients", contributor, "Food_Meat_Raw", 4);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 2, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 10_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(contributor, claim.pileOwner("output"),
                "the produce pile belongs to whoever loaded the first-consumed socket");
    }

    @Test
    void settle_appliesTheDeterministicYield() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 10);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, StationAsset.Yield.of(2, null, null, null), 100,
                unattended(null, null), 5_000L, 10_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(4, claim.totalQuantity("output"),
                "Yield.Base 2 per cycle x 2 cycles - the same deterministic quantity an attended cycle pays");
    }

    @Test
    void settle_exactSetRow_skipsWhenThePileIsContaminated_looserRowRuns() {
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 2);
        claim.addTo("ingredients", claim.ownerId, "Food_Carrot", 1);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion exact = StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 2, "ingredients"),
                Ingredient.of("Food_Kebab", null, 1, "output")).withExactSet(true);
        StationAsset.Conversion loose = StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 1, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output")).withTier(1);
        StationAsset.Conversion[] rows = {exact, loose};

        StationUnattended.Settle settle = StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 5_000L, NO_FAMILIES, NO_TAGS);

        assertEquals(1, settle.conversionIndex(),
                "the contaminated exact-set row yields to the looser one, attended parity");
        assertEquals(1, claim.totalQuantity("output"));
    }

    // ==================== the hydrate index (seeding over a stubbed walk) ====================

    @Test
    void index_hydrateSeedsRegisterAndEvictLazily() {
        UnattendedIndex index = new UnattendedIndex();
        String worldPrefix = "w1:";
        String sectionKey = UnattendedIndex.sectionKey(worldPrefix, 5, 68, 9, 4);
        assertEquals("w1:s:0:4:0", sectionKey);
        assertFalse(index.isHydrated(sectionKey));

        // The stubbed section walk: mark the section, register what the stashes seeded.
        index.markHydrated(sectionKey);
        index.register("w1:5:68:9");
        index.register("w1:6:68:9");
        index.register("w2:5:68:9");

        assertTrue(index.isHydrated(sectionKey), "a hydrated section is not re-walked");
        assertEquals(List.of("w1:5:68:9", "w1:6:68:9").size(), index.blocksInWorld(worldPrefix).size());

        // Lazy eviction: a visit that found the stash gone drops the block alone...
        index.evictBlock("w1:6:68:9");
        assertFalse(index.containsBlock("w1:6:68:9"));
        // ...and one that found the SECTION unloaded re-arms the walk for it.
        index.dropSection(sectionKey);
        assertFalse(index.isHydrated(sectionKey), "a reloaded section is re-hydrated");
    }

    @Test
    void index_worldSweepDropsBlocksAndMarkersByPrefix() {
        UnattendedIndex index = new UnattendedIndex();
        index.register("w1:1:64:1");
        index.register("w2:1:64:1");
        index.markHydrated(UnattendedIndex.sectionKey("w1:", 1, 64, 1, 4));
        index.markHydrated(UnattendedIndex.sectionKey("w2:", 1, 64, 1, 4));

        index.dropWorld("w1:");

        assertEquals(0, index.blocksInWorld("w1:").size());
        assertEquals(1, index.blocksInWorld("w2:").size());
        assertFalse(index.isHydrated(UnattendedIndex.sectionKey("w1:", 1, 64, 1, 4)));
        assertTrue(index.isHydrated(UnattendedIndex.sectionKey("w2:", 1, 64, 1, 4)));

        // The shutdown sweep's match-all predicate clears the rest.
        index.dropMatching(key -> true);
        assertEquals(0, index.blockCount());
    }

    @Test
    void settle_leavesDonenessKeysAlone() {
        // The two records share the produce pile without sharing a key: a settle's accrual lands
        // beside a standing doneness batch count, never over it.
        StationCustodyClaim claim = pitClaim(UUID.randomUUID());
        claim.addTo("ingredients", claim.ownerId, "Food_Meat_Raw", 4);
        claim.noteDonenessBatch("output", 1_000L);
        claim.setUnattendedLastGameTime(0L);
        StationAsset.Conversion[] rows = {StationAsset.Conversion.of(
                Ingredient.of("Food_Meat_Raw", null, 2, "ingredients"),
                Ingredient.of("Food_Stew", null, 1, "output"))};

        StationUnattended.settle(claim,
                sockets(itemSocket("ingredients", 100), itemSocket("output", 100)),
                rows, null, 100, unattended(null, null), 5_000L, 5_000L, NO_FAMILIES, NO_TAGS);

        Map<String, Integer> pending = claim.pendingCycles("output");
        assertEquals(1, pending.get(StationDoneness.BATCHES_KEY), "the doneness record survives");
        assertEquals(1, pending.get(StationUnattended.accrualKey(0)), "the accrual lands beside it");
        assertNull(pending.get("doneness:" + StationUnattended.accrualKey(0)), "namespaces never nest");
    }
}
