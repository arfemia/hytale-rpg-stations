import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Add-ons & Integrations',
  description: 'The typed extension surface any progression mod hooks, and the MMO Skill Tree pairing.',
  openGraph: {
    title: 'Add-ons & Integrations | RPG Stations Docs',
    description: 'Native events plus a typed api artifact let any progression mod reward station work - without either mod depending on the other.',
  },
}

export default function IntegrationsPage() {
  return (
    <DocPage
      title="Add-ons & Integrations"
      description="How a progression mod hooks station work through the api artifact, and the MMO Skill Tree pairing"
      breadcrumbs={[{ title: 'Add-ons & Integrations' }]}
    >
      <p>
        RPG Stations has <strong>no progression of its own</strong> and depends on no progression mod. Every
        station runs its full standalone loop - conditional loot, command rewards, enhancement - with zero XP
        granted. When a progression mod IS installed alongside it, a soft extension surface lets that mod turn
        completed work into its own rewards. The coupling is one-directional and optional in both directions:
        neither mod hard-depends on the other, and each runs unaffected when the other is absent.
      </p>

      <H2 id="the-api-artifact">The api artifact</H2>
      <p>
        The extension surface is a small, typed contract published as its own jar (
        <code>rpg-stations-api</code>). A mod that wants to hook the engine links against it{' '}
        <code>compileOnly</code> and declares RPG Stations an optional dependency, then presence-checks the
        plugin at runtime before touching it. The api is split by shape, following the same native-events
        convention the rest of the ecosystem uses:
      </p>
      <ul>
        <li><strong>Observe-only moments are native Hytale events</strong> a mod listens for.</li>
        <li><strong>Request/response points are typed registries</strong> a mod registers a provider into.</li>
      </ul>

      <H2 id="events">Native events (observe-only)</H2>
      <p>
        RPG Stations dispatches native Hytale events at each meaningful moment of a work session, on the
        owning world thread, only when something is actually listening. A listener observes; it never has to
        answer:
      </p>
      <table>
        <thead>
          <tr><th>Event</th><th>Fires when</th></tr>
        </thead>
        <tbody>
          <tr><td><code>StationSessionStartedEvent</code></td><td>A player engages a station and a work session begins.</td></tr>
          <tr><td><code>StationCycleCompletedEvent</code></td><td>One work cycle finishes; carries the station&apos;s declared per-cycle XP asks.</td></tr>
          <tr><td><code>StationSessionCompletedEvent</code></td><td>The session stops (for any reason); fires after summary enrichers run.</td></tr>
          <tr><td><code>StationEnhanceCompletedEvent</code></td><td>An enhancement Stamp commits; carries before/after item copies and the enhancement report.</td></tr>
          <tr><td><code>StationToolBrokeEvent</code></td><td>A tool the session was using breaks.</td></tr>
        </tbody>
      </table>
      <p>
        Each event&apos;s fields document which are plain data (safe to keep) and which are live world-thread
        context valid only during dispatch - a listener that defers work captures the plain fields and
        re-resolves the rest.
      </p>

      <H2 id="registries">Typed registries (request/response)</H2>
      <p>
        Where the engine needs an answer from a progression mod, it reads a typed registry on the static api
        holder. Register a provider once at startup:
      </p>
      <H3 id="factor-registry">Factor registry</H3>
      <p>
        The one extensible numeric-factor vocabulary every conditional-lootable <code>Roll</code> (its
        conditions, chances, and ladders) and every station <code>Requires</code> gate evaluates over. RPG
        Stations ships its own built-ins under the <code>rpgstations:</code> namespace (session seconds,
        cycle count, tool power, tool durability); an external mod registers namespace-prefixed ids. An
        unknown factor at runtime fails a condition closed and resolves a value to 0, each with a one-time
        warning - never a crash. See <Link href="/docs/guides/loot-and-factors/">Loot &amp; Factors</Link>{' '}
        for how a station author references one.
      </p>
      <H3 id="flair-unlock-registry">Flair-unlock registry</H3>
      <p>
        Answers &quot;which flair ids has this player unlocked&quot;. The engine consults the union across
        every registered provider; persistence is the registering mod&apos;s own concern, since RPG Stations
        stores no per-player fact. See <Link href="/docs/guides/flairs/">Flairs</Link>.
      </p>
      <H3 id="enhance-stamper-registry">Enhance-stamper registry</H3>
      <p>
        The single active delegate the anvil&apos;s Stamp step calls to read and write a weapon&apos;s
        enhancement state. RPG Stations owns all the roll and cap math; the stamper only encodes how a given
        server stores enhancement points onto a stack, and returns a report RPG Stations renders verbatim in
        the summary - so no stat vocabulary leaks into this mod. With none registered, the Stamp step still
        applies durability. See <Link href="/docs/guides/enhancement-and-stamp/">Enhancement &amp; Stamp</Link>.
      </p>
      <H3 id="summary-enricher-registry">Summary-enricher registry</H3>
      <p>
        Adds extra ledger rows to the session-summary panel (prepended before the engine&apos;s own item
        rows) plus an optional theming hook over the panel. See{' '}
        <Link href="/docs/guides/settings/#the-summary-panel">Settings</Link> for what the panel shows.
      </p>

      <H2 id="mmo-skill-tree">The MMO Skill Tree pairing</H2>
      <p>
        <strong>MMO Skill Tree</strong> is the first mod to pair with RPG Stations, and the reference
        consumer of every seam above. Install both and:
      </p>
      <ul>
        <li>each completed work cycle forwards its declared XP asks into the MMO&apos;s skill system;</li>
        <li>loot and enhancement formulas can read any of the MMO&apos;s stat channels (luck, skill level, combat level, and more) through the factor registry - see <Link href="/docs/stat-channels/">Stat Channels</Link>;</li>
        <li>the session summary panel gains XP rows alongside RPG Stations&apos; own totals, via a summary enricher.</li>
      </ul>
      <p>
        None of this is a hard dependency: RPG Stations runs a complete standalone experience without MMO
        Skill Tree installed, and MMO Skill Tree runs unaffected without RPG Stations installed. Any other
        progression mod can hook the same surface with zero RPG Stations code change.
      </p>
    </DocPage>
  )
}
