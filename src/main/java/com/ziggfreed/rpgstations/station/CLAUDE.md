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
- Volatile block-keyed maps are global, keyed `"<worldUuid>:<x>:<y>:<z>"`; a new one joins `forgetBlockKeyedState`, so a world unload evicts it.

## Content and validation

- A `Lootable` folds by id and a later layer replaces the whole file, so give every roll another mod may re-tune its own file.
- `StationValidator` never blocks an asset, errors included; its findings are advisory. Keep the full pass on the first `PlayerReadyEvent` until a live boot shows `STAMP_UNKNOWN_POOL`, `LOOT_UNKNOWN_DROPLIST` and `MISSING_*_LANG` do not false-positive under `AllWorldsLoadedEvent`.
