import { Fragment } from 'react'
import Markdown from 'react-markdown'

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
export function AgentMarkdown({ text }: Readonly<{ text: string }>) {
  return (
    <div className="mt-ct-intro mt-agent-markdown">
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
  )
}
