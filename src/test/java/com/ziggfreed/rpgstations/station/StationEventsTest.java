package com.ziggfreed.rpgstations.station;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Zero-listener dispatch is a safe no-op (design section 3.1's guarded fire idiom). In a plain
 * JUnit JVM there is no live {@code HytaleServer} instance, so {@code HytaleServer.get()} itself
 * fails inside {@link StationEvents} BEFORE any event is ever constructed or a listener could be
 * checked - {@link StationEvents}' whole-body {@code try/catch(Throwable)} swallows that failure
 * exactly the same way it swallows a genuine "zero listeners registered" no-op on a live server.
 * Real Hytale-native {@code Store}/{@code PlayerRef}/{@code CommandBuffer} args are irrelevant
 * here (never touched, since the failure happens resolving the dispatcher first), so {@code null}
 * placeholders stand in for them - every fire call below must return normally regardless.
 */
class StationEventsTest {

    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SESSION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void fireSessionStarted_noListener_neverThrows() {
        assertDoesNotThrow(() -> StationEvents.fireSessionStarted(null, null, PLAYER_ID, SESSION_ID,
                "sawmill", "work", 0, 64, 0, false));
    }

    @Test
    void fireCycleCompleted_noListener_neverThrows() {
        assertDoesNotThrow(() -> StationEvents.fireCycleCompleted(null, null, null, PLAYER_ID, SESSION_ID,
                "sawmill", "work", 1, false, List.of(), 1.0));
    }

    @Test
    void fireSessionCompleted_noListener_neverThrows() {
        assertDoesNotThrow(() -> StationEvents.fireSessionCompleted(null, null, PLAYER_ID, SESSION_ID,
                "sawmill", "PLAYER_EXIT", false, 3, 12_000L));
    }

    @Test
    void fireToolBroke_noListener_neverThrows() {
        assertDoesNotThrow(() -> StationEvents.fireToolBroke(null, null, PLAYER_ID, SESSION_ID,
                "sawmill", "Tool_Hatchet_Iron"));
    }
}
