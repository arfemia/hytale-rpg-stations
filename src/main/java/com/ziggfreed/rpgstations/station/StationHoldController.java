package com.ziggfreed.rpgstations.station;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector2f;
import org.joml.Vector3f;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.camera.ServerCameraService;
import com.ziggfreed.common.instance.effect.EntityEffectService;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Policy-thin glue over ziggfreed-common for a station session's hold + camera + work emote.
 * Ported verbatim from the MMO's {@code station.StationHoldController} (RPG Stations
 * extraction leg 2), {@code SafeLog} severed to RpgStations' own {@code util.Log} - every
 * other call is the SAME common primitive the MMO already used (no severance needed there:
 * {@code ServerCameraService}/{@code EntityEffectService} are already {@code ziggfreed-common}
 * classes).
 *
 * <p><b>Hold mechanics (decay-as-release):</b> the hold {@code EntityEffect} is applied with a
 * SHORT fixed TTL ({@link #HOLD_TTL_SECONDS}) and re-applied every heartbeat under
 * {@code OverlapBehavior.OVERWRITE}. Stop re-applying and the hold decays on its own within
 * 2.5s even if a teardown step is missed.
 *
 * <p>The shipped {@code RPG_Station_Hold.json} disables walk/run/sprint/jump but leaves
 * CROUCH enabled - crouch is the diegetic "step back" exit input.
 *
 * <p>Every method is best-effort and world-thread (store-touching calls); camera reset is
 * packet-only so it is safe from any teardown path.
 */
final class StationHoldController {

    /** Hold-effect TTL in seconds; the 1000ms heartbeat re-applies well inside it. */
    static final float HOLD_TTL_SECONDS = 2.5f;

    /**
     * The seat-mode Action-slot swing fix's zero-authoring default clip id: the Hatchet
     * family's own attack clip ({@code "Chop"}). NOT a cross-family default - a station whose
     * {@code Tool} gate requires a different family MUST author its own {@code ActionClip}.
     */
    static final String DEFAULT_ACTION_CLIP = "Chop";

    private StationHoldController() {
    }

    /** Apply (or refresh) the short-TTL hold effect. No-op when the session has no movement lock. */
    static void applyHold(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        if (!s.movementLock || s.holdEffectId == null || s.holdEffectId.isBlank()) {
            return;
        }
        boolean ok = EntityEffectService.applyTimed(
                s.ref, s.holdEffectId, HOLD_TTL_SECONDS, OverlapBehavior.OVERWRITE, store);
        if (!ok) {
            Log.fine("STATION hold effect '" + s.holdEffectId + "' did not apply");
        }
    }

    /**
     * Release the hold effect INSTANTLY via the engine's targeted
     * {@code EffectControllerComponent.removeEffect}. The short-TTL decay stays as the
     * self-healing fallback.
     */
    static void releaseHold(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        if (!s.movementLock || s.holdEffectId == null || s.holdEffectId.isBlank()
                || s.ref == null || !s.ref.isValid()) {
            return;
        }
        try {
            int index = EntityEffect.getAssetMap().getIndex(s.holdEffectId);
            if (index < 0) {
                return;
            }
            EffectControllerComponent controller =
                    store.getComponent(s.ref, EffectControllerComponent.getComponentType());
            if (controller != null) {
                controller.removeEffect(s.ref, index, store);
            }
        } catch (Throwable t) {
            Log.fine("STATION hold release failed (decay fallback covers it): " + t.getMessage());
        }
    }

    /**
     * A modest downward pitch (radians) for the {@code FaceBlock} fixed work camera.
     */
    private static final float FACE_BLOCK_PITCH = -0.3f;

    /**
     * Force the working camera: a behind-the-player third-person follow cam, sent in the
     * FIRST-PARTY packet shape - {@code ClientCameraView.Custom} + a fully-populated
     * {@code ServerCameraSettings}. Every first-party sender uses Custom+settings to engage
     * and Custom+false+null to disable.
     *
     * <p>When {@link StationSession#faceBlock} is set, the recipe sent is one of
     * {@link StationCameraPreset}'s documented experiments - see that enum's javadoc.
     */
    static void applyCamera(@Nonnull StationSession s) {
        if (!s.cameraApplied || s.playerRef == null) {
            return;
        }
        try {
            ServerCameraSettings settings = new ServerCameraSettings();
            settings.positionLerpSpeed = 0.2f;
            settings.rotationLerpSpeed = 0.2f;
            settings.distance = 4.0f;
            settings.displayCursor = false;
            settings.isFirstPerson = false;
            settings.eyeOffset = true;
            settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
            if (s.faceBlock) {
                applyFaceBlockPreset(settings, s);
            }
            ServerCameraService.apply(s.playerRef, ClientCameraView.Custom, s.cameraLocked, settings);
        } catch (Throwable t) {
            Log.fine("STATION camera apply failed: " + t.getMessage());
        }
    }

    /**
     * Fills in the {@code FaceBlock} field diff for the resolved {@link StationCameraPreset}
     * ({@link StationCameraPreset#resolve} - the player's explicit override via
     * {@link StationCameraPrefs#getExplicit}, else the station asset's own
     * {@link StationSession#cameraRecipe} (design 9.7's {@code Camera.Recipe}), else
     * {@link StationCameraPreset#LOOK_ROT}).
     */
    private static void applyFaceBlockPreset(@Nonnull ServerCameraSettings settings, @Nonnull StationSession s) {
        float yaw = StationFacing.yawToward(s.originX, s.originZ, s.blockX + 0.5, s.blockZ + 0.5);
        StationCameraPreset override = StationCameraPrefs.getExplicit(s.playerRef.getUuid());
        StationCameraPreset preset = StationCameraPreset.resolve(override, s.cameraRecipe);
        switch (preset) {
            case FREE_NULL:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                break;
            case FREE_DIR:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                break;
            case LOOK_ROT:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                settings.applyLookType = ApplyLookType.Rotation;
                settings.rotation = new Direction(yaw, FACE_BLOCK_PITCH, 0f);
                break;
            case LOOK_ROT_BLEND:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                settings.applyLookType = ApplyLookType.Rotation;
                settings.rotation = new Direction(yaw, FACE_BLOCK_PITCH, 0f);
                settings.lookMultiplier = new Vector2f(1f, 1f);
                break;
            case LOOK_ROT_NO_TARGET:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                settings.applyLookType = ApplyLookType.Rotation;
                break;
            case LOOK_ROT_ATTACHED:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                settings.applyLookType = ApplyLookType.Rotation;
                settings.rotation = new Direction(yaw, FACE_BLOCK_PITCH, 0f);
                settings.rotationType = RotationType.AttachedToPlusOffset;
                break;
            case CUSTOM_SEED:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                settings.rotationType = RotationType.Custom;
                settings.rotation = new Direction(yaw, FACE_BLOCK_PITCH, 0f);
                break;
            case FROZEN:
            default:
                settings.movementForceRotationType = MovementForceRotationType.Custom;
                settings.movementForceRotation = new Direction(yaw, 0f, 0f);
                settings.rotationType = RotationType.Custom;
                settings.rotation = new Direction(yaw, FACE_BLOCK_PITCH, 0f);
                settings.mouseInputType = MouseInputType.LookAtPlane;
                settings.planeNormal = new Vector3f(0f, 1f, 0f);
                break;
        }
    }

    /** Return the camera to default player control. Safe from any teardown path. */
    static void resetCamera(@Nullable PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        try {
            ServerCameraService.reset(playerRef);
        } catch (Throwable t) {
            Log.fine("STATION camera reset failed: " + t.getMessage());
        }
    }

    /**
     * (Re)play the station's work emote clip on the {@code Emote} slot ({@code sendToSelf=true}).
     * EFFECT-MODE ONLY (the seated-worker swing fix): a seat-mode session never calls this -
     * see {@link #playActionSwing}.
     */
    static void playEmote(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        if (s.emoteId == null || s.emoteId.isBlank()) {
            return;
        }
        try {
            AnimationUtils.playAnimation(s.ref, AnimationSlot.Emote, null, s.emoteId, true, store);
        } catch (Throwable t) {
            Log.warn("STATION emote animation '" + s.emoteId + "' failed: " + t.getMessage());
        }
    }

    /**
     * The seated-worker swing fix's per-swing SEAT-MODE route, replacing {@link #playEmote}'s
     * {@code Emote}-slot re-fire with a one-shot on the {@code Action} slot via the currently
     * HELD ITEM'S OWN {@code ItemPlayerAnimations} clip set.
     */
    static void playActionSwing(@Nonnull StationSession s, @Nullable Player player,
                                @Nonnull Store<EntityStore> store) {
        if (player == null) {
            return;
        }
        try {
            ItemStack held = player.getInventory().getActiveHotbarItem();
            Item item = held != null ? held.getItem() : null;
            String itemAnimationsId = item != null ? item.getPlayerAnimationsId() : null;
            if (itemAnimationsId == null || itemAnimationsId.isBlank()) {
                // [SMOKEDIAG] R2 seated-swing diagnosis (THE decisive line, elevated from the
                // pre-existing Log.fine to info for the smoke boot): a runtime precondition
                // failure (empty/non-hatchet active slot, no PlayerAnimationsId) is
                // server-fixable, distinct from a fully-correct resolve below.
                Log.info("[SMOKEDIAG] clip-resolved SKIP activeItem="
                        + (item != null ? item.getId() : "null") + " playerAnimationsId=null");
                return;
            }
            String clipId = s.actionClip != null && !s.actionClip.isBlank() ? s.actionClip : DEFAULT_ACTION_CLIP;
            // [SMOKEDIAG] R2 seated-swing diagnosis: a fully-correct resolve (activeItem=
            // Tool_Hatchet_*, playerAnimationsId=Hatchet, clip=Chop) means the server dispatches
            // a valid swing packet on every beat - the remaining question is client rendering.
            Log.info("[SMOKEDIAG] clip-resolved activeItem=" + item.getId()
                    + " playerAnimationsId=" + itemAnimationsId + " clip=" + clipId);
            AnimationUtils.playAnimation(s.ref, AnimationSlot.Action, itemAnimationsId, clipId, true, store);
            // [SMOKEDIAG] R2 seated-swing diagnosis: confirms the server dispatched the swing
            // packet with concrete valid values (sendToSelf=true - the caster also sees it).
            Log.info("[SMOKEDIAG] packet-sent slot=Action anim=" + itemAnimationsId
                    + " clip=" + clipId + " sendToSelf=true");
        } catch (Throwable t) {
            Log.warn("STATION seat-mode action animation failed: " + t.getMessage());
        }
    }

    /** Stop the work emote (only when the entity still exists). Harmless defense. */
    static void stopEmote(@Nonnull StationSession s, @Nonnull Store<EntityStore> store) {
        if (s.emoteId == null || s.emoteId.isBlank()) {
            return;
        }
        try {
            AnimationUtils.stopAnimation(s.ref, AnimationSlot.Emote, true, store);
        } catch (Throwable t) {
            Log.fine("STATION emote animation stop failed: " + t.getMessage());
        }
    }
}
