import { SparkMark } from '../shared/ui'
import type { AgentActivity } from './types'

const PRODUCT_SEARCH_AGENT_NAMES: Record<string, string> = {
  discovery: 'Discovery agent',
  curator: 'Meant Curator agent',
  search: 'Search agent',
}

function productSearchAgentName(agent: string | null | undefined): string {
  if (!agent) {
    return 'Search agent'
  }
  return (
    PRODUCT_SEARCH_AGENT_NAMES[agent] ?? `${agent.charAt(0).toUpperCase()}${agent.slice(1)} agent`
  )
}

export function AgentActivityPanel({
  activities,
}: Readonly<{
  activities: readonly AgentActivity[]
}>) {
  if (activities.length === 0) {
    return null
  }
  return (
    <div className="mt-agent-rail" aria-live="polite">
      {activities.slice(-5).map((activity) => (
        <div className={`mt-agent-step ${activity.state}`} key={activity.agent}>
          <span className="mt-agent-orb" aria-hidden>
            <SparkMark size={11} />
          </span>
          <span className="mt-agent-copy">
            <span className="mt-agent-name mt-mono">{productSearchAgentName(activity.agent)}</span>
            <span className="mt-agent-label">{activity.label}</span>
          </span>
        </div>
      ))}
    </div>
  )
}
