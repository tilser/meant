import { expect, test } from 'bun:test'

import type { components, paths } from './schema'

test('generated OpenAPI schema exposes only federated V1 search routes', () => {
  const expectedPaths: Array<keyof paths> = [
    '/api/v1/users/me/product-search-qualifications',
    '/api/v1/users/me/product-searches',
    '/api/v1/users/me/product-searches:stream',
    '/api/v1/users/me/products/{canonicalProductKey}',
    '/api/v1/users/me/products/{canonicalProductKey}/similar',
    '/api/v1/users/me/products:rehydrate',
    '/api/v1/users/me/product-variant-selections',
  ]
  const expectedSchemas: Array<keyof components['schemas']> = [
    'UserProductSearchQualificationRequest',
    'UserProductSearchQualificationResponse',
    'UserProductSearchQualificationStatus',
    'UserProductSearchFilterKind',
    'UserGroupedProductSearchV1Response',
    'UserSimilarProductSearchV1Response',
    'CanonicalProductResponse',
    'CanonicalProductPersonalizationResponse',
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
    'UserSimilarProductSearchRequest',
    'UserCanonicalProductRehydrationRequest',
    'UserCanonicalProductRehydrationV1Response',
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

  expect(expectedPaths).toHaveLength(7)
  expect(expectedSchemas).toHaveLength(31)
  expect(federatedEventFields).toContain('observationSources')
  expect(rankingFields).toContain('diversityPolicyOutcome')

  const personalizationFields: Array<
    keyof components['schemas']['CanonicalProductPersonalizationResponse']
  > = ['whyMeantForYou', 'matchedFilterIds', 'missedFilterIds']
  expect(personalizationFields).toContain('whyMeantForYou')

  const canonicalAttributeFields: Array<
    keyof components['schemas']['CanonicalProductAttributeResponse']
  > = ['group', 'name', 'value']
  expect(canonicalAttributeFields).toContain('group')

  const similarSearchFields: Array<keyof components['schemas']['UserSimilarProductSearchRequest']> =
    ['query', 'qualificationId']
  expect(similarSearchFields).toEqual(['query', 'qualificationId'])

  const groupedResultSetField: keyof components['schemas']['UserGroupedProductSearchV1Response'] =
    'productResultSetId'
  const similarResponseFields: Array<
    keyof components['schemas']['UserSimilarProductSearchV1Response']
  > = ['query', 'products', 'hasMore']
  expect(groupedResultSetField).toBe('productResultSetId')
  expect(similarResponseFields).toEqual(['query', 'products', 'hasMore'])

  const rehydrationRequestFields: Array<
    keyof components['schemas']['UserCanonicalProductRehydrationRequest']
  > = ['canonicalProductKeys']
  const rehydrationResponseFields: Array<
    keyof components['schemas']['UserCanonicalProductRehydrationV1Response']
  > = ['products', 'unavailableCanonicalProductKeys']
  expect(rehydrationRequestFields).toEqual(['canonicalProductKeys'])
  expect(rehydrationResponseFields).toEqual(['products', 'unavailableCanonicalProductKeys'])
})

test('cart creation accepts only server-issued offer identity for line selection', () => {
  type CartCreate = components['schemas']['CartCreateRequest']
  type CartAddItem = components['schemas']['CartAddItemRequest']
  type CartBuyer = components['schemas']['CartBuyerIdentityRequest']
  type CartAddress = components['schemas']['CartDeliveryAddressSelectionRequest']
  type CartDeliveryOption = components['schemas']['CartDeliveryOptionSelectionRequest']
  type HasStringIndex<T> = string extends keyof T ? true : false
  const createFields: Array<keyof CartCreate> = [
    'addItems',
    'buyerIdentity',
    'deliveryAddressesToAdd',
    'selectedDeliveryOptions',
    'discountCodes',
    'giftCardCodes',
  ]
  const addFields: Array<keyof CartAddItem> = ['offerKey', 'quantity']
  const buyerFields: Array<keyof CartBuyer> = ['email', 'phoneNumber', 'firstName', 'lastName']
  const addressFields: Array<keyof CartAddress> = [
    'methodId',
    'streetAddress',
    'addressLocality',
    'addressRegion',
    'postalCode',
    'addressCountry',
  ]
  const deliveryOptionFields: Array<keyof CartDeliveryOption> = [
    'methodId',
    'groupId',
    'selectedOptionId',
  ]
  const buyerHasNoStringIndex: HasStringIndex<CartBuyer> = false
  const addressHasNoStringIndex: HasStringIndex<CartAddress> = false
  const optionHasNoStringIndex: HasStringIndex<CartDeliveryOption> = false

  expect(createFields as string[]).not.toContain('merchantId')
  expect(createFields as string[]).not.toContain('merchantDomain')
  expect(addFields as string[]).not.toContain('productVariantId')
  expect(addFields).toContain('offerKey')
  expect(buyerFields).toContain('email')
  expect(addressFields).toContain('addressCountry')
  expect(deliveryOptionFields).toContain('selectedOptionId')
  expect(buyerHasNoStringIndex).toBeFalse()
  expect(addressHasNoStringIndex).toBeFalse()
  expect(optionHasNoStringIndex).toBeFalse()
})

test('settings expose editable scoped product size preferences', () => {
  type Settings = components['schemas']['UserSettingsResponse']
  type UpdateSettings = components['schemas']['UpdateUserSettingsRequest']
  type Preference = components['schemas']['UserProductSearchPreferenceResponse']
  const settingsFields: Array<keyof Settings> = ['productSearchPreferences']
  const updateFields: Array<keyof UpdateSettings> = ['productSearchPreferences']
  const preferenceFields: Array<keyof Preference> = ['scope', 'attributeName', 'values']

  expect(settingsFields).toContain('productSearchPreferences')
  expect(updateFields).toContain('productSearchPreferences')
  expect(preferenceFields).toContain('values')

  const preferencePaths: Array<keyof paths> = [
    '/api/users/me/settings/product-search-preferences/{scope}',
  ]
  expect(preferencePaths).toHaveLength(1)
})

test('delivery locations expose validated UCP shipping fields', () => {
  type Suggestion = components['schemas']['LocationSuggestionResponse']
  type SavedLocation = components['schemas']['UserLocationResponse']
  type LocationRequest = components['schemas']['UserLocationRequest']
  const suggestionFields: Array<keyof Suggestion> = [
    'id',
    'city',
    'country',
    'region',
    'postalCode',
  ]
  const savedFields: Array<keyof SavedLocation> = ['id', 'code', 'region', 'postalCode']
  const requestFields: Array<keyof LocationRequest> = ['id']
  const locationPaths: Array<keyof paths> = ['/api/locations/suggestions']

  expect(suggestionFields).toContain('country')
  expect(savedFields).toContain('region')
  expect(requestFields).toEqual(['id'])
  expect(locationPaths).toHaveLength(1)
})

test('Discover conversation persistence uses optimistic revisions', () => {
  type Request = components['schemas']['UserDiscoverConversationRequest']
  type Response = components['schemas']['UserDiscoverConversationResponse']
  const requestFields: Array<keyof Request> = ['expectedRevision']
  const responseFields: Array<keyof Response> = ['revision']

  expect(requestFields).toContain('expectedRevision')
  expect(responseFields).toContain('revision')

  const ownedDetailPaths: Array<keyof paths> = [
    '/api/v1/users/me/discover/conversations/{conversationId}',
  ]
  expect(ownedDetailPaths).toHaveLength(1)

  type ProductResultSet = components['schemas']['UserDiscoverProductResultSetResponse']
  const productResultSetFields: Array<keyof ProductResultSet> = [
    'resultSetId',
    'products',
    'unavailableCount',
  ]
  const productResultSetPaths: Array<keyof paths> = [
    '/api/v1/users/me/discover/conversations/{conversationId}/product-result-sets/{resultSetId}',
  ]
  expect(productResultSetFields).toContain('products')
  expect(productResultSetPaths).toHaveLength(1)
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
    'merchantOrigin',
    'productVariantId',
  ]
  expect(savedPaths).toHaveLength(1)
  expect(savedOfferFields).toContain('offerKey')
  expect(savedOfferFields as string[]).not.toContain('merchantDomain')

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
    'merchantOrigin',
  ]
  expect(savedProductFields).toContain('details')
  expect(savedDetailFields).toContain('variants')
  expect(savedDetailFields).toContain('messages')
  expect(savedDetailFields).toContain('ratingScore')
  expect(savedDetailFields).toContain('ratingScaleMax')
  expect(savedDetailFields).toContain('reviewCount')
  expect(savedDetailFields).toContain('merchantName')
  expect(savedDetailFields).toContain('merchantOrigin')

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
    '/api/carts/{cartId}/checkout/embedded/{sessionId}/opened',
    '/api/carts/{cartId}/checkout/embedded/{sessionId}/complete',
    '/api/carts/{cartId}/checkout/embedded/{sessionId}/cancel',
  ]
  type Bootstrap = components['schemas']['EmbeddedCheckoutBootstrapResponse']
  const fields: Array<keyof Bootstrap> = [
    'action',
    'sessionId',
    'cartId',
    'checkoutAttemptId',
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

  expect(expectedPaths).toHaveLength(4)
  expect(fields).toContain('checkoutAttemptId')
  expect(fields as string[]).not.toContain('clientSecret')
  expect(fields as string[]).not.toContain('accessToken')
})

test('checkout exposes typed saved contact and delivery details for explicit reuse', () => {
  type Checkout = components['schemas']['CheckoutResponse']
  type SavedDetails = components['schemas']['SavedCheckoutDetailsResponse']
  type SavedBuyer = components['schemas']['SavedCheckoutBuyerResponse']
  type SavedAddress = components['schemas']['SavedCheckoutShippingAddressResponse']
  type IsOptional<T, K extends keyof T> = object extends Pick<T, K> ? true : false
  type AcceptsNull<T, K extends keyof T> = null extends T[K] ? true : false

  const checkoutFields: Array<keyof Checkout> = ['savedCheckoutDetails']
  const savedDetailFields: Array<keyof SavedDetails> = ['buyer', 'shippingAddress', 'updatedAt']
  const buyerFields: Array<keyof SavedBuyer> = ['email', 'firstName', 'lastName', 'phoneNumber']
  const addressFields: Array<keyof SavedAddress> = [
    'streetAddress',
    'extendedAddress',
    'addressLocality',
    'addressRegion',
    'postalCode',
    'addressCountry',
  ]
  const savedDetailsOptional: IsOptional<Checkout, 'savedCheckoutDetails'> = true
  const savedDetailsNullable: AcceptsNull<Checkout, 'savedCheckoutDetails'> = true
  const phoneOptional: IsOptional<SavedBuyer, 'phoneNumber'> = true
  const phoneNullable: AcceptsNull<SavedBuyer, 'phoneNumber'> = true
  const extendedAddressOptional: IsOptional<SavedAddress, 'extendedAddress'> = true
  const extendedAddressNullable: AcceptsNull<SavedAddress, 'extendedAddress'> = true
  const addressRegionOptional: IsOptional<SavedAddress, 'addressRegion'> = true
  const addressRegionNullable: AcceptsNull<SavedAddress, 'addressRegion'> = true

  expect(checkoutFields).toContain('savedCheckoutDetails')
  expect(savedDetailFields).toEqual(['buyer', 'shippingAddress', 'updatedAt'])
  expect(buyerFields).toEqual(['email', 'firstName', 'lastName', 'phoneNumber'])
  expect(addressFields).toEqual([
    'streetAddress',
    'extendedAddress',
    'addressLocality',
    'addressRegion',
    'postalCode',
    'addressCountry',
  ])
  expect(savedDetailsOptional).toBeTrue()
  expect(savedDetailsNullable).toBeTrue()
  expect(phoneOptional).toBeTrue()
  expect(phoneNullable).toBeTrue()
  expect(extendedAddressOptional).toBeTrue()
  expect(extendedAddressNullable).toBeTrue()
  expect(addressRegionOptional).toBeTrue()
  expect(addressRegionNullable).toBeTrue()
})

test('inventory exposes uploaded photo details without legacy browser photo inputs', () => {
  type Inventory = components['schemas']['UserInventoryItemResponse']
  type CommerceReference = components['schemas']['UserInventoryCommerceReferenceResponse']
  type SelectedOption = components['schemas']['UserInventorySelectedOptionResponse']
  type AddInventory = components['schemas']['AddUserInventoryItemRequest']
  type UpdateInventory = components['schemas']['UpdateUserInventoryItemRequest']
  type HasCommerceReference<T> = 'commerceReference' extends keyof T ? true : false
  type HasCheckoutAttempt<T> = 'sourceCheckoutAttemptId' extends keyof T ? true : false
  type HasLegacyImageInput<T> = 'imageUrl' extends keyof T ? true : false
  type IsOptional<T, K extends keyof T> = object extends Pick<T, K> ? true : false
  type AcceptsNull<T, K extends keyof T> = null extends T[K] ? true : false
  type HasLegacyPhotoRoute = '/api/users/me/inventory/photos' extends keyof paths ? true : false

  const inventoryFields: Array<keyof Inventory> = [
    'sourceCheckoutAttemptId',
    'commerceReference',
    'photoPath',
    'purchasedOn',
    'size',
    'color',
    'material',
  ]
  const commerceReferenceFields: Array<keyof CommerceReference> = [
    'provider',
    'merchantIntegrationId',
    'externalMerchantId',
    'canonicalProductKey',
    'offerKey',
    'sourceType',
    'sourceIdentity',
    'externalProductId',
    'externalVariantId',
    'selectedOptions',
  ]
  expect(commerceReferenceFields as string[]).not.toContain('externalMerchantDomain')
  const selectedOptionFields: Array<keyof SelectedOption> = ['group', 'name', 'value']
  const addHasNoCommerceReference: HasCommerceReference<AddInventory> = false
  const addHasNoCheckoutAttempt: HasCheckoutAttempt<AddInventory> = false
  const updateHasNoCommerceReference: HasCommerceReference<UpdateInventory> = false
  const updateHasNoCheckoutAttempt: HasCheckoutAttempt<UpdateInventory> = false
  const addHasNoLegacyImageInput: HasLegacyImageInput<AddInventory> = false
  const updateHasNoLegacyImageInput: HasLegacyImageInput<UpdateInventory> = false
  const addPhotoPathRequired: IsOptional<AddInventory, 'photoPath'> = false
  const addCategoryRequired: IsOptional<AddInventory, 'category'> = false
  const addAttributesOptional: IsOptional<AddInventory, 'attributes'> = true
  const responsePhotoPathOptional: IsOptional<Inventory, 'photoPath'> = true
  const responsePhotoPathNullable: AcceptsNull<Inventory, 'photoPath'> = true
  const responsePurchasedOnNullable: AcceptsNull<Inventory, 'purchasedOn'> = true
  const hasNoLegacyPhotoRoute: HasLegacyPhotoRoute = false

  expect(inventoryFields).toContain('commerceReference')
  expect(inventoryFields).toContain('photoPath')
  expect(commerceReferenceFields).toContain('selectedOptions')
  expect(selectedOptionFields).toEqual(['group', 'name', 'value'])
  expect(addHasNoCommerceReference).toBeFalse()
  expect(addHasNoCheckoutAttempt).toBeFalse()
  expect(updateHasNoCommerceReference).toBeFalse()
  expect(updateHasNoCheckoutAttempt).toBeFalse()
  expect(addHasNoLegacyImageInput).toBeFalse()
  expect(updateHasNoLegacyImageInput).toBeFalse()
  expect(addPhotoPathRequired).toBeFalse()
  expect(addCategoryRequired).toBeFalse()
  expect(addAttributesOptional).toBeTrue()
  expect(responsePhotoPathOptional).toBeTrue()
  expect(responsePhotoPathNullable).toBeTrue()
  expect(responsePurchasedOnNullable).toBeTrue()
  expect(hasNoLegacyPhotoRoute).toBeFalse()
})
