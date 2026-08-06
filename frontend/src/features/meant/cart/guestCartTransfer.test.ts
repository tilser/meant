import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import { accountSessionStorageKey } from '../shared/accountStorage'
import type { CartItem } from '../types'
import type { MerchantCartSnapshot } from './types'
import {
  consumeGuestCartTransferState,
  mergeTransferredGuestCart,
  mergeTransferredGuestCartSnapshots,
} from './guestCartTransfer'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
  }

  removeItem(key: string): void {
    this.values.delete(key)
  }
}

let sessionStorage: MemoryStorage

beforeEach(() => {
  sessionStorage = new MemoryStorage()
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { sessionStorage },
  })
})

afterEach(() => Reflect.deleteProperty(globalThis, 'window'))

function line(id: string, merchantKey: string, cartId: string): CartItem {
  return {
    id,
    merchant: `${merchantKey}.example`,
    merchantScopeKey: merchantKey,
    cartId,
    offerKey: `offer-${id}`,
    qty: 1,
  }
}

function snapshot(merchantKey: string, cartId: string): MerchantCartSnapshot {
  return {
    merchantKey,
    merchant: `${merchantKey}.example`,
    cartId,
    remoteCartId: `remote-${cartId}`,
    checkoutUrl: null,
    continueUrl: null,
    subtotalAmount: 20,
    totalAmount: 20,
    currency: 'USD',
    appliedCodes: [],
  }
}

describe('guest cart transfer', () => {
  test('consumes guest-scoped browser state only after the server claim', () => {
    const guestOwnerId = 'guest-user'
    const cart = [line('guest-product', 'merchant-a', 'guest-cart')]
    const snapshots = { 'merchant-a': snapshot('merchant-a', 'guest-cart') }
    const cartKey = accountSessionStorageKey('meant.cart', guestOwnerId)
    const snapshotsKey = accountSessionStorageKey('meant.cartSnapshots', guestOwnerId)
    sessionStorage.setItem(cartKey, JSON.stringify(cart))
    sessionStorage.setItem(snapshotsKey, JSON.stringify(snapshots))

    expect(consumeGuestCartTransferState(guestOwnerId)).toEqual({ cart, snapshots })
    expect(sessionStorage.getItem(cartKey)).toBeNull()
    expect(sessionStorage.getItem(snapshotsKey)).toBeNull()
  })

  test('guest state replaces the same merchant partition and preserves unrelated account carts', () => {
    const existingSameMerchant = line('account-old', 'merchant-a', 'account-cart')
    const unrelated = line('account-other', 'merchant-b', 'other-cart')
    const guest = line('guest-current', 'merchant-a', 'guest-cart')

    expect(mergeTransferredGuestCart([existingSameMerchant, unrelated], [guest])).toEqual([
      unrelated,
      guest,
    ])
    expect(
      mergeTransferredGuestCartSnapshots(
        {
          'merchant-a': snapshot('merchant-a', 'account-cart'),
          'merchant-b': snapshot('merchant-b', 'other-cart'),
        },
        { 'merchant-a': snapshot('merchant-a', 'guest-cart') },
      ),
    ).toEqual({
      'merchant-a': snapshot('merchant-a', 'guest-cart'),
      'merchant-b': snapshot('merchant-b', 'other-cart'),
    })
  })
})
