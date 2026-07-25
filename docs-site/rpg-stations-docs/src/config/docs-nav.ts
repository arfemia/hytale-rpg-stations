/**
 * Single source of truth for documentation navigation AND search.
 *
 * Both the {@link Sidebar} (structure: groups, icons, colors, ordering) and the
 * {@link Search} component (page + section index) derive from this file, so a
 * new doc page is wired in ONE place: add a leaf with a `search` block to `nav`
 * and it shows up in the sidebar and the Cmd+K index automatically.
 *
 * - `nav` is the rendered navigation tree (what the sidebar maps over).
 * - Each searchable leaf/link carries an optional `search` block; `buildSearchIndex`
 *   flattens those into page-level results and expands any `sections` into
 *   anchor-level results.
 * - `searchExtras` holds search-only rows that have no nav leaf of their own.
 *
 * CONTENT NOTE (wave-5): the tree follows the page map from the unified design
 * (section 6.3) PLUS two pages the design's late-adoption header added - Selection
 * & Output Categories and Native Composition - neither of which existed as a
 * concept when 6.3 was drafted. Every leaf below is backed by a real page: the
 * Authoring Guides, the reference pages (Stat Channels, CustomSkillAsset,
 * Commands, Add-ons & Integrations), and Schema Reference, which renders the
 * generated per-type field tables from the codec-walked schema.json.
 */
import type { ElementType } from 'react'
import {
  BookOpen,
  Boxes,
  FileText,
  Gauge,
  Hammer,
  Home,
  Layers,
  Rocket,
  ScrollText,
  Settings,
  Wand2,
} from 'lucide-react'

export type SearchCategory =
  | 'General'
  | 'Concepts'
  | 'Authoring'
  | 'Reference'
  | 'Schema'
  | 'Developer'

export type SearchResult = {
  title: string
  description: string
  href: string
  category: SearchCategory
  section?: string
  keywords?: string[]
}

/** Search metadata attached to a nav leaf. `title` overrides the nav title in results. */
type LeafSearch = {
  title?: string
  description: string
  category: SearchCategory
  keywords?: string[]
  /** Anchor-level sub-entries (deep links into the page). */
  sections?: {
    title: string
    /** The `#anchor` appended to the page href. */
    hash: string
    description: string
    keywords?: string[]
  }[]
}

export type NavLeaf = {
  title: string
  href: string
  search?: LeafSearch
}

export type NavItem = {
  title: string
  href?: string
  icon?: ElementType
  badge?: string
  color?: string
  children?: NavLeaf[]
  /** Top-level links can be searchable too. */
  search?: LeafSearch
}

export const nav: NavItem[] = [
  {
    title: 'Home',
    href: '/',
    icon: Home,
    search: { description: 'RPG Stations documentation homepage', category: 'General', keywords: ['docs', 'documentation', 'start'] },
  },
  {
    title: 'Getting Started',
    href: '/docs/getting-started/',
    icon: Rocket,
    search: {
      description: 'Install RPG Stations and its ZiggfreedCommon dependency, enable it, place your first station',
      category: 'General',
      keywords: ['install', 'setup', 'download', 'server', 'ziggfreedcommon', 'dependency', 'mods folder', 'manifest'],
      sections: [
        { title: 'Dependencies', hash: 'dependencies', description: 'ZiggfreedCommon is the only hard dependency; MMO Skill Tree is an optional bridge partner', keywords: ['ziggfreedcommon', 'mmo skill tree', 'optional'] },
        { title: 'Installing', hash: 'installing', description: 'Drop the jar in Mods/, restart, confirm the boot log', keywords: ['mods folder', 'restart', 'boot log'] },
        { title: 'Enabling', hash: 'enabling', description: 'The Settings asset Enabled flag and how it differs from a config file', keywords: ['settings', 'enabled', 'settings.json'] },
      ],
    },
  },
  {
    title: 'Concepts',
    href: '/docs/concepts/',
    icon: BookOpen,
    search: {
      description: 'Stations, sessions, actions, steps, custody, and the puppet in prose',
      category: 'Concepts',
      keywords: ['station', 'session', 'action', 'step', 'custody', 'puppet', 'concepts', 'overview', 'work loop'],
      sections: [
        { title: 'Stations and sessions', hash: 'stations-and-sessions', description: 'A station is one asset + one block + one interaction; a session is the transient in-memory work loop', keywords: ['StationAsset', 'StationService', 'toggle'] },
        { title: 'Actions', hash: 'actions', description: 'A station runs one or more named actions, selected diegetically by held item or custody', keywords: ['ActionDef', 'ActionAsset', 'selection'] },
        { title: 'Steps', hash: 'steps', description: 'An action either runs the implicit convert loop, or an authored step program', keywords: ['StationStep', 'phases', 'beat'] },
        { title: 'Custody', hash: 'custody', description: 'Session-scoped placed-input claims, never persisted', keywords: ['custody', 'claim'] },
        { title: 'The puppet', hash: 'the-puppet', description: 'Hide the player, spawn a performer that does the work instead', keywords: ['puppet', 'performer'] },
      ],
    },
  },
  {
    title: 'Authoring Guides',
    icon: Hammer,
    children: [
      {
        title: 'Your First Station',
        href: '/docs/guides/your-first-station/',
        search: {
          description: 'A worked walkthrough authoring one station end to end: asset, block, interaction, lang, validate',
          category: 'Authoring',
          keywords: ['first station', 'walkthrough', 'tutorial', 'StationAsset', 'RootInteraction', 'rpg_station_use', 'validate'],
        },
      },
      {
        title: 'Actions & Step Programs',
        href: '/docs/guides/actions-and-steps/',
        search: {
          description: 'Phases, beats, Repeat, Duration, and the fixed execution order a step program walks',
          category: 'Authoring',
          keywords: ['action', 'step', 'beats', 'repeat', 'duration', 'phase order', 'ActionDef', 'ActionAsset', 'StationStep', 'consume', 'produce', 'roll', 'commands', 'implicit program'],
        },
      },
      {
        title: 'Multi-Station Programs',
        href: '/docs/guides/multi-station-programs/',
        search: {
          description: 'Anchors, walking between stations, claiming remote blocks, and the fish preparation walkthrough',
          category: 'Authoring',
          keywords: ['anchor', 'walk', 'claim', 'multi-station', 'fish', 'PrepFish', 'CookingFire', 'CuttingBoard', 'anchors discovery'],
        },
      },
      {
        title: 'Custody & Placed Display',
        href: '/docs/guides/custody-and-placed-display/',
        search: {
          description: 'Placed-input custody claims, block states, and the facing-relative placed-as-entity display',
          category: 'Authoring',
          keywords: ['custody', 'placed display', 'facing-relative', 'MaxQuantity', 'States', 'Display', 'StationCustodyDisplay'],
        },
      },
      {
        title: 'Puppet & Performers',
        href: '/docs/guides/puppet-presentation/',
        search: {
          description: 'Performer look sources, NpcRole, persistence and reconcile, and the walk mechanism',
          category: 'Authoring',
          keywords: ['puppet', 'performer', 'look', 'presentation', 'PlayerClone', 'Model', 'NpcRole', 'Hide.Route', 'Scale', 'reconcile'],
        },
      },
      {
        title: 'Selection & Output Categories',
        href: '/docs/guides/selection/',
        search: {
          description: 'Sneak+F routing: the native bench window, the multi-output picker, and the plain-F default category',
          category: 'Authoring',
          keywords: ['sneak', 'crouch', 'picker', 'bench window', 'category', 'decideRoute', 'first authored', 'pending selection'],
        },
      },
      {
        title: 'Loot & Factors',
        href: '/docs/guides/loot-and-factors/',
        search: {
          description: 'The weighted loot vocabulary, the stat factor provider, and the either-or luck rule',
          category: 'Authoring',
          keywords: ['loot', 'factor', 'FactorRef', 'stat', 'weighted', 'either-or', 'Roll', 'Chance', 'Ladder', 'Grants', 'station_luck'],
        },
      },
      {
        title: 'Native Composition',
        href: '/docs/guides/native-composition/',
        search: {
          description: 'The id-ref-only principle: referencing native interactions, effects, drop lists, recipes, tags, emotes, sounds and particles',
          category: 'Authoring',
          keywords: ['native composition', 'id-ref-only', 'Interaction', 'EffectRef', 'DropList', 'FromCrafting', 'Tags.Family', 'Emote', 'Sound', 'Particle'],
        },
      },
      {
        title: 'Enhancement & Stamp',
        href: '/docs/guides/enhancement-and-stamp/',
        search: {
          description: 'The Stamp step: reagents, durability, composable stat rolls, and the Budgets cap model',
          category: 'Authoring',
          keywords: ['enhancement', 'stamp', 'caps', 'budgets', 'RollPool', 'StatRollEntry', 'EnhanceStamper', 'anvil'],
        },
      },
      {
        title: 'Flairs',
        href: '/docs/guides/flairs/',
        search: { description: 'The open flair/moment vocabulary and the standalone FlairAsset', category: 'Authoring', keywords: ['flair', 'moment', 'FlairAsset', 'cosmetic'] },
      },
      {
        title: 'Extending Other Packs',
        href: '/docs/guides/extending-other-packs/',
        search: {
          description: 'The fourth-party extension vocabulary: ExtensionAsset, targets, payload table, conflict rules, and the non-extensible list',
          category: 'Authoring',
          keywords: ['extension', 'ExtensionAsset', 'anchor', 'insertion', 'conflict', 'fourth-party', 'non-extensible', 'Target', 'Priority'],
        },
      },
      {
        title: 'Settings',
        href: '/docs/guides/settings/',
        search: { description: 'The RpgStationsSettingsAsset server-wide Enabled flag and Summary HUD', category: 'Authoring', keywords: ['settings', 'SettingsAsset', 'SummaryHud'] },
      },
      {
        title: 'Localization',
        href: '/docs/guides/localization/',
        search: {
          description: 'The three lang families: rpgstations.lang, the native item/emote namespaces, and mmoskilltree.lang',
          category: 'Authoring',
          keywords: ['localization', 'lang', 'i18n', 'translation', 'RpgMsg', 'items.lang', 'avatarCustomization'],
        },
      },
    ],
  },
  {
    title: 'Schema Reference',
    href: '/docs/schema/',
    icon: Layers,
    color: 'text-cyan-400',
    search: {
      description: 'Autogenerated field tables for every asset type, walked from the live codecs',
      category: 'Schema',
      keywords: ['schema', 'reference', 'codec', 'field', 'StationAsset', 'ActionAsset', 'ActionDef', 'StationStep', 'Ingredient', 'LootRef', 'Roll', 'Condition', 'FactorRef', 'Custody', 'Puppet', 'Requires', 'Presentation', 'StatRollEntry', 'RollPool', 'LootableAsset', 'FlairAsset', 'ExtensionAsset', 'SettingsAsset'],
    },
  },
  {
    title: 'Stat Channels',
    href: '/docs/stat-channels/',
    icon: Gauge,
    search: {
      description: 'The MMO Skill Tree channel-id table, units, MMO_Level_*/MMO_CombatLevel, and the generic Zig_Entity_Stats tag',
      category: 'Reference',
      keywords: ['stat channel', 'MMO_Level', 'MMO_CombatLevel', 'Zig_Entity_Stats', 'stat', 'channel', 'MMO_Luck', 'station_luck', 'EntityStatMap'],
    },
  },
  {
    title: 'CustomSkillAsset',
    href: '/docs/custom-skill-asset/',
    icon: Wand2,
    search: {
      description: 'The MMO-side custom skill type, documented with the same treatment as a native asset',
      category: 'Reference',
      keywords: ['CustomSkillAsset', 'custom skill', 'mmo'],
    },
  },
  {
    title: 'Server Setup',
    icon: Settings,
    children: [
      {
        title: 'Commands',
        href: '/docs/commands/',
        search: { description: '/rpgstations camera <preset>|list, /rpgstations validate', category: 'Reference', keywords: ['command', '/rpgstations', 'camera', 'validate', 'admin'] },
      },
    ],
  },
  {
    title: 'Add-ons & Integrations',
    href: '/docs/integrations/',
    icon: Boxes,
    search: {
      description: 'Any progression mod can hook the api artifact; the MMO Skill Tree pairing',
      category: 'Developer',
      keywords: ['integration', 'api', 'MMO Skill Tree', 'progression mod', 'native events'],
    },
  },
  {
    title: 'Changelog / Patch Notes',
    href: '/docs/changelog/',
    icon: FileText,
    search: { description: 'Developer changelog and per-version release notes', category: 'Reference', keywords: ['changelog', 'patch notes', 'release', 'version', 'history'] },
  },
]

/**
 * Search-only rows that don't correspond to a nav leaf. Empty for the scaffold;
 * the content leg (D3) adds keyword rows once pages carry real prose.
 */
const searchExtras: SearchResult[] = []

function flattenLeafSearch(title: string, href: string, search: LeafSearch): SearchResult[] {
  const out: SearchResult[] = [
    { title: search.title ?? title, description: search.description, href, category: search.category, keywords: search.keywords },
  ]
  if (search.sections) {
    for (const s of search.sections) {
      out.push({
        title: s.title,
        description: s.description,
        href: `${href}#${s.hash}`,
        category: search.category,
        section: search.title ?? title,
        keywords: s.keywords,
      })
    }
  }
  return out
}

/** Built once at module load. Page + section entries from `nav`, then `searchExtras`. */
export const searchIndex: SearchResult[] = (() => {
  const out: SearchResult[] = []
  for (const item of nav) {
    if (item.search && item.href) out.push(...flattenLeafSearch(item.title, item.href, item.search))
    if (item.children) {
      for (const child of item.children) {
        if (child.search) out.push(...flattenLeafSearch(child.title, child.href, child.search))
      }
    }
  }
  out.push(...searchExtras)
  return out
})()
