import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import {
  createDiscoverChatThread,
  deleteStoredDiscoverChatThread,
  initialDiscoverChatThreads,
  saveStoredDiscoverChatThreads,
} from './utils'

const originalWindow = globalThis.window
let store: Map<string, string>

beforeEach(() => {
  store = new Map<string, string>()
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {
      localStorage: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => {
          store.set(key, value)
        },
      },
    } as unknown as Window,
  })
})

afterEach(() => {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: originalWindow,
  })
})

describe('discover chat history storage', () => {
  test('restores locally saved discover chat threads', () => {
    const thread = {
      ...createDiscoverChatThread(
        [
          {
            id: 'message-1',
            role: 'you' as const,
            text: 'running shoes',
          },
        ],
        'running shoes',
      ),
      updatedAt: 123,
    }

    saveStoredDiscoverChatThreads([thread])

    expect(initialDiscoverChatThreads()).toEqual([thread])
  })

  test('deletes locally saved discover chat threads', () => {
    const thread = createDiscoverChatThread(
      [
        {
          id: 'message-1',
          role: 'you' as const,
          text: 'coffee',
        },
      ],
      'coffee',
    )
    saveStoredDiscoverChatThreads([thread])

    deleteStoredDiscoverChatThread(thread.id)

    expect(initialDiscoverChatThreads()[0]?.messages).toHaveLength(0)
  })
})
