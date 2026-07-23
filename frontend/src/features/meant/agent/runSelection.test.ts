import { describe, expect, test } from 'bun:test'

import { agentRunCandidateIds, preferredAgentRunSnapshot } from './runSelection'

describe('agent run selection', () => {
  test('tries the server-selected current run before deduplicated message history', () => {
    expect(
      agentRunCandidateIds('run-current', ['run-old', null, 'run-current', 'run-new']),
    ).toEqual(['run-current', 'run-new', 'run-old'])
  })

  test('keeps streaming an older running run instead of switching to a newer queued run', () => {
    const newerQueued = { runId: 'run-new', status: 'QUEUED' as const }
    const olderRunning = { runId: 'run-old', status: 'RUNNING' as const }

    expect(preferredAgentRunSnapshot([newerQueued, olderRunning])).toBe(olderRunning)
  })

  test('prefers queued work over terminal history and otherwise keeps newest-first order', () => {
    const newestCompleted = { runId: 'run-complete', status: 'COMPLETED' as const }
    const queued = { runId: 'run-queued', status: 'QUEUED' as const }
    const olderFailed = { runId: 'run-failed', status: 'FAILED' as const }

    expect(preferredAgentRunSnapshot([newestCompleted, queued, olderFailed])).toBe(queued)
    expect(preferredAgentRunSnapshot([newestCompleted, olderFailed])).toBe(newestCompleted)
    expect(preferredAgentRunSnapshot([])).toBeNull()
  })
})
