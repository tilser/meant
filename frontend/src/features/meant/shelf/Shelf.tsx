import {
  type DragEvent as ReactDragEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  useEffect,
  useRef,
  useState,
} from 'react'

import { BookmarkIcon, ChevronIcon, CollapseIcon, OpenIcon, SearchIcon } from '../shared/icons'
import { CloseIcon } from '../shared/ui'
import { useStoredState } from '../shared/storage'
import type { Product, ProductId } from '../types'
import { money } from '../utils'
import type { ShelfDragPayload, ShelfItem, ShelfProductSnapshot, ShelfThumb } from './types'
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
  onFind: (messageId: string) => void
  onFindProduct: (productId: ProductId) => void
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
    const thumbUrl = product?.imageUrl ?? snapshot.imageUrl
    const tone = product?.tone ?? snapshot.tone
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
                    {product?.brand ?? snapshot.brand}
                  </span>
                  <span className="mt-shelf-prod-name">{title}</span>
                  <span className="mt-shelf-prod-price">
                    {money(product?.priceFrom ?? snapshot.priceFrom)}
                    <span className="mt-shelf-prod-from">
                      {' '}
                      from {product?.merchants ?? snapshot.merchants} stores
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
                  onClick={() => onFindProduct(item.productId)}
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
  return (
    <DustingContainer dusting={dusting} onGone={() => onRemove(item.uid)}>
      <div className={`mt-shelf-card ${snapshot.side}`}>
        <div className="mt-shelf-card-head">
          <span className="mt-shelf-kind mt-mono">{snapshot.title}</span>
          {tools}
        </div>
        {item.collapsed ? (
          <button
            className="mt-shelf-collapsed"
            type="button"
            onClick={() => onToggleCollapse(item.uid)}
          >
            {snapshot.thumbs[0] ? (
              <span className="mt-shelf-thumb" style={{ background: snapshot.thumbs[0].tone }}>
                {snapshot.thumbs[0].imageUrl ? (
                  <img src={snapshot.thumbs[0].imageUrl} alt="" loading="lazy" />
                ) : null}
              </span>
            ) : null}
            <span className="mt-shelf-collapsed-name">{snapshot.text || snapshot.title}</span>
          </button>
        ) : (
          <>
            {snapshot.text ? <p className="mt-shelf-text">{snapshot.text}</p> : null}
            <ShelfThumbs thumbs={snapshot.thumbs} />
          </>
        )}
        <button className="mt-shelf-find" type="button" onClick={() => onFind(item.messageId)}>
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
  onAddProduct: (snapshot: ShelfProductSnapshot) => void
  onRemove: (uid: string) => void
  onClear: () => void
  onToggleCollapse: (uid: string) => void
  onFind: (messageId: string) => void
  onFindProduct: (productId: ProductId) => void
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
    onAddProduct(payload.snapshot)
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
        style={{ width: `${safeWidth}px` }}
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
              <ChevronIcon direction="right" size={14} />
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
