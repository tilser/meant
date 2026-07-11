import type { CartProfile } from '../../../lib/apiClient'
import type { CartItem, ProductId } from '../types'
import { cartMerchantKey, mergeCartSnapshot } from '../utils'

export function confirmedCartIdForOffer(
  cart: readonly CartItem[],
  offerKey: string,
): string | undefined {
  const exactOfferKey = offerKey.trim()
  if (!exactOfferKey) return undefined

  return (
    cart.find(
      (item) =>
        item.offerKey === exactOfferKey &&
        Boolean(item.cartId) &&
        Boolean(item.cartLineId || item.remoteCartLineId) &&
        item.syncing !== true &&
        !item.syncError,
    )?.cartId ?? undefined
  )
}

export function mergeInitialSelectedOfferSnapshot(
  cart: readonly CartItem[],
  productId: ProductId,
  offerKey: string,
  snapshot: CartProfile,
): CartItem[] {
  return cart.map((item) => {
    if (item.id !== productId || item.offerKey !== offerKey) return item
    return mergeCartSnapshot([item], cartMerchantKey(item), snapshot)[0] ?? item
  })
}

export function mergeConfirmedCartSnapshot(
  cart: readonly CartItem[],
  confirmedCartId: string,
  snapshot: CartProfile,
): CartItem[] {
  return cart.map((item) => {
    if (item.cartId !== confirmedCartId) return item
    return mergeCartSnapshot([item], cartMerchantKey(item), snapshot)[0] ?? item
  })
}
