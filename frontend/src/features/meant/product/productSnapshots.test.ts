import { describe, expect, test } from 'bun:test'

import { productImageUrl } from './productSnapshots'

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
})
