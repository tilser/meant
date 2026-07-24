import { describe, expect, test } from 'bun:test'

import type { UserProductSearchProductProfile } from '../../../lib/apiClient'
import { productFromSearchResult } from './productSearchMapping'

function searchResult(
  overrides: Partial<UserProductSearchProductProfile> = {},
): UserProductSearchProductProfile {
  return {
    productKey: 'legacy-product',
    productHash: 'legacy-product-hash',
    merchantId: 'merchant-id',
    merchantDomain: 'nycfactory.com',
    merchantName: 'sollys-online-grocery.myshopify.com',
    merchantRank: 1,
    merchantSemanticScore: 1,
    merchantRerankScore: 1,
    productId: 'provider-product-id',
    title: 'Trail shoe',
    descriptionHtml: null,
    url: 'https://nycfactory.com/products/trail-shoe',
    imageUrl: null,
    priceMinAmount: 12900,
    priceMaxAmount: 12900,
    priceCurrency: 'USD',
    listPriceAmount: null,
    listPriceCurrency: null,
    ratingScore: null,
    reviewCount: 0,
    media: [],
    categories: [],
    certifications: [],
    materials: [],
    skus: [],
    collections: [],
    attributes: [],
    available: true,
    detailError: null,
    detailDescription: null,
    detailImageUrl: null,
    detailPriceMin: null,
    detailPriceMax: null,
    detailPriceCurrency: null,
    selectedVariantId: null,
    selectedVariantTitle: null,
    selectedVariantPriceAmount: null,
    selectedVariantPriceCurrency: null,
    selectedVariantImageUrl: null,
    selectedVariantImageAltText: null,
    selectedVariantAvailable: true,
    catalogRank: 1,
    productRerankScore: 1,
    rank: 1,
    matchScore: 91,
    whyMeantForYou: 'A strong fit.',
    matchedFilterIds: [],
    missedFilterIds: [],
    inventoryRelationship: 'NONE',
    inventoryItemId: null,
    inventoryItemName: null,
    ...overrides,
  }
}

describe('productFromSearchResult merchant display identity', () => {
  test('prefers the official merchant domain over a Shopify transport seller label', () => {
    const product = productFromSearchResult(searchResult(), [])

    expect(product.brand).toBe('nycfactory.com')
    expect(product.offers[0]?.merchant).toBe('nycfactory.com')
    expect(JSON.stringify({ brand: product.brand, offers: product.offers })).not.toContain(
      'sollys-online-grocery.myshopify.com',
    )
  })

  test('uses a neutral display when the purported merchant domain is itself technical', () => {
    const product = productFromSearchResult(
      searchResult({ merchantDomain: 'mcp.shop.example' }),
      [],
    )

    expect(product.brand).toBe('Merchant')
    expect(product.offers[0]?.merchant).toBe('Merchant')
  })
})
