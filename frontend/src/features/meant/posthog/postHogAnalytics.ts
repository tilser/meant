import type { CaptureResult, CapturedNetworkRequest, PostHogConfig, Properties } from 'posthog-js'

import {
  MEANT_ANALYTICS_EVENT_NAME,
  isMeantAnalyticsEventDetail,
  type MeantAnalyticsEvent,
  type MeantAnalyticsEventDetail,
} from '../analytics'

const DEFAULT_REPLAY_SAMPLE_RATE = 0.05
const MAXIMUM_REPLAY_SAMPLE_RATE = 0.1
const MAXIMUM_INITIAL_EVENTS = 100
const MAXIMUM_PENDING_OPERATIONS = 200
const POSTHOG_USER_ID_PROPERTY = '$user_id'

const attributionPropertyNames = [
  'utm_source',
  'utm_medium',
  'utm_campaign',
  'utm_content',
] as const

const authSheetReasons = new Set([
  'generic',
  'save-product',
  'preferences',
  'new-conversation',
  'cart',
  'checkout',
])
const authMethods = new Set(['google', 'apple', 'email', 'existing-account'])
const identityLinkResults = new Set(['linked', 'signed-in'])
const pendingActionTypes = new Set([
  'SAVE_PRODUCT',
  'REMEMBER_PREFERENCES',
  'START_NEW_CONVERSATION',
  'OPEN_SAVED',
  'OPEN_HISTORY',
  'OPEN_INVENTORY',
  'OPEN_ORDERS',
  'OPEN_CART',
  'OPEN_PREFERENCES',
  'OPEN_ACCOUNT',
  'ADD_TO_CART',
  'ENABLE_ALERT',
])

const urlPropertyNames = [
  '$current_url',
  '$initial_current_url',
  '$session_entry_url',
  '$referrer',
  '$initial_referrer',
] as const

const deniedPropertyNames = [
  'authorization',
  'access_token',
  'refresh_token',
  'auth_token',
  'email',
  '$email',
  'user_email',
  'full_name',
  'brief',
  'prompt',
  'message',
  'chat_content',
  'checkout_details',
  'payment_details',
  'request_body',
  'response_body',
  'request_headers',
  'response_headers',
  'ph_keyword',
  '$initial_ph_keyword',
  'referrer',
  '$referrer',
  '$initial_referrer',
  'referring_domain',
  '$referring_domain',
  '$initial_referring_domain',
]

const automaticPersonAttributionProperty =
  /^(?:utm_.+|.*clid|gbraid|wbraid|gad_source|mc_cid|igshid|epik|qclid|sccid|referrer|referring_domain)$/i
const safeAttributionIdentifier = /^[A-Za-z0-9][A-Za-z0-9._~+-]{0,99}$/
const secretLikeAttribution =
  /^(?:(?:phc|sk|pk|sbp|ghp|github_pat|xox[baprs])[_-]|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|[A-Za-z0-9_-]{32,})/i

const replayBlockedSelector = [
  '.mt-auth',
  '.mt-auth-callback',
  '.mt-auth-sheet-backdrop',
  '.mt-ct-tabs',
  '.mt-ct-history-wrap',
  '.mt-ct-share-scrim',
  '.mt-ct-thread',
  '.mt-ct-dock',
  '.mt-ask-thread',
  '.mt-shelf',
  '.mt-inventory-view',
  '.mt-avatar-anchor',
  '.mt-acctmenu',
  '.mt-acct-card',
  '.mt-acct-danger',
  '.mt-account-notice',
  '.mt-profile',
  '.mt-prefs-view',
  '.mt-orders',
  '.mt-cart',
  '.mt-cart-pop',
  '.mt-checkout-shell',
  '.mt-embedded-checkout',
].join(', ')

export interface PostHogEnvironment {
  PROD?: boolean
  MODE?: string
  VITE_POSTHOG_ENABLED?: string
  VITE_POSTHOG_PROJECT_TOKEN?: string
  VITE_POSTHOG_HOST?: string
  VITE_POSTHOG_SESSION_REPLAY_ENABLED?: string
  VITE_POSTHOG_SESSION_REPLAY_SAMPLE_RATE?: string
}

export interface PostHogRuntimeConfig {
  projectToken: string
  apiHost: string
  sessionReplayEnabled: boolean
  sessionReplaySampleRate: number
}

export interface PostHogClient {
  init(projectToken: string, config: Partial<PostHogConfig>): PostHogClient
  capture(name: string, properties?: Record<string, unknown>): unknown
  identify(distinctId: string): void
  reset(): void
  get_property(name: string): unknown
  startSessionRecording?(override?: { sampling?: boolean } | true): void
  stopSessionRecording?(): void
}

export type MeantAnalyticsIdentity =
  | { kind: 'unresolved' }
  | { kind: 'anonymous' }
  | { kind: 'signed-out' }
  | { kind: 'permanent'; userId: string }

export interface SupabaseIdentitySession {
  user: {
    id?: string
    is_anonymous?: boolean
  }
}

interface AnalyticsEventTarget {
  addEventListener(type: string, listener: EventListener): void
  removeEventListener(type: string, listener: EventListener): void
}

interface PostHogAnalyticsDependencies {
  eventTarget: AnalyticsEventTarget
  loadClient: () => Promise<PostHogClient>
  random?: () => number
  warn?: (message: string, cause: unknown) => void
}

export interface PostHogAnalyticsIntegration {
  start(): void
  stop(): void
  ready(): Promise<void>
  syncIdentity(identity: MeantAnalyticsIdentity): void
  resetIdentity(): void
}

type PendingOperation =
  | { kind: 'event'; detail: MeantAnalyticsEventDetail }
  | { kind: 'identity'; identity: MeantAnalyticsIdentity }
  | { kind: 'reset' }

function normalizedString(value: unknown, maximumLength: number): string | null {
  if (typeof value !== 'string') return null
  const normalized = [...value]
    .filter((character) => {
      const code = character.charCodeAt(0)
      return code >= 32 && code !== 127
    })
    .join('')
    .trim()
  return normalized ? normalized.slice(0, maximumLength) : null
}

function safeReplaySampleRate(value: string | undefined): number {
  if (value === undefined || value.trim() === '') return DEFAULT_REPLAY_SAMPLE_RATE
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return DEFAULT_REPLAY_SAMPLE_RATE
  return Math.min(parsed, MAXIMUM_REPLAY_SAMPLE_RATE)
}

function normalizedApiHost(value: string): string | null {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
      return null
    }
    return url.toString().replace(/\/$/, '')
  } catch {
    return null
  }
}

function isLocalHostname(hostname: string): boolean {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, '')
  if (!normalized) return true
  if (
    normalized === 'localhost' ||
    normalized.endsWith('.localhost') ||
    normalized.endsWith('.local') ||
    normalized.endsWith('.lan') ||
    normalized.endsWith('.test') ||
    normalized.endsWith('.invalid') ||
    normalized.endsWith('.example') ||
    normalized === '0.0.0.0' ||
    normalized === 'host.docker.internal' ||
    normalized === '127.0.0.1' ||
    normalized === '::1' ||
    normalized === '::' ||
    (normalized.includes(':') &&
      (normalized.startsWith('fc') ||
        normalized.startsWith('fd') ||
        normalized.startsWith('fe80:')))
  ) {
    return true
  }
  if (normalized.startsWith('::ffff:')) return isLocalHostname(normalized.slice(7))

  const ipv4 = normalized.split('.').map(Number)
  if (ipv4.length !== 4 || ipv4.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false
  }
  return (
    ipv4[0] === 0 ||
    ipv4[0] === 10 ||
    ipv4[0] === 127 ||
    (ipv4[0] === 169 && ipv4[1] === 254) ||
    (ipv4[0] === 172 && ipv4[1] >= 16 && ipv4[1] <= 31) ||
    (ipv4[0] === 192 && ipv4[1] === 168)
  )
}

export function resolvePostHogRuntimeConfig(
  environment: PostHogEnvironment,
  location: Pick<Location, 'hostname' | 'pathname'>,
): PostHogRuntimeConfig | null {
  if (
    environment.PROD !== true ||
    environment.MODE === 'test' ||
    environment.VITE_POSTHOG_ENABLED !== 'true' ||
    isLocalHostname(location.hostname)
  ) {
    return null
  }

  const projectToken = environment.VITE_POSTHOG_PROJECT_TOKEN?.trim()
  const apiHost = normalizedApiHost(environment.VITE_POSTHOG_HOST?.trim() ?? '')
  if (!projectToken || !apiHost) return null

  return {
    projectToken,
    apiHost,
    sessionReplayEnabled:
      environment.VITE_POSTHOG_SESSION_REPLAY_ENABLED === 'true' && location.pathname === '/',
    sessionReplaySampleRate: safeReplaySampleRate(
      environment.VITE_POSTHOG_SESSION_REPLAY_SAMPLE_RATE,
    ),
  }
}

export function browserPostHogRuntimeConfig(): PostHogRuntimeConfig | null {
  if (typeof window === 'undefined') return null
  return resolvePostHogRuntimeConfig(import.meta.env, window.location)
}

function sanitizedUrl(value: unknown): string | null {
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    const base = typeof window === 'undefined' ? 'https://invalid.local' : window.location.origin
    const url = new URL(value, base)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    url.username = ''
    url.password = ''
    url.search = ''
    url.hash = ''
    return url.toString()
  } catch {
    return null
  }
}

function sanitizedCaptureProperties(source: Properties, personProperties = false): Properties {
  const properties = { ...source }
  for (const name of urlPropertyNames) {
    if (!(name in properties)) continue
    const url = sanitizedUrl(properties[name])
    if (url) properties[name] = url
    else delete properties[name]
  }
  for (const name of Object.keys(properties)) {
    if (
      deniedPropertyNames.includes(name) ||
      name.startsWith('$session_entry_') ||
      (personProperties &&
        automaticPersonAttributionProperty.test(name.replace(/^\$/i, '').replace(/^initial_/i, '')))
    ) {
      delete properties[name]
    }
  }
  return properties
}

export function sanitizePostHogCapture(capture: CaptureResult | null): CaptureResult | null {
  if (!capture) return null
  return {
    ...capture,
    properties: sanitizedCaptureProperties(capture.properties),
    $set: capture.$set ? sanitizedCaptureProperties(capture.$set, true) : undefined,
    $set_once: capture.$set_once ? sanitizedCaptureProperties(capture.$set_once, true) : undefined,
  }
}

function sanitizeRecordedNetworkRequest(request: CapturedNetworkRequest): CapturedNetworkRequest {
  const safeUrl = sanitizedUrl(request.name)
  const origin = safeUrl ? new URL(safeUrl).origin : ''
  return {
    ...request,
    name: origin ? `${origin}/` : '',
    requestHeaders: undefined,
    requestBody: undefined,
    responseHeaders: undefined,
    responseBody: undefined,
  }
}

export function postHogBrowserOptions(config: PostHogRuntimeConfig): Partial<PostHogConfig> {
  return {
    api_host: config.apiHost,
    autocapture: false,
    capture_pageview: false,
    capture_pageleave: false,
    rageclick: false,
    capture_dead_clicks: false,
    capture_heatmaps: false,
    capture_exceptions: false,
    capture_performance: false,
    disable_surveys: true,
    disable_conversations: true,
    disable_product_tours: true,
    disable_web_experiments: true,
    opt_in_site_apps: false,
    // Replay needs the safe JSON remote-config endpoint; feature-flag POSTs stay disabled below.
    advanced_disable_flags: !config.sessionReplayEnabled,
    advanced_disable_feature_flags: true,
    person_profiles: 'identified_only',
    save_campaign_params: false,
    save_referrer: false,
    disable_capture_url_hashes: true,
    // Recording is started only after Supabase identity reconciliation. The recorder is bundled
    // separately, so no other project-controlled PostHog extensions can be loaded at runtime.
    disable_session_recording: true,
    disable_external_dependency_loading: true,
    enable_recording_console_log: false,
    mask_personal_data_properties: true,
    custom_personal_data_properties: [
      'brief',
      'prompt',
      'code',
      'token',
      'access_token',
      'refresh_token',
      'authorization',
    ],
    mask_all_text: true,
    mask_all_element_attributes: true,
    respect_dnt: true,
    property_denylist: deniedPropertyNames,
    get_current_url: (defaultUrl) => sanitizedUrl(defaultUrl) ?? 'https://invalid.local/',
    before_send: sanitizePostHogCapture,
    session_recording: {
      sampleRate: config.sessionReplayEnabled ? config.sessionReplaySampleRate : 0,
      maskAllInputs: true,
      maskTextSelector: '*',
      blockSelector: replayBlockedSelector,
      recordHeaders: false,
      recordBody: false,
      streamNetworkBody: false,
      recordCrossOriginIframes: false,
      collectFonts: false,
      captureCanvas: { recordCanvas: false },
      maskCapturedNetworkRequestFn: sanitizeRecordedNetworkRequest,
    },
  }
}

function includeBoolean(
  source: Record<string, unknown>,
  target: Record<string, string | number | boolean>,
  name: string,
) {
  if (typeof source[name] === 'boolean') target[name] = source[name]
}

function includeEnum(
  source: Record<string, unknown>,
  target: Record<string, string | number | boolean>,
  name: string,
  values: ReadonlySet<string>,
) {
  const value = source[name]
  if (typeof value === 'string' && values.has(value)) target[name] = value
}

function safeAttributionValue(value: unknown): string | null {
  const normalized = normalizedString(value, 101)
  return normalized &&
    normalized.length <= 100 &&
    safeAttributionIdentifier.test(normalized) &&
    !secretLikeAttribution.test(normalized)
    ? normalized
    : null
}

export function safeMeantEventProperties(
  name: MeantAnalyticsEvent,
  properties: Record<string, unknown>,
): Record<string, string | number | boolean> {
  const safe: Record<string, string | number | boolean> = {}
  for (const propertyName of attributionPropertyNames) {
    const value = safeAttributionValue(properties[propertyName])
    if (value) safe[propertyName] = value
  }

  switch (name) {
    case 'anonymous_session_created':
    case 'campaign_brief_loaded':
    case 'brief_submitted':
    case 'agent_results_viewed':
    case 'product_opened':
    case 'new_conversation_gate_viewed':
      includeBoolean(properties, safe, 'anonymous')
      break
    case 'agent_refinement_submitted': {
      includeBoolean(properties, safe, 'anonymous')
      const ordinal = properties.refinement_ordinal
      if (
        typeof ordinal === 'number' &&
        Number.isInteger(ordinal) &&
        ordinal >= 0 &&
        ordinal <= 10_000
      ) {
        safe.refinement_ordinal = ordinal
      }
      break
    }
    case 'protected_action_attempted':
      includeBoolean(properties, safe, 'anonymous')
      includeEnum(properties, safe, 'reason', authSheetReasons)
      includeEnum(properties, safe, 'pending_action_type', pendingActionTypes)
      break
    case 'auth_sheet_viewed':
    case 'auth_sheet_dismissed':
      includeEnum(properties, safe, 'reason', authSheetReasons)
      break
    case 'auth_method_selected':
      includeEnum(properties, safe, 'auth_method', authMethods)
      break
    case 'anonymous_account_converted':
    case 'existing_account_signed_in':
      includeEnum(properties, safe, 'identity_link_result', identityLinkResults)
      break
    case 'pending_action_completed':
    case 'pending_action_failed':
    case 'activated_account':
      includeEnum(properties, safe, 'pending_action_type', pendingActionTypes)
      break
    case 'anonymous_session_failed':
    case 'guest_conversation_imported':
    case 'merchant_outbound_clicked':
      break
  }
  return safe
}

export function identityForSupabaseSession(
  session: SupabaseIdentitySession | null,
): MeantAnalyticsIdentity {
  if (!session) return { kind: 'signed-out' }
  if (session.user.is_anonymous === true) return { kind: 'anonymous' }
  const userId = session.user.id?.trim()
  return userId ? { kind: 'permanent', userId } : { kind: 'unresolved' }
}

function postHogUserId(client: PostHogClient): string | null {
  return normalizedString(client.get_property(POSTHOG_USER_ID_PROPERTY), 200)
}

export function createPostHogAnalyticsIntegration(
  config: PostHogRuntimeConfig | null,
  dependencies: PostHogAnalyticsDependencies,
): PostHogAnalyticsIntegration {
  let client: PostHogClient | null = null
  let loading: Promise<void> | null = null
  let listenerAttached = false
  let identifiedUserId: string | null = null
  let initialIdentityResolved = false
  let replayStarted = false
  const replaySampled =
    config?.sessionReplayEnabled === true &&
    (dependencies.random?.() ?? Math.random()) < config.sessionReplaySampleRate
  const initialEvents: MeantAnalyticsEventDetail[] = []
  const pendingOperations: PendingOperation[] = []

  const applyIdentity = (identity: MeantAnalyticsIdentity) => {
    if (!client || identity.kind === 'unresolved') return
    const currentUserId = identifiedUserId ?? postHogUserId(client)
    if (identity.kind === 'permanent') {
      if (currentUserId && currentUserId !== identity.userId) {
        client.reset()
        identifiedUserId = null
      }
      if (currentUserId !== identity.userId) client.identify(identity.userId)
      identifiedUserId = identity.userId
      return
    }
    if (currentUserId) client.reset()
    identifiedUserId = null
  }

  const applyOperation = (operation: PendingOperation) => {
    if (!client) return
    if (operation.kind === 'event') {
      client.capture(
        operation.detail.name,
        safeMeantEventProperties(operation.detail.name, operation.detail.properties),
      )
    } else if (operation.kind === 'identity') {
      applyIdentity(operation.identity)
    } else {
      client.reset()
      identifiedUserId = null
    }
  }

  const enqueueOrApply = (operation: PendingOperation) => {
    if (client) {
      applyOperation(operation)
      return
    }
    pendingOperations.push(operation)
    const pendingEventCount = pendingOperations.reduce(
      (count, pending) => count + (pending.kind === 'event' ? 1 : 0),
      0,
    )
    if (pendingEventCount > MAXIMUM_PENDING_OPERATIONS) {
      const oldestEventIndex = pendingOperations.findIndex((pending) => pending.kind === 'event')
      if (oldestEventIndex >= 0) pendingOperations.splice(oldestEventIndex, 1)
    }
  }

  const startReplay = () => {
    if (!replaySampled || replayStarted || !listenerAttached || !client?.startSessionRecording) {
      return
    }
    client.startSessionRecording({ sampling: true })
    replayStarted = true
  }

  const activateClient = (loadedClient: PostHogClient) => {
    if (client) return
    client = loadedClient

    // The first operation is always the resolved Supabase identity (or a real logout reset).
    // Apply it from PostHog's loaded callback, before the SDK requests remote config.
    const initialOperation = pendingOperations.shift()
    if (initialOperation) applyOperation(initialOperation)

    while (pendingOperations.length > 0) {
      const operation = pendingOperations.shift()
      if (operation) applyOperation(operation)
    }
    startReplay()
  }

  const ensureClient = () => {
    if (!config || !initialIdentityResolved) return Promise.resolve()
    if (loading) return loading
    loading = dependencies
      .loadClient()
      .then((sdk) => {
        const initializedClient = sdk.init(config.projectToken, {
          ...postHogBrowserOptions(config),
          loaded: (loadedClient) => {
            activateClient(loadedClient as unknown as PostHogClient)
          },
        })
        // Test doubles and older compatible clients may not invoke `loaded` synchronously.
        activateClient(initializedClient)
      })
      .catch((cause) => {
        initialEvents.length = 0
        pendingOperations.length = 0
        dependencies.warn?.('PostHog analytics could not be initialized.', cause)
      })
    return loading
  }

  const onMeantAnalyticsEvent: EventListener = (event) => {
    const detail = (event as CustomEvent<unknown>).detail
    if (!isMeantAnalyticsEventDetail(detail)) return
    if (!initialIdentityResolved) {
      initialEvents.push(detail)
      if (initialEvents.length > MAXIMUM_INITIAL_EVENTS) initialEvents.shift()
      return
    }
    enqueueOrApply({ kind: 'event', detail })
  }

  const resolveInitialIdentity = (operation: PendingOperation) => {
    initialIdentityResolved = true
    enqueueOrApply(operation)
    startReplay()
    while (initialEvents.length > 0) {
      const detail = initialEvents.shift()
      if (detail) enqueueOrApply({ kind: 'event', detail })
    }
  }

  return {
    start() {
      if (!config || listenerAttached) return
      listenerAttached = true
      dependencies.eventTarget.addEventListener(MEANT_ANALYTICS_EVENT_NAME, onMeantAnalyticsEvent)
    },
    stop() {
      if (!listenerAttached) return
      listenerAttached = false
      dependencies.eventTarget.removeEventListener(
        MEANT_ANALYTICS_EVENT_NAME,
        onMeantAnalyticsEvent,
      )
      initialIdentityResolved = false
      initialEvents.length = 0
      if (replayStarted) client?.stopSessionRecording?.()
      replayStarted = false
    },
    ready() {
      return loading ?? Promise.resolve()
    },
    syncIdentity(identity) {
      if (!config) return
      if (identity.kind === 'unresolved') {
        return
      }
      if (!initialIdentityResolved) {
        resolveInitialIdentity({ kind: 'identity', identity })
        void ensureClient()
        return
      }
      enqueueOrApply({ kind: 'identity', identity })
      void ensureClient()
    },
    resetIdentity() {
      if (!config) return
      if (!initialIdentityResolved) {
        resolveInitialIdentity({ kind: 'reset' })
        void ensureClient()
        return
      }
      enqueueOrApply({ kind: 'reset' })
      void ensureClient()
    },
  }
}

let browserIntegration: PostHogAnalyticsIntegration | null = null

async function loadPostHogClient(config: PostHogRuntimeConfig): Promise<PostHogClient> {
  if (config.sessionReplayEnabled) {
    await import('posthog-js/dist/posthog-recorder')
  }
  const { default: posthog } = await import('posthog-js/dist/module.no-external')
  return posthog as unknown as PostHogClient
}

export function startBrowserPostHogAnalytics(): PostHogAnalyticsIntegration | null {
  const config = browserPostHogRuntimeConfig()
  if (!config || typeof window === 'undefined') return null
  browserIntegration ??= createPostHogAnalyticsIntegration(config, {
    eventTarget: window,
    loadClient: () => loadPostHogClient(config),
    warn: (message, cause) => console.warn(message, cause),
  })
  browserIntegration.start()
  return browserIntegration
}
