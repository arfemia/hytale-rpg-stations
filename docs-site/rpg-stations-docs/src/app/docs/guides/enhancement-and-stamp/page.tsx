import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Enhancement & Stamp',
  description: 'The Stamp step: reagents, durability, composable stat rolls, and the Budgets cap model.',
  openGraph: {
    title: 'Enhancement & Stamp | RPG Stations Docs',
    description: 'Roll stats onto a placed item with the composable Stamp step.',
  },
}

export default function EnhancementAndStampPage() {
  return (
    <DocPage
      title="Enhancement & Stamp"
      description="Roll stats onto a placed item: reagents, durability, and the composable cap model"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Enhancement & Stamp' }]}
      prevPage={{ title: 'Native Composition', href: '/docs/guides/native-composition/' }}
      nextPage={{ title: 'Flairs', href: '/docs/guides/flairs/' }}
    >
      <p>
        <code>Stamp</code> is a step phase that rolls stats onto a placed item, consumes reagents, and
        optionally raises durability - the mechanism behind the Anvil&apos;s Enhance ritual. It runs as ONE
        transaction: every roll and every check is validated with zero mutation first, and only committed once
        everything is known to succeed (compute-then-commit) - a Stamp step never leaves an item or an
        inventory half-changed.
      </p>

      <H2 id="the-shape">The shape</H2>
      <pre><code>{`"Stamp": {
  "Reagents": [ { "ResourceTypeId": "Metal_Bars", "Quantity": 2 } ],
  "Durability": { "AddMax": 10 },
  "Stats": {
    "Pool": "anvilweaponpool",
    "Picks": { "Min": 1, "Max": 2 },
    "Unique": true,
    "Caps": {
      "Budgets": [
        { "Points": 30 },
        { "PointsPer": 0.5, "Factors": [ { "Factor": "yourmod:skill_level", "Param": "SMITHING" } ] }
      ],
      "PerStat": { "YourMod_CritChance": 10 }
    }
  }
}`}</code></pre>
      <ul>
        <li><code>Reagents</code> - <code>Ingredient</code>s consumed from the player&apos;s INVENTORY at commit (never from a custody claim - the placed item being enhanced is what lives in custody).</li>
        <li><code>Durability.AddMax</code> - raises the placed stack&apos;s max durability (and its current durability by the same amount) - a genuine upgrade, real with no other mod installed at all.</li>
        <li><code>Stats</code> - the composable stat-roll model, below.</li>
      </ul>

      <H2 id="stat-roll-entries">Stat roll entries</H2>
      <p>
        A candidate roll is a <code>StatRollEntry</code>: <code>{'{Stat, Points{Min,Max,AddFactors?}, Weight, Always}'}</code>.
        <code>Stat</code> is opaque to RPG Stations itself - the registered stamper interprets what a &quot;stat&quot;
        id means. The rolled point value is{' '}
        <code>uniform(Min, Max)</code>, optionally plus a weighted <code>AddFactors</code> sum (the same{' '}
        <Link href="/docs/guides/loot-and-factors/">FactorRef</Link> vocabulary loot chances use). An entry
        with <code>Always: true</code> is granted unconditionally on every stamp, independent of the weighted
        pool - so one Stamp can mix a guaranteed baseline stat with weighted bonus rolls.
      </p>
      <p>
        Entries come from EITHER a reusable, shared <code>Server/RpgStations/RollPools/&lt;Name&gt;.json</code>{' '}
        table (referenced via <code>Stats.Pool</code>) OR inline <code>Stats.Entries</code>, or both at once -
        both authoring routes share the exact same entry shape, so an engine change never has to special-case
        which route produced an entry.
      </p>
      <pre><code>{`// RollPools/AnvilWeaponPool.json
{ "Entries": [
  { "Stat": "YourMod_CritChance",      "Points": { "Min": 2, "Max": 6 },  "Weight": 1 },
  { "Stat": "YourMod_CritMultiplier",  "Points": { "Min": 5, "Max": 15 }, "Weight": 1 },
  { "Stat": "YourMod_Lifesteal",       "Points": { "Min": 2, "Max": 5 },  "Weight": 1 },
  { "Stat": "YourMod_CooldownReduction","Points": { "Min": 2, "Max": 6 }, "Weight": 1 },
  { "Stat": "YourMod_Luck",            "Points": { "Min": 3, "Max": 10 }, "Weight": 1 },
  { "Stat": "Mana",                    "Points": { "Min": 5, "Max": 15 }, "Weight": 1 }
] }`}</code></pre>

      <H2 id="picks-and-unique">Picks and Unique</H2>
      <p>
        <code>Picks: {'{Min, Max}'}</code> controls how many weighted-pool entries a single Stamp attempt
        picks (<code>Always</code> entries are extra, not counted toward this range).{' '}
        <code>Unique: true</code> means a stat id is never picked twice in the same stamp.
      </p>

      <H2 id="caps">Caps: the Budgets model</H2>
      <p>
        Rolled points are clamped by <code>Caps</code>, which composes three independent, orthogonal
        controls:
      </p>
      <table>
        <thead><tr><th>Field</th><th>What it does</th></tr></thead>
        <tbody>
          <tr><td><code>Budgets</code></td><td>A list of total-point-budget entries. Each entry is EITHER a flat <code>{'{Points}'}</code> OR a factor-scaled <code>{'{PointsPer, Factors}'}</code> (effective = <code>PointsPer * sum(resolve(f) * f.Weight)</code>) - never both on the same entry. <strong>The EFFECTIVE total budget is the MINIMUM across every authored Budget entry.</strong></td></tr>
          <tr><td><code>PerStat</code></td><td>A per-stat-id ceiling layered ON TOP of the total budget (<code>{'{"YourMod_CritChance": 10}'}</code> caps that one stat at 10 points regardless of the total budget available).</td></tr>
          <tr><td><code>Economics</code></td><td><code>{'{RepeatCostMultiplier}'}</code> scales REAGENT cost per prior stamp count on the same item (<code>ceil(baseQuantity * (1 + mult * priorStampCount))</code>) - this affects cost only, never the point budget.</td></tr>
        </tbody>
      </table>
      <p>
        The Anvil&apos;s two <code>Budgets</code> entries compose the flat-plus-scaled pattern directly: a
        flat 30-point ceiling, AND a budget scaled off a registered factor (<code>0.5</code> points per unit
        of whatever <code>yourmod:skill_level</code> resolves to). The minimum of the two wins - so while the
        factor is low the scaled budget is the binding constraint, and past a certain point the flat 30-point
        ceiling takes over. This is the same either-of-flat-or-scaled shape a factor-based budget always
        follows: author
        multiple <code>Budgets</code> entries to express &quot;never above X, but also never above Y that scales
        with something&quot;.
      </p>

      <H2 id="compute-then-commit">Compute-then-commit</H2>
      <p>
        A Stamp step validates the ENTIRE outcome (the roll, the cap clamp, reagent availability, and room
        for the enhanced item to return) with zero mutation. Only once every check passes does it commit:
        reagents are consumed and the item is mutated, each under its own guard that restores exactly what was
        consumed if anything fails partway. This means a Stamp attempt either fully succeeds or leaves the
        player&apos;s reagents and the placed item completely untouched - never a half-consumed, half-rolled
        state.
      </p>
      <p>
        A Stamp step is typically the last step in a non-repeating ritual (<code>Work.Repeat: false</code>,
        see <Link href="/docs/guides/actions-and-steps/">Actions &amp; Step Programs</Link>), followed by a{' '}
        <code>Duration</code> hold so the flourish plays out before the session completes - see the Anvil
        exemplar on that page.
      </p>
    </DocPage>
  )
}
