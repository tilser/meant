import { describe, expect, test } from 'bun:test'

import type {
  CanonicalOfferProfile,
  CanonicalProductDetailProfile,
  MerchantProductDetailsProfile,
  ProductSelectedOptionProfile,
  ProductVariantSelectionProfile,
} from '../../../lib/apiClient'
import { ApiError } from '../../../lib/apiError'
import type { Product } from '../types'
import {
  bindPreparedProductPurchaseWithRecovery,
  isExactProductVariantSelection,
  prepareDirectProductPurchase,
  requiresExplicitVariantSelection,
} from './directPurchasePreparation'

const baseProduct: Product = {
  id: 'product-1',
  name: 'Current product',
  brand: 'Merchant',
  category: 'Shirts',
  tone: 'black',
  match: 90,
  priceFrom: 79.9,
  priceCurrency: 'USD',
  merchants: 1,
  satisfies: [],
  misses: [],
  note: '',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [
    {
      offerKey: 'history-offer',
      merchant: 'rawganique.com',
      price: 79.9,
      priceCurrency: 'USD',
      delivery: 'Calculated at checkout',
      available: true,
    },
  ],
}

function details(
  overrides: Partial<MerchantProductDetailsProfile> = {},
): MerchantProductDetailsProfile {
  return {
    productId: 'product-1',
    handle: 'current-product',
    title: 'Current product',
    description: null,
    url: null,
    imageUrl: null,
    images: [],
    media: [],
    categories: [],
    tags: [],
    options: [],
    variants: [
      {
        variantId: 'variant-current',
        handle: null,
        title: 'Default',
        description: null,
        url: null,
        priceAmount: '2500.00',
        priceCurrency: 'CZK',
        listPriceAmount: null,
        listPriceCurrency: null,
        sku: null,
        imageUrl: null,
        imageAltText: null,
        media: [],
        available: true,
        selectedOptions: [],
        categories: [],
        tags: [],
        attributes: [],
      },
    ],
    totalVariants: 1,
    priceMin: '2500.00',
    priceMax: '2500.00',
    priceCurrency: 'CZK',
    listPriceMin: null,
    listPriceMax: null,
    listPriceCurrency: null,
    requiresSellingPlan: false,
    selectedVariantId: 'variant-current',
    selectedVariantTitle: 'Default',
    selectedVariantPriceAmount: '2500.00',
    selectedVariantPriceCurrency: 'CZK',
    selectedVariantSku: null,
    selectedVariantListPriceAmount: null,
    selectedVariantListPriceCurrency: null,
    selectedVariantImageUrl: null,
    selectedVariantImageAltText: null,
    selectedVariantAvailable: true,
    selectedOptions: [],
    skus: [],
    certifications: [],
    materials: [],
    collections: [],
    attributes: [],
    messages: [],
    ...overrides,
  }
}

function exactOffer(
  key: string,
  selectedOptions: readonly ProductSelectedOptionProfile[] = [],
): CanonicalOfferProfile {
  return {
    key,
    identity: {
      provider: 'SHOPIFY',
      merchantScope: {
        externalMerchantIdentity: {
          type: 'MERCHANT',
          namespace: 'SHOPIFY',
          value: 'merchant-1',
        },
      },
      externalProductIdentity: {
        type: 'PRODUCT',
        namespace: 'SHOPIFY',
        value: 'product-1',
      },
      externalVariantIdentity: {
        type: 'VARIANT',
        namespace: 'SHOPIFY',
        value: 'variant-current',
      },
      components: [],
      selectedOptions: [],
    },
    merchantName: 'Rawganique',
    merchantOrigin: 'rawganique.com',
    variantTitle: 'Default',
    price: { minorUnits: 250_000, currency: 'CZK' },
    availability: { status: 'IN_STOCK' },
    delivery: [],
    selectedOptions: selectedOptions.map((option) => ({
      name: option.name ?? '',
      value: option.value ?? '',
    })),
    checkoutExperience: 'MEANT_MANAGED',
    commercialState: {} as CanonicalOfferProfile['commercialState'],
    provenance: [],
  } as unknown as CanonicalOfferProfile
}

function selection(
  offerKey: string,
  currentDetails: MerchantProductDetailsProfile = details(),
): ProductVariantSelectionProfile {
  return {
    details: currentDetails,
    selectedOfferKey: offerKey,
    selectedOffer: exactOffer(offerKey, currentDetails.selectedOptions),
    cartable: true,
  }
}

const unusedCanonicalLoader = async (): Promise<CanonicalProductDetailProfile> => {
  throw new Error('canonical detail should not be loaded')
}

describe('direct product purchase preparation', () => {
  test('replaces a historical saved anchor with the current exact selection and currency', async () => {
    let receivedAnchor = ''

    const prepared = await prepareDirectProductPurchase(
      { product: { ...baseProduct, rehydratedDetails: details() }, expectedUserId: 'user-1' },
      {
        loadCanonicalDetail: unusedCanonicalLoader,
        selectVariant: async (input) => {
          receivedAnchor = input.anchorOfferKey
          return selection('current-exact-offer')
        },
      },
    )

    expect(receivedAnchor).toBe('history-offer')
    expect(prepared.status).toBe('ready')
    if (prepared.status !== 'ready') throw new Error('Expected a ready purchase')
    expect(prepared.offerKey).toBe('current-exact-offer')
    expect(prepared.product.offers[0]?.offerKey).toBe('current-exact-offer')
    expect(prepared.product.offers[0]?.price).toBe(2500)
    expect(prepared.product.offers[0]?.priceCurrency).toBe('CZK')
  })

  test('refreshes a canonical anchor before selecting the exact variant', async () => {
    const refreshedOffer = exactOffer('refreshed-anchor')
    const canonicalDetail = {
      product: {
        key: 'canonical-1',
        recommendedOfferKey: refreshedOffer.key,
        offers: [refreshedOffer],
      },
      recommendedOfferKey: refreshedOffer.key,
      selectedOfferKey: refreshedOffer.key,
      sourceStates: [],
    } as unknown as CanonicalProductDetailProfile
    let receivedAnchor = ''

    const prepared = await prepareDirectProductPurchase(
      {
        product: {
          ...baseProduct,
          canonicalProduct: {
            ...canonicalDetail.product,
            recommendedOfferKey: 'historical-canonical-anchor',
          },
        },
        expectedUserId: 'user-1',
      },
      {
        loadCanonicalDetail: async () => canonicalDetail,
        selectVariant: async (input) => {
          receivedAnchor = input.anchorOfferKey
          return selection('current-exact-offer')
        },
      },
    )

    expect(receivedAnchor).toBe('refreshed-anchor')
    expect(prepared.status).toBe('ready')
  })

  test('adds the same exact current variant even when other variants are available', async () => {
    const selectedOptions = [
      { name: 'Size', value: 'M' },
      { name: 'Color', value: 'Black' },
    ]
    const configurable = details({
      totalVariants: 2,
      selectedOptions,
      options: [
        {
          name: 'Size',
          values: ['M', 'L'],
          valueDetails: [
            { value: 'M', available: true, exists: true },
            { value: 'L', available: true, exists: true },
          ],
        },
      ],
      variants: [
        { ...details().variants[0]!, selectedOptions },
        {
          ...details().variants[0]!,
          variantId: 'variant-large',
          selectedOptions: [
            { name: 'Size', value: 'L' },
            { name: 'Color', value: 'Black' },
          ],
        },
      ],
    })

    const prepared = await prepareDirectProductPurchase(
      {
        product: { ...baseProduct, rehydratedDetails: configurable },
        expectedUserId: 'user-1',
      },
      {
        loadCanonicalDetail: unusedCanonicalLoader,
        selectVariant: async () => selection('current-exact-offer', configurable),
      },
    )

    expect(prepared.status).toBe('ready')
    if (prepared.status !== 'ready') throw new Error('Expected a ready purchase')
    expect(prepared.offerKey).toBe('current-exact-offer')
    expect(requiresExplicitVariantSelection(configurable)).toBe(true)
  })

  test('rejects a provider-relaxed or ambiguous tuple', async () => {
    const requested = [{ name: 'Size', value: 'M' }]
    const effective = details({ selectedOptions: [{ name: 'Size', value: 'L' }] })
    const relaxed = selection('current-exact-offer', effective)

    expect(isExactProductVariantSelection(relaxed, requested)).toBe(false)
  })

  test('returns unavailable without any trusted offer anchor', async () => {
    const prepared = await prepareDirectProductPurchase(
      { product: { ...baseProduct, offers: [] }, expectedUserId: 'user-1' },
      {
        loadCanonicalDetail: unusedCanonicalLoader,
        selectVariant: async () => selection('should-not-run'),
      },
    )

    expect(prepared.status).toBe('unavailable')
  })

  test('re-prepares an explicit selection once after a typed stale binding failure', async () => {
    const addedKeys: string[] = []
    let preparations = 0

    const added = await bindPreparedProductPurchaseWithRecovery({
      product: baseProduct,
      offerKey: 'exact-v1',
      expectedUserId: 'user-1',
      bindSelectedOffer: async (_product, offerKey) => {
        addedKeys.push(offerKey)
        if (offerKey === 'exact-v1') {
          throw new ApiError(
            'The selected offer is not currently eligible for cart.',
            409,
            'bad_request',
            'stale_or_unavailable',
          )
        }
        return true
      },
      preparePurchase: async (input) => {
        preparations += 1
        expect(input.expectedUserId).toBe('user-1')
        return { status: 'ready', product: baseProduct, offerKey: 'exact-v2' }
      },
    })

    expect(added).toBe(true)
    expect(preparations).toBe(1)
    expect(addedKeys).toEqual(['exact-v1', 'exact-v2'])
  })
})
