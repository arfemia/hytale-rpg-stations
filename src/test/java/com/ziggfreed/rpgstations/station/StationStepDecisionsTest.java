package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The pure decision cores behind the {@code station.step} conditions gate, the Goto branch
 * mechanism, and a {@code Wait} step's suspend/resume math (design section 9.3) - the "sessions
 * suspend/resume across ticks" claim, verified WITHOUT a live server via a two-tick simulation.
 */
public class StationStepDecisionsTest {

    // ==================== Wait suspend/resume (the kernel's binding resume contract) ====================

    @Test
    void wait_firstEntry_commitsAFreshDeadlineAndIsNotYetDue() {
        long now = 1_000L;
        long durationMs = 500L;
        long deadline = StationStepDecisions.commitOrReadDeadline(now, durationMs, 0L);
        assertEquals(1_500L, deadline);
        assertFalse(StationStepDecisions.waitDue(now, deadline), "not due at the moment it commits");
    }

    @Test
    void wait_reEntry_readsTheCommittedDeadlineVerbatim_neverReDerives() {
        long committedDeadline = 1_500L;
        // A re-entry passes a DIFFERENT durationMs (simulating a naive re-derive bug) - the
        // stored deadline must win regardless, per the kernel's binding resume contract.
        long deadline = StationStepDecisions.commitOrReadDeadline(2_000L, 999_999L, committedDeadline);
        assertEquals(committedDeadline, deadline, "a re-entry NEVER re-derives a fresh window");
    }

    @Test
    void wait_twoTickSimulation_suspendsThenResumesThenCompletes() {
        long durationMs = 500L;
        long storedDeadline = 0L; // "not yet committed" sentinel, matching StationSession.stepDeadlineMs

        // Tick 1 (t=1000): first entry - commits the deadline, not yet due.
        long tick1Now = 1_000L;
        storedDeadline = StationStepDecisions.commitOrReadDeadline(tick1Now, durationMs, storedDeadline);
        assertFalse(StationStepDecisions.waitDue(tick1Now, storedDeadline));

        // Tick 2 (t=1200): re-entry BEFORE the deadline - still suspended, deadline unchanged.
        long tick2Now = 1_200L;
        long deadlineAtTick2 = StationStepDecisions.commitOrReadDeadline(tick2Now, durationMs, storedDeadline);
        assertEquals(storedDeadline, deadlineAtTick2, "the deadline never moves across re-entries");
        assertFalse(StationStepDecisions.waitDue(tick2Now, deadlineAtTick2));

        // Tick 3 (t=1600): re-entry AFTER the deadline - due, the step completes.
        long tick3Now = 1_600L;
        assertTrue(StationStepDecisions.waitDue(tick3Now, storedDeadline));
    }

    // ==================== Generic per-step Presentation entry (maintainer-approved extension) ====================

    @Test
    void presentationEntry_freshDispatch_playsForAnyNonPresentStep() {
        StationStep wait = StationStep.of("strike1", StationStep.TYPE_WAIT);
        assertTrue(StationStepDecisions.shouldEmitPresentationOnEntry(wait, null),
                "a fresh (non-resuming) dispatch has no resumingStep - every step plays its own Presentation");
    }

    @Test
    void presentationEntry_presentTypedStep_neverPlaysGenerically_evenFresh() {
        StationStep present = StationStep.of("finale", StationStep.TYPE_PRESENT);
        assertFalse(StationStepDecisions.shouldEmitPresentationOnEntry(present, null),
                "a Present-typed step's OWN handler already emits - the generic hook must not double-play it");
    }

    @Test
    void presentationEntry_resumeReCheckOfTheSuspendedStep_doesNotReplay() {
        StationStep wait = StationStep.of("strike1", StationStep.TYPE_WAIT);
        // The exact SAME object identity as the step a resume re-enters (StationSession
        // .activeProgramSteps is the same List reference across a suspend/resume pair).
        assertFalse(StationStepDecisions.shouldEmitPresentationOnEntry(wait, wait),
                "the resume re-check of an already-started Wait must not replay its Presentation");
    }

    @Test
    void presentationEntry_resumeDispatch_stillPlaysForALaterFreshStep() {
        StationStep resumedWait = StationStep.of("strike1", StationStep.TYPE_WAIT);
        StationStep laterWait = StationStep.of("strike2", StationStep.TYPE_WAIT);
        // Within the SAME resumed walk, once the resumed step succeeds and the walk advances to a
        // DIFFERENT step object, that later step is a genuine fresh entry and must still play.
        assertTrue(StationStepDecisions.shouldEmitPresentationOnEntry(laterWait, resumedWait),
                "a later step reached within a resumed walk is still its OWN fresh entry");
    }

    // ==================== Conditions gate (design 9.3's "Branch is NOT a step type") ====================

    private static final StationService.FactorLookup ALWAYS_TEN = (factorId, param) -> 10.0;
    private static final StationService.FactorLookup UNKNOWN = (factorId, param) -> null;

    @Test
    void conditions_absent_pass() {
        assertEquals(StationStepDecisions.ConditionOutcome.PASS,
                StationStepDecisions.resolveConditionOutcome(null, null, ALWAYS_TEN));
        assertEquals(StationStepDecisions.ConditionOutcome.PASS,
                StationStepDecisions.resolveConditionOutcome(new Condition[0], null, ALWAYS_TEN));
    }

    @Test
    void conditions_allPass_resolvesPass() {
        Condition[] conditions = {Condition.of("x", null, 5.0, null)};
        assertEquals(StationStepDecisions.ConditionOutcome.PASS,
                StationStepDecisions.resolveConditionOutcome(conditions, null, ALWAYS_TEN));
    }

    @Test
    void conditions_failWithNoOnConditionFail_defaultsToFail() {
        Condition[] conditions = {Condition.of("x", null, 50.0, null)};
        assertEquals(StationStepDecisions.ConditionOutcome.FAIL,
                StationStepDecisions.resolveConditionOutcome(conditions, null, ALWAYS_TEN));
    }

    @Test
    void conditions_failWithSkipResult_resolvesSkip() {
        Condition[] conditions = {Condition.of("x", null, 50.0, null)};
        StationStep.OnConditionFail skip = StationStep.OnConditionFail.of(StationStep.OnConditionFail.RESULT_SKIP, null);
        assertEquals(StationStepDecisions.ConditionOutcome.SKIP,
                StationStepDecisions.resolveConditionOutcome(conditions, skip, ALWAYS_TEN));
    }

    @Test
    void conditions_unknownFactor_failsClosed() {
        Condition[] conditions = {Condition.of("unregistered:factor", null, null, null)};
        assertEquals(StationStepDecisions.ConditionOutcome.FAIL,
                StationStepDecisions.resolveConditionOutcome(conditions, null, UNKNOWN));
    }

    // ==================== Goto resolution ====================

    @Test
    void gotoTarget_findsMatchingStepIdCaseInsensitively() {
        List<StationStep> steps = List.of(
                StationStep.of("a", StationStep.TYPE_WAIT),
                StationStep.of("b", StationStep.TYPE_ROLL),
                StationStep.of("c", StationStep.TYPE_PRESENT));
        assertEquals(2, StationStepDecisions.resolveGotoTarget(steps, "C"));
    }

    @Test
    void gotoTarget_unknownId_returnsNegativeOne() {
        List<StationStep> steps = List.of(StationStep.of("a", StationStep.TYPE_WAIT));
        assertEquals(-1, StationStepDecisions.resolveGotoTarget(steps, "nope"));
    }

    @Test
    void gotoTarget_blankOrNullId_returnsNegativeOne() {
        List<StationStep> steps = List.of(StationStep.of("a", StationStep.TYPE_WAIT));
        assertEquals(-1, StationStepDecisions.resolveGotoTarget(steps, null));
        assertEquals(-1, StationStepDecisions.resolveGotoTarget(steps, "  "));
    }
}
