import type {
  UserInventoryCategory,
  UserInventoryItemInput,
  UserInventoryItemProfile,
  UserInventoryItemUpdateInput,
  UserInventoryPhotoInput,
  UserInventorySelectedOptionProfile,
} from '../../../lib/apiClient'

export interface InventoryFormState {
  name: string
  brand: string
  category: UserInventoryCategory
  description: string
  imageUrl: string
  productUrl: string
  photoUrl: string
  quantity: string
  unit: string
  location: string
  notes: string
  attributes: string
  consumable: boolean
  restockEnabled: boolean
  restockThreshold: string
}

export const INVENTORY_CATEGORIES: readonly UserInventoryCategory[] = [
  'APPAREL',
  'PANTRY',
  'HOME',
  'OTHER',
]

const INVENTORY_CATEGORY_LABELS: Readonly<Record<UserInventoryCategory, string>> = {
  APPAREL: 'Wardrobe',
  PANTRY: 'Pantry',
  HOME: 'Home',
  OTHER: 'Other',
}

const INVENTORY_SOURCE_LABELS: Readonly<Record<UserInventoryItemProfile['source'], string>> = {
  MANUAL: 'Manual',
  PHOTO: 'Photo',
  MEANT_PURCHASE: 'Meant purchase',
}

const INVENTORY_PHOTO_DATA_URL_LIMIT = 1_900_000

export function inventoryCategoryLabel(category: UserInventoryCategory): string {
  return INVENTORY_CATEGORY_LABELS[category] ?? INVENTORY_CATEGORY_LABELS.OTHER
}

export function inventorySourceLabel(source: UserInventoryItemProfile['source']): string {
  return INVENTORY_SOURCE_LABELS[source] ?? source
}

export function inventoryItemImage(item: UserInventoryItemProfile): string | null {
  return item.imageUrl || item.photoUrl
}

export function inventorySelectedOptionLabel(
  option: UserInventorySelectedOptionProfile,
): string | null {
  const name = option.name?.trim()
  const value = option.value?.trim()
  if (!name || !value) return null
  const group = option.group?.trim()
  return group ? `${group} · ${name}: ${value}` : `${name}: ${value}`
}

export function upsertInventorySnapshot(
  items: readonly UserInventoryItemProfile[],
  item: UserInventoryItemProfile,
): UserInventoryItemProfile[] {
  const existing = items.some((candidate) => candidate.id === item.id)
    ? items.map((candidate) => (candidate.id === item.id ? item : candidate))
    : [item, ...items]
  return [...existing].sort(
    (left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt),
  )
}

function optionalText(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed || undefined
}

function attributeList(value: string): string[] {
  return value
    .split(/[\n,]/)
    .map((part) => part.trim())
    .filter(Boolean)
}

function positiveInteger(value: string, fallback: number): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback
  }
  return Math.round(parsed)
}

function nonNegativeInteger(value: string): number | undefined {
  if (!value.trim()) {
    return undefined
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    return undefined
  }
  return Math.round(parsed)
}

export function inventoryDateLabel(value?: string | null): string | null {
  if (!value) {
    return null
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result)
        return
      }
      reject(new Error('Unsupported image file'))
    }
    reader.onerror = () => reject(new Error('Could not read image file'))
    reader.readAsDataURL(file)
  })
}

export async function fileToInventoryPhotoUrl(file: File): Promise<string> {
  const dataUrl = await readFileAsDataUrl(file)
  if (dataUrl.length <= INVENTORY_PHOTO_DATA_URL_LIMIT) {
    return dataUrl
  }
  return resizeInventoryPhotoDataUrl(dataUrl)
}

function loadImage(dataUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Could not process image file'))
    image.src = dataUrl
  })
}

async function resizeInventoryPhotoDataUrl(dataUrl: string): Promise<string> {
  const image = await loadImage(dataUrl)
  const attempts = [
    { max: 1280, quality: 0.78 },
    { max: 1024, quality: 0.72 },
    { max: 840, quality: 0.66 },
  ]
  let latest = dataUrl
  for (const attempt of attempts) {
    const ratio = Math.min(1, attempt.max / Math.max(image.width, image.height))
    const width = Math.max(1, Math.round(image.width * ratio))
    const height = Math.max(1, Math.round(image.height * ratio))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) {
      break
    }
    context.drawImage(image, 0, 0, width, height)
    latest = canvas.toDataURL('image/jpeg', attempt.quality)
    if (latest.length <= INVENTORY_PHOTO_DATA_URL_LIMIT) {
      return latest
    }
  }
  if (latest.length > INVENTORY_PHOTO_DATA_URL_LIMIT) {
    throw new Error('Photo is too large')
  }
  return latest
}

export function initialInventoryForm(
  category: UserInventoryCategory = 'APPAREL',
): InventoryFormState {
  return {
    name: '',
    brand: '',
    category,
    description: '',
    imageUrl: '',
    productUrl: '',
    photoUrl: '',
    quantity: '1',
    unit: '',
    location: '',
    notes: '',
    attributes: '',
    consumable: category === 'PANTRY',
    restockEnabled: false,
    restockThreshold: '',
  }
}

export function inventoryFormFromItem(item: UserInventoryItemProfile): InventoryFormState {
  return {
    name: item.name,
    brand: item.brand ?? '',
    category: item.category,
    description: item.description ?? '',
    imageUrl: item.imageUrl ?? '',
    productUrl: item.productUrl ?? '',
    photoUrl: item.photoUrl ?? '',
    quantity: String(item.quantity),
    unit: item.unit ?? '',
    location: item.location ?? '',
    notes: item.notes ?? '',
    attributes: item.attributes.join(', '),
    consumable: item.consumable,
    restockEnabled: item.restockEnabled,
    restockThreshold: item.restockThreshold === null ? '' : String(item.restockThreshold),
  }
}

export function inventoryItemInputFromForm(form: InventoryFormState): UserInventoryItemInput {
  return {
    name: form.name.trim(),
    brand: optionalText(form.brand),
    category: form.category,
    description: optionalText(form.description),
    imageUrl: optionalText(form.imageUrl),
    productUrl: optionalText(form.productUrl),
    quantity: positiveInteger(form.quantity, 1),
    unit: optionalText(form.unit),
    location: optionalText(form.location),
    notes: optionalText(form.notes),
    attributes: attributeList(form.attributes),
    consumable: form.consumable,
    restockEnabled: form.restockEnabled,
    restockThreshold: form.restockEnabled ? nonNegativeInteger(form.restockThreshold) : undefined,
  }
}

export function inventoryPhotoInputFromForm(form: InventoryFormState): UserInventoryPhotoInput {
  return {
    photoUrl: form.photoUrl,
    name: optionalText(form.name),
    brand: optionalText(form.brand),
    category: form.category,
    description: optionalText(form.description),
    quantity: positiveInteger(form.quantity, 1),
    unit: optionalText(form.unit),
    location: optionalText(form.location),
    notes: optionalText(form.notes),
    attributes: attributeList(form.attributes),
    consumable: form.consumable,
    restockEnabled: form.restockEnabled,
    restockThreshold: form.restockEnabled ? nonNegativeInteger(form.restockThreshold) : undefined,
  }
}

export function inventoryUpdateInputFromForm(
  form: InventoryFormState,
): UserInventoryItemUpdateInput {
  return {
    name: optionalText(form.name),
    brand: optionalText(form.brand),
    category: form.category,
    description: optionalText(form.description),
    imageUrl: optionalText(form.imageUrl),
    productUrl: optionalText(form.productUrl),
    photoUrl: optionalText(form.photoUrl),
    quantity: positiveInteger(form.quantity, 1),
    unit: optionalText(form.unit),
    location: optionalText(form.location),
    notes: optionalText(form.notes),
    attributes: attributeList(form.attributes),
    consumable: form.consumable,
    restockEnabled: form.restockEnabled,
    restockThreshold: form.restockEnabled ? nonNegativeInteger(form.restockThreshold) : undefined,
  }
}
