import { describe, expect, test } from 'bun:test'

import type { UserSavedProductProfile } from '../../../lib/apiClient'
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
  commercialFactsAuthoritative: false,
  createdAt: '2026-07-11T00:00:00Z',
  updatedAt: '2026-07-11T00:00:00Z',
}

describe('savedProductFromProfile', () => {
  test('keeps unavailable commercial facts unknown instead of displaying a free price', () => {
    const product = savedProductFromProfile(unavailable)

    expect(product.priceFrom).toBeNull()
    expect(product.offers).toEqual([])
    expect(product.commercialFactsAuthoritative).toBe(false)
    expect(money(product.priceFrom)).toBe('Price unavailable')
    expect(savedProductInput(product).priceFrom).toBeNull()
  })

  test('uses fresh price only when the backend marks the facts authoritative', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      name: 'Current title',
      priceFrom: 42,
      merchants: 1,
      commercialFactsAuthoritative: true,
    })

    expect(product.priceFrom).toBe(42)
    expect(money(product.priceFrom)).toBe('$42.00')
  })
})
