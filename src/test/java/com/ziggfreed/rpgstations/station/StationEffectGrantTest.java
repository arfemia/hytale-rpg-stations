package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.effect.AppliedEffectTracker;
import com.ziggfreed.rpgstations.asset.EffectRef;

/**
 * F1 (decision 51d): the apply+track leg of {@code Grants.Effects[]} -
 * {@link StationService#applyAndTrackEffects} applies each granted effect through an injected seam
 * and tracks every SUCCESS on the session's {@link AppliedEffectTracker}, so {@code stop()}'s
 * teardown ({@code removeAll}) strips them. The injected applier lets "a granted effect lands in the
 * tracked list, teardown clears it" be verified without a live effect asset map (constructing an
 * engine {@code EntityEffect} throws outside a running server); the null ref/store stand-ins follow
 * ziggfreed-common's own {@code AppliedEffectTrackerTest} pattern ({@code track} only records, and
 * {@code removeAll}'s engine calls fail-closed on a null ref).
 */
class StationEffectGrantTest {

    private static final Ref<EntityStore> NULL_REF = null;
    private static final Store<EntityStore> NULL_STORE = null;

    @Test
    void grantedEffects_landInTrackedList_thenTeardownClears() {
        AppliedEffectTracker tracker = new AppliedEffectTracker();
        List<String> applied = new ArrayList<>();

        List<EffectRef> effects = List.of(
                EffectRef.of("Root", null),
                EffectRef.of("Slow", 3000L),
                EffectRef.of("", null)); // blank id - skipped (never applied, never tracked)

        StationService.applyAndTrackEffects(effects, NULL_REF, tracker, (id, dur) -> {
            applied.add(id);
            return true; // simulate a successful native apply
        });

        assertEquals(List.of("Root", "Slow"), applied, "each non-blank effect is applied once, in order");
        assertEquals(2, tracker.size(), "every successful apply lands in the tracked list");

        tracker.removeAll(NULL_STORE);
        assertTrue(tracker.isEmpty(), "teardown clears the tracked list");
    }

    @Test
    void failedApply_isNeverTracked() {
        AppliedEffectTracker tracker = new AppliedEffectTracker();
        StationService.applyAndTrackEffects(List.of(EffectRef.of("Root", null)), NULL_REF, tracker,
                (id, dur) -> false); // native apply reported no-op (unresolved id / missing controller)
        assertTrue(tracker.isEmpty(), "a failed apply leaves nothing to remove at stop()");
    }

    @Test
    void durationOverride_isThreadedToTheApplier() {
        List<Long> durations = new ArrayList<>();
        StationService.applyAndTrackEffects(
                List.of(EffectRef.of("A", null), EffectRef.of("B", 5000L)),
                NULL_REF, new AppliedEffectTracker(), (id, dur) -> {
                    durations.add(dur);
                    return true;
                });
        assertEquals(java.util.Arrays.asList(null, 5000L), durations,
                "the authored DurationMs override (null = asset TTL) reaches the applier verbatim");
    }

    /**
     * MIN-1 (arc-close): {@code StationService.stop()} calls the appliedEffects teardown
     * (removeAll) BEFORE it calls {@code rollCompletionLoot} - so a Completion-trigger roll's
     * granted effect is tracked AFTER this same stop()'s teardown already ran, and therefore is
     * deliberately NEVER stripped by it (a finishing reward that persists for its own
     * authored/asset duration, not session-scoped like the per-cycle route). Simulated here with
     * the exact same pure {@code applyAndTrackEffects}/{@code AppliedEffectTracker} seam, in the
     * REAL call order stop() uses (teardown, THEN the completion grant) - see the corrected
     * comment at {@code StationService#applyGrantResult} and {@code Roll.Grants.Effects}'
     * {@code .documentation} for the full two-route contract.
     */
    @Test
    void completionRouteEffect_appliedAfterTeardown_deliberatelyPersists_isNotTornDownByThisStop() {
        AppliedEffectTracker tracker = new AppliedEffectTracker();

        // stop()'s appliedEffects.removeAll runs first, well before rollCompletionLoot is ever
        // reached (nothing tracked yet in this simulation - a mid-session per-cycle grant, if any,
        // would already have been stripped by this same call).
        tracker.removeAll(NULL_STORE);

        // rollCompletionLoot -> applyGrantResult -> applyAndTrackEffects runs AFTER that teardown.
        StationService.applyAndTrackEffects(List.of(EffectRef.of("FinishingBuff", 30_000L)),
                NULL_REF, tracker, (id, dur) -> true);

        assertEquals(1, tracker.size(), "the completion-route effect lands in the tracked list AFTER "
                + "this stop()'s own teardown already ran, so it deliberately persists (never torn down "
                + "by this session, unlike the per-cycle route)");
    }
}
