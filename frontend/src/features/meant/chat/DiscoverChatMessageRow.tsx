import {
  type DragEvent as ReactDragEvent,
  type ReactNode,
  useEffect,
  useId,
  useRef,
  useState,
} from 'react'

import { BookmarkIcon, CopyIcon } from '../shared/icons'
import { CloseIcon, SparkMark } from '../shared/ui'
import type {
  CartItem,
  CheckoutPayload,
  Preference,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import type { ActiveCheckoutSession, CheckoutAssistantHandler } from '../cart/checkoutTypes'
import { DiscoverChatBlockView } from './DiscoverChatBlockView'
import type { DiscoverChatMessage } from './types'
import {
  copyTextToClipboard,
  discoverChatMessageCopyText,
  discoverProductResearchQuery,
} from './utils'

function stableDustSeed(value: string): number {
  return Array.from(value).reduce((seed, character) => seed + character.charCodeAt(0), 0) || 1
}

function DustWrap({
  children,
  side,
  onGone,
  onSetAside,
  onShelfDragStart,
  onCopy,
  saved,
  deletable = true,
}: Readonly<{
  children: ReactNode
  side: 'you' | 'meant'
  onGone: () => void
  onSetAside?: (sourceElement: HTMLElement) => void
  onShelfDragStart?: (event: ReactDragEvent<HTMLElement>) => void
  onCopy?: () => void
  saved: boolean
  deletable?: boolean
}>) {
  const [dusting, setDusting] = useState(false)
  const [copied, setCopied] = useState(false)
  const uniqueId = useId().replace(/[^a-zA-Z0-9_-]/g, '')
  const idRef = useRef<string>(`mtdust-${uniqueId}`)
  const seedRef = useRef<number>(stableDustSeed(uniqueId))
  const copiedTimerRef = useRef<number | null>(null)
  const displacementRef = useRef<SVGFEDisplacementMapElement | null>(null)
  const blurRef = useRef<SVGFEGaussianBlurElement | null>(null)

  useEffect(() => {
    if (!dusting) {
      return undefined
    }
    let frame = 0
    let goneTimer: number | null = null
    let startedAt = 0
    const duration = 1050
    const step = (time: number) => {
      if (!startedAt) {
        startedAt = time
      }
      const progress = Math.min(1, (time - startedAt) / duration)
      const eased = progress * progress
      displacementRef.current?.setAttribute('scale', (eased * 140).toFixed(1))
      blurRef.current?.setAttribute('stdDeviation', (eased * 1.8).toFixed(2))
      if (progress < 1) {
        frame = window.requestAnimationFrame(step)
        return
      }
      goneTimer = window.setTimeout(onGone, 20)
    }
    frame = window.requestAnimationFrame(step)
    return () => {
      window.cancelAnimationFrame(frame)
      if (goneTimer !== null) {
        window.clearTimeout(goneTimer)
      }
    }
  }, [dusting, onGone])

  useEffect(
    () => () => {
      if (copiedTimerRef.current !== null) {
        window.clearTimeout(copiedTimerRef.current)
      }
    },
    [],
  )

  const copy = () => {
    onCopy?.()
    setCopied(true)
    if (copiedTimerRef.current !== null) {
      window.clearTimeout(copiedTimerRef.current)
    }
    copiedTimerRef.current = window.setTimeout(() => setCopied(false), 1400)
  }

  return (
    <div className={`mt-dustwrap side-${side} ${dusting ? 'dusting' : ''}`}>
      <div
        className="mt-dust-inner"
        style={dusting ? { filter: `url(#${idRef.current})` } : undefined}
      >
        {children}
      </div>
      {!dusting ? (
        <div className={`mt-msg-tools side-${side}`}>
          {onSetAside ? (
            <button
              className={`mt-msg-tool mt-tool-shelf ${saved ? 'on' : ''} ${
                onShelfDragStart ? '' : 'click-only'
              }`}
              type="button"
              draggable={Boolean(onShelfDragStart)}
              onClick={(event) => onSetAside(event.currentTarget)}
              onDragStart={onShelfDragStart}
              onDragEnd={() => document.body.classList.remove('mt-dragging')}
              aria-label={saved ? 'On your shelf' : 'Set aside on shelf'}
              title={
                saved
                  ? 'On your shelf'
                  : onShelfDragStart
                    ? 'Click or drag to set aside'
                    : 'Click to set aside'
              }
            >
              <BookmarkIcon filled={saved} size={12} />
            </button>
          ) : null}
          {onCopy ? (
            <button
              className={`mt-msg-tool mt-tool-copy ${copied ? 'done' : ''}`}
              type="button"
              draggable={false}
              onClick={copy}
              onDragStart={(event) => event.stopPropagation()}
              aria-label={copied ? 'Copied message' : 'Copy message'}
              title={copied ? 'Copied' : 'Copy message'}
            >
              <CopyIcon size={12} />
            </button>
          ) : null}
          {deletable ? (
            <button
              className="mt-msg-tool mt-tool-del"
              type="button"
              onClick={() => setDusting(true)}
              aria-label="Delete message"
              title="Delete message"
            >
              <CloseIcon size={12} />
            </button>
          ) : null}
        </div>
      ) : null}
      {dusting ? (
        <svg className="mt-dust-svg" aria-hidden="true" width="0" height="0">
          <defs>
            <filter
              id={idRef.current}
              x="-40%"
              y="-40%"
              width="180%"
              height="180%"
              colorInterpolationFilters="sRGB"
            >
              <feTurbulence
                type="fractalNoise"
                baseFrequency="0.7"
                numOctaves="2"
                seed={seedRef.current}
                result="n"
              />
              <feDisplacementMap
                ref={displacementRef}
                in="SourceGraphic"
                in2="n"
                scale="0"
                xChannelSelector="R"
                yChannelSelector="G"
                result="d"
              />
              <feGaussianBlur ref={blurRef} in="d" stdDeviation="0" />
            </filter>
          </defs>
        </svg>
      ) : null}
    </div>
  )
}

function ProductContextChip({ product }: Readonly<{ product: Product }>) {
  return (
    <span className="mt-ct-attach-chip product">
      <span className="mt-ct-attach-thumb solid" style={{ background: product.tone }}>
        {product.imageUrl ? (
          <img className="mt-product-img" src={product.imageUrl} alt="" loading="lazy" />
        ) : null}
      </span>
      <span className="mt-ct-attach-label">Re: {product.name}</span>
    </span>
  )
}

export function DiscoverChatMessageRow({
  threadId,
  message,
  deliveryLocations,
  preferences,
  cart,
  cartProducts,
  savedSet,
  savePendingSet,
  pinnedSet,
  watchedSet,
  shelfMessageSet,
  shelfProductSet,
  flash,
  celebrateArrival,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onJustPick,
  onCompareHere,
  onOpenFullCompare,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onReviewCartHere,
  onRestoreCartLine,
  onCartQty,
  onCartRemove,
  onCheckout,
  activeCheckout,
  checkoutBusy,
  checkoutError,
  onCheckoutAssistant,
  onRefreshCheckout,
  onCheckoutHere,
  newsletter,
  newsletterPending,
  onNewsletterSignup,
  onDelete,
  onShelfAddMessage,
  onShelfAddProduct,
  onDragMessage,
  onDragProduct,
  onRetryProductResultSet,
  immutable = false,
}: Readonly<{
  threadId: string
  message: DiscoverChatMessage
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfMessageSet: ReadonlySet<string>
  shelfProductSet: ReadonlySet<ProductId>
  flash: boolean
  celebrateArrival: boolean
  onOpen: (product: Product, products?: readonly Product[], researchQuery?: string | null) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (
    kind: 'reviews' | 'code' | 'similar',
    product: Product,
    query?: string,
    qualificationId?: string,
  ) => void
  onJustPick: (products: readonly Product[]) => void
  onCompareHere: (products: readonly Product[]) => void
  onOpenFullCompare: (products: readonly Product[]) => void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onReviewCartHere: (lines?: readonly CartItem[], products?: readonly Product[]) => void
  onRestoreCartLine: (product: Product, merchant: string, price?: number, offerKey?: string) => void
  onCartQty: (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    qty: number,
    nextCart: readonly CartItem[],
    identity?: string,
  ) => void
  onCartRemove: (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
    identity?: string,
  ) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  activeCheckout: ActiveCheckoutSession | null
  checkoutBusy: boolean
  checkoutError: string | null
  onCheckoutAssistant: CheckoutAssistantHandler
  onRefreshCheckout: () => Promise<void> | void
  onCheckoutHere: () => void
  newsletter: boolean
  newsletterPending: boolean
  onNewsletterSignup: () => void
  onDelete: (messageId: string) => void
  onShelfAddMessage: (message: DiscoverChatMessage, sourceElement: HTMLElement) => void
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragMessage: (event: ReactDragEvent<HTMLElement>, message: DiscoverChatMessage) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
  onRetryProductResultSet: (threadId: string, resultSetId: string) => void
  immutable?: boolean
}>) {
  const onShelf = shelfMessageSet.has(message.id)
  const copyMessage = () => copyTextToClipboard(discoverChatMessageCopyText(message))
  const containsCheckoutBlock = message.blocks?.some((block) => block.type === 'checkout') ?? false
  const messageDraggable = !containsCheckoutBlock

  if (message.role === 'you') {
    return (
      <div
        className={`mt-ct-msg mt-ct-you ${flash ? 'flash' : ''}`}
        data-mid={message.id}
        draggable
        onDragStart={(event) => onDragMessage(event, message)}
        onDragEnd={() => document.body.classList.remove('mt-dragging')}
      >
        <DustWrap
          side="you"
          onGone={() => onDelete(message.id)}
          onSetAside={(sourceElement) => onShelfAddMessage(message, sourceElement)}
          onShelfDragStart={(event) => onDragMessage(event, message)}
          onCopy={copyMessage}
          saved={onShelf}
          deletable={!immutable}
        >
          <div className="mt-ct-you-bubble">
            {message.productContext ? (
              <ProductContextChip product={message.productContext} />
            ) : null}
            {message.text ? <span className="mt-ct-you-text">{message.text}</span> : null}
          </div>
        </DustWrap>
      </div>
    )
  }

  return (
    <div
      className={`mt-ct-msg mt-ct-meant ${flash ? 'flash' : ''} ${
        celebrateArrival ? 'mt-ct-arrival' : ''
      } ${messageDraggable ? '' : 'no-drag'}`}
      data-mid={message.id}
      draggable={messageDraggable}
      onDragStart={messageDraggable ? (event) => onDragMessage(event, message) : undefined}
      onDragEnd={messageDraggable ? () => document.body.classList.remove('mt-dragging') : undefined}
    >
      <DustWrap
        side="meant"
        onGone={() => onDelete(message.id)}
        onSetAside={(sourceElement) => onShelfAddMessage(message, sourceElement)}
        onShelfDragStart={messageDraggable ? (event) => onDragMessage(event, message) : undefined}
        onCopy={copyMessage}
        saved={onShelf}
        deletable={!immutable}
      >
        <div className="mt-ct-meant-inner">
          <span className="mt-ct-av">
            <SparkMark size={13} />
          </span>
          <div className="mt-ct-meant-body">
            {message.blocks?.map((block, index) => (
              <DiscoverChatBlockView
                key={`${message.id}-${index}`}
                threadId={threadId}
                block={block}
                researchQuery={discoverProductResearchQuery(block, message.query)}
                deliveryLocations={deliveryLocations}
                preferences={preferences}
                cart={cart}
                cartProducts={cartProducts}
                savedSet={savedSet}
                savePendingSet={savePendingSet}
                pinnedSet={pinnedSet}
                watchedSet={watchedSet}
                shelfProductSet={shelfProductSet}
                onOpen={onOpen}
                onToggleSave={onToggleSave}
                onAddCart={onAddCart}
                onPin={onPin}
                onWatch={onWatch}
                onDig={onDig}
                onJustPick={onJustPick}
                onCompareHere={onCompareHere}
                onOpenFullCompare={onOpenFullCompare}
                onOpenSaved={onOpenSaved}
                onOpenOrders={onOpenOrders}
                onOpenPrefs={onOpenPrefs}
                onOpenCart={onOpenCart}
                onReviewCartHere={onReviewCartHere}
                onRestoreCartLine={onRestoreCartLine}
                onCartQty={(id, merchant, qty, nextCart, identity) =>
                  onCartQty(message.id, index, id, merchant, qty, nextCart, identity)
                }
                onCartRemove={(id, merchant, nextCart, identity) =>
                  onCartRemove(message.id, index, id, merchant, nextCart, identity)
                }
                onCheckout={onCheckout}
                activeCheckout={activeCheckout}
                checkoutBusy={checkoutBusy}
                checkoutError={checkoutError}
                onCheckoutAssistant={onCheckoutAssistant}
                onRefreshCheckout={onRefreshCheckout}
                onCheckoutHere={onCheckoutHere}
                newsletter={newsletter}
                newsletterPending={newsletterPending}
                onNewsletterSignup={onNewsletterSignup}
                onShelfAddProduct={onShelfAddProduct}
                onDragProduct={onDragProduct}
                onRetryProductResultSet={onRetryProductResultSet}
                immutable={immutable}
              />
            ))}
            {message.pending ? (
              <div className="mt-ct-system">
                <span className="mt-scan-pulse" />
                {message.pendingText ?? 'Meant is checking merchants and ranking matches.'}
              </div>
            ) : null}
          </div>
        </div>
      </DustWrap>
    </div>
  )
}
