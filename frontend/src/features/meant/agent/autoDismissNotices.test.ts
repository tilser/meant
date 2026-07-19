import { describe, expect, test } from 'bun:test'

import type { AgentMessageProfile } from '../../../lib/apiClient'
import { PRODUCT_PIN_NOTICE_LIFETIME_MS, isProductPinNotice } from './autoDismissNotices'

function message(
  overrides: Partial<AgentMessageProfile> = {},
): AgentMessageProfile {
  return {
    messageId: 'message-1',
    runId: null,
    sequenceNumber: 1,
    role: 'USER_ACTION',
    contentKind: 'TEXT',
    textContent: 'Pinned product Trail Boot.',
    contentJson: null,
    correlationId: 'direct:pin_product',
    createdAt: '2026-07-19T12:00:00Z',
    ...overrides,
  }
}

describe('agent auto-dismiss notices', () => {
  test('keeps pin and unpin notices visible for five seconds', () => {
    expect(PRODUCT_PIN_NOTICE_LIFETIME_MS).toBe(5_000)
    expect(isProductPinNotice(message())).toBe(true)
    expect(isProductPinNotice(message({ correlationId: 'direct:unpin_product' }))).toBe(true)
  })

  test('does not auto-dismiss watch actions or matching user-authored text', () => {
    expect(isProductPinNotice(message({ correlationId: 'direct:watch_product' }))).toBe(false)
    expect(isProductPinNotice(message({ role: 'USER', correlationId: null }))).toBe(false)
  })
})
