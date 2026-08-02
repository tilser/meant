import type { CartItem, Product, ProductId, UserLocation } from '../types'
import { cartItemIdentity, money, productPriceFrom } from '../utils'
import type {
  DiscoverChatBlock,
  DiscoverChatMessage,
  DiscoverChatThread,
  MiniCompareRow,
} from './types'
import { merchantAdjacentDisplayLabel, merchantDisplayOrigin } from '../cart/merchantOrigin'
import { plainAgentText } from '../agent/agentText'

type ProductOpenHandler = (
  product: Product,
  products?: readonly Product[],
  researchQuery?: string | null,
) => void

export function discoverProductResearchQuery(
  block: DiscoverChatBlock,
  messageQuery: string | null | undefined,
): string | null {
  const blockQuery =
    block.type === 'products' || block.type === 'similar' ? block.query?.trim() : null
  return blockQuery || messageQuery?.trim() || null
}

export function productOpenWithResearchQuery(
  onOpen: ProductOpenHandler,
  researchQuery: string | null | undefined,
): (product: Product, products?: readonly Product[]) => void {
  const normalizedQuery = researchQuery?.trim() || null
  return (product, products) => onOpen(product, products, normalizedQuery)
}

function winningIndex(values: readonly (number | null)[], higherIsBetter: boolean): number {
  return values.reduce<number>((bestIndex, value, index) => {
    if (value == null) {
      return bestIndex
    }
    const bestValue = values[bestIndex]
    if (bestValue == null) {
      return index
    }
    return higherIsBetter
      ? value > bestValue
        ? index
        : bestIndex
      : value < bestValue
        ? index
        : bestIndex
  }, 0)
}

export function createMiniCompareBlock(
  products: readonly Product[],
  deliveryLocations: readonly UserLocation[],
): Extract<DiscoverChatBlock, { type: 'minicompare' }> | null {
  const nextProducts = products.slice(0, 4)
  if (nextProducts.length < 2) {
    return null
  }

  const prices = nextProducts.map((product) => productPriceFrom(product, deliveryLocations))
  const priceCurrencies = new Set(
    nextProducts
      .map((product) => product.priceCurrency?.trim().toUpperCase())
      .filter((currency): currency is string => Boolean(currency)),
  )
  const pricesComparable =
    priceCurrencies.size === 1 &&
    nextProducts.every((product) => Boolean(product.priceCurrency?.trim()))
  const reviews = nextProducts.map((product) => product.review.score ?? 0)
  const fitGaps = nextProducts.map((product) => product.misses.length)
  const rows: readonly MiniCompareRow[] = [
    {
      label: 'Match',
      values: nextProducts.map((product) => `${product.match}%`),
      winnerIndex: winningIndex(
        nextProducts.map((product) => product.match),
        true,
      ),
    },
    {
      label: 'From',
      values: prices.map((price, index) => money(price, nextProducts[index]?.priceCurrency)),
      winnerIndex: pricesComparable ? winningIndex(prices, false) : -1,
    },
    {
      label: 'Reviews',
      values: nextProducts.map((product) =>
        product.review.score === null
          ? `${product.review.count.toLocaleString()} reviews`
          : `${product.review.score.toFixed(1)} · ${product.review.count.toLocaleString()}`,
      ),
      winnerIndex: winningIndex(reviews, true),
    },
    {
      label: 'Fit gaps',
      values: fitGaps.map((misses) =>
        misses === 0 ? 'None' : `${misses} gap${misses === 1 ? '' : 's'}`,
      ),
      winnerIndex: winningIndex(fitGaps, false),
    },
  ]
  const winCounts = nextProducts.map((product, index) => ({
    index,
    wins: rows.filter((row) => row.winnerIndex === index).length,
    match: product.match,
  }))
  const pickIndex = [...winCounts].sort(
    (left, right) => right.wins - left.wins || right.match - left.match,
  )[0].index

  return { type: 'minicompare', products: nextProducts, rows, pickIndex }
}

export { cartItemIdentity }

function cartSnapshotIdentity(item: CartItem): string {
  const offerKey = item.offerKey?.trim()
  if (offerKey) return `offer:${offerKey}`
  const variantId = item.productVariantId?.trim()
  if (variantId) {
    return `variant:${item.merchantId ?? item.merchantDomain ?? item.merchant}:${variantId}`
  }
  return cartItemIdentity(item)
}

export function cartItemsWithFallback(
  liveCart: readonly CartItem[],
  fallbackCart: readonly CartItem[],
): readonly CartItem[] {
  if (fallbackCart.length === 0) {
    return liveCart
  }
  const seen = new Set(liveCart.map(cartSnapshotIdentity))
  return [
    ...liveCart,
    ...fallbackCart.filter((item) => {
      const key = cartSnapshotIdentity(item)
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    }),
  ]
}

export function cartItemsForChatBlock(
  liveCart: readonly CartItem[],
  artifactCart: readonly CartItem[] | undefined,
  immutable: boolean,
  useLiveCart = false,
): readonly CartItem[] {
  if (useLiveCart) {
    return liveCart
  }
  if (immutable && artifactCart !== undefined) {
    return artifactCart
  }
  return cartItemsWithFallback(liveCart, artifactCart ?? [])
}

/** Keeps one current cart card connected while older agent cart cards remain historical snapshots. */
export function latestCartBlockMessageId(messages: readonly DiscoverChatMessage[]): string | null {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]
    if (message?.blocks?.some((block) => block.type === 'cart')) {
      return message.id
    }
  }
  return null
}

/** Prevents a dismissed current cart from promoting a historical cart snapshot to live state. */
export function visibleLatestCartBlockMessageId(
  allMessages: readonly DiscoverChatMessage[],
  visibleMessages: readonly DiscoverChatMessage[],
): string | null {
  const latestMessageId = latestCartBlockMessageId(allMessages)
  return latestMessageId && visibleMessages.some((message) => message.id === latestMessageId)
    ? latestMessageId
    : null
}

export function productsInDiscoverMessage(message: DiscoverChatMessage): readonly Product[] {
  const products: Product[] = []
  for (const block of message.blocks ?? []) {
    products.push(...productsInDiscoverBlock(block))
  }
  return [...new Map(products.map((product) => [product.id, product])).values()]
}

function productsInDiscoverBlock(block: DiscoverChatBlock): readonly Product[] {
  if (block.type === 'products' || block.type === 'saved' || block.type === 'minicompare') {
    return block.products
  }
  if (block.type === 'similar') {
    return [...(block.product ? [block.product] : []), ...block.products]
  }
  if (
    block.type === 'reviews' ||
    block.type === 'code' ||
    block.type === 'watch' ||
    block.type === 'friendvote' ||
    block.type === 'added'
  ) {
    return [block.product]
  }
  if (block.type === 'decision') {
    return [block.product, ...(block.runnerUp ? [block.runnerUp] : [])]
  }
  if (block.type === 'cart' && block.products) {
    return block.products
  }
  return []
}

function discoverMessageProductOpenContext(
  message: DiscoverChatMessage,
  selectedProduct: Product,
): {
  product: Product
  products: readonly Product[]
  researchQuery: string | null
} | null {
  for (const exactSnapshot of [true, false]) {
    for (const block of message.blocks ?? []) {
      const products = productsInDiscoverBlock(block)
      const product = products.find((candidate) =>
        exactSnapshot ? candidate === selectedProduct : candidate.id === selectedProduct.id,
      )
      if (product) {
        return {
          product,
          products,
          researchQuery: discoverProductResearchQuery(block, message.query),
        }
      }
    }
  }
  return null
}

export function openProductFromDiscoverMessage(
  message: DiscoverChatMessage,
  product: Product,
  onOpen: ProductOpenHandler,
): void {
  const context = discoverMessageProductOpenContext(message, product)
  onOpen(
    context?.product ?? product,
    context?.products ?? [product],
    context?.researchQuery ?? message.query,
  )
}

export function cartLineForAddedBlock(
  cart: readonly CartItem[],
  block: Extract<DiscoverChatBlock, { type: 'added' }>,
): CartItem | undefined {
  const exactCartLineIdentity = block.cartLineIdentity?.trim()
  if (exactCartLineIdentity) {
    const exactLine = cart.find((item) => cartItemIdentity(item) === exactCartLineIdentity)
    if (exactLine) return exactLine
  }

  const exactOfferKey = block.offerKey?.trim()
  if (exactOfferKey) {
    return cart.find(
      (item) => item.id === block.product.id && item.offerKey?.trim() === exactOfferKey,
    )
  }

  if (exactCartLineIdentity) return undefined
  return cart.find((item) => item.id === block.product.id && item.merchant === block.merchant)
}

export function productsWithFallback(
  fallbackProducts: readonly Product[] | undefined,
  products: readonly Product[],
): readonly Product[] {
  if (!fallbackProducts?.length) {
    return products
  }
  const seen = new Set<ProductId>()
  return [...fallbackProducts, ...products].filter((product) => {
    if (seen.has(product.id)) {
      return false
    }
    seen.add(product.id)
    return true
  })
}

function productArtworkUrl(product: Product): string | null {
  const direct = product.imageUrl?.trim()
  if (direct) {
    return direct
  }
  return (
    product.media
      ?.filter((item) => item.type?.toLowerCase() === 'image')
      ?.map((item) => item.url?.trim() ?? '')
      ?.find(Boolean) ?? null
  )
}

export function isRenderableSearchProduct(product: Product): boolean {
  if (product.canonicalProduct) {
    return product.agentStage !== 'candidate' && product.canonicalProduct.offers.length > 0
  }
  return product.agentStage !== 'candidate' && Boolean(productArtworkUrl(product))
}

export function searchProductReviewInsight(
  candidate: boolean,
  ratingScore: number | null,
  reviewCount: number,
): string {
  if (candidate) {
    return 'Review signals pending.'
  }
  if (reviewCount <= 0) {
    return 'No review data available from this catalog result.'
  }
  if (ratingScore === null) {
    return 'Review count is available, but no rating summary has been fetched yet.'
  }
  return 'Rating data is available; no review-summary agent has run yet.'
}

function productCopyLine(product: Product): string {
  const merchant = product.offers[0]?.merchant
    ? merchantAdjacentDisplayLabel(product.offers[0].merchant)
    : `${product.merchants} merchants`
  return `${product.name} - ${money(product.priceFrom)} - ${merchant}`
}

function discoverBlockCopyText(block: DiscoverChatBlock): string {
  if (block.type === 'text' || block.type === 'system') {
    return block.text
  }
  if (block.type === 'newsletter') {
    return "This functionality isn't ready yet. We're working on it!"
  }
  if (block.type === 'products') {
    return [
      block.query ? `Products for "${block.query}":` : 'Products:',
      ...block.products.map(productCopyLine),
    ].join('\n')
  }
  if (block.type === 'reviews') {
    const reviewCount = block.product.review.count
    const rating = block.product.review.score
    const ratingSummary =
      rating === null
        ? `${reviewCount.toLocaleString()} ${reviewCount === 1 ? 'review' : 'reviews'}`
        : `${rating.toFixed(1)}/5 from ${reviewCount.toLocaleString()} ${reviewCount === 1 ? 'review' : 'reviews'}`
    return `${block.product.name} reviews: ${ratingSummary}. ${block.product.review.insight}`
  }
  if (block.type === 'code') {
    const codes = block.codes ?? []
    if (codes.length > 0) {
      return [
        `${block.product.name} ${codes.length === 1 ? 'code' : 'codes'}:`,
        ...codes.map((code) => {
          const details = [
            code.description ?? code.title,
            code.restrictions,
            code.validationMessage,
            code.validUntil ? `Valid until ${code.validUntil}` : null,
            code.expiresAt ? `Expires ${code.expiresAt}` : null,
          ].filter((value): value is string => Boolean(value?.trim()))
          return `${code.code}${details.length > 0 ? ` - ${details.join(' · ')}` : ''}`
        }),
      ].join('\n')
    }
    return `${block.product.name} code search: ${block.message ?? 'No accepted code found'}`
  }
  if (block.type === 'similar') {
    if (block.similarityAnchor) {
      return block.products.map(productCopyLine).join('\n')
    }
    const heading = block.product ? `Similar to ${block.product.name}:` : 'Similar products:'
    return [heading, ...block.products.map(productCopyLine)].join('\n')
  }
  if (block.type === 'decision') {
    return [
      `Pick: ${productCopyLine(block.product)}`,
      block.product.note ? `Why: ${block.product.note}` : '',
      block.runnerUp ? `Runner-up: ${productCopyLine(block.runnerUp)}` : '',
    ]
      .filter(Boolean)
      .join('\n')
  }
  if (block.type === 'watch') {
    return `Watching ${block.product.name}: ${money(block.price)} at ${merchantAdjacentDisplayLabel(block.merchant)}`
  }
  if (block.type === 'friendvote') {
    return `${block.person} voted ${block.vote} on ${block.product.name}: ${block.note}`
  }
  if (block.type === 'added') {
    const price = block.price ?? block.product.priceFrom
    return [
      `Added ${block.product.name} to cart from ${merchantAdjacentDisplayLabel(block.merchant)}.`,
      price === null ? '' : `Price: ${money(price)}.`,
      block.count === undefined
        ? ''
        : `${block.count} ${block.count === 1 ? 'item' : 'items'} in cart.`,
    ]
      .filter(Boolean)
      .join(' ')
  }
  if (block.type === 'saved') {
    return ['Saved products:', ...block.products.map(productCopyLine)].join('\n')
  }
  if (block.type === 'orders') {
    return [
      `${block.orders.length} recent ${block.orders.length === 1 ? 'order' : 'orders'}:`,
      ...block.orders.map((order) => {
        const details = [
          order.status,
          order.date,
          order.statusNote,
          `${order.items.length} ${order.items.length === 1 ? 'item' : 'items'}`,
          order.savedNote,
        ].filter((value) => value.trim())
        return `${order.id} - ${details.join(' · ')}`
      }),
    ].join('\n')
  }
  if (block.type === 'prefs') {
    return ['Preferences:', ...block.preferences.map((preference) => preference.label)].join('\n')
  }
  if (block.type === 'cart') {
    return [
      'Cart:',
      ...block.lines.map((line) => {
        const product = line.productTitle?.trim() || line.id
        const price = line.lineTotalAmount ?? line.unitPriceAmount
        const priceLabel = price
          ? ` · ${price}${line.cartCurrency ? ` ${line.cartCurrency}` : ''}`
          : ''
        return `${line.qty} x ${product}${line.variantTitle ? ` (${line.variantTitle})` : ''} - ${merchantDisplayOrigin(line.merchantOrigin)}${priceLabel}`
      }),
    ].join('\n')
  }
  if (block.type === 'checkout') {
    return `Checkout across ${block.merchantCount} ${block.merchantCount === 1 ? 'merchant' : 'merchants'}`
  }
  if (block.type === 'mission') {
    return [
      `${block.goal} (${block.status})`,
      ...block.requirements.map(
        (requirement) =>
          `${requirement.label}: ${requirement.coveredQuantity}/${requirement.requiredQuantity} ${requirement.state.toLowerCase()}`,
      ),
      ...block.assumptions.map((assumption) => `Assumption: ${assumption}`),
    ].join('\n')
  }
  const pick = block.products[block.pickIndex]
  return [
    'Compare:',
    ...block.products.map((product, index) =>
      index === block.pickIndex ? `${productCopyLine(product)} (pick)` : productCopyLine(product),
    ),
    ...block.rows.map((row) =>
      [
        `${row.label}:`,
        ...row.values.map((value, index) => {
          const name = block.products[index]?.name ?? `Option ${index + 1}`
          return `${name} ${value}${row.winnerIndex === index ? ' (best)' : ''}`
        }),
      ].join(' '),
    ),
    pick ? `Recommended: ${pick.name}.` : '',
  ]
    .filter(Boolean)
    .join('\n')
}

export function discoverChatMessageCopyText(message: DiscoverChatMessage): string {
  const parts = [
    message.productContext ? `About ${message.productContext.name}` : '',
    message.text ?? '',
    ...(message.blocks?.map(discoverBlockCopyText) ?? []),
  ]
  return parts
    .map((part) => part.trim())
    .filter(Boolean)
    .join('\n\n')
}

export function copyTextToClipboard(value: string): void {
  const text = value.trim()
  if (!text || typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
    return
  }
  void navigator.clipboard.writeText(text).catch(() => undefined)
}

function compactChatHistoryText(value: string): string {
  const normalized = value.trim().replace(/\s+/g, ' ')
  if (!normalized) {
    return ''
  }
  return normalized.length > 82 ? `${normalized.slice(0, 79)}...` : normalized
}

function discoverBlockPreview(block: DiscoverChatBlock): string {
  if (block.type === 'text') {
    return plainAgentText(block.text)
  }
  if (block.type === 'system') {
    return block.text
  }
  if (block.type === 'newsletter') {
    return "This functionality isn't ready yet."
  }
  if (block.type === 'products') {
    return block.query
      ? `${block.products.length} products for "${block.query}"`
      : `${block.products.length} products`
  }
  if (block.type === 'cart') {
    return `${block.lines.length} cart ${block.lines.length === 1 ? 'item' : 'items'}`
  }
  if (block.type === 'checkout') {
    return `Checkout across ${block.merchantCount} ${block.merchantCount === 1 ? 'merchant' : 'merchants'}`
  }
  if (block.type === 'minicompare') {
    return `Compare ${block.products.length} products`
  }
  if (block.type === 'saved') {
    return `${block.products.length} saved ${block.products.length === 1 ? 'item' : 'items'}`
  }
  if (block.type === 'orders') {
    return `${block.orders.length} recent ${block.orders.length === 1 ? 'order' : 'orders'}`
  }
  if (block.type === 'prefs') {
    return `${block.preferences.length} active preferences`
  }
  return 'Product follow-up'
}

function discoverMessagePreview(message: DiscoverChatMessage): string {
  if (message.text) {
    return message.role === 'ai' ? plainAgentText(message.text) : message.text
  }
  const block = message.blocks?.find((candidate) =>
    compactChatHistoryText(discoverBlockPreview(candidate)),
  )
  return block ? discoverBlockPreview(block) : ''
}

export function discoverThreadPreview(thread: DiscoverChatThread): string {
  for (let index = thread.messages.length - 1; index >= 0; index -= 1) {
    const preview = compactChatHistoryText(discoverMessagePreview(thread.messages[index]))
    if (preview) {
      return preview
    }
  }
  return discoverThreadMessageCountValue(thread) > 0 ? 'Open to view messages' : 'No messages yet'
}

export function discoverThreadTime(thread: DiscoverChatThread): number | null {
  return thread.updatedAt ?? thread.createdAt ?? null
}

export function discoverThreadTimeLabel(thread: DiscoverChatThread): string {
  const timestamp = discoverThreadTime(thread)
  if (!timestamp) {
    return 'Saved'
  }
  const date = new Date(timestamp)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) {
    return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(date)
  }
  if (date.toDateString() === yesterday.toDateString()) {
    return 'Yesterday'
  }
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date)
}

export function discoverThreadMessageCount(thread: DiscoverChatThread): string {
  const count = discoverThreadMessageCountValue(thread)
  return `${count} ${count === 1 ? 'message' : 'messages'}`
}

function discoverThreadMessageCountValue(thread: DiscoverChatThread): number {
  const serverCount = Number.isFinite(thread.messageCount)
    ? Math.max(0, Math.trunc(thread.messageCount ?? 0))
    : 0
  return Math.max(thread.messages.length, serverCount)
}
