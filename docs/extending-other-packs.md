# Extending Other Packs

ExtensionAsset: add to another pack's content additively, without owning or replacing its files.

A fourth-party pack often wants to ADD something to a station or action a different, third-party pack
ships - a contribution on a new channel, an extra loot table, a bonus ritual step - without owning that
station's file (which would mean re-shipping the whole thing and risking drift) or replacing it
wholesale (which would silently discard whatever the original author changes later). **ExtensionAsset**
is the one mechanism for exactly this: additive-only composition onto content you did not author.

## The shape

An extension lives at `Server/RpgStations/Extensions/<Name>.json` (Pattern A, id = lowercased
filename). It names EXACTLY ONE target, and carries only the payload groups that target type accepts:

```json
{
  "Target": { "Station": "Sawmill", "Action": "Mill" },
  "Priority": 0,
  "Bonus": { "Lootables": ["SawmillLuckTiers"] }
}
```

`Target` names ONE target through orthogonal leaves, never a discriminator-plus-id pair. Since a
station's own groups live entirely on its actions, only `Actions` (new entries) targets a station
directly - every mechanical payload key targets an ACTION instead:

| Target | Payload keys it may carry |
|---|---|
| `{Station: "<id>"}` | `Actions` (NEW entries appended to the station's ordered list; the base wins an Id collision, and an appended action is selected only after every base action) |
| `{Action: "<id>"}` | `PerCycleContributions`, `Bonus`, `ContributionScale`, `Conversions`, `Steps`, `Anchors` (new keys only), `Puppet`, `Custody` |
| `{Station: "<id>", Action: "<id>"}` | the same Action payload keys, applied only where THAT station resolves that action id |
| `{Lootable: "<id>"}` | `Rolls` (appended) |
| `{RollPool: "<id>"}` | `Entries` (appended) |

Authoring a payload key the target type does not accept is a content-audit finding, never a silent
no-op left undocumented.

An `Action` target names the id the engine resolves that action by: the `ActionAsset` id when a
station's entry `Ref`s one (so the extension reaches EVERY station that shares that action), else
the station's own inline `Id` for that entry.

### Bare or station-scoped?

An action id is not globally unique, so the two Action shapes answer two different questions:

- **`{Action: "PrepFish"}`** - "wherever this action runs". The reach IS the point when the target is
  a shared `ActionAsset` several stations `Ref`: one extension, every station that reuses the ritual.
- **`{Station: "Sawmill", Action: "Mill"}`** - "this station's copy of it". Use this when the action
  is a station's own inline entry, or when a shared action should be tuned on ONE station only. It is
  also the safe shape against id collisions: another pack shipping its own `Mill` action never picks
  up your payload.

Scoping changes nothing else - the same payload keys, the same merge rules, the same apply order.
The content audit resolves a scoped target as "that station exists AND resolves that action id", and
it reports a collision only between claims that genuinely meet: two extensions claiming the same key
on the same action id but on DIFFERENT stations are correctly silent, while a bare claim and a scoped
one on that key - both of which apply on the scoped station - are reported.

## Merge and conflict rules

1. **Additive only.** An extension never mutates, replaces, or removes anything the base already
   authored - replacing a whole file stays a load-order concern, not this mechanism's job.
2. **Keyed collections** - a Station target's `Actions` (an ORDERED array, but still keyed by `Id`) and
   an Action target's `Anchors` (a map) both let the BASE win an Id/key collision; a new key is folded
   in (an appended action is selected only AFTER every base action, since selection order follows
   authored order), and among several extensions adding new keys, apply order (below) decides.
3. **Unkeyed arrays** (`PerCycleContributions`, `Conversions`, `Rolls`, `Entries`) - pure append, in
   apply order.
4. **Nested per-leaf overlay** (`Puppet`, `Custody`, `ContributionScale`) - recursively, at every
   nesting depth, an AUTHORED extension leaf wins and an unauthored one leaves the base's own value
   intact; among several extensions the later (higher-priority) one overlays on top.

   **OVERLAY IS NOT EXTENSION.** An overlay RE-TUNES leaves of something the base already authored;
   it never grows the base's closed vocabulary. `Custody.Sockets` is the one keyed collection inside
   an overlayable group, and it composes with both faces at once: an overlay entry whose socket id
   the base already authors deep-merges per leaf (the re-tuning face - retint one socket's `Display`
   without touching its `Match` or capacity), while an id the base does not author is APPENDED as a
   new socket (the additive face; the base's authored order, which is placement priority, is never
   disturbed). Within one merged socket the base keeps every leaf the overlay does not explicitly
   author - the base wins everything unsaid. A socket's `Item`/`Block` route pair is the one
   leaf-walk exception: an overlay authoring a route group commits the socket to THAT route (its
   group merges per leaf, the base's other route drops), and an overlay authoring neither keeps the
   base's untouched. `Custody.States` stays the contrast case: it has no collection, so an overlay
   may re-skin its state names per leaf and can never add a state the engine does not know.
5. **Ordered step insertion** (`Steps`) - each insertion carries an `Anchor`, exactly one of
   `{After: "<stepId>"} | {Before: "<stepId>"} | {AtStart: true} | {AtEnd: true}`. A missing or
   dangling `After`/`Before` target degrades to `AtEnd` plus a content-audit note. Inserted steps
   need their own `Id`s so a LATER extension can anchor on one of THEM. Which program an insertion
   reaches is decided entirely by the extension's `Target` - the two Action shapes above are the one
   aiming mechanism, so an insertion names no action of its own and an extension whose insertions
   belong to two different actions is two extensions.

   An insertion ADDS beats to a program the target action **already authors**. An action with no
   `Steps` of its own runs the recipe-driven convert loop instead, and no insertion can turn it into
   a step-programmed one - that would silently bypass its conversion check. To extend that shape,
   append `Conversions` or a `Bonus` roll.

```json
{
  "Target": { "Action": "PrepFish" },
  "PerCycleContributions": [ { "Channel": "yourmod:craft_quality", "Param": "COOKING", "Amount": 8.0 } ]
}
```

This is the shape a fish-exemplar pack-side extension uses: it is the ONLY thing that makes the jar's
`PrepFish` action post anything at all - the jar action declares no contributions, and this one small
file is entirely responsible for wiring the two together.

### Apply order

When several extensions could touch the same target, they apply in one deterministic order: `Priority`
ascending (a HIGHER priority applies LATER, and wins a key-collision or same-leaf tie), then extension
id alphabetically. This is a total order over distinct assets (ids are unique), so a stable sort fully
determines the result on every server regardless of pack load order - two extensions anchored on the
SAME step insertion point apply in exactly this order too.

### Composition order with native Parent

Extensions apply to the `Parent`-resolved target AT READ TIME, and extension additions do **not** flow
down `Parent` chains themselves: a bare `{Target: {Action}}` extension reaches every station that
references that action via `Ref` (a station-scoped one stops at the station it names); a
`{Target: {Station}}` `Actions` append applies to that ONE station only, even if another station
inherits from it via `Parent`.

## Avoiding double-counted contributions

`PerCycleContributions` APPENDS, it never replaces. If the base already posts
`{Channel: "yourmod:craft_quality", Param: "OAK", Amount: 8.0}` and an extension adds an entry for the
SAME `(Channel, Param)` pair, the amounts SUM - the effective total doubles to 16.0 per cycle rather
than crediting something new. An extension should add a genuinely NEW pair; to retune an existing one
on content you do not own, that is a request to the base author, not something this mechanism can
safely do for you.

The content audit catches this for you: `EXTENSION_CONTRIBUTION_DUPLICATE` fires when an extension
appends a `(Channel, Param)` pair the base - or another extension on the same target - already
declares, and names the colliding extensions.

## Deliberately non-extensible

A few things are intentionally left OUT of this mechanism, because additive composition would be the
wrong tool for them:

- `Requires` - an extension must never tighten or loosen another author's access gate.
- `Settings` - the server-wide singleton is an owner-only concern.
- `Custody.States` - the visual empty/loaded/working trio itself; there is nothing meaningful to
  append to it (the surrounding `Custody` group's OTHER leaves, like `Display`, are still overlayable
  per the per-leaf rule above).
- Scalar groups (`Work`, `Worker.Hold`, `Worker.Camera`, `Worker.Animation`, `Recipe.FromCrafting`,
  `Recipe.Yield`) - overriding these is load-order's job (a full replacement or a `Parent` override),
  not an additive extension. `Recipe.Conversions` is the one exception, appended per rule 3 above.
- The internals of an existing `Roll` - an extender adds their OWN new Roll beside it instead of
  reaching inside someone else's.
- `FlairAsset.Moments` - see [Flairs](flairs.md); the flair union already composes without needing this
  mechanism.

---

Previous: [Flairs](flairs.md) · Next: [Settings](settings.md)
