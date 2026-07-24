export const DEFAULT_MERCHANT_DISPLAY = 'Merchant'

function trustedMerchantOrigin(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null
  try {
    const url = new URL(
      /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`,
    )
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      (url.pathname !== '' && url.pathname !== '/')
    ) {
      return null
    }
    const host = url.hostname.toLocaleLowerCase()
    if (host.startsWith('mcp.') || host.includes('.mcp.')) return null
    return url.port ? `${host}:${url.port}` : host
  } catch {
    return null
  }
}

function isTechnicalMerchantLabel(value: string): boolean {
  const normalized = value.trim().toLocaleLowerCase()
  return (
    /^[a-z][a-z0-9+.-]*:\/\//.test(normalized) ||
    normalized.startsWith('//') ||
    /(^|[./])[^/\s]*\.myshopify\.com(?:[/:?#]|$)/.test(normalized) ||
    /(^|[./])mcp(?:[./?:#]|$)/.test(normalized)
  )
}

export function merchantDisplayOrigin(merchantOrigin?: string | null): string {
  return trustedMerchantOrigin(merchantOrigin) ?? DEFAULT_MERCHANT_DISPLAY
}

export function merchantAdjacentDisplayLabel(
  label: string | null | undefined,
  merchantOrigin?: string | null,
): string {
  const trimmed = label?.trim()
  return trimmed && !isTechnicalMerchantLabel(trimmed)
    ? trimmed
    : merchantDisplayOrigin(merchantOrigin)
}

export function merchantAdjacentEditableLabel(label: string | null | undefined): string {
  const trimmed = label?.trim()
  return trimmed && !isTechnicalMerchantLabel(trimmed) ? trimmed : ''
}

export function merchantOriginFromItems(
  items: readonly { merchantOrigin?: string | null }[],
): string | null {
  return (
    items
      .map((item) => item.merchantOrigin?.trim())
      .find((merchantOrigin): merchantOrigin is string => Boolean(merchantOrigin)) ?? null
  )
}
