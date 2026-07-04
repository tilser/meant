import {
  type ChangeEvent,
  type CSSProperties,
  type Dispatch,
  type DragEvent as ReactDragEvent,
  type FormEvent,
  Fragment,
  type KeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  type SetStateAction,
  type TouchEvent as ReactTouchEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'

import {
  DEFAULT_COMPARE,
  DEFAULT_PREFERENCE_IDS,
  DEFAULT_USER,
  LOCATIONS,
  PREFERENCES,
  PRODUCTS,
  PROFILE,
  PROMPTS,
} from './data'
import { type AuthActions, useSupabaseAuth } from './auth/useSupabaseAuth'
import { CartPopover } from './cart/CartPopover'
import { CartView } from './cart/CartView'
import type { MerchantCartSnapshot } from './cart/types'
import { useCartController } from './cart/useCartController'
import { cartableOfferForProduct, offerCartable } from './cart/utils'
import { deliveryLocationSummary } from './shared/locations'
import { useStoredState } from './shared/storage'
import {
  CartIcon,
  CloseIcon,
  EmptyState,
  Placeholder,
  ProductArtwork,
  SparkMark,
  ViewHead,
} from './shared/ui'
import {
  acceptUserTasteSuggestion,
  completeMerchantIdentityAuthorization,
  createUserInventoryItem,
  createUserInventoryPhotoItem,
  deleteUserInventoryItem,
  deleteProfilePictureFile,
  exportUserInventory,
  getAssistantConversation,
  getAssistantConversations,
  getCartCheckout,
  getCurrentUser,
  getMerchantProductDetails,
  getMerchantIdentityLinks,
  getMerchants,
  getOrders,
  getProfilePictureUrl,
  getProductDiscovery,
  getUserInventoryItems,
  getUserProductSearchSuggestions,
  getUserSettings,
  getUserTasteProfile,
  removeProfilePicture,
  removeSavedProduct,
  removeUserTasteSignal,
  rejectUserTasteSuggestion,
  revokeMerchantIdentityLink,
  saveUserProduct,
  startMerchantIdentityAuthorization,
  streamAssistantMessage,
  streamUserProductSearch,
  type AssistantChatContextInput,
  type SaveUserProductInput,
  type CheckoutProfile,
  type MerchantIdentityLinkProfile,
  type MerchantProductDetailsProfile,
  type MerchantProfile,
  type OrderProfile,
  type ProductOptionProfile,
  type ProductSelectedOptionProfile,
  type ShoppingFilterProfile,
  type UserInventoryCategory,
  type UserInventoryItemInput,
  type UserInventoryItemProfile,
  type UserInventoryItemUpdateInput,
  type UserInventoryPhotoInput,
  type UserAssistantConversationProfile,
  type UserAssistantConversationSummaryProfile,
  type UserProductSearchStreamEventProfile,
  type UserSavedProductProfile,
  type UserTasteProfile,
  updateUserInventoryItem,
  type UserProductSearchProductProfile,
  updateUserTasteSignal,
  updateProfile,
  updateProfilePicture,
  updateUserSettings,
  uploadProfilePictureFile,
  type UserSettingsProfile,
} from '../../lib/apiClient'
import type {
  AuthMode,
  CartItem,
  ClothingFit,
  CheckoutPayload,
  CorePreferenceId,
  Offer,
  Order,
  OrderStatus,
  Preference,
  PreferenceId,
  Product,
  ProductAgentStage,
  ProductCatalogAttribute,
  ProductCatalogCategory,
  ProductId,
  ProductMedia,
  ProductOption,
  ProductSelectedOption,
  Theme,
  UserAccount,
  UserLocation,
  View,
} from './types'
import {
  IMPORT_ASK,
  availableOffers,
  bestOffer,
  cartGroups,
  cartLines,
  computeSmartAlerts,
  createOrder,
  displayProductCategoryValue,
  firstUrl,
  formatOrderDate,
  money,
  normalizedMerchantName,
  prefLabel,
  productMerchantCount,
  productPriceFrom,
  productsForLocation,
  productsForClothingFit,
  readStorage,
  resolveAsk,
} from './utils'

interface Message {
  role: 'you' | 'ai'
  text: string
  products?: readonly Product[]
  pending?: boolean
}

interface MiniCompareRow {
  label: string
  values: readonly string[]
  winnerIndex: number
}

type DiscoverChatBlock =
  | { type: 'text'; text: string }
  | { type: 'products'; products: readonly Product[]; query?: string }
  | { type: 'reviews'; product: Product }
  | { type: 'code'; product: Product; code: string; saved: number }
  | { type: 'similar'; product: Product; products: readonly Product[] }
  | { type: 'decision'; product: Product; runnerUp: Product | null }
  | { type: 'watch'; product: Product; price: number; merchant: string }
  | { type: 'friendvote'; person: string; product: Product; vote: 'up' | 'down'; note: string }
  | {
      type: 'added'
      product: Product
      merchant: string
      synced: boolean
      price?: number
      count?: number
      code?: string
    }
  | { type: 'saved'; products: readonly Product[] }
  | { type: 'orders'; orders: readonly Order[] }
  | { type: 'prefs'; preferences: readonly Preference[] }
  | { type: 'cart'; lines: readonly CartItem[]; products?: readonly Product[] }
  | { type: 'checkout'; merchantCount: number }
  | {
      type: 'minicompare'
      products: readonly Product[]
      rows: readonly MiniCompareRow[]
      pickIndex: number
    }
  | { type: 'system'; text: string }

interface DiscoverChatMessage {
  id: string
  role: 'you' | 'ai'
  text?: string
  blocks?: readonly DiscoverChatBlock[]
  pending?: boolean
  query?: string
  productContext?: Product
}

interface DiscoverChatThread {
  id: string
  title: string
  messages: readonly DiscoverChatMessage[]
  named?: boolean
  focusProductId?: ProductId
  createdAt?: number
  updatedAt?: number
}

interface ShelfThumb {
  name: string
  tone: string
  imageUrl?: string | null
}

interface ShelfMessageSnapshot {
  side: 'you' | 'meant'
  title: string
  text: string
  thumbs: readonly ShelfThumb[]
}

interface ShelfProductSnapshot {
  productId: ProductId
  name: string
  brand: string
  category: string
  tone: string
  priceFrom: number
  merchants: number
  imageUrl?: string | null
}

type ShelfItem =
  | {
      uid: string
      kind: 'message'
      messageId: string
      collapsed: boolean
      snapshot: ShelfMessageSnapshot
    }
  | {
      uid: string
      kind: 'product'
      productId: ProductId
      collapsed: boolean
      snapshot: ShelfProductSnapshot
    }

type ShelfDragPayload =
  | { kind: 'message'; messageId: string; snapshot: ShelfMessageSnapshot }
  | { kind: 'product'; snapshot: ShelfProductSnapshot }

interface ProductDetailChatRequest {
  id: string
  product: Product
  question: string
}

type DiscoverFindRequest =
  | { id: string; kind: 'message'; messageId: string }
  | { id: string; kind: 'product'; productId: ProductId }

interface AssistantProductAction {
  product: Product
  shouldAddToCart: boolean
  shouldOpen: boolean
}

interface AgentActivity {
  agent: string
  label: string
  state: 'active' | 'done' | 'error'
  updatedAt: number
}

interface AskPanelSize {
  width: number
  height: number
}

type ProductDetailLoadState = 'idle' | 'loading' | 'loaded' | 'error'

interface ProductOpenProps {
  onOpen: (product: Product, products?: readonly Product[]) => void
}

interface ProductSaveProps {
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  onToggleSave: (product: Product) => void
  onDismiss?: (product: Product) => void
}

const EMPTY_TASTE_PROFILE: UserTasteProfile = {
  profileHash: '',
  signals: [],
  suggestions: [],
}

const MODAL_THUMBNAIL_PAGE_SIZE = 8
const SHELF_MIN_WIDTH = 300
const SHELF_MAX_WIDTH = 720
const SHELF_DRAG_MIME = 'application/x-meant-shelf'

const askContexts: Readonly<Record<View, { label: string; suggestions: readonly string[] }>> = {
  discover: {
    label: 'Your feed',
    suggestions: [
      "What's the best value here?",
      'Find me something healthy',
      'Help me pick clothing',
    ],
  },
  saved: {
    label: 'Your saved items',
    suggestions: ['Compare my saved items', 'Best value in my list?'],
  },
  compare: {
    label: 'Comparing products',
    suggestions: ['Which one is better for me?', 'Cheaper of these?'],
  },
  inventory: {
    label: 'Your inventory',
    suggestions: ['What should I restock?', 'What complements this?'],
  },
  preferences: {
    label: 'Your profile',
    suggestions: ['What should I add?', 'Suggest products for these filters'],
  },
  cart: {
    label: 'Your cart',
    suggestions: ['Is everything compatible?', 'Find me more codes'],
  },
  orders: {
    label: 'Your orders',
    suggestions: ["Where's my latest order?", 'What did I buy last month?'],
  },
  account: {
    label: 'Your account',
    suggestions: ['Where are my saved items?', 'How do I change preferences?'],
  },
}

const ASK_PANEL_DEFAULT_SIZE: AskPanelSize = { width: 460, height: 620 }
const ASK_PANEL_MIN_WIDTH = 360
const ASK_PANEL_MIN_HEIGHT = 440
const ASK_PANEL_MAX_WIDTH = 720
const ASK_PANEL_MAX_HEIGHT = 760

interface InventoryFormState {
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

const INVENTORY_CATEGORIES: readonly UserInventoryCategory[] = [
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

const INVENTORY_RELATIONSHIP_LABELS = {
  DUPLICATE: 'Already own',
  COMPLEMENT: 'Complements',
  RESTOCK: 'Restock',
} as const

const INVENTORY_PHOTO_DATA_URL_LIMIT = 1_900_000

const DEFAULT_GREETING = 'Good afternoon'
const DEFAULT_BUDGET = 120
const SEARCH_SUGGESTION_COUNT = 4
const PRODUCT_SEARCH_PAGE_SIZE = 20
const NO_CONFIRMED_PREFERENCE_TAKE =
  'No preference matches are confirmed yet; review the details and offers.'
const SEARCH_RELEVANCE_TAKE =
  'This looks relevant to your search based on the available product details.'

const CLOTHING_FIT_OPTIONS: readonly { value: ClothingFit; label: string }[] = [
  { value: 'none', label: 'No preference' },
  { value: 'men', label: "Men's" },
  { value: 'women', label: "Women's" },
  { value: 'other', label: 'Other' },
]

function greetingForHour(hour: number): string {
  if (hour >= 5 && hour < 12) {
    return 'Good morning'
  }
  if (hour < 18) {
    return 'Good afternoon'
  }
  return 'Good evening'
}

function currentBrowserGreeting(): string {
  return greetingForHour(new Date().getHours())
}

function completeSearchSuggestions(suggestions: unknown): string[] {
  const completed: string[] = []
  const addSuggestion = (suggestion: unknown) => {
    if (typeof suggestion !== 'string') {
      return
    }
    const normalized = suggestion.trim().replace(/\s+/g, ' ')
    if (
      normalized &&
      completed.length < SEARCH_SUGGESTION_COUNT &&
      !completed.some((current) => current.toLowerCase() === normalized.toLowerCase())
    ) {
      completed.push(normalized)
    }
  }

  if (Array.isArray(suggestions)) {
    suggestions.forEach(addSuggestion)
  }
  PROMPTS.forEach(addSuggestion)
  return completed.slice(0, SEARCH_SUGGESTION_COUNT)
}

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function normalizedAskPanelSize(size: AskPanelSize | null | undefined): AskPanelSize {
  const candidate = size && typeof size === 'object' ? (size as Partial<AskPanelSize>) : {}
  const width =
    typeof candidate.width === 'number' && Number.isFinite(candidate.width)
      ? candidate.width
      : ASK_PANEL_DEFAULT_SIZE.width
  const height =
    typeof candidate.height === 'number' && Number.isFinite(candidate.height)
      ? candidate.height
      : ASK_PANEL_DEFAULT_SIZE.height
  return {
    width: Math.round(clampNumber(width, ASK_PANEL_MIN_WIDTH, ASK_PANEL_MAX_WIDTH)),
    height: Math.round(clampNumber(height, ASK_PANEL_MIN_HEIGHT, ASK_PANEL_MAX_HEIGHT)),
  }
}

function askPanelViewportMax(): AskPanelSize {
  return {
    width: Math.min(ASK_PANEL_MAX_WIDTH, Math.max(ASK_PANEL_MIN_WIDTH, window.innerWidth - 40)),
    height: Math.min(
      ASK_PANEL_MAX_HEIGHT,
      Math.max(ASK_PANEL_MIN_HEIGHT, window.innerHeight - 118),
    ),
  }
}

function useChangePulse(value: number, duration = 440): boolean {
  const [pulse, setPulse] = useState(false)
  const previousRef = useRef(value)
  const timeoutRef = useRef<number | null>(null)

  useEffect(() => {
    if (previousRef.current === value) {
      return
    }
    previousRef.current = value
    setPulse(true)
    if (timeoutRef.current !== null) {
      window.clearTimeout(timeoutRef.current)
    }
    timeoutRef.current = window.setTimeout(() => {
      setPulse(false)
      timeoutRef.current = null
    }, duration)
  }, [duration, value])

  useEffect(
    () => () => {
      if (timeoutRef.current !== null) {
        window.clearTimeout(timeoutRef.current)
      }
    },
    [],
  )

  return pulse
}

function useBrowserGreeting(): string {
  const [greeting, setGreeting] = useState(DEFAULT_GREETING)

  useEffect(() => {
    const updateGreeting = () => setGreeting(currentBrowserGreeting())
    updateGreeting()

    const intervalId = window.setInterval(updateGreeting, 60_000)
    return () => window.clearInterval(intervalId)
  }, [])

  return greeting
}

function firstNameFromName(name: string): string {
  return name.trim().split(/\s+/)[0] || name
}

function preferenceFromFilter(filter: ShoppingFilterProfile): Preference {
  return {
    id: filter.id,
    label: filter.label,
    desc: filter.description,
    category: filter.category,
    polarity: filter.polarity,
    displayOrder: filter.displayOrder,
  }
}

function orderFromProfile(profile: OrderProfile): Order {
  return {
    id: profile.displayId || profile.remoteOrderId || profile.id,
    date: profile.date,
    status: profile.status as OrderStatus,
    statusNote: profile.statusNote,
    items: profile.lines.map((line) => ({
      id: line.productKey || line.productId || line.id,
      merchant: line.merchantName || profile.merchantName || profile.merchantDomain,
      qty: line.quantity ?? 0,
      merchantId: profile.merchantId,
      merchantDomain: profile.merchantDomain,
      productVariantId: line.productVariantId,
      variantTitle: line.variantTitle,
      productTitle: line.productTitle,
      imageUrl: line.imageUrl,
      productUrl: line.productUrl,
      unitPriceAmount: line.unitAmount,
      lineTotalAmount: line.totalAmount,
      orderCurrency: line.currency ?? profile.currency,
    })),
    saved: 0,
    savedNote: '',
  }
}

function initialDeliveryLocations(): UserLocation[] {
  const locations = readStorage<UserLocation[]>('meant.locations', [])
  if (Array.isArray(locations) && locations.length > 0) {
    return locations
  }
  const legacyLocation = readStorage<UserLocation | null>('meant.location', null)
  return legacyLocation ? [legacyLocation] : []
}

function settingsLocations(settings: UserSettingsProfile): UserLocation[] {
  if (settings.locations?.length) {
    return settings.locations
  }
  return settings.location ? [settings.location] : []
}

function clothingFitFromSettings(settings: UserSettingsProfile): ClothingFit {
  return settings.clothingFit ?? 'none'
}

function clothingFitLabel(value: ClothingFit): string {
  return CLOTHING_FIT_OPTIONS.find((option) => option.value === value)?.label ?? 'No preference'
}

function applySettingsPayload(
  settings: UserSettingsProfile,
  setAvailablePrefs: Dispatch<SetStateAction<Preference[]>>,
  setPrefsOn: Dispatch<SetStateAction<PreferenceId[]>>,
  setBudget: Dispatch<SetStateAction<number | null>>,
  setDeliveryLocations: Dispatch<SetStateAction<UserLocation[]>>,
  setClothingFit: Dispatch<SetStateAction<ClothingFit>>,
) {
  setAvailablePrefs(settings.availableFilters.map(preferenceFromFilter))
  setPrefsOn(settings.filters.map((filter) => filter.id))
  setBudget(settings.budget)
  setDeliveryLocations(settingsLocations(settings))
  setClothingFit(clothingFitFromSettings(settings))
}

function stripHtml(value: string | null | undefined): string {
  return (
    value
      ?.replace(/<[^>]*>/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() ?? ''
  )
}

function parsePriceAmount(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return value / 100
  }
  const parsed = Number.parseFloat(normalizeLocalizedPriceAmount(value))
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeLocalizedPriceAmount(value: string): string {
  const cleaned = value.trim().replace(/[^0-9.,-]+/g, '')
  if (!cleaned) {
    return ''
  }
  const sign = cleaned.includes('-') ? '-' : ''
  const unsigned = cleaned.replace(/-/g, '')
  if (!/[0-9]/.test(unsigned)) {
    return ''
  }
  const lastDot = unsigned.lastIndexOf('.')
  const lastComma = unsigned.lastIndexOf(',')

  if (lastDot !== -1 && lastComma !== -1) {
    return (
      sign +
      (lastComma > lastDot
        ? unsigned.replace(/\./g, '').replace(',', '.')
        : unsigned.replace(/,/g, ''))
    )
  }
  if (lastComma !== -1) {
    return sign + normalizeSingleSeparatorPriceAmount(unsigned, ',')
  }
  if (lastDot !== -1) {
    return sign + normalizeSingleSeparatorPriceAmount(unsigned, '.')
  }
  return sign + unsigned
}

function normalizeSingleSeparatorPriceAmount(value: string, separator: ',' | '.'): string {
  const firstSeparator = value.indexOf(separator)
  const lastSeparator = value.lastIndexOf(separator)
  const separatorPattern = separator === ',' ? /,/g : /\./g
  if (firstSeparator !== lastSeparator) {
    return hasGroupedThousandsPriceAmount(value, separator)
      ? value.replace(separatorPattern, '')
      : ''
  }
  const fractionalDigits = value.length - lastSeparator - 1
  if (separator === ',' && fractionalDigits === 3 && lastSeparator <= 3) {
    return value.replace(separatorPattern, '')
  }
  return separator === ',' ? value.replace(',', '.') : value
}

function hasGroupedThousandsPriceAmount(value: string, separator: ',' | '.'): boolean {
  const firstSeparator = value.indexOf(separator)
  if (firstSeparator <= 0 || firstSeparator > 3) {
    return false
  }
  let groupStart = firstSeparator + 1
  while (groupStart < value.length) {
    const nextSeparator = value.indexOf(separator, groupStart)
    const groupEnd = nextSeparator === -1 ? value.length : nextSeparator
    if (groupEnd - groupStart !== 3) {
      return false
    }
    groupStart = groupEnd + 1
  }
  return groupStart === value.length + 1
}

function normalizeRatingScore(value: number | null | undefined): number | null {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null
  }
  return Math.max(0, Math.min(5, value))
}

function searchProductPrice(product: UserProductSearchProductProfile): number {
  return (
    parsePriceAmount(product.selectedVariantPriceAmount) ??
    parsePriceAmount(product.detailPriceMin) ??
    parsePriceAmount(product.priceMinAmount) ??
    0
  )
}

function searchProductListPrice(
  product: UserProductSearchProductProfile,
  currentPrice: number,
): number | null {
  const listPrice = parsePriceAmount(product.listPriceAmount)
  if (listPrice === null || listPrice <= currentPrice) {
    return null
  }
  return listPrice
}

function searchProductMedia(product: UserProductSearchProductProfile): ProductMedia[] {
  const seen = new Set<string>()
  const fromApi = (product.media ?? [])
    .map((item): ProductMedia | null => {
      if (!item.url) {
        return null
      }
      return {
        type: item.type || 'image',
        url: item.url,
        altText: item.altText,
      }
    })
    .filter((item): item is ProductMedia => item !== null)
  const fallback = [product.selectedVariantImageUrl, product.detailImageUrl, product.imageUrl]
    .filter((url): url is string => Boolean(url))
    .map((url) => ({ type: 'image', url, altText: product.selectedVariantImageAltText }))

  return [...fromApi, ...fallback].filter((item) => {
    const key = `${item.type.toLowerCase()}|${item.url}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function searchProductCatalogCategories(
  product: UserProductSearchProductProfile,
): ProductCatalogCategory[] {
  return (product.categories ?? [])
    .filter((category) => Boolean(displayProductCategoryValue(category.value)))
    .map((category) => ({
      value: displayProductCategoryValue(category.value) ?? '',
      taxonomy: category.taxonomy,
    }))
}

function searchProductStringValues(values: readonly string[] | null | undefined): string[] {
  const seen = new Set<string>()
  return (values ?? [])
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => {
      const key = value.toLowerCase()
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
}

function searchProductAttributes(
  product: UserProductSearchProductProfile,
): ProductCatalogAttribute[] {
  return (product.attributes ?? [])
    .filter((attribute) => Boolean(attribute.name) && Boolean(attribute.value))
    .map((attribute) => ({
      name: attribute.name ?? '',
      value: attribute.value ?? '',
    }))
}

function productOptionsFromProfiles(
  options: readonly ProductOptionProfile[] | null | undefined,
): ProductOption[] {
  return (options ?? [])
    .map((option): ProductOption | null => {
      const name = option.name?.trim()
      const values = (option.values ?? []).map((value) => value.trim()).filter(Boolean)
      if (!name || values.length === 0) {
        return null
      }
      return { name, values }
    })
    .filter((option): option is ProductOption => option !== null)
}

function productSelectedOptionsFromProfiles(
  options: readonly ProductSelectedOptionProfile[] | null | undefined,
): ProductSelectedOption[] {
  return (options ?? [])
    .map((option): ProductSelectedOption | null => {
      const name = option.name?.trim()
      const value = option.value?.trim()
      if (!name || !value) {
        return null
      }
      return { name, value }
    })
    .filter((option): option is ProductSelectedOption => option !== null)
}

function mediaFromMerchantDetails(details: MerchantProductDetailsProfile | null): ProductMedia[] {
  if (!details) {
    return []
  }
  const media = (details.media ?? [])
    .map((item): ProductMedia | null => {
      const url = item.url || item.previewImageUrl
      if (!url) {
        return null
      }
      return {
        type: item.type || 'image',
        url,
        altText: item.altText,
      }
    })
    .filter((item): item is ProductMedia => item !== null)
  const images = (details.images ?? [])
    .map((image): ProductMedia | null => {
      if (!image.url) {
        return null
      }
      return { type: 'image', url: image.url, altText: image.altText }
    })
    .filter((item): item is ProductMedia => item !== null)
  const fallbacks = [details.selectedVariantImageUrl, details.imageUrl]
    .filter((url): url is string => Boolean(url))
    .map((url) => ({ type: 'image', url, altText: details.selectedVariantImageAltText }))

  return [...media, ...images, ...fallbacks]
}

function mergeProductMedia(
  product: Product,
  details: MerchantProductDetailsProfile | null,
): ProductMedia[] {
  const seen = new Set<string>()
  return [...mediaFromMerchantDetails(details), ...(product.media ?? [])].filter((item) => {
    const key = `${item.type.toLowerCase()}|${item.url}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function searchProductCategory(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
): string {
  const catalogCategory = (product.categories ?? [])
    .map((category) => displayProductCategoryValue(category.value))
    .find(Boolean)
  if (catalogCategory) {
    return catalogCategory
  }

  const category = product.matchedFilterIds
    .map((id) => preferences.find((preference) => preference.id === id)?.category)
    .find(Boolean)

  switch (category) {
    case 'food':
      return 'Groceries'
    case 'materials':
      return 'Clothing'
    case 'technology':
      return 'Technology'
    case 'home':
      return 'Home & Kitchen'
    case 'personal-care':
      return 'Personal Care'
    default:
      return 'Product'
  }
}

function toneForSearchProduct(product: UserProductSearchProductProfile): string {
  const tones = ['#e7ebef', '#eaede6', '#eceae7', '#eee9ed', '#e9ede8']
  const code = Array.from(product.productKey).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return tones[code % tones.length] ?? tones[0]
}

function productFromSearchResult(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
  agentStage?: ProductAgentStage,
): Product {
  const price = searchProductPrice(product)
  const media = searchProductMedia(product)
  const listPrice = searchProductListPrice(product, price)
  const catalogCategories = searchProductCatalogCategories(product)
  const certifications = searchProductStringValues(product.certifications)
  const materials = searchProductStringValues(product.materials)
  const skus = searchProductStringValues(product.skus)
  const collections = searchProductStringValues(product.collections)
  const catalogAttributes = searchProductAttributes(product)
  const brand = product.merchantName || product.merchantDomain
  const detail = stripHtml(product.detailDescription || product.descriptionHtml)
  const ratingScore = normalizeRatingScore(product.ratingScore)
  const reviewCount = Math.max(0, product.reviewCount ?? 0)
  const candidate = agentStage === 'candidate'
  const baseProduct: Product = {
    id: product.productKey,
    productHash: product.productHash,
    merchantId: product.merchantId,
    merchantDomain: product.merchantDomain,
    merchantProductId: product.productId,
    name: product.title,
    brand,
    category: searchProductCategory(product, preferences),
    tone: toneForSearchProduct(product),
    imageUrl:
      media.find((item) => item.type.toLowerCase() === 'image')?.url ||
      product.imageUrl ||
      product.detailImageUrl ||
      product.selectedVariantImageUrl,
    productUrl: product.url,
    remote: true,
    match: product.matchScore,
    priceFrom: price,
    listPrice,
    merchants: 1,
    satisfies: product.matchedFilterIds,
    misses: product.missedFilterIds,
    note: product.whyMeantForYou || detail,
    pros: [],
    cons: [],
    review: {
      score: ratingScore,
      count: reviewCount,
      insight: searchProductReviewInsight(candidate, ratingScore, reviewCount),
    },
    media,
    catalogCategories,
    certifications,
    materials,
    skus,
    collections,
    catalogAttributes,
    detailError: product.detailError,
    detailDescription: detail || null,
    offers: [
      {
        merchant: brand,
        price,
        delivery:
          product.available === false || product.selectedVariantAvailable === false
            ? 'Availability unclear'
            : 'Available from merchant',
        merchantId: product.merchantId,
        merchantDomain: product.merchantDomain,
        productVariantId: product.selectedVariantId,
        variantTitle: product.selectedVariantTitle,
        available:
          product.available === false || product.selectedVariantAvailable === false
            ? false
            : product.selectedVariantAvailable,
      },
    ],
    inventoryRelationship: product.inventoryRelationship,
    inventoryItemId: product.inventoryItemId,
    inventoryItemName: product.inventoryItemName,
    agentStage,
    agentUpdatedAt: agentStage ? Date.now() : undefined,
  }
  return productWithCuratedFields(baseProduct, preferences)
}

function preferenceLabels(
  ids: readonly PreferenceId[],
  preferences: readonly Preference[],
): string[] {
  return ids.map((id) => prefLabel(preferences, id)).filter(Boolean)
}

function lowerLabel(label: string): string {
  return label.trim().toLowerCase()
}

function humanList(items: readonly string[]): string {
  if (items.length <= 1) {
    return items[0] ?? ''
  }
  if (items.length === 2) {
    return `${items[0]} and ${items[1]}`
  }
  return `${items.slice(0, -1).join(', ')}, and ${items[items.length - 1]}`
}

function preferenceTarget(label: string): { kind: 'prefer' | 'avoid'; text: string } | null {
  const normalized = lowerLabel(label).replace(/\s+/g, ' ')
  if (!normalized) {
    return null
  }
  if (normalized.startsWith('no ')) {
    return { kind: 'avoid', text: normalized.slice(3).trim() }
  }
  if (normalized.startsWith('avoid ')) {
    return { kind: 'avoid', text: normalized.slice(6).trim() }
  }
  if (normalized.startsWith('without ')) {
    return { kind: 'avoid', text: normalized.slice(8).trim() }
  }
  if (normalized.startsWith('prefer ')) {
    return { kind: 'prefer', text: normalized.slice(7).trim() }
  }
  return { kind: 'prefer', text: normalized }
}

function preferenceSummary(labels: readonly string[]): string {
  const targets = labels
    .slice(0, 3)
    .map(preferenceTarget)
    .filter((target): target is { kind: 'prefer' | 'avoid'; text: string } => Boolean(target?.text))
  const preferred = targets
    .filter((target) => target.kind === 'prefer')
    .map((target) => target.text)
  const avoided = targets.filter((target) => target.kind === 'avoid').map((target) => target.text)
  const parts = [
    preferred.length > 0 ? `for ${humanList(preferred)}` : '',
    avoided.length > 0 ? `to avoid ${humanList(avoided)}` : '',
  ].filter(Boolean)
  const preferenceWord = targets.length === 1 ? 'preference' : 'preferences'
  return parts.length > 0 ? `your ${preferenceWord} ${humanList(parts)}` : 'your preferences'
}

function productPreferenceFacts(product: Product): string[] {
  return Array.from(
    new Set(
      [...(product.materials ?? []), ...(product.certifications ?? [])]
        .map((fact) => fact.trim())
        .filter(Boolean),
    ),
  )
}

function factMatchesPreference(fact: string, preference: string): boolean {
  const normalizedFact = lowerLabel(fact)
  const normalizedPreference = lowerLabel(preference)
  if (!normalizedFact || !normalizedPreference) {
    return false
  }
  return normalizedFact.includes(normalizedPreference)
}

function productPreferenceMatchPhrase(product: Product, labels: readonly string[]): string | null {
  const facts = productPreferenceFacts(product)
  for (const label of labels.slice(0, 3)) {
    const target = preferenceTarget(label)
    if (!target || target.kind !== 'prefer') {
      continue
    }
    const fact = facts.find((candidate) => factMatchesPreference(candidate, target.text))
    if (fact) {
      return `This item is listed as ${fact}, matching ${preferenceSummary([label])}`
    }
  }
  return null
}

function productFallbackTake(product: Product): string {
  const note = product.note.trim()
  if (note && note !== NO_CONFIRMED_PREFERENCE_TAKE) {
    return note
  }
  return SEARCH_RELEVANCE_TAKE
}

function productCuratedTake(product: Product, preferences: readonly Preference[]): string {
  if (product.agentStage === 'candidate') {
    return 'This is being checked against your preferences.'
  }
  if (product.inventoryRelationship === 'DUPLICATE') {
    return `This looks close to ${product.inventoryItemName ?? 'something you already own'}, so compare before buying.`
  }
  const matched = preferenceLabels(product.satisfies, preferences)
  const missed = preferenceLabels(product.misses, preferences)
  const matchedPhrase = productPreferenceMatchPhrase(product, matched)
  if (matched.length > 0 && missed.length > 0) {
    return `${matchedPhrase ?? `This matches ${preferenceSummary(matched)}`}, but check whether it fits ${preferenceSummary(missed)} before deciding.`
  }
  if (matched.length > 0) {
    return `${matchedPhrase ?? `This matches ${preferenceSummary(matched)}`}.`
  }
  if (missed.length > 0) {
    return `Check whether this fits ${preferenceSummary(missed)} before deciding.`
  }
  return productFallbackTake(product)
}

function productCuratedAdvantages(product: Product, preferences: readonly Preference[]): string[] {
  const advantages = preferenceLabels(product.satisfies, preferences).map(
    (label) => `Matches ${preferenceSummary([label])}`,
  )
  if (product.review.score !== null && product.review.count > 0) {
    advantages.push(
      `Rated ${product.review.score.toFixed(1)} out of 5 from ${product.review.count.toLocaleString()} reviews`,
    )
  }
  product.certifications?.slice(0, 2).forEach((certification) => {
    advantages.push(`Product details list ${certification}`)
  })
  if (advantages.length === 0 && product.materials && product.materials.length > 0) {
    advantages.push(`Product details list ${product.materials[0]}`)
  }
  if (advantages.length === 0) {
    advantages.push(
      product.agentStage === 'candidate'
        ? 'Checking this against your preferences'
        : 'No confirmed preference advantages yet',
    )
  }
  return Array.from(new Set(advantages))
}

function productCuratedTradeoffs(product: Product, preferences: readonly Preference[]): string[] {
  const tradeoffs = preferenceLabels(product.misses, preferences).map(
    (label) => `May not fit ${preferenceSummary([label])}`,
  )
  if (product.inventoryRelationship === 'DUPLICATE') {
    tradeoffs.push(`Similar to ${product.inventoryItemName ?? 'something you already own'}`)
  }
  if (product.offers?.every((offer) => offer.available === false)) {
    tradeoffs.push('Current offers are marked unavailable')
  }
  if (tradeoffs.length === 0) {
    tradeoffs.push('No preference trade-offs found in the available details')
  }
  return Array.from(new Set(tradeoffs))
}

function productCuratedFields(
  product: Product,
  preferences: readonly Preference[],
): Pick<Product, 'note' | 'pros' | 'cons'> {
  if (preferences.length === 0) {
    return {
      note: product.note,
      pros: product.pros,
      cons: product.cons,
    }
  }
  return {
    note: productCuratedTake(product, preferences),
    pros: productCuratedAdvantages(product, preferences),
    cons: productCuratedTradeoffs(product, preferences),
  }
}

function productWithCuratedFields(product: Product, preferences: readonly Preference[]): Product {
  return {
    ...product,
    ...productCuratedFields(product, preferences),
  }
}

function searchProductReviewInsight(
  candidate: boolean,
  ratingScore: number | null,
  reviewCount: number,
): string {
  if (candidate) {
    return 'Review signals pending.'
  }
  if (reviewCount <= 0) {
    return 'No review data available from this catalog result.'
  }
  if (ratingScore === null) {
    return 'Review count is available, but no rating summary has been fetched yet.'
  }
  return 'Rating data is available; no review-summary agent has run yet.'
}

function assistantMessageFromProfile(
  message: UserAssistantConversationProfile['messages'][number],
  preferences: readonly Preference[],
): Message {
  return {
    role: message.role === 'assistant' ? 'ai' : 'you',
    text: message.content,
    products: message.products.map((product) => productFromSearchResult(product, preferences)),
  }
}

function messagesFromAssistantConversation(
  conversation: UserAssistantConversationProfile,
  preferences: readonly Preference[],
): Message[] {
  return conversation.messages.map((message) => assistantMessageFromProfile(message, preferences))
}

function assistantConversationSummary(
  conversation: UserAssistantConversationProfile,
): UserAssistantConversationSummaryProfile | null {
  if (!conversation.conversationId) {
    return null
  }
  const timestamp = new Date().toISOString()
  return {
    conversationId: conversation.conversationId,
    title: conversation.title?.trim() || 'New chat',
    createdAt: conversation.createdAt ?? timestamp,
    updatedAt: conversation.updatedAt ?? conversation.createdAt ?? timestamp,
  }
}

function upsertAssistantConversationSummary(
  history: readonly UserAssistantConversationSummaryProfile[],
  summary: UserAssistantConversationSummaryProfile,
): UserAssistantConversationSummaryProfile[] {
  return [
    summary,
    ...history.filter((conversation) => conversation.conversationId !== summary.conversationId),
  ].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
}

function askConversationDateLabel(updatedAt: string): string {
  const date = new Date(updatedAt)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
  }).format(date)
}

function savedProductFromProfile(
  product: UserSavedProductProfile,
  preferences: readonly Preference[] = [],
): Product {
  const snapshot: Product = {
    id: product.id,
    productHash: product.productHash,
    name: product.name,
    brand: product.brand,
    category: displayProductCategoryValue(product.category) ?? 'Product',
    tone: product.tone,
    imageUrl: product.imageUrl,
    productUrl: product.productUrl,
    remote: product.remote,
    match: product.match,
    priceFrom: product.priceFrom,
    merchants: product.merchants,
    satisfies: product.satisfies,
    misses: product.misses,
    note: product.note,
    pros: product.pros,
    cons: product.cons,
    review: product.review,
    offers: product.offers,
    needs: product.needs ? (product.needs as Product['needs']) : undefined,
    provides:
      (product.provides?.length ?? 0) > 0 ? (product.provides as Product['provides']) : undefined,
  }
  return productWithCuratedFields(snapshot, preferences)
}

function findLastAssistantMessageIndex(messages: readonly Message[]): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index]?.role === 'ai') {
      return index
    }
  }
  return -1
}

function savedProductInput(
  product: Product,
  preferences: readonly Preference[] = [],
): SaveUserProductInput {
  const curatedFields = productCuratedFields(product, preferences)
  return {
    id: product.id,
    productHash: product.productHash ?? null,
    name: product.name,
    brand: product.brand,
    category: product.category,
    tone: product.tone,
    imageUrl: product.imageUrl ?? null,
    productUrl: product.productUrl ?? null,
    remote: product.remote ?? false,
    match: product.match,
    priceFrom: product.priceFrom,
    merchants: product.merchants,
    satisfies: [...product.satisfies],
    misses: [...product.misses],
    note: curatedFields.note,
    pros: [...curatedFields.pros],
    cons: [...curatedFields.cons],
    review: {
      score: product.review.score ?? 0,
      count: product.review.count,
      insight: product.review.insight,
    },
    offers: product.offers.map((offer) => ({
      merchant: offer.merchant,
      price: offer.price,
      delivery: offer.delivery,
      merchantId: offer.merchantId ?? null,
      merchantDomain: offer.merchantDomain ?? null,
      productVariantId: offer.productVariantId ?? null,
      variantTitle: offer.variantTitle ?? null,
      available: offer.available ?? null,
    })),
    needs: product.needs ?? null,
    provides: product.provides ? [...product.provides] : [],
  }
}

function assistantProductContext(
  product: Product,
  deliveryLocations: readonly UserLocation[],
  preferences: readonly Preference[] = [],
) {
  const curatedFields = productCuratedFields(product, preferences)
  return {
    id: product.id,
    name: product.name,
    brand: product.brand,
    category: product.category,
    match: product.match,
    priceFrom: productPriceFrom(product, deliveryLocations),
    note: curatedFields.note,
  }
}

function inventoryCategoryLabel(category: UserInventoryCategory): string {
  return INVENTORY_CATEGORY_LABELS[category] ?? INVENTORY_CATEGORY_LABELS.OTHER
}

function inventorySourceLabel(source: UserInventoryItemProfile['source']): string {
  return INVENTORY_SOURCE_LABELS[source] ?? source
}

function inventoryRelationshipLabel(
  relationship: Product['inventoryRelationship'] | undefined,
): string | null {
  if (!relationship || relationship === 'NONE') {
    return null
  }
  return INVENTORY_RELATIONSHIP_LABELS[relationship] ?? null
}

function inventoryItemImage(item: UserInventoryItemProfile): string | null {
  return item.imageUrl || item.photoUrl
}

function upsertInventorySnapshot(
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

function inventoryDateLabel(value?: string | null): string | null {
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

async function fileToInventoryPhotoUrl(file: File): Promise<string> {
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

function initialInventoryForm(category: UserInventoryCategory = 'APPAREL'): InventoryFormState {
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

function inventoryFormFromItem(item: UserInventoryItemProfile): InventoryFormState {
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

function inventoryItemInputFromForm(form: InventoryFormState): UserInventoryItemInput {
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

function inventoryPhotoInputFromForm(form: InventoryFormState): UserInventoryPhotoInput {
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

function inventoryUpdateInputFromForm(form: InventoryFormState): UserInventoryItemUpdateInput {
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

function upsertProductSnapshot(products: Product[], product: Product): Product[] {
  const existingIndex = products.findIndex((candidate) => candidate.id === product.id)
  if (existingIndex < 0) {
    return [product, ...products]
  }
  return products.map((candidate, index) => (index === existingIndex ? product : candidate))
}

function productSnapshotsForIds(products: Product[], ids: readonly ProductId[]): Product[] {
  const byId = new Map(products.map((product) => [product.id, product] as const))
  return ids.map((id) => byId.get(id)).filter((product): product is Product => Boolean(product))
}

function appendProductSnapshots(products: Product[], nextProducts: readonly Product[]): Product[] {
  const merged = [...products]
  const indexes = new Map(merged.map((product, index) => [product.id, index] as const))
  nextProducts.forEach((product) => {
    const index = indexes.get(product.id)
    if (index === undefined) {
      indexes.set(product.id, merged.length)
      merged.push(product)
      return
    }
    merged[index] = product
  })
  return merged
}

const PRODUCT_SEARCH_AGENT_NAMES: Record<string, string> = {
  discovery: 'Discovery agent',
  curator: 'Meant Curator agent',
  search: 'Search agent',
}

function productSearchAgentName(agent: string | null | undefined): string {
  if (!agent) {
    return 'Search agent'
  }
  return (
    PRODUCT_SEARCH_AGENT_NAMES[agent] ?? `${agent.charAt(0).toUpperCase()}${agent.slice(1)} agent`
  )
}

function upsertAgentActivity(
  activities: readonly AgentActivity[],
  event: Pick<UserProductSearchStreamEventProfile, 'agent' | 'label'>,
  state: AgentActivity['state'] = 'active',
): AgentActivity[] {
  const agent = event.agent ?? 'search'
  const label = event.label ?? 'Working'
  const next = {
    agent,
    label,
    state,
    updatedAt: Date.now(),
  }
  const existing = activities.findIndex((activity) => activity.agent === agent)
  if (existing < 0) {
    return [...activities, next]
  }
  return activities.map((activity, index) => (index === existing ? next : activity))
}

function advanceAgentActivity(
  activities: readonly AgentActivity[],
  event: Pick<UserProductSearchStreamEventProfile, 'agent' | 'label'>,
): AgentActivity[] {
  const agent = event.agent ?? 'search'
  return upsertAgentActivity(
    activities.map((activity) =>
      activity.agent !== agent && activity.state === 'active'
        ? { ...activity, state: 'done' }
        : activity,
    ),
    event,
  )
}

function merchantNameSet(merchant: MerchantProfile): ReadonlySet<string> {
  return new Set(
    [normalizedMerchantName(merchant.name), normalizedMerchantName(merchant.domain)].filter(
      Boolean,
    ),
  )
}

function merchantPrimarySearchValues(merchant: MerchantProfile): string[] {
  return [normalizedMerchantName(merchant.name), normalizedMerchantName(merchant.domain)].filter(
    Boolean,
  )
}

function merchantSearchValues(merchant: MerchantProfile): string[] {
  return [
    ...merchantPrimarySearchValues(merchant),
    normalizedMerchantName(merchant.description),
    normalizedMerchantName(merchant.advertisedMcpEndpoint),
    normalizedMerchantName(merchant.profileMcpEndpoint),
  ].filter(Boolean)
}

function merchantSearchRank(merchant: MerchantProfile, searchText: string): number {
  if (!searchText) {
    return 0
  }

  const primaryValues = merchantPrimarySearchValues(merchant)
  if (primaryValues.some((value) => value.startsWith(searchText))) {
    return 0
  }
  if (primaryValues.some((value) => value.includes(searchText))) {
    return 1
  }
  if (merchantSearchValues(merchant).some((value) => value.includes(searchText))) {
    return 2
  }
  return 3
}

function orderedMerchantMatches(
  merchants: readonly MerchantProfile[],
  merchantCounts: ReadonlyMap<string, number>,
  searchText: string,
): MerchantProfile[] {
  return merchants
    .map((merchant) => ({
      merchant,
      count: merchantCounts.get(merchant.id) ?? 0,
      rank: merchantSearchRank(merchant, searchText),
    }))
    .filter((item) => !searchText || item.rank < 3)
    .sort((left, right) => {
      if (left.rank !== right.rank) {
        return left.rank - right.rank
      }
      if (left.count !== right.count) {
        return right.count - left.count
      }
      return left.merchant.name.localeCompare(right.merchant.name, undefined, {
        sensitivity: 'base',
      })
    })
    .map((item) => item.merchant)
}

function productForMerchant(product: Product, merchant: MerchantProfile): Product | null {
  const merchantNames = merchantNameSet(merchant)
  const offers = product.offers.filter(
    (offer) =>
      offer.merchantId === merchant.id ||
      merchantNames.has(normalizedMerchantName(offer.merchantDomain)) ||
      merchantNames.has(normalizedMerchantName(offer.merchant)),
  )
  if (offers.length > 0) {
    return {
      ...product,
      offers,
      priceFrom: Math.min(...offers.map((offer) => offer.price)),
      merchants: 1,
    }
  }
  if (merchantNames.has(normalizedMerchantName(product.brand))) {
    return product
  }
  return null
}

function normalizeAssistantActionText(value: string): string {
  return value
    .toLowerCase()
    .replace(/[\u2018\u2019]/g, "'")
    .replace(/\s+/g, ' ')
    .trim()
}

function latestAssistantProductList(messages: readonly Message[]): readonly Product[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]
    if (message?.role === 'ai' && message.products && message.products.length > 0) {
      return message.products
    }
  }
  return []
}

function assistantProductTargetIndex(text: string, productCount: number): number | null {
  if (productCount <= 0) {
    return null
  }
  if (/\b(first|1st|top|best)\b/.test(text)) {
    return 0
  }
  if (/\b(second|2nd)\b/.test(text)) {
    return productCount > 1 ? 1 : null
  }
  if (/\b(third|3rd)\b/.test(text)) {
    return productCount > 2 ? 2 : null
  }
  if (/\b(fourth|4th)\b/.test(text)) {
    return productCount > 3 ? 3 : null
  }
  if (/\blast\b/.test(text)) {
    return productCount - 1
  }
  if (productCount === 1 && /\b(it|this|that|one|item|product)\b/.test(text)) {
    return 0
  }
  return null
}

function resolveAssistantProductAction(
  question: string,
  messages: readonly Message[],
): AssistantProductAction | null {
  const products = latestAssistantProductList(messages)
  if (products.length === 0) {
    return null
  }

  const text = normalizeAssistantActionText(question)
  const shouldAddToCart =
    /\b(add|put|place)\b.*\b(cart|bag)\b/.test(text) ||
    /\b(cart|bag)\b.*\b(add|put|place)\b/.test(text)
  const shouldOpen = /\b(open|view)\b/.test(text) || /\bdetails?\b/.test(text)
  if (!shouldAddToCart && !shouldOpen) {
    return null
  }

  const targetIndex = assistantProductTargetIndex(text, products.length)
  if (targetIndex === null) {
    return null
  }

  return {
    product: products[targetIndex],
    shouldAddToCart,
    shouldOpen,
  }
}

function ProductSearchLoading({
  label = 'Searching products across stores',
}: Readonly<{
  label?: string
}>) {
  return (
    <div
      className="mt-search-state mt-search-state-loading"
      role="status"
      aria-live="polite"
      aria-label={label}
    >
      <div className="mt-search-loader" aria-hidden="true">
        <span className="mt-search-loader-spark">
          <SparkMark size={14} />
        </span>
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
      </div>
      <span className="mt-search-loading-text">
        {label}
        <span className="mt-search-loading-dots" aria-hidden="true">
          <span>.</span>
          <span>.</span>
          <span>.</span>
        </span>
      </span>
    </div>
  )
}

function AgentActivityPanel({
  activities,
}: Readonly<{
  activities: readonly AgentActivity[]
}>) {
  if (activities.length === 0) {
    return null
  }
  return (
    <div className="mt-agent-rail" aria-live="polite">
      {activities.slice(-5).map((activity) => (
        <div className={`mt-agent-step ${activity.state}`} key={activity.agent}>
          <span className="mt-agent-orb" aria-hidden>
            <SparkMark size={11} />
          </span>
          <span className="mt-agent-copy">
            <span className="mt-agent-name mt-mono">{productSearchAgentName(activity.agent)}</span>
            <span className="mt-agent-label">{activity.label}</span>
          </span>
        </div>
      ))}
    </div>
  )
}

function MerchantIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M3 3h12l-.8 4a2 2 0 0 1-2 1.6H5.8a2 2 0 0 1-2-1.6L3 3zM4 8.6V15h10V8.6"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function SunIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.6" />
      <path
        d="M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M20 14.5A8 8 0 0 1 9.5 4a7 7 0 1 0 10.5 10.5z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function HeartIcon({ filled }: Readonly<{ filled: boolean }>) {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M9 15.5S2.5 11.5 2.5 6.8A3.3 3.3 0 0 1 9 5.2a3.3 3.3 0 0 1 6.5 1.6C15.5 11.5 9 15.5 9 15.5z"
        fill={filled ? 'var(--accent)' : 'none'}
        stroke={filled ? 'var(--accent)' : 'currentColor'}
        strokeWidth="1.4"
      />
    </svg>
  )
}

function ChevronIcon({
  direction,
  size = 18,
}: Readonly<{ direction: 'left' | 'right'; size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <path
        d={direction === 'left' ? 'M11 3 6 9l5 6' : 'M7 3l5 6-5 6'}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function HistoryIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <path
        d="M5.4 5.2H2.8V2.6M3 8.8a6 6 0 1 0 1.9-4.4L2.8 6.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M9 5.7V9l2.4 1.4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function PlusIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <path
        d="M9 3.8v10.4M3.8 9h10.4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  )
}

function ShareIcon({ size = 14 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden>
      <circle cx="4.5" cy="9" r="1.9" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="13.5" cy="4.5" r="1.9" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="13.5" cy="13.5" r="1.9" stroke="currentColor" strokeWidth="1.4" />
      <path d="M6.2 8 11.8 5.3M6.2 10l5.6 2.7" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  )
}

function CopyIcon({ size = 14 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden>
      <rect
        x="6.2"
        y="5.1"
        width="8"
        height="9.6"
        rx="1.5"
        stroke="currentColor"
        strokeWidth="1.4"
      />
      <path
        d="M4 11.9H3.8A1.8 1.8 0 0 1 2 10.1V4a1.8 1.8 0 0 1 1.8-1.8h5A1.8 1.8 0 0 1 10.6 4v.2"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  )
}

function SearchIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" aria-hidden>
      <circle cx="8" cy="8" r="4.6" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <path
        d="M11.5 11.5 15 15"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

function BookmarkIcon({
  filled = false,
  size = 14,
}: Readonly<{
  filled?: boolean
  size?: number
}>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill={filled ? 'currentColor' : 'none'}
      aria-hidden
    >
      <path
        d="M4 2h8v12l-4-2.8L4 14V2z"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function CollapseIcon({ collapsed }: Readonly<{ collapsed: boolean }>) {
  return (
    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d={collapsed ? 'M4 6l4 4 4-4' : 'M4 10l4-4 4 4'}
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function OpenIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path
        d="M5.5 3.5h7v7M12 4 4 12"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function flyToShelf(fromElement: HTMLElement | null, tone?: string | null) {
  if (!fromElement || typeof document === 'undefined') {
    return
  }
  const shelf = document.querySelector('.mt-shelf.open') ?? document.querySelector('.mt-shelf-tab')
  if (!(shelf instanceof HTMLElement)) {
    return
  }
  const from = fromElement.getBoundingClientRect()
  const to = shelf.getBoundingClientRect()
  const ghost = document.createElement('div')
  ghost.className = 'mt-fly-ghost'
  ghost.style.left = `${from.left + from.width / 2 - 15}px`
  ghost.style.top = `${from.top + from.height / 2 - 15}px`
  ghost.style.background = tone || 'var(--accent)'
  document.body.appendChild(ghost)
  const tx = to.left + to.width / 2 - (from.left + from.width / 2)
  const ty = to.top + to.height / 2 - (from.top + from.height / 2)
  const animation = ghost.animate(
    [
      { transform: 'translate(0, 0) scale(1)', opacity: 1, borderRadius: '9px' },
      {
        transform: `translate(${tx * 0.5}px, ${ty * 0.5 - 46}px) scale(.78)`,
        opacity: 1,
        borderRadius: '11px',
        offset: 0.6,
      },
      { transform: `translate(${tx}px, ${ty}px) scale(.2)`, opacity: 0, borderRadius: '50%' },
    ],
    { duration: 640, easing: 'cubic-bezier(.5,0,.2,1)' },
  )
  animation.onfinish = () => ghost.remove()
}

function flyMessageToChat(fromElement: HTMLElement | null, text: string) {
  if (!fromElement || typeof document === 'undefined') {
    return
  }
  const from = fromElement.getBoundingClientRect()
  const ghost = document.createElement('div')
  ghost.className = 'mt-fly-msg'
  ghost.textContent = text.length > 64 ? `${text.slice(0, 62)}...` : text
  ghost.style.left = `${from.left}px`
  ghost.style.top = `${from.top}px`
  ghost.style.maxWidth = `${Math.min(from.width, 360)}px`
  document.body.appendChild(ghost)
  const tx = window.innerWidth / 2 - (from.left + from.width / 2)
  const ty = window.innerHeight - 118 - from.top
  const animation = ghost.animate(
    [
      { transform: 'translate(0, 0) scale(1)', opacity: 0.96 },
      {
        transform: `translate(${tx * 0.35}px, ${ty * 0.55}px) scale(.94)`,
        opacity: 1,
        offset: 0.5,
      },
      { transform: `translate(${tx}px, ${ty}px) scale(.7)`, opacity: 0 },
    ],
    { duration: 680, easing: 'cubic-bezier(.5,0,.2,1)' },
  )
  animation.onfinish = () => ghost.remove()
}

function Avatar({
  user,
  size = 38,
}: Readonly<{
  user: UserAccount
  size?: number
}>) {
  const initial = (user.name.trim().charAt(0) || 'M').toUpperCase()
  if (user.avatar) {
    return (
      <img className="mt-ava-img" src={user.avatar} alt="" style={{ width: size, height: size }} />
    )
  }
  return (
    <span
      className="mt-ava-fallback"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.42) }}
    >
      {initial}
    </span>
  )
}

function MatchRing({
  value,
  size = 44,
  stroke = 3,
}: Readonly<{
  value: number
  size?: number
  stroke?: number
}>) {
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference * (1 - value / 100)
  return (
    <div className="mt-ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} aria-hidden>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--line)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={value >= 85 ? 'var(--accent)' : 'var(--muted)'}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="mt-mono mt-ring-num" style={{ fontSize: size * 0.26 }}>
        {value}
      </span>
    </div>
  )
}

function PrefChip({
  label,
  variant = 'muted',
  small = false,
}: Readonly<{
  label: string
  variant?: 'lit' | 'muted' | 'missed'
  small?: boolean
}>) {
  return (
    <span className={`mt-chip mt-chip-${variant} mt-chip-in ${small ? 'mt-chip-sm' : ''}`}>
      {variant === 'lit' && <span className="mt-chip-dot" />}
      {variant === 'missed' && <span className="mt-chip-x">x</span>}
      {label}
    </span>
  )
}

function InventorySignalBadge({
  product,
  compact = false,
}: Readonly<{
  product: Product
  compact?: boolean
}>) {
  const label = inventoryRelationshipLabel(product.inventoryRelationship)
  if (!label) {
    return null
  }
  return (
    <span
      className={`mt-inv-signal ${compact ? 'compact' : ''} ${product.inventoryRelationship?.toLowerCase()}`}
    >
      <span className="mt-inv-signal-dot" />
      {label}
      {!compact && product.inventoryItemName ? (
        <span className="mt-inv-signal-item">{product.inventoryItemName}</span>
      ) : null}
    </span>
  )
}

function renderAssistantMarkdown(text: string, pending?: boolean): ReactNode {
  if (!text) {
    return pending ? 'Thinking...' : ''
  }

  const lines = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index].trim()
    if (!line) {
      index += 1
      continue
    }

    const orderedMatch = line.match(/^\d+\.\s+(.+)$/)
    if (orderedMatch) {
      const items: ReactNode[] = []
      while (index < lines.length) {
        const itemMatch = lines[index].trim().match(/^\d+\.\s+(.+)$/)
        if (!itemMatch) {
          if (!lines[index].trim()) {
            index += 1
            continue
          }
          break
        }
        items.push(
          <li key={`ol-${blocks.length}-${items.length}`}>
            {renderMarkdownInline(itemMatch[1], `ol-${blocks.length}-${items.length}`)}
          </li>,
        )
        index += 1
      }
      blocks.push(<ol key={`block-${blocks.length}`}>{items}</ol>)
      continue
    }

    const bulletMatch = line.match(/^[-*]\s+(.+)$/)
    if (bulletMatch) {
      const items: ReactNode[] = []
      while (index < lines.length) {
        const itemMatch = lines[index].trim().match(/^[-*]\s+(.+)$/)
        if (!itemMatch) {
          if (!lines[index].trim()) {
            index += 1
            continue
          }
          break
        }
        items.push(
          <li key={`ul-${blocks.length}-${items.length}`}>
            {renderMarkdownInline(itemMatch[1], `ul-${blocks.length}-${items.length}`)}
          </li>,
        )
        index += 1
      }
      blocks.push(<ul key={`block-${blocks.length}`}>{items}</ul>)
      continue
    }

    const paragraphLines: string[] = []
    while (index < lines.length) {
      const current = lines[index].trim()
      if (!current || current.match(/^\d+\.\s+.+$/) || current.match(/^[-*]\s+.+$/)) {
        break
      }
      paragraphLines.push(current)
      index += 1
    }
    blocks.push(
      <p key={`block-${blocks.length}`}>
        {renderMarkdownInline(paragraphLines.join(' '), `p-${blocks.length}`)}
      </p>,
    )
  }

  return blocks
}

function renderMarkdownInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = []
  const pattern = /(\*\*[^*]+\*\*|\*[^*]+\*)/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index))
    }
    const token = match[0]
    const key = `${keyPrefix}-${match.index}`
    if (token.startsWith('**') && token.endsWith('**')) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>)
    } else {
      nodes.push(<em key={key}>{token.slice(1, -1)}</em>)
    }
    lastIndex = match.index + token.length
  }

  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex))
  }
  return nodes
}

function AskThinkingIndicator() {
  return (
    <span className="mt-msg-thinking" role="status" aria-label="Ask Meant is thinking">
      <span aria-hidden="true" />
      <span aria-hidden="true" />
      <span aria-hidden="true" />
    </span>
  )
}

function renderAssistantMessageContent(message: Message): ReactNode {
  if (message.pending && !message.text) {
    return <AskThinkingIndicator />
  }

  return (
    <>
      {renderAssistantMarkdown(message.text, message.pending)}
      {message.pending ? <span className="mt-msg-cursor" aria-hidden="true" /> : null}
    </>
  )
}

function AskThread({
  messages,
  onProductOpen,
}: Readonly<{
  messages: readonly Message[]
  onProductOpen?: (product: Product) => void
}>) {
  const endRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (endRef.current) {
      endRef.current.scrollTop = endRef.current.scrollHeight
    }
  }, [messages])

  if (messages.length === 0) {
    return null
  }

  return (
    <div className="mt-ask-thread" ref={endRef}>
      {messages.map((message, index) => {
        const streaming = message.role === 'ai' && Boolean(message.pending)
        return (
          <div
            key={`${message.role}-${index}`}
            className={`mt-msg mt-msg-${message.role} ${streaming ? 'mt-msg-streaming' : ''}`}
            style={{ '--mt-msg-index': index } as CSSProperties}
          >
            {message.role === 'ai' ? (
              <span className="mt-msg-av">
                <SparkMark size={12} />
              </span>
            ) : null}
            <div className="mt-msg-stack">
              <div className={`mt-msg-bubble ${streaming ? 'mt-msg-bubble-streaming' : ''}`}>
                {message.role === 'ai' ? renderAssistantMessageContent(message) : message.text}
              </div>
              {message.products && message.products.length > 0 ? (
                <div className="mt-msg-products">
                  {message.products.map((product, productIndex) => (
                    <button
                      key={product.id}
                      className="mt-msg-product"
                      type="button"
                      onClick={() => onProductOpen?.(product)}
                      disabled={!onProductOpen}
                      style={{ '--mt-product-index': productIndex } as CSSProperties}
                    >
                      <div className="mt-msg-product-media">
                        <ProductArtwork product={product} label={product.category.toLowerCase()} />
                      </div>
                      <span className="mt-msg-product-main">
                        <span className="mt-msg-product-name">{product.name}</span>
                        <span className="mt-mono mt-msg-product-meta">
                          {product.match}% · {money(product.priceFrom)}
                        </span>
                      </span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function AskComposer({
  placeholder,
  suggestions,
  showChips,
  onAsk,
  autoFocus = false,
  disabled = false,
}: Readonly<{
  placeholder: string
  suggestions: readonly string[]
  showChips: boolean
  onAsk: (question: string) => void
  autoFocus?: boolean
  disabled?: boolean
}>) {
  const [value, setValue] = useState('')
  const [sentPulse, setSentPulse] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const sentPulseTimeoutRef = useRef<number | null>(null)

  useEffect(() => {
    if (autoFocus) {
      inputRef.current?.focus()
    }
  }, [autoFocus])

  useEffect(
    () => () => {
      if (sentPulseTimeoutRef.current !== null) {
        window.clearTimeout(sentPulseTimeoutRef.current)
      }
    },
    [],
  )

  const send = (text?: string) => {
    if (disabled) {
      return
    }
    const question = (text ?? value).trim()
    if (!question) {
      return
    }
    setSentPulse(true)
    if (sentPulseTimeoutRef.current !== null) {
      window.clearTimeout(sentPulseTimeoutRef.current)
    }
    sentPulseTimeoutRef.current = window.setTimeout(() => {
      setSentPulse(false)
      sentPulseTimeoutRef.current = null
    }, 520)
    setValue('')
    onAsk(question)
  }

  const hasValue = value.trim().length > 0
  const canSend = hasValue && !disabled

  return (
    <div className={`mt-ask-composer ${disabled ? 'mt-ask-composer-disabled' : ''}`}>
      {showChips && suggestions.length > 0 ? (
        <div className="mt-ask-chips">
          {suggestions.map((suggestion, index) => (
            <button
              key={suggestion}
              className="mt-ask-chip"
              type="button"
              onClick={() => send(suggestion)}
              disabled={disabled}
              style={{ '--mt-chip-index': index } as CSSProperties}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
      <form
        className={`mt-ask-bar ${hasValue ? 'mt-ask-writing' : ''} ${sentPulse ? 'mt-ask-sent' : ''} ${disabled ? 'mt-ask-busy' : ''}`}
        onSubmit={(event) => {
          event.preventDefault()
          send()
        }}
      >
        <span className="mt-ask-spark">
          <SparkMark size={17} />
        </span>
        <input
          ref={inputRef}
          className="mt-ask-input"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          aria-label="Ask Meant message"
        />
        <button type="submit" className="mt-ask-go" aria-label="Ask" disabled={!canSend}>
          <svg width="16" height="16" viewBox="0 0 18 18" fill="none" aria-hidden>
            <path
              d="M3.5 9h11M9.5 4l5 5-5 5"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </form>
    </div>
  )
}

let dustSequence = 0

function DustWrap({
  children,
  side,
  onGone,
  onSetAside,
  onShelfDragStart,
  onCopy,
  saved,
}: Readonly<{
  children: ReactNode
  side: 'you' | 'meant'
  onGone: () => void
  onSetAside?: (sourceElement: HTMLElement) => void
  onShelfDragStart?: (event: ReactDragEvent<HTMLElement>) => void
  onCopy?: () => void
  saved: boolean
}>) {
  const [dusting, setDusting] = useState(false)
  const [copied, setCopied] = useState(false)
  const idRef = useRef<string>('')
  const copiedTimerRef = useRef<number | null>(null)
  const displacementRef = useRef<SVGFEDisplacementMapElement | null>(null)
  const blurRef = useRef<SVGFEGaussianBlurElement | null>(null)

  if (!idRef.current) {
    dustSequence += 1
    idRef.current = `mtdust-${dustSequence}`
  }

  useEffect(() => {
    if (!dusting) {
      return undefined
    }
    let frame = 0
    let startedAt = 0
    const duration = 1050
    const step = (time: number) => {
      if (!startedAt) {
        startedAt = time
      }
      const progress = Math.min(1, (time - startedAt) / duration)
      const eased = progress * progress
      displacementRef.current?.setAttribute('scale', (eased * 140).toFixed(1))
      blurRef.current?.setAttribute('stdDeviation', (eased * 1.8).toFixed(2))
      if (progress < 1) {
        frame = window.requestAnimationFrame(step)
        return
      }
      window.setTimeout(onGone, 20)
    }
    frame = window.requestAnimationFrame(step)
    return () => window.cancelAnimationFrame(frame)
  }, [dusting, onGone])

  useEffect(
    () => () => {
      if (copiedTimerRef.current !== null) {
        window.clearTimeout(copiedTimerRef.current)
      }
    },
    [],
  )

  const copy = () => {
    onCopy?.()
    setCopied(true)
    if (copiedTimerRef.current !== null) {
      window.clearTimeout(copiedTimerRef.current)
    }
    copiedTimerRef.current = window.setTimeout(() => setCopied(false), 1400)
  }

  return (
    <div className={`mt-dustwrap side-${side} ${dusting ? 'dusting' : ''}`}>
      <div
        className="mt-dust-inner"
        style={dusting ? { filter: `url(#${idRef.current})` } : undefined}
      >
        {children}
      </div>
      {!dusting ? (
        <div className={`mt-msg-tools side-${side}`}>
          {onSetAside ? (
            <button
              className={`mt-msg-tool mt-tool-shelf ${saved ? 'on' : ''}`}
              type="button"
              draggable={Boolean(onShelfDragStart)}
              onClick={(event) => onSetAside(event.currentTarget)}
              onDragStart={onShelfDragStart}
              onDragEnd={() => document.body.classList.remove('mt-dragging')}
              aria-label={saved ? 'On your shelf' : 'Set aside on shelf'}
              title={saved ? 'On your shelf' : 'Click or drag to set aside'}
            >
              <BookmarkIcon filled={saved} size={12} />
            </button>
          ) : null}
          {onCopy ? (
            <button
              className={`mt-msg-tool mt-tool-copy ${copied ? 'done' : ''}`}
              type="button"
              draggable={false}
              onClick={copy}
              onDragStart={(event) => event.stopPropagation()}
              aria-label={copied ? 'Copied message' : 'Copy message'}
              title={copied ? 'Copied' : 'Copy message'}
            >
              <CopyIcon size={12} />
            </button>
          ) : null}
          <button
            className="mt-msg-tool mt-tool-del"
            type="button"
            onClick={() => setDusting(true)}
            aria-label="Delete message"
            title="Delete message"
          >
            <CloseIcon size={12} />
          </button>
        </div>
      ) : null}
      {dusting ? (
        <svg className="mt-dust-svg" aria-hidden="true" width="0" height="0">
          <defs>
            <filter
              id={idRef.current}
              x="-40%"
              y="-40%"
              width="180%"
              height="180%"
              colorInterpolationFilters="sRGB"
            >
              <feTurbulence
                type="fractalNoise"
                baseFrequency="0.7"
                numOctaves="2"
                seed={dustSequence}
                result="n"
              />
              <feDisplacementMap
                ref={displacementRef}
                in="SourceGraphic"
                in2="n"
                scale="0"
                xChannelSelector="R"
                yChannelSelector="G"
                result="d"
              />
              <feGaussianBlur ref={blurRef} in="d" stdDeviation="0" />
            </filter>
          </defs>
        </svg>
      ) : null}
    </div>
  )
}

function DustingContainer({
  children,
  dusting,
  className,
  onGone,
}: Readonly<{
  children: ReactNode
  dusting: boolean
  className?: string
  onGone: () => void
}>) {
  const idRef = useRef<string>('')
  const displacementRef = useRef<SVGFEDisplacementMapElement | null>(null)
  const blurRef = useRef<SVGFEGaussianBlurElement | null>(null)

  if (!idRef.current) {
    dustSequence += 1
    idRef.current = `mtdust-solo-${dustSequence}`
  }

  useEffect(() => {
    if (!dusting) {
      return undefined
    }
    let frame = 0
    let startedAt = 0
    const duration = 1050
    const step = (time: number) => {
      if (!startedAt) {
        startedAt = time
      }
      const progress = Math.min(1, (time - startedAt) / duration)
      const eased = progress * progress
      displacementRef.current?.setAttribute('scale', (eased * 140).toFixed(1))
      blurRef.current?.setAttribute('stdDeviation', (eased * 1.8).toFixed(2))
      if (progress < 1) {
        frame = window.requestAnimationFrame(step)
        return
      }
      window.setTimeout(onGone, 20)
    }
    frame = window.requestAnimationFrame(step)
    return () => window.cancelAnimationFrame(frame)
  }, [dusting, onGone])

  return (
    <div
      className={`${className ?? ''} ${dusting ? 'mt-dusting-solo' : ''}`.trim() || undefined}
      style={dusting ? { filter: `url(#${idRef.current})` } : undefined}
    >
      {children}
      {dusting ? (
        <svg className="mt-dust-svg" aria-hidden="true" width="0" height="0">
          <defs>
            <filter
              id={idRef.current}
              x="-40%"
              y="-40%"
              width="180%"
              height="180%"
              colorInterpolationFilters="sRGB"
            >
              <feTurbulence
                type="fractalNoise"
                baseFrequency="0.7"
                numOctaves="2"
                seed={dustSequence}
                result="n"
              />
              <feDisplacementMap
                ref={displacementRef}
                in="SourceGraphic"
                in2="n"
                scale="0"
                xChannelSelector="R"
                yChannelSelector="G"
                result="d"
              />
              <feGaussianBlur ref={blurRef} in="d" stdDeviation="0" />
            </filter>
          </defs>
        </svg>
      ) : null}
    </div>
  )
}

function ProductContextChip({ product }: Readonly<{ product: Product }>) {
  return (
    <span className="mt-ct-attach-chip product">
      <span className="mt-ct-attach-thumb solid" style={{ background: product.tone }}>
        {product.imageUrl ? (
          <img className="mt-product-img" src={product.imageUrl} alt="" loading="lazy" />
        ) : null}
      </span>
      <span className="mt-ct-attach-label">Re: {product.name}</span>
    </span>
  )
}

function ShelfThumbs({ thumbs }: Readonly<{ thumbs: readonly ShelfThumb[] }>) {
  if (thumbs.length === 0) {
    return null
  }
  return (
    <div className="mt-shelf-thumbs">
      {thumbs.map((thumb) => (
        <span
          key={`${thumb.name}-${thumb.tone}`}
          className="mt-shelf-thumb"
          title={thumb.name}
          style={{ background: thumb.tone }}
        >
          {thumb.imageUrl ? <img src={thumb.imageUrl} alt="" loading="lazy" /> : null}
        </span>
      ))}
    </div>
  )
}

function parseShelfDragPayload(dataTransfer: DataTransfer): ShelfDragPayload | null {
  const raw = dataTransfer.getData(SHELF_DRAG_MIME)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as ShelfDragPayload
    if (parsed.kind === 'message' || parsed.kind === 'product') {
      return parsed
    }
  } catch {
    return null
  }
  return null
}

function ShelfCard({
  item,
  product,
  onRemove,
  onToggleCollapse,
  onFind,
  onFindProduct,
  onOpenProduct,
}: Readonly<{
  item: ShelfItem
  product?: Product | null
  onRemove: (uid: string) => void
  onToggleCollapse: (uid: string) => void
  onFind: (messageId: string) => void
  onFindProduct: (productId: ProductId) => void
  onOpenProduct: (product: Product) => void
}>) {
  const [dusting, setDusting] = useState(false)
  const tools = (
    <div className="mt-shelf-card-tools">
      <button
        type="button"
        onClick={() => onToggleCollapse(item.uid)}
        title={item.collapsed ? 'Expand' : 'Minimize'}
        aria-label={item.collapsed ? 'Expand shelf item' : 'Minimize shelf item'}
      >
        <CollapseIcon collapsed={item.collapsed} />
      </button>
      <button
        type="button"
        onClick={() => setDusting(true)}
        title="Remove from shelf"
        aria-label="Remove from shelf"
      >
        <CloseIcon size={11} />
      </button>
    </div>
  )

  if (item.kind === 'product') {
    const snapshot = item.snapshot
    const title = product?.name ?? snapshot.name
    const thumbUrl = product?.imageUrl ?? snapshot.imageUrl
    const tone = product?.tone ?? snapshot.tone
    return (
      <DustingContainer dusting={dusting} onGone={() => onRemove(item.uid)}>
        <div className="mt-shelf-card product">
          <div className="mt-shelf-card-head">
            <span className="mt-shelf-kind mt-mono">Product</span>
            {tools}
          </div>
          {item.collapsed ? (
            <button
              className="mt-shelf-collapsed"
              type="button"
              onClick={() => onToggleCollapse(item.uid)}
            >
              <span className="mt-shelf-thumb" style={{ background: tone }}>
                {thumbUrl ? <img src={thumbUrl} alt="" loading="lazy" /> : null}
              </span>
              <span className="mt-shelf-collapsed-name">{title}</span>
            </button>
          ) : (
            <>
              <button
                className="mt-shelf-prod"
                type="button"
                onClick={() => {
                  if (product) {
                    onOpenProduct(product)
                  }
                }}
                disabled={!product}
              >
                <span className="mt-shelf-prod-thumb" style={{ background: tone }}>
                  {thumbUrl ? <img src={thumbUrl} alt="" loading="lazy" /> : null}
                </span>
                <span className="mt-shelf-prod-info">
                  <span className="mt-mono mt-shelf-prod-brand">
                    {product?.brand ?? snapshot.brand}
                  </span>
                  <span className="mt-shelf-prod-name">{title}</span>
                  <span className="mt-shelf-prod-price">
                    {money(product?.priceFrom ?? snapshot.priceFrom)}
                    <span className="mt-shelf-prod-from">
                      {' '}
                      from {product?.merchants ?? snapshot.merchants} stores
                    </span>
                  </span>
                </span>
              </button>
              <div className="mt-shelf-actions">
                {product ? (
                  <button
                    className="mt-shelf-find"
                    type="button"
                    onClick={() => onOpenProduct(product)}
                  >
                    <OpenIcon /> Open product
                  </button>
                ) : null}
                <button
                  className="mt-shelf-find"
                  type="button"
                  onClick={() => onFindProduct(item.productId)}
                >
                  <SearchIcon size={12} /> Find in chat
                </button>
              </div>
            </>
          )}
        </div>
      </DustingContainer>
    )
  }

  const snapshot = item.snapshot
  return (
    <DustingContainer dusting={dusting} onGone={() => onRemove(item.uid)}>
      <div className={`mt-shelf-card ${snapshot.side}`}>
        <div className="mt-shelf-card-head">
          <span className="mt-shelf-kind mt-mono">{snapshot.title}</span>
          {tools}
        </div>
        {item.collapsed ? (
          <button
            className="mt-shelf-collapsed"
            type="button"
            onClick={() => onToggleCollapse(item.uid)}
          >
            {snapshot.thumbs[0] ? (
              <span className="mt-shelf-thumb" style={{ background: snapshot.thumbs[0].tone }}>
                {snapshot.thumbs[0].imageUrl ? (
                  <img src={snapshot.thumbs[0].imageUrl} alt="" loading="lazy" />
                ) : null}
              </span>
            ) : null}
            <span className="mt-shelf-collapsed-name">{snapshot.text || snapshot.title}</span>
          </button>
        ) : (
          <>
            {snapshot.text ? <p className="mt-shelf-text">{snapshot.text}</p> : null}
            <ShelfThumbs thumbs={snapshot.thumbs} />
          </>
        )}
        <button className="mt-shelf-find" type="button" onClick={() => onFind(item.messageId)}>
          <SearchIcon size={12} /> Find in chat
        </button>
      </div>
    </DustingContainer>
  )
}

function Shelf({
  open,
  items,
  productsById,
  onToggle,
  onAddMessage,
  onAddProduct,
  onRemove,
  onClear,
  onToggleCollapse,
  onFind,
  onFindProduct,
  onOpenProduct,
}: Readonly<{
  open: boolean
  items: readonly ShelfItem[]
  productsById: ReadonlyMap<ProductId, Product>
  onToggle: () => void
  onAddMessage: (payload: Extract<ShelfDragPayload, { kind: 'message' }>) => void
  onAddProduct: (snapshot: ShelfProductSnapshot) => void
  onRemove: (uid: string) => void
  onClear: () => void
  onToggleCollapse: (uid: string) => void
  onFind: (messageId: string) => void
  onFindProduct: (productId: ProductId) => void
  onOpenProduct: (product: Product) => void
}>) {
  const [over, setOver] = useState(false)
  const [clearing, setClearing] = useState(false)
  const [width, setWidth] = useStoredState('meant.shelfW', 340)
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const safeWidth = clampNumber(width, SHELF_MIN_WIDTH, SHELF_MAX_WIDTH)

  useEffect(() => {
    document.documentElement.style.setProperty('--shelf-w', `${safeWidth}px`)
  }, [safeWidth])

  useEffect(() => {
    document.body.classList.toggle('shelf-open', open)
    return () => document.body.classList.remove('shelf-open')
  }, [open])

  useEffect(
    () => () => {
      resizeCleanupRef.current?.()
    },
    [],
  )

  useEffect(() => {
    if (items.length === 0 && clearing) {
      setClearing(false)
    }
  }, [clearing, items.length])

  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) {
      return
    }
    event.preventDefault()
    resizeCleanupRef.current?.()
    document.body.classList.add('mt-shelf-resizing')
    const move = (moveEvent: PointerEvent) => {
      setWidth(clampNumber(window.innerWidth - moveEvent.clientX, SHELF_MIN_WIDTH, SHELF_MAX_WIDTH))
    }
    const cleanup = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', cleanup)
      window.removeEventListener('pointercancel', cleanup)
      document.body.classList.remove('mt-shelf-resizing')
      resizeCleanupRef.current = null
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', cleanup, { once: true })
    window.addEventListener('pointercancel', cleanup, { once: true })
    resizeCleanupRef.current = cleanup
  }

  const drop = (event: ReactDragEvent<HTMLElement>) => {
    event.preventDefault()
    setOver(false)
    document.body.classList.remove('mt-dragging')
    const payload = parseShelfDragPayload(event.dataTransfer)
    if (!payload) {
      return
    }
    if (payload.kind === 'message') {
      onAddMessage(payload)
      return
    }
    onAddProduct(payload.snapshot)
  }

  return (
    <>
      <button
        className="mt-shelf-tab"
        type="button"
        onClick={onToggle}
        title="Your shelf - messages and products you set aside"
        aria-label={open ? 'Hide shelf' : 'Show shelf'}
        aria-expanded={open}
      >
        <BookmarkIcon filled={items.length > 0} size={15} />
        {items.length > 0 ? (
          <span className="mt-shelf-tab-count mt-mono">{items.length}</span>
        ) : null}
      </button>
      <aside
        className={`mt-shelf ${open ? 'open' : ''} ${over ? 'over' : ''}`}
        style={{ width: `${safeWidth}px` }}
        onDragOver={(event) => {
          event.preventDefault()
          event.dataTransfer.dropEffect = 'copy'
          setOver(true)
        }}
        onDragLeave={(event) => {
          const nextTarget = event.relatedTarget
          if (!(nextTarget instanceof Node) || !event.currentTarget.contains(nextTarget)) {
            setOver(false)
          }
        }}
        onDrop={drop}
      >
        <div
          className="mt-shelf-resize"
          onPointerDown={startResize}
          title="Drag to resize the shelf"
        >
          <span />
        </div>
        <div className="mt-shelf-head">
          <div className="mt-shelf-title-wrap">
            <span className="mt-shelf-title">Shelf</span>
            <span className="mt-mono mt-shelf-sub">Set aside · spans all chats</span>
          </div>
          <div className="mt-shelf-head-tools">
            {items.length > 0 ? (
              <button
                className="mt-shelf-clear"
                type="button"
                onClick={() => setClearing(true)}
                disabled={clearing}
              >
                Clear
              </button>
            ) : null}
            <button
              className="mt-shelf-close"
              type="button"
              onClick={onToggle}
              title="Hide shelf"
              aria-label="Hide shelf"
            >
              <ChevronIcon direction="right" size={14} />
            </button>
          </div>
        </div>
        <div className="mt-shelf-body">
          {items.length === 0 ? (
            <div className="mt-shelf-empty">
              <span className="mt-shelf-empty-mark">
                <BookmarkIcon size={22} />
              </span>
              <p className="mt-shelf-empty-title">Nothing set aside yet</p>
              <p className="mt-shelf-empty-sub">
                Drag any message or product over here, or tap the bookmark on hover, to keep it
                handy and jump back later.
              </p>
            </div>
          ) : (
            <DustingContainer
              className="mt-shelf-clear-region"
              dusting={clearing}
              onGone={() => {
                setClearing(false)
                onClear()
              }}
            >
              {items.map((item) => (
                <ShelfCard
                  key={item.uid}
                  item={item}
                  product={item.kind === 'product' ? productsById.get(item.productId) : null}
                  onRemove={onRemove}
                  onToggleCollapse={onToggleCollapse}
                  onFind={onFind}
                  onFindProduct={onFindProduct}
                  onOpenProduct={onOpenProduct}
                />
              ))}
              <div className="mt-shelf-dropzone">Drop here to set aside</div>
            </DustingContainer>
          )}
        </div>
      </aside>
    </>
  )
}

function FloatingAsk({
  contextLabel,
  context,
  suggestions,
  preferences,
  onProducts,
  onProductOpen,
  onAddProductToCart,
  hidden = false,
}: Readonly<{
  contextLabel: string
  context: AssistantChatContextInput
  suggestions: readonly string[]
  preferences: readonly Preference[]
  onProducts: (products: readonly Product[], sourceQuery: string) => void
  onProductOpen: (product: Product) => void
  onAddProductToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  hidden?: boolean
}>) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [conversationHistory, setConversationHistory] = useState<
    UserAssistantConversationSummaryProfile[]
  >([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [loading, setLoading] = useState(false)
  const [panelSize, setPanelSize] = useStoredState<AskPanelSize>(
    'meant.askPanelSize',
    ASK_PANEL_DEFAULT_SIZE,
  )
  const resizeCleanupRef = useRef<(() => void) | null>(null)
  const assistantAbortRef = useRef<AbortController | null>(null)
  const historyAbortRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)
  const safePanelSize = normalizedAskPanelSize(panelSize)
  const panelStyle = {
    '--mt-askpanel-width': `${safePanelSize.width}px`,
    '--mt-askpanel-height': `${safePanelSize.height}px`,
  } as CSSProperties

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      resizeCleanupRef.current?.()
      assistantAbortRef.current?.abort()
      historyAbortRef.current?.abort()
    }
  }, [])

  const restoreConversation = useCallback(
    (conversation: UserAssistantConversationProfile) => {
      setConversationId(conversation.conversationId)
      setMessages(messagesFromAssistantConversation(conversation, preferences))
      const summary = assistantConversationSummary(conversation)
      if (summary) {
        setConversationHistory((current) => upsertAssistantConversationSummary(current, summary))
      }
    },
    [preferences],
  )

  const loadConversationHistory = useCallback(
    (restoreLatest: boolean) => {
      historyAbortRef.current?.abort()
      const controller = new AbortController()
      historyAbortRef.current = controller
      setHistoryLoading(true)

      const run = async () => {
        const history = await getAssistantConversations({ signal: controller.signal })
        if (controller.signal.aborted || !mountedRef.current) {
          return
        }
        setConversationHistory(history)
        setHistoryLoaded(true)

        if (restoreLatest) {
          const latest = history[0]
          if (!latest) {
            setConversationId(null)
            setMessages([])
            return
          }
          const conversation = await getAssistantConversation(latest.conversationId, {
            signal: controller.signal,
          })
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          restoreConversation(conversation)
        }
      }

      run()
        .catch(() => {
          if (mountedRef.current && !controller.signal.aborted) {
            setHistoryLoaded(true)
          }
        })
        .finally(() => {
          if (historyAbortRef.current === controller) {
            historyAbortRef.current = null
          }
          if (mountedRef.current && !controller.signal.aborted) {
            setHistoryLoading(false)
          }
        })
    },
    [restoreConversation],
  )

  const loadConversation = useCallback(
    (nextConversationId: string) => {
      historyAbortRef.current?.abort()
      const controller = new AbortController()
      historyAbortRef.current = controller
      setHistoryLoading(true)

      getAssistantConversation(nextConversationId, { signal: controller.signal })
        .then((conversation) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          restoreConversation(conversation)
        })
        .catch(() => undefined)
        .finally(() => {
          if (historyAbortRef.current === controller) {
            historyAbortRef.current = null
          }
          if (mountedRef.current && !controller.signal.aborted) {
            setHistoryLoading(false)
          }
        })
    },
    [restoreConversation],
  )

  useEffect(() => {
    if (open && !historyLoaded) {
      loadConversationHistory(false)
    }
  }, [historyLoaded, loadConversationHistory, open])

  const startPanelResize = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0) {
      return
    }
    event.preventDefault()
    resizeCleanupRef.current?.()

    const startX = event.clientX
    const startY = event.clientY
    const startSize = normalizedAskPanelSize(panelSize)
    const previousUserSelect = document.body.style.userSelect
    document.body.style.userSelect = 'none'

    let cleanup = () => {}
    const onMove = (moveEvent: PointerEvent) => {
      const maxSize = askPanelViewportMax()
      setPanelSize({
        width: clampNumber(
          startSize.width + startX - moveEvent.clientX,
          ASK_PANEL_MIN_WIDTH,
          maxSize.width,
        ),
        height: clampNumber(
          startSize.height + startY - moveEvent.clientY,
          ASK_PANEL_MIN_HEIGHT,
          maxSize.height,
        ),
      })
    }
    cleanup = () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', cleanup)
      window.removeEventListener('pointercancel', cleanup)
      document.body.style.userSelect = previousUserSelect
      resizeCleanupRef.current = null
    }

    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', cleanup, { once: true })
    window.addEventListener('pointercancel', cleanup, { once: true })
    resizeCleanupRef.current = cleanup
  }

  const updateStreamingMessage = (update: (message: Message) => Message) => {
    setMessages((current) => {
      const next = [...current]
      const index = findLastAssistantMessageIndex(next)
      if (index < 0) {
        return current
      }
      next[index] = update(next[index])
      return next
    })
  }

  const selectConversation = (nextConversationId: string) => {
    if (loading || historyLoading || nextConversationId === conversationId) {
      return
    }
    setHistoryOpen(false)
    loadConversation(nextConversationId)
  }

  const startNewConversation = () => {
    if (loading) {
      return
    }
    setConversationId(null)
    setMessages([])
    setHistoryOpen(false)
  }

  const executeProductAction = (question: string, action: AssistantProductAction) => {
    assistantAbortRef.current?.abort()
    const controller = new AbortController()
    assistantAbortRef.current = controller
    setLoading(true)
    setHistoryOpen(false)
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: '', pending: true },
    ])

    const run = async () => {
      const offer = action.shouldAddToCart ? cartableOfferForProduct(action.product) : null
      let added = false
      let addAttempted = false

      if (action.shouldAddToCart && offer) {
        addAttempted = true
        added = await onAddProductToCart(action.product, offer)
      }

      if (controller.signal.aborted || !mountedRef.current) {
        return
      }

      if (action.shouldOpen) {
        onProductOpen(action.product)
      }

      const text = (() => {
        if (action.shouldAddToCart && !offer) {
          return action.shouldOpen
            ? `I opened ${action.product.name}, but this item is not available for merchant checkout.`
            : `${action.product.name} is not available for merchant checkout.`
        }
        if (addAttempted && !added) {
          return action.shouldOpen
            ? `I opened ${action.product.name}, but could not add it to the merchant cart.`
            : `I could not add ${action.product.name} to the merchant cart.`
        }
        if (action.shouldAddToCart && action.shouldOpen) {
          return `I added ${action.product.name} to your cart and opened its details.`
        }
        if (action.shouldAddToCart) {
          return `I added ${action.product.name} to your cart.`
        }
        return `I opened ${action.product.name}.`
      })()

      updateStreamingMessage((message) => ({
        ...message,
        text,
        products: [action.product],
        pending: false,
      }))
    }

    run()
      .catch(() => {
        if (controller.signal.aborted || !mountedRef.current) {
          return
        }
        updateStreamingMessage((message) => ({
          ...message,
          text: `I could not update ${action.product.name} right now. Try again in a moment.`,
          products: [action.product],
          pending: false,
        }))
      })
      .finally(() => {
        if (assistantAbortRef.current === controller) {
          assistantAbortRef.current = null
        }
        if (mountedRef.current) {
          setLoading(false)
        }
      })
  }

  const ask = (question: string) => {
    if (loading) {
      return
    }
    const productAction = resolveAssistantProductAction(question, messages)
    if (productAction) {
      executeProductAction(question, productAction)
      return
    }
    assistantAbortRef.current?.abort()
    const controller = new AbortController()
    assistantAbortRef.current = controller
    let streamedText = ''
    setLoading(true)
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: '', pending: true },
    ])
    streamAssistantMessage(
      {
        conversationId,
        message: question,
        context,
        signal: controller.signal,
      },
      {
        onMetadata: (event) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          setConversationId(event.conversationId)
          if (event.conversationId) {
            const metadataConversationId = event.conversationId
            const normalizedTitle = question.replace(/\s+/g, ' ').trim()
            const title =
              normalizedTitle.length > 80 ? `${normalizedTitle.slice(0, 77)}...` : normalizedTitle
            const timestamp = new Date().toISOString()
            setConversationHistory((current) =>
              upsertAssistantConversationSummary(current, {
                conversationId: metadataConversationId,
                title,
                createdAt: timestamp,
                updatedAt: timestamp,
              }),
            )
          }
        },
        onDelta: (text) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          streamedText += text
          updateStreamingMessage((message) => ({
            ...message,
            text: streamedText,
            pending: true,
          }))
        },
        onDone: (event) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          const products = event.products.map((product) =>
            productFromSearchResult(product, preferences),
          )
          updateStreamingMessage((message) => ({
            ...message,
            text: event.text ?? streamedText,
            products,
            pending: false,
          }))
          if (products.length > 0) {
            onProducts(products, question)
          }
          loadConversationHistory(false)
        },
        onError: (message) => {
          if (controller.signal.aborted || !mountedRef.current) {
            return
          }
          updateStreamingMessage((current) => ({
            ...current,
            text: message,
            pending: false,
          }))
        },
      },
    )
      .catch(() => {
        if (!mountedRef.current) {
          return
        }
        if (controller.signal.aborted) {
          updateStreamingMessage((message) => ({
            ...message,
            text: streamedText || 'Response stopped.',
            pending: false,
          }))
          return
        }
        updateStreamingMessage((message) => ({
          ...message,
          text: 'Ask Meant could not respond right now. Try again in a moment.',
          pending: false,
        }))
      })
      .finally(() => {
        if (assistantAbortRef.current === controller) {
          assistantAbortRef.current = null
        }
        if (mountedRef.current) {
          setLoading(false)
        }
      })
  }

  const closeAsk = () => {
    assistantAbortRef.current?.abort()
    setOpen(false)
  }

  return (
    <div
      className={`mt-fab-wrap ${open ? 'open' : ''} ${loading ? 'busy' : ''} ${hidden ? 'mt-fab-wrap-hidden' : ''}`}
    >
      {open ? (
        <div
          className={`mt-askpanel ${loading ? 'mt-askpanel-thinking' : ''}`}
          role="dialog"
          aria-label="Ask Meant"
          style={panelStyle}
        >
          <button
            className="mt-askpanel-resize"
            type="button"
            aria-label="Resize Ask Meant"
            title="Drag to resize"
            onPointerDown={startPanelResize}
            onDoubleClick={() => setPanelSize({ ...ASK_PANEL_DEFAULT_SIZE })}
          />
          <div className="mt-askpanel-head">
            <div className="mt-askpanel-title">
              <SparkMark size={15} /> Ask Meant
            </div>
            <div className="mt-askpanel-actions">
              <button
                className={`mt-askpanel-icon ${historyOpen ? 'on' : ''}`}
                type="button"
                onClick={() => setHistoryOpen((current) => !current)}
                aria-label="Chat history"
                aria-pressed={historyOpen}
                title="Chat history"
              >
                <HistoryIcon size={15} />
              </button>
              <button
                className="mt-askpanel-icon"
                type="button"
                onClick={startNewConversation}
                aria-label="New chat"
                title="New chat"
                disabled={loading}
              >
                <PlusIcon size={15} />
              </button>
              <button
                className="mt-askpanel-close"
                type="button"
                onClick={closeAsk}
                aria-label="Close"
              >
                <CloseIcon size={14} />
              </button>
            </div>
          </div>
          <div className="mt-mono mt-askpanel-ctx">{contextLabel}</div>
          {historyOpen ? (
            <div className="mt-ask-history" role="listbox" aria-label="Ask Meant chat history">
              {conversationHistory.length === 0 ? (
                <div className="mt-ask-history-empty">
                  {historyLoading ? 'Loading chats...' : 'No chats yet.'}
                </div>
              ) : (
                conversationHistory.map((conversation) => (
                  <button
                    key={conversation.conversationId}
                    className={`mt-ask-history-row ${conversation.conversationId === conversationId ? 'active' : ''}`}
                    type="button"
                    role="option"
                    aria-selected={conversation.conversationId === conversationId}
                    onClick={() => selectConversation(conversation.conversationId)}
                    disabled={loading || historyLoading}
                  >
                    <span className="mt-ask-history-title">{conversation.title}</span>
                    <span className="mt-mono mt-ask-history-date">
                      {askConversationDateLabel(conversation.updatedAt)}
                    </span>
                  </button>
                ))
              )}
            </div>
          ) : null}
          {messages.length === 0 ? (
            <p className="mt-askpanel-hint">Ask anything. I already know your preferences.</p>
          ) : null}
          <AskThread messages={messages} onProductOpen={onProductOpen} />
          <AskComposer
            placeholder="Ask Meant..."
            suggestions={suggestions}
            showChips={messages.length === 0}
            onAsk={ask}
            autoFocus
            disabled={loading || historyLoading}
          />
        </div>
      ) : null}
      <button
        className={`mt-fab ${loading ? 'mt-fab-busy' : ''}`}
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-label={open ? 'Close Ask Meant' : 'Open Ask Meant'}
        aria-expanded={open}
      >
        {open ? (
          <CloseIcon size={18} />
        ) : (
          <>
            <SparkMark size={16} color="#fff" /> <span>Ask Meant</span>
          </>
        )}
      </button>
    </div>
  )
}

function productWasPrice(
  product: Product,
  deliveryLocations: readonly UserLocation[],
): number | null {
  const price = productPriceFrom(product, deliveryLocations)
  if (product.listPrice === null || product.listPrice === undefined || product.listPrice <= price) {
    return null
  }
  return product.listPrice
}

function ProductPriceLine({
  product,
  deliveryLocations,
  className,
}: Readonly<{
  product: Product
  deliveryLocations: readonly UserLocation[]
  className: string
}>) {
  const price = productPriceFrom(product, deliveryLocations)
  const wasPrice = productWasPrice(product, deliveryLocations)

  return (
    <span className={className}>
      <span className="mt-mono mt-card-from">from</span> <span>{money(price)}</span>
      {wasPrice ? <span className="mt-was-price">{money(wasPrice)}</span> : null}
    </span>
  )
}

function ProductReviewSummary({ product }: Readonly<{ product: Product }>) {
  if (product.agentStage === 'candidate' && product.review.count <= 0) {
    return <span className="mt-mono mt-card-rating mt-card-rating-live">Checking reviews</span>
  }
  if (product.review.count <= 0) {
    return <span className="mt-mono mt-card-rating muted">No review data</span>
  }
  if (product.review.score === null) {
    return (
      <span className="mt-mono mt-card-rating">
        {product.review.count.toLocaleString()} reviews
      </span>
    )
  }
  return (
    <span className="mt-mono mt-card-rating">
      ★ {product.review.score.toFixed(1)} · {product.review.count.toLocaleString()}
    </span>
  )
}

function catalogBadgeLabels(product: Product): string[] {
  const values = [
    ...(product.certifications ?? []),
    ...(product.materials ?? []),
    ...(product.collections ?? []),
  ]
  const seen = new Set<string>()
  return values
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => {
      const key = value.toLowerCase()
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
}

function ProductCard({
  product,
  index,
  deliveryLocations,
  preferences,
  onOpen,
  savedSet,
  savePendingSet,
  onToggleSave,
  onDismiss,
}: Readonly<
  {
    product: Product
    index: number
    deliveryLocations: readonly UserLocation[]
    preferences: readonly Preference[]
  } & ProductOpenProps &
    ProductSaveProps
>) {
  const open = () => onOpen(product)
  const savePending = savePendingSet.has(product.id)
  const catalogBadges = catalogBadgeLabels(product).slice(0, 3)
  const liveStage = product.agentStage ?? 'ranked'
  const curatedFields = productCuratedFields(product, preferences)
  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      open()
    }
  }

  return (
    <div
      className={`mt-card mt-card-in mt-card-live-${liveStage}`}
      data-product-id={product.id}
      role="button"
      tabIndex={0}
      onClick={open}
      onKeyDown={handleKey}
      style={{
        animationDelay: `${index * 45}ms`,
        transitionDelay: `${index * 18}ms`,
      }}
    >
      <div className="mt-card-media">
        <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
        <span className="mt-mono mt-card-cat">{product.category}</span>
        <div className="mt-card-ring">
          <MatchRing value={product.match} />
        </div>
        <button
          className={`mt-save ${savedSet.has(product.id) ? 'on' : ''}`}
          type="button"
          aria-label={
            savePending ? 'Saving' : savedSet.has(product.id) ? 'Remove from saved' : 'Save'
          }
          disabled={savePending}
          onClick={(event) => {
            event.stopPropagation()
            onToggleSave(product)
          }}
        >
          <HeartIcon filled={savedSet.has(product.id)} />
        </button>
        {onDismiss ? (
          <button
            className="mt-dismiss"
            type="button"
            aria-label="Dismiss from recommendations"
            onClick={(event) => {
              event.stopPropagation()
              onDismiss(product)
            }}
          >
            <CloseIcon size={14} />
          </button>
        ) : null}
      </div>

      <div className="mt-card-body">
        <div className="mt-mono mt-card-brand">{product.brand}</div>
        <InventorySignalBadge product={product} compact />
        <div className="mt-card-name">{product.name}</div>
        <div className="mt-chips">
          {product.satisfies.slice(0, 3).map((id) => (
            <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" small />
          ))}
          {product.misses.map((id) => (
            <PrefChip key={id} label={prefLabel(preferences, id)} variant="missed" small />
          ))}
        </div>
        {catalogBadges.length > 0 || product.detailError ? (
          <div className="mt-catalog-pills">
            {catalogBadges.map((label) => (
              <span className="mt-catalog-pill" key={label}>
                {label}
              </span>
            ))}
            {product.detailError ? (
              <span className="mt-catalog-pill mt-catalog-pill-warning">Details unavailable</span>
            ) : null}
          </div>
        ) : null}
        <div className="mt-card-foot">
          <ProductPriceLine
            product={product}
            deliveryLocations={deliveryLocations}
            className="mt-card-price"
          />
          <span className="mt-mono mt-card-stores">
            {productMerchantCount(product, deliveryLocations)} stores
          </span>
        </div>
        <ProductReviewSummary product={product} />
        <div
          className={`mt-card-note ${product.agentStage === 'candidate' ? 'mt-card-note-live' : ''}`}
        >
          <span className="mt-note-key">Why it is meant for you</span>
          {curatedFields.note}
        </div>
      </div>
    </div>
  )
}

function ChatHero({
  profile,
  greeting,
  prompts,
  onSubmit,
  loading,
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  merchantsLoading,
  merchantsError,
  onMerchant,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  prompts: readonly string[]
  onSubmit: (query: string) => void
  loading: boolean
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  merchantsLoading: boolean
  merchantsError: string | null
  onMerchant: (merchant: MerchantProfile | null) => void
}>) {
  const [value, setValue] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const submittedTimeoutRef = useRef<number | null>(null)
  const hasSearchText = value.trim().length > 0

  useEffect(
    () => () => {
      if (submittedTimeoutRef.current !== null) {
        window.clearTimeout(submittedTimeoutRef.current)
      }
    },
    [],
  )

  const submit = (text?: string) => {
    if (loading) {
      return
    }
    const query = (text ?? value).trim()
    if (!query) {
      return
    }
    setSubmitted(true)
    if (submittedTimeoutRef.current !== null) {
      window.clearTimeout(submittedTimeoutRef.current)
    }
    submittedTimeoutRef.current = window.setTimeout(() => {
      setSubmitted(false)
      submittedTimeoutRef.current = null
    }, 520)
    setValue('')
    onSubmit(query)
  }

  return (
    <header className="mt-hero">
      <div className="mt-mono mt-hero-eyebrow">
        {greeting}, {profile.name}
      </div>
      <h1 className="mt-hero-title">
        Everything here is <em>meant</em> for you.
      </h1>
      <p className="mt-hero-sub">
        Ask for products across supported merchants. Meant already knows you prefer{' '}
        {profile.summary}
      </p>
      <form
        className={`mt-search${hasSearchText ? ' mt-search-writing' : ''}${submitted ? ' mt-search-submitted' : ''}`}
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        <span className="mt-search-spark" aria-hidden>
          <SparkMark size={20} />
        </span>
        <input
          className="mt-search-input"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder='Ask Meant anything - "a good cotton T-shirt under $50"'
          disabled={loading}
        />
        <button type="submit" className="mt-search-go" aria-label="Ask" disabled={loading}>
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
            <path
              d="M3.5 9h11M9.5 4l5 5-5 5"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </form>
      <MerchantScope
        merchants={merchants}
        selectedMerchant={selectedMerchant}
        merchantCounts={merchantCounts}
        totalProductCount={totalProductCount}
        loading={merchantsLoading}
        error={merchantsError}
        onMerchant={onMerchant}
      />
      <div className="mt-prompts">
        {prompts.map((prompt) => (
          <button
            key={prompt}
            type="button"
            className="mt-prompt"
            onClick={() => submit(prompt)}
            disabled={loading}
          >
            {prompt}
          </button>
        ))}
      </div>
    </header>
  )
}

function MerchantScope({
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  loading,
  error,
  onMerchant,
}: Readonly<{
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  loading: boolean
  error: string | null
  onMerchant: (merchant: MerchantProfile | null) => void
}>) {
  const [open, setOpen] = useState(false)
  const [merchantSearch, setMerchantSearch] = useState('')
  const ref = useRef<HTMLDivElement | null>(null)
  const searchRef = useRef<HTMLInputElement | null>(null)
  const merchantSearchText = merchantSearch.trim().toLowerCase()
  const filteredMerchants = useMemo(
    () => orderedMerchantMatches(merchants, merchantCounts, merchantSearchText),
    [merchantCounts, merchantSearchText, merchants],
  )

  useEffect(() => {
    if (!open) {
      return
    }
    const onDown = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  useEffect(() => {
    if (!open) {
      setMerchantSearch('')
      return
    }
    const timeout = window.setTimeout(() => searchRef.current?.focus(), 0)
    return () => window.clearTimeout(timeout)
  }, [open])

  if (loading && merchants.length === 0) {
    return (
      <div className="mt-scope">
        <div className="mt-scope-status mt-mono">Loading merchants</div>
      </div>
    )
  }

  if (error && merchants.length === 0) {
    return (
      <div className="mt-scope">
        <div className="mt-scope-status mt-scope-error mt-mono">{error}</div>
      </div>
    )
  }

  if (merchants.length === 0) {
    return null
  }

  return (
    <div className="mt-scope">
      <div className="mt-scope-combo" ref={ref}>
        <button
          className={`mt-scope-btn ${selectedMerchant ? 'on' : ''}`}
          type="button"
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
        >
          {selectedMerchant ? <span className="mt-scope-dot" aria-hidden /> : <MerchantIcon />}
          <span>{selectedMerchant?.name ?? 'All merchants'}</span>
          <span className={`mt-caret ${open ? 'up' : ''}`}>v</span>
        </button>
        {open ? (
          <div className="mt-scope-menu">
            <label className="mt-scope-search">
              <span className="mt-scope-search-icon" aria-hidden>
                <svg width="14" height="14" viewBox="0 0 18 18" fill="none">
                  <circle cx="8" cy="8" r="4.6" stroke="currentColor" strokeWidth="1.4" />
                  <path
                    d="M11.4 11.4 15 15"
                    stroke="currentColor"
                    strokeWidth="1.4"
                    strokeLinecap="round"
                  />
                </svg>
              </span>
              <input
                ref={searchRef}
                className="mt-scope-search-input"
                value={merchantSearch}
                onChange={(event) => setMerchantSearch(event.target.value)}
                placeholder="Search merchants"
                aria-label="Search merchants"
              />
            </label>
            <div className="mt-scope-list" role="listbox">
              <button
                className={`mt-scope-opt ${selectedMerchant ? '' : 'on'}`}
                type="button"
                role="option"
                aria-selected={!selectedMerchant}
                onClick={() => {
                  onMerchant(null)
                  setOpen(false)
                }}
              >
                <span className="mt-scope-opt-name">All merchants</span>
                <span className="mt-mono mt-scope-opt-count">{totalProductCount}</span>
              </button>
              <div className="mt-scope-sep" />
              {filteredMerchants.map((merchant) => (
                <button
                  key={merchant.id}
                  className={`mt-scope-opt ${selectedMerchant?.id === merchant.id ? 'on' : ''}`}
                  type="button"
                  role="option"
                  aria-selected={selectedMerchant?.id === merchant.id}
                  onClick={() => {
                    onMerchant(merchant)
                    setOpen(false)
                  }}
                >
                  <span className="mt-scope-opt-main">
                    <span className="mt-scope-opt-name">{merchant.name}</span>
                    <span className="mt-mono mt-scope-opt-domain">{merchant.domain}</span>
                  </span>
                  <span className="mt-mono mt-scope-opt-count">
                    {merchantCounts.get(merchant.id) ?? 0}
                  </span>
                </button>
              ))}
              {filteredMerchants.length === 0 ? (
                <div className="mt-scope-empty mt-mono">No merchants found</div>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}

let discoverChatMessageSequence = 0
const nextDiscoverChatMessageId = () => {
  discoverChatMessageSequence += 1
  return `discover-chat-${discoverChatMessageSequence}`
}

let discoverChatThreadSequence = 0
const nextDiscoverChatThreadId = () => {
  discoverChatThreadSequence += 1
  return `discover-thread-${Date.now().toString(36)}-${discoverChatThreadSequence}`
}

const DEFAULT_DISCOVER_CHAT_TITLE = 'New chat'

function deriveDiscoverChatTitle(text: string): string {
  const normalized = text.trim().replace(/\s+/g, ' ')
  if (!normalized) {
    return DEFAULT_DISCOVER_CHAT_TITLE
  }
  return normalized.length > 24 ? `${normalized.slice(0, 22)}...` : normalized
}

function createDiscoverChatThread(
  messages: readonly DiscoverChatMessage[] = [],
  title = DEFAULT_DISCOVER_CHAT_TITLE,
): DiscoverChatThread {
  const now = Date.now()
  return {
    id: nextDiscoverChatThreadId(),
    title,
    messages,
    createdAt: now,
    updatedAt: now,
  }
}

function normalizeDiscoverChatThreads(
  threads: readonly DiscoverChatThread[],
): DiscoverChatThread[] {
  const now = Date.now()
  return threads.map((thread, index) => {
    const fallbackTime = now - (threads.length - index) * 1000
    const createdAt = thread.createdAt ?? fallbackTime
    return {
      ...thread,
      createdAt,
      updatedAt: thread.updatedAt ?? createdAt,
    }
  })
}

function initialDiscoverChatThreads(): DiscoverChatThread[] {
  const storedThreads = readStorage<DiscoverChatThread[] | null>('meant.discoverChatThreads', null)
  if (storedThreads?.length) {
    return normalizeDiscoverChatThreads(storedThreads)
  }
  const legacyMessages = readStorage<DiscoverChatMessage[]>('meant.discoverChatMessages', [])
  return [
    createDiscoverChatThread(
      legacyMessages,
      legacyMessages.length > 0 ? 'Shopping agent' : DEFAULT_DISCOVER_CHAT_TITLE,
    ),
  ]
}

function resolveStateAction<T>(action: SetStateAction<T>, current: T): T {
  return typeof action === 'function' ? (action as (previous: T) => T)(current) : action
}

function discoverBlockPrimaryProduct(block: DiscoverChatBlock): Product | null {
  if (block.type === 'products') {
    return block.products[0] ?? null
  }
  if (block.type === 'similar') {
    return block.products[0] ?? block.product
  }
  if (block.type === 'decision') {
    return block.product
  }
  if (
    block.type === 'reviews' ||
    block.type === 'code' ||
    block.type === 'watch' ||
    block.type === 'friendvote' ||
    block.type === 'added'
  ) {
    return block.product
  }
  if (block.type === 'saved') {
    return block.products[0] ?? null
  }
  if (block.type === 'cart') {
    return block.products?.[0] ?? null
  }
  if (block.type === 'minicompare') {
    return block.products[block.pickIndex] ?? block.products[0] ?? null
  }
  return null
}

function discoverBlockHasProduct(block: DiscoverChatBlock, productId: ProductId): boolean {
  if (block.type === 'products' || block.type === 'saved' || block.type === 'minicompare') {
    return block.products.some((product) => product.id === productId)
  }
  if (block.type === 'similar') {
    return (
      block.product.id === productId || block.products.some((product) => product.id === productId)
    )
  }
  if (block.type === 'cart') {
    return (
      block.lines.some((line) => line.id === productId) ||
      Boolean(block.products?.some((product) => product.id === productId))
    )
  }
  if (
    block.type === 'reviews' ||
    block.type === 'code' ||
    block.type === 'decision' ||
    block.type === 'watch' ||
    block.type === 'friendvote' ||
    block.type === 'added'
  ) {
    return block.product.id === productId
  }
  return false
}

function discoverThreadFocusProduct(
  thread: DiscoverChatThread,
  productsById: ReadonlyMap<ProductId, Product>,
): Product | null {
  if (thread.focusProductId) {
    const focused = productsById.get(thread.focusProductId)
    if (focused) {
      return focused
    }
  }
  for (const message of [...thread.messages].reverse()) {
    for (const block of [...(message.blocks ?? [])].reverse()) {
      const product = discoverBlockPrimaryProduct(block)
      if (product) {
        return product
      }
    }
    if (message.productContext) {
      return message.productContext
    }
  }
  return null
}

const nextShelfUid = () =>
  `shelf-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`

function shelfProductSnapshot(product: Product): ShelfProductSnapshot {
  return {
    productId: product.id,
    name: product.name,
    brand: product.brand,
    category: product.category,
    tone: product.tone,
    priceFrom: product.priceFrom,
    merchants: product.merchants,
    imageUrl: product.imageUrl,
  }
}

function shelfThumbForProduct(product: Product): ShelfThumb {
  return {
    name: product.name,
    tone: product.tone,
    imageUrl: product.imageUrl,
  }
}

function shelfMessageSnapshot(message: DiscoverChatMessage): ShelfMessageSnapshot {
  if (message.role === 'you') {
    return {
      side: 'you',
      title: message.productContext ? 'You asked about' : 'Your message',
      text: message.text ?? message.productContext?.name ?? '',
      thumbs: message.productContext ? [shelfThumbForProduct(message.productContext)] : [],
    }
  }

  let text = message.text ?? ''
  let title = 'Meant'
  const products: Product[] = []

  for (const block of message.blocks ?? []) {
    if ((block.type === 'text' || block.type === 'system') && !text) {
      text = block.text
    }
    if (block.type === 'products') {
      title = `${block.products.length} match${block.products.length === 1 ? '' : 'es'}`
      products.push(...block.products)
    }
    if (block.type === 'similar') {
      title = 'Similar picks'
      products.push(block.product, ...block.products)
    }
    if (block.type === 'reviews') {
      title = 'Reviews'
      products.push(block.product)
    }
    if (block.type === 'code') {
      title = 'Discount code'
      products.push(block.product)
    }
    if (block.type === 'decision') {
      title = "Meant's pick"
      products.push(block.product)
      if (block.runnerUp) {
        products.push(block.runnerUp)
      }
    }
    if (block.type === 'watch') {
      title = 'Price watch'
      products.push(block.product)
    }
    if (block.type === 'friendvote') {
      title = `${block.person} weighed in`
      text = block.note
      products.push(block.product)
    }
    if (block.type === 'added') {
      title = 'Added to cart'
      products.push(block.product)
    }
    if (block.type === 'saved') {
      title = 'Saved items'
      products.push(...block.products)
    }
    if (block.type === 'orders') {
      title = 'Orders'
    }
    if (block.type === 'prefs') {
      title = 'Preferences'
    }
    if (block.type === 'cart') {
      title = 'Your cart'
    }
    if (block.type === 'checkout') {
      title = 'Checkout'
    }
  }

  const seen = new Set<ProductId>()
  const thumbs = products
    .filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
    .slice(0, 6)
    .map(shelfThumbForProduct)

  return {
    side: 'meant',
    title,
    text,
    thumbs,
  }
}

function productDetailChatBlocks(
  question: string,
  product: Product,
  products: readonly Product[],
  preferences: readonly Preference[],
): readonly DiscoverChatBlock[] {
  const lower = question.toLowerCase()
  if (/\breviews?\b|\bratings?\b|\bpeople say\b|\bfeedback\b/.test(lower)) {
    return [
      {
        type: 'text',
        text: `Here is what I can tell from the current product data for ${product.name}.`,
      },
      { type: 'reviews', product },
    ]
  }
  if (/\bcode\b|\bcoupon\b|\bdiscount\b|\bpromo\b|\bdeal\b|\bcheaper\b|\bsave\b/.test(lower)) {
    const discount = chatDiscountForProduct(product)
    return [
      { type: 'text', text: `I checked ${product.name} for a better price path.` },
      { type: 'code', product, code: discount.code, saved: discount.saved },
    ]
  }
  if (
    /\bsimilar\b|\balternative\b|\blike this\b|\bother option\b|\binstead\b|\bcompare\b/.test(lower)
  ) {
    return [
      {
        type: 'text',
        text: `Close matches to ${product.name}, filtered through the products already loaded here.`,
      },
      { type: 'similar', product, products: similarChatProducts(product, products) },
    ]
  }
  if (/\bmatch\b|\bpreferences?\b|\bfit\b|\bmeant\b/.test(lower)) {
    return [
      { type: 'text', text: resolveAsk(question, product, preferences) },
      { type: 'decision', product, runnerUp: similarChatProducts(product, products)[0] ?? null },
    ]
  }
  return [{ type: 'text', text: resolveAsk(question, product, preferences) }]
}

function chatDiscountForProduct(product: Product): { code: string; saved: number } {
  const best = product.offers[0]
  const base = best?.price ?? product.priceFrom
  const code = product.category.toLowerCase().includes('clothing') ? 'MEANT15' : 'MEANT10'
  return { code, saved: Math.max(1, Math.round(base * (code === 'MEANT15' ? 0.15 : 0.1))) }
}

function similarChatProducts(product: Product, products: readonly Product[]): readonly Product[] {
  const sameCategory = products
    .filter((candidate) => candidate.id !== product.id && candidate.category === product.category)
    .sort((left, right) => right.match - left.match)
  const fallback = products
    .filter((candidate) => candidate.id !== product.id)
    .sort((left, right) => right.match - left.match)
  return (sameCategory.length > 0 ? sameCategory : fallback).slice(0, 4)
}

function winningIndex(values: readonly number[], higherIsBetter: boolean): number {
  return values.reduce((bestIndex, value, index) => {
    const bestValue = values[bestIndex] ?? value
    return higherIsBetter
      ? value > bestValue
        ? index
        : bestIndex
      : value < bestValue
        ? index
        : bestIndex
  }, 0)
}

function createMiniCompareBlock(
  products: readonly Product[],
  deliveryLocations: readonly UserLocation[],
): Extract<DiscoverChatBlock, { type: 'minicompare' }> | null {
  const nextProducts = products.slice(0, 4)
  if (nextProducts.length < 2) {
    return null
  }

  const prices = nextProducts.map((product) => productPriceFrom(product, deliveryLocations))
  const reviews = nextProducts.map((product) => product.review.score ?? 0)
  const fitGaps = nextProducts.map((product) => product.misses.length)
  const rows: readonly MiniCompareRow[] = [
    {
      label: 'Match',
      values: nextProducts.map((product) => `${product.match}%`),
      winnerIndex: winningIndex(
        nextProducts.map((product) => product.match),
        true,
      ),
    },
    {
      label: 'From',
      values: prices.map((price) => money(price)),
      winnerIndex: winningIndex(prices, false),
    },
    {
      label: 'Reviews',
      values: nextProducts.map((product) =>
        product.review.score === null
          ? `${product.review.count.toLocaleString()} reviews`
          : `${product.review.score.toFixed(1)} · ${product.review.count.toLocaleString()}`,
      ),
      winnerIndex: winningIndex(reviews, true),
    },
    {
      label: 'Fit gaps',
      values: fitGaps.map((misses) =>
        misses === 0 ? 'None' : `${misses} gap${misses === 1 ? '' : 's'}`,
      ),
      winnerIndex: winningIndex(fitGaps, false),
    },
  ]
  const winCounts = nextProducts.map((product, index) => ({
    index,
    wins: rows.filter((row) => row.winnerIndex === index).length,
    match: product.match,
  }))
  const pickIndex = [...winCounts].sort(
    (left, right) => right.wins - left.wins || right.match - left.match,
  )[0].index

  return { type: 'minicompare', products: nextProducts, rows, pickIndex }
}

function cartItemIdentity(item: Pick<CartItem, 'id' | 'merchant'>): string {
  return `${item.id}:${item.merchant}`
}

function cartItemsWithFallback(
  liveCart: readonly CartItem[],
  fallbackCart: readonly CartItem[],
): readonly CartItem[] {
  if (fallbackCart.length === 0) {
    return liveCart
  }
  const seen = new Set(liveCart.map(cartItemIdentity))
  return [
    ...liveCart,
    ...fallbackCart.filter((item) => {
      const key = cartItemIdentity(item)
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    }),
  ]
}

function productsWithFallback(
  fallbackProducts: readonly Product[] | undefined,
  products: readonly Product[],
): readonly Product[] {
  if (!fallbackProducts?.length) {
    return products
  }
  const seen = new Set<ProductId>()
  return [...fallbackProducts, ...products].filter((product) => {
    if (seen.has(product.id)) {
      return false
    }
    seen.add(product.id)
    return true
  })
}

function isRenderableSearchProduct(product: Product): boolean {
  return product.agentStage !== 'candidate'
}

function productCopyLine(product: Product): string {
  const merchant = product.offers[0]?.merchant ?? `${product.merchants} merchants`
  return `${product.name} - ${money(product.priceFrom)} - ${merchant}`
}

function discoverBlockCopyText(block: DiscoverChatBlock): string {
  if (block.type === 'text' || block.type === 'system') {
    return block.text
  }
  if (block.type === 'products') {
    return [
      block.query ? `Products for "${block.query}":` : 'Products:',
      ...block.products.map(productCopyLine),
    ].join('\n')
  }
  if (block.type === 'reviews') {
    return `${block.product.name} reviews: ${block.product.review.insight}`
  }
  if (block.type === 'code') {
    return `${block.product.name} code: ${block.code} saves ${money(block.saved)}`
  }
  if (block.type === 'similar') {
    return [`Similar to ${block.product.name}:`, ...block.products.map(productCopyLine)].join('\n')
  }
  if (block.type === 'decision') {
    return `Pick: ${productCopyLine(block.product)}`
  }
  if (block.type === 'watch') {
    return `Watching ${block.product.name}: ${money(block.price)} at ${block.merchant}`
  }
  if (block.type === 'friendvote') {
    return `${block.person} voted ${block.vote} on ${block.product.name}: ${block.note}`
  }
  if (block.type === 'added') {
    return `Added ${block.product.name} to cart from ${block.merchant}.`
  }
  if (block.type === 'saved') {
    return ['Saved products:', ...block.products.map(productCopyLine)].join('\n')
  }
  if (block.type === 'orders') {
    return `${block.orders.length} recent ${block.orders.length === 1 ? 'order' : 'orders'}`
  }
  if (block.type === 'prefs') {
    return ['Preferences:', ...block.preferences.map((preference) => preference.label)].join('\n')
  }
  if (block.type === 'cart') {
    return [
      'Cart:',
      ...block.lines.map(
        (line) =>
          `${line.qty} x ${line.id}${line.variantTitle ? ` (${line.variantTitle})` : ''} - ${line.merchant}`,
      ),
    ].join('\n')
  }
  if (block.type === 'checkout') {
    return `Checkout across ${block.merchantCount} ${block.merchantCount === 1 ? 'merchant' : 'merchants'}`
  }
  return [
    'Compare:',
    ...block.products.map((product, index) =>
      index === block.pickIndex ? `${productCopyLine(product)} (pick)` : productCopyLine(product),
    ),
  ].join('\n')
}

function discoverChatMessageCopyText(message: DiscoverChatMessage): string {
  const parts = [
    message.productContext ? `About ${message.productContext.name}` : '',
    message.text ?? '',
    ...(message.blocks?.map(discoverBlockCopyText) ?? []),
  ]
  return parts
    .map((part) => part.trim())
    .filter(Boolean)
    .join('\n\n')
}

function copyTextToClipboard(value: string): void {
  const text = value.trim()
  if (!text || typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
    return
  }
  void navigator.clipboard.writeText(text).catch(() => undefined)
}

function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined
    }
    const matcher = window.matchMedia(query)
    const update = () => setMatches(matcher.matches)
    update()
    matcher.addEventListener('change', update)
    return () => matcher.removeEventListener('change', update)
  }, [query])

  return matches
}

function DiscoverChatProduct({
  product,
  index,
  deliveryLocations,
  preferences,
  savedSet,
  savePendingSet,
  pinned,
  watched,
  shelfed,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onShelfAdd,
  onDragProduct,
}: Readonly<{
  product: Product
  index: number
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinned: boolean
  watched: boolean
  shelfed: boolean
  onOpen: (product: Product) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (kind: 'reviews' | 'code' | 'similar' | 'resale', product: Product) => void
  onShelfAdd: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
}>) {
  return (
    <div
      className="mt-ct-prod"
      draggable
      onDragStart={(event) => onDragProduct(event, product)}
      onDragEnd={() => document.body.classList.remove('mt-dragging')}
    >
      <button
        className={`mt-ct-prod-shelf ${shelfed ? 'on' : ''}`}
        type="button"
        onClick={(event) => {
          event.stopPropagation()
          onShelfAdd(product, event.currentTarget)
        }}
        aria-label={shelfed ? 'On your shelf' : 'Set aside on shelf'}
        title={shelfed ? 'On your shelf' : 'Set aside on your shelf'}
      >
        <BookmarkIcon filled={shelfed} size={13} />
      </button>
      <ProductCard
        product={product}
        index={index}
        deliveryLocations={deliveryLocations}
        preferences={preferences}
        onOpen={onOpen}
        savedSet={savedSet}
        savePendingSet={savePendingSet}
        onToggleSave={onToggleSave}
      />
      <div className="mt-ct-actionrow">
        <button
          className="mt-ct-addbtn"
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onAddCart(product)
          }}
        >
          <CartIcon /> Add to cart
        </button>
        <button
          className={`mt-ct-pinbtn ${pinned ? 'on' : ''}`}
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onPin(product)
          }}
        >
          {pinned ? 'Pinned' : 'Pin'}
        </button>
        <button
          className={`mt-ct-watchbtn ${watched ? 'on' : ''}`}
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onWatch(product)
          }}
        >
          {watched ? 'Watching' : 'Watch'}
        </button>
      </div>
      <div className="mt-ct-askbar">
        <span className="mt-mono mt-ct-askbar-lead">Dig in</span>
        <button
          className="mt-ct-askchip"
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onDig('reviews', product)
          }}
        >
          Reviews
        </button>
        <button
          className="mt-ct-askchip"
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onDig('code', product)
          }}
        >
          Find a code
        </button>
        <button
          className="mt-ct-askchip"
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onDig('similar', product)
          }}
        >
          Similar
        </button>
        <button
          className="mt-ct-askchip"
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onDig('resale', product)
          }}
        >
          Second-hand
        </button>
      </div>
    </div>
  )
}

function DiscoverProductBatch({
  products,
  query,
  deliveryLocations,
  preferences,
  savedSet,
  savePendingSet,
  pinnedSet,
  watchedSet,
  shelfProductSet,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onJustPick,
  onCompareHere,
  onShelfAddProduct,
  onDragProduct,
}: Readonly<{
  products: readonly Product[]
  query?: string
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfProductSet: ReadonlySet<ProductId>
  onOpen: (product: Product, products?: readonly Product[]) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (kind: 'reviews' | 'code' | 'similar' | 'resale', product: Product) => void
  onJustPick: (products: readonly Product[]) => void
  onCompareHere: (products: readonly Product[]) => void
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
}>) {
  const isPhone = useMediaQuery('(max-width: 720px)')
  const [page, setPage] = useState(0)
  const [phoneIndex, setPhoneIndex] = useState(0)
  const carouselRef = useRef<HTMLDivElement | null>(null)
  const pageSize = isPhone ? Math.max(products.length, 1) : 4
  const pageCount = Math.max(1, Math.ceil(products.length / pageSize))
  const currentPage = Math.min(page, pageCount - 1)
  const pageProducts = isPhone
    ? products
    : products.slice(currentPage * pageSize, currentPage * pageSize + pageSize)
  const many = !isPhone && products.length > pageSize
  const phoneMany = isPhone && products.length > 1
  const scrollPhoneCarousel = (direction: -1 | 1) => {
    const nextIndex = Math.min(products.length - 1, Math.max(0, phoneIndex + direction))
    setPhoneIndex(nextIndex)
    const target = carouselRef.current?.children.item(nextIndex)
    if (target instanceof HTMLElement) {
      target.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'start' })
    }
  }
  const syncPhoneCarouselIndex = () => {
    const scroller = carouselRef.current
    const first = scroller?.firstElementChild
    if (!isPhone || !scroller || !(first instanceof HTMLElement)) {
      return
    }
    const gap = Number.parseFloat(window.getComputedStyle(scroller).columnGap || '0') || 0
    const stride = first.offsetWidth + gap
    if (stride <= 0) {
      return
    }
    setPhoneIndex(
      Math.min(products.length - 1, Math.max(0, Math.round(scroller.scrollLeft / stride))),
    )
  }
  const renderPager = () =>
    many ? (
      <div className="mt-ct-pager">
        <button
          className="mt-ct-pager-btn"
          type="button"
          disabled={currentPage === 0}
          aria-label="Previous products"
          onClick={() => setPage((value) => Math.max(0, value - 1))}
        >
          <ChevronIcon direction="left" size={15} />
        </button>
        <span className="mt-mono mt-ct-pager-of">
          {currentPage + 1}/{pageCount}
        </span>
        <button
          className="mt-ct-pager-btn"
          type="button"
          disabled={currentPage >= pageCount - 1}
          aria-label="Next products"
          onClick={() => setPage((value) => Math.min(pageCount - 1, value + 1))}
        >
          <ChevronIcon direction="right" size={15} />
        </button>
      </div>
    ) : null
  const renderPhonePager = () =>
    phoneMany ? (
      <div className="mt-ct-swipe-nav" aria-label="Product carousel">
        <span className="mt-mono mt-ct-swipe-hint">Swipe</span>
        <button
          className="mt-ct-pager-btn mt-ct-swipe-btn"
          type="button"
          disabled={phoneIndex === 0}
          aria-label="Previous product"
          onClick={() => scrollPhoneCarousel(-1)}
        >
          <ChevronIcon direction="left" size={15} />
        </button>
        <span className="mt-mono mt-ct-swipe-count">
          {phoneIndex + 1}/{products.length}
        </span>
        <button
          className="mt-ct-pager-btn mt-ct-swipe-btn"
          type="button"
          disabled={phoneIndex >= products.length - 1}
          aria-label="Next product"
          onClick={() => scrollPhoneCarousel(1)}
        >
          <ChevronIcon direction="right" size={15} />
        </button>
      </div>
    ) : null

  useEffect(() => {
    setPage(0)
    setPhoneIndex(0)
    carouselRef.current?.scrollTo({ left: 0 })
  }, [isPhone, query, products])

  if (products.length === 0) {
    return null
  }

  return (
    <div className="mt-ct-batch">
      <div className="mt-ct-batch-head">
        <div className="mt-mono mt-ct-batch-count">
          {products.length} match{products.length === 1 ? '' : 'es'}
          {pageCount > 1
            ? ` · ${currentPage * pageSize + 1}-${Math.min((currentPage + 1) * pageSize, products.length)}`
            : ''}
        </div>
        {renderPager()}
        {renderPhonePager()}
      </div>
      <div
        ref={carouselRef}
        className={`mt-ct-grid${isPhone ? ' phone-swipe' : ''}`}
        aria-label={isPhone ? 'Swipe through products' : undefined}
        onScroll={syncPhoneCarouselIndex}
      >
        {pageProducts.map((product, index) => (
          <DiscoverChatProduct
            key={product.id}
            product={product}
            index={isPhone ? index % 4 : index}
            deliveryLocations={deliveryLocations}
            preferences={preferences}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            pinned={pinnedSet.has(product.id)}
            watched={watchedSet.has(product.id)}
            shelfed={shelfProductSet.has(product.id)}
            onOpen={(nextProduct) => onOpen(nextProduct, products)}
            onToggleSave={onToggleSave}
            onAddCart={onAddCart}
            onPin={onPin}
            onWatch={onWatch}
            onDig={onDig}
            onShelfAdd={onShelfAddProduct}
            onDragProduct={onDragProduct}
          />
        ))}
      </div>
      <div className="mt-ct-batch-foot">
        <button className="mt-ct-suggchip" type="button" onClick={() => onJustPick(products)}>
          Just pick one for me
        </button>
        {products.length >= 2 ? (
          <button
            className="mt-ct-suggchip ghost"
            type="button"
            onClick={() => onCompareHere(products)}
          >
            Compare here
          </button>
        ) : null}
        {many ? <span className="mt-ct-batch-foot-sp" /> : null}
        {renderPager()}
      </div>
    </div>
  )
}

function InlineMiniCompareBlock({
  block,
  deliveryLocations,
  onOpen,
  onAddCart,
  onOpenFullCompare,
}: Readonly<{
  block: Extract<DiscoverChatBlock, { type: 'minicompare' }>
  deliveryLocations: readonly UserLocation[]
  onOpen: (product: Product, products?: readonly Product[]) => void
  onAddCart: (product: Product) => void
  onOpenFullCompare: (products: readonly Product[]) => void
}>) {
  const pick = block.products[block.pickIndex] ?? block.products[0]
  const gridStyle: CSSProperties = {
    gridTemplateColumns: `92px repeat(${block.products.length}, minmax(0, 1fr))`,
  }

  return (
    <div className="mt-ct-block mt-ct-mini">
      <div className="mt-ct-block-head">
        <div className="mt-mono mt-ct-block-key">Inline compare</div>
        <span className="mt-ct-code-save mt-mono">{block.products.length} pinned</span>
      </div>
      <div className="mt-ct-mini-grid" style={gridStyle}>
        <span className="mt-ct-mini-axis" />
        {block.products.map((product, index) => (
          <button
            key={product.id}
            className={`mt-ct-mini-prod ${index === block.pickIndex ? 'best' : ''}`}
            type="button"
            onClick={() => onOpen(product, block.products)}
          >
            <span className="mt-ct-mini-thumb">
              <ProductArtwork product={product} label={product.category.toLowerCase()} />
            </span>
            <span className="mt-ct-mini-name">{product.name}</span>
            <span className="mt-mono mt-ct-mini-price">
              {money(productPriceFrom(product, deliveryLocations))}
            </span>
          </button>
        ))}
        {block.rows.map((row) => (
          <Fragment key={row.label}>
            <span className="mt-mono mt-ct-mini-label">{row.label}</span>
            {row.values.map((value, index) => (
              <span
                key={`${row.label}-${block.products[index]?.id ?? index}`}
                className={`mt-ct-mini-cell ${row.winnerIndex === index ? 'win' : ''}`}
              >
                {value}
              </span>
            ))}
          </Fragment>
        ))}
      </div>
      {pick ? (
        <div className="mt-ct-mini-pick">
          <span>
            <b>{pick.name}</b> wins this quick pass on match, price, reviews, and fit gaps.
          </span>
          <button className="mt-ct-addbtn solid" type="button" onClick={() => onAddCart(pick)}>
            <CartIcon /> Add pick
          </button>
        </div>
      ) : null}
      <button
        className="mt-ct-mini-full"
        type="button"
        onClick={() => onOpenFullCompare(block.products)}
      >
        Open full compare
      </button>
    </div>
  )
}

function InlineCartBlock({
  cart,
  products,
  onQty,
  onRemove,
  onAddCart,
  onOpenCart,
  onCheckoutHere,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  onQty: (id: ProductId, merchant: string, qty: number, nextCart: readonly CartItem[]) => void
  onRemove: (id: ProductId, merchant: string, nextCart: readonly CartItem[]) => void
  onAddCart: (product: Product) => void
  onOpenCart: () => void
  onCheckoutHere: () => void
}>) {
  const lines = cartLines(cart, products)
  const alerts = computeSmartAlerts(lines, products)
  const groups = cartGroups(lines, false)
  const itemCount = lines.reduce((sum, line) => sum + line.qty, 0)
  const saved = groups.reduce((sum, group) => sum + group.itemDiscount, 0)
  const total = groups.reduce((sum, group) => sum + group.total, 0)
  const productById = new Map(products.map((product) => [product.id, product]))
  const cartAfterQty = (target: CartItem, qty: number) =>
    qty <= 0
      ? lines.filter((line) => cartItemIdentity(line) !== cartItemIdentity(target))
      : lines.map((line) =>
          cartItemIdentity(line) === cartItemIdentity(target) ? { ...line, qty } : line,
        )
  const cartAfterRemove = (target: CartItem) =>
    lines.filter((line) => cartItemIdentity(line) !== cartItemIdentity(target))

  return (
    <div className="mt-ct-block mt-ct-cart">
      <div className="mt-ct-block-head">
        <div className="mt-mono mt-ct-block-key">Cart in chat</div>
        <span className="mt-ct-code-save mt-mono">
          {itemCount} item{itemCount === 1 ? '' : 's'}
        </span>
      </div>
      {alerts.length > 0 ? (
        <div className="mt-ct-cart-signals">
          {alerts.slice(0, 2).map((alert) => (
            <div className={`mt-cart-sig mt-cart-sig-${alert.kind}`} key={alert.id}>
              <b>{alert.title}</b>
              <span>{alert.body}</span>
              {alert.fix ? (
                <button
                  className="mt-ct-cart-fix"
                  type="button"
                  onClick={() => {
                    const product = productById.get(alert.fix?.id ?? '')
                    if (product) {
                      onAddCart(product)
                    }
                  }}
                >
                  {alert.fix.label}
                </button>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
      {lines.length > 0 ? (
        <div className="mt-ct-cart-list">
          {lines.map((line) => (
            <div className="mt-ct-cart-row" key={`${line.id}-${line.merchant}`}>
              <button className="mt-ct-cart-media" type="button" onClick={() => onOpenCart()}>
                <ProductArtwork
                  product={line.product}
                  label={line.product.category.toLowerCase()}
                />
              </button>
              <div className="mt-ct-cart-info">
                <div className="mt-ct-cart-name">{line.product.name}</div>
                <div className="mt-mono mt-ct-cart-meta">
                  {line.merchant} · {line.delivery}
                </div>
              </div>
              <div className="mt-ct-cart-actions">
                <div className="mt-qty" aria-label={`Quantity for ${line.product.name}`}>
                  <button
                    type="button"
                    aria-label="Decrease quantity"
                    onClick={() =>
                      onQty(line.id, line.merchant, line.qty - 1, cartAfterQty(line, line.qty - 1))
                    }
                  >
                    -
                  </button>
                  <span>{line.qty}</span>
                  <button
                    type="button"
                    aria-label="Increase quantity"
                    onClick={() =>
                      onQty(line.id, line.merchant, line.qty + 1, cartAfterQty(line, line.qty + 1))
                    }
                  >
                    +
                  </button>
                </div>
                <span className="mt-ct-cart-price">{money(line.price * line.qty)}</span>
                <button
                  className="mt-ct-cart-remove"
                  type="button"
                  aria-label={`Remove ${line.product.name}`}
                  onClick={() => onRemove(line.id, line.merchant, cartAfterRemove(line))}
                >
                  <CloseIcon size={12} />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="mt-ct-cart-empty">Your cart is empty.</p>
      )}
      <div className="mt-ct-cart-foot">
        <div>
          <span className="mt-mono mt-ct-cart-foot-label">
            {groups.length} merchant{groups.length === 1 ? '' : 's'}
          </span>
          <strong>{money(total)}</strong>
          {saved > 0 ? <span className="mt-ct-cart-save">Saved {money(saved)}</span> : null}
        </div>
        <div className="mt-ct-cart-foot-actions">
          <button className="mt-ct-cart-openfull" type="button" onClick={onOpenCart}>
            Full cart
          </button>
          <button
            className="mt-ct-cart-checkout"
            type="button"
            disabled={lines.length === 0}
            onClick={onCheckoutHere}
          >
            Checkout here
          </button>
        </div>
      </div>
    </div>
  )
}

function InlineCheckoutBlock({
  cart,
  products,
  onCheckout,
  onOpenCart,
  onOpenOrders,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onOpenCart: () => void
  onOpenOrders: () => void
}>) {
  const [payingMerchant, setPayingMerchant] = useState<string | null>(null)
  const [placedMerchant, setPlacedMerchant] = useState<string | null>(null)
  const lines = cartLines(cart, products)
  const groups = cartGroups(lines, false)
  const alerts = computeSmartAlerts(lines, products)
  const total = groups.reduce((sum, group) => sum + group.total, 0)

  const payGroup = async (group: (typeof groups)[number]) => {
    setPayingMerchant(group.merchant)
    setPlacedMerchant(null)
    try {
      await onCheckout({
        merchant: group.merchant,
        items: group.items,
        saved: group.itemDiscount,
        savedNote: group.found ? `${group.found.code.code} applied in chat` : 'Checked out in chat',
        checkoutUrl: firstUrl(...group.items.map((item) => item.checkoutUrl)),
        continueUrl: firstUrl(...group.items.map((item) => item.continueUrl)),
      })
      setPlacedMerchant(group.merchant)
    } finally {
      setPayingMerchant(null)
    }
  }

  return (
    <div className="mt-ct-block mt-ct-checkout">
      <div className="mt-ct-block-head">
        <div className="mt-mono mt-ct-block-key">Checkout in chat</div>
        <span className="mt-ct-code-save mt-mono">
          {groups.length} merchant{groups.length === 1 ? '' : 's'}
        </span>
      </div>
      {groups.length > 0 ? (
        <>
          <div className="mt-ct-checkout-total">
            <span>Total ready now</span>
            <strong>{money(total)}</strong>
          </div>
          {alerts.some((alert) => alert.kind === 'warn') ? (
            <div className="mt-ct-checkout-warn">
              Review compatibility warnings before paying. You can still continue from here.
            </div>
          ) : null}
          <div className="mt-ct-checkout-groups">
            {groups.map((group) => (
              <div className="mt-ct-cogroup" key={group.merchant}>
                <div className="mt-ct-cogroup-head">
                  <div>
                    <div className="mt-ct-cogroup-name">{group.merchant}</div>
                    <div className="mt-mono mt-ct-cogroup-meta">
                      {group.items.reduce((sum, line) => sum + line.qty, 0)} items · Delivery{' '}
                      {group.delivery === 0 ? 'free' : money(group.delivery)}
                    </div>
                  </div>
                  <strong>{money(group.total)}</strong>
                </div>
                {group.found ? (
                  <div className="mt-ct-cocode">
                    <span className="mt-mono">{group.found.code.code}</span>
                    saves {money(group.found.save)}
                  </div>
                ) : null}
                <div className="mt-ct-coframe">
                  <span>Payment</span>
                  <span>Address</span>
                  <span>Delivery</span>
                  <span>Review</span>
                </div>
                <button
                  className="mt-ct-cobtn"
                  type="button"
                  disabled={payingMerchant !== null}
                  onClick={() => void payGroup(group)}
                >
                  {payingMerchant === group.merchant
                    ? 'Placing order'
                    : placedMerchant === group.merchant
                      ? 'Order placed'
                      : `Pay ${money(group.total)} with Meant`}
                </button>
              </div>
            ))}
          </div>
        </>
      ) : (
        <div className="mt-ct-checkout-empty">
          <SparkMark size={13} />
          <span>
            {placedMerchant ? `Order placed with ${placedMerchant}.` : 'Your cart is empty.'}
          </span>
          <button className="mt-ct-mini-full" type="button" onClick={onOpenOrders}>
            Open orders
          </button>
        </div>
      )}
      <button className="mt-ct-cart-openfull" type="button" onClick={onOpenCart}>
        Open full cart
      </button>
    </div>
  )
}

function DiscoverChatBlockView({
  block,
  deliveryLocations,
  preferences,
  cart,
  cartProducts,
  savedSet,
  savePendingSet,
  pinnedSet,
  watchedSet,
  shelfProductSet,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onJustPick,
  onCompareHere,
  onOpenFullCompare,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onReviewCartHere,
  onRestoreCartLine,
  onCartQty,
  onCartRemove,
  onCheckout,
  onCheckoutHere,
  onShelfAddProduct,
  onDragProduct,
}: Readonly<{
  block: DiscoverChatBlock
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfProductSet: ReadonlySet<ProductId>
  onOpen: (product: Product, products?: readonly Product[]) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (kind: 'reviews' | 'code' | 'similar' | 'resale', product: Product) => void
  onJustPick: (products: readonly Product[]) => void
  onCompareHere: (products: readonly Product[]) => void
  onOpenFullCompare: (products: readonly Product[]) => void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onReviewCartHere: (lines?: readonly CartItem[], products?: readonly Product[]) => void
  onRestoreCartLine: (product: Product, merchant: string, price?: number) => void
  onCartQty: (id: ProductId, merchant: string, qty: number, nextCart: readonly CartItem[]) => void
  onCartRemove: (id: ProductId, merchant: string, nextCart: readonly CartItem[]) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onCheckoutHere: () => void
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
}>) {
  if (block.type === 'text') {
    return <p className="mt-ct-intro">{block.text}</p>
  }
  if (block.type === 'system') {
    return (
      <div className="mt-ct-system">
        <SparkMark size={11} color="var(--faint)" />
        {block.text}
      </div>
    )
  }
  if (block.type === 'products') {
    return (
      <DiscoverProductBatch
        products={block.products}
        query={block.query}
        deliveryLocations={deliveryLocations}
        preferences={preferences}
        savedSet={savedSet}
        savePendingSet={savePendingSet}
        pinnedSet={pinnedSet}
        watchedSet={watchedSet}
        shelfProductSet={shelfProductSet}
        onOpen={onOpen}
        onToggleSave={onToggleSave}
        onAddCart={onAddCart}
        onPin={onPin}
        onWatch={onWatch}
        onDig={onDig}
        onJustPick={onJustPick}
        onCompareHere={onCompareHere}
        onShelfAddProduct={onShelfAddProduct}
        onDragProduct={onDragProduct}
      />
    )
  }
  if (block.type === 'reviews') {
    const score = block.product.review.score
    return (
      <div className="mt-ct-block">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Reviews · {block.product.name}</div>
          <div className="mt-reviews-score">
            {score !== null ? (
              <span className="mt-stars">{'★'.repeat(Math.round(score))}</span>
            ) : null}
            <span className="mt-mono">
              {score !== null ? `${score.toFixed(1)} · ` : ''}
              {block.product.review.count.toLocaleString()}
            </span>
          </div>
        </div>
        <p className="mt-ct-review-sum">
          {block.product.review.insight ||
            searchProductReviewInsight(
              block.product.agentStage === 'candidate',
              block.product.review.score,
              block.product.review.count,
            )}
        </p>
      </div>
    )
  }
  if (block.type === 'code') {
    const offer = bestOffer(block.product, deliveryLocations)
    return (
      <div className="mt-ct-block mt-ct-code">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Discount found · {offer.merchant}</div>
          <span className="mt-ct-code-save mt-mono">Mocked code · save {money(block.saved)}</span>
        </div>
        <div className="mt-ct-code-row">
          <span className="mt-code">
            <span className="mt-code-val mt-mono">{block.code}</span>
            <span className="mt-code-act mt-mono">mock</span>
          </span>
          <div className="mt-ct-code-detail">
            <div className="mt-ct-code-label">A mocked coupon agent found this candidate.</div>
            <div className="mt-ct-code-price">
              <span className="mt-ct-code-was">{money(offer.price)}</span>
              <span className="mt-ct-code-now">
                {money(Math.max(0, offer.price - block.saved))}
              </span>
              <span className="mt-mono mt-ct-code-deliv">{offer.delivery}</span>
            </div>
          </div>
          <button
            className="mt-ct-addbtn solid"
            type="button"
            onClick={() => onAddCart(block.product)}
          >
            <CartIcon /> Add
          </button>
        </div>
      </div>
    )
  }
  if (block.type === 'similar') {
    return (
      <div className="mt-ct-block">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Similar to {block.product.name}</div>
        </div>
        <DiscoverProductBatch
          products={block.products}
          deliveryLocations={deliveryLocations}
          preferences={preferences}
          savedSet={savedSet}
          savePendingSet={savePendingSet}
          pinnedSet={pinnedSet}
          watchedSet={watchedSet}
          shelfProductSet={shelfProductSet}
          onOpen={onOpen}
          onToggleSave={onToggleSave}
          onAddCart={onAddCart}
          onPin={onPin}
          onWatch={onWatch}
          onDig={onDig}
          onJustPick={onJustPick}
          onCompareHere={onCompareHere}
          onShelfAddProduct={onShelfAddProduct}
          onDragProduct={onDragProduct}
        />
      </div>
    )
  }
  if (block.type === 'decision') {
    return (
      <div className="mt-ct-decision">
        <div className="mt-ct-decision-head">
          <span className="mt-mono mt-ct-decision-key">
            <SparkMark size={12} /> Meant's pick
          </span>
          <span className="mt-ct-decision-conf">
            {Math.max(76, block.product.match)}% confident
          </span>
        </div>
        <button className="mt-ct-decision-prod" type="button" onClick={() => onOpen(block.product)}>
          <span className="mt-ct-decision-media">
            <ProductArtwork product={block.product} label={block.product.category.toLowerCase()} />
          </span>
          <span className="mt-ct-decision-info">
            <span className="mt-mono mt-ct-decision-brand">{block.product.brand}</span>
            <span className="mt-ct-decision-name">{block.product.name}</span>
            <span className="mt-ct-decision-price">
              {money(productPriceFrom(block.product, deliveryLocations))}
            </span>
          </span>
        </button>
        <p className="mt-ct-decision-why">{productCuratedTake(block.product, preferences)}</p>
        {block.runnerUp ? (
          <div className="mt-ct-decision-beat">
            <span className="mt-mono">vs.</span> Beat {block.runnerUp.name} on match score and fit.
          </div>
        ) : null}
        <div className="mt-ct-decision-actions">
          <button
            className="mt-ct-addbtn solid"
            type="button"
            onClick={() => onAddCart(block.product)}
          >
            <CartIcon /> Add pick
          </button>
        </div>
      </div>
    )
  }
  if (block.type === 'watch') {
    return (
      <div className="mt-ct-watchalert">
        <span className="mt-ct-watchalert-ico">
          <SparkMark size={14} />
        </span>
        <div className="mt-ct-watchalert-body">
          <div className="mt-mono mt-ct-watchalert-key">Mock price watch</div>
          <div className="mt-ct-watchalert-text">
            The <b>{block.product.name}</b> dropped to {money(block.price)} at {block.merchant}.
          </div>
        </div>
        <button
          className="mt-ct-addbtn solid"
          type="button"
          onClick={() => onAddCart(block.product)}
        >
          Add
        </button>
      </div>
    )
  }
  if (block.type === 'friendvote') {
    return (
      <div className="mt-ct-friend">
        <span className="mt-ct-friend-av">{block.person.slice(0, 1)}</span>
        <div className="mt-ct-friend-body">
          <div className="mt-ct-friend-head">
            <span>
              <b>{block.person}</b> weighed in on your pick
            </span>
            <span className={`mt-ct-friend-vote ${block.vote}`}>
              {block.vote === 'up' ? 'Yes, this one' : "I'd skip it"}
            </span>
          </div>
          <p className="mt-ct-friend-quote">"{block.note}"</p>
          <button className="mt-ct-friend-prod" type="button" onClick={() => onOpen(block.product)}>
            <span className="mt-ct-friend-thumb">
              <ProductArtwork
                product={block.product}
                label={block.product.category.toLowerCase()}
              />
            </span>
            <span className="mt-ct-friend-name">{block.product.name}</span>
          </button>
          {block.vote === 'up' ? (
            <button className="mt-ct-addbtn" type="button" onClick={() => onAddCart(block.product)}>
              <CartIcon /> Add their pick
            </button>
          ) : null}
        </div>
      </div>
    )
  }
  if (block.type === 'added') {
    const addedPrice = block.price ?? productPriceFrom(block.product, deliveryLocations)
    const addedCount = block.count ?? cart.reduce((sum, item) => sum + item.qty, 0)
    return (
      <div className="mt-ct-added">
        <span className="mt-ct-added-check">
          <svg width="13" height="13" viewBox="0 0 14 14" aria-hidden>
            <path
              d="M3 7.3l2.6 2.6L11 4.2"
              stroke="currentColor"
              strokeWidth="1.9"
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        <div className="mt-ct-added-body">
          <span className="mt-ct-added-name">
            Added <b>{block.product.name}</b> to your cart
          </span>
          <span className="mt-ct-added-meta">
            {money(addedPrice)} · {block.merchant}
            {block.code ? ` · code ${block.code}` : ''} · {addedCount} in cart
          </span>
        </div>
        <div className="mt-ct-added-actions">
          <button
            className="mt-ct-added-go"
            type="button"
            onClick={() => {
              const liveLine = cart.find(
                (item) => item.id === block.product.id && item.merchant === block.merchant,
              )
              if (!liveLine) {
                onRestoreCartLine(block.product, block.merchant, addedPrice)
              }
              onReviewCartHere(
                [
                  liveLine ?? {
                    id: block.product.id,
                    merchant: block.merchant,
                    qty: 1,
                    productTitle: block.product.name,
                    imageUrl: block.product.imageUrl,
                    unitPriceAmount: String(addedPrice),
                  },
                ],
                [block.product],
              )
            }}
          >
            Review here
          </button>
          <button className="mt-ct-added-go ghost" type="button" onClick={onOpenCart}>
            Open cart
          </button>
        </div>
      </div>
    )
  }
  if (block.type === 'saved') {
    return (
      <div className="mt-ct-block mt-ct-mini2">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Saved items</div>
          <span className="mt-ct-code-save mt-mono">{block.products.length} saved</span>
        </div>
        {block.products.length > 0 ? (
          <div className="mt-ct-mini2-grid">
            {block.products.slice(0, 4).map((product) => (
              <button
                key={product.id}
                className="mt-ct-mini2-card"
                type="button"
                onClick={() => onOpen(product)}
              >
                <span className="mt-ct-mini2-media">
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                </span>
                <span className="mt-ct-mini2-name">{product.name}</span>
                <span className="mt-ct-mini2-price">
                  {money(productPriceFrom(product, deliveryLocations))}
                </span>
              </button>
            ))}
          </div>
        ) : (
          <p className="mt-ct-cart-empty">Nothing saved yet.</p>
        )}
        <button className="mt-ct-mini-full" type="button" onClick={onOpenSaved}>
          Open saved
        </button>
      </div>
    )
  }
  if (block.type === 'orders') {
    return (
      <div className="mt-ct-block mt-ct-mini2">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Recent orders</div>
          <span className="mt-ct-code-save mt-mono">{block.orders.length} total</span>
        </div>
        {block.orders.length > 0 ? (
          <div className="mt-ct-mini2-list">
            {block.orders.slice(0, 3).map((order) => (
              <div className="mt-ct-mini2-row" key={order.id}>
                <div className="mt-ct-mini2-info">
                  <div className="mt-ct-mini2-title">{order.id}</div>
                  <div className="mt-mono mt-ct-mini2-meta">
                    {order.status} · {formatOrderDate(order.date)}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-ct-cart-empty">No orders yet.</p>
        )}
        <button className="mt-ct-mini-full" type="button" onClick={onOpenOrders}>
          Open orders
        </button>
      </div>
    )
  }
  if (block.type === 'prefs') {
    return (
      <div className="mt-ct-block mt-ct-mini2">
        <div className="mt-ct-block-head">
          <div className="mt-mono mt-ct-block-key">Your preferences</div>
          <span className="mt-ct-code-save mt-mono">{block.preferences.length} active</span>
        </div>
        <div className="mt-chips">
          {block.preferences.slice(0, 12).map((preference) => (
            <span key={preference.id} className="mt-chip mt-chip-muted mt-chip-sm in">
              {preference.label}
            </span>
          ))}
        </div>
        <button className="mt-ct-mini-full" type="button" onClick={onOpenPrefs}>
          Edit preferences
        </button>
      </div>
    )
  }
  if (block.type === 'cart') {
    return (
      <InlineCartBlock
        cart={cartItemsWithFallback(cart, block.lines)}
        products={productsWithFallback(block.products, cartProducts)}
        onQty={onCartQty}
        onRemove={onCartRemove}
        onAddCart={onAddCart}
        onOpenCart={onOpenCart}
        onCheckoutHere={onCheckoutHere}
      />
    )
  }
  if (block.type === 'checkout') {
    return (
      <InlineCheckoutBlock
        cart={cart}
        products={cartProducts}
        onCheckout={onCheckout}
        onOpenCart={onOpenCart}
        onOpenOrders={onOpenOrders}
      />
    )
  }
  return (
    <InlineMiniCompareBlock
      block={block}
      deliveryLocations={deliveryLocations}
      onOpen={onOpen}
      onAddCart={onAddCart}
      onOpenFullCompare={onOpenFullCompare}
    />
  )
}

function DiscoverChatMessageRow({
  message,
  deliveryLocations,
  preferences,
  cart,
  cartProducts,
  savedSet,
  savePendingSet,
  pinnedSet,
  watchedSet,
  shelfMessageSet,
  shelfProductSet,
  flash,
  onOpen,
  onToggleSave,
  onAddCart,
  onPin,
  onWatch,
  onDig,
  onJustPick,
  onCompareHere,
  onOpenFullCompare,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onReviewCartHere,
  onRestoreCartLine,
  onCartQty,
  onCartRemove,
  onCheckout,
  onCheckoutHere,
  onDelete,
  onShelfAddMessage,
  onShelfAddProduct,
  onDragMessage,
  onDragProduct,
}: Readonly<{
  message: DiscoverChatMessage
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  pinnedSet: ReadonlySet<ProductId>
  watchedSet: ReadonlySet<ProductId>
  shelfMessageSet: ReadonlySet<string>
  shelfProductSet: ReadonlySet<ProductId>
  flash: boolean
  onOpen: (product: Product, products?: readonly Product[]) => void
  onToggleSave: (product: Product) => void
  onAddCart: (product: Product) => void
  onPin: (product: Product) => void
  onWatch: (product: Product) => void
  onDig: (kind: 'reviews' | 'code' | 'similar' | 'resale', product: Product) => void
  onJustPick: (products: readonly Product[]) => void
  onCompareHere: (products: readonly Product[]) => void
  onOpenFullCompare: (products: readonly Product[]) => void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onReviewCartHere: (lines?: readonly CartItem[], products?: readonly Product[]) => void
  onRestoreCartLine: (product: Product, merchant: string, price?: number) => void
  onCartQty: (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    qty: number,
    nextCart: readonly CartItem[],
  ) => void
  onCartRemove: (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
  ) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onCheckoutHere: () => void
  onDelete: (messageId: string) => void
  onShelfAddMessage: (message: DiscoverChatMessage, sourceElement: HTMLElement) => void
  onShelfAddProduct: (product: Product, sourceElement: HTMLElement) => void
  onDragMessage: (event: ReactDragEvent<HTMLElement>, message: DiscoverChatMessage) => void
  onDragProduct: (event: ReactDragEvent<HTMLElement>, product: Product) => void
}>) {
  const onShelf = shelfMessageSet.has(message.id)
  const copyMessage = () => copyTextToClipboard(discoverChatMessageCopyText(message))

  if (message.role === 'you') {
    return (
      <div
        className={`mt-ct-msg mt-ct-you ${flash ? 'flash' : ''}`}
        data-mid={message.id}
        draggable
        onDragStart={(event) => onDragMessage(event, message)}
        onDragEnd={() => document.body.classList.remove('mt-dragging')}
      >
        <DustWrap
          side="you"
          onGone={() => onDelete(message.id)}
          onSetAside={(sourceElement) => onShelfAddMessage(message, sourceElement)}
          onShelfDragStart={(event) => onDragMessage(event, message)}
          onCopy={copyMessage}
          saved={onShelf}
        >
          <div className="mt-ct-you-bubble">
            {message.productContext ? (
              <ProductContextChip product={message.productContext} />
            ) : null}
            {message.text ? <span className="mt-ct-you-text">{message.text}</span> : null}
          </div>
        </DustWrap>
      </div>
    )
  }

  return (
    <div
      className={`mt-ct-msg mt-ct-meant ${flash ? 'flash' : ''}`}
      data-mid={message.id}
      draggable
      onDragStart={(event) => onDragMessage(event, message)}
      onDragEnd={() => document.body.classList.remove('mt-dragging')}
    >
      <DustWrap
        side="meant"
        onGone={() => onDelete(message.id)}
        onSetAside={(sourceElement) => onShelfAddMessage(message, sourceElement)}
        onShelfDragStart={(event) => onDragMessage(event, message)}
        onCopy={copyMessage}
        saved={onShelf}
      >
        <div className="mt-ct-meant-inner">
          <span className="mt-ct-av">
            <SparkMark size={13} />
          </span>
          <div className="mt-ct-meant-body">
            {message.blocks?.map((block, index) => (
              <DiscoverChatBlockView
                key={`${message.id}-${index}`}
                block={block}
                deliveryLocations={deliveryLocations}
                preferences={preferences}
                cart={cart}
                cartProducts={cartProducts}
                savedSet={savedSet}
                savePendingSet={savePendingSet}
                pinnedSet={pinnedSet}
                watchedSet={watchedSet}
                shelfProductSet={shelfProductSet}
                onOpen={onOpen}
                onToggleSave={onToggleSave}
                onAddCart={onAddCart}
                onPin={onPin}
                onWatch={onWatch}
                onDig={onDig}
                onJustPick={onJustPick}
                onCompareHere={onCompareHere}
                onOpenFullCompare={onOpenFullCompare}
                onOpenSaved={onOpenSaved}
                onOpenOrders={onOpenOrders}
                onOpenPrefs={onOpenPrefs}
                onOpenCart={onOpenCart}
                onReviewCartHere={onReviewCartHere}
                onRestoreCartLine={onRestoreCartLine}
                onCartQty={(id, merchant, qty, nextCart) =>
                  onCartQty(message.id, index, id, merchant, qty, nextCart)
                }
                onCartRemove={(id, merchant, nextCart) =>
                  onCartRemove(message.id, index, id, merchant, nextCart)
                }
                onCheckout={onCheckout}
                onCheckoutHere={onCheckoutHere}
                onShelfAddProduct={onShelfAddProduct}
                onDragProduct={onDragProduct}
              />
            ))}
            {message.pending ? (
              <div className="mt-ct-system">
                <span className="mt-scan-pulse" />
                Meant is checking merchants and ranking matches.
              </div>
            ) : null}
          </div>
        </div>
      </DustWrap>
    </div>
  )
}

function DiscoverShareSheet({
  thread,
  onClose,
  onSend,
}: Readonly<{
  thread: DiscoverChatThread
  onClose: () => void
  onSend: (person: string) => void
}>) {
  const [copied, setCopied] = useState(false)
  const slug = thread.title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 14)
  const link = `meant.app/s/${thread.id}-${slug || 'chat'}`
  const people = [
    { name: 'Alex', initial: 'A' },
    { name: 'Sam', initial: 'S' },
    { name: 'Jordan', initial: 'J' },
  ]
  const copy = () => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(link).catch(() => undefined)
    }
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1600)
  }

  return (
    <div className="mt-modal-root open" role="dialog" aria-label="Share for a second opinion">
      <button className="mt-modal-scrim" type="button" aria-label="Close" onClick={onClose} />
      <div className="mt-ct-sharesheet">
        <button className="mt-modal-close" type="button" onClick={onClose} aria-label="Close">
          <CloseIcon size={14} />
        </button>
        <div className="mt-ct-share-eyebrow mt-mono">Second opinion</div>
        <h3 className="mt-ct-share-title">Ask someone you trust</h3>
        <p className="mt-ct-share-sub">
          Send <b>"{thread.title}"</b> to a friend or partner. They can see your picks and vote
          before you buy - no account, no sign-up.
        </p>
        <div className="mt-ct-share-linkrow">
          <span className="mt-ct-share-link mt-mono">{link}</span>
          <button
            className={`mt-ct-share-copy ${copied ? 'done' : ''}`}
            type="button"
            onClick={copy}
          >
            {copied ? 'Copied' : 'Copy link'}
          </button>
        </div>
        <div className="mt-ct-share-or">
          <span>or send straight to</span>
        </div>
        <div className="mt-ct-share-people">
          {people.map((person) => (
            <button
              key={person.name}
              className="mt-ct-share-person"
              type="button"
              onClick={() => onSend(person.name)}
            >
              <span className="mt-ct-share-person-av">{person.initial}</span>
              <span>{person.name}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

function compactChatHistoryText(value: string): string {
  const normalized = value.trim().replace(/\s+/g, ' ')
  if (!normalized) {
    return ''
  }
  return normalized.length > 82 ? `${normalized.slice(0, 79)}...` : normalized
}

function discoverBlockPreview(block: DiscoverChatBlock): string {
  if (block.type === 'text' || block.type === 'system') {
    return block.text
  }
  if (block.type === 'products') {
    return block.query
      ? `${block.products.length} products for "${block.query}"`
      : `${block.products.length} products`
  }
  if (block.type === 'cart') {
    return `${block.lines.length} cart ${block.lines.length === 1 ? 'item' : 'items'}`
  }
  if (block.type === 'checkout') {
    return `Checkout across ${block.merchantCount} ${block.merchantCount === 1 ? 'merchant' : 'merchants'}`
  }
  if (block.type === 'minicompare') {
    return `Compare ${block.products.length} products`
  }
  if (block.type === 'saved') {
    return `${block.products.length} saved ${block.products.length === 1 ? 'item' : 'items'}`
  }
  if (block.type === 'orders') {
    return `${block.orders.length} recent ${block.orders.length === 1 ? 'order' : 'orders'}`
  }
  if (block.type === 'prefs') {
    return `${block.preferences.length} active preferences`
  }
  return 'Product follow-up'
}

function discoverMessagePreview(message: DiscoverChatMessage): string {
  if (message.text) {
    return message.text
  }
  const block = message.blocks?.find((candidate) =>
    compactChatHistoryText(discoverBlockPreview(candidate)),
  )
  return block ? discoverBlockPreview(block) : ''
}

function discoverThreadPreview(thread: DiscoverChatThread): string {
  for (let index = thread.messages.length - 1; index >= 0; index -= 1) {
    const preview = compactChatHistoryText(discoverMessagePreview(thread.messages[index]))
    if (preview) {
      return preview
    }
  }
  return 'No messages yet'
}

function discoverThreadTime(thread: DiscoverChatThread): number | null {
  return thread.updatedAt ?? thread.createdAt ?? null
}

function discoverThreadTimeLabel(thread: DiscoverChatThread): string {
  const timestamp = discoverThreadTime(thread)
  if (!timestamp) {
    return 'Saved'
  }
  const date = new Date(timestamp)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) {
    return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(date)
  }
  if (date.toDateString() === yesterday.toDateString()) {
    return 'Yesterday'
  }
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date)
}

function discoverThreadMessageCount(thread: DiscoverChatThread): string {
  const count = thread.messages.length
  return `${count} ${count === 1 ? 'message' : 'messages'}`
}

function DiscoverThreadTabs({
  threads,
  activeId,
  onSelect,
  onClose,
  onNew,
  onRename,
  onShare,
  onReorder,
}: Readonly<{
  threads: readonly DiscoverChatThread[]
  activeId: string
  onSelect: (threadId: string) => void
  onClose: (threadId: string) => void
  onNew: () => void
  onRename: (threadId: string, title: string) => void
  onShare: () => void
  onReorder: (fromIndex: number, toIndex: number) => void
}>) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [dragId, setDragId] = useState<string | null>(null)
  const [overId, setOverId] = useState<string | null>(null)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [tabsOverflow, setTabsOverflow] = useState(false)
  const [canScrollTabsLeft, setCanScrollTabsLeft] = useState(false)
  const [canScrollTabsRight, setCanScrollTabsRight] = useState(false)
  const tabsScrollRef = useRef<HTMLDivElement | null>(null)
  const historyRef = useRef<HTMLDivElement | null>(null)
  const historyThreads = useMemo(
    () =>
      threads
        .map((thread, index) => ({ thread, index, time: discoverThreadTime(thread) ?? 0 }))
        .sort((left, right) => right.time - left.time || left.index - right.index)
        .map(({ thread }) => thread),
    [threads],
  )

  const updateTabsScrollState = useCallback(() => {
    const element = tabsScrollRef.current
    if (!element) {
      setTabsOverflow(false)
      setCanScrollTabsLeft(false)
      setCanScrollTabsRight(false)
      return
    }
    const overflow = element.scrollWidth > element.clientWidth + 1
    const maxLeft = Math.max(0, element.scrollWidth - element.clientWidth)
    setTabsOverflow(overflow)
    setCanScrollTabsLeft(overflow && element.scrollLeft > 2)
    setCanScrollTabsRight(overflow && element.scrollLeft < maxLeft - 2)
  }, [])

  const scrollTabs = (direction: 'left' | 'right') => {
    const element = tabsScrollRef.current
    if (!element) {
      return
    }
    element.scrollBy({
      left: (direction === 'left' ? -1 : 1) * Math.max(220, element.clientWidth * 0.72),
      behavior: 'smooth',
    })
  }

  const beginEdit = (thread: DiscoverChatThread) => {
    setEditingId(thread.id)
    setDraft(thread.title)
  }
  const commitEdit = () => {
    if (!editingId) {
      return
    }
    onRename(editingId, draft.trim() || 'Untitled')
    setEditingId(null)
  }
  const dropThread = (targetId: string) => {
    if (!dragId || dragId === targetId) {
      setDragId(null)
      setOverId(null)
      return
    }
    const from = threads.findIndex((thread) => thread.id === dragId)
    const to = threads.findIndex((thread) => thread.id === targetId)
    if (from >= 0 && to >= 0) {
      onReorder(from, to)
    }
    setDragId(null)
    setOverId(null)
  }

  useEffect(() => {
    updateTabsScrollState()
    const element = tabsScrollRef.current
    if (!element) {
      return undefined
    }
    const resizeObserver = new ResizeObserver(updateTabsScrollState)
    resizeObserver.observe(element)
    return () => resizeObserver.disconnect()
  }, [threads, updateTabsScrollState])

  useEffect(() => {
    const activeTab = tabsScrollRef.current?.querySelector<HTMLElement>(
      '[data-active-thread="true"]',
    )
    activeTab?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
    window.requestAnimationFrame(updateTabsScrollState)
  }, [activeId, updateTabsScrollState])

  useEffect(() => {
    if (!historyOpen) {
      return undefined
    }
    const onDown = (event: MouseEvent) => {
      if (!historyRef.current?.contains(event.target as Node)) {
        setHistoryOpen(false)
      }
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setHistoryOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [historyOpen])

  return (
    <div className="mt-ct-tabs">
      <div
        className={`mt-ct-tabs-strip ${tabsOverflow ? 'overflowing' : ''} ${
          canScrollTabsLeft ? 'can-left' : ''
        } ${canScrollTabsRight ? 'can-right' : ''}`}
      >
        <button
          className="mt-ct-tabs-arrow left"
          type="button"
          disabled={!canScrollTabsLeft}
          onClick={() => scrollTabs('left')}
          aria-label="Previous chats"
          title="Previous chats"
        >
          <ChevronIcon direction="left" size={15} />
        </button>
        <div
          className="mt-ct-tabs-scroll"
          ref={tabsScrollRef}
          onScroll={updateTabsScrollState}
          onWheel={(event) => {
            const element = tabsScrollRef.current
            if (!element || !tabsOverflow || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) {
              return
            }
            event.preventDefault()
            element.scrollLeft += event.deltaY
            updateTabsScrollState()
          }}
        >
          {threads.map((thread) => (
            <div
              key={thread.id}
              data-thread-id={thread.id}
              data-active-thread={thread.id === activeId ? 'true' : undefined}
              draggable={editingId !== thread.id}
              className={`mt-ct-tab ${thread.id === activeId ? 'on' : ''} ${
                dragId === thread.id ? 'dragging' : ''
              } ${overId === thread.id ? 'over' : ''}`}
              onClick={() => onSelect(thread.id)}
              onDragStart={(event) => {
                setDragId(thread.id)
                event.dataTransfer.effectAllowed = 'move'
                event.dataTransfer.setData('text/plain', 'tab')
              }}
              onDragOver={(event) => {
                event.preventDefault()
                if (dragId && overId !== thread.id) {
                  setOverId(thread.id)
                }
              }}
              onDrop={(event) => {
                event.preventDefault()
                dropThread(thread.id)
              }}
              onDragEnd={() => {
                setDragId(null)
                setOverId(null)
              }}
              title="Drag to reorder your chats"
            >
              <SparkMark
                size={11}
                color={thread.id === activeId ? 'var(--accent)' : 'var(--faint)'}
              />
              {editingId === thread.id ? (
                <input
                  className="mt-ct-tab-edit"
                  autoFocus
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onClick={(event) => event.stopPropagation()}
                  onBlur={commitEdit}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      commitEdit()
                    }
                    if (event.key === 'Escape') {
                      setEditingId(null)
                    }
                  }}
                />
              ) : (
                <span
                  className="mt-ct-tab-title"
                  title="Double-click to rename this mission"
                  onDoubleClick={(event) => {
                    event.stopPropagation()
                    beginEdit(thread)
                  }}
                >
                  {thread.title}
                </span>
              )}
              <button
                className="mt-ct-tab-x"
                type="button"
                onClick={(event) => {
                  event.stopPropagation()
                  onClose(thread.id)
                }}
                aria-label="Close chat"
                title={threads.length > 1 ? 'Close this chat' : 'Close chat - back to start'}
              >
                <CloseIcon size={11} />
              </button>
            </div>
          ))}
        </div>
        <button
          className="mt-ct-tabs-arrow right"
          type="button"
          disabled={!canScrollTabsRight}
          onClick={() => scrollTabs('right')}
          aria-label="Next chats"
          title="Next chats"
        >
          <ChevronIcon direction="right" size={15} />
        </button>
      </div>
      <div className="mt-ct-tabs-right">
        <button
          className="mt-ct-tabtool"
          type="button"
          onClick={onShare}
          title="Share this chat for a second opinion"
        >
          <ShareIcon />
          Share
        </button>
        <div className="mt-ct-history-wrap" ref={historyRef}>
          <button
            className={`mt-ct-tabtool ${historyOpen ? 'on' : ''}`}
            type="button"
            onClick={() => setHistoryOpen((current) => !current)}
            aria-expanded={historyOpen}
            aria-haspopup="dialog"
            title="Open chat history"
          >
            <HistoryIcon size={15} />
            History
            <span className="mt-ct-history-badge">{threads.length}</span>
          </button>
          {historyOpen ? (
            <div className="mt-ct-history-pop" role="dialog" aria-label="Chat history">
              <div className="mt-ct-history-head">
                <span>Chat history</span>
                <span>{threads.length} saved</span>
              </div>
              <div className="mt-ct-history-list">
                {historyThreads.map((thread) => (
                  <button
                    key={thread.id}
                    className={`mt-ct-history-row ${thread.id === activeId ? 'active' : ''}`}
                    type="button"
                    onClick={() => {
                      onSelect(thread.id)
                      setHistoryOpen(false)
                    }}
                  >
                    <span className="mt-ct-history-main">
                      <span className="mt-ct-history-title">{thread.title}</span>
                      <span className="mt-ct-history-preview">{discoverThreadPreview(thread)}</span>
                    </span>
                    <span className="mt-ct-history-meta">
                      <span>{discoverThreadTimeLabel(thread)}</span>
                      <span>{discoverThreadMessageCount(thread)}</span>
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </div>
        <button className="mt-ct-newtab" type="button" onClick={onNew}>
          <PlusIcon />
          New chat
        </button>
      </div>
    </div>
  )
}

function ChatDiscoverView({
  profile,
  greeting,
  products,
  hiddenByShip,
  agentActivities,
  deliveryLocations,
  prompts,
  reply,
  query,
  loading,
  loadingMore,
  hasMore,
  error,
  preferences,
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  merchantsLoading,
  savedProducts,
  cart,
  cartProducts,
  orders,
  shelf,
  shelfFlashMessageId,
  productDetailChatRequest,
  discoverFindRequest,
  savedSet,
  savePendingSet,
  onSubmit,
  onLoadMore,
  onClear,
  onMerchant,
  onOpen,
  onToggleSave,
  onAddProductToCart,
  onFallbackAddToCart,
  onCompareProducts,
  onCartQty,
  onCartRemove,
  onCheckout,
  onOpenSaved,
  onOpenOrders,
  onOpenPrefs,
  onOpenCart,
  onOpenShelf,
  onShelfAddMessage,
  onShelfAddProduct,
  onProductDetailChatRequestHandled,
  onFlashMessage,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  products: readonly Product[]
  hiddenByShip: number
  agentActivities: readonly AgentActivity[]
  deliveryLocations: readonly UserLocation[]
  prompts: readonly string[]
  reply: string | null
  query: string
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  error: string | null
  preferences: readonly Preference[]
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  merchantsLoading: boolean
  savedProducts: readonly Product[]
  cart: readonly CartItem[]
  cartProducts: readonly Product[]
  orders: readonly Order[]
  shelf: readonly ShelfItem[]
  shelfFlashMessageId: string | null
  productDetailChatRequest: ProductDetailChatRequest | null
  discoverFindRequest: DiscoverFindRequest | null
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  onSubmit: (query: string) => void
  onLoadMore: () => void
  onClear: () => void
  onMerchant: (merchant: MerchantProfile | null) => void
  onOpen: (product: Product, products?: readonly Product[]) => void
  onToggleSave: (product: Product) => void
  onAddProductToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  onFallbackAddToCart: (product: Product, offer: Offer) => void
  onCompareProducts: (products: readonly Product[]) => void
  onCartQty: (id: ProductId, merchant: string, qty: number) => void
  onCartRemove: (id: ProductId, merchant: string) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onOpenSaved: () => void
  onOpenOrders: () => void
  onOpenPrefs: () => void
  onOpenCart: () => void
  onOpenShelf: () => void
  onShelfAddMessage: (payload: Extract<ShelfDragPayload, { kind: 'message' }>) => void
  onShelfAddProduct: (snapshot: ShelfProductSnapshot) => void
  onProductDetailChatRequestHandled: (requestId: string) => void
  onFlashMessage: (messageId: string) => void
}>) {
  const [threads, setThreads] = useStoredState<DiscoverChatThread[]>(
    'meant.discoverChatThreads',
    initialDiscoverChatThreads(),
  )
  const [activeThreadId, setActiveThreadId] = useStoredState<string>(
    'meant.discoverActiveThreadId',
    threads[0]?.id ?? DEFAULT_DISCOVER_CHAT_TITLE,
  )
  const fallbackThread = useMemo(() => createDiscoverChatThread(), [])
  const activeThread =
    threads.find((thread) => thread.id === activeThreadId) ?? threads[0] ?? fallbackThread
  const activeThreadIdSafe = activeThread.id
  const messages = activeThread.messages
  const [activeSearchTarget, setActiveSearchTarget] = useState<{
    threadId: string
    messageId: string
  } | null>(null)
  const [shareOpen, setShareOpen] = useState(false)
  const [pinnedIds, setPinnedIds] = useStoredState<ProductId[]>('meant.chatPinned', [])
  const [trayClearing, setTrayClearing] = useState(false)
  const [watchedIds, setWatchedIds] = useState<ProductId[]>([])
  const chatBottomRef = useRef<HTMLDivElement | null>(null)
  const previousMessageCountRef = useRef(messages.length)
  const previousActiveThreadIdRef = useRef(activeThreadIdSafe)
  const handledDiscoverFindRequestRef = useRef<string | null>(null)
  const pinnedSet = useMemo(() => new Set(pinnedIds), [pinnedIds])
  const watchedSet = useMemo(() => new Set(watchedIds), [watchedIds])
  const shelfMessageSet = useMemo(
    () =>
      new Set(
        shelf
          .filter(
            (item): item is Extract<ShelfItem, { kind: 'message' }> => item.kind === 'message',
          )
          .map((item) => item.messageId),
      ),
    [shelf],
  )
  const shelfProductSet = useMemo(
    () =>
      new Set(
        shelf
          .filter(
            (item): item is Extract<ShelfItem, { kind: 'product' }> => item.kind === 'product',
          )
          .map((item) => item.productId),
      ),
    [shelf],
  )
  const displayProducts = useMemo(() => {
    if (products.length > 0) {
      return products
    }
    if (savedProducts.length > 0) {
      return savedProducts
    }
    return PRODUCTS.slice(0, 4)
  }, [products, savedProducts])
  const knownProductsById = useMemo(() => {
    const next = new Map<ProductId, Product>()
    for (const product of [...PRODUCTS, ...savedProducts, ...cartProducts, ...displayProducts]) {
      next.set(product.id, product)
    }
    return next
  }, [cartProducts, displayProducts, savedProducts])
  const pinnedProducts = pinnedIds
    .map((id) => displayProducts.find((product) => product.id === id))
    .filter((product): product is Product => Boolean(product))
  const currentCartLines = cartLines(cart, cartProducts)
  const activeSearchProducts = useMemo(
    () => (query ? products.filter(isRenderableSearchProduct) : []),
    [products, query],
  )

  useEffect(() => {
    if (!discoverFindRequest || handledDiscoverFindRequestRef.current === discoverFindRequest.id) {
      return
    }
    handledDiscoverFindRequestRef.current = discoverFindRequest.id

    const orderedThreads = [...threads].sort((left, right) => {
      if (left.id === activeThreadIdSafe) {
        return -1
      }
      if (right.id === activeThreadIdSafe) {
        return 1
      }
      return (right.updatedAt ?? 0) - (left.updatedAt ?? 0)
    })
    for (const thread of orderedThreads) {
      for (let index = thread.messages.length - 1; index >= 0; index -= 1) {
        const message = thread.messages[index]
        const matchesRequest =
          discoverFindRequest.kind === 'message'
            ? message.id === discoverFindRequest.messageId
            : message.productContext?.id === discoverFindRequest.productId ||
              Boolean(
                message.blocks?.some((block) =>
                  discoverBlockHasProduct(block, discoverFindRequest.productId),
                ),
              )
        if (!matchesRequest) {
          continue
        }
        setActiveThreadId(thread.id)
        window.setTimeout(() => {
          const element = document.querySelector(`[data-mid="${CSS.escape(message.id)}"]`)
          if (!(element instanceof HTMLElement)) {
            return
          }
          const top = window.scrollY + element.getBoundingClientRect().top - 150
          window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
          onFlashMessage(message.id)
        }, 160)
        return
      }
    }
  }, [activeThreadIdSafe, discoverFindRequest, onFlashMessage, setActiveThreadId, threads])

  useEffect(() => {
    if (threads.some((thread) => !thread.createdAt || !thread.updatedAt)) {
      setThreads((current) => normalizeDiscoverChatThreads(current))
    }
  }, [setThreads, threads])

  const scrollChatToBottom = useCallback(() => {
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
      })
    })
  }, [])

  useEffect(() => {
    const messageCountIncreased = messages.length > previousMessageCountRef.current
    const activeThreadChanged = activeThreadIdSafe !== previousActiveThreadIdRef.current
    if (activeThreadChanged) {
      scrollChatToBottom()
    } else if (messageCountIncreased) {
      scrollChatToBottom()
    }
    previousMessageCountRef.current = messages.length
    previousActiveThreadIdRef.current = activeThreadIdSafe
  }, [activeThreadIdSafe, messages.length, scrollChatToBottom])

  useEffect(() => {
    if (threads.length === 0) {
      const nextThread = createDiscoverChatThread()
      setThreads([nextThread])
      setActiveThreadId(nextThread.id)
      return
    }
    if (!threads.some((thread) => thread.id === activeThreadId)) {
      setActiveThreadId(threads[0].id)
    }
  }, [activeThreadId, setActiveThreadId, setThreads, threads])

  useEffect(() => {
    if (pinnedProducts.length === 0 && trayClearing) {
      setTrayClearing(false)
    }
  }, [pinnedProducts.length, trayClearing])

  const updateThreadMessages = useCallback(
    (threadId: string, action: SetStateAction<readonly DiscoverChatMessage[]>) => {
      const now = Date.now()
      setThreads((current) =>
        current.map((thread) =>
          thread.id === threadId
            ? { ...thread, messages: resolveStateAction(action, thread.messages), updatedAt: now }
            : thread,
        ),
      )
    },
    [setThreads],
  )

  const setMessages = useCallback(
    (action: SetStateAction<readonly DiscoverChatMessage[]>) => {
      updateThreadMessages(activeThreadIdSafe, action)
    },
    [activeThreadIdSafe, updateThreadMessages],
  )

  const appendMessagesToActiveThread = useCallback(
    (
      nextMessages: readonly DiscoverChatMessage[],
      options: { titleSeed?: string; focusProductId?: ProductId } = {},
    ) => {
      const now = Date.now()
      setThreads((current) =>
        current.map((thread) => {
          if (thread.id !== activeThreadIdSafe) {
            return thread
          }
          const shouldTitle = thread.messages.length === 0 && !thread.named && options.titleSeed
          return {
            ...thread,
            title: shouldTitle ? deriveDiscoverChatTitle(options.titleSeed ?? '') : thread.title,
            focusProductId: options.focusProductId ?? thread.focusProductId,
            messages: [...thread.messages, ...nextMessages],
            updatedAt: now,
          }
        }),
      )
    },
    [activeThreadIdSafe, setThreads],
  )

  useEffect(() => {
    if (!activeSearchTarget) {
      return
    }
    updateThreadMessages(activeSearchTarget.threadId, (current) =>
      current.map((message) => {
        if (message.id !== activeSearchTarget.messageId) {
          return message
        }
        const statusText = error
          ? 'Live product search is unavailable, so I mocked a starter shortlist from the demo catalog.'
          : (reply ??
            (activeSearchProducts.length > 0
              ? `I found ${activeSearchProducts.length} match${activeSearchProducts.length === 1 ? '' : 'es'} so far.`
              : 'Searching across supported merchants...'))
        return {
          ...message,
          pending: loading || loadingMore,
          blocks: [
            { type: 'text', text: statusText },
            { type: 'products', products: activeSearchProducts, query },
          ],
        }
      }),
    )
    scrollChatToBottom()
    if (!loading && !loadingMore && (reply || error)) {
      setActiveSearchTarget(null)
    }
  }, [
    activeSearchTarget,
    activeSearchProducts,
    error,
    loading,
    loadingMore,
    query,
    reply,
    scrollChatToBottom,
    updateThreadMessages,
  ])

  const appendMessagePair = (text: string, blocks: readonly DiscoverChatBlock[]) => {
    appendMessagesToActiveThread(
      [
        { id: nextDiscoverChatMessageId(), role: 'you', text },
        { id: nextDiscoverChatMessageId(), role: 'ai', blocks },
      ],
      { titleSeed: text },
    )
  }

  const appendProductDetailQuestion = useCallback(
    (product: Product, question: string, requestProducts: readonly Product[]) => {
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'you',
            text: question,
            productContext: product,
          },
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: productDetailChatBlocks(question, product, requestProducts, preferences),
          },
        ],
        { titleSeed: question, focusProductId: product.id },
      )
    },
    [appendMessagesToActiveThread, preferences],
  )

  useEffect(() => {
    if (!productDetailChatRequest) {
      return
    }
    const requestProducts = displayProducts.some(
      (candidate) => candidate.id === productDetailChatRequest.product.id,
    )
      ? displayProducts
      : [productDetailChatRequest.product, ...displayProducts]
    appendProductDetailQuestion(
      productDetailChatRequest.product,
      productDetailChatRequest.question,
      requestProducts,
    )
    onProductDetailChatRequestHandled(productDetailChatRequest.id)
  }, [
    appendProductDetailQuestion,
    displayProducts,
    onProductDetailChatRequestHandled,
    productDetailChatRequest,
  ])

  const deleteMessage = (messageId: string) => {
    setMessages((current) => current.filter((message) => message.id !== messageId))
    if (activeSearchTarget?.messageId === messageId) {
      setActiveSearchTarget(null)
    }
  }

  const updateChatCartBlock = useCallback(
    (messageId: string, blockIndex: number, nextCart: readonly CartItem[]) => {
      updateThreadMessages(activeThreadIdSafe, (current) =>
        current.flatMap((message) => {
          if (message.id !== messageId) {
            return [message]
          }
          const blocks = [...(message.blocks ?? [])]
          const block = blocks[blockIndex]
          if (!block || block.type !== 'cart') {
            return [message]
          }
          if (nextCart.length === 0) {
            const nextBlocks = blocks.filter((_, index) => index !== blockIndex)
            const hasVisibleBlocks = nextBlocks.some(
              (nextBlock) => nextBlock.type !== 'text' && nextBlock.type !== 'system',
            )
            return hasVisibleBlocks ? [{ ...message, blocks: nextBlocks }] : []
          }
          blocks[blockIndex] = { ...block, lines: nextCart }
          return [{ ...message, blocks }]
        }),
      )
    },
    [activeThreadIdSafe, updateThreadMessages],
  )

  const updateChatCartQty = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    qty: number,
    nextCart: readonly CartItem[],
  ) => {
    onCartQty(id, merchant, qty)
    updateChatCartBlock(messageId, blockIndex, nextCart)
  }

  const removeChatCartLine = (
    messageId: string,
    blockIndex: number,
    id: ProductId,
    merchant: string,
    nextCart: readonly CartItem[],
  ) => {
    onCartRemove(id, merchant)
    updateChatCartBlock(messageId, blockIndex, nextCart)
  }

  const addMessageToShelf = (message: DiscoverChatMessage, sourceElement: HTMLElement) => {
    if (!shelfMessageSet.has(message.id)) {
      flyToShelf(sourceElement, message.role === 'you' ? 'var(--accent)' : 'var(--accent-tint)')
    }
    onShelfAddMessage({
      kind: 'message',
      messageId: message.id,
      snapshot: shelfMessageSnapshot(message),
    })
    onOpenShelf()
  }

  const addProductToShelf = (product: Product, sourceElement: HTMLElement) => {
    if (!shelfProductSet.has(product.id)) {
      flyToShelf(sourceElement, product.tone)
    }
    onShelfAddProduct(shelfProductSnapshot(product))
    onOpenShelf()
  }

  const dragMessage = (event: ReactDragEvent<HTMLElement>, message: DiscoverChatMessage) => {
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData(
      SHELF_DRAG_MIME,
      JSON.stringify({
        kind: 'message',
        messageId: message.id,
        snapshot: shelfMessageSnapshot(message),
      } satisfies ShelfDragPayload),
    )
    document.body.classList.add('mt-dragging')
    onOpenShelf()
  }

  const dragProduct = (event: ReactDragEvent<HTMLElement>, product: Product) => {
    event.stopPropagation()
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData(
      SHELF_DRAG_MIME,
      JSON.stringify({
        kind: 'product',
        snapshot: shelfProductSnapshot(product),
      } satisfies ShelfDragPayload),
    )
    document.body.classList.add('mt-dragging')
    onOpenShelf()
  }

  const runSearchInChat = (text: string) => {
    const aiId = nextDiscoverChatMessageId()
    appendMessagesToActiveThread(
      [
        { id: nextDiscoverChatMessageId(), role: 'you', text },
        {
          id: aiId,
          role: 'ai',
          query: text,
          pending: true,
          blocks: [{ type: 'text', text: 'Searching across supported merchants...' }],
        },
      ],
      { titleSeed: text },
    )
    setActiveSearchTarget({ threadId: activeThreadIdSafe, messageId: aiId })
    onSubmit(text)
  }

  const submit = (text: string) => {
    const normalized = text.trim()
    if (!normalized) {
      return
    }
    const lower = normalized.toLowerCase()
    if (/\border history\b|\borders?\b|\bpurchases?\b/.test(lower)) {
      appendMessagePair(normalized, [
        {
          type: 'text',
          text: 'Here are your recent orders. The full order view stays connected to the backend.',
        },
        { type: 'orders', orders },
      ])
      return
    }
    if (/\bsaved\b|\bshortlist\b|\bwishlist\b/.test(lower)) {
      appendMessagePair(normalized, [
        { type: 'text', text: 'Here are the products you saved.' },
        { type: 'saved', products: savedProducts },
      ])
      return
    }
    if (/\bpreferences?\b|\bfilters?\b|\bprofile\b/.test(lower)) {
      appendMessagePair(normalized, [
        { type: 'text', text: 'These are the preferences shaping every recommendation.' },
        { type: 'prefs', preferences },
      ])
      return
    }
    if (/\bcompare\b|\bversus\b|\bvs\b|\bbetter\b/.test(lower)) {
      const compareBlock = createMiniCompareBlock(
        pinnedProducts.length >= 2 ? pinnedProducts : displayProducts,
        deliveryLocations,
      )
      appendMessagePair(
        normalized,
        compareBlock
          ? [
              {
                type: 'text',
                text:
                  pinnedProducts.length >= 2
                    ? 'Here is the pinned comparison, without leaving the chat.'
                    : 'Here is a quick comparison from the current shortlist.',
              },
              compareBlock,
            ]
          : [
              {
                type: 'system',
                text: 'Pin at least two products or search for a shortlist before comparing.',
              },
            ],
      )
      return
    }
    if (/\bcart\b|\bbasket\b/.test(lower) && !/\badd\b/.test(lower)) {
      appendMessagePair(normalized, [
        {
          type: 'text',
          text: 'Here is your cart. You can adjust quantities, remove items, and start checkout right here.',
        },
        { type: 'cart', lines: cart },
      ])
      return
    }
    if (/\bcheckout\b|\bpay\b|\bbuy\b/.test(lower)) {
      appendMessagePair(normalized, [
        {
          type: 'text',
          text: 'Here is checkout inside the chat, grouped by merchant.',
        },
        {
          type: 'checkout',
          merchantCount: new Set(currentCartLines.map((line) => line.merchant)).size,
        },
      ])
      return
    }
    runSearchInChat(normalized)
  }

  const addCartFromChat = async (product: Product) => {
    const offer = bestOffer(product, deliveryLocations)
    const synced = offerCartable(offer)
    if (synced) {
      const added = await onAddProductToCart(product, offer)
      if (!added) {
        onFallbackAddToCart(product, offer)
      }
    } else {
      onFallbackAddToCart(product, offer)
    }
    appendMessagesToActiveThread(
      [
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [
            {
              type: 'added',
              product,
              merchant: offer.merchant,
              synced,
              price: offer.price,
              count: cart.reduce((sum, item) => sum + item.qty, 0) + 1,
            },
          ],
        },
      ],
      { focusProductId: product.id },
    )
  }

  const restoreCartLineFromReview = async (product: Product, merchant: string, price?: number) => {
    if (cart.some((item) => item.id === product.id && item.merchant === merchant)) {
      return
    }
    const merchantOffer = product.offers.find((offer) => offer.merchant === merchant)
    if (merchantOffer && offerCartable(merchantOffer)) {
      const added = await onAddProductToCart(product, merchantOffer)
      if (added) {
        return
      }
      onFallbackAddToCart(product, merchantOffer)
      return
    }
    onFallbackAddToCart(
      product,
      merchantOffer ?? {
        merchant,
        price: price ?? productPriceFrom(product, deliveryLocations),
        delivery: 'Available from merchant',
        available: true,
      },
    )
  }

  const digIntoProduct = (kind: 'reviews' | 'code' | 'similar' | 'resale', product: Product) => {
    if (kind === 'reviews') {
      appendMessagePair(`What do reviewers say about ${product.name}?`, [
        {
          type: 'text',
          text: `Here is what I can tell from the current product data for ${product.name}.`,
        },
        { type: 'reviews', product },
      ])
      return
    }
    if (kind === 'code') {
      const discount = chatDiscountForProduct(product)
      appendMessagePair(`Find a code for ${product.name}.`, [
        {
          type: 'text',
          text: 'Coupon hunting is mocked for now, but the block shape is ready for a backend agent.',
        },
        { type: 'code', product, code: discount.code, saved: discount.saved },
      ])
      return
    }
    if (kind === 'similar') {
      appendMessagePair(`Show me similar options to ${product.name}.`, [
        { type: 'text', text: 'Closest matches from the products already loaded in this session.' },
        { type: 'similar', product, products: similarChatProducts(product, displayProducts) },
      ])
      return
    }
    appendMessagePair(`Can I find ${product.name} second-hand?`, [
      {
        type: 'system',
        text: 'Second-hand and resale lookup is mocked until backend marketplace search exists.',
      },
    ])
  }

  const chooseOne = (candidates: readonly Product[]) => {
    const ranked = [...candidates].sort((left, right) => right.match - left.match)
    const pick = ranked[0]
    if (!pick) {
      return
    }
    appendMessagePair('Just pick one for me.', [
      { type: 'text', text: 'Done. I would buy this one.' },
      { type: 'decision', product: pick, runnerUp: ranked[1] ?? null },
    ])
  }

  const compareHere = (candidates: readonly Product[]) => {
    const compareBlock = createMiniCompareBlock(candidates, deliveryLocations)
    if (!compareBlock) {
      return
    }
    appendMessagePair('Compare these here.', [
      { type: 'text', text: 'I lined them up here so you can decide without leaving the chat.' },
      compareBlock,
    ])
    scrollChatToBottom()
  }

  const showCheckoutHere = () => {
    appendMessagePair('Checkout here.', [
      { type: 'text', text: 'I grouped checkout by merchant and kept it inside the chat.' },
      {
        type: 'checkout',
        merchantCount: new Set(currentCartLines.map((line) => line.merchant)).size,
      },
    ])
  }

  const showCartReviewHere = (
    fallbackLines: readonly CartItem[] = [],
    fallbackProducts: readonly Product[] = [],
  ) => {
    const reviewLines = cartItemsWithFallback(cart, fallbackLines)
    appendMessagesToActiveThread([
      {
        id: nextDiscoverChatMessageId(),
        role: 'ai',
        blocks: [
          {
            type: 'text',
            text: 'Here is the live cart review. You can adjust quantities, remove items, or continue to checkout here.',
          },
          { type: 'cart', lines: reviewLines, products: fallbackProducts },
        ],
      },
    ])
    scrollChatToBottom()
  }

  const togglePin = (product: Product) => {
    const wasPinned = pinnedIds.includes(product.id)
    const nextIds = wasPinned
      ? pinnedIds.filter((id) => id !== product.id)
      : [...pinnedIds, product.id].slice(-4)
    setPinnedIds(nextIds)
  }

  const toggleWatch = (product: Product) => {
    const watching = watchedIds.includes(product.id)
    setWatchedIds((current) =>
      watching ? current.filter((id) => id !== product.id) : [...current, product.id],
    )
    if (!watching) {
      appendMessagesToActiveThread(
        [
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: [
              {
                type: 'system',
                text: `Watching ${product.name}. A mocked price-drop alert will land in this chat.`,
              },
            ],
          },
        ],
        { focusProductId: product.id },
      )
      const watchThreadId = activeThreadIdSafe
      window.setTimeout(() => {
        const offer = bestOffer(product, deliveryLocations)
        updateThreadMessages(watchThreadId, (current) => [
          ...current,
          {
            id: nextDiscoverChatMessageId(),
            role: 'ai',
            blocks: [
              {
                type: 'watch',
                product,
                merchant: offer.merchant,
                price: Math.max(1, Math.round(offer.price * 0.9 * 100) / 100),
              },
            ],
          },
        ])
      }, 5000)
    }
  }

  const newThread = () => {
    const nextThread = createDiscoverChatThread()
    setThreads((current) => [...current, nextThread])
    setActiveThreadId(nextThread.id)
    setActiveSearchTarget(null)
    onClear()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const closeThread = (threadId: string) => {
    const closingIndex = threads.findIndex((thread) => thread.id === threadId)
    const nextThreads = threads.filter((thread) => thread.id !== threadId)
    if (nextThreads.length === 0) {
      const nextThread = createDiscoverChatThread()
      setThreads([nextThread])
      setActiveThreadId(nextThread.id)
      setActiveSearchTarget(null)
      onClear()
      return
    }
    setThreads(nextThreads)
    if (threadId === activeThreadIdSafe) {
      const nextActive = nextThreads[Math.max(0, closingIndex - 1)] ?? nextThreads[0]
      setActiveThreadId(nextActive.id)
      setActiveSearchTarget((currentTarget) =>
        currentTarget?.threadId === threadId ? null : currentTarget,
      )
    }
  }

  const renameThread = (threadId: string, title: string) => {
    const now = Date.now()
    setThreads((current) =>
      current.map((thread) =>
        thread.id === threadId ? { ...thread, title, named: true, updatedAt: now } : thread,
      ),
    )
  }

  const reorderThreads = (fromIndex: number, toIndex: number) => {
    setThreads((current) => {
      const next = [...current]
      const [moved] = next.splice(fromIndex, 1)
      if (!moved) {
        return current
      }
      next.splice(toIndex, 0, moved)
      return next
    })
  }

  const shareThread = (person: string) => {
    setShareOpen(false)
    const sharedThreadId = activeThreadIdSafe
    const sharedProduct = discoverThreadFocusProduct(activeThread, knownProductsById)
    appendMessagesToActiveThread([
      {
        id: nextDiscoverChatMessageId(),
        role: 'ai',
        blocks: [
          {
            type: 'system',
            text: `Shared this chat with ${person}. Their vote will land right here.`,
          },
        ],
      },
    ])
    if (!sharedProduct) {
      return
    }
    const notes = [
      'This one. The reviews sold me - go for it.',
      'Yes, get it. Looks exactly like your style.',
      'Do it - best value of the bunch, honestly.',
    ]
    window.setTimeout(() => {
      updateThreadMessages(sharedThreadId, (current) => [
        ...current,
        {
          id: nextDiscoverChatMessageId(),
          role: 'ai',
          blocks: [
            {
              type: 'friendvote',
              person,
              product: sharedProduct,
              vote: 'up',
              note: notes[Math.floor(Math.random() * notes.length)] ?? notes[0],
            },
          ],
        },
      ])
    }, 4200)
  }

  const messageBlockProps = {
    deliveryLocations,
    preferences,
    savedSet,
    savePendingSet,
    cart,
    cartProducts,
    pinnedSet,
    watchedSet,
    shelfMessageSet,
    shelfProductSet,
    onOpen,
    onToggleSave,
    onAddCart: (product: Product) => void addCartFromChat(product),
    onPin: togglePin,
    onWatch: toggleWatch,
    onDig: digIntoProduct,
    onJustPick: chooseOne,
    onCompareHere: compareHere,
    onOpenFullCompare: onCompareProducts,
    onOpenSaved,
    onOpenOrders,
    onOpenPrefs,
    onOpenCart,
    onReviewCartHere: showCartReviewHere,
    onRestoreCartLine: (product: Product, merchant: string, price?: number) =>
      void restoreCartLineFromReview(product, merchant, price),
    onCartQty: updateChatCartQty,
    onCartRemove: removeChatCartLine,
    onCheckout,
    onCheckoutHere: showCheckoutHere,
    onDelete: deleteMessage,
    onShelfAddMessage: addMessageToShelf,
    onShelfAddProduct: addProductToShelf,
    onDragMessage: dragMessage,
    onDragProduct: dragProduct,
  }

  const empty = messages.length === 0 && !query
  const activeThreadSearchPending = activeSearchTarget?.threadId === activeThreadIdSafe

  if (empty && threads.length === 1) {
    return (
      <main className="mt-feed mt-ct-feed mt-ct-feed-hero">
        <ChatHero
          profile={profile}
          greeting={greeting}
          prompts={prompts}
          onSubmit={submit}
          loading={loading}
          merchants={merchants}
          selectedMerchant={selectedMerchant}
          merchantCounts={merchantCounts}
          totalProductCount={totalProductCount}
          merchantsLoading={merchantsLoading}
          merchantsError={null}
          onMerchant={onMerchant}
        />
      </main>
    )
  }

  return (
    <main className="mt-feed mt-ct-feed">
      <DiscoverThreadTabs
        threads={threads}
        activeId={activeThreadIdSafe}
        onSelect={setActiveThreadId}
        onClose={closeThread}
        onNew={newThread}
        onRename={renameThread}
        onShare={() => setShareOpen(true)}
        onReorder={reorderThreads}
      />
      <div className="mt-ct-thread">
        <div className="mt-ct-msg mt-ct-meant mt-ct-greeting">
          <span className="mt-ct-av">
            <SparkMark size={13} />
          </span>
          <div className="mt-ct-meant-body">
            <p className="mt-ct-intro">
              I only surface products that fit your profile. I can also open your live cart, orders,
              saved items, and preferences right here.
            </p>
          </div>
        </div>
        {empty ? (
          <div className="mt-ct-empty-prompts">
            {prompts.map((prompt) => (
              <button
                key={prompt}
                className="mt-ct-suggchip"
                type="button"
                onClick={() => submit(prompt)}
              >
                {prompt}
              </button>
            ))}
            <button
              className="mt-ct-suggchip"
              type="button"
              onClick={() => chooseOne(displayProducts)}
            >
              <SparkMark size={11} />
              Just pick for me
            </button>
          </div>
        ) : null}
        {messages.map((message) => (
          <DiscoverChatMessageRow
            key={message.id}
            message={message}
            flash={shelfFlashMessageId === message.id}
            {...messageBlockProps}
          />
        ))}
        {error && !activeThreadSearchPending ? (
          <div className="mt-ct-system">
            Live search is unavailable, so Meant is showing demo products for this chat.
          </div>
        ) : null}
        {agentActivities.length > 0 && loading ? (
          <AgentActivityPanel activities={agentActivities} />
        ) : null}
        {hasMore ? (
          <div className="mt-load-more">
            <button
              className="mt-load-more-btn"
              type="button"
              onClick={onLoadMore}
              disabled={loadingMore}
            >
              {loadingMore ? 'Loading more' : 'Load more results'}
            </button>
          </div>
        ) : null}
        {deliveryLocations.length > 0 && hiddenByShip > 0 ? (
          <div className="mt-ship-strip">
            <span>
              Shipping to <strong>{deliveryLocationSummary(deliveryLocations)}</strong>
            </span>
            <span className="mt-ship-strip-hidden mt-mono">
              {hiddenByShip} hidden · cannot reach you
            </span>
          </div>
        ) : null}
        <div ref={chatBottomRef} className="mt-ct-bottom-sentinel" aria-hidden="true" />
      </div>
      {shareOpen ? (
        <DiscoverShareSheet
          thread={activeThread}
          onClose={() => setShareOpen(false)}
          onSend={shareThread}
        />
      ) : null}

      {pinnedProducts.length > 0 ? (
        <DustingContainer
          className="mt-ct-tray"
          dusting={trayClearing}
          onGone={() => {
            setTrayClearing(false)
            setPinnedIds([])
          }}
        >
          <span className="mt-mono mt-ct-tray-label">
            Compare tray
            <br />
            <span className="mt-ct-tray-note">chat picks</span>
          </span>
          <div className="mt-ct-tray-items">
            {pinnedProducts.map((product) => (
              <span className="mt-ct-tray-chip" key={product.id}>
                <span className="mt-ct-tray-thumb">
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                </span>
                {product.name}
                <button
                  className="mt-ct-tray-x"
                  type="button"
                  aria-label={`Remove ${product.name}`}
                  onClick={() =>
                    setPinnedIds((current) => current.filter((id) => id !== product.id))
                  }
                >
                  <CloseIcon size={10} />
                </button>
              </span>
            ))}
            {pinnedProducts.length < 2 ? (
              <span className="mt-ct-tray-hint">Pin one more product to compare.</span>
            ) : null}
          </div>
          <button
            className="mt-ct-tray-mini"
            type="button"
            disabled={pinnedProducts.length < 2 || trayClearing}
            onClick={() => compareHere(pinnedProducts)}
          >
            Compare here
          </button>
          <button
            className="mt-ct-tray-go"
            type="button"
            disabled={pinnedProducts.length < 2 || trayClearing}
            onClick={() => onCompareProducts(pinnedProducts)}
          >
            Full compare
          </button>
          <button
            className="mt-ct-tray-clear"
            type="button"
            onClick={() => setTrayClearing(true)}
            disabled={trayClearing}
          >
            Clear
          </button>
        </DustingContainer>
      ) : null}

      <div className="mt-ct-dock">
        <div className="mt-ct-dock-inner">
          <AskComposer
            placeholder="Ask, compare, show cart, or paste a product idea..."
            suggestions={[]}
            showChips={false}
            onAsk={submit}
            disabled={loading}
          />
        </div>
      </div>
    </main>
  )
}

function ProductModal({
  product,
  deliveryLocations,
  preferences,
  saved,
  savePending,
  inCompare,
  onClose,
  onToggleSave,
  onCompare,
  onAddToCart,
  onAskInChat,
  canPrev,
  canNext,
  onPrev,
  onNext,
}: Readonly<{
  product: Product | null
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
  saved: boolean
  savePending: boolean
  inCompare: boolean
  onClose: () => void
  onToggleSave: (product: Product) => void
  onCompare: (product: Product) => void
  onAddToCart: (product: Product, offer: Offer) => Promise<boolean> | boolean
  onAskInChat?: (product: Product, question: string) => void
  canPrev: boolean
  canNext: boolean
  onPrev: () => void
  onNext: () => void
}>) {
  const [messages, setMessages] = useState<Message[]>([])
  const [added, setAdded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)
  const [selectedMediaUrl, setSelectedMediaUrl] = useState<string | null>(null)
  const [thumbnailPage, setThumbnailPage] = useState(0)
  const [zoomImageUrl, setZoomImageUrl] = useState<string | null>(null)
  const [merchantDetails, setMerchantDetails] = useState<MerchantProductDetailsProfile | null>(null)
  const [detailLoadState, setDetailLoadState] = useState<ProductDetailLoadState>('idle')
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null)
  const addedTimeoutRef = useRef<number | null>(null)
  const addSelectedOfferRef = useRef<(() => Promise<void>) | null>(null)
  const touchStartRef = useRef<{ x: number; y: number } | null>(null)
  const modalDockRef = useRef<HTMLDivElement | null>(null)
  const deliveryCountryCode = deliveryLocations[0]?.code ?? null

  useEffect(() => {
    setMessages([])
    setAdded(false)
    setAdding(false)
    setAddError(null)
    setSelectedMediaUrl(null)
    setThumbnailPage(0)
    setZoomImageUrl(null)
    setMerchantDetails(null)
    setDetailLoadState('idle')
    setDetailLoadError(null)
  }, [product?.id])

  useEffect(() => {
    if (!product?.remote || !product.merchantId || !product.merchantProductId) {
      return
    }
    const controller = new AbortController()
    setDetailLoadState('loading')
    setDetailLoadError(null)
    const language =
      typeof window === 'undefined' ? null : window.navigator.language.split('-')[0] || null
    getMerchantProductDetails({
      merchantId: product.merchantId,
      productId: product.merchantProductId,
      addressCountry: deliveryCountryCode,
      language,
      signal: controller.signal,
    })
      .then((details) => {
        setMerchantDetails(details)
        setDetailLoadState('loaded')
      })
      .catch(() => {
        if (controller.signal.aborted) {
          return
        }
        setMerchantDetails(null)
        setDetailLoadState('error')
        setDetailLoadError('Latest product details are unavailable right now.')
      })
    return () => controller.abort()
  }, [
    deliveryCountryCode,
    product?.id,
    product?.merchantId,
    product?.merchantProductId,
    product?.remote,
  ])

  useEffect(
    () => () => {
      if (addedTimeoutRef.current !== null) {
        window.clearTimeout(addedTimeoutRef.current)
      }
    },
    [],
  )

  useEffect(() => {
    if (!product) {
      return
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const typing =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.isContentEditable === true
      if (zoomImageUrl) {
        if (event.key === 'Escape') {
          event.preventDefault()
          setZoomImageUrl(null)
        }
        return
      }
      if (event.key === 'Escape') {
        onClose()
        return
      }
      if (typing) {
        return
      }
      if (event.key === 'ArrowRight' && canNext) {
        event.preventDefault()
        onNext()
        return
      }
      if (event.key === 'ArrowLeft' && canPrev) {
        event.preventDefault()
        onPrev()
        return
      }
      if (event.key === 'Enter' && addSelectedOfferRef.current) {
        event.preventDefault()
        void addSelectedOfferRef.current()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, product, canNext, canPrev, onNext, onPrev, zoomImageUrl])

  if (!product) {
    return null
  }

  const offers = availableOffers(product, deliveryLocations)
  const visibleOffers = offers.length > 0 ? offers : product.offers
  const modalMedia = mergeProductMedia(product, merchantDetails)
  const thumbnailPageCount = Math.ceil(modalMedia.length / MODAL_THUMBNAIL_PAGE_SIZE)
  const boundedThumbnailPage = Math.min(thumbnailPage, Math.max(thumbnailPageCount - 1, 0))
  const thumbnailStart = boundedThumbnailPage * MODAL_THUMBNAIL_PAGE_SIZE
  const visibleModalMedia = modalMedia.slice(
    thumbnailStart,
    thumbnailStart + MODAL_THUMBNAIL_PAGE_SIZE,
  )
  const hasMediaPages = thumbnailPageCount > 1
  const selectedMedia = selectedMediaUrl
    ? modalMedia.find((item) => item.url === selectedMediaUrl)
    : null
  const selectedImageUrl = selectedMedia?.type.toLowerCase() === 'image' ? selectedMedia.url : null
  const modalImageUrl =
    selectedImageUrl ??
    merchantDetails?.selectedVariantImageUrl ??
    merchantDetails?.imageUrl ??
    product.imageUrl
  const curatorTake = productCuratedTake(product, preferences)
  const curatorAdvantages = productCuratedAdvantages(product, preferences)
  const curatorTradeoffs = productCuratedTradeoffs(product, preferences)
  const hasPreferenceMatches = product.satisfies.length > 0 || product.misses.length > 0
  const detailDescription = stripHtml(
    merchantDetails?.description || product.detailDescription || '',
  )
  const detailOptions = merchantDetails
    ? productOptionsFromProfiles(merchantDetails.options)
    : [...(product.detailOptions ?? [])]
  const selectedOptions = merchantDetails
    ? productSelectedOptionsFromProfiles(merchantDetails.selectedOptions)
    : [...(product.selectedOptions ?? [])]
  const hasProductDetails =
    detailLoadState === 'loading' ||
    Boolean(detailDescription) ||
    detailOptions.length > 0 ||
    selectedOptions.length > 0 ||
    Boolean(merchantDetails?.totalVariants) ||
    detailLoadState === 'error'
  const showProductDetailLoading =
    detailLoadState === 'loading' &&
    !detailDescription &&
    detailOptions.length === 0 &&
    selectedOptions.length === 0
  const reviewInsight =
    product.review.count > 0
      ? product.review.insight || 'Rating data is available; no review-summary agent has run yet.'
      : 'No review data available from this catalog result.'
  const showThumbnailPage = (nextPage: number) => {
    const page = Math.max(0, Math.min(nextPage, thumbnailPageCount - 1))
    const pageMedia = modalMedia.slice(
      page * MODAL_THUMBNAIL_PAGE_SIZE,
      page * MODAL_THUMBNAIL_PAGE_SIZE + MODAL_THUMBNAIL_PAGE_SIZE,
    )
    const firstImage = pageMedia.find((item) => item.type.toLowerCase() === 'image')
    setThumbnailPage(page)
    if (firstImage) {
      setSelectedMediaUrl(firstImage.url)
    }
  }
  const ask = (question: string) => {
    if (onAskInChat) {
      const sourceElement =
        modalDockRef.current?.querySelector<HTMLElement>('.mt-ask-bar') ?? modalDockRef.current
      flyMessageToChat(sourceElement, question)
      onAskInChat(product, question)
      return
    }
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: resolveAsk(question, product, preferences) },
    ])
  }
  const selectedOffer = bestOffer(product, deliveryLocations)
  const canAddToCart = offerCartable(selectedOffer)
  const addDisabled = adding || !canAddToCart
  const addButtonLabel = added
    ? 'Added to cart'
    : adding
      ? 'Adding...'
      : selectedOffer.available === false
        ? 'Unavailable'
        : canAddToCart
          ? 'Add to cart'
          : 'Checkout unavailable'
  const addSelectedOffer = async () => {
    if (!canAddToCart || adding) {
      return
    }
    setAdding(true)
    setAddError(null)
    try {
      const addedToCart = await onAddToCart(product, selectedOffer)
      if (!addedToCart) {
        setAddError('Could not add this offer to the merchant cart.')
        return
      }
      setAdded(true)
      if (addedTimeoutRef.current !== null) {
        window.clearTimeout(addedTimeoutRef.current)
      }
      addedTimeoutRef.current = window.setTimeout(() => {
        setAdded(false)
        addedTimeoutRef.current = null
      }, 1600)
    } catch {
      setAddError('Could not add this offer to the merchant cart.')
    } finally {
      setAdding(false)
    }
  }
  addSelectedOfferRef.current = addDisabled ? null : addSelectedOffer

  const onTouchStart = (event: ReactTouchEvent) => {
    const touch = event.touches[0]
    touchStartRef.current = touch ? { x: touch.clientX, y: touch.clientY } : null
  }
  const onTouchEnd = (event: ReactTouchEvent) => {
    const start = touchStartRef.current
    touchStartRef.current = null
    if (!start) {
      return
    }
    const touch = event.changedTouches[0]
    if (!touch) {
      return
    }
    const dx = touch.clientX - start.x
    const dy = touch.clientY - start.y
    // Horizontal swipe only: needs enough travel and must dominate vertical movement,
    // so vertical scrolls inside the modal don't trigger navigation.
    if (Math.abs(dx) < 60 || Math.abs(dx) < Math.abs(dy) * 1.5) {
      return
    }
    if (dx < 0 && canNext) {
      onNext()
    } else if (dx > 0 && canPrev) {
      onPrev()
    }
  }

  return (
    <div className="mt-modal-root open">
      <button
        className="mt-modal-scrim"
        type="button"
        aria-label="Close product detail"
        onClick={onClose}
      />
      {canPrev || canNext ? (
        <>
          <button
            className="mt-modal-nav mt-modal-nav-prev"
            type="button"
            onClick={onPrev}
            disabled={!canPrev}
            aria-label="Previous product"
          >
            <ChevronIcon direction="left" />
            <span className="mt-modal-nav-text mt-mono">Prev</span>
          </button>
          <button
            className="mt-modal-nav mt-modal-nav-next"
            type="button"
            onClick={onNext}
            disabled={!canNext}
            aria-label="Next product"
          >
            <span className="mt-modal-nav-text mt-mono">Next</span>
            <ChevronIcon direction="right" />
          </button>
        </>
      ) : null}
      <div
        className="mt-modal"
        role="dialog"
        aria-modal="true"
        aria-label={product.name}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <button className="mt-modal-close" type="button" onClick={onClose} aria-label="Close">
          <CloseIcon />
        </button>
        <div className="mt-modal-body" key={product.id}>
          <div className="mt-modal-left">
            <button
              className={`mt-modal-media ${modalImageUrl ? 'mt-modal-media-open' : ''}`}
              type="button"
              disabled={!modalImageUrl}
              aria-label={modalImageUrl ? `Enlarge photo of ${product.name}` : undefined}
              onClick={() => {
                if (modalImageUrl) {
                  setZoomImageUrl(modalImageUrl)
                }
              }}
            >
              <ProductArtwork
                product={product}
                label={`${product.category.toLowerCase()} shot`}
                imageUrl={modalImageUrl}
              />
              <div className="mt-modal-ring">
                <MatchRing value={product.match} size={56} stroke={4} />
              </div>
            </button>
            {modalMedia.length > 1 ? (
              <div className={`mt-modal-thumbs-wrap ${hasMediaPages ? 'paged' : ''}`}>
                {hasMediaPages ? (
                  <button
                    className="mt-modal-thumb-page"
                    type="button"
                    disabled={boundedThumbnailPage === 0}
                    aria-label={`Previous images for ${product.name}`}
                    onClick={() => showThumbnailPage(boundedThumbnailPage - 1)}
                  >
                    <ChevronIcon direction="left" size={16} />
                  </button>
                ) : null}
                <div className="mt-modal-thumbs">
                  {visibleModalMedia.map((item, index) => {
                    const isImage = item.type.toLowerCase() === 'image'
                    const isSelected = isImage && item.url === modalImageUrl
                    const imageIndex = thumbnailStart + index + 1
                    return (
                      <button
                        className={`mt-modal-thumb ${isSelected ? 'active' : ''}`}
                        key={`${item.type}-${item.url}`}
                        type="button"
                        disabled={!isImage}
                        aria-label={
                          isImage
                            ? `Show image ${imageIndex} for ${product.name}`
                            : `${item.type} media`
                        }
                        aria-pressed={isImage ? isSelected : undefined}
                        onClick={() => setSelectedMediaUrl(item.url)}
                      >
                        {isImage ? (
                          <img src={item.url} alt={item.altText || product.name} loading="lazy" />
                        ) : (
                          <span className="mt-mono">{item.type}</span>
                        )}
                      </button>
                    )
                  })}
                </div>
                {hasMediaPages ? (
                  <button
                    className="mt-modal-thumb-page"
                    type="button"
                    disabled={boundedThumbnailPage >= thumbnailPageCount - 1}
                    aria-label={`Next images for ${product.name}`}
                    onClick={() => showThumbnailPage(boundedThumbnailPage + 1)}
                  >
                    <ChevronIcon direction="right" size={16} />
                  </button>
                ) : null}
              </div>
            ) : null}
            <div className="mt-mono mt-card-brand">
              {product.brand} · {product.category}
            </div>
            <h2 className="mt-modal-name">{product.name}</h2>
            <div className="mt-modal-price-row">
              <ProductPriceLine
                product={product}
                deliveryLocations={deliveryLocations}
                className="mt-modal-price"
              />
              <span className="mt-mono mt-modal-stores">
                · {productMerchantCount(product, deliveryLocations)} stores
              </span>
            </div>
            <InventorySignalBadge product={product} />
            <div className="mt-modal-actions">
              <button
                className={`mt-act mt-act-icon ${saved ? 'on' : ''}`}
                type="button"
                onClick={() => onToggleSave(product)}
                disabled={savePending}
                aria-label={savePending ? 'Saving saved product' : saved ? 'Saved' : 'Save'}
              >
                <HeartIcon filled={saved} />
              </button>
              <button
                className={`mt-act mt-act-ghost ${inCompare ? 'on' : ''}`}
                type="button"
                onClick={() => onCompare(product)}
              >
                {inCompare ? 'In compare' : 'Add to compare'}
              </button>
              <button
                className={`mt-act mt-act-primary ${added ? 'done' : ''}`}
                type="button"
                onClick={() => void addSelectedOffer()}
                disabled={addDisabled}
              >
                <span>{addButtonLabel}</span>
                {!addDisabled && !added ? (
                  <kbd className="mt-act-key" aria-hidden>
                    ↵
                  </kbd>
                ) : null}
              </button>
            </div>
            {addError ? <div className="mt-cart-inline-error">{addError}</div> : null}
            {!canAddToCart && !addError ? (
              <div className="mt-cart-inline-error muted">
                This offer is not available for merchant checkout.
              </div>
            ) : null}
          </div>

          <div className="mt-modal-right">
            <div className="mt-drawer-note">
              <span className="mt-note-key">Meant's take</span>
              {curatorTake}
            </div>

            {hasProductDetails ? (
              <section className="mt-block">
                <div className="mt-block-label mt-mono">About this product</div>
                {showProductDetailLoading ? (
                  <p className="mt-product-detail-muted mt-mono">
                    Loading latest product details...
                  </p>
                ) : null}
                {detailDescription ? (
                  <p className="mt-product-detail-description">{detailDescription}</p>
                ) : null}
                {selectedOptions.length > 0 ? (
                  <div className="mt-product-detail-facts">
                    {selectedOptions.map((option) => (
                      <span
                        className="mt-product-detail-fact"
                        key={`${option.name}-${option.value}`}
                      >
                        <span className="mt-mono">{option.name}</span>
                        {option.value}
                      </span>
                    ))}
                  </div>
                ) : null}
                {detailOptions.length > 0 ? (
                  <div className="mt-product-options">
                    {detailOptions.slice(0, 4).map((option) => (
                      <div className="mt-product-option" key={option.name}>
                        <span className="mt-mono">{option.name}</span>
                        <span>{option.values.slice(0, 8).join(', ')}</span>
                      </div>
                    ))}
                  </div>
                ) : null}
                {merchantDetails?.totalVariants ? (
                  <p className="mt-product-detail-muted mt-mono">
                    {merchantDetails.totalVariants.toLocaleString()} variants available
                  </p>
                ) : null}
                {detailLoadState === 'error' && !detailDescription ? (
                  <p className="mt-product-detail-muted mt-product-detail-error">
                    {detailLoadError || 'Latest product details are unavailable right now.'}
                  </p>
                ) : null}
              </section>
            ) : null}

            <section className="mt-block">
              <div className="mt-block-label mt-mono">Preference match</div>
              {hasPreferenceMatches ? (
                <div className="mt-chips">
                  {product.satisfies.map((id) => (
                    <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" />
                  ))}
                  {product.misses.map((id) => (
                    <PrefChip key={id} label={prefLabel(preferences, id)} variant="missed" />
                  ))}
                </div>
              ) : (
                <div className="mt-mono mt-pref-empty-inline">
                  No confirmed preference matches yet
                </div>
              )}
            </section>

            <section className="mt-block">
              <div className="mt-procon">
                <div>
                  <div className="mt-block-label mt-mono">Advantages</div>
                  <ul className="mt-list mt-list-pro">
                    {curatorAdvantages.map((pro) => (
                      <li key={pro}>{pro}</li>
                    ))}
                  </ul>
                </div>
                <div>
                  <div className="mt-block-label mt-mono">Trade-offs</div>
                  <ul className="mt-list mt-list-con">
                    {curatorTradeoffs.map((con) => (
                      <li key={con}>{con}</li>
                    ))}
                  </ul>
                </div>
              </div>
            </section>

            <section className="mt-block">
              <div className="mt-reviews-head">
                <div className="mt-block-label mt-mono">From the reviews</div>
                {product.review.count > 0 ? (
                  <div className="mt-reviews-score">
                    {product.review.score !== null ? (
                      <span className="mt-stars">
                        {'★'.repeat(Math.round(product.review.score))}
                      </span>
                    ) : null}
                    <span className="mt-mono">
                      {product.review.score !== null ? `${product.review.score.toFixed(1)} · ` : ''}
                      {product.review.count.toLocaleString()}
                      {product.review.score === null ? ' reviews' : ''}
                    </span>
                  </div>
                ) : (
                  <span className="mt-mono mt-reviews-empty">No review data</span>
                )}
              </div>
              <p className="mt-reviews-insight">{reviewInsight}</p>
            </section>

            <section className="mt-block">
              <div className="mt-block-label mt-mono">Available offers</div>
              <div className="mt-offers">
                {visibleOffers.map((offer, index) => (
                  <div className={`mt-offer ${index === 0 ? 'best' : ''}`} key={offer.merchant}>
                    <div className="mt-offer-merch">
                      {offer.merchant}
                      {index === 0 ? <span className="mt-mono mt-offer-tag">best</span> : null}
                    </div>
                    <div className="mt-offer-right">
                      <span className="mt-mono mt-offer-deliv">{offer.delivery}</span>
                      <span className="mt-offer-price">{money(offer.price)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          </div>
        </div>

        <div className="mt-modal-dock" ref={modalDockRef}>
          <AskThread messages={messages} />
          <AskComposer
            placeholder={`Ask Meant about ${product.name}...`}
            suggestions={[
              'Does this match my preferences?',
              'Is there a cheaper option?',
              'What do reviewers say?',
            ]}
            showChips={messages.length === 0}
            onAsk={ask}
          />
          {onAskInChat ? (
            <div className="mt-mono mt-modal-dock-hint">
              Your question moves into the chat, where Meant answers in full.
            </div>
          ) : null}
        </div>
      </div>
      {zoomImageUrl ? (
        <div
          className="mt-image-zoom"
          role="dialog"
          aria-modal="true"
          aria-label={`Larger photo of ${product.name}`}
        >
          <button
            className="mt-image-zoom-scrim"
            type="button"
            aria-label="Close enlarged photo"
            onClick={() => setZoomImageUrl(null)}
          />
          <div className="mt-image-zoom-panel">
            <img className="mt-image-zoom-img" src={zoomImageUrl} alt={product.name} />
            <button
              className="mt-image-zoom-close"
              type="button"
              aria-label="Close enlarged photo"
              onClick={() => setZoomImageUrl(null)}
            >
              <CloseIcon />
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function ProfileBar({
  preferences,
  deliveryLocations,
  clothingFit,
  onEdit,
}: Readonly<{
  preferences: readonly Preference[]
  deliveryLocations: readonly UserLocation[]
  clothingFit: ClothingFit
  onEdit: () => void
}>) {
  return (
    <div className="mt-profile">
      <span className="mt-mono mt-profile-key">Your profile</span>
      <button
        className={`mt-loc-chip ${deliveryLocations.length > 0 ? '' : 'empty'}`}
        type="button"
        onClick={onEdit}
      >
        <span aria-hidden>⌖</span>
        {deliveryLocationSummary(deliveryLocations)}
      </button>
      {clothingFit !== 'none' ? (
        <button className="mt-loc-chip" type="button" onClick={onEdit}>
          {clothingFitLabel(clothingFit)}
        </button>
      ) : null}
      <div className="mt-profile-chips">
        {preferences.length === 0 ? (
          <span className="mt-profile-empty mt-mono">No active preferences</span>
        ) : (
          preferences.slice(0, 6).map((preference) => (
            <span className="mt-pref-pill" key={preference.id}>
              {preference.label}
            </span>
          ))
        )}
      </div>
      <button className="mt-profile-edit mt-mono" type="button" onClick={onEdit}>
        Edit
      </button>
    </div>
  )
}

function TopBar({
  view,
  theme,
  user,
  savedCount,
  cart,
  products,
  cartSnapshots,
  cartPeek,
  accountMenu,
  onNav,
  onToggleTheme,
  onToggleCart,
  onToggleAccount,
  onCloseAccount,
  onRemoveFromCart,
  onSignOut,
}: Readonly<{
  view: View
  theme: Theme
  user: UserAccount
  savedCount: number
  cart: readonly CartItem[]
  products: readonly Product[]
  cartSnapshots: Readonly<Record<string, MerchantCartSnapshot>>
  cartPeek: boolean
  accountMenu: boolean
  onNav: (view: View) => void
  onToggleTheme: () => void
  onToggleCart: () => void
  onToggleAccount: () => void
  onCloseAccount: () => void
  onRemoveFromCart: (id: ProductId, merchant: string) => void
  onSignOut: () => void
}>) {
  // Count only items that resolve to a known product, so the badge can never
  // disagree with what the cart actually shows (e.g. a stale persisted cart).
  const cartCount = cartLines(cart, products).reduce((sum, line) => sum + line.qty, 0)
  const savedBump = useChangePulse(savedCount)
  const cartBump = useChangePulse(cartCount)

  return (
    <div className="mt-topbar">
      <button
        className="mt-brand"
        type="button"
        onClick={() => onNav('discover')}
        aria-label="Meant home"
      >
        <img className="mt-brand-logo" src="/assets/meant-logo.png" alt="Meant" />
      </button>
      <nav className="mt-nav" aria-label="Primary">
        {[
          ['discover', 'Discover'],
          ['saved', 'Saved'],
          ['compare', 'Compare'],
          ['inventory', 'Inventory'],
        ].map(([key, label]) => (
          <button
            key={key}
            className={`mt-nav-item ${view === key ? 'on' : ''}`}
            type="button"
            onClick={() => onNav(key as View)}
          >
            {label}
            {key === 'saved' && savedCount > 0 ? (
              <span className={`mt-mono mt-nav-count${savedBump ? ' bump' : ''}`}>
                {savedCount}
              </span>
            ) : null}
          </button>
        ))}
      </nav>
      <div className="mt-topbar-right">
        <button
          className="mt-icon-btn"
          type="button"
          aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
          onClick={onToggleTheme}
        >
          {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
        </button>
        <button
          className={`mt-icon-btn ${view === 'saved' ? 'on' : ''}`}
          type="button"
          aria-label="Saved"
          onClick={() => onNav('saved')}
        >
          <HeartIcon filled={view === 'saved'} />
        </button>
        <div className="mt-cart-anchor">
          <button
            className={`mt-icon-btn mt-cart-btn ${cartPeek || view === 'cart' ? 'on' : ''}`}
            type="button"
            aria-label="Cart"
            aria-expanded={cartPeek}
            onClick={onToggleCart}
          >
            <CartIcon />
            {cartCount > 0 ? (
              <span className={`mt-cart-badge mt-mono${cartBump ? ' bump' : ''}`}>{cartCount}</span>
            ) : null}
          </button>
          {cartPeek ? (
            <CartPopover
              cart={cart}
              products={products}
              cartSnapshots={cartSnapshots}
              onViewFull={() => onNav('cart')}
              onClose={onToggleCart}
              onRemove={onRemoveFromCart}
            />
          ) : null}
        </div>
        <div className="mt-avatar-anchor">
          <button
            className={`mt-avatar ${accountMenu || view === 'account' ? 'on' : ''}`}
            type="button"
            onClick={onToggleAccount}
            aria-label="Your account"
            aria-expanded={accountMenu}
          >
            <Avatar user={user} size={38} />
          </button>
          {accountMenu ? (
            <AccountMenu user={user} onNav={onNav} onClose={onCloseAccount} onSignOut={onSignOut} />
          ) : null}
        </div>
      </div>
    </div>
  )
}

function AccountMenu({
  user,
  onNav,
  onClose,
  onSignOut,
}: Readonly<{
  user: UserAccount
  onNav: (view: View) => void
  onClose: () => void
  onSignOut: () => void
}>) {
  const ref = useRef<HTMLDivElement | null>(null)
  const items: ReadonlyArray<{ key: View; label: string }> = [
    { key: 'account', label: 'Account settings' },
    { key: 'orders', label: 'Order history' },
    { key: 'inventory', label: 'Inventory' },
    { key: 'preferences', label: 'Your preferences' },
    { key: 'saved', label: 'Saved items' },
    { key: 'cart', label: 'Your cart' },
  ]

  useEffect(() => {
    const onDown = (event: MouseEvent) => {
      const target = event.target as Node
      const anchor = ref.current?.closest('.mt-avatar-anchor')
      if (anchor && anchor.contains(target)) {
        return
      }
      if (ref.current && !ref.current.contains(target)) {
        onClose()
      }
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  return (
    <div className="mt-acctmenu" role="menu" ref={ref}>
      <div className="mt-acctmenu-head">
        <Avatar user={user} size={42} />
        <div className="mt-acctmenu-id">
          <div className="mt-acctmenu-name">{user.name}</div>
          <div className="mt-acctmenu-mail mt-mono">{user.email}</div>
        </div>
      </div>
      <div className="mt-acctmenu-list">
        {items.map((item) => (
          <button
            key={item.key}
            className="mt-acctmenu-item"
            type="button"
            onClick={() => {
              onNav(item.key)
              onClose()
            }}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="mt-acctmenu-sep" />
      <button
        className="mt-acctmenu-item mt-acctmenu-signout"
        type="button"
        onClick={() => {
          onClose()
          onSignOut()
        }}
      >
        Sign out
      </button>
    </div>
  )
}

function SavedView({
  products,
  deliveryLocations,
  preferences,
  savedSet,
  savePendingSet,
  onOpen,
  onToggleSave,
}: Readonly<
  {
    products: readonly Product[]
    deliveryLocations: readonly UserLocation[]
    preferences: readonly Preference[]
  } & ProductOpenProps &
    ProductSaveProps
>) {
  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Your shortlist"
        title="Saved"
        sub={
          products.length > 0
            ? "Everything you've kept waits here, even after the search moves on."
            : undefined
        }
      />
      {products.length === 0 ? (
        <EmptyState
          title="Nothing saved yet"
          sub="Tap the heart on any product and it will wait for you here."
          mark={<HeartIcon filled={false} />}
        />
      ) : (
        <div className="mt-grid">
          {products.map((product, index) => (
            <ProductCard
              key={product.id}
              product={product}
              index={index}
              deliveryLocations={deliveryLocations}
              preferences={preferences}
              onOpen={onOpen}
              savedSet={savedSet}
              savePendingSet={savePendingSet}
              onToggleSave={onToggleSave}
            />
          ))}
        </div>
      )}
    </main>
  )
}

function InventoryView({
  items,
  loading,
  error,
  onRefresh,
  onAddItem,
  onAddPhotoItem,
  onUpdateItem,
  onDeleteItem,
  onExport,
}: Readonly<{
  items: readonly UserInventoryItemProfile[]
  loading: boolean
  error: string | null
  onRefresh: () => void
  onAddItem: (input: UserInventoryItemInput) => Promise<UserInventoryItemProfile>
  onAddPhotoItem: (input: UserInventoryPhotoInput) => Promise<UserInventoryItemProfile>
  onUpdateItem: (
    itemId: string,
    input: UserInventoryItemUpdateInput,
  ) => Promise<UserInventoryItemProfile>
  onDeleteItem: (itemId: string) => Promise<void>
  onExport: () => Promise<void>
}>) {
  const [categoryFilter, setCategoryFilter] = useState<UserInventoryCategory | 'ALL'>('ALL')
  const [restockOnly, setRestockOnly] = useState(false)
  const [manualForm, setManualForm] = useState<InventoryFormState>(() => initialInventoryForm())
  const [photoForm, setPhotoForm] = useState<InventoryFormState>(() =>
    initialInventoryForm('OTHER'),
  )
  const [saving, setSaving] = useState<'manual' | 'photo' | null>(null)
  const [photoProcessing, setPhotoProcessing] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const filteredItems = useMemo(
    () =>
      items.filter(
        (item) =>
          (categoryFilter === 'ALL' || item.category === categoryFilter) &&
          (!restockOnly || item.restockEnabled),
      ),
    [categoryFilter, items, restockOnly],
  )
  const pantryCount = items.filter((item) => item.category === 'PANTRY').length
  const restockCount = items.filter((item) => item.restockEnabled).length
  const purchasedCount = items.filter((item) => item.source === 'MEANT_PURCHASE').length

  const addManual = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!manualForm.name.trim() || saving) {
      return
    }
    setSaving('manual')
    setFormError(null)
    setNotice(null)
    try {
      await onAddItem(inventoryItemInputFromForm(manualForm))
      setManualForm(initialInventoryForm(manualForm.category))
      setNotice('Item added')
    } catch {
      setFormError('Could not add item')
    } finally {
      setSaving(null)
    }
  }

  const addPhoto = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!photoForm.photoUrl.trim() || saving || photoProcessing) {
      return
    }
    setSaving('photo')
    setFormError(null)
    setNotice(null)
    try {
      await onAddPhotoItem(inventoryPhotoInputFromForm(photoForm))
      setPhotoForm(initialInventoryForm('OTHER'))
      setNotice('Photo item added')
    } catch {
      setFormError('Could not add photo item')
    } finally {
      setSaving(null)
    }
  }

  const readPhoto = async (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget
    const file = input.files?.[0]
    if (!file) {
      return
    }
    setPhotoProcessing(true)
    setFormError(null)
    setNotice(null)
    try {
      const photoUrl = await fileToInventoryPhotoUrl(file)
      const fileName = file.name
        .replace(/\.[^.]+$/, '')
        .replace(/[-_]+/g, ' ')
        .trim()
      setPhotoForm((current) => ({
        ...current,
        photoUrl,
        name: current.name || fileName,
      }))
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Could not process photo')
    } finally {
      setPhotoProcessing(false)
      input.value = ''
    }
  }

  const exportItems = async () => {
    if (exporting) {
      return
    }
    setExporting(true)
    setFormError(null)
    setNotice(null)
    try {
      await onExport()
      setNotice('Inventory exported')
    } catch {
      setFormError('Could not export inventory')
    } finally {
      setExporting(false)
    }
  }

  return (
    <main className="mt-feed mt-view mt-inventory-view">
      <ViewHead
        eyebrow="Owned items"
        title="Inventory"
        right={
          <div className="mt-inv-head-actions">
            <button
              className="mt-empty-btn ghost"
              type="button"
              onClick={onRefresh}
              disabled={loading}
            >
              Refresh
            </button>
            <button
              className="mt-empty-btn"
              type="button"
              onClick={() => void exportItems()}
              disabled={exporting}
            >
              {exporting ? 'Exporting' : 'Export data'}
            </button>
          </div>
        }
      />

      <div className="mt-inv-stats">
        <InventoryStat label="Items" value={items.length} />
        <InventoryStat label="Restock" value={restockCount} />
        <InventoryStat label="Pantry" value={pantryCount} />
        <InventoryStat label="Purchases" value={purchasedCount} />
      </div>

      <div className="mt-inv-workbench">
        <InventoryManualFormPanel
          form={manualForm}
          pending={saving === 'manual'}
          onChange={setManualForm}
          onSubmit={addManual}
        />
        <InventoryPhotoFormPanel
          form={photoForm}
          pending={saving === 'photo'}
          processing={photoProcessing}
          onChange={setPhotoForm}
          onPhoto={readPhoto}
          onSubmit={addPhoto}
        />
      </div>

      {error ? <div className="mt-search-state mt-search-state-error">{error}</div> : null}
      {formError ? <div className="mt-search-state mt-search-state-error">{formError}</div> : null}
      {notice ? <div className="mt-inv-notice mt-mono">{notice}</div> : null}

      <div className="mt-inv-list-head">
        <div className="mt-inv-tabs" role="group" aria-label="Inventory category filter">
          <button
            className={`mt-inv-tab ${categoryFilter === 'ALL' ? 'on' : ''}`}
            type="button"
            onClick={() => setCategoryFilter('ALL')}
          >
            All
          </button>
          {INVENTORY_CATEGORIES.map((category) => (
            <button
              key={category}
              className={`mt-inv-tab ${categoryFilter === category ? 'on' : ''}`}
              type="button"
              onClick={() => setCategoryFilter(category)}
            >
              {inventoryCategoryLabel(category)}
            </button>
          ))}
        </div>
        <label className="mt-inv-check">
          <input
            type="checkbox"
            checked={restockOnly}
            onChange={(event) => setRestockOnly(event.target.checked)}
          />
          <span>Restock only</span>
        </label>
      </div>

      {loading && items.length === 0 ? (
        <ProductSearchLoading label="Loading inventory" />
      ) : filteredItems.length > 0 ? (
        <div className="mt-inv-list">
          {filteredItems.map((item) => (
            <InventoryItemCard
              key={item.id}
              item={item}
              onUpdate={onUpdateItem}
              onDelete={onDeleteItem}
            />
          ))}
        </div>
      ) : (
        <EmptyState
          title={items.length === 0 ? 'No owned items yet' : 'No items match this filter'}
          sub={
            items.length === 0
              ? 'Manual entries, photo adds, and Meant purchases will appear here.'
              : 'Change the category or restock filter.'
          }
          mark={<SparkMark />}
        />
      )}
    </main>
  )
}

function InventoryStat({ label, value }: Readonly<{ label: string; value: number }>) {
  return (
    <div className="mt-inv-stat">
      <span className="mt-mono mt-inv-stat-label">{label}</span>
      <span className="mt-inv-stat-value">{value}</span>
    </div>
  )
}

function InventoryManualFormPanel({
  form,
  pending,
  onChange,
  onSubmit,
}: Readonly<{
  form: InventoryFormState
  pending: boolean
  onChange: Dispatch<SetStateAction<InventoryFormState>>
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}>) {
  return (
    <form className="mt-inv-panel" onSubmit={onSubmit}>
      <div className="mt-inv-panel-head">
        <h2 className="mt-inv-panel-title">Manual add</h2>
        <button
          className="mt-act mt-act-primary"
          type="submit"
          disabled={!form.name.trim() || pending}
        >
          {pending ? 'Adding' : 'Add item'}
        </button>
      </div>
      <div className="mt-inv-form-grid">
        <InventoryTextField
          label="Name"
          value={form.name}
          onChange={(name) => onChange((current) => ({ ...current, name }))}
          required
        />
        <InventoryTextField
          label="Brand"
          value={form.brand}
          onChange={(brand) => onChange((current) => ({ ...current, brand }))}
        />
        <InventoryCategoryField
          value={form.category}
          onChange={(category) =>
            onChange((current) => ({
              ...current,
              category,
              consumable: category === 'PANTRY' ? true : current.consumable,
            }))
          }
        />
        <InventoryTextField
          label="Quantity"
          type="number"
          value={form.quantity}
          onChange={(quantity) => onChange((current) => ({ ...current, quantity }))}
          min="1"
        />
        <InventoryTextField
          label="Unit"
          value={form.unit}
          onChange={(unit) => onChange((current) => ({ ...current, unit }))}
        />
        <InventoryTextField
          label="Location"
          value={form.location}
          onChange={(location) => onChange((current) => ({ ...current, location }))}
        />
      </div>
      <InventoryTextField
        label="Image URL"
        value={form.imageUrl}
        onChange={(imageUrl) => onChange((current) => ({ ...current, imageUrl }))}
      />
      <InventoryTextField
        label="Product URL"
        value={form.productUrl}
        onChange={(productUrl) => onChange((current) => ({ ...current, productUrl }))}
      />
      <InventoryTextArea
        label="Attributes"
        value={form.attributes}
        onChange={(attributes) => onChange((current) => ({ ...current, attributes }))}
      />
      <InventoryTextArea
        label="Notes"
        value={form.notes}
        onChange={(notes) => onChange((current) => ({ ...current, notes }))}
      />
      <InventoryRestockFields form={form} onChange={onChange} />
    </form>
  )
}

function InventoryPhotoFormPanel({
  form,
  pending,
  processing,
  onChange,
  onPhoto,
  onSubmit,
}: Readonly<{
  form: InventoryFormState
  pending: boolean
  processing: boolean
  onChange: Dispatch<SetStateAction<InventoryFormState>>
  onPhoto: (event: ChangeEvent<HTMLInputElement>) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}>) {
  return (
    <form className="mt-inv-panel" onSubmit={onSubmit}>
      <div className="mt-inv-panel-head">
        <h2 className="mt-inv-panel-title">Photo add</h2>
        <button
          className="mt-act mt-act-primary"
          type="submit"
          disabled={!form.photoUrl.trim() || pending || processing}
        >
          {pending ? 'Adding' : 'Use photo'}
        </button>
      </div>
      <label className="mt-field">
        <span className="mt-field-label mt-mono">Photo</span>
        <input className="mt-file" type="file" accept="image/*" onChange={onPhoto} />
      </label>
      {form.photoUrl ? (
        <div className="mt-inv-photo-preview">
          <img src={form.photoUrl} alt="" />
        </div>
      ) : (
        <div className="mt-inv-photo-empty mt-mono">
          {processing ? 'Processing photo' : 'No photo selected'}
        </div>
      )}
      <div className="mt-inv-form-grid">
        <InventoryTextField
          label="Name"
          value={form.name}
          onChange={(name) => onChange((current) => ({ ...current, name }))}
        />
        <InventoryTextField
          label="Brand"
          value={form.brand}
          onChange={(brand) => onChange((current) => ({ ...current, brand }))}
        />
        <InventoryCategoryField
          value={form.category}
          onChange={(category) =>
            onChange((current) => ({
              ...current,
              category,
              consumable: category === 'PANTRY' ? true : current.consumable,
            }))
          }
        />
        <InventoryTextField
          label="Quantity"
          type="number"
          value={form.quantity}
          onChange={(quantity) => onChange((current) => ({ ...current, quantity }))}
          min="1"
        />
      </div>
      <InventoryTextArea
        label="Notes"
        value={form.notes}
        onChange={(notes) => onChange((current) => ({ ...current, notes }))}
      />
      <InventoryRestockFields form={form} onChange={onChange} />
    </form>
  )
}

function InventoryItemCard({
  item,
  onUpdate,
  onDelete,
}: Readonly<{
  item: UserInventoryItemProfile
  onUpdate: (
    itemId: string,
    input: UserInventoryItemUpdateInput,
  ) => Promise<UserInventoryItemProfile>
  onDelete: (itemId: string) => Promise<void>
}>) {
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<InventoryFormState>(() => inventoryFormFromItem(item))
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const image = inventoryItemImage(item)
  const purchasedAt = inventoryDateLabel(item.purchasedAt)
  const updatedAt = inventoryDateLabel(item.updatedAt)

  useEffect(() => {
    setForm(inventoryFormFromItem(item))
    setEditing(false)
    setConfirmDelete(false)
    setError(null)
  }, [item])

  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!form.name.trim() || saving) {
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onUpdate(item.id, inventoryUpdateInputFromForm(form))
      setEditing(false)
    } catch {
      setError('Could not update item')
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    if (!confirmDelete) {
      setConfirmDelete(true)
      return
    }
    setDeleting(true)
    setError(null)
    try {
      await onDelete(item.id)
    } catch {
      setError('Could not delete item')
      setDeleting(false)
      setConfirmDelete(false)
    }
  }

  return (
    <article className={`mt-inv-item ${editing ? 'editing' : ''}`}>
      <div className="mt-inv-media">
        {image ? (
          <img src={image} alt="" loading="lazy" />
        ) : (
          <Placeholder label={inventoryCategoryLabel(item.category)} tone="#e7ebef" />
        )}
      </div>
      <div className="mt-inv-main">
        <div className="mt-inv-item-top">
          <div>
            <div className="mt-mono mt-inv-source">
              {inventorySourceLabel(item.source)} · {inventoryCategoryLabel(item.category)}
            </div>
            <h3 className="mt-inv-name">{item.name}</h3>
            {item.brand ? <div className="mt-inv-brand">{item.brand}</div> : null}
          </div>
          <div className="mt-inv-actions">
            <button
              className="mt-act mt-act-ghost"
              type="button"
              onClick={() => setEditing((current) => !current)}
            >
              {editing ? 'Cancel' : 'Edit'}
            </button>
            <button
              className="mt-act mt-act-ghost danger"
              type="button"
              onClick={() => void remove()}
              disabled={deleting}
            >
              {confirmDelete ? 'Confirm delete' : deleting ? 'Deleting' : 'Delete'}
            </button>
          </div>
        </div>

        {editing ? (
          <form className="mt-inv-edit" onSubmit={save}>
            <div className="mt-inv-form-grid">
              <InventoryTextField
                label="Name"
                value={form.name}
                onChange={(name) => setForm((current) => ({ ...current, name }))}
                required
              />
              <InventoryTextField
                label="Brand"
                value={form.brand}
                onChange={(brand) => setForm((current) => ({ ...current, brand }))}
              />
              <InventoryCategoryField
                value={form.category}
                onChange={(category) =>
                  setForm((current) => ({
                    ...current,
                    category,
                    consumable: category === 'PANTRY' ? true : current.consumable,
                  }))
                }
              />
              <InventoryTextField
                label="Quantity"
                type="number"
                value={form.quantity}
                onChange={(quantity) => setForm((current) => ({ ...current, quantity }))}
                min="1"
              />
              <InventoryTextField
                label="Unit"
                value={form.unit}
                onChange={(unit) => setForm((current) => ({ ...current, unit }))}
              />
              <InventoryTextField
                label="Location"
                value={form.location}
                onChange={(location) => setForm((current) => ({ ...current, location }))}
              />
            </div>
            <InventoryTextArea
              label="Attributes"
              value={form.attributes}
              onChange={(attributes) => setForm((current) => ({ ...current, attributes }))}
            />
            <InventoryTextArea
              label="Notes"
              value={form.notes}
              onChange={(notes) => setForm((current) => ({ ...current, notes }))}
            />
            <InventoryRestockFields form={form} onChange={setForm} />
            <div className="mt-inv-save-row">
              <button
                className="mt-act mt-act-primary"
                type="submit"
                disabled={!form.name.trim() || saving}
              >
                {saving ? 'Saving' : 'Save changes'}
              </button>
            </div>
          </form>
        ) : (
          <>
            <div className="mt-inv-meta">
              <span>
                {item.quantity}
                {item.unit ? ` ${item.unit}` : ''}
              </span>
              {item.location ? <span>{item.location}</span> : null}
              {item.restockEnabled ? (
                <span>
                  Restock{item.restockThreshold !== null ? ` at ${item.restockThreshold}` : ''}
                </span>
              ) : null}
              {purchasedAt ? <span>Bought {purchasedAt}</span> : null}
              {updatedAt ? <span>Updated {updatedAt}</span> : null}
            </div>
            {item.attributes.length > 0 ? (
              <div className="mt-chips mt-inv-attrs">
                {item.attributes.map((attribute) => (
                  <PrefChip key={attribute} label={attribute} variant="muted" small />
                ))}
              </div>
            ) : null}
            {item.notes ? <p className="mt-inv-notes">{item.notes}</p> : null}
          </>
        )}
        {error ? <div className="mt-cart-inline-error">{error}</div> : null}
      </div>
    </article>
  )
}

function InventoryTextField({
  label,
  value,
  onChange,
  type = 'text',
  required = false,
  min,
}: Readonly<{
  label: string
  value: string
  onChange: (value: string) => void
  type?: 'text' | 'number' | 'url'
  required?: boolean
  min?: string
}>) {
  return (
    <label className="mt-field">
      <span className="mt-field-label mt-mono">{label}</span>
      <input
        className="mt-input"
        type={type}
        value={value}
        min={min}
        required={required}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

function InventoryTextArea({
  label,
  value,
  onChange,
}: Readonly<{
  label: string
  value: string
  onChange: (value: string) => void
}>) {
  return (
    <label className="mt-field">
      <span className="mt-field-label mt-mono">{label}</span>
      <textarea
        className="mt-input mt-textarea"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

function InventoryCategoryField({
  value,
  onChange,
}: Readonly<{
  value: UserInventoryCategory
  onChange: (value: UserInventoryCategory) => void
}>) {
  return (
    <label className="mt-field">
      <span className="mt-field-label mt-mono">Category</span>
      <select
        className="mt-select"
        value={value}
        onChange={(event) => onChange(event.target.value as UserInventoryCategory)}
      >
        {INVENTORY_CATEGORIES.map((category) => (
          <option key={category} value={category}>
            {inventoryCategoryLabel(category)}
          </option>
        ))}
      </select>
    </label>
  )
}

function InventoryRestockFields({
  form,
  onChange,
}: Readonly<{
  form: InventoryFormState
  onChange: Dispatch<SetStateAction<InventoryFormState>>
}>) {
  return (
    <div className="mt-inv-restock-controls">
      <label className="mt-inv-check">
        <input
          type="checkbox"
          checked={form.consumable}
          onChange={(event) =>
            onChange((current) => ({ ...current, consumable: event.target.checked }))
          }
        />
        <span>Consumable</span>
      </label>
      <label className="mt-inv-check">
        <input
          type="checkbox"
          checked={form.restockEnabled}
          onChange={(event) =>
            onChange((current) => ({
              ...current,
              restockEnabled: event.target.checked,
              consumable: event.target.checked ? true : current.consumable,
            }))
          }
        />
        <span>Restock</span>
      </label>
      <label className="mt-field mt-inv-threshold">
        <span className="mt-field-label mt-mono">Threshold</span>
        <input
          className="mt-input"
          type="number"
          min="0"
          value={form.restockThreshold}
          disabled={!form.restockEnabled}
          onChange={(event) =>
            onChange((current) => ({ ...current, restockThreshold: event.target.value }))
          }
        />
      </label>
    </div>
  )
}

function CompareView({
  products,
  savedProducts,
  compareIds,
  preferences,
  deliveryLocations,
  onRemove,
  onAdd,
  onOpen,
}: Readonly<{
  products: readonly Product[]
  savedProducts: readonly Product[]
  compareIds: readonly ProductId[]
  preferences: readonly Preference[]
  deliveryLocations: readonly UserLocation[]
  onRemove: (index: number) => void
  onAdd: (product: Product) => void
  onOpen: (product: Product, products: readonly Product[]) => void
}>) {
  const items = compareIds
    .map((id) => products.find((product) => product.id === id))
    .filter((product): product is Product => Boolean(product))
  const selectedIds = items.map((product) => product.id)
  const showAdd = items.length < 4
  const enough = items.length >= 2
  const compareColumnCount = items.length + (showAdd ? 1 : 0)
  const gridStyle = {
    gridTemplateColumns: `180px repeat(${compareColumnCount}, minmax(180px, 240px))`,
  }
  const bestMatch = enough ? Math.max(...items.map((product) => product.match)) : null
  const bestPrice = enough
    ? Math.min(...items.map((product) => productPriceFrom(product, deliveryLocations)))
    : null
  const bestMerchantCount = enough
    ? Math.max(...items.map((product) => productMerchantCount(product, deliveryLocations)))
    : null
  const winner = enough
    ? [...items].sort(
        (left, right) =>
          right.match - left.match ||
          productPriceFrom(left, deliveryLocations) - productPriceFrom(right, deliveryLocations),
      )[0]
    : null
  const comparisonPreferenceIds = preferences
    .map((preference) => preference.id)
    .filter((id) =>
      items.some((product) => product.satisfies.includes(id) || product.misses.includes(id)),
    )
  const meantTake = (product: Product) => productCuratedFields(product, preferences).note

  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Side by side"
        title="Compare"
        sub="Compare up to four products at once. Add products from detail pages or add saved products here, then Meant lines them up against everything you care about."
      />
      {winner ? (
        <div className="mt-cmp-verdict">
          <div>
            <div className="mt-mono mt-cmp-verdict-key">Meant pick</div>
            <div className="mt-cmp-verdict-title">{winner.name}</div>
          </div>
          <p>{meantTake(winner)}</p>
        </div>
      ) : null}
      <div className="mt-cmp">
        <div className="mt-cmp-grid mt-cmp-headrow" style={gridStyle}>
          <div className="mt-cmp-rowlabel mt-cmp-corner mt-mono">{items.length} of 4</div>
          {items.map((product, index) => (
            <CompareSlot
              key={product.id}
              index={index}
              product={product}
              canRemove
              onRemove={onRemove}
              onOpen={(item) => onOpen(item, items)}
            />
          ))}
          {showAdd ? (
            <CompareAddSlot products={savedProducts} selectedIds={selectedIds} onAdd={onAdd} />
          ) : null}
        </div>
        {enough ? (
          <>
            <CompareMetricRow
              label="Match"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: `${product.match}%`,
                win: bestMatch !== null && product.match === bestMatch,
              }))}
              addSpacer={showAdd}
            />
            <CompareMetricRow
              label="Price from"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: money(productPriceFrom(product, deliveryLocations)),
                win:
                  bestPrice !== null && productPriceFrom(product, deliveryLocations) === bestPrice,
              }))}
              addSpacer={showAdd}
            />
            <CompareMetricRow
              label="Stores"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: `${productMerchantCount(product, deliveryLocations)}`,
                win:
                  bestMerchantCount !== null &&
                  productMerchantCount(product, deliveryLocations) === bestMerchantCount,
              }))}
              addSpacer={showAdd}
            />
            <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Reviews</div>
              {items.map((product) => (
                <div key={product.id} className="mt-cmp-cell">
                  {product.review.count > 0 ? (
                    <>
                      {product.review.score !== null ? (
                        <span className="mt-stars">
                          {'★'.repeat(Math.round(product.review.score))}
                        </span>
                      ) : null}
                      <span className="mt-mono mt-cmp-sub">
                        {product.review.score !== null
                          ? `${product.review.score.toFixed(1)} · `
                          : ''}
                        {product.review.count.toLocaleString()}
                        {product.review.score === null ? ' reviews' : ''}
                      </span>
                    </>
                  ) : (
                    <span className="mt-mono mt-cmp-sub">No review data</span>
                  )}
                </div>
              ))}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
            <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Meant take</div>
              {items.map((product) => (
                <div key={product.id} className="mt-cmp-cell">
                  <span className="mt-cmp-text">{meantTake(product)}</span>
                </div>
              ))}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
            <div className="mt-cmp-grid mt-cmp-section" style={gridStyle}>
              <div className="mt-cmp-rowlabel mt-cmp-seclabel mt-mono">Your preferences</div>
              {items.map((product) => (
                <div key={product.id} />
              ))}
              {showAdd ? <div /> : null}
            </div>
            {comparisonPreferenceIds.map((id) => (
              <div className="mt-cmp-grid mt-cmp-row" key={id} style={gridStyle}>
                <div className="mt-cmp-rowlabel">{prefLabel(preferences, id)}</div>
                {items.map((product) => (
                  <div key={product.id} className="mt-cmp-cell">
                    <CompareMark product={product} preferenceId={id} />
                  </div>
                ))}
                {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
              </div>
            ))}
            <div className="mt-cmp-grid mt-cmp-row mt-cmp-last" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Best price at</div>
              {items.map((product) => {
                const offer = bestOffer(product, deliveryLocations)
                return (
                  <div key={product.id} className="mt-cmp-cell">
                    <span className="mt-cmp-store">{offer.merchant}</span>
                    <span className="mt-mono mt-cmp-sub">
                      {money(offer.price)} · {offer.delivery}
                    </span>
                  </div>
                )
              })}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
          </>
        ) : (
          <div className="mt-cmp-hint-row">
            Add at least two products to see them compared field by field.
          </div>
        )}
      </div>
      <p className="mt-cmp-footnote">
        Add products to Compare from a product detail page with Add to compare. Once a product is in
        compare, In compare opens this page. The Add a saved product control only lists products you
        have saved.
      </p>
    </main>
  )
}

function CompareMetricRow({
  label,
  gridStyle,
  cells,
  addSpacer,
}: Readonly<{
  label: string
  gridStyle: { gridTemplateColumns: string }
  cells: readonly { key: string; value: string; win: boolean }[]
  addSpacer: boolean
}>) {
  return (
    <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
      <div className="mt-cmp-rowlabel">{label}</div>
      {cells.map((cell) => (
        <div key={cell.key} className={`mt-cmp-cell ${cell.win ? 'win' : ''}`}>
          <span className="mt-cmp-big">{cell.value}</span>
        </div>
      ))}
      {addSpacer ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
    </div>
  )
}

function CompareMark({
  product,
  preferenceId,
}: Readonly<{
  product: Product
  preferenceId: CorePreferenceId
}>) {
  if (product.satisfies.includes(preferenceId)) {
    return <span className="mt-cmp-mark yes">yes</span>
  }
  if (product.misses.includes(preferenceId)) {
    return <span className="mt-cmp-mark no">no</span>
  }
  return <span className="mt-cmp-mark na">-</span>
}

function CompareSlot({
  index,
  product,
  canRemove,
  onRemove,
  onOpen,
}: Readonly<{
  index: number
  product: Product
  canRemove: boolean
  onRemove: (index: number) => void
  onOpen: (product: Product) => void
}>) {
  return (
    <div className="mt-cmp-col">
      <div className="mt-cmp-media">
        <button
          className="mt-cmp-media-open"
          type="button"
          onClick={() => onOpen(product)}
          aria-label={`Open ${product.name}`}
        >
          <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
          <div className="mt-cmp-ring">
            <MatchRing value={product.match} size={48} stroke={3} />
          </div>
        </button>
        {canRemove ? (
          <button
            className="mt-cmp-remove"
            type="button"
            onClick={() => onRemove(index)}
            aria-label="Remove from comparison"
          >
            <CloseIcon size={12} />
          </button>
        ) : null}
      </div>
      <div className="mt-mono mt-card-brand">{product.brand}</div>
      <div className="mt-cmp-name">{product.name}</div>
    </div>
  )
}

function CompareAddSlot({
  products,
  selectedIds,
  onAdd,
}: Readonly<{
  products: readonly Product[]
  selectedIds: readonly ProductId[]
  onAdd: (product: Product) => void
}>) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-cmp-col mt-cmp-col-empty">
      <div className="mt-cmp-picker">
        <button className="mt-cmp-choose" type="button" onClick={() => setOpen((value) => !value)}>
          <span className="mt-cmp-plus">+</span>
          <span className="mt-cmp-choose-text">Add a saved product</span>
        </button>
        {open ? (
          <CompareMenu
            products={products}
            selectedIds={selectedIds}
            onChoose={(product) => {
              onAdd(product)
              setOpen(false)
            }}
          />
        ) : null}
      </div>
    </div>
  )
}

function CompareMenu({
  products,
  selectedIds,
  onChoose,
}: Readonly<{
  products: readonly Product[]
  selectedIds: readonly ProductId[]
  onChoose: (product: Product) => void
}>) {
  const options = products.filter((product) => !selectedIds.includes(product.id))
  return (
    <div className="mt-cmp-menu">
      {options.length === 0 ? (
        <div className="mt-cmp-menu-empty">No saved products available to add.</div>
      ) : null}
      {options.map((product) => (
        <button
          key={product.id}
          className="mt-cmp-opt"
          type="button"
          onClick={() => onChoose(product)}
        >
          <span className="mt-cmp-opt-sw" style={{ background: product.tone }} />
          <span className="mt-cmp-opt-main">
            <span className="mt-cmp-opt-name">{product.name}</span>
            <span className="mt-mono mt-cmp-opt-cat">{product.category}</span>
          </span>
          <span className="mt-mono mt-cmp-opt-match">{product.match}%</span>
        </button>
      ))}
    </div>
  )
}

type PreferenceGroupId = 'needs' | 'values' | 'taste' | 'interests'

const PREFERENCE_GROUPS: readonly {
  id: PreferenceGroupId
  label: string
  description: string
  categories: readonly string[]
}[] = [
  {
    id: 'needs',
    label: 'Needs',
    description: 'Diet, materials, care, and home constraints',
    categories: ['food', 'materials', 'personal-care', 'home'],
  },
  {
    id: 'values',
    label: 'Values',
    description: 'Ethics, sustainability, and sourcing',
    categories: ['sustainability'],
  },
  {
    id: 'taste',
    label: 'Taste',
    description: 'Quality, shopping style, and tech preferences',
    categories: ['shopping', 'technology'],
  },
  {
    id: 'interests',
    label: 'Interests',
    description: 'Culture and hobbies that influence style',
    categories: ['interests'],
  },
]

const PREFERENCE_CATEGORY_LABELS: Record<string, string> = {
  food: 'Food',
  materials: 'Materials and fit',
  sustainability: 'Values',
  'personal-care': 'Personal care',
  shopping: 'Shopping style',
  technology: 'Technology',
  home: 'Home',
  interests: 'Interests',
}

const PREFERENCE_POLARITY_LABELS: Record<string, string> = {
  avoid: 'Avoid',
  prefer: 'Prefer',
  require: 'Need',
}

function preferenceCategory(preference: Preference): string {
  return preference.category || 'other'
}

function preferenceCategoryLabel(category: string): string {
  return (
    PREFERENCE_CATEGORY_LABELS[category] ??
    category
      .split('-')
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ')
  )
}

function preferenceSortValue(preference: Preference): number {
  return preference.displayOrder ?? Number.MAX_SAFE_INTEGER
}

function sortPreferences(preferences: readonly Preference[]): Preference[] {
  return [...preferences].sort(
    (left, right) =>
      preferenceSortValue(left) - preferenceSortValue(right) ||
      left.label.localeCompare(right.label),
  )
}

function preferenceMatchesSearch(preference: Preference, searchText: string): boolean {
  const category = preferenceCategoryLabel(preferenceCategory(preference))
  return [
    preference.label,
    preference.desc,
    preference.category,
    preference.polarity,
    category,
  ].some((value) => typeof value === 'string' && value.toLowerCase().includes(searchText))
}

function preferenceGroupsFor(
  preferences: readonly Preference[],
): { category: string; label: string; preferences: Preference[] }[] {
  const groups = new Map<string, Preference[]>()
  sortPreferences(preferences).forEach((preference) => {
    const category = preferenceCategory(preference)
    groups.set(category, [...(groups.get(category) ?? []), preference])
  })
  return [...groups.entries()].map(([category, groupedPreferences]) => ({
    category,
    label: preferenceCategoryLabel(category),
    preferences: groupedPreferences,
  }))
}

function PreferencesView({
  allPrefs,
  prefsOn,
  onToggle,
  onApplyDescription,
  budget,
  onBudget,
  deliveryLocations,
  onDeliveryLocations,
  clothingFit,
  onClothingFit,
  tasteProfile,
  onAcceptTasteSuggestion,
  onRejectTasteSuggestion,
  onDisableTasteSignal,
  onRemoveTasteSignal,
  profile,
  onDone,
}: Readonly<{
  allPrefs: readonly Preference[]
  prefsOn: ReadonlySet<PreferenceId>
  onToggle: (id: PreferenceId) => void
  onApplyDescription: (text: string) => Promise<boolean>
  budget: number | null
  onBudget: (value: number | null) => void
  deliveryLocations: readonly UserLocation[]
  onDeliveryLocations: (locations: UserLocation[]) => void
  clothingFit: ClothingFit
  onClothingFit: (value: ClothingFit) => void
  tasteProfile: UserTasteProfile
  onAcceptTasteSuggestion: (filterId: string) => void
  onRejectTasteSuggestion: (filterId: string) => void
  onDisableTasteSignal: (signalId: string, disabled: boolean) => void
  onRemoveTasteSignal: (signalId: string) => void
  profile: typeof PROFILE
  onDone: () => void
}>) {
  const [desc, setDesc] = useState('')
  const [importText, setImportText] = useState('')
  const [imported, setImported] = useState(false)
  const [copied, setCopied] = useState(false)
  const [parsing, setParsing] = useState(false)
  const [activePreferenceGroup, setActivePreferenceGroup] = useState<PreferenceGroupId>('interests')
  const [preferenceSearch, setPreferenceSearch] = useState('')
  const enabled = sortPreferences(allPrefs.filter((preference) => prefsOn.has(preference.id)))
  const preferenceSearchText = preferenceSearch.trim().toLowerCase()
  const activeGroup =
    PREFERENCE_GROUPS.find((group) => group.id === activePreferenceGroup) ?? PREFERENCE_GROUPS[0]
  const visiblePreferences = useMemo(() => {
    if (preferenceSearchText) {
      return sortPreferences(
        allPrefs.filter((preference) => preferenceMatchesSearch(preference, preferenceSearchText)),
      )
    }
    const activeCategories = new Set(activeGroup.categories)
    return sortPreferences(
      allPrefs.filter((preference) => activeCategories.has(preferenceCategory(preference))),
    )
  }, [activeGroup, allPrefs, preferenceSearchText])
  const visiblePreferenceGroups = useMemo(
    () => preferenceGroupsFor(visiblePreferences),
    [visiblePreferences],
  )
  const finiteBudget = budget ?? DEFAULT_BUDGET

  const copyQuestion = () => {
    const done = () => {
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    }
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(IMPORT_ASK).then(done, done)
    } else {
      done()
    }
  }

  const apply = (text: string, clear: () => void) => {
    if (!text.trim()) {
      return
    }
    setParsing(true)
    onApplyDescription(text.trim())
      .then((applied) => {
        if (applied) {
          clear()
        }
      })
      .finally(() => setParsing(false))
  }

  return (
    <main className="mt-feed mt-view mt-prefs-view">
      <ViewHead
        eyebrow={`${profile.name}'s profile`}
        title="What matters to you"
        sub="Start from prepared filters, describe it in your own words, or import your preferences from another AI."
        right={
          <button className="mt-act mt-act-primary mt-prefs-done" type="button" onClick={onDone}>
            Done
          </button>
        }
      />

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Where it ships</h3>
            <p className="mt-secsub">
              Leave shipping unrestricted, or add every place you want merchants to be able to
              deliver.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">
            {deliveryLocations.length > 0 ? `${deliveryLocations.length} active` : 'Anywhere'}
          </span>
        </div>
        <LocationSection locations={deliveryLocations} onSet={onDeliveryLocations} />
      </section>

      <LearnedTasteSection
        profile={tasteProfile}
        onAcceptSuggestion={onAcceptTasteSuggestion}
        onRejectSuggestion={onRejectTasteSuggestion}
        onDisableSignal={onDisableTasteSignal}
        onRemoveSignal={onRemoveTasteSignal}
      />

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Clothing fit</h3>
            <p className="mt-secsub">
              Used when apparel, shoes, or sizing-specific products need a fit signal.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">{clothingFitLabel(clothingFit)}</span>
        </div>
        <div className="mt-fit-options" role="radiogroup" aria-label="Clothing fit">
          {CLOTHING_FIT_OPTIONS.map((option) => (
            <button
              className={`mt-fit-option ${clothingFit === option.value ? 'active' : ''}`}
              key={option.value}
              type="button"
              role="radio"
              aria-checked={clothingFit === option.value}
              onClick={() => onClothingFit(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Describe it in your words</h3>
            <p className="mt-secsub">
              Tell Meant what you care about. We will turn it into filters you can fine-tune below.
            </p>
          </div>
        </div>
        <textarea
          className="mt-describe"
          value={desc}
          onChange={(event) => setDesc(event.target.value)}
          placeholder="I eat mostly organic, avoid polyester in clothing, prefer sustainable brands, and want gluten-free snacks."
        />
        <div className="mt-describe-actions">
          <button
            className="mt-act mt-act-primary"
            type="button"
            disabled={!desc.trim() || parsing}
            onClick={() => apply(desc, () => setDesc(''))}
          >
            {parsing ? 'Creating filters' : 'Create filters'}
          </button>
          <span className="mt-describe-hint">
            Meant matches your words to filters and creates new ones for anything custom.
          </span>
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Shopping profile</h3>
            <p className="mt-secsub">
              Selected needs, values, taste, and interests shape every product Meant shows you.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">{enabled.length} active</span>
        </div>
        <div className="mt-profile-summary">
          {enabled.length > 0 ? (
            <div className="mt-active-chip-grid" aria-label="Active shopping profile">
              {enabled.map((preference) => (
                <button
                  className={`mt-active-chip ${preferenceCategory(preference) === 'interests' ? 'interest' : ''}`}
                  key={preference.id}
                  type="button"
                  title={preference.desc}
                  onClick={() => onToggle(preference.id)}
                >
                  <span>{preference.label}</span>
                  <CloseIcon size={12} />
                </button>
              ))}
            </div>
          ) : (
            <div className="mt-pref-empty">No profile filters selected yet.</div>
          )}
        </div>

        <div className="mt-profile-builder">
          <div className="mt-pref-builder-head">
            <div>
              <div className="mt-pref-name">Add to profile</div>
              <div className="mt-pref-desc">
                Interests tune themes and references; needs and values still guide fit and ranking.
              </div>
            </div>
            <span className="mt-mono mt-sec-count">{visiblePreferences.length} shown</span>
          </div>

          <div className="mt-pref-tabs" role="tablist" aria-label="Preference groups">
            {PREFERENCE_GROUPS.map((group) => (
              <button
                className={`mt-pref-tab ${group.id === activePreferenceGroup ? 'active' : ''}`}
                key={group.id}
                type="button"
                role="tab"
                aria-selected={group.id === activePreferenceGroup}
                onClick={() => setActivePreferenceGroup(group.id)}
              >
                <span>{group.label}</span>
                <small>{group.description}</small>
              </button>
            ))}
          </div>

          <label className="mt-pref-search">
            <SearchIcon size={15} />
            <input
              value={preferenceSearch}
              onChange={(event) => setPreferenceSearch(event.target.value)}
              placeholder="Search filters and interests"
              aria-label="Search filters and interests"
            />
            {preferenceSearchText ? (
              <button
                className="mt-pref-search-clear"
                type="button"
                aria-label="Clear preference search"
                onClick={() => setPreferenceSearch('')}
              >
                <CloseIcon size={13} />
              </button>
            ) : null}
          </label>

          <div className="mt-pref-picker">
            {visiblePreferenceGroups.length === 0 ? (
              <div className="mt-pref-empty">No matching filters.</div>
            ) : null}
            {visiblePreferenceGroups.map((group) => (
              <div className="mt-pref-category" key={group.category}>
                <div className="mt-pref-category-head">
                  <span>{group.label}</span>
                  <span className="mt-mono">{group.preferences.length}</span>
                </div>
                <div className="mt-pref-chip-grid">
                  {group.preferences.map((preference) => {
                    const active = prefsOn.has(preference.id)
                    const category = preferenceCategory(preference)
                    const polarity = preference.polarity || 'prefer'
                    return (
                      <button
                        className={`mt-filter-chip ${active ? 'active' : ''} ${category === 'interests' ? 'interest' : ''} polarity-${polarity}`}
                        key={preference.id}
                        type="button"
                        aria-pressed={active}
                        title={preference.desc}
                        onClick={() => onToggle(preference.id)}
                      >
                        <span className="mt-filter-chip-label">{preference.label}</span>
                        {category === 'interests' ? null : (
                          <span className="mt-filter-chip-meta">
                            {PREFERENCE_POLARITY_LABELS[polarity] ?? polarity}
                          </span>
                        )}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-budget">
          <div className="mt-budget-head">
            <div>
              <div className="mt-pref-name">Comfortable spend</div>
              <div className="mt-pref-desc">
                {budget === null
                  ? 'Meant will not apply a profile-level price ceiling.'
                  : 'Meant looks for the best quality it can find under this.'}
              </div>
            </div>
            <div className="mt-budget-val">{budget === null ? 'Unlimited' : `$${budget}`}</div>
          </div>
          <label className="mt-budget-toggle">
            <input
              type="checkbox"
              checked={budget === null}
              onChange={(event) => onBudget(event.target.checked ? null : finiteBudget)}
            />
            <span>No spend limit</span>
          </label>
          <input
            className="mt-range"
            type="range"
            min="20"
            max="300"
            step="5"
            value={finiteBudget}
            disabled={budget === null}
            onChange={(event) => onBudget(Number(event.target.value))}
          />
          <div className="mt-budget-scale mt-mono">
            <span>$20</span>
            <span>$300</span>
          </div>
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Bring your profile from another AI</h3>
            <p className="mt-secsub">
              Already chat with ChatGPT or Claude? Ask it about you, then paste its answer here to
              build filters.
            </p>
          </div>
        </div>
        <div className="mt-sync-pane">
          <div className="mt-sync-block">
            <div className="mt-sync-row">
              <div className="mt-sync-label">
                <span className="mt-step-num">1</span> Ask your assistant about you
              </div>
              <button
                className={`mt-act mt-act-ghost mt-copy ${copied ? 'done' : ''}`}
                type="button"
                onClick={copyQuestion}
              >
                {copied ? 'Copied' : 'Copy question'}
              </button>
            </div>
            <p className="mt-sync-desc">
              Paste this into another AI. It should reply with what it knows about your shopping
              preferences.
            </p>
            <textarea
              className="mt-prompt-text mt-mono"
              readOnly
              value={IMPORT_ASK}
              onFocus={(event) => event.currentTarget.select()}
            />
          </div>
          <div className="mt-sync-block">
            <div className="mt-sync-row">
              <div className="mt-sync-label">
                <span className="mt-step-num">2</span> Paste its answer back
              </div>
            </div>
            <textarea
              className="mt-describe"
              value={importText}
              onChange={(event) => setImportText(event.target.value)}
              placeholder="Paste your assistant's reply about your preferences here..."
            />
            <div className="mt-describe-actions">
              <button
                className="mt-act mt-act-primary"
                type="button"
                disabled={!importText.trim() || parsing}
                onClick={() => {
                  apply(importText, () => setImportText(''))
                  setImported(true)
                  window.setTimeout(() => setImported(false), 3200)
                }}
              >
                Create filters from this
              </button>
              <span className="mt-describe-hint">
                {imported
                  ? 'Added to your filters above.'
                  : 'Meant reads it the same way as your own description.'}
              </span>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}

function LearnedTasteSection({
  profile,
  onAcceptSuggestion,
  onRejectSuggestion,
  onDisableSignal,
  onRemoveSignal,
}: Readonly<{
  profile: UserTasteProfile
  onAcceptSuggestion: (filterId: string) => void
  onRejectSuggestion: (filterId: string) => void
  onDisableSignal: (signalId: string, disabled: boolean) => void
  onRemoveSignal: (signalId: string) => void
}>) {
  const visibleSignals = profile.signals
    .filter((signal) => Math.abs(signal.weight) >= 0.25)
    .slice(0, 12)
  return (
    <section className="mt-prefs-section mt-learned">
      <div className="mt-sechead">
        <div>
          <h3 className="mt-sectitle">Learned from behavior</h3>
          <p className="mt-secsub">
            Saves, purchases, dismissals, and repeat searches tune ranking without changing explicit
            filters.
          </p>
        </div>
        <span className="mt-mono mt-sec-count">{visibleSignals.length} signals</span>
      </div>

      {profile.suggestions.length > 0 ? (
        <div className="mt-learned-suggestions">
          {profile.suggestions.map((suggestion) => (
            <div className="mt-learned-suggestion" key={suggestion.filterId}>
              <div>
                <div className="mt-pref-name">Add {suggestion.label}</div>
                <div className="mt-pref-desc">{suggestion.reason}</div>
              </div>
              <div className="mt-learned-actions">
                <button
                  className="mt-act mt-act-primary"
                  type="button"
                  onClick={() => onAcceptSuggestion(suggestion.filterId)}
                >
                  Add filter
                </button>
                <button
                  className="mt-act mt-act-ghost"
                  type="button"
                  onClick={() => onRejectSuggestion(suggestion.filterId)}
                >
                  Dismiss
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {visibleSignals.length > 0 ? (
        <div className="mt-learned-grid">
          {visibleSignals.map((signal) => {
            const disabled = signal.status === 'DISABLED'
            return (
              <div className={`mt-learned-signal ${disabled ? 'disabled' : ''}`} key={signal.id}>
                <div>
                  <div className="mt-learned-label">{signal.label}</div>
                  <div className="mt-learned-meta">
                    {tasteSignalTypeLabel(signal.signalType)} ·{' '}
                    {tasteSignalWeightLabel(signal.weight)}
                  </div>
                </div>
                <div className="mt-learned-actions">
                  <button
                    className="mt-act mt-act-ghost"
                    type="button"
                    onClick={() => onDisableSignal(signal.id, !disabled)}
                  >
                    {disabled ? 'Enable' : 'Disable'}
                  </button>
                  <button
                    className="mt-act mt-act-ghost"
                    type="button"
                    onClick={() => onRemoveSignal(signal.id)}
                  >
                    Remove
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="mt-pref-empty">Meant has not learned enough from your behavior yet.</div>
      )}
    </section>
  )
}

function tasteSignalTypeLabel(type: UserTasteProfile['signals'][number]['signalType']): string {
  return type.toLowerCase().replaceAll('_', ' ')
}

function tasteSignalWeightLabel(weight: number): string {
  if (weight > 0) {
    return `boost +${weight.toFixed(1)}`
  }
  return `penalty ${Math.abs(weight).toFixed(1)}`
}

function LocationSection({
  locations,
  onSet,
}: Readonly<{
  locations: readonly UserLocation[]
  onSet: (locations: UserLocation[]) => void
}>) {
  const [adding, setAdding] = useState(false)
  const [code, setCode] = useState('')
  const [city, setCity] = useState('')
  const country = LOCATIONS.find((option) => option.code === code)
  const locationKeys = new Set(locations.map((location) => `${location.code}:${location.city}`))

  const save = () => {
    if (!country || !city) {
      return
    }
    const nextLocation = { code: country.code, country: country.country, city }
    const nextKey = `${nextLocation.code}:${nextLocation.city}`
    onSet(locationKeys.has(nextKey) ? [...locations] : [...locations, nextLocation])
    setCode('')
    setCity('')
    setAdding(false)
  }

  return (
    <div className="mt-loc-stack">
      <div className={`mt-loc-set ${locations.length === 0 ? 'unrestricted' : ''}`}>
        <span className="mt-loc-pin">⌖</span>
        <div className="mt-loc-text">
          <div className="mt-loc-city">
            {locations.length === 0 ? 'Anywhere' : deliveryLocationSummary(locations)}
          </div>
          <div className="mt-loc-note">
            {locations.length === 0
              ? 'Meant is not hiding products by delivery destination.'
              : 'Meant shows products that can ship to at least one selected destination.'}
          </div>
        </div>
        <div className="mt-loc-actions">
          {locations.length > 0 ? (
            <button className="mt-loc-change" type="button" onClick={() => onSet([])}>
              No delivery filter
            </button>
          ) : null}
          <button className="mt-loc-change" type="button" onClick={() => setAdding(true)}>
            <PlusIcon size={14} />
            Add location
          </button>
        </div>
      </div>

      {locations.length > 0 ? (
        <div className="mt-loc-selected" aria-label="Delivery locations">
          {locations.map((location) => (
            <button
              className="mt-active-chip"
              key={`${location.code}:${location.city}`}
              type="button"
              onClick={() =>
                onSet(
                  locations.filter(
                    (candidate) =>
                      candidate.code !== location.code || candidate.city !== location.city,
                  ),
                )
              }
            >
              <span>
                {location.city}, {location.country}
              </span>
              <CloseIcon size={12} />
            </button>
          ))}
        </div>
      ) : null}

      {adding ? (
        <div className="mt-loc-form">
          <div className="mt-loc-fields">
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Country</span>
              <select
                className="mt-select"
                value={code}
                onChange={(event) => {
                  setCode(event.target.value)
                  setCity('')
                }}
              >
                <option value="">Select country</option>
                {LOCATIONS.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.country}
                  </option>
                ))}
              </select>
            </label>
            <label className="mt-field">
              <span className="mt-field-label mt-mono">City</span>
              <select
                className="mt-select"
                value={city}
                disabled={!code}
                onChange={(event) => setCity(event.target.value)}
              >
                <option value="">{code ? 'Select city' : 'Pick a country first'}</option>
                {(country?.cities ?? []).map((candidate) => (
                  <option key={candidate} value={candidate}>
                    {candidate}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="mt-describe-actions">
            <button
              className="mt-act mt-act-primary"
              type="button"
              onClick={save}
              disabled={!code || !city}
            >
              Add location
            </button>
            <button
              className="mt-act mt-act-ghost"
              type="button"
              onClick={() => {
                setAdding(false)
                setCode('')
                setCity('')
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function OrdersView({
  orders,
  products,
  loading,
  error,
  flashId,
  preferences,
  onOpen,
  onReorder,
}: Readonly<{
  orders: readonly Order[]
  products: readonly Product[]
  loading: boolean
  error: string | null
  flashId: string | null
  preferences: readonly Preference[]
  onOpen: (product: Product) => void
  onReorder: (order: Order) => void
}>) {
  const [page, setPage] = useState(0)
  const pageSize = 3
  const pageCount = Math.ceil(orders.length / pageSize)
  const safePage = Math.min(page, Math.max(0, pageCount - 1))
  const start = safePage * pageSize
  const visible = orders.slice(start, start + pageSize)
  const totalSaved = orders.reduce((sum, order) => sum + order.saved, 0)

  useEffect(() => {
    if (flashId) {
      setPage(0)
    }
  }, [flashId])

  if (orders.length === 0) {
    return (
      <main className="mt-feed mt-view">
        <ViewHead eyebrow="Your purchases" title="Order history" />
        <EmptyState
          title={loading ? 'Loading orders' : error ? 'Could not load orders' : 'No orders yet'}
          sub={error ?? 'When you check out, your orders land here.'}
          mark={<CartIcon />}
        />
      </main>
    )
  }

  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Your purchases"
        title="Order history"
        sub={`${orders.length} orders across your stores, each one filtered to what matters to you.`}
        right={
          totalSaved > 0 ? (
            <div className="mt-order-lifetime">
              <span className="mt-mono mt-order-lifetime-k">Saved with Meant</span>
              <span className="mt-order-lifetime-v">{money(totalSaved)}</span>
            </div>
          ) : undefined
        }
      />
      <div className="mt-orders">
        {flashId && safePage === 0 ? (
          <div className="mt-order-flash">Order {flashId} placed. Meant is preparing dispatch.</div>
        ) : null}
        {visible.map((order) => (
          <OrderCard
            key={order.id}
            order={order}
            products={products}
            preferences={preferences}
            flash={order.id === flashId}
            onOpen={onOpen}
            onReorder={onReorder}
          />
        ))}
      </div>
      {pageCount > 1 ? (
        <nav className="mt-pager" aria-label="Order history pages">
          <span className="mt-mono mt-pager-info">
            Showing {start + 1}-{Math.min(start + pageSize, orders.length)} of {orders.length}
          </span>
          <div className="mt-pager-ctrls">
            <button
              className="mt-pager-btn"
              type="button"
              disabled={safePage === 0}
              onClick={() => setPage(safePage - 1)}
            >
              Prev
            </button>
            {Array.from({ length: pageCount }).map((_, index) => (
              <button
                key={index}
                className={`mt-pager-num mt-mono ${index === safePage ? 'on' : ''}`}
                type="button"
                aria-current={index === safePage ? 'page' : undefined}
                onClick={() => setPage(index)}
              >
                {index + 1}
              </button>
            ))}
            <button
              className="mt-pager-btn"
              type="button"
              disabled={safePage === pageCount - 1}
              onClick={() => setPage(safePage + 1)}
            >
              Next
            </button>
          </div>
        </nav>
      ) : null}
    </main>
  )
}

function orderLineUnitPrice(item: CartItem, product?: Product): number {
  if (product) {
    const offer =
      product.offers.find((candidate) => candidate.merchant === item.merchant) ?? product.offers[0]
    return offer ? offer.price : 0
  }
  const unitAmount = parseOrderAmount(item.unitPriceAmount)
  if (unitAmount !== null) {
    return unitAmount
  }
  const totalAmount = parseOrderAmount(item.lineTotalAmount)
  return totalAmount !== null && item.qty > 0 ? totalAmount / item.qty : 0
}

function orderLineTotal(item: CartItem, product?: Product): number {
  if (product) {
    return orderLineUnitPrice(item, product) * item.qty
  }
  const totalAmount = parseOrderAmount(item.lineTotalAmount)
  return totalAmount ?? orderLineUnitPrice(item, product) * item.qty
}

function parseOrderAmount(value?: string | number | null): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const amount = Number(trimmed)
  return Number.isFinite(amount) ? amount : null
}

function OrderCard({
  order,
  products,
  preferences,
  flash,
  onOpen,
  onReorder,
}: Readonly<{
  order: Order
  products: readonly Product[]
  preferences: readonly Preference[]
  flash: boolean
  onOpen: (product: Product) => void
  onReorder: (order: Order) => void
}>) {
  const lines = order.items.map((item) => ({
    item,
    product: products.find((product) => product.id === item.id),
  }))
  const total = lines.reduce((sum, { item, product }) => sum + orderLineTotal(item, product), 0)
  const matched = Array.from(new Set(lines.flatMap((line) => line.product?.satisfies ?? [])))

  return (
    <div className={`mt-order ${flash ? 'flash' : ''}`}>
      <div className="mt-order-head">
        <div className="mt-order-head-l">
          <span className="mt-mono mt-order-id">{order.id}</span>
          <span className="mt-order-date">
            Placed {formatOrderDate(order.date)} · {lines.length} items
          </span>
        </div>
        <div className="mt-order-head-r">
          <span
            className={`mt-order-status mt-order-status-${order.status.toLowerCase().replace(/\s+/g, '-')}`}
          >
            <span className="mt-order-status-dot" /> {order.status}
          </span>
          <span className="mt-order-total">{money(total)}</span>
        </div>
      </div>
      <div className="mt-order-track mt-mono">{order.statusNote}</div>
      <div className="mt-order-items">
        {lines.map(({ item, product }) => {
          const unitPrice = orderLineUnitPrice(item, product)
          const lineTotal = orderLineTotal(item, product)
          const productName = product?.name ?? item.productTitle ?? item.productVariantId ?? item.id
          const productBrand = product?.brand ?? item.merchant
          return (
            <button
              className="mt-order-item"
              key={`${item.id}-${item.merchant}`}
              type="button"
              onClick={() => product && onOpen(product)}
              disabled={!product}
            >
              <div className="mt-order-item-media">
                {product ? (
                  <ProductArtwork product={product} label={product.category.toLowerCase()} />
                ) : item.imageUrl ? (
                  <img className="mt-order-item-img" src={item.imageUrl} alt={productName} />
                ) : (
                  <div className="mt-order-item-fallback mt-mono">
                    {productName.slice(0, 2).toUpperCase()}
                  </div>
                )}
              </div>
              <div className="mt-order-item-info">
                <div className="mt-mono mt-order-item-brand">{productBrand}</div>
                <div className="mt-order-item-name">{productName}</div>
                <div className="mt-mono mt-order-item-meta">
                  {item.qty} × {money(unitPrice)} · {item.merchant}
                </div>
              </div>
              <div className="mt-order-item-price">{money(lineTotal)}</div>
            </button>
          )
        })}
      </div>
      <div className="mt-order-foot">
        <div className="mt-order-prefs">
          <span className="mt-mono mt-order-prefs-label">Matched your preferences</span>
          <div className="mt-chips">
            {matched.slice(0, 4).map((id) => (
              <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" small />
            ))}
          </div>
        </div>
        <div className="mt-order-foot-right">
          {order.saved > 0 ? (
            <div className="mt-order-saved mt-mono">
              Meant saved you {money(order.saved)} {order.savedNote ? `· ${order.savedNote}` : ''}
            </div>
          ) : null}
          <button className="mt-order-reorder" type="button" onClick={() => onReorder(order)}>
            Order again
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * Splits a single full-name field into first name + surname the same way the backend does:
 * first whitespace-separated token is the first name, the remainder is the surname.
 */
function splitName(fullName: string): { firstName: string; surname: string | null } {
  const tokens = fullName.trim().split(/\s+/).filter(Boolean)
  const firstName = tokens[0] ?? ''
  const surname = tokens.length > 1 ? tokens.slice(1).join(' ') : null
  return { firstName, surname }
}

const PROFILE_PICTURE_ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const PROFILE_PICTURE_MAX_BYTES = 5 * 1024 * 1024

function AccountView({
  user,
  userId,
  merchants,
  merchantIdentityLinks,
  merchantIdentityLinksLoading,
  merchantIdentityLinksError,
  onSave,
  onSignOut,
  onEditPrefs,
  onConnectMerchant,
  onRevokeMerchant,
  onDone,
}: Readonly<{
  user: UserAccount
  userId?: string
  merchants: readonly MerchantProfile[]
  merchantIdentityLinks: readonly MerchantIdentityLinkProfile[]
  merchantIdentityLinksLoading: boolean
  merchantIdentityLinksError: string | null
  onSave: (user: UserAccount) => void
  onSignOut: () => void
  onEditPrefs: () => void
  onConnectMerchant: (merchant: MerchantProfile) => void
  onRevokeMerchant: (merchantId: string) => void
  onDone: () => void
}>) {
  const [name, setName] = useState(user.name)
  const [avatar, setAvatar] = useState<string | null>(user.avatar)
  const [avatarPath, setAvatarPath] = useState<string | null>(user.avatarPath)
  const [pendingFile, setPendingFile] = useState<File | null>(null)
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)
  const savedTimeoutRef = useRef<number | null>(null)
  const preview: UserAccount = { name, email: user.email, avatar, avatarPath }
  const dirty = name !== user.name || avatarPath !== user.avatarPath || pendingFile !== null
  const hasProfilePicture = Boolean(avatar || avatarPath)
  const identityLinksByMerchant = new Map(
    merchantIdentityLinks.map((link) => [link.merchantId, link]),
  )
  const linkableMerchants = merchants.filter((merchant) => merchant.supportsIdentityLinking)

  useEffect(() => {
    return () => {
      if (savedTimeoutRef.current !== null) {
        window.clearTimeout(savedTimeoutRef.current)
      }
    }
  }, [])

  const showSaved = () => {
    if (savedTimeoutRef.current !== null) {
      window.clearTimeout(savedTimeoutRef.current)
    }
    setSaved(true)
    savedTimeoutRef.current = window.setTimeout(() => {
      setSaved(false)
      savedTimeoutRef.current = null
    }, 1800)
  }

  const onFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    setSaved(false)
    setError(null)
    if (!PROFILE_PICTURE_ALLOWED_TYPES.has(file.type)) {
      setError('Use a JPG, PNG, or WebP image.')
      event.target.value = ''
      return
    }
    if (file.size > PROFILE_PICTURE_MAX_BYTES) {
      setError('Profile photo must be 5 MB or smaller.')
      event.target.value = ''
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setAvatar(reader.result)
        setPendingFile(file)
      }
    }
    reader.readAsDataURL(file)
    event.target.value = ''
  }

  const saveAccount = async () => {
    const nextName = name.trim() || user.name
    const { firstName, surname } = splitName(nextName)
    let uploadedPath: string | null = null
    setSaving(true)
    setError(null)
    try {
      let savedName = nextName
      let savedEmail = user.email
      let savedAvatar = avatar
      let savedAvatarPath = avatarPath

      if (nextName !== user.name) {
        const profile = await updateProfile({ firstName, surname })
        savedName =
          [profile.firstName, profile.surname].filter(Boolean).join(' ').trim() || nextName
        savedEmail = profile.email || user.email
        onSave({ ...user, name: savedName, email: savedEmail })
        setName(savedName)
      }

      if (pendingFile) {
        if (!userId) {
          throw new Error('Missing authenticated user id')
        }
        const uploaded = await uploadProfilePictureFile(userId, pendingFile)
        uploadedPath = uploaded.path
        const profile = await updateProfilePicture(uploaded.path)
        savedAvatarPath = profile.profilePicturePath ?? uploaded.path
        savedAvatar = uploaded.signedUrl
        if (user.avatarPath && user.avatarPath !== savedAvatarPath) {
          void deleteProfilePictureFile(user.avatarPath)
        }
      } else if (avatarPath === null && user.avatarPath) {
        const profile = await removeProfilePicture()
        savedAvatarPath = profile.profilePicturePath ?? null
        savedAvatar = null
        void deleteProfilePictureFile(user.avatarPath)
      }

      onSave({
        name: savedName,
        email: savedEmail,
        avatar: savedAvatar,
        avatarPath: savedAvatarPath,
      })
      setName(savedName)
      setAvatar(savedAvatar)
      setAvatarPath(savedAvatarPath)
      setPendingFile(null)
      showSaved()
    } catch {
      if (uploadedPath) {
        void deleteProfilePictureFile(uploadedPath)
      }
      setError('Could not save your changes. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="mt-feed mt-view">
      <ViewHead
        eyebrow="Account"
        title="Your account"
        sub="Your name and photo are how you show up across Meant. This is separate from your shopping preferences."
        right={
          <button className="mt-act mt-act-primary mt-prefs-done" type="button" onClick={onDone}>
            Done
          </button>
        }
      />
      <div className="mt-acct-card">
        <div className="mt-acct-idrow">
          <div className="mt-acct-avatar">
            <Avatar user={preview} size={84} />
          </div>
          <div className="mt-acct-photo-actions">
            <button
              className="mt-acct-uploadbtn"
              type="button"
              onClick={() => fileRef.current?.click()}
            >
              {hasProfilePicture ? 'Change photo' : 'Upload photo'}
            </button>
            {hasProfilePicture ? (
              <button
                className="mt-acct-removebtn"
                type="button"
                onClick={() => {
                  setSaved(false)
                  setPendingFile(null)
                  setAvatar(null)
                  setAvatarPath(null)
                }}
              >
                Remove
              </button>
            ) : null}
            <div className="mt-acct-photo-hint">
              JPG, PNG, or WebP up to 5 MB. A square image works best.
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={onFile}
              hidden
            />
          </div>
        </div>
        <div className="mt-acct-fields">
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Full name</span>
            <input
              className="mt-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Email</span>
            <input className="mt-input" type="email" value={user.email} readOnly disabled />
            <span className="mt-field-hint">
              Your email is your sign-in identity and can't be changed here.
            </span>
          </label>
        </div>
        <div className="mt-acct-save-row">
          <button
            className="mt-acct-save"
            type="button"
            disabled={!dirty || saving}
            onClick={() => {
              void saveAccount()
            }}
          >
            {saving ? 'Saving…' : 'Save changes'}
          </button>
          {saved ? <span className="mt-acct-saved-note">Saved</span> : null}
          {error ? <span className="mt-acct-save-error">{error}</span> : null}
        </div>
      </div>
      <button className="mt-acct-link" type="button" onClick={onEditPrefs}>
        <div>
          <div className="mt-acct-link-t">Shopping preferences</div>
          <div className="mt-acct-link-s">
            The filters Meant applies to everything it shows you.
          </div>
        </div>
      </button>
      <section className="mt-acct-card mt-acct-links">
        <div className="mt-acct-link-head">
          <div>
            <div className="mt-acct-link-t">Connected stores</div>
            <div className="mt-acct-link-s">
              Link supported merchant accounts so Meant can use scoped order, account, and loyalty
              access.
            </div>
          </div>
          {merchantIdentityLinksLoading ? (
            <span className="mt-mono mt-acct-link-status">Loading</span>
          ) : null}
        </div>
        {merchantIdentityLinksError ? (
          <div className="mt-acct-save-error">{merchantIdentityLinksError}</div>
        ) : null}
        {linkableMerchants.length > 0 ? (
          <div className="mt-acct-store-list">
            {linkableMerchants.map((merchant) => {
              const link = identityLinksByMerchant.get(merchant.id)
              const connected = link?.status === 'CONNECTED'
              return (
                <div className="mt-acct-store" key={merchant.id}>
                  <div>
                    <div className="mt-acct-store-name">{merchant.name}</div>
                    <div className="mt-mono mt-acct-store-meta">
                      {merchant.domain} ·{' '}
                      {connected
                        ? 'Connected'
                        : link?.status === 'PENDING'
                          ? 'Pending consent'
                          : 'Not connected'}
                    </div>
                  </div>
                  {connected ? (
                    <button
                      className="mt-acct-removebtn"
                      type="button"
                      onClick={() => onRevokeMerchant(merchant.id)}
                    >
                      Revoke
                    </button>
                  ) : (
                    <button
                      className="mt-acct-uploadbtn"
                      type="button"
                      onClick={() => onConnectMerchant(merchant)}
                    >
                      Connect
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        ) : merchantIdentityLinksLoading ? null : (
          <div className="mt-acct-empty">
            No listed merchants currently advertise identity linking.
          </div>
        )}
      </section>
      <div className="mt-acct-danger">
        <div>
          <div className="mt-acct-link-t">Sign out</div>
          <div className="mt-acct-link-s">
            You will need your email and password to sign back in.
          </div>
        </div>
        <button className="mt-acct-signout" type="button" onClick={onSignOut}>
          Sign out
        </button>
      </div>
    </main>
  )
}

const GoogleGlyph = (
  <svg width="17" height="17" viewBox="0 0 18 18" aria-hidden="true">
    <path
      d="M17.6 9.2c0-.6-.05-1.18-.16-1.74H9v3.3h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.64-3.88 2.64-6.54z"
      fill="#4285F4"
    />
    <path
      d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z"
      fill="#34A853"
    />
    <path d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z" fill="#FBBC05" />
    <path
      d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.47.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z"
      fill="#EA4335"
    />
  </svg>
)

const AppleGlyph = (
  <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" fill="currentColor">
    <path d="M11.18 8.5c-.02-1.66 1.36-2.46 1.42-2.5-.77-1.13-1.98-1.29-2.4-1.3-1.02-.1-1.99.6-2.5.6-.52 0-1.31-.59-2.16-.57-1.1.02-2.13.65-2.7 1.64-1.16 2.01-.3 4.98.83 6.6.55.8 1.21 1.69 2.07 1.66.83-.03 1.15-.54 2.15-.54 1 0 1.29.54 2.16.52.9-.01 1.46-.81 2-1.61.64-.92.9-1.82.91-1.86-.02-.01-1.75-.67-1.78-2.64zM9.6 3.62c.45-.55.76-1.31.67-2.07-.65.03-1.45.44-1.92.98-.42.48-.79 1.26-.69 2 .73.06 1.48-.37 1.94-.91z" />
  </svg>
)

function AuthScreen({
  mode,
  onMode,
  auth,
}: Readonly<{
  mode: AuthMode
  onMode: (mode: AuthMode) => void
  auth: Omit<AuthActions, 'signOut'>
}>) {
  const signup = mode === 'signup'
  const reset = mode === 'reset'
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [sent, setSent] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const runOAuth = async (provider: 'google' | 'apple') => {
    setError(null)
    const { error: oauthError } = await auth.signInWithOAuth(provider)
    if (oauthError) {
      setError(oauthError)
    }
  }

  if (reset) {
    return (
      <div className="mt-auth">
        <AuthBrand />
        <main className="mt-auth-panel">
          <form
            className="mt-auth-card"
            onSubmit={async (event: FormEvent<HTMLFormElement>) => {
              event.preventDefault()
              if (!email.trim() || pending) {
                return
              }
              setPending(true)
              setError(null)
              const { error: resetError } = await auth.resetPassword(email.trim())
              setPending(false)
              if (resetError) {
                setError(resetError)
                return
              }
              setSent(true)
            }}
          >
            <button
              className="mt-reset-back mt-mono"
              type="button"
              onClick={() => onMode('signin')}
            >
              Back to sign in
            </button>
            <div className="mt-mono mt-auth-eyebrow">Password reset</div>
            <h2 className="mt-auth-title">Forgot your password?</h2>
            <p className="mt-auth-sub">
              Enter your email and Meant will send you a link to reset your password.
            </p>
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Email</span>
              <input
                className="mt-input"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            {sent ? (
              <div className="mt-reset-ok mt-mono">Check your inbox for a reset link.</div>
            ) : null}
            {error ? <div className="mt-auth-error mt-mono">{error}</div> : null}
            <button className="mt-auth-primary" type="submit" disabled={!email.trim() || pending}>
              {pending ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        </main>
      </div>
    )
  }

  const canSubmit = Boolean(email.trim() && password.trim() && (!signup || name.trim())) && !pending

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    setPending(true)
    setError(null)
    setNotice(null)
    if (signup) {
      const { error: signUpError, needsConfirmation } = await auth.signUp(
        email.trim(),
        password,
        name.trim(),
      )
      setPending(false)
      if (signUpError) {
        setError(signUpError)
        return
      }
      if (needsConfirmation) {
        setNotice('Account created. Check your email to confirm, then sign in.')
        onMode('signin')
      }
      // When confirmation is not required, the auth state listener flips the app into the app shell.
      return
    }
    const { error: signInError } = await auth.signInWithPassword(email.trim(), password)
    setPending(false)
    if (signInError) {
      setError(signInError)
    }
  }

  return (
    <div className="mt-auth">
      <AuthBrand />
      <main className="mt-auth-panel">
        <form className="mt-auth-card" onSubmit={submit}>
          <div className="mt-mono mt-auth-eyebrow">
            {signup ? 'Create your account' : 'Welcome back'}
          </div>
          <h2 className="mt-auth-title">
            {signup ? 'Start shopping the way you mean it.' : 'Sign in to Meant.'}
          </h2>
          <p className="mt-auth-sub">
            {signup
              ? 'Set up your profile once and Meant applies it across supported stores.'
              : 'Pick up right where you left off.'}
          </p>
          <div className="mt-auth-social">
            <button
              className="mt-social-btn"
              type="button"
              disabled={pending}
              onClick={() => runOAuth('google')}
            >
              {GoogleGlyph} Continue with Google
            </button>
            <button
              className="mt-social-btn"
              type="button"
              disabled={pending}
              onClick={() => runOAuth('apple')}
            >
              {AppleGlyph} Continue with Apple
            </button>
          </div>
          <div className="mt-auth-div">
            <span>or with email</span>
          </div>
          {signup ? (
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Full name</span>
              <input
                className="mt-input"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
          ) : null}
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Email</span>
            <input
              className="mt-input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Password</span>
            <input
              className="mt-input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {!signup ? (
            <button
              className="mt-auth-forgot mt-mono"
              type="button"
              onClick={() => onMode('reset')}
            >
              Forgot password?
            </button>
          ) : null}
          {error ? <div className="mt-auth-error mt-mono">{error}</div> : null}
          {notice ? <div className="mt-reset-ok mt-mono">{notice}</div> : null}
          <button className="mt-auth-primary" type="submit" disabled={!canSubmit}>
            {pending ? 'Working…' : signup ? 'Create account' : 'Sign in'}
          </button>
          <div className="mt-auth-toggle">
            {signup ? 'Already have an account? ' : 'New to Meant? '}
            <button
              type="button"
              onClick={() => {
                setError(null)
                onMode(signup ? 'signin' : 'signup')
              }}
            >
              {signup ? 'Sign in' : 'Create an account'}
            </button>
          </div>
        </form>
      </main>
    </div>
  )
}

function AuthBrand() {
  return (
    <aside className="mt-auth-brand">
      <img className="mt-auth-brand-logo" src="/assets/meant-logo.png" alt="Meant" />
      <div className="mt-auth-brand-mid">
        <h1 className="mt-auth-brand-line">
          Everything here is <em>meant</em> for you.
        </h1>
        <p className="mt-auth-brand-sub">
          One account for supported stores. Meant learns what matters to you and quietly filters out
          the rest.
        </p>
        <div className="mt-auth-pills">
          {['Organic', 'Natural materials', 'Strong reviews', 'Sustainable brands'].map((pill) => (
            <span className="mt-auth-pill" key={pill}>
              {pill}
            </span>
          ))}
        </div>
      </div>
      <div className="mt-auth-brand-foot mt-mono">
        Your preferences travel with you everywhere you shop.
      </div>
    </aside>
  )
}

export function MeantApp() {
  const [view, setView] = useState<View>('discover')
  const [authMode, setAuthMode] = useState<AuthMode>('signin')
  const { session, loading: authLoading, signOut, ...authActions } = useSupabaseAuth()
  const authed = Boolean(session)
  const [theme, setTheme] = useStoredState<Theme>('meant.theme', 'light')
  const [reply, setReply] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [searchResults, setSearchResults] = useState<Product[]>([])
  const [remoteProducts, setRemoteProducts] = useState<Product[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchLoadingMore, setSearchLoadingMore] = useState(false)
  const [searchHasMore, setSearchHasMore] = useState(false)
  const [searchNextOffset, setSearchNextOffset] = useState<number | null>(null)
  const [searchMerchantId, setSearchMerchantId] = useState<string | null>(null)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [productSearchActivities, setProductSearchActivities] = useState<AgentActivity[]>([])
  const [searchSuggestions, setSearchSuggestions] = useState<string[]>([])
  const [merchants, setMerchants] = useState<MerchantProfile[]>([])
  const [merchantsLoading, setMerchantsLoading] = useState(false)
  const [merchantIdentityLinks, setMerchantIdentityLinks] = useState<MerchantIdentityLinkProfile[]>(
    [],
  )
  const [merchantIdentityLinksLoading, setMerchantIdentityLinksLoading] = useState(false)
  const [merchantIdentityLinksError, setMerchantIdentityLinksError] = useState<string | null>(null)
  const [inventoryItems, setInventoryItems] = useState<UserInventoryItemProfile[]>([])
  const [inventoryLoading, setInventoryLoading] = useState(false)
  const [inventoryError, setInventoryError] = useState<string | null>(null)
  const [selectedMerchantId, setSelectedMerchantId] = useStoredState<string | null>(
    'meant.merchant',
    null,
  )
  const [activeProduct, setActiveProduct] = useState<Product | null>(null)
  const [navProducts, setNavProducts] = useState<readonly Product[]>([])
  const [shelf, setShelf] = useStoredState<ShelfItem[]>('meant.shelf', [])
  const [shelfOpen, setShelfOpen] = useState(false)
  const [shelfFlashMessageId, setShelfFlashMessageId] = useState<string | null>(null)
  const [discoverFindRequest, setDiscoverFindRequest] = useState<DiscoverFindRequest | null>(null)
  const [productDetailChatRequest, setProductDetailChatRequest] =
    useState<ProductDetailChatRequest | null>(null)
  const [savedIds, setSavedIds] = useState<ProductId[]>([])
  const [savedProducts, setSavedProducts] = useState<Product[]>([])
  const [savePendingIds, setSavePendingIds] = useState<ProductId[]>([])
  const [compareIds, setCompareIds] = useStoredState<ProductId[]>('meant.compare', [
    ...DEFAULT_COMPARE,
  ])
  const [compareProducts, setCompareProducts] = useStoredState<Product[]>(
    'meant.compareProducts',
    [],
  )
  const [availablePrefs, setAvailablePrefs] = useState<Preference[]>([...PREFERENCES])
  const [prefsOn, setPrefsOn] = useStoredState<PreferenceId[]>('meant.prefsOn', [
    ...DEFAULT_PREFERENCE_IDS,
  ])
  const [tasteProfile, setTasteProfile] = useState<UserTasteProfile>(EMPTY_TASTE_PROFILE)
  const [budget, setBudget] = useStoredState<number | null>('meant.budget', DEFAULT_BUDGET)
  const [deliveryLocations, setDeliveryLocations] = useStoredState<UserLocation[]>(
    'meant.locations',
    initialDeliveryLocations(),
  )
  const [clothingFit, setClothingFit] = useStoredState<ClothingFit>('meant.clothingFit', 'none')
  const [checkoutMerchant, setCheckoutMerchant] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState<{ merchant: string; message: string } | null>(
    null,
  )
  const [orders, setOrders] = useState<Order[]>([])
  const [ordersLoading, setOrdersLoading] = useState(false)
  const [ordersError, setOrdersError] = useState<string | null>(null)
  const [lastPlaced, setLastPlaced] = useState<string | null>(null)
  const [user, setUser] = useStoredState<UserAccount>('meant.user', DEFAULT_USER)
  const [cartPeek, setCartPeek] = useState(false)
  const [accountMenu, setAccountMenu] = useState(false)
  const searchRequestRef = useRef(0)
  const searchAbortRef = useRef<AbortController | null>(null)
  const searchSuggestionsRequestRef = useRef(0)
  const inventoryRequestRef = useRef(0)
  const ordersRequestRef = useRef(0)
  const compareIdsRef = useRef<readonly ProductId[]>(compareIds)
  const savePendingRef = useRef(new Set<ProductId>())
  const allPreferencesRef = useRef<readonly Preference[]>(availablePrefs)
  const greeting = useBrowserGreeting()
  const closeAccountMenu = useCallback(() => {
    setAccountMenu(false)
  }, [])

  const refreshMerchantIdentityLinks = useCallback(async () => {
    if (!authed) {
      setMerchantIdentityLinks([])
      setMerchantIdentityLinksError(null)
      return
    }
    setMerchantIdentityLinksLoading(true)
    setMerchantIdentityLinksError(null)
    try {
      setMerchantIdentityLinks(await getMerchantIdentityLinks())
    } catch {
      setMerchantIdentityLinks([])
      setMerchantIdentityLinksError('Could not load connected stores')
    } finally {
      setMerchantIdentityLinksLoading(false)
    }
  }, [authed])

  const allPreferences = availablePrefs
  const activePreferences = allPreferences.filter((preference) => prefsOn.includes(preference.id))
  const savedSet = useMemo(() => new Set(savedIds), [savedIds])
  const savePendingSet = useMemo(() => new Set(savePendingIds), [savePendingIds])
  const compareSet = useMemo(() => new Set(compareIds), [compareIds])
  const allKnownProducts = useMemo(() => {
    const seen = new Set<ProductId>()
    return [
      ...searchResults,
      ...remoteProducts,
      ...savedProducts,
      ...PRODUCTS,
      ...compareProducts,
    ].filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
  }, [compareProducts, remoteProducts, savedProducts, searchResults])
  const allKnownProductsMap = useMemo(
    () =>
      new Map<ProductId, Product>(
        allKnownProducts.map((product) => [product.id, product] as const),
      ),
    [allKnownProducts],
  )
  const {
    cart,
    cartSnapshots,
    setCart,
    updateStoredCart,
    addProductOfferToCart,
    addToCart,
    removeFromCart,
    updateQty,
    applyCartCode,
    removeCartCode,
    updateDeliveryAddress,
    updateDeliveryOption,
  } = useCartController(allKnownProducts)
  const savedListProducts = useMemo(
    () =>
      savedIds
        .map((id) => allKnownProductsMap.get(id))
        .filter((product): product is Product => Boolean(product)),
    [allKnownProductsMap, savedIds],
  )
  const discoveryProducts = useMemo(() => {
    const seen = new Set<ProductId>()
    return [...savedListProducts, ...remoteProducts].filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
  }, [remoteProducts, savedListProducts])

  const liveProfile = useMemo(
    () => ({
      ...PROFILE,
      name: firstNameFromName(user.name),
    }),
    [user.name],
  )

  const searchActive = Boolean(query || searchLoading || searchError)
  const baseFeed = searchActive ? searchResults : discoveryProducts
  const shippingScopedFeedProducts = productsForLocation(baseFeed, deliveryLocations)
  const unscopedFeedProducts = productsForClothingFit(shippingScopedFeedProducts, clothingFit)
  const selectedMerchant = useMemo(
    () => merchants.find((merchant) => merchant.id === selectedMerchantId) ?? null,
    [merchants, selectedMerchantId],
  )
  const merchantCounts = useMemo(() => {
    const counts = new Map<string, number>()
    merchants.forEach((merchant) => counts.set(merchant.id, 0))
    unscopedFeedProducts.forEach((product) => {
      merchants.forEach((merchant) => {
        if (productForMerchant(product, merchant)) {
          counts.set(merchant.id, (counts.get(merchant.id) ?? 0) + 1)
        }
      })
    })
    return counts
  }, [merchants, unscopedFeedProducts])
  const merchantScopedFeedProducts = selectedMerchant
    ? unscopedFeedProducts
        .map((product) => productForMerchant(product, selectedMerchant))
        .filter((product): product is Product => Boolean(product))
    : unscopedFeedProducts
  const feedProducts = merchantScopedFeedProducts
  const hiddenByShip = baseFeed.length - shippingScopedFeedProducts.length

  const openProduct = useCallback((product: Product, list?: readonly Product[]) => {
    setActiveProduct(product)
    setNavProducts(list ?? [product])
  }, [])

  const navIndex = activeProduct
    ? navProducts.findIndex((candidate) => candidate.id === activeProduct.id)
    : -1
  const canNavPrev = navIndex > 0
  const canNavNext = navIndex >= 0 && navIndex < navProducts.length - 1

  const navigateProduct = useCallback(
    (direction: -1 | 1) => {
      setActiveProduct((current) => {
        if (!current) {
          return current
        }
        const index = navProducts.findIndex((candidate) => candidate.id === current.id)
        if (index < 0) {
          return current
        }
        const nextIndex = index + direction
        if (nextIndex < 0 || nextIndex >= navProducts.length) {
          return current
        }
        return navProducts[nextIndex]
      })
    },
    [navProducts],
  )

  useEffect(() => {
    compareIdsRef.current = compareIds
  }, [compareIds])

  useEffect(() => {
    allPreferencesRef.current = allPreferences
  }, [allPreferences])

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])

  useEffect(() => {
    if (!authed) {
      setMerchants([])
      setMerchantsLoading(false)
      return
    }
    let active = true
    setMerchantsLoading(true)
    getMerchants()
      .then((result) => {
        if (!active) return
        setMerchants(result)
      })
      .catch(() => {
        if (!active) return
        setMerchants([])
      })
      .finally(() => {
        if (!active) return
        setMerchantsLoading(false)
      })
    return () => {
      active = false
    }
  }, [authed])

  useEffect(() => {
    void refreshMerchantIdentityLinks()
  }, [refreshMerchantIdentityLinks])

  useEffect(() => {
    if (!authed) {
      return
    }
    const params = new URLSearchParams(window.location.search)
    const state = params.get('state')
    const code = params.get('code')
    const issuer = params.get('iss')
    if (!state || !code) {
      return
    }
    // Strip the OAuth params from the URL immediately, before the exchange. This keeps the authorization
    // code out of the address bar and ensures a second run of this effect (e.g. React StrictMode's
    // double-invoke, or a refresh) sees no code and bails, so the same code is never exchanged twice.
    // Other query params are preserved.
    params.delete('state')
    params.delete('code')
    params.delete('iss')
    const remainingSearch = params.toString()
    window.history.replaceState(
      {},
      document.title,
      window.location.pathname + (remainingSearch ? `?${remainingSearch}` : ''),
    )
    let active = true
    completeMerchantIdentityAuthorization({ state, code, issuer })
      .then((link) => {
        if (!active) return
        setMerchantIdentityLinks((current) => [
          link,
          ...current.filter((candidate) => candidate.merchantId !== link.merchantId),
        ])
        setMerchantIdentityLinksError(null)
      })
      .catch(() => {
        if (!active) return
        setMerchantIdentityLinksError('Could not complete store connection')
      })
    return () => {
      active = false
    }
  }, [authed])

  useEffect(() => {
    if (selectedMerchantId && merchants.length > 0 && !selectedMerchant) {
      setSelectedMerchantId(null)
    }
  }, [merchants.length, selectedMerchant, selectedMerchantId, setSelectedMerchantId])

  // Once authenticated, hydrate the profile from the backend (which creates the users row on first
  // call). Falls back to the JWT email if the backend is unreachable so the shell still renders.
  // Keyed on the user identity rather than the whole session object so periodic token refreshes
  // (which replace `session` hourly) don't trigger a redundant re-fetch.
  const userId = session?.user?.id
  const userEmail = session?.user?.email
  const loadInventory = useCallback(
    async (options?: { silent?: boolean }) => {
      if (!userId) {
        setInventoryItems([])
        setInventoryError(null)
        return
      }
      const requestId = inventoryRequestRef.current + 1
      inventoryRequestRef.current = requestId
      if (!options?.silent) {
        setInventoryLoading(true)
      }
      setInventoryError(null)
      try {
        const items = await getUserInventoryItems()
        if (inventoryRequestRef.current !== requestId) {
          return
        }
        setInventoryItems(items)
      } catch {
        if (inventoryRequestRef.current !== requestId) {
          return
        }
        setInventoryError('Could not load inventory')
      } finally {
        if (inventoryRequestRef.current === requestId && !options?.silent) {
          setInventoryLoading(false)
        }
      }
    },
    [userId],
  )

  const loadOrders = useCallback(
    async (options?: { silent?: boolean }) => {
      if (!userId) {
        setOrders([])
        setOrdersError(null)
        setOrdersLoading(false)
        return
      }
      const requestId = ordersRequestRef.current + 1
      ordersRequestRef.current = requestId
      if (!options?.silent) {
        setOrdersLoading(true)
      }
      setOrdersError(null)
      try {
        const result = await getOrders()
        if (ordersRequestRef.current !== requestId) {
          return
        }
        setOrders(result.map(orderFromProfile))
      } catch {
        if (ordersRequestRef.current !== requestId) {
          return
        }
        setOrdersError('Could not load orders')
        setOrders([])
      } finally {
        if (ordersRequestRef.current === requestId && !options?.silent) {
          setOrdersLoading(false)
        }
      }
    },
    [userId],
  )

  useEffect(() => {
    if (!authed) {
      setInventoryItems([])
      setInventoryError(null)
      setInventoryLoading(false)
      return
    }
    void loadInventory()
  }, [authed, loadInventory])

  useEffect(() => {
    if (!authed) {
      setOrders([])
      setOrdersError(null)
      setOrdersLoading(false)
      return
    }
    void loadOrders()
  }, [authed, loadOrders])

  const refreshSearchSuggestions = useCallback(async () => {
    if (!userId) {
      setSearchSuggestions([])
      return
    }

    const requestId = searchSuggestionsRequestRef.current + 1
    searchSuggestionsRequestRef.current = requestId
    try {
      const result = await getUserProductSearchSuggestions()
      if (searchSuggestionsRequestRef.current !== requestId) {
        return
      }
      setSearchSuggestions(completeSearchSuggestions(result.suggestions))
    } catch {
      if (searchSuggestionsRequestRef.current !== requestId) {
        return
      }
      setSearchSuggestions([...PROMPTS])
    }
  }, [userId])

  useEffect(() => {
    void refreshSearchSuggestions()
  }, [refreshSearchSuggestions])

  useEffect(() => {
    if (!userId) {
      return
    }
    let active = true
    getCurrentUser()
      .then(async (profile) => {
        if (!active) return
        const avatar = await getProfilePictureUrl(profile.profilePicturePath).catch(() => null)
        if (!active) return
        const fullName = [profile.firstName, profile.surname].filter(Boolean).join(' ').trim()
        setUser((current) => ({
          ...current,
          name: fullName || current.name,
          email: profile.email || current.email,
          avatar,
          avatarPath: profile.profilePicturePath ?? null,
        }))
      })
      .catch(() => {
        if (!active) return
        if (userEmail) {
          setUser((current) => ({ ...current, email: userEmail }))
        }
      })
    getUserSettings()
      .then((settings) => {
        if (!active) return
        applySettingsPayload(
          settings,
          setAvailablePrefs,
          setPrefsOn,
          setBudget,
          setDeliveryLocations,
          setClothingFit,
        )
      })
      .catch(() => {
        if (!active) return
        setAvailablePrefs([...PREFERENCES])
      })
    getUserTasteProfile()
      .then((profile) => {
        if (!active) return
        setTasteProfile(profile)
      })
      .catch(() => {
        if (!active) return
        setTasteProfile(EMPTY_TASTE_PROFILE)
      })
    getProductDiscovery()
      .then((discovery) => {
        if (!active) return
        const snapshots = discovery.savedProducts.map((product) =>
          savedProductFromProfile(product, allPreferencesRef.current),
        )
        const recentSnapshots = discovery.recentProducts.map((product) =>
          productFromSearchResult(product, allPreferencesRef.current),
        )
        setSavedProducts(snapshots)
        setSavedIds(snapshots.map((product) => product.id))
        setRemoteProducts((current) => {
          const byId = new Map(current.map((product) => [product.id, product]))
          recentSnapshots.forEach((product) => byId.set(product.id, product))
          return Array.from(byId.values())
        })
      })
      .catch(() => {
        if (!active) return
        setSavedProducts([])
        setSavedIds([])
      })
    return () => {
      active = false
    }
  }, [userId, userEmail, setUser, setPrefsOn, setBudget, setDeliveryLocations, setClothingFit])

  const handleSignOut = () => {
    void signOut()
    setAuthMode('signin')
    setView('discover')
    setQuery('')
    setReply(null)
    setSearchResults([])
    setRemoteProducts([])
    setSearchError(null)
    setSearchLoading(false)
    setSearchLoadingMore(false)
    setSearchHasMore(false)
    setSearchNextOffset(null)
    setSearchMerchantId(null)
    setProductSearchActivities([])
    setTasteProfile(EMPTY_TASTE_PROFILE)
    searchRequestRef.current += 1
    searchAbortRef.current?.abort()
    searchAbortRef.current = null
    searchSuggestionsRequestRef.current += 1
    setSearchSuggestions([])
    setSelectedMerchantId(null)
    setMerchantIdentityLinks([])
    setMerchantIdentityLinksError(null)
    setMerchantIdentityLinksLoading(false)
    setSavedIds([])
    setSavedProducts([])
    savePendingRef.current.clear()
    setSavePendingIds([])
  }

  const connectMerchantIdentity = (merchant: MerchantProfile) => {
    setMerchantIdentityLinksError(null)
    void startMerchantIdentityAuthorization(merchant.id)
      .then((authorization) => {
        window.location.assign(authorization.authorizationUrl)
      })
      .catch(() => {
        setMerchantIdentityLinksError(`Could not start account linking for ${merchant.name}`)
      })
  }

  const revokeMerchantIdentity = (merchantId: string) => {
    setMerchantIdentityLinksError(null)
    setMerchantIdentityLinks((current) => current.filter((link) => link.merchantId !== merchantId))
    void revokeMerchantIdentityLink(merchantId)
      .then(() => refreshMerchantIdentityLinks())
      .catch(() => {
        setMerchantIdentityLinksError('Could not revoke store connection')
        void refreshMerchantIdentityLinks()
      })
  }

  const nav = useCallback(
    (next: View) => {
      setView(next)
      setCartPeek(false)
      setAccountMenu(false)
      if (next === 'orders') {
        void loadOrders({ silent: true })
      }
      if (next !== 'orders') {
        setLastPlaced(null)
      }
      window.scrollTo({ top: 0 })
    },
    [loadOrders],
  )

  const addMessageToShelf = useCallback(
    (payload: Extract<ShelfDragPayload, { kind: 'message' }>) => {
      setShelf((current) => {
        if (
          current.some((item) => item.kind === 'message' && item.messageId === payload.messageId)
        ) {
          return current.map((item) =>
            item.kind === 'message' && item.messageId === payload.messageId
              ? { ...item, snapshot: payload.snapshot }
              : item,
          )
        }
        return [
          ...current,
          {
            uid: nextShelfUid(),
            kind: 'message',
            messageId: payload.messageId,
            collapsed: false,
            snapshot: payload.snapshot,
          },
        ]
      })
    },
    [setShelf],
  )

  const addProductToShelf = useCallback(
    (snapshot: ShelfProductSnapshot) => {
      setShelf((current) => {
        if (
          current.some((item) => item.kind === 'product' && item.productId === snapshot.productId)
        ) {
          return current.map((item) =>
            item.kind === 'product' && item.productId === snapshot.productId
              ? { ...item, snapshot }
              : item,
          )
        }
        return [
          ...current,
          {
            uid: nextShelfUid(),
            kind: 'product',
            productId: snapshot.productId,
            collapsed: false,
            snapshot,
          },
        ]
      })
    },
    [setShelf],
  )

  const removeShelfItem = useCallback(
    (uid: string) => {
      setShelf((current) => current.filter((item) => item.uid !== uid))
    },
    [setShelf],
  )

  const toggleShelfItemCollapse = useCallback(
    (uid: string) => {
      setShelf((current) =>
        current.map((item) => (item.uid === uid ? { ...item, collapsed: !item.collapsed } : item)),
      )
    },
    [setShelf],
  )

  const flashShelfMessage = useCallback((messageId: string) => {
    setShelfFlashMessageId(messageId)
    window.setTimeout(() => setShelfFlashMessageId(null), 2200)
  }, [])

  const findShelfMessage = useCallback(
    (messageId: string) => {
      nav('discover')
      setDiscoverFindRequest({
        id: `shelf-find-message-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`,
        kind: 'message',
        messageId,
      })
    },
    [nav],
  )

  const findShelfProduct = useCallback(
    (productId: ProductId) => {
      nav('discover')
      setDiscoverFindRequest({
        id: `shelf-find-product-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`,
        kind: 'product',
        productId,
      })
    },
    [nav],
  )

  const sendProductQuestionToDiscover = useCallback(
    (product: Product, question: string) => {
      setActiveProduct(null)
      setProductDetailChatRequest({
        id: `detail-chat-${Date.now().toString(36)}`,
        product,
        question,
      })
      nav('discover')
    },
    [nav],
  )

  const beginSaveOperation = (id: ProductId) => {
    if (savePendingRef.current.has(id)) {
      return false
    }
    savePendingRef.current.add(id)
    setSavePendingIds(Array.from(savePendingRef.current))
    return true
  }

  const endSaveOperation = (id: ProductId) => {
    savePendingRef.current.delete(id)
    setSavePendingIds(Array.from(savePendingRef.current))
  }

  const toggleSave = (product: Product) => {
    if (!beginSaveOperation(product.id)) {
      return
    }
    const productSnapshot = productWithCuratedFields(product, allPreferencesRef.current)
    const wasSaved = savedSet.has(product.id)
    if (wasSaved) {
      setSavedIds((current) => current.filter((candidate) => candidate !== product.id))
      setSavedProducts((current) => current.filter((candidate) => candidate.id !== product.id))
      void removeSavedProduct(product.id)
        .catch(() => {
          setSavedProducts((current) => upsertProductSnapshot(current, productSnapshot))
          setSavedIds((current) =>
            current.includes(product.id) ? current : [product.id, ...current],
          )
        })
        .finally(() => endSaveOperation(product.id))
      return
    }

    setSavedProducts((current) => upsertProductSnapshot(current, productSnapshot))
    setSavedIds((current) => (current.includes(product.id) ? current : [product.id, ...current]))
    void saveUserProduct(savedProductInput(product, allPreferencesRef.current))
      .then((savedProduct) => {
        const snapshot = savedProductFromProfile(savedProduct, allPreferencesRef.current)
        setSavedProducts((current) => upsertProductSnapshot(current, snapshot))
        setSavedIds((current) =>
          current.includes(snapshot.id) ? current : [snapshot.id, ...current],
        )
        void refreshTasteProfile()
      })
      .catch(() => {
        setSavedProducts((current) => current.filter((candidate) => candidate.id !== product.id))
        setSavedIds((current) => current.filter((candidate) => candidate !== product.id))
      })
      .finally(() => endSaveOperation(product.id))
  }

  const commitCompareProducts = (nextIds: ProductId[], product?: Product) => {
    compareIdsRef.current = nextIds
    setCompareIds(nextIds)
    setCompareProducts((current) => {
      const withProduct = product ? upsertProductSnapshot(current, product) : current
      return productSnapshotsForIds(withProduct, nextIds)
    })
  }

  const addProductToCompare = (product: Product) => {
    const current = [...compareIdsRef.current]
    const next = current.includes(product.id)
      ? current
      : current.length < 4
        ? [...current, product.id]
        : [...current.slice(1), product.id]

    commitCompareProducts(next, product)
  }

  const handleProductCompare = (product: Product) => {
    if (compareIdsRef.current.includes(product.id)) {
      setActiveProduct(null)
      nav('compare')
      return
    }
    addProductToCompare(product)
  }

  const removeCompareProduct = (index: number) => {
    const next = compareIdsRef.current.filter((_, currentIndex) => currentIndex !== index)
    commitCompareProducts(next)
  }

  const addCompareProduct = (product: Product) => {
    if (!savedSet.has(product.id)) {
      return
    }
    const current = [...compareIdsRef.current]
    const next = current.includes(product.id) ? current : [...current, product.id].slice(0, 4)
    commitCompareProducts(next, product)
  }

  const applySavedSettings = (settings: UserSettingsProfile) => {
    applySettingsPayload(
      settings,
      setAvailablePrefs,
      setPrefsOn,
      setBudget,
      setDeliveryLocations,
      setClothingFit,
    )
  }

  const refreshTasteProfile = useCallback(async () => {
    if (!userId) {
      setTasteProfile(EMPTY_TASTE_PROFILE)
      return
    }
    try {
      setTasteProfile(await getUserTasteProfile())
    } catch {
      setTasteProfile(EMPTY_TASTE_PROFILE)
    }
  }, [userId])

  const saveSettings = async (input: {
    budget?: number | null
    clothingFit?: ClothingFit
    locations?: readonly UserLocation[]
    filterIds?: readonly PreferenceId[]
    preferenceDescription?: string
  }) => {
    try {
      applySavedSettings(await updateUserSettings(input))
      void refreshSearchSuggestions()
      void refreshTasteProfile()
      return true
    } catch {
      return false
    }
  }

  const applyDescription = async (text: string) => {
    return saveSettings({
      filterIds: prefsOn,
      preferenceDescription: text,
    })
  }

  const acceptTasteSuggestion = (filterId: string) => {
    void acceptUserTasteSuggestion(filterId).then((settings) => {
      applySavedSettings(settings)
      void refreshTasteProfile()
    })
  }

  const rejectTasteSuggestion = (filterId: string) => {
    setTasteProfile((current) => ({
      ...current,
      suggestions: current.suggestions.filter((suggestion) => suggestion.filterId !== filterId),
    }))
    void rejectUserTasteSuggestion(filterId)
      .then(() => refreshTasteProfile())
      .catch(() => refreshTasteProfile())
  }

  const disableTasteSignal = (signalId: string, disabled: boolean) => {
    setTasteProfile((current) => ({
      ...current,
      signals: current.signals.map((signal) =>
        signal.id === signalId ? { ...signal, status: disabled ? 'DISABLED' : 'ACTIVE' } : signal,
      ),
    }))
    void updateUserTasteSignal({ signalId, disabled })
      .then((updated) => {
        setTasteProfile((current) => ({
          ...current,
          signals: current.signals.map((signal) => (signal.id === updated.id ? updated : signal)),
        }))
      })
      .catch(() => refreshTasteProfile())
  }

  const removeTasteSignal = (signalId: string) => {
    setTasteProfile((current) => ({
      ...current,
      signals: current.signals.filter((signal) => signal.id !== signalId),
    }))
    void removeUserTasteSignal(signalId).catch(() => refreshTasteProfile())
  }

  const addInventoryItem = async (input: UserInventoryItemInput) => {
    const item = await createUserInventoryItem(input)
    setInventoryItems((current) => upsertInventorySnapshot(current, item))
    return item
  }

  const addInventoryPhotoItem = async (input: UserInventoryPhotoInput) => {
    const item = await createUserInventoryPhotoItem(input)
    setInventoryItems((current) => upsertInventorySnapshot(current, item))
    return item
  }

  const editInventoryItem = async (itemId: string, item: UserInventoryItemUpdateInput) => {
    const updated = await updateUserInventoryItem({ itemId, item })
    setInventoryItems((current) => upsertInventorySnapshot(current, updated))
    return updated
  }

  const removeInventoryItem = async (itemId: string) => {
    await deleteUserInventoryItem(itemId)
    setInventoryItems((current) => current.filter((item) => item.id !== itemId))
  }

  const downloadInventory = async () => {
    const exported = await exportUserInventory()
    const blob = new Blob([JSON.stringify(exported, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `meant-inventory-${new Date(exported.exportedAt).toISOString().slice(0, 10)}.json`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  }

  const runProductSearch = async (
    nextQuery: string,
    options?: { append?: boolean; offset?: number; merchantId?: string | null },
  ) => {
    const submittedQuery = nextQuery.trim()
    if (!submittedQuery) {
      return
    }
    const append = options?.append === true
    const offset = options?.offset ?? 0
    const merchantId = append
      ? (options?.merchantId ?? searchMerchantId)
      : (selectedMerchant?.id ?? null)
    const merchantAtSubmit = merchants.find((merchant) => merchant.id === merchantId) ?? null
    const requestId = searchRequestRef.current + 1
    searchRequestRef.current = requestId
    searchAbortRef.current?.abort()
    const controller = new AbortController()
    searchAbortRef.current = controller
    setQuery(submittedQuery)
    setSearchError(null)
    if (append) {
      setProductSearchActivities([])
      setSearchLoadingMore(true)
    } else {
      setReply(null)
      setSearchLoading(true)
      setSearchLoadingMore(false)
      setSearchResults([])
      setSearchHasMore(false)
      setSearchNextOffset(null)
      setSearchMerchantId(merchantId)
      setProductSearchActivities([])
    }
    const streamedProductIds = new Set<ProductId>()
    const upsertStreamProduct = (
      event: UserProductSearchStreamEventProfile,
      stage: ProductAgentStage,
    ) => {
      if (searchRequestRef.current !== requestId || !event.product) {
        return
      }
      const product = productFromSearchResult(event.product, allPreferences, stage)
      if (isRenderableSearchProduct(product)) {
        streamedProductIds.add(product.id)
        setSearchResults((current) => appendProductSnapshots(current, [product]))
        setRemoteProducts((current) => appendProductSnapshots(current, [product]))
      }
      setProductSearchActivities((current) => upsertAgentActivity(current, event))
    }
    const orderedStreamProducts = (
      event: UserProductSearchStreamEventProfile,
      stage: ProductAgentStage,
    ) => event.products.map((product) => productFromSearchResult(product, allPreferences, stage))
    const noteFinalStreamProducts = (products: readonly Product[]) => {
      if (products.length === 0) {
        return streamedProductIds.size
      }
      streamedProductIds.clear()
      products.forEach((product) => streamedProductIds.add(product.id))
      return products.length
    }
    const finalStreamProducts = (current: Product[], products: readonly Product[]) => {
      if (products.length === 0) {
        return current
      }
      if (!append) {
        return [...products]
      }
      return appendProductSnapshots(
        current.filter(
          (product) => product.agentStage !== 'candidate' && product.agentStage !== 'curating',
        ),
        products,
      )
    }
    try {
      await streamUserProductSearch(
        {
          query: submittedQuery,
          merchantId,
          offset,
          limit: PRODUCT_SEARCH_PAGE_SIZE,
          signal: controller.signal,
        },
        {
          onPhase: (event) => {
            if (searchRequestRef.current !== requestId) {
              return
            }
            setProductSearchActivities((current) => advanceAgentActivity(current, event))
          },
          onProduct: (event) => {
            upsertStreamProduct(event, 'candidate')
          },
          onProductUpdate: (event) => {
            upsertStreamProduct(event, event.agent === 'discovery' ? 'enriched' : 'curating')
          },
          onRankUpdate: (event) => {
            if (searchRequestRef.current !== requestId) {
              return
            }
            const products = orderedStreamProducts(event, 'curated')
            noteFinalStreamProducts(products)
            setSearchResults((current) => finalStreamProducts(current, products))
            setRemoteProducts((current) => appendProductSnapshots(current, products))
            setProductSearchActivities((current) => upsertAgentActivity(current, event))
          },
          onDone: (event) => {
            if (searchRequestRef.current !== requestId) {
              return
            }
            const products = orderedStreamProducts(event, 'curated')
            const displayedCount = noteFinalStreamProducts(products)
            setSearchResults((current) => finalStreamProducts(current, products))
            setRemoteProducts((current) => appendProductSnapshots(current, products))
            setSearchHasMore(Boolean(event.hasMore))
            setSearchNextOffset(event.nextOffset)
            setSearchMerchantId(merchantId)
            setProductSearchActivities((current) =>
              upsertAgentActivity(
                current.map((activity) => ({ ...activity, state: 'done' })),
                event,
                'done',
              ),
            )
            if (append) {
              setReply(
                displayedCount > 0
                  ? `Loaded ${displayedCount} more match${displayedCount === 1 ? '' : 'es'} for "${submittedQuery}".`
                  : `No more matches found for "${submittedQuery}".`,
              )
            } else {
              setReply(
                event.cached
                  ? `Showing ${displayedCount} cached match${displayedCount === 1 ? '' : 'es'} for "${submittedQuery}".`
                  : `Found ${displayedCount} match${displayedCount === 1 ? '' : 'es'} for "${submittedQuery}"${merchantAtSubmit ? ` on ${merchantAtSubmit.name}` : ''}.`,
              )
            }
          },
          onError: (message) => {
            if (searchRequestRef.current !== requestId) {
              return
            }
            setSearchError(message)
            setProductSearchActivities((current) =>
              upsertAgentActivity(
                current,
                {
                  agent: 'search',
                  label: message,
                },
                'error',
              ),
            )
          },
        },
      )
    } catch {
      if (searchRequestRef.current !== requestId) {
        return
      }
      if (controller.signal.aborted) {
        return
      }
      if (!append) {
        setSearchResults([])
        setSearchHasMore(false)
        setSearchNextOffset(null)
      }
      setSearchError(
        append
          ? 'Could not load more products. Please try again.'
          : 'Product search failed. Please try again.',
      )
    } finally {
      if (searchRequestRef.current === requestId) {
        if (searchAbortRef.current === controller) {
          searchAbortRef.current = null
        }
        if (append) {
          setSearchLoadingMore(false)
        } else {
          setSearchLoading(false)
        }
      }
    }
  }

  const loadMoreSearchResults = () => {
    if (
      !query ||
      !searchHasMore ||
      searchNextOffset === null ||
      searchLoading ||
      searchLoadingMore
    ) {
      return
    }
    void runProductSearch(query, {
      append: true,
      offset: searchNextOffset,
      merchantId: searchMerchantId,
    })
  }

  const applyAssistantProducts = (products: readonly Product[], sourceQuery: string) => {
    searchAbortRef.current?.abort()
    searchAbortRef.current = null
    setView('discover')
    setQuery(sourceQuery)
    setReply(
      `Ask Meant found ${products.length} match${products.length === 1 ? '' : 'es'} for "${sourceQuery}".`,
    )
    setSearchError(null)
    setSearchLoading(false)
    setSearchLoadingMore(false)
    setProductSearchActivities([])
    setSearchHasMore(false)
    setSearchNextOffset(null)
    setSearchMerchantId(null)
    setSearchResults([...products])
    setRemoteProducts((current) => {
      const byId = new Map(current.map((product) => [product.id, product]))
      products.forEach((product) => byId.set(product.id, product))
      return Array.from(byId.values())
    })
  }

  const compareChatProducts = (products: readonly Product[]) => {
    const nextProducts = products.slice(0, 4)
    if (nextProducts.length < 2) {
      return
    }
    const nextIds = nextProducts.map((product) => product.id)
    compareIdsRef.current = nextIds
    setCompareIds(nextIds)
    setCompareProducts((current) => {
      const byId = new Map(current.map((product) => [product.id, product]))
      nextProducts.forEach((product) => byId.set(product.id, product))
      return productSnapshotsForIds(Array.from(byId.values()), nextIds)
    })
    nav('compare')
  }

  const checkout = async (payload: CheckoutPayload) => {
    const merchant = payload.merchant ?? payload.items[0]?.merchant ?? 'merchant'
    const cartId = payload.items.find((item) => item.cartId)?.cartId
    const payloadCheckoutUrl = firstUrl(
      payload.checkoutUrl,
      payload.items.find((item) => item.checkoutUrl)?.checkoutUrl,
    )
    const payloadContinueUrl = firstUrl(
      payload.continueUrl,
      payload.items.find((item) => item.continueUrl)?.continueUrl,
    )
    if (!cartId) {
      setCheckoutError({
        merchant,
        message: 'Checkout is not available until this merchant cart syncs.',
      })
      return
    }
    setCheckoutMerchant(merchant)
    setCheckoutError(null)
    try {
      let checkoutProfile: CheckoutProfile | null = null
      let checkoutUrl = firstUrl(payloadContinueUrl, payloadCheckoutUrl)
      if (!checkoutUrl) {
        checkoutProfile = await getCartCheckout({ cartId })
        checkoutUrl = firstUrl(checkoutProfile.continueUrl, checkoutProfile.checkoutUrl)
      }
      if (!checkoutUrl) {
        throw new Error('Missing checkout URL')
      }
      const nextCheckoutUrl = checkoutProfile?.checkoutUrl ?? payloadCheckoutUrl
      const nextContinueUrl = checkoutProfile?.continueUrl ?? payloadContinueUrl

      const checkoutItems = new Set(payload.items.map((item) => `${item.id}:${item.merchant}`))
      updateStoredCart((current) =>
        current.map((item) =>
          checkoutItems.has(`${item.id}:${item.merchant}`)
            ? {
                ...item,
                remoteCartId: checkoutProfile?.remoteCartId ?? item.remoteCartId,
                checkoutUrl: nextCheckoutUrl ?? item.checkoutUrl,
                continueUrl: nextContinueUrl ?? item.continueUrl,
                syncError: null,
              }
            : item,
        ),
      )

      // `window.open` with `noopener` returns null even on success, so its
      // return value can't tell us whether the tab opened. Open via a detached
      // anchor instead — this reliably opens a new tab and never falls through
      // to navigating the current page.
      const opener = document.createElement('a')
      opener.href = checkoutUrl
      opener.target = '_blank'
      opener.rel = 'noopener noreferrer'
      // Firefox/older Safari only act on a click if the anchor is in the
      // document, so attach it briefly and remove it right after.
      document.body.appendChild(opener)
      opener.click()
      opener.remove()
      void loadInventory({ silent: true })
      void loadOrders({ silent: true })
    } catch {
      setCheckoutError({
        merchant,
        message: 'Could not open merchant checkout. Try again.',
      })
    } finally {
      setCheckoutMerchant(null)
    }
  }

  const checkoutInChat = async (payload: CheckoutPayload) => {
    if (payload.items.length === 0) {
      return
    }
    const order = createOrder(payload)
    const checkoutItems = new Set(payload.items.map((item) => `${item.id}:${item.merchant}`))
    setOrders((current) => [order, ...current])
    setCart((current) =>
      current.filter((item) => !checkoutItems.has(`${item.id}:${item.merchant}`)),
    )
    setCheckoutError(null)
    setLastPlaced(order.id)
  }

  const content = (() => {
    if (authLoading) {
      return <div className="mt-auth-loading" />
    }
    if (!authed) {
      return <AuthScreen mode={authMode} onMode={setAuthMode} auth={authActions} />
    }

    switch (view) {
      case 'saved':
        return (
          <SavedView
            products={savedListProducts}
            deliveryLocations={deliveryLocations}
            preferences={allPreferences}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            onOpen={(product) => openProduct(product, savedListProducts)}
            onToggleSave={toggleSave}
          />
        )
      case 'compare':
        return (
          <CompareView
            products={allKnownProducts}
            savedProducts={savedListProducts}
            compareIds={compareIds}
            preferences={allPreferences}
            deliveryLocations={deliveryLocations}
            onRemove={removeCompareProduct}
            onAdd={addCompareProduct}
            onOpen={openProduct}
          />
        )
      case 'inventory':
        return (
          <InventoryView
            items={inventoryItems}
            loading={inventoryLoading}
            error={inventoryError}
            onRefresh={() => void loadInventory()}
            onAddItem={addInventoryItem}
            onAddPhotoItem={addInventoryPhotoItem}
            onUpdateItem={editInventoryItem}
            onDeleteItem={removeInventoryItem}
            onExport={downloadInventory}
          />
        )
      case 'preferences':
        return (
          <PreferencesView
            allPrefs={allPreferences}
            prefsOn={new Set(prefsOn)}
            onToggle={(id) => {
              setPrefsOn((current) => {
                const next = current.includes(id)
                  ? current.filter((candidate) => candidate !== id)
                  : [...current, id]
                void saveSettings({ filterIds: next })
                return next
              })
            }}
            onApplyDescription={applyDescription}
            budget={budget}
            onBudget={(value) => {
              setBudget(value)
              void saveSettings({ budget: value })
            }}
            deliveryLocations={deliveryLocations}
            onDeliveryLocations={(nextLocations) => {
              setDeliveryLocations(nextLocations)
              void saveSettings({ locations: nextLocations })
            }}
            clothingFit={clothingFit}
            onClothingFit={(nextClothingFit) => {
              setClothingFit(nextClothingFit)
              void saveSettings({ clothingFit: nextClothingFit })
            }}
            tasteProfile={tasteProfile}
            onAcceptTasteSuggestion={acceptTasteSuggestion}
            onRejectTasteSuggestion={rejectTasteSuggestion}
            onDisableTasteSignal={disableTasteSignal}
            onRemoveTasteSignal={removeTasteSignal}
            profile={liveProfile}
            onDone={() => nav('discover')}
          />
        )
      case 'cart':
        return (
          <CartView
            cart={cart}
            products={allKnownProducts}
            cartSnapshots={cartSnapshots}
            deliveryLocations={deliveryLocations}
            onRemove={removeFromCart}
            onQty={updateQty}
            onAdd={addToCart}
            onApplyCode={applyCartCode}
            onRemoveCode={removeCartCode}
            onDeliveryAddress={updateDeliveryAddress}
            onDeliveryOption={updateDeliveryOption}
            onCheckout={checkout}
            checkoutMerchant={checkoutMerchant}
            checkoutError={checkoutError}
          />
        )
      case 'orders':
        return (
          <OrdersView
            orders={orders}
            products={allKnownProducts}
            loading={ordersLoading}
            error={ordersError}
            preferences={allPreferences}
            flashId={lastPlaced}
            onOpen={(product) => openProduct(product)}
            onReorder={(order) => {
              setCart((current) => [...current, ...order.items])
              nav('cart')
            }}
          />
        )
      case 'account':
        return (
          <AccountView
            user={user}
            userId={userId}
            merchants={merchants}
            merchantIdentityLinks={merchantIdentityLinks}
            merchantIdentityLinksLoading={merchantIdentityLinksLoading}
            merchantIdentityLinksError={merchantIdentityLinksError}
            onSave={setUser}
            onSignOut={handleSignOut}
            onEditPrefs={() => nav('preferences')}
            onConnectMerchant={connectMerchantIdentity}
            onRevokeMerchant={revokeMerchantIdentity}
            onDone={() => nav('discover')}
          />
        )
      case 'discover':
      default:
        return (
          <ChatDiscoverView
            profile={liveProfile}
            greeting={greeting}
            products={feedProducts}
            hiddenByShip={hiddenByShip}
            agentActivities={productSearchActivities}
            deliveryLocations={deliveryLocations}
            prompts={searchSuggestions}
            reply={reply}
            query={query}
            loading={searchLoading}
            loadingMore={searchLoadingMore}
            hasMore={searchHasMore}
            error={searchError}
            preferences={allPreferences}
            merchants={merchants}
            selectedMerchant={selectedMerchant}
            merchantCounts={merchantCounts}
            totalProductCount={unscopedFeedProducts.length}
            merchantsLoading={merchantsLoading}
            savedProducts={savedListProducts}
            cart={cart}
            cartProducts={allKnownProducts}
            orders={orders}
            shelf={shelf}
            shelfFlashMessageId={shelfFlashMessageId}
            discoverFindRequest={discoverFindRequest}
            productDetailChatRequest={productDetailChatRequest}
            onSubmit={(nextQuery) => {
              void runProductSearch(nextQuery)
            }}
            onLoadMore={loadMoreSearchResults}
            onClear={() => {
              searchRequestRef.current += 1
              searchAbortRef.current?.abort()
              searchAbortRef.current = null
              setReply(null)
              setQuery('')
              setSearchResults([])
              setSearchError(null)
              setSearchLoading(false)
              setSearchLoadingMore(false)
              setSearchHasMore(false)
              setSearchNextOffset(null)
              setSearchMerchantId(null)
              setProductSearchActivities([])
            }}
            onMerchant={(merchant) => {
              setSelectedMerchantId(merchant?.id ?? null)
            }}
            onOpen={(product, products) => openProduct(product, products ?? feedProducts)}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            onToggleSave={toggleSave}
            onAddProductToCart={addProductOfferToCart}
            onFallbackAddToCart={(product, offer) => addToCart(product.id, offer.merchant)}
            onCompareProducts={compareChatProducts}
            onCartQty={updateQty}
            onCartRemove={removeFromCart}
            onCheckout={checkoutInChat}
            onOpenSaved={() => nav('saved')}
            onOpenOrders={() => nav('orders')}
            onOpenPrefs={() => nav('preferences')}
            onOpenCart={() => nav('cart')}
            onOpenShelf={() => setShelfOpen(true)}
            onShelfAddMessage={addMessageToShelf}
            onShelfAddProduct={addProductToShelf}
            onProductDetailChatRequestHandled={(requestId) => {
              setProductDetailChatRequest((current) => (current?.id === requestId ? null : current))
            }}
            onFlashMessage={flashShelfMessage}
          />
        )
    }
  })()

  if (!authed) {
    return content
  }

  const askContext = askContexts[view]
  const assistantCartLines = cartLines(cart, allKnownProducts)
  const cartCount = assistantCartLines.reduce((sum, line) => sum + line.qty, 0)
  const assistantVisibleProducts = (() => {
    switch (view) {
      case 'saved':
        return savedListProducts
      case 'compare':
        return compareIds
          .map((id) => allKnownProductsMap.get(id))
          .filter((product): product is Product => Boolean(product))
      case 'cart':
        return assistantCartLines.map((line) => line.product)
      case 'orders':
        return orders
          .flatMap((order) => order.items)
          .map((item) => allKnownProductsMap.get(item.id))
          .filter((product): product is Product => Boolean(product))
      case 'inventory':
        return []
      case 'discover':
      default:
        return feedProducts
    }
  })()
  const assistantContext: AssistantChatContextInput = {
    view,
    contextLabel: askContext.label,
    currentSearchQuery: query || null,
    selectedMerchantName: selectedMerchant?.name ?? null,
    savedProductCount: savedIds.length,
    cartItemCount: cartCount,
    visibleProducts: assistantVisibleProducts
      .slice(0, 8)
      .map((product) => assistantProductContext(product, deliveryLocations, allPreferences)),
    cartItems: assistantCartLines.slice(0, 8).map((line) => ({
      name: line.product.name,
      merchant: line.merchant,
      quantity: line.qty,
      price: line.price,
    })),
    orders: orders.slice(0, 5).map((order) => ({
      id: order.id,
      date: order.date,
      status: order.status,
      statusNote: order.statusNote,
      itemCount: order.items.reduce((sum, item) => sum + item.qty, 0),
    })),
  }

  return (
    <div className="mt-app">
      <TopBar
        view={view}
        theme={theme}
        user={user}
        savedCount={savedIds.length}
        cart={cart}
        products={allKnownProducts}
        cartSnapshots={cartSnapshots}
        cartPeek={cartPeek}
        accountMenu={accountMenu}
        onNav={nav}
        onToggleTheme={() => setTheme((current) => (current === 'dark' ? 'light' : 'dark'))}
        onToggleCart={() => {
          setCartPeek((current) => !current)
          setAccountMenu(false)
        }}
        onToggleAccount={() => {
          setAccountMenu((current) => !current)
          setCartPeek(false)
        }}
        onCloseAccount={closeAccountMenu}
        onRemoveFromCart={removeFromCart}
        onSignOut={handleSignOut}
      />
      <ProfileBar
        preferences={activePreferences}
        deliveryLocations={deliveryLocations}
        clothingFit={clothingFit}
        onEdit={() => nav('preferences')}
      />
      {content}
      <ProductModal
        product={activeProduct}
        deliveryLocations={deliveryLocations}
        preferences={allPreferences}
        saved={activeProduct ? savedSet.has(activeProduct.id) : false}
        savePending={activeProduct ? savePendingSet.has(activeProduct.id) : false}
        inCompare={activeProduct ? compareSet.has(activeProduct.id) : false}
        onClose={() => setActiveProduct(null)}
        onToggleSave={toggleSave}
        onCompare={handleProductCompare}
        onAddToCart={addProductOfferToCart}
        onAskInChat={sendProductQuestionToDiscover}
        canPrev={canNavPrev}
        canNext={canNavNext}
        onPrev={() => navigateProduct(-1)}
        onNext={() => navigateProduct(1)}
      />
      <FloatingAsk
        contextLabel={askContext.label}
        context={assistantContext}
        suggestions={askContext.suggestions}
        preferences={allPreferences}
        onProducts={applyAssistantProducts}
        onProductOpen={(product) => openProduct(product, [product])}
        onAddProductToCart={addProductOfferToCart}
        hidden={view === 'discover' || Boolean(activeProduct)}
      />
      <Shelf
        open={shelfOpen}
        items={shelf}
        productsById={allKnownProductsMap}
        onToggle={() => setShelfOpen((current) => !current)}
        onAddMessage={addMessageToShelf}
        onAddProduct={addProductToShelf}
        onRemove={removeShelfItem}
        onClear={() => setShelf([])}
        onToggleCollapse={toggleShelfItemCollapse}
        onFind={findShelfMessage}
        onFindProduct={findShelfProduct}
        onOpenProduct={(product) => openProduct(product, [product])}
      />
      <span className="mt-cart-count-debug" aria-hidden>
        {cartCount}
      </span>
    </div>
  )
}
