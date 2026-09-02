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
 * The pre-release schema sweep adds {@code ui.station.summary.produced_breakdown} - the smaller
 * SECOND line under a PRODUCED ledger row, decomposing that row's total into its per-cycle base and
 * yield-bonus terms plus the cycle count ({@code StationService#yieldBreakdownLine}). It renders only
 * when the yield actually moved the number, so its presence is itself the signal that the tool is
 * earning something; the wording stays tool-shaped, never progression-shaped.
 * Round-7 (D-6) adds {@code ui.station.summary.enhance_durability} (the engine-owned durability
 * row of the enhancement session summary, {@code StationService#enhanceLedgerRows}) and
 * {@code ui.station.summary.enhance_stat}, the same summary's plain per-stat row for a stat no mod
 * supplied a styled label for (a wordless "{0} +{1}" format string, identical in all nine locales -
 * the stat id is an opaque token this engine cannot translate); round-7 (D-4)
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
 * denial toasts), and the shipped fish-exemplar content keys ({@code station.cuttingboard.name}/
 * {@code .desc}, {@code station.cookingfire.name}/{@code .desc}, {@code action.prepfish.label}).
 * The scope-3 standing-mount verify PREP adds {@code station.mountspike.name}/{@code .desc} (the
 * throwaway {@code MountSpike.json} dev station, {@code Hold.Mount.Surface: "Entity"}); remove
 * alongside the station/item/interaction files once the maintainer's in-game smoke is done.
 * The AV-wave anchor-toast split (2026-07-29) adds {@code ui.station.anchor_unreachable}: an anchor
 * that WAS found but that the puppet cannot path to used to reuse {@code ui.station.anchor_missing}
 * ("No {0} found within {1} blocks"), which told the player the exact wrong thing - two distinct
 * failures now carry two distinct toasts.
 * The doneness leg adds {@code ui.station.output_ready} (a produced batch's ready window opening,
 * toasted to the worker once per window, {@code StationService#noteCustodyProduce}) and
 * {@code ui.station.output_overdone} (an expired window collapsing to its Overdone items, toasted
 * to whoever's touch settled it, {@code StationService#settleDoneness}).
 * A mismatch means either a shipped key the validator doesn't know about (harmless) or a
 * validator entry for a retired key (also harmless, but stale).
 */
public final class RpgStationsLangKeys {

    private static final Set<String> KEYS = Set.of(
            "rpgstations.ui.station.start",
            "rpgstations.ui.station.locked",
            "rpgstations.ui.station.occupied",
            // The two owner-ceiling denials (Settings.Limits): too many sessions in this world, and
            // too many stations in it already holding placed input.
            "rpgstations.ui.station.server_busy",
            "rpgstations.ui.station.storage_full",
            "rpgstations.ui.station.no_materials",
            "rpgstations.ui.station.custody.placed",
            "rpgstations.ui.station.custody.topped_up",
            "rpgstations.ui.station.retrieve.busy",
            "rpgstations.ui.station.retrieve.done",
            "rpgstations.ui.station.retrieve.hint",
            // The per-socket custody refusals: a socket with no room, a material no socket takes,
            // a Required socket unfilled at engage (plus its Label-naming form), a Required block
            // socket lost mid-session, and a Share-gated refusal (plus its Label-naming form).
            "rpgstations.ui.station.socket_full",
            "rpgstations.ui.station.socket_wrong_input",
            "rpgstations.ui.station.socket_missing",
            "rpgstations.ui.station.socket_missing_named",
            "rpgstations.ui.station.socket_lost",
            // The multiblock-structure toasts: a completed shape whose anchor another pattern
            // already claims, the pattern's Requires gate failing for the placer (plus its
            // NameKey-naming form), and the STRUCTURE_LOST stop toast.
            "rpgstations.ui.station.structure_conflict",
            "rpgstations.ui.station.pattern_requirements_unmet",
            "rpgstations.ui.station.pattern_requirements_unmet_named",
            "rpgstations.ui.station.structure_lost",
            // The doneness ready-window toasts: a produced batch now waiting Ready in its custody
            // pile (to the worker, once per window open), and an expired window collapsing to its
            // Overdone items (to whoever's touch settled it).
            "rpgstations.ui.station.output_ready",
            "rpgstations.ui.station.output_overdone",
            "rpgstations.ui.station.not_shared",
            "rpgstations.ui.station.not_shared_named",
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
            "rpgstations.ui.station.summary.title",
            "rpgstations.ui.station.summary.cycles",
            "rpgstations.ui.station.summary.item_consumed",
            "rpgstations.ui.station.summary.item_produced",
            "rpgstations.ui.station.summary.produced_breakdown",
            "rpgstations.ui.station.summary.lucky",
            "rpgstations.ui.station.summary.items_more",
            "rpgstations.ui.station.summary.enhance_durability",
            "rpgstations.ui.station.summary.enhance_stat",
            "rpgstations.ui.station.gain.produced",
            "rpgstations.station.sawmill.name",
            "rpgstations.station.sawmill.desc",
            "rpgstations.station.cuttingboard.name",
            "rpgstations.station.cuttingboard.desc",
            "rpgstations.station.cookingfire.name",
            "rpgstations.station.cookingfire.desc",
            // The cooking pit family: the station's identity, the structure pattern's identity
            // (the pattern/structure toasts name it), the two action labels, and the three
            // socket Labels the *_named refusals speak.
            "rpgstations.station.cookingpit.name",
            "rpgstations.station.cookingpit.desc",
            "rpgstations.structure.cookingpit.name",
            "rpgstations.structure.cookingpit.desc",
            "rpgstations.action.grill.label",
            "rpgstations.action.stew.label",
            "rpgstations.socket.cookingpit.vessel",
            "rpgstations.socket.cookingpit.ingredients",
            "rpgstations.socket.cookingpit.output",
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
