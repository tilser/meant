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
}): Promise<UserProductSearchProfile> {
  const response = await fetch(`${API_URL}/api/users/me/product-searches`, {
    method: 'POST',
    headers: {
      ...(await authHeaders()),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query: input.query }),
  })
  return parseJsonResponse<UserProductSearchProfile>(response, 'Failed to search products')
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
