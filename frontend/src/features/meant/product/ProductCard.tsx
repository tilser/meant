import {
  type KeyboardEvent,
  type ReactNode,
  type UIEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'

import { InventorySignalBadge } from '../inventory/InventorySignalBadge'
import { ChevronIcon, HeartIcon, MatchRing, PrefChip } from '../shared/icons'
import { CloseIcon, ProductArtwork } from '../shared/ui'
import type { Preference, Product, UserLocation } from '../types'
import { money, prefLabel, productMerchantCount, productPriceFrom } from '../utils'
import { productCuratedFields } from './productCuration'
import type { ProductOpenProps, ProductSaveProps } from './types'

interface TagRailPosition {
  canScrollBack: boolean
  hiddenAhead: number
}

function tagRailPosition(rail: HTMLDivElement): TagRailPosition {
  const obscuredByControl = 32
  const visibleEnd = rail.scrollLeft + rail.clientWidth - obscuredByControl
  const tags = Array.from(rail.children) as HTMLElement[]

  return {
    canScrollBack: rail.scrollLeft > 2,
    hiddenAhead: tags.filter((tag) => tag.offsetLeft + tag.offsetWidth > visibleEnd).length,
  }
}

function ProductTagRail({
  ariaLabel,
  children,
  className = '',
  itemCount,
}: Readonly<{
  ariaLabel: string
  children: ReactNode
  className?: string
  itemCount: number
}>) {
  const railRef = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState<TagRailPosition>({
    canScrollBack: false,
    hiddenAhead: 0,
  })

  const updatePosition = useCallback(() => {
    const rail = railRef.current
    if (!rail) {
      return
    }
    setPosition(tagRailPosition(rail))
  }, [])

  useEffect(() => {
    const rail = railRef.current
    if (!rail) {
      return
    }

    updatePosition()
    const resizeObserver =
      typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(updatePosition)
    resizeObserver?.observe(rail)
    window.addEventListener('resize', updatePosition)

    return () => {
      resizeObserver?.disconnect()
      window.removeEventListener('resize', updatePosition)
    }
  }, [itemCount, updatePosition])

  const scroll = (direction: -1 | 1) => {
    const rail = railRef.current
    if (!rail) {
      return
    }
    rail.scrollBy({
      left: direction * Math.max(rail.clientWidth * 0.72, 120),
      behavior: 'smooth',
    })
  }

  const handleRailKey = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault()
      event.stopPropagation()
      scroll(event.key === 'ArrowLeft' ? -1 : 1)
    }
  }

  const handleRailScroll = (event: UIEvent<HTMLDivElement>) => {
    setPosition(tagRailPosition(event.currentTarget))
  }

  return (
    <div
      className={`mt-card-tag-rail ${className}`}
      data-scroll-back={position.canScrollBack}
      data-scroll-ahead={position.hiddenAhead > 0}
    >
      {position.canScrollBack ? (
        <button
          className="mt-card-tag-control mt-card-tag-control-back"
          type="button"
          aria-label={`Show previous ${ariaLabel.toLowerCase()}`}
          onClick={(event) => {
            event.stopPropagation()
            scroll(-1)
          }}
        >
          <ChevronIcon direction="left" size={13} />
        </button>
      ) : null}
      <div
        ref={railRef}
        className="mt-card-tag-list"
        role="group"
        aria-label={ariaLabel}
        tabIndex={position.canScrollBack || position.hiddenAhead > 0 ? 0 : undefined}
        onClick={(event) => event.stopPropagation()}
        onKeyDown={handleRailKey}
        onScroll={handleRailScroll}
      >
        {children}
      </div>
      {position.hiddenAhead > 0 ? (
        <button
          className="mt-card-tag-control mt-card-tag-control-more"
          type="button"
          aria-label={`Show ${position.hiddenAhead} more ${ariaLabel.toLowerCase()}`}
          onClick={(event) => {
            event.stopPropagation()
            scroll(1)
          }}
        >
          +{position.hiddenAhead}
        </button>
      ) : null}
    </div>
  )
}

function productWasPrice(
  product: Product,
  deliveryLocations: readonly UserLocation[],
): number | null {
  const price = productPriceFrom(product, deliveryLocations)
  if (
    price == null ||
    product.listPrice === null ||
    product.listPrice === undefined ||
    product.listPrice <= price
  ) {
    return null
  }
  return product.listPrice
}

export function ProductPriceLine({
  product,
  deliveryLocations,
  className,
}: Readonly<{
  product: Product
  deliveryLocations: readonly UserLocation[]
  className: string
}>) {
  const price = productPriceFrom(product, deliveryLocations)
  const wasPrice = productWasPrice(product, deliveryLocations)

  return (
    <span className={className}>
      <span className="mt-mono mt-card-from">from</span>{' '}
      <span>{money(price, product.priceCurrency)}</span>
      {wasPrice ? (
        <span className="mt-was-price">{money(wasPrice, product.priceCurrency)}</span>
      ) : null}
    </span>
  )
}

function ProductReviewSummary({ product }: Readonly<{ product: Product }>) {
  if (product.agentStage === 'candidate' && product.review.count <= 0) {
    return <span className="mt-mono mt-card-rating mt-card-rating-live">Checking reviews</span>
  }
  if (product.review.count <= 0) {
    return <span className="mt-mono mt-card-rating muted">No review data</span>
  }
  if (product.review.score === null) {
    return (
      <span className="mt-mono mt-card-rating">
        {product.review.count.toLocaleString()} reviews
      </span>
    )
  }
  return (
    <span className="mt-mono mt-card-rating">
      ★ {product.review.score.toFixed(1)} · {product.review.count.toLocaleString()}
    </span>
  )
}

function catalogBadgeLabels(product: Product): string[] {
  const values = [
    ...(product.certifications ?? []),
    ...(product.materials ?? []),
    ...(product.collections ?? []),
  ]
  const seen = new Set<string>()
  return values
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => {
      const key = value.toLowerCase()
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
}

export function ProductCard({
  product,
  index,
  deliveryLocations,
  preferences,
  onOpen,
  savedSet,
  savePendingSet,
  onToggleSave,
  onDismiss,
}: Readonly<
  {
    product: Product
    index: number
    deliveryLocations: readonly UserLocation[]
    preferences: readonly Preference[]
  } & ProductOpenProps &
    ProductSaveProps
>) {
  const open = () => onOpen(product)
  const savePending = savePendingSet.has(product.id)
  const catalogBadges = catalogBadgeLabels(product)
  const preferenceTagCount = product.satisfies.length + product.misses.length
  const catalogTagCount = catalogBadges.length + (product.detailError ? 1 : 0)
  const liveStage = product.agentStage ?? 'ranked'
  const curatedFields = productCuratedFields(product, preferences)
  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.currentTarget !== event.target) {
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      open()
    }
  }

  return (
    <div
      className={`mt-card mt-card-in mt-card-live-${liveStage}`}
      data-product-id={product.id}
      role="button"
      tabIndex={0}
      onClick={open}
      onKeyDown={handleKey}
      style={{
        animationDelay: `${index * 45}ms`,
        transitionDelay: `${index * 18}ms`,
      }}
    >
      <div className="mt-card-media">
        <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
        <span className="mt-mono mt-card-cat">{product.category}</span>
        {!product.rankingUnavailable ? (
          <div className="mt-card-ring">
            <MatchRing value={product.match} />
          </div>
        ) : null}
        <button
          className={`mt-save ${savedSet.has(product.id) ? 'on' : ''}`}
          type="button"
          aria-label={
            savePending ? 'Saving' : savedSet.has(product.id) ? 'Remove from saved' : 'Save'
          }
          disabled={savePending}
          onClick={(event) => {
            event.stopPropagation()
            onToggleSave(product)
          }}
        >
          <HeartIcon filled={savedSet.has(product.id)} />
        </button>
        {onDismiss ? (
          <button
            className="mt-dismiss"
            type="button"
            aria-label="Dismiss from recommendations"
            onClick={(event) => {
              event.stopPropagation()
              onDismiss(product)
            }}
          >
            <CloseIcon size={14} />
          </button>
        ) : null}
      </div>

      <div className="mt-card-body">
        <div className="mt-mono mt-card-brand">{product.brand}</div>
        <InventorySignalBadge product={product} compact />
        <div className="mt-card-name">{product.name}</div>
        {preferenceTagCount > 0 ? (
          <ProductTagRail ariaLabel="Preference tags" itemCount={preferenceTagCount}>
            {product.satisfies.map((id) => (
              <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" small />
            ))}
            {product.misses.map((id) => (
              <PrefChip key={id} label={prefLabel(preferences, id)} variant="missed" small />
            ))}
          </ProductTagRail>
        ) : null}
        {catalogTagCount > 0 ? (
          <ProductTagRail
            ariaLabel="Product detail tags"
            className="mt-card-catalog-tag-rail"
            itemCount={catalogTagCount}
          >
            {catalogBadges.map((label) => (
              <span className="mt-catalog-pill" key={label}>
                {label}
              </span>
            ))}
            {product.detailError ? (
              <span className="mt-catalog-pill mt-catalog-pill-warning">Details unavailable</span>
            ) : null}
          </ProductTagRail>
        ) : null}
        <div className="mt-card-foot">
          <ProductPriceLine
            product={product}
            deliveryLocations={deliveryLocations}
            className="mt-card-price"
          />
          <span className="mt-mono mt-card-stores">
            {productMerchantCount(product, deliveryLocations)} stores
          </span>
        </div>
        <ProductReviewSummary product={product} />
        <div
          className={`mt-card-note ${product.agentStage === 'candidate' ? 'mt-card-note-live' : ''}`}
        >
          <span className="mt-note-key">Why it is meant for you</span>
          {curatedFields.note}
        </div>
      </div>
    </div>
  )
}
