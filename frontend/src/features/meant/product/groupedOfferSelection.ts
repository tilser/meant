import type { CanonicalOfferProfile, CanonicalProductDetailProfile } from '../../../lib/apiClient'

export interface OfferSelectionState {
  selectedOfferKey: string
  recommendedOfferKey: string
  selectedOfferMissing: boolean
  userOverrodeDefault: boolean
}

export function initialOfferSelection(detail: CanonicalProductDetailProfile): OfferSelectionState {
  return {
    selectedOfferKey: detail.selectedOfferKey,
    recommendedOfferKey: detail.recommendedOfferKey,
    selectedOfferMissing: !detail.product.offers.some(
      (offer) => offer.key === detail.selectedOfferKey,
    ),
    userOverrodeDefault: detail.selectedOfferKey !== detail.recommendedOfferKey,
  }
}

export function selectOffer(state: OfferSelectionState, offerKey: string): OfferSelectionState {
  return {
    ...state,
    selectedOfferKey: offerKey,
    selectedOfferMissing: false,
    userOverrodeDefault: offerKey !== state.recommendedOfferKey,
  }
}

export function reconcileOfferSelection(
  state: OfferSelectionState,
  detail: CanonicalProductDetailProfile,
): OfferSelectionState {
  const selectedStillExists = detail.product.offers.some(
    (offer) => offer.key === state.selectedOfferKey,
  )
  return {
    selectedOfferKey: state.selectedOfferKey,
    recommendedOfferKey: detail.recommendedOfferKey,
    selectedOfferMissing: !selectedStillExists,
    userOverrodeDefault: state.selectedOfferKey !== detail.recommendedOfferKey,
  }
}

function freshnessIsStale(freshUntil: string | undefined, now: number): boolean {
  if (!freshUntil) return false
  const timestamp = Date.parse(freshUntil)
  return Number.isFinite(timestamp) && timestamp <= now
}

export function offerNeedsRefresh(offer: CanonicalOfferProfile, now = Date.now()): boolean {
  const state = offer.commercialState
  return (
    offer.availability.status === 'UNKNOWN' ||
    state.authority !== 'REHYDRATED_CURRENT' ||
    state.rehydrationStatus === 'DEGRADED' ||
    state.rehydrationStatus === 'UNAVAILABLE' ||
    Boolean(state.degradation) ||
    freshnessIsStale(state.priceFreshness?.freshUntil, now) ||
    freshnessIsStale(state.availabilityFreshness?.freshUntil, now) ||
    freshnessIsStale(state.deliveryFreshness?.freshUntil, now)
  )
}

export function offerCanSelect(offer: CanonicalOfferProfile): boolean {
  return !['OUT_OF_STOCK', 'DISCONTINUED', 'UNKNOWN'].includes(offer.availability.status)
}
