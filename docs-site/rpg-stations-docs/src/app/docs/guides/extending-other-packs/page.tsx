import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Extending Other Packs',
  description: 'ExtensionAsset: add to another pack\'s content additively, without owning or replacing its files.',
  openGraph: {
    title: 'Extending Other Packs | RPG Stations Docs',
    description: 'The one additive-extension mechanism for stations, actions, lootables, and roll pools.',
  },
}

export default function ExtendingOtherPacksPage() {
  return (
    <DocPage
      title="Extending Other Packs"
      description="Add to another pack's content additively, without owning or replacing its files"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Extending Other Packs' }]}
      prevPage={{ title: 'Flairs', href: '/docs/guides/flairs/' }}
      nextPage={{ title: 'Settings', href: '/docs/guides/settings/' }}
    >
      <p>
        A fourth-party pack often wants to ADD something to a station or action a different, third-party pack
        ships - a contribution on a new channel, an extra loot table, a bonus ritual step - without owning that
        station&apos;s file (which would mean re-shipping the whole thing and risking drift) or replacing it
        wholesale (which would silently discard whatever the original author changes later).{' '}
        <strong>ExtensionAsset</strong> is the one mechanism for exactly this: additive-only composition onto
        content you did not author.
      </p>

      <H2 id="the-shape">The shape</H2>
      <p>
        An extension lives at <code>Server/RpgStations/Extensions/&lt;Name&gt;.json</code> (Pattern A, id =
        lowercased filename). It names EXACTLY ONE target, and carries only the payload groups that target
        type accepts:
      </p>
      <pre><code>{`{
  "Target": { "Station": "sawmill" },
  "Priority": 0,
  "Loot": { "Lootables": ["SawmillLuckTiers"] }
}`}</code></pre>
      <p><code>Target</code> is an exactly-one-of leaf, never a discriminator-plus-id pair:</p>
      <table>
        <thead><tr><th>Target</th><th>Payload keys it may carry</th></tr></thead>
        <tbody>
          <tr><td><code>{'{Station: "<id>"}'}</code></td><td><code>PerCycleContributions</code>, <code>Loot</code>, <code>Actions</code> (new keys only), <code>Conversions</code>, <code>Steps</code>, <code>Anchors</code> (new keys only), <code>Puppet</code>, <code>Custody</code></td></tr>
          <tr><td><code>{'{Action: "<id>"}'}</code></td><td><code>Steps</code>, <code>Anchors</code> (new keys only), <code>Loot</code>, <code>Conversions</code>, <code>PerCycleContributions</code>, <code>Puppet</code>, <code>Custody</code></td></tr>
          <tr><td><code>{'{Lootable: "<id>"}'}</code></td><td><code>Rolls</code> (appended)</td></tr>
          <tr><td><code>{'{RollPool: "<id>"}'}</code></td><td><code>Entries</code> (appended)</td></tr>
        </tbody>
      </table>
      <p>Authoring a payload key the target type does not accept is a content-audit finding, never a silent no-op left undocumented.</p>

      <H2 id="merge-rules">Merge and conflict rules</H2>
      <ol>
        <li><strong>Additive only.</strong> An extension never mutates, replaces, or removes anything the base already authored - replacing a whole file stays a load-order concern, not this mechanism&apos;s job.</li>
        <li><strong>Keyed collections</strong> (<code>Actions</code>, <code>Anchors</code>) - the BASE always wins a key collision; a new key is folded in, and among several extensions adding new keys, apply order (below) decides.</li>
        <li><strong>Unkeyed arrays</strong> (<code>PerCycleContributions</code>, <code>Conversions</code>, <code>Rolls</code>, <code>Entries</code>) - pure append, in apply order.</li>
        <li>
          <strong>Ordered step insertion</strong> (<code>Steps</code>) - each insertion names an <code>Action</code> (which action&apos;s program to insert into) and an <code>Anchor</code>, exactly one of{' '}
          <code>{'{After: "<stepId>"} | {Before: "<stepId>"} | {AtStart: true} | {AtEnd: true}'}</code>. A missing or dangling <code>After</code>/<code>Before</code> target degrades to <code>AtEnd</code> plus a content-audit note. Inserted steps need their own <code>Id</code>s so a LATER extension can anchor on one of THEM.
        </li>
      </ol>
      <pre><code>{`{
  "Target": { "Action": "prepfish" },
  "PerCycleContributions": [ { "Channel": "yourmod:craft_quality", "Param": "COOKING", "Amount": 8.0 } ]
}`}</code></pre>
      <p>
        This is the shape the fish exemplar&apos;s pack-side extension uses: it is the ONLY thing that makes
        the jar&apos;s <code>prepfish</code> action post anything at all - the jar action declares no
        contributions, and this one small file is entirely responsible for wiring the two together.
      </p>

      <H3 id="apply-order">Apply order</H3>
      <p>
        When several extensions could touch the same target, they apply in one deterministic order:{' '}
        <code>Priority</code> ascending (a HIGHER priority applies LATER, and wins a key-collision tie), then
        extension id alphabetically. This is a total order over distinct assets (ids are unique), so a stable
        sort fully determines the result on every server regardless of pack load order - two extensions
        anchored on the SAME step insertion point apply in exactly this order too.
      </p>

      <H3 id="composition-order">Composition order with native Parent</H3>
      <p>
        Extensions apply to the <code>Parent</code>-resolved target AT READ TIME, and extension additions do{' '}
        <strong>not</strong> flow down <code>Parent</code> chains themselves: a{' '}
        <code>{'{Target: {Action}}'}</code> extension reaches every station that references that action via{' '}
        <code>Ref</code>; a <code>{'{Target: {Station}}'}</code> step-insertion applies to that ONE station
        only, even if another station inherits from it via <code>Parent</code>.
      </p>

      <H2 id="avoiding-double-counts">Avoiding double-counted contributions</H2>
      <p>
        <code>PerCycleContributions</code> APPENDS, it never replaces. If the base already posts{' '}
        <code>{'{Channel: "yourmod:craft_quality", Param: "OAK", Amount: 8.0}'}</code> and an extension adds
        an entry for the SAME <code>(Channel, Param)</code> pair, the amounts SUM - the effective total
        doubles to 16.0 per cycle rather than crediting something new. An extension should add a genuinely
        NEW pair; to retune an existing one on content you do not own, that is a request to the base author,
        not something this mechanism can safely do for you.
      </p>
      <p>
        The content audit catches this for you: <code>EXTENSION_CONTRIBUTION_DUPLICATE</code> fires when an
        extension appends a <code>(Channel, Param)</code> pair the base - or another extension on the same
        target - already declares, and names the colliding extensions.
      </p>

      <H2 id="non-extensible">Deliberately non-extensible</H2>
      <p>
        A few things are intentionally left OUT of this mechanism, because additive composition would be the
        wrong tool for them:
      </p>
      <ul>
        <li><code>Requires</code> - an extension must never tighten or loosen another author&apos;s access gate.</li>
        <li><code>Settings</code> - the server-wide singleton is an owner-only concern.</li>
        <li><code>Custody.States</code> - one visual empty/loaded pair per action; there is nothing meaningful to append to it.</li>
        <li>Scalar groups (<code>Work</code>, <code>Hold</code>, <code>Camera</code>, <code>Animation</code>, <code>Puppet</code>) - overriding these is load-order&apos;s job (a full replacement or a <code>Parent</code> override), not an additive extension.</li>
        <li>The internals of an existing <code>Roll</code> - an extender adds their OWN new Roll beside it instead of reaching inside someone else&apos;s.</li>
        <li><code>FlairAsset.Moments</code> - see <Link href="/docs/guides/flairs/">Flairs</Link>; the flair union already composes without needing this mechanism.</li>
      </ul>
    </DocPage>
  )
}
