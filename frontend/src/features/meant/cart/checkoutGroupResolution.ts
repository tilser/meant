import type { CartGroup } from '../utils'
import { cartItemsShareMerchantPartition } from './cartPartition'

function checkoutLineIdentity(item: CartGroup['items'][number]): string | null {
  const offerKey = item.offerKey?.trim()
  if (offerKey) return `offer:${offerKey}:qty:${item.qty}`
  const variantId = item.productVariantId?.trim()
  return variantId ? `variant:${variantId}:qty:${item.qty}` : null
}

function hasOneCompleteCart(group: CartGroup): boolean {
  const cartIds = group.items.map((item) => item.cartId?.trim()).filter(Boolean)
  return cartIds.length === group.items.length && new Set(cartIds).size === 1
}

function hasSameCheckoutLines(displayGroup: CartGroup, actionGroup: CartGroup): boolean {
  const displayLines = displayGroup.items.map(checkoutLineIdentity)
  const actionLines = actionGroup.items.map(checkoutLineIdentity)
  return (
    displayLines.every((identity): identity is string => Boolean(identity)) &&
    actionLines.every((identity): identity is string => Boolean(identity)) &&
    displayLines.sort().join('\u0000') === actionLines.sort().join('\u0000')
  )
}

/** Resolves immutable display rows to exactly one current merchant cart. */
export function liveCheckoutGroupFor(
  displayGroup: CartGroup,
  actionGroups: readonly CartGroup[],
): CartGroup | null {
  const eligibleGroups = actionGroups.filter(
    (group) => hasOneCompleteCart(group) && hasSameCheckoutLines(displayGroup, group),
  )
  const displayCartIds = new Set(
    displayGroup.items.flatMap((item) => (item.cartId ? [item.cartId] : [])),
  )
  if (displayCartIds.size > 0) {
    const exact = eligibleGroups.filter((group) =>
      group.items.some((item) => Boolean(item.cartId && displayCartIds.has(item.cartId))),
    )
    if (exact.length === 1) return exact[0]!
    if (exact.length > 1) return null
  }
  const matchingPartitions = eligibleGroups.filter((group) =>
    group.items.some((actionItem) =>
      displayGroup.items.some((displayItem) =>
        cartItemsShareMerchantPartition(actionItem, displayItem),
      ),
    ),
  )
  return matchingPartitions.length === 1 ? matchingPartitions[0]! : null
}
