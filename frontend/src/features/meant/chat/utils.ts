import type { CartItem, Product, ProductId, UserLocation } from '../types'
import { cartItemIdentity, money, productPriceFrom, readStorage, writeStorage } from '../utils'
import type {
  DiscoverChatBlock,
  DiscoverChatMessage,
  DiscoverChatThread,
  MiniCompareRow,
} from './types'
import { normalizedProductResultSetId } from './productResultSetReference'

let discoverChatThreadSequence = 0
const DISCOVER_CHAT_THREADS_STORAGE_KEY = 'meant.discoverChatThreads'
const LEGACY_DISCOVER_CHAT_MESSAGES_STORAGE_KEY = 'meant.discoverChatMessages'
const DISCOVER_CHAT_THREAD_UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const nextDiscoverChatThreadId = () => {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  discoverChatThreadSequence += 1
  return `00000000-0000-4000-8000-${discoverChatThreadSequence.toString().padStart(12, '0')}`
}

export const DEFAULT_DISCOVER_CHAT_TITLE = 'New chat'

type ProductOpenHandler = (
  product: Product,
  products?: readonly Product[],
  researchQuery?: string | null,
) => void

export function discoverProductResearchQuery(
  block: DiscoverChatBlock,
  messageQuery: string | null | undefined,
): string | null {
  const blockQuery = block.type === 'products' ? block.query?.trim() : null
  return blockQuery || messageQuery?.trim() || null
}

export function productOpenWithResearchQuery(
  onOpen: ProductOpenHandler,
  researchQuery: string | null | undefined,
): (product: Product, products?: readonly Product[]) => void {
  const normalizedQuery = researchQuery?.trim() || null
  return (product, products) => onOpen(product, products, normalizedQuery)
}

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

export function discoverSuggestedReplies(
  messages: readonly DiscoverChatMessage[],
): readonly string[] {
  const latestMessage = messages[messages.length - 1]
  if (!latestMessage || latestMessage.role !== 'ai' || latestMessage.pending) {
    return []
  }
  return Array.from(
    new Set(
      (latestMessage.suggestedReplies ?? []).map((suggestion) => suggestion.trim()).filter(Boolean),
    ),
  )
}

export function discoverThreadSearchContext(thread: DiscoverChatThread): {
  query: string
  products: readonly Product[]
} {
  for (let index = thread.messages.length - 1; index >= 0; index -= 1) {
    const message = thread.messages[index]
    const query = message?.query?.trim()
    if (!query) {
      continue
    }
    const products = message.blocks?.find(
      (block): block is Extract<DiscoverChatBlock, { type: 'products' }> =>
        block.type === 'products',
    )?.products
    return { query, products: products ?? [] }
  }
  return { query: '', products: [] }
}

export function normalizeDiscoverChatThreads(
  threads: readonly DiscoverChatThread[],
): DiscoverChatThread[] {
  const now = Date.now()
  const seenIds = new Set<string>()
  return threads.map((thread, index) => {
    const fallbackTime = now - (threads.length - index) * 1000
    const createdAt = thread.createdAt ?? fallbackTime
    const id =
      DISCOVER_CHAT_THREAD_UUID.test(thread.id) && !seenIds.has(thread.id)
        ? thread.id
        : nextDiscoverChatThreadId()
    seenIds.add(id)
    return {
      ...thread,
      id,
      messages: thread.messages.map((message) =>
        message.pending
          ? {
              ...message,
              pending: false,
              pendingText: undefined,
              suggestedReplies: undefined,
              blocks:
                message.blocks && message.blocks.length > 0
                  ? message.blocks
                  : [
                      {
                        type: 'system' as const,
                        text: 'This search was interrupted. Send your last answer again to continue.',
                      },
                    ],
            }
          : message,
      ),
      createdAt,
      updatedAt: thread.updatedAt ?? createdAt,
    }
  })
}

function durableDiscoverBlock(block: DiscoverChatBlock): DiscoverChatBlock {
  switch (block.type) {
    case 'text':
      return { type: 'text', text: block.text }
    case 'newsletter':
      return { type: 'newsletter' }
    case 'prefs':
      return {
        type: 'prefs',
        preferences: block.preferences.map((preference) => ({
          id: preference.id,
          label: preference.label,
          desc: preference.desc,
          category: preference.category,
          polarity: preference.polarity,
          displayOrder: preference.displayOrder,
        })),
      }
    case 'products': {
      const productResultSetId = normalizedProductResultSetId(block.productResultSetId)
      if (!productResultSetId) {
        return { ...block, products: [...block.products] }
      }
      return {
        type: 'products',
        products: [],
        productResultSetId,
        query: block.query,
      }
    }
    case 'system':
      return { type: 'system', text: block.text }
    default:
      return { ...block }
  }
}

function durableDiscoverMessage(message: DiscoverChatMessage): DiscoverChatMessage {
  const durableBlocks = message.blocks?.map(durableDiscoverBlock) ?? []

  return {
    id: message.id,
    role: message.role,
    text: message.text,
    blocks: durableBlocks.length > 0 ? durableBlocks : undefined,
    pending: message.pending,
    pendingText: message.pendingText,
    suggestedReplies: message.suggestedReplies ? [...message.suggestedReplies] : undefined,
    query: message.query,
    productContext: message.productContext,
  }
}

/** Stores the complete structured conversation while rehydrating server-backed search result lists. */
export function durableDiscoverChatThread(
  thread: DiscoverChatThread,
  archived: boolean | undefined = thread.archived,
): DiscoverChatThread {
  return {
    id: thread.id,
    title: thread.title,
    archived,
    messages: thread.messages.map(durableDiscoverMessage),
    qualificationId: thread.qualificationId,
    named: thread.named,
    focusProductId: thread.focusProductId,
    createdAt: thread.createdAt,
    updatedAt: thread.updatedAt,
    persistedRevision: thread.persistedRevision,
  }
}

function scopedStorageKey(baseKey: string, storageScope?: string): string {
  const normalizedScope = storageScope?.trim()
  return normalizedScope ? `${baseKey}.${normalizedScope}` : baseKey
}

export function initialDiscoverChatThreads(storageScope?: string): DiscoverChatThread[] {
  if (storageScope) {
    // Pre-account-scoping builds stored whole Shopify results under shared keys. Never migrate
    // those facts into an authenticated account; purge them once scoped storage is available.
    writeStorage(DISCOVER_CHAT_THREADS_STORAGE_KEY, [])
    writeStorage(LEGACY_DISCOVER_CHAT_MESSAGES_STORAGE_KEY, [])
  }
  const storedThreads = readStorage<DiscoverChatThread[] | null>(
    scopedStorageKey(DISCOVER_CHAT_THREADS_STORAGE_KEY, storageScope),
    null,
  )
  if (storedThreads?.length) {
    return normalizeDiscoverChatThreads(storedThreads).map((thread) =>
      durableDiscoverChatThread(thread),
    )
  }
  const legacyMessages = storageScope
    ? []
    : readStorage<DiscoverChatMessage[]>(LEGACY_DISCOVER_CHAT_MESSAGES_STORAGE_KEY, [])
  return [
    createDiscoverChatThread(
      legacyMessages,
      legacyMessages.length > 0 ? 'Shopping agent' : DEFAULT_DISCOVER_CHAT_TITLE,
    ),
  ]
}

export function saveStoredDiscoverChatThreads(
  threads: readonly DiscoverChatThread[],
  storageScope?: string,
): void {
  writeStorage(
    scopedStorageKey(DISCOVER_CHAT_THREADS_STORAGE_KEY, storageScope),
    threads.map((thread) => durableDiscoverChatThread(thread)),
  )
}

/**
 * Keeps unsaved local edits only while they are based on the currently observed server revision.
 * A newer remote revision always wins, regardless of client timestamps, so a stale tab cannot
 * launder an old snapshot through localStorage and overwrite newer history after a reload.
 */
export function mergeDiscoverThreadSources(
  remoteThreads: readonly DiscoverChatThread[],
  localThreads: readonly DiscoverChatThread[],
): DiscoverChatThread[] {
  const hasHistory = (thread: DiscoverChatThread) =>
    thread.messages.length > 0 || Boolean(thread.focusProductId) || thread.named === true
  const remoteById = new Map(remoteThreads.map((thread) => [thread.id, thread]))
  const merged = new Map<string, DiscoverChatThread>()

  for (const local of localThreads) {
    if (!hasHistory(local)) continue
    const remote = remoteById.get(local.id)
    if (!remote) {
      merged.set(local.id, local)
      continue
    }
    const sameBaseRevision =
      local.persistedRevision !== undefined && local.persistedRevision === remote.persistedRevision
    merged.set(
      local.id,
      sameBaseRevision && (local.updatedAt ?? 0) > (remote.updatedAt ?? 0) ? local : remote,
    )
    remoteById.delete(local.id)
  }

  for (const remote of remoteById.values()) {
    if (hasHistory(remote)) merged.set(remote.id, remote)
  }

  return Array.from(merged.values()).sort((left, right) => {
    const timeDifference = (discoverThreadTime(right) ?? 0) - (discoverThreadTime(left) ?? 0)
    return timeDifference || left.title.localeCompare(right.title)
  })
}

export function canRestoreDiscoverThreadAfterConflict(
  currentThread: DiscoverChatThread | undefined,
  expectedLocalUpdatedAt: number | undefined,
): boolean {
  return Boolean(currentThread && currentThread.updatedAt === expectedLocalUpdatedAt)
}

export function deleteStoredDiscoverChatThread(threadId: string, storageScope?: string): void {
  const storageKey = scopedStorageKey(DISCOVER_CHAT_THREADS_STORAGE_KEY, storageScope)
  const storedThreads = readStorage<DiscoverChatThread[] | null>(storageKey, null)
  if (!storedThreads?.length) {
    if (!storageScope) {
      writeStorage(LEGACY_DISCOVER_CHAT_MESSAGES_STORAGE_KEY, [])
    }
    return
  }
  const nextThreads = storedThreads.filter((thread) => thread.id !== threadId)
  writeStorage(storageKey, nextThreads)
  if (nextThreads.length === 0 && !storageScope) {
    writeStorage(LEGACY_DISCOVER_CHAT_MESSAGES_STORAGE_KEY, [])
  }
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
  const merchant = product.offers[0]?.merchant ?? `${product.merchants} merchants`
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
    return [`Similar to ${block.product.name}:`, ...block.products.map(productCopyLine)].join('\n')
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
    return `Watching ${block.product.name}: ${money(block.price)} at ${block.merchant}`
  }
  if (block.type === 'friendvote') {
    return `${block.person} voted ${block.vote} on ${block.product.name}: ${block.note}`
  }
  if (block.type === 'added') {
    const price = block.price ?? block.product.priceFrom
    return [
      `Added ${block.product.name} to cart from ${block.merchant}.`,
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
        return `${line.qty} x ${product}${line.variantTitle ? ` (${line.variantTitle})` : ''} - ${line.merchant}${priceLabel}`
      }),
    ].join('\n')
  }
  if (block.type === 'checkout') {
    return `Checkout across ${block.merchantCount} ${block.merchantCount === 1 ? 'merchant' : 'merchants'}`
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
  if (block.type === 'text' || block.type === 'system') {
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
