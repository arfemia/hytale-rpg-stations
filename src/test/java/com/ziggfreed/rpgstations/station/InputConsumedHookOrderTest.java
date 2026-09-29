package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The ONE input-consumed hook's contract, pinned PER CONSUMPTION PATH on the source itself: every
 * path below runs only against a live store and inventory (nothing in a unit JVM can drive it), so
 * the order is read off the method bodies. On every path the consumption lands first, then it
 * COMMITS, then it reaches {@code StationService#onInputConsumed}, once; a consumption that is
 * refunded never reaches it.
 *
 * <ul>
 *   <li><b>A session consume</b> (a {@code Consume} phase, or a {@code Convert} phase's drain):
 *       the consume body records its batch into the ledger's hook half, never the hook itself;
 *       the commit ({@code commitIteration}: each {@code Produce} route, the {@code Convert}
 *       phase's end, the completed program pass) takes the ledger and THEN reports; the refund at
 *       stop drops the hook half unreported.</li>
 *   <li><b>A {@code Stamp} phase's reagents</b>: drained, then committed with the enhanced
 *       stack, then reported.</li>
 *   <li><b>An unattended settle</b>: the transform drains, the stash is marked, then the
 *       consumption is reported with no worker.</li>
 * </ul>
 *
 * <p>The hook has exactly three call sites, one per commit shape; a fourth path must be pinned
 * here before it can reach the hook. Renaming one of these methods fails this test on purpose:
 * the order is the contract a consumer mod's input-consumed event and objective are built on.
 */
class InputConsumedHookOrderTest {

    private static final String HOOK = "onInputConsumed(";

    private static String handlers() throws IOException {
        return SourcePins.read("StationStepHandlers.java");
    }

    private static String service() throws IOException {
        return SourcePins.read("StationService.java");
    }

    private static void inOrder(String body, String... calls) {
        int previous = -1;
        String previousCall = null;
        for (String call : calls) {
            int at = body.indexOf(call, previous + 1);
            assertTrue(at >= 0, "missing " + call + (previousCall != null ? " after " + previousCall : ""));
            previous = at;
            previousCall = call;
        }
    }

    // ==================== a session consume: recorded, committed, then reported ====================

    @Test
    void theConsumeBody_recordsItsBatchForTheCommit_andNeverReportsItself() throws IOException {
        String consume = SourcePins.methodBody(handlers(), "static StationStepResult consumeItems(");
        inOrder(consume, "removeItemStack(input)", "StationService.recordIterationConsumedInputs(ctx.session, null, taken);",
                "return null;");
        String custody = SourcePins.methodBody(handlers(), "private static StationStepResult consumeFromCustody(");
        inOrder(custody, "StationCustody.drainFromPile(", "claim.takeUniqueIfDrained(socketId)",
                "StationService.recordIterationConsumedInputs(ctx.session, step.getAt(), taken);", "return null;");
        for (String body : List.of(consume, custody)) {
            assertEquals(-1, body.indexOf(HOOK), "a consume never reaches the hook itself");
            assertEquals(-1, body.indexOf("commitIteration("), "a consume is never its own commit");
        }
    }

    @Test
    void eachProduceRoute_commitsAfterItsOutputsLand() throws IOException {
        String produce = SourcePins.methodBody(handlers(), "static StationStepResult produceItems(");
        assertEquals(2, SourcePins.count(produce, "StationService.commitIteration(ctx.session, ctx.store);"),
                "one commit per produce route (To:Custody and To:Inventory)");
        inOrder(produce, "produceIntoCustody(", "StationService.commitIteration(ctx.session, ctx.store);",
                "ItemGrantUtil.grantOrDrop(", "StationService.commitIteration(ctx.session, ctx.store);");
        assertEquals(-1, produce.indexOf(HOOK), "the produce reaches the hook only through the commit");
    }

    @Test
    void theConvertPhase_consumesThenProduces_andCommitsLast() throws IOException {
        String body = SourcePins.methodBody(handlers(), "static StationStepResult convertPhase(");
        inOrder(body, "consumeItems(ctx, step, consume)", "produceItems(ctx, step,", "return produced;",
                "StationService.commitIteration(s, ctx.store);");
        assertEquals(1, SourcePins.count(body, "commitIteration("),
                "one commit, the last call: no earlier commit can close the iteration before its produce lands");
        assertEquals(-1, body.indexOf(HOOK), "convertPhase never calls the hook directly");
    }

    @Test
    void aCompletedPass_commitsAfterTheWalkCompletes() throws IOException {
        String body = SourcePins.methodBody(service(), "private boolean dispatchProgram(", "preselected");
        inOrder(body, "StationStepKernel.runResumable(ctx, startIndex)", "stop(s, fail.reason(), store, commandBuffer);",
                "s.cyclesDone++;", "commitIteration(s, store);");
        assertEquals(1, SourcePins.count(body, "commitIteration("),
                "one commit per pass, after the walk completes: no earlier commit can slip in before it");
    }

    @Test
    void theCommit_takesTheLedgerBeforeItReports() throws IOException {
        String commit = SourcePins.methodBody(service(), "static void commitIteration(");
        inOrder(commit, "commitIterationLedger(s)", "onInputConsumed(store, consumption);");
        String ledger = SourcePins.methodBody(service(), "static List<InputConsumption> commitIterationLedger(");
        inOrder(ledger, "s.iterationConsumedInputs.clear();", "clearIterationLedgerOnCommittedProduce(s);",
                "return committed;");
    }

    @Test
    void aRefund_dropsTheHookHalf_andNeverReports() throws IOException {
        String refund = SourcePins.methodBody(service(), "private void refundIterationLedger(");
        inOrder(refund, "s.iterationConsumedInputs.clear();", "if (s.iterationConsumed.isEmpty()");
        assertEquals(-1, refund.indexOf(HOOK), "a refunded consumption never reaches the hook");
        assertEquals(-1, refund.indexOf("commitIteration("), "a refund is never a commit");
    }

    // ==================== a Stamp phase's reagents: drained, committed with the stack, reported ====================

    @Test
    void theStampPhase_reportsItsReagentsAfterTheEnhancedStackCommits() throws IOException {
        String body = SourcePins.methodBody(handlers(), "static StationStepResult executeStampPhase(");
        inOrder(body, "consumeReagent(", "restoreReagents(ctx, consumedForRestore);",
                "restoreReagents(ctx, consumedForRestore);", "claim.setUniqueStack(mutation.stack());",
                "claim.markDirty();", "StationService.reportCommittedConsumption(ctx.session, ctx.store, null,");
        assertEquals(2, SourcePins.count(body, "restoreReagents(ctx, consumedForRestore);"),
                "both failure restores sit before the commit, so a restored reagent is never reported");
    }

    // ==================== an unattended settle: transformed, marked, reported with no worker ====================

    @Test
    void theUnattendedSettle_reportsAfterItsTransformIsMarked() throws IOException {
        String body = SourcePins.methodBody(service(), "private void settleUnattendedAt(");
        inOrder(body, "StationUnattended.settle(", "claim.markDirty();", "reportUnattendedConsumption(world, claim, settle);");
        String report = SourcePins.methodBody(service(), "private static void reportUnattendedConsumption(");
        inOrder(report, "ConsumedInput.fromPile(", "onInputConsumed(world.getEntityStore().getStore(), InputConsumption.unattended(");
    }

    // ==================== the hook's call sites: one per commit shape, and no other ====================

    @Test
    void theHook_hasExactlyOneCallSitePerCommitShape() throws IOException {
        String service = service();
        String callSites = SourcePins.methodBody(service, "static void commitIteration(")
                + SourcePins.methodBody(service, "static void reportCommittedConsumption(")
                + SourcePins.methodBody(service, "private static void reportUnattendedConsumption(");
        assertEquals(3, SourcePins.count(callSites, HOOK), "each commit shape calls the hook once");
        int declarations = SourcePins.count(service, "static void onInputConsumed(");
        assertEquals(1, declarations, "one hook");
        int total = 0;
        try (var files = Files.walk(SourcePins.stationSourceDir().getParent())) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                total += SourcePins.count(Files.readString(p), HOOK);
            }
        }
        assertEquals(3 + declarations, total, "no other main-tree class or method reaches the hook: a new"
                + " consumption path gets its own ordering pin here first");
    }
}
