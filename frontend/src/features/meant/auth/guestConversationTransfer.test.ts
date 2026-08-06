import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import {
  clearGuestConversationTransfer,
  readGuestConversationTransfer,
  storeGuestConversationTransfer,
} from './guestConversationTransfer'

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

describe('guest conversation transfer state', () => {
  test('survives a redirect and clears only the matching one-time token', () => {
    const transfer = {
      token: 'opaque-token',
      conversationId: 'conversation-1',
      expiresAt: '2026-08-05T12:10:00Z',
      guestUserId: 'guest-user-1',
    }
    storeGuestConversationTransfer(transfer)

    expect(readGuestConversationTransfer()).toEqual(transfer)
    clearGuestConversationTransfer('another-token')
    expect(readGuestConversationTransfer()).toEqual(transfer)
    clearGuestConversationTransfer(transfer.token)
    expect(readGuestConversationTransfer()).toBeNull()
  })
})
