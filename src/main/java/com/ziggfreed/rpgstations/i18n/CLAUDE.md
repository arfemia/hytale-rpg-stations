# i18n/ - the rpgstations. lang namespace

Router for `i18n/`.

- **[`RpgMsg`](RpgMsg.java)** - a prefix-free facade over `ziggfreed-common`'s mod-agnostic
  `i18n.Msg` (that class carries no fixed namespace so several consumer mods share it; a
  consumer wanting a prefix-free call site wraps it, mirroring how the MMO's own `i18n.Msg`
  reads). `RpgMsg.tr(key, args...)` resolves `"rpgstations." + key` against
  `Server/Languages/<bcp47>/rpgstations.lang`.
- **[`RpgStationsLangKeys`](RpgStationsLangKeys.java)** - a hand-maintained `Set<String>` of every
  message id this mod authors in `rpgstations.lang`, backing `station.StationValidator`'s
  lang-key-known check (critique m10's binding fix: keep a cheap lang-key-presence check rather
  than silently dropping it, since RpgStations has no `EnglishDefaults.java` generator to derive
  the set from). **Keep this set in lockstep with `rpgstations.lang` by hand** whenever a key is
  added or removed - there is no build-time generator to catch drift, only the validator noticing
  a shipped key it doesn't recognize (harmless) or a stale entry for a retired key (also harmless).
- **No `EnglishDefaults.java` generator in 1.0.0** - the `.lang` files are authored directly (a
  small key count). `i18n.LangFileIntegrityTest` (`src/test/`, leg 7A) guards every locale
  directory that exists against placeholder mismatches / em-dashes / duplicate keys.
- **Native-namespace files stay separate**: `items.lang` (block name/description/interaction hint)
  and `avatarCustomization.lang` (the work emote's display name) are Hytale's OWN lang namespaces,
  not `rpgstations.` - they are NOT covered by `RpgStationsLangKeys`, only by the integrity test's
  placeholder/em-dash/duplicate checks.
