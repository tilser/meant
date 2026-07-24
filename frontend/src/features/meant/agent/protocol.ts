import type {
  AgentArtifactProfile,
  AgentArtifactTypeProfile,
  AgentRunEventEnvelopeProfile,
} from '../../../lib/apiClient'
import { sanitizeBuyerVisibleJson, sanitizeBuyerVisibleText } from './buyerVisibleText'

export const AGENT_EVENT_SCHEMA_VERSION = 1 as const

export const AGENT_V1_EVENT_TYPES = [
  'run.started',
  'assistant.delta',
  'assistant.completed',
  'tool.proposed',
  'tool.started',
  'tool.completed',
  'tool.failed',
  'artifact.upserted',
  'cart.changed',
  'checkout.ready',
  'run.waiting_for_user',
  'run.completed',
  'run.failed',
  'run.cancelled',
] as const

export type AgentV1EventType = (typeof AGENT_V1_EVENT_TYPES)[number]

const AGENT_ARTIFACT_TYPES = new Set<AgentArtifactTypeProfile>([
  'PRODUCT',
  'OFFER',
  'INVENTORY_ITEM',
  'ORDER',
  'SAVED_PRODUCT',
  'PRODUCT_STATE',
  'COMPARISON',
  'REVIEWS',
  'DISCOUNT_CODES',
  'MISSION',
  'CART',
  'CART_LINE',
  'CHECKOUT',
])

const AGENT_V1_EVENT_TYPE_SET = new Set<string>(AGENT_V1_EVENT_TYPES)

export interface AgentEventMetadata {
  schemaVersion: number
  cursor: number
  conversationId: string
  runId: string
  type: string
  occurredAt: string
}

export type AgentEventArtifact = AgentArtifactProfile

interface AgentEventBase<TType extends AgentV1EventType> extends AgentEventMetadata {
  schemaVersion: typeof AGENT_EVENT_SCHEMA_VERSION
  type: TType
}

export type AgentV1Event =
  | AgentEventBase<'run.started'>
  | (AgentEventBase<'assistant.delta'> & { text: string })
  | (AgentEventBase<'assistant.completed'> & {
      text: string
      messageId: string | null
      sequenceNumber: number | null
    })
  | (AgentEventBase<'tool.proposed' | 'tool.started' | 'tool.completed' | 'tool.failed'> & {
      modelToolCallId: string
      toolName: string
      summary: string | null
      resultJson: string | null
      failureCode: string | null
      text: string | null
    })
  | (AgentEventBase<'artifact.upserted'> & {
      artifact: AgentEventArtifact
    })
  | (AgentEventBase<'cart.changed' | 'checkout.ready'> & {
      artifact: AgentEventArtifact | null
      modelToolCallId: string | null
      toolName: string | null
      summary: string | null
      resultJson: string | null
    })
  | (AgentEventBase<'run.waiting_for_user' | 'run.completed' | 'run.failed' | 'run.cancelled'> & {
      text: string | null
      failureCode: string | null
    })

export type AgentEventDecodeResult =
  | { kind: 'event'; event: AgentV1Event }
  | { kind: 'unknown_type'; envelope: AgentEventMetadata }
  | { kind: 'unsupported_schema'; envelope: AgentEventMetadata }
  | { kind: 'malformed'; reason: string; envelope?: AgentEventMetadata }

type JsonObject = Record<string, unknown>

interface FieldResult<T> {
  valid: boolean
  value: T
}

function isObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function nullableString(value: unknown): FieldResult<string | null> {
  if (value === undefined || value === null) {
    return { valid: true, value: null }
  }
  return typeof value === 'string' ? { valid: true, value } : { valid: false, value: null }
}

function nullableInteger(value: unknown): FieldResult<number | null> {
  if (value === undefined || value === null) {
    return { valid: true, value: null }
  }
  return Number.isSafeInteger(value)
    ? { valid: true, value: value as number }
    : { valid: false, value: null }
}

function requiredString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}

function metadataFromEnvelope(value: JsonObject): AgentEventMetadata | null {
  if (
    !Number.isSafeInteger(value.schemaVersion) ||
    !Number.isSafeInteger(value.cursor) ||
    (value.cursor as number) < 1 ||
    !requiredString(value.conversationId) ||
    !requiredString(value.runId) ||
    !requiredString(value.type) ||
    !requiredString(value.occurredAt)
  ) {
    return null
  }
  return {
    schemaVersion: value.schemaVersion as number,
    cursor: value.cursor as number,
    conversationId: value.conversationId,
    runId: value.runId,
    type: value.type,
    occurredAt: value.occurredAt,
  }
}

function parsePayload(payloadJson: string): JsonObject | null {
  try {
    const payload = JSON.parse(payloadJson) as unknown
    return isObject(payload) ? payload : null
  } catch {
    return null
  }
}

function mapArtifact(value: unknown): AgentEventArtifact | null {
  if (!isObject(value)) {
    return null
  }
  const runId = nullableString(value.runId)
  const label = nullableString(value.label)
  const canonicalProductKey = nullableString(value.canonicalProductKey)
  const offerKey = nullableString(value.offerKey)
  const inventoryItemId = nullableString(value.inventoryItemId)
  const cartId = nullableString(value.cartId)
  const cartLineId = nullableString(value.cartLineId)
  const checkoutAttemptId = nullableString(value.checkoutAttemptId)
  if (
    !runId.valid ||
    !label.valid ||
    !canonicalProductKey.valid ||
    !offerKey.valid ||
    !inventoryItemId.valid ||
    !cartId.valid ||
    !cartLineId.valid ||
    !checkoutAttemptId.valid ||
    !requiredString(value.artifactId) ||
    !requiredString(value.messageId) ||
    !requiredString(value.type) ||
    !AGENT_ARTIFACT_TYPES.has(value.type as AgentArtifactTypeProfile) ||
    !Number.isSafeInteger(value.ordinal) ||
    (value.ordinal as number) < 0 ||
    !requiredString(value.stableKey) ||
    typeof value.payloadJson !== 'string' ||
    !requiredString(value.createdAt)
  ) {
    return null
  }
  return {
    artifactId: value.artifactId,
    messageId: value.messageId,
    runId: runId.value,
    type: value.type as AgentArtifactTypeProfile,
    ordinal: value.ordinal as number,
    stableKey: value.stableKey,
    label: label.value === null ? null : sanitizeBuyerVisibleText(label.value),
    canonicalProductKey: canonicalProductKey.value,
    offerKey: offerKey.value,
    inventoryItemId: inventoryItemId.value,
    cartId: cartId.value,
    cartLineId: cartLineId.value,
    checkoutAttemptId: checkoutAttemptId.value,
    payloadJson: value.payloadJson,
    createdAt: value.createdAt,
  }
}

function malformed(reason: string, envelope: AgentEventMetadata): AgentEventDecodeResult {
  return { kind: 'malformed', reason, envelope }
}

/**
 * Converts the deliberately narrow server envelope into the stable event model used by the UI.
 * Unknown v1 event types are preserved as ordered no-ops; unknown schema versions require a
 * snapshot instead of being guessed at.
 */
export function decodeAgentEventEnvelope(value: unknown): AgentEventDecodeResult {
  if (!isObject(value)) {
    return { kind: 'malformed', reason: 'Event envelope must be an object' }
  }
  const envelope = metadataFromEnvelope(value)
  if (!envelope || typeof value.payloadJson !== 'string') {
    return { kind: 'malformed', reason: 'Event envelope fields are invalid' }
  }
  if (envelope.schemaVersion !== AGENT_EVENT_SCHEMA_VERSION) {
    return { kind: 'unsupported_schema', envelope }
  }
  if (!AGENT_V1_EVENT_TYPE_SET.has(envelope.type)) {
    return { kind: 'unknown_type', envelope }
  }
  const payload = parsePayload(value.payloadJson)
  if (!payload) {
    return malformed('Event payloadJson must contain a JSON object', envelope)
  }
  const base = {
    ...envelope,
    schemaVersion: AGENT_EVENT_SCHEMA_VERSION,
    type: envelope.type as AgentV1EventType,
  }

  switch (base.type) {
    case 'run.started':
      return { kind: 'event', event: { ...base, type: 'run.started' } }
    case 'assistant.delta': {
      if (typeof payload.text !== 'string') {
        return malformed('assistant.delta payload requires text', envelope)
      }
      return {
        kind: 'event',
        event: {
          ...base,
          type: 'assistant.delta',
          text: sanitizeBuyerVisibleText(payload.text),
        },
      }
    }
    case 'assistant.completed': {
      const messageId = nullableString(payload.messageId)
      const sequenceNumber = nullableInteger(payload.sequenceNumber)
      if (typeof payload.text !== 'string' || !messageId.valid || !sequenceNumber.valid) {
        return malformed('assistant.completed payload fields are invalid', envelope)
      }
      return {
        kind: 'event',
        event: {
          ...base,
          type: 'assistant.completed',
          text: sanitizeBuyerVisibleText(payload.text),
          messageId: messageId.value,
          sequenceNumber: sequenceNumber.value,
        },
      }
    }
    case 'tool.proposed':
    case 'tool.started':
    case 'tool.completed':
    case 'tool.failed': {
      const summary = nullableString(payload.summary)
      const resultJson = nullableString(payload.resultJson)
      const failureCode = nullableString(payload.failureCode)
      const text = nullableString(payload.text)
      if (
        !requiredString(payload.modelToolCallId) ||
        !requiredString(payload.toolName) ||
        !summary.valid ||
        !resultJson.valid ||
        !failureCode.valid ||
        !text.valid
      ) {
        return malformed(`${base.type} payload fields are invalid`, envelope)
      }
      return {
        kind: 'event',
        event: {
          ...base,
          type: base.type,
          modelToolCallId: payload.modelToolCallId,
          toolName: payload.toolName,
          summary: summary.value === null ? null : sanitizeBuyerVisibleText(summary.value),
          resultJson: sanitizeBuyerVisibleJson(resultJson.value),
          failureCode: failureCode.value,
          text: text.value === null ? null : sanitizeBuyerVisibleText(text.value),
        },
      }
    }
    case 'artifact.upserted': {
      const artifact = payload.artifact === null ? null : mapArtifact(payload.artifact)
      if (!artifact) {
        return malformed('artifact.upserted payload fields are invalid', envelope)
      }
      return {
        kind: 'event',
        event: {
          ...base,
          type: 'artifact.upserted',
          artifact,
        },
      }
    }
    case 'cart.changed':
    case 'checkout.ready': {
      const artifact = payload.artifact == null ? null : mapArtifact(payload.artifact)
      const modelToolCallId = nullableString(payload.modelToolCallId)
      const toolName = nullableString(payload.toolName)
      const summary = nullableString(payload.summary)
      const resultJson = nullableString(payload.resultJson)
      if (
        (payload.artifact !== undefined && payload.artifact !== null && !artifact) ||
        !modelToolCallId.valid ||
        !toolName.valid ||
        !summary.valid ||
        !resultJson.valid
      ) {
        return malformed(`${base.type} payload fields are invalid`, envelope)
      }
      return {
        kind: 'event',
        event: {
          ...base,
          type: base.type,
          artifact,
          modelToolCallId: modelToolCallId.value,
          toolName: toolName.value,
          summary: summary.value === null ? null : sanitizeBuyerVisibleText(summary.value),
          resultJson: sanitizeBuyerVisibleJson(resultJson.value),
        },
      }
    }
    case 'run.waiting_for_user':
    case 'run.completed':
    case 'run.failed':
    case 'run.cancelled': {
      const text = nullableString(payload.text)
      const summary = nullableString(payload.summary)
      const failureCode = nullableString(payload.failureCode)
      if (!text.valid || !summary.valid || !failureCode.valid) {
        return malformed(`${base.type} payload fields are invalid`, envelope)
      }
      return {
        kind: 'event',
        event: {
          ...base,
          type: base.type,
          text:
            text.value !== null
              ? sanitizeBuyerVisibleText(text.value)
              : summary.value !== null
                ? sanitizeBuyerVisibleText(summary.value)
                : null,
          failureCode: failureCode.value,
        },
      }
    }
  }
}

export function isAgentRunEventEnvelope(value: unknown): value is AgentRunEventEnvelopeProfile {
  return (
    isObject(value) && metadataFromEnvelope(value) !== null && typeof value.payloadJson === 'string'
  )
}
