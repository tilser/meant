abstract class TrackedAgentActionQueue {
  private pendingActions = 0
  private readonly inFlightByKey = new Map<string, Promise<unknown>>()
  private readonly settledListeners = new Set<() => void>()

  enqueue<T>(action: () => Promise<T>): Promise<T> {
    this.pendingActions += 1
    const result = this.schedule(action)
    const tracked = result.finally(() => {
      this.pendingActions -= 1
      this.settledListeners.forEach((listener) => {
        try {
          listener()
        } catch {
          // A view refresh listener cannot change the commerce action outcome.
        }
      })
    })
    return tracked
  }

  enqueueUnique<T>(key: string, action: () => Promise<T>): Promise<T> {
    const existing = this.inFlightByKey.get(key)
    if (existing) return existing as Promise<T>

    const result = this.enqueue(action)
    this.inFlightByKey.set(key, result)
    void result.then(
      () => this.clearInFlight(key, result),
      () => this.clearInFlight(key, result),
    )
    return result
  }

  hasPending(): boolean {
    return this.pendingActions > 0
  }

  subscribeToSettled(listener: () => void): () => void {
    this.settledListeners.add(listener)
    return () => this.settledListeners.delete(listener)
  }

  protected abstract schedule<T>(action: () => Promise<T>): Promise<T>

  private clearInFlight<T>(key: string, result: Promise<T>): void {
    if (this.inFlightByKey.get(key) === result) {
      this.inFlightByKey.delete(key)
    }
  }
}

/** Runs independent agent work immediately while coalescing identical in-flight requests. */
export class ConcurrentAgentActionQueue extends TrackedAgentActionQueue {
  protected schedule<T>(action: () => Promise<T>): Promise<T> {
    return Promise.resolve().then(action)
  }
}

/** A failure-safe FIFO lane for commerce workflows that mutate shared cart or checkout state. */
export class SerializedAgentActionQueue extends TrackedAgentActionQueue {
  private tail: Promise<void> = Promise.resolve()

  protected schedule<T>(action: () => Promise<T>): Promise<T> {
    const result = this.tail.then(action, action)
    this.tail = result.then(
      () => undefined,
      () => undefined,
    )
    return result
  }
}

const accountAgentQueues = new Map<string, ConcurrentAgentActionQueue>()
const conversationTurnSubmissionQueues = new Map<string, SerializedAgentActionQueue>()
const accountCommerceQueues = new Map<string, SerializedAgentActionQueue>()

/** Account-scoped agent work survives navigation and remounts without blocking unrelated actions. */
export function agentActionQueueFor(accountId: string): ConcurrentAgentActionQueue {
  const existing = accountAgentQueues.get(accountId)
  if (existing) return existing
  const created = new ConcurrentAgentActionQueue()
  accountAgentQueues.set(accountId, created)
  return created
}

/** Keeps turn admission ordered per conversation without blocking another chat. */
export function agentTurnSubmissionQueueFor(
  accountId: string,
  conversationId: string,
): SerializedAgentActionQueue {
  const laneKey = JSON.stringify([accountId, conversationId])
  const existing = conversationTurnSubmissionQueues.get(laneKey)
  if (existing) return existing
  const created = new SerializedAgentActionQueue()
  conversationTurnSubmissionQueues.set(laneKey, created)
  return created
}

/** Serializes mutations that can target the same remote cart or checkout. */
export function commerceActionQueueFor(accountId: string): SerializedAgentActionQueue {
  const existing = accountCommerceQueues.get(accountId)
  if (existing) return existing
  const created = new SerializedAgentActionQueue()
  accountCommerceQueues.set(accountId, created)
  return created
}
