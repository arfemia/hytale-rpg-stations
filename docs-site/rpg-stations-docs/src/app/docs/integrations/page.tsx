import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Add-ons & Integrations',
  description: 'The typed extension surface any mod hooks to turn station work into its own rewards.',
  openGraph: {
    title: 'Add-ons & Integrations | RPG Stations Docs',
    description: 'Native events plus a typed api artifact let any mod reward station work - without either mod depending on the other.',
  },
}

export default function IntegrationsPage() {
  return (
    <DocPage
      title="Add-ons & Integrations"
      description="How another mod hooks station work through the api artifact"
      breadcrumbs={[{ title: 'Add-ons & Integrations' }]}
    >
      <p>
        RPG Stations carries <strong>no progression vocabulary of its own</strong> and depends on no other
        mod. Every station runs its full loop - conditional loot, command rewards, enhancement - standalone.
        When another mod IS installed alongside it, a soft extension surface lets that mod turn completed work
        into its own rewards. The coupling is optional in both directions: neither mod hard-depends on the
        other, and each runs unaffected when the other is absent.
      </p>
      <p>
        The two-registry contract is the whole idea in one line: <strong>factors read a number IN, channels
        post a number OUT</strong>, and both are namespaced ids this engine never interprets. Read{' '}
        <Link href="/docs/extension-channels/">Extension Channels</Link> first - it teaches both directions
        with a complete worked example.
      </p>

      <H2 id="the-api-artifact">The api artifact</H2>
      <p>
        The extension surface is a small, typed contract published as its own jar (
        <code>rpg-stations-api</code>). A mod that wants to hook the engine links against it{' '}
        <code>compileOnly</code> and declares RPG Stations an optional dependency, then presence-checks the
        plugin at runtime before touching it. The api is split by shape, following the same native-events
        convention the rest of the ecosystem uses:
      </p>
      <p>
        <strong>The two-step idiom is not optional.</strong> The JVM verifies a method&apos;s whole bytecode
        the first time it is invoked, so a method that both presence-checks RPG Stations AND references an api
        type throws <code>NoClassDefFoundError</code> merely by being CALLED when RPG Stations is absent -
        even though the guard clause would have skipped the api branch at runtime. Split it: (1) a presence
        check using only core Hytale types (<code>PluginManager.getPlugin</code>, which returns null for both
        a missing plugin and a disabled one), and (2) a private nested class holder that references api types,
        loaded only on its own first active use, which now only happens after step 1 confirmed a live
        instance. Every public method the consumer exposes follows the same shape: a thin flag-gated outer
        wrapper with zero api-type references, delegating into the holder.
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
          <tr><td><code>StationCycleCompletedEvent</code></td><td>One work cycle finishes; carries the station&apos;s declared contributions (per-cycle and one-shot) plus the tool multiplier.</td></tr>
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
        Where the engine needs an answer from another mod, it reads a typed registry on the static api
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
      <H3 id="contribution-channel-registry">Contribution-channel registry</H3>
      <p>
        The write-side twin: <code>declare(channelId)</code> registers a namespaced id a station&apos;s{' '}
        <code>Contributions</code> can post to. Declaration only - there is nothing to resolve, because the
        engine forwards every contribution verbatim on the cycle event and interprets none of them. Declaring
        feeds the <code>rpgstations:channels</code> editor dropdown and the <code>UNKNOWN_CHANNEL</code>{' '}
        validator warning; an undeclared channel still forwards. See{' '}
        <Link href="/docs/extension-channels/">Extension Channels</Link>.
      </p>
      <H3 id="validation-hook-registry">Validation-hook registry</H3>
      <p>
        Third-party content checks that run inside the engine&apos;s own full validate pass, so a mod that
        owns a factor family or a contribution channel keeps its composition rules with the vocabulary instead
        of hardcoding them in this engine. A hook sees the folded stations and every roll&apos;s reference
        structure and formula numbers, and reports info/warn findings. Advisory only, try-guarded, never
        blocking.
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

      <H2 id="known-integrations">Known integrations</H2>
      <p>
        Mods that ship an RPG Stations integration. Each one documents its own ids and behavior on its own
        site; nothing about them is duplicated here.
      </p>
      <ul>
        <li>
          <a href="https://mmo-skill-tree-docs.ziggfreed.com" target="_blank" rel="noopener noreferrer">
            MMO Skill Tree
          </a>
        </li>
      </ul>
      <p>
        Building one? Everything a consumer needs is on this page and{' '}
        <Link href="/docs/extension-channels/">Extension Channels</Link> - no RPG Stations code change is
        involved, and no listing here is required for an integration to work.
      </p>
    </DocPage>
  )
}
