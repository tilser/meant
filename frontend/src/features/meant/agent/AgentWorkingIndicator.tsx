import { useEffect, useState } from 'react'

import { MeantHeartMark } from '../shared/ui'
import type { AgentWorkingStage } from './agentWorkingState'
import { SEARCH_STATUS_MESSAGES, SEARCH_STATUS_ROTATION_MS } from './searchStatus'

const STAGE_COPY: Record<AgentWorkingStage, { title: string; detail: string }> = {
  submitting: {
    title: 'Meant is receiving your request',
    detail: 'Opening the thread and getting oriented.',
  },
  queued: {
    title: 'Meant is getting oriented',
    detail: 'Reading the thread and finding the next move.',
  },
  working: {
    title: '',
    detail: 'Following the thread and gathering what matters.',
  },
}

function RotatingSearchStatus() {
  const [messageIndex, setMessageIndex] = useState(0)

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setMessageIndex((currentIndex) => (currentIndex + 1) % SEARCH_STATUS_MESSAGES.length)
    }, SEARCH_STATUS_ROTATION_MS)

    return () => window.clearInterval(intervalId)
  }, [])

  const message = SEARCH_STATUS_MESSAGES[messageIndex]

  return (
    <span className="mt-agent-working-title" key={messageIndex}>
      {message.beforeMeant}
      <em>Meant</em>
      {message.afterMeant}
    </span>
  )
}

export function AgentWorkingIndicator({ stage }: Readonly<{ stage: AgentWorkingStage }>) {
  const copy = STAGE_COPY[stage]

  return (
    <div
      className={`mt-agent-working ${stage}`}
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      <span className="mt-agent-working-mark" aria-hidden="true">
        <span className="mt-agent-heart-ripple first" />
        <span className="mt-agent-heart-ripple second" />
        <span className="mt-agent-heart-orbit">
          <i />
          <i />
          <i />
        </span>
        <span className="mt-agent-heart">
          <MeantHeartMark />
        </span>
      </span>
      <span className="mt-agent-working-copy">
        <strong>{stage === 'working' ? <RotatingSearchStatus /> : copy.title}</strong>
        <span>{copy.detail}</span>
      </span>
    </div>
  )
}
