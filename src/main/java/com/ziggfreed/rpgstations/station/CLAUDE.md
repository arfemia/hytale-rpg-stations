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
- **THE `emitMoment` choke point, and its 4-second particle cap**: `emitMoment(store, s, momentId,
  presentation, targetPos)` in `StationService` is the ONE presentation-playback funnel every
  station moment goes through (`StationFlairs.MOMENT_CYCLE` for the real/idle cycle,
  `MOMENT_SWING` for the per-swing cue, `MOMENT_IMPACT` for its delayed impact - a SEPARATE moment
  id since design 9.6/phase 2 leg F, previously fused onto `MOMENT_SWING` - `MOMENT_RARE_FIND` for
  a loot-tier flourish, `MOMENT_COMPLETION` for session-end, plus a per-step
  `StationFlairs.stepMomentId(actionId, stepId)` id for a `Present` step) - it is ALSO the
  flair-resolution choke point (`StationFlairs.effective` against `FlairCatalog
  .effectiveFlairsFor`'s merged map, the per-player unlock overlay). `targetPos` is a plain
  per-call-site argument, never a fork:
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
  (`Swing.Impact.{DelayMs, Presentation}`) that fires through the SAME `emitMoment` choke point on
  a later frame, on its OWN `StationFlairs.MOMENT_IMPACT` moment id (design 9.6, phase 2 leg F -
  previously fused onto the swing cue's moment id; a flair author can now target either
  independently).
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
- **Loot + flairs, the OPEN vocabulary (design 9.6, phase 2 leg F, LANDED)**:
  [`StationFlairs`](StationFlairs.java) resolves the per-player cosmetic overlay for a moment id
  against the UNION of every registered api `FlairUnlockProvider` (the MMO's
  `StationComponent`-backed provider is the only one registered today - persistence stays
  MMO-side by maintainer ruling, an unlock is a per-player fact this session-scoped mod never
  stores). The old fixed `Slot` enum (`CYCLE`/`SWING`/`RARE_FIND`/`COMPLETION`) is RETIRED in
  favor of an open STRING moment id: `StationFlairs.MOMENT_CYCLE`/`MOMENT_SWING`/`MOMENT_IMPACT`/
  `MOMENT_RARE_FIND`/`MOMENT_COMPLETION` are the engine's own well-known constants (`impact` is
  NEW this leg, split off `swing` - the delayed swing-impact cue used to reuse the swing slot
  verbatim; a flair author can now target either cue independently), plus
  `StationFlairs.stepMomentId(actionId, stepId)` builds a per-step `step:<actionId>:<stepId>` id a
  `Present`-typed `StationStep` resolves against (`StationStepHandlers.presentMomentId`, falling
  back to `MOMENT_CYCLE` when the step authors no `Id`). The flair map itself is now the merge of
  TWO sources ([`FlairCatalog`](FlairCatalog.java)`.effectiveFlairsFor`): a station's own inline
  `Flairs` (an authoring convenience, `asset.StationAsset.Flair` reshaped this leg to a single
  open `Moments` map instead of 4 fixed leaves) UNIONED with every folded `asset.FlairAsset`
  (`Server/RpgStations/Flairs/*.json`, Pattern A, ANY mod can ship one) whose `Stations` list
  applies (null = every station) - a same-flair-id `FlairAsset` entry wins ("folds ONTO" the
  inline map). `StationService.emitMoment`/`effectiveFlairs` resolve this merged map fresh per
  moment (no caching - flair playback is not a hot per-tick path); `api.impl.StationViewImpl
  .flairIds()` and `StationCatalog.allFlairIds()` both reuse the SAME merge point rather than a
  narrower inline-only view. `StationValidator.checkFlairs`/`validateFlairAssets` share one
  `checkFlairMoments` core: an empty `Moments` map warns `EMPTY_FLAIR`, an unrecognized moment id
  warns `UNKNOWN_FLAIR_MOMENT_ID` (never blocks - "a future engine moment must not break an older
  pack", design's own binding note), and a `FlairAsset.Stations` entry naming an unknown station
  id warns `FLAIR_ASSET_UNKNOWN_STATION`. See `../loot/CLAUDE.md` for the conditional-lootable
  roll layer this package calls into per cycle.
- **Engine settings**: [`SettingsCatalog`](SettingsCatalog.java) holds the folded
  `asset.RpgStationsSettingsAsset` singleton (`Enabled`, `SummaryHud.{Enabled,Position,OffsetY,TtlMs}`) -
  `Enabled` backs the heartbeat's engine-wide feature-toggle terminate check.
- **Validation**: [`StationValidator`](StationValidator.java) runs TWO passes (fix-wave D4, timing
  not checks - the boot-log evidence was a false `STAMP_UNKNOWN_POOL`/`LOOT_UNKNOWN_DROPLIST`/
  `MISSING_*_LANG` from a later pack layer's RollPool/Drops/lang overlay not having folded yet at
  an EARLIER layer's Station-fold callback). `validateStructural()`/`runStructuralAndLog()` runs at
  EVERY asset-load fold (`RpgStationsPlugin.onStationAssetsLoaded`/`onFlairAssetsLoaded`) - every
  check except the cross-layer reference-existence ones (native `ItemDropList` id, this mod's own
  `Lootable`/`RollPool`/station-id references, lang key). `validate()`/`runAndLog()` (the FULL set)
  runs ONCE, post-load, from `RpgStationsPlugin`'s first-`PlayerReadyEvent` hook (mirrors the MMO's
  own `ContentAudit` first-PlayerReady startup-audit timing - by then every asset pack has finished
  merging) and on demand from [`/rpgstations validate`](../command/CLAUDE.md) (leg P0, already
  post-load) - both chat/log the same aggregate (summary + every finding). The lang-key check
  itself (`langKeyKnownLive`) is a MERGED-view check (D5 fix): a miss against the jar's own
  hand-maintained `i18n.RpgStationsLangKeys` set falls through to a LIVE `I18nModule.getMessage`
  query, so a pack's own additive `rpgstations.lang` overlay (e.g. the anvil's
  `station.anvil.name`/`.desc`) resolves correctly instead of false-warning
  `MISSING_NAME_LANG`/`MISSING_DESC_LANG`. The pure `validate(...)` core is unit-tested.
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
  inputs, the sawmill's zero-extra-authoring "logs by ResourceTypeId family" fallback). Round-5
  (2026-07-22) retired the pure `shouldReturnToInventory` all-or-nothing batch decision in favor
  of `util.ItemGrantUtil`'s per-stack hotbar-first-then-backpack-then-drop-at-block routing (a
  policy wrapper over `ziggfreed-common`'s `inventory.InventoryGrant`), so a claim holding several
  distinct item ids can land some in the hotbar, some in the backpack, and only the genuine
  overflow on the ground. `toggle`
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
- **The placed-input PLACED-AS-ENTITY visual** (design section 9, phase 2 leg G, LANDED - the
  maintainer's directed route over a Blockbench baked-node model swap):
  [`StationCustodyDisplay`](StationCustodyDisplay.java) spawns a static, network-replicated,
  pickup-immune, physics-free prop entity rendering the claim's placed item at the station's
  block-top anchor (the SAME point every cycle/swing/impact/rare-find moment already targets),
  gated on a new nullable `asset.Custody.Display` group (`{Offset{X,Y,Z}, Scale, Rotation{X,Y,Z}}`,
  every leaf `appendInherited`, null = no visual - the leg-C default; **round-7 D-1** made
  `Rotation` a nested `{X,Y,Z}` DEGREES group, X pitch/Y yaw/Z roll, so a placed weapon lies flat
  on an anvil via `{"X":90}` - a bare-number `Rotation` is migration-tolerated as legacy Y-only yaw
  with a WARN). **Mechanism (fix round: delegated
  to `ziggfreed-common`'s `entity.ItemPropEntityService`, lifted config-free out of this class's
  own prior verbatim copy)**: originally copied verbatim from the engine's own sanctioned exemplar,
  the admin "Entity Spawn Page" Items tab
  (`hytale-shared-source/HytaleServer/NPC/.../pages/EntitySpawnPage.java`) - a block-shaped
  representative item (`Item#hasBlockType()`, e.g. the sawmill's placed logs) spawns a real
  `BlockEntity` (renders the actual block model, not a flat icon); everything else (most
  weapons/tools - no entity-atlas `ModelAsset`, e.g. the anvil's placed weapon) spawns a bare
  `ItemComponent` prop with `setOverrideDroppedItemAnimation(true)` - the generic "dropped item
  minus physics" shape. The THIRD exemplar route (`ModelAsset`-backed items) is NOT implemented
  (rare in practice, out of this leg's scope). Pickup-disable is `PreventPickup.INSTANCE` +
  `PropComponent` (both routes carry both). `StationCustodyDisplay#spawn` now calls
  `ItemPropEntityService.buildHolder` for the un-added `Holder`, adds its own press-F retrieve
  interaction onto it (`#addRetrieveInteraction`), then commits via `ItemPropEntityService.spawn`;
  `#despawn` delegates straight through to `ItemPropEntityService.despawn`. This class now owns
  only station-specific policy (block-top-anchor offset/yaw/scale resolution + the retrieve
  interaction), the spawn/despawn mechanism itself lives in common (see its own `entity/CLAUDE.md`).
  **Never-persisted, by construction**: both routes
  `ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType())` - the same native
  `NonSerialized` marker `ItemComponent.generatePickedUpItem`/Teleport/Projectile/Deployables use
  for a transient plugin-owned entity - so a display entity CANNOT survive a server restart,
  mirroring the custody claim's own "never persisted, crash = loss" lifecycle exactly and closing
  the "reconcile orphans on restart" requirement by construction (nothing survives to reconcile).
  Lifecycle: spawned once in `StationService#placeIntoCustody` (guarded on
  `StationCustodyClaim#displayRef()` being null, so a top-up never re-spawns), despawned in
  whichever of the TWO claim-removal sites fires first - `StationService#returnCustody` (every
  session-stop exit, `stopAll`'s shutdown sweep included) or `#onCustodyBlockBroken` (the
  no-active-session block-break path, which despawns even when the claim has already drained to
  zero items mid-session - the only site left to reach it in that case). The ref lives ON the
  claim (`StationCustodyClaim#displayRef`/`#setDisplayRef`, mirroring the `uniqueStack` field's own
  pattern) - no second tracking map. Offset/Scale/Rotation math
  (`StationCustodyDisplay#resolveWorldOffset`/`#resolvePosition`/`#resolveRotationRadians`/
  `#resolveScale`) is kept PRIMITIVE-typed (doubles/floats only, no `Vector3d`/`Rotation3f` touch)
  so it stays unit-tested without a running Hytale server, the same discipline
  `StationEntityMountController#resolveAttachmentOffset` established. **Round-8: `Offset`/`Rotation`
  are FACING-RELATIVE** (commit `cc52fb4`, superseding the pre-round-8 world-space simplification
  this bullet used to document): the ONE impure read (`#blockYawRadians`, isolated so the
  composition cores stay pure) resolves the placed block's own facing yaw at spawn via the
  non-deprecated `World#getBlockRotationIndex` -> `RotationTuple` (World resolved off the command
  buffer's store through `ziggfreed-common`'s `WorldEvictors#worldOf`); `#resolveWorldOffset` then
  rotates the authored HORIZONTAL `Offset` (X/Z) into world space by that yaw
  (`x' = x*cos + z*sin`, `z' = -x*sin + z*cos` - the engine's own block-vector yaw convention) while
  `Offset.Y` stays vertical, and `#resolveRotationRadians` ADDS the block yaw into the authored
  `Rotation.Y` (`X` pitch / `Z` roll unchanged). Authoring convention: `+Z` = toward the block's
  FRONT, `+X` = its right. A DEFAULT-orientation placement (yaw 0) is the identity (`cos 0 = 1`,
  `sin 0 = 0`), so every pre-round-8 authored value renders BYTE-IDENTICALLY - a horizontal offset
  now TRACKS a rotated placement instead of pointing at a fixed world axis. The read is try-guarded
  to yaw 0 (unloaded chunk / null-facing / any throw), degrading gracefully to the prior world-space
  behavior, never aborting the spawn. Shipped values (the anvil's `enhance.Custody.Display` gained a
  facing-relative `Offset.X` + a `Rotation.Z` roll for the placed weapon; `convert` + the sawmill log
  stay vertical-offset-only, so unchanged at any orientation) are in the pack's own `CLAUDE.md`,
  still provisional pending the smoke round.
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
- **Round-7/round-8 stepped-program timing (maintainer-approved, `[D77DIAG]`-proven then swept)**:
  a NON-repeating authored Steps program (`Work.effectiveRepeat()` false, e.g. the anvil's Enhance)
  gets INSTANT first dispatch - `toggle` sets `s.nextCycleAtMs = now` (not `now + CycleMs`) when
  `stepsProgram && !work.effectiveRepeat()`, so a one-shot ritual fires its only cycle immediately
  at engage instead of eating a full `Work.CycleMs` of pure latency (a REPEATING program - the
  sawmill loop or any `Repeat: true` steps program - keeps the pre-delay; idle mode never applies to
  a Steps program). `dispatchProgram` takes an explicit `resuming` flag (a resume's `startIndex` can
  legally be 0, so `startIndex == 0` alone cannot distinguish fresh-vs-resume) and a FRESH dispatch
  explicitly zeroes `s.stepDeadlineMs` before the walk (an explicit guarantee on top of every `Wait`
  step's own success-path reset), plus feeds `StationStepContext#resumingStep` (the round-8 per-step
  puppet clip + the generic per-step `Presentation` hook both read it to skip an already-played
  step's replay on resume). **The `[D77DIAG]` enhance-timing instrumentation was REMOVED 2026-07-23
  (round-8)** after it proved the ritual timing correct - every tagged `Log.info`/`Log.warn` across
  `StationService`/`StationStepHandlers` plus the per-player resume-log throttle map is gone (same
  one-sweep-removable pattern as the retired `[SMOKEDIAG]` lines); only the functional
  instant-dispatch / `resuming` / `Presentation` changes above survive.
- **Enhancement outcome reporting (round-7 D-6, MMO-agnostic)**: after the Stamp step's ONE custody
  write (`claim.setUniqueStack`), `StampHandler#execute` captures a
  [`StationEnhanceOutcome`](StationEnhanceOutcome.java) (`itemId` + immutable before/after
  `ItemStack` copies + the stamper's `List<EnhanceLine>` report + the native `Durability.AddMax`
  delta) onto `StationSession#enhanceOutcomes` and fires the api `StationEnhanceCompletedEvent`
  (`StationEvents#fireEnhanceCompleted`, `hasListener`-gated). `applyStampMutation` now returns a
  pure `Mutation(stack, lines, durabilityAdded)` record - the stamper's `EnhanceStamper.apply`
  returns a `StampResult` (mutated stack + `EnhanceLine` report) instead of a bare stack (the
  pre-1.0.0 api reshape). `StationService#enhanceLedgerRows` (extracted pure/static, unit-tested)
  turns each outcome into summary rows: one `SummaryRow.Kind.ENHANCE` row per `EnhanceLine`
  (label rendered VERBATIM - the provider owns stat vocabulary/wording/color) PLUS one engine-owned
  `Durability +N` row (durability is RpgStations-native, so a bare anvil with NO stamper still
  reports its enhancement) accent-colored `#c9a2ff` at composition; `StationSummaryHud`'s
  `ENHANCE` case renders both without recolor. Zero MMO stat vocabulary enters this mod.
- **Phase 2 legs A-G are all LANDED** - see the "Loot + flairs" and "placed-input
  PLACED-AS-ENTITY visual" bullets above for F/G's file-by-file detail. **Leg H (the phase-2
  smoke round) is docs-landed** (see the mod-root `CLAUDE.md`'s Phase 2 section and
  `../../../../../../../../../.claude/plans/work-stations-mod-extraction-prompt.md`'s PHASE-2
  SMOKE CHECKLIST); no engine file in this package changed for it, the in-game confirmation pass
  itself is still batched/pending.
- **First fresh-boot smoke fix round (R1-R5, LANDED this leg)**: five defects the maintainer's
  fresh boot surfaced, all fixed against the actually-smoked artifacts.
  - **R1 (item localization + HUD width)**: `StationService.itemNameMsg` was a bare `Msg.key(
    "items." + itemId + ".name")` with NO existence probe and NO native-namespace fallback, so
    every NATIVE vanilla item in the session-summary ledger (most rows - the sawmill's logs, the
    anvil's bars) resolved to an unregistered `items.<id>.name` key the client rendered as raw
    unresolved text. Fixed by delegating to a new lifted `ziggfreed-common` primitive,
    `i18n.NativeNames.itemNameMsg` (probes `server.items.<id>.name` FIRST, then `items.<id>.name`,
    then a prettified raw fallback - the exact two-tier shape the MMO's own `content.objective
    .TargetNameResolver#itemNameMsg` already proved necessary). Also widened the summary panel
    +48px (`StationSummaryHud.PANEL_WIDTH_PX` 480->528, every `RpgStationSummary.ui` `#Content`
    child 444->492) to restore the headroom the extraction had silently shrunk from the
    pre-extraction MMO panel's 520px.
  - **R2 (seated swing, since resolved by the puppet route)**: a full paper trace found the
    seated-worker swing DISPATCH (`runSwing`/`useActionSlotForSwing`/
    `StationHoldController#playActionSwing`) provably correct and unchanged since the
    in-game-proven pre-extraction commit - the break was CLIENT-SIDE rendering of a server-fired
    `Action`-slot animation on a block-mounted (seated) player, unobservable from server source.
    A subsequent boot's `[SMOKEDIAG]` readout proved every server-side link fired correctly
    through `packet-sent` each beat, pinning the failure client-side conclusively; the PUPPET
    presentation route (its own spawned entity animates on the render-guaranteed
    `ActiveAnimationComponent` path, in-game confirmed 2026-07-23) superseded the seated-swing
    question for every shipped station, and the five `[SMOKEDIAG]` `Log.info` lines were DELETED
    in the round-6 cleanup pass (2026-07-23), mirroring the mod-root CLAUDE.md's status wording.
  - **R3 (inventory pull for custody placement)**: `StationService#toggle`'s placed-input custody
    branch only ever tried the ACTIVE HOTBAR SLOT (`custodyAccepts`/`placeIntoCustody`), so
    matching material sitting unheld in storage/backpack was invisible to placement - a false
    "no materials" toast. `placeIntoCustody` now takes an explicit source `ItemContainer`/slot/
    stack (generalized off the hotbar-only derivation) so BOTH the held-item path AND a new
    fallback, `findFirstCustodyMatchInInventory` (scans `InventoryComponent
    .HOTBAR_STORAGE_BACKPACK`, skipping the already-tried held slot), route through the IDENTICAL
    placement engine - held still wins first, the fallback only runs when the held slot didn't
    match. No new lang key (directive 5 only widens WHERE a match can be found, not the
    messaging).
  - **R4 (display entity, TWO independent causes fixed)**: (a) AUTHORING - the anvil's `convert`
    action's `Custody` authored no `Display` group at all, so a placed bar never even attempted a
    spawn (`placeIntoCustody` guards the spawn on a non-null `Custody.Display`) - fixed with a
    `convert.Custody.Display` matching `enhance`'s own values, see the pack's own `CLAUDE.md`.
    (b) ENGINE - `StationCustodyDisplay#spawn`/`#despawn` called `store.addEntity`/
    `store.removeEntity` DIRECTLY, which throws `IllegalStateException("Store is currently
    processing!")` when invoked from inside an interaction handler (the ONLY call site,
    `StationService#placeIntoCustody`, runs inside `toggle()`, itself inside the store's
    processing lock) - the throw was swallowed by the method's own `catch (Throwable)` into a
    silent WARN, so the sawmill's placed-logs display (which DID author `Display` from leg G)
    never appeared either. Fixed by switching both methods to the tick-safe
    `CommandBuffer<EntityStore>` primitive (`commandBuffer.addEntity`/`.removeEntity`, the same
    one `StationEntityMountController#spawnAnchor` already used) - `placeIntoCustody` now takes a
    `commandBuffer` param, and `stop()`/`returnCustody`/`onCustodyBlockBroken` all thread a
    nullable `CommandBuffer` through so teardown despawn is tick-safe too wherever one is
    available (every frame-tick/interaction call site has one; the damage/death/disconnect/
    shutdown hooks pass `null` and leave the entity behind - harmless, it is `NonSerialized` so it
    cannot survive a restart regardless).
  - **R5 (loaded-station restart-orphan recovery)**: action selection at `toggle()` only ever
    consulted the live claim (gone after a restart - custody is memory-only) or the held item
    (scored against `ActionInput`, which the wrong tool never matches) - a stale `Loaded`
    block-state with no claim behind it dead-ended EVERY press with `ui.station.no_action`
    ("nothing this station can work with"), even holding the right tool. A new pure resolver,
    `ActionResolver#selectActionForBlockState(asset, currentStateName)`, is consulted as a THIRD
    fallback (only when both the live-claim and held-item selectors return null): it matches the
    block's own CURRENT persisted interaction-state name (read via the source-verified
    `BlockAccessor.getCurrentInteractionState(world.getBlockType(x,y,z))` reverse-lookup, the
    exact inverse of `flipCustodyState`'s own `setBlockInteractionState` write) against each
    action's `Custody.States.Loaded` name. Recovering the action id re-enters the ALREADY-correct
    custody fall-through (`preClaim` stays null, so the existing not-loaded self-heal flips the
    block back to Empty), so the player is unstuck on the very next press and denies with the
    truthful `ui.station.no_materials` (claim genuinely lost) instead of the dead-end
    `ui.station.no_action`.
- **Second fresh-boot smoke fix round (R6, LANDED this leg, 2026-07-22)**: the anvil's work-start
  deny (`ui.station.mount_unavailable` firing even though the tool gate and custody viability had
  already passed) plus a NEW press-F custody retrieval feature.
  - **R6 diagnosis**: the maintainer's fourth smoke boot found the anvil denying EVERY work-start
    attempt with `ui.station.mount_unavailable` (holding the correct hammer, tool gate and custody
    viability already passed) - `StationService#toggle`'s Entity-mount engage path sends that exact
    toast whenever `spawnAnchor`/`attach` returns null/false. Source reading of the anvil's
    `Hold.Mount.Surface:"Entity"` mechanism (design 9.2, a phase-2 spike never verified in-game)
    surfaced a SEPARATE, confirmed defect in the same code path, independent of whichever condition
    actually triggered the observed deny: `StationEntityMountController#spawnAnchor`'s anchor
    carried no `NetworkId` component (it deliberately excludes `MinecartComponent`, the ONE
    component a native `MountSystems.EnsureMinecartComponents` auto-ensures a `NetworkId` for), so
    `MountSystems.PlayerMount#onComponentAdded` (the player's own self-view `mountId`) and
    `MountSystems.TrackerUpdate#queueUpdatesFor` (the third-party `MountedUpdate` broadcast) BOTH
    silently `return` on a null `NetworkId` lookup - even a mount that DID succeed would have
    rendered invisibly. Source-confirmed fix: `spawnAnchor` now adds `NetworkId` explicitly (mirroring
    `StationCustodyDisplay#spawnItemEntity`'s own item-prop route, the one other anchor-adjacent
    entity in this package that needed to add it by hand for the identical reason); `Visible` -
    the OTHER iteration knob the class javadoc used to leave open - does NOT need adding
    (`TrackerUpdate`'s query runs over the entity that HAS `MountedComponent`, the PLAYER, who
    already carries `Visible`). See `StationEntityMountController`'s header javadoc for the full
    source trail.
  - **R6 graceful degradation + orphan-anchor leak + teardown tick-safety (engine hardening,
    regardless of which mount surface a station picks)**: `StationService#toggle`'s entity-mount
    engage no longer denies the whole work loop with a toast on a spawn/attach failure - it
    despawns whatever `spawnAnchor` already queued (closing the leak: `spawnAnchor` queues the
    entity BEFORE `attach` can fail, so every failed press used to leak a `NonSerialized` orphan)
    and falls back to effect-mode (movement lock + hold effect), the same posture a station with
    no `Mount` group authored at all gets. `StationEntityMountController#despawn` was ALSO the
    exact `store.removeEntity`-from-an-interaction-handler class of bug the R4 wave already fixed
    for `StationCustodyDisplay` (`IllegalStateException("Store is currently processing!")`,
    silently swallowed into a WARN) - fixed the identical way, threading a `CommandBuffer` through
    instead of a raw `Store`.
  - **R6 anvil authoring fix (pack, `content-packs/skill-stations-pack`)**: `Anvil.json`'s `Hold`
    moved OFF `Mount.Surface:"Entity"` onto the proven effect-mode default (`MovementLock`/
    `EffectId:"RPG_Station_Hold"`/`InterruptOnDamage`) - the maintainer-recommended swap to the
    phase-1 proven hold while the Entity mount stays an unverified spike, not a permanent design
    reversal (the engine-side NetworkId fix above makes the Entity mount genuinely usable again
    for a future pack revision that wants the standing-worker pose).
  - **Press-F custody RETRIEVAL (NEW FEATURE, R6)**: the placed-input display entity itself
    (design section 9's visual, phase 2 leg G) is now press-F interactable -
    `StationCustodyDisplay#addRetrieveInteraction` adds `Interactable` (the marker; tells the
    CLIENT this entity can be F-interacted) + an `Interactions` entry (`InteractionType.Use` ->
    the jar-shipped generic `RPG_Station_Retrieve` RootInteraction asset, plus a lang-keyed hint)
    to BOTH spawn routes - the exact component pair NPCs/minecarts use for a non-block Use target,
    zero NPC/minecart dependency (confirmed via the shared source: `InteractionManager` stamps
    `Interaction.TARGET_ENTITY` into the chain's meta store from the incoming packet's `entityId`
    BEFORE any interaction node runs; `UseEntityInteraction` does its OWN independent target
    lookup off `getClientState().entityId` to validate reach and read `Interactions`, then pushes
    the registered RootInteraction onto the SAME context via `context.execute(...)` - it never
    touches `TARGET_ENTITY` itself, but because the context is unchanged, the value
    `InteractionManager` stamped earlier survives, so `interaction.StationRetrieveInteraction`
    recovers the exact clicked ref via `ctx.getTargetEntity()`). `StationService#retrieveCustody` resolves that ref
    back to its owning block key by comparing `NetworkId` VALUES (not `Ref` identity - keeps the
    matching decision engine-free/unit-testable; see `StationCustodyRetrieval#findOwningBlockKey`)
    against a live snapshot built from every claim's `displayRef()`, then routes the eligibility
    decision through the PURE `StationCustodyRetrieval#decide` (precedence: `UNKNOWN_TARGET` ->
    `BUSY` -> `NOT_OWNER` -> `NOTHING_TO_RETRIEVE` -> `RETRIEVE`) - **`BUSY` is the maintainer's
    explicit no-op case: a session ACTIVELY working the target block always wins over
    ownership/claim-contents checks**, because yanking materials out from under a running Consume
    step would either silently short a cycle or race the session's own auto-return on its next
    stop. On `RETRIEVE`: hands the claim back via a NEW shared `giveClaimToOwner` helper (extracted
    DRY from `returnCustody`'s own inventory-first/drop-at-block logic - both now call the ONE
    give-back engine), despawns the display, flips the block back to Empty, removes the claim.
    `StationCustodyClaim` gained `blockX`/`blockY`/`blockZ` fields (stashed at construction rather
    than re-parsed out of the block-key string) since the retrieval entry point has no
    block-coordinate packet field to read, unlike every other custody call site. Unit-tested via
    `StationCustodyRetrievalTest` (the network-id lookup + every `decide` precedence branch,
    including the BUSY-outranks-everything case). See `interaction/CLAUDE.md`'s
    `StationRetrieveInteraction` bullet for the interaction-handler half.
- **Third fresh-boot smoke fix round (R7, LANDED this leg, 2026-07-22)**: the pack's R5 icon-tuning
  leg removed `Identity.Icon` from `Sawmill.json`/`Anvil.json` on the claim that
  `StationService#blockItemIdAt`'s no-icon-authored fallback (the anchor block's own `BlockType`
  id at ENGAGE time) already equals the station's own item id - a claim that is FALSE for any
  CUSTODY station (design 9.4), because custody engagement only happens AFTER materials are
  placed, which has already flipped the block to its `Loaded`/`BarsPlaced`/`WeaponPlaced` state.
  A state variant is a DISTINCT, generated-key `BlockType` asset (`StateData#generateBlockKey`:
  `GENERATED_ID_PREFIX + parentKey + "_" + stateName`, `GENERATED_ID_PREFIX = "*"`), so the old
  fallback's raw `blockType.getId()` returned e.g. `*RPG_Station_Sawmill_Loaded` at engage - not a
  real item id - and the summary-crest's `new ItemStack(id, 1)` silently resolved the UNKNOWN
  placeholder icon instead of the station's own, a regression from the R4-era (valid-if-wrong)
  raw-material icon. `blockItemIdAt` now resolves via `BlockType#getItem()` (the block's
  containing Item asset) instead of the raw `BlockType#getId()`, falling back to `getId()` only
  when the block has no containing Item at all (the pre-fix behavior for that edge case). Verified
  against the shared source: a state variant decodes through `ContainedAssetCodec
  .Mode.INJECT_PARENT`, so its `Data.containerData` is the PARENT block's own `Data` - itself
  linked to the owning `Item` via the native `Item.BlockType` field's `INHERIT_ID_AND_PARENT`
  containment - and `AssetExtraInfo.Data#getContainerKey` recurses up that chain regardless of
  nesting depth, so `getItem()` resolves the SAME base item id whether the block is in its
  `Default` state or any generated state variant. No pack authoring change needed - this is an
  engine-only fix, and the fallback's original "zero duplicated id to drift" intent now genuinely
  holds. See `content-packs/skill-stations-pack/CLAUDE.md`'s own R7 correction for the pack-side
  narrative fix.
- **The puppet presentation engine (round-4 design, `.claude/research/raw/rpg-stations-puppet-presentation-design-2026-07-22.md`
  section 4, legs P3+P4+P5, LANDED)**: [`StationPuppetController`](StationPuppetController.java) is
  the policy-thin glue over `ziggfreed-common`'s `entity.PlayerPuppetService`/`PlayerModelService`
  for "mount the player, hide their player model, and spawn/display a visual of their character
  model performing the steps" - sibling to `StationEntityMountController`/`StationHoldController`,
  never duplicating the generic spawn/hide-by-scale primitives the common lift already owns
  (leg B). **Spawn + hide, at engage** (`spawnAndHide`, called from `StationService#toggle` AFTER
  the mount-attach block - the puppet layers on WHATEVER hold/mount the real player already has,
  never replacing it): resolves `Puppet.Offset`/`Yaw` off the station's block-top anchor, the
  initial `Puppet.Prop` (mirror-held/forced-id/none), and spawns via `PlayerPuppetService.spawn`;
  a null spawn is non-fatal (`StationSession.puppetActive` stays false, the session continues
  in-body, matching `StationEntityMountController`'s own graceful-degradation contract). **Hide
  route, this leg**: `Hide.Route` is a three-arm union - only `"Scale"` actually hides
  (`hideByScale`/`revealByScale`, the in-game-crowned mechanism), `"Effect"` is schema-reserved
  future work (the shadowstep/`Portal_Teleport` pointer) and `"None"` is the deliberate degraded
  fallback - both apply NO hide this leg, the puppet just spawns alongside a still-visible real
  player (validator `PUPPET_WITHOUT_HIDE` warns, never blocks). The design doc's `"ModelSwap"`/
  `"HiddenManager"` routes were already RETIRED before the schema shipped (leg C) - this
  controller never touches `HiddenPlayersManager` at all. **Reveal + despawn, in the ONE
  idempotent `stop()` funnel** (`revealAndDespawn`, called right after `returnCustody` - the SAME
  unconditional-on-every-exit-path posture, resolving its OWN store off `s.ref.getStore()` (falling
  back to `s.puppetRef.getStore()`) rather than trusting the possibly-null `store` parameter, so a
  disconnect/shutdown stop still reveals + despawns cleanly). **Animation routing** (design 4.3):
  a puppet-active session's engage-time loop (`playLoop`) and per-swing beat (`playSwing`, called
  from `StationService#runSwing`'s new `s.puppetActive` branch) BOTH fire on the puppet's `Emote`
  slot, SUPERSEDING `useActionSlotForSwing`/`playActionSwing` entirely for that session - a puppet
  has no sit pose to fight, so it needs none of the seat-mode Action-slot held-item workaround
  (sidesteps the whole S5/R2 seated-swing-render defect class by construction). **The per-step
  `StationStep.Puppet` override, round-8 step-synced swings:** the per-step **`Clip`** now plays
  once at STEP ITERATION ENTRY (`StationStepRegistry`'s guard calls `#playStepClip` when
  `StationStepDecisions.shouldPlayClipOnEntry(step, resumingStep)` passes - the SAME once-per-entry /
  never-on-resume-recheck gate the generic per-step `Presentation` hook uses, `resumingStep`
  identity-compared, so a `Wait` step's suspend/resume never replays its clip; per-iteration-entry by
  construction, so future step repetition fires one clip per iteration for free). To stop a
  double-fire, `StationService#runSwing`/`start` SUPPRESS the generic engage/swing puppet CLIP for a
  stepped program whose steps author any `Clip` (`StationSession.stepProgramAuthorsClip`, resolved
  once at engage via `StationStepDecisions.programAuthorsAnyStepClip`; a stepped program with NO step
  clips keeps its one generic engage swing). The per-step **`Prop`** override now ALSO syncs at STEP
  ENTRY (round-8 continuation), mirroring the clip's entry hook exactly: `StationStepRegistry`'s guard
  calls `StationPuppetController#syncStepProp` when `StationStepDecisions.shouldSyncPropOnEntry(step,
  resumingStep)` passes (the SAME `resumingStep` identity guard), so a step's own `Puppet.Prop`
  override fires the moment it begins executing - the anvil's `stamp` beat's `Prop.Source:"None"`
  empty-hands swap finally shows (the enhance action has NO swing beats, so the prior swing-beat-only
  sync never visibly fired). Unlike the clip gate, this is NOT gated on the step authoring an override:
  EVERY fresh step entry syncs to that step's effective prop (its override when authored, ELSE the
  session default), so moving PAST a prop-overriding step reverts the prop to the default (the exit
  edge, one rule, no separate revert path). `#syncStepProp` delegates to the SAME `#syncProp` plumbing
  the swing beat uses, which STILL also runs every beat off the session's step-resume state
  (`programSuspended`/`activeProgramSteps`/`programIndex`, holding a suspended step's prop across
  heartbeats - that is why the entry sync is safely skipped on a resume re-check). `resolveEffectiveClip`/
  `resolveEffectivePropItemId`/`shouldPlayClipOnEntry`/`shouldSyncPropOnEntry`/`programAuthorsAnyStepClip`
  are the pure decision cores, unit-tested (`StationPuppetControllerTest`/`StationStepDecisionsTest`). The shipped anvil authors
  `MMO_Emote_Hammer` on its `strike1`/`strike2` steps (the same clip the generic swing route resolves
  for the held hammer) so the puppet visibly hammers on both strike beats. **Safety net** (design
  4.4/leg P5): `reassertOnReady` unconditionally clears any lingering `EntityScaleComponent` and
  restores the correct cosmetic model on a FRESH `PlayerReadyEvent` ref/store - NOT gated on any
  remembered session (a restart wipes every in-memory `StationSession` by construction), wired via
  a new public `StationService#reassertPuppetOnReady` and a new `RpgStationsPlugin
  #registerPuppetSafetyNet` registration - originally independent of the temporary `puppetspike/`
  harness's own net (which had delegated its scale-clear/model-restore half to this same production
  method rather than duplicating it, keeping only its own `HiddenPlayersManager` un-hide call). No
  new player-facing strings (the group adds no UI text, per the design doc's own P6 note). **The
  `puppetspike/` package, its `/rpgstations puppet` subcommand, and the five `[SMOKEDIAG]` log
  lines in `StationService`/`StationHoldController` were DELETED in the cleanup pass (2026-07-23)**
  once the maintainer's full in-game puppet confirm landed (held-item mirror updates within a beat,
  player visible after every stop path incl. damage/death/relog, sawmill + anvil positioning
  good) - `StationPuppetController` is now the sole puppet-presentation code path, see its own
  header javadoc.
  - **Puppet fix round (LANDED, this leg)**: two defects the maintainer's fix-round review found
    against the puppet engine above, both source-verified in `hytale-shared-source`'s `Store.java`
    (`putComponent`/`addComponent`/`removeComponent`/`removeComponentIfExists`/`removeEntity` all
    call `assertWriteProcessing()` and throw `IllegalStateException("Store is currently
    processing!")` when the store's processing lock is held).
    - **Accessor bug**: `spawnAndHide`'s `Scale` hide (`hideByScale`), `revealAndDespawn`'s reveal
      (`revealByScale`) and despawn (`PlayerPuppetService.despawn`), and `syncProp`'s `Hotbar`
      component swap ALL used to mutate through the live `store` on code paths that run inside the
      store's own processing lock (`toggle` - an interaction handler - and the heartbeat frame-drain
      via `runSwing`/`stop`) - every throw was silently swallowed by each method's own
      `catch (Throwable)`, so (a) the real player was NEVER actually hidden despite every shipped
      station authoring `Hide.Route:"Scale"` (masked by the SAME bug - the hide itself never
      applied), (b) `stop()`'s common exit paths (re-press, walk-off, tool-changed, out-of-inputs,
      `RITUAL_COMPLETE`) left a GHOST PUPPET untracked in the world, and (c) a per-step
      `Puppet.Prop` override (e.g. an anvil stamp beat swapping to empty-handed) never applied.
      Fixed by threading `commandBuffer` through all four sites instead - `spawnAndHide` (which
      already received it for the spawn itself) now also hides through it; `revealAndDespawn`
      gained a `@Nullable CommandBuffer` parameter `stop()` threads through (the SAME nullable
      value `returnCustody`/the mount-anchor despawn already accept, with the SAME accepted
      null-degrades-to-"leave it, self-heals via `reassertOnReady`/restart" tradeoff on the
      damage/death/disconnect/shutdown sweeps); `playSwing`/`syncProp` gained a `commandBuffer`
      parameter `StationService#runSwing` now threads from the heartbeat's own. `spawnAndHide` no
      longer takes a `store` parameter at all (unused once the hide moved off it).
    - **Render-evidence route was dead**: `StationService#toggle` called `spawnAndHide` (which
      reads `s.emoteId` to pre-seed the puppet's render-guaranteed initial
      `ActiveAnimationComponent`) BEFORE `s.emoteId` itself was assigned a few lines later - so
      every fresh puppet spawned with NO initial animation component, and a viewer who started
      tracking the puppet between swing beats saw it frozen until the next swing. Fixed by moving
      the `Animation` group's field assignment (`s.emoteId`/`s.actionClip`) to run BEFORE the
      `spawnAndHide` call.
- **Round-5 item-grant UX refinements (LANDED, notification half of the round-5 wave; the
  hotbar-first-then-backpack-then-drop custody-return ORDERING itself is already documented in the
  "Placed-input custody" bullet above)**: two notification changes on top of that ordering,
  both routed through `ziggfreed-common`'s `feedback.PickupMimic`. **Retrieve feedback mimics a
  real item pickup**: `StationService#notifyNativePickup` (called from `retrieveCustody`) loops
  `PickupMimic.notifyLikeNativePickup(ref, store, stack, pos)` once per stack that genuinely
  landed in inventory - the exact native item-icon `Notification` + `SFX_Player_Pickup_Item` cue a
  real ground-item pickup fires, never a generic toast; a stack that fell through to a
  drop-at-block instead (inventory full) falls back to the plain "materials retrieved" toast
  rather than falsely claiming a pickup. **Item-gain notifications while working**:
  `StationService#notifyItemGain(playerRef, itemId, quantity, lucky)` fires per distinct item id
  for both produced-material grants and lucky/rare-find bonus grants (`applyGrantResult`,
  `RollEvaluator`'s bonus-copy + droplist paths), showing WHAT was gained with the item icon
  (`ui.station.gain.produced`); `lucky=true` appends the existing `ui.station.summary.lucky` tag
  and styles the WHOLE line `GOLD` (a distinct `Color` constant from the plain-toast YELLOW) -
  REPLACING the old two generic `ui.station.lucky`/`ui.station.rare_find` toasts. Deliberately
  LIGHTER than `notifyNativePickup`/`PickupMimic` (no SFX, no pickup-packet mimicry) since it fires
  mid-session, potentially every cycle, layered on top of whatever moment/particle presentation
  `emitMoment` already plays for the same event.
- **Deprecation sweep (LANDED, repo-wide edict close-out, 2026-07-22)**: compiled with
  `-Xlint:deprecation` to enumerate every call site across this package plus `StationStepHandlers`/
  `StationHoldController`/`interaction.StationUseInteraction` (33 total), each replaced with the
  exact non-deprecated replacement its own engine javadoc names (`InventoryComponent.Storage`
  component fetch instead of `Player.getInventory().getStorage()`; `InventoryComponent.Hotbar
  #getActiveItem()` instead of `getActiveHotbarItem()` - deliberately NOT `getItemInHand()`, a
  different semantic that also folds in `Tool`; `InventoryComponent#getCombined(...,
  BACKPACK_STORAGE_HOTBAR)` instead of `getCombinedBackpackStorageHotbar()`; a manually-fetched
  `PlayerRef` component instead of `Player.getPlayerRef()`). New `util.InventoryAccess` DRYs every
  call site (replaces `loot.LootEngine`'s own private `storageContainerOf`). Zero deprecated calls
  remain in this package, `loot/`, or `interaction/`; both build and the full test suite stayed
  green through the sweep.
