import { describe, expect, test } from 'bun:test'

import type {
  CanonicalOfferProfile,
  CanonicalProductProfile,
  MerchantProductDetailsProfile,
  MerchantProductVariantProfile,
  ProductOptionProfile,
  ProductVariantSelectionProfile,
} from '../../../lib/apiClient'
import type { Product } from '../types'
import {
  merchantOfferChoices,
  optionValueState,
  productWithVariantSelection,
  savedOfferInitiallyCartable,
  savedSelectionChanged,
  selectedOptionsWithPreference,
} from './variantSelection'

function option(overrides: Partial<ProductOptionProfile> = {}): ProductOptionProfile {
  return { name: 'Size', values: ['S', 'M'], valueDetails: null, ...overrides }
}

function variant(
  size: string,
  available: boolean | null,
  color = 'Blue',
): MerchantProductVariantProfile {
  return {
    variantId: `${size}-${color}`,
    title: `${size} / ${color}`,
    sku: null,
    available,
    selectedOptions: [
      { name: 'Size', value: size },
      { name: 'Color', value: color },
    ],
    priceAmount: null,
    priceCurrency: null,
    listPriceAmount: null,
    listPriceCurrency: null,
    imageUrl: null,
    imageAltText: null,
  } as unknown as MerchantProductVariantProfile
}

function canonicalOffer(
  key: string,
  merchantId: string,
  selectedValue = 'M',
  provider = 'shopify',
): CanonicalOfferProfile {
  return {
    key,
    merchantName: `Merchant ${merchantId}`,
    selectedOptions: [{ name: 'Size', value: selectedValue }],
    identity: {
      provider,
      merchantScope: {
        type: 'LOCAL_MERCHANT_INTEGRATION_FALLBACK',
        externalMerchantIdentity: null,
        merchantIntegrationFallbackId: merchantId,
      },
    },
    provenance: [],
    availability: { status: 'IN_STOCK' },
    price: { minorUnits: 1200, currency: 'USD' },
    delivery: [],
    checkoutExperience: 'NATIVE',
  } as unknown as CanonicalOfferProfile
}

describe('variant selection helpers', () => {
  test('keeps impossible and sold-out option values distinct using authoritative value details', () => {
    const profile = option({
      valueDetails: [
        { value: 'S', exists: false, available: false },
        { value: 'M', exists: true, available: false },
        { value: 'L', exists: true, available: true },
      ],
    })

    expect(optionValueState(profile, [variant('S', true)], [], 'S')).toBe('impossible')
    expect(optionValueState(profile, [], [], 'M')).toBe('sold-out')
    expect(optionValueState(profile, [], [], 'L')).toBe('available')
  })

  test('uses combination-aware variants only as a legacy fallback', () => {
    const profile = option({ valueDetails: null })
    const variants = [variant('S', true, 'Blue'), variant('M', false, 'Blue')]
    const color = [{ name: 'Color', value: 'Blue' }]

    expect(optionValueState(profile, variants, color, 'S')).toBe('available')
    expect(optionValueState(profile, variants, color, 'M')).toBe('sold-out')
    expect(optionValueState(profile, variants, color, 'XL')).toBe('impossible')
  })

  test('preserves the clicked option and orders it according to the provider option order', () => {
    expect(
      selectedOptionsWithPreference(
        [
          { name: 'Color', value: 'Blue' },
          { name: 'Size', value: 'S' },
        ],
        { name: 'Size', value: 'M' },
        ['Size', 'Color'],
      ),
    ).toEqual([
      { name: 'Size', value: 'M' },
      { name: 'Color', value: 'Blue' },
    ])
  })

  test('compares saved variants rather than incompatible durable and canonical offer keys', () => {
    const baseline = {
      selectedVariantId: 'variant-m',
      selectedOptions: [{ name: 'Size', value: 'M' }],
    } as MerchantProductDetailsProfile

    expect(savedSelectionChanged(baseline, { ...baseline }, 'merchant-1', 'merchant-1')).toBe(false)
    expect(
      savedSelectionChanged(
        baseline,
        {
          selectedVariantId: 'variant-l',
          selectedOptions: [{ name: 'Size', value: 'L' }],
        } as MerchantProductDetailsProfile,
        'merchant-1',
        'merchant-1',
      ),
    ).toBe(true)
    expect(savedSelectionChanged(baseline, { ...baseline }, 'merchant-1', 'merchant-2')).toBe(true)
  })

  test('only initializes a durable saved offer as cartable for confirmed availability', () => {
    expect(savedOfferInitiallyCartable({ offerKey: 'saved-offer', available: true })).toBe(true)
    expect(savedOfferInitiallyCartable({ offerKey: 'saved-offer', available: false })).toBe(false)
    expect(savedOfferInitiallyCartable({ offerKey: 'saved-offer', available: null })).toBe(false)
    expect(savedOfferInitiallyCartable({ offerKey: 'saved-offer' })).toBe(false)
  })

  test('shows one merchant anchor and keeps the recommended merchant first', () => {
    const product = {
      recommendedOfferKey: 'recommended',
      offers: [
        canonicalOffer('other-size', 'merchant-a', 'S'),
        canonicalOffer('merchant-b', 'merchant-b'),
        canonicalOffer('recommended', 'merchant-a', 'M'),
      ],
    } as CanonicalProductProfile

    expect(merchantOfferChoices(product).map((choice) => choice.anchor.key)).toEqual([
      'recommended',
      'merchant-b',
    ])
  })

  test('keeps equal external and integration merchant values separate across providers', () => {
    const shopifyExternal = {
      ...canonicalOffer('shopify-external', 'unused'),
      identity: {
        provider: 'shopify',
        merchantScope: {
          type: 'EXTERNAL_MERCHANT',
          externalMerchantIdentity: {
            type: 'MERCHANT',
            namespace: 'global',
            value: 'shared-merchant',
          },
          merchantIntegrationFallbackId: null,
        },
      },
    } as unknown as CanonicalOfferProfile
    const etsyExternal = {
      ...shopifyExternal,
      key: 'etsy-external',
      identity: { ...shopifyExternal.identity, provider: 'etsy' },
    } as unknown as CanonicalOfferProfile
    const product = {
      recommendedOfferKey: shopifyExternal.key,
      offers: [
        shopifyExternal,
        etsyExternal,
        canonicalOffer('shopify-integration', 'shared-integration', 'M', 'shopify'),
        canonicalOffer('etsy-integration', 'shared-integration', 'M', 'etsy'),
      ],
    } as CanonicalProductProfile

    const choices = merchantOfferChoices(product)
    expect(choices.map((choice) => choice.anchor.key)).toEqual([
      'shopify-external',
      'etsy-external',
      'shopify-integration',
      'etsy-integration',
    ])
    expect(new Set(choices.map((choice) => choice.key))).toHaveLength(4)
  })

  test('uses the exact server offer verbatim instead of inheriting anchor provenance', () => {
    const anchor = canonicalOffer('anchor', 'merchant-a', 'S')
    const exact = {
      ...canonicalOffer('exact', 'merchant-a', 'M'),
      provenance: [{ externalMerchantDomain: 'exact.example' }],
    } as CanonicalOfferProfile
    const canonical = {
      key: 'product-a',
      recommendedOfferKey: anchor.key,
      offers: [anchor],
    } as CanonicalProductProfile
    const product = {
      id: 'product-a',
      offers: [],
      canonicalProduct: canonical,
    } as unknown as Product
    const details = {
      variants: [variant('M', true)],
      selectedVariantId: 'M-Blue',
      selectedVariantTitle: 'M / Blue',
      selectedOptions: [
        { name: 'Size', value: 'M' },
        { name: 'Color', value: 'Blue' },
      ],
    } as MerchantProductDetailsProfile
    const selection = {
      details,
      selectedOfferKey: exact.key,
      selectedOffer: exact,
      cartable: true,
    } as ProductVariantSelectionProfile

    const selected = productWithVariantSelection(product, canonical, selection, details)

    expect(selected.canonicalProduct?.offers[0]).toBe(exact)
    expect(selected.canonicalProduct?.offers[0]?.provenance).toEqual(exact.provenance)
    expect(selected.offers[0]?.offerKey).toBe('exact')
    expect(selected.offers[0]?.productVariantId).toBe('M-Blue')
  })
})
