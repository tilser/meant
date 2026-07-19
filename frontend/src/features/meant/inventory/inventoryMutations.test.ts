import { describe, expect, test } from 'bun:test'

import type { UserInventoryItemProfile } from '../../../lib/apiClient'
import {
  createInventoryItemWithPhoto,
  deleteInventoryItemWithPhoto,
  type InventoryMutationDependencies,
  updateInventoryItemWithPhoto,
} from './inventoryMutations'

const item: UserInventoryItemProfile = {
  id: 'item-1',
  source: 'MANUAL',
  sourceProductKey: null,
  productHash: null,
  name: 'Blue shirt',
  brand: null,
  category: 'APPAREL',
  description: null,
  photoPath: 'user-1/old.jpg',
  imageUrl: null,
  productUrl: null,
  photoUrl: null,
  size: null,
  color: 'Blue',
  material: null,
  quantity: 1,
  unit: null,
  location: null,
  notes: null,
  attributes: [],
  consumable: false,
  restockEnabled: false,
  restockThreshold: null,
  purchasedOn: null,
  purchasedAt: null,
  createdAt: '2026-07-19T12:00:00Z',
  updatedAt: '2026-07-19T12:00:00Z',
}

function dependencies(
  overrides: Partial<InventoryMutationDependencies> = {},
): InventoryMutationDependencies {
  return {
    uploadPhoto: async () => ({ path: 'user-1/new.jpg' }),
    createItem: async () => item,
    updateItem: async () => ({ ...item, photoPath: 'user-1/new.jpg' }),
    deleteItem: async () => undefined,
    deletePhoto: async () => undefined,
    ...overrides,
  }
}

describe('inventory photo mutation lifecycle', () => {
  test('uploads before create and retains the committed photo', async () => {
    const events: string[] = []
    let submittedPhotoPath: string | undefined
    const result = await createInventoryItemWithPhoto(
      {
        userId: 'user-1',
        item: { name: 'Blue shirt', category: 'APPAREL' },
        photo: new File(['photo'], 'shirt.jpg', { type: 'image/jpeg' }),
      },
      dependencies({
        uploadPhoto: async () => {
          events.push('upload-new')
          return { path: 'user-1/new.jpg' }
        },
        createItem: async (input) => {
          events.push('create')
          submittedPhotoPath = input.photoPath
          return item
        },
        deletePhoto: async () => {
          events.push('delete')
        },
      }),
    )

    expect(result).toBe(item)
    expect(submittedPhotoPath).toBe('user-1/new.jpg')
    expect(events).toEqual(['upload-new', 'create'])
  })

  test('cleans up the new photo when create fails', async () => {
    const deleted: Array<string | null | undefined> = []
    await expect(
      createInventoryItemWithPhoto(
        {
          userId: 'user-1',
          item: { name: 'Blue shirt', category: 'APPAREL' },
          photo: new File(['photo'], 'shirt.jpg', { type: 'image/jpeg' }),
        },
        dependencies({
          createItem: async () => {
            throw Object.assign(new Error('create failed'), { status: 400 })
          },
          deletePhoto: async (path) => {
            deleted.push(path)
          },
        }),
      ),
    ).rejects.toThrow('create failed')

    expect(deleted).toEqual(['user-1/new.jpg'])
  })

  test('retains the uploaded photo when the create result is ambiguous', async () => {
    const deleted: Array<string | null | undefined> = []
    await expect(
      createInventoryItemWithPhoto(
        {
          userId: 'user-1',
          item: { name: 'Blue shirt', category: 'APPAREL' },
          photo: new File(['photo'], 'shirt.jpg', { type: 'image/jpeg' }),
        },
        dependencies({
          createItem: async () => {
            throw new Error('connection lost')
          },
          deletePhoto: async (path) => {
            deleted.push(path)
          },
        }),
      ),
    ).rejects.toThrow('connection lost')

    expect(deleted).toEqual([])
  })

  test('uploads replacement, patches its path, then deletes the old photo', async () => {
    const events: string[] = []
    let patchedPhotoPath: string | null | undefined
    const updated = { ...item, photoPath: 'user-1/new.jpg' }
    const result = await updateInventoryItemWithPhoto(
      {
        userId: 'user-1',
        existing: item,
        item: { color: 'Navy' },
        replacementPhoto: new File(['new photo'], 'shirt.webp', { type: 'image/webp' }),
      },
      dependencies({
        uploadPhoto: async () => {
          events.push('upload-new')
          return { path: 'user-1/new.jpg' }
        },
        updateItem: async (input) => {
          events.push('patch')
          patchedPhotoPath = input.item.photoPath
          return updated
        },
        deletePhoto: async (path) => {
          events.push(`delete:${path}`)
        },
      }),
    )

    expect(result).toBe(updated)
    expect(patchedPhotoPath).toBe('user-1/new.jpg')
    expect(events).toEqual(['upload-new', 'patch', 'delete:user-1/old.jpg'])
  })

  test('deletes only the new photo when replacement patch fails', async () => {
    const deleted: Array<string | null | undefined> = []
    await expect(
      updateInventoryItemWithPhoto(
        {
          userId: 'user-1',
          existing: item,
          item: { color: 'Navy' },
          replacementPhoto: new File(['new photo'], 'shirt.webp', { type: 'image/webp' }),
        },
        dependencies({
          updateItem: async () => {
            throw Object.assign(new Error('patch failed'), { status: 400 })
          },
          deletePhoto: async (path) => {
            deleted.push(path)
          },
        }),
      ),
    ).rejects.toThrow('patch failed')

    expect(deleted).toEqual(['user-1/new.jpg'])
  })

  test('retains a replacement photo when the patch result is ambiguous', async () => {
    const deleted: Array<string | null | undefined> = []
    await expect(
      updateInventoryItemWithPhoto(
        {
          userId: 'user-1',
          existing: item,
          item: { color: 'Navy' },
          replacementPhoto: new File(['new photo'], 'shirt.webp', { type: 'image/webp' }),
        },
        dependencies({
          updateItem: async () => {
            throw Object.assign(new Error('server failed'), { status: 500 })
          },
          deletePhoto: async (path) => {
            deleted.push(path)
          },
        }),
      ),
    ).rejects.toThrow('server failed')

    expect(deleted).toEqual([])
  })

  test('deletes the item before its photo and tolerates storage cleanup failure', async () => {
    const events: string[] = []
    await expect(
      deleteInventoryItemWithPhoto(
        { userId: 'user-1', item },
        dependencies({
          deleteItem: async () => {
            events.push('delete-item')
          },
          deletePhoto: async () => {
            events.push('delete-photo')
            throw new Error('storage unavailable')
          },
        }),
      ),
    ).resolves.toBeUndefined()

    expect(events).toEqual(['delete-item', 'delete-photo'])
  })

  test('does not delete the photo when item deletion fails', async () => {
    const events: string[] = []
    await expect(
      deleteInventoryItemWithPhoto(
        { userId: 'user-1', item },
        dependencies({
          deleteItem: async () => {
            events.push('delete-item')
            throw new Error('delete failed')
          },
          deletePhoto: async () => {
            events.push('delete-photo')
          },
        }),
      ),
    ).rejects.toThrow('delete failed')

    expect(events).toEqual(['delete-item'])
  })
})
