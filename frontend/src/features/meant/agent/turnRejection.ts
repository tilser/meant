const DAILY_MESSAGE_LIMIT_CODE = 'agent_daily_message_limit'

export function agentTurnChatRejectionMessage(error: unknown): string | null {
  if (error === null || typeof error !== 'object') return null
  const rejection = error as { code?: unknown; message?: unknown }
  if (rejection.code !== DAILY_MESSAGE_LIMIT_CODE || typeof rejection.message !== 'string') {
    return null
  }
  const message = rejection.message.trim()
  return message || null
}
