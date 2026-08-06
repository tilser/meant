import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import {
  claimPendingAccountAction,
  completePendingAccountAction,
  pendingAccountNavigation,
  peekPendingAccountAction,
  storePendingAccountAction,
} from './pendingAccountAction'

class MemoryStorage {
  private readonly values = new Map<string, string>()
  getItem(key: string) {
    return this.values.get(key) ?? null
  }
  setItem(key: string, value: string) {
    this.values.set(key, value)
  }
  removeItem(key: string) {
    this.values.delete(key)
  }
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: { sessionStorage: new MemoryStorage() },
  })
})

afterEach(() => Reflect.deleteProperty(globalThis, 'window'))

describe('pending account actions', () => {
  test('survives redirects and can be claimed exactly once', () => {
    storePendingAccountAction({ type: 'SAVE_PRODUCT', productId: 'product-1' })

    expect(peekPendingAccountAction()).toEqual({ type: 'SAVE_PRODUCT', productId: 'product-1' })
    const claimed = claimPendingAccountAction()
    expect(claimed?.action.type).toBe('SAVE_PRODUCT')
    expect(claimPendingAccountAction()).toBeNull()

    if (!claimed) throw new Error('Expected an action')
    completePendingAccountAction(claimed.id)
    expect(peekPendingAccountAction()).toBeNull()
  })

  test('keeps an imported guest conversation ahead of deferred preference navigation', () => {
    expect(pendingAccountNavigation({ type: 'OPEN_PREFERENCES' }, true)).toBeNull()
    expect(
      pendingAccountNavigation(
        { type: 'REMEMBER_PREFERENCES', preferenceDraftId: 'draft-1' },
        true,
      ),
    ).toBeNull()
    expect(pendingAccountNavigation({ type: 'OPEN_PREFERENCES' }, false)).toEqual({
      view: 'preferences',
    })
  })
})
