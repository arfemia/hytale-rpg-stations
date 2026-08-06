import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Flairs',
  description: 'The open flair/moment vocabulary and the standalone FlairAsset.',
  openGraph: {
    title: 'Flairs | RPG Stations Docs',
    description: 'Cosmetic overlays a player can unlock, keyed by an open moment id.',
  },
}

export default function FlairsPage() {
  return (
    <DocPage
      title="Flairs"
      description="Cosmetic overlays a player can unlock, applied per moment"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Flairs' }]}
      prevPage={{ title: 'Enhancement & Stamp', href: '/docs/guides/enhancement-and-stamp/' }}
      nextPage={{ title: 'Extending Other Packs', href: '/docs/guides/extending-other-packs/' }}
    >
      <p>
        A <strong>flair</strong> is a cosmetic <code>Presentation</code> overlay - a fancier sound, extra
        particles, a camera shake - that applies at a specific moment of a station&apos;s work loop, gated
        behind a player unlocking it (through whatever mod registers a <code>FlairUnlockProvider</code>; RPG
        Stations itself never decides who has unlocked what). Flairs are purely presentational: they never change what a
        station produces, only how it looks and sounds while doing it.
      </p>

      <H2 id="moment-ids">The open moment vocabulary</H2>
      <p>
        A flair is keyed by a <strong>moment id</strong> - an open string, never a fixed enum, so a future
        engine version can emit a new moment without a schema break. Five well-known ids cover the built-in
        engine moments:
      </p>
      <table>
        <thead><tr><th>Moment id</th><th>Fires when</th></tr></thead>
        <tbody>
          <tr><td><code>cycle</code></td><td>Each completed work cycle.</td></tr>
          <tr><td><code>swing</code></td><td>Each per-swing animation beat.</td></tr>
          <tr><td><code>impact</code></td><td>A delayed impact cue after a swing.</td></tr>
          <tr><td><code>rare_find</code></td><td>A loot Ladder floor is reached.</td></tr>
          <tr><td><code>completion</code></td><td>The session ends (non-silent, at least one completed cycle).</td></tr>
        </tbody>
      </table>
      <p>
        A step program adds a SIXTH kind of moment id automatically for every step: <code>step:&lt;actionId&gt;:&lt;stepId&gt;</code>,
        letting a flair target one specific beat of a specific ritual (for example{' '}
        <code>step:enhance:stamp</code> for the Anvil&apos;s enhance-commit beat specifically). An
        unrecognized moment id is a content-audit note (typo detection), never an error - an older-authored
        flair never breaks against a newer engine that has grown new moment ids.
      </p>

      <H2 id="flairasset">The standalone FlairAsset</H2>
      <p>
        A flair lives in its own file, <code>Server/RpgStations/Flairs/&lt;Name&gt;.json</code> (Pattern A,
        id = lowercased filename) - ANY installed mod or pack can ship one, targeting any station, without
        touching that station&apos;s own file at all:
      </p>
      <pre><code>{`{
  "Stations": ["sawmill"],
  "Moments": {
    "swing": { "Particles": "Petal_Burst" },
    "step:enhance:stamp": { "Sound": "SFX_Choir_Hit" }
  }
}`}</code></pre>
      <p>
        <code>Stations</code> null or empty means &quot;applies to every station&quot;; <code>Moments</code> is a plain
        moment id → <code>Presentation</code> map. A grantor unlocks this asset&apos;s own id (the
        filename) for a player through whichever unlock mechanism it uses; RPG Stations just consults the
        result per moment.
      </p>

      <H2 id="inline-flairs">A station&apos;s own inline flairs</H2>
      <p>
        A station can also author its own <code>Flairs</code> map directly, the SAME open{' '}
        <code>{'{Moments}'}</code> shape as a standalone <code>FlairAsset</code> - a pure authoring
        convenience for a flair that will only ever apply to this one station and does not need to be
        shareable across packs.
      </p>

      <H2 id="the-merge">How they combine</H2>
      <p>
        The effective flair set for a station is the UNION of its own inline <code>Flairs</code> map with
        every folded <code>FlairAsset</code> whose <code>Stations</code> list applies to it. When a
        standalone asset ships the same flair id as the station&apos;s own inline entry, the standalone
        asset&apos;s <code>Moments</code> win for that id. This means multiple packs can each ship their own
        flair asset targeting the same station with no coordination needed, as long as they use distinct flair
        ids.
      </p>

      <H2 id="not-extension-composable">Not extension-composable, by design</H2>
      <p>
        Flairs are deliberately excluded from the{' '}
        <Link href="/docs/guides/extending-other-packs/">Extending Other Packs</Link> mechanism - a
        cosmetic overlay never needs to be additively merged into an existing flair&apos;s{' '}
        <code>Moments</code>, because the union above already composes any number of separately-authored
        flairs cleanly. To add a new cosmetic to an existing station, ship another <code>FlairAsset</code>{' '}
        naming that station; there is nothing to extend.
      </p>
    </DocPage>
  )
}
