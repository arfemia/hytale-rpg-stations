import { ReactNode } from 'react'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'

type Breadcrumb = {
  title: string
  href?: string
}

type DocPageProps = {
  title: string
  description?: string
  breadcrumbs?: Breadcrumb[]
  children: ReactNode
  prevPage?: { title: string; href: string }
  nextPage?: { title: string; href: string }
}

export function DocPage({
  title,
  description,
  breadcrumbs,
  children,
  prevPage,
  nextPage,
}: DocPageProps) {
  return (
    <article className="doc-content">
      {/* Breadcrumbs */}
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav className="flex flex-wrap items-center gap-1 text-sm text-dark-400 mb-4">
          <Link href="/" className="hover:text-white">
            Docs
          </Link>
          {breadcrumbs.map((crumb, i) => (
            <span key={i} className="flex items-center gap-1">
              <ChevronRight className="w-4 h-4" />
              {crumb.href ? (
                <Link href={crumb.href} className="hover:text-white">
                  {crumb.title}
                </Link>
              ) : (
                <span className="text-dark-300">{crumb.title}</span>
              )}
            </span>
          ))}
        </nav>
      )}

      {/* Header */}
      <header className="mb-8">
        <h1 className="text-3xl font-bold mb-2">{title}</h1>
        {description && <p className="text-lg text-dark-400">{description}</p>}
      </header>

      {/* Content */}
      <div className="prose prose-invert max-w-none min-w-0 overflow-x-hidden">{children}</div>

      {/* Navigation */}
      {(prevPage || nextPage) && (
        <nav className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mt-12 pt-6 border-t border-dark-700">
          {prevPage ? (
            <Link
              href={prevPage.href}
              className="group flex flex-col items-start"
            >
              <span className="text-xs text-dark-500 mb-1">Previous</span>
              <span className="text-primary-400 group-hover:text-primary-300">
                ← {prevPage.title}
              </span>
            </Link>
          ) : (
            <div className="hidden sm:block" />
          )}
          {nextPage && (
            <Link
              href={nextPage.href}
              className="group flex flex-col items-start sm:items-end"
            >
              <span className="text-xs text-dark-500 mb-1">Next</span>
              <span className="text-primary-400 group-hover:text-primary-300">
                {nextPage.title} →
              </span>
            </Link>
          )}
        </nav>
      )}
    </article>
  )
}
