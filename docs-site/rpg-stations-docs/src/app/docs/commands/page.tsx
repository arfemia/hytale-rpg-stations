import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Commands',
  description: 'The /rpgstations admin command group: camera preset tuning and the content validator.',
  openGraph: {
    title: 'Commands | RPG Stations Docs',
    description: '/rpgstations camera <preset>|list and /rpgstations validate - the admin-gated maintenance commands.',
  },
}

export default function CommandsPage() {
  return (
    <DocPage
      title="Commands"
      description="The single admin-gated /rpgstations command group - camera tuning and content validation"
      breadcrumbs={[{ title: 'Server Setup' }, { title: 'Commands' }]}
    >
      <p>
        RPG Stations registers one command group, <code>/rpgstations &lt;camera|validate&gt; [action]</code>.
        It is <strong>admin-gated</strong> at the framework level (the calling player is an operator when
        permissions are off, otherwise holds the admin permission group); a non-admin never sees it. There is
        no per-station or per-player configuration command - stations are content, authored and audited as
        assets, so these two subcommands cover the whole runtime surface.
      </p>

      <H2 id="camera">/rpgstations camera</H2>
      <p>
        Sets the CALLING player&apos;s own station-camera tuning override for their next station session.
        The override is transient - held in memory, never persisted - and applies to that player only (a
        console sender has no camera preference to set). It overrides whatever <code>Camera.Recipe</code>{' '}
        default the station asset itself declares, so it is the quick way to preview a camera feel in-world
        without editing and reloading an asset.
      </p>
      <table>
        <thead>
          <tr><th>Command</th><th>Effect</th></tr>
        </thead>
        <tbody>
          <tr>
            <td><code>/rpgstations camera &lt;preset&gt;</code></td>
            <td>Set your own camera preset override for your next session.</td>
          </tr>
          <tr>
            <td><code>/rpgstations camera list</code></td>
            <td>Chat every known preset id (listed dynamically, never a hand-maintained list) plus your current one.</td>
          </tr>
        </tbody>
      </table>
      <p>
        An unknown preset id is rejected with the list of valid ids, so <code>camera list</code> is the
        discovery path - run it first to see what your build offers.
      </p>

      <H2 id="validate">/rpgstations validate</H2>
      <p>
        Runs the full content audit over every loaded station, action, lootable, roll pool, and extension,
        then reports the aggregate in chat: a summary line followed by every finding, each coloured by
        severity (error / warning / info). This is the <strong>same audit</strong> that runs once
        automatically at server boot and prints to the log - <code>validate</code> just lets you re-run it on
        demand and read it in chat, and it re-logs a matching run at the same time.
      </p>
      <p>
        The audit is advisory: warnings never stop a station from loading or running. Use it after authoring
        or editing content to catch typos (an unknown factor id, a moment id that matches no known moment, a
        flair naming a station that does not exist) before players hit them. See{' '}
        <Link href="/docs/getting-started/">Getting Started</Link> for where it fits in a first-boot check.
      </p>
    </DocPage>
  )
}
