# Localization

The lang families RPG Stations content resolves display text through.

Every piece of display text in RPG Stations content is resolved client-side through a localization key
- never a raw hardcoded string. Which `.lang` file a key lives in depends on WHOSE namespace the
content belongs to: RPG Stations' own generic UI/content strings, or a native Hytale asset's own
name/description.

## 1. rpgstations.lang - RPG Stations' own namespace

RPG Stations' generic UI strings and per-station/per-action content keys live in
`Server/Languages/<bcp47>/rpgstations.lang`. Java code resolves them through a prefix-free facade that
automatically prepends `rpgstations.`, so a content key authored as `station.sawmill.name` is
referenced from JSON as `rpgstations.station.sawmill.name`:

```
ui.station.locked = This station is not available right now.
ui.station.occupied = Someone is already using this station.
ui.station.no_action = You have nothing this station can work with.
station.sawmill.name = Sawmill
station.sawmill.desc = Saw logs from your inventory into planks, one cycle at a time.
action.prepfish.label = Prepare Fish
```

A pack that ships its own station reuses this same file convention: author your own additive
`rpgstations.lang` overlay carrying just your new keys (RPG Stations reads the jar's shipped file and
every pack's overlay together, additively) - never duplicate the jar's existing keys. This is the
family every `Identity.NameKey`/`DescKey`, `ActionDef.Label`, and every `ui.*` UI string resolves
through.

## 2. Native namespaces - a block's own item/emote text

A station's BLOCK is an ordinary native Hytale item, and its display text lives in Hytale's OWN native
lang namespaces, not RPG Stations' own:

- `items.lang` - the block's name, description, and interaction hint (including the separate
  empty-vs-loaded hint text for a custody-governed block).
- `avatarCustomization.lang` - a work emote's own display name, if the station's emote is
  server-authored rather than a native built-in.

```
RPG_Station_Sawmill.name = Sawmill
RPG_Station_Sawmill.description = A work station that saws logs into planks. Press use to start working.
RPG_Station_Sawmill.hint.empty = Press [{key}] to load logs
RPG_Station_Sawmill.hint.loaded = Press [{key}] to work
```

These keys belong to the BLOCK asset, not to RPG Stations' own vocabulary - they follow whatever
naming convention native item/emote assets already use, and they are not covered by RPG Stations' own
lang-key-presence audit (only `rpgstations.lang` keys are).

## Why two families, not one

Each family belongs to the system that owns the content it describes: RPG Stations owns its own
generic UI vocabulary and per-station convention keys; the native engine owns every item/emote asset's
text regardless of which mod placed that asset. Keeping them separate means a pack never has to guess
which file a given piece of text belongs in - the OWNER of the asset the text describes is always the
answer.

The same rule extends past these two. A pack that also ships assets belonging to some OTHER mod puts
their text in THAT mod's lang namespace, following that mod's own key convention - not in
`rpgstations.lang`, and not documented here.

---

Previous: [Settings](settings.md)
