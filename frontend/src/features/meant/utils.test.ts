import { describe, expect, test } from 'bun:test'

import { cartDeliveryOptions, cartGroups } from './utils'
import type { CartDeliveryGroup, CartDeliveryOption, CartLine, Product } from './types'

const product: Product = {
  id: 'product-1',
  name: 'Test product',
  brand: 'Test',
  category: 'home',
  tone: 'neutral',
  match: 80,
  priceFrom: 20,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: '',
  pros: [],
  cons: [],
  review: {
    score: 4.5,
    count: 10,
    insight: '',
  },
  offers: [],
}

function option(handle: string, amount: string, selected: boolean): CartDeliveryOption {
  return {
    handle,
    title: handle,
    cost: {
      amount,
      currency: 'USD',
    },
    selected,
  }
}

function group(id: string, options: readonly CartDeliveryOption[], selected?: CartDeliveryOption): CartDeliveryGroup {
  return {
    id,
    deliveryOptions: options,
    selectedDeliveryOption: selected ?? null,
  }
}

function line(deliveryGroups: readonly CartDeliveryGroup[]): CartLine {
  return {
    id: 'product-1',
    merchant: 'Review Merchant',
    qty: 1,
    product,
    price: 20,
    delivery: 'standard',
    deliveryGroups,
  }
}

describe('cart delivery groups', () => {
  test('requires every shipment with options to have a selected delivery option', () => {
    const selected = option('standard', '5.00', true)
    const unselected = option('express', '8.00', false)

    const [cartGroup] = cartGroups([
      line([
        group('shipment-1', [selected], selected),
        group('shipment-2', [unselected]),
      ]),
    ], false)

    expect(cartGroup.hasDeliveryOptions).toBe(true)
    expect(cartGroup.hasSelectedDelivery).toBe(false)
    expect(cartGroup.deliveryRaw).toBe(4.99)
  })

  test('uses selected delivery cost only after all selectable shipments are selected', () => {
    const standard = option('standard', '5.00', true)
    const economy = option('economy', '3.00', true)

    const [cartGroup] = cartGroups([
      line([
        group('shipment-1', [standard], standard),
        group('shipment-2', [economy], economy),
      ]),
    ], false)

    expect(cartGroup.hasSelectedDelivery).toBe(true)
    expect(cartGroup.deliveryRaw).toBe(8)
    expect(cartGroup.total).toBe(28)
  })

  test('does not require selection for shipment groups without delivery options', () => {
    const standard = option('standard', '5.00', true)

    const [cartGroup] = cartGroups([
      line([
        group('shipment-1', []),
        group('shipment-2', [standard], standard),
      ]),
    ], false)

    expect(cartGroup.hasSelectedDelivery).toBe(true)
    expect(cartGroup.deliveryRaw).toBe(5)
  })

  test('filters null delivery options at the cart boundary', () => {
    const standard = option('standard', '5.00', true)
    const deliveryGroup = {
      id: 'shipment-1',
      deliveryOptions: [null, standard],
    } as unknown as CartDeliveryGroup

    expect(cartDeliveryOptions(deliveryGroup)).toEqual([standard])
  })
})
