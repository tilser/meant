export interface ProvisionalPreferenceDraft {
  id: string
  preferenceIds: readonly string[]
  createdAt: number
}

export const PROVISIONAL_PREFERENCE_DRAFT_KEY = 'meant.provisionalPreferenceDraft.v1'

const MAX_PREFERENCE_IDS = 12
const MAX_DRAFT_AGE_MS = 24 * 60 * 60 * 1000

function readStored(): ProvisionalPreferenceDraft | null {
  if (typeof window === 'undefined') return null
  try {
    const value = JSON.parse(
      window.sessionStorage.getItem(PROVISIONAL_PREFERENCE_DRAFT_KEY) ?? 'null',
    ) as Partial<ProvisionalPreferenceDraft> | null
    if (
      !value ||
      typeof value.id !== 'string' ||
      typeof value.createdAt !== 'number' ||
      !Array.isArray(value.preferenceIds) ||
      value.preferenceIds.some((id) => typeof id !== 'string') ||
      Date.now() - value.createdAt > MAX_DRAFT_AGE_MS
    ) {
      window.sessionStorage.removeItem(PROVISIONAL_PREFERENCE_DRAFT_KEY)
      return null
    }
    return {
      id: value.id,
      preferenceIds: [...new Set(value.preferenceIds)].slice(0, MAX_PREFERENCE_IDS),
      createdAt: value.createdAt,
    }
  } catch {
    return null
  }
}

export function createProvisionalPreferenceDraft(
  preferenceIds: readonly string[],
): ProvisionalPreferenceDraft {
  const draft = {
    id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
    preferenceIds: [...new Set(preferenceIds.filter(Boolean))].slice(0, MAX_PREFERENCE_IDS),
    createdAt: Date.now(),
  }
  window.sessionStorage.setItem(PROVISIONAL_PREFERENCE_DRAFT_KEY, JSON.stringify(draft))
  return draft
}

export function readProvisionalPreferenceDraft(id: string): ProvisionalPreferenceDraft | null {
  const draft = readStored()
  return draft?.id === id ? draft : null
}

export function clearProvisionalPreferenceDraft(id: string): void {
  const draft = readStored()
  if (draft?.id === id) window.sessionStorage.removeItem(PROVISIONAL_PREFERENCE_DRAFT_KEY)
}
