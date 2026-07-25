import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Concepts',
  description: 'Stations, sessions, actions, steps, custody, and the puppet - the RPG Stations vocabulary.',
  openGraph: {
    title: 'Concepts | RPG Stations Docs',
    description: 'The core vocabulary: station, session, action, step, custody, puppet.',
  },
}

export default function ConceptsPage() {
  return (
    <DocPage
      title="Concepts"
      description="The vocabulary every other page in these docs assumes"
      breadcrumbs={[{ title: 'Concepts' }]}
      prevPage={{ title: 'Getting Started', href: '/docs/getting-started/' }}
      nextPage={{ title: 'Your First Station', href: '/docs/guides/your-first-station/' }}
    >
      <p>
        RPG Stations models a diegetic work loop: a player interacts with a placed block, their character
        performs the work in-world (swinging a saw, hammering an anvil, scaling a fish), and materials convert
        into results over real time instead of an instant menu transaction. Six concepts cover the whole
        engine.
      </p>

      <H2 id="stations-and-sessions">Stations and sessions</H2>
      <p>
        A <strong>station</strong> is one <code>StationAsset</code> JSON, one in-world block, and one
        registered interaction handler tying them together - a Sawmill, a Cutting Board, an Anvil. The asset
        declares the station&apos;s groups: <code>Identity</code> (name/desc/icon), <code>Work</code> (cycle
        cadence and XP declarations), <code>Recipe</code>, <code>Hold</code> (how the player is held in
        place), <code>Tool</code> (the held-tool gate), <code>Custody</code>, <code>Loot</code>,{' '}
        <code>Camera</code>, <code>Animation</code>, <code>Presentation</code>, <code>Requires</code>,{' '}
        <code>Puppet</code>, <code>Flairs</code>, and <code>Actions</code>.
      </p>
      <p>
        A <strong>session</strong> is the transient, in-memory work loop a player starts by pressing{' '}
        <code>F</code> on a station block. Sessions are never persisted - a server restart or crash loses
        every in-flight session by construction, and every affected state (custody claims, the puppet, display
        entities, anchor claims) self-heals or auto-returns on the next interaction. One entry point
        (<code>toggle</code>) starts or stops a session; one exit funnel (<code>stop</code>) handles every
        reason a session ends (re-press, crouch, walk off, damage, death, disconnect, tool broke, ritual
        complete, inputs exhausted, an anchor block was broken, a path was blocked, or server shutdown) and
        always fires a completion event, silent stops included.
      </p>

      <H2 id="actions">Actions</H2>
      <p>
        A station runs one or more named <strong>actions</strong>. A station with no <code>Actions</code> map
        gets exactly one implicit action called <code>work</code>, built from the station&apos;s own
        top-level groups (its <code>Work</code>/<code>Recipe</code>/<code>Tool</code>/etc.) - this is what
        every single-purpose station like a classic Sawmill uses, and it is byte-identical to the pre-multi-
        action engine.
      </p>
      <p>
        A <strong>multi-action station</strong> (the Anvil is the flagship example) authors an{' '}
        <code>Actions</code> map with two or more named entries, each a whole-group override of any station
        group. Which action a session runs is decided <strong>diegetically</strong>: at engage, the engine
        matches the player&apos;s held item (or a loaded custody claim) against each action&apos;s{' '}
        <code>Input</code> matcher and picks the first that fits - no menu, no dropdown, just &quot;what are
        you holding&quot;. A standalone, reusable action can also live in its own file (an{' '}
        <code>ActionAsset</code>) and be attached to a station by reference. See{' '}
        <Link href="/docs/guides/actions-and-steps/">Actions &amp; Step Programs</Link>.
      </p>

      <H2 id="steps">Steps</H2>
      <p>
        An action either runs the <strong>implicit program</strong> (the classic convert-consume-produce-
        roll-present loop every plain station uses) or an authored <strong>step program</strong> - an ordered
        array of <code>StationStep</code>s. Each step composes any combination of independent phases
        (Walk, Consume, Stamp, Produce, Roll, Commands) in one fixed order, plus a post-phase{' '}
        <code>Duration</code> hold and an iteration <code>Repeat</code> count. A step authoring no phase at
        all is a pure <strong>beat</strong> - just a clip, a presentation cue, and a hold, used for the
        anvil&apos;s hammer strikes. See{' '}
        <Link href="/docs/guides/actions-and-steps/">Actions &amp; Step Programs</Link>.
      </p>

      <H2 id="custody">Custody</H2>
      <p>
        <strong>Custody</strong> is a session-scoped, placed-input claim: press <code>F</code> while holding a
        matching stack and it loads the whole stack into the block (a repeat press tops it up), then a
        press by the owner starts working from that placed pile instead of the live backpack. Custody is
        never persisted (a crash returns to the owner or drops at the block on the next interaction) and can
        optionally render as a real placed-as-entity prop at the block. See{' '}
        <Link href="/docs/guides/custody-and-placed-display/">Custody &amp; Placed Display</Link>.
      </p>

      <H2 id="the-puppet">The puppet</H2>
      <p>
        By default a session holds the real player in place and plays the work animation on their own body.
        An optional <strong>puppet</strong> route instead hides the player entirely and spawns a stand-in
        performer (a clone of the player&apos;s own look, a fixed model, or a Role-driven NPC) that visibly
        does the work - including walking between a primary station and a nearby anchor station in a
        multi-station program. See{' '}
        <Link href="/docs/guides/puppet-presentation/">Puppet &amp; Performers</Link>.
      </p>

      <H2 id="content-not-config">Content, not config files</H2>
      <p>
        Every one of the concepts above is authored as a Hytale asset-pack JSON under{' '}
        <code>Server/RpgStations/&lt;Type&gt;/*.json</code> - stations, standalone actions, lootables, roll
        pools, flairs, extensions, and settings. There is no separate config-file layer; adding or changing
        content is adding or editing an asset, and a server owner or pack author gets the exact same
        authoring surface RPG Stations itself ships its default content through.
      </p>
    </DocPage>
  )
}
