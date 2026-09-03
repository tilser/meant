import { afterEach, describe, expect, test } from 'bun:test'
import type { CaptureResult, CapturedNetworkRequest, PostHogConfig } from 'posthog-js'

import type { MeantAnalyticsEventDetail } from '../analytics'
import {
  browserPostHogRuntimeConfig,
  createPostHogAnalyticsIntegration,
  identityForSupabaseSession,
  postHogBrowserOptions,
  resolvePostHogRuntimeConfig,
  safeMeantEventProperties,
  sanitizePostHogCapture,
  type PostHogClient,
  type PostHogRuntimeConfig,
} from './postHogAnalytics'

const runtimeConfig: PostHogRuntimeConfig = {
  projectToken: 'phc_public_browser_token',
  apiHost: 'https://eu.i.posthog.com',
  sessionReplayEnabled: false,
  sessionReplaySampleRate: 0.05,
}

class CountingEventTarget {
  readonly listeners = new Set<EventListener>()
  additions = 0

  addEventListener(_type: string, listener: EventListener) {
    this.additions += 1
    this.listeners.add(listener)
  }

  removeEventListener(_type: string, listener: EventListener) {
    this.listeners.delete(listener)
  }

  emit(detail: MeantAnalyticsEventDetail) {
    const event = new CustomEvent('meant:analytics', { detail })
    for (const listener of this.listeners) listener(event)
  }
}

class MockPostHog implements PostHogClient {
  readonly captures: Array<{ name: string; properties: Record<string, unknown> }> = []
  readonly captureUserIds: Array<string | null> = []
  readonly identityCalls: string[] = []
  readonly lifecycle: string[] = []
  readonly recordingCalls: Array<{ sampling?: boolean } | true | undefined> = []
  initCalls = 0
  stopRecordingCalls = 0
  initializedWith: { token: string; config: Partial<PostHogConfig> } | null = null
  userId: string | null = null

  init(token: string, config: Partial<PostHogConfig>) {
    this.initCalls += 1
    this.initializedWith = { token, config }
    this.lifecycle.push('init')
    config.loaded?.(this as never)
    this.lifecycle.push('init-complete')
    return this
  }

  capture(name: string, properties: Record<string, unknown> = {}) {
    this.captures.push({ name, properties })
    this.captureUserIds.push(this.userId)
  }

  identify(distinctId: string) {
    this.identityCalls.push(distinctId)
    this.lifecycle.push(`identify:${distinctId}`)
    this.userId = distinctId
  }

  reset() {
    this.lifecycle.push('reset')
    this.userId = null
  }

  get_property(name: string) {
    return name === '$user_id' ? this.userId : undefined
  }

  startSessionRecording(override?: { sampling?: boolean } | true) {
    this.recordingCalls.push(override)
    this.lifecycle.push('start-recording')
  }

  stopSessionRecording() {
    this.stopRecordingCalls += 1
  }
}

function setup(
  config: PostHogRuntimeConfig | null = runtimeConfig,
  random: () => number = () => 0.5,
) {
  const target = new CountingEventTarget()
  const client = new MockPostHog()
  let loads = 0
  const integration = createPostHogAnalyticsIntegration(config, {
    eventTarget: target,
    loadClient: async () => {
      loads += 1
      return client
    },
    random,
  })
  return { client, integration, target, loads: () => loads }
}

afterEach(() => {
  Reflect.deleteProperty(globalThis, 'window')
})

describe('PostHog runtime configuration', () => {
  const productionEnvironment = {
    PROD: true,
    MODE: 'production',
    VITE_POSTHOG_ENABLED: 'true',
    VITE_POSTHOG_PROJECT_TOKEN: 'phc_public_browser_token',
    VITE_POSTHOG_HOST: 'https://eu.i.posthog.com',
  }

  test('requires explicit production configuration and rejects local traffic', () => {
    expect(
      resolvePostHogRuntimeConfig(
        { ...productionEnvironment, PROD: false },
        { hostname: 'app.usemeant.com', pathname: '/' },
      ),
    ).toBeNull()
    expect(
      resolvePostHogRuntimeConfig(
        { ...productionEnvironment, MODE: 'test' },
        { hostname: 'app.usemeant.com', pathname: '/' },
      ),
    ).toBeNull()
    expect(
      resolvePostHogRuntimeConfig(productionEnvironment, {
        hostname: 'localhost',
        pathname: '/',
      }),
    ).toBeNull()
    expect(
      resolvePostHogRuntimeConfig(productionEnvironment, {
        hostname: '192.168.1.20',
        pathname: '/',
      }),
    ).toBeNull()
    expect(
      resolvePostHogRuntimeConfig(productionEnvironment, {
        hostname: 'meant.local',
        pathname: '/',
      }),
    ).toBeNull()
    expect(
      resolvePostHogRuntimeConfig(
        { ...productionEnvironment, VITE_POSTHOG_PROJECT_TOKEN: '' },
        { hostname: 'app.usemeant.com', pathname: '/' },
      ),
    ).toBeNull()
  })

  test('keeps replay bounded and disabled on auth routes', () => {
    const enabled = {
      ...productionEnvironment,
      VITE_POSTHOG_SESSION_REPLAY_ENABLED: 'true',
      VITE_POSTHOG_SESSION_REPLAY_SAMPLE_RATE: '1',
    }

    expect(
      resolvePostHogRuntimeConfig(enabled, { hostname: 'app.usemeant.com', pathname: '/' }),
    ).toMatchObject({ sessionReplayEnabled: true, sessionReplaySampleRate: 0.1 })
    expect(
      resolvePostHogRuntimeConfig(enabled, {
        hostname: 'app.usemeant.com',
        pathname: '/auth/callback',
      }),
    ).toMatchObject({ sessionReplayEnabled: false })
  })

  test('is a safe no-op during SSR', () => {
    expect(browserPostHogRuntimeConfig()).toBeNull()
  })
})

describe('PostHog semantic event forwarding', () => {
  test('forwards one event after repeated integration starts', async () => {
    const { client, integration, target, loads } = setup()

    integration.start()
    integration.start()
    target.emit({ name: 'product_opened', properties: { anonymous: true } })
    integration.syncIdentity({ kind: 'anonymous' })
    await integration.ready()

    expect(target.additions).toBe(1)
    expect(loads()).toBe(1)
    expect(client.captures).toEqual([{ name: 'product_opened', properties: { anonymous: true } }])
  })

  test('allows only bounded funnel properties and campaign attribution', async () => {
    const { client, integration, target } = setup()
    integration.start()
    integration.syncIdentity({ kind: 'anonymous' })
    await integration.ready()

    target.emit({
      name: 'agent_refinement_submitted',
      properties: {
        anonymous: true,
        refinement_ordinal: 2,
        utm_source: 'person@example.com',
        utm_medium: 'email',
        utm_campaign: 'autumn_launch',
        utm_content: 'Bearer secret-token',
        conversation_id: 'conversation-secret',
        prompt: 'raw user prompt',
        email: 'person@example.com',
        access_token: 'secret',
      },
    })
    target.emit({
      name: 'anonymous_session_failed',
      properties: { reason: 'raw upstream error with request details' },
    })

    expect(client.captures).toEqual([
      {
        name: 'agent_refinement_submitted',
        properties: {
          anonymous: true,
          refinement_ordinal: 2,
          utm_medium: 'email',
          utm_campaign: 'autumn_launch',
        },
      },
      { name: 'anonymous_session_failed', properties: {} },
    ])
  })

  test('rejects credential-shaped and overlong campaign values', () => {
    expect(
      safeMeantEventProperties('brief_submitted', {
        utm_source: 'phc_abcdefghijklmnopqrstuvwxyz123456',
        utm_medium: 'sk_live_abcdefghijklmnopqrstuvwxyz123456',
        utm_campaign: 'ghp_abcdefghijklmnopqrstuvwxyz123456',
        utm_content: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwcml2YXRlIn0.signature',
      }),
    ).toEqual({})
    expect(
      safeMeantEventProperties('brief_submitted', {
        utm_source: 'token=oauth-secret',
        utm_medium: 'a'.repeat(101),
        utm_campaign: 'autumn_launch',
      }),
    ).toEqual({ utm_campaign: 'autumn_launch' })
  })

  test('does not load the SDK or attach listeners without configuration', async () => {
    const { client, integration, target, loads } = setup(null)
    integration.start()
    integration.syncIdentity({ kind: 'permanent', userId: 'user-a' })
    target.emit({ name: 'brief_submitted', properties: {} })
    await integration.ready()

    expect(target.additions).toBe(0)
    expect(loads()).toBe(0)
    expect(client.initCalls).toBe(0)
    expect(client.captures).toHaveLength(0)
  })

  test('removes and reattaches the exact listener without duplicating captures', async () => {
    const { client, integration, target } = setup()
    integration.start()
    integration.syncIdentity({ kind: 'anonymous' })
    await integration.ready()
    integration.stop()
    target.emit({ name: 'product_opened', properties: {} })
    integration.start()
    integration.syncIdentity({ kind: 'anonymous' })
    target.emit({ name: 'product_opened', properties: {} })

    expect(client.captures).toHaveLength(1)
  })

  test('reconciles persisted identity before forwarding initial events', async () => {
    const { client, integration, target } = setup()
    client.userId = 'previous-permanent-user'
    integration.start()
    target.emit({ name: 'campaign_brief_loaded', properties: { anonymous: true } })
    await integration.ready()

    expect(client.initCalls).toBe(0)
    expect(client.captures).toHaveLength(0)

    integration.syncIdentity({ kind: 'signed-out' })
    await integration.ready()

    expect(client.lifecycle).toEqual(['init', 'reset', 'init-complete'])
    expect(client.captureUserIds).toEqual([null])
    expect(client.captures).toHaveLength(1)
  })

  test('retains identity reconciliation when the delayed event queue overflows', async () => {
    const target = new CountingEventTarget()
    const client = new MockPostHog()
    client.userId = 'previous-permanent-user'
    let resolveClient: ((client: PostHogClient) => void) | undefined
    const integration = createPostHogAnalyticsIntegration(runtimeConfig, {
      eventTarget: target,
      loadClient: () =>
        new Promise<PostHogClient>((resolve) => {
          resolveClient = resolve
        }),
    })

    integration.start()
    integration.syncIdentity({ kind: 'anonymous' })
    for (let index = 0; index < 250; index += 1) {
      target.emit({ name: 'product_opened', properties: { anonymous: true } })
    }
    resolveClient?.(client)
    await integration.ready()

    expect(client.lifecycle.slice(0, 3)).toEqual(['init', 'reset', 'init-complete'])
    expect(client.captures).toHaveLength(200)
    expect(client.captureUserIds.every((userId) => userId === null)).toBe(true)
  })
})

describe('PostHog identity lifecycle', () => {
  test('never identifies a Supabase anonymous user', () => {
    expect(
      identityForSupabaseSession({ user: { id: 'supabase-anonymous-id', is_anonymous: true } }),
    ).toEqual({ kind: 'anonymous' })
    expect(
      identityForSupabaseSession({ user: { id: 'canonical-user-id', is_anonymous: false } }),
    ).toEqual({ kind: 'permanent', userId: 'canonical-user-id' })
  })

  test('merges anonymous history on permanent auth and resets on logout', async () => {
    const { client, integration } = setup()
    integration.start()
    integration.syncIdentity({ kind: 'anonymous' })
    await integration.ready()
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-a' })
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-a' })
    integration.resetIdentity()
    integration.syncIdentity({ kind: 'anonymous' })
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-b' })

    expect(client.lifecycle).toEqual([
      'init',
      'init-complete',
      'identify:canonical-user-a',
      'reset',
      'identify:canonical-user-b',
    ])
    expect(client.identityCalls).not.toContain('supabase-anonymous-id')
  })

  test('resets before a direct permanent account switch', async () => {
    const { client, integration } = setup()
    integration.start()
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-a' })
    await integration.ready()
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-b' })

    expect(client.lifecycle).toEqual([
      'init',
      'identify:canonical-user-a',
      'init-complete',
      'reset',
      'identify:canonical-user-b',
    ])
  })

  test('reconciles a queued account switch before starting delayed replay', async () => {
    const target = new CountingEventTarget()
    const client = new MockPostHog()
    let resolveClient: ((client: PostHogClient) => void) | undefined
    const integration = createPostHogAnalyticsIntegration(
      { ...runtimeConfig, sessionReplayEnabled: true },
      {
        eventTarget: target,
        loadClient: () =>
          new Promise<PostHogClient>((resolve) => {
            resolveClient = resolve
          }),
        random: () => 0,
      },
    )

    integration.start()
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-a' })
    integration.syncIdentity({ kind: 'permanent', userId: 'canonical-user-b' })
    resolveClient?.(client)
    await integration.ready()

    expect(client.lifecycle).toEqual([
      'init',
      'identify:canonical-user-a',
      'reset',
      'identify:canonical-user-b',
      'start-recording',
      'init-complete',
    ])
  })
})

describe('PostHog privacy options', () => {
  test('disables automatic capture and replay by default', () => {
    const options = postHogBrowserOptions(runtimeConfig)

    expect(options).toMatchObject({
      autocapture: false,
      capture_pageview: false,
      capture_pageleave: false,
      capture_dead_clicks: false,
      capture_exceptions: false,
      capture_performance: false,
      disable_session_recording: true,
      disable_external_dependency_loading: true,
      disable_conversations: true,
      disable_product_tours: true,
      disable_web_experiments: true,
      opt_in_site_apps: false,
      advanced_disable_flags: true,
      advanced_disable_feature_flags: true,
      enable_recording_console_log: false,
      mask_personal_data_properties: true,
      person_profiles: 'identified_only',
      save_campaign_params: false,
    })
    expect(options.session_recording).toMatchObject({
      sampleRate: 0,
      maskAllInputs: true,
      maskTextSelector: '*',
      recordHeaders: false,
      recordBody: false,
      streamNetworkBody: false,
    })
    expect(options.session_recording?.blockSelector).toContain('.mt-ct-tabs')
    expect(options.session_recording?.blockSelector).toContain('.mt-ct-history-wrap')
    expect(options.session_recording?.blockSelector).toContain('.mt-orders')
  })

  test('starts only a locally sampled replay after identity reconciliation', async () => {
    const replayConfig = { ...runtimeConfig, sessionReplayEnabled: true }
    expect(postHogBrowserOptions(replayConfig).advanced_disable_flags).toBe(false)
    const sampled = setup(replayConfig, () => 0)
    sampled.integration.start()

    expect(sampled.loads()).toBe(0)
    sampled.integration.syncIdentity({ kind: 'anonymous' })
    await sampled.integration.ready()

    expect(sampled.client.recordingCalls).toEqual([{ sampling: true }])
    expect(sampled.client.lifecycle).toEqual(['init', 'start-recording', 'init-complete'])

    const notSampled = setup(replayConfig, () => 0.5)
    notSampled.integration.start()
    notSampled.integration.syncIdentity({ kind: 'anonymous' })
    await notSampled.integration.ready()
    expect(notSampled.client.recordingCalls).toHaveLength(0)
  })

  test('strips query data and sensitive fields before sending', () => {
    const capture = sanitizePostHogCapture({
      uuid: 'event-id',
      event: 'brief_submitted',
      properties: {
        token: 'phc_public_browser_token',
        $current_url: 'https://app.usemeant.com/?brief=private&utm_source=newsletter#private',
        $session_entry_referrer: 'https://identity.example/callback?code=private',
        $session_entry_gclid: 'private-click-id',
        $referrer: 'https://identity.example/callback?code=private',
        $referring_domain: 'identity.example',
        utm_source: 'newsletter',
        ph_keyword: 'raw search terms from the referrer',
        email: 'person@example.com',
        brief: 'private request',
        anonymous: true,
      },
      $set_once: {
        $initial_current_url: 'https://app.usemeant.com/auth/callback?code=private',
        utm_term: 'person@example.com',
        gclid: 'private-click-id',
        referrer: 'https://identity.example/callback?code=private',
        $referrer: 'https://identity.example/callback?code=private',
        $referring_domain: 'identity.example',
        $initial_referrer: 'https://identity.example/callback?code=private',
        $initial_referring_domain: 'identity.example',
        $initial_ph_keyword: 'raw search terms from the referrer',
        email: 'person@example.com',
      },
    } as CaptureResult)

    expect(capture?.properties).toEqual({
      token: 'phc_public_browser_token',
      $current_url: 'https://app.usemeant.com/',
      utm_source: 'newsletter',
      anonymous: true,
    })
    expect(capture?.$set_once).toEqual({
      $initial_current_url: 'https://app.usemeant.com/auth/callback',
    })
  })

  test('removes replay network bodies and headers even when replay is enabled', () => {
    const options = postHogBrowserOptions({
      ...runtimeConfig,
      sessionReplayEnabled: true,
    })
    const sanitizer = options.session_recording?.maskCapturedNetworkRequestFn
    const sanitized = sanitizer?.({
      name: 'https://user:private@example.com/agent?token=private',
      entryType: 'resource',
      startTime: 0,
      duration: 1,
      requestHeaders: { authorization: 'Bearer private' },
      requestBody: 'private request',
      responseHeaders: { 'set-cookie': 'private' },
      responseBody: 'private response',
    } as CapturedNetworkRequest)

    expect(sanitized).toMatchObject({
      name: 'https://example.com/',
      requestHeaders: undefined,
      requestBody: undefined,
      responseHeaders: undefined,
      responseBody: undefined,
    })
  })
})
