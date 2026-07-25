import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Custody & Placed Display',
  description: 'Placed-input custody claims, block states, and the facing-relative placed-as-entity display.',
  openGraph: {
    title: 'Custody & Placed Display | RPG Stations Docs',
    description: 'Load materials into a station block and render them as a real placed prop.',
  },
}

export default function CustodyAndPlacedDisplayPage() {
  return (
    <DocPage
      title="Custody & Placed Display"
      description="Load materials into a station block, and render them as a placed prop"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Custody & Placed Display' }]}
      prevPage={{ title: 'Multi-Station Programs', href: '/docs/guides/multi-station-programs/' }}
      nextPage={{ title: 'Puppet & Performers', href: '/docs/guides/puppet-presentation/' }}
    >
      <p>
        <code>Custody</code> opts a station (or one action of a multi-action station) into a
        state-dependent <code>F</code> interaction: press <code>F</code> while holding a matching stack and
        it loads into the block instead of starting a session (a repeat press tops it up with further matching
        stacks); the owner pressing <code>F</code> on a loaded block starts working FROM that placed pile
        instead of the live backpack. The claim itself is in-memory only - never persisted, and it
        auto-returns to the owner (or drops at the block) on every session-stop path and on server shutdown.
      </p>

      <H2 id="the-group">The Custody group</H2>
      <pre><code>{`"Custody": {
  "MaxQuantity": 100,
  "Input": { "ResourceTypeId": "Fish" },
  "States": { "Empty": "Default", "Loaded": "Loaded" },
  "Display": { "Offset": { "Y": 0.55 }, "Scale": 1.0 }
}`}</code></pre>
      <table>
        <thead><tr><th>Field</th><th>What it does</th></tr></thead>
        <tbody>
          <tr><td><code>MaxQuantity</code></td><td>The cap on the claim (default 100). The Anvil&apos;s weapon-placement custody uses <code>1</code> - a single, metadata-preserving item, not a stack.</td></tr>
          <tr><td><code>Input</code></td><td>The placement-acceptance matcher, reusing the same <code>ItemId</code>/<code>ResourceTypeId</code>/<code>Tags</code>/<code>Function</code> routes an action&apos;s diegetic <code>Input</code> uses. When absent, acceptance derives from the resolved action&apos;s own <code>Recipe.Conversions</code> inputs - zero extra authoring for a plain convert station (the &quot;logs by ResourceTypeId family&quot; fallback).</td></tr>
          <tr><td><code>States</code></td><td><code>{'{Empty?, Loaded?}'}</code> - the block&apos;s OWN interaction-state names custody flips between (hint-only: swaps the interaction-hint text, no visual model swap). Omit it and custody still works mechanically, just with no state flip.</td></tr>
          <tr><td><code>Display</code></td><td>Opts the placed input into a rendered prop entity at the block. See below.</td></tr>
        </tbody>
      </table>
      <p>
        A single-item placement (<code>MaxQuantity: 1</code>) preserves the placed stack&apos;s full metadata
        (durability, enhancement rolls) rather than collapsing it to a bare fresh stack - this is what makes
        placing an already-enhanced weapon on the Anvil safe.
      </p>

      <H2 id="placed-display">The placed-as-entity display</H2>
      <p>
        Authoring <code>Custody.Display</code> spawns a real, network-replicated, pickup-immune,
        physics-free prop entity rendering the placed item at the station&apos;s block-top anchor - the same
        point every cycle/swing/rare-find presentation moment already targets. Block-shaped custody items (the
        Sawmill&apos;s placed logs) render as the real block model; everything else (the Anvil&apos;s placed
        weapon or bar) renders as the generic dropped-item prop shape. The display entity is never persisted -
        it despawns on return or block break, matching the claim&apos;s own lifecycle exactly.
      </p>
      <pre><code>{`"Display": {
  "Offset": { "X": 0.4, "Y": 0.55 },
  "Scale": 1.0,
  "Rotation": { "Y": 0.0, "Z": 90.0 }
}`}</code></pre>
      <table>
        <thead><tr><th>Field</th><th>What it does</th></tr></thead>
        <tbody>
          <tr><td><code>Offset</code></td><td><code>{'{X,Y,Z}'}</code> shift off the block-top anchor. <code>Y</code> is vertical; <code>X</code>/<code>Z</code> are horizontal.</td></tr>
          <tr><td><code>Scale</code></td><td>Resizes the prop (default 1.0).</td></tr>
          <tr><td><code>Rotation</code></td><td><code>{'{X,Y,Z}'}</code> in DEGREES: X is pitch, Y is yaw, Z is roll. Every leaf defaults to 0.</td></tr>
        </tbody>
      </table>

      <H3 id="facing-relative">Facing-relative, not absolute world-space</H3>
      <p>
        <code>Offset</code> and <code>Rotation</code> are authored RELATIVE TO THE PLACED BLOCK&apos;S OWN
        FACING, not absolute world axes: an authored <code>+Z</code> offset always lands toward the same face
        of the block regardless of how a server owner rotated it when placing it, and the block&apos;s own
        facing yaw is folded into <code>Rotation.Y</code> so a rotated placement carries the prop&apos;s
        position AND facing around with it. At a default-orientation placement (block yaw 0) the local frame
        equals the world frame, so an authored value there behaves exactly like a naive world-space offset -
        every station that only authors a vertical <code>Offset.Y</code> (the Sawmill&apos;s placed logs, the
        Anvil&apos;s placed bar) is completely unaffected by this convention and needs no re-tuning.
      </p>
      <p>
        Rotation composes through the engine&apos;s yaw-then-pitch-then-roll order. For a real weapon mesh
        prop, tune it empirically in three questions: is the blade spun the wrong way in the horizontal plane
        (adjust <code>Rotation.Y</code>)? Is it standing on its edge instead of lying flat (check{' '}
        <code>Rotation.Z</code>, typically <code>90</code> to lay flat)? Is it sunk into or floating above the
        block (nudge <code>Offset.Y</code> in small steps)?
      </p>

      <H2 id="retrieval">Press-F retrieval</H2>
      <p>
        A placed-as-entity display is directly retrievable: pressing <code>F</code> on the display entity
        itself (not the block) hands the placed contents back to the owner and despawns the display, provided
        no session is actively working that block - a session actively working the station always wins over a
        retrieval attempt.
      </p>

      <H2 id="acceptance-precedence">Acceptance precedence at a claimed block</H2>
      <p>
        A block already busy with its own session, or already holding a non-empty custody claim, refuses an
        incoming claim from a different program (relevant when the same block is also declared as a{' '}
        <Link href="/docs/guides/multi-station-programs/">multi-station anchor</Link> by some OTHER action) -
        a restart&apos;s self-heal consults the live custody claim, not just the session map, so this
        precedence holds even across a server restart.
      </p>
    </DocPage>
  )
}
