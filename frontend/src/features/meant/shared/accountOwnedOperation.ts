export interface AccountOwnedOperation<Key> {
  readonly key: Key
  readonly ownerId: string
}

export function beginAccountOwnedOperation<Key>(
  operations: Map<Key, AccountOwnedOperation<Key>>,
  key: Key,
  ownerId: string,
): AccountOwnedOperation<Key> | null {
  if (operations.has(key)) {
    return null
  }

  const operation = { key, ownerId }
  operations.set(key, operation)
  return operation
}

export function isAccountOwnedOperationCurrent<Key>(
  operations: ReadonlyMap<Key, AccountOwnedOperation<Key>>,
  operation: AccountOwnedOperation<Key>,
  activeOwnerId: string | undefined,
): boolean {
  return activeOwnerId === operation.ownerId && operations.get(operation.key) === operation
}

export function endAccountOwnedOperation<Key>(
  operations: Map<Key, AccountOwnedOperation<Key>>,
  operation: AccountOwnedOperation<Key>,
): boolean {
  if (operations.get(operation.key) !== operation) {
    return false
  }

  operations.delete(operation.key)
  return true
}
