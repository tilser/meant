import type { DiscoverChatMessage } from '../chat/types'
import type { CartItem } from '../types'

/** Opens the UI checkout surface for cart-API state without requiring a live agent resource. */
export function checkoutInChatMessage(
  messageId: string,
  cart: readonly CartItem[],
): DiscoverChatMessage | null {
  const merchantCount = new Set(
    cart.flatMap((item) => (item.cartId?.trim() ? [item.cartId.trim()] : [])),
  ).size
  if (merchantCount === 0) return null

  return {
    id: messageId,
    role: 'ai',
    blocks: [
      {
        type: 'text',
        text: 'I grouped checkout by merchant and kept it inside the chat.',
      },
      { type: 'checkout', merchantCount },
    ],
  }
}
