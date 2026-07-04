import type { KeyboardEvent } from 'react'

import { InventorySignalBadge } from '../inventory/InventorySignalBadge'
import { HeartIcon, MatchRing, PrefChip } from '../shared/icons'
import { CloseIcon, ProductArtwork } from '../shared/ui'
import type { Preference, Product, UserLocation } from '../types'
import { money, prefLabel, productMerchantCount, productPriceFrom } from '../utils'
import { productCuratedFields } from './productCuration'
import type { ProductOpenProps, ProductSaveProps } from './types'

function productWasPrice(
  product: Product,
  deliveryLocations: readonly UserLocation[],
): number | null {
  const price = productPriceFrom(product, deliveryLocations)
  if (product.listPrice === null || product.listPrice === undefined || product.listPrice <= price) {
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
      <span className="mt-mono mt-card-from">from</span> <span>{money(price)}</span>
      {wasPrice ? <span className="mt-was-price">{money(wasPrice)}</span> : null}
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
  const catalogBadges = catalogBadgeLabels(product).slice(0, 3)
  const liveStage = product.agentStage ?? 'ranked'
  const curatedFields = productCuratedFields(product, preferences)
  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
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
        <div className="mt-card-ring">
          <MatchRing value={product.match} />
        </div>
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
        <div className="mt-chips">
          {product.satisfies.slice(0, 3).map((id) => (
            <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" small />
          ))}
          {product.misses.map((id) => (
            <PrefChip key={id} label={prefLabel(preferences, id)} variant="missed" small />
          ))}
        </div>
        {catalogBadges.length > 0 || product.detailError ? (
          <div className="mt-catalog-pills">
            {catalogBadges.map((label) => (
              <span className="mt-catalog-pill" key={label}>
                {label}
              </span>
            ))}
            {product.detailError ? (
              <span className="mt-catalog-pill mt-catalog-pill-warning">Details unavailable</span>
            ) : null}
          </div>
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
