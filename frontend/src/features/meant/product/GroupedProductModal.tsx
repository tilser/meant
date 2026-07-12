import { useEffect, useMemo, useRef, useState } from 'react'

import {
  ApiError,
  getCanonicalProductDetail,
  type CanonicalOfferProfile,
  type CanonicalProductDetailProfile,
} from '../../../lib/apiClient'
import type { Product } from '../types'
import { minorUnitsToMajor, money } from '../utils'
import { trackCommerceEvent } from './commerceAnalytics'
import {
  offerCanAdd,
  offerCanSelect,
  offerNeedsRefresh,
  reconcileOfferSelection,
  selectOffer,
  type OfferSelectionState,
} from './groupedOfferSelection'
import { selectedOfferCartFailure, type SelectedOfferCartFailure } from './selectedOfferCartFailure'

type DetailStatus = 'loading' | 'loaded' | 'not-found' | 'error'

function offerPrice(offer: CanonicalOfferProfile): string {
  return offer.price
    ? money(minorUnitsToMajor(offer.price.minorUnits, offer.price.currency), offer.price.currency)
    : 'Price unavailable'
}

function deliveryText(offer: CanonicalOfferProfile): string {
  const delivery = offer.delivery[0]
  if (!delivery) return 'Shipping and delivery calculated by merchant'
  const range =
    delivery.minimumBusinessDays != null || delivery.maximumBusinessDays != null
      ? `${delivery.minimumBusinessDays ?? delivery.maximumBusinessDays}-${delivery.maximumBusinessDays ?? delivery.minimumBusinessDays} business days`
      : 'Timing not supplied'
  const cost = delivery.cost
    ? money(
        minorUnitsToMajor(delivery.cost.minorUnits, delivery.cost.currency),
        delivery.cost.currency,
      )
    : 'cost calculated by merchant'
  return `${delivery.method.toLowerCase()} · ${range} · ${cost}`
}

function checkoutText(offer: CanonicalOfferProfile): string {
  switch (offer.checkoutExperience) {
    case 'MEANT_MANAGED':
      return 'Checkout managed in Meant'
    case 'PROVIDER_HANDOFF':
      return 'Checkout continues with this merchant'
    default:
      return 'Checkout experience confirmed when adding'
  }
}

function offerExplanation(offer: CanonicalOfferProfile): string {
  const explanation = offer.rankingExplanation
  if (!explanation) return 'Offer order uses the available price, availability, and delivery facts.'
  const knownFeatures = explanation.features
    .filter((feature) => feature.availability === 'AVAILABLE')
    .map((feature) => feature.name.toLowerCase().replaceAll('_', ' '))
    .slice(0, 3)
  return knownFeatures.length > 0
    ? `Ranked #${explanation.finalRank} using ${knownFeatures.join(', ')}.`
    : `Ranked #${explanation.finalRank}; some comparison facts are not yet known.`
}

function commercialLabels(offer: CanonicalOfferProfile): string[] {
  const state = offer.commercialState
  return [
    state.authority === 'REHYDRATED_CURRENT' ? 'Current merchant facts' : 'Discovery snapshot',
    state.rehydrationStatus ? state.rehydrationStatus.toLowerCase().replaceAll('_', ' ') : null,
    state.degradation ? `Limited: ${state.degradation.toLowerCase().replaceAll('_', ' ')}` : null,
  ].filter((label): label is string => Boolean(label))
}

function OfferChoice({
  offer,
  recommended,
  selected,
  onSelect,
  onRefresh,
}: Readonly<{
  offer: CanonicalOfferProfile
  recommended: boolean
  selected: boolean
  onSelect: () => void
  onRefresh: () => void
}>) {
  const unavailable = !offerCanSelect(offer)
  const needsRefresh = offerNeedsRefresh(offer)
  return (
    <div
      className={`mt-grouped-offer ${selected ? 'selected' : ''} ${unavailable ? 'unavailable' : ''}`}
    >
      <label className="mt-grouped-offer-choice">
        <input
          type="radio"
          name="grouped-offer"
          checked={selected}
          onChange={onSelect}
          disabled={unavailable}
        />
        <span className="mt-grouped-offer-main">
          <span className="mt-grouped-offer-head">
            <strong>{offer.merchantName?.trim() || 'Merchant'}</strong>
            <span className="mt-grouped-offer-price">{offerPrice(offer)}</span>
          </span>
          <span className="mt-grouped-offer-meta">
            {offer.variantTitle || 'Standard offer'} ·{' '}
            {offer.availability.status.toLowerCase().replaceAll('_', ' ')}
          </span>
          <span className="mt-grouped-offer-meta">{deliveryText(offer)}</span>
          <span className="mt-grouped-offer-meta">{checkoutText(offer)}</span>
          <span className="mt-grouped-offer-explanation">{offerExplanation(offer)}</span>
        </span>
      </label>
      <div className="mt-grouped-offer-flags">
        {recommended ? <span className="mt-grouped-pill best">Recommended</span> : null}
        {commercialLabels(offer).map((label) => (
          <span className={`mt-grouped-pill ${needsRefresh ? 'warning' : ''}`} key={label}>
            {label}
          </span>
        ))}
        {needsRefresh ? (
          <button type="button" className="mt-grouped-refresh" onClick={onRefresh}>
            Refresh offer
          </button>
        ) : null}
      </div>
    </div>
  )
}

export function GroupedOfferSelector({
  product,
  onResearch,
  onAddOfferKey,
  onSelectionChange,
}: Readonly<{
  product: Product
  onResearch: (query: string) => void
  onAddOfferKey?: (offerKey: string) => Promise<boolean>
  onSelectionChange?: (selection: { offerKey: string | null; canAdd: boolean }) => void
}>) {
  const recommendedOfferKey = product.canonicalProduct?.recommendedOfferKey ?? ''
  const [detail, setDetail] = useState<CanonicalProductDetailProfile | null>(null)
  const [selection, setSelection] = useState<OfferSelectionState>(() => ({
    selectedOfferKey: recommendedOfferKey,
    recommendedOfferKey,
    selectedOfferMissing: false,
    userOverrodeDefault: false,
  }))
  const [status, setStatus] = useState<DetailStatus>('loading')
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [adding, setAdding] = useState(false)
  const [addMessage, setAddMessage] = useState<string | null>(null)
  const [addFailure, setAddFailure] = useState<SelectedOfferCartFailure | null>(null)
  const requestRef = useRef(0)
  const viewedProductKeyRef = useRef<string | null>(null)
  const viewedOfferKeysRef = useRef(new Set<string>())
  const canonicalKey = product.canonicalProduct?.key ?? product.id
  const selectedOfferKey = selection.selectedOfferKey

  useEffect(() => {
    const requestId = requestRef.current + 1
    requestRef.current = requestId
    const controller = new AbortController()
    setStatus('loading')
    setAddMessage(null)
    setAddFailure(null)
    getCanonicalProductDetail({
      canonicalProductKey: canonicalKey,
      selectedOfferKey,
      signal: controller.signal,
    })
      .then((nextDetail) => {
        if (controller.signal.aborted || requestRef.current !== requestId) return
        setDetail(nextDetail)
        setSelection((current) => reconcileOfferSelection(current, nextDetail))
        setStatus('loaded')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || requestRef.current !== requestId) return
        setStatus(error instanceof ApiError && error.status === 404 ? 'not-found' : 'error')
      })
    return () => controller.abort()
  }, [canonicalKey, refreshVersion, selectedOfferKey])

  useEffect(() => {
    if (!detail) return
    if (viewedProductKeyRef.current !== canonicalKey) {
      viewedProductKeyRef.current = canonicalKey
      viewedOfferKeysRef.current.clear()
      trackCommerceEvent('product_view', {
        canonicalProductKey: canonicalKey,
        offerCount: detail.product.offers.length,
      })
    }
    detail.product.offers.forEach((offer, index) => {
      if (viewedOfferKeysRef.current.has(offer.key)) return
      viewedOfferKeysRef.current.add(offer.key)
      trackCommerceEvent('offer_view', {
        canonicalProductKey: canonicalKey,
        offerKey: offer.key,
        offerRank: index + 1,
        offerCount: detail.product.offers.length,
        checkoutExperience: offer.checkoutExperience,
        availability: offer.availability.status,
      })
    })
  }, [canonicalKey, detail])

  const selectedOffer = useMemo(
    () => detail?.product.offers.find((offer) => offer.key === selection.selectedOfferKey) ?? null,
    [detail, selection.selectedOfferKey],
  )
  useEffect(() => {
    onSelectionChange?.({
      offerKey: selectedOffer?.key ?? null,
      canAdd: status === 'loaded' && Boolean(selectedOffer && offerCanAdd(selectedOffer)),
    })
  }, [onSelectionChange, selectedOffer, status])
  const select = (offer: CanonicalOfferProfile) => {
    if (offer.key === selection.selectedOfferKey) return
    const next = selectOffer(selection, offer.key)
    setSelection(next)
    setAddMessage(null)
    setAddFailure(null)
    trackCommerceEvent('offer_selection', {
      canonicalProductKey: canonicalKey,
      offerKey: offer.key,
      offerRank: offer.rankingExplanation?.finalRank,
      checkoutExperience: offer.checkoutExperience,
      availability: offer.availability.status,
    })
    if (!selection.userOverrodeDefault && next.userOverrodeDefault) {
      trackCommerceEvent('default_offer_override', {
        canonicalProductKey: canonicalKey,
        offerKey: offer.key,
        offerRank: offer.rankingExplanation?.finalRank,
      })
    }
  }
  const addSelected = async () => {
    if (!selectedOffer || !offerCanAdd(selectedOffer) || adding) return
    if (!onAddOfferKey) {
      setAddMessage('Cart support for exact offers is being updated. Your selected offer was kept.')
      return
    }
    setAdding(true)
    setAddMessage(null)
    setAddFailure(null)
    trackCommerceEvent('add_to_cart', {
      canonicalProductKey: canonicalKey,
      offerKey: selectedOffer.key,
      result: 'attempted',
    })
    try {
      const added = await onAddOfferKey(selectedOffer.key)
      setAddMessage(added ? 'Added to this merchant cart.' : 'Could not add this exact offer.')
      trackCommerceEvent('add_to_cart', {
        canonicalProductKey: canonicalKey,
        offerKey: selectedOffer.key,
        result: added ? 'succeeded' : 'failed',
      })
    } catch (error: unknown) {
      const failure = selectedOfferCartFailure(error)
      setAddFailure(failure)
      setAddMessage(failure.message)
      trackCommerceEvent('add_to_cart', {
        canonicalProductKey: canonicalKey,
        offerKey: selectedOffer.key,
        result: 'failed',
      })
    } finally {
      setAdding(false)
    }
  }

  return (
    <div className="mt-grouped-offer-selector">
      {status === 'loading' ? (
        <div className="mt-grouped-loading" role="status" aria-live="polite">
          <span className="mt-grouped-skeleton wide" />
          <span className="mt-grouped-skeleton" />
          <span>Refreshing offers without changing your selection…</span>
        </div>
      ) : null}
      {status === 'not-found' ? (
        <div className="mt-grouped-recovery" role="alert">
          <h3>This product session has expired</h3>
          <p>Search again to get fresh product and offer keys.</p>
          <button
            type="button"
            className="mt-act mt-act-primary"
            onClick={() => onResearch(product.name)}
          >
            Re-search this product
          </button>
        </div>
      ) : null}
      {status === 'error' ? (
        <div className="mt-grouped-recovery" role="alert">
          <h3>Offers could not be loaded</h3>
          <p>Your prior selection has not been replaced.</p>
          <button
            type="button"
            className="mt-act mt-act-ghost"
            onClick={() => setRefreshVersion((value) => value + 1)}
          >
            Try again
          </button>
        </div>
      ) : null}

      {detail && status === 'loaded' ? (
        <section className="mt-grouped-offers" aria-labelledby="grouped-offers-title">
          <div className="mt-grouped-offers-heading">
            <div>
              <h3 id="grouped-offers-title">Choose one merchant offer</h3>
              <p>Each merchant is a separate cart, checkout, charge, and transaction.</p>
            </div>
            <button
              type="button"
              className="mt-grouped-refresh"
              onClick={() => setRefreshVersion((value) => value + 1)}
            >
              Refresh all offers
            </button>
          </div>
          {detail.sourceStates.some((source) => source.degraded) ? (
            <div className="mt-grouped-degraded" role="status">
              Some sources are limited right now. Healthy merchant offers remain available.
            </div>
          ) : null}
          {selection.selectedOfferMissing ? (
            <div className="mt-grouped-degraded" role="alert">
              Your selected offer is no longer returned. Meant did not switch merchants; choose a
              new offer explicitly.
            </div>
          ) : null}
          <div className="mt-grouped-offer-list">
            {detail.product.offers.length === 0 ? (
              <div className="mt-grouped-recovery" role="status">
                No eligible merchant offers are available. Re-search for a fresh product session.
              </div>
            ) : null}
            {detail.product.offers.map((offer) => (
              <OfferChoice
                key={offer.key}
                offer={offer}
                selected={offer.key === selection.selectedOfferKey}
                recommended={offer.key === detail.recommendedOfferKey}
                onSelect={() => select(offer)}
                onRefresh={() => setRefreshVersion((value) => value + 1)}
              />
            ))}
          </div>
          <div className="mt-grouped-cart-bar">
            <span>
              {selectedOffer
                ? `${selectedOffer.merchantName || 'Merchant'} · ${offerPrice(selectedOffer)}`
                : 'Choose an available offer'}
            </span>
            <button
              type="button"
              className="mt-act mt-act-primary"
              disabled={!selectedOffer || !offerCanAdd(selectedOffer) || adding}
              onClick={() => void addSelected()}
            >
              {adding ? 'Adding exact offer…' : 'Add selected offer to cart'}
            </button>
          </div>
          {addMessage ? (
            <div className="mt-cart-inline-error" role="status">
              {addMessage}
            </div>
          ) : null}
          {addFailure ? (
            <div className="mt-grouped-recovery-actions">
              {addFailure.refresh ? (
                <button
                  type="button"
                  className="mt-grouped-refresh"
                  onClick={() => setRefreshVersion((value) => value + 1)}
                >
                  Refresh selected offer
                </button>
              ) : null}
              {addFailure.research ? (
                <button
                  type="button"
                  className="mt-grouped-refresh"
                  onClick={() => onResearch(product.name)}
                >
                  Re-search this product
                </button>
              ) : null}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  )
}
