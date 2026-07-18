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
