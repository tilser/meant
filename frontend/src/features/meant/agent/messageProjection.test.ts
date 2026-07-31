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
  test('preserves Markdown while an assistant message is streaming', () => {
    const streaming = {
      ...projection('RUNNING'),
      assistantMessages: [],
      streamingAssistantText: '## Picks\n\n1. **Jacket**\n2. *Coat*',
    }

    const messages = withProjectedAgentMessages([], new Set(), new Set(), 'run-current', streaming)

    expect(messages[0]?.blocks).toEqual([
      { type: 'text', text: '## Picks\n\n1. **Jacket**\n2. *Coat*' },
    ])
  })

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

    expect(messages).toHaveLength(1)
    expect(messages[0]).toMatchObject({
      id: 'clarification-message',
      blocks: [
        { type: 'text', text: clarification },
        { type: 'products', query: 'caps' },
      ],
    })
  })

  test('hosts a completed live assistant message together with current-run product cards', () => {
    const messages = withProjectedAgentMessages(
      [productResults],
      new Set([productResults.id]),
      new Set([productResults.id]),
      'run-current',
      projection('RUNNING'),
    )

    expect(messages).toHaveLength(1)
    expect(messages[0]).toMatchObject({
      id: 'clarification-message',
      blocks: [
        { type: 'text', text: clarification },
        { type: 'products', query: 'caps' },
      ],
    })
  })

  test('hosts grounded similarity cards under the live assistant message', () => {
    const messages = withProjectedAgentMessages(
      [similarResults],
      new Set([similarResults.id]),
      new Set([similarResults.id]),
      'run-current',
      projection('RUNNING'),
    )

    expect(messages).toHaveLength(1)
    expect(messages[0]).toMatchObject({
      id: 'clarification-message',
      blocks: [{ type: 'text', text: clarification }, { type: 'similar' }],
    })
  })

  test('keeps early product artifacts disabled and visibly finishing before assistant copy arrives', () => {
    const running = {
      ...projection('RUNNING'),
      assistantMessages: [],
    }

    const messages = withProjectedAgentMessages(
      [productResults],
      new Set([productResults.id]),
      new Set([productResults.id]),
      'run-current',
      running,
    )

    expect(messages).toEqual([
      {
        ...productResults,
        pending: true,
        pendingText: 'Finishing…',
        settling: true,
      },
    ])
  })

  test('moves early product artifacts under streaming assistant copy until it finishes', () => {
    const streaming = {
      ...projection('RUNNING'),
      assistantMessages: [],
      streamingAssistantText: 'Here are the strongest matches so far:',
    }

    const messages = withProjectedAgentMessages(
      [productResults],
      new Set([productResults.id]),
      new Set([productResults.id]),
      'run-current',
      streaming,
    )

    expect(messages).toEqual([
      {
        id: 'run-current:streaming',
        role: 'ai',
        blocks: [
          { type: 'text', text: 'Here are the strongest matches so far:' },
          { type: 'products', products: [], query: 'caps' },
        ],
        pending: true,
        pendingText: 'Finishing…',
        settling: true,
      },
    ])
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
