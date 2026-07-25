import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Multi-Station Programs',
  description: 'Anchors, walking between stations, claims, and the fish-preparation walkthrough.',
  openGraph: {
    title: 'Multi-Station Programs | RPG Stations Docs',
    description: 'A step program that reaches out to a second nearby station block.',
  },
}

export default function MultiStationProgramsPage() {
  return (
    <DocPage
      title="Multi-Station Programs"
      description="A step program that reaches out to a second, nearby station"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Multi-Station Programs' }]}
      prevPage={{ title: 'Actions & Step Programs', href: '/docs/guides/actions-and-steps/' }}
      nextPage={{ title: 'Custody & Placed Display', href: '/docs/guides/custody-and-placed-display/' }}
    >
      <p>
        A step program is not limited to the block the player pressed <code>F</code> on. An action can
        declare named <strong>anchors</strong> - other, separately-placed station blocks it discovers nearby -
        and a step can move its puppet to one, read or write that anchor&apos;s own custody claim, and use its
        own presentation. The player never has to interact with the anchor block directly; the whole program
        runs from the primary station&apos;s press.
      </p>

      <H2 id="anchors">Declaring anchors</H2>
      <p>
        <code>Anchors</code> lives on an <code>ActionDef</code> (inline or standalone), a map of anchor id to{' '}
        <code>{'{Station, MaxRadius}'}</code>:
      </p>
      <pre><code>{`"Anchors": { "fire": { "Station": "cookingfire", "MaxRadius": 12 } }`}</code></pre>
      <p>
        <code>Station</code> is a TYPE filter (the target station&apos;s own id, e.g. <code>cookingfire</code>);{' '}
        <code>MaxRadius</code> is the horizontal block search radius from the primary station block (defaults
        to 12). The anchor id <code>self</code> is reserved for the primary block itself and is never
        authored.
      </p>

      <H3 id="discovery">Discovery</H3>
      <p>
        At engage, the engine finds the NEAREST placed block whose station id matches each declared anchor,
        within its radius, in the same world - it uses a lazily-populated index of station blocks it has
        seen (fed by interactions and block-place events), falling back to one bounded ring scan if the index
        comes up empty. In practice: <strong>the engine finds station blocks it has interacted with before</strong>;
        a freshly world-edited block becomes discoverable after any interaction with it. If an anchor cannot
        be resolved at all, the engage denies gracefully with a localized hint naming the missing station and
        radius - never a partial engage.
      </p>

      <H3 id="claiming">Claiming</H3>
      <p>
        A resolved anchor is CLAIMED atomically at engage (first engager wins, world-thread serial) - the
        exact same claim mechanism a session already holds on its own primary block, generalized. While
        claimed, another player&apos;s press on that anchor block denies as occupied, and a second program
        trying to claim the same block fails its own claim and denies the whole engage. There is no queuing.
      </p>
      <p>
        The anchor station&apos;s OWN action is never invoked - only its block&apos;s custody claim and
        interaction-state visuals matter. &quot;Cooking at the fire&quot; is entirely the primary program&apos;s own
        step, dressed with the fire&apos;s own presentation.
      </p>

      <H2 id="the-walk-phase">The Walk phase</H2>
      <p>
        A step&apos;s <code>Walk</code> phase moves the PUPPET (not the real player, who stays hidden/held at
        the primary station) to a named anchor at a configurable speed:
      </p>
      <pre><code>{`{ "Id": "walkout", "Walk": { "To": "fire", "SpeedMps": 2.5 } }`}</code></pre>
      <p>
        <code>SpeedMps</code> defaults to 2.5. A station whose action authors any <code>Walk</code> step{' '}
        <strong>requires</strong> <code>Puppet</code> enabled on that action - the validator flags a{' '}
        <code>Walk</code> with no active puppet, and the engine denies the engage gracefully rather than
        walking an invisible nothing.
      </p>
      <p>
        The walk is real, obstacle-aware pathing (a small bounded pathfinder solved at engage per anchor, and
        re-solved at the start of each walk step - so a mid-program blockage denies that step gracefully
        rather than wedging the whole program), not a straight-line pass-through. A timeout guard (distance
        over speed plus a grace window) means a walk can never wedge the session even if something goes wrong
        mid-path.
      </p>

      <H2 id="custody-at-anchors">Custody at anchors</H2>
      <p>
        A step&apos;s <code>At</code> field names which anchor it runs at (absent = the primary station,{' '}
        <code>self</code>). A <code>Consume</code>/<code>Produce</code> phase on a step authoring{' '}
        <code>At</code> reads/writes THAT anchor&apos;s own custody claim, not the primary station&apos;s -{' '}
        <code>Produce.To: &quot;Custody&quot;</code> is exactly how a program deposits an in-progress item onto a
        remote block for the anchor&apos;s own cook/process loop to work.
      </p>

      <H2 id="the-fish-walkthrough">Walkthrough: preparing fish</H2>
      <p>
        RPG Stations ships a complete, zero-progression, two-station exemplar in the jar itself: a{' '}
        <strong>Cutting Board</strong> (the primary station, where the player presses F) and a{' '}
        <strong>Cooking Fire</strong> (the anchor, a fully-useful standalone station in its own right that the
        Cutting Board&apos;s program reaches out to). The whole loop: scale raw fish at the board, walk the
        fish over to a nearby fire, cook it, walk the cooked fish back, deposit it.
      </p>

      <H3 id="the-primary-station">CuttingBoard.json (primary)</H3>
      <pre><code>{`{
  "Identity": { "NameKey": "rpgstations.station.cuttingboard.name",
                "DescKey": "rpgstations.station.cuttingboard.desc" },
  "Puppet": {
    "Enabled": true,
    "Hide": { "Route": "Scale" },
    "Look": { "Source": "PlayerClone" },
    "Offset": { "X": 0.0, "Y": -0.4, "Z": 0.6 }, "Yaw": 0.0,
    "Prop": { "Source": "MirrorHeld", "Slot": "Hotbar" }
  },
  "Actions": { "prepfish": { "Ref": "prepfish" } }
}`}</code></pre>
      <p>
        The station itself is deliberately thin - the only action is a reference to a standalone{' '}
        <code>ActionAsset</code>, which owns every mechanical group. <code>Puppet</code> is authored HERE
        (at station level, so the resolved <code>prepfish</code> action inherits it) because the design rule
        for <code>Walk</code> requires a puppet: the player stays hidden at the board while their stand-in
        walks to the fire and back.
      </p>

      <H3 id="the-action">Actions/PrepFish.json (the program)</H3>
      <pre><code>{`{
  "Label": "rpgstations.action.prepfish.label",
  "Custody": { "MaxQuantity": 100, "Input": { "ResourceTypeId": "Fish" },
               "States": { "Empty": "Default", "Loaded": "Loaded" } },
  "Tool": { "Tags": { "Family": ["Dagger"] } },
  "Work": { "CycleMs": 500, "MaxDurationMs": 600000, "Exclusive": true, "Repeat": true },
  "Anchors": { "fire": { "Station": "cookingfire", "MaxRadius": 12 } },
  "Steps": [
    { "Id": "load",   "Consume": { "ResourceTypeId": "Fish", "Quantity": 1, "From": "Custody" } },
    { "Id": "scale",  "Repeat": { "Times": 3 }, "Duration": { "Ms": 600 },
      "Puppet": { "Clip": "RPG_Emote_Knife" }, "Presentation": { "Sound": "SFX_Daggers_T1_Swing" } },
    { "Id": "walkout", "Walk": { "To": "fire" },
      "Puppet": { "Prop": { "Source": "ItemId", "ItemId": "Food_Fish_Raw" } } },
    { "Id": "placeraw", "At": "fire", "Produce": { "ItemId": "Food_Fish_Raw", "Quantity": 1, "To": "Custody" },
      "Duration": { "Ms": 400 } },
    { "Id": "cook",     "At": "fire", "Duration": { "Ms": 2500 },
      "Presentation": { "Particles": "Smoke_Black" } },
    { "Id": "harvest",  "At": "fire", "Consume": { "ItemId": "Food_Fish_Raw", "Quantity": 1, "From": "Custody" },
      "Duration": { "Ms": 400 } },
    { "Id": "walkback", "Walk": { "To": "self" },
      "Puppet": { "Prop": { "Source": "ItemId", "ItemId": "Food_Fish_Grilled" } } },
    { "Id": "deposit",  "Produce": { "ItemId": "Food_Fish_Grilled", "Quantity": 1, "To": "Custody" },
      "Duration": { "Ms": 300 }, "Presentation": { "Sound": "SFX_Player_Drop_Item" } }
  ]
}`}</code></pre>
      <p>Reading the program beat by beat:</p>
      <ul>
        <li><code>load</code> consumes one fish from the board&apos;s custody (a one-shot step, no <code>Repeat</code> - the footgun rule from <Link href="/docs/guides/actions-and-steps/">Actions &amp; Step Programs</Link>).</li>
        <li><code>scale</code> repeats three knife-scrape beats over that one loaded fish - no further Consume.</li>
        <li><code>walkout</code> walks the puppet to the <code>fire</code> anchor, swapping its held prop to the raw fish along the way.</li>
        <li><code>placeraw</code>/<code>cook</code>/<code>harvest</code> all run <code>At: &quot;fire&quot;</code> - they read and write the Cooking Fire&apos;s OWN custody claim, not the Cutting Board&apos;s. The fire&apos;s own 2.5-second cook duration plays with the fire&apos;s own particle cue.</li>
        <li><code>walkback</code> returns the puppet to <code>self</code> (the Cutting Board) carrying the grilled fish.</li>
        <li><code>deposit</code> produces the grilled fish back into the Cutting Board&apos;s custody, where it accumulates until the session stops (auto-returned to the player&apos;s inventory).</li>
      </ul>
      <p>
        The tool gate is the native <strong>daggers</strong> weapon family (<code>Tags.Family: [&quot;Dagger&quot;]</code>),
        matched against every dagger tier with zero per-tier enumeration - see{' '}
        <Link href="/docs/guides/native-composition/">Native Composition</Link>. <code>Work.Repeat: true</code>{' '}
        plus the engine&apos;s graceful inputs-exhausted stop is &quot;repeats while fish remain&quot; with no extra
        mode flag.
      </p>

      <H3 id="the-anchor-station">CookingFire.json (the anchor)</H3>
      <p>
        The Cooking Fire is a fully standalone-useful station on its own - place raw fish directly, press F,
        get grilled fish, with its own classic convert loop and its own <code>Custody.States</code>{' '}
        (<code>Loaded</code> here is named <code>Lit</code>, matching the block&apos;s own lit-campfire state
        variant). When claimed as a remote anchor, none of its OWN Work/Recipe/Tool groups run - only its
        custody claim and block state matter to the visiting program.
      </p>

      <H2 id="lifecycle">Lifecycle at the edges</H2>
      <p>
        A server restart mid-walk or mid-program loses the session, its claims, its custody, and its puppet
        entirely by construction (nothing here is persisted) - block states self-heal to Empty on the next
        interaction. If a remote anchor block is broken mid-program, the session stops with a dedicated
        reason, every OTHER claimed block&apos;s custody auto-returns, and any in-flight iteration&apos;s
        already-consumed inputs are refunded. Every exit path (owner disconnect, death, damage, walk-off)
        releases anchor claims and refunds the ledger before the usual custody return.
      </p>
    </DocPage>
  )
}
