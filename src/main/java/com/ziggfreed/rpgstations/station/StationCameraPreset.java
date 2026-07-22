package com.ziggfreed.rpgstations.station;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Admin-iterable camera-recipe presets for the free-mouse-camera + locked-body
 * {@code FaceBlock} hunt. Ported verbatim from the MMO's {@code station.StationCameraPreset}
 * (RPG Stations extraction leg 2). No first-party-proven {@code ServerCameraSettings}
 * combination delivers BOTH a free mouse-orbitable camera and a locked player body/head at
 * once; each preset below is a distinct, documented experiment over the confirmed
 * {@code ServerCameraSettings} field catalog.
 *
 * <p><b>Findings from the original hunt (preserved for continuity):</b> {@link #LOOK_ROT} is
 * the first recipe that actually STOPS the body (the core win) but also pins the camera (the
 * free mouse-look is lost). {@link #FREE_NULL}/{@link #FREE_DIR} keep the camera free but fail
 * to hold the body (the control group). {@link #FROZEN} behaves exactly as the pre-hunt
 * shipped recipe (no camera rotation at all, both channels frozen). The round-2 hybrids
 * ({@link #LOOK_ROT_BLEND}/{@link #LOOK_ROT_NO_TARGET}/{@link #LOOK_ROT_ATTACHED}/
 * {@link #CUSTOM_SEED}) each poke one adjacent, still-unexercised field starting from
 * {@link #LOOK_ROT} (or the shared baseline) to try to sever the camera/body coupling; none
 * of these combinations exists in first-party shared source.
 *
 * <p>Retire this enum once a winner is crowned in-game: fold the winning recipe back into
 * {@code StationHoldController#applyCamera} as the sole {@code FaceBlock} behavior, then
 * delete this enum, {@link StationCameraPrefs}, and the {@code camera} command family.
 */
public enum StationCameraPreset {

    /** The pre-hunt shipped recipe: body held, camera frozen too (both channels locked). */
    FROZEN,

    /** All three first-party {@code ServerCameraSettings} senders' shape: free camera, body NOT held. */
    FREE_NULL,

    /** The known-FAILED attempt-1 control: free camera, body NOT held (populated movementForceRotation alone). */
    FREE_DIR,

    /** The core win: {@code ApplyLookType.Rotation} holds the body, but the camera pins too. */
    LOOK_ROT,

    /** Round-2 hybrid 1: {@link #LOOK_ROT} plus an explicit {@code lookMultiplier=(1,1)} (untested field). */
    LOOK_ROT_BLEND,

    /** Round-2 hybrid 2: {@link #LOOK_ROT} with {@code rotation} left null (isolates the mode-switch from the target). */
    LOOK_ROT_NO_TARGET,

    /** Round-2 hybrid 3: {@link #LOOK_ROT} plus an EXPLICIT {@code rotationType=AttachedToPlusOffset} (defensive check). */
    LOOK_ROT_ATTACHED,

    /** Round-2 hybrid 4: body-lock channel + a formally-Custom camera rotation, WITHOUT the mouse-lock or applyLookType. */
    CUSTOM_SEED;

    /** The command-listing display fallback for an unset player - NOT the runtime FaceBlock default (see {@link #resolve}). */
    public static final StationCameraPreset DEFAULT = FROZEN;

    /** Parse a preset id (case-insensitive, matches the enum name lower-cased), or {@code null} for an unknown id. */
    @Nullable
    public static StationCameraPreset fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        for (StationCameraPreset preset : values()) {
            if (preset.id().equals(trimmed)) {
                return preset;
            }
        }
        return null;
    }

    /** The machine id used in command args/chat output (the enum name, lower-cased). */
    @Nonnull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The ONE precedence choke point for which preset a {@code FaceBlock}-enabled session
     * actually uses: the player's EXPLICIT camera-preference override wins when present
     * ({@code explicitOverride} non-null); otherwise the station asset's own
     * {@code Camera.FaceBlockMode} leaf, parsed via {@link #fromId}; otherwise
     * {@link #LOOK_ROT} when the asset leaf is absent, blank, or fails to parse.
     */
    @Nonnull
    public static StationCameraPreset resolve(@Nullable StationCameraPreset explicitOverride,
            @Nullable String assetFaceBlockModeId) {
        if (explicitOverride != null) {
            return explicitOverride;
        }
        StationCameraPreset assetPreset = fromId(assetFaceBlockModeId);
        return assetPreset != null ? assetPreset : LOOK_ROT;
    }
}
