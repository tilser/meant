import {
  type CSSProperties,
  type DragEvent as ReactDragEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  useEffect,
  useRef,
  useState,
} from 'react'

import { BookmarkIcon, ChevronIcon, CollapseIcon, OpenIcon, SearchIcon } from '../shared/icons'
import { merchantAdjacentDisplayLabel } from '../cart/merchantOrigin'
import { CloseIcon, MeantHeartMark } from '../shared/ui'
import { useStoredState } from '../shared/storage'
import { productImageUrl } from '../product/productSnapshots'
import type { Product, ProductId } from '../types'
import { money } from '../utils'
import { isLegacyCartShelfSnapshot, shelfBuyerFacingLabel } from './snapshots'
import type {
  ShelfCartSnapshot,
  ShelfDragPayload,
  ShelfItem,
  ShelfMessageSnapshot,
  ShelfThumb,
} from './types'
import { SHELF_DRAG_MIME } from './types'

const SHELF_MIN_WIDTH = 300
const SHELF_MAX_WIDTH = 720

type DustingContainerComponent = (props: {
  children: ReactNode
  dusting: boolean
  className?: string
  onGone: () => void
}) => ReactNode

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function ShelfThumbs({ thumbs }: Readonly<{ thumbs: readonly ShelfThumb[] }>) {
  if (thumbs.length === 0) {
    return null
  }
  return (
    <div className="mt-shelf-thumbs">
      {thumbs.map((thumb) => (
        <span
          key={`${thumb.name}-${thumb.tone}`}
          className="mt-shelf-thumb"
          title={thumb.name}
          style={{ background: thumb.tone }}
        >
          {thumb.imageUrl ? <img src={thumb.imageUrl} alt="" loading="lazy" /> : null}
        </span>
      ))}
    </div>
  )
}

function shelfCartMeta(itemCount: number, merchantCount: number): string {
  const itemLabel = `${itemCount} item${itemCount === 1 ? '' : 's'}`
  const merchantLabel = `${merchantCount} store${merchantCount === 1 ? '' : 's'}`
  return merchantCount > 0 ? `${itemLabel} · ${merchantLabel}` : itemLabel
}

function ShelfCartPreview({ cart }: Readonly<{ cart: ShelfCartSnapshot }>) {
  const visibleLines = cart.lines.slice(0, 6)
  const hiddenLineCount = Math.max(0, cart.lines.length - visibleLines.length)

  return (
    <div className="mt-shelf-cart-preview" aria-label="Cart saved from chat">
      <div className="mt-shelf-cart-brand">
        <span className="mt-shelf-cart-mark" aria-hidden>
          <MeantHeartMark size={18} />
        </span>
        <span className="mt-shelf-cart-brand-copy">
          <strong>
            Your <em>Meant</em> finds
          </strong>
          <span>{shelfCartMeta(cart.itemCount, cart.merchantCount)}</span>
        </span>
        {cart.total !== null && cart.priceCurrency ? (
          <span className="mt-shelf-cart-total">
            <small>Total</small>
            <strong>{money(cart.total, cart.priceCurrency)}</strong>
          </span>
        ) : null}
      </div>
      <div className="mt-shelf-cart-lines">
        {visibleLines.map((line, index) => (
          <div className="mt-shelf-cart-line" key={`${line.name}-${line.merchant}-${index}`}>
            <span className="mt-shelf-cart-media" style={{ background: line.tone }}>
              {line.imageUrl ? <img src={line.imageUrl} alt="" loading="lazy" /> : null}
            </span>
            <span className="mt-shelf-cart-line-copy">
              <strong>{line.name}</strong>
              <span className="mt-mono">
                {line.merchant}
                {line.delivery ? ` · ${line.delivery}` : ''}
              </span>
            </span>
            <span className="mt-shelf-cart-line-facts">
              {line.lineTotal !== null && line.priceCurrency ? (
                <strong>{money(line.lineTotal, line.priceCurrency)}</strong>
              ) : null}
              <span>Qty {line.quantity}</span>
            </span>
          </div>
        ))}
        {hiddenLineCount > 0 ? (
          <div className="mt-shelf-cart-more">
            +{hiddenLineCount} more cart {hiddenLineCount === 1 ? 'line' : 'lines'}
          </div>
        ) : null}
      </div>
    </div>
  )
}

function ShelfLegacyCartPreview({
  snapshot,
}: Readonly<{
  snapshot: ShelfMessageSnapshot
}>) {
  const safeThumbs = snapshot.thumbs.map((thumb) => ({
    ...thumb,
    name: shelfBuyerFacingLabel(thumb.name) ?? 'Cart item',
  }))
  const visibleNames = safeThumbs.slice(0, 3).map((thumb) => thumb.name)
  const hiddenNameCount = Math.max(0, safeThumbs.length - visibleNames.length)

  return (
    <div className="mt-shelf-cart-preview legacy" aria-label="Cart saved from chat">
      <div className="mt-shelf-cart-brand">
        <span className="mt-shelf-cart-mark" aria-hidden>
          <MeantHeartMark size={18} />
        </span>
        <span className="mt-shelf-cart-brand-copy">
          <strong>
            Your <em>Meant</em> finds
          </strong>
          <span>Saved from chat</span>
        </span>
      </div>
      <ShelfThumbs thumbs={safeThumbs} />
      {visibleNames.length > 0 ? (
        <p className="mt-shelf-cart-legacy-names">
          {visibleNames.join(' · ')}
          {hiddenNameCount > 0 ? ` · +${hiddenNameCount} more` : ''}
        </p>
      ) : (
        <p className="mt-shelf-cart-legacy-names">Open the chat to see the saved cart.</p>
      )}
    </div>
  )
}

function parseShelfDragPayload(dataTransfer: DataTransfer): ShelfDragPayload | null {
  const raw = dataTransfer.getData(SHELF_DRAG_MIME)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<ShelfDragPayload> | null
    if (
      parsed &&
      typeof parsed === 'object' &&
      (parsed.kind === 'message' || parsed.kind === 'product')
    ) {
      return parsed as ShelfDragPayload
    }
  } catch {
    return null
  }
  return null
}

function ShelfCard({
  item,
  product,
  DustingContainer,
  onRemove,
  onToggleCollapse,
  onFind,
  onFindProduct,
  onOpenProduct,
}: Readonly<{
  item: ShelfItem
  product?: Product | null
  DustingContainer: DustingContainerComponent
  onRemove: (uid: string) => void
  onToggleCollapse: (uid: string) => void
  onFind: (conversationId: string | undefined, messageId: string) => void
  onFindProduct: (
    conversationId: string | undefined,
    productId: ProductId,
    messageId: string | undefined,
  ) => void
  onOpenProduct: (product: Product) => void
}>) {
  const [dusting, setDusting] = useState(false)
  const tools = (
    <div className="mt-shelf-card-tools">
      <button
        type="button"
        onClick={() => onToggleCollapse(item.uid)}
        title={item.collapsed ? 'Expand' : 'Minimize'}
        aria-label={item.collapsed ? 'Expand shelf item' : 'Minimize shelf item'}
      >
        <CollapseIcon collapsed={item.collapsed} />
      </button>
      <button
        type="button"
        onClick={() => setDusting(true)}
        title="Remove from shelf"
        aria-label="Remove from shelf"
      >
        <CloseIcon size={11} />
      </button>
    </div>
  )

  if (item.kind === 'product') {
    const snapshot = item.snapshot
    const title = product?.name ?? snapshot.name
    const authoritative = product != null && product.commercialFactsAuthoritative !== false
    const thumbUrl =
      (product ? productImageUrl(product) : null) ?? snapshot.imageUrl?.trim() ?? null
    const tone = product?.tone ?? snapshot.tone
    const price = authoritative ? product.priceFrom : snapshot.priceFrom
    const priceCurrency = authoritative ? product.priceCurrency : snapshot.priceCurrency
    const merchants = authoritative ? product.merchants : snapshot.merchants
    const merchantSummary =
      authoritative || merchants > 0
        ? `from ${merchants} ${merchants === 1 ? 'store' : 'stores'}`
        : 'current store count unavailable'
    return (
      <DustingContainer dusting={dusting} onGone={() => onRemove(item.uid)}>
        <div className="mt-shelf-card product">
          <div className="mt-shelf-card-head">
            <span className="mt-shelf-kind mt-mono">Product</span>
            {tools}
          </div>
          {item.collapsed ? (
            <button
              className="mt-shelf-collapsed"
              type="button"
              onClick={() => onToggleCollapse(item.uid)}
            >
              <span className="mt-shelf-thumb" style={{ background: tone }}>
                {thumbUrl ? <img src={thumbUrl} alt="" loading="lazy" /> : null}
              </span>
              <span className="mt-shelf-collapsed-name">{title}</span>
            </button>
          ) : (
            <>
              <button
                className="mt-shelf-prod"
                type="button"
                onClick={() => {
                  if (product) {
                    onOpenProduct(product)
                  }
                }}
                disabled={!product}
              >
                <span className="mt-shelf-prod-thumb" style={{ background: tone }}>
                  {thumbUrl ? <img src={thumbUrl} alt="" loading="lazy" /> : null}
                </span>
                <span className="mt-shelf-prod-info">
                  <span className="mt-mono mt-shelf-prod-brand">
                    {merchantAdjacentDisplayLabel(product?.brand ?? snapshot.brand)}
                  </span>
                  <span className="mt-shelf-prod-name">{title}</span>
                  <span className="mt-shelf-prod-price">
                    {money(price, priceCurrency)}
                    <span className="mt-shelf-prod-from">
                      {' '}
                      {merchantSummary}
                      {authoritative ? null : ' · Last checked'}
                    </span>
                  </span>
                </span>
              </button>
              <div className="mt-shelf-actions">
                {product ? (
                  <button
                    className="mt-shelf-find"
                    type="button"
                    onClick={() => onOpenProduct(product)}
                  >
                    <OpenIcon /> Open product
                  </button>
                ) : null}
                <button
                  className="mt-shelf-find"
                  type="button"
                  onClick={() => onFindProduct(item.conversationId, item.productId, item.messageId)}
                >
                  <SearchIcon size={12} /> Find in chat
                </button>
              </div>
            </>
          )}
        </div>
      </DustingContainer>
    )
  }

  const snapshot = item.snapshot
  const cartLike = isLegacyCartShelfSnapshot(snapshot)
  const cartMeta = snapshot.cart
    ? shelfCartMeta(snapshot.cart.itemCount, snapshot.cart.merchantCount)
    : 'Saved cart from chat'
  return (
    <DustingContainer dusting={dusting} onGone={() => onRemove(item.uid)}>
      <div className={`mt-shelf-card ${snapshot.side} ${cartLike ? 'cart' : ''}`}>
        <div className="mt-shelf-card-head">
          <span className="mt-shelf-kind mt-mono">
            {cartLike ? 'Cart from chat' : snapshot.title}
          </span>
          {tools}
        </div>
        {item.collapsed ? (
          <button
            className="mt-shelf-collapsed"
            type="button"
            onClick={() => onToggleCollapse(item.uid)}
          >
            {cartLike ? (
              <span className="mt-shelf-cart-collapsed-mark" aria-hidden>
                <MeantHeartMark size={16} />
              </span>
            ) : snapshot.thumbs[0] ? (
              <span className="mt-shelf-thumb" style={{ background: snapshot.thumbs[0].tone }}>
                {snapshot.thumbs[0].imageUrl ? (
                  <img src={snapshot.thumbs[0].imageUrl} alt="" loading="lazy" />
                ) : null}
              </span>
            ) : null}
            <span className="mt-shelf-collapsed-name">
              {cartLike ? cartMeta : snapshot.text || snapshot.title}
            </span>
          </button>
        ) : (
          <>
            {snapshot.cart ? (
              <ShelfCartPreview cart={snapshot.cart} />
            ) : cartLike ? (
              <ShelfLegacyCartPreview snapshot={snapshot} />
            ) : (
              <>
                {snapshot.text ? <p className="mt-shelf-text">{snapshot.text}</p> : null}
                <ShelfThumbs thumbs={snapshot.thumbs} />
              </>
            )}
          </>
        )}
        <button
          className="mt-shelf-find"
          type="button"
          onClick={() => onFind(item.conversationId, item.messageId)}
        >
          <SearchIcon size={12} /> Find in chat
        </button>
      </div>
    </DustingContainer>
  )
}

export function Shelf({
  open,
  items,
  productsById,
  DustingContainer,
  onToggle,
  onAddMessage,
  onAddProduct,
  onRemove,
  onClear,
  onToggleCollapse,
  onFind,
  onFindProduct,
  onOpenProduct,
}: Readonly<{
  open: boolean
  items: readonly ShelfItem[]
  productsById: ReadonlyMap<ProductId, Product>
  DustingContainer: DustingContainerComponent
  onToggle: () => void
  onAddMessage: (payload: Extract<ShelfDragPayload, { kind: 'message' }>) => void
  onAddProduct: (payload: Extract<ShelfDragPayload, { kind: 'product' }>) => void
  onRemove: (uid: string) => void
  onClear: () => void
  onToggleCollapse: (uid: string) => void
  onFind: (conversationId: string | undefined, messageId: string) => void
  onFindProduct: (
    conversationId: string | undefined,
    productId: ProductId,
    messageId: string | undefined,
  ) => void
  onOpenProduct: (product: Product) => void
}>) {
  const [over, setOver] = useState(false)
  const [clearing, setClearing] = useState(false)
  const [width, setWidth] = useStoredState('meant.shelfW', 340)
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const safeWidth = clampNumber(width, SHELF_MIN_WIDTH, SHELF_MAX_WIDTH)

  useEffect(() => {
    document.documentElement.style.setProperty('--shelf-w', `${safeWidth}px`)
  }, [safeWidth])

  useEffect(() => {
    document.body.classList.toggle('shelf-open', open)
    return () => document.body.classList.remove('shelf-open')
  }, [open])

  useEffect(
    () => () => {
      resizeCleanupRef.current?.()
    },
    [],
  )

  useEffect(() => {
    if (items.length === 0 && clearing) {
      setClearing(false)
    }
  }, [clearing, items.length])

  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) {
      return
    }
    event.preventDefault()
    resizeCleanupRef.current?.()
    document.body.classList.add('mt-shelf-resizing')
    const move = (moveEvent: PointerEvent) => {
      setWidth(clampNumber(window.innerWidth - moveEvent.clientX, SHELF_MIN_WIDTH, SHELF_MAX_WIDTH))
    }
    const cleanup = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', cleanup)
      window.removeEventListener('pointercancel', cleanup)
      document.body.classList.remove('mt-shelf-resizing')
      resizeCleanupRef.current = null
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', cleanup, { once: true })
    window.addEventListener('pointercancel', cleanup, { once: true })
    resizeCleanupRef.current = cleanup
  }

  const drop = (event: ReactDragEvent<HTMLElement>) => {
    event.preventDefault()
    setOver(false)
    document.body.classList.remove('mt-dragging')
    const payload = parseShelfDragPayload(event.dataTransfer)
    if (!payload) {
      return
    }
    if (payload.kind === 'message') {
      onAddMessage(payload)
      return
    }
    onAddProduct(payload)
  }

  return (
    <>
      <button
        className="mt-shelf-tab"
        type="button"
        onClick={onToggle}
        title="Your shelf - messages and products you set aside"
        aria-label={open ? 'Hide shelf' : 'Show shelf'}
        aria-expanded={open}
      >
        <BookmarkIcon filled={items.length > 0} size={15} />
        {items.length > 0 ? (
          <span className="mt-shelf-tab-count mt-mono">{items.length}</span>
        ) : null}
      </button>
      <aside
        className={`mt-shelf ${open ? 'open' : ''} ${over ? 'over' : ''}`}
        style={{ '--shelf-w': `${safeWidth}px` } as CSSProperties}
        onDragOver={(event) => {
          event.preventDefault()
          event.dataTransfer.dropEffect = 'copy'
          setOver(true)
        }}
        onDragLeave={(event) => {
          const nextTarget = event.relatedTarget
          if (!(nextTarget instanceof Node) || !event.currentTarget.contains(nextTarget)) {
            setOver(false)
          }
        }}
        onDrop={drop}
      >
        <div
          className="mt-shelf-resize"
          onPointerDown={startResize}
          title="Drag to resize the shelf"
        >
          <span />
        </div>
        <div className="mt-shelf-head">
          <div className="mt-shelf-title-wrap">
            <span className="mt-shelf-title">Shelf</span>
            <span className="mt-mono mt-shelf-sub">Set aside · spans all chats</span>
          </div>
          <div className="mt-shelf-head-tools">
            {items.length > 0 ? (
              <button
                className="mt-shelf-clear"
                type="button"
                onClick={() => setClearing(true)}
                disabled={clearing}
              >
                Clear
              </button>
            ) : null}
            <button
              className="mt-shelf-close"
              type="button"
              onClick={onToggle}
              title="Hide shelf"
              aria-label="Hide shelf"
            >
              <span className="mt-shelf-close-desktop" aria-hidden>
                <ChevronIcon direction="right" size={14} />
              </span>
              <span className="mt-shelf-close-mobile" aria-hidden>
                <CloseIcon size={14} />
              </span>
            </button>
          </div>
        </div>
        <div className="mt-shelf-body">
          {items.length === 0 ? (
            <div className="mt-shelf-empty">
              <span className="mt-shelf-empty-mark">
                <BookmarkIcon size={22} />
              </span>
              <p className="mt-shelf-empty-title">Nothing set aside yet</p>
              <p className="mt-shelf-empty-sub">
                Drag any message or product over here, or tap the bookmark on hover, to keep it
                handy and jump back later.
              </p>
            </div>
          ) : (
            <DustingContainer
              className="mt-shelf-clear-region"
              dusting={clearing}
              onGone={() => {
                setClearing(false)
                onClear()
              }}
            >
              {items.map((item) => (
                <ShelfCard
                  key={item.uid}
                  item={item}
                  product={item.kind === 'product' ? productsById.get(item.productId) : null}
                  DustingContainer={DustingContainer}
                  onRemove={onRemove}
                  onToggleCollapse={onToggleCollapse}
                  onFind={onFind}
                  onFindProduct={onFindProduct}
                  onOpenProduct={onOpenProduct}
                />
              ))}
              <div className="mt-shelf-dropzone">Drop here to set aside</div>
            </DustingContainer>
          )}
        </div>
      </aside>
    </>
  )
}
