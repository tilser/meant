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
  ApiError,
  deleteDiscoverConversation,
  getDiscoverConversation,
  getDiscoverConversations,
  saveDiscoverConversation,
  searchDiscountCodes,
  type DiscountCodeProfile,
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
import { bestOffer, cartLines, productPriceFrom, resolveAsk } from '../utils'
import { resolveCartableOffer } from '../cart/cartOfferResolver'
import type { ActiveCheckoutSession, CheckoutAssistantHandler } from '../cart/checkoutTypes'
import { canResolveCartOffer, offerCartable } from '../cart/utils'
import { AskComposer } from '../ask/AskComposer'
import type { AskReplyDraft } from '../ask/types'
import { flyToShelf } from '../shared/animations'
import { DustingContainer } from '../shared/DustingContainer'
import { MerchantIcon } from '../shared/icons'
import { deliveryLocationSummary } from '../shared/locations'
import { accountSessionStorageKey } from '../shared/accountStorage'
import { useSessionStoredState } from '../shared/storage'
import { CloseIcon, ProductArtwork, SparkMark } from '../shared/ui'
import { productImageUrl } from '../product/productSnapshots'
import type {
  ShelfDragPayload,
  ShelfItem,
  ShelfMessageSnapshot,
  ShelfProductSnapshot,
  ShelfThumb,
} from '../shelf/types'
import { SHELF_DRAG_MIME } from '../shelf/types'
import { AgentActivityPanel } from './AgentActivityPanel'
import { InlineCheckoutBlock } from './blocks/InlineCheckoutBlock'
import { createConversationPersistenceCoordinator } from './conversationPersistence'
import { DiscoverChatMessageRow } from './DiscoverChatMessageRow'
import { DiscoverShareSheet } from './DiscoverShareSheet'
import { DiscoverThreadHistoryButton } from './DiscoverThreadHistoryButton'
import { DiscoverThreadTabs } from './DiscoverThreadTabs'
import { Workbench } from './workbench/Workbench'
import type {
  AgentActivity,
  DiscoverChatBlock,
  DiscoverChatMessage,
  DiscoverChatThread,
  DiscoverFindRequest,
  DiscoverProductSearchTurnInput,
  DiscoverProductSearchTurnResult,
  ProductDetailChatRequest,
} from './types'
import {
  cartItemsWithFallback,
  canRestoreDiscoverThreadAfterConflict,
  createDiscoverChatThread,
  createMiniCompareBlock,
  DEFAULT_DISCOVER_CHAT_TITLE,
  deleteStoredDiscoverChatThread,
  durableDiscoverChatThread,
  discoverSuggestedReplies,
  discoverThreadSearchContext,
  discoverThreadTime,
  initialDiscoverChatThreads,
  mergeDiscoverThreadSources,
  normalizeDiscoverChatThreads,
  saveStoredDiscoverChatThreads,
} from './utils'

const DISCOVER_HISTORY_SYNC_DELAY = 500

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
    const normalized = normalizeDiscoverChatThreads([
      {
        ...snapshot,
        id: profile.conversationId,
        title: snapshot.title?.trim() || profile.title || DEFAULT_DISCOVER_CHAT_TITLE,
        messages,
        archived: snapshot.archived === true,
        createdAt: snapshot.createdAt ?? timestampFromProfile(profile.createdAt),
        updatedAt: snapshot.updatedAt ?? timestampFromProfile(profile.updatedAt),
      },
    ])[0]
    return normalized
      ? durableDiscoverChatThread({ ...normalized, persistedRevision: profile.revision })
      : null
  } catch {
    return null
  }
}

function discoverThreadJson(thread: DiscoverChatThread, archived: boolean): string {
  return JSON.stringify({
    ...durableDiscoverChatThread(thread, archived),
    persistedRevision: undefined,
  })
}

function discoverThreadTitle(thread: DiscoverChatThread): string {
  const title = thread.title.trim() || DEFAULT_DISCOVER_CHAT_TITLE
  return title.length <= 120 ? title : `${title.slice(0, 117)}...`
}

function activeAndArchivedDiscoverThreads(restoredThreads: readonly DiscoverChatThread[]): {
  threads: DiscoverChatThread[]
  archivedThreads: DiscoverChatThread[]
} {
  const archivedThreads = restoredThreads.filter((thread) => thread.archived === true)
  const activeThreads = restoredThreads.filter((thread) => thread.archived !== true)
  return {
    threads: activeThreads.length > 0 ? activeThreads : [createDiscoverChatThread()],
    archivedThreads,
  }
}

function ChatHero({
  profile,
  greeting,
  prompts,
  onSubmit,
  loading,
  historyThreads,
  activeThreadId,
  onHistorySelect,
  onHistoryDelete,
  replyDraft,
  onClearReply,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  prompts: readonly string[]
  onSubmit: (query: string) => void
  loading: boolean
  historyThreads: readonly DiscoverChatThread[]
  activeThreadId: string
  onHistorySelect: (threadId: string) => void
  onHistoryDelete: (threadId: string) => void
  replyDraft: AskReplyDraft | null
  onClearReply: () => void
}>) {
  const [value, setValue] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
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

  useEffect(() => {
    if (!replyDraft) {
      return
    }
    setValue(replyDraft.suggestedText)
    inputRef.current?.focus()
  }, [replyDraft])

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
    onClearReply()
    onSubmit(query)
  }

  return (
    <header className="mt-hero">
      <div className="mt-mono mt-hero-eyebrow">
        {greeting}, {profile.name}
      </div>
      <h1 className="mt-hero-title">
        Everything here is <em>Meant</em> for you.
      </h1>
      <p className="mt-hero-sub">
        Ask for products across supported merchants. Meant already knows you prefer{' '}
        {profile.summary}
      </p>
      {replyDraft ? (
        <div className="mt-ask-replyto mt-hero-replyto">
          <span className="mt-ask-replyto-bar" />
          <span className="mt-ask-replyto-body">
            <span className="mt-mono mt-ask-replyto-key">{replyDraft.label}</span>
            <span className="mt-ask-replyto-text">{replyDraft.text}</span>
          </span>
          <button
            className="mt-ask-replyto-x"
            type="button"
            onClick={onClearReply}
            aria-label="Cancel insight reply"
          >
            <CloseIcon size={11} />
          </button>
        </div>
      ) : null}
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
          ref={inputRef}
          className="mt-search-input"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder='Search with Meant - "a good cotton T-shirt under $50"'
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
        <MerchantScope />
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

function MerchantScope() {
  return (
    <div className="mt-scope">
      <button
        className="mt-scope-btn"
        type="button"
        disabled
        title="Store-specific search requires a verified Shopify Shop ID"
      >
        <MerchantIcon />
        <span>All merchants</span>
      </button>
      <span className="mt-mono mt-scope-unavailable">
        Store filters unavailable until verified Shop IDs are supported
      </span>
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
    priceCurrency: product.priceCurrency,
    merchants: product.merchants,
    imageUrl: productImageUrl(product),
  }
}

function shelfThumbForProduct(product: Product): ShelfThumb {
  return {
    name: product.name,
    tone: product.tone,
    imageUrl: productImageUrl(product),
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
  storageScope,
  greeting,
  products,
  hiddenByShip,
  deliveryLocations,
  prompts,
  preferences,
  savedProducts,
  cart,
  cartProducts,
  orders,
  shelf,
  shelfFlashMessageId,
  productDetailChatRequest,
  discoverFindRequest,
  homeRequestId,
  newsletter,
  savedSet,
  savePendingSet,
  onSubmit,
  onOpen,
  onToggleSave,
  onAddProductToCart,
  onAddSelectedOfferToCart,
  onFallbackAddToCart,
  onCompareProducts,
  onCartQty,
  onCartRemove,
  onCheckout,
  activeCheckout,
  checkoutBusy,
  checkoutError,
  onCheckoutAssistant,
  onRefreshCheckout,
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
  storageScope: string
  greeting: string
  products: readonly Product[]
  hiddenByShip: number
  deliveryLocations: readonly UserLocation[]
  prompts: readonly string[]
  preferences: readonly Preference[]
  savedProducts: readonly Product[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  orders: readonly Order[]
  shelf: readonly ShelfItem[]
  shelfFlashMessageId: string | null
  productDetailChatRequest: ProductDetailChatRequest | null
  discoverFindRequest: DiscoverFindRequest | null
  homeRequestId: number
  newsletter: boolean
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  onSubmit: (input: DiscoverProductSearchTurnInput) => Promise<DiscoverProductSearchTurnResult>
  onOpen: (product: Product, products?: readonly Product[], researchQuery?: string | null) => void
  onToggleSave: (product: Product) => void
  onAddProductToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  onAddSelectedOfferToCart: (product: Product, offerKey: string) => Promise<boolean>
  onFallbackAddToCart: (product: Product, offer: Offer) => void
  onCompareProducts: (products: readonly Product[]) => void
  onCartQty: (id: ProductId, merchant: string, qty: number, identity?: string) => void
  onCartRemove: (id: ProductId, merchant: string, identity?: string) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  activeCheckout: ActiveCheckoutSession | null
  checkoutBusy: boolean
  checkoutError: string | null
  onCheckoutAssistant: CheckoutAssistantHandler
  onRefreshCheckout: () => Promise<void> | void
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
  const searchContext = discoverThreadSearchContext(activeThread)
  const query = searchContext.query
  const [searchTargetsByThread, setSearchTargetsByThread] = useState<Record<string, string>>({})
  const [searchActivitiesByThread, setSearchActivitiesByThread] = useState<
    Record<string, readonly AgentActivity[]>
  >({})
  const loading = Boolean(searchTargetsByThread[activeThreadIdSafe])
  const agentActivities = searchActivitiesByThread[activeThreadIdSafe] ?? []
  const [conversationDeleteError, setConversationDeleteError] = useState<string | null>(null)
  const [deletingThreadIds, setDeletingThreadIds] = useState<ReadonlySet<string>>(() => new Set())
  const [arrivalMessageId, setArrivalMessageId] = useState<string | null>(null)
  const [shareOpen, setShareOpen] = useState(false)
  const [composerReply, setComposerReply] = useState<AskReplyDraft | null>(null)
  const [pinnedIds, setPinnedIds] = useSessionStoredState<ProductId[]>(
    accountSessionStorageKey('meant.chatPinned', storageScope),
    [],
  )
  const [trayClearing, setTrayClearing] = useState(false)
  const [newsletterPending, setNewsletterPending] = useState(false)
  const chatBottomRef = useRef<HTMLDivElement | null>(null)
  const previousMessageCountRef = useRef(messages.length)
  const previousActiveThreadIdRef = useRef(activeThreadIdSafe)
  const activeThreadIdRef = useRef(activeThreadIdSafe)
  activeThreadIdRef.current = activeThreadIdSafe
  const didInitialScrollRef = useRef(false)
  const handledDiscoverFindRequestRef = useRef<string | null>(null)
  const scheduledChatTimersRef = useRef<number[]>([])
  const conversationPersistenceRef = useRef(createConversationPersistenceCoordinator())
  const persistenceScopeRef = useRef(storageScope)
  persistenceScopeRef.current = storageScope
  const conversationRevisionsRef = useRef<Record<string, number>>({})
  const lastSavedTimestampsRef = useRef<Record<string, number>>({})
  const conflictedThreadIdsRef = useRef(new Set<string>())
  const conversationStateRef = useRef({ threads, archivedThreads })
  conversationStateRef.current = { threads, archivedThreads }
  const handledHomeRequestRef = useRef(0)
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

  useEffect(() => {
    if (handledHomeRequestRef.current === homeRequestId) {
      return
    }
    handledHomeRequestRef.current = homeRequestId
    const homeThread = createDiscoverChatThread()
    setThreads((current) => [homeThread, ...current.filter(hasDiscoverThreadHistory)])
    setActiveThreadId(homeThread.id)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [homeRequestId])

  const displayProducts = useMemo(() => {
    if (query) {
      return searchContext.products
    }
    if (products.length > 0) {
      return products
    }
    if (savedProducts.length > 0) {
      return savedProducts
    }
    return PRODUCTS.slice(0, 4)
  }, [products, query, savedProducts, searchContext.products])
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

  const recordPersistedRevision = useCallback((threadId: string, revision: number) => {
    conversationRevisionsRef.current[threadId] = revision
    const attachRevision = (current: DiscoverChatThread[]) =>
      current.map((thread) =>
        thread.id === threadId ? { ...thread, persistedRevision: revision } : thread,
      )
    setThreads(attachRevision)
    setArchivedThreads(attachRevision)
  }, [])

  const restoreRemoteConversation = useCallback(
    async (
      threadId: string,
      persistenceScope: string | undefined,
      expectedLocalUpdatedAt: number | undefined,
    ) => {
      const profile = await getDiscoverConversation(threadId, {
        expectedUserId: persistenceScope,
      })
      if (persistenceScopeRef.current !== persistenceScope) return false
      const remoteThread = discoverThreadFromProfile(profile)
      if (!remoteThread) return false

      const state = conversationStateRef.current
      const currentThread = [...state.threads, ...state.archivedThreads].find(
        (thread) => thread.id === threadId,
      )
      if (!canRestoreDiscoverThreadAfterConflict(currentThread, expectedLocalUpdatedAt)) {
        conflictedThreadIdsRef.current.add(threadId)
        return false
      }

      conversationRevisionsRef.current[threadId] = profile.revision
      lastSavedTimestampsRef.current[threadId] = remoteThread.updatedAt ?? Date.now()
      conflictedThreadIdsRef.current.delete(threadId)
      if (remoteThread.archived) {
        const nextThreads = state.threads.filter((thread) => thread.id !== threadId)
        const nextArchivedThreads = [
          remoteThread,
          ...state.archivedThreads.filter((thread) => thread.id !== threadId),
        ]
        conversationStateRef.current = {
          threads: nextThreads,
          archivedThreads: nextArchivedThreads,
        }
        setThreads(nextThreads)
        setArchivedThreads(nextArchivedThreads)
      } else {
        const nextArchivedThreads = state.archivedThreads.filter((thread) => thread.id !== threadId)
        const nextThreads = [
          remoteThread,
          ...state.threads.filter((thread) => thread.id !== threadId),
        ]
        conversationStateRef.current = {
          threads: nextThreads,
          archivedThreads: nextArchivedThreads,
        }
        setArchivedThreads(nextArchivedThreads)
        setThreads(nextThreads)
      }
      return true
    },
    [],
  )

  useEffect(() => {
    const controller = new AbortController()
    getDiscoverConversations({ expectedUserId: storageScope, signal: controller.signal })
      .then((profiles) => {
        if (controller.signal.aborted) {
          return
        }
        const remoteThreads = profiles
          .map(discoverThreadFromProfile)
          .filter((thread): thread is DiscoverChatThread => Boolean(thread))
        conversationRevisionsRef.current = Object.fromEntries(
          profiles.map((profile) => [profile.conversationId, profile.revision]),
        )
        conflictedThreadIdsRef.current.clear()
        lastSavedTimestampsRef.current = {}
        remoteThreads.forEach((thread) => {
          // The backend returns an allowlisted snapshot, so its revision/timestamp is the durable
          // baseline. A local snapshot is saved only when mergeDiscoverThreadSources proves it was
          // based on this same revision and has a newer client timestamp.
          lastSavedTimestampsRef.current[thread.id] = thread.updatedAt ?? Date.now()
        })
        const restoredThreads = mergeDiscoverThreadSources(
          remoteThreads,
          initialDiscoverChatThreads(storageScope),
        )
        const homeThread = createDiscoverChatThread()
        if (restoredThreads.length === 0) {
          setThreads([homeThread])
          setArchivedThreads([])
          setActiveThreadId(homeThread.id)
          setDiscoverHistoryLoaded(true)
          return
        }

        const { threads: nextThreads, archivedThreads: nextArchivedThreads } =
          activeAndArchivedDiscoverThreads(restoredThreads)
        setThreads([homeThread, ...nextThreads])
        setArchivedThreads(nextArchivedThreads)
        setActiveThreadId(homeThread.id)
        setDiscoverHistoryLoaded(true)
      })
      .catch(() => {
        if (controller.signal.aborted) {
          return
        }
        lastSavedTimestampsRef.current = {}
        const localThreads = initialDiscoverChatThreads(storageScope)
        const restoredThreads = localThreads.filter(hasDiscoverThreadHistory)
        const { threads: nextThreads, archivedThreads: nextArchivedThreads } =
          activeAndArchivedDiscoverThreads(restoredThreads)
        const homeThread = createDiscoverChatThread()
        setThreads([homeThread, ...nextThreads])
        setArchivedThreads(nextArchivedThreads)
        setActiveThreadId(homeThread.id)
        setDiscoverHistoryLoaded(true)
      })
    return () => controller.abort()
  }, [storageScope])

  useEffect(() => {
    if (!discoverHistoryLoaded) {
      return
    }
    saveStoredDiscoverChatThreads([...threads, ...archivedThreads], storageScope)
  }, [archivedThreads, discoverHistoryLoaded, storageScope, threads])

  useEffect(() => {
    if (!discoverHistoryLoaded) {
      return undefined
    }
    const snapshots = [
      ...threads.map((thread) => ({ thread, archived: false })),
      ...archivedThreads.map((thread) => ({ thread, archived: true })),
    ].filter(({ thread }) => {
      if (
        !hasDiscoverThreadHistory(thread) ||
        deletingThreadIds.has(thread.id) ||
        conflictedThreadIdsRef.current.has(thread.id)
      ) {
        return false
      }
      const lastSaved = lastSavedTimestampsRef.current[thread.id] ?? 0
      return !lastSaved || (thread.updatedAt ?? 0) > lastSaved
    })
    if (snapshots.length === 0) {
      return undefined
    }
    const persistenceScope = storageScope
    const timer = window.setTimeout(() => {
      snapshots.forEach(({ thread, archived }) => {
        const save = conversationPersistenceRef.current.enqueue(thread.id, async () => {
          if (persistenceScopeRef.current !== persistenceScope) {
            throw new Error('The signed-in account changed before this chat could be saved.')
          }
          const saved = await saveDiscoverConversation({
            conversationId: thread.id,
            title: discoverThreadTitle(thread),
            threadJson: discoverThreadJson(thread, archived),
            expectedRevision: conversationRevisionsRef.current[thread.id],
            expectedUserId: persistenceScope,
          })
          conversationRevisionsRef.current[thread.id] = saved.revision
          return saved
        })
        if (!save) return
        void save
          .then((saved) => {
            if (persistenceScopeRef.current !== persistenceScope) return
            lastSavedTimestampsRef.current[thread.id] = thread.updatedAt ?? Date.now()
            recordPersistedRevision(thread.id, saved.revision)
          })
          .catch((error: unknown) => {
            if (
              persistenceScopeRef.current === persistenceScope &&
              error instanceof ApiError &&
              error.status === 409
            ) {
              void restoreRemoteConversation(thread.id, persistenceScope, thread.updatedAt)
                .then((restored) => {
                  if (!restored) conflictedThreadIdsRef.current.add(thread.id)
                })
                .catch(() => conflictedThreadIdsRef.current.add(thread.id))
            }
          })
      })
    }, DISCOVER_HISTORY_SYNC_DELAY)

    return () => {
      window.clearTimeout(timer)
    }
  }, [
    archivedThreads,
    deletingThreadIds,
    discoverHistoryLoaded,
    recordPersistedRevision,
    restoreRemoteConversation,
    storageScope,
    threads,
  ])

  const pinnedProducts = pinnedIds
    .map((id) => displayProducts.find((product) => product.id === id))
    .filter((product): product is Product => Boolean(product))
  const currentCartLines = cartLines(cart, cartProducts)

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
            messages: [
              ...thread.messages,
              ...nextMessages.map((message) =>
                options.focusProductId && message.role === 'ai'
                  ? { ...message, sessionOnly: true }
                  : message,
              ),
            ],
            updatedAt: now,
          }
        }),
      )
    },
    [activeThreadIdSafe, setThreads],
  )

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
      if (preferred && (offerCartable(preferred) || canResolveCartOffer(product, preferred))) {
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

      const resolved = await resolveCartableOffer({
        product,
        offer,
        location,
        expectedUserId: storageScope === 'anonymous' ? undefined : storageScope,
      })
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
    [discountOfferForProduct, storageScope],
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
          expectedUserId: storageScope === 'anonymous' ? undefined : storageScope,
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
      storageScope,
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
    if (searchTargetsByThread[activeThreadIdSafe] === messageId) {
      setSearchTargetsByThread((current) => {
        const next = { ...current }
        delete next[activeThreadIdSafe]
        return next
      })
      setSearchActivitiesByThread((current) => {
        const next = { ...current }
        delete next[activeThreadIdSafe]
        return next
      })
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
    identity?: string,
  ) => {
    onCartQty(id, merchant, qty, identity)
    updateChatCartBlock(messageId, blockIndex, nextCart)
  }

  const removeChatCartLine = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
    identity?: string,
  ) => {
    onCartRemove(id, merchant, identity)
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
    const threadId = activeThreadIdSafe
    if (conflictedThreadIdsRef.current.has(threadId)) {
      updateThreadMessages(threadId, (current) => [
        ...current,
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [
            {
              type: 'system',
              text: 'This chat changed in another tab. Reload it before sending another request.',
            },
          ],
        },
      ])
      return
    }
    if (conversationPersistenceRef.current.isBlocked(threadId)) {
      return
    }
    const persistenceScope = storageScope
    const qualificationId = activeThread.qualificationId
    const aiId = nextDiscoverChatMessageId()
    const userMessage: DiscoverChatMessage = {
      id: nextDiscoverChatMessageId(),
      role: 'you',
      text,
    }
    const assistantMessage: DiscoverChatMessage = {
      id: aiId,
      role: 'ai',
      query: text,
      pending: true,
      pendingText: 'Meant is understanding which details matter for this search.',
      blocks: [],
    }
    const now = Date.now()
    const submittedThread: DiscoverChatThread = {
      ...activeThread,
      title:
        activeThread.messages.length === 0 && !activeThread.named
          ? deriveDiscoverChatTitle(text)
          : activeThread.title,
      messages: [...activeThread.messages, userMessage, assistantMessage],
      updatedAt: now,
    }
    setThreads((current) =>
      current.map((thread) => (thread.id === threadId ? submittedThread : thread)),
    )
    setSearchTargetsByThread((current) => ({ ...current, [threadId]: aiId }))
    const conversationSave = conversationPersistenceRef.current.enqueue(threadId, async () => {
      if (persistenceScopeRef.current !== persistenceScope) {
        throw new Error('The signed-in account changed before this chat could be saved.')
      }
      const saved = await saveDiscoverConversation({
        conversationId: threadId,
        title: discoverThreadTitle(submittedThread),
        threadJson: discoverThreadJson(submittedThread, false),
        expectedRevision: conversationRevisionsRef.current[threadId],
        expectedUserId: persistenceScope,
      })
      conversationRevisionsRef.current[threadId] = saved.revision
      return saved
    })
    if (!conversationSave) {
      return
    }
    void conversationSave
      .then((saved) => {
        if (persistenceScopeRef.current !== persistenceScope) {
          throw new Error('The signed-in account changed before this search could start.')
        }
        if (conversationPersistenceRef.current.isBlocked(threadId)) {
          throw new Error('This chat is being deleted.')
        }
        lastSavedTimestampsRef.current[threadId] = now
        recordPersistedRevision(threadId, saved.revision)
        return onSubmit({
          conversationId: threadId,
          qualificationId,
          message: text,
          onActivities: (activities) =>
            setSearchActivitiesByThread((current) => ({ ...current, [threadId]: activities })),
        })
      })
      .then((result) => {
        if (persistenceScopeRef.current !== persistenceScope) return
        const blocks: DiscoverChatBlock[] = [
          {
            type: 'text',
            text:
              result.assistantMessage.trim() ||
              (result.status === 'NEEDS_INPUT'
                ? 'What else should I know before I search?'
                : `I found ${result.products.length} match${result.products.length === 1 ? '' : 'es'}.`),
          },
        ]
        if (result.status === 'READY') {
          blocks.push({
            type: 'products',
            products: result.products,
            query: result.effectiveQuery,
          })
        }
        const now = Date.now()
        setThreads((current) =>
          current.map((thread) =>
            thread.id === threadId
              ? {
                  ...thread,
                  qualificationId:
                    result.status === 'NEEDS_INPUT' ? result.qualificationId : undefined,
                  messages: thread.messages.map((message) =>
                    message.id === aiId
                      ? {
                          ...message,
                          pending: false,
                          pendingText: undefined,
                          query: result.effectiveQuery,
                          suggestedReplies:
                            result.status === 'NEEDS_INPUT' ? result.suggestedReplies : undefined,
                          blocks,
                        }
                      : message,
                  ),
                  updatedAt: now,
                }
              : thread,
          ),
        )
        setSearchTargetsByThread((current) => {
          if (current[threadId] !== aiId) {
            return current
          }
          const next = { ...current }
          delete next[threadId]
          return next
        })
        setSearchActivitiesByThread((current) => {
          const next = { ...current }
          delete next[threadId]
          return next
        })
        if (activeThreadIdRef.current === threadId) {
          scrollChatToBottom()
        }
      })
      .catch(async (error: unknown) => {
        if (persistenceScopeRef.current !== persistenceScope) return
        const conflict = error instanceof ApiError && error.status === 409
        let conflictRestored = false
        if (conflict) {
          conflictRestored = await restoreRemoteConversation(threadId, persistenceScope, now).catch(
            () => false,
          )
          if (!conflictRestored) {
            conflictedThreadIdsRef.current.add(threadId)
          }
        }
        const errorMessage = conflict
          ? conflictRestored
            ? 'This chat changed in another tab. I restored the latest version; send your request again.'
            : 'This chat changed in another tab while you were editing it. Reload before sending again.'
          : error instanceof Error && error.message.trim()
            ? error.message
            : 'Product search failed. Please try again.'
        updateThreadMessages(threadId, (current) =>
          conflict
            ? [
                ...current,
                {
                  id: nextDiscoverChatMessageId(),
                  role: 'ai',
                  blocks: [{ type: 'system', text: errorMessage }],
                },
              ]
            : current.map((message) =>
                message.id === aiId
                  ? {
                      ...message,
                      pending: false,
                      pendingText: undefined,
                      suggestedReplies: undefined,
                      blocks: [{ type: 'text', text: errorMessage }],
                    }
                  : message,
              ),
        )
        setSearchTargetsByThread((current) => {
          if (current[threadId] !== aiId) {
            return current
          }
          const next = { ...current }
          delete next[threadId]
          return next
        })
        setSearchActivitiesByThread((current) => {
          const next = { ...current }
          delete next[threadId]
          return next
        })
        if (activeThreadIdRef.current === threadId) {
          scrollChatToBottom()
        }
      })
  }

  const submit = (text: string) => {
    const normalized = text.trim()
    if (!normalized) {
      return
    }
    if (activeThread.qualificationId) {
      runSearchInChat(normalized)
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
    if (product.canonicalProduct) {
      const recommendedOfferKey = product.canonicalProduct.recommendedOfferKey
      const canonicalOfferIndex = product.canonicalProduct.offers.findIndex(
        (offer) => offer.key === recommendedOfferKey,
      )
      const appendCartFailure = () =>
        appendMessagesToActiveThread(
          [
            {
              id: nextDiscoverChatMessageId(),
              role: 'ai',
              blocks: [
                {
                  type: 'text',
                  text: `I couldn't add ${product.name} to the merchant cart. Please try again or choose another merchant in the product details.`,
                },
              ],
            },
          ],
          { focusProductId: product.id },
        )
      const offer = product.offers[canonicalOfferIndex] ?? product.offers[0]
      if (!offer) {
        appendCartFailure()
        return
      }
      try {
        const added = await onAddSelectedOfferToCart(product, recommendedOfferKey)
        if (!added) {
          appendCartFailure()
          return
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
                  synced: true,
                  offerKey: recommendedOfferKey,
                  price: offer.price,
                  count: cart.reduce((sum, item) => sum + item.qty, 0) + 1,
                },
              ],
            },
          ],
          { focusProductId: product.id },
        )
      } catch {
        appendCartFailure()
      }
      return
    }
    const offer = bestOffer(product, deliveryLocations)
    if (!offer) {
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: [
              {
                type: 'text',
                text: `I couldn't load a current merchant offer for ${product.name}. Open the product again to retry.`,
              },
            ],
          },
        ],
        { focusProductId: product.id },
      )
      return
    }
    const exactOfferKey = offer.offerKey?.trim()
    const canSync =
      Boolean(exactOfferKey) || offerCartable(offer) || canResolveCartOffer(product, offer)
    let synced = false
    if (canSync) {
      try {
        synced = await onAddProductToCart(product, offer)
      } catch {
        synced = false
      }
    }
    if (!synced && exactOfferKey) {
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: [
              {
                type: 'text',
                text: `I couldn't add the current merchant offer for ${product.name}. Open the product and try again.`,
              },
            ],
          },
        ],
        { focusProductId: product.id },
      )
      return
    }
    if (!synced) {
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
              offerKey: exactOfferKey || undefined,
              price: offer.price,
              count: cart.reduce((sum, item) => sum + item.qty, 0) + 1,
            },
          ],
        },
      ],
      { focusProductId: product.id },
    )
  }

  const restoreCartLineFromReview = async (
    product: Product,
    merchant: string,
    price?: number,
    offerKey?: string,
  ) => {
    const exactOfferKey = offerKey?.trim()
    if (exactOfferKey) {
      if (cart.some((item) => item.id === product.id && item.offerKey?.trim() === exactOfferKey)) {
        return
      }
      try {
        if (await onAddSelectedOfferToCart(product, exactOfferKey)) {
          return
        }
      } catch {
        // Keep exact identity: opening the product is safer than restoring a sibling variant.
      }
      onOpen(product)
      return
    }
    if (product.canonicalProduct) {
      onOpen(product)
      return
    }
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
    const fallbackPrice = price ?? productPriceFrom(product, deliveryLocations)
    if (fallbackPrice == null) {
      return
    }
    onFallbackAddToCart(
      product,
      merchantOffer ?? {
        merchant,
        price: fallbackPrice,
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
    window.scrollTo({ top: 0, behavior: 'smooth' })
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
  }

  const deleteHistoryThread = (threadId: string) => {
    const persistenceScope = storageScope
    const deletion = conversationPersistenceRef.current.delete(threadId, async () => {
      if (persistenceScopeRef.current !== persistenceScope) {
        throw new Error('The signed-in account changed before this chat could be deleted.')
      }
      return deleteDiscoverConversation(threadId, { expectedUserId: persistenceScope })
    })
    if (!deletion) {
      return
    }
    setDeletingThreadIds((current) => new Set(current).add(threadId))
    setConversationDeleteError(null)

    void deletion
      .then(() => {
        if (persistenceScopeRef.current !== persistenceScope) return
        delete lastSavedTimestampsRef.current[threadId]
        delete conversationRevisionsRef.current[threadId]
        conflictedThreadIdsRef.current.delete(threadId)
        deleteStoredDiscoverChatThread(threadId, storageScope)
        setArchivedThreads((current) => current.filter((thread) => thread.id !== threadId))
        setThreads((current) => {
          const next = current.filter((thread) => thread.id !== threadId)
          return next.length > 0 ? next : [createDiscoverChatThread()]
        })
        setActiveThreadId((current) => (current === threadId ? null : current))
        setSearchTargetsByThread((current) => {
          const next = { ...current }
          delete next[threadId]
          return next
        })
        setSearchActivitiesByThread((current) => {
          const next = { ...current }
          delete next[threadId]
          return next
        })
      })
      .catch(() => {
        if (persistenceScopeRef.current !== persistenceScope) return
        setConversationDeleteError(
          "Couldn't delete that chat. It is still available; please try again.",
        )
      })
      .finally(() => {
        setDeletingThreadIds((current) => {
          const next = new Set(current)
          next.delete(threadId)
          return next
        })
      })
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

  const openWorkbenchAgentReport = useCallback(
    (task: string, result: string) => {
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: [
              { type: 'system', text: `Mock research agent · ${task}` },
              { type: 'text', text: result },
            ],
          },
        ],
        { titleSeed: task },
      )
      scrollChatToBottom()
    },
    [appendMessagesToActiveThread, scrollChatToBottom],
  )

  const visibleActiveCheckout =
    activeCheckout && (!activeCheckout.threadId || activeCheckout.threadId === activeThreadIdSafe)
      ? activeCheckout
      : null

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
    onRestoreCartLine: (product: Product, merchant: string, price?: number, offerKey?: string) =>
      void restoreCartLineFromReview(product, merchant, price, offerKey),
    onCartQty: updateChatCartQty,
    onCartRemove: removeChatCartLine,
    onCheckout,
    activeCheckout: visibleActiveCheckout,
    checkoutBusy,
    checkoutError,
    onCheckoutAssistant,
    onRefreshCheckout,
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

  const empty = messages.length === 0
  const suggestedReplies = discoverSuggestedReplies(messages)
  const threadHasCheckoutBlock = messages.some((message) =>
    message.blocks?.some((block) => block.type === 'checkout'),
  )
  const checkoutHostMessageId =
    [...messages]
      .reverse()
      .find((message) => message.blocks?.some((block) => block.type === 'checkout'))?.id ?? null
  const workbenchProduct = discoverThreadFocusProduct(activeThread, knownProductsById)
  if (empty && !visibleActiveCheckout) {
    return (
      <>
        <Workbench
          storageScope={storageScope}
          product={workbenchProduct}
          query={query}
          preferences={preferences}
          onReply={setComposerReply}
          onAgentReport={openWorkbenchAgentReport}
        />
        <main className="mt-feed mt-ct-feed mt-ct-feed-hero">
          <ChatHero
            profile={profile}
            greeting={greeting}
            prompts={prompts}
            onSubmit={submit}
            loading={loading || !discoverHistoryLoaded}
            historyThreads={historyThreads}
            activeThreadId={activeThreadIdSafe}
            onHistorySelect={selectHistoryThread}
            onHistoryDelete={deleteHistoryThread}
            replyDraft={composerReply}
            onClearReply={() => setComposerReply(null)}
          />
          {conversationDeleteError ? (
            <div className="mt-ct-history-error" role="alert">
              <span>{conversationDeleteError}</span>
              <button type="button" onClick={() => setConversationDeleteError(null)}>
                Dismiss
              </button>
            </div>
          ) : null}
        </main>
      </>
    )
  }

  return (
    <>
      <Workbench
        storageScope={storageScope}
        product={workbenchProduct}
        query={query}
        preferences={preferences}
        onReply={setComposerReply}
        onAgentReport={openWorkbenchAgentReport}
      />
      <main className="mt-feed mt-ct-feed">
        <DiscoverThreadTabs
          threads={threads}
          activeId={activeThreadIdSafe}
          onSelect={selectHistoryThread}
          onDelete={deleteHistoryThread}
          onNew={newThread}
          onRename={renameThread}
          onShare={() => setShareOpen(true)}
          onReorder={reorderThreads}
          onDeleteHistory={deleteHistoryThread}
          historyThreads={historyThreads}
        />
        {conversationDeleteError ? (
          <div className="mt-ct-history-error" role="alert">
            <span>{conversationDeleteError}</span>
            <button type="button" onClick={() => setConversationDeleteError(null)}>
              Dismiss
            </button>
          </div>
        ) : null}
        <div className="mt-ct-thread">
          <div className="mt-ct-msg mt-ct-meant mt-ct-greeting">
            <span className="mt-ct-av">
              <SparkMark size={13} />
            </span>
            <div className="mt-ct-meant-body">
              <p className="mt-ct-intro">
                I only surface products that fit your profile. I can also open your live cart,
                orders, saved items, and preferences right here.
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
              threadId={activeThreadIdSafe}
              message={message}
              flash={shelfFlashMessageId === message.id}
              celebrateArrival={arrivalMessageId === message.id}
              {...messageBlockProps}
              activeCheckout={message.id === checkoutHostMessageId ? visibleActiveCheckout : null}
            />
          ))}
          {visibleActiveCheckout && !threadHasCheckoutBlock ? (
            <div className="mt-ct-msg mt-ct-meant">
              <span className="mt-ct-av">
                <SparkMark size={13} />
              </span>
              <div className="mt-ct-meant-body">
                <InlineCheckoutBlock
                  threadId={activeThreadIdSafe}
                  cart={cart}
                  products={cartProducts}
                  onCheckout={onCheckout}
                  activeCheckout={visibleActiveCheckout}
                  checkoutBusy={checkoutBusy}
                  checkoutError={checkoutError}
                  onCheckoutAssistant={onCheckoutAssistant}
                  onRefreshCheckout={onRefreshCheckout}
                  onOpenCart={onOpenCart}
                  onOpenOrders={onOpenOrders}
                />
              </div>
            </div>
          ) : null}
          {agentActivities.length > 0 && loading ? (
            <AgentActivityPanel activities={agentActivities} />
          ) : null}
          {!query && deliveryLocations.length > 0 && hiddenByShip > 0 ? (
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
              suggestions={suggestedReplies}
              showChips={suggestedReplies.length > 0}
              onAsk={submit}
              disabled={loading || !discoverHistoryLoaded}
              replyDraft={composerReply}
              onClearReply={() => setComposerReply(null)}
            />
          </div>
        </div>
      </main>
    </>
  )
}
