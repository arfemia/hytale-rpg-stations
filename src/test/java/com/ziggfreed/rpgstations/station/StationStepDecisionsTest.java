package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.asset.Condition;
import com.ziggfreed.rpgstations.asset.Puppet;
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

    // ==================== Step-synced puppet swings (maintainer-approved, round-8) ====================

    private static StationStep waitWithClip(String id, String clip) {
        return StationStep.of(id, StationStep.TYPE_WAIT)
                .withPuppet(StationStep.PuppetOverride.of(clip, null));
    }

    @Test
    void clipEntry_freshDispatch_stepAuthorsClip_plays() {
        StationStep strike = waitWithClip("strike1", "MMO_Emote_Hammer");
        assertTrue(StationStepDecisions.shouldPlayClipOnEntry(strike, null),
                "a fresh (non-resuming) dispatch plays a step's own authored Puppet.Clip at its iteration entry");
    }

    @Test
    void clipEntry_stepAuthorsNoPuppet_neverPlays() {
        StationStep settle = StationStep.of("settle", StationStep.TYPE_WAIT);
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(settle, null),
                "a step with no Puppet override authors no clip - nothing to play (it idles)");
    }

    @Test
    void clipEntry_stepAuthorsBlankClip_neverPlays() {
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(waitWithClip("s", "   "), null),
                "a blank Clip is 'inherit the default', owned by the generic swing - not a per-step-entry play");
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(waitWithClip("s", null), null),
                "an absent Clip (Prop-only override) never plays a clip at entry");
    }

    @Test
    void clipEntry_resumeReCheckOfTheSuspendedStep_doesNotReplay() {
        StationStep strike = waitWithClip("strike1", "MMO_Emote_Hammer");
        // The exact SAME object identity as the step a resume re-enters must not replay its clip
        // on every heartbeat re-check while suspended - only ONCE when it BEGINS.
        assertFalse(StationStepDecisions.shouldPlayClipOnEntry(strike, strike),
                "the resume re-check of an already-started Wait must not replay its Puppet.Clip");
    }

    @Test
    void clipEntry_resumeDispatch_stillPlaysForALaterFreshStep() {
        StationStep resumedStrike = waitWithClip("strike1", "MMO_Emote_Hammer");
        StationStep laterStrike = waitWithClip("strike2", "MMO_Emote_Hammer");
        assertTrue(StationStepDecisions.shouldPlayClipOnEntry(laterStrike, resumedStrike),
                "a later clip-authoring step reached within a resumed walk is still its OWN fresh iteration entry");
    }

    @Test
    void programAuthorsAnyStepClip_nullOrEmpty_false() {
        assertFalse(StationStepDecisions.programAuthorsAnyStepClip(null));
        assertFalse(StationStepDecisions.programAuthorsAnyStepClip(List.of()));
    }

    @Test
    void programAuthorsAnyStepClip_noStepAuthorsAClip_false() {
        List<StationStep> steps = List.of(
                StationStep.of("settle", StationStep.TYPE_WAIT),
                StationStep.of("stamp", StationStep.TYPE_STAMP)
                        .withPuppet(StationStep.PuppetOverride.of(null, null)));
        assertFalse(StationStepDecisions.programAuthorsAnyStepClip(steps),
                "a program whose every step inherits the default clip keeps its one generic engage swing");
    }

    @Test
    void programAuthorsAnyStepClip_anyStepAuthorsAClip_true() {
        List<StationStep> steps = List.of(
                waitWithClip("strike1", "MMO_Emote_Hammer"),
                StationStep.of("settle", StationStep.TYPE_WAIT));
        assertTrue(StationStepDecisions.programAuthorsAnyStepClip(steps),
                "one clip-authoring step is enough to suppress the generic swing for the whole program");
    }

    // ==================== Step-synced puppet PROP swap (round-8 continuation) ====================

    private static StationStep waitWithProp(String id, String propSource) {
        return StationStep.of(id, StationStep.TYPE_WAIT)
                .withPuppet(StationStep.PuppetOverride.of(null, Puppet.Prop.of(propSource, null, null)));
    }

    @Test
    void propSync_freshDispatch_propOverridingStep_syncs() {
        StationStep stamp = waitWithProp("stamp", Puppet.PROP_SOURCE_NONE);
        assertTrue(StationStepDecisions.shouldSyncPropOnEntry(stamp, null),
                "a fresh (non-resuming) dispatch syncs a step's own Puppet.Prop override at its iteration entry");
    }

    @Test
    void propSync_freshDispatch_stepAuthorsNoPropOverride_stillSyncsToRevertToDefault() {
        // The load-bearing difference from the clip gate: even a step authoring NO prop override
        // syncs on fresh entry - that is how the prop reverts to the session default when the program
        // moves PAST a prop-overriding step (the exit edge), made consistent with the swing-beat sync.
        StationStep plain = StationStep.of("settle", StationStep.TYPE_WAIT);
        assertTrue(StationStepDecisions.shouldSyncPropOnEntry(plain, null),
                "a step authoring no Puppet.Prop still syncs on fresh entry (reverts the prop to the session default)");
        StationStep clipOnly = waitWithClip("strike1", "MMO_Emote_Hammer");
        assertTrue(StationStepDecisions.shouldSyncPropOnEntry(clipOnly, null),
                "a clip-only step (Prop absent) still syncs on fresh entry - the exit edge is not gated on authoring a prop");
    }

    @Test
    void propSync_resumeReCheckOfTheSuspendedStep_doesNotReSync() {
        StationStep stamp = waitWithProp("stamp", Puppet.PROP_SOURCE_NONE);
        // The exact SAME object identity as the step a resume re-enters must not re-sync its prop on
        // every heartbeat re-check while suspended - the swing-beat suspension-gated sync already
        // HOLDS the suspended step's prop, so re-syncing here would be redundant.
        assertFalse(StationStepDecisions.shouldSyncPropOnEntry(stamp, stamp),
                "the resume re-check of an already-started step must not re-sync its Puppet.Prop");
    }

    @Test
    void propSync_resumeDispatch_stillSyncsForALaterFreshStep() {
        StationStep resumedStrike = waitWithClip("strike1", "MMO_Emote_Hammer");
        StationStep laterStamp = waitWithProp("stamp", Puppet.PROP_SOURCE_NONE);
        assertTrue(StationStepDecisions.shouldSyncPropOnEntry(laterStamp, resumedStrike),
                "a later step reached within a resumed walk is its OWN fresh entry - it syncs its prop (the empty-hands swap)");
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
