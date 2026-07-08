import {
  type Dispatch,
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
  PREFERENCES,
  PRODUCTS,
  PROFILE,
  PROMPTS,
} from './data'
import { Avatar } from './account/Avatar'
import { AccountView } from './account/AccountView'
import { AuthScreen } from './auth/AuthScreen'
import { useSupabaseAuth } from './auth/useSupabaseAuth'
import { CartPopover } from './cart/CartPopover'
import { CartView } from './cart/CartView'
import {
  CheckoutSheet,
  type ActiveCheckoutSession,
  type CompleteCheckoutInput,
  type UpdateCheckoutAddressInput,
} from './cart/CheckoutSheet'
import { resolveCartableOffer } from './cart/cartOfferResolver'
import type { MerchantCartSnapshot } from './cart/types'
import { useCartController } from './cart/useCartController'
import { ChatDiscoverView } from './chat/ChatDiscoverView'
import { CompareView } from './compare/CompareView'
import { InventoryView } from './inventory/InventoryView'
import { PreferencesView } from './preferences/PreferencesView'
import { DEFAULT_BUDGET, clothingFitLabel } from './preferences/preferencesUtils'
import { upsertInventorySnapshot } from './inventory/inventoryUtils'
import { ProductCard } from './product/ProductCard'
import { ProductModal } from './product/ProductModal'
import {
  appendProductSnapshots,
  mergeProductSnapshot,
  productSnapshotsForIds,
  upsertProductSnapshot,
} from './product/productSnapshots'
import { productFromSearchResult } from './product/productSearchMapping'
import { savedProductFromProfile, savedProductInput } from './product/savedProductMapping'
import { OrdersView } from './orders/OrdersView'
import { orderFromProfile } from './orders/orderMapping'
import { FloatingAsk } from './ask/FloatingAsk'
import { assistantProductContext } from './ask/assistantContext'
import type { AgentActivity, DiscoverFindRequest, ProductDetailChatRequest } from './chat/types'
import { isRenderableSearchProduct } from './chat/utils'
import type { ProductOpenProps, ProductSaveProps } from './product/types'
import { productWithCuratedFields } from './product/productCuration'
import { deliveryLocationSummary } from './shared/locations'
import { DustingContainer } from './shared/DustingContainer'
import { useStoredState } from './shared/storage'
import { CartIcon, EmptyState, ViewHead } from './shared/ui'
import { HeartIcon, MoonIcon, SunIcon } from './shared/icons'
import { Shelf } from './shelf/Shelf'
import type { ShelfDragPayload, ShelfItem, ShelfProductSnapshot } from './shelf/types'
import {
  acceptUserTasteSuggestion,
  completeCartCheckout,
  completeMerchantIdentityAuthorization,
  createCheckoutConsent,
  createUserInventoryItem,
  createUserInventoryPhotoItem,
  deleteUserInventoryItem,
  exportUserInventory,
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
  removeSavedProduct,
  removeUserTasteSignal,
  rejectUserTasteSuggestion,
  revokeMerchantIdentityLink,
  saveUserProduct,
  startMerchantIdentityAuthorization,
  streamUserProductSearch,
  type AssistantChatContextInput,
  type CheckoutProfile,
  type MerchantIdentityLinkProfile,
  type MerchantProfile,
  type ShoppingFilterProfile,
  type UserInventoryItemInput,
  type UserInventoryItemProfile,
  type UserInventoryItemUpdateInput,
  type UserInventoryPhotoInput,
  type UserProductSearchStreamEventProfile,
  type UserTasteProfile,
  updateUserInventoryItem,
  updateNewsletterSubscription,
  updateCartCheckout,
  updateUserTasteSignal,
  updateUserSettings,
  type UserSettingsProfile,
} from '../../lib/apiClient'
import type {
  AuthMode,
  CartItem,
  ClothingFit,
  CheckoutPayload,
  Offer,
  Order,
  Preference,
  PreferenceId,
  Product,
  ProductAgentStage,
  ProductId,
  Theme,
  UserAccount,
  UserLocation,
  View,
} from './types'
import {
  cartLines,
  createOrder,
  normalizedMerchantName,
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

const DEFAULT_GREETING = 'Good afternoon'
const SEARCH_SUGGESTION_COUNT = 4
const PRODUCT_SEARCH_PAGE_SIZE = 20

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
  const [activeCheckout, setActiveCheckout] = useState<ActiveCheckoutSession | null>(null)
  const [checkoutSheetBusy, setCheckoutSheetBusy] = useState(false)
  const [checkoutSheetError, setCheckoutSheetError] = useState<string | null>(null)
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
  const addProductOfferToCartResolved = useCallback(
    async (product: Product, offer: Offer): Promise<boolean> => {
      try {
        const resolved = await resolveCartableOffer({
          product,
          offer,
          location: deliveryLocations[0],
        })
        if (!resolved.ok) {
          return false
        }
        return addProductOfferToCart(resolved.product, resolved.offer)
      } catch {
        return false
      }
    },
    [addProductOfferToCart, deliveryLocations],
  )
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
          newsletter: profile.newsletter ?? current.newsletter,
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

  const updateNewsletter = useCallback(
    async (newsletter: boolean) => {
      const profile = await updateNewsletterSubscription(newsletter)
      setUser((current) => ({
        ...current,
        email: profile.email || current.email,
        newsletter: profile.newsletter ?? newsletter,
      }))
    },
    [setUser],
  )

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

  const runProductSearch = async (nextQuery: string) => {
    const submittedQuery = nextQuery.trim()
    if (!submittedQuery) {
      return
    }
    const merchantId = selectedMerchant?.id ?? null
    const merchantAtSubmit = merchants.find((merchant) => merchant.id === merchantId) ?? null
    const requestId = searchRequestRef.current + 1
    searchRequestRef.current = requestId
    searchAbortRef.current?.abort()
    const controller = new AbortController()
    searchAbortRef.current = controller
    setQuery(submittedQuery)
    setSearchError(null)
    setReply(null)
    setSearchLoading(true)
    setSearchResults([])
    setProductSearchActivities([])
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
      return mergedProducts
    }
    try {
      await streamUserProductSearch(
        {
          query: submittedQuery,
          merchantId,
          offset: 0,
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
            setProductSearchActivities((current) =>
              upsertAgentActivity(
                current.map((activity) => ({ ...activity, state: 'done' })),
                event,
                'done',
              ),
            )
            setReply(
              event.cached
                ? `Showing ${displayedCount} cached match${displayedCount === 1 ? '' : 'es'} for "${submittedQuery}".`
                : `Found ${displayedCount} match${displayedCount === 1 ? '' : 'es'} for "${submittedQuery}"${merchantAtSubmit ? ` on ${merchantAtSubmit.name}` : ''}.`,
            )
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
      setSearchResults([])
      setSearchError('Product search failed. Please try again.')
    } finally {
      if (searchRequestRef.current === requestId) {
        if (searchAbortRef.current === controller) {
          searchAbortRef.current = null
        }
        setSearchLoading(false)
      }
    }
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
    setProductSearchActivities([])
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

  const updateCartWithCheckoutProfile = (
    payload: CheckoutPayload,
    checkoutProfile: CheckoutProfile,
  ) => {
    const checkoutItems = new Set(payload.items.map((item) => `${item.id}:${item.merchant}`))
    updateStoredCart((current) =>
      current.map((item) =>
        checkoutItems.has(`${item.id}:${item.merchant}`)
          ? {
              ...item,
              remoteCartId: checkoutProfile.remoteCartId ?? item.remoteCartId,
              checkoutUrl: checkoutProfile.checkoutUrl ?? item.checkoutUrl,
              continueUrl: checkoutProfile.continueUrl ?? item.continueUrl,
              syncError: null,
            }
          : item,
      ),
    )
  }

  const completeLocalOrder = (payload: CheckoutPayload) => {
    const order = createOrder(payload)
    const checkoutItems = new Set(payload.items.map((item) => `${item.id}:${item.merchant}`))
    setOrders((current) => [order, ...current])
    setCart((current) =>
      current.filter((item) => !checkoutItems.has(`${item.id}:${item.merchant}`)),
    )
    setCheckoutError(null)
    setLastPlaced(order.id)
  }

  const startCheckout = async (
    payload: CheckoutPayload,
    source: ActiveCheckoutSession['source'],
  ) => {
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
    setCheckoutSheetError(null)
    try {
      const checkoutProfile = await getCartCheckout({ cartId, refresh: true })
      updateCartWithCheckoutProfile(payload, checkoutProfile)
      setActiveCheckout({
        cartId,
        merchant,
        source,
        items: payload.items,
        saved: payload.saved,
        savedNote: payload.savedNote,
        profile: checkoutProfile,
        completion: null,
      })
    } catch {
      setCheckoutError({
        merchant,
        message: 'Could not start checkout. Try again.',
      })
    } finally {
      setCheckoutMerchant(null)
    }
  }

  const checkout = async (payload: CheckoutPayload) => {
    await startCheckout(payload, 'cart')
  }

  const checkoutInChat = async (payload: CheckoutPayload) => {
    if (payload.items.length === 0) {
      return
    }
    await startCheckout(payload, 'chat')
  }

  const refreshActiveCheckout = async () => {
    if (!activeCheckout) {
      return
    }
    setCheckoutSheetBusy(true)
    setCheckoutSheetError(null)
    try {
      const checkoutProfile = await getCartCheckout({
        cartId: activeCheckout.cartId,
        refresh: true,
      })
      updateCartWithCheckoutProfile(activeCheckout, checkoutProfile)
      setActiveCheckout((current) =>
        current
          ? {
              ...current,
              profile: checkoutProfile,
              completion: null,
            }
          : current,
      )
    } catch {
      setCheckoutSheetError('Could not refresh checkout.')
    } finally {
      setCheckoutSheetBusy(false)
    }
  }

  const updateActiveCheckoutAddress = async ({
    buyer,
    shippingAddress,
  }: UpdateCheckoutAddressInput) => {
    if (!activeCheckout) {
      return
    }
    setCheckoutSheetBusy(true)
    setCheckoutSheetError(null)
    try {
      const checkoutProfile = await updateCartCheckout({
        cartId: activeCheckout.cartId,
        buyer,
        shippingAddress,
      })
      updateCartWithCheckoutProfile(activeCheckout, checkoutProfile)
      setActiveCheckout((current) =>
        current
          ? {
              ...current,
              profile: checkoutProfile,
              completion: null,
            }
          : current,
      )
    } catch {
      setCheckoutSheetError('Could not update checkout address.')
    } finally {
      setCheckoutSheetBusy(false)
    }
  }

  const completeActiveCheckout = async ({ handler, token }: CompleteCheckoutInput) => {
    if (!activeCheckout) {
      return
    }
    const checkoutId = activeCheckout.profile.checkoutId
    const amountMinor = activeCheckout.profile.totalAmountMinor
    const currency = activeCheckout.profile.currency
    if (
      !checkoutId ||
      typeof amountMinor !== 'number' ||
      !Number.isFinite(amountMinor) ||
      !currency
    ) {
      setCheckoutSheetError('Checkout is missing merchant total details.')
      return
    }
    setCheckoutSheetBusy(true)
    setCheckoutSheetError(null)
    try {
      const consent = await createCheckoutConsent({
        cartId: activeCheckout.cartId,
        checkoutId,
        paymentInstrumentReference: `${handler}:${token}`,
        shippingMethod: 'selected',
      })
      if (!consent.buyerConsentId) {
        throw new Error('Missing buyer consent id')
      }
      const completion = await completeCartCheckout({
        cartId: activeCheckout.cartId,
        buyerConsentId: consent.buyerConsentId,
        checkoutId,
        handler,
        amountMinor,
        currency,
        token,
        idempotencyKey:
          typeof crypto !== 'undefined' && 'randomUUID' in crypto
            ? `web-${crypto.randomUUID()}`
            : `web-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      })
      setActiveCheckout((current) => (current ? { ...current, completion } : current))
      if (completion.status === 'COMPLETED') {
        completeLocalOrder(activeCheckout)
        void loadInventory({ silent: true })
        void loadOrders({ silent: true })
      } else if (completion.status === 'RECOVERABLE_ERROR') {
        setCheckoutSheetError(null)
      } else if (
        completion.status === 'UNRECOVERABLE_ERROR' ||
        completion.status === 'SECURITY_LOCKED'
      ) {
        setCheckoutSheetError(completion.messages?.[0] ?? 'Checkout could not be completed.')
      }
    } catch {
      setCheckoutSheetError('Could not complete checkout.')
    } finally {
      setCheckoutSheetBusy(false)
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
            onNewsletterChange={updateNewsletter}
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
            newsletter={user.newsletter}
            onSubmit={(nextQuery) => {
              void runProductSearch(nextQuery)
            }}
            onClear={() => {
              searchRequestRef.current += 1
              searchAbortRef.current?.abort()
              searchAbortRef.current = null
              setReply(null)
              setQuery('')
              setSearchResults([])
              setSearchError(null)
              setSearchLoading(false)
              setProductSearchActivities([])
            }}
            onMerchant={(merchant) => {
              setSelectedMerchantId(merchant?.id ?? null)
            }}
            onOpen={(product, products) => openProduct(product, products ?? feedProducts)}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            onToggleSave={toggleSave}
            onAddProductToCart={addProductOfferToCartResolved}
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
            onNewsletterChange={updateNewsletter}
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
        onAddToCart={addProductOfferToCartResolved}
        onAskInChat={sendProductQuestionToDiscover}
        canPrev={canNavPrev}
        canNext={canNavNext}
        onPrev={() => navigateProduct(-1)}
        onNext={() => navigateProduct(1)}
      />
      {activeCheckout ? (
        <CheckoutSheet
          session={activeCheckout}
          busy={checkoutSheetBusy}
          error={checkoutSheetError}
          buyerDefaults={{ email: user.email, name: user.name }}
          deliveryLocation={deliveryLocations[0] ?? null}
          onClose={() => {
            setActiveCheckout(null)
            setCheckoutSheetError(null)
          }}
          onRefresh={refreshActiveCheckout}
          onUpdateAddress={updateActiveCheckoutAddress}
          onComplete={completeActiveCheckout}
        />
      ) : null}
      <FloatingAsk
        contextLabel={askContext.label}
        context={assistantContext}
        suggestions={askContext.suggestions}
        preferences={allPreferences}
        onProducts={applyAssistantProducts}
        onProductOpen={(product) => openProduct(product, [product])}
        onAddProductToCart={addProductOfferToCartResolved}
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
