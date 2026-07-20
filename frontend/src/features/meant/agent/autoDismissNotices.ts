import type { AgentMessageProfile } from '../../../lib/apiClient'

export const PRODUCT_PIN_NOTICE_LIFETIME_MS = 5_000

const PRODUCT_PIN_ACTIONS = new Set(['pin_product', 'unpin_product'])
const PRODUCT_PIN_NOTICE_TEXT = /^(?:Pinned|Unpinned) product\b/i

export function isProductPinNotice(message: AgentMessageProfile): boolean {
  if (message.role !== 'USER_ACTION' || message.contentKind !== 'ACTION') return false

  const toolName = message.correlationId?.split(':').at(-1)
  return (
    Boolean(toolName && PRODUCT_PIN_ACTIONS.has(toolName)) ||
    PRODUCT_PIN_NOTICE_TEXT.test(message.textContent ?? '')
  )
}
