import {
  createUserInventoryItem,
  deleteInventoryPhotoFile,
  deleteUserInventoryItem,
  type UserInventoryItemProfile,
  type UserInventoryItemUpdateInput,
  updateUserInventoryItem,
  uploadInventoryPhotoFile,
} from '../../../lib/apiClient'
import type { UserInventoryItemDraftInput } from './inventoryUtils'

export interface InventoryMutationDependencies {
  uploadPhoto: typeof uploadInventoryPhotoFile
  createItem: typeof createUserInventoryItem
  updateItem: typeof updateUserInventoryItem
  deleteItem: typeof deleteUserInventoryItem
  deletePhoto: typeof deleteInventoryPhotoFile
}

const DEFAULT_DEPENDENCIES: InventoryMutationDependencies = {
  uploadPhoto: uploadInventoryPhotoFile,
  createItem: createUserInventoryItem,
  updateItem: updateUserInventoryItem,
  deleteItem: deleteUserInventoryItem,
  deletePhoto: deleteInventoryPhotoFile,
}

function isDefinitiveApiRejection(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }
  const candidate = error as { status?: unknown }
  return (
    typeof candidate.status === 'number' &&
    candidate.status >= 400 &&
    candidate.status < 500 &&
    candidate.status !== 408
  )
}

async function deleteRejectedUpload(
  error: unknown,
  photoPath: string,
  userId: string,
  dependencies: InventoryMutationDependencies,
): Promise<void> {
  // Network, timeout, and server errors are ambiguous: the database write may have committed.
  // Only remove the upload when the API explicitly rejected the request before committing it.
  if (isDefinitiveApiRejection(error)) {
    await dependencies.deletePhoto(photoPath, { expectedUserId: userId }).catch(() => undefined)
  }
}

export async function createInventoryItemWithPhoto(
  input: {
    userId: string
    item: UserInventoryItemDraftInput
    photo: File
  },
  dependencies: InventoryMutationDependencies = DEFAULT_DEPENDENCIES,
): Promise<UserInventoryItemProfile> {
  const uploaded = await dependencies.uploadPhoto(input.userId, input.photo)
  try {
    return await dependencies.createItem(
      { ...input.item, photoPath: uploaded.path },
      { expectedUserId: input.userId },
    )
  } catch (error) {
    await deleteRejectedUpload(error, uploaded.path, input.userId, dependencies)
    throw error
  }
}

export async function updateInventoryItemWithPhoto(
  input: {
    userId: string
    existing: UserInventoryItemProfile
    item: UserInventoryItemUpdateInput
    replacementPhoto?: File
  },
  dependencies: InventoryMutationDependencies = DEFAULT_DEPENDENCIES,
): Promise<UserInventoryItemProfile> {
  if (!input.replacementPhoto) {
    return dependencies.updateItem({
      itemId: input.existing.id,
      item: input.item,
      expectedUserId: input.userId,
    })
  }

  const uploaded = await dependencies.uploadPhoto(input.userId, input.replacementPhoto)
  let updated: UserInventoryItemProfile
  try {
    updated = await dependencies.updateItem({
      itemId: input.existing.id,
      item: { ...input.item, photoPath: uploaded.path },
      expectedUserId: input.userId,
    })
  } catch (error) {
    await deleteRejectedUpload(error, uploaded.path, input.userId, dependencies)
    throw error
  }

  if (input.existing.photoPath && input.existing.photoPath !== uploaded.path) {
    await dependencies
      .deletePhoto(input.existing.photoPath, { expectedUserId: input.userId })
      .catch(() => undefined)
  }
  return updated
}

export async function deleteInventoryItemWithPhoto(
  input: { userId: string; item: UserInventoryItemProfile },
  dependencies: InventoryMutationDependencies = DEFAULT_DEPENDENCIES,
): Promise<void> {
  await dependencies.deleteItem(input.item.id, { expectedUserId: input.userId })
  // The database delete is authoritative. Storage has no cross-service transaction, so a failed
  // best-effort cleanup must not make the already-deleted item look recoverable in the UI.
  await dependencies
    .deletePhoto(input.item.photoPath, { expectedUserId: input.userId })
    .catch(() => undefined)
}
