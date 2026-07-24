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

### Deprecation sweep (maintainer edict close-out)

- Fixes every remaining `@Deprecated(forRemoval = true)` engine-API call in this mod's `src/main`
  (33 sites across `StationService`/`StationStepHandlers`/`StationHoldController`/
  `StationUseInteraction`, plus `LootEngine`'s own pre-existing single-purpose helper): `Player
  .getInventory().getStorage()`/`.getActiveHotbarItem()`/`.getCombinedBackpackStorageHotbar()`
  and `Player.getPlayerRef()`, every replacement the exact one each deprecated method's own
  javadoc names (`InventoryComponent.Storage`/`Hotbar` component fetch, `InventoryComponent
  #getCombined(..., BACKPACK_STORAGE_HOTBAR)`, the `PlayerRef` component fetch), never a
  guessed/wider alternative (`InventoryComponent#getItemInHand` was deliberately NOT substituted
  for `getActiveHotbarItem()` - it also folds in the `Tool` component, a different semantic).
  New `util.InventoryAccess` (DRY: the shared ref/store null-guard + component fetch every one of
  those call sites duplicated) replaces `LootEngine`'s own private `storageContainerOf` and backs
  every other site. Zero `@SuppressWarnings("deprecation")` anywhere; `ziggfreed-common`'s
  arc-touched files (`cast/CastKernel`/`StepSemantics`, `i18n/Msg`, `ui/hud/KeyedCustomHud`,
  `ui/rows/SummaryRow*`) and the MMO's `integration/stations`/`station` packages were audited via
  a `-Xlint:deprecation` compile and carried zero deprecated calls to begin with.

### Round-5: item-grant UX refinements (maintainer in-game, 2026-07-22)

Three grant-side UX refinements from the maintainer's in-game smoke session, with the generic
engine pieces lifted to `ziggfreed-common` per the root lift paradigm (this mod keeps only its own
policy):

- Adds a hotbar-first-if-space, then-backpack-storage GRANT ordering for every item this mod hands
  a player: placed-input custody retrieval/return, a per-cycle produced output, a luck bonus-copy
  grant, and a rare-find/tier `ItemDropList` grant all route through a new `util.ItemGrantUtil`
  seam, itself a thin policy wrapper (the drop-at-block fallback target only) over
  `ziggfreed-common`'s new generic `inventory.InventoryGrant` ordering primitive. Deliberately
  GRANT-side only - this mod's CONSUME side (the per-cycle Convert drain, held-tool reads) keeps
  preferring backpack storage over the hotbar for the historic client-camera reason documented on
  `ItemGrantUtil`'s own javadoc. `giveClaimToOwner` (custody give-back) is now PER-STACK instead of
  an all-or-nothing batch check, so a claim holding several distinct item ids can land some in the
  hotbar, some in the backpack, and only the genuine overflow on the ground.
- Adds native-pickup-mimic feedback to press-F custody retrieval: a retrieved stack now plays the
  SAME message + SFX + item-icon notification a genuine walk-over/block-harvest pickup does, via
  `ziggfreed-common`'s new `feedback.PickupMimic` primitive (which delegates straight to the
  engine's own pickup-notify method for byte-exact parity) - replacing the old generic "materials
  retrieved" toast.
- Adds live item-gain notifications while working: a produced material and a lucky drop (bonus
  copy or rare-find) each show WHAT was gained, with the item's own icon and name; a lucky drop
  renders in GOLD text, replacing the old generic "Lucky!"/"You find something extra!" toasts.
  New key `ui.station.gain.produced` (9 locales).

### Round-7: maintainer in-game smoke fixes (2026-07-23)

Fixes and additions from the maintainer's round-7 in-game smoke, scoped to this mod (D-1 the
placed-prop rotation, D-4 the item-gain toast copy, D-6 the enhancement session-summary + api
outcome; the sibling CustomSkill migration and XP-toast-stacking defects land MMO-side).

- Adds a nested `Custody.Display.Rotation` `{X, Y, Z}` degrees group (D-1), replacing the single
  scalar world-space yaw: the placed prop can now tip about all three axes (`X` pitch lays a placed
  weapon flat on an anvil, `Y` yaw turns it, `Z` roll tips it sideways), applied to the prop's
  `TransformComponent` on both spawn routes and mirrored onto `HeadRotation` for the item-entity
  route. The retired scalar form is tolerated on load - a stale bare-number `Rotation` decodes as
  the legacy Y-only yaw with a WARN naming the migration, never aborting the asset load.
- Adds an MMO-agnostic enhancement outcome to the session summary and the `api` (D-6): a Stamp step
  now records what it actually applied (the provider's own opaque per-stat report PLUS immutable
  before/after item snapshots) and reports it two ways - one `ENHANCE` summary row per stat
  (rendered verbatim, the provider owns the vocabulary/wording/color) plus one engine-owned
  `Durability +N` row (durability is RpgStations-native, so a bare anvil with no stamper still
  reports its enhancement) - and a new native `StationEnhanceCompletedEvent` carrying both reporting
  shapes for any future consumer, with zero MMO stat vocabulary entering this mod. The
  `EnhanceStamper.apply` contract now returns a `StampResult` (mutated stack + `EnhanceLine` report)
  instead of a bare stack (a pre-1.0.0 api reshape). New key `ui.station.summary.enhance_durability`.
- Fixes the live item-gain toast to read exactly like a native pickup (D-4): the produced/lucky
  toast value is now the bare item name, with the quantity riding the item-slot count badge (the
  same packet field a native pickup uses, routed through `ziggfreed-common`'s shared
  `feedback.Notify#itemKeyed`), instead of a leading `+N` in the text that froze stale when the
  client coalesced consecutive grants.

### Round-8: facing-relative custody display + step-synced puppet swings (2026-07-23)

- Adds facing-relative `Custody.Display` placement: a placed prop's authored `Offset`/`Rotation`
  are now relative to the placed station block's own facing yaw instead of absolute world axes.
  `StationCustodyDisplay` reads the block's non-deprecated `getBlockRotationIndex` yaw at spawn,
  rotates the horizontal `Offset` (X/Z) by it (authored `+Z` = toward the block's FRONT, `+X` = its
  right; `Y` stays vertical), and adds the block yaw into `Rotation.Y`, so a rotated station carries
  its display prop's position AND facing around with it. A default-orientation placement (yaw 0) is
  the identity, so every pre-round-8 authored value renders byte-identically (no pack re-tune
  needed); a failed block-facing read degrades gracefully to the prior world-space behavior and
  never aborts the spawn. New pure `resolveWorldOffset` plus extended `resolvePosition`/
  `resolveRotationRadians` take the block yaw as a plain scalar (unit-tested, all offset/rotation
  math still primitive-typed so it needs no live server).
- Adds step-synced puppet swings: a `StationStep` that authors its own `Puppet.Clip` now plays that
  clip once on the session's puppet the moment the step begins EXECUTING, at each step's ITERATION
  entry (`StationStepRegistry`'s guard, gated by the new pure `StationStepDecisions
  .shouldPlayClipOnEntry`, mirroring the generic per-step Presentation hook's once-per-entry,
  never-on-resume-recheck semantics - per-iteration-entry by construction, forward-compatible with
  the future step-repetition work). The generic engage/swing puppet clip is SUPPRESSED for a stepped
  program whose steps author any clip (`StationSession.stepProgramAuthorsClip`, resolved once at
  engage via `StationStepDecisions.programAuthorsAnyStepClip`) so the step-entry clips are the sole
  animation driver and never double-fire on top of a generic swing; a stepped program with NO step
  clips keeps its one generic engage swing, and the puppet prop-sync path is unaffected. The shipped
  anvil's Enhance ritual authors `MMO_Emote_Hammer` on its `strike1`/`strike2` steps so the puppet
  visibly hammers on both strike beats (content ships in `content-packs/skill-stations-pack`).
- Removes the temporary `[D77DIAG]` enhance-timing instrumentation after it proved the stepped-
  ritual timing correct: every `[D77DIAG]` `Log.info`/`Log.warn` line across `StationService`/
  `StationStepHandlers` plus the per-player resume-log throttle map is gone (same one-sweep-removable
  pattern as the retired `[SMOKEDIAG]` lines). The functional changes that landed alongside it stay:
  instant dispatch for a non-repeating authored Steps program (`Work.Repeat: false`, e.g. the anvil's
  Enhance, fires its first and only cycle immediately at engage instead of waiting a full
  `Work.CycleMs` - a ritual runs once, so the pre-delay was pure latency; a repeating program is
  unaffected), the explicit `dispatchProgram` `resuming` flag with fresh-dispatch `stepDeadlineMs`
  zeroing, and the generic per-step Presentation emission (any step's own authored `Presentation`
  plays once when it begins executing, not just the dedicated `Present` step's).
