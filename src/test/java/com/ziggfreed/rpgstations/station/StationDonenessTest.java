package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.ziggfreed.common.world.record.BlockRecordSection;
import com.ziggfreed.common.world.stash.BlockStash;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.asset.Ingredient;
import com.ziggfreed.rpgstations.asset.StationAsset;

/**
 * The doneness ready window's pure decision core ({@link StationDoneness}), its persisted record
 * on the claim ({@link StationCustodyClaim}'s doneness accessors), and the D87 fold
 * ({@code StationAsset.Doneness.resolve}) - all without a live server. The impure settle
 * orchestration ({@code StationService#settleDoneness}) composes exactly these pieces; the pieces
 * carry the invariants: boundary exactness, the whole-pile replacement rule, batch scaling, owner
 * preservation, per-socket isolation, the outage-settles-zero clock property, and the
 * ReadyMs-without-Overdone presentational path.
 */
class StationDonenessTest {

    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static StationCustodyClaim claim() {
        return new StationCustodyClaim(OWNER, "fixturepit", "stew", 3, 64, 5);
    }

    private static StationAsset.Doneness window(long readyMs, Ingredient... overdone) {
        return StationAsset.Doneness.of(readyMs, overdone.length == 0 ? null : overdone);
    }

    // ==================== The D87 fold: conversion over recipe, per leaf ====================

    @Test
    void resolve_conversionLeafWinsPerLeaf() {
        StationAsset.Doneness recipeLevel = StationAsset.Doneness.of(60000L,
                new Ingredient[] {Ingredient.item("Fixture_Charcoal", 1)});
        StationAsset.Doneness rowLevel = StationAsset.Doneness.of(5000L, null);
        StationAsset.Doneness resolved = StationAsset.Doneness.resolve(rowLevel, recipeLevel);
        assertNotNull(resolved);
        assertEquals(5000L, resolved.getReadyMs(), "the conversion-level ReadyMs wins");
        assertEquals("Fixture_Charcoal", resolved.getOverdone()[0].getItemId(),
                "the unauthored Overdone leaf falls back to the recipe default - per LEAF, not per group");
    }

    @Test
    void resolve_derivedOrUnauthoredRowInheritsTheRecipeDefaultWhole() {
        // A FromCrafting-derived row (and any authored row without its own group) carries a null
        // Doneness; the fold hands it the recipe-level default untouched.
        StationAsset.Doneness recipeLevel = StationAsset.Doneness.of(60000L,
                new Ingredient[] {Ingredient.item("Fixture_Charcoal", 2)});
        StationAsset.Conversion derived = StationAsset.Conversion.derivedRow(
                new Ingredient[] {Ingredient.item("Fixture_In", 1)},
                new Ingredient[] {Ingredient.item("Fixture_Out", 1)}, null, null);
        assertNull(derived.getDoneness(), "the deriver stamps no doneness of its own");
        StationAsset.Doneness resolved = StationAsset.Doneness.resolve(derived.getDoneness(), recipeLevel);
        assertEquals(60000L, resolved.getReadyMs());
        assertEquals(2, resolved.getOverdone()[0].effectiveQuantity());
    }

    @Test
    void resolve_neitherAltitudeIsNoWindow() {
        assertNull(StationAsset.Doneness.resolve(null, null));
        assertFalse(StationAsset.Doneness.of(null, null).hasReadyWindow(),
                "no ReadyMs = no window, whatever else is authored");
        assertFalse(StationAsset.Doneness.of(0L, null).hasReadyWindow(),
                "a non-positive ReadyMs opens no window");
    }

    // ==================== Boundary exactness ====================

    @Test
    void expired_isBoundaryExact() {
        assertFalse(StationDoneness.expired(1000L, 1000L + 4999L, 5000L),
                "one millisecond short of the window stays Ready");
        assertTrue(StationDoneness.expired(1000L, 1000L + 5000L, 5000L),
                "elapsed == ReadyMs settles (the >= boundary)");
        assertTrue(StationDoneness.expired(1000L, 1000L + 5001L, 5000L));
        assertFalse(StationDoneness.expired(1000L, 1000L, 0L),
                "a non-positive ReadyMs never expires");
    }

    @Test
    void outage_gameTimeUnchangedSettlesZero_acrossTheSectionCodec() {
        // The clock property that makes the window outage-proof: the stamp is WORLD GAME time,
        // and game time does not advance while the server is down. A stash serialized mid-window
        // and reloaded therefore reads the SAME elapsed as at shutdown - here literally zero extra.
        BlockStash stash = new BlockStash();
        StationCustodyClaim.stampNewStash(stash, OWNER, "fixturepit", "stew");
        StationCustodyClaim written = StationCustodyClaim.of(stash, 3, 64, 5, () -> { });
        assertNotNull(written);
        written.addTo("output", OWNER, "Fixture_Stew", 3);
        written.noteDonenessBatch("output", 100_000L);

        BuilderCodec<BlockRecordSection<BlockStash>> sectionCodec =
                BlockRecordSection.buildCodec(BlockStash.CODEC);
        BlockRecordSection<BlockStash> section = sectionCodec.getDefaultValue();
        section.put(ChunkUtil.indexBlock(3, 4, 5), stash);
        ExtraInfo info = new ExtraInfo();
        BlockStash restored = sectionCodec.decode(sectionCodec.encode(section, info), info)
                .get(ChunkUtil.indexBlock(3, 4, 5));
        StationCustodyClaim reloaded = StationCustodyClaim.of(restored, 3, 64, 5, () -> { });
        assertNotNull(reloaded);
        assertEquals(100_000L, reloaded.donenessWindowStart(),
                "the mid-window stamp survives the chunk-section round trip");
        assertEquals("output", reloaded.donenessWindowSocketId());
        assertEquals(1, reloaded.donenessBatches("output"));
        assertFalse(StationDoneness.expired(reloaded.donenessWindowStart(), 100_000L, 60_000L),
                "game time has not advanced across the reload, so a 60s window settles ZERO extra");
        assertEquals(3, reloaded.totalQuantity("output"), "nothing cooked, nothing burned");
    }

    // ==================== The overdone replacement rule ====================

    @Test
    void degradableOverdone_keepsOnlyExactItemEntries() {
        StationAsset.Doneness resolved = window(5000L,
                Ingredient.item("Fixture_Charcoal", 1),
                Ingredient.resource("Fixture_Family", 2),
                Ingredient.matchAny(3),
                Ingredient.tagged(Map.of("Fixture_Tag", new String[0]), 1));
        List<Ingredient> degradable = StationDoneness.degradableOverdone(resolved);
        assertEquals(1, degradable.size(),
                "the output route rule: a route-less / family / tags entry is warned and IGNORED");
        assertEquals("Fixture_Charcoal", degradable.get(0).getItemId());
        assertTrue(StationDoneness.degradableOverdone(window(5000L)).isEmpty(),
                "no Overdone authored = purely presentational, nothing degrades");
        assertTrue(StationDoneness.degradableOverdone(null).isEmpty());
    }

    @Test
    void overdoneReplacement_scalesEachEntryByTheBatchCount() {
        List<Ingredient> degradable = List.of(
                Ingredient.item("Fixture_Charcoal", 2), Ingredient.item("Fixture_Ash", 1));
        Map<String, Integer> replaced = StationDoneness.overdoneReplacement(degradable, 3);
        assertEquals(6, replaced.get("Fixture_Charcoal"), "2 per batch x 3 batches");
        assertEquals(3, replaced.get("Fixture_Ash"));
        assertEquals(2, StationDoneness.overdoneReplacement(degradable, 0).get("Fixture_Charcoal"),
                "a batch count below one floors at one (a windowed pile always held at least one batch)");
    }

    @Test
    void settleOverdone_replacesTheWholePileKeepsOwnerAndIsolatesSockets() {
        StationCustodyClaim c = claim();
        // Two piles: the ingredients socket (someone else's) and the produced output socket.
        c.addTo("ingredients", OTHER, "Fixture_Meat", 4);
        c.addTo("output", OWNER, "Fixture_Stew", 3);
        c.addTo("output", OWNER, "Fixture_Bowl", 1);
        c.noteDonenessBatch("output", 1000L);

        c.settleDonenessOverdone("output",
                StationDoneness.overdoneReplacement(List.of(Ingredient.item("Fixture_Charcoal", 1)),
                        c.donenessBatches("output")));

        assertEquals(Map.of("Fixture_Charcoal", 1), Map.copyOf(c.items("output")),
                "the WHOLE windowed pile collapses - one pot, one fate");
        assertEquals(OWNER, c.pileOwner("output"), "the replacement keeps the pile's owner (decision 82)");
        assertEquals(4, c.totalQuantity("ingredients"),
                "per-socket isolation: a window on the output pile never touches the ingredients");
        assertEquals(OTHER, c.pileOwner("ingredients"));
        assertNull(c.donenessWindowStart(), "the window clears at settle");
        assertNull(c.donenessWindowSocketId(), "the batches key comes off at settle");
        assertTrue(c.donenessOverdoneMarked("output"), "the collapsed pile wears the overdone mark");
        assertTrue(c.anyDonenessOverdoneMarked());
    }

    // ==================== The window record on the claim ====================

    @Test
    void noteDonenessBatch_opensRefreshesAndMovesTheOneWindow() {
        StationCustodyClaim c = claim();
        c.addTo("output", OWNER, "Fixture_Stew", 1);
        assertEquals(1, c.noteDonenessBatch("output", 1000L), "the first batch OPENS the window");
        assertEquals(1000L, c.donenessWindowStart());
        assertEquals("output", c.donenessWindowSocketId());

        assertEquals(2, c.noteDonenessBatch("output", 2500L),
                "a later batch counts up and re-stamps (stirring the pot)");
        assertEquals(2500L, c.donenessWindowStart(), "the window measures time since the LAST batch");

        assertEquals(1, c.noteDonenessBatch("shelf", 4000L),
                "one window per stash: a produce into another pile MOVES the window there");
        assertEquals("shelf", c.donenessWindowSocketId());
        assertEquals(0, c.donenessBatches("output"), "the old pile's batches key is gone");
    }

    @Test
    void noteDonenessBatch_clearsAStandingOverdoneMark() {
        StationCustodyClaim c = claim();
        c.addTo("output", OWNER, "Fixture_Stew", 1);
        c.noteDonenessBatch("output", 1000L);
        c.settleDonenessOverdone("output",
                StationDoneness.overdoneReplacement(List.of(Ingredient.item("Fixture_Charcoal", 1)), 1));
        assertTrue(c.donenessOverdoneMarked("output"));

        assertEquals(1, c.noteDonenessBatch("output", 9000L),
                "fresh output over a collapsed pile opens a NEW window");
        assertFalse(c.donenessOverdoneMarked("output"),
                "the overdone mark clears - the pile's fate is the new window's now");
    }

    @Test
    void gatheringTheWindowedPile_clearsTheWindow() {
        StationCustodyClaim c = claim();
        c.addTo("output", OWNER, "Fixture_Stew", 2);
        c.noteDonenessBatch("output", 1000L);
        // The retrieval path: the pile leaves (its batches key with it), the stamp is cleared.
        c.removePile("output");
        c.clearDonenessWindowStamp();
        assertNull(c.donenessWindowStart());
        assertNull(c.donenessWindowSocketId());
    }

    @Test
    void clearDonenessWindow_dropsStampAndEveryBatchesKeyButKeepsMarks() {
        StationCustodyClaim c = claim();
        c.addTo("output", OWNER, "Fixture_Stew", 2);
        c.noteDonenessBatch("output", 1000L);
        c.addTo("shelf", OWNER, "Fixture_Bread", 1);
        c.settleDonenessOverdone("shelf",
                StationDoneness.overdoneReplacement(List.of(Ingredient.item("Fixture_Crumbs", 1)), 1));
        // settleDonenessOverdone cleared the stamp; reopen on output to exercise clearDonenessWindow.
        c.noteDonenessBatch("output", 2000L);
        c.clearDonenessWindow();
        assertNull(c.donenessWindowStart());
        assertNull(c.donenessWindowSocketId());
        assertTrue(c.donenessOverdoneMarked("shelf"),
                "closing a window never forgets a separate pile's collapsed state");
    }

    // ==================== The D38 hand-back exemption (pure half) ====================

    @Test
    void pilesToHandBack_exemptTheOpenWindowedPileOnly() {
        StationCustodyClaim c = claim();
        c.addTo("ingredients", OWNER, "Fixture_Meat", 4);
        c.addTo("output", OWNER, "Fixture_Stew", 2);
        c.noteDonenessBatch("output", 1000L);
        assertEquals(List.of("ingredients"), StationService.pilesToHandBack(c, OWNER),
                "the windowed pile belongs to the world now; everything else hands back");
        // A CLOSED window (settled overdone) is ordinary contents again - it hands back.
        c.settleDonenessOverdone("output",
                StationDoneness.overdoneReplacement(List.of(Ingredient.item("Fixture_Charcoal", 1)), 1));
        assertEquals(List.of("ingredients", "output"), StationService.pilesToHandBack(c, OWNER));
    }

    // ==================== The resting-state pick ====================

    @Test
    void restingStateName_precedenceTable() {
        Custody.States states = Custody.States.of("Default", "Loaded", "Lit", "Steaming", "Burnt");
        assertEquals("Default", StationDoneness.restingStateName(states, false, false, false));
        assertEquals("Loaded", StationDoneness.restingStateName(states, true, false, false));
        assertEquals("Steaming", StationDoneness.restingStateName(states, true, true, false));
        assertEquals("Burnt", StationDoneness.restingStateName(states, true, false, true));
        assertEquals("Steaming", StationDoneness.restingStateName(states, true, true, true),
                "an open window's Ready wins over a stale mark - fresh output is the actionable signal");
        assertNull(StationDoneness.restingStateName(null, true, true, true), "no States = no flip");
        Custody.States noPair = Custody.States.of("Default", "Loaded", "Lit");
        assertEquals("Loaded", StationDoneness.restingStateName(noPair, true, true, false),
                "an unauthored Ready leaf falls through to Loaded");
        assertEquals("Loaded", StationDoneness.restingStateName(noPair, true, false, true),
                "an unauthored Overdone leaf falls through to Loaded");
    }
}
