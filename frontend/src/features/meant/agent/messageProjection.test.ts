import { describe, expect, test } from 'bun:test'

import type { DiscoverChatMessage } from '../chat/types'
import type { AgentRunProjection } from './eventReducer'
import { withProjectedAgentMessages } from './messageProjection'

const clarification = 'Which product should I add? Reply with a number or product name.'

function projection(status: AgentRunProjection['status']): AgentRunProjection {
  return {
    runId: 'run-current',
    conversationId: 'conversation-1',
    status,
    lastCursor: 4,
    streamingAssistantText: '',
    assistantMessages: [
      {
        messageId: 'clarification-message',
        sequenceNumber: 5,
        text: clarification,
        completedAt: '2026-07-19T12:00:00Z',
      },
    ],
    tools: {},
    artifacts: {},
    artifactOrder: [],
    terminalMessage: status === 'WAITING_FOR_USER' ? clarification : null,
    failureCode: null,
    lastOccurredAt: '2026-07-19T12:00:00Z',
    duplicateEventCount: 0,
    unknownEventCount: 0,
  }
}

const productResults: DiscoverChatMessage = {
  id: 'previous-product-results',
  role: 'ai',
  blocks: [{ type: 'products', products: [], query: 'caps' }],
}

const similarResults: DiscoverChatMessage = {
  id: 'current-similar-results',
  role: 'ai',
  blocks: [
    { type: 'text', text: 'Here are similar jackets to your Black Quilted Jacket:' },
    {
      type: 'similar',
      products: [],
      similarityAnchor: {
        canonicalProductKey: 'canonical:owned-jacket',
        inventoryItemId: '00000000-0000-0000-0000-000000000402',
        label: 'Black Quilted Jacket',
        query: 'similar jackets',
      },
    },
  ],
}

describe('agent message projection', () => {
  test('renders a waiting clarification immediately when prior product results exist', () => {
    const messages = withProjectedAgentMessages(
      [productResults],
      new Set([productResults.id]),
      new Set(['current-user-message']),
      'run-current',
      projection('WAITING_FOR_USER'),
    )

    expect(messages).toHaveLength(2)
    expect(messages.at(-1)?.blocks).toEqual([{ type: 'text', text: clarification }])
  })

  test('never suppresses a waiting clarification behind current-run product cards', () => {
    const messages = withProjectedAgentMessages(
      [productResults],
      new Set([productResults.id]),
      new Set([productResults.id]),
      'run-current',
      projection('WAITING_FOR_USER'),
    )

    expect(messages.map((message) => message.id)).toEqual([
      productResults.id,
      'clarification-message',
    ])
  })

  test('still suppresses a duplicate running summary for current-run product cards', () => {
    const messages = withProjectedAgentMessages(
      [productResults],
      new Set([productResults.id]),
      new Set([productResults.id]),
      'run-current',
      projection('RUNNING'),
    )

    expect(messages).toEqual([productResults])
  })

  test('suppresses duplicate live assistant prose behind grounded similarity cards', () => {
    const messages = withProjectedAgentMessages(
      [similarResults],
      new Set([similarResults.id]),
      new Set([similarResults.id]),
      'run-current',
      projection('RUNNING'),
    )

    expect(messages).toEqual([similarResults])
  })

  test('does not duplicate a clarification already present in the durable transcript', () => {
    const durableQuestion: DiscoverChatMessage = {
      id: 'clarification-message',
      role: 'ai',
      blocks: [{ type: 'text', text: clarification }],
    }
    const messages = withProjectedAgentMessages(
      [productResults, durableQuestion],
      new Set([productResults.id, durableQuestion.id]),
      new Set([durableQuestion.id]),
      'run-current',
      projection('WAITING_FOR_USER'),
    )

    expect(messages).toEqual([productResults, durableQuestion])
  })
})
