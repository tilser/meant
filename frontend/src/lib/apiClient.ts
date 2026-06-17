import createClient, { type Middleware } from 'openapi-fetch'

import type { components, paths } from '../api/schema'
import { supabase } from './supabase'

const API_URL = import.meta.env.VITE_MEANT_API_URL ?? 'http://localhost:8080'

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
  location: UserSettingsLocation | null
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
}

export interface UserProductSearchProfile {
  query: string
  normalizedQuery: string
  profileHash: string
  cached: boolean
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
  messages: UserAssistantMessageProfile[]
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
export type CheckoutProfile = components['schemas']['CheckoutResponse']

export interface CartAddItemInput {
  productVariantId: string
  quantity: number
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

async function parseJsonResponse<T>(response: Response, message: string): Promise<T> {
  if (!response.ok) {
    throw new Error(message)
  }
  return (await response.json()) as T
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
  budget?: number
  location?: UserSettingsLocation | null
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
      budget: input.budget,
      location: input.location,
      filterIds: input.filterIds,
      preferenceDescription: input.preferenceDescription,
    }),
  })
  return parseJsonResponse<UserSettingsProfile>(response, 'Failed to update user settings')
}

export async function searchUserProducts(input: {
  query: string
  merchantId?: string | null
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

export async function streamAssistantMessage(
  input: {
    conversationId?: string | null
    message: string
    context: AssistantChatContextInput
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
    const events = buffer.split(/\n\n/)
    buffer = events.pop() ?? ''
    events.forEach((rawEvent) => handleAssistantStreamEvent(rawEvent, handlers))
  }

  buffer += decoder.decode()
  if (buffer.trim()) {
    handleAssistantStreamEvent(buffer, handlers)
  }
}

export async function getSavedProducts(): Promise<UserSavedProductProfile[]> {
  const response = await fetch(`${API_URL}/api/users/me/saved-products`, {
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
