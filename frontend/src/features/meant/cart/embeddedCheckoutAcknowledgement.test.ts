import { describe, expect, test } from 'bun:test'

import {
  acknowledgeEmbeddedCheckoutOpenedWithRetry,
  embeddedCheckoutAcknowledgementRetryable,
  type EmbeddedCheckoutOpenedAcknowledgement,
} from './embeddedCheckoutAcknowledgement'

const acknowledgement: EmbeddedCheckoutOpenedAcknowledgement = {
  cartId: 'cart-1',
  sessionId: 'session-1',
  expectedUserId: 'user-1',
}

function httpError(
  status: number,
  code: string | null = null,
): Error & {
  status: number
  code: string | null
} {
  return Object.assign(new Error(`HTTP ${status}`), { status, code })
}

describe('embedded checkout start acknowledgement', () => {
  test('retries transport and retryable HTTP failures with the same semantic session', async () => {
    const calls: EmbeddedCheckoutOpenedAcknowledgement[] = []
    const delays: number[] = []
    const failures = [new TypeError('network unavailable'), httpError(408), httpError(503)]

    await acknowledgeEmbeddedCheckoutOpenedWithRetry(acknowledgement, {
      acknowledge: async (input) => {
        calls.push({ ...input })
        const failure = failures.shift()
        if (failure) throw failure
      },
      retryDelaysMs: [10, 20, 30],
      wait: async (delayMs) => {
        delays.push(delayMs)
      },
    })

    expect(calls).toEqual([acknowledgement, acknowledgement, acknowledgement, acknowledgement])
    expect(delays).toEqual([10, 20, 30])
  })

  test('fails closed without retrying stale or unauthorized acknowledgements', async () => {
    let calls = 0
    const staleError = httpError(409, 'stale_checkout_attempt')

    await expect(
      acknowledgeEmbeddedCheckoutOpenedWithRetry(acknowledgement, {
        acknowledge: async () => {
          calls += 1
          throw staleError
        },
        retryDelaysMs: [0, 0],
        wait: async () => undefined,
      }),
    ).rejects.toBe(staleError)

    expect(calls).toBe(1)
  })

  test('classifies throttling and server errors as retryable', () => {
    expect(embeddedCheckoutAcknowledgementRetryable(httpError(429))).toBe(true)
    expect(embeddedCheckoutAcknowledgementRetryable(httpError(500))).toBe(true)
    expect(embeddedCheckoutAcknowledgementRetryable(httpError(403))).toBe(false)
  })
})
