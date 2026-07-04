import {
  type ChangeEvent,
  type CSSProperties,
  type Dispatch,
  type FormEvent,
  type PointerEvent as ReactPointerEvent,
  type SetStateAction,
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
import { cartableOfferForProduct } from './cart/utils'
import { ChatDiscoverView } from './chat/ChatDiscoverView'
import { ProductCard } from './product/ProductCard'
import { ProductModal } from './product/ProductModal'
import { ProductSearchLoading } from './chat/ProductSearchLoading'
import { AskComposer } from './ask/AskComposer'
import { AskThread } from './ask/AskThread'
import type {
  AgentActivity,
  AssistantProductAction,
  DiscoverFindRequest,
  ProductDetailChatRequest,
} from './chat/types'
import { isRenderableSearchProduct, searchProductReviewInsight } from './chat/utils'
import type { AskPanelSize, Message } from './ask/types'
import type { ProductOpenProps, ProductSaveProps } from './product/types'
import { productCuratedFields, productWithCuratedFields } from './product/productCuration'
import { deliveryLocationSummary } from './shared/locations'
import { DustingContainer } from './shared/DustingContainer'
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
  HeartIcon,
  HistoryIcon,
  MatchRing,
  MoonIcon,
  PlusIcon,
  PrefChip,
  SearchIcon,
  SunIcon,
} from './shared/icons'
import { Shelf } from './shelf/Shelf'
import type { ShelfDragPayload, ShelfItem, ShelfProductSnapshot } from './shelf/types'
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
  type MerchantProfile,
  type OrderProfile,
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
  Theme,
  UserAccount,
  UserLocation,
  View,
} from './types'
import {
  IMPORT_ASK,
  bestOffer,
  cartLines,
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
} from './utils'

const EMPTY_TASTE_PROFILE: UserTasteProfile = {
  profileHash: '',
  signals: [],
  suggestions: [],
}

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

function nonEmptyImageUrl(value?: string | null): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function firstProductMediaImage(media?: readonly ProductMedia[]): string | null {
  return (
    media
      ?.filter((item) => item.type.toLowerCase() === 'image')
      .map((item) => nonEmptyImageUrl(item.url))
      .find((url): url is string => Boolean(url)) ?? null
  )
}

function mergeProductMediaSnapshots(
  existingMedia?: readonly ProductMedia[],
  nextMedia?: readonly ProductMedia[],
): ProductMedia[] {
  const merged: ProductMedia[] = []
  const seen = new Set<string>()
  for (const item of [...(nextMedia ?? []), ...(existingMedia ?? [])]) {
    const url = nonEmptyImageUrl(item.url)
    if (!url) {
      continue
    }
    const key = `${item.type.toLowerCase()}|${url}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    merged.push({ ...item, url })
  }
  return merged
}

function mergeProductSnapshot(existing: Product | undefined, product: Product): Product {
  if (!existing) {
    const imageUrl = nonEmptyImageUrl(product.imageUrl) ?? firstProductMediaImage(product.media)
    return imageUrl && imageUrl !== product.imageUrl ? { ...product, imageUrl } : product
  }

  const media = mergeProductMediaSnapshots(existing.media, product.media)
  const imageUrl =
    nonEmptyImageUrl(product.imageUrl) ??
    firstProductMediaImage(media) ??
    nonEmptyImageUrl(existing.imageUrl) ??
    firstProductMediaImage(existing.media)

  return {
    ...product,
    imageUrl,
    media: media.length > 0 ? media : product.media,
  }
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
    return [mergeProductSnapshot(undefined, product), ...products]
  }
  return products.map((candidate, index) =>
    index === existingIndex ? mergeProductSnapshot(candidate, product) : candidate,
  )
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
      merged.push(mergeProductSnapshot(undefined, product))
      return
    }
    merged[index] = mergeProductSnapshot(merged[index], product)
  })
  return merged
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

const nextShelfUid = () =>
  `shelf-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`

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
      const currentById = new Map(current.map((product) => [product.id, product] as const))
      const mergedProducts = products
        .map((product) => mergeProductSnapshot(currentById.get(product.id), product))
        .filter(isRenderableSearchProduct)
      if (!append) {
        return mergedProducts
      }
      return appendProductSnapshots(current.filter(isRenderableSearchProduct), mergedProducts)
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
            setRemoteProducts((current) =>
              appendProductSnapshots(current, products).filter(isRenderableSearchProduct),
            )
            setProductSearchActivities((current) => upsertAgentActivity(current, event))
          },
          onDone: (event) => {
            if (searchRequestRef.current !== requestId) {
              return
            }
            const products = orderedStreamProducts(event, 'curated')
            const displayedCount = noteFinalStreamProducts(products)
            setSearchResults((current) => finalStreamProducts(current, products))
            setRemoteProducts((current) =>
              appendProductSnapshots(current, products).filter(isRenderableSearchProduct),
            )
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
    const renderableProducts = products.filter(isRenderableSearchProduct)
    searchAbortRef.current?.abort()
    searchAbortRef.current = null
    setView('discover')
    setQuery(sourceQuery)
    setReply(
      `Ask Meant found ${renderableProducts.length} match${renderableProducts.length === 1 ? '' : 'es'} for "${sourceQuery}".`,
    )
    setSearchError(null)
    setSearchLoading(false)
    setSearchLoadingMore(false)
    setProductSearchActivities([])
    setSearchHasMore(false)
    setSearchNextOffset(null)
    setSearchMerchantId(null)
    setSearchResults([...renderableProducts])
    setRemoteProducts((current) => {
      const byId = new Map(current.map((product) => [product.id, product]))
      renderableProducts.forEach((product) => byId.set(product.id, product))
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
        DustingContainer={DustingContainer}
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
