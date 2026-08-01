import { describe, expect, test } from 'bun:test'

import {
  accountSessionStorageKey,
  accountStorageKey,
  purgeLegacyAccountStorage,
} from './accountStorage'

describe('account storage isolation', () => {
  test('uses deterministic per-user keys without sharing the anonymous scope', () => {
    expect(accountStorageKey('meant.cart', ' user/a ')).toBe('meant.cart.account.user%2Fa')
    expect(accountStorageKey('meant.cart', 'user-b')).toBe('meant.cart.account.user-b')
    expect(accountStorageKey('meant.cart', undefined)).toBe('meant.cart.account.anonymous')
  })

  test('admits product and transaction snapshots only to account-scoped session keys', () => {
    expect(accountSessionStorageKey('meant.shelf', 'user-a')).toBe(
      'meant.shelf.buyerSafeV2.account.user-a',
    )
    expect(accountSessionStorageKey('meant.compare', 'user-a')).toBe(
      'meant.compare.buyerSafeV3.account.user-a',
    )
    expect(accountSessionStorageKey('meant.compareProducts', 'user-a')).toBe(
      'meant.compareProducts.buyerSafeV3.account.user-a',
    )
    expect(accountSessionStorageKey('meant.cartSnapshots', 'user-a')).toBe(
      'meant.cartSnapshots.buyerSafeV2.account.user-a',
    )
    expect(accountSessionStorageKey('meant.agentPendingCartRuns', 'user-a')).toBe(
      'meant.agentPendingCartRuns.buyerSafeV2.account.user-a',
    )
    expect(accountSessionStorageKey('meant.agentCartProvenance', 'user-a')).toBe(
      'meant.agentCartProvenance.buyerSafeV2.account.user-a',
    )
    expect(() => accountSessionStorageKey('meant.locations', 'user-a')).toThrow(
      'Account storage key is not session-only',
    )
  })

  test('purges every legacy account-owned key while keeping device-global settings', () => {
    const values = new Map([
      ['meant.shelf', 'products'],
      ['meant.compare', 'products'],
      ['meant.compareProducts', 'products'],
      ['meant.prefsOn', 'preferences'],
      ['meant.budget', 'budget'],
      ['meant.location', 'location'],
      ['meant.locations', 'locations'],
      ['meant.clothingFit', 'fit'],
      ['meant.user', 'profile'],
      ['meant.cart', 'cart'],
      ['meant.cartSnapshots', 'cart facts'],
      ['meant.agentPendingCartRuns', 'pending cart run'],
      ['meant.agentCartProvenance', 'agent cart provenance'],
      ['meant.shelf.account.user-a', 'scoped persistent products'],
      ['meant.cartSnapshots.account.user-b', 'scoped persistent cart facts'],
      ['meant.agentPendingCartRuns.account.user-b', 'scoped pending cart run'],
      ['meant.agentCartProvenance.account.user-b', 'scoped agent cart provenance'],
      ['meant.locations.account.user-a', 'valid scoped durable preference'],
      ['meant.theme', 'dark'],
    ])

    purgeLegacyAccountStorage({
      get length() {
        return values.size
      },
      key: (index) => Array.from(values.keys())[index] ?? null,
      removeItem: (key) => {
        values.delete(key)
      },
    })

    expect(values).toEqual(
      new Map([
        ['meant.locations.account.user-a', 'valid scoped durable preference'],
        ['meant.theme', 'dark'],
      ]),
    )
  })
})
