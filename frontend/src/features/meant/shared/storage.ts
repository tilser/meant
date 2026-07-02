import {
  type Dispatch,
  type SetStateAction,
  useEffect,
  useRef,
  useState,
} from 'react'

import { readStorage, writeStorage } from '../utils'

export function useStoredState<T>(
  key: string,
  fallback: T,
): readonly [T, Dispatch<SetStateAction<T>>] {
  const fallbackRef = useRef(fallback)
  const [value, setValue] = useState<T>(fallback)
  const [hydrated, setHydrated] = useState(false)

  useEffect(() => {
    setValue(readStorage(key, fallbackRef.current))
    setHydrated(true)
  }, [key])

  useEffect(() => {
    if (hydrated) {
      writeStorage(key, value)
    }
  }, [hydrated, key, value])

  return [value, setValue] as const
}
