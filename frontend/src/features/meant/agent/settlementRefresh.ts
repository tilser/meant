interface AgentRunSettlementRefreshOptions<T> {
  activeConversationId: string | null
  refreshLists: () => Promise<unknown>
  refreshConversation: (conversationId: string) => Promise<T>
  discoverLatestRun: (snapshot: T) => Promise<unknown> | unknown
}

/** Refreshes durable navigation state without creating another run-status watcher. */
export async function refreshAgentViewAfterSettlement<T>({
  activeConversationId,
  refreshLists,
  refreshConversation,
  discoverLatestRun,
}: AgentRunSettlementRefreshOptions<T>): Promise<void> {
  const conversationRefresh = activeConversationId
    ? refreshConversation(activeConversationId).then(discoverLatestRun)
    : Promise.resolve()
  await Promise.allSettled([refreshLists(), conversationRefresh])
}
