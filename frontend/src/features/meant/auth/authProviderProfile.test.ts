import { describe, expect, test } from 'bun:test'

import { authProviderAvatarUrl } from './authProviderProfile'

describe('authProviderAvatarUrl', () => {
  test('reads the avatar URL copied from OAuth provider metadata', () => {
    expect(
      authProviderAvatarUrl({
        user_metadata: {
          avatar_url: 'https://images.example.com/avatar.png',
          picture: 'https://images.example.com/alternate.png',
        },
        identities: [],
      }),
    ).toBe('https://images.example.com/avatar.png')
  })

  test('falls back to linked identity data', () => {
    expect(
      authProviderAvatarUrl({
        user_metadata: {},
        identities: [
          {
            id: 'provider-user',
            user_id: 'meant-user',
            identity_id: 'identity-id',
            provider: 'google',
            identity_data: { picture: 'https://images.example.com/identity.png' },
            created_at: '2026-07-22T00:00:00Z',
            updated_at: '2026-07-22T00:00:00Z',
            last_sign_in_at: '2026-07-22T00:00:00Z',
          },
        ],
      }),
    ).toBe('https://images.example.com/identity.png')
  })

  test('ignores malformed and non-HTTPS image values', () => {
    expect(
      authProviderAvatarUrl({
        user_metadata: { avatar_url: 'javascript:alert(1)' },
        identities: [],
      }),
    ).toBeNull()
    expect(
      authProviderAvatarUrl({
        user_metadata: { picture: 'http://images.example.com/avatar.png' },
        identities: [],
      }),
    ).toBeNull()
  })
})
