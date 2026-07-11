import { describe, expect, mock, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'

mock.module('../../../lib/apiClient', () => ({
  ApiError: class ApiError extends Error {
    status = 500
  },
  getCanonicalProductDetail: () => new Promise(() => undefined),
}))

const { GroupedProductModal } = await import('./GroupedProductModal')

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
  offers: [],
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

describe('GroupedProductModal', () => {
  test('renders an accessible loading dialog without a speculative cart action', () => {
    const markup = renderToStaticMarkup(
      <GroupedProductModal
        product={product}
        onClose={() => undefined}
        onResearch={() => undefined}
      />,
    )

    expect(markup).toContain('role="dialog"')
    expect(markup).toContain('aria-modal="true"')
    expect(markup).toContain('Refreshing offers without changing your selection')
    expect(markup).not.toContain('Add selected offer to cart')
  })
})
