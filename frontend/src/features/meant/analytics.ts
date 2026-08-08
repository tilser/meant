import { storedCampaignAttribution } from './campaign/campaignAttribution'

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

export const MEANT_ANALYTICS_EVENT_NAME = 'meant:analytics'

export interface MeantAnalyticsEventDetail {
  name: MeantAnalyticsEvent
  properties: Record<string, unknown>
}

const eventNames = new Set<MeantAnalyticsEvent>([
  'anonymous_session_created',
  'anonymous_session_failed',
  'campaign_brief_loaded',
  'brief_submitted',
  'agent_results_viewed',
  'agent_refinement_submitted',
  'product_opened',
  'protected_action_attempted',
  'auth_sheet_viewed',
  'auth_sheet_dismissed',
  'auth_method_selected',
  'anonymous_account_converted',
  'existing_account_signed_in',
  'guest_conversation_imported',
  'pending_action_completed',
  'pending_action_failed',
  'new_conversation_gate_viewed',
  'merchant_outbound_clicked',
  'activated_account',
])

function campaignAttributionProperties(): Record<string, string> {
  const attribution = storedCampaignAttribution()
  if (!attribution) return {}
  return Object.fromEntries(
    [
      ['utm_source', attribution.utmSource],
      ['utm_medium', attribution.utmMedium],
      ['utm_campaign', attribution.utmCampaign],
      ['utm_content', attribution.utmContent],
    ].filter((entry): entry is [string, string] => typeof entry[1] === 'string' && entry[1] !== ''),
  )
}

export function isMeantAnalyticsEventDetail(value: unknown): value is MeantAnalyticsEventDetail {
  if (!value || typeof value !== 'object') return false
  const detail = value as Partial<MeantAnalyticsEventDetail>
  return (
    typeof detail.name === 'string' &&
    eventNames.has(detail.name as MeantAnalyticsEvent) &&
    Boolean(detail.properties) &&
    typeof detail.properties === 'object' &&
    !Array.isArray(detail.properties)
  )
}

export function trackMeantEvent(
  name: MeantAnalyticsEvent,
  properties: Record<string, unknown> = {},
) {
  if (typeof window === 'undefined') return
  const detail: MeantAnalyticsEventDetail = {
    name,
    properties: { ...properties, ...campaignAttributionProperties() },
  }
  window.dispatchEvent(new CustomEvent(MEANT_ANALYTICS_EVENT_NAME, { detail }))
}
