package com.ziggfreed.rpgstations.station;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * TEMPORARY transient per-player preference for {@link StationCameraPreset} (the admin-
 * iterable camera-recipe hunt). Ported verbatim from the MMO's
 * {@code station.StationCameraPrefs} (RPG Stations extraction leg 2). In-memory ONLY - never
 * persisted, never wired into any player-data seam. A live tuning knob for cycling candidate
 * camera recipes within one session (a future {@code /rpgstations camera <preset>}), not
 * player-facing data that should survive a restart.
 *
 * <p>{@code StationHoldController#applyCamera} runs ONCE per session, at engage - a preset
 * switch takes effect on the player's NEXT station session, not an already-active one.
 */
public final class StationCameraPrefs {

    private static final Map<UUID, StationCameraPreset> PRESETS = new ConcurrentHashMap<>();

    private StationCameraPrefs() {
    }

    /**
     * The player's current preset, defaulting to {@link StationCameraPreset#DEFAULT} when
     * unset. Display-only convenience (a preset-list command) - the runtime FaceBlock
     * resolution does NOT use this method (it cannot distinguish "explicitly chose FROZEN"
     * from "never touched the command" and would silently shadow the station asset's own
     * default). Use {@link #getExplicit} for resolution.
     */
    @Nonnull
    public static StationCameraPreset get(@Nonnull UUID playerId) {
        StationCameraPreset preset = PRESETS.get(playerId);
        return preset != null ? preset : StationCameraPreset.DEFAULT;
    }

    /**
     * The player's EXPLICIT preset choice, or {@code null} if they have never set one this
     * session - distinct from {@link #get}. {@link StationCameraPreset#resolve} consults THIS
     * accessor so an untouched player falls through to the station asset's own default.
     */
    @Nullable
    public static StationCameraPreset getExplicit(@Nonnull UUID playerId) {
        return PRESETS.get(playerId);
    }

    /** Set the player's preset going forward. */
    public static void set(@Nonnull UUID playerId, @Nonnull StationCameraPreset preset) {
        PRESETS.put(playerId, preset);
    }
}
