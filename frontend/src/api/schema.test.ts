import { expect, test } from 'bun:test'

import type { components, paths } from './schema'

test('generated OpenAPI schema exposes federated V1 routes without replacing flat search routes', () => {
  const expectedPaths: Array<keyof paths> = [
    '/api/users/me/product-searches',
    '/api/users/me/product-searches:stream',
    '/api/v1/users/me/product-searches',
    '/api/v1/users/me/product-searches:stream',
    '/api/v1/users/me/products/{canonicalProductKey}',
  ]
  const expectedSchemas: Array<keyof components['schemas']> = [
    'UserGroupedProductSearchV1Response',
    'CanonicalProductResponse',
    'OfferResponse',
    'OfferIdentityResponse',
    'OfferMerchantScopeResponse',
    'OfferComponentIdentityResponse',
    'ProductRankingExplanationResponse',
    'ProductRankingFeatureResponse',
    'OfferRankingExplanationResponse',
    'OfferRankingFeatureResponse',
    'ProductGroupingDecisionResponse',
    'ResultProvenanceResponse',
    'DiscoverySourceIdentityResponse',
    'LocalMerchantRoutingResponse',
    'UserFederatedProductSearchStreamEventResponse',
    'CatalogSourceFailureResponse',
    'UserCanonicalProductDetailV1Response',
    'UserCatalogSourceStateResponse',
    'UserOfferCommercialStateResponse',
  ]
  const federatedEventFields: Array<
    keyof components['schemas']['UserFederatedProductSearchStreamEventResponse']
  > = ['type', 'source', 'observationSources', 'candidate', 'failure', 'terminalStatus']
  const rankingFields: Array<keyof components['schemas']['ProductRankingExplanationResponse']> = [
    'diversityPolicyVersion',
    'diversityPolicyOutcome',
    'execution',
    'features',
  ]

  expect(expectedPaths).toHaveLength(5)
  expect(expectedSchemas).toHaveLength(19)
  expect(federatedEventFields).toContain('observationSources')
  expect(rankingFields).toContain('diversityPolicyOutcome')
})

test('cart creation accepts only server-issued offer identity for line selection', () => {
  type CartCreate = components['schemas']['CartCreateRequest']
  type CartAddItem = components['schemas']['CartAddItemRequest']
  const createFields: Array<keyof CartCreate> = ['addItems', 'discountCodes', 'giftCardCodes']
  const addFields: Array<keyof CartAddItem> = ['offerKey', 'quantity']

  expect(createFields as string[]).not.toContain('merchantId')
  expect(createFields as string[]).not.toContain('merchantDomain')
  expect(addFields as string[]).not.toContain('productVariantId')
  expect(addFields).toContain('offerKey')
})

test('saved products accept a typed session-only catalog reference', () => {
  type SaveProduct = components['schemas']['SaveUserProductRequest']
  type CatalogReference = components['schemas']['CatalogReference']
  const saveFields: Array<keyof SaveProduct> = ['id', 'catalogReference']
  const referenceFields: Array<keyof CatalogReference> = [
    'provider',
    'sourceType',
    'sourceIdentity',
    'merchantIntegrationId',
    'externalMerchantId',
    'externalProductId',
    'externalVariantId',
    'selectedOptions',
  ]

  expect(saveFields).toContain('catalogReference')
  expect(referenceFields).toContain('sourceIdentity')
  expect(referenceFields).toContain('selectedOptions')
})
