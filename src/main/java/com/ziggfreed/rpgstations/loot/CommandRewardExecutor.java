package com.ziggfreed.rpgstations.loot;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.common.util.CommandExecutor;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The zero-code integration surface a {@link com.ziggfreed.rpgstations.asset.Roll.Grants}
 * command runs through (design section 4.5.1): substitutes the fixed placeholder set, then
 * dispatches via the common {@code util.CommandExecutor} AS THE SERVER CONSOLE (so an authored
 * command is never limited to what the triggering player has permission for - a server owner
 * writing {@code "give {player} Wood_Hardwood_Planks 1"} expects it to just work).
 */
public final class CommandRewardExecutor {

    private CommandRewardExecutor() {
    }

    /** One resolved-command placeholder set for a single grant moment. */
    public static final class Placeholders {
        @Nonnull private final String player;
        @Nonnull private final String uuid;
        @Nonnull private final String station;
        @Nonnull private final String action;
        @Nonnull private final String cycles;

        public Placeholders(@Nonnull String player, @Nonnull String uuid, @Nonnull String station,
                @Nonnull String action, int cycles) {
            this.player = player;
            this.uuid = uuid;
            this.station = station;
            this.action = action;
            this.cycles = Integer.toString(cycles);
        }

        @Nonnull
        public static Placeholders of(@Nonnull PlayerRef playerRef, @Nonnull String stationId,
                @Nonnull String actionId, int cycles) {
            String username = playerRef.getUsername();
            String uuidStr = playerRef.getUuid() != null ? playerRef.getUuid().toString() : "";
            return new Placeholders(username != null ? username : "", uuidStr, stationId, actionId, cycles);
        }
    }

    /** Substitute {@code {player}}/{@code {uuid}}/{@code {station}}/{@code {action}}/{@code {cycles}} in {@code raw}. */
    @Nonnull
    public static String substitute(@Nonnull String raw, @Nonnull Placeholders p) {
        return raw
                .replace("{player}", p.player)
                .replace("{uuid}", p.uuid)
                .replace("{station}", p.station)
                .replace("{action}", p.action)
                .replace("{cycles}", p.cycles);
    }

    /** Substitute + run {@code raw} as the server console; never throws. */
    public static void run(@Nonnull String raw, @Nonnull Placeholders p) {
        if (raw.isBlank()) {
            return;
        }
        try {
            String resolved = substitute(raw, p);
            CommandExecutor.executeAsConsole(resolved, p.player);
        } catch (Throwable t) {
            Log.fine("STATION loot command grant failed for '" + raw + "': " + t.getMessage());
        }
    }
}
