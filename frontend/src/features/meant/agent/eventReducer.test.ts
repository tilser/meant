import { describe, expect, test } from 'bun:test'

import type { AgentRunSnapshotProfile } from '../../../lib/apiClient'
import {
  createAgentEventReducerState,
  reduceAgentEvent,
  restoreAgentRunSnapshot,
  snapshotRecoveryForAgentRun,
} from './eventReducer'

const occurredAt = '2026-07-18T12:00:00Z'

function event(
  runId: string,
  cursor: number,
  type: string,
  payload: Record<string, unknown> = {},
  overrides: Partial<{
    schemaVersion: number
    conversationId: string
    payloadJson: string
  }> = {},
): Record<string, unknown> {
  return {
    schemaVersion: overrides.schemaVersion ?? 1,
    cursor,
    conversationId: overrides.conversationId ?? `conversation-${runId}`,
    runId,
    type,
    occurredAt,
    payloadJson: overrides.payloadJson ?? JSON.stringify(payload),
  }
}

const artifact = {
  artifactId: 'artifact-1',
  messageId: 'message-1',
  runId: 'run-a',
  type: 'PRODUCT',
  ordinal: 1,
  stableKey: 'product:one',
  label: 'Trail shoe',
  canonicalProductKey: 'product-one',
  offerKey: 'offer-one',
  inventoryItemId: null,
  cartId: null,
  cartLineId: null,
  checkoutAttemptId: null,
  payloadJson: '{"productKey":"product-one"}',
  createdAt: occurredAt,
}

describe('agent v1 event reducer', () => {
  test('assembles assistant deltas and replaces them with the persisted completed message', () => {
    let state = createAgentEventReducerState()
    state = reduceAgentEvent(state, event('run-a', 1, 'run.started'))
    state = reduceAgentEvent(state, event('run-a', 2, 'assistant.delta', { text: 'Hel' }))
    state = reduceAgentEvent(state, event('run-a', 2, 'assistant.delta', { text: 'wrong' }))
    state = reduceAgentEvent(state, event('run-a', 3, 'assistant.delta', { text: 'lo' }))

    expect(state.runs['run-a']?.streamingAssistantText).toBe('Hello')
    expect(state.runs['run-a']?.duplicateEventCount).toBe(1)

    state = reduceAgentEvent(
      state,
      event('run-a', 4, 'assistant.completed', {
        messageId: 'message-2',
        sequenceNumber: 2,
        text: 'Hello!',
      }),
    )

    expect(state.runs['run-a']?.streamingAssistantText).toBe('')
    expect(state.runs['run-a']?.assistantMessages).toEqual([
      {
        messageId: 'message-2',
        sequenceNumber: 2,
        text: 'Hello!',
        completedAt: occurredAt,
      },
    ])
    expect(state.runs['run-a']?.lastCursor).toBe(4)
  })

  test('keeps cursor ordering independent for each run and consumes unknown v1 types as no-ops', () => {
    let state = createAgentEventReducerState()
    state = reduceAgentEvent(state, event('run-a', 1, 'run.started'))
    state = reduceAgentEvent(state, event('run-b', 1, 'run.started'))
    state = reduceAgentEvent(
      state,
      event('run-a', 2, 'future.activity', {}, { payloadJson: 'not parsed for unknown types' }),
    )
    state = reduceAgentEvent(state, event('run-a', 3, 'assistant.delta', { text: 'A' }))
    state = reduceAgentEvent(state, event('run-b', 2, 'assistant.delta', { text: 'B' }))

    expect(state.runs['run-a']?.lastCursor).toBe(3)
    expect(state.runs['run-a']?.streamingAssistantText).toBe('A')
    expect(state.runs['run-a']?.unknownEventCount).toBe(1)
    expect(state.runs['run-b']?.lastCursor).toBe(2)
    expect(state.runs['run-b']?.streamingAssistantText).toBe('B')
  })

  test('does not apply across a cursor gap and clears the signal only after ordered replay heals it', () => {
    let state = reduceAgentEvent(createAgentEventReducerState(), event('run-a', 1, 'run.started'))
    state = reduceAgentEvent(state, event('run-a', 3, 'assistant.delta', { text: 'late' }))

    expect(state.runs['run-a']?.lastCursor).toBe(1)
    expect(state.runs['run-a']?.streamingAssistantText).toBe('')
    expect(state.snapshotRecovery['run-a']).toMatchObject({
      reason: 'CURSOR_GAP',
      expectedCursor: 2,
      receivedCursor: 3,
      requiredSnapshots: ['RUN', 'CONVERSATION'],
    })

    state = reduceAgentEvent(state, event('run-a', 2, 'assistant.delta', { text: 'on time ' }))
    expect(state.snapshotRecovery['run-a']).toBeDefined()
    state = reduceAgentEvent(state, event('run-a', 3, 'assistant.delta', { text: 'late' }))

    expect(state.runs['run-a']?.streamingAssistantText).toBe('on time late')
    expect(state.snapshotRecovery['run-a']).toBeUndefined()
  })

  test('requests snapshots for unknown schema versions and malformed known payloads', () => {
    let unsupported = reduceAgentEvent(
      createAgentEventReducerState(),
      event('run-a', 1, 'run.started', {}, { schemaVersion: 2 }),
    )
    expect(unsupported.runs['run-a']?.lastCursor).toBe(0)
    expect(unsupported.snapshotRecovery['run-a']).toMatchObject({
      reason: 'UNSUPPORTED_SCHEMA',
      schemaVersion: 2,
      receivedCursor: 1,
    })

    unsupported = reduceAgentEvent(unsupported, event('run-b', 1, 'assistant.delta', { text: 42 }))
    expect(unsupported.runs['run-b']?.streamingAssistantText).toBe('')
    expect(unsupported.snapshotRecovery['run-b']).toMatchObject({
      reason: 'MALFORMED_EVENT',
      receivedCursor: 1,
    })
  })

  test('tracks tool activity and upserts artifacts by stable identity', () => {
    let state = reduceAgentEvent(createAgentEventReducerState(), event('run-a', 1, 'run.started'))
    state = reduceAgentEvent(
      state,
      event('run-a', 2, 'tool.proposed', {
        modelToolCallId: 'tool-call-1',
        toolName: 'search_catalog',
        summary: 'Search for trail shoes',
      }),
    )
    state = reduceAgentEvent(
      state,
      event('run-a', 3, 'tool.completed', {
        modelToolCallId: 'tool-call-1',
        toolName: 'search_catalog',
        summary: 'Found one product',
        resultJson: '{"count":1}',
      }),
    )
    state = reduceAgentEvent(state, event('run-a', 4, 'artifact.upserted', { artifact }))
    state = reduceAgentEvent(
      state,
      event('run-a', 5, 'artifact.upserted', {
        artifact: { ...artifact, artifactId: 'artifact-2', label: 'Updated trail shoe' },
      }),
    )

    expect(state.runs['run-a']?.tools['tool-call-1']).toMatchObject({
      status: 'COMPLETED',
      toolName: 'search_catalog',
      resultJson: '{"count":1}',
    })
    expect(state.runs['run-a']?.artifactOrder).toEqual(['product:one'])
    expect(state.runs['run-a']?.artifacts['product:one']?.artifactId).toBe('artifact-2')
    expect(state.runs['run-a']?.artifacts['product:one']?.label).toBe('Updated trail shoe')
  })

  test('sanitizes live assistant and tool text without collapsing ordinary sentences', () => {
    let state = reduceAgentEvent(createAgentEventReducerState(), event('run-a', 1, 'run.started'))
    state = reduceAgentEvent(
      state,
      event('run-a', 2, 'assistant.delta', {
        text:
          'I need details from seller.myshopify.com. ' +
          'Browse https://official.example/products/shoe.',
      }),
    )
    state = reduceAgentEvent(
      state,
      event('run-a', 3, 'tool.completed', {
        modelToolCallId: 'tool-call-1',
        toolName: 'search_catalog',
        summary: 'Called mcp.shop.example.',
        resultJson: JSON.stringify({
          message: 'Retry https://transport.example/api/ucp/mcp/session/1.',
          officialUrl: 'https://official.example/products/shoe',
        }),
      }),
    )

    expect(state.runs['run-a']?.streamingAssistantText).toBe(
      'I need details from the merchant. Browse https://official.example/products/shoe.',
    )
    expect(state.runs['run-a']?.tools['tool-call-1']?.summary).toBe('Called the merchant.')
    expect(JSON.parse(state.runs['run-a']?.tools['tool-call-1']?.resultJson ?? 'null')).toEqual({
      message: 'Retry the merchant.',
      officialUrl: 'https://official.example/products/shoe',
    })
  })

  test('persists terminal state before ignoring duplicate terminal replay', () => {
    let state = reduceAgentEvent(createAgentEventReducerState(), event('run-a', 1, 'run.started'))
    const failed = event('run-a', 2, 'run.failed', {
      text: 'Please try again.',
      failureCode: 'MODEL_TIMEOUT',
    })
    state = reduceAgentEvent(state, failed)
    state = reduceAgentEvent(state, failed)

    expect(state.runs['run-a']).toMatchObject({
      status: 'FAILED',
      terminalMessage: 'Please try again.',
      failureCode: 'MODEL_TIMEOUT',
      lastCursor: 2,
      duplicateEventCount: 1,
    })
    expect(state.snapshotRecovery['run-a']).toBeUndefined()

    state = reduceAgentEvent(state, event('run-a', 3, 'assistant.delta', { text: 'too late' }))
    expect(state.snapshotRecovery['run-a']?.reason).toBe('EVENT_AFTER_TERMINAL')
    expect(state.runs['run-a']?.lastCursor).toBe(2)
  })

  test('resets a damaged projection to an authoritative run snapshot', () => {
    let state = reduceAgentEvent(createAgentEventReducerState(), event('run-a', 2, 'run.completed'))
    expect(state.snapshotRecovery['run-a']?.reason).toBe('CURSOR_GAP')

    const snapshot: AgentRunSnapshotProfile = {
      runId: 'run-a',
      conversationId: 'conversation-run-a',
      status: 'COMPLETED',
      model: 'test-model',
      promptVersion: 'v1',
      iterationCount: 1,
      toolInvocationCount: 0,
      inputTokens: 10,
      outputTokens: 5,
      failureCode: null,
      safeMessage: null,
      cancellationRequested: false,
      latestCursor: 2,
      createdAt: occurredAt,
      startedAt: occurredAt,
      completedAt: occurredAt,
    }
    state = restoreAgentRunSnapshot(state, snapshot)

    expect(state.snapshotRecovery['run-a']).toBeUndefined()
    expect(state.runs['run-a']).toMatchObject({
      status: 'COMPLETED',
      lastCursor: 2,
      assistantMessages: [],
      artifacts: {},
    })
  })

  test('clears an envelope-less recovery after one authoritative snapshot', () => {
    let state = reduceAgentEvent(createAgentEventReducerState(), event('run-a', 1, 'run.started'))
    state = reduceAgentEvent(state, {
      kind: 'malformed',
      reason: 'SSE data must contain a JSON event envelope',
    })

    expect(snapshotRecoveryForAgentRun(state, 'run-a')).toMatchObject({
      reason: 'MALFORMED_EVENT',
      runId: null,
    })

    const snapshot: AgentRunSnapshotProfile = {
      runId: 'run-a',
      conversationId: 'conversation-run-a',
      status: 'RUNNING',
      model: 'test-model',
      promptVersion: 'v1',
      iterationCount: 1,
      toolInvocationCount: 0,
      inputTokens: 10,
      outputTokens: 0,
      failureCode: null,
      safeMessage: null,
      cancellationRequested: false,
      latestCursor: 1,
      createdAt: occurredAt,
      startedAt: occurredAt,
      completedAt: null,
    }
    state = restoreAgentRunSnapshot(state, snapshot)

    expect(state.unscopedRecovery).toBeNull()
    expect(snapshotRecoveryForAgentRun(state, 'run-a')).toBeNull()

    state = reduceAgentEvent(state, event('run-a', 2, 'assistant.delta', { text: 'reconnected' }))
    expect(state.runs['run-a']?.streamingAssistantText).toBe('reconnected')
    expect(state.runs['run-a']?.lastCursor).toBe(2)
    expect(snapshotRecoveryForAgentRun(state, 'run-a')).toBeNull()
  })
})
