import {
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'

import {
  getAssistantConversation,
  getAssistantConversations,
  streamAssistantMessage,
  type AssistantChatContextInput,
  type UserAssistantConversationProfile,
  type UserAssistantConversationSummaryProfile,
} from '../../../lib/apiClient'
import { cartableOfferForProduct } from '../cart/utils'
import type { AssistantProductAction } from '../chat/types'
import { HistoryIcon, PlusIcon } from '../shared/icons'
import { CloseIcon, SparkMark } from '../shared/ui'
import { useStoredState } from '../shared/storage'
import type { Offer, Preference, Product } from '../types'
import { productFromSearchResult } from '../product/productSearchMapping'
import { AskComposer } from './AskComposer'
import { AskThread } from './AskThread'
import {
  askConversationDateLabel,
  assistantConversationSummary,
  findLastAssistantMessageIndex,
  messagesFromAssistantConversation,
  resolveAssistantProductAction,
  upsertAssistantConversationSummary,
} from './assistantActions'
import type { AskPanelSize, Message } from './types'

const ASK_PANEL_DEFAULT_SIZE: AskPanelSize = { width: 460, height: 620 }
const ASK_PANEL_MIN_WIDTH = 360
const ASK_PANEL_MIN_HEIGHT = 440
const ASK_PANEL_MAX_WIDTH = 720
const ASK_PANEL_MAX_HEIGHT = 760

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function normalizedAskPanelSize(size: AskPanelSize | null | undefined): AskPanelSize {
  const candidate = size && typeof size === 'object' ? (size as Partial<AskPanelSize>) : {}
  const width =
    typeof candidate.width === 'number' && Number.isFinite(candidate.width)
      ? candidate.width
      : ASK_PANEL_DEFAULT_SIZE.width
  const height =
    typeof candidate.height === 'number' && Number.isFinite(candidate.height)
      ? candidate.height
      : ASK_PANEL_DEFAULT_SIZE.height
  return {
    width: Math.round(clampNumber(width, ASK_PANEL_MIN_WIDTH, ASK_PANEL_MAX_WIDTH)),
    height: Math.round(clampNumber(height, ASK_PANEL_MIN_HEIGHT, ASK_PANEL_MAX_HEIGHT)),
  }
}

function askPanelViewportMax(): AskPanelSize {
  return {
    width: Math.min(ASK_PANEL_MAX_WIDTH, Math.max(ASK_PANEL_MIN_WIDTH, window.innerWidth - 40)),
    height: Math.min(
      ASK_PANEL_MAX_HEIGHT,
      Math.max(ASK_PANEL_MIN_HEIGHT, window.innerHeight - 118),
    ),
  }
}

export function FloatingAsk({
  contextLabel,
  context,
  suggestions,
  preferences,
  onProducts,
  onProductOpen,
  onAddProductToCart,
  hidden = false,
}: Readonly<{
  contextLabel: string
  context: AssistantChatContextInput
  suggestions: readonly string[]
  preferences: readonly Preference[]
  onProducts: (products: readonly Product[], sourceQuery: string) => void
  onProductOpen: (product: Product) => void
  onAddProductToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  hidden?: boolean
}>) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [conversationHistory, setConversationHistory] = useState<
    UserAssistantConversationSummaryProfile[]
  >([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [loading, setLoading] = useState(false)
  const [panelSize, setPanelSize] = useStoredState<AskPanelSize>(
    'meant.askPanelSize',
    ASK_PANEL_DEFAULT_SIZE,
  )
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const assistantAbortRef = useRef<AbortController | null>(null)
  const historyAbortRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)
  const safePanelSize = normalizedAskPanelSize(panelSize)
  const panelStyle = {
    '--mt-askpanel-width': `${safePanelSize.width}px`,
    '--mt-askpanel-height': `${safePanelSize.height}px`,
  } as CSSProperties

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      resizeCleanupRef.current?.()
      assistantAbortRef.current?.abort()
      historyAbortRef.current?.abort()
    }
  }, [])

  const restoreConversation = useCallback(
    (conversation: UserAssistantConversationProfile) => {
      setConversationId(conversation.conversationId)
      setMessages(messagesFromAssistantConversation(conversation, preferences))
      const summary = assistantConversationSummary(conversation)
      if (summary) {
        setConversationHistory((current) => upsertAssistantConversationSummary(current, summary))
      }
    },
    [preferences],
  )

  const loadConversationHistory = useCallback(
    (restoreLatest: boolean) => {
      historyAbortRef.current?.abort()
      const controller = new AbortController()
      historyAbortRef.current = controller
      setHistoryLoading(true)

      const run = async () => {
        const history = await getAssistantConversations({ signal: controller.signal })
        if (controller.signal.aborted || !mountedRef.current) {
          return
        }
        setConversationHistory(history)
        setHistoryLoaded(true)

        if (restoreLatest) {
          const latest = history[0]
          if (!latest) {
            setConversationId(null)
            setMessages([])
            return
          }
          const conversation = await getAssistantConversation(latest.conversationId, {
            signal: controller.signal,
          })
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          restoreConversation(conversation)
        }
      }

      run()
        .catch(() => {
          if (mountedRef.current && !controller.signal.aborted) {
            setHistoryLoaded(false)
          }
        })
        .finally(() => {
          if (historyAbortRef.current === controller) {
            historyAbortRef.current = null
          }
          if (mountedRef.current && !controller.signal.aborted) {
            setHistoryLoading(false)
          }
        })
    },
    [restoreConversation],
  )

  const loadConversation = useCallback(
    (nextConversationId: string) => {
      historyAbortRef.current?.abort()
      const controller = new AbortController()
      historyAbortRef.current = controller
      setHistoryLoading(true)

      getAssistantConversation(nextConversationId, { signal: controller.signal })
        .then((conversation) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          restoreConversation(conversation)
        })
        .catch(() => undefined)
        .finally(() => {
          if (historyAbortRef.current === controller) {
            historyAbortRef.current = null
          }
          if (mountedRef.current && !controller.signal.aborted) {
            setHistoryLoading(false)
          }
        })
    },
    [restoreConversation],
  )

  useEffect(() => {
    if (open && !historyLoaded) {
      loadConversationHistory(false)
    }
  }, [historyLoaded, loadConversationHistory, open])

  const startPanelResize = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0) {
      return
    }
    event.preventDefault()
    resizeCleanupRef.current?.()

    const startX = event.clientX
    const startY = event.clientY
    const startSize = normalizedAskPanelSize(panelSize)
    const previousUserSelect = document.body.style.userSelect
    document.body.style.userSelect = 'none'

    let cleanup = () => {}
    const onMove = (moveEvent: PointerEvent) => {
      const maxSize = askPanelViewportMax()
      setPanelSize({
        width: clampNumber(
          startSize.width + startX - moveEvent.clientX,
          ASK_PANEL_MIN_WIDTH,
          maxSize.width,
        ),
        height: clampNumber(
          startSize.height + startY - moveEvent.clientY,
          ASK_PANEL_MIN_HEIGHT,
          maxSize.height,
        ),
      })
    }
    cleanup = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', cleanup)
      window.removeEventListener('pointercancel', cleanup)
      document.body.style.userSelect = previousUserSelect
      resizeCleanupRef.current = null
    }

    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', cleanup, { once: true })
    window.addEventListener('pointercancel', cleanup, { once: true })
    resizeCleanupRef.current = cleanup
  }

  const updateStreamingMessage = (update: (message: Message) => Message) => {
    setMessages((current) => {
      const next = [...current]
      const index = findLastAssistantMessageIndex(next)
      if (index < 0) {
        return current
      }
      next[index] = update(next[index])
      return next
    })
  }

  const selectConversation = (nextConversationId: string) => {
    if (loading || historyLoading || nextConversationId === conversationId) {
      return
    }
    setHistoryOpen(false)
    loadConversation(nextConversationId)
  }

  const startNewConversation = () => {
    if (loading) {
      return
    }
    setConversationId(null)
    setMessages([])
    setHistoryOpen(false)
  }

  const executeProductAction = (question: string, action: AssistantProductAction) => {
    assistantAbortRef.current?.abort()
    const controller = new AbortController()
    assistantAbortRef.current = controller
    setLoading(true)
    setHistoryOpen(false)
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: '', pending: true },
    ])

    const run = async () => {
      const offer = action.shouldAddToCart ? cartableOfferForProduct(action.product) : null
      let added = false
      let addAttempted = false

      if (action.shouldAddToCart && offer) {
        addAttempted = true
        added = await onAddProductToCart(action.product, offer)
      }

      if (controller.signal.aborted || !mountedRef.current) {
        return
      }

      if (action.shouldOpen) {
        onProductOpen(action.product)
      }

      const text = (() => {
        if (action.shouldAddToCart && !offer) {
          return action.shouldOpen
            ? `I opened ${action.product.name}, but this item is not available for merchant checkout.`
            : `${action.product.name} is not available for merchant checkout.`
        }
        if (addAttempted && !added) {
          return action.shouldOpen
            ? `I opened ${action.product.name}, but could not add it to the merchant cart.`
            : `I could not add ${action.product.name} to the merchant cart.`
        }
        if (action.shouldAddToCart && action.shouldOpen) {
          return `I added ${action.product.name} to your cart and opened its details.`
        }
        if (action.shouldAddToCart) {
          return `I added ${action.product.name} to your cart.`
        }
        return `I opened ${action.product.name}.`
      })()

      updateStreamingMessage((message) => ({
        ...message,
        text,
        products: [action.product],
        pending: false,
      }))
    }

    run()
      .catch(() => {
        if (controller.signal.aborted || !mountedRef.current) {
          return
        }
        updateStreamingMessage((message) => ({
          ...message,
          text: `I could not update ${action.product.name} right now. Try again in a moment.`,
          products: [action.product],
          pending: false,
        }))
      })
      .finally(() => {
        if (assistantAbortRef.current === controller) {
          assistantAbortRef.current = null
        }
        if (mountedRef.current) {
          setLoading(false)
        }
      })
  }

  const ask = (question: string) => {
    if (loading) {
      return
    }
    const productAction = resolveAssistantProductAction(question, messages)
    if (productAction) {
      executeProductAction(question, productAction)
      return
    }
    assistantAbortRef.current?.abort()
    const controller = new AbortController()
    assistantAbortRef.current = controller
    let streamedText = ''
    setLoading(true)
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: '', pending: true },
    ])
    streamAssistantMessage(
      {
        conversationId,
        message: question,
        context,
        signal: controller.signal,
      },
      {
        onMetadata: (event) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          setConversationId(event.conversationId)
          if (event.conversationId) {
            const metadataConversationId = event.conversationId
            const normalizedTitle = question.replace(/\s+/g, ' ').trim()
            const title =
              normalizedTitle.length > 80 ? `${normalizedTitle.slice(0, 77)}...` : normalizedTitle
            const timestamp = new Date().toISOString()
            setConversationHistory((current) =>
              upsertAssistantConversationSummary(current, {
                conversationId: metadataConversationId,
                title,
                createdAt: timestamp,
                updatedAt: timestamp,
              }),
            )
          }
        },
        onDelta: (text) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          streamedText += text
          updateStreamingMessage((message) => ({
            ...message,
            text: streamedText,
            pending: true,
          }))
        },
        onDone: (event) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          const products = (event.products ?? []).map((product) =>
            productFromSearchResult(product, preferences),
          )
          updateStreamingMessage((message) => ({
            ...message,
            text: event.text ?? streamedText,
            products,
            pending: false,
          }))
          if (products.length > 0) {
            onProducts(products, question)
          }
          loadConversationHistory(false)
        },
        onError: (message) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          updateStreamingMessage((current) => ({
            ...current,
            text: message,
            pending: false,
          }))
        },
      },
    )
      .catch(() => {
        if (!mountedRef.current) {
          return
        }
        if (controller.signal.aborted) {
          updateStreamingMessage((message) => ({
            ...message,
            text: streamedText || 'Response stopped.',
            pending: false,
          }))
          return
        }
        updateStreamingMessage((message) => ({
          ...message,
          text: 'Ask Meant could not respond right now. Try again in a moment.',
          pending: false,
        }))
      })
      .finally(() => {
        if (assistantAbortRef.current === controller) {
          assistantAbortRef.current = null
        }
        if (mountedRef.current) {
          setLoading(false)
        }
      })
  }

  const closeAsk = () => {
    assistantAbortRef.current?.abort()
    setOpen(false)
  }

  return (
    <div
      className={`mt-fab-wrap ${open ? 'open' : ''} ${loading ? 'busy' : ''} ${hidden ? 'mt-fab-wrap-hidden' : ''}`}
    >
      {open ? (
        <div
          className={`mt-askpanel ${loading ? 'mt-askpanel-thinking' : ''}`}
          role="dialog"
          aria-label="Ask Meant"
          style={panelStyle}
        >
          <button
            className="mt-askpanel-resize"
            type="button"
            aria-label="Resize Ask Meant"
            title="Drag to resize"
            onPointerDown={startPanelResize}
            onDoubleClick={() => setPanelSize({ ...ASK_PANEL_DEFAULT_SIZE })}
          />
          <div className="mt-askpanel-head">
            <div className="mt-askpanel-title">
              <SparkMark size={15} /> Ask Meant
            </div>
            <div className="mt-askpanel-actions">
              <button
                className={`mt-askpanel-icon ${historyOpen ? 'on' : ''}`}
                type="button"
                onClick={() => setHistoryOpen((current) => !current)}
                aria-label="Chat history"
                aria-pressed={historyOpen}
                title="Chat history"
              >
                <HistoryIcon size={15} />
              </button>
              <button
                className="mt-askpanel-icon"
                type="button"
                onClick={startNewConversation}
                aria-label="New chat"
                title="New chat"
                disabled={loading}
              >
                <PlusIcon size={15} />
              </button>
              <button
                className="mt-askpanel-close"
                type="button"
                onClick={closeAsk}
                aria-label="Close"
              >
                <CloseIcon size={14} />
              </button>
            </div>
          </div>
          <div className="mt-mono mt-askpanel-ctx">{contextLabel}</div>
          {historyOpen ? (
            <div className="mt-ask-history" role="listbox" aria-label="Ask Meant chat history">
              {conversationHistory.length === 0 ? (
                <div className="mt-ask-history-empty">
                  {historyLoading ? 'Loading chats...' : 'No chats yet.'}
                </div>
              ) : (
                conversationHistory.map((conversation) => (
                  <button
                    key={conversation.conversationId}
                    className={`mt-ask-history-row ${conversation.conversationId === conversationId ? 'active' : ''}`}
                    type="button"
                    role="option"
                    aria-selected={conversation.conversationId === conversationId}
                    onClick={() => selectConversation(conversation.conversationId)}
                    disabled={loading || historyLoading}
                  >
                    <span className="mt-ask-history-title">{conversation.title}</span>
                    <span className="mt-mono mt-ask-history-date">
                      {askConversationDateLabel(conversation.updatedAt)}
                    </span>
                  </button>
                ))
              )}
            </div>
          ) : null}
          {messages.length === 0 ? (
            <p className="mt-askpanel-hint">Ask anything. I already know your preferences.</p>
          ) : null}
          <AskThread messages={messages} onProductOpen={onProductOpen} />
          <AskComposer
            placeholder="Ask Meant..."
            suggestions={suggestions}
            showChips={messages.length === 0}
            onAsk={ask}
            autoFocus
            disabled={loading || historyLoading}
          />
        </div>
      ) : null}
      <button
        className={`mt-fab ${loading ? 'mt-fab-busy' : ''}`}
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-label={open ? 'Close Ask Meant' : 'Open Ask Meant'}
        aria-expanded={open}
      >
        {open ? (
          <CloseIcon size={18} />
        ) : (
          <>
            <SparkMark size={16} color="#fff" /> <span>Ask Meant</span>
          </>
        )}
      </button>
    </div>
  )
}
