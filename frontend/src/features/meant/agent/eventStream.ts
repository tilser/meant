import { openAgentRunEventStream, type AgentRunEventStreamInput } from '../../../lib/apiClient'
import { decodeAgentEventEnvelope, type AgentEventDecodeResult } from './protocol'
import { parseServerSentEventStream } from './sse'

/** Opens the authenticated run stream and yields decoded protocol outcomes in wire order. */
export async function* streamAgentRunEvents(
  input: AgentRunEventStreamInput,
): AsyncGenerator<AgentEventDecodeResult> {
  const response = await openAgentRunEventStream(input)
  for await (const serverEvent of parseServerSentEventStream(response.body!)) {
    if (serverEvent.data === '[DONE]') {
      continue
    }
    try {
      yield decodeAgentEventEnvelope(JSON.parse(serverEvent.data) as unknown)
    } catch {
      yield { kind: 'malformed', reason: 'SSE data must contain a JSON event envelope' }
    }
  }
}

export function isExpiredAgentEventCursor(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    (error as { status?: unknown }).status === 410
  )
}
