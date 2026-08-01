import { describe, expect, test } from 'bun:test'

import { similaritySearchQuery } from './similarAction'

describe('similaritySearchQuery', () => {
  test('preserves the originating catalog query instead of replacing it with the product title', () => {
    expect(
      similaritySearchQuery('  Find me a natural-material T-shirt  ', 'Find Your Wild T-shirt'),
    ).toBe('Find me a natural-material T-shirt')
  })

  test('falls back to the selected product and respects the tool query limit', () => {
    expect(similaritySearchQuery(undefined, 'Trail shoe')).toBe('products similar to Trail shoe')
    expect(similaritySearchQuery('x'.repeat(600), 'Trail shoe')).toHaveLength(500)
  })
})
