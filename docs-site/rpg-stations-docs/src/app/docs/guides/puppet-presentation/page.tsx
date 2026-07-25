import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Puppet & Performers',
  description: 'Hide the player, spawn a performer that does the work instead.',
  openGraph: {
    title: 'Puppet & Performers | RPG Stations Docs',
    description: 'Look sources, NpcRole performers, persistence and reconcile, and the walk mechanism.',
  },
}

export default function PuppetPresentationPage() {
  return (
    <DocPage
      title="Puppet & Performers"
      description="Mount the player, hide their body, spawn a performer doing the work instead"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Puppet & Performers' }]}
      prevPage={{ title: 'Custody & Placed Display', href: '/docs/guides/custody-and-placed-display/' }}
      nextPage={{ title: 'Selection & Output Categories', href: '/docs/guides/selection/' }}
    >
      <p>
        By default a session holds the real player in place and plays the work animation directly on their
        body. The optional <code>Puppet</code> group flips that: it hides the player entirely and spawns a
        stand-in <strong>performer</strong> that visibly does the work - the maintainer&apos;s own framing is
        &quot;mount the player, hide their player model, and spawn a visual of their character model performing
        the steps.&quot; A station whose action authors any <code>Walk</code> step (see{' '}
        <Link href="/docs/guides/multi-station-programs/">Multi-Station Programs</Link>) REQUIRES a puppet -
        there is nothing else to walk.
      </p>
      <p>
        <code>Puppet</code> is a top-level group, a sibling to <code>Hold</code>/<code>Camera</code>/
        <code>Animation</code>/<code>Custody</code> - orthogonal to whichever mount mechanism holds the real
        player, never nested under <code>Hold</code>. It is whole-group overridable per action, exactly like
        every other station group.
      </p>

      <H2 id="the-group">The Puppet group</H2>
      <pre><code>{`"Puppet": {
  "Enabled": true,
  "Hide":   { "Route": "Scale" },
  "Look":   { "Source": "PlayerClone" },
  "Offset": { "X": 0.0, "Y": -0.4, "Z": 0.6 },
  "Yaw":    0.0,
  "Prop":   { "Source": "MirrorHeld", "Slot": "Hotbar" }
}`}</code></pre>
      <p>
        <code>Offset</code>/<code>Yaw</code> place the puppet relative to the station&apos;s block-top anchor
        in WORLD space (unlike the facing-relative <code>Custody.Display</code>). Presence of the group with{' '}
        <code>Enabled</code> not explicitly <code>false</code> activates the whole route - spawn and hide
        together; <code>false</code> keeps the classic in-body worker regardless of what the other leaves
        author (useful for flipping a puppet off on a <code>Parent</code>-inherited variant while keeping the
        rest of the group).
      </p>

      <H2 id="hide-route">Hide.Route</H2>
      <p>
        <code>Hide.Route</code> is a union discriminator, not a mode - three structurally different arms:
      </p>
      <ul>
        <li><code>&quot;Scale&quot;</code> (the default, proven in-game) - fully hides the puppeteer&apos;s own rendered body, including the held item, in both first- and third-person.</li>
        <li><code>&quot;Effect&quot;</code> - schema-reserved for a future native-effect-based hide route; not implemented yet.</li>
        <li><code>&quot;None&quot;</code> - the degraded fallback: the puppet spawns but the real player stays visible too.</li>
      </ul>

      <H2 id="look-source">Look.Source</H2>
      <p>
        <code>Look.Source</code> is a three-arm union deciding who/what the performer looks like:
      </p>
      <table>
        <thead><tr><th>Source</th><th>Group read</th><th>Behavior</th></tr></thead>
        <tbody>
          <tr><td><code>PlayerClone</code> (default)</td><td>none</td><td>The puppet clones the live player&apos;s own skin - &quot;their character model.&quot;</td></tr>
          <tr><td><code>Model</code></td><td><code>Look.Model {'{ModelId, FallbackModelId}'}</code></td><td>A fixed authored model, regardless of who is working (e.g. an apprentice or golem stand-in).</td></tr>
          <tr><td><code>NpcRole</code></td><td><code>Look.Role</code> (below)</td><td>A Role-driven NPC entity performs the work instead of a player-shaped puppet.</td></tr>
        </tbody>
      </table>
      <p>
        <code>Look.Model.FallbackModelId</code> doubles as the resolution-ladder fallback for the WHOLE Look
        group, any source: an unresolvable primary look (a clone failure, a dangling <code>ModelId</code> or
        role id) falls to it, then to the engine&apos;s own default rig - never a red-X or a crash.
      </p>

      <H3 id="npcrole-performer">The NpcRole performer</H3>
      <pre><code>{`"Look": { "Source": "NpcRole", "Role": {
  "RoleId": "SomeRole",
  "SkinSource": "PlayerClone",
  "Persist": false,
  "SpeedMps": 2.5
} }`}</code></pre>
      <table>
        <thead><tr><th>Field</th><th>What it does</th></tr></thead>
        <tbody>
          <tr><td><code>RoleId</code></td><td>The native Role asset id backing this performer (an id-ref-only reference - see <Link href="/docs/guides/native-composition/">Native Composition</Link>). Required for this source; a dangling id falls back to the bare player-shaped puppet with one warning.</td></tr>
          <tr><td><code>SkinSource</code></td><td><code>PlayerClone</code> (default, the Role NPC wears a clone of the working player&apos;s own skin) or <code>RoleDefault</code> (the role asset&apos;s own model).</td></tr>
          <tr><td><code>Persist</code></td><td><code>false</code> (default) spawns the NPC as a transient, non-serialized entity, matching the bare-puppet posture. <code>true</code> is reserved for a future persistent-hireling posture.</td></tr>
          <tr><td><code>SpeedMps</code></td><td>The performer&apos;s walk-speed override; null defers to the role asset&apos;s own configured walk speed (2.5 m/s parity with the default puppet).</td></tr>
        </tbody>
      </table>
      <p>
        At engage the engine reads <code>Look.Source</code> and picks the backend: <code>PlayerClone</code>/
        <code>Model</code> use the proven player-shaped puppet; <code>NpcRole</code> uses the Role-driven
        backend, with an engage-time fail-closed fallback to the player-shaped puppet (one warning logged)
        when the role id is blank or unregistered. Every later mutation during the session (clip changes,
        prop swaps, despawn) goes through the same per-call accessor regardless of which backend is active, so
        step programs never need to know which performer is running.
      </p>

      <H2 id="prop">Prop</H2>
      <pre><code>{`"Prop": { "Source": "MirrorHeld", "Slot": "Hotbar" }`}</code></pre>
      <p>
        <code>Prop.Source</code> defaults to <code>MirrorHeld</code> (the puppet holds a live copy of
        whatever the player is holding). <code>ItemId</code> forces a specific held prop (a ritual can hand
        the puppet a specific tool regardless of what the player holds); <code>None</code> empties the
        puppet&apos;s hands. This exact shape is reused verbatim by a per-step <code>Puppet.Prop</code>{' '}
        override for a moment-to-moment swap (the fish exemplar swaps the carried prop to the raw fish while
        walking out, then to the grilled fish while walking back).
      </p>

      <H2 id="walk-mechanism">The walk mechanism</H2>
      <p>
        A step&apos;s <code>Walk</code> phase (see{' '}
        <Link href="/docs/guides/multi-station-programs/">Multi-Station Programs</Link>) moves the PUPPET, not
        the real player, along an obstacle-aware path toward a declared anchor, and toggles its movement state
        so the walk renders as an actual walking gait rather than a slide.
      </p>

      <H2 id="reconcile">Identity and reconcile</H2>
      <p>
        Every spawned performer carries a marked identity component so the engine can find and clean up any
        performer left over from a crash or restart: at server boot, one reconcile sweep despawns every
        performer with no live session behind it, and a fresh engage also runs a targeted stale-performer
        sweep for the block being interacted with. Combined with the &quot;nothing here is persisted&quot; rule (see{' '}
        <Link href="/docs/concepts/">Concepts</Link>), this means a puppet or NPC performer never survives a
        restart as an orphaned entity, even after an ungraceful shutdown.
      </p>
    </DocPage>
  )
}
