import { describe, expect, test } from 'bun:test'

import type { AgentConversationSummaryProfile } from '../../../lib/apiClient'
import { discoverThreadMessageCount, discoverThreadPreview } from '../chat/utils'
import { threadFromAgentConversationSummary } from './conversationHistory'

const summary = {
  conversationId: 'conversation-1',
  merchantId: null,
  title: 'cool black jacket',
  status: 'ACTIVE',
  activeMissionId: null,
  latestSequence: 4,
  createdAt: '2026-07-22T19:30:00.000Z',
  updatedAt: '2026-07-22T19:33:00.000Z',
} satisfies AgentConversationSummaryProfile

describe('agent conversation history', () => {
  test('uses the persisted sequence count before the transcript is loaded', () => {
    const thread = threadFromAgentConversationSummary(summary)

    expect(thread.messages).toEqual([])
    expect(discoverThreadMessageCount(thread)).toBe('4 messages')
    expect(discoverThreadPreview(thread)).toBe('Open to view messages')
  })

  test('uses the visible messages after the transcript is loaded', () => {
    const thread = threadFromAgentConversationSummary(summary, [
      { id: 'user-message', role: 'you', text: 'Find me a cool black jacket' },
      { id: 'assistant-message', role: 'ai', text: 'Here are my picks.' },
    ])

    expect(discoverThreadMessageCount(thread)).toBe('2 messages')
    expect(discoverThreadPreview(thread)).toBe('Here are my picks.')
  })

  test('sanitizes technical transport coordinates in persisted titles', () => {
    const thread = threadFromAgentConversationSummary({
      ...summary,
      title:
        'Continue with seller.myshopify.com, then browse https://official.example/products/shoe',
    })

    expect(thread.title).toBe(
      'Continue with the merchant, then browse https://official.example/products/shoe',
    )
  })
})
