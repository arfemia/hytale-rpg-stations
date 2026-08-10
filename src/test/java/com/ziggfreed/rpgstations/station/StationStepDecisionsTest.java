package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.FactorRef;
import com.ziggfreed.rpgstations.asset.Presentation;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationStep;

/**
 * The pure decision cores behind the {@code station.step} conditions gate, the Goto branch, the
 * per-step {@code Repeat} count, and a {@code Duration} hold's suspend/resume math (scope-2 design
 * 2.1) - verified WITHOUT a live server, incl. a two-tick suspend/resume simulation.
 */
public class StationStepDecisionsTest {

    private static final java.util.function.BiFunction<String, String, Double> ALWAYS_TEN_BI = (f, p) -> 10.0;

    // ==================== Duration suspend/resume (reuses the retired Wait math verbatim) ====================

    @Test
    void duration_firstEntry_commitsAFreshDeadlineAndIsNotYetDue() {
        long deadline = StationStepDecisions.commitOrReadDeadline(1_000L, 500L, 0L);
        assertEquals(1_500L, deadline);
        assertFalse(StationStepDecisions.durationDue(1_000L, deadline), "not due at the moment it commits");
    }

    @Test
    void duration_reEntry_readsTheCommittedDeadlineVerbatim_neverReDerives() {
        long deadline = StationStepDecisions.commitOrReadDeadline(2_000L, 999_999L, 1_500L);
        assertEquals(1_500L, deadline, "a re-entry NEVER re-derives a fresh window");
    }

    @Test
    void duration_twoTickSimulation_suspendsThenResumesThenCompletes() {
        long durationMs = 500L;
        long stored = 0L; // "not yet committed" sentinel, matching StationSession.stepDeadlineMs

        long t1 = 1_000L;
        stored = StationStepDecisions.commitOrReadDeadline(t1, durationMs, stored);
        assertFalse(StationStepDecisions.durationDue(t1, stored));

        long t2 = 1_200L;
        long atT2 = StationStepDecisions.commitOrReadDeadline(t2, durationMs, stored);
        assertEquals(stored, atT2, "the deadline never moves across re-entries");
        assertFalse(StationStepDecisions.durationDue(t2, atT2));

        long t3 = 1_600L;
        assertTrue(StationStepDecisions.durationDue(t3, stored));
    }

    // ==================== Repeat resolution ====================

    @Test
    void repeat_null_isOneIteration() {
        assertEquals(1, StationStepDecisions.resolveRepeatCount(null, 0.0));
        assertEquals(0.0, StationStepDecisions.repeatFactorContribution(null, ALWAYS_TEN_BI));
    }

    @Test
    void repeat_fixedTimes_wins_andContributesNoFactor() {
        StationStep.Repeat r = StationStep.Repeat.times(3);
        assertEquals(3, StationStepDecisions.resolveRepeatCount(r, 99.0), "a fixed Times ignores any factor contribution");
        assertEquals(0.0, StationStepDecisions.repeatFactorContribution(r, ALWAYS_TEN_BI),
                "a fixed Times never sums its (absent) AddFactors");
    }

    @Test
    void repeat_rangedWithFactors_clampsRoundMinPlusContribution() {
        // Min 1, Max 4, one factor weight 0.2 over a value of 10 -> contribution 2.0 -> round(1+2)=3.
        StationStep.Repeat r = StationStep.Repeat.of(null, 1, 4,
                new FactorRef[]{FactorRef.of("stat", "MMO_X", 0.2)});
        double contribution = StationStepDecisions.repeatFactorContribution(r, ALWAYS_TEN_BI);
        assertEquals(2.0, contribution, 1e-9);
        assertEquals(3, StationStepDecisions.resolveRepeatCount(r, contribution));
    }

    @Test
    void repeat_rangedClampsToMax() {
        StationStep.Repeat r = StationStep.Repeat.of(null, 1, 2,
                new FactorRef[]{FactorRef.of("stat", "MMO_X", 1.0)});
        double contribution = StationStepDecisions.repeatFactorContribution(r, ALWAYS_TEN_BI); // 10.0
        assertEquals(2, StationStepDecisions.resolveRepeatCount(r, contribution), "clamped to Max");
    }

    // ==================== Per-iteration-entry presentation ====================

    @Test
    void presentationEntry_freshEntry_playsWhenStepAuthorsAPresentation() {
        StationStep step = StationStep.of("beat").withPresentation(Presentation.ofSound("SFX"));
        assertTrue(StationStepDecisions.shouldEmitPresentationOnEntry(step, null, false));
    }

    @Test
    void presentationEntry_noPresentationAnywhere_neverPlays() {
        assertFalse(StationStepDecisions.shouldEmitPresentationOnEntry(StationStep.of("beat"), null, false));
    }

    @Test
    void presentationEntry_theActionsOwnMomentsEntryIsEnoughOnItsOwn() {
        assertTrue(StationStepDecisions.shouldEmitPresentationOnEntry(StationStep.of("beat"), null, true),
                "a step with no Presentation still has a moment to play when the action authored one for it");
    }

    @Test
    void presentationEntry_resumeReCheckOfTheSuspendedStep_doesNotReplay() {
        StationStep step = StationStep.of("beat").withPresentation(Presentation.ofSound("SFX"));
        assertFalse(StationStepDecisions.shouldEmitPresentationOnEntry(step, step, false),
                "the resume re-check of an already-started step must not replay its Presentation");
        assertFalse(StationStepDecisions.shouldEmitPresentationOnEntry(step, step, true),
                "and an action-authored moment for it does not reopen that door either");
    }

    // ==================== Which moment id a step's entry cue plays under ====================

    @Test
    void momentId_authoredStepId_isThePerStepId() {
        assertEquals("step:mill:chop", StationStepDecisions.momentIdForStep("Mill", "Chop", false),
                "both halves lowercase, so a PascalCase-authored pair composes to one stable id");
    }

    @Test
    void momentId_stepWithNoId_fallsBackToTheActionWideCycleMoment() {
        assertEquals(StationFlairs.MOMENT_CYCLE, StationStepDecisions.momentIdForStep("mill", null, false));
        assertEquals(StationFlairs.MOMENT_CYCLE, StationStepDecisions.momentIdForStep("mill", "  ", false));
    }

    @Test
    void momentId_implicitProgram_isTheCycleMoment_neverAStepIdNoAuthorWrote() {
        assertEquals(StationFlairs.MOMENT_CYCLE,
                StationStepDecisions.momentIdForStep("mill", ImplicitProgram.ID_WORK, true),
                "the implicit convert loop's one synthesized step IS the cycle, so a flair targets it as 'cycle'");
    }

    // ==================== The action-authored route into a step's entry cue ====================

    @Test
    void actionMoments_driveAStepCueOnlyForAPerStepMomentId() {
        Map<String, Presentation> moments = Map.of("step:mill:chop", Presentation.ofSound("SFX"));
        assertTrue(StationStepDecisions.actionAuthorsStepMoment(moments, "step:mill:chop"));
        assertTrue(StationStepDecisions.actionAuthorsStepMoment(moments, "STEP:MILL:CHOP"),
                "the lookup lowercases to match the session's canonicalized snapshot");
        assertFalse(StationStepDecisions.actionAuthorsStepMoment(moments, "step:mill:sand"));
    }

    @Test
    void actionMoments_cycleEntry_neverDrivesAnUnnamedStepsEntryCue() {
        Map<String, Presentation> moments = Map.of(StationFlairs.MOMENT_CYCLE, Presentation.ofSound("SFX"));
        assertFalse(StationStepDecisions.actionAuthorsStepMoment(moments, StationFlairs.MOMENT_CYCLE),
                "an unnamed step resolves to the action-wide cycle moment, which the cycle machinery owns -"
                        + " honoring it here would replay the cycle cue once per beat");
    }

    @Test
    void actionMoments_absentMap_isNeverARoute() {
        assertFalse(StationStepDecisions.actionAuthorsStepMoment(null, "step:mill:chop"));
    }

    // ==================== Step-synced puppet clip ====================

    private static StationStep withClip(String id, String clip) {
        return StationStep.of(id).withPuppet(StationStep.PuppetOverride.of(clip, null));
    }

    @Test
    void clipEntry_freshEntry_stepAuthorsClip_plays() {
        assertTrue(StationStepDecisions.shouldPlayClipOnEntry(withClip("strike1", "MMO_Emote_Hammer"), null));
    }

    @Test
    void clipEntry_noClip_neverPlays() {
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(StationStep.of("settle"), null));
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(withClip("s", "   "), null));
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(withClip("s", null), null));
    }

    @Test
    void clipEntry_resumeReCheckOfTheSuspendedStep_doesNotReplay() {
        StationStep strike = withClip("strike1", "MMO_Emote_Hammer");
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(strike, strike));
    }

    @Test
    void programAuthorsAnyStepClip_variants() {
        assertFalse(StationStepDecisions.programAuthorsAnyStepClip(null));
        assertFalse(StationStepDecisions.programAuthorsAnyStepClip(List.of()));
        assertFalse(StationStepDecisions.programAuthorsAnyStepClip(List.of(StationStep.of("settle"))));
        assertTrue(StationStepDecisions.programAuthorsAnyStepClip(
                List.of(withClip("strike1", "MMO_Emote_Hammer"), StationStep.of("settle"))));
    }

    // ==================== Step-synced puppet PROP swap ====================

    @Test
    void propSync_everyFreshEntrySyncs_evenWithoutAnOverride() {
        StationStep stamp = StationStep.of("stamp")
                .withPuppet(StationStep.PuppetOverride.of(null, Puppet.Prop.of(Puppet.PROP_SOURCE_NONE, null, null)));
        assertTrue(StationStepDecisions.shouldSyncPropOnEntry(stamp, null));
        assertTrue(StationStepDecisions.shouldSyncPropOnEntry(StationStep.of("settle"), null),
                "even a step with no prop override syncs on fresh entry (reverts to the session default)");
    }

    @Test
    void propSync_resumeReCheckDoesNotReSync() {
        StationStep stamp = StationStep.of("stamp");
        assertFalse(StationStepDecisions.shouldSyncPropOnEntry(stamp, stamp));
    }

    // ==================== Conditions gate ====================

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
        List<StationStep> steps = List.of(StationStep.of("a"), StationStep.of("b"), StationStep.of("c"));
        assertEquals(2, StationStepDecisions.resolveGotoTarget(steps, "C"));
    }

    @Test
    void gotoTarget_unknownOrBlankId_returnsNegativeOne() {
        List<StationStep> steps = List.of(StationStep.of("a"));
        assertEquals(-1, StationStepDecisions.resolveGotoTarget(steps, "nope"));
        assertEquals(-1, StationStepDecisions.resolveGotoTarget(steps, null));
        assertEquals(-1, StationStepDecisions.resolveGotoTarget(steps, "  "));
    }
}
