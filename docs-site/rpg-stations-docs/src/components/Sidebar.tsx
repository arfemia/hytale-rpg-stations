'use client'

import { clsx } from 'clsx'
import {
  ChevronDown,
  ChevronRight,
  ExternalLink,
  Hammer,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  X,
} from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useState, useEffect } from 'react'
import { nav, type NavItem } from '@/config/docs-nav'
import { Search } from './Search'
import { useSidebar } from './SidebarContext'

const MOD_VERSION = '1.0.0-unreleased'

function NavLink({ item, pathname }: { item: NavItem; pathname: string }) {
  const [isOpen, setIsOpen] = useState(
    item.children?.some((child) => pathname.startsWith(child.href)) ?? false
  )
  const isActive = item.href === pathname
  const Icon = item.icon

  if (item.children) {
    return (
      <div>
        <button
          onClick={() => setIsOpen(!isOpen)}
          className={clsx(
            'flex items-center justify-between w-full px-3 py-2 text-sm rounded-lg transition-colors',
            item.color
              ? `${item.color} hover:brightness-125 hover:bg-dark-800`
              : 'hover:bg-dark-800 text-dark-300 hover:text-white'
          )}
        >
          <span className="flex items-center gap-2">
            {Icon && <Icon className="w-4 h-4" />}
            {item.title}
          </span>
          {isOpen ? (
            <ChevronDown className="w-4 h-4" />
          ) : (
            <ChevronRight className="w-4 h-4" />
          )}
        </button>
        {isOpen && (
          <div className="ml-6 mt-1 space-y-1">
            {item.children.map((child) => (
              <Link
                key={child.href}
                href={child.href}
                className={clsx(
                  'block px-3 py-1.5 text-sm rounded-lg transition-colors',
                  pathname === child.href
                    ? 'bg-primary-500/20 text-primary-400'
                    : 'text-dark-400 hover:text-white hover:bg-dark-800'
                )}
              >
                {child.title}
              </Link>
            ))}
          </div>
        )}
      </div>
    )
  }

  return (
    <Link
      href={item.href!}
      className={clsx(
        'flex items-center gap-2 px-3 py-2 text-sm rounded-lg transition-colors',
        isActive
          ? 'bg-primary-500/20 text-primary-400'
          : item.color
            ? `${item.color} hover:brightness-125 hover:bg-dark-800`
            : 'text-dark-300 hover:text-white hover:bg-dark-800'
      )}
    >
      {Icon && <Icon className="w-4 h-4" />}
      {item.title}
      {item.badge && (
        <span className="ml-auto text-[10px] font-medium px-1.5 py-0.5 rounded bg-primary-500/15 text-primary-400">
          {item.badge}
        </span>
      )}
    </Link>
  )
}

export function Sidebar() {
  const pathname = usePathname()
  const [mobileOpen, setMobileOpen] = useState(false)
  const { collapsed, toggle } = useSidebar()

  // Lock body scroll when mobile sidebar is open
  useEffect(() => {
    if (mobileOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [mobileOpen])

  // Close sidebar on route change
  useEffect(() => {
    setMobileOpen(false)
  }, [pathname])

  return (
    <>
      {/* Mobile toggle */}
      <button
        onClick={() => setMobileOpen(true)}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 bg-dark-800 rounded-lg border border-dark-700"
      >
        <Menu className="w-5 h-5" />
      </button>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="lg:hidden fixed inset-0 bg-black/50 z-40"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Desktop expand button - shown when sidebar is collapsed */}
      {collapsed && (
        <button
          onClick={toggle}
          className="hidden lg:flex fixed top-4 left-4 z-40 p-2 bg-dark-800 hover:bg-dark-700 rounded-lg border border-dark-700 transition-colors"
          title="Show sidebar"
        >
          <PanelLeftOpen className="w-5 h-5 text-dark-300" />
        </button>
      )}

      {/* Sidebar */}
      <aside
        className={clsx(
          'fixed top-0 left-0 z-50 h-full w-72 bg-dark-900 border-r border-dark-700 overflow-y-auto',
          'transition-transform duration-200',
          mobileOpen ? 'translate-x-0' : collapsed ? '-translate-x-full' : '-translate-x-full lg:translate-x-0'
        )}
      >
        {/* Header */}
        <div className="sticky top-0 bg-dark-900 border-b border-dark-700 p-4">
          <div className="flex items-center justify-between">
            <Link href="/" className="flex items-center gap-2">
              <div className="w-8 h-8 bg-primary-500 rounded-lg flex items-center justify-center">
                <Hammer className="w-5 h-5 text-white" />
              </div>
              <div>
                <div className="font-bold text-white">RPG Stations</div>
                <div className="text-xs text-dark-400">v{MOD_VERSION}</div>
              </div>
            </Link>
            <div className="flex items-center gap-1">
              <button
                onClick={toggle}
                className="hidden lg:block p-1 hover:bg-dark-800 rounded text-dark-400 hover:text-white transition-colors"
                title="Hide sidebar"
              >
                <PanelLeftClose className="w-5 h-5" />
              </button>
              <button
                onClick={() => setMobileOpen(false)}
                className="lg:hidden p-1 hover:bg-dark-800 rounded"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>
        </div>

        {/* Search */}
        <div className="p-4 pb-2">
          <Search />
        </div>

        {/* Navigation */}
        <nav className="p-4 pt-2 space-y-1">
          {nav.map((item) => (
            <NavLink key={item.title} item={item} pathname={pathname} />
          ))}
        </nav>

        {/* External Links */}
        <div className="p-4 border-t border-dark-700">
          <div className="text-xs font-semibold text-dark-500 uppercase mb-2">Links</div>
          <div className="space-y-1">
            <a
              href="https://github.com/arfemia/hytale-rpg-stations"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 px-3 py-2 text-sm text-dark-400 hover:text-white hover:bg-dark-800 rounded-lg transition-colors"
            >
              GitHub
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>
      </aside>
    </>
  )
}
