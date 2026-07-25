import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Loot & Factors',
  description: 'The weighted conditional-loot vocabulary, the stat factor provider, and the either-or luck rule.',
  openGraph: {
    title: 'Loot & Factors | RPG Stations Docs',
    description: 'Roll, Chance, Ladder, Grants, FactorRef, and how loot reads any native stat.',
  },
}

export default function LootAndFactorsPage() {
  return (
    <DocPage
      title="Loot & Factors"
      description="The conditional-loot vocabulary, and the weighted factor system it rides on"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Loot & Factors' }]}
      prevPage={{ title: 'Selection & Output Categories', href: '/docs/guides/selection/' }}
      nextPage={{ title: 'Native Composition', href: '/docs/guides/native-composition/' }}
    >
      <p>
        Every conditional-loot site in RPG Stations - a station&apos;s own <code>Loot</code> group, an
        action&apos;s override, a step&apos;s <code>Roll</code> phase, an extension&apos;s appended loot -
        uses the SAME two building blocks: a <strong>LootRef</strong> (a bag of referenced lootable tables plus
        inline rolls) and a <strong>Roll</strong> (one gate-and-payoff unit). Both feed off the SAME weighted
        <strong>factor</strong> vocabulary that also drives step iteration counts and enhancement caps.
      </p>

      <H2 id="lootref">LootRef: tables and inline rolls</H2>
      <pre><code>{`"Loot": { "Lootables": ["sawmillfinds"], "Rolls": [ /* inline Roll entries */ ] }`}</code></pre>
      <p>
        <code>Lootables</code> references one or more standalone{' '}
        <code>Server/RpgStations/Lootables/&lt;Name&gt;.json</code> files (a reusable table of rolls, shareable
        across stations); <code>Rolls</code> authors rolls directly at this site. Both resolve together when
        both are present - a station&apos;s own inline rolls run alongside every referenced table&apos;s
        rolls.
      </p>

      <H2 id="roll">Roll: gate + payoff</H2>
      <pre><code>{`{
  "Trigger": "Cycle",
  "Conditions": [ { "Factor": "rpgstations:cycle_count", "Min": 3 } ],
  "Chance":    { "BasePercent": 2, "AddFactors": [ { "Factor": "rpgstations:tool_power" } ], "CapPercent": 25 },
  "Ladder":    { "Values": [ { "Factor": "stat", "Param": "MMO_Luck" },
                              { "Factor": "stat", "Param": "MMO_Luck_WOODCUTTING" } ],
                 "Floors": [ { "Min": 50,  "Grants": { "DropList": "SawmillFinds_T1" } },
                             { "Min": 100, "Grants": { "DropList": "SawmillFinds_T2" },
                               "Presentation": { "Sound": "SFX_Coins_Land" } } ] },
  "Grants":    { "BonusOutputCopies": 1 }
}`}</code></pre>
      <ul>
        <li><code>Trigger</code> - <code>Cycle</code> (default, once per completed work cycle) or <code>Completion</code> (once, at session stop).</li>
        <li><code>Conditions</code> - a hard gate; every entry must pass a bounded factor check before the roll is even considered.</li>
        <li><code>Chance</code> - a probabilistic gate over the WHOLE roll (Ladder included): <code>clamp(BasePercent + sum(resolve(factor) * Weight), 0, CapPercent)</code>, rolled once against a uniform 0-100 sample. A failing Chance means nothing fires, and the Ladder is never even evaluated.</li>
        <li><code>Ladder</code> - an UNCAPPED, summed factor value looked up against a floor list; the HIGHEST reached floor&apos;s own <code>Grants</code> fires (a floor above a factor&apos;s &quot;normal&quot; range stays reachable via a multi-source stack).</li>
        <li><code>Grants</code> - the reward vocabulary, below. Top-level <code>Grants</code> AND the reached floor&apos;s own <code>Grants</code> both apply when a Ladder is present.</li>
      </ul>

      <H2 id="grants">Grants: the reward vocabulary</H2>
      <table>
        <thead><tr><th>Field</th><th>What it grants</th></tr></thead>
        <tbody>
          <tr><td><code>BonusOutputCopies</code></td><td>N extra copies of THIS cycle&apos;s own output. Only meaningful on a <code>Cycle</code> trigger (the validator warns otherwise); silently skipped if the inventory is full.</td></tr>
          <tr><td><code>DropList</code></td><td>A native <code>ItemDropList</code> asset id, rolled through the engine&apos;s own drop-list roller. See <Link href="/docs/guides/native-composition/">Native Composition</Link>.</td></tr>
          <tr><td><code>Commands</code></td><td>Console commands, with <code>{'{player}'}</code>/<code>{'{uuid}'}</code>/<code>{'{station}'}</code>/<code>{'{action}'}</code>/<code>{'{cycles}'}</code> placeholders substituted.</td></tr>
          <tr><td><code>Effects</code></td><td>Native EntityEffects applied to the player, id-ref-only. See <Link href="/docs/guides/native-composition/">Native Composition</Link>.</td></tr>
        </tbody>
      </table>

      <H2 id="factorref">FactorRef: the one weighted-sum leaf</H2>
      <p>
        Every place a numeric factor is SUMMED - a Chance&apos;s <code>AddFactors</code>, a Ladder&apos;s{' '}
        <code>Values</code>, a step&apos;s <code>Repeat.AddFactors</code>, an enhancement budget&apos;s{' '}
        <code>Factors</code> - takes an array of <code>FactorRef</code>:
      </p>
      <pre><code>{`{ "Factor": "stat", "Param": "MMO_Luck", "Weight": 1.0 }`}</code></pre>
      <p>
        Composition is ALWAYS a flat weighted sum, <code>sum(resolve(Factor, Param) * Weight)</code> - there
        is deliberately no expression-tree DSL. <code>Weight</code> defaults to 1.0. An unregistered factor id
        resolves to 0 (fail-closed) rather than throwing, so a Roll referencing a factor from an uninstalled
        progression mod just quietly contributes nothing.
      </p>
      <p>
        <code>Condition</code> is the sibling GATE shape (<code>{'{Factor, Param?, Min?, Max?}'}</code>) used
        anywhere a factor value must pass a bound instead of being summed - a <code>Requires</code> gate, a{' '}
        <code>Roll.Conditions</code> entry, a <code>StationStep.Conditions</code> entry.
      </p>

      <H2 id="the-stat-factor">The stat factor: any native stat works</H2>
      <p>
        RPG Stations registers one built-in, mod-agnostic factor id, <strong><code>stat</code></strong>. Its{' '}
        <code>Param</code> is any native stat channel id, read straight off the player&apos;s live entity stat
        map - the same modifier-target read the engine itself already performs:
      </p>
      <pre><code>{`{ "Factor": "stat", "Param": "MMO_Luck" }`}</code></pre>
      <p>
        Because this reads the NATIVE stat substrate directly, <strong>any mod that writes a native stat
        participates in loot formulas with zero bridge code</strong> - RPG Stations never needs to know MMO
        Skill Tree (or any other progression mod) exists. See{' '}
        <Link href="/docs/stat-channels/">Stat Channels</Link> for the full MMO channel-id table this composes
        with, and the generic <code>Zig_Entity_Stats</code> tag any item can carry.
      </p>

      <H2 id="either-or-rule">The either-or luck rule</H2>
      <p>
        When the MMO Skill Tree bridge is installed, it exposes one convenience AGGREGATE factor,{' '}
        <code>mmoskilltree:station_luck</code>, which is defined as the exact same weighted composition of the
        MMO&apos;s own luck channels a hand-authored formula would build with <code>stat</code> references
        directly (e.g. <code>MMO_Luck</code> + <code>MMO_Luck_&lt;skill&gt;</code>). The rule is a hard
        either-or per formula: <strong>compose <code>stat</code> channels yourself, OR reference the
        aggregate - never both in the same Roll.</strong> Referencing both double-counts the same luck twice.
        A validator check flags a Roll that mixes the aggregate with any <code>stat</code>/<code>MMO_Luck*</code>{' '}
        factor as a best-effort guard, but the contract is ultimately an authoring discipline these docs state
        plainly.
      </p>
      <p>
        The pack-side Sawmill luck ladder is the composition exemplar - it sums two weighted <code>stat</code>{' '}
        channels directly rather than using the aggregate:
      </p>
      <pre><code>{`"Ladder": { "Values": [
  { "Factor": "stat", "Param": "MMO_Luck", "Weight": 1.0 },
  { "Factor": "stat", "Param": "MMO_Luck_WOODCUTTING", "Weight": 1.0 }
], "Floors": [ /* ... */ ] }`}</code></pre>

      <H2 id="rpgstations-built-ins">RPG Stations&apos; own built-in factors</H2>
      <p>
        Independent of any progression mod, RPG Stations registers its own session-scoped factors so its
        jar-shipped standalone content stays fully rewarding with nothing else installed:{' '}
        <code>rpgstations:tool_power</code> (the currently held tool&apos;s power) and{' '}
        <code>rpgstations:cycle_count</code> (cycles completed so far this session) are the two the shipped
        Sawmill lootable rolls against.
      </p>
    </DocPage>
  )
}
