import { describe, expect, test } from 'bun:test'

import {
  MARKET_ANCHOR_GMV,
  MODEL_ASSUMPTION_LABEL,
  calculateRevenueScenario,
  formatCompactUsd,
  pitchSourceGroups,
  revenueScenarioPresets,
  type PitchSource,
} from './pitchModel'

describe('pitch revenue model', () => {
  test.each([
    [0.02, 800_000_000, 16_000_000],
    [0.1, 4_000_000_000, 80_000_000],
    [0.5, 20_000_000_000, 400_000_000],
  ])('models a %s%% share of the $4T anchor', (sharePercent, gmv, revenue) => {
    expect(
      calculateRevenueScenario({
        marketGmv: MARKET_ANCHOR_GMV,
        sharePercent,
        takeRatePercent: 2,
      }),
    ).toMatchObject({ gmv, revenue })
  })

  test('rejects negative inputs instead of producing misleading output', () => {
    expect(() =>
      calculateRevenueScenario({
        marketGmv: MARKET_ANCHOR_GMV,
        sharePercent: -0.1,
        takeRatePercent: 2,
      }),
    ).toThrow(RangeError)
  })

  test('rejects non-finite inputs instead of rendering invalid market math', () => {
    expect(() =>
      calculateRevenueScenario({
        marketGmv: MARKET_ANCHOR_GMV,
        sharePercent: Number.NaN,
        takeRatePercent: 2,
      }),
    ).toThrow(RangeError)
  })

  test('formats scenario values without false precision', () => {
    expect(formatCompactUsd(MARKET_ANCHOR_GMV)).toBe('$4T')
    expect(formatCompactUsd(800_000_000)).toBe('$800M')
    expect(formatCompactUsd(4_000_000_000)).toBe('$4B')
    expect(formatCompactUsd(16_000_000)).toBe('$16M')
    expect(formatCompactUsd(80_000_000)).toBe('$80M')
    expect(formatCompactUsd(400_000_000)).toBe('$400M')
  })

  test('ships the three required scenario shares and explicit assumption language', () => {
    expect(revenueScenarioPresets.map(({ sharePercent }) => sharePercent)).toEqual([0.02, 0.1, 0.5])
    expect(MODEL_ASSUMPTION_LABEL).toContain('not a forecast')
  })
})

describe('pitch sources', () => {
  test('uses direct secure sources and no superseded secondary publications', () => {
    const sources = pitchSourceGroups.reduce<PitchSource[]>(
      (allSources, group) => [...allSources, ...group.sources],
      [],
    )
    const blockedHosts = [
      'axios.com',
      'investopedia.com',
      'investors.com',
      'apnews.com',
      'theverge.com',
    ]

    expect(sources.length).toBeGreaterThan(10)
    for (const source of sources) {
      const url = new URL(source.href)
      expect(url.protocol).toBe('https:')
      expect(blockedHosts).not.toContain(url.hostname.replace(/^www\./, ''))
      expect(source.label.length).toBeGreaterThan(0)
      expect(source.note.length).toBeGreaterThan(0)
    }
  })
})
