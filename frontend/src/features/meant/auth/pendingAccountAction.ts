import type { View } from '../types'

export type PendingAccountAction =
  | { type: 'SAVE_PRODUCT'; productId: string }
  | { type: 'REMEMBER_PREFERENCES'; preferenceDraftId: string }
  | { type: 'START_NEW_CONVERSATION'; initialQuery?: string }
  | { type: 'OPEN_SAVED' }
  | { type: 'OPEN_HISTORY' }
  | { type: 'OPEN_INVENTORY' }
  | { type: 'OPEN_ORDERS' }
  | { type: 'OPEN_CART' }
  | { type: 'OPEN_PREFERENCES' }
  | { type: 'OPEN_ACCOUNT' }
  | { type: 'ADD_TO_CART'; productId: string; offerKey: string }
  | { type: 'ENABLE_ALERT'; productId: string }

interface StoredPendingAccountAction {
  id: string
  action: PendingAccountAction
  state: 'pending' | 'claimed'
  createdAt: number
}

export const PENDING_ACCOUNT_ACTION_KEY = 'meant.pendingAccountAction.v1'

export interface PendingAccountNavigation {
  view: View
  home?: boolean
}

/** An imported guest chat is the post-login destination; deferred actions may still run in place. */
export function pendingAccountNavigation(
  action: PendingAccountAction,
  preserveImportedConversation: boolean,
): PendingAccountNavigation | null {
  if (preserveImportedConversation) return null
  switch (action.type) {
    case 'OPEN_SAVED':
      return { view: 'saved' }
    case 'OPEN_INVENTORY':
      return { view: 'inventory' }
    case 'OPEN_ORDERS':
      return { view: 'orders' }
    case 'OPEN_CART':
      return { view: 'cart' }
    case 'OPEN_PREFERENCES':
    case 'REMEMBER_PREFERENCES':
      return { view: 'preferences' }
    case 'OPEN_ACCOUNT':
      return { view: 'account' }
    case 'START_NEW_CONVERSATION':
      return { view: 'discover', home: true }
    case 'OPEN_HISTORY':
      return { view: 'discover' }
    default:
      return null
  }
}

function readStored(): StoredPendingAccountAction | null {
  if (typeof window === 'undefined') return null
  try {
    const value = JSON.parse(window.sessionStorage.getItem(PENDING_ACCOUNT_ACTION_KEY) ?? 'null')
    if (!value || typeof value.id !== 'string' || typeof value.action?.type !== 'string')
      return null
    return value as StoredPendingAccountAction
  } catch {
    return null
  }
}

export function storePendingAccountAction(action: PendingAccountAction): string {
  const id = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
  const stored: StoredPendingAccountAction = { id, action, state: 'pending', createdAt: Date.now() }
  window.sessionStorage.setItem(PENDING_ACCOUNT_ACTION_KEY, JSON.stringify(stored))
  return id
}

/** Claims an action before execution, preventing auth callback and listener replays from running it twice. */
export function claimPendingAccountAction(): StoredPendingAccountAction | null {
  const stored = readStored()
  if (!stored || stored.state !== 'pending') return null
  const claimed = { ...stored, state: 'claimed' as const }
  window.sessionStorage.setItem(PENDING_ACCOUNT_ACTION_KEY, JSON.stringify(claimed))
  return claimed
}

export function completePendingAccountAction(id: string): void {
  const stored = readStored()
  if (stored?.id === id) window.sessionStorage.removeItem(PENDING_ACCOUNT_ACTION_KEY)
}

export function retryPendingAccountAction(id: string): void {
  const stored = readStored()
  if (stored?.id === id) {
    window.sessionStorage.setItem(
      PENDING_ACCOUNT_ACTION_KEY,
      JSON.stringify({ ...stored, state: 'pending' }),
    )
  }
}

export function peekPendingAccountAction(): PendingAccountAction | null {
  return readStored()?.action ?? null
}
