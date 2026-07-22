import { describe, expect, test } from 'bun:test'

import type { DiscoverChatMessage } from '../chat/types'
import { mergeAnchoredLocalMessages, type AnchoredLocalMessage } from './localMessageOrdering'

function message(id: string): DiscoverChatMessage {
  return { id, role: 'ai', blocks: [{ type: 'text', text: id }] }
}

function placement(id: string, precedingMessageIds: readonly string[]): AnchoredLocalMessage {
  return { message: message(id), precedingMessageIds }
}

describe('local agent message ordering', () => {
  test('keeps a coming-soon message ahead of later durable messages', () => {
    const result = mergeAnchoredLocalMessages(
      [message('checkout'), message('next-user'), message('next-assistant')],
      [placement('coming-soon', ['checkout'])],
    )

    expect(result.map(({ id }) => id)).toEqual([
      'checkout',
      'coming-soon',
      'next-user',
      'next-assistant',
    ])
  })

  test('places a local message created in an empty conversation before later messages', () => {
    const result = mergeAnchoredLocalMessages(
      [message('next-user'), message('next-assistant')],
      [placement('coming-soon', [])],
    )

    expect(result.map(({ id }) => id)).toEqual(['coming-soon', 'next-user', 'next-assistant'])
  })

  test('preserves local insertion order', () => {
    const result = mergeAnchoredLocalMessages(
      [message('checkout'), message('next-user')],
      [
        placement('coming-soon', ['checkout']),
        placement('newsletter-status', ['checkout', 'coming-soon']),
      ],
    )

    expect(result.map(({ id }) => id)).toEqual([
      'checkout',
      'coming-soon',
      'newsletter-status',
      'next-user',
    ])
  })

  test('uses the last surviving predecessor when a transient message is replaced', () => {
    const result = mergeAnchoredLocalMessages(
      [message('checkout'), message('next-user')],
      [placement('coming-soon', ['checkout', 'transient-assistant'])],
    )

    expect(result.map(({ id }) => id)).toEqual(['checkout', 'coming-soon', 'next-user'])
  })
})
