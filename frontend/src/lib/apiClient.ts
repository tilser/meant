import createClient, { type Middleware } from 'openapi-fetch'

import type { components, paths } from '../api/schema'
import { parseErrorResponse, parseJsonResponse } from './apiError'
export { ApiError, parseErrorResponse, parseJsonResponse } from './apiError'
import { supabase } from './supabase'

const API_URL = import.meta.env.VITE_MEANT_API_URL ?? 'http://localhost:8080'
const PROFILE_PICTURE_BUCKET = 'profile-pictures'
const PROFILE_PICTURE_SIGNED_URL_SECONDS = 60 * 60

/** Profile shape served by the backend, sourced from the generated OpenAPI schema. */
export type UserProfile = components['schemas']['UserResponse']
export type GroupedProductSearchProfile =
  components['schemas']['UserGroupedProductSearchV1Response']
export type CanonicalProductDetailProfile =
  components['schemas']['UserCanonicalProductDetailV1Response']
export type CanonicalProductProfile = components['schemas']['CanonicalProductResponse']
export type CanonicalOfferProfile = components['schemas']['OfferResponse']

export interface ShoppingFilterProfile {
  id: string
  label: string
  description: string
  category: string
  polarity: string
  displayOrder: number
}

export interface UserSettingsLocation {
  country: string
  code: string
  city: string
}

export interface UserProductSearchPreferenceProfile {
  scope: string
  attributeName: 'SIZE'
  values: string[]
}

export interface UserSettingsProfile {
  budget: number | null
  clothingFit: 'men' | 'women' | 'other' | null
  location: UserSettingsLocation | null
  locations: UserSettingsLocation[]
  filters: ShoppingFilterProfile[]
  availableFilters: ShoppingFilterProfile[]
  parsedFilterIds: string[]
  unmappedPreferences: string[]
  productSearchPreferences?: UserProductSearchPreferenceProfile[]
  createdAt: string
  updatedAt: string
}

export interface UserProductSearchProductProfile {
  productKey: string
  productHash: string
  merchantId: string
  merchantDomain: string
  merchantName: string | null
  endpoint: string | null
  merchantRank: number
  merchantSemanticScore: number
  merchantRerankScore: number
  productId: string
  title: string
  descriptionHtml: string | null
  url: string | null
  imageUrl: string | null
  priceMinAmount: number | null
  priceMaxAmount: number | null
  priceCurrency: string | null
  listPriceAmount: number | null
  listPriceCurrency: string | null
  ratingScore: number | null
  reviewCount: number | null
  media: ProductMediaProfile[]
  categories: ProductCategoryProfile[]
  certifications: string[]
  materials: string[]
  skus: string[]
  collections: string[]
  attributes: ProductAttributeProfile[]
  available: boolean | null
  detailError: string | null
  detailDescription: string | null
  detailImageUrl: string | null
  detailPriceMin: string | null
  detailPriceMax: string | null
  detailPriceCurrency: string | null
  selectedVariantId: string | null
  selectedVariantTitle: string | null
  selectedVariantPriceAmount: string | null
  selectedVariantPriceCurrency: string | null
  selectedVariantImageUrl: string | null
  selectedVariantImageAltText: string | null
  selectedVariantAvailable: boolean | null
  catalogRank: number
  productRerankScore: number
  rank: number
  matchScore: number
  whyMeantForYou: string
  matchedFilterIds: string[]
  missedFilterIds: string[]
  inventoryRelationship: UserInventoryRecommendationRelationship
  inventoryItemId: string | null
  inventoryItemName: string | null
}

export interface ProductMediaProfile {
  type: string | null
  url: string | null
  altText: string | null
}

export interface ProductCategoryProfile {
  value: string | null
  taxonomy: string | null
}

export interface ProductAttributeProfile {
  name: string | null
  value: string | null
}

export interface MerchantProductImageProfile {
  url: string | null
  altText: string | null
}

export interface MerchantProductDetailMediaProfile {
  type: string | null
  url: string | null
  altText: string | null
  previewImageUrl: string | null
}

export interface ProductOptionProfile {
  name: string | null
  values: string[] | null
  valueDetails?: ProductOptionValueProfile[] | null
}

export interface ProductOptionValueProfile {
  value: string | null
  available: boolean | null
  exists: boolean | null
}

export interface ProductSelectedOptionProfile {
  name: string | null
  value: string | null
}

export interface MerchantProductDetailsProfile {
  endpoint: string | null
  productId: string | null
  handle: string | null
  title: string | null
  description: string | null
  url: string | null
  imageUrl: string | null
  images: MerchantProductImageProfile[]
  media: MerchantProductDetailMediaProfile[]
  categories: ProductCategoryProfile[]
  tags: string[]
  options: ProductOptionProfile[]
  variants: MerchantProductVariantProfile[]
  totalVariants: number | null
  priceMin: string | null
  priceMax: string | null
  priceCurrency: string | null
  listPriceMin: string | null
  listPriceMax: string | null
  listPriceCurrency: string | null
  requiresSellingPlan: boolean | null
  selectedVariantId: string | null
  selectedVariantTitle: string | null
  selectedVariantPriceAmount: string | null
  selectedVariantPriceCurrency: string | null
  selectedVariantSku: string | null
  selectedVariantListPriceAmount: string | null
  selectedVariantListPriceCurrency: string | null
  selectedVariantImageUrl: string | null
  selectedVariantImageAltText: string | null
  selectedVariantAvailable: boolean | null
  selectedOptions: ProductSelectedOptionProfile[]
  skus: string[]
  certifications: string[]
  materials: string[]
  collections: string[]
  attributes: ProductAttributeProfile[]
  messages: ProductMessageProfile[]
}

export interface MerchantProductVariantProfile {
  variantId: string | null
  handle: string | null
  title: string | null
  description: string | null
  url: string | null
  priceAmount: string | null
  priceCurrency: string | null
  listPriceAmount: string | null
  listPriceCurrency: string | null
  sku: string | null
  imageUrl: string | null
  imageAltText: string | null
  media: MerchantProductDetailMediaProfile[]
  available: boolean | null
  selectedOptions: ProductSelectedOptionProfile[]
  categories: ProductCategoryProfile[]
  tags: string[]
  attributes: ProductAttributeProfile[]
}

export interface ProductMessageProfile {
  type: string | null
  code: string | null
  path: string | null
  contentType: string | null
  content: string | null
  severity: string | null
  presentation: string | null
  imageUrl: string | null
  url: string | null
}

export type ProductReviewProviderProfile = 'KLAVIYO' | 'YOTPO' | 'UNKNOWN' | 'NONE'

export interface ProductReviewProfile {
  externalId: string | null
  author: string | null
  rating: number | null
  content: string | null
  verified: boolean | null
  createdAt: string | null
  variantId: string | null
  variantTitle: string | null
}

export interface ProductReviewsProfile {
  merchantId: string
  productId: string
  provider: ProductReviewProviderProfile
  rating: number | null
  reviewCount: number
  hasMore: boolean
  reviews: ProductReviewProfile[]
  cached: boolean
  supported: boolean
  message: string | null
}

export interface UserProductSearchSuggestionsProfile {
  suggestions: string[]
}

export type ProductSearchQualificationFilter = components['schemas']['UserProductSearchFilterKind']
export type ProductSearchQualificationProfile =
  components['schemas']['UserProductSearchQualificationResponse']

export type UserTasteBehaviorType = 'SAVE' | 'PURCHASE' | 'DISMISS'
export type UserTasteSignalStatus = 'ACTIVE' | 'DISABLED'
export type UserTasteSignalType =
  'FILTER' | 'BRAND' | 'CATEGORY' | 'MATERIAL' | 'CERTIFICATION' | 'QUERY'
export type UserTasteSuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export interface UserTasteSignalProfile {
  id: string
  signalType: UserTasteSignalType
  signalKey: string
  label: string
  weight: number
  positiveCount: number
  negativeCount: number
  lastBehavior: string
  suggestedFilterId: string | null
  suggestionStatus: UserTasteSuggestionStatus
  status: UserTasteSignalStatus
  createdAt: string
  updatedAt: string
}

export interface UserTasteSuggestionProfile {
  filterId: string
  label: string
  description: string
  reason: string
  score: number
}

export interface UserTasteProfile {
  profileHash: string
  signals: UserTasteSignalProfile[]
  suggestions: UserTasteSuggestionProfile[]
}

export interface UserProductDiscoveryProfile {
  savedProducts: UserSavedProductProfile[]
  recentProducts: UserProductSearchProductProfile[]
}

export interface UserPopularProductSearchProfile {
  displayQuery: string
  query: string
}

export interface UserDiscoverConversationProfile {
  conversationId: string
  title: string
  createdAt: string
  updatedAt: string
  threadJson: string
  revision: number
}

export type UserDiscoverProductResultSetProfile =
  components['schemas']['UserDiscoverProductResultSetResponse']

export interface UserSavedProductOfferProfile {
  offerKey?: string | null
  merchant: string | null
  price: number | null
  priceMinorUnits: number | null
  priceCurrency: string | null
  delivery: string | null
  merchantId: string | null
  merchantDomain: string | null
  productVariantId: string | null
  variantTitle: string | null
  available: boolean | null
}

export interface UserSavedProductReviewProfile {
  score: number | null
  count: number | null
  insight: string | null
}

export type UserSavedProductDetailsProfile = Omit<MerchantProductDetailsProfile, 'endpoint'> & {
  ratingScore?: number | null
  ratingScaleMax?: number | null
  reviewCount?: number | null
  merchantName?: string | null
}

export interface ProductVariantSelectionProfile {
  details: UserSavedProductDetailsProfile
  selectedOfferKey?: string | null
  selectedOffer?: CanonicalOfferProfile | null
  cartable: boolean
}

export interface UserSavedProductProfile {
  id: string
  productHash: string | null
  name: string | null
  brand: string | null
  category: string | null
  tone: string | null
  imageUrl: string | null
  productUrl: string | null
  remote: boolean | null
  match: number | null
  priceFrom: number | null
  priceFromMinorUnits: number | null
  priceCurrency: string | null
  merchants: number | null
  satisfies: string[]
  misses: string[]
  note: string | null
  pros: string[]
  cons: string[]
  review: UserSavedProductReviewProfile | null
  offers: UserSavedProductOfferProfile[]
  needs: string | null
  provides: string[]
  marketCountry: string | null
  marketContextApplied: boolean
  commercialFactsAuthoritative: boolean
  details?: UserSavedProductDetailsProfile | null
  createdAt: string
  updatedAt: string
}

export type CatalogProductReferenceInput = {
  provider: string
  sourceType:
    | 'MERCHANT_STOREFRONT'
    | 'PROVIDER_CATALOG'
    | 'DATASET_IMPORT'
    | 'CACHED_OBSERVATION'
    | 'MANUAL_ASSERTION'
  sourceIdentity: string
  localMerchantId?: string
  merchantIntegrationId?: string
  externalMerchantId?: string
  externalMerchantDomain?: string
  externalProductId: string
  externalVariantId?: string
  selectedOptions: { group?: string; name: string; value: string }[]
  offerKey?: string
  components?: Array<{
    externalProductId: string
    externalVariantId?: string
    quantity: number
    selectedOptions: { group?: string; name: string; value: string }[]
  }>
  sellingPlan?: {
    groupId?: string
    planId?: string
    options: { name: string; value: string }[]
  }
}

export type SaveUserProductInput = Omit<
  UserSavedProductProfile,
  | 'commercialFactsAuthoritative'
  | 'createdAt'
  | 'updatedAt'
  | 'priceFromMinorUnits'
  | 'priceCurrency'
  | 'marketCountry'
  | 'marketContextApplied'
  | 'offers'
> & {
  offers: Array<Omit<UserSavedProductOfferProfile, 'priceMinorUnits' | 'priceCurrency'>>
  catalogReference?: CatalogProductReferenceInput
  selectedOfferKey?: string
}

export interface MerchantProfile {
  id: string
  domain: string
  name: string
  description: string
  advertisedMcpEndpoint: string | null
  profileMcpEndpoint: string | null
  supportsIdentityLinking: boolean
}

export interface MerchantIdentityLinkProfile {
  merchantId: string
  merchantDomain: string
  merchantName: string
  status: 'PENDING' | 'CONNECTED'
  scope: string | null
  expiresAt: string | null
  updatedAt: string
}

export interface MerchantIdentityAuthorizationProfile {
  merchantId: string
  authorizationUrl: string
  state: string
  scopes: string[]
}

export type CartProfile = components['schemas']['CartResponse']
export type CartDeliveryGroupProfile = NonNullable<CartProfile['deliveryGroups']>[number]
export type CheckoutProfile = components['schemas']['CheckoutResponse']
export type CheckoutMessageProfile = components['schemas']['MessageResponse']
export type CheckoutConsentProfile = components['schemas']['CheckoutConsentResponse']
export type CheckoutCompletionProfile = components['schemas']['CheckoutCompletionResponse']
export type EmbeddedCheckoutBootstrapProfile =
  components['schemas']['EmbeddedCheckoutBootstrapResponse']

export interface CheckoutBuyerInput {
  email: string
  firstName: string
  lastName: string
  phoneNumber?: string | null
}

export interface CheckoutShippingAddressInput {
  streetAddress: string
  extendedAddress?: string | null
  addressLocality: string
  addressRegion?: string | null
  postalCode: string
  addressCountry: string
}

export interface UpdateCheckoutInput {
  cartId: string
  buyer: CheckoutBuyerInput
  shippingAddress: CheckoutShippingAddressInput
  discountCodes?: readonly string[]
  expectedUserId?: string
}

export interface CreateCheckoutConsentInput {
  cartId: string
  checkoutId: string
  paymentInstrumentReference: string
  shippingMethod?: string | null
  presentedTermsHash?: string | null
  expectedUserId?: string
}

export interface CompleteCartCheckoutInput {
  cartId: string
  buyerConsentId: string
  checkoutId: string
  handler: string
  amountMinor: number
  currency: string
  token: string
  credentialType?: string
  credentialDetails?: CheckoutCredentialDetailsInput
  idempotencyKey?: string
  expectedUserId?: string
}

export interface CheckoutCredentialDetailsInput {
  source?: string
}

export interface DiscountCodeProfile {
  code: string
  title: string | null
  description: string | null
  sourceUrl: string | null
  confidence: number | null
  restrictions: string | null
  validUntil: string | null
  expiresAt: string | null
  validationMessage: string | null
}

export interface DiscountCodeSearchProfile {
  merchantId: string
  merchantDomain: string
  cached: boolean
  searchedAt: string
  expiresAt: string
  codes: DiscountCodeProfile[]
}

export interface OrderLineProfile {
  id: string
  productKey: string | null
  productId: string | null
  productTitle: string | null
  merchantName: string | null
  productVariantId: string | null
  variantTitle: string | null
  sku: string | null
  imageUrl: string | null
  productUrl: string | null
  quantity: number | null
  unitAmount: string | null
  totalAmount: string | null
  currency: string | null
}

export interface OrderProfile {
  id: string
  merchantId: string
  merchantDomain: string
  merchantName: string | null
  remoteOrderId: string
  displayId: string
  orderNumber: string | null
  state: string
  status: string
  statusNote: string
  date: string
  totalAmount: string | null
  subtotalAmount: string | null
  currency: string | null
  totalQuantity: number
  orderStatusUrl: string | null
  lines: OrderLineProfile[]
  createdAt: string
  updatedAt: string
}

export type UserInventoryCategory = 'APPAREL' | 'PANTRY' | 'HOME' | 'OTHER'
export type UserInventorySource = 'MANUAL' | 'PHOTO' | 'MEANT_PURCHASE'
export type UserInventoryRecommendationRelationship =
  'NONE' | 'DUPLICATE' | 'COMPLEMENT' | 'RESTOCK'

export interface UserInventoryItemProfile {
  id: string
  source: UserInventorySource
  sourceProductKey: string | null
  productHash: string | null
  name: string
  brand: string | null
  category: UserInventoryCategory
  description: string | null
  imageUrl: string | null
  productUrl: string | null
  photoUrl: string | null
  quantity: number
  unit: string | null
  location: string | null
  notes: string | null
  attributes: string[]
  consumable: boolean
  restockEnabled: boolean
  restockThreshold: number | null
  purchasedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface UserInventoryExportProfile {
  exportedAt: string
  items: UserInventoryItemProfile[]
}

export interface UserInventoryItemInput {
  name: string
  brand?: string | null
  category?: UserInventoryCategory | null
  description?: string | null
  imageUrl?: string | null
  productUrl?: string | null
  quantity?: number | null
  unit?: string | null
  location?: string | null
  notes?: string | null
  attributes?: readonly string[]
  consumable?: boolean | null
  restockEnabled?: boolean | null
  restockThreshold?: number | null
}

export interface UserInventoryPhotoInput {
  photoUrl: string
  name?: string | null
  brand?: string | null
  category?: UserInventoryCategory | null
  description?: string | null
  quantity?: number | null
  unit?: string | null
  location?: string | null
  notes?: string | null
  attributes?: readonly string[]
  consumable?: boolean | null
  restockEnabled?: boolean | null
  restockThreshold?: number | null
}

export interface UserInventoryItemUpdateInput {
  name?: string | null
  brand?: string | null
  category?: UserInventoryCategory | null
  description?: string | null
  imageUrl?: string | null
  productUrl?: string | null
  photoUrl?: string | null
  quantity?: number | null
  unit?: string | null
  location?: string | null
  notes?: string | null
  attributes?: readonly string[]
  consumable?: boolean | null
  restockEnabled?: boolean | null
  restockThreshold?: number | null
}

export interface CartAddItemInput {
  productVariantId: string
  quantity: number
}

export interface SelectedOfferCartAddItemInput {
  offerKey: string
  quantity: number
}

export type CartBuyerIdentityInput = components['schemas']['CartBuyerIdentityRequest']
export type CartDeliveryAddressInput = components['schemas']['CartDeliveryAddressSelectionRequest']
export type CartDeliveryOptionSelectionInput =
  components['schemas']['CartDeliveryOptionSelectionRequest']

type SearchDiscountCodesBuyerIdentityInput =
  components['schemas']['SearchDiscountCodesBuyerIdentityRequest']
type SearchDiscountCodesDeliveryAddressInput =
  components['schemas']['SearchDiscountCodesDeliveryAddressSelectionRequest']
type SearchDiscountCodesDeliveryOptionInput =
  components['schemas']['SearchDiscountCodesDeliveryOptionSelectionRequest']
type SearchDiscountCodesItemInput = components['schemas']['SearchDiscountCodesItemRequest']

export interface SearchDiscountCodesInput {
  merchantId?: string | null
  merchantDomain?: string | null
  items: readonly SearchDiscountCodesItemInput[]
  buyerIdentity?: SearchDiscountCodesBuyerIdentityInput | null
  deliveryAddressesToAdd?: readonly SearchDiscountCodesDeliveryAddressInput[]
  deliveryAddressesToReplace?: readonly SearchDiscountCodesDeliveryAddressInput[]
  selectedDeliveryOptions?: readonly SearchDiscountCodesDeliveryOptionInput[]
  expectedUserId?: string
}

/** Injects the current Supabase access token as a Bearer header on every request. */
const authMiddleware: Middleware = {
  async onRequest({ request }) {
    const { data } = await supabase.auth.getSession()
    const token = data.session?.access_token
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
}

const client = createClient<paths>({ baseUrl: API_URL })
client.use(authMiddleware)

async function authHeaders(expectedUserId?: string): Promise<HeadersInit> {
  const { data } = await supabase.auth.getSession()
  const session = data.session
  if (expectedUserId && session?.user.id !== expectedUserId) {
    throw new Error('Authenticated user changed before request')
  }
  const token = session?.access_token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export interface AccountBoundRequestOptions {
  expectedUserId?: string
  signal?: AbortSignal
}

/** Fetches the current user, creating the backend profile row on first call (upsert-on-read). */
export async function getCurrentUser(): Promise<UserProfile> {
  const { data, error } = await client.GET('/api/users/me')
  if (error || !data) {
    throw new Error('Failed to load current user')
  }
  return data
}

export async function updateProfile(
  input: { firstName: string; surname: string | null },
  options?: AccountBoundRequestOptions,
): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ firstName: input.firstName, surname: input.surname ?? undefined }),
    signal: options?.signal,
  })
  return parseJsonResponse<UserProfile>(response, 'Failed to update profile')
}

export async function updateProfilePicture(
  profilePicturePath: string,
  options?: AccountBoundRequestOptions,
): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me/profile-picture`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ profilePicturePath }),
    signal: options?.signal,
  })
  return parseJsonResponse<UserProfile>(response, 'Failed to update profile picture')
}

export async function updateNewsletterSubscription(
  newsletter: boolean,
  options?: AccountBoundRequestOptions,
): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me/newsletter`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ newsletter }),
    signal: options?.signal,
  })
  return parseJsonResponse<UserProfile>(response, 'Failed to update newsletter subscription')
}

export async function removeProfilePicture(
  options?: AccountBoundRequestOptions,
): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me/profile-picture`, {
    method: 'DELETE',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserProfile>(response, 'Failed to remove profile picture')
}

export async function getProfilePictureUrl(
  profilePicturePath?: string | null,
): Promise<string | null> {
  if (!profilePicturePath) {
    return null
  }
  const { data, error } = await supabase.storage
    .from(PROFILE_PICTURE_BUCKET)
    .createSignedUrl(profilePicturePath, PROFILE_PICTURE_SIGNED_URL_SECONDS)
  if (error) {
    throw new Error('Failed to load profile picture')
  }
  return data.signedUrl
}

export async function uploadProfilePictureFile(
  userId: string,
  file: File,
): Promise<{
  path: string
  signedUrl: string | null
}> {
  await authHeaders(userId)
  const extension = profilePictureExtension(file)
  const path = `${userId}/${randomUuid()}.${extension}`
  const { data, error } = await supabase.storage.from(PROFILE_PICTURE_BUCKET).upload(path, file, {
    cacheControl: '3600',
    contentType: file.type,
    upsert: false,
  })
  if (error) {
    throw new Error('Failed to upload profile picture')
  }
  return {
    path: data.path,
    signedUrl: await getProfilePictureUrl(data.path).catch(() => null),
  }
}

export async function deleteProfilePictureFile(
  profilePicturePath?: string | null,
  options?: AccountBoundRequestOptions,
): Promise<void> {
  if (!profilePicturePath) {
    return
  }
  await authHeaders(options?.expectedUserId)
  await supabase.storage.from(PROFILE_PICTURE_BUCKET).remove([profilePicturePath])
}

function profilePictureExtension(file: File): 'jpg' | 'png' | 'webp' {
  if (file.type === 'image/png') {
    return 'png'
  }
  if (file.type === 'image/webp') {
    return 'webp'
  }
  return 'jpg'
}

function randomUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    return [...bytes]
      .map((byte, index) => {
        const value = byte.toString(16).padStart(2, '0')
        return [4, 6, 8, 10].includes(index) ? `-${value}` : value
      })
      .join('')
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (candidate) => {
    const random = Math.floor(Math.random() * 16)
    return (candidate === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
}

export async function getUserSettings(
  options?: AccountBoundRequestOptions,
): Promise<UserSettingsProfile> {
  const response = await fetch(`${API_URL}/api/users/me/settings`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to load user settings')
}

export async function getMerchants(
  options?: AccountBoundRequestOptions,
): Promise<MerchantProfile[]> {
  const response = await fetch(`${API_URL}/api/merchants`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<MerchantProfile[]>(response, 'Failed to load merchants')
}

export async function getMerchantIdentityLinks(
  options?: AccountBoundRequestOptions,
): Promise<MerchantIdentityLinkProfile[]> {
  const response = await fetch(`${API_URL}/api/merchants/identity-links`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<MerchantIdentityLinkProfile[]>(
    response,
    'Failed to load merchant account connections',
  )
}

export async function startMerchantIdentityAuthorization(
  merchantId: string,
  options?: AccountBoundRequestOptions,
): Promise<MerchantIdentityAuthorizationProfile> {
  const response = await fetch(
    `${API_URL}/api/merchants/${merchantId}/identity-link/authorization`,
    {
      method: 'POST',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<MerchantIdentityAuthorizationProfile>(
    response,
    'Failed to start merchant account linking',
  )
}

export async function completeMerchantIdentityAuthorization(input: {
  state: string
  code: string
  issuer?: string | null
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<MerchantIdentityLinkProfile> {
  const response = await fetch(`${API_URL}/api/merchants/identity-links/oauth/callback`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      state: input.state,
      code: input.code,
      issuer: input.issuer ?? undefined,
    }),
    signal: input.signal,
  })
  return parseJsonResponse<MerchantIdentityLinkProfile>(
    response,
    'Failed to complete merchant account linking',
  )
}

export async function revokeMerchantIdentityLink(
  merchantId: string,
  options?: AccountBoundRequestOptions,
): Promise<void> {
  const response = await fetch(`${API_URL}/api/merchants/identity-links/${merchantId}`, {
    method: 'DELETE',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  if (!response.ok) {
    throw new Error('Failed to revoke merchant account connection')
  }
}

export async function updateUserSettings(
  input: {
    budget?: number | null
    clothingFit?: 'men' | 'women' | 'other' | 'none'
    location?: UserSettingsLocation | null
    locations?: readonly UserSettingsLocation[]
    filterIds?: readonly string[]
    preferenceDescription?: string
    productSearchPreferences?: readonly UserProductSearchPreferenceProfile[]
  },
  options?: AccountBoundRequestOptions,
): Promise<UserSettingsProfile> {
  const response = await fetch(`${API_URL}/api/users/me/settings`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      budget: typeof input.budget === 'number' ? input.budget : undefined,
      budgetUnlimited: input.budget === null ? true : undefined,
      clothingFit: input.clothingFit,
      location: input.location,
      locations: input.locations,
      filterIds: input.filterIds,
      preferenceDescription: input.preferenceDescription,
      productSearchPreferences: input.productSearchPreferences,
    }),
    signal: options?.signal,
  })
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to update user settings')
}

export async function deleteUserProductSearchPreference(
  scope: string,
  options?: AccountBoundRequestOptions,
): Promise<UserSettingsProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/settings/product-search-preferences/${encodeURIComponent(scope)}`,
    {
      method: 'DELETE',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<UserSettingsProfile>(
    response,
    'Failed to remove saved product search preference',
  )
}

export async function qualifyProductSearch(input: {
  conversationId: string
  qualificationId?: string | null
  message: string
  merchantId?: string | null
  signal?: AbortSignal
  expectedUserId?: string
}): Promise<ProductSearchQualificationProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/me/product-search-qualifications`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId: input.conversationId,
      qualificationId: input.qualificationId ?? undefined,
      message: input.message,
      merchantId: input.merchantId ?? undefined,
    }),
    signal: input.signal,
  })
  return parseJsonResponse<ProductSearchQualificationProfile>(
    response,
    'Failed to qualify product search',
  )
}

export async function searchGroupedProducts(input: {
  query: string
  qualificationId: string
  merchantId?: string | null
  offset?: number
  limit?: number
  signal?: AbortSignal
  expectedUserId?: string
}): Promise<GroupedProductSearchProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/me/product-searches`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      query: input.query,
      qualificationId: input.qualificationId ?? undefined,
      merchantId: input.merchantId ?? undefined,
      offset: input.offset ?? undefined,
      limit: input.limit ?? undefined,
    }),
    signal: input.signal,
  })
  return parseJsonResponse<GroupedProductSearchProfile>(
    response,
    'Failed to search grouped products',
  )
}

export async function getCanonicalProductDetail(input: {
  canonicalProductKey: string
  selectedOfferKey?: string | null
  signal?: AbortSignal
  expectedUserId?: string
}): Promise<CanonicalProductDetailProfile> {
  const search = new URLSearchParams()
  if (input.selectedOfferKey) {
    search.set('selectedOfferKey', input.selectedOfferKey)
  }
  const suffix = search.size === 0 ? '' : `?${search.toString()}`
  const response = await fetch(
    `${API_URL}/api/v1/users/me/products/${encodeURIComponent(input.canonicalProductKey)}${suffix}`,
    {
      cache: 'no-store',
      headers: await authHeaders(input.expectedUserId),
      signal: input.signal,
    },
  )
  return parseJsonResponse<CanonicalProductDetailProfile>(
    response,
    'Failed to load canonical product details',
  )
}

export async function selectProductVariant(input: {
  anchorOfferKey: string
  selectedOptions: readonly { name: string; value: string }[]
  preferredOptionName?: string | null
  signal?: AbortSignal
  expectedUserId?: string
}): Promise<ProductVariantSelectionProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/me/product-variant-selections`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      anchorOfferKey: input.anchorOfferKey,
      selectedOptions: input.selectedOptions,
      ...(input.preferredOptionName?.trim()
        ? { preferredOptionName: input.preferredOptionName.trim() }
        : {}),
    }),
    signal: input.signal,
  })
  return parseJsonResponse<ProductVariantSelectionProfile>(
    response,
    'Failed to select product variant',
  )
}

export async function getUserProductSearchSuggestions(
  options?: AccountBoundRequestOptions,
): Promise<UserProductSearchSuggestionsProfile> {
  const response = await fetch(`${API_URL}/api/users/me/product-search-suggestions`, {
    cache: 'no-store',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserProductSearchSuggestionsProfile>(
    response,
    'Failed to generate product search suggestions',
  )
}

export async function getProductDiscovery(
  options?: AccountBoundRequestOptions,
): Promise<UserProductDiscoveryProfile> {
  const response = await fetch(`${API_URL}/api/users/me/product-discovery`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserProductDiscoveryProfile>(
    response,
    'Failed to load product discovery',
  )
}

export async function getMerchantProductDetails(input: {
  merchantId: string
  productId: string
  addressCountry?: string | null
  language?: string | null
  signal?: AbortSignal
  expectedUserId?: string
}): Promise<MerchantProductDetailsProfile> {
  const search = new URLSearchParams({ productId: input.productId })
  if (input.addressCountry) {
    search.set('addressCountry', input.addressCountry)
  }
  if (input.language) {
    search.set('language', input.language)
  }
  const response = await fetch(
    `${API_URL}/api/merchants/${encodeURIComponent(input.merchantId)}/product-details?${search.toString()}`,
    {
      cache: 'no-store',
      headers: await authHeaders(input.expectedUserId),
      signal: input.signal,
    },
  )
  return parseJsonResponse<MerchantProductDetailsProfile>(
    response,
    'Failed to load product details',
  )
}

export async function getProductReviews(input: {
  merchantId: string
  productId: string
  limit?: number
  offset?: number
  signal?: AbortSignal
  expectedUserId?: string
}): Promise<ProductReviewsProfile> {
  const search = new URLSearchParams({ productId: input.productId })
  if (input.limit !== undefined) {
    search.set('limit', String(input.limit))
  }
  if (input.offset !== undefined) {
    search.set('offset', String(input.offset))
  }
  const response = await fetch(
    `${API_URL}/api/reviews/merchants/${encodeURIComponent(input.merchantId)}/products?${search.toString()}`,
    {
      cache: 'no-store',
      headers: await authHeaders(input.expectedUserId),
      signal: input.signal,
    },
  )
  return parseJsonResponse<ProductReviewsProfile>(response, 'Failed to load product reviews')
}

export async function getUserInventoryItems(input?: {
  category?: UserInventoryCategory | null
  restockOnly?: boolean
  page?: number
  limit?: number
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<UserInventoryItemProfile[]> {
  const search = new URLSearchParams()
  if (input?.category) {
    search.set('category', input.category)
  }
  if (input?.restockOnly) {
    search.set('restockOnly', 'true')
  }
  if (input?.page !== undefined) {
    search.set('page', String(input.page))
  }
  if (input?.limit !== undefined) {
    search.set('limit', String(input.limit))
  }
  const query = search.toString()
  const response = await fetch(`${API_URL}/api/users/me/inventory${query ? `?${query}` : ''}`, {
    cache: 'no-store',
    headers: await authHeaders(input?.expectedUserId),
    signal: input?.signal,
  })
  return parseJsonResponse<UserInventoryItemProfile[]>(response, 'Failed to load inventory')
}

export async function exportUserInventory(
  options?: AccountBoundRequestOptions,
): Promise<UserInventoryExportProfile> {
  const response = await fetch(`${API_URL}/api/users/me/inventory/export`, {
    cache: 'no-store',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserInventoryExportProfile>(response, 'Failed to export inventory')
}

export async function createUserInventoryItem(
  input: UserInventoryItemInput,
  options?: AccountBoundRequestOptions,
): Promise<UserInventoryItemProfile> {
  const response = await fetch(`${API_URL}/api/users/me/inventory`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
    signal: options?.signal,
  })
  return parseJsonResponse<UserInventoryItemProfile>(response, 'Failed to add inventory item')
}

export async function createUserInventoryPhotoItem(
  input: UserInventoryPhotoInput,
  options?: AccountBoundRequestOptions,
): Promise<UserInventoryItemProfile> {
  const response = await fetch(`${API_URL}/api/users/me/inventory/photos`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
    signal: options?.signal,
  })
  return parseJsonResponse<UserInventoryItemProfile>(response, 'Failed to add photo inventory item')
}

export async function updateUserInventoryItem(input: {
  itemId: string
  item: UserInventoryItemUpdateInput
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<UserInventoryItemProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/inventory/${encodeURIComponent(input.itemId)}`,
    {
      method: 'PATCH',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(input.item),
      signal: input.signal,
    },
  )
  return parseJsonResponse<UserInventoryItemProfile>(response, 'Failed to update inventory item')
}

export async function deleteUserInventoryItem(
  itemId: string,
  options?: AccountBoundRequestOptions,
): Promise<void> {
  const response = await fetch(`${API_URL}/api/users/me/inventory/${encodeURIComponent(itemId)}`, {
    method: 'DELETE',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  if (!response.ok) {
    throw new Error('Failed to delete inventory item')
  }
}

export async function getPopularProductSearches(
  options?: AccountBoundRequestOptions,
): Promise<UserPopularProductSearchProfile[]> {
  const response = await fetch(`${API_URL}/api/users/me/popular-product-searches`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserPopularProductSearchProfile[]>(
    response,
    'Failed to load popular searches',
  )
}

export async function getDiscoverConversations(options?: {
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<UserDiscoverConversationProfile[]> {
  const response = await fetch(`${API_URL}/api/users/me/discover/conversations`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserDiscoverConversationProfile[]>(
    response,
    'Failed to load Discover conversations',
  )
}

export async function getDiscoverConversation(
  conversationId: string,
  options?: { expectedUserId?: string; signal?: AbortSignal },
): Promise<UserDiscoverConversationProfile> {
  const response = await fetch(
    `${API_URL}/api/v1/users/me/discover/conversations/${encodeURIComponent(conversationId)}`,
    {
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<UserDiscoverConversationProfile>(
    response,
    'Failed to load Discover conversation',
  )
}

export async function getDiscoverConversationProductResultSet(
  conversationId: string,
  resultSetId: string,
  options?: { expectedUserId?: string; signal?: AbortSignal },
): Promise<UserDiscoverProductResultSetProfile> {
  const response = await fetch(
    `${API_URL}/api/v1/users/me/discover/conversations/${encodeURIComponent(conversationId)}/product-result-sets/${encodeURIComponent(resultSetId)}`,
    {
      cache: 'no-store',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<UserDiscoverProductResultSetProfile>(
    response,
    'Failed to refresh saved product results',
  )
}

export async function saveDiscoverConversation(input: {
  conversationId: string
  title: string
  threadJson: string
  expectedRevision?: number
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<UserDiscoverConversationProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/discover/conversations/${encodeURIComponent(input.conversationId)}`,
    {
      method: 'PUT',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        title: input.title,
        threadJson: input.threadJson,
        expectedRevision: input.expectedRevision,
      }),
      signal: input.signal,
    },
  )
  return parseJsonResponse<UserDiscoverConversationProfile>(
    response,
    'Failed to save Discover conversation',
  )
}

export async function deleteDiscoverConversation(
  conversationId: string,
  options?: { expectedUserId?: string; signal?: AbortSignal },
): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/users/me/discover/conversations/${encodeURIComponent(conversationId)}`,
    {
      method: 'DELETE',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  if (!response.ok && response.status !== 404) {
    throw new Error('Failed to delete Discover conversation')
  }
}

export async function getSavedProducts(input?: {
  page?: number
  limit?: number
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<UserSavedProductProfile[]> {
  const search = new URLSearchParams()
  if (input?.page !== undefined) {
    search.set('page', String(input.page))
  }
  if (input?.limit !== undefined) {
    search.set('limit', String(input.limit))
  }
  const query = search.toString()
  const response = await fetch(
    `${API_URL}/api/users/me/saved-products${query ? `?${query}` : ''}`,
    {
      headers: await authHeaders(input?.expectedUserId),
      signal: input?.signal,
    },
  )
  return parseJsonResponse<UserSavedProductProfile[]>(response, 'Failed to load saved products')
}

export async function getSavedProduct(
  productKey: string,
  options?: { expectedUserId?: string; signal?: AbortSignal },
): Promise<UserSavedProductProfile> {
  const search = new URLSearchParams({ productKey })
  const response = await fetch(`${API_URL}/api/users/me/saved-products/detail?${search}`, {
    cache: 'no-store',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserSavedProductProfile>(response, 'Failed to load saved product')
}

export async function saveUserProduct(
  input: SaveUserProductInput,
  options?: { expectedUserId?: string },
): Promise<UserSavedProductProfile> {
  const response = await fetch(`${API_URL}/api/users/me/saved-products`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
  return parseJsonResponse<UserSavedProductProfile>(response, 'Failed to save product')
}

export async function removeSavedProduct(
  productKey: string,
  options?: { expectedUserId?: string },
): Promise<void> {
  const search = new URLSearchParams({ productKey })
  const response = await fetch(`${API_URL}/api/users/me/saved-products?${search.toString()}`, {
    method: 'DELETE',
    headers: await authHeaders(options?.expectedUserId),
  })
  if (!response.ok) {
    throw new Error('Failed to remove saved product')
  }
}

export async function getUserTasteProfile(
  options?: AccountBoundRequestOptions,
): Promise<UserTasteProfile> {
  const response = await fetch(`${API_URL}/api/users/me/taste-profile`, {
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<UserTasteProfile>(response, 'Failed to load learned taste profile')
}

export async function recordUserTasteBehavior(
  input: {
    behavior: UserTasteBehaviorType
    product: SaveUserProductInput
  },
  options?: AccountBoundRequestOptions,
): Promise<UserTasteProfile> {
  const response = await fetch(`${API_URL}/api/users/me/taste-profile/behaviors`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(options?.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
    signal: options?.signal,
  })
  return parseJsonResponse<UserTasteProfile>(response, 'Failed to record taste behavior')
}

export async function updateUserTasteSignal(input: {
  signalId: string
  weight?: number
  disabled?: boolean
  expectedUserId?: string
  signal?: AbortSignal
}): Promise<UserTasteSignalProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/signals/${encodeURIComponent(input.signalId)}`,
    {
      method: 'PATCH',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        weight: input.weight,
        disabled: input.disabled,
      }),
      signal: input.signal,
    },
  )
  return parseJsonResponse<UserTasteSignalProfile>(response, 'Failed to update taste signal')
}

export async function removeUserTasteSignal(
  signalId: string,
  options?: AccountBoundRequestOptions,
): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/signals/${encodeURIComponent(signalId)}`,
    {
      method: 'DELETE',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  if (!response.ok) {
    throw new Error('Failed to remove taste signal')
  }
}

export async function acceptUserTasteSuggestion(
  filterId: string,
  options?: AccountBoundRequestOptions,
): Promise<UserSettingsProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/suggestions/${encodeURIComponent(filterId)}:accept`,
    {
      method: 'POST',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to accept taste suggestion')
}

export async function rejectUserTasteSuggestion(
  filterId: string,
  options?: AccountBoundRequestOptions,
): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/suggestions/${encodeURIComponent(filterId)}:reject`,
    {
      method: 'POST',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  if (!response.ok) {
    throw new Error('Failed to reject taste suggestion')
  }
}

export async function createCart(input: {
  addItems: readonly SelectedOfferCartAddItemInput[]
  buyerIdentity?: CartBuyerIdentityInput | null
  discountCodes?: readonly string[]
  giftCardCodes?: readonly string[]
  deliveryAddressesToAdd?: readonly CartDeliveryAddressInput[]
  deliveryAddressesToReplace?: readonly CartDeliveryAddressInput[]
  selectedDeliveryOptions?: readonly CartDeliveryOptionSelectionInput[]
  note?: string | null
  expectedUserId?: string
}): Promise<CartProfile> {
  const response = await fetch(`${API_URL}/api/carts`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      addItems: input.addItems,
      buyerIdentity: input.buyerIdentity,
      discountCodes: input.discountCodes,
      giftCardCodes: input.giftCardCodes,
      deliveryAddressesToAdd: input.deliveryAddressesToAdd,
      deliveryAddressesToReplace: input.deliveryAddressesToReplace,
      selectedDeliveryOptions: input.selectedDeliveryOptions,
      note: input.note,
    }),
  })
  return parseJsonResponse<CartProfile>(response, 'Failed to create cart')
}

export async function updateCart(input: {
  cartId: string
  addItems?: readonly SelectedOfferCartAddItemInput[]
  updateItems?: readonly {
    cartLineId?: string | null
    remoteCartLineId?: string | null
    quantity: number
  }[]
  removeCartLineIds?: readonly string[]
  removeRemoteCartLineIds?: readonly string[]
  buyerIdentity?: CartBuyerIdentityInput | null
  discountCodes?: readonly string[]
  giftCardCodes?: readonly string[]
  deliveryAddressesToAdd?: readonly CartDeliveryAddressInput[]
  deliveryAddressesToReplace?: readonly CartDeliveryAddressInput[]
  selectedDeliveryOptions?: readonly CartDeliveryOptionSelectionInput[]
  note?: string | null
  expectedUserId?: string
}): Promise<CartProfile> {
  const response = await fetch(`${API_URL}/api/carts/${encodeURIComponent(input.cartId)}`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      addItems: input.addItems,
      updateItems: input.updateItems,
      removeCartLineIds: input.removeCartLineIds,
      removeRemoteCartLineIds: input.removeRemoteCartLineIds,
      buyerIdentity: input.buyerIdentity,
      discountCodes: input.discountCodes,
      giftCardCodes: input.giftCardCodes,
      deliveryAddressesToAdd: input.deliveryAddressesToAdd,
      deliveryAddressesToReplace: input.deliveryAddressesToReplace,
      selectedDeliveryOptions: input.selectedDeliveryOptions,
      note: input.note,
    }),
  })
  return parseJsonResponse<CartProfile>(response, 'Failed to update cart')
}

/** PCOS-013 binding: the browser contributes only a server-issued exact offer key and quantity. */
export async function bindSelectedOfferToCart(input: {
  offerKey: string
  quantity?: number
  cartId?: string | null
  expectedUserId?: string
}): Promise<CartProfile> {
  const addItems = [{ offerKey: input.offerKey, quantity: input.quantity ?? 1 }]
  return input.cartId
    ? updateCart({
        cartId: input.cartId,
        addItems,
        expectedUserId: input.expectedUserId,
      })
    : createCart({ addItems, expectedUserId: input.expectedUserId })
}

export async function getCartCheckout(input: {
  cartId: string
  refresh?: boolean
  expectedUserId?: string
}): Promise<CheckoutProfile> {
  const search = new URLSearchParams()
  if (input.refresh !== undefined) {
    search.set('refresh', String(input.refresh))
  }
  const query = search.toString()
  const suffix = query ? `?${query}` : ''
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout${suffix}`,
    {
      headers: await authHeaders(input.expectedUserId),
    },
  )
  return parseJsonResponse<CheckoutProfile>(response, 'Failed to get checkout')
}

export async function bootstrapEmbeddedCheckout(
  cartId: string,
  options?: AccountBoundRequestOptions,
): Promise<EmbeddedCheckoutBootstrapProfile> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(cartId)}/checkout/embedded`,
    {
      method: 'POST',
      headers: await authHeaders(options?.expectedUserId),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<EmbeddedCheckoutBootstrapProfile>(
    response,
    'Failed to prepare embedded checkout',
  )
}

export async function completeEmbeddedCheckout(input: {
  cartId: string
  sessionId: string
  expectedUserId?: string
}): Promise<CheckoutProfile> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout/embedded/${encodeURIComponent(input.sessionId)}/complete`,
    {
      method: 'POST',
      headers: await authHeaders(input.expectedUserId),
    },
  )
  return parseJsonResponse<CheckoutProfile>(response, 'Failed to verify embedded checkout')
}

export async function cancelEmbeddedCheckout(input: {
  cartId: string
  sessionId: string
  expectedUserId?: string
}): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout/embedded/${encodeURIComponent(input.sessionId)}/cancel`,
    {
      method: 'POST',
      headers: await authHeaders(input.expectedUserId),
    },
  )
  if (!response.ok) {
    throw await parseErrorResponse(response, 'Failed to close embedded checkout')
  }
}

export async function updateCartCheckout(input: UpdateCheckoutInput): Promise<CheckoutProfile> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout`,
    {
      method: 'PATCH',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        buyer: input.buyer,
        shippingAddress: input.shippingAddress,
        discountCodes: input.discountCodes,
      }),
    },
  )
  return parseJsonResponse<CheckoutProfile>(response, 'Failed to update checkout')
}

export interface CheckoutAssistantMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistCheckoutInput {
  cartId: string
  message: string
  merchantDeliveryHint?: string | null
  history?: readonly CheckoutAssistantMessage[]
  expectedUserId?: string
}

export interface CheckoutAssistantResult {
  reply: string
  checkoutUpdated: boolean
  checkout: CheckoutProfile
}

export async function assistCartCheckout(
  input: AssistCheckoutInput,
): Promise<CheckoutAssistantResult> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout/assistant`,
    {
      method: 'POST',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: input.message,
        merchantDeliveryHint: input.merchantDeliveryHint || undefined,
        history: input.history?.slice(-40),
      }),
    },
  )
  return parseJsonResponse<CheckoutAssistantResult>(response, 'Failed to reach checkout assistant')
}

export async function createCheckoutConsent(
  input: CreateCheckoutConsentInput,
): Promise<CheckoutConsentProfile> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout/consent`,
    {
      method: 'POST',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        checkout_id: input.checkoutId,
        payment_instrument_reference: input.paymentInstrumentReference,
        shipping_method: input.shippingMethod || undefined,
        presented_terms_hash: input.presentedTermsHash || undefined,
      }),
    },
  )
  return parseJsonResponse<CheckoutConsentProfile>(response, 'Failed to authorize checkout')
}

export async function completeCartCheckout(
  input: CompleteCartCheckoutInput,
): Promise<CheckoutCompletionProfile> {
  const credentialType = input.credentialType?.trim() || 'token'
  const credentialDetails = input.credentialDetails ?? { source: 'meant_web_checkout' }
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout/complete`,
    {
      method: 'POST',
      headers: {
        ...(await authHeaders(input.expectedUserId)),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        buyer_consent_id: input.buyerConsentId,
        checkout_id: input.checkoutId,
        payment_instruments: [
          {
            handler: input.handler,
            amount: input.amountMinor,
            currency: input.currency,
            credential: {
              type: credentialType,
              token: input.token,
              details: credentialDetails,
            },
            sca_liability: {
              liable_party: 'platform',
              liability_shifted: false,
              challenge_required: false,
              reason: 'User confirmed checkout inside Meant.',
            },
          },
        ],
        idempotency_key: input.idempotencyKey || undefined,
        signals: {
          'dev.meant.checkout_surface': 'web',
        },
      }),
    },
  )
  return parseJsonResponse<CheckoutCompletionProfile>(response, 'Failed to complete checkout')
}

export async function searchDiscountCodes(
  input: SearchDiscountCodesInput,
): Promise<DiscountCodeSearchProfile> {
  const response = await fetch(`${API_URL}/api/discounts/search`, {
    method: 'POST',
    headers: {
      ...(await authHeaders(input.expectedUserId)),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      merchantId: input.merchantId ?? undefined,
      merchantDomain: input.merchantDomain ?? undefined,
      items: input.items,
      buyerIdentity: input.buyerIdentity ?? undefined,
      deliveryAddressesToAdd: input.deliveryAddressesToAdd,
      deliveryAddressesToReplace: input.deliveryAddressesToReplace,
      selectedDeliveryOptions: input.selectedDeliveryOptions,
    }),
  })
  return parseJsonResponse<DiscountCodeSearchProfile>(response, 'Failed to find discount codes')
}

export async function getOrders(options?: AccountBoundRequestOptions): Promise<OrderProfile[]> {
  const response = await fetch(`${API_URL}/api/orders`, {
    cache: 'no-store',
    headers: await authHeaders(options?.expectedUserId),
    signal: options?.signal,
  })
  return parseJsonResponse<OrderProfile[]>(response, 'Failed to load orders')
}
