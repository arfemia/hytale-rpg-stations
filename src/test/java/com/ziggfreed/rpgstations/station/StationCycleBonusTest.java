package com.ziggfreed.rpgstations.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootRef;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.rpgstations.asset.ActionDef;
import com.ziggfreed.rpgstations.asset.ExtensionAsset;
import com.ziggfreed.rpgstations.asset.StationAsset;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.loot.LootFixtures;
import com.ziggfreed.rpgstations.loot.StationLootEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Trigger: "Cycle"} means THE action's cycle-completed moment, whatever program shape runs
 * it. An action that authors a {@code Steps} program used to resolve its own {@code Bonus} only at
 * session stop (the {@code Completion} pass), because the per-cycle pass lived exclusively inside
 * the implicit convert program's Roll phase - so identical content was live under one program shape
 * and inert under the other.
 *
 * <p>Covered here at the two seams a test JVM can reach without a running server: the ONE
 * {@link StationService#effectiveBonus} read both program shapes share (an authored program's
 * action resolves the same rolls, extension appends included), one pass over those rolls granting
 * exactly the {@code Cycle}-trigger ones, and the {@code OutputItems} tally still landing nowhere
 * under an authored program. The dispatch wiring itself
 * ({@code dispatchProgram}'s {@code bonusAtCompletion} flag) needs a live store and player.
 */
class StationCycleBonusTest {

    private static final Player NULL_PLAYER = null;
    private static final Store<EntityStore> NULL_STORE = null;

    private static ExtensionAsset ext(String id, String body) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(ExtensionAsset.class, id, null);
        return ExtensionAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(body), null, new AssetExtraInfo<>(data));
    }

    private static StationAsset stationWith(String id, ActionDef... actions) {
        return StationAsset.of(id, StationAsset.Identity.of(
                "rpgstations.station." + id + ".name", "rpgstations.station." + id + ".desc",
                "Fixture_Icon"), actions);
    }

    /** A ritual action: an authored step program, plus a Bonus of its own. */
    private static StationAsset ritualStation(LootRef bonus) {
        return stationWith("fixtureritual", ActionDef.of("Enhance")
                .withSteps(new StationStep[] {StationStep.of("Beat")})
                .withBonus(bonus));
    }

    /**
     * A lookup that answers nothing, which is all these cases need: every fixture roll here is
     * ungated and un-laddered, so no factor is ever read.
     */
    private static FactorLookup lookup() {
        return FactorLookup.none();
    }

    @AfterEach
    void clearFoldedExtensions() {
        ExtensionCatalog.getInstance().fold(Map.of(), true);
    }

    @Test
    void authoredProgramAction_resolvesTheSameBonusRollsTheImplicitPathDoes() throws Exception {
        StationAsset station = ritualStation(LootRef.of(null, new Roll[] {
                Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null, LootGrants.ofDropList("Fixture_Own_Drops"), null)}));
        fold(ext("ritual-ext", "{ \"Target\":{\"Action\":\"Enhance\"}, \"Bonus\":{ \"Rolls\":[ {"
                + " \"Trigger\":\"Cycle\", \"Grants\":{\"DropLists\":[\"Fixture_Ext_Drops\"]} } ] } }"));

        List<Roll> rolls = resolvedBonus(station).rolls();

        assertEquals(2, rolls.size(),
                "an authored-program action resolves its own Bonus plus every extension append");
        assertEquals(List.of("Fixture_Own_Drops", "Fixture_Ext_Drops"), dropListIds(rolls));
    }

    @Test
    void oneCompletedPass_grantsTheCycleRollsAndLeavesACompletionRollForStop() {
        // One pass over the resolved rolls is what a completed program walk performs. A
        // Completion-trigger roll in the same Bonus must NOT ride it - that pass runs at stop().
        StationAsset station = ritualStation(LootRef.of(null, new Roll[] {
                Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null,
                        LootFixtures.contribution("yourmod:test_alpha", 4.0), null),
                Roll.of(StationLootEngine.TRIGGER_COMPLETION, null, null, null,
                        LootFixtures.contribution("yourmod:test_omega", 9.0), null)}));

        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(resolvedBonus(station),
                StationLootEngine.TRIGGER_CYCLE, lookup(), NULL_PLAYER, null, "fixtureritual",
                "Enhance", 1, null, NULL_STORE, 0, 0, 0);

        assertEquals(1, result.getContributions().size(), "exactly the Cycle-trigger roll granted");
        assertEquals("yourmod:test_alpha", result.getContributions().get(0).getChannel());
    }

    @Test
    void outputItems_underAnAuthoredProgram_grantNothing() {
        // The pass now genuinely reaches the grant side with a tallied amount, and an authored
        // program has no cycle output to add items to - so this must fail quietly rather than
        // grab a stale item id (LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT warns at authoring time). A whole
        // fixture amount keeps the case deterministic: it resolves to items with no roll involved.
        StationSession s = new StationSession();
        s.stationId = "fixtureritual";
        s.cycleOutputItemId = null;

        StationService.grantBonusOutputItems(s, NULL_STORE, null, NULL_PLAYER, 3.0);

        assertTrue(s.producedItems.isEmpty(), "nothing is produced when there is no cycle output");
        assertTrue(s.producedYield.isEmpty(), "and no yield-breakdown row is invented for it");
    }

    @Test
    void outputItemsTally_isStillCollectedForACycleTriggerRoll() {
        // The tally itself is unchanged: the roll reports a fractional amount and the caller
        // resolves and grants it. This is what makes the no-op above the ONLY thing standing
        // between the tally and a wrong grant.
        StationAsset station = ritualStation(LootRef.of(null, new Roll[] {
                Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null, LootFixtures.outputItems(2.0), null)}));

        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(resolvedBonus(station),
                StationLootEngine.TRIGGER_CYCLE, lookup(), NULL_PLAYER, null, "fixtureritual",
                "Enhance", 1, null, NULL_STORE, 0, 0, 0);

        assertEquals(2.0, result.getOutputItems());
        assertFalse(result.getDropListItems().containsKey("Fixture_Plank"),
                "the count is reported, never granted here");
    }

    /** The ritual action's effective Bonus, resolved the way every live route resolves it. */
    private static LootEngine.Resolved resolvedBonus(StationAsset station) {
        return StationLootEngine.resolve(
                StationService.effectiveBonus(station, ActionResolver.resolve(station, "Enhance")));
    }

    private static void fold(ExtensionAsset... exts) {
        Map<String, ExtensionAsset> layer = new java.util.LinkedHashMap<>();
        for (ExtensionAsset e : exts) {
            layer.put(e.getId(), e);
        }
        ExtensionCatalog.getInstance().fold(layer, true);
    }

    private static List<String> dropListIds(List<Roll> rolls) {
        List<String> ids = new ArrayList<>();
        for (Roll r : rolls) {
            ids.add(r.getGrants().getDropLists()[0]);
        }
        return ids;
    }
}
