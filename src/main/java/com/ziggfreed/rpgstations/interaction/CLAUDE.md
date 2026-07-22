# interaction/ - the station block + custody-display interaction handlers

Router for `interaction/`. Two registered types: one backs every station block in every installed
pack, the other backs every placed-input display entity's own press-F retrieval.

- **[`StationUseInteraction`](StationUseInteraction.java)** - `extends SimpleInstantInteraction`,
  registered type name **`rpg_station_use`** (the MMO's pre-extraction copy was `mmo_station_use`;
  the two never coexisted post-leg-5, but the rename is why an old dev-world block placed under the
  MMO's engine no longer resolves - see the mod-root `CLAUDE.md`'s shared-block-id story). A
  station block's `RootInteraction` JSON references it in the OBJECT form,
  `{ "Type": "rpg_station_use", "Station": "<id>" }`, so ONE Java interaction type backs any
  number of station blocks with zero extra Java per station (mirrors the MMO's bounty-board /
  token-shop object-form-param pattern). Pressing F calls `station.StationService#toggle`: starts a
  session (every denial a localized toast) or stops the player's running one. Every exit path sets
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
  `station.StationService#retrieveCustody`: owner-only, a no-op
  keyed toast while a session is actively working that station, otherwise hands the placed
  contents back and despawns the display. See `station/CLAUDE.md`'s retrieval bullet for the
  engine-side detail (`StationCustodyRetrieval`'s pure eligibility decision).

Both registered once each in `RpgStationsPlugin` (`#registerStationInteraction` /
`#registerStationRetrieveInteraction`) via `getCodecRegistry(Interaction.CODEC).register(...)`.
