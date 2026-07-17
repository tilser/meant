export interface ConversationPersistenceCoordinator {
  enqueue<T>(threadId: string, operation: () => Promise<T>): Promise<T> | null
  delete(threadId: string, operation: () => Promise<void>): Promise<void> | null
  isBlocked(threadId: string): boolean
}

export function createConversationPersistenceCoordinator(): ConversationPersistenceCoordinator {
  const saveTails = new Map<string, Promise<void>>()
  const blockedThreadIds = new Set<string>()

  return {
    enqueue<T>(threadId: string, operation: () => Promise<T>) {
      if (blockedThreadIds.has(threadId)) {
        return null
      }
      const result = (saveTails.get(threadId) ?? Promise.resolve()).then(operation)
      const tail = result.then(
        () => undefined,
        () => undefined,
      )
      saveTails.set(threadId, tail)
      void tail.then(() => {
        if (saveTails.get(threadId) === tail) {
          saveTails.delete(threadId)
        }
      })
      return result
    },

    delete(threadId: string, operation: () => Promise<void>) {
      if (blockedThreadIds.has(threadId)) {
        return null
      }
      blockedThreadIds.add(threadId)
      return (saveTails.get(threadId) ?? Promise.resolve())
        .then(operation)
        .catch((error: unknown) => {
          blockedThreadIds.delete(threadId)
          throw error
        })
    },

    isBlocked(threadId: string) {
      return blockedThreadIds.has(threadId)
    },
  }
}
