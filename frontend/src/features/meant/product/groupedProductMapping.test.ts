import { describe, expect, test } from 'bun:test'

import type { CanonicalProductProfile } from '../../../lib/apiClient'
import { isRenderableSearchProduct } from '../chat/utils'
import { productPriceFrom } from '../utils'
import { productCuratedTake } from './productCuration'
import { productFromCanonical } from './groupedProductMapping'

function canonicalProduct(key: string): CanonicalProductProfile {
  return {
    key,
    title: `Product ${key}`,
    media: [],
    attributes: [{ name: 'brand', value: 'Shared brand' }],
    materials: [],
    certifications: [],
    attribution: [],
    identityEvidence: [],
    provenance: [],
    personalization: {
      whyMeantForYou: 'This looks relevant to your search based on the available product details.',
      matchedFilterIds: [],
      missedFilterIds: [],
    },
    recommendedOfferKey: `${key}-offer-b`,
    offers: ['a', 'b'].map((suffix, index) => ({
      key: `${key}-offer-${suffix}`,
      identity: {
        provider: 'test',
        merchantScope: {
          type: 'EXTERNAL_MERCHANT',
          externalMerchantIdentity: { type: 'MERCHANT', value: `${key}-merchant-${suffix}` },
        },
        externalProductIdentity: { type: 'PRODUCT', value: `${key}-product` },
        components: [],
      },
      merchantName: `Merchant ${suffix}`,
      price: { minorUnits: 1200 - index * 200, currency: 'USD' },
      availability: { status: 'IN_STOCK' },
      delivery: [],
      selectedOptions: [],
      checkoutExperience: 'MEANT_MANAGED',
      commercialState: { authority: 'REHYDRATED_CURRENT', rehydrationStatus: 'FRESH' },
      provenance: [],
    })),
  }
}

describe('grouped product card mapping', () => {
  test('maps exactly one existing view model per canonical product', () => {
    const canonical = [canonicalProduct('one'), canonicalProduct('two')]
    const mapped = canonical.map(productFromCanonical)

    expect(mapped.map((product) => product.id)).toEqual(['one', 'two'])
    expect(mapped[0]?.canonicalProduct).toBe(canonical[0])
    expect(mapped[0]?.merchants).toBe(2)
    expect(mapped[0]?.priceFrom).toBe(10)
  })

  test('keeps exact generated offer keys only in the canonical payload', () => {
    const mapped = productFromCanonical(canonicalProduct('one'))

    expect(mapped.canonicalProduct?.recommendedOfferKey).toBe('one-offer-b')
    expect(mapped.canonicalProduct?.offers.map((offer) => offer.key)).toEqual([
      'one-offer-a',
      'one-offer-b',
    ])
    expect(mapped.offers.every((offer) => !('offerKey' in offer))).toBe(true)
  })

  test('renders a valid grouped canonical product without media', () => {
    const mapped = productFromCanonical(canonicalProduct('no-media'))

    expect(mapped.media).toEqual([])
    expect(mapped.imageUrl).toBeNull()
    expect(isRenderableSearchProduct(mapped)).toBe(true)
  })

  test('preserves the legacy artwork requirement for non-canonical products', () => {
    const mapped = productFromCanonical(canonicalProduct('legacy-shape'))
    const legacy = { ...mapped, canonicalProduct: undefined }

    expect(isRenderableSearchProduct(legacy)).toBe(false)
  })

  test('compares grouped card prices only within the recommended offer currency', () => {
    const canonical = canonicalProduct('mixed')
    canonical.offers[0]!.price = { minorUnits: 100, currency: 'JPY' }
    canonical.offers[1]!.price = { minorUnits: 1000, currency: 'USD' }
    canonical.offers.push({
      ...canonical.offers[0]!,
      key: 'mixed-offer-usd-cheaper',
      price: { minorUnits: 900, currency: 'USD' },
    })

    const mapped = productFromCanonical(canonical)

    expect(mapped.priceFrom).toBe(9)
    expect(mapped.priceFromMinorUnits).toBe(900)
    expect(mapped.priceCurrency).toBe('USD')
    expect(productPriceFrom(mapped, [])).toBe(9)
  })

  test('uses the first ranked priced currency when the recommendation has no price', () => {
    const canonical = canonicalProduct('fallback-currency')
    canonical.offers[0]!.price = { minorUnits: 1234, currency: 'KWD' }
    canonical.offers[1]!.price = undefined
    canonical.offers.push({
      ...canonical.offers[0]!,
      key: 'fallback-currency-offer-jpy',
      price: { minorUnits: 100, currency: 'JPY' },
    })

    const mapped = productFromCanonical(canonical)

    expect(mapped.priceFrom).toBe(1.234)
    expect(mapped.priceFromMinorUnits).toBe(1234)
    expect(mapped.priceCurrency).toBe('KWD')
  })

  test('ties displayed price authority to the offer that supplied that price', () => {
    const canonical = canonicalProduct('authority')
    canonical.offers[0]!.price = { minorUnits: 800, currency: 'USD' }
    canonical.offers[0]!.commercialState.authority = 'DISCOVERY_OBSERVATION'
    canonical.offers[1]!.price = { minorUnits: 1000, currency: 'USD' }

    const mapped = productFromCanonical(canonical)

    expect(mapped.priceFrom).toBe(8)
    expect(mapped.commercialFactsAuthoritative).toBe(false)
  })

  test('maps canonical media and offer options into the original product detail model', () => {
    const canonical = canonicalProduct('detail')
    canonical.media = [
      {
        type: 'image',
        url: 'https://cdn.example/front.jpg',
        altText: 'Front',
        previewImageUrl: 'https://cdn.example/front.jpg',
      },
      {
        type: 'image',
        url: 'https://cdn.example/back.jpg',
        altText: 'Back',
        previewImageUrl: 'https://cdn.example/back.jpg',
      },
    ]
    canonical.description = 'Full product description'
    canonical.offers[0]!.selectedOptions = [
      { name: 'Size', value: 'M' },
      { name: 'Color', value: 'Black' },
    ]
    canonical.offers[1]!.selectedOptions = [
      { name: 'Size', value: 'L' },
      { name: 'Color', value: 'Black' },
    ]

    const mapped = productFromCanonical(canonical)

    expect(mapped.media?.map((item) => item.url)).toEqual([
      'https://cdn.example/front.jpg',
      'https://cdn.example/back.jpg',
    ])
    expect(mapped.detailDescription).toBe('Full product description')
    expect(mapped.detailOptions).toEqual([
      { name: 'Size', values: ['L', 'M'] },
      { name: 'Color', values: ['Black'] },
    ])
    expect(mapped.selectedOptions).toEqual([
      { name: 'Size', value: 'L' },
      { name: 'Color', value: 'Black' },
    ])
  })

  test('maps evidence-backed personalization instead of exposing ranking mechanics', () => {
    const canonical = canonicalProduct('personalized')
    canonical.personalization = {
      whyMeantForYou:
        'Product details list organic and gluten-free, matching your saved preferences.',
      matchedFilterIds: ['organic', 'gluten-free'],
      missedFilterIds: [],
    }

    const mapped = productFromCanonical(canonical)

    expect(mapped.satisfies).toEqual(['organic', 'gluten-free'])
    expect(mapped.misses).toEqual([])
    expect(mapped.note).toBe(canonical.personalization.whyMeantForYou)
    expect(
      productCuratedTake(mapped, [
        { id: 'organic', label: 'Organic', desc: '' },
        { id: 'gluten-free', label: 'Gluten-free', desc: '' },
      ]),
    ).toBe(canonical.personalization.whyMeantForYou)
    expect(mapped.note).not.toContain('Ranked #')
  })
})
