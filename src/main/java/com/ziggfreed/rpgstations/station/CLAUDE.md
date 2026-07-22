# station/ - the session engine (interactive work stations)

Router for `station/`, THE big package in this mod: the diegetic work-loop session machine, moved
verbatim out of the MMO Skill Tree jar (phase 1 leg 2) with progression severed to the api event/
registry surface. Press F on a station block -> camera pulls third-person (or the player mounts
the block as a seat), the work animation plays per swing, items convert per cycle, loot rolls
through `loot/`, and skill-XP declarations forward as `XpAsk`s any listening progression mod
interprets. Design authority: `../../../../../../.claude/research/raw/rpg-stations-unified-design-2026-07-21.md`
sections 4.1 (file-by-file move) and 4.4 (schema). This file carries forward the HARD-WON engine
rules from the pre-extraction MMO `station/CLAUDE.md` (git history:
`git log --all -- src/main/java/com/ziggfreed/mmoskilltree/station/CLAUDE.md` in the hyMMO root,
commit `80f542c` is the last pre-extraction snapshot) - read them before touching camera, tool
gating, or the moment-playback choke point. They are load-bearing, not decorative.

- **Content**: [`asset/StationAsset`](../asset/CLAUDE.md) (Pattern A codec, `Server/RpgStations/Stations/*.json` -
  the design's leg-2 store-path change from the MMO's old `Server/MMOSkillTree/Stations`) folded
  into [`StationCatalog`](StationCatalog.java) via `AssetStoreRegistrar` + a `LoadedAssetsEvent`
  handler in `RpgStationsPlugin`. Ids are LOWERCASE (canonicalized at decode). **This jar ships
  its OWN default Sawmill** (`Server/RpgStations/Stations/Sawmill.json`, jar-resident, standalone-
  playable with the built-in `rpgstations:` factors + `SawmillFinds` lootable) - unlike the
  pre-extraction MMO package, this is NOT an engine-only jar. The `skill-stations-pack` overrides
  this same station id (`sawmill`) with an MMO-bridged, luck-tiered version via `defaults < pack`
  load order - see `../../../../../../CONTENT_PACKS.md`'s Station authoring section. Do not
  duplicate that authoring guide here.
- **Sessions**: [`StationService`](StationService.java) (1583 lines, the biggest class in the mod)
  owns the `IDLE -> STARTING -> WORKING -> STOPPING` machine over transient, player-anchored
  [`StationSession`](StationSession.java)s (never persisted - no per-player state lives in this
  mod, by construction). One entry (`toggle`, from `interaction/StationUseInteraction`'s
  object-form `Station` param), one idempotent exit funnel (`stop`), every start-denial a
  localized toast. Sessions bucket per world in a `WorldKeyedQueues` (ziggfreed-common) drained by
  [`StationFrameSystem`](StationFrameSystem.java) `extends AbstractWorldFrameSystem` (ECS systems
  are class-keyed - this is RpgStations' OWN concrete subclass, not a shared one with the MMO).
- **Held-tool gate** (`StationAsset.Tool`, checked at start AND per heartbeat -> `TOOL_CHANGED`
  stop, `heldToolMatches`): the player must HOLD a matching tool. Three NATIVE routes, match = ANY
  (null/no-live-route group = ungated): `Tags` = the native item-tag object map intersected
  case-insensitively with the held item's raw tags - the forward-native route; `Gather` = the
  FUNCTIONAL test over the held item's `ItemToolSpec.getGatherType()/getPower()`, the engine's
  real definition of a hatchet/pickaxe; `Ids` = the FALLBACK for modded items, exact id OR
  case-insensitive underscore-segment match. Diegetic RP AND load-bearing for client stability:
  the work emote NEVER sets `HideItemInHand` (that equipment machinery correlated with a client
  NullReferenceException in the pre-extraction engine's smoke testing). Cycle consume also prefers
  BACKPACK storage over the combined view for the same reason (hotbar mutation fans an Equipment
  update to every viewer, self included, under the server camera).
- **THE `ItemToolSpec` construction trap** ([`StationToolScaling`](StationToolScaling.java)):
  `heldPowerFor` takes an injected `ToolPower(gatherType, power)` value shape rather than the live
  `ItemToolSpec` directly, because merely CONSTRUCTING a real `ItemToolSpec` triggers its
  `AssetBuilderCodec` static init, which THROWS outside a running Hytale server - the same trap
  [`StationRecipeDeriver`](StationRecipeDeriver.java)'s `CraftingCandidate` shape avoids.
  `StationService` adapts the live specs at the ONE call site that has a running server; every
  pure/unit-tested core in this package takes the adapted shape, never the raw engine type. If you
  add a new pure-tested helper that reads tool data, follow this pattern - do not construct
  `ItemToolSpec` (or any other `AssetBuilderCodec`-backed engine type) in code that must run in a
  unit JVM.
- **Tool-power XP scaling** (`StationAsset.Tool.XpScale`): `multiplier()` =
  `clamp((heldPower/ReferencePower)^Exponent, MinMult, MaxMult)` (defaults `Exponent 1.0`/
  `MinMult 0.5`/`MaxMult 2.0`), read fresh every cycle off the currently-held item (no snapshot),
  neutral 1.0 for a null/inactive scale or a held tool with no matching spec (never a penalty for
  missing data). The multiplier forwards on `StationCycleCompletedEvent.toolMultiplier` - the
  engine computes it, a progression listener decides what to do with it.
- **Recipe ingredients are NATIVE-shaped** (mirroring vanilla `MaterialQuantity`):
  `Conversion.Input` is `{"ItemId"|"ResourceTypeId", "Quantity"}` (exactly one of the two -
  `ResourceTypeId` is a native `Item.ResourceTypes` FAMILY, e.g. `Wood_Hardwood_Trunk` = any
  hardwood log); `Conversion.Output` is always an exact `{"ItemId","Quantity"}`. `ItemResourceType`
  exposes its id as a PUBLIC FIELD `.id` (NO `getId()` - a protocol class quirk).
- **`Recipe.FromCrafting` = derive-from-native, zero hardcoded conversions**
  ([`StationRecipeDeriver`](StationRecipeDeriver.java)): `{"Categories": String[],
  "OutputPerInput"?: Integer}` derives one Conversion per LIVE `Item` whose native
  `Recipe.BenchRequirement[].Categories` intersects `Categories` and whose native recipe has
  EXACTLY ONE input. The shipped Sawmill is just `{"FromCrafting":{"Categories":["WoodPlanks"]}}` -
  it reproduces all 11 wood-plank families with zero authored conversions. `BenchRequirement`
  fields are public (protocol class, no getter, like `ItemResourceType.id`); items are read via
  `item.collectRecipesToGenerate(list)`. The PURE core (`resolve`/`deriveFromCrafting`) takes
  injected `CraftingCandidate`s so it is unit-tested without a live item map.
- **Cadence**: 1000ms heartbeat (terminate checks: ref/store validity, block-gone, walk-off
  `MaxMoveMeters`, crouch exit, held-tool still matching, `MaxDurationMs` cap, the engine-toggle
  check via `SettingsCatalog`; then hold TTL refresh) + per-`Work.CycleMs` cycle (Convert
  transaction with output-room PRE-check before consume - zero item loss; then loot rolls via
  `loot/LootEngine`; then `StationEvents.fireCycleCompleted`; then the cycle `Presentation` at the
  block via `emitMoment`).
- **THE `emitMoment` choke point, and its 4-second particle cap**: `emitMoment(store, s, slot,
  presentation, targetPos)` in `StationService` is the ONE presentation-playback funnel every
  station moment goes through (`Slot.CYCLE` for the real/idle cycle, `Slot.SWING` for the
  per-swing cue and its delayed impact, `Slot.RARE_FIND` for a loot-tier flourish, `Slot.COMPLETION`
  for session-end) - it is ALSO the flair-resolution choke point (`StationFlairs.resolve`, the
  per-player unlock overlay). `targetPos` is a plain per-call-site argument, never a fork:
  cycle/swing/impact/rare-find pass the block center, `COMPLETION` passes the player's own
  position (a rare-find/completion moment celebrating the player, not the furniture). **Every
  particle spawned here is capped to `MOMENT_PARTICLE_MAX_DURATION_SECONDS` (4.0f) via
  ziggfreed-common's `ModelParticleService`'s duration-capped `spawnAt` overload** - this exists
  because at least one shipped particle asset (`Block_Gem_Sparks`) authors an UNBOUNDED spawner
  (`TotalParticles < 0`, the engine's sentinel for a persistent per-entity VFX) that, fired at a
  bare position with no duration cap, never stops spawning. If you add a new moment call site,
  route it through `emitMoment` - do not call `ModelParticleService` directly, or you lose both
  the flair overlay AND the leak guard. This bug was found in-game (a station's completion
  particles leaked forever at the block); do not reintroduce it.
- **Per-swing cadence** (`StationAsset.Animation.Swing`, its OWN `Presentation`): an independent
  server-side timer fires a swing SFX/VFX cue, TOGETHER with a one-shot re-fire of the work
  animation - clip + sound + particles land as one moment, 1:1 with the swing. **The work emote
  must NOT loop client-side** by convention: a station whose emote is authored LOOPING
  (`IsLooping:true`) with no `Swing` group behaves exactly as before (client loops it on its own,
  zero re-fires); a NON-looping emote needs an authored `Swing.IntervalMs` to keep animating at
  all - there is no engine-side lookup of `IsLooping`, the asset author owns the clip/cadence
  pairing (the shipped Sawmill's `933ms` is 4x its emote clip's `233ms`). `runSwing` picks the
  animation ROUTE via the pure `useActionSlotForSwing(seatMode)` decision - see the seat/swing
  routing bullet below. `scheduleImpactAt`/`impactDue` schedule an optional DELAYED impact cue
  (`Swing.Impact.{DelayMs, Presentation}`) that fires through the SAME `emitMoment`/`Slot.SWING`
  choke point on a later frame - a flair overlay of the swing moment overlays the delayed impact
  too, no third slot invented.
- **THE camera packet shapes - written in blood, do not improvise a fourth combination**
  ([`StationHoldController`](StationHoldController.java)`.applyCamera`): the working camera is
  sent in the FIRST-PARTY packet shape ONLY - engage = `ClientCameraView.Custom` + a
  fully-populated `ServerCameraSettings`, disable = `Custom` + `false` + `null`. NEVER send a
  built-in view (`ThirdPerson`/`FirstPerson`) or locked+null-settings; that unexercised client
  path correlated with a deterministic post-walk-off client `NullReferenceException` in the
  pre-extraction engine. **The `FaceBlock` fixed-camera recipe** (`applyFaceBlockPreset`) only
  combines fields the THREE first-party `ServerCameraSettings` senders in the shared source
  (`PlayerCameraTopdownCommand`/`PlayerCameraSideScrollerCommand`/`CameraDemo`, all under
  `HytaleServer/CoreServer/.../command/commands/player/camera/`) actually establish:
  `movementForceRotationType=Custom` + `movementForceRotation` (a `Direction` yaw, freezes
  rotation while MOVING only) is necessary but NOT sufficient to stop mouse-driven camera spin
  while STANDING STILL - that additionally needs `rotationType=Custom` + a fixed `rotation`
  `Direction` + `mouseInputType=LookAtPlane` + `planeNormal=(0,1,0)` (the camera's OWN static
  orientation, as opposed to `movementForceRotation`, which every first-party sender leaves
  `null`). `FaceBlock` therefore implies a genuinely FIXED work camera (no mouse look-around) -
  the accepted tradeoff for a real facing hold. [`StationCameraPreset`](StationCameraPreset.java)
  is the surviving experimentation enum from the round-2 packet hunt (`FROZEN`/`FREE_NULL`/
  `FREE_DIR`/`LOOK_ROT`/`LOOK_ROT_BLEND`/`LOOK_ROT_NO_TARGET`/`LOOK_ROT_ATTACHED`/`CUSTOM_SEED`,
  8 presets, each a targeted field-diff experiment - see its own javadoc for the confirmed
  results: `LOOK_ROT` is the first recipe that holds the body but pins the camera too; `FROZEN`
  is the full fixed-camera win described above; `FREE_NULL`/`FREE_DIR` keep the camera free but
  fail to hold the body). [`StationCameraPrefs`](StationCameraPrefs.java) is the transient,
  never-persisted per-player override, set via [`/rpgstations camera <preset>|list`](../command/CLAUDE.md)
  (leg P0, phase-1 closeout) - `StationCameraPreset.resolve` consults it. **Never invent a fourth
  `ServerCameraSettings` field combination beyond what those three first-party sources establish.**
- **The `Hold.Mount` knob family, the camera-problem's real answer** (design section 9.2, phase 2
  leg D; REPLACES the phase-1 `Hold.Seat.Enabled` flag - unreleased rename, no back-compat alias,
  the pack's own copy of the sawmill moved in lockstep): `StationAsset.Hold.Mount.Surface` is a
  UNION DISCRIMINATOR between `"Block"` and `"Entity"` - **critique m3's bless, recorded here per
  the binding fix's "write the one-line rationale into the codec javadoc and the router"
  instruction**: the two values route to STRUCTURALLY DIFFERENT engine mechanisms (native
  `BlockMountAPI.mountOnBlock` vs a plugin-spawned anchor entity + a directly-attached
  `MountedComponent`), each with its own sub-knob set and its own steering/drift risk profile -
  the same shape as `EffectStep.Type`, never a bundled mode collapsing independent switches into
  one enum. Absent `Surface` on an authored `Mount` group defaults to `"Block"`.
  - **`Surface: "Block"`** ([`StationMountController`](StationMountController.java), UNCHANGED
    behind the refactor - the regression anchor): mounts the player on the station block via
    native `BlockMountAPI.mountOnBlock` - the answer to the packet hunt's dead end (no
    `ServerCameraSettings` combination ever combined a free mouse-orbit camera with a locked
    body). Mounting sidesteps the whole packet hunt: the client renders its own free-orbit camera
    for a seated entity, and the ENGINE broadcasts the seated player's facing to every OTHER
    viewer from the seat's fixed geometry, never from the seated player's own live
    `TransformComponent`. **Requires the station BLOCK to author `BlockType.Seats[]`**
    (`{"Offset":{...},"Yaw":<degrees>}`) - `mountOnBlock` refuses to mount at an un-authored
    position. **The shared `RPG_Station_Sawmill` block authors `Seats[].Yaw: 180`** because the
    engine adds a hard, unconditional +180deg in `BlockMountPoint.computeRotationEuler` (a
    first-party `// ?` comment in the shared source) - an authored `Yaw: 0` faces the worker AWAY
    from the bench; this was a landed in-game fix (pack commit `224f2c6`), not a hypothetical.
    Movement lock: the Block route forces `movementLock = false` (`Hold.MovementLock`/`EffectId`
    stay authorable as a fallback but are ignored while mounted this way) - the mount itself is
    the lock. Exit detection: the native engine removes `MountedComponent` on its own once the
    player sends a movement input past its 600ms grace; the heartbeat's mounted-mode branch checks
    `StationMountController.isMounted` INSTEAD of the ordinary `MaxMoveMeters` origin-delta
    walk-off check (the mount SNAPS the player's transform to the seat's fixed geometry at engage
    - an intentional reposition, not a walk-off). **The one unresolved, documented risk**: nothing
    server-side stops the CLIENT from continuing to send mouse-derived `bodyOrientation` in its
    `ClientMovement` packets while seated (`MountSystems.HandleMountInput`'s `SetBody` branch
    unconditionally re-applies it); whether the client itself suppresses it while visually seated
    cannot be confirmed from server source alone - an in-game verification item, not solved in
    code.
  - **`Surface: "Entity"`** ([`StationEntityMountController`](StationEntityMountController.java))
    - the STANDING work mount (design 9.2's "furniture / vehicle / mount that can show player NOT
    sitting"; the maintainer's anvil authors this surface, see the mod-root `CLAUDE.md`'s Phase 2
    section). At engage: spawn a minimal anchor entity at the station block's center
    (`spawnAnchor` - a phase-2 SPIKE component set, `SpawnMinecartInteraction`'s own list minus
    the cart/model leaves, see that class's javadoc for the iteration knobs), then attach
    `MountedComponent(anchorRef, attachmentOffset, MountController.Minecart)` to the player
    DIRECTLY via the public constructor (`attach` - no interaction chain, the plugin attaches it
    itself). Because this path NEVER populates the client's `MountedUpdate.Block` field (that leaf
    is BlockMount-exclusive), the mount mine infers the player renders STANDING by construction -
    the strongest source-backed inference, but genuinely **in-game-unverifiable from server source
    alone** (the maintainer's phase-2 smoke item). **CRITIQUE FIX (m7)**:
    `Hold.Mount.Entity.Offset {X,Y,Z}` converts EXPLICITLY to the `MountedComponent` constructor's
    `Rotation3f attachmentOffset` parameter at the one ECS call site (`attach`) - that parameter is
    a `Rotation3f`, NOT a `Vector3f`, despite reading like a plain positional offset (a native
    mislabeling the mount mine confirms). **The steering/drift risk** (documented, not solved
    here): the native entity-mount controller has NO auto-dismount and applies WASD input DIRECTLY
    to the anchor's own transform (`HandleMountInput`, the asymmetry that exempts entity mounts
    from the Block route's 600ms-grace auto-dismount). The default (`Steerable` false, the
    maintainer's fallback-is-a-data-change design) mitigation is two-layered: the SAME hold effect
    effect-mode uses (via `StationHoldController`) defeats client-sent movement input, and the
    heartbeat calls `snapBack` every tick to re-assert the anchor's authored transform. Neither
    mitigation is proven sufficient against client-trust `SetBody` (mouse-look) drift - the SAME
    unresolved-risk class the Block route's `bodyOrientation` question already carries; an
    in-game verification item. `DismountOnMove` (default true) runs the SAME origin-delta
    walk-off check effect-mode uses (the entity-mount controller has no native auto-dismount, so
    this IS the dismount); `false` = hard-lock until crouch/re-press (the enchanting-circle look).
    `Steerable: true` (reserved, `StationValidator`'s `MOUNT_STEERABLE_UNTESTED` warns it) skips
    both mitigations, letting WASD freely drive the anchor - a future vehicle-like station's knob,
    unused by anything shipped. Anchor lifecycle: session-scoped, despawned in the ONE idempotent
    `stop()` funnel (`stopAll`'s shutdown sweep included, resolving its own store off the anchor
    ref the same way `returnCustody` resolves its owner store off a possibly-null `stop()`
    parameter). Engage-time behavior otherwise matches EFFECT mode, not the Block route: the work
    emote plays normally at engage and per swing (no Action-slot swing-route substitution - that
    workaround exists only to route around the Block route's sit-pose suppressing the `Emote`
    slot, which does not apply to a standing mount), and the default work camera stays off (free
    mount-orbit camera) the same way the Block route defaults it off.
- **Seat/swing routing, the seated-worker fix** (Block route only - `useActionSlotForSwing` stays
  keyed to `seatMode`, unaffected by the Entity route, see above): a seat-mode session's swing does NOT re-fire the
  work emote on the `Emote` slot (the sit pose wins over that slot's clip, so the player never
  visibly swings). `StationService.runSwing`'s pure `useActionSlotForSwing(seatMode)` decision
  routes a seat-mode session through `StationHoldController.playActionSwing` instead of
  `.playEmote`: it fires the swing on `AnimationSlot.Action` against the CURRENTLY HELD ITEM'S OWN
  `ItemPlayerAnimations` clip set (native `Item.getPlayerAnimationsId()`, resolved fresh off the
  active hotbar item every swing, no snapshot), the exact mechanism vanilla combat swings ride.
  The clip id is `StationAsset.Animation.ActionClip` when authored, else
  `StationHoldController.DEFAULT_ACTION_CLIP` (`"Chop"`, the Hatchet family's own attack clip) -
  deliberately NOT a cross-family default (Pickaxe = `"Mine"`, Sword = `"SwingLeft"`, no shared
  key across families); a station gated on a different tool family MUST author its own
  `ActionClip` or the swing plays nothing (`StationValidator`'s `ACTION_CLIP_WITHOUT_SWING` warns
  an authored-but-dead override with no `Swing` group). Engage-time play also differs: `toggle`
  skips the `Emote`-slot engage play entirely in seat mode (the native sit pose already owns the
  idle look between swings); effect mode is unchanged. **In-game unverified**: whether a
  server-fired `Action`-slot animation actually renders visibly on a PLAYER entity was proven in
  the pre-extraction engine's round-3 smoke test (confirmed working) - re-verify after the
  extraction if camera/animation behavior regresses, since the code moved verbatim but was not
  independently re-smoked post-move.
- **Idle practice mode** (`StationAsset.Work.Idle`, opt-in, default OFF): a `NO_INPUTS` start
  proceeds into idle mode instead of denying. Idle cycles grant fractional XP asks only
  (`PerCycle * XpFraction`, multiplier forced to 1.0) with NO conversion, NO loot, and are marked
  `idle=true` on `StationCycleCompletedEvent` so a progression listener can withhold quest/
  achievement/statistics progress the same way the pre-extraction engine did.
- **Opt-in held-tool durability drain** (`StationAsset.Tool.Durability {PerSwing, PerCycle}`):
  both default OFF; the mutation is the native `ItemUtils.updateItemStackDurability`. A broken
  held stack (`ItemStack.isBroken()`) stops the session (`TOOL_BROKEN`) and fires
  `StationToolBrokeEvent`.
- **Exit hooks**: re-press F / crouch / walk-off (heartbeat), damage
  ([`StationInterruptDamageSystem`](StationInterruptDamageSystem.java), Inspect group - read-only,
  calls `stop` only), death ([`StationDeathSystem`](StationDeathSystem.java) -> `stopForRef`),
  disconnect (`RpgStationsPlugin`'s `PlayerDisconnectEvent` registration -> `stopFor`, wired leg 5
  fill-in), world-change (heartbeat store check), shutdown (`stopAll`, `RpgStationsPlugin.shutdown`).
  `stop()` is the ONE idempotent exit funnel: it fires `StationSessionCompletedEvent`
  UNCONDITIONALLY (every stop, silent included - the api's one guaranteed cleanup signal for a
  progression listener's per-session accumulator).
- **Loot + flairs**: [`StationFlairs`](StationFlairs.java) resolves the per-player cosmetic
  overlay for a moment slot (`enum Slot { CYCLE, SWING, RARE_FIND, COMPLETION }`) against the
  UNION of every registered api `FlairUnlockProvider` (the MMO's `StationComponent`-backed
  provider is the only one registered today - persistence stays MMO-side by maintainer ruling, an
  unlock is a per-player fact this session-scoped mod never stores). See `../loot/CLAUDE.md` for
  the conditional-lootable roll layer this package calls into per cycle.
- **Engine settings**: [`SettingsCatalog`](SettingsCatalog.java) holds the folded
  `asset.SettingsAsset` singleton (`Enabled`, `SummaryHud.{Enabled,Position,OffsetY,TtlMs}`) -
  `Enabled` backs the heartbeat's engine-wide feature-toggle terminate check.
- **Validation**: [`StationValidator`](StationValidator.java) (750 lines) runs at every asset-load
  fold (`RpgStationsPlugin.onStationAssetsLoaded` calls `StationValidator.runAndLog()`); the pure
  `validate(...)` core is unit-tested. [`/rpgstations validate`](../command/CLAUDE.md) (leg P0) runs
  the same live validator and chats the aggregate (summary + every finding), matching what the
  boot-log audit prints.
- **Multi-action stations + the step engine** (design section 9.1/9.3, phase 2 leg B, LANDED):
  [`ActionResolver`](ActionResolver.java) is the PURE whole-group-override choke point
  (`resolve(asset, actionId)` - an action's own group REPLACES the station-level default
  wholesale; omitting inherits it) and the diegetic selection core (`selectAction`, first-match-
  wins over `asset.ActionInput`). A station's `Actions` map defaults to ONE implicit
  `ActionResolver.ACTION_WORK` action when absent - `StationService.runRealCycle` always resolves
  through `ActionResolver.resolve`, so the shipped sawmill (no `Actions` authored) and a future
  multi-action station run the IDENTICAL code path. [`ImplicitProgram`](ImplicitProgram.java) is
  the PURE builder for the classic-convert-loop's four-step shape (`[Consume, Produce, Roll,
  Present]`, `asset.StationStep` instances built from the live `ConversionCheck` pick + the
  resolved action's `Loot`/`Presentation` groups) - the byte-stable regression anchor made
  concrete. The `station.step` engine itself (all in THIS package, not a subpackage, so it keeps
  package-private access to `StationService`'s helpers): [`StationStepContext`](StationStepContext.java)
  (the per-run bundle, rebuilt fresh every dispatch/resume per the kernel's contract - resume
  state that must SURVIVE a suspension lives on `StationSession` instead),
  [`StationStepResult`](StationStepResult.java) (sealed `Success`/`Suspend`/`Skip`/`Fail`),
  [`StationStepSemantics`](StationStepSemantics.java) (the `StepSemantics` adapter - `isSuspend`/
  `nextIndex` wire the Goto branch mechanism), [`StationStepRegistry`](StationStepRegistry.java)
  (registers the six executable handlers, EACH wrapped in a conditions-gate + throw-guard layer -
  the design 9.3/M4 binding fix: a throwing step degrades to a clean session `stop()`, never
  crashes the shared per-world frame drain), [`StationStepHandlers`](StationStepHandlers.java)
  (Consume/Produce/Wait/Roll/Command/Present - `Consume`/`Produce` support ONLY `From`/`To`
  `"Inventory"` this leg, `"Custody"` decodes but fails cleanly until phase-2 leg C),
  [`StationStepDecisions`](StationStepDecisions.java) (the PURE decision cores - a Wait step's
  suspend/resume math, the conditions-gate outcome, Goto target resolution - unit-tested without a
  live server, mirroring `loot.RollEvaluator`'s role), and
  [`StationStepKernel`](StationStepKernel.java) (the ONE production `CastKernel` instance every
  program - implicit or authored - walks). `StationSession` carries the resume-across-ticks state
  (`programSuspended`/`programIndex`/`stepDeadlineMs`/`activeProgramSteps`/
  `activeProgramCycleOutput`/`activeProgramCycleIndex`) - unreached by the sawmill (its implicit
  program has no `Wait` step, so it always completes synchronously within one
  `tickFrameOnce` drain), exercised by [`StationStepDecisionsTest`](../../../../../test/java/com/ziggfreed/rpgstations/station/StationStepDecisionsTest.java)'s
  two-tick suspend/resume simulation. `Camera.FaceBlockMode` is RENAMED `Camera.Recipe` this leg
  (design 9.7) - `StationSession.cameraRecipe` / `StationCameraPreset.resolve`'s
  `assetRecipeId` param, no deprecated alias (unreleased, no shipped JSON used the old key).
- **Placed-input custody + block states** (design section 9.4, phase 2 leg C, LANDED):
  [`StationCustodyClaim`](StationCustodyClaim.java) is one block's live claim (owner uuid +
  `itemId -> quantity` tally, insertion-ordered oldest-first - never persisted, the same
  no-per-player-persistence constraint every session type here honors); `StationService` owns the
  `custodyByBlock` map (keyed the SAME `"<worldUuid>:<x>:<y>:<z>"` blockKey `byBlock` uses).
  [`StationCustody`](StationCustody.java) is the PURE decision core (mirrors `StationToolScaling`'s
  injected-live-resolver pattern so nothing here constructs a live `Item`): `placeableQuantity`
  (whole-stack-then-top-up-then-cap math), `available`/`drain` (family-matched over an injected
  `itemId -> resourceTypeId[]` resolver, oldest-placed-first, tallying REAL drained ids into the
  session ledger), `matchesInput`/`matchesAnyConversionInput` (the placement-acceptance matchers -
  an explicit `asset.Custody.getInput()` OR, absent, ANY of the resolved `Recipe.Conversions`
  inputs, the sawmill's zero-extra-authoring "logs by ResourceTypeId family" fallback), and
  `shouldReturnToInventory` (the auto-return branch decision, exhaustively unit-tested). `toggle`
  gates a `Custody`-governing station behind ONE state-dependent F: not-loaded (no live claim, or
  an empty one) + a matching held stack places/tops-up (`placeIntoCustody`, removing exactly the
  moved amount off the ACTIVE HOTBAR SLOT via `ItemContainer.removeItemStackFromSlot`) and returns
  before reaching the classic engage flow; loaded + a non-owner denies `ui.station.occupied`;
  otherwise (loaded-by-owner-with-nothing-to-place, or empty-with-nothing-held) falls through to
  the classic engage flow, now sourcing `Convert` viability from the claim
  (`firstRunnableConversionFromCustody`, the inventory-sourced `firstRunnableConversion`'s
  custody-reading sibling - output room is STILL checked against the live inventory, only the
  INPUT side moved into custody). `runRealCycle` builds its implicit `Consume` step with
  `From: "Custody"` whenever the resolved action authors `Custody` (never `Inventory` - one
  coherent per-station choice, not a per-cycle one); `StationStepHandlers.ConsumeHandler` gained
  the matching drain branch (`Produce` stays `To: "Inventory"` always this leg - custody governs
  input only). **Auto-return, every exit path**: `stop()`'s new `returnCustody` call is
  UNCONDITIONAL and near the very top (before the silent/non-silent notification branching), so
  every `StopReason` returns unconsumed custody to the owner's inventory (room-checked) or drops
  it at the block once (`ItemComponent.generateItemDrops` + `store.addEntity`) - it resolves its
  store off `s.ref.getStore()` (NOT the `store` parameter `stop()` may have been handed `null`,
  e.g. `stopAll`'s shutdown sweep) specifically so a valid ref still covers that sweep.
  [`StationCustodyBreakSystem`](StationCustodyBreakSystem.java) (`BreakBlockEvent`, registered in
  `RpgStationsPlugin`) covers the complementary no-active-session case (input placed, block broken
  before a session ever starts) - the two paths cannot double-drop (`ConcurrentHashMap.remove` is
  the idempotency gate). **Block-state flip** (`flipCustodyState`, the kweebec shrine-furnace
  precedent - `world.setBlockInteractionState` guarded by `bt.getBlockForState(name) != null`) is
  HINT-ONLY this leg (mechanism-first maintainer ruling; the display-entity visual layer is a later
  Visuals leg) and self-heals: `toggle` re-asserts the Empty state on every not-loaded interaction,
  so a Loaded block-state surviving a restart with no live claim behind it (custody is memory-only,
  the design's accepted crash-loses-it ruling) resets on the next press, no dupe risk. The shipped
  sawmill (jar default AND the pack's MMO-bridged copy) migrated to placed input this leg - see
  `asset/CLAUDE.md`'s `Custody` entry and the pack's own `CLAUDE.md`.
- **The anvil arc (design section 9.5, phase 2 leg E, LANDED)**: see the mod-root `CLAUDE.md`'s
  Phase 2 section for the full narrative (the `Stamp` step, the `StampCapEngine` roll/cap engine,
  AND the live multi-action wiring leg E ALSO had to land - leg B shipped the schema/step-engine
  machinery but never actually wired `toggle()`/`runCycle()` to read a resolved action's groups or
  dispatch an authored `Steps` program). In THIS file's terms: [`StampCapEngine`](StampCapEngine.java)
  (pure, `station`-package-private, unit-tested via `StampCapEngineTest`) is called ONLY from
  [`StationStepHandlers.StampHandler`](StationStepHandlers.java) (registered in
  [`StationStepRegistry`](StationStepRegistry.java) alongside the other six handlers).
  [`ActionResolver.selectActionByFamily`](ActionResolver.java) (a DIFFERENT NAME from
  `selectAction`, never an overload - a `null` 3rd arg would otherwise be ambiguous between the
  `String`/`String[]` forms) is the resource-type-FAMILY-aware selection entry `StationService`
  calls from its new `selectActionForHeld`/`liveFunctionOf` helpers. `StationCustodyClaim` gained
  `uniqueStack`/`setUniqueStack` (a metadata-preserving single-item placement, `placeIntoCustody`
  populates it whenever `custody.effectiveMaxQuantity() == 1`, `toItemStacks()` prefers it) - the
  weapon-durability/prior-enhancement-loss fix. `StationCatalog` gained an action-aware
  `resolvedConversions(asset, actionId, actionRecipe)` overload (a multi-action station's per-action
  `Recipe` override needs its OWN derived-conversion cache entry). `StationService.dispatchProgram`
  reads the resolved action's `Work.effectiveRepeat()` and calls `stop(..., StopReason.RITUAL_COMPLETE, ...)`
  on a completed non-repeating program.
- **Not yet landed** (design scope, not started): phase 2 legs F-H - the open flair/moment
  vocabulary, the art leg, and the phase-2 smoke round (see the mod-root `CLAUDE.md`'s Phase 2
  section).
