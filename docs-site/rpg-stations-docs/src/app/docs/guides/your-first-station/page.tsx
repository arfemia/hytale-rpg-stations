import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Your First Station',
  description: 'A worked walkthrough authoring one RPG Stations station end to end.',
  openGraph: {
    title: 'Your First Station | RPG Stations Docs',
    description: 'Author a StationAsset, its block, its interaction, its lang keys, and validate it.',
  },
}

export default function YourFirstStationPage() {
  return (
    <DocPage
      title="Your First Station"
      description="Author one station end to end: the asset, the block, the interaction, the lang keys"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Your First Station' }]}
      prevPage={{ title: 'Concepts', href: '/docs/concepts/' }}
      nextPage={{ title: 'Actions & Step Programs', href: '/docs/guides/actions-and-steps/' }}
    >
      <p>
        A station is four pieces working together: a <code>StationAsset</code> JSON, a block item, a{' '}
        <code>RootInteraction</code> pointing the block&apos;s <code>Use</code> at RPG Stations&apos; one
        registered interaction type, and a pair of localization keys. This walkthrough authors a simplified
        Sawmill-shaped station - the shipped jar default is a good reference to compare against once you are
        done.
      </p>

      <H2 id="step-1-the-station-asset">1. The StationAsset</H2>
      <p>
        Drop a file at <code>Server/RpgStations/Stations/&lt;Name&gt;.json</code>. The filename, lowercased,
        becomes the station id every other reference (the block&apos;s interaction, an <code>Extension</code>,
        an anchor declaration) uses.
      </p>
      <pre><code>{`{
  "Identity": {
    "NameKey": "rpgstations.station.sawmill.name",
    "DescKey": "rpgstations.station.sawmill.desc"
  },
  "Work": {
    "CycleMs": 4665,
    "MaxDurationMs": 600000,
    "Exclusive": true,
    "Xp": [ { "Skill": "WOODCUTTING", "PerCycle": 8.0 } ]
  },
  "Recipe": {
    "FromCrafting": { "Categories": ["WoodPlanks"] }
  },
  "Custody": {
    "MaxQuantity": 100,
    "States": { "Empty": "Default", "Loaded": "Loaded" }
  },
  "Hold": {
    "MovementLock": true,
    "EffectId": "RPG_Station_Hold",
    "InterruptOnDamage": true
  },
  "Tool": {
    "Gather": { "GatherType": "Woods", "MinPower": 0.1 },
    "Tags": { "Family": ["Hatchet"] }
  },
  "Animation": { "EmoteId": "RPG_Emote_Saw" },
  "Presentation": { "Sound": "SFX_Wood_Break" }
}`}</code></pre>
      <p>
        Every top-level group is optional and nullable - omit <code>Loot</code>, <code>Puppet</code>,{' '}
        <code>Camera</code>, and <code>Actions</code> entirely for the simplest possible station. The example
        above:
      </p>
      <ul>
        <li><code>Work.Xp</code> is a bare progression <em>declaration</em> - RPG Stations never interprets it itself, it just forwards the ask on the cycle-completed event for whatever progression mod (if any) is listening.</li>
        <li><code>Recipe.FromCrafting</code> derives conversions from every native crafting recipe in the <code>WoodPlanks</code> category, instead of hand-listing every wood species. See <Link href="/docs/guides/native-composition/">Native Composition</Link>.</li>
        <li><code>Custody</code> opts this station into placed-input material loading (press <code>F</code> holding logs to place them, then press again to start working the pile). See <Link href="/docs/guides/custody-and-placed-display/">Custody &amp; Placed Display</Link>.</li>
        <li><code>Tool</code> requires holding a hatchet-family/Woods-gathering tool to start or keep working.</li>
      </ul>

      <H3 id="native-parent-reuse">Reuse with native Parent</H3>
      <p>
        A variant station can inherit from another by authoring <code>&quot;Parent&quot;: &quot;Sawmill&quot;</code> and
        overriding only the leaves it wants to change - every leaf in every group is registered for native
        inherit-on-omit, so a partial override never silently drops a sibling leaf you did not mention.
      </p>

      <H2 id="step-2-the-block">2. The block</H2>
      <p>
        The station&apos;s block is an ordinary Hytale item/block asset. Reuse a vanilla model where possible
        (the shipped Sawmill reuses the Lumbermill model, the Anvil reuses the vanilla Anvil bench model - no
        new art needed). Point its <code>BlockType.Interactions.Use</code> at a{' '}
        <code>RootInteraction</code> id you author next. If the station uses <code>Custody</code>, give the
        block a matching pair of interaction states so the engine has something to flip between:
      </p>
      <pre><code>{`{ "State": { "Definitions": {
  "Default": { "InteractionHint": "items.RPG_Station_MyBlock.hint.empty" },
  "Loaded":  { "InteractionHint": "items.RPG_Station_MyBlock.hint.loaded" }
} } }`}</code></pre>
      <p>The state names here (<code>Default</code>/<code>Loaded</code>) are just a convention - they only have to match whatever your <code>StationAsset.Custody.States.Empty</code>/<code>.Loaded</code> name.</p>

      <H2 id="step-3-the-interaction">3. The RootInteraction</H2>
      <p>
        RPG Stations registers exactly ONE Java interaction type, <code>rpg_station_use</code>, that backs
        every station block in every installed pack. One interaction JSON per block, in the{' '}
        <strong>object form</strong> so the same Java class serves any number of stations with zero extra
        code:
      </p>
      <pre><code>{`{
  "Cooldown": { "Id": "BlockInteraction", "Cooldown": 0.278, "ClickBypass": true },
  "Interactions": [ { "Type": "rpg_station_use", "Station": "sawmill" } ]
}`}</code></pre>
      <p>
        The <code>Station</code> field is the id your <code>StationAsset</code> file decodes to (the
        lowercased filename). Pressing <code>F</code> on the block fires this interaction, which calls the
        engine&apos;s <code>toggle</code> to start or stop the pressing player&apos;s session.
      </p>

      <H2 id="step-4-lang">4. Localization keys</H2>
      <p>
        <code>Identity.NameKey</code>/<code>DescKey</code> above point at two keys in your own{' '}
        <code>Server/Languages/&lt;bcp47&gt;/rpgstations.lang</code> overlay (RPG Stations reads this
        additively over its own shipped file, so a pack never needs to duplicate the jar&apos;s keys):
      </p>
      <pre><code>{`station.sawmill.name = Sawmill
station.sawmill.desc = Saw logs from your inventory into planks, one cycle at a time.`}</code></pre>
      <p>
        The block&apos;s own name/description/interaction hint live in the NATIVE{' '}
        <code>items.lang</code> namespace instead - see{' '}
        <Link href="/docs/guides/localization/">Localization</Link> for the full three-family picture.
      </p>

      <H2 id="step-5-validate">5. Validate</H2>
      <p>
        Run <code>/rpgstations validate</code> in-game or from console after a restart. It runs the full
        content audit over every folded station/lootable/action/extension and chats a summary line plus every
        finding (color-coded ERROR/WARNING/INFO) - the exact audit RPG Stations already runs once at boot.
        Every finding is warn-only by design (a content mistake never blocks the server from starting); read
        the finding codes to catch typos and missing references before a player does.
      </p>
      <p>
        That is a complete station. From here, <Link href="/docs/guides/actions-and-steps/">Actions &amp; Step
        Programs</Link> covers giving one station multiple actions and authoring a step-by-step ritual instead
        of the classic convert loop.
      </p>
    </DocPage>
  )
}
