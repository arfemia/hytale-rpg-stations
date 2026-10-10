# station/ - the session engine

`StationService` runs every session, cycle and custody touch. Mechanism lives in each class's javadoc and the mod's `docs/` guides; these are the cross-class rules.

## Resolution and reads

- Once an action id is chosen, read every group through `ActionResolver.ResolvedAction`, never the raw `StationAsset`/`ActionDef`/`ActionAsset`: the `Puppet`/`Custody`/`ContributionScale`/`Anchors` extension overlays apply inside the resolver.
- Merge extension `Steps` only in `StationService.effectiveProgramSteps`, never onto `ResolvedAction.getSteps()`, which decides whether an action runs the convert loop or a step program.
- A fold that can change a derived conversion calls `StationCatalog.invalidateResolvedConversions()` (the Station, Action and Extension folds all do): the stores fold in no guaranteed order, so a new store that feeds conversions calls it too.
- Output-room checks use zc's `InventoryGrant.canAdd`/`canAddAll`, never a container read of this mod's own, so the probe and the grant cannot disagree.

## Moments

- Every session-scoped moment, a loot `Cue` included, plays through `StationService.emitMoment` (flair overlay, delay queue, particle leak guard), and every sessionless one through `playPresentationAt`; never spawn a moment's particles or sounds directly.
- Delayed cues have one scheduler (`scheduleCueAt`/`cueDue`) and a per-world queue drained at the top of `tickFrameOnce`, ahead of the session early return: a completion cue outlives its session, so never key that queue per session.
- Keep the per-burst particle `DurationSeconds` cap (default 4s): some vanilla spawners never stop (`TotalParticles` -1, as in `Block_Gem_Sparks`). Author `DurationSeconds: 0` only for a system that ends on its own.
- A press the station turns away answers through `StationRefusals` (`press.refuse(<ui.station.* key>)`), never a bare `toast`: the seam plays the `Refused:<Reason>` cue, throttles repeats and fires `StationRefusedEvent`, and the reason is the key's tail after `ui.station.`.

## Conversion, consumption and pacing

- A conversion has ONE code path: the `Convert` phase (`StationStepHandlers#convertPhase`). The implicit program is `{Convert, Roll, Presentation}`; the pre-dispatch `selectConversion` only drives idle practice, stops and pace and rides the dispatch as `preselected`.
- Every consuming path reaches `StationService#onInputConsumed` EXACTLY ONCE, AFTER the consumption commits, and a refunded consumption never reaches it: session consume records into the ledger's hook half and `commitIteration` reports it; `Stamp` reagents report after the stack commits; an unattended settle reports with no worker. Add a new consuming path to this hook in the same change (`InputConsumedHookOrderTest` pins the order).
- Every commit point makes the ONE call `commitIteration`; a `Convert` phase commits whether or not it produced anything.
- A single-item socket's last consume takes the piece's `Unique` stack off the pile and ledgers that REAL stack so an interrupted iteration puts it back intact; only a stack THIS drain took is ledgered (`StationCustodyLedger#pieceTaken`).
- The metadata guard covers every consuming path, never one route: the custody runnable scan and the unattended settle skip any row that would consume a single-item socket's real stack carrying an undeclared key (`StationMetadataGuard#consumesRefusedPiece`, read through `refusedPieces(claim)`), `consumeFromCustody` fails the same way, and the ritual queue walks `StationMetadataGuard#workable`. A single-item socket refuses the piece at placement (`socketAccepts`) whenever every action reading it consumes it (`socketUse`, `consumedByEveryReader`); a `Stamp` reader keeps accept-then-scan. A count pile never reaches the guard: it refuses metadata, wear and a re-qualified stack itself.
- A fallback row is offered ONE at a time (`StationFallbackRoutes#offeredRows`), so a crafting-share row with no room answers NO_ROOM and the essence-only row never rescues it. Derived rows sort by primary output id then recipe id, and the derived cache re-derives when the recipe index's `generation()` moves.
- A step's pace resolves ONCE at its fresh entry (`StationPacing`, `StationSession.stepPaceScale`); a `Paced` step stretches its `Duration` and its entry presentation's timing. A program with a `RollBonus` beat skips the completion-time `Bonus` pass.
- The ritual queue (`Work.Queue`) sets `StationSession.queueSocketId` to the next filled Item socket (`StationCustody#nextFilledSocket`) before each pass, and `Convert` drains that socket.
- `OutputItems` has a cycle output only once a `Convert` beat has run; a program with none is what `LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT` warns about.

## Placement

- Acceptance against an `ActionInput` is ONE rule, `StationCustody.accepts`; `matchesInput` is the routes-only seam behind it, never an acceptance site.
- Placement (`StationService#socketAcceptsInput`, `routeStack`) layers three rules over it: a route-less matcher's `Except` holes carve out what the station DERIVES, never everything; a hole-refused or protect-listed material (`ProtectListCatalog.protects`) is `PlacementDenial.PROTECTED`, which outranks every other reason and keeps its own `ui.station.protected` key; a count pile refuses per-instance data.
- A press that placed nothing refuses only at a station that was EMPTY before it (`unplacedPressRefusal`), Protected included, so a loaded station judges the held item as a tool and protecting a trophy tool never locks its owner out.

## Presentation targets and working state

- A moment's `Target` resolves at PLAY time (`playMoment` -> `resolveAim`), never at emit time, so a delayed cue lands where its target is when due. `Puppet` falls to the BLOCK when no double stands: a cue never lands on the worker's own body.
- At an entity aim sounds go through zc `Sound3D.playOn`, and a burst rides the entity (`ModelParticleService.spawnOn`) only when `MomentBursts.plan` proves it ends on its own (zc `ParticleLifetimes`); everything else plays at the entity's position under its cap. A cue at a freshly spawned entity nobody has been shown yet falls back to its position.
- An effect with `Target "Puppet"` goes on the double with NO expiry (`NativeEffectUtil.applyInfinite`; the double has no `EntityStatMap`, so the engine's timer never runs), and an authored `DurationMs` is kept on this engine's own cue clock (`queueEffectRemoval`); track every effect on the session so teardown strips it.
- `enterWorkingState` is idempotent per block AND state name: a different name (a step's `State`) re-flips in place without the resting look between (`workingMove`, `workingStateName`).
- `Display.Animated` is a spawn-time option; a step's `Display` overlay RESPAWNS the prop when `sameLook` says the look changed. A `Display` cue after the piece is consumed lands where the prop last stood (`displayRestingPosition`).

## Client stability (observed in game)

- Engage the work camera with `ClientCameraView.Custom` and a fully populated `ServerCameraSettings`, and release it with zc's `ServerCameraService.reset` (`Custom`, `false`, `null`); other shapes correlated with a client `NullReferenceException` after walk-off.
- A work emote never sets `HideItemInHand`, and cycle consume drains backpack storage before the combined view; both were load-bearing for client stability.
- An entity the engine's setup systems do not stamp (they cover props, block entities, dropped items, projectiles and minecarts; the `Entity`-mount anchor is none of these) needs an explicit `NetworkId`, or it spawns invisible to every viewer.
- Put render-critical components (an initial `ActiveAnimationComponent`) on the holder before the spawn call: a viewer who starts tracking the entity before a later write sees it frozen with no pose.
- A `Block`-surface mount needs the station block to author `BlockType.Seats[]`; the engine adds 180 degrees to the seat yaw (`BlockMountPoint.computeRotationEuler`), which is why the Sawmill authors `Yaw: 180`.

## Engine traps

- Never construct `ItemToolSpec` or another `AssetBuilderCodec`-backed engine type in unit-tested code: its static init throws outside a server, so inject a value shape (`StationToolScaling.ToolPower`, `StationRecipeDeriver.CraftingCandidate`).
- Compare blocks by their Item (`BlockType#getItem()`, through `BlockOps.itemOf` or `StationService#blockItemIdAt`), never by block-type id: a custody state flip replaces the block with a generated state-variant type, and zc's `BlockOps.blockItemIdAt` returns that type id despite its name.
- `PlayerAccess.storage` returns the `Storage` component, not its container: unwrap through `StationStepHandlers#storageContainer(Player)`, never a fresh `getInventory()` chain.
- Column-bound chunk sections fire no `SectionUnloadEvent`, so section-keyed state is evicted lazily on the next visit.

## Custody and world state

- Custody lives in zc's chunk-persisted `BlockStashes`; `StationCustodyClaim` is a per-touch view with no cache, and each in-place mutation batch ends with exactly one `claim.markDirty()`.
- `DISCONNECTED`, `SERVER_STOP` and `WORLD_CHANGED` stops leave custody standing, since they can run off the world thread; every other stop hands it back (`custodyReturnsAtStop`).
- While its block stands, demote a pattern-marked stash through `removeOrDemoteStashAt`, never delete it: the mark is what lets a later break revert the structure. Only the block-gone path removes it.
- A `NetworkId` is per world and not boot-stable: keep it only in the volatile `displayByBlock` map and scope every match to the presser's world (`StationCustodyRetrieval#owns`).
- The `doneness:` prefix in a pile's `PendingCycles` is reserved; the unattended accrual keys (`accrual:conversion:<index>`) sit beside it.
- An unattended settle reports what it drained per pile (`Settle#drains`) to the input hook after its `markDirty`, and a drain that takes a single-item socket's last also drops that socket's prop.
- Volatile block-keyed maps are global, keyed `"<worldUuid>:<x>:<y>:<z>"`; a new one joins `forgetBlockKeyedState`, so a world unload evicts it.

## Content and validation

- A `Lootable` folds by id and a later layer replaces the whole file, so give every roll another mod may re-tune its own file.
- `Target:{RollPool}` `Entries` join zc's ONE roll pool composition as an entry source (`ExtensionCatalog.registerRollPoolEntrySource`, owner `rpgstations`, registered at plugin setup beside the `Lootable` roll source), so every stamp drawing from an extended pool sees them, a station's or a stamped reward's anywhere. A Stamp step hands its `Stats` group straight to `StampCapEngine.resolve` and reads the pool's rename and rarity through `RollPoolConfig.poolOf`; never re-merge extension entries at a call site. A test reading an extended pool registers the source in `@BeforeEach` (`ExtensionCatalogTest`).
- `StationValidator` audits the TABLES a station rolls, never every table: the ids the station, action-asset and extension-`Bonus` walks reference (collected through the `lootableKnown` predicate) plus their `ContributesTo` files (`stationLootables`). The shared roll rules over every table are zc's `LootableValidator`'s; a table another site rolls is that site's to audit. A table is `LOOT_EMPTY_TABLE` only with neither rolls nor a pool. Inline and table rolls check reward kinds against `RewardKinds.shared()`, so a validator test authoring station kinds registers them in `@BeforeEach`; the per-fold structural pass drops `LOOT_UNKNOWN_REWARD_KIND`, since a file-authored kind may fold later.
- An inline station roll or a roll in a station-rolled table whose effective trigger is neither `Cycle` nor `Completion` (an omitted `Trigger` reads `Default`) is `LOOT_TRIGGER_NEVER_ASKED`, a warning: no station pass asks for it. A `Target:{Lootable}` extension's own rolls are exempt, since they join the table at every site that rolls it.
- `StationValidator` never blocks an asset, errors included; its findings are advisory. Keep the full pass on the first `PlayerReadyEvent` until a live boot shows `STAMP_UNKNOWN_POOL`, `LOOT_UNKNOWN_DROPLIST` and `MISSING_*_LANG` do not false-positive under `AllWorldsLoadedEvent`.
