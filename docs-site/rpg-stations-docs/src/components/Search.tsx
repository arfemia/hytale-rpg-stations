'use client'

import { useState, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { Search as SearchIcon, X } from 'lucide-react'
import { searchIndex, type SearchResult } from '@/config/docs-nav'

function scoreResult(item: SearchResult, query: string): number {
  const lowerQuery = query.toLowerCase()
  const title = item.title.toLowerCase()
  const desc = item.description.toLowerCase()
  const cat = item.category.toLowerCase()

  let score = 0

  // Title matches (highest priority)
  if (title === lowerQuery) {
    score += 100
  } else if (title.startsWith(lowerQuery)) {
    score += 80
  } else if (title.includes(lowerQuery)) {
    score += 60
  }

  // Keyword matches
  if (item.keywords?.some(k => k.toLowerCase().includes(lowerQuery) || lowerQuery.includes(k.toLowerCase()))) {
    score += 50
  }

  // Description match
  if (desc.includes(lowerQuery)) {
    score += 30
  }

  // Category match
  if (cat.includes(lowerQuery)) {
    score += 10
  }

  return score
}

export function Search() {
  const [query, setQuery] = useState('')
  const [isOpen, setIsOpen] = useState(false)
  const [results, setResults] = useState<SearchResult[]>([])
  const [selectedIndex, setSelectedIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const router = useRouter()

  useEffect(() => {
    if (query.length < 2) {
      setResults([])
      return
    }

    const lowerQuery = query.toLowerCase()

    // Score and filter results
    const scored = searchIndex
      .map(item => ({ item, score: scoreResult(item, lowerQuery) }))
      .filter(({ score }) => score > 0)
      .sort((a, b) => b.score - a.score)

    // Dedupe by href (keep highest scored)
    const seen = new Set<string>()
    const deduped = scored.filter(({ item }) => {
      if (seen.has(item.href)) return false
      seen.add(item.href)
      return true
    })

    setResults(deduped.slice(0, 10).map(({ item }) => item))
    setSelectedIndex(0)
  }, [query])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'k' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault()
        setIsOpen(true)
        setTimeout(() => inputRef.current?.focus(), 100)
      }
      if (e.key === 'Escape') {
        setIsOpen(false)
        setQuery('')
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIndex(i => Math.min(i + 1, results.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIndex(i => Math.max(i - 1, 0))
    } else if (e.key === 'Enter' && results[selectedIndex]) {
      router.push(results[selectedIndex].href)
      setIsOpen(false)
      setQuery('')
    }
  }

  const handleSelect = (href: string) => {
    router.push(href)
    setIsOpen(false)
    setQuery('')
  }

  return (
    <>
      {/* Search Button */}
      <button
        onClick={() => {
          setIsOpen(true)
          setTimeout(() => inputRef.current?.focus(), 100)
        }}
        className="flex items-center gap-2 w-full px-3 py-2 text-sm text-dark-400 bg-dark-800 border border-dark-700 rounded-lg hover:bg-dark-700 hover:text-white transition-colors"
      >
        <SearchIcon className="w-4 h-4" />
        <span>Search docs...</span>
        <kbd className="ml-auto text-xs bg-dark-700 px-1.5 py-0.5 rounded hidden sm:inline">⌘K</kbd>
      </button>

      {/* Modal */}
      {isOpen && (
        <div className="fixed inset-0 z-[100] flex items-start justify-center pt-[8vh] sm:pt-[15vh] px-4">
          <div className="fixed inset-0 bg-black/60" onClick={() => { setIsOpen(false); setQuery('') }} />
          <div className="relative w-full max-w-xl bg-dark-900 border border-dark-700 rounded-xl shadow-2xl overflow-hidden">
            {/* Input */}
            <div className="flex items-center gap-3 px-4 py-3 border-b border-dark-700">
              <SearchIcon className="w-5 h-5 text-dark-400" />
              <input
                ref={inputRef}
                type="text"
                value={query}
                onChange={e => setQuery(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="Search documentation..."
                className="flex-1 bg-transparent text-base text-white placeholder-dark-500 outline-none"
              />
              {query && (
                <button onClick={() => setQuery('')} className="text-dark-400 hover:text-white">
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* Results */}
            {results.length > 0 && (
              <div className="max-h-96 overflow-y-auto py-2">
                {results.map((result, i) => (
                  <button
                    key={result.href + result.title}
                    onClick={() => handleSelect(result.href)}
                    className={`w-full px-4 py-3 text-left flex items-start gap-3 transition-colors ${
                      i === selectedIndex ? 'bg-primary-500/20' : 'hover:bg-dark-800'
                    }`}
                  >
                    <div className="flex-1 min-w-0">
                      {result.section && (
                        <div className="text-xs text-dark-500 mb-0.5">{result.section}</div>
                      )}
                      <div className="text-white font-medium">{result.title}</div>
                      <div className="text-sm text-dark-400 truncate">{result.description}</div>
                    </div>
                    <span className="text-xs text-dark-500 bg-dark-800 px-2 py-1 rounded shrink-0">
                      {result.category}
                    </span>
                  </button>
                ))}
              </div>
            )}

            {query.length >= 2 && results.length === 0 && (
              <div className="px-4 py-8 text-center text-dark-400">
                No results found for &ldquo;{query}&rdquo;
              </div>
            )}

            {query.length < 2 && (
              <div className="px-4 py-6 text-center text-dark-500 text-sm">
                Type at least 2 characters to search
              </div>
            )}

            {/* Footer */}
            <div className="px-4 py-2 border-t border-dark-700 text-xs text-dark-500 hidden sm:flex gap-4">
              <span><kbd className="bg-dark-700 px-1 rounded">↑↓</kbd> Navigate</span>
              <span><kbd className="bg-dark-700 px-1 rounded">Enter</kbd> Select</span>
              <span><kbd className="bg-dark-700 px-1 rounded">Esc</kbd> Close</span>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
