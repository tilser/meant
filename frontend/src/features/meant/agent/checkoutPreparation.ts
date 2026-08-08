import type { DiscoverChatBlock } from '../chat/types'
import type { CartItem } from '../types'

/** Prepares the inline checkout surface for cart-API state without creating another chat message. */
export function checkoutInChatBlock(
  cart: readonly CartItem[],
): Extract<DiscoverChatBlock, { type: 'checkout' }> | null {
  const merchantCount = new Set(
    cart.flatMap((item) => (item.cartId?.trim() ? [item.cartId.trim()] : [])),
  ).size
  if (merchantCount === 0) return null

  return {
    type: 'checkout',
    merchantCount,
  }
}
