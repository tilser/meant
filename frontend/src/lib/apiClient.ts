import createClient, { type Middleware } from 'openapi-fetch'

import type { components, paths } from '../api/schema'
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

export interface UserProductSearchSuggestionsProfile {
  suggestions: string[]
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
}

export type CartProfile = components['schemas']['CartResponse']
export type CartDeliveryGroupProfile = NonNullable<CartProfile['deliveryGroups']>[number]
export type CheckoutProfile = components['schemas']['CheckoutResponse']

export type UserInventoryCategory = 'APPAREL' | 'PANTRY' | 'HOME' | 'OTHER'
export type UserInventorySource = 'MANUAL' | 'PHOTO' | 'MEANT_PURCHASE'
export type UserInventoryRecommendationRelationship =
  | 'NONE'
  | 'DUPLICATE'
  | 'COMPLEMENT'
  | 'RESTOCK'

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

async function parseJsonResponse<T>(response: Response, message: string): Promise<T> {
  if (!response.ok) {
    throw new Error(await parseErrorResponse(response, message))
  }
  return (await response.json()) as T
}

async function parseErrorResponse(response: Response, fallback: string): Promise<string> {
  try {
    const payload = await response.json() as { detail?: unknown; title?: unknown; message?: unknown }
    const detail = typeof payload.detail === 'string' ? payload.detail : null
    const title = typeof payload.title === 'string' ? payload.title : null
    const payloadMessage = typeof payload.message === 'string' ? payload.message : null
    return detail || payloadMessage || title || fallback
  } catch {
    return fallback
  }
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

export async function removeProfilePicture(): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/users/me/profile-picture`, {
    method: 'DELETE',
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserProfile>(response, 'Failed to remove profile picture')
}

export async function getProfilePictureUrl(profilePicturePath?: string | null): Promise<string | null> {
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

export async function uploadProfilePictureFile(userId: string, file: File): Promise<{
  path: string
  signedUrl: string | null
}> {
  const extension = profilePictureExtension(file)
  const path = `${userId}/${randomUuid()}.${extension}`
  const { data, error } = await supabase.storage
    .from(PROFILE_PICTURE_BUCKET)
    .upload(path, file, {
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
  await supabase.storage
    .from(PROFILE_PICTURE_BUCKET)
    .remove([profilePicturePath])
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
    return [...bytes].map((byte, index) => {
      const value = byte.toString(16).padStart(2, '0')
      return [4, 6, 8, 10].includes(index) ? `-${value}` : value
    }).join('')
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
  return parseJsonResponse<UserProductDiscoveryProfile>(response, 'Failed to load product discovery')
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
  const response = await fetch(`${API_URL}/api/users/me/inventory/${encodeURIComponent(input.itemId)}`, {
    method: 'PATCH',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input.item),
  })
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
  return parseJsonResponse<UserPopularProductSearchProfile[]>(response, 'Failed to load popular searches')
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
  const response = await fetch(`${API_URL}/api/users/me/saved-products${query ? `?${query}` : ''}`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<UserSavedProductProfile[]>(response, 'Failed to load saved products')
}

export async function saveUserProduct(input: SaveUserProductInput): Promise<UserSavedProductProfile> {
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
  const response = await fetch(`${API_URL}/api/carts/${encodeURIComponent(input.cartId)}/checkout${suffix}`, {
    headers: await authHeaders(),
  })
  return parseJsonResponse<CheckoutProfile>(response, 'Failed to get checkout')
}

function handleAssistantStreamEvent(
  rawEvent: string,
  handlers: UserAssistantStreamHandlers,
) {
  const data = rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice('data:'.length).trimStart())
    .join('\n')

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
