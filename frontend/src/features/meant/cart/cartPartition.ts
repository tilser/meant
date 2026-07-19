import type { CartItem } from '../types'
import { cartItemIdentity, cartMerchantKey } from '../utils'

function normalizedIdentity(value: string | null | undefined): string {
  return value?.trim().toLowerCase() ?? ''
}

/**
 * Compares merchant partitions from their strongest shared identity first.
 * When both sides carry a strong identity, a mismatch is decisive so two
 * integrations on the same storefront domain can never collapse together.
 */
export function cartItemsShareMerchantPartition(left: CartItem, right: CartItem): boolean {
  if (left.cartId && right.cartId && left.cartId === right.cartId) return true

  if (left.routingScopeKey && right.routingScopeKey) {
    return left.routingScopeKey === right.routingScopeKey
  }
  if (left.merchantIntegrationId && right.merchantIntegrationId) {
    return left.merchantIntegrationId === right.merchantIntegrationId
  }
  if (left.merchantId && right.merchantId) return left.merchantId === right.merchantId

  const leftProvider = normalizedIdentity(left.provider)
  const rightProvider = normalizedIdentity(right.provider)
  if (leftProvider && rightProvider && leftProvider !== rightProvider) return false
  if (left.externalMerchantId && right.externalMerchantId) {
    return left.externalMerchantId === right.externalMerchantId
  }
  if (left.routingScopeKey && left.routingScopeKey === right.merchantScopeKey) return true
  if (right.routingScopeKey && right.routingScopeKey === left.merchantScopeKey) return true
  if (
    !left.routingScopeKey &&
    !right.routingScopeKey &&
    left.merchantScopeKey &&
    right.merchantScopeKey
  ) {
    return left.merchantScopeKey === right.merchantScopeKey
  }
  const leftDomain = normalizedIdentity(left.merchantDomain)
  const rightDomain = normalizedIdentity(right.merchantDomain)
  if (leftDomain && rightDomain) return leftDomain === rightDomain
  return cartMerchantKey(left) === cartMerchantKey(right)
}

/**
 * Resolves an immutable historical row against the current server-owned cart.
 * Exact line identity wins; a recreated cart may fall back only to one exact
 * offer in the same merchant partition.
 */
export function resolveLiveCartItem(
  cart: readonly CartItem[],
  source: CartItem,
  historicalIdentity?: string,
): CartItem | null {
  if (historicalIdentity) {
    const exact = cart.find((item) => cartItemIdentity(item) === historicalIdentity)
    if (exact) return exact
  }
  const offerKey = source.offerKey?.trim()
  if (!offerKey) return null
  const candidates = cart.filter(
    (item) => item.offerKey?.trim() === offerKey && cartItemsShareMerchantPartition(item, source),
  )
  return candidates.length === 1 ? candidates[0]! : null
}
