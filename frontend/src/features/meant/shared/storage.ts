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
  const [hydratedKey, setHydratedKey] = useState<string | null>(null)

  useEffect(() => {
    setValue(readStorage(key, fallbackRef.current))
    setHydratedKey(key)
  }, [key])

  useEffect(() => {
    if (hydratedKey === key) {
      writeStorage(key, value)
    }
  }, [hydratedKey, key, value])

  return [value, setValue] as const
}
