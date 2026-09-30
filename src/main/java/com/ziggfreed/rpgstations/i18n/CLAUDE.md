# i18n/

- A new domain's keys go in their own `rpgstations.<domain>.lang`, not in `rpgstations.lang`. The engine prefixes each key with the file basename, so entries drop the segment the filename carries.
- Block names, descriptions and interaction hints go in `items.lang`, and the work emote's name in `avatarCustomization.lang` (Hytale's own namespaces), never in `rpgstations.lang`.
- `RpgStationsLangKeys` only speeds up the validator's lang-key check, which falls through to a live `I18nModule` lookup: a key missing from the set is harmless and nothing checks it.
