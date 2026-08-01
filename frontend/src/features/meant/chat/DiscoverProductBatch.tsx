import { type DragEvent as ReactDragEvent, useEffect, useRef, useState } from 'react'

import { ProductCard } from '../product/ProductCard'
import { BookmarkIcon, ChevronIcon } from '../shared/icons'
import { CartIcon } from '../shared/ui'
import { useMediaQuery } from '../shared/useMediaQuery'
import type { Preference, Product, ProductId, UserLocation } from '../types'
import { productCarouselItemAccessibility } from './productCarouselAccessibility'
import type { VisibleProductContextChange } from './types'
import { DESKTOP_PRODUCT_PAGE_SIZE, visibleProductContextForBatch } from './visibleProductContext'

function DiscoverChatProduct({
  product,
  index,
  carouselAccessibility,
  query,
  qualificationId,
  deliveryLocations,
  preferences,
  savedSet,
  savePendingSet,
  pinned,
  watched,
  shelfed,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onShelfAdd,
  onDragProduct,
  agentActionsDisabled,
}: Readonly<{
  product: Product
  index: number
  carouselAccessibility: ReturnType<typeof productCarouselItemAccessibility>
  query?: string
  qualificationId?: string
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinned: boolean
  watched: boolean
  shelfed: boolean
  onOpen: (product: Product) => void
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
  onShelfAdd: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
  agentActionsDisabled: boolean
}>) {
  return (
    <div
      className="mt-ct-prod"
      {...carouselAccessibility}
      draggable
      onDragStart={(event) => onDragProduct(event, product)}
      onDragEnd={() => document.body.classList.remove('mt-dragging')}
    >
      <button
        className={`mt-ct-prod-shelf ${shelfed ? 'on' : ''}`}
        type="button"
        onClick={(event) => {
          event.stopPropagation()
          onShelfAdd(product, event.currentTarget)
        }}
        aria-label={shelfed ? 'On your shelf' : 'Set aside on shelf'}
        title={shelfed ? 'On your shelf' : 'Set aside on your shelf'}
      >
        <BookmarkIcon filled={shelfed} size={13} />
      </button>
      <ProductCard
        product={product}
        index={index}
        deliveryLocations={deliveryLocations}
        preferences={preferences}
        onOpen={onOpen}
        savedSet={savedSet}
        savePendingSet={savePendingSet}
        onToggleSave={onToggleSave}
      />
      <div className="mt-ct-actionrow">
        <button
          className="mt-ct-addbtn"
          type="button"
          disabled={agentActionsDisabled}
          onClick={(event) => {
            event.stopPropagation()
            onAddCart(product)
          }}
        >
          <CartIcon /> Add to cart
        </button>
        <button
          className={`mt-ct-pinbtn ${pinned ? 'on' : ''}`}
          type="button"
          disabled={agentActionsDisabled}
          onClick={(event) => {
            event.stopPropagation()
            onPin(product)
          }}
        >
          {pinned ? 'Pinned' : 'Pin'}
        </button>
        <button
          className={`mt-ct-watchbtn ${watched ? 'on' : ''}`}
          type="button"
          disabled={agentActionsDisabled}
          onClick={(event) => {
            event.stopPropagation()
            onWatch(product)
          }}
        >
          {watched ? 'Watching' : 'Watch'}
        </button>
      </div>
      <div className="mt-ct-askbar">
        <span className="mt-mono mt-ct-askbar-lead">Dig in</span>
        <button
          className="mt-ct-askchip"
          type="button"
          disabled={agentActionsDisabled}
          onClick={(event) => {
            event.stopPropagation()
            onDig('reviews', product)
          }}
        >
          Reviews
        </button>
        <button
          className="mt-ct-askchip"
          type="button"
          disabled={agentActionsDisabled}
          onClick={(event) => {
            event.stopPropagation()
            onDig('code', product)
          }}
        >
          Find a discount
        </button>
        <button
          className="mt-ct-askchip"
          type="button"
          disabled={agentActionsDisabled}
          onClick={(event) => {
            event.stopPropagation()
            onDig('similar', product, query, qualificationId)
          }}
        >
          Similar
        </button>
      </div>
    </div>
  )
}

export function DiscoverProductBatch({
  products,
  query,
  qualificationId,
  deliveryLocations,
  preferences,
  savedSet,
  savePendingSet,
  pinnedSet,
  watchedSet,
  shelfProductSet,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onJustPick,
  onCompareHere,
  onShelfAddProduct,
  onDragProduct,
  sourceMessageId,
  onVisibleProductContextChange,
  agentActionsDisabled = false,
}: Readonly<{
  products: readonly Product[]
  query?: string
  qualificationId?: string
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfProductSet: ReadonlySet<ProductId>
  onOpen: (product: Product, products?: readonly Product[]) => void
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
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
  sourceMessageId?: string
  onVisibleProductContextChange?: VisibleProductContextChange
  agentActionsDisabled?: boolean
}>) {
  const isPhone = useMediaQuery('(max-width: 720px)')
  const [page, setPage] = useState(0)
  const [phoneIndex, setPhoneIndex] = useState(0)
  const carouselRef = useRef<HTMLDivElement | null>(null)
  const phoneScrollFrameRef = useRef<number | null>(null)
  const phoneStrideRef = useRef(0)
  const pageSize = isPhone ? Math.max(products.length, 1) : DESKTOP_PRODUCT_PAGE_SIZE
  const pageCount = Math.max(1, Math.ceil(products.length / pageSize))
  const currentPage = Math.min(page, pageCount - 1)
  const pageProducts = isPhone
    ? products
    : products.slice(currentPage * pageSize, currentPage * pageSize + pageSize)
  const many = !isPhone && products.length > pageSize
  const phoneMany = isPhone && products.length > 1
  const scrollPhoneCarousel = (direction: -1 | 1) => {
    const nextIndex = Math.min(products.length - 1, Math.max(0, phoneIndex + direction))
    setPhoneIndex(nextIndex)
    const target = carouselRef.current?.children.item(nextIndex)
    if (target instanceof HTMLElement) {
      target.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' })
    }
  }
  const syncPhoneCarouselIndex = () => {
    const scroller = carouselRef.current
    const stride = phoneStrideRef.current
    if (!isPhone || !scroller || stride <= 0 || phoneScrollFrameRef.current !== null) {
      return
    }
    phoneScrollFrameRef.current = window.requestAnimationFrame(() => {
      phoneScrollFrameRef.current = null
      setPhoneIndex(
        Math.min(products.length - 1, Math.max(0, Math.round(scroller.scrollLeft / stride))),
      )
    })
  }
  const renderPager = () =>
    many ? (
      <div className="mt-ct-pager">
        <button
          className="mt-ct-pager-btn"
          type="button"
          disabled={currentPage === 0}
          aria-label="Previous products"
          onClick={() => setPage((value) => Math.max(0, value - 1))}
        >
          <ChevronIcon direction="left" size={15} />
        </button>
        <span className="mt-mono mt-ct-pager-of">
          {currentPage + 1}/{pageCount}
        </span>
        <button
          className="mt-ct-pager-btn"
          type="button"
          disabled={currentPage >= pageCount - 1}
          aria-label="Next products"
          onClick={() => setPage((value) => Math.min(pageCount - 1, value + 1))}
        >
          <ChevronIcon direction="right" size={15} />
        </button>
      </div>
    ) : null
  const renderPhonePager = () =>
    phoneMany ? (
      <div className="mt-ct-swipe-nav" role="group" aria-label="Product carousel navigation">
        <span className="mt-mono mt-ct-swipe-hint">Swipe</span>
        <button
          className="mt-ct-pager-btn mt-ct-swipe-btn"
          type="button"
          disabled={phoneIndex === 0}
          aria-label="Previous product"
          onClick={() => scrollPhoneCarousel(-1)}
        >
          <ChevronIcon direction="left" size={15} />
        </button>
        <span className="mt-mono mt-ct-swipe-count" aria-live="polite" aria-atomic="true">
          {phoneIndex + 1}/{products.length}
        </span>
        <button
          className="mt-ct-pager-btn mt-ct-swipe-btn"
          type="button"
          disabled={phoneIndex >= products.length - 1}
          aria-label="Next product"
          onClick={() => scrollPhoneCarousel(1)}
        >
          <ChevronIcon direction="right" size={15} />
        </button>
      </div>
    ) : null

  useEffect(() => {
    setPage(0)
    setPhoneIndex(0)
    carouselRef.current?.scrollTo({ left: 0 })
  }, [isPhone, query, products])

  useEffect(() => {
    const contextSourceMessageId = sourceMessageId?.trim()
    if (!contextSourceMessageId) return
    const context = visibleProductContextForBatch({
      products,
      sourceMessageId: contextSourceMessageId,
      isPhone,
      page: currentPage,
      phoneIndex,
    })
    onVisibleProductContextChange?.(contextSourceMessageId, context)
    return () => onVisibleProductContextChange?.(contextSourceMessageId, undefined)
  }, [currentPage, isPhone, onVisibleProductContextChange, phoneIndex, products, sourceMessageId])

  useEffect(() => {
    if (!isPhone) {
      phoneStrideRef.current = 0
      return
    }
    const updatePhoneStride = () => {
      const scroller = carouselRef.current
      const first = scroller?.firstElementChild
      if (!(scroller instanceof HTMLElement) || !(first instanceof HTMLElement)) {
        phoneStrideRef.current = 0
        return
      }
      const gap = Number.parseFloat(window.getComputedStyle(scroller).columnGap || '0') || 0
      phoneStrideRef.current = first.offsetWidth + gap
    }
    updatePhoneStride()
    window.addEventListener('resize', updatePhoneStride)
    return () => {
      window.removeEventListener('resize', updatePhoneStride)
    }
  }, [isPhone, products.length])

  useEffect(
    () => () => {
      if (phoneScrollFrameRef.current !== null) {
        window.cancelAnimationFrame(phoneScrollFrameRef.current)
      }
    },
    [],
  )

  if (products.length === 0) {
    return null
  }

  return (
    <div className="mt-ct-batch">
      <div className="mt-ct-batch-head">
        <div className="mt-mono mt-ct-batch-count">
          {products.length} match{products.length === 1 ? '' : 'es'}
          {pageCount > 1
            ? ` · ${currentPage * pageSize + 1}-${Math.min((currentPage + 1) * pageSize, products.length)}`
            : ''}
        </div>
        {renderPager()}
        {renderPhonePager()}
      </div>
      <div
        ref={carouselRef}
        className={`mt-ct-grid${isPhone ? ' phone-swipe' : ''}`}
        role={isPhone ? 'region' : undefined}
        aria-roledescription={isPhone ? 'carousel' : undefined}
        aria-label={isPhone ? 'Product results' : undefined}
        onScroll={isPhone ? syncPhoneCarouselIndex : undefined}
      >
        {pageProducts.map((product, index) => (
          <DiscoverChatProduct
            key={product.id}
            carouselAccessibility={productCarouselItemAccessibility({
              isPhone,
              index,
              activeIndex: phoneIndex,
              total: products.length,
              name: product.name,
            })}
            product={product}
            index={isPhone ? index % 4 : index}
            query={query}
            qualificationId={qualificationId}
            deliveryLocations={deliveryLocations}
            preferences={preferences}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            pinned={pinnedSet.has(product.id)}
            watched={watchedSet.has(product.id)}
            shelfed={shelfProductSet.has(product.id)}
            onOpen={(nextProduct) => onOpen(nextProduct, products)}
            onToggleSave={onToggleSave}
            onAddCart={onAddCart}
            onPin={onPin}
            onWatch={onWatch}
            onDig={onDig}
            onShelfAdd={onShelfAddProduct}
            onDragProduct={onDragProduct}
            agentActionsDisabled={agentActionsDisabled}
          />
        ))}
      </div>
      <div className="mt-ct-batch-foot">
        <button
          className="mt-ct-suggchip"
          type="button"
          disabled={agentActionsDisabled}
          onClick={() => onJustPick(products)}
        >
          Just pick one for me
        </button>
        {products.length >= 2 ? (
          <button
            className="mt-ct-suggchip ghost"
            type="button"
            disabled={agentActionsDisabled}
            onClick={() => onCompareHere(products)}
          >
            Compare here
          </button>
        ) : null}
        {many ? <span className="mt-ct-batch-foot-sp" /> : null}
        {renderPager()}
      </div>
    </div>
  )
}
