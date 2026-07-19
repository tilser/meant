import type { DiscoverChatMessage } from './types'

export function comingSoonMessage(id: string): DiscoverChatMessage {
  return {
    id,
    role: 'ai',
    blocks: [{ type: 'newsletter' }],
  }
}
