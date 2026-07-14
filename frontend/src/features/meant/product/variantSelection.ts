import type {
  CanonicalOfferProfile,
  CanonicalProductProfile,
  MerchantProductDetailsProfile,
  MerchantProductVariantProfile,
  ProductOptionProfile,
  ProductSelectedOptionProfile,
  ProductVariantSelectionProfile,
} from '../../../lib/apiClient'
import type { Offer, Product } from '../types'
import { canonicalMerchantScopeKey, minorUnitsToMajor } from '../utils'

export type OptionValueState = 'available' | 'sold-out' | 'impossible' | 'unknown'

export interface MerchantOfferChoice {
  key: string
  label: string
  anchor: CanonicalOfferProfile
}

function normalized(value: string | null | undefined): string {
  return value?.trim().toLocaleLowerCase() ?? ''
}

export function optionSelectionKey(option: Pick<ProductSelectedOptionProfile, 'name'>): string {
  return normalized(option.name)
}

export function cleanSelectedOptions(
  options: readonly ProductSelectedOptionProfile[] | null | undefined,
): ProductSelectedOptionProfile[] {
  const byName = new Map<string, ProductSelectedOptionProfile>()
  ;(options ?? []).forEach((option) => {
    const name = option.name?.trim()
    const value = option.value?.trim()
    if (!name || !value) return
    byName.set(normalized(name), { name, value })
  })
  return Array.from(byName.values())
}

export function selectedOptionsWithPreference(
  current: readonly ProductSelectedOptionProfile[],
  preferred: ProductSelectedOptionProfile,
  optionOrder: readonly string[],
): ProductSelectedOptionProfile[] {
  const next = new Map(
    cleanSelectedOptions(current).map((option) => [optionSelectionKey(option), option]),
  )
  const name = preferred.name?.trim()
  const value = preferred.value?.trim()
  if (name && value) next.set(normalized(name), { name, value })

  const order = new Map(optionOrder.map((option, index) => [normalized(option), index]))
  return Array.from(next.values()).sort(
    (left, right) =>
      (order.get(optionSelectionKey(left)) ?? Number.MAX_SAFE_INTEGER) -
        (order.get(optionSelectionKey(right)) ?? Number.MAX_SAFE_INTEGER) ||
      left.name!.localeCompare(right.name!),
  )
}

export function selectedOptionsEqual(
  left: readonly ProductSelectedOptionProfile[] | null | undefined,
  right: readonly ProductSelectedOptionProfile[] | null | undefined,
): boolean {
  const normalizedOptions = (options: readonly ProductSelectedOptionProfile[] | null | undefined) =>
    cleanSelectedOptions(options)
      .map((option) => `${normalized(option.name)}\u0000${normalized(option.value)}`)
      .sort()
  const first = normalizedOptions(left)
  const second = normalizedOptions(right)
  return first.length === second.length && first.every((value, index) => value === second[index])
}

export function savedOfferInitiallyCartable(
  offer: Pick<Offer, 'offerKey' | 'available'> | null | undefined,
): boolean {
  return Boolean(offer?.offerKey?.trim() && offer.available === true)
}

export function savedSelectionChanged(
  baseline: Pick<MerchantProductDetailsProfile, 'selectedVariantId' | 'selectedOptions'>,
  current: Pick<MerchantProductDetailsProfile, 'selectedVariantId' | 'selectedOptions'>,
  baselineMerchantKey?: string | null,
  currentMerchantKey?: string | null,
): boolean {
  if (
    baselineMerchantKey &&
    currentMerchantKey &&
    normalized(baselineMerchantKey) !== normalized(currentMerchantKey)
  ) {
    return true
  }
  const baselineVariantId = baseline.selectedVariantId?.trim()
  const currentVariantId = current.selectedVariantId?.trim()
  if (baselineVariantId && currentVariantId) {
    return baselineVariantId !== currentVariantId
  }
  return !selectedOptionsEqual(baseline.selectedOptions, current.selectedOptions)
}

function variantOptionValue(
  variant: MerchantProductVariantProfile,
  optionName: string,
): string | null {
  return (
    variant.selectedOptions
      .find((option) => normalized(option.name) === normalized(optionName))
      ?.value?.trim() || null
  )
}

function variantMatches(
  variant: MerchantProductVariantProfile,
  selections: readonly ProductSelectedOptionProfile[],
): boolean {
  return selections.every(
    (selection) =>
      normalized(variantOptionValue(variant, selection.name ?? '')) === normalized(selection.value),
  )
}

export function optionValueState(
  option: ProductOptionProfile,
  variants: readonly MerchantProductVariantProfile[],
  selections: readonly ProductSelectedOptionProfile[],
  optionValue: string,
): OptionValueState {
  const detail = option.valueDetails?.find(
    (value) => normalized(value.value) === normalized(optionValue),
  )
  if (detail) {
    if (detail.exists === false) return 'impossible'
    if (detail.available === false) return 'sold-out'
    if (detail.available === true) return 'available'
    return 'unknown'
  }

  const optionName = option.name ?? ''
  const otherSelections = selections.filter(
    (selection) => normalized(selection.name) !== normalized(optionName),
  )
  const matching = variants.filter(
    (variant) =>
      normalized(variantOptionValue(variant, optionName)) === normalized(optionValue) &&
      variantMatches(variant, otherSelections),
  )
  if (matching.length === 0) {
    return 'impossible'
  }
  if (matching.every((variant) => variant.available === false)) return 'sold-out'
  return matching.some((variant) => variant.available === true) ? 'available' : 'unknown'
}

export function findSelectedVariant(
  variants: readonly MerchantProductVariantProfile[],
  variantId: string | null | undefined,
  selections: readonly ProductSelectedOptionProfile[],
): MerchantProductVariantProfile | null {
  const id = variantId?.trim()
  if (id) {
    const byId = variants.find((variant) => variant.variantId?.trim() === id)
    if (byId) return byId
  }
  return variants.find((variant) => variantMatches(variant, selections)) ?? null
}

function merchantChoiceKey(offer: CanonicalOfferProfile): string {
  const provider = normalized(offer.identity.provider)
  const identityScopeKey = canonicalMerchantScopeKey(offer)
  if (identityScopeKey) return identityScopeKey

  const provenanceIntegrationId = offer.provenance
    .find((item) => item.localRouting?.merchantIntegrationId)
    ?.localRouting?.merchantIntegrationId?.trim()
  if (provenanceIntegrationId) {
    return `${provider}:integration-fallback:${provenanceIntegrationId}`
  }

  const merchantName = normalized(offer.merchantName)
  return merchantName
    ? `${provider}:merchant-name:${merchantName}`
    : `${provider}:offer:${offer.key}`
}

export function merchantOfferChoices(product: CanonicalProductProfile): MerchantOfferChoice[] {
  const recommended = product.offers.find((offer) => offer.key === product.recommendedOfferKey)
  const byMerchant = new Map<string, CanonicalOfferProfile>()
  if (recommended) byMerchant.set(merchantChoiceKey(recommended), recommended)
  product.offers.forEach((offer) => {
    const key = merchantChoiceKey(offer)
    if (!byMerchant.has(key)) byMerchant.set(key, offer)
  })
  return Array.from(byMerchant.entries()).map(([key, anchor]) => ({
    key,
    label: anchor.merchantName?.trim() || 'Merchant',
    anchor,
  }))
}

export function productWithVariantSelection(
  product: Product,
  canonicalProduct: CanonicalProductProfile | null,
  selection: ProductVariantSelectionProfile,
  details: MerchantProductDetailsProfile | null,
): Product {
  const selectedOptions = cleanSelectedOptions(selection.details.selectedOptions).map((option) => ({
    name: option.name ?? '',
    value: option.value ?? '',
  }))
  const variant = findSelectedVariant(
    details?.variants ?? [],
    selection.details.selectedVariantId,
    selectedOptions,
  )
  // Exact identity is server-authored. Never inherit an anchor offer's identity, components,
  // selling plan, or provenance for a sibling variant.
  const exactOffer = selection.selectedOffer ?? null
  const exactLocalRouting = exactOffer?.provenance.find((item) => item.localRouting)?.localRouting
  const exactExternalMerchant = exactOffer?.provenance.find(
    (item) => item.externalMerchantReference,
  )?.externalMerchantReference
  const exactAvailable = exactOffer
    ? exactOffer.availability.status === 'OUT_OF_STOCK' ||
      exactOffer.availability.status === 'DISCONTINUED'
      ? false
      : exactOffer.availability.status === 'UNKNOWN'
        ? null
        : true
    : null
  const currentCanonical = canonicalProduct ?? product.canonicalProduct ?? null
  const nextCanonical =
    currentCanonical && exactOffer
      ? {
          ...currentCanonical,
          recommendedOfferKey: exactOffer.key,
          offers: [
            exactOffer,
            ...currentCanonical.offers.filter((offer) => offer.key !== exactOffer.key),
          ],
        }
      : product.canonicalProduct

  return {
    ...product,
    selectedOptions,
    selectedVariantAvailable: variant?.available ?? exactAvailable,
    ...(details ? { rehydratedDetails: details } : {}),
    ...(nextCanonical ? { canonicalProduct: nextCanonical } : {}),
    offers:
      exactOffer && selection.selectedOfferKey
        ? [
            {
              offerKey: selection.selectedOfferKey,
              merchant: exactOffer.merchantName?.trim() || 'Merchant',
              price: variant?.priceAmount
                ? Number(variant.priceAmount)
                : (minorUnitsToMajor(exactOffer.price?.minorUnits, exactOffer.price?.currency) ??
                  Number.NaN),
              priceMinorUnits: exactOffer.price?.minorUnits ?? null,
              priceCurrency: variant?.priceCurrency ?? exactOffer.price?.currency ?? null,
              delivery: 'Delivery calculated by merchant',
              merchantId: null,
              merchantDomain:
                exactOffer.provenance.find((item) => item.externalMerchantDomain)
                  ?.externalMerchantDomain ?? null,
              provider: exactOffer.identity.provider,
              merchantIntegrationId:
                exactLocalRouting?.merchantIntegrationId ??
                exactOffer.identity.merchantScope.merchantIntegrationFallbackId ??
                null,
              externalMerchantId:
                exactOffer.identity.merchantScope.externalMerchantIdentity?.value ??
                exactExternalMerchant?.value ??
                null,
              merchantScopeKey: canonicalMerchantScopeKey(exactOffer),
              productVariantId: selection.details.selectedVariantId,
              variantTitle: selection.details.selectedVariantTitle,
              available: variant?.available ?? exactAvailable,
            },
            ...product.offers.filter((offer) => offer.offerKey !== selection.selectedOfferKey),
          ]
        : product.offers,
  }
}
