import { describe, expect, test } from 'bun:test'

import type { AgentMessageProfile } from '../../../lib/apiClient'
import {
  EMPTY_CART_MESSAGE_LIFETIME_MS,
  PRODUCT_PIN_NOTICE_LIFETIME_MS,
  isProductPinNotice,
  shouldAutoDismissEmptyCartMessage,
} from './autoDismissNotices'

function message(overrides: Partial<AgentMessageProfile> = {}): AgentMessageProfile {
  return {
    messageId: 'message-1',
    runId: null,
    sequenceNumber: 1,
    role: 'USER_ACTION',
    contentKind: 'ACTION',
    textContent: 'Pinned product Trail Boot.',
    contentJson: null,
    correlationId: 'action:550e8400-e29b-41d4-a716-446655440000',
    createdAt: '2026-07-19T12:00:00Z',
    ...overrides,
  }
}

describe('agent auto-dismiss notices', () => {
  test('recognizes persisted pin and unpin actions for three-second dismissal', () => {
    expect(PRODUCT_PIN_NOTICE_LIFETIME_MS).toBe(3_000)
    expect(isProductPinNotice(message())).toBe(true)
    expect(isProductPinNotice(message({ textContent: 'Unpinned product Trail Boot.' }))).toBe(true)
  })

  test('recognizes an explicit product pin tool correlation', () => {
    expect(
      isProductPinNotice(
        message({
          textContent: 'Action completed.',
          correlationId: 'action:550e8400-e29b-41d4-a716-446655440000:pin_product',
        }),
      ),
    ).toBe(true)
    expect(
      isProductPinNotice(
        message({
          textContent: 'Action completed.',
          correlationId: 'action:550e8400-e29b-41d4-a716-446655440000:unpin_product',
        }),
      ),
    ).toBe(true)
  })

  test('does not auto-dismiss other actions or matching non-action messages', () => {
    expect(
      isProductPinNotice(
        message({
          textContent: 'Watching product Trail Boot.',
          correlationId: 'action:550e8400-e29b-41d4-a716-446655440000:watch_product',
        }),
      ),
    ).toBe(false)
    expect(isProductPinNotice(message({ role: 'USER', correlationId: null }))).toBe(false)
    expect(isProductPinNotice(message({ contentKind: 'TEXT', correlationId: null }))).toBe(false)
  })

  test('auto-dismisses an empty visible cart after five seconds', () => {
    expect(EMPTY_CART_MESSAGE_LIFETIME_MS).toBe(5_000)
    expect(shouldAutoDismissEmptyCartMessage('cart-message', 0, false)).toBe(true)
  })

  test('keeps the cart message while it has lines or an addition is pending', () => {
    expect(shouldAutoDismissEmptyCartMessage('cart-message', 1, false)).toBe(false)
    expect(shouldAutoDismissEmptyCartMessage('cart-message', 0, true)).toBe(false)
    expect(shouldAutoDismissEmptyCartMessage(null, 0, false)).toBe(false)
  })
})
