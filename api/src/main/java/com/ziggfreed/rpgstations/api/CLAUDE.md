# api/ - the extension surface

Router for the `api` artifact. The types' javadoc is the contract; this file holds the policy the javadoc cites by heading.

## Additive growth policy

- The api grows additively only: a new default-bodied interface method, a new event class under `api.event`, or a new additive getter. Never a changed, renamed or removed member or signature.
- Bump `RpgStationsApiImpl.API_VERSION` by one per addition batch, never on its own. `apiVersion()` is the runtime contract number; the api artifact (`api_version` in `gradle.properties`) carries the mod's release version.
- The api is not frozen; declaring it frozen is the maintainer's decision, never implied by a version number.

## Contribution channels

- The engine owns built-in factors and zero built-in channels: `ContributionChannelRegistry` is declaration-only and fail-open, and the engine never resolves a channel.
- `StationContribution` and `ContributionChannelRegistry` stay api-local until a second consumer needs the same write-side relay; then lift both to `ziggfreed-common` as a copy-move and re-export them here.

## Two-step consumer idiom

- A consumer checks presence first and only then reaches an api class from a separate holder class, so a missing mod never triggers class loading. The copyable pattern is in `docs/integrations.md`.
