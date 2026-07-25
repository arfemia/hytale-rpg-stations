import { Metadata } from 'next'
import { DocPage } from '@/components/DocPage'
import { MarkdownContent } from '@/components/MarkdownContent'
import { getPatchNotes } from '@/lib/changelog'

export const metadata: Metadata = {
  title: 'Patch Notes',
  description: 'User-friendly release notes for RPG Stations.',
}

export default function PatchNotesPage() {
  const sections = getPatchNotes()

  return (
    <DocPage
      title="Patch Notes"
      description="User-friendly release notes"
      breadcrumbs={[{ title: 'Patch Notes' }]}
    >
      <p className="text-dark-300 mb-8">
        For the full technical history, see the <a href="/docs/changelog/" className="text-primary-400 hover:underline">Changelog</a>.
      </p>

      {sections.length === 0 && (
        <p className="text-dark-400">
          No patch notes yet. RPG Stations has not adopted a per-version <code>patch-notes/</code> convention;
          see the <a href="/docs/changelog/" className="text-primary-400 hover:underline">Changelog</a> instead.
        </p>
      )}

      {sections.map((section, i) => (
        <div key={i} className="mb-8 pb-8 border-b border-dark-700 last:border-0">
          <h2 id={section.versionNumber} className="text-xl font-bold text-primary-400 mb-4">
            {section.version}
          </h2>
          <MarkdownContent content={section.content} />
        </div>
      ))}
    </DocPage>
  )
}
