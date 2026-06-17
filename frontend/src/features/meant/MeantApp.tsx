import {
  type ChangeEvent,
  type Dispatch,
  type FormEvent,
  type KeyboardEvent,
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
  getCartCheckout,
  getCurrentUser,
  getMerchants,
  getSavedProducts,
  getUserSettings,
  removeSavedProduct,
  saveUserProduct,
  searchUserProducts,
  type SaveUserProductInput,
  type CartProfile,
  type MerchantProfile,
  type ShoppingFilterProfile,
  type UserSavedProductProfile,
  updateCart,
  type UserProductSearchProductProfile,
  updateProfile,
  updateUserSettings,
  type UserSettingsProfile,
} from '../../lib/apiClient'
import type {
  AuthMode,
  CartItem,
  CheckoutPayload,
  CorePreferenceId,
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
  IMPORT_ASK,
  availableOffers,
  bestOffer,
  canMerchantShip,
  cartGroups,
  cartLines,
  computeSmartAlerts,
  formatOrderDate,
  listJoin,
  money,
  prefLabel,
  productMerchantCount,
  productPriceFrom,
  productsForPreferences,
  productsForLocation,
  readStorage,
  resolveAsk,
  writeStorage,
} from './utils'

interface Message {
  role: 'you' | 'ai'
  text: string
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

function applySettingsPayload(
  settings: UserSettingsProfile,
  setAvailablePrefs: Dispatch<SetStateAction<Preference[]>>,
  setPrefsOn: Dispatch<SetStateAction<PreferenceId[]>>,
  setBudget: Dispatch<SetStateAction<number>>,
  setLocation: Dispatch<SetStateAction<UserLocation | null>>,
) {
  setAvailablePrefs(settings.availableFilters.map(preferenceFromFilter))
  setPrefsOn(settings.filters.map((filter) => filter.id))
  setBudget(settings.budget ?? 120)
  setLocation(settings.location)
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

function searchProductPrice(product: UserProductSearchProductProfile): number {
  return (
    parsePriceAmount(product.selectedVariantPriceAmount) ??
    parsePriceAmount(product.detailPriceMin) ??
    parsePriceAmount(product.priceMinAmount) ??
    0
  )
}

function searchProductCategory(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
): string {
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
  const brand = product.merchantName || product.merchantDomain
  const detail = stripHtml(product.detailDescription || product.descriptionHtml)
  return {
    id: product.productKey,
    productHash: product.productHash,
    name: product.title,
    brand,
    category: searchProductCategory(product, preferences),
    tone: toneForSearchProduct(product),
    imageUrl: product.imageUrl || product.detailImageUrl || product.selectedVariantImageUrl,
    productUrl: product.url,
    remote: true,
    match: product.matchScore,
    priceFrom: price,
    merchants: 1,
    satisfies: product.matchedFilterIds,
    misses: product.missedFilterIds,
    note: product.whyMeantForYou,
    pros: product.matchedFilterIds.length > 0
      ? product.matchedFilterIds.map((id) => `Matches ${prefLabel(preferences, id).toLowerCase()}`)
      : [detail || 'Ranked highly for your search'],
    cons: product.missedFilterIds.map((id) => `May miss ${prefLabel(preferences, id).toLowerCase()}`),
    review: {
      score: 0,
      count: 0,
      insight: detail || product.whyMeantForYou,
    },
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
  }
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
    provides: product.provides.length > 0 ? product.provides as Product['provides'] : undefined,
  }
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
    review: product.review,
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

function upsertProductSnapshot(products: Product[], product: Product): Product[] {
  const existingIndex = products.findIndex((candidate) => candidate.id === product.id)
  if (existingIndex < 0) {
    return [product, ...products]
  }
  return products.map((candidate, index) => index === existingIndex ? product : candidate)
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

function mergeCartSnapshot(
  cart: readonly CartItem[],
  merchantKey: string,
  snapshot: CartProfile,
): CartItem[] {
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
      qty: line?.quantity ?? item.qty,
      syncing: false,
      syncError: null,
    }
  })
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

function ProductSearchLoading() {
  return (
    <div
      className="mt-search-state mt-search-state-loading"
      role="status"
      aria-live="polite"
      aria-label="Searching products across stores"
    >
      <div className="mt-search-loader" aria-hidden="true">
        <span className="mt-search-loader-spark"><SparkMark size={14} /></span>
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
        <span className="mt-search-loader-dot" />
      </div>
      <span className="mt-search-loading-text">
        Searching products across stores
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

function CheckIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden>
      <path
        d="M3 7.3l2.6 2.6L11 4.2"
        stroke="currentColor"
        strokeWidth="1.8"
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

function AskThread({ messages }: Readonly<{ messages: readonly Message[] }>) {
  const endRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (endRef.current) {
      endRef.current.scrollTop = endRef.current.scrollHeight
    }
  }, [messages.length])

  if (messages.length === 0) {
    return null
  }

  return (
    <div className="mt-ask-thread" ref={endRef}>
      {messages.map((message, index) => (
        <div key={`${message.role}-${index}`} className={`mt-msg mt-msg-${message.role}`}>
          {message.role === 'ai' ? (
            <span className="mt-msg-av">
              <SparkMark size={12} />
            </span>
          ) : null}
          <div className="mt-msg-bubble">{message.text}</div>
        </div>
      ))}
    </div>
  )
}

function AskComposer({
  placeholder,
  suggestions,
  showChips,
  onAsk,
  autoFocus = false,
}: Readonly<{
  placeholder: string
  suggestions: readonly string[]
  showChips: boolean
  onAsk: (question: string) => void
  autoFocus?: boolean
}>) {
  const [value, setValue] = useState('')
  const inputRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (autoFocus) {
      inputRef.current?.focus()
    }
  }, [autoFocus])

  const send = (text?: string) => {
    const question = (text ?? value).trim()
    if (!question) {
      return
    }
    setValue('')
    onAsk(question)
  }

  return (
    <div className="mt-ask-composer">
      {showChips && suggestions.length > 0 ? (
        <div className="mt-ask-chips">
          {suggestions.map((suggestion) => (
            <button
              key={suggestion}
              className="mt-ask-chip"
              type="button"
              onClick={() => send(suggestion)}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
      <form
        className="mt-ask-bar"
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
        />
        <button type="submit" className="mt-ask-go" aria-label="Ask">
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
  suggestions,
  preferences,
}: Readonly<{
  contextLabel: string
  suggestions: readonly string[]
  preferences: readonly Preference[]
}>) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])

  const ask = (question: string) => {
    const answer = resolveAsk(question, null, preferences)
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: answer },
    ])
  }

  return (
    <div className={`mt-fab-wrap ${open ? 'open' : ''}`}>
      {open ? (
        <div className="mt-askpanel" role="dialog" aria-label="Ask Meant">
          <div className="mt-askpanel-head">
            <div className="mt-askpanel-title">
              <SparkMark size={15} /> Ask Meant
            </div>
            <button
              className="mt-askpanel-close"
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Close"
            >
              <CloseIcon size={14} />
            </button>
          </div>
          <div className="mt-mono mt-askpanel-ctx">{contextLabel}</div>
          {messages.length === 0 ? (
            <p className="mt-askpanel-hint">
              Ask anything. I already know your preferences.
            </p>
          ) : null}
          <AskThread messages={messages} />
          <AskComposer
            placeholder="Ask Meant..."
            suggestions={suggestions}
            showChips={messages.length === 0}
            onAsk={ask}
            autoFocus
          />
        </div>
      ) : null}
      <button className="mt-fab" type="button" onClick={() => setOpen((current) => !current)}>
        {open ? <CloseIcon size={18} /> : <><SparkMark size={16} color="#fff" /> <span>Ask Meant</span></>}
      </button>
    </div>
  )
}

function ProductCard({
  product,
  index,
  location,
  preferences,
  onOpen,
  savedSet,
  onToggleSave,
}: Readonly<{
  product: Product
  index: number
  location: UserLocation | null
  preferences: readonly Preference[]
} & ProductOpenProps & ProductSaveProps>) {
  const open = () => onOpen(product)
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
      style={{ transitionDelay: `${index * 45}ms` }}
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
          aria-label={savedSet.has(product.id) ? 'Remove from saved' : 'Save'}
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
        <div className="mt-card-foot">
          <span className="mt-card-price">
            <span className="mt-mono mt-card-from">from</span>{' '}
            {money(productPriceFrom(product, location))}
          </span>
          <span className="mt-mono mt-card-stores">
            {productMerchantCount(product, location)} stores
          </span>
        </div>
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
  onlyMeantOnMerchant,
  onMerchant,
  onToggleMeantOnMerchant,
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
  onlyMeantOnMerchant: boolean
  onMerchant: (merchant: MerchantProfile | null) => void
  onToggleMeantOnMerchant: () => void
}>) {
  const [value, setValue] = useState('')
  const hasSearchText = value.trim().length > 0

  const submit = (text?: string) => {
    if (loading) {
      return
    }
    const query = (text ?? value).trim()
    if (!query) {
      return
    }
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
        Ask for anything across every store. Meant already knows you prefer{' '}
        {profile.summary}
      </p>
      <form
        className={`mt-search${hasSearchText ? ' mt-search-writing' : ''}`}
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
        onlyMeantOnMerchant={onlyMeantOnMerchant}
        onMerchant={onMerchant}
        onToggleMeantOnMerchant={onToggleMeantOnMerchant}
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
  onlyMeantOnMerchant,
  onMerchant,
  onToggleMeantOnMerchant,
}: Readonly<{
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  loading: boolean
  error: string | null
  onlyMeantOnMerchant: boolean
  onMerchant: (merchant: MerchantProfile | null) => void
  onToggleMeantOnMerchant: () => void
}>) {
  const [open, setOpen] = useState(false)
  const [merchantSearch, setMerchantSearch] = useState('')
  const ref = useRef<HTMLDivElement | null>(null)
  const searchRef = useRef<HTMLInputElement | null>(null)
  const merchantSearchText = merchantSearch.trim().toLowerCase()
  const filteredMerchants = merchantSearchText
    ? merchants.filter((merchant) =>
        [
          merchant.name,
          merchant.domain,
          merchant.description,
          merchant.advertisedMcpEndpoint,
          merchant.profileMcpEndpoint,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
          .includes(merchantSearchText),
      )
    : merchants

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
      {selectedMerchant ? (
        <button
          className={`mt-scope-cta ${onlyMeantOnMerchant ? 'on' : ''}`}
          type="button"
          aria-pressed={onlyMeantOnMerchant}
          onClick={onToggleMeantOnMerchant}
        >
          {onlyMeantOnMerchant ? <CheckIcon /> : <SparkMark size={15} color="currentColor" />}
          {onlyMeantOnMerchant
            ? `Showing what's meant for you on ${selectedMerchant.name}`
            : `Find everything meant for me on ${selectedMerchant.name}`}
        </button>
      ) : null}
    </div>
  )
}

function FeedView({
  profile,
  greeting,
  products,
  hiddenByShip,
  location,
  reply,
  query,
  loading,
  error,
  preferences,
  merchants,
  selectedMerchant,
  merchantCounts,
  totalProductCount,
  merchantsLoading,
  merchantsError,
  onlyMeantOnMerchant,
  onSubmit,
  onClear,
  onMerchant,
  onToggleMeantOnMerchant,
  onOpen,
  savedSet,
  onToggleSave,
}: Readonly<{
  profile: typeof PROFILE
  greeting: string
  products: readonly Product[]
  hiddenByShip: number
  location: UserLocation | null
  reply: string | null
  query: string
  loading: boolean
  error: string | null
  preferences: readonly Preference[]
  merchants: readonly MerchantProfile[]
  selectedMerchant: MerchantProfile | null
  merchantCounts: ReadonlyMap<string, number>
  totalProductCount: number
  merchantsLoading: boolean
  merchantsError: string | null
  onlyMeantOnMerchant: boolean
  onSubmit: (query: string) => void
  onClear: () => void
  onMerchant: (merchant: MerchantProfile | null) => void
  onToggleMeantOnMerchant: () => void
} & ProductOpenProps & ProductSaveProps>) {
  const merchantName = selectedMerchant?.name
  const title = query
    ? merchantName ? `Your matches on ${merchantName}` : 'Your matches'
    : merchantName ? `Meant for you on ${merchantName}` : 'Meant for you'

  return (
    <main className="mt-feed">
      <ChatHero
        profile={profile}
        greeting={greeting}
        prompts={PROMPTS}
        onSubmit={onSubmit}
        loading={loading}
        merchants={merchants}
        selectedMerchant={selectedMerchant}
        merchantCounts={merchantCounts}
        totalProductCount={totalProductCount}
        merchantsLoading={merchantsLoading}
        merchantsError={merchantsError}
        onlyMeantOnMerchant={onlyMeantOnMerchant}
        onMerchant={onMerchant}
        onToggleMeantOnMerchant={onToggleMeantOnMerchant}
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
      <div className="mt-feed-head">
        <h2 className="mt-feed-title">{title}</h2>
        <span className="mt-mono mt-feed-count">
          {loading
            ? 'Searching stores'
            : merchantName
              ? `${products.length} on ${merchantName}${onlyMeantOnMerchant ? ' · meant for you' : ''}`
              : `${products.length} shown · sorted by match`}
        </span>
      </div>
      {location ? (
        <div className="mt-ship-strip">
          <span aria-hidden>⌖</span>
          <span>
            Shipping to <strong>{location.city}, {location.country}</strong>
          </span>
          {hiddenByShip > 0 ? (
            <span className="mt-ship-strip-hidden mt-mono">
              {hiddenByShip} hidden · cannot reach you
            </span>
          ) : null}
        </div>
      ) : null}
      {loading ? (
        <ProductSearchLoading />
      ) : products.length > 0 ? (
        <div className="mt-grid">
          {products.map((product, index) => (
            <ProductCard
              key={product.id}
              product={product}
              index={index}
              location={location}
              preferences={preferences}
              onOpen={onOpen}
              savedSet={savedSet}
              onToggleSave={onToggleSave}
            />
          ))}
        </div>
      ) : merchantName ? (
        <div className="mt-empty">
          <div className="mt-empty-mark"><MerchantIcon /></div>
          <h3 className="mt-empty-title">
            {onlyMeantOnMerchant ? `Nothing meant for you on ${merchantName}` : `Nothing here on ${merchantName}`}
          </h3>
          <p className="mt-empty-sub">
            {onlyMeantOnMerchant
              ? `${merchantName} has no currently loaded products that cleanly match your profile.`
              : `Meant has no currently loaded products from ${merchantName}. Try a search or return to all merchants.`}
          </p>
          <div className="mt-empty-actions">
            {onlyMeantOnMerchant ? (
              <button className="mt-empty-btn" type="button" onClick={onToggleMeantOnMerchant}>
                Show all on {merchantName}
              </button>
            ) : null}
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
      ) : null}
      {!query ? (
        <p className="mt-mono mt-feed-foot">
          Meant hid products that clash with your profile. Nothing here works
          against what matters to you.
        </p>
      ) : null}
    </main>
  )
}

function ProductModal({
  product,
  location,
  preferences,
  saved,
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
  location: UserLocation | null
  preferences: readonly Preference[]
  saved: boolean
  inCompare: boolean
  onClose: () => void
  onToggleSave: (product: Product) => void
  onCompare: (id: ProductId) => void
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

  const offers = availableOffers(product, location)
  const visibleOffers = offers.length > 0 ? offers : product.offers
  const ask = (question: string) => {
    setMessages((current) => [
      ...current,
      { role: 'you', text: question },
      { role: 'ai', text: resolveAsk(question, product, preferences) },
    ])
  }
  const selectedOffer = bestOffer(product, location)
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
        <div className="mt-modal-body">
          <div className="mt-modal-left">
            <div className="mt-modal-media">
              <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
              <div className="mt-modal-ring">
                <MatchRing value={product.match} size={56} stroke={4} />
              </div>
            </div>
            <div className="mt-mono mt-card-brand">{product.brand} · {product.category}</div>
            <h2 className="mt-modal-name">{product.name}</h2>
            <div className="mt-modal-price">
              <span className="mt-mono mt-card-from">from</span>{' '}
              {money(productPriceFrom(product, location))}
              <span className="mt-mono mt-modal-stores">
                · {productMerchantCount(product, location)} stores
              </span>
            </div>
            <div className="mt-modal-actions">
              <button
                className={`mt-act mt-act-icon ${saved ? 'on' : ''}`}
                type="button"
                onClick={() => onToggleSave(product)}
                aria-label={saved ? 'Saved' : 'Save'}
              >
                <HeartIcon filled={saved} />
              </button>
              <button
                className={`mt-act mt-act-ghost ${inCompare ? 'on' : ''}`}
                type="button"
                onClick={() => onCompare(product.id)}
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
                <div className="mt-reviews-score">
                  <span className="mt-stars">
                    {'★'.repeat(Math.round(product.review.score))}
                  </span>
                  <span className="mt-mono">
                    {product.review.score.toFixed(1)} ·{' '}
                    {product.review.count.toLocaleString()}
                  </span>
                </div>
              </div>
              <p className="mt-reviews-insight">{product.review.insight}</p>
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
  location,
  onEdit,
}: Readonly<{
  preferences: readonly Preference[]
  location: UserLocation | null
  onEdit: () => void
}>) {
  return (
    <div className="mt-profile">
      <span className="mt-mono mt-profile-key">Your profile</span>
      <button
        className={`mt-loc-chip ${location ? '' : 'empty'}`}
        type="button"
        onClick={onEdit}
      >
        <span aria-hidden>⌖</span>
        {location
          ? `${location.city}, ${location.country}`
          : 'Set delivery location'}
      </button>
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
  onViewFull,
  onClose,
  onRemove,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
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
            Add products and Meant checks compatibility and hunts for codes.
          </div>
        </div>
      </div>
    )
  }

  const alerts = computeSmartAlerts(lines, products)
  const warnCount = alerts.filter((alert) => alert.kind === 'warn').length
  const groups = cartGroups(lines, false)
  const itemTotal = groups.reduce((sum, group) => sum + group.subtotal, 0)
  const discountTotal = groups.reduce((sum, group) => sum + group.itemDiscount, 0)
  const deliveryTotal = groups.reduce((sum, group) => sum + group.delivery, 0)
  const codeCount = groups.filter((group) => group.found).length
  const grandTotal = itemTotal - discountTotal + deliveryTotal
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
          {codeCount > 0 ? `${codeCount} codes · -${money(discountTotal)}` : 'No codes found'}
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
            You are saving {money(discountTotal)} with codes Meant found.
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
  cartPeek,
  accountMenu,
  onNav,
  onToggleTheme,
  onToggleCart,
  onToggleAccount,
  onRemoveFromCart,
  onSignOut,
}: Readonly<{
  view: View
  theme: Theme
  user: UserAccount
  savedCount: number
  cart: readonly CartItem[]
  products: readonly Product[]
  cartPeek: boolean
  accountMenu: boolean
  onNav: (view: View) => void
  onToggleTheme: () => void
  onToggleCart: () => void
  onToggleAccount: () => void
  onRemoveFromCart: (id: ProductId, merchant: string) => void
  onSignOut: () => void
}>) {
  // Count only items that resolve to a known product, so the badge can never
  // disagree with what the cart actually shows (e.g. a stale persisted cart).
  const cartCount = cartLines(cart, products).reduce((sum, line) => sum + line.qty, 0)

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
        ].map(([key, label]) => (
          <button
            key={key}
            className={`mt-nav-item ${view === key ? 'on' : ''}`}
            type="button"
            onClick={() => onNav(key as View)}
          >
            {label}
            {key === 'saved' && savedCount > 0 ? (
              <span className="mt-mono mt-nav-count">{savedCount}</span>
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
            {cartCount > 0 ? <span className="mt-cart-badge mt-mono">{cartCount}</span> : null}
          </button>
          {cartPeek ? (
            <CartPopover
              cart={cart}
              products={products}
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
              onClose={onToggleAccount}
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
  const items: ReadonlyArray<{ key: View; label: string }> = [
    { key: 'account', label: 'Account settings' },
    { key: 'orders', label: 'Order history' },
    { key: 'preferences', label: 'Your preferences' },
    { key: 'saved', label: 'Saved items' },
    { key: 'cart', label: 'Your cart' },
  ]

  return (
    <div className="mt-acctmenu" role="menu">
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
      <button className="mt-acctmenu-item mt-acctmenu-signout" type="button" onClick={onSignOut}>
        Sign out
      </button>
    </div>
  )
}

function SavedView({
  products,
  location,
  preferences,
  savedSet,
  onOpen,
  onToggleSave,
}: Readonly<{
  products: readonly Product[]
  location: UserLocation | null
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
              location={location}
              preferences={preferences}
              onOpen={onOpen}
              savedSet={savedSet}
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

function CompareView({
  products,
  compareIds,
  preferences,
  onPick,
  onRemove,
  onAdd,
}: Readonly<{
  products: readonly Product[]
  compareIds: readonly ProductId[]
  preferences: readonly Preference[]
  onPick: (index: number, id: ProductId) => void
  onRemove: (index: number) => void
  onAdd: (id: ProductId) => void
}>) {
  const items = compareIds.map((id) => products.find((product) => product.id === id)).filter((product): product is Product => Boolean(product))
  const selectedIds = items.map((product) => product.id)
  const showAdd = items.length < 4
  const enough = items.length >= 2
  const gridStyle = {
    gridTemplateColumns: `190px repeat(${items.length + (showAdd ? 1 : 0)}, minmax(0, 1fr))`,
  }
  const bestMatch = enough ? Math.max(...items.map((product) => product.match)) : null
  const bestPrice = enough ? Math.min(...items.map((product) => product.priceFrom)) : null
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
        sub="Compare up to four products at once. Swap, add, or remove any of them and Meant lines them up against everything you care about."
      />
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
              products={products}
              selectedIds={selectedIds}
              canRemove={items.length > 1}
              onPick={onPick}
              onRemove={onRemove}
            />
          ))}
          {showAdd ? (
            <CompareAddSlot products={products} selectedIds={selectedIds} onAdd={onAdd} />
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
                value: money(product.priceFrom),
                win: bestPrice !== null && product.priceFrom === bestPrice,
              }))}
              addSpacer={showAdd}
            />
            <div className="mt-cmp-grid mt-cmp-row" style={gridStyle}>
              <div className="mt-cmp-rowlabel">Reviews</div>
              {items.map((product) => (
                <div key={product.id} className="mt-cmp-cell">
                  <span className="mt-stars">
                    {'★'.repeat(Math.round(product.review.score))}
                  </span>
                  <span className="mt-mono mt-cmp-sub">
                    {product.review.score.toFixed(1)} · {product.review.count.toLocaleString()}
                  </span>
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
              {items.map((product) => (
                <div key={product.id} className="mt-cmp-cell">
                  <span className="mt-cmp-store">{product.offers[0].merchant}</span>
                  <span className="mt-mono mt-cmp-sub">
                    {money(product.offers[0].price)} · {product.offers[0].delivery}
                  </span>
                </div>
              ))}
              {showAdd ? <div className="mt-cmp-cell mt-cmp-addspacer" /> : null}
            </div>
          </>
        ) : (
          <div className="mt-cmp-hint-row">
            Add at least two products to see them compared field by field.
          </div>
        )}
      </div>
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
  products,
  selectedIds,
  canRemove,
  onPick,
  onRemove,
}: Readonly<{
  index: number
  product: Product
  products: readonly Product[]
  selectedIds: readonly ProductId[]
  canRemove: boolean
  onPick: (index: number, id: ProductId) => void
  onRemove: (index: number) => void
}>) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-cmp-col">
      <div className="mt-cmp-media">
        <ProductArtwork product={product} label={`${product.category.toLowerCase()} shot`} />
        <div className="mt-cmp-ring">
          <MatchRing value={product.match} size={48} stroke={3} />
        </div>
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
      <div className="mt-cmp-picker">
        <button className="mt-cmp-swap" type="button" onClick={() => setOpen((value) => !value)}>
          Swap <span className={`mt-caret ${open ? 'up' : ''}`}>v</span>
        </button>
        {open ? (
          <CompareMenu
            products={products}
            selectedIds={selectedIds}
            currentId={product.id}
            onChoose={(id) => {
              onPick(index, id)
              setOpen(false)
            }}
          />
        ) : null}
      </div>
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
  onAdd: (id: ProductId) => void
}>) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-cmp-col mt-cmp-col-empty">
      <div className="mt-cmp-picker">
        <button className="mt-cmp-choose" type="button" onClick={() => setOpen((value) => !value)}>
          <span className="mt-cmp-plus">+</span> Add a product
        </button>
        {open ? (
          <CompareMenu
            products={products}
            selectedIds={selectedIds}
            currentId={null}
            onChoose={(id) => {
              onAdd(id)
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
  currentId,
  onChoose,
}: Readonly<{
  products: readonly Product[]
  selectedIds: readonly ProductId[]
  currentId: ProductId | null
  onChoose: (id: ProductId) => void
}>) {
  const options = products.filter(
    (product) => product.id === currentId || !selectedIds.includes(product.id),
  )
  return (
    <div className="mt-cmp-menu">
      {options.map((product) => (
        <button
          key={product.id}
          className={`mt-cmp-opt ${product.id === currentId ? 'on' : ''}`}
          type="button"
          onClick={() => onChoose(product.id)}
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

function Toggle({
  on,
  onClick,
}: Readonly<{
  on: boolean
  onClick: () => void
}>) {
  return (
    <button
      className={`mt-toggle ${on ? 'on' : ''}`}
      role="switch"
      aria-checked={on}
      type="button"
      onClick={onClick}
    >
      <span className="mt-toggle-knob" />
    </button>
  )
}

function PreferencesView({
  allPrefs,
  prefsOn,
  onToggle,
  onApplyDescription,
  budget,
  onBudget,
  location,
  onLocation,
  profile,
  onDone,
}: Readonly<{
  allPrefs: readonly Preference[]
  prefsOn: ReadonlySet<PreferenceId>
  onToggle: (id: PreferenceId) => void
  onApplyDescription: (text: string) => Promise<boolean>
  budget: number
  onBudget: (value: number) => void
  location: UserLocation | null
  onLocation: (location: UserLocation) => void
  profile: typeof PROFILE
  onDone: () => void
}>) {
  const [desc, setDesc] = useState('')
  const [importText, setImportText] = useState('')
  const [imported, setImported] = useState(false)
  const [copied, setCopied] = useState(false)
  const [parsing, setParsing] = useState(false)
  const enabled = allPrefs.filter((preference) => prefsOn.has(preference.id))

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
              Set where you are and Meant only shows products from merchants that can deliver to you.
            </p>
          </div>
          {location ? <span className="mt-mono mt-sec-count">Active</span> : null}
        </div>
        <LocationSection location={location} onSet={onLocation} />
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
            <h3 className="mt-sectitle">Your filters</h3>
            <p className="mt-secsub">Applied everywhere automatically.</p>
          </div>
          <span className="mt-mono mt-sec-count">{enabled.length} active</span>
        </div>
        <div className="mt-prefs-list">
          {allPrefs.map((preference) => {
            const active = prefsOn.has(preference.id)
            return (
              <div className={`mt-pref-row ${active ? '' : 'off'}`} key={preference.id}>
                <div className="mt-pref-text">
                  <div className="mt-pref-name">
                    {preference.label}
                  </div>
                  <div className="mt-pref-desc">{preference.desc}</div>
                </div>
                <div className="mt-pref-controls">
                  <Toggle on={active} onClick={() => onToggle(preference.id)} />
                </div>
              </div>
            )
          })}
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-budget">
          <div className="mt-budget-head">
            <div>
              <div className="mt-pref-name">Comfortable spend</div>
              <div className="mt-pref-desc">
                Meant looks for the best quality it can find under this.
              </div>
            </div>
            <div className="mt-budget-val">${budget}</div>
          </div>
          <input
            className="mt-range"
            type="range"
            min="20"
            max="300"
            step="5"
            value={budget}
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
  location,
  onSet,
}: Readonly<{
  location: UserLocation | null
  onSet: (location: UserLocation) => void
}>) {
  const [editing, setEditing] = useState(!location)
  const [code, setCode] = useState(location?.code ?? '')
  const [city, setCity] = useState(location?.city ?? '')
  const country = LOCATIONS.find((option) => option.code === code)

  const save = () => {
    if (!country || !city) {
      return
    }
    onSet({ code: country.code, country: country.country, city })
    setEditing(false)
  }

  if (location && !editing) {
    return (
      <div className="mt-loc-set">
        <span className="mt-loc-pin">⌖</span>
        <div className="mt-loc-text">
          <div className="mt-loc-city">{location.city}, {location.country}</div>
          <div className="mt-loc-note">
            Meant is hiding anything that cannot ship here.
          </div>
        </div>
        <button className="mt-loc-change" type="button" onClick={() => setEditing(true)}>
          Change
        </button>
      </div>
    )
  }

  return (
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
          Save location
        </button>
        {location ? (
          <button className="mt-act mt-act-ghost" type="button" onClick={() => setEditing(false)}>
            Cancel
          </button>
        ) : null}
        <span className="mt-describe-hint">
          Meant only shows products from merchants that can deliver to you.
        </span>
      </div>
    </div>
  )
}

function CartView({
  cart,
  products,
  location,
  onRemove,
  onQty,
  onAdd,
  onCheckout,
  checkoutMerchant,
  checkoutError,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  location: UserLocation | null
  onRemove: (id: ProductId, merchant: string) => void
  onQty: (id: ProductId, merchant: string, qty: number) => void
  onAdd: (id: ProductId, merchant: string) => void
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  checkoutMerchant: string | null
  checkoutError: { merchant: string; message: string } | null
}>) {
  const [scanning, setScanning] = useState(true)

  useEffect(() => {
    setScanning(true)
    const timeout = window.setTimeout(() => setScanning(false), 1700)
    return () => window.clearTimeout(timeout)
  }, [cart.length])

  const lines = cartLines(cart, products)
  const alerts = computeSmartAlerts(lines, products)
  const shipWarnings = location
    ? lines.filter((line) => !canMerchantShip(line.merchant, location))
    : []
  const groups = cartGroups(lines, scanning)
  const itemsTotal = groups.reduce((sum, group) => sum + group.subtotal, 0)
  const discountTotal = groups.reduce((sum, group) => sum + group.itemDiscount, 0)
  const deliveryTotal = groups.reduce((sum, group) => sum + group.delivery, 0)
  const grandTotal = itemsTotal - discountTotal + deliveryTotal
  const codeCount = groups.filter((group) => group.found).length
  const warnCount = alerts.filter((alert) => alert.kind === 'warn').length

  if (lines.length === 0) {
    return (
      <main className="mt-feed mt-view">
        <ViewHead eyebrow="Smart cart" title="Your cart" />
        <EmptyState
          title="Your cart is empty"
          sub="Add products and Meant checks compatibility and hunts for codes."
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
                    <div className="mt-alert-title">Does not ship to {location?.city}</div>
                    <div className="mt-alert-text">
                      {line.merchant} cannot deliver {line.product.name} to {location?.city}, {location?.country}.
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

          {groups.map((group) => {
            const groupTotal = group.subtotal - group.itemDiscount + group.delivery
            const groupSyncing = group.items.some((item) => item.syncing)
            const groupLineError = group.items.find((item) => item.syncError)?.syncError
            const groupCheckoutError = checkoutError?.merchant === group.merchant
              ? checkoutError.message
              : null
            const groupCheckoutable = group.items.every((item) =>
              Boolean(item.cartId && item.productVariantId && !item.syncError),
            )
            const checkoutBusy = checkoutMerchant === group.merchant
            const checkoutBlocked = scanning || groupSyncing || !groupCheckoutable || Boolean(checkoutMerchant)
            const checkoutSub = groupLineError ?? groupCheckoutError ??
              (groupSyncing
                ? 'Syncing merchant cart'
                : !groupCheckoutable
                  ? 'Checkout needs a merchant cart-ready item'
                  : group.found
                    ? `${group.found.code.code} found · ${group.delivery === 0 ? 'free delivery' : `${money(group.delivery)} delivery`}`
                    : group.delivery === 0
                      ? 'Free delivery'
                      : `${money(group.delivery)} delivery`)

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
                    {group.delivery === 0 ? 'Free delivery' : `${money(group.delivery)} delivery`}
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
                <div className="mt-mgroup-foot">
                  {scanning ? (
                    <div className="mt-scan">
                      <span className="mt-scan-pulse" /> Scanning {group.merchant} for codes...
                    </div>
                  ) : group.found ? (
                    <div className="mt-found">
                      <span className="mt-code">
                        <span className="mt-code-val mt-mono">{group.found.code.code}</span>
                        <span className="mt-code-act mt-mono">copy</span>
                      </span>
                      <span className="mt-found-label">{group.found.code.label}</span>
                      <span className="mt-found-save mt-mono">-{money(group.found.save)}</span>
                    </div>
                  ) : (
                    <div className="mt-found mt-found-none mt-mono">
                      No codes found for {group.merchant}
                    </div>
                  )}
                  <div className="mt-mgroup-sub">
                    Subtotal <span>{money(group.subtotal)}</span>
                  </div>
                </div>
                <div className="mt-mgroup-pay">
                  <div>
                    <div className="mt-mgroup-pay-total">
                      <span className="mt-mono">Merchant total</span>
                      <strong>{money(groupTotal)}</strong>
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
                        saved: group.itemDiscount,
                        savedNote: group.found ? `${group.found.code.code} found` : '',
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
              {scanning ? (
                <>
                  <span className="mt-scan-pulse" /> Meant is searching the web for discount codes...
                </>
              ) : (
                <>
                  <SparkMark size={14} /> Found {codeCount} codes across {groups.length} merchants
                </>
              )}
            </div>
            <div className="mt-sum-row">
              <span>Items ({lines.reduce((sum, line) => sum + line.qty, 0)})</span>
              <span>{money(itemsTotal)}</span>
            </div>
            <div className={`mt-sum-row ${discountTotal > 0 ? 'save' : 'muted'}`}>
              <span>Discounts found</span>
              <span>{discountTotal > 0 ? `-${money(discountTotal)}` : scanning ? '...' : money(0)}</span>
            </div>
            <div className="mt-sum-row">
              <span>Delivery</span>
              <span>{deliveryTotal === 0 ? 'Free' : money(deliveryTotal)}</span>
            </div>
            <div className="mt-sum-total">
              <span>Total</span>
              <span>{money(grandTotal)}</span>
            </div>
            {!scanning && discountTotal > 0 ? (
              <div className="mt-sum-note mt-mono">
                You are saving {money(discountTotal)} with codes Meant found.
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

function AccountView({
  user,
  onSave,
  onSignOut,
  onEditPrefs,
  onDone,
}: Readonly<{
  user: UserAccount
  onSave: (user: UserAccount) => void
  onSignOut: () => void
  onEditPrefs: () => void
  onDone: () => void
}>) {
  const [name, setName] = useState(user.name)
  const [avatar, setAvatar] = useState<string | null>(user.avatar)
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)
  const preview: UserAccount = { name, email: user.email, avatar }
  const dirty = name !== user.name || avatar !== user.avatar

  const onFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setAvatar(reader.result)
      }
    }
    reader.readAsDataURL(file)
    event.target.value = ''
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
              {avatar ? 'Change photo' : 'Upload photo'}
            </button>
            {avatar ? (
              <button className="mt-acct-removebtn" type="button" onClick={() => setAvatar(null)}>
                Remove
              </button>
            ) : null}
            <div className="mt-acct-photo-hint">JPG or PNG. A square image works best.</div>
            <input ref={fileRef} type="file" accept="image/*" onChange={onFile} hidden />
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
              const nextName = name.trim() || user.name
              const { firstName, surname } = splitName(nextName)
              setSaving(true)
              setError(null)
              updateProfile({ firstName, surname })
                .then((profile) => {
                  const savedName =
                    [profile.firstName, profile.surname].filter(Boolean).join(' ').trim() || nextName
                  onSave({ name: savedName, email: user.email, avatar })
                  setSaved(true)
                  window.setTimeout(() => setSaved(false), 1800)
                })
                .catch(() => setError('Could not save your changes. Please try again.'))
                .finally(() => setSaving(false))
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
            {signup ? 'Set up your profile once and Meant applies it across every store.' : 'Pick up right where you left off.'}
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
          One account, every store. Meant learns what matters to you and quietly filters out the rest.
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
  const [searchError, setSearchError] = useState<string | null>(null)
  const [merchants, setMerchants] = useState<MerchantProfile[]>([])
  const [merchantsLoading, setMerchantsLoading] = useState(false)
  const [merchantsError, setMerchantsError] = useState<string | null>(null)
  const [selectedMerchantId, setSelectedMerchantId] = useStoredState<string | null>('meant.merchant', null)
  const [onlyMeantOnMerchant, setOnlyMeantOnMerchant] = useState(false)
  const [activeProduct, setActiveProduct] = useState<Product | null>(null)
  const [navProducts, setNavProducts] = useState<readonly Product[]>([])
  const [savedIds, setSavedIds] = useState<ProductId[]>([])
  const [savedProducts, setSavedProducts] = useState<Product[]>([])
  const [compareIds, setCompareIds] = useStoredState<ProductId[]>('meant.compare', [...DEFAULT_COMPARE])
  const [availablePrefs, setAvailablePrefs] = useState<Preference[]>([...PREFERENCES])
  const [prefsOn, setPrefsOn] = useStoredState<PreferenceId[]>('meant.prefsOn', [...DEFAULT_PREFERENCE_IDS])
  const [budget, setBudget] = useStoredState('meant.budget', 120)
  const [location, setLocation] = useStoredState<UserLocation | null>('meant.location', null)
  const [cart, setCart] = useStoredState<CartItem[]>('meant.cart', [...DEFAULT_CART])
  const [checkoutMerchant, setCheckoutMerchant] = useState<string | null>(null)
  const [checkoutError, setCheckoutError] = useState<{ merchant: string; message: string } | null>(null)
  const [orders] = useStoredState<Order[]>('meant.orders', [...DEFAULT_ORDERS])
  const [lastPlaced, setLastPlaced] = useState<string | null>(null)
  const [user, setUser] = useStoredState<UserAccount>('meant.user', DEFAULT_USER)
  const [cartPeek, setCartPeek] = useState(false)
  const [accountMenu, setAccountMenu] = useState(false)
  const searchRequestRef = useRef(0)
  const cartRef = useRef<readonly CartItem[]>(cart)
  const greeting = useBrowserGreeting()

  const allPreferences = availablePrefs
  const activePreferences = allPreferences.filter((preference) => prefsOn.includes(preference.id))
  const savedSet = useMemo(() => new Set(savedIds), [savedIds])
  const compareSet = useMemo(() => new Set(compareIds), [compareIds])
  const allKnownProducts = useMemo(() => {
    const seen = new Set<ProductId>()
    return [...searchResults, ...remoteProducts, ...savedProducts, ...PRODUCTS].filter((product) => {
      if (seen.has(product.id)) {
        return false
      }
      seen.add(product.id)
      return true
    })
  }, [remoteProducts, savedProducts, searchResults])
  const savedListProducts = useMemo(
    () => savedIds
      .map((id) => allKnownProducts.find((product) => product.id === id))
      .filter((product): product is Product => Boolean(product)),
    [allKnownProducts, savedIds],
  )
  const shippableProducts = useMemo(() => productsForLocation(PRODUCTS, location), [location])
  const visibleProducts = productsForPreferences(shippableProducts, activePreferences)

  const liveProfile = useMemo(
    () => ({
      ...PROFILE,
      name: firstNameFromName(user.name),
    }),
    [user.name],
  )

  const searchActive = Boolean(query || searchLoading || searchError)
  const baseFeed = searchActive ? searchResults : PRODUCTS
  const localizedFeedProducts = productsForLocation(baseFeed, location)
  const unscopedFeedProducts = searchActive
    ? localizedFeedProducts
    : productsForPreferences(localizedFeedProducts, activePreferences)
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
  const feedProducts = selectedMerchant && onlyMeantOnMerchant
    ? merchantScopedFeedProducts.filter((product) => product.misses.length === 0)
    : merchantScopedFeedProducts
  const hiddenByShip = baseFeed.length - localizedFeedProducts.length

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
      setOnlyMeantOnMerchant(false)
    }
  }, [merchants.length, selectedMerchant, selectedMerchantId, setSelectedMerchantId])

  // Once authenticated, hydrate the profile from the backend (which creates the users row on first
  // call). Falls back to the JWT email if the backend is unreachable so the shell still renders.
  // Keyed on the user identity rather than the whole session object so periodic token refreshes
  // (which replace `session` hourly) don't trigger a redundant re-fetch.
  const userId = session?.user?.id
  const userEmail = session?.user?.email
  useEffect(() => {
    if (!userId) {
      return
    }
    let active = true
    getCurrentUser()
      .then((profile) => {
        if (!active) return
        const fullName = [profile.firstName, profile.surname].filter(Boolean).join(' ').trim()
        setUser((current) => ({
          ...current,
          name: fullName || current.name,
          email: profile.email || current.email,
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
        applySettingsPayload(settings, setAvailablePrefs, setPrefsOn, setBudget, setLocation)
      })
      .catch(() => {
        if (!active) return
        setAvailablePrefs([...PREFERENCES])
      })
    getSavedProducts()
      .then((products) => {
        if (!active) return
        const snapshots = products.map(savedProductFromProfile)
        setSavedProducts(snapshots)
        setSavedIds(snapshots.map((product) => product.id))
      })
      .catch(() => {
        if (!active) return
        setSavedProducts([])
        setSavedIds([])
      })
    return () => {
      active = false
    }
  }, [userId, userEmail, setUser, setPrefsOn, setBudget, setLocation])

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
    setSelectedMerchantId(null)
    setOnlyMeantOnMerchant(false)
    setSavedIds([])
    setSavedProducts([])
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

  const toggleSave = (product: Product) => {
    const wasSaved = savedSet.has(product.id)
    if (wasSaved) {
      setSavedIds((current) => current.filter((candidate) => candidate !== product.id))
      setSavedProducts((current) => current.filter((candidate) => candidate.id !== product.id))
      void removeSavedProduct(product.id).catch(() => {
        setSavedProducts((current) => upsertProductSnapshot(current, product))
        setSavedIds((current) => current.includes(product.id) ? current : [product.id, ...current])
      })
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
  }

  const addToCompare = (id: ProductId) => {
    setCompareIds((current) => {
      if (current.includes(id)) {
        return current
      }
      return current.length < 4 ? [...current, id] : [...current.slice(1), id]
    })
    nav('compare')
  }

  const updateStoredCart = (updater: (current: CartItem[]) => CartItem[]) => {
    setCart((current) => {
      const next = updater(current)
      cartRef.current = next
      return next
    })
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

  const applySavedSettings = (settings: UserSettingsProfile) => {
    applySettingsPayload(settings, setAvailablePrefs, setPrefsOn, setBudget, setLocation)
  }

  const saveSettings = async (input: {
    budget?: number
    location?: UserLocation | null
    filterIds?: readonly PreferenceId[]
    preferenceDescription?: string
  }) => {
    try {
      applySavedSettings(await updateUserSettings(input))
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

  const runProductSearch = async (nextQuery: string) => {
    const submittedQuery = nextQuery.trim()
    if (!submittedQuery) {
      return
    }
    const requestId = searchRequestRef.current + 1
    searchRequestRef.current = requestId
    setQuery(submittedQuery)
    setReply(null)
    setSearchError(null)
    setSearchLoading(true)
    setSearchResults([])
    try {
      const result = await searchUserProducts({ query: submittedQuery })
      if (searchRequestRef.current !== requestId) {
        return
      }
      const products = result.products.map((product) =>
        productFromSearchResult(product, allPreferences),
      )
      setSearchResults(products)
      setRemoteProducts((current) => {
        const byId = new Map(current.map((product) => [product.id, product]))
        products.forEach((product) => byId.set(product.id, product))
        return Array.from(byId.values())
      })
      setReply(
        result.cached
          ? `Showing ${products.length} cached match${products.length === 1 ? '' : 'es'} for "${submittedQuery}".`
          : `Found ${products.length} match${products.length === 1 ? '' : 'es'} for "${submittedQuery}".`,
      )
    } catch {
      if (searchRequestRef.current !== requestId) {
        return
      }
      setSearchResults([])
      setSearchError('Product search failed. Please try again.')
    } finally {
      if (searchRequestRef.current === requestId) {
        setSearchLoading(false)
      }
    }
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
            location={location}
            preferences={allPreferences}
            savedSet={savedSet}
            onOpen={(product) =>
              openProduct(product, savedListProducts)
            }
            onToggleSave={toggleSave}
          />
        )
      case 'compare':
        return (
          <CompareView
            products={visibleProducts}
            compareIds={compareIds}
            preferences={allPreferences}
            onPick={(index, id) =>
              setCompareIds((current) => current.map((existing, currentIndex) => (currentIndex === index ? id : existing)))
            }
            onRemove={(index) => setCompareIds((current) => current.filter((_, currentIndex) => currentIndex !== index))}
            onAdd={(id) => setCompareIds((current) => (current.includes(id) ? current : [...current, id].slice(0, 4)))}
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
            location={location}
            onLocation={(nextLocation) => {
              setLocation(nextLocation)
              void saveSettings({ location: nextLocation })
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
            location={location}
            onRemove={removeFromCart}
            onQty={updateQty}
            onAdd={addToCart}
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
            hiddenByShip={hiddenByShip}
            location={location}
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
            merchantsError={merchantsError}
            onlyMeantOnMerchant={Boolean(selectedMerchant && onlyMeantOnMerchant)}
            onSubmit={(nextQuery) => {
              void runProductSearch(nextQuery)
            }}
            onClear={() => {
              setReply(null)
              setQuery('')
              setSearchResults([])
              setSearchError(null)
              setSearchLoading(false)
            }}
            onMerchant={(merchant) => {
              setSelectedMerchantId(merchant?.id ?? null)
              if (!merchant) {
                setOnlyMeantOnMerchant(false)
              }
            }}
            onToggleMeantOnMerchant={() => setOnlyMeantOnMerchant((current) => !current)}
            onOpen={(product) => openProduct(product, feedProducts)}
            savedSet={savedSet}
            onToggleSave={toggleSave}
          />
        )
    }
  })()

  if (!authed) {
    return content
  }

  const askContext = askContexts[view]
  const cartCount = cartLines(cart, allKnownProducts).reduce((sum, line) => sum + line.qty, 0)

  return (
    <div className="mt-app">
      <TopBar
        view={view}
        theme={theme}
        user={user}
        savedCount={savedIds.length}
        cart={cart}
        products={allKnownProducts}
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
        onRemoveFromCart={removeFromCart}
        onSignOut={handleSignOut}
      />
      <ProfileBar
        preferences={activePreferences}
        location={location}
        onEdit={() => nav('preferences')}
      />
      {content}
      <ProductModal
        product={activeProduct}
        location={location}
        preferences={allPreferences}
        saved={activeProduct ? savedSet.has(activeProduct.id) : false}
        inCompare={activeProduct ? compareSet.has(activeProduct.id) : false}
        onClose={() => setActiveProduct(null)}
        onToggleSave={toggleSave}
        onCompare={addToCompare}
        onAddToCart={addProductOfferToCart}
        canPrev={canNavPrev}
        canNext={canNavNext}
        onPrev={() => navigateProduct(-1)}
        onNext={() => navigateProduct(1)}
      />
      {!activeProduct ? (
        <FloatingAsk
          contextLabel={askContext.label}
          suggestions={askContext.suggestions}
          preferences={allPreferences}
        />
      ) : null}
      <span className="mt-cart-count-debug" aria-hidden>{cartCount}</span>
    </div>
  )
}
