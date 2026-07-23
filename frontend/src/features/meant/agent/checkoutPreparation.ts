import type { CartItem } from '../types'
import type { DiscoverChatMessage } from '../chat/types'

/** Opens the UI checkout surface for cart-API state without forging an agent tool reference. */
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
