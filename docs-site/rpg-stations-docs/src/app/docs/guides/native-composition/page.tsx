import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Native Composition',
  description: 'The id-ref-only principle: RPG Stations references native Hytale content, never inlines it.',
  openGraph: {
    title: 'Native Composition | RPG Stations Docs',
    description: 'Every native reference leaf: interactions, effects, drop lists, recipes, tags, emotes, sounds, particles.',
  },
}

export default function NativeCompositionPage() {
  return (
    <DocPage
      title="Native Composition"
      description="RPG Stations references Hytale's own content by id - it never re-invents it"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Native Composition' }]}
      prevPage={{ title: 'Loot & Factors', href: '/docs/guides/loot-and-factors/' }}
      nextPage={{ title: 'Enhancement & Stamp', href: '/docs/guides/enhancement-and-stamp/' }}
    >
      <p>
        RPG Stations leans on the native Hytale asset catalog wherever it can, instead of re-implementing a
        parallel system: interactions, entity effects, drop tables, crafting recipes, item tags, emotes,
        sounds, and particles are all referenced <strong>by id only</strong>. A leaf that composes with native
        content NEVER inlines the native asset&apos;s own body - it holds a string id (and sometimes a small
        override like a duration), and the engine resolves that id against the live asset catalog at the
        moment it is needed.
      </p>
      <p>
        The payoff: any native content a server owner or another pack already ships (a custom
        <code>EntityEffect</code>, a modded weapon family, a new drop table) is immediately usable from RPG
        Stations content with zero new Java code - and an unresolvable id degrades gracefully (a warning at
        content audit time, a silent no-op at runtime) rather than crashing a session.
      </p>

      <H2 id="presentation-interaction">Presentation.Interaction - firing a native interaction chain</H2>
      <pre><code>{`"Presentation": { "Interaction": { "Id": "SomeRootInteractionId" } }`}</code></pre>
      <p>
        Any moment that carries a <code>Presentation</code> (a station&apos;s per-cycle moment, a step&apos;s
        entry cue, a flair overlay) can also fire a native <code>RootInteraction</code> chain by id - a
        one-leaf reference, never an inlined interaction body. An unresolvable id is a content-audit note (typo
        detection) and a no-op at fire time.
      </p>

      <H2 id="presentation-effect">Presentation.Effect and Roll.Grants.Effects - native EntityEffects</H2>
      <pre><code>{`"Presentation": { "Effect": { "Id": "SomeEntityEffect", "DurationMs": 3000 } }
"Grants": { "Effects": [ { "Id": "SomeEntityEffect" } ] }`}</code></pre>
      <p>
        The same <code>{'{Id, DurationMs?}'}</code> reference shape applies a native <code>EntityEffect</code>{' '}
        asset by id in two places: a single per-moment effect on a <code>Presentation</code>, or an array of
        reward-time effects on a Roll&apos;s <code>Grants</code>. <code>DurationMs</code> is an OPTIONAL
        override - omit it and the effect runs for whatever duration the referenced effect asset itself
        declares. Every session-scoped effect the engine applies is tracked so it is removed automatically when
        the session stops.
      </p>

      <H2 id="grants-droplist">Grants.DropList - native item drop tables</H2>
      <pre><code>{`"Grants": { "DropList": "MMO_Station_Sawmill_T2" }`}</code></pre>
      <p>
        A Roll&apos;s (or a Ladder floor&apos;s) <code>DropList</code> names a native <code>ItemDropList</code>{' '}
        asset id, rolled through the engine&apos;s own drop-list roller - the exact same mechanism a mob loot
        table or a chest uses. Author drop tables the ordinary Hytale way and reference them here; RPG
        Stations never defines its own drop-table format.
      </p>

      <H2 id="recipe-fromcrafting">Recipe.FromCrafting - deriving from native recipes</H2>
      <pre><code>{`"Recipe": { "FromCrafting": {
  "Categories": ["WoodPlanks"],
  "Benches": ["Campfire"],
  "Types": ["Processing"],
  "NativeTime": { "Scale": 1.0, "OffsetMs": 500 }
} }`}</code></pre>
      <p>
        Instead of hand-listing every conversion, a station can derive its convert recipe straight from the
        engine&apos;s own crafting/processing recipe catalog:
      </p>
      <ul>
        <li><code>Categories</code> - native recipe categories to pull from (e.g. every wood species that shares the <code>WoodPlanks</code> category derives its own conversion, with zero per-species authoring).</li>
        <li><code>Benches</code> - native <code>BenchRequirement</code> bench ids to scope to (a second derivation route for recipes that carry a bench requirement but no category at all).</li>
        <li><code>Types</code> - which recipe kinds to derive, <code>Crafting</code> and/or <code>Processing</code>; absent means both.</li>
      </ul>
      <p>
        A hand-authored <code>Recipe.Conversions</code> entry can also carry an optional <code>Category</code>{' '}
        tag directly - a derived conversion is stamped with its source category automatically, which is what
        powers the multi-output picker (see{' '}
        <Link href="/docs/guides/selection/">Selection &amp; Output Categories</Link>).
      </p>

      <H3 id="native-time-pacing">NativeTime: native recipes drive WHAT, a station owns the PACE</H3>
      <p>
        A native recipe carries its own instant (or near-instant) craft time - that is fine for a vanilla
        bench, but a diegetic work loop is supposed to take longer; the pacing IS the value a station adds.{' '}
        <code>FromCrafting.NativeTime</code> is a linear transform (<code>Scale * recipeTimeMs + OffsetMs</code>)
        applied over a derived recipe&apos;s own native time, with defaults that deliberately keep a station
        slower than vanilla even when authored as an empty <code>{'{}'}</code> group. Per-cycle time
        resolution follows a fixed precedence: an authored <code>Conversion.DurationMs</code> (highest) beats
        the <code>NativeTime</code> transform, which beats the station&apos;s own flat{' '}
        <code>Work.CycleMs</code> (lowest, the fallback when neither of the others applies).
      </p>

      <H2 id="tool-tags">Tool.Tags and Custody.Input.Tags - native tag-family matching</H2>
      <pre><code>{`"Tool": { "Tags": { "Family": ["Dagger"] } }`}</code></pre>
      <p>
        A gate can match the held item&apos;s native tags directly - an ANY-of match per key against the
        item&apos;s own raw tag data. Because most vanilla item families share tags through their native
        parent asset (every dagger tier inherits <code>Tags: {'{Type:["Weapon"], Family:["Dagger"]}'}</code>{' '}
        from a shared template), one <code>Tags.Family</code> reference matches every tier of that family with{' '}
        <strong>zero per-tier enumeration</strong> - and a future new tier of the same family just works, with
        no content update needed here at all. This is the preferred route over an <code>Ids</code> list
        whenever the target items share a native tag; <code>Ids</code> stays as the fallback for items (often
        modded) that carry no shared tag to match on, as the Anvil&apos;s hammer gate does.
      </p>

      <H2 id="animation-emoteid">Animation.EmoteId and Puppet.Prop - native emotes and items</H2>
      <p>
        A station&apos;s work animation (<code>Animation.EmoteId</code>) and a puppet&apos;s held prop
        (<code>Prop.ItemId</code>) are both plain native asset ids - an emote authored the ordinary Hytale way,
        or any item id in the catalog. There is no RPG Stations-specific emote or item format.
      </p>

      <H2 id="sound-particle">Sound and Particles - native audio/VFX assets</H2>
      <p>
        Every <code>Presentation</code>&apos;s <code>Sound</code> and <code>Particles</code> leaves are native
        asset ids too - the shipped content reuses already-existing vanilla ids wherever one fits (a fish
        exemplar&apos;s knife scrape reuses the vanilla dagger swing sound; the Anvil reuses the vanilla metal-
        hit sound and gem-sparks particle) rather than authoring new audio/VFX assets for a mechanic that
        already has a natural-sounding native match.
      </p>

      <H2 id="the-principle">The principle, stated plainly</H2>
      <p>
        Every reference leaf above shares one shape: <strong>a string id (plus, occasionally, a small
        override), never an inlined native asset body.</strong> This keeps RPG Stations content forward-
        compatible with anything a server adds to the native catalog later, keeps authoring DRY (one native
        asset, referenced from as many stations as want it), and keeps every native-reference site
        content-audited the same way - an unresolvable id is always a warn-only finding, never a hard failure.
      </p>
    </DocPage>
  )
}
