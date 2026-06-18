import {
  type ChangeEvent,
  type CSSProperties,
  type Dispatch,
  type FormEvent,
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
  DEFAULT_CART,
  DEFAULT_COMPARE,
  DEFAULT_ORDERS,
  DEFAULT_PREFERENCE_IDS,
  DEFAULT_USER,
  LOCATIONS,
  PREFERENCES,
  PRODUCTS,
  PROFILE,
  PROMPTS,
} from './data'
import { type AuthActions, useSupabaseAuth } from './auth/useSupabaseAuth'
import {
  createCart,
  createUserInventoryItem,
  createUserInventoryPhotoItem,
  deleteUserInventoryItem,
  deleteProfilePictureFile,
  exportUserInventory,
  getAssistantConversation,
  getAssistantConversations,
  getCartCheckout,
  getCurrentUser,
  getMerchants,
  getProfilePictureUrl,
  getProductDiscovery,
  getPopularProductSearches,
  getUserInventoryItems,
  getUserProductSearchSuggestions,
  getUserSettings,
  removeProfilePicture,
  removeSavedProduct,
  saveUserProduct,
  searchUserProducts,
  streamAssistantMessage,
  type AssistantChatContextInput,
  type SaveUserProductInput,
  type CartProfile,
  type MerchantProfile,
  type ShoppingFilterProfile,
  type UserInventoryCategory,
  type UserInventoryItemInput,
  type UserInventoryItemProfile,
  type UserInventoryItemUpdateInput,
  type UserInventoryPhotoInput,
  type UserAssistantConversationProfile,
  type UserAssistantConversationSummaryProfile,
  type UserPopularProductSearchProfile,
  type UserSavedProductProfile,
  updateCart,
  updateUserInventoryItem,
  type UserProductSearchProductProfile,
  updateProfile,
  updateProfilePicture,
  updateUserSettings,
  uploadProfilePictureFile,
  type UserSettingsProfile,
} from '../../lib/apiClient'
import type {
  AuthMode,
  CartDeliveryGroup,
  CartDeliveryOption,
  CartItem,
  ClothingFit,
  CheckoutPayload,
  CorePreferenceId,
  Offer,
  Order,
  Preference,
  PreferenceId,
  Product,
  ProductCatalogAttribute,
  ProductCatalogCategory,
  ProductId,
  ProductMedia,
  Theme,
  UserAccount,
  UserLocation,
  View,
} from './types'
import {
  IMPORT_ASK,
  availableOffers,
  bestOffer,
  canMerchantShip,
  cartGroups,
  cartLines,
  computeSmartAlerts,
  cartDeliveryOptionAmount,
  cartDeliveryOptions,
  formatOrderDate,
  listJoin,
  money,
  prefLabel,
  productMerchantCount,
  productPriceFrom,
  productsForLocation,
  productsForClothingFit,
  readStorage,
  resolveAsk,
  selectedCartDeliveryOption,
  writeStorage,
} from './utils'

interface Message {
  role: 'you' | 'ai'
  text: string
  products?: readonly Product[]
  pending?: boolean
}

interface AssistantProductAction {
  product: Product
  shouldAddToCart: boolean
  shouldOpen: boolean
}

interface AskPanelSize {
  width: number
  height: number
}

type AppliedCartCodeType = 'DISCOUNT' | 'GIFT_CARD'

interface AppliedCartCode {
  type: AppliedCartCodeType
  code: string | null
  displayCode: string | null
  label: string | null
  applicable: boolean | null
  amount: number | null
  currency: string | null
}

interface MerchantCartSnapshot {
  merchantKey: string
  merchant: string
  cartId: string | null
  remoteCartId: string | null
  checkoutUrl: string | null
  subtotalAmount: number | null
  totalAmount: number | null
  currency: string | null
  appliedCodes: AppliedCartCode[]
}

interface ApplyCartCodeInput {
  merchantKey: string
  merchant: string
  cartId: string
  code: string
  type: AppliedCartCodeType
}

interface RemoveCartCodeInput {
  merchantKey: string
  merchant: string
  cartId: string
  code: AppliedCartCode
}

interface DeliveryAddressDraft {
  countryCode: string
  city: string
  postalCode: string
  provinceCode: string
}

interface DeliveryAddressPayload extends DeliveryAddressDraft {
  cartId: string
  merchantKey: string
  merchant: string
}

interface DeliveryOptionPayload {
  cartId: string
  merchantKey: string
  merchant: string
  group: CartDeliveryGroup
  option: CartDeliveryOption
}

interface ViewHeadProps {
  eyebrow: string
  title: string
  sub?: string
  right?: ReactNode
}

interface ProductOpenProps {
  onOpen: (product: Product) => void
}

interface ProductSaveProps {
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  onToggleSave: (product: Product) => void
}

const askContexts: Readonly<Record<View, { label: string; suggestions: readonly string[] }>> =
  {
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

interface SearchSuggestion {
  label: string
  detail?: string
  query: string
}

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

const STARTER_SEARCHES: readonly SearchSuggestion[] = [
  {
    label: 'Healthy breakfast',
    detail: 'Low sugar, high protein, organic options',
    query: 'healthy breakfast cereal with low sugar and high protein',
  },
  {
    label: 'Cotton basics',
    detail: 'Natural materials, no polyester, under $50',
    query: 'organic cotton T-shirt under $50 with no polyester',
  },
  {
    label: 'Home upgrades',
    detail: 'Quiet, durable, easy to clean',
    query: 'quiet durable home products that are easy to clean',
  },
  {
    label: 'Travel tech',
    detail: 'Compact USB-C gear for a carry-on',
    query: 'compact USB-C travel tech accessories',
  },
  {
    label: 'Sensitive skin',
    detail: 'Fragrance-free personal care',
    query: 'fragrance-free personal care for sensitive skin',
  },
  {
    label: 'Gift under $50',
    detail: 'Strong reviews and easy returns',
    query: 'highly rated gift under $50 with easy returns',
  },
]

const INVENTORY_CATEGORIES: readonly UserInventoryCategory[] = ['APPAREL', 'PANTRY', 'HOME', 'OTHER']

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

function useStoredState<T>(
  key: string,
  fallback: T,
): readonly [T, Dispatch<SetStateAction<T>>] {
  const fallbackRef = useRef(fallback)
  const [value, setValue] = useState<T>(fallback)
  const [hydrated, setHydrated] = useState(false)

  useEffect(() => {
    setValue(readStorage(key, fallbackRef.current))
    setHydrated(true)
  }, [key])

  useEffect(() => {
    if (hydrated) {
      writeStorage(key, value)
    }
  }, [hydrated, key, value])

  return [value, setValue] as const
}

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function normalizedAskPanelSize(size: AskPanelSize | null | undefined): AskPanelSize {
  const candidate = size && typeof size === 'object'
    ? size as Partial<AskPanelSize>
    : {}
  const width = typeof candidate.width === 'number' && Number.isFinite(candidate.width)
    ? candidate.width
    : ASK_PANEL_DEFAULT_SIZE.width
  const height = typeof candidate.height === 'number' && Number.isFinite(candidate.height)
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
    height: Math.min(ASK_PANEL_MAX_HEIGHT, Math.max(ASK_PANEL_MIN_HEIGHT, window.innerHeight - 118)),
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

  useEffect(() => () => {
    if (timeoutRef.current !== null) {
      window.clearTimeout(timeoutRef.current)
    }
  }, [])

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

function deliveryLocationSummary(locations: readonly UserLocation[]): string {
  if (locations.length === 0) {
    return 'Anywhere'
  }
  const [primary, ...rest] = locations
  const primaryLabel = `${primary.city}, ${primary.country}`
  return rest.length > 0 ? `${primaryLabel} +${rest.length}` : primaryLabel
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
  return value?.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim() ?? ''
}

function parsePriceAmount(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return value > 999 ? value / 100 : value
  }
  const parsed = Number.parseFloat(value.replace(/[^0-9.]+/g, ''))
  return Number.isFinite(parsed) ? parsed : null
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

function searchProductListPrice(product: UserProductSearchProductProfile, currentPrice: number): number | null {
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
  const fallback = [
    product.selectedVariantImageUrl,
    product.detailImageUrl,
    product.imageUrl,
  ]
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

function searchProductCatalogCategories(product: UserProductSearchProductProfile): ProductCatalogCategory[] {
  return (product.categories ?? [])
    .filter((category) => Boolean(category.value))
    .map((category) => ({
      value: category.value ?? '',
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

function searchProductAttributes(product: UserProductSearchProductProfile): ProductCatalogAttribute[] {
  return (product.attributes ?? [])
    .filter((attribute) => Boolean(attribute.name) && Boolean(attribute.value))
    .map((attribute) => ({
      name: attribute.name ?? '',
      value: attribute.value ?? '',
    }))
}

function searchProductCategory(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
): string {
  const catalogCategory = (product.categories ?? []).find((category) => category.value)?.value
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
  const code = Array.from(product.productKey).reduce(
    (sum, char) => sum + char.charCodeAt(0),
    0,
  )
  return tones[code % tones.length] ?? tones[0]
}

function productFromSearchResult(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
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
  return {
    id: product.productKey,
    productHash: product.productHash,
    name: product.title,
    brand,
    category: searchProductCategory(product, preferences),
    tone: toneForSearchProduct(product),
    imageUrl: media.find((item) => item.type.toLowerCase() === 'image')?.url
      || product.imageUrl
      || product.detailImageUrl
      || product.selectedVariantImageUrl,
    productUrl: product.url,
    remote: true,
    match: product.matchScore,
    priceFrom: price,
    listPrice,
    merchants: 1,
    satisfies: product.matchedFilterIds,
    misses: product.missedFilterIds,
    note: product.whyMeantForYou,
    pros: product.matchedFilterIds.length > 0
      ? product.matchedFilterIds.map((id) => `Matches ${prefLabel(preferences, id).toLowerCase()}`)
      : [detail || 'Ranked highly for your search'],
    cons: product.missedFilterIds.map((id) => `May miss ${prefLabel(preferences, id).toLowerCase()}`),
    review: {
      score: ratingScore,
      count: reviewCount,
      insight: detail || product.whyMeantForYou,
    },
    media,
    catalogCategories,
    certifications,
    materials,
    skus,
    collections,
    catalogAttributes,
    offers: [
      {
        merchant: brand,
        price,
        delivery: product.available === false || product.selectedVariantAvailable === false
          ? 'Availability unclear'
          : 'Available from merchant',
        merchantId: product.merchantId,
        merchantDomain: product.merchantDomain,
        productVariantId: product.selectedVariantId,
        variantTitle: product.selectedVariantTitle,
        available: product.available === false || product.selectedVariantAvailable === false
          ? false
          : product.selectedVariantAvailable,
      },
    ],
    inventoryRelationship: product.inventoryRelationship,
    inventoryItemId: product.inventoryItemId,
    inventoryItemName: product.inventoryItemName,
  }
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

function savedProductFromProfile(product: UserSavedProductProfile): Product {
  return {
    id: product.id,
    productHash: product.productHash,
    name: product.name,
    brand: product.brand,
    category: product.category,
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
    needs: product.needs ? product.needs as Product['needs'] : undefined,
    provides: (product.provides?.length ?? 0) > 0 ? product.provides as Product['provides'] : undefined,
  }
}

function searchSuggestionFromPopular(search: UserPopularProductSearchProfile): SearchSuggestion {
  return {
    label: search.displayQuery,
    query: search.query,
  }
}

function findLastAssistantMessageIndex(messages: readonly Message[]): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    if (messages[index]?.role === 'ai') {
      return index
    }
  }
  return -1
}

function savedProductInput(product: Product): SaveUserProductInput {
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
    note: product.note,
    pros: [...product.pros],
    cons: [...product.cons],
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
) {
  return {
    id: product.id,
    name: product.name,
    brand: product.brand,
    category: product.category,
    match: product.match,
    priceFrom: productPriceFrom(product, deliveryLocations),
    note: product.note,
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
    ? items.map((candidate) => candidate.id === item.id ? item : candidate)
    : [item, ...items]
  return [...existing].sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
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
  return products.map((candidate, index) => index === existingIndex ? product : candidate)
}

function productSnapshotsForIds(products: Product[], ids: readonly ProductId[]): Product[] {
  const byId = new Map(products.map((product) => [product.id, product] as const))
  return ids
    .map((id) => byId.get(id))
    .filter((product): product is Product => Boolean(product))
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

function normalizedMerchantName(value: string | null | undefined): string {
  return value?.trim().toLowerCase() ?? ''
}

function merchantNameSet(merchant: MerchantProfile): ReadonlySet<string> {
  return new Set([
    normalizedMerchantName(merchant.name),
    normalizedMerchantName(merchant.domain),
  ].filter(Boolean))
}

function merchantPrimarySearchValues(merchant: MerchantProfile): string[] {
  return [
    normalizedMerchantName(merchant.name),
    normalizedMerchantName(merchant.domain),
  ].filter(Boolean)
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
  const offers = product.offers.filter((offer) =>
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

function cartMerchantKey(input: {
  merchant: string
  merchantId?: string | null
  merchantDomain?: string | null
}): string {
  return input.merchantId || normalizedMerchantName(input.merchantDomain) || normalizedMerchantName(input.merchant)
}

function emptyDeliveryAddressDraft(locations: readonly UserLocation[]): DeliveryAddressDraft {
  const location = locations[0]
  return {
    countryCode: location?.code ?? 'US',
    city: location?.city ?? '',
    postalCode: '',
    provinceCode: '',
  }
}

function deliveryAddressArguments(input: DeliveryAddressDraft): Record<string, unknown> {
  return {
    country_code: input.countryCode.trim().toUpperCase(),
    city: input.city.trim(),
    postal_code: input.postalCode.trim(),
    province_code: input.provinceCode.trim() || undefined,
  }
}

function selectedDeliveryOptionArguments(
  group: CartDeliveryGroup,
  option: CartDeliveryOption | null | undefined,
): Record<string, unknown> | null {
  const deliveryGroupId = group.id || group.handle
  const deliveryOptionHandle = option?.handle
  if (!deliveryGroupId || !deliveryOptionHandle) {
    return null
  }
  return {
    delivery_group_id: deliveryGroupId,
    delivery_option_handle: deliveryOptionHandle,
  }
}

function sameDeliveryGroup(left: CartDeliveryGroup, right: CartDeliveryGroup): boolean {
  if (left.id && right.id) {
    return left.id === right.id
  }
  if (left.handle && right.handle) {
    return left.handle === right.handle
  }
  return left === right
}

function selectedDeliveryOptionsForCart(
  cart: readonly CartItem[],
  merchantKey: string,
  selectedGroup: CartDeliveryGroup,
  selectedOption: CartDeliveryOption,
): Record<string, unknown>[] {
  const groups = (cart.find((item) => cartMerchantKey(item) === merchantKey)?.deliveryGroups ?? [])
    .filter((group): group is CartDeliveryGroup => Boolean(group))
  const selectionGroups = groups.length > 0 ? groups : [selectedGroup]
  return selectionGroups
    .map((group) => selectedDeliveryOptionArguments(
      group,
      sameDeliveryGroup(group, selectedGroup) ? selectedOption : selectedCartDeliveryOption(group),
    ))
    .filter((selection): selection is Record<string, unknown> => Boolean(selection))
}

function formatCartAmount(amount: number, currency?: string | null): string {
  if (!currency || currency.toUpperCase() === 'USD') {
    return money(amount)
  }
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
    }).format(amount)
  } catch {
    return `${amount.toFixed(2)} ${currency}`
  }
}

function deliveryOptionTitle(option: CartDeliveryOption): string {
  return option.title || option.code || option.handle || 'Delivery option'
}

function deliveryOptionSpeed(option: CartDeliveryOption): string | null {
  return option.deliveryEstimate || option.estimatedDeliveryTime || option.description || option.estimatedDeliveryAt || null
}

function deliveryOptionCost(option: CartDeliveryOption, fallbackCurrency?: string | null): string {
  const amount = cartDeliveryOptionAmount(option)
  if (amount === null) {
    return 'Cost at checkout'
  }
  return formatCartAmount(amount, option.cost?.currency ?? fallbackCurrency)
}

function deliveryGroupSummary(
  deliveryGroups: readonly CartDeliveryGroup[],
  fallbackAmount: number,
  fallbackCurrency?: string | null,
): string {
  const selected = deliveryGroups
    .map((group) => selectedCartDeliveryOption(group))
    .filter((option): option is CartDeliveryOption => Boolean(option))
  if (selected.length === 0) {
    return fallbackAmount === 0 ? 'Free delivery' : `${formatCartAmount(fallbackAmount, fallbackCurrency)} delivery`
  }
  return selected.map((option) => {
    const speed = deliveryOptionSpeed(option)
    return `${deliveryOptionTitle(option)} · ${deliveryOptionCost(option, fallbackCurrency)}${speed ? ` · ${speed}` : ''}`
  }).join(' + ')
}

function offerCartable(offer: Offer): boolean {
  return Boolean(
    offer.productVariantId &&
    offer.available !== false &&
    (offer.merchantId || offer.merchantDomain),
  )
}

function cartLineForItem(
  snapshot: CartProfile,
  item: CartItem,
): NonNullable<CartProfile['lines']>[number] | undefined {
  const lines = snapshot.lines ?? []
  return lines.find((line) => {
    return Boolean(
      (item.cartLineId && line.cartLineId === item.cartLineId) ||
      (item.remoteCartLineId && line.remoteCartLineId === item.remoteCartLineId) ||
      (item.productVariantId && line.productVariantId === item.productVariantId),
    )
  })
}

function parseCartAmount(value?: string | null): number | null {
  if (!value) {
    return null
  }
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

function looksLikeGiftCardSuffix(value: string | null): boolean {
  return Boolean(value && /^[a-z0-9]{1,4}$/i.test(value))
}

function cartSnapshotFromProfile(
  snapshot: CartProfile,
  merchantKey: string,
  merchant: string,
): MerchantCartSnapshot {
  return {
    merchantKey,
    merchant,
    cartId: snapshot.cartId ?? null,
    remoteCartId: snapshot.remoteCartId ?? null,
    checkoutUrl: snapshot.checkoutUrl ?? null,
    subtotalAmount: parseCartAmount(snapshot.subtotalAmount),
    totalAmount: parseCartAmount(snapshot.totalAmount),
    currency: snapshot.currency ?? null,
    appliedCodes: (snapshot.appliedCodes ?? [])
      .map((code): AppliedCartCode => {
        const type = code.type === 'GIFT_CARD' ? 'GIFT_CARD' : 'DISCOUNT'
        const displayCode = code.code?.trim() || null
        const transportCode = type === 'GIFT_CARD' && looksLikeGiftCardSuffix(displayCode)
          ? null
          : displayCode
        return {
          type,
          code: transportCode,
          displayCode,
          label: code.label ?? null,
          applicable: code.applicable ?? null,
          amount: parseCartAmount(code.amount),
          currency: code.currency ?? snapshot.currency ?? null,
        }
      })
      .filter((code) => code.code || code.label || code.amount !== null),
  }
}

function cartMoney(value: number, currency?: string | null): string {
  if (!currency || currency === 'USD') {
    return money(value)
  }
  return `${currency} ${value.toFixed(2)}`
}

function appliedCodeDisplay(code: AppliedCartCode): string {
  const codeValue = code.displayCode?.trim() || code.code?.trim()
  if (code.type === 'GIFT_CARD' && codeValue) {
    return `•••• ${codeValue.slice(-4)}`
  }
  const displayValue = code.label?.trim() || (code.type === 'GIFT_CARD' ? 'Gift card' : 'Discount')
  return displayValue
}

function cartSnapshotSavings(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackSubtotal: number,
  fallbackTotal: number,
): number {
  if (!snapshot) {
    return 0
  }
  const codeSavings = snapshot.appliedCodes.reduce((sum, code) => sum + Math.abs(code.amount ?? 0), 0)
  if (codeSavings > 0) {
    return codeSavings
  }
  const subtotal = snapshot.subtotalAmount ?? fallbackSubtotal
  const total = snapshot.totalAmount ?? fallbackTotal
  return Math.max(subtotal - total, 0)
}

function cartSnapshotTotal(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackTotal: number,
): number {
  return snapshot?.totalAmount ?? fallbackTotal
}

function cartSnapshotSubtotal(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackSubtotal: number,
): number {
  return snapshot?.subtotalAmount ?? fallbackSubtotal
}

function mergeCartSnapshot(
  cart: readonly CartItem[],
  merchantKey: string,
  snapshot: CartProfile,
): CartItem[] {
  const deliveryGroups = (snapshot.deliveryGroups as readonly CartDeliveryGroup[] | undefined)
    ?.filter((group): group is CartDeliveryGroup => Boolean(group))
  return cart.map((item) => {
    if (cartMerchantKey(item) !== merchantKey) {
      return item
    }
    const line = cartLineForItem(snapshot, item)
    return {
      ...item,
      merchantId: snapshot.merchantId ?? item.merchantId,
      merchantDomain: snapshot.merchantDomain ?? item.merchantDomain,
      cartId: snapshot.cartId ?? item.cartId,
      remoteCartId: snapshot.remoteCartId ?? item.remoteCartId,
      checkoutUrl: snapshot.checkoutUrl ?? item.checkoutUrl,
      cartLineId: line?.cartLineId ?? item.cartLineId,
      remoteCartLineId: line?.remoteCartLineId ?? item.remoteCartLineId,
      productVariantId: line?.productVariantId ?? item.productVariantId,
      variantTitle: line?.variantTitle ?? item.variantTitle,
      cartTotalAmount: snapshot.totalAmount ?? item.cartTotalAmount,
      cartSubtotalAmount: snapshot.subtotalAmount ?? item.cartSubtotalAmount,
      cartCurrency: snapshot.currency ?? item.cartCurrency,
      deliveryGroups: deliveryGroups ?? item.deliveryGroups ?? [],
      qty: line?.quantity ?? item.qty,
      syncing: false,
      syncError: null,
    }
  })
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

function cartableOfferForProduct(product: Product): Offer | null {
  return product.offers.find(offerCartable) ?? null
}

function SparkMark({ size = 16, color = 'var(--accent)' }: Readonly<{
  size?: number
  color?: string
}>) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden>
      <path
        d="M10 2.5l1.7 4.8 4.8 1.7-4.8 1.7L10 17.5l-1.7-4.8L3.5 11l4.8-1.7L10 2.5z"
        fill={color}
      />
    </svg>
  )
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
        <span className="mt-search-loader-spark"><SparkMark size={14} /></span>
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
      </div>
      <span className="mt-search-loading-text">
        {label}
        <span className="mt-search-loading-dots" aria-hidden="true">
          <span>.</span><span>.</span><span>.</span>
        </span>
      </span>
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

function CartIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden>
      <path
        d="M2 2.5h2.1l1.4 8.2h7.2l1.4-6.1H5.1"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="7" cy="14.6" r="1.15" fill="currentColor" />
      <circle cx="12.6" cy="14.6" r="1.15" fill="currentColor" />
    </svg>
  )
}

function CloseIcon({ size = 16 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" aria-hidden>
      <path
        d="M3 3l10 10M13 3 3 13"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

function ChevronIcon({ direction, size = 18 }: Readonly<{ direction: 'left' | 'right'; size?: number }>) {
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

function Avatar({ user, size = 38 }: Readonly<{
  user: UserAccount
  size?: number
}>) {
  const initial = (user.name.trim().charAt(0) || 'M').toUpperCase()
  if (user.avatar) {
    return (
      <img
        className="mt-ava-img"
        src={user.avatar}
        alt=""
        style={{ width: size, height: size }}
      />
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

function Placeholder({
  label,
  tone,
  radius = 0,
}: Readonly<{
  label: string
  tone: string
  radius?: number
}>) {
  return (
    <div className="mt-ph" style={{ background: tone, borderRadius: radius }}>
      <div className="mt-ph-stripes" />
      <span className="mt-mono mt-ph-label">{label}</span>
    </div>
  )
}

function ProductArtwork({ product, label }: Readonly<{
  product: Product
  label: string
}>) {
  if (product.imageUrl) {
    return (
      <img
        className="mt-product-img"
        src={product.imageUrl}
        alt=""
        loading="lazy"
      />
    )
  }
  return <Placeholder label={label} tone={product.tone} />
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
    <span className={`mt-inv-signal ${compact ? 'compact' : ''} ${product.inventoryRelationship?.toLowerCase()}`}>
      <span className="mt-inv-signal-dot" />
      {label}
      {!compact && product.inventoryItemName ? (
        <span className="mt-inv-signal-item">{product.inventoryItemName}</span>
      ) : null}
    </span>
  )
}

function ViewHead({ eyebrow, title, sub, right }: Readonly<ViewHeadProps>) {
  return (
    <div className="mt-view-head">
      <div>
        <div className="mt-mono mt-view-eyebrow">{eyebrow}</div>
        <h1 className="mt-view-title">{title}</h1>
        {sub ? <p className="mt-view-sub">{sub}</p> : null}
      </div>
      {right}
    </div>
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
                {message.role === 'ai'
                  ? renderAssistantMessageContent(message)
                  : message.text}
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

  useEffect(() => () => {
    if (sentPulseTimeoutRef.current !== null) {
      window.clearTimeout(sentPulseTimeoutRef.current)
    }
  }, [])

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
  const [conversationHistory, setConversationHistory] = useState<UserAssistantConversationSummaryProfile[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [historyLoaded, setHistoryLoaded] = useState(false)
  const [loading, setLoading] = useState(false)
  const [panelSize, setPanelSize] = useStoredState<AskPanelSize>('meant.askPanelSize', ASK_PANEL_DEFAULT_SIZE)
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

  const restoreConversation = useCallback((conversation: UserAssistantConversationProfile) => {
    setConversationId(conversation.conversationId)
    setMessages(messagesFromAssistantConversation(conversation, preferences))
    const summary = assistantConversationSummary(conversation)
    if (summary) {
      setConversationHistory((current) => upsertAssistantConversationSummary(current, summary))
    }
  }, [preferences])

  const loadConversationHistory = useCallback((restoreLatest: boolean) => {
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
  }, [restoreConversation])

  const loadConversation = useCallback((nextConversationId: string) => {
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
  }, [restoreConversation])

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
        width: clampNumber(startSize.width + startX - moveEvent.clientX, ASK_PANEL_MIN_WIDTH, maxSize.width),
        height: clampNumber(startSize.height + startY - moveEvent.clientY, ASK_PANEL_MIN_HEIGHT, maxSize.height),
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
            const title = normalizedTitle.length > 80
              ? `${normalizedTitle.slice(0, 77)}...`
              : normalizedTitle
            const timestamp = new Date().toISOString()
            setConversationHistory((current) => upsertAssistantConversationSummary(current, {
              conversationId: metadataConversationId,
              title,
              createdAt: timestamp,
              updatedAt: timestamp,
            }))
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
    <div className={`mt-fab-wrap ${open ? 'open' : ''} ${loading ? 'busy' : ''} ${hidden ? 'mt-fab-wrap-hidden' : ''}`}>
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
              ) : conversationHistory.map((conversation) => (
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
              ))}
            </div>
          ) : null}
          {messages.length === 0 ? (
            <p className="mt-askpanel-hint">
              Ask anything. I already know your preferences.
            </p>
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
        {open ? <CloseIcon size={18} /> : <><SparkMark size={16} color="#fff" /> <span>Ask Meant</span></>}
      </button>
    </div>
  )
}

function productWasPrice(product: Product, deliveryLocations: readonly UserLocation[]): number | null {
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
      <span className="mt-mono mt-card-from">from</span>{' '}
      <span>{money(price)}</span>
      {wasPrice ? <span className="mt-was-price">{money(wasPrice)}</span> : null}
    </span>
  )
}

function ProductReviewSummary({ product }: Readonly<{ product: Product }>) {
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

function mediaSummary(product: Product): string | null {
  const media = product.media ?? []
  if (media.length <= 1) {
    return null
  }
  const imageCount = media.filter((item) => item.type.toLowerCase() === 'image').length
  const videoCount = media.filter((item) => item.type.toLowerCase() === 'video').length
  const modelCount = media.filter((item) => item.type.toLowerCase().includes('3d') || item.type.toLowerCase() === 'model').length
  return [
    imageCount > 1 ? `${imageCount} images` : null,
    videoCount > 0 ? `${videoCount} video${videoCount === 1 ? '' : 's'}` : null,
    modelCount > 0 ? `${modelCount} 3D` : null,
  ].filter(Boolean).join(' · ') || `${media.length} media`
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
}: Readonly<{
  product: Product
  index: number
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
} & ProductOpenProps & ProductSaveProps>) {
  const open = () => onOpen(product)
  const savePending = savePendingSet.has(product.id)
  const catalogBadges = catalogBadgeLabels(product).slice(0, 3)
  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      open()
    }
  }

  return (
    <div
      className="mt-card mt-card-in"
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
          aria-label={savePending ? 'Saving' : savedSet.has(product.id) ? 'Remove from saved' : 'Save'}
          disabled={savePending}
          onClick={(event) => {
            event.stopPropagation()
            onToggleSave(product)
          }}
        >
          <HeartIcon filled={savedSet.has(product.id)} />
        </button>
      </div>

      <div className="mt-card-body">
        <div className="mt-mono mt-card-brand">{product.brand}</div>
        <InventorySignalBadge product={product} compact />
        <div className="mt-card-name">{product.name}</div>
        <div className="mt-chips">
          {product.satisfies.slice(0, 3).map((id) => (
            <PrefChip
              key={id}
              label={prefLabel(preferences, id)}
              variant="lit"
              small
            />
          ))}
          {product.misses.map((id) => (
            <PrefChip
              key={id}
              label={prefLabel(preferences, id)}
              variant="missed"
              small
            />
          ))}
        </div>
        {catalogBadges.length > 0 ? (
          <div className="mt-catalog-pills">
            {catalogBadges.map((label) => (
              <span className="mt-catalog-pill" key={label}>{label}</span>
            ))}
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
        <div className="mt-card-note">
          <span className="mt-note-key">Why it is meant for you</span>
          {product.note}
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

  useEffect(() => () => {
    if (submittedTimeoutRef.current !== null) {
      window.clearTimeout(submittedTimeoutRef.current)
    }
  }, [])

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

function SearchSuggestionPanel({
  title,
  note,
  searches,
  loading,
  compact,
  onSubmit,
}: Readonly<{
  title: string
  note: string
  searches: readonly SearchSuggestion[]
  loading: boolean
  compact: boolean
  onSubmit: (query: string) => void
}>) {
  return (
    <section className={`mt-starters${compact ? ' compact' : ''}`} aria-label={title}>
      <div className="mt-starters-head">
        <h2 className="mt-starters-title">{title}</h2>
        <span className="mt-mono mt-starters-note">{note}</span>
      </div>
      {searches.length === 0 ? (
        <div className="mt-starters-loading mt-mono">Loading searches</div>
      ) : null}
      <div className="mt-starters-grid">
        {searches.map((search) => (
          <button
            key={search.query}
            className="mt-starter-card"
            type="button"
            onClick={() => onSubmit(search.query)}
            disabled={loading}
          >
            <span className="mt-starter-main">
              <span className="mt-starter-label">{search.label}</span>
              {search.detail ? (
                <span className="mt-starter-detail">{search.detail}</span>
              ) : null}
            </span>
            <span className="mt-starter-arrow" aria-hidden>
              <svg width="15" height="15" viewBox="0 0 18 18" fill="none">
                <path
                  d="M3.5 9h10M9.5 5l4 4-4 4"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}

function RestockNudges({
  items,
  onSubmit,
}: Readonly<{
  items: readonly UserInventoryItemProfile[]
  onSubmit: (query: string) => void
}>) {
  return (
    <section className="mt-restock-band" aria-label="Restock">
      <div className="mt-restock-band-head">
        <span className="mt-mono mt-restock-band-k">Restock</span>
        <span className="mt-restock-band-count">{items.length}</span>
      </div>
      <div className="mt-restock-band-list">
        {items.slice(0, 4).map((item) => (
          <button
            key={item.id}
            className="mt-restock-pill"
            type="button"
            onClick={() => onSubmit(`restock ${item.name}`)}
          >
            <span className="mt-restock-pill-name">{item.name}</span>
            <span className="mt-mono mt-restock-pill-meta">
              {inventoryCategoryLabel(item.category)}
              {item.quantity > 0 ? ` · ${item.quantity}${item.unit ? ` ${item.unit}` : ''}` : ''}
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}

function FeedView({
  profile,
  greeting,
  products,
  restockItems,
  hiddenByShip,
  discoveryLoading,
  discoveryError,
  popularSearches,
  popularSearchesLoading,
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
  merchantsError,
  onSubmit,
  onLoadMore,
  onClear,
  onMerchant,
  onOpen,
  savedSet,
  savePendingSet,
  onToggleSave,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  products: readonly Product[]
  restockItems: readonly UserInventoryItemProfile[]
  hiddenByShip: number
  discoveryLoading: boolean
  discoveryError: string | null
  popularSearches: readonly SearchSuggestion[]
  popularSearchesLoading: boolean
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
  merchantsError: string | null
  onSubmit: (query: string) => void
  onLoadMore: () => void
  onClear: () => void
  onMerchant: (merchant: MerchantProfile | null) => void
} & ProductOpenProps & ProductSaveProps>) {
  const merchantName = selectedMerchant?.name
  const searchActive = Boolean(query || loading || error)
  const preSearch = !searchActive
  const title = query
    ? merchantName ? `Your matches on ${merchantName}` : 'Your matches'
    : merchantName ? `Your context on ${merchantName}` : 'Your saved and recent products'
  const count = loading
    ? 'Searching stores'
    : preSearch
      ? discoveryLoading
        ? 'Loading your context'
        : `${products.length} from your context`
      : loadingMore
        ? `${products.length} shown · loading more`
      : merchantName
        ? `${products.length} on ${merchantName}`
        : `${products.length} shown · sorted by match`
  const waitingForPopularSearches = popularSearchesLoading && popularSearches.length === 0
  const suggestionSearches = waitingForPopularSearches
    ? []
    : popularSearches.length > 0 ? popularSearches : STARTER_SEARCHES
  const suggestionTitle = popularSearches.length > 0 || waitingForPopularSearches
    ? 'What others search for'
    : 'Try a starter search'
  const suggestionNote = popularSearches.length > 0
    ? 'Popular searches from the last 24 hours'
    : waitingForPopularSearches
      ? 'Loading popular searches'
      : 'Searches run across supported merchants'

  return (
    <main className="mt-feed">
      <ChatHero
        profile={profile}
        greeting={greeting}
        prompts={prompts}
        onSubmit={onSubmit}
        loading={loading}
        merchants={merchants}
        selectedMerchant={selectedMerchant}
        merchantCounts={merchantCounts}
        totalProductCount={totalProductCount}
        merchantsLoading={merchantsLoading}
        merchantsError={merchantsError}
        onMerchant={onMerchant}
      />
      {reply ? (
        <div className="mt-reply">
          <div className="mt-reply-av">
            <SparkMark />
          </div>
          <div className="mt-reply-body">
            <div className="mt-mono mt-reply-q">You asked: "{query}"</div>
            <p className="mt-reply-text">{reply}</p>
          </div>
          <button className="mt-reply-clear mt-mono" type="button" onClick={onClear}>
            Back to your feed
          </button>
        </div>
      ) : null}
      {error ? (
        <div className="mt-search-state mt-search-state-error">
          {error}
        </div>
      ) : null}
      {preSearch ? (
        <SearchSuggestionPanel
          title={suggestionTitle}
          note={suggestionNote}
          searches={suggestionSearches}
          loading={loading}
          compact={products.length > 0 || discoveryLoading}
          onSubmit={onSubmit}
        />
      ) : null}
      {preSearch && discoveryError ? (
        <div className="mt-search-state mt-search-state-error">
          {discoveryError}
        </div>
      ) : null}
      {preSearch && restockItems.length > 0 ? (
        <RestockNudges items={restockItems} onSubmit={onSubmit} />
      ) : null}
      <div className="mt-feed-head">
        <h2 className="mt-feed-title">{title}</h2>
        <span className="mt-mono mt-feed-count">{count}</span>
      </div>
      {deliveryLocations.length > 0 ? (
        <div className="mt-ship-strip">
          <span aria-hidden>⌖</span>
          <span>
            Shipping to <strong>{deliveryLocationSummary(deliveryLocations)}</strong>
          </span>
          {hiddenByShip > 0 ? (
            <span className="mt-ship-strip-hidden mt-mono">
              {hiddenByShip} hidden · cannot reach you
            </span>
          ) : null}
        </div>
      ) : null}
      {loading || (preSearch && discoveryLoading && products.length === 0) ? (
        <ProductSearchLoading
          label={loading ? undefined : 'Loading your saved and recent products'}
        />
      ) : products.length > 0 ? (
        <>
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
          {!preSearch && hasMore ? (
            <div className="mt-load-more">
              <button
                className="mt-load-more-btn"
                type="button"
                onClick={onLoadMore}
                disabled={loadingMore}
                aria-busy={loadingMore}
              >
                {loadingMore ? 'Loading more' : 'Load more'}
              </button>
            </div>
          ) : null}
        </>
      ) : merchantName ? (
        <div className="mt-empty">
          <div className="mt-empty-mark"><MerchantIcon /></div>
          <h3 className="mt-empty-title">
            {preSearch ? `No saved or recent products on ${merchantName}` : `Nothing here on ${merchantName}`}
          </h3>
          <p className="mt-empty-sub">
            {preSearch
              ? `Run a search on ${merchantName} to build this view.`
              : `Meant has no currently loaded products from ${merchantName}. Try a search or return to all merchants.`}
          </p>
          <div className="mt-empty-actions">
            <button className="mt-empty-btn ghost" type="button" onClick={() => onMerchant(null)}>
              Search all merchants
            </button>
          </div>
        </div>
      ) : query ? (
        <EmptyState
          title="No products found"
          sub="Try a broader search or adjust your preferences."
          mark={<SparkMark />}
        />
      ) : preSearch ? (
        <EmptyState
          title="No product context yet"
          sub="Run a starter search above or save products you want to revisit."
          mark={<SparkMark />}
        />
      ) : null}
      {preSearch && products.length > 0 ? (
        <p className="mt-mono mt-feed-foot">
          These are only your saved products and recent search results.
        </p>
      ) : null}
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
  canPrev: boolean
  canNext: boolean
  onPrev: () => void
  onNext: () => void
}>) {
  const [messages, setMessages] = useState<Message[]>([])
  const [added, setAdded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)
  const addedTimeoutRef = useRef<number | null>(null)
  const addSelectedOfferRef = useRef<(() => Promise<void>) | null>(null)
  const touchStartRef = useRef<{ x: number; y: number } | null>(null)

  useEffect(() => {
    setMessages([])
    setAdded(false)
    setAdding(false)
    setAddError(null)
  }, [product?.id])

  useEffect(() => () => {
    if (addedTimeoutRef.current !== null) {
      window.clearTimeout(addedTimeoutRef.current)
    }
  }, [])

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
  }, [onClose, product, canNext, canPrev, onNext, onPrev])

  if (!product) {
    return null
  }

  const offers = availableOffers(product, deliveryLocations)
  const visibleOffers = offers.length > 0 ? offers : product.offers
  const modalMedia = (product.media ?? []).slice(0, 4)
  const catalogBadges = catalogBadgeLabels(product)
  const mediaInfo = mediaSummary(product)
  const hasCatalogDetails =
    catalogBadges.length > 0 ||
    (product.skus?.length ?? 0) > 0 ||
    (product.catalogAttributes?.length ?? 0) > 0 ||
    Boolean(mediaInfo)
  const ask = (question: string) => {
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
            <div className="mt-modal-media">
              <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
              <div className="mt-modal-ring">
                <MatchRing value={product.match} size={56} stroke={4} />
              </div>
            </div>
            {modalMedia.length > 1 ? (
              <div className="mt-modal-thumbs">
                {modalMedia.map((item) => (
                  <div className="mt-modal-thumb" key={`${item.type}-${item.url}`}>
                    {item.type.toLowerCase() === 'image' ? (
                      <img src={item.url} alt={item.altText || product.name} loading="lazy" />
                    ) : (
                      <span className="mt-mono">{item.type}</span>
                    )}
                  </div>
                ))}
              </div>
            ) : null}
            <div className="mt-mono mt-card-brand">{product.brand} · {product.category}</div>
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
                  <kbd className="mt-act-key" aria-hidden>↵</kbd>
                ) : null}
              </button>
            </div>
            {addError ? (
              <div className="mt-cart-inline-error">{addError}</div>
            ) : null}
            {!canAddToCart && !addError ? (
              <div className="mt-cart-inline-error muted">
                This offer is not available for merchant checkout.
              </div>
            ) : null}
          </div>

          <div className="mt-modal-right">
            <div className="mt-drawer-note">
              <span className="mt-note-key">Meant's take</span>
              {product.note}
            </div>

            <section className="mt-block">
              <div className="mt-block-label mt-mono">Preference match</div>
              <div className="mt-chips">
                {product.satisfies.map((id) => (
                  <PrefChip key={id} label={prefLabel(preferences, id)} variant="lit" />
                ))}
                {product.misses.map((id) => (
                  <PrefChip key={id} label={prefLabel(preferences, id)} variant="missed" />
                ))}
              </div>
            </section>

            <section className="mt-block">
              <div className="mt-procon">
                <div>
                  <div className="mt-block-label mt-mono">Advantages</div>
                  <ul className="mt-list mt-list-pro">
                    {product.pros.map((pro) => (
                      <li key={pro}>{pro}</li>
                    ))}
                  </ul>
                </div>
                <div>
                  <div className="mt-block-label mt-mono">Trade-offs</div>
                  <ul className="mt-list mt-list-con">
                    {product.cons.map((con) => (
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
              <p className="mt-reviews-insight">{product.review.insight}</p>
            </section>

            {hasCatalogDetails ? (
              <section className="mt-block">
                <div className="mt-block-label mt-mono">Catalog details</div>
                <div className="mt-catalog-details">
                  {catalogBadges.length > 0 ? (
                    <div className="mt-catalog-detail-row">
                      <span className="mt-mono">Signals</span>
                      <div className="mt-catalog-pills">
                        {catalogBadges.slice(0, 6).map((label) => (
                          <span className="mt-catalog-pill" key={label}>{label}</span>
                        ))}
                      </div>
                    </div>
                  ) : null}
                  {(product.skus?.length ?? 0) > 0 ? (
                    <div className="mt-catalog-detail-row">
                      <span className="mt-mono">SKU</span>
                      <span>{product.skus?.slice(0, 3).join(', ')}</span>
                    </div>
                  ) : null}
                  {mediaInfo ? (
                    <div className="mt-catalog-detail-row">
                      <span className="mt-mono">Media</span>
                      <span>{mediaInfo}</span>
                    </div>
                  ) : null}
                  {(product.catalogAttributes?.length ?? 0) > 0 ? (
                    <div className="mt-catalog-detail-row">
                      <span className="mt-mono">Specs</span>
                      <span>
                        {product.catalogAttributes
                          ?.slice(0, 3)
                          .map((attribute) => `${attribute.name}: ${attribute.value}`)
                          .join(' · ')}
                      </span>
                    </div>
                  ) : null}
                </div>
              </section>
            ) : null}

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

        <div className="mt-modal-dock">
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
        </div>
      </div>
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

function CartPopover({
  cart,
  products,
  cartSnapshots,
  onViewFull,
  onClose,
  onRemove,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  cartSnapshots: Readonly<Record<string, MerchantCartSnapshot>>
  onViewFull: () => void
  onClose: () => void
  onRemove: (id: ProductId, merchant: string) => void
}>) {
  const ref = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const onDown = (event: MouseEvent) => {
      const target = event.target as Node
      // Ignore clicks on the cart button itself (it shares the anchor with the
      // popover). Otherwise this would close the popover and the button's own
      // onClick would immediately re-open it, so it could never be toggled shut.
      const anchor = ref.current?.closest('.mt-cart-anchor')
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

  const lines = cartLines(cart, products)

  if (lines.length === 0) {
    return (
      <div className="mt-cart-pop" ref={ref}>
        <div className="mt-cart-pop-head">
          <div className="mt-cart-pop-title">
            <SparkMark size={15} /> Smart cart
          </div>
        </div>
        <div className="mt-cart-pop-empty">
          <div className="mt-cart-pop-empty-mark">
            <CartIcon />
          </div>
          <div className="mt-cart-pop-empty-title">Your cart is empty</div>
          <div className="mt-cart-pop-empty-sub">
            Add products and Meant keeps merchant totals in sync.
          </div>
        </div>
      </div>
    )
  }

  const alerts = computeSmartAlerts(lines, products)
  const warnCount = alerts.filter((alert) => alert.kind === 'warn').length
  const groups = cartGroups(lines, false)
  const groupSummaries = groups.map((group) => {
    const merchantKey = group.items[0] ? cartMerchantKey(group.items[0]) : normalizedMerchantName(group.merchant)
    const snapshot = cartSnapshots[merchantKey]
    const fallbackTotal = group.subtotal + group.delivery
    return {
      group,
      snapshot,
      subtotal: cartSnapshotSubtotal(snapshot, group.subtotal),
      savings: cartSnapshotSavings(snapshot, group.subtotal, fallbackTotal),
      total: cartSnapshotTotal(snapshot, fallbackTotal),
    }
  })
  const discountTotal = groupSummaries.reduce((sum, summary) => sum + summary.savings, 0)
  const codeCount = groupSummaries.reduce((sum, summary) => sum + (summary.snapshot?.appliedCodes.length ?? 0), 0)
  const grandTotal = groupSummaries.reduce((sum, summary) => sum + summary.total, 0)
  const itemCount = lines.reduce((sum, line) => sum + line.qty, 0)

  return (
    <div className="mt-cart-pop" ref={ref}>
      <div className="mt-cart-pop-head">
        <div className="mt-cart-pop-title">
          <SparkMark size={15} /> Smart cart
        </div>
        <span className="mt-mono mt-cart-pop-count">
          {itemCount} items · {groups.length} merchants
        </span>
      </div>
      <div className="mt-cart-pop-signals">
        <div className={`mt-cart-sig ${warnCount > 0 ? 'mt-cart-sig-warn' : 'mt-cart-sig-good'}`}>
          {warnCount > 0 ? `${warnCount} issue to review` : 'All compatible'}
        </div>
        <div className={`mt-cart-sig ${codeCount > 0 ? 'mt-cart-sig-good' : 'mt-cart-sig-muted'}`}>
          <SparkMark size={13} />
          {codeCount > 0 ? `${codeCount} applied · -${money(discountTotal)}` : 'No applied codes'}
        </div>
      </div>
      <div className="mt-cart-pop-list">
        {lines.map((line) => (
          <div className="mt-cart-pop-item" key={`${line.id}-${line.merchant}`}>
            <div className="mt-cart-pop-media">
              <ProductArtwork product={line.product} label={line.product.category.toLowerCase()} />
            </div>
            <div className="mt-cart-pop-info">
              <div className="mt-cart-pop-name">{line.product.name}</div>
              <div className="mt-mono mt-cart-pop-meta">
                {line.qty} × {money(line.price)} · {line.merchant}
              </div>
            </div>
            <div className="mt-cart-pop-price">{money(line.price * line.qty)}</div>
            <button
              className="mt-cart-pop-x"
              type="button"
              onClick={() => onRemove(line.id, line.merchant)}
              aria-label="Remove"
            >
              <CloseIcon size={12} />
            </button>
          </div>
        ))}
      </div>
      <div className="mt-cart-pop-foot">
        {discountTotal > 0 ? (
          <div className="mt-cart-pop-save mt-mono">
            You are saving {money(discountTotal)} with applied merchant codes.
          </div>
        ) : null}
        <div className="mt-cart-pop-total">
          <span>Total</span>
          <span>{money(grandTotal)}</span>
        </div>
        <button className="mt-cart-pop-detail" type="button" onClick={onViewFull}>
          View full cart
        </button>
      </div>
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
      <button className="mt-brand" type="button" onClick={() => onNav('discover')} aria-label="Meant home">
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
              <span className={`mt-mono mt-nav-count${savedBump ? ' bump' : ''}`}>{savedCount}</span>
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
            <AccountMenu
              user={user}
              onNav={onNav}
              onClose={onCloseAccount}
              onSignOut={onSignOut}
            />
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
}: Readonly<{
  products: readonly Product[]
  deliveryLocations: readonly UserLocation[]
  preferences: readonly Preference[]
} & ProductOpenProps & ProductSaveProps>) {
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

function EmptyState({
  title,
  sub,
  mark,
}: Readonly<{
  title: string
  sub: string
  mark: ReactNode
}>) {
  return (
    <div className="mt-empty">
      <div className="mt-empty-mark">{mark}</div>
      <h3 className="mt-empty-title">{title}</h3>
      <p className="mt-empty-sub">{sub}</p>
    </div>
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
  onUpdateItem: (itemId: string, input: UserInventoryItemUpdateInput) => Promise<UserInventoryItemProfile>
  onDeleteItem: (itemId: string) => Promise<void>
  onExport: () => Promise<void>
}>) {
  const [categoryFilter, setCategoryFilter] = useState<UserInventoryCategory | 'ALL'>('ALL')
  const [restockOnly, setRestockOnly] = useState(false)
  const [manualForm, setManualForm] = useState<InventoryFormState>(() => initialInventoryForm())
  const [photoForm, setPhotoForm] = useState<InventoryFormState>(() => initialInventoryForm('OTHER'))
  const [saving, setSaving] = useState<'manual' | 'photo' | null>(null)
  const [photoProcessing, setPhotoProcessing] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const filteredItems = useMemo(
    () => items.filter((item) =>
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
      const fileName = file.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ').trim()
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
        right={(
          <div className="mt-inv-head-actions">
            <button className="mt-empty-btn ghost" type="button" onClick={onRefresh} disabled={loading}>
              Refresh
            </button>
            <button className="mt-empty-btn" type="button" onClick={() => void exportItems()} disabled={exporting}>
              {exporting ? 'Exporting' : 'Export data'}
            </button>
          </div>
        )}
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
          sub={items.length === 0 ? 'Manual entries, photo adds, and Meant purchases will appear here.' : 'Change the category or restock filter.'}
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
        <button className="mt-act mt-act-primary" type="submit" disabled={!form.name.trim() || pending}>
          {pending ? 'Adding' : 'Add item'}
        </button>
      </div>
      <div className="mt-inv-form-grid">
        <InventoryTextField label="Name" value={form.name} onChange={(name) => onChange((current) => ({ ...current, name }))} required />
        <InventoryTextField label="Brand" value={form.brand} onChange={(brand) => onChange((current) => ({ ...current, brand }))} />
        <InventoryCategoryField value={form.category} onChange={(category) => onChange((current) => ({
          ...current,
          category,
          consumable: category === 'PANTRY' ? true : current.consumable,
        }))} />
        <InventoryTextField label="Quantity" type="number" value={form.quantity} onChange={(quantity) => onChange((current) => ({ ...current, quantity }))} min="1" />
        <InventoryTextField label="Unit" value={form.unit} onChange={(unit) => onChange((current) => ({ ...current, unit }))} />
        <InventoryTextField label="Location" value={form.location} onChange={(location) => onChange((current) => ({ ...current, location }))} />
      </div>
      <InventoryTextField label="Image URL" value={form.imageUrl} onChange={(imageUrl) => onChange((current) => ({ ...current, imageUrl }))} />
      <InventoryTextField label="Product URL" value={form.productUrl} onChange={(productUrl) => onChange((current) => ({ ...current, productUrl }))} />
      <InventoryTextArea label="Attributes" value={form.attributes} onChange={(attributes) => onChange((current) => ({ ...current, attributes }))} />
      <InventoryTextArea label="Notes" value={form.notes} onChange={(notes) => onChange((current) => ({ ...current, notes }))} />
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
        <div className="mt-inv-photo-empty mt-mono">{processing ? 'Processing photo' : 'No photo selected'}</div>
      )}
      <div className="mt-inv-form-grid">
        <InventoryTextField label="Name" value={form.name} onChange={(name) => onChange((current) => ({ ...current, name }))} />
        <InventoryTextField label="Brand" value={form.brand} onChange={(brand) => onChange((current) => ({ ...current, brand }))} />
        <InventoryCategoryField value={form.category} onChange={(category) => onChange((current) => ({
          ...current,
          category,
          consumable: category === 'PANTRY' ? true : current.consumable,
        }))} />
        <InventoryTextField label="Quantity" type="number" value={form.quantity} onChange={(quantity) => onChange((current) => ({ ...current, quantity }))} min="1" />
      </div>
      <InventoryTextArea label="Notes" value={form.notes} onChange={(notes) => onChange((current) => ({ ...current, notes }))} />
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
  onUpdate: (itemId: string, input: UserInventoryItemUpdateInput) => Promise<UserInventoryItemProfile>
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
            <div className="mt-mono mt-inv-source">{inventorySourceLabel(item.source)} · {inventoryCategoryLabel(item.category)}</div>
            <h3 className="mt-inv-name">{item.name}</h3>
            {item.brand ? <div className="mt-inv-brand">{item.brand}</div> : null}
          </div>
          <div className="mt-inv-actions">
            <button className="mt-act mt-act-ghost" type="button" onClick={() => setEditing((current) => !current)}>
              {editing ? 'Cancel' : 'Edit'}
            </button>
            <button className="mt-act mt-act-ghost danger" type="button" onClick={() => void remove()} disabled={deleting}>
              {confirmDelete ? 'Confirm delete' : deleting ? 'Deleting' : 'Delete'}
            </button>
          </div>
        </div>

        {editing ? (
          <form className="mt-inv-edit" onSubmit={save}>
            <div className="mt-inv-form-grid">
              <InventoryTextField label="Name" value={form.name} onChange={(name) => setForm((current) => ({ ...current, name }))} required />
              <InventoryTextField label="Brand" value={form.brand} onChange={(brand) => setForm((current) => ({ ...current, brand }))} />
              <InventoryCategoryField value={form.category} onChange={(category) => setForm((current) => ({
                ...current,
                category,
                consumable: category === 'PANTRY' ? true : current.consumable,
              }))} />
              <InventoryTextField label="Quantity" type="number" value={form.quantity} onChange={(quantity) => setForm((current) => ({ ...current, quantity }))} min="1" />
              <InventoryTextField label="Unit" value={form.unit} onChange={(unit) => setForm((current) => ({ ...current, unit }))} />
              <InventoryTextField label="Location" value={form.location} onChange={(location) => setForm((current) => ({ ...current, location }))} />
            </div>
            <InventoryTextArea label="Attributes" value={form.attributes} onChange={(attributes) => setForm((current) => ({ ...current, attributes }))} />
            <InventoryTextArea label="Notes" value={form.notes} onChange={(notes) => setForm((current) => ({ ...current, notes }))} />
            <InventoryRestockFields form={form} onChange={setForm} />
            <div className="mt-inv-save-row">
              <button className="mt-act mt-act-primary" type="submit" disabled={!form.name.trim() || saving}>
                {saving ? 'Saving' : 'Save changes'}
              </button>
            </div>
          </form>
        ) : (
          <>
            <div className="mt-inv-meta">
              <span>{item.quantity}{item.unit ? ` ${item.unit}` : ''}</span>
              {item.location ? <span>{item.location}</span> : null}
              {item.restockEnabled ? <span>Restock{item.restockThreshold !== null ? ` at ${item.restockThreshold}` : ''}</span> : null}
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
          <option key={category} value={category}>{inventoryCategoryLabel(category)}</option>
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
          onChange={(event) => onChange((current) => ({ ...current, consumable: event.target.checked }))}
        />
        <span>Consumable</span>
      </label>
      <label className="mt-inv-check">
        <input
          type="checkbox"
          checked={form.restockEnabled}
          onChange={(event) => onChange((current) => ({
            ...current,
            restockEnabled: event.target.checked,
            consumable: event.target.checked ? true : current.consumable,
          }))}
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
          onChange={(event) => onChange((current) => ({ ...current, restockThreshold: event.target.value }))}
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
  const bestPrice = enough ? Math.min(...items.map((product) => productPriceFrom(product, deliveryLocations))) : null
  const bestMerchantCount = enough
    ? Math.max(...items.map((product) => productMerchantCount(product, deliveryLocations)))
    : null
  const winner = enough
    ? [...items].sort((left, right) =>
        right.match - left.match ||
        productPriceFrom(left, deliveryLocations) - productPriceFrom(right, deliveryLocations),
      )[0]
    : null
  const comparisonPreferenceIds = preferences
    .map((preference) => preference.id)
    .filter((id) =>
      items.some((product) => product.satisfies.includes(id) || product.misses.includes(id)),
    )

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
          <p>{winner.note}</p>
        </div>
      ) : null}
      <div className="mt-cmp">
        <div className="mt-cmp-grid mt-cmp-headrow" style={gridStyle}>
          <div className="mt-cmp-rowlabel mt-cmp-corner mt-mono">
            {items.length} of 4
          </div>
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
                win: bestPrice !== null && productPriceFrom(product, deliveryLocations) === bestPrice,
              }))}
              addSpacer={showAdd}
            />
            <CompareMetricRow
              label="Stores"
              gridStyle={gridStyle}
              cells={items.map((product) => ({
                key: product.id,
                value: `${productMerchantCount(product, deliveryLocations)}`,
                win: bestMerchantCount !== null && productMerchantCount(product, deliveryLocations) === bestMerchantCount,
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
                        {product.review.score !== null ? `${product.review.score.toFixed(1)} · ` : ''}
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
                  <span className="mt-cmp-text">{product.note}</span>
                </div>
              ))}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
            <div className="mt-cmp-grid mt-cmp-section" style={gridStyle}>
              <div className="mt-cmp-rowlabel mt-cmp-seclabel mt-mono">
                Your preferences
              </div>
              {items.map((product) => <div key={product.id} />)}
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
        Add products to Compare from a product detail page with Add to compare. Once a product is in compare,
        In compare opens this page. The Add a saved product control only lists products you have saved.
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
  return PREFERENCE_CATEGORY_LABELS[category] ?? category
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function preferenceSortValue(preference: Preference): number {
  return preference.displayOrder ?? Number.MAX_SAFE_INTEGER
}

function sortPreferences(preferences: readonly Preference[]): Preference[] {
  return [...preferences].sort((left, right) =>
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
  ]
    .some((value) => typeof value === 'string' && value.toLowerCase().includes(searchText))
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
  const activeGroup = PREFERENCE_GROUPS.find((group) => group.id === activePreferenceGroup) ?? PREFERENCE_GROUPS[0]
  const visiblePreferences = useMemo(() => {
    if (preferenceSearchText) {
      return sortPreferences(allPrefs.filter((preference) => preferenceMatchesSearch(preference, preferenceSearchText)))
    }
    const activeCategories = new Set(activeGroup.categories)
    return sortPreferences(allPrefs.filter((preference) => activeCategories.has(preferenceCategory(preference))))
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
        right={<button className="mt-act mt-act-primary mt-prefs-done" type="button" onClick={onDone}>Done</button>}
      />

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Where it ships</h3>
            <p className="mt-secsub">
              Leave shipping unrestricted, or add every place you want merchants to be able to deliver.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">
            {deliveryLocations.length > 0 ? `${deliveryLocations.length} active` : 'Anywhere'}
          </span>
        </div>
        <LocationSection locations={deliveryLocations} onSet={onDeliveryLocations} />
      </section>

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
              Already chat with ChatGPT or Claude? Ask it about you, then paste its answer here to build filters.
            </p>
          </div>
        </div>
        <div className="mt-sync-pane">
          <div className="mt-sync-block">
            <div className="mt-sync-row">
              <div className="mt-sync-label"><span className="mt-step-num">1</span> Ask your assistant about you</div>
              <button className={`mt-act mt-act-ghost mt-copy ${copied ? 'done' : ''}`} type="button" onClick={copyQuestion}>
                {copied ? 'Copied' : 'Copy question'}
              </button>
            </div>
            <p className="mt-sync-desc">
              Paste this into another AI. It should reply with what it knows about your shopping preferences.
            </p>
            <textarea className="mt-prompt-text mt-mono" readOnly value={IMPORT_ASK} onFocus={(event) => event.currentTarget.select()} />
          </div>
          <div className="mt-sync-block">
            <div className="mt-sync-row">
              <div className="mt-sync-label"><span className="mt-step-num">2</span> Paste its answer back</div>
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
              onClick={() => onSet(locations.filter((candidate) =>
                candidate.code !== location.code || candidate.city !== location.city
              ))}
            >
              <span>{location.city}, {location.country}</span>
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
            <button className="mt-act mt-act-primary" type="button" onClick={save} disabled={!code || !city}>
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

function MerchantDeliveryPanel({
  merchantKey,
  merchant,
  cartId,
  deliveryGroups,
  currency,
  draft,
  busy,
  error,
  onDraft,
  onSubmitAddress,
  onSelectOption,
}: Readonly<{
  merchantKey: string
  merchant: string
  cartId?: string | null
  deliveryGroups: readonly CartDeliveryGroup[]
  currency?: string | null
  draft: DeliveryAddressDraft
  busy: boolean
  error: string | null
  onDraft: (patch: Partial<DeliveryAddressDraft>) => void
  onSubmitAddress: () => void
  onSelectOption: (group: CartDeliveryGroup, option: CartDeliveryOption) => void
}>) {
  const deliveryOptionCount = deliveryGroups.reduce((sum, group) => sum + cartDeliveryOptions(group).length, 0)
  const hasOptions = deliveryOptionCount > 0
  const canSubmit = Boolean(cartId && draft.countryCode.trim() && draft.city.trim() && draft.postalCode.trim())

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (canSubmit && !busy) {
      onSubmitAddress()
    }
  }

  return (
    <div className="mt-delivery-panel">
      <div className="mt-delivery-head">
        <div>
          <div className="mt-delivery-title">Delivery options</div>
          <div className="mt-mono mt-delivery-privacy">City, postal code and country only</div>
        </div>
        {hasOptions ? (
          <span className="mt-mono mt-delivery-count">
            {deliveryOptionCount} options
          </span>
        ) : null}
      </div>
      <form className="mt-delivery-address" onSubmit={submit}>
        <label className="mt-field">
          <span className="mt-field-label">Country</span>
          <select
            className="mt-select"
            value={draft.countryCode}
            onChange={(event) => onDraft({ countryCode: event.target.value })}
          >
            {LOCATIONS.map((location) => (
              <option key={location.code} value={location.code}>
                {location.country}
              </option>
            ))}
          </select>
        </label>
        <label className="mt-field">
          <span className="mt-field-label">City</span>
          <input
            className="mt-input"
            value={draft.city}
            onChange={(event) => onDraft({ city: event.target.value })}
            autoComplete="address-level2"
          />
        </label>
        <label className="mt-field">
          <span className="mt-field-label">Postal code</span>
          <input
            className="mt-input"
            value={draft.postalCode}
            onChange={(event) => onDraft({ postalCode: event.target.value })}
            autoComplete="postal-code"
          />
        </label>
        <label className="mt-field">
          <span className="mt-field-label">Region</span>
          <input
            className="mt-input"
            value={draft.provinceCode}
            onChange={(event) => onDraft({ provinceCode: event.target.value })}
            autoComplete="address-level1"
          />
        </label>
        <button className="mt-delivery-refresh" type="submit" disabled={!canSubmit || busy}>
          {busy ? 'Updating...' : 'Get options'}
        </button>
      </form>
      {error ? <div className="mt-mono mt-delivery-error">{error}</div> : null}
      {hasOptions ? (
        <div className="mt-delivery-groups">
          {deliveryGroups.map((group, index) => {
            const selected = selectedCartDeliveryOption(group)
            const options = cartDeliveryOptions(group)
            return (
              <div className="mt-delivery-group" key={group.id ?? group.handle ?? `${merchantKey}-${index}`}>
                {deliveryGroups.length > 1 ? (
                  <div className="mt-mono mt-delivery-group-title">Shipment {index + 1}</div>
                ) : null}
                <div className="mt-delivery-options">
                  {options.length > 0 ? options.map((option, optionIndex) => {
                    const optionSelected = selected?.handle && option.handle
                      ? selected.handle === option.handle
                      : option.selected === true
                    const speed = deliveryOptionSpeed(option)
                    return (
                      <button
                        className={`mt-delivery-option ${optionSelected ? 'selected' : ''}`}
                        key={option.handle ?? option.title ?? `${merchantKey}-${index}-${optionIndex}`}
                        type="button"
                        disabled={busy || !option.handle}
                        aria-pressed={optionSelected}
                        onClick={() => onSelectOption(group, option)}
                      >
                        <span className="mt-delivery-option-main">
                          <span className="mt-delivery-option-title">{deliveryOptionTitle(option)}</span>
                          {speed ? <span className="mt-mono mt-delivery-option-speed">{speed}</span> : null}
                        </span>
                        <span className="mt-mono mt-delivery-option-cost">
                          {deliveryOptionCost(option, currency)}
                        </span>
                      </button>
                    )
                  }) : (
                    <div className="mt-mono mt-delivery-empty">No options available for this shipment.</div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="mt-mono mt-delivery-empty">
          {cartId ? `No delivery options loaded for ${merchant}.` : 'Merchant cart is syncing.'}
        </div>
      )}
    </div>
  )
}

function CartView({
  cart,
  products,
  cartSnapshots,
  deliveryLocations,
  onRemove,
  onQty,
  onAdd,
  onApplyCode,
  onRemoveCode,
  onDeliveryAddress,
  onDeliveryOption,
  onCheckout,
  checkoutMerchant,
  checkoutError,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  cartSnapshots: Readonly<Record<string, MerchantCartSnapshot>>
  deliveryLocations: readonly UserLocation[]
  onRemove: (id: ProductId, merchant: string) => void
  onQty: (id: ProductId, merchant: string, qty: number) => void
  onAdd: (id: ProductId, merchant: string) => void
  onApplyCode: (input: ApplyCartCodeInput) => Promise<{ ok: boolean; message?: string }>
  onRemoveCode: (input: RemoveCartCodeInput) => Promise<{ ok: boolean; message?: string }>
  onDeliveryAddress: (payload: DeliveryAddressPayload) => Promise<boolean> | boolean
  onDeliveryOption: (payload: DeliveryOptionPayload) => Promise<boolean> | boolean
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  checkoutMerchant: string | null
  checkoutError: { merchant: string; message: string } | null
}>) {
  const [scanning, setScanning] = useState(true)
  const [codeEntries, setCodeEntries] = useState<Record<string, { discount: string; giftCard: string }>>({})
  const [codeBusy, setCodeBusy] = useState<Record<string, AppliedCartCodeType | 'REMOVE' | null>>({})
  const [codeErrors, setCodeErrors] = useState<Record<string, string | null>>({})
  const [addressDrafts, setAddressDrafts] = useState<Record<string, DeliveryAddressDraft>>({})
  const [deliveryBusyByMerchant, setDeliveryBusyByMerchant] = useState<Record<string, number>>({})
  const [deliveryErrorsByMerchant, setDeliveryErrorsByMerchant] = useState<Record<string, string>>({})

  useEffect(() => {
    setScanning(true)
    const timeout = window.setTimeout(() => setScanning(false), 1700)
    return () => window.clearTimeout(timeout)
  }, [cart.length])

  const lines = cartLines(cart, products)
  const alerts = computeSmartAlerts(lines, products)
  const shipWarnings = deliveryLocations.length > 0
    ? lines.filter((line) => !canMerchantShip(line.merchant, deliveryLocations))
    : []
  const groups = cartGroups(lines, scanning)
  const groupSummaries = groups.map((group) => {
    const merchantKey = group.items[0] ? cartMerchantKey(group.items[0]) : normalizedMerchantName(group.merchant)
    const snapshot = cartSnapshots[merchantKey]
    const fallbackTotal = group.subtotal + group.delivery
    return {
      group,
      merchantKey,
      snapshot,
      subtotal: cartSnapshotSubtotal(snapshot, group.subtotal),
      savings: cartSnapshotSavings(snapshot, group.subtotal, fallbackTotal),
      total: cartSnapshotTotal(snapshot, fallbackTotal),
      currency: snapshot?.currency ?? null,
    }
  })
  const itemsTotal = groupSummaries.reduce((sum, summary) => sum + summary.subtotal, 0)
  const discountTotal = groupSummaries.reduce((sum, summary) => sum + summary.savings, 0)
  const deliveryTotal = groupSummaries.reduce((sum, summary) =>
    summary.snapshot?.totalAmount == null ? sum + summary.group.delivery : sum, 0)
  const grandTotal = groupSummaries.reduce((sum, summary) => sum + summary.total, 0)
  const codeCount = groupSummaries.reduce((sum, summary) => sum + (summary.snapshot?.appliedCodes.length ?? 0), 0)
  const warnCount = alerts.filter((alert) => alert.kind === 'warn').length

  const updateCodeEntry = (merchantKey: string, field: 'discount' | 'giftCard', value: string) => {
    setCodeEntries((current) => ({
      ...current,
      [merchantKey]: {
        discount: current[merchantKey]?.discount ?? '',
        giftCard: current[merchantKey]?.giftCard ?? '',
        [field]: value,
      },
    }))
    setCodeErrors((current) => ({ ...current, [merchantKey]: null }))
  }

  const submitCode = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    type: AppliedCartCodeType,
  ) => {
    if (!cartId) {
      setCodeErrors((current) => ({
        ...current,
        [merchantKey]: 'This merchant cart is still syncing.',
      }))
      return
    }
    const field = type === 'DISCOUNT' ? 'discount' : 'giftCard'
    const code = (codeEntries[merchantKey]?.[field] ?? '').trim()
    setCodeBusy((current) => ({ ...current, [merchantKey]: type }))
    const result = await onApplyCode({ merchantKey, merchant, cartId, code, type })
    setCodeBusy((current) => ({ ...current, [merchantKey]: null }))
    if (result.ok) {
      setCodeEntries((current) => ({
        ...current,
        [merchantKey]: {
          discount: type === 'DISCOUNT' ? '' : (current[merchantKey]?.discount ?? ''),
          giftCard: type === 'GIFT_CARD' ? '' : (current[merchantKey]?.giftCard ?? ''),
        },
      }))
      setCodeErrors((current) => ({ ...current, [merchantKey]: null }))
      return
    }
    setCodeErrors((current) => ({
      ...current,
      [merchantKey]: result.message ?? 'The merchant did not accept this code.',
    }))
  }

  const removeCode = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    code: AppliedCartCode,
  ) => {
    if (!cartId) {
      return
    }
    setCodeBusy((current) => ({ ...current, [merchantKey]: 'REMOVE' }))
    const result = await onRemoveCode({ merchantKey, merchant, cartId, code })
    setCodeBusy((current) => ({ ...current, [merchantKey]: null }))
    setCodeErrors((current) => ({
      ...current,
      [merchantKey]: result.ok ? null : (result.message ?? 'The merchant could not remove this code.'),
    }))
  }

  const addressDraft = (merchantKey: string) =>
    addressDrafts[merchantKey] ?? emptyDeliveryAddressDraft(deliveryLocations)

  const updateAddressDraft = (merchantKey: string, patch: Partial<DeliveryAddressDraft>) => {
    setAddressDrafts((current) => ({
      ...current,
      [merchantKey]: {
        ...(current[merchantKey] ?? emptyDeliveryAddressDraft(deliveryLocations)),
        ...patch,
      },
    }))
  }

  const beginDeliveryBusy = (merchantKey: string) => {
    setDeliveryBusyByMerchant((current) => ({
      ...current,
      [merchantKey]: (current[merchantKey] ?? 0) + 1,
    }))
  }

  const endDeliveryBusy = (merchantKey: string) => {
    setDeliveryBusyByMerchant((current) => {
      const next = { ...current }
      const count = (next[merchantKey] ?? 0) - 1
      if (count > 0) {
        next[merchantKey] = count
      } else {
        delete next[merchantKey]
      }
      return next
    })
  }

  const setDeliveryErrorForMerchant = (merchantKey: string, message: string | null) => {
    setDeliveryErrorsByMerchant((current) => {
      const next = { ...current }
      if (message) {
        next[merchantKey] = message
      } else {
        delete next[merchantKey]
      }
      return next
    })
  }

  const submitDeliveryAddress = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    draft: DeliveryAddressDraft,
  ) => {
    if (!cartId) {
      setDeliveryErrorForMerchant(merchantKey, 'Merchant cart is still syncing.')
      return
    }
    beginDeliveryBusy(merchantKey)
    setDeliveryErrorForMerchant(merchantKey, null)
    try {
      const ok = await onDeliveryAddress({ cartId, merchantKey, merchant, ...draft })
      if (!ok) {
        setDeliveryErrorForMerchant(merchantKey, 'Could not load delivery options.')
      }
    } catch {
      setDeliveryErrorForMerchant(merchantKey, 'Could not load delivery options.')
    } finally {
      endDeliveryBusy(merchantKey)
    }
  }

  const selectDeliveryOption = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    group: CartDeliveryGroup,
    option: CartDeliveryOption,
  ) => {
    if (!cartId) {
      setDeliveryErrorForMerchant(merchantKey, 'Merchant cart is still syncing.')
      return
    }
    beginDeliveryBusy(merchantKey)
    setDeliveryErrorForMerchant(merchantKey, null)
    try {
      const ok = await onDeliveryOption({ cartId, merchantKey, merchant, group, option })
      if (!ok) {
        setDeliveryErrorForMerchant(merchantKey, 'Could not update delivery choice.')
      }
    } catch {
      setDeliveryErrorForMerchant(merchantKey, 'Could not update delivery choice.')
    } finally {
      endDeliveryBusy(merchantKey)
    }
  }

  if (lines.length === 0) {
    return (
      <main className="mt-feed mt-view">
        <ViewHead eyebrow="Smart cart" title="Your cart" />
        <EmptyState
          title="Your cart is empty"
          sub="Add products and Meant keeps merchant totals in sync."
          mark={<CartIcon />}
        />
      </main>
    )
  }

  return (
    <main className="mt-feed mt-view mt-cart">
      <ViewHead
        eyebrow="Smart cart"
        title="Your cart"
        sub={`${lines.length} items from ${groups.length} merchants - one smart cart, with checkout handled at each merchant.`}
      />
      <div className="mt-cart-grid">
        <div className="mt-cart-main">
          {alerts.length > 0 || shipWarnings.length > 0 ? (
            <div className="mt-alerts">
              {shipWarnings.map((line) => (
                <div key={`ship-${line.id}-${line.merchant}`} className="mt-alert mt-alert-warn">
                  <span className="mt-alert-ico">!</span>
                  <div className="mt-alert-body">
                    <div className="mt-alert-title">Does not ship to selected destinations</div>
                    <div className="mt-alert-text">
                      {line.merchant} cannot deliver {line.product.name} to {deliveryLocationSummary(deliveryLocations)}.
                    </div>
                  </div>
                  <button className="mt-alert-fix" type="button" onClick={() => onRemove(line.id, line.merchant)}>
                    Remove item
                    <span className="mt-alert-fix-sub mt-mono">will not ship</span>
                  </button>
                </div>
              ))}
              {alerts.map((alert) => (
                <div key={alert.id} className={`mt-alert mt-alert-${alert.kind}`}>
                  <span className="mt-alert-ico">{alert.kind === 'warn' ? '!' : 'ok'}</span>
                  <div className="mt-alert-body">
                    <div className="mt-alert-title">{alert.title}</div>
                    <div className="mt-alert-text">{alert.body}</div>
                  </div>
                  {alert.fix ? (
                    <button
                      className="mt-alert-fix"
                      type="button"
                      onClick={() => onAdd(alert.fix?.id ?? 'adapter', alert.fix?.merchant ?? 'Lumen Store')}
                    >
                      {alert.fix.label}
                      <span className="mt-alert-fix-sub mt-mono">{alert.fix.sub}</span>
                    </button>
                  ) : null}
                </div>
              ))}
            </div>
          ) : null}

          {groupSummaries.map(({ group, merchantKey, snapshot, subtotal, savings, total, currency }) => {
            const cartId = snapshot?.cartId ?? group.items.find((item) => item.cartId)?.cartId
            const groupCurrency = currency ?? group.items.find((item) => item.cartCurrency)?.cartCurrency
            const groupDraft = addressDraft(merchantKey)
            const groupDeliveryBusy = (deliveryBusyByMerchant[merchantKey] ?? 0) > 0
            const groupDeliveryError = deliveryErrorsByMerchant[merchantKey] ?? null
            const deliverySummary = deliveryGroupSummary(group.deliveryGroups, group.delivery, groupCurrency)
            const groupSyncing = group.items.some((item) => item.syncing)
            const groupLineError = group.items.find((item) => item.syncError)?.syncError
            const groupCheckoutError = checkoutError?.merchant === group.merchant
              ? checkoutError.message
              : null
            const appliedCodes = snapshot?.appliedCodes ?? []
            const entry = codeEntries[merchantKey] ?? { discount: '', giftCard: '' }
            const busy = codeBusy[merchantKey] ?? null
            const codeError = codeErrors[merchantKey]
            const codeControlsDisabled = groupSyncing || !cartId || Boolean(busy)
            const groupCheckoutable = group.items.every((item) =>
              Boolean(item.cartId && item.productVariantId && !item.syncError),
            )
            const checkoutNeedsDelivery = group.hasDeliveryOptions && !group.hasSelectedDelivery
            const checkoutBusy = checkoutMerchant === group.merchant
            const deliveryDisplay = checkoutNeedsDelivery ? 'Choose delivery option' : deliverySummary
            const checkoutBlocked = scanning ||
              groupSyncing ||
              !groupCheckoutable ||
              checkoutNeedsDelivery ||
              Boolean(checkoutMerchant)
            const checkoutSub = groupLineError ?? groupCheckoutError ??
              (groupSyncing
                ? 'Syncing merchant cart'
                : !groupCheckoutable
                  ? 'Checkout needs a merchant cart-ready item'
                  : checkoutNeedsDelivery
                    ? 'Choose a delivery option'
                  : appliedCodes.length > 0
                    ? `${appliedCodes.length} applied · -${cartMoney(savings, currency)}`
                    : deliveryDisplay)

            return (
              <div className="mt-mgroup" key={group.merchant}>
                <div className="mt-mgroup-head">
                  <div className="mt-mgroup-name">
                    <span className="mt-mgroup-dot" />
                    {group.merchant}
                    <span className="mt-mono mt-mgroup-count">
                      {group.items.length} item{group.items.length > 1 ? 's' : ''}
                    </span>
                  </div>
                  <div className="mt-mono mt-mgroup-ship">
                    {deliveryDisplay}
                  </div>
                </div>
                {group.items.map((line) => (
                  <div className="mt-citem" key={`${line.id}-${line.merchant}`}>
                    <div className="mt-citem-media">
                      <ProductArtwork product={line.product} label={line.product.category.toLowerCase()} />
                    </div>
                    <div className="mt-citem-info">
                      <div className="mt-mono mt-citem-brand">{line.product.brand}</div>
                      <div className="mt-citem-name">{line.product.name}</div>
                      <div className="mt-mono mt-citem-deliv">
                        {line.syncing ? 'Syncing cart...' : `Arrives ${line.delivery.toLowerCase()}`}
                      </div>
                      {line.syncError ? (
                        <div className="mt-mono mt-citem-error">{line.syncError}</div>
                      ) : null}
                    </div>
                    <div className="mt-citem-right">
                      <div className="mt-qty">
                        <button
                          type="button"
                          onClick={() => onQty(line.id, line.merchant, line.qty - 1)}
                          aria-label="Decrease"
                          disabled={line.syncing}
                        >
                          -
                        </button>
                        <span>{line.qty}</span>
                        <button
                          type="button"
                          onClick={() => onQty(line.id, line.merchant, line.qty + 1)}
                          aria-label="Increase"
                          disabled={line.syncing}
                        >
                          +
                        </button>
                      </div>
                      <div className="mt-citem-price">{money(line.price * line.qty)}</div>
                      <button
                        className="mt-citem-remove"
                        type="button"
                        onClick={() => onRemove(line.id, line.merchant)}
                        aria-label="Remove"
                        disabled={line.syncing}
                      >
                        <CloseIcon size={13} />
                      </button>
                    </div>
                  </div>
                ))}
                <MerchantDeliveryPanel
                  merchantKey={merchantKey}
                  merchant={group.merchant}
                  cartId={cartId}
                  deliveryGroups={group.deliveryGroups}
                  currency={groupCurrency}
                  draft={groupDraft}
                  busy={groupDeliveryBusy}
                  error={groupDeliveryError}
                  onDraft={(patch) => updateAddressDraft(merchantKey, patch)}
                  onSubmitAddress={() => {
                    void submitDeliveryAddress(merchantKey, group.merchant, cartId, groupDraft)
                  }}
                  onSelectOption={(deliveryGroup, option) => {
                    void selectDeliveryOption(merchantKey, group.merchant, cartId, deliveryGroup, option)
                  }}
                />
                <div className="mt-mgroup-foot">
                  <div className="mt-code-panel">
                    <div className="mt-code-forms">
                      <form
                        className="mt-code-form"
                        onSubmit={(event) => {
                          event.preventDefault()
                          void submitCode(merchantKey, group.merchant, cartId, 'DISCOUNT')
                        }}
                      >
                        <input
                          className="mt-code-input mt-mono"
                          value={entry.discount}
                          onChange={(event) => updateCodeEntry(merchantKey, 'discount', event.target.value)}
                          placeholder="Discount code"
                          disabled={codeControlsDisabled}
                        />
                        <button type="submit" disabled={codeControlsDisabled || !entry.discount.trim()}>
                          {busy === 'DISCOUNT' ? 'Applying...' : 'Apply'}
                        </button>
                      </form>
                      <form
                        className="mt-code-form"
                        onSubmit={(event) => {
                          event.preventDefault()
                          void submitCode(merchantKey, group.merchant, cartId, 'GIFT_CARD')
                        }}
                      >
                        <input
                          className="mt-code-input mt-mono"
                          value={entry.giftCard}
                          onChange={(event) => updateCodeEntry(merchantKey, 'giftCard', event.target.value)}
                          placeholder="Gift card"
                          disabled={codeControlsDisabled}
                        />
                        <button type="submit" disabled={codeControlsDisabled || !entry.giftCard.trim()}>
                          {busy === 'GIFT_CARD' ? 'Applying...' : 'Apply'}
                        </button>
                      </form>
                    </div>
                    {appliedCodes.length > 0 ? (
                      <div className="mt-applied-codes">
                        {appliedCodes.map((code, index) => (
                          <span
                            className="mt-applied-code"
                            key={`${code.type}-${code.code ?? code.displayCode ?? code.label ?? 'code'}-${index}`}
                          >
                            <span className="mt-code-val mt-mono">{appliedCodeDisplay(code)}</span>
                            <span className="mt-found-label">{code.label ?? (code.type === 'GIFT_CARD' ? 'Gift card' : 'Discount')}</span>
                            {code.amount ? (
                              <span className="mt-found-save mt-mono">-{cartMoney(Math.abs(code.amount), code.currency ?? currency)}</span>
                            ) : null}
                            <button
                              className="mt-code-remove"
                              type="button"
                              disabled={!cartId || Boolean(busy) || !code.code}
                              onClick={() => void removeCode(merchantKey, group.merchant, cartId, code)}
                              aria-label={`Remove ${appliedCodeDisplay(code)}`}
                            >
                              <CloseIcon size={11} />
                            </button>
                          </span>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-found mt-found-none mt-mono">
                        No applied codes for {group.merchant}
                      </div>
                    )}
                    {codeError ? (
                      <div className="mt-cart-inline-error">{codeError}</div>
                    ) : null}
                  </div>
                  <div className="mt-mgroup-sub">
                    Subtotal <span>{cartMoney(subtotal, currency)}</span>
                  </div>
                  {savings > 0 ? (
                    <div className="mt-mgroup-sub save">
                      Savings <span>-{cartMoney(savings, currency)}</span>
                    </div>
                  ) : null}
                </div>
                <div className="mt-mgroup-pay">
                  <div>
                    <div className="mt-mgroup-pay-total">
                      <span className="mt-mono">Merchant total</span>
                      <strong>{cartMoney(total, groupCurrency)}</strong>
                    </div>
                    <div className={`mt-mgroup-pay-sub ${groupLineError || groupCheckoutError ? 'error' : ''}`}>
                      {checkoutSub}
                    </div>
                  </div>
                  <button
                    className="mt-mcheckout"
                    type="button"
                    disabled={checkoutBlocked}
                    onClick={() =>
                      void onCheckout({
                        items: group.items,
                        saved: savings,
                        savedNote: appliedCodes.length > 0
                          ? appliedCodes.map((code) => `${appliedCodeDisplay(code)} applied`).join(', ')
                          : '',
                        merchant: group.merchant,
                      })
                    }
                  >
                    {checkoutBusy ? 'Opening checkout...' : `Check out at ${group.merchant}`}
                  </button>
                </div>
              </div>
            )
          })}
        </div>

        <aside className="mt-summary">
          <div className="mt-summary-card">
            <div className="mt-summary-title">Order summary</div>
            <div className="mt-scan-banner">
              {codeCount > 0 ? (
                <>
                  <SparkMark size={14} /> {codeCount} merchant code{codeCount > 1 ? 's' : ''} applied
                </>
              ) : (
                <>Apply discount or gift-card codes at each merchant.</>
              )}
            </div>
            <div className="mt-sum-row">
              <span>Items ({lines.reduce((sum, line) => sum + line.qty, 0)})</span>
              <span>{money(itemsTotal)}</span>
            </div>
            <div className={`mt-sum-row ${discountTotal > 0 ? 'save' : 'muted'}`}>
              <span>Applied savings</span>
              <span>{discountTotal > 0 ? `-${money(discountTotal)}` : money(0)}</span>
            </div>
            <div className="mt-sum-row">
              <span>Delivery</span>
              <span>{deliveryTotal === 0 ? 'Free' : money(deliveryTotal)}</span>
            </div>
            <div className="mt-sum-total">
              <span>Total</span>
              <span>{money(grandTotal)}</span>
            </div>
            {discountTotal > 0 ? (
              <div className="mt-sum-note mt-mono">
                You are saving {money(discountTotal)} with merchant-applied codes.
              </div>
            ) : null}
            {warnCount > 0 ? (
              <div className="mt-sum-warn">
                <span className="mt-sum-warn-dot" /> {warnCount} compatibility issue to review above
              </div>
            ) : null}
            <div className="mt-sum-handoff">
              <SparkMark size={14} />
              <span>
                Checkout happens on each merchant's site. Use the checkout button inside every merchant group.
              </span>
            </div>
            <div className="mt-mono mt-summary-foot">
              {groups.length} merchant checkout{groups.length > 1 ? 's' : ''} needed.
            </div>
          </div>
        </aside>
      </div>
    </main>
  )
}

function OrdersView({
  orders,
  products,
  flashId,
  preferences,
  onOpen,
  onReorder,
}: Readonly<{
  orders: readonly Order[]
  products: readonly Product[]
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
          title="No orders yet"
          sub="When you check out, your orders land here."
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
            <button className="mt-pager-btn" type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
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
            <button className="mt-pager-btn" type="button" disabled={safePage === pageCount - 1} onClick={() => setPage(safePage + 1)}>
              Next
            </button>
          </div>
        </nav>
      ) : null}
    </main>
  )
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
  })).filter((line): line is { item: CartItem; product: Product } => Boolean(line.product))
  const total = lines.reduce((sum, { item, product }) => {
    const offer = product.offers.find((candidate) => candidate.merchant === item.merchant) ?? product.offers[0]
    return sum + offer.price * item.qty
  }, 0)
  const matched = Array.from(
    new Set(lines.flatMap((line) => line.product.satisfies)),
  )

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
          <span className={`mt-order-status mt-order-status-${order.status.toLowerCase().replace(/\s+/g, '-')}`}>
            <span className="mt-order-status-dot" /> {order.status}
          </span>
          <span className="mt-order-total">{money(total)}</span>
        </div>
      </div>
      <div className="mt-order-track mt-mono">{order.statusNote}</div>
      <div className="mt-order-items">
        {lines.map(({ item, product }) => {
          const offer = product.offers.find((candidate) => candidate.merchant === item.merchant) ?? product.offers[0]
          return (
            <button
              className="mt-order-item"
              key={`${item.id}-${item.merchant}`}
              type="button"
              onClick={() => onOpen(product)}
            >
              <div className="mt-order-item-media">
                <ProductArtwork product={product} label={product.category.toLowerCase()} />
              </div>
              <div className="mt-order-item-info">
                <div className="mt-mono mt-order-item-brand">{product.brand}</div>
                <div className="mt-order-item-name">{product.name}</div>
                <div className="mt-mono mt-order-item-meta">
                  {item.qty} × {money(offer.price)} · {item.merchant}
                </div>
              </div>
              <div className="mt-order-item-price">{money(offer.price * item.qty)}</div>
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
  onSave,
  onSignOut,
  onEditPrefs,
  onDone,
}: Readonly<{
  user: UserAccount
  userId?: string
  onSave: (user: UserAccount) => void
  onSignOut: () => void
  onEditPrefs: () => void
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
        savedName = [profile.firstName, profile.surname].filter(Boolean).join(' ').trim() || nextName
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

      onSave({ name: savedName, email: savedEmail, avatar: savedAvatar, avatarPath: savedAvatarPath })
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
        right={<button className="mt-act mt-act-primary mt-prefs-done" type="button" onClick={onDone}>Done</button>}
      />
      <div className="mt-acct-card">
        <div className="mt-acct-idrow">
          <div className="mt-acct-avatar">
            <Avatar user={preview} size={84} />
          </div>
          <div className="mt-acct-photo-actions">
            <button className="mt-acct-uploadbtn" type="button" onClick={() => fileRef.current?.click()}>
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
            <div className="mt-acct-photo-hint">JPG, PNG, or WebP up to 5 MB. A square image works best.</div>
            <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" onChange={onFile} hidden />
          </div>
        </div>
        <div className="mt-acct-fields">
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Full name</span>
            <input className="mt-input" value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Email</span>
            <input className="mt-input" type="email" value={user.email} readOnly disabled />
            <span className="mt-field-hint">Your email is your sign-in identity and can't be changed here.</span>
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
      <div className="mt-acct-danger">
        <div>
          <div className="mt-acct-link-t">Sign out</div>
          <div className="mt-acct-link-s">You will need your email and password to sign back in.</div>
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
    <path d="M17.6 9.2c0-.6-.05-1.18-.16-1.74H9v3.3h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.64-3.88 2.64-6.54z" fill="#4285F4" />
    <path d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z" fill="#34A853" />
    <path d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z" fill="#FBBC05" />
    <path d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.47.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z" fill="#EA4335" />
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
            <button className="mt-reset-back mt-mono" type="button" onClick={() => onMode('signin')}>
              Back to sign in
            </button>
            <div className="mt-mono mt-auth-eyebrow">Password reset</div>
            <h2 className="mt-auth-title">Forgot your password?</h2>
            <p className="mt-auth-sub">
              Enter your email and Meant will send you a link to reset your password.
            </p>
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Email</span>
              <input className="mt-input" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
            </label>
            {sent ? <div className="mt-reset-ok mt-mono">Check your inbox for a reset link.</div> : null}
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
          <div className="mt-mono mt-auth-eyebrow">{signup ? 'Create your account' : 'Welcome back'}</div>
          <h2 className="mt-auth-title">{signup ? 'Start shopping the way you mean it.' : 'Sign in to Meant.'}</h2>
          <p className="mt-auth-sub">
            {signup ? 'Set up your profile once and Meant applies it across supported stores.' : 'Pick up right where you left off.'}
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
          <div className="mt-auth-div"><span>or with email</span></div>
          {signup ? (
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Full name</span>
              <input className="mt-input" value={name} onChange={(event) => setName(event.target.value)} />
            </label>
          ) : null}
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Email</span>
            <input className="mt-input" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
          </label>
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Password</span>
            <input className="mt-input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          {!signup ? (
            <button className="mt-auth-forgot mt-mono" type="button" onClick={() => onMode('reset')}>
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
          One account for supported stores. Meant learns what matters to you and quietly filters out the rest.
        </p>
        <div className="mt-auth-pills">
          {['Organic', 'Natural materials', 'Strong reviews', 'Sustainable brands'].map((pill) => (
            <span className="mt-auth-pill" key={pill}>{pill}</span>
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
  const [searchSuggestions, setSearchSuggestions] = useState<string[]>([])
  const [discoveryLoading, setDiscoveryLoading] = useState(false)
  const [discoveryError, setDiscoveryError] = useState<string | null>(null)
  const [popularSearches, setPopularSearches] = useState<SearchSuggestion[]>([])
  const [popularSearchesLoading, setPopularSearchesLoading] = useState(false)
  const [merchants, setMerchants] = useState<MerchantProfile[]>([])
  const [merchantsLoading, setMerchantsLoading] = useState(false)
  const [merchantsError, setMerchantsError] = useState<string | null>(null)
  const [inventoryItems, setInventoryItems] = useState<UserInventoryItemProfile[]>([])
  const [inventoryLoading, setInventoryLoading] = useState(false)
  const [inventoryError, setInventoryError] = useState<string | null>(null)
  const [selectedMerchantId, setSelectedMerchantId] = useStoredState<string | null>('meant.merchant', null)
  const [activeProduct, setActiveProduct] = useState<Product | null>(null)
  const [navProducts, setNavProducts] = useState<readonly Product[]>([])
  const [savedIds, setSavedIds] = useState<ProductId[]>([])
  const [savedProducts, setSavedProducts] = useState<Product[]>([])
  const [savePendingIds, setSavePendingIds] = useState<ProductId[]>([])
  const [compareIds, setCompareIds] = useStoredState<ProductId[]>('meant.compare', [...DEFAULT_COMPARE])
  const [compareProducts, setCompareProducts] = useStoredState<Product[]>('meant.compareProducts', [])
  const [availablePrefs, setAvailablePrefs] = useState<Preference[]>([...PREFERENCES])
  const [prefsOn, setPrefsOn] = useStoredState<PreferenceId[]>('meant.prefsOn', [...DEFAULT_PREFERENCE_IDS])
  const [budget, setBudget] = useStoredState<number | null>('meant.budget', DEFAULT_BUDGET)
  const [deliveryLocations, setDeliveryLocations] = useStoredState<UserLocation[]>(
    'meant.locations',
    initialDeliveryLocations(),
  )
  const [clothingFit, setClothingFit] = useStoredState<ClothingFit>('meant.clothingFit', 'none')
  const [cart, setCart] = useStoredState<CartItem[]>('meant.cart', [...DEFAULT_CART])
  const [cartSnapshots, setCartSnapshots] = useStoredState<Record<string, MerchantCartSnapshot>>(
    'meant.cartSnapshots',
    {},
  )
  const [checkoutMerchant, setCheckoutMerchant] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState<{ merchant: string; message: string } | null>(null)
  const [orders] = useStoredState<Order[]>('meant.orders', [...DEFAULT_ORDERS])
  const [lastPlaced, setLastPlaced] = useState<string | null>(null)
  const [user, setUser] = useStoredState<UserAccount>('meant.user', DEFAULT_USER)
  const [cartPeek, setCartPeek] = useState(false)
  const [accountMenu, setAccountMenu] = useState(false)
  const searchRequestRef = useRef(0)
  const searchSuggestionsRequestRef = useRef(0)
  const inventoryRequestRef = useRef(0)
  const cartRef = useRef<readonly CartItem[]>(cart)
  const cartSnapshotsRef = useRef<Record<string, MerchantCartSnapshot>>(cartSnapshots)
  const compareIdsRef = useRef<readonly ProductId[]>(compareIds)
  const savePendingRef = useRef(new Set<ProductId>())
  const allPreferencesRef = useRef<readonly Preference[]>(availablePrefs)
  const greeting = useBrowserGreeting()
  const closeAccountMenu = useCallback(() => {
    setAccountMenu(false)
  }, [])

  const allPreferences = availablePrefs
  const activePreferences = allPreferences.filter((preference) => prefsOn.includes(preference.id))
  const savedSet = useMemo(() => new Set(savedIds), [savedIds])
  const savePendingSet = useMemo(() => new Set(savePendingIds), [savePendingIds])
  const compareSet = useMemo(() => new Set(compareIds), [compareIds])
  const allKnownProducts = useMemo(() => {
    const seen = new Set<ProductId>()
    return [...searchResults, ...remoteProducts, ...savedProducts, ...PRODUCTS, ...compareProducts].filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
  }, [compareProducts, remoteProducts, savedProducts, searchResults])
  const allKnownProductsMap = useMemo(
    () => new Map<ProductId, Product>(
      allKnownProducts.map((product) => [product.id, product] as const),
    ),
    [allKnownProducts],
  )
  const savedListProducts = useMemo(
    () => savedIds
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
  const restockInventoryItems = useMemo(
    () => inventoryItems.filter((item) => item.restockEnabled),
    [inventoryItems],
  )

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
    cartRef.current = cart
  }, [cart])

  useEffect(() => {
    cartSnapshotsRef.current = cartSnapshots
  }, [cartSnapshots])

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
      return
    }
    let active = true
    setMerchantsLoading(true)
    setMerchantsError(null)
    getMerchants()
      .then((result) => {
        if (!active) return
        setMerchants(result)
      })
      .catch(() => {
        if (!active) return
        setMerchants([])
        setMerchantsError('Could not load merchants')
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
  const loadInventory = useCallback(async (options?: { silent?: boolean }) => {
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
  }, [userId])

  useEffect(() => {
    if (!authed) {
      setInventoryItems([])
      setInventoryError(null)
      setInventoryLoading(false)
      return
    }
    void loadInventory()
  }, [authed, loadInventory])

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
        applySettingsPayload(settings, setAvailablePrefs, setPrefsOn, setBudget, setDeliveryLocations, setClothingFit)
      })
      .catch(() => {
        if (!active) return
        setAvailablePrefs([...PREFERENCES])
      })
    setDiscoveryLoading(true)
    setDiscoveryError(null)
    getProductDiscovery()
      .then((discovery) => {
        if (!active) return
        const snapshots = discovery.savedProducts.map(savedProductFromProfile)
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
        setDiscoveryError('Could not load your saved and recent products.')
      })
      .finally(() => {
        if (!active) return
        setDiscoveryLoading(false)
      })
    setPopularSearchesLoading(true)
    getPopularProductSearches()
      .then((searches) => {
        if (!active) return
        setPopularSearches(searches.map(searchSuggestionFromPopular))
      })
      .catch(() => {
        if (!active) return
        setPopularSearches([])
      })
      .finally(() => {
        if (!active) return
        setPopularSearchesLoading(false)
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
    searchRequestRef.current += 1
    searchSuggestionsRequestRef.current += 1
    setSearchSuggestions([])
    setDiscoveryError(null)
    setDiscoveryLoading(false)
    setPopularSearches([])
    setPopularSearchesLoading(false)
    setSelectedMerchantId(null)
    setSavedIds([])
    setSavedProducts([])
    savePendingRef.current.clear()
    setSavePendingIds([])
  }

  const nav = (next: View) => {
    setView(next)
    setCartPeek(false)
    setAccountMenu(false)
    if (next !== 'orders') {
      setLastPlaced(null)
    }
    window.scrollTo({ top: 0 })
  }

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
    const wasSaved = savedSet.has(product.id)
    if (wasSaved) {
      setSavedIds((current) => current.filter((candidate) => candidate !== product.id))
      setSavedProducts((current) => current.filter((candidate) => candidate.id !== product.id))
      void removeSavedProduct(product.id)
        .catch(() => {
          setSavedProducts((current) => upsertProductSnapshot(current, product))
          setSavedIds((current) => current.includes(product.id) ? current : [product.id, ...current])
        })
        .finally(() => endSaveOperation(product.id))
      return
    }

    setSavedProducts((current) => upsertProductSnapshot(current, product))
    setSavedIds((current) => current.includes(product.id) ? current : [product.id, ...current])
    void saveUserProduct(savedProductInput(product))
      .then((savedProduct) => {
        const snapshot = savedProductFromProfile(savedProduct)
        setSavedProducts((current) => upsertProductSnapshot(current, snapshot))
        setSavedIds((current) => current.includes(snapshot.id) ? current : [snapshot.id, ...current])
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
      : current.length < 4 ? [...current, product.id] : [...current.slice(1), product.id]

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

  const updateStoredCart = (updater: (current: CartItem[]) => CartItem[]) => {
    setCart((current) => {
      const next = updater(current)
      cartRef.current = next
      return next
    })
  }

  const updateStoredCartSnapshots = (
    updater: (current: Record<string, MerchantCartSnapshot>) => Record<string, MerchantCartSnapshot>,
  ) => {
    setCartSnapshots((current) => {
      const next = updater(current)
      cartSnapshotsRef.current = next
      return next
    })
  }

  const storeCartSnapshot = (merchantKey: string, merchant: string, snapshot: CartProfile) => {
    updateStoredCartSnapshots((current) => ({
      ...current,
      [merchantKey]: cartSnapshotFromProfile(snapshot, merchantKey, merchant),
    }))
  }

  const updateMerchantCartItems = (
    merchantKey: string,
    patch: Pick<CartItem, 'syncing' | 'syncError'>,
  ) => {
    updateStoredCart((current) =>
      current.map((item) => cartMerchantKey(item) === merchantKey ? { ...item, ...patch } : item),
    )
  }

  const cartItemMatches = (item: CartItem, id: ProductId, merchant: string) =>
    item.id === id && item.merchant === merchant

  const addProductOfferToCart = async (product: Product, offer: Offer): Promise<boolean> => {
    const productVariantId = offer.productVariantId
    if (!productVariantId || !offerCartable(offer)) {
      return false
    }

    const merchantKey = cartMerchantKey(offer)
    const existingGroup = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)
    const existingItem = cartRef.current.find((item) =>
      item.id === product.id && cartMerchantKey(item) === merchantKey,
    )

    updateStoredCart((current) => {
      const existing = current.find((item) =>
        item.id === product.id && cartMerchantKey(item) === merchantKey,
      )
      if (existing) {
        return current.map((item) =>
          item.id === product.id && cartMerchantKey(item) === merchantKey
            ? {
                ...item,
                merchant: offer.merchant,
                merchantId: offer.merchantId ?? item.merchantId,
                merchantDomain: offer.merchantDomain ?? item.merchantDomain,
                productVariantId,
                variantTitle: offer.variantTitle ?? item.variantTitle,
                cartId: existingGroup?.cartId ?? item.cartId,
                remoteCartId: existingGroup?.remoteCartId ?? item.remoteCartId,
                checkoutUrl: existingGroup?.checkoutUrl ?? item.checkoutUrl,
                qty: item.qty + 1,
                syncing: true,
                syncError: null,
              }
            : item,
        )
      }
      return [
        ...current,
        {
          id: product.id,
          merchant: offer.merchant,
          merchantId: offer.merchantId,
          merchantDomain: offer.merchantDomain,
          productVariantId,
          variantTitle: offer.variantTitle,
          cartId: existingGroup?.cartId,
          remoteCartId: existingGroup?.remoteCartId,
          checkoutUrl: existingGroup?.checkoutUrl,
          qty: 1,
          syncing: true,
          syncError: null,
        },
      ]
    })

    try {
      const currentGroup = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)
      const snapshot = currentGroup?.cartId
        ? await updateCart({
            cartId: currentGroup.cartId,
            addItems: [{ productVariantId, quantity: 1 }],
          })
        : await createCart({
            merchantId: offer.merchantId,
            merchantDomain: offer.merchantDomain,
            addItems: [{ productVariantId, quantity: 1 }],
          })
      updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
      storeCartSnapshot(merchantKey, offer.merchant, snapshot)
      return true
    } catch {
      updateStoredCart((current) => {
        if (!existingItem) {
          return current.filter((item) => !(item.id === product.id && cartMerchantKey(item) === merchantKey))
        }
        return current.map((item) =>
          item.id === product.id && cartMerchantKey(item) === merchantKey
            ? {
                ...item,
                qty: existingItem.qty,
                syncing: false,
                syncError: 'Could not add this item to the merchant cart.',
              }
            : item,
        )
      })
      return false
    }
  }

  const addToCart = (id: ProductId, merchant: string) => {
    const product = allKnownProducts.find((candidate) => candidate.id === id)
    const offer = product?.offers.find((candidate) => candidate.merchant === merchant)
    if (product && offer && offerCartable(offer)) {
      void addProductOfferToCart(product, offer)
      return
    }

    updateStoredCart((current) => {
      const existing = current.find((item) => item.id === id && item.merchant === merchant)
      if (existing) {
        return current.map((item) =>
          item.id === id && item.merchant === merchant
            ? { ...item, qty: item.qty + 1 }
            : item,
        )
      }
      return [...current, { id, merchant, qty: 1 }]
    })
  }

  const removeFromCart = (id: ProductId, merchant: string) => {
    const item = cartRef.current.find((candidate) => cartItemMatches(candidate, id, merchant))
    if (!item) {
      return
    }
    const merchantKey = cartMerchantKey(item)
    updateStoredCart((current) => current.filter((candidate) => !cartItemMatches(candidate, id, merchant)))

    if (!item.cartId || (!item.cartLineId && !item.remoteCartLineId)) {
      return
    }

    void updateCart({
      cartId: item.cartId,
      removeCartLineIds: item.cartLineId ? [item.cartLineId] : undefined,
      removeRemoteCartLineIds: item.remoteCartLineId ? [item.remoteCartLineId] : undefined,
    })
      .then((snapshot) => {
        updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
        storeCartSnapshot(merchantKey, item.merchant, snapshot)
      })
      .catch(() => {
        updateStoredCart((current) => [
          ...current,
          {
            ...item,
            syncing: false,
            syncError: 'Could not remove this item from the merchant cart.',
          },
        ])
      })
  }

  const updateQty = (id: ProductId, merchant: string, qty: number) => {
    if (qty <= 0) {
      removeFromCart(id, merchant)
      return
    }

    const item = cartRef.current.find((candidate) => cartItemMatches(candidate, id, merchant))
    if (!item) {
      return
    }
    const merchantKey = cartMerchantKey(item)
    const shouldSync = Boolean(item.cartId && (item.cartLineId || item.remoteCartLineId))
    updateStoredCart((current) =>
      current.map((candidate) =>
        cartItemMatches(candidate, id, merchant)
          ? { ...candidate, qty, syncing: shouldSync, syncError: null }
          : candidate,
      ),
    )

    if (!shouldSync || !item.cartId) {
      return
    }

    void updateCart({
      cartId: item.cartId,
      updateItems: [
        {
          cartLineId: item.cartLineId,
          remoteCartLineId: item.remoteCartLineId,
          quantity: qty,
        },
      ],
    })
      .then((snapshot) => {
        updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
        storeCartSnapshot(merchantKey, item.merchant, snapshot)
      })
      .catch(() => {
        updateStoredCart((current) =>
          current.map((candidate) =>
            cartItemMatches(candidate, id, merchant)
              ? {
                  ...candidate,
                  qty: item.qty,
                  syncing: false,
                  syncError: 'Could not update this item in the merchant cart.',
                }
              : candidate,
          ),
        )
      })
  }

  const appliedCodesForType = (
    snapshot: MerchantCartSnapshot | undefined,
    type: AppliedCartCodeType,
  ): string[] => {
    return Array.from(new Set(
      (snapshot?.appliedCodes ?? [])
        .filter((code) => code.type === type && code.code)
        .map((code) => code.code as string),
    ))
  }

  const applyCartCode = async (input: ApplyCartCodeInput): Promise<{ ok: boolean; message?: string }> => {
    const code = input.code.trim()
    if (!code) {
      return { ok: false, message: 'Enter a code first.' }
    }

    const snapshot = cartSnapshotsRef.current[input.merchantKey]
    const existingCodes = appliedCodesForType(snapshot, input.type)
    const nextCodes = Array.from(new Set([...existingCodes, code]))

    try {
      const updated = await updateCart({
        cartId: input.cartId,
        discountCodes: input.type === 'DISCOUNT' ? nextCodes : undefined,
        giftCardCodes: input.type === 'GIFT_CARD' ? nextCodes : undefined,
      })
      updateStoredCart((current) => mergeCartSnapshot(current, input.merchantKey, updated))
      storeCartSnapshot(input.merchantKey, input.merchant, updated)
      return { ok: true }
    } catch (error) {
      return {
        ok: false,
        message: error instanceof Error ? error.message : 'The merchant did not accept this code.',
      }
    }
  }

  const removeCartCode = async (input: RemoveCartCodeInput): Promise<{ ok: boolean; message?: string }> => {
    const codeToRemove = input.code.code
    if (!codeToRemove) {
      return { ok: false, message: 'The merchant did not return a removable code for this adjustment.' }
    }
    const snapshot = cartSnapshotsRef.current[input.merchantKey]
    const remainingCodes = appliedCodesForType(snapshot, input.code.type)
      .filter((code) => code !== codeToRemove)

    try {
      const updated = await updateCart({
        cartId: input.cartId,
        discountCodes: input.code.type === 'DISCOUNT' ? remainingCodes : undefined,
        giftCardCodes: input.code.type === 'GIFT_CARD' ? remainingCodes : undefined,
      })
      updateStoredCart((current) => mergeCartSnapshot(current, input.merchantKey, updated))
      storeCartSnapshot(input.merchantKey, input.merchant, updated)
      return { ok: true }
    } catch (error) {
      return {
        ok: false,
        message: error instanceof Error ? error.message : 'The merchant could not remove this code.',
      }
    }
  }

  const updateDeliveryAddress = async (payload: DeliveryAddressPayload): Promise<boolean> => {
    updateMerchantCartItems(payload.merchantKey, { syncing: true, syncError: null })
    try {
      const snapshot = await updateCart({
        cartId: payload.cartId,
        deliveryAddressesToAdd: [deliveryAddressArguments(payload)],
      })
      updateStoredCart((current) => mergeCartSnapshot(current, payload.merchantKey, snapshot))
      storeCartSnapshot(payload.merchantKey, payload.merchant, snapshot)
      return true
    } catch {
      updateMerchantCartItems(payload.merchantKey, {
        syncing: false,
        syncError: 'Could not load delivery options for this merchant.',
      })
      return false
    }
  }

  const updateDeliveryOption = async (payload: DeliveryOptionPayload): Promise<boolean> => {
    const selectedDeliveryOptions = selectedDeliveryOptionsForCart(
      cartRef.current,
      payload.merchantKey,
      payload.group,
      payload.option,
    )
    if (selectedDeliveryOptions.length === 0) {
      updateMerchantCartItems(payload.merchantKey, {
        syncing: false,
        syncError: 'This merchant did not return a selectable delivery handle.',
      })
      return false
    }

    updateMerchantCartItems(payload.merchantKey, { syncing: true, syncError: null })
    try {
      const snapshot = await updateCart({
        cartId: payload.cartId,
        selectedDeliveryOptions,
      })
      updateStoredCart((current) => mergeCartSnapshot(current, payload.merchantKey, snapshot))
      storeCartSnapshot(payload.merchantKey, payload.merchant, snapshot)
      return true
    } catch {
      updateMerchantCartItems(payload.merchantKey, {
        syncing: false,
        syncError: 'Could not update the delivery option.',
      })
      return false
    }
  }

  const applySavedSettings = (settings: UserSettingsProfile) => {
    applySettingsPayload(settings, setAvailablePrefs, setPrefsOn, setBudget, setDeliveryLocations, setClothingFit)
  }

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
      ? options?.merchantId ?? searchMerchantId
      : selectedMerchant?.id ?? null
    const merchantAtSubmit = merchants.find((merchant) => merchant.id === merchantId) ?? null
    const requestId = searchRequestRef.current + 1
    searchRequestRef.current = requestId
    setQuery(submittedQuery)
    setSearchError(null)
    if (append) {
      setSearchLoadingMore(true)
    } else {
      setReply(null)
      setSearchLoading(true)
      setSearchLoadingMore(false)
      setSearchResults([])
      setSearchHasMore(false)
      setSearchNextOffset(null)
      setSearchMerchantId(merchantId)
    }
    try {
      const result = await searchUserProducts({
        query: submittedQuery,
        merchantId,
        offset,
        limit: PRODUCT_SEARCH_PAGE_SIZE,
      })
      if (searchRequestRef.current !== requestId) {
        return
      }
      const products = result.products.map((product) =>
        productFromSearchResult(product, allPreferences),
      )
      setSearchResults((current) => append ? appendProductSnapshots(current, products) : products)
      setRemoteProducts((current) => {
        const byId = new Map(current.map((product) => [product.id, product]))
        products.forEach((product) => byId.set(product.id, product))
        return Array.from(byId.values())
      })
      setSearchHasMore(result.hasMore)
      setSearchNextOffset(result.nextOffset)
      setSearchMerchantId(merchantId)
      if (append) {
        setReply(
          products.length > 0
            ? `Loaded ${products.length} more match${products.length === 1 ? '' : 'es'} for "${submittedQuery}".`
            : `No more matches found for "${submittedQuery}".`,
        )
      } else {
        setReply(
          result.cached
            ? `Showing ${products.length} cached match${products.length === 1 ? '' : 'es'} for "${submittedQuery}".`
            : `Found ${products.length} match${products.length === 1 ? '' : 'es'} for "${submittedQuery}"${merchantAtSubmit ? ` on ${merchantAtSubmit.name}` : ''}.`,
        )
      }
    } catch {
      if (searchRequestRef.current !== requestId) {
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
        if (append) {
          setSearchLoadingMore(false)
        } else {
          setSearchLoading(false)
        }
      }
    }
  }

  const loadMoreSearchResults = () => {
    if (!query || !searchHasMore || searchNextOffset === null || searchLoading || searchLoadingMore) {
      return
    }
    void runProductSearch(query, {
      append: true,
      offset: searchNextOffset,
      merchantId: searchMerchantId,
    })
  }

  const applyAssistantProducts = (products: readonly Product[], sourceQuery: string) => {
    setView('discover')
    setQuery(sourceQuery)
    setReply(`Ask Meant found ${products.length} match${products.length === 1 ? '' : 'es'} for "${sourceQuery}".`)
    setSearchError(null)
    setSearchLoading(false)
    setSearchLoadingMore(false)
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

  const checkout = async (payload: CheckoutPayload) => {
    const merchant = payload.merchant ?? payload.items[0]?.merchant ?? 'merchant'
    const cartId = payload.items.find((item) => item.cartId)?.cartId
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
      const checkoutProfile = await getCartCheckout({ cartId, refresh: true })
      const checkoutUrl = checkoutProfile.checkoutUrl ??
        payload.checkoutUrl ??
        payload.items.find((item) => item.checkoutUrl)?.checkoutUrl
      if (!checkoutUrl) {
        throw new Error('Missing checkout URL')
      }

      const checkoutItems = new Set(payload.items.map((item) => `${item.id}:${item.merchant}`))
      updateStoredCart((current) =>
        current.map((item) =>
          checkoutItems.has(`${item.id}:${item.merchant}`)
            ? {
                ...item,
                remoteCartId: checkoutProfile.remoteCartId ?? item.remoteCartId,
                checkoutUrl,
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
    } catch {
      setCheckoutError({
        merchant,
        message: 'Could not open merchant checkout. Try again.',
      })
    } finally {
      setCheckoutMerchant(null)
    }
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
            onOpen={(product) =>
              openProduct(product, savedListProducts)
            }
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
            onSave={setUser}
            onSignOut={handleSignOut}
            onEditPrefs={() => nav('preferences')}
            onDone={() => nav('discover')}
          />
        )
      case 'discover':
      default:
        return (
          <FeedView
            profile={liveProfile}
            greeting={greeting}
            products={feedProducts}
            restockItems={restockInventoryItems}
            hiddenByShip={hiddenByShip}
            discoveryLoading={discoveryLoading}
            discoveryError={discoveryError}
            popularSearches={popularSearches}
            popularSearchesLoading={popularSearchesLoading}
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
            merchantsError={merchantsError}
            onSubmit={(nextQuery) => {
              void runProductSearch(nextQuery)
            }}
            onLoadMore={loadMoreSearchResults}
            onClear={() => {
              searchRequestRef.current += 1
              setReply(null)
              setQuery('')
              setSearchResults([])
              setSearchError(null)
              setSearchLoading(false)
              setSearchLoadingMore(false)
              setSearchHasMore(false)
              setSearchNextOffset(null)
              setSearchMerchantId(null)
            }}
            onMerchant={(merchant) => {
              setSelectedMerchantId(merchant?.id ?? null)
            }}
            onOpen={(product) => openProduct(product, feedProducts)}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            onToggleSave={toggleSave}
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
      .map((product) => assistantProductContext(product, deliveryLocations)),
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
        hidden={Boolean(activeProduct)}
      />
      <span className="mt-cart-count-debug" aria-hidden>{cartCount}</span>
    </div>
  )
}
