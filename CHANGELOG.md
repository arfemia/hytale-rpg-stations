# Changelog

Developer changelog for RPG Stations. No em-dashes.

**0.1.0 is RPG Stations' first public release.** Everything below shipped into this one version;
there is no prior public release to diff against, so every entry is additive by definition.

## 0.1.0 (first public release)

A standalone, richly self-sufficient diegetic interactive work-station engine: with RPG Stations
alone installed, a station runs its full work loop (camera/hold/mount, tool gating, recipe
conversion, conditional-lootable rolls, command rewards) and needs no other mod. An add-on reaches
in through a soft extension surface (native events + typed registries, `api/`): it registers
factors a station's formulas read, and receives the contributions a station posts on each completed
cycle. Neither side hard-depends on the other. See `CLAUDE.md` for the full package-by-package
reference.

**Release scope: the engine is complete, the shipped default content is the Sawmill alone.** The
engine entries below all ship in full. What 0.1.0 deliberately does NOT ship is three finished
default stations held back for a later release: the two-station fish-prep exemplar (`CuttingBoard`
plus `CookingFire`, the multi-station claimed-anchor walk) and the `MountSpike` standing-mount
experiment, whose Entity-surface mount is still in-game unverified. They live, complete and
restorable in one command, under `unreleased/` (see `unreleased/README.md`); their lang keys stay
shipped in all 9 locales. The throwaway `/rpgstations npcspike` dev harness is unwired for the same
reason, with `NpcPerformerSpike.java` kept in git. Capabilities those stations demonstrated
(multi-station walks, the Entity mount surface, step programs) are engine features and remain fully
authorable by any pack.

**The `api` extension surface is NOT frozen at 0.1.0.** The freeze was always scoped to a 1.0.0
release; shipping 0.1.0 means the contract may still change before then. Integrators should expect
to recompile against a later release rather than treat these types as stable.

### Phase 1: extraction + the engine

- Adds the station engine itself: `StationAsset`/`LootableAsset`/`RpgStationsSettingsAsset` Pattern A codecs
  (native `Parent` inheritance, every leaf `appendInherited`), a per-player session state machine
  (`StationService`/`StationSession`), packet-camera third-person pull with a curated recipe
  vocabulary (`Camera.Recipe`, an admin-iterable preset switch for the free-camera-vs-locked-body
  hunt), an effect-mode movement lock, and native block-mount seating (`Hold.Mount` with
  `Surface: "Block"`) as the crowned answer for a held/facing worker.
- Adds tool gating (native `Tags`/`Gather`/`Ids` routes), and recipe derivation either authored
  (`Recipe.Conversions`) or derived from native crafting recipes (`Recipe.FromCrafting`), zero
  hand-authored conversions for a station like the Sawmill.
- Adds `Recipe.Yield`, the per-cycle output-quantity transform that applies to authored and derived
  conversions alike and is purely DETERMINISTIC: a flat `Base`, a `Scale` multiplier, and `Min`/`Max`
  clamps, resolved per cycle over a 1-item floor so a conversion can never eat its inputs and produce
  nothing. Reading the group tells an author exactly how much one cycle makes; everything conditional
  or probabilistic about output is a `Roll` in the action's `Bonus` group instead.
- Adds three tool-describing built-in factors so a "better tools yield more" curve is authorable
  with no code: `hytale:tool_quality` (the native `ItemQuality.QualityValue`) and
  `hytale:tool_item_level` (the native `ItemLevel`) beside
  `hytale:tool_power`. Summing all three is the intended shape, because no two of them can rank
  a full tool family alone: gather power saturates across the upper tiers, quality cannot separate
  tools that share a tier, and item level does not track rarity at all. The shipped Sawmill uses
  exactly that curve to pay for its milling time, running from one plank per log on a starter
  hatchet up to five with the station's own drop-only trophy hatchet (four on the best forgeable
  one). See the shipped-content section below for the whole curve.
- A factor's NAMESPACE names the vocabulary's owner rather than whoever registered the provider.
  A straight native read is `hytale:` and therefore portable, meaning the same thing to any mod that
  reads native data: `hytale:tool_power` (an `ItemToolSpec` power, its native `GatherType` given as
  the `Param` so the addressing is explicit, defaulting to the station's own when omitted),
  `hytale:tool_quality`, `hytale:tool_item_level`, `hytale:tool_durability_percent`, and
  `hytale:stat` for any registered `EntityStatType`. Two mods converging on one of those ids is
  agreement rather than a collision, and an author can tell from an id alone whether a factor
  travels. `rpgstations:` is reserved for vocabulary this engine actually owns, which is exactly
  `session_seconds` and `cycle_count` - they exist only because a station session does.
- Ships the standalone default Sawmill (native ids, jar-shipped) alongside the standalone
  `loot/` layer: conditional lootable rolls over native `ItemDropList`s gated/weighted by an
  extensible condition system (session length, tool durability/power, and similar session-derived
  factors, via a `FactorRegistry` other mods can extend), plus command rewards, so any third party
  integrates with zero code.
- Adds validation (`StationValidator`, warn-only, never blocks), reporting in ziggfreed-common's
  shared finding vocabulary (`com.ziggfreed.common.validation.{Finding, Severity, ValidationReport}`)
  so a validation hook another mod registers speaks the same record this engine does, and a
  session-summary panel (`ui/StationSummaryHud`) showing cycles and items consumed/produced, plus whatever extra rows a
  listening mod adds via a registered `SummaryEnricher`.
- Adds the `api` extension-surface artifact (still unfrozen at 0.1.0; the freeze lands at 1.0.0): native Hytale events for
  observe-only moments (session started/cycle completed/session completed/tool broke) and typed
  registries for request/response points (`FactorRegistry`, `ContributionChannelRegistry`,
  `FlairUnlockRegistry`, `SummaryEnricherRegistry`, `ValidationHookRegistry`), the mechanism an
  add-on consumes to reach back without either mod manifest-depending on the other.
- Adds `/rpgstations camera <preset>|list` (tune the camera-recipe preset) and `/rpgstations
  validate` (run the station content validator), admin-gated.
- Ships 9-locale `rpgstations.lang` (all UI/command strings key-complete across every shipped
  locale from the start).

### Phase 2: multi-action stations, placed-input custody, the Mount family, the anvil arc

- Adds multi-action stations: a station's ORDERED `StationAsset.Actions[]` array lets one station
  block host several distinct, fully self-contained actions (each carrying its own Work/Tool/Recipe/
  Custody/Requires/Worker groups, nothing inherited from the station), diegetically selected by what
  the player is holding or has placed - the first entry whose `Select` matches the context wins. An
  action is a STEP PROGRAM (`StationStep`) run through one production step-dispatch kernel
  (`StationStepKernel`, built on the lifted `ziggfreed-common` `cast.step` kernel); an action that
  authors no `Steps` gets the classic convert loop as an implicit program built from its own
  `Recipe`, so the shipped Sawmill authors none.
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
- Adds the `Hold.Mount` knob family: `Surface: "Block"` (the native seat mount, the default arm on
  an authored group) or `Surface: "Entity"` (a standing work mount for a
  station that wants its worker on their feet, with a steerable/dismount-on-move knob pair).
- Adds the open flair/moment vocabulary: a moment is an open string id (the well-known
  cycle/swing/impact/rare_find/completion constants plus a per-step `step:<actionId>:<stepId>` id
  any step's own `Presentation` resolves against), and a standalone `FlairAsset` Pattern A type lets
  ANY installed mod or pack ship a cosmetic flair layer for a station without touching that
  station's own JSON.
- Adds the anvil arc's `Stamp` phase: a composable roll+cap engine for rolling stat entries onto a
  placed item (`RollPool` Pattern A store, a shared `StatRollEntry` codec, weighted-pick/unique
  selection, and a composable cap model - weighted `Budgets` entries, per-stat caps, and repeat-cost
  economics, all independently authorable) plus a registered `EnhanceStamper` api contract
  (`inspect`/`apply`) a mod implements to read/write its own item-enhancement format. Compute-then-commit:
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
- Fixes a false runnability validator ERROR on a multi-action station whose actions each supply
  their OWN recipe or step program (the anvil's `enhance` action runs entirely off a `Stamp`
  ritual, no `Recipe` at all): the check is action-aware, erroring only when a station authors no
  actions at all (`STATION_NO_ACTIONS`) and warning per action that authors neither `Ref`, `Recipe`,
  nor `Steps` (`ACTION_NO_BODY`).
- Fixes validation-ordering false positives (`STAMP_UNKNOWN_POOL`/`LOOT_UNKNOWN_DROPLIST`/
  `MISSING_*_LANG`): the per-fold validator ran before a LATER asset layer (RollPool/Drops/lang)
  had folded the very reference it was checking. `StationValidator` now runs two passes: a
  structural-only pass at every fold (`validateStructural`/`runStructuralAndLog`, safe regardless
  of load order), and the FULL pass (incl. every cross-layer reference-existence check) ONCE,
  post-load, from a new first-`PlayerReadyEvent` hook (`RpgStationsPlugin.registerPostLoadAudit`)
  - `/rpgstations validate` (already
  post-load) is unaffected. The lang-key check itself is now a MERGED view: a miss against the
  jar's own hand-maintained key set falls through to a live `I18nModule.getMessage` query, so a
  pack's own additive `rpgstations.lang` overlay resolves correctly.

See `station/CLAUDE.md`'s Validation bullet for the full detail; the sibling pack fixes (the
anvil's redundant `Camera.FaceBlock`, a missing reagent `ResourceType` asset) and the consumer-side
bridge presence-check hardening live in their own repos' history.

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
  The player-accessor sites (`storage`/`hotbar`/`activeHotbarItem`/`combinedBackpackStorageHotbar`/
  `playerRef`) read through `ziggfreed-common`'s `inventory.PlayerAccess` (the shared ref/store
  guard + component fetch, so the replacement for a deprecated accessor has ONE shared home across
  this mod and its siblings rather than a copy per mod), which also backs the shared
  `InventoryGrant` delivery path behind `util/ItemGrantUtil`; the sweep's other replacements (the
  component fetches named above at sites that never went through the deleted accessor class) stay
  inline where they were. The one unwrap to the raw
  Storage container stays a single private helper (`station/StationStepHandlers#storageContainer`),
  so the reagent probe and drain paths never repeat it. Zero `@SuppressWarnings("deprecation")` anywhere; `ziggfreed-common`'s
  arc-touched files (`cast/CastKernel`/`StepSemantics`, `i18n/Msg`, `ui/hud/KeyedCustomHud`,
  `ui/rows/SummaryRow*`) were audited via a `-Xlint:deprecation` compile and carried zero
  deprecated calls to begin with.

### Round-5: item-grant UX refinements (maintainer in-game, 2026-07-22)

Three grant-side UX refinements from the maintainer's in-game smoke session, with the generic
engine pieces lifted to `ziggfreed-common` per the root lift paradigm (this mod keeps only its own
policy):

- Adds a hotbar-first-if-space, then-backpack-storage GRANT ordering for every item this mod hands
  a player: placed-input custody retrieval/return, a per-cycle produced output, a bonus output-item
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
- Adds live item-gain notifications while working: a produced material and a lucky drop (a bonus
  output item or a rare find) each show WHAT was gained, with the item's own icon and name; a lucky
  drop renders in GOLD text, replacing the old generic "Lucky!"/"You find something extra!" toasts.
  New key `ui.station.gain.produced` (9 locales).

### Round-7: maintainer in-game smoke fixes (2026-07-23)

Fixes and additions from the maintainer's round-7 in-game smoke, scoped to this mod (D-1 the
placed-prop rotation, D-4 the item-gain toast copy, D-6 the enhancement session-summary + api
outcome; the sibling toast-stacking defects land in the consumer mod's own repo).

- Adds a nested `Custody.Display.Rotation` `{Yaw, Pitch, Roll}` degrees group (D-1), replacing the
  single scalar world-space yaw: the placed prop can now tip about all three axes (`Pitch` lays a
  placed weapon flat on an anvil, `Yaw` turns it, `Roll` tips it sideways), applied to the prop's
  `TransformComponent` on both spawn routes and mirrored onto `HeadRotation` for the item-entity
  route. The retired scalar form is tolerated on load - a stale bare-number `Rotation` decodes as
  the legacy Y-only yaw with a WARN naming the migration, never aborting the asset load.
- Adds a vocabulary-agnostic enhancement outcome to the session summary and the `api` (D-6): a Stamp step
  now records what it actually applied (the provider's own opaque per-stat report PLUS immutable
  before/after item snapshots) and reports it two ways - one `ENHANCE` summary row per stat
  (rendered verbatim, the provider owns the vocabulary/wording/color) plus one engine-owned
  `Durability +N` row (durability is RpgStations-native, so a bare anvil with no stamper still
  reports its enhancement) - and a new native `StationEnhanceCompletedEvent` carrying both reporting
  shapes for any future consumer, with zero foreign stat vocabulary entering this mod. The
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
  right; `Y` stays vertical), and adds the block yaw into `Rotation.Yaw`, so a rotated station carries
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
  anvil's Enhance ritual authors a hammer clip on its `strike1`/`strike2` steps so the puppet
  visibly hammers on both strike beats (that content ships in its own pack, not in this jar).
- Removes the temporary `[D77DIAG]` enhance-timing instrumentation after it proved the stepped-
  ritual timing correct: every `[D77DIAG]` `Log.info`/`Log.warn` line across `StationService`/
  `StationStepHandlers` plus the per-player resume-log throttle map is gone (same one-sweep-removable
  pattern as the retired `[SMOKEDIAG]` lines). The functional changes that landed alongside it stay:
  instant dispatch for a non-repeating authored Steps program (`Work.Looping: false`, e.g. the anvil's
  Enhance, fires its first and only cycle immediately at engage instead of waiting a full
  `Work.CycleMs` - a ritual runs once, so the pre-delay was pure latency; a repeating program is
  unaffected), the explicit `dispatchProgram` `resuming` flag with fresh-dispatch `stepDeadlineMs`
  zeroing, and the generic per-step Presentation emission (any step's own authored `Presentation`
  plays once when it begins executing, not only a step whose whole job is the cue).

### Round-8b: Stamp reagents in the session-summary consumed ledger (2026-07-23)

- Fixes the anvil Enhance summary omitting a consumed row for the reagents the ritual ate: the
  `Stamp` step drains its reagents (the sharpened bars) directly through `consumeReagent`, which only
  built a restore-on-failure list and never recorded into the session's `consumedItems` ledger, so
  the summary showed the enhancement stat and durability rows but NO consumed row. The
  `StampHandler` now tallies its committed reagents into the SAME `s.consumedItems` ledger the
  implicit-program `Consume` step feeds (recorded only after `claim.setUniqueStack`, the commit's
  point of no return, so a restore-on-failure refund is never counted as consumed), and
  `ledgerRows` renders one `SummaryRow.Kind.CONSUMED` row per input stack (e.g. the 2 sharpened
  bars) through the existing pipeline - zero HUD change. The consume tally now has one authority: a
  shared pure `StationService.mergeConsumedSlots` fold backs both the `ResourceTypeId` family route
  (`tallyConsumedResource`, with its raw-type fallback) and the new Stamp reagent route
  (`tallyConsumedStacks`). Produce was already tallied by `ProduceHandler`; no gap there.

### Scope 2: from-scratch authoring surface, unified factor vocabulary, multi-station seam (2026-07-24)

- Adds the orthogonal-phase `StationStep`: one step record composes any combination of nullable
  `Walk`/`Consume`/`Stamp`/`Produce`/`Roll`/`Commands` phases in one fixed order, so a single step
  carries several effects at once instead of needing one step per effect, and a phase-free step with
  a `Duration` is a pure timed beat.
- Adds a unified `LootRef` (`{Lootables[], Rolls[]}`) that an action's own `Bonus` group and a
  step's `Roll` phase both share, and expresses the Stamp phase's stat-roll caps as a weighted
  `FactorRef` budget vocabulary (`Budgets[]`) that also drives loot chances and roll magnitudes -
  one factor vocabulary composes everywhere a number needs to scale off tool power, a native stat,
  or any other registered signal.
- Adds `ActionAsset` (`Server/RpgStations/Actions/*.json`): a station's `Actions[]` entry can
  `Ref` a reusable, independently authored action instead of always inlining one, so several
  stations can share one job definition.
- Adds `ExtensionAsset` (`Server/RpgStations/Extensions/*.json`): the one additive extension
  mechanism a fourth-party pack uses to append a loot reference, an extra ritual step, or a
  contribution on a new channel onto another pack's station, action, lootable, or roll pool,
  without owning or replacing that pack's original file (base always wins a key collision).
  An Action target may optionally be scoped to one station (`Target: {Station, Action}`) for
  the case where an inline action id is not unique across installed stations; a bare Action
  target follows a shared `ActionAsset` wherever stations `Ref` it.
- Adds the multi-station seam: a step program can author `Walk`/`At` to reach out to a second,
  separately-placed station nearby and `Produce.To: "Custody"` to deposit its output straight into
  that station's placed-input slot instead of a player's backpack. `ActionDef.Anchors` discovers and
  claims the nearby station, a refund ledger returns any in-flight materials if the walk is
  interrupted, and a walk timeout clears a stuck walking state. The fish-preparation exemplar built
  on this seam (a cutting board that walks a character to a nearby fire and back to finish the job,
  all from one `F` press on the primary block) is complete but HELD BACK from 0.1.0 under
  `unreleased/`; the seam itself ships and any pack can author against it.

### Scope 3: native composition, performer contract, and the sneak+F recipe picker (2026-07-24 to 2026-07-29)

- Adds native composition throughout the engine: steps and actions reference native Hytale
  interactions, effects, and drop lists by id instead of re-describing their behavior, recipes
  derive from native crafting recipes with an authorable `{Scale, OffsetMs}` pacing transform, and
  tool gates read native `Tags.Family`. Recipe pacing resolves with explicit precedence, and an
  effect chain tears down cleanly on an interrupted stop.
- Adds the `StationPerformer` contract: the clone-puppet route is one implementation of
  a common performer abstraction, alongside a bare-Holder performer and a full NPC-role performer
  (`Look.Source: PlayerClone|Model|NpcRole`). Swapping performer backends lands byte-parity with the
  original puppet route, including a walking NPC-role performer routed through the same contract as
  the multi-station walk.
- Adds the sneak+F recipe picker: sneaking and pressing `F` at a station that offers more than one
  output category opens a picker previewing whatever material is currently placed in the block,
  defaulting to the first-authored category. This supersedes the earlier native-bench-window
  prototype for multi-category selection (retired), and ships a picker restyle plus a species/output
  preview.
- Adds honest custody denial: a station correctly refuses (rather than silently no-opping) a
  press-F custody load when the held item does not match what that station's current action
  accepts, and a station-wide collect gesture returns every placed input across every claimed
  station in one interaction.
- Adds a fire performer variant and station-side idle/working animation states for the puppet at a
  fire-backed station (the fish exemplar's remote leg).
- Fixes facing-relative puppet placement and prop syncing, an extension pack's `Puppet`/`Custody`
  overlays layering correctly onto a base station, and sawmill presentation parity after the step
  reshape (three maintainer smoke-fix rounds, decisions 57 through 67 in the design log).

### Presentation + moment vocabulary pass

One moment vocabulary, one scheduler, and cues authored where a reader looks for them. The schema
is pre-release, so each change below is a hard break with no alias.

- Adds per-sound timing. A `Presentation.Sounds` entry is now EITHER a bare `SoundEvent` id (the
  shorthand every existing file already uses, byte-unchanged) OR `{EventId, DelayMs}`, decided per
  entry, so a moment can stagger a thud and a chime without splitting into two moments. A
  shorthand entry re-encodes as a bare string, so nothing inflates on a round trip. The per-sound
  `DelayMs` ADDS to the moment's own: the moment delay offsets the moment, the entry delay offsets
  that sound inside it, and both land on the same one-tick-resolution playback queue. `Volume` and
  `Pitch` stay deliberately unauthorable - the engine's positional one-shot call takes neither, so
  either leaf would decode and then do nothing; vary them by referencing a different `SoundEvent`.
- **An action's `Moments` is an OPEN map keyed by moment id**, replacing the fixed
  `{Cycle, Completion}` pair - the same open vocabulary and the same shape a `FlairAsset` already
  keys its own `Moments` by, so a cue reads the same whether an action authored it or a flair
  overlaid it. `Cycle` and `Completion` keep working verbatim (matching is case-insensitive), and
  `swing`/`impact`/`step:<actionId>:<stepId>` are authorable beside them. Native
  `Parent` merges the map per KEY and per leaf under it, so a child re-skinning one moment inherits
  every other. **Specificity wins**: an entry is the base for its moment id wherever the engine has
  nothing more specific, and a step's own `Presentation` (or a loot floor's cue) outranks it for
  that emission. An unrecognized key is the same warn-only typo finding a flair map gets.
  `rare_find` is the one well-known id an action does NOT author, since that moment only ever fires
  with the earning `Roll`/`Ladder.Floor` cue already in hand: author it there, and the new warn-only
  `RARE_FIND_MOMENT_NEVER_PLAYS` finding catches a map entry that could never play. An action's
  `Moments` entry drives a STEP's cue only for a `step:<actionId>:<stepId>` id, so an unnamed step
  never replays the action-wide `cycle` cue per beat. The implicit convert loop's per-cycle cue plays
  under `cycle` itself, so a flair re-skins the classic work loop by the id the docs name for it.
- **A swing's cues moved out of `Worker.Animation` and into `Moments`.** `Animation.Swing` is now
  pure cadence (`IntervalMs`); the swing cue is the `swing` moment and the strike landing behind it
  is the `impact` moment, late purely because it authors the generic `Presentation.DelayMs`. That
  removes the engine's one piece of dedicated single-cue scheduling machinery: every offset in the
  mod now rides one queue and one due-time core. `impact` stays a distinct flair-targetable moment
  id, so a flair can still re-skin or re-time the strike independently of the swing.
- **`Puppet.Yaw` is `Puppet.Rotation`**, the shared `{Yaw, Pitch, Roll}` degrees group already used
  by `Custody.Display` and particle bursts. `Rotation.Yaw` folds with the placed block's facing
  exactly as the scalar leaf did (identity at yaw 0); `Pitch` and `Roll` are the puppet's own tilt
  and are not composed with the block, the same rule a particle burst's `RotationOffset` follows.
  An `ExtensionAsset`'s `Puppet` overlay merges the group per axis, so a pack authoring only
  `Rotation.Pitch` keeps the base's `Yaw` and `Roll`.
- **Every moment and flair map merges per KEY under native `Parent`.** `StationAsset.Flairs` (keyed
  by flair id), a station's inline `Flairs[].Moments`, and `FlairAsset.Moments` (both keyed by
  moment id) decode through the same `InheritMapCodec` an action's own `Moments` uses, so a child
  station restyling one flair inherits every other flair the base authored, and a child flair
  re-skinning one moment inherits the rest. Inline and standalone flair content stays ONE shape,
  merge behaviour included. Those maps also accept an inline `$Comment` (or any `$`-key) directly
  among their entries, the same editorial keys a structured group has always taken. The map leaves
  that still read EVERY key as a map key are `Anchors`, `Stamp.Stats.Caps.PerStat`, and every `Tags`
  leaf; put a note there on one of the map's values or on the enclosing object instead.
- Adds the warn-only `PUPPET_NPC_ROLE_ROLL_DROPPED` validator finding: an action pairing
  `Puppet.Look.Source: "NpcRole"` with a non-zero `Puppet.Rotation.Roll`. An NPC keeps its pose from
  a leash that carries heading and pitch only, so the bank is dropped and the puppet stands level
  about that axis. `Yaw` and `Pitch` both still apply; author `Look.Source "PlayerClone"` or
  `"Model"` when the roll matters.

### Pre-release schema + authoring pass (2026-08-05)

Multi-item recipes, tunable particle bursts, ref-or-inline authoring, shared spatial leaves, a
full field-documentation sweep, and in-game Asset Editor support across every content type. The
schema is pre-release, so the renames below are hard breaks with no aliases.

- Adds MULTI-ITEM recipes and step phases. `Recipe.Conversions[].Input`/`Output` and
  `StationStep.Consume`/`Produce` all take an `Ingredient` ARRAY (`Consume`/`Produce` under an
  `Items` key, keeping their `From`/`To` route at group level), mirroring native
  `CraftingRecipe.Input`/`Output` - so "2 planks + 1 nail -> 1 crate" is one conversion and one
  atomic step-phase pair rather than a step split, and a recipe yielding a main product plus a
  byproduct authors directly. Both sides are all-or-nothing: a cycle needs every input available
  and room for every output before it starts, and a step phase checks the whole list before
  removing anything. `Recipe.FromCrafting` derives multi-input native recipes too, instead of
  skipping any recipe without exactly one input.
- Adds tunable particle bursts. A `Presentation.Particles` entry is a `ModelParticle`-shaped group
  (`SystemId` plus optional `Scale`, `DurationSeconds`, `RotationOffset` in degrees, and a
  facing-relative `PositionOffset`) and the leaf is an ARRAY, matching native
  `InteractionEffects.Particles`, so a moment can layer bursts. Unauthored knobs land on plain
  playback (scale 1, a 4-second client-playback cap, no rotation or offset); the
  duration cap is authorable per burst but stays a leak guard against unbounded-spawner systems.
- Adds `Presentation.DelayMs`, a per-moment playback offset on the shared presentation type, so
  every site that authors a `Presentation` can land its cues on the beat they belong to rather than
  the instant the engine reached them: any of an action's own `Moments` entries, a step's own
  `Presentation`, a `Roll`'s or a `Ladder.Floor`'s, and a `FlairAsset` moment. It offsets the
  whole group as one cue, is applied after the flair fold (so a flair can re-time a moment as well
  as re-skin it), and resolves on the ONE due-time core every offset in the engine shares.
  Null, zero, or a negative value plays at once. A delayed cue survives the end of the run that
  earned it (a completed ritual's final cues still play), while an interrupted session falls silent.
  The jar's Sawmill authors `DelayMs: 100` on its cycle moment and `140` on its impact moment.
- Adds three warn-only validator checks. `CYCLE_DELAY_OVERLAPS_NEXT_CYCLE`,
  `STEP_DELAY_OVERLAPS_ITS_DURATION` and `IMPACT_OVERLAPS_NEXT_SWING` catch a
  `Presentation.DelayMs` held longer than the window it plays inside (a repeating action's own
  `Work.CycleMs`, a step's authored `Duration.Ms`, or one `Animation.Swing.IntervalMs`). `LOOT_DROPLIST_NEVER_RESOLVES` rolls every
  referenced `ItemDropList` a few times during the full validate pass and reports a table that pays
  out nothing every time - the shape a container tree of pure `Droplist` references takes at runtime,
  which an existence check alone can never see.
- Adds ref-or-inline authoring on the three leaves that reference one of this mod's own asset
  types: `LootRef.Lootables[]`, `StationStep.Stamp.Stats.Pool`, and `ActionDef.Ref` each accept an
  inline anonymous body (optionally with its own `Parent`) in place of an id, through the engine's
  own contained-asset codec, and each emits a typed cross-reference into the generated schema
  reference instead of an untyped string. References to NATIVE assets stay id-only.
- Adds four authoring knobs: `Roll.Grants.Contributions[]` (one-shot amounts posted on a
  conditional-lootable find, forwarded on their own unscaled list so a find is worth the same
  whatever tool the worker holds, and restricted to a `Cycle` trigger),
  `Tool.Durability.MinStartPercent` (refuse to start work with a tool worn below a threshold; a
  session already running still ends at
  breakage, not at the threshold), `Custody.SingleFamily` (lock a claim to the first-placed item's
  resource family, so a station holds 50 oak or 50 pine but never 100 mixed), and
  `SummaryHud.OffsetX` beside its `OffsetY` sibling.
- Renames the two keys that were spelled the same at two altitudes with two different types.
  `Work.Repeat` (a boolean) becomes `Work.Looping`, freeing `Repeat` for the iteration COUNT it
  means natively and one level down on `StationStep.Repeat`; `StationStep.Working` (a boolean)
  becomes `IsWork`, matching the native `Is*` boolean idiom and separating it from the
  `Custody.States.Working` block-state name. Pre-release renames with no alias: an authored file
  using an old key loses that leaf silently, so re-spell both when upgrading a draft pack.
- Collapses the duplicated spatial and tag leaves onto three shared types. One `Vec3` `{X, Y, Z}`
  replaces the four separately-declared offset codecs (`Custody.Display.Offset`, `Puppet.Offset`,
  `Hold.Mount.Entity.Offset`); one `Rotation` `{Yaw, Pitch, Roll}` in degrees carries every
  rotation leaf, so both the puppet's scalar `Yaw` and a placed display's rotation spell the
  vertical axis the same way and `{X, Y, Z}` means position everywhere; one `TagMatch` map backs
  both `Tool.Tags` and `ActionInput.Tags` behind a single matcher. Every axis stays independently
  nullable, so a partial `"Offset": {"Y": -0.1}` keeps overlaying as before. `Anchors.*.MaxRadius`
  is spelled `MaxRadiusMeters`, naming its unit.
- Moves `Puppet.Hide.EffectId` onto the shared `EffectRef` group as `Hide.Effect` (`{Id,
  DurationMs?}`), finishing the effect-reference consolidation. Two effect leaves deliberately stay
  bare ids and say so in their own docs: `Hold.EffectId` (the movement hold's lifetime is
  engine-owned, re-applied per heartbeat, so an authored duration would be inert or would defeat
  the release safety net) and `Presentation.Shake.EffectId` (a camera effect whose duration lives
  inside the referenced asset with no per-use override on the engine's fire-and-forget path).
- Documents every codec leaf. Every authorable leaf across the seven content types carries a
  description of what it does and what it defaults to, and a coverage test fails the build on a
  blank one, so the generated schema reference and the in-game Asset Editor both show real help
  text on every field.
- Adds in-game Asset Editor support to the content types: collapsible section headings over each
  top-level group, pick lists on the value vocabularies (this mod's live station / action /
  lootable / roll-pool / factor ids, plus every closed union discriminator such as mount surface,
  camera preset, puppet hide route, and consume/produce route), localization-key fields on
  `Identity.NameKey`/`DescKey` and an action `Label`, and an icon picker on `Identity.Icon`. The
  content validator remains the authority: it backs every one of these for hand-written JSON, and
  map-KEY vocabularies (flair moment ids, per-stat cap keys, tag families) are validator-only by
  design.
- Adds field-level warnings at decode time. Quantities, cycle times, budgets and required ids that
  are authored out of range report a warning as the asset loads, and the exactly-one-of contracts
  (an ingredient's item route, an extension's target, a stamp budget's route) report when more than
  one arm is authored. Every one of these WARNS: an asset always loads, matching this mod's
  never-block posture, and none of them can drop content. Three new validator checks land beside
  them: a duplicate `(Channel, Param)` contribution between an extension and the station or action
  it targets (or between two extensions targeting the same thing, which sum rather than override), a
  `Tool.Durability.MinStartPercent` authored outside `(0, 100]`, and a redundant `Custody.SingleFamily`
  on a claim whose capacity already holds one item.
- Adds `RpgStationsApi.apiVersion()` plus non-throwing `isAvailable()`/`find()` accessors, and
  writes down the additive growth policy the surface follows after 1.0.0 (default-bodied interface
  methods, new event classes, additive event getters; no signature changes). The two accessors
  carry an explicit caveat in their own javadoc: they do NOT replace the `PluginManager` presence
  check a consumer runs first, because these api types are classloader-unresolvable exactly when
  RpgStations is absent. `api/CLAUDE.md` carries the copy-pasteable two-step consumer idiom.

### The extension vocabulary: one shape, two directions (2026-08-05)

- Adds `Contribution` (`{Channel, Param?, Amount}`), the ONE outbound numeric-post leaf, and its
  api record `StationContribution`. A station authors amounts against a namespaced channel id this
  engine never resolves: it forwards `{Channel, Param, Amount}` verbatim on
  `StationCycleCompletedEvent` and leaves interpretation entirely to whichever mod owns the
  channel. This is the exact mirror of the read side (`FactorRef`/`Condition` + `FactorRegistry`),
  applied in reverse, so an author learns one convention and uses it in both directions.
- Adds `ContributionChannelRegistry` (reached via `RpgStationsApi.channels()`) and its concrete
  `declare(channelId)` implementation. Declaration only, because there is nothing to resolve. A
  declared id feeds the LIVE `rpgstations:channels` Asset Editor dropdown and the fail-open
  `UNKNOWN_CHANNEL` validator warning; an undeclared channel still forwards, so a warning never
  blocks content. The engine ships zero built-in channels by design: it owns built-in FACTORS
  because it can compute them, and owns no channels because it interprets none.
- Adds `ValidationHookRegistry` (`ValidationHook`/`ValidationScope`/`RollView`/`FactorRefView`/
  `FindingSink`): third-party content checks that run inside this engine's own full validate pass,
  so a mod owning a factor family or a channel keeps its composition rules with the vocabulary
  rather than hardcoding them here. Hooks see both the reference structure and the formula numbers,
  report info/warn findings only, are try-guarded, and never block.
- Adds `LOOT_DUPLICATE_FACTOR` (INFO): the same `(Factor, Param)` pair referenced more than once
  inside one Roll, across its `Conditions`, `Chance.Factors`, and `Ladder.Factors`. Two `stat`
  references with different `Param`s are a legitimate composition and never fire it.
- Names the authoring sites for what they mean, so the two scaling rules are visible in the JSON
  rather than only in the engine: `Work.PerCycleContributions[]` is posted every completed cycle,
  multiplied by the action's resolved `ContributionScale` and pre-scaled by `Work.Idle.Fraction` on
  an idle cycle; `Roll.Grants.Contributions[]` is posted once and verbatim, inheriting neither. Same
  record type, different documented semantics per owning group, no mode flag on the entry. The event
  carries them as two lists, `contributions()` and `oneShotContributions()`, and the multiplier it
  reports as `contributionScale()` applies to the first only.
- Names the remaining scaling knob for the mechanism instead of a consumer's reward type:
  `Work.Idle.Fraction` (the fraction of a normal cycle's amounts an idle practice cycle posts).
  The matching validator ids are `MISSING_CONTRIBUTION_CHANNEL`, `NONPOSITIVE_CONTRIBUTION_AMOUNT`,
  `EXTENSION_CONTRIBUTION_DUPLICATE`, and `LOOT_CONTRIBUTION_{WRONG_TRIGGER,MISSING_CHANNEL,
  NONPOSITIVE_AMOUNT}`.
- Adds `MmoAgnosticismTest`, which scans `src/main/java`, `api/src/main/java`, `src/main/resources`,
  and the in-repo `docs/` guides for foreign progression vocabulary and fails the build on any hit.
  The docs source is scanned because it is the public authoring surface, and a tutorial teaching the
  foreign vocabulary is the worst leak of all; exactly ONE line is allowlisted, the Add-ons &
  Integrations page naming the companion mod it links out to. `CHANGELOG.md`, `CURSEFORGE.md`, and
  the in-repo package routers stay out of scope structurally rather than for convenience: they are
  the surfaces that STATE this rule and narrate its history, so they have to be able to quote the
  retired vocabulary while explaining why it is retired. A convenient comment is exactly how a
  vocabulary creeps back in.

### Pre-release schema sweep (the last authoring-surface pass before 0.1.0)

The whole authoring surface was reviewed once more while a rename or removal was still free (the
`api` freezes at 1.0.0, content schema had no back-compat obligation yet, and an unrecognized key only
ever produces a boot-log `WARNING: Unused key(s)` line). Everything below is part of the 0.1.0 schema
as shipped, not a change to something previously released.

- **A station is an ORDERED LIST OF SELF-CONTAINED ACTIONS, and station-level group inheritance is
  DELETED.** `StationAsset` keeps only `Identity`/`Block`/`Requires`/`Flairs`/`Actions[]`; every
  other group (`Work`, `Recipe`, `Tool`, `Custody`, the `Worker` presentation groups
  `Hold`/`Camera`/`Animation`/`Puppet`, and the `Moments` cue pair) lives
  EXCLUSIVELY on an `ActionDef` entry, with no station-level default left to fall back to. The
  four worker-presentation groups nest under one `Worker` group (how the person looks doing this)
  and the two cue presentations under one `Moments` group (`Cycle`/`Completion` - what it sounds
  and looks like, at two times), so an action reads as roughly eight concerns rather than fourteen
  flat siblings. Two
  actions that used to share a station-level default now share by REFERENCE - both `Ref` the same
  standalone `ActionAsset`, or both name the same native `Parent` between `ActionAsset`s - never by
  implicit fallback. Selection stays the station's `Actions[]` AUTHORED ORDER (the first action
  whose `Select` matches the held/placed context wins); the station's own `Requires` ANDs with the
  resolved action's own, neither defaulting the other.
- **`Recipe` is singular: an action authors AT MOST ONE, gated by that action's own `Tool`.** The
  `Recipes[]` tried-in-order list (and its pure selection core, `station.RecipeSelection`) is gone:
  "which tool" and "which transform" are answered by the same group a reader is already looking
  at, so there is nothing left to try in order. Two variants that used to be two `Recipes[]`
  entries sharing one action are now two `ActionDef`s instead - the diegetic `Select` match already
  IS the "try this, else that" chain, one level up.
- **`Recipe.Yield` is now PURELY deterministic - `Base`/`Scale`/`Min`/`Max`, nothing else - and ALL
  probabilistic output moved to the loot layer.** `Roll.Grants.BonusOutputCopies` is deleted
  outright (it granted N copies of the WHOLE produced stack, so a station whose yield already paid
  4 planks silently handed out 4 more for a leaf reading "+1", with the two numbers living in
  different files under different concept names); its replacement, `Roll.Grants.OutputItems` (a
  `Double`), is ADDITIVE - extra units of the cycle's own primary output, directly comparable to
  `Yield`'s own number because both count the same item. The amount is FRACTIONAL: the whole part is
  granted every time and the leftover fraction is the chance of one more, so `1.5` pays one item
  always plus a second half the time and averages exactly 1.5. Everything a cycle grants is summed
  before one resolution, so two rolls paying `0.5` each average a whole item rather than rounding
  twice. That is what lets a half-step tool tier be authored on the ladder floor that earns it (the
  sawmill's Iron rung), instead of a roll banded to one quality tier beside the ladder, which cannot
  compose with the rungs above it. A `Roll` in an action's own `Bonus` decides
  every bit of "sometimes you get extra", with the full `Roll` vocabulary (`Trigger`, `Conditions`,
  `Chance`, `Ladder`) available for it; `Yield` decides only "how much of the thing you made,
  guaranteed".
- **A `Roll` carries its own top-level `Presentation`, and a celebration never plays over nothing.**
  A plain chance roll can hang its own cue directly on the roll, beside the `Ladder.Floor`
  `Presentation` that already celebrated a reached tier, so a roll with no tiers to climb needs no
  degenerate one-floor ladder standing in for a cue (a shape the validator flagged as unreachable
  besides). **The smart-cue rule binds both altitudes**: each cue is paired with the `Grants` group
  authored BESIDE it (the roll's own top-level `Grants` for the roll-level cue, the floor's own for
  a floor cue). With no grants beside it a cue is pure presentation and plays on the plain hit or
  reach; with grants authored it plays only once applying them actually PRODUCED something - an item
  a referenced drop table's own internal weights really handed over, a command run, an effect
  applied, an `OutputItems` amount tallied, or a contribution posted. That matters because a
  `DropLists` entry names a native table carrying its own internal empty weight, so "the floor was
  reached" and "the player got something" are genuinely different facts, and a jackpot fanfare over
  an empty hand is the outcome the rule exists to prevent. The two altitudes are judged
  independently and both can play in one pass. Enforced engine-side (only the engine knows what a
  grant produced, which is why applying a grants group reports a boolean), and unit-tested against a
  pinned drop-table outcome through an injected roller seam rather than live randomness.
- **A new `ActionDef.ContributionScale` group** (`{Factors[], Floors[]}`, the SAME
  `loot.FactorLadder` core `Roll.Ladder` uses) multiplies every `Work.PerCycleContributions` amount
  before it is forwarded. The engine PRE-SCALES: the resolved multiplier applies before
  `StationCycleCompletedEvent` dispatches, and rides the event (`contributionScale()`) for DISPLAY
  ONLY, so a listener that forgot to multiply cannot under-award and one that multiplied again
  cannot over-award. `ExtensionAsset`'s per-leaf overlay (rule 5) gains `ContributionScale` as a
  third payload beside `Puppet`/`Custody`, so a re-skinning pack can retune only the floors it
  cares about.
- **One ladder core, one set of semantics.** Every ladder-shaped consumer in the schema
  (`Roll.Ladder`, `ContributionScale`) resolves through the shared `loot.FactorLadder`: an
  absent/empty `Factors` array resolves 0.0, a floor's `Min` reader-defaults to 0 and a `Min <= 0`
  floor IS reachable, and an equal-`Min` tie goes to the LAST authored floor. A duplicate `Min` in
  one ladder is `LADDER_DUPLICATE_FLOOR_MIN`, from one shared check. The two per-floor loot checks
  report what that core actually does: a floor authoring no positive `Min` is
  `LOOT_LADDER_FLOOR_MISSING_MIN` at INFO, because it is legal and engine-honored - the always-reached
  baseline tier - and only worth a confirm that a baseline was intended; and
  `LOOT_LADDER_FLOOR_EMPTY_GRANTS` warns only when a floor authors NEITHER `Grants` nor a
  `Presentation`, since a grants-less floor carrying a cue is the blessed pure-cue shape and reaching
  it does something. Only a floor with neither does nothing at all.
- **One name for the one weighted-factor concept:** `AddFactors` and `Values` are both now `Factors`
  (`Roll.Chance`, `Roll.Ladder`, `ContributionScale`, `StatRollEntry.Points`,
  `StationStep.Repeat`, `Stamp.Stats.Caps.Budgets[]`).
- **Other renames:** `StationAsset.Loot`/`ActionDef.Loot` is `ActionDef.Bonus` (an action's whole
  "what else a cycle hands over" group now lives beside its `Recipe`, never on the station);
  an action's `Presentation` group is its `Moments.Cycle` (so it pairs with its `Moments.Completion`
  sibling and both name their moment); `Presentation.Sound` is `Sounds[]` (played in authored order - a thud
  plus a chime is two entries; an entry is a bare id or `{EventId, DelayMs}`, and `Volume`/`Pitch`
  stay unauthorable because the sound primitive takes neither); `Roll.Grants.DropList` is `DropLists[]` (each entry rolled
  independently, so a guaranteed common table plus a rare one is two entries); `Tool
  .MinDurabilityPercent` is `Tool.Durability.MinStartPercent` (inside the group covering the same
  concern, with the engage-only semantics in the name); `Puppet.Look.Model.FallbackModelId` is
  `Puppet.Look.FallbackModelId` (it is read for every `Look.Source` arm, so it must not sit inside
  one arm's group); `SummaryHud.Position` presets are authored PascalCase (`"TopCenter"`) like
  every other id in the schema, with the legacy SCREAMING_SNAKE spelling still resolving.
- **Deletions, all of them things the engine never executed.** `Picker`/`Picker.ShowLocked` (no engine
  path produces a locked category, so both values rendered identically, and the per-action override
  was never read); `Camera.FaceBlock` (authoring `Camera.Recipe` at all IS the fixed-look opt-in now,
  which also makes the four exemplar stations' authored preset actually take effect); `Camera.Mode`,
  replaced by `Camera.Enabled` (its second arm meant "off", which is a boolean); `Tool.PowerScale`
  and with it `StationCycleCompletedEvent.toolMultiplier()` (a baked, non-composable curve over the
  same number `hytale:tool_power` already exposes as a free factor, whose only possible output was a
  contribution amount the engine never interprets - a better tool is now authored as a factor inside
  an action's `Bonus` rolls for output, or its `ContributionScale` ladder for a posted amount); and
  the four reserved `Presentation` leaves `Animation`/`AnimationItem`/`AnimationSlot`/`CameraEffect`.
- **One casing convention.** Every id is authored in the casing of the thing it names: an own-asset id
  is its filename, so PascalCase (`"Lootables": ["SawmillFinds"]`, `"Ref": "PrepFish"`), and a native
  id keeps whatever the native asset is called. Matching stays case-insensitive, so this is
  readability rather than a requirement. The two lowercase-snake union values are respelled
  (`Camera.Recipe: "LookRot"`). **A real trap closed with it:** `Flairs`/`FlairAsset` moment keys were
  matched EXACTLY while the validator lowercased first, so a key authored `"Cycle"` validated as known
  and then silently never fired; both ends now canonicalize to lowercase, which also lets ONE
  `FlairUnlockProvider` satisfy both the inline and standalone authoring routes. The lang-key
  namespace stays lowercase forever, independent of all of this.
- **`Work.Idle.Enabled` reader-defaults to TRUE**, matching `Puppet.Enabled` and every other
  opt-in-by-presence group, so authoring `"Idle": {"Fraction": 0.2}` does what it looks like it does.
  The leaf survives so a native `Parent` child can flip idle off while inheriting the rest of the
  group.
- **A produced-row breakdown line on the session summary.** A PRODUCED ledger row can now carry a
  smaller second line decomposing its total per cycle - the deterministic `Yield` amount plus
  whatever a `Bonus` roll's `Grants.OutputItems` added, e.g. `1 base + 3 bonus  x 12 cycles`
  explaining a 48-plank row. It renders ONLY when the produced quantity actually differs from the
  conversion's own authored amount, so a station with no `Yield`/`Bonus` tuning gets no second
  line, and the line's presence is itself the signal that something is earning extra. New key
  `ui.station.summary.produced_breakdown` in all 9 locales.
- **Guards so this class of drift cannot recur.** `ActionDef` and `ActionAsset` are held to the SAME
  authorable field set by a parity test with named exclusion sets (they had already drifted, and the
  drift ships as a capability a standalone action silently cannot author); every codec
  `.documentation(...)` string is scanned for internal process narration, since those strings ship in
  the jar schema and the generated schema reference and are therefore public;
  `StationStep.Repeat` gains the fixed-vs-ranged `afterDecode` warn its four sibling exactly-one-of
  groups already had; and `loot.FactorLadder` is a unit-tested pure core shared by every ladder
  consumer.

### Owner-facing limits and world-lifecycle hardening

- Adds `RpgStationsSettingsAsset.Limits`, three independent nullable per-WORLD ceilings on what the
  engine may have live at once - `MaxSessionsPerWorld` and `MaxCustodyClaimsPerWorld` (all-or-nothing:
  a new session, or a NEW custody claim, is denied with a localized toast; topping up an existing
  claim never counts) and `MaxPuppetsPerWorld` (pure presentation: past it, a session simply
  performs in the player's own body instead of spawning a worker double, the same graceful fallback
  a failed spawn already takes). Every leaf is unlimited when absent, so a server that never
  authors `Limits` behaves exactly as it did before the group existed.
- Adds world-unload eviction: this engine's block-keyed maps are global, keyed by a composite
  `"<worldUuid>:<x>:<y>:<z>"` string rather than partitioned per world, so unloading a world used
  to remove nothing from them - an instance-world fleet accumulated a stale entry (pinning a stale
  `World` and display `Ref`) per station block for the whole server uptime. A `RemoveWorldEvent`
  listener now stops every session still tracked for the removed world and releases every
  block-keyed entry naming it, by world-uuid prefix.
- Adds disconnect claim eviction: a player who places custody input and disconnects without a live
  session touching that block (place logs, walk away, log off) used to leak both the claim and its
  display prop entity forever, since none of the pre-existing claim-removal paths (session stop,
  block break, retrieve press, world unload) ever covered that shape. The disconnect handler now
  hands back every claim the departing player owns, in the departure world inline and in every
  other world they hold a claim in via its own world-thread hop.
- Narrows `StationInterruptDamageSystem`/`StationDeathSystem` from `Query.any()` to
  `PlayerRef.getComponentType()`: only a player can hold a work session, so a mob taking damage or
  dying (the overwhelming majority of either event in a populated world) used to pay a dispatch
  plus a session lookup for a question whose answer could only ever be "no".
- Fixes press-F custody retrieval to scope its display-entity match to the presser's own world:
  a `NetworkId` is issued from a per-world counter starting at 1 in every world, so the same
  integer routinely names a different entity in each loaded world, and an unscoped match could
  resolve (and hand over the contents of) a claim standing in a DIFFERENT world. The claim now
  caches its own display entity's network id at spawn time too, so a retrieve press reads no live
  components at all.
- Adds `RpgStationsApi.stationCount()`, a default-bodied `stations().size()` the shipped
  implementation overrides with the direct catalog size - the cheap presence-check/count path for a
  consumer that only wants to know whether stations are installed, without materializing a full
  `StationView` per station.
- Locale-hardened id handling: `StationUseInteraction`'s station-id fold passes `Locale.ROOT`, so a
  station id can no longer corrupt on a JVM whose default locale case-folds differently (the Turkish
  dotless-i class).

### The shipped Sawmill: its tool curve, its finds, and how you get the bench

The one default station 0.1.0 ships, authored entirely in ordinary content assets a server owner can
retune or replace leaf by leaf. Nothing here is engine-special-cased.

- Ships the Sawmill's tool-yield ladder, the curve that pays for the milling time. Three weighted
  tool factors sum into one ladder value (`hytale:tool_quality` at weight 10, `hytale:tool_item_level`
  at 0.1 to break ties inside a quality tier, `hytale:tool_power` at 1.0), and five floors at
  `11`/`22`/`33`/`40`/`50` grant `1`/`1.5`/`2`/`3`/`4` bonus planks on top of the deterministic
  `Yield.Base` of 1. Over the vanilla hatchets that reads as: a Wood or Crude hatchet reaches no floor
  and mills one plank per log; Copper reaches 11 for two; Iron reaches 22, whose fractional `1.5` pays
  two planks always and a third half the time, so the rung sits genuinely between its neighbours
  instead of collapsing onto one; Thorium, Cobalt and Adamantite reach 33 for three; Onyxium and
  Mithril reach 40 for four; and the 50 floor's five planks is beyond every forgeable tool, reachable
  only with the station's own drop-only trophy hatchet or a better modded one. That ladder is the
  action's only inline roll: everything the bench pays for a better hatchet is one readable curve,
  with no second proc layered on the same axis. A bare `Chance` roll remains good authoring for an
  outcome a ladder cannot express, and `docs/loot-and-factors.md` carries a worked example.
- Ships a `ContributionScale` ladder tracking that curve rung for rung - the same three factors and
  the same `11`/`22`/`33`/`40`/`50` crossings, scaling `2.0`/`2.5`/`3.0`/`4.0`/`5.0` - so a tool that
  doubles a worker's planks also doubles whatever amounts a listening mod attaches to the action. The
  jar attaches none itself (its own content is deliberately free of any progression vocabulary), so
  the ladder ships ready and inert until something declares a channel.
- Ships the Sawmill's bonus rewards as THREE separate lootables, one roll each, referenced together
  from the action's `Bonus.Lootables`. The split is an extension-point decision rather than a filing
  one: a lootable folds by id and a later layer replaces a whole FILE, so which rolls share a file
  decides what an add-on must inherit in order to re-tune one of them. One roll per file means each
  can be replaced alone.
- Ships `SawmillFinds`, the session-loyalty find table. It gates on staying at the bench: cycle 10 or
  later AND a tool of quality 2 or better, then 15 percent of qualifying cycles, into a three-floor
  ladder over the session's own cycle count - the first tier from cycle 10, the second from 25, the
  third from 50, each granting its own jar-shipped drop table. The upper two floors carry their own
  celebration cue, which the smart-cue rule keeps silent on a cycle whose table resolved to nothing.
- Ships the find table reading the HELD TOOL as well as the session, in two halves that each read
  one input. The tool decides HOW OFTEN: `BasePercent` 0 with the canonical tool ratio at 1.62x
  (`16.2` / `0.162` / `1.62`) and `CapPercent` 90, so the probability IS the hatchet - iron finds on
  about 36 percent of cycles, the thorium-to-adamantite band 54 to 56, onyxium 66, mithril 74, and
  the Sawmiller's Hatchet lands exactly on the cap. A zero base is safe because the quality-2
  Condition already decides who may find at all. The session then decides HOW DEEP, its cycle count
  plus 5 per quality step against floors at 5 / 25 / 50: iron opens the second tier at cycle 15 and
  the third at 40, mithril at 5 and 30, and the trophy starts a session already on the second tier.
  Quality is the only tool axis in that ladder, which keeps every step a whole rarity tier apart and
  readable straight off a floor number.
- Fixes the Stamp ritual losing a player's reagents when their inventory was full. Its failure path
  restored consumed reagents straight to backpack storage with no fallback, so a full inventory
  destroyed them - and a ritual can be failing precisely because its own output filled the last
  slot. Restores now route through the same hotbar-then-storage-then-drop-at-block grant every other
  payout in the mod uses.
- Fixes the station validator warning that this jar's own shipped `RPG_Station_Hold` effect was
  unknown. Station validation can run before the native asset registry finishes loading, and the
  `EntityEffect` existence check failed CLOSED against a store that was merely empty rather than
  missing the id. It now fails open on an empty store, matching the stance its sibling index checks
  already document.
- Ships the find tables as COMPOSED drop lists, using the native drop-list vocabulary rather than a
  flat list per tier. `RPG_Station_Sawmill_Byproducts` holds the offcut vocabulary in one file -
  plant fibre, tree bark, tree sap and sticks, as a `Multiple` container so a single pull can shake
  loose several at once - and each tier is itself a `Multiple` combining N `Droplist` references to
  that shared list with its own life-essence `Choice`. Retuning what milling yields is therefore one
  edit that moves every tier together, a richer tier simply references the shared list more times
  (one pull at T1 and T2, two at T3 and T4), and a pack can override that single id to reshape
  offcuts across the whole bench. The first tier pays offcuts ALONE: life essence enters at the
  second, so reaching it is a change in kind rather than more of the same.
- Ships `SawmillTrophy`, the trophy chase, alone in its own file precisely because it is the roll an
  add-on is most likely to reshape: a progression mod wants it scaled by its own notion of luck,
  which this engine cannot express, so the version here is deliberately the plainest one possible -
  a single flat chance with no factors at all. It wants all three tool axes at the vanilla Mithril
  hatchet's own values (quality 4, item level 50, Woods gather power 0.5) from cycle 5 onward, and
  then a visible `Chance` of `0.04` percent - 1 in 2500 eligible cycles, on the order of one per
  twenty-five full sessions. The whole probability is one readable leaf, with no ladder and no tiers
  hiding inside it. The win grants inline from the roll's own `Grants`, a command handing over the
  new **Sawmiller's Hatchet**: a drop-only Legendary masterwork copy of the Mithril hatchet with 500
  durability (the original carries 400) and a Woods gather power of `0.55` (the highest any vanilla
  hatchet reaches is `0.5`). It is authored standalone with no `Parent` and deliberately no `Recipe`,
  because inheriting the Mithril hatchet would inherit its forge recipe and make the chase pointless.
  The roll's own top-level `Presentation` fires the celebration on the win.
- Ships `SawmillMasterworkFinds`, a fourth find tier for the trophy's owner, in its own file for the
  same reason: it is what the chase pays out, so an add-on layering its own reward onto the trophy
  reshapes it without touching the chase or the loyalty ladder. It gates on the Sawmiller's Hatchet's
  own three axes (quality 5, item level 50, Woods power `0.55`), each exactly one notch above the
  chase's own gate and none reachable by a forgeable vanilla tool, and
  needs no cycle GATE at all - the chase already proved the loyalty. The cycle count drives its
  CHANCE instead, 15 percent rising half a point per cycle to a 75 percent ceiling reached at cycle
  120, so the trophy buys entry to the tier outright and a long session turns that entry into a
  near-certainty. It pays into the one find table with no empty entry, so the tool that took 1 in
  2500 to earn never comes up dry on a find. The reward composes rather than branching: a Sawmiller's Hatchet in hand
  scores 55.55 on the yield ladder, so it unlocks the 50 floor's fifth plank, the matching
  contribution rung, and this tier at once.
- Ships the Sawmill block as a craftable item on a bare install: a **tier 2 Workbench** recipe taking
  one crude hatchet (the tool becomes part of the bench), any one log, and any four planks, both
  material inputs authored as native resource-type families so every wood species qualifies. The tier
  gate puts it behind the first workbench upgrade, so the bench arrives as an earned base improvement
  rather than a day-one freebie, and the Crafting bench is deliberately where it sits (that is where
  vanilla crafts every functional station and every hatchet) rather than the Furniture bench the
  decor-grade vanilla Lumbermill sits at. That craftability is a property of the jar's own block item
  and nothing else: a pack shipping its own block under the same id replaces the whole item by load
  order, so a pack authoring no `Recipe` on its copy makes the station uncraftable the moment it is
  installed and owns acquisition its own way. No flag, no engine branch, pure load order.

### Docs: the RPG Stations documentation

- Ships a top-level `README.md` (what the mod is, install/build, a pointer into the guides below)
  plus documentation for every feature above as in-repo Markdown: a getting-started guide, a
  concepts primer, authored guides (your first station, actions and steps, custody and placed
  displays, enhancement and stamping, extending other packs, flairs, localization, loot and
  factors, multi-station programs, native composition, puppet presentation, selection, settings,
  commands, integrations) under `docs/`, an Extension Channels page teaching both directions of
  the extension vocabulary in one place, and a codec-generated schema reference (`SCHEMA.md`,
  regenerated via `gradlew generateSchemaDocs`) covering every content type. The public GitHub
  repository is the docs surface: no separate site build or deploy step is involved. A standalone
  documentation site (a Next.js static export under `docs-site/`) was drafted and then retired
  before release in favor of this in-repo surface, so the shipped 0.1.0 docs are `README.md`, the
  `docs/` markdown guides, and `SCHEMA.md` only.

### The loot layer re-bases onto ziggfreed-common's shared core

- Adopts `ziggfreed-common`'s shared loot core as this mod's loot layer, deleting the duplicated
  model and evaluator on this side, so identical JSON now behaves identically at a station, in a
  chest, and at a quest turn-in. The engine, the seams and the shipped Sawmill behave as before;
  what changed is where the vocabulary lives and, in four places, how it is spelled.
  - **Loot tables and roll pools are the shared library's assets**: `Server/ZiggfreedCommon/
    Lootables/<Name>.json` and `Server/ZiggfreedCommon/RollPools/<Name>.json`, registered by
    `ziggfreed-common` rather than by this mod. Ids, case-insensitive matching and the
    replace-by-id fold are unchanged, as is `ExtensionAsset`'s ability to append rolls to a table
    or entries to a pool - both appends still reach every site that reads them.
  - **A `Roll.Chance` is the shared `{Base, Factors, Clamp}` formula**, read as a percentage and
    held inside `0..100` whatever the terms say. `BasePercent` becomes `Base` and `CapPercent`
    becomes `Clamp.Max`. A ladder's `Factors` stays a bare weighted-term array, because a ladder
    has no base to stand on and no ceiling to hold it.
  - **Every `Factors` array is the shared weighted factor TERM** (`{Factor, Param?, Weight?}`), the
    same leaf at a chance, a ladder, a `ContributionScale`, a stat-roll's `Points`, a Stamp budget
    and a step's `Repeat`. The shape an author writes is unchanged.
  - **A celebration is a `Cue`, a MOMENT ID string, not an inline `Presentation` body.** The loot
    table names a moment and the station decides what it sounds like, through the same emission
    funnel every other station moment uses - so re-skinning every find at once is one edit in an
    action's `Moments` map rather than one per table, and a flair can target a find cue by name.
    Well-known moment ids, a per-step `step:<actionId>:<stepId>` and the OPEN author-defined
    `cue:<yourName>` namespace all resolve. The smart-cue rule is unchanged: a cue beside grants
    rides only once those grants genuinely produced something. The shipped Sawmill publishes a
    four-cue palette (`rare_find`, `cue:find_deep`, `cue:find_apex`, `cue:trophy`) a table can name
    with no presentation of its own.
  - **The three station-only payouts are registered reward KINDS** inside `Grants.Rewards`, so they
    compose with `Items`, `DropLists`, `Commands` and anything another mod registered:
    `rpgstations:output_items` (`{"Count": "1.5"}`, replacing `Grants.OutputItems`),
    `rpgstations:contribution` (`{"Channel", "Param"?, "Amount"}`, replacing
    `Grants.Contributions`), and `rpgstations:effect` (`{"Id", "DurationMs"?}`, replacing
    `Grants.Effects`). Every rule around them is unchanged: output items stay fractional and summed
    once per cycle, contributions stay one-shot and unscaled, and effect teardown still differs by
    trigger.
  - **A `StatRollEntry` authoring `Weight: 0` now means NEVER DRAWN**, not "the default 1". Omit
    `Weight` for the neutral 1.0.
- **The stamper contract moves to `ziggfreed-common`** (`loot.stamp.Stamper`, installed through the
  static `StamperRegistry`), taking `StampInspection`, `StatRoll` and the whole roll + budget engine
  with it. The api artifact goes to **0.2.0**: `EnhanceStamper`, `EnhanceStamperRegistry`,
  `StampInspection`, `StampResult`, `StatRoll` and `RpgStationsApi.enhanceStampers()` are removed,
  and a mod that stamped gear registers a `Stamper` instead. `EnhanceLine` stays, with its `label`
  now optional - what a stamped stat is CALLED belongs to whichever mod owns that vocabulary, so the
  summary paints the id and its points plainly and a consumer supplies the styled row through
  `SummaryEnricherRegistry`.
- `Stamp.Stats.Caps.Economics` moves up one level to `Stamp.Economics`: it prices the REAGENTS and
  never touches the point budget, so it sits beside the reagents rather than among the ceilings.
- `hytale:tool_power` registers through the nullable resolution seam, so a gather type the held tool
  has no spec for answers "cannot tell" rather than `0` - a bounds-less gate on it stays shut. The
  no-`Param` form still answers the station's own effective gather type. The held-tool power fold
  now goes through the shared reader, which keeps the STRONGEST spec when a tool authors one gather
  type twice (it previously kept the last).

### The factor vocabulary re-bases onto ziggfreed-common's shared core

- Adopts `ziggfreed-common`'s shared factor vocabulary as the machinery behind this mod's own
  factor surface, deleting the duplicated engine on this side. The `api` types third parties code
  against (`FactorRegistry`, `StationFactorProvider`, `FactorContext`) are unchanged and remain
  source-compatible: a registration written against them keeps compiling and behaving identically,
  and every authored schema key stays byte-identical, so no content changes.
  - The authored gate leaf `{Factor, Param?, Min?, Max?}` IS the shared `FactorCondition` now,
    built through its codec factory so the `Factor` field keeps this mod's live
    `rpgstations:factors` Asset Editor pick list. `asset/Conditions` holds the single codec
    instance every gate site embeds.
  - Registration gains owner attribution plus a per-provider failure ledger from the shared
    registry, so an admin listing can name WHICH mod claimed a factor and whose provider keeps
    failing; an unregistered id now warns once instead of resolving silently.
  - `hytale:stat` is answered by the shared portable standard library (a straight read of the
    acting entity's own stat map) rather than this mod's own copy of that read. The four
    `hytale:tool_*` ids stay this engine's own, answered from the SESSION's tool snapshot, which is
    the correct number at a station: same portable vocabulary, context-appropriate resolution.
  - One bound-gate authority (`loot/FactorGate`) now backs every `Conditions` array evaluated
    through a factor lookup, and the station `Requires` gate uses the shared array evaluator, which
    names the factor that shut the gate in the deny log.
  - Behavior note for `hytale:stat`: an unreadable stat (no live subject, an unregistered channel,
    a blank `Param`) resolves as UNRESOLVABLE rather than `0`, so a bounds-less presence check on
    it fails closed instead of passing. A `Min`-bounded gate and every summed `Factors` reference
    behave exactly as before.
