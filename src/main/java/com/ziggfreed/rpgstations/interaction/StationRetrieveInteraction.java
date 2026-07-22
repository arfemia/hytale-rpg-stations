package com.ziggfreed.rpgstations.interaction;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
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
import com.ziggfreed.rpgstations.util.InventoryAccess;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Custom interaction handler for press-F custody RETRIEVAL (new feature): pressing F on the
 * placed-input PLACED-AS-ENTITY display entity (design section 9's visual, phase 2 leg G) hands
 * the station's placed custody back to the pressing player.
 *
 * <p>Referenced NOT from a block's RootInteraction JSON (there is no per-station param to author
 * - the target entity itself identifies which claim it belongs to, by network id) but
 * programmatically, set on the display entity's own {@code Interactions} component at spawn time
 * ({@code station.StationCustodyDisplay#addRetrieveInteraction}) via
 * {@code Interactions.setInteractionId(InteractionType.Use, "RPG_Station_Retrieve")} - the SAME
 * {@code Interactable}/{@code Interactions} mechanism NPCs and minecarts use for a non-block Use
 * target, ZERO NPC/minecart dependency (confirmed via the shared-source native
 * {@code UseEntityInteraction} node: it validates reach, reads {@code Interactions} off the
 * clicked entity, looks up the registered id for {@code InteractionType.Use}, and pushes THIS
 * class's {@code RootInteraction} onto the SAME interaction chain/context - so
 * {@link InteractionContext#getTargetEntity()} recovers the exact clicked entity ref here).
 * {@code RPG_Station_Retrieve} (the RootInteraction ASSET, {@code Server/Item/RootInteractions/
 * RPG_Station_Retrieve.json}) wraps THIS class's registered TYPE_NAME - two different ids, see
 * {@code station.StationCustodyDisplay}'s own javadoc for the split.
 *
 * <p>{@link StationService#retrieveCustody} does the actual work: owner-only, and a no-op keyed
 * toast while a session is actively working that station (the session owns its own input).
 */
public final class StationRetrieveInteraction extends SimpleInstantInteraction {

    /** The registered Java interaction TYPE this class's codec is bound under. */
    public static final String TYPE_NAME = "rpg_station_retrieve";

    public static final BuilderCodec<StationRetrieveInteraction> CODEC
            = BuilderCodec.builder(StationRetrieveInteraction.class, StationRetrieveInteraction::new,
                    SimpleInstantInteraction.CODEC)
                    .build();

    public static BuilderCodec<StationRetrieveInteraction> getCODEC() {
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

            // Player.getPlayerRef() is @Deprecated(forRemoval=true) - fetch the PlayerRef
            // component manually per its own javadoc replacement note (InventoryAccess.playerRefOf).
            PlayerRef playerRef = InventoryAccess.playerRefOf(player);
            if (playerRef == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref.getStore();

            Ref<EntityStore> targetEntity = ctx.getTargetEntity();
            if (targetEntity == null) {
                ctx.getState().state = InteractionState.Finished;
                return;
            }

            StationService.getInstance().retrieveCustody(store, ref, playerRef, commandBuffer, targetEntity);

            ctx.getState().state = InteractionState.Finished;

        } catch (Exception e) {
            Log.severe("Error retrieving station custody: " + e.getMessage());
            ctx.getState().state = InteractionState.Failed;
        }
    }
}
