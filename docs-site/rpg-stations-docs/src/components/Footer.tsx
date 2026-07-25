export function Footer() {
  return (
    <footer className="border-t border-dark-700 px-4 py-8 lg:px-8 text-dark-400 text-sm">
      <div className="text-center">
        <p>RPG Stations by <a href="https://wintergreen-solutions.com" className="text-primary-400 hover:underline">Wintergreen Solutions</a></p>
        <p className="mt-1">Not affiliated with Hypixel Studios or Hytale.</p>
        <p className="mt-4">
          <a href="https://github.com/arfemia/hytale-rpg-stations" target="_blank" rel="noopener noreferrer" className="text-primary-400 hover:underline">GitHub</a>
          {' · '}
          <a href="/docs/patch-notes/" className="text-primary-400 hover:underline">Patch Notes</a>
        </p>
      </div>
    </footer>
  )
}
