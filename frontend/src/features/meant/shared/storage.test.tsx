import { afterEach, beforeEach, describe, expect, test } from 'bun:test'
import { useRef, useState } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { accountSessionStorageKey } from './accountStorage'
import { useSessionStoredState } from './storage'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
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

afterEach(() => {
  Reflect.deleteProperty(globalThis, 'window')
})

function CartProbe({ ownerId }: { ownerId: string }) {
  const [cart] = useSessionStoredState<{ id: string; qty: number }[]>(
    accountSessionStorageKey('meant.cart', ownerId),
    [],
  )
  return <span>{cart.map((item) => `${item.id}:${item.qty}`).join(',')}</span>
}

describe('session stored state hydration', () => {
  test('restores an account cart on the first browser render after reload', () => {
    const ownerId = 'user-1'
    sessionStorage.setItem(
      accountSessionStorageKey('meant.cart', ownerId),
      JSON.stringify([{ id: 'persisted-product', qty: 2 }]),
    )

    expect(renderToStaticMarkup(<CartProbe ownerId={ownerId} />)).toContain('persisted-product:2')
  })

  test('resolves an update during initial hydration from the persisted cart', () => {
    const ownerId = 'user-2'
    sessionStorage.setItem(
      accountSessionStorageKey('meant.cart', ownerId),
      JSON.stringify([{ id: 'persisted-product', qty: 1 }]),
    )

    function InitialUpdateProbe() {
      const [cart, setCart] = useSessionStoredState<{ id: string; qty: number }[]>(
        accountSessionStorageKey('meant.cart', ownerId),
        [],
      )
      const updated = useRef(false)
      if (!updated.current) {
        updated.current = true
        setCart((current) => current.map((item) => ({ ...item, qty: item.qty + 1 })))
      }
      return <span>{cart.map((item) => `${item.id}:${item.qty}`).join(',')}</span>
    }

    expect(renderToStaticMarkup(<InitialUpdateProbe />)).toContain('persisted-product:2')
  })

  test('loads the account cart when auth hydration changes the owner key', () => {
    const ownerId = 'user-3'
    sessionStorage.setItem(
      accountSessionStorageKey('meant.cart', ownerId),
      JSON.stringify([{ id: 'account-product', qty: 1 }]),
    )

    function AuthHydrationProbe() {
      const [activeOwnerId, setActiveOwnerId] = useState<string>()
      const [cart] = useSessionStoredState<{ id: string; qty: number }[]>(
        accountSessionStorageKey('meant.cart', activeOwnerId),
        [],
      )
      if (!activeOwnerId) {
        setActiveOwnerId(ownerId)
      }
      return <span>{cart.map((item) => `${item.id}:${item.qty}`).join(',')}</span>
    }

    expect(renderToStaticMarkup(<AuthHydrationProbe />)).toContain('account-product:1')
    expect(sessionStorage.getItem(accountSessionStorageKey('meant.cart', ownerId))).toBe(
      JSON.stringify([{ id: 'account-product', qty: 1 }]),
    )
  })
})
