const fs = require('fs')
const path = require('path')

// Sync build-time docs sources from the rpg-stations mod root into src/data:
//  - CHANGELOG.md  -> developer changelog (/docs/changelog)
//  - CURSEFORGE.md -> CurseForge listing source (once authored)
//  - patch-notes/  -> user-facing per-version notes (/docs/patch-notes), once
//    the mod adopts the same per-version note convention as the MMO jar.
//
// The 3-levels-up constant mirrors the MMO docs site's copy-docs.js: from
// scripts/ this resolves rpg-stations-docs -> docs-site -> the rpg-stations
// mod root, the same relative depth as hyMMO's mmo-skill-tree-docs/scripts ->
// docs-site -> the hyMMO repo root.
const srcDir = path.join(__dirname, '..', '..', '..')
const destDir = path.join(__dirname, '..', 'src', 'data')

if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true })
}

// Single files
for (const file of ['CHANGELOG.md', 'CURSEFORGE.md']) {
  const srcPath = path.join(srcDir, file)
  if (fs.existsSync(srcPath)) {
    fs.copyFileSync(srcPath, path.join(destDir, file))
    console.log(`Copied ${file} to src/data/`)
  } else {
    console.warn(`Warning: ${file} not found at ${srcPath}`)
  }
}

// patch-notes/ directory (per-version user-facing notes)
const pnSrc = path.join(srcDir, 'patch-notes')
const pnDest = path.join(destDir, 'patch-notes')
if (fs.existsSync(pnSrc)) {
  fs.rmSync(pnDest, { recursive: true, force: true })
  fs.mkdirSync(pnDest, { recursive: true })
  let count = 0
  for (const f of fs.readdirSync(pnSrc)) {
    if (f.endsWith('.md')) {
      fs.copyFileSync(path.join(pnSrc, f), path.join(pnDest, f))
      count++
    }
  }
  console.log(`Copied patch-notes/ to src/data/patch-notes/ (${count} files)`)
} else {
  console.warn(`Warning: patch-notes/ not found at ${pnSrc}`)
}

console.log('Documentation files synced successfully!')
