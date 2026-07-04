import { useEffect, useState } from 'react'

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined
    }
    const matcher = window.matchMedia(query)
    const update = () => setMatches(matcher.matches)
    update()
    matcher.addEventListener('change', update)
    return () => matcher.removeEventListener('change', update)
  }, [query])

  return matches
}
