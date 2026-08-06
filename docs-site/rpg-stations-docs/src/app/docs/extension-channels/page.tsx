import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Extension Channels',
  description: 'One vocabulary, two directions: factors read a number in, contributions post a number out.',
  openGraph: {
    title: 'Extension Channels | RPG Stations Docs',
    description: 'Factor/Param and FactorRegistry for reads, Contributions and ContributionChannelRegistry for writes.',
  },
}

export default function ExtensionChannelsPage() {
  return (
    <DocPage
      title="Extension Channels"
      description="How another mod plugs numbers into a station, and how a station posts numbers back out"
      breadcrumbs={[{ title: 'Extension Channels' }]}
    >
      <p>
        RPG Stations carries no progression vocabulary of its own. It has exactly one extension idea, applied
        in both directions: <strong>content names a namespaced id, and some other mod owns what that id
        means.</strong> Reading a number in and posting a number out are the same shape, mirrored.
      </p>

      <table>
        <thead>
          <tr><th></th><th>READ - factors</th><th>WRITE - contributions</th></tr>
        </thead>
        <tbody>
          <tr>
            <td>Authored leaf</td>
            <td><code>{'{ "Factor": "<ns>:<id>", "Param": "<opaque>" }'}</code></td>
            <td><code>{'{ "Channel": "<ns>:<id>", "Param": "<opaque>", "Amount": <number> }'}</code></td>
          </tr>
          <tr><td>Registry</td><td><code>FactorRegistry.register(id, provider)</code></td><td><code>ContributionChannelRegistry.declare(id)</code></td></tr>
          <tr><td>Api accessor</td><td><code>RpgStationsApi.factors()</code></td><td><code>RpgStationsApi.channels()</code></td></tr>
          <tr><td>Editor dropdown</td><td><code>rpgstations:factors</code></td><td><code>rpgstations:channels</code></td></tr>
          <tr><td>Validator warn</td><td><code>UNKNOWN_FACTOR</code></td><td><code>UNKNOWN_CHANNEL</code></td></tr>
          <tr><td>What the engine does</td><td>Asks a registered provider for a <code>double</code>.</td><td>Forwards <code>{'{Channel, Param, Amount}'}</code> on the cycle event. Nothing else.</td></tr>
        </tbody>
      </table>
      <p>
        The one asymmetry is deliberate: the engine ships built-in FACTORS (<code>rpgstations:session_seconds</code>,{' '}
        <code>cycle_count</code>, <code>tool_power</code>, <code>tool_durability_percent</code>, and{' '}
        <code>stat</code>) because it can compute them. It ships <strong>zero</strong> built-in channels,
        because it interprets none.
      </p>

      <H2 id="reads">Reads: Factor and Param</H2>
      <p>
        A factor id resolves to a number a formula sums or gates on. See{' '}
        <Link href="/docs/guides/loot-and-factors/">Loot &amp; Factors</Link> for every site that accepts one.
        A mod registers a provider once at startup:
      </p>
      <pre><code>{`RpgStationsApi api = RpgStationsApi.get();
api.factors().register("yourmod:reputation", (ctx, param) -> reputationOf(ctx.playerId(), param));`}</code></pre>
      <p>
        <code>Param</code> is opaque to this engine - whatever the provider documents. An unregistered factor
        resolves to 0 and fails a <code>Condition</code> closed, with a one-time warning; content written for
        a mod that is not installed degrades quietly instead of breaking.
      </p>

      <H2 id="writes">Writes: Contributions</H2>
      <p>
        A contribution is an amount a station posts OUT to a named channel. The engine never resolves a
        channel; it forwards the entry verbatim on <code>StationCycleCompletedEvent</code> and lets the
        channel&apos;s owner decide what it means. A mod declares its channel ids at startup:
      </p>
      <pre><code>{`api.channels().declare("yourmod:crop_quality");`}</code></pre>
      <p>
        Declaring is optional in the sense that it never blocks anything: an undeclared channel is still
        forwarded, and the validator only emits an <code>UNKNOWN_CHANNEL</code> warning. Declaring is worth it
        anyway, because it puts the id in the in-game Asset Editor&apos;s <code>rpgstations:channels</code>{' '}
        dropdown and turns a typo from a silent no-op into a boot-log line.
      </p>

      <H3 id="two-sites">Two authoring sites, two meanings, one record</H3>
      <p>
        Scaling is decided by WHERE a contribution is authored, never by a flag on the entry. Same leaf shape,
        different documented semantics per owning group:
      </p>
      <table>
        <thead><tr><th>Site</th><th>Fires</th><th>Scaling</th></tr></thead>
        <tbody>
          <tr>
            <td><code>Work.PerCycleContributions[]</code></td>
            <td>Every completed cycle.</td>
            <td>Multiplied by the resolved tool multiplier (<code>Tool.PowerScale</code>); on an idle cycle, pre-scaled by <code>Work.Idle.Fraction</code> with the tool multiplier forced to 1.0.</td>
          </tr>
          <tr>
            <td><code>Roll.Grants.Contributions[]</code></td>
            <td>Once, when that roll grants.</td>
            <td>None. Posted verbatim - a rare find is worth the same whatever tool the player holds. <code>Cycle</code>-trigger rolls only.</td>
          </tr>
        </tbody>
      </table>
      <p>
        The per-cycle key says <code>PerCycle</code> out loud precisely so the two never read as the same blob
        in an author&apos;s eye. There is no <code>Scaled</code> knob: it would be meaningless at the grants
        site and would let content defeat the one-shot rule.
      </p>

      <H2 id="worked-example">A worked example</H2>
      <p>
        A fictitious <code>yourmod</code> rewards farming quality. It declares one channel and reads one
        factor, and the station content names both by id:
      </p>
      <pre><code>{`{
  "Work": {
    "CycleMs": 4000,
    "PerCycleContributions": [
      { "Channel": "yourmod:crop_quality", "Param": "WHEAT", "Amount": 6.0 }
    ]
  },
  "Loot": {
    "Rolls": [ {
      "Trigger": "Cycle",
      "Chance": { "BasePercent": 3,
                  "AddFactors": [ { "Factor": "yourmod:reputation", "Param": "guild" } ],
                  "CapPercent": 20 },
      "Grants": {
        "DropList": "RPG_Station_Sawmill_T1",
        "Contributions": [
          { "Channel": "yourmod:crop_quality", "Param": "WHEAT", "Amount": 25.0 }
        ]
      }
    } ]
  }
}`}</code></pre>
      <p>The listener side filters by channel and posts whatever the amount means to it:</p>
      <pre><code>{`eventBus.register(StationCycleCompletedEvent.class, event -> {
    for (StationContribution c : event.contributions()) {
        if (!"yourmod:crop_quality".equalsIgnoreCase(c.channel())) continue;
        creditQuality(event.playerId(), c.param(), c.amount() * event.toolMultiplier());
    }
    for (StationContribution c : event.oneShotContributions()) {
        if (!"yourmod:crop_quality".equalsIgnoreCase(c.channel())) continue;
        creditQuality(event.playerId(), c.param(), c.amount());   // verbatim, never scaled
    }
});`}</code></pre>
      <p>
        <strong>Filtering by channel is mandatory.</strong> Both lists carry every channel the station
        authored, including ones belonging to other mods; a listener that consumes an entry it did not declare
        is reading someone else&apos;s vocabulary. And note the two loops apply the tool multiplier
        differently - that difference IS the contract, not an oversight.
      </p>

      <H2 id="one-channel-many-params">One channel, many Params</H2>
      <p>
        Prefer ONE channel id with a meaningful <code>Param</code> over one channel per thing being credited.
        A channel-per-thing design explodes the declared set, makes the editor dropdown useless, and gives an
        author a new convention to learn for every new value. <code>Param</code> exists for exactly this.
      </p>

      <H2 id="next">Next</H2>
      <p>
        <Link href="/docs/integrations/">Add-ons &amp; Integrations</Link> covers the rest of the api surface
        (native events, the flair-unlock, enhance-stamper, summary-enricher and validation-hook registries)
        and the two-step presence check a consumer must use before touching any of it.
      </p>
    </DocPage>
  )
}
