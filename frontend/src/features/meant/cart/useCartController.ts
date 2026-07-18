import { type Dispatch, type SetStateAction, useRef } from 'react'

import {
  bindSelectedOfferToCart as bindSelectedOfferToRemoteCart,
  createCart as createRemoteCart,
  updateCart as updateRemoteCart,
  type CartProfile,
} from '../../../lib/apiClient'
import { accountSessionStorageKey } from '../shared/accountStorage'
import { useSessionStoredState } from '../shared/storage'
import type { CartItem, Offer, Product, ProductId } from '../types'
import {
  cartItemIdentity,
  cartMerchantKey,
  cartRebuildItems,
  canonicalMerchantScopeKey,
  isCartNotFoundError,
  mergeCartSnapshot,
} from '../utils'
import type {
  AppliedCartCodeType,
  ApplyCartCodeInput,
  DeliveryAddressPayload,
  DeliveryOptionPayload,
  MerchantCartSnapshot,
  RemoveCartCodeInput,
} from './types'
import {
  cartSnapshotFromProfile,
  deliveryAddressArguments,
  offerCartable,
  selectedDeliveryOptionsForCart,
} from './utils'
import {
  bindSelectedOfferWithStaleCartRecovery,
  cartSnapshotExactOfferLine,
  cartSnapshotHasExactOfferLine,
  confirmedCartIdForMerchant,
  confirmedCartIdForOffer,
  mergeConfirmedCartSnapshot,
  mergeInitialSelectedOfferSnapshot,
  settleUnconfirmedSelectedOfferAddition,
} from './selectedOfferCartBinding'

function resolveSetStateAction<T>(action: SetStateAction<T>, current: T): T {
  return typeof action === 'function' ? (action as (previous: T) => T)(current) : action
}

export function useCartController(products: readonly Product[], ownerId: string | undefined) {
  const [cart, setStoredCart] = useSessionStoredState<CartItem[]>(
    accountSessionStorageKey('meant.cart', ownerId),
    [],
  )
  const [cartSnapshots, setStoredCartSnapshots] = useSessionStoredState<
    Record<string, MerchantCartSnapshot>
  >(accountSessionStorageKey('meant.cartSnapshots', ownerId), {})
  const activeOwnerIdRef = useRef(ownerId)
  activeOwnerIdRef.current = ownerId
  const cartRef = useRef<CartItem[]>(cart)
  const cartSnapshotsRef = useRef<Record<string, MerchantCartSnapshot>>(cartSnapshots)
  cartRef.current = cart
  cartSnapshotsRef.current = cartSnapshots

  const isAccountCurrent = () => Boolean(ownerId && activeOwnerIdRef.current === ownerId)
  const requireCurrentOwner = (): string => {
    if (!ownerId || activeOwnerIdRef.current !== ownerId) {
      throw new Error('Account changed during cart operation')
    }
    return ownerId
  }

  const createCart = async (
    input: Omit<Parameters<typeof createRemoteCart>[0], 'expectedUserId'>,
  ): Promise<CartProfile> => {
    const expectedUserId = requireCurrentOwner()
    const snapshot = await createRemoteCart({ ...input, expectedUserId })
    requireCurrentOwner()
    return snapshot
  }

  const updateCart = async (
    input: Omit<Parameters<typeof updateRemoteCart>[0], 'expectedUserId'>,
  ): Promise<CartProfile> => {
    const expectedUserId = requireCurrentOwner()
    const snapshot = await updateRemoteCart({ ...input, expectedUserId })
    requireCurrentOwner()
    return snapshot
  }

  const bindSelectedOfferToCart = async (
    input: Omit<Parameters<typeof bindSelectedOfferToRemoteCart>[0], 'expectedUserId'>,
  ): Promise<CartProfile> => {
    const expectedUserId = requireCurrentOwner()
    const snapshot = await bindSelectedOfferToRemoteCart({ ...input, expectedUserId })
    requireCurrentOwner()
    return snapshot
  }

  const setCart: Dispatch<SetStateAction<CartItem[]>> = (action) => {
    if (!isAccountCurrent()) {
      return
    }
    const next = resolveSetStateAction(action, cartRef.current)
    cartRef.current = next
    setStoredCart(next)
  }

  const setCartSnapshots: Dispatch<SetStateAction<Record<string, MerchantCartSnapshot>>> = (
    action,
  ) => {
    if (!isAccountCurrent()) {
      return
    }
    const next = resolveSetStateAction(action, cartSnapshotsRef.current)
    cartSnapshotsRef.current = next
    setStoredCartSnapshots(next)
  }

  const updateStoredCart = (updater: (current: CartItem[]) => CartItem[]) => {
    setCart(updater)
  }

  const updateStoredCartSnapshots = (
    updater: (
      current: Record<string, MerchantCartSnapshot>,
    ) => Record<string, MerchantCartSnapshot>,
  ) => {
    setCartSnapshots(updater)
  }

  const storeCartSnapshot = (merchantKey: string, merchant: string, snapshot: CartProfile) => {
    updateStoredCartSnapshots((current) => ({
      ...current,
      [merchantKey]: cartSnapshotFromProfile(snapshot, merchantKey, merchant),
    }))
  }

  const clearMerchantCartState = (merchantKey: string) => {
    updateStoredCartSnapshots((current) => {
      if (!current[merchantKey]) {
        return current
      }
      const next = { ...current }
      delete next[merchantKey]
      return next
    })
    updateStoredCart((current) =>
      current.map((item) =>
        cartMerchantKey(item) === merchantKey
          ? {
              ...item,
              cartId: null,
              remoteCartId: null,
              checkoutUrl: null,
              continueUrl: null,
              cartLineId: null,
              remoteCartLineId: null,
              cartTotalAmount: null,
              cartSubtotalAmount: null,
              cartCurrency: null,
              deliveryGroups: [],
              syncing: false,
              syncError: null,
            }
          : item,
      ),
    )
  }

  const cartAddItemsForMerchant = (
    merchantKey: string,
    fallbackOfferKey: string,
  ): { offerKey: string; quantity: number }[] => {
    if (!isAccountCurrent()) {
      return []
    }
    const addItems = cartRebuildItems(cartRef.current, merchantKey)
    return addItems.length > 0 ? addItems : [{ offerKey: fallbackOfferKey, quantity: 1 }]
  }

  /**
   * Recovers from a stale (server-side-expired) cart: clears the dead cartId
   * from local state and rebuilds the merchant cart from the items still in the
   * local cart, returning the fresh snapshot so callers can re-apply their
   * intent (codes, delivery) against the new cartId. Returns null when there is
   * nothing left to rebuild (e.g. the merchant has no remaining cartable items).
   */
  const recreateMerchantCart = async (merchantKey: string): Promise<CartProfile | null> => {
    if (!isAccountCurrent()) {
      return null
    }
    const merchantItem = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)
    const rebuildItems = cartRebuildItems(cartRef.current, merchantKey)
    clearMerchantCartState(merchantKey)
    if (rebuildItems.length === 0) {
      return null
    }
    const snapshot = await createCart({
      addItems: rebuildItems,
    })
    updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
    storeCartSnapshot(merchantKey, merchantItem?.merchant ?? merchantKey, snapshot)
    return snapshot
  }

  const updateMerchantCartItems = (
    merchantKey: string,
    patch: Pick<CartItem, 'syncing' | 'syncError'>,
  ) => {
    updateStoredCart((current) =>
      current.map((item) => (cartMerchantKey(item) === merchantKey ? { ...item, ...patch } : item)),
    )
  }

  const cartItemMatches = (item: CartItem, id: ProductId, merchant: string, identity?: string) =>
    identity ? cartItemIdentity(item) === identity : item.id === id && item.merchant === merchant

  const addProductOfferToCart = async (product: Product, offer: Offer): Promise<boolean> => {
    if (!isAccountCurrent()) {
      return false
    }
    const productVariantId = offer.productVariantId
    const offerKey = offer.offerKey?.trim()
    if (!offerKey || !productVariantId || !offerCartable(offer)) {
      return false
    }

    const merchantKey = cartMerchantKey(offer)
    const existingGroup = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)

    updateStoredCart((current) => {
      const existing = current.find(
        (item) => item.id === product.id && cartMerchantKey(item) === merchantKey,
      )
      if (existing) {
        return current.map((item) =>
          item.id === product.id && cartMerchantKey(item) === merchantKey
            ? {
                ...item,
                merchant: offer.merchant,
                merchantId: offer.merchantId ?? item.merchantId,
                merchantDomain: offer.merchantDomain ?? item.merchantDomain,
                productVariantId,
                offerKey,
                variantTitle: offer.variantTitle ?? item.variantTitle,
                cartId: existingGroup?.cartId ?? item.cartId,
                remoteCartId: existingGroup?.remoteCartId ?? item.remoteCartId,
                checkoutUrl: existingGroup?.checkoutUrl ?? item.checkoutUrl,
                continueUrl: existingGroup?.continueUrl ?? item.continueUrl,
                qty: item.qty + 1,
                syncing: true,
                syncError: null,
              }
            : item,
        )
      }
      return [
        ...current,
        {
          id: product.id,
          merchant: offer.merchant,
          merchantId: offer.merchantId,
          merchantDomain: offer.merchantDomain,
          productVariantId,
          offerKey,
          variantTitle: offer.variantTitle,
          cartId: existingGroup?.cartId,
          remoteCartId: existingGroup?.remoteCartId,
          checkoutUrl: existingGroup?.checkoutUrl,
          continueUrl: existingGroup?.continueUrl,
          qty: 1,
          syncing: true,
          syncError: null,
        },
      ]
    })

    try {
      let snapshot: CartProfile
      try {
        snapshot = existingGroup?.cartId
          ? await updateCart({
              cartId: existingGroup.cartId,
              addItems: [{ offerKey, quantity: 1 }],
            })
          : await createCart({
              addItems: [{ offerKey, quantity: 1 }],
            })
      } catch (error) {
        if (!existingGroup?.cartId || !isCartNotFoundError(error)) {
          throw error
        }
        clearMerchantCartState(merchantKey)
        snapshot = await createCart({
          addItems: cartAddItemsForMerchant(merchantKey, offerKey),
        })
      }
      updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
      storeCartSnapshot(merchantKey, offer.merchant, snapshot)
      return true
    } catch {
      updateStoredCart((current) =>
        current.flatMap((item) => {
          if (item.id !== product.id || cartMerchantKey(item) !== merchantKey) {
            return [item]
          }

          const qty = item.qty - 1
          if (qty <= 0) {
            return []
          }

          return [
            {
              ...item,
              qty,
              syncing: false,
              syncError: 'Could not add this item to the merchant cart.',
            },
          ]
        }),
      )
      return false
    }
  }

  const addSelectedOfferToCart = async (product: Product, offerKey: string): Promise<boolean> => {
    if (!isAccountCurrent()) {
      return false
    }
    const exactOfferKey = offerKey.trim()
    const selectedOffer = product.canonicalProduct?.offers.find(
      (offer) => offer.key === exactOfferKey,
    )
    const selectedDisplayOffer = product.offers.find(
      (offer) => offer.offerKey?.trim() === exactOfferKey,
    )
    if (!exactOfferKey) {
      return false
    }
    const priorExactQuantity =
      cartRef.current.find((item) => item.id === product.id && item.offerKey === exactOfferKey)
        ?.qty ?? 0
    const expectedExactQuantity =
      Number.isInteger(priorExactQuantity) && priorExactQuantity > 0 ? priorExactQuantity + 1 : 1

    const merchant =
      selectedOffer?.merchantName?.trim() ||
      selectedDisplayOffer?.merchant.trim() ||
      'Selected merchant'
    const sourceMerchantIntegrationId =
      selectedDisplayOffer?.merchantIntegrationId ??
      selectedOffer?.provenance.find((item) => item.localRouting?.merchantIntegrationId)
        ?.localRouting?.merchantIntegrationId ??
      selectedOffer?.identity.merchantScope.merchantIntegrationFallbackId
    const externalMerchantId =
      selectedDisplayOffer?.externalMerchantId ??
      selectedOffer?.identity.merchantScope.externalMerchantIdentity?.value ??
      selectedOffer?.provenance.find((item) => item.externalMerchantReference)
        ?.externalMerchantReference?.value
    const merchantDomain =
      selectedOffer?.provenance.find((item) => item.externalMerchantDomain)
        ?.externalMerchantDomain ?? selectedDisplayOffer?.merchantDomain
    const provider = selectedDisplayOffer?.provider ?? selectedOffer?.identity.provider
    const merchantScopeKey =
      selectedDisplayOffer?.merchantScopeKey ??
      (selectedOffer ? canonicalMerchantScopeKey(selectedOffer) : null)
    const confirmedCartId =
      confirmedCartIdForOffer(cartRef.current, exactOfferKey) ??
      confirmedCartIdForMerchant(cartRef.current, {
        merchantScopeKey,
        merchantIntegrationId: sourceMerchantIntegrationId,
        externalMerchantId,
        merchantDomain,
        provider,
      })
    const existingCartItem = confirmedCartId
      ? cartRef.current.find((item) => item.cartId === confirmedCartId)
      : undefined
    const existingMerchantScopeKey =
      existingCartItem?.merchantScopeKey?.trim() === existingCartItem?.routingScopeKey?.trim()
        ? null
        : existingCartItem?.merchantScopeKey?.trim()
    const resolvedMerchantScopeKey = merchantScopeKey ?? existingMerchantScopeKey
    const merchantKey = resolvedMerchantScopeKey
      ? cartMerchantKey({ merchant, merchantDomain, merchantScopeKey: resolvedMerchantScopeKey })
      : existingCartItem
        ? cartMerchantKey(existingCartItem)
        : cartMerchantKey({ merchant, merchantDomain })
    updateStoredCart((current) => {
      const merchantScopedCurrent =
        confirmedCartId && resolvedMerchantScopeKey
          ? current.map((item) =>
              item.cartId === confirmedCartId
                ? { ...item, merchantScopeKey: resolvedMerchantScopeKey }
                : item,
            )
          : current
      const existing = merchantScopedCurrent.find(
        (item) => item.id === product.id && item.offerKey === exactOfferKey,
      )
      if (existing) {
        return merchantScopedCurrent.map((item) =>
          item.id === product.id && item.offerKey === exactOfferKey
            ? {
                ...item,
                qty: item.qty + 1,
                syncing: true,
                syncError: null,
              }
            : item,
        )
      }
      return [
        ...merchantScopedCurrent,
        {
          id: product.id,
          merchant,
          merchantId: selectedDisplayOffer?.merchantId ?? existingCartItem?.merchantId,
          merchantDomain: merchantDomain ?? existingCartItem?.merchantDomain,
          provider: provider ?? existingCartItem?.provider,
          merchantIntegrationId:
            existingCartItem?.merchantIntegrationId ?? sourceMerchantIntegrationId,
          externalMerchantId: externalMerchantId ?? existingCartItem?.externalMerchantId,
          routingScopeKey: existingCartItem?.routingScopeKey,
          merchantScopeKey: resolvedMerchantScopeKey,
          productVariantId: selectedDisplayOffer?.productVariantId,
          offerKey: exactOfferKey,
          variantTitle: selectedDisplayOffer?.variantTitle,
          unitPriceAmount:
            selectedDisplayOffer && Number.isFinite(selectedDisplayOffer.price)
              ? String(selectedDisplayOffer.price)
              : undefined,
          orderCurrency: selectedDisplayOffer?.priceCurrency,
          cartId: confirmedCartId,
          remoteCartId: existingCartItem?.remoteCartId,
          checkoutUrl: existingCartItem?.checkoutUrl,
          continueUrl: existingCartItem?.continueUrl,
          qty: 1,
          syncing: true,
          syncError: null,
        },
      ]
    })

    try {
      const { snapshot, rebuilt } = await bindSelectedOfferWithStaleCartRecovery(
        confirmedCartId,
        () =>
          bindSelectedOfferToCart({
            offerKey: exactOfferKey,
            quantity: 1,
            cartId: confirmedCartId,
          }),
        async () => {
          const addItems = cartAddItemsForMerchant(merchantKey, exactOfferKey)
          clearMerchantCartState(merchantKey)
          return createCart({ addItems })
        },
      )
      const returnedExactLine = cartSnapshotExactOfferLine(snapshot, exactOfferKey)
      const exactLineConfirmed = cartSnapshotHasExactOfferLine(
        snapshot,
        exactOfferKey,
        expectedExactQuantity,
      )
      const reconciliationSnapshot = returnedExactLine
        ? snapshot
        : {
            ...snapshot,
            lines: snapshot.lines?.filter((line) => line.offerKey?.trim() !== exactOfferKey) ?? [],
          }
      const serverMerchant = snapshot.merchantDomain?.trim() || merchant
      updateStoredCart((current) => {
        const merged = rebuilt
          ? mergeCartSnapshot(current, merchantKey, reconciliationSnapshot)
          : confirmedCartId
            ? mergeConfirmedCartSnapshot(current, confirmedCartId, reconciliationSnapshot)
            : mergeInitialSelectedOfferSnapshot(
                current,
                product.id,
                exactOfferKey,
                reconciliationSnapshot,
              )
        const named = merged.map((item) =>
          item.id === product.id && item.offerKey === exactOfferKey
            ? {
                ...item,
                merchant: serverMerchant,
                merchantScopeKey: item.merchantScopeKey ?? resolvedMerchantScopeKey ?? undefined,
              }
            : item,
        )
        if (exactLineConfirmed) return named
        return settleUnconfirmedSelectedOfferAddition(
          named,
          product.id,
          exactOfferKey,
          Boolean(returnedExactLine),
        )
      })
      const serverMerchantKey = cartMerchantKey({
        merchant: serverMerchant,
        merchantId: snapshot.merchantId,
        merchantDomain: snapshot.merchantDomain,
        merchantScopeKey: resolvedMerchantScopeKey,
      })
      storeCartSnapshot(serverMerchantKey, serverMerchant, snapshot)
      return exactLineConfirmed
    } catch (error) {
      updateStoredCart((current) =>
        settleUnconfirmedSelectedOfferAddition(current, product.id, exactOfferKey, false),
      )
      throw error
    }
  }

  const addToCart = (id: ProductId, merchant: string) => {
    if (!isAccountCurrent()) {
      return
    }
    const product = products.find((candidate) => candidate.id === id)
    const offer = product?.offers.find((candidate) => candidate.merchant === merchant)
    if (product && offer && offerCartable(offer)) {
      void addProductOfferToCart(product, offer)
      return
    }

    updateStoredCart((current) => {
      const existing = current.find((item) => item.id === id && item.merchant === merchant)
      if (existing) {
        return current.map((item) =>
          item.id === id && item.merchant === merchant ? { ...item, qty: item.qty + 1 } : item,
        )
      }
      return [...current, { id, merchant, qty: 1 }]
    })
  }

  const removeFromCart = (id: ProductId, merchant: string, identity?: string) => {
    if (!isAccountCurrent()) {
      return
    }
    const item = cartRef.current.find((candidate) =>
      cartItemMatches(candidate, id, merchant, identity),
    )
    if (!item) {
      return
    }
    const merchantKey = cartMerchantKey(item)
    updateStoredCart((current) =>
      current.filter((candidate) => !cartItemMatches(candidate, id, merchant, identity)),
    )

    if (!item.cartId || (!item.cartLineId && !item.remoteCartLineId)) {
      return
    }

    void updateCart({
      cartId: item.cartId,
      removeCartLineIds: item.remoteCartLineId
        ? undefined
        : item.cartLineId
          ? [item.cartLineId]
          : undefined,
      removeRemoteCartLineIds: item.remoteCartLineId ? [item.remoteCartLineId] : undefined,
    })
      .then((snapshot) => {
        updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
        storeCartSnapshot(merchantKey, item.merchant, snapshot)
      })
      .catch((error) => {
        if (isCartNotFoundError(error)) {
          clearMerchantCartState(merchantKey)
          return
        }
        updateStoredCart((current) => {
          const exists = current.some((candidate) =>
            cartItemMatches(candidate, id, merchant, identity),
          )
          if (exists) {
            return current.map((candidate) =>
              cartItemMatches(candidate, id, merchant, identity)
                ? { ...candidate, syncError: 'Could not remove this item from the merchant cart.' }
                : candidate,
            )
          }
          return [
            ...current,
            {
              ...item,
              syncing: false,
              syncError: 'Could not remove this item from the merchant cart.',
            },
          ]
        })
      })
  }

  const updateQty = (id: ProductId, merchant: string, qty: number, identity?: string) => {
    if (!isAccountCurrent()) {
      return
    }
    if (qty <= 0) {
      removeFromCart(id, merchant, identity)
      return
    }

    const item = cartRef.current.find((candidate) =>
      cartItemMatches(candidate, id, merchant, identity),
    )
    if (!item) {
      return
    }
    const merchantKey = cartMerchantKey(item)
    const shouldSync = Boolean(item.cartId && (item.cartLineId || item.remoteCartLineId))
    updateStoredCart((current) =>
      current.map((candidate) =>
        cartItemMatches(candidate, id, merchant, identity)
          ? { ...candidate, qty, syncing: shouldSync, syncError: null }
          : candidate,
      ),
    )

    if (!shouldSync || !item.cartId) {
      return
    }

    void updateCart({
      cartId: item.cartId,
      updateItems: [
        {
          cartLineId: item.cartLineId,
          remoteCartLineId: item.remoteCartLineId,
          quantity: qty,
        },
      ],
    })
      .then((snapshot) => {
        updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
        storeCartSnapshot(merchantKey, item.merchant, snapshot)
      })
      .catch(async (error) => {
        if (isCartNotFoundError(error)) {
          // The local qty is already applied, so rebuilding the cart from
          // current items carries the new quantity onto the fresh cart.
          await recreateMerchantCart(merchantKey).catch(() => undefined)
          return
        }
        updateStoredCart((current) =>
          current.map((candidate) =>
            cartItemMatches(candidate, id, merchant, identity) && candidate.qty === qty
              ? {
                  ...candidate,
                  qty: item.qty,
                  syncing: false,
                  syncError: 'Could not update this item in the merchant cart.',
                }
              : candidate,
          ),
        )
      })
  }

  const appliedCodesForType = (
    snapshot: MerchantCartSnapshot | undefined,
    type: AppliedCartCodeType,
  ): string[] => {
    return Array.from(
      new Set(
        (snapshot?.appliedCodes ?? [])
          .filter((code) => code.type === type && code.code)
          .map((code) => code.code as string),
      ),
    )
  }

  const applyCartCode = async (
    input: ApplyCartCodeInput,
  ): Promise<{ ok: boolean; message?: string }> => {
    if (!isAccountCurrent()) {
      return { ok: false, message: 'Account changed during cart operation.' }
    }
    const code = input.code.trim()
    if (!code) {
      return { ok: false, message: 'Enter a code first.' }
    }

    const snapshot = cartSnapshotsRef.current[input.merchantKey]
    const existingCodes = appliedCodesForType(snapshot, input.type)
    const nextCodes = Array.from(new Set([...existingCodes, code]))

    try {
      let cartId = input.cartId
      try {
        const updated = await updateCart({
          cartId,
          discountCodes: input.type === 'DISCOUNT' ? nextCodes : undefined,
          giftCardCodes: input.type === 'GIFT_CARD' ? nextCodes : undefined,
        })
        updateStoredCart((current) => mergeCartSnapshot(current, input.merchantKey, updated))
        storeCartSnapshot(input.merchantKey, input.merchant, updated)
        return { ok: true }
      } catch (error) {
        if (!isCartNotFoundError(error)) {
          throw error
        }
        const rebuilt = await recreateMerchantCart(input.merchantKey)
        if (!rebuilt?.cartId) {
          throw error
        }
        cartId = rebuilt.cartId
      }
      const reapplied = await updateCart({
        cartId,
        discountCodes: input.type === 'DISCOUNT' ? nextCodes : undefined,
        giftCardCodes: input.type === 'GIFT_CARD' ? nextCodes : undefined,
      })
      updateStoredCart((current) => mergeCartSnapshot(current, input.merchantKey, reapplied))
      storeCartSnapshot(input.merchantKey, input.merchant, reapplied)
      return { ok: true }
    } catch (error) {
      return {
        ok: false,
        message: error instanceof Error ? error.message : 'The merchant did not accept this code.',
      }
    }
  }

  const removeCartCode = async (
    input: RemoveCartCodeInput,
  ): Promise<{ ok: boolean; message?: string }> => {
    if (!isAccountCurrent()) {
      return { ok: false, message: 'Account changed during cart operation.' }
    }
    const codeToRemove = input.code.code
    if (!codeToRemove) {
      return {
        ok: false,
        message: 'The merchant did not return a removable code for this adjustment.',
      }
    }
    const snapshot = cartSnapshotsRef.current[input.merchantKey]
    const remainingCodes = appliedCodesForType(snapshot, input.code.type).filter(
      (code) => code !== codeToRemove,
    )

    try {
      let cartId = input.cartId
      try {
        const updated = await updateCart({
          cartId,
          discountCodes: input.code.type === 'DISCOUNT' ? remainingCodes : undefined,
          giftCardCodes: input.code.type === 'GIFT_CARD' ? remainingCodes : undefined,
        })
        updateStoredCart((current) => mergeCartSnapshot(current, input.merchantKey, updated))
        storeCartSnapshot(input.merchantKey, input.merchant, updated)
        return { ok: true }
      } catch (error) {
        if (!isCartNotFoundError(error)) {
          throw error
        }
        const rebuilt = await recreateMerchantCart(input.merchantKey)
        // A rebuilt cart starts with no codes, so the removal is already
        // satisfied; re-apply the codes that should remain on the fresh cart.
        if (!rebuilt?.cartId) {
          return { ok: true }
        }
        cartId = rebuilt.cartId
      }
      const reapplied = await updateCart({
        cartId,
        discountCodes: input.code.type === 'DISCOUNT' ? remainingCodes : undefined,
        giftCardCodes: input.code.type === 'GIFT_CARD' ? remainingCodes : undefined,
      })
      updateStoredCart((current) => mergeCartSnapshot(current, input.merchantKey, reapplied))
      storeCartSnapshot(input.merchantKey, input.merchant, reapplied)
      return { ok: true }
    } catch (error) {
      return {
        ok: false,
        message:
          error instanceof Error ? error.message : 'The merchant could not remove this code.',
      }
    }
  }

  const updateDeliveryAddress = async (payload: DeliveryAddressPayload): Promise<boolean> => {
    if (!isAccountCurrent()) {
      return false
    }
    updateMerchantCartItems(payload.merchantKey, { syncing: true, syncError: null })
    try {
      let cartId = payload.cartId
      try {
        const snapshot = await updateCart({
          cartId,
          deliveryAddressesToAdd: [deliveryAddressArguments(payload)],
        })
        updateStoredCart((current) => mergeCartSnapshot(current, payload.merchantKey, snapshot))
        storeCartSnapshot(payload.merchantKey, payload.merchant, snapshot)
        return true
      } catch (error) {
        if (!isCartNotFoundError(error)) {
          throw error
        }
        const rebuilt = await recreateMerchantCart(payload.merchantKey)
        if (!rebuilt?.cartId) {
          throw error
        }
        cartId = rebuilt.cartId
      }
      const snapshot = await updateCart({
        cartId,
        deliveryAddressesToAdd: [deliveryAddressArguments(payload)],
      })
      updateStoredCart((current) => mergeCartSnapshot(current, payload.merchantKey, snapshot))
      storeCartSnapshot(payload.merchantKey, payload.merchant, snapshot)
      return true
    } catch {
      updateMerchantCartItems(payload.merchantKey, {
        syncing: false,
        syncError: 'Could not load delivery options for this merchant.',
      })
      return false
    }
  }

  const updateDeliveryOption = async (payload: DeliveryOptionPayload): Promise<boolean> => {
    if (!isAccountCurrent()) {
      return false
    }
    const selectedDeliveryOptions = selectedDeliveryOptionsForCart(
      cartRef.current,
      payload.merchantKey,
      payload.group,
      payload.option,
    )
    if (selectedDeliveryOptions.length === 0) {
      updateMerchantCartItems(payload.merchantKey, {
        syncing: false,
        syncError: 'This merchant did not return a selectable delivery handle.',
      })
      return false
    }

    updateMerchantCartItems(payload.merchantKey, { syncing: true, syncError: null })
    try {
      const snapshot = await updateCart({
        cartId: payload.cartId,
        selectedDeliveryOptions,
      })
      updateStoredCart((current) => mergeCartSnapshot(current, payload.merchantKey, snapshot))
      storeCartSnapshot(payload.merchantKey, payload.merchant, snapshot)
      return true
    } catch (error) {
      if (isCartNotFoundError(error)) {
        // A rebuilt cart has fresh delivery groups, so the old delivery handle
        // is no longer valid — recover the cart and ask the user to re-select
        // rather than replaying a stale handle.
        await recreateMerchantCart(payload.merchantKey).catch(() => undefined)
        updateMerchantCartItems(payload.merchantKey, {
          syncing: false,
          syncError: 'Cart was refreshed — please choose a delivery option again.',
        })
        return false
      }
      updateMerchantCartItems(payload.merchantKey, {
        syncing: false,
        syncError: 'Could not update the delivery option.',
      })
      return false
    }
  }

  return {
    cart,
    cartSnapshots,
    setCart,
    updateStoredCart,
    addProductOfferToCart,
    addSelectedOfferToCart,
    addToCart,
    removeFromCart,
    updateQty,
    applyCartCode,
    removeCartCode,
    updateDeliveryAddress,
    updateDeliveryOption,
  }
}
