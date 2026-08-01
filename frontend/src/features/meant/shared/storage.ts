import { type Dispatch, type SetStateAction, useCallback, useEffect, useRef, useState } from 'react'

import { readStorage, writeStorage } from '../utils'

interface StoredValue<T> {
  hydrated: boolean
  key: string
  value: T
}

type StoredStateFallback<T> = T | (() => T)
type BrowserStorageKind = 'local' | 'session'

function resolveFallback<T>(fallback: StoredStateFallback<T>): T {
  return typeof fallback === 'function' ? (fallback as () => T)() : fallback
}

function resolveSetStateAction<T>(action: SetStateAction<T>, current: T): T {
  return typeof action === 'function' ? (action as (previous: T) => T)(current) : action
}

function readSessionStorage<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') {
    return fallback
  }
  try {
    const raw = window.sessionStorage.getItem(key)
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

function writeSessionStorage<T>(key: string, value: T): void {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Session storage can be unavailable in privacy-restricted browsers.
  }
}

function readBrowserStorage<T>(kind: BrowserStorageKind, key: string, fallback: T): T {
  return kind === 'session' ? readSessionStorage(key, fallback) : readStorage(key, fallback)
}

function writeBrowserStorage<T>(kind: BrowserStorageKind, key: string, value: T): void {
  if (kind === 'session') {
    writeSessionStorage(key, value)
  } else {
    writeStorage(key, value)
  }
}

function useBrowserStoredState<T>(
  kind: BrowserStorageKind,
  key: string,
  fallback: StoredStateFallback<T>,
): readonly [T, Dispatch<SetStateAction<T>>] {
  const keyRef = useRef(key)
  const [initialFallback] = useState(() => resolveFallback(fallback))
  const fallbackRef = useRef(initialFallback)
  keyRef.current = key
  if (typeof fallback !== 'function') {
    fallbackRef.current = fallback
  }

  const [stored, setStored] = useState<StoredValue<T>>(() => {
    // Session-backed account state must be available before consumers can update the fallback.
    const hydrated = kind === 'session' && typeof window !== 'undefined'
    return {
      hydrated,
      key,
      value: hydrated ? readBrowserStorage(kind, key, initialFallback) : initialFallback,
    }
  })

  let current = stored
  if (stored.key !== key) {
    current = {
      hydrated: true,
      key,
      value: readBrowserStorage(kind, key, fallbackRef.current),
    }
    setStored(current)
  }

  const setValue: Dispatch<SetStateAction<T>> = useCallback(
    (action) => {
      setStored((previous) => {
        const currentKey = keyRef.current
        const previousValue =
          previous.key === currentKey && previous.hydrated
            ? previous.value
            : readBrowserStorage(kind, currentKey, fallbackRef.current)

        return {
          hydrated: true,
          key: currentKey,
          value: resolveSetStateAction(action, previousValue),
        }
      })
    },
    [kind],
  )

  useEffect(() => {
    if (!current.hydrated) {
      const hydratedValue = readBrowserStorage(kind, key, fallbackRef.current)
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
  }, [current.hydrated, key, kind])

  useEffect(() => {
    if (current.hydrated) {
      writeBrowserStorage(kind, key, current.value)
    }
  }, [current.hydrated, key, current.value, kind])

  return [current.value, setValue] as const
}

export function useStoredState<T>(
  key: string,
  fallback: StoredStateFallback<T>,
): readonly [T, Dispatch<SetStateAction<T>>] {
  return useBrowserStoredState('local', key, fallback)
}

export function useSessionStoredState<T>(
  key: string,
  fallback: StoredStateFallback<T>,
): readonly [T, Dispatch<SetStateAction<T>>] {
  return useBrowserStoredState('session', key, fallback)
}
