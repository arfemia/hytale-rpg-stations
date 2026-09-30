# api/impl/

This package is in the main module; the interfaces it implements live in the separate `api` Gradle module (router: `api/src/main/java/com/ziggfreed/rpgstations/api/CLAUDE.md` from the mod root).

- Engine code reads the `*Impl` singletons directly (`FactorRegistryImpl.resolve`/`isKnown`/`registeredIds`/`snapshotFor`, `ContributionChannelRegistryImpl.isDeclared`, `SummaryEnricherRegistryImpl.enrichers`, `ValidationHookRegistryImpl.hooks`). The public api interfaces stay register-only, so a new engine read goes on the Impl, never on the interface.
- A factor's namespace names who owns the concept, not who registers it: `rpgstations:` only for a session or station-only concept, `hytale:` for a native read that means the same with no station involved.
- A factor that cannot answer resolves to null (fail closed), never a substituted 0. The api `StationFactorProvider` returns a primitive `double`, so a built-in that can fail registers through the nullable core seam (`core.register` with `CoreFactorVocabulary.wrap`), as `tool_power` and `socket_filled` do.
- Build one `snapshotFor(ctx)` per moment and discard it: the context holds live world-thread handles.
- Only `CoreFactorVocabulary` imports zc's `FactorContext` and `FactorRegistry`; their simple names collide with this mod's api types, so everywhere else those names mean the api's.
- The item family (`hytale:item_quality`, `item_level`, `item_durability_percent`, `item_stat`) reads the piece through `FactorContext.item()` and registers beside `stat` through `registerPortable`; `ziggfreedcommon:item_stamp_points` needs no line here because the shared registry falls through to the library's process-wide contribution.
