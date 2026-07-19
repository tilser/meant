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
} from '../../../lib/apiClient'
import { AskComposer } from '../ask/AskComposer'
import type { ActiveCheckoutSession, CheckoutAssistantHandler } from '../cart/checkoutTypes'
import { AgentActivityPanel } from '../chat/AgentActivityPanel'
import { DiscoverChatMessageRow } from '../chat/DiscoverChatMessageRow'
import { DiscoverThreadTabs } from '../chat/DiscoverThreadTabs'
import type {
  AgentActivity,
  DiscoverChatMessage,
  DiscoverChatThread,
  DiscoverFindRequest,
  ProductDetailChatRequest,
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
import { ProductArtwork, SparkMark } from '../shared/ui'
import type {
  CartItem,
  CheckoutPayload,
  Preference,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import { cartItemIdentity } from '../utils'
import {
  cartItemsFromAgentArtifacts,
  discoverMessagesFromAgentConversation,
  latestCartSnapshotArtifacts,
  productInteractionState,
  productsFromAgentArtifacts,
} from './artifactMapping'
import {
  createAgentEventReducerState,
  reduceAgentEvent,
  restoreAgentRunSnapshot,
  snapshotRecoveryForAgentRun,
  type AgentEventReducerState,
} from './eventReducer'
import { isExpiredAgentEventCursor, streamAgentRunEvents } from './eventStream'
import { AgentActionRequestIdentityStore } from './requestIdentity'

const TERMINAL_RUNS = new Set(['WAITING_FOR_USER', 'COMPLETED', 'FAILED', 'CANCELLED'])

function isTerminalRun(run: AgentRunSnapshotProfile | null | undefined): boolean {
  return Boolean(run && TERMINAL_RUNS.has(run.status))
}

function uniqueRequestId(prefix: string): string {
  const random = globalThis.crypto?.randomUUID?.()
  return random ? `${prefix}:${random}` : `${prefix}:${Date.now()}:${Math.random()}`
}

function derivedTitle(text: string): string {
  const normalized = text.trim().replace(/\s+/g, ' ')
  return normalized.length <= 48 ? normalized : `${normalized.slice(0, 45)}...`
}

function threadFromSummary(
  summary: AgentConversationSummaryProfile,
  messages: readonly DiscoverChatMessage[] = [],
): DiscoverChatThread {
  return {
    id: summary.conversationId,
    title: summary.title,
    messages,
    archived: summary.status === 'ARCHIVED',
    createdAt: Date.parse(summary.createdAt),
    updatedAt: Date.parse(summary.updatedAt),
    named: summary.title !== 'New conversation',
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
  newsletter: boolean
  onOpen: (product: Product, products?: readonly Product[], researchQuery?: string | null) => void
  onToggleSave: (product: Product) => void
  onCompareProducts: (products: readonly Product[]) => void
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
  onAgentCartSnapshot?: (lines: readonly CartItem[], cartIds: ReadonlySet<string>) => void
  onProductDetailChatRequestHandled: (requestId: string) => void
  onFlashMessage: (messageId: string) => void
}

export function AgentDiscoverView({
  expectedUserId,
  profile,
  greeting,
  prompts,
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
  newsletter,
  onOpen,
  onToggleSave,
  onCompareProducts,
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
  onAgentCartSnapshot,
  onProductDetailChatRequestHandled,
  onFlashMessage,
}: Readonly<AgentDiscoverViewProps>) {
  const [conversations, setConversations] = useState<AgentConversationSummaryProfile[]>([])
  const [archivedConversations, setArchivedConversations] = useState<
    AgentConversationSummaryProfile[]
  >([])
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [conversation, setConversation] = useState<AgentConversationDetailProfile | null>(null)
  const [activeRunId, setActiveRunId] = useState<string | null>(null)
  const [runSnapshot, setRunSnapshot] = useState<AgentRunSnapshotProfile | null>(null)
  const [eventState, setEventState] = useState<AgentEventReducerState>(() =>
    createAgentEventReducerState(),
  )
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [actionPending, setActionPending] = useState<ReadonlySet<string>>(new Set())
  const [error, setError] = useState<string | null>(null)
  const [shareNotice, setShareNotice] = useState<string | null>(null)
  const streamAbortRef = useRef<AbortController | null>(null)
  const eventStateRef = useRef(eventState)
  const activeConversationIdRef = useRef(activeConversationId)
  const recoveryInFlightRef = useRef(new Set<string>())
  const actionPendingRef = useRef(new Set<string>())
  const actionIdempotencyRef = useRef(new AgentActionRequestIdentityStore())
  const bottomRef = useRef<HTMLDivElement | null>(null)
  const handledHomeRequestRef = useRef(homeRequestId)
  const handledFindRequestRef = useRef<string | null>(null)
  const handledProductRequestRef = useRef<string | null>(null)
  eventStateRef.current = eventState
  activeConversationIdRef.current = activeConversationId

  const refreshLists = useCallback(async () => {
    const [active, archived] = await Promise.all([
      getAgentConversations({ expectedUserId, archived: false, limit: 50 }),
      getAgentConversations({ expectedUserId, archived: true, limit: 50 }),
    ])
    setConversations(active)
    setArchivedConversations(archived)
    return active
  }, [expectedUserId])

  const refreshConversation = useCallback(
    async (conversationId: string): Promise<AgentConversationDetailProfile> => {
      const snapshot = await getCompleteAgentConversation(conversationId, {
        expectedUserId,
        pageSize: 200,
      })
      if (activeConversationId === conversationId || !activeConversationId) {
        setConversation(snapshot)
      }
      return snapshot
    },
    [activeConversationId, expectedUserId],
  )

  const discoverLatestRun = useCallback(
    async (snapshot: AgentConversationDetailProfile) => {
      const runIds = [
        ...new Set(
          [...snapshot.messages]
            .reverse()
            .flatMap((message) => (message.runId ? [message.runId] : [])),
        ),
      ]
      for (const runId of runIds.slice(0, 3)) {
        try {
          const run = await getAgentRun(runId, { expectedUserId })
          setRunSnapshot(run)
          setEventState((current) => {
            const next = restoreAgentRunSnapshot(current, run)
            eventStateRef.current = next
            return next
          })
          if (!isTerminalRun(run)) {
            setActiveRunId(run.runId)
          }
          return
        } catch {
          // An old/pruned run does not prevent the durable conversation from rendering.
        }
      }
      setRunSnapshot(null)
      setActiveRunId(null)
    },
    [expectedUserId],
  )

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void (async () => {
      try {
        let active = await refreshLists()
        if (controller.signal.aborted) return
        if (active.length === 0) {
          const created = await createAgentConversation({
            expectedUserId,
            signal: controller.signal,
          })
          active = [created]
          setConversations(active)
        }
        const firstId = active[0]?.conversationId
        if (firstId) setActiveConversationId(firstId)
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
    setLoading(true)
    setError(null)
    setActiveRunId(null)
    streamAbortRef.current?.abort()
    void getCompleteAgentConversation(activeConversationId, {
      expectedUserId,
      pageSize: 200,
      signal: controller.signal,
    })
      .then(async (snapshot) => {
        if (controller.signal.aborted) return
        setConversation(snapshot)
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
  }, [activeConversationId, discoverLatestRun, expectedUserId])

  const recoverSnapshots = useCallback(
    async (runId: string) => {
      if (!activeConversationId || recoveryInFlightRef.current.has(runId)) return
      recoveryInFlightRef.current.add(runId)
      try {
        const [run, nextConversation] = await Promise.all([
          getAgentRun(runId, { expectedUserId }),
          getCompleteAgentConversation(activeConversationId, { expectedUserId, pageSize: 200 }),
        ])
        setRunSnapshot(run)
        setConversation(nextConversation)
        setEventState((current) => {
          const next = restoreAgentRunSnapshot(current, run)
          eventStateRef.current = next
          return next
        })
        if (isTerminalRun(run)) setActiveRunId(null)
      } finally {
        recoveryInFlightRef.current.delete(runId)
      }
    },
    [activeConversationId, expectedUserId],
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
          setRunSnapshot(run)
          if (isTerminalRun(run)) {
            await refreshConversation(activeConversationId)
            setActiveRunId(null)
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

  const combinedConversation = useMemo(() => {
    if (!conversation) return null
    const knownIds = new Set(conversation.artifacts.map((artifact) => artifact.artifactId))
    const knownMessageIds = new Set(conversation.messages.map((message) => message.messageId))
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
      ...conversation,
      messages: [
        ...conversation.messages,
        ...transientMessageIds.map((messageId, index) => ({
          messageId,
          runId: activeRunId,
          sequenceNumber: conversation.latestSequence + index + 1,
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
        ...conversation.artifacts,
        ...streamArtifacts.filter((artifact) => !knownIds.has(artifact.artifactId)),
      ],
    }
  }, [activeRunId, conversation, eventState.runs, streamArtifacts])

  const products = useMemo(
    () => productsFromAgentArtifacts(combinedConversation?.artifacts ?? []),
    [combinedConversation?.artifacts],
  )
  const allProducts = useMemo(() => {
    const byId = new Map(cartProducts.map((product) => [product.id, product]))
    products.forEach((product) => byId.set(product.id, product))
    return [...byId.values()]
  }, [cartProducts, products])
  const interactionState = useMemo(
    () => productInteractionState(combinedConversation?.artifacts ?? []),
    [combinedConversation?.artifacts],
  )
  const latestCart = useMemo(
    () =>
      cartItemsFromAgentArtifacts(
        latestCartSnapshotArtifacts(combinedConversation?.artifacts ?? []),
        allProducts,
      ),
    [allProducts, combinedConversation?.artifacts],
  )
  const visibleCart = latestCart.length > 0 ? latestCart : cart

  const messages = useMemo(() => {
    if (!combinedConversation) return []
    const authoritative = discoverMessagesFromAgentConversation(
      combinedConversation,
      deliveryLocations,
    )
    if (!activeRunId) return authoritative
    const projection = eventState.runs[activeRunId]
    if (!projection) return authoritative
    const knownMessageIds = new Set(
      combinedConversation.messages.map((message) => message.messageId),
    )
    const transient: DiscoverChatMessage[] = projection.assistantMessages.flatMap(
      (message, index) =>
        message.messageId && knownMessageIds.has(message.messageId)
          ? []
          : [
              {
                id: message.messageId ?? `${activeRunId}:assistant:${index}`,
                role: 'ai' as const,
                blocks: [{ type: 'text' as const, text: message.text }],
              },
            ],
    )
    if (projection.streamingAssistantText) {
      transient.push({
        id: `${activeRunId}:streaming`,
        role: 'ai',
        blocks: [{ type: 'text', text: projection.streamingAssistantText }],
        pending: true,
        pendingText: 'Meant is still working…',
      })
    }
    return [...authoritative, ...transient]
  }, [activeRunId, combinedConversation, deliveryLocations, eventState.runs])

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
      conversations.map((summary) =>
        threadFromSummary(summary, summary.conversationId === activeConversationId ? messages : []),
      ),
    [activeConversationId, conversations, messages],
  )
  const historyThreads = useMemo(
    () => [...activeThreads, ...archivedConversations.map((summary) => threadFromSummary(summary))],
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

  const submit = useCallback(
    async (text: string) => {
      if (!activeConversationId || submitting) return
      const targetConversationId = activeConversationId
      setSubmitting(true)
      setError(null)
      try {
        if (activeRunId) {
          await cancelAgentRun(activeRunId, { expectedUserId }).catch(() => null)
        }
        if (conversation?.latestSequence === 0 && conversation.title === 'New conversation') {
          const renamed = await updateAgentConversation({
            conversationId: activeConversationId,
            title: derivedTitle(text),
            expectedUserId,
          })
          setConversations((current) =>
            current.map((item) =>
              item.conversationId === renamed.conversationId ? renamed : item,
            ),
          )
        }
        const turn = await submitAgentTurn({
          conversationId: activeConversationId,
          message: text,
          clientTurnId: uniqueRequestId('turn'),
          expectedUserId,
        })
        if (activeConversationIdRef.current === targetConversationId) {
          setConversation((current) =>
            current
              ? {
                  ...current,
                  latestSequence: Math.max(current.latestSequence, turn.userMessage.sequenceNumber),
                  messages: current.messages.some(
                    (message) => message.messageId === turn.userMessage.messageId,
                  )
                    ? current.messages
                    : [...current.messages, turn.userMessage],
                }
              : current,
          )
          setRunSnapshot(null)
          setActiveRunId(turn.runId)
        }
      } catch (caught) {
        setError(caught instanceof Error ? caught.message : 'Could not submit this message.')
      } finally {
        setSubmitting(false)
      }
    },
    [
      activeConversationId,
      activeRunId,
      conversation?.latestSequence,
      conversation?.title,
      expectedUserId,
      submitting,
    ],
  )

  const stop = useCallback(async () => {
    if (!activeRunId) return
    setError(null)
    try {
      const cancelled = await cancelAgentRun(activeRunId, { expectedUserId })
      if (cancelled) setRunSnapshot(cancelled)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not stop this run.')
    }
  }, [activeRunId, expectedUserId])

  const performAction = useCallback(
    async (toolName: string, argumentsValue: unknown, summary: string) => {
      if (!activeConversationId) return null
      const targetConversationId = activeConversationId
      const argumentsJson = JSON.stringify(argumentsValue)
      const pendingKey = `${expectedUserId}:${activeConversationId}:${toolName}:${argumentsJson}`
      if (actionPendingRef.current.has(pendingKey)) return null
      const idempotencyKey = actionIdempotencyRef.current.keyFor(pendingKey, () =>
        uniqueRequestId('action'),
      )
      actionPendingRef.current.add(pendingKey)
      setActionPending((current) => new Set(current).add(pendingKey))
      setError(null)
      try {
        const result = await recordAgentDirectAction({
          conversationId: activeConversationId,
          toolName,
          argumentsJson,
          idempotencyKey,
          summary,
          expectedUserId,
        })
        if (activeConversationIdRef.current === targetConversationId) {
          setConversation((current) => (current ? mergeAction(current, result) : current))
        }
        const cartSnapshotArtifacts = latestCartSnapshotArtifacts(result.artifacts)
        const cartIds = new Set(
          cartSnapshotArtifacts.flatMap((artifact) => (artifact.cartId ? [artifact.cartId] : [])),
        )
        if (cartIds.size > 0) {
          onAgentCartSnapshot?.(
            cartItemsFromAgentArtifacts(cartSnapshotArtifacts, allProducts),
            cartIds,
          )
        }
        actionIdempotencyRef.current.completed(pendingKey)
        void refreshLists()
        return result
      } catch (caught) {
        actionIdempotencyRef.current.failed(pendingKey, caught)
        setError(caught instanceof Error ? caught.message : 'That action could not be completed.')
        return null
      } finally {
        actionPendingRef.current.delete(pendingKey)
        setActionPending((current) => {
          const next = new Set(current)
          next.delete(pendingKey)
          return next
        })
      }
    },
    [activeConversationId, allProducts, expectedUserId, onAgentCartSnapshot, refreshLists],
  )

  const exactOfferKey = (product: Product): string | null =>
    product.canonicalProduct?.recommendedOfferKey ??
    product.canonicalProduct?.offers[0]?.key ??
    product.offers.find((offer) => offer.offerKey)?.offerKey ??
    null

  const togglePin = (product: Product) => {
    const pinned = interactionState.pinned.has(product.id)
    void performAction(
      pinned ? 'unpin_product' : 'pin_product',
      { canonicalProductKey: product.id, offerKey: exactOfferKey(product) ?? undefined },
      `${pinned ? 'Unpinned' : 'Pinned'} ${product.name}`,
    )
  }
  const toggleWatch = (product: Product) => {
    const watched = interactionState.watched.has(product.id)
    void performAction(
      watched ? 'unwatch_product' : 'watch_product',
      { canonicalProductKey: product.id, offerKey: exactOfferKey(product) ?? undefined },
      `${watched ? 'Stopped watching' : 'Watching'} ${product.name}`,
    )
  }
  const addToCart = (product: Product) => {
    const offerKey = exactOfferKey(product)
    if (!offerKey) {
      setError('This product does not have an exact purchasable offer yet.')
      return
    }
    void performAction(
      'prepare_carts',
      { offers: [{ offerKey, quantity: 1 }] },
      `Added ${product.name} to cart`,
    )
  }
  const dig = (kind: 'reviews' | 'code' | 'similar', product: Product) => {
    const key = product.id
    const offerKey = exactOfferKey(product) ?? undefined
    if (kind === 'reviews') {
      void performAction(
        'get_product_reviews',
        { canonicalProductKey: key, selectedOfferKey: offerKey },
        `Loaded reviews for ${product.name}`,
      )
    } else if (kind === 'code') {
      void performAction(
        'find_discount_codes',
        { canonicalProductKey: key, selectedOfferKey: offerKey, quantity: 1 },
        `Searched discount codes for ${product.name}`,
      )
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

  const latestArtifactCartLine = (id: ProductId, merchant: string, identity?: string) =>
    visibleCart.find((line) =>
      identity
        ? cartItemIdentity(line) === identity
        : line.id === id && (line.merchant === merchant || visibleCart.length === 1),
    )
  const updateCartQuantity = (
    _messageId: string,
    _blockIndex: number,
    id: ProductId,
    merchant: string,
    quantity: number,
    _nextCart: readonly CartItem[],
    identity?: string,
  ) => {
    const line = latestArtifactCartLine(id, merchant, identity)
    if (!line?.cartId || !line.cartLineId) return
    void performAction(
      quantity <= 0 ? 'remove_cart_line' : 'update_cart_line',
      quantity <= 0
        ? { cartId: line.cartId, cartLineId: line.cartLineId }
        : { cartId: line.cartId, cartLineId: line.cartLineId, quantity },
      quantity <= 0 ? `Removed ${line.productTitle ?? id} from cart` : 'Updated cart quantity',
    )
  }
  const removeCartLine = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
    identity?: string,
  ) => updateCartQuantity(messageId, blockIndex, id, merchant, 0, nextCart, identity)
  const prepareCheckout = () => {
    const cartIds = [...new Set(visibleCart.flatMap((line) => (line.cartId ? [line.cartId] : [])))]
    if (cartIds.length === 0) {
      setError('Your merchant cart must be ready before checkout can start.')
      return
    }
    void performAction('prepare_checkout', { cartIds }, 'Prepared checkout')
  }

  const newConversation = useCallback(async () => {
    try {
      const created = await createAgentConversation({ expectedUserId })
      setConversations((current) => [created, ...current])
      setActiveConversationId(created.conversationId)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not create a conversation.')
    }
  }, [expectedUserId])

  useEffect(() => {
    if (handledHomeRequestRef.current === homeRequestId) return
    handledHomeRequestRef.current = homeRequestId
    void newConversation()
  }, [homeRequestId, newConversation])

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
    setActiveConversationId(conversationId)
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
        if (remaining[0]) setActiveConversationId(remaining[0].conversationId)
        else await newConversation()
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not archive this conversation.')
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

  const currentStatus = eventState.runs[activeRunId ?? '']?.status ?? runSnapshot?.status
  const isRunning = Boolean(activeRunId && !TERMINAL_RUNS.has(currentStatus ?? 'QUEUED'))
  const checkoutHostMessageId =
    [...messages]
      .reverse()
      .find((message) => message.blocks?.some((block) => block.type === 'checkout'))?.id ?? null

  return (
    <main className="mt-feed mt-ct-feed">
      {activeThreads.length > 0 && activeConversationId ? (
        <DiscoverThreadTabs
          threads={activeThreads}
          activeId={activeConversationId}
          onSelect={(id) => void selectConversation(id)}
          onDelete={(id) => void archiveConversation(id)}
          onNew={() => void newConversation()}
          onRename={(id, title) => void renameConversation(id, title)}
          onShare={() => {
            void navigator.clipboard?.writeText(window.location.href)
            setShareNotice('Link copied. This private conversation still requires your account.')
          }}
          onReorder={(fromIndex, toIndex) =>
            setConversations((current) => {
              const next = [...current]
              const [moved] = next.splice(fromIndex, 1)
              if (moved) next.splice(toIndex, 0, moved)
              return next
            })
          }
          onDeleteHistory={(id) => void archiveConversation(id)}
          historyThreads={historyThreads}
        />
      ) : null}
      {error || shareNotice ? (
        <div className="mt-ct-history-error" role={error ? 'alert' : 'status'}>
          <span>{error ?? shareNotice}</span>
          <button
            type="button"
            onClick={() => {
              setError(null)
              setShareNotice(null)
            }}
          >
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
              {greeting}, {profile.name}. I can search, compare, inspect what you own, build carts,
              and prepare merchant checkout while keeping every step in this conversation.
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
            watchedSet={interactionState.watched}
            shelfMessageSet={shelfMessageSet}
            shelfProductSet={shelfProductSet}
            flash={shelfFlashMessageId === message.id}
            celebrateArrival={false}
            immutable
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
            activeCheckout={message.id === checkoutHostMessageId ? activeCheckout : null}
            checkoutBusy={checkoutBusy}
            checkoutError={checkoutError}
            onCheckoutAssistant={onCheckoutAssistant}
            onRefreshCheckout={onRefreshCheckout}
            onCheckoutHere={prepareCheckout}
            newsletter={newsletter}
            newsletterPending={false}
            onNewsletterSignup={() => void onNewsletterChange(true)}
            onDelete={() => undefined}
            onShelfAddMessage={(item) => addMessageToShelf(item)}
            onShelfAddProduct={(product) => addProductToShelf(product)}
            onDragMessage={dragMessage}
            onDragProduct={dragProduct}
            onRetryProductResultSet={() => undefined}
          />
        ))}
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

      {interactionState.pinned.size > 0 ? (
        <div className="mt-ct-tray">
          <span className="mt-mono mt-ct-tray-label">Compare tray</span>
          <div className="mt-ct-tray-items">
            {products
              .filter((product) => interactionState.pinned.has(product.id))
              .map((product) => (
                <span className="mt-ct-tray-chip" key={product.id}>
                  <span className="mt-ct-tray-thumb">
                    <ProductArtwork product={product} label={product.category.toLowerCase()} />
                  </span>
                  {product.name}
                  <button
                    className="mt-ct-tray-x"
                    type="button"
                    aria-label={`Unpin ${product.name}`}
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
            disabled={interactionState.pinned.size < 2}
            onClick={() =>
              void compareHere(
                products.filter((product) => interactionState.pinned.has(product.id)),
              )
            }
          >
            Compare here
          </button>
          <button
            className="mt-ct-tray-go"
            type="button"
            disabled={interactionState.pinned.size < 2}
            onClick={() =>
              void compareHere(
                products.filter((product) => interactionState.pinned.has(product.id)),
                true,
              )
            }
          >
            Full compare
          </button>
        </div>
      ) : null}

      <div className="mt-ct-dock">
        <div className="mt-ct-dock-inner">
          {isRunning ? (
            <button className="mt-ct-cobtn" type="button" onClick={() => void stop()}>
              Stop
            </button>
          ) : null}
          <AskComposer
            placeholder="Ask Meant to search, compare, inspect inventory, or build your cart…"
            suggestions={[]}
            showChips={false}
            onAsk={(text) => void submit(text)}
            disabled={loading || submitting || !activeConversationId}
          />
        </div>
      </div>
    </main>
  )
}
