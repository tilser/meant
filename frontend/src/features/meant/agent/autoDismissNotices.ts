import type { AgentMessageProfile } from '../../../lib/apiClient'

export const PRODUCT_PIN_NOTICE_LIFETIME_MS = 5_000

const PRODUCT_PIN_ACTIONS = new Set(['pin_product', 'unpin_product'])

export function isProductPinNotice(message: AgentMessageProfile): boolean {
  const toolName = message.correlationId?.split(':').at(-1)
  return message.role === 'USER_ACTION' && Boolean(toolName && PRODUCT_PIN_ACTIONS.has(toolName))
}
