const LEGACY_UNSCOPED_ACCOUNT_STORAGE_KEYS = [
  'meant.shelf',
  'meant.compare',
  'meant.compareProducts',
  'meant.prefsOn',
  'meant.budget',
  'meant.location',
  'meant.locations',
  'meant.clothingFit',
  'meant.user',
  'meant.cart',
  'meant.cartSnapshots',
  'meant.agentPendingCartRuns',
  'meant.agentCartProvenance',
  'meant.agentCartMessages',
  'meant.chatPinned',
  'meant.workbench.insights',
  'meant.workbench.agents',
  'meant.activeAgentConversation',
] as const

const PRODUCT_BEARING_ACCOUNT_STORAGE_KEYS = [
  'meant.shelf',
  'meant.compare',
  'meant.compareProducts',
  'meant.cart',
  'meant.cartSnapshots',
  'meant.agentPendingCartRuns',
  'meant.agentCartProvenance',
  'meant.agentCartMessages',
  'meant.chatPinned',
  'meant.workbench.insights',
  'meant.workbench.agents',
  'meant.activeAgentConversation',
] as const

const COMPARE_SESSION_STORAGE_KEYS = ['meant.compare', 'meant.compareProducts'] as const

interface RemovableStorage {
  readonly length?: number
  key?(index: number): string | null
  removeItem(key: string): void
}

export function accountStorageKey(baseKey: string, userId: string | undefined): string {
  const ownerScope = userId?.trim() ? encodeURIComponent(userId.trim()) : 'anonymous'
  return `${baseKey}.account.${ownerScope}`
}

export function accountSessionStorageKey(baseKey: string, userId: string | undefined): string {
  if (!PRODUCT_BEARING_ACCOUNT_STORAGE_KEYS.includes(baseKey as never)) {
    throw new Error(`Account storage key is not session-only: ${baseKey}`)
  }
  const version = COMPARE_SESSION_STORAGE_KEYS.includes(baseKey as never)
    ? 'buyerSafeV3'
    : 'buyerSafeV2'
  return accountStorageKey(`${baseKey}.${version}`, userId)
}

export function purgeLegacyAccountStorage(storage?: RemovableStorage): void {
  let target = storage
  if (!target && typeof window !== 'undefined') {
    try {
      target = window.localStorage
    } catch {
      return
    }
  }
  if (!target) {
    return
  }

  const keysToRemove = new Set<string>(LEGACY_UNSCOPED_ACCOUNT_STORAGE_KEYS)
  if (typeof target.length === 'number' && target.key) {
    for (let index = 0; index < target.length; index += 1) {
      const key = target.key(index)
      if (
        key &&
        PRODUCT_BEARING_ACCOUNT_STORAGE_KEYS.some((baseKey) =>
          key.startsWith(`${baseKey}.account.`),
        )
      ) {
        keysToRemove.add(key)
      }
    }
  }

  for (const key of keysToRemove) {
    try {
      target.removeItem(key)
    } catch {
      // Storage can be unavailable in privacy-restricted browsers. Scoped keys still prevent reads.
    }
  }
}
