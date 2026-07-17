import { type TouchEvent as ReactTouchEvent, useCallback, useEffect, useRef, useState } from 'react'

import {
  getMerchantProductDetails,
  type MerchantProductDetailsProfile,
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
  displayProductCategoryValue,
  money,
  prefLabel,
  productMerchantCount,
  productPriceFrom,
  resolveAsk,
} from '../utils'
import {
  productCuratedAdvantages,
  productCuratedTake,
  productCuratedTradeoffs,
} from './productCuration'
import { mergeRehydratedProductDetails, savedProductDetailsRefreshShell } from './productSnapshots'
import { mergeProductMedia } from './productMapping'
import { merchantProductDetailRequest } from './productDetailLoading'
import { ProductReviewsPanel } from './ProductReviewsPanel'
import { GroupedOfferSelector, type ProductPurchaseSelection } from './GroupedProductModal'
import { containModalTabFocus } from './modalFocusTrap'
import { findSelectedVariant } from './variantSelection'

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
  const normalizedAmount = amount?.trim()
  const normalizedCurrency = currency?.trim().toUpperCase()
  if (!normalizedAmount || !normalizedCurrency || !/^[A-Z]{3}$/.test(normalizedCurrency)) {
    return null
  }
  const value = Number(normalizedAmount)
  if (!Number.isFinite(value) || value < 0) {
    return null
  }
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: normalizedCurrency,
    }).format(value)
  } catch {
    return null
  }
}

function cleanCategoryValues(
  values: readonly (string | null | undefined)[] | null | undefined,
): string[] {
  const seen = new Set<string>()
  return (values ?? [])
    .map(displayProductCategoryValue)
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

function messageRows(
  messages: readonly ProductMessageProfile[] | null | undefined,
): ProductMessageProfile[] {
  return (messages ?? []).filter(
    (message): message is ProductMessageProfile =>
      Boolean(message) && Boolean(stripMarkdown(message.content)),
  )
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

function purchaseSelectionKey(selection: ProductPurchaseSelection): string {
  const options = selection.selectedOptions
    .map((option) => `${option.name?.trim().toLowerCase()}:${option.value?.trim().toLowerCase()}`)
    .sort()
    .join('|')
  return `${selection.offerKey ?? ''}|${selection.selectedVariantId ?? ''}|${options}`
}

function variantSavings(
  currentAmount: string | null | undefined,
  currentCurrency: string | null | undefined,
  listAmount: string | null | undefined,
  listCurrency: string | null | undefined,
): { amount: string; percent: number } | null {
  if (!currentAmount?.trim() || !listAmount?.trim()) {
    return null
  }
  const normalizedCurrentCurrency = currentCurrency?.trim().toUpperCase()
  const normalizedListCurrency = listCurrency?.trim().toUpperCase()
  const currentValue = Number(currentAmount)
  const listValue = Number(listAmount)
  if (
    !normalizedCurrentCurrency ||
    !/^[A-Z]{3}$/.test(normalizedCurrentCurrency) ||
    normalizedCurrentCurrency !== normalizedListCurrency ||
    !Number.isFinite(currentValue) ||
    !Number.isFinite(listValue) ||
    currentValue < 0 ||
    listValue <= currentValue
  ) {
    return null
  }
  const amount = detailMoney(String(listValue - currentValue), normalizedCurrentCurrency)
  if (!amount) {
    return null
  }
  return {
    amount,
    percent: Math.round(((listValue - currentValue) / listValue) * 100),
  }
}

export function ProductModal({
  product,
  userId,
  deliveryLocations,
  preferences,
  saved,
  savePending,
  savedOfferRefreshPending = false,
  inCompare,
  onClose,
  onToggleSave,
  onUpdateSavedChoice,
  onCompare,
  onAddToCart,
  onAddOfferKey,
  onRefreshProduct,
  researchQuery,
  onAskInChat,
  canPrev,
  canNext,
  onPrev,
  onNext,
}: Readonly<{
  product: Product | null
  userId?: string
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  saved: boolean
  savePending: boolean
  savedOfferRefreshPending?: boolean
  inCompare: boolean
  onClose: () => void
  onToggleSave: (product: Product) => void
  onUpdateSavedChoice?: (product: Product, offerKey: string) => void
  onCompare: (product: Product) => void
  onAddToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  onAddOfferKey?: (product: Product, offerKey: string) => Promise<boolean>
  onRefreshProduct?: (product: Product) => void
  researchQuery?: string | null
  onAskInChat?: (product: Product, question: string) => void
  canPrev: boolean
  canNext: boolean
  onPrev: () => void
  onNext: () => void
}>) {
  const refreshingSavedOffers = saved && savedOfferRefreshPending
  const canonicalProductKey = refreshingSavedOffers
    ? null
    : (product?.canonicalProduct?.key ?? null)
  const merchantDetailRequest = merchantProductDetailRequest(product)
  const merchantDetailMerchantId = merchantDetailRequest?.merchantId ?? null
  const merchantDetailProductId = merchantDetailRequest?.productId ?? null
  const [messages, setMessages] = useState<Message[]>([])
  const [added, setAdded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)
  const [purchaseSelection, setPurchaseSelection] = useState<ProductPurchaseSelection | null>(null)
  const [selectedMediaUrl, setSelectedMediaUrl] = useState<string | null>(null)
  const [thumbnailPage, setThumbnailPage] = useState(0)
  const [zoomImageUrl, setZoomImageUrl] = useState<string | null>(null)
  const [merchantDetails, setMerchantDetails] = useState<MerchantProductDetailsProfile | null>(
    product?.rehydratedDetails ?? null,
  )
  const [detailLoadState, setDetailLoadState] = useState<ProductDetailLoadState>(
    product?.rehydratedDetails ? 'loaded' : 'idle',
  )
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null)
  const addedTimeoutRef = useRef<number | null>(null)
  const addSelectedOfferRef = useRef<(() => Promise<void>) | null>(null)
  const touchStartRef = useRef<{ x: number; y: number } | null>(null)
  const modalDockRef = useRef<HTMLDivElement | null>(null)
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const returnFocusRef = useRef<HTMLElement | null>(null)
  const settledSelectionKeyRef = useRef<string | null>(null)
  const settledSelectionProductIdRef = useRef<string | null>(product?.id ?? null)
  const deliveryCountryCode = deliveryLocations[0]?.code ?? null

  useEffect(() => {
    setMessages([])
    setSelectedMediaUrl(null)
    setThumbnailPage(0)
    setZoomImageUrl(null)
    setMerchantDetails(null)
    setDetailLoadState('idle')
    setDetailLoadError(null)
    settledSelectionKeyRef.current = null
    settledSelectionProductIdRef.current = product?.id ?? null
  }, [product?.id])

  useEffect(() => {
    const details = product?.rehydratedDetails
    if (!details) return
    setMerchantDetails((current) => mergeRehydratedProductDetails(current, details))
    setDetailLoadState('loaded')
    setDetailLoadError(null)
  }, [product?.id, product?.rehydratedDetails])

  useEffect(() => {
    if (!refreshingSavedOffers) return
    setMerchantDetails((current) => savedProductDetailsRefreshShell(current))
  }, [refreshingSavedOffers, product?.id])

  useEffect(() => {
    setAdded(false)
    setAdding(false)
    setAddError(null)
    setPurchaseSelection(null)
  }, [canonicalProductKey, product?.canonicalProduct?.recommendedOfferKey, product?.id])

  const handlePurchaseSelection = useCallback(
    (selection: ProductPurchaseSelection) => {
      setPurchaseSelection(selection)
      if (settledSelectionProductIdRef.current !== product?.id) {
        settledSelectionProductIdRef.current = product?.id ?? null
        settledSelectionKeyRef.current = null
      }
      if (selection.loading || !selection.offerKey || !selection.details) {
        return
      }
      const nextSelectionKey = purchaseSelectionKey(selection)
      const previousSelectionKey = settledSelectionKeyRef.current
      settledSelectionKeyRef.current = nextSelectionKey
      // Initial hydration establishes the default selection without replacing the image the user
      // opened. A later settled selection represents an explicit merchant/variant choice.
      if (previousSelectionKey === null || previousSelectionKey === nextSelectionKey) {
        return
      }
      const variantImage = findSelectedVariant(
        selection.details.variants,
        selection.selectedVariantId,
        selection.selectedOptions,
      )?.imageUrl
      setSelectedMediaUrl(variantImage ?? null)
    },
    [product?.id],
  )

  useEffect(() => {
    if (!merchantDetailMerchantId || !merchantDetailProductId) {
      return
    }
    const controller = new AbortController()
    setDetailLoadState('loading')
    setDetailLoadError(null)
    const language =
      typeof window === 'undefined' ? null : window.navigator.language.split('-')[0] || null
    getMerchantProductDetails({
      merchantId: merchantDetailMerchantId,
      productId: merchantDetailProductId,
      addressCountry: deliveryCountryCode,
      language,
      signal: controller.signal,
      expectedUserId: userId,
    })
      .then((details) => {
        if (controller.signal.aborted) {
          return
        }
        setMerchantDetails((current) => mergeRehydratedProductDetails(current, details))
        setDetailLoadState('loaded')
      })
      .catch(() => {
        if (controller.signal.aborted) {
          return
        }
        setDetailLoadState('error')
        setDetailLoadError('Latest product details are unavailable right now.')
      })
    return () => controller.abort()
  }, [deliveryCountryCode, merchantDetailMerchantId, merchantDetailProductId, product?.id, userId])

  useEffect(
    () => () => {
      if (addedTimeoutRef.current !== null) {
        window.clearTimeout(addedTimeoutRef.current)
      }
    },
    [],
  )

  useEffect(() => {
    returnFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    window.setTimeout(() => dialogRef.current?.focus(), 0)
    return () => returnFocusRef.current?.focus()
  }, [])

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
      if (event.key === 'Tab' && dialogRef.current) {
        const focusable = Array.from(
          dialogRef.current.querySelectorAll<HTMLElement>(
            'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
          ),
        ).filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true')
        if (
          containModalTabFocus(
            focusable,
            document.activeElement instanceof HTMLElement ? document.activeElement : null,
            event.shiftKey,
            dialogRef.current,
          )
        ) {
          event.preventDefault()
        }
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
  const visibleOffers = refreshingSavedOffers ? [] : offers.length > 0 ? offers : product.offers
  const activeMerchantDetails = purchaseSelection?.details ?? merchantDetails
  const selectedPurchaseVariant = activeMerchantDetails
    ? findSelectedVariant(
        activeMerchantDetails.variants,
        purchaseSelection?.selectedVariantId ?? activeMerchantDetails.selectedVariantId,
        purchaseSelection?.selectedOptions ?? activeMerchantDetails.selectedOptions,
      )
    : null
  const actionProduct = purchaseSelection?.actionProduct ?? product
  const modalMedia = mergeProductMedia(actionProduct, activeMerchantDetails)
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
    product.imageUrl ??
    selectedPurchaseVariant?.imageUrl ??
    activeMerchantDetails?.selectedVariantImageUrl ??
    activeMerchantDetails?.imageUrl
  const curatorTake = productCuratedTake(product, preferences)
  const curatorAdvantages = productCuratedAdvantages(product, preferences)
  const curatorTradeoffs = productCuratedTradeoffs(product, preferences)
  const hasPreferenceMatches = product.satisfies.length > 0 || product.misses.length > 0
  const detailDescription = stripHtml(
    activeMerchantDetails?.description || product.detailDescription || '',
  )
  const productCategory = displayProductCategoryValue(product.category)
  const productMeta = [product.brand.trim(), productCategory].filter(Boolean).join(' · ')
  const merchantDetailCategories = cleanCategoryValues(
    activeMerchantDetails?.categories.map((category) => category?.value),
  )
  const detailCategories =
    merchantDetailCategories.length > 0
      ? merchantDetailCategories
      : cleanCategoryValues(product.catalogCategories?.map((category) => category.value))
  const detailMessages = messageRows(activeMerchantDetails?.messages)
  const selectedVariantPriceAmount =
    selectedPurchaseVariant?.priceAmount ?? activeMerchantDetails?.selectedVariantPriceAmount
  const selectedVariantPriceCurrency =
    selectedPurchaseVariant?.priceCurrency ?? activeMerchantDetails?.selectedVariantPriceCurrency
  const selectedVariantListPriceAmount =
    selectedPurchaseVariant?.listPriceAmount ??
    activeMerchantDetails?.selectedVariantListPriceAmount
  const selectedVariantListPriceCurrency =
    selectedPurchaseVariant?.listPriceCurrency ??
    activeMerchantDetails?.selectedVariantListPriceCurrency
  const selectedVariantPrice = detailMoney(selectedVariantPriceAmount, selectedVariantPriceCurrency)
  const selectedVariantSavings = variantSavings(
    selectedVariantPriceAmount,
    selectedVariantPriceCurrency,
    selectedVariantListPriceAmount,
    selectedVariantListPriceCurrency,
  )
  const selectedVariantListPrice = selectedVariantSavings
    ? detailMoney(selectedVariantListPriceAmount, selectedVariantListPriceCurrency)
    : null
  const selectedVariantAvailability =
    selectedPurchaseVariant?.available ??
    activeMerchantDetails?.selectedVariantAvailable ??
    product.selectedVariantAvailable
  const hasProductDetails =
    detailLoadState === 'loading' ||
    Boolean(detailDescription) ||
    detailMessages.length > 0 ||
    detailCategories.length > 0 ||
    detailLoadState === 'error'
  const showProductDetailLoading =
    detailLoadState === 'loading' &&
    !detailDescription &&
    detailMessages.length === 0 &&
    detailCategories.length === 0
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
  const bestAvailableOffer = bestOffer(product, deliveryLocations)
  const durableSavedOffer = product.offers.find((offer) => offer.offerKey?.trim()) ?? null
  const selectedOffer = saved ? (durableSavedOffer ?? bestAvailableOffer) : bestAvailableOffer
  const isCanonicalProduct = Boolean(product.canonicalProduct) && !refreshingSavedOffers
  const hasVariantSelector =
    !refreshingSavedOffers &&
    (isCanonicalProduct || Boolean(product.rehydratedDetails && durableSavedOffer))
  const selectedServerOfferKey = hasVariantSelector
    ? (purchaseSelection?.offerKey ?? null)
    : selectedOffer?.offerKey?.trim() || null
  const saveDisabled = savePending || (!saved && hasVariantSelector && !selectedServerOfferKey)
  const canAddToCart =
    !refreshingSavedOffers &&
    (hasVariantSelector
      ? Boolean(purchaseSelection?.canAdd && selectedServerOfferKey && onAddOfferKey)
      : saved
        ? Boolean(
            selectedOffer &&
            selectedOffer.available !== false &&
            selectedServerOfferKey &&
            onAddOfferKey,
          )
        : Boolean(
            selectedOffer &&
            selectedOffer.available !== false &&
            ((selectedServerOfferKey && onAddOfferKey) ||
              offerCartable(selectedOffer) ||
              canResolveCartOffer(product, selectedOffer)),
          ))
  const addDisabled = adding || !canAddToCart
  const addButtonLabel = added
    ? 'Added to cart'
    : adding
      ? 'Adding...'
      : refreshingSavedOffers
        ? 'Loading offers…'
        : hasVariantSelector && purchaseSelection?.loading !== false
          ? 'Loading offers…'
          : hasVariantSelector && !canAddToCart
            ? 'Unavailable'
            : !selectedOffer || selectedOffer.available === false
              ? 'Unavailable'
              : canAddToCart
                ? 'Add to cart'
                : 'Checkout unavailable'
  const fallbackPriceAmount = productPriceFrom(actionProduct, deliveryLocations)
  const fallbackPrice =
    fallbackPriceAmount === null
      ? null
      : detailMoney(String(fallbackPriceAmount), actionProduct.priceCurrency)
  const displayedPrice = selectedVariantPrice ?? fallbackPrice ?? 'Price unavailable'
  const priceLabel = selectedVariantSavings
    ? 'Sale price'
    : selectedVariantPrice
      ? 'Current price'
      : fallbackPrice
        ? 'Price from'
        : 'Price'
  const merchantCount = product.canonicalProduct
    ? product.merchants
    : productMerchantCount(product, deliveryLocations)
  const merchantCountLabel = `${merchantCount} ${merchantCount === 1 ? 'store' : 'stores'}`
  const stockPending =
    refreshingSavedOffers ||
    purchaseSelection?.loading === true ||
    (hasVariantSelector && !purchaseSelection && !activeMerchantDetails)
  const stockLabel = stockPending
    ? 'Checking stock…'
    : availabilityLabel(selectedVariantAvailability)
  const stockClass = stockPending ? 'unknown' : availabilityClass(selectedVariantAvailability)
  const addSelectedOffer = async () => {
    if (!canAddToCart || adding) {
      return
    }
    if (selectedServerOfferKey && onAddOfferKey) {
      setAdding(true)
      setAddError(null)
      try {
        const addedToCart = await onAddOfferKey(actionProduct, selectedServerOfferKey)
        if (!addedToCart) {
          setAddError('Could not add this exact merchant offer to cart.')
          return
        }
        setAdded(true)
        if (addedTimeoutRef.current !== null) window.clearTimeout(addedTimeoutRef.current)
        addedTimeoutRef.current = window.setTimeout(() => {
          setAdded(false)
          addedTimeoutRef.current = null
        }, 1600)
      } catch {
        setAddError('Could not add this exact merchant offer to cart.')
      } finally {
        setAdding(false)
      }
      return
    }
    if (!selectedOffer) {
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
        tabIndex={-1}
        ref={dialogRef}
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
                label={`${(productCategory ?? 'product').toLowerCase()} shot`}
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
            {productMeta ? <div className="mt-mono mt-card-brand">{productMeta}</div> : null}
            <h2 className="mt-modal-name">{product.name}</h2>
            <section
              className="mt-modal-price-panel"
              aria-label="Price and availability"
              aria-live="polite"
              aria-atomic="true"
            >
              <div className="mt-modal-price-main">
                <span className="mt-mono mt-modal-price-label">{priceLabel}</span>
                <div className="mt-modal-price-values">
                  <strong className="mt-modal-price">{displayedPrice}</strong>
                  {selectedVariantListPrice ? (
                    <span className="mt-modal-price-was">
                      Was <s>{selectedVariantListPrice}</s>
                    </span>
                  ) : null}
                </div>
                {selectedVariantSavings ? (
                  <span className="mt-modal-price-saving">
                    Save {selectedVariantSavings.amount}
                    {selectedVariantSavings.percent > 0
                      ? ` (${selectedVariantSavings.percent}%)`
                      : ''}
                  </span>
                ) : null}
              </div>
              <div className="mt-modal-price-meta">
                <span className="mt-mono">{merchantCountLabel}</span>
                <span className={`mt-modal-price-stock ${stockClass}`}>
                  <span className="mt-modal-price-stock-dot" aria-hidden />
                  {stockLabel}
                </span>
              </div>
            </section>
            <InventorySignalBadge product={actionProduct} />
            {hasVariantSelector ? (
              <GroupedOfferSelector
                product={product}
                researchQuery={researchQuery}
                userId={userId}
                onSelectionChange={handlePurchaseSelection}
              />
            ) : null}
            <div className="mt-modal-actions">
              <button
                className={`mt-act mt-act-icon ${saved ? 'on' : ''}`}
                type="button"
                onClick={() => onToggleSave(saved ? product : actionProduct)}
                disabled={saveDisabled}
                aria-label={
                  savePending
                    ? 'Saving saved product'
                    : saved
                      ? 'Saved'
                      : saveDisabled
                        ? 'Choose an exact item before saving'
                        : 'Save'
                }
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
            {saved && purchaseSelection?.changedFromSaved && selectedServerOfferKey ? (
              <button
                className="mt-act mt-act-ghost mt-update-saved-choice"
                type="button"
                disabled={savePending || !onUpdateSavedChoice}
                onClick={() => onUpdateSavedChoice?.(actionProduct, selectedServerOfferKey)}
              >
                {savePending ? 'Updating saved choice…' : 'Update saved choice'}
              </button>
            ) : null}
            {addError ? <div className="mt-cart-inline-error">{addError}</div> : null}
            {refreshingSavedOffers && !addError ? (
              <div className="mt-cart-inline-error muted" role="status">
                Loading current merchant offers…
              </div>
            ) : !isCanonicalProduct && !selectedOffer && !addError ? (
              <div className="mt-cart-inline-error muted">
                Current merchant offers are unavailable. Close and open this product to retry.
              </div>
            ) : !isCanonicalProduct && !canAddToCart && !addError ? (
              <div className="mt-cart-inline-error muted">
                This offer is not available for merchant checkout.
              </div>
            ) : null}
          </div>

          <div className="mt-modal-right-shell">
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
                  {detailCategories.length > 0 ? (
                    <div className="mt-product-category-strip">
                      {detailCategories.map((category) => (
                        <span className="mt-product-category-chip" key={category}>
                          {category}
                        </span>
                      ))}
                    </div>
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

              <ProductReviewsPanel product={product} userId={userId} />

              {!hasVariantSelector ? (
                <section className="mt-block">
                  <div className="mt-block-label mt-mono">Available offers</div>
                  <div className="mt-offers">
                    {visibleOffers.length === 0 ? (
                      <div className="mt-grouped-recovery" role="status">
                        <p>
                          {refreshingSavedOffers
                            ? 'Loading current offers…'
                            : 'Current offers are unavailable.'}
                        </p>
                        {!refreshingSavedOffers && onRefreshProduct ? (
                          <button
                            type="button"
                            className="mt-act mt-act-ghost"
                            onClick={() => onRefreshProduct(product)}
                          >
                            Try again
                          </button>
                        ) : null}
                      </div>
                    ) : (
                      visibleOffers.map((offer, index) => (
                        <div
                          className={`mt-offer ${index === 0 ? 'best' : ''}`}
                          key={offer.offerKey ?? `${offer.merchant}-${index}`}
                        >
                          <div className="mt-offer-merch">
                            {offer.merchant}
                            {index === 0 ? (
                              <span className="mt-mono mt-offer-tag">best</span>
                            ) : null}
                          </div>
                          <div className="mt-offer-right">
                            <span className="mt-mono mt-offer-deliv">{offer.delivery}</span>
                            <span className="mt-offer-price">
                              {money(offer.price, offer.priceCurrency)}
                            </span>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </section>
              ) : null}
            </div>

            <div className="mt-modal-dock" ref={modalDockRef}>
              <AskThread messages={messages} />
              <AskComposer
                placeholder={`Ask about ${product.name}...`}
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
