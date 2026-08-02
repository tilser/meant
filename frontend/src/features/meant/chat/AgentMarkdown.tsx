import { Fragment, type ReactNode, useId, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

import type { Product } from '../types'
import { ChevronIcon } from '../shared/icons'
import { productArtworkUrl } from '../shared/productArtwork'

const COMPACT_MESSAGE_MIN_LENGTH = 420
const COMPACT_PREVIEW_MAX_LENGTH = 280

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

function inlineText(children: ReactNode): string | null {
  if (typeof children === 'string' || typeof children === 'number') {
    return String(children)
  }
  if (!Array.isArray(children)) {
    return null
  }

  const parts = children.map(inlineText)
  return parts.every((part): part is string => part !== null) ? parts.join('') : null
}

function normalizedProductTitle(value: string): string {
  return value.normalize('NFKC').replace(/\s+/g, ' ').trim().toLocaleLowerCase()
}

function normalizedProductReferenceTitle(value: string): string {
  return normalizedProductTitle(value).replace(/^\d{1,3}[.)]\s+/, '')
}

function productTitlePattern(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+')
}

function isWordCharacter(value: string | undefined): boolean {
  return value ? /[\p{L}\p{N}]/u.test(value) : false
}

function ProductMentionArtwork({ product }: Readonly<{ product: Product }>) {
  const artworkUrl = productArtworkUrl(product)
  const fallbackLabel = product.name.trim().charAt(0).toLocaleUpperCase() || 'M'

  return (
    <span className="mt-agent-product-link-media" aria-hidden>
      {artworkUrl ? (
        <img
          className="mt-agent-product-link-image"
          src={artworkUrl}
          alt=""
          loading="lazy"
          decoding="async"
        />
      ) : (
        <span className="mt-agent-product-link-fallback" style={{ backgroundColor: product.tone }}>
          {fallbackLabel}
        </span>
      )}
    </span>
  )
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
  onOpenProduct?: (product: Product) => void
}>) {
  const [expanded, setExpanded] = useState(false)
  const contentId = useId()
  const collapsible = compact && text.trim().length >= COMPACT_MESSAGE_MIN_LENGTH
  const collapsed = collapsible && !expanded
  const renderedText = collapsed ? compactAgentPreview(text) : text
  const productByTitle = new Map<string, Product | null>()
  products.forEach((product) => {
    const title = normalizedProductTitle(product.name)
    if (!title) return
    if (!productByTitle.has(title)) {
      productByTitle.set(title, product)
    } else if (productByTitle.get(title)?.id !== product.id) {
      productByTitle.set(title, null)
    }
  })
  const groundedProduct = (children: ReactNode): Product | null => {
    const title = inlineText(children)
    return title ? (productByTitle.get(normalizedProductReferenceTitle(title)) ?? null) : null
  }
  const productAction = (product: Product, children: ReactNode, key?: string) => (
    <button
      key={key}
      className="mt-agent-product-link"
      type="button"
      data-product-id={product.id}
      aria-label={`Open ${product.name} in Meant`}
      onClick={(event) => {
        event.preventDefault()
        event.stopPropagation()
        onOpenProduct?.(product)
      }}
      onDragStart={(event) => event.stopPropagation()}
    >
      <ProductMentionArtwork product={product} />
      <span className="mt-agent-product-link-copy">
        <span className="mt-agent-product-link-title">{children}</span>
        <span className="mt-agent-product-link-hint" aria-hidden>
          View product
          <ChevronIcon direction="right" size={10} />
        </span>
      </span>
    </button>
  )
  const mentionProducts = [...productByTitle.entries()]
    .filter((entry): entry is [string, Product] => entry[1] !== null)
    .map(([, product]) => product)
    .sort((left, right) => right.name.length - left.name.length)
  const mentionPattern = mentionProducts.length
    ? new RegExp(
        mentionProducts.map((product) => productTitlePattern(product.name)).join('|'),
        'giu',
      )
    : null
  const productMentions = (children: ReactNode): ReactNode => {
    if (Array.isArray(children)) {
      return children.map((child) => productMentions(child))
    }
    if (typeof children !== 'string' || !mentionPattern || !onOpenProduct) {
      return children
    }

    const mentions: ReactNode[] = []
    let cursor = 0
    mentionPattern.lastIndex = 0
    for (const match of children.matchAll(mentionPattern)) {
      const start = match.index
      const value = match[0]
      const end = start + value.length
      if (isWordCharacter(children[start - 1]) || isWordCharacter(children[end])) {
        continue
      }
      const product = productByTitle.get(normalizedProductTitle(value))
      if (!product) continue
      if (start > cursor) mentions.push(children.slice(cursor, start))
      mentions.push(productAction(product, value, `${product.id}-${start}`))
      cursor = end
    }
    if (cursor === 0) return children
    if (cursor < children.length) mentions.push(children.slice(cursor))
    return mentions
  }

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
          remarkPlugins={[remarkGfm]}
          components={{
            p: ({ children }) => <p>{productMentions(children)}</p>,
            li: ({ children }) => <li>{productMentions(children)}</li>,
            a: ({ children, href }) => {
              const product = groundedProduct(children)
              if (product && onOpenProduct) {
                return productAction(product, children)
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
            strong: ({ children }) => {
              const product = groundedProduct(children)
              return (
                <strong>
                  {product && onOpenProduct ? productAction(product, children) : children}
                </strong>
              )
            },
            table: ({ children }) => (
              <div
                className="mt-agent-markdown-table"
                role="region"
                aria-label="Scrollable table"
                tabIndex={0}
              >
                <table>{children}</table>
              </div>
            ),
            td: ({ children, node, ...props }) => {
              void node
              return <td {...props}>{productMentions(children)}</td>
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
