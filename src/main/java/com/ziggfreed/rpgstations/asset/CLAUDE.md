# asset/ - codecs and stores

The codec `.documentation` strings, each class's javadoc, the generated `SCHEMA.md` and `docs/` are the schema reference; this file holds what they do not say.

- The codec is the schema: every new leaf is nullable, `appendInherited`, and carries `.documentation` in the same edit (`AssetDocumentationCoverageTest` fails on a blank or process-narrating string).
- `SCHEMA.md` is generated (from the workspace root, `tools\lane\lane.ps1 build -Dir mmo-family\rpg-stations -Tasks 'generateSchemaDocs'`), never hand-edited; `SchemaDocDriftTest` fails on drift.
- After 1.0.0 the schema grows additively (plural arrays, nested groups, `StringOrObjectCodec` promotion). A rename or removal without an alias is the maintainer's decision.
- Field-local warn checks use `CodecWarnValidators`, never the engine's `Validators` factory: a failing engine validator makes the store drop the whole asset, and this mod's posture is that an asset always loads and findings are advisory.
- A new editor `Dropdown` needs its dataset id as a constant on `AssetEditorDataSets` with a handler; nothing cross-checks the two, and a missing handler yields a silently empty pick list.
- A leaf added to a nested group joins its extension overlay factory in the same change (`ExtensionCatalog`'s `overlayStates`, `overlayEffectRef`), or the overlay drops it. A new `ActionInput` route also joins every site's material reading (`heldQuality` beside `heldFunction` in `StationService`) and `ExtensionCatalog#overlayInput`.
- Lang keys stay lowercase (`rpgstations.station.<lowercased id>.name`) although asset ids are authored PascalCase. Never "fix" them.
- A fold replaces a lootable or station by id at whole-file granularity, so ship one roll per lootable file unless the rolls must be re-tuned together.
- Extend another pack's content with an `ExtensionAsset`, never a full-file override; what stays out of extensions on purpose is `docs/extending-other-packs.md`'s "Deliberately non-extensible", and only an `ExtensionAsset.Target` kind can be extended.
