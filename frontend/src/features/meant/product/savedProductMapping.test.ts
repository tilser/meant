import { describe, expect, test } from 'bun:test'

import type {
  CanonicalProductProfile,
  UserSavedProductDetailsProfile,
  UserSavedProductProfile,
} from '../../../lib/apiClient'
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

function fullSavedDetails(): UserSavedProductDetailsProfile {
  return {
    productId: 'provider-product-1',
    handle: 'perfect-shirt',
    title: 'Perfect T-Shirt',
    description: '<p>Heavyweight organic cotton.</p>',
    url: 'https://merchant.test/products/perfect-shirt',
    imageUrl: 'https://merchant.test/shirt.jpg',
    images: [{ url: 'https://merchant.test/shirt-back.jpg', altText: 'Shirt back' }],
    media: [
      {
        type: 'image',
        url: 'https://merchant.test/shirt.jpg',
        altText: 'Shirt front',
        previewImageUrl: null,
      },
    ],
    categories: [{ value: 'T-Shirts', taxonomy: 'Shopify' }],
    tags: ['organic', 'heavyweight'],
    options: [{ name: 'Size', values: ['S', 'M', 'L'] }],
    variants: [
      {
        variantId: 'variant-m',
        handle: 'perfect-shirt-m',
        title: 'Medium',
        description: 'Medium shirt',
        url: 'https://merchant.test/products/perfect-shirt?variant=m',
        priceAmount: '11.00',
        priceCurrency: 'USD',
        listPriceAmount: '15.00',
        listPriceCurrency: 'USD',
        sku: 'SHIRT-M',
        imageUrl: 'https://merchant.test/shirt-m.jpg',
        imageAltText: 'Medium shirt',
        media: [],
        available: true,
        selectedOptions: [{ name: 'Size', value: 'M' }],
        categories: [{ value: 'T-Shirts', taxonomy: 'Shopify' }],
        tags: ['organic'],
        attributes: [{ name: 'Fit', value: 'Regular' }],
      },
    ],
    totalVariants: 3,
    priceMin: '11.00',
    priceMax: '13.00',
    priceCurrency: 'USD',
    listPriceMin: '15.00',
    listPriceMax: '17.00',
    listPriceCurrency: 'USD',
    requiresSellingPlan: false,
    selectedVariantId: 'variant-m',
    selectedVariantTitle: 'Medium',
    selectedVariantPriceAmount: '11.00',
    selectedVariantPriceCurrency: 'USD',
    selectedVariantSku: 'SHIRT-M',
    selectedVariantListPriceAmount: '15.00',
    selectedVariantListPriceCurrency: 'USD',
    selectedVariantImageUrl: 'https://merchant.test/shirt-m.jpg',
    selectedVariantImageAltText: 'Medium shirt',
    selectedVariantAvailable: true,
    selectedOptions: [{ name: 'Size', value: 'M' }],
    skus: ['SHIRT-S', 'SHIRT-M', 'SHIRT-L'],
    certifications: ['GOTS'],
    materials: ['Organic cotton'],
    collections: ['Essentials'],
    attributes: [{ name: 'Fit', value: 'Regular' }],
    messages: [
      {
        type: 'INFO',
        code: 'CARE',
        path: null,
        contentType: 'text/plain',
        content: 'Machine wash cold',
        severity: 'INFO',
        presentation: 'INLINE',
        imageUrl: null,
        url: null,
      },
    ],
    ratingScore: 8.7,
    ratingScaleMax: 10,
    reviewCount: 123,
  }
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
      personalization: {
        whyMeantForYou:
          'This looks relevant to your search based on the available product details.',
        matchedFilterIds: [],
        missedFilterIds: [],
        unknownFilterIds: [],
        hardConstraintFilterIds: [],
      },
      recommendedOfferKey: offer.key,
      offers: [offer],
    },
  }
}

describe('savedProductFromProfile', () => {
  test('maps every durable get-product detail into the product and retained detail profile', () => {
    const details = fullSavedDetails()
    const product = savedProductFromProfile({
      ...unavailable,
      name: 'Perfect T-Shirt',
      productUrl: 'https://merchant.test/products/stale-saved-url',
      commercialFactsAuthoritative: true,
      offers: [
        {
          offerKey: 'saved_offer_v1_current',
          merchant: 'Current merchant',
          price: 11,
          priceMinorUnits: 1100,
          priceCurrency: 'USD',
          delivery: null,
          merchantId: '11111111-1111-1111-1111-111111111111',
          merchantOrigin: 'merchant.test',
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
      details,
    })

    expect(product.imageUrl).toBe('https://merchant.test/shirt-m.jpg')
    expect(product.productUrl).toBe('https://merchant.test/products/perfect-shirt')
    expect(product.detailDescription).toBe('<p>Heavyweight organic cotton.</p>')
    expect(product.media).toHaveLength(2)
    expect(product.catalogCategories).toEqual([{ value: 'T-Shirts', taxonomy: 'Shopify' }])
    expect(product.detailOptions).toEqual([{ name: 'Size', values: ['S', 'M', 'L'] }])
    expect(product.selectedOptions).toEqual([{ name: 'Size', value: 'M' }])
    expect(product.totalVariants).toBe(3)
    expect(product.selectedVariantAvailable).toBe(true)
    expect(product.materials).toEqual(['Organic cotton'])
    expect(product.certifications).toEqual(['GOTS'])
    expect(product.rehydratedDetails).toEqual(details)
    expect(product.rehydratedDetails?.variants[0]?.sku).toBe('SHIRT-M')
    expect(product.rehydratedDetails?.tags).toEqual(['organic', 'heavyweight'])
    expect(product.rehydratedDetails?.messages[0]?.content).toBe('Machine wash cold')
    expect(product.review).toEqual({
      score: 4.35,
      count: 123,
      insight: 'Current review facts are unavailable.',
    })
    expect(product.merchantId).toBe('11111111-1111-1111-1111-111111111111')
    expect(product.merchantDomain).toBe('merchant.test')
    expect(product.offers[0]?.merchant).toBe('merchant.test')
    expect(product.offers[0]?.merchantDomain).toBe('merchant.test')
    expect(product.merchantProductId).toBe('provider-product-1')
    expect(product.remote).toBe(true)
  })

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
          offerKey: 'saved_offer_1',
          merchant: 'Verified merchant',
          price: 12.34,
          priceMinorUnits: 1234,
          priceCurrency: 'EUR',
          delivery: null,
          merchantId: null,
          productVariantId: 'variant-1',
          variantTitle: null,
          available: true,
        },
      ],
    })

    expect(product.offers).toHaveLength(1)
    expect(product.offers[0]?.delivery).toBe('Calculated at checkout')
    expect(product.offers[0]?.priceCurrency).toBe('EUR')
    expect(product.offers[0]?.offerKey).toBe('saved_offer_1')
  })

  test('keeps current authority but rejects an unsupported display currency', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      priceFrom: 12.34,
      priceFromMinorUnits: 1234,
      priceCurrency: 'INVALID',
      commercialFactsAuthoritative: true,
    })

    expect(product.priceFrom).toBeNull()
    expect(product.commercialFactsAuthoritative).toBe(true)
    expect(money(12.34, 'INVALID')).toBe('Price unavailable')
  })

  test('keeps a fresh exact cart key when the provider has no current display price', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      offers: [
        {
          offerKey: 'saved_offer_without_price',
          merchant: 'Verified merchant',
          price: null,
          priceMinorUnits: null,
          priceCurrency: null,
          delivery: null,
          merchantId: null,
          productVariantId: 'variant-1',
          variantTitle: null,
          available: true,
        },
      ],
    })

    expect(product.priceFrom).toBeNull()
    expect(product.offers).toHaveLength(1)
    expect(product.offers[0]?.offerKey).toBe('saved_offer_without_price')
    expect(money(product.offers[0]?.price, product.offers[0]?.priceCurrency)).toBe(
      'Price unavailable',
    )
  })

  test('uses the current detail merchant name instead of an internal routing identity', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      details: { ...fullSavedDetails(), merchantName: 'Actual Merchant' },
      offers: [
        {
          offerKey: 'saved_offer_current_detail_merchant',
          merchant: 'LOCAL_STOREFRONT:2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          price: 11,
          priceMinorUnits: 1100,
          priceCurrency: 'USD',
          delivery: null,
          merchantId: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.offers).toHaveLength(1)
    expect(product.offers[0]?.merchant).toBe('Actual Merchant')
    expect(product.offers[0]?.merchant).not.toContain('LOCAL_STOREFRONT')
  })

  test('uses a neutral merchant label when all current names are technical references', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      details: { ...fullSavedDetails(), merchantName: 'PROVIDER_CATALOG' },
      offers: [
        {
          offerKey: 'saved_offer_domain_fallback',
          merchant: 'MERCHANT_INTEGRATION:2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          price: null,
          priceMinorUnits: null,
          priceCurrency: null,
          delivery: null,
          merchantId: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.offers[0]?.merchant).toBe('Merchant')
  })

  test('uses a neutral merchant label instead of exposing an identity value', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      details: { ...fullSavedDetails(), merchantName: 'gid://shopify/Shop/123' },
      offers: [
        {
          offerKey: 'saved_offer_neutral_fallback',
          merchant: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          price: null,
          priceMinorUnits: null,
          priceCurrency: null,
          delivery: null,
          merchantId: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.offers[0]?.merchant).toBe('Merchant')
  })

  test('keeps an ordinary current merchant name', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      offers: [
        {
          offerKey: 'saved_offer_display_name',
          merchant: 'Local Storefront Goods',
          price: null,
          priceMinorUnits: null,
          priceCurrency: null,
          delivery: null,
          merchantId: null,
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.offers[0]?.merchant).toBe('Local Storefront Goods')
  })

  test('never exposes a Shopify transport seller label without an official origin', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      details: {
        ...fullSavedDetails(),
        merchantName: 'https://sollys-online-grocery.myshopify.com/mcp',
      },
      offers: [
        {
          offerKey: 'saved_offer_poisoned_transport_label',
          merchant: 'sollys-online-grocery.myshopify.com',
          price: 11,
          priceMinorUnits: 1100,
          priceCurrency: 'USD',
          delivery: null,
          merchantId: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.offers[0]?.merchant).toBe('Merchant')
    expect(JSON.stringify(product.offers)).not.toContain('sollys-online-grocery.myshopify.com')
  })

  test('shows a verified myshopify storefront origin when it is the official origin', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      offers: [
        {
          offerKey: 'saved_offer_verified_shopify_origin',
          merchant: 'Internal seller label',
          merchantOrigin: 'official-store.myshopify.com',
          price: 11,
          priceMinorUnits: 1100,
          priceCurrency: 'USD',
          delivery: null,
          merchantId: null,
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.merchantDomain).toBe('official-store.myshopify.com')
    expect(product.offers[0]?.merchant).toBe('official-store.myshopify.com')
  })

  test('keeps an exact server offer when its merchant display name is null', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      commercialFactsAuthoritative: true,
      offers: [
        {
          offerKey: 'saved_offer_null_merchant',
          merchant: null,
          price: null,
          priceMinorUnits: null,
          priceCurrency: null,
          delivery: null,
          merchantId: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          productVariantId: 'variant-m',
          variantTitle: 'Medium',
          available: true,
        },
      ],
    })

    expect(product.offers).toHaveLength(1)
    expect(product.offers[0]?.offerKey).toBe('saved_offer_null_merchant')
    expect(product.offers[0]?.merchant).toBe('Merchant')
  })
})

describe('savedProductInput grouped catalog reference', () => {
  test('passes an exact server offer key for new saves and saved-choice updates', () => {
    const input = savedProductInput(groupedProduct(), [], ' exact-offer ')

    expect(input.selectedOfferKey).toBe('exact-offer')
  })

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
      offerKey: 'recommended-offer',
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
      offerKey: 'recommended-offer',
    })
  })

  test('maps the exact bundle and selling-plan identity with the server offer key', () => {
    const product = groupedProduct()
    const offer = product.canonicalProduct!.offers[0]!
    offer.identity.components = [
      {
        externalProductIdentity: {
          type: 'PRODUCT',
          namespace: 'shopify',
          value: 'component-product',
        },
        externalVariantIdentity: {
          type: 'VARIANT',
          namespace: 'shopify',
          value: 'component-variant',
        },
        quantity: 2,
        selectedOptions: [
          {
            group: 'variant-option',
            name: 'Color',
            value: 'Blue',
          },
        ],
      },
    ]
    offer.identity.sellingPlanIdentity = {
      groupReference: {
        type: 'SELLING_PLAN_GROUP',
        namespace: 'shopify',
        value: 'subscription-group',
      },
      planReference: {
        type: 'SELLING_PLAN',
        namespace: 'shopify',
        value: 'monthly-plan',
      },
      options: [{ name: 'frequency', value: 'monthly' }],
    }

    expect(savedProductInput(product).catalogReference).toMatchObject({
      offerKey: 'recommended-offer',
      components: [
        {
          externalProductId: 'component-product',
          externalVariantId: 'component-variant',
          quantity: 2,
          selectedOptions: [{ group: 'variant-option', name: 'Color', value: 'Blue' }],
        },
      ],
      sellingPlan: {
        groupId: 'subscription-group',
        planId: 'monthly-plan',
        options: [{ name: 'frequency', value: 'monthly' }],
      },
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
