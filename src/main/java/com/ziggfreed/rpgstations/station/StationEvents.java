package com.ziggfreed.rpgstations.station;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.ziggfreed.rpgstations.api.StationContribution;
import com.ziggfreed.rpgstations.api.event.StationCycleCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationEnhanceCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationOutputProducedEvent;
import com.ziggfreed.rpgstations.api.event.StationSessionCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationSessionStartedEvent;
import com.ziggfreed.rpgstations.api.event.StationStructureChangedEvent;
import com.ziggfreed.rpgstations.api.event.StationToolBrokeEvent;
import com.ziggfreed.rpgstations.api.event.StationUnattendedGatheredEvent;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Fires the api artifact's {@code IEvent<Void>} POJOs on the shared Hytale event bus
 * (design section 3.1, the kweebec {@code event.RoundEvents} recipe -
 * {@code additional-mods/kweebec-nightmare/.../event/RoundEvents.java}): resolve the dispatcher,
 * guard on {@code hasListener()} (silent no-op with zero listeners), dispatch synchronously on
 * the calling (world) thread, whole body try/catch(Throwable)-guarded to a warn log. The callers
 * are {@link StationService} (session/cycle/gather moments, plus the produce-committed funnel the
 * step engine's produce phase reports through) and {@link StationStructures} (the
 * structure-changed moment); every firing point is on the world thread per the design's firing
 * rules (section 3.1).
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
            int cycleIndex, boolean idle, @Nonnull List<StationContribution> contributions,
            @Nonnull List<StationContribution> oneShotContributions, double contributionScale,
            @Nonnull Map<String, Integer> socketCounts) {
        try {
            IEventDispatcher<StationCycleCompletedEvent, StationCycleCompletedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationCycleCompletedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationCycleCompletedEvent(store, commandBuffer, playerRef, playerId, sessionId,
                        stationId, actionId, cycleIndex, idle, contributions, oneShotContributions,
                        contributionScale, socketCounts));
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

    /**
     * Fires decision 90's payout event from {@code StationService#grantAccruedAtGather}, AFTER
     * the engine's own item/loot grants for the batch landed on the gatherer. {@code gatherer} is
     * never null by construction - the grant runs only for a live, present player.
     */
    static void fireUnattendedGathered(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull UUID gathererId, @Nonnull Ref<EntityStore> gatherer, @Nonnull UUID worldUuid,
            int blockX, int blockY, int blockZ, @Nonnull String stationId, @Nonnull String actionId,
            int settledCycles, @Nonnull List<StationContribution> contributions) {
        try {
            IEventDispatcher<StationUnattendedGatheredEvent, StationUnattendedGatheredEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationUnattendedGatheredEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationUnattendedGatheredEvent(store, playerRef, gatherer, gathererId,
                        worldUuid, blockX, blockY, blockZ, stationId, actionId, settledCycles,
                        contributions));
            }
        } catch (Throwable t) {
            log("StationUnattendedGathered", t);
        }
    }

    /**
     * Fires the output-produced moment from {@code StationService#fireOutputProduced} (the funnel
     * {@code StationStepHandlers.producePhase} reports through), AFTER one produce phase's whole
     * batch committed - to placed custody ({@code socketId} names the receiving pile) or to the
     * worker's inventory ({@code socketId} null). Attended sessions only; an unattended settle
     * surfaces its output at gather instead.
     */
    static void fireOutputProduced(@Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef,
            @Nonnull Ref<EntityStore> worker, @Nonnull UUID workerId, @Nonnull UUID worldUuid,
            int blockX, int blockY, int blockZ, @Nonnull String stationId, @Nonnull String actionId,
            @Nullable String socketId, @Nonnull List<ItemStack> outputs) {
        try {
            IEventDispatcher<StationOutputProducedEvent, StationOutputProducedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationOutputProducedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationOutputProducedEvent(store, playerRef, worker, workerId, worldUuid,
                        blockX, blockY, blockZ, stationId, actionId, socketId, outputs));
            }
        } catch (Throwable t) {
            log("StationOutputProduced", t);
        }
    }

    /**
     * Fires the structure-changed moment from {@code StationStructures}, AFTER an anchor swap and
     * its pattern bookkeeping committed - activation ({@code activated} true, {@code actor} the
     * placer) or revert ({@code activated} false, {@code actor} the breaker, null on an
     * environment break).
     */
    static void fireStructureChanged(@Nonnull UUID worldUuid, int anchorX, int anchorY, int anchorZ,
            @Nonnull String patternId, @Nonnull String blockItemId, boolean activated,
            @Nullable UUID actorId, @Nullable Ref<EntityStore> actor) {
        try {
            IEventDispatcher<StationStructureChangedEvent, StationStructureChangedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(StationStructureChangedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new StationStructureChangedEvent(worldUuid, anchorX, anchorY, anchorZ,
                        patternId, blockItemId, activated, actorId, actor));
            }
        } catch (Throwable t) {
            log("StationStructureChanged", t);
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
