import type { Preference, PreferenceId, Product } from '../types'
import { prefLabel } from '../utils'

export const NO_CONFIRMED_PREFERENCE_TAKE =
  'No preference matches are confirmed yet; review the details and offers.'
const SEARCH_RELEVANCE_TAKE =
  'This looks relevant to your search based on the available product details.'

function preferenceLabels(
  ids: readonly PreferenceId[],
  preferences: readonly Preference[],
): string[] {
  return ids.map((id) => prefLabel(preferences, id)).filter(Boolean)
}

function lowerLabel(label: string): string {
  return label.trim().toLowerCase()
}

function humanList(items: readonly string[]): string {
  if (items.length <= 1) {
    return items[0] ?? ''
  }
  if (items.length === 2) {
    return `${items[0]} and ${items[1]}`
  }
  return `${items.slice(0, -1).join(', ')}, and ${items[items.length - 1]}`
}

function preferenceTarget(label: string): { kind: 'prefer' | 'avoid'; text: string } | null {
  const normalized = lowerLabel(label).replace(/\s+/g, ' ')
  if (!normalized) {
    return null
  }
  if (normalized.startsWith('no ')) {
    return { kind: 'avoid', text: normalized.slice(3).trim() }
  }
  if (normalized.startsWith('avoid ')) {
    return { kind: 'avoid', text: normalized.slice(6).trim() }
  }
  if (normalized.startsWith('without ')) {
    return { kind: 'avoid', text: normalized.slice(8).trim() }
  }
  if (normalized.startsWith('prefer ')) {
    return { kind: 'prefer', text: normalized.slice(7).trim() }
  }
  return { kind: 'prefer', text: normalized }
}

function preferenceSummary(labels: readonly string[]): string {
  const targets = labels
    .slice(0, 3)
    .map(preferenceTarget)
    .filter((target): target is { kind: 'prefer' | 'avoid'; text: string } => Boolean(target?.text))
  const preferred = targets
    .filter((target) => target.kind === 'prefer')
    .map((target) => target.text)
  const avoided = targets.filter((target) => target.kind === 'avoid').map((target) => target.text)
  const parts = [
    preferred.length > 0 ? `for ${humanList(preferred)}` : '',
    avoided.length > 0 ? `to avoid ${humanList(avoided)}` : '',
  ].filter(Boolean)
  const preferenceWord = targets.length === 1 ? 'preference' : 'preferences'
  return parts.length > 0 ? `your ${preferenceWord} ${humanList(parts)}` : 'your preferences'
}

function productPreferenceFacts(product: Product): string[] {
  return Array.from(
    new Set(
      [...(product.materials ?? []), ...(product.certifications ?? [])]
        .map((fact) => fact.trim())
        .filter(Boolean),
    ),
  )
}

function factMatchesPreference(fact: string, preference: string): boolean {
  const normalizedFact = lowerLabel(fact)
  const normalizedPreference = lowerLabel(preference)
  if (!normalizedFact || !normalizedPreference) {
    return false
  }
  return normalizedFact.includes(normalizedPreference)
}

function productPreferenceMatchPhrase(product: Product, labels: readonly string[]): string | null {
  const facts = productPreferenceFacts(product)
  for (const label of labels.slice(0, 3)) {
    const target = preferenceTarget(label)
    if (!target || target.kind !== 'prefer') {
      continue
    }
    const fact = facts.find((candidate) => factMatchesPreference(candidate, target.text))
    if (fact) {
      return `This item is listed as ${fact}, matching ${preferenceSummary([label])}`
    }
  }
  return null
}

function productFallbackTake(product: Product): string {
  const note = product.note.trim()
  if (note && note !== NO_CONFIRMED_PREFERENCE_TAKE) {
    return note
  }
  return SEARCH_RELEVANCE_TAKE
}

export function productCuratedTake(product: Product, preferences: readonly Preference[]): string {
  if (product.agentStage === 'candidate') {
    return 'This is being checked against your preferences.'
  }
  if (product.inventoryRelationship === 'DUPLICATE') {
    return `This looks close to ${product.inventoryItemName ?? 'something you already own'}, so compare before buying.`
  }
  const matched = preferenceLabels(product.satisfies, preferences)
  const missed = preferenceLabels(product.misses, preferences)
  const matchedPhrase = productPreferenceMatchPhrase(product, matched)
  if (matched.length > 0 && missed.length > 0) {
    return `${matchedPhrase ?? `This matches ${preferenceSummary(matched)}`}, but check whether it fits ${preferenceSummary(missed)} before deciding.`
  }
  if (matched.length > 0) {
    return `${matchedPhrase ?? `This matches ${preferenceSummary(matched)}`}.`
  }
  if (missed.length > 0) {
    return `Check whether this fits ${preferenceSummary(missed)} before deciding.`
  }
  return productFallbackTake(product)
}

export function productCuratedAdvantages(
  product: Product,
  preferences: readonly Preference[],
): string[] {
  const advantages = preferenceLabels(product.satisfies, preferences).map(
    (label) => `Matches ${preferenceSummary([label])}`,
  )
  if (product.review.score !== null && product.review.count > 0) {
    advantages.push(
      `Rated ${product.review.score.toFixed(1)} out of 5 from ${product.review.count.toLocaleString()} reviews`,
    )
  }
  product.certifications?.slice(0, 2).forEach((certification) => {
    advantages.push(`Product details list ${certification}`)
  })
  if (advantages.length === 0 && product.materials && product.materials.length > 0) {
    advantages.push(`Product details list ${product.materials[0]}`)
  }
  if (advantages.length === 0) {
    advantages.push(
      product.agentStage === 'candidate'
        ? 'Checking this against your preferences'
        : 'No confirmed preference advantages yet',
    )
  }
  return Array.from(new Set(advantages))
}

export function productCuratedTradeoffs(
  product: Product,
  preferences: readonly Preference[],
): string[] {
  const tradeoffs = preferenceLabels(product.misses, preferences).map(
    (label) => `May not fit ${preferenceSummary([label])}`,
  )
  if (product.inventoryRelationship === 'DUPLICATE') {
    tradeoffs.push(`Similar to ${product.inventoryItemName ?? 'something you already own'}`)
  }
  if (product.offers?.every((offer) => offer.available === false)) {
    tradeoffs.push('Current offers are marked unavailable')
  }
  if (tradeoffs.length === 0) {
    tradeoffs.push('No preference trade-offs found in the available details')
  }
  return Array.from(new Set(tradeoffs))
}

export function productCuratedFields(
  product: Product,
  preferences: readonly Preference[],
): Pick<Product, 'note' | 'pros' | 'cons'> {
  if (preferences.length === 0) {
    return {
      note: product.note,
      pros: product.pros,
      cons: product.cons,
    }
  }
  return {
    note: productCuratedTake(product, preferences),
    pros: productCuratedAdvantages(product, preferences),
    cons: productCuratedTradeoffs(product, preferences),
  }
}

export function productWithCuratedFields(
  product: Product,
  preferences: readonly Preference[],
): Product {
  return {
    ...product,
    ...productCuratedFields(product, preferences),
  }
}
