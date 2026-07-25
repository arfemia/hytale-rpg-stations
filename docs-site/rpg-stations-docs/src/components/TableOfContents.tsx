'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'

type TOCItem = {
  id: string
  title: string
  level: number
}

export function TableOfContents({ items }: { items: TOCItem[] }) {
  const [activeId, setActiveId] = useState<string>('')

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id)
          }
        })
      },
      { rootMargin: '-100px 0% -80% 0%' }
    )

    items.forEach((item) => {
      const element = document.getElementById(item.id)
      if (element) observer.observe(element)
    })

    return () => observer.disconnect()
  }, [items])

  if (items.length === 0) return null

  return (
    <nav className="hidden xl:block fixed right-8 top-24 w-56">
      <div className="text-xs font-semibold text-dark-500 uppercase mb-3">
        On This Page
      </div>
      <ul className="space-y-2 text-sm">
        {items.map((item) => (
          <li
            key={item.id}
            style={{ paddingLeft: `${(item.level - 2) * 12}px` }}
          >
            <a
              href={`#${item.id}`}
              className={clsx(
                'block py-1 transition-colors',
                activeId === item.id
                  ? 'text-primary-400'
                  : 'text-dark-400 hover:text-white'
              )}
            >
              {item.title}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  )
}
