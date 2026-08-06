import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Actions & Step Programs',
  description: 'Phases, beats, Repeat, Duration, and the fixed step execution order.',
  openGraph: {
    title: 'Actions & Step Programs | RPG Stations Docs',
    description: 'Multi-action stations, the implicit program, and authored step programs.',
  },
}

export default function ActionsAndStepsPage() {
  return (
    <DocPage
      title="Actions & Step Programs"
      description="Multiple actions per station, and authored step-by-step programs"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Actions & Step Programs' }]}
      prevPage={{ title: 'Your First Station', href: '/docs/guides/your-first-station/' }}
      nextPage={{ title: 'Multi-Station Programs', href: '/docs/guides/multi-station-programs/' }}
    >
      <H2 id="actions">Actions</H2>
      <p>
        A station&apos;s <code>Actions</code> map holds one or more named <code>ActionDef</code> entries. Every
        leaf on an <code>ActionDef</code> - <code>Input</code>, <code>Custody</code>, <code>Puppet</code>,{' '}
        <code>Work</code>, <code>Recipe</code>, <code>Tool</code>, <code>Hold</code>, <code>Camera</code>,{' '}
        <code>Animation</code>, <code>Presentation</code>, <code>Completion</code>, <code>Loot</code>,{' '}
        <code>Requires</code>, <code>Steps</code>, <code>Anchors</code>, <code>Picker</code> - is a{' '}
        <strong>whole-group override</strong> of the matching station-level group: authoring a group on an
        action REPLACES the station&apos;s own group for that action, never a per-leaf merge. Omit a group and
        the action falls back to whatever the station authors at the top level.
      </p>
      <p>
        The Anvil&apos;s two actions - <code>convert</code> (sharpen a bar) and <code>enhance</code> (roll
        stats onto a placed weapon) - share the station-level <code>Tool</code>/<code>Puppet</code>/
        <code>Hold</code>/<code>Camera</code> groups but each author their own <code>Input</code>,{' '}
        <code>Custody</code>, and <code>Work</code>:
      </p>
      <pre><code>{`"Actions": {
  "convert": {
    "Input": { "ResourceTypeId": "Metal_Bars" },
    "Custody": { "MaxQuantity": 100, "States": { "Empty": "Default", "Loaded": "BarsPlaced" } },
    "Work": { "CycleMs": 3800,
              "PerCycleContributions": [ { "Channel": "yourmod:craft_quality", "Param": "IRON", "Amount": 6.0 } ] },
    "Recipe": { "Conversions": [ /* ...one entry per metal... */ ] }
  },
  "enhance": {
    "Input": { "Function": "Weapon" },
    "Custody": { "MaxQuantity": 1, "Input": { "Function": "Weapon" },
                 "States": { "Empty": "Default", "Loaded": "WeaponPlaced" } },
    "Work": { "CycleMs": 600, "Repeat": false,
              "PerCycleContributions": [ { "Channel": "yourmod:craft_quality", "Param": "IRON", "Amount": 25.0 } ] },
    "Steps": [ /* the ritual - see below */ ]
  }
}`}</code></pre>

      <H3 id="diegetic-selection">Diegetic selection</H3>
      <p>
        Which action a session runs is picked by <strong>what the player is holding</strong> (or by a loaded
        custody claim, which always commits to its own action rather than being re-selected). Each
        action&apos;s <code>Input</code> is an <code>ActionInput</code> matcher: <code>ItemId</code>,{' '}
        <code>ResourceTypeId</code>, native item <code>Tags</code>, or a <strong>functional</strong> route -{' '}
        <code>Function: &quot;Weapon&quot;|&quot;Armor&quot;|&quot;Tool&quot;</code>, tested against the held item&apos;s live
        shape. A match is ANY route satisfied; an <code>Input</code> with nothing authored is a catch-all
        (matches anything - the validator flags an unreachable catch-all placed before a more specific
        action).
      </p>

      <H3 id="standalone-actions">Standalone actions and Ref</H3>
      <p>
        An action can also live in its own reusable file, <code>Server/RpgStations/Actions/&lt;Name&gt;.json</code>{' '}
        (an <code>ActionAsset</code>, id = lowercased filename) - the EXACT same field set as an inline{' '}
        <code>ActionDef</code>. A station attaches it by reference:
      </p>
      <pre><code>{`"Actions": { "prepfish": { "Ref": "prepfish" } }`}</code></pre>
      <p>
        Any OTHER group authored alongside <code>Ref</code> on that same inline entry overlays the referenced
        action group-wise (the same whole-group-replace rule applied twice: station default → referenced
        action → inline overlay). A dangling <code>Ref</code> is a validator finding and denies the engage
        gracefully rather than throwing. Native <code>Parent</code> BETWEEN <code>ActionAsset</code>s is the
        &quot;author only the delta&quot; reuse route between two standalone actions - a different axis from{' '}
        <code>Ref</code>, which is the per-station <em>attachment</em> route.
      </p>

      <H2 id="the-implicit-program">The implicit program</H2>
      <p>
        An action with no authored <code>Steps</code> gets the classic convert loop for free: one implicit
        step running Consume → Produce → Roll → Presentation. This is exactly what every plain single-purpose
        station (a Sawmill with no <code>Actions</code> map at all) runs, and it is byte-identical behavior to
        a hand-authored one-step program.
      </p>

      <H2 id="the-step-record">The step record</H2>
      <p>
        An authored program is an ordered array of <code>StationStep</code>s. Every field on a step is
        nullable; a step composes any combination of independent <strong>phase groups</strong> plus a handful
        of base fields that apply to every step regardless of which phases it authors.
      </p>

      <table>
        <thead><tr><th>Field</th><th>What it does</th></tr></thead>
        <tbody>
          <tr><td><code>Id</code></td><td>Unique within one action&apos;s <code>Steps</code>; required whenever another step or an extension insertion anchors on it.</td></tr>
          <tr><td><code>Conditions</code> / <code>OnConditionFail</code></td><td>A gate re-checked at each iteration entry. A failing check either <code>Skip</code>s (no-op, continue) or <code>Fail</code>s (default); <code>Goto</code> jumps to another step&apos;s Id on a passing/skipping result.</td></tr>
          <tr><td><code>Repeat</code></td><td>A fixed <code>Times</code>, or a factor-resolved <code>{'{Min,Max,AddFactors}'}</code> range, resolved once at step entry.</td></tr>
          <tr><td><code>Duration</code></td><td><code>{'{Ms}'}</code> - a post-phase hold; the puppet&apos;s prop/clip and any presentation persist across the hold.</td></tr>
          <tr><td><code>Puppet</code></td><td>A per-step <code>{'{Clip?, Prop?}'}</code> override for the moment-to-moment animation/held item, played once at iteration entry.</td></tr>
          <tr><td><code>Presentation</code></td><td>A sound/particle/etc. cue played once at iteration entry.</td></tr>
          <tr><td><code>Walk</code></td><td>Move the puppet to a named anchor. See <Link href="/docs/guides/multi-station-programs/">Multi-Station Programs</Link>.</td></tr>
          <tr><td><code>Consume</code></td><td><code>{'{ItemId|ResourceTypeId, Quantity, From: Inventory|Custody}'}</code> - drain from the player&apos;s backpack or the block&apos;s placed-input claim.</td></tr>
          <tr><td><code>Stamp</code></td><td>The enhance-commit phase (reagents, durability, stat rolls). See <Link href="/docs/guides/enhancement-and-stamp/">Enhancement &amp; Stamp</Link>.</td></tr>
          <tr><td><code>Produce</code></td><td><code>{'{ItemId, Quantity, To: Inventory|Custody}'}</code> - grant to the backpack, or deposit into a custody claim (the primary station&apos;s or a claimed anchor&apos;s).</td></tr>
          <tr><td><code>Roll</code></td><td>A <code>LootRef</code> - the same weighted loot vocabulary a station&apos;s own <code>Loot</code> group uses. See <Link href="/docs/guides/loot-and-factors/">Loot &amp; Factors</Link>.</td></tr>
          <tr><td><code>Commands</code></td><td>Console commands run with the usual placeholder substitutions.</td></tr>
        </tbody>
      </table>

      <H3 id="execution-order">Execution order</H3>
      <p>
        Every step iteration runs the SAME fixed order, regardless of which phases it authors:
      </p>
      <pre><code>{`Conditions gate -> Walk -> Consume -> Stamp -> Produce -> Roll -> Commands
  -> Presentation / Puppet clip (fire at iteration entry)
  -> Duration hold (suspend)
  -> next iteration or next step`}</code></pre>
      <p>
        A step combining <code>Consume</code> and <code>Produce</code> in the same step is an{' '}
        <strong>atomic transform</strong> - there is no window where inputs are consumed but nothing has been
        produced yet. When a program deliberately needs to split consuming and producing across a delay or a
        walk (the fish exemplar splits &quot;place raw fish&quot; from &quot;harvest cooked fish&quot; across a 2.5-second
        cook), the engine&apos;s iteration refund ledger covers the gap: an iteration that consumed inputs but
        stopped before its matching <code>Produce</code> committed (session stopped mid-walk, damage
        interrupt, disconnect) refunds that iteration&apos;s consumed tally when the session stops.
      </p>

      <H3 id="pure-beats">Pure beats</H3>
      <p>
        A step authoring NO phase group at all is a pure <strong>beat</strong> - just a hold, a clip, and a
        presentation cue. The Anvil&apos;s hammer-strike ritual is three beats followed by the stamp:
      </p>
      <pre><code>{`"Steps": [
  { "Id": "strike1", "Duration": { "Ms": 650 }, "Puppet": { "Clip": "RPG_Emote_Saw" },
    "Presentation": { "Sound": "SFX_Metal_Hit", "Particles": "Block_Gem_Sparks" } },
  { "Id": "strike2", "Duration": { "Ms": 650 }, "Puppet": { "Clip": "RPG_Emote_Saw" },
    "Presentation": { "Sound": "SFX_Metal_Hit", "Particles": "Block_Gem_Sparks" } },
  { "Id": "settle",  "Duration": { "Ms": 900 } },
  { "Id": "stamp",   "Duration": { "Ms": 800 }, "Puppet": { "Prop": { "Source": "None" } },
    "Stamp": { /* ...see Enhancement & Stamp... */ },
    "Presentation": { "Sound": "SFX_Chest_Legendary_FirstOpen_Player", "Particles": "Block_Gem_Sparks" } }
]`}</code></pre>
      <p>
        The <code>stamp</code> step&apos;s own <code>Puppet.Prop.Source: &quot;None&quot;</code> empties the puppet&apos;s
        hands for the enchant-flourish beat (it authors a Prop override but no Clip, so no hammer swing fires
        on it), and its 800ms <code>Duration</code> is the post-stamp flourish hold - the ritual visibly
        pauses with empty hands after the stat roll commits, before the session completes.
      </p>

      <H3 id="repeat-footgun">The Consume-inside-Repeat footgun</H3>
      <p>
        A step that both <code>Consume</code>s AND authors a <code>Repeat</code> count re-runs its Consume
        phase on <strong>every</strong> iteration - a step meant to consume ONE input then perform several
        repeated beats over it will over-drain the source. Split it: a one-shot step with{' '}
        <code>Consume</code> and no <code>Repeat</code>, followed by a separate pure-beat step with{' '}
        <code>Repeat</code> and no <code>Consume</code>:
      </p>
      <pre><code>{`{ "Id": "load",  "Consume": { "ResourceTypeId": "Fish", "Quantity": 1, "From": "Custody" } },
{ "Id": "scale", "Repeat": { "Times": 3 }, "Duration": { "Ms": 600 },
  "Puppet": { "Clip": "RPG_Emote_Knife" }, "Presentation": { "Sound": "SFX_Daggers_T1_Swing" } }`}</code></pre>
      <p>
        This is the exact shape the fish-preparation exemplar uses (three knife scrapes over one loaded fish,
        not three consumed fish) - see{' '}
        <Link href="/docs/guides/multi-station-programs/">Multi-Station Programs</Link> for the full program.
      </p>

      <H3 id="repeat-while-inputs">Repeat-while-inputs</H3>
      <p>
        There is no separate &quot;repeat while inputs remain&quot; flag. A repeating action (<code>Work.Repeat: true</code>,
        the default) whose <code>Consume</code> phase finds insufficient inputs at its source simply ends the
        session gracefully once none remain - the session summary shows the completed totals, and custody
        auto-returns everywhere. A <strong>non-repeating</strong> program (<code>Work.Repeat: false</code>, the
        Anvil&apos;s <code>enhance</code> ritual shape) completes the whole session after one program run
        instead of looping, and gets an instant first dispatch - no cycle-cadence latency eaten before its one
        and only cycle.
      </p>
    </DocPage>
  )
}
