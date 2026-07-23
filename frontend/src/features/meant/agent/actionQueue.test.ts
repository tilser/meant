import { describe, expect, test } from 'bun:test'

import {
  ConcurrentAgentActionQueue,
  SerializedAgentActionQueue,
  agentActionQueueFor,
  agentTurnSubmissionQueueFor,
  commerceActionQueueFor,
} from './actionQueue'

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('agent action queues', () => {
  test('starts unrelated agent actions while an earlier action is still running', async () => {
    const queue = new ConcurrentAgentActionQueue()
    const firstStarted = deferred<void>()
    const releaseFirst = deferred<void>()
    const events: string[] = []

    const first = queue.enqueue(async () => {
      events.push('first:start')
      firstStarted.resolve()
      await releaseFirst.promise
      events.push('first:complete')
      return 'first'
    })
    await firstStarted.promise

    const second = queue.enqueue(async () => {
      events.push('second:complete')
      return 'second'
    })

    await expect(second).resolves.toBe('second')
    expect(events).toEqual(['first:start', 'second:complete'])
    expect(queue.hasPending()).toBe(true)

    releaseFirst.resolve()
    await expect(first).resolves.toBe('first')
    expect(events).toEqual(['first:start', 'second:complete', 'first:complete'])
    expect(queue.hasPending()).toBe(false)
  })

  test('returns one shared promise and result to five identical in-flight callers', async () => {
    const queue = new SerializedAgentActionQueue()
    const completion = deferred<string>()
    let executions = 0
    const action = async () => {
      executions += 1
      return completion.promise
    }

    const callers = Array.from({ length: 5 }, () => queue.enqueueUnique('add:offer-1', action))
    const first = callers[0]!

    callers.forEach((caller) => expect(caller).toBe(first))
    await Promise.resolve()
    expect(executions).toBe(1)

    completion.resolve('added once')
    await expect(Promise.all(callers)).resolves.toEqual(Array(5).fill('added once'))

    const later = queue.enqueueUnique('add:offer-1', async () => {
      executions += 1
      return 'added later'
    })
    expect(later).not.toBe(first)
    await expect(later).resolves.toBe('added later')
    expect(executions).toBe(2)
  })

  test('keeps agent, turn-admission, and commerce lanes separate per account', () => {
    const account = 'queue-lane-test-account'
    const conversation = 'conversation-1'

    expect(agentActionQueueFor(account)).toBe(agentActionQueueFor(account))
    expect(agentTurnSubmissionQueueFor(account, conversation)).toBe(
      agentTurnSubmissionQueueFor(account, conversation),
    )
    expect(agentTurnSubmissionQueueFor(account, conversation)).not.toBe(
      agentTurnSubmissionQueueFor(account, 'conversation-2'),
    )
    expect(commerceActionQueueFor(account)).toBe(commerceActionQueueFor(account))
    expect(agentActionQueueFor(account)).not.toBe(
      agentTurnSubmissionQueueFor(account, conversation),
    )
    expect(agentActionQueueFor(account)).not.toBe(commerceActionQueueFor(account))
    expect(agentActionQueueFor(account)).not.toBe(agentActionQueueFor(`${account}:other`))
  })
})
