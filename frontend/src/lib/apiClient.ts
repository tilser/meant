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
