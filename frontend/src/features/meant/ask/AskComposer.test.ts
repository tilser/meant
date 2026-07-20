import { describe, expect, test } from 'bun:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { AskComposer } from './AskComposer'
import { askComposerSubmissionAccepted, askComposerSuggestionValue } from './askSubmission'

describe('AskComposer submission acknowledgement', () => {
  test('keeps the draft when the current run rejects the submission', async () => {
    const accepted = await askComposerSubmissionAccepted(async () => false, 'the second one')

    expect(accepted).toBe(false)
  })

  test('clears the draft after an accepted submission', async () => {
    expect(await askComposerSubmissionAccepted(async () => true, 'the second one')).toBe(true)
    expect(await askComposerSubmissionAccepted(() => undefined, 'the second one')).toBe(true)
  })

  test('keeps the draft when a submission handler rejects', async () => {
    const accepted = await askComposerSubmissionAccepted(async () => {
      throw new Error('network unavailable')
    }, 'the second one')

    expect(accepted).toBe(false)
  })

  test('submits the safe value behind a descriptive clarification chip', () => {
    expect(
      askComposerSuggestionValue('2. An extremely long product title that is only for display', 1, [
        '1',
        '2',
      ]),
    ).toBe('2')
  })

  test('replaces the send action with Stop in the same button slot while running', () => {
    const markup = renderToStaticMarkup(
      createElement(AskComposer, {
        placeholder: 'Ask Meant...',
        suggestions: [],
        showChips: false,
        onAsk: () => undefined,
        running: true,
        onStop: () => undefined,
      }),
    )

    expect(markup).toContain('class="mt-ask-go mt-ask-stop"')
    expect(markup).toContain('type="button"')
    expect(markup).toContain('aria-label="Stop"')
    expect(markup).not.toContain('aria-label="Ask"')
    expect(markup).not.toContain('mt-ct-cobtn')
  })
})
