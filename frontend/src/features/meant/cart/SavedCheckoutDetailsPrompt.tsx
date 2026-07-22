import { useEffect, useRef, useState } from 'react'

import type { CheckoutProfile } from '../../../lib/apiClient'
import type { CheckoutAssistantHandler } from './checkoutTypes'
import {
  savedCheckoutAddressLines,
  savedCheckoutAssistantContext,
  savedCheckoutRecipient,
  type SavedCheckoutDetailsProfile,
} from './savedCheckoutDetails'

export const SAVED_CHECKOUT_DETAILS_ERROR =
  'Could not use your saved details. Try again or enter different details.'

export function SavedCheckoutDetailsPrompt({
  details,
  busy,
  onUse,
  onManual,
  onCheckoutAssistant,
  onRefresh,
}: Readonly<{
  details: SavedCheckoutDetailsProfile
  busy: boolean
  onUse: () => void
  onManual: () => void
  onCheckoutAssistant: CheckoutAssistantHandler
  onRefresh: (checkout?: CheckoutProfile) => Promise<void> | void
}>) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const mountedRef = useRef(true)
  const disabled = busy || submitting
  const recipient = savedCheckoutRecipient(details)
  const addressLines = savedCheckoutAddressLines(details)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const useSavedDetails = async () => {
    if (disabled) return
    setSubmitting(true)
    setError(null)
    try {
      const result = await onCheckoutAssistant('', [], savedCheckoutAssistantContext(details))
      if (!result?.checkoutUpdated) {
        throw new Error(SAVED_CHECKOUT_DETAILS_ERROR)
      }
      onUse()
      await onRefresh(result.checkout)
    } catch {
      if (mountedRef.current) {
        setError(SAVED_CHECKOUT_DETAILS_ERROR)
      }
    } finally {
      if (mountedRef.current) {
        setSubmitting(false)
      }
    }
  }

  return (
    <div className="mt-checkout-saved-details">
      <div className="mt-checkout-saved-question">
        Use your saved contact and delivery details for this checkout?
      </div>
      <div className="mt-checkout-saved-summary">
        {recipient ? <strong>{recipient}</strong> : null}
        <address>
          {addressLines.map((line, index) => (
            <span key={`${index}-${line}`}>{line}</span>
          ))}
        </address>
        <span>{details.buyer.email}</span>
        {details.buyer.phoneNumber ? <span>{details.buyer.phoneNumber}</span> : null}
      </div>
      {error ? (
        <div className="mt-checkout-error" role="alert">
          {error}
        </div>
      ) : null}
      <div className="mt-checkout-saved-actions">
        <button type="button" className="primary" disabled={disabled} onClick={useSavedDetails}>
          {submitting ? 'Using saved details...' : 'Use saved details'}
        </button>
        <button type="button" disabled={disabled} onClick={onManual}>
          Enter different details
        </button>
      </div>
    </div>
  )
}
