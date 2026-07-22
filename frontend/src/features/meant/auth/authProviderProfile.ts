import type { User } from '@supabase/supabase-js'

type AuthProviderUser = Pick<User, 'user_metadata' | 'identities'>

/**
 * Resolves the profile image supplied by an OAuth identity. Supabase copies provider claims into
 * user metadata, while older or differently configured projects may expose them only on the linked
 * identity. Only HTTPS URLs are admitted because the result is rendered directly by the browser.
 */
export function authProviderAvatarUrl(user?: AuthProviderUser | null): string | null {
  const candidates: unknown[] = [user?.user_metadata?.avatar_url, user?.user_metadata?.picture]

  for (const identity of user?.identities ?? []) {
    candidates.push(identity.identity_data?.avatar_url, identity.identity_data?.picture)
  }

  for (const candidate of candidates) {
    const url = httpsUrl(candidate)
    if (url) return url
  }

  return null
}

function httpsUrl(value: unknown): string | null {
  if (typeof value !== 'string' || !value.trim()) return null

  const candidate = value.trim()
  try {
    return new URL(candidate).protocol === 'https:' ? candidate : null
  } catch {
    return null
  }
}
