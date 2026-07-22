# interaction/ - the station block interaction handler

Router for `interaction/`. One registered type backs every station block in every installed pack.

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

Registered once in `RpgStationsPlugin#registerStationInteraction` via
`getCodecRegistry(Interaction.CODEC).register(TYPE_NAME, StationUseInteraction.class,
StationUseInteraction.CODEC)`.
