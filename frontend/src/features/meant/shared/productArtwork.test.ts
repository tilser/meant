import { describe, expect, test } from 'bun:test'

import type { Product } from '../types'
import { productArtworkUrl } from './productArtwork'

function artworkProduct(overrides: Partial<Product>): Product {
  return {
    id: 'product-1',
    name: 'Organic cotton tee',
    brand: 'Maker',
    category: 'T-shirts',
    tone: '#eee',
    match: 90,
    priceFrom: null,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: '',
    pros: [],
    cons: [],
    review: { score: null, count: 0, insight: '' },
    offers: [],
    ...overrides,
  }
}

describe('product artwork selection', () => {
  test('uses the same explicit, snapshot, and media priority for cards and agent mentions', () => {
    const product = artworkProduct({
      imageUrl: ' https://cdn.example/snapshot.jpg ',
      media: [{ type: 'image', url: 'https://cdn.example/media.jpg' }],
    })

    expect(productArtworkUrl(product, ' https://cdn.example/explicit.jpg ')).toBe(
      'https://cdn.example/explicit.jpg',
    )
    expect(productArtworkUrl(product)).toBe('https://cdn.example/snapshot.jpg')
    expect(productArtworkUrl({ ...product, imageUrl: null })).toBe('https://cdn.example/media.jpg')
  })

  test('returns no image when the product snapshot has no usable artwork', () => {
    expect(
      productArtworkUrl(
        artworkProduct({
          imageUrl: ' ',
          media: [
            { type: 'video', url: 'https://cdn.example/video.mp4' },
            { type: 'image', url: ' ' },
          ],
        }),
      ),
    ).toBeNull()
  })
})
