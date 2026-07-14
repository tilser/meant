import type { CartProfile } from '../../../lib/apiClient'
import type { CartItem, ProductId } from '../types'
import { cartMerchantKey, isCartNotFoundError, mergeCartSnapshot } from '../utils'

type MerchantIdentity = {
  merchantScopeKey?: string | null
  routingScopeKey?: string | null
  merchantIntegrationId?: string | null
  externalMerchantId?: string | null
  merchantDomain?: string | null
  provider?: string | null
}

function normalized(value: string | null | undefined): string | null {
  return value?.trim() || null
}

function normalizedProvider(value: string | null | undefined): string | null {
  return value?.trim().toLocaleLowerCase() || null
}

function providerScopedIdentityMatches(
  storedProvider: string | null,
  requestedProvider: string | null,
): boolean {
  return requestedProvider ? storedProvider === requestedProvider : storedProvider === null
}

function merchantIdentityMatches(item: CartItem, requested: MerchantIdentity): boolean {
  const storedMerchantScopeKey = normalized(item.merchantScopeKey)
  const requestedMerchantScopeKey = normalized(requested.merchantScopeKey)
  if (storedMerchantScopeKey && requestedMerchantScopeKey) {
    return storedMerchantScopeKey === requestedMerchantScopeKey
  }

  const storedRoutingScopeKey = normalized(item.routingScopeKey)
  const requestedRoutingScopeKey = normalized(requested.routingScopeKey)
  if (storedRoutingScopeKey && requestedRoutingScopeKey) {
    return storedRoutingScopeKey === requestedRoutingScopeKey
  }

  const storedMerchantIntegrationId = normalized(item.merchantIntegrationId)
  const requestedMerchantIntegrationId = normalized(requested.merchantIntegrationId)
  if (storedMerchantIntegrationId && requestedMerchantIntegrationId) {
    return storedMerchantIntegrationId === requestedMerchantIntegrationId
  }

  const storedProvider = normalizedProvider(item.provider)
  const requestedProvider = normalizedProvider(requested.provider)
  const storedExternalMerchantId = normalized(item.externalMerchantId)
  const requestedExternalMerchantId = normalized(requested.externalMerchantId)
  if (storedExternalMerchantId && requestedExternalMerchantId) {
    return (
      storedExternalMerchantId === requestedExternalMerchantId &&
      providerScopedIdentityMatches(storedProvider, requestedProvider)
    )
  }

  const storedMerchantDomain = normalized(item.merchantDomain)?.toLocaleLowerCase()
  const requestedMerchantDomain = normalized(requested.merchantDomain)?.toLocaleLowerCase()
  if (storedMerchantDomain && requestedMerchantDomain) {
    return (
      storedMerchantDomain === requestedMerchantDomain &&
      providerScopedIdentityMatches(storedProvider, requestedProvider)
    )
  }

  return false
}

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

export function confirmedCartIdForMerchant(
  cart: readonly CartItem[],
  identity: MerchantIdentity,
): string | undefined {
  const merchantScopeKey = normalized(identity.merchantScopeKey)
  const routingScopeKey = normalized(identity.routingScopeKey)
  const merchantIntegrationId = normalized(identity.merchantIntegrationId)
  const externalMerchantId = normalized(identity.externalMerchantId)
  const merchantDomain = normalized(identity.merchantDomain)
  if (
    !merchantScopeKey &&
    !routingScopeKey &&
    !merchantIntegrationId &&
    !externalMerchantId &&
    !merchantDomain
  ) {
    return undefined
  }

  return (
    cart.find((item) => {
      return Boolean(
        merchantIdentityMatches(item, identity) &&
        item.cartId &&
        (item.cartLineId || item.remoteCartLineId) &&
        item.syncing !== true &&
        !item.syncError,
      )
    })?.cartId ?? undefined
  )
}

export async function bindSelectedOfferWithStaleCartRecovery(
  confirmedCartId: string | undefined,
  bind: () => Promise<CartProfile>,
  rebuild: () => Promise<CartProfile>,
  isStaleCart: (error: unknown) => boolean = isCartNotFoundError,
): Promise<{ snapshot: CartProfile; rebuilt: boolean }> {
  try {
    return { snapshot: await bind(), rebuilt: false }
  } catch (error) {
    if (!confirmedCartId || !isStaleCart(error)) {
      throw error
    }
    return { snapshot: await rebuild(), rebuilt: true }
  }
}

export function cartSnapshotExactOfferLine(
  snapshot: CartProfile,
  offerKey: string,
): CartProfile['lines'][number] | undefined {
  const exactOfferKey = offerKey.trim()
  if (!exactOfferKey) return undefined

  return snapshot.lines?.find(
    (line) =>
      line.offerKey?.trim() === exactOfferKey &&
      Boolean(line.cartLineId?.trim() || line.remoteCartLineId?.trim()) &&
      Number.isInteger(line.quantity) &&
      line.quantity > 0,
  )
}

export function cartSnapshotHasExactOfferLine(
  snapshot: CartProfile,
  offerKey: string,
  expectedQuantity: number,
): boolean {
  if (!Number.isInteger(expectedQuantity) || expectedQuantity <= 0) return false

  return (cartSnapshotExactOfferLine(snapshot, offerKey)?.quantity ?? 0) >= expectedQuantity
}

export function settleUnconfirmedSelectedOfferAddition(
  cart: readonly CartItem[],
  productId: ProductId,
  offerKey: string,
  returnedExactLine: boolean,
): CartItem[] {
  return cart.flatMap((item) => {
    if (item.id !== productId || item.offerKey !== offerKey) {
      return [item]
    }
    if (returnedExactLine) {
      // Reconciliation has already applied the merchant's authoritative pre-add quantity. Keep
      // its live cart handle; the false action result reports the rejected increment to the UI.
      return [{ ...item, syncing: false, syncError: null }]
    }

    const qty = item.qty - 1
    return qty <= 0
      ? []
      : [{ ...item, qty, syncing: false, syncError: 'Could not add this exact offer.' }]
  })
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
