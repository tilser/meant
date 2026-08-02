import { Fragment, useId, useState } from 'react'
import Markdown from 'react-markdown'

import type { Product } from '../types'
import { ChevronIcon } from '../shared/icons'

const COMPACT_MESSAGE_MIN_LENGTH = 420
const COMPACT_PREVIEW_MAX_LENGTH = 280
const INTERNAL_PRODUCT_LINK_PREFIX = 'meant:product:'
const CANONICAL_PRODUCT_KEY = /^[A-Za-z0-9:_-]{1,500}$/

function compactAgentPreview(text: string): string {
  if (text.length <= COMPACT_PREVIEW_MAX_LENGTH) {
    return text
  }

  const candidate = text.slice(0, COMPACT_PREVIEW_MAX_LENGTH + 1)
  const lastLineBreak = candidate.lastIndexOf('\n')
  const lastSpace = candidate.lastIndexOf(' ')
  const boundary = lastLineBreak >= COMPACT_PREVIEW_MAX_LENGTH * 0.6 ? lastLineBreak : lastSpace

  return `${candidate
    .slice(0, boundary > 0 ? boundary : COMPACT_PREVIEW_MAX_LENGTH)
    .trimEnd()}\n\n…`
}

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

function internalProductKey(href: string | undefined): string | null {
  if (!href?.startsWith(INTERNAL_PRODUCT_LINK_PREFIX)) return null

  const key = href.slice(INTERNAL_PRODUCT_LINK_PREFIX.length)
  return CANONICAL_PRODUCT_KEY.test(key) ? key : null
}

/** Renders the agent's buyer-safe Markdown without allowing raw HTML or remote images. */
export function AgentMarkdown({
  text,
  compact = false,
  products = [],
  onOpenProduct,
}: Readonly<{
  text: string
  compact?: boolean
  products?: readonly Product[]
  onOpenProduct?: (product: Product, products: readonly Product[]) => void
}>) {
  const [expanded, setExpanded] = useState(false)
  const contentId = useId()
  const collapsible = compact && text.trim().length >= COMPACT_MESSAGE_MIN_LENGTH
  const collapsed = collapsible && !expanded
  const renderedText = collapsed ? compactAgentPreview(text) : text
  const productByCanonicalKey = new Map(
    products.flatMap((product) => {
      const key = product.canonicalProduct?.key
      return key ? [[key, product] as const] : []
    }),
  )

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
          urlTransform={(url) => url}
          components={{
            a: ({ children, href }) => {
              const productKey = internalProductKey(href)
              const product = productKey ? productByCanonicalKey.get(productKey) : undefined
              if (product && onOpenProduct) {
                return (
                  <button
                    className="mt-agent-product-link"
                    type="button"
                    data-product-key={productKey}
                    aria-label={`Open ${product.name} in Meant`}
                    onClick={(event) => {
                      event.preventDefault()
                      event.stopPropagation()
                      onOpenProduct(product, products)
                    }}
                    onDragStart={(event) => event.stopPropagation()}
                  >
                    {children}
                  </button>
                )
              }
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
          {renderedText}
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
