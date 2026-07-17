import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import {
  ApiError,
  selectProductVariant,
  type CanonicalOfferProfile,
  type CanonicalProductDetailProfile,
  type CanonicalProductProfile,
  type MerchantProductDetailsProfile,
  type ProductSelectedOptionProfile,
  type ProductVariantSelectionProfile,
} from '../../../lib/apiClient'
import type { Product } from '../types'
import { minorUnitsToMajor, money } from '../utils'
import { loadCanonicalProductDetailWithRecovery } from './canonicalProductSessionRecovery'
import { trackCommerceEvent } from './commerceAnalytics'
import {
  cleanSelectedOptions,
  findSelectedVariant,
  merchantOfferChoices,
  optionSelectionKey,
  optionValueState,
  productWithVariantSelection,
  savedOfferInitiallyCartable,
  savedSelectionChanged,
  selectedOptionsEqual,
  selectedOptionsWithPreference,
} from './variantSelection'

type DetailStatus = 'idle' | 'loading' | 'loaded' | 'not-found' | 'error'

export interface ProductPurchaseSelection {
  offerKey: string | null
  canAdd: boolean
  loading: boolean
  details: MerchantProductDetailsProfile | null
  selectedOptions: readonly ProductSelectedOptionProfile[]
  selectedVariantId: string | null
  selectedVariantTitle: string | null
  actionProduct: Product
  changedFromSaved: boolean
}

interface MerchantChoice {
  key: string
  label: string
  anchorOfferKey: string
  selectedOptions: ProductSelectedOptionProfile[]
  price: string
  available: boolean
}

function offerPrice(offer: CanonicalOfferProfile): string {
  return offer.price
    ? money(minorUnitsToMajor(offer.price.minorUnits, offer.price.currency), offer.price.currency)
    : 'Price unavailable'
}

function savedOfferPrice(product: Product, offerKey: string): string {
  const offer = product.offers.find((candidate) => candidate.offerKey?.trim() === offerKey)
  return offer && Number.isFinite(offer.price)
    ? money(offer.price, offer.priceCurrency)
    : 'Price unavailable'
}

function savedMerchantChoices(product: Product): MerchantChoice[] {
  const detailsOptions = cleanSelectedOptions(product.rehydratedDetails?.selectedOptions)
  const seen = new Set<string>()
  return product.offers.flatMap((offer, index) => {
    const offerKey = offer.offerKey?.trim()
    if (!offerKey) return []
    const merchantKey =
      offer.merchantId?.trim() ||
      offer.merchantDomain?.trim().toLocaleLowerCase() ||
      offer.merchant.trim().toLocaleLowerCase() ||
      `merchant-${index}`
    if (seen.has(merchantKey)) return []
    seen.add(merchantKey)
    return [
      {
        key: merchantKey,
        label: offer.merchant.trim() || 'Merchant',
        anchorOfferKey: offerKey,
        selectedOptions: detailsOptions,
        price: savedOfferPrice(product, offerKey),
        // An out-of-stock anchor can still expose other purchasable variants after rehydration.
        available: true,
      },
    ]
  })
}

function canonicalMerchantChoices(product: CanonicalProductProfile): MerchantChoice[] {
  return merchantOfferChoices(product).map((choice) => ({
    key: choice.key,
    label: choice.label,
    anchorOfferKey: choice.anchor.key,
    selectedOptions: cleanSelectedOptions(choice.anchor.selectedOptions),
    price: offerPrice(choice.anchor),
    // The anchor is server-issued identity, not proof that every merchant variant is unavailable.
    available: true,
  }))
}

function merchantDetails(selection: ProductVariantSelectionProfile): MerchantProductDetailsProfile {
  return { endpoint: null, ...selection.details }
}

function optionValues(option: MerchantProductDetailsProfile['options'][number]): string[] {
  const seen = new Set<string>()
  return [...(option.valueDetails?.map((value) => value.value) ?? []), ...(option.values ?? [])]
    .map((value) => value?.trim())
    .filter((value): value is string => Boolean(value))
    .filter((value) => {
      const key = value.toLocaleLowerCase()
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
}

function optionStateLabel(state: ReturnType<typeof optionValueState>): string {
  switch (state) {
    case 'available':
      return 'Available'
    case 'impossible':
      return 'Not offered'
    case 'sold-out':
      return 'Sold out'
    default:
      return 'Check stock'
  }
}

function compactOptionGroup(values: readonly string[]): boolean {
  return values.length <= 6 && values.every((value) => value.length <= 8)
}

function selectionValue(
  selections: readonly ProductSelectedOptionProfile[],
  optionName: string,
): string | null {
  return (
    selections.find(
      (selection) => optionSelectionKey(selection) === optionName.trim().toLowerCase(),
    )?.value ?? null
  )
}

function exactSelection(selection: ProductVariantSelectionProfile): boolean {
  return Boolean(
    selection.selectedOfferKey?.trim() &&
    selection.selectedOffer?.key === selection.selectedOfferKey,
  )
}

export function GroupedOfferSelector({
  product,
  researchQuery,
  userId,
  onSelectionChange,
}: Readonly<{
  product: Product
  researchQuery?: string | null
  userId?: string
  onSelectionChange?: (selection: ProductPurchaseSelection) => void
}>) {
  const canonicalKey = product.canonicalProduct?.key ?? null
  const savedBaselineOffer = product.offers.find((offer) => offer.offerKey?.trim())
  const savedBaselineOfferKey = savedBaselineOffer?.offerKey ?? null
  const savedBaselineCartable = !canonicalKey && savedOfferInitiallyCartable(savedBaselineOffer)
  const [canonicalDetail, setCanonicalDetail] = useState<CanonicalProductDetailProfile | null>(null)
  const [status, setStatus] = useState<DetailStatus>(canonicalKey ? 'loading' : 'idle')
  const [selectedMerchantKey, setSelectedMerchantKey] = useState<string | null>(null)
  const [selectedOptions, setSelectedOptions] = useState<ProductSelectedOptionProfile[]>([])
  const [details, setDetails] = useState<MerchantProductDetailsProfile | null>(
    product.rehydratedDetails ?? null,
  )
  const [acceptedDetails, setAcceptedDetails] = useState<MerchantProductDetailsProfile | null>(
    product.rehydratedDetails ?? null,
  )
  const [selectionResponse, setSelectionResponse] = useState<ProductVariantSelectionProfile | null>(
    null,
  )
  const [selectedOfferKey, setSelectedOfferKey] = useState<string | null>(
    canonicalKey ? null : savedBaselineOfferKey,
  )
  const [cartable, setCartable] = useState(savedBaselineCartable)
  const [selecting, setSelecting] = useState(Boolean(canonicalKey))
  const [selectionError, setSelectionError] = useState<string | null>(null)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const detailRequestRef = useRef(0)
  const selectionRequestRef = useRef(0)
  const selectionAbortRef = useRef<AbortController | null>(null)
  const viewedProductKeyRef = useRef<string | null>(null)
  const viewedOfferKeysRef = useRef(new Set<string>())

  const choices = useMemo(
    () =>
      canonicalDetail
        ? canonicalMerchantChoices(canonicalDetail.product)
        : canonicalKey
          ? []
          : savedMerchantChoices(product),
    [canonicalDetail, canonicalKey, product],
  )
  const selectedChoice =
    choices.find((choice) => choice.key === selectedMerchantKey) ?? choices[0] ?? null

  const resolveSelection = useCallback(
    async (
      choice: MerchantChoice,
      requested: readonly ProductSelectedOptionProfile[],
      currentCanonical: CanonicalProductProfile | null,
      preferredOptionName?: string,
    ) => {
      const requestId = selectionRequestRef.current + 1
      selectionRequestRef.current = requestId
      selectionAbortRef.current?.abort()
      const controller = new AbortController()
      selectionAbortRef.current = controller
      const cleanRequested = cleanSelectedOptions(requested)
      setSelectedOptions(cleanRequested)
      setSelectedOfferKey(null)
      setCartable(false)
      setSelecting(true)
      setSelectionError(null)
      try {
        const response = await selectProductVariant({
          anchorOfferKey: choice.anchorOfferKey,
          selectedOptions: cleanRequested.map((option) => ({
            name: option.name ?? '',
            value: option.value ?? '',
          })),
          preferredOptionName,
          signal: controller.signal,
          expectedUserId: userId,
        })
        if (controller.signal.aborted || selectionRequestRef.current !== requestId) return
        const nextDetails = merchantDetails(response)
        setDetails(nextDetails)
        const effective = cleanSelectedOptions(response.details.selectedOptions)
        const accepted = exactSelection(response) && selectedOptionsEqual(cleanRequested, effective)
        if (accepted) {
          setSelectedOptions(effective)
          setSelectedOfferKey(response.selectedOfferKey?.trim() ?? null)
          setCartable(response.cartable)
          setAcceptedDetails(nextDetails)
          setSelectionResponse(response)
          trackCommerceEvent('offer_selection', {
            canonicalProductKey: currentCanonical?.key ?? product.id,
            offerKey: response.selectedOfferKey ?? undefined,
            checkoutExperience: response.selectedOffer?.checkoutExperience ?? undefined,
            availability: response.selectedOffer?.availability.status ?? undefined,
          })
        } else {
          // Keep the user's requested tuple visible. Provider-relaxed or ambiguous selections must
          // never silently become the item added to cart or saved.
          setSelectedOptions(cleanRequested)
          setSelectedOfferKey(null)
          setCartable(false)
          setSelectionError('That exact combination is not available. Choose another option.')
        }
        setStatus('loaded')
      } catch (error: unknown) {
        if (controller.signal.aborted || selectionRequestRef.current !== requestId) return
        setSelectedOfferKey(null)
        setCartable(false)
        setStatus(error instanceof ApiError && error.status === 404 ? 'not-found' : 'loaded')
        setSelectionError(
          error instanceof ApiError && error.status === 404
            ? 'Current product choices changed. Try again to reload them.'
            : 'Current variant availability could not be loaded. Try again.',
        )
      } finally {
        if (!controller.signal.aborted && selectionRequestRef.current === requestId) {
          setSelecting(false)
        }
      }
    },
    [product.id, userId],
  )

  useEffect(() => {
    const requestId = detailRequestRef.current + 1
    detailRequestRef.current = requestId
    selectionRequestRef.current += 1
    selectionAbortRef.current?.abort()
    setCanonicalDetail(null)
    setSelectionResponse(null)
    setSelectionError(null)
    setDetails(product.rehydratedDetails ?? null)
    setAcceptedDetails(product.rehydratedDetails ?? null)
    setSelectedOptions(cleanSelectedOptions(product.rehydratedDetails?.selectedOptions))
    setSelectedOfferKey(canonicalKey ? null : savedBaselineOfferKey)
    setCartable(savedBaselineCartable)
    setSelecting(Boolean(canonicalKey))

    if (!canonicalKey) {
      const savedChoices = savedMerchantChoices(product)
      const choice = savedChoices[0] ?? null
      setSelectedMerchantKey(choice?.key ?? null)
      setStatus('loaded')
      return
    }

    const controller = new AbortController()
    setStatus('loading')
    loadCanonicalProductDetailWithRecovery({
      product,
      historicalQuery: researchQuery,
      selectedOfferKey: product.canonicalProduct?.recommendedOfferKey,
      signal: controller.signal,
      expectedUserId: userId,
    })
      .then((nextDetail) => {
        if (controller.signal.aborted || detailRequestRef.current !== requestId) return
        setCanonicalDetail(nextDetail)
        const nextChoices = canonicalMerchantChoices(nextDetail.product)
        const selected =
          nextChoices.find((choice) => choice.anchorOfferKey === nextDetail.selectedOfferKey) ??
          nextChoices.find((choice) => choice.anchorOfferKey === nextDetail.recommendedOfferKey) ??
          nextChoices[0]
        if (!selected) {
          setStatus('loaded')
          setSelecting(false)
          setSelectionError('No current merchant choices are available for this product.')
          return
        }
        setSelectedMerchantKey(selected.key)
        void resolveSelection(selected, selected.selectedOptions, nextDetail.product)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || detailRequestRef.current !== requestId) return
        setStatus(error instanceof ApiError && error.status === 404 ? 'not-found' : 'error')
        setSelecting(false)
      })
    return () => controller.abort()
  }, [
    canonicalKey,
    product,
    researchQuery,
    refreshVersion,
    resolveSelection,
    savedBaselineCartable,
    savedBaselineOfferKey,
    userId,
  ])

  useEffect(
    () => () => {
      selectionAbortRef.current?.abort()
    },
    [],
  )

  useEffect(() => {
    if (!canonicalDetail) return
    if (viewedProductKeyRef.current !== canonicalDetail.product.key) {
      viewedProductKeyRef.current = canonicalDetail.product.key
      viewedOfferKeysRef.current.clear()
      trackCommerceEvent('product_view', {
        canonicalProductKey: canonicalDetail.product.key,
        offerCount: canonicalDetail.product.offers.length,
      })
    }
    canonicalDetail.product.offers.forEach((offer, index) => {
      if (viewedOfferKeysRef.current.has(offer.key)) return
      viewedOfferKeysRef.current.add(offer.key)
      trackCommerceEvent('offer_view', {
        canonicalProductKey: canonicalDetail.product.key,
        offerKey: offer.key,
        offerRank: index + 1,
        offerCount: canonicalDetail.product.offers.length,
        checkoutExperience: offer.checkoutExperience,
        availability: offer.availability.status,
      })
    })
  }, [canonicalDetail])

  const acceptedSelectedOptions = useMemo(
    () => cleanSelectedOptions(acceptedDetails?.selectedOptions),
    [acceptedDetails],
  )
  const selectedVariant = useMemo(
    () =>
      findSelectedVariant(
        acceptedDetails?.variants ?? [],
        acceptedDetails?.selectedVariantId,
        acceptedSelectedOptions,
      ),
    [acceptedDetails, acceptedSelectedOptions],
  )
  const savedBaselineMerchantKey = useMemo(
    () => savedMerchantChoices(product)[0]?.key ?? null,
    [product],
  )
  const actionProduct = useMemo(
    () =>
      selectionResponse
        ? productWithVariantSelection(
            product,
            canonicalDetail?.product ?? null,
            selectionResponse,
            acceptedDetails,
          )
        : product,
    [acceptedDetails, canonicalDetail, product, selectionResponse],
  )
  const changedFromSaved = Boolean(
    !canonicalKey &&
    selectedOfferKey &&
    product.rehydratedDetails &&
    acceptedDetails &&
    savedSelectionChanged(
      product.rehydratedDetails,
      {
        selectedVariantId: selectedVariant?.variantId ?? acceptedDetails.selectedVariantId,
        selectedOptions: acceptedSelectedOptions,
      },
      savedBaselineMerchantKey,
      selectedMerchantKey,
    ),
  )

  useEffect(() => {
    onSelectionChange?.({
      offerKey: selectedOfferKey,
      canAdd: status === 'loaded' && cartable && Boolean(selectedOfferKey),
      loading: status === 'loading' || selecting,
      details: acceptedDetails,
      selectedOptions: acceptedSelectedOptions,
      selectedVariantId: selectedVariant?.variantId ?? acceptedDetails?.selectedVariantId ?? null,
      selectedVariantTitle: selectedVariant?.title ?? acceptedDetails?.selectedVariantTitle ?? null,
      actionProduct,
      changedFromSaved,
    })
  }, [
    actionProduct,
    acceptedDetails,
    acceptedSelectedOptions,
    cartable,
    changedFromSaved,
    onSelectionChange,
    selectedOfferKey,
    selectedVariant,
    selecting,
    status,
  ])

  const selectMerchant = (choice: MerchantChoice) => {
    if (choice.key === selectedMerchantKey || selecting) return
    setSelectedMerchantKey(choice.key)
    void resolveSelection(choice, choice.selectedOptions, canonicalDetail?.product ?? null)
  }

  const selectOption = (optionName: string, optionValue: string) => {
    if (!selectedChoice || selecting) return
    const order = details?.options.map((option) => option.name ?? '') ?? []
    const requested = selectedOptionsWithPreference(
      selectedOptions,
      { name: optionName, value: optionValue },
      order,
    )
    void resolveSelection(selectedChoice, requested, canonicalDetail?.product ?? null, optionName)
  }

  if (status === 'not-found') {
    return (
      <div className="mt-grouped-recovery" role="alert">
        <h3>Product choices could not be restored</h3>
        <p>Current merchant and variant choices are unavailable.</p>
        <button
          type="button"
          className="mt-act mt-act-ghost"
          onClick={() => setRefreshVersion((value) => value + 1)}
        >
          Try again
        </button>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="mt-grouped-recovery" role="alert">
        <h3>Product choices could not be loaded</h3>
        <p>No variant has been selected.</p>
        <button
          type="button"
          className="mt-act mt-act-ghost"
          onClick={() => setRefreshVersion((value) => value + 1)}
        >
          Try again
        </button>
      </div>
    )
  }

  return (
    <section
      className="mt-grouped-offers mt-grouped-offers-compact"
      aria-label="Product purchase options"
    >
      {status === 'loading' ? (
        <div className="mt-grouped-loading" role="status" aria-live="polite">
          <span className="mt-grouped-skeleton wide" />
          <span className="mt-grouped-skeleton" />
          <span>Loading current choices…</span>
        </div>
      ) : null}

      {choices.length > 0 ? (
        <div className="mt-product-choice-section">
          <div className="mt-merchant-choice-list" role="group" aria-label="Store">
            {choices.map((choice) => {
              const selected = choice.key === selectedMerchantKey
              return (
                <button
                  key={choice.key}
                  type="button"
                  className={`mt-merchant-choice ${selected ? 'selected' : ''}`}
                  aria-pressed={selected}
                  disabled={!choice.available || selecting}
                  onClick={() => selectMerchant(choice)}
                >
                  <span>{choice.label}</span>
                  <strong>{choice.price}</strong>
                </button>
              )
            })}
          </div>
        </div>
      ) : null}

      {details?.options.length ? (
        <div className="mt-product-choice-section">
          <div className="mt-product-option-groups">
            {details.options.map((option) => {
              const name = option.name?.trim()
              if (!name) return null
              const values = optionValues(option)
              return (
                <fieldset
                  className={`mt-product-option-group ${compactOptionGroup(values) ? 'compact' : 'wide'}`}
                  key={name}
                >
                  <legend className="mt-product-option-head">
                    <span className="mt-mono">{name}</span>
                    <span>{selectionValue(selectedOptions, name) ?? 'Choose one'}</span>
                  </legend>
                  <div className="mt-product-option-values">
                    {values.map((value) => {
                      const state = optionValueState(
                        option,
                        details.variants,
                        selectedOptions,
                        value,
                      )
                      const selected =
                        selectionValue(selectedOptions, name)?.trim().toLowerCase() ===
                        value.trim().toLowerCase()
                      const stateLabel = optionStateLabel(state)
                      return (
                        <button
                          className={`mt-product-option-chip ${state} ${selected ? 'selected' : ''}`}
                          key={`${name}-${value}`}
                          type="button"
                          title={state === 'unknown' ? stateLabel : undefined}
                          aria-label={`${name}: ${value}. ${stateLabel}.`}
                          aria-pressed={selected}
                          disabled={selecting || (state === 'impossible' && !selected)}
                          onClick={() => selectOption(name, value)}
                        >
                          <span>{value}</span>
                          <span className="mt-mono mt-product-option-chip-state">{stateLabel}</span>
                        </button>
                      )
                    })}
                  </div>
                </fieldset>
              )
            })}
          </div>
        </div>
      ) : null}

      <div className="mt-product-selection-status" aria-live="polite">
        {selecting ? (
          <span>Checking that exact combination…</span>
        ) : selectionError ? (
          <span className="error">{selectionError}</span>
        ) : selectedOfferKey && cartable ? (
          <span>
            {selectedVariant?.title?.trim() ||
              details?.selectedVariantTitle?.trim() ||
              'Selected item'}{' '}
            is ready to add.
          </span>
        ) : selectedOfferKey ? (
          <span className="error">
            That exact item is selected, but it is not currently available to add.
          </span>
        ) : (
          <span>Choose an available combination to continue.</span>
        )}
      </div>
    </section>
  )
}
