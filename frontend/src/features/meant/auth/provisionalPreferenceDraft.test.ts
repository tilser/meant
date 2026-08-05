import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import {
  clearProvisionalPreferenceDraft,
  createProvisionalPreferenceDraft,
  readProvisionalPreferenceDraft,
} from './provisionalPreferenceDraft'

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

describe('provisional preference drafts', () => {
  test('keeps a bounded, deduplicated draft until explicit confirmation or dismissal', () => {
    const draft = createProvisionalPreferenceDraft(['wool', 'wool', 'no-polyester'])

    expect(readProvisionalPreferenceDraft(draft.id)?.preferenceIds).toEqual([
      'wool',
      'no-polyester',
    ])
    expect(readProvisionalPreferenceDraft('another-draft')).toBeNull()

    clearProvisionalPreferenceDraft(draft.id)
    expect(readProvisionalPreferenceDraft(draft.id)).toBeNull()
  })
})
