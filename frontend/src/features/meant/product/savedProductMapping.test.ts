import { describe, expect, test } from 'bun:test'

import type { CanonicalProductProfile, UserSavedProductProfile } from '../../../lib/apiClient'
import type { Product } from '../types'
import { money } from '../utils'
import { savedProductFromProfile, savedProductInput } from './savedProductMapping'

const unavailable: UserSavedProductProfile = {
  id: 'saved-1',
  productHash: null,
  name: null,
  brand: null,
  category: null,
  tone: null,
  imageUrl: null,
  productUrl: null,
  remote: null,
  match: null,
  priceFrom: null,
  priceFromMinorUnits: null,
  priceCurrency: null,
  merchants: null,
  satisfies: [],
  misses: [],
  note: null,
  pros: [],
  cons: [],
  review: null,
  offers: [],
  needs: null,
  provides: [],
  marketCountry: null,
  marketContextApplied: false,
  commercialFactsAuthoritative: false,
  createdAt: '2026-07-11T00:00:00Z',
  updatedAt: '2026-07-11T00:00:00Z',
}

function groupedProduct(): Product {
  const provenance: CanonicalProductProfile['offers'][number]['provenance'][number] = {
    provider: 'shopify',
    discoverySource: { provider: 'shopify', type: 'PROVIDER_CATALOG', value: 'global-catalog' },
    externalMerchantReference: { type: 'MERCHANT', value: 'merchant-external' },
    externalProductReference: { type: 'PRODUCT', value: 'product-external' },
    externalVariantReference: { type: 'VARIANT', value: 'variant-external' },
    freshness: { observedAt: '2026-07-11T00:00:00Z' },
    sourceReference: { type: 'PROVIDER_CATALOG', reference: 'catalog-record' },
  }
  const offer: CanonicalProductProfile['offers'][number] = {
    key: 'recommended-offer',
    identity: {
      provider: 'shopify',
      merchantScope: {
        type: 'EXTERNAL_MERCHANT',
        externalMerchantIdentity: { type: 'MERCHANT', value: 'merchant-external' },
      },
      externalProductIdentity: { type: 'PRODUCT', value: 'product-external' },
      externalVariantIdentity: { type: 'VARIANT', value: 'variant-external' },
      components: [],
    },
    merchantName: 'Display Merchant',
    price: { minorUnits: 1200, currency: 'USD' },
    availability: { status: 'IN_STOCK' },
    delivery: [],
    selectedOptions: [{ name: 'Size', value: 'Large' }],
    checkoutExperience: 'MEANT_MANAGED',
    commercialState: { authority: 'REHYDRATED_CURRENT', rehydrationStatus: 'FRESH' },
    provenance: [provenance],
  }
  return {
    id: 'canonical-product',
    name: 'Grouped product',
    brand: 'Display brand',
    category: 'Product',
    tone: '#eee',
    productUrl: 'https://display-only.invalid/product',
    remote: true,
    match: 90,
    priceFrom: 12,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: 'Grouped result',
    pros: [],
    cons: [],
    review: { score: null, count: 0, insight: 'No review data' },
    offers: [{ merchant: 'Display Merchant', price: 12, delivery: 'Unknown' }],
    canonicalProduct: {
      key: 'canonical-product',
      title: 'Grouped product',
      media: [],
      attributes: [],
      materials: [],
      certifications: [],
      attribution: [],
      identityEvidence: [],
      provenance: [],
      recommendedOfferKey: offer.key,
      offers: [offer],
    },
  }
}

describe('savedProductFromProfile', () => {
  test('keeps unavailable commercial facts unknown instead of displaying a free price', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      name: 'Saved title',
      imageUrl: 'https://saved.test/image.jpg',
    })

    expect(product.priceFrom).toBeNull()
    expect(product.offers).toEqual([])
    expect(product.commercialFactsAuthoritative).toBe(false)
    expect(product.name).toBe('Saved title')
    expect(product.imageUrl).toBe('https://saved.test/image.jpg')
    expect(money(product.priceFrom)).toBe('Price unavailable')
    expect(savedProductInput(product).priceFrom).toBeNull()
  })

  test('preserves currency exponents and never labels non-USD prices as dollars', () => {
    for (const [currency, minorUnits, majorUnits] of [
      ['USD', 1234, 12.34],
      ['EUR', 1234, 12.34],
      ['JPY', 1234, 1234],
      ['KWD', 1234, 1.234],
    ] as const) {
      const product = savedProductFromProfile({
        ...unavailable,
        name: 'Current title',
        priceFrom: 999,
        priceFromMinorUnits: minorUnits,
        priceCurrency: currency,
        merchants: 1,
        commercialFactsAuthoritative: true,
      })

      expect(product.priceFrom).toBe(majorUnits)
      expect(product.priceCurrency).toBe(currency)
      const rendered = money(product.priceFrom, product.priceCurrency)
      expect(rendered).not.toBe('Price unavailable')
      if (currency !== 'USD') expect(rendered).not.toContain('$')
    }
  })

  test('keeps a fresh exact offer visible when delivery timing is unknown', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      name: 'Current title',
      priceFrom: 12.34,
      priceFromMinorUnits: 1234,
      priceCurrency: 'EUR',
      merchants: 1,
      commercialFactsAuthoritative: true,
      offers: [
        {
          merchant: 'Verified merchant',
          price: 12.34,
          priceMinorUnits: 1234,
          priceCurrency: 'EUR',
          delivery: null,
          merchantId: null,
          merchantDomain: null,
          productVariantId: 'variant-1',
          variantTitle: null,
          available: true,
        },
      ],
    })

    expect(product.offers).toHaveLength(1)
    expect(product.offers[0]?.delivery).toBe('Calculated at checkout')
    expect(product.offers[0]?.priceCurrency).toBe('EUR')
  })

  test('rejects unsupported currency instead of guessing an exponent or symbol', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      priceFrom: 12.34,
      priceFromMinorUnits: 1234,
      priceCurrency: 'INVALID',
      commercialFactsAuthoritative: true,
    })

    expect(product.priceFrom).toBeNull()
    expect(product.commercialFactsAuthoritative).toBe(false)
    expect(money(12.34, 'INVALID')).toBe('Price unavailable')
  })
})

describe('savedProductInput grouped catalog reference', () => {
  test('maps the first matching recommended-offer provider-catalog provenance', () => {
    const product = groupedProduct()
    product.id = 'display-view-model-id'
    product.canonicalProduct!.offers[0]!.provenance.push({
      ...product.canonicalProduct!.offers[0]!.provenance[0]!,
      discoverySource: {
        provider: 'shopify',
        type: 'PROVIDER_CATALOG',
        value: 'later-catalog',
      },
      externalProductReference: { type: 'PRODUCT', value: 'product-external' },
    })

    const input = savedProductInput(product)
    expect(input.id).toBe('canonical-product')
    expect(input.catalogReference).toEqual({
      provider: 'shopify',
      sourceType: 'PROVIDER_CATALOG',
      sourceIdentity: 'global-catalog',
      externalMerchantId: 'merchant-external',
      externalProductId: 'product-external',
      externalVariantId: 'variant-external',
      selectedOptions: [{ name: 'Size', value: 'Large' }],
    })
  })

  test('keeps a Shopify lookup product GID distinct from its transformed grouping anchor', () => {
    const product = groupedProduct()
    const offer = product.canonicalProduct!.offers[0]!
    offer.identity.externalProductIdentity.value =
      'variant-product:v1:gid://shopify/ProductVariant/1'
    offer.identity.externalVariantIdentity!.value = 'gid://shopify/ProductVariant/1'
    offer.provenance[0]!.externalProductReference.value = 'gid://shopify/Product/2'
    offer.provenance[0]!.externalVariantReference!.value = 'gid://shopify/ProductVariant/1'

    expect(savedProductInput(product).catalogReference).toMatchObject({
      provider: 'shopify',
      externalProductId: 'gid://shopify/Product/2',
      externalVariantId: 'gid://shopify/ProductVariant/1',
    })
  })

  test('maps local routing only from the selected provenance record', () => {
    const product = groupedProduct()
    const offer = product.canonicalProduct!.offers[0]!
    offer.identity.merchantScope = {
      type: 'LOCAL_MERCHANT_INTEGRATION_FALLBACK',
      merchantIntegrationFallbackId: '11111111-1111-1111-1111-111111111111',
    }
    offer.provenance[0]!.externalMerchantReference = undefined
    offer.provenance[0]!.localRouting = {
      merchantIntegrationId: '11111111-1111-1111-1111-111111111111',
    }

    expect(savedProductInput(product).catalogReference).toEqual({
      provider: 'shopify',
      sourceType: 'PROVIDER_CATALOG',
      sourceIdentity: 'global-catalog',
      merchantIntegrationId: '11111111-1111-1111-1111-111111111111',
      externalProductId: 'product-external',
      externalVariantId: 'variant-external',
      selectedOptions: [{ name: 'Size', value: 'Large' }],
    })
  })

  test('fails closed when exact merchant provenance or local fallback routing is missing', () => {
    const missingMerchant = groupedProduct()
    const missingMerchantOffer = missingMerchant.canonicalProduct!.offers[0]!
    missingMerchantOffer.provenance[0]!.externalMerchantReference = undefined
    missingMerchantOffer.provenance.push({
      ...missingMerchantOffer.provenance[0]!,
      externalMerchantReference: { type: 'MERCHANT', value: 'merchant-external' },
      externalProductReference: { type: 'PRODUCT', value: 'different-product' },
      externalVariantReference: { type: 'VARIANT', value: 'different-variant' },
    })
    expect(savedProductInput(missingMerchant).catalogReference).toBeUndefined()

    const missingRouting = groupedProduct()
    const offer = missingRouting.canonicalProduct!.offers[0]!
    offer.identity.merchantScope = {
      type: 'LOCAL_MERCHANT_INTEGRATION_FALLBACK',
      merchantIntegrationFallbackId: '11111111-1111-1111-1111-111111111111',
    }
    offer.provenance[0]!.externalMerchantReference = undefined
    expect(savedProductInput(missingRouting).catalogReference).toBeUndefined()
  })

  test('fails closed for extra, missing, or wrong exact variant provenance', () => {
    const extraVariant = groupedProduct()
    extraVariant.canonicalProduct!.offers[0]!.identity.externalVariantIdentity = undefined
    expect(savedProductInput(extraVariant).catalogReference).toBeUndefined()

    const missingVariant = groupedProduct()
    missingVariant.canonicalProduct!.offers[0]!.provenance[0]!.externalVariantReference = undefined
    expect(savedProductInput(missingVariant).catalogReference).toBeUndefined()

    const wrongVariant = groupedProduct()
    wrongVariant.canonicalProduct!.offers[0]!.provenance[0]!.externalVariantReference = {
      type: 'VARIANT',
      value: 'other-variant',
    }
    expect(savedProductInput(wrongVariant).catalogReference).toBeUndefined()
  })

  test('fails closed when product, merchant, or variant identifier roles are wrong', () => {
    const wrongProductRole = groupedProduct()
    wrongProductRole.canonicalProduct!.offers[0]!.provenance[0]!.externalProductReference.type =
      'MERCHANT'
    expect(savedProductInput(wrongProductRole).catalogReference).toBeUndefined()

    const wrongMerchantRole = groupedProduct()
    wrongMerchantRole.canonicalProduct!.offers[0]!.provenance[0]!.externalMerchantReference!.type =
      'PRODUCT'
    expect(savedProductInput(wrongMerchantRole).catalogReference).toBeUndefined()

    const wrongVariantRole = groupedProduct()
    wrongVariantRole.canonicalProduct!.offers[0]!.provenance[0]!.externalVariantReference!.type =
      'PRODUCT'
    expect(savedProductInput(wrongVariantRole).catalogReference).toBeUndefined()
  })

  test('fails closed when an exact selected option is blank', () => {
    const product = groupedProduct()
    product.canonicalProduct!.offers[0]!.selectedOptions = [{ name: 'Size', value: ' ' }]

    expect(savedProductInput(product).catalogReference).toBeUndefined()
  })

  test('does not infer a catalog reference from display fields', () => {
    const product = groupedProduct()
    product.canonicalProduct!.offers[0]!.provenance = []

    expect(savedProductInput(product).catalogReference).toBeUndefined()
  })

  test('keeps legacy product save inputs unchanged', () => {
    const input = savedProductInput(savedProductFromProfile(unavailable))

    expect('catalogReference' in input).toBe(false)
  })
})
