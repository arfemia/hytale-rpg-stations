import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { MarkdownContent } from '@/components/MarkdownContent'
import { getAllVersions } from '@/lib/changelog'

export const metadata: Metadata = {
  title: 'Changelog',
  description: 'Complete version history and release notes for RPG Stations.',
}

export default function ChangelogPage() {
  const sections = getAllVersions()

  return (
    <DocPage
      title="Changelog"
      description="Complete version history"
      breadcrumbs={[{ title: 'Changelog' }]}
    >
      <p className="text-dark-300 mb-8">
        Full release history with all features, fixes, and technical changes, synced from the
        mod root&apos;s <code>CHANGELOG.md</code> at build time.
      </p>

      {sections.length === 0 && (
        <p className="text-dark-400">Changelog file not found. Run <code>npm run sync-docs</code> first.</p>
      )}

      {sections.map((section, i) => (
        <div key={i} className="mb-8 pb-8 border-b border-dark-700 last:border-0">
          <h2 id={section.version.toLowerCase().replace(/[^a-z0-9]/g, '-')} className="text-xl font-bold text-primary-400 mb-4">
            {section.version}
          </h2>
          <MarkdownContent content={section.content} />
        </div>
      ))}
    </DocPage>
  )
}
