package com.ziggfreed.rpgstations.command;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.rpgstations.i18n.RpgMsg;
import com.ziggfreed.rpgstations.station.StationCameraPreset;
import com.ziggfreed.rpgstations.station.StationCameraPrefs;
import com.ziggfreed.rpgstations.station.StationValidator;
import com.ziggfreed.rpgstations.validation.Finding;

/**
 * {@code /rpgstations <camera|validate> [action]} - the admin maintenance/tuning command group
 * (design 4.1). Admin-gated with {@link HytalePermissionsProvider#GROUP_ADMIN} at the framework
 * level (the mob-scaling/kweebec sibling-mod idiom), never a manual runtime permission check.
 *
 * <ul>
 *   <li>{@code camera <preset>} - set the CALLING player's own {@link StationCameraPreset}
 *       tuning override for their next station session (transient, {@link StationCameraPrefs},
 *       never persisted; overrides a station asset's own {@code Camera.Recipe} default).</li>
 *   <li>{@code camera list} - chat every known preset id (dynamic over {@link
 *       StationCameraPreset#values()}, never a hand-maintained list) plus the caller's current
 *       preset.</li>
 *   <li>{@code validate} - run {@link StationValidator#validate()} over the folded
 *       station/lootable catalog and chat the aggregate: the summary line plus every finding,
 *       the SAME information {@code RpgStationsPlugin.onStationAssetsLoaded}'s boot-log audit
 *       already prints via {@link StationValidator#runAndLog()} (also called here so the log
 *       carries an identical run: chat a live run, then log a matching one).</li>
 * </ul>
 */
public final class RpgStationsCommand extends CommandBase {

    private final RequiredArg<String> subArg;
    private final OptionalArg<String> actionArg;
    private final OptionalArg<String> optArg;

    /** The throwaway scope-3 NPC-performer spike harness (dev-only, admin-gated with the command). */
    private final NpcPerformerSpike npcSpike = new NpcPerformerSpike();

    public RpgStationsCommand() {
        // The engine resolves the command + arg descriptions as localization keys.
        super("rpgstations", "rpgstations.command.desc");
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADMIN);
        this.subArg = withRequiredArg("sub", "rpgstations.command.arg.sub", ArgTypes.STRING);
        this.actionArg = withOptionalArg("action", "rpgstations.command.arg.action", ArgTypes.STRING);
        this.optArg = withOptionalArg("opt", "rpgstations.command.arg.opt", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String sub = subArg.get(ctx);
        sub = sub == null ? "" : sub.trim().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "camera" -> camera(ctx);
            case "validate" -> validate(ctx);
            case "npcspike" -> npcspike(ctx);
            default -> ctx.sendMessage(RpgMsg.tr("command.usage").color(Color.YELLOW));
        }
    }

    /**
     * {@code camera <preset>|list} applies to the CALLING player only (no {@code --player}
     * target - a console sender has no station-camera preference to set).
     */
    private void camera(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(RpgMsg.tr("command.camera.players_only").color(Color.RED));
            return;
        }
        String action = ctx.provided(actionArg) ? actionArg.get(ctx) : null;
        String trimmed = action == null ? "" : action.trim();
        if (trimmed.isEmpty()) {
            ctx.sendMessage(RpgMsg.tr("command.camera.usage").color(Color.YELLOW));
            return;
        }
        if ("list".equalsIgnoreCase(trimmed)) {
            StationCameraPreset current = StationCameraPrefs.get(player.getUuid());
            ctx.sendMessage(RpgMsg.tr("command.camera.list", presetIdList(), current.id()).color(Color.WHITE));
            return;
        }
        StationCameraPreset preset = StationCameraPreset.fromId(trimmed);
        if (preset == null) {
            ctx.sendMessage(RpgMsg.tr("command.camera.unknown_preset", trimmed, presetIdList()).color(Color.YELLOW));
            return;
        }
        StationCameraPrefs.set(player.getUuid(), preset);
        ctx.sendMessage(RpgMsg.tr("command.camera.set", preset.id()).color(Color.GREEN));
    }

    /** Comma-joined preset ids in declaration order (machine ids - safe as a raw arg). */
    @Nonnull
    private static String presetIdList() {
        StringBuilder sb = new StringBuilder();
        for (StationCameraPreset preset : StationCameraPreset.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(preset.id());
        }
        return sb.toString();
    }

    /**
     * {@code npcspike start [noserialize] | walk | stop | prop <itemId|off> | clip <emoteId>} -
     * the throwaway scope-3 NPC-performer spike (dev-only). Applies to the CALLING player only
     * (it clones the caller's own skin onto the spawned Role NPC and drives it from the caller's
     * position/facing). {@code prop}/{@code clip} are the decision-48 batch-2 spike extension
     * (prop mirroring + clip playing, the two performer-contract items the merged spike left
     * UNPROVEN - see {@link NpcPerformerSpike}'s class javadoc). Delegates the world-thread work
     * to {@link NpcPerformerSpike}.
     */
    private void npcspike(@Nonnull CommandContext ctx) {
        if (!(ctx.sender() instanceof PlayerRef player)) {
            ctx.sendMessage(RpgMsg.tr("command.camera.players_only").color(Color.RED));
            return;
        }
        String action = ctx.provided(actionArg) ? actionArg.get(ctx) : null;
        action = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        // Preserve case: item ids and emote ids (e.g. "RPG_Emote_Hammer") are case-sensitive.
        String opt = ctx.provided(optArg) ? optArg.get(ctx) : null;
        boolean noSerialize = opt != null && "noserialize".equalsIgnoreCase(opt.trim());
        switch (action) {
            case "start" -> npcSpike.start(player, noSerialize);
            case "walk" -> npcSpike.walk(player);
            case "stop" -> npcSpike.stop(player);
            case "prop" -> npcSpike.prop(player, opt);
            case "clip" -> npcSpike.clip(player, opt);
            default -> ctx.sendMessage(RpgMsg.tr("command.npcspike.usage").color(Color.YELLOW));
        }
    }

    /**
     * Run the station validator and chat the aggregate: the summary line plus every finding,
     * matching what the boot-log audit prints. Also re-runs {@link StationValidator#runAndLog()}
     * so the log carries a matching entry (the {@code /mmoconfig validate} dual-call shape:
     * chat a live run, log a matching one).
     */
    private void validate(@Nonnull CommandContext ctx) {
        List<Finding> findings = StationValidator.validate();
        ctx.sendMessage(RpgMsg.tr("command.validate.header").color(Color.WHITE));
        boolean hasProblems = StationValidator.problemCount(findings) > 0;
        ctx.sendMessage(Message.raw(StationValidator.summarize(findings)).color(hasProblems ? Color.YELLOW : Color.GREEN));
        for (Finding f : findings) {
            Color color = switch (f.severity()) {
                case ERROR -> Color.RED;
                case WARNING -> Color.YELLOW;
                case INFO -> Color.GRAY;
            };
            ctx.sendMessage(Message.raw("[" + f.code() + "] " + f.message()).color(color));
        }
        StationValidator.runAndLog();
    }
}
