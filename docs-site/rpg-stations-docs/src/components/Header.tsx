'use client'

import { ExternalLink } from 'lucide-react'
import { Search } from './Search'

export function Header() {
  return (
    <header className="sticky top-0 z-30 bg-dark-900/95 backdrop-blur border-b border-dark-700">
      <div className="flex items-center justify-between px-4 py-3 lg:px-8">
        {/* Spacer for mobile menu button */}
        <div className="w-10 lg:hidden" />

        {/* Search */}
        <div className="flex-1 max-w-md mx-4">
          <Search />
        </div>

        {/* Right side links */}
        <div className="flex items-center gap-2">
          <a
            href="https://github.com/arfemia/hytale-rpg-stations"
            target="_blank"
            rel="noopener noreferrer"
            className="hidden sm:flex items-center gap-1 px-3 py-1.5 text-sm text-dark-300 hover:text-white transition-colors"
          >
            GitHub
            <ExternalLink className="w-3 h-3" />
          </a>
        </div>
      </div>
    </header>
  )
}
