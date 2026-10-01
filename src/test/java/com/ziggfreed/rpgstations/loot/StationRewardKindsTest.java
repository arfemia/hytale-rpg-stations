package com.ziggfreed.rpgstations.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootGrants;
import com.ziggfreed.common.loot.LootableValidator;
import com.ziggfreed.common.loot.Roll;
import com.ziggfreed.common.loot.reward.RewardGrants;
import com.ziggfreed.common.loot.reward.RewardHandler;
import com.ziggfreed.common.loot.reward.RewardKindRegistry;
import com.ziggfreed.common.loot.reward.RewardKinds;
import com.ziggfreed.common.loot.reward.RewardSpec;
import com.ziggfreed.common.subject.Subject;
import com.ziggfreed.common.validation.Finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three payouts a station owns are registered reward KINDS that COLLECT onto the pass rather
 * than acting. They live in the ONE shared vocabulary, and a pass hands each grant its own sink on
 * the subject. These tests pin that contract: each kind reaches its pass's own sink, a completion
 * pass refuses the two cycle-scoped ones, a refused grant still earns the cue beside it, a station
 * kind with no pass around it counts lost, and the process-wide vocabulary reaches a station roll
 * unchanged.
 *
 * <p>The nested case (a {@code Lootable} reward rolled at a station paying its table's station kinds
 * into the pass) needs a live player, which nothing here can build; Ziggfreed Common's own
 * {@code CollectingRewardKindTest} pins that a nested pass forwards the very subject this pass builds.
 *
 * <p>Fixture ids and amounts are this test's own and deliberately mirror no shipped content.
 */
class StationRewardKindsTest {

    /** Who the pass pays; the three station kinds collect rather than pay, so it goes unread. */
    private static final Subject WORKER =
            Subject.of(UUID.fromString("55555555-5555-5555-5555-555555555555"), "Fixture");

    /**
     * The shared vocabulary is process-wide and a reset anywhere empties it, so every case registers
     * the station kinds again rather than trusting whatever ran before it.
     */
    @BeforeEach
    void registerTheStationKinds() {
        StationRewardKinds.registerInto(RewardKinds.shared());
    }

    private static Roll cycleRoll(LootGrants grants) {
        return Roll.of(StationLootEngine.TRIGGER_CYCLE, null, null, null, grants, null);
    }

    /** Runs one pass with no world at all: no item sink, no drop lists, no commands. */
    private static StationLootEngine.GrantResult pass(String trigger, LootGrants... grants) {
        Roll[] rolls = new Roll[grants.length];
        for (int i = 0; i < grants.length; i++) {
            rolls[i] = Roll.of(trigger, null, null, null, grants[i], null);
        }
        return StationLootEngine.rollAndGrant(new LootEngine.Resolved(List.of(rolls), List.of()),
                trigger, FactorLookup.none(), () -> 0.0, null, null, WORKER, null, "fixture");
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

    /**
     * A grant the pass drops returns quietly rather than failing, so it counts as paid, and the
     * smart-cue rule reads it as produced: a {@code Cue} authored beside a contribution or an
     * output-item reward on a {@code Completion} roll still plays.
     */
    @Test
    void completionPass_aDroppedCycleGrantBesideACue_stillEarnsTheCue() {
        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(
                new LootEngine.Resolved(List.of(
                        Roll.of(StationLootEngine.TRIGGER_COMPLETION, null, null, null,
                                LootFixtures.contribution("yourmod:fixture", 5.0), "Cue:Fixture_Contribution"),
                        Roll.of(StationLootEngine.TRIGGER_COMPLETION, null, null, null,
                                LootFixtures.outputItems(2.0), "Cue:Fixture_Output")), List.of()),
                StationLootEngine.TRIGGER_COMPLETION, FactorLookup.none(), () -> 0.0,
                null, null, WORKER, null, "fixture");

        assertTrue(result.getContributions().isEmpty(), "the contribution itself is dropped");
        assertEquals(0.0, result.getOutputItems(), "and so are the output items");
        assertEquals(List.of("Cue:Fixture_Contribution", "Cue:Fixture_Output"), result.getCues());
    }

    @Test
    void aRollWhoseTriggerDoesNotMatch_isNeverEvaluated() {
        StationLootEngine.GrantResult result = StationLootEngine.rollAndGrant(
                new LootEngine.Resolved(List.of(cycleRoll(LootFixtures.outputItems(3.0))), List.of()),
                StationLootEngine.TRIGGER_COMPLETION, FactorLookup.none(), () -> 0.0,
                null, null, WORKER, null, "fixture");

        assertEquals(0.0, result.getOutputItems());
        assertFalse(result.anyGranted());
    }

    /**
     * The station pass pays through the shared vocabulary itself, so a kind another mod registered
     * pays out at a station exactly as it does anywhere else, and the subject it is handed carries
     * the pass's own sink, answered by type.
     */
    @Test
    void aProcessWideKind_paysOnceInsideAStationPass_andItsSubjectAnswersTheSink() {
        AtomicInteger paid = new AtomicInteger();
        AtomicReference<Subject> paidTo = new AtomicReference<>();
        RewardHandler handler = (spec, subject) -> {
            paid.incrementAndGet();
            paidTo.set(subject);
        };
        RewardKinds.shared().register("fixture:elsewhere", "fixture", handler);

        StationLootEngine.GrantResult result = pass(StationLootEngine.TRIGGER_CYCLE,
                LootGrants.of(null, null, null, new LootGrants.Reward[] {
                        LootGrants.Reward.of("fixture:elsewhere", Map.of())}));

        assertEquals(1, paid.get(), "the shared kind paid exactly once");
        assertSame(result, paidTo.get().handleAs(StationRewardKinds.Sink.class),
                "the subject the pass pays carries this pass's own sink");
        assertEquals(WORKER.id(), paidTo.get().id(), "the worker's identity is kept");
        assertEquals(WORKER.name(), paidTo.get().name());
    }

    /** With no pass around it there is no sink to collect onto: the grant counts lost, not paid. */
    @Test
    void aStationKind_grantedWithNoPassAroundIt_countsLost() {
        List<String> warnings = new ArrayList<>();
        RewardGrants.GrantOutcome outcome = RewardGrants.grantAll(
                List.of(RewardSpec.of(StationRewardKinds.KIND_OUTPUT_ITEMS, Map.of("Count", "1"))),
                WORKER, "fixture", RewardKinds.shared(), null, warnings::add);

        assertEquals(0, outcome.granted());
        assertEquals(1, outcome.failed());
        assertEquals(1, warnings.size(), "the loss is reported: " + warnings);
    }

    /** Registered once, a station kind is known to the shared loot-table audit. */
    @Test
    void aRollAuthoringAStationKind_isKnownToTheSharedAudit() {
        Roll roll = cycleRoll(LootFixtures.outputItems(1.0));

        assertFalse(codes(LootableValidator.auditRoll(roll, "fixture", RewardKinds.shared()))
                .contains(LootableValidator.UNKNOWN_REWARD_KIND));
        assertTrue(codes(LootableValidator.auditRoll(roll, "fixture", new RewardKindRegistry()))
                        .contains(LootableValidator.UNKNOWN_REWARD_KIND),
                "the same roll against a vocabulary without the station kinds is still flagged");
    }

    /** A session with no resolvable player reference still pays an anonymous, never-null subject. */
    @Test
    void theSubjectOfAnUnidentifiedWorker_isAnonymous() {
        Subject subject = StationLootEngine.subjectOf(null, null);

        assertEquals(new UUID(0L, 0L), subject.id());
        assertEquals("", subject.name());
        assertNull(subject.handleAs(PlayerRef.class));
    }

    private static List<String> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).toList();
    }
}
