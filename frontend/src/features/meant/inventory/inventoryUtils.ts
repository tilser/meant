import type {
  UserInventoryCategory,
  UserInventoryItemInput,
  UserInventoryItemProfile,
  UserInventoryItemUpdateInput,
  UserInventorySelectedOptionProfile,
} from '../../../lib/apiClient'
import { merchantAdjacentEditableLabel } from '../cart/merchantOrigin'

export type UserInventoryItemDraftInput = Omit<UserInventoryItemInput, 'photoPath'>

export interface InventoryFormState {
  name: string
  brand: string
  category: UserInventoryCategory
  description: string
  productUrl: string
  purchasedOn: string
  size: string
  color: string
  material: string
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

export function inventoryCategoryLabel(category: UserInventoryCategory): string {
  return INVENTORY_CATEGORY_LABELS[category] ?? INVENTORY_CATEGORY_LABELS.OTHER
}

export function inventorySourceLabel(source: UserInventoryItemProfile['source']): string {
  return INVENTORY_SOURCE_LABELS[source] ?? source
}

export function inventoryItemImage(
  item: UserInventoryItemProfile,
  signedPhotoUrl?: string | null,
): string | null {
  return (
    signedPhotoUrl ||
    safeInventoryProductUrl(item.imageUrl, item.commerceReference?.merchantOrigin) ||
    safeInventoryProductUrl(item.photoUrl, item.commerceReference?.merchantOrigin)
  )
}

export function safeInventoryProductUrl(
  value?: string | null,
  merchantOrigin?: string | null,
): string | null {
  if (!value) {
    return null
  }
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
      return null
    }
    const host = url.hostname.toLocaleLowerCase()
    const officialHost = merchantOrigin?.trim().toLocaleLowerCase() || null
    if (
      host.startsWith('mcp.') ||
      host.includes('.mcp.') ||
      ((host === 'myshopify.com' || host.endsWith('.myshopify.com')) && host !== officialHost)
    ) {
      return null
    }
    const path = url.pathname.toLocaleLowerCase().replace(/\/+$/, '') || '/'
    if (
      [
        '/.well-known/ucp.json',
        '/.well-known/ucp',
        '/api/ucp/mcp',
        '/api/mcp',
        '/mcp',
      ].some((protocolPath) => path === protocolPath || path.startsWith(`${protocolPath}/`))
    ) {
      return null
    }
    return url.toString()
  } catch {
    return null
  }
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
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  const date = dateOnly
    ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

export function initialInventoryForm(
  category: UserInventoryCategory = 'APPAREL',
): InventoryFormState {
  return {
    name: '',
    brand: '',
    category,
    description: '',
    productUrl: '',
    purchasedOn: '',
    size: '',
    color: '',
    material: '',
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
    brand: merchantAdjacentEditableLabel(item.brand),
    category: item.category,
    description: item.description ?? '',
    productUrl: item.productUrl ?? '',
    purchasedOn: item.purchasedOn ?? '',
    size: item.size ?? '',
    color: item.color ?? '',
    material: item.material ?? '',
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

export function inventoryItemInputFromForm(form: InventoryFormState): UserInventoryItemDraftInput {
  return {
    name: form.name.trim(),
    brand: optionalText(form.brand),
    category: form.category,
    description: optionalText(form.description),
    productUrl: optionalText(form.productUrl),
    purchasedOn: optionalText(form.purchasedOn),
    size: optionalText(form.size),
    color: optionalText(form.color),
    material: optionalText(form.material),
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
    name: form.name.trim(),
    brand: form.brand.trim(),
    category: form.category,
    description: form.description.trim(),
    productUrl: form.productUrl.trim(),
    purchasedOn: form.purchasedOn.trim(),
    size: form.size.trim(),
    color: form.color.trim(),
    material: form.material.trim(),
    quantity: positiveInteger(form.quantity, 1),
    unit: form.unit.trim(),
    location: form.location.trim(),
    notes: form.notes.trim(),
    attributes: attributeList(form.attributes),
    consumable: form.consumable,
    restockEnabled: form.restockEnabled,
    restockThreshold: form.restockEnabled
      ? (nonNegativeInteger(form.restockThreshold) ?? null)
      : null,
  }
}
