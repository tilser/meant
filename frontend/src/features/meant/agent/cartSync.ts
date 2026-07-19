import type { AgentRunStatusProfile } from '../../../lib/apiClient'
import { cartMerchantKey } from '../utils'
import type { MerchantCartSnapshot, MerchantCartStateReplacement } from '../cart/types'
import type { CartItem } from '../types'

/** Alias ownership is compact; each complete revision is serialized only once. */
export interface AgentCartPartitionFingerprints {
  aliases: Readonly<Record<string, string | null>>
  revisions: Readonly<Record<string, string>>
}

export interface PendingAgentCartRun {
  conversationId: string
  cartFingerprints?: AgentCartPartitionFingerprints
  /** Compatibility with pending runs stored by the first cart-sync rollout. */
  cartFingerprint?: string
}

export function registerPendingAgentCartRun(
  current: Readonly<Record<string, PendingAgentCartRun>>,
  runId: string,
  conversationId: string,
  cartFingerprints: AgentCartPartitionFingerprints | undefined,
): Record<string, PendingAgentCartRun> {
  if (current[runId]) return current
  return {
    ...current,
    [runId]: cartFingerprints
      ? {
          conversationId,
          cartFingerprints: {
            aliases: { ...cartFingerprints.aliases },
            revisions: { ...cartFingerprints.revisions },
          },
        }
      : { conversationId },
  }
}

export const TERMINAL_AGENT_RUN_STATUSES: ReadonlySet<AgentRunStatusProfile> = new Set([
  'WAITING_FOR_USER',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
])

export function isTerminalAgentRunStatus(status: AgentRunStatusProfile): boolean {
  return TERMINAL_AGENT_RUN_STATUSES.has(status)
}

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableValue)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => [key, stableValue(item)]),
  )
}

function normalizedIdentity(value: string | null | undefined): string | null {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function normalizedInsensitiveIdentity(value: string | null | undefined): string | null {
  return normalizedIdentity(value)?.toLowerCase() ?? null
}

function identityAlias(prefix: string, value: string | null | undefined): string | null {
  const normalized = normalizedIdentity(value)
  return normalized ? `${prefix}:${normalized}` : null
}

function providerIdentityAlias(
  prefix: string,
  provider: string | null | undefined,
  value: string | null | undefined,
): string | null {
  const normalizedValue = normalizedInsensitiveIdentity(value)
  if (!normalizedValue) return null
  return `${prefix}:${normalizedInsensitiveIdentity(provider) ?? ''}:${normalizedValue}`
}

function uniqueAliases(aliases: readonly (string | null)[]): string[] {
  return [...new Set(aliases.filter((alias): alias is string => Boolean(alias)))]
}

function cartItemAliases(item: CartItem): string[] {
  return uniqueAliases([
    identityAlias('routing', item.routingScopeKey ?? item.merchantScopeKey),
    identityAlias('integration', item.merchantIntegrationId),
    identityAlias('merchant', item.merchantId),
    providerIdentityAlias('external', item.provider, item.externalMerchantId),
    providerIdentityAlias('domain', item.provider, item.merchantDomain),
    identityAlias('cart', item.cartId),
    identityAlias('remote-cart', item.remoteCartId),
    identityAlias('key', cartMerchantKey(item)),
  ])
}

function cartPartitionGroupKey(item: CartItem): string {
  return (
    identityAlias('cart', item.cartId) ??
    identityAlias('remote-cart', item.remoteCartId) ??
    identityAlias('routing', item.routingScopeKey ?? item.merchantScopeKey) ??
    identityAlias('integration', item.merchantIntegrationId) ??
    identityAlias('merchant', item.merchantId) ??
    providerIdentityAlias('external', item.provider, item.externalMerchantId) ??
    providerIdentityAlias('domain', item.provider, item.merchantDomain) ??
    identityAlias('key', cartMerchantKey(item)) ??
    `product:${item.id}`
  )
}

function orderedCart(cart: readonly CartItem[]): CartItem[] {
  return [...cart].sort((left, right) => {
    const leftKey = `${left.cartId ?? ''}\u0000${left.cartLineId ?? ''}\u0000${left.offerKey ?? ''}\u0000${left.id}`
    const rightKey = `${right.cartId ?? ''}\u0000${right.cartLineId ?? ''}\u0000${right.offerKey ?? ''}\u0000${right.id}`
    return leftKey.localeCompare(rightKey)
  })
}

interface CartPartition {
  id: string
  cart: CartItem[]
  snapshots: Array<readonly [string, MerchantCartSnapshot]>
  aliases: Set<string>
}

function snapshotAliases(storageKey: string, snapshot: MerchantCartSnapshot): string[] {
  return uniqueAliases([
    identityAlias('cart', snapshot.cartId),
    identityAlias('remote-cart', snapshot.remoteCartId),
    identityAlias('key', storageKey),
    identityAlias('key', snapshot.merchantKey),
  ])
}

function fingerprintPartition(partition: CartPartition): string {
  const snapshots = [...partition.snapshots].sort(([left], [right]) => left.localeCompare(right))
  return JSON.stringify(stableValue({ cart: orderedCart(partition.cart), snapshots }))
}

/** Captures independent local revisions for each merchant cart partition. */
export function agentCartPartitionFingerprints(
  cart: readonly CartItem[],
  snapshots: Readonly<Record<string, MerchantCartSnapshot>>,
): AgentCartPartitionFingerprints {
  const partitions = new Map<string, CartPartition>()
  cart.forEach((item) => {
    const id = cartPartitionGroupKey(item)
    const partition = partitions.get(id) ?? {
      id,
      cart: [],
      snapshots: [],
      aliases: new Set<string>(),
    }
    partition.cart.push(item)
    cartItemAliases(item).forEach((alias) => partition.aliases.add(alias))
    partitions.set(id, partition)
  })

  Object.entries(snapshots).forEach(([storageKey, snapshot], snapshotIndex) => {
    const aliases = snapshotAliases(storageKey, snapshot)
    const cartAliases = uniqueAliases([
      identityAlias('cart', snapshot.cartId),
      identityAlias('remote-cart', snapshot.remoteCartId),
    ])
    const keyAliases = aliases.filter((alias) => !cartAliases.includes(alias))
    const exactCartPartitions = [...partitions.values()].filter((partition) =>
      cartAliases.some((alias) => partition.aliases.has(alias)),
    )
    const keyMatchingPartitions = [...partitions.values()].filter((partition) =>
      keyAliases.some((alias) => partition.aliases.has(alias)),
    )
    const matchingPartitions =
      exactCartPartitions.length > 0 ? exactCartPartitions : keyMatchingPartitions
    const partition =
      matchingPartitions.length === 1
        ? matchingPartitions[0]!
        : {
            id: `snapshot:${storageKey}:${snapshotIndex}`,
            cart: [],
            snapshots: [],
            aliases: new Set<string>(),
          }
    partition.snapshots.push([storageKey, snapshot])
    aliases.forEach((alias) => partition.aliases.add(alias))
    partitions.set(partition.id, partition)
  })

  const aliases: Record<string, string | null> = {}
  const revisions: Record<string, string> = {}
  const aliasOwners = new Map<string, string>()
  partitions.forEach((partition) => {
    revisions[partition.id] = fingerprintPartition(partition)
    partition.aliases.forEach((alias) => {
      const owner = aliasOwners.get(alias)
      if (owner && owner !== partition.id) {
        aliases[alias] = null
        return
      }
      aliasOwners.set(alias, partition.id)
      aliases[alias] = partition.id
    })
  })
  return { aliases, revisions }
}

function replacementAliases(replacement: MerchantCartStateReplacement): string[] {
  return uniqueAliases([
    identityAlias('routing', replacement.routingScopeKey),
    identityAlias('integration', replacement.merchantIntegrationId),
    identityAlias('merchant', replacement.merchantId),
    providerIdentityAlias('external', replacement.provider, replacement.externalMerchantId),
    providerIdentityAlias('domain', replacement.provider, replacement.merchantDomain),
    ...replacement.lines.flatMap(cartItemAliases),
    identityAlias('cart', replacement.snapshot.cartId),
    identityAlias('remote-cart', replacement.snapshot.remoteCartId),
    identityAlias('key', replacement.merchantKey),
    identityAlias('key', replacement.snapshot.merchantKey),
  ])
}

function hasOwnAlias(fingerprints: AgentCartPartitionFingerprints, alias: string): boolean {
  return Object.prototype.hasOwnProperty.call(fingerprints.aliases, alias)
}

function isStringRecord(value: unknown): value is Readonly<Record<string, string | null>> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

/**
 * Returns true only when the merchant partition targeted by a delayed agent
 * replacement still has the revision captured when the run was submitted.
 */
export function agentCartReplacementUnchangedSinceSubmission(
  replacement: MerchantCartStateReplacement,
  submitted: AgentCartPartitionFingerprints | undefined,
  current: AgentCartPartitionFingerprints,
): boolean {
  // Pending runs live in session storage, so tolerate the short-lived flat
  // fingerprint shape written by an earlier rollout and fail closed.
  if (!submitted || !isStringRecord(submitted.aliases) || !isStringRecord(submitted.revisions)) {
    return false
  }
  const aliases = replacementAliases(replacement)
  if (aliases.length === 0) return false
  const submittedAlias = aliases.find((alias) => hasOwnAlias(submitted, alias))
  if (submittedAlias) {
    const submittedPartition = submitted.aliases[submittedAlias]
    if (!submittedPartition) return false
    const currentPartition = current.aliases[submittedAlias]
    if (!currentPartition) return false
    const submittedRevision = submitted.revisions[submittedPartition]
    const currentRevision = current.revisions[currentPartition]
    return Boolean(submittedRevision && currentRevision && submittedRevision === currentRevision)
  }
  return !aliases.some((alias) => hasOwnAlias(current, alias))
}

/** Whole-cart compatibility guard for pending runs written by the previous local schema. */
export function agentCartStateFingerprint(
  cart: readonly CartItem[],
  snapshots: Readonly<Record<string, MerchantCartSnapshot>>,
): string {
  return JSON.stringify(stableValue({ cart: orderedCart(cart), snapshots }))
}

/** Historical rows express intent as a delta; current server-owned quantity remains authoritative. */
export function liveCartQuantityAfterDelta(currentQuantity: number, delta: number): number {
  return Math.max(0, currentQuantity + delta)
}
