package com.ziggfreed.rpgstations.progression;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.objectives.producer.ProgressDispatch;
import com.ziggfreed.common.progress.runtime.MomentPayload;
import com.ziggfreed.rpgstations.api.event.StationCycleCompletedEvent;
import com.ziggfreed.rpgstations.api.event.StationOutputProducedEvent;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The two progression producers this engine owns. It fires {@link #WORK_STATION} and
 * {@link #STATION_OUTPUT} into ziggfreed-common's shared progression runtime itself, the way the
 * library's own producers fire a block broken or an item crafted, so a quest or achievement
 * authored against either kind advances from real station play with no other mod installed. The
 * two kinds are DESCRIBED by the kind files this jar ships
 * ({@code Server/ZiggfreedCommon/ObjectiveKinds/RpgStations/*.json}), which the library folds into
 * its kind registry when assets load; nothing is registered from Java, and this class carries no
 * vocabulary beyond the two ids.
 *
 * <p><b>Both producers listen to this engine's OWN api events</b>, on purpose: they see exactly
 * what a third-party consumer sees, so the moment content advances on and the moment a consumer
 * counts can never disagree, and every rule the events already state (an unattended settle fires
 * nothing, a command payout is invisible, a gathered batch reports once) holds here for free.
 *
 * <ul>
 *   <li>{@link #WORK_STATION} - one moment per REAL completed cycle, off
 *   {@link StationCycleCompletedEvent}: target the station id, no qualifier, amount 1, riding the
 *   cycle event's own command buffer. An idle-practice cycle produced nothing and counts for
 *   nothing ({@link #countsAsWork}).
 *   <li>{@link #STATION_OUTPUT} - one moment per stack {@link StationOutputProducedEvent}
 *   carries (a produce phase's own yield, or a grant pass's bonus units, item grants and drop-list
 *   finds): target the item id, qualifier the station id (content scopes to one station by naming
 *   it, or counts every station by leaving the qualifier off), amount the stack's quantity. A
 *   blank id or an empty stack fires nothing ({@link #countable}).
 * </ul>
 *
 * <p>Each moment carries a typed {@link MomentPayload} ({@link StationWorkPayload} /
 * {@link StationOutputPayload}) wrapping the api event it came off, so a listener in another mod
 * keying per action, per cycle or per socket reads those from the event instead of re-deriving
 * them. Every handler is guarded whole: a producer that throws costs its own moment and never the
 * engine's cycle.
 */
public final class StationProgressProducers {

    /** The objective kind one real completed cycle advances; its target is the station id. */
    public static final String WORK_STATION = "WORK_STATION";

    /** The objective kind one landed stack advances; its target is the item id, its qualifier the station id. */
    public static final String STATION_OUTPUT = "STATION_OUTPUT";

    /** One real cycle is one unit of work. */
    static final long WORK_AMOUNT = 1L;

    /**
     * The dispatch seam, the shape of {@code ProgressDispatch.fire}'s producer form: production
     * hands the shared dispatch in, a fixture test hands in a recorder and pins what each event
     * would have fired with no runtime anywhere near it.
     */
    @FunctionalInterface
    interface Dispatch {
        void fire(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                @Nullable CommandBuffer<EntityStore> commandBuffer, @Nonnull String kindId,
                @Nonnull String target, @Nullable String qualifier, long amount,
                @Nullable MomentPayload payload);
    }

    private final Dispatch dispatch;

    StationProgressProducers(@Nonnull Dispatch dispatch) {
        this.dispatch = dispatch;
    }

    /** Subscribes both producers to the api events on the plugin's own event registry. */
    public static void register(@Nonnull EventRegistry registry) {
        StationProgressProducers producers = new StationProgressProducers(ProgressDispatch::fire);
        registry.registerGlobal(StationCycleCompletedEvent.class, producers::onCycleCompleted);
        registry.registerGlobal(StationOutputProducedEvent.class, producers::onOutputProduced);
    }

    void onCycleCompleted(@Nonnull StationCycleCompletedEvent event) {
        try {
            if (!countsAsWork(event)) {
                return;
            }
            PlayerRef playerRef = event.playerRef();
            Ref<EntityStore> ref = playerRef != null ? playerRef.getReference() : null;
            if (ref == null) {
                Log.fine("STATION work moment skipped at '" + event.stationId() + "': the worker has no live entity");
                return;
            }
            dispatch.fire(event.store(), ref, event.commandBuffer(), WORK_STATION, event.stationId(), null,
                    WORK_AMOUNT, new StationWorkPayload(event));
        } catch (Throwable t) {
            Log.warn("STATION work moment failed: " + t.getMessage(), t);
        }
    }

    void onOutputProduced(@Nonnull StationOutputProducedEvent event) {
        try {
            for (ItemStack stack : event.outputs()) {
                if (stack == null || !countable(stack.getItemId(), stack.getQuantity())) {
                    continue;
                }
                dispatch.fire(event.store(), event.worker(), null, STATION_OUTPUT, stack.getItemId(),
                        event.stationId(), stack.getQuantity(), new StationOutputPayload(event, stack));
            }
        } catch (Throwable t) {
            Log.warn("STATION output moment failed: " + t.getMessage(), t);
        }
    }

    /** A real cycle is work; an idle-practice cycle produced nothing and counts for nothing. */
    static boolean countsAsWork(@Nonnull StationCycleCompletedEvent event) {
        return !event.idle();
    }

    /** A stack counts when it names an item and holds at least one of it. */
    static boolean countable(@Nullable String itemId, int quantity) {
        return itemId != null && !itemId.isBlank() && quantity > 0;
    }
}
