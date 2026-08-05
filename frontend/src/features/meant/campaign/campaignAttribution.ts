export const CAMPAIGN_ATTRIBUTION_KEY = 'meant.campaignAttribution.v1'
export const CAMPAIGN_BRIEF_KEY = 'meant.campaignBrief.v1'
const CAMPAIGN_BRIEF_LOADED_EVENT_KEY = 'meant.campaignBriefLoaded.v1'
export const CAMPAIGN_BRIEF_MAX_LENGTH = 1000

export interface CampaignAttribution {
  utmSource: string | null
  utmMedium: string | null
  utmCampaign: string | null
  utmContent: string | null
}

export interface CampaignEntry {
  brief: string
  attribution: CampaignAttribution
}

const clean = (value: string | null, maximum: number) => {
  const normalized = value
    ? [...value]
        .filter((character) => {
          const code = character.charCodeAt(0)
          return code >= 32 && code !== 127
        })
        .join('')
        .trim()
    : ''
  return normalized ? normalized.slice(0, maximum) : null
}

export function captureCampaignEntry(
  location: Pick<Location, 'pathname' | 'search'>,
): CampaignEntry {
  const parameters = new URLSearchParams(location.search)
  const brief = clean(parameters.get('brief'), CAMPAIGN_BRIEF_MAX_LENGTH) ?? ''
  const attribution: CampaignAttribution = {
    utmSource: clean(parameters.get('utm_source'), 200),
    utmMedium: clean(parameters.get('utm_medium'), 200),
    utmCampaign: clean(parameters.get('utm_campaign'), 200),
    utmContent: clean(parameters.get('utm_content'), 200),
  }
  if (Object.values(attribution).some(Boolean)) {
    window.sessionStorage.setItem(CAMPAIGN_ATTRIBUTION_KEY, JSON.stringify(attribution))
  }
  if (brief) window.sessionStorage.setItem(CAMPAIGN_BRIEF_KEY, brief)
  for (const name of ['brief', 'utm_source', 'utm_medium', 'utm_campaign', 'utm_content']) {
    parameters.delete(name)
  }
  const search = parameters.toString()
  window.history.replaceState(
    {},
    document.title,
    `${location.pathname}${search ? `?${search}` : ''}`,
  )
  return { brief: brief || window.sessionStorage.getItem(CAMPAIGN_BRIEF_KEY) || '', attribution }
}

export function clearStoredCampaignBrief(): void {
  window.sessionStorage.removeItem(CAMPAIGN_BRIEF_KEY)
}

export function claimCampaignBriefLoaded(brief: string): boolean {
  if (!brief || window.sessionStorage.getItem(CAMPAIGN_BRIEF_LOADED_EVENT_KEY) === brief)
    return false
  window.sessionStorage.setItem(CAMPAIGN_BRIEF_LOADED_EVENT_KEY, brief)
  return true
}

export function storedCampaignAttribution(): CampaignAttribution | null {
  try {
    return JSON.parse(window.sessionStorage.getItem(CAMPAIGN_ATTRIBUTION_KEY) ?? 'null')
  } catch {
    return null
  }
}
