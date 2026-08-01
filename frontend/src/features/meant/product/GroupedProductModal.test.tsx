import { describe, expect, mock, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { ApiError } from '../../../lib/apiError'
import type { ProductVariantSelectionProfile } from '../../../lib/apiClient'
import type { Product } from '../types'

mock.module('../../../lib/apiClient', () => ({
  ApiError,
  getCanonicalProductDetail: () => new Promise(() => undefined),
  getMerchantProductDetails: () => new Promise(() => undefined),
  getProductReviews: () => new Promise(() => undefined),
  searchGroupedProducts: () => new Promise(() => undefined),
  selectProductVariant: () => new Promise(() => undefined),
}))

const { ProductModal } = await import('./ProductModal')
const { exactSelectionPrice } = await import('./variantSelection')
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
  priceCurrency: 'USD',
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
    personalization: {
      whyMeantForYou: 'This looks relevant to your search based on the available product details.',
      matchedFilterIds: [],
      missedFilterIds: [],
      unknownFilterIds: [],
      hardConstraintFilterIds: [],
    },
    recommendedOfferKey: 'offer-a',
    offers: [],
  },
}

describe('canonical product detail', () => {
  test('shows the exact refreshed variant price instead of the stale merchant anchor price', () => {
    const selection = {
      selectedOfferKey: 'offer-current',
      selectedOffer: { key: 'offer-current', price: null },
      details: {
        selectedVariantPriceAmount: '39.98',
        selectedVariantPriceCurrency: 'USD',
      },
      cartable: true,
    } as unknown as ProductVariantSelectionProfile

    expect(exactSelectionPrice(selection, 'offer-current')).toBe('$39.98')
    expect(exactSelectionPrice(selection, 'different-offer')).toBeNull()
  })

  test('does not replace durable saved-product detail with a second merchant request', () => {
    expect(
      merchantProductDetailRequest({
        ...product,
        remote: true,
        merchantId: 'merchant-1',
        merchantProductId: 'provider-product-1',
        rehydratedDetails: {
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
        canPrev={true}
        canNext={true}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('role="dialog"')
    expect(markup).toContain('aria-modal="true"')
    expect(markup).toContain('class="mt-modal-right-shell"')
    expect(markup).toContain('Previous product')
    expect(markup).toContain('Next product')
    expect(markup).toContain('Meant&#x27;s take')
    expect(markup).toContain('Preference match')
    expect(markup).toContain('Add to compare')
    expect(markup).toContain('aria-label="Price and availability"')
    expect(markup).toContain('Price from')
    expect(markup).toContain('$10.00')
    expect(markup).toContain('2 stores')
    expect(markup).toContain('Checking stock…')
    expect(markup).toContain('Loading offers…')
    expect(markup).toContain('Loading current choices…')
    expect(markup).toContain('aria-label="Product purchase options"')
    expect(markup).not.toContain('Choose your item')
    expect(markup).not.toContain('Store, then exact options.')
    expect(markup).not.toContain('Refresh choices')
    expect(markup).not.toContain('1. Store')
    expect(markup).not.toContain('2. Options')
    expect(markup).not.toContain('Add selected offer to cart')
  })

  test('filters technical and placeholder category values at the modal render boundary', () => {
    const markup = renderToStaticMarkup(
      <ProductModal
        product={{
          ...product,
          category: 'gid://shopify/TaxonomyCategory/na',
          catalogCategories: [
            { value: 'gid://shopify/TaxonomyCategory/aa-1', taxonomy: 'Shopify' },
            { value: 'not_applicable', taxonomy: null },
            { value: 'T-Shirts', taxonomy: 'Shopify' },
          ],
        }}
        deliveryLocations={[]}
        preferences={[]}
        saved={false}
        savePending={false}
        inCompare={false}
        onClose={() => undefined}
        onToggleSave={() => undefined}
        onCompare={() => undefined}
        onAddToCart={() => false}
        canPrev={false}
        canNext={false}
        onPrev={() => undefined}
        onNext={() => undefined}
      />,
    )

    expect(markup).toContain('<div class="mt-mono mt-card-brand">Shared brand</div>')
    expect(markup).toContain('T-Shirts')
    expect(markup).not.toContain('gid://')
    expect(markup).not.toContain('not_applicable')
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

    expect(markup).toContain('Merchant')
    expect(markup).not.toContain('shop.example')
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
      offers: [
        {
          ...product.offers[0]!,
          offerKey: 'saved_offer_variant_m',
          productVariantId: 'variant-m',
        },
      ],
      rehydratedDetails: {
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
        options: [
          {
            name: 'Size',
            values: ['S', 'M', 'L', 'XL'],
            valueDetails: [
              { value: 'S', exists: false, available: false },
              { value: 'M', exists: true, available: true },
              { value: 'L', exists: true, available: false },
              { value: 'XL', exists: true, available: null },
            ],
          },
          {
            name: 'Color',
            values: ['Power Red'],
            valueDetails: [{ value: 'Power Red', exists: true, available: true }],
          },
        ],
        variants: [
          {
            variantId: 'variant-m',
            handle: null,
            title: 'Medium',
            description: null,
            url: null,
            priceAmount: '11.00',
            priceCurrency: 'USD',
            listPriceAmount: '15.00',
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
        priceMin: '11.00',
        priceMax: '13.00',
        priceCurrency: 'USD',
        listPriceMin: '15.00',
        listPriceMax: '15.00',
        listPriceCurrency: 'USD',
        requiresSellingPlan: false,
        selectedVariantId: 'variant-m',
        selectedVariantTitle: 'Medium',
        selectedVariantPriceAmount: '11.00',
        selectedVariantPriceCurrency: 'USD',
        selectedVariantSku: 'SHIRT-M',
        selectedVariantListPriceAmount: '15.00',
        selectedVariantListPriceCurrency: 'USD',
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
    const renderSavedDetail = (detailProduct: Product, preferredCurrency = 'USD') =>
      renderToStaticMarkup(
        <ProductModal
          product={detailProduct}
          preferredCurrency={preferredCurrency}
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
    const withPriceCurrencies = (
      exactCurrency: string | null,
      fallbackCurrency: string | null,
    ): Product => ({
      ...savedWithDetail,
      priceCurrency: fallbackCurrency,
      rehydratedDetails: {
        ...savedWithDetail.rehydratedDetails!,
        selectedVariantPriceCurrency: exactCurrency,
        variants: savedWithDetail.rehydratedDetails!.variants.map((variant) =>
          variant ? { ...variant, priceCurrency: exactCurrency } : variant,
        ),
      },
    })
    const markup = renderSavedDetail(savedWithDetail)

    expect(markup).toContain('Heavyweight organic cotton.')
    expect(markup).toContain('Machine wash cold')
    expect(markup).toContain('aria-label="Price and availability"')
    expect(markup).toContain('Sale price')
    expect(markup).toContain('$11.00')
    expect(markup).toContain('Was <s>$15.00</s>')
    expect(markup).toContain('Save $4.00 (27%)')
    expect(markup).toContain('1 store')
    expect(markup).toContain('Available')
    expect(markup).toContain('Medium')
    expect(markup).not.toContain('mt-product-detail-facts')
    expect(markup).not.toContain('Product data')
    expect(markup).not.toContain('Merchant data')
    expect(markup).not.toContain('List price')
    expect(markup).not.toContain('SHIRT-M')
    expect(markup).not.toContain('Organic cotton')
    expect(markup).not.toContain('GOTS')
    expect(markup).not.toContain('Essentials')
    expect(markup).not.toContain('mt-product-variant-table')
    expect(markup).toContain('mt-grouped-offers mt-grouped-offers-compact')
    expect(markup).toContain('aria-label="Product purchase options"')
    expect(markup).toContain('Merchant A')
    expect(markup).toContain('class="mt-merchant-choice-list" role="group" aria-label="Store"')
    expect(markup).toMatch(
      /<legend class="mt-product-option-head"><span class="mt-mono">Size<\/span>/,
    )
    expect(markup).toMatch(
      /<legend class="mt-product-option-head"><span class="mt-mono">Color<\/span>/,
    )
    expect(markup).not.toContain('Choose your item')
    expect(markup).not.toContain('Store, then exact options.')
    expect(markup).not.toContain('Refresh choices')
    expect(markup).not.toContain('1. Store')
    expect(markup).not.toContain('2. Options')
    expect(markup).toContain('mt-product-option-group compact')
    expect(markup).toContain('mt-product-option-group wide')
    expect(markup).toContain('aria-label="Color: Power Red. Available."')
    expect(markup).toContain('aria-label="Size: M. Available."')
    expect(markup).toContain('aria-label="Size: XL. Check stock."')
    expect(markup).toContain('title="Check stock"')
    expect(markup).toContain('class="mt-product-option-chip sold-out "')
    expect(markup).toMatch(/<button class="mt-product-option-chip sold-out "[^>]*><span>L<\/span>/)
    expect(markup).not.toMatch(/mt-product-option-chip sold-out "[^>]*disabled/)

    const missingExactCurrencyMarkup = renderSavedDetail(withPriceCurrencies(null, 'USD'))
    expect(missingExactCurrencyMarkup).toContain('<strong class="mt-modal-price">$10.00</strong>')
    expect(missingExactCurrencyMarkup).toContain('Price from')
    expect(missingExactCurrencyMarkup).not.toContain(
      '<strong class="mt-modal-price">$11.00</strong>',
    )
    expect(missingExactCurrencyMarkup).not.toContain('mt-modal-price-saving')

    const invalidCurrencyMarkup = renderSavedDetail(withPriceCurrencies('US_DOLLARS', 'US_DOLLARS'))
    expect(invalidCurrencyMarkup).toContain(
      '<strong class="mt-modal-price">Price unavailable</strong>',
    )
    expect(invalidCurrencyMarkup).not.toContain('Sale price')
    expect(invalidCurrencyMarkup).not.toContain('mt-modal-price-was')
    expect(invalidCurrencyMarkup).not.toContain('mt-modal-price-saving')

    const mismatchedCurrencyMarkup = renderSavedDetail(withPriceCurrencies('EUR', 'EUR'))
    expect(mismatchedCurrencyMarkup).toContain('<strong class="mt-modal-price">€11.00</strong>')
    expect(mismatchedCurrencyMarkup).toContain('EUR merchant currency')
    expect(mismatchedCurrencyMarkup).not.toContain('Price unavailable')
    expect(mismatchedCurrencyMarkup).not.toContain('$11.00')

    const soldOutAnchorMarkup = renderSavedDetail({
      ...savedWithDetail,
      offers: savedWithDetail.offers.map((offer) => ({ ...offer, available: false })),
    })
    expect(soldOutAnchorMarkup).toContain('aria-label="Product purchase options"')
    expect(soldOutAnchorMarkup).toContain('That exact item is selected')
    expect(soldOutAnchorMarkup).toContain('<span>L</span>')
  })
})
