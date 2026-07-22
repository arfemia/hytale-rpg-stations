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
 * rpgstations.lang} whenever a key is added or removed - both the fixed engine keys below
 * AND every per-station content key ({@code rpgstations.station.<id>.name}/{@code .desc})
 * once a leg ships one (leg 2 ships the engine only, no station content, so only the fixed
 * engine keys are listed here today; leg 3's standalone sawmill adds its own two entries when
 * it lands). A mismatch means either a shipped key the validator doesn't know about
 * (harmless) or a validator entry for a retired key (also harmless, but stale).
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
            "rpgstations.ui.station.stop.player",
            "rpgstations.ui.station.stop.moved",
            "rpgstations.ui.station.stop.damaged",
            "rpgstations.ui.station.stop.out_of_inputs",
            "rpgstations.ui.station.stop.inventory_full",
            "rpgstations.ui.station.stop.session_cap",
            "rpgstations.ui.station.stop.station_gone",
            "rpgstations.ui.station.stop.tool_changed",
            "rpgstations.ui.station.stop.tool_broke");

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
