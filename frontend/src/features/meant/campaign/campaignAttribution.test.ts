import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import {
  CAMPAIGN_BRIEF_MAX_LENGTH,
  captureCampaignEntry,
  claimCampaignBriefLoaded,
  storedCampaignAttribution,
} from './campaignAttribution'

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

let replacedUrl = ''

beforeEach(() => {
  replacedUrl = ''
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {
      sessionStorage: new MemoryStorage(),
      history: {
        replaceState: (_state: unknown, _title: string, url: string) => {
          replacedUrl = url
        },
      },
    },
  })
  Object.defineProperty(globalThis, 'document', { configurable: true, value: { title: 'Meant' } })
})

afterEach(() => {
  Reflect.deleteProperty(globalThis, 'window')
  Reflect.deleteProperty(globalThis, 'document')
})

describe('campaign entry capture', () => {
  test('prefills a bounded brief, preserves attribution, and removes tracking parameters', () => {
    const entry = captureCampaignEntry({
      pathname: '/',
      search: `?brief=${'coat'.repeat(400)}&utm_source=tiktok&utm_campaign=natural_fibers&keep=yes`,
    })

    expect(entry.brief.length).toBe(CAMPAIGN_BRIEF_MAX_LENGTH)
    expect(storedCampaignAttribution()?.utmSource).toBe('tiktok')
    expect(replacedUrl).toBe('/?keep=yes')
  })

  test('claims the brief-loaded analytics event once across refreshes', () => {
    expect(claimCampaignBriefLoaded('Find a wool coat')).toBeTrue()
    expect(claimCampaignBriefLoaded('Find a wool coat')).toBeFalse()
  })
})
