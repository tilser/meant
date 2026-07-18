import { acknowledgeEmbeddedCheckoutOpened } from '../../../lib/apiClient'

export interface EmbeddedCheckoutOpenedAcknowledgement {
  cartId: string
  sessionId: string
  expectedUserId?: string
}

interface EmbeddedCheckoutAcknowledgementOptions {
  acknowledge?: (input: EmbeddedCheckoutOpenedAcknowledgement) => Promise<void>
  retryDelaysMs?: readonly number[]
  wait?: (delayMs: number) => Promise<void>
}

const DEFAULT_RETRY_DELAYS_MS = [250, 1_000, 2_500] as const

function wait(delayMs: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, delayMs))
}

export function embeddedCheckoutAcknowledgementRetryable(error: unknown): boolean {
  const status =
    typeof error === 'object' && error !== null && 'status' in error
      ? (error as { status?: unknown }).status
      : null
  if (typeof status === 'number') {
    return status === 408 || status === 429 || status >= 500
  }
  if (error instanceof TypeError) return true
  return false
}

export async function acknowledgeEmbeddedCheckoutOpenedWithRetry(
  input: EmbeddedCheckoutOpenedAcknowledgement,
  options: EmbeddedCheckoutAcknowledgementOptions = {},
): Promise<void> {
  const acknowledge = options.acknowledge ?? acknowledgeEmbeddedCheckoutOpened
  const retryDelaysMs = options.retryDelaysMs ?? DEFAULT_RETRY_DELAYS_MS
  const waitForRetry = options.wait ?? wait

  for (let attempt = 0; ; attempt += 1) {
    try {
      await acknowledge(input)
      return
    } catch (error) {
      const retryDelay = retryDelaysMs[attempt]
      if (retryDelay === undefined || !embeddedCheckoutAcknowledgementRetryable(error)) {
        throw error
      }
      await waitForRetry(retryDelay)
    }
  }
}
