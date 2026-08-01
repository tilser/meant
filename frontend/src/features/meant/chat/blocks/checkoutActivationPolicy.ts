export function merchantCheckoutStartBlocked({
  groupReady,
  payingMerchant,
  activeCartId,
  releasedCartIds,
  targetCartId,
}: Readonly<{
  groupReady: boolean
  payingMerchant: string | null
  activeCartId: string | null
  releasedCartIds: ReadonlySet<string>
  targetCartId: string | null
}>): boolean {
  if (!groupReady || payingMerchant !== null) {
    return true
  }
  if (!activeCartId || releasedCartIds.has(activeCartId)) {
    return false
  }
  return activeCartId !== targetCartId
}

export function shouldAutoStartMerchantCheckout({
  enabled,
  merchantCount,
  groupReady,
  checkoutBusy,
  payingMerchant,
  activeCartId,
  targetCartId,
}: Readonly<{
  enabled: boolean
  merchantCount: number
  groupReady: boolean
  checkoutBusy: boolean
  payingMerchant: string | null
  activeCartId: string | null
  targetCartId: string | null
}>): boolean {
  return (
    enabled &&
    merchantCount === 1 &&
    groupReady &&
    !checkoutBusy &&
    payingMerchant === null &&
    activeCartId === null &&
    targetCartId !== null
  )
}
