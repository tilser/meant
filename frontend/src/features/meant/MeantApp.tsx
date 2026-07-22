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
import { authProviderAvatarUrl } from './auth/authProviderProfile'
import { useSupabaseAuth } from './auth/useSupabaseAuth'
import { CartPopover } from './cart/CartPopover'
import { CartCheckoutDialog } from './cart/CartCheckoutDialog'
import { CartView } from './cart/CartView'
import type { ActiveCheckoutSession, CheckoutAssistantContext } from './cart/checkoutTypes'
import { resolveCartableOffer } from './cart/cartOfferResolver'
import type { MerchantCartSnapshot, MerchantCartStateReplacement } from './cart/types'
import { useCartController } from './cart/useCartController'
import { ChatDiscoverView } from './chat/ChatDiscoverView'
import { AgentDiscoverView } from './agent/AgentDiscoverView'
import { agentActionQueueFor } from './agent/actionQueue'
import {
  cartStateReplacementsFromAgentArtifacts,
  currentAgentProductSnapshots,
  latestCartSnapshotArtifacts,
  mergeAgentProductSnapshots,
  type AgentProductSnapshot,
} from './agent/artifactMapping'
import {
  agentCartPartitionFingerprints,
  agentCartReplacementUnchangedSinceSubmission,
  agentCartStateFingerprint,
  isTerminalAgentRunStatus,
  registerPendingAgentCartRun,
  type PendingAgentCartRun,
} from './agent/cartSync'
import { isAgenticDiscoverEnabled } from './agent/featureFlag'
import { CompareView } from './compare/CompareView'
import { InventoryView } from './inventory/InventoryView'
import {
  createInventoryItemWithPhoto,
  deleteInventoryItemWithPhoto,
  updateInventoryItemWithPhoto,
} from './inventory/inventoryMutations'
import { PreferencesView } from './preferences/PreferencesView'
import { DEFAULT_BUDGET, clothingFitLabel } from './preferences/preferencesUtils'
import {
  type UserInventoryItemDraftInput,
  upsertInventorySnapshot,
} from './inventory/inventoryUtils'
import { ProductCard } from './product/ProductCard'
import { ProductModal } from './product/ProductModal'
import { productFromCanonical } from './product/groupedProductMapping'
import {
  appendProductSnapshots,
  confirmedSavedProductSnapshot,
  productSnapshotsForIds,
  refreshedSavedProductSnapshot,
  savedProductRefreshShell,
  uniqueProductSnapshotsByPriority,
  upsertProductSnapshot,
} from './product/productSnapshots'
import { productFromSearchResult } from './product/productSearchMapping'
import { savedProductFromProfile, savedProductInput } from './product/savedProductMapping'
import { OrdersView } from './orders/OrdersView'
import { orderFromProfile } from './orders/orderMapping'
import type {
  DiscoverFindRequest,
  DiscoverProductSearchTurnInput,
  DiscoverProductSearchTurnResult,
  ProductDetailChatRequest,
} from './chat/types'
import { isRenderableSearchProduct } from './chat/utils'
import type { ProductOpenProps, ProductSaveProps } from './product/types'
import { productWithCuratedFields } from './product/productCuration'
import { deliveryLocationSummary } from './shared/locations'
import {
  beginAccountOwnedOperation,
  endAccountOwnedOperation,
  isAccountOwnedOperationCurrent,
  type AccountOwnedOperation,
} from './shared/accountOwnedOperation'
import {
  accountSessionStorageKey,
  accountStorageKey,
  purgeLegacyAccountStorage,
} from './shared/accountStorage'
import { DustingContainer } from './shared/DustingContainer'
import { useSessionStoredState, useStoredState } from './shared/storage'
import { CartIcon, EmptyState, ViewHead } from './shared/ui'
import { HeartIcon, MoonIcon, SunIcon } from './shared/icons'
import { Shelf } from './shelf/Shelf'
import type { ShelfDragPayload, ShelfItem, ShelfProductSnapshot } from './shelf/types'
import {
  acceptUserTasteSuggestion,
  assistCartCheckout,
  completeMerchantIdentityAuthorization,
  deleteUserProductSearchPreference,
  exportUserInventory,
  getCartCheckout,
  getCompleteAgentConversation,
  getCurrentUser,
  getAgentRun,
  getMerchantIdentityLinks,
  getMerchants,
  getOrders,
  getProfilePictureUrl,
  getProductDiscovery,
  getSavedProduct,
  getUserInventoryItems,
  getUserProductSearchSuggestions,
  getUserSettings,
  getUserTasteProfile,
  removeSavedProduct,
  removeUserTasteSignal,
  rejectUserTasteSuggestion,
  rehydrateCanonicalProducts,
  qualifyProductSearch,
  revokeMerchantIdentityLink,
  saveUserProduct,
  startMerchantIdentityAuthorization,
  searchGroupedProducts,
  searchSimilarGroupedProducts,
  type CheckoutAssistantMessage,
  type CheckoutAssistantResult,
  type CheckoutProfile,
  type MerchantIdentityLinkProfile,
  type MerchantProfile,
  type ShoppingFilterProfile,
  type UserInventoryItemProfile,
  type UserInventoryItemUpdateInput,
  type UserProductSearchPreferenceProfile,
  type UserTasteProfile,
  updateNewsletterSubscription,
  updateCartCheckout,
  updateUserTasteSignal,
  updateUserSettings,
  ApiError,
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
  ProductId,
  Theme,
  UserAccount,
  UserLocation,
  View,
} from './types'
import {
  cartItemIdentity,
  cartLines,
  cartMerchantKey,
  normalizedMerchantName,
  productsForLocation,
  productsForClothingFit,
} from './utils'

const EMPTY_TASTE_PROFILE: UserTasteProfile = {
  profileHash: '',
  signals: [],
  suggestions: [],
}

purgeLegacyAccountStorage()

interface NavOptions {
  home?: boolean
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
  cartMutationBlocked,
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
  cartMutationBlocked: boolean
  cartPeek: boolean
  accountMenu: boolean
  onNav: (view: View, options?: NavOptions) => void
  onToggleTheme: () => void
  onToggleCart: () => void
  onToggleAccount: () => void
  onCloseAccount: () => void
  onRemoveFromCart: (id: ProductId, merchant: string, identity?: string) => void
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
        onClick={() => onNav('discover', { home: true })}
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
            onClick={() => onNav(key as View, key === 'discover' ? { home: true } : undefined)}
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
              mutationBlocked={cartMutationBlocked}
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
  const userId = session?.user?.id
  const userEmail = session?.user?.email
  const commerceOperationQueue = useMemo(
    () => agentActionQueueFor(userId ?? 'signed-out'),
    [userId],
  )
  const providerAvatar = authProviderAvatarUrl(session?.user)
  const [theme, setTheme] = useStoredState<Theme>('meant.theme', 'light')
  const [remoteProducts, setRemoteProducts] = useState<Product[]>([])
  const [agentProductSnapshots, setAgentProductSnapshots] = useState<AgentProductSnapshot[]>([])
  const [pendingAgentCartRuns, setPendingAgentCartRuns] = useSessionStoredState<
    Record<string, PendingAgentCartRun>
  >(accountSessionStorageKey('meant.agentPendingCartRuns', userId), {})
  const [accountStateOwnerId, setAccountStateOwnerId] = useState<string | null>(userId ?? null)
  const [accountStateVersion, setAccountStateVersion] = useState(0)
  const [searchSuggestions, setSearchSuggestions] = useState<string[]>([])
  const [merchants, setMerchants] = useState<MerchantProfile[]>([])
  const [merchantsLoading, setMerchantsLoading] = useState(false)
  const [merchantsError, setMerchantsError] = useState<string | null>(null)
  const [merchantIdentityLinks, setMerchantIdentityLinks] = useState<MerchantIdentityLinkProfile[]>(
    [],
  )
  const [merchantIdentityLinksLoading, setMerchantIdentityLinksLoading] = useState(false)
  const [merchantIdentityLinksError, setMerchantIdentityLinksError] = useState<string | null>(null)
  const [inventoryItems, setInventoryItems] = useState<UserInventoryItemProfile[]>([])
  const [inventoryLoading, setInventoryLoading] = useState(false)
  const [inventoryError, setInventoryError] = useState<string | null>(null)
  const [activeProduct, setActiveProduct] = useState<Product | null>(null)
  const [activeProductResearchQuery, setActiveProductResearchQuery] = useState<string | null>(null)
  const [navProducts, setNavProducts] = useState<readonly Product[]>([])
  const [savedProductDetailLoadingId, setSavedProductDetailLoadingId] = useState<ProductId | null>(
    null,
  )
  const [shelf, setShelf] = useSessionStoredState<ShelfItem[]>(
    accountSessionStorageKey('meant.shelf', userId),
    [],
  )
  const [shelfOpen, setShelfOpen] = useState(false)
  const [shelfFlashMessageId, setShelfFlashMessageId] = useState<string | null>(null)
  const [discoverFindRequest, setDiscoverFindRequest] = useState<DiscoverFindRequest | null>(null)
  const [discoverHomeRequestId, setDiscoverHomeRequestId] = useState(0)
  const [productDetailChatRequest, setProductDetailChatRequest] =
    useState<ProductDetailChatRequest | null>(null)
  const [savedIds, setSavedIds] = useState<ProductId[]>([])
  const [savedProducts, setSavedProducts] = useState<Product[]>([])
  const [savePendingIds, setSavePendingIds] = useState<ProductId[]>([])
  const [compareIds, setCompareIds] = useSessionStoredState<ProductId[]>(
    accountSessionStorageKey('meant.compare', userId),
    [...DEFAULT_COMPARE],
  )
  const [compareProducts, setCompareProducts] = useSessionStoredState<Product[]>(
    accountSessionStorageKey('meant.compareProducts', userId),
    [],
  )
  const [availablePrefs, setAvailablePrefs] = useState<Preference[]>([...PREFERENCES])
  const [prefsOn, setPrefsOn] = useStoredState<PreferenceId[]>(
    accountStorageKey('meant.prefsOn', userId),
    [...DEFAULT_PREFERENCE_IDS],
  )
  const [tasteProfile, setTasteProfile] = useState<UserTasteProfile>(EMPTY_TASTE_PROFILE)
  const [budget, setBudget] = useStoredState<number | null>(
    accountStorageKey('meant.budget', userId),
    DEFAULT_BUDGET,
  )
  const [deliveryLocations, setDeliveryLocations] = useStoredState<UserLocation[]>(
    accountStorageKey('meant.locations', userId),
    [],
  )
  const [clothingFit, setClothingFit] = useStoredState<ClothingFit>(
    accountStorageKey('meant.clothingFit', userId),
    'none',
  )
  const [productSearchPreferences, setProductSearchPreferences] = useState<
    UserProductSearchPreferenceProfile[]
  >([])
  const [productSearchPreferencesBusy, setProductSearchPreferencesBusy] = useState(false)
  const [productSearchPreferencesError, setProductSearchPreferencesError] = useState<string | null>(
    null,
  )
  const [checkoutMerchantKey, setCheckoutMerchantKey] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState<{
    merchant: string
    merchantKey: string
    message: string
  } | null>(null)
  const [activeCheckout, setActiveCheckout] = useState<ActiveCheckoutSession | null>(null)
  const [checkoutFlowBusy, setCheckoutFlowBusy] = useState(false)
  const [checkoutFlowError, setCheckoutFlowError] = useState<string | null>(null)
  const [orders, setOrders] = useState<Order[]>([])
  const [ordersLoading, setOrdersLoading] = useState(false)
  const [ordersError, setOrdersError] = useState<string | null>(null)
  const [user, setUser] = useStoredState<UserAccount>(
    accountStorageKey('meant.user', userId),
    DEFAULT_USER,
  )
  const [cartPeek, setCartPeek] = useState(false)
  const [accountMenu, setAccountMenu] = useState(false)
  const searchRequestSequenceRef = useRef(0)
  const searchRequestsRef = useRef(
    new Map<string, { requestId: number; controller: AbortController }>(),
  )
  const activeUserIdRef = useRef(userId)
  activeUserIdRef.current = userId
  const pendingAgentCartRunsRef = useRef(pendingAgentCartRuns)
  pendingAgentCartRunsRef.current = pendingAgentCartRuns
  const productSearchPreferencesQueueRef = useRef<Promise<void>>(Promise.resolve())
  const productSearchPreferencesPendingOperationsRef = useRef(0)
  const productSearchPreferencesRefreshPendingRef = useRef(false)
  const productSearchPreferencesSessionRef = useRef(0)
  const savedProductDetailRequestRef = useRef<{
    productId: ProductId
    ownerId: string
    controller: AbortController
  } | null>(null)
  const searchSuggestionsRequestRef = useRef(0)
  const inventoryRequestRef = useRef(0)
  const ordersRequestRef = useRef(0)
  const checkoutOperationSequenceRef = useRef(0)
  const checkoutOperationRef = useRef<{ token: number; ownerId: string } | null>(null)
  const activeCheckoutRef = useRef(activeCheckout)
  activeCheckoutRef.current = activeCheckout
  const activeCheckoutSequenceRef = useRef(0)
  const compareIdsRef = useRef<readonly ProductId[]>(compareIds)
  const savePendingRef = useRef(new Map<ProductId, AccountOwnedOperation<ProductId>>())
  const allPreferencesRef = useRef<readonly Preference[]>(availablePrefs)
  const greeting = useBrowserGreeting()
  const closeAccountMenu = useCallback(() => {
    setAccountMenu(false)
  }, [])

  const updateActiveCheckoutState = useCallback(
    (update: SetStateAction<ActiveCheckoutSession | null>) => {
      const current = activeCheckoutRef.current
      const next =
        typeof update === 'function'
          ? (update as (value: ActiveCheckoutSession | null) => ActiveCheckoutSession | null)(
              current,
            )
          : update
      if (next !== current) activeCheckoutSequenceRef.current += 1
      activeCheckoutRef.current = next
      setActiveCheckout(next)
    },
    [],
  )

  const updatePendingAgentCartRuns = useCallback(
    (update: SetStateAction<Record<string, PendingAgentCartRun>>) => {
      const current = pendingAgentCartRunsRef.current
      const next =
        typeof update === 'function'
          ? (
              update as (
                value: Record<string, PendingAgentCartRun>,
              ) => Record<string, PendingAgentCartRun>
            )(current)
          : update
      pendingAgentCartRunsRef.current = next
      setPendingAgentCartRuns(next)
    },
    [setPendingAgentCartRuns],
  )

  useEffect(() => {
    searchRequestsRef.current.forEach(({ controller }) => controller.abort())
    searchRequestsRef.current.clear()
    searchSuggestionsRequestRef.current += 1
    inventoryRequestRef.current += 1
    ordersRequestRef.current += 1
    checkoutOperationSequenceRef.current += 1
    checkoutOperationRef.current = null
    productSearchPreferencesSessionRef.current += 1
    savedProductDetailRequestRef.current?.controller.abort()
    savedProductDetailRequestRef.current = null
    setSavedProductDetailLoadingId(null)
    setRemoteProducts([])
    setAgentProductSnapshots([])
    setSavedIds([])
    setSavedProducts([])
    setActiveProduct(null)
    setActiveProductResearchQuery(null)
    setNavProducts([])
    setDiscoverFindRequest(null)
    setProductDetailChatRequest(null)
    setSearchSuggestions([])
    setMerchants([])
    setMerchantsLoading(false)
    setMerchantsError(null)
    setMerchantIdentityLinks([])
    setMerchantIdentityLinksLoading(false)
    setMerchantIdentityLinksError(null)
    setInventoryItems([])
    setInventoryLoading(false)
    setInventoryError(null)
    setOrders([])
    setOrdersLoading(false)
    setOrdersError(null)
    setTasteProfile(EMPTY_TASTE_PROFILE)
    setProductSearchPreferences([])
    setProductSearchPreferencesError(null)
    updateActiveCheckoutState(null)
    setCheckoutMerchantKey(null)
    setCheckoutError(null)
    setCheckoutFlowBusy(false)
    setCheckoutFlowError(null)
    setCartPeek(false)
    savePendingRef.current.clear()
    setSavePendingIds([])
    setAccountStateOwnerId(userId ?? null)
    setAccountStateVersion((current) => current + 1)
  }, [updateActiveCheckoutState, userId])

  const enqueueProductSearchPreferencesOperation = useCallback((operation: () => Promise<void>) => {
    productSearchPreferencesPendingOperationsRef.current += 1
    setProductSearchPreferencesBusy(true)
    const queued = productSearchPreferencesQueueRef.current.then(operation)
    productSearchPreferencesQueueRef.current = queued.catch(() => undefined)
    void queued
      .finally(() => {
        productSearchPreferencesPendingOperationsRef.current -= 1
        if (productSearchPreferencesPendingOperationsRef.current === 0) {
          setProductSearchPreferencesBusy(false)
        }
      })
      .catch(() => undefined)
    return queued
  }, [])

  const refreshMerchantIdentityLinks = useCallback(async () => {
    const requestedUserId = userId
    if (!requestedUserId) {
      setMerchantIdentityLinks([])
      setMerchantIdentityLinksError(null)
      return
    }
    setMerchantIdentityLinksLoading(true)
    setMerchantIdentityLinksError(null)
    try {
      const links = await getMerchantIdentityLinks({ expectedUserId: requestedUserId })
      if (activeUserIdRef.current !== requestedUserId) return
      setMerchantIdentityLinks(links)
    } catch {
      if (activeUserIdRef.current !== requestedUserId) return
      setMerchantIdentityLinks([])
      setMerchantIdentityLinksError('Could not load connected stores')
    } finally {
      if (activeUserIdRef.current === requestedUserId) {
        setMerchantIdentityLinksLoading(false)
      }
    }
  }, [userId])

  const allPreferences = availablePrefs
  compareIdsRef.current = compareIds
  allPreferencesRef.current = allPreferences
  const activePreferences = allPreferences.filter((preference) => prefsOn.includes(preference.id))
  const accountStateCurrent = accountStateOwnerId === (userId ?? null)
  const currentSearchSuggestions = accountStateCurrent ? searchSuggestions : []
  const currentMerchants = accountStateCurrent ? merchants : []
  const currentMerchantIdentityLinks = accountStateCurrent ? merchantIdentityLinks : []
  const currentMerchantIdentityLinksLoading = accountStateCurrent
    ? merchantIdentityLinksLoading
    : false
  const currentMerchantIdentityLinksError = accountStateCurrent ? merchantIdentityLinksError : null
  const currentInventoryItems = accountStateCurrent ? inventoryItems : []
  const currentInventoryLoading = accountStateCurrent ? inventoryLoading : false
  const currentInventoryError = accountStateCurrent ? inventoryError : null
  const currentOrders = accountStateCurrent ? orders : []
  const currentOrdersLoading = accountStateCurrent ? ordersLoading : false
  const currentOrdersError = accountStateCurrent ? ordersError : null
  const currentTasteProfile = accountStateCurrent ? tasteProfile : EMPTY_TASTE_PROFILE
  const currentProductSearchPreferences = accountStateCurrent ? productSearchPreferences : []
  const currentProductSearchPreferencesBusy = accountStateCurrent
    ? productSearchPreferencesBusy
    : false
  const currentProductSearchPreferencesError = accountStateCurrent
    ? productSearchPreferencesError
    : null
  const currentRemoteProducts = useMemo(
    () => (accountStateCurrent ? remoteProducts : []),
    [accountStateCurrent, remoteProducts],
  )
  const currentAgentProducts = useMemo(
    () => (accountStateCurrent ? agentProductSnapshots.map((snapshot) => snapshot.product) : []),
    [accountStateCurrent, agentProductSnapshots],
  )
  const currentSavedIds = useMemo(
    () => (accountStateCurrent ? savedIds : []),
    [accountStateCurrent, savedIds],
  )
  const currentSavedProducts = useMemo(
    () => (accountStateCurrent ? savedProducts : []),
    [accountStateCurrent, savedProducts],
  )
  const currentActiveProduct = accountStateCurrent ? activeProduct : null
  const currentProductDetailChatRequest = accountStateCurrent ? productDetailChatRequest : null
  const currentDiscoverFindRequest = accountStateCurrent ? discoverFindRequest : null
  const savedSet = useMemo(() => new Set(currentSavedIds), [currentSavedIds])
  const savePendingSet = useMemo(
    () => new Set(accountStateCurrent ? savePendingIds : []),
    [accountStateCurrent, savePendingIds],
  )
  const compareSet = useMemo(() => new Set(compareIds), [compareIds])
  const allKnownProducts = useMemo(
    () =>
      uniqueProductSnapshotsByPriority(
        currentSavedProducts,
        currentRemoteProducts,
        currentAgentProducts,
        PRODUCTS,
        compareProducts,
      ),
    [compareProducts, currentAgentProducts, currentRemoteProducts, currentSavedProducts],
  )
  const allKnownProductsMap = useMemo(
    () =>
      new Map<ProductId, Product>(
        allKnownProducts.map((product) => [product.id, product] as const),
      ),
    [allKnownProducts],
  )
  const allKnownProductsRef = useRef(allKnownProducts)
  allKnownProductsRef.current = allKnownProducts
  const {
    cart,
    cartSnapshots,
    getCurrentCart,
    getCurrentCartSnapshots,
    setCart,
    replaceMerchantCartStates,
    updateStoredCart,
    addProductOfferToCart,
    addSelectedOfferToCart,
    addToCart,
    removeFromCart,
    updateQty,
    applyCartCode,
    removeCartCode,
    updateDeliveryAddress,
    updateDeliveryOption,
  } = useCartController(allKnownProducts, userId)
  const replaceMerchantCartStatesRef = useRef(replaceMerchantCartStates)
  replaceMerchantCartStatesRef.current = replaceMerchantCartStates

  const runCommerceMutation = useCallback(
    async <T,>(operation: () => Promise<T>): Promise<T> => {
      return commerceOperationQueue.enqueue(operation)
    },
    [commerceOperationQueue],
  )

  const addSelectedOfferToCartGuarded = useCallback(
    async (...args: Parameters<typeof addSelectedOfferToCart>) => {
      return runCommerceMutation(() => addSelectedOfferToCart(...args))
    },
    [addSelectedOfferToCart, runCommerceMutation],
  )

  const addToCartGuarded = useCallback(
    async (...args: Parameters<typeof addToCart>) => {
      return runCommerceMutation(() => addToCart(...args))
    },
    [addToCart, runCommerceMutation],
  )

  const removeFromCartGuarded = useCallback(
    async (...args: Parameters<typeof removeFromCart>) => {
      return runCommerceMutation(() => removeFromCart(...args))
    },
    [removeFromCart, runCommerceMutation],
  )

  const updateQtyGuarded = useCallback(
    async (...args: Parameters<typeof updateQty>) => {
      return runCommerceMutation(() => updateQty(...args))
    },
    [runCommerceMutation, updateQty],
  )

  const applyCartCodeGuarded = useCallback(
    (...args: Parameters<typeof applyCartCode>) => {
      return runCommerceMutation(() => applyCartCode(...args))
    },
    [applyCartCode, runCommerceMutation],
  )

  const removeCartCodeGuarded = useCallback(
    (...args: Parameters<typeof removeCartCode>) => {
      return runCommerceMutation(() => removeCartCode(...args))
    },
    [removeCartCode, runCommerceMutation],
  )

  const updateDeliveryAddressGuarded = useCallback(
    (...args: Parameters<typeof updateDeliveryAddress>) => {
      return runCommerceMutation(() => updateDeliveryAddress(...args))
    },
    [runCommerceMutation, updateDeliveryAddress],
  )

  const updateDeliveryOptionGuarded = useCallback(
    (...args: Parameters<typeof updateDeliveryOption>) => {
      return runCommerceMutation(() => updateDeliveryOption(...args))
    },
    [runCommerceMutation, updateDeliveryOption],
  )

  const addProductOfferToCartResolved = useCallback(
    async (product: Product, offer: Offer): Promise<boolean> => {
      return runCommerceMutation(async () => {
        const requestedUserId = userId
        if (!requestedUserId || activeUserIdRef.current !== requestedUserId) {
          return false
        }
        const exactOfferKey = offer.offerKey?.trim()
        if (exactOfferKey) {
          return addSelectedOfferToCart(product, exactOfferKey)
        }
        try {
          const resolved = await resolveCartableOffer({
            product,
            offer,
            location: deliveryLocations[0],
            expectedUserId: requestedUserId,
          })
          if (!resolved.ok || activeUserIdRef.current !== requestedUserId) {
            return false
          }
          return addProductOfferToCart(resolved.product, resolved.offer)
        } catch {
          return false
        }
      })
    },
    [addProductOfferToCart, addSelectedOfferToCart, deliveryLocations, runCommerceMutation, userId],
  )
  const savedListProducts = useMemo(
    () =>
      currentSavedIds
        .map((id) => allKnownProductsMap.get(id))
        .filter((product): product is Product => Boolean(product)),
    [allKnownProductsMap, currentSavedIds],
  )
  const discoveryProducts = useMemo(() => {
    const seen = new Set<ProductId>()
    return [...savedListProducts, ...currentRemoteProducts].filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
  }, [currentRemoteProducts, savedListProducts])

  const liveProfile = useMemo(
    () => ({
      ...PROFILE,
      name: firstNameFromName(user.name),
    }),
    [user.name],
  )

  const baseFeed = discoveryProducts
  const shippingScopedFeedProducts = productsForLocation(baseFeed, deliveryLocations)
  const unscopedFeedProducts = productsForClothingFit(shippingScopedFeedProducts, clothingFit)
  const feedProducts = unscopedFeedProducts
  const hiddenByShip = baseFeed.length - shippingScopedFeedProducts.length

  const refreshSavedProductForOpen = useCallback(
    (product: Product, force = false) => {
      const requestOwnerId = userId
      if (!requestOwnerId || activeUserIdRef.current !== requestOwnerId) {
        return
      }
      if (!force && !savedSet.has(product.id)) {
        savedProductDetailRequestRef.current?.controller.abort()
        savedProductDetailRequestRef.current = null
        setSavedProductDetailLoadingId(null)
        return
      }
      savedProductDetailRequestRef.current?.controller.abort()
      const controller = new AbortController()
      savedProductDetailRequestRef.current = {
        productId: product.id,
        ownerId: requestOwnerId,
        controller,
      }
      setSavedProductDetailLoadingId(product.id)
      setSavedProducts((current) =>
        current.map((candidate) =>
          candidate.id === product.id ? savedProductRefreshShell(candidate) : candidate,
        ),
      )
      setNavProducts((current) =>
        current.map((candidate) =>
          candidate.id === product.id ? savedProductRefreshShell(candidate) : candidate,
        ),
      )
      setActiveProduct((current) =>
        current?.id === product.id ? savedProductRefreshShell(current) : current,
      )
      void getSavedProduct(product.id, {
        expectedUserId: requestOwnerId,
        signal: controller.signal,
      })
        .then((profile) => {
          const currentRequest = savedProductDetailRequestRef.current
          if (
            controller.signal.aborted ||
            activeUserIdRef.current !== requestOwnerId ||
            currentRequest?.controller !== controller ||
            currentRequest.productId !== product.id ||
            currentRequest.ownerId !== requestOwnerId
          ) {
            return
          }
          const snapshot = savedProductFromProfile(profile, allPreferencesRef.current)
          setSavedProducts((current) => {
            const existing = current.find((candidate) => candidate.id === product.id)
            return upsertProductSnapshot(current, refreshedSavedProductSnapshot(existing, snapshot))
          })
          setNavProducts((current) =>
            current.map((candidate) =>
              candidate.id === product.id
                ? refreshedSavedProductSnapshot(candidate, snapshot)
                : candidate,
            ),
          )
          setActiveProduct((current) =>
            current?.id === product.id ? refreshedSavedProductSnapshot(current, snapshot) : current,
          )
        })
        .catch(() => {
          // Keep the saved presentation shell. A later open starts a new durable refresh attempt.
        })
        .finally(() => {
          if (savedProductDetailRequestRef.current?.controller === controller) {
            savedProductDetailRequestRef.current = null
            setSavedProductDetailLoadingId((current) => (current === product.id ? null : current))
          }
        })
    },
    [savedSet, userId],
  )

  const openProduct = useCallback(
    (product: Product, list?: readonly Product[], researchQuery?: string | null) => {
      setActiveProduct(product)
      setActiveProductResearchQuery(researchQuery?.trim() || null)
      setNavProducts(list ?? [product])
      refreshSavedProductForOpen(product)
    },
    [refreshSavedProductForOpen],
  )

  const navIndex = currentActiveProduct
    ? navProducts.findIndex((candidate) => candidate.id === currentActiveProduct.id)
    : -1
  const canNavPrev = navIndex > 0
  const canNavNext = navIndex >= 0 && navIndex < navProducts.length - 1

  const navigateProduct = useCallback(
    (direction: -1 | 1) => {
      if (!activeProduct) return
      const index = navProducts.findIndex((candidate) => candidate.id === activeProduct.id)
      const next = index < 0 ? undefined : navProducts[index + direction]
      if (!next) return
      setActiveProduct(next)
      refreshSavedProductForOpen(next)
    },
    [activeProduct, navProducts, refreshSavedProductForOpen],
  )

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])

  useEffect(() => {
    const requestedUserId = userId
    if (!requestedUserId) {
      setMerchants([])
      setMerchantsLoading(false)
      setMerchantsError(null)
      return
    }
    let active = true
    setMerchantsLoading(true)
    setMerchantsError(null)
    getMerchants({ expectedUserId: requestedUserId })
      .then((result) => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setMerchants(result)
        setMerchantsLoading(false)
      })
      .catch((error: unknown) => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setMerchants([])
        setMerchantsLoading(false)
        setMerchantsError(
          error instanceof Error && error.message.trim()
            ? error.message
            : 'Failed to load merchants',
        )
      })
    return () => {
      active = false
    }
  }, [userId])

  useEffect(() => {
    void refreshMerchantIdentityLinks()
  }, [refreshMerchantIdentityLinks])

  useEffect(() => {
    const requestedUserId = userId
    if (!requestedUserId) {
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
    completeMerchantIdentityAuthorization({
      state,
      code,
      issuer,
      expectedUserId: requestedUserId,
    })
      .then((link) => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setMerchantIdentityLinks((current) => [
          link,
          ...current.filter((candidate) => candidate.merchantId !== link.merchantId),
        ])
        setMerchantIdentityLinksError(null)
      })
      .catch(() => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setMerchantIdentityLinksError('Could not complete store connection')
      })
    return () => {
      active = false
    }
  }, [userId])

  // Once authenticated, hydrate the profile from the backend (which creates the users row on first
  // call). Falls back to the JWT email if the backend is unreachable so the shell still renders.
  // Keyed on the user identity rather than the whole session object so periodic token refreshes
  // (which replace `session` hourly) don't trigger a redundant re-fetch.
  const loadInventory = useCallback(
    async (options?: { silent?: boolean }) => {
      const requestedUserId = userId
      if (!requestedUserId) {
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
        const items = await getUserInventoryItems({ expectedUserId: requestedUserId })
        if (
          inventoryRequestRef.current !== requestId ||
          activeUserIdRef.current !== requestedUserId
        ) {
          return
        }
        setInventoryItems(items)
      } catch {
        if (
          inventoryRequestRef.current !== requestId ||
          activeUserIdRef.current !== requestedUserId
        ) {
          return
        }
        setInventoryError('Could not load inventory')
      } finally {
        if (
          inventoryRequestRef.current === requestId &&
          activeUserIdRef.current === requestedUserId &&
          !options?.silent
        ) {
          setInventoryLoading(false)
        }
      }
    },
    [userId],
  )

  const loadOrders = useCallback(
    async (options?: { silent?: boolean }) => {
      const requestedUserId = userId
      if (!requestedUserId) {
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
        const result = await getOrders({ expectedUserId: requestedUserId })
        if (ordersRequestRef.current !== requestId || activeUserIdRef.current !== requestedUserId) {
          return
        }
        setOrders(result.map(orderFromProfile))
      } catch {
        if (ordersRequestRef.current !== requestId || activeUserIdRef.current !== requestedUserId) {
          return
        }
        setOrdersError('Could not load orders')
        setOrders([])
      } finally {
        if (
          ordersRequestRef.current === requestId &&
          activeUserIdRef.current === requestedUserId &&
          !options?.silent
        ) {
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
    const requestedUserId = userId
    if (!requestedUserId) {
      setSearchSuggestions([])
      return
    }

    const requestId = searchSuggestionsRequestRef.current + 1
    searchSuggestionsRequestRef.current = requestId
    try {
      const result = await getUserProductSearchSuggestions({ expectedUserId: requestedUserId })
      if (
        searchSuggestionsRequestRef.current !== requestId ||
        activeUserIdRef.current !== requestedUserId
      ) {
        return
      }
      setSearchSuggestions(completeSearchSuggestions(result.suggestions))
    } catch {
      if (
        searchSuggestionsRequestRef.current !== requestId ||
        activeUserIdRef.current !== requestedUserId
      ) {
        return
      }
      setSearchSuggestions([...PROMPTS])
    }
  }, [userId])

  useEffect(() => {
    void refreshSearchSuggestions()
  }, [refreshSearchSuggestions])

  useEffect(() => {
    const requestedUserId = userId
    if (!requestedUserId) {
      return
    }
    let active = true
    const settingsSession = productSearchPreferencesSessionRef.current + 1
    productSearchPreferencesSessionRef.current = settingsSession
    setProductSearchPreferences([])
    setProductSearchPreferencesError(null)
    getCurrentUser()
      .then(async (profile) => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        const uploadedAvatar = await getProfilePictureUrl(profile.profilePicturePath).catch(
          () => null,
        )
        if (!active || activeUserIdRef.current !== requestedUserId) return
        const fullName = [profile.firstName, profile.surname].filter(Boolean).join(' ').trim()
        setUser((current) => ({
          ...current,
          name: fullName || DEFAULT_USER.name,
          email: profile.email || current.email,
          avatar: uploadedAvatar ?? providerAvatar,
          avatarPath: profile.profilePicturePath ?? null,
          newsletter: profile.newsletter ?? current.newsletter,
        }))
      })
      .catch(() => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setUser((current) => ({
          ...current,
          email: userEmail || current.email,
          avatar: current.avatar ?? providerAvatar,
        }))
      })
    void enqueueProductSearchPreferencesOperation(async () => {
      try {
        if (
          !active ||
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        const settings = await getUserSettings({ expectedUserId: requestedUserId })
        if (
          !active ||
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        applySettingsPayload(
          settings,
          setAvailablePrefs,
          setPrefsOn,
          setBudget,
          setDeliveryLocations,
          setClothingFit,
        )
        setProductSearchPreferences(settings.productSearchPreferences ?? [])
        setProductSearchPreferencesError(null)
      } catch {
        if (
          !active ||
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        setAvailablePrefs([...PREFERENCES])
        setProductSearchPreferencesError('Could not load saved sizes. Retry before editing them.')
      }
    })
    getUserTasteProfile({ expectedUserId: requestedUserId })
      .then((profile) => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setTasteProfile(profile)
      })
      .catch(() => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setTasteProfile(EMPTY_TASTE_PROFILE)
      })
    getProductDiscovery({ expectedUserId: requestedUserId })
      .then((discovery) => {
        if (!active || activeUserIdRef.current !== requestedUserId) return
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
        if (!active || activeUserIdRef.current !== requestedUserId) return
        setSavedProducts([])
        setSavedIds([])
      })
    return () => {
      active = false
      if (productSearchPreferencesSessionRef.current === settingsSession) {
        productSearchPreferencesSessionRef.current += 1
      }
    }
  }, [
    enqueueProductSearchPreferencesOperation,
    userId,
    userEmail,
    providerAvatar,
    setUser,
    setPrefsOn,
    setBudget,
    setDeliveryLocations,
    setClothingFit,
  ])

  const handleSignOut = () => {
    void signOut()
    setAuthMode('signin')
    setView('discover')
    setRemoteProducts([])
    setTasteProfile(EMPTY_TASTE_PROFILE)
    setProductSearchPreferences([])
    setProductSearchPreferencesBusy(false)
    setProductSearchPreferencesError(null)
    productSearchPreferencesSessionRef.current += 1
    searchRequestsRef.current.forEach(({ controller }) => controller.abort())
    searchRequestsRef.current.clear()
    savedProductDetailRequestRef.current?.controller.abort()
    savedProductDetailRequestRef.current = null
    setSavedProductDetailLoadingId(null)
    searchSuggestionsRequestRef.current += 1
    setSearchSuggestions([])
    setMerchantIdentityLinks([])
    setMerchantIdentityLinksError(null)
    setMerchantIdentityLinksLoading(false)
    setSavedIds([])
    setSavedProducts([])
    savePendingRef.current.clear()
    setSavePendingIds([])
  }

  const connectMerchantIdentity = (merchant: MerchantProfile) => {
    const requestedUserId = requireCurrentAccountUser()
    setMerchantIdentityLinksError(null)
    void startMerchantIdentityAuthorization(merchant.id, { expectedUserId: requestedUserId })
      .then((authorization) => {
        if (activeUserIdRef.current !== requestedUserId) return
        window.location.assign(authorization.authorizationUrl)
      })
      .catch(() => {
        if (activeUserIdRef.current !== requestedUserId) return
        setMerchantIdentityLinksError(`Could not start account linking for ${merchant.name}`)
      })
  }

  const revokeMerchantIdentity = (merchantId: string) => {
    const requestedUserId = requireCurrentAccountUser()
    setMerchantIdentityLinksError(null)
    setMerchantIdentityLinks((current) => current.filter((link) => link.merchantId !== merchantId))
    void revokeMerchantIdentityLink(merchantId, { expectedUserId: requestedUserId })
      .then(() => {
        if (activeUserIdRef.current === requestedUserId) void refreshMerchantIdentityLinks()
      })
      .catch(() => {
        if (activeUserIdRef.current !== requestedUserId) return
        setMerchantIdentityLinksError('Could not revoke store connection')
        void refreshMerchantIdentityLinks()
      })
  }

  const updateNewsletter = useCallback(
    async (newsletter: boolean) => {
      const requestedUserId = userId
      if (!requestedUserId || activeUserIdRef.current !== requestedUserId) {
        throw new Error('Account changed before newsletter update')
      }
      const profile = await updateNewsletterSubscription(newsletter, {
        expectedUserId: requestedUserId,
      })
      if (activeUserIdRef.current !== requestedUserId) {
        throw new Error('Account changed during newsletter update')
      }
      setUser((current) => ({
        ...current,
        email: profile.email || current.email,
        newsletter: profile.newsletter ?? newsletter,
      }))
    },
    [setUser, userId],
  )

  const nav = useCallback(
    (next: View, options?: NavOptions) => {
      setView(next)
      if (next === 'discover' && options?.home) {
        setDiscoverHomeRequestId((current) => current + 1)
      }
      setCartPeek(false)
      setAccountMenu(false)
      if (next === 'orders') {
        void loadOrders({ silent: true })
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

  const registerAgentProducts = useCallback(
    (snapshots: readonly AgentProductSnapshot[]) => {
      if (
        !accountStateCurrent ||
        accountStateVersion === 0 ||
        !userId ||
        activeUserIdRef.current !== userId ||
        snapshots.length === 0
      ) {
        return
      }
      setAgentProductSnapshots((current) => mergeAgentProductSnapshots(current, snapshots))
    },
    [accountStateCurrent, accountStateVersion, userId],
  )

  const captureAgentCartRevision = useCallback(() => {
    if (!userId || activeUserIdRef.current !== userId) return undefined
    return agentCartPartitionFingerprints(getCurrentCart(), getCurrentCartSnapshots())
  }, [getCurrentCart, getCurrentCartSnapshots, userId])

  const registerAgentCartSnapshot = useCallback(
    (
      replacements: readonly MerchantCartStateReplacement[],
      submittedCartRevision: ReturnType<typeof agentCartPartitionFingerprints> | undefined,
    ): readonly CartItem[] => {
      if (!userId || activeUserIdRef.current !== userId) return getCurrentCart()
      const currentRevision = agentCartPartitionFingerprints(
        getCurrentCart(),
        getCurrentCartSnapshots(),
      )
      const safeReplacements = submittedCartRevision
        ? replacements.filter((replacement) =>
            agentCartReplacementUnchangedSinceSubmission(
              replacement,
              submittedCartRevision,
              currentRevision,
            ),
          )
        : []
      if (safeReplacements.length > 0) {
        replaceMerchantCartStatesRef.current(safeReplacements)
      }
      return getCurrentCart()
    },
    [getCurrentCart, getCurrentCartSnapshots, userId],
  )

  const registerAgentCartRun = useCallback(
    (
      runId: string,
      conversationId: string,
      submittedCartRevision: ReturnType<typeof agentCartPartitionFingerprints> | undefined,
    ) => {
      if (!userId || activeUserIdRef.current !== userId) return
      updatePendingAgentCartRuns((current) =>
        registerPendingAgentCartRun(current, runId, conversationId, submittedCartRevision),
      )
    },
    [updatePendingAgentCartRuns, userId],
  )

  useEffect(() => {
    if (!accountStateCurrent || !userId || Object.keys(pendingAgentCartRuns).length === 0) {
      return
    }
    const controller = new AbortController()
    let retryTimer: number | null = null
    let retryDelay = 750
    const removePendingRun = (runId: string, expected: unknown) => {
      updatePendingAgentCartRuns((current) => {
        if (current[runId] !== expected) return current
        const next = { ...current }
        delete next[runId]
        return next
      })
    }
    const synchronize = async () => {
      let retryNeeded = false
      for (const [runId, pendingRun] of Object.entries(pendingAgentCartRuns)) {
        if (controller.signal.aborted || activeUserIdRef.current !== userId) return
        if (
          !pendingRun ||
          typeof pendingRun !== 'object' ||
          typeof pendingRun.conversationId !== 'string' ||
          !pendingRun.conversationId.trim()
        ) {
          removePendingRun(runId, pendingRun)
          continue
        }
        const { conversationId } = pendingRun
        try {
          const run = await getAgentRun(runId, {
            expectedUserId: userId,
            signal: controller.signal,
          })
          if (!isTerminalAgentRunStatus(run.status)) {
            retryNeeded = true
            continue
          }
          const conversation = await getCompleteAgentConversation(conversationId, {
            expectedUserId: userId,
            signal: controller.signal,
            pageSize: 200,
          })
          if (controller.signal.aborted || activeUserIdRef.current !== userId) return
          const productSnapshots = currentAgentProductSnapshots(conversation.artifacts)
          registerAgentProducts(productSnapshots)
          const runArtifacts = conversation.artifacts.filter((artifact) => artifact.runId === runId)
          const currentCartArtifacts = latestCartSnapshotArtifacts(runArtifacts)
          const replacementProducts = [
            ...allKnownProductsRef.current,
            ...productSnapshots.map((snapshot) => snapshot.product),
          ]
          const replacements = cartStateReplacementsFromAgentArtifacts(
            currentCartArtifacts,
            replacementProducts,
          )
          const liveCart = getCurrentCart()
          const liveSnapshots = getCurrentCartSnapshots()
          const currentCartRevision = agentCartPartitionFingerprints(liveCart, liveSnapshots)
          const safeReplacements = pendingRun.cartFingerprints
            ? replacements.filter((replacement) =>
                agentCartReplacementUnchangedSinceSubmission(
                  replacement,
                  pendingRun.cartFingerprints,
                  currentCartRevision,
                ),
              )
            : pendingRun.cartFingerprint === agentCartStateFingerprint(liveCart, liveSnapshots)
              ? replacements
              : []
          if (safeReplacements.length > 0) {
            replaceMerchantCartStatesRef.current(safeReplacements)
          }
          updatePendingAgentCartRuns((current) => {
            if (current[runId]?.conversationId !== conversationId) return current
            const next = { ...current }
            delete next[runId]
            return next
          })
          if (safeReplacements.length > 0) return
        } catch (error) {
          if (controller.signal.aborted) return
          if (error instanceof ApiError && [400, 403, 404].includes(error.status)) {
            removePendingRun(runId, pendingRun)
          } else {
            retryNeeded = true
          }
        }
      }
      if (retryNeeded && !controller.signal.aborted) {
        retryTimer = window.setTimeout(() => void synchronize(), retryDelay)
        retryDelay = Math.min(retryDelay * 2, 10_000)
      }
    }
    void synchronize()
    return () => {
      controller.abort()
      if (retryTimer !== null) window.clearTimeout(retryTimer)
    }
  }, [
    accountStateCurrent,
    getCurrentCart,
    getCurrentCartSnapshots,
    pendingAgentCartRuns,
    registerAgentProducts,
    updatePendingAgentCartRuns,
    userId,
  ])

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
      setActiveProductResearchQuery(null)
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
    const operationOwnerId = userId
    if (!operationOwnerId || activeUserIdRef.current !== operationOwnerId) {
      return null
    }
    const operation = beginAccountOwnedOperation(savePendingRef.current, id, operationOwnerId)
    if (!operation) {
      return null
    }
    setSavePendingIds(Array.from(savePendingRef.current.keys()))
    return operation
  }

  const isSaveOperationCurrent = (operation: AccountOwnedOperation<ProductId>) =>
    isAccountOwnedOperationCurrent(savePendingRef.current, operation, activeUserIdRef.current)

  const endSaveOperation = (operation: AccountOwnedOperation<ProductId>) => {
    if (!endAccountOwnedOperation(savePendingRef.current, operation)) {
      return
    }
    setSavePendingIds(Array.from(savePendingRef.current.keys()))
  }

  const toggleSave = (product: Product) => {
    const saveOperation = beginSaveOperation(product.id)
    if (!saveOperation) {
      return
    }
    const productSnapshot = productWithCuratedFields(product, allPreferencesRef.current)
    const wasSaved = savedSet.has(product.id)
    if (wasSaved) {
      const pendingDetailRequest = savedProductDetailRequestRef.current
      if (pendingDetailRequest?.productId === product.id) {
        pendingDetailRequest.controller.abort()
        savedProductDetailRequestRef.current = null
        setSavedProductDetailLoadingId((current) => (current === product.id ? null : current))
      }
      const savedSnapshot = savedProducts.find((candidate) => candidate.id === product.id)
      const rollbackSnapshot = savedSnapshot
        ? refreshedSavedProductSnapshot(productSnapshot, savedSnapshot)
        : savedProductRefreshShell(productSnapshot)
      setSavedIds((current) => current.filter((candidate) => candidate !== product.id))
      setSavedProducts((current) => current.filter((candidate) => candidate.id !== product.id))
      setCompareProducts((current) =>
        current.map((candidate) =>
          candidate.id === product.id ? savedProductRefreshShell(candidate) : candidate,
        ),
      )
      setNavProducts((current) =>
        current.map((candidate) =>
          candidate.id === product.id ? savedProductRefreshShell(candidate) : candidate,
        ),
      )
      setActiveProduct((current) =>
        current?.id === product.id ? savedProductRefreshShell(current) : current,
      )
      void removeSavedProduct(product.id, { expectedUserId: saveOperation.ownerId })
        .catch(() => {
          if (!isSaveOperationCurrent(saveOperation)) {
            return
          }
          setSavedProducts((current) => upsertProductSnapshot(current, rollbackSnapshot))
          setSavedIds((current) =>
            current.includes(product.id) ? current : [product.id, ...current],
          )
          setCompareProducts((current) =>
            current.map((candidate) =>
              candidate.id === product.id
                ? refreshedSavedProductSnapshot(candidate, rollbackSnapshot)
                : candidate,
            ),
          )
          setNavProducts((current) =>
            current.map((candidate) =>
              candidate.id === product.id
                ? refreshedSavedProductSnapshot(candidate, rollbackSnapshot)
                : candidate,
            ),
          )
          setActiveProduct((current) =>
            current?.id === product.id
              ? refreshedSavedProductSnapshot(current, rollbackSnapshot)
              : current,
          )
          if (!rollbackSnapshot.offers.some((offer) => Boolean(offer.offerKey?.trim()))) {
            refreshSavedProductForOpen(rollbackSnapshot, true)
          }
        })
        .finally(() => endSaveOperation(saveOperation))
      return
    }

    setSavedProducts((current) => upsertProductSnapshot(current, productSnapshot))
    setSavedIds((current) => (current.includes(product.id) ? current : [product.id, ...current]))
    const selectedOfferKey =
      product.canonicalProduct?.recommendedOfferKey?.trim() ||
      product.offers.find((offer) => offer.offerKey?.trim())?.offerKey?.trim() ||
      null
    void saveUserProduct(savedProductInput(product, allPreferencesRef.current, selectedOfferKey), {
      expectedUserId: saveOperation.ownerId,
    })
      .then((savedProduct) => {
        if (!isSaveOperationCurrent(saveOperation)) {
          return
        }
        const snapshot = savedProductFromProfile(savedProduct, allPreferencesRef.current)
        const confirmedSnapshot = confirmedSavedProductSnapshot(productSnapshot, snapshot)
        setSavedProducts((current) => upsertProductSnapshot(current, confirmedSnapshot))
        setSavedIds((current) =>
          current.includes(snapshot.id) ? current : [snapshot.id, ...current],
        )
        refreshSavedProductForOpen(confirmedSnapshot, true)
        void refreshTasteProfile()
      })
      .catch(() => {
        if (!isSaveOperationCurrent(saveOperation)) {
          return
        }
        setSavedProducts((current) => current.filter((candidate) => candidate.id !== product.id))
        setSavedIds((current) => current.filter((candidate) => candidate !== product.id))
      })
      .finally(() => endSaveOperation(saveOperation))
  }

  const updateSavedChoice = (product: Product, offerKey: string) => {
    const exactOfferKey = offerKey.trim()
    if (!exactOfferKey) {
      return
    }
    const saveOperation = beginSaveOperation(product.id)
    if (!saveOperation) {
      return
    }
    const productSnapshot = productWithCuratedFields(product, allPreferencesRef.current)
    void saveUserProduct(savedProductInput(product, allPreferencesRef.current, exactOfferKey), {
      expectedUserId: saveOperation.ownerId,
    })
      .then((savedProduct) => {
        if (!isSaveOperationCurrent(saveOperation)) {
          return
        }
        const snapshot = savedProductFromProfile(savedProduct, allPreferencesRef.current)
        const confirmedSnapshot = confirmedSavedProductSnapshot(productSnapshot, snapshot)
        setSavedProducts((current) => upsertProductSnapshot(current, confirmedSnapshot))
        setSavedIds((current) =>
          current.includes(snapshot.id) ? current : [snapshot.id, ...current],
        )
        setCompareProducts((current) =>
          current.map((candidate) =>
            candidate.id === product.id
              ? refreshedSavedProductSnapshot(candidate, confirmedSnapshot)
              : candidate,
          ),
        )
        setNavProducts((current) =>
          current.map((candidate) =>
            candidate.id === product.id
              ? refreshedSavedProductSnapshot(candidate, confirmedSnapshot)
              : candidate,
          ),
        )
        setActiveProduct((current) =>
          current?.id === product.id
            ? refreshedSavedProductSnapshot(current, confirmedSnapshot)
            : current,
        )
        refreshSavedProductForOpen(confirmedSnapshot, true)
      })
      .catch(() => undefined)
      .finally(() => endSaveOperation(saveOperation))
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
      setActiveProductResearchQuery(null)
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

  const requireCurrentAccountUser = () => {
    const expectedUserId = userId
    if (!expectedUserId || activeUserIdRef.current !== expectedUserId) {
      throw new Error('Account changed during user operation')
    }
    return expectedUserId
  }

  const refreshProductSearchPreferences = useCallback(() => {
    const requestedUserId = userId
    if (!requestedUserId || activeUserIdRef.current !== requestedUserId) {
      setProductSearchPreferences([])
      setProductSearchPreferencesError(null)
      return
    }
    if (productSearchPreferencesRefreshPendingRef.current) {
      return
    }
    productSearchPreferencesRefreshPendingRef.current = true
    const settingsSession = productSearchPreferencesSessionRef.current
    setProductSearchPreferencesError(null)
    void enqueueProductSearchPreferencesOperation(async () => {
      try {
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        const settings = await getUserSettings({ expectedUserId: requestedUserId })
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        setProductSearchPreferences(settings.productSearchPreferences ?? [])
      } catch {
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        setProductSearchPreferencesError(
          'Could not refresh saved sizes. Retry before editing them.',
        )
      } finally {
        productSearchPreferencesRefreshPendingRef.current = false
      }
    })
  }, [enqueueProductSearchPreferencesOperation, userId])

  const refreshTasteProfile = useCallback(async () => {
    const requestedUserId = userId
    if (!requestedUserId || activeUserIdRef.current !== requestedUserId) {
      setTasteProfile(EMPTY_TASTE_PROFILE)
      return
    }
    try {
      const profile = await getUserTasteProfile({ expectedUserId: requestedUserId })
      if (activeUserIdRef.current !== requestedUserId) return
      setTasteProfile(profile)
    } catch {
      if (activeUserIdRef.current !== requestedUserId) return
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
    const requestedUserId = requireCurrentAccountUser()
    try {
      const settings = await updateUserSettings(input, { expectedUserId: requestedUserId })
      if (activeUserIdRef.current !== requestedUserId) return false
      applySavedSettings(settings)
      void refreshSearchSuggestions()
      void refreshTasteProfile()
      return true
    } catch {
      return false
    }
  }

  const saveProductSearchPreference = (preference: UserProductSearchPreferenceProfile) => {
    const requestedUserId = requireCurrentAccountUser()
    const settingsSession = productSearchPreferencesSessionRef.current
    setProductSearchPreferencesError(null)
    void enqueueProductSearchPreferencesOperation(async () => {
      try {
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        const settings = await updateUserSettings(
          { productSearchPreferences: [preference] },
          { expectedUserId: requestedUserId },
        )
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        applySavedSettings(settings)
        setProductSearchPreferences(settings.productSearchPreferences ?? [])
        void refreshSearchSuggestions()
        void refreshTasteProfile()
      } catch {
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        setProductSearchPreferencesError('Saved size was not updated. Please try again.')
      }
    })
  }

  const removeProductSearchPreference = (scope: string) => {
    const requestedUserId = requireCurrentAccountUser()
    const settingsSession = productSearchPreferencesSessionRef.current
    setProductSearchPreferencesError(null)
    void enqueueProductSearchPreferencesOperation(async () => {
      try {
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        const settings = await deleteUserProductSearchPreference(scope, {
          expectedUserId: requestedUserId,
        })
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        applySavedSettings(settings)
        setProductSearchPreferences(settings.productSearchPreferences ?? [])
        void refreshSearchSuggestions()
        void refreshTasteProfile()
      } catch {
        if (
          activeUserIdRef.current !== requestedUserId ||
          productSearchPreferencesSessionRef.current !== settingsSession
        )
          return
        setProductSearchPreferencesError('Saved size was not removed. Please try again.')
      }
    })
  }

  const applyDescription = async (text: string) => {
    return saveSettings({
      filterIds: prefsOn,
      preferenceDescription: text,
    })
  }

  const acceptTasteSuggestion = (filterId: string) => {
    const requestedUserId = requireCurrentAccountUser()
    void acceptUserTasteSuggestion(filterId, { expectedUserId: requestedUserId }).then(
      (settings) => {
        if (activeUserIdRef.current !== requestedUserId) return
        applySavedSettings(settings)
        void refreshTasteProfile()
      },
    )
  }

  const rejectTasteSuggestion = (filterId: string) => {
    const requestedUserId = requireCurrentAccountUser()
    setTasteProfile((current) => ({
      ...current,
      suggestions: current.suggestions.filter((suggestion) => suggestion.filterId !== filterId),
    }))
    void rejectUserTasteSuggestion(filterId, { expectedUserId: requestedUserId })
      .then(() => {
        if (activeUserIdRef.current === requestedUserId) void refreshTasteProfile()
      })
      .catch(() => {
        if (activeUserIdRef.current === requestedUserId) void refreshTasteProfile()
      })
  }

  const disableTasteSignal = (signalId: string, disabled: boolean) => {
    const requestedUserId = requireCurrentAccountUser()
    setTasteProfile((current) => ({
      ...current,
      signals: current.signals.map((signal) =>
        signal.id === signalId ? { ...signal, status: disabled ? 'DISABLED' : 'ACTIVE' } : signal,
      ),
    }))
    void updateUserTasteSignal({ signalId, disabled, expectedUserId: requestedUserId })
      .then((updated) => {
        if (activeUserIdRef.current !== requestedUserId) return
        setTasteProfile((current) => ({
          ...current,
          signals: current.signals.map((signal) => (signal.id === updated.id ? updated : signal)),
        }))
      })
      .catch(() => {
        if (activeUserIdRef.current === requestedUserId) void refreshTasteProfile()
      })
  }

  const removeTasteSignal = (signalId: string) => {
    const requestedUserId = requireCurrentAccountUser()
    setTasteProfile((current) => ({
      ...current,
      signals: current.signals.filter((signal) => signal.id !== signalId),
    }))
    void removeUserTasteSignal(signalId, { expectedUserId: requestedUserId }).catch(() => {
      if (activeUserIdRef.current === requestedUserId) void refreshTasteProfile()
    })
  }

  const addInventoryItem = async (input: UserInventoryItemDraftInput, photo: File) => {
    const requestedUserId = requireCurrentAccountUser()
    const item = await createInventoryItemWithPhoto({
      userId: requestedUserId,
      item: input,
      photo,
    })
    if (activeUserIdRef.current !== requestedUserId) throw new Error('Account changed')
    setInventoryItems((current) => upsertInventorySnapshot(current, item))
    return item
  }

  const editInventoryItem = async (
    existing: UserInventoryItemProfile,
    item: UserInventoryItemUpdateInput,
    replacementPhoto?: File,
  ) => {
    const requestedUserId = requireCurrentAccountUser()
    const updated = await updateInventoryItemWithPhoto({
      userId: requestedUserId,
      existing,
      item,
      replacementPhoto,
    })
    if (activeUserIdRef.current !== requestedUserId) throw new Error('Account changed')
    setInventoryItems((current) => upsertInventorySnapshot(current, updated))
    return updated
  }

  const removeInventoryItem = async (item: UserInventoryItemProfile) => {
    const requestedUserId = requireCurrentAccountUser()
    await deleteInventoryItemWithPhoto({ userId: requestedUserId, item })
    if (activeUserIdRef.current !== requestedUserId) throw new Error('Account changed')
    setInventoryItems((current) => current.filter((candidate) => candidate.id !== item.id))
  }

  const downloadInventory = async () => {
    const requestedUserId = requireCurrentAccountUser()
    const exported = await exportUserInventory({ expectedUserId: requestedUserId })
    if (activeUserIdRef.current !== requestedUserId) return
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
    turn: DiscoverProductSearchTurnInput,
  ): Promise<DiscoverProductSearchTurnResult> => {
    const submittedMessage = turn.message.trim()
    if (!submittedMessage) {
      throw new Error('Tell Meant what you want to find.')
    }
    const merchantId = turn.merchantId ?? null
    const searchUserId = userId
    if (!searchUserId) {
      throw new Error('Sign in before starting a product search.')
    }
    const requestId = searchRequestSequenceRef.current + 1
    searchRequestSequenceRef.current = requestId
    searchRequestsRef.current.get(turn.conversationId)?.controller.abort()
    const controller = new AbortController()
    searchRequestsRef.current.set(turn.conversationId, { requestId, controller })
    const isCurrentRequest = () =>
      searchRequestsRef.current.get(turn.conversationId)?.requestId === requestId &&
      !controller.signal.aborted &&
      activeUserIdRef.current === searchUserId
    turn.onActivities?.([
      {
        agent: 'qualification',
        label: 'Understanding which filters matter for this search',
        state: 'active',
        updatedAt: Date.now(),
      },
    ])
    try {
      const qualification = await qualifyProductSearch({
        conversationId: turn.conversationId,
        qualificationId: turn.qualificationId,
        message: submittedMessage,
        merchantId,
        signal: controller.signal,
        expectedUserId: searchUserId,
      })
      if (!isCurrentRequest()) {
        throw new Error('Product search was cancelled.')
      }

      const effectiveQuery = qualification.effectiveQuery.trim() || submittedMessage
      void refreshProductSearchPreferences()
      if (qualification.status === 'NEEDS_INPUT') {
        turn.onActivities?.([
          {
            agent: 'qualification',
            label: 'Waiting for your search preferences',
            state: 'done',
            updatedAt: Date.now(),
          },
        ])
        return {
          status: 'NEEDS_INPUT',
          qualificationId: qualification.qualificationId,
          assistantMessage: qualification.assistantMessage,
          suggestedReplies: qualification.suggestedReplies,
          effectiveQuery,
        }
      }

      turn.onActivities?.([
        {
          agent: 'search',
          label: 'Grouping products and ranking merchant offers',
          state: 'active',
          updatedAt: Date.now(),
        },
      ])
      const result = await searchGroupedProducts({
        query: effectiveQuery,
        qualificationId: qualification.qualificationId,
        merchantId,
        offset: 0,
        limit: PRODUCT_SEARCH_PAGE_SIZE,
        signal: controller.signal,
        expectedUserId: searchUserId,
      })
      if (!isCurrentRequest()) {
        throw new Error('Product search was cancelled.')
      }
      const products = result.products.map(productFromCanonical).filter(isRenderableSearchProduct)
      setRemoteProducts((current) => appendProductSnapshots(current, products))
      const degradedCount = result.sourceStates.filter((source) => source.degraded).length
      const sourceNote =
        degradedCount > 0
          ? ` ${degradedCount} source${degradedCount === 1 ? ' is' : 's are'} limited; healthy offers are still shown.`
          : ''
      const resultMessage = result.cached
        ? `Showing ${products.length} cached grouped match${products.length === 1 ? '' : 'es'} for "${effectiveQuery}".${sourceNote}`
        : `Found ${products.length} grouped product${products.length === 1 ? '' : 's'} for "${effectiveQuery}".${sourceNote}`
      const qualificationMessage = qualification.assistantMessage.trim()
      turn.onActivities?.([
        {
          agent: 'search',
          label: 'Grouped products and ranked offers',
          state: 'done',
          updatedAt: Date.now(),
        },
      ])
      return {
        status: 'READY',
        qualificationId: qualification.qualificationId,
        assistantMessage: qualificationMessage
          ? `${qualificationMessage} ${resultMessage}`
          : resultMessage,
        suggestedReplies: qualification.suggestedReplies,
        effectiveQuery,
        products,
        productResultSetId: result.productResultSetId,
      }
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error && error.message.trim()
          ? error.message
          : 'Product search failed. Please try again.'
      turn.onActivities?.([
        {
          agent: 'search',
          label: 'Grouped product search failed',
          state: 'error',
          updatedAt: Date.now(),
        },
      ])
      throw error instanceof Error ? error : new Error(errorMessage)
    } finally {
      if (searchRequestsRef.current.get(turn.conversationId)?.requestId === requestId) {
        searchRequestsRef.current.delete(turn.conversationId)
      }
    }
  }

  const findSimilarProducts = useCallback(
    async (
      product: Product,
      sourceQuery: string,
      qualificationId: string | undefined,
      signal: AbortSignal,
    ): Promise<Product[]> => {
      const canonicalProductKey = product.canonicalProduct?.key ?? product.id
      const result = await searchSimilarGroupedProducts({
        canonicalProductKey,
        query: sourceQuery,
        qualificationId,
        signal,
      })
      if (signal.aborted) {
        return []
      }
      const seen = new Set<ProductId>([product.id, canonicalProductKey])
      const similarProducts = result.products
        .filter((candidate) => candidate.key !== canonicalProductKey)
        .map(productFromCanonical)
        .filter(isRenderableSearchProduct)
        .filter((candidate) => {
          if (seen.has(candidate.id)) {
            return false
          }
          seen.add(candidate.id)
          return true
        })

      setRemoteProducts((current) => appendProductSnapshots(current, similarProducts))
      return similarProducts
    },
    [],
  )

  const rehydrateSimilarProducts = useCallback(
    async (canonicalProductKeys: readonly string[], signal: AbortSignal) => {
      const result = await rehydrateCanonicalProducts({ canonicalProductKeys, signal })
      if (signal.aborted) {
        throw new DOMException('Similar product rehydration was aborted', 'AbortError')
      }
      const products = result.products.map(productFromCanonical).filter(isRenderableSearchProduct)
      setRemoteProducts((current) => appendProductSnapshots(current, products))
      return {
        products,
        unavailableCanonicalProductKeys: result.unavailableCanonicalProductKeys,
      }
    },
    [],
  )

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
    const checkoutCartIds = new Set(
      payload.items
        .map((item) => item.cartId?.trim())
        .filter((cartId): cartId is string => Boolean(cartId)),
    )
    const checkoutItems = new Set(payload.items.map(cartItemIdentity))
    updateStoredCart((current) =>
      current.map((item) =>
        (item.cartId?.trim() && checkoutCartIds.has(item.cartId.trim())) ||
        checkoutItems.has(cartItemIdentity(item))
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

  const startCheckoutNow = async (
    payload: CheckoutPayload,
    source: ActiveCheckoutSession['source'],
  ): Promise<string | null> => {
    const requestedUserId = requireCurrentAccountUser()
    const merchant = payload.merchant ?? payload.items[0]?.merchant ?? 'merchant'
    const merchantKey =
      payload.merchantKey ??
      (payload.items[0] ? cartMerchantKey(payload.items[0]) : normalizedMerchantName(merchant))
    const cartId = payload.items.find((item) => item.cartId)?.cartId
    if (!cartId) {
      const message = 'Checkout is not available until this merchant cart syncs.'
      setCheckoutError({
        merchant,
        merchantKey,
        message,
      })
      return message
    }
    const currentCheckout = activeCheckoutRef.current
    if (currentCheckout?.ownerId === requestedUserId && currentCheckout.cartId !== cartId) {
      const message = `Finish or close the active checkout for ${currentCheckout.merchant} before starting another.`
      setCheckoutError({ merchant, merchantKey, message })
      return message
    }
    if (checkoutOperationRef.current) {
      const message = 'Another checkout operation is already in progress.'
      setCheckoutError({ merchant, merchantKey, message })
      return message
    }
    const checkoutOperationToken = checkoutOperationSequenceRef.current + 1
    checkoutOperationSequenceRef.current = checkoutOperationToken
    checkoutOperationRef.current = {
      token: checkoutOperationToken,
      ownerId: requestedUserId,
    }
    setCheckoutMerchantKey(merchantKey)
    setCheckoutError(null)
    setCheckoutFlowBusy(true)
    setCheckoutFlowError(null)
    try {
      const checkoutProfile = await getCartCheckout({
        cartId,
        refresh: true,
        expectedUserId: requestedUserId,
      })
      if (
        activeUserIdRef.current !== requestedUserId ||
        checkoutOperationRef.current?.token !== checkoutOperationToken
      ) {
        return 'Account changed during checkout.'
      }
      updateCartWithCheckoutProfile(payload, checkoutProfile)
      updateActiveCheckoutState({
        ownerId: requestedUserId,
        cartId,
        threadId: payload.chatThreadId ?? null,
        merchant,
        source,
        items: payload.items,
        saved: payload.saved,
        savedNote: payload.savedNote,
        profile: checkoutProfile,
        completion: null,
      })
      return null
    } catch {
      const message = 'Could not start checkout. Try again.'
      if (
        activeUserIdRef.current === requestedUserId &&
        checkoutOperationRef.current?.token === checkoutOperationToken
      ) {
        setCheckoutError({
          merchant,
          merchantKey,
          message,
        })
      }
      return message
    } finally {
      if (
        activeUserIdRef.current === requestedUserId &&
        checkoutOperationRef.current?.token === checkoutOperationToken
      ) {
        checkoutOperationRef.current = null
        setCheckoutMerchantKey(null)
        setCheckoutFlowBusy(false)
      }
    }
  }

  const startCheckout = (
    payload: CheckoutPayload,
    source: ActiveCheckoutSession['source'],
  ): Promise<string | null> =>
    commerceOperationQueue.enqueue(() => startCheckoutNow(payload, source))

  const checkout = async (payload: CheckoutPayload) => {
    await startCheckout(payload, 'cart')
  }

  const checkoutInChat = async (payload: CheckoutPayload) => {
    if (payload.items.length === 0) {
      return
    }
    const errorMessage = await startCheckout(payload, 'chat')
    if (errorMessage) {
      throw new Error(errorMessage)
    }
  }

  const releaseChatCheckout = useCallback(
    (cartId: string) => {
      updateActiveCheckoutState((current) =>
        current?.source === 'chat' && current.cartId === cartId ? null : current,
      )
      setCheckoutFlowError(null)
    },
    [updateActiveCheckoutState],
  )

  const refreshActiveCheckoutNow = async (verifiedCheckout?: CheckoutProfile) => {
    const inventoryRefresh = loadInventory({ silent: true })
    const checkoutSession = activeCheckoutRef.current
    if (!checkoutSession) {
      await inventoryRefresh
      return
    }
    const requestedUserId = requireCurrentAccountUser()
    if (checkoutSession.ownerId !== requestedUserId) {
      await inventoryRefresh
      return
    }
    if (checkoutOperationRef.current) {
      await inventoryRefresh
      return
    }
    const checkoutOperationToken = checkoutOperationSequenceRef.current + 1
    checkoutOperationSequenceRef.current = checkoutOperationToken
    checkoutOperationRef.current = {
      token: checkoutOperationToken,
      ownerId: requestedUserId,
    }
    const checkoutSessionSequence = activeCheckoutSequenceRef.current
    setCheckoutFlowBusy(true)
    setCheckoutFlowError(null)
    try {
      const checkoutProfile =
        verifiedCheckout ??
        (await getCartCheckout({
          cartId: checkoutSession.cartId,
          refresh: true,
          expectedUserId: requestedUserId,
        }))
      if (
        activeUserIdRef.current !== requestedUserId ||
        checkoutOperationRef.current?.token !== checkoutOperationToken ||
        activeCheckoutSequenceRef.current !== checkoutSessionSequence ||
        activeCheckoutRef.current !== checkoutSession
      ) {
        return
      }
      updateCartWithCheckoutProfile(checkoutSession, checkoutProfile)
      updateActiveCheckoutState((current) =>
        current?.ownerId === requestedUserId && current.cartId === checkoutSession.cartId
          ? {
              ...current,
              profile: checkoutProfile,
              completion: null,
            }
          : current,
      )
    } catch {
      if (
        activeUserIdRef.current === requestedUserId &&
        checkoutOperationRef.current?.token === checkoutOperationToken
      ) {
        setCheckoutFlowError('Could not refresh checkout.')
      }
    } finally {
      await inventoryRefresh.catch(() => undefined)
      if (
        activeUserIdRef.current === requestedUserId &&
        checkoutOperationRef.current?.token === checkoutOperationToken
      ) {
        checkoutOperationRef.current = null
        setCheckoutFlowBusy(false)
      }
    }
  }

  const refreshActiveCheckout = (verifiedCheckout?: CheckoutProfile): Promise<void> =>
    commerceOperationQueue.enqueue(() => refreshActiveCheckoutNow(verifiedCheckout))

  const assistActiveCheckoutNow = async (
    message: string,
    history: readonly CheckoutAssistantMessage[],
    context?: CheckoutAssistantContext,
  ) => {
    const checkoutSession = activeCheckoutRef.current
    if (!checkoutSession || checkoutOperationRef.current) {
      return null
    }
    const requestedUserId = requireCurrentAccountUser()
    if (checkoutSession.ownerId !== requestedUserId) {
      return null
    }
    const checkoutOperationToken = checkoutOperationSequenceRef.current + 1
    checkoutOperationSequenceRef.current = checkoutOperationToken
    checkoutOperationRef.current = {
      token: checkoutOperationToken,
      ownerId: requestedUserId,
    }
    const checkoutSessionSequence = activeCheckoutSequenceRef.current
    setCheckoutFlowBusy(true)
    setCheckoutFlowError(null)
    try {
      const result: CheckoutAssistantResult = context?.savedCheckoutDetails
        ? {
            reply: 'I applied your saved contact and delivery details to this checkout.',
            checkoutUpdated: true,
            checkout: await updateCartCheckout({
              cartId: checkoutSession.cartId,
              buyer: context.savedCheckoutDetails.buyer,
              shippingAddress: context.savedCheckoutDetails.shippingAddress,
              expectedUserId: requestedUserId,
            }),
          }
        : await assistCartCheckout({
            cartId: checkoutSession.cartId,
            message,
            merchantDeliveryHint: context?.merchantDeliveryHint,
            history,
            expectedUserId: requestedUserId,
          })
      if (
        activeUserIdRef.current !== requestedUserId ||
        checkoutOperationRef.current?.token !== checkoutOperationToken ||
        activeCheckoutSequenceRef.current !== checkoutSessionSequence ||
        activeCheckoutRef.current !== checkoutSession
      ) {
        return null
      }
      updateCartWithCheckoutProfile(checkoutSession, result.checkout)
      updateActiveCheckoutState((current) =>
        current?.ownerId === requestedUserId && current.cartId === checkoutSession.cartId
          ? {
              ...current,
              profile: result.checkout,
              completion: null,
            }
          : current,
      )
      return result
    } catch {
      return null
    } finally {
      if (
        activeUserIdRef.current === requestedUserId &&
        checkoutOperationRef.current?.token === checkoutOperationToken
      ) {
        checkoutOperationRef.current = null
        setCheckoutFlowBusy(false)
      }
    }
  }

  const assistActiveCheckout = (
    message: string,
    history: readonly CheckoutAssistantMessage[],
    context?: CheckoutAssistantContext,
  ): Promise<CheckoutAssistantResult | null> =>
    commerceOperationQueue.enqueue(() => assistActiveCheckoutNow(message, history, context))

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
            key={userId}
            userId={requireCurrentAccountUser()}
            items={currentInventoryItems}
            loading={currentInventoryLoading}
            error={currentInventoryError}
            onRefresh={() => void loadInventory()}
            onAddItem={addInventoryItem}
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
            productSearchPreferences={currentProductSearchPreferences}
            productSearchPreferencesBusy={currentProductSearchPreferencesBusy}
            productSearchPreferencesError={currentProductSearchPreferencesError}
            onSaveProductSearchPreference={saveProductSearchPreference}
            onRemoveProductSearchPreference={removeProductSearchPreference}
            onRefreshProductSearchPreferences={refreshProductSearchPreferences}
            tasteProfile={currentTasteProfile}
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
            onRemove={removeFromCartGuarded}
            onQty={updateQtyGuarded}
            onAdd={addToCartGuarded}
            onApplyCode={applyCartCodeGuarded}
            onRemoveCode={removeCartCodeGuarded}
            onDeliveryAddress={updateDeliveryAddressGuarded}
            onDeliveryOption={updateDeliveryOptionGuarded}
            onCheckout={checkout}
            agentBusy={false}
            checkoutMerchantKey={checkoutMerchantKey}
            checkoutError={checkoutError}
          />
        )
      case 'orders':
        return (
          <OrdersView
            orders={currentOrders}
            products={allKnownProducts}
            loading={currentOrdersLoading}
            error={currentOrdersError}
            preferences={allPreferences}
            flashId={null}
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
            key={userId ?? 'anonymous'}
            user={user}
            userId={userId}
            providerAvatar={providerAvatar}
            merchants={currentMerchants}
            merchantIdentityLinks={currentMerchantIdentityLinks}
            merchantIdentityLinksLoading={currentMerchantIdentityLinksLoading}
            merchantIdentityLinksError={currentMerchantIdentityLinksError}
            onSave={(nextUser) => {
              if (activeUserIdRef.current === userId) {
                setUser(nextUser)
              }
            }}
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
        return isAgenticDiscoverEnabled() ? (
          <AgentDiscoverView
            key={userId ?? 'anonymous'}
            expectedUserId={userId ?? ''}
            profile={liveProfile}
            greeting={greeting}
            prompts={currentSearchSuggestions}
            merchants={currentMerchants}
            merchantsLoading={merchantsLoading}
            merchantsError={merchantsError}
            deliveryLocations={deliveryLocations}
            preferences={allPreferences}
            cart={cart}
            cartProducts={allKnownProducts}
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            shelf={shelf}
            shelfFlashMessageId={shelfFlashMessageId}
            productDetailChatRequest={currentProductDetailChatRequest}
            discoverFindRequest={currentDiscoverFindRequest}
            homeRequestId={discoverHomeRequestId}
            newsletter={user.newsletter}
            onOpen={(product, products, researchQuery) =>
              openProduct(product, products ?? feedProducts, researchQuery)
            }
            onToggleSave={toggleSave}
            onCompareProducts={compareChatProducts}
            onCheckout={checkoutInChat}
            activeCheckout={activeCheckout?.source === 'chat' ? activeCheckout : null}
            checkoutBusy={checkoutFlowBusy}
            checkoutError={checkoutFlowError}
            onCheckoutAssistant={assistActiveCheckout}
            onRefreshCheckout={refreshActiveCheckout}
            onReleaseCheckout={releaseChatCheckout}
            onOpenSaved={() => nav('saved')}
            onOpenOrders={() => nav('orders')}
            onOpenPrefs={() => nav('preferences')}
            onOpenCart={() => nav('cart')}
            onOpenShelf={() => setShelfOpen(true)}
            onNewsletterChange={updateNewsletter}
            onShelfAddMessage={addMessageToShelf}
            onShelfAddProduct={addProductToShelf}
            onAgentProductsSnapshot={registerAgentProducts}
            onReadAgentCart={getCurrentCart}
            onCaptureAgentCartRevision={captureAgentCartRevision}
            onAgentCartSnapshot={registerAgentCartSnapshot}
            onAgentRunSubmitted={registerAgentCartRun}
            onProductDetailChatRequestHandled={(requestId) => {
              setProductDetailChatRequest((current) => (current?.id === requestId ? null : current))
            }}
            onFlashMessage={flashShelfMessage}
          />
        ) : (
          <ChatDiscoverView
            key={userId ?? 'anonymous'}
            profile={liveProfile}
            storageScope={userId ?? 'anonymous'}
            greeting={greeting}
            products={feedProducts}
            hiddenByShip={hiddenByShip}
            deliveryLocations={deliveryLocations}
            prompts={currentSearchSuggestions}
            preferences={allPreferences}
            merchants={currentMerchants}
            merchantsLoading={merchantsLoading}
            merchantsError={merchantsError}
            savedProducts={savedListProducts}
            cart={cart}
            cartProducts={allKnownProducts}
            orders={currentOrders}
            shelf={shelf}
            shelfFlashMessageId={shelfFlashMessageId}
            discoverFindRequest={currentDiscoverFindRequest}
            homeRequestId={discoverHomeRequestId}
            productDetailChatRequest={currentProductDetailChatRequest}
            newsletter={user.newsletter}
            onSubmit={runProductSearch}
            onSearchSimilarProducts={findSimilarProducts}
            onRehydrateSimilarProducts={rehydrateSimilarProducts}
            onOpen={(product, products, researchQuery) =>
              openProduct(product, products ?? feedProducts, researchQuery)
            }
            savedSet={savedSet}
            savePendingSet={savePendingSet}
            onToggleSave={toggleSave}
            onAddProductToCart={addProductOfferToCartResolved}
            onAddSelectedOfferToCart={addSelectedOfferToCartGuarded}
            onFallbackAddToCart={(product, offer) => addToCartGuarded(product.id, offer.merchant)}
            onCompareProducts={compareChatProducts}
            onCartQty={updateQtyGuarded}
            onCartRemove={removeFromCartGuarded}
            onCheckout={checkoutInChat}
            activeCheckout={activeCheckout?.source === 'chat' ? activeCheckout : null}
            checkoutBusy={checkoutFlowBusy}
            checkoutError={checkoutFlowError}
            onCheckoutAssistant={assistActiveCheckout}
            onRefreshCheckout={refreshActiveCheckout}
            onReleaseCheckout={releaseChatCheckout}
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
        cartMutationBlocked={false}
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
        onRemoveFromCart={removeFromCartGuarded}
        onSignOut={handleSignOut}
      />
      <ProfileBar
        preferences={activePreferences}
        deliveryLocations={deliveryLocations}
        clothingFit={clothingFit}
        onEdit={() => nav('preferences')}
      />
      {content}
      {activeCheckout && (activeCheckout.source === 'cart' || view === 'cart') ? (
        <CartCheckoutDialog
          session={activeCheckout}
          busy={checkoutFlowBusy}
          error={checkoutFlowError}
          onClose={() => {
            updateActiveCheckoutState(null)
            setCheckoutFlowError(null)
          }}
          onRefresh={refreshActiveCheckout}
          onCheckoutAssistant={assistActiveCheckout}
        />
      ) : null}
      <ProductModal
        product={currentActiveProduct}
        userId={userId}
        deliveryLocations={deliveryLocations}
        preferences={allPreferences}
        saved={currentActiveProduct ? savedSet.has(currentActiveProduct.id) : false}
        savePending={currentActiveProduct ? savePendingSet.has(currentActiveProduct.id) : false}
        savedOfferRefreshPending={
          currentActiveProduct ? savedProductDetailLoadingId === currentActiveProduct.id : false
        }
        inCompare={currentActiveProduct ? compareSet.has(currentActiveProduct.id) : false}
        cartMutationBlocked={false}
        onClose={() => {
          setActiveProduct(null)
          setActiveProductResearchQuery(null)
        }}
        onToggleSave={toggleSave}
        onUpdateSavedChoice={updateSavedChoice}
        onCompare={handleProductCompare}
        onAddToCart={addProductOfferToCartResolved}
        onAddOfferKey={addSelectedOfferToCartGuarded}
        onRefreshProduct={refreshSavedProductForOpen}
        researchQuery={activeProductResearchQuery}
        onAskInChat={sendProductQuestionToDiscover}
        canPrev={canNavPrev}
        canNext={canNavNext}
        onPrev={() => navigateProduct(-1)}
        onNext={() => navigateProduct(1)}
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
    </div>
  )
}
