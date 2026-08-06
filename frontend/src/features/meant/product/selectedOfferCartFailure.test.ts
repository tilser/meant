import { describe, expect, test } from 'bun:test'

import { ApiError } from '../../../lib/apiError'
import { selectedOfferCartFailure } from './selectedOfferCartFailure'

describe('selected offer cart failure', () => {
  test.each([
    [404, 'unknown_or_expired', false, true],
    [409, 'stale_identity_or_routing', true, true],
    [401, 'authentication', false, true],
    [503, 'provider_unavailable', true, false],
    [400, 'unknown', true, false],
  ] as const)('maps HTTP %i to actionable %s recovery', (status, kind, refresh, research) => {
    expect(selectedOfferCartFailure({ status, code: 'bad_request' })).toMatchObject({
      kind,
      refresh,
      research,
    })
  })

  test('does not expose unknown exception contents', () => {
    const failure = selectedOfferCartFailure(new Error('provider secret'))

    expect(failure.kind).toBe('unknown')
    expect(failure.message).not.toContain('provider secret')
  })

  test('shows the sanitized merchant rejection returned by the API', () => {
    const failure = selectedOfferCartFailure(
      new ApiError("The product 'Coffee Maker - Beige' is already sold out.", 400, 'bad_request'),
    )

    expect(failure).toEqual({
      kind: 'merchant_rejected',
      message: "The product 'Coffee Maker - Beige' is already sold out.",
      refresh: true,
      research: true,
    })
  })
})
