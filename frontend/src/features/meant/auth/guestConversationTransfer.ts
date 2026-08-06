export const GUEST_CONVERSATION_TRANSFER_KEY = 'meant.guestConversationTransfer.v1'

export interface StoredGuestConversationTransfer {
  token: string
  conversationId: string
  expiresAt: string
  guestUserId?: string
}

export function storeGuestConversationTransfer(transfer: StoredGuestConversationTransfer): void {
  window.sessionStorage.setItem(GUEST_CONVERSATION_TRANSFER_KEY, JSON.stringify(transfer))
}

export function readGuestConversationTransfer(): StoredGuestConversationTransfer | null {
  if (typeof window === 'undefined') return null
  try {
    const value = JSON.parse(
      window.sessionStorage.getItem(GUEST_CONVERSATION_TRANSFER_KEY) ?? 'null',
    )
    if (
      !value ||
      typeof value.token !== 'string' ||
      typeof value.conversationId !== 'string' ||
      typeof value.expiresAt !== 'string' ||
      (value.guestUserId !== undefined && typeof value.guestUserId !== 'string')
    ) {
      return null
    }
    return value as StoredGuestConversationTransfer
  } catch {
    return null
  }
}

export function clearGuestConversationTransfer(token?: string): void {
  const stored = readGuestConversationTransfer()
  if (!token || stored?.token === token) {
    window.sessionStorage.removeItem(GUEST_CONVERSATION_TRANSFER_KEY)
  }
}
