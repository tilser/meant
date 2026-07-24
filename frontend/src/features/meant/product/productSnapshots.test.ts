import { describe, expect, test } from 'bun:test'

import type { Product } from '../types'
import {
  confirmedSavedProductSnapshot,
  mergeRehydratedProductDetails,
  productImageUrl,
  refreshedSavedProductSnapshot,
  savedProductRefreshShell,
  uniqueProductSnapshotsByPriority,
} from './productSnapshots'

describe('product image snapshots', () => {
  test('keeps product artwork independent from commercial fact authority', () => {
    const product = {
      imageUrl: 'https://catalog.test/shoe.jpg',
      commercialFactsAuthoritative: false,
    }

    expect(productImageUrl(product)).toBe('https://catalog.test/shoe.jpg')
  })

  test('uses the first media image when the direct image is missing', () => {
    const product = {
      imageUrl: null,
      media: [
        { type: 'video', url: 'https://catalog.test/shoe.mp4' },
        { type: 'image', url: 'https://catalog.test/shoe.jpg' },
      ],
    }

    expect(productImageUrl(product)).toBe('https://catalog.test/shoe.jpg')
  })

  test('keeps the durable saved copy ahead of a live snapshot with the same id', () => {
    const base: Product = {
      id: 'same-product',
      name: 'Product',
      brand: 'Brand',
      category: 'Product',
      tone: '#eee',
      match: 90,
      priceFrom: 10,
      merchants: 1,
      satisfies: [],
      misses: [],
      note: '',
      pros: [],
      cons: [],
      review: { score: null, count: 0, insight: '' },
      offers: [],
    }
    const live: Product = {
      ...base,
      offers: [
        {
          offerKey: 'session-offer',
          merchant: 'Live merchant',
          price: 10,
          delivery: 'Tomorrow',
        },
      ],
    }
    const saved: Product = {
      ...base,
      offers: [
        {
          offerKey: 'saved_offer_v1_durable',
          merchant: 'Saved merchant',
          price: 11,
          delivery: 'Calculated at checkout',
        },
      ],
    }

    const result = uniqueProductSnapshotsByPriority([saved], [live])

    expect(result).toHaveLength(1)
    expect(result[0]).toBe(saved)
    expect(result[0]?.offers[0]?.offerKey).toBe('saved_offer_v1_durable')
  })

  test('keeps a usable live offer when save confirmation has no commercial facts', () => {
    const live: Product = {
      id: 'saved-product',
      name: 'Live title',
      brand: 'Live brand',
      category: 'Product',
      tone: '#eee',
      match: 90,
      priceFrom: 12,
      merchants: 1,
      satisfies: [],
      misses: [],
      note: 'Live curation',
      pros: [],
      cons: [],
      review: { score: null, count: 0, insight: '' },
      offers: [{ merchant: 'Merchant', price: 12, delivery: 'Tomorrow' }],
      commercialFactsAuthoritative: true,
    }
    const confirmed: Product = {
      ...live,
      name: 'Confirmed title',
      priceFrom: null,
      merchants: 0,
      offers: [],
      commercialFactsAuthoritative: false,
    }

    const merged = confirmedSavedProductSnapshot(live, confirmed)

    expect(merged.name).toBe('Confirmed title')
    expect(merged.priceFrom).toBe(12)
    expect(merged.offers).toEqual(live.offers)
    expect(merged.commercialFactsAuthoritative).toBe(true)
  })

  test('removes retained session offers and routing while durable saved detail is loading', () => {
    const live: Product = {
      id: 'saved-product',
      merchantId: 'merchant-1',
      merchantDomain: 'merchant.test',
      merchantProductId: 'product-1',
      name: 'Live title',
      brand: 'Live brand',
      category: 'Product',
      tone: '#eee',
      imageUrl: 'https://catalog.test/product.jpg',
      match: 90,
      priceFrom: 12,
      priceFromMinorUnits: 1200,
      priceCurrency: 'USD',
      listPrice: 15,
      merchants: 1,
      satisfies: [],
      misses: [],
      note: 'Live curation',
      pros: [],
      cons: [],
      review: { score: null, count: 0, insight: '' },
      selectedVariantAvailable: true,
      rehydratedDetails: {
        productId: 'session-product',
        handle: 'perfect-shirt',
        title: 'Perfect T-Shirt',
        description: 'Heavyweight organic cotton.',
        url: null,
        imageUrl: null,
        images: [],
        media: [],
        categories: [],
        tags: [],
        options: [{ name: 'Size', values: ['S', 'M', 'L'] }],
        variants: [
          {
            variantId: 'variant-m',
            handle: null,
            title: 'Medium',
            description: null,
            url: null,
            priceAmount: '10.00',
            priceCurrency: 'USD',
            listPriceAmount: '12.00',
            listPriceCurrency: 'USD',
            sku: 'SHIRT-M',
            imageUrl: null,
            imageAltText: null,
            media: [],
            available: true,
            selectedOptions: [{ name: 'Size', value: 'M' }],
            categories: [],
            tags: [],
            attributes: [],
          },
        ],
        totalVariants: 3,
        priceMin: '10.00',
        priceMax: '10.00',
        priceCurrency: 'USD',
        listPriceMin: '12.00',
        listPriceMax: '12.00',
        listPriceCurrency: 'USD',
        requiresSellingPlan: false,
        selectedVariantId: 'variant-m',
        selectedVariantTitle: 'Medium',
        selectedVariantPriceAmount: '10.00',
        selectedVariantPriceCurrency: 'USD',
        selectedVariantSku: 'SHIRT-M',
        selectedVariantListPriceAmount: '12.00',
        selectedVariantListPriceCurrency: 'USD',
        selectedVariantImageUrl: null,
        selectedVariantImageAltText: null,
        selectedVariantAvailable: true,
        selectedOptions: [{ name: 'Size', value: 'M' }],
        skus: ['SHIRT-M'],
        certifications: [],
        materials: ['Organic cotton'],
        collections: [],
        attributes: [],
        messages: [
          {
            type: 'warning',
            code: 'OLD_NOTICE',
            path: null,
            contentType: 'text/plain',
            content: 'Old provider notice',
            severity: 'warning',
            presentation: 'inline',
            imageUrl: null,
            url: null,
          },
        ],
      },
      offers: [
        {
          offerKey: 'session-offer-key',
          merchant: 'Merchant',
          price: 12,
          delivery: 'Tomorrow',
        },
      ],
      commercialFactsAuthoritative: true,
      canonicalProduct: {
        key: 'session-product-key',
        recommendedOfferKey: 'session-offer-key',
      } as Product['canonicalProduct'],
    }

    const shell = savedProductRefreshShell(live)

    expect(shell.name).toBe(live.name)
    expect(shell.imageUrl).toBe(live.imageUrl)
    expect(shell.offers).toEqual([])
    expect(shell.canonicalProduct).toBeUndefined()
    expect(shell.merchantId).toBeNull()
    expect(shell.merchantDomain).toBeNull()
    expect(shell.merchantProductId).toBeNull()
    expect(shell.priceFrom).toBeNull()
    expect(shell.selectedVariantAvailable).toBeNull()
    expect(shell.commercialFactsAuthoritative).toBe(false)
    expect(shell.rehydratedDetails?.description).toBe('Heavyweight organic cotton.')
    expect(shell.rehydratedDetails?.options).toEqual(live.rehydratedDetails?.options)
    expect(shell.rehydratedDetails?.selectedVariantPriceAmount).toBeNull()
    expect(shell.rehydratedDetails?.selectedVariantAvailable).toBeNull()
    expect(shell.rehydratedDetails?.variants[0]?.priceAmount).toBeNull()
    expect(shell.rehydratedDetails?.variants[0]?.available).toBeNull()
    expect(live.offers).toHaveLength(1)

    const refreshedDetails = mergeRehydratedProductDetails(live.rehydratedDetails, {
      ...live.rehydratedDetails!,
      variants: [],
      messages: [],
    })
    expect(refreshedDetails?.variants).toEqual(live.rehydratedDetails?.variants)
    expect(refreshedDetails?.messages).toEqual([])
  })

  test('keeps rich presentation detail while replacing every commerce field with fresh saved data', () => {
    const live: Product = {
      id: 'saved-product',
      merchantId: 'session-merchant',
      merchantDomain: 'session.example',
      merchantProductId: 'session-product',
      name: 'Perfect T-Shirt',
      brand: 'Merchant',
      category: 'T-Shirts',
      tone: '#eee',
      match: 90,
      priceFrom: 10,
      merchants: 1,
      satisfies: [],
      misses: [],
      note: 'Rich live result',
      pros: [],
      cons: [],
      review: { score: null, count: 0, insight: '' },
      media: [
        { type: 'image', url: 'https://merchant.test/front.jpg' },
        { type: 'image', url: 'https://merchant.test/back.jpg' },
      ],
      detailDescription: 'Heavyweight organic cotton.',
      detailOptions: [{ name: 'Size', values: ['S', 'M', 'L'] }],
      selectedOptions: [{ name: 'Size', value: 'M' }],
      totalVariants: 3,
      selectedVariantAvailable: true,
      offers: [
        {
          offerKey: 'session-offer',
          merchant: 'Session merchant',
          price: 10,
          delivery: 'Tomorrow',
        },
      ],
      commercialFactsAuthoritative: true,
      canonicalProduct: {
        key: 'session-product-key',
        recommendedOfferKey: 'session-offer',
      } as Product['canonicalProduct'],
    }
    const shell = savedProductRefreshShell(live)
    const refreshed: Product = {
      ...shell,
      priceFrom: 11,
      priceFromMinorUnits: 1100,
      priceCurrency: 'USD',
      merchants: 1,
      selectedOptions: [],
      selectedVariantAvailable: false,
      offers: [
        {
          offerKey: 'durable-saved-offer',
          merchant: 'Current merchant',
          price: 11,
          delivery: 'Calculated at checkout',
        },
      ],
      media: [],
      detailDescription: null,
      detailOptions: [],
      totalVariants: null,
      commercialFactsAuthoritative: true,
    }

    const result = refreshedSavedProductSnapshot(shell, refreshed)

    expect(result.detailDescription).toBe('Heavyweight organic cotton.')
    expect(result.media).toEqual(live.media)
    expect(result.detailOptions).toEqual(live.detailOptions)
    expect(result.selectedOptions).toEqual(live.selectedOptions)
    expect(result.totalVariants).toBe(3)
    expect(result.priceFrom).toBe(11)
    expect(result.selectedVariantAvailable).toBe(false)
    expect(result.offers[0]?.offerKey).toBe('durable-saved-offer')
    expect(result.canonicalProduct).toBeUndefined()
    expect(result.merchantId).toBeNull()
    expect(result.merchantDomain).toBeNull()
    expect(result.merchantProductId).toBeNull()
  })
})
