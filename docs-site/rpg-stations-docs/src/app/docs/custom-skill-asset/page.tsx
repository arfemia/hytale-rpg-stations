import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'CustomSkillAsset',
  description: 'The MMO Skill Tree custom-skill asset a station pack authors when it pairs work with a new skill.',
  openGraph: {
    title: 'CustomSkillAsset | RPG Stations Docs',
    description: 'How a pack adds a whole new MMO Skill Tree skill (Smithing) alongside its RPG Stations content.',
  },
}

export default function CustomSkillAssetPage() {
  return (
    <DocPage
      title="CustomSkillAsset"
      description="How a pack adds a new MMO Skill Tree skill alongside its station content"
      breadcrumbs={[{ title: 'CustomSkillAsset' }]}
    >
      <p>
        <code>CustomSkillAsset</code> is an <strong>MMO Skill Tree</strong> asset type, not an RPG Stations
        one - it belongs to the MMO&apos;s own asset space and only exists when MMO Skill Tree is installed.
        It is documented here because it is the natural companion to a station pack: when a pack pairs its
        work loop with a brand-new skill (the Anvil pack&apos;s <code>SMITHING</code>, say), the skill itself
        is a <code>CustomSkillAsset</code> the same pack ships. RPG Stations forwards the XP asks; MMO Skill
        Tree owns the skill.
      </p>
      <p>
        A pack authors it at{' '}
        <code>Server/MMOSkillTree/CustomSkills/&lt;Skill&gt;.json</code>. The asset id is the filename, and
        the skill id is that id UPPERCASED - <code>Smithing.json</code> becomes the skill{' '}
        <code>SMITHING</code>. It merges into the MMO&apos;s skill registry under the pack layer, with
        precedence <code>defaults &lt; pack &lt; owner</code> (a server owner&apos;s own{' '}
        <code>custom-skills.json</code> still overrides per id).
      </p>

      <H2 id="shape">Shape</H2>
      <p>
        It is a structured Pattern-A asset with nested groups and nullable leaves, so a partial override
        changes only the keys it names:
      </p>
      <table>
        <thead>
          <tr><th>Field</th><th>Meaning</th></tr>
        </thead>
        <tbody>
          <tr><td><code>Display.Name</code></td><td>Roster display name - the raw LAST-RESORT fallback only; the lang key wins (see below).</td></tr>
          <tr><td><code>Display.Description</code></td><td>Roster description - also a raw fallback.</td></tr>
          <tr><td><code>Display.DescriptionKey</code></td><td>Explicit i18n key override for the description.</td></tr>
          <tr><td><code>Display.Icon</code></td><td>Icon texture path.</td></tr>
          <tr><td><code>Placement.Category</code></td><td>The <code>SkillCategory</code> the skill sits under on the roster (falls back to MISC).</td></tr>
          <tr><td><code>Placement.InsertAfter</code></td><td>The skill id to interleave this one after, for roster ordering.</td></tr>
          <tr><td><code>Triggers</code></td><td>Trigger type names; an unrecognized name warns and is skipped.</td></tr>
          <tr><td><code>RequiresFeatures</code></td><td>Server-feature ids that gate the skill, the same as quest / achievement feature gates.</td></tr>
          <tr><td><code>MaxLevel</code></td><td>Optional per-skill level cap (the owner override still wins over it).</td></tr>
        </tbody>
      </table>
      <p>
        There is deliberately <strong>no</strong> <code>NameKey</code> field: the roster name resolves from
        the MMO&apos;s <code>skill.&lt;id&gt;</code> convention key first, so <code>Display.Name</code> is
        only the fallback when that key is missing. See{' '}
        <Link href="/docs/guides/localization/#family-3-mmoskilltree">Localization</Link> for the{' '}
        <code>mmoskilltree.lang</code> family a pack uses to translate the skill&apos;s name and description.
      </p>

      <H2 id="example">Example</H2>
      <pre><code>{`{
  "Display": {
    "Name": "Smithing",
    "Description": "Forge weapons and armor to gain experience",
    "DescriptionKey": null,
    "Icon": "Icons/ItemsGenerated/Ingredient_Bar_Iron.png"
  },
  "Placement": { "Category": "CRAFTING", "InsertAfter": "ENCHANTING" },
  "Triggers": ["CRAFT_ITEM"],
  "RequiresFeatures": ["stations"],
  "MaxLevel": null
}`}</code></pre>
      <p>
        Once the skill exists, a station&apos;s own <code>Work.Xp</code> declaration names it, and every
        per-skill stat channel (<code>MMO_Level_SMITHING</code>, <code>MMO_Luck_SMITHING</code>, and the
        rest) becomes available to loot and enhancement formulas automatically - see{' '}
        <Link href="/docs/stat-channels/#per-skill-channels">Stat Channels</Link>.
      </p>
    </DocPage>
  )
}
