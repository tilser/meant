import { ApiError } from '../../../lib/apiError'

export type SelectedOfferCartFailureKind =
  | 'unknown_or_expired'
  | 'stale_identity_or_routing'
  | 'merchant_rejected'
  | 'authentication'
  | 'provider_unavailable'
  | 'unknown'

export interface SelectedOfferCartFailure {
  kind: SelectedOfferCartFailureKind
  message: string
  refresh: boolean
  research: boolean
}

export function selectedOfferCartFailure(error: unknown): SelectedOfferCartFailure {
  const status =
    typeof error === 'object' && error !== null && 'status' in error
      ? (error as { status?: unknown }).status
      : null
  if (typeof status !== 'number' || !Number.isInteger(status)) {
    return {
      kind: 'unknown',
      message: 'This exact offer could not be added. Your selection was kept; try again.',
      refresh: true,
      research: false,
    }
  }
  if (status === 404) {
    return {
      kind: 'unknown_or_expired',
      message: 'This offer session expired or is no longer known. Re-search for a fresh offer.',
      refresh: false,
      research: true,
    }
  }
  if (status === 409) {
    return {
      kind: 'stale_identity_or_routing',
      message:
        'The selected offer is stale, unavailable, or cannot be routed with its current identity. Meant did not choose another offer.',
      refresh: true,
      research: true,
    }
  }
  if (status === 401 || status === 403) {
    return {
      kind: 'authentication',
      message:
        'Your session cannot add this offer. Sign in again, then re-search for fresh offers.',
      refresh: false,
      research: true,
    }
  }
  if (status === 400 && error instanceof ApiError && error.message.trim()) {
    return {
      kind: 'merchant_rejected',
      message: error.message.trim(),
      refresh: true,
      research: true,
    }
  }
  if (status >= 500) {
    return {
      kind: 'provider_unavailable',
      message:
        'The merchant or commerce provider is temporarily unavailable. Your selected offer was kept.',
      refresh: true,
      research: false,
    }
  }
  return {
    kind: 'unknown',
    message: 'This exact offer could not be added. Your selection was kept; try again.',
    refresh: true,
    research: false,
  }
}
