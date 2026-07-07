import {
  type DragEvent as ReactDragEvent,
  type SetStateAction,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import {
  deleteDiscoverConversation,
  getDiscoverConversations,
  saveDiscoverConversation,
  searchDiscountCodes,
  type DiscountCodeProfile,
  type MerchantProfile,
  type UserDiscoverConversationProfile,
} from '../../../lib/apiClient'
import { PRODUCTS, PROFILE } from '../data'
import type {
  CartItem,
  CheckoutPayload,
  Offer,
  Order,
  Preference,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import {
  bestOffer,
  cartLines,
  normalizedMerchantName,
  productPriceFrom,
  resolveAsk,
} from '../utils'
import { resolveCartableOffer } from '../cart/cartOfferResolver'
import { canResolveCartOffer, offerCartable } from '../cart/utils'
import { AskComposer } from '../ask/AskComposer'
import { flyToShelf } from '../shared/animations'
import { DustingContainer } from '../shared/DustingContainer'
import { MerchantIcon } from '../shared/icons'
import { deliveryLocationSummary } from '../shared/locations'
import { useStoredState } from '../shared/storage'
import { CloseIcon, ProductArtwork, SparkMark } from '../shared/ui'
import type {
  ShelfDragPayload,
  ShelfItem,
  ShelfMessageSnapshot,
  ShelfProductSnapshot,
  ShelfThumb,
} from '../shelf/types'
import { SHELF_DRAG_MIME } from '../shelf/types'
import { AgentActivityPanel } from './AgentActivityPanel'
import { DiscoverChatMessageRow } from './DiscoverChatMessageRow'
import { DiscoverShareSheet } from './DiscoverShareSheet'
import { DiscoverThreadHistoryButton } from './DiscoverThreadHistoryButton'
import { DiscoverThreadTabs } from './DiscoverThreadTabs'
import type {
  AgentActivity,
  DiscoverChatBlock,
  DiscoverChatMessage,
  DiscoverChatThread,
  DiscoverFindRequest,
  ProductDetailChatRequest,
} from './types'
import {
  cartItemsWithFallback,
  createDiscoverChatThread,
  createMiniCompareBlock,
  DEFAULT_DISCOVER_CHAT_TITLE,
  discoverThreadTime,
  initialDiscoverChatThreads,
  isRenderableSearchProduct,
  normalizeDiscoverChatThreads,
} from './utils'

const DISCOVER_HISTORY_SYNC_DELAY = 500

function merchantPrimarySearchValues(merchant: MerchantProfile): string[] {
  return [normalizedMerchantName(merchant.name), normalizedMerchantName(merchant.domain)].filter(
    Boolean,
  )
}

function merchantSearchValues(merchant: MerchantProfile): string[] {
  return [
    ...merchantPrimarySearchValues(merchant),
    normalizedMerchantName(merchant.description),
    normalizedMerchantName(merchant.advertisedMcpEndpoint),
    normalizedMerchantName(merchant.profileMcpEndpoint),
  ].filter(Boolean)
}

function merchantSearchRank(merchant: MerchantProfile, searchText: string): number {
  if (!searchText) {
    return 0
  }

  const primaryValues = merchantPrimarySearchValues(merchant)
  if (primaryValues.some((value) => value.startsWith(searchText))) {
    return 0
  }
  if (primaryValues.some((value) => value.includes(searchText))) {
    return 1
  }
  if (merchantSearchValues(merchant).some((value) => value.includes(searchText))) {
    return 2
  }
  return 3
}

function orderedMerchantMatches(
  merchants: readonly MerchantProfile[],
  merchantCounts: ReadonlyMap<string, number>,
  searchText: string,
): MerchantProfile[] {
  return merchants
    .map((merchant) => ({
      merchant,
      count: merchantCounts.get(merchant.id) ?? 0,
      rank: merchantSearchRank(merchant, searchText),
    }))
    .filter((item) => !searchText || item.rank < 3)
    .sort((left, right) => {
      if (left.rank !== right.rank) {
        return left.rank - right.rank
      }
      if (left.count !== right.count) {
        return right.count - left.count
      }
      return left.merchant.name.localeCompare(right.merchant.name, undefined, {
        sensitivity: 'base',
      })
    })
    .map((item) => item.merchant)
}

function hasDiscoverThreadHistory(thread: DiscoverChatThread): boolean {
  return thread.messages.length > 0 || Boolean(thread.focusProductId) || thread.named === true
}

function timestampFromProfile(value: string): number {
  const timestamp = Date.parse(value)
  return Number.isFinite(timestamp) ? timestamp : Date.now()
}

function discoverThreadFromProfile(
  profile: UserDiscoverConversationProfile,
): DiscoverChatThread | null {
  try {
    const parsed: unknown = JSON.parse(profile.threadJson)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null
    }
    const snapshot = parsed as Partial<DiscoverChatThread>
    const messages = Array.isArray(snapshot.messages) ? snapshot.messages : []
    return (
      normalizeDiscoverChatThreads([
        {
          ...snapshot,
          id: profile.conversationId,
          title: snapshot.title?.trim() || profile.title || DEFAULT_DISCOVER_CHAT_TITLE,
          messages,
          archived: snapshot.archived === true,
          createdAt: snapshot.createdAt ?? timestampFromProfile(profile.createdAt),
          updatedAt: snapshot.updatedAt ?? timestampFromProfile(profile.updatedAt),
        },
      ])[0] ?? null
    )
  } catch {
    return null
  }
}

function discoverThreadJson(thread: DiscoverChatThread, archived: boolean): string {
  return JSON.stringify({
    ...thread,
    archived,
  })
}

function discoverThreadTitle(thread: DiscoverChatThread): string {
  const title = thread.title.trim() || DEFAULT_DISCOVER_CHAT_TITLE
  return title.length <= 120 ? title : `${title.slice(0, 117)}...`
}

function ChatHero({
  profile,
  greeting,
  prompts,
  onSubmit,
  loading,
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  merchantsLoading,
  merchantsError,
  historyThreads,
  activeThreadId,
  onMerchant,
  onHistorySelect,
  onHistoryDelete,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  prompts: readonly string[]
  onSubmit: (query: string) => void
  loading: boolean
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  merchantsLoading: boolean
  merchantsError: string | null
  historyThreads: readonly DiscoverChatThread[]
  activeThreadId: string
  onMerchant: (merchant: MerchantProfile | null) => void
  onHistorySelect: (threadId: string) => void
  onHistoryDelete: (threadId: string) => void
}>) {
  const [value, setValue] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const submittedTimeoutRef = useRef<number | null>(null)
  const hasSearchText = value.trim().length > 0

  useEffect(
    () => () => {
      if (submittedTimeoutRef.current !== null) {
        window.clearTimeout(submittedTimeoutRef.current)
      }
    },
    [],
  )

  const submit = (text?: string) => {
    if (loading) {
      return
    }
    const query = (text ?? value).trim()
    if (!query) {
      return
    }
    setSubmitted(true)
    if (submittedTimeoutRef.current !== null) {
      window.clearTimeout(submittedTimeoutRef.current)
    }
    submittedTimeoutRef.current = window.setTimeout(() => {
      setSubmitted(false)
      submittedTimeoutRef.current = null
    }, 520)
    setValue('')
    onSubmit(query)
  }

  return (
    <header className="mt-hero">
      <div className="mt-mono mt-hero-eyebrow">
        {greeting}, {profile.name}
      </div>
      <h1 className="mt-hero-title">
        Everything here is <em>meant</em> for you.
      </h1>
      <p className="mt-hero-sub">
        Ask for products across supported merchants. Meant already knows you prefer{' '}
        {profile.summary}
      </p>
      <form
        className={`mt-search${hasSearchText ? ' mt-search-writing' : ''}${submitted ? ' mt-search-submitted' : ''}`}
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <span className="mt-search-spark" aria-hidden>
          <SparkMark size={20} />
        </span>
        <input
          className="mt-search-input"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder='Ask Meant anything - "a good cotton T-shirt under $50"'
          disabled={loading}
        />
        <button type="submit" className="mt-search-go" aria-label="Ask" disabled={loading}>
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
            <path
              d="M3.5 9h11M9.5 4l5 5-5 5"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </form>
      <div className="mt-hero-context">
        <MerchantScope
          merchants={merchants}
          selectedMerchant={selectedMerchant}
          merchantCounts={merchantCounts}
          totalProductCount={totalProductCount}
          loading={merchantsLoading}
          error={merchantsError}
          onMerchant={onMerchant}
        />
        <DiscoverThreadHistoryButton
          threads={historyThreads}
          activeId={activeThreadId}
          onSelect={onHistorySelect}
          onDelete={onHistoryDelete}
        />
      </div>
      <div className="mt-prompts">
        {prompts.map((prompt) => (
          <button
            key={prompt}
            type="button"
            className="mt-prompt"
            onClick={() => submit(prompt)}
            disabled={loading}
          >
            {prompt}
          </button>
        ))}
      </div>
    </header>
  )
}

function MerchantScope({
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  loading,
  error,
  onMerchant,
}: Readonly<{
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  loading: boolean
  error: string | null
  onMerchant: (merchant: MerchantProfile | null) => void
}>) {
  const [open, setOpen] = useState(false)
  const [merchantSearch, setMerchantSearch] = useState('')
  const ref = useRef<HTMLDivElement | null>(null)
  const searchRef = useRef<HTMLInputElement | null>(null)
  const merchantSearchText = merchantSearch.trim().toLowerCase()
  const filteredMerchants = useMemo(
    () => orderedMerchantMatches(merchants, merchantCounts, merchantSearchText),
    [merchantCounts, merchantSearchText, merchants],
  )

  useEffect(() => {
    if (!open) {
      return
    }
    const onDown = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      setMerchantSearch('')
      return
    }
    const timeout = window.setTimeout(() => searchRef.current?.focus(), 0)
    return () => window.clearTimeout(timeout)
  }, [open])

  if (loading && merchants.length === 0) {
    return (
      <div className="mt-scope">
        <div className="mt-scope-status mt-mono">Loading merchants</div>
      </div>
    )
  }

  if (error && merchants.length === 0) {
    return (
      <div className="mt-scope">
        <div className="mt-scope-status mt-scope-error mt-mono">{error}</div>
      </div>
    )
  }

  if (merchants.length === 0) {
    return null
  }

  return (
    <div className="mt-scope">
      <div className="mt-scope-combo" ref={ref}>
        <button
          className={`mt-scope-btn ${selectedMerchant ? 'on' : ''}`}
          type="button"
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
        >
          {selectedMerchant ? <span className="mt-scope-dot" aria-hidden /> : <MerchantIcon />}
          <span>{selectedMerchant?.name ?? 'All merchants'}</span>
          <span className={`mt-caret ${open ? 'up' : ''}`}>v</span>
        </button>
        {open ? (
          <div className="mt-scope-menu">
            <label className="mt-scope-search">
              <span className="mt-scope-search-icon" aria-hidden>
                <svg width="14" height="14" viewBox="0 0 18 18" fill="none">
                  <circle cx="8" cy="8" r="4.6" stroke="currentColor" strokeWidth="1.4" />
                  <path
                    d="M11.4 11.4 15 15"
                    stroke="currentColor"
                    strokeWidth="1.4"
                    strokeLinecap="round"
                  />
                </svg>
              </span>
              <input
                ref={searchRef}
                className="mt-scope-search-input"
                value={merchantSearch}
                onChange={(event) => setMerchantSearch(event.target.value)}
                placeholder="Search merchants"
                aria-label="Search merchants"
              />
            </label>
            <div className="mt-scope-list" role="listbox">
              <button
                className={`mt-scope-opt ${selectedMerchant ? '' : 'on'}`}
                type="button"
                role="option"
                aria-selected={!selectedMerchant}
                onClick={() => {
                  onMerchant(null)
                  setOpen(false)
                }}
              >
                <span className="mt-scope-opt-name">All merchants</span>
                <span className="mt-mono mt-scope-opt-count">{totalProductCount}</span>
              </button>
              <div className="mt-scope-sep" />
              {filteredMerchants.map((merchant) => (
                <button
                  key={merchant.id}
                  className={`mt-scope-opt ${selectedMerchant?.id === merchant.id ? 'on' : ''}`}
                  type="button"
                  role="option"
                  aria-selected={selectedMerchant?.id === merchant.id}
                  onClick={() => {
                    onMerchant(merchant)
                    setOpen(false)
                  }}
                >
                  <span className="mt-scope-opt-main">
                    <span className="mt-scope-opt-name">{merchant.name}</span>
                    <span className="mt-mono mt-scope-opt-domain">{merchant.domain}</span>
                  </span>
                  <span className="mt-mono mt-scope-opt-count">
                    {merchantCounts.get(merchant.id) ?? 0}
                  </span>
                </button>
              ))}
              {filteredMerchants.length === 0 ? (
                <div className="mt-scope-empty mt-mono">No merchants found</div>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
let discoverChatMessageSequence = 0
const nextDiscoverChatMessageId = () => {
  discoverChatMessageSequence += 1
  return `discover-chat-${discoverChatMessageSequence}`
}
const NEWSLETTER_SUBSCRIBED_MESSAGE =
  'You are subscribed to the newsletter. If you want to unsubscribe, you can do so in your account settings.'

function deriveDiscoverChatTitle(text: string): string {
  const normalized = text.trim().replace(/\s+/g, ' ')
  if (!normalized) {
    return DEFAULT_DISCOVER_CHAT_TITLE
  }
  return normalized.length > 24 ? `${normalized.slice(0, 22)}...` : normalized
}

function resolveStateAction<T>(action: SetStateAction<T>, current: T): T {
  return typeof action === 'function' ? (action as (previous: T) => T)(current) : action
}

function discoverBlockPrimaryProduct(block: DiscoverChatBlock): Product | null {
  if (block.type === 'products') {
    return block.products[0] ?? null
  }
  if (block.type === 'similar') {
    return block.products[0] ?? block.product
  }
  if (block.type === 'decision') {
    return block.product
  }
  if (
    block.type === 'reviews' ||
    block.type === 'code' ||
    block.type === 'watch' ||
    block.type === 'friendvote' ||
    block.type === 'added'
  ) {
    return block.product
  }
  if (block.type === 'saved') {
    return block.products[0] ?? null
  }
  if (block.type === 'cart') {
    return block.products?.[0] ?? null
  }
  if (block.type === 'minicompare') {
    return block.products[block.pickIndex] ?? block.products[0] ?? null
  }
  return null
}

function discoverBlockHasProduct(block: DiscoverChatBlock, productId: ProductId): boolean {
  if (block.type === 'products' || block.type === 'saved' || block.type === 'minicompare') {
    return block.products.some((product) => product.id === productId)
  }
  if (block.type === 'similar') {
    return (
      block.product.id === productId || block.products.some((product) => product.id === productId)
    )
  }
  if (block.type === 'cart') {
    return (
      block.lines.some((line) => line.id === productId) ||
      Boolean(block.products?.some((product) => product.id === productId))
    )
  }
  if (
    block.type === 'reviews' ||
    block.type === 'code' ||
    block.type === 'decision' ||
    block.type === 'watch' ||
    block.type === 'friendvote' ||
    block.type === 'added'
  ) {
    return block.product.id === productId
  }
  return false
}

function discoverThreadFocusProduct(
  thread: DiscoverChatThread,
  productsById: ReadonlyMap<ProductId, Product>,
): Product | null {
  if (thread.focusProductId) {
    const focused = productsById.get(thread.focusProductId)
    if (focused) {
      return focused
    }
  }
  for (const message of [...thread.messages].reverse()) {
    for (const block of [...(message.blocks ?? [])].reverse()) {
      const product = discoverBlockPrimaryProduct(block)
      if (product) {
        return product
      }
    }
    if (message.productContext) {
      return message.productContext
    }
  }
  return null
}

function isDiscountCodeQuestion(question: string): boolean {
  return /\bcode\b|\bcoupon\b|\bdiscount\b|\bpromo\b|\bdeal\b|\bcheaper\b|\bsave\b/.test(
    question.toLowerCase(),
  )
}

function foundDiscountCodeFromProfile(code: DiscountCodeProfile) {
  return {
    code: code.code,
    title: code.title,
    description: code.description,
    sourceUrl: code.sourceUrl,
    confidence: code.confidence,
    restrictions: code.restrictions,
    validUntil: code.validUntil,
    expiresAt: code.expiresAt,
    validationMessage: code.validationMessage,
  }
}

function shelfProductSnapshot(product: Product): ShelfProductSnapshot {
  return {
    productId: product.id,
    name: product.name,
    brand: product.brand,
    category: product.category,
    tone: product.tone,
    priceFrom: product.priceFrom,
    merchants: product.merchants,
    imageUrl: product.imageUrl,
  }
}

function shelfThumbForProduct(product: Product): ShelfThumb {
  return {
    name: product.name,
    tone: product.tone,
    imageUrl: product.imageUrl,
  }
}

function shelfMessageSnapshot(message: DiscoverChatMessage): ShelfMessageSnapshot {
  if (message.role === 'you') {
    return {
      side: 'you',
      title: message.productContext ? 'You asked about' : 'Your message',
      text: message.text ?? message.productContext?.name ?? '',
      thumbs: message.productContext ? [shelfThumbForProduct(message.productContext)] : [],
    }
  }

  let text = message.text ?? ''
  let title = 'Meant'
  const products: Product[] = []

  for (const block of message.blocks ?? []) {
    if ((block.type === 'text' || block.type === 'system') && !text) {
      text = block.text
    }
    if (block.type === 'newsletter') {
      title = 'Coming soon'
      text = "This functionality isn't ready yet. We're working on it!"
    }
    if (block.type === 'products') {
      title = `${block.products.length} match${block.products.length === 1 ? '' : 'es'}`
      products.push(...block.products)
    }
    if (block.type === 'similar') {
      title = 'Similar picks'
      products.push(block.product, ...block.products)
    }
    if (block.type === 'reviews') {
      title = 'Reviews'
      products.push(block.product)
    }
    if (block.type === 'code') {
      title = 'Discount code'
      products.push(block.product)
    }
    if (block.type === 'decision') {
      title = "Meant's pick"
      products.push(block.product)
      if (block.runnerUp) {
        products.push(block.runnerUp)
      }
    }
    if (block.type === 'watch') {
      title = 'Price watch'
      products.push(block.product)
    }
    if (block.type === 'friendvote') {
      title = `${block.person} weighed in`
      text = block.note
      products.push(block.product)
    }
    if (block.type === 'added') {
      title = 'Added to cart'
      products.push(block.product)
    }
    if (block.type === 'saved') {
      title = 'Saved items'
      products.push(...block.products)
    }
    if (block.type === 'orders') {
      title = 'Orders'
    }
    if (block.type === 'prefs') {
      title = 'Preferences'
    }
    if (block.type === 'cart') {
      title = 'Your cart'
    }
    if (block.type === 'checkout') {
      title = 'Checkout'
    }
  }

  const seen = new Set<ProductId>()
  const thumbs = products
    .filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
    .slice(0, 6)
    .map(shelfThumbForProduct)

  return {
    side: 'meant',
    title,
    text,
    thumbs,
  }
}
function productDetailChatBlocks(
  question: string,
  product: Product,
  products: readonly Product[],
  preferences: readonly Preference[],
): readonly DiscoverChatBlock[] {
  const lower = question.toLowerCase()
  if (/\breviews?\b|\bratings?\b|\bpeople say\b|\bfeedback\b/.test(lower)) {
    return [
      {
        type: 'text',
        text: `Here is what I can tell from the current product data for ${product.name}.`,
      },
      { type: 'reviews', product },
    ]
  }
  if (
    /\bsimilar\b|\balternative\b|\blike this\b|\bother option\b|\binstead\b|\bcompare\b/.test(lower)
  ) {
    return [{ type: 'newsletter' }]
  }
  if (/\bmatch\b|\bpreferences?\b|\bfit\b|\bmeant\b/.test(lower)) {
    return [
      { type: 'text', text: resolveAsk(question, product, preferences) },
      { type: 'decision', product, runnerUp: similarChatProducts(product, products)[0] ?? null },
    ]
  }
  return [{ type: 'text', text: resolveAsk(question, product, preferences) }]
}

function similarChatProducts(product: Product, products: readonly Product[]): readonly Product[] {
  const sameCategory = products
    .filter((candidate) => candidate.id !== product.id && candidate.category === product.category)
    .sort((left, right) => right.match - left.match)
  const fallback = products
    .filter((candidate) => candidate.id !== product.id)
    .sort((left, right) => right.match - left.match)
  return (sameCategory.length > 0 ? sameCategory : fallback).slice(0, 4)
}

export function ChatDiscoverView({
  profile,
  greeting,
  products,
  hiddenByShip,
  agentActivities,
  deliveryLocations,
  prompts,
  reply,
  query,
  loading,
  error,
  preferences,
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  merchantsLoading,
  savedProducts,
  cart,
  cartProducts,
  orders,
  shelf,
  shelfFlashMessageId,
  productDetailChatRequest,
  discoverFindRequest,
  newsletter,
  savedSet,
  savePendingSet,
  onSubmit,
  onClear,
  onMerchant,
  onOpen,
  onToggleSave,
  onAddProductToCart,
  onFallbackAddToCart,
  onCompareProducts,
  onCartQty,
  onCartRemove,
  onCheckout,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onOpenShelf,
  onNewsletterChange,
  onShelfAddMessage,
  onShelfAddProduct,
  onProductDetailChatRequestHandled,
  onFlashMessage,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  products: readonly Product[]
  hiddenByShip: number
  agentActivities: readonly AgentActivity[]
  deliveryLocations: readonly UserLocation[]
  prompts: readonly string[]
  reply: string | null
  query: string
  loading: boolean
  error: string | null
  preferences: readonly Preference[]
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  merchantsLoading: boolean
  savedProducts: readonly Product[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  orders: readonly Order[]
  shelf: readonly ShelfItem[]
  shelfFlashMessageId: string | null
  productDetailChatRequest: ProductDetailChatRequest | null
  discoverFindRequest: DiscoverFindRequest | null
  newsletter: boolean
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  onSubmit: (query: string) => void
  onClear: () => void
  onMerchant: (merchant: MerchantProfile | null) => void
  onOpen: (product: Product, products?: readonly Product[]) => void
  onToggleSave: (product: Product) => void
  onAddProductToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  onFallbackAddToCart: (product: Product, offer: Offer) => void
  onCompareProducts: (products: readonly Product[]) => void
  onCartQty: (id: ProductId, merchant: string, qty: number) => void
  onCartRemove: (id: ProductId, merchant: string) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onOpenShelf: () => void
  onNewsletterChange: (newsletter: boolean) => Promise<void> | void
  onShelfAddMessage: (payload: Extract<ShelfDragPayload, { kind: 'message' }>) => void
  onShelfAddProduct: (snapshot: ShelfProductSnapshot) => void
  onProductDetailChatRequestHandled: (requestId: string) => void
  onFlashMessage: (messageId: string) => void
}>) {
  const [threads, setThreads] = useState<DiscoverChatThread[]>(() => [createDiscoverChatThread()])
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null)
  const [archivedThreads, setArchivedThreads] = useState<DiscoverChatThread[]>([])
  const [discoverHistoryLoaded, setDiscoverHistoryLoaded] = useState(false)
  const fallbackThread = useMemo(() => createDiscoverChatThread(), [])
  const activeThread =
    threads.find((thread) => thread.id === activeThreadId) ?? threads[0] ?? fallbackThread
  const activeThreadIdSafe = activeThread.id
  const messages = activeThread.messages
  const [activeSearchTarget, setActiveSearchTarget] = useState<{
    threadId: string
    messageId: string
  } | null>(null)
  const [arrivalMessageId, setArrivalMessageId] = useState<string | null>(null)
  const [shareOpen, setShareOpen] = useState(false)
  const [pinnedIds, setPinnedIds] = useStoredState<ProductId[]>('meant.chatPinned', [])
  const [trayClearing, setTrayClearing] = useState(false)
  const [newsletterPending, setNewsletterPending] = useState(false)
  const chatBottomRef = useRef<HTMLDivElement | null>(null)
  const previousMessageCountRef = useRef(messages.length)
  const previousActiveThreadIdRef = useRef(activeThreadIdSafe)
  const didInitialScrollRef = useRef(false)
  const handledDiscoverFindRequestRef = useRef<string | null>(null)
  const scheduledChatTimersRef = useRef<number[]>([])
  const deletedDiscoverThreadIdsRef = useRef(new Set<string>())
  const skipNextDiscoverHistorySyncRef = useRef(false)
  const pinnedSet = useMemo(() => new Set(pinnedIds), [pinnedIds])
  const watchedSet = useMemo(() => new Set<ProductId>(), [])
  const shelfMessageSet = useMemo(
    () =>
      new Set(
        shelf
          .filter(
            (item): item is Extract<ShelfItem, { kind: 'message' }> => item.kind === 'message',
          )
          .map((item) => item.messageId),
      ),
    [shelf],
  )
  const shelfProductSet = useMemo(
    () =>
      new Set(
        shelf
          .filter(
            (item): item is Extract<ShelfItem, { kind: 'product' }> => item.kind === 'product',
          )
          .map((item) => item.productId),
      ),
    [shelf],
  )
  const scheduleChatTimer = useCallback((callback: () => void, delay: number) => {
    const timer = window.setTimeout(() => {
      scheduledChatTimersRef.current = scheduledChatTimersRef.current.filter(
        (candidate) => candidate !== timer,
      )
      callback()
    }, delay)
    scheduledChatTimersRef.current = [...scheduledChatTimersRef.current, timer]
  }, [])
  const displayProducts = useMemo(() => {
    if (products.length > 0) {
      return products
    }
    if (savedProducts.length > 0) {
      return savedProducts
    }
    return PRODUCTS.slice(0, 4)
  }, [products, savedProducts])
  const knownProductsById = useMemo(() => {
    const next = new Map<ProductId, Product>()
    for (const product of [...PRODUCTS, ...savedProducts, ...cartProducts, ...displayProducts]) {
      next.set(product.id, product)
    }
    return next
  }, [cartProducts, displayProducts, savedProducts])
  const historyThreads = useMemo(() => {
    const byId = new Map<string, DiscoverChatThread>()
    for (const thread of [...threads, ...archivedThreads]) {
      if (!hasDiscoverThreadHistory(thread)) {
        continue
      }
      const current = byId.get(thread.id)
      if (!current || (discoverThreadTime(thread) ?? 0) >= (discoverThreadTime(current) ?? 0)) {
        byId.set(thread.id, thread)
      }
    }
    return Array.from(byId.values())
  }, [archivedThreads, threads])

  useEffect(() => {
    const controller = new AbortController()
    getDiscoverConversations({ signal: controller.signal })
      .then((profiles) => {
        if (controller.signal.aborted) {
          return
        }
        const restoredThreads = profiles
          .map(discoverThreadFromProfile)
          .filter((thread): thread is DiscoverChatThread => Boolean(thread))
        if (restoredThreads.length === 0) {
          const localThreads = initialDiscoverChatThreads()
          setThreads(localThreads.length > 0 ? localThreads : [createDiscoverChatThread()])
          setArchivedThreads([])
          setActiveThreadId(localThreads[0]?.id ?? null)
          setDiscoverHistoryLoaded(true)
          return
        }

        const activeThreads = restoredThreads.filter((thread) => thread.archived !== true)
        const nextArchivedThreads = restoredThreads.filter((thread) => thread.archived === true)
        const nextThreads = activeThreads.length > 0 ? activeThreads : [createDiscoverChatThread()]
        skipNextDiscoverHistorySyncRef.current = true
        setThreads(nextThreads)
        setArchivedThreads(nextArchivedThreads)
        setActiveThreadId(nextThreads[0]?.id ?? null)
        setDiscoverHistoryLoaded(true)
      })
      .catch(() => {
        if (controller.signal.aborted) {
          return
        }
        const localThreads = initialDiscoverChatThreads()
        setThreads(localThreads.length > 0 ? localThreads : [createDiscoverChatThread()])
        setArchivedThreads([])
        setActiveThreadId(localThreads[0]?.id ?? null)
        setDiscoverHistoryLoaded(true)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!discoverHistoryLoaded) {
      return undefined
    }
    if (skipNextDiscoverHistorySyncRef.current) {
      skipNextDiscoverHistorySyncRef.current = false
      return undefined
    }
    const controller = new AbortController()
    const snapshots = [
      ...threads.map((thread) => ({ thread, archived: false })),
      ...archivedThreads.map((thread) => ({ thread, archived: true })),
    ].filter(({ thread }) => hasDiscoverThreadHistory(thread))
    const timer = window.setTimeout(() => {
      snapshots.forEach(({ thread, archived }) => {
        if (deletedDiscoverThreadIdsRef.current.has(thread.id)) {
          return
        }
        void saveDiscoverConversation({
          conversationId: thread.id,
          title: discoverThreadTitle(thread),
          threadJson: discoverThreadJson(thread, archived),
          signal: controller.signal,
        }).catch(() => undefined)
      })
    }, DISCOVER_HISTORY_SYNC_DELAY)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [archivedThreads, discoverHistoryLoaded, threads])

  const pinnedProducts = pinnedIds
    .map((id) => displayProducts.find((product) => product.id === id))
    .filter((product): product is Product => Boolean(product))
  const currentCartLines = cartLines(cart, cartProducts)
  const activeSearchProducts = useMemo(
    () => (query ? products.filter(isRenderableSearchProduct) : []),
    [products, query],
  )

  useEffect(() => {
    if (!discoverFindRequest || handledDiscoverFindRequestRef.current === discoverFindRequest.id) {
      return
    }
    handledDiscoverFindRequestRef.current = discoverFindRequest.id

    const orderedThreads = [...threads].sort((left, right) => {
      if (left.id === activeThreadIdSafe) {
        return -1
      }
      if (right.id === activeThreadIdSafe) {
        return 1
      }
      return (right.updatedAt ?? 0) - (left.updatedAt ?? 0)
    })
    for (const thread of orderedThreads) {
      for (let index = thread.messages.length - 1; index >= 0; index -= 1) {
        const message = thread.messages[index]
        const matchesRequest =
          discoverFindRequest.kind === 'message'
            ? message.id === discoverFindRequest.messageId
            : message.productContext?.id === discoverFindRequest.productId ||
              Boolean(
                message.blocks?.some((block) =>
                  discoverBlockHasProduct(block, discoverFindRequest.productId),
                ),
              )
        if (!matchesRequest) {
          continue
        }
        setActiveThreadId(thread.id)
        const timeoutId = window.setTimeout(() => {
          const element = document.querySelector(`[data-mid="${CSS.escape(message.id)}"]`)
          if (!(element instanceof HTMLElement)) {
            return
          }
          const top = window.scrollY + element.getBoundingClientRect().top - 150
          window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
          onFlashMessage(message.id)
        }, 160)
        return () => {
          window.clearTimeout(timeoutId)
        }
      }
    }
  }, [activeThreadIdSafe, discoverFindRequest, onFlashMessage, setActiveThreadId, threads])

  useEffect(() => {
    if (threads.some((thread) => !thread.createdAt || !thread.updatedAt)) {
      setThreads((current) => normalizeDiscoverChatThreads(current))
    }
  }, [setThreads, threads])

  useEffect(() => {
    if (archivedThreads.some((thread) => !thread.createdAt || !thread.updatedAt)) {
      setArchivedThreads((current) => normalizeDiscoverChatThreads(current))
    }
  }, [archivedThreads, setArchivedThreads])

  const scrollChatToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    const scroll = () => {
      chatBottomRef.current?.scrollIntoView({ behavior, block: 'end' })
    }
    window.requestAnimationFrame(() => {
      scroll()
      window.requestAnimationFrame(scroll)
      window.setTimeout(scroll, 120)
      window.setTimeout(scroll, 360)
    })
  }, [])

  useEffect(() => {
    if (!didInitialScrollRef.current) {
      didInitialScrollRef.current = true
      if (messages.length > 0) {
        scrollChatToBottom('auto')
      }
      previousMessageCountRef.current = messages.length
      previousActiveThreadIdRef.current = activeThreadIdSafe
      return
    }
    const messageCountIncreased = messages.length > previousMessageCountRef.current
    const activeThreadChanged = activeThreadIdSafe !== previousActiveThreadIdRef.current
    if (activeThreadChanged) {
      if (messages.length > 0) {
        scrollChatToBottom()
      }
      setArrivalMessageId(null)
    } else if (messageCountIncreased) {
      scrollChatToBottom()
      let nextArrivalMessageId: string | null = null
      for (let index = messages.length - 1; index >= previousMessageCountRef.current; index -= 1) {
        const message = messages[index]
        if (message?.role === 'ai') {
          nextArrivalMessageId = message.id
          break
        }
      }
      setArrivalMessageId(nextArrivalMessageId)
    }
    previousMessageCountRef.current = messages.length
    previousActiveThreadIdRef.current = activeThreadIdSafe
  }, [activeThreadIdSafe, messages, scrollChatToBottom])

  useEffect(
    () => () => {
      for (const timer of scheduledChatTimersRef.current) {
        window.clearTimeout(timer)
      }
      scheduledChatTimersRef.current = []
    },
    [],
  )

  useEffect(() => {
    if (threads.length === 0) {
      const nextThread = createDiscoverChatThread()
      setThreads([nextThread])
      setActiveThreadId(nextThread.id)
      return
    }
    if (!threads.some((thread) => thread.id === activeThreadId)) {
      setActiveThreadId(threads[0].id)
    }
  }, [activeThreadId, setActiveThreadId, setThreads, threads])

  useEffect(() => {
    if (pinnedProducts.length === 0 && trayClearing) {
      setTrayClearing(false)
    }
  }, [pinnedProducts.length, trayClearing])

  const updateThreadMessages = useCallback(
    (threadId: string, action: SetStateAction<readonly DiscoverChatMessage[]>) => {
      const now = Date.now()
      setThreads((current) =>
        current.map((thread) =>
          thread.id === threadId
            ? { ...thread, messages: resolveStateAction(action, thread.messages), updatedAt: now }
            : thread,
        ),
      )
    },
    [setThreads],
  )

  const setMessages = useCallback(
    (action: SetStateAction<readonly DiscoverChatMessage[]>) => {
      updateThreadMessages(activeThreadIdSafe, action)
    },
    [activeThreadIdSafe, updateThreadMessages],
  )

  const appendMessagesToActiveThread = useCallback(
    (
      nextMessages: readonly DiscoverChatMessage[],
      options: { titleSeed?: string; focusProductId?: ProductId } = {},
    ) => {
      const now = Date.now()
      setThreads((current) =>
        current.map((thread) => {
          if (thread.id !== activeThreadIdSafe) {
            return thread
          }
          const shouldTitle = thread.messages.length === 0 && !thread.named && options.titleSeed
          return {
            ...thread,
            title: shouldTitle ? deriveDiscoverChatTitle(options.titleSeed ?? '') : thread.title,
            focusProductId: options.focusProductId ?? thread.focusProductId,
            messages: [...thread.messages, ...nextMessages],
            updatedAt: now,
          }
        }),
      )
    },
    [activeThreadIdSafe, setThreads],
  )

  useEffect(() => {
    if (!activeSearchTarget) {
      return
    }
    updateThreadMessages(activeSearchTarget.threadId, (current) =>
      current.map((message) => {
        if (message.id !== activeSearchTarget.messageId) {
          return message
        }
        const statusText = error
          ? 'Live product search is unavailable, so I mocked a starter shortlist from the demo catalog.'
          : (reply ??
            (activeSearchProducts.length > 0
              ? `I found ${activeSearchProducts.length} match${activeSearchProducts.length === 1 ? '' : 'es'} so far.`
              : 'Searching across supported merchants...'))
        return {
          ...message,
          pending: loading,
          blocks: [
            { type: 'text', text: statusText },
            { type: 'products', products: activeSearchProducts, query },
          ],
        }
      }),
    )
    scrollChatToBottom()
    if (!loading && (reply || error)) {
      setActiveSearchTarget(null)
    }
  }, [
    activeSearchTarget,
    activeSearchProducts,
    error,
    loading,
    query,
    reply,
    scrollChatToBottom,
    updateThreadMessages,
  ])

  const appendMessagePair = (text: string, blocks: readonly DiscoverChatBlock[]) => {
    appendMessagesToActiveThread(
      [
        { id: nextDiscoverChatMessageId(), role: 'you', text },
        { id: nextDiscoverChatMessageId(), role: 'ai', blocks },
      ],
      { titleSeed: text },
    )
  }

  const appendUnavailableFeatureMessage = (product?: Product) => {
    appendMessagesToActiveThread(
      [
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [{ type: 'newsletter' }],
        },
      ],
      product ? { focusProductId: product.id } : undefined,
    )
    scrollChatToBottom()
  }

  const subscribeToNewsletter = async () => {
    if (newsletter || newsletterPending) {
      return
    }
    setNewsletterPending(true)
    try {
      await onNewsletterChange(true)
      appendMessagesToActiveThread([
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [{ type: 'system', text: NEWSLETTER_SUBSCRIBED_MESSAGE }],
        },
      ])
      scrollChatToBottom()
    } catch {
      appendMessagesToActiveThread([
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [
            {
              type: 'system',
              text: 'Could not update newsletter settings. Please try again.',
            },
          ],
        },
      ])
      scrollChatToBottom()
    } finally {
      setNewsletterPending(false)
    }
  }

  const discountOfferForProduct = useCallback(
    (product: Product): Offer | null => {
      if (product.offers.length === 0) {
        return null
      }
      const preferred = bestOffer(product, deliveryLocations)
      if (offerCartable(preferred) || canResolveCartOffer(product, preferred)) {
        return preferred
      }
      return (
        product.offers.find(offerCartable) ??
        product.offers.find((offer) => canResolveCartOffer(product, offer)) ??
        null
      )
    },
    [deliveryLocations],
  )

  const resolveDiscountSearchContext = useCallback(
    async (
      product: Product,
      location: UserLocation | undefined,
    ): Promise<
      | { ok: true; product: Product; offer: Offer; productVariantId: string }
      | { ok: false; offer: Offer | null; message: string }
    > => {
      const offer = discountOfferForProduct(product)
      if (!offer) {
        return {
          ok: false,
          offer: null,
          message: 'Discount search needs a cartable merchant offer for this item.',
        }
      }

      const resolved = await resolveCartableOffer({ product, offer, location })
      if (!resolved.ok) {
        return {
          ok: false,
          offer: resolved.offer,
          message: resolved.message,
        }
      }
      return {
        ok: true,
        product: resolved.product,
        offer: resolved.offer,
        productVariantId: resolved.productVariantId,
      }
    },
    [discountOfferForProduct],
  )

  const updateDiscountCodeMessage = useCallback(
    (threadId: string, messageId: string, blocks: readonly DiscoverChatBlock[]) => {
      updateThreadMessages(threadId, (current) =>
        current.map((message) =>
          message.id === messageId
            ? {
                ...message,
                pending: false,
                blocks,
              }
            : message,
        ),
      )
      scrollChatToBottom()
    },
    [scrollChatToBottom, updateThreadMessages],
  )

  const appendDiscountCodeSearch = useCallback(
    (product: Product, question = `Find a code for ${product.name}.`) => {
      const threadId = activeThreadIdSafe
      const aiId = nextDiscoverChatMessageId()
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'you',
            text: question,
            productContext: product,
          },
          {
            id: aiId,
            role: 'ai',
            pending: true,
            pendingText: 'Meant is searching for discount codes. It can take few minutes.',
            blocks: [],
          },
        ],
        { titleSeed: question, focusProductId: product.id },
      )

      const primaryLocation = deliveryLocations[0]
      const initialOffer = discountOfferForProduct(product)
      const search = async () => {
        const context = await resolveDiscountSearchContext(product, primaryLocation)
        if (!context.ok) {
          updateDiscountCodeMessage(threadId, aiId, [
            {
              type: 'code',
              product,
              merchant: context.offer?.merchant,
              status: 'error',
              message: context.message,
            },
          ])
          return
        }

        const result = await searchDiscountCodes({
          merchantId: context.offer.merchantId,
          merchantDomain: context.offer.merchantDomain,
          items: [{ productVariantId: context.productVariantId, quantity: 1 }],
          buyerIdentity: primaryLocation ? { countryCode: primaryLocation.code } : undefined,
          deliveryAddressesToAdd: primaryLocation
            ? [
                {
                  city: primaryLocation.city,
                  countryCode: primaryLocation.code,
                },
              ]
            : undefined,
        })
        const codes = result.codes.map(foundDiscountCodeFromProfile)
        updateDiscountCodeMessage(threadId, aiId, [
          {
            type: 'text',
            text:
              codes.length > 0
                ? `${codes.length} validated code${codes.length === 1 ? '' : 's'} accepted by ${context.offer.merchant}.`
                : `No valid code was accepted by ${context.offer.merchant} for this item right now.`,
          },
          {
            type: 'code',
            product: context.product,
            merchant: context.offer.merchant,
            codes,
            cached: result.cached,
            searchedAt: result.searchedAt,
            expiresAt: result.expiresAt,
            status: codes.length > 0 ? 'found' : 'empty',
            message:
              codes.length === 0
                ? `No valid code accepted by ${context.offer.merchant} for this item right now.`
                : undefined,
          },
        ])
      }

      void search().catch((error: unknown) => {
        updateDiscountCodeMessage(threadId, aiId, [
          {
            type: 'code',
            product,
            merchant: initialOffer?.merchant,
            status: 'error',
            message:
              error instanceof Error
                ? error.message
                : 'Discount code search is unavailable right now.',
          },
        ])
      })
    },
    [
      activeThreadIdSafe,
      appendMessagesToActiveThread,
      deliveryLocations,
      discountOfferForProduct,
      resolveDiscountSearchContext,
      updateDiscountCodeMessage,
    ],
  )

  const appendProductDetailQuestion = useCallback(
    (product: Product, question: string, requestProducts: readonly Product[]) => {
      if (isDiscountCodeQuestion(question)) {
        appendDiscountCodeSearch(product, question)
        return
      }
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'you',
            text: question,
            productContext: product,
          },
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: productDetailChatBlocks(question, product, requestProducts, preferences),
          },
        ],
        { titleSeed: question, focusProductId: product.id },
      )
    },
    [appendDiscountCodeSearch, appendMessagesToActiveThread, preferences],
  )

  useEffect(() => {
    if (!productDetailChatRequest) {
      return
    }
    const requestProducts = displayProducts.some(
      (candidate) => candidate.id === productDetailChatRequest.product.id,
    )
      ? displayProducts
      : [productDetailChatRequest.product, ...displayProducts]
    appendProductDetailQuestion(
      productDetailChatRequest.product,
      productDetailChatRequest.question,
      requestProducts,
    )
    onProductDetailChatRequestHandled(productDetailChatRequest.id)
  }, [
    appendProductDetailQuestion,
    displayProducts,
    onProductDetailChatRequestHandled,
    productDetailChatRequest,
  ])

  const deleteMessage = (messageId: string) => {
    setMessages((current) => current.filter((message) => message.id !== messageId))
    if (activeSearchTarget?.messageId === messageId) {
      setActiveSearchTarget(null)
    }
  }

  const updateChatCartBlock = useCallback(
    (messageId: string, blockIndex: number, nextCart: readonly CartItem[]) => {
      updateThreadMessages(activeThreadIdSafe, (current) =>
        current.flatMap((message) => {
          if (message.id !== messageId) {
            return [message]
          }
          const blocks = [...(message.blocks ?? [])]
          const block = blocks[blockIndex]
          if (!block || block.type !== 'cart') {
            return [message]
          }
          if (nextCart.length === 0) {
            const nextBlocks = blocks.filter((_, index) => index !== blockIndex)
            const hasVisibleBlocks = nextBlocks.some(
              (nextBlock) => nextBlock.type !== 'text' && nextBlock.type !== 'system',
            )
            return hasVisibleBlocks ? [{ ...message, blocks: nextBlocks }] : []
          }
          blocks[blockIndex] = { ...block, lines: nextCart }
          return [{ ...message, blocks }]
        }),
      )
    },
    [activeThreadIdSafe, updateThreadMessages],
  )

  const updateChatCartQty = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    qty: number,
    nextCart: readonly CartItem[],
  ) => {
    onCartQty(id, merchant, qty)
    updateChatCartBlock(messageId, blockIndex, nextCart)
  }

  const removeChatCartLine = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
  ) => {
    onCartRemove(id, merchant)
    updateChatCartBlock(messageId, blockIndex, nextCart)
  }

  const addMessageToShelf = (message: DiscoverChatMessage, sourceElement: HTMLElement) => {
    if (!shelfMessageSet.has(message.id)) {
      flyToShelf(sourceElement, message.role === 'you' ? 'var(--accent)' : 'var(--accent-tint)')
    }
    onShelfAddMessage({
      kind: 'message',
      messageId: message.id,
      snapshot: shelfMessageSnapshot(message),
    })
    onOpenShelf()
  }

  const addProductToShelf = (product: Product, sourceElement: HTMLElement) => {
    if (!shelfProductSet.has(product.id)) {
      flyToShelf(sourceElement, product.tone)
    }
    onShelfAddProduct(shelfProductSnapshot(product))
    onOpenShelf()
  }

  const dragMessage = (event: ReactDragEvent<HTMLElement>, message: DiscoverChatMessage) => {
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData(
      SHELF_DRAG_MIME,
      JSON.stringify({
        kind: 'message',
        messageId: message.id,
        snapshot: shelfMessageSnapshot(message),
      } satisfies ShelfDragPayload),
    )
    document.body.classList.add('mt-dragging')
    onOpenShelf()
  }

  const dragProduct = (event: ReactDragEvent<HTMLElement>, product: Product) => {
    event.stopPropagation()
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData(
      SHELF_DRAG_MIME,
      JSON.stringify({
        kind: 'product',
        snapshot: shelfProductSnapshot(product),
      } satisfies ShelfDragPayload),
    )
    document.body.classList.add('mt-dragging')
    onOpenShelf()
  }

  const runSearchInChat = (text: string) => {
    const aiId = nextDiscoverChatMessageId()
    appendMessagesToActiveThread(
      [
        { id: nextDiscoverChatMessageId(), role: 'you', text },
        {
          id: aiId,
          role: 'ai',
          query: text,
          pending: true,
          blocks: [{ type: 'text', text: 'Searching across supported merchants...' }],
        },
      ],
      { titleSeed: text },
    )
    setActiveSearchTarget({ threadId: activeThreadIdSafe, messageId: aiId })
    onSubmit(text)
  }

  const submit = (text: string) => {
    const normalized = text.trim()
    if (!normalized) {
      return
    }
    const lower = normalized.toLowerCase()
    if (/\border history\b|\borders?\b|\bpurchases?\b/.test(lower)) {
      appendMessagePair(normalized, [
        {
          type: 'text',
          text: 'Here are your recent orders. The full order view stays connected to the backend.',
        },
        { type: 'orders', orders },
      ])
      return
    }
    if (/\bsaved\b|\bshortlist\b|\bwishlist\b/.test(lower)) {
      appendMessagePair(normalized, [
        { type: 'text', text: 'Here are the products you saved.' },
        { type: 'saved', products: savedProducts },
      ])
      return
    }
    if (/\bpreferences?\b|\bfilters?\b|\bprofile\b/.test(lower)) {
      appendMessagePair(normalized, [
        { type: 'text', text: 'These are the preferences shaping every recommendation.' },
        { type: 'prefs', preferences },
      ])
      return
    }
    if (/\bcompare\b|\bversus\b|\bvs\b|\bbetter\b/.test(lower)) {
      const compareBlock = createMiniCompareBlock(
        pinnedProducts.length >= 2 ? pinnedProducts : displayProducts,
        deliveryLocations,
      )
      appendMessagePair(
        normalized,
        compareBlock
          ? [
              {
                type: 'text',
                text:
                  pinnedProducts.length >= 2
                    ? 'Here is the pinned comparison, without leaving the chat.'
                    : 'Here is a quick comparison from the current shortlist.',
              },
              compareBlock,
            ]
          : [
              {
                type: 'system',
                text: 'Pin at least two products or search for a shortlist before comparing.',
              },
            ],
      )
      return
    }
    if (isDiscountCodeQuestion(normalized)) {
      const targetProduct =
        discoverThreadFocusProduct(activeThread, knownProductsById) ??
        currentCartLines[0]?.product ??
        displayProducts[0] ??
        null
      if (targetProduct) {
        appendDiscountCodeSearch(targetProduct, normalized)
      } else {
        appendMessagePair(normalized, [
          {
            type: 'system',
            text: 'Open a product first so I can search codes against a merchant cart.',
          },
        ])
      }
      return
    }
    if (/\bcart\b|\bbasket\b/.test(lower) && !/\badd\b/.test(lower)) {
      appendMessagePair(normalized, [
        {
          type: 'text',
          text: 'Here is your cart. You can adjust quantities, remove items, and start checkout right here.',
        },
        { type: 'cart', lines: cart },
      ])
      return
    }
    if (/\bcheckout\b|\bpay\b|\bbuy\b/.test(lower)) {
      appendMessagePair(normalized, [
        {
          type: 'text',
          text: 'Here is checkout inside the chat, grouped by merchant.',
        },
        {
          type: 'checkout',
          merchantCount: new Set(currentCartLines.map((line) => line.merchant)).size,
        },
      ])
      return
    }
    runSearchInChat(normalized)
  }

  const addCartFromChat = async (product: Product) => {
    const offer = bestOffer(product, deliveryLocations)
    const synced = offerCartable(offer) || canResolveCartOffer(product, offer)
    if (synced) {
      try {
        const added = await onAddProductToCart(product, offer)
        if (!added) {
          onFallbackAddToCart(product, offer)
        }
      } catch {
        onFallbackAddToCart(product, offer)
      }
    } else {
      onFallbackAddToCart(product, offer)
    }
    appendMessagesToActiveThread(
      [
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [
            {
              type: 'added',
              product,
              merchant: offer.merchant,
              synced,
              price: offer.price,
              count: cart.reduce((sum, item) => sum + item.qty, 0) + 1,
            },
          ],
        },
      ],
      { focusProductId: product.id },
    )
  }

  const restoreCartLineFromReview = async (product: Product, merchant: string, price?: number) => {
    if (cart.some((item) => item.id === product.id && item.merchant === merchant)) {
      return
    }
    const merchantOffer = product.offers.find((offer) => offer.merchant === merchant)
    if (
      merchantOffer &&
      (offerCartable(merchantOffer) || canResolveCartOffer(product, merchantOffer))
    ) {
      try {
        const added = await onAddProductToCart(product, merchantOffer)
        if (added) {
          return
        }
      } catch {
        // Fall back to the local cart path below.
      }
      onFallbackAddToCart(product, merchantOffer)
      return
    }
    onFallbackAddToCart(
      product,
      merchantOffer ?? {
        merchant,
        price: price ?? productPriceFrom(product, deliveryLocations),
        delivery: 'Available from merchant',
        available: true,
      },
    )
  }

  const digIntoProduct = (kind: 'reviews' | 'code' | 'similar', product: Product) => {
    if (kind === 'reviews') {
      appendMessagePair(`What do reviewers say about ${product.name}?`, [
        {
          type: 'text',
          text: `Here is what I can tell from the current product data for ${product.name}.`,
        },
        { type: 'reviews', product },
      ])
      return
    }
    if (kind === 'code') {
      appendDiscountCodeSearch(product)
      return
    }
    if (kind === 'similar') {
      appendUnavailableFeatureMessage(product)
      return
    }
  }

  const chooseOne = (candidates: readonly Product[]) => {
    const ranked = [...candidates].sort((left, right) => right.match - left.match)
    const pick = ranked[0]
    if (!pick) {
      return
    }
    appendMessagePair('Just pick one for me.', [
      { type: 'text', text: 'Done. I would buy this one.' },
      { type: 'decision', product: pick, runnerUp: ranked[1] ?? null },
    ])
  }

  const compareHere = (candidates: readonly Product[]) => {
    const compareBlock = createMiniCompareBlock(candidates, deliveryLocations)
    if (!compareBlock) {
      return
    }
    appendMessagePair('Compare these here.', [
      { type: 'text', text: 'I lined them up here so you can decide without leaving the chat.' },
      compareBlock,
    ])
    scrollChatToBottom()
  }

  const showCheckoutHere = () => {
    appendMessagePair('Checkout here.', [
      { type: 'text', text: 'I grouped checkout by merchant and kept it inside the chat.' },
      {
        type: 'checkout',
        merchantCount: new Set(currentCartLines.map((line) => line.merchant)).size,
      },
    ])
  }

  const showCartReviewHere = (
    fallbackLines: readonly CartItem[] = [],
    fallbackProducts: readonly Product[] = [],
  ) => {
    const reviewLines = cartItemsWithFallback(cart, fallbackLines)
    appendMessagesToActiveThread([
      {
        id: nextDiscoverChatMessageId(),
        role: 'ai',
        blocks: [
          {
            type: 'text',
            text: 'Here is the live cart review. You can adjust quantities, remove items, or continue to checkout here.',
          },
          { type: 'cart', lines: reviewLines, products: fallbackProducts },
        ],
      },
    ])
    scrollChatToBottom()
  }

  const togglePin = (product: Product) => {
    const wasPinned = pinnedIds.includes(product.id)
    const nextIds = wasPinned
      ? pinnedIds.filter((id) => id !== product.id)
      : [...pinnedIds, product.id].slice(-4)
    setPinnedIds(nextIds)
  }

  const toggleWatch = (product: Product) => {
    appendUnavailableFeatureMessage(product)
  }

  const newThread = () => {
    const nextThread = createDiscoverChatThread()
    setThreads((current) => [...current, nextThread])
    setActiveThreadId(nextThread.id)
    setActiveSearchTarget(null)
    onClear()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const closeThread = (threadId: string) => {
    const closingIndex = threads.findIndex((thread) => thread.id === threadId)
    const closingThread = threads[closingIndex]
    if (closingThread && hasDiscoverThreadHistory(closingThread)) {
      const archivedThread = { ...closingThread, archived: true, updatedAt: Date.now() }
      setArchivedThreads((current) => [
        archivedThread,
        ...current.filter((thread) => thread.id !== closingThread.id),
      ])
    }
    const nextThreads = threads.filter((thread) => thread.id !== threadId)
    if (nextThreads.length === 0) {
      const nextThread = createDiscoverChatThread()
      setThreads([nextThread])
      setActiveThreadId(nextThread.id)
      setActiveSearchTarget(null)
      onClear()
      return
    }
    setThreads(nextThreads)
    if (threadId === activeThreadIdSafe) {
      const nextActive = nextThreads[Math.max(0, closingIndex - 1)] ?? nextThreads[0]
      setActiveThreadId(nextActive.id)
      setActiveSearchTarget((currentTarget) =>
        currentTarget?.threadId === threadId ? null : currentTarget,
      )
    }
  }

  const selectHistoryThread = (threadId: string) => {
    if (threads.some((thread) => thread.id === threadId)) {
      setActiveThreadId(threadId)
      return
    }
    const archivedThread = archivedThreads.find((thread) => thread.id === threadId)
    if (!archivedThread) {
      return
    }
    setThreads((current) => [
      ...current.filter((thread) => thread.id !== threadId),
      { ...archivedThread, archived: false, updatedAt: Date.now() },
    ])
    setArchivedThreads((current) => current.filter((thread) => thread.id !== threadId))
    setActiveThreadId(threadId)
    setActiveSearchTarget(null)
  }

  const deleteHistoryThread = (threadId: string) => {
    deletedDiscoverThreadIdsRef.current.add(threadId)
    const deletingIndex = threads.findIndex((thread) => thread.id === threadId)
    setArchivedThreads((current) => current.filter((thread) => thread.id !== threadId))
    void deleteDiscoverConversation(threadId).catch(() => undefined)
    if (deletingIndex < 0) {
      return
    }

    const nextThreads = threads.filter((thread) => thread.id !== threadId)
    if (nextThreads.length === 0) {
      const nextThread = createDiscoverChatThread()
      setThreads([nextThread])
      setActiveThreadId(nextThread.id)
      setActiveSearchTarget(null)
      onClear()
      return
    }

    setThreads(nextThreads)
    if (threadId === activeThreadIdSafe) {
      const nextActive = nextThreads[Math.max(0, deletingIndex - 1)] ?? nextThreads[0]
      setActiveThreadId(nextActive.id)
      setActiveSearchTarget(null)
    } else {
      setActiveSearchTarget((currentTarget) =>
        currentTarget?.threadId === threadId ? null : currentTarget,
      )
    }
  }

  const renameThread = (threadId: string, title: string) => {
    const now = Date.now()
    setThreads((current) =>
      current.map((thread) =>
        thread.id === threadId ? { ...thread, title, named: true, updatedAt: now } : thread,
      ),
    )
  }

  const reorderThreads = (fromIndex: number, toIndex: number) => {
    setThreads((current) => {
      const next = [...current]
      const [moved] = next.splice(fromIndex, 1)
      if (!moved) {
        return current
      }
      next.splice(toIndex, 0, moved)
      return next
    })
  }

  const shareThread = (person: string) => {
    setShareOpen(false)
    const sharedThreadId = activeThreadIdSafe
    const sharedProduct = discoverThreadFocusProduct(activeThread, knownProductsById)
    appendMessagesToActiveThread([
      {
        id: nextDiscoverChatMessageId(),
        role: 'ai',
        blocks: [
          {
            type: 'system',
            text: `Shared this chat with ${person}. Their vote will land right here.`,
          },
        ],
      },
    ])
    if (!sharedProduct) {
      return
    }
    const notes = [
      'This one. The reviews sold me - go for it.',
      'Yes, get it. Looks exactly like your style.',
      'Do it - best value of the bunch, honestly.',
    ]
    scheduleChatTimer(() => {
      updateThreadMessages(sharedThreadId, (current) => [
        ...current,
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [
            {
              type: 'friendvote',
              person,
              product: sharedProduct,
              vote: 'up',
              note: notes[Math.floor(Math.random() * notes.length)] ?? notes[0],
            },
          ],
        },
      ])
    }, 4200)
  }

  const messageBlockProps = {
    deliveryLocations,
    preferences,
    savedSet,
    savePendingSet,
    cart,
    cartProducts,
    pinnedSet,
    watchedSet,
    shelfMessageSet,
    shelfProductSet,
    onOpen,
    onToggleSave,
    onAddCart: (product: Product) => void addCartFromChat(product),
    onPin: togglePin,
    onWatch: toggleWatch,
    onDig: digIntoProduct,
    onJustPick: chooseOne,
    onCompareHere: compareHere,
    onOpenFullCompare: onCompareProducts,
    onOpenSaved,
    onOpenOrders,
    onOpenPrefs,
    onOpenCart,
    onReviewCartHere: showCartReviewHere,
    onRestoreCartLine: (product: Product, merchant: string, price?: number) =>
      void restoreCartLineFromReview(product, merchant, price),
    onCartQty: updateChatCartQty,
    onCartRemove: removeChatCartLine,
    onCheckout,
    onCheckoutHere: showCheckoutHere,
    newsletter,
    newsletterPending,
    onNewsletterSignup: () => void subscribeToNewsletter(),
    onDelete: deleteMessage,
    onShelfAddMessage: addMessageToShelf,
    onShelfAddProduct: addProductToShelf,
    onDragMessage: dragMessage,
    onDragProduct: dragProduct,
  }

  const empty = messages.length === 0 && !query
  const activeThreadSearchPending = activeSearchTarget?.threadId === activeThreadIdSafe

  if (empty && threads.length === 1) {
    return (
      <main className="mt-feed mt-ct-feed mt-ct-feed-hero">
        <ChatHero
          profile={profile}
          greeting={greeting}
          prompts={prompts}
          onSubmit={submit}
          loading={loading}
          merchants={merchants}
          selectedMerchant={selectedMerchant}
          merchantCounts={merchantCounts}
          totalProductCount={totalProductCount}
          merchantsLoading={merchantsLoading}
          merchantsError={null}
          historyThreads={historyThreads}
          activeThreadId={activeThreadIdSafe}
          onMerchant={onMerchant}
          onHistorySelect={selectHistoryThread}
          onHistoryDelete={deleteHistoryThread}
        />
      </main>
    )
  }

  return (
    <main className="mt-feed mt-ct-feed">
      <DiscoverThreadTabs
        threads={threads}
        activeId={activeThreadIdSafe}
        onSelect={selectHistoryThread}
        onClose={closeThread}
        onNew={newThread}
        onRename={renameThread}
        onShare={() => setShareOpen(true)}
        onReorder={reorderThreads}
        onDeleteHistory={deleteHistoryThread}
        historyThreads={historyThreads}
      />
      <div className="mt-ct-thread">
        <div className="mt-ct-msg mt-ct-meant mt-ct-greeting">
          <span className="mt-ct-av">
            <SparkMark size={13} />
          </span>
          <div className="mt-ct-meant-body">
            <p className="mt-ct-intro">
              I only surface products that fit your profile. I can also open your live cart, orders,
              saved items, and preferences right here.
            </p>
          </div>
        </div>
        {empty ? (
          <div className="mt-ct-empty-prompts">
            {prompts.map((prompt) => (
              <button
                key={prompt}
                className="mt-ct-suggchip"
                type="button"
                onClick={() => submit(prompt)}
              >
                {prompt}
              </button>
            ))}
            <button
              className="mt-ct-suggchip"
              type="button"
              onClick={() => chooseOne(displayProducts)}
            >
              <SparkMark size={11} />
              Just pick for me
            </button>
          </div>
        ) : null}
        {messages.map((message) => (
          <DiscoverChatMessageRow
            key={message.id}
            message={message}
            flash={shelfFlashMessageId === message.id}
            celebrateArrival={arrivalMessageId === message.id}
            {...messageBlockProps}
          />
        ))}
        {error && !activeThreadSearchPending ? (
          <div className="mt-ct-system">
            Live search is unavailable, so Meant is showing demo products for this chat.
          </div>
        ) : null}
        {agentActivities.length > 0 && loading ? (
          <AgentActivityPanel activities={agentActivities} />
        ) : null}
        {deliveryLocations.length > 0 && hiddenByShip > 0 ? (
          <div className="mt-ship-strip">
            <span>
              Shipping to <strong>{deliveryLocationSummary(deliveryLocations)}</strong>
            </span>
            <span className="mt-ship-strip-hidden mt-mono">
              {hiddenByShip} hidden · cannot reach you
            </span>
          </div>
        ) : null}
        <div ref={chatBottomRef} className="mt-ct-bottom-sentinel" aria-hidden="true" />
      </div>
      {shareOpen ? (
        <DiscoverShareSheet
          thread={activeThread}
          onClose={() => setShareOpen(false)}
          onSend={shareThread}
        />
      ) : null}

      {pinnedProducts.length > 0 ? (
        <DustingContainer
          className="mt-ct-tray"
          dusting={trayClearing}
          onGone={() => {
            setTrayClearing(false)
            setPinnedIds([])
          }}
        >
          <span className="mt-mono mt-ct-tray-label">
            Compare tray
            <br />
            <span className="mt-ct-tray-note">chat picks</span>
          </span>
          <div className="mt-ct-tray-items">
            {pinnedProducts.map((product) => (
              <span className="mt-ct-tray-chip" key={product.id}>
                <span className="mt-ct-tray-thumb">
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                </span>
                {product.name}
                <button
                  className="mt-ct-tray-x"
                  type="button"
                  aria-label={`Remove ${product.name}`}
                  onClick={() =>
                    setPinnedIds((current) => current.filter((id) => id !== product.id))
                  }
                >
                  <CloseIcon size={10} />
                </button>
              </span>
            ))}
            {pinnedProducts.length < 2 ? (
              <span className="mt-ct-tray-hint">Pin one more product to compare.</span>
            ) : null}
          </div>
          <button
            className="mt-ct-tray-mini"
            type="button"
            disabled={pinnedProducts.length < 2 || trayClearing}
            onClick={() => compareHere(pinnedProducts)}
          >
            Compare here
          </button>
          <button
            className="mt-ct-tray-go"
            type="button"
            disabled={pinnedProducts.length < 2 || trayClearing}
            onClick={() => onCompareProducts(pinnedProducts)}
          >
            Full compare
          </button>
          <button
            className="mt-ct-tray-clear"
            type="button"
            onClick={() => setTrayClearing(true)}
            disabled={trayClearing}
          >
            Clear
          </button>
        </DustingContainer>
      ) : null}

      <div className="mt-ct-dock">
        <div className="mt-ct-dock-inner">
          <AskComposer
            placeholder="Ask, compare, show cart, or paste a product idea..."
            suggestions={[]}
            showChips={false}
            onAsk={submit}
            disabled={loading}
          />
        </div>
      </div>
    </main>
  )
}
