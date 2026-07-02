import type { UserLocation } from '../types'

export function deliveryLocationSummary(locations: readonly UserLocation[]): string {
  if (locations.length === 0) {
    return 'Anywhere'
  }
  const [primary, ...rest] = locations
  const primaryLabel = `${primary.city}, ${primary.country}`
  return rest.length > 0 ? `${primaryLabel} +${rest.length}` : primaryLabel
}
