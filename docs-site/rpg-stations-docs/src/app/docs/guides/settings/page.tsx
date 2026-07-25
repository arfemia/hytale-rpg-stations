import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Settings',
  description: 'The RpgStationsSettingsAsset server-wide Enabled flag and Summary HUD.',
  openGraph: {
    title: 'Settings | RPG Stations Docs',
    description: 'One asset, two orthogonal knobs: engine on/off, and the session-summary HUD.',
  },
}

export default function SettingsPage() {
  return (
    <DocPage
      title="Settings"
      description="One server-wide asset: engine on/off, and the session-summary HUD"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Settings' }]}
      prevPage={{ title: 'Extending Other Packs', href: '/docs/guides/extending-other-packs/' }}
      nextPage={{ title: 'Localization', href: '/docs/guides/localization/' }}
    >
      <p>
        RPG Stations has no separate config-file layer - even its server-wide toggles are an ordinary content
        asset, <code>Server/RpgStations/Settings/Settings.json</code>. There is exactly one fixed id (
        <code>settings</code>) regardless of the filename you use; the jar ships a complete default, and a
        server owner or pack overrides it the same way any other Pattern-A asset is overridden - a pack
        layering its own <code>Settings.json</code> wins over the jar default via ordinary load order.
      </p>

      <H2 id="the-shape">The shape</H2>
      <pre><code>{`{
  "Enabled": true,
  "SummaryHud": { "Enabled": true, "Position": "top_center", "OffsetY": 72, "TtlMs": 6000 }
}`}</code></pre>
      <table>
        <thead><tr><th>Field</th><th>Default</th><th>What it does</th></tr></thead>
        <tbody>
          <tr><td><code>Enabled</code></td><td><code>true</code></td><td>The engine-wide kill switch. <code>false</code> keeps the mod loaded but disables every station&apos;s work loop server-wide.</td></tr>
          <tr><td><code>SummaryHud.Enabled</code></td><td><code>true</code></td><td>Whether the post-session summary panel shows at all.</td></tr>
          <tr><td><code>SummaryHud.Position</code></td><td><code>top_center</code></td><td>A named HUD-position preset. An unknown or omitted value falls back to the HUD&apos;s own built-in default.</td></tr>
          <tr><td><code>SummaryHud.OffsetY</code></td><td>none</td><td>A pixel vertical offset applied on top of the position preset.</td></tr>
          <tr><td><code>SummaryHud.TtlMs</code></td><td>none</td><td>How long the summary panel stays on screen before it auto-dismisses, in milliseconds.</td></tr>
        </tbody>
      </table>
      <p>
        The two top-level knobs (<code>Enabled</code>, <code>SummaryHud</code>) are independent and
        composable - disabling the summary HUD does not disable the engine, and vice versa. Both leaves are
        nullable, so a partial owner override changes only what it mentions.
      </p>

      <H2 id="the-summary-panel">What the summary panel shows</H2>
      <p>
        The session-summary panel renders after a work session stops: a header naming the station (its own
        icon, resolved from <code>Identity.Icon</code> or the block&apos;s own item id when omitted), the
        session totals, any enhancement outcome rows (durability gained, stats rolled - MMO-agnostic; a bare
        Anvil with no progression mod installed still reports its durability gain), and whatever additional
        ledger rows a progression mod&apos;s own bridge chooses to add. See{' '}
        <Link href="/docs/integrations/">Add-ons &amp; Integrations</Link> for what the MMO Skill Tree bridge
        contributes to this panel when it is installed alongside RPG Stations.
      </p>

      <H2 id="why-an-asset-not-a-config">Why an asset, not a config file</H2>
      <p>
        Treating settings as content rather than configuration means a server owner authors and audits it the
        exact same way they author every other piece of RPG Stations content - through the Pattern-A
        asset-pack pipeline, <code>defaults &lt; pack &lt; owner</code> precedence, and{' '}
        <code>/rpgstations validate</code> - rather than a second, differently-shaped file format to learn.
      </p>
    </DocPage>
  )
}
