import fs from 'fs'
import path from 'path'

export type ChangelogSection = {
  version: string
  versionNumber: string
  content: string
}

function readChangelog(): string {
  const changelogPath = path.join(process.cwd(), 'src', 'data', 'CHANGELOG.md')
  try {
    return fs.readFileSync(changelogPath, 'utf-8')
  } catch {
    return ''
  }
}

function compareSemverDesc(a: string, b: string): number {
  const pa = a.split('.').map(x => (/^\d+$/.test(x) ? Number(x) : -1))
  const pb = b.split('.').map(x => (/^\d+$/.test(x) ? Number(x) : -1))
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const diff = (pb[i] ?? 0) - (pa[i] ?? 0)
    if (diff !== 0) return diff
  }
  return 0
}

export function parseChangelog(markdown: string): ChangelogSection[] {
  const sections: ChangelogSection[] = []
  const lines = markdown.split('\n')
  let currentVersion = ''
  let currentContent: string[] = []

  const flush = () => {
    if (currentVersion) {
      sections.push({
        version: currentVersion,
        versionNumber: currentVersion.replace(/^v/, '').replace(/ - .*$/, ''),
        content: currentContent.join('\n'),
      })
    }
    currentVersion = ''
    currentContent = []
  }

  for (const line of lines) {
    if (line.startsWith('## v')) {
      flush()
      currentVersion = line.replace('## ', '').trim()
    } else if (line.startsWith('## ')) {
      // Non-version H2 (e.g. "## Future / Unreleased ...") ends the current
      // version block so unreleased sections never leak into a released version.
      flush()
    } else if (currentVersion) {
      currentContent.push(line)
    }
  }
  flush()
  return sections
}

// ---------------------------------------------------------------------------
// User-facing patch notes (mod-root patch-notes/<version>.md, synced to
// src/data/patch-notes/ by copy-docs.js), once the mod adopts that convention.
// ---------------------------------------------------------------------------

function parseFrontmatter(raw: string): { fm: Record<string, string>; body: string } {
  const m = raw.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/)
  if (!m) return { fm: {}, body: raw }
  const fm: Record<string, string> = {}
  for (const line of m[1].split(/\r?\n/)) {
    const idx = line.indexOf(':')
    if (idx > 0) fm[line.slice(0, idx).trim()] = line.slice(idx + 1).trim()
  }
  return { fm, body: m[2] }
}

export function getPatchNotes(): ChangelogSection[] {
  const dir = path.join(process.cwd(), 'src', 'data', 'patch-notes')
  let files: string[] = []
  try {
    files = fs.readdirSync(dir).filter(f => /^\d/.test(f) && f.endsWith('.md'))
  } catch {
    return []
  }
  const sections = files
    .map(f => {
      const { fm, body } = parseFrontmatter(fs.readFileSync(path.join(dir, f), 'utf-8'))
      const versionNumber = fm.version || f.replace(/\.md$/, '')
      const content = body.split(/\r?\n## Related/)[0].trim()
      return { version: fm.title || `v${versionNumber}`, versionNumber, content, status: fm.status || 'released' }
    })
    .filter(s => s.status === 'released')
    .map(({ version, versionNumber, content }) => ({ version, versionNumber, content }))
  return sections.sort((a, b) => compareSemverDesc(a.versionNumber, b.versionNumber))
}

// ---------------------------------------------------------------------------
// Developer changelog (mod-root CHANGELOG.md) - powers the /docs/changelog route.
// No database merge here (static export, no commerce/API stack).
// ---------------------------------------------------------------------------

export function getAllVersions(): ChangelogSection[] {
  const markdown = readChangelog()
  if (!markdown) return []
  return parseChangelog(markdown)
}
