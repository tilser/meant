import { type TouchEvent as ReactTouchEvent, useEffect, useRef, useState } from 'react'

import {
  getMerchantProductDetails,
  type MerchantProductDetailsProfile,
  type MerchantProductVariantProfile,
  type ProductAttributeProfile,
  type ProductMessageProfile,
} from '../../../lib/apiClient'
import { AskComposer } from '../ask/AskComposer'
import { AskThread } from '../ask/AskThread'
import type { Message } from '../ask/types'
import { canResolveCartOffer, offerCartable } from '../cart/utils'
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
import { ProductReviewsPanel } from './ProductReviewsPanel'

type ProductDetailLoadState = 'idle' | 'loading' | 'loaded' | 'error'

const MODAL_THUMBNAIL_PAGE_SIZE = 8

function stripHtml(value: string | null | undefined): string {
  return (value ?? '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function stripMarkdown(value: string | null | undefined): string {
  return stripHtml(value)
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/(\*\*|__)(.*?)\1/g, '$2')
    .replace(/(\*|_)(.*?)\1/g, '$2')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^\s*[-*+>#]+\s+/gm, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function safeMessageUrl(value: string | null | undefined): string | null {
  if (!value) {
    return null
  }
  try {
    const url = new URL(value)
    return ['http:', 'https:', 'mailto:'].includes(url.protocol) ? value : null
  } catch {
    return null
  }
}

function detailMoney(
  amount: string | null | undefined,
  currency: string | null | undefined,
): string | null {
  if (!amount) {
    return null
  }
  const value = Number(amount)
  if (!Number.isFinite(value)) {
    return amount
  }
  if (currency && /^[A-Z]{3}$/i.test(currency)) {
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: currency.toUpperCase(),
      }).format(value)
    } catch {
      return `${currency.toUpperCase()} ${value.toFixed(2)}`
    }
  }
  return `$${value.toFixed(2)}`
}

function sameAmount(first: string | null | undefined, second: string | null | undefined): boolean {
  const firstValue = Number(first)
  const secondValue = Number(second)
  if (Number.isFinite(firstValue) && Number.isFinite(secondValue)) {
    return firstValue === secondValue
  }
  return first === second
}

function cleanValues(values: readonly (string | null | undefined)[] | null | undefined): string[] {
  const seen = new Set<string>()
  return (values ?? [])
    .map((value) => value?.trim())
    .filter((value): value is string => Boolean(value))
    .filter((value) => {
      const key = value.toLowerCase()
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
}

function attributeRows(
  attributes: readonly ProductAttributeProfile[] | null | undefined,
): ProductAttributeProfile[] {
  const seen = new Set<string>()
  return (attributes ?? [])
    .map((attribute): ProductAttributeProfile | null => {
      const name = attribute?.name?.trim()
      const value = attribute?.value?.trim()
      if (!name || !value) {
        return null
      }
      const key = `${name.toLowerCase()}|${value.toLowerCase()}`
      if (seen.has(key)) {
        return null
      }
      seen.add(key)
      return { name, value }
    })
    .filter((attribute): attribute is ProductAttributeProfile => attribute !== null)
}

function messageRows(
  messages: readonly ProductMessageProfile[] | null | undefined,
): ProductMessageProfile[] {
  return (messages ?? []).filter(
    (message): message is ProductMessageProfile =>
      Boolean(message) && Boolean(stripMarkdown(message.content)),
  )
}

function selectedOptionValue(
  variant: MerchantProductVariantProfile,
  optionName: string,
): string | null {
  const normalizedName = optionName.trim().toLowerCase()
  return (
    variant.selectedOptions?.find(
      (option) => option?.name?.trim()?.toLowerCase() === normalizedName,
    )?.value ?? null
  )
}

function optionAvailability(
  variants: readonly MerchantProductVariantProfile[],
  optionName: string,
  optionValue: string,
): { label: string; className: string } {
  const matchingVariants = variants.filter(
    (variant) =>
      Boolean(variant) &&
      selectedOptionValue(variant, optionName)?.trim()?.toLowerCase() ===
        optionValue.trim().toLowerCase(),
  )
  if (matchingVariants.length === 0) {
    return { label: 'Listed', className: 'unknown' }
  }
  const availableCount = matchingVariants.filter((variant) => variant.available === true).length
  if (availableCount > 0) {
    return {
      label:
        availableCount === matchingVariants.length
          ? 'Available'
          : `${availableCount}/${matchingVariants.length} available`,
      className: 'available',
    }
  }
  if (matchingVariants.every((variant) => variant.available === false)) {
    return { label: 'Unavailable', className: 'unavailable' }
  }
  return { label: 'Check merchant', className: 'unknown' }
}

function variantOptionSummary(variant: MerchantProductVariantProfile): string {
  const options = productSelectedOptionsFromProfiles(variant.selectedOptions)
  if (options.length > 0) {
    return options.map((option) => `${option.name}: ${option.value}`).join(' / ')
  }
  return variant.title?.trim() || 'Default'
}

function availabilityLabel(value: boolean | null | undefined): string {
  if (value === true) {
    return 'Available'
  }
  if (value === false) {
    return 'Unavailable'
  }
  return 'Check merchant'
}

function availabilityClass(value: boolean | null | undefined): string {
  if (value === true) {
    return 'available'
  }
  if (value === false) {
    return 'unavailable'
  }
  return 'unknown'
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
      const target = event.target instanceof HTMLElement ? event.target : null
      const isInteractive = Boolean(
        target?.closest(
          'input, textarea, select, button, a, [contenteditable="true"], [role="button"], [role="link"], [role="menuitem"], [role="textbox"]',
        ) ?? target?.isContentEditable,
      )
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
      if (isInteractive) {
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
  const detailVariants = (merchantDetails?.variants ?? []).filter(
    (variant): variant is MerchantProductVariantProfile => Boolean(variant),
  )
  const availableVariantCount = detailVariants.filter(
    (variant) => variant.available === true,
  ).length
  const unavailableVariantCount = detailVariants.filter(
    (variant) => variant.available === false,
  ).length
  const unknownVariantCount = Math.max(
    detailVariants.length - availableVariantCount - unavailableVariantCount,
    0,
  )
  const detailCategories = cleanValues(
    (merchantDetails?.categories ?? []).map((category) => category?.value),
  )
  const detailTags = cleanValues(merchantDetails?.tags)
  const detailSkus = cleanValues(merchantDetails?.skus)
  const detailMaterials = cleanValues(merchantDetails?.materials)
  const detailCertifications = cleanValues(merchantDetails?.certifications)
  const detailCollections = cleanValues(merchantDetails?.collections)
  const detailAttributes = attributeRows(merchantDetails?.attributes)
  const detailMessages = messageRows(merchantDetails?.messages)
  const selectedVariantPrice = detailMoney(
    merchantDetails?.selectedVariantPriceAmount,
    merchantDetails?.selectedVariantPriceCurrency,
  )
  const selectedVariantListPrice = detailMoney(
    merchantDetails?.selectedVariantListPriceAmount,
    merchantDetails?.selectedVariantListPriceCurrency,
  )
  const hasSelectedVariantFacts =
    selectedOptions.length > 0 ||
    Boolean(merchantDetails?.selectedVariantSku) ||
    Boolean(selectedVariantPrice) ||
    (merchantDetails?.selectedVariantAvailable !== null &&
      merchantDetails?.selectedVariantAvailable !== undefined)
  const listPriceRange =
    merchantDetails?.listPriceMin && merchantDetails?.listPriceMax
      ? sameAmount(merchantDetails.listPriceMin, merchantDetails.listPriceMax)
        ? detailMoney(merchantDetails.listPriceMin, merchantDetails.listPriceCurrency)
        : `${detailMoney(merchantDetails.listPriceMin, merchantDetails.listPriceCurrency)} - ${detailMoney(
            merchantDetails.listPriceMax,
            merchantDetails.listPriceCurrency,
          )}`
      : null
  const detailDataGroups = [
    { label: 'Materials', values: detailMaterials },
    { label: 'Certifications', values: detailCertifications },
    { label: 'Collections', values: detailCollections },
    { label: 'Tags', values: detailTags },
    {
      label: 'Identifiers',
      values: cleanValues([
        merchantDetails?.handle ? `Handle: ${merchantDetails.handle}` : null,
        merchantDetails?.productId ? `Product: ${merchantDetails.productId}` : null,
        ...detailSkus.map((sku) => `SKU: ${sku}`),
      ]),
    },
  ].filter((group) => group.values.length > 0)
  const hasMerchantData = detailDataGroups.length > 0 || detailAttributes.length > 0
  const hasProductDetails =
    detailLoadState === 'loading' ||
    Boolean(detailDescription) ||
    detailOptions.length > 0 ||
    selectedOptions.length > 0 ||
    hasSelectedVariantFacts ||
    detailVariants.length > 0 ||
    detailMessages.length > 0 ||
    detailCategories.length > 0 ||
    hasMerchantData ||
    Boolean(merchantDetails?.totalVariants) ||
    detailLoadState === 'error'
  const showProductDetailLoading =
    detailLoadState === 'loading' &&
    !detailDescription &&
    detailOptions.length === 0 &&
    selectedOptions.length === 0
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
  const canAddToCart = offerCartable(selectedOffer) || canResolveCartOffer(product, selectedOffer)
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
                {detailMessages.length > 0 ? (
                  <div className="mt-product-messages">
                    {detailMessages.map((message, index) => {
                      const messageUrl = safeMessageUrl(message.url)
                      return (
                        <div
                          className={`mt-product-message ${message.presentation === 'disclosure' ? 'disclosure' : ''} ${message.type || 'info'}`}
                          key={`${message.code ?? message.type ?? 'message'}-${index}`}
                        >
                          <div className="mt-product-message-main">
                            <span className="mt-mono">
                              {message.presentation === 'disclosure'
                                ? 'Disclosure'
                                : message.type || 'Notice'}
                            </span>
                            <p>{stripMarkdown(message.content)}</p>
                          </div>
                          {messageUrl ? (
                            <a
                              className="mt-product-message-link mt-mono"
                              href={messageUrl}
                              target="_blank"
                              rel="noreferrer"
                            >
                              Source
                            </a>
                          ) : null}
                        </div>
                      )
                    })}
                  </div>
                ) : null}
                {hasSelectedVariantFacts ? (
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
                    {merchantDetails?.selectedVariantSku ? (
                      <span className="mt-product-detail-fact">
                        <span className="mt-mono">SKU</span>
                        {merchantDetails.selectedVariantSku}
                      </span>
                    ) : null}
                    {selectedVariantPrice ? (
                      <span className="mt-product-detail-fact">
                        <span className="mt-mono">Selected</span>
                        {selectedVariantListPrice ? (
                          <>
                            <s>{selectedVariantListPrice}</s> {selectedVariantPrice}
                          </>
                        ) : (
                          selectedVariantPrice
                        )}
                      </span>
                    ) : null}
                    {merchantDetails?.selectedVariantAvailable !== null &&
                    merchantDetails?.selectedVariantAvailable !== undefined ? (
                      <span
                        className={`mt-product-detail-fact ${availabilityClass(
                          merchantDetails.selectedVariantAvailable,
                        )}`}
                      >
                        <span className="mt-mono">Stock</span>
                        {availabilityLabel(merchantDetails.selectedVariantAvailable)}
                      </span>
                    ) : null}
                  </div>
                ) : null}
                {detailCategories.length > 0 ? (
                  <div className="mt-product-category-strip">
                    {detailCategories.map((category) => (
                      <span className="mt-product-category-chip" key={category}>
                        {category}
                      </span>
                    ))}
                  </div>
                ) : null}
                {listPriceRange ? (
                  <p className="mt-product-detail-muted mt-mono">List price {listPriceRange}</p>
                ) : null}
                {detailLoadState === 'error' && !detailDescription ? (
                  <p className="mt-product-detail-muted mt-product-detail-error">
                    {detailLoadError || 'Latest product details are unavailable right now.'}
                  </p>
                ) : null}
              </section>
            ) : null}

            {detailOptions.length > 0 ? (
              <section className="mt-block">
                <div className="mt-block-label mt-mono">Options and availability</div>
                <div className="mt-product-option-groups">
                  {detailOptions.map((option) => (
                    <div className="mt-product-option-group" key={option.name}>
                      <div className="mt-product-option-head">
                        <span className="mt-mono">{option.name}</span>
                        <span>
                          {option.values.length} value{option.values.length === 1 ? '' : 's'}
                        </span>
                      </div>
                      <div className="mt-product-option-values">
                        {option.values.map((value) => {
                          const availability = optionAvailability(
                            detailVariants,
                            option.name,
                            value,
                          )
                          return (
                            <span
                              className={`mt-product-option-chip ${availability.className}`}
                              key={`${option.name}-${value}`}
                            >
                              <span>{value}</span>
                              <span className="mt-mono">{availability.label}</span>
                            </span>
                          )
                        })}
                      </div>
                    </div>
                  ))}
                </div>
                {detailOptions.length > 0 ? (
                  <p className="mt-product-detail-muted mt-mono">
                    {merchantDetails?.totalVariants
                      ? `${merchantDetails.totalVariants.toLocaleString()} merchant variants`
                      : `${detailVariants.length.toLocaleString()} merchant variants`}
                    {availableVariantCount > 0 ? ` / ${availableVariantCount} available` : ''}
                    {unavailableVariantCount > 0 ? ` / ${unavailableVariantCount} unavailable` : ''}
                    {unknownVariantCount > 0 ? ` / ${unknownVariantCount} check merchant` : ''}
                  </p>
                ) : null}
              </section>
            ) : null}

            {detailVariants.length > 0 ? (
              <section className="mt-block">
                <div className="mt-block-label mt-mono">Variants</div>
                <div className="mt-product-variant-table" role="table">
                  <div className="mt-product-variant-head" role="row">
                    <span>Variant</span>
                    <span>Options</span>
                    <span>Price</span>
                    <span>Availability</span>
                  </div>
                  <div className="mt-product-variant-rows">
                    {detailVariants.map((variant, index) => {
                      const price = detailMoney(variant.priceAmount, variant.priceCurrency)
                      const listPrice = detailMoney(
                        variant.listPriceAmount,
                        variant.listPriceCurrency,
                      )
                      const variantTitle = variant.title?.trim() || `Variant ${index + 1}`
                      return (
                        <div
                          className="mt-product-variant-row"
                          key={variant.variantId || `${variantTitle}-${index}`}
                          role="row"
                        >
                          <div className="mt-product-variant-main">
                            {variant.imageUrl ? (
                              <img
                                src={variant.imageUrl}
                                alt={variant.imageAltText || variantTitle}
                                loading="lazy"
                              />
                            ) : null}
                            <div>
                              <div className="mt-product-variant-title">{variantTitle}</div>
                              {variant.sku ? (
                                <div className="mt-product-variant-sku mt-mono">{variant.sku}</div>
                              ) : null}
                            </div>
                          </div>
                          <div className="mt-product-variant-options">
                            {variantOptionSummary(variant)}
                          </div>
                          <div className="mt-product-variant-price">
                            {listPrice ? <s>{listPrice}</s> : null}
                            <span>{price ?? 'See merchant'}</span>
                          </div>
                          <div>
                            <span
                              className={`mt-product-variant-availability ${availabilityClass(
                                variant.available,
                              )}`}
                            >
                              {availabilityLabel(variant.available)}
                            </span>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </section>
            ) : null}

            {hasMerchantData ? (
              <section className="mt-block">
                <div className="mt-block-label mt-mono">Merchant data</div>
                <div className="mt-product-data">
                  {detailDataGroups.map((group) => (
                    <div className="mt-product-data-group" key={group.label}>
                      <div className="mt-mono">{group.label}</div>
                      <div>
                        {group.values.map((value) => (
                          <span key={value}>{value}</span>
                        ))}
                      </div>
                    </div>
                  ))}
                  {detailAttributes.map((attribute) => (
                    <div
                      className="mt-product-data-group"
                      key={`${attribute.name}-${attribute.value}`}
                    >
                      <div className="mt-mono">{attribute.name}</div>
                      <div>
                        <span>{attribute.value}</span>
                      </div>
                    </div>
                  ))}
                </div>
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

            <ProductReviewsPanel product={product} />

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
