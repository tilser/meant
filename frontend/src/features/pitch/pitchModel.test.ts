import { describe, expect, test } from 'bun:test'

import {
  MODEL_ASSUMPTION_LABEL,
  calculateCommerceScenario,
  commerceScenarioPresets,
  formatCompactUsd,
  pitchSourceGroups,
  type PitchSource,
} from './pitchModel'

describe('pitch bottom-up commerce model', () => {
  test('models consumer GMV and agent infrastructure revenue independently', () => {
    expect(calculateCommerceScenario(commerceScenarioPresets[1])).toMatchObject({
      consumerGmv: 9_600_000_000,
      transactionRevenue: 192_000_000,
      infrastructureRevenue: 10_000_000,
      totalRevenue: 202_000_000,
    })
  })

  test('rejects negative inputs instead of producing misleading output', () => {
    expect(() =>
      calculateCommerceScenario({
        ...commerceScenarioPresets[1],
        activeUsers: -1,
      }),
    ).toThrow(RangeError)
  })

  test('rejects non-finite inputs instead of rendering invalid market math', () => {
    expect(() =>
      calculateCommerceScenario({
        ...commerceScenarioPresets[1],
        infrastructureFee: Number.NaN,
      }),
    ).toThrow(RangeError)
  })

  test('formats scenario values without false precision', () => {
    expect(formatCompactUsd(9_600_000_000)).toBe('$9.6B')
    expect(formatCompactUsd(192_000_000)).toBe('$192M')
    expect(formatCompactUsd(10_000_000)).toBe('$10M')
    expect(formatCompactUsd(202_000_000)).toBe('$202M')
  })

  test('ships three bottom-up scenarios and explicit assumption language', () => {
    expect(commerceScenarioPresets.map(({ id }) => id)).toEqual(['launch', 'scale', 'global'])
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
