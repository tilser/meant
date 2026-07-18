/** Closed by default until the complete Phase 2 acceptance suite passes. */
export function isAgenticDiscoverEnabled(
  rawValue: string | undefined = import.meta.env.VITE_AGENTIC_DISCOVER_ENABLED,
): boolean {
  return rawValue?.trim().toLowerCase() === 'true'
}
