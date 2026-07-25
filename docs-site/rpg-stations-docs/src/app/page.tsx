import { Metadata } from 'next'
import Link from 'next/link'
import {
  Anchor,
  Boxes,
  Coins,
  Gauge,
  Gem,
  Hammer,
  Layers,
  Puzzle,
  Sparkles,
  User,
} from 'lucide-react'

export const metadata: Metadata = {
  title: 'RPG Stations - Diegetic Work Stations for Hytale Servers',
  description: 'Place a station, press F, watch your character work. A standalone Hytale server mod for diegetic interactive work stations - sawmills, forges, and anything a server owner authors.',
}

const features = [
  {
    icon: Hammer,
    title: 'Multi-Action Stations',
    description: 'One station can offer several actions, each with its own step program',
    href: '/docs/guides/actions-and-steps/',
  },
  {
    icon: Layers,
    title: 'Step Programs',
    description: 'Beats, Repeat, and Duration compose into orthogonal step phases, not modes',
    href: '/docs/guides/actions-and-steps/',
  },
  {
    icon: Anchor,
    title: 'Multi-Station Walks',
    description: 'A program can anchor across several stations, walking the player between them',
    href: '/docs/guides/multi-station-programs/',
  },
  {
    icon: Boxes,
    title: 'Placed-Input Custody',
    description: 'Facing-relative custody and display for items placed into a station',
    href: '/docs/guides/custody-and-placed-display/',
  },
  {
    icon: User,
    title: 'The Puppet',
    description: 'A skinned performer stands in for the player and performs the work',
    href: '/docs/guides/puppet-presentation/',
  },
  {
    icon: Gem,
    title: 'Conditional Loot',
    description: 'Weighted loot with a native stat factor and an either-or rule against double counting',
    href: '/docs/guides/loot-and-factors/',
  },
  {
    icon: Sparkles,
    title: 'Enhancement Stamping',
    description: 'A standalone stamper applies caps and rolls onto native per-stack stats',
    href: '/docs/guides/enhancement-and-stamp/',
  },
  {
    icon: Puzzle,
    title: 'Extension Surface',
    description: 'A fourth-party pack can extend another pack’s station via ExtensionAsset',
    href: '/docs/guides/extending-other-packs/',
  },
  {
    icon: Gauge,
    title: 'Schema-Backed Assets',
    description: 'Every asset type is a structured codec with generated field documentation',
    href: '/docs/schema/',
  },
  {
    icon: Coins,
    title: 'Progression-Agnostic',
    description: 'No progression of its own; any progression mod hooks the api artifact',
    href: '/docs/integrations/',
  },
]

export default function Home() {
  return (
    <div className="doc-content">
      {/* Hero Section */}
      <div className="text-center mb-12">
        <h1 className="text-4xl lg:text-5xl font-bold mb-4 bg-gradient-to-r from-primary-400 to-primary-600 bg-clip-text text-transparent border-none pb-0">
          RPG Stations
        </h1>
        <p className="text-xl text-dark-300 mb-6 max-w-2xl mx-auto">
          Diegetic work stations for Hytale servers - place, press F, watch your character work
        </p>
        <div className="flex flex-wrap justify-center gap-4">
          <Link
            href="/docs/getting-started/"
            className="px-6 py-3 bg-primary-600 hover:bg-primary-700 text-white font-semibold rounded-lg transition-colors no-underline"
          >
            Get Started
          </Link>
          <a
            href="https://github.com/arfemia/hytale-rpg-stations"
            target="_blank"
            rel="noopener noreferrer"
            className="px-6 py-3 bg-dark-700 hover:bg-dark-600 text-white font-semibold rounded-lg transition-colors no-underline"
          >
            View on GitHub
          </a>
        </div>
      </div>

      {/* Features Grid */}
      <h2 className="text-2xl font-bold mb-6 border-none">Features</h2>
      <div className="grid md:grid-cols-2 gap-4 mb-12">
        {features.map((feature) => (
          <Link
            key={feature.title}
            href={feature.href}
            className="group p-4 bg-dark-800 hover:bg-dark-700 border border-dark-700 rounded-lg transition-colors no-underline"
          >
            <div className="flex items-start gap-3">
              <feature.icon className="w-6 h-6 text-primary-400 mt-1 flex-shrink-0" />
              <div>
                <h3 className="font-semibold text-white group-hover:text-primary-400 transition-colors">
                  {feature.title}
                </h3>
                <p className="text-dark-400 text-sm mt-1">{feature.description}</p>
              </div>
            </div>
          </Link>
        ))}
      </div>

      {/* Required dependency callout */}
      <div className="p-6 mb-12 bg-dark-800 border border-primary-500/30 rounded-xl">
        <h2 className="text-lg font-semibold text-white border-none mb-2">Required Dependency</h2>
        <p className="text-dark-400 text-sm">
          RPG Stations depends on <code>ZiggfreedCommon</code> only, never any other mod. Neither
          RPG Stations nor a progression mod hard-depends on the other; the optional MMO Skill
          Tree pairing reaches back through a soft extension surface (native events plus a typed
          <code> api</code> artifact).
        </p>
      </div>

      {/* Quick Links */}
      <h2 className="text-2xl font-bold mb-4 border-none">Quick Links</h2>
      <div className="grid md:grid-cols-2 gap-2">
        <Link href="/docs/getting-started/" className="text-primary-400 hover:text-primary-300">
          Getting Started
        </Link>
        <Link href="/docs/concepts/" className="text-primary-400 hover:text-primary-300">
          Concepts
        </Link>
        <Link href="/docs/commands/" className="text-primary-400 hover:text-primary-300">
          Command Reference
        </Link>
        <Link href="/docs/schema/" className="text-primary-400 hover:text-primary-300">
          Schema Reference
        </Link>
        <Link href="/docs/stat-channels/" className="text-primary-400 hover:text-primary-300">
          Stat Channels
        </Link>
        <Link href="/docs/changelog/" className="text-primary-400 hover:text-primary-300">
          Changelog / Patch Notes
        </Link>
      </div>
    </div>
  )
}
