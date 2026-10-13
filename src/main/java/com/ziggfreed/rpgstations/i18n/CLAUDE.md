# i18n/

- A new domain's keys go in their own `rpgstations.<domain>.lang`, not in `rpgstations.lang`. The engine prefixes each key with the file basename, so entries drop the segment the filename carries.
- Block names, descriptions and interaction hints go in `items.lang`, and the work emote's name in `avatarCustomization.lang` (Hytale's own namespaces), never in `rpgstations.lang`.
- The validator's lang-key check asks the loaded catalogue through zc's `TextKeyAudit.liveCatalogue()`; there is no hand-kept key set to update.
