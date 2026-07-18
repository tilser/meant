import type { AgentRunSnapshotProfile, AgentRunStatusProfile } from '../../../lib/apiClient'
import {
  decodeAgentEventEnvelope,
  type AgentEventArtifact,
  type AgentEventDecodeResult,
  type AgentEventMetadata,
  type AgentV1Event,
} from './protocol'

export interface AgentAssistantMessageState {
  messageId: string | null
  sequenceNumber: number | null
  text: string
  completedAt: string
}

export type AgentToolActivityStatus = 'PROPOSED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface AgentToolActivityState {
  modelToolCallId: string
  toolName: string
  status: AgentToolActivityStatus
  summary: string | null
  resultJson: string | null
  failureCode: string | null
  safeMessage: string | null
  updatedAt: string
}

export interface AgentRunProjection {
  runId: string
  conversationId: string
  status: AgentRunStatusProfile
  lastCursor: number
  streamingAssistantText: string
  assistantMessages: readonly AgentAssistantMessageState[]
  tools: Readonly<Record<string, AgentToolActivityState>>
  artifacts: Readonly<Record<string, AgentEventArtifact>>
  artifactOrder: readonly string[]
  terminalMessage: string | null
  failureCode: string | null
  lastOccurredAt: string | null
  duplicateEventCount: number
  unknownEventCount: number
}

export type AgentSnapshotRecoveryReason =
  | 'CURSOR_GAP'
  | 'UNSUPPORTED_SCHEMA'
  | 'MALFORMED_EVENT'
  | 'RUN_ID_COLLISION'
  | 'EVENT_AFTER_TERMINAL'

export interface AgentSnapshotRecoverySignal {
  runId: string | null
  conversationId: string | null
  reason: AgentSnapshotRecoveryReason
  lastAppliedCursor: number | null
  expectedCursor: number | null
  receivedCursor: number | null
  schemaVersion: number | null
  detail: string
  requiredSnapshots: readonly ['RUN', 'CONVERSATION']
}

export interface AgentEventReducerState {
  runs: Readonly<Record<string, AgentRunProjection>>
  snapshotRecovery: Readonly<Record<string, AgentSnapshotRecoverySignal>>
  unscopedRecovery: AgentSnapshotRecoverySignal | null
  malformedEventCount: number
}

export function snapshotRecoveryForAgentRun(
  state: AgentEventReducerState,
  runId: string,
): AgentSnapshotRecoverySignal | null {
  return state.snapshotRecovery[runId] ?? state.unscopedRecovery
}

const TERMINAL_RUN_STATUSES = new Set<AgentRunStatusProfile>([
  'WAITING_FOR_USER',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
])

function emptyRun(
  runId: string,
  conversationId: string,
  status: AgentRunStatusProfile = 'QUEUED',
  lastCursor = 0,
): AgentRunProjection {
  return {
    runId,
    conversationId,
    status,
    lastCursor,
    streamingAssistantText: '',
    assistantMessages: [],
    tools: {},
    artifacts: {},
    artifactOrder: [],
    terminalMessage: null,
    failureCode: null,
    lastOccurredAt: null,
    duplicateEventCount: 0,
    unknownEventCount: 0,
  }
}

export function createAgentEventReducerState(
  snapshots: readonly AgentRunSnapshotProfile[] = [],
): AgentEventReducerState {
  const runs: Record<string, AgentRunProjection> = {}
  for (const snapshot of snapshots) {
    runs[snapshot.runId] = emptyRun(
      snapshot.runId,
      snapshot.conversationId,
      snapshot.status,
      snapshot.latestCursor,
    )
  }
  return {
    runs,
    snapshotRecovery: {},
    unscopedRecovery: null,
    malformedEventCount: 0,
  }
}

function isDecodeResult(value: unknown): value is AgentEventDecodeResult {
  if (typeof value !== 'object' || value === null || !('kind' in value)) {
    return false
  }
  const kind = (value as { kind?: unknown }).kind
  return (
    kind === 'event' ||
    kind === 'unknown_type' ||
    kind === 'unsupported_schema' ||
    kind === 'malformed'
  )
}

function metadataFor(result: AgentEventDecodeResult): AgentEventMetadata | undefined {
  switch (result.kind) {
    case 'event':
      return result.event
    case 'unknown_type':
    case 'unsupported_schema':
      return result.envelope
    case 'malformed':
      return result.envelope
  }
}

function recoverySignal(
  reason: AgentSnapshotRecoveryReason,
  detail: string,
  run: AgentRunProjection | undefined,
  envelope?: AgentEventMetadata,
): AgentSnapshotRecoverySignal {
  return {
    runId: envelope?.runId ?? run?.runId ?? null,
    conversationId: envelope?.conversationId ?? run?.conversationId ?? null,
    reason,
    lastAppliedCursor: run?.lastCursor ?? null,
    expectedCursor: run ? run.lastCursor + 1 : envelope ? 1 : null,
    receivedCursor: envelope?.cursor ?? null,
    schemaVersion: envelope?.schemaVersion ?? null,
    detail,
    requiredSnapshots: ['RUN', 'CONVERSATION'],
  }
}

function withRecovery(
  state: AgentEventReducerState,
  signal: AgentSnapshotRecoverySignal,
): AgentEventReducerState {
  if (!signal.runId) {
    return {
      ...state,
      unscopedRecovery: signal,
      malformedEventCount: state.malformedEventCount + 1,
    }
  }
  const existing = state.snapshotRecovery[signal.runId]
  if (
    existing?.reason === signal.reason &&
    existing.receivedCursor === signal.receivedCursor &&
    existing.schemaVersion === signal.schemaVersion
  ) {
    return state
  }
  return {
    ...state,
    snapshotRecovery: { ...state.snapshotRecovery, [signal.runId]: signal },
  }
}

function replaceRun(
  state: AgentEventReducerState,
  run: AgentRunProjection,
): AgentEventReducerState {
  const recovery = state.snapshotRecovery[run.runId]
  if (
    recovery?.reason === 'CURSOR_GAP' &&
    recovery.receivedCursor !== null &&
    run.lastCursor >= recovery.receivedCursor
  ) {
    const snapshotRecovery = { ...state.snapshotRecovery }
    delete snapshotRecovery[run.runId]
    return {
      ...state,
      runs: { ...state.runs, [run.runId]: run },
      snapshotRecovery,
    }
  }
  return { ...state, runs: { ...state.runs, [run.runId]: run } }
}

function toolStatus(type: AgentV1Event['type']): AgentToolActivityStatus | null {
  switch (type) {
    case 'tool.proposed':
      return 'PROPOSED'
    case 'tool.started':
      return 'RUNNING'
    case 'tool.completed':
      return 'COMPLETED'
    case 'tool.failed':
      return 'FAILED'
    default:
      return null
  }
}

function upsertArtifact(
  run: AgentRunProjection,
  artifact: AgentEventArtifact | null,
): AgentRunProjection {
  if (!artifact) {
    return run
  }
  const alreadyPresent = artifact.stableKey in run.artifacts
  return {
    ...run,
    artifacts: { ...run.artifacts, [artifact.stableKey]: artifact },
    artifactOrder: alreadyPresent ? run.artifactOrder : [...run.artifactOrder, artifact.stableKey],
  }
}

function applyKnownEvent(run: AgentRunProjection, event: AgentV1Event): AgentRunProjection {
  const next: AgentRunProjection = {
    ...run,
    lastCursor: event.cursor,
    lastOccurredAt: event.occurredAt,
  }

  switch (event.type) {
    case 'run.started':
      return { ...next, status: 'RUNNING', terminalMessage: null, failureCode: null }
    case 'assistant.delta':
      return { ...next, streamingAssistantText: next.streamingAssistantText + event.text }
    case 'assistant.completed':
      return {
        ...next,
        streamingAssistantText: '',
        assistantMessages: [
          ...next.assistantMessages,
          {
            messageId: event.messageId,
            sequenceNumber: event.sequenceNumber,
            text: event.text,
            completedAt: event.occurredAt,
          },
        ],
      }
    case 'tool.proposed':
    case 'tool.started':
    case 'tool.completed':
    case 'tool.failed': {
      const status = toolStatus(event.type)!
      return {
        ...next,
        tools: {
          ...next.tools,
          [event.modelToolCallId]: {
            modelToolCallId: event.modelToolCallId,
            toolName: event.toolName,
            status,
            summary: event.summary,
            resultJson: event.resultJson,
            failureCode: event.failureCode,
            safeMessage: event.text,
            updatedAt: event.occurredAt,
          },
        },
      }
    }
    case 'artifact.upserted':
    case 'cart.changed':
    case 'checkout.ready':
      return upsertArtifact(next, event.artifact)
    case 'run.waiting_for_user':
      return {
        ...next,
        status: 'WAITING_FOR_USER',
        streamingAssistantText: '',
        terminalMessage: event.text,
        failureCode: event.failureCode,
      }
    case 'run.completed':
      return {
        ...next,
        status: 'COMPLETED',
        streamingAssistantText: '',
        terminalMessage: event.text,
        failureCode: event.failureCode,
      }
    case 'run.failed':
      return {
        ...next,
        status: 'FAILED',
        streamingAssistantText: '',
        terminalMessage: event.text,
        failureCode: event.failureCode,
      }
    case 'run.cancelled':
      return {
        ...next,
        status: 'CANCELLED',
        streamingAssistantText: '',
        terminalMessage: event.text,
        failureCode: event.failureCode,
      }
  }
}

/**
 * Pure projection reducer. A cursor gap or incompatible event never mutates transcript/artifact
 * state; instead it emits a per-run instruction to reload both authoritative snapshots.
 */
export function reduceAgentEvent(
  state: AgentEventReducerState,
  input: unknown,
): AgentEventReducerState {
  const decoded = isDecodeResult(input) ? input : decodeAgentEventEnvelope(input)
  const envelope = metadataFor(decoded)
  if (!envelope) {
    return withRecovery(
      state,
      recoverySignal(
        'MALFORMED_EVENT',
        decoded.kind === 'malformed' ? decoded.reason : 'Malformed event',
        undefined,
      ),
    )
  }

  const existing = state.runs[envelope.runId]
  const run = existing ?? emptyRun(envelope.runId, envelope.conversationId)
  if (run.conversationId !== envelope.conversationId) {
    return withRecovery(
      state,
      recoverySignal(
        'RUN_ID_COLLISION',
        'One runId was observed with more than one conversationId',
        run,
        envelope,
      ),
    )
  }
  if (envelope.cursor <= run.lastCursor) {
    return replaceRun(state, { ...run, duplicateEventCount: run.duplicateEventCount + 1 })
  }
  if (envelope.cursor !== run.lastCursor + 1) {
    const stateWithRun = existing ? state : replaceRun(state, run)
    return withRecovery(
      stateWithRun,
      recoverySignal(
        'CURSOR_GAP',
        `Expected cursor ${run.lastCursor + 1} but received ${envelope.cursor}`,
        run,
        envelope,
      ),
    )
  }
  if (decoded.kind === 'unsupported_schema') {
    const stateWithRun = existing ? state : replaceRun(state, run)
    return withRecovery(
      stateWithRun,
      recoverySignal(
        'UNSUPPORTED_SCHEMA',
        `Event schema version ${envelope.schemaVersion} is not supported`,
        run,
        envelope,
      ),
    )
  }
  if (decoded.kind === 'malformed') {
    const stateWithRun = existing ? state : replaceRun(state, run)
    return withRecovery(
      stateWithRun,
      recoverySignal('MALFORMED_EVENT', decoded.reason, run, envelope),
    )
  }
  if (decoded.kind === 'unknown_type') {
    return replaceRun(state, {
      ...run,
      lastCursor: envelope.cursor,
      lastOccurredAt: envelope.occurredAt,
      unknownEventCount: run.unknownEventCount + 1,
    })
  }
  if (TERMINAL_RUN_STATUSES.has(run.status)) {
    return withRecovery(
      state,
      recoverySignal(
        'EVENT_AFTER_TERMINAL',
        `Received ${decoded.event.type} after run reached ${run.status}`,
        run,
        envelope,
      ),
    )
  }
  return replaceRun(state, applyKnownEvent(run, decoded.event))
}

/** Clears a recovery marker after authoritative run and conversation snapshots were loaded. */
export function restoreAgentRunSnapshot(
  state: AgentEventReducerState,
  snapshot: AgentRunSnapshotProfile,
): AgentEventReducerState {
  const snapshotRecovery = { ...state.snapshotRecovery }
  delete snapshotRecovery[snapshot.runId]
  return {
    ...state,
    unscopedRecovery: null,
    runs: {
      ...state.runs,
      [snapshot.runId]: emptyRun(
        snapshot.runId,
        snapshot.conversationId,
        snapshot.status,
        snapshot.latestCursor,
      ),
    },
    snapshotRecovery,
  }
}
