import type { AgentConversationSummaryProfile } from '../../../lib/apiClient'
import type { DiscoverChatMessage, DiscoverChatThread } from '../chat/types'
import { sanitizeBuyerVisibleText } from './buyerVisibleText'

export function threadFromAgentConversationSummary(
  summary: AgentConversationSummaryProfile,
  messages?: readonly DiscoverChatMessage[],
): DiscoverChatThread {
  const loadedMessages = messages ?? []
  return {
    id: summary.conversationId,
    title: sanitizeBuyerVisibleText(summary.title),
    messages: loadedMessages,
    messageCount: messages === undefined ? summary.latestSequence : loadedMessages.length,
    archived: summary.status === 'ARCHIVED',
    createdAt: Date.parse(summary.createdAt),
    updatedAt: Date.parse(summary.updatedAt),
    named: summary.title !== 'New conversation',
  }
}
