import { describe, expect, test } from 'bun:test'

import { ApiError } from '../../../lib/apiError'
import {
  AgentActionRequestIdentityStore,
  type AgentActionRequestIdentityStorage,
  retainAgentActionIdempotencyKey,
} from './requestIdentity'

describe('agent direct-action request identity', () => {
  test('retains the key for unknown, uncertain, and still-running outcomes', () => {
    expect(retainAgentActionIdempotencyKey(new TypeError('connection lost'))).toBe(true)
    expect(
      retainAgentActionIdempotencyKey(
        new ApiError('The action timed out.', 408, 'agent_action_uncertain'),
      ),
    ).toBe(true)
    expect(
      retainAgentActionIdempotencyKey(
        new ApiError('The action is still running.', 409, 'agent_action_in_progress'),
      ),
    ).toBe(true)
    expect(retainAgentActionIdempotencyKey(new ApiError('Gateway timeout.', 504, null))).toBe(true)
  })

  test('releases the key after a definitive rejection', () => {
    expect(
      retainAgentActionIdempotencyKey(new ApiError('Invalid target.', 400, 'bad_request')),
    ).toBe(false)
  })

  test('classifies API errors independently of their constructor identity', () => {
    const foreignApiError = Object.assign(new Error('Invalid target.'), {
      name: 'ApiError',
      status: 400,
      code: 'bad_request',
    })
    const transferredApiError = {
      name: 'ApiError',
      message: 'Invalid target.',
      status: 400,
      code: 'bad_request',
    }

    expect(retainAgentActionIdempotencyKey(foreignApiError)).toBe(false)
    expect(retainAgentActionIdempotencyKey(transferredApiError)).toBe(false)
  })

  test('reuses one key through an uncertain retry and rotates it only after completion', () => {
    const store = new AgentActionRequestIdentityStore()
    let sequence = 0
    const create = () => `action:${++sequence}`

    expect(store.keyFor('prepare:offer-1', create)).toBe('action:1')
    store.failed(
      'prepare:offer-1',
      new ApiError('The action timed out.', 408, 'agent_action_uncertain'),
    )
    expect(store.keyFor('prepare:offer-1', create)).toBe('action:1')

    store.completed('prepare:offer-1')
    expect(store.keyFor('prepare:offer-1', create)).toBe('action:2')
  })

  test('survives a view remount until the outcome is confirmed', () => {
    const values = new Map<string, string>()
    const storage: AgentActionRequestIdentityStorage = {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: (key) => void values.delete(key),
    }
    const create = () => 'action:original'
    const firstView = new AgentActionRequestIdentityStore(storage)

    expect(firstView.keyFor('user:conversation:prepare:offer-1', create)).toBe('action:original')
    firstView.failed('user:conversation:prepare:offer-1', new TypeError('connection lost'))

    const remountedView = new AgentActionRequestIdentityStore(storage)
    expect(remountedView.keyFor('user:conversation:prepare:offer-1', () => 'action:new')).toBe(
      'action:original',
    )
    remountedView.completed('user:conversation:prepare:offer-1')
    expect(
      new AgentActionRequestIdentityStore(storage).keyFor(
        'user:conversation:prepare:offer-1',
        () => 'action:new',
      ),
    ).toBe('action:new')
  })
})
