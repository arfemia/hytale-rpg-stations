package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
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
 * death, disconnect, shutdown). Every player/puppet component + entity MUTATION (the spawn, the
 * {@code Scale} hide/reveal, the despawn, and the per-swing prop sync) routes through the
 * {@code CommandBuffer} the caller threads through, never a live {@code Store} - {@code toggle}
 * (an interaction handler) and the heartbeat frame-drain both run inside the store's own
 * write-processing lock, where a direct {@code store.putComponent}/{@code removeEntity} throws
 * (accessor-bug fix, fix round; see each method's own javadoc). {@code teardownStore} (resolved
 * off {@code s.ref.getStore()}, falling back to {@code s.puppetRef.getStore()}) still backs
 * {@link #revealAndDespawn}'s entity-still-resolvable validity gate, mirroring {@code
 * returnCustody}'s exact resolution pattern; the mutation itself uses the passed-in {@code
 * commandBuffer}, which is {@code null} on the same damage/death/disconnect/shutdown sweeps
 * {@code returnCustody} already accepts a {@code null} commandBuffer for.
 * {@link #reassertOnReady} is the belt-and-suspenders {@code PlayerReadyEvent} safety net (design
 * 4.4/leg P5): unconditional, not gated on any remembered session (a restart wipes every
 * in-memory {@code StationSession} by construction), it also catches the residual case of a
 * {@code null}-commandBuffer exit leaving a hidden player un-revealed for the rest of that
 * session - the only OTHER residual risk is a PERSISTED {@link EntityScaleComponent} on the
 * player's own entity from an unclean shutdown mid-hide. {@link #reassertOnReady} itself runs via
 * {@code world.execute}, outside processing, so it correctly stays on {@code store}.
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
     *
     * <p><b>Accessor-bug fix (fix round):</b> both the spawn AND the {@code Scale} hide route
     * through {@code commandBuffer}, never {@code store} - this call runs from {@code
     * StationService#toggle}, inside the store's write-processing lock (an interaction-handler
     * call site), where a direct {@code store.putComponent} throws {@code IllegalStateException(
     * "Store is currently processing!")} (verified in {@code hytale-shared-source}'s {@code
     * Store#putComponent}/{@code assertWriteProcessing}). The prior {@code store}-routed hide
     * silently swallowed that throw into the method's own catch, so the real player was NEVER
     * actually hidden even though every shipped station authors {@code Hide.Route:"Scale"}.
     */
    static void spawnAndHide(@Nonnull StationSession s, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nullable Puppet puppet, @Nullable Player player) {
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
            s.puppetHeldItemId = propItemId;
            s.puppetActive = true;
            Puppet.Hide hide = puppet.getHide();
            s.puppetHideRoute = hide != null ? hide.effectiveRoute() : Puppet.HIDE_ROUTE_SCALE;
            if (Puppet.HIDE_ROUTE_SCALE.equals(s.puppetHideRoute)) {
                s.puppetSavedScale = PlayerPuppetService.hideByScale(commandBuffer, s.ref);
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
     * is false. {@code teardownStore} (resolved off {@code s.ref.getStore()}, falling back to
     * {@code s.puppetRef.getStore()} when the player ref itself is gone) is used ONLY as an
     * entity-still-resolvable validity gate now - the actual reveal/despawn mutations route
     * through {@code commandBuffer}.
     *
     * <p><b>Accessor-bug fix (fix round):</b> {@code revealByScale}/{@code despawn} now take the
     * {@code commandBuffer} {@code stop()} threads through (the SAME nullable parameter it already
     * passes to {@code returnCustody} and the mount-anchor despawn), never {@code teardownStore} -
     * the prior store-routed calls threw {@code IllegalStateException("Store is currently
     * processing!")} on every common exit path (re-press F via {@code toggle}, walk-off/
     * tool-changed/out-of-inputs/RITUAL_COMPLETE via the heartbeat), silently swallowed into a
     * fine log, leaving a ghost puppet untracked in the world and (whenever the hide itself HAD
     * applied) the real player stuck invisible.
     *
     * <p><b>Round-6 puppet smoke (D-A secondary):</b> DAMAGED ({@code onDamage}) and DIED
     * ({@code stopForRef}) NOW thread the live {@code CommandBuffer} their own dispatch already
     * receives (both are the common connected-player exit reasons - a station-interrupting hit or
     * a death mid-work - previously stranding an invisible player until their next reconnect with
     * no recovery). Only {@code stopFor} (disconnect) and {@code stopAll} (server shutdown)
     * genuinely have no live accessor and still pass {@code null} through {@code stop()} - a
     * disconnecting/shutting-down player has no live entity left to network a reveal packet to
     * anyway, so this is not a gap: {@code #reassertOnReady} unconditionally re-networks the
     * correct scale/model on that player's NEXT {@code PlayerReadyEvent} (reconnect), independent
     * of whatever this method did or skipped at disconnect time. A {@code null} commandBuffer here
     * still leaves the reveal/despawn un-applied for THIS exit - the puppet is {@code
     * NonSerialized} so a leftover despawn is harmless past a restart.
     */
    static void revealAndDespawn(@Nonnull StationSession s, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        if (!s.puppetActive) {
            return;
        }
        Store<EntityStore> teardownStore = resolveTeardownStore(s);
        if (teardownStore != null) {
            if (Puppet.HIDE_ROUTE_SCALE.equals(s.puppetHideRoute) && s.ref != null && s.ref.isValid()
                    && commandBuffer != null) {
                PlayerPuppetService.revealByScale(commandBuffer, s.ref, s.puppetSavedScale);
            }
            PlayerPuppetService.despawn(s.puppetRef, commandBuffer);
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
        s.puppetHeldItemId = null;
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
     * the action's default clip/prop". The animation packet itself still routes through {@code
     * store} ({@link PlayerPuppetService#playAnimation} sends a network packet, not a component
     * mutation - not part of the accessor-bug fix); the prop sync (a real {@code Hotbar} component
     * mutation) routes through {@code commandBuffer} - see {@link #syncProp}.
     */
    static void playSwing(@Nonnull StationSession s, @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nullable Player player) {
        if (!s.puppetActive) {
            return;
        }
        StationStep.PuppetOverride override = activeStepPuppetOverride(s);
        String clip = resolveEffectiveClip(override != null ? override.getClip() : null, s.emoteId);
        if (clip != null && !clip.isBlank()) {
            PlayerPuppetService.playAnimation(store, s.puppetRef, AnimationSlot.Emote, null, clip, true);
        }
        syncProp(s, commandBuffer, player, override);
    }

    /**
     * P-1 fix (held-item mirror refresh, post-round-5 puppet smoke): resolves the CURRENT
     * effective prop item id fresh every beat (a station's own {@code Puppet.Prop} may be
     * {@code MirrorHeld}, so this tracks a live tool switch, e.g. hatchet-for-hammer mid-work) and
     * hands it to {@code ziggfreed-common}'s {@link PlayerPuppetService#updateHeldItem} - the
     * dirty-gated primitive that only touches the puppet's {@code Hotbar} component (and so only
     * fans a per-viewer equipment-update packet) when the resolved id actually CHANGED from
     * {@link StationSession#puppetHeldItemId} (this session's own last-mirrored value, threaded
     * back from the primitive's return so it stays authoritative across beats - the primitive
     * itself is stateless). Accessor-bug fix (fix round, unchanged by this leg): the mutation
     * still routes through {@code commandBuffer}, never {@code store} - this runs from
     * {@link #playSwing}, reached via {@code StationService#runSwing} on the heartbeat
     * frame-drain's swing-beat timer, a processing context (the same class as {@code toggle}).
     */
    private static void syncProp(@Nonnull StationSession s, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nullable Player player, @Nullable StationStep.PuppetOverride override) {
        if (s.puppetRef == null || !s.puppetRef.isValid()) {
            return;
        }
        Puppet.Prop effectiveProp = override != null && override.getProp() != null
                ? override.getProp() : s.puppetDefaultProp;
        String itemId = resolveEffectivePropItemId(heldItemIdOf(player), effectiveProp);
        s.puppetHeldItemId = PlayerPuppetService.updateHeldItem(commandBuffer, s.puppetRef, s.puppetHeldItemId, itemId);
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
