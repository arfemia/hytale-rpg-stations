# progression/ - the two objective kinds this engine fires

Router for `com.ziggfreed.rpgstations.progression`: RPG Stations OWNS the station objective
kinds. It fires `WORK_STATION` and `STATION_OUTPUT` into ziggfreed-common's shared progression
runtime itself (`ProgressDispatch.fire`, the library's documented entry for a net-new moment,
exactly the way its own producers fire a block broken or an item crafted), and ships the two kind
files that describe them (`src/main/resources/Server/ZiggfreedCommon/ObjectiveKinds/RpgStations/
{Work_Station,Station_Output}.json`, folded into the library's kind registry when assets load - a
file naming an id nothing registered IS the registration, so no Java touches the registry) plus
the four `objective.text.*` templates in `rpgstations.lang` (all 9 locales) so a step reads on a
server with nothing else installed. A quest or achievement authored against either kind advances
from real station play with only ziggfreed-common and this jar present.

**This package carries no progression vocabulary beyond the two kind ids** (`MmoAgnosticismTest`
scans it): quest / objective / progress are the library's words and pass.

## Files

- **`StationProgressProducers`** - the two listeners, registered from `RpgStationsPlugin.setup()`
  (`registerProgressProducers`) on the plugin's own `EventRegistry`, subscribed to this engine's
  OWN api events on purpose: the producers see exactly what a third-party consumer sees, so the
  moment content advances on and the moment a consumer counts can never disagree, and every rule
  the events state (an unattended settle fires nothing, a command payout is invisible, a gathered
  batch reports once on `StationUnattendedGatheredEvent`) holds here for free.
  - `WORK_STATION`: off `StationCycleCompletedEvent`, REAL cycles only (`countsAsWork`), target =
    the station id, qualifier null, amount 1, the cycle event's own command buffer threaded through.
  - `STATION_OUTPUT`: off `StationOutputProducedEvent`, one moment PER STACK the event carries
    (`countable`: a named item with a positive quantity), target = the item id, qualifier = the
    station id (content scopes to one station by naming it, or leaves the qualifier off and counts
    every station - a missing qualifier matches every event), amount = the stack's quantity, no
    command buffer (the output event carries none; `ProgressDispatch.fire` tolerates the null).
  - The `Dispatch` seam (package-private, the shape of `ProgressDispatch.fire`'s producer form) is
    what `StationProgressProducersTest` records through; production hands the shared dispatch in.
  - Guarded whole: a throwing producer costs its own moment, never the engine's cycle.
- **`StationWorkPayload`** / **`StationOutputPayload`** - the typed `MomentPayload` records
  (the library's own `CraftPayload` shape: the event the moment came off, plus for output the ONE
  stack this moment counts), so a listener keying per action / cycle index / socket / landing
  block reads those off the event instead of re-deriving them.

## What is and is not counted (the contract, restated from the output event)

- A produce phase's own yield, a grant pass's `Grants.OutputItems` bonus units (the LANDED count),
  every `Grants.Items` stack and every `Grants.DropLists` find: counted, once each, on the pass
  that paid them (`StationService.applyGrantResult` reports the batch through the same
  `fireOutputProduced` funnel the produce phase uses, socket null, after the cues and toasts).
- A `Grants.Commands` payout: NEVER counted - the engine cannot know what a console command gave.
  `SawmillTrophy.json` grants its hatchet through `Grants.Items` for exactly this reason.
- An unattended settle and the gather that pays out its accrued rolls: never counted here
  (`applyGatherGrantResult` fires no output event); that output surfaces once on
  `StationUnattendedGatheredEvent`.
- An idle-practice cycle: not work.

## Tests

`StationProgressProducersTest` pins the decisions (`countsAsWork`, `countable`), the idle skip,
the empty-batch skip and the never-throws guard over null live handles through the recording
seam. The positive dispatch (a live `PlayerRef` resolving to a `Ref`) is a live-server boundary,
verified by the dev-server smoke rather than a unit JVM. `ShippedAssetDecodeTest` decodes both
kind files through zc's `ObjectiveKindAsset.CODEC`.
