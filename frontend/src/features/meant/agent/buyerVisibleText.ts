const NEUTRAL_MERCHANT = 'the merchant'
const TECHNICAL_HOST_SOURCE =
  '(?:(?:[a-z0-9-]+\\.)*[a-z0-9-]+\\.myshopify\\.com|(?:[a-z0-9-]+\\.)*mcp(?:\\.[a-z0-9-]+)+)'
const URL_TAIL_SOURCE = `(?:/[^\\s<>{}\\[\\]"']*)?`
const TECHNICAL_URL_PATTERN = new RegExp(
  `(^|[^a-z0-9.-])(https?://${TECHNICAL_HOST_SOURCE}(?::\\d{1,5})?${URL_TAIL_SOURCE})`,
  'giu',
)
const TECHNICAL_HOST_PATTERN = new RegExp(
  `(^|[^a-z0-9.-])(${TECHNICAL_HOST_SOURCE}(?::\\d{1,5})?)(?![a-z0-9-]|\\.[a-z0-9-])`,
  'giu',
)
const PROTOCOL_PATH_SOURCE =
  '(?:/\\.well-known/ucp\\.json|/\\.well-known/ucp|/api/ucp/mcp|/api/mcp|/mcp)'
const PROTOCOL_URL_PATTERN = new RegExp(
  `(^|[^a-z0-9.-])((?:https?://)?(?:[a-z0-9-]+\\.)+[a-z0-9-]+(?::\\d{1,5})?${PROTOCOL_PATH_SOURCE}(?=$|[/\\\\?#\\s<>{}\\[\\]"'.,;!:)}])(?:[/\\\\?#][^\\s<>{}\\[\\]"']*)?)`,
  'giu',
)
const STANDALONE_PROTOCOL_PATH_PATTERN = new RegExp(
  `(^|[^a-z0-9_./-])(${PROTOCOL_PATH_SOURCE}(?=$|[/\\\\?#\\s<>{}\\[\\]"'.,;!:)}])(?:[/\\\\?#][^\\s<>{}\\[\\]"']*)?)`,
  'giu',
)
const TRAILING_PUNCTUATION = /[.,;!?:)\]}]$/

function replaceTechnicalReferences(value: string, pattern: RegExp, replacement: string): string {
  return value.replace(
    pattern,
    (_match: string, prefix: string, technicalReference: string): string => {
      let reference = technicalReference
      let trailing = ''
      while (TRAILING_PUNCTUATION.test(reference)) {
        trailing = reference.slice(-1) + trailing
        reference = reference.slice(0, -1)
      }
      return `${prefix}${replacement}${trailing}`
    },
  )
}

function sanitizeWithReplacement(value: string, replacement: string): string {
  const withoutTechnicalUrls = replaceTechnicalReferences(value, TECHNICAL_URL_PATTERN, replacement)
  const withoutProtocolUrls = replaceTechnicalReferences(
    withoutTechnicalUrls,
    PROTOCOL_URL_PATTERN,
    replacement,
  )
  const withoutProtocolPaths = replaceTechnicalReferences(
    withoutProtocolUrls,
    STANDALONE_PROTOCOL_PATH_PATTERN,
    replacement,
  )
  return replaceTechnicalReferences(withoutProtocolPaths, TECHNICAL_HOST_PATTERN, replacement)
}

function buyerVisibleMerchantOrigin(merchantOrigin?: string | null): string {
  const trimmed = merchantOrigin?.trim()
  if (!trimmed) return NEUTRAL_MERCHANT
  try {
    const url = new URL(
      /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`,
    )
    const host = url.hostname.toLocaleLowerCase()
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      (url.pathname !== '' && url.pathname !== '/') ||
      host.startsWith('mcp.') ||
      host.includes('.mcp.')
    ) {
      return NEUTRAL_MERCHANT
    }
    return url.port ? `${host}:${url.port}` : host
  } catch {
    return NEUTRAL_MERCHANT
  }
}

/** Neutralizes transport coordinates without replacing the surrounding buyer-facing sentence. */
export function sanitizeBuyerVisibleText(value: string, merchantOrigin?: string | null): string {
  return sanitizeWithReplacement(value, buyerVisibleMerchantOrigin(merchantOrigin))
}

export function sanitizeBuyerVisibleValue(value: unknown): unknown {
  if (typeof value === 'string') {
    return sanitizeBuyerVisibleText(value)
  }
  if (Array.isArray(value)) {
    return value.map(sanitizeBuyerVisibleValue)
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [key, sanitizeBuyerVisibleValue(child)]),
    )
  }
  return value
}

export function sanitizeBuyerVisibleJson(value: string | null): string | null {
  if (value === null || value.length === 0) return value
  try {
    return JSON.stringify(sanitizeBuyerVisibleValue(JSON.parse(value) as unknown))
  } catch {
    return sanitizeBuyerVisibleText(value)
  }
}
