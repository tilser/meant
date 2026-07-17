export function productSearchPreferenceValues(draft: string): string[] {
  const unique = new Map<string, string>()
  draft
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
    .forEach((value) => {
      const key = value.toLowerCase()
      if (!unique.has(key)) {
        unique.set(key, value)
      }
    })
  return [...unique.values()]
}
