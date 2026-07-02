import { type Dispatch, type SetStateAction, useCallback, useEffect, useRef, useState } from 'react'

import { readStorage, writeStorage } from '../utils'

interface StoredValue<T> {
  hydrated: boolean
  key: string
  value: T
}

function resolveSetStateAction<T>(action: SetStateAction<T>, current: T): T {
  return typeof action === 'function' ? (action as (previous: T) => T)(current) : action
}

export function useStoredState<T>(
  key: string,
  fallback: T,
): readonly [T, Dispatch<SetStateAction<T>>] {
  const keyRef = useRef(key)
  const fallbackRef = useRef(fallback)
  keyRef.current = key
  fallbackRef.current = fallback

  const [stored, setStored] = useState<StoredValue<T>>(() => ({
    hydrated: false,
    key,
    value: fallback,
  }))

  let current = stored
  if (stored.key !== key) {
    current = {
      hydrated: true,
      key,
      value: readStorage(key, fallbackRef.current),
    }
    setStored(current)
  }

  const setValue: Dispatch<SetStateAction<T>> = useCallback((action) => {
    setStored((previous) => {
      const currentKey = keyRef.current
      const previousValue =
        previous.key === currentKey ? previous.value : readStorage(currentKey, fallbackRef.current)

      return {
        hydrated: true,
        key: currentKey,
        value: resolveSetStateAction(action, previousValue),
      }
    })
  }, [])

  useEffect(() => {
    if (!current.hydrated) {
      const hydratedValue = readStorage(key, fallbackRef.current)
      setStored((previous) => {
        if (previous.hydrated || previous.key !== key) {
          return previous
        }

        return {
          hydrated: true,
          key,
          value: hydratedValue,
        }
      })
    }
  }, [current.hydrated, key])

  useEffect(() => {
    if (current.hydrated) {
      writeStorage(key, current.value)
    }
  }, [current.hydrated, key, current.value])

  return [current.value, setValue] as const
}
