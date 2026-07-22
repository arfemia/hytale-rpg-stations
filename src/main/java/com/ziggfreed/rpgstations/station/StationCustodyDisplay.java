package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import java.util.Map;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.entity.ItemPropEntityService;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The placed-input PLACED-AS-ENTITY visual (design section 9, phase 2 leg G): a static,
 * network-replicated, pickup-immune, physics-free prop entity rendering the custody claim's item
 * at the station's block-top anchor - the SAME point every cycle/swing/impact/rare-find moment
 * already targets ({@code blockX+0.5, blockY+0.5, blockZ+0.5}, offset-adjustable via
 * {@link Custody.Display}). Maintainer-directed route over a Blockbench baked-node model swap.
 *
 * <p><b>Mechanism (delegated to the {@code ziggfreed-common} lift):</b> the spawn/despawn
 * MECHANISM itself (the block-shaped-vs-everything-else routing, the pickup-disable markers, the
 * {@code NonSerialized} never-persisted marker) is
 * {@link com.ziggfreed.common.entity.ItemPropEntityService} - lifted config-free out of THIS
 * class's own prior verbatim copy (itself copied from the engine's sanctioned admin "Entity Spawn
 * Page" Items tab exemplar), per the root additional-mods PARADIGM (a reusable Hytale primitive
 * belongs in common, not duplicated here). This class now owns only STATION-SPECIFIC policy: the
 * block-top-anchor offset/yaw/scale resolution against {@link Custody.Display}'s knobs, and the
 * press-F retrieve interaction wiring (below) added onto the common primitive's two-phase
 * {@code buildHolder}/{@code spawn} API before the entity commits.
 *
 * <p><b>Never-persisted, by construction (resolves "reconcile orphans on restart"):</b> the
 * common primitive's {@code NonSerialized} marker means a display entity CANNOT survive a server
 * restart (chunk save/reload skips it) - exactly mirroring the custody claim's own "never
 * persisted, crash = loss" lifecycle (design section 9.4), so there is no orphan case to
 * reconcile: a restart loses BOTH the claim and its visual together, and the self-heal that
 * already resets a stale Loaded block state on the next interaction
 * ({@code StationService#toggle}) covers the whole picture.
 *
 * <p><b>Offset/Scale/Rotation math is kept PRIMITIVE-typed</b> ({@link #resolvePosition}/
 * {@link #resolveYawRadians}/{@link #resolveScale} take/return only doubles/floats, never touch
 * {@link Vector3d}/{@link Rotation3f}) so it stays unit-testable without a running Hytale server -
 * the same discipline {@code StationEntityMountController#resolveAttachmentOffset} established.
 *
 * <p><b>Press-F RETRIEVAL (new feature, the R6 fix round)</b>: the spawned display entity is
 * marked press-F interactable ({@link Interactable} + a {@link Interactions} entry on
 * {@code InteractionType.Use} pointing at the jar-shipped generic {@code RPG_Station_Retrieve}
 * RootInteraction asset, plus a lang-keyed hint) via {@link #addRetrieveInteraction} - the SAME
 * two-component pair NPCs and minecarts use for their own non-block Use target (a plugin-addable
 * marker + interaction-id table, ZERO NPC/minecart dependency), added onto the common primitive's
 * un-added {@code Holder} before {@link #spawn} commits it. Firing it resolves the clicked entity
 * ref back to its owning claim by {@code NetworkId} and hands the placed contents back to the
 * presser; see {@code interaction.StationRetrieveInteraction} and
 * {@code StationService#retrieveCustody}/{@link StationCustodyRetrieval}.
 */
final class StationCustodyDisplay {

    /**
     * The RootInteraction ASSET id (jar-shipped, {@code Server/Item/RootInteractions/
     * RPG_Station_Retrieve.json}) every display entity's {@code Interactions} entry points at for
     * press-F retrieval - a DIFFERENT string from {@code interaction.StationRetrieveInteraction
     * .TYPE_NAME} (the registered Java interaction TYPE that asset's own body references): an
     * entity's {@code Interactions} component stores a RootInteraction asset reference
     * (mirroring {@code SpawnMinecartInteraction}'s {@code CartInteractions} codec field, decoded
     * through {@code RootInteraction.CHILD_ASSET_CODEC}), not a raw registered type name. Generic
     * (no per-station param needed - the clicked entity ref alone identifies its owning claim),
     * so ONE shared jar asset backs every custody display in every installed pack.
     */
    private static final String RETRIEVE_ROOT_INTERACTION_ID = "RPG_Station_Retrieve";

    private StationCustodyDisplay() {
    }

    /**
     * Marks {@code holder} press-F interactable for retrieval (new feature): the two components
     * {@code UseEntityInteraction} needs together - {@link Interactable} (a pure marker; its
     * presence is what tells the CLIENT this entity can be F-interacted) and {@link Interactions}
     * (the per-type interaction-id table; {@code InteractionType.Use} points at {@link
     * #RETRIEVE_ROOT_INTERACTION_ID}) - plus a lang-keyed hint. Shared by both spawn routes.
     */
    private static void addRetrieveInteraction(@Nonnull Holder<EntityStore> holder) {
        holder.ensureComponent(Interactable.getComponentType());
        Interactions interactions = new Interactions(Map.of(InteractionType.Use, RETRIEVE_ROOT_INTERACTION_ID));
        interactions.setInteractionHint("rpgstations.ui.station.retrieve.hint");
        holder.addComponent(Interactions.getComponentType(), interactions);
    }

    /**
     * Spawns the display entity for {@code visualStack} at {@code (blockX, blockY, blockZ)} per
     * {@code display}'s knobs. Returns {@code null} (never throws) on any failure, on a blank
     * item id, or when {@code visualStack} is null - the caller treats a null return as "no
     * visual this time", never a hard error (placement itself already succeeded by this point).
     *
     * <p>Resolves this station's own offset/yaw/scale, then delegates the spawn MECHANISM to
     * {@link ItemPropEntityService#buildHolder}/{@link ItemPropEntityService#spawn(
     * com.hypixel.hytale.component.ComponentAccessor, Holder)} - the two-phase API lets this class
     * add {@link #addRetrieveInteraction} onto the un-added holder before commit. {@code
     * commandBuffer} (never {@code store}) is the tick-safe primitive the call site ({@code
     * StationService#placeIntoCustody}, itself inside {@code toggle()}'s interaction-handler
     * processing lock) needs - a direct {@code store.addEntity} there throws {@code
     * IllegalStateException("Store is currently processing!")} (R4 fix); it queues the add and
     * returns a PENDING {@link Ref} valid after the current tick's commandBuffer flush, which is
     * fine here since the ref is only read back on a LATER tick
     * ({@link StationCustodyClaim#displayRef()}).
     */
    @Nullable
    static Ref<EntityStore> spawn(@Nonnull CommandBuffer<EntityStore> commandBuffer, @Nullable ItemStack visualStack,
            @Nonnull Custody.Display display, int blockX, int blockY, int blockZ) {
        if (visualStack == null) {
            return null;
        }
        String itemId = visualStack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            double[] pos = resolvePosition(display, blockX, blockY, blockZ);
            Vector3d position = new Vector3d(pos[0], pos[1], pos[2]);
            Rotation3f rotation = new Rotation3f(0f, resolveYawRadians(display), 0f);
            float scale = resolveScale(display);

            Holder<EntityStore> holder = ItemPropEntityService.buildHolder(commandBuffer, itemId, position, rotation, scale);
            if (holder == null) {
                Log.warn("STATION custody display spawn produced no holder for '" + itemId + "'");
                return null;
            }
            addRetrieveInteraction(holder);
            Ref<EntityStore> ref = ItemPropEntityService.spawn(commandBuffer, holder);
            if (ref == null) {
                Log.warn("STATION custody display spawn produced no entity for '" + itemId + "'");
            }
            return ref;
        } catch (Throwable t) {
            Log.warn("STATION custody display spawn failed for '" + itemId + "': " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Despawns {@code displayRef} (never throws; no-op when already gone or {@code commandBuffer}
     * could not be resolved) - called from whichever claim-removal path fires first
     * ({@code StationService#returnCustody} or {@code #onCustodyBlockBroken}). Delegates straight
     * to {@link ItemPropEntityService#despawn(Ref, CommandBuffer)} (the SAME tick-safe primitive
     * {@link #spawn} builds through - R4 fix: takes a {@code commandBuffer} instead of the raw
     * {@code store}, the same processing-lock hazard {@link #spawn} carries).
     */
    static void despawn(@Nullable Ref<EntityStore> displayRef, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        ItemPropEntityService.despawn(displayRef, commandBuffer);
    }

    /**
     * Pure: the block-top anchor ({@code blockX+0.5, blockY+0.5, blockZ+0.5}) shifted by
     * {@code display}'s authored {@code Offset} (each leaf independently nullable, default 0).
     * Returns {@code [x, y, z]} - kept primitive so it needs no live Hytale type.
     */
    @Nonnull
    static double[] resolvePosition(@Nullable Custody.Display display, int blockX, int blockY, int blockZ) {
        Custody.Display.Offset offset = display != null ? display.getOffset() : null;
        double ox = offset != null && offset.getX() != null ? offset.getX() : 0.0;
        double oy = offset != null && offset.getY() != null ? offset.getY() : 0.0;
        double oz = offset != null && offset.getZ() != null ? offset.getZ() : 0.0;
        return new double[] {blockX + 0.5 + ox, blockY + 0.5 + oy, blockZ + 0.5 + oz};
    }

    /** Pure: {@code display}'s authored {@code Rotation} (degrees, world-space yaw) converted to radians. */
    static float resolveYawRadians(@Nullable Custody.Display display) {
        double degrees = display != null ? display.effectiveRotationDegrees() : 0.0;
        return (float) Math.toRadians(degrees);
    }

    /** Pure: {@code display}'s authored {@code Scale}, defaulted to {@code 1.0} when absent/non-positive. */
    static float resolveScale(@Nullable Custody.Display display) {
        return display != null ? (float) display.effectiveScale() : 1f;
    }
}
