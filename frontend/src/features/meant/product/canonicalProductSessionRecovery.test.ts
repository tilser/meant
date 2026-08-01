import { describe, expect, mock, test } from 'bun:test'

import type {
  CanonicalOfferProfile,
  CanonicalProductDetailProfile,
  CanonicalProductProfile,
} from '../../../lib/apiClient'
import type { Product } from '../types'
import {
  canonicalProductRecoveryQuery,
  findRestoredCanonicalProduct,
  loadCanonicalProductDetailWithRecovery,
} from './canonicalProductSessionRecovery'

function canonicalOffer(
  key: string,
  variantValue: string,
  merchantValue = 'merchant-1',
): CanonicalOfferProfile {
  return {
    key,
    identity: {
      provider: 'shopify',
      merchantScope: {
        type: 'EXTERNAL_MERCHANT',
        externalMerchantIdentity: {
          type: 'MERCHANT',
          namespace: 'shopify',
          value: merchantValue,
        },
      },
      externalProductIdentity: {
        type: 'PRODUCT',
        namespace: 'shopify',
        value: 'product-1',
      },
      externalVariantIdentity: {
        type: 'VARIANT',
        namespace: 'shopify',
        value: variantValue,
      },
      components: [],
    },
    merchantName: 'Merchant',
    availability: { status: 'IN_STOCK' },
    delivery: [],
    selectedOptions: [{ group: 'Variant', name: 'Size', value: 'M' }],
    checkoutExperience: 'PROVIDER_HANDOFF',
    commercialState: {
      authority: 'DISCOVERY_OBSERVATION',
    },
    provenance: [],
  } as CanonicalOfferProfile
}

function canonicalProduct(key: string, offer: CanonicalOfferProfile): CanonicalProductProfile {
  return {
    key,
    title: 'Organic gluten-free snack',
    media: [],
    attributes: [],
    materials: [],
    certifications: [],
    attribution: [],
    identityEvidence: [],
    provenance: [],
    personalization: {
      whyMeantForYou: 'Organic and gluten-free matches your preferences.',
      matchedFilterIds: [],
      missedFilterIds: [],
      unknownFilterIds: [],
      hardConstraintFilterIds: [],
    },
    recommendedOfferKey: offer.key,
    offers: [offer],
  }
}

function productSnapshot(canonical: CanonicalProductProfile): Product {
  return {
    id: canonical.key,
    name: canonical.title ?? 'Product',
    canonicalProduct: canonical,
  } as Product
}

describe('canonical product session recovery', () => {
  test('uses the historical product-search query and falls back to the product title', () => {
    const product = productSnapshot(canonicalProduct('old-key', canonicalOffer('old-offer', 'v1')))

    expect(canonicalProductRecoveryQuery(product, '  organic snacks  ')).toBe('organic snacks')
    expect(canonicalProductRecoveryQuery(product, '   ')).toBe('Organic gluten-free snack')
  })

  test('requires the same canonical key or a stable offer identity', () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const unrelated = canonicalProduct(
      'unrelated-key',
      canonicalOffer('unrelated-offer', 'variant-2'),
    )
    const restored = canonicalProduct('fresh-key', canonicalOffer('fresh-offer', 'variant-1'))

    expect(findRestoredCanonicalProduct(previous, [unrelated, restored])).toBe(restored)
    expect(findRestoredCanonicalProduct(previous, [unrelated])).toBeNull()
  })

  test('loads a canonical detail directly without starting another product search', async () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const detail = {
      product: previous,
      recommendedOfferKey: 'old-offer',
      selectedOfferKey: 'old-offer',
      sourceStates: [],
    } satisfies CanonicalProductDetailProfile
    const loadDetail = mock(async () => detail)

    await expect(
      loadCanonicalProductDetailWithRecovery(
        {
          product: productSnapshot(previous),
          historicalQuery: 'organic snacks',
          selectedOfferKey: 'old-offer',
        },
        { loadDetail },
      ),
    ).resolves.toBe(detail)

    expect(loadDetail).toHaveBeenCalledWith({
      canonicalProductKey: 'old-key',
      selectedOfferKey: 'old-offer',
      signal: undefined,
    })
  })

  test('rethrows an expired detail instead of bypassing qualification with a blind search', async () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const expired = { status: 404 }
    const loadDetail = mock(async () => {
      throw expired
    })

    await expect(
      loadCanonicalProductDetailWithRecovery(
        {
          product: productSnapshot(previous),
          historicalQuery: 'broad historical query',
        },
        { loadDetail },
      ),
    ).rejects.toBe(expired)
    expect(loadDetail).toHaveBeenCalledTimes(1)
  })
})
