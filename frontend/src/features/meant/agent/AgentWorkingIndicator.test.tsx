import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { AgentWorkingIndicator } from './AgentWorkingIndicator'
import { agentWorkingStage } from './agentWorkingState'
import { SEARCH_STATUS_MESSAGES, SEARCH_STATUS_ROTATION_MS } from './searchStatus'

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
    expect(markup).toContain('Searching for what’s <em>Meant</em> for you.')
    expect(markup).toContain('Following the thread and gathering what matters.')
  })

  test('defines the complete two-second searching sequence', () => {
    expect(SEARCH_STATUS_ROTATION_MS).toBe(2_000)
    expect(
      SEARCH_STATUS_MESSAGES.map(
        ({ beforeMeant, afterMeant }) => `${beforeMeant}Meant${afterMeant}`,
      ),
    ).toEqual([
      'Searching for what’s Meant for you.',
      'Filtering out what isn’t Meant for you.',
      'Checking what’s truly Meant for you.',
      'Almost found what’s Meant for you.',
    ])
  })
})
