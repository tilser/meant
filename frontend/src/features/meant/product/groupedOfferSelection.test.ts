import { describe, expect, test } from 'bun:test'

import type { CanonicalOfferProfile, CanonicalProductDetailProfile } from '../../../lib/apiClient'
import {
  initialOfferSelection,
  offerCanSelect,
  offerNeedsRefresh,
  reconcileOfferSelection,
  selectOffer,
} from './groupedOfferSelection'

function offer(
  key: string,
  input: Partial<CanonicalOfferProfile['commercialState']> = {},
): CanonicalOfferProfile {
  return {
    key,
    identity: {
      provider: 'test',
      merchantScope: {
        type: 'EXTERNAL_MERCHANT',
        externalMerchantIdentity: { type: 'MERCHANT', value: `merchant-${key}` },
      },
      externalProductIdentity: { type: 'PRODUCT', value: `product-${key}` },
      components: [],
    },
    merchantName: `Merchant ${key}`,
    availability: { status: 'IN_STOCK' },
    delivery: [],
    selectedOptions: [],
    checkoutExperience: 'MEANT_MANAGED',
    commercialState: {
      authority: 'REHYDRATED_CURRENT',
      rehydrationStatus: 'FRESH',
      ...input,
    },
    provenance: [],
  }
}

function detail(offerKeys: readonly string[], selected = offerKeys[0] ?? 'missing') {
  const offers = offerKeys.map((key) => offer(key))
  return {
    product: {
      key: 'canonical-1',
      media: [],
      attributes: [],
      materials: [],
      certifications: [],
      attribution: [],
      identityEvidence: [],
      provenance: [],
      recommendedOfferKey: offerKeys[0] ?? 'recommended-missing',
      offers,
    },
    recommendedOfferKey: offerKeys[0] ?? 'recommended-missing',
    selectedOfferKey: selected,
    sourceStates: [],
  } satisfies CanonicalProductDetailProfile
}

describe('grouped offer selection', () => {
  test('starts from the server-selected recommended offer', () => {
    expect(initialOfferSelection(detail(['offer-a', 'offer-b']))).toEqual({
      selectedOfferKey: 'offer-a',
      recommendedOfferKey: 'offer-a',
      selectedOfferMissing: false,
      userOverrodeDefault: false,
    })
  })

  test('preserves an exact user override when refreshed offers reorder', () => {
    const selected = selectOffer(initialOfferSelection(detail(['offer-a', 'offer-b'])), 'offer-b')
    const refreshed = detail(['offer-a', 'offer-b'])
    refreshed.product.offers.reverse()

    expect(reconcileOfferSelection(selected, refreshed)).toMatchObject({
      selectedOfferKey: 'offer-b',
      selectedOfferMissing: false,
      userOverrodeDefault: true,
    })
  })

  test('does not silently fall back when the selected offer disappears', () => {
    const selected = selectOffer(initialOfferSelection(detail(['offer-a', 'offer-b'])), 'offer-b')

    expect(reconcileOfferSelection(selected, detail(['offer-a']))).toMatchObject({
      selectedOfferKey: 'offer-b',
      selectedOfferMissing: true,
    })
  })

  test('requires refresh for observations, degradation, and expired freshness', () => {
    expect(offerNeedsRefresh(offer('observed', { authority: 'DISCOVERY_OBSERVATION' }))).toBe(true)
    expect(offerNeedsRefresh(offer('degraded', { rehydrationStatus: 'DEGRADED' }))).toBe(true)
    expect(
      offerNeedsRefresh(
        offer('expired', {
          priceFreshness: {
            observedAt: '2026-01-01T00:00:00Z',
            freshUntil: '2026-01-02T00:00:00Z',
          },
        }),
        Date.parse('2026-01-03T00:00:00Z'),
      ),
    ).toBe(true)
  })

  test('allows stale in-stock observations to be submitted for server validation', () => {
    expect(offerCanSelect(offer('current'))).toBe(true)
    expect(offerCanSelect(offer('stale', { authority: 'DISCOVERY_OBSERVATION' }))).toBe(true)
    expect(offerCanSelect({ ...offer('sold-out'), availability: { status: 'OUT_OF_STOCK' } })).toBe(
      false,
    )
  })

  test('treats unknown availability as refresh-required and non-addable', () => {
    const unknown = { ...offer('unknown'), availability: { status: 'UNKNOWN' as const } }

    expect(offerNeedsRefresh(unknown)).toBe(true)
    expect(offerCanSelect(unknown)).toBe(false)
  })
})
