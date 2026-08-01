const DESKTOP_SEARCH_PLACEHOLDER = 'Search with Meant - "a good cotton T-shirt under $50"'
const MOBILE_SEARCH_PLACEHOLDER = 'Search products with Meant'

export function discoverSearchPlaceholder(isPhone: boolean): string {
  return isPhone ? MOBILE_SEARCH_PLACEHOLDER : DESKTOP_SEARCH_PLACEHOLDER
}
