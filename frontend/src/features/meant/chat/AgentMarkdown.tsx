import {
  cloneElement,
  Fragment,
  isValidElement,
  type ReactElement,
  type ReactNode,
  useId,
  useState,
} from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

import type { Product } from '../types'
import { ChevronIcon } from '../shared/icons'
import { productArtworkUrl } from '../shared/productArtwork'

const COMPACT_MESSAGE_MIN_LENGTH = 420
const COMPACT_PREVIEW_MAX_LENGTH = 280
const PRODUCT_GROUNDING_BOUNDARY = ' meantproductgroundingboundary '

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

function isProductActionElement(
  value: ReactNode,
): value is ReactElement<{ children?: ReactNode; 'data-product-id'?: string }> {
  return (
    isValidElement<{ children?: ReactNode; 'data-product-id'?: string }>(value) &&
    typeof value.props['data-product-id'] === 'string'
  )
}

function containsProductAction(children: ReactNode): boolean {
  if (isProductActionElement(children)) return true
  if (Array.isArray(children)) return children.some(containsProductAction)
  return isValidElement<{ children?: ReactNode }>(children)
    ? containsProductAction(children.props.children)
    : false
}

function inlineText(children: ReactNode): string | null {
  if (typeof children === 'string' || typeof children === 'number') {
    return String(children)
  }
  if (isProductActionElement(children)) return null
  if (isValidElement<{ children?: ReactNode }>(children)) {
    return inlineText(children.props.children)
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

interface ProductTitleToken {
  value: string
  start: number
  end: number
}

interface ProductMentionRange {
  product: Product
  start: number
  end: number
}

function productTitleTokens(value: string): ProductTitleToken[] {
  return [...value.matchAll(/[\p{L}\p{N}]+/gu)].map((match) => ({
    value: match[0].normalize('NFKC').toLocaleLowerCase(),
    start: match.index,
    end: match.index + match[0].length,
  }))
}

function skipsProductGrounding(value: ReactNode): boolean {
  if (isProductActionElement(value)) return true
  return (
    isValidElement<{ children?: ReactNode }>(value) &&
    typeof value.type === 'string' &&
    (value.type === 'code' || value.type === 'pre')
  )
}

function productGroundingText(children: ReactNode): string {
  if (typeof children === 'string' || typeof children === 'number') return String(children)
  if (skipsProductGrounding(children)) return PRODUCT_GROUNDING_BOUNDARY
  if (Array.isArray(children)) return children.map(productGroundingText).join('')
  return isValidElement<{ children?: ReactNode }>(children)
    ? productGroundingText(children.props.children)
    : ''
}

function productMentionRanges(
  children: ReactNode,
  products: readonly Product[],
): ProductMentionRange[] {
  const textTokens = productTitleTokens(productGroundingText(children))
  if (textTokens.length === 0 || products.length === 0) return []

  const productByTokenTitle = new Map<string, Product | null>()
  for (const product of products) {
    const signature = productTitleTokens(product.name)
      .map((token) => token.value)
      .join('\u0000')
    if (!signature) continue
    const existing = productByTokenTitle.get(signature)
    if (existing === undefined) {
      productByTokenTitle.set(signature, product)
    } else if (existing?.id !== product.id) {
      productByTokenTitle.set(signature, null)
    }
  }
  const candidates = [...productByTokenTitle.entries()]
    .filter((entry): entry is [string, Product] => entry[1] !== null)
    .map(([signature, product]) => ({ product, tokens: signature.split('\u0000') }))
    .sort(
      (left, right) =>
        right.tokens.length - left.tokens.length ||
        right.tokens.join('').length - left.tokens.join('').length,
    )

  const ranges: ProductMentionRange[] = []
  for (let tokenIndex = 0; tokenIndex < textTokens.length;) {
    const candidate = candidates.find(
      ({ tokens }) =>
        tokens.length <= textTokens.length - tokenIndex &&
        tokens.every((token, offset) => token === textTokens[tokenIndex + offset]?.value),
    )
    if (!candidate) {
      tokenIndex += 1
      continue
    }
    const firstToken = textTokens[tokenIndex]
    const lastToken = textTokens[tokenIndex + candidate.tokens.length - 1]
    if (firstToken && lastToken) {
      ranges.push({
        product: candidate.product,
        start: firstToken.start,
        end: lastToken.end,
      })
    }
    tokenIndex += candidate.tokens.length
  }
  return ranges
}

function replaceProductMentionRanges(
  children: ReactNode,
  ranges: readonly ProductMentionRange[],
  renderProduct: (range: ProductMentionRange) => ReactNode,
): ReactNode {
  if (ranges.length === 0) return children

  let textOffset = 0
  const insertedRanges = new Set<ProductMentionRange>()
  const replace = (value: ReactNode): ReactNode => {
    if (typeof value === 'string' || typeof value === 'number') {
      const text = String(value)
      const segmentStart = textOffset
      const segmentEnd = segmentStart + text.length
      textOffset = segmentEnd
      const overlapping = ranges.filter(
        (range) => range.start < segmentEnd && range.end > segmentStart,
      )
      if (overlapping.length === 0) return value

      const parts: ReactNode[] = []
      let localOffset = 0
      for (const range of overlapping) {
        const overlapStart = Math.max(range.start, segmentStart) - segmentStart
        const overlapEnd = Math.min(range.end, segmentEnd) - segmentStart
        if (overlapStart > localOffset) parts.push(text.slice(localOffset, overlapStart))
        if (!insertedRanges.has(range)) {
          parts.push(renderProduct(range))
          insertedRanges.add(range)
        }
        localOffset = Math.max(localOffset, overlapEnd)
      }
      if (localOffset < text.length) parts.push(text.slice(localOffset))
      return parts
    }
    if (skipsProductGrounding(value)) {
      textOffset += PRODUCT_GROUNDING_BOUNDARY.length
      return value
    }
    if (Array.isArray(value)) return value.map(replace)
    if (isValidElement<{ children?: ReactNode }>(value)) {
      return cloneElement(value, undefined, replace(value.props.children))
    }
    return value
  }
  return replace(children)
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
  const productMentions = (children: ReactNode): ReactNode => {
    if (!onOpenProduct) return children
    return replaceProductMentionRanges(
      children,
      productMentionRanges(children, mentionProducts),
      (range) =>
        productAction(range.product, range.product.name, `${range.product.id}-${range.start}`),
    )
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
            h1: ({ children }) => <h1>{productMentions(children)}</h1>,
            h2: ({ children }) => <h2>{productMentions(children)}</h2>,
            h3: ({ children }) => <h3>{productMentions(children)}</h3>,
            h4: ({ children }) => <h4>{productMentions(children)}</h4>,
            h5: ({ children }) => <h5>{productMentions(children)}</h5>,
            h6: ({ children }) => <h6>{productMentions(children)}</h6>,
            a: ({ children, href }) => {
              if (containsProductAction(children)) {
                return <Fragment>{children}</Fragment>
              }
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
            th: ({ children, node, ...props }) => {
              void node
              return <th {...props}>{productMentions(children)}</th>
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
