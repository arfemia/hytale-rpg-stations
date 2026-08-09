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
  hunt), an effect-mode movement lock, and native block-mount seating (`Hold.Seat`) as the crowned
  answer for a held/facing worker.
- Adds tool gating (native `Tags`/`Gather`/`Ids` routes) with optional tool-power contribution
  scaling (`Tool.PowerScale`), and
  recipe derivation either authored (`Recipe.Conversions`) or derived from native crafting recipes
  (`Recipe.FromCrafting`), zero hand-authored conversions for a station like the Sawmill.
- Adds `Recipe.Yield`, the per-cycle output-quantity transform that applies to authored and derived
  conversions alike: a flat `Base`, a `Scale` multiplier, `Min`/`Max` clamps, and a `Bonus` ladder
  whose `Values` are the same weighted `FactorRef` sum every other formula site uses, so a yield
  bonus can key off any registered factor. Yields may be FRACTIONAL: an effective `2.5` pays two
  items every cycle and a third on a 50% roll, so the long-run average is exactly the authored
  number and a mid-ladder tool can sit genuinely between two whole yields instead of being rounded
  onto a neighbour.
- Adds three tool-describing built-in factors so a "better tools yield more" curve is authorable
  with no code: `hytale:tool_quality` (the native `ItemQuality.QualityValue`) and
  `hytale:tool_item_level` (the native `ItemLevel`) beside the existing
  `hytale:tool_power`. Summing all three is the intended shape, because no two of them can rank
  a full tool family alone: gather power saturates across the upper tiers, quality cannot separate
  tools that share a tier, and item level does not track rarity at all. The shipped Sawmill uses
  exactly that curve to pay for its milling time, running from one plank per log on a starter
  hatchet up to four on the best.
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
- Adds validation (`StationValidator`, warn-only, never blocks) and a session-summary panel
  (`ui/StationSummaryHud`) showing cycles and items consumed/produced, plus whatever extra rows a
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
  New `util.InventoryAccess` (DRY: the shared ref/store null-guard + component fetch every one of
  those call sites duplicated) replaces `LootEngine`'s own private `storageContainerOf` and backs
  every other site. Zero `@SuppressWarnings("deprecation")` anywhere; `ziggfreed-common`'s
  arc-touched files (`cast/CastKernel`/`StepSemantics`, `i18n/Msg`, `ui/hud/KeyedCustomHud`,
  `ui/rows/SummaryRow*`) were audited via a `-Xlint:deprecation` compile and carried zero
  deprecated calls to begin with.

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
  plays once when it begins executing, not just the dedicated `Present` step's).

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

- Adds the orthogonal-phase `StationStep` reshape: the old `Type`-discriminated union (`Consume`/
  `Produce`/`Wait`/`Roll`/`Command`/`Stamp`) is replaced by a step record that composes any
  combination of nullable `Walk`/`Consume`/`Stamp`/`Produce`/`Roll`/`Commands` phases in one fixed
  order, so a single step can carry several effects at once instead of needing one step per effect
  (a phase-free step is a pure timed beat).
- Adds a unified `LootRef` (`{Lootables[], Rolls[]}`) that `StationAsset.Loot`, `ActionDef.Loot`,
  and a step's `Roll` phase all share, and reshapes the Stamp step's stat-roll caps onto a weighted
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
  `InteractionEffects.Particles`, so a moment can layer bursts. Unauthored knobs reproduce the
  previous playback exactly (scale 1, a 4-second client-playback cap, no rotation or offset); the
  duration cap is authorable per burst but stays a leak guard against unbounded-spawner systems.
  The sibling `Presentation.Camera` leaf is spelled `CameraEffect`, matching native
  `InteractionEffects` and disambiguating it from the station-level `Camera` group.
- Adds ref-or-inline authoring on the three leaves that reference one of this mod's own asset
  types: `LootRef.Lootables[]`, `StationStep.Stamp.Stats.Pool`, and `ActionDef.Ref` each accept an
  inline anonymous body (optionally with its own `Parent`) in place of an id, through the engine's
  own contained-asset codec, and each emits a typed cross-reference into the generated schema
  reference instead of an untyped string. References to NATIVE assets stay id-only.
- Adds four authoring knobs: `Roll.Grants.Contributions[]` (one-shot amounts posted on a
  conditional-lootable find, forwarded on their own unscaled list so a find is worth the same
  whatever tool the worker holds, and restricted to a `Cycle` trigger), `Tool.MinDurabilityPercent`
  (refuse to start work with a tool worn below a threshold; a session already running still ends at
  breakage, not at the threshold), `Custody.SingleFamily` (lock a claim to the first-placed item's
  resource family, so a station holds 50 oak or 50 pine but never 100 mixed), and
  `SummaryHud.OffsetX` beside the existing `OffsetY`.
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
- Documents every codec leaf. All 309 authorable leaves across the seven content types carry a
  description of what they do and what they default to, and a coverage test fails the build on a
  blank one, so the generated schema reference and the in-game Asset Editor both show real help
  text on every field.
- Adds in-game Asset Editor support to the content types: collapsible section headings over each
  top-level group, pick lists on 19 value vocabularies (this mod's live station / action /
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
  `Tool.MinDurabilityPercent` authored outside `(0, 100]`, and a redundant `Custody.SingleFamily`
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
  inside one Roll, across its `Conditions`, `Chance.AddFactors`, and `Ladder.Values`. Two `stat`
  references with different `Param`s are a legitimate composition and never fire it.
- Names the authoring sites for what they mean, so the two scaling rules are visible in the JSON
  rather than only in the engine: `Work.PerCycleContributions[]` is posted every completed cycle,
  multiplied by the resolved tool multiplier and pre-scaled by `Work.Idle.Fraction` on an idle
  cycle; `Roll.Grants.Contributions[]` is posted once and verbatim, inheriting neither. Same record
  type, different documented semantics per owning group, no mode flag on the entry. The event
  carries them as two lists, `contributions()` and `oneShotContributions()`, and `toolMultiplier()`
  applies to the first only.
- Names the remaining scaling knobs for the mechanism instead of a consumer's reward type:
  `Tool.PowerScale` (tool power to the per-cycle multiplier, leaves unchanged) and
  `Work.Idle.Fraction` (the fraction of a normal cycle's amounts an idle practice cycle posts).
  The matching validator ids are `MISSING_CONTRIBUTION_CHANNEL`, `NONPOSITIVE_CONTRIBUTION_AMOUNT`,
  `EXTENSION_CONTRIBUTION_DUPLICATE`, and `LOOT_CONTRIBUTION_{WRONG_TRIGGER,MISSING_CHANNEL,
  NONPOSITIVE_AMOUNT}`.
- Adds `MmoAgnosticismTest`, which scans `src/main/java`, `api/src/main/java`, and
  `src/main/resources` for foreign progression vocabulary and fails the build on any hit, with an
  empty allowlist. A convenient comment is exactly how a vocabulary creeps back in.

### Pre-release schema sweep (the last authoring-surface pass before 0.1.0)

The whole authoring surface was reviewed once more while a rename or removal was still free (the
`api` freezes at 1.0.0, content schema had no back-compat obligation yet, and an unrecognized key only
ever produces a boot-log `WARNING: Unused key(s)` line). Everything below is part of the 0.1.0 schema
as shipped, not a change to something previously released.

- **A station is an ORDERED LIST OF SELF-CONTAINED ACTIONS, and station-level group inheritance is
  DELETED.** `StationAsset` keeps only `Identity`/`Block`/`Requires`/`Flairs`/`Actions[]`; every
  other group (`Work`, `Recipe`, `Tool`, `Hold`, `Camera`, `Animation`, `Custody`) lives
  EXCLUSIVELY on an `ActionDef` entry, with no station-level default left to fall back to. Two
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
  different files under different concept names); its replacement, `Roll.Grants.OutputItems` (an
  `Integer`), is ADDITIVE - N extra units of the cycle's own primary output, directly comparable to
  `Yield`'s own number because both count the same item. A `Roll` in an action's own `Bonus` decides
  every bit of "sometimes you get extra", with the full `Roll` vocabulary (`Trigger`, `Conditions`,
  `Chance`, `Ladder`) available for it; `Yield` decides only "how much of the thing you made,
  guaranteed".
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
  one ladder is `LADDER_DUPLICATE_FLOOR_MIN`, from one shared check.
- **One name for the one weighted-factor concept:** `AddFactors` and `Values` are both now `Factors`
  (`Roll.Chance`, `Roll.Ladder`, `ContributionScale`, `StatRollEntry.Points`,
  `StationStep.Repeat`, `Stamp.Stats.Caps.Budgets[]`).
- **Other renames:** `StationAsset.Loot`/`ActionDef.Loot` is `ActionDef.Bonus` (an action's whole
  "what else a cycle hands over" group now lives beside its `Recipe`, never on the station);
  the action/station `Presentation` group is `Cycle` (so it pairs with its `Completion` sibling and
  both name their moment); `Presentation.Sound` is `Sounds[]` (played in authored order - a thud
  plus a chime is two entries; deliberately not promoted to `[{EventId, Volume, Pitch}]`, which the
  sound primitive cannot honour); `Roll.Grants.DropList` is `DropLists[]` (each entry rolled
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
