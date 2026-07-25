import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Localization',
  description: 'The three lang families RPG Stations content resolves display text through.',
  openGraph: {
    title: 'Localization | RPG Stations Docs',
    description: 'rpgstations.lang, the native item/emote namespaces, and mmoskilltree.lang for CustomSkillAsset.',
  },
}

export default function LocalizationPage() {
  return (
    <DocPage
      title="Localization"
      description="Three lang families cover every piece of display text RPG Stations content can author"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Localization' }]}
      prevPage={{ title: 'Settings', href: '/docs/guides/settings/' }}
    >
      <p>
        Every piece of display text in RPG Stations content is resolved client-side through a localization
        key - never a raw hardcoded string. Which <code>.lang</code> file a key lives in depends on WHOSE
        namespace the content belongs to: RPG Stations&apos; own generic UI/content strings, a native Hytale
        asset&apos;s own name/description, or (only when pairing with the MMO Skill Tree bridge) an MMO-side
        skill&apos;s display text.
      </p>

      <H2 id="family-1-rpgstations-lang">1. rpgstations.lang - RPG Stations&apos; own namespace</H2>
      <p>
        RPG Stations&apos; generic UI strings and per-station/per-action content keys live in{' '}
        <code>Server/Languages/&lt;bcp47&gt;/rpgstations.lang</code>. Java code resolves them through a
        prefix-free facade that automatically prepends <code>rpgstations.</code>, so a content key authored
        as <code>station.sawmill.name</code> is referenced from JSON as{' '}
        <code>rpgstations.station.sawmill.name</code>:
      </p>
      <pre><code>{`ui.station.locked = This station is not available right now.
ui.station.occupied = Someone is already using this station.
ui.station.no_action = You have nothing this station can work with.
station.sawmill.name = Sawmill
station.sawmill.desc = Saw logs from your inventory into planks, one cycle at a time.
action.prepfish.label = Prepare Fish`}</code></pre>
      <p>
        A pack that ships its own station reuses this same file convention: author your own additive{' '}
        <code>rpgstations.lang</code> overlay carrying just your new keys (RPG Stations reads the jar&apos;s
        shipped file and every pack&apos;s overlay together, additively) - never duplicate the jar&apos;s
        existing keys. This is the family every <code>Identity.NameKey</code>/<code>DescKey</code>,{' '}
        <code>ActionDef.Label</code>, and every <code>ui.*</code> UI string resolves through.
      </p>

      <H2 id="family-2-native">2. Native namespaces - a block&apos;s own item/emote text</H2>
      <p>
        A station&apos;s BLOCK is an ordinary native Hytale item, and its display text lives in Hytale&apos;s
        OWN native lang namespaces, not RPG Stations&apos; own:
      </p>
      <ul>
        <li><code>items.lang</code> - the block&apos;s name, description, and interaction hint (including the separate empty-vs-loaded hint text for a custody-governed block).</li>
        <li><code>avatarCustomization.lang</code> - a work emote&apos;s own display name, if the station&apos;s emote is server-authored rather than a native built-in.</li>
      </ul>
      <pre><code>{`RPG_Station_Sawmill.name = Sawmill
RPG_Station_Sawmill.description = A sawmill for turning logs into planks.
RPG_Station_Sawmill.hint.empty = Press [{key}] to place logs
RPG_Station_Sawmill.hint.loaded = Press [{key}] to work`}</code></pre>
      <p>
        These keys belong to the BLOCK asset, not to RPG Stations&apos; own vocabulary - they follow whatever
        naming convention native item/emote assets already use, and they are not covered by RPG Stations&apos;
        own lang-key-presence audit (only <code>rpgstations.lang</code> keys are).
      </p>

      <H2 id="family-3-mmoskilltree">3. mmoskilltree.lang - only for a CustomSkillAsset (MMO-side)</H2>
      <p>
        When a pack pairs RPG Stations content with an MMO Skill Tree skill (adding a{' '}
        <code>Server/MMOSkillTree/CustomSkills/&lt;Skill&gt;.json</code> asset - see{' '}
        <Link href="/docs/custom-skill-asset/">CustomSkillAsset</Link>), that skill&apos;s roster display
        name and description resolve through the MMO&apos;s OWN convention key,{' '}
        <code>skill.&lt;id&gt;</code> / <code>skill.&lt;id&gt;.desc</code>, in that pack&apos;s{' '}
        <code>Server/Languages/&lt;bcp47&gt;/mmoskilltree.lang</code> - a THIRD namespace, entirely separate
        from RPG Stations&apos; own <code>rpgstations.lang</code>. A <code>CustomSkillAsset</code>&apos;s{' '}
        <code>Display.Name</code>/<code>Description</code> fields are only the raw LAST-RESORT fallback if the
        lang key is missing, never the authored display text itself.
      </p>

      <H2 id="why-three-families">Why three families, not one</H2>
      <p>
        Each family belongs to the system that owns the content it describes: RPG Stations owns its own
        generic UI vocabulary and per-station convention keys; the native engine owns every item/emote asset&apos;s
        text regardless of which mod placed that asset; and the MMO Skill Tree owns its skill roster&apos;s
        text regardless of which mod&apos;s work loop feeds that skill XP. Keeping them separate means a pack
        never has to guess which file a given piece of text belongs in - the OWNER of the asset the text
        describes is always the answer.
      </p>
    </DocPage>
  )
}
