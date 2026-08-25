package com.ziggfreed.rpgstations.interaction;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.station.StationService;
import com.ziggfreed.common.inventory.PlayerAccess;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Custom interaction handler for in-world <b>station</b> blocks, registered under the type name
 * {@code "rpg_station_use"} (namespaced to this mod, so a station block's chain can never collide
 * with another mod's interaction type).
 *
 * <p>Referenced from a station block's {@code RootInteraction} in the OBJECT form,
 * {@code { "Type": "rpg_station_use", "Station": "sawmill" } }, so ONE interaction type backs
 * any number of station blocks with no extra Java per station.
 *
 * <p>Pressing F toggles the work session: {@link StationService#toggle} starts a session
 * (validation denials are localized toasts) or stops the player's running one. Every exit
 * path sets {@code ctx.getState().state}; a user denial is {@code Finished}, never
 * {@code Failed}.
 */
public final class StationUseInteraction extends SimpleInstantInteraction {

    /** The codec type name referenced from a station block's RootInteraction JSON. */
    public static final String TYPE_NAME = "rpg_station_use";

    /**
     * The station id this block runs, read from the RootInteraction's object form. Blank or
     * missing means the interaction is mis-authored (there is no default station) and the
     * press toasts the generic locked message via the service's unknown-id path.
     */
    protected String stationId;

    public static final BuilderCodec<StationUseInteraction> CODEC
            = BuilderCodec.builder(StationUseInteraction.class, StationUseInteraction::new,
                    SimpleInstantInteraction.CODEC)
                    .append(new KeyedCodec<>("Station", Codec.STRING),
                            (interaction, value, info) -> interaction.stationId = value,
                            (interaction, info) -> interaction.stationId)
                    .add()
                    .build();

    public static BuilderCodec<StationUseInteraction> getCODEC() {
        return CODEC;
    }

    /**
     * The station id this decoded interaction runs. Read by
     * {@code station.StationService#seedStationBlockIndexFromAssets}, which DERIVES the anchor
     * discovery index by walking the live {@code RootInteraction}/{@code Interaction} asset maps for
     * entries of this type - the engine has already decoded the object form, so the seed reads this
     * field instead of re-parsing any JSON.
     */
    @Nullable
    public String getStationId() {
        return stationId;
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext ctx,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        try {
            var commandBuffer = ctx.getCommandBuffer();
            if (commandBuffer == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            Player player = commandBuffer.getComponent(ctx.getEntity(), Player.getComponentType());
            if (player == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            // Player.getPlayerRef() is @Deprecated(forRemoval=true) - fetch the PlayerRef
            // component manually per its own javadoc replacement note (PlayerAccess.playerRef).
            PlayerRef playerRef = PlayerAccess.playerRef(player);
            if (playerRef == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref.getStore();

            var targetBlock = ctx.getTargetBlock();
            if (targetBlock == null) {
                ctx.getState().state = InteractionState.Finished;
                return;
            }

            // Sneak read at fire time (selection wave, decision 50): the SAME crouch flag the
            // heartbeat reads as the diegetic exit input (MovementStatesComponent.crouching,
            // client-populated + synced) - a sneak+F press routes to the multi-output selection
            // surface instead of the plain work toggle. Fetched off the command buffer here exactly
            // as the Player component above is; a missing component reads as not-sneaking (plain
            // toggle). In-game timing reliability of the crouch flag at the interaction instant is a
            // smoke item; the round-3 selector-entity press-F pattern is the documented fallback if
            // it misbehaves.
            boolean sneaking = readSneaking(commandBuffer, ctx.getEntity());

            String id = stationId != null ? stationId.toLowerCase(Locale.ROOT) : "";
            StationService.getInstance().toggle(store, ref, player, commandBuffer, id,
                    targetBlock.x, targetBlock.y, targetBlock.z, sneaking);

            ctx.getState().state = InteractionState.Finished;

        } catch (Exception e) {
            Log.severe("Error toggling station session: " + e.getMessage());
            ctx.getState().state = InteractionState.Failed;
        }
    }

    /**
     * The clean sneak read at fire time: the player's {@code MovementStatesComponent.crouching}
     * flag (client-populated, server-synced) - the SAME flag {@code StationService}'s heartbeat
     * reads as the crouch exit. Fetched off the command buffer exactly like the {@code Player}
     * component; a missing component or any read failure resolves to {@code false} (plain toggle),
     * never a throw into the interaction handler.
     */
    private static boolean readSneaking(@Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Ref<EntityStore> entity) {
        try {
            MovementStatesComponent ms =
                    commandBuffer.getComponent(entity, MovementStatesComponent.getComponentType());
            return ms != null && ms.getMovementStates() != null && ms.getMovementStates().crouching;
        } catch (Throwable t) {
            return false;
        }
    }
}
