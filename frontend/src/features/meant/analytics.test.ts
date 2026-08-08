import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import {
  MEANT_ANALYTICS_EVENT_NAME,
  trackMeantEvent,
  type MeantAnalyticsEventDetail,
} from './analytics'
import { CAMPAIGN_ATTRIBUTION_KEY } from './campaign/campaignAttribution'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }

  removeItem(key: string) {
    this.values.delete(key)
  }
}

let browserEvents: EventTarget
let sessionStorage: MemoryStorage

beforeEach(() => {
  browserEvents = new EventTarget()
  sessionStorage = new MemoryStorage()
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: Object.assign(browserEvents, { sessionStorage }),
  })
})

afterEach(() => {
  Reflect.deleteProperty(globalThis, 'window')
})

describe('Meant semantic analytics', () => {
  test('dispatches a semantic browser event without mutating caller properties', () => {
    const properties = { anonymous: true }
    const received: MeantAnalyticsEventDetail[] = []
    browserEvents.addEventListener(MEANT_ANALYTICS_EVENT_NAME, (event) => {
      received.push((event as CustomEvent<MeantAnalyticsEventDetail>).detail)
    })

    trackMeantEvent('brief_submitted', properties)

    expect(received).toEqual([{ name: 'brief_submitted', properties: { anonymous: true } }])
    expect(properties).toEqual({ anonymous: true })
  })

  test('adds stored campaign attribution to every downstream event', () => {
    sessionStorage.setItem(
      CAMPAIGN_ATTRIBUTION_KEY,
      JSON.stringify({
        utmSource: 'newsletter',
        utmMedium: 'email',
        utmCampaign: 'autumn_launch',
        utmContent: 'hero',
      }),
    )
    const received: MeantAnalyticsEventDetail[] = []
    browserEvents.addEventListener(MEANT_ANALYTICS_EVENT_NAME, (event) => {
      received.push((event as CustomEvent<MeantAnalyticsEventDetail>).detail)
    })

    trackMeantEvent('merchant_outbound_clicked')

    expect(received.at(-1)?.properties).toEqual({
      utm_source: 'newsletter',
      utm_medium: 'email',
      utm_campaign: 'autumn_launch',
      utm_content: 'hero',
    })
  })

  test('is an SSR-safe no-op', () => {
    Reflect.deleteProperty(globalThis, 'window')
    expect(() => trackMeantEvent('product_opened')).not.toThrow()
  })
})
