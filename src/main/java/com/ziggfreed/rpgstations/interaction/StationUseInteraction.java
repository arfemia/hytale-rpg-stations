package com.ziggfreed.rpgstations.interaction;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.station.StationService;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Custom interaction handler for in-world <b>station</b> blocks. Ported from the MMO's
 * {@code interaction.StationUseInteraction} (RPG Stations extraction leg 2); registered
 * type name changes to {@code "rpg_station_use"} (the MMO's own copy stays
 * {@code "mmo_station_use"} and coexists unchanged until leg 5 - the two ids let both jars'
 * station engines run side by side without collision).
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

            PlayerRef playerRef = player.getPlayerRef();
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

            String id = stationId != null ? stationId.toLowerCase() : "";
            StationService.getInstance().toggle(store, ref, playerRef, player, commandBuffer, id,
                    targetBlock.x, targetBlock.y, targetBlock.z);

            ctx.getState().state = InteractionState.Finished;

        } catch (Exception e) {
            Log.severe("Error toggling station session: " + e.getMessage());
            ctx.getState().state = InteractionState.Failed;
        }
    }
}
