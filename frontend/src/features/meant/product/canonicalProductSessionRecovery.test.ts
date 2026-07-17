import { describe, expect, mock, test } from 'bun:test'

import type {
  CanonicalOfferProfile,
  CanonicalProductDetailProfile,
  CanonicalProductProfile,
  GroupedProductSearchProfile,
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

function searchResult(products: readonly CanonicalProductProfile[]): GroupedProductSearchProfile {
  return { products } as GroupedProductSearchProfile
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

  test('re-searches and retries detail in place with the restored offer identity', async () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const restored = canonicalProduct('fresh-key', canonicalOffer('fresh-offer', 'variant-1'))
    const restoredDetail = {
      product: restored,
      recommendedOfferKey: 'fresh-offer',
      selectedOfferKey: 'fresh-offer',
      sourceStates: [],
    } satisfies CanonicalProductDetailProfile
    const detailCalls: Array<{
      canonicalProductKey: string
      selectedOfferKey?: string | null
      signal?: AbortSignal
    }> = []
    const searchCalls: Array<{
      query: string
      offset?: number
      limit?: number
      signal?: AbortSignal
    }> = []
    const loadDetail = mock(
      async (input: {
        canonicalProductKey: string
        selectedOfferKey?: string | null
        signal?: AbortSignal
      }) => {
        detailCalls.push(input)
        if (input.canonicalProductKey === 'old-key') {
          throw { status: 404 }
        }
        return restoredDetail
      },
    )
    const search = mock(
      async (input: { query: string; offset?: number; limit?: number; signal?: AbortSignal }) => {
        searchCalls.push(input)
        return searchResult([restored])
      },
    )

    await expect(
      loadCanonicalProductDetailWithRecovery(
        {
          product: productSnapshot(previous),
          historicalQuery: 'organic snacks',
          selectedOfferKey: 'old-offer',
        },
        { loadDetail, search },
      ),
    ).resolves.toBe(restoredDetail)

    expect(searchCalls).toEqual([{ query: 'organic snacks', offset: 0, limit: 20 }])
    expect(detailCalls).toEqual([
      { canonicalProductKey: 'old-key', selectedOfferKey: 'old-offer', signal: undefined },
      { canonicalProductKey: 'fresh-key', selectedOfferKey: 'fresh-offer', signal: undefined },
    ])
  })

  test('falls back to the product title when a legacy product moved beyond the broad result page', async () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const unrelated = canonicalProduct(
      'unrelated-key',
      canonicalOffer('unrelated-offer', 'variant-2'),
    )
    const restored = canonicalProduct('fresh-key', canonicalOffer('fresh-offer', 'variant-1'))
    const searchCalls: string[] = []

    const result = await loadCanonicalProductDetailWithRecovery(
      {
        product: productSnapshot(previous),
        historicalQuery: 'broad historical query',
        selectedOfferKey: 'old-offer',
      },
      {
        loadDetail: async ({ canonicalProductKey }) => {
          if (canonicalProductKey === 'old-key') throw { status: 404 }
          return {
            product: restored,
            recommendedOfferKey: 'fresh-offer',
            selectedOfferKey: 'fresh-offer',
            sourceStates: [],
          } as CanonicalProductDetailProfile
        },
        search: async ({ query }) => {
          searchCalls.push(query)
          return searchResult(query === previous.title ? [restored] : [unrelated])
        },
      },
    )

    expect(result.product).toBe(restored)
    expect(searchCalls).toEqual(['broad historical query', 'Organic gluten-free snack'])
  })

  test('does not substitute an unrelated product after both recovery searches', async () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const unrelated = canonicalProduct(
      'unrelated-key',
      canonicalOffer('unrelated-offer', 'variant-2'),
    )
    const expired = { status: 404 }
    const search = mock(async () => searchResult([unrelated]))

    await expect(
      loadCanonicalProductDetailWithRecovery(
        {
          product: productSnapshot(previous),
          historicalQuery: 'broad historical query',
        },
        {
          loadDetail: async () => {
            throw expired
          },
          search,
        },
      ),
    ).rejects.toBe(expired)
    expect(search).toHaveBeenCalledTimes(2)
  })

  test('does not re-search when the detail failure is not a 404', async () => {
    const previous = canonicalProduct('old-key', canonicalOffer('old-offer', 'variant-1'))
    const failure = { status: 503 }
    const search = mock(async () => searchResult([]))

    await expect(
      loadCanonicalProductDetailWithRecovery(
        { product: productSnapshot(previous) },
        {
          loadDetail: async () => {
            throw failure
          },
          search,
        },
      ),
    ).rejects.toBe(failure)
    expect(search).not.toHaveBeenCalled()
  })
})
