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

## Client stability (observed in game)

- Engage the work camera with `ClientCameraView.Custom` and a fully populated `ServerCameraSettings`, and release it with zc's `ServerCameraService.reset` (`Custom`, `false`, `null`); other shapes correlated with a client `NullReferenceException` after walk-off.
- A work emote never sets `HideItemInHand`, and cycle consume drains backpack storage before the combined view; both were load-bearing for client stability.

## Engine traps and world state

- The engine's entity, seat, state-variant, section-unload and static-init traps are the `hytale-api` skill's "Entities and blocks".
- Read a station block's id through `StationService#blockItemIdAt` (its Item, stable across state flips), never zc's `BlockOps.blockItemIdAt`, which returns the state-variant type id despite its name.
- Volatile block-keyed maps are global, keyed `"<worldUuid>:<x>:<y>:<z>"`; a new one joins `forgetBlockKeyedState`, so a world unload evicts it.

## Content and validation

- A Stamp step hands its `Stats` group straight to `StampCapEngine.resolve` and reads the pool's rename and rarity through `RollPoolConfig#poolOf`; never re-merge extension entries at a call site.
- `StationValidator` audits only the tables a station rolls (`stationLootables`). Its roll checks read reward kinds from `RewardKinds.shared()`, so a validator test authoring station kinds registers them in `@BeforeEach`, and the per-fold structural pass drops `LOOT_UNKNOWN_REWARD_KIND`, since a file-authored kind may fold later.
- A table zc's mod gate refused (`LootableValidator.modGateRefused`) is absent by design: `LOOT_UNKNOWN_TABLE` and a `Lootable` extension target's `EXTENSION_TARGET_UNKNOWN` name none, while a table nobody authored still warns.
