import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Stat Channels',
  description: 'The MMO Skill Tree stat-channel id table RPG Stations loot/enhancement formulas can read.',
  openGraph: {
    title: 'Stat Channels | RPG Stations Docs',
    description: 'MMO_* channel ids, units, and the generic Zig_Entity_Stats tag - for use with the stat factor.',
  },
}

export default function StatChannelsPage() {
  return (
    <DocPage
      title="Stat Channels"
      description="Native stat channel ids the stat factor can read - MMO Skill Tree's, and the generic tool-stat tag"
      breadcrumbs={[{ title: 'Stat Channels' }]}
    >
      <p>
        RPG Stations&apos; <code>stat</code> factor (see{' '}
        <Link href="/docs/guides/loot-and-factors/">Loot &amp; Factors</Link>) reads ANY native stat channel
        id off a player&apos;s live entity stat map - RPG Stations itself does not define or own any of these
        ids. This page documents the channel ids the <strong>MMO Skill Tree</strong> mod registers, because
        it is the most common pairing partner and its channel table is otherwise scattered across its own
        source. <strong>Every id below belongs to MMO Skill Tree, not to RPG Stations</strong> - they are
        listed here purely as the reference a station author reaches for when composing a{' '}
        <code>{'{"Factor": "stat", "Param": "<id>"}'}</code> reference. If MMO Skill Tree is not installed,
        none of these ids resolve (the <code>stat</code> factor fails closed to 0, never a throw).
      </p>

      <H2 id="global-channels">Global channels (writer-backed)</H2>
      <p>These 11 channels aggregate every source that contributes to them (gear, mastery, effects, ...):</p>
      <table>
        <thead><tr><th>Channel</th><th>Unit</th></tr></thead>
        <tbody>
          <tr><td><code>MMO_Defense</code></td><td>Damage reduction percent</td></tr>
          <tr><td><code>MMO_CritChance</code></td><td>Percent</td></tr>
          <tr><td><code>MMO_Lifesteal</code></td><td>Percent</td></tr>
          <tr><td><code>MMO_LifestealFlat</code></td><td>Flat HP</td></tr>
          <tr><td><code>MMO_ComboDamage</code></td><td>Percent</td></tr>
          <tr><td><code>MMO_ComboDamageFlat</code></td><td>Flat damage</td></tr>
          <tr><td><code>MMO_FallDamageReduction</code></td><td>Percent</td></tr>
          <tr><td><code>MMO_EffectDuration</code></td><td>Percent</td></tr>
          <tr><td><code>MMO_CooldownReduction</code></td><td>Percent</td></tr>
          <tr><td><code>MMO_Luck</code></td><td>Points (arbitrary loot-formula unit)</td></tr>
          <tr><td><code>MMO_BonusXp</code></td><td>Percent</td></tr>
        </tbody>
      </table>
      <p>
        <code>MMO_CritMultiplier</code> is base-only (a starting multiplier value, not an aggregate-of-many
        writer channel).
      </p>

      <H2 id="school-channels">School channels</H2>
      <p>
        Six damage schools each get a percent and a flat bonus channel:{' '}
        <code>MMO_Bonus_&lt;School&gt;</code> and <code>MMO_BonusFlat_&lt;School&gt;</code> (12 channels
        total).
      </p>

      <H2 id="per-skill-channels">Per-skill channel families (dynamic)</H2>
      <p>
        Every skill (built-in or a pack&apos;s own <code>CustomSkillAsset</code>) gets its OWN set of these
        channels, with <code>&lt;skill&gt;</code> replaced by the skill id (e.g.{' '}
        <code>MMO_Luck_WOODCUTTING</code>, <code>MMO_Damage_SMITHING</code>):
      </p>
      <ul>
        <li><code>MMO_Damage_&lt;skill&gt;</code> / <code>MMO_DamageFlat_&lt;skill&gt;</code></li>
        <li><code>MMO_Luck_&lt;skill&gt;</code></li>
        <li><code>MMO_BonusXp_&lt;skill&gt;</code></li>
        <li><code>MMO_CritChance_&lt;skill&gt;</code> / <code>MMO_CritMultiplier_&lt;skill&gt;</code></li>
        <li><code>MMO_Lifesteal_&lt;skill&gt;</code> / <code>MMO_LifestealFlat_&lt;skill&gt;</code></li>
        <li><code>MMO_ComboDamage_&lt;skill&gt;</code> / <code>MMO_ComboDamageFlat_&lt;skill&gt;</code></li>
        <li><code>MMO_CooldownReduction_&lt;skill&gt;</code></li>
      </ul>

      <H2 id="level-channels">MMO_Level_&lt;skill&gt; and MMO_CombatLevel</H2>
      <p>
        Every skill also mirrors its raw LEVEL onto its own channel, <code>MMO_Level_&lt;skill&gt;</code>{' '}
        (e.g. <code>MMO_Level_SMITHING</code>) - written on every level-up, skill reset, and admin XP set, so
        it is always current. This is what powers the Anvil&apos;s enhancement budget scaling in{' '}
        <Link href="/docs/guides/enhancement-and-stamp/">Enhancement &amp; Stamp</Link>:
      </p>
      <pre><code>{`{ "Factor": "stat", "Param": "MMO_Level_SMITHING" }`}</code></pre>
      <p>
        <code>MMO_CombatLevel</code> is a single derived channel aggregating combat-skill levels into one
        overall combat-level number.
      </p>

      <H2 id="station-luck-aggregate">The station_luck convenience aggregate</H2>
      <p>
        MMO Skill Tree&apos;s station bridge also exposes one convenience factor,{' '}
        <code>mmoskilltree:station_luck</code> - a fixed, documented composition of the MMO&apos;s luck
        channels (equivalent to summing <code>MMO_Luck</code> plus every relevant{' '}
        <code>MMO_Luck_&lt;skill&gt;</code>). See{' '}
        <Link href="/docs/guides/loot-and-factors/#either-or-rule">Loot &amp; Factors: the either-or luck
        rule</Link> - compose the raw <code>stat</code> channels yourself, OR reference this aggregate, never
        both in the same Roll.
      </p>

      <H2 id="zig-entity-stats">The generic Zig_Entity_Stats tag</H2>
      <p>
        Separately from any MMO channel, <strong><code>Zig_Entity_Stats</code></strong> is a generic,
        mod-agnostic native item TAG (not a channel id) any item asset can author, in the form{' '}
        <code>&quot;&lt;StatId&gt;:&lt;amount&gt;&quot;</code> (additive only). It applies an item&apos;s tagged stat
        bonus to the SAME native stat channels this page documents while the item is held - the mechanism
        Hytale tools use to keep contributing to their functional stats (mining power, etc.) with no bespoke
        per-mod tool-stat parser. Because it targets the same native channel space, a tag-stat tool and a
        Stamp-enhanced tool compose additively with zero double-count risk.
      </p>

      <H2 id="any-native-stat-works">Any native stat works</H2>
      <p>
        This table is not exhaustive by design - it documents MMO Skill Tree&apos;s ids because that is the
        common pairing, but the <code>stat</code> factor itself has no allowlist. Any mod that writes ANY
        native stat channel participates in RPG Stations&apos; loot and enhancement formulas immediately, with
        zero RPG Stations code change: author a <code>{'{Factor: "stat", Param: "<the channel id>"}'}</code>{' '}
        reference anywhere a <code>FactorRef</code> is accepted.
      </p>
    </DocPage>
  )
}
