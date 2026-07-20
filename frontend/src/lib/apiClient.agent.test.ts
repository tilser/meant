import { afterEach, beforeEach, describe, expect, mock, test } from 'bun:test'

import type { AgentMessageProfile } from './apiClient'

let authenticatedUserId = 'user-a'

mock.module('./supabase', () => ({
  supabase: {
    auth: {
      getSession: async () => ({
        data: {
          session: { access_token: 'agent-token', user: { id: authenticatedUserId } },
        },
      }),
    },
  },
}))

const {
  cancelAgentRun,
  createAgentConversation,
  deleteAgentConversation,
  getCompleteAgentConversation,
  getAgentConversation,
  getAgentConversations,
  getAgentRun,
  openAgentRunEventStream,
  recordAgentDirectAction,
  submitAgentTurn,
  updateAgentConversation,
} = await import('./apiClient')

const originalFetch = globalThis.fetch
let requests: Request[] = []

const message: AgentMessageProfile = {
  messageId: 'message-1',
  runId: 'run-1',
  sequenceNumber: 1,
  role: 'USER',
  contentKind: 'TEXT',
  textContent: 'Find trail shoes',
  contentJson: null,
  correlationId: null,
  createdAt: '2026-07-18T12:00:00Z',
}

const run = {
  runId: 'run-1',
  conversationId: 'conversation-1',
  status: 'RUNNING',
  model: 'test-model',
  promptVersion: 'v1',
  iterationCount: 1,
  toolInvocationCount: 0,
  inputTokens: null,
  outputTokens: null,
  failureCode: null,
  safeMessage: null,
  cancellationRequested: false,
  latestCursor: 1,
  createdAt: '2026-07-18T12:00:00Z',
  startedAt: '2026-07-18T12:00:00Z',
  completedAt: null,
}

beforeEach(() => {
  authenticatedUserId = 'user-a'
  requests = []
  globalThis.fetch = mock(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = new Request(input, init)
    requests.push(request)
    if (request.url.includes('/events')) {
      return new Response('data: {}\n\n', {
        headers: { 'Content-Type': 'text/event-stream' },
      })
    }
    if (request.url.endsWith('/turns')) {
      return Response.json(
        { runId: 'run-1', firstEventCursor: 1, userMessage: message },
        { status: 202 },
      )
    }
    if (request.url.endsWith('/cancel') || request.url.endsWith('/runs/run-1')) {
      return Response.json(run)
    }
    if (request.url.endsWith('/actions')) {
      return Response.json({
        message: { ...message, role: 'USER_ACTION' },
        resultJson: '{}',
        artifacts: [],
      })
    }
    if (request.method === 'DELETE') {
      return new Response(null, { status: 204 })
    }
    return Response.json({
      conversationId: 'conversation-1',
      title: 'Trail shoes',
      status: 'ACTIVE',
      activeMissionId: null,
      latestSequence: 1,
      createdAt: '2026-07-18T12:00:00Z',
      updatedAt: '2026-07-18T12:00:00Z',
      rollingSummary: null,
      summaryVersion: 0,
      latestCursor: 1,
      messages: [message],
      artifacts: [],
    })
  }) as unknown as typeof fetch
})

afterEach(() => {
  globalThis.fetch = originalFetch
})

describe('agent conversation API', () => {
  test('uses the authenticated v1 lifecycle routes and explicit patch fields', async () => {
    await createAgentConversation({ title: 'Trail shoes', expectedUserId: 'user-a' })
    await getAgentConversations({ expectedUserId: 'user-a' })
    await getAgentConversation('conversation/1', { expectedUserId: 'user-a' })
    await updateAgentConversation({
      conversationId: 'conversation/1',
      title: 'Running shoes',
      archived: false,
      expectedUserId: 'user-a',
    })
    await deleteAgentConversation('conversation/1', { expectedUserId: 'user-a' })

    expect(requests.map((request) => `${request.method} ${request.url}`)).toEqual([
      'POST http://localhost:8080/api/v1/users/me/agent/conversations',
      'GET http://localhost:8080/api/v1/users/me/agent/conversations',
      'GET http://localhost:8080/api/v1/users/me/agent/conversations/conversation%2F1',
      'PATCH http://localhost:8080/api/v1/users/me/agent/conversations/conversation%2F1',
      'DELETE http://localhost:8080/api/v1/users/me/agent/conversations/conversation%2F1',
    ])
    expect(
      requests.every((request) => request.headers.get('Authorization') === 'Bearer agent-token'),
    ).toBe(true)
    expect(await requests[0]?.json()).toEqual({ title: 'Trail shoes' })
    expect(await requests[3]?.json()).toEqual({ title: 'Running shoes', archived: false })
  })

  test('submits visible product and Shelf context with the natural-language turn', async () => {
    const turn = await submitAgentTurn({
      conversationId: 'conversation-1',
      message: 'Add the third one',
      visibleProductContext: {
        sourceMessageId: 'message-tool-search',
        orderedCanonicalProductKeys: ['product-5', 'product-6', 'product-7', 'product-8'],
      },
      shelfContext: {
        items: [
          {
            kind: 'PRODUCT',
            canonicalProductKey: 'product-5',
            title: 'Linen shirt',
            relatedProductNames: [],
          },
        ],
      },
      expectedUserId: 'user-a',
    })

    expect(turn).toEqual({ runId: 'run-1', firstEventCursor: 1, userMessage: message })
    expect(requests[0]?.method).toBe('POST')
    expect(requests[0]?.url).toEndWith('/api/v1/users/me/agent/conversations/conversation-1/turns')
    expect(await requests[0]?.json()).toEqual({
      message: 'Add the third one',
      visibleProductContext: {
        sourceMessageId: 'message-tool-search',
        orderedCanonicalProductKeys: ['product-5', 'product-6', 'product-7', 'product-8'],
      },
      shelfContext: {
        items: [
          {
            kind: 'PRODUCT',
            canonicalProductKey: 'product-5',
            title: 'Linen shirt',
            relatedProductNames: [],
          },
        ],
      },
    })
  })

  test('treats an already deleted conversation as removed from history', async () => {
    globalThis.fetch = mock(
      async () => new Response(null, { status: 404 }),
    ) as unknown as typeof fetch

    await expect(
      deleteAgentConversation('conversation-1', { expectedUserId: 'user-a' }),
    ).resolves.toBeUndefined()
  })

  test('loads every transcript page and deduplicates the latest artifact projection', async () => {
    globalThis.fetch = mock(async (input: RequestInfo | URL, init?: RequestInit) => {
      const request = new Request(input, init)
      requests.push(request)
      const after = new URL(request.url).searchParams.get('afterSequence')
      const sequenceNumber = after === '0' ? 1 : 2
      return Response.json({
        conversationId: 'conversation-1',
        title: 'Long conversation',
        status: 'ACTIVE',
        activeMissionId: null,
        latestSequence: 2,
        createdAt: '2026-07-18T12:00:00Z',
        updatedAt: '2026-07-18T12:02:00Z',
        rollingSummary: null,
        summaryVersion: 0,
        latestCursor: 4,
        messages: [{ ...message, messageId: `message-${sequenceNumber}`, sequenceNumber }],
        artifacts: [
          {
            artifactId: 'latest-artifact',
            messageId: 'message-2',
            runId: 'run-1',
            type: 'PRODUCT',
            ordinal: 1,
            stableKey: 'product-1',
            label: null,
            canonicalProductKey: 'product-1',
            offerKey: 'offer-1',
            inventoryItemId: null,
            cartId: null,
            cartLineId: null,
            checkoutAttemptId: null,
            payloadJson: '{}',
            createdAt: '2026-07-18T12:02:00Z',
          },
        ],
      })
    }) as unknown as typeof fetch

    const conversation = await getCompleteAgentConversation('conversation-1', {
      expectedUserId: 'user-a',
      pageSize: 1,
    })

    expect(conversation.messages.map((item) => item.sequenceNumber)).toEqual([1, 2])
    expect(conversation.artifacts).toHaveLength(1)
    expect(
      requests.map((request) => new URL(request.url).searchParams.get('afterSequence')),
    ).toEqual(['0', '1'])
  })

  test('records a successful direct UI action without creating a model turn', async () => {
    await recordAgentDirectAction({
      conversationId: 'conversation-1',
      toolName: 'add_to_cart',
      argumentsJson: '{"offerKey":"offer-1"}',
      idempotencyKey: 'action-1',
      summary: 'Added Trail shoe to cart',
      expectedUserId: 'user-a',
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.url).toEndWith('/agent/conversations/conversation-1/actions')
    expect(await requests[0]?.json()).toEqual({
      toolName: 'add_to_cart',
      argumentsJson: '{"offerKey":"offer-1"}',
      idempotencyKey: 'action-1',
      summary: 'Added Trail shoe to cart',
    })
  })
})

describe('agent run API', () => {
  test('loads snapshots, opens an authenticated replay stream, and cancels explicitly', async () => {
    await getAgentRun('run/1', { expectedUserId: 'user-a' })
    const stream = await openAgentRunEventStream({
      runId: 'run/1',
      afterCursor: 7,
      expectedUserId: 'user-a',
    })
    await cancelAgentRun('run-1', { expectedUserId: 'user-a' })

    expect(await stream.text()).toBe('data: {}\n\n')
    expect(requests.map((request) => `${request.method} ${request.url}`)).toEqual([
      'GET http://localhost:8080/api/v1/users/me/agent/runs/run%2F1',
      'GET http://localhost:8080/api/v1/users/me/agent/runs/run%2F1/events?afterCursor=7',
      'POST http://localhost:8080/api/v1/users/me/agent/runs/run-1/cancel',
    ])
    expect(requests[1]?.headers.get('Accept')).toBe('text/event-stream')
  })

  test('refuses a request if the authenticated account changed', async () => {
    authenticatedUserId = 'user-b'

    await expect(getAgentRun('run-1', { expectedUserId: 'user-a' })).rejects.toThrow(
      'Authenticated user changed before request',
    )
    expect(requests).toHaveLength(0)
  })

  test('surfaces a retention-expired stream as a typed 410 error', async () => {
    globalThis.fetch = mock(async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push(new Request(input, init))
      return Response.json(
        {
          code: 'AGENT_EVENT_CURSOR_EXPIRED',
          detail: 'Reload snapshots before reconnecting.',
        },
        { status: 410 },
      )
    }) as unknown as typeof fetch

    try {
      await openAgentRunEventStream({ runId: 'run-1', afterCursor: 1 })
      throw new Error('Expected stream opening to fail')
    } catch (error) {
      expect(error).toMatchObject({
        status: 410,
        code: 'AGENT_EVENT_CURSOR_EXPIRED',
        message: 'Reload snapshots before reconnecting.',
      })
    }
  })
})
