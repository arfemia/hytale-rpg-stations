function formatInline(text: string) {
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code class="bg-dark-700 px-1 rounded">$1</code>')
    .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" class="text-primary-400 hover:underline">$1</a>')
}

export function MarkdownContent({ content }: { content: string }) {
  const lines = content.split('\n')
  const elements: React.ReactNode[] = []
  let inList = false
  let listItems: string[] = []
  let inCodeBlock = false
  let codeLines: string[] = []
  let key = 0

  const flushList = () => {
    if (listItems.length > 0) {
      elements.push(
        <ul key={key++} className="list-disc list-inside space-y-1 mb-4">
          {listItems.map((item, i) => (
            <li key={i} dangerouslySetInnerHTML={{ __html: formatInline(item) }} />
          ))}
        </ul>
      )
      listItems = []
    }
    inList = false
  }

  const flushCode = () => {
    if (codeLines.length > 0) {
      elements.push(
        <pre key={key++} className="bg-dark-800 p-4 rounded-lg overflow-x-auto mb-4">
          <code>{codeLines.join('\n')}</code>
        </pre>
      )
      codeLines = []
    }
    inCodeBlock = false
  }

  for (const line of lines) {
    if (line.startsWith('```')) {
      if (inCodeBlock) {
        flushCode()
      } else {
        flushList()
        inCodeBlock = true
      }
      continue
    }

    if (inCodeBlock) {
      codeLines.push(line)
      continue
    }

    if (line.startsWith('### ')) {
      flushList()
      elements.push(
        <h3 key={key++} className="text-lg font-semibold mt-6 mb-3 text-white">
          {line.replace('### ', '')}
        </h3>
      )
    } else if (line.startsWith('#### ')) {
      flushList()
      elements.push(
        <h4 key={key++} className="text-base font-semibold mt-4 mb-2 text-dark-200">
          {line.replace('#### ', '')}
        </h4>
      )
    } else if (line.startsWith('> ')) {
      flushList()
      elements.push(
        <blockquote key={key++} className="border-l-4 border-yellow-500 bg-yellow-500/10 p-4 mb-4 rounded-r">
          <span dangerouslySetInnerHTML={{ __html: formatInline(line.replace('> ', '')) }} />
        </blockquote>
      )
    } else if (line.startsWith('- ') || line.startsWith('* ')) {
      inList = true
      listItems.push(line.replace(/^[-*] /, ''))
    } else if (line.startsWith('  - ') || line.startsWith('  * ')) {
      listItems.push('&nbsp;&nbsp;&nbsp;&nbsp;' + line.replace(/^\s+[-*] /, ''))
    } else if (line.startsWith('    - ') || line.startsWith('    * ')) {
      listItems.push('&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' + line.replace(/^\s+[-*] /, ''))
    } else if (line.startsWith('---')) {
      flushList()
    } else if (line.trim() === '') {
      flushList()
    } else if (line.trim()) {
      flushList()
      elements.push(
        <p key={key++} className="mb-4" dangerouslySetInnerHTML={{ __html: formatInline(line) }} />
      )
    }
  }
  flushList()
  flushCode()

  return <>{elements}</>
}
