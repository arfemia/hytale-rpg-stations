# Changelog

Developer changelog for RPG Stations. Version stays `1.0.0` until first release (the repo-wide
no-churn rule); everything below shipped into that same unreleased 1.0.0. No em-dashes.

## 1.0.0 (unreleased)

The diegetic interactive work-station engine, extracted out of the MMO Skill Tree jar into a
standalone, richly self-sufficient mod: with RPG Stations alone installed, a station runs its full
work loop (camera/hold/mount, tool gating, recipe conversion, conditional-lootable rolls, command
rewards) with zero progression. The MMO Skill Tree mod reaches back through a soft extension
surface (native events + typed registries, `api/`) to turn a work session into skill XP, luck, and
mastery bonuses; neither mod hard-depends on the other. See `CLAUDE.md` for the full package-by-
package reference and the origin story.

### Phase 1: extraction + the engine

- Adds the station engine itself: `StationAsset`/`LootableAsset`/`RpgStationsSettingsAsset` Pattern A codecs
  (native `Parent` inheritance, every leaf `appendInherited`), a per-player session state machine
  (`StationService`/`StationSession`), packet-camera third-person pull with a curated recipe
  vocabulary (`Camera.Recipe`, an admin-iterable preset switch for the free-camera-vs-locked-body
  hunt), an effect-mode movement lock, and native block-mount seating (`Hold.Seat`) as the crowned
  answer for a held/facing worker.
- Adds tool gating (native `Tags`/`Gather`/`Ids` routes) with optional tool-power XP scaling, and
  recipe derivation either authored (`Recipe.Conversions`) or derived from native crafting recipes
  (`Recipe.FromCrafting`), zero hand-authored conversions for a station like the Sawmill.
  Ships the standalone default Sawmill (native ids, jar-shipped) alongside the standalone
  `loot/` layer: conditional lootable rolls over native `ItemDropList`s gated/weighted by an
  extensible condition system (session length, tool durability/power, and similar session-derived
  factors, via a `FactorRegistry` other mods can extend), plus command rewards, so any third party
  integrates with zero code.
- Adds validation (`StationValidator`, warn-only, never blocks) and a session-summary panel
  (`ui/StationSummaryHud`) showing cycles, items consumed/produced, and (when a progression mod is
  present) per-skill XP rows via a `SummaryEnricher`.
- Adds the `api` extension-surface artifact (frozen once 1.0.0 releases): native Hytale events for
  observe-only moments (session started/cycle completed/session completed/tool broke) and typed
  registries for request/response points (`FactorRegistry`, `FlairUnlockRegistry`,
  `SummaryEnricherRegistry`), the mechanism the MMO bridge (`integration/stations/` in the MMO jar)
  consumes to reach back without either mod manifest-depending on the other.
- Adds `/rpgstations camera <preset>|list` (tune the camera-recipe preset) and `/rpgstations
  validate` (run the station content validator), admin-gated.
- Ships 9-locale `rpgstations.lang` (all UI/command strings key-complete across every shipped
  locale from the start).

### Phase 2: multi-action stations, placed-input custody, the Mount family, the anvil arc

- Adds multi-action stations: a `StationAsset.Actions` map lets one station block host several
  distinct actions (each a whole-group override of the station's own Work/Hold/Camera/Animation/
  Tool/Custody/Requires groups, native `Parent` inherits the whole map), diegetically selected by
  what the player is holding or has placed. An action is a STEP PROGRAM (`StationStep`, a
  `Type`-discriminated union: `Consume`/`Produce`/`Wait`/`Roll`/`Command`/`Stamp`/`Mount`) run
  through one production step-dispatch kernel (`StationStepKernel`, built on the lifted
  `ziggfreed-common` `cast.step` kernel); the classic single-action convert loop is the implicit
  four-step program every station with no `Actions` map gets for free, so the shipped Sawmill
  authors nothing new.
- Adds session-scoped placed-input custody: a state-dependent single F/use interaction where an
  empty station accepts a held (or inventory-matched) stack, a repeat press tops it up, and a
  loaded station works the placed pouch instead of draining the backpack per cycle. Unconsumed
  custody auto-returns on every session-stop path (walk-off, damage, death, disconnect, the block
  itself breaking), to the owner's inventory when reachable or dropped at the block otherwise.
  Block states flip a per-state interaction hint (`world.setBlockInteractionState`); a
  `MaxQuantity: 1` placement preserves the placed item's own metadata (durability, prior
  enhancements) rather than resetting it to a bare fresh stack on return.
- Adds a placed-input PLACED-AS-ENTITY visual: a `Custody.Display` group spawns a static,
  network-replicated, pickup-immune, physics-free prop entity at the station's block-top anchor
  rendering whatever is currently placed (a real block-shaped entity for a block item like logs, a
  bare dropped-item-style prop otherwise); the display entity is never persisted, so it never
  survives a restart, matching custody's own crash-loses-it lifecycle by construction.
- Adds the `Hold.Mount` knob family: `Surface: "Block"` (the existing seat mount, refactored behind
  the new group with zero behavior change) or `Surface: "Entity"` (a new standing work mount for a
  station that wants its worker on their feet, with a steerable/dismount-on-move knob pair).
- Adds the open flair/moment vocabulary: the old fixed 4-slot `Slot` enum is retired for an open
  string moment id (well-known constants plus a per-step `step:<actionId>:<stepId>` id a `Present`
  step resolves against), and a new standalone `FlairAsset` Pattern A type lets ANY installed mod
  or pack ship a cosmetic flair layer for a station without touching that station's own JSON.
- Adds the anvil arc's `Stamp` step: a composable roll+cap engine for rolling stat entries onto a
  placed item (`RollPool` Pattern A store, a shared `StatRollEntry` codec, weighted-pick/unique
  selection, and a composable cap model - per-item budget, per-stat caps, skill-scaled budget, all
  independently authorable) plus a registered `EnhanceStamper` api contract (`inspect`/`apply`) a
  progression mod implements to read/write its own item-enhancement format. Compute-then-commit:
  every roll/cap/availability check runs with zero mutation first, so a cancelled or failed ritual
  never partially consumes reagents or partially mutates the placed item.

See `station/CLAUDE.md`, `asset/CLAUDE.md`, `api/CLAUDE.md`, and `api/impl/CLAUDE.md` for the full
file-by-file detail behind every bullet above, including the handful of documented deviations from
the original design doc's literal prose (each grounded in the real shared source, never invented).

### Fix wave: first-boot defects (post phase 2)

The maintainer's first real boot log after phase 2 landed surfaced a handful of first-boot
defects, all fixed with no design change:

- Fixes a native `AssetStoreTypeHandler` id collision: `SettingsAsset` (this mod's own engine-
  settings singleton) collided with another loaded plugin's asset class of the same simple name
  (the id key is the CLASS SIMPLE NAME, not the fully-qualified name). Renamed to
  `RpgStationsSettingsAsset` throughout (class, codec, tests, docs).
- Fixes a false `EMPTY_CONVERSIONS` validator ERROR on a multi-action station whose station-level
  `Recipe` is absent but every action supplies its OWN recipe/program source (the anvil's
  `enhance` action runs entirely off a `Stamp`-step ritual, no `Recipe` at all) - the check is now
  action-aware, erroring only when NEITHER the station level NOR any authored action can ever run
  a cycle.
- Fixes validation-ordering false positives (`STAMP_UNKNOWN_POOL`/`LOOT_UNKNOWN_DROPLIST`/
  `MISSING_*_LANG`): the per-fold validator ran before a LATER asset layer (RollPool/Drops/lang)
  had folded the very reference it was checking. `StationValidator` now runs two passes: a
  structural-only pass at every fold (`validateStructural`/`runStructuralAndLog`, safe regardless
  of load order), and the FULL pass (incl. every cross-layer reference-existence check) ONCE,
  post-load, from a new first-`PlayerReadyEvent` hook (`RpgStationsPlugin.registerPostLoadAudit`,
  mirroring the MMO's own `ContentAudit` startup-audit timing) - `/rpgstations validate` (already
  post-load) is unaffected. The lang-key check itself is now a MERGED view: a miss against the
  jar's own hand-maintained key set falls through to a live `I18nModule.getMessage` query, so a
  pack's own additive `rpgstations.lang` overlay resolves correctly.

See `station/CLAUDE.md`'s Validation bullet for the full detail; the sibling pack fixes (the
anvil's redundant `Camera.FaceBlock`, the missing `MMO_Sharpened_Bar` `ResourceType` asset) and
the MMO-side bridge presence-check hardening live in their own repos' history.

Status: build-green throughout (Java + tests); the phase-1 parity gate and the phase-2 smoke round
(design doc section 11; the mod-root `CLAUDE.md`'s Phase 2 section) are both maintainer in-game
smoke passes still batched/pending as of this entry.
