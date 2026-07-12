import { describe, expect, test } from 'bun:test'

import {
  CHECKOUT_KIT_ECP_VERSION,
  createCheckoutKitHandle,
  type CheckoutKitElementPort,
  type CheckoutKitRuntime,
} from './checkoutKitAdapter'

class FakeCheckoutElement implements CheckoutKitElementPort {
  src = ''
  target = ''
  debug = false
  style = { display: '' }
  openCount = 0
  focusCount = 0
  closeCount = 0
  removed = false
  private readonly listeners = new Map<string, Set<EventListener>>()

  open() {
    this.openCount += 1
  }

  focus() {
    this.focusCount += 1
  }

  close() {
    this.closeCount += 1
  }

  addEventListener(type: string, listener: EventListener) {
    const listeners = this.listeners.get(type) ?? new Set<EventListener>()
    listeners.add(listener)
    this.listeners.set(type, listeners)
  }

  removeEventListener(type: string, listener: EventListener) {
    this.listeners.get(type)?.delete(listener)
  }

  remove() {
    this.removed = true
  }

  emit(type: string) {
    this.listeners.get(type)?.forEach((listener) => {
      listener({ type } as Event)
    })
  }
}

function runtime(element: FakeCheckoutElement, supported = true): CheckoutKitRuntime {
  return {
    supported: () => supported,
    createElement: async () => element,
  }
}

describe('Meant Checkout Kit adapter', () => {
  test('pins the Shopify release that supports checkout redirects to custom storefront domains', async () => {
    const manifest = (await Bun.file(
      new URL('../../../../package.json', import.meta.url),
    ).json()) as {
      dependencies?: Record<string, string>
    }

    expect(manifest.dependencies?.['@shopify/checkout-kit']).toBe('4.0.0-alpha.2')
  })

  test('isolates SDK events behind a provider-neutral lifecycle', async () => {
    const element = new FakeCheckoutElement()
    const events: string[] = []
    const handle = await createCheckoutKitHandle(
      'https://shop.example/checkouts/cn/test',
      {
        onReady: () => events.push('ready'),
        onStart: () => events.push('start'),
        onComplete: () => events.push('complete'),
        onClose: () => events.push('close'),
        onError: (failure) => events.push(`error:${failure}`),
      },
      { runtime: runtime(element) },
    )

    expect(CHECKOUT_KIT_ECP_VERSION).toBe('2026-04-08')
    expect(element.src).toBe('https://shop.example/checkouts/cn/test')
    expect(element.target).toBe('popup')
    expect(element.style.display).toBe('')
    expect(events).toEqual(['ready'])

    handle.open()
    handle.focus()
    element.emit('checkout:start')
    element.emit('checkout:complete')
    element.emit('checkout:error')
    element.emit('checkout:close')

    expect(element.openCount).toBe(1)
    expect(element.focusCount).toBe(1)
    expect(events).toEqual(['ready', 'start', 'complete', 'error:SDK_ERROR', 'close'])

    handle.destroy()
    element.emit('checkout:start')
    expect(element.removed).toBe(true)
    expect(events).toHaveLength(5)
  })

  test('fails closed before loading the SDK for unsupported browsers and unsafe URLs', async () => {
    const element = new FakeCheckoutElement()
    const lifecycle = {
      onReady: () => undefined,
      onStart: () => undefined,
      onComplete: () => undefined,
      onClose: () => undefined,
      onError: () => undefined,
    }

    await expect(
      createCheckoutKitHandle('https://shop.example/checkout', lifecycle, {
        runtime: runtime(element, false),
      }),
    ).rejects.toMatchObject({ failure: 'UNSUPPORTED_BROWSER' })
    await expect(
      createCheckoutKitHandle('http://shop.example/checkout', lifecycle, {
        runtime: runtime(element),
      }),
    ).rejects.toMatchObject({ failure: 'INVALID_CHECKOUT_URL' })
  })
})
