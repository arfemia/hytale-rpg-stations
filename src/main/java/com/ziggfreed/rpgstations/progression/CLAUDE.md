# progression/

- This mod owns the `WORK_STATION` and `STATION_OUTPUT` objective kinds: it fires them itself through zc's `ProgressDispatch.fire`, and the two kind files under `Server/ZiggfreedCommon/ObjectiveKinds/RpgStations/` are the whole registration; no Java touches the kind registry.
- The producers listen to this mod's own api events (`StationCycleCompletedEvent`, `StationOutputProducedEvent`) on purpose, so content advances on exactly what a third-party listener sees.
- `WORK_STATION` counts real (non-idle) cycles with target = station id and no qualifier; `STATION_OUTPUT` fires once per stack with target = item id and qualifier = station id.
- Unattended settles and gathers fire no progression moment; their output surfaces once, on `StationUnattendedGatheredEvent`.
- The positive dispatch path runs only on a live server; unit tests record through the package-private `Dispatch` seam.
