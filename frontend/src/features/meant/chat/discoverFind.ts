import type { DiscoverChatMessage, DiscoverFindRequest } from './types'
import { productsInDiscoverMessage } from './utils'

export type DiscoverFindResolution =
  | { kind: 'switch-conversation'; conversationId: string }
  | { kind: 'found'; messageId: string }
  | { kind: 'pending' }

export function shouldAutoScrollChatToBottom(
  request: DiscoverFindRequest | null,
  handledRequestId: string | null,
): boolean {
  return request === null || request.id === handledRequestId
}

export function resolveDiscoverFind(
  request: DiscoverFindRequest,
  activeConversationId: string | null,
  messages: readonly DiscoverChatMessage[],
): DiscoverFindResolution {
  if (request.conversationId && request.conversationId !== activeConversationId) {
    return { kind: 'switch-conversation', conversationId: request.conversationId }
  }

  const exactMessageId = request.messageId
  const exactMessage = exactMessageId
    ? messages.find((message) => message.id === exactMessageId)
    : undefined
  if (exactMessage) {
    return { kind: 'found', messageId: exactMessage.id }
  }

  if (request.kind === 'product') {
    const productMessage = messages.find((message) =>
      productsInDiscoverMessage(message).some((product) => product.id === request.productId),
    )
    if (productMessage) {
      return { kind: 'found', messageId: productMessage.id }
    }
  }

  return { kind: 'pending' }
}
