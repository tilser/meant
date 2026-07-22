import type { CartItem } from '../types'

const MAX_CHECKOUT_CARTS = 10

export type CheckoutPreparationOutcome = 'missing-cart' | 'submitted'

type ExecuteCheckoutAction = (
  toolName: 'prepare_checkout',
  argumentsValue: { cartIds: string[] },
  summary: string,
) => Promise<unknown>

/** Prepares checkout from the authoritative live cart without adding a redundant cart-read action. */
export async function prepareCheckoutFromCurrentCart(
  cart: readonly CartItem[],
  executeAction: ExecuteCheckoutAction,
): Promise<CheckoutPreparationOutcome> {
  const cartIds = [
    ...new Set(cart.flatMap((item) => (item.cartId?.trim() ? [item.cartId.trim()] : []))),
  ].slice(0, MAX_CHECKOUT_CARTS)
  if (cartIds.length === 0) return 'missing-cart'

  await executeAction('prepare_checkout', { cartIds }, 'Prepared checkout')
  return 'submitted'
}
