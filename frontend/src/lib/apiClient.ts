import createClient, { type Middleware } from 'openapi-fetch'

import type { components, paths } from '../api/schema'
import { parseJsonResponse } from './apiError'
export { ApiError, parseErrorResponse, parseJsonResponse } from './apiError'
import { supabase } from './supabase'

const API_URL = import.meta.env.VITE_MEANT_API_URL ?? 'http://localhost:8080'
const PROFILE_PICTURE_BUCKET = 'profile-pictures'
const PROFILE_PICTURE_SIGNED_URL_SECONDS = 60 * 60

/** Profile shape served by the backend, sourced from the generated OpenAPI schema. */
export type UserProfile = components['schemas']['UserResponse']

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

export interface UserSettingsProfile {
  budget: number | null
  clothingFit: 'men' | 'women' | 'other' | null
  location: UserSettingsLocation | null
  locations: UserSettingsLocation[]
  filters: ShoppingFilterProfile[]
  availableFilters: ShoppingFilterProfile[]
  parsedFilterIds: string[]
  unmappedPreferences: string[]
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

export interface UserProductSearchProfile {
  query: string
  normalizedQuery: string
  profileHash: string
  cached: boolean
  offset: number
  limit: number
  nextOffset: number | null
  hasMore: boolean
  products: UserProductSearchProductProfile[]
}

export type UserProductSearchStreamEventType =
  'phase' | 'product' | 'product_update' | 'rank_update' | 'done' | 'error'

export interface UserProductSearchStreamEventProfile {
  type: UserProductSearchStreamEventType
  agent: string | null
  label: string | null
  productKey: string | null
  product: UserProductSearchProductProfile | null
  products: UserProductSearchProductProfile[]
  query: string | null
  normalizedQuery: string | null
  profileHash: string | null
  cached: boolean | null
  offset: number | null
  limit: number | null
  nextOffset: number | null
  hasMore: boolean | null
  message: string | null
}

export interface UserProductSearchStreamHandlers {
  onPhase?: (event: UserProductSearchStreamEventProfile) => void
  onProduct?: (event: UserProductSearchStreamEventProfile) => void
  onProductUpdate?: (event: UserProductSearchStreamEventProfile) => void
  onRankUpdate?: (event: UserProductSearchStreamEventProfile) => void
  onDone?: (event: UserProductSearchStreamEventProfile) => void
  onError?: (message: string) => void
}

export interface UserProductSearchSuggestionsProfile {
  suggestions: string[]
}

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

export interface AssistantProductContextInput {
  id: string
  name: string
  brand: string
  category: string
  match: number
  priceFrom: number
  note: string
}

export interface AssistantCartItemContextInput {
  name: string
  merchant: string
  quantity: number
  price: number
}

export interface AssistantOrderContextInput {
  id: string
  date: string
  status: string
  statusNote: string
  itemCount: number
}

export interface AssistantChatContextInput {
  view: string
  contextLabel: string
  currentSearchQuery?: string | null
  selectedMerchantName?: string | null
  savedProductCount: number
  cartItemCount: number
  visibleProducts: readonly AssistantProductContextInput[]
  cartItems: readonly AssistantCartItemContextInput[]
  orders: readonly AssistantOrderContextInput[]
}

export interface UserAssistantMessageProfile {
  id: string
  role: 'user' | 'assistant'
  content: string
  products: UserProductSearchProductProfile[]
  createdAt: string
}

export interface UserAssistantConversationProfile {
  conversationId: string | null
  title: string | null
  createdAt: string | null
  updatedAt: string | null
  messages: UserAssistantMessageProfile[]
}

export interface UserAssistantConversationSummaryProfile {
  conversationId: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface UserDiscoverConversationProfile {
  conversationId: string
  title: string
  createdAt: string
  updatedAt: string
  threadJson: string
}

export interface UserAssistantStreamEventProfile {
  type: 'metadata' | 'delta' | 'done' | 'error'
  conversationId: string | null
  messageId: string | null
  text: string | null
  products: UserProductSearchProductProfile[]
}

export interface UserAssistantStreamHandlers {
  onMetadata?: (event: UserAssistantStreamEventProfile) => void
  onDelta?: (text: string) => void
  onDone?: (event: UserAssistantStreamEventProfile) => void
  onError?: (message: string) => void
}

export interface UserSavedProductOfferProfile {
  merchant: string
  price: number
  delivery: string
  merchantId: string | null
  merchantDomain: string | null
  productVariantId: string | null
  variantTitle: string | null
  available: boolean | null
}

export interface UserSavedProductReviewProfile {
  score: number
  count: number
  insight: string
}

export interface UserSavedProductProfile {
  id: string
  productHash: string | null
  name: string
  brand: string
  category: string
  tone: string
  imageUrl: string | null
  productUrl: string | null
  remote: boolean
  match: number
  priceFrom: number
  merchants: number
  satisfies: string[]
  misses: string[]
  note: string
  pros: string[]
  cons: string[]
  review: UserSavedProductReviewProfile
  offers: UserSavedProductOfferProfile[]
  needs: string | null
  provides: string[]
  createdAt: string
  updatedAt: string
}

export type SaveUserProductInput = Omit<UserSavedProductProfile, 'createdAt' | 'updatedAt'>

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
export type CheckoutMessageProfile = components['schemas']['CheckoutMessageResponse']
export type CheckoutConsentProfile = components['schemas']['CheckoutConsentResponse']
export type CheckoutCompletionProfile = components['schemas']['CheckoutCompletionResponse']

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
}

export interface CreateCheckoutConsentInput {
  cartId: string
  checkoutId: string
  paymentInstrumentReference: string
  shippingMethod?: string | null
  presentedTermsHash?: string | null
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

export type CartToolMapInput = Record<string, unknown>

export interface SearchDiscountCodesInput {
  merchantId?: string | null
  merchantDomain?: string | null
  items: readonly CartAddItemInput[]
  buyerIdentity?: {
    email?: string | null
    phoneNumber?: string | null
    firstName?: string | null
    lastName?: string | null
    countryCode?: string | null
  } | null
  deliveryAddressesToAdd?: readonly {
    id?: string | null
    selected?: boolean | null
    firstName?: string | null
    lastName?: string | null
    phoneNumber?: string | null
    streetAddress?: string | null
    extendedAddress?: string | null
    city?: string | null
    provinceCode?: string | null
    postalCode?: string | null
    countryCode?: string | null
  }[]
  deliveryAddressesToReplace?: readonly {
    id?: string | null
    selected?: boolean | null
    firstName?: string | null
    lastName?: string | null
    phoneNumber?: string | null
    streetAddress?: string | null
    extendedAddress?: string | null
    city?: string | null
    provinceCode?: string | null
    postalCode?: string | null
    countryCode?: string | null
  }[]
  selectedDeliveryOptions?: readonly {
    id?: string | null
    groupId?: string | null
    deliveryGroupId?: string | null
    optionHandle?: string | null
    deliveryOptionHandle?: string | null
    selectedOptionId?: string | null
  }[]
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

async function authHeaders(): Promise<HeadersInit> {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** Fetches the current user, creating the backend profile row on first call (upsert-on-read). */
export async function getCurrentUser(): Promise<UserProfile> {
  const { data, error } = await client.GET('/api/users/me')
  if (error || !data) {
    throw new Error('Failed to load current user')
  }
  return data
}

export async function updateProfile(input: {
  firstName: string
  surname: string | null
}): Promise<UserProfile> {
  const { data, error } = await client.PATCH('/api/users/me', {
    body: { firstName: input.firstName, surname: input.surname ?? undefined },
  })
  if (error || !data) {
    throw new Error('Failed to update profile')
  }
  return data
}

export async function updateProfilePicture(profilePicturePath: string): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me/profile-picture`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ profilePicturePath }),
  })
  return parseJsonResponse<UserProfile>(response, 'Failed to update profile picture')
}

export async function updateNewsletterSubscription(newsletter: boolean): Promise<UserProfile> {
  const { data, error } = await client.PATCH('/api/users/me/newsletter', {
    body: { newsletter },
  })
  if (error || !data) {
    throw new Error('Failed to update newsletter subscription')
  }
  return data
}

export async function removeProfilePicture(): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me/profile-picture`, {
    method: 'DELETE',
    headers: await authHeaders(),
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

export async function deleteProfilePictureFile(profilePicturePath?: string | null): Promise<void> {
  if (!profilePicturePath) {
    return
  }
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

export async function getUserSettings(): Promise<UserSettingsProfile> {
  const response = await fetch(`${API_URL}/api/users/me/settings`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to load user settings')
}

export async function getMerchants(): Promise<MerchantProfile[]> {
  const response = await fetch(`${API_URL}/api/merchants`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<MerchantProfile[]>(response, 'Failed to load merchants')
}

export async function getMerchantIdentityLinks(): Promise<MerchantIdentityLinkProfile[]> {
  const response = await fetch(`${API_URL}/api/merchants/identity-links`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<MerchantIdentityLinkProfile[]>(
    response,
    'Failed to load merchant account connections',
  )
}

export async function startMerchantIdentityAuthorization(
  merchantId: string,
): Promise<MerchantIdentityAuthorizationProfile> {
  const response = await fetch(
    `${API_URL}/api/merchants/${merchantId}/identity-link/authorization`,
    {
      method: 'POST',
      headers: await authHeaders(),
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
}): Promise<MerchantIdentityLinkProfile> {
  const response = await fetch(`${API_URL}/api/merchants/identity-links/oauth/callback`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      state: input.state,
      code: input.code,
      issuer: input.issuer ?? undefined,
    }),
  })
  return parseJsonResponse<MerchantIdentityLinkProfile>(
    response,
    'Failed to complete merchant account linking',
  )
}

export async function revokeMerchantIdentityLink(merchantId: string): Promise<void> {
  const response = await fetch(`${API_URL}/api/merchants/identity-links/${merchantId}`, {
    method: 'DELETE',
    headers: await authHeaders(),
  })
  if (!response.ok) {
    throw new Error('Failed to revoke merchant account connection')
  }
}

export async function updateUserSettings(input: {
  budget?: number | null
  clothingFit?: 'men' | 'women' | 'other' | 'none'
  location?: UserSettingsLocation | null
  locations?: readonly UserSettingsLocation[]
  filterIds?: readonly string[]
  preferenceDescription?: string
}): Promise<UserSettingsProfile> {
  const response = await fetch(`${API_URL}/api/users/me/settings`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders()),
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
    }),
  })
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to update user settings')
}

export async function searchUserProducts(input: {
  query: string
  merchantId?: string | null
  offset?: number
  limit?: number
}): Promise<UserProductSearchProfile> {
  const response = await fetch(`${API_URL}/api/users/me/product-searches`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      query: input.query,
      merchantId: input.merchantId ?? undefined,
      offset: input.offset ?? undefined,
      limit: input.limit ?? undefined,
    }),
  })
  return parseJsonResponse<UserProductSearchProfile>(response, 'Failed to search products')
}

export async function streamUserProductSearch(
  input: {
    query: string
    merchantId?: string | null
    offset?: number
    limit?: number
    signal?: AbortSignal
  },
  handlers: UserProductSearchStreamHandlers,
): Promise<void> {
  const response = await fetch(`${API_URL}/api/users/me/product-searches:stream`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      query: input.query,
      merchantId: input.merchantId ?? undefined,
      offset: input.offset ?? undefined,
      limit: input.limit ?? undefined,
    }),
    signal: input.signal,
  })

  if (!response.ok || !response.body) {
    throw new Error('Failed to stream product search')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() ?? ''
      events.forEach((rawEvent) => handleProductSearchStreamEvent(rawEvent, handlers))
    }

    buffer += decoder.decode()
    if (buffer.trim()) {
      handleProductSearchStreamEvent(buffer, handlers)
    }
  } finally {
    reader.releaseLock()
  }
}

export async function getUserProductSearchSuggestions(): Promise<UserProductSearchSuggestionsProfile> {
  const response = await fetch(`${API_URL}/api/users/me/product-search-suggestions`, {
    cache: 'no-store',
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserProductSearchSuggestionsProfile>(
    response,
    'Failed to generate product search suggestions',
  )
}

export async function getProductDiscovery(): Promise<UserProductDiscoveryProfile> {
  const response = await fetch(`${API_URL}/api/users/me/product-discovery`, {
    headers: await authHeaders(),
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
      headers: await authHeaders(),
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
      headers: await authHeaders(),
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
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserInventoryItemProfile[]>(response, 'Failed to load inventory')
}

export async function exportUserInventory(): Promise<UserInventoryExportProfile> {
  const response = await fetch(`${API_URL}/api/users/me/inventory/export`, {
    cache: 'no-store',
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserInventoryExportProfile>(response, 'Failed to export inventory')
}

export async function createUserInventoryItem(
  input: UserInventoryItemInput,
): Promise<UserInventoryItemProfile> {
  const response = await fetch(`${API_URL}/api/users/me/inventory`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
  return parseJsonResponse<UserInventoryItemProfile>(response, 'Failed to add inventory item')
}

export async function createUserInventoryPhotoItem(
  input: UserInventoryPhotoInput,
): Promise<UserInventoryItemProfile> {
  const response = await fetch(`${API_URL}/api/users/me/inventory/photos`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
  return parseJsonResponse<UserInventoryItemProfile>(response, 'Failed to add photo inventory item')
}

export async function updateUserInventoryItem(input: {
  itemId: string
  item: UserInventoryItemUpdateInput
}): Promise<UserInventoryItemProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/inventory/${encodeURIComponent(input.itemId)}`,
    {
      method: 'PATCH',
      headers: {
        ...(await authHeaders()),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(input.item),
    },
  )
  return parseJsonResponse<UserInventoryItemProfile>(response, 'Failed to update inventory item')
}

export async function deleteUserInventoryItem(itemId: string): Promise<void> {
  const response = await fetch(`${API_URL}/api/users/me/inventory/${encodeURIComponent(itemId)}`, {
    method: 'DELETE',
    headers: await authHeaders(),
  })
  if (!response.ok) {
    throw new Error('Failed to delete inventory item')
  }
}

export async function getPopularProductSearches(): Promise<UserPopularProductSearchProfile[]> {
  const response = await fetch(`${API_URL}/api/users/me/popular-product-searches`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserPopularProductSearchProfile[]>(
    response,
    'Failed to load popular searches',
  )
}

export async function getLatestAssistantConversation(options?: {
  signal?: AbortSignal
}): Promise<UserAssistantConversationProfile> {
  const response = await fetch(`${API_URL}/api/users/me/assistant/conversations/latest`, {
    headers: await authHeaders(),
    signal: options?.signal,
  })
  return parseJsonResponse<UserAssistantConversationProfile>(
    response,
    'Failed to load Ask Meant conversation',
  )
}

export async function getAssistantConversations(options?: {
  signal?: AbortSignal
}): Promise<UserAssistantConversationSummaryProfile[]> {
  const response = await fetch(`${API_URL}/api/users/me/assistant/conversations`, {
    headers: await authHeaders(),
    signal: options?.signal,
  })
  return parseJsonResponse<UserAssistantConversationSummaryProfile[]>(
    response,
    'Failed to load Ask Meant conversations',
  )
}

export async function getAssistantConversation(
  conversationId: string,
  options?: {
    signal?: AbortSignal
  },
): Promise<UserAssistantConversationProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/assistant/conversations/${encodeURIComponent(conversationId)}`,
    {
      headers: await authHeaders(),
      signal: options?.signal,
    },
  )
  return parseJsonResponse<UserAssistantConversationProfile>(
    response,
    'Failed to load Ask Meant conversation',
  )
}

export async function getDiscoverConversations(options?: {
  signal?: AbortSignal
}): Promise<UserDiscoverConversationProfile[]> {
  const response = await fetch(`${API_URL}/api/users/me/discover/conversations`, {
    headers: await authHeaders(),
    signal: options?.signal,
  })
  return parseJsonResponse<UserDiscoverConversationProfile[]>(
    response,
    'Failed to load Discover conversations',
  )
}

export async function saveDiscoverConversation(input: {
  conversationId: string
  title: string
  threadJson: string
  signal?: AbortSignal
}): Promise<UserDiscoverConversationProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/discover/conversations/${encodeURIComponent(input.conversationId)}`,
    {
      method: 'PUT',
      headers: {
        ...(await authHeaders()),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        title: input.title,
        threadJson: input.threadJson,
      }),
      signal: input.signal,
    },
  )
  return parseJsonResponse<UserDiscoverConversationProfile>(
    response,
    'Failed to save Discover conversation',
  )
}

export async function deleteDiscoverConversation(conversationId: string): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/users/me/discover/conversations/${encodeURIComponent(conversationId)}`,
    {
      method: 'DELETE',
      headers: await authHeaders(),
    },
  )
  if (!response.ok && response.status !== 404) {
    throw new Error('Failed to delete Discover conversation')
  }
}

export async function streamAssistantMessage(
  input: {
    conversationId?: string | null
    message: string
    context: AssistantChatContextInput
    signal?: AbortSignal
  },
  handlers: UserAssistantStreamHandlers,
): Promise<void> {
  const response = await fetch(`${API_URL}/api/users/me/assistant/messages:stream`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      conversationId: input.conversationId ?? undefined,
      message: input.message,
      context: input.context,
    }),
    signal: input.signal,
  })

  if (!response.ok || !response.body) {
    throw new Error('Failed to stream Ask Meant response')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() ?? ''
      events.forEach((rawEvent) => handleAssistantStreamEvent(rawEvent, handlers))
    }

    buffer += decoder.decode()
    if (buffer.trim()) {
      handleAssistantStreamEvent(buffer, handlers)
    }
  } finally {
    reader.releaseLock()
  }
}

export async function getSavedProducts(input?: {
  page?: number
  limit?: number
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
      headers: await authHeaders(),
    },
  )
  return parseJsonResponse<UserSavedProductProfile[]>(response, 'Failed to load saved products')
}

export async function saveUserProduct(
  input: SaveUserProductInput,
): Promise<UserSavedProductProfile> {
  const response = await fetch(`${API_URL}/api/users/me/saved-products`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
  return parseJsonResponse<UserSavedProductProfile>(response, 'Failed to save product')
}

export async function removeSavedProduct(productKey: string): Promise<void> {
  const search = new URLSearchParams({ productKey })
  const response = await fetch(`${API_URL}/api/users/me/saved-products?${search.toString()}`, {
    method: 'DELETE',
    headers: await authHeaders(),
  })
  if (!response.ok) {
    throw new Error('Failed to remove saved product')
  }
}

export async function getUserTasteProfile(): Promise<UserTasteProfile> {
  const response = await fetch(`${API_URL}/api/users/me/taste-profile`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserTasteProfile>(response, 'Failed to load learned taste profile')
}

export async function recordUserTasteBehavior(input: {
  behavior: UserTasteBehaviorType
  product: SaveUserProductInput
}): Promise<UserTasteProfile> {
  const response = await fetch(`${API_URL}/api/users/me/taste-profile/behaviors`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })
  return parseJsonResponse<UserTasteProfile>(response, 'Failed to record taste behavior')
}

export async function updateUserTasteSignal(input: {
  signalId: string
  weight?: number
  disabled?: boolean
}): Promise<UserTasteSignalProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/signals/${encodeURIComponent(input.signalId)}`,
    {
      method: 'PATCH',
      headers: {
        ...(await authHeaders()),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        weight: input.weight,
        disabled: input.disabled,
      }),
    },
  )
  return parseJsonResponse<UserTasteSignalProfile>(response, 'Failed to update taste signal')
}

export async function removeUserTasteSignal(signalId: string): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/signals/${encodeURIComponent(signalId)}`,
    {
      method: 'DELETE',
      headers: await authHeaders(),
    },
  )
  if (!response.ok) {
    throw new Error('Failed to remove taste signal')
  }
}

export async function acceptUserTasteSuggestion(filterId: string): Promise<UserSettingsProfile> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/suggestions/${encodeURIComponent(filterId)}:accept`,
    {
      method: 'POST',
      headers: await authHeaders(),
    },
  )
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to accept taste suggestion')
}

export async function rejectUserTasteSuggestion(filterId: string): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/users/me/taste-profile/suggestions/${encodeURIComponent(filterId)}:reject`,
    {
      method: 'POST',
      headers: await authHeaders(),
    },
  )
  if (!response.ok) {
    throw new Error('Failed to reject taste suggestion')
  }
}

export async function createCart(input: {
  merchantId?: string | null
  merchantDomain?: string | null
  addItems: readonly CartAddItemInput[]
  discountCodes?: readonly string[]
  giftCardCodes?: readonly string[]
  deliveryAddressesToAdd?: readonly CartToolMapInput[]
  deliveryAddressesToReplace?: readonly CartToolMapInput[]
  selectedDeliveryOptions?: readonly CartToolMapInput[]
}): Promise<CartProfile> {
  const response = await fetch(`${API_URL}/api/carts`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      merchantId: input.merchantId ?? undefined,
      merchantDomain: input.merchantDomain ?? undefined,
      addItems: input.addItems,
      discountCodes: input.discountCodes,
      giftCardCodes: input.giftCardCodes,
      deliveryAddressesToAdd: input.deliveryAddressesToAdd,
      deliveryAddressesToReplace: input.deliveryAddressesToReplace,
      selectedDeliveryOptions: input.selectedDeliveryOptions,
    }),
  })
  return parseJsonResponse<CartProfile>(response, 'Failed to create cart')
}

export async function updateCart(input: {
  cartId: string
  addItems?: readonly CartAddItemInput[]
  updateItems?: readonly {
    cartLineId?: string | null
    remoteCartLineId?: string | null
    quantity: number
  }[]
  removeCartLineIds?: readonly string[]
  removeRemoteCartLineIds?: readonly string[]
  discountCodes?: readonly string[]
  giftCardCodes?: readonly string[]
  deliveryAddressesToAdd?: readonly CartToolMapInput[]
  deliveryAddressesToReplace?: readonly CartToolMapInput[]
  selectedDeliveryOptions?: readonly CartToolMapInput[]
}): Promise<CartProfile> {
  const response = await fetch(`${API_URL}/api/carts/${encodeURIComponent(input.cartId)}`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      addItems: input.addItems,
      updateItems: input.updateItems,
      removeCartLineIds: input.removeCartLineIds,
      removeRemoteCartLineIds: input.removeRemoteCartLineIds,
      discountCodes: input.discountCodes,
      giftCardCodes: input.giftCardCodes,
      deliveryAddressesToAdd: input.deliveryAddressesToAdd,
      deliveryAddressesToReplace: input.deliveryAddressesToReplace,
      selectedDeliveryOptions: input.selectedDeliveryOptions,
    }),
  })
  return parseJsonResponse<CartProfile>(response, 'Failed to update cart')
}

export async function getCartCheckout(input: {
  cartId: string
  refresh?: boolean
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
      headers: await authHeaders(),
    },
  )
  return parseJsonResponse<CheckoutProfile>(response, 'Failed to get checkout')
}

export async function updateCartCheckout(input: UpdateCheckoutInput): Promise<CheckoutProfile> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout`,
    {
      method: 'PATCH',
      headers: {
        ...(await authHeaders()),
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

export async function createCheckoutConsent(
  input: CreateCheckoutConsentInput,
): Promise<CheckoutConsentProfile> {
  const response = await fetch(
    `${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout/consent`,
    {
      method: 'POST',
      headers: {
        ...(await authHeaders()),
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
        ...(await authHeaders()),
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
      ...(await authHeaders()),
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

export async function getOrders(): Promise<OrderProfile[]> {
  const response = await fetch(`${API_URL}/api/orders`, {
    cache: 'no-store',
    headers: await authHeaders(),
  })
  return parseJsonResponse<OrderProfile[]>(response, 'Failed to load orders')
}

function handleAssistantStreamEvent(rawEvent: string, handlers: UserAssistantStreamHandlers) {
  const data = streamEventData(rawEvent)

  if (!data) {
    return
  }

  const event = JSON.parse(data) as UserAssistantStreamEventProfile
  switch (event.type) {
    case 'metadata':
      handlers.onMetadata?.(event)
      break
    case 'delta':
      if (event.text) {
        handlers.onDelta?.(event.text)
      }
      break
    case 'done':
      handlers.onDone?.(event)
      break
    case 'error':
      handlers.onError?.(event.text ?? 'Ask Meant could not respond right now.')
      break
    default:
      break
  }
}

function handleProductSearchStreamEvent(
  rawEvent: string,
  handlers: UserProductSearchStreamHandlers,
) {
  const data = streamEventData(rawEvent)

  if (!data) {
    return
  }

  const event = JSON.parse(data) as UserProductSearchStreamEventProfile
  switch (event.type) {
    case 'phase':
      handlers.onPhase?.(event)
      break
    case 'product':
      handlers.onProduct?.(event)
      break
    case 'product_update':
      handlers.onProductUpdate?.(event)
      break
    case 'rank_update':
      handlers.onRankUpdate?.(event)
      break
    case 'done':
      handlers.onDone?.(event)
      break
    case 'error':
      handlers.onError?.(event.message ?? 'Product search failed. Please try again.')
      break
    default:
      break
  }
}

function streamEventData(rawEvent: string): string {
  return rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice('data:'.length).trimStart())
    .join('\n')
}
