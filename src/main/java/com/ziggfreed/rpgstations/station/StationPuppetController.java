package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.entity.PlayerModelService;
import com.ziggfreed.common.entity.PlayerPuppetService;
import com.ziggfreed.rpgstations.asset.Puppet;
import com.ziggfreed.rpgstations.asset.StationStep;
import com.ziggfreed.rpgstations.util.InventoryAccess;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Policy-thin glue over {@code ziggfreed-common}'s {@code entity.PlayerPuppetService} for a
 * station session's PUPPET presentation route (round-4 design, {@code
 * .claude/research/raw/rpg-stations-puppet-presentation-design-2026-07-22.md} section 4 - "mount
 * the player, hide their player model, and spawn/display a visual of their character model
 * performing the steps"). Sibling to {@link StationEntityMountController}/{@link
 * StationHoldController}: this class owns ONLY the puppet's spawn/hide/reveal/despawn/animation
 * MECHANISM (the offset/yaw/prop resolution against the station's own block-top anchor, which
 * hide route an author picked, and the swing-beat cadence caller policy) - the generic
 * "clone-a-skin-onto-a-networked-entity" + "scale self-hide" primitives themselves live in common
 * ({@code entity.PlayerPuppetService}/{@code entity.PlayerModelService}), per the root
 * additional-mods PARADIGM (a reusable Hytale primitive belongs in common, not duplicated here).
 *
 * <p><b>Hide route, this leg (design round-4 crowned decision):</b> {@code Hide.Route} is a
 * THREE-arm union - {@code "Scale"} (in-game PROVEN, the ONLY route this class actually applies:
 * {@link PlayerPuppetService#hideByScale}/{@link PlayerPuppetService#revealByScale}), {@code
 * "Effect"} (schema-reserved future work - the shadowstep/{@code Portal_Teleport} pointer, see
 * {@link Puppet.Hide}'s own javadoc; this leg applies NO hide for it, a puppet spawns but the real
 * player stays visible, the same degraded posture as {@code "None"}), and {@code "None"} (the
 * deliberate degraded fallback). The design doc's {@code "ModelSwap"}/{@code "HiddenManager"}
 * routes were RETIRED before this schema shipped (buggy/unproven in the P0 spike) - this class
 * therefore never touches {@code HiddenPlayersManager} at all, unlike the temporary {@code
 * puppetspike.PuppetSpikeService} harness, which still exercises those retired routes for
 * diagnostic comparison and is NOT superseded by this class (a separate, still-live spike tool).
 *
 * <p><b>Animation routing (design 4.3):</b> a puppet ALWAYS plays its clip on the {@code Emote}
 * slot - it has no sit pose to fight (unlike a seat-mounted real player), so it needs none of
 * {@code StationHoldController#playActionSwing}'s Action-slot held-item workaround. This
 * SUPERSEDES {@code StationService#useActionSlotForSwing} entirely for a puppet-active session -
 * see {@code StationService#runSwing}'s branch. The per-step {@code StationStep.Puppet} override
 * ({@link StationStep.PuppetOverride}) is read off the SESSION's already-tracked step-resume state
 * ({@code StationSession#programSuspended}/{@code #activeProgramSteps}/{@code #programIndex}) so a
 * ritual's distinct beats (e.g. the anvil's future strike/quench/stamp poses) get their own puppet
 * clip/prop for as long as that step stays suspended, re-applied on the SAME swing-beat cadence
 * that already drives the puppet's animation - no new step-dispatch hookup needed.
 *
 * <p><b>Lifecycle:</b> spawn + hide at engage ({@link #spawnAndHide}, called from {@code
 * StationService#toggle} AFTER the existing mount attach - the puppet layers on WHATEVER hold/
 * mount the real player already has, never replacing it); reveal + despawn in the ONE idempotent
 * {@code stop()} funnel ({@link #revealAndDespawn}, EVERY exit path - re-press, walk-off, damage,
 * death, disconnect, shutdown), resolving its OWN store off {@code s.ref.getStore()} (falling back
 * to {@code s.puppetRef.getStore()}) rather than trusting the possibly-{@code null} {@code store}
 * parameter {@code stop()} may have been handed, mirroring {@code returnCustody}'s exact pattern -
 * so a disconnect/shutdown stop still reveals the real player and despawns the puppet cleanly.
 * {@link #reassertOnReady} is the belt-and-suspenders {@code PlayerReadyEvent} safety net (design
 * 4.4/leg P5): unconditional, not gated on any remembered session (a restart wipes every
 * in-memory {@code StationSession} by construction) - the only residual risk is a PERSISTED
 * {@link EntityScaleComponent} on the player's own entity from an unclean shutdown mid-hide.
 */
final class StationPuppetController {

    private StationPuppetController() {
    }

    // ==================== engage: spawn + hide ====================

    /**
     * Spawn the puppet + hide the real player, per {@code puppet} (the resolved action's {@code
     * Puppet} group). No-op when {@code puppet} is null or {@link Puppet#effectiveEnabled()} is
     * false (the classic in-body worker). A spawn failure is NON-FATAL: logs, leaves every {@code
     * StationSession} puppet field at its default, the session continues in-body - matching {@link
     * StationEntityMountController}'s own graceful-degradation contract.
     */
    static void spawnAndHide(@Nonnull StationSession s, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Store<EntityStore> store, @Nullable Puppet puppet, @Nullable Player player) {
        if (puppet == null || !puppet.effectiveEnabled() || s.ref == null) {
            return;
        }
        try {
            double anchorX = s.blockX + 0.5;
            double anchorY = s.blockY + 0.5;
            double anchorZ = s.blockZ + 0.5;
            Puppet.Offset offset = puppet.getOffset();
            double[] pos = PlayerPuppetService.offsetPosition(anchorX, anchorY, anchorZ,
                    offset != null && offset.getX() != null ? offset.getX() : 0.0,
                    offset != null && offset.getY() != null ? offset.getY() : 0.0,
                    offset != null && offset.getZ() != null ? offset.getZ() : 0.0);
            float yawRadians = PlayerPuppetService.yawRadiansFromDegrees(puppet.effectiveYawDegrees());

            Puppet.Prop prop = puppet.getProp();
            String propItemId = resolveEffectivePropItemId(heldItemIdOf(player), prop);

            PlayerPuppetService.PuppetSpawnRequest.Builder reqBuilder = PlayerPuppetService.PuppetSpawnRequest.builder()
                    .sourceRef(s.ref)
                    .position(new Vector3d(pos[0], pos[1], pos[2]))
                    .yawRadians(yawRadians);
            if (propItemId != null) {
                reqBuilder.heldItemIdOverride(propItemId);
            }
            if (s.emoteId != null && !s.emoteId.isBlank()) {
                reqBuilder.initialAnimation(AnimationSlot.Emote, s.emoteId);
            }

            Ref<EntityStore> puppetRef = PlayerPuppetService.spawn(commandBuffer, reqBuilder.build());
            if (puppetRef == null) {
                Log.warn("STATION puppet spawn failed for station '" + s.stationId + "' action '" + s.actionId
                        + "' - continuing in-body");
                return;
            }

            s.puppetRef = puppetRef;
            s.puppetDefaultProp = prop;
            s.puppetActive = true;
            Puppet.Hide hide = puppet.getHide();
            s.puppetHideRoute = hide != null ? hide.effectiveRoute() : Puppet.HIDE_ROUTE_SCALE;
            if (Puppet.HIDE_ROUTE_SCALE.equals(s.puppetHideRoute)) {
                s.puppetSavedScale = PlayerPuppetService.hideByScale(store, s.ref);
            }
            // "Effect" (schema-reserved future work) and "None" (the deliberate degraded
            // fallback) apply NO hide this leg - the puppet still spawns and animates, the real
            // player just stays visible alongside it (validator PUPPET_WITHOUT_HIDE warns).
        } catch (Throwable t) {
            Log.warn("STATION puppet spawn/hide failed: " + t.getMessage(), t);
        }
    }

    // ==================== teardown: reveal + despawn ====================

    /**
     * Reveal the real player (undo the hide) + despawn the puppet - the {@code stop()} funnel's
     * ONE puppet-teardown call, every exit path. No-op when {@link StationSession#puppetActive}
     * is false. Resolves its own store off {@code s.ref.getStore()} (falling back to {@code
     * s.puppetRef.getStore()} when the player ref itself is gone) rather than trusting a
     * possibly-{@code null} parameter, mirroring {@code StationService#returnCustody}'s exact
     * pattern - so a disconnect ({@code stop(..., null, null)}) still tears down cleanly.
     */
    static void revealAndDespawn(@Nonnull StationSession s) {
        if (!s.puppetActive) {
            return;
        }
        Store<EntityStore> teardownStore = resolveTeardownStore(s);
        if (teardownStore != null) {
            if (Puppet.HIDE_ROUTE_SCALE.equals(s.puppetHideRoute) && s.ref != null && s.ref.isValid()) {
                PlayerPuppetService.revealByScale(teardownStore, s.ref, s.puppetSavedScale);
            }
            PlayerPuppetService.despawn(s.puppetRef, teardownStore);
        } else {
            // Both the player ref and the puppet ref are gone/unresolvable - nothing left to
            // reveal or despawn via any store. Harmless: the puppet is NonSerialized (never
            // survives a restart regardless), and a gone player ref has no scale left to restore.
            Log.fine("STATION puppet teardown skipped (no resolvable store)");
        }
        s.puppetRef = null;
        s.puppetActive = false;
        s.puppetHideRoute = null;
        s.puppetSavedScale = null;
        s.puppetDefaultProp = null;
    }

    @Nullable
    private static Store<EntityStore> resolveTeardownStore(@Nonnull StationSession s) {
        if (s.ref != null && s.ref.isValid()) {
            try {
                Store<EntityStore> store = s.ref.getStore();
                if (store != null) {
                    return store;
                }
            } catch (Throwable ignored) {
                // fall through to the puppet-ref fallback below
            }
        }
        if (s.puppetRef != null && s.puppetRef.isValid()) {
            try {
                return s.puppetRef.getStore();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    // ==================== animation + prop routing ====================

    /**
     * Engage-time loop clip - mirrors {@code StationHoldController#playEmote} exactly, but
     * targets the puppet instead of the real player. No-op when {@link StationSession#puppetActive}
     * is false or the station authors no {@code Animation.EmoteId}.
     */
    static void playLoop(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        if (!s.puppetActive || s.emoteId == null || s.emoteId.isBlank()) {
            return;
        }
        PlayerPuppetService.playAnimation(store, s.puppetRef, AnimationSlot.Emote, null, s.emoteId, true);
    }

    /**
     * The per-swing beat: fires the effective clip on the puppet's {@code Emote} slot (design
     * 4.3 - supersedes the real-player Action-slot seat workaround entirely) and syncs its held
     * prop. Both the clip and the prop prefer the currently-suspended step's own {@code
     * StationStep.Puppet} override when one is in flight, else fall back to the resolved action's
     * defaults ({@code s.emoteId}/{@code s.puppetDefaultProp}) - design 3.1's "absent = inherit
     * the action's default clip/prop".
     */
    static void playSwing(@Nonnull StationSession s, @Nonnull Store<EntityStore> store, @Nullable Player player) {
        if (!s.puppetActive) {
            return;
        }
        StationStep.PuppetOverride override = activeStepPuppetOverride(s);
        String clip = resolveEffectiveClip(override != null ? override.getClip() : null, s.emoteId);
        if (clip != null && !clip.isBlank()) {
            PlayerPuppetService.playAnimation(store, s.puppetRef, AnimationSlot.Emote, null, clip, true);
        }
        syncProp(s, store, player, override);
    }

    private static void syncProp(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nullable Player player, @Nullable StationStep.PuppetOverride override) {
        if (s.puppetRef == null || !s.puppetRef.isValid()) {
            return;
        }
        Puppet.Prop effectiveProp = override != null && override.getProp() != null
                ? override.getProp() : s.puppetDefaultProp;
        String itemId = resolveEffectivePropItemId(heldItemIdOf(player), effectiveProp);
        try {
            if (itemId == null || itemId.isBlank()) {
                store.removeComponentIfExists(s.puppetRef, InventoryComponent.Hotbar.getComponentType());
                return;
            }
            SimpleItemContainer container = new SimpleItemContainer((short) 1);
            container.setItemStackForSlot((short) 0, new ItemStack(itemId, 1));
            store.putComponent(s.puppetRef, InventoryComponent.Hotbar.getComponentType(),
                    new InventoryComponent.Hotbar(container, (byte) 0));
        } catch (Throwable t) {
            Log.fine("STATION puppet prop sync failed: " + t.getMessage());
        }
    }

    @Nullable
    private static StationStep.PuppetOverride activeStepPuppetOverride(@Nonnull StationSession s) {
        if (!s.programSuspended || s.activeProgramSteps == null
                || s.programIndex < 0 || s.programIndex >= s.activeProgramSteps.size()) {
            return null;
        }
        StationStep step = s.activeProgramSteps.get(s.programIndex);
        return step != null ? step.getPuppet() : null;
    }

    @Nullable
    private static String heldItemIdOf(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        try {
            ItemStack held = InventoryAccess.activeHotbarItemOf(player);
            return held != null ? held.getItemId() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================== pure cores (unit-tested without a live server) ====================

    /**
     * PURE: {@code stepClipOverride} wins when authored, else {@code defaultClip} - design 3.1's
     * "absent = inherit the action's default clip".
     */
    @Nullable
    static String resolveEffectiveClip(@Nullable String stepClipOverride, @Nullable String defaultClip) {
        return stepClipOverride != null && !stepClipOverride.isBlank() ? stepClipOverride : defaultClip;
    }

    /**
     * PURE: the effective puppet prop item id (design 3.6) - {@code null} {@code prop} (no group
     * authored at all) mirrors the live held item ({@code MirrorHeld}'s own zero-authoring
     * default); {@code "None"} always empties the puppet's hand; {@code "ItemId"} forces {@code
     * prop.getItemId()} (blank/absent degrades to empty-handed, never a crash); every other
     * (including the default {@code "MirrorHeld"}) source mirrors {@code heldItemId}.
     */
    @Nullable
    static String resolveEffectivePropItemId(@Nullable String heldItemId, @Nullable Puppet.Prop prop) {
        if (prop == null) {
            return heldItemId;
        }
        String source = prop.effectiveSource();
        if (Puppet.PROP_SOURCE_NONE.equalsIgnoreCase(source)) {
            return null;
        }
        if (Puppet.PROP_SOURCE_ITEM_ID.equalsIgnoreCase(source)) {
            String itemId = prop.getItemId();
            return itemId != null && !itemId.isBlank() ? itemId : null;
        }
        return heldItemId;
    }

    // ==================== PlayerReady safety net (design 4.4 / leg P5) ====================

    /**
     * Unconditionally clears any lingering {@link EntityScaleComponent} and restores the correct
     * cosmetic model on the FRESH ready ref/store - the belt-and-suspenders net design 4.4 calls
     * for ("a spike must never strand an invisible/shrunk player"), generalized from the shape
     * {@code puppetspike.PuppetSpikeService#safetyNetOnReady} already proved (that class now
     * delegates its scale-clear/model-restore half to THIS method rather than duplicating it -
     * see its own javadoc). Deliberately NOT gated on any remembered {@code StationSession}: a
     * server restart wipes every in-memory session by construction, so the only residual risk is
     * a PERSISTED component on the player's own entity from an unclean shutdown mid-hide. Never
     * throws.
     */
    static void reassertOnReady(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            store.removeComponentIfExists(ref, EntityScaleComponent.getComponentType());
        } catch (Throwable t) {
            Log.fine("STATION puppet ready safety-net scale-clear failed: " + t.getMessage());
        }
        PlayerModelService.restore(ref, store);
    }
}
