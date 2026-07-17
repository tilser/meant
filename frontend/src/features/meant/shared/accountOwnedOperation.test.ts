import { describe, expect, test } from 'bun:test'

import {
  beginAccountOwnedOperation,
  endAccountOwnedOperation,
  isAccountOwnedOperationCurrent,
} from './accountOwnedOperation'

describe('account-owned operations', () => {
  test('a stale completion cannot mutate the next account or clear its pending operation', () => {
    const operations = new Map<string, { key: string; ownerId: string }>()
    const firstAccountOperation = beginAccountOwnedOperation(operations, 'product-1', 'user-a')
    expect(firstAccountOperation).not.toBeNull()
    if (!firstAccountOperation) {
      throw new Error('Expected the first operation to start')
    }

    operations.clear()
    const nextAccountOperation = beginAccountOwnedOperation(operations, 'product-1', 'user-b')
    expect(nextAccountOperation).not.toBeNull()
    if (!nextAccountOperation) {
      throw new Error('Expected the next account operation to start')
    }

    expect(isAccountOwnedOperationCurrent(operations, firstAccountOperation, 'user-b')).toBeFalse()
    expect(endAccountOwnedOperation(operations, firstAccountOperation)).toBeFalse()
    expect(operations.get('product-1')).toBe(nextAccountOperation)

    expect(isAccountOwnedOperationCurrent(operations, nextAccountOperation, 'user-b')).toBeTrue()
    expect(endAccountOwnedOperation(operations, nextAccountOperation)).toBeTrue()
    expect(operations).toHaveLength(0)
  })
})
