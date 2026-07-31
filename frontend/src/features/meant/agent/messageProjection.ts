import type { DiscoverChatBlock, DiscoverChatMessage } from '../chat/types'
import { agentMarkdownText } from './agentText'
import type { AgentRunProjection } from './eventReducer'

function isProductResult(block: DiscoverChatBlock): boolean {
  return block.type === 'similar' || block.type === 'products'
}

function isUnhostedProductResult(message: DiscoverChatMessage): boolean {
  const blocks = message.blocks ?? []
  return blocks.some(isProductResult) && !blocks.some((block) => block.type === 'text')
}

/**
 * Adds live assistant events to the durable transcript while it is catching up. Product artifacts
 * can arrive before the assistant event; until then they remain visibly unfinished, then move under
 * the live assistant copy just as they will be hosted by the durable assistant ledger message.
 */
export function withProjectedAgentMessages(
  authoritative: readonly DiscoverChatMessage[],
  knownMessageIds: ReadonlySet<string>,
  activeRunMessageIds: ReadonlySet<string>,
  activeRunId: string,
  projection: AgentRunProjection | undefined,
): DiscoverChatMessage[] {
  if (!projection) return [...authoritative]

  const unhostedResultMessages = authoritative.filter(
    (message) => activeRunMessageIds.has(message.id) && isUnhostedProductResult(message),
  )
  const unhostedResultIds = new Set(unhostedResultMessages.map((message) => message.id))
  const productResultBlocks = unhostedResultMessages.flatMap((message) =>
    (message.blocks ?? []).filter(isProductResult),
  )
  const transient: DiscoverChatMessage[] = projection.assistantMessages.flatMap((message, index) =>
    message.messageId && knownMessageIds.has(message.messageId)
      ? []
      : [
          {
            id: message.messageId ?? `${activeRunId}:assistant:${index}`,
            role: 'ai' as const,
            blocks: [{ type: 'text' as const, text: agentMarkdownText(message.text) }],
          },
        ],
  )
  const streamingText = projection.streamingAssistantText.trim()
  const hasLiveAssistant = transient.length > 0 || Boolean(streamingText)
  let displayedAuthoritative: DiscoverChatMessage[]
  if (hasLiveAssistant && productResultBlocks.length > 0) {
    displayedAuthoritative = authoritative.flatMap((message) => {
      if (!unhostedResultIds.has(message.id)) return [message]
      const remainingBlocks = (message.blocks ?? []).filter((block) => !isProductResult(block))
      return remainingBlocks.length > 0 ? [{ ...message, blocks: remainingBlocks }] : []
    })
  } else {
    displayedAuthoritative = authoritative.map((message) =>
      unhostedResultIds.has(message.id)
        ? {
            ...message,
            pending: true,
            pendingText: 'Finishing…',
            settling: true,
          }
        : message,
    )
  }

  if (streamingText) {
    transient.push({
      id: `${activeRunId}:streaming`,
      role: 'ai',
      blocks: [
        { type: 'text', text: agentMarkdownText(projection.streamingAssistantText) },
        ...productResultBlocks,
      ],
      pending: true,
      pendingText: productResultBlocks.length > 0 ? 'Finishing…' : 'Meant is still working…',
      settling: productResultBlocks.length > 0,
    })
  } else if (productResultBlocks.length > 0 && transient.length > 0) {
    const hostIndex = transient.length - 1
    const host = transient[hostIndex]!
    transient[hostIndex] = {
      ...host,
      blocks: [...(host.blocks ?? []), ...productResultBlocks],
    }
  }
  return [...displayedAuthoritative, ...transient]
}
