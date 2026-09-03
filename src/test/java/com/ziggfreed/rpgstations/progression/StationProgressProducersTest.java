package com.ziggfreed.rpgstations.progression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.rpgstations.api.event.StationCycleCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationOutputProducedEvent;

/**
 * The two producers' decisions and guards, driven through the recording {@code Dispatch} seam with
 * no runtime anywhere near them. Live world-thread handles ({@code Store}/{@code PlayerRef}/
 * {@code Ref}) and {@code ItemStack} cannot be constructed in a bare unit JVM, so the positive
 * dispatch (a live worker resolving to a ref, a real stack) is a live-server boundary verified in
 * play; what is pinned here is everything that decides whether a moment fires at all, and that a
 * handler over absent handles never throws.
 */
class StationProgressProducersTest {

    private static final UUID PLAYER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID WORLD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** One recorded fire: the kind, the target, the qualifier and the amount. */
    private record Fired(String kind, String target, String qualifier, long amount) {
    }

    private final List<Fired> fired = new ArrayList<>();
    private final StationProgressProducers producers = new StationProgressProducers(
            (store, ref, commandBuffer, kind, target, qualifier, amount, payload) ->
                    fired.add(new Fired(kind, target, qualifier, amount)));

    @Test
    void kindIds_areTheTwoStationKinds() {
        assertEquals("WORK_STATION", StationProgressProducers.WORK_STATION);
        assertEquals("STATION_OUTPUT", StationProgressProducers.STATION_OUTPUT);
        assertEquals(1L, StationProgressProducers.WORK_AMOUNT, "one real cycle is one unit of work");
    }

    @Test
    void aRealCycle_countsAsWork_anIdleCycleDoesNot() {
        assertTrue(StationProgressProducers.countsAsWork(cycle(false)));
        assertFalse(StationProgressProducers.countsAsWork(cycle(true)),
                "an idle-practice cycle produced nothing and counts for nothing");
    }

    @Test
    void anIdleCycle_firesNothing() {
        producers.onCycleCompleted(cycle(true));

        assertTrue(fired.isEmpty());
    }

    @Test
    void aRealCycleWithNoLiveWorker_firesNothingAndNeverThrows() {
        // The fixture event carries no PlayerRef (a live-server handle), so the worker cannot
        // resolve to a ref: the handler must swallow that rather than fail the engine's cycle.
        assertDoesNotThrow(() -> producers.onCycleCompleted(cycle(false)));

        assertTrue(fired.isEmpty());
    }

    @Test
    void aStack_countsOnlyWhenNamedAndNonEmpty() {
        assertTrue(StationProgressProducers.countable("Plank_Oak", 1));
        assertTrue(StationProgressProducers.countable("Plank_Oak", 12));
        assertFalse(StationProgressProducers.countable(null, 1));
        assertFalse(StationProgressProducers.countable("   ", 1));
        assertFalse(StationProgressProducers.countable("Plank_Oak", 0));
        assertFalse(StationProgressProducers.countable("Plank_Oak", -3));
    }

    @Test
    void anEmptyOutputBatch_firesNothingAndNeverThrows() {
        StationOutputProducedEvent event = new StationOutputProducedEvent(null, null, null, PLAYER_ID,
                WORLD_ID, 0, 64, 0, "sawmill", "Mill", null, List.of());

        assertDoesNotThrow(() -> producers.onOutputProduced(event));

        assertTrue(fired.isEmpty(), "a batch nothing landed from advances nothing");
    }

    private static StationCycleCompletedEvent cycle(boolean idle) {
        return new StationCycleCompletedEvent(null, null, null, PLAYER_ID, SESSION_ID, "sawmill", "Mill",
                1, idle, List.of(), List.of(), 1.0, Map.of());
    }
}
