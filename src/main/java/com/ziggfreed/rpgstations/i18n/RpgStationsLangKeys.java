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
 * Phase-2 leg C adds the {@code ui.station.custody.*} placed-input toast keys (design 9.4).
 * Phase-2 leg D adds {@code ui.station.mount_unavailable} (design 9.2's Mount knob family - the
 * Entity surface's engage-denial toast; {@code seat_unavailable} stays the Block surface's own).
 * The press-F custody RETRIEVAL fix round (2026-07-22) adds {@code ui.station.retrieve.busy}/
 * {@code .done} (the toast keys, {@code StationService#retrieveCustody}) and
 * {@code ui.station.retrieve.hint} (the display entity's own {@code Interactions} hint,
 * {@code StationCustodyDisplay#addRetrieveInteraction}).
 * The round-5 grant-notification round (2026-07-22) adds {@code ui.station.gain.produced} (the
 * live item-gain toast, {@code StationService#notifyItemGain}).
 * Round-7 (D-6) adds {@code ui.station.summary.enhance_durability} (the engine-owned durability
 * row of the enhancement session summary, {@code StationService#enhanceLedgerRows}); round-7 (D-4)
 * also drops the quantity from {@code ui.station.gain.produced}'s value (now bare {@code {0}}) so
 * the toast matches a native pickup exactly - the quantity rides the item-slot count badge.
 * Decision 48 (batch-2 spike extension, 2026-07-24) adds the {@code command.npcspike.prop_*}/
 * {@code clip_*} keys backing {@code NpcPerformerSpike}'s {@code prop}/{@code clip} subcommands.
 * The seam-wave picker leg (R3, decision 50) adds {@code ui.station.picker.title}/{@code .hint}/
 * {@code .locked}/{@code .locked_generic} backing {@code pages.RpgStationPickerPage}, plus
 * {@code ui.station.picker.selected} (the selection-confirm toast fired from
 * {@code StationService#onPickerSelect}, added in the selection-wave verify-fix).
 * The maintainer picker-smoke fix (2026-07-28) adds {@code ui.station.picker.cost} (the per-tab
 * cost line, "{0}x {1} -&gt; {2}x {3}", {@code StationService#pickerCostLine}) - present in ALL
 * NINE locale {@code rpgstations.lang} files (a wordless format string, identical in each).
 * The seam-wave validator/content leg (R4) LOCKSTEP CLEANUP folds in the wave-3 gap this set had
 * drifted from its own {@code rpgstations.lang}: {@code ui.station.no_action} (the multi-action
 * "nothing this station can work with" toast), the wave-3 {@code ui.station.stop.complete}/
 * {@code .capped}/{@code .inputs_exhausted}/{@code .anchor_lost}/{@code .path_blocked} stop-reason
 * keys, {@code ui.station.anchor_missing}/{@code .anchor_busy} (the multi-station anchor-claim
 * denial toasts), the shipped fish-exemplar content keys ({@code station.cuttingboard.name}/
 * {@code .desc}, {@code station.cookingfire.name}/{@code .desc}, {@code action.prepfish.label}),
 * and adds {@code ui.station.bench.hint} (the decision-51a native SNEAK+F bench-window advertise
 * hint, the {@code retrieve.hint} sibling).
 * The scope-3 standing-mount verify PREP adds {@code station.mountspike.name}/{@code .desc} (the
 * throwaway {@code MountSpike.json} dev station, {@code Hold.Mount.Surface: "Entity"}); remove
 * alongside the station/item/interaction files once the maintainer's in-game smoke is done.
 * The AV-wave anchor-toast split (2026-07-29) adds {@code ui.station.anchor_unreachable}: an anchor
 * that WAS found but that the puppet cannot path to used to reuse {@code ui.station.anchor_missing}
 * ("No {0} found within {1} blocks"), which told the player the exact wrong thing - two distinct
 * failures now carry two distinct toasts.
 * A mismatch means either a shipped key the validator doesn't know about (harmless) or a
 * validator entry for a retired key (also harmless, but stale).
 */
public final class RpgStationsLangKeys {

    private static final Set<String> KEYS = Set.of(
            "rpgstations.ui.station.start",
            "rpgstations.ui.station.locked",
            "rpgstations.ui.station.occupied",
            "rpgstations.ui.station.no_materials",
            "rpgstations.ui.station.custody.placed",
            "rpgstations.ui.station.custody.topped_up",
            "rpgstations.ui.station.retrieve.busy",
            "rpgstations.ui.station.retrieve.done",
            "rpgstations.ui.station.retrieve.hint",
            "rpgstations.ui.station.inventory_full",
            "rpgstations.ui.station.wrong_tool",
            "rpgstations.ui.station.tool_worn",
            "rpgstations.ui.station.seat_unavailable",
            "rpgstations.ui.station.mount_unavailable",
            "rpgstations.ui.station.practice",
            "rpgstations.ui.station.rare_find",
            "rpgstations.ui.station.lucky",
            "rpgstations.ui.station.no_action",
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
            "rpgstations.ui.station.stop.complete",
            "rpgstations.ui.station.stop.capped",
            "rpgstations.ui.station.stop.inputs_exhausted",
            "rpgstations.ui.station.stop.anchor_lost",
            "rpgstations.ui.station.stop.path_blocked",
            "rpgstations.ui.station.anchor_missing",
            "rpgstations.ui.station.anchor_unreachable",
            "rpgstations.ui.station.anchor_busy",
            "rpgstations.ui.station.bench.hint",
            "rpgstations.ui.station.summary.title",
            "rpgstations.ui.station.summary.cycles",
            "rpgstations.ui.station.summary.item_consumed",
            "rpgstations.ui.station.summary.item_produced",
            "rpgstations.ui.station.summary.lucky",
            "rpgstations.ui.station.summary.items_more",
            "rpgstations.ui.station.summary.enhance_durability",
            "rpgstations.ui.station.gain.produced",
            "rpgstations.station.sawmill.name",
            "rpgstations.station.sawmill.desc",
            "rpgstations.station.cuttingboard.name",
            "rpgstations.station.cuttingboard.desc",
            "rpgstations.station.cookingfire.name",
            "rpgstations.station.cookingfire.desc",
            "rpgstations.action.prepfish.label",
            "rpgstations.command.desc",
            "rpgstations.command.arg.sub",
            "rpgstations.command.arg.action",
            "rpgstations.command.usage",
            "rpgstations.command.camera.usage",
            "rpgstations.command.camera.players_only",
            "rpgstations.command.camera.unknown_preset",
            "rpgstations.command.camera.set",
            "rpgstations.command.camera.list",
            "rpgstations.command.validate.header",
            // SCOPE-3 NPC-performer spike (throwaway dev harness, /rpgstations npcspike).
            "rpgstations.command.arg.opt",
            "rpgstations.command.npcspike.usage",
            "rpgstations.command.npcspike.spawned",
            "rpgstations.command.npcspike.spawned_noserialize",
            "rpgstations.command.npcspike.spawn_failed",
            "rpgstations.command.npcspike.role_missing",
            "rpgstations.command.npcspike.walking",
            "rpgstations.command.npcspike.walk_no_npc",
            "rpgstations.command.npcspike.role_not_ready",
            "rpgstations.command.npcspike.stopped",
            "rpgstations.command.npcspike.stop_none",
            // Decision 48 (batch-2 spike extension): prop + clip checks.
            "rpgstations.command.npcspike.prop_set",
            "rpgstations.command.npcspike.prop_cleared",
            "rpgstations.command.npcspike.prop_unknown_item",
            "rpgstations.command.npcspike.clip_usage",
            "rpgstations.command.npcspike.clip_attempted",
            // Seam-wave picker leg (R3, decision 50).
            "rpgstations.ui.station.picker.title",
            "rpgstations.ui.station.picker.hint",
            "rpgstations.ui.station.picker.locked",
            "rpgstations.ui.station.picker.locked_generic",
            "rpgstations.ui.station.picker.selected",
            "rpgstations.ui.station.picker.cost",
            // SCOPE-3 standing-mount verify PREP (throwaway dev spike, /rpgstations - the block's
            // own Use interaction, no new command). Delete alongside MountSpike.json.
            "rpgstations.station.mountspike.name",
            "rpgstations.station.mountspike.desc");

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
