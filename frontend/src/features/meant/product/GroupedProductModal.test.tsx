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
})
