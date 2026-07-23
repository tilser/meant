import type { AgentRunStatusProfile } from '../../../lib/apiClient'
import { resolveLiveCartItem } from '../cart/cartPartition'
import { cartItemIdentity, cartMerchantKey } from '../utils'
import type { MerchantCartSnapshot, MerchantCartStateReplacement } from '../cart/types'
import type { CartItem, ProductId } from '../types'

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

/** Removes exactly the pending run whose durable conversation has been synchronized. */
export function settlePendingAgentCartRun(
  current: Readonly<Record<string, PendingAgentCartRun>>,
  runId: string,
  conversationId: string,
): Record<string, PendingAgentCartRun> {
  if (current[runId]?.conversationId !== conversationId) return current
  const next = { ...current }
  delete next[runId]
  return next
}

function partitionAliases(
  fingerprints: AgentCartPartitionFingerprints,
  partitionId: string,
): string[] {
  return Object.entries(fingerprints.aliases).flatMap(([alias, owner]) =>
    owner === partitionId ? [alias] : [],
  )
}

function rebaseAgentCartFingerprintsAfterReplacement(
  replacement: MerchantCartStateReplacement,
  submitted: AgentCartPartitionFingerprints,
  before: AgentCartPartitionFingerprints,
  after: AgentCartPartitionFingerprints,
): AgentCartPartitionFingerprints {
  if (!agentCartReplacementUnchangedSinceSubmission(replacement, submitted, before)) {
    return submitted
  }

  const aliases = replacementAliases(replacement)
  const submittedAlias = aliases.find((alias) => hasOwnAlias(submitted, alias))
  const submittedPartition = submittedAlias ? submitted.aliases[submittedAlias] : null
  const afterPartitions = new Set(
    aliases.flatMap((alias) => {
      const owner = after.aliases[alias]
      return owner ? [owner] : []
    }),
  )
  if (afterPartitions.size !== 1) return submitted

  const afterPartition = [...afterPartitions][0]!
  const afterRevision = after.revisions[afterPartition]
  if (!afterRevision) return submitted
  if (
    afterPartition !== submittedPartition &&
    Object.prototype.hasOwnProperty.call(submitted.revisions, afterPartition)
  ) {
    return submitted
  }

  const nextAliases = { ...submitted.aliases }
  const nextRevisions = { ...submitted.revisions }
  if (submittedPartition) {
    partitionAliases(submitted, submittedPartition).forEach((alias) => {
      delete nextAliases[alias]
    })
    delete nextRevisions[submittedPartition]
  }

  let copiedAlias = false
  partitionAliases(after, afterPartition).forEach((alias) => {
    if (!Object.prototype.hasOwnProperty.call(nextAliases, alias)) {
      nextAliases[alias] = afterPartition
      copiedAlias = true
    }
  })
  if (!copiedAlias) return submitted
  nextRevisions[afterPartition] = afterRevision
  return { aliases: nextAliases, revisions: nextRevisions }
}

/**
 * Advances only safely matching merchant partitions for later FIFO runs in
 * the same conversation after an authoritative replacement is applied.
 */
export function rebasePendingAgentCartRunsAfterReplacements(
  current: Readonly<Record<string, PendingAgentCartRun>>,
  settledRunId: string,
  conversationId: string,
  replacements: readonly MerchantCartStateReplacement[],
  before: AgentCartPartitionFingerprints,
  after: AgentCartPartitionFingerprints,
): Record<string, PendingAgentCartRun> {
  if (replacements.length === 0) return current
  let changed = false
  let settledRunSeen = false
  const next = Object.fromEntries(
    Object.entries(current).map(([runId, pendingRun]) => {
      if (runId === settledRunId && pendingRun.conversationId === conversationId) {
        settledRunSeen = true
        return [runId, pendingRun]
      }
      if (!settledRunSeen || pendingRun.conversationId !== conversationId) {
        return [runId, pendingRun]
      }
      let fingerprints = pendingRun.cartFingerprints
      if (fingerprints) {
        replacements.forEach((replacement) => {
          if (!fingerprints) return
          const rebased = rebaseAgentCartFingerprintsAfterReplacement(
            replacement,
            fingerprints,
            before,
            after,
          )
          if (rebased !== fingerprints) {
            changed = true
            fingerprints = rebased
          }
        })
      }
      return [
        runId,
        fingerprints === pendingRun.cartFingerprints
          ? pendingRun
          : { ...pendingRun, cartFingerprints: fingerprints },
      ]
    }),
  )
  return changed ? next : (current as Record<string, PendingAgentCartRun>)
}

/** Records only the partitions that were actually replaced from an agent result. */
export function agentCartProvenanceAfterReplacements(
  current: AgentCartPartitionFingerprints | null | undefined,
  replacements: readonly MerchantCartStateReplacement[],
  after: AgentCartPartitionFingerprints,
): AgentCartPartitionFingerprints {
  const aliases = { ...(current?.aliases ?? {}) }
  const revisions = { ...(current?.revisions ?? {}) }
  replacements.forEach((replacement) => {
    const replacementAliasList = replacementAliases(replacement)
    const replacedPartitions = new Set(
      replacementAliasList.flatMap((alias) => {
        const owner = aliases[alias]
        return owner ? [owner] : []
      }),
    )
    replacedPartitions.forEach((partitionId) => {
      Object.entries(aliases).forEach(([alias, owner]) => {
        if (owner === partitionId) delete aliases[alias]
      })
      delete revisions[partitionId]
    })

    const afterPartitions = new Set(
      replacementAliasList.flatMap((alias) => {
        const owner = after.aliases[alias]
        return owner ? [owner] : []
      }),
    )
    if (afterPartitions.size !== 1) return
    const afterPartition = [...afterPartitions][0]!
    const afterRevision = after.revisions[afterPartition]
    if (!afterRevision) return
    partitionAliases(after, afterPartition).forEach((alias) => {
      aliases[alias] = afterPartition
    })
    revisions[afterPartition] = afterRevision
  })
  return { aliases, revisions }
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

/**
 * Detects an active local cart for the same merchant scope under a different
 * cart identity. This lets an older inactive cart read settle without wiping
 * the replacement cart.
 */
export function agentCartReplacementSupersededByCurrentPartition(
  replacement: MerchantCartStateReplacement,
  current: AgentCartPartitionFingerprints,
): boolean {
  const cartAliases = uniqueAliases([
    identityAlias('cart', replacement.snapshot.cartId),
    identityAlias('remote-cart', replacement.snapshot.remoteCartId),
  ])
  const stableAliases = replacementAliases(replacement).filter(
    (alias) => !alias.startsWith('cart:') && !alias.startsWith('remote-cart:'),
  )
  const currentPartitions = new Set(
    stableAliases.flatMap((alias) => {
      const owner = current.aliases[alias]
      return owner ? [owner] : []
    }),
  )
  if (currentPartitions.size !== 1) return false
  const currentPartition = [...currentPartitions][0]!
  return (
    cartAliases.length > 0 &&
    cartAliases.every((alias) => current.aliases[alias] !== currentPartition)
  )
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

export interface OptimisticAgentCartQuantityChange {
  cart: CartItem[]
  identity: string
  quantity: number
  target: CartItem
}

/**
 * Applies a cart-card intent to the current live cart immediately. Historical cards express
 * quantity controls as deltas, so a stale displayed quantity can never overwrite the live value.
 */
export function optimisticAgentCartQuantityChange(
  cart: readonly CartItem[],
  id: ProductId,
  merchant: string,
  quantity: number,
  identity?: string,
  quantityDelta?: number,
  sourceItem?: CartItem,
): OptimisticAgentCartQuantityChange | null {
  const target = sourceItem
    ? resolveLiveCartItem(cart, sourceItem, identity)
    : (() => {
        const candidates = cart.filter((line) =>
          identity
            ? cartItemIdentity(line) === identity
            : line.id === id && (line.merchant === merchant || cart.length === 1),
        )
        return candidates.length === 1 ? candidates[0]! : null
      })()
  if (!target) return null

  const targetIdentity = cartItemIdentity(target)
  const nextQuantity =
    quantityDelta === undefined
      ? Math.max(0, quantity)
      : liveCartQuantityAfterDelta(target.qty, quantityDelta)
  const nextCart =
    nextQuantity <= 0
      ? cart.filter((line) => cartItemIdentity(line) !== targetIdentity)
      : cart.map((line) =>
          cartItemIdentity(line) === targetIdentity
            ? { ...line, qty: nextQuantity, syncing: true, syncError: null }
            : line,
        )

  return {
    cart: nextCart,
    identity: targetIdentity,
    quantity: nextQuantity,
    target,
  }
}
