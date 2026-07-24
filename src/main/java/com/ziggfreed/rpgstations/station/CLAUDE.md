# station/ - the session engine (interactive work stations)

Router for `station/`, THE big package in this mod: the diegetic work-loop session machine. Press
F on a station block -> camera pulls third-person (or the player mounts the block as a seat, or a
puppet spawns and performs the work), the work animation plays per swing, items convert per cycle
or per an authored step program, loot rolls through `loot/`, and skill-XP declarations forward as
`XpAsk`s any listening progression mod interprets. Design authority:
`../../../../../../.claude/research/raw/rpg-stations-scope2-unified-design-2026-07-23.md`
(sections 2-3, decisions 33-41 in `../../../../../../.claude/research/rpg-stations-extraction-design.md`),
superseding the phase-1/phase-2 design for everything the scope-2 redesign touches.

## WAVE BOUNDARY (read first)

The scope-2 schema (`../asset/CLAUDE.md`) carries the FULL multi-station field set, but this
engine executes only a SUBSET of it this wave:

- **Executes now**: `Consume`/`Stamp`/`Produce` (`To:"Inventory"` only)/`Roll`/`Commands`/
  `Duration`/`Repeat`, all at the PRIMARY station (`At` absent). Placed-input custody
  (single-station), the anvil's Stamp ritual, the sawmill's implicit convert loop, camera/mount/
  puppet presentation, exit hooks, validation - all unchanged and fully live.
- **`[wave 3]` - decodes and validates, does NOT execute**: `StationStep.Walk` (puppet travel to
  an anchor), `StationStep.At` (running a step at a non-primary anchor), `Produce.To:"Custody"`
  (cross-station output), `ActionDef.Anchors` DISCOVERY/CLAIMING (the codec's `Anchors` map
  decodes and the validator checks `Station` ids exist, but no engine code resolves a placed
  block or claims one). A step authoring any of these (`StationStep.authorsWave3OnlyPhase()`)
  draws a `WAVE3_PENDING`-style validator WARN at load and denies engage gracefully with a
  localized toast - never a crash, never a partial program run. **No shipped wave-2 content
  authors any of them.**

## Content + catalogs

- **Stations**: [`asset.StationAsset`](../asset/CLAUDE.md) (`Server/RpgStations/Stations/*.json`)
  folds into [`StationCatalog`](StationCatalog.java). Ids are lowercase (canonicalized at
  decode). This jar ships its OWN default Sawmill (`Server/RpgStations/Stations/Sawmill.json`,
  standalone-playable with the built-in `rpgstations:` factors + `SawmillFinds` lootable); the
  `skill-stations-pack` adds its own luck-tier lootable as an ADDITIVE `Extensions/
  SawmillProgression.json` (below) rather than a full-file override (it does NOT re-author the
  jar's Woodcutting/Crafting Xp - the jar base is the single Xp authority; an extension appends a
  genuinely-new skill, never re-adds one the base already grants, A8 review M1) - see
  `../../../../../../CONTENT_PACKS.md`'s Station authoring section for the authoring guide (brief
  reference only; do not duplicate it here).
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
INFO `EXTENSION_APPLIED` summary line per target ("station sawmill: +1 lootable from
sawmillprogression") so a server owner can see the composed result.

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
  interaction handler or the heartbeat frame drain).

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

[`StationPuppetController`](StationPuppetController.java) is the policy-thin glue over
`ziggfreed-common`'s `entity.PlayerPuppetService`/`PlayerModelService` for "mount the player, hide
their player model, and spawn/display a visual of their character model performing the steps" -
sibling to `StationEntityMountController`/`StationHoldController`. **Spawn + hide, at engage**
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
`ANCHOR_STATION_UNKNOWN`, `WALK_REQUIRES_PUPPET`, and the `[wave 3]` boundary marker
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
