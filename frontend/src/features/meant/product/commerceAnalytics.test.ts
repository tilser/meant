import { describe, expect, test } from 'bun:test'

import { commerceEventDetail } from './commerceAnalytics'

describe('commerce analytics', () => {
  test('bounds opaque keys and numeric dimensions', () => {
    const event = commerceEventDetail('offer_view', {
      canonicalProductKey: 'p'.repeat(300),
      offerKey: 'o'.repeat(300),
      offerRank: 500,
      offerCount: 900,
      availability: 'IN_STOCK',
    })

    expect(event.fields.canonicalProductKey).toHaveLength(160)
    expect(event.fields.offerKey).toHaveLength(160)
    expect(event.fields.offerRank).toBe(100)
    expect(event.fields.offerCount).toBe(100)
    expect(Object.keys(event.fields).sort()).toEqual(
      ['availability', 'canonicalProductKey', 'offerCount', 'offerKey', 'offerRank'].sort(),
    )
  })
})
