import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { AgentWorkingIndicator } from './AgentWorkingIndicator'
import { agentWorkingStage } from './agentWorkingState'

describe('agent working indicator', () => {
  test('covers submission and the silent queued or running states', () => {
    expect(
      agentWorkingStage({
        submitting: true,
        isRunning: false,
        status: undefined,
        hasVisibleOutput: false,
      }),
    ).toBe('submitting')
    expect(
      agentWorkingStage({
        submitting: false,
        isRunning: true,
        status: 'QUEUED',
        hasVisibleOutput: false,
      }),
    ).toBe('queued')
    expect(
      agentWorkingStage({
        submitting: false,
        isRunning: true,
        status: 'RUNNING',
        hasVisibleOutput: false,
      }),
    ).toBe('working')
  })

  test('hands off as soon as visible agent output arrives', () => {
    expect(
      agentWorkingStage({
        submitting: false,
        isRunning: true,
        status: 'RUNNING',
        hasVisibleOutput: true,
      }),
    ).toBeNull()
    expect(
      agentWorkingStage({
        submitting: false,
        isRunning: false,
        status: 'RUNNING',
        hasVisibleOutput: false,
      }),
    ).toBeNull()
  })

  test('renders the branded heart as an accessible live status', () => {
    const markup = renderToStaticMarkup(<AgentWorkingIndicator stage="working" />)

    expect(markup).toContain('role="status"')
    expect(markup).toContain('aria-live="polite"')
    expect(markup).toContain('mt-agent-heart')
    expect(markup).toContain('Meant is thinking with you')
    expect(markup).toContain('Following the thread and gathering what matters.')
  })
})
