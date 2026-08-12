package com.ziggfreed.rpgstations.loot;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.reward.RewardHandler;
import com.ziggfreed.common.loot.reward.RewardKindRegistry;
import com.ziggfreed.common.loot.reward.RewardKinds;
import com.ziggfreed.common.subject.Subject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three payouts a station owns are registered reward KINDS, and the whole reason they are a
 * PER-PASS registry is that they COLLECT onto the pass rather than acting. These tests pin that
 * contract: each kind reaches its pass's own sink, a completion pass refuses the two cycle-scoped
 * ones, and the process-wide vocabulary still reaches a station roll unchanged.
 *
 * <p>Fixture ids and amounts are this test's own and deliberately mirror no shipped content.
 */
class StationRewardKindsTest {

    /** Who the pass pays; the three station kinds collect rather than pay, so it goes unread. */
    private static final Subject WORKER =
            Subject.of(UUID.fromString("55555555-5555-5555-5555-555555555555"), "Fixture");

    private static Roll cycleRoll(LootGrants grants) {
        return Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null, grants, null);
    }

    /** Runs one pass with no world at all: no item sink, no drop lists, no commands. */
    private static StationLootEngine.GrantResult pass(String trigger, LootGrants... grants) {
        Roll[] rolls = new Roll[grants.length];
        for (int i = 0; i < grants.length; i++) {
            rolls[i] = Roll.of(trigger, null, null, null, grants[i], null);
        }
        return StationLootEngine.rollAndGrant(List.of(rolls), trigger, FactorLookup.none(),
                () -> 0.0, null, null, WORKER, null, "fixture");
    }

    @Test
    void outputItemsReward_talliesFractionallyOntoThePass() {
        StationLootEngine.GrantResult result =
                pass(StationLootEngine.TRIGGER_CYCLE, LootFixtures.outputItems(0.5),
                        LootFixtures.outputItems(0.25));

        assertEquals(0.75, result.getOutputItems(), 1e-9,
                "the pass sums before anything resolves it to whole items");
    }

    @Test
    void contributionReward_collectsChannelParamAndAmount() {
        StationLootEngine.GrantResult result = pass(StationLootEngine.TRIGGER_CYCLE,
                LootGrants.of(null, null, null, new LootGrants.Reward[] {
                        LootGrants.Reward.of(StationRewardKinds.KIND_CONTRIBUTION,
                                Map.of("Channel", "yourmod:fixture", "Param", "ALPHA", "Amount", "4"))}));

        assertEquals(1, result.getContributions().size());
        assertEquals("yourmod:fixture", result.getContributions().get(0).getChannel());
        assertEquals("ALPHA", result.getContributions().get(0).getParam());
        assertEquals(4.0, result.getContributions().get(0).getAmount());
    }

    @Test
    void effectReward_collectsTheIdAndItsDuration() {
        StationLootEngine.GrantResult result = pass(StationLootEngine.TRIGGER_CYCLE,
                LootGrants.of(null, null, null, new LootGrants.Reward[] {
                        LootGrants.Reward.of(StationRewardKinds.KIND_EFFECT,
                                Map.of("Id", "Fixture_Effect", "DurationMs", "3000"))}));

        assertEquals(1, result.getEffectGrants().size());
        assertEquals("Fixture_Effect", result.getEffectGrants().get(0).getId());
        assertEquals(3000L, result.getEffectGrants().get(0).getDurationMs());
    }

    /**
     * A completion pass fires from inside session stop: the cycle event has already gone and the
     * cycle's output is already paid out, so the two cycle-scoped kinds must drop rather than queue
     * for a cycle that never comes. An effect is not cycle-scoped and still lands.
     */
    @Test
    void completionPass_dropsTheCycleScopedKindsAndKeepsTheEffect() {
        StationLootEngine.GrantResult result = pass(StationLootEngine.TRIGGER_COMPLETION,
                LootFixtures.outputItems(2.0),
                LootFixtures.contribution("yourmod:fixture", 5.0),
                LootFixtures.effect("Fixture_Effect"));

        assertEquals(0.0, result.getOutputItems());
        assertTrue(result.getContributions().isEmpty());
        assertEquals(1, result.getEffectGrants().size());
    }

    @Test
    void aRollWhoseTriggerDoesNotMatch_isNeverEvaluated() {
        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(
                List.of(cycleRoll(LootFixtures.outputItems(3.0))),
                StationLootEngine.TRIGGER_COMPLETION, FactorLookup.none(), () -> 0.0,
                null, null, WORKER, null, "fixture");

        assertEquals(0.0, result.getOutputItems());
        assertFalse(result.anyGranted());
    }

    /**
     * The per-pass registry is SEEDED from the process-wide vocabulary, so a kind another mod
     * registered pays out at a station exactly as it does anywhere else - the station kinds overlay
     * it rather than replacing it.
     */
    @Test
    void aProcessWideKind_stillPaysOutInsideAStationPass() {
        RewardKinds.clear();
        AtomicInteger paid = new AtomicInteger();
        RewardHandler handler = (spec, subject) -> paid.incrementAndGet();
        RewardKinds.shared().register("fixture:elsewhere", handler);
        try {
            RewardKindRegistry perPass = StationRewardKinds.forPass(
                    new StationLootEngine.GrantResult(true));

            assertTrue(perPass.isRegistered("fixture:elsewhere"));
            assertTrue(perPass.isRegistered(StationRewardKinds.KIND_OUTPUT_ITEMS));
        } finally {
            RewardKinds.clear();
        }
    }
}
