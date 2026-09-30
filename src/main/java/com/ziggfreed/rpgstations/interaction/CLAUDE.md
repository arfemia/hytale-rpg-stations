# interaction/

- `rpg_station_use` backs every station block: the block's RootInteraction names it in object form, `{"Type": "rpg_station_use", "Station": "<id>"}`, so a new station needs no Java.
- Every exit path sets `ctx.getState().state`; a user-initiated denial is `Finished`, never `Failed`.
- `rpg_station_retrieve` is never referenced from block JSON: `StationCustodyDisplay#addRetrieveInteraction` attaches the `RPG_Station_Retrieve` RootInteraction to each display entity in code.
- Sneak+F with no session running opens the recipe picker only when 2+ rows resolve, else it engages; any press over a running session, sneak included, stops it.
