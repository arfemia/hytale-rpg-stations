# Changelog

Developer changelog for RPG Stations. No em-dashes.

## 1.1.0 - unreleased

Held until the maintainer releases it. Built against Ziggfreed Common 2.2.0 (the manifest floor
moves to `>=2.2.0`); the Sawmill's derived rows, its `Yield.Base 1` outcome, its three tool
readings and its HUD and summary rows are byte-identical to 1.0.0, each pinned by a test
(`SawmillDerivationParityTest`, `StationYieldCompositionTest`, `StationToolReadingsTest`,
`StationLootOriginTest`). The api artifact ships as 1.1.0 with the contract at 10.

- **A station derives from ANY vanilla bench, standalone recipes included, with each row's full
  outputs.** `Recipe.FromCrafting` now reads Ziggfreed Common's native recipe index
  (`RecipeIndex.live()`), which holds the standalone recipe files as well as the recipes authored
  inside items, so `Benches: ["Salvagebench"]` derives one conversion per vanilla salvage recipe and
  every row carries that recipe's whole output list at native quantities (an iron sword's two ore,
  a hide and a scrap are three outputs of one row). The derived-conversion cache
  (`StationCatalog`) re-derives the moment the index's `generation()` moves, so a recipe or item
  reload never leaves a stale row behind. `StationRecipeDeriver.candidatesOf(RecipeCatalog)` is
  the pure half of the live adapter, so a test walks the same code over a hand-built catalog. Two
  recipes making the same item keep one order on every boot (the recipe id breaks the tie). The
  Sawmill's 33 plank rows derive exactly as before: same rows, same order, same category stamps,
  same quantities.
- **`Recipe.Yield` states its composition rule over a multi-output row.** `Base` replaces the
  PRIMARY (first) output's quantity and nothing else; every other output keeps the quantity its
  recipe authored; `Scale`, `Min` and `Max` then apply to every output. A salvage row keeps its
  hide and its scrap under `Yield.Base 1`, and a single-output row reads exactly as it always has
  (`StationYield.resolveQuantity(yield, quantity, primary)`; the unattended settle applies the same
  rule).
- **Fallback routes for a placed piece no row covers** (`Recipe.Fallback`, custody-routed only).
  Tried in order once the row scan finds nothing: `CraftingShare {Share}`, a share of the piece's
  OWN crafting recipe (each exact-item line at `floor(Quantity x Share / OutputQuantity)`, where
  `OutputQuantity` is how many pieces that recipe makes per craft, so a batch recipe pays per
  piece and never the whole batch's share: one iron bar making four darts pays nothing back per
  dart at a half share, five Life essence making two bait pay one per bait; a line that rounds to
  nothing is dropped, a recipe with no line left does not take the route; family and tag lines
  name no item to give back and are dropped), then `EssenceOnly {}` (the piece is consumed and
  nothing is produced; the action's `Bonus` rolls are its whole payout, the ONE row shape allowed
  to carry no output). Both are scoped by `Fallback.Input` (the shared matcher) and by the engine's
  METADATA GUARD (`StationMetadataGuard`): a stack is refused when it carries a metadata key no mod
  declared disposable, or when its keys cannot be read. The read and the declared-key list are
  ziggfreed-common's (`ItemReadings.undeclaredMetadataKeys`, `DisposableItemMetadata`): a mod
  declares its own disposable keys through `DisposableItemMetadata.declare` at setup, a registered
  stamper's keys are declared when it registers, and a stamped stack vouches for nothing beyond its
  declared keys. Wear rides the stack's durability leaves and never counts. Priority is authored
  rows (tier 0) > salvage-derived (1) > crafting share (2) > essence only (3); a fourth party
  overrides any piece by authoring a row for it, on the station or through an extension's
  `Conversions` payload. A piece is offered ONE route, the first that applies
  (`StationFallbackRoutes.routeFor`), a pile ONE row, its oldest falling-back piece's, and
  selection hands the runnable scan exactly that row (`StationFallbackRoutes.offeredRows`, pinned
  at length one), so a full inventory still answers inventory-full: a row scan that found a
  covering row but no room never falls back, and a crafting-share row with no room answers
  inventory-full rather than falling through to the essence-only row, so a full bag never
  destroys a piece for nothing. Placement acceptance asks the same question the routes will, so
  drop-only gear can be placed. Validator: `FALLBACK_WITHOUT_CUSTODY`, `FALLBACK_NO_ROUTE`,
  `FALLBACK_SHARE_OUT_OF_RANGE`, `FALLBACK_INPUT_CATCH_ALL` (INFO).
- **`Except` holes on the shared input matcher, one or several.** `ActionInput` (an action's
  `Select`, a `Custody.Input`, a socket's `Match`, a fallback's `Input`) gains `Except`, the same
  four routes one level down: a material the routes accept is refused when a hole accepts it too,
  so `{"Tags": {"Type": ["Weapon", "Tool"]}, "Except": {"Tags": {"Type": ["Ammo"]}}}` takes
  weapons and tools but never ammunition without listing every id. `Except` is authored as ONE
  matcher or as an ARRAY of them (`ActionInput.EXCEPTS_CODEC`, the dual-shape
  `ObjectOrArrayCodec`; a one-entry array re-encodes as the bare object), and an extension's
  `Custody.Input` overlay ADDS its entries beside the base's (`ExtensionCatalog.concatExcepts`),
  never in their place, so a pack protects its own item from a station without restating what the
  jar protects. One codec definition serves both levels (`ActionInput.EXCEPT_CODEC`); an entry has
  no `Except` of its own. Every site asks ONE acceptance rule, `StationCustody.accepts` (action
  selection, a socket's `Match` and an explicit `Custody.Input` at placement, a Block socket's
  match, a fallback's `Input`): an absent or catch-all matcher accepts everything, a route set
  accepts what a route matches, and the holes are carved out either way. At PLACEMENT a
  route-less matcher's holes are carved out of what the station DERIVES (its conversion inputs and
  fallback routes), never out of everything: an `Input` that authors only `Except` narrows what
  the station would otherwise take rather than turning it into everything but the hole, while an
  `Input` that authors routes replaces the derivation as before. An entry that authors no route
  matches nothing, so it carves no hole: the matcher accepts exactly what it would without it.
  Validator: `EXCEPT_CATCH_ALL` (an entry with no route does nothing, almost always a route left
  out; an extension's `Custody` overlay gets the same check on its `Input` and each socket's
  `Match`), and an unknown `Except.Function` reports as `UNKNOWN_ACTION_FUNCTION`.
- **A ritual can run a recipe: the `Convert` step phase.** `StationStep.Convert {Enabled?}` runs
  the action's `Recipe` at that beat: the matched row is selected exactly as the classic loop
  selects one (authored, derived, then the fallback routes; narrowed to the chosen output
  category), its inputs are consumed (from custody when the action authors `Custody`, else the
  inventory), `Recipe.Yield` is applied, the yield breakdown and the cycle's output item are
  recorded (so `rpgstations:output_items` applies in a Steps program), the outputs are produced to
  the inventory as ordinary rows, and only then does the conversion COMMIT
  (`StationService.commitIteration`: the refund ledger clears and the consumption reaches the ONE
  input-consumed hook, below); a produce that fails returns before the commit, so the inputs are
  still refunded at stop and no listener hears of a consumption that was undone. The implicit
  program is now `Convert` + `Roll` + `Presentation` on one step, so the classic loop and an
  authored beat convert through ONE code path; the loop still chooses its row before dispatch
  (idle practice, the OUT_OF_INPUTS / INVENTORY_FULL stops, the per-conversion pace and the
  feedable-cycles count all ride that choice) and hands it to the phase as its preselected row. A
  switched-on convert IS work (`IsWork` derives true; a `Convert {Enabled: false}` is not). Phase
  order is now Walk -> the block state and the step's display overlay -> Consume -> Stamp ->
  Convert -> Produce -> Roll -> Commands -> entry cues -> Duration.
  `LOOT_OUTPUT_ITEMS_NO_CYCLE_OUTPUT` fires only on a program with no Convert beat, and names the
  missing beat. Validator: `CONVERT_WITHOUT_RECIPE`, `CONVERT_WITH_CONSUME_PRODUCE`,
  `CONVERT_REPEATED` (INFO).
- **ONE input-consumed hook, reached from every path on which a station consumes input, and the
  api event it fires.** `StationService.onInputConsumed(Store, InputConsumption)` is called exactly
  once per committed consumption, after the commit, and never for a consumption the engine gave
  back. Its session-free record (`InputConsumption`) carries the worker (null on an unattended
  settle, and whole on an attended one: handle, entity ref and uuid), the world (the STATION's own,
  read off the worker's entity; a `PlayerRef` names no world only before its first join, never
  between worlds, so it is not consulted), the block the consumed pile stood at, the station and
  action ids, and the consumed stacks, each with the custody socket it left (`ConsumedInput`: the
  placed piece's own stack when a single-item socket gave it up, else a bare stack per drained item
  id and count). An attended batch whose session has lost part of its worker, or whose entity is
  gone, is not reported (teardown racing the phase). A session consume (a `Consume` phase, or a
  `Convert` phase's drain) records its batch into a new hook half of the iteration ledger
  (`StationSession.iterationConsumedInputs`); the iteration's commit
  (`StationService.commitIteration`: a committed produce on either route, a committed conversion,
  a completed program pass) clears the refund halves and then reports each batch once, and a
  refund at stop drops it unreported. A `Stamp` phase's reagents are reported the moment the
  enhanced stack commits, since no later stop refunds them. An unattended settle reports what it
  drained, per socket pile (`StationUnattended.Settle.drains`), once its transform is committed to
  the stash; like every attended custody consume, a settle that takes a single-item socket's last
  now takes the piece's stack off the pile with it and drops that socket's prop. The hook's body
  fires the api's new **`StationInputConsumedEvent`** (`StationEvents.fireInputConsumed`): the
  worker (null on an unattended settle, the one shape with none; `attended()`), the world and
  block, the station and action ids, and each consumed stack as an immutable copy with its socket
  and the quality index and item level read off it through Ziggfreed Common's `ItemReadings`
  (null where the stack cannot tell; an inventory-route stack is a bare id and count, so wear and
  metadata are not on it). `InputConsumedHookOrderTest` pins each path's order on the source (the
  consumption lands, then commits, then reaches the hook) and the hook's three call sites, one per
  commit shape.
- **The `STATION_INPUT` objective kind.** `Server/ZiggfreedCommon/ObjectiveKinds/RpgStations/
  Station_Input.json` sits beside `Work_Station` and `Station_Output` with the same `Target` /
  `Qualifier` / `MatchMode` semantics: target the item id, qualifier the station id, amount the
  stack's quantity, one moment per stack the input event carries
  (`progression.StationProgressProducers.onInputConsumedEvent`, riding the api event like the
  other two producers, with `StationInputPayload` on the moment). An unattended settle names no
  worker, so it credits nobody: progress belongs to a player who was there. The step sentences
  `objective.text.station_input` and `.any` ship in `rpgstations.lang` (en-US authored here; the
  other locales follow). A step that should count one station and its greater tier authors the
  shared stem with Ziggfreed Common's new `QualifierMatchMode: "PREFIX"`.
- **Placement safety.** `Custody.HeldOnly` (default false) places only what the player holds: the
  hotbar and backpack are never scanned for a match, so a press at a station that takes one
  valuable piece can never pull gear out of the bag unasked. A COUNT pile (a socket capacity above
  one) refuses a stack carrying per-instance data, a tool that tracks wear or a stack with
  metadata (`StationCustody.carriesInstanceData`), since a pile keeps ids and counts only and
  would hand it back as a bare fresh stack: a free repair, or a lost enhancement; a single-item
  socket keeps the real stack and takes it. A protected piece, on the server-wide protect-list
  (below) or in a station's own `Except` hole, is refused with its own reason:
  `PlacementDenial.PROTECTED` (first in the enum, so it outranks every other denial), the
  `ui.station.protected` key in `rpgstations.lang` (en-US; the other locales follow) and
  `RpgStationsLangKeys`, and the moment `Refused:Protected` through `StationRefusals`; it is the
  one placement denial that keeps its own key on a socket-less custody, where every other denial
  folds to `no_materials`. Like every other placement denial it refuses only at a station that was
  EMPTY before the press (and cannot idle); at a loaded station the press falls through to the
  engage gates, where the held item is judged as a TOOL, never as input, so an owner who protects
  a trophy tool still works a loaded station with it (`StationService.unplacedPressRefusal`).
- **The server-wide protect-list, a store of its own where every layer counts.** Adds
  `Server/RpgStations/ProtectLists/<Name>.json` (`ProtectListAsset`, a Pattern A store folded into
  `ProtectListCatalog`): each file carries `Protects`, what it protects, as the shared input matcher
  (one `ActionInput` or an array, each with its own `Except` hole; an entry authoring no route
  protects nothing, never everything), and may be scoped with `Stations` and `Actions` (absent or
  empty = every consuming station, or every action; both authored = both must match; ids matched
  without regard to case). Every loaded file ADDS to one server-wide list, whether it comes from the
  jar, a pack or the owner's own pack, so no layer's list replaces another's; a later layer's file
  with the same name replaces the earlier one inside the store, which is how an entry is taken
  back. The check runs before any socket is offered the piece (`StationCustody.isProtected` over the
  files in scope at the station and action). The jar ships no file: the list is the server owner's
  policy, and a station that must refuse one of its own pieces says so in its own
  `Custody.Input.Except`, in the station's file. Validator: `PROTECT_LIST_EMPTY`,
  `PROTECT_LIST_CATCH_ALL`, `PROTECT_LIST_UNKNOWN_STATION`, `PROTECT_LIST_UNKNOWN_ACTION`,
  `PROTECT_LIST_UNKNOWN_ITEM` (INFO), plus the shared `EXCEPT_CATCH_ALL` and
  `UNKNOWN_ACTION_FUNCTION` on each entry.
- **Expected versus found loot rows.** A roll authored `Expected: true` (Ziggfreed Common's new
  `Roll` leaf) pays the moment's EXPECTED payout, a wage or a return, and its items read as
  ORDINARY produced output: the plain item row on the HUD and the produced ledger row on the
  summary, exactly like the cycle's own output; everything else a pass pays stays the gold find
  row. The origin rides the shared engine's per-roll split (`LootEngine.Result.getExpectedItems` /
  `getFoundItems`) into `StationLootEngine.GrantResult` (`getExpectedItems` / `getFoundItems`
  beside the unchanged merged `getDropListItems`), and both grant routes (`applyGrantResult`,
  `applyGatherGrantResult`) split on it. No shipped Sawmill table authors the knob, so every
  Sawmill row is what it was (`StationLootOriginTest` pins the shipped files).
- **The presentation `Target`.** A `Presentation` names where its sounds and particles play:
  `"Block"` (the default, the block centre, where every moment has always played), `"Display"`
  (the placed piece's prop, the socket a ritual queue is working, else the first socket showing
  one; where that prop last stood when it is gone, which is where a `Convert` beat's cues land
  after the beat consumed the piece and dropped its prop) or `"Puppet"` (the worker's double; the
  BLOCK when no double stands, so a moment's sounds and particles never land on the worker's own
  body), a bare word or `{Kind, Node}` to attach the particles to one named node of the double's
  model. The target is resolved when the cue PLAYS (`StationService.resolveAim` over the pure
  `aimSource`), so a delayed cue lands where its target is when it comes due. At an entity target
  the sounds follow the entity (Ziggfreed Common's `Sound3D.playOn`), delivered only to the players
  whose tracker shows it, never the engine's world-wide broadcast. A particle system RIDES the
  entity (`ModelParticleService.spawnOn`) only when it provably ends on its own, its native asset
  giving it a positive `LifeSpan` (read through Ziggfreed Common's `ParticleLifetimes`, which sits
  beside the attached-particle sender): an attached system carries no playback cap, so it lives until that lifetime ends or the entity is removed (a prop when its piece is
  consumed or taken back, a double when the session ends), and `DurationSeconds` cannot shorten it.
  Any other system plays at the entity's position under its `DurationSeconds` cap, so nothing
  endless ever rides a prop or a double. A freshly spawned prop or double has been shown to nobody
  until the tracker's next tick, so a cue in the same beat falls back to its position and no cue is
  lost. The shake stays on the worker's camera and the interaction on the worker. A queued pass's
  beat cues therefore play at the current socket's prop once a `Target: "Display"` is authored;
  unauthored, they play at the block as before. The sessionless route (refusals, structures, the
  gather) has no session to aim through and plays at the block. Validator:
  `PRESENTATION_TARGET_UNKNOWN_KIND`, `PRESENTATION_TARGET_NODE_AT_BLOCK` (INFO),
  `PRESENTATION_DISPLAY_TARGET_NO_DISPLAY`, `PRESENTATION_PUPPET_TARGET_NO_PUPPET` (INFO),
  `PRESENTATION_ENTITY_TARGET_UNBOUNDED` (a burst at an entity target whose system has no positive
  `LifeSpan` the server can read, so it plays at the entity's position instead of riding it) and
  `PRESENTATION_ENTITY_TARGET_CAP_IGNORED` (INFO: a riding burst authors a `DurationSeconds` that
  does nothing there).
- **An effect on the double.** `EffectRef.Target` is `"Player"` (the default, the worker's own
  body, what a `LocalSoundEventId` sting or a screen effect needs) or `"Puppet"` (the worker's
  double, for an aura or a ModelVFX the onlookers see on the performer; the worker's own body when
  no double stands). The double carries no `EntityStatMap`, so the engine's effect timer never runs
  on it: an effect there is put on with NO expiry (Ziggfreed Common's
  `NativeEffectUtil.applyInfinite`, the double's effect controller added before its spawn by
  `PlayerPuppetService`) and an authored `DurationMs` is kept by this engine's own cue clock
  (`StationService.queueEffectRemoval`, a removal parked on the one delayed-cue queue and drained
  into `NativeEffectUtil.remove`); every effect is tracked on the session either way, so the
  teardown strips whatever is still on. An extension overlay carries the `Target` through
  (`ExtensionCatalog.overlayEffectRef`). The `Target` is read on a presentation's `Effect` only.
  Validator: `EFFECT_TARGET_UNKNOWN`, `EFFECT_PUPPET_TARGET_NO_PUPPET` (INFO), and
  `EFFECT_TARGET_IGNORED` for a `Target` authored where it means nothing (a `Puppet.Hide.Effect`,
  an `rpgstations:effect` reward).
- **A `Color` on a presentation burst.** `Presentation.Particles[].Color` is a `#rrggbb` tint
  applied through the engine's own colour argument, so a tintable vanilla system takes a moment's
  palette without a copied spawner file. Every block-positioned burst now spawns through Ziggfreed
  Common's full-arity `ModelParticleService.spawnAt` (rotation, scale, the tint and the playback cap
  together, the one engine overload that carries both a colour and a cap), so a tinted burst keeps
  its leak guard. Validator: `PRESENTATION_PARTICLE_BAD_COLOR`.
- **A per-step block state.** `StationStep.State` names a block `State.Definitions` name a work
  beat holds its `At` block in INSTEAD of the custody's `Working` name, a deeper look of one
  ritual. It needs the custody's `States` group (the exit reads the resting look off it), applies
  on a work step only, and must exist in the block's own definitions; the working flip now
  tracks the state NAME it wears (`WorkingFlip.stateName`), so the same block re-flips in place
  from one beat's name to the next and never shows the resting look between. Validator:
  `STEP_STATE_WITHOUT_STATES`, `STEP_STATE_NOT_WORK` (INFO).
- **Display motion and a per-beat display overlay.** `Custody.Display.Animated` (default false)
  spawns the prop with the client's own dropped-item turn and bob, a spawn-time option of the
  shared prop primitive. `StationStep.Display` overlays the worked piece's `Display` for one beat
  (`Custody.Display.overlaid`, the same per-leaf overlay an extension's `Custody` uses; `Offset`,
  `Scale`, `Rotation` and `Animated`, each authored leaf replacing the socket's own), respawning
  the prop at iteration entry when the look changed (`StationService.applyStepDisplay`; the
  display handle records the group it was spawned with, so the same overlay twice never
  respawns), so a beat can lift the piece, set it turning or enlarge it. The session remembers the
  look it last dressed each socket's prop in (`StationSession.shownDisplays`), past the prop's
  despawn, so a `Display`-targeted cue after the `Convert` beat lands where the lifted piece last
  stood. Validator: `STEP_DISPLAY_WITHOUT_DISPLAY`.
- **The placed-piece preview.** `Custody.Preview` (default false) toasts, on placement, what the
  piece will give back: the row the recipe would run for it right now (authored, derived, or a
  fallback route's), listed as count and name (`ui.station.preview.returns`), or that it gives
  nothing back on its own when only the essence-only route takes it
  (`ui.station.preview.nothing_back`); a piece no row runs for right now says nothing. The lines
  are worded for any recipe station. The selection is the one a cycle or a Convert beat makes,
  addressed to the socket the piece landed in, whether it came from the hand or the backpack.
  Validator: `CUSTODY_PREVIEW_WITHOUT_RECIPE`.
- **Custody correctness, three fixes.** A custody consume that takes the last of a single-item
  socket's piece now takes the pile's metadata-bearing `Unique` stack with it
  (`StationCustodyClaim.takeUniqueIfDrained`), so the placed piece is actually destroyed and its
  prop despawns in the same tick; before, the stack stayed and was handed back at stop as a piece
  that no longer existed. The refund ledger gained a unique-stack half
  (`StationSession.iterationConsumedUnique`): an interrupted ritual puts the SAME stack back on its
  pile, wear and stamps intact, or hands it to the player as itself when the pile is gone, never as
  a bare fresh stack (`StationCustodyLedger.countsBesideUnique` nets the count half). Only a stack
  the drain itself took is ledgered (`StationCustodyLedger.pieceTaken`), so a `Unique` already
  left dangling on its pile is cleared off it and never put back. A completed program pass now
  clears every half of the ledger (it commits through `StationService.commitIteration`), not only
  the inventory half. A pile whose `Unique` outlived its count (the shape the old consume left
  behind) reads as holding nothing, answers no unique stack and hands nothing back for it; the
  decision behind that (`StationCustody.uniqueDrained`) is what the tests pin, while a 1.0.0
  stash of that shape loading through the engine is a dev-server check, since no unit test can
  build one.
- **The piece in the factors.** The session captures the piece the work is about
  (`StationSession.factorItem`: the addressed socket's unique stack, else a bare stack of the
  oldest matching material, else the inventory route's exact-item input) before every snapshot,
  re-captured on each program pass and carried across a resume, and every factor-context build
  site publishes it through the api `FactorContext.item()` into Ziggfreed Common's item leaf, the
  sessionless gather twin included, and the engage-time `Requires` gate, which reads the piece
  standing in the block's custody (the same first-socket capture a pass makes) so a gate over
  `hytale:item_*` judges the placed piece; the inventory route's piece is unknown before a
  conversion is chosen, so that gate reads no item. The four portable item factors
  (`hytale:item_quality`, `hytale:item_level`, `hytale:item_durability_percent`,
  `hytale:item_stat`) are adopted into the station vocabulary, and
  `ziggfreedcommon:item_stamp_points` reaches it through the library's process-wide contribution.
  The three `hytale:tool_*` readings moved onto the shared item reader (`StationToolReadings` over
  `ItemReadings`) with the station's own 0 / 100 defaults; `tool_quality` reads the held ITEM's
  current quality, never the stack's copied index, so its values are the 1.0.0 ones.
- **A ritual queue.** `Work.Queue: true` runs the authored `Steps` program once per FILLED custody
  socket in authored order: each pass takes the next filled socket, captures its stack for the
  factors, addresses it for the `Convert` phase's drain, and consumes it; while another filled
  socket waits, the next pass starts on the next frame; once every socket is empty the ordinary
  end-of-pass rule decides (`Looping false` ends the session). An interrupted pass refunds only the
  socket in progress; the other pieces follow the station's standing custody-return rule at stop.
  A pass's beat cues play at the socket it works once the beat's presentation authors
  `Target: "Display"` (the current socket's prop, or its resting position after the Convert beat
  dropped it); unauthored, they play at the block. A one-socket station is unaffected. Validator:
  `QUEUE_WITHOUT_STEPS`, `QUEUE_WITHOUT_SOCKETS`, `QUEUE_SOCKET_NOT_SINGLE`, `QUEUE_WITH_LOOPING`
  (INFO).
- **Paced beats.** `ActionDef.Pace {Ladder, Clamp}`: the ladder is the `ContributionScale` codec
  (`Factors` + `Floors [{Min, Scale}]`, never a second ladder shape) and `Clamp {Min, Max}` is the
  shared clamp leaf. A step marked `Paced: true` has its `Duration.Ms` multiplied by the resolved
  pace, and its own presentation's `DelayMs`, each sound's `DelayMs` and each burst's
  `DurationSeconds` stretched with it, so its accents keep their place; an unpaced beat keeps its
  authored length. Each matching `ExtensionAsset` may carry its OWN complete `Pace` ladder, typed
  `{Ladder}` only (`Pace.LADDER_ONLY_CODEC`; there is no `Clamp` leaf on an extension's pace): the
  scales MULTIPLY (each ladder from its own thresholds) and the action's `Clamp` alone bounds the
  product, so a jar ladder over one factor and a pack ladder over another compose without either
  restating the other (`StationPacing`). Because the action's clamp is the one bound on the
  composed pace, an action's `Pace` should author BOTH sides of it; `PACE_UNCLAMPED` warns on a
  missing clamp or a missing side. The pace resolves ONCE at step entry and is cached beside the
  repeat count (`StationSession.stepPaceScale`), so a resume never re-scales a committed deadline.
  Validator: `PACE_WITHOUT_STEPS`, `PACE_NO_PACED_STEP`, `PACE_UNCLAMPED`, `PACE_CLAMP_INVERTED`,
  `PACE_FLOOR_NONPOSITIVE`, `PACE_EXTENSION_NO_LADDER`, `PACED_STEP_WITHOUT_PACE` (INFO).
- **Finds on a beat.** `StationStep.RollBonus: true` rolls the action's `Bonus` (its own tables and
  rolls plus every matching extension's) at that beat, once per iteration, and a program with such
  a beat skips its completion-time pass so the Bonus never rolls twice. Unset, nothing moves.
  Validator: `BONUS_AT_BEAT_NO_BONUS`, `BONUS_AT_BEAT_REPEATED` (INFO).
- **Extension overlays keep every leaf.** An extension's `Custody.States` overlay now keeps the
  base's `Ready` and `Overdone` names (before, authoring any `States` group dropped them), and its
  `EffectRef` overlay keeps the `Target`.
- **Docs.** `SCHEMA.md` regenerated for every leaf above. New guides:
  `docs/derive-from-any-bench.md` (the Sawmill and the Disenchanting Table as the two worked
  examples) and `docs/disenchanting.md` (the staged ritual, end to end). `docs/loot-and-factors.md`
  names the item factors and corrects its Grants table (`Effects` and `Contributions` are the
  `rpgstations:effect` and `rpgstations:contribution` reward kinds, never keys of `Grants`);
  `docs/integrations.md` carries the tenth event, the third kind and the api version scheme; the
  custody, presentation and settings guides carry the leaves above, and the custody guide describes
  the protect-list store and its layering rule. Three claims the docs made against the code are
  corrected everywhere they appeared (the routers, the javadoc, the codec documentation and so
  `SCHEMA.md`): a custody state flip is not hint-only (it swaps the block to the state's own
  variant, so the state's texture, animation, light, ambient loop and particles come with it), the
  display and puppet anchor is the block CENTRE, not the block top, and the engine does have a
  colour call for particles. Its positional sound call and its entity-following packet both take a
  volume and a pitch modifier too, which corrects the 1.0.0 notes below, where the positional call
  is twice said to take neither; a cue's loudness and tone still live on its `SoundEvent` asset,
  and a quieter or pitched cue is a one-line derived sound asset (`{"Parent": "<vanilla id>",
  "Pitch": .., "Volume": ..}`), so no per-cue leaf is added.
- **Technical.** The api artifact is versioned WITH the mod (`api_version=1.1.0`; 1.0.0 carried
  contract 9, 1.1.0 carries 10), and `RpgStationsApi.apiVersion()` moves to **10** for ONE batch:
  the `StationInputConsumedEvent` event class and the `FactorContext.item()` accessor with its
  `Builder.item(ItemStack)` leaf (the piece a moment is about); the api router and the interface
  javadoc no longer tie a freeze to a release number, since none was declared.
  `StationService.ConversionCheck` carries the chosen row and is package-visible for the step
  context; `StationStepContext` carries the station asset, the composed pace and the preselected
  row. `ImplicitProgram.build` takes the Bonus and the cycle presentation only. A consume body
  records `ConsumedInput`s (the real stack plus the socket it left) into the iteration ledger's
  hook half beside its refund halves. The consumption paths run only against a live store, so
  `InputConsumedHookOrderTest` and the fallback selection pin read the order off the source, one
  method body at a time (`SourcePins`, a test-only reader that cuts a body at its matching brace).
  `StationService.placementDenyKey`, `unplacedPressRefusal`, `socketAccepts` (placement acceptance
  over the live reads it is handed), `aimSource`, `effectGoesOnDouble`, `workingMove` and
  `workingStateName`, `placedPiece` (the socket the placed-piece preview names), `restingDisplay`
  and `restingPosition` (where a cue aimed at a consumed piece lands, under its last per-beat
  look), `StationCustody.carriesInstanceData` over the raw readings, and `MomentBursts` (which
  bursts ride the aimed entity and which play at its position, over Ziggfreed Common's
  `ParticleLifetimes`) are package-visible pure cores, each pinned by a test, and `SourcePins` gained
  `loopBodies` for the grant routing pin. `Presentation.of` gained the `Target`-carrying overload
  every rebuild site uses (`StationPacing.scaleInTime`, the offset-sound split,
  `Presentation.overlaid`). The test task declares `docs/` as an input, so a docs-only change
  re-runs `MmoAgnosticismTest`.

## 1.0.0 - 2026-09-12 (first public release)

**1.0.0 is RPG Stations' first public release.** Everything in this section shipped into this one
version; there is no prior public release to diff against, so every entry below is additive by
definition.

- **The summary panel wears the colour every HUD card shares, or one of its own.** The
  end-of-session panel is a HUD card like Ziggfreed Common's bar panels and quest tracker, and it
  reads the one look that library ships for all of them (`Server/ZiggfreedCommon/HudCards/Default.json`,
  owner layer `mods/ziggfreedcommon/hud-cards.json`: one hex multiplied over the frame, eight digits
  carrying a transparency in the last two, `#ffffffb8` being the same panel at about 72 percent), so
  a server owner dims every card on the server at once from one file. `SummaryHud.Color` in
  `Settings.json` gives this panel a colour of its own over it (`RpgStationsSettingsAsset.SummaryHud`,
  raw as authored; `StationSummaryHud.cardLook()` validates it through the library's `HudCardLook`,
  a value that is not a hex being ignored with one line in the log). The colour is pushed through
  `UiRetint.retintColor` on the frame's element (`#RpgStationSummaryRoot #Content`, the root carrying
  no background) with every summary, after a listening mod's decorate hook, so an authored colour is
  the owner's last word and an absent one keeps that mod's theme; the shipped look sends nothing.
  `SCHEMA.md` and the settings guide carry the leaf. Tests: `RpgStationsSettingsAssetCodecTest`,
  `StationSummaryHudTest`.
- **A station that turns you away says so, and every id reads `Is_Like_This`.** Pressing F at a
  Sawmill with nothing to mill used to stack the same yellow notice twice and make no sound at all,
  so the station read as broken rather than as refusing. Every denial - nothing to work with, the
  wrong tool, a worn tool, a full socket, someone else's pile, a busy or missing anchor, a locked
  gate, a full server, a structure that cannot be raised, a retrieve at a busy block - now answers
  through one seam (`station.StationRefusals`): the notice, a `Refused:<Reason>` moment cue, and a
  native `StationRefusedEvent` on the api (apiVersion 9, artifact 0.9.0). The cue resolves nearest
  first, per leaf: the action's `Refused:<Reason>`, the action's `Refused`, the settings'
  `Refused:<Reason>`, the settings' `Refused`, an omitted leaf falling through and an authored empty
  `Sounds` array meaning silence; a flair overlays the result like any other moment. The jar's
  `Settings.json` ships the one engine-wide default, `Refused` playing
  `SFX_Generic_Crafting_Failed`, the sound the vanilla benches make when they cannot proceed, so a
  station with no refusal cue of its own still sounds like it heard you. A repeat of the same reason
  by the same player at the same block inside `Refusals.RepeatWindowMs` (default 1500) is a mashed
  key: no second notice, no second particle burst or shake, no event - but the sound plays on every
  press, on purpose, so the station always answers audibly. `StationStructures`' own 5s
  refusal-toast throttle is retired into the seam. With it, every id this mod mints or accepts
  is underscore-separated PascalCase, matching Hytale's own: the moment constants are `Cycle`,
  `Swing`, `Impact`, `Rare_Find`, `Completion`, `Ready`, `Overdone`, `Refused`; the prefixes are
  `Cue:`, `Step:` (a composed `Step:Mill:Chop` keeps its parts' authored casing) and `Refused:`; a
  pattern's moments are `Activated`/`Broken`; the reserved anchor is `Self`; the Sawmill's cues are
  `Cue:Find_Deep`/`Cue:Find_Apex`/`Cue:Trophy` and its finds name `Rare_Find`. Matching stays
  case-insensitive everywhere (`StationFlairs.caseInsensitiveMomentKeys` holds every moment map as a
  case-insensitive map that keeps the authored spelling; no validator refuses a lowercase id), so
  older-authored content keeps resolving unchanged. `SCHEMA.md`, the guides and the routers carry
  the convention.
- **What a mill makes is counted on the shared HUD panel, not shouted down the feed.** The corner
  feed drains strictly oldest first and stops at the first entry that has not expired, and an item
  notice merges into a matching entry and refreshes it where it stands, so a station reporting its
  output every cycle held the front of the feed for the whole run. Every ordinary output (produced
  stacks, bonus units, stacks taken out with press-F) goes to Ziggfreed Common's progress-bar panel
  instead, one row per item with its own icon, name and a number climbing beside it, nothing
  authored for any of it (`HudBars.itemMoved`). A retrieve keeps its pickup sound at the block.
- **A lucky find gets its own gold row, and the run gets a bar of its own.** A find is rare enough
  to be worth pointing out and too frequent to hold the feed, so it takes a gold row per item on
  the panel, apart from that item's ordinary running total. The session itself has a bar: its gain
  is the cycles behind the worker and its fill is the cycles the pile in front of them can still
  feed, out of what it could feed when the run began, so it drains as the materials run down and
  climbs back when someone tops the station up. The engine only ever asked whether a conversion
  could run at all, never how many more times, so that count is a new read through the matchers
  the question already builds; a cycle can land in four places and a suspended program's dispatch
  answers the same for "a cycle landed" and "still waiting", so each compares the count either side
  of the call rather than trusting the boolean.
- **A run's rows are its ledger, and stepping away ends it sooner.** Every row a session puts up is
  HELD for the whole run and they go away together when it stops, from the one funnel every stop
  path reaches, silent and abnormal ones included; nothing fades out from under a total still being
  added to. The numbers come from the maps the end-of-session summary already reads, so the bar and
  the summary cannot disagree, and the session row states the cycles behind the worker as a total
  worded by its own key (a count of cycles is not a gain). An unattended gather and a press-F
  retrieve stay plain gains that fade on their own, having no live run to fold into. The walk-off
  distance drops to half a block, since it is now also what takes the run's ledger off the screen;
  the Sawmill stops pinning its own and takes the default.
- **The Sawmill's placed logs sit on the bench instead of sunk into it.** The pile drew at
  `Offset.Y` -0.1 and 0.46 scale, half inside the bench top, and read as a token rather than a
  load; it sits at 0.15 and 0.75, clear of the surface at just over half a real block, and the
  comment says which way each knob moves the pile.
- **A run that finishes on its own holds its summary panel until the worker walks away.** A station
  that works through a stack of logs finishes whenever it finishes, often minutes after whoever
  started it stopped watching, so a panel on a six-second timer would be gone before they read it.
  When the material runs out, a repeating program works its inputs down, or a ritual completes, the
  panel stays up while the worker is still standing where the run left them, and `SummaryHud.TtlMs`
  runs from the moment they step outside the station's own walk-off radius, so the summary is still
  readable on the way out. Engaging the station again releases it the same way. A stop the worker
  made themselves (crouching out, walking off, swapping tools, taking a hit) keeps the plain timed
  panel, since they are already leaving.
- **The session summary grows to twelve ledger rows, and `SummaryHud.MaxRows` draws fewer.** A long
  run fills the panel: two contribution rows from a listening mod, what it consumed, what it
  produced and a lucky find each get a line, and the panel folded everything past six into
  `+N more` with no way to read them. `Pages/RpgStationSummary.ui` declares twelve row slots
  (`#RpgStationSummaryItem0..11`) and `StationSummaryHud.MAX_LEDGER_ROWS` matches; the panel is
  content-height sized, so a quiet session still draws a short one and only a busy session grows.
  `SummaryHud.MaxRows` is the authored cap, held to the slots the document declares, read per push
  so a reload lands on the next summary. `StationSummaryHudTest` pins the slot run against the
  constant, since a command written against a slot the document does not declare crashes the
  client.
- **What a station makes shows on the corner HUD, not the notification feed.** Every produced
  stack, every bonus unit and every stack taken out with press-F is counted on Ziggfreed Common's
  progress-bar panel, one row per item: the item's own icon and name, the number climbing beside
  it for as long as the run keeps going, gone a few seconds after the last gain, with nothing
  authored for it (a vanilla item, a pack item and an id nobody has written a file for all read the
  same). The feed drains strictly oldest first and merging into an entry refreshes it in place, so
  an item notice landing every cycle pinned everything behind it for a whole session; the panel
  has no such rule. A retrieve still sounds like a pickup at the block (`PickupMimic.playPickupSfx`,
  the cue alone). The one output notice left on the feed is a lucky find (`notifyLuckyFind`, gold,
  tagged per item so a second find grows the same line), because it is rare and its words have no
  home on a row. Denials, a seat unavailable, inventory full and the rest of the one-off notices
  stay where they were.
- **Requires ZiggfreedCommon 2.1.0 or newer.** The manifest floor and the compile pin move together with the library's 2.1.0 release, the version the whole mod family stands on; a 2.0.x jar fails this mod's load by name rather than mid-cycle.
- **The station objective kinds are this engine's own: `WORK_STATION` and `STATION_OUTPUT`.**
  RPG Stations fires both into ziggfreed-common's shared progression runtime itself
  (`progression/StationProgressProducers`, listening to its own api events so the moment content
  advances on is exactly the moment a consumer sees): `WORK_STATION` once per real completed cycle
  (target the station id, amount 1; an idle-practice cycle counts for nothing) and
  `STATION_OUTPUT` once per stack a worker is handed (target the item id, qualifier the station id,
  amount the stack's quantity). It ships the two kind files
  (`Server/ZiggfreedCommon/ObjectiveKinds/RpgStations/{Work_Station,Station_Output}.json`, folded
  into the library's kind registry from the file alone, each with a `TargetIcons` picture for the
  mill) and the four `objective.text.*` step templates in all nine locales, so a quest or
  achievement authored against either kind advances from station play with only ziggfreed-common
  and this jar installed. A `Grants.Commands` payout and unattended work are invisible to
  `STATION_OUTPUT` by design; each moment carries a typed payload (`StationWorkPayload` /
  `StationOutputPayload`) wrapping the api event it came off.
- **Every item a grant pass hands a worker is reported as station output (apiVersion 8, artifact
  0.8.0).** `StationOutputProducedEvent` fires once per committed grant pass beside its once per
  committed produce phase: the batch carries the cycle's `Grants.OutputItems` bonus units as the
  count that landed, every `Grants.Items` stack and every `Grants.DropLists` find, with a null
  socket and the paying action, from the per-cycle `Roll` phase, an authored program's completed
  pass and the completion pass alike. A pass that landed nothing fires nothing, a command payout is
  never reported (the engine cannot know what a command gave), and an unattended settle or gather
  still fires nothing (that output surfaces on `StationUnattendedGatheredEvent`).
  `StationService.grantBonusOutputItems` answers the landed count, and the output funnel is static
  since it reads only session fields.
- **The Sawmiller's Hatchet is an item grant.** `SawmillTrophy.json` pays the trophy through
  `Grants.Items` (one `RPG_Tool_Hatchet_Sawmiller`: hotbar first, then the backpack, then the
  ground at the station when the bag is full), so the win is countable station output and the
  `Cue:Trophy` fanfare plays only when the hatchet actually landed.
- `ShippedAssetDecodeTest` also decodes what the jar ships into the shared library's stores
  (`Server/ZiggfreedCommon/Lootables` and `ObjectiveKinds`) through the library's own codecs, keyed
  by the store folder directly under each root so a store may group its files one level deeper.
- **Placed custody survives restarts, chunk unloads and logoffs.** Materials placed into a station
  are stored on the block's own chunk (the shared library's per-block stash store) and saved with
  it, exactly like a chest's contents: leave logs in the sawmill, restart the server, and they are
  still there to mill; log off and your placed materials wait in the world for your return. A
  single-item placement keeps its full metadata (durability, enhancement rolls) across a restart
  too. A session stop while the player is present (re-press, walk-off, damage, death, running out
  of inputs) still hands materials back to the inventory; disconnecting leaves them placed, and
  breaking the block still drops them at the block once. The floating placed-item display respawns
  from the persisted contents shortly after the chunk loads (the unattended pass's hydrate walk,
  budgeted to a handful of prop spawns per world per tick), with the first interaction as the
  immediate fallback. Caveat: the live in-game chunk save/load round trip plus the enhanced-item
  metadata survival are verified in play rather than by the build's tests.
- **Unattended work: stations that keep working while nobody stands at them.** An action
  authoring `Work.Unattended` (the group's presence is the opt-in; `MaxCycles` and `CatchUpMaxMs`
  bound one settle burst at 24 cycles and 24 hours of catch-up by default) keeps settling its
  recipe conversions against the block's placed custody piles on world GAME time - load the pot,
  walk away, and the ingredients keep becoming stew, in loaded chunks, with an outage cooking and
  owing nothing. The transform is immediate (inputs drain, outputs land in their piles, the
  doneness window opens and can burn exactly as an attended batch would); the loot rolls and
  contribution posts those cycles would have earned ACCRUE on the output pile and pay out to
  whoever GATHERS it - evaluated as if attended with the gatherer as the worker, at the idle
  contribution rate, capped by the same `MaxCycles` ceiling - and the new
  `StationUnattendedGatheredEvent` carries the batch (gatherer, cycle count, already-scaled
  contributions) to any listening mod. A live session always wins (the pass skips a block being
  worked); authored `Steps`/`Anchors` programs stay attended-only (the validator says so);
  `Limits.UnattendedIntervalMs` paces each world's pass. The same pass doubles as the
  chunk-load hydrate walk that rebuilds missing placed-item displays and block states. Caveats:
  the live loaded-section walk, the in-game prop respawn timing, and a real multi-hour catch-up
  are verified in play rather than by the build's tests (the settle math, clamps, accrual
  namespace and gather payout are unit-pinned).
- **Custody sockets: named placement slots with their own piles, owners and displays.** A station's
  `Custody` group can author a `Sockets` map - a stew pot's meat rack beside its herb basket beside
  its output shelf - each slot with its own acceptance matcher, capacity (the smaller of its own cap
  and the custody-level one, which also caps the block's total across every slot), per-slot
  `SingleFamily` lock, its own placed-item display prop, and one-at-a-time loading via
  `PlacePerPress` (absent keeps the classic whole-stack press). A pressed stack routes to the first
  accepting slot in authored order, refusals say exactly what stood in the way (no room, nothing
  takes that material, a required slot unfilled), and a station authoring no sockets behaves exactly
  as before through one implicit slot. A `Block` socket is a real world block beside the station
  (the pot on the fire), matched by item identity at a facing-relative offset that rotates with the
  placed station block, and re-checked while working: breaking it ends the session gracefully. Step
  programs and recipe rows address slots by id (a phase-level `Socket` plus a per-entry one), so a
  set recipe can draw meat from one slot and greens from another in a single row.
- **Sharing placed materials.** Every placed pile belongs to exactly one player - whoever put the
  first item in (a produced pile belongs to whoever did the work) - and three independent `Share`
  knobs (per station or per socket, all default off) open it up deliberately: `Place` lets someone
  else start a pile in an EMPTY slot (they own it until it drains empty again; materials never mix
  inside one pile), `Use` lets them work from it, and `Reclaim` lets them take it back out. A
  session stop hands back only the stopping player's own piles; an interrupted work cycle refunds
  what it consumed into the pile it came from; everything else stays standing for its owner.
- **Set recipes: tag and match-any ingredients, tiers, and exact sets.** A recipe row's input now
  names its material through any of three routes - an exact `ItemId`, a `ResourceTypeId` family, or
  native item `Tags` (the same tag map action selection and socket matchers use, so "any item
  tagged CookingIngredient" is one input line; a family key with an empty value list matches on the
  key alone) - or through NO route at all, the match-any input that accepts whatever its custody
  pile holds ("three of anything in the pot"; it never draws from a player's open inventory, and
  the validator flags a match-any row on a station without placed custody). Two optional knobs
  order a multi-row recipe: `Tier` (lower scans first, authored order unchanged inside a tier, and
  a file authoring no tiers keeps exactly its authored order; native-derived rows run at tier 1, so
  any hand-written row outranks the derived block by default) and `IsExactSet` (the row matches
  only while the pile(s) it draws from hold nothing beyond its own inputs, per input `Socket`;
  extras elsewhere never block) - so "exactly 2 meat + 1 vegetable makes kebab, anything else makes
  stew" is two rows and zero code. Ordering stays explicit and readable: the engine never
  invisibly reorders, and two informational validator findings nudge the exact-first /
  match-any-last authoring convention. Native derivation also widens: recipe inputs selecting by
  native `ItemTag` now derive (previously the whole recipe was skipped), and a Crafting-type
  bench's category rows (the Cookingbench tabs) derive exactly like a Processing bench's. Item
  matching itself now runs through the shared library's one `ItemMatch` core, so a recipe
  ingredient, an action selector and a socket matcher can never drift apart.
- **Doneness: a ready window on produced output.** A recipe (or any one conversion row, winning
  per leaf over the recipe default; native-derived rows inherit the recipe default like any other
  row) can author `Doneness: { ReadyMs, Overdone }`: a batch a step produces into a custody pile
  then sits READY to collect for `ReadyMs` of world GAME time and, if nobody gathers it, collapses
  once into the `Overdone` items - stew left on the fire too long becomes charcoal. The clock is
  game time, never wall clock, so a server outage cooks and burns nothing; the window settles
  lazily at whatever next touches the stash (a press at the station, a press-F retrieval, the
  working session's own heartbeat) with expiry boundary-exact at `elapsed >= ReadyMs`. Every new
  batch re-stamps the clock ("stirring the pot"), and at expiry the WHOLE windowed pile collapses
  together - the `Overdone` quantities scale by the batches produced into the window, the pile
  keeps its owner, and no other socket's pile is touched. A session stop leaves an open window's
  pile standing with its clock running (the batch is world state now - it is neither refunded nor
  duplicated); gathering before expiry simply ends the window. Two new `Custody.States` leaves
  (`Ready`, `Overdone` - the state set stays closed by the engine, packs re-point names only) and
  two new moment ids (`Ready`, `Overdone`, flair-overlayable like every cue) carry the look and
  sound, `ReadyMs` alone is a legal purely-presentational window, and two validator findings catch
  an `Overdone` that can never settle and a window with no custody produce to sit on.
- **The sneak+F picker also opens for multi-recipe stations.** A station whose action carries two
  or more hand-authored recipe rows shows one card per row (listed first, labeled by that row's own
  output item with its input-to-output cost line) beside the usual derived-category cards; a
  station deriving 33 species still shows three category cards, never 33 rows. Picking a row
  commits the next session to exactly that recipe, per session as before, and each card's preview
  reads the custody pile its own recipe actually draws from.
- **Multiblock structures: build a station out of world blocks.** A new `StructurePatternAsset`
  (`Server/RpgStations/Patterns/*.json`) describes an arrangement of blocks - exact ids, whole
  resource families ("any rock"), tag matches, or required-empty cells - with one ANCHOR cell.
  When a player finishes building the shape (any build order, any of the four compass orientations
  by default, optionally mirrored), the anchor block turns into the pattern's station block,
  keeping the rotation it was placed with, and is an ordinary station from then on; a pattern
  whose activation block equals its own anchor block arms a custom core block with no swap at all.
  An optional `Requires` gate (permission and/or factors) checks the builder before activating,
  authored `Activated`/`Broken` moments play at the anchor, and a spot already claimed by a
  different structure refuses politely. Breaking any block of the standing shape reverts the
  anchor to its original block, stops whoever was working there (their placed materials hand
  back), and drops anything else stored at the block - fire and explosions included. A standing
  ACTIVATED structure survives restarts (its mark lives on the block's own chunk). Caveat: the
  half-built-shape memory is in-memory only, so a partial build does not auto-complete across a
  restart - re-place any exact-id block of the pattern (the anchor is the natural one) and
  detection picks the build up again. The type is deliberately not extension-targetable: a cell
  appended by another pack would invalidate every standing build of the original shape. The
  authored Cooking Pit pattern that exercises this end to end is held under `unreleased/` (see
  the release-scope note below); the pattern engine itself ships, fully authorable by any pack.
- **The Cooking Pit exemplar: authored and HELD under `unreleased/`, not in the shipped jar.**
  A complete buildable cooking station exists in this repo - a stone ring around an unlit
  campfire becomes a working pit (Grill on the bare pit through the campfire's own native
  recipes; craft the iron Cooking Pot, mount it in the open cell, and the same press becomes
  Stew, deciding kebab / caesar salad / Hearty Stew off the ruled recipe rows), exercising the
  multiblock, socket, doneness, unattended and sharing machinery above end to end. It is held
  back from 1.0.0 with the rest of the held content set (see the release-scope note below); the
  engine features it exercises all ship, and `HeldCookingPitPatternTest` keeps the held files
  build-verified so a later restore ships pre-verified.
- **A gate factor for pot-shaped stations: `rpgstations:socket_filled`.** A station action's
  `Requires.Conditions` can ask whether a named custody socket is satisfied at this block - an
  Item socket's pile holds something, a Block socket's world block stands and matches - with the
  socket id as the condition's `Param`. The held-back cooking-pit exemplar's two actions gate on
  the same reading in opposite directions (`Min: 1` on Stew, `Max: 0` on Grill), which is the
  whole one-block-two-jobs layering; the reading spans every action's sockets, fails closed on a
  socket the evaluation cannot see, and reaches any registered factor provider through the api
  `FactorContext.socketFilled(String)`.
- **Action selection respects each action's own `Requires` gate.** When several actions match what
  you are holding, the press picks the first whose gate actually passes, falling back to the first
  match (whose gate then explains the denial) when none does - so a station can layer a gated
  action over an open one and the right one answers. A single-action station selects and denies
  exactly as before.
- **An explosion or fire destroying a station block drops its placed materials.** Environmental
  breaks (fire, physics, unattributed explosions) run the same drop-once cleanup a player break
  does, so a destroyed block never strands stored materials.
- **Settings: `Limits.MaxStashesPerSection` caps placed-input stores per chunk section** (a
  32x32x32 cube, the scope a per-chunk store can enforce). Topping up material already placed is
  never denied; only a placement that would open a new store past the ceiling is, and a
  multiblock structure's own activation mark never counts against it. The
  `Limits.MaxCustodyClaimsPerWorld` spelling is ignored with a boot warning naming the
  replacement.
- **Settings: `Limits.MaxUnattendedGatherCycles` caps what one unattended gather pays out.** The effective ceiling is the smaller of it and each action's own `Work.Unattended.MaxCycles`, so a server owner tightens every gather at once without raising any action's ceiling; absent means each action's own knob alone applies.
- **Two new api events and a structure view for consumer mods (apiVersion 6, artifact 0.6.0).**
  `StationOutputProducedEvent` fires once per committed produce phase of an attended session -
  whether the batch landed in placed custody (the receiving socket named) or in the worker's
  inventory - carrying fresh immutable copies of the committed stacks; an unattended settle
  deliberately fires nothing, since that output pays out at gather on the existing
  `StationUnattendedGatheredEvent`. `StationStructureChangedEvent` fires when a multiblock
  pattern activates at its anchor or a standing build reverts, naming the pattern, the block now
  standing, and the acting player (absent on an environment break). `RpgStationsApi.patterns()`
  (default-bodied, per the additive growth policy) hands a consumer a read-only projection of
  every folded structure pattern - anchor-relative cells with per-cell matcher summaries,
  activation/revert blocks, rotation flags - so a mod can lint its own content against the shapes
  this engine recognizes without live world handles.
- **The cycle event reports where committed produce landed (apiVersion 7, artifact 0.7.0).** `StationCycleCompletedEvent.socketCounts()` carries per-socket-id counts of the items a cycle's committed produce landed in placed custody - a socket-less custody's one implicit pile reports under `main`, and a cycle whose produce went to the worker's inventory reports an empty map, never null.
- **The Asset Editor shows each knob's real default beside its dropdown.** The station schemas already offer pick lists on the closed discriminators (`Look.Source`, `Hide.Route`, `Prop.Source`/`Slot`, `Mount.Surface`, `Consume.From`/`Produce.To`, `OnConditionFail.Result`); they now also declare each one's unauthored default (PlayerClone, Scale, MirrorHeld, Hotbar, Block, Inventory, Fail), plus the engine master `Enabled` and a work action's `Looping` (both true) and the summary HUD's `Enabled`, so an unauthored field renders its effective value instead of the control's zero-state. `FromCrafting.Types` exports its closed Crafting/Processing pair as a per-entry dropdown. Decode is unchanged.
- **The content audit covers the socket, structure and cooking surface, warn-or-info throughout
  (content always loads).** Sockets: an Item socket rendering no display prop (advisory - placement still works,
  players just see nothing), pile-shaped leaves on a Block socket (which stores nothing), a socket
  matcher no loaded item can ever satisfy, a recipe row or step phase addressing a socket its
  action never declares, and a socket address on an inventory-routed phase (where it is ignored).
  Structures: an activation block that is unknown or resolves no station (an inert build), an
  unresolvable revert block, an oversized cell list, duplicate cell offsets, and a gated pattern
  with no display name for its refusal toast. Cooking and enhancement: a station deriving recipes
  from a native bench that wants fuel (the fuel requirement never derives; the held-back pit's
  own file comment says the same), and a Stamp ritual beside sockets that never fill the pile Stamp reads
  from. A third-party validation hook can lint structure patterns too, through the api's
  read-only pattern view, live during the full pass.
- **The audit flags a socket cap above the custody-level cap** (`SOCKET_MAX_EXCEEDS_CUSTODY_MAX`, warning): the effective capacity is the smaller of the two, so `/rpgstations validate` surfaces the same authoring the decode-time warning reports at load.
- **Editor sections for the new schemas, and a station-block pick list.** The Asset Editor puts
  the new groups under their own sections (a structure pattern's
  Identity/Structure/Requirements/Moments, custody's Sharing and Sockets, a recipe row's Doneness,
  work's Unattended), and a pattern's activation block offers a live dropdown of every block the
  discovery index maps to a station.
- **A refused structure completion toasts once, not on every placed block.** A refusal (a
  conflicting anchor, a failed build gate) leaves the half-built shape re-checkable on purpose, so
  each nearby placement re-runs the walk; the refusal toast is throttled per player per anchor
  with a short cooldown, while the refusal itself never is.

A standalone, richly self-sufficient diegetic interactive work-station engine: with RPG Stations
alone installed, a station runs its full work loop (camera/hold/mount, tool gating, recipe
conversion, conditional-lootable rolls, command rewards) and needs no other mod. An add-on reaches
in through a soft extension surface (native events + typed registries, `api/`): it registers
factors a station's formulas read, and receives the contributions a station posts on each completed
cycle. Neither side hard-depends on the other. See `CLAUDE.md` for the full package-by-package
reference.

**Release scope: the engine is complete; the shipped default content is the Sawmill alone.** The
engine entries above all ship in full. What 1.0.0 deliberately does NOT ship is the finished
default content held back for a later release: the buildable Cooking Pit family (the pit's
structure pattern, its two-action station, the Cooking Pot vessel and the Hearty Stew), the
two-station fish-prep exemplar
(`CuttingBoard` plus `CookingFire`, the multi-station claimed-anchor walk) and the `MountSpike`
standing-mount experiment, whose Entity-surface mount is still in-game unverified. They live, complete and
restorable in one command, under `unreleased/` (see `unreleased/README.md`); their lang keys stay
shipped in all 9 locales, and the cooking pit keeps a build-run parity gate
(`HeldCookingPitPatternTest`) over its held files. The throwaway `/rpgstations npcspike` dev
harness is unwired for the same
reason, with `NpcPerformerSpike.java` kept in git. Capabilities the held stations demonstrate
(multiblock structure patterns, custody sockets, doneness windows, unattended processing,
multi-station walks, the Entity mount surface, step programs) are engine features and remain fully
authorable by any pack.

**The `api` extension surface is NOT frozen at 1.0.0.** The contract may still change in a later
release, additively where it can and otherwise where it must. Integrators should expect to
recompile against a later release rather than treat these types as stable; the artifact's own
version says which contract a build carries.

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
- Adds the `api` extension-surface artifact (not frozen at 1.0.0; it may still change in a later release): native Hytale events for
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
  station that wants its worker on their feet, with a dismount-on-move knob).
- Adds the open flair/moment vocabulary: a moment is an open string id (the well-known
  Cycle/Swing/Impact/Rare_Find/Completion constants plus a per-step `Step:<ActionId>:<StepId>` id
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
  all from one `F` press on the primary block) is complete but HELD BACK from 1.0.0 under
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
  `Swing`/`Impact`/`Step:<ActionId>:<StepId>` are authorable beside them. Native
  `Parent` merges the map per KEY and per leaf under it, so a child re-skinning one moment inherits
  every other. **Specificity wins**: an entry is the base for its moment id wherever the engine has
  nothing more specific, and a step's own `Presentation` (or a loot floor's cue) outranks it for
  that emission. An unrecognized key is the same warn-only typo finding a flair map gets.
  `Rare_Find` is the one well-known id an action does NOT author, since that moment only ever fires
  with the earning `Roll`/`Ladder.Floor` cue already in hand: author it there, and the new warn-only
  `RARE_FIND_MOMENT_NEVER_PLAYS` finding catches a map entry that could never play. An action's
  `Moments` entry drives a STEP's cue only for a `Step:<ActionId>:<StepId>` id, so an unnamed step
  never replays the action-wide `Cycle` cue per beat. The implicit convert loop's per-cycle cue plays
  under `Cycle` itself, so a flair re-skins the classic work loop by the id the docs name for it.
- **A swing's cues moved out of `Worker.Animation` and into `Moments`.** `Animation.Swing` is now
  pure cadence (`IntervalMs`); the swing cue is the `Swing` moment and the strike landing behind it
  is the `Impact` moment, late purely because it authors the generic `Presentation.DelayMs`. That
  removes the engine's one piece of dedicated single-cue scheduling machinery: every offset in the
  mod now rides one queue and one due-time core. `Impact` stays a distinct flair-targetable moment
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

### Pre-release schema sweep (the last authoring-surface pass before 1.0.0)

The whole authoring surface was reviewed once more while a rename or removal was still free (the
`api` freezes at 1.0.0, content schema had no back-compat obligation yet, and an unrecognized key only
ever produces a boot-log `WARNING: Unused key(s)` line). Everything below is part of the 1.0.0 schema
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

The one default station 1.0.0 ships, authored entirely in ordinary content assets a server owner can
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
  before release in favor of this in-repo surface, so the shipped 1.0.0 docs are `README.md`, the
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
    Well-known moment ids, a per-step `Step:<ActionId>:<StepId>` and the OPEN author-defined
    `Cue:<Your_Name>` namespace all resolve. The smart-cue rule is unchanged: a cue beside grants
    rides only once those grants genuinely produced something. The shipped Sawmill publishes a
    four-cue palette (`Rare_Find`, `Cue:Find_Deep`, `Cue:Find_Apex`, `Cue:Trophy`) a table can name
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

### Flair unlocks work standalone (2026-08-27)

- **The engine seeds its own flair-unlock read.** Setup registers a built-in `FlairUnlockProvider`
  (`station.ZigFlairUnlockProvider`) that reads ziggfreed-common's persisted per-player
  unlocked-flair component (`ZigFlairComponent`), so a station's flair overlays resolve with this
  jar and the library alone - previously the union was empty until some other mod registered a
  provider, and an unlocked flair could never show on a server running RPG Stations without one.
  The registry contract is unchanged: providers still union, persistence stays outside this engine,
  and a mod keeping unlocks in a genuinely foreign store registers its provider exactly as before
  (a second provider answering the same ids merges harmlessly).
