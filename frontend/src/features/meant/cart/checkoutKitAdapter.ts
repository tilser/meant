export const CHECKOUT_KIT_ECP_VERSION = '2026-04-08'

export type CheckoutKitFailure =
  'UNSUPPORTED_BROWSER' | 'INVALID_CHECKOUT_URL' | 'SDK_LOAD_FAILED' | 'SDK_ERROR'

export interface CheckoutKitLifecycle {
  onReady: () => void
  onStart: () => void
  onComplete: () => void
  onClose: () => void
  onError: (failure: CheckoutKitFailure) => void
}

export interface CheckoutKitHandle {
  open: () => void
  focus: () => void
  close: () => void
  destroy: () => void
}

export interface CheckoutKitElementPort {
  src: string
  target: string
  debug: boolean
  open: () => void
  focus: () => void
  close: () => void
  addEventListener: (type: string, listener: EventListener) => void
  removeEventListener: (type: string, listener: EventListener) => void
  remove: () => void
}

export interface CheckoutKitRuntime {
  supported: () => boolean
  createElement: () => Promise<CheckoutKitElementPort>
}

function browserSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof document !== 'undefined' &&
    typeof window.customElements !== 'undefined' &&
    typeof window.AbortController !== 'undefined' &&
    typeof window.HTMLDialogElement !== 'undefined'
  )
}

const browserRuntime: CheckoutKitRuntime = {
  supported: browserSupported,
  async createElement() {
    await import('@shopify/checkout-kit')
    const element = document.createElement('shopify-checkout') as unknown as CheckoutKitElementPort
    document.body.append(element as unknown as Node)
    return element
  },
}

function validCheckoutUrl(value: string): string | null {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && url.hostname ? url.toString() : null
  } catch {
    return null
  }
}

export async function createCheckoutKitHandle(
  checkoutUrl: string,
  lifecycle: CheckoutKitLifecycle,
  options?: Readonly<{ debug?: boolean; runtime?: CheckoutKitRuntime }>,
): Promise<CheckoutKitHandle> {
  const runtime = options?.runtime ?? browserRuntime
  if (!runtime.supported()) {
    throw new CheckoutKitAdapterError('UNSUPPORTED_BROWSER')
  }
  const safeCheckoutUrl = validCheckoutUrl(checkoutUrl)
  if (!safeCheckoutUrl) {
    throw new CheckoutKitAdapterError('INVALID_CHECKOUT_URL')
  }

  let element: CheckoutKitElementPort
  try {
    element = await runtime.createElement()
  } catch {
    throw new CheckoutKitAdapterError('SDK_LOAD_FAILED')
  }

  let destroyed = false
  const listeners: ReadonlyArray<readonly [string, EventListener]> = [
    ['checkout:start', () => lifecycle.onStart()],
    ['checkout:complete', () => lifecycle.onComplete()],
    ['checkout:close', () => lifecycle.onClose()],
    ['checkout:error', () => lifecycle.onError('SDK_ERROR')],
  ]
  element.src = safeCheckoutUrl
  element.target = 'popup'
  element.debug = options?.debug === true
  listeners.forEach(([type, listener]) => element.addEventListener(type, listener))
  lifecycle.onReady()

  const requireActive = () => {
    if (destroyed) {
      throw new Error('Checkout Kit handle has been destroyed')
    }
  }

  return {
    open() {
      requireActive()
      element.open()
    },
    focus() {
      requireActive()
      element.focus()
    },
    close() {
      if (!destroyed) element.close()
    },
    destroy() {
      if (destroyed) return
      destroyed = true
      listeners.forEach(([type, listener]) => element.removeEventListener(type, listener))
      element.close()
      element.remove()
    },
  }
}

export class CheckoutKitAdapterError extends Error {
  readonly failure: CheckoutKitFailure

  constructor(failure: CheckoutKitFailure) {
    super(failure)
    this.name = 'CheckoutKitAdapterError'
    this.failure = failure
  }
}
