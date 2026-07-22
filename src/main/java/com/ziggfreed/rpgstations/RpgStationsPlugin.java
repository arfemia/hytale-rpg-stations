package com.ziggfreed.rpgstations;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.ziggfreed.rpgstations.util.Log;

/**
 * Entry point for RPG Stations, a standalone Hytale mod owning the diegetic
 * interactive work-station engine (sawmill, forge, and friends) extracted out of the
 * MMO Skill Tree mod. It depends on {@code ziggfreed-common} ONLY - the MMO Skill Tree
 * keeps every piece of progression and reaches the station engine exclusively through
 * a soft extension surface (native events + the {@code api} artifact); neither mod
 * hard-deps the other.
 *
 * <p>Scaffold stage (phase 1, leg 0): this class registers nothing yet beyond the
 * setup/shutdown log lines. The real station engine (asset stores, the interaction
 * type, the frame-drain system, the extension surface impl) lands in the legs that
 * follow, per {@code .claude/research/raw/rpg-stations-unified-design-2026-07-21.md}.
 */
public class RpgStationsPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static RpgStationsPlugin instance;

    @Nonnull
    public static RpgStationsPlugin getInstance() {
        return instance;
    }

    public RpgStationsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("RpgStations initializing...");
    }

    @Override
    protected void setup() {
        Log.info("RpgStations setup complete (scaffold - station engine not yet registered).");
    }

    @Override
    protected void shutdown() {
        Log.info("RpgStations shutdown complete.");
    }
}
