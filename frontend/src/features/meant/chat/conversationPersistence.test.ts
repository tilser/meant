import { describe, expect, test } from 'bun:test'

import { createConversationPersistenceCoordinator } from './conversationPersistence'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('conversation persistence coordination', () => {
  test('orders per-thread saves before delete and blocks resurrection', async () => {
    const coordinator = createConversationPersistenceCoordinator()
    const first = deferred()
    const second = deferred()
    const events: string[] = []

    const firstSave = coordinator.enqueue('thread-1', async () => {
      events.push('put-1-start')
      await first.promise
      events.push('put-1-end')
    })
    const secondSave = coordinator.enqueue('thread-1', async () => {
      events.push('put-2-start')
      await second.promise
      events.push('put-2-end')
    })
    const deletion = coordinator.delete('thread-1', async () => {
      events.push('delete')
    })

    expect(coordinator.enqueue('thread-1', async () => events.push('late-put'))).toBeNull()
    expect(events).toEqual([])
    await Promise.resolve()
    expect(events).toEqual(['put-1-start'])

    first.resolve()
    await firstSave
    await Promise.resolve()
    expect(events).toEqual(['put-1-start', 'put-1-end', 'put-2-start'])

    second.resolve()
    await Promise.all([secondSave, deletion])
    expect(events).toEqual(['put-1-start', 'put-1-end', 'put-2-start', 'put-2-end', 'delete'])
    expect(coordinator.enqueue('thread-1', async () => events.push('resurrect'))).toBeNull()
  })

  test('unblocks saves when deletion fails', async () => {
    const coordinator = createConversationPersistenceCoordinator()
    await expect(
      coordinator.delete('thread-1', async () => {
        throw new Error('delete failed')
      }),
    ).rejects.toThrow('delete failed')

    await expect(coordinator.enqueue('thread-1', async () => undefined)).resolves.toBeUndefined()
  })
})
