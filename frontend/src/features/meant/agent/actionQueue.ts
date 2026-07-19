/** A failure-safe FIFO lane for direct commerce actions and atomic workflows. */
export class SerializedAgentActionQueue {
  private tail: Promise<void> = Promise.resolve()
  private pendingActions = 0
  private readonly uniqueKeys = new Set<string>()
  private readonly settledListeners = new Set<() => void>()

  enqueue<T>(action: () => Promise<T>): Promise<T> {
    this.pendingActions += 1
    const result = this.tail.then(action, action)
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
    this.tail = tracked.then(
      () => undefined,
      () => undefined,
    )
    return tracked
  }

  enqueueUnique<T>(key: string, action: () => Promise<T>): Promise<T | null> {
    if (this.uniqueKeys.has(key)) return Promise.resolve(null)
    this.uniqueKeys.add(key)
    return this.enqueue(action).finally(() => this.uniqueKeys.delete(key))
  }

  hasPending(): boolean {
    return this.pendingActions > 0
  }

  subscribeToSettled(listener: () => void): () => void {
    this.settledListeners.add(listener)
    return () => this.settledListeners.delete(listener)
  }
}

const accountQueues = new Map<string, SerializedAgentActionQueue>()

/** Account-scoped queues survive discover-view navigation and remounts. */
export function agentActionQueueFor(accountId: string): SerializedAgentActionQueue {
  const existing = accountQueues.get(accountId)
  if (existing) return existing
  const created = new SerializedAgentActionQueue()
  accountQueues.set(accountId, created)
  return created
}
