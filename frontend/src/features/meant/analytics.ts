export type MeantAnalyticsEvent =
  | 'anonymous_session_created'
  | 'anonymous_session_failed'
  | 'campaign_brief_loaded'
  | 'brief_submitted'
  | 'agent_results_viewed'
  | 'agent_refinement_submitted'
  | 'product_opened'
  | 'protected_action_attempted'
  | 'auth_sheet_viewed'
  | 'auth_sheet_dismissed'
  | 'auth_method_selected'
  | 'anonymous_account_converted'
  | 'existing_account_signed_in'
  | 'guest_conversation_imported'
  | 'pending_action_completed'
  | 'pending_action_failed'
  | 'new_conversation_gate_viewed'
  | 'merchant_outbound_clicked'
  | 'activated_account'

export function trackMeantEvent(
  name: MeantAnalyticsEvent,
  properties: Record<string, unknown> = {},
) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('meant:analytics', { detail: { name, properties } }))
}
