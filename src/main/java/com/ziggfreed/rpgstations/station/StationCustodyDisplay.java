package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import java.util.Map;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.rpgstations.asset.Custody;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The placed-input PLACED-AS-ENTITY visual (design section 9, phase 2 leg G): a static,
 * network-replicated, pickup-immune, physics-free prop entity rendering the custody claim's item
 * at the station's block-top anchor - the SAME point every cycle/swing/impact/rare-find moment
 * already targets ({@code blockX+0.5, blockY+0.5, blockZ+0.5}, offset-adjustable via
 * {@link Custody.Display}). Maintainer-directed route over a Blockbench baked-node model swap.
 *
 * <p><b>Mechanism (copied verbatim from the engine's own sanctioned exemplar):</b> the admin
 * "Entity Spawn Page" (Models/Items tab,
 * {@code hytale-shared-source/HytaleServer/NPC/.../pages/EntitySpawnPage.java}) spawns exactly
 * this shape of entity for its Items tab. Two routes, picked by whether the representative item
 * has a native {@code BlockType}:
 * <ul>
 * <li><b>Block-shaped item</b> ({@link Item#hasBlockType()}) - {@link #spawnBlockEntity}: a
 * {@link BlockEntity} renders the REAL block model (not a flat icon) - the sawmill's placed logs
 * land here (a log item IS a placeable block). Mirrors {@code EntitySpawnPage.spawnItem}'s
 * {@code item.hasBlockType()} branch (lines 663-677) exactly, INCLUDING the base
 * {@code EntityScaleComponent} scale-doubling ({@link #BLOCK_ENTITY_BASE_SCALE}) that branch
 * applies - {@code EntitySpawnPage}'s own tuned default for how a {@code BlockEntity} renders at
 * "true" scale.</li>
 * <li><b>Everything else</b> (most weapons/tools - no dedicated entity-atlas {@code ModelAsset})
 * - {@link #spawnItemEntity}: a bare {@link ItemComponent} with
 * {@code setOverrideDroppedItemAnimation(true)}, the generic "dropped item minus physics" prop -
 * the anvil's placed weapon lands here. Mirrors {@code EntitySpawnPage.spawnItem}'s final
 * {@code else} branch (lines 678-693) exactly. The THIRD route ({@code ModelAsset}-backed items,
 * lines 645-662) is deliberately NOT implemented - rare in practice (most vanilla items carry no
 * entity-atlas model at all per that method's own comment) and out of this leg's scope; a future
 * leg can add it the same way if a shipped station ever needs it.</li>
 * </ul>
 *
 * <p><b>Pickup-disable</b>: {@link PreventPickup#INSTANCE} (a pure marker) - the native
 * {@code PlayerItemEntityPickupSystem} query excludes it (plus {@link PropComponent}, which both
 * routes also carry).
 *
 * <p><b>Never-persisted, by construction (resolves "reconcile orphans on restart"):</b> both
 * routes {@code ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType())} - the SAME
 * native {@code NonSerialized} marker {@code ItemComponent.generatePickedUpItem} and several
 * first-party plugins (Teleport, Projectile, Deployables) use for a transient, plugin-owned
 * entity. A display entity therefore CANNOT survive a server restart (chunk save/reload skips
 * it) - exactly mirroring the custody claim's own "never persisted, crash = loss" lifecycle
 * (design section 9.4), so there is no orphan case to reconcile: a restart loses BOTH the claim
 * and its visual together, and the self-heal that already resets a stale Loaded block state on
 * the next interaction ({@code StationService#toggle}) covers the whole picture.
 *
 * <p><b>Offset/Scale/Rotation math is kept PRIMITIVE-typed</b> ({@link #resolvePosition}/
 * {@link #resolveYawRadians}/{@link #resolveScale} take/return only doubles/floats, never touch
 * {@link Vector3d}/{@link Rotation3f}) so it stays unit-testable without a running Hytale server -
 * the same discipline {@code StationEntityMountController#resolveAttachmentOffset} established.
 *
 * <p><b>Press-F RETRIEVAL (new feature, this fix round)</b>: both spawn routes now ALSO mark the
 * display entity press-F interactable ({@link Interactable} + a {@link Interactions} entry on
 * {@code InteractionType.Use} pointing at the jar-shipped generic {@code RPG_Station_Retrieve}
 * RootInteraction asset, plus a lang-keyed hint) via {@link #addRetrieveInteraction} - the SAME
 * two-component pair NPCs and minecarts use for their own non-block Use target (a plugin-addable
 * marker + interaction-id table, ZERO NPC/minecart dependency). Firing it resolves the clicked
 * entity ref back to its owning claim by {@code NetworkId} and hands the placed contents back to
 * the presser; see {@code interaction.StationRetrieveInteraction} and
 * {@code StationService#retrieveCustody}/{@link StationCustodyRetrieval}.
 */
final class StationCustodyDisplay {

    /**
     * {@code EntitySpawnPage.BLOCK_ENTITY_BASE_SCALE} (hytale-shared-source, NPC module) - the
     * engine's own tuned default for how a {@link BlockEntity} renders at "true" scale.
     */
    private static final float BLOCK_ENTITY_BASE_SCALE = 2f;

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
     * <p><b>R4 fix</b>: takes a {@code commandBuffer} instead of the raw {@code store}. The
     * call site (({@code StationService#placeIntoCustody}) runs from {@code toggle()}, itself
     * INSIDE an interaction handler - i.e. inside the store's processing lock - so a direct
     * {@code store.addEntity} throws {@code IllegalStateException("Store is currently
     * processing!")} (Store.java's {@code assertWriteProcessing}), which the caller's own
     * {@code catch (Throwable)} swallowed into a silently no-op spawn. {@code commandBuffer
     * .addEntity} is the tick-safe primitive first-party interaction handlers use for exactly
     * this (e.g. {@code SpawnMinecartInteraction}, this mod's own sibling
     * {@code StationEntityMountController#spawnAnchor}) - it queues the add and returns a
     * PENDING {@link Ref} valid after the current tick's commandBuffer flush, which is fine here
     * since the ref is only read back on a LATER tick ({@link StationCustodyClaim#displayRef()}).
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

            Item item = Item.getAssetMap().getAsset(itemId);
            Ref<EntityStore> ref = (item != null && item.hasBlockType())
                    ? spawnBlockEntity(commandBuffer, itemId, position, rotation, scale)
                    : spawnItemEntity(commandBuffer, itemId, position, rotation, scale);
            if (ref == null) {
                Log.warn("STATION custody display spawn produced no entity for '" + itemId + "'");
            }
            return ref;
        } catch (Throwable t) {
            Log.warn("STATION custody display spawn failed for '" + itemId + "': " + t.getMessage(), t);
            return null;
        }
    }

    /** Route 1 (block-shaped item): {@code EntitySpawnPage.spawnItem}'s {@code item.hasBlockType()} branch, verbatim. */
    @Nonnull
    private static Ref<EntityStore> spawnBlockEntity(@Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull String itemId, @Nonnull Vector3d position, @Nonnull Rotation3f rotation, float scale) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(BlockEntity.getComponentType(), new BlockEntity(itemId));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(EntityScaleComponent.getComponentType(),
                new EntityScaleComponent(scale * BLOCK_ENTITY_BASE_SCALE));
        ItemStack tooltip = new ItemStack(itemId, 1);
        tooltip.setOverrideDroppedItemAnimation(true);
        holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(tooltip));
        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        addRetrieveInteraction(holder);
        return commandBuffer.addEntity(holder, AddReason.SPAWN);
    }

    /** Route 2 (everything else): {@code EntitySpawnPage.spawnItem}'s final {@code else} branch, verbatim. */
    @Nonnull
    private static Ref<EntityStore> spawnItemEntity(@Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull String itemId, @Nonnull Vector3d position, @Nonnull Rotation3f rotation, float scale) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(commandBuffer.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        ItemStack tooltip = new ItemStack(itemId, 1);
        tooltip.setOverrideDroppedItemAnimation(true);
        holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(tooltip));
        holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
        holder.addComponent(HeadRotation.getComponentType(), new HeadRotation(rotation));
        holder.addComponent(PropComponent.getComponentType(), PropComponent.get());
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        addRetrieveInteraction(holder);
        return commandBuffer.addEntity(holder, AddReason.SPAWN);
    }

    /**
     * Despawns {@code displayRef} (never throws; no-op when already gone or {@code commandBuffer}
     * could not be resolved) - called from whichever claim-removal path fires first
     * ({@code StationService#returnCustody} or {@code #onCustodyBlockBroken}).
     *
     * <p><b>R4 fix</b>: takes a {@code commandBuffer} instead of the raw {@code store} - the SAME
     * processing-lock hazard {@link #spawn} carries applies to teardown too (both claim-removal
     * call sites run inside {@code stop()}, itself often inside an interaction/tick handler).
     */
    static void despawn(@Nullable Ref<EntityStore> displayRef, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        if (displayRef == null || !displayRef.isValid() || commandBuffer == null) {
            return;
        }
        try {
            commandBuffer.removeEntity(displayRef, RemoveReason.REMOVE);
        } catch (Throwable t) {
            Log.fine("STATION custody display despawn failed: " + t.getMessage());
        }
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
