import { describe, expect, mock, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'

mock.module('../../../lib/apiClient', () => ({
  ApiError: class ApiError extends Error {
    status = 500
  },
  getCanonicalProductDetail: () => new Promise(() => undefined),
  getMerchantProductDetails: () => new Promise(() => undefined),
  getProductReviews: () => new Promise(() => undefined),
}))

const { ProductModal } = await import('./ProductModal')
const { merchantProductDetailRequest } = await import('./productDetailLoading')
const { savedProductFromProfile } = await import('./savedProductMapping')

const product: Product = {
  id: 'canonical-1',
  name: 'Grouped product',
  brand: 'Shared brand',
  category: 'Product',
  tone: '#eeeeee',
  match: 90,
  priceFrom: 10,
  listPrice: null,
  merchants: 2,
  satisfies: [],
  misses: [],
  note: 'Ranked using product relevance and saved preferences.',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [
    {
      merchant: 'Merchant A',
      price: 10,
      priceCurrency: 'USD',
      delivery: 'Calculated by merchant',
      available: true,
    },
  ],
  canonicalProduct: {
    key: 'canonical-1',
    title: 'Grouped product',
    media: [],
    attributes: [],
    materials: [],
    certifications: [],
    attribution: [],
    identityEvidence: [],
    provenance: [],
    recommendedOfferKey: 'offer-a',
    offers: [],
  },
}

describe('canonical product detail', () => {
  test('does not replace durable saved-product detail with a second merchant request', () => {
    expect(
      merchantProductDetailRequest({
        ...product,
        remote: true,
        merchantId: 'merchant-1',
        merchantProductId: 'provider-product-1',
        rehydratedDetails: {
          endpoint: null,
          productId: 'provider-product-1',
          handle: null,
          title: 'Complete saved detail',
          description: null,
          url: null,
          imageUrl: null,
          images: [],
          media: [],
          categories: [],
          tags: [],
          options: [],
          variants: [],
          totalVariants: 0,
          priceMin: null,
          priceMax: null,
          priceCurrency: null,
          listPriceMin: null,
          listPriceMax: null,
          listPriceCurrency: null,
          requiresSellingPlan: false,
          selectedVariantId: null,
          selectedVariantTitle: null,
          selectedVariantPriceAmount: null,
          selectedVariantPriceCurrency: null,
          selectedVariantSku: null,
          selectedVariantListPriceAmount: null,
          selectedVariantListPriceCurrency: null,
          selectedVariantImageUrl: null,
          selectedVariantImageAltText: null,
          selectedVariantAvailable: null,
          selectedOptions: [],
          skus: [],
          certifications: [],
          materials: [],
          collections: [],
          attributes: [],
          messages: [],
        },
      }),
    ).toBeNull()

    expect(
      merchantProductDetailRequest({
        ...product,
        remote: true,
        merchantId: 'merchant-1',
        merchantProductId: 'provider-product-1',
      }),
    ).toEqual({ merchantId: 'merchant-1', productId: 'provider-product-1' })
  })

  test('keeps the original product detail shell and embeds exact merchant offers', () => {
    const markup = renderToStaticMarkup(
      <ProductModal
        product={product}
        deliveryLocations={[]}
        preferences={[]}
        saved={false}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        onResearch={() => undefined}
        canPrev={true}
        canNext={true}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('role="dialog"')
    expect(markup).toContain('aria-modal="true"')
    expect(markup).toContain('Previous product')
    expect(markup).toContain('Next product')
    expect(markup).toContain('Meant&#x27;s take')
    expect(markup).toContain('Preference match')
    expect(markup).toContain('Add to compare')
    expect(markup).toContain('Loading offers…')
    expect(markup).toContain('Loading merchant availability…')
    expect(markup).not.toContain('Add selected offer to cart')
  })

  test('renders a saved-product shell and retry action when no current offer is available', () => {
    const unavailableSavedProduct: Product = {
      ...product,
      id: 'saved-unavailable',
      canonicalProduct: undefined,
      offers: [],
      priceFrom: null,
      merchants: 0,
      commercialFactsAuthoritative: false,
    }
    const markup = renderToStaticMarkup(
      <ProductModal
        product={unavailableSavedProduct}
        deliveryLocations={[]}
        preferences={[]}
        saved={true}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        onRefreshProduct={() => undefined}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('role="dialog"')
    expect(markup).toContain('Current merchant offers are unavailable')
    expect(markup).toContain('Current offers are unavailable')
    expect(markup).toContain('Try again')
    expect(markup).toContain('disabled=""')
  })

  test('enables cart binding for a refreshed flat saved offer with a server key', () => {
    const refreshedSavedProduct: Product = {
      ...product,
      id: 'saved-current',
      canonicalProduct: undefined,
      offers: [
        {
          offerKey: 'saved_offer_1',
          merchant: 'Current merchant',
          price: Number.NaN,
          delivery: 'Calculated at checkout',
          available: true,
        },
      ],
      priceFrom: null,
      merchants: 1,
      commercialFactsAuthoritative: true,
    }
    const markup = renderToStaticMarkup(
      <ProductModal
        product={refreshedSavedProduct}
        deliveryLocations={[]}
        preferences={[]}
        saved={true}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        onAddOfferKey={async () => true}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('<span>Add to cart</span>')
    expect(markup).toContain('Price unavailable')
    expect(markup).not.toContain('This offer is not available for merchant checkout')
  })

  test('does not render an internal storefront identity as the saved-offer merchant', () => {
    const mappedSavedProduct = savedProductFromProfile({
      id: 'saved-internal-merchant-reference',
      productHash: null,
      name: 'Saved product',
      brand: null,
      category: null,
      tone: null,
      imageUrl: null,
      productUrl: null,
      remote: null,
      match: null,
      priceFrom: 11,
      priceFromMinorUnits: 1100,
      priceCurrency: 'USD',
      merchants: 1,
      satisfies: [],
      misses: [],
      note: null,
      pros: [],
      cons: [],
      review: null,
      offers: [
        {
          offerKey: 'saved_offer_internal_merchant_reference',
          merchant: 'LOCAL_STOREFRONT:2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          price: 11,
          priceMinorUnits: 1100,
          priceCurrency: 'USD',
          delivery: null,
          merchantId: '2dec9bf6-f8f2-4747-a058-7bb7fa086730',
          merchantDomain: 'shop.example',
          productVariantId: 'variant-1',
          variantTitle: null,
          available: true,
        },
      ],
      needs: null,
      provides: [],
      marketCountry: null,
      marketContextApplied: false,
      commercialFactsAuthoritative: true,
      createdAt: '2026-07-14T00:00:00Z',
      updatedAt: '2026-07-14T00:00:00Z',
    })
    const markup = renderToStaticMarkup(
      <ProductModal
        product={mappedSavedProduct}
        deliveryLocations={[]}
        preferences={[]}
        saved={true}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        onAddOfferKey={async () => true}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('shop.example')
    expect(markup).not.toContain('LOCAL_STOREFRONT')
    expect(markup).not.toContain('2dec9bf6-f8f2-4747-a058-7bb7fa086730')
  })

  test('does not expose a retained offer key while durable saved detail is pending', () => {
    const retainedSavedProduct: Product = {
      ...product,
      id: 'saved-pending',
      canonicalProduct: undefined,
      offers: [
        {
          offerKey: 'retained-session-offer',
          merchant: 'Retained session merchant',
          price: 10,
          delivery: 'Tomorrow',
          available: true,
        },
      ],
      commercialFactsAuthoritative: true,
    }
    const markup = renderToStaticMarkup(
      <ProductModal
        product={retainedSavedProduct}
        deliveryLocations={[]}
        preferences={[]}
        saved={true}
        savePending={false}
        savedOfferRefreshPending={true}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        onAddOfferKey={async () => true}
        onRefreshProduct={() => undefined}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('<span>Loading offers…</span>')
    expect(markup).toContain('Loading current merchant offers…')
    expect(markup).toContain('Loading current offers…')
    expect(markup).not.toContain('<span>Add to cart</span>')
    expect(markup).not.toContain('Retained session merchant')
    expect(markup).not.toContain('Try again')
  })

  test('does not expose a failing legacy cart path for a saved offer without a server key', () => {
    const savedWithoutServerKey: Product = {
      ...product,
      id: 'saved-without-key',
      canonicalProduct: undefined,
      offers: [
        {
          merchant: 'Current merchant',
          merchantDomain: 'merchant.example',
          productVariantId: 'variant-1',
          price: 12,
          delivery: 'Calculated at checkout',
          available: true,
        },
      ],
      commercialFactsAuthoritative: true,
    }
    const markup = renderToStaticMarkup(
      <ProductModal
        product={savedWithoutServerKey}
        deliveryLocations={[]}
        preferences={[]}
        saved={true}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => true}
        onAddOfferKey={async () => true}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('<span>Checkout unavailable</span>')
    expect(markup).toContain('This offer is not available for merchant checkout')
  })

  test('renders the complete transient detail returned for a durable saved product', () => {
    const savedWithDetail: Product = {
      ...product,
      id: 'saved-with-detail',
      canonicalProduct: undefined,
      commercialFactsAuthoritative: true,
      rehydratedDetails: {
        endpoint: null,
        productId: 'provider-product-1',
        handle: 'perfect-shirt',
        title: 'Perfect T-Shirt',
        description: 'Heavyweight organic cotton.',
        url: 'https://merchant.test/perfect-shirt',
        imageUrl: 'https://merchant.test/shirt.jpg',
        images: [],
        media: [],
        categories: [{ value: 'T-Shirts', taxonomy: 'Shopify' }],
        tags: ['heavyweight'],
        options: [{ name: 'Size', values: ['S', 'M', 'L'] }],
        variants: [
          {
            variantId: 'variant-m',
            handle: null,
            title: 'Medium',
            description: null,
            url: null,
            priceAmount: '11.00',
            priceCurrency: 'USD',
            listPriceAmount: null,
            listPriceCurrency: null,
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
        priceMin: '11.00',
        priceMax: '13.00',
        priceCurrency: 'USD',
        listPriceMin: null,
        listPriceMax: null,
        listPriceCurrency: null,
        requiresSellingPlan: false,
        selectedVariantId: 'variant-m',
        selectedVariantTitle: 'Medium',
        selectedVariantPriceAmount: '11.00',
        selectedVariantPriceCurrency: 'USD',
        selectedVariantSku: 'SHIRT-M',
        selectedVariantListPriceAmount: null,
        selectedVariantListPriceCurrency: null,
        selectedVariantImageUrl: null,
        selectedVariantImageAltText: null,
        selectedVariantAvailable: true,
        selectedOptions: [{ name: 'Size', value: 'M' }],
        skus: ['SHIRT-M'],
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
      },
    }
    const markup = renderToStaticMarkup(
      <ProductModal
        product={savedWithDetail}
        deliveryLocations={[]}
        preferences={[]}
        saved={true}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        onAddOfferKey={async () => true}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('Heavyweight organic cotton.')
    expect(markup).toContain('Machine wash cold')
    expect(markup).toContain('SHIRT-M')
    expect(markup).toContain('Organic cotton')
    expect(markup).toContain('GOTS')
    expect(markup).toContain('Medium')
    expect(markup).toContain('3 merchant variants')
  })
})
