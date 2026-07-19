import { describe, expect, test } from 'bun:test'

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
})
