import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Getting Started',
  description: 'Install RPG Stations on your Hytale server.',
  openGraph: {
    title: 'Getting Started | RPG Stations Docs',
    description: 'Install RPG Stations, add the ZiggfreedCommon dependency, and place your first station.',
  },
}

export default function GettingStartedPage() {
  return (
    <DocPage
      title="Getting Started"
      description="Install RPG Stations on your Hytale server"
      breadcrumbs={[{ title: 'Getting Started' }]}
      nextPage={{ title: 'Concepts', href: '/docs/concepts/' }}
    >
      <p>
        RPG Stations is a standalone Hytale server mod that adds diegetic interactive work stations - place a
        block, press <code>F</code>, and watch your character (or a stand-in performer) do the work. It is a
        server plugin, package root <code>com.ziggfreed.rpgstations</code>, and it ships as its own jar
        separate from any progression mod.
      </p>

      <H2 id="dependencies">Dependencies</H2>
      <p>
        RPG Stations has exactly <strong>one hard dependency</strong>: <strong>ZiggfreedCommon</strong>{' '}
        (<code>&gt;=1.4.0</code>). ZiggfreedCommon is a shared, mod-agnostic Hytale primitive library
        (camera/sound/HUD primitives, the puppet/performer engine, the generic cast-step kernel) that RPG
        Stations builds its work loop on top of. There is no dependency on any progression mod - RPG Stations
        runs a complete, rewarding work loop (conditional loot, command rewards, enhancement) with{' '}
        <strong>zero skill XP required</strong>.
      </p>
      <p>
        <strong>MMO Skill Tree is optional.</strong> If it is installed alongside RPG Stations, a soft bridge
        (native events and a typed extension registry - never a hard code dependency in either direction)
        turns each completed work cycle into skill XP, and lets loot/enhancement formulas read the MMO&apos;s
        stat channels. See <Link href="/docs/integrations/">Add-ons &amp; Integrations</Link> for the pairing.
      </p>

      <H2 id="installing">Installing</H2>
      <ol>
        <li>Install <strong>ZiggfreedCommon</strong> first - drop its jar into your server&apos;s <code>Mods/</code> folder.</li>
        <li>Drop the <strong>RPG Stations</strong> jar into the same <code>Mods/</code> folder.</li>
        <li>
          Optionally add a content pack that ships station content, such as the Sawmill/Anvil pack - RPG
          Stations ships its own minimal jar-default content (a Sawmill and a fish-preparation exemplar) so it
          is playable with no pack at all, but a pack is where most server owners will get their station
          catalog from.
        </li>
        <li>Restart the server.</li>
      </ol>
      <p>
        Confirm the mod loaded by checking the boot log for a Station asset layer fold line naming each loaded
        station id (for example <code>sawmill</code>), and no <code>Asset validation FAILED</code> entries. Run{' '}
        <code>/rpgstations validate</code> in-game (or from console) at any time to re-check the full content
        audit.
      </p>

      <H2 id="enabling">Enabling</H2>
      <p>
        RPG Stations has no traditional on/off config file toggle - it is governed by an ordinary content
        asset, the <strong>Settings asset</strong> (<code>Server/RpgStations/Settings/Settings.json</code>),
        the same way every other piece of RPG Stations content is authored. The jar ships a default with{' '}
        <code>Enabled: true</code>, so the engine is live out of the box; a server owner (or a pack) can layer
        their own <code>Settings.json</code> over it to disable the engine entirely or retune the session
        summary HUD. See <Link href="/docs/guides/settings/">Settings</Link> for the full shape.
      </p>

      <H2 id="whats-next">What&apos;s next</H2>
      <p>
        Read <Link href="/docs/concepts/">Concepts</Link> for the vocabulary (station, session, action, step,
        custody, puppet), then follow{' '}
        <Link href="/docs/guides/your-first-station/">Your First Station</Link> to author one station end to
        end.
      </p>
    </DocPage>
  )
}
