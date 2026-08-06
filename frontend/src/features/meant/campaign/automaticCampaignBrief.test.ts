import { describe, expect, test } from 'bun:test'

import { submitAutomaticCampaignBrief } from './automaticCampaignBrief'

describe('automatic campaign brief submission', () => {
  test('submits and consumes a normalized brief only once', async () => {
    const attempt = { current: null as string | null }
    const submissions: string[] = []
    let consumed = 0
    const input = {
      initialBrief: '  Find me a wool coat  ',
      unavailable: false,
      attempt,
      submit: async (brief: string) => {
        submissions.push(brief)
        return true
      },
      onConsumed: () => {
        consumed += 1
      },
    }

    expect(await submitAutomaticCampaignBrief(input)).toBeTrue()
    expect(await submitAutomaticCampaignBrief(input)).toBeFalse()
    expect(submissions).toEqual(['Find me a wool coat'])
    expect(consumed).toBe(1)
  })

  test('claims the brief before an asynchronous submission settles', async () => {
    const attempt = { current: null as string | null }
    let acceptSubmission: (accepted: boolean) => void = () => undefined
    const input = {
      initialBrief: 'Find a quiet coffee machine',
      unavailable: false,
      attempt,
      submit: () =>
        new Promise<boolean>((resolve) => {
          acceptSubmission = resolve
        }),
      onConsumed: () => undefined,
    }

    const first = submitAutomaticCampaignBrief(input)
    expect(await submitAutomaticCampaignBrief(input)).toBeFalse()
    acceptSubmission(true)
    expect(await first).toBeTrue()
  })

  test('waits while submission is unavailable', async () => {
    const attempt = { current: null as string | null }
    let submitted = false

    expect(
      await submitAutomaticCampaignBrief({
        initialBrief: 'Find running shoes',
        unavailable: true,
        attempt,
        submit: async () => {
          submitted = true
          return true
        },
        onConsumed: () => undefined,
      }),
    ).toBeFalse()
    expect(submitted).toBeFalse()
    expect(attempt.current).toBeNull()
  })

  test('releases the claim when the chat rejects the submission', async () => {
    const attempt = { current: null as string | null }

    expect(
      await submitAutomaticCampaignBrief({
        initialBrief: 'Find running shoes',
        unavailable: false,
        attempt,
        submit: async () => false,
        onConsumed: () => undefined,
      }),
    ).toBeFalse()
    expect(attempt.current).toBeNull()
  })
})
