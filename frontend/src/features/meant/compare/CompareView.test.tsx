import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'
import { CompareView } from './CompareView'

const technicalSeller = 'sollys-online-grocery.myshopify.com'

function product(id: string, price: number): Product {
  return {
    id,
    name: `Trail shoe ${id}`,
    brand: technicalSeller,
    category: 'Running shoes',
    tone: '#eee',
    match: 90,
    priceFrom: price,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: 'A strong fit.',
    pros: [],
    cons: [],
    review: { score: null, count: 0, insight: 'No reviews.' },
    offers: [
      {
        merchant: technicalSeller,
        price,
        delivery: 'Calculated at checkout',
      },
    ],
  }
}

describe('CompareView merchant display identity', () => {
  test('fails closed for poisoned product-brand and offer-merchant snapshots', () => {
    const products = [product('one', 100), product('two', 120)]
    const markup = renderToStaticMarkup(
      <CompareView
        products={products}
        savedProducts={[]}
        compareIds={products.map((item) => item.id)}
        preferences={[]}
        deliveryLocations={[]}
        onRemove={() => undefined}
        onAdd={() => undefined}
        onOpen={() => undefined}
      />,
    )

    expect(markup).toContain('Merchant')
    expect(markup).not.toContain(technicalSeller)
  })
})
