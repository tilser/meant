export type AgentWorkingStage = 'submitting' | 'queued' | 'working'

interface AgentWorkingStateInput {
  submitting: boolean
  isRunning: boolean
  status: 'QUEUED' | 'RUNNING' | undefined
  hasVisibleOutput: boolean
}

export function agentWorkingStage({
  submitting,
  isRunning,
  status,
  hasVisibleOutput,
}: Readonly<AgentWorkingStateInput>): AgentWorkingStage | null {
  if (submitting) return 'submitting'
  if (!isRunning || hasVisibleOutput) return null
  return status === 'RUNNING' ? 'working' : 'queued'
}
