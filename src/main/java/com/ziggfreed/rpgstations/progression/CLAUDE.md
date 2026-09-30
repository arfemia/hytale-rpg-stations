# progression/

- This mod owns the `WORK_STATION`, `STATION_OUTPUT` and `STATION_INPUT` objective kinds: it fires them itself through zc's `ProgressDispatch.fire`, and the three kind files under `Server/ZiggfreedCommon/ObjectiveKinds/RpgStations/` are the whole registration; no Java touches the kind registry.
- The producers listen to this mod's own api events (`StationCycleCompletedEvent`, `StationOutputProducedEvent`) on purpose, so content advances on exactly what a third-party listener sees.
- `WORK_STATION` counts real (non-idle) cycles with target = station id and no qualifier; `STATION_OUTPUT` fires once per stack with target = item id and qualifier = station id.
- `STATION_INPUT` fires once per stack off `StationInputConsumedEvent`, target = item id, qualifier = station id, amount = quantity, credited to the event's worker. An unattended settle names no worker and credits nobody (`creditsSomeone`).
- A step counting a station and its greater tier authors the shared stem with the library's `QualifierMatchMode: "PREFIX"`; a qualifier is otherwise compared whole.
- Unattended settles and gathers fire no OUTPUT moment; their output surfaces once, on `StationUnattendedGatheredEvent`.
- The positive dispatch path runs only on a live server; unit tests record through the package-private `Dispatch` seam.
