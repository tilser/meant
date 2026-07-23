import { describe, expect, test } from 'bun:test'

import { refreshAgentViewAfterSettlement } from './settlementRefresh'

describe('agent run settlement refresh', () => {
  test('refreshes conversation lists and the active durable conversation', async () => {
    const refreshedConversations: string[] = []
    const discoveredSnapshots: string[] = []
    let listRefreshes = 0

    await refreshAgentViewAfterSettlement({
      activeConversationId: 'conversation-1',
      refreshLists: async () => {
        listRefreshes += 1
      },
      refreshConversation: async (conversationId) => {
        refreshedConversations.push(conversationId)
        return `snapshot:${conversationId}`
      },
      discoverLatestRun: (snapshot) => {
        discoveredSnapshots.push(snapshot)
      },
    })

    expect(listRefreshes).toBe(1)
    expect(refreshedConversations).toEqual(['conversation-1'])
    expect(discoveredSnapshots).toEqual(['snapshot:conversation-1'])
  })

  test('refreshes only lists when no conversation is active', async () => {
    let listRefreshes = 0
    let conversationRefreshes = 0

    await refreshAgentViewAfterSettlement({
      activeConversationId: null,
      refreshLists: async () => {
        listRefreshes += 1
      },
      refreshConversation: async () => {
        conversationRefreshes += 1
        return 'unused'
      },
      discoverLatestRun: () => undefined,
    })

    expect(listRefreshes).toBe(1)
    expect(conversationRefreshes).toBe(0)
  })
})
