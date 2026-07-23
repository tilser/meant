import type { AgentRunSnapshotProfile } from '../../../lib/apiClient'

/** Uses the server-selected FIFO head first, then newest message-linked runs as recovery candidates. */
export function agentRunCandidateIds(
  currentRunId: string | null | undefined,
  chronologicalMessageRunIds: readonly (string | null | undefined)[],
): string[] {
  return [
    ...new Set([
      ...(currentRunId ? [currentRunId] : []),
      ...[...chronologicalMessageRunIds]
        .reverse()
        .filter((runId): runId is string => Boolean(runId)),
    ]),
  ]
}

/**
 * Snapshots arrive newest-first. Prefer work that is already streaming, then
 * queued work, and only fall back to the newest terminal run.
 */
export function preferredAgentRunSnapshot<T extends Pick<AgentRunSnapshotProfile, 'status'>>(
  snapshots: readonly T[],
): T | null {
  return (
    snapshots.find((snapshot) => snapshot.status === 'RUNNING') ??
    snapshots.find((snapshot) => snapshot.status === 'QUEUED') ??
    snapshots[0] ??
    null
  )
}
