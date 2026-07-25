'use client'

import { useState, ReactNode } from 'react'
import { Link2, Check } from 'lucide-react'

type HeadingProps = {
  as: 'h2' | 'h3' | 'h4'
  id: string
  children: ReactNode
}

export function Heading({ as: Tag, id, children }: HeadingProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    const url = `${window.location.origin}${window.location.pathname}#${id}`
    navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Tag id={id}>
      {children}
      <button
        onClick={handleCopy}
        className={`anchor-link inline-flex items-center ${copied ? 'copied' : ''}`}
        title={copied ? 'Copied!' : 'Copy link'}
        aria-label="Copy link to section"
      >
        {copied ? (
          <Check className="w-4 h-4" />
        ) : (
          <Link2 className="w-4 h-4" />
        )}
      </button>
    </Tag>
  )
}

// Convenience exports
export function H2({ id, children }: Omit<HeadingProps, 'as'>) {
  return <Heading as="h2" id={id}>{children}</Heading>
}

export function H3({ id, children }: Omit<HeadingProps, 'as'>) {
  return <Heading as="h3" id={id}>{children}</Heading>
}

export function H4({ id, children }: Omit<HeadingProps, 'as'>) {
  return <Heading as="h4" id={id}>{children}</Heading>
}
