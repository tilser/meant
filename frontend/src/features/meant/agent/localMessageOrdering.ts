import type { DiscoverChatMessage } from '../chat/types'

export interface AnchoredLocalMessage {
  message: DiscoverChatMessage
  /** Messages that were already visible when this local message was created. */
  precedingMessageIds: readonly string[]
}

/** Restores the client-owned live cart card without duplicating it during the current mount. */
export function withPersistedCartMessage(
  localMessages: readonly AnchoredLocalMessage[],
  persistedCartMessage: AnchoredLocalMessage | undefined,
  cartHasItems: boolean,
): AnchoredLocalMessage[] {
  if (
    !cartHasItems ||
    !persistedCartMessage ||
    localMessages.some(({ message }) => message.id === persistedCartMessage.message.id)
  ) {
    return [...localMessages]
  }
  return [...localMessages, persistedCartMessage]
}

/**
 * Keeps client-only messages at their original point in the transcript when newer durable
 * messages arrive from the agent ledger.
 */
export function mergeAnchoredLocalMessages(
  authoritative: readonly DiscoverChatMessage[],
  localMessages: readonly AnchoredLocalMessage[],
): DiscoverChatMessage[] {
  const merged = [...authoritative]

  for (const local of localMessages) {
    const precedingIds = new Set(local.precedingMessageIds)
    let insertionIndex = 0

    for (let index = merged.length - 1; index >= 0; index -= 1) {
      if (precedingIds.has(merged[index].id)) {
        insertionIndex = index + 1
        break
      }
    }

    merged.splice(insertionIndex, 0, local.message)
  }

  return merged
}
