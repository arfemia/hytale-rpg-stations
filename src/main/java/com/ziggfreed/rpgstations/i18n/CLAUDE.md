# i18n/ - the rpgstations. lang namespace

Router for `i18n/`.

- **[`RpgMsg`](RpgMsg.java)** - a prefix-free facade over `ziggfreed-common`'s mod-agnostic
  `i18n.Msg` (that class carries no fixed namespace so several consumer mods share it; a
  consumer wanting a prefix-free call site wraps it, which is what this class is).
  `RpgMsg.tr(key, args...)` resolves `"rpgstations." + key` against
  `Server/Languages/<bcp47>/rpgstations.lang`.
- **[`RpgStationsLangKeys`](RpgStationsLangKeys.java)** - a hand-maintained `Set<String>` of every
  message id this mod authors in `rpgstations.lang`, backing `station.StationValidator`'s
  lang-key-known check (critique m10's binding fix: keep a cheap lang-key-presence check rather
  than silently dropping it, since nothing generates the set from the file). **Keep this set in
  lockstep with `rpgstations.lang` by hand** whenever a key is added or removed - there is no
  build-time check for drift, only the validator noticing a shipped key it doesn't recognize
  (harmless) or a stale entry for a retired key (also harmless). The MMO Skill Tree solves the same
  problem by PARSING its own shipped `.lang` back off the classpath (`i18n/LangResources`) instead
  of maintaining a list; worth lifting here if this set ever grows enough to drift in practice.
- **The `.lang` files are AUTHORED directly** - nothing generates them, which is also how the MMO
  Skill Tree works (it retired its Java-baked English map in its own 1.6.0 cycle), so there is one
  localization story across the family. `i18n.LangFileIntegrityTest` (`src/test/`, leg 7A) guards
  every locale directory that exists against placeholder mismatches / em-dashes / duplicate keys.
  A net-new domain gets its OWN `rpgstations.<domain>.lang`, entries dropping the segment the
  filename carries (the engine prefixes by basename), matching `ziggfreedcommon.*.lang`.
- **Native-namespace files stay separate**: `items.lang` (block name/description/interaction hint)
  and `avatarCustomization.lang` (the work emote's display name) are Hytale's OWN lang namespaces,
  not `rpgstations.` - they are NOT covered by `RpgStationsLangKeys`, only by the integrity test's
  placeholder/em-dash/duplicate checks.
