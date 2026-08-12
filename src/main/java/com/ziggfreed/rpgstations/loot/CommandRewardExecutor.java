package com.ziggfreed.rpgstations.loot;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.common.command.CommandRunner;
import com.ziggfreed.common.util.CommandExecutor;
import com.ziggfreed.rpgstations.util.Log;

/**
 * The zero-code integration surface a {@code Grants.Commands} entry runs through: this engine's own
 * placeholder vocabulary, plus the console dispatcher every station command is run as.
 *
 * <p>The substitution, the {@code /give} positional-quantity fix (the engine reads a count only from
 * {@code --quantity=N}, so a positional one silently delivers a single item), and the per-command
 * guard all live in the shared {@code command.CommandRunner} primitive; what stays here is the two
 * things that are genuinely this engine's - WHICH placeholders a station offers, and running as the
 * SERVER CONSOLE rather than as the triggering player, so an authored
 * {@code "give {player} Wood_Hardwood_Planks 1"} just works whatever that player may do themselves.
 */
public final class CommandRewardExecutor {

    private CommandRewardExecutor() {
    }

    /**
     * The placeholders a station grant substitutes: {@code {player}}, {@code {uuid}},
     * {@code {station}}, {@code {action}}, {@code {cycles}}.
     */
    @Nonnull
    public static Map<String, String> placeholders(@Nonnull PlayerRef playerRef, @Nonnull String stationId,
            @Nonnull String actionId, int cycles) {
        String username = playerRef.getUsername();
        String uuid = playerRef.getUuid() != null ? playerRef.getUuid().toString() : "";
        return placeholders(username != null ? username : "", uuid, stationId, actionId, cycles);
    }

    /** The same set, built from already-resolved values (fixtures / a caller holding no PlayerRef). */
    @Nonnull
    public static Map<String, String> placeholders(@Nonnull String player, @Nonnull String uuid,
            @Nonnull String stationId, @Nonnull String actionId, int cycles) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("player", player);
        out.put("uuid", uuid);
        out.put("station", stationId);
        out.put("action", actionId);
        out.put("cycles", Integer.toString(cycles));
        return out;
    }

    /**
     * The dispatcher station command grants run through: the server console, with {@code player}
     * named as the acting username so a command that reads one resolves it. A refused command is
     * logged and swallowed - a failed command costs its own line, never the cycle that earned it.
     */
    @Nonnull
    public static CommandRunner.Dispatcher consoleAs(@Nonnull String player) {
        return command -> {
            if (!CommandExecutor.executeAsConsole(command, player)) {
                Log.fine("STATION loot command grant was refused: '" + command + "'");
            }
        };
    }
}
