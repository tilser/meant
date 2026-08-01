import { describe, expect, test } from 'bun:test'

import { ApiError } from '../../../lib/apiError'
import { agentTurnChatRejectionMessage } from './turnRejection'

describe('agent turn rejection messages', () => {
  test('returns the friendly daily-limit detail without exposing the HTTP status', () => {
    const detail =
      "You've reached today's limit of 100 messages. Come back tomorrow to continue shopping."

    expect(
      agentTurnChatRejectionMessage(new ApiError(detail, 429, 'agent_daily_message_limit')),
    ).toBe(detail)
  })

  test('leaves unrelated failures in the normal error flow', () => {
    expect(
      agentTurnChatRejectionMessage(new ApiError('Service unavailable.', 503, null)),
    ).toBeNull()
  })
})
