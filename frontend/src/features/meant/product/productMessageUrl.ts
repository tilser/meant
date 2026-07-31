export function safeProductMessageUrl(value: string | null | undefined): string | null {
  if (!value) {
    return null
  }
  try {
    const url = new URL(value)
    if (!['http:', 'https:', 'mailto:'].includes(url.protocol) || url.username || url.password) {
      return null
    }
    if (url.protocol !== 'mailto:') {
      const host = url.hostname.toLocaleLowerCase()
      if (
        host === 'myshopify.com' ||
        host.endsWith('.myshopify.com') ||
        host.startsWith('mcp.') ||
        host.includes('.mcp.')
      ) {
        return null
      }
      const path = url.pathname.toLocaleLowerCase().replace(/\/+$/, '') || '/'
      if (
        ['/.well-known/ucp.json', '/.well-known/ucp', '/api/ucp/mcp', '/api/mcp', '/mcp'].some(
          (protocolPath) => path === protocolPath || path.startsWith(`${protocolPath}/`),
        )
      ) {
        return null
      }
    }
    return value
  } catch {
    return null
  }
}
