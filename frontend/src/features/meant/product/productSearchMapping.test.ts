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

  test('keeps a selected merchant-native amount paired with its source currency', () => {
    const product = productFromSearchResult(
      searchResult({
        title: 'Salthouse T-Shirt Black',
        priceCurrency: 'EUR',
        selectedVariantPriceAmount: '32.00',
        selectedVariantPriceCurrency: 'eur',
      }),
      [],
    )

    expect(product.priceFrom).toBe(32)
    expect(product.priceCurrency).toBe('EUR')
    expect(product.priceFromMinorUnits).toBeNull()
    expect(product.offers[0]?.price).toBe(32)
    expect(product.offers[0]?.priceCurrency).toBe('EUR')
  })

  test('uses the account-currency search price when current merchant detail is native EUR', () => {
    const product = productFromSearchResult(
      searchResult({
        title: 'Salthouse T-Shirt Black',
        priceMinAmount: 3669,
        priceMaxAmount: 3669,
        priceCurrency: 'USD',
        selectedVariantPriceAmount: '32.00',
        selectedVariantPriceCurrency: 'EUR',
      }),
      [],
    )

    expect(product.priceFrom).toBe(36.69)
    expect(product.priceCurrency).toBe('USD')
    expect(product.priceFromMinorUnits).toBe(3669)
    expect(product.offers[0]?.price).toBe(36.69)
    expect(product.offers[0]?.priceCurrency).toBe('USD')
  })

  test('does not attach an unpaired selected amount to the search-result currency', () => {
    const product = productFromSearchResult(
      searchResult({
        selectedVariantPriceAmount: '32.00',
        selectedVariantPriceCurrency: null,
      }),
      [],
    )

    expect(product.priceFrom).toBe(129)
    expect(product.priceCurrency).toBe('USD')
    expect(product.priceFromMinorUnits).toBe(12900)
  })
})
