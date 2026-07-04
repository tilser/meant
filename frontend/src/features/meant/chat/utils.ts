import type { CartItem, Product, ProductId, UserLocation } from '../types'
import { money, productPriceFrom, readStorage } from '../utils'
import type {
  DiscoverChatBlock,
  DiscoverChatMessage,
  DiscoverChatThread,
  MiniCompareRow,
} from './types'

let discoverChatThreadSequence = 0
const nextDiscoverChatThreadId = () => {
  discoverChatThreadSequence += 1
  return `discover-thread-${Date.now().toString(36)}-${discoverChatThreadSequence}`
}

export const DEFAULT_DISCOVER_CHAT_TITLE = 'New chat'

export function createDiscoverChatThread(
  messages: readonly DiscoverChatMessage[] = [],
  title = DEFAULT_DISCOVER_CHAT_TITLE,
): DiscoverChatThread {
  const now = Date.now()
  return {
    id: nextDiscoverChatThreadId(),
    title,
    messages,
    createdAt: now,
    updatedAt: now,
  }
}

export function normalizeDiscoverChatThreads(
  threads: readonly DiscoverChatThread[],
): DiscoverChatThread[] {
  const now = Date.now()
  return threads.map((thread, index) => {
    const fallbackTime = now - (threads.length - index) * 1000
    const createdAt = thread.createdAt ?? fallbackTime
    return {
      ...thread,
      createdAt,
      updatedAt: thread.updatedAt ?? createdAt,
    }
  })
}

export function initialDiscoverChatThreads(): DiscoverChatThread[] {
  const storedThreads = readStorage<DiscoverChatThread[] | null>('meant.discoverChatThreads', null)
  if (storedThreads?.length) {
    return normalizeDiscoverChatThreads(storedThreads)
  }
  const legacyMessages = readStorage<DiscoverChatMessage[]>('meant.discoverChatMessages', [])
  return [
    createDiscoverChatThread(
      legacyMessages,
      legacyMessages.length > 0 ? 'Shopping agent' : DEFAULT_DISCOVER_CHAT_TITLE,
    ),
  ]
}

function winningIndex(values: readonly number[], higherIsBetter: boolean): number {
  return values.reduce((bestIndex, value, index) => {
    const bestValue = values[bestIndex] ?? value
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
      values: prices.map((price) => money(price)),
      winnerIndex: winningIndex(prices, false),
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

export function cartItemIdentity(item: Pick<CartItem, 'id' | 'merchant'>): string {
  return `${item.id}:${item.merchant}`
}

export function cartItemsWithFallback(
  liveCart: readonly CartItem[],
  fallbackCart: readonly CartItem[],
): readonly CartItem[] {
  if (fallbackCart.length === 0) {
    return liveCart
  }
  const seen = new Set(liveCart.map(cartItemIdentity))
  return [
    ...liveCart,
    ...fallbackCart.filter((item) => {
      const key = cartItemIdentity(item)
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    }),
  ]
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

export function isRenderableSearchProduct(product: Product): boolean {
  return product.agentStage !== 'candidate'
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
  const merchant = product.offers[0]?.merchant ?? `${product.merchants} merchants`
  return `${product.name} - ${money(product.priceFrom)} - ${merchant}`
}

function discoverBlockCopyText(block: DiscoverChatBlock): string {
  if (block.type === 'text' || block.type === 'system') {
    return block.text
  }
  if (block.type === 'products') {
    return [
      block.query ? `Products for "${block.query}":` : 'Products:',
      ...block.products.map(productCopyLine),
    ].join('\n')
  }
  if (block.type === 'reviews') {
    return `${block.product.name} reviews: ${block.product.review.insight}`
  }
  if (block.type === 'code') {
    return `${block.product.name} code: ${block.code} saves ${money(block.saved)}`
  }
  if (block.type === 'similar') {
    return [`Similar to ${block.product.name}:`, ...block.products.map(productCopyLine)].join('\n')
  }
  if (block.type === 'decision') {
    return `Pick: ${productCopyLine(block.product)}`
  }
  if (block.type === 'watch') {
    return `Watching ${block.product.name}: ${money(block.price)} at ${block.merchant}`
  }
  if (block.type === 'friendvote') {
    return `${block.person} voted ${block.vote} on ${block.product.name}: ${block.note}`
  }
  if (block.type === 'added') {
    return `Added ${block.product.name} to cart from ${block.merchant}.`
  }
  if (block.type === 'saved') {
    return ['Saved products:', ...block.products.map(productCopyLine)].join('\n')
  }
  if (block.type === 'orders') {
    return `${block.orders.length} recent ${block.orders.length === 1 ? 'order' : 'orders'}`
  }
  if (block.type === 'prefs') {
    return ['Preferences:', ...block.preferences.map((preference) => preference.label)].join('\n')
  }
  if (block.type === 'cart') {
    return [
      'Cart:',
      ...block.lines.map(
        (line) =>
          `${line.qty} x ${line.id}${line.variantTitle ? ` (${line.variantTitle})` : ''} - ${line.merchant}`,
      ),
    ].join('\n')
  }
  if (block.type === 'checkout') {
    return `Checkout across ${block.merchantCount} ${block.merchantCount === 1 ? 'merchant' : 'merchants'}`
  }
  return [
    'Compare:',
    ...block.products.map((product, index) =>
      index === block.pickIndex ? `${productCopyLine(product)} (pick)` : productCopyLine(product),
    ),
  ].join('\n')
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
  if (block.type === 'text' || block.type === 'system') {
    return block.text
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
    return message.text
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
  return 'No messages yet'
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
  const count = thread.messages.length
  return `${count} ${count === 1 ? 'message' : 'messages'}`
}
