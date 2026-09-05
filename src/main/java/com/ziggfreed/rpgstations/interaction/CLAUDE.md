# interaction/ - the station block + custody-display interaction handlers

Router for `interaction/`. Two registered types: one backs every station block in every installed
pack, the other backs every placed-input display entity's own press-F retrieval.

- **[`StationUseInteraction`](StationUseInteraction.java)** - `extends SimpleInstantInteraction`,
  registered type name **`rpg_station_use`** (namespaced to this mod, so a station block's chain
  can never collide with another mod's interaction type). A
  station block's `RootInteraction` JSON references it in the OBJECT form,
  `{ "Type": "rpg_station_use", "Station": "<id>" }`, so ONE Java interaction type backs any
  number of station blocks with zero extra Java per station (the first-party object-form-param
  pattern). Pressing F calls `station.StationService#toggle`: starts a
  session (every denial a localized toast) or stops the player's running one. The press also
  carries a SNEAK flag read at fire time (`#readSneaking`, the player's own
  `MovementStatesComponent.crouching`, false on any read failure) as `toggle`'s last argument:
  with no session running, a sneak+F press routes to the recipe picker
  (`StationService#routeSneakSelection` -> `pages.RpgStationPickerPage`) instead of engaging work,
  falling through to the plain engage when fewer than two picker rows resolve; over a RUNNING
  session any press, sneak included, stops it. Every exit path sets
  `ctx.getState().state`; a user-initiated denial is `Finished`, never `Failed`.
- **[`StationRetrieveInteraction`](StationRetrieveInteraction.java)** (new feature, 2026-07-22 fix
  round) - `extends SimpleInstantInteraction`, registered type name **`rpg_station_retrieve`**.
  Unlike `StationUseInteraction`, NOT referenced from any block JSON - `station
  .StationCustodyDisplay#addRetrieveInteraction` sets it PROGRAMMATICALLY on every placed-input
  display entity's own `Interactions` component (`InteractionType.Use` -> the jar-shipped generic
  `RPG_Station_Retrieve` RootInteraction asset, `Server/Item/RootInteractions/
  RPG_Station_Retrieve.json`, no per-station param). Pressing F on the display entity reads
  `ctx.getTargetEntity()` (populated by `InteractionManager` off the incoming packet's `entityId`
  before this class's `firstRun` even runs, and surviving into it because `UseEntityInteraction`
  pushes the registered RootInteraction onto the SAME context - see `StationRetrieveInteraction`'s
  own class javadoc for the exact shared-source chain) and calls
  `station.StationService#retrieveCustody`: it resolves the clicked entity back to its owning
  (blockKey, socket) pair by `NetworkId`, scoped to the presser's own world, then asks
  `StationCustodyRetrieval#decide` - the clicked SOCKET's own pile owner (relaxed when that socket
  authors `Share.Reclaim`), and a no-op keyed toast while a session is actively working that
  station. On success it hands THAT socket's pile back, despawns that socket's display, removes
  the pile (and the stash once its last pile is gone), and flips the block back to its Empty
  custody state when nothing is left in it. See `station/CLAUDE.md`'s retrieval bullet for the
  engine-side detail (`StationCustodyRetrieval`'s pure eligibility decision).

Both registered once each in `RpgStationsPlugin` (`#registerStationInteraction` /
`#registerStationRetrieveInteraction`) via `getCodecRegistry(Interaction.CODEC).register(...)`.
