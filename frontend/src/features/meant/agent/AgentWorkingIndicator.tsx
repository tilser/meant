import { useEffect, useId, useState } from 'react'

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

function MeantHeartMark() {
  const gradientId = useId()

  return (
    <svg viewBox="0 0 48 44" aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="5" y1="5" x2="43" y2="39">
          <stop offset="0" stopColor="#16d3df" />
          <stop offset="0.48" stopColor="#4d99e8" />
          <stop offset="1" stopColor="#9847d6" />
        </linearGradient>
      </defs>
      <path
        d="M24 40.2C21.3 38 5 26.4 5 14.2 5 7.5 9.5 3.5 15.1 3.5c3.8 0 7.1 2 8.9 5.2 1.8-3.2 5.1-5.2 8.9-5.2 5.6 0 10.1 4 10.1 10.7 0 12.2-16.3 23.8-19 26Z"
        fill={`url(#${gradientId})`}
      />
      <path
        className="mt-agent-heart-check"
        d="m15.2 20.7 6.2 5.7 12.1-12"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="5.2"
      />
    </svg>
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
