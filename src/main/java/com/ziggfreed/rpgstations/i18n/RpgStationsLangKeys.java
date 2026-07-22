package com.ziggfreed.rpgstations.i18n;

import java.util.Set;

import javax.annotation.Nonnull;

/**
 * A hand-maintained set of every message id RpgStations authors in
 * {@code Server/Languages/<bcp47>/rpgstations.lang} (RpgStations has no
 * {@code EnglishDefaults.java}-style generator in 1.0.0 - the {@code .lang} files are
 * authored directly, design section 4.7). Backs {@code StationValidator}'s lang-key-known
 * check (critique m10 binding fix: "KEEP the lang-key presence validator check, rewired
 * against RpgStations' own lang loading; do not silently drop it").
 *
 * <p>Keep this set in lockstep with {@code src/main/resources/Server/Languages/en-US/
 * rpgstations.lang} whenever a key is added or removed. Leg 3 adds the {@code rare_find}/
 * {@code lucky} loot-notification keys, the summary-panel keys, and the shipped sawmill's own
 * content keys ({@code station.sawmill.name}/{@code .desc}). Leg P0 (phase-1 closeout) adds the
 * {@code command.*} keys backing {@code RpgStationsCommand} ({@code /rpgstations camera|validate}).
 * A mismatch means either a shipped key the validator doesn't know about (harmless) or a
 * validator entry for a retired key (also harmless, but stale).
 */
public final class RpgStationsLangKeys {

    private static final Set<String> KEYS = Set.of(
            "rpgstations.ui.station.start",
            "rpgstations.ui.station.locked",
            "rpgstations.ui.station.occupied",
            "rpgstations.ui.station.no_materials",
            "rpgstations.ui.station.inventory_full",
            "rpgstations.ui.station.wrong_tool",
            "rpgstations.ui.station.seat_unavailable",
            "rpgstations.ui.station.practice",
            "rpgstations.ui.station.rare_find",
            "rpgstations.ui.station.lucky",
            "rpgstations.ui.station.stop.player",
            "rpgstations.ui.station.stop.moved",
            "rpgstations.ui.station.stop.damaged",
            "rpgstations.ui.station.stop.out_of_inputs",
            "rpgstations.ui.station.stop.inventory_full",
            "rpgstations.ui.station.stop.session_cap",
            "rpgstations.ui.station.stop.station_gone",
            "rpgstations.ui.station.stop.tool_changed",
            "rpgstations.ui.station.stop.tool_broke",
            "rpgstations.ui.station.stop.step_failed",
            "rpgstations.ui.station.summary.title",
            "rpgstations.ui.station.summary.cycles",
            "rpgstations.ui.station.summary.item_consumed",
            "rpgstations.ui.station.summary.item_produced",
            "rpgstations.ui.station.summary.lucky",
            "rpgstations.ui.station.summary.items_more",
            "rpgstations.station.sawmill.name",
            "rpgstations.station.sawmill.desc",
            "rpgstations.command.desc",
            "rpgstations.command.arg.sub",
            "rpgstations.command.arg.action",
            "rpgstations.command.usage",
            "rpgstations.command.camera.usage",
            "rpgstations.command.camera.players_only",
            "rpgstations.command.camera.unknown_preset",
            "rpgstations.command.camera.set",
            "rpgstations.command.camera.list",
            "rpgstations.command.validate.header");

    private RpgStationsLangKeys() {
    }

    /**
     * True when {@code fullKey} (a fully-qualified message id, e.g.
     * {@code "rpgstations.ui.station.locked"}) is a known, shipped key.
     */
    public static boolean isKnown(@Nonnull String fullKey) {
        return KEYS.contains(fullKey);
    }
}
