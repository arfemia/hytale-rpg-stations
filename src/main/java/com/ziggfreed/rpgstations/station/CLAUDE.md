# station/ - the session engine

`StationService` runs every session, cycle and custody touch. Mechanism lives in each class's javadoc; these are the cross-class rules.

- Once an action is chosen, read every group through `ActionResolver.ResolvedAction` (extension overlays apply inside it), never the raw `StationAsset`/`ActionDef`/`ActionAsset`; extension `Steps` merge only in `StationService.effectiveProgramSteps`, never onto `ResolvedAction.getSteps()`.
- A new store that feeds conversions calls `StationCatalog.invalidateResolvedConversions()` from its fold, as the existing folds do.
- Output-room checks use zc's `InventoryGrant.canAdd`/`canAddAll`, never a container read of this mod's own, so the probe and the grant cannot disagree.
- Every moment, a loot `Cue` included, plays through `StationService.emitMoment` (sessionless: `playPresentationAt`), never as direct particles or sounds; a delayed cue waits in the per-world queue, never a per-session one (a completion cue outlives its session).
- A refused press answers through `StationRefusals` (`press.refuse(<ui.station.* key>)`), never a bare `toast`.
- A conversion has ONE code path, the `Convert` phase (`StationStepHandlers#convertPhase`); `selectConversion` only preselects its row.
- Every consuming path reaches `StationService#onInputConsumed` exactly once, after the commit and never for a refund, and every commit point makes the ONE call `commitIteration`. A new consuming path joins that hook (`InputConsumedHookOrderTest`) and the metadata guard (`StationMetadataGuard`: every path, never one route) in the same change, and ledgers a single-item socket's piece as its REAL stack only when THIS drain took it (`StationCustodyLedger#pieceTaken`).
- Acceptance against an `ActionInput` is ONE rule, `StationCustody.accepts`; `matchesInput` is never an acceptance site. Placement layers its rules over it (`StationService#socketAcceptsInput`, `routeStack`, `unplacedPressRefusal`): `PlacementDenial.PROTECTED` outranks every other reason, and only an EMPTY station refuses a press that placed nothing.
- Client stability (observed in game): engage the work camera with `ClientCameraView.Custom` and a fully populated `ServerCameraSettings` and release it with zc's `ServerCameraService.reset`; a work emote keeps `HideItemInHand` false; a consume drains backpack storage before the combined view.
- Engine traps are the `hytale-api` skill's "Entities and blocks". Read a station block's id through `StationService#blockItemIdAt`, never zc's `BlockOps.blockItemIdAt` (the state-variant id, despite its name).
- A new volatile block-keyed map is keyed `"<worldUuid>:<x>:<y>:<z>"` and joins `forgetBlockKeyedState`, so a world unload evicts it.
- `StationValidator` audits only the tables a station rolls (`stationLootables`); its tests register the station reward kinds they author in `@BeforeEach`. A table zc's mod gate refused (`LootableValidator.modGateRefused`) is absent by design, never `LOOT_UNKNOWN_TABLE` or `EXTENSION_TARGET_UNKNOWN`, while a table nobody authored still warns.
