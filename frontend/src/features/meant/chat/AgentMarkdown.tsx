import { Fragment, useId, useState } from 'react'
import Markdown from 'react-markdown'

import { ChevronIcon } from '../shared/icons'

const COMPACT_MESSAGE_MIN_LENGTH = 420

function safeAgentLink(href: string | undefined): string | null {
  if (!href) return null

  try {
    const url = new URL(href)
    const host = url.hostname.toLocaleLowerCase()
    const path = url.pathname.replace(/\/+$/, '') || '/'
    const transportPath = [
      '/.well-known/ucp.json',
      '/.well-known/ucp',
      '/api/ucp/mcp',
      '/api/mcp',
      '/mcp',
    ].some((value) => path === value || path.startsWith(`${value}/`))
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      host.startsWith('mcp.') ||
      host.includes('.mcp.') ||
      host === 'myshopify.com' ||
      host.endsWith('.myshopify.com') ||
      transportPath
    ) {
      return null
    }
    return url.toString()
  } catch {
    return null
  }
}

/** Renders the agent's buyer-safe Markdown without allowing raw HTML or remote images. */
export function AgentMarkdown({
  text,
  compact = false,
}: Readonly<{ text: string; compact?: boolean }>) {
  const [expanded, setExpanded] = useState(false)
  const contentId = useId()
  const collapsible = compact && text.trim().length >= COMPACT_MESSAGE_MIN_LENGTH

  return (
    <div
      className={`mt-ct-intro mt-agent-markdown ${
        collapsible ? (expanded ? 'is-expanded' : 'is-collapsed') : ''
      }`}
    >
      <div className="mt-agent-markdown-content" id={contentId}>
        <Markdown
          skipHtml
          disallowedElements={['img']}
          components={{
            a: ({ children, href }) => {
              const safeHref = safeAgentLink(href)
              return safeHref ? (
                <a href={safeHref} target="_blank" rel="noreferrer">
                  {children}
                </a>
              ) : (
                <Fragment>{children}</Fragment>
              )
            },
          }}
        >
          {text}
        </Markdown>
      </div>
      {collapsible ? (
        <button
          className="mt-agent-markdown-toggle"
          type="button"
          aria-expanded={expanded}
          aria-controls={contentId}
          draggable={false}
          onClick={() => setExpanded((value) => !value)}
          onDragStart={(event) => event.stopPropagation()}
        >
          {expanded ? 'Show less' : 'Show full message'}
          <ChevronIcon direction="right" size={13} />
        </button>
      ) : null}
    </div>
  )
}
