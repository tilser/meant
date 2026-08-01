import {
  selectProductVariant,
  type CanonicalProductDetailProfile,
  type CanonicalProductProfile,
  type MerchantProductDetailsProfile,
  type ProductSelectedOptionProfile,
  type ProductVariantSelectionProfile,
} from '../../../lib/apiClient'
import { ApiError } from '../../../lib/apiError'
import type { Product } from '../types'
import { loadCanonicalProductDetailWithRecovery } from './canonicalProductSessionRecovery'
import {
  cleanSelectedOptions,
  merchantOfferChoices,
  productWithVariantSelection,
  selectedOptionsEqual,
} from './variantSelection'

export type DirectPurchasePreparation =
  | { status: 'ready'; product: Product; offerKey: string }
  | { status: 'requires-selection'; message: string }
  | { status: 'unavailable'; message: string }

interface PurchaseAnchor {
  offerKey: string
  selectedOptions: ProductSelectedOptionProfile[]
  canonicalProduct: CanonicalProductProfile | null
}

interface DirectPurchaseDependencies {
  loadCanonicalDetail: typeof loadCanonicalProductDetailWithRecovery
  selectVariant: typeof selectProductVariant
}

const DEFAULT_DEPENDENCIES: DirectPurchaseDependencies = {
  loadCanonicalDetail: loadCanonicalProductDetailWithRecovery,
  selectVariant: selectProductVariant,
}

function normalizedValues(values: readonly (string | null | undefined)[]): string[] {
  const seen = new Set<string>()
  return values.flatMap((value) => {
    const trimmed = value?.trim()
    if (!trimmed) return []
    const key = trimmed.toLocaleLowerCase()
    if (seen.has(key)) return []
    seen.add(key)
    return [trimmed]
  })
}

function selectableOptionValues(
  option: MerchantProductDetailsProfile['options'][number],
): string[] {
  const detailed = option.valueDetails?.filter(
    (value) => value.exists !== false && value.available !== false,
  )
  if (detailed?.length) {
    return normalizedValues(detailed.map((value) => value.value))
  }
  return normalizedValues(option.values ?? [])
}

/** Direct add is safe only when the current provider detail has no unresolved shopper choice. */
export function requiresExplicitVariantSelection(details: MerchantProductDetailsProfile): boolean {
  if (details.options.some((option) => selectableOptionValues(option).length > 1)) {
    return true
  }

  const availableVariants = details.variants.filter((variant) => variant.available === true)
  if (availableVariants.length > 1) {
    return true
  }

  const totalVariants = details.totalVariants
  const returnedEveryVariant =
    totalVariants !== null && totalVariants >= 0 && details.variants.length >= totalVariants
  if (totalVariants !== null && totalVariants > 1 && !returnedEveryVariant) {
    return true
  }

  if (details.variants.length > 1 && availableVariants.length !== 1) {
    return true
  }

  return false
}

export function isExactProductVariantSelection(
  selection: ProductVariantSelectionProfile,
  requestedOptions: readonly ProductSelectedOptionProfile[],
): boolean {
  const selectedOfferKey = selection.selectedOfferKey?.trim()
  return Boolean(
    selectedOfferKey &&
    selection.selectedOffer?.key === selectedOfferKey &&
    selectedOptionsEqual(requestedOptions, selection.details.selectedOptions),
  )
}

export function isRecoverableSelectedOfferFailure(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false
  return (
    error.status === 404 ||
    error.reason === 'unknown_or_expired' ||
    error.reason === 'stale_or_unavailable' ||
    error.reason === 'identity_mismatch'
  )
}

function canonicalPurchaseAnchor(detail: CanonicalProductDetailProfile): PurchaseAnchor | null {
  const choices = merchantOfferChoices(detail.product)
  const choice =
    choices.find((candidate) => candidate.anchor.key === detail.selectedOfferKey) ??
    choices.find((candidate) => candidate.anchor.key === detail.recommendedOfferKey) ??
    choices[0]
  return choice
    ? {
        offerKey: choice.anchor.key,
        selectedOptions: cleanSelectedOptions(choice.anchor.selectedOptions),
        canonicalProduct: detail.product,
      }
    : null
}

function savedPurchaseAnchor(product: Product): PurchaseAnchor | null {
  const offerKey = product.offers.find((offer) => offer.offerKey?.trim())?.offerKey?.trim()
  if (!offerKey) return null
  return {
    offerKey,
    selectedOptions: cleanSelectedOptions(
      product.rehydratedDetails?.selectedOptions ?? product.selectedOptions,
    ),
    canonicalProduct: null,
  }
}

async function currentPurchaseAnchor(
  product: Product,
  expectedUserId: string,
  signal: AbortSignal | undefined,
  dependencies: DirectPurchaseDependencies,
): Promise<PurchaseAnchor | null> {
  if (!product.canonicalProduct) {
    return savedPurchaseAnchor(product)
  }
  const detail = await dependencies.loadCanonicalDetail({
    product,
    selectedOfferKey: product.canonicalProduct.recommendedOfferKey,
    signal,
    expectedUserId,
  })
  return canonicalPurchaseAnchor(detail)
}

export async function prepareDirectProductPurchase(
  input: {
    product: Product
    expectedUserId: string
    signal?: AbortSignal
  },
  dependencies: DirectPurchaseDependencies = DEFAULT_DEPENDENCIES,
): Promise<DirectPurchasePreparation> {
  const anchor = await currentPurchaseAnchor(
    input.product,
    input.expectedUserId,
    input.signal,
    dependencies,
  )
  if (!anchor) {
    return {
      status: 'unavailable',
      message: 'This product does not have a current merchant offer.',
    }
  }

  const selection = await dependencies.selectVariant({
    anchorOfferKey: anchor.offerKey,
    selectedOptions: anchor.selectedOptions.map((option) => ({
      name: option.name ?? '',
      value: option.value ?? '',
    })),
    signal: input.signal,
    expectedUserId: input.expectedUserId,
  })

  if (!isExactProductVariantSelection(selection, anchor.selectedOptions)) {
    return {
      status: 'requires-selection',
      message: 'Choose an exact available variant before adding this product.',
    }
  }
  if (!selection.cartable) {
    return requiresExplicitVariantSelection(selection.details)
      ? {
          status: 'requires-selection',
          message: 'That variant is unavailable. Choose another product option.',
        }
      : {
          status: 'unavailable',
          message: 'This exact product is not currently available from the merchant.',
        }
  }
  const selectedOfferKey = selection.selectedOfferKey!.trim()
  return {
    status: 'ready',
    offerKey: selectedOfferKey,
    product: productWithVariantSelection(
      input.product,
      anchor.canonicalProduct,
      selection,
      selection.details,
    ),
  }
}

export async function bindPreparedProductPurchaseWithRecovery(input: {
  product: Product
  offerKey: string
  expectedUserId: string
  bindSelectedOffer: (product: Product, offerKey: string) => Promise<boolean>
  preparePurchase?: typeof prepareDirectProductPurchase
}): Promise<boolean> {
  try {
    return await input.bindSelectedOffer(input.product, input.offerKey)
  } catch (error) {
    if (!isRecoverableSelectedOfferFailure(error)) throw error
  }

  const preparePurchase = input.preparePurchase ?? prepareDirectProductPurchase
  const refreshed = await preparePurchase({
    product: input.product,
    expectedUserId: input.expectedUserId,
  })
  return refreshed.status === 'ready'
    ? input.bindSelectedOffer(refreshed.product, refreshed.offerKey)
    : false
}
