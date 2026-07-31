import type { DiscoverChatMessage } from '../chat/types'
import { agentMarkdownText } from './artifactMapping'
import type { AgentRunProjection } from './eventReducer'

function hasCatalogResult(message: DiscoverChatMessage): boolean {
  return Boolean(
    message.blocks?.some(
      (block) => block.type === 'similar' || (block.type === 'products' && Boolean(block.query)),
    ),
  )
}

/**
 * Adds live assistant events to the durable transcript while it is catching up. Product cards
 * only suppress a duplicate assistant summary when those cards belong to this same run. A
 * clarification is never suppressed: it must be visible as soon as the run starts waiting.
 */
export function withProjectedAgentMessages(
  authoritative: readonly DiscoverChatMessage[],
  knownMessageIds: ReadonlySet<string>,
  activeRunMessageIds: ReadonlySet<string>,
  activeRunId: string,
  projection: AgentRunProjection | undefined,
): DiscoverChatMessage[] {
  if (!projection) return [...authoritative]

  const currentRunHasRenderedCatalogResult = authoritative.some(
    (message) => activeRunMessageIds.has(message.id) && hasCatalogResult(message),
  )
  const suppressCompletedAssistant =
    currentRunHasRenderedCatalogResult && projection.status !== 'WAITING_FOR_USER'
  const transient: DiscoverChatMessage[] = projection.assistantMessages.flatMap((message, index) =>
    suppressCompletedAssistant || (message.messageId && knownMessageIds.has(message.messageId))
      ? []
      : [
          {
            id: message.messageId ?? `${activeRunId}:assistant:${index}`,
            role: 'ai' as const,
            blocks: [{ type: 'text' as const, text: agentMarkdownText(message.text) }],
          },
        ],
  )
  if (projection.streamingAssistantText && !currentRunHasRenderedCatalogResult) {
    transient.push({
      id: `${activeRunId}:streaming`,
      role: 'ai',
      blocks: [{ type: 'text', text: agentMarkdownText(projection.streamingAssistantText) }],
      pending: true,
      pendingText: 'Meant is still working…',
    })
  }
  return [...authoritative, ...transient]
}
