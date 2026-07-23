package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.api.XpAsk;
import com.ziggfreed.rpgstations.api.event.StationCycleCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationEnhanceCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationSessionCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationSessionStartedEvent;
import com.ziggfreed.rpgstations.api.event.StationToolBrokeEvent;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Fires the api artifact's four {@code IEvent<Void>} POJOs on the shared Hytale event bus
 * (design section 3.1, the kweebec {@code event.RoundEvents} recipe -
 * {@code additional-mods/kweebec-nightmare/.../event/RoundEvents.java}): resolve the dispatcher,
 * guard on {@code hasListener()} (silent no-op with zero listeners), dispatch synchronously on
 * the calling (world) thread, whole body try/catch(Throwable)-guarded to a warn log. {@link
 * StationService} is the only caller, all four firing points on the world thread per the design's
 * firing rules (section 3.1).
 */
final class StationEvents {

    private StationEvents() {
    }

    static void fireSessionStarted(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            int blockX, int blockY, int blockZ, boolean idleMode) {
        try {
            IEventDispatcher<StationSessionStartedEvent, StationSessionStartedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationSessionStartedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationSessionStartedEvent(store, playerRef, playerId, sessionId, stationId,
                        actionId, blockX, blockY, blockZ, idleMode));
            }
        } catch (Throwable t) {
            log("StationSessionStarted", t);
        }
    }

    /** {@code commandBuffer} is GUARANTEED non-null by every caller - see {@code StationCycleCompletedEvent}'s javadoc. */
    static void fireCycleCompleted(@Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            int cycleIndex, boolean idle, @Nonnull List<XpAsk> xpAsks, double toolMultiplier) {
        try {
            IEventDispatcher<StationCycleCompletedEvent, StationCycleCompletedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationCycleCompletedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationCycleCompletedEvent(store, commandBuffer, playerRef, playerId, sessionId,
                        stationId, actionId, cycleIndex, idle, xpAsks, toolMultiplier));
            }
        } catch (Throwable t) {
            log("StationCycleCompleted", t);
        }
    }

    /** {@code store}/{@code playerRef} may be {@code null} on a disconnect/server-stop teardown. */
    static void fireSessionCompleted(@Nullable Store<EntityStore> store, @Nullable PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String stopReason,
            boolean silent, int cyclesDone, long durationMs) {
        try {
            IEventDispatcher<StationSessionCompletedEvent, StationSessionCompletedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationSessionCompletedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationSessionCompletedEvent(store, playerRef, playerId, sessionId, stationId,
                        stopReason, silent, cyclesDone, durationMs));
            }
        } catch (Throwable t) {
            log("StationSessionCompleted", t);
        }
    }

    /**
     * Fires the D-6 enhancement-completed event from {@code StationStepHandlers.StampHandler}, after
     * the mutated stack is committed to custody (so it reports a committed enhancement only). Plain
     * data + immutable {@code ItemStack} copies travel on {@link StationEnhanceOutcome}; the live
     * {@code store}/{@code playerRef} are dispatch-synchronous only (see the event's javadoc).
     */
    static void fireEnhanceCompleted(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String actionId,
            @Nonnull StationEnhanceOutcome outcome) {
        try {
            IEventDispatcher<StationEnhanceCompletedEvent, StationEnhanceCompletedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationEnhanceCompletedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationEnhanceCompletedEvent(store, playerRef, playerId, sessionId, stationId,
                        actionId, outcome.itemId(), outcome.before(), outcome.after(), outcome.lines(),
                        outcome.durabilityAdded()));
            }
        } catch (Throwable t) {
            log("StationEnhanceCompleted", t);
        }
    }

    static void fireToolBroke(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID playerId, @Nonnull UUID sessionId, @Nonnull String stationId, @Nonnull String heldItemId) {
        try {
            IEventDispatcher<StationToolBrokeEvent, StationToolBrokeEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationToolBrokeEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationToolBrokeEvent(store, playerRef, playerId, sessionId, stationId, heldItemId));
            }
        } catch (Throwable t) {
            log("StationToolBroke", t);
        }
    }

    private static void log(@Nonnull String which, @Nonnull Throwable t) {
        Log.warn("STATION failed to fire " + which + " event: " + t.getMessage());
    }
}
