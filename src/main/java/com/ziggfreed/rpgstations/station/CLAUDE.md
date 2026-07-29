# station/ - the session engine (interactive work stations)

Router for `station/`, THE big package in this mod: the diegetic work-loop session machine. Press
F on a station block -> camera pulls third-person (or the player mounts the block as a seat, or a
puppet spawns and performs the work), the work animation plays per swing, items convert per cycle
or per an authored step program, loot rolls through `loot/`, and skill-XP declarations forward as
`XpAsk`s any listening progression mod interprets. Design authority:
`../../../../../../.claude/research/raw/rpg-stations-scope2-unified-design-2026-07-23.md`
(sections 2-3, decisions 33-41 in `../../../../../../.claude/research/rpg-stations-extraction-design.md`),
superseding the phase-1/phase-2 design for everything the scope-2 redesign touches.

## WAVE BOUNDARY (scope-2 wave 3 landed - the multi-station seam EXECUTES)

The scope-2 schema (`../asset/CLAUDE.md`) carries the FULL multi-station field set; as of wave 3
the WHOLE set executes:

- **Single-station (waves 1-2, unchanged)**: `Consume`/`Stamp`/`Produce`(`To:"Inventory"`)/`Roll`/
  `Commands`/`Duration`/`Repeat` at the PRIMARY station. Placed-input custody, the anvil's Stamp
  ritual, the sawmill's implicit convert loop, camera/mount/puppet, exit hooks - all unchanged.
- **Multi-station (wave 3, NEW - this leg)**: `StationStep.Walk` (the puppet travels to an anchor
  via `ziggfreed-common`'s `PuppetNav` bounded A* + `PlayerPuppetService.walkTick`/`setWalking`,
  suspend/resume per frame through `StationWalkState`), `StationStep.At` (a step's `Consume`/
  `Produce` custody resolves the anchor's blockKey, not the primary), `Produce.To:"Custody"`
  (cross-station output into the anchor's claim + display), and `ActionDef.Anchors`
  DISCOVERY/CLAIMING (the DERIVED block-item seed below, plus the lazy `knownStationBlocks` index fed
  by interaction warming + `PlaceBlockEvent`,
  bounded ring scan last resort; atomic first-wins claim into the generalized `byBlock` map with the
  gate-m5 own-session/custody precedence). New stop reasons `INPUTS_EXHAUSTED` (repeat-while-inputs,
  design 2.4), `ANCHOR_LOST` (a remote anchor broken mid-program, design 2.6), `PATH_BLOCKED` (a
  walk-step-entry re-solve failed). The `iterationConsumed` refund ledger (design 2.5/M1) refunds an
  in-flight iteration's consumed inputs at `stop()`, cleared by any `Produce.To:Custody` (refund and
  custody-return mutually exclusive). The `WAVE3_PENDING` validator warn is GONE; the anchor/walk
  validator checks (`ANCHOR_STATION_UNKNOWN`/`WALK_TARGET_UNKNOWN_ANCHOR`/`STEP_AT_UNKNOWN_ANCHOR`/
  `WALK_REQUIRES_PUPPET`) stay as the live discovery-time coverage. See `StationAnchors`
  (pure cores), `StationWalkState`, `StationBlockPlaceSystem`, and the anchor section of
  `StationService`.

## Content + catalogs

- **Stations**: [`asset.StationAsset`](../asset/CLAUDE.md) (`Server/RpgStations/Stations/*.json`)
  folds into [`StationCatalog`](StationCatalog.java). Ids are lowercase (canonicalized at
  decode). This jar ships its OWN default Sawmill (`Server/RpgStations/Stations/Sawmill.json`,
  standalone-playable with the built-in `rpgstations:` factors + `SawmillFinds` lootable); the
  `skill-stations-pack` adds its own luck-tier lootable plus an MMO-luck-scaled bonus-copy roll as
  an ADDITIVE `Extensions/SawmillProgression.json` (below) rather than a full-file override (it does
  NOT re-author the jar's Woodcutting/Crafting Xp - the jar base is the single Xp authority; an
  extension appends a genuinely-new skill, never re-adds one the base already grants, A8 review M1)
  - see `../../../../../../CONTENT_PACKS.md`'s Station authoring section for the authoring guide
  (brief reference only; do not duplicate it here). **The jar Sawmill owns the PRESENTATION defaults
  too** (`Puppet` + `Custody.Display`, the maintainer's in-game-tuned values, plus `Work.CycleMs`
  4805): they used to live only in the pack's full-file station override, so retiring that override
  dropped them and the sawmill regressed to a visible seat-mounted player working invisible placed
  logs. They ship here now, and a pack re-skins them through an `ExtensionAsset` `Puppet`/`Custody`
  per-leaf overlay instead of re-owning the whole file.
- **Standalone actions**: `asset.ActionAsset` (`Server/RpgStations/Actions/*.json`) folds into
  `ActionCatalog` (same `AssetStoreRegistrar` + `LoadedAssetsEvent` pattern as every other type).
  An inline `Actions` map entry's `Ref` leaf resolves against this catalog - see the ActionAsset
  bullet below.
- **Extensions**: `asset.ExtensionAsset` (`Server/RpgStations/Extensions/*.json`) folds into
  `ExtensionCatalog` - see its own bullet below.
- **Lootables/RollPools/Flairs/Settings**: unchanged catalog shape (`loot.LootableCatalog`,
  `loot.RollPoolCatalog`, `FlairCatalog`, `SettingsCatalog`) - see `../loot/CLAUDE.md`.

## Sessions

[`StationService`](StationService.java) (the biggest class in the mod) owns the
`IDLE -> STARTING -> WORKING -> STOPPING` machine over transient, player-anchored
[`StationSession`](StationSession.java)s (never persisted - no per-player state lives in this mod,
by construction). One entry (`toggle`, from `interaction/StationUseInteraction`'s object-form
`Station` param), one idempotent exit funnel (`stop`), every start-denial a localized toast.
Sessions bucket per world in a `WorldKeyedQueues` (ziggfreed-common) drained by
[`StationFrameSystem`](StationFrameSystem.java) `extends AbstractWorldFrameSystem` (ECS systems
are class-keyed - this is RpgStations' OWN concrete subclass).

## The step engine (design 2.1, decisions 34/38 - REWRITTEN this wave)

The pre-scope-2 engine dispatched a `StationStep.Type` union through a per-type handler table.
**That union is gone.** A step is now an orthogonal-phase record (`../asset/CLAUDE.md`'s
`StationStep` bullet); the engine walks a FIXED phase order per iteration instead of branching on
a discriminator.

- [`StationStepRegistry`](StationStepRegistry.java) is now ONE composite handler walking
  Conditions -> `Walk` -> `Consume` -> `Stamp` -> `Produce` -> `Roll` -> `Commands` ->
  `Presentation`/`Puppet.Clip` -> `Duration` per step, still wrapped in the conditions-gate +
  throw-guard layer (design 9.3/M4's binding fix carries forward: a throwing phase degrades to a
  clean session `stop()`, never crashes the shared per-world frame drain) - never six independent
  per-type entries.
- [`StationStepKernel`](StationStepKernel.java)/[`StationStepSemantics`](StationStepSemantics.java)
  keep the `cast.step` contract unchanged (`isSuspend`/`nextIndex` still wire the `OnConditionFail
  .Goto` branch mechanism); they are agnostic to whether a step is single-phase or multi-phase.
- [`StationStepDecisions`](StationStepDecisions.java) gains the PURE cores this reshape needs:
  `Repeat` resolution (delegates to `StationStep.Repeat#resolveCount`, the caller supplies the
  resolved `factorContribution`), and `Duration` suspend/resume math (reusing the old `Wait`
  step's suspend/resume math verbatim - a `Duration` hold suspends exactly like the retired
  `Wait` type did).
- [`StationStepContext`](StationStepContext.java)/[`StationStepResult`](StationStepResult.java)
  (the per-run bundle + sealed `Success`/`Suspend`/`Skip`/`Fail`) are unchanged in shape.
- [`ImplicitProgram`](ImplicitProgram.java) COLLAPSES to ONE step
  (`{Consume, Stamp:null, Produce, Roll, Presentation}` folded onto a single `StationStep`
  instead of the old four-step `[Consume, Produce, Roll, Present]` array) - byte-equivalent
  behavior (a station with no `Actions`/`Steps` authored runs identically), simpler anchor for
  the phase model.
- `StationSession` resume state (`programSuspended`/`programIndex`/`stepDeadlineMs`/
  `activeProgramSteps`) is UNCHANGED this wave - a `Duration` hold suspends/resumes through the
  exact same fields the old `Wait` type used. The design's `stepIteration`/`walkProgress`
  additions (per-step iteration count tracking, in-flight walk position) are `[wave 3]` - not
  added this wave, since `Repeat` resolves once at step entry (no cross-tick iteration state
  needed yet) and `Walk` does not execute.
- **A step combining `Consume` + `Produce` is an ATOMIC transform** (no consumed-without-produced
  window across one iteration) - the design's transactional-edges rule (2.5/decision 38): a
  `Produce.To:"Custody"` commit (when wave 3 lands it) clears the CURRENT iteration's consumed
  ledger; refund and custody-return stay mutually exclusive per iteration. This wave, every
  shipped program's `Consume`+`Produce` pair lands within one step or is bridged by the session's
  existing per-cycle commit boundary - never split across a suspend this wave (that split is
  exactly what `Walk` would enable, `[wave 3]`).

## ActionAsset resolution (design 1.5)

`Ref` resolution is a NEW branch inside the existing [`ActionResolver`](ActionResolver.java)
choke point (`resolve(asset, actionId)`): when an inline `Actions` entry authors `Ref`, the named
`ActionCatalog` entry is the BASE and the inline entry's other groups overlay it group-wise (the
SAME whole-group-replace rule `ActionResolver` already applies station -> action, applied twice).
A dangling `Ref` is validator finding `ACTION_REF_UNKNOWN`; `toggle()` denies gracefully with
`ui.station.action_unavailable` rather than throwing. `ActionResolver.selectAction`/
`selectActionByFamily` (the diegetic input-matched selection cores) are unaffected - they operate
on the RESOLVED `ActionDef` regardless of whether it came from an inline body or a `Ref`.

## ExtensionAsset resolution (design 1.8, decisions 27/37)

`ExtensionCatalog.applyTo(target, targetType, targetId)` is the ONE resolve-at-read fold point
(the `FlairCatalog.effectiveFlairsFor` pattern generalized): given a station/action/lootable/
rollpool about to be used, it gathers every folded `ExtensionAsset` whose `Target` resolves to
that type+id, sorts them via `ExtensionAsset.sortedForApply` (the `(Priority, extension id)`
tuple), and applies each in order per the codec's own documented merge rules (`../asset/
CLAUDE.md`'s ExtensionAsset bullet: additive-only, base-wins key collisions, append for unkeyed
arrays, anchored insertion for `Steps`). Cached per fold generation alongside `StationCatalog
.resolvedConversions`, so a hot per-cycle read never re-walks the extension set. Composition order
(m7): extensions apply to the `Parent`-resolved target at READ time and do NOT flow down `Parent`
chains; a `Target:{Action}` extension reaches every `Ref` user of that action, a
`Target:{Station}` step-insert applies post-`Ref` to that one station only. Boot log carries one
INFO `EXTENSION_APPLIED` summary line per target, enumerating the CONTRIBUTION KINDS that composed
onto it and not just how many extensions did (`EXTENSION_APPLIED: Station sawmill <- 1 extension(s)
[Loot]`); the enumeration comes from the pure `authoredPayloadKinds`.

**The two PER-LEAF presentation overlays (`Puppet`, `Custody`)** are the one non-collection payload
shape, so they merge leaf-wise instead of appending: `applyToStationPuppet`/`applyToActionPuppet`/
`applyToStationCustody`/`applyToActionCustody` are the read-side entry points over the pure
`overlayPuppet`/`overlayCustody` cores, which walk the group recursively and take the OVERLAY's leaf
where it is authored, the BASE's where it is not (`firstNonNull` is the ONE rule at every depth). A
`Custody` overlay carrying only `Display` therefore never clobbers `States`/`MaxQuantity`/`Input` -
that is the whole reason the capability exists, so a pack can re-skin a station's placed-input visual
without silently disabling its placement mechanics. Overlays apply in `APPLY_ORDER`, so the later
(higher-priority) extension wins a same-leaf contest and the fold stays deterministic; a null overlay
group returns the base object unchanged. Covered by `ExtensionOverlayTest` (`src/test/java/com/
ziggfreed/rpgstations/station/`, fixture JSON authored by the test and decoded through the real
shipped codecs). **Call-site status: WIRED** - the 2-arg live `ActionResolver.resolve` applies both
overlays after the pure resolution (`applyExtensionOverlays`: the `Ref` `ActionAsset` id first, then
the inline map key when it differs, then the station id - most-specific-wins per leaf;
identity-preserving, so the zero-extension path returns the pure result untouched). The 3-arg pure
core stays extension-free for unit tests. Unlike `Loot`/`Xp` (applied at `StationService`'s own read
sites), `Puppet`/`Custody` overlay INSIDE the resolver choke point, so every reader
(`StationService`, `StationStepHandlers`, `selectActionForBlockState`'s restart recovery) sees the
same effective groups with no per-site wiring.

## Held-tool gate (unchanged by scope-2)

`StationAsset.Tool`, checked at start AND per heartbeat -> `TOOL_CHANGED` stop
(`heldToolMatches`): the player must HOLD a matching tool. Three NATIVE routes, match = ANY
(null/no-live-route group = ungated): `Tags` = the native item-tag object map intersected
case-insensitively with the held item's raw tags; `Gather` = the FUNCTIONAL test over the held
item's `ItemToolSpec.getGatherType()/getPower()`; `Ids` = the FALLBACK for modded items, exact id
OR case-insensitive underscore-segment match. Diegetic AND load-bearing for client stability: the
work emote NEVER sets `HideItemInHand` (correlated with a client `NullReferenceException` in
early smoke testing). Cycle consume prefers BACKPACK storage over the combined view for the same
reason.

## THE `ItemToolSpec` construction trap ([`StationToolScaling`](StationToolScaling.java))

`heldPowerFor` takes an injected `ToolPower(gatherType, power)` value shape rather than the live
`ItemToolSpec` directly, because merely CONSTRUCTING a real `ItemToolSpec` triggers its
`AssetBuilderCodec` static init, which THROWS outside a running Hytale server - the same trap
[`StationRecipeDeriver`](StationRecipeDeriver.java)'s `CraftingCandidate` shape avoids. If you add
a new pure-tested helper that reads tool data, follow this pattern - do not construct
`ItemToolSpec` (or any other `AssetBuilderCodec`-backed engine type) in code that must run in a
unit JVM.

## Tool-power XP scaling (unchanged)

`StationAsset.Tool.XpScale`: `multiplier()` = `clamp((heldPower/ReferencePower)^Exponent,
MinMult, MaxMult)` (defaults `Exponent 1.0`/`MinMult 0.5`/`MaxMult 2.0`), read fresh every cycle
off the currently-held item, neutral 1.0 for a null/inactive scale or a held tool with no
matching spec. Forwards on `StationCycleCompletedEvent.toolMultiplier`.

## Recipe ingredients (now `asset.Ingredient`-shaped)

`Conversion.Input`/`Output` are `asset.Ingredient` (`../asset/CLAUDE.md`) - exactly one of
`ItemId`/`ResourceTypeId` on Input, `ItemId` only on Output; `ResourceTypeId` is a native
`Item.ResourceTypes` FAMILY (e.g. `Wood_Hardwood_Trunk` = any hardwood log). `ItemResourceType`
exposes its id as a PUBLIC FIELD `.id` (no `getId()` - a protocol class quirk).
[`StationRecipeDeriver`](StationRecipeDeriver.java)'s `Recipe.FromCrafting` derives one
`Conversion` per LIVE `Item` whose native `Recipe.BenchRequirement[].Categories` intersects the
authored `Categories` and whose native recipe has exactly one input, zero hardcoding (the shipped
Sawmill is just `{"FromCrafting":{"Categories":["WoodPlanks"]}}`). The PURE core
(`resolve`/`deriveFromCrafting`) takes injected `CraftingCandidate`s, unit-tested without a live
item map.

## Cadence + the `emitMoment` choke point (unchanged)

1000ms heartbeat (terminate checks: ref/store validity, block-gone, walk-off `MaxMoveMeters`,
crouch exit, held-tool still matching, `MaxDurationMs` cap, the engine-toggle check via
`SettingsCatalog`; hold TTL refresh) + per-`Work.CycleMs` cycle (Convert transaction with
output-room PRE-check before consume; loot rolls via `loot/LootEngine`; `StationEvents
.fireCycleCompleted`; the cycle `Presentation` at the block via `emitMoment`). A multi-action
station's authored `Steps` program dispatches through the step engine above instead of the
classic Convert transaction; the implicit single-step program (`ImplicitProgram`) is what a
station with no `Actions`/`Steps` gets, so both paths converge on the SAME step engine.

`emitMoment(store, s, momentId, presentation, targetPos)` in `StationService` is the ONE
presentation-playback funnel every station moment goes through (`StationFlairs.MOMENT_CYCLE`/
`MOMENT_SWING`/`MOMENT_IMPACT`/`MOMENT_RARE_FIND`/`MOMENT_COMPLETION`, plus a per-step
`StationFlairs.stepMomentId(actionId, stepId)`) - it is ALSO the flair-resolution choke point
(`StationFlairs.effective` against `FlairCatalog.effectiveFlairsFor`'s merged map). **Every
particle spawned here is capped to `MOMENT_PARTICLE_MAX_DURATION_SECONDS` (4.0f)** via
ziggfreed-common's `ModelParticleService`'s duration-capped `spawnAt` overload - at least one
shipped particle asset (`Block_Gem_Sparks`) authors an UNBOUNDED spawner (`TotalParticles < 0`)
that, fired without a duration cap, never stops spawning. Route a new moment call site through
`emitMoment` - never call `ModelParticleService` directly, or you lose both the flair overlay AND
the leak guard (this bug was found in-game; do not reintroduce it).

## Per-swing cadence (unchanged)

`StationAsset.Animation.Swing` (its OWN `Presentation`): an independent server-side timer fires a
swing SFX/VFX cue TOGETHER with a one-shot re-fire of the work animation. The work emote must NOT
loop client-side by convention: a looping emote (`IsLooping:true`) with no `Swing` group behaves
as before (client loops it, zero re-fires); a non-looping emote needs an authored
`Swing.IntervalMs`. `runSwing` picks the animation ROUTE via `useActionSlotForSwing(seatMode)` -
see the seat/swing routing bullet below. `scheduleImpactAt`/`impactDue` schedule an optional
delayed impact cue (`Swing.Impact.{DelayMs, Presentation}`) on its own `MOMENT_IMPACT` moment id.

## THE camera packet shapes - written in blood, do not improvise a fourth combination

([`StationHoldController`](StationHoldController.java)`.applyCamera`): the working camera is
sent in the FIRST-PARTY packet shape ONLY - engage = `ClientCameraView.Custom` + a
fully-populated `ServerCameraSettings`, disable = `Custom` + `false` + `null`. NEVER send a
built-in view (`ThirdPerson`/`FirstPerson`) or locked+null-settings; that unexercised client path
correlated with a deterministic post-walk-off client `NullReferenceException` pre-extraction. The
`FaceBlock` fixed-camera recipe (`applyFaceBlockPreset`) only combines fields the THREE
first-party `ServerCameraSettings` senders in the shared source actually establish:
`movementForceRotationType=Custom` + `movementForceRotation` is necessary but NOT sufficient to
stop mouse-driven camera spin while STANDING STILL - that additionally needs
`rotationType=Custom` + a fixed `rotation` `Direction` + `mouseInputType=LookAtPlane` +
`planeNormal=(0,1,0)`. [`StationCameraPreset`](StationCameraPreset.java) is the surviving
experimentation enum (`FROZEN`/`FREE_NULL`/`FREE_DIR`/`LOOK_ROT`/`LOOK_ROT_BLEND`/
`LOOK_ROT_NO_TARGET`/`LOOK_ROT_ATTACHED`/`CUSTOM_SEED`), each a targeted field-diff experiment;
`FROZEN` is the full fixed-camera win. [`StationCameraPrefs`](StationCameraPrefs.java) is the
transient, never-persisted per-player override, set via `/rpgstations camera <preset>|list` (see
`../command/CLAUDE.md`). **Never invent a fourth `ServerCameraSettings` field combination beyond
what those three first-party sources establish.**

## The `Hold.Mount` knob family (unchanged by scope-2)

`StationAsset.Hold.Mount.Surface` is a union discriminator between `"Block"` (default) and
`"Entity"` - two structurally different engine mechanisms, not a mode.
- **`"Block"`** ([`StationMountController`](StationMountController.java)): mounts the player on
  the station block via native `BlockMountAPI.mountOnBlock` - the client renders its own
  free-orbit camera for a seated entity, and every OTHER viewer sees the seated player's facing
  from the seat's fixed geometry. Requires the station BLOCK to author `BlockType.Seats[]`
  (`{"Offset":{...},"Yaw":<degrees>}`). The `RPG_Station_Sawmill` block authors `Seats[].Yaw:
  180` because the engine adds a hard, unconditional +180deg in `BlockMountPoint
  .computeRotationEuler` (a first-party `// ?` comment). Movement lock: the Block route forces
  `movementLock = false` (`Hold.MovementLock`/`EffectId` are ignored while mounted this way).
- **`"Entity"`** ([`StationEntityMountController`](StationEntityMountController.java)) - the
  standing work mount (spawns a minimal anchor entity at the block center, attaches
  `MountedComponent` directly to the player, no interaction chain). Never populates the client's
  `MountedUpdate.Block`, so the player renders standing. `Hold.Mount.Entity.Offset {X,Y,Z}`
  converts to the constructor's `Rotation3f attachmentOffset` parameter (a native mislabeling -
  it is really a spatial offset, not a rotation). `Steerable` (default false) applies the SAME
  hold effect effect-mode uses plus a per-heartbeat `snapBack`; `DismountOnMove` (default true)
  runs the same origin-delta walk-off check effect-mode uses. Anchor lifecycle: session-scoped,
  despawned in the ONE idempotent `stop()` funnel via `CommandBuffer` (tick-safe from an
  interaction handler or the heartbeat frame drain). **A WORKING Entity mount renders NOTHING by
  design** (decision 62, source-traced: no model on the anchor, no pose packet -
  `MountedUpdate.Block == null` is what makes the client render default STANDING): the mount is a
  positioning/input-lock primitive; real station visuals come from `Camera`/`Animation`/`Puppet`
  authoring. `Entity.VisibleAnchorItemId` (nullable codec leaf) is the confirm-kit/diagnostic
  knob - the anchor wears a dropped-item-style marker body (prop-hygiene trio carried); the
  native `/mount check` command reads the attach live, and a client that silently REJECTS a
  server-attached mount has its whole movement packet dropped (`GamePacketHandler`'s mountedTo
  filter), observable as the worker's head-look freezing for other viewers.

## Seat/swing routing, the seated-worker fix (Block route only)

`useActionSlotForSwing` stays keyed to `seatMode`: a seat-mode session's swing does NOT re-fire
the work emote on the `Emote` slot (the sit pose wins over that slot's clip). `StationService
.runSwing`'s `useActionSlotForSwing(seatMode)` routes a seat-mode session through
`StationHoldController.playActionSwing` instead - fires the swing on `AnimationSlot.Action`
against the CURRENTLY HELD ITEM'S OWN `ItemPlayerAnimations` clip set, the exact mechanism
vanilla combat swings ride. The clip id is `StationAsset.Animation.ActionClip` when authored,
else `DEFAULT_ACTION_CLIP` (`"Chop"`) - deliberately NOT a cross-family default; a station gated
on a different tool family must author its own `ActionClip` or the swing plays nothing
(`ACTION_CLIP_WITHOUT_SWING` warns).

## Idle practice mode + tool durability drain (unchanged)

`StationAsset.Work.Idle` (opt-in, default OFF): a `NO_INPUTS` start proceeds into idle mode
instead of denying. Idle cycles grant fractional XP asks only (`PerCycle * XpFraction`,
multiplier forced to 1.0) with NO conversion, NO loot, marked `idle=true` on
`StationCycleCompletedEvent`. `StationAsset.Tool.Durability {PerSwing, PerCycle}` (both default
OFF): the mutation is native `ItemUtils.updateItemStackDurability`; a broken held stack
(`ItemStack.isBroken()`) stops the session (`TOOL_BROKEN`) and fires `StationToolBrokeEvent`.

## Exit hooks (unchanged)

Re-press F / crouch / walk-off (heartbeat), damage
([`StationInterruptDamageSystem`](StationInterruptDamageSystem.java), read-only, calls `stop`
only), death ([`StationDeathSystem`](StationDeathSystem.java) -> `stopForRef`), disconnect
(`RpgStationsPlugin`'s `PlayerDisconnectEvent` registration -> `stopFor`), world-change
(heartbeat store check), shutdown (`stopAll`). `stop()` is the ONE idempotent exit funnel: it
fires `StationSessionCompletedEvent` UNCONDITIONALLY (every stop, silent included).

## Placed-input custody + block states (unchanged mechanism; phase-based Consume this wave)

[`StationCustodyClaim`](StationCustodyClaim.java) is one block's live claim (owner uuid +
`itemId -> quantity` tally, insertion-ordered oldest-first, plus an optional `uniqueStack` for a
`MaxQuantity:1` placement that preserves metadata/durability - never persisted).
[`StationCustody`](StationCustody.java) is the PURE decision core (`placeableQuantity`,
`available`/`drain`, `matchesInput`/`matchesAnyConversionInput`). `toggle` gates a
`Custody`-governing action behind ONE state-dependent F: not-loaded + a matching held stack
places/tops-up (`placeIntoCustody`); loaded + non-owner denies `ui.station.occupied`; otherwise
falls through to the classic engage flow, sourcing viability from the claim
(`firstRunnableConversionFromCustody`). The implicit program's `Consume` phase reads
`From:"Custody"` whenever the resolved action authors `Custody` (`StationStepHandlers`'s Consume
phase, family-matched over an injected `itemId -> resourceTypeId[]` resolver, same pattern as
`StationToolScaling`). **Auto-return on every exit path**: `stop()`'s `returnCustody` call is
UNCONDITIONAL, resolving its store off `s.ref.getStore()` (not the possibly-null `store`
parameter) so `stopAll`'s shutdown sweep is covered too - returns to the owner's inventory
(room-checked, hotbar-first via `util.ItemGrantUtil`) or drops at the block once.
[`StationCustodyBreakSystem`](StationCustodyBreakSystem.java) covers the no-active-session case
(input placed, block broken before a session starts). Block-state flip (`flipCustodyState`,
`world.setBlockInteractionState`) is HINT-ONLY and self-heals: a Loaded state surviving a restart
with no live claim behind it resets to Empty on the next interaction. **Precedence rule (gate
m5)**: a block busy with its OWN session OR a non-empty `custodyByBlock` claim REFUSES an
incoming anchor claim; restart self-heal consults `custodyByBlock`, not just the session map -
load-bearing for `[wave 3]`'s multi-station claiming, already true today for the single-station
case.

## Anchor discovery: the DERIVED block-item seed (AV wave) + the two denial toasts

Anchor discovery resolves "is there a `cookingfire` near me" through the `blockItemId -> stationId`
index `stationBlockItemToId`. That index used to be LEARNED ONLY - `registerKnownStationBlock`
filled it from an actual F press, and nothing persists it - so on a COLD server (any restart, or a
world nobody has pressed F in yet) the ring scan resolved every scanned block to `null` and the
`PlaceBlockEvent` feed no-oped, and a `Walk` action denied "No Cooking Fire found within 12 blocks"
for old AND freshly placed fires alike. It is now DERIVED from the native assets, zero new
authoring (maintainer ruling):

- **`StationService#seedStationBlockIndexFromAssets()`** inverts how a station block is authored.
  Pass 1 walks the `RootInteraction` asset map, and for each root interaction resolves its
  `getInteractionIds()` through the `Interaction` asset map, collecting `rootInteractionId ->
  stationId` for every entry the engine decoded into this mod's own `StationUseInteraction` (its
  `Station` leaf is a real codec field, so this is an `instanceof` read, never a JSON re-parse - the
  new `StationUseInteraction#getStationId()` is the accessor). Pass 2 walks the `BlockType` asset map
  once, pairing each block's `getItem()` id (the SAME accessor `blockItemIdAt` reads back in the
  world, so state variants fold onto their base item by construction) with the RootInteraction its
  `Interactions.Use` names. `StationAnchors#deriveBlockItemIndex` is the PURE join (case-insensitive
  both sides, lowercased output, first-wins, blanks skipped; unit-tested).
- **Two call sites, both idempotent**: the `StationAsset` `LoadedAssetsEvent` fold, and once more at
  the first `PlayerReadyEvent` immediately before `StationValidator.runAndLog()` (a native
  Item/BlockType layer from a later pack can settle AFTER the station fold fires - the same timing
  reason the FULL validator pass is deferred there). Try-guarded at both altitudes: a malformed
  entry skips with one warn, a total failure logs, nothing ever throws into the fold.
- The F-press learning and the `PlaceBlockEvent` feed STAY as harmless redundancy (they re-write an
  identical entry once the derivation already covered a block).
- **Validator**: `ANCHOR_STATION_NOT_DISCOVERABLE` (warn-only) rides beside
  `ANCHOR_STATION_UNKNOWN` - an `Anchors[].Station` naming a station that EXISTS but that no block
  item maps to is undiscoverable until a player interacts with such a block.
  `StationValidator#stationDiscoverableLive` fails OPEN on an EMPTY index (unseeded fold /
  unit JVM), exactly as `benchIdKnownLive` does for a cold Item map.

**Two failures, two toasts** (they shared one before): `ui.station.anchor_missing` ("No {0} found
within {1} blocks") is the DISCOVERY miss; `ui.station.anchor_unreachable` ("The {0} nearby cannot
be reached") is the walk-targeted anchor whose engage-time `PuppetNav.solve` fails - the block was
found, it just cannot be pathed to. Both denials roll back every partial claim.

## The ACTIVELY-WORKING block state (`Custody.States.Working`, AV wave)

The maintainer ruling is **"Lit = actively cooking"**: a station block's working look (the cooking
fire's flames) must be on ONLY while work is genuinely running there, never merely because input
was placed. That is `asset.Custody.States`' third nullable leaf (`../asset/CLAUDE.md`), driven by
two package-private seams on `StationService`:

- **`enterWorkingState(session, anchorId)`** resolves the anchor through the SAME
  `anchorBlockKeyFor` the step phases use, so ONE call covers both altitudes: the PRIMARY block
  (absent/`"self"`) and a CLAIMED REMOTE ANCHOR. It is IDEMPOTENT per block (re-entering the same
  block never re-writes the state, so a repeating single-step convert program holds a steady look
  instead of flickering once per cycle) and exits any previously-working block first, so at most
  one block per player is ever left working (`workingByPlayer`, a transient `UUID -> WorkingFlip`
  map, never persisted).
- **`exitWorkingState(session)`** returns the block to `Loaded` (a claim still stands there) or
  `Empty` (it does not). Idempotent, so every "work is no longer running" moment can call it
  freely.

**Which steps count as work** is `StationStep.isWorkingStep()` (derived default: a
`Consume`+`Produce` atomic-transform CONVERT is work, everything else is not; an authored
`"Working"` boolean overrides either way). **Flip sites, the complete set:**

1. `toggle`'s engage, for a CLASSIC (non-`Steps`, non-idle) session - the implicit program has no
   authored step to light on entry and its first conversion only commits a full `CycleMs` later,
   but that whole `CycleMs` IS the work, so it lights from engage rather than a cycle late.
2. `StationStepHandlers.runIterations`, per iteration, POST-walk: a working step enters at its
   `At` anchor, any other step exits.
3. `runIterations` again, at a `Walk` phase's departure (dark while travelling, per the ruling).
4. `stop()`, unconditionally, AFTER `releaseAnchorClaims` + `returnCustody` so the Loaded-vs-Empty
   read sees the post-return truth. This ONE call is what covers every stop reason with no
   per-reason hook - `RITUAL_COMPLETE`, `INPUTS_EXHAUSTED`, `ANCHOR_LOST`, `PATH_BLOCKED`,
   `STEP_FAILED`, `TOOL_CHANGED`, damage, death, disconnect, world change, shutdown - because a
   failing step program reaches `stop()` through `dispatchProgram`'s `Failed` branch.

`releaseAnchorClaims` also now resets each released anchor's block state to Empty (mirroring
`returnCustody`'s long-standing flip for the primary block, and skipped when a FOREIGN claim still
stands there): a program can hand an anchor its Loaded look and harvest it empty several steps
later, which without this would strand a "has input" hint over nothing.

The raw write is the extracted `setBlockState` (one guard set, returns whether the write landed);
`flipCustodyState` is now a thin Empty/Loaded wrapper over it. A disconnect/shutdown stop has no
world to write through, so the block can be left wearing its Working look - EXACTLY the
pre-existing restart-orphan story for Loaded, self-healed by `toggle`'s not-loaded reset.

**Crackle + embers are NATIVE, zero engine work.** `BlockType` carries a per-state
`AmbientSoundEventId` (LOOPING+MONO validated, "a looping ambient sound event that emits from this
block when placed") and per-state `Particles`; both start and STOP automatically with the
`setBlockInteractionState` flip, which matters because nothing in the protocol can stop a playing
sound or particle system. The jar's `RPG_Station_CookingFire` block's `Lit` state copies vanilla
`Furniture_Crude_Brazier` verbatim for this. Corollary for step `Presentation.Sound`: only ever
author a ONE-SHOT SoundEvent there - a looping id fired as a one-shot never ends.

## The placed-input PLACED-AS-ENTITY visual (unchanged)

[`StationCustodyDisplay`](StationCustodyDisplay.java) spawns a static, network-replicated,
pickup-immune, physics-free prop entity rendering the claim's placed item at the station's
block-top anchor, gated on `asset.Custody.Display`. Block-shaped items (the sawmill's placed
logs) spawn a real `BlockEntity`; everything else (the anvil's placed weapon) spawns a bare
`ItemComponent` prop. Both routes `ensureComponent(EntityStore.REGISTRY
.getNonSerializedComponentType())` - never survives a restart, matching the custody claim's own
lifecycle. Ref lives ON the claim (`StationCustodyClaim#displayRef`); spawned once at first
placement, despawned at whichever of `#returnCustody`/`#onCustodyBlockBroken` fires first.
`Offset`/`Rotation` are FACING-RELATIVE to the placed block's own yaw (`#blockYawRadians` reads
`World#getBlockRotationIndex`, try-guarded to yaw 0 on any failure) - see `../asset/CLAUDE.md`'s
`Custody.Display` bullet for the full authoring convention. Press-F RETRIEVAL
([`StationCustodyRetrieval`](StationCustodyRetrieval.java)) resolves the clicked display entity's
`NetworkId` back to its owning block key and routes eligibility through the pure `decide`
(precedence: `UNKNOWN_TARGET` -> `BUSY` -> `NOT_OWNER` -> `NOTHING_TO_RETRIEVE` -> `RETRIEVE` -
a session actively working the block always wins over ownership checks).

## The anvil arc - the Stamp step + roll/cap engine (mechanism unchanged; Caps reshaped)

[`StampCapEngine`](StampCapEngine.java) (pure, unit-tested via `StampCapEngineTest`) is called
ONLY from `StationStepHandlers`'s Stamp phase handler: compute-then-commit (roll + weighted-pick/
`Picks`/`Unique` + cap-clamp validated with ZERO mutation first, then reagent consumption and the
weapon mutation each run under their OWN try/catch that restores exactly what was consumed on
failure - `claim.setUniqueStack` is the LAST line, reached only on full success). **Caps
composition is re-anchored on the scope-2 `Budgets[]` shape** (`../asset/CLAUDE.md`'s Stamp
bullet: MIN over every `Budget` entry, `PerStat` layered on top, `Economics` unchanged) - the
engine's MIN-composition RULE is identical to pre-scope-2, only the authoring shape (`Budgets`
replacing `PerItemBudget`/`SkillScaledBudget`) changed; `StampCapEngineTest`'s fixtures were
re-anchored on the new shape. `ActionResolver.selectActionByFamily` (a DIFFERENT NAME from
`selectAction`, never an overload) is the resource-type-FAMILY-aware selection entry
`StationService` calls from `selectActionForHeld`/`liveFunctionOf`. `StationCatalog` carries an
action-aware `resolvedConversions(asset, actionId, actionRecipe)` overload. `StationService
.dispatchProgram` reads the resolved action's `Work.effectiveRepeat()` and calls
`stop(..., StopReason.RITUAL_COMPLETE, ...)` on a completed non-repeating program; a
non-repeating authored Steps program (e.g. the anvil's Enhance) gets INSTANT first dispatch
(`s.nextCycleAtMs = now`, no `CycleMs` latency eaten before the ritual's only cycle).
**Enhancement outcome reporting** (`StationEnhanceOutcome` on `StationSession
#enhanceOutcomes`, `StationEvents#fireEnhanceCompleted`, `StationService#enhanceLedgerRows`) is
MMO-agnostic - the stamper's `List<EnhanceLine>` renders verbatim, plus one engine-owned
`Durability +N` row, so a bare anvil with no registered stamper still reports its durability
enhancement. See `../api/CLAUDE.md`'s `EnhanceStamperRegistry` entry for the api contract and
`content-packs/skill-stations-pack/CLAUDE.md` for the shipped Anvil content.

## The puppet presentation engine (unchanged)

[`StationPuppetController`](StationPuppetController.java) drives ONE `ziggfreed-common`
`entity.performer.StationPerformer` (seam wave decision 47/48/55) for "mount the player, hide their
player model, and spawn/display a visual of their character model performing the steps" - sibling
to `StationEntityMountController`/`StationHoldController`. **The performer swap (decision 55):** at
engage `spawnAndHide` reads `Puppet.Look.Source` (`resolveLook`) and picks the backend
(`createBackend`): `PlayerClone`/`Model` -> `HolderPerformer` (the crowned bare-Holder puppet,
byte-parity with the pre-swap route), `NpcRole` -> `NpcRolePerformer` with an engage-time
FAIL-CLOSED fallback to the Holder (one warn) when the role id is blank/unregistered. The performer
lives on `StationSession.performer`; every later mutation threads a FRESH per-call accessor
(clip/loop/step-clip via the live `store` = a network packet; `setProp`/despawn via the tick-safe
`commandBuffer` = a Hotbar/entity mutation - the exact pre-swap split, so a `CommandBuffer` is never
captured across frames). `s.puppetRef` is RE-POINTED at `performer.ref()` every frame by
`StationPuppetController#refreshPuppetRef` (F2 part a, called from `tickFrameOnce`), covering the
`NpcRole` backend's one-tick deferred-spawn window (its `ref()` is null at engage, non-null once the
deferred spawn lands) so the direct-puppetRef readers (the `storeFor` store fallback, custody
retrieval) never go stale. The `Walk` phase itself drives the performer seam
(`s.performer.walkTo`/`WalkHandle`, F2 part b - `StationService#resolveWalkTarget` supplies the anchor
target, the backend re-solves the path), not the raw ref. Identity/reconcile: `RpgStationsPlugin` registers
`PerformerIdentityComponent` at setup + a once-per-world `PerformerReconciler.sweep(bootDespawnAll)`
at first ready (`StationService.reconcilePerformersAtBoot`); `toggle` fires a deferred `engageStale`
sweep (`reconcileStalePerformersAtEngage`, via `world.execute` so the native sweep runs outside the
processing lock). **Legacy mechanics carry over below.** **Spawn + hide, at engage**
(`spawnAndHide`, called from `toggle` AFTER the mount-attach block): resolves `Puppet.Offset`/
`Yaw` off the block-top anchor + the initial `Puppet.Prop`, spawns via `PlayerPuppetService
.spawn`; a null spawn is non-fatal (session continues in-body). Only `Hide.Route:"Scale"`
actually hides (`hideByScale`/`revealByScale`); `"Effect"`/`"None"` apply no hide. **Reveal +
despawn** happens in the ONE idempotent `stop()` funnel (`revealAndDespawn`, right after
`returnCustody`), resolving its own store so a disconnect/shutdown stop still reveals + despawns.
**Animation routing**: a puppet-active session's engage-time loop and per-swing beat BOTH fire on
the puppet's `Emote` slot, superseding `useActionSlotForSwing`/`playActionSwing` entirely for
that session. **Per-step sync**: a `StationStep.Puppet.Clip` plays once at step ITERATION ENTRY
(`StationStepDecisions.shouldPlayClipOnEntry`, resume-safe via `resumingStep` identity); the
generic engage/swing clip is suppressed for a stepped program whose steps author any clip
(`StationSession.stepProgramAuthorsClip`); a `StationStep.Puppet.Prop` override syncs at the SAME
step-entry point (`shouldSyncPropOnEntry`) and is NOT gated on the step authoring an override -
every fresh step entry syncs to that step's effective prop (its override, else the session
default), so moving past a prop-overriding step reverts the prop. **All mutation through this
engine runs via `CommandBuffer`, never a raw `store` mutation** - `hideByScale`/`revealByScale`/
`despawn`/the `Hotbar` component swap all throw `IllegalStateException("Store is currently
processing!")` if called on the live store from inside `toggle()`/the heartbeat frame drain (both
run inside the store's processing lock); route every new call site through the `CommandBuffer`
parameter these methods already thread, never add a raw-`store` mutation here. **Safety net**:
`reassertOnReady` unconditionally clears any lingering `EntityScaleComponent` and restores the
correct cosmetic model on every fresh `PlayerReadyEvent` (a restart wipes every in-memory session
by construction) - not gated on any remembered session.

## Loot + flairs, the open vocabulary (unchanged mechanism; `LootRef` terminology)

[`StationFlairs`](StationFlairs.java) resolves the per-player cosmetic overlay for a moment id
against the UNION of every registered api `FlairUnlockProvider` (the MMO's `StationComponent`-
backed provider is the only one registered today). The open STRING moment id vocabulary
(`MOMENT_CYCLE`/`MOMENT_SWING`/`MOMENT_IMPACT`/`MOMENT_RARE_FIND`/`MOMENT_COMPLETION`, plus
`stepMomentId(actionId, stepId)`) is unchanged. The flair map is the merge of TWO sources
([`FlairCatalog`](FlairCatalog.java)`.effectiveFlairsFor`): a station's own inline `Flairs`
(`asset.StationAsset.Flair`, `{Moments}`) UNIONED with every folded `asset.FlairAsset` whose
`Stations` list applies - a same-flair-id `FlairAsset` entry wins. `api.impl.StationViewImpl
.flairIds()` and `StationCatalog.allFlairIds()` both reuse the SAME merge point. See
`../loot/CLAUDE.md` for the `LootRef`/`Roll` evaluation engine this package calls into per cycle
and per step `Roll` phase (both routes are the SAME `loot.LootEngine` call - one roll engine,
whether the source is a station's implicit cycle or an authored step's `Roll` phase).

## Engine settings + Validation (unchanged mechanism; new checks)

[`SettingsCatalog`](SettingsCatalog.java) holds the folded `asset.RpgStationsSettingsAsset`
singleton. [`StationValidator`](StationValidator.java) keeps its two-pass structure and warn-only
posture: `validateStructural()`/`runStructuralAndLog()` runs at EVERY asset-load fold (every
check except cross-layer reference-existence ones); `validate()`/`runAndLog()` (the FULL set)
runs ONCE post-load from the first `PlayerReadyEvent` and on demand from `/rpgstations validate`.
The lang-key check (`langKeyKnownLive`) is a MERGED-view check: a miss against the jar's own
`i18n.RpgStationsLangKeys` falls through to a LIVE `I18nModule.getMessage` query, so a pack's own
additive `rpgstations.lang` overlay resolves correctly. **New scope-2 checks**:
`ACTION_REF_UNKNOWN`, `EXTENSION_TARGET_UNKNOWN`, `EXTENSION_PAYLOAD_MISMATCH`,
`EXTENSION_KEY_COLLISION`, `EXTENSION_ANCHOR_MISSING`, `EXTENSION_STEP_MISSING_ID`,
`ANCHOR_STATION_UNKNOWN`, `ANCHOR_STATION_NOT_DISCOVERABLE` (AV wave - see the discovery-seed
section above), `WALK_REQUIRES_PUPPET`, and the `[wave 3]` boundary marker
(`WAVE3_PENDING`-style, one finding per step authoring `Walk`/`At`/`Produce.To:Custody`).
**Dropped checks** (their reserved fields no longer exist): `UNIMPLEMENTED_STEP_TYPE`,
`UNIMPLEMENTED_CONSUME_SOURCE`, `UNIMPLEMENTED_PRODUCE_DEST`, `WAIT_BOTH_ROUTES`,
`UNIMPLEMENTED_WAIT_BEATS`. The pure `validate(...)` core is unit-tested.

## Landed fix history (still-true warnings only; condensed)

Several maintainer-smoke-driven fix rounds landed across phase 1/2 (extraction through the anvil
arc, the puppet presentation build, and a round-8 facing-relative/step-sync pass). The narrative
detail lives in git history and `../../../../../../.claude/plans/work-stations-mod-extraction-prompt.md`;
the load-bearing lessons that still apply going forward:

- **Every ECS mutation from inside an interaction handler or the heartbeat frame drain must go
  through a `CommandBuffer`**, never `store.addEntity`/`removeEntity`/`putComponent` directly -
  both run inside the store's processing lock and throw `IllegalStateException("Store is
  currently processing!")` otherwise (this bit the custody display entity, the entity-mount
  anchor, and three puppet-controller call sites independently; the fix is always the same
  thread-a-`CommandBuffer`-parameter shape).
- **A spawned network entity needs an explicit `NetworkId` component** unless the engine
  auto-ensures one for you (only `MinecartComponent` gets that for free via
  `MountSystems.EnsureMinecartComponents`) - a plugin-spawned anchor/prop entity that skips it
  renders invisibly to every viewer including the entity's own controller.
- **A block's item id at engage-time is `BlockType#getItem()`, never raw `BlockType#getId()`** -
  a state-variant block (e.g. `Loaded`) decodes to a distinct generated id
  (`*RPG_Station_Sawmill_Loaded`) that is not a real item id; `getItem()` resolves the base item
  through the containment chain regardless of which state the block is currently in.
  `StationService#blockItemIdAt` falls back to `getId()` only when the block has no containing
  Item at all.
- **The same rule binds the BLOCK-GONE check: compare by ITEM id, never by raw block id.**
  `setBlockInteractionState` does not annotate a block, it REPLACES it - `BlockAccessor
  #setBlockInteractionState` resolves `blockType.getBlockForState(state)` and calls `setBlock(...,
  BlockType.getAssetMap().getIndex(newState.getId()), ...)`, so the int `World#getBlock` returns
  changes on EVERY `Custody.States` flip this engine performs. A raw-int compare against the
  engage-time snapshot therefore reads the engine's own `Empty`/`Loaded`/`Working` flip as "the
  station is gone" (the round-2 smoke regression: the cooking fire's own session died at its first
  1s heartbeat the moment engage lit it). The heartbeat now runs the pure
  `StationAnchors#blockGone(startBlockItemId, currentBlockItemId, startBlockId, currentBlockId)`:
  item-id compare (case-insensitive, null current = gone) when the session captured one at engage
  (`StationSession#startBlockItemId`, resolved ONCE and shared with the summary crest), raw-int
  fallback only for a block with no containing Item. This covers the latent twin by construction -
  a `StationStepHandlers` working-step flip at `At: "self"` writes the SAME primary block through
  the SAME check.
- **A restart-orphaned `Loaded` block state with no live claim behind it must recover, not
  dead-end.** `ActionResolver#selectActionForBlockState(asset, currentStateName)` is the THIRD
  action-selection fallback (after the live claim and the held item) - it matches the block's
  persisted interaction-state name against each action's `Custody.States.Loaded` name so a
  correct-tool press after a restart re-enters the existing not-loaded self-heal instead of
  denying `ui.station.no_action` forever.
- **A one-shot puppet/display spawn call ordering matters**: seed any render-guaranteed initial
  component (e.g. an `ActiveAnimationComponent`) using data already assigned BEFORE the spawn
  call, not after - a viewer who starts tracking the entity between the spawn and the later
  assignment sees it frozen with no initial pose.

Deprecation discipline: this package (plus `StationStepHandlers`/`StationHoldController`/
`interaction.StationUseInteraction`) is swept clean of deprecated API calls
(`util.InventoryAccess` DRYs the non-deprecated replacements) - keep it that way per the root
CLAUDE.md's never-call-a-deprecated-API edict; the shared source's deprecation javadoc always
names the current replacement.
