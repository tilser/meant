import { type TouchEvent as ReactTouchEvent, useEffect, useRef, useState } from 'react'

import {
  getMerchantProductDetails,
  type MerchantProductDetailsProfile,
} from '../../../lib/apiClient'
import { AskComposer } from '../ask/AskComposer'
import { AskThread } from '../ask/AskThread'
import type { Message } from '../ask/types'
import { offerCartable } from '../cart/utils'
import { InventorySignalBadge } from '../inventory/InventorySignalBadge'
import { flyMessageToChat } from '../shared/animations'
import { ChevronIcon, HeartIcon, MatchRing, PrefChip } from '../shared/icons'
import { CloseIcon, ProductArtwork } from '../shared/ui'
import type { Offer, Preference, Product, UserLocation } from '../types'
import {
  availableOffers,
  bestOffer,
  money,
  prefLabel,
  productMerchantCount,
  resolveAsk,
} from '../utils'
import { ProductPriceLine } from './ProductCard'
import {
  productCuratedAdvantages,
  productCuratedTake,
  productCuratedTradeoffs,
} from './productCuration'
import {
  mergeProductMedia,
  productOptionsFromProfiles,
  productSelectedOptionsFromProfiles,
} from './productMapping'

type ProductDetailLoadState = 'idle' | 'loading' | 'loaded' | 'error'

const MODAL_THUMBNAIL_PAGE_SIZE = 8

function stripHtml(value: string | null | undefined): string {
  return (
    value
      ?.replace(/<[^>]*>/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() ?? ''
  )
}

export function ProductModal({
  product,
  deliveryLocations,
  preferences,
  saved,
  savePending,
  inCompare,
  onClose,
  onToggleSave,
  onCompare,
  onAddToCart,
  onAskInChat,
  canPrev,
  canNext,
  onPrev,
  onNext,
}: Readonly<{
  product: Product | null
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  saved: boolean
  savePending: boolean
  inCompare: boolean
  onClose: () => void
  onToggleSave: (product: Product) => void
  onCompare: (product: Product) => void
  onAddToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  onAskInChat?: (product: Product, question: string) => void
  canPrev: boolean
  canNext: boolean
  onPrev: () => void
  onNext: () => void
}>) {
  const [messages, setMessages] = useState<Message[]>([])
  const [added, setAdded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)
  const [selectedMediaUrl, setSelectedMediaUrl] = useState<string | null>(null)
  const [thumbnailPage, setThumbnailPage] = useState(0)
  const [zoomImageUrl, setZoomImageUrl] = useState<string | null>(null)
  const [merchantDetails, setMerchantDetails] = useState<MerchantProductDetailsProfile | null>(null)
  const [detailLoadState, setDetailLoadState] = useState<ProductDetailLoadState>('idle')
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null)
  const addedTimeoutRef = useRef<number | null>(null)
  const addSelectedOfferRef = useRef<(() => Promise<void>) | null>(null)
  const touchStartRef = useRef<{ x: number; y: number } | null>(null)
  const modalDockRef = useRef<HTMLDivElement | null>(null)
  const deliveryCountryCode = deliveryLocations[0]?.code ?? null

  useEffect(() => {
    setMessages([])
    setAdded(false)
    setAdding(false)
    setAddError(null)
    setSelectedMediaUrl(null)
    setThumbnailPage(0)
    setZoomImageUrl(null)
    setMerchantDetails(null)
    setDetailLoadState('idle')
    setDetailLoadError(null)
  }, [product?.id])

  useEffect(() => {
    if (!product?.remote || !product.merchantId || !product.merchantProductId) {
      return
    }
    const controller = new AbortController()
    setDetailLoadState('loading')
    setDetailLoadError(null)
    const language =
      typeof window === 'undefined' ? null : window.navigator.language.split('-')[0] || null
    getMerchantProductDetails({
      merchantId: product.merchantId,
      productId: product.merchantProductId,
      addressCountry: deliveryCountryCode,
      language,
      signal: controller.signal,
    })
      .then((details) => {
        if (controller.signal.aborted) {
          return
        }
        setMerchantDetails(details)
        setDetailLoadState('loaded')
      })
      .catch(() => {
        if (controller.signal.aborted) {
          return
        }
        setMerchantDetails(null)
        setDetailLoadState('error')
        setDetailLoadError('Latest product details are unavailable right now.')
      })
    return () => controller.abort()
  }, [
    deliveryCountryCode,
    product?.id,
    product?.merchantId,
    product?.merchantProductId,
    product?.remote,
  ])

  useEffect(
    () => () => {
      if (addedTimeoutRef.current !== null) {
        window.clearTimeout(addedTimeoutRef.current)
      }
    },
    [],
  )

  useEffect(() => {
    if (!product) {
      return
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.isContentEditable === true
      if (zoomImageUrl) {
        if (event.key === 'Escape') {
          event.preventDefault()
          setZoomImageUrl(null)
        }
        return
      }
      if (event.key === 'Escape') {
        onClose()
        return
      }
      if (typing) {
        return
      }
      if (event.key === 'ArrowRight' && canNext) {
        event.preventDefault()
        onNext()
        return
      }
      if (event.key === 'ArrowLeft' && canPrev) {
        event.preventDefault()
        onPrev()
        return
      }
      if (event.key === 'Enter' && addSelectedOfferRef.current) {
        event.preventDefault()
        void addSelectedOfferRef.current()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, product, canNext, canPrev, onNext, onPrev, zoomImageUrl])

  if (!product) {
    return null
  }

  const offers = availableOffers(product, deliveryLocations)
  const visibleOffers = offers.length > 0 ? offers : product.offers
  const modalMedia = mergeProductMedia(product, merchantDetails)
  const thumbnailPageCount = Math.ceil(modalMedia.length / MODAL_THUMBNAIL_PAGE_SIZE)
  const boundedThumbnailPage = Math.min(thumbnailPage, Math.max(thumbnailPageCount - 1, 0))
  const thumbnailStart = boundedThumbnailPage * MODAL_THUMBNAIL_PAGE_SIZE
  const visibleModalMedia = modalMedia.slice(
    thumbnailStart,
    thumbnailStart + MODAL_THUMBNAIL_PAGE_SIZE,
  )
  const hasMediaPages = thumbnailPageCount > 1
  const selectedMedia = selectedMediaUrl
    ? modalMedia.find((item) => item.url === selectedMediaUrl)
    : null
  const selectedImageUrl = selectedMedia?.type?.toLowerCase() === 'image' ? selectedMedia.url : null
  const modalImageUrl =
    selectedImageUrl ??
    merchantDetails?.selectedVariantImageUrl ??
    merchantDetails?.imageUrl ??
    product.imageUrl
  const curatorTake = productCuratedTake(product, preferences)
  const curatorAdvantages = productCuratedAdvantages(product, preferences)
  const curatorTradeoffs = productCuratedTradeoffs(product, preferences)
  const hasPreferenceMatches = product.satisfies.length > 0 || product.misses.length > 0
  const detailDescription = stripHtml(
    merchantDetails?.description || product.detailDescription || '',
  )
  const detailOptions = merchantDetails
    ? productOptionsFromProfiles(merchantDetails.options)
    : [...(product.detailOptions ?? [])]
  const selectedOptions = merchantDetails
    ? productSelectedOptionsFromProfiles(merchantDetails.selectedOptions)
    : [...(product.selectedOptions ?? [])]
  const hasProductDetails =
    detailLoadState === 'loading' ||
    Boolean(detailDescription) ||
    detailOptions.length > 0 ||
    selectedOptions.length > 0 ||
    Boolean(merchantDetails?.totalVariants) ||
    detailLoadState === 'error'
  const showProductDetailLoading =
    detailLoadState === 'loading' &&
    !detailDescription &&
    detailOptions.length === 0 &&
    selectedOptions.length === 0
  const reviewInsight =
    product.review.count > 0
      ? product.review.insight || 'Rating data is available; no review-summary agent has run yet.'
      : 'No review data available from this catalog result.'
  const showThumbnailPage = (nextPage: number) => {
    const page = Math.max(0, Math.min(nextPage, thumbnailPageCount - 1))
    const pageMedia = modalMedia.slice(
      page * MODAL_THUMBNAIL_PAGE_SIZE,
      page * MODAL_THUMBNAIL_PAGE_SIZE + MODAL_THUMBNAIL_PAGE_SIZE,
    )
    const firstImage = pageMedia.find((item) => item.type?.toLowerCase() === 'image')
    setThumbnailPage(page)
    if (firstImage) {
      setSelectedMediaUrl(firstImage.url)
    }
  }
  const ask = (question: string) => {
    if (onAskInChat) {
      const sourceElement =
        modalDockRef.current?.querySelector<HTMLElement>('.mt-ask-bar') ?? modalDockRef.current
      flyMessageToChat(sourceElement, question)
      onAskInChat(product, question)
      return
    }
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: resolveAsk(question, product, preferences) },
    ])
  }
  const selectedOffer = bestOffer(product, deliveryLocations)
  const canAddToCart = offerCartable(selectedOffer)
  const addDisabled = adding || !canAddToCart
  const addButtonLabel = added
    ? 'Added to cart'
    : adding
      ? 'Adding...'
      : selectedOffer.available === false
        ? 'Unavailable'
        : canAddToCart
          ? 'Add to cart'
          : 'Checkout unavailable'
  const addSelectedOffer = async () => {
    if (!canAddToCart || adding) {
      return
    }
    setAdding(true)
    setAddError(null)
    try {
      const addedToCart = await onAddToCart(product, selectedOffer)
      if (!addedToCart) {
        setAddError('Could not add this offer to the merchant cart.')
        return
      }
      setAdded(true)
      if (addedTimeoutRef.current !== null) {
        window.clearTimeout(addedTimeoutRef.current)
      }
      addedTimeoutRef.current = window.setTimeout(() => {
        setAdded(false)
        addedTimeoutRef.current = null
      }, 1600)
    } catch {
      setAddError('Could not add this offer to the merchant cart.')
    } finally {
      setAdding(false)
    }
  }
  addSelectedOfferRef.current = addDisabled ? null : addSelectedOffer

  const onTouchStart = (event: ReactTouchEvent) => {
    const touch = event.touches[0]
    touchStartRef.current = touch ? { x: touch.clientX, y: touch.clientY } : null
  }
  const onTouchEnd = (event: ReactTouchEvent) => {
    const start = touchStartRef.current
    touchStartRef.current = null
    if (!start) {
      return
    }
    const touch = event.changedTouches[0]
    if (!touch) {
      return
    }
    const dx = touch.clientX - start.x
    const dy = touch.clientY - start.y
    // Horizontal swipe only: needs enough travel and must dominate vertical movement,
    // so vertical scrolls inside the modal don't trigger navigation.
    if (Math.abs(dx) < 60 || Math.abs(dx) < Math.abs(dy) * 1.5) {
      return
    }
    if (dx < 0 && canNext) {
      onNext()
    } else if (dx > 0 && canPrev) {
      onPrev()
    }
  }

  return (
    <div className="mt-modal-root open">
      <button
        className="mt-modal-scrim"
        type="button"
        aria-label="Close product detail"
        onClick={onClose}
      />
      {canPrev || canNext ? (
        <>
          <button
            className="mt-modal-nav mt-modal-nav-prev"
            type="button"
            onClick={onPrev}
            disabled={!canPrev}
            aria-label="Previous product"
          >
            <ChevronIcon direction="left" />
            <span className="mt-modal-nav-text mt-mono">Prev</span>
          </button>
          <button
            className="mt-modal-nav mt-modal-nav-next"
            type="button"
            onClick={onNext}
            disabled={!canNext}
            aria-label="Next product"
          >
            <span className="mt-modal-nav-text mt-mono">Next</span>
            <ChevronIcon direction="right" />
          </button>
        </>
      ) : null}
      <div
        className="mt-modal"
        role="dialog"
        aria-modal="true"
        aria-label={product.name}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <button className="mt-modal-close" type="button" onClick={onClose} aria-label="Close">
          <CloseIcon />
        </button>
        <div className="mt-modal-body" key={product.id}>
          <div className="mt-modal-left">
            <button
              className={`mt-modal-media ${modalImageUrl ? 'mt-modal-media-open' : ''}`}
              type="button"
              disabled={!modalImageUrl}
              aria-label={modalImageUrl ? `Enlarge photo of ${product.name}` : undefined}
              onClick={() => {
                if (modalImageUrl) {
                  setZoomImageUrl(modalImageUrl)
                }
              }}
            >
              <ProductArtwork
                product={product}
                label={`${product.category.toLowerCase()} shot`}
                imageUrl={modalImageUrl}
              />
              <div className="mt-modal-ring">
                <MatchRing value={product.match} size={56} stroke={4} />
              </div>
            </button>
            {modalMedia.length > 1 ? (
              <div className={`mt-modal-thumbs-wrap ${hasMediaPages ? 'paged' : ''}`}>
                {hasMediaPages ? (
                  <button
                    className="mt-modal-thumb-page"
                    type="button"
                    disabled={boundedThumbnailPage === 0}
                    aria-label={`Previous images for ${product.name}`}
                    onClick={() => showThumbnailPage(boundedThumbnailPage - 1)}
                  >
                    <ChevronIcon direction="left" size={16} />
                  </button>
                ) : null}
                <div className="mt-modal-thumbs">
                  {visibleModalMedia.map((item, index) => {
                    const isImage = item.type?.toLowerCase() === 'image'
                    const isSelected = isImage && item.url === modalImageUrl
                    const imageIndex = thumbnailStart + index + 1
                    return (
                      <button
                        className={`mt-modal-thumb ${isSelected ? 'active' : ''}`}
                        key={`${item.type}-${item.url}`}
                        type="button"
                        disabled={!isImage}
                        aria-label={
                          isImage
                            ? `Show image ${imageIndex} for ${product.name}`
                            : `${item.type} media`
                        }
                        aria-pressed={isImage ? isSelected : undefined}
                        onClick={() => setSelectedMediaUrl(item.url)}
                      >
                        {isImage ? (
                          <img src={item.url} alt={item.altText || product.name} loading="lazy" />
                        ) : (
                          <span className="mt-mono">{item.type}</span>
                        )}
                      </button>
                    )
                  })}
                </div>
                {hasMediaPages ? (
                  <button
                    className="mt-modal-thumb-page"
                    type="button"
                    disabled={boundedThumbnailPage >= thumbnailPageCount - 1}
                    aria-label={`Next images for ${product.name}`}
                    onClick={() => showThumbnailPage(boundedThumbnailPage + 1)}
                  >
                    <ChevronIcon direction="right" size={16} />
                  </button>
                ) : null}
              </div>
            ) : null}
            <div className="mt-mono mt-card-brand">
              {product.brand} · {product.category}
            </div>
            <h2 className="mt-modal-name">{product.name}</h2>
            <div className="mt-modal-price-row">
              <ProductPriceLine
                product={product}
                deliveryLocations={deliveryLocations}
                className="mt-modal-price"
              />
              <span className="mt-mono mt-modal-stores">
                · {productMerchantCount(product, deliveryLocations)} stores
              </span>
            </div>
            <InventorySignalBadge product={product} />
            <div className="mt-modal-actions">
              <button
                className={`mt-act mt-act-icon ${saved ? 'on' : ''}`}
                type="button"
                onClick={() => onToggleSave(product)}
                disabled={savePending}
                aria-label={savePending ? 'Saving saved product' : saved ? 'Saved' : 'Save'}
              >
                <HeartIcon filled={saved} />
              </button>
              <button
                className={`mt-act mt-act-ghost ${inCompare ? 'on' : ''}`}
                type="button"
                onClick={() => onCompare(product)}
              >
                {inCompare ? 'In compare' : 'Add to compare'}
              </button>
              <button
                className={`mt-act mt-act-primary ${added ? 'done' : ''}`}
                type="button"
                onClick={() => void addSelectedOffer()}
                disabled={addDisabled}
              >
                <span>{addButtonLabel}</span>
                {!addDisabled && !added ? (
                  <kbd className="mt-act-key" aria-hidden>
                    ↵
                  </kbd>
                ) : null}
              </button>
            </div>
            {addError ? <div className="mt-cart-inline-error">{addError}</div> : null}
            {!canAddToCart && !addError ? (
              <div className="mt-cart-inline-error muted">
                This offer is not available for merchant checkout.
              </div>
            ) : null}
          </div>

          <div className="mt-modal-right">
            <div className="mt-drawer-note">
              <span className="mt-note-key">Meant's take</span>
              {curatorTake}
            </div>

            {hasProductDetails ? (
              <section className="mt-block">
                <div className="mt-block-label mt-mono">About this product</div>
                {showProductDetailLoading ? (
                  <p className="mt-product-detail-muted mt-mono">
                    Loading latest product details...
                  </p>
                ) : null}
                {detailDescription ? (
                  <p className="mt-product-detail-description">{detailDescription}</p>
                ) : null}
                {selectedOptions.length > 0 ? (
                  <div className="mt-product-detail-facts">
                    {selectedOptions.map((option) => (
                      <span
                        className="mt-product-detail-fact"
                        key={`${option.name}-${option.value}`}
                      >
                        <span className="mt-mono">{option.name}</span>
                        {option.value}
                      </span>
                    ))}
                  </div>
                ) : null}
                {detailOptions.length > 0 ? (
                  <div className="mt-product-options">
                    {detailOptions.slice(0, 4).map((option) => (
                      <div className="mt-product-option" key={option.name}>
                        <span className="mt-mono">{option.name}</span>
                        <span>{option.values.slice(0, 8).join(', ')}</span>
                      </div>
                    ))}
                  </div>
                ) : null}
                {merchantDetails?.totalVariants ? (
                  <p className="mt-product-detail-muted mt-mono">
                    {merchantDetails.totalVariants.toLocaleString()} variants available
                  </p>
                ) : null}
                {detailLoadState === 'error' && !detailDescription ? (
                  <p className="mt-product-detail-muted mt-product-detail-error">
                    {detailLoadError || 'Latest product details are unavailable right now.'}
                  </p>
                ) : null}
              </section>
            ) : null}

            <section className="mt-block">
              <div className="mt-block-label mt-mono">Preference match</div>
              {hasPreferenceMatches ? (
                <div className="mt-chips">
                  {product.satisfies.map((id) => (
                    <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" />
                  ))}
                  {product.misses.map((id) => (
                    <PrefChip key={id} label={prefLabel(preferences, id)} variant="missed" />
                  ))}
                </div>
              ) : (
                <div className="mt-mono mt-pref-empty-inline">
                  No confirmed preference matches yet
                </div>
              )}
            </section>

            <section className="mt-block">
              <div className="mt-procon">
                <div>
                  <div className="mt-block-label mt-mono">Advantages</div>
                  <ul className="mt-list mt-list-pro">
                    {curatorAdvantages.map((pro) => (
                      <li key={pro}>{pro}</li>
                    ))}
                  </ul>
                </div>
                <div>
                  <div className="mt-block-label mt-mono">Trade-offs</div>
                  <ul className="mt-list mt-list-con">
                    {curatorTradeoffs.map((con) => (
                      <li key={con}>{con}</li>
                    ))}
                  </ul>
                </div>
              </div>
            </section>

            <section className="mt-block">
              <div className="mt-reviews-head">
                <div className="mt-block-label mt-mono">From the reviews</div>
                {product.review.count > 0 ? (
                  <div className="mt-reviews-score">
                    {product.review.score !== null ? (
                      <span className="mt-stars">
                        {'★'.repeat(Math.round(product.review.score))}
                      </span>
                    ) : null}
                    <span className="mt-mono">
                      {product.review.score !== null ? `${product.review.score.toFixed(1)} · ` : ''}
                      {product.review.count.toLocaleString()}
                      {product.review.score === null ? ' reviews' : ''}
                    </span>
                  </div>
                ) : (
                  <span className="mt-mono mt-reviews-empty">No review data</span>
                )}
              </div>
              <p className="mt-reviews-insight">{reviewInsight}</p>
            </section>

            <section className="mt-block">
              <div className="mt-block-label mt-mono">Available offers</div>
              <div className="mt-offers">
                {visibleOffers.map((offer, index) => (
                  <div className={`mt-offer ${index === 0 ? 'best' : ''}`} key={offer.merchant}>
                    <div className="mt-offer-merch">
                      {offer.merchant}
                      {index === 0 ? <span className="mt-mono mt-offer-tag">best</span> : null}
                    </div>
                    <div className="mt-offer-right">
                      <span className="mt-mono mt-offer-deliv">{offer.delivery}</span>
                      <span className="mt-offer-price">{money(offer.price)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          </div>
        </div>

        <div className="mt-modal-dock" ref={modalDockRef}>
          <AskThread messages={messages} />
          <AskComposer
            placeholder={`Ask Meant about ${product.name}...`}
            suggestions={[
              'Does this match my preferences?',
              'Is there a cheaper option?',
              'What do reviewers say?',
            ]}
            showChips={messages.length === 0}
            onAsk={ask}
          />
          {onAskInChat ? (
            <div className="mt-mono mt-modal-dock-hint">
              Your question moves into the chat, where Meant answers in full.
            </div>
          ) : null}
        </div>
      </div>
      {zoomImageUrl ? (
        <div
          className="mt-image-zoom"
          role="dialog"
          aria-modal="true"
          aria-label={`Larger photo of ${product.name}`}
        >
          <button
            className="mt-image-zoom-scrim"
            type="button"
            aria-label="Close enlarged photo"
            onClick={() => setZoomImageUrl(null)}
          />
          <div className="mt-image-zoom-panel">
            <img className="mt-image-zoom-img" src={zoomImageUrl} alt={product.name} />
            <button
              className="mt-image-zoom-close"
              type="button"
              aria-label="Close enlarged photo"
              onClick={() => setZoomImageUrl(null)}
            >
              <CloseIcon />
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
