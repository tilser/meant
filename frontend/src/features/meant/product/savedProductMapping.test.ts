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
  priceFromMinorUnits: null,
  priceCurrency: null,
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
  marketCountry: null,
  marketContextApplied: false,
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

  test('preserves currency exponents and never labels non-USD prices as dollars', () => {
    for (const [currency, minorUnits, majorUnits] of [
      ['USD', 1234, 12.34],
      ['EUR', 1234, 12.34],
      ['JPY', 1234, 1234],
      ['KWD', 1234, 1.234],
    ] as const) {
      const product = savedProductFromProfile({
        ...unavailable,
        name: 'Current title',
        priceFrom: 999,
        priceFromMinorUnits: minorUnits,
        priceCurrency: currency,
        merchants: 1,
        commercialFactsAuthoritative: true,
      })

      expect(product.priceFrom).toBe(majorUnits)
      expect(product.priceCurrency).toBe(currency)
      const rendered = money(product.priceFrom, product.priceCurrency)
      expect(rendered).not.toBe('Price unavailable')
      if (currency !== 'USD') expect(rendered).not.toContain('$')
    }
  })

  test('keeps a fresh exact offer visible when delivery timing is unknown', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      name: 'Current title',
      priceFrom: 12.34,
      priceFromMinorUnits: 1234,
      priceCurrency: 'EUR',
      merchants: 1,
      commercialFactsAuthoritative: true,
      offers: [
        {
          merchant: 'Verified merchant',
          price: 12.34,
          priceMinorUnits: 1234,
          priceCurrency: 'EUR',
          delivery: null,
          merchantId: null,
          merchantDomain: null,
          productVariantId: 'variant-1',
          variantTitle: null,
          available: true,
        },
      ],
    })

    expect(product.offers).toHaveLength(1)
    expect(product.offers[0]?.delivery).toBe('Calculated at checkout')
    expect(product.offers[0]?.priceCurrency).toBe('EUR')
  })

  test('rejects unsupported currency instead of guessing an exponent or symbol', () => {
    const product = savedProductFromProfile({
      ...unavailable,
      priceFrom: 12.34,
      priceFromMinorUnits: 1234,
      priceCurrency: 'INVALID',
      commercialFactsAuthoritative: true,
    })

    expect(product.priceFrom).toBeNull()
    expect(product.commercialFactsAuthoritative).toBe(false)
    expect(money(12.34, 'INVALID')).toBe('Price unavailable')
  })
})
