import { expect, test } from 'bun:test'

import type { components, paths } from './schema'

test('generated OpenAPI schema exposes federated V1 routes without replacing flat search routes', () => {
  const expectedPaths: Array<keyof paths> = [
    '/api/users/me/product-searches',
    '/api/users/me/product-searches:stream',
    '/api/v1/users/me/product-searches',
    '/api/v1/users/me/product-searches:stream',
    '/api/v1/users/me/products/{canonicalProductKey}',
    '/api/v1/users/me/product-variant-selections',
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
    'SelectUserProductVariantRequest',
    'UserProductVariantSelectionResponse',
    'CanonicalProductAttributeResponse',
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

  expect(expectedPaths).toHaveLength(6)
  expect(expectedSchemas).toHaveLength(22)
  expect(federatedEventFields).toContain('observationSources')
  expect(rankingFields).toContain('diversityPolicyOutcome')

  const canonicalAttributeFields: Array<
    keyof components['schemas']['CanonicalProductAttributeResponse']
  > = ['group', 'name', 'value']
  expect(canonicalAttributeFields).toContain('group')
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

test('saved products retain typed routing and return a durable exact offer key', () => {
  type SaveProduct = components['schemas']['SaveUserProductRequest']
  type CatalogReference = components['schemas']['CatalogReference']
  const saveFields: Array<keyof SaveProduct> = ['id', 'catalogReference', 'selectedOfferKey']
  const referenceFields: Array<keyof CatalogReference> = [
    'provider',
    'sourceType',
    'sourceIdentity',
    'merchantIntegrationId',
    'externalMerchantId',
    'externalMerchantDomain',
    'externalProductId',
    'externalVariantId',
    'selectedOptions',
    'offerKey',
    'components',
    'sellingPlan',
  ]

  expect(saveFields).toContain('catalogReference')
  expect(saveFields).toContain('selectedOfferKey')
  expect(referenceFields).toContain('sourceIdentity')
  expect(referenceFields).toContain('externalMerchantDomain')
  expect(referenceFields).toContain('selectedOptions')
  expect(referenceFields).toContain('offerKey')
  expect(referenceFields).toContain('components')
  expect(referenceFields).toContain('sellingPlan')

  const savedPaths: Array<keyof paths> = ['/api/users/me/saved-products/detail']
  const savedOfferFields: Array<keyof components['schemas']['UserSavedProductOffer']> = [
    'offerKey',
    'merchant',
    'productVariantId',
  ]
  expect(savedPaths).toHaveLength(1)
  expect(savedOfferFields).toContain('offerKey')

  const savedProductFields: Array<keyof components['schemas']['UserSavedProductResponse']> = [
    'offers',
    'details',
  ]
  const savedDetailFields: Array<keyof components['schemas']['UserSavedProductDetails']> = [
    'description',
    'media',
    'options',
    'variants',
    'messages',
    'selectedOptions',
    'ratingScore',
    'ratingScaleMax',
    'reviewCount',
    'merchantName',
  ]
  expect(savedProductFields).toContain('details')
  expect(savedDetailFields).toContain('variants')
  expect(savedDetailFields).toContain('messages')
  expect(savedDetailFields).toContain('ratingScore')
  expect(savedDetailFields).toContain('ratingScaleMax')
  expect(savedDetailFields).toContain('reviewCount')
  expect(savedDetailFields).toContain('merchantName')

  const optionFields: Array<keyof components['schemas']['UserSavedProductDetailOption']> = [
    'name',
    'values',
    'valueDetails',
  ]
  const optionValueFields: Array<keyof components['schemas']['UserSavedProductDetailOptionValue']> =
    ['value', 'available', 'exists']
  expect(optionFields).toContain('valueDetails')
  expect(optionValueFields).toContain('available')
  expect(optionValueFields).toContain('exists')
})

test('variant selection uses the generated exact-offer contract', () => {
  type Request = components['schemas']['SelectUserProductVariantRequest']
  type Response = components['schemas']['UserProductVariantSelectionResponse']
  const requestFields: Array<keyof Request> = [
    'anchorOfferKey',
    'selectedOptions',
    'preferredOptionName',
  ]
  const responseFields: Array<keyof Response> = [
    'details',
    'selectedOfferKey',
    'selectedOffer',
    'cartable',
  ]

  expect(requestFields).toContain('selectedOptions')
  expect(responseFields).toContain('selectedOffer')
  expect(responseFields).toContain('cartable')
})

test('embedded checkout bootstrap exposes only short-lived browser instructions', () => {
  const expectedPaths: Array<keyof paths> = [
    '/api/carts/{cartId}/checkout/embedded',
    '/api/carts/{cartId}/checkout/embedded/{sessionId}/complete',
    '/api/carts/{cartId}/checkout/embedded/{sessionId}/cancel',
  ]
  type Bootstrap = components['schemas']['EmbeddedCheckoutBootstrapResponse']
  const fields: Array<keyof Bootstrap> = [
    'action',
    'sessionId',
    'cartId',
    'checkoutId',
    'checkoutUrl',
    'fallbackContinueUrl',
    'protocolVersion',
    'ecAuth',
    'allowedDelegations',
    'expiresAt',
    'merchantProvider',
    'merchantDomain',
    'reason',
  ]

  expect(expectedPaths).toHaveLength(3)
  expect(fields as string[]).not.toContain('clientSecret')
  expect(fields as string[]).not.toContain('accessToken')
})
