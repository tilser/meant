import {
  type DragEvent as ReactDragEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import {
  cancelAgentRun,
  createAgentConversation,
  deleteAgentConversation,
  getCompleteAgentConversation,
  getAgentConversations,
  getAgentRun,
  recordAgentDirectAction,
  submitAgentTurn,
  updateAgentConversation,
  type AgentConversationDetailProfile,
  type AgentConversationSummaryProfile,
  type AgentDirectActionProfile,
  type AgentRunSnapshotProfile,
  type MerchantProfile,
} from '../../../lib/apiClient'
import { ApiError } from '../../../lib/apiError'
import { AskComposer } from '../ask/AskComposer'
import type {
  ActiveCheckoutSession,
  CheckoutAssistantHandler,
  CheckoutReleaseHandler,
} from '../cart/checkoutTypes'
import { AgentActivityPanel } from '../chat/AgentActivityPanel'
import { DiscoverHomeHero } from '../chat/DiscoverHomeHero'
import { comingSoonMessage } from '../chat/comingSoon'
import { DiscoverChatMessageRow } from '../chat/DiscoverChatMessageRow'
import { DiscoverShareSheet } from '../chat/DiscoverShareSheet'
import { DiscoverThreadTabs } from '../chat/DiscoverThreadTabs'
import { latestCartBlockMessageId } from '../chat/utils'
import type {
  AgentActivity,
  DiscoverChatMessage,
  DiscoverFindRequest,
  ProductDetailChatRequest,
  VisibleProductContext,
  VisibleProductContextChange,
} from '../chat/types'
import { PROFILE } from '../data'
import { productImageUrl } from '../product/productSnapshots'
import type {
  ShelfDragPayload,
  ShelfItem,
  ShelfMessageSnapshot,
  ShelfProductSnapshot,
  ShelfThumb,
} from '../shelf/types'
import { SHELF_DRAG_MIME } from '../shelf/types'
import { agentShelfContext } from '../shelf/agentShelfContext'
import { MeantHeartMark, ProductArtwork } from '../shared/ui'
import { accountStorageKey } from '../shared/accountStorage'
import { useStoredState } from '../shared/storage'
import { Workbench } from '../chat/workbench/Workbench'
import {
  updateVisibleProductContextRegistry,
  type VisibleProductContextRegistry,
} from '../chat/visibleProductContext'
import type {
  CartItem,
  CheckoutPayload,
  Preference,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import {
  cartStateReplacementsFromAgentArtifacts,
  currentAgentProductSnapshots,
  discoverMessagesFromAgentConversation,
  productInteractionState,
  productsFromAgentArtifacts,
  type AgentProductSnapshot,
} from './artifactMapping'
import type { MerchantCartStateReplacement } from '../cart/types'
import {
  isTerminalAgentRunStatus,
  optimisticAgentCartQuantityChange,
  type AgentCartPartitionFingerprints,
} from './cartSync'
import {
  createAgentEventReducerState,
  reduceAgentEvent,
  restoreAgentRunSnapshot,
  snapshotRecoveryForAgentRun,
  type AgentEventReducerState,
} from './eventReducer'
import { isExpiredAgentEventCursor, streamAgentRunEvents } from './eventStream'
import { AgentActionRequestIdentityStore } from './requestIdentity'
import {
  agentActionQueueFor,
  agentTurnSubmissionQueueFor,
  commerceActionQueueFor,
} from './actionQueue'
import { PRODUCT_PIN_NOTICE_LIFETIME_MS, isProductPinNotice } from './autoDismissNotices'
import { checkoutInChatMessage } from './checkoutPreparation'
import { withProjectedAgentMessages } from './messageProjection'
import { mergeAnchoredLocalMessages, type AnchoredLocalMessage } from './localMessageOrdering'
import { AgentWorkingIndicator } from './AgentWorkingIndicator'
import { agentWorkingStage } from './agentWorkingState'
import { threadFromAgentConversationSummary } from './conversationHistory'
import { addChatProductToCart, cartInChatMessage, exactProductOfferKey } from './chatCartAddition'
import { agentRunCandidateIds, preferredAgentRunSnapshot } from './runSelection'
import { refreshAgentViewAfterSettlement } from './settlementRefresh'

const NEWSLETTER_SUBSCRIBED_MESSAGE =
  'You are subscribed to the newsletter. If you want to unsubscribe, you can do so in your account settings.'

function isTerminalRun(run: AgentRunSnapshotProfile | null | undefined): boolean {
  return Boolean(run && isTerminalAgentRunStatus(run.status))
}

function uniqueRequestId(prefix: string): string {
  const random = globalThis.crypto?.randomUUID?.()
  return random ? `${prefix}:${random}` : `${prefix}:${Date.now()}:${Math.random()}`
}

function derivedTitle(text: string): string {
  const normalized = text.trim().replace(/\s+/g, ' ')
  return normalized.length <= 48 ? normalized : `${normalized.slice(0, 45)}...`
}

function emptyConversationFromSummary(
  summary: AgentConversationSummaryProfile,
): AgentConversationDetailProfile {
  return {
    ...summary,
    rollingSummary: null,
    summaryVersion: 0,
    latestCursor: 0,
    messages: [],
    artifacts: [],
  }
}

function mergeAction(
  conversation: AgentConversationDetailProfile,
  result: AgentDirectActionProfile,
): AgentConversationDetailProfile {
  const messageExists = conversation.messages.some(
    (message) => message.messageId === result.message.messageId,
  )
  const artifactIds = new Set(conversation.artifacts.map((artifact) => artifact.artifactId))
  return {
    ...conversation,
    latestSequence: Math.max(conversation.latestSequence, result.message.sequenceNumber),
    updatedAt: result.message.createdAt,
    messages: messageExists
      ? conversation.messages
      : [...conversation.messages, result.message].sort(
          (left, right) => left.sequenceNumber - right.sequenceNumber,
        ),
    artifacts: [
      ...conversation.artifacts,
      ...result.artifacts.filter((artifact) => !artifactIds.has(artifact.artifactId)),
    ],
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

function shelfThumb(product: Product): ShelfThumb {
  return { name: product.name, tone: product.tone, imageUrl: productImageUrl(product) }
}

function productsInMessage(message: DiscoverChatMessage): Product[] {
  const products: Product[] = []
  for (const block of message.blocks ?? []) {
    if (block.type === 'products' || block.type === 'saved' || block.type === 'minicompare') {
      products.push(...block.products)
    } else if (block.type === 'similar') {
      if (block.product) products.push(block.product)
      products.push(...block.products)
    } else if (
      block.type === 'reviews' ||
      block.type === 'code' ||
      block.type === 'watch' ||
      block.type === 'friendvote' ||
      block.type === 'added'
    ) {
      products.push(block.product)
    } else if (block.type === 'decision') {
      products.push(block.product)
      if (block.runnerUp) products.push(block.runnerUp)
    } else if (block.type === 'cart' && block.products) {
      products.push(...block.products)
    }
  }
  return [...new Map(products.map((product) => [product.id, product])).values()]
}

function shelfMessageSnapshot(message: DiscoverChatMessage): ShelfMessageSnapshot {
  const products = productsInMessage(message)
  const text =
    message.text ??
    message.blocks?.find((block) => block.type === 'text' || block.type === 'system')?.text ??
    ''
  return {
    side: message.role === 'you' ? 'you' : 'meant',
    title: message.role === 'you' ? 'Your message' : products.length ? 'Meant picks' : 'Meant',
    text,
    thumbs: products.slice(0, 6).map(shelfThumb),
  }
}

export interface AgentDiscoverViewProps {
  expectedUserId: string
  profile: typeof PROFILE
  greeting: string
  prompts: readonly string[]
  merchants: readonly MerchantProfile[]
  merchantsLoading: boolean
  merchantsError: string | null
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  shelf: readonly ShelfItem[]
  shelfFlashMessageId: string | null
  productDetailChatRequest: ProductDetailChatRequest | null
  discoverFindRequest: DiscoverFindRequest | null
  homeRequestId: number
  agentRunSettlementRevision: number
  newsletter: boolean
  onOpen: (product: Product, products?: readonly Product[], researchQuery?: string | null) => void
  onToggleSave: (product: Product) => void
  onAddSelectedOfferToCart: (product: Product, offerKey: string) => Promise<boolean>
  onCompareProducts: (products: readonly Product[]) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  activeCheckout: ActiveCheckoutSession | null
  checkoutBusy: boolean
  checkoutError: string | null
  onCheckoutAssistant: CheckoutAssistantHandler
  onRefreshCheckout: () => Promise<void> | void
  onReleaseCheckout: CheckoutReleaseHandler
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onOpenShelf: () => void
  onNewsletterChange: (newsletter: boolean) => Promise<void> | void
  onShelfAddMessage: (payload: Extract<ShelfDragPayload, { kind: 'message' }>) => void
  onShelfAddProduct: (snapshot: ShelfProductSnapshot) => void
  onAgentProductsSnapshot?: (snapshots: readonly AgentProductSnapshot[]) => void
  onReadAgentCart: () => readonly CartItem[]
  onCaptureAgentCartRevision: () => AgentCartPartitionFingerprints | undefined
  onAgentCartSnapshot: (
    replacements: readonly MerchantCartStateReplacement[],
    submittedCartRevision: AgentCartPartitionFingerprints | undefined,
  ) => readonly CartItem[]
  onUpdateCartQuantity: (
    sourceItem: CartItem,
    quantity: number,
    identity: string,
  ) => Promise<void> | void
  onAgentRunSubmitted?: (
    runId: string,
    conversationId: string,
    submittedCartRevision: AgentCartPartitionFingerprints | undefined,
  ) => void
  onProductDetailChatRequestHandled: (requestId: string) => void
  onFlashMessage: (messageId: string) => void
}

export function AgentDiscoverView({
  expectedUserId,
  profile,
  greeting,
  prompts,
  merchants,
  merchantsLoading,
  merchantsError,
  deliveryLocations,
  preferences,
  cart,
  cartProducts,
  savedSet,
  savePendingSet,
  shelf,
  shelfFlashMessageId,
  productDetailChatRequest,
  discoverFindRequest,
  homeRequestId,
  agentRunSettlementRevision,
  newsletter,
  onOpen,
  onToggleSave,
  onAddSelectedOfferToCart,
  onCompareProducts,
  onCheckout,
  activeCheckout,
  checkoutBusy,
  checkoutError,
  onCheckoutAssistant,
  onRefreshCheckout,
  onReleaseCheckout,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onOpenShelf,
  onNewsletterChange,
  onShelfAddMessage,
  onShelfAddProduct,
  onAgentProductsSnapshot,
  onReadAgentCart,
  onCaptureAgentCartRevision,
  onAgentCartSnapshot,
  onUpdateCartQuantity,
  onAgentRunSubmitted,
  onProductDetailChatRequestHandled,
  onFlashMessage,
}: Readonly<AgentDiscoverViewProps>) {
  const [conversations, setConversations] = useState<AgentConversationSummaryProfile[]>([])
  const [archivedConversations, setArchivedConversations] = useState<
    AgentConversationSummaryProfile[]
  >([])
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [draftMerchantId, setDraftMerchantId] = useState<string | null>(null)
  const [conversation, setConversation] = useState<AgentConversationDetailProfile | null>(null)
  const [activeRunId, setActiveRunId] = useState<string | null>(null)
  const [runSnapshot, setRunSnapshot] = useState<AgentRunSnapshotProfile | null>(null)
  const [eventState, setEventState] = useState<AgentEventReducerState>(() =>
    createAgentEventReducerState(),
  )
  const [loading, setLoading] = useState(true)
  const [pendingSubmissions, setPendingSubmissions] = useState(0)
  const [actionPending, setActionPending] = useState<ReadonlySet<string>>(new Set())
  const [optimisticCart, setOptimisticCart] = useState<readonly CartItem[] | null>(null)
  const [autoRemovingMessageKeys, setAutoRemovingMessageKeys] = useState<ReadonlySet<string>>(
    new Set(),
  )
  const [trayClearing, setTrayClearing] = useState(false)
  const [newsletterPending, setNewsletterPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [shareOpen, setShareOpen] = useState(false)
  const [localMessagesByConversationId, setLocalMessagesByConversationId] = useState<
    Record<string, AnchoredLocalMessage[]>
  >({})
  const [dismissedMessageIds, setDismissedMessageIds] = useStoredState<Record<string, string[]>>(
    accountStorageKey('meant.agentDismissedMessages', expectedUserId),
    {},
  )
  const streamAbortRef = useRef<AbortController | null>(null)
  const eventStateRef = useRef(eventState)
  const activeConversationIdRef = useRef(activeConversationId)
  const activeRunIdRef = useRef(activeRunId)
  const conversationRef = useRef<AgentConversationDetailProfile | null>(conversation)
  const conversationSnapshotRequestRef = useRef(new Map<string, number>())
  const committedConversationSnapshotRef = useRef(new Map<string, AgentConversationDetailProfile>())
  const recoveryInFlightRef = useRef(new Set<string>())
  const refreshListsRequestRef = useRef(0)
  const actionPendingRef = useRef(new Set<string>())
  const optimisticCartRef = useRef<readonly CartItem[] | null>(null)
  const cartMutationSequenceRef = useRef(0)
  const actionIdempotencyRef = useRef(new AgentActionRequestIdentityStore())
  const visibleProductContextRef = useRef<{
    conversationId: string
    context: VisibleProductContext
  } | null>(null)
  const visibleProductContextsRef = useRef<VisibleProductContextRegistry>(new Map())
  const visibleMessageIdsByConversationRef = useRef(new Map<string, readonly string[]>())
  const liveCartMessageIdRef = useRef<string | null>(null)
  const actionQueue = useMemo(() => agentActionQueueFor(expectedUserId), [expectedUserId])
  const turnSubmissionQueue = useMemo(
    () => agentTurnSubmissionQueueFor(expectedUserId, activeConversationId ?? 'draft'),
    [activeConversationId, expectedUserId],
  )
  const commerceActionQueue = useMemo(
    () => commerceActionQueueFor(expectedUserId),
    [expectedUserId],
  )
  const autoDismissTimeoutsRef = useRef(new Map<string, number>())
  const visibleCartRef = useRef(cart)
  const bottomRef = useRef<HTMLDivElement | null>(null)
  const handledHomeRequestRef = useRef(homeRequestId)
  const handledAgentRunSettlementRevisionRef = useRef(agentRunSettlementRevision)
  const handledFindRequestRef = useRef<string | null>(null)
  const handledProductRequestRef = useRef<string | null>(null)
  eventStateRef.current = eventState
  activeConversationIdRef.current = activeConversationId

  useEffect(() => {
    visibleCartRef.current = cart
  }, [cart])

  const updateConversationState = useCallback(
    (
      updater: (
        current: AgentConversationDetailProfile | null,
      ) => AgentConversationDetailProfile | null,
    ) => {
      const next = updater(conversationRef.current)
      conversationRef.current = next
      setConversation(next)
    },
    [],
  )

  const updateActiveConversationId = useCallback((next: string | null) => {
    if (activeConversationIdRef.current !== next) {
      visibleProductContextRef.current = null
      visibleProductContextsRef.current.clear()
    }
    activeConversationIdRef.current = next
    setActiveConversationId(next)
  }, [])

  const captureVisibleProductContext = useCallback<VisibleProductContextChange>(
    (sourceMessageId, context) => {
      const conversationId = activeConversationIdRef.current
      const normalizedSourceMessageId = sourceMessageId.trim()
      if (!conversationId || !normalizedSourceMessageId) return

      const latest = updateVisibleProductContextRegistry(
        visibleProductContextsRef.current,
        conversationId,
        normalizedSourceMessageId,
        context,
      )
      visibleProductContextRef.current = latest ? { conversationId, context: latest } : null
    },
    [],
  )

  const updateActiveRunId = useCallback(
    (nextValue: string | null | ((current: string | null) => string | null)) => {
      const next = typeof nextValue === 'function' ? nextValue(activeRunIdRef.current) : nextValue
      activeRunIdRef.current = next
      setActiveRunId(next)
    },
    [],
  )

  const invalidateConversationSnapshotRequests = useCallback((conversationId: string) => {
    const requestId = (conversationSnapshotRequestRef.current.get(conversationId) ?? 0) + 1
    conversationSnapshotRequestRef.current.set(conversationId, requestId)
    committedConversationSnapshotRef.current.delete(conversationId)
  }, [])

  const beginConversationSnapshotRequest = useCallback((conversationId: string) => {
    const requestId = (conversationSnapshotRequestRef.current.get(conversationId) ?? 0) + 1
    conversationSnapshotRequestRef.current.set(conversationId, requestId)
    return requestId
  }, [])

  const commitConversationSnapshot = useCallback(
    (
      conversationId: string,
      requestId: number,
      snapshot: AgentConversationDetailProfile,
    ): boolean => {
      if (
        activeConversationIdRef.current !== conversationId ||
        conversationSnapshotRequestRef.current.get(conversationId) !== requestId
      ) {
        return false
      }
      const current = conversationRef.current
      if (
        current?.conversationId === conversationId &&
        current.latestSequence > snapshot.latestSequence
      ) {
        return false
      }
      committedConversationSnapshotRef.current.set(conversationId, snapshot)
      updateConversationState(() => snapshot)
      return true
    },
    [updateConversationState],
  )

  const refreshLists = useCallback(async () => {
    const requestId = refreshListsRequestRef.current + 1
    refreshListsRequestRef.current = requestId
    const [active, archived] = await Promise.all([
      getAgentConversations({ expectedUserId, archived: false, limit: 50 }),
      getAgentConversations({ expectedUserId, archived: true, limit: 50 }),
    ])
    if (refreshListsRequestRef.current === requestId) {
      setConversations(active)
      setArchivedConversations(archived)
    }
    return active
  }, [expectedUserId])

  const refreshConversation = useCallback(
    async (conversationId: string): Promise<AgentConversationDetailProfile> => {
      const requestId = beginConversationSnapshotRequest(conversationId)
      const snapshot = await getCompleteAgentConversation(conversationId, {
        expectedUserId,
        pageSize: 200,
      })
      commitConversationSnapshot(conversationId, requestId, snapshot)
      return snapshot
    },
    [beginConversationSnapshotRequest, commitConversationSnapshot, expectedUserId],
  )

  const discoverLatestRun = useCallback(
    async (initialSnapshot: AgentConversationDetailProfile) => {
      if (
        committedConversationSnapshotRef.current.get(initialSnapshot.conversationId) !==
        initialSnapshot
      ) {
        return
      }
      const recoverRun = async (runId: string): Promise<AgentRunSnapshotProfile | null> => {
        try {
          return await getAgentRun(runId, { expectedUserId })
        } catch (caught) {
          if (!(caught instanceof ApiError) || caught.status !== 404) {
            throw caught
          }
          // An old/pruned run does not prevent the durable conversation from rendering.
          return null
        }
      }
      const showRun = (
        run: AgentRunSnapshotProfile | null,
        snapshot: AgentConversationDetailProfile,
      ) => {
        setRunSnapshot(run)
        if (!run) {
          updateActiveRunId(null)
          return
        }
        setEventState((current) => {
          const next = restoreAgentRunSnapshot(current, run)
          eventStateRef.current = next
          return next
        })
        if (isTerminalRun(run)) {
          updateActiveRunId(null)
          return
        }
        onAgentRunSubmitted?.(run.runId, snapshot.conversationId, undefined)
        updateActiveRunId(run.runId)
      }

      let snapshot = initialSnapshot
      const observedCurrentRunIds = new Set<string>()
      while (snapshot.currentRunId && !observedCurrentRunIds.has(snapshot.currentRunId)) {
        const advertisedRunId = snapshot.currentRunId
        observedCurrentRunIds.add(advertisedRunId)
        const currentRun = await recoverRun(advertisedRunId)
        if (
          activeConversationIdRef.current !== snapshot.conversationId ||
          committedConversationSnapshotRef.current.get(snapshot.conversationId) !== snapshot
        ) {
          return
        }
        if (currentRun && !isTerminalRun(currentRun)) {
          showRun(currentRun, snapshot)
          return
        }

        // The advertised FIFO head may finish between the conversation and run requests.
        // Refresh until the server either exposes the next head or confirms that the queue drained.
        const refreshed = await refreshConversation(snapshot.conversationId)
        if (
          activeConversationIdRef.current !== refreshed.conversationId ||
          committedConversationSnapshotRef.current.get(refreshed.conversationId) !== refreshed
        ) {
          return
        }
        snapshot = refreshed
      }

      const runIds = agentRunCandidateIds(
        snapshot.currentRunId,
        snapshot.messages
          .filter((message) => message.role === 'USER')
          .map((message) => message.runId),
      ).slice(0, 3)
      const runs = (await Promise.all(runIds.map(recoverRun))).filter(
        (run): run is AgentRunSnapshotProfile => run !== null,
      )
      if (
        activeConversationIdRef.current !== snapshot.conversationId ||
        committedConversationSnapshotRef.current.get(snapshot.conversationId) !== snapshot
      ) {
        return
      }
      const run = preferredAgentRunSnapshot(runs)
      showRun(run, snapshot)
    },
    [expectedUserId, onAgentRunSubmitted, refreshConversation, updateActiveRunId],
  )

  const refreshAfterSettlement = useCallback(() => {
    void refreshAgentViewAfterSettlement({
      activeConversationId: activeConversationIdRef.current,
      refreshLists,
      refreshConversation,
      discoverLatestRun,
    })
  }, [discoverLatestRun, refreshConversation, refreshLists])

  useEffect(() => {
    const unsubscribeAgent = actionQueue.subscribeToSettled(refreshAfterSettlement)
    const unsubscribeTurn = turnSubmissionQueue.subscribeToSettled(refreshAfterSettlement)
    const unsubscribeCommerce = commerceActionQueue.subscribeToSettled(refreshAfterSettlement)
    return () => {
      unsubscribeAgent()
      unsubscribeTurn()
      unsubscribeCommerce()
    }
  }, [actionQueue, commerceActionQueue, refreshAfterSettlement, turnSubmissionQueue])

  useEffect(() => {
    if (handledAgentRunSettlementRevisionRef.current === agentRunSettlementRevision) return
    handledAgentRunSettlementRevisionRef.current = agentRunSettlementRevision
    refreshAfterSettlement()
  }, [agentRunSettlementRevision, refreshAfterSettlement])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void (async () => {
      try {
        await refreshLists()
        if (controller.signal.aborted) return
      } catch (caught) {
        if (!controller.signal.aborted) {
          setError(caught instanceof Error ? caught.message : 'Could not load agent conversations.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    })()
    return () => controller.abort()
  }, [expectedUserId, refreshLists])

  useEffect(() => {
    if (!activeConversationId) return
    const controller = new AbortController()
    const requestId = beginConversationSnapshotRequest(activeConversationId)
    setLoading(true)
    setError(null)
    updateActiveRunId(null)
    updateConversationState(() => null)
    streamAbortRef.current?.abort()
    void getCompleteAgentConversation(activeConversationId, {
      expectedUserId,
      pageSize: 200,
      signal: controller.signal,
    })
      .then(async (snapshot) => {
        if (controller.signal.aborted) return
        if (!commitConversationSnapshot(activeConversationId, requestId, snapshot)) return
        await discoverLatestRun(snapshot)
      })
      .catch((caught) => {
        if (!controller.signal.aborted) {
          setError(caught instanceof Error ? caught.message : 'Could not load this conversation.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [
    activeConversationId,
    beginConversationSnapshotRequest,
    commitConversationSnapshot,
    discoverLatestRun,
    expectedUserId,
    updateActiveRunId,
    updateConversationState,
  ])

  const recoverSnapshots = useCallback(
    async (runId: string) => {
      if (!activeConversationId || recoveryInFlightRef.current.has(runId)) return
      const targetConversationId = activeConversationId
      const requestId = beginConversationSnapshotRequest(targetConversationId)
      recoveryInFlightRef.current.add(runId)
      try {
        const [run, nextConversation] = await Promise.all([
          getAgentRun(runId, { expectedUserId }),
          getCompleteAgentConversation(targetConversationId, { expectedUserId, pageSize: 200 }),
        ])
        if (
          activeRunIdRef.current !== runId ||
          activeConversationIdRef.current !== targetConversationId
        ) {
          return
        }
        if (!commitConversationSnapshot(targetConversationId, requestId, nextConversation)) return
        setRunSnapshot(run)
        setEventState((current) => {
          const next = restoreAgentRunSnapshot(current, run)
          eventStateRef.current = next
          return next
        })
        if (isTerminalRun(run)) {
          await discoverLatestRun(nextConversation)
        }
      } finally {
        recoveryInFlightRef.current.delete(runId)
      }
    },
    [
      activeConversationId,
      beginConversationSnapshotRequest,
      commitConversationSnapshot,
      discoverLatestRun,
      expectedUserId,
    ],
  )

  useEffect(() => {
    if (!activeRunId || !activeConversationId) return
    const controller = new AbortController()
    streamAbortRef.current?.abort()
    streamAbortRef.current = controller
    let reconnectDelay = 250
    void (async () => {
      while (!controller.signal.aborted) {
        const cursor = eventStateRef.current.runs[activeRunId]?.lastCursor ?? 0
        try {
          for await (const decoded of streamAgentRunEvents({
            runId: activeRunId,
            afterCursor: cursor,
            expectedUserId,
            signal: controller.signal,
          })) {
            if (controller.signal.aborted) return
            const next = reduceAgentEvent(eventStateRef.current, decoded)
            eventStateRef.current = next
            setEventState(next)
            const recovery = snapshotRecoveryForAgentRun(next, activeRunId)
            if (recovery) {
              await recoverSnapshots(activeRunId)
              break
            }
          }
          if (controller.signal.aborted) return
          const run = await getAgentRun(activeRunId, { expectedUserId, signal: controller.signal })
          if (
            controller.signal.aborted ||
            activeRunIdRef.current !== activeRunId ||
            activeConversationIdRef.current !== activeConversationId
          ) {
            return
          }
          setRunSnapshot(run)
          if (isTerminalRun(run)) {
            const snapshot = await refreshConversation(activeConversationId)
            if (
              controller.signal.aborted ||
              activeRunIdRef.current !== activeRunId ||
              activeConversationIdRef.current !== activeConversationId
            ) {
              return
            }
            await discoverLatestRun(snapshot)
            void refreshLists()
            return
          }
          reconnectDelay = 250
        } catch (caught) {
          if (controller.signal.aborted) return
          if (isExpiredAgentEventCursor(caught)) {
            await recoverSnapshots(activeRunId)
            reconnectDelay = 250
            continue
          }
          await new Promise((resolve) => window.setTimeout(resolve, reconnectDelay))
          reconnectDelay = Math.min(4000, reconnectDelay * 2)
        }
      }
    })()
    return () => controller.abort()
  }, [
    activeConversationId,
    activeRunId,
    discoverLatestRun,
    expectedUserId,
    recoverSnapshots,
    refreshConversation,
    refreshLists,
  ])

  const streamArtifacts = useMemo(() => {
    if (!activeRunId) return []
    const projection = eventState.runs[activeRunId]
    return projection
      ? projection.artifactOrder.flatMap((key) => {
          const artifact = projection.artifacts[key]
          return artifact ? [artifact] : []
        })
      : []
  }, [activeRunId, eventState.runs])

  const selectedConversation =
    conversation?.conversationId === activeConversationId ? conversation : null
  const combinedConversation = useMemo(() => {
    if (!selectedConversation) return null
    const knownIds = new Set(selectedConversation.artifacts.map((artifact) => artifact.artifactId))
    const knownMessageIds = new Set(
      selectedConversation.messages.map((message) => message.messageId),
    )
    const transientMessageIds = [
      ...new Set(
        streamArtifacts.flatMap((artifact) =>
          knownMessageIds.has(artifact.messageId) ? [] : [artifact.messageId],
        ),
      ),
    ]
    const projection = activeRunId ? eventState.runs[activeRunId] : undefined
    const latestTool = projection
      ? Object.values(projection.tools)
          .sort((left, right) => left.updatedAt.localeCompare(right.updatedAt))
          .at(-1)
      : undefined
    return {
      ...selectedConversation,
      messages: [
        ...selectedConversation.messages,
        ...transientMessageIds.map((messageId, index) => ({
          messageId,
          runId: activeRunId,
          sequenceNumber: selectedConversation.latestSequence + index + 1,
          role: 'TOOL' as const,
          contentKind: 'TOOL_RESULT' as const,
          textContent: null,
          contentJson: null,
          correlationId: latestTool ? `stream:${latestTool.toolName}` : 'stream:tool',
          createdAt:
            streamArtifacts.find((artifact) => artifact.messageId === messageId)?.createdAt ??
            new Date().toISOString(),
        })),
      ],
      artifacts: [
        ...selectedConversation.artifacts,
        ...streamArtifacts.filter((artifact) => !knownIds.has(artifact.artifactId)),
      ],
    }
  }, [activeRunId, eventState.runs, selectedConversation, streamArtifacts])

  const products = useMemo(
    () => productsFromAgentArtifacts(combinedConversation?.artifacts ?? []),
    [combinedConversation?.artifacts],
  )
  const currentProductSnapshots = useMemo(
    () => currentAgentProductSnapshots(combinedConversation?.artifacts ?? []),
    [combinedConversation?.artifacts],
  )
  useEffect(() => {
    if (currentProductSnapshots.length > 0) {
      onAgentProductsSnapshot?.(currentProductSnapshots)
    }
  }, [currentProductSnapshots, onAgentProductsSnapshot])
  const allProducts = useMemo(() => {
    const byId = new Map(cartProducts.map((product) => [product.id, product]))
    products.forEach((product) => byId.set(product.id, product))
    return [...byId.values()]
  }, [cartProducts, products])
  const interactionState = useMemo(
    () => productInteractionState(combinedConversation?.artifacts ?? []),
    [combinedConversation?.artifacts],
  )
  const pinnedProducts = useMemo(
    () => products.filter((product) => interactionState.pinned.has(product.id)),
    [interactionState.pinned, products],
  )
  const watchedSet = useMemo(() => new Set<ProductId>(), [])
  const visibleCart = optimisticCart ?? cart

  const allMessages = useMemo(() => {
    const localMessages = activeConversationId
      ? (localMessagesByConversationId[activeConversationId] ?? [])
      : []
    if (!combinedConversation) return localMessages.map(({ message }) => message)
    const authoritative = discoverMessagesFromAgentConversation(
      combinedConversation,
      deliveryLocations,
    )
    if (!activeRunId) return mergeAnchoredLocalMessages(authoritative, localMessages)
    const projection = eventState.runs[activeRunId]
    const knownMessageIds = new Set(
      combinedConversation.messages.map((message) => message.messageId),
    )
    const activeRunMessageIds = new Set(
      combinedConversation.messages.flatMap((message) =>
        message.runId === activeRunId ? [message.messageId] : [],
      ),
    )
    return mergeAnchoredLocalMessages(
      withProjectedAgentMessages(
        authoritative,
        knownMessageIds,
        activeRunMessageIds,
        activeRunId,
        projection,
      ),
      localMessages,
    )
  }, [
    activeConversationId,
    activeRunId,
    combinedConversation,
    deliveryLocations,
    eventState.runs,
    localMessagesByConversationId,
  ])
  if (activeConversationId) {
    visibleMessageIdsByConversationRef.current.set(
      activeConversationId,
      allMessages.map(({ id }) => id),
    )
  }

  const messages = useMemo(() => {
    if (!activeConversationId) return allMessages
    const dismissed = new Set(dismissedMessageIds[activeConversationId] ?? [])
    return dismissed.size > 0
      ? allMessages.filter((message) => !dismissed.has(message.id))
      : allMessages
  }, [activeConversationId, allMessages, dismissedMessageIds])
  const liveCartMessageId = useMemo(() => latestCartBlockMessageId(messages), [messages])
  liveCartMessageIdRef.current = liveCartMessageId

  const removeMessage = useCallback(
    (messageId: string) => {
      if (!activeConversationId) return
      const messageKey = `${activeConversationId}:${messageId}`
      const timeout = autoDismissTimeoutsRef.current.get(messageKey)
      if (timeout !== undefined) {
        window.clearTimeout(timeout)
        autoDismissTimeoutsRef.current.delete(messageKey)
      }
      setAutoRemovingMessageKeys((current) => {
        if (!current.has(messageKey)) return current
        const next = new Set(current)
        next.delete(messageKey)
        return next
      })
      setDismissedMessageIds((current) => {
        const existing = current[activeConversationId] ?? []
        if (existing.includes(messageId)) return current
        return { ...current, [activeConversationId]: [...existing, messageId] }
      })
    },
    [activeConversationId, setDismissedMessageIds],
  )

  const appendLocalMessage = useCallback(
    (message: DiscoverChatMessage, conversationId = activeConversationIdRef.current) => {
      if (!conversationId) return
      setLocalMessagesByConversationId((current) => {
        const precedingMessageIds =
          visibleMessageIdsByConversationRef.current.get(conversationId) ?? []
        visibleMessageIdsByConversationRef.current.set(conversationId, [
          ...precedingMessageIds,
          message.id,
        ])
        return {
          ...current,
          [conversationId]: [...(current[conversationId] ?? []), { message, precedingMessageIds }],
        }
      })
    },
    [],
  )

  const appendUnavailableFeatureMessage = useCallback(() => {
    appendLocalMessage(comingSoonMessage(uniqueRequestId('coming-soon')))
  }, [appendLocalMessage])

  const subscribeToNewsletter = useCallback(async () => {
    if (newsletter || newsletterPending) return
    const conversationId = activeConversationIdRef.current
    if (!conversationId) return
    setNewsletterPending(true)
    try {
      await onNewsletterChange(true)
      appendLocalMessage(
        {
          id: uniqueRequestId('newsletter'),
          role: 'ai',
          blocks: [{ type: 'system', text: NEWSLETTER_SUBSCRIBED_MESSAGE }],
        },
        conversationId,
      )
    } catch {
      appendLocalMessage(
        {
          id: uniqueRequestId('newsletter'),
          role: 'ai',
          blocks: [
            {
              type: 'system',
              text: 'Could not update newsletter settings. Please try again.',
            },
          ],
        },
        conversationId,
      )
    } finally {
      setNewsletterPending(false)
    }
  }, [appendLocalMessage, newsletter, newsletterPending, onNewsletterChange])

  useEffect(() => {
    if (!combinedConversation) return
    const conversationId = combinedConversation.conversationId
    const dismissed = new Set(dismissedMessageIds[conversationId] ?? [])
    for (const message of combinedConversation.messages) {
      if (!isProductPinNotice(message) || dismissed.has(message.messageId)) continue
      const timeoutKey = `${conversationId}:${message.messageId}`
      if (autoDismissTimeoutsRef.current.has(timeoutKey)) continue
      const timeout = window.setTimeout(() => {
        setAutoRemovingMessageKeys((current) => {
          if (current.has(timeoutKey)) return current
          return new Set(current).add(timeoutKey)
        })
        autoDismissTimeoutsRef.current.delete(timeoutKey)
      }, PRODUCT_PIN_NOTICE_LIFETIME_MS)
      autoDismissTimeoutsRef.current.set(timeoutKey, timeout)
    }
  }, [combinedConversation, dismissedMessageIds, setDismissedMessageIds])

  useEffect(() => {
    const timeouts = autoDismissTimeoutsRef.current
    return () => {
      timeouts.forEach((timeout) => window.clearTimeout(timeout))
      timeouts.clear()
    }
  }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [messages.length, activeRunId, eventState.runs])

  const toolActivities = useMemo<AgentActivity[]>(() => {
    if (!activeRunId) return []
    const projection = eventState.runs[activeRunId]
    if (!projection) return []
    return Object.values(projection.tools).map((activity) => ({
      agent: activity.toolName,
      label: activity.safeMessage ?? activity.summary ?? activity.toolName.replaceAll('_', ' '),
      state:
        activity.status === 'FAILED'
          ? 'error'
          : activity.status === 'COMPLETED'
            ? 'done'
            : 'active',
      updatedAt: Date.parse(activity.updatedAt),
    }))
  }, [activeRunId, eventState.runs])

  const activeThreads = useMemo(
    () =>
      conversations
        .filter(
          (summary) =>
            summary.latestSequence > 0 || summary.conversationId === activeConversationId,
        )
        .map((summary) =>
          threadFromAgentConversationSummary(
            summary,
            summary.conversationId === activeConversationId ? messages : undefined,
          ),
        ),
    [activeConversationId, conversations, messages],
  )
  const historyThreads = useMemo(
    () => [
      ...activeThreads,
      ...archivedConversations
        .filter((summary) => summary.latestSequence > 0)
        .map((summary) => threadFromAgentConversationSummary(summary)),
    ],
    [activeThreads, archivedConversations],
  )
  const shelfMessageSet = useMemo(
    () => new Set(shelf.flatMap((item) => (item.kind === 'message' ? [item.messageId] : []))),
    [shelf],
  )
  const shelfProductSet = useMemo(
    () => new Set(shelf.flatMap((item) => (item.kind === 'product' ? [item.productId] : []))),
    [shelf],
  )
  const currentStatus =
    eventState.runs[activeRunId ?? '']?.status ??
    (runSnapshot?.runId === activeRunId ? runSnapshot.status : undefined)
  const submitting = pendingSubmissions > 0
  const isRunning = Boolean(activeRunId && !isTerminalAgentRunStatus(currentStatus ?? 'QUEUED'))
  const agentActionsDisabled = submitting || Boolean(activeRunId)
  const activeRunProjection = activeRunId ? eventState.runs[activeRunId] : undefined
  const activeRunHasVisibleOutput = Boolean(
    activeRunId &&
    (activeRunProjection?.streamingAssistantText.trim() ||
      activeRunProjection?.assistantMessages.length ||
      combinedConversation?.messages.some(
        (message) => message.runId === activeRunId && message.role === 'ASSISTANT',
      ) ||
      combinedConversation?.artifacts.some((artifact) => artifact.runId === activeRunId)),
  )
  const workingStage = agentWorkingStage({
    submitting,
    isRunning,
    status:
      currentStatus === 'RUNNING' ? 'RUNNING' : currentStatus === 'QUEUED' ? 'QUEUED' : undefined,
    hasVisibleOutput: activeRunHasVisibleOutput,
  })
  const suggestedReplies = useMemo(() => {
    const latest = messages.at(-1)
    return latest?.role === 'ai' ? (latest.suggestedReplies ?? []) : []
  }, [messages])
  const suggestedReplySubmissions = useMemo(() => {
    const latest = messages.at(-1)
    return latest?.role === 'ai' ? (latest.suggestedReplySubmissions ?? []) : []
  }, [messages])

  const submit = useCallback(
    async (text: string): Promise<boolean> => {
      const queuedText = text.trim()
      if (loading || !queuedText) {
        return false
      }
      const requestedConversationId = activeConversationIdRef.current
      const requestedConversation =
        conversationRef.current?.conversationId === requestedConversationId
          ? conversationRef.current
          : null
      setError(null)
      void turnSubmissionQueue
        .enqueue(async () => {
          setPendingSubmissions((current) => current + 1)
          try {
            let targetConversationId = requestedConversationId ?? activeConversationIdRef.current
            let targetConversation =
              requestedConversation ??
              (conversationRef.current?.conversationId === targetConversationId
                ? conversationRef.current
                : null)
            if (!targetConversationId || !targetConversation) {
              const created = await createAgentConversation({
                merchantId: draftMerchantId,
                expectedUserId,
              })
              targetConversationId = created.conversationId
              targetConversation = emptyConversationFromSummary(created)
              setConversations((current) => [
                created,
                ...current.filter((item) => item.conversationId !== created.conversationId),
              ])
              updateActiveConversationId(targetConversationId)
              updateConversationState(() => targetConversation)
            }
            invalidateConversationSnapshotRequests(targetConversationId)
            if (
              targetConversation.latestSequence === 0 &&
              targetConversation.title === 'New conversation'
            ) {
              const renamed = await updateAgentConversation({
                conversationId: targetConversationId,
                title: derivedTitle(queuedText),
                expectedUserId,
              })
              setConversations((current) =>
                current.map((item) =>
                  item.conversationId === renamed.conversationId ? renamed : item,
                ),
              )
            }
            const submittedCartRevision = onCaptureAgentCartRevision()
            const visibleProductContext =
              visibleProductContextRef.current?.conversationId === targetConversationId
                ? visibleProductContextRef.current.context
                : undefined
            const turn = await submitAgentTurn({
              conversationId: targetConversationId,
              message: queuedText,
              clientTurnId: uniqueRequestId('turn'),
              visibleProductContext,
              shelfContext: agentShelfContext(shelf),
              expectedUserId,
            })
            onAgentRunSubmitted?.(turn.runId, targetConversationId, submittedCartRevision)
            if (activeConversationIdRef.current === targetConversationId) {
              updateConversationState((current) => {
                const base =
                  current?.conversationId === targetConversationId ? current : targetConversation
                return {
                  ...base,
                  latestSequence: Math.max(base.latestSequence, turn.userMessage.sequenceNumber),
                  messages: base.messages.some(
                    (message) => message.messageId === turn.userMessage.messageId,
                  )
                    ? base.messages
                    : [...base.messages, turn.userMessage],
                }
              })
              if (!activeRunIdRef.current) {
                setRunSnapshot(null)
                updateActiveRunId(turn.runId)
              }
            }
          } catch (caught) {
            setError(caught instanceof Error ? caught.message : 'Could not submit this message.')
          } finally {
            setPendingSubmissions((current) => Math.max(0, current - 1))
          }
        })
        .catch((caught) => {
          setError(caught instanceof Error ? caught.message : 'Could not process this request.')
        })
      return true
    },
    [
      expectedUserId,
      invalidateConversationSnapshotRequests,
      loading,
      onCaptureAgentCartRevision,
      onAgentRunSubmitted,
      draftMerchantId,
      shelf,
      updateActiveConversationId,
      updateActiveRunId,
      updateConversationState,
      turnSubmissionQueue,
    ],
  )

  const stop = useCallback(async () => {
    if (!activeRunId) return
    const targetRunId = activeRunId
    setError(null)
    try {
      const cancelled = await cancelAgentRun(targetRunId, { expectedUserId })
      if (cancelled && activeRunIdRef.current === targetRunId) setRunSnapshot(cancelled)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not stop this run.')
    }
  }, [activeRunId, expectedUserId])

  const executeAction = useCallback(
    async (
      targetConversationId: string,
      toolName: string,
      argumentsValue: unknown,
      summary: string,
    ) => {
      const argumentsJson = JSON.stringify(argumentsValue)
      const pendingKey = `${expectedUserId}:${targetConversationId}:${toolName}:${argumentsJson}`
      if (actionPendingRef.current.has(pendingKey)) return null
      const idempotencyKey = actionIdempotencyRef.current.keyFor(pendingKey, () =>
        uniqueRequestId('action'),
      )
      actionPendingRef.current.add(pendingKey)
      setActionPending((current) => new Set(current).add(pendingKey))
      setError(null)
      try {
        const submittedCartRevision = onCaptureAgentCartRevision()
        let result: AgentDirectActionProfile
        try {
          result = await recordAgentDirectAction({
            conversationId: targetConversationId,
            toolName,
            argumentsJson,
            idempotencyKey,
            summary,
            expectedUserId,
          })
        } catch (caught) {
          actionIdempotencyRef.current.failed(pendingKey, caught)
          setError(caught instanceof Error ? caught.message : 'That action could not be completed.')
          return null
        }
        actionIdempotencyRef.current.completed(pendingKey)
        try {
          if (activeConversationIdRef.current === targetConversationId) {
            invalidateConversationSnapshotRequests(targetConversationId)
            updateConversationState((current) => (current ? mergeAction(current, result) : current))
          }
          const cartReplacements = cartStateReplacementsFromAgentArtifacts(
            result.artifacts,
            allProducts,
          )
          if (cartReplacements.length > 0) {
            visibleCartRef.current = onAgentCartSnapshot(cartReplacements, submittedCartRevision)
          }
        } catch {
          setError('The action completed, but its latest result could not be displayed.')
        }
        return result
      } finally {
        actionPendingRef.current.delete(pendingKey)
        setActionPending((current) => {
          const next = new Set(current)
          next.delete(pendingKey)
          return next
        })
      }
    },
    [
      allProducts,
      expectedUserId,
      invalidateConversationSnapshotRequests,
      onAgentCartSnapshot,
      onCaptureAgentCartRevision,
      updateConversationState,
    ],
  )

  const performAction = useCallback(
    (toolName: string, argumentsValue: unknown, summary: string) => {
      const targetConversationId = activeConversationId
      if (!targetConversationId) return Promise.resolve(null)
      const pendingKey = `${expectedUserId}:${targetConversationId}:${toolName}:${JSON.stringify(argumentsValue)}`
      return actionQueue.enqueueUnique(pendingKey, () =>
        executeAction(targetConversationId, toolName, argumentsValue, summary),
      )
    },
    [actionQueue, activeConversationId, executeAction, expectedUserId],
  )

  const togglePin = (product: Product) => {
    const pinned = interactionState.pinned.has(product.id)
    void performAction(
      pinned ? 'unpin_product' : 'pin_product',
      { canonicalProductKey: product.id, offerKey: exactProductOfferKey(product) ?? undefined },
      `${pinned ? 'Unpinned' : 'Pinned'} ${product.name}`,
    )
  }
  const clearCompareTray = async () => {
    setTrayClearing(true)
    try {
      await Promise.all(
        pinnedProducts.map((product) =>
          performAction(
            'unpin_product',
            {
              canonicalProductKey: product.id,
              offerKey: exactProductOfferKey(product) ?? undefined,
            },
            `Unpinned ${product.name}`,
          ),
        ),
      )
    } finally {
      setTrayClearing(false)
    }
  }
  const toggleWatch = () => {
    appendUnavailableFeatureMessage()
  }
  const addToCart = (product: Product) => {
    setError(null)
    void addChatProductToCart(product, onAddSelectedOfferToCart, () => {
      const conversationId = activeConversationIdRef.current
      if (!conversationId || liveCartMessageIdRef.current) return
      const messageId = uniqueRequestId('cart')
      liveCartMessageIdRef.current = messageId
      appendLocalMessage(cartInChatMessage(messageId), conversationId)
    }).then((result) => {
      if (result !== 'added') {
        setError(
          result === 'missing-offer'
            ? 'This product does not have an exact purchasable offer yet.'
            : 'Could not add this item to the merchant cart.',
        )
      }
    })
  }
  const dig = (kind: 'reviews' | 'code' | 'similar', product: Product) => {
    const key = product.id
    const offerKey = exactProductOfferKey(product) ?? undefined
    if (kind === 'reviews') {
      void performAction(
        'get_product_reviews',
        { canonicalProductKey: key, selectedOfferKey: offerKey },
        `Loaded reviews for ${product.name}`,
      )
    } else if (kind === 'code') {
      appendUnavailableFeatureMessage()
    } else {
      void performAction(
        'find_similar_products',
        { canonicalProductKey: key, query: `products similar to ${product.name}` },
        `Found products similar to ${product.name}`,
      )
    }
  }
  const compareHere = async (candidates: readonly Product[], openFull = false) => {
    const selected = candidates.slice(0, 4)
    if (selected.length < 2) return
    const result = await performAction(
      'compare_products',
      { canonicalProductKeys: selected.map((product) => product.id) },
      `Compared ${selected.map((product) => product.name).join(' and ')}`,
    )
    if (result && openFull) onCompareProducts(selected)
  }
  const justPick = (candidates: readonly Product[]) => {
    if (candidates.length === 0) return
    void performAction(
      'pick_recommended_product',
      { canonicalProductKeys: candidates.slice(0, 10).map((product) => product.id) },
      'Picked the recommended product',
    )
  }

  const updateCartQuantity = (
    _messageId: string,
    _blockIndex: number,
    id: ProductId,
    merchant: string,
    quantity: number,
    _nextCart: readonly CartItem[],
    identity?: string,
    quantityDelta?: number,
    sourceItem?: CartItem,
  ) => {
    const change = optimisticAgentCartQuantityChange(
      optimisticCartRef.current ?? onReadAgentCart(),
      id,
      merchant,
      quantity,
      identity,
      quantityDelta,
      sourceItem,
    )
    if (!change) {
      setError('This cart changed. Open the full cart to review its current items.')
      return
    }

    optimisticCartRef.current = change.cart
    setOptimisticCart(change.cart)
    setError(null)
    const mutationSequence = cartMutationSequenceRef.current + 1
    cartMutationSequenceRef.current = mutationSequence

    void Promise.resolve()
      .then(() => onUpdateCartQuantity(change.target, change.quantity, change.identity))
      .catch((caught) => {
        setError(caught instanceof Error ? caught.message : 'Could not update this cart.')
      })
      .finally(() => {
        if (cartMutationSequenceRef.current !== mutationSequence) return
        optimisticCartRef.current = null
        setOptimisticCart(null)
      })
  }
  const removeCartLine = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
    identity?: string,
    sourceItem?: CartItem,
  ) =>
    updateCartQuantity(
      messageId,
      blockIndex,
      id,
      merchant,
      0,
      nextCart,
      identity,
      undefined,
      sourceItem,
    )
  const openCheckoutInChat = () => {
    const targetConversationId = activeConversationIdRef.current
    if (!targetConversationId) return
    const message = checkoutInChatMessage(uniqueRequestId('checkout'), onReadAgentCart())
    if (!message) {
      setError('Your merchant cart must be ready before checkout can start.')
      return
    }
    setError(null)
    appendLocalMessage(message, targetConversationId)
  }

  const returnHome = useCallback(() => {
    streamAbortRef.current?.abort()
    streamAbortRef.current = null
    updateActiveConversationId(null)
    updateActiveRunId(null)
    updateConversationState(() => null)
    setRunSnapshot(null)
    setLoading(false)
    setError(null)
    setShareOpen(false)
    setDraftMerchantId(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [updateActiveConversationId, updateActiveRunId, updateConversationState])

  useEffect(() => {
    if (handledHomeRequestRef.current === homeRequestId) return
    handledHomeRequestRef.current = homeRequestId
    returnHome()
  }, [homeRequestId, returnHome])

  useEffect(() => {
    if (
      !productDetailChatRequest ||
      handledProductRequestRef.current === productDetailChatRequest.id
    ) {
      return
    }
    const request = productDetailChatRequest
    handledProductRequestRef.current = request.id
    onProductDetailChatRequestHandled(request.id)
    void submit(`About ${request.product.name}: ${request.question}`)
  }, [onProductDetailChatRequestHandled, productDetailChatRequest, submit])

  useEffect(() => {
    if (!discoverFindRequest || handledFindRequestRef.current === discoverFindRequest.id) return
    handledFindRequestRef.current = discoverFindRequest.id
    const found = messages.find((message) =>
      discoverFindRequest.kind === 'message'
        ? message.id === discoverFindRequest.messageId
        : productsInMessage(message).some(
            (product) => product.id === discoverFindRequest.productId,
          ),
    )
    if (!found) return
    window.requestAnimationFrame(() => {
      document.querySelector<HTMLElement>(`[data-mid="${CSS.escape(found.id)}"]`)?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      })
      onFlashMessage(found.id)
    })
  }, [discoverFindRequest, messages, onFlashMessage])
  const selectConversation = async (conversationId: string) => {
    const archived = archivedConversations.find((item) => item.conversationId === conversationId)
    if (archived) {
      try {
        const restored = await updateAgentConversation({
          conversationId,
          archived: false,
          expectedUserId,
        })
        setArchivedConversations((current) =>
          current.filter((item) => item.conversationId !== conversationId),
        )
        setConversations((current) => [restored, ...current])
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : 'Could not restore this conversation.')
        return
      }
    }
    updateActiveConversationId(conversationId)
  }
  const archiveConversation = async (conversationId: string) => {
    if (!conversations.some((item) => item.conversationId === conversationId)) {
      return
    }
    try {
      const archived = await updateAgentConversation({
        conversationId,
        archived: true,
        expectedUserId,
      })
      const remaining = conversations.filter((item) => item.conversationId !== conversationId)
      setConversations(remaining)
      setArchivedConversations((current) => [archived, ...current])
      if (conversationId === activeConversationId) {
        const nextConversation = remaining.find((item) => item.latestSequence > 0)
        if (nextConversation) updateActiveConversationId(nextConversation.conversationId)
        else returnHome()
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not archive this conversation.')
    }
  }
  const deleteConversation = async (conversationId: string) => {
    try {
      await deleteAgentConversation(conversationId, { expectedUserId })
      const timeoutPrefix = `${conversationId}:`
      for (const [key, timeout] of autoDismissTimeoutsRef.current) {
        if (!key.startsWith(timeoutPrefix)) continue
        window.clearTimeout(timeout)
        autoDismissTimeoutsRef.current.delete(key)
      }
      const remaining = conversations.filter((item) => item.conversationId !== conversationId)
      setConversations(remaining)
      setArchivedConversations((current) =>
        current.filter((item) => item.conversationId !== conversationId),
      )
      setDismissedMessageIds((current) => {
        if (!(conversationId in current)) return current
        const next = { ...current }
        delete next[conversationId]
        return next
      })
      if (conversationId === activeConversationId) {
        const nextConversation = remaining.find((item) => item.latestSequence > 0)
        if (nextConversation) updateActiveConversationId(nextConversation.conversationId)
        else returnHome()
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not delete this conversation.')
    }
  }
  const renameConversation = async (conversationId: string, title: string) => {
    try {
      const renamed = await updateAgentConversation({
        conversationId,
        title,
        expectedUserId,
      })
      setConversations((current) =>
        current.map((item) => (item.conversationId === conversationId ? renamed : item)),
      )
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not rename this conversation.')
    }
  }

  const addMessageToShelf = (message: DiscoverChatMessage) => {
    onShelfAddMessage({
      kind: 'message',
      messageId: message.id,
      snapshot: shelfMessageSnapshot(message),
    })
    onOpenShelf()
  }
  const addProductToShelf = (product: Product) => {
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

  const checkoutHostMessageId =
    [...messages]
      .reverse()
      .find((message) => message.blocks?.some((block) => block.type === 'checkout'))?.id ?? null
  const conversationCheckout =
    activeCheckout?.threadId === activeConversationId ? activeCheckout : null

  if (!activeConversationId) {
    return (
      <>
        <Workbench />
        <main className="mt-feed mt-ct-feed mt-ct-feed-hero">
          <DiscoverHomeHero
            profile={profile}
            greeting={greeting}
            prompts={prompts}
            onSubmit={(text) => void submit(text)}
            loading={loading || submitting}
            merchants={merchants}
            selectedMerchantId={draftMerchantId}
            merchantsLoading={merchantsLoading}
            merchantsError={merchantsError}
            onMerchant={setDraftMerchantId}
            historyThreads={historyThreads}
            activeThreadId=""
            onHistorySelect={(id) => void selectConversation(id)}
            onHistoryDelete={(id) => void deleteConversation(id)}
            replyDraft={null}
            onClearReply={() => undefined}
          />
          {workingStage ? <AgentWorkingIndicator stage={workingStage} /> : null}
          {error ? (
            <div className="mt-ct-history-error" role="alert">
              <span>{error}</span>
              <button
                type="button"
                onClick={() => {
                  setError(null)
                }}
              >
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
      <Workbench />
      <main className="mt-feed mt-ct-feed">
        {activeThreads.length > 0 && activeConversationId ? (
          <DiscoverThreadTabs
            threads={activeThreads}
            activeId={activeConversationId}
            onSelect={(id) => void selectConversation(id)}
            onDelete={(id) => void archiveConversation(id)}
            onNew={returnHome}
            onRename={(id, title) => void renameConversation(id, title)}
            onShare={() => setShareOpen(true)}
            onReorder={(fromIndex, toIndex) =>
              setConversations((current) => {
                const next = [...current]
                const [moved] = next.splice(fromIndex, 1)
                if (moved) next.splice(toIndex, 0, moved)
                return next
              })
            }
            onDeleteHistory={(id) => void deleteConversation(id)}
            historyThreads={historyThreads}
          />
        ) : null}
        {error ? (
          <div className="mt-ct-history-error" role="alert">
            <span>{error}</span>
            <button
              type="button"
              onClick={() => {
                setError(null)
              }}
            >
              Dismiss
            </button>
          </div>
        ) : null}
        {shareOpen ? (
          <DiscoverShareSheet
            thread={
              activeThreads.find((thread) => thread.id === activeConversationId) ?? {
                id: activeConversationId,
                title: 'Shopping conversation',
                messages: [],
              }
            }
            newsletter={newsletter}
            newsletterPending={newsletterPending}
            onClose={() => setShareOpen(false)}
            onSend={() => {
              setShareOpen(false)
              appendUnavailableFeatureMessage()
            }}
            onNewsletterSignup={() => void subscribeToNewsletter()}
          />
        ) : null}
        <div className="mt-ct-thread">
          <div className="mt-ct-msg mt-ct-meant mt-ct-greeting">
            <span className="mt-ct-av">
              <MeantHeartMark size={18} />
            </span>
            <div className="mt-ct-meant-body">
              <p className="mt-ct-intro">
                {greeting}, {profile.name}. I can search, compare, inspect what you own, build
                carts, and prepare merchant checkout while keeping every step in this conversation.
              </p>
            </div>
          </div>
          {!loading && messages.length === 0 ? (
            <div className="mt-ct-empty-prompts">
              {prompts.map((prompt) => (
                <button
                  key={prompt}
                  className="mt-ct-suggchip"
                  type="button"
                  onClick={() => void submit(prompt)}
                >
                  {prompt}
                </button>
              ))}
            </div>
          ) : null}
          {messages.map((message) => (
            <DiscoverChatMessageRow
              key={message.id}
              threadId={activeConversationId ?? 'agent'}
              message={message}
              deliveryLocations={deliveryLocations}
              preferences={preferences}
              cart={visibleCart}
              cartProducts={allProducts}
              savedSet={savedSet}
              savePendingSet={savePendingSet}
              pinnedSet={interactionState.pinned}
              watchedSet={watchedSet}
              shelfMessageSet={shelfMessageSet}
              shelfProductSet={shelfProductSet}
              flash={shelfFlashMessageId === message.id}
              celebrateArrival={false}
              immutable
              useLiveCart={message.id === liveCartMessageId}
              agentActionsDisabled={agentActionsDisabled}
              deletable
              removing={autoRemovingMessageKeys.has(
                `${activeConversationId ?? 'agent'}:${message.id}`,
              )}
              onOpen={onOpen}
              onToggleSave={onToggleSave}
              onAddCart={addToCart}
              onPin={togglePin}
              onWatch={toggleWatch}
              onDig={dig}
              onJustPick={justPick}
              onCompareHere={(items) => void compareHere(items)}
              onOpenFullCompare={(items) => void compareHere(items, true)}
              onOpenSaved={onOpenSaved}
              onOpenOrders={onOpenOrders}
              onOpenPrefs={onOpenPrefs}
              onOpenCart={onOpenCart}
              onReviewCartHere={() => undefined}
              onRestoreCartLine={addToCart}
              onCartQty={updateCartQuantity}
              onCartRemove={removeCartLine}
              onCheckout={onCheckout}
              activeCheckout={message.id === checkoutHostMessageId ? conversationCheckout : null}
              checkoutBusy={checkoutBusy}
              checkoutError={checkoutError}
              onCheckoutAssistant={onCheckoutAssistant}
              onRefreshCheckout={onRefreshCheckout}
              onReleaseCheckout={onReleaseCheckout}
              onCheckoutHere={openCheckoutInChat}
              newsletter={newsletter}
              newsletterPending={newsletterPending}
              onNewsletterSignup={() => void subscribeToNewsletter()}
              onDelete={removeMessage}
              onShelfAddMessage={(item) => addMessageToShelf(item)}
              onShelfAddProduct={(product) => addProductToShelf(product)}
              onDragMessage={dragMessage}
              onDragProduct={dragProduct}
              onVisibleProductContextChange={captureVisibleProductContext}
            />
          ))}
          {workingStage ? <AgentWorkingIndicator stage={workingStage} /> : null}
          {toolActivities.length > 0 && isRunning ? (
            <AgentActivityPanel activities={toolActivities} />
          ) : null}
          {loading ? (
            <div className="mt-ct-system">
              <span className="mt-scan-pulse" /> Loading your agent conversation…
            </div>
          ) : null}
          {actionPending.size > 0 ? (
            <div className="mt-ct-system" aria-live="polite">
              <span className="mt-scan-pulse" /> Applying your action…
            </div>
          ) : null}
          <div ref={bottomRef} className="mt-ct-bottom-sentinel" aria-hidden="true" />
        </div>

        {pinnedProducts.length > 0 ? (
          <div className="mt-ct-tray">
            <span className="mt-mono mt-ct-tray-label">Compare tray</span>
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
                    aria-label={`Unpin ${product.name}`}
                    disabled={trayClearing || agentActionsDisabled}
                    onClick={() => togglePin(product)}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
            <button
              className="mt-ct-tray-mini"
              type="button"
              disabled={pinnedProducts.length < 2 || trayClearing || agentActionsDisabled}
              onClick={() => void compareHere(pinnedProducts)}
            >
              Compare here
            </button>
            <button
              className="mt-ct-tray-go"
              type="button"
              disabled={pinnedProducts.length < 2 || trayClearing || agentActionsDisabled}
              onClick={() => void compareHere(pinnedProducts, true)}
            >
              Full compare
            </button>
            <button
              className="mt-ct-tray-clear"
              type="button"
              onClick={() => void clearCompareTray()}
              disabled={trayClearing || agentActionsDisabled}
            >
              Clear
            </button>
          </div>
        ) : null}

        <div className="mt-ct-dock">
          <div className="mt-ct-dock-inner">
            <AskComposer
              placeholder="Ask Meant to search, compare, inspect inventory, or build your cart…"
              suggestions={suggestedReplies}
              suggestionValues={suggestedReplySubmissions}
              showChips={suggestedReplies.length > 0}
              onAsk={submit}
              running={isRunning}
              queueWhileRunning
              onStop={stop}
              disabled={loading || !activeConversationId}
            />
          </div>
        </div>
      </main>
    </>
  )
}
