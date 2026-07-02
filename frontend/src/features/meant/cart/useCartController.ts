import {
  useEffect,
  useRef,
} from 'react'

import {
  createCart,
  updateCart,
  type CartProfile,
} from '../../../lib/apiClient'
import { DEFAULT_CART } from '../data'
import { useStoredState } from '../shared/storage'
import type {
  CartItem,
  Offer,
  Product,
  ProductId,
} from '../types'
import {
  cartMerchantKey,
  cartRebuildItems,
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

export function useCartController(products: readonly Product[]) {
  const [cart, setCart] = useStoredState<CartItem[]>('meant.cart', [...DEFAULT_CART])
  const [cartSnapshots, setCartSnapshots] = useStoredState<Record<string, MerchantCartSnapshot>>(
    'meant.cartSnapshots',
    {},
  )
  const cartRef = useRef<readonly CartItem[]>(cart)
  const cartSnapshotsRef = useRef<Record<string, MerchantCartSnapshot>>(cartSnapshots)

  useEffect(() => {
    cartRef.current = cart
  }, [cart])

  useEffect(() => {
    cartSnapshotsRef.current = cartSnapshots
  }, [cartSnapshots])

  const updateStoredCart = (updater: (current: CartItem[]) => CartItem[]) => {
    setCart((current) => {
      const next = updater(current)
      cartRef.current = next
      return next
    })
  }

  const updateStoredCartSnapshots = (
    updater: (current: Record<string, MerchantCartSnapshot>) => Record<string, MerchantCartSnapshot>,
  ) => {
    setCartSnapshots((current) => {
      const next = updater(current)
      cartSnapshotsRef.current = next
      return next
    })
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
    fallbackProductVariantId: string,
  ): { productVariantId: string; quantity: number }[] => {
    const addItems = cartRebuildItems(cartRef.current, merchantKey)
    return addItems.length > 0
      ? addItems
      : [{ productVariantId: fallbackProductVariantId, quantity: 1 }]
  }

  /**
   * Recovers from a stale (server-side-expired) cart: clears the dead cartId
   * from local state and rebuilds the merchant cart from the items still in the
   * local cart, returning the fresh snapshot so callers can re-apply their
   * intent (codes, delivery) against the new cartId. Returns null when there is
   * nothing left to rebuild (e.g. the merchant has no remaining cartable items).
   */
  const recreateMerchantCart = async (merchantKey: string): Promise<CartProfile | null> => {
    const merchantItem = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)
    const rebuildItems = cartRebuildItems(cartRef.current, merchantKey)
    clearMerchantCartState(merchantKey)
    if (rebuildItems.length === 0) {
      return null
    }
    const snapshot = await createCart({
      merchantId: merchantItem?.merchantId,
      merchantDomain: merchantItem?.merchantDomain,
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
      current.map((item) => cartMerchantKey(item) === merchantKey ? { ...item, ...patch } : item),
    )
  }

  const cartItemMatches = (item: CartItem, id: ProductId, merchant: string) =>
    item.id === id && item.merchant === merchant

  const addProductOfferToCart = async (product: Product, offer: Offer): Promise<boolean> => {
    const productVariantId = offer.productVariantId
    if (!productVariantId || !offerCartable(offer)) {
      return false
    }

    const merchantKey = cartMerchantKey(offer)
    const existingGroup = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)
    const existingItem = cartRef.current.find((item) =>
      item.id === product.id && cartMerchantKey(item) === merchantKey,
    )

    updateStoredCart((current) => {
      const existing = current.find((item) =>
        item.id === product.id && cartMerchantKey(item) === merchantKey,
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
      const currentGroup = cartRef.current.find((item) => cartMerchantKey(item) === merchantKey)
      let snapshot: CartProfile
      try {
        snapshot = currentGroup?.cartId
          ? await updateCart({
              cartId: currentGroup.cartId,
              addItems: [{ productVariantId, quantity: 1 }],
            })
          : await createCart({
              merchantId: offer.merchantId,
              merchantDomain: offer.merchantDomain,
              addItems: [{ productVariantId, quantity: 1 }],
            })
      } catch (error) {
        if (!currentGroup?.cartId || !isCartNotFoundError(error)) {
          throw error
        }
        clearMerchantCartState(merchantKey)
        snapshot = await createCart({
          merchantId: offer.merchantId,
          merchantDomain: offer.merchantDomain,
          addItems: cartAddItemsForMerchant(merchantKey, productVariantId),
        })
      }
      updateStoredCart((current) => mergeCartSnapshot(current, merchantKey, snapshot))
      storeCartSnapshot(merchantKey, offer.merchant, snapshot)
      return true
    } catch {
      updateStoredCart((current) => {
        if (!existingItem) {
          return current.filter((item) => !(item.id === product.id && cartMerchantKey(item) === merchantKey))
        }
        return current.map((item) =>
          item.id === product.id && cartMerchantKey(item) === merchantKey
            ? {
                ...item,
                qty: existingItem.qty,
                syncing: false,
                syncError: 'Could not add this item to the merchant cart.',
              }
            : item,
        )
      })
      return false
    }
  }

  const addToCart = (id: ProductId, merchant: string) => {
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
          item.id === id && item.merchant === merchant
            ? { ...item, qty: item.qty + 1 }
            : item,
        )
      }
      return [...current, { id, merchant, qty: 1 }]
    })
  }

  const removeFromCart = (id: ProductId, merchant: string) => {
    const item = cartRef.current.find((candidate) => cartItemMatches(candidate, id, merchant))
    if (!item) {
      return
    }
    const merchantKey = cartMerchantKey(item)
    updateStoredCart((current) => current.filter((candidate) => !cartItemMatches(candidate, id, merchant)))

    if (!item.cartId || (!item.cartLineId && !item.remoteCartLineId)) {
      return
    }

    void updateCart({
      cartId: item.cartId,
      removeCartLineIds: item.cartLineId ? [item.cartLineId] : undefined,
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
        updateStoredCart((current) => [
          ...current,
          {
            ...item,
            syncing: false,
            syncError: 'Could not remove this item from the merchant cart.',
          },
        ])
      })
  }

  const updateQty = (id: ProductId, merchant: string, qty: number) => {
    if (qty <= 0) {
      removeFromCart(id, merchant)
      return
    }

    const item = cartRef.current.find((candidate) => cartItemMatches(candidate, id, merchant))
    if (!item) {
      return
    }
    const merchantKey = cartMerchantKey(item)
    const shouldSync = Boolean(item.cartId && (item.cartLineId || item.remoteCartLineId))
    updateStoredCart((current) =>
      current.map((candidate) =>
        cartItemMatches(candidate, id, merchant)
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
            cartItemMatches(candidate, id, merchant)
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
    return Array.from(new Set(
      (snapshot?.appliedCodes ?? [])
        .filter((code) => code.type === type && code.code)
        .map((code) => code.code as string),
    ))
  }

  const applyCartCode = async (input: ApplyCartCodeInput): Promise<{ ok: boolean; message?: string }> => {
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

  const removeCartCode = async (input: RemoveCartCodeInput): Promise<{ ok: boolean; message?: string }> => {
    const codeToRemove = input.code.code
    if (!codeToRemove) {
      return { ok: false, message: 'The merchant did not return a removable code for this adjustment.' }
    }
    const snapshot = cartSnapshotsRef.current[input.merchantKey]
    const remainingCodes = appliedCodesForType(snapshot, input.code.type)
      .filter((code) => code !== codeToRemove)

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
        message: error instanceof Error ? error.message : 'The merchant could not remove this code.',
      }
    }
  }

  const updateDeliveryAddress = async (payload: DeliveryAddressPayload): Promise<boolean> => {
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
    addToCart,
    removeFromCart,
    updateQty,
    applyCartCode,
    removeCartCode,
    updateDeliveryAddress,
    updateDeliveryOption,
  }
}
