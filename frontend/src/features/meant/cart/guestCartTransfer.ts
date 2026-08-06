import { accountSessionStorageKey } from '../shared/accountStorage'
import type { CartItem } from '../types'
import { cartMerchantKey } from '../utils'
import type { MerchantCartSnapshot } from './types'

export interface GuestCartTransferState {
  cart: CartItem[]
  snapshots: Record<string, MerchantCartSnapshot>
}

function readJson(key: string): unknown {
  try {
    return JSON.parse(window.sessionStorage.getItem(key) ?? 'null')
  } catch {
    return null
  }
}

/** Consumes browser-only cart presentation state after server ownership transfer succeeds. */
export function consumeGuestCartTransferState(sourceOwnerId: string): GuestCartTransferState {
  const cartKey = accountSessionStorageKey('meant.cart', sourceOwnerId)
  const snapshotsKey = accountSessionStorageKey('meant.cartSnapshots', sourceOwnerId)
  const rawCart = readJson(cartKey)
  const rawSnapshots = readJson(snapshotsKey)
  try {
    window.sessionStorage.removeItem(cartKey)
    window.sessionStorage.removeItem(snapshotsKey)
  } catch {
    // Server ownership is authoritative even when browser storage is unavailable.
  }
  return {
    cart: Array.isArray(rawCart) ? (rawCart as CartItem[]) : [],
    snapshots:
      rawSnapshots && typeof rawSnapshots === 'object' && !Array.isArray(rawSnapshots)
        ? (rawSnapshots as Record<string, MerchantCartSnapshot>)
        : {},
  }
}

/** The just-claimed guest cart wins only for merchant partitions it actually contains. */
export function mergeTransferredGuestCart(
  current: readonly CartItem[],
  transferred: readonly CartItem[],
): CartItem[] {
  if (transferred.length === 0) return [...current]
  const transferredMerchantKeys = new Set(transferred.map(cartMerchantKey))
  return [
    ...current.filter((item) => !transferredMerchantKeys.has(cartMerchantKey(item))),
    ...transferred,
  ]
}

export function mergeTransferredGuestCartSnapshots(
  current: Readonly<Record<string, MerchantCartSnapshot>>,
  transferred: Readonly<Record<string, MerchantCartSnapshot>>,
): Record<string, MerchantCartSnapshot> {
  return { ...current, ...transferred }
}
