import { expect, test } from 'bun:test'

import type { components, paths } from './schema'

test('generated OpenAPI schema exposes grouped V1 without replacing flat search routes', () => {
  const expectedPaths: Array<keyof paths> = [
    '/api/users/me/product-searches',
    '/api/users/me/product-searches:stream',
    '/api/v1/users/me/product-searches',
  ]
  const expectedSchemas: Array<keyof components['schemas']> = [
    'UserGroupedProductSearchV1Response',
    'CanonicalProductResponse',
    'OfferResponse',
    'OfferIdentityResponse',
    'OfferMerchantScopeResponse',
    'OfferComponentIdentityResponse',
    'ResultProvenanceResponse',
    'DiscoverySourceIdentityResponse',
    'LocalMerchantRoutingResponse',
  ]

  expect(expectedPaths).toHaveLength(3)
  expect(expectedSchemas).toHaveLength(9)
})
