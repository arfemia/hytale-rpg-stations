import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { H2, H3 } from '@/components/Heading'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Selection & Output Categories',
  description: 'Sneak+F routing: the native bench window, the multi-output picker, and the plain-F default.',
  openGraph: {
    title: 'Selection & Output Categories | RPG Stations Docs',
    description: 'What sneak+F opens on a station block, and how a multi-output station picks its default.',
  },
}

export default function SelectionPage() {
  return (
    <DocPage
      title="Selection & Output Categories"
      description="What sneak+F does at a station block, and how a multi-output station defaults"
      breadcrumbs={[{ title: 'Authoring Guides', href: '/docs/getting-started/' }, { title: 'Selection & Output Categories' }]}
      prevPage={{ title: 'Puppet & Performers', href: '/docs/guides/puppet-presentation/' }}
      nextPage={{ title: 'Loot & Factors', href: '/docs/guides/loot-and-factors/' }}
    >
      <p>
        A plain <code>F</code> press on a station block always toggles the diegetic work loop. A{' '}
        <strong>sneak (crouch) + F</strong> press instead opens a selection surface, when one applies - the
        SAME crouch flag the engine already reads as the diegetic crouch-to-exit signal, read fresh at the
        moment of the press.
      </p>

      <H2 id="routing">The routing decision</H2>
      <p>The routing is a small, pure decision tree, in priority order:</p>
      <ol>
        <li>Not sneaking → always the plain work toggle.</li>
        <li>Sneaking, and the block authors a native crafting/processing <strong>bench</strong> identity → open the NATIVE bench window.</li>
        <li>Sneaking, no bench, and the resolved station derives 2 or more distinct output CATEGORIES → open the multi-output PICKER.</li>
        <li>Sneaking, no bench, one or zero categories → falls through to the plain work toggle (a single-category station never shows anything for sneak+F).</li>
      </ol>

      <H2 id="bench-window">The native bench window</H2>
      <p>
        A station whose block authors a native crafting/processing bench identity opens the vanilla Hytale
        crafting UI on sneak+F, using the exact same window-construction path the first-party bench openers
        use, while a plain <code>F</code> press keeps running the diegetic work loop on the same block. This
        lets a station coexist with the ordinary bench menu players already know, without giving up the
        diegetic loop as the block&apos;s primary interaction. This route degrades gracefully (falls straight
        through to the plain toggle) on every non-bench block, a bench block missing an associated item, or an
        unsupported bench type - it never throws.
      </p>

      <H2 id="the-picker">The multi-output picker</H2>
      <p>
        When a station&apos;s effective conversions span more than one distinct source CATEGORY, sneak+F opens
        a picker page instead: one tab per category, each showing a representative output item as its icon (a
        category id has no display name of its own, so the icon differentiates the tabs). Selecting a tab
        commits the session to that category&apos;s conversions only.
      </p>
      <p>
        The Sawmill is the flagship multi-category exemplar: its <code>Recipe.FromCrafting.Categories</code>{' '}
        spans <code>WoodPlanks</code>, <code>DecorativePlanks</code>, and <code>OrnatePlanks</code> - one held
        log, three genuinely different valid outputs. Each derived conversion is stamped with its source
        category automatically (the same native-recipe-composition machinery in{' '}
        <Link href="/docs/guides/native-composition/">Native Composition</Link> that derives the conversions
        in the first place), so no per-conversion category authoring is needed beyond widening the{' '}
        <code>Categories</code> list.
      </p>
      <p>
        A conversion can also be tagged explicitly by hand-authoring its own <code>Category</code> field,
        for a station whose conversions are not native-derived.
      </p>

      <H3 id="locked-categories">Locked categories</H3>
      <p>
        The station-level (or per-action) <code>Picker</code> group has one knob:
      </p>
      <pre><code>{`"Picker": { "ShowLocked": true }`}</code></pre>
      <p>
        <code>ShowLocked</code> defaults to <code>true</code>: a category the player&apos;s current tool
        cannot reach renders greyed in the picker instead of disappearing, so the player can see what exists
        even while gated out of it. <code>false</code> hides a locked category from the picker entirely.
      </p>

      <H2 id="the-default-category">The plain-F default: first-authored priority</H2>
      <p>
        Once a station has more than one category, plain <code>F</code> (no sneak, no explicit picker
        choice) needs SOME default. The rule: with no explicit choice recorded, a multi-category station
        defaults to the FIRST entry in its authored <code>FromCrafting.Categories</code> array that actually
        produced at least one derived conversion - authors control the plain-F default purely by ORDERING that
        array, never by a separate default flag. For a hand-authored (non-derived) multi-category station, the
        default falls to the first category present among its own conversions.
      </p>
      <p>
        On the Sawmill, <code>Categories: [&quot;WoodPlanks&quot;, &quot;DecorativePlanks&quot;, &quot;OrnatePlanks&quot;]</code> means
        plain <code>F</code> always defaults to plain planks, exactly matching the station&apos;s behavior
        before it ever grew a picker - reordering the array is the one authoring lever to change the default;
        sneak+F still offers all three regardless of the array order.
      </p>

      <H3 id="pending-selection">The pending-selection flow</H3>
      <p>
        An EXPLICIT choice made through the picker always wins verbatim over the first-authored default, for
        that next session only - picking a category, then pressing plain <code>F</code>, starts a session
        filtered to exactly that category&apos;s conversions. There is no persisted &quot;remembered&quot; choice across
        sessions; each fresh engage without an explicit picker pick falls back to the first-authored default
        again.
      </p>
    </DocPage>
  )
}
